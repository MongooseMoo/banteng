package world.mongoose.banteng.vm;

import java.util.ArrayDeque;
import world.mongoose.banteng.world.SuspendedActivation.BuiltinContinuation;
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
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.BytecodeProgram.HandlerSpec;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;

/**
 * Explicit heap state for one MOO bytecode execution.
 *
 * <p>This is single-owner mutable task state. A scheduler worker owns an instance for one execution
 * segment, then publishes an immutable {@link VmSnapshot}; no {@code VmState} instance is shared
 * between workers or accessed concurrently.
 */
public final class VmState {
  private final Deque<Frame> frames = new ArrayDeque<>();
  private Outcome outcome = Outcome.RUNNING;
  private Optional<MooValue> returnValue = Optional.empty();
  private Optional<MooValue> pendingError = Optional.empty();
  private Optional<MooValue> uncaughtError = Optional.empty();
  private Optional<ForkRequest> forkRequest = Optional.empty();
  private OptionalDouble suspensionDelaySeconds = OptionalDouble.empty();
  private Optional<Callable<BuiltinResult>> hostWork = Optional.empty();
  private boolean awaitingHostResult;
  private Optional<VmSnapshot.PendingBuiltin> pendingBuiltin = Optional.empty();
  private MooValue taskLocal = new MapValue(Map.of());
  private TaskOrigin origin;
  private final TaskBudget budget;
  private final EffectOutbox effects = new EffectOutbox();

  /** Creates a task from immutable origin metadata and its owned resource budget. */
  public VmState(TaskOrigin origin, TaskBudget budget) {
    this.origin = Objects.requireNonNull(origin, "origin");
    this.budget = Objects.requireNonNull(budget, "budget");
  }

  public TaskBudget budget() { return budget; }

  public EffectOutbox effects() { return effects; }

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
  public Optional<MooValue> pendingError() {
    return pendingError;
  }

  /** Returns an uncaught MOO error after execution terminates. */
  public Optional<MooValue> uncaughtError() {
    return uncaughtError;
  }

  /** Returns the current task programmer. */
  public long programmer() {
    Frame frame = frames.peekFirst();
    return frame == null ? origin.programmer() : frame.programmer;
  }

  MooValue taskLocal() {
    return taskLocal;
  }

  void setTaskLocal(MooValue value) {
    taskLocal = value;
  }

  void abortTickExhaustion() {
    pendingError = Optional.empty();
    effects.stageOutput("Task ran out of ticks");
    outcome = Outcome.ABORTED;
  }

  void abortSecondsExhaustion() {
    pendingError = Optional.empty();
    effects.stageOutput("Task ran out of seconds");
    outcome = Outcome.SECONDS_EXHAUSTED;
  }

  void abortKilled() {
    pendingError = Optional.empty();
    outcome = Outcome.KILLED;
  }

