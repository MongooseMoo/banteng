package moo.vm;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import moo.builtin.BuiltinResult;
import moo.builtin.BuiltinCatalog.ConnectionOptionRequest;
import moo.builtin.BuiltinCatalog.ForcedInputRequest;
import moo.builtin.CheckpointRequest;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.BytecodeProgram.HandlerSpec;
import moo.value.MooValue;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;

/**
 * Explicit heap state for one MOO bytecode execution.
 *
 * <p>This is single-owner mutable task state. A scheduler worker owns an instance for one execution
 * segment, then publishes an immutable {@link VmSnapshot}; no {@code VmState} instance is shared
 * between workers or accessed concurrently.
 */
public final class VmState {
  private static final long DEFAULT_FOREGROUND_TICKS = 60_000;
  private static final long DEFAULT_FOREGROUND_SECONDS = 5;
  private static final long DEFAULT_MAX_STACK_DEPTH = 50;
  private static final ThreadMXBean THREAD_CPU = ManagementFactory.getThreadMXBean();

  private final Deque<Frame> frames = new ArrayDeque<>();
  private final Map<String, MooValue> initialLocals;
  private final List<String> output = new ArrayList<>();
  private final List<ConnectionOptionRequest> connectionOptionRequests = new ArrayList<>();
  private final List<Long> bootPlayerTargets = new ArrayList<>();
  private final List<ForcedInputRequest> forcedInputRequests = new ArrayList<>();
  private final List<CheckpointRequest> checkpointRequests = new ArrayList<>();
  private final List<AnonymousObjectValue> anonymousCollectionDeferrals = new ArrayList<>();
  private Outcome outcome = Outcome.RUNNING;
  private Optional<MooValue> returnValue = Optional.empty();
  private Optional<ErrorValue> pendingError = Optional.empty();
  private Optional<ErrorValue> uncaughtError = Optional.empty();
  private OptionalLong switchedPlayer = OptionalLong.empty();
  private Optional<ForkRequest> forkRequest = Optional.empty();
  private OptionalDouble suspensionDelaySeconds = OptionalDouble.empty();
  private Optional<Callable<BuiltinResult>> hostWork = Optional.empty();
  private boolean awaitingHostResult;
  private Optional<VmSnapshot.PendingBuiltin> pendingBuiltin = Optional.empty();
  private MooValue taskLocal = new MapValue(Map.of());
  private long remainingTicks;
  private long elapsedCpuNanos;
  private long remainingCpuNanos;
  private final long maxStackDepth;
  private long segmentCpuAnchorNanos = -1;
  private final long initialProgrammer;
  private final MooValue initialVerbLocation;
  private final String initialCalledVerb;
  private final String initialFullVerbName;
  private final boolean initialDebug;

  /** Creates an empty state for a pure root program. */
  public VmState() {
    this(Map.of(), 0, new ObjectValue(-1));
  }

  /** Creates a state with explicit verb locals and task permissions. */
  public VmState(Map<String, MooValue> locals, long programmer) {
    this(
        locals,
        programmer,
        normalizedLocals(locals).get("this") instanceof ObjectValue object
            ? object
            : new ObjectValue(-1),
        DEFAULT_FOREGROUND_TICKS,
        DEFAULT_FOREGROUND_SECONDS);
  }

  /** Creates a state with explicit root verb metadata. */
  public VmState(Map<String, MooValue> locals, long programmer, MooValue verbLocation) {
    this(locals, programmer, verbLocation, DEFAULT_FOREGROUND_TICKS, DEFAULT_FOREGROUND_SECONDS);
  }

  /** Creates a state with explicit root metadata and remaining ticks. */
  public VmState(
      Map<String, MooValue> locals,
      long programmer,
      MooValue verbLocation,
      long remainingTicks) {
    this(locals, programmer, verbLocation, remainingTicks, 0);
  }

  /** Creates a state with explicit root metadata and execution limits. */
  public VmState(
      Map<String, MooValue> locals,
      long programmer,
      MooValue verbLocation,
      long remainingTicks,
      long secondsLimit) {
    this(locals, programmer, verbLocation, remainingTicks, secondsLimit, DEFAULT_MAX_STACK_DEPTH);
  }

  /** Creates a state with explicit root metadata and all execution limits. */
  public VmState(
      Map<String, MooValue> locals,
      long programmer,
      MooValue verbLocation,
      long remainingTicks,
      long secondsLimit,
      long maxStackDepth) {
    this(locals, programmer, verbLocation, remainingTicks, secondsLimit, maxStackDepth, true);
  }

  /** Creates a state with explicit root metadata, execution limits, and verb debug mode. */
  public VmState(
      Map<String, MooValue> locals,
      long programmer,
      MooValue verbLocation,
      long remainingTicks,
      long secondsLimit,
      long maxStackDepth,
      boolean debug) {
    this(
        locals,
        programmer,
        verbLocation,
        remainingTicks,
        secondsLimit,
        maxStackDepth,
        debug,
        calledVerb(locals),
        calledVerb(locals));
  }

