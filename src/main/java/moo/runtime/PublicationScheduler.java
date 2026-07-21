package moo.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import moo.bytecode.MooCompiler;
import moo.persistence.LambdaMooV17Codec.QueuedTask;
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
  private long nextTicket;
  private long nextTaskId;
  private long nextPublicationTicket;
  private boolean publicationDraining;
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

  void restoreQueuedTasks(List<QueuedTask> restoredTasks) {
    List<TimedWork> timers =
        Objects.requireNonNull(restoredTasks, "restoredTasks").stream()
            .map(this::restoreQueuedTask)
            .toList();
    timers.forEach(this::startTimer);
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
            MooRuntime.DEFAULT_MAX_STACK_DEPTH);
    state.ensureRoot(program);
    state.setThreadMode(restored.threadMode());
    SuspendedWork work =
        new SuspendedWork(
            restored.taskId(),
            program,
            state.snapshot(),
            restored.taskPlayer(),
            Optional.empty(),
            true);
    taskRegistry.registerFork(
        restored.taskId(),
        restored.scheduledEpochSecond(),
        restored.programmer(),
        restored.verbLocation(),
        restored.initialLocals());
    nextTaskId = Math.max(nextTaskId, Math.addExact(restored.taskId(), 1));
    return new TimedWork(
        work, Math.multiplyExact(restored.scheduledEpochSecond(), 1_000L), false);
  }

  synchronized List<QueuedTask> queuedTasks() {
    return timedWork.values().stream()
        .filter(timed -> !timed.resume())
        .map(
            timed -> {
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
                  snapshot.frames().isEmpty()
                      || snapshot.frames().getFirst().threadMode());
            })
        .toList();
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
        MooRuntime.RuntimeStep step =
            runtime.execute(continuation.orElseThrow(), completedVm);
        if (step.output().isPresent()) {
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
          runtime.publishVmState(state, taskPlayer);
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
                  state.snapshot().maxStackDepth());
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
          runtime.publishVmState(state, taskPlayer);
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
        if (state.outcome() == VmState.Outcome.RETURNED
            || state.outcome() == VmState.Outcome.ERRORED
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
      synchronized (this) {
        completion = finishSuccess(start, result.output().orElseThrow());
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
              childState.initialLocals());
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
            timer = Optional.of(new TimedWork(child, scheduledStartMillis, false));
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
      if (delaySeconds == 0.0) {
        enqueueWake(suspended, Result.value(new IntegerValue(0)));
      } else {
        long scheduledStartMillis =
            Math.addExact(System.currentTimeMillis(), Math.round(delaySeconds * 1_000.0));
        startTimer(new TimedWork(suspended, scheduledStartMillis, true));
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
      if (timedWork.putIfAbsent(timed.work().taskId(), timed) != null) {
        throw new IllegalStateException("task already has a pending timer");
      }
    }
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
                }
                if (timed.resume()) {
                  enqueueWake(timed.work(), Result.value(new IntegerValue(0)));
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
    taskRegistry.remove(entry.taskId());
    CompletableFuture<List<String>> future = ingress.remove(entry.taskId());
    return future == null ? RootCompletion.none() : RootCompletion.success(future, output);
  }

  private RootCompletion finishFailure(Entry entry, Throwable failure) {
    nextPublicationTicket++;
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
      SuspendedWork work, long scheduledEpochMilli, boolean resume) {}

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