  public void ensureRoot(BytecodeProgram program) {
    if (frames.isEmpty()) {
      MooValue receiver = origin.locals().getOrDefault("this", new ObjectValue(-1));
      pushFrame(program, origin.locals(), new FrameOrigin(
          ReturnMode.ROOT, origin.programmer(), receiver, origin.verbLocation(),
          origin.calledVerb(), origin.fullVerbName(), Optional.empty(), OptionalLong.empty(),
          OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(), Optional.empty(), origin.debug()));
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

  /** Named activation metadata and lifecycle continuations for the single frame push. */
  public record FrameOrigin(
      ReturnMode returnMode, long programmer, MooValue receiver, MooValue verbLocation,
      String calledVerb, String fullVerbName, Optional<MooValue> createReturnOverride,
      OptionalLong recycleTarget, OptionalLong moveObject, OptionalLong moveDestination,
      OptionalLong movePosition, Optional<AnonymousObjectValue> anonymousRecycleTarget,
      boolean debug) {}

  boolean pushFrame(BytecodeProgram program, Map<String, MooValue> locals, FrameOrigin origin) {
    if (origin.returnMode() != ReturnMode.ROOT && frames.size() >= budget.maxStackDepth()) {
      return false;
    }
    if (origin.returnMode() == ReturnMode.ROOT) {
      Map<String, MooValue> merged = new LinkedHashMap<>(this.origin.locals());
      merged.putAll(locals);
      this.origin = new TaskOrigin(merged, this.origin.programmer(), this.origin.verbLocation(),
          this.origin.calledVerb(), this.origin.fullVerbName(), this.origin.debug());
    }
    frames.push(new Frame(program, locals, origin.returnMode(), origin.programmer(),
        origin.receiver(), origin.verbLocation(), origin.calledVerb(), origin.fullVerbName(),
        origin.createReturnOverride(), origin.recycleTarget(), origin.moveObject(),
        origin.moveDestination(), origin.movePosition(), origin.anonymousRecycleTarget(),
        origin.debug(), true));
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
    List<Frame> stack = List.copyOf(frames);
    for (int index = 0; index < stack.size(); index++) {
      Frame frame = stack.get(index);
      if (!current) {
        List<MooValue> fields = new ArrayList<>();
        fields.add(frame.receiver);
        fields.add(StringValue.of(frame.calledVerb));
        fields.add(new ObjectValue(frame.programmer));
        fields.add(frame.verbLocation);
        fields.add(frame.localOrDefault(5, new ObjectValue(-1)));
        if (lineNumbers) {
          int callInstruction =
              Math.max(
                  0,
                  Math.min(
                      frame.instructionPointer - 1, frame.program.instructions().size() - 1));
          fields.add(
              new world.mongoose.banteng.value.MooValue.IntegerValue(
                  frame.program.instructions().get(callInstruction).sourceLine()));
        }
        callers.add(new ListValue(fields));
      } else {
        current = false;
      }
      if (index + 1 < stack.size()) {
        frame.builtinStackFrame(stack.get(index + 1), lineNumbers).ifPresent(callers::add);
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
          .push(new ListValue(List.of(new world.mongoose.banteng.value.MooValue.IntegerValue(1), value)));
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

  void beginError(MooValue error) {
    pendingError = Optional.of(error);
  }

  void clearPendingError() {
    pendingError = Optional.empty();
  }

  void failUncaught(MooValue error) {
    failUncaught(error, Optional.empty());
  }

  void failUncaught(MooValue error, ListValue exception) {
    failUncaught(error, Optional.of(exception));
  }

  private void failUncaught(MooValue error, Optional<ListValue> exception) {
    pendingError = Optional.empty();
    uncaughtError = Optional.of(error);
    returnValue = exception.map(value -> (MooValue) value);
    outcome = Outcome.ERRORED;
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
                frame.namedLocals(),
                frame.programmer,
                frame.verbLocation,
                frame.calledVerb,
                frame.fullVerbName,
                frame.debug,
                delaySeconds));
    outcome = Outcome.FORKED;
  }

  /** Binds the assigned child ID before copying the fork's environment. */
  public VmSnapshot.Fork continueAfterFork(IntegerValue taskId) {
    if (outcome != Outcome.FORKED || forkRequest.isEmpty()) {
      throw new IllegalStateException("VM is not at a fork boundary");
    }
    ForkRequest request = forkRequest.orElseThrow();
    Map<String, MooValue> childLocals = new LinkedHashMap<>(request.locals());
    Frame frame = currentFrame();
    // The compiler emits the fork's named binding immediately after FORK.
    var binding = frame.program.instructions().get(frame.instructionPointer);
    if (binding.opcode() == BytecodeProgram.Opcode.STORE_LOCAL) {
      childLocals.put(frame.program.variableNames().get(Math.toIntExact(binding.operand().orElseThrow())), taskId);
    }
    forkRequest = Optional.empty();
    currentFrame().operandStack.push(taskId);
    outcome = Outcome.RUNNING;
    return new VmSnapshot.Fork(request.program(), childLocals, request.programmer(),
        request.verbLocation(), request.calledVerb(), request.fullVerbName(),
        request.debug(), request.delaySeconds());
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
    return snapshot(TaskBudget.currentThreadCpuNanos());
  }

  VmSnapshot snapshot(long currentThreadCpuNanos) {
    TaskBudget.Capture captured = budget.capture(currentThreadCpuNanos);

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
        origin.locals(),
        origin.programmer(),
        origin.verbLocation(),
        origin.calledVerb(),
        origin.fullVerbName(),
        frameSnapshots,
        List.copyOf(effects.output),
        List.copyOf(effects.connectionOptionRequests),
        List.copyOf(effects.bootPlayerTargets),
        List.copyOf(effects.forcedInputRequests),
        List.copyOf(effects.notificationRequests),
        List.copyOf(effects.checkpointRequests),
        List.copyOf(effects.anonymousCollectionDeferrals),
        outcome,
        returnValue,
        pendingError,
        uncaughtError,
        forkSnapshot,
        suspensionDelaySeconds,
        awaitingHostResult,
        pendingBuiltin,
        taskLocal,
        captured.remainingTicks(),
        captured.elapsedCpuNanos(),
        captured.remainingCpuNanos(),
        captured.maxStackDepth());
  }

  /** Restores a task from a value-only retry or checkpoint snapshot. */
  public static VmState restore(VmSnapshot snapshot) {
    return restore(snapshot, TaskBudget.restore(
        snapshot.remainingTicks(), snapshot.elapsedCpuNanos(),
        snapshot.remainingCpuNanos(), snapshot.maxStackDepth()));
  }

  /** Restores a suspended task with a fresh background execution budget. */
  public static VmState restoreBackground(
      VmSnapshot snapshot, long remainingTicks, long secondsLimit, long maxStackDepth) {
    return restore(snapshot, new TaskBudget(remainingTicks, secondsLimit, maxStackDepth));
  }

  private static VmState restore(VmSnapshot snapshot, TaskBudget budget) {
    VmState state = new VmState(
        new TaskOrigin(snapshot.initialLocals(), snapshot.initialProgrammer(),
            snapshot.initialVerbLocation(), snapshot.initialCalledVerb(), snapshot.initialFullVerbName(),
            snapshot.frames().isEmpty() || snapshot.frames().getLast().debug()),
        budget);
    for (VmSnapshot.Frame frame : snapshot.frames()) {
      state.frames.addLast(restore(frame));
    }
    state.effects.output.addAll(snapshot.output());
    state.effects.connectionOptionRequests.addAll(snapshot.connectionOptionRequests());
    state.effects.bootPlayerTargets.addAll(snapshot.bootPlayerTargets());
    state.effects.forcedInputRequests.addAll(snapshot.forcedInputRequests());
    state.effects.notificationRequests.addAll(snapshot.notificationRequests());
    state.effects.checkpointRequests.addAll(snapshot.checkpointRequests());
    state.effects.anonymousCollectionDeferrals.addAll(snapshot.anonymousCollectionDeferrals());
    state.outcome = snapshot.outcome();
    state.returnValue = snapshot.returnValue();
    state.pendingError = snapshot.pendingError();
    state.uncaughtError = snapshot.uncaughtError();
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
        frame.namedLocals(),
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
        frame.anonymousRecycleTarget,
        frame.builtinContinuation);
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
    frame.builtinContinuation = snapshot.builtinContinuation();
    Map<String, MooValue> normalized = normalizedLocals(snapshot.locals());
    for (int slot = 0; slot < frame.locals.length; slot++) {
      frame.locals[slot] = normalized.get(frame.program.variableNames().get(slot));
    }
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

  private static Map<String, MooValue> normalizedLocals(Map<String, MooValue> locals) {
    Map<String, MooValue> normalized = new LinkedHashMap<>();
    locals.forEach((name, value) -> normalized.put(name.toLowerCase(Locale.ROOT), value));
    return normalized;
  }

  /** Initial runtime constants for importing older named activation environments. */
  public static Map<String, MooValue> constantLocals(BytecodeProgram program) {
    Map<String, MooValue> constants = new LinkedHashMap<>();
    for (int slot = 0; slot < Frame.CONSTANT_VALUES.length; slot++) {
      MooValue value = Frame.CONSTANT_VALUES[slot];
      if (value != null) constants.put(program.variableNames().get(slot), value);
    }
    return constants;
  }

  static final class Frame {
    private static final @org.jspecify.annotations.Nullable MooValue[] CONSTANT_VALUES = constantValues();

    private static @org.jspecify.annotations.Nullable MooValue[] constantValues() {
      @org.jspecify.annotations.Nullable MooValue[] values = new MooValue[24];
      int[] slots = {0, 1, 2, 3, 4, 16, 17, 18, 19, 20, 21};
      MooValue.Type[] types = {MooValue.Type.INTEGER, MooValue.Type.OBJECT,
          MooValue.Type.STRING, MooValue.Type.LIST, MooValue.Type.ERROR, MooValue.Type.INTEGER,
          MooValue.Type.FLOAT, MooValue.Type.MAP, MooValue.Type.ANONYMOUS, MooValue.Type.WAIF,
          MooValue.Type.BOOLEAN};
      for (int index = 0; index < slots.length; index++) {
        values[slots[index]] = new MooValue.IntegerValue(types[index].code());
      }
      values[22] = MooValue.BooleanValue.TRUE;
      values[23] = MooValue.BooleanValue.FALSE;
      return values;
    }

    final BytecodeProgram program;
    final Deque<MooValue> operandStack = new ArrayDeque<>();
    final Deque<IndexContext> indexCollections = new ArrayDeque<>();
    final @org.jspecify.annotations.Nullable MooValue[] locals;
    final Deque<ActiveHandler> handlers = new ArrayDeque<>();
    final Deque<VmSnapshot.FinallyState> finallyContinuations = new ArrayDeque<>();
    final Map<Integer, LoopCursor> loops = new LinkedHashMap<>();
    final ReturnMode returnMode;
    Optional<BuiltinContinuation> builtinContinuation = Optional.empty();
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

    Optional<ListValue> builtinStackFrame(Frame parent, boolean lines) {
      return VmSnapshot.builtinStackFrame(builtinContinuation,
          VmSnapshot.ReturnMode.valueOf(returnMode.name()), createReturnOverride.isPresent(),
          recycleTarget.isPresent() || anonymousRecycleTarget.isPresent(),
          parent.program, parent.instructionPointer, localOrDefault(5, new ObjectValue(-1)),
          Map.of(), lines, false);
    }

    MooValue localOrDefault(int slot, MooValue fallback) {
      MooValue value = locals[slot];
      return value == null ? fallback : value;
    }

    Map<String, MooValue> namedLocals() {
      Map<String, MooValue> named = new LinkedHashMap<>();
      for (int slot = 0; slot < locals.length; slot++) {
        MooValue value = locals[slot];
        if (value != null) named.put(program.variableNames().get(slot), value);
      }
      return named;
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
      this.locals = java.util.Arrays.copyOf(CONSTANT_VALUES, program.variableNames().size());
      Map<String, MooValue> normalized = normalizedLocals(locals);
      for (int slot = 0; slot < this.locals.length; slot++) {
        String name = program.variableNames().get(slot);
        if (normalized.containsKey(name)) this.locals[slot] = normalized.get(name);
      }
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

  public enum ReturnMode {
    ROOT,
    EVAL,
    VERB,
    MOVE_ACCEPT,
    MOVE_EXIT,
    MOVE_ENTER
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
    ABORTED,
    SECONDS_EXHAUSTED,
    KILLED
  }
}