  /** Creates a state with explicit root activation dispatch metadata. */
  public VmState(
      Map<String, MooValue> locals,
      long programmer,
      MooValue verbLocation,
      long remainingTicks,
      long secondsLimit,
      long maxStackDepth,
      boolean debug,
      String calledVerb,
      String fullVerbName) {
    initialLocals = normalizedLocals(locals);
    initialProgrammer = programmer;
    initialVerbLocation = verbLocation;
    initialCalledVerb = Objects.requireNonNull(calledVerb, "calledVerb");
    initialFullVerbName = Objects.requireNonNull(fullVerbName, "fullVerbName");
    initialDebug = debug;
    this.remainingTicks = remainingTicks;
    remainingCpuNanos = Math.max(0L, TimeUnit.SECONDS.toNanos(secondsLimit));
    this.maxStackDepth = maxStackDepth;
  }

  /** Returns the next instruction index in the active frame. */
  public int instructionPointer() {
    Frame frame = frames.peekFirst();
    return frame == null ? 0 : frame.instructionPointer;
  }

  /** Returns an immutable stack snapshot with the top operand first. */
  public List<MooValue> operandStack() {
    Frame frame = frames.peekFirst();
    return frame == null ? List.of() : List.copyOf(frame.operandStack);
  }

  /** Returns whether execution is running, returned, or ended in a MOO error. */
  public Outcome outcome() {
    return outcome;
  }

  /** Returns the child task requested at the current fork boundary. */
  public Optional<ForkRequest> forkRequest() {
    return forkRequest;
  }

  /** Returns the timed delay requested at the current suspension boundary. */
  public OptionalDouble suspensionDelaySeconds() {
    return suspensionDelaySeconds;
  }

  /** Returns the external result that will resume the current suspended task. */
  public Optional<Callable<BuiltinResult>> hostWork() {
    return hostWork;
  }

  /** Returns whether this activation dispatches background-capable builtins to host work. */
  public boolean threadMode() {
    return currentFrame().threadMode;
  }

  /** Applies background-thread mode to this activation only. */
  public void setThreadMode(boolean enabled) {
    currentFrame().threadMode = enabled;
  }

  /** Returns the value-only builtin invocation waiting for publication authorization. */
  public Optional<VmSnapshot.PendingBuiltin> pendingBuiltin() {
    return pendingBuiltin;
  }

  /** Returns the value stored by a completed root return. */
  public Optional<MooValue> returnValue() {
    return returnValue;
  }

  /** Returns the MOO error currently being routed through handlers. */
  public Optional<ErrorValue> pendingError() {
    return pendingError;
  }

  /** Returns an uncaught MOO error after execution terminates. */
  public Optional<ErrorValue> uncaughtError() {
    return uncaughtError;
  }

  /** Returns ordered output staged by this execution. */
  public List<String> output() {
    return List.copyOf(output);
  }

  /** Returns a connection player switch staged by this execution. */
  public OptionalLong switchedPlayer() {
    return switchedPlayer;
  }

  /** Returns the current task programmer. */
  public long programmer() {
    Frame frame = frames.peekFirst();
    return frame == null ? initialProgrammer : frame.programmer;
  }

  MooValue taskLocal() {
    return taskLocal;
  }

  void setTaskLocal(MooValue value) {
    taskLocal = value;
  }

  long remainingTicks() {
    return remainingTicks;
  }

  long remainingSeconds() {
    long elapsedNanos = segmentElapsedCpuNanos(currentThreadCpuNanos());
    long remainingNanos =
        Math.max(0L, remainingCpuNanos - Math.min(remainingCpuNanos, elapsedNanos));
    return TimeUnit.NANOSECONDS.toSeconds(remainingNanos);
  }

  void decrementRemainingTicks() {
    remainingTicks--;
  }

  void abortTickExhaustion() {
    pendingError = Optional.empty();
    output.add("Task ran out of ticks");
    outcome = Outcome.ABORTED;
  }

  void abortSecondsExhaustion() {
    pendingError = Optional.empty();
    output.add("Task ran out of seconds");
    outcome = Outcome.ABORTED;
  }

  public void ensureRoot(BytecodeProgram program) {
    if (frames.isEmpty()) {
      MooValue receiver = initialLocals.getOrDefault("this", new ObjectValue(-1));
      frames.push(
          new Frame(
              program,
              initialLocals,
              ReturnMode.ROOT,
              initialProgrammer,
              receiver,
              initialVerbLocation,
              initialCalledVerb,
              initialFullVerbName,
              Optional.empty(),
              OptionalLong.empty(),
              OptionalLong.empty(),
              OptionalLong.empty(),
              OptionalLong.empty(),
              initialDebug,
              true));
    }
  }

  void beginSegment() {
    beginSegment(currentThreadCpuNanos());
  }

  void beginSegment(long currentThreadCpuNanos) {
    if (segmentCpuAnchorNanos < 0) {
      segmentCpuAnchorNanos = currentThreadCpuNanos;
    }
  }

