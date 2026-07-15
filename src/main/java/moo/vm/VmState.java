package moo.vm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import moo.builtin.BuiltinCatalog.ConnectionOptionRequest;
import moo.builtin.BuiltinCatalog.ForcedInputRequest;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.BytecodeProgram.HandlerSpec;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;

/** Explicit heap state for one MOO bytecode execution. */
public final class VmState {
  private static final long DEFAULT_FOREGROUND_TICKS = 60_000;
  private static final long DEFAULT_FOREGROUND_SECONDS = 5;
  private static final long DEFAULT_MAX_STACK_DEPTH = 50;

  private final Deque<Frame> frames = new ArrayDeque<>();
  private final Map<String, MooValue> initialLocals;
  private final List<String> output = new ArrayList<>();
  private final List<ConnectionOptionRequest> connectionOptionRequests = new ArrayList<>();
  private final List<Long> bootPlayerTargets = new ArrayList<>();
  private final List<ForcedInputRequest> forcedInputRequests = new ArrayList<>();
  private Outcome outcome = Outcome.RUNNING;
  private Optional<MooValue> returnValue = Optional.empty();
  private Optional<ErrorValue> pendingError = Optional.empty();
  private Optional<ErrorValue> uncaughtError = Optional.empty();
  private OptionalLong switchedPlayer = OptionalLong.empty();
  private Optional<ForkRequest> forkRequest = Optional.empty();
  private OptionalDouble suspensionDelaySeconds = OptionalDouble.empty();
  private Optional<CompletableFuture<MooValue>> hostResult = Optional.empty();
  private MooValue taskLocal = new MapValue(Map.of());
  private long remainingTicks;
  private final long secondsLimit;
  private final long maxStackDepth;
  private long processCpuAnchorNanos;
  private final long initialProgrammer;
  private final ObjectValue initialVerbLocation;

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
  public VmState(Map<String, MooValue> locals, long programmer, ObjectValue verbLocation) {
    this(locals, programmer, verbLocation, DEFAULT_FOREGROUND_TICKS, DEFAULT_FOREGROUND_SECONDS);
  }

  /** Creates a state with explicit root metadata and remaining ticks. */
  public VmState(
      Map<String, MooValue> locals,
      long programmer,
      ObjectValue verbLocation,
      long remainingTicks) {
    this(locals, programmer, verbLocation, remainingTicks, 0);
  }

  /** Creates a state with explicit root metadata and execution limits. */
  public VmState(
      Map<String, MooValue> locals,
      long programmer,
      ObjectValue verbLocation,
      long remainingTicks,
      long secondsLimit) {
    this(locals, programmer, verbLocation, remainingTicks, secondsLimit, DEFAULT_MAX_STACK_DEPTH);
  }

  /** Creates a state with explicit root metadata and all execution limits. */
  public VmState(
      Map<String, MooValue> locals,
      long programmer,
      ObjectValue verbLocation,
      long remainingTicks,
      long secondsLimit,
      long maxStackDepth) {
    initialLocals = normalizedLocals(locals);
    initialProgrammer = programmer;
    initialVerbLocation = verbLocation;
    this.remainingTicks = remainingTicks;
    this.secondsLimit = secondsLimit;
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
  public Optional<CompletableFuture<MooValue>> hostResult() {
    return hostResult;
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
    long limitNanos = Math.max(0L, TimeUnit.SECONDS.toNanos(secondsLimit));
    long currentProcessCpuNanos =
        ProcessHandle.current().info().totalCpuDuration().orElseThrow().toNanos();
    long elapsedNanos = Math.max(0L, currentProcessCpuNanos - processCpuAnchorNanos);
    long remainingNanos = Math.max(0L, limitNanos - Math.min(limitNanos, elapsedNanos));
    return TimeUnit.NANOSECONDS.toSeconds(remainingNanos);
  }

  void decrementRemainingTicks() {
    remainingTicks--;
  }

  void ensureRoot(BytecodeProgram program) {
    if (frames.isEmpty()) {
      processCpuAnchorNanos =
          ProcessHandle.current().info().totalCpuDuration().orElseThrow().toNanos();
      MooValue receiver = initialLocals.getOrDefault("this", new ObjectValue(-1));
      frames.push(
          new Frame(
              program,
              initialLocals,
              ReturnMode.ROOT,
              initialProgrammer,
              receiver,
              initialVerbLocation,
              OptionalLong.empty(),
              OptionalLong.empty(),
              OptionalLong.empty()));
    }
  }

  Frame currentFrame() {
    Frame frame = frames.peekFirst();
    if (frame == null) {
      throw new IllegalStateException("VM has no active frame");
    }
    return frame;
  }

  boolean pushEvalFrame(BytecodeProgram program) {
    if (frames.size() >= maxStackDepth) {
      return false;
    }
    Frame caller = currentFrame();
    frames.push(
        new Frame(
            program,
            caller.locals,
            ReturnMode.EVAL,
            caller.programmer,
            caller.receiver,
            caller.verbLocation,
            OptionalLong.empty(),
            OptionalLong.empty(),
            OptionalLong.empty()));
    return true;
  }

  boolean pushVerbFrame(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      MooValue receiver,
      ObjectValue verbLocation,
      OptionalLong recycleTarget,
      OptionalLong moveObject,
      OptionalLong moveDestination) {
    if (frames.size() >= maxStackDepth) {
      return false;
    }
    frames.push(
        new Frame(
            program,
            locals,
            ReturnMode.VERB,
            programmer,
            receiver,
            verbLocation,
            recycleTarget,
            moveObject,
            moveDestination));
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
    List<MooValue> callers = new ArrayList<>();
    boolean current = true;
    for (Frame frame : frames) {
      if (current) {
        current = false;
        continue;
      }
      callers.add(
          new ListValue(
              List.of(
                  frame.receiver,
                  frame.locals.getOrDefault("verb", new StringValue(new byte[0])),
                  new ObjectValue(frame.programmer),
                  frame.verbLocation,
                  frame.locals.getOrDefault("player", new ObjectValue(-1)))));
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
      currentFrame().operandStack.push(value);
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
    pendingError = Optional.empty();
    uncaughtError = Optional.of(error);
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
                program, frame.locals, frame.programmer, frame.verbLocation, delaySeconds));
    outcome = Outcome.FORKED;
  }

