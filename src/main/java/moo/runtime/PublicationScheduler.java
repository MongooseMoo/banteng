package moo.runtime;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import jdk.jfr.FlightRecorder;
import moo.builtin.BuiltinCatalog.Result;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.BytecodeProgram.Instruction;
import moo.bytecode.BytecodeProgram.Opcode;
import moo.bytecode.MooCompiler;
import moo.persistence.LambdaMooV17Codec.DurableTask;
import moo.persistence.LambdaMooV17Codec.QueuedTask;
import moo.persistence.LambdaMooV17Codec.SuspendedActivation;
import moo.persistence.LambdaMooV17Codec.SuspendedStackSlot;
import moo.persistence.LambdaMooV17Codec.SuspendedTask;
import moo.persistence.ToastV17ProgramLayout;
import moo.persistence.ToastV17ProgramLayout.ContinuationSite;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.vm.VmSnapshot;
import moo.vm.VmState;
import moo.world.WorldTxn;
import org.jspecify.annotations.Nullable;

/** The sole deterministic execution, validation, retry, and publication owner. */
final class PublicationScheduler implements AutoCloseable {
  private static final long INDEFINITE_SUSPEND_EPOCH_MILLIS =
      Math.multiplyExact((long) Integer.MAX_VALUE, 1_000L);
  private final WorldTxn committedWorld;
  private final MooRuntime runtime;
  private final TaskRegistry taskRegistry;
  private volatile int workers;
  private volatile int backgroundWorkers;
  private final ThreadPoolExecutor executor;
  private final Queue<Entry> ready = new ArrayDeque<>();
  private final Map<Long, Attempt> completed = new TreeMap<>();
  private final Map<Long, CompletableFuture<List<String>>> ingress = new TreeMap<>();
  private final Map<Long, Long> lastInputTasks = new TreeMap<>();
  private final Map<Long, TimedWork> timedWork = new TreeMap<>();
  private final Map<Long, TimedWork> checkpointingWork = new TreeMap<>();
  private final Map<Long, SuspendedWork> finalizationBlocked = new LinkedHashMap<>();
  private long nextTicket;
  private long nextTaskId;
  private long nextPublicationTicket;
  private boolean publicationDraining;
  private boolean restoredTasksActivated;
  private boolean closed;

  PublicationScheduler(WorldTxn committedWorld, MooRuntime runtime) {
    this(
        committedWorld,
        runtime,
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        new TaskRegistry());
  }

  PublicationScheduler(WorldTxn committedWorld, MooRuntime runtime, TaskRegistry taskRegistry) {
    this(
        committedWorld,
        runtime,
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        taskRegistry);
  }

  PublicationScheduler(WorldTxn committedWorld, MooRuntime runtime, int workers) {
    this(committedWorld, runtime, workers, new TaskRegistry());
  }