  Frame currentFrame() {
    Frame frame = frames.peekFirst();
    if (frame == null) {
      throw new IllegalStateException("VM has no active frame");
    }
    return frame;
  }

  List<Frame> activeFrames() {
    return List.copyOf(frames);
  }

  boolean pushEvalFrame(BytecodeProgram program) {
    if (frames.size() >= maxStackDepth) {
      return false;
    }
    Frame caller = currentFrame();
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("player", caller.locals.getOrDefault("player", new ObjectValue(-1)));
    locals.put("caller", caller.receiver);
    locals.put("this", new ObjectValue(-1));
    locals.put("dobj", new ObjectValue(-1));
    locals.put("iobj", new ObjectValue(-1));
    locals.put("dobjstr", StringValue.of(new byte[0]));
    locals.put("iobjstr", StringValue.of(new byte[0]));
    locals.put("argstr", StringValue.of(new byte[0]));
    locals.put("prepstr", StringValue.of(new byte[0]));
    locals.put("verb", StringValue.of(new byte[0]));
    locals.put("args", new ListValue(List.of()));
    frames.push(
        new Frame(
            program,
            locals,
            ReturnMode.EVAL,
            caller.programmer,
            new ObjectValue(-1),
            new ObjectValue(-1),
            "",
            "Input to EVAL",
            Optional.empty(),
            OptionalLong.empty(),
            OptionalLong.empty(),
            OptionalLong.empty(),
            OptionalLong.empty(),
            true,
            caller.threadMode));
    return true;
  }

  boolean pushVerbFrame(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      MooValue receiver,
      MooValue verbLocation,
      OptionalLong recycleTarget,
      OptionalLong moveObject,
      OptionalLong moveDestination) {
    return pushVerbFrame(
        program,
        locals,
        programmer,
        receiver,
        verbLocation,
        recycleTarget,
        moveObject,
        moveDestination,
        calledVerb(locals),
        calledVerb(locals),
        true);
  }

  boolean pushVerbFrame(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      MooValue receiver,
      MooValue verbLocation,
      OptionalLong recycleTarget,
      OptionalLong moveObject,
      OptionalLong moveDestination,
      String calledVerb,
      String fullVerbName,
      boolean debug) {
    return pushVerbFrame(
        program,
        locals,
        programmer,
        receiver,
        verbLocation,
        recycleTarget,
        moveObject,
        moveDestination,
        OptionalLong.empty(),
        calledVerb,
        fullVerbName,
        debug);
  }

  boolean pushVerbFrame(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      MooValue receiver,
      MooValue verbLocation,
      OptionalLong recycleTarget,
      OptionalLong moveObject,
      OptionalLong moveDestination,
      OptionalLong movePosition) {
    return pushVerbFrame(
        program,
        locals,
        programmer,
        receiver,
        verbLocation,
        recycleTarget,
        moveObject,
        moveDestination,
        movePosition,
        calledVerb(locals),
        calledVerb(locals),
        true);
  }

  boolean pushVerbFrame(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      MooValue receiver,
      MooValue verbLocation,
      OptionalLong recycleTarget,
      OptionalLong moveObject,
      OptionalLong moveDestination,
      OptionalLong movePosition,
      String calledVerb,
      String fullVerbName,
      boolean debug) {
    return pushVerbFrame(
        program,
        locals,
        programmer,
        receiver,
        verbLocation,
        Optional.empty(),
        recycleTarget,
        moveObject,
        moveDestination,
        movePosition,
        calledVerb,
        fullVerbName,
        debug);
  }

  boolean pushCreateInitializeFrame(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      MooValue receiver,
      MooValue verbLocation,
      MooValue created) {
    return pushCreateInitializeFrame(
        program,
        locals,
        programmer,
        receiver,
        verbLocation,
        created,
        calledVerb(locals),
        calledVerb(locals),
        true);
  }

  boolean pushCreateInitializeFrame(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      MooValue receiver,
      MooValue verbLocation,
      MooValue created,
      String calledVerb,
      String fullVerbName,
      boolean debug) {
    return pushVerbFrame(
        program,
        locals,
        programmer,
        receiver,
        verbLocation,
        Optional.of(created),
        OptionalLong.empty(),
        OptionalLong.empty(),
        OptionalLong.empty(),
        OptionalLong.empty(),
        calledVerb,
        fullVerbName,
        debug);
  }

  boolean pushAnonymousRecycleFrame(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      AnonymousObjectValue receiver,
      MooValue verbLocation) {
    return pushAnonymousRecycleFrame(
        program,
        locals,
        programmer,
        receiver,
        verbLocation,
        calledVerb(locals),
        calledVerb(locals),
        true);
  }

