package world.mongoose.banteng.runtime;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.FutureTask;
import world.mongoose.banteng.builtin.BuiltinCall;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.vm.VmSnapshot;
import world.mongoose.banteng.world.ObjectFlags;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldTxn;

/** The scheduler-owned live registry used by task-management builtins. */
final class TaskRegistry {
  private static final long BACKGROUND_TICKS = 30_000;

  enum TaskControlDecision {
    ALLOWED,
    DENIED,
    MISSING
  }

  @GuardedBy("this") private final Map<Long, TaskInfo> tasks = new TreeMap<>();
  @GuardedBy("this") private final Map<Long, FutureTask<BuiltinResult>> hostWork = new TreeMap<>();
  @GuardedBy("this") private final Map<Long, Runnable> cancellationActions = new TreeMap<>();
  @GuardedBy("this") private final Set<Long> canceled = new HashSet<>();
  @GuardedBy("this") private long nextHostHandle = 1;
  @GuardedBy("this") private long nextQueueSequence = 1;

  synchronized void registerFork(
      long taskId,
      long scheduledStart,
      long programmer,
      MooValue verbLocation,
      Map<String, MooValue> variables) {
    registerFork(
        taskId,
        scheduledStart,
        programmer,
        verbLocation,
        variables,
        stringVariable(variables, "verb"),
        0,
        Optional.empty());
  }

  synchronized void registerFork(
      long taskId,
      long scheduledStart,
      long programmer,
      MooValue verbLocation,
      Map<String, MooValue> variables,
      String fullVerbName,
      long sourceLine,
      VmSnapshot snapshot) {
    registerFork(
        taskId,
        scheduledStart,
        programmer,
        verbLocation,
        variables,
        StringValue.of(fullVerbName),
        sourceLine,
        Optional.of(snapshot));
  }

  private synchronized void registerFork(
      long taskId,
      long scheduledStart,
      long programmer,
      MooValue verbLocation,
      Map<String, MooValue> variables,
      StringValue fullVerbName,
      long sourceLine,
      Optional<VmSnapshot> snapshot) {
    if (tasks.putIfAbsent(
            taskId,
            new TaskInfo(
                taskId,
                new IntegerValue(scheduledStart),
                nextQueueSequence++,
                programmer,
                verbLocation,
                fullVerbName,
                sourceLine,
                variables.getOrDefault("this", verbLocation),
                0,
                variables,
                0,
                true,
                snapshot))
        != null) {
      throw new IllegalStateException("duplicate live task " + taskId);
    }
  }

  synchronized void registerSuspended(
      long taskId, long scheduledStart, VmSnapshot snapshot) {
    tasks.put(taskId, suspendedTask(taskId, scheduledStart, nextQueueSequence++, snapshot));
  }

  synchronized void updateSuspended(
      long taskId, long scheduledStart, VmSnapshot snapshot) {
    TaskInfo current = tasks.get(taskId);
    if (current != null) {
      tasks.put(
          taskId,
          suspendedTask(taskId, scheduledStart, current.queueSequence(), snapshot));
    }
  }

  private static TaskInfo suspendedTask(
      long taskId, long scheduledStart, long queueSequence, VmSnapshot snapshot) {
    VmSnapshot.Frame activation = snapshot.frames().getFirst();
    int suspendedInstruction = Math.subtractExact(activation.instructionPointer(), 1);
    return new TaskInfo(
        taskId,
        new IntegerValue(scheduledStart),
        queueSequence,
        activation.programmer(),
        activation.verbLocation(),
        StringValue.of(activation.fullVerbName()),
        activation.program().sourceLine(suspendedInstruction),
        activation.receiver(),
        snapshot.byteSize(),
        activation.locals(),
        0,
        true,
        Optional.of(snapshot));
  }

  synchronized void remove(long taskId) {
    tasks.remove(taskId);
    hostWork.remove(taskId);
    cancellationActions.remove(taskId);
    if (hostWork.isEmpty()) {
      nextHostHandle = 1;
    }
    canceled.remove(taskId);
  }

  synchronized boolean registerHost(
      long taskId,
      VmSnapshot snapshot,
      FutureTask<BuiltinResult> submitted) {
    if (canceled.contains(taskId)) {
      return false;
    }
    if (hostWork.putIfAbsent(taskId, submitted) != null) {
      throw new IllegalStateException("duplicate host work " + taskId);
    }
    VmSnapshot.Frame activation = snapshot.frames().getFirst();
    int suspendedInstruction = Math.subtractExact(activation.instructionPointer(), 1);
    long handle = nextHostHandle++;
    TaskInfo current = tasks.get(taskId);
    long queueSequence = current == null ? nextQueueSequence++ : current.queueSequence();
    tasks.put(
        taskId,
        new TaskInfo(
            taskId,
            StringValue.of("waiting on thread " + handle),
            queueSequence,
            activation.programmer(),
            activation.verbLocation(),
            StringValue.of(activation.fullVerbName()),
            activation.program().sourceLine(suspendedInstruction),
            activation.receiver(),
            snapshot.byteSize(),
            Map.of(),
            handle,
            false,
            Optional.of(snapshot)));
    return true;
  }