  PublicationScheduler(
      WorldTxn committedWorld, MooRuntime runtime, int workers, TaskRegistry taskRegistry) {
    this.committedWorld = Objects.requireNonNull(committedWorld, "committedWorld");
    this.runtime = Objects.requireNonNull(runtime, "runtime");
    this.taskRegistry = Objects.requireNonNull(taskRegistry, "taskRegistry");
    if (workers < 1) {
      throw new IllegalArgumentException("workers must be positive");
    }
    this.workers = workers;
    backgroundWorkers = workers;
    executor =
        new ThreadPoolExecutor(
            workers,
            workers,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(Math.multiplyExact(workers, 4)),
            Thread.ofPlatform().name("moo-vm-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());
  }

  void restoreTasks(List<DurableTask> restoredTasks) {
    List<TimedWork> timers =
        Objects.requireNonNull(restoredTasks, "restoredTasks").stream()
            .map(
                task ->
                    switch (task) {
                      case QueuedTask queued -> restoreQueuedTask(queued);
                      case SuspendedTask suspended -> restoreSuspendedTask(suspended);
                    })
            .toList();
    synchronized (this) {
      if (!timedWork.isEmpty()) {
        throw new IllegalStateException("durable tasks have already been restored");
      }
      for (TimedWork timer : timers) {
        registerTimer(timer);
      }
    }
  }

  /** Starts restored timers once startup callbacks and {@code server_started} have completed. */
  void activateRestoredTasks() {
    List<TimedWork> restored;
    synchronized (this) {
      if (restoredTasksActivated) {
        throw new IllegalStateException("restored tasks have already been activated");
      }
      restoredTasksActivated = true;
      restored = List.copyOf(timedWork.values());
    }
    restored.stream()
        .filter(timer -> timer.scheduledEpochMilli() != INDEFINITE_SUSPEND_EPOCH_MILLIS)
        .forEach(this::launchTimer);
  }

  synchronized Result threadPool(
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
    int requested =
        arguments.size() == 3 ? Math.toIntExact(((IntegerValue) arguments.get(2)).value()) : 0;
    backgroundWorkers = requested;
    if (requested == 0) {
      return Result.value(new IntegerValue(1));
    }
    if (requested > executor.getMaximumPoolSize()) {
      executor.setMaximumPoolSize(requested);
      executor.setCorePoolSize(requested);
    } else {
      executor.setCorePoolSize(requested);
      executor.setMaximumPoolSize(requested);
    }
    workers = requested;
    return Result.value(new IntegerValue(1));
  }

  private TimedWork restoreQueuedTask(QueuedTask restored) {
    BytecodeProgram program = new MooCompiler().compile(restored.programSource());
    VmState state =
        new VmState(
            restored.initialLocals(),
            restored.programmer(),
            restored.verbLocation(),
            MooRuntime.DEFAULT_BACKGROUND_TICKS,
            MooRuntime.DEFAULT_BACKGROUND_SECONDS,
            MooRuntime.DEFAULT_MAX_STACK_DEPTH,
            restored.debug());
    state.ensureRoot(program);
    state.setThreadMode(restored.threadMode());
    VmSnapshot snapshot = state.snapshot();
    SuspendedWork work =
        new SuspendedWork(
            restored.taskId(),
            program,
            snapshot,
            restored.taskPlayer(),
            Optional.empty(),
            true);
    taskRegistry.registerFork(
        restored.taskId(),
        restored.scheduledEpochSecond(),
        restored.programmer(),
        restored.verbLocation(),
        restored.initialLocals(),
        snapshot);
    nextTaskId = Math.max(nextTaskId, Math.addExact(restored.taskId(), 1));
    return new TimedWork(
        work,
        Math.multiplyExact(restored.scheduledEpochSecond(), 1_000L),
        Optional.empty(),
        Optional.of(restored));
  }

  private TimedWork restoreSuspendedTask(SuspendedTask restored) {
    if (restored.functionId() != 0) {
      throw new IllegalArgumentException(
          "unsupported v17 function id " + restored.functionId() + " for task " + restored.taskId());
    }
    if (restored.maxStackDepth() < 1) {
      throw new IllegalArgumentException("invalid v17 maximum stack depth for task " + restored.taskId());
    }

    ToastV17ProgramLayout layout = new ToastV17ProgramLayout();
    List<VmSnapshot.Frame> rootToCurrent = new ArrayList<>();
    for (int activationIndex = 0;
        activationIndex < restored.activations().size();
        activationIndex++) {
      SuspendedActivation activation = restored.activations().get(activationIndex);
      int vector = activationIndex == 0 ? restored.rootActivationVector() : -1;
      rootToCurrent.add(importActivation(activation, vector, activationIndex == 0, layout));
    }
    Collections.reverse(rootToCurrent);
    VmSnapshot.Frame root = rootToCurrent.getLast();
    VmSnapshot snapshot =
        new VmSnapshot(
            root.locals(),
            root.programmer(),
            root.verbLocation(),
            rootToCurrent,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            VmState.Outcome.SUSPENDED,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            OptionalLong.empty(),
            Optional.empty(),
            OptionalDouble.of(0.0),
            false,
            Optional.empty(),
            restored.taskLocal(),
            MooRuntime.DEFAULT_BACKGROUND_TICKS,
            0,
            TimeUnit.SECONDS.toNanos(MooRuntime.DEFAULT_BACKGROUND_SECONDS),
            restored.maxStackDepth());
    SuspendedActivation current = restored.activations().getLast();
    SuspendedWork work =
        new SuspendedWork(
            restored.taskId(),
            snapshot.frames().getFirst().program(),
            snapshot,
            current.taskPlayer(),
            Optional.empty(),
            false);
    taskRegistry.registerSuspended(
        restored.taskId(), restored.scheduledEpochSecond(), snapshot);
    nextTaskId = Math.max(nextTaskId, Math.addExact(restored.taskId(), 1));
    Result wake =
        restored.interruptionStatus().isPresent()
            ? Result.error(ErrorValue.E_INTRPT)
            : restored.resumeValue() instanceof ErrorValue error
                ? Result.error(error)
                : Result.value(restored.resumeValue());
    return new TimedWork(
        work,
        Math.multiplyExact(restored.scheduledEpochSecond(), 1_000L),
        Optional.of(wake),
        Optional.of(restored));
  }

  private static VmSnapshot.Frame importActivation(
      SuspendedActivation activation,
      int vector,
      boolean rootActivation,
      ToastV17ProgramLayout layout) {
    if (activation.languageVersion() != 17) {
      throw new IllegalArgumentException(
          "unsupported suspended activation language version " + activation.languageVersion());
    }
    if (activation.builtinFunctionCounter() != 0) {
      throw new IllegalArgumentException("suspended builtin continuation is unsupported");
    }
    if (activation.temporary().isPresent()) {
      throw new IllegalArgumentException("suspended activation temporary value is unsupported");
    }

    BytecodeProgram compiled = new MooCompiler().compile(activation.programSource());
    BytecodeProgram selected =
        vector < 0
            ? compiled
            : compiled.forkVectors().get(vector);
    ToastV17ProgramLayout.CallBoundary boundary =
        layout.resolve(
            activation.programSource(),
            vector,
            activation.errorCounter(),
            activation.programCounter());

    ContinuationSite site = layout.resolveContinuation(selected, boundary);
    ToastV17ProgramLayout.StructuralStackShape structural =
        layout.resolveStructuralStack(
            activation.programSource(), vector, selected, boundary);
    ImportedActivationStack imported =
        importStructuralStack(structural, activation.operandStack());
    Map<String, MooValue> locals = new LinkedHashMap<>();
    activation.locals().forEach(
        (name, value) -> value.ifPresent(present -> locals.put(name, present)));
    return new VmSnapshot.Frame(
        site.call().program(),
        imported.operandStack(),
        List.of(),
        locals,
        imported.handlers(),
        imported.finallyStates(),
        imported.loops(),
        rootActivation ? VmSnapshot.ReturnMode.ROOT : VmSnapshot.ReturnMode.VERB,
        activation.receiver(),
        activation.verbLocation(),
        Optional.empty(),
        OptionalLong.empty(),
        OptionalLong.empty(),
        OptionalLong.empty(),
        OptionalLong.empty(),
        activation.programmer(),
        activation.debug(),
        activation.threadMode(),
        site.call().resumeInstructionPointer(),
        Optional.empty());
  }

  private static ImportedActivationStack importStructuralStack(
      ToastV17ProgramLayout.StructuralStackShape shape,
      List<SuspendedStackSlot> topToBase) {
    if (shape.postArgumentDepth() != topToBase.size()) {
      throw new IllegalArgumentException(
          "v17 runtime stack depth "
              + topToBase.size()
              + " disagrees with structural depth "
              + shape.postArgumentDepth());
    }
    List<SuspendedStackSlot> baseToTop = new ArrayList<>(topToBase);
    Collections.reverse(baseToTop);
    boolean[] consumed = new boolean[baseToTop.size()];
    List<ImportedHandlerGroup> handlerGroups = new ArrayList<>();
    List<ImportedFinally> finallyStates = new ArrayList<>();
    Map<Integer, VmSnapshot.LoopState> loops = new LinkedHashMap<>();

    for (ToastV17ProgramLayout.StructuralStackEntry entry : shape.entriesBaseToTop()) {
      switch (entry) {
        case ToastV17ProgramLayout.CatchGroup group ->
            importCatchGroup(group, baseToTop, consumed, handlerGroups);
        case ToastV17ProgramLayout.ProtectedFinally protectedFinally -> {
          requireControl(
              baseToTop,
              consumed,
              protectedFinally.markerDepth(),
              8,
              protectedFinally.handlerLabelProgramCounter(),
              "protected finally");
          handlerGroups.add(
              new ImportedHandlerGroup(
                  protectedFinally.baseDepth(),
                  List.of(
                      new ImportedHandler(
                          protectedFinally.ownerControl(),
                          VmSnapshot.HandlerPhase.TRY))));
        }
        case ToastV17ProgramLayout.FinallyContinuation continuation -> {
          MooValue reason =
              requireValue(
                  baseToTop,
                  consumed,
                  continuation.reasonDepth(),
                  "finally reason");
          MooValue value =
              requireValue(
                  baseToTop,
                  consumed,
                  continuation.valueDepth(),
                  "finally value");
          finallyStates.add(
              new ImportedFinally(
                  continuation.baseDepth(),
                  importFinallyState(continuation, reason, value)));
        }
        case ToastV17ProgramLayout.CollectionLoop loop ->
            loops.put(
                loop.control().instructionIndex(),
                importCollectionLoop(loop, baseToTop, consumed));
        case ToastV17ProgramLayout.RangeLoop loop ->
            loops.put(
                loop.control().instructionIndex(),
                importRangeLoop(loop, baseToTop, consumed));
      }
    }

    handlerGroups.sort(
        java.util.Comparator.comparingInt(ImportedHandlerGroup::baseDepth).reversed());
    List<VmSnapshot.HandlerState> handlers =
        handlerGroups.stream()
            .flatMap(
                group -> {
                  int operandDepth = translatedDepth(consumed, group.baseDepth());
                  return group.handlers().stream()
                      .map(
                          handler ->
                              handlerState(
                                  handler.control(), operandDepth, handler.phase()));
                })
            .toList();
    finallyStates.sort(
        java.util.Comparator.comparingInt(ImportedFinally::baseDepth).reversed());
    List<VmSnapshot.FinallyState> finalizers =
        finallyStates.stream().map(ImportedFinally::state).toList();

    List<MooValue> ordinary = new ArrayList<>();
    for (int topIndex = 0; topIndex < topToBase.size(); topIndex++) {
      int baseDepth = topToBase.size() - topIndex - 1;
      if (!consumed[baseDepth]) {
        SuspendedStackSlot slot = topToBase.get(topIndex);
        if (slot.value().isEmpty()) {
          throw new IllegalArgumentException(
              "unclaimed v17 runtime control tag " + slot.controlTag());
        }
        ordinary.add(slot.value().orElseThrow());
      }
    }
    return new ImportedActivationStack(ordinary, handlers, finalizers, loops);
  }

  private static void importCatchGroup(
      ToastV17ProgramLayout.CatchGroup group,
      List<SuspendedStackSlot> baseToTop,
      boolean[] consumed,
      List<ImportedHandlerGroup> handlerGroups) {
    List<ImportedHandler> handlers = new ArrayList<>();
    switch (group.phase()) {
      case PROTECTED -> {
        for (int index = 0; index < group.clauses().size(); index++) {
          ToastV17ProgramLayout.ToastHandlerClause clause = group.clauses().get(index);
          requireExactValue(
              baseToTop,
              consumed,
              group.baseDepth() + index * 2,
              toastSelectorValue(clause.selector()),
              "catch selector");
          requireExactValue(
              baseToTop,
              consumed,
              group.baseDepth() + index * 2 + 1,
              new IntegerValue(clause.handlerLabelProgramCounter()),
              "catch handler label");
          handlers.add(
              new ImportedHandler(
                  group.clauseControls().get(index), VmSnapshot.HandlerPhase.TRY));
        }
        requireControl(
            baseToTop,
            consumed,
            group.markerDepth().orElseThrow(),
            7,
            group.clauses().size(),
            "catch marker");
        handlers.add(
            new ImportedHandler(group.ownerControl(), VmSnapshot.HandlerPhase.TRY));
      }
      case EXCEPT_CLAUSE ->
          handlers.add(
              new ImportedHandler(group.ownerControl(), VmSnapshot.HandlerPhase.CATCH));
      case EXPRESSION_FALLBACK -> {
        int active = group.activeClauseIndex().orElseThrow();
        handlers.add(
            new ImportedHandler(
                group.clauseControls().get(active), VmSnapshot.HandlerPhase.CATCH));
        handlers.add(
            new ImportedHandler(group.ownerControl(), VmSnapshot.HandlerPhase.CATCH));
      }
      case FINALLY_HANDLER -> throw new IllegalArgumentException("catch group is in finally phase");
    }
    handlerGroups.add(new ImportedHandlerGroup(group.baseDepth(), handlers));
  }

  private static VmSnapshot.HandlerState handlerState(
      ToastV17ProgramLayout.BantengHandlerControl control,
      int operandDepth,
      VmSnapshot.HandlerPhase phase) {
    return new VmSnapshot.HandlerState(control.specification(), operandDepth, phase);
  }

  private static MooValue toastSelectorValue(
      ToastV17ProgramLayout.ToastErrorSelector selector) {
    if (selector.catchesAny()) {
      return new IntegerValue(0);
    }
    return new ListValue(
        selector.errors().stream()
            .map(name -> ErrorValue.valueOf(name.toUpperCase(java.util.Locale.ROOT)))
            .toList());
  }

  private static VmSnapshot.FinallyState importFinallyState(
      ToastV17ProgramLayout.FinallyContinuation continuation,
      MooValue reasonValue,
      MooValue value) {
    if (!(reasonValue instanceof IntegerValue reason)) {
      throw new IllegalArgumentException("v17 finally reason is not an integer");
    }
    return switch (Math.toIntExact(reason.value())) {
      case 0 -> new VmSnapshot.FallThrough(continuation.ownerControl().specification().endTarget());
      case 1 ->
          value instanceof ListValue exception
              ? new VmSnapshot.Raise(exception)
              : throwFinally("v17 raise continuation is not a tuple");
      case 2 -> new VmSnapshot.Uncaught(value);
      case 3 -> new VmSnapshot.Return(value);
      case 4 -> throw new IllegalArgumentException("v17 FIN_ABORT continuation is unsupported");
      case 5 -> importFinallyExit(continuation, value);
      default -> throw new IllegalArgumentException("unknown v17 finally reason " + reason.value());
    };
  }

  private static VmSnapshot.FinallyState importFinallyExit(
      ToastV17ProgramLayout.FinallyContinuation continuation, MooValue value) {
    if (!(value instanceof ListValue exit)
        || exit.size() != 2
        || !(exit.elements().get(0) instanceof IntegerValue depth)
        || !(exit.elements().get(1) instanceof IntegerValue programCounter)) {
      throw new IllegalArgumentException("v17 exit continuation requires [depth, pc]");
    }
    ToastV17ProgramLayout.ToastExitTarget target =
        continuation.resolveToastExitTarget(
            depth.value(), programCounter.value());
    return new VmSnapshot.Exit(
        target.bantengOperandDepth(),
        target.bantengControl().targetInstructionPointer());
  }

  private static VmSnapshot.FinallyState throwFinally(String message) {
    throw new IllegalArgumentException(message);
  }

  private static VmSnapshot.LoopState importCollectionLoop(
      ToastV17ProgramLayout.CollectionLoop loop,
      List<SuspendedStackSlot> baseToTop,
      boolean[] consumed) {
    MooValue base =
        requireValue(baseToTop, consumed, loop.baseDepth(), "collection loop base");
    SuspendedStackSlot iterator =
        requireSlot(baseToTop, consumed, loop.iteratorDepth(), "collection loop iterator");
    VmSnapshot.CollectionKind kind = collectionKind(loop, base);
    Optional<MooValue> next;
    if (kind == VmSnapshot.CollectionKind.MAP && iterator.controlTag() == 6) {
      next = Optional.empty();
    } else if (iterator.value().isPresent()) {
      next = iterator.value();
    } else {
      throw new IllegalArgumentException("invalid v17 collection loop iterator");
    }
    return new VmSnapshot.CollectionLoop(kind, base, next);
  }

  private static VmSnapshot.CollectionKind collectionKind(
      ToastV17ProgramLayout.CollectionLoop loop, MooValue base) {
    VmSnapshot.CollectionKind actual =
        switch (base) {
          case ListValue ignored -> VmSnapshot.CollectionKind.LIST;
          case moo.value.MooValue.StringValue ignored -> VmSnapshot.CollectionKind.STRING;
          case moo.value.MooValue.MapValue ignored -> VmSnapshot.CollectionKind.MAP;
          default -> throw new IllegalArgumentException("unsupported v17 collection loop base");
        };
    if (loop.staticallyKnownKind().isPresent()
        && !loop.staticallyKnownKind().orElseThrow().name().equals(actual.name())) {
      throw new IllegalArgumentException("v17 collection loop kind disagrees with its base");
    }
    return actual;
  }

  private static VmSnapshot.LoopState importRangeLoop(
      ToastV17ProgramLayout.RangeLoop loop,
      List<SuspendedStackSlot> baseToTop,
      boolean[] consumed) {
    MooValue next = requireValue(baseToTop, consumed, loop.nextDepth(), "range next");
    MooValue end = requireValue(baseToTop, consumed, loop.endDepth(), "range end");
    VmSnapshot.RangeKind kind =
        switch (next) {
          case IntegerValue ignored when end instanceof IntegerValue -> VmSnapshot.RangeKind.INTEGER;
          case moo.value.MooValue.ObjectValue ignored
              when end instanceof moo.value.MooValue.ObjectValue -> VmSnapshot.RangeKind.OBJECT;
          default -> throw new IllegalArgumentException("v17 range values have different kinds");
        };
    if (loop.staticallyKnownKind().isPresent()
        && !loop.staticallyKnownKind().orElseThrow().name().equals(kind.name())) {
      throw new IllegalArgumentException("v17 range kind disagrees with its values");
    }
    return new VmSnapshot.RangeLoop(kind, next, end);
  }

  private static void requireControl(
      List<SuspendedStackSlot> baseToTop,
      boolean[] consumed,
      int depth,
      int tag,
      long controlValue,
      String description) {
    SuspendedStackSlot actual = requireSlot(baseToTop, consumed, depth, description);
    if (actual.controlTag() != tag || actual.controlValue() != controlValue) {
      throw new IllegalArgumentException(description + " disagrees with structural layout");
    }
  }

  private static void requireExactValue(
      List<SuspendedStackSlot> baseToTop,
      boolean[] consumed,
      int depth,
      MooValue expected,
      String description) {
    MooValue actual = requireValue(baseToTop, consumed, depth, description);
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException(description + " disagrees with structural layout");
    }
  }

  private static MooValue requireValue(
      List<SuspendedStackSlot> baseToTop,
      boolean[] consumed,
      int depth,
      String description) {
    SuspendedStackSlot slot = requireSlot(baseToTop, consumed, depth, description);
    return slot.value().orElseThrow(
        () -> new IllegalArgumentException(description + " is not a MOO value"));
  }

  private static SuspendedStackSlot requireSlot(
      List<SuspendedStackSlot> baseToTop,
      boolean[] consumed,
      int depth,
      String description) {
    if (depth < 0 || depth >= baseToTop.size() || consumed[depth]) {
      throw new IllegalArgumentException("invalid or overlapping " + description + " depth");
    }
    consumed[depth] = true;
    return baseToTop.get(depth);
  }

  private static int translatedDepth(boolean[] consumed, int rawDepth) {
    int result = 0;
    for (int depth = 0; depth < rawDepth; depth++) {
      if (!consumed[depth]) {
        result++;
      }
    }
    return result;
  }

  synchronized List<DurableTask> durableTasks() {
    Map<Long, TimedWork> durable = new TreeMap<>(timedWork);
    checkpointingWork.forEach(
        (taskId, work) -> {
          if (durable.putIfAbsent(taskId, work) != null) {
            throw new IllegalStateException("task has duplicate durable state " + taskId);
          }
        });
    return durable.values().stream().map(PublicationScheduler::durableTask).toList();
  }

  private static DurableTask durableTask(TimedWork timed) {
    if (timed.durableTask().isPresent()) {
      return timed.durableTask().orElseThrow();
    }
    if (timed.wakeResult().isPresent()) {
      return durableSuspension(timed);
    }
    SuspendedWork work = timed.work();
    VmSnapshot snapshot = work.snapshot();
    if (work.program().source().isEmpty()) {
      throw new IllegalStateException("queued fork has no durable source");
    }
    return new QueuedTask(
        work.taskId(),
        Math.floorDiv(timed.scheduledEpochMilli(), 1_000L),
        work.program().source(),
        snapshot.initialLocals(),
        snapshot.initialProgrammer(),
        snapshot.initialVerbLocation(),
        work.taskPlayer(),
        snapshot.frames().isEmpty() || snapshot.frames().getFirst().debug(),
        snapshot.frames().isEmpty() || snapshot.frames().getFirst().threadMode());
  }

  Result resumeTask(
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
    if (!taskRegistry.mayControl(taskId, world, programmer)) {
      return Result.error(
          taskRegistry.contains(taskId) ? ErrorValue.E_PERM : ErrorValue.E_INVARG);
    }
    MooValue value = arguments.size() == 2 ? arguments.get(1) : new IntegerValue(0);
    TimedWork resumed;
    synchronized (this) {
      resumed = timedWork.get(taskId);
      if (resumed == null || resumed.wakeResult().isEmpty()) {
        return Result.error(ErrorValue.E_INVARG);
      }
      if (!timedWork.remove(taskId, resumed)) {
        return Result.error(ErrorValue.E_INVARG);
      }
      TimedWork readyToResume =
          new TimedWork(
              resumed.work(),
              System.currentTimeMillis(),
              Optional.of(Result.value(value)),
              resumed.durableTask());
      if (checkpointingWork.putIfAbsent(taskId, readyToResume) != null) {
        throw new IllegalStateException("task already has checkpointing state " + taskId);
      }
      resumed = readyToResume;
    }
    enqueueWake(resumed.work(), resumed.wakeResult().orElseThrow());
    return Result.value(new IntegerValue(0));
  }

  private static SuspendedTask durableSuspension(TimedWork timed) {
    SuspendedWork work = timed.work();
    if (work.continuation().isPresent()) {
      throw new IllegalStateException(
          "suspended task " + work.taskId() + " has a non-v17 runtime continuation");
    }
    VmSnapshot snapshot = work.snapshot();
    if (!snapshot.anonymousCollectionDeferrals().isEmpty()) {
      throw new IllegalStateException(
          "suspended task has anonymous collection deferrals with no v17 representation");
    }
    if (snapshot.frames().isEmpty()) {
      throw new IllegalStateException("suspended task has no activation frames");
    }
    List<SuspendedActivation> rootToCurrent = new ArrayList<>();
    List<VmSnapshot.Frame> frames = new ArrayList<>(snapshot.frames());
    Collections.reverse(frames);
    for (VmSnapshot.Frame frame : frames) {
      rootToCurrent.add(exportActivation(frame, work.taskPlayer()));
    }
    Result wake = timed.wakeResult().orElseThrow();
    MooValue resumeValue =
        wake.error().<MooValue>map(error -> error).orElseGet(() -> wake.value().orElseThrow());
    return new SuspendedTask(
        work.taskId(),
        Math.floorDiv(timed.scheduledEpochMilli(), 1_000L),
        resumeValue,
        snapshot.taskLocal(),
        -1,
        0,
        snapshot.maxStackDepth(),
        Optional.empty(),
        rootToCurrent);
  }

  private static List<SuspendedStackSlot> exportStructuralStack(
      ToastV17ProgramLayout.StructuralStackShape shape, VmSnapshot.Frame frame) {
    SuspendedStackSlot[] baseToTop = new SuspendedStackSlot[shape.postArgumentDepth()];
    boolean[] structural = new boolean[baseToTop.length];
    List<ImportedHandlerGroup> expectedHandlerGroups = new ArrayList<>();
    List<ToastV17ProgramLayout.StructuralStackEntry> innerToOuter =
        new ArrayList<>(shape.entriesBaseToTop());
    innerToOuter.sort(
        java.util.Comparator.comparingInt(
                ToastV17ProgramLayout.StructuralStackEntry::baseDepth)
            .reversed());
    int finallyIndex = 0;
    Map<Integer, VmSnapshot.LoopState> remainingLoops =
        new LinkedHashMap<>(frame.loops());

    for (ToastV17ProgramLayout.StructuralStackEntry entry : innerToOuter) {
      switch (entry) {
        case ToastV17ProgramLayout.CatchGroup group -> {
          if (group.phase() == ToastV17ProgramLayout.StructuralPhase.PROTECTED) {
            for (int index = 0; index < group.clauses().size(); index++) {
              ToastV17ProgramLayout.ToastHandlerClause clause = group.clauses().get(index);
              putValue(
                  baseToTop,
                  structural,
                  group.baseDepth() + index * 2,
                  toastSelectorValue(clause.selector()),
                  "catch selector");
              putValue(
                  baseToTop,
                  structural,
                  group.baseDepth() + index * 2 + 1,
                  new IntegerValue(clause.handlerLabelProgramCounter()),
                  "catch handler label");
            }
            putControl(
                baseToTop,
                structural,
                group.markerDepth().orElseThrow(),
                7,
                group.clauses().size(),
                "catch marker");
          }
          expectedHandlerGroups.add(exportedCatchHandlers(group));
        }
        case ToastV17ProgramLayout.ProtectedFinally protectedFinally -> {
          putControl(
              baseToTop,
              structural,
              protectedFinally.markerDepth(),
              8,
              protectedFinally.handlerLabelProgramCounter(),
              "protected finally");
          expectedHandlerGroups.add(
              new ImportedHandlerGroup(
                  protectedFinally.baseDepth(),
                  List.of(
                      new ImportedHandler(
                          protectedFinally.ownerControl(),
                          VmSnapshot.HandlerPhase.TRY))));
        }
        case ToastV17ProgramLayout.FinallyContinuation continuation -> {
          if (finallyIndex >= frame.finallyStates().size()) {
            throw new IllegalStateException("missing Banteng finally continuation");
          }
          exportFinallyState(
              continuation,
              frame.finallyStates().get(finallyIndex++),
              baseToTop,
              structural);
        }
        case ToastV17ProgramLayout.CollectionLoop loop -> {
          VmSnapshot.LoopState state = remainingLoops.remove(loop.control().instructionIndex());
          if (!(state instanceof VmSnapshot.CollectionLoop collection)) {
            throw new IllegalStateException("missing Banteng collection-loop state");
          }
          exportCollectionLoop(loop, collection, baseToTop, structural);
        }
        case ToastV17ProgramLayout.RangeLoop loop -> {
          VmSnapshot.LoopState state = remainingLoops.remove(loop.control().instructionIndex());
          if (!(state instanceof VmSnapshot.RangeLoop range)) {
            throw new IllegalStateException("missing Banteng range-loop state");
          }
          exportRangeLoop(loop, range, baseToTop, structural);
        }
      }
    }
    if (finallyIndex != frame.finallyStates().size()) {
      throw new IllegalStateException("unclaimed Banteng finally continuation");
    }
    if (!remainingLoops.isEmpty()) {
      throw new IllegalStateException("unclaimed Banteng loop state");
    }
    validateExportedHandlers(expectedHandlerGroups, frame.handlers(), structural);
    fillOrdinarySlots(baseToTop, structural, frame.operandStack());
    List<SuspendedStackSlot> topToBase = new ArrayList<>(List.of(baseToTop));
    Collections.reverse(topToBase);
    return List.copyOf(topToBase);
  }

  private static ImportedHandlerGroup exportedCatchHandlers(
      ToastV17ProgramLayout.CatchGroup group) {
    List<ImportedHandler> handlers = new ArrayList<>();
    switch (group.phase()) {
      case PROTECTED -> {
        group.clauseControls().forEach(
            control ->
                handlers.add(
                    new ImportedHandler(control, VmSnapshot.HandlerPhase.TRY)));
        handlers.add(
            new ImportedHandler(group.ownerControl(), VmSnapshot.HandlerPhase.TRY));
      }
      case EXCEPT_CLAUSE ->
          handlers.add(
              new ImportedHandler(group.ownerControl(), VmSnapshot.HandlerPhase.CATCH));
      case EXPRESSION_FALLBACK -> {
        handlers.add(
            new ImportedHandler(
                group.clauseControls().get(group.activeClauseIndex().orElseThrow()),
                VmSnapshot.HandlerPhase.CATCH));
        handlers.add(
            new ImportedHandler(group.ownerControl(), VmSnapshot.HandlerPhase.CATCH));
      }
      case FINALLY_HANDLER -> throw new IllegalStateException("catch group is in finally phase");
    }
    return new ImportedHandlerGroup(group.baseDepth(), handlers);
  }

  private static void validateExportedHandlers(
      List<ImportedHandlerGroup> groups,
      List<VmSnapshot.HandlerState> actualHandlers,
      boolean[] structural) {
    groups.sort(
        java.util.Comparator.comparingInt(ImportedHandlerGroup::baseDepth).reversed());
    int actualIndex = 0;
    for (ImportedHandlerGroup group : groups) {
      int operandDepth = translatedDepth(structural, group.baseDepth());
      for (ImportedHandler expected : group.handlers()) {
        if (actualIndex >= actualHandlers.size()) {
          throw new IllegalStateException("missing Banteng handler state");
        }
        VmSnapshot.HandlerState actual = actualHandlers.get(actualIndex++);
        if (!actual.specification().equals(expected.control().specification())
            || actual.operandDepth() != operandDepth
            || actual.phase() != expected.phase()) {
          throw new IllegalStateException("Banteng handler disagrees with v17 structure");
        }
      }
    }
    if (actualIndex != actualHandlers.size()) {
      throw new IllegalStateException("unclaimed Banteng handler state");
    }
  }

  private static void fillOrdinarySlots(
      SuspendedStackSlot[] baseToTop,
      boolean[] structural,
      List<MooValue> operandTopToBase) {
    int ordinaryCount = 0;
    for (boolean occupied : structural) {
      if (!occupied) {
        ordinaryCount++;
      }
    }
    if (ordinaryCount != operandTopToBase.size()) {
      throw new IllegalStateException(
          "Banteng operand depth "
              + operandTopToBase.size()
              + " disagrees with v17 ordinary depth "
              + ordinaryCount);
    }
    int operandIndex = operandTopToBase.size() - 1;
    for (int depth = 0; depth < baseToTop.length; depth++) {
      if (!structural[depth]) {
        baseToTop[depth] =
            new SuspendedStackSlot(
                Optional.of(operandTopToBase.get(operandIndex--)), -1, 0);
      }
    }
  }

  private static void putValue(
      SuspendedStackSlot[] baseToTop,
      boolean[] structural,
      int depth,
      MooValue value,
      String description) {
    putSlot(
        baseToTop,
        structural,
        depth,
        new SuspendedStackSlot(Optional.of(value), -1, 0),
        description);
  }

  private static void putControl(
      SuspendedStackSlot[] baseToTop,
      boolean[] structural,
      int depth,
      int tag,
      long value,
      String description) {
    putSlot(
        baseToTop,
        structural,
        depth,
        new SuspendedStackSlot(Optional.empty(), tag, value),
        description);
  }

  private static void putSlot(
      SuspendedStackSlot[] baseToTop,
      boolean[] structural,
      int depth,
      SuspendedStackSlot slot,
      String description) {
    if (depth < 0 || depth >= baseToTop.length || structural[depth]) {
      throw new IllegalStateException("invalid or overlapping " + description + " depth");
    }
    baseToTop[depth] = slot;
    structural[depth] = true;
  }

  private static void exportFinallyState(
      ToastV17ProgramLayout.FinallyContinuation continuation,
      VmSnapshot.FinallyState state,
      SuspendedStackSlot[] baseToTop,
      boolean[] structural) {
    long reason;
    MooValue value;
    switch (state) {
      case VmSnapshot.FallThrough fallThrough -> {
        if (fallThrough.target()
            != continuation.ownerControl().specification().endTarget()) {
          throw new IllegalStateException("Banteng finally fall-through target disagrees");
        }
        reason = 0;
        value = new IntegerValue(0);
      }
      case VmSnapshot.Raise raise -> {
        reason = 1;
        value = raise.exception();
      }
      case VmSnapshot.Uncaught uncaught -> {
        reason = 2;
        value = uncaught.value();
      }
      case VmSnapshot.Return returned -> {
        reason = 3;
        value = returned.value();
      }
      case VmSnapshot.Exit exit -> {
        reason = 5;
        ToastV17ProgramLayout.ToastExitTarget target =
            continuation.resolveBantengExitTarget(exit.operandDepth(), exit.target());
        value =
            new ListValue(
                List.of(
                    new IntegerValue(target.targetStackDepth()),
                    new IntegerValue(target.targetProgramCounter())));
      }
    }
    putValue(
        baseToTop,
        structural,
        continuation.reasonDepth(),
        new IntegerValue(reason),
        "finally reason");
    putValue(
        baseToTop,
        structural,
        continuation.valueDepth(),
        value,
        "finally value");
  }

  private static void exportCollectionLoop(
      ToastV17ProgramLayout.CollectionLoop shape,
      VmSnapshot.CollectionLoop state,
      SuspendedStackSlot[] baseToTop,
      boolean[] structural) {
    if (shape.staticallyKnownKind().isPresent()
        && !shape.staticallyKnownKind().orElseThrow().name().equals(state.kind().name())) {
      throw new IllegalStateException("Banteng collection-loop kind disagrees with v17 structure");
    }
    putValue(
        baseToTop,
        structural,
        shape.baseDepth(),
        state.base(),
        "collection loop base");
    if (state.kind() == VmSnapshot.CollectionKind.MAP && state.next().isEmpty()) {
      putControl(
          baseToTop,
          structural,
          shape.iteratorDepth(),
          6,
          0,
          "collection loop iterator");
    } else {
      putValue(
          baseToTop,
          structural,
          shape.iteratorDepth(),
          state.next().orElseThrow(
              () -> new IllegalStateException("missing collection-loop cursor")),
          "collection loop iterator");
    }
  }

  private static void exportRangeLoop(
      ToastV17ProgramLayout.RangeLoop shape,
      VmSnapshot.RangeLoop state,
      SuspendedStackSlot[] baseToTop,
      boolean[] structural) {
    if (shape.staticallyKnownKind().isPresent()
        && !shape.staticallyKnownKind().orElseThrow().name().equals(state.kind().name())) {
      throw new IllegalStateException("Banteng range-loop kind disagrees with v17 structure");
    }
    putValue(
        baseToTop, structural, shape.nextDepth(), state.next(), "range next");
    putValue(baseToTop, structural, shape.endDepth(), state.end(), "range end");
  }

  private static SuspendedActivation exportActivation(VmSnapshot.Frame frame, long taskPlayer) {
    if (!frame.indexCollections().isEmpty()
        || frame.createReturnOverride().isPresent()
        || frame.recycleTarget().isPresent()
        || frame.anonymousRecycleTarget().isPresent()
        || frame.moveObject().isPresent()
        || frame.moveDestination().isPresent()
        || frame.movePosition().isPresent()) {
      throw new IllegalStateException("VM frame has no direct v17 activation representation");
    }
    int callIndex = Math.subtractExact(frame.instructionPointer(), 1);
    Instruction call = frame.program().instructions().get(callIndex);
    if ((call.opcode() != Opcode.CALL && call.opcode() != Opcode.CALL_VERB)
        || call.astPath().isEmpty()) {
      throw new IllegalStateException("suspended VM is not positioned after a structural call");
    }
    BytecodeProgram canonical = new MooCompiler().compile(frame.program().source());
    if (callIndex >= canonical.instructions().size()) {
      throw new IllegalStateException("suspended call is absent from canonical source");
    }
    Instruction canonicalCall = canonical.instructions().get(callIndex);
    if (canonicalCall.opcode() != call.opcode() || canonicalCall.astPath().isEmpty()) {
      throw new IllegalStateException("suspended call disagrees with canonical source structure");
    }
    ToastV17ProgramLayout layout = new ToastV17ProgramLayout();
    List<ToastV17ProgramLayout.CallBoundary> boundaries =
        layout.callBoundaries(frame.program().source(), -1).stream()
            .filter(boundary -> boundary.astPath().equals(canonicalCall.astPath().orElseThrow()))
            .toList();
    if (boundaries.size() != 1) {
      throw new IllegalStateException(
          "suspended call resolved " + boundaries.size() + " v17 boundaries");
    }
    ToastV17ProgramLayout.CallBoundary boundary = boundaries.getFirst();
    ToastV17ProgramLayout.StructuralStackShape structural =
        layout.resolveStructuralStack(frame.program().source(), -1, canonical, boundary);
    List<SuspendedStackSlot> stack = exportStructuralStack(structural, frame);
    Map<String, Optional<MooValue>> locals = new LinkedHashMap<>();
    frame.locals().forEach((name, value) -> locals.put(name, Optional.of(value)));
    String verb =
        frame.locals().get("verb") instanceof moo.value.MooValue.StringValue value
            ? new String(value.bytes(), StandardCharsets.ISO_8859_1)
            : "";
    return new SuspendedActivation(
        17,
        frame.program().source(),
        locals,
        stack,
        frame.receiver(),
        frame.verbLocation(),
        frame.threadMode(),
        taskPlayer,
        frame.programmer(),
        true,
        verb,
        verb,
        Optional.empty(),
        boundary.programCounter(),
        0,
        boundary.errorProgramCounter());
  }

  List<String> submit(MooRuntime.RuntimeRequest request) {
    CompletableFuture<List<String>> published = new CompletableFuture<>();
    synchronized (this) {
      ensureOpen();
      long taskId = nextTaskId++;
      long ticket = nextTicket++;
      if (request.operation() == MooRuntime.Operation.LINE) {
        long player = runtime.connectionPlayer(request.connectionId()).orElse(-1);
        if (player >= 0) {
          lastInputTasks.put(player, taskId);
        }
      }
      ingress.put(taskId, published);
      ready.add(Entry.runtime(ticket, taskId, MooRuntime.RuntimeContinuation.ingress(request)));
      dispatch();
    }
    try {
      return published.join();
    } catch (CompletionException failure) {
      Throwable cause = failure.getCause();
      if (cause instanceof RuntimeException runtimeFailure) {
        throw runtimeFailure;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw failure;
    }
  }

  synchronized void enqueueDetached(MooRuntime.RuntimeRequest request) {
    ensureOpen();
    long taskId = nextTaskId++;
    if (request.operation() == MooRuntime.Operation.LINE) {
      long player = runtime.connectionPlayer(request.connectionId()).orElse(-1);
      if (player >= 0) {
        lastInputTasks.put(player, taskId);
      }
    }
    ready.add(
        Entry.runtime(
            nextTicket++, taskId, MooRuntime.RuntimeContinuation.ingress(request)));
    dispatch();
  }

  synchronized boolean isLastInputTask(long taskId) {
    return lastInputTasks.containsValue(taskId);
  }

  synchronized OptionalLong lastInputPlayer(long taskId) {
    for (Map.Entry<Long, Long> entry : lastInputTasks.entrySet()) {
      if (entry.getValue() == taskId) {
        return OptionalLong.of(entry.getKey());
      }
    }
    return OptionalLong.empty();
  }

  private void dispatch() {
    if (closed) {
      return;
    }
    while (!ready.isEmpty() && hasExecutorCapacity()) {
      Entry entry = ready.remove();
      executor.execute(() -> executeAttempt(entry));
    }
  }

  private boolean hasExecutorCapacity() {
    return executor.getActiveCount() < workers || executor.getQueue().remainingCapacity() > 0;
  }

  private void executeAttempt(Entry entry) {
    @Nullable TaskSegmentEvent segment = null;
    if (FlightRecorder.isInitialized()) {
      segment = new TaskSegmentEvent();
      segment.taskId = entry.taskId();
      segment.ticket = entry.ticket();
      segment.begin();
    }
    WorldTxn transaction = committedWorld.begin();
    MooRuntime.AttemptContext context = null;
    SegmentResult result = null;
    Throwable failure = null;
    try {
      context = runtime.openAttempt(transaction);
      result = executeSegment(entry, transaction);
      context = runtime.finishAttempt();
      transaction = context.world;
    } catch (Throwable caught) {
      failure = caught;
      runtime.abandonAttempt();
    }
    if (segment != null) {
      segment.commit();
    }
    complete(
        new Attempt(
            entry,
            transaction,
            Optional.ofNullable(context),
            Optional.ofNullable(result),
            Optional.ofNullable(failure)));
  }

  private SegmentResult executeSegment(Entry start, WorldTxn transaction) {
    Optional<MooRuntime.RuntimeContinuation> continuation = start.continuation();
    Optional<VmSnapshot> completedVm =
        start.kind() == EntryKind.RUNTIME_TRANSITION ? start.snapshot() : Optional.empty();
    Optional<BytecodeProgram> program = start.program();
    Optional<VmSnapshot> snapshot =
        start.kind() == EntryKind.VM_SEGMENT ? start.snapshot() : Optional.empty();
    long taskPlayer = start.taskPlayer();
    boolean startingBackground = start.startingBackground();
    Optional<Result> wakeResult = start.wakeResult();
    List<PendingFork> pendingForks = new ArrayList<>();
    boolean aborted = false;
    Optional<VmSnapshot> timeoutSnapshot = Optional.empty();

    while (true) {
      if (program.isEmpty()) {
        Optional<MooRuntime.RuntimeTransition> executingTransition =
            continuation.flatMap(MooRuntime.RuntimeContinuation::transition);
        MooRuntime.RuntimeStep step =
            runtime.execute(continuation.orElseThrow(), completedVm);
        if (step.output().isPresent()) {
          if (executingTransition
              .filter(
                  transition ->
                      transition == MooRuntime.RuntimeTransition.ANONYMOUS_FINALIZATION_RETURN)
              .isPresent()) {
            runtime.collectAfterAnonymousFinalization(
                taskRegistry.snapshotsExcluding(start.taskId()));
          }
          return SegmentResult.returned(
              step.output().orElseThrow(),
              taskPlayer,
              pendingForks,
              aborted,
              timeoutSnapshot);
        }
        program = step.program();
        snapshot = step.snapshot();
        taskPlayer = step.taskPlayer();
        continuation = step.continuation();
        startingBackground = false;
        wakeResult = Optional.empty();
      }

      VmState state =
          startingBackground
              ? runtime.startBackgroundTask(snapshot.orElseThrow())
              : VmState.restore(snapshot.orElseThrow());
      if (wakeResult.isPresent()) {
        Result completion = wakeResult.orElseThrow();
        if (state.outcome() == VmState.Outcome.FORKED) {
          state.continueAfterFork((IntegerValue) completion.value().orElseThrow());
        } else if (completion.error().isPresent()) {
          runtime.vm().resumeWithError(state, completion, transaction);
        } else {
          state.resume(completion.value().orElseThrow());
        }
      }
      startingBackground = false;
      wakeResult = Optional.empty();

      while (true) {
        runtime
            .vm()
            .execute(
                program.orElseThrow(),
                state,
                transaction,
                runtime.builtins(),
                start.taskId());
        if (state.outcome() != VmState.Outcome.ABORTED) {
          runtime.publishVmState(
              state,
              taskPlayer,
              continuation
                  .flatMap(MooRuntime.RuntimeContinuation::transition)
                  .filter(
                      transition ->
                          transition
                                  == MooRuntime.RuntimeTransition.ANONYMOUS_FINALIZATION_RETURN
                              || transition
                                  == MooRuntime.RuntimeTransition.WAIF_FINALIZATION_RETURN
                              || transition
                                  == MooRuntime.RuntimeTransition.WAIF_BACKGROUND_FINALIZATION_RETURN)
                  .isEmpty(),
              taskRegistry.snapshotsExcluding(start.taskId()));
        }
        if (state.outcome() == VmState.Outcome.FORKED) {
          VmSnapshot.Fork fork = state.snapshot().forkRequest().orElseThrow();
          VmState child =
              new VmState(
                  fork.locals(),
                  fork.programmer(),
                  fork.verbLocation(),
                  MooRuntime.DEFAULT_BACKGROUND_TICKS,
                  MooRuntime.DEFAULT_BACKGROUND_SECONDS,
                  state.snapshot().maxStackDepth(),
                  fork.debug());
          pendingForks.add(
              new PendingFork(
                  fork.program(), child.snapshot(), taskPlayer, fork.delaySeconds()));
          return SegmentResult.boundary(
              program.orElseThrow(),
              state.snapshot(),
              taskPlayer,
              continuation,
              Optional.empty(),
              pendingForks);
        }
        if (state.outcome() == VmState.Outcome.PENDING_BUILTIN) {
          if (!start.irrevocableAuthorized()) {
            return SegmentResult.irrevocable(pendingForks);
          }
          runtime
              .vm()
              .authorizePendingBuiltin(
                  state, transaction, runtime.builtins(), start.taskId());
          runtime.publishVmState(
              state,
              taskPlayer,
              continuation
                  .flatMap(MooRuntime.RuntimeContinuation::transition)
                  .filter(
                      transition ->
                          transition
                                  == MooRuntime.RuntimeTransition.ANONYMOUS_FINALIZATION_RETURN
                              || transition
                                  == MooRuntime.RuntimeTransition.WAIF_FINALIZATION_RETURN
                              || transition
                                  == MooRuntime.RuntimeTransition.WAIF_BACKGROUND_FINALIZATION_RETURN)
                  .isEmpty(),
              taskRegistry.snapshotsExcluding(start.taskId()));
          continue;
        }

        VmSnapshot completed = state.snapshot();
        if (state.outcome() == VmState.Outcome.ABORTED) {
          aborted = true;
          boolean timeoutHandlerAborted =
              continuation
                  .flatMap(MooRuntime.RuntimeContinuation::transition)
                  .filter(transition -> transition == MooRuntime.RuntimeTransition.TASK_TIMEOUT_RETURN)
                  .isPresent();
          if (!timeoutHandlerAborted) {
            timeoutSnapshot = Optional.of(completed);
          }
        }
        if ((state.outcome() == VmState.Outcome.RETURNED
                || state.outcome() == VmState.Outcome.ERRORED
                || state.outcome() == VmState.Outcome.ABORTED)
            && continuation.isPresent()) {
          completedVm = Optional.of(completed);
          program = Optional.empty();
          snapshot = Optional.empty();
          break;
        }
        if (state.outcome() == VmState.Outcome.ERRORED) {
          MooRuntime.RuntimeStep uncaught = runtime.startUncaughtError(state);
          if (uncaught.output().isPresent()) {
            return SegmentResult.returned(
                uncaught.output().orElseThrow(),
                taskPlayer,
                pendingForks,
                aborted,
                timeoutSnapshot);
          }
          program = uncaught.program();
          snapshot = uncaught.snapshot();
          taskPlayer = uncaught.taskPlayer();
          continuation = uncaught.continuation();
          break;
        }
        if (state.outcome() == VmState.Outcome.RETURNED
            || state.outcome() == VmState.Outcome.ABORTED) {
          return SegmentResult.returned(
              completed.output(), taskPlayer, pendingForks, aborted, timeoutSnapshot);
        }
        if (state.outcome() == VmState.Outcome.SUSPENDED) {
          return SegmentResult.boundary(
              program.orElseThrow(),
              completed,
              taskPlayer,
              continuation,
              state.hostWork(),
              pendingForks);
        }
        throw new IllegalStateException("VM segment ended without an observable boundary");
      }
    }
  }

  private void complete(Attempt attempt) {
    boolean elected = false;
    synchronized (this) {
      if (completed.put(attempt.entry().ticket(), attempt) != null) {
        throw new IllegalStateException("duplicate completion ticket " + attempt.entry().ticket());
      }
      if (!publicationDraining) {
        publicationDraining = true;
        elected = true;
      }
      dispatch();
    }
    if (elected) {
      drainPublications();
    }
  }

  private void drainPublications() {
    while (true) {
      Attempt attempt;
      synchronized (this) {
        attempt = completed.remove(nextPublicationTicket);
        if (attempt == null) {
          publicationDraining = false;
          dispatch();
          return;
        }
      }
      PublishedAttempt published = publishAttempt(attempt);
      if (published.retry() || published.authorizeIrrevocable()) {
        synchronized (this) {
          ready.add(
              published.authorizeIrrevocable()
                  ? attempt.entry().authorizeIrrevocable()
                  : attempt.entry());
          publicationDraining = false;
          dispatch();
        }
        return;
      }
      if (published.failure().isPresent()) {
        RootCompletion completion;
        synchronized (this) {
          completion = finishFailure(attempt.entry(), published.failure().orElseThrow());
        }
        completion.complete();
        continue;
      }
      SegmentResult result = attempt.result().orElseThrow();
      synchronized (this) {
        for (MooRuntime.RuntimeStep spawned : published.spawned()) {
          enqueueSpawned(spawned);
        }
        dispatch();
      }
      publishSegmentResultOutsideMonitor(attempt.entry(), result);
    }
  }

  private void publishSegmentResultOutsideMonitor(Entry start, SegmentResult result) {
    if (result.timeoutSnapshot().isPresent()) {
      synchronized (this) {
        nextPublicationTicket++;
        ready.add(
            Entry.runtime(
                nextTicket++,
                start.taskId(),
                MooRuntime.RuntimeContinuation.timeout(
                    result.timeoutSnapshot().orElseThrow(),
                    result.taskPlayer(),
                    result.output().orElseThrow())));
        dispatch();
      }
      return;
    }
    if (result.output().isPresent()) {
      RootCompletion completion;
      boolean completedAnonymousFinalization =
          start
              .continuation()
              .flatMap(MooRuntime.RuntimeContinuation::transition)
              .filter(
                  transition ->
                      transition == MooRuntime.RuntimeTransition.ANONYMOUS_FINALIZATION_RETURN)
              .isPresent();
      synchronized (this) {
        completion = finishSuccess(start, result.output().orElseThrow());
      }
      if (completedAnonymousFinalization) {
        releaseFinalizationBlocked();
      }
      completion.complete();
      return;
    }
    Entry boundary =
        Entry.vm(
            start.ticket(),
            start.taskId(),
            result.program().orElseThrow(),
            result.snapshot().orElseThrow(),
            result.taskPlayer(),
            result.continuation());
    publishVmCompletionOutsideMonitor(
        boundary,
        result.snapshot().orElseThrow(),
        result.hostWork(),
        result.pendingForks());
  }

  private PublishedAttempt publishAttempt(Attempt attempt) {
    if (taskRegistry.discardIfCanceled(attempt.entry().taskId())) {
      attempt.transaction().close();
      return PublishedAttempt.failed(
          new CancellationException("task " + attempt.entry().taskId() + " was killed"));
    }
    if (attempt.failure().isPresent()) {
      attempt.transaction().close();
      return PublishedAttempt.failed(attempt.failure().orElseThrow());
    }
    SegmentResult segment = attempt.result().orElseThrow();
    if (segment.aborted()) {
      attempt.transaction().close();
      return PublishedAttempt.published(List.of());
    }
    MooRuntime.AttemptContext context = attempt.context().orElseThrow();
    if (!runtime.sessionsAreCurrent(context)) {
      attempt.transaction().close();
      if (attempt.entry().irrevocableAuthorized()) {
        return PublishedAttempt.failed(
            new IllegalStateException("session changed after irrevocable authorization"));
      }
      return PublishedAttempt.retryAttempt();
    }
    if (segment.needsIrrevocable()) {
      WorldTxn.ValidationResult validation = attempt.transaction().validate();
      if (!validation.isValid()) {
        WorldConflictEvent conflict = new WorldConflictEvent();
        conflict.taskId = attempt.entry().taskId();
        conflict.ticket = attempt.entry().ticket();
        conflict.cause = "WORLD_TXN";
        conflict.commit();
        attempt.transaction().close();
        return PublishedAttempt.retryAttempt();
      }
      attempt.transaction().close();
      return PublishedAttempt.authorizeIrrevocableAttempt();
    }
    WorldTxn.CommitResult result = attempt.transaction().commit();
    if (!result.isCommitted()) {
      WorldConflictEvent conflict = new WorldConflictEvent();
      conflict.taskId = attempt.entry().taskId();
      conflict.ticket = attempt.entry().ticket();
      conflict.cause = "WORLD_TXN";
      conflict.commit();
      attempt.transaction().close();
      if (attempt.entry().irrevocableAuthorized()) {
        return PublishedAttempt.failed(
            new IllegalStateException("world changed after irrevocable authorization"));
      }
      return PublishedAttempt.retryAttempt();
    }
    attempt.transaction().close();
    try {
      runtime.publishAttempt(context, committedWorld.snapshot());
      return PublishedAttempt.published(runtime.takeSpawnedSteps(context));
    } catch (Throwable failure) {
      return PublishedAttempt.failed(failure);
    }
  }

  private void publishVmCompletionOutsideMonitor(
      Entry entry,
      VmSnapshot snapshot,
      Optional<Callable<Result>> hostWork,
      List<PendingFork> pendingForks) {
    synchronized (this) {
      checkpointingWork.remove(entry.taskId());
    }
    switch (snapshot.outcome()) {
      case SUSPENDED -> publishSuspension(entry, snapshot, hostWork);
      case FORKED -> {
        if (pendingForks.size() != 1) {
          throw new IllegalStateException("fork boundary requires exactly one child");
        }
        PendingFork fork = pendingForks.getFirst();
        Optional<TimedWork> timer;
        synchronized (this) {
          long childTaskId = nextTaskId++;
          VmSnapshot childState = fork.initialState();
          long scheduledStartMillis =
              Math.addExact(
                  System.currentTimeMillis(), Math.round(fork.delaySeconds() * 1_000.0));
          long scheduledStart = Math.floorDiv(scheduledStartMillis, 1_000L);
          taskRegistry.registerFork(
              childTaskId,
              scheduledStart,
              childState.initialProgrammer(),
              childState.initialVerbLocation(),
              childState.initialLocals(),
              childState);
          SuspendedWork child =
              new SuspendedWork(
                  childTaskId,
                  fork.program(),
                  childState,
                  fork.taskPlayer(),
                  Optional.empty(),
                  true);
          SuspendedWork parent =
              new SuspendedWork(
                  entry.taskId(),
                  entry.program().orElseThrow(),
                  snapshot,
                  entry.taskPlayer(),
                  entry.continuation(),
                  false);
          nextPublicationTicket++;
          ready.add(parent.wake(nextTicket++, Result.value(new IntegerValue(childTaskId))));
          if (fork.delaySeconds() == 0.0) {
            ready.add(child.ready(nextTicket++));
            timer = Optional.empty();
          } else {
            timer =
                Optional.of(
                    new TimedWork(
                        child, scheduledStartMillis, Optional.empty(), Optional.empty()));
          }
          dispatch();
        }
        timer.ifPresent(this::startTimer);
      }
      case PENDING_BUILTIN, RETURNED, ERRORED, ABORTED, RUNNING ->
          throw new IllegalStateException(
              "worker returned a non-boundary VM outcome: " + snapshot.outcome());
    }
  }

  private void publishSuspension(
      Entry entry,
      VmSnapshot snapshot,
      Optional<Callable<Result>> hostWork) {
    SuspendedWork suspended =
        new SuspendedWork(
            entry.taskId(),
            entry.program().orElseThrow(),
            snapshot,
            entry.taskPlayer(),
            entry.continuation(),
            true);
    synchronized (this) {
      nextPublicationTicket++;
    }
    if (snapshot.suspensionDelaySeconds().isPresent()) {
      double delaySeconds = snapshot.suspensionDelaySeconds().orElseThrow();
      long scheduledStartMillis =
          Double.isInfinite(delaySeconds)
              ? INDEFINITE_SUSPEND_EPOCH_MILLIS
              : Math.addExact(System.currentTimeMillis(), Math.round(delaySeconds * 1_000.0));
      taskRegistry.updateSuspended(
          entry.taskId(), Math.floorDiv(scheduledStartMillis, 1_000L), snapshot);
      if (Double.isInfinite(delaySeconds)) {
        synchronized (this) {
          registerTimer(
              new TimedWork(
                  suspended,
                  scheduledStartMillis,
                  Optional.of(Result.value(new IntegerValue(0))),
                  Optional.empty()));
        }
      } else if (delaySeconds == 0.0) {
        enqueueZeroDelayWake(suspended);
      } else {
        startTimer(
            new TimedWork(
                suspended,
                scheduledStartMillis,
                Optional.of(Result.value(new IntegerValue(0))),
                Optional.empty()));
      }
      return;
    }
    FutureTask<Result> submitted = new FutureTask<>(hostWork.orElseThrow());
    if (!taskRegistry.registerHost(
        entry.taskId(),
        snapshot,
        submitted)) {
      cancelWaiting(suspended);
      return;
    }
    if (backgroundWorkers == 0) {
      if (taskRegistry.claimHostTerminal(entry.taskId())) {
        enqueueWake(suspended, Result.error(ErrorValue.E_QUOTA));
      } else {
        cancelWaiting(suspended);
      }
      return;
    }
    try {
      executor.execute(submitted);
    } catch (RejectedExecutionException rejected) {
      if (taskRegistry.claimHostTerminal(entry.taskId())) {
        enqueueWake(suspended, Result.error(ErrorValue.E_QUOTA));
      } else {
        cancelWaiting(suspended);
      }
      return;
    }
    Thread.ofVirtual()
        .name("moo-host-wake-" + entry.taskId())
        .start(
            () -> {
              try {
                Result completion = submitted.get();
                if (completion.value().isPresent() == completion.error().isPresent()) {
                  throw new IllegalStateException(
                      "host completion requires exactly one value or MOO error");
                }
                if (taskRegistry.claimHostTerminal(entry.taskId())) {
                  enqueueWake(suspended, completion);
                } else {
                  cancelWaiting(suspended);
                }
              } catch (CancellationException canceled) {
                cancelWaiting(suspended);
              } catch (ExecutionException failed) {
                if (taskRegistry.claimHostTerminal(entry.taskId())) {
                  failWaiting(suspended, Objects.requireNonNull(failed.getCause()));
                } else {
                  cancelWaiting(suspended);
                }
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (taskRegistry.claimHostTerminal(entry.taskId())) {
                  failWaiting(suspended, interrupted);
                } else {
                  cancelWaiting(suspended);
                }
              } catch (Throwable failure) {
                if (taskRegistry.claimHostTerminal(entry.taskId())) {
                  failWaiting(suspended, failure);
                } else {
                  cancelWaiting(suspended);
                }
              }
            });
  }

  private void startTimer(TimedWork timed) {
    synchronized (this) {
      registerTimer(timed);
    }
    launchTimer(timed);
  }

  private void registerTimer(TimedWork timed) {
    if (timedWork.putIfAbsent(timed.work().taskId(), timed) != null) {
      throw new IllegalStateException("task already has a pending timer");
    }
    taskRegistry.registerCancellation(
        timed.work().taskId(),
        () -> cancelDurableWork(timed.work().taskId(), timed));
  }

  private synchronized void cancelDurableWork(long taskId, TimedWork expected) {
    timedWork.remove(taskId, expected);
    checkpointingWork.remove(taskId);
  }

  private void launchTimer(TimedWork timed) {
    long delayMillis =
        Math.max(
            0L,
            Math.subtractExact(timed.scheduledEpochMilli(), System.currentTimeMillis()));
    Thread.ofVirtual()
        .name("moo-timer-wake-" + timed.work().taskId())
        .start(
            () -> {
              try {
                TimeUnit.MILLISECONDS.sleep(delayMillis);
                synchronized (this) {
                  if (!timedWork.remove(timed.work().taskId(), timed)) {
                    return;
                  }
                  if (checkpointingWork.putIfAbsent(timed.work().taskId(), timed) != null) {
                    throw new IllegalStateException(
                        "task already has checkpointing state " + timed.work().taskId());
                  }
                }
                if (timed.wakeResult().isPresent()) {
                  enqueueWake(timed.work(), timed.wakeResult().orElseThrow());
                } else {
                  enqueueReady(timed.work());
                }
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                synchronized (this) {
                  timedWork.remove(timed.work().taskId(), timed);
                }
                failWaiting(timed.work(), interrupted);
              }
            });
  }

  private synchronized void enqueueWake(SuspendedWork work, Result completion) {
    if (!closed && !taskRegistry.discardIfCanceled(work.taskId())) {
      ready.add(work.wake(nextTicket++, completion));
      dispatch();
    }
  }

  private synchronized void enqueueZeroDelayWake(SuspendedWork work) {
    if (runtime.hasActiveAnonymousFinalization(committedWorld.snapshot())) {
      if (finalizationBlocked.putIfAbsent(work.taskId(), work) != null) {
        throw new IllegalStateException("task already blocked by anonymous finalization");
      }
      return;
    }
    ready.add(work.wake(nextTicket++, Result.value(new IntegerValue(0))));
    dispatch();
  }

  private synchronized void releaseFinalizationBlocked() {
    if (runtime.hasActiveAnonymousFinalization(committedWorld.snapshot())
        || finalizationBlocked.isEmpty()) {
      return;
    }
    for (SuspendedWork work : finalizationBlocked.values()) {
      if (!closed && !taskRegistry.discardIfCanceled(work.taskId())) {
        ready.add(work.wake(nextTicket++, Result.value(new IntegerValue(0))));
      }
    }
    finalizationBlocked.clear();
    dispatch();
  }

  private synchronized void enqueueReady(SuspendedWork work) {
    if (!closed && !taskRegistry.discardIfCanceled(work.taskId())) {
      ready.add(work.ready(nextTicket++));
      dispatch();
    }
  }

  private void cancelWaiting(SuspendedWork work) {
    CompletableFuture<List<String>> canceledIngress;
    synchronized (this) {
      if (!taskRegistry.discardIfCanceled(work.taskId())) {
        return;
      }
      canceledIngress = ingress.remove(work.taskId());
      dispatch();
    }
    if (canceledIngress != null) {
      canceledIngress.completeExceptionally(
          new CancellationException("task " + work.taskId() + " was killed"));
    }
  }

  private void failWaiting(SuspendedWork work, Throwable failure) {
    Entry failed;
    synchronized (this) {
      if (closed) {
        return;
      }
      failed = work.ready(nextTicket++);
    }
    complete(
        new Attempt(
            failed,
            committedWorld.begin(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(failure)));
  }

  private void enqueueSpawned(MooRuntime.RuntimeStep step) {
    if (step.output().isPresent()) {
      return;
    }
    ready.add(
        Entry.vm(
            nextTicket++,
            nextTaskId++,
            step.program().orElseThrow(),
            step.snapshot().orElseThrow(),
            step.taskPlayer(),
            step.continuation()));
  }

  private RootCompletion finishSuccess(Entry entry, List<String> output) {
    nextPublicationTicket++;
    checkpointingWork.remove(entry.taskId());
    taskRegistry.remove(entry.taskId());
    CompletableFuture<List<String>> future = ingress.remove(entry.taskId());
    return future == null ? RootCompletion.none() : RootCompletion.success(future, output);
  }

  private RootCompletion finishFailure(Entry entry, Throwable failure) {
    nextPublicationTicket++;
    checkpointingWork.remove(entry.taskId());
    taskRegistry.remove(entry.taskId());
    CompletableFuture<List<String>> future = ingress.remove(entry.taskId());
    return future == null
        ? RootCompletion.none()
        : RootCompletion.failure(future, failure);
  }

  synchronized long nextTicket() {
    return nextTicket;
  }

  synchronized long nextPublicationTicket() {
    return nextPublicationTicket;
  }

  int workers() {
    return workers;
  }

  int queueCapacity() {
    return executor.getQueue().size() + executor.getQueue().remainingCapacity();
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("publication scheduler is closed");
    }
  }

  @Override
  public void close() {
    List<CompletableFuture<List<String>>> pending;
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
      pending = List.copyOf(ingress.values());
      ingress.clear();
    }
    executor.shutdownNow();
    IllegalStateException failure = new IllegalStateException("publication scheduler is closed");
    pending.forEach(future -> future.completeExceptionally(failure));
  }

  boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return executor.awaitTermination(timeout, unit);
  }