  boolean pushAnonymousRecycleFrame(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      AnonymousObjectValue receiver,
      MooValue verbLocation,
      String calledVerb,
      String fullVerbName,
      boolean debug) {
    return pushVerbFrame(
        program,
        locals,
        programmer,
        receiver,
        verbLocation,
        Optional.empty(),
        OptionalLong.empty(),
        OptionalLong.empty(),
        OptionalLong.empty(),
        OptionalLong.empty(),
        Optional.of(receiver),
        calledVerb,
        fullVerbName,
        debug);
  }

  private boolean pushVerbFrame(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      MooValue receiver,
      MooValue verbLocation,
      Optional<MooValue> createReturnOverride,
      OptionalLong recycleTarget,
      OptionalLong moveObject,
      OptionalLong moveDestination,
      OptionalLong movePosition,
      String calledVerb,
      String fullVerbName,
      boolean debug) {
    return pushVerbFrame(
        program,
        locals,
        programmer,
        receiver,
        verbLocation,
        createReturnOverride,
        recycleTarget,
        moveObject,
        moveDestination,
        movePosition,
        Optional.empty(),
        calledVerb,
        fullVerbName,
        debug);
  }

  private boolean pushVerbFrame(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      MooValue receiver,
      MooValue verbLocation,
      Optional<MooValue> createReturnOverride,
      OptionalLong recycleTarget,
      OptionalLong moveObject,
      OptionalLong moveDestination,
      OptionalLong movePosition,
      Optional<AnonymousObjectValue> anonymousRecycleTarget,
      String calledVerb,
      String fullVerbName,
      boolean debug) {
    if (frames.size() >= maxStackDepth) {
      return false;
    }
    boolean inheritedThreadMode = currentFrame().threadMode;
    frames.push(
        new Frame(
            program,
            locals,
            ReturnMode.VERB,
            programmer,
            receiver,
            verbLocation,
            calledVerb,
            fullVerbName,
            createReturnOverride,
            recycleTarget,
            moveObject,
            moveDestination,
            movePosition,
            anonymousRecycleTarget,
            debug,
            inheritedThreadMode));
    return true;
  }

  long callerProgrammer() {
    boolean current = true;
    for (Frame frame : frames) {
      if (current) {
        current = false;
      } else {
        return frame.programmer;
      }
    }
    return programmer();
  }

  ListValue callers() {
    return callers(false);
  }

  ListValue callers(boolean lineNumbers) {
    List<MooValue> callers = new ArrayList<>();
    boolean current = true;
    for (Frame frame : frames) {
      if (!current) {
        List<MooValue> fields = new ArrayList<>();
        fields.add(frame.receiver);
        fields.add(frame.locals.getOrDefault("verb", StringValue.of(new byte[0])));
        fields.add(new ObjectValue(frame.programmer));
        fields.add(frame.verbLocation);
        fields.add(frame.locals.getOrDefault("player", new ObjectValue(-1)));
        if (lineNumbers) {
          int callInstruction =
              Math.max(
                  0,
                  Math.min(
                      frame.instructionPointer - 1, frame.program.instructions().size() - 1));
          fields.add(
              new moo.value.MooValue.IntegerValue(
                  frame.program.instructions().get(callInstruction).sourceLine()));
        }
        callers.add(new ListValue(fields));
      } else {
        current = false;
      }
      if (frame.returnMode == ReturnMode.EVAL) {
        List<MooValue> fields =
            new ArrayList<>(
                List.of(
                    new ObjectValue(-1),
                    StringValue.of("eval"),
                    new ObjectValue(-1),
                    new ObjectValue(-1),
                    frame.locals.getOrDefault("player", new ObjectValue(-1))));
        if (lineNumbers) {
          fields.add(new moo.value.MooValue.IntegerValue(2));
        }
        callers.add(new ListValue(fields));
      }
    }
    return new ListValue(callers);
  }

  void finishFrame(MooValue value) {
    Frame frame = currentFrame();
    if (frame.returnMode == ReturnMode.ROOT) {
      returnValue = Optional.of(value);
      outcome = Outcome.RETURNED;
      return;
    }
    frames.removeFirst();
    if (frame.returnMode == ReturnMode.EVAL) {
      currentFrame()
          .operandStack
          .push(new ListValue(List.of(new moo.value.MooValue.IntegerValue(1), value)));
    } else {
      currentFrame().operandStack.push(frame.createReturnOverride.orElse(value));
    }
  }

  boolean unwindChildFrame() {
    if (currentFrame().returnMode == ReturnMode.ROOT) {
      return false;
    }
    frames.removeFirst();
    return true;
  }

  void beginError(ErrorValue error) {
    pendingError = Optional.of(error);
  }

  void clearPendingError() {
    pendingError = Optional.empty();
  }

  void failUncaught(ErrorValue error) {
    failUncaught(error, Optional.empty());
  }

  void failUncaught(ErrorValue error, ListValue exception) {
    failUncaught(error, Optional.of(exception));
  }

  private void failUncaught(ErrorValue error, Optional<ListValue> exception) {
    pendingError = Optional.empty();
    uncaughtError = Optional.of(error);
    returnValue = exception.map(value -> (MooValue) value);
    outcome = Outcome.ERRORED;
  }

