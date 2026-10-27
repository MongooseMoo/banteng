package world.mongoose.banteng.runtime;

import com.google.errorprone.annotations.concurrent.GuardedBy;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import jdk.jfr.FlightRecorder;
import org.jspecify.annotations.Nullable;
import world.mongoose.banteng.builtin.BuiltinCall;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.persistence.DurableTaskCodec;
import world.mongoose.banteng.persistence.LambdaMooV17Codec.DurableTask;
import world.mongoose.banteng.persistence.LambdaMooV17Codec.QueuedTask;
import world.mongoose.banteng.persistence.LambdaMooV17Codec.SuspendedActivation;
import world.mongoose.banteng.persistence.LambdaMooV17Codec.SuspendedTask;
import world.mongoose.banteng.persistence.ToastV17ProgramLayout;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.vm.VmSnapshot;
import world.mongoose.banteng.vm.VmState;
import world.mongoose.banteng.world.WorldTxn;

/** The sole deterministic execution, validation, retry, and publication owner. */
final class PublicationScheduler implements AutoCloseable {
  private static final long INDEFINITE_SUSPEND_EPOCH_MILLIS =
      Math.multiplyExact((long) Integer.MAX_VALUE, 1_000L);
  private final WorldTxn committedWorld;
  private final SchedulerRuntime runtime;
  private final TaskRegistry taskRegistry;
  private volatile int workers;
  private volatile int backgroundWorkers;
  private final ThreadPoolExecutor executor;
  @GuardedBy("this")
  private final Queue<Entry> ready = new ArrayDeque<>();
  @GuardedBy("this")
  private final Map<Long, Attempt> completed = new TreeMap<>();
  @GuardedBy("this")
  private final Map<Long, Ingress> ingress = new TreeMap<>();
  @GuardedBy("this")
  private final Map<Long, Long> lastInputTasks = new TreeMap<>();
  @GuardedBy("this")
  private final Map<Long, TimedWork> timedWork = new TreeMap<>();
  @GuardedBy("this")
  private final Map<Long, TimedWork> checkpointingWork = new TreeMap<>();
  @GuardedBy("this")
  private final Map<Long, SuspendedWork> finalizationBlocked = new LinkedHashMap<>();
  @GuardedBy("this")
  private final Map<MooRuntime.FinalizationControl, Long> activeFinalizations =
      new LinkedHashMap<>();
  @GuardedBy("this")
  private long nextTicket;
  @GuardedBy("this")
  private long nextTaskId;
  @GuardedBy("this")
  private long nextPublicationTicket;
  @GuardedBy("this")
  private boolean publicationDraining;
  @GuardedBy("this")
  private boolean restoredTasksActivated;
  @GuardedBy("this")
  private boolean closed;

  PublicationScheduler(WorldTxn committedWorld, SchedulerRuntime runtime) {
    this(
        committedWorld,
        runtime,
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        new TaskRegistry());
  }

  PublicationScheduler(WorldTxn committedWorld, SchedulerRuntime runtime, TaskRegistry taskRegistry) {
    this(
        committedWorld,
        runtime,
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        taskRegistry);
  }

  PublicationScheduler(WorldTxn committedWorld, SchedulerRuntime runtime, int workers) {
    this(committedWorld, runtime, workers, new TaskRegistry());
  }