  enum EntryKind {
    VM_SEGMENT,
    RUNTIME_TRANSITION
  }

  private record Entry(
      long ticket,
      long taskId,
      EntryKind kind,
      Optional<BytecodeProgram> program,
      Optional<VmSnapshot> snapshot,
      long taskPlayer,
      Optional<MooRuntime.RuntimeContinuation> continuation,
      Optional<Result> wakeResult,
      boolean startingBackground,
      boolean irrevocableAuthorized) {
    Entry {
      Objects.requireNonNull(program, "program");
      Objects.requireNonNull(snapshot, "snapshot");
      Objects.requireNonNull(continuation, "continuation");
      Objects.requireNonNull(wakeResult, "wakeResult");
      if (kind == EntryKind.VM_SEGMENT && (program.isEmpty() || snapshot.isEmpty())) {
        throw new IllegalArgumentException("VM entry requires program and snapshot values");
      }
      if (kind == EntryKind.RUNTIME_TRANSITION
          && (program.isPresent() || continuation.isEmpty())) {
        throw new IllegalArgumentException("runtime entry requires only a continuation");
      }
      if (kind == EntryKind.RUNTIME_TRANSITION && startingBackground) {
        throw new IllegalArgumentException("only VM entries can start background tasks");
      }
    }

    static Entry runtime(
        long ticket, long taskId, MooRuntime.RuntimeContinuation continuation) {
      return new Entry(
          ticket,
          taskId,
          EntryKind.RUNTIME_TRANSITION,
          Optional.empty(),
          Optional.empty(),
          Long.MIN_VALUE,
          Optional.of(continuation),
          Optional.empty(),
          false,
          false);
    }

    static Entry vm(
        long ticket,
        long taskId,
        BytecodeProgram program,
        VmSnapshot snapshot,
        long taskPlayer,
        Optional<MooRuntime.RuntimeContinuation> continuation) {
      return vm(ticket, taskId, program, snapshot, taskPlayer, continuation, false);
    }

    static Entry vm(
        long ticket,
        long taskId,
        BytecodeProgram program,
        VmSnapshot snapshot,
        long taskPlayer,
        Optional<MooRuntime.RuntimeContinuation> continuation,
        boolean startingBackground) {
      return new Entry(
          ticket,
          taskId,
          EntryKind.VM_SEGMENT,
          Optional.of(program),
          Optional.of(snapshot),
          taskPlayer,
          continuation,
          Optional.empty(),
          startingBackground,
          false);
    }

    Entry withWake(Result completion) {
      return new Entry(
          ticket,
          taskId,
          kind,
          program,
          snapshot,
          taskPlayer,
          continuation,
          Optional.of(completion),
          startingBackground,
          irrevocableAuthorized);
    }

    Entry authorizeIrrevocable() {
      if (irrevocableAuthorized) {
        throw new IllegalStateException("segment is already irrevocable-authorized");
      }
      return new Entry(
          ticket,
          taskId,
          kind,
          program,
          snapshot,
          taskPlayer,
          continuation,
          wakeResult,
          startingBackground,
          true);
    }
  }