  /** Clears a queued fork boundary so the parent continues before its child runs. */
  public void continueAfterFork() {
    if (outcome != Outcome.FORKED || forkRequest.isEmpty()) {
      throw new IllegalStateException("VM is not at a fork boundary");
    }
    forkRequest = Optional.empty();
    outcome = Outcome.RUNNING;
  }

  void suspend(OptionalDouble delaySeconds, Optional<CompletableFuture<MooValue>> externalResult) {
    if (delaySeconds.isPresent() == externalResult.isPresent()) {
      throw new IllegalArgumentException("suspension requires exactly one wake source");
    }
    suspensionDelaySeconds = delaySeconds;
    hostResult = externalResult;
    outcome = Outcome.SUSPENDED;
  }

  /** Resumes this exact captured VM and supplies the suspended builtin's value. */
  public void resume(MooValue value) {
    if (outcome != Outcome.SUSPENDED) {
      throw new IllegalStateException("VM is not suspended");
    }
    suspensionDelaySeconds = OptionalDouble.empty();
    hostResult = Optional.empty();
    currentFrame().operandStack.push(value);
    outcome = Outcome.RUNNING;
  }

  private static Map<String, MooValue> normalizedLocals(Map<String, MooValue> locals) {
    Map<String, MooValue> normalized = new LinkedHashMap<>();
    locals.forEach((name, value) -> normalized.put(name.toLowerCase(Locale.ROOT), value));
    return normalized;
  }

  static final class Frame {
    final BytecodeProgram program;
    final Deque<MooValue> operandStack = new ArrayDeque<>();
    final Map<String, MooValue> locals;
    final Deque<ActiveHandler> handlers = new ArrayDeque<>();
    final Deque<FinallyContinuation> finallyContinuations = new ArrayDeque<>();
    final Map<Integer, LoopCursor> loops = new LinkedHashMap<>();
    final ReturnMode returnMode;
    final MooValue receiver;
    final ObjectValue verbLocation;
    final OptionalLong recycleTarget;
    final OptionalLong moveObject;
    final OptionalLong moveDestination;
    long programmer;
    int instructionPointer;

    Frame(
        BytecodeProgram program,
        Map<String, MooValue> locals,
        ReturnMode returnMode,
        long programmer,
        MooValue receiver,
        ObjectValue verbLocation,
        OptionalLong recycleTarget,
        OptionalLong moveObject,
        OptionalLong moveDestination) {
      this.program = program;
      this.locals = normalizedLocals(locals);
      this.returnMode = returnMode;
      this.programmer = programmer;
      this.receiver = receiver;
      this.verbLocation = verbLocation;
      this.recycleTarget = recycleTarget;
      this.moveObject = moveObject;
      this.moveDestination = moveDestination;
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

  record FinallyContinuation(
      ContinuationKind kind,
      int normalTarget,
      Optional<MooValue> returnValue,
      Optional<ErrorValue> error) {}

  /** Immutable child state captured when a fork instruction queues work. */
  public record ForkRequest(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      ObjectValue verbLocation,
      double delaySeconds) {
    public ForkRequest {
      locals = Map.copyOf(locals);
    }
  }

  static final class LoopCursor {
    final ListValue values;
    int nextIndex;

    LoopCursor(ListValue values) {
      this.values = values;
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

  enum ContinuationKind {
    NORMAL,
    RETURN,
    ERROR
  }

  /** Terminal status held directly in VM state. */
  public enum Outcome {
    RUNNING,
    FORKED,
    SUSPENDED,
    RETURNED,
    ERRORED
  }
}