  PublicationScheduler(
      WorldTxn committedWorld, SchedulerRuntime runtime, int workers, TaskRegistry taskRegistry) {
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
      if (closed) {
        return;
      }
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

  synchronized BuiltinResult threadPool(BuiltinCall call) {
    int requested =
        call.arguments().size() == 3
            ? Math.toIntExact(((IntegerValue) call.arguments().get(2)).value())
            : 0;
    backgroundWorkers = requested;
    if (requested == 0) {
      return BuiltinResult.value(new IntegerValue(1));
    }
    if (requested > executor.getMaximumPoolSize()) {
      executor.setMaximumPoolSize(requested);
      executor.setCorePoolSize(requested);
    } else {
      executor.setCorePoolSize(requested);
      executor.setMaximumPoolSize(requested);
    }
    workers = requested;
    return BuiltinResult.value(new IntegerValue(1));
  }

  private TimedWork restoreQueuedTask(QueuedTask restored) {
    BytecodeProgram program = DurableTaskCodec.restoreQueuedProgram(restored);
    VmState state =
        new VmState(
            restored.initialLocals(),
            restored.programmer(),
            restored.verbLocation(),
            MooRuntime.DEFAULT_BACKGROUND_TICKS,
            MooRuntime.DEFAULT_BACKGROUND_SECONDS,
            MooRuntime.DEFAULT_MAX_STACK_DEPTH,
            restored.debug(),
            restored.calledVerb(),
            restored.fullVerbName());
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
            Optional.empty(),
            true);
    taskRegistry.registerFork(
        restored.taskId(),
        restored.scheduledEpochSecond(),
        restored.programmer(),
        restored.verbLocation(),
        restored.initialLocals(),
        restored.fullVerbName(),
        restored.firstSourceLine(),
        snapshot);
    synchronized (this) {
      nextTaskId = Math.max(nextTaskId, Math.addExact(restored.taskId(), 1));
    }
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
      rootToCurrent.add(
          DurableTaskCodec.importActivation(activation, vector, activationIndex == 0, layout));
    }
    Collections.reverse(rootToCurrent);
    VmSnapshot.Frame root = rootToCurrent.getLast();
    VmSnapshot snapshot =
        new VmSnapshot(
            root.locals(),
            root.programmer(),
            root.verbLocation(),
            root.calledVerb(),
            root.fullVerbName(),
            rootToCurrent,
            List.of(),
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
            Optional.empty(),
            false);
    taskRegistry.registerSuspended(
        restored.taskId(), restored.scheduledEpochSecond(), snapshot);
    synchronized (this) {
      nextTaskId = Math.max(nextTaskId, Math.addExact(restored.taskId(), 1));
    }
    BuiltinResult wake =
        restored.interruptionStatus().isPresent()
            ? BuiltinResult.error(ErrorValue.E_INTRPT)
            : restored.resumeValue() instanceof ErrorValue error
                ? BuiltinResult.error(error)
                : BuiltinResult.value(restored.resumeValue());
    return new TimedWork(
        work,
        Math.multiplyExact(restored.scheduledEpochSecond(), 1_000L),
        Optional.of(wake),
        Optional.of(restored));
  }

  synchronized List<DurableTask> durableTasks() {
    return durableTasksExcluding(Long.MIN_VALUE);
  }

  synchronized List<DurableTask> durableTasksExcluding(long excludedTaskId) {
    Map<Long, TimedWork> durable = new TreeMap<>(timedWork);
    checkpointingWork.forEach(
        (taskId, work) -> {
          if (durable.putIfAbsent(taskId, work) != null) {
            throw new IllegalStateException("task has duplicate durable state " + taskId);
          }
        });
    durable.remove(excludedTaskId);
    return durable.values().stream().map(PublicationScheduler::durableTask).toList();
  }

  private static DurableTask durableTask(TimedWork timed) {
    if (timed.durableTask().isPresent()) {
      return timed.durableTask().orElseThrow();
    }
    SuspendedWork work = timed.work();
    if (timed.wakeResult().isPresent()) {
      if (work.continuation().isPresent()) {
        throw new IllegalStateException(
            "suspended task " + work.taskId() + " has a non-v17 runtime continuation");
      }
      BuiltinResult wake = timed.wakeResult().orElseThrow();
      MooValue resumeValue =
          switch (wake) {
            case BuiltinResult.Value value -> value.value();
            case BuiltinResult.ErrorResult error -> error.error();
            case BuiltinResult.RaisedError raised -> raised.error();
            default -> throw new IllegalStateException("invalid durable wake result: " + wake);
          };
      return DurableTaskCodec.durableSuspension(
          work.taskId(), timed.scheduledEpochMilli(), work.snapshot(), work.taskPlayer(),
          resumeValue);
    }
    return DurableTaskCodec.exportQueuedTask(
        work.taskId(), timed.scheduledEpochMilli(), work.program(), work.snapshot(),
        work.taskPlayer());
  }

  BuiltinResult resumeTask(BuiltinCall call) {
    long taskId = ((IntegerValue) call.arguments().getFirst()).value();
    if (taskId == call.taskId()) return BuiltinResult.error(ErrorValue.E_INVARG);
    switch (taskRegistry.taskControlDecision(taskId, call.world(), call.programmer())) {
      case DENIED -> {
        return BuiltinResult.error(ErrorValue.E_PERM);
      }
      case MISSING -> {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      case ALLOWED -> {
        // Continue with the scheduler-owned suspended-task lookup.
      }
    }
    MooValue value =
        call.arguments().size() == 2 ? call.arguments().get(1) : new IntegerValue(0);
    BuiltinResult completion = value instanceof ErrorValue error
        ? BuiltinResult.error(error) : BuiltinResult.value(value);
    TimedWork resumed;
    synchronized (this) {
      resumed = timedWork.get(taskId);
      if (resumed == null) {
        resumed = checkpointingWork.get(taskId);
        if (resumed == null || resumed.wakeResult().isEmpty()) {
          return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        checkpointingWork.put(taskId, new TimedWork(resumed.work(), System.currentTimeMillis(),
            Optional.of(completion), resumed.durableTask()));
        return BuiltinResult.value(new IntegerValue(0));
      }
      if (resumed == null || resumed.wakeResult().isEmpty()) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      if (!timedWork.remove(taskId, resumed)) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      TimedWork readyToResume =
          new TimedWork(
              resumed.work(),
              System.currentTimeMillis(),
              Optional.of(completion),
              resumed.durableTask());
      if (checkpointingWork.putIfAbsent(taskId, readyToResume) != null) {
        throw new IllegalStateException("task already has checkpointing state " + taskId);
      }
      resumed = readyToResume;
    }
    enqueueWake(resumed.work(), resumed.wakeResult().orElseThrow());
    return BuiltinResult.value(new IntegerValue(0));
  }

  List<String> submit(MooRuntime.RuntimeRequest request) {
    return submit(request, false);
  }

  List<String> submit(MooRuntime.RuntimeRequest request, boolean socketInput) {
    CompletableFuture<List<String>> published = new CompletableFuture<>();
    synchronized (this) {
      if (closed && (request.operation() == MooRuntime.Operation.STARTUP
          || request.operation() == MooRuntime.Operation.TIMEOUT)) {
        return List.of();
      }
      ensureOpen();
      long taskId = nextTaskId++;
      long ticket = nextTicket++;
      if (request.operation() == MooRuntime.Operation.OPEN) {
        long player = runtime.connectionPlayer(request.connectionId()).orElse(-1);
        lastInputTasks.put(player >= 0 ? player : request.connectionId(), taskId);
      } else if (request.operation() == MooRuntime.Operation.LINE) {
        OptionalLong inputPlayer = runtime.inputTaskPlayer(request.connectionId(), request.text());
        if (inputPlayer.isPresent()) lastInputTasks.put(inputPlayer.orElseThrow(), taskId);
      }
      ingress.put(taskId, new Ingress(published,
          socketInput ? OptionalLong.of(request.connectionId()) : OptionalLong.empty(),
          request.operation(), List.of()));
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
      OptionalLong inputPlayer = runtime.inputTaskPlayer(request.connectionId(), request.text());
      if (inputPlayer.isPresent()) lastInputTasks.put(inputPlayer.orElseThrow(), taskId);
    }
    ready.add(
        Entry.runtime(
            nextTicket++, taskId, MooRuntime.RuntimeContinuation.ingress(request)));
    dispatch();
  }

  synchronized boolean isLastInputTask(long taskId) {
    return lastInputTasks.containsValue(taskId);
  }

  synchronized long lastInputTask(long player) {
    return lastInputTasks.getOrDefault(player, 0L);
  }

  synchronized void clearLastInputTask(long player, long taskId) {
    lastInputTasks.remove(player, taskId);
  }

  synchronized OptionalLong lastInputPlayer(long taskId) {
    for (Map.Entry<Long, Long> entry : lastInputTasks.entrySet()) {
      if (entry.getValue() == taskId) {
        return OptionalLong.of(entry.getKey());
      }
    }
    return OptionalLong.empty();
  }

  private synchronized void dispatch() {
    if (closed) {
      return;
    }
    while (!ready.isEmpty() && hasExecutorCapacity()) {
      Entry entry = ready.element();
      try {
        executor.execute(() -> executeAttempt(entry));
      } catch (RejectedExecutionException rejected) {
        return;
      }
      ready.remove();
    }
  }

  private boolean hasExecutorCapacity() {
    return executor.getActiveCount() < workers || executor.getQueue().remainingCapacity() > 0;
  }

  private void executeAttempt(Entry entry) {
    synchronized (this) {
      TimedWork pending = checkpointingWork.get(entry.taskId());
      if (entry.wakeResult().isPresent() && pending != null && pending.wakeResult().isPresent()) {
        entry = entry.withWake(pending.wakeResult().orElseThrow());
      }
    }
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
      context = runtime.openAttempt(transaction, entry.taskId());
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
    Optional<MooRuntime.FinalizationControl> finalizationControl = start.finalizationControl();
    Optional<VmSnapshot> completedVm =
        start.kind() == EntryKind.RUNTIME_TRANSITION ? start.snapshot() : Optional.empty();
    Optional<BytecodeProgram> program = start.program();
    Optional<VmSnapshot> snapshot =
        start.kind() == EntryKind.VM_SEGMENT ? start.snapshot() : Optional.empty();
    long taskPlayer = start.taskPlayer();
    boolean startingBackground = start.startingBackground();
    Optional<BuiltinResult> wakeResult = start.wakeResult();
    List<PendingFork> pendingForks = new ArrayList<>();
    boolean aborted = false;
    Optional<VmSnapshot> timeoutSnapshot = Optional.empty();

    while (true) {
      if (program.isEmpty()) {
        Optional<MooRuntime.FinalizationControl> executingFinalization = finalizationControl;
        MooRuntime.RuntimeStep step =
            runtime.execute(continuation.orElseThrow(), completedVm);
        if (step.output().isPresent()) {
          if (executingFinalization
              .filter(
                  control -> control.kind() == MooRuntime.FinalizationKind.ANONYMOUS)
              .isPresent()) {
            MooRuntime.FinalizationControl anonymousFinalization =
                executingFinalization.orElseThrow();
            runtime.collectAfterAnonymousFinalization(
                (AnonymousObjectValue) anonymousFinalization.target(),
                taskRegistry.snapshotsExcluding(start.taskId()),
                startsNextAnonymousFinalizationWave(anonymousFinalization));
          }
          return SegmentResult.returned(
              step.output().orElseThrow(),
              taskPlayer,
              pendingForks,
              aborted,
              timeoutSnapshot,
              executingFinalization);
        }
        program = step.program();
        snapshot = step.snapshot();
        taskPlayer = step.taskPlayer();
        continuation = step.continuation();
        finalizationControl =
            continuation.flatMap(MooRuntime.RuntimeContinuation::finalizationControl);
        startingBackground = false;
        wakeResult = Optional.empty();
      }

      VmState state =
          startingBackground
              ? runtime.startBackgroundTask(snapshot.orElseThrow())
              : VmState.restore(snapshot.orElseThrow());
      if (wakeResult.isPresent()) {
        BuiltinResult completion = wakeResult.orElseThrow();
        if (completion instanceof BuiltinResult.ErrorResult
            || completion instanceof BuiltinResult.RaisedError) {
          runtime.vm().resumeWithError(state, completion, transaction);
        } else if (completion instanceof BuiltinResult.Value value) {
          state.resume(value.value());
        } else {
          throw new IllegalStateException("invalid wake result: " + completion);
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
        runtime.publishVmState(
              state,
              taskPlayer,
              finalizationControl.isEmpty(),
              taskRegistry.snapshotsExcluding(start.taskId()));
        if (state.outcome() == VmState.Outcome.FORKED) {
          if (!start.irrevocableAuthorized()) {
            return SegmentResult.irrevocable(pendingForks, finalizationControl);
          }
          long childTaskId;
          synchronized (this) {
            childTaskId = nextTaskId++;
          }
          VmSnapshot.Fork fork = state.continueAfterFork(new IntegerValue(childTaskId));
          VmState child =
              new VmState(
                  fork.locals(),
                  fork.programmer(),
                  fork.verbLocation(),
                  MooRuntime.DEFAULT_BACKGROUND_TICKS,
                  MooRuntime.DEFAULT_BACKGROUND_SECONDS,
                  state.snapshot().maxStackDepth(),
                  fork.debug(),
                  fork.calledVerb(),
                  fork.fullVerbName());
          VmSnapshot childState = child.snapshot();
          long scheduledStart = Math.addExact(
              System.currentTimeMillis(), Math.round(fork.delaySeconds() * 1_000.0));
          TimedWork pending = new TimedWork(
              new SuspendedWork(childTaskId, fork.program(), childState, taskPlayer,
                  Optional.empty(), Optional.empty(), true),
              scheduledStart, Optional.empty(), Optional.empty());
          synchronized (this) {
            taskRegistry.registerFork(childTaskId, Math.floorDiv(scheduledStart, 1_000L),
                childState.initialProgrammer(), childState.initialVerbLocation(),
                childState.initialLocals(), childState.initialFullVerbName(),
                fork.program().instructions().isEmpty() ? 1 : fork.program().sourceLine(0), childState);
            checkpointingWork.put(childTaskId, pending);
          }
          pendingForks.add(new PendingFork(pending));
          continue;
        }
        if (state.outcome() == VmState.Outcome.PENDING_BUILTIN) {
          if (!start.irrevocableAuthorized()) {
            return SegmentResult.irrevocable(pendingForks, finalizationControl);
          }
          runtime
              .vm()
              .authorizePendingBuiltin(
                  state, transaction, runtime.builtins(), start.taskId());
          runtime.publishVmState(
              state,
              taskPlayer,
              finalizationControl.isEmpty(),
              taskRegistry.snapshotsExcluding(start.taskId()));
          continue;
        }

        VmSnapshot completed = state.snapshot();
        if (state.outcome() == VmState.Outcome.KILLED) {
          aborted = true;
        }
        if (state.outcome() == VmState.Outcome.ABORTED
            || state.outcome() == VmState.Outcome.SECONDS_EXHAUSTED) {
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
                || state.outcome() == VmState.Outcome.ABORTED
                || state.outcome() == VmState.Outcome.SECONDS_EXHAUSTED
                || state.outcome() == VmState.Outcome.KILLED)
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
                timeoutSnapshot,
                finalizationControl);
          }
          program = uncaught.program();
          snapshot = uncaught.snapshot();
          taskPlayer = uncaught.taskPlayer();
          continuation = uncaught.continuation();
          finalizationControl =
              continuation.flatMap(MooRuntime.RuntimeContinuation::finalizationControl);
          break;
        }
        if (state.outcome() == VmState.Outcome.RETURNED
            || state.outcome() == VmState.Outcome.ABORTED
            || state.outcome() == VmState.Outcome.SECONDS_EXHAUSTED
            || state.outcome() == VmState.Outcome.KILLED) {
          return SegmentResult.returned(
              completed.output(),
              taskPlayer,
              pendingForks,
              aborted,
              timeoutSnapshot,
              finalizationControl);
        }
        if (state.outcome() == VmState.Outcome.SUSPENDED) {
          Optional<List<String>> inputOutput = continuation.flatMap(
              value -> runtime.suspendedInputOutput(value, state));
          if (inputOutput.isEmpty() && continuation.isEmpty() && !state.output().isEmpty()) {
            inputOutput = Optional.of(state.output());
            state.clearOutput();
          }
          if (inputOutput.isPresent()) {
            continuation = Optional.empty();
            completed = state.snapshot();
          }
          continuation = continuation.filter(
              value -> value.transition().orElseThrow() != MooRuntime.RuntimeTransition.SIGNAL_RETURN);
          return SegmentResult.boundary(
              program.orElseThrow(),
              completed,
              taskPlayer,
              continuation,
              finalizationControl,
              state.hostWork(),
              pendingForks,
              inputOutput);
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
      }
      publishSegmentResultOutsideMonitor(attempt.entry(), result);
      synchronized (this) {
        dispatch();
      }
    }
  }

  private void publishSegmentResultOutsideMonitor(Entry start, SegmentResult result) {
    Ingress input;
    synchronized (this) {
      input = ingress.get(start.taskId());
    }
    if (result.timeoutSnapshot().isEmpty() && input != null && input.socketConnection().isPresent()) {
      Optional<List<String>> output = result.output().or(result::inputOutput);
      if (output.isPresent()) {
        runtime.writeConnectionOutput(input.socketConnection().orElseThrow(), output.orElseThrow());
      }
    }
    for (PendingFork fork : result.pendingForks()) {
      TimedWork pending = fork.work();
      synchronized (this) {
        if (taskRegistry.discardIfCanceled(pending.work().taskId())) {
          checkpointingWork.remove(pending.work().taskId());
          continue;
        }
      }
      if (pending.scheduledEpochMilli() <= System.currentTimeMillis()) {
        enqueueReady(pending.work());
      } else {
        startTimer(pending);
      }
    }
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
      boolean detached;
      synchronized (this) {
        detached = !ingress.containsKey(start.taskId());
        registerActiveFinalization(start.taskId(), result.finalizationControl());
        completion =
            finishSuccess(start, result.finalizationControl(), result.output().orElseThrow());
      }
      completion.complete();
      if (detached) {
        runtime.writeDetachedOutput(result.taskPlayer(), result.output().orElseThrow());
      }
      return;
    }
    CompletableFuture<List<String>> foreground = null;
    boolean retainedIngress = false;
    if (result.inputOutput().isPresent()) {
      synchronized (this) {
        input = ingress.get(start.taskId());
        if (input != null && input.socketConnection().isPresent()) {
          foreground = ingress.remove(start.taskId()).future();
        } else if (input != null) {
          ingress.put(start.taskId(), input.append(result.inputOutput().orElseThrow()));
          retainedIngress = true;
        }
      }
    }
    synchronized (this) {
      registerActiveFinalization(start.taskId(), result.finalizationControl());
    }
    Entry boundary =
        Entry.vm(
            start.ticket(),
            start.taskId(),
            result.program().orElseThrow(),
            result.snapshot().orElseThrow(),
            result.taskPlayer(),
            result.continuation(),
            result.finalizationControl(),
            false);
    publishVmCompletionOutsideMonitor(
        boundary,
        result.snapshot().orElseThrow(),
        result.hostWork());
    if (result.inputOutput().isPresent()) {
      if (foreground != null) {
        foreground.complete(List.of());
      } else if (!retainedIngress) {
        runtime.writeDetachedOutput(result.taskPlayer(), result.inputOutput().orElseThrow());
      }
    }
  }

  private PublishedAttempt publishAttempt(Attempt attempt) {
    synchronized (this) {
      TimedWork pending = checkpointingWork.get(attempt.entry().taskId());
      if (attempt.entry().wakeResult().isPresent() && pending != null
          && pending.wakeResult().isPresent()
          && !pending.wakeResult().equals(attempt.entry().wakeResult())) {
        attempt.transaction().close();
        return PublishedAttempt.retryAttempt();
      }
    }
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
      Optional<Callable<BuiltinResult>> hostWork) {
    synchronized (this) {
      checkpointingWork.remove(entry.taskId());
    }
    switch (snapshot.outcome()) {
      case SUSPENDED -> publishSuspension(entry, snapshot, hostWork);
      case FORKED, PENDING_BUILTIN, RETURNED, ERRORED, ABORTED, SECONDS_EXHAUSTED, KILLED, RUNNING ->
          throw new IllegalStateException(
              "worker returned a non-boundary VM outcome: " + snapshot.outcome());
    }
  }

  private void publishSuspension(
      Entry entry,
      VmSnapshot snapshot,
      Optional<Callable<BuiltinResult>> hostWork) {
    SuspendedWork suspended =
        new SuspendedWork(
            entry.taskId(),
            entry.program().orElseThrow(),
            snapshot,
            entry.taskPlayer(),
            entry.continuation(),
            entry.finalizationControl(),
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
                  Optional.of(BuiltinResult.value(new IntegerValue(0))),
                  Optional.empty()));
        }
      } else if (delaySeconds == 0.0) {
        synchronized (this) {
          checkpointingWork.put(entry.taskId(), new TimedWork(suspended, scheduledStartMillis,
              Optional.of(BuiltinResult.value(new IntegerValue(0))), Optional.empty()));
        }
        enqueueZeroDelayWake(suspended);
      } else {
        startTimer(
            new TimedWork(
                suspended,
                scheduledStartMillis,
                Optional.of(BuiltinResult.value(new IntegerValue(0))),
                Optional.empty()));
      }
      return;
    }
    FutureTask<BuiltinResult> submitted = new FutureTask<>(hostWork.orElseThrow());
    if (!taskRegistry.registerHost(
        entry.taskId(),
        snapshot,
        submitted)) {
      cancelWaiting(suspended);
      return;
    }
    if (backgroundWorkers == 0) {
      if (taskRegistry.claimHostTerminal(entry.taskId())) {
        enqueueWake(suspended, BuiltinResult.error(ErrorValue.E_QUOTA));
      } else {
        cancelWaiting(suspended);
      }
      return;
    }
    try {
      executor.execute(submitted);
    } catch (RejectedExecutionException rejected) {
      if (taskRegistry.claimHostTerminal(entry.taskId())) {
        enqueueWake(suspended, BuiltinResult.error(ErrorValue.E_QUOTA));
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
                BuiltinResult completion = submitted.get();
                if (!(completion instanceof BuiltinResult.Value
                    || completion instanceof BuiltinResult.ErrorResult
                    || completion instanceof BuiltinResult.RaisedError)) {
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

  private synchronized void registerTimer(TimedWork timed) {
    checkpointingWork.remove(timed.work().taskId(), timed);
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

  private synchronized void enqueueWake(SuspendedWork work, BuiltinResult completion) {
    if (!closed && !taskRegistry.discardIfCanceled(work.taskId())) {
      ready.add(work.wake(nextTicket++, completion));
      dispatch();
    }
  }

  private synchronized void enqueueZeroDelayWake(SuspendedWork work) {
    boolean anonymousFinalizer =
        work.finalizationControl()
            .filter(control -> control.kind() == MooRuntime.FinalizationKind.ANONYMOUS)
            .isPresent();
    if (!anonymousFinalizer && hasActiveAnonymousFinalization()) {
      if (finalizationBlocked.putIfAbsent(work.taskId(), work) != null) {
        throw new IllegalStateException("task already blocked by anonymous finalization");
      }
      return;
    }
    ready.add(work.wake(nextTicket++, BuiltinResult.value(new IntegerValue(0))));
    dispatch();
  }

  private synchronized void releaseFinalizationBlocked() {
    if (hasActiveAnonymousFinalization() || finalizationBlocked.isEmpty()) {
      return;
    }
    for (SuspendedWork work : finalizationBlocked.values()) {
      if (!closed && !taskRegistry.discardIfCanceled(work.taskId())) {
        ready.add(work.wake(nextTicket++, BuiltinResult.value(new IntegerValue(0))));
      }
    }
    finalizationBlocked.clear();
    dispatch();
  }

  private synchronized boolean hasActiveAnonymousFinalization() {
    return activeFinalizations.keySet().stream()
        .anyMatch(control -> control.kind() == MooRuntime.FinalizationKind.ANONYMOUS);
  }

  private synchronized boolean startsNextAnonymousFinalizationWave(
      MooRuntime.FinalizationControl completing) {
    Objects.requireNonNull(completing, "completing");
    if (finalizationBlocked.isEmpty()
        || completing.kind() != MooRuntime.FinalizationKind.ANONYMOUS
        || !activeFinalizations.containsKey(completing)) {
      return false;
    }
    long activeAnonymousFinalizations =
        activeFinalizations.keySet().stream()
            .filter(control -> control.kind() == MooRuntime.FinalizationKind.ANONYMOUS)
            .count();
    return activeAnonymousFinalizations == 1;
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
      Ingress input = ingress.remove(work.taskId());
      canceledIngress = input == null ? null : input.future();
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

  private synchronized void enqueueSpawned(MooRuntime.RuntimeStep step) {
    if (step.output().isPresent()) {
      return;
    }
    Optional<MooRuntime.FinalizationControl> finalizationControl =
        step.continuation().flatMap(MooRuntime.RuntimeContinuation::finalizationControl);
    if (finalizationControl.filter(activeFinalizations::containsKey).isPresent()) {
      return;
    }
    long taskId = nextTaskId++;
    if (!registerActiveFinalization(taskId, finalizationControl)) {
      return;
    }
    ready.add(
        Entry.vm(
            nextTicket++,
            taskId,
            step.program().orElseThrow(),
            step.snapshot().orElseThrow(),
            step.taskPlayer(),
            step.continuation(),
            finalizationControl,
            false));
  }

  private synchronized boolean registerActiveFinalization(
      long taskId, Optional<MooRuntime.FinalizationControl> control) {
    activeFinalizations.entrySet().removeIf(entry -> entry.getValue() == taskId);
    if (control.isEmpty()) {
      return true;
    }
    Long activeTask = activeFinalizations.putIfAbsent(control.orElseThrow(), taskId);
    if (activeTask == null || activeTask == taskId) {
      return true;
    }
    return false;
  }

  private synchronized void finishActiveFinalization(
      long taskId, Optional<MooRuntime.FinalizationControl> control) {
    if (control.isEmpty()) {
      return;
    }
    MooRuntime.FinalizationControl finished = control.orElseThrow();
    activeFinalizations.remove(finished, taskId);
    if (finished.kind() == MooRuntime.FinalizationKind.ANONYMOUS) {
      releaseFinalizationBlocked();
    }
  }

  private synchronized RootCompletion finishSuccess(
      Entry entry,
      Optional<MooRuntime.FinalizationControl> finalizationControl,
      List<String> output) {
    nextPublicationTicket++;
    checkpointingWork.remove(entry.taskId());
    taskRegistry.remove(entry.taskId());
    finishActiveFinalization(entry.taskId(), finalizationControl);
    Ingress input = ingress.remove(entry.taskId());
    return input == null ? RootCompletion.none()
        : RootCompletion.success(input.future(), input.socketConnection().isPresent()
            ? List.of() : input.append(output).output());
  }

  private synchronized RootCompletion finishFailure(Entry entry, Throwable failure) {
    nextPublicationTicket++;
    checkpointingWork.remove(entry.taskId());
    taskRegistry.remove(entry.taskId());
    finishActiveFinalization(entry.taskId(), entry.finalizationControl());
    Ingress input = ingress.remove(entry.taskId());
    return input == null
        ? RootCompletion.none()
        : RootCompletion.failure(input.future(), failure);
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

  private synchronized void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("publication scheduler is closed");
    }
  }

  @Override
  public void close() {
    List<Ingress> pending;
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
    for (Ingress input : pending) {
      if (input.operation() == MooRuntime.Operation.STARTUP) {
        input.future().complete(input.output());
      } else {
        input.future().completeExceptionally(failure);
      }
    }
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
      Optional<MooRuntime.FinalizationControl> finalizationControl,
      Optional<BuiltinResult> wakeResult,
      boolean startingBackground,
      boolean irrevocableAuthorized) {
    Entry {
      Objects.requireNonNull(program, "program");
      Objects.requireNonNull(snapshot, "snapshot");
      Objects.requireNonNull(continuation, "continuation");
      Objects.requireNonNull(finalizationControl, "finalizationControl");
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
      if (!continuation.flatMap(MooRuntime.RuntimeContinuation::finalizationControl)
          .equals(finalizationControl)) {
        throw new IllegalArgumentException(
            "entry finalization control disagrees with its runtime continuation");
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
          continuation.finalizationControl(),
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
        Optional<MooRuntime.RuntimeContinuation> continuation,
        Optional<MooRuntime.FinalizationControl> finalizationControl,
        boolean startingBackground) {
      return new Entry(
          ticket,
          taskId,
          EntryKind.VM_SEGMENT,
          Optional.of(program),
          Optional.of(snapshot),
          taskPlayer,
          continuation,
          finalizationControl,
          Optional.empty(),
          startingBackground,
          false);
    }

    Entry withWake(BuiltinResult completion) {
      return new Entry(
          ticket,
          taskId,
          kind,
          program,
          snapshot,
          taskPlayer,
          continuation,
          finalizationControl,
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
          finalizationControl,
          wakeResult,
          startingBackground,
          true);
    }
  }

  private record Attempt(
      Entry entry,
      WorldTxn transaction,
      Optional<MooRuntime.AttemptContext> context,
      Optional<SegmentResult> result,
      Optional<Throwable> failure) {}

  private record Ingress(
      CompletableFuture<List<String>> future, OptionalLong socketConnection,
      MooRuntime.Operation operation, List<String> output) {
    Ingress append(List<String> lines) {
      List<String> combined = new ArrayList<>(output);
      combined.addAll(lines);
      return new Ingress(future, socketConnection, operation, List.copyOf(combined));
    }
  }

  private record PendingFork(TimedWork work) {}

  private record SegmentResult(
      Optional<List<String>> output,
      Optional<BytecodeProgram> program,
      Optional<VmSnapshot> snapshot,
      long taskPlayer,
      Optional<MooRuntime.RuntimeContinuation> continuation,
      Optional<MooRuntime.FinalizationControl> finalizationControl,
      Optional<Callable<BuiltinResult>> hostWork,
      boolean aborted,
      Optional<VmSnapshot> timeoutSnapshot,
      boolean needsIrrevocable,
      List<PendingFork> pendingForks,
      Optional<List<String>> inputOutput) {
    SegmentResult {
      output = output.map(List::copyOf);
      Objects.requireNonNull(finalizationControl, "finalizationControl");
      Objects.requireNonNull(timeoutSnapshot, "timeoutSnapshot");
      pendingForks = List.copyOf(pendingForks);
      boolean returned = output.isPresent();
      boolean boundary = program.isPresent() && snapshot.isPresent();
      int modes = (returned ? 1 : 0) + (boundary ? 1 : 0) + (needsIrrevocable ? 1 : 0);
      if (modes != 1 || program.isPresent() != snapshot.isPresent()) {
        throw new IllegalArgumentException(
            "segment result requires output, a VM boundary, or irrevocable rerun");
      }
      if (boundary
          && !continuation.flatMap(MooRuntime.RuntimeContinuation::finalizationControl)
              .equals(finalizationControl)) {
        throw new IllegalArgumentException(
            "segment finalization control disagrees with its runtime continuation");
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
        Optional<VmSnapshot> timeoutSnapshot,
        Optional<MooRuntime.FinalizationControl> finalizationControl) {
      return new SegmentResult(
          Optional.of(output),
          Optional.empty(),
          Optional.empty(),
          taskPlayer,
          Optional.empty(),
          finalizationControl,
          Optional.empty(),
          aborted,
          timeoutSnapshot,
          false,
          pendingForks,
          Optional.empty());
    }

    static SegmentResult boundary(
        BytecodeProgram program,
        VmSnapshot snapshot,
        long taskPlayer,
        Optional<MooRuntime.RuntimeContinuation> continuation,
        Optional<MooRuntime.FinalizationControl> finalizationControl,
        Optional<Callable<BuiltinResult>> hostWork,
        List<PendingFork> pendingForks,
        Optional<List<String>> inputOutput) {
      return new SegmentResult(
          Optional.empty(),
          Optional.of(program),
          Optional.of(snapshot),
          taskPlayer,
          continuation,
          finalizationControl,
          hostWork,
          false,
          Optional.empty(),
          false,
          pendingForks,
          inputOutput);
    }

    static SegmentResult irrevocable(
        List<PendingFork> pendingForks,
        Optional<MooRuntime.FinalizationControl> finalizationControl) {
      return new SegmentResult(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Long.MIN_VALUE,
          Optional.empty(),
          finalizationControl,
          Optional.empty(),
          false,
          Optional.empty(),
          true,
          pendingForks,
          Optional.empty());
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
      Optional<MooRuntime.FinalizationControl> finalizationControl,
      boolean startingBackground) {
    SuspendedWork {
      Objects.requireNonNull(continuation, "continuation");
      Objects.requireNonNull(finalizationControl, "finalizationControl");
      if (!continuation.flatMap(MooRuntime.RuntimeContinuation::finalizationControl)
          .equals(finalizationControl)) {
        throw new IllegalArgumentException(
            "suspended finalization control disagrees with its runtime continuation");
      }
    }

    Entry ready(long ticket) {
      return Entry.vm(
          ticket,
          taskId,
          program,
          snapshot,
          taskPlayer,
          continuation,
          finalizationControl,
          startingBackground);
    }

    Entry wake(long ticket, BuiltinResult completion) {
      return ready(ticket).withWake(completion);
    }
  }

  private record TimedWork(
      SuspendedWork work,
      long scheduledEpochMilli,
      Optional<BuiltinResult> wakeResult,
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