  private record ImportedActivationStack(
      List<MooValue> operandStack,
      List<VmSnapshot.HandlerState> handlers,
      List<VmSnapshot.FinallyState> finallyStates,
      Map<Integer, VmSnapshot.LoopState> loops) {
    ImportedActivationStack {
      operandStack = List.copyOf(operandStack);
      handlers = List.copyOf(handlers);
      finallyStates = List.copyOf(finallyStates);
      loops = Collections.unmodifiableMap(new LinkedHashMap<>(loops));
    }
  }

  private record ImportedHandler(
      ToastV17ProgramLayout.BantengHandlerControl control,
      VmSnapshot.HandlerPhase phase) {}

  private record ImportedHandlerGroup(int baseDepth, List<ImportedHandler> handlers) {
    ImportedHandlerGroup {
      handlers = List.copyOf(handlers);
    }
  }

  private record ImportedFinally(int baseDepth, VmSnapshot.FinallyState state) {}

  private record Attempt(
      Entry entry,
      WorldTxn transaction,
      Optional<MooRuntime.AttemptContext> context,
      Optional<SegmentResult> result,
      Optional<Throwable> failure) {}

  private record PendingFork(
      BytecodeProgram program,
      VmSnapshot initialState,
      long taskPlayer,
      double delaySeconds) {}

