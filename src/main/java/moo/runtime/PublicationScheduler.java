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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import jdk.jfr.FlightRecorder;
import moo.bytecode.BytecodeProgram;
import moo.value.MooValue;
import moo.value.MooValue.IntegerValue;
import moo.vm.VmSnapshot;
import moo.vm.VmState;
import moo.world.WorldTxn;
import org.jspecify.annotations.Nullable;

/** The sole deterministic execution, validation, retry, and publication owner. */
final class PublicationScheduler implements AutoCloseable {
  private final WorldTxn committedWorld;
  private final MooRuntime runtime;
  private final TaskRegistry taskRegistry;
  private final int workers;
  private final ThreadPoolExecutor executor;
  private final Queue<Entry> ready = new ArrayDeque<>();
  private final Map<Long, Attempt> completed = new TreeMap<>();
  private final Map<Long, CompletableFuture<List<String>>> ingress = new TreeMap<>();
  private final Map<Long, Long> lastInputTasks = new TreeMap<>();
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
    Optional<MooValue> wakeValue = start.wakeValue();
    List<PendingFork> pendingForks = new ArrayList<>();

    while (true) {
      if (program.isEmpty()) {
        MooRuntime.RuntimeStep step =
            runtime.execute(continuation.orElseThrow(), completedVm);
        if (step.output().isPresent()) {
          return SegmentResult.returned(step.output().orElseThrow(), pendingForks);
        }
        program = step.program();
        snapshot = step.snapshot();
        taskPlayer = step.taskPlayer();
        continuation = step.continuation();
        startingBackground = false;
        wakeValue = Optional.empty();
      }

      VmState state =
          startingBackground
              ? runtime.startBackgroundTask(snapshot.orElseThrow())
              : VmState.restore(snapshot.orElseThrow());
      if (wakeValue.isPresent()) {
        MooValue value = wakeValue.orElseThrow();
        if (state.outcome() == VmState.Outcome.FORKED) {
          state.continueAfterFork((IntegerValue) value);
        } else {
          state.resume(value);
        }
      }
      startingBackground = false;
      wakeValue = Optional.empty();

      while (true) {
        runtime
            .vm()
            .execute(
                program.orElseThrow(),
                state,
                transaction,
                runtime.builtins(),
                start.taskId());
        runtime.publishVmState(state, taskPlayer);
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
        if ((state.outcome() == VmState.Outcome.RETURNED
                || state.outcome() == VmState.Outcome.ERRORED)
            && continuation.isPresent()) {
          completedVm = Optional.of(completed);
          program = Optional.empty();
          snapshot = Optional.empty();
          break;
        }
        if (state.outcome() == VmState.Outcome.RETURNED
            || state.outcome() == VmState.Outcome.ERRORED) {
          return SegmentResult.returned(completed.output(), pendingForks);
        }
        if (state.outcome() == VmState.Outcome.SUSPENDED) {
          return SegmentResult.boundary(
              program.orElseThrow(),
              completed,
              taskPlayer,
              continuation,
              state.hostResult(),
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
        result.hostWake(),
        result.pendingForks());
  }

  private PublishedAttempt publishAttempt(Attempt attempt) {
    if (attempt.failure().isPresent()) {
      attempt.transaction().close();
      return PublishedAttempt.failed(attempt.failure().orElseThrow());
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
    if (attempt.result().orElseThrow().needsIrrevocable()) {
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
      Optional<CompletableFuture<MooValue>> hostWake,
      List<PendingFork> pendingForks) {
    switch (snapshot.outcome()) {
      case SUSPENDED -> publishSuspension(entry, snapshot, hostWake);
      case FORKED -> {
        if (pendingForks.size() != 1) {
          throw new IllegalStateException("fork boundary requires exactly one child");
        }
        PendingFork fork = pendingForks.getFirst();
        Optional<TimedWork> timer;
        synchronized (this) {
          long childTaskId = nextTaskId++;
          VmSnapshot childState = fork.initialState();
          long scheduledStart =
              Math.round(System.currentTimeMillis() / 1_000.0 + fork.delaySeconds());
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
          ready.add(parent.wake(nextTicket++, new IntegerValue(childTaskId)));
          if (fork.delaySeconds() == 0.0) {
            ready.add(child.ready(nextTicket++));
            timer = Optional.empty();
          } else {
            timer = Optional.of(new TimedWork(child, fork.delaySeconds(), false));
          }
          dispatch();
        }
        timer.ifPresent(this::startTimer);
      }
      case PENDING_BUILTIN, RETURNED, ERRORED, RUNNING ->
          throw new IllegalStateException(
              "worker returned a non-boundary VM outcome: " + snapshot.outcome());
    }
  }

  private void publishSuspension(
      Entry entry,
      VmSnapshot snapshot,
      Optional<CompletableFuture<MooValue>> hostWake) {
    SuspendedWork suspended =
        new SuspendedWork(
            entry.taskId(),
            entry.program().orElseThrow(),
            snapshot,
            entry.taskPlayer(),
            entry.continuation(),
            false);
    synchronized (this) {
      nextPublicationTicket++;
    }
    if (snapshot.suspensionDelaySeconds().isPresent()) {
      double delaySeconds = snapshot.suspensionDelaySeconds().orElseThrow();
      if (delaySeconds == 0.0) {
        enqueueWake(suspended, new IntegerValue(0));
      } else {
        startTimer(new TimedWork(suspended, delaySeconds, true));
      }
      return;
    }
    CompletableFuture<MooValue> wake = hostWake.orElseThrow();
    Thread.ofVirtual()
        .name("moo-host-wake-" + entry.taskId())
        .start(
            () -> {
              try {
                enqueueWake(suspended, wake.join());
              } catch (Throwable failure) {
                failWaiting(suspended, failure);
              }
            });
  }

  private void startTimer(TimedWork timed) {
    long delayNanos =
        Math.max(0L, Math.round(timed.delaySeconds() * 1_000_000_000.0));
    Thread.ofVirtual()
        .name("moo-timer-wake-" + timed.work().taskId())
        .start(
            () -> {
              try {
                TimeUnit.NANOSECONDS.sleep(delayNanos);
                if (timed.resume()) {
                  enqueueWake(timed.work(), new IntegerValue(0));
                } else {
                  enqueueReady(timed.work());
                }
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                failWaiting(timed.work(), interrupted);
              }
            });
  }

  private synchronized void enqueueWake(SuspendedWork work, MooValue value) {
    if (!closed && !taskRegistry.discardIfCanceled(work.taskId())) {
      ready.add(work.wake(nextTicket++, value));
      dispatch();
    }
  }

  private synchronized void enqueueReady(SuspendedWork work) {
    if (!closed && !taskRegistry.discardIfCanceled(work.taskId())) {
      ready.add(work.ready(nextTicket++));
      dispatch();
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
    return workers * 4;
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
      Optional<MooValue> wakeValue,
      boolean startingBackground,
      boolean irrevocableAuthorized) {
    Entry {
      Objects.requireNonNull(program, "program");
      Objects.requireNonNull(snapshot, "snapshot");
      Objects.requireNonNull(continuation, "continuation");
      Objects.requireNonNull(wakeValue, "wakeValue");
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

    Entry withWake(MooValue value) {
      return new Entry(
          ticket,
          taskId,
          kind,
          program,
          snapshot,
          taskPlayer,
          continuation,
          Optional.of(value),
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
          wakeValue,
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
      Optional<CompletableFuture<MooValue>> hostWake,
      boolean needsIrrevocable,
      List<PendingFork> pendingForks) {
    SegmentResult {
      output = output.map(List::copyOf);
      pendingForks = List.copyOf(pendingForks);
      boolean returned = output.isPresent();
      boolean boundary = program.isPresent() && snapshot.isPresent();
      int modes = (returned ? 1 : 0) + (boundary ? 1 : 0) + (needsIrrevocable ? 1 : 0);
      if (modes != 1 || program.isPresent() != snapshot.isPresent()) {
        throw new IllegalArgumentException(
            "segment result requires output, a VM boundary, or irrevocable rerun");
      }
    }

    static SegmentResult returned(List<String> output, List<PendingFork> pendingForks) {
      return new SegmentResult(
          Optional.of(output),
          Optional.empty(),
          Optional.empty(),
          Long.MIN_VALUE,
          Optional.empty(),
          Optional.empty(),
          false,
          pendingForks);
    }

    static SegmentResult boundary(
        BytecodeProgram program,
        VmSnapshot snapshot,
        long taskPlayer,
        Optional<MooRuntime.RuntimeContinuation> continuation,
        Optional<CompletableFuture<MooValue>> hostWake,
        List<PendingFork> pendingForks) {
      return new SegmentResult(
          Optional.empty(),
          Optional.of(program),
          Optional.of(snapshot),
          taskPlayer,
          continuation,
          hostWake,
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

    Entry wake(long ticket, MooValue value) {
      return ready(ticket).withWake(value);
    }
  }

  private record TimedWork(SuspendedWork work, double delaySeconds, boolean resume) {}

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