  void stageOutput(String line) {
    output.add(line);
  }

  void stageConnectionOptionRequest(ConnectionOptionRequest request) {
    connectionOptionRequests.add(request);
  }

  /** Removes and returns connection-option requests in their task execution order. */
  public List<ConnectionOptionRequest> drainConnectionOptionRequests() {
    List<ConnectionOptionRequest> requests = List.copyOf(connectionOptionRequests);
    connectionOptionRequests.clear();
    return requests;
  }

  void stageBootPlayerTarget(long target) {
    bootPlayerTargets.add(target);
  }

  /** Removes and returns boot-player targets in their task execution order. */
  public List<Long> drainBootPlayerTargets() {
    List<Long> targets = List.copyOf(bootPlayerTargets);
    bootPlayerTargets.clear();
    return targets;
  }

  void stageForcedInputRequest(ForcedInputRequest request) {
    forcedInputRequests.add(request);
  }

  /** Removes and returns forced-input requests in their task execution order. */
  public List<ForcedInputRequest> drainForcedInputRequests() {
    List<ForcedInputRequest> requests = List.copyOf(forcedInputRequests);
    forcedInputRequests.clear();
    return requests;
  }

  void stageCheckpointRequest(CheckpointRequest request) {
    checkpointRequests.add(request);
  }

  /** Removes and returns checkpoint requests in task execution order. */
  public List<CheckpointRequest> drainCheckpointRequests() {
    List<CheckpointRequest> requests = List.copyOf(checkpointRequests);
    checkpointRequests.clear();
    return requests;
  }

  void deferAnonymousCollection(List<AnonymousObjectValue> identities) {
    for (AnonymousObjectValue identity : identities) {
      if (!anonymousCollectionDeferrals.contains(identity)) {
        anonymousCollectionDeferrals.add(identity);
      }
    }
  }

  /** Returns anonymous identities excluded from collection for this task's publication. */
  public List<AnonymousObjectValue> anonymousCollectionDeferrals() {
    return List.copyOf(anonymousCollectionDeferrals);
  }

  void switchPlayer(long player) {
    switchedPlayer = OptionalLong.of(player);
  }

  void setProgrammer(long programmer) {
    currentFrame().programmer = programmer;
  }

  void requestFork(BytecodeProgram program, double delaySeconds) {
    Frame frame = currentFrame();
    forkRequest =
        Optional.of(
            new ForkRequest(
                program,
                frame.locals,
                frame.programmer,
                frame.verbLocation,
                frame.calledVerb,
                frame.fullVerbName,
                frame.debug,
                delaySeconds));
    outcome = Outcome.FORKED;
  }

  /** Clears a published fork boundary and supplies its assigned child task ID. */
  public void continueAfterFork(IntegerValue taskId) {
    if (outcome != Outcome.FORKED || forkRequest.isEmpty()) {
      throw new IllegalStateException("VM is not at a fork boundary");
    }
    forkRequest = Optional.empty();
    currentFrame().operandStack.push(taskId);
    outcome = Outcome.RUNNING;
  }

  void suspend(OptionalDouble delaySeconds, Optional<Callable<BuiltinResult>> externalWork) {
    if (delaySeconds.isPresent() == externalWork.isPresent()) {
      throw new IllegalArgumentException("suspension requires exactly one wake source");
    }
    suspensionDelaySeconds = delaySeconds;
    hostWork = externalWork;
    awaitingHostResult = externalWork.isPresent();
    outcome = Outcome.SUSPENDED;
  }

  /** Resumes this exact captured VM and supplies the suspended builtin's value. */
  public void resume(MooValue value) {
    if (outcome != Outcome.SUSPENDED) {
      throw new IllegalStateException("VM is not suspended");
    }
    suspensionDelaySeconds = OptionalDouble.empty();
    hostWork = Optional.empty();
    awaitingHostResult = false;
    currentFrame().operandStack.push(value);
    outcome = Outcome.RUNNING;
  }

  void resumeError() {
    if (outcome != Outcome.SUSPENDED) {
      throw new IllegalStateException("VM is not suspended");
    }
    suspensionDelaySeconds = OptionalDouble.empty();
    hostWork = Optional.empty();
    awaitingHostResult = false;
    outcome = Outcome.RUNNING;
  }

  void yieldBuiltin(VmSnapshot.PendingBuiltin request) {
    if (outcome != Outcome.RUNNING || pendingBuiltin.isPresent()) {
      throw new IllegalStateException("VM cannot yield another builtin request");
    }
    pendingBuiltin = Optional.of(request);
    outcome = Outcome.PENDING_BUILTIN;
  }

  VmSnapshot.PendingBuiltin authorizePendingBuiltin() {
    if (outcome != Outcome.PENDING_BUILTIN || pendingBuiltin.isEmpty()) {
      throw new IllegalStateException("VM has no pending builtin request");
    }
    VmSnapshot.PendingBuiltin request = pendingBuiltin.orElseThrow();
    pendingBuiltin = Optional.empty();
    outcome = Outcome.RUNNING;
    return request;
  }