  private record SegmentResult(
      Optional<List<String>> output,
      Optional<BytecodeProgram> program,
      Optional<VmSnapshot> snapshot,
      long taskPlayer,
      Optional<MooRuntime.RuntimeContinuation> continuation,
      Optional<Callable<Result>> hostWork,
      boolean aborted,
      Optional<VmSnapshot> timeoutSnapshot,
      boolean needsIrrevocable,
      List<PendingFork> pendingForks) {
    SegmentResult {
      output = output.map(List::copyOf);
      Objects.requireNonNull(timeoutSnapshot, "timeoutSnapshot");
      pendingForks = List.copyOf(pendingForks);
      boolean returned = output.isPresent();
      boolean boundary = program.isPresent() && snapshot.isPresent();
      int modes = (returned ? 1 : 0) + (boundary ? 1 : 0) + (needsIrrevocable ? 1 : 0);
      if (modes != 1 || program.isPresent() != snapshot.isPresent()) {
        throw new IllegalArgumentException(
            "segment result requires output, a VM boundary, or irrevocable rerun");
      }
      if (timeoutSnapshot.isPresent() && !aborted) {
        throw new IllegalArgumentException("timeout snapshot requires an aborted segment");
      }
    }

    static SegmentResult returned(
        List<String> output,
        long taskPlayer,
        List<PendingFork> pendingForks,
        boolean aborted,
        Optional<VmSnapshot> timeoutSnapshot) {
      return new SegmentResult(
          Optional.of(output),
          Optional.empty(),
          Optional.empty(),
          taskPlayer,
          Optional.empty(),
          Optional.empty(),
          aborted,
          timeoutSnapshot,
          false,
          pendingForks);
    }

    static SegmentResult boundary(
        BytecodeProgram program,
        VmSnapshot snapshot,
        long taskPlayer,
        Optional<MooRuntime.RuntimeContinuation> continuation,
        Optional<Callable<Result>> hostWork,
        List<PendingFork> pendingForks) {
      return new SegmentResult(
          Optional.empty(),
          Optional.of(program),
          Optional.of(snapshot),
          taskPlayer,
          continuation,
          hostWork,
          false,
          Optional.empty(),
          false,
          pendingForks);
    }

    static SegmentResult irrevocable(List<PendingFork> pendingForks) {
      return new SegmentResult(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Long.MIN_VALUE,
          Optional.empty(),
          Optional.empty(),
          false,
          Optional.empty(),
          true,
          pendingForks);
    }
  }

