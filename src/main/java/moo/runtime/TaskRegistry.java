package moo.runtime;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.FutureTask;
import moo.builtin.BuiltinCatalog;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.vm.VmSnapshot;
import moo.world.WorldObject;
import moo.world.WorldTxn;

/** The scheduler-owned live registry used by task-management builtins. */
final class TaskRegistry {
  private static final long BACKGROUND_TICKS = 30_000;

  private final Map<Long, TaskInfo> tasks = new TreeMap<>();
  private final Map<Long, FutureTask<BuiltinCatalog.Result>> hostWork = new TreeMap<>();
  private final Set<Long> canceled = new HashSet<>();
  private long nextHostHandle = 1;

  synchronized void registerFork(
      long taskId,
      long scheduledStart,
      long programmer,
      ObjectValue verbLocation,
      Map<String, MooValue> variables) {
    if (tasks.putIfAbsent(
            taskId,
            new TaskInfo(
                taskId,
                new IntegerValue(scheduledStart),
                programmer,
                verbLocation,
                stringVariable(variables, "verb"),
                0,
                variables.getOrDefault("this", verbLocation),
                0,
                variables,
                0,
                true))
        != null) {
      throw new IllegalStateException("duplicate live task " + taskId);
    }
  }

  synchronized void remove(long taskId) {
    tasks.remove(taskId);
    hostWork.remove(taskId);
    if (hostWork.isEmpty()) {
      nextHostHandle = 1;
    }
    canceled.remove(taskId);
  }

  synchronized boolean registerHost(
      long taskId,
      VmSnapshot snapshot,
      FutureTask<BuiltinCatalog.Result> submitted) {
    if (canceled.contains(taskId)) {
      return false;
    }
    if (hostWork.putIfAbsent(taskId, submitted) != null) {
      throw new IllegalStateException("duplicate host work " + taskId);
    }
    VmSnapshot.Frame activation = snapshot.frames().getFirst();
    int suspendedInstruction = Math.subtractExact(activation.instructionPointer(), 1);
    long handle = nextHostHandle++;
    tasks.put(
        taskId,
        new TaskInfo(
            taskId,
            string("waiting on thread " + handle),
            activation.programmer(),
            activation.verbLocation(),
            stringVariable(activation.locals(), "verb"),
            activation.program().sourceLine(suspendedInstruction),
            activation.receiver(),
            snapshot.byteSize(),
            Map.of(),
            handle,
            false));
    return true;
  }

  synchronized boolean claimHostTerminal(long taskId) {
    if (!tasks.containsKey(taskId) || canceled.contains(taskId)) {
      return false;
    }
    tasks.remove(taskId);
    hostWork.remove(taskId);
    if (hostWork.isEmpty()) {
      nextHostHandle = 1;
    }
    return true;
  }

  synchronized int size() {
    return tasks.size();
  }

  synchronized BuiltinCatalog.Result threads(
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long currentTaskId,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers) {
    return BuiltinCatalog.Result.value(
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

  synchronized BuiltinCatalog.Result killTask(
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long currentTaskId,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers) {
    long taskId = ((IntegerValue) arguments.getFirst()).value();
    TaskInfo task = tasks.get(taskId);
    if (task == null) {
      return BuiltinCatalog.Result.error(ErrorValue.E_INVARG);
    }
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && (actor.flags() & 4) != 0;
    if (!wizard && task.programmer() != programmer) {
      return BuiltinCatalog.Result.error(ErrorValue.E_PERM);
    }
    tasks.remove(taskId);
    FutureTask<BuiltinCatalog.Result> submitted = hostWork.remove(taskId);
    if (hostWork.isEmpty()) {
      nextHostHandle = 1;
    }
    canceled.add(taskId);
    if (submitted != null) {
      submitted.cancel(true);
    }
    return BuiltinCatalog.Result.value(new IntegerValue(0));
  }

  BuiltinCatalog.Result queuedTasks(
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long currentTaskId,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers) {
    boolean includeVariables =
        arguments.size() == 1 && ((IntegerValue) arguments.getFirst()).isTruthy();
    boolean returnCount =
        arguments.size() == 2 && ((IntegerValue) arguments.get(1)).isTruthy();
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && (actor.flags() & 4) != 0;
    List<TaskInfo> visible = visibleTo(programmer, wizard);
    if (returnCount) {
      return BuiltinCatalog.Result.value(new IntegerValue(visible.size()));
    }
    return BuiltinCatalog.Result.value(
        new ListValue(visible.stream().map(task -> task.row(includeVariables)).toList()));
  }

  private synchronized List<TaskInfo> visibleTo(long programmer, boolean wizard) {
    return tasks.values().stream()
        .filter(task -> wizard || task.programmer() == programmer)
        .toList();
  }

  private static StringValue stringVariable(Map<String, MooValue> variables, String name) {
    MooValue value = variables.get(name);
    return value instanceof StringValue string ? string : string("");
  }

  private static StringValue string(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  private record TaskInfo(
      long taskId,
      MooValue status,
      long programmer,
      ObjectValue verbLocation,
      StringValue verbName,
      long sourceLine,
      MooValue receiver,
      long bytes,
      Map<String, MooValue> variables,
      long hostHandle,
      boolean variablesVisible) {
    TaskInfo {
      variables = Map.copyOf(variables);
    }

    ListValue row(boolean includeVariables) {
      boolean appendVariables = includeVariables && variablesVisible;
      List<MooValue> fields = new ArrayList<>(appendVariables ? 11 : 10);
      fields.add(new IntegerValue(taskId));
      fields.add(status);
      fields.add(new IntegerValue(0));
      fields.add(new IntegerValue(BACKGROUND_TICKS));
      fields.add(new ObjectValue(programmer));
      fields.add(verbLocation);
      fields.add(verbName);
      fields.add(new IntegerValue(sourceLine));
      fields.add(receiver);
      fields.add(new IntegerValue(bytes));
      if (appendVariables) {
        Map<MooValue, MooValue> runtimeVariables = new LinkedHashMap<>();
        variables.forEach((name, value) -> runtimeVariables.put(string(name), value));
        fields.add(new MapValue(runtimeVariables));
      }
      return new ListValue(fields);
    }
  }
}