  synchronized boolean claimHostTerminal(long taskId) {
    if (!tasks.containsKey(taskId) || canceled.contains(taskId)) {
      return false;
    }
    tasks.remove(taskId);
    hostWork.remove(taskId);
    cancellationActions.remove(taskId);
    if (hostWork.isEmpty()) {
      nextHostHandle = 1;
    }
    return true;
  }

  synchronized int size() {
    return tasks.size();
  }

  synchronized List<VmSnapshot> snapshotsExcluding(long taskId) {
    return tasks.entrySet().stream()
        .filter(entry -> entry.getKey() != taskId)
        .map(Map.Entry::getValue)
        .map(TaskInfo::snapshot)
        .flatMap(Optional::stream)
        .toList();
  }

  synchronized void registerCancellation(long taskId, Runnable action) {
    if (tasks.containsKey(taskId)) {
      cancellationActions.put(taskId, Objects.requireNonNull(action, "action"));
    }
  }

  synchronized List<Long> queuePlayers() {
    return tasks.values().stream().map(TaskInfo::programmer).distinct().sorted().toList();
  }

  synchronized long backgroundTaskCount(long player) {
    return tasks.values().stream().filter(task -> task.programmer() == player).count();
  }

  synchronized TaskControlDecision taskControlDecision(
      long taskId, WorldTxn world, long programmer) {
    TaskInfo task = tasks.get(taskId);
    if (task == null) {
      return TaskControlDecision.MISSING;
    }
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && ObjectFlags.isWizard(actor.flags());
    return wizard || task.programmer() == programmer
        ? TaskControlDecision.ALLOWED
        : TaskControlDecision.DENIED;
  }

  synchronized BuiltinResult threads(BuiltinCall call) {
    return BuiltinResult.value(
        new ListValue(
            tasks.values().stream()
                .map(TaskInfo::hostHandle)
                .filter(handle -> handle > 0)
                .map(IntegerValue::new)
                .toList()));
  }

  synchronized boolean discardIfCanceled(long taskId) {
    return canceled.remove(taskId);
  }