  private record PublishedAttempt(
      boolean retry,
      boolean authorizeIrrevocable,
      List<MooRuntime.RuntimeStep> spawned,
      Optional<Throwable> failure) {
    PublishedAttempt {
      spawned = List.copyOf(spawned);
      if (retry && authorizeIrrevocable) {
        throw new IllegalArgumentException("publication attempt cannot have two retry causes");
      }
    }

    static PublishedAttempt retryAttempt() {
      return new PublishedAttempt(true, false, List.of(), Optional.empty());
    }

    static PublishedAttempt authorizeIrrevocableAttempt() {
      return new PublishedAttempt(false, true, List.of(), Optional.empty());
    }

    static PublishedAttempt published(List<MooRuntime.RuntimeStep> spawned) {
      return new PublishedAttempt(false, false, spawned, Optional.empty());
    }

    static PublishedAttempt failed(Throwable failure) {
      return new PublishedAttempt(false, false, List.of(), Optional.of(failure));
    }
  }

  private record SuspendedWork(
      long taskId,
      BytecodeProgram program,
      VmSnapshot snapshot,
      long taskPlayer,
      Optional<MooRuntime.RuntimeContinuation> continuation,
      boolean startingBackground) {
    Entry ready(long ticket) {
      return Entry.vm(
          ticket, taskId, program, snapshot, taskPlayer, continuation, startingBackground);
    }

    Entry wake(long ticket, Result completion) {
      return ready(ticket).withWake(completion);
    }
  }