  /** Captures this task as value-only state for retry or checkpoint storage. */
  public VmSnapshot snapshot() {
    return snapshot(currentThreadCpuNanos());
  }

  VmSnapshot snapshot(long currentThreadCpuNanos) {
    long segmentElapsedCpuNanos = segmentElapsedCpuNanos(currentThreadCpuNanos);
    long chargedCpuNanos = Math.min(remainingCpuNanos, segmentElapsedCpuNanos);
    long capturedRemainingCpuNanos = remainingCpuNanos - chargedCpuNanos;
    long capturedElapsedCpuNanos = saturatedAdd(elapsedCpuNanos, chargedCpuNanos);

    List<VmSnapshot.Frame> frameSnapshots = new ArrayList<>(frames.size());
    for (Frame frame : frames) {
      frameSnapshots.add(snapshot(frame));
    }
    Optional<VmSnapshot.Fork> forkSnapshot =
        forkRequest.map(
            request ->
                new VmSnapshot.Fork(
                    request.program(),
                    request.locals(),
                    request.programmer(),
                    request.verbLocation(),
                    request.calledVerb(),
                    request.fullVerbName(),
                    request.debug(),
                    request.delaySeconds()));
    return new VmSnapshot(
        initialLocals,
        initialProgrammer,
        initialVerbLocation,
        initialCalledVerb,
        initialFullVerbName,
        frameSnapshots,
        output,
        connectionOptionRequests,
        bootPlayerTargets,
        forcedInputRequests,
        checkpointRequests,
        anonymousCollectionDeferrals,
        outcome,
        returnValue,
        pendingError,
        uncaughtError,
        switchedPlayer,
        forkSnapshot,
        suspensionDelaySeconds,
        awaitingHostResult,
        pendingBuiltin,
        taskLocal,
        remainingTicks,
        capturedElapsedCpuNanos,
        capturedRemainingCpuNanos,
        maxStackDepth);
  }

  /** Restores a task from a value-only retry or checkpoint snapshot. */
  public static VmState restore(VmSnapshot snapshot) {
    return restore(
        snapshot,
        snapshot.remainingTicks(),
        snapshot.elapsedCpuNanos(),
        snapshot.remainingCpuNanos(),
        snapshot.maxStackDepth());
  }

  /** Restores a suspended task with a fresh background execution budget. */
  public static VmState restoreBackground(
      VmSnapshot snapshot, long remainingTicks, long secondsLimit, long maxStackDepth) {
    return restore(
        snapshot,
        remainingTicks,
        0,
        Math.max(0L, TimeUnit.SECONDS.toNanos(secondsLimit)),
        maxStackDepth);
  }

  private static VmState restore(
      VmSnapshot snapshot,
      long remainingTicks,
      long elapsedCpuNanos,
      long remainingCpuNanos,
      long maxStackDepth) {
    VmState state =
        new VmState(
            snapshot.initialLocals(),
            snapshot.initialProgrammer(),
            snapshot.initialVerbLocation(),
            remainingTicks,
            0,
            maxStackDepth,
            snapshot.frames().isEmpty() || snapshot.frames().getLast().debug(),
            snapshot.initialCalledVerb(),
            snapshot.initialFullVerbName());
    for (VmSnapshot.Frame frame : snapshot.frames()) {
      state.frames.addLast(restore(frame));
    }
    state.output.addAll(snapshot.output());
    state.connectionOptionRequests.addAll(snapshot.connectionOptionRequests());
    state.bootPlayerTargets.addAll(snapshot.bootPlayerTargets());
    state.forcedInputRequests.addAll(snapshot.forcedInputRequests());
    state.checkpointRequests.addAll(snapshot.checkpointRequests());
    state.anonymousCollectionDeferrals.addAll(snapshot.anonymousCollectionDeferrals());
    state.outcome = snapshot.outcome();
    state.returnValue = snapshot.returnValue();
    state.pendingError = snapshot.pendingError();
    state.uncaughtError = snapshot.uncaughtError();
    state.switchedPlayer = snapshot.switchedPlayer();
    state.forkRequest =
        snapshot
            .forkRequest()
            .map(
                request ->
                    new ForkRequest(
                        request.program(),
                        request.locals(),
                        request.programmer(),
                        request.verbLocation(),
                        request.calledVerb(),
                        request.fullVerbName(),
                        request.debug(),
                        request.delaySeconds()));
    state.suspensionDelaySeconds = snapshot.suspensionDelaySeconds();
    state.awaitingHostResult = snapshot.awaitingHostResult();
    state.hostWork = Optional.empty();
    state.pendingBuiltin = snapshot.pendingBuiltin();
    state.taskLocal = snapshot.taskLocal();
    state.elapsedCpuNanos = elapsedCpuNanos;
    state.remainingCpuNanos = remainingCpuNanos;
    state.segmentCpuAnchorNanos = -1;
    return state;
  }