  BuiltinResult killTask(BuiltinCall call) {
    long taskId = ((IntegerValue) call.arguments().getFirst()).value();
    WorldObject actor = call.world().object(call.programmer()).orElse(null);
    boolean wizard = actor != null && ObjectFlags.isWizard(actor.flags());
    FutureTask<BuiltinResult> submitted;
    Runnable cancellation;
    synchronized (this) {
      TaskInfo task = tasks.get(taskId);
      if (task == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      if (!wizard && task.programmer() != call.programmer()) {
        return BuiltinResult.error(ErrorValue.E_PERM);
      }
      tasks.remove(taskId);
      submitted = hostWork.remove(taskId);
      cancellation = cancellationActions.remove(taskId);
      if (hostWork.isEmpty()) {
        nextHostHandle = 1;
      }
      canceled.add(taskId);
    }
    if (submitted != null) {
      submitted.cancel(true);
    }
    if (cancellation != null) {
      cancellation.run();
    }
    return BuiltinResult.value(new IntegerValue(0));
  }

  BuiltinResult queuedTasks(BuiltinCall call) {
    boolean includeVariables =
        call.arguments().size() == 1
            && ((IntegerValue) call.arguments().getFirst()).isTruthy();
    boolean returnCount =
        call.arguments().size() == 2 && ((IntegerValue) call.arguments().get(1)).isTruthy();
    WorldObject actor = call.world().object(call.programmer()).orElse(null);
    boolean wizard = actor != null && ObjectFlags.isWizard(actor.flags());
    List<TaskInfo> visible = visibleTo(call.programmer(), wizard);
    if (returnCount) {
      return BuiltinResult.value(new IntegerValue(visible.size()));
    }
    return BuiltinResult.value(
        new ListValue(
            visible.stream()
                .map(task -> task.row(includeVariables, call.world(), call.programmer()))
                .toList()));
  }

  BuiltinResult taskStack(BuiltinCall call) {
    long taskId = ((IntegerValue) call.arguments().getFirst()).value();
    TaskInfo task;
    synchronized (this) {
      task = tasks.get(taskId);
    }
    if (task == null || taskId == call.taskId() || task.snapshot().isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    WorldObject actor = call.world().object(call.programmer()).orElse(null);
    boolean wizard = actor != null && ObjectFlags.isWizard(actor.flags());
    if (!wizard && task.programmer() != call.programmer()) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    boolean includeLines = call.arguments().size() >= 2 && call.arguments().get(1).isTruthy();
    boolean includeVariables = call.arguments().size() >= 3 && call.arguments().get(2).isTruthy();
    return BuiltinResult.value(
        new ListValue(
            task.snapshot().orElseThrow().frames().stream()
                .map(frame -> stackFrame(frame, includeLines, includeVariables))
                .map(frame -> projectStackFrame(frame, call.world(), call.programmer()))
                .toList()));
  }

  private static ListValue projectStackFrame(
      ListValue frame, WorldTxn world, long programmer) {
    List<MooValue> fields = new ArrayList<>(frame.elements());
    fields.set(0, anonymizeTaskReference(fields.get(0), world, programmer));
    fields.set(3, anonymizeTaskReference(fields.get(3), world, programmer));
    return new ListValue(fields);
  }

  private static MooValue anonymizeTaskReference(
      MooValue value, WorldTxn world, long programmer) {
    if (!(value instanceof world.mongoose.banteng.value.MooValue.AnonymousObjectValue anonymous)) {
      return value;
    }
    var body = world.anonymousObject(anonymous).orElse(null);
    if (body == null) {
      return value;
    }
    WorldObject viewer = world.object(programmer).orElse(null);
    boolean wizard = viewer != null && ObjectFlags.isWizard(viewer.flags());
    return wizard || body.owner() == programmer
        ? value
        : new world.mongoose.banteng.value.MooValue.AnonymousObjectValue();
  }

  private static ListValue stackFrame(
      VmSnapshot.Frame frame, boolean includeLines, boolean includeVariables) {
    List<MooValue> fields = new ArrayList<>(5 + (includeLines ? 1 : 0) + (includeVariables ? 1 : 0));
    fields.add(frame.receiver());
    fields.add(StringValue.of(frame.calledVerb()));
    fields.add(new ObjectValue(frame.programmer()));
    fields.add(frame.verbLocation());
    fields.add(frame.locals().getOrDefault("player", new ObjectValue(-1)));
    if (includeLines) {
      int instructionCount = frame.program().instructions().size();
      int instruction =
          instructionCount == 0
              ? -1
              : Math.max(0, Math.min(frame.instructionPointer() - 1, instructionCount - 1));
      fields.add(
          new IntegerValue(
              instruction < 0 ? 0 : frame.program().sourceLine(instruction)));
    }
    if (includeVariables) {
      Map<MooValue, MooValue> variables = new LinkedHashMap<>();
      frame.locals().forEach((name, value) -> variables.put(StringValue.of(name), value));
      fields.add(new MapValue(variables));
    }
    return new ListValue(fields);
  }

  private synchronized List<TaskInfo> visibleTo(long programmer, boolean wizard) {
    return tasks.values().stream()
        .filter(task -> wizard || task.programmer() == programmer)
        .sorted(
            Comparator.comparingInt(TaskRegistry::queueClass)
                .thenComparingLong(TaskRegistry::scheduledStart)
                .thenComparingLong(TaskInfo::queueSequence))
        .toList();
  }

  private static int queueClass(TaskInfo task) {
    return task.status() instanceof IntegerValue ? 0 : 1;
  }

  private static long scheduledStart(TaskInfo task) {
    return task.status() instanceof IntegerValue start ? start.value() : 0;
  }

  private static StringValue stringVariable(Map<String, MooValue> variables, String name) {
    MooValue value = variables.get(name);
    return value instanceof StringValue string ? string : StringValue.of("");
  }

  private record TaskInfo(
      long taskId,
      MooValue status,
      long queueSequence,
      long programmer,
      MooValue verbLocation,
      StringValue verbName,
      long sourceLine,
      MooValue receiver,
      long bytes,
      Map<String, MooValue> variables,
      long hostHandle,
      boolean variablesVisible,
      Optional<VmSnapshot> snapshot) {
    TaskInfo {
      variables = Map.copyOf(variables);
      Objects.requireNonNull(snapshot, "snapshot");
    }

    ListValue row(boolean includeVariables, WorldTxn world, long viewerProgrammer) {
      boolean appendVariables = includeVariables && variablesVisible;
      List<MooValue> fields = new ArrayList<>(appendVariables ? 11 : 10);
      fields.add(new IntegerValue(taskId));
      fields.add(status);
      fields.add(new IntegerValue(0));
      fields.add(new IntegerValue(BACKGROUND_TICKS));
      fields.add(new ObjectValue(programmer));
      fields.add(anonymizeTaskReference(verbLocation, world, viewerProgrammer));
      fields.add(verbName);
      fields.add(new IntegerValue(sourceLine));
      fields.add(anonymizeTaskReference(receiver, world, viewerProgrammer));
      fields.add(new IntegerValue(bytes));
      if (appendVariables) {
        Map<MooValue, MooValue> runtimeVariables = new LinkedHashMap<>();
        variables.forEach((name, value) -> runtimeVariables.put(StringValue.of(name), value));
        fields.add(new MapValue(runtimeVariables));
      }
      return new ListValue(fields);
    }
  }
}