  private record TimedWork(
      SuspendedWork work,
      long scheduledEpochMilli,
      Optional<Result> wakeResult,
      Optional<DurableTask> durableTask) {
    TimedWork {
      Objects.requireNonNull(wakeResult, "wakeResult");
      Objects.requireNonNull(durableTask, "durableTask");
    }
  }

  private record RootCompletion(
      Optional<CompletableFuture<List<String>>> future,
      Optional<List<String>> output,
      Optional<Throwable> failure) {
    RootCompletion {
      output = output.map(List::copyOf);
    }

    static RootCompletion none() {
      return new RootCompletion(Optional.empty(), Optional.empty(), Optional.empty());
    }

    static RootCompletion success(
        CompletableFuture<List<String>> future, List<String> output) {
      return new RootCompletion(Optional.of(future), Optional.of(output), Optional.empty());
    }

    static RootCompletion failure(
        CompletableFuture<List<String>> future, Throwable failure) {
      return new RootCompletion(Optional.of(future), Optional.empty(), Optional.of(failure));
    }

    void complete() {
      if (future.isEmpty()) {
        return;
      }
      if (failure.isPresent()) {
        future.orElseThrow().completeExceptionally(failure.orElseThrow());
      } else {
        future.orElseThrow().complete(output.orElseThrow());
      }
    }
  }
}