  private static VmSnapshot.Frame snapshot(Frame frame) {
    List<VmSnapshot.HandlerState> handlers = new ArrayList<>(frame.handlers.size());
    for (ActiveHandler handler : frame.handlers) {
      handlers.add(
          new VmSnapshot.HandlerState(
              handler.specification,
              handler.operandDepth,
              VmSnapshot.HandlerPhase.valueOf(handler.phase.name())));
    }
    List<VmSnapshot.FinallyState> finallyStates =
        List.copyOf(frame.finallyContinuations);
    List<VmSnapshot.IndexState> indexStates =
        new ArrayList<>(frame.indexCollections.size());
    for (IndexContext context : frame.indexCollections) {
      indexStates.add(
          new VmSnapshot.IndexState(
              context.collection(), context.key(), context.operandDepth()));
    }
    Map<Integer, VmSnapshot.LoopState> loops = new LinkedHashMap<>();
    frame.loops.forEach(
        (instruction, cursor) ->
            loops.put(instruction, cursor.snapshot()));
    return new VmSnapshot.Frame(
        frame.program,
        List.copyOf(frame.operandStack),
        indexStates,
        frame.locals,
        handlers,
        finallyStates,
        loops,
        VmSnapshot.ReturnMode.valueOf(frame.returnMode.name()),
        frame.receiver,
        frame.verbLocation,
        frame.calledVerb,
        frame.fullVerbName,
        frame.createReturnOverride,
        frame.recycleTarget,
        frame.moveObject,
        frame.moveDestination,
        frame.movePosition,
        frame.programmer,
        frame.debug,
        frame.threadMode,
        frame.instructionPointer,
        frame.anonymousRecycleTarget);
  }

  private static Frame restore(VmSnapshot.Frame snapshot) {
    Frame frame =
        new Frame(
            snapshot.program(),
            snapshot.locals(),
            ReturnMode.valueOf(snapshot.returnMode().name()),
            snapshot.programmer(),
            snapshot.receiver(),
            snapshot.verbLocation(),
            snapshot.calledVerb(),
            snapshot.fullVerbName(),
            snapshot.createReturnOverride(),
            snapshot.recycleTarget(),
            snapshot.moveObject(),
            snapshot.moveDestination(),
            snapshot.movePosition(),
            snapshot.anonymousRecycleTarget(),
            snapshot.debug(),
            snapshot.threadMode());
    frame.operandStack.addAll(snapshot.operandStack());
    for (VmSnapshot.IndexState index : snapshot.indexCollections()) {
      frame.indexCollections.addLast(
          new IndexContext(index.collection(), index.key(), index.operandDepth()));
    }
    for (VmSnapshot.HandlerState handler : snapshot.handlers()) {
      ActiveHandler restored =
          new ActiveHandler(handler.specification(), handler.operandDepth());
      restored.phase = HandlerPhase.valueOf(handler.phase().name());
      frame.handlers.addLast(restored);
    }
    frame.finallyContinuations.addAll(snapshot.finallyStates());
    snapshot
        .loops()
        .forEach(
            (instruction, loop) -> {
              LoopCursor cursor =
                  switch (loop) {
                    case VmSnapshot.CollectionLoop collection ->
                        new CollectionCursor(
                            collection.kind(), collection.base(), collection.next());
                    case VmSnapshot.RangeLoop range ->
                        new RangeCursor(range.kind(), range.next(), range.end());
                  };
              frame.loops.put(instruction, cursor);
            });
    frame.instructionPointer = snapshot.instructionPointer();
    return frame;
  }

  private long segmentElapsedCpuNanos(long currentThreadCpuNanos) {
    return segmentCpuAnchorNanos < 0
        ? 0
        : Math.max(0L, currentThreadCpuNanos - segmentCpuAnchorNanos);
  }

  private static long currentThreadCpuNanos() {
    long nanos = THREAD_CPU.getCurrentThreadCpuTime();
    if (nanos < 0) {
      throw new IllegalStateException("current-thread CPU time is unavailable");
    }
    return nanos;
  }

  private static long saturatedAdd(long left, long right) {
    return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
  }

  private static Map<String, MooValue> normalizedLocals(Map<String, MooValue> locals) {
    Map<String, MooValue> normalized = new LinkedHashMap<>();
    locals.forEach((name, value) -> normalized.put(name.toLowerCase(Locale.ROOT), value));
    return normalized;
  }

  private static String calledVerb(Map<String, MooValue> locals) {
    MooValue verb = locals.get("verb");
    return verb instanceof StringValue string
        ? string.text()
        : "";
  }

