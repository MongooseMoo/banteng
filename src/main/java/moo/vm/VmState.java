package moo.vm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.BytecodeProgram.HandlerSpec;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.ListValue;

/** Explicit heap state for one MOO bytecode execution. */
public final class VmState {
  private final Deque<Frame> frames = new ArrayDeque<>();
  private final Map<String, MooValue> initialLocals;
  private final List<String> output = new ArrayList<>();
  private Outcome outcome = Outcome.RUNNING;
  private Optional<MooValue> returnValue = Optional.empty();
  private Optional<ErrorValue> pendingError = Optional.empty();
  private Optional<ErrorValue> uncaughtError = Optional.empty();
  private OptionalLong switchedPlayer = OptionalLong.empty();
  private long programmer;

  /** Creates an empty state for a pure root program. */
  public VmState() {
    this(Map.of(), 0);
  }

  /** Creates a state with explicit verb locals and task permissions. */
  public VmState(Map<String, MooValue> locals, long programmer) {
    initialLocals = normalizedLocals(locals);
    this.programmer = programmer;
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
    return programmer;
  }

  void ensureRoot(BytecodeProgram program) {
    if (frames.isEmpty()) {
      frames.push(new Frame(program, initialLocals, ReturnMode.ROOT));
    }
  }

  Frame currentFrame() {
    Frame frame = frames.peekFirst();
    if (frame == null) {
      throw new IllegalStateException("VM has no active frame");
    }
    return frame;
  }

  void pushEvalFrame(BytecodeProgram program) {
    frames.push(new Frame(program, currentFrame().locals, ReturnMode.EVAL));
  }

  void finishFrame(MooValue value) {
    Frame frame = currentFrame();
    if (frame.returnMode == ReturnMode.ROOT) {
      returnValue = Optional.of(value);
      outcome = Outcome.RETURNED;
      return;
    }
    frames.removeFirst();
    currentFrame()
        .operandStack
        .push(new ListValue(List.of(new moo.value.MooValue.IntegerValue(1), value)));
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

  void switchPlayer(long player) {
    switchedPlayer = OptionalLong.of(player);
  }

  void setProgrammer(long programmer) {
    this.programmer = programmer;
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
    int instructionPointer;

    Frame(BytecodeProgram program, Map<String, MooValue> locals, ReturnMode returnMode) {
      this.program = program;
      this.locals = normalizedLocals(locals);
      this.returnMode = returnMode;
    }
  }

  static final class ActiveHandler {
    final HandlerSpec specification;
    HandlerPhase phase = HandlerPhase.TRY;

    ActiveHandler(HandlerSpec specification) {
      this.specification = specification;
    }
  }

  record FinallyContinuation(
      ContinuationKind kind,
      int normalTarget,
      Optional<MooValue> returnValue,
      Optional<ErrorValue> error) {}

  static final class LoopCursor {
    final ListValue values;
    int nextIndex;

    LoopCursor(ListValue values) {
      this.values = values;
    }
  }

  enum ReturnMode {
    ROOT,
    EVAL
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
    RETURNED,
    ERRORED
  }
}