  static final class Frame {
    final BytecodeProgram program;
    final Deque<MooValue> operandStack = new ArrayDeque<>();
    final Deque<IndexContext> indexCollections = new ArrayDeque<>();
    final Map<String, MooValue> locals;
    final Deque<ActiveHandler> handlers = new ArrayDeque<>();
    final Deque<VmSnapshot.FinallyState> finallyContinuations = new ArrayDeque<>();
    final Map<Integer, LoopCursor> loops = new LinkedHashMap<>();
    final ReturnMode returnMode;
    final MooValue receiver;
    final MooValue verbLocation;
    final String calledVerb;
    final String fullVerbName;
    final Optional<MooValue> createReturnOverride;
    final OptionalLong recycleTarget;
    final OptionalLong moveObject;
    final OptionalLong moveDestination;
    final OptionalLong movePosition;
    final Optional<AnonymousObjectValue> anonymousRecycleTarget;
    final boolean debug;
    long programmer;
    boolean threadMode;
    int instructionPointer;

    Frame(
        BytecodeProgram program,
        Map<String, MooValue> locals,
        ReturnMode returnMode,
        long programmer,
        MooValue receiver,
        MooValue verbLocation,
        String calledVerb,
        String fullVerbName,
        Optional<MooValue> createReturnOverride,
        OptionalLong recycleTarget,
        OptionalLong moveObject,
        OptionalLong moveDestination,
        OptionalLong movePosition,
        boolean debug,
        boolean threadMode) {
      this(
          program,
          locals,
          returnMode,
          programmer,
          receiver,
          verbLocation,
          calledVerb,
          fullVerbName,
          createReturnOverride,
          recycleTarget,
          moveObject,
          moveDestination,
          movePosition,
          Optional.empty(),
          debug,
          threadMode);
    }

    Frame(
        BytecodeProgram program,
        Map<String, MooValue> locals,
        ReturnMode returnMode,
        long programmer,
        MooValue receiver,
        MooValue verbLocation,
        String calledVerb,
        String fullVerbName,
        Optional<MooValue> createReturnOverride,
        OptionalLong recycleTarget,
        OptionalLong moveObject,
        OptionalLong moveDestination,
        OptionalLong movePosition,
        Optional<AnonymousObjectValue> anonymousRecycleTarget,
        boolean debug,
        boolean threadMode) {
      this.program = program;
      this.locals = normalizedLocals(locals);
      this.returnMode = returnMode;
      this.programmer = programmer;
      this.receiver = receiver;
      this.verbLocation = verbLocation;
      this.calledVerb = Objects.requireNonNull(calledVerb, "calledVerb");
      this.fullVerbName = Objects.requireNonNull(fullVerbName, "fullVerbName");
      this.createReturnOverride = createReturnOverride;
      this.recycleTarget = recycleTarget;
      this.moveObject = moveObject;
      this.moveDestination = moveDestination;
      this.movePosition = movePosition;
      this.anonymousRecycleTarget = anonymousRecycleTarget;
      this.debug = debug;
      this.threadMode = threadMode;
    }
  }

  static final class ActiveHandler {
    final HandlerSpec specification;
    final int operandDepth;
    HandlerPhase phase = HandlerPhase.TRY;

    ActiveHandler(HandlerSpec specification, int operandDepth) {
      this.specification = specification;
      this.operandDepth = operandDepth;
    }
  }

  /** Immutable child state captured when a fork instruction queues work. */
  public record ForkRequest(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      MooValue verbLocation,
      String calledVerb,
      String fullVerbName,
      boolean debug,
      double delaySeconds) {
    public ForkRequest {
      locals = Collections.unmodifiableMap(new LinkedHashMap<>(locals));
      Objects.requireNonNull(calledVerb, "calledVerb");
      Objects.requireNonNull(fullVerbName, "fullVerbName");
    }
  }

  record IndexContext(MooValue collection, Optional<MooValue> key, int operandDepth) {}

  sealed interface LoopCursor permits CollectionCursor, RangeCursor {
    VmSnapshot.LoopState snapshot();
  }

  static final class CollectionCursor implements LoopCursor {
    final VmSnapshot.CollectionKind kind;
    final MooValue base;
    Optional<MooValue> next;

    CollectionCursor(
        VmSnapshot.CollectionKind kind, MooValue base, Optional<MooValue> next) {
      this.kind = kind;
      this.base = base;
      this.next = next;
    }

    @Override
    public VmSnapshot.CollectionLoop snapshot() {
      return new VmSnapshot.CollectionLoop(kind, base, next);
    }
  }

  static final class RangeCursor implements LoopCursor {
    final VmSnapshot.RangeKind kind;
    MooValue next;
    MooValue end;

    RangeCursor(VmSnapshot.RangeKind kind, MooValue next, MooValue end) {
      this.kind = kind;
      this.next = next;
      this.end = end;
    }

    @Override
    public VmSnapshot.RangeLoop snapshot() {
      return new VmSnapshot.RangeLoop(kind, next, end);
    }
  }

  enum ReturnMode {
    ROOT,
    EVAL,
    VERB
  }

  enum HandlerPhase {
    TRY,
    CATCH
  }

  /** Terminal status held directly in VM state. */
  public enum Outcome {
    RUNNING,
    FORKED,
    SUSPENDED,
    PENDING_BUILTIN,
    RETURNED,
    ERRORED,
    ABORTED
  }
}
