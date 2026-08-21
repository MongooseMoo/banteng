package world.mongoose.banteng.vm;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import world.mongoose.banteng.builtin.BuiltinCatalog.ConnectionOptionRequest;
import world.mongoose.banteng.builtin.BuiltinCatalog.ForcedInputRequest;
import world.mongoose.banteng.builtin.CheckpointRequest;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.BytecodeProgram.HandlerSpec;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.BooleanValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.WaifValue;

/** Value-only durable state for one MOO task at an execution boundary. */
public record VmSnapshot(
    Map<String, MooValue> initialLocals,
    long initialProgrammer,
    MooValue initialVerbLocation,
    String initialCalledVerb,
    String initialFullVerbName,
    List<Frame> frames,
    List<String> output,
    List<ConnectionOptionRequest> connectionOptionRequests,
    List<Long> bootPlayerTargets,
    List<ForcedInputRequest> forcedInputRequests,
    List<CheckpointRequest> checkpointRequests,
    List<AnonymousObjectValue> anonymousCollectionDeferrals,
    VmState.Outcome outcome,
    Optional<MooValue> returnValue,
    Optional<ErrorValue> pendingError,
    Optional<ErrorValue> uncaughtError,
    OptionalLong switchedPlayer,
    Optional<Fork> forkRequest,
    OptionalDouble suspensionDelaySeconds,
    boolean awaitingHostResult,
    Optional<PendingBuiltin> pendingBuiltin,
    MooValue taskLocal,
    long remainingTicks,
    long elapsedCpuNanos,
    long remainingCpuNanos,
    long maxStackDepth) {
  public VmSnapshot {
    initialLocals =
        Collections.unmodifiableMap(new LinkedHashMap<>(initialLocals));
    Objects.requireNonNull(initialCalledVerb, "initialCalledVerb");
    Objects.requireNonNull(initialFullVerbName, "initialFullVerbName");
    frames = List.copyOf(frames);
    output = List.copyOf(output);
    connectionOptionRequests = List.copyOf(connectionOptionRequests);
    bootPlayerTargets = List.copyOf(bootPlayerTargets);
    forcedInputRequests = List.copyOf(forcedInputRequests);
    checkpointRequests = List.copyOf(checkpointRequests);
    anonymousCollectionDeferrals = List.copyOf(anonymousCollectionDeferrals);
    if (elapsedCpuNanos < 0
        || remainingCpuNanos < 0
        || maxStackDepth < 1) {
      throw new IllegalArgumentException("negative VM limit state");
    }
    if (outcome == VmState.Outcome.SUSPENDED
        && suspensionDelaySeconds.isPresent() == awaitingHostResult) {
      throw new IllegalArgumentException("suspended snapshot requires exactly one wake kind");
    }
    if (outcome != VmState.Outcome.SUSPENDED
        && (suspensionDelaySeconds.isPresent() || awaitingHostResult)) {
      throw new IllegalArgumentException("only suspended snapshots have a wake kind");
    }
    if ((outcome == VmState.Outcome.PENDING_BUILTIN) != pendingBuiltin.isPresent()) {
      throw new IllegalArgumentException("pending builtin outcome requires one request");
    }
  }

  /**
   * Returns the deterministic logical byte size of the durable VM payload retained by this
   * snapshot.
   *
   * <p>Fixed-width scalar fields contribute their primitive width. Collections contribute their
   * element count and every retained occurrence recursively. MOO strings contribute their owned
   * binary bytes, while Java text fields contribute their UTF-8 bytes. Programs include their
   * instructions, handlers, source, and fork vectors. This is task billing, so shared values and
   * programs are counted at each retained occurrence. It is neither a JVM object-layout estimate
   * nor the length of a rendered or serialized snapshot.
   */
  public long byteSize() {
    long size = stringValueMapSize(initialLocals);
    size = add(size, Long.BYTES);
    size = add(size, valueSize(initialVerbLocation));
    size = add(size, textSize(initialCalledVerb));
    size = add(size, textSize(initialFullVerbName));
    size = add(size, Integer.BYTES);
    for (Frame frame : frames) {
      size = add(size, frameSize(frame));
    }
    size = add(size, Integer.BYTES);
    for (String line : output) {
      size = add(size, textSize(line));
    }
    size = add(size, Integer.BYTES);
    for (ConnectionOptionRequest request : connectionOptionRequests) {
      size = add(size, Long.BYTES);
      size = add(size, Byte.BYTES);
      size = add(size, valueSize(request.value()));
    }
    size = add(size, Integer.BYTES);
    size = add(size, multiply(bootPlayerTargets.size(), Long.BYTES));
    size = add(size, Integer.BYTES);
    for (ForcedInputRequest request : forcedInputRequests) {
      size = add(size, Long.BYTES);
      size = add(size, textSize(request.line()));
    }
    size = add(size, Integer.BYTES);
    size = add(size, multiply(checkpointRequests.size(), Byte.BYTES));
    size = add(size, Integer.BYTES);
    for (AnonymousObjectValue anonymous : anonymousCollectionDeferrals) {
      size = add(size, valueSize(anonymous));
    }
    size = add(size, Byte.BYTES);
    size = add(size, optionalValueSize(returnValue));
    size = add(size, optionalValueSize(pendingError.map(error -> error)));
    size = add(size, optionalValueSize(uncaughtError.map(error -> error)));
    size = add(size, Byte.BYTES);
    if (switchedPlayer.isPresent()) {
      size = add(size, Long.BYTES);
    }
    size = add(size, Byte.BYTES);
    if (forkRequest.isPresent()) {
      size = add(size, forkSize(forkRequest.orElseThrow()));
    }
    size = add(size, Byte.BYTES);
    if (suspensionDelaySeconds.isPresent()) {
      size = add(size, Double.BYTES);
    }
    size = add(size, Byte.BYTES);
    size = add(size, Byte.BYTES);
    if (pendingBuiltin.isPresent()) {
      size = add(size, pendingBuiltinSize(pendingBuiltin.orElseThrow()));
    }
    size = add(size, valueSize(taskLocal));
    size = add(size, multiply(4, Long.BYTES));
    return size;
  }

  private static long frameSize(Frame frame) {
    long size = programSize(frame.program());
    size = add(size, valueListSize(frame.operandStack()));
    size = add(size, Integer.BYTES);
    for (IndexState index : frame.indexCollections()) {
      size = add(size, valueSize(index.collection()));
      size = add(size, optionalValueSize(index.key()));
      size = add(size, Integer.BYTES);
    }
    size = add(size, stringValueMapSize(frame.locals()));
    size = add(size, Integer.BYTES);
    for (HandlerState handler : frame.handlers()) {
      size = add(size, handlerSpecSize(handler.specification()));
      size = add(size, Integer.BYTES);
      size = add(size, Byte.BYTES);
    }
    size = add(size, Integer.BYTES);
    for (FinallyState state : frame.finallyStates()) {
      size = add(size, finallySize(state));
    }
    size = add(size, Integer.BYTES);
    for (Map.Entry<Integer, LoopState> entry : frame.loops().entrySet()) {
      size = add(size, Integer.BYTES);
      size = add(size, loopSize(entry.getValue()));
    }
    size = add(size, Byte.BYTES);
    size = add(size, valueSize(frame.receiver()));
    size = add(size, valueSize(frame.verbLocation()));
    size = add(size, textSize(frame.calledVerb()));
    size = add(size, textSize(frame.fullVerbName()));
    size = add(size, optionalValueSize(frame.createReturnOverride()));
    size = add(size, optionalLongSize(frame.recycleTarget()));
    size = add(size, optionalValueSize(frame.anonymousRecycleTarget()));
    size = add(size, optionalLongSize(frame.moveObject()));
    size = add(size, optionalLongSize(frame.moveDestination()));
    size = add(size, optionalLongSize(frame.movePosition()));
    size = add(size, Long.BYTES);
    size = add(size, Byte.BYTES);
    size = add(size, Byte.BYTES);
    return add(size, Integer.BYTES);
  }

  private static long loopSize(LoopState loop) {
    if (loop instanceof CollectionLoop collection) {
      long size = add(Byte.BYTES, valueSize(collection.base()));
      return add(size, optionalValueSize(collection.next()));
    }
    RangeLoop range = (RangeLoop) loop;
    long size = add(Byte.BYTES, valueSize(range.next()));
    return add(size, valueSize(range.end()));
  }

  private static long finallySize(FinallyState state) {
    long size = Byte.BYTES;
    if (state instanceof FallThrough) {
      return add(size, Integer.BYTES);
    }
    if (state instanceof Raise raise) {
      return add(size, valueSize(raise.exception()));
    }
    if (state instanceof Uncaught uncaught) {
      return add(size, valueSize(uncaught.value()));
    }
    if (state instanceof Return returned) {
      return add(size, valueSize(returned.value()));
    }
    if (!(state instanceof Exit)) {
      throw new AssertionError(state);
    }
    return add(size, multiply(2, Integer.BYTES));
  }

  private static long forkSize(Fork fork) {
    long size = programSize(fork.program());
    size = add(size, stringValueMapSize(fork.locals()));
    size = add(size, Long.BYTES);
    size = add(size, valueSize(fork.verbLocation()));
    size = add(size, textSize(fork.calledVerb()));
    size = add(size, textSize(fork.fullVerbName()));
    size = add(size, Byte.BYTES);
    return add(size, Double.BYTES);
  }

  private static long pendingBuiltinSize(PendingBuiltin pending) {
    long size = textSize(pending.name());
    size = add(size, valueListSize(pending.arguments()));
    size = add(size, Long.BYTES);
    size = add(size, valueSize(pending.taskLocal()));
    size = add(size, multiply(2, Long.BYTES));
    size = add(size, valueSize(pending.receiver()));
    size = add(size, Long.BYTES);
    return add(size, valueSize(pending.callers()));
  }

  private static long programSize(BytecodeProgram program) {
    long size = Integer.BYTES;
    for (BytecodeProgram.Instruction instruction : program.instructions()) {
      size = add(size, Byte.BYTES);
      size = add(size, Byte.BYTES);
      if (instruction.operand().isPresent()) {
        size = add(size, Long.BYTES);
      }
      size = add(size, Byte.BYTES);
      if (instruction.text().isPresent()) {
        size = add(size, textSize(instruction.text().orElseThrow()));
      }
      size = add(size, Byte.BYTES);
      if (instruction.handler().isPresent()) {
        size = add(size, handlerSpecSize(instruction.handler().orElseThrow()));
      }
      size = add(size, Integer.BYTES);
    }
    size = add(size, Integer.BYTES);
    for (BytecodeProgram forkVector : program.forkVectors()) {
      size = add(size, programSize(forkVector));
    }
    return add(size, textSize(program.source()));
  }

  private static long handlerSpecSize(HandlerSpec handler) {
    long size = multiply(3, Integer.BYTES);
    size = add(size, Byte.BYTES);
    if (handler.catchVariable().isPresent()) {
      size = add(size, textSize(handler.catchVariable().orElseThrow()));
    }
    size = add(size, Byte.BYTES);
    size = add(size, Integer.BYTES);
    for (String error : handler.caughtErrors()) {
      size = add(size, textSize(error));
    }
    return add(size, Byte.BYTES);
  }

  private static long stringValueMapSize(Map<String, ? extends MooValue> values) {
    long size = Integer.BYTES;
    for (Map.Entry<String, ? extends MooValue> entry : values.entrySet()) {
      size = add(size, textSize(entry.getKey()));
      size = add(size, valueSize(entry.getValue()));
    }
    return size;
  }

  private static long valueListSize(List<? extends MooValue> values) {
    long size = Integer.BYTES;
    for (MooValue value : values) {
      size = add(size, valueSize(value));
    }
    return size;
  }

  private static long optionalValueSize(Optional<? extends MooValue> value) {
    return value.isPresent() ? add(Byte.BYTES, valueSize(value.orElseThrow())) : Byte.BYTES;
  }

  private static long optionalLongSize(OptionalLong value) {
    return value.isPresent() ? Byte.BYTES + Long.BYTES : Byte.BYTES;
  }

  private static long valueSize(MooValue value) {
    return switch (value) {
      case IntegerValue ignored -> Byte.BYTES + Long.BYTES;
      case BooleanValue ignored -> Byte.BYTES + Byte.BYTES;
      case FloatValue ignored -> Byte.BYTES + Double.BYTES;
      case StringValue string -> add(Byte.BYTES + Integer.BYTES, string.length());
      case ObjectValue ignored -> Byte.BYTES + Long.BYTES;
      case AnonymousObjectValue ignored -> Byte.BYTES + Long.BYTES;
      case WaifValue waif ->
          add(Byte.BYTES, add(valueSize(waif.classObject()), valueSize(waif.owner())));
      case ErrorValue ignored -> Byte.BYTES + Integer.BYTES;
      case ListValue list -> add(Byte.BYTES, valueListSize(list.elements()));
      case MapValue map -> {
        long size = Byte.BYTES + Integer.BYTES;
        for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
          size = add(size, valueSize(entry.getKey()));
          size = add(size, valueSize(entry.getValue()));
        }
        yield size;
      }
    };
  }

  private static long textSize(String value) {
    return add(Integer.BYTES, value.getBytes(StandardCharsets.UTF_8).length);
  }

  private static long add(long left, long right) {
    return Math.addExact(left, right);
  }

  private static long multiply(long left, long right) {
    return Math.multiplyExact(left, right);
  }

  /** Value-only state for one activation frame, ordered from current to root. */
  public record Frame(
      BytecodeProgram program,
      List<MooValue> operandStack,
      List<IndexState> indexCollections,
      Map<String, MooValue> locals,
      List<HandlerState> handlers,
      List<FinallyState> finallyStates,
      Map<Integer, LoopState> loops,
      ReturnMode returnMode,
      MooValue receiver,
      MooValue verbLocation,
      String calledVerb,
      String fullVerbName,
      Optional<MooValue> createReturnOverride,
      OptionalLong recycleTarget,
      OptionalLong moveObject,
      OptionalLong moveDestination,
      OptionalLong movePosition,
      long programmer,
      boolean debug,
      boolean threadMode,
      int instructionPointer,
      Optional<AnonymousObjectValue> anonymousRecycleTarget) {
    public Frame(
        BytecodeProgram program,
        List<MooValue> operandStack,
        List<IndexState> indexCollections,
        Map<String, MooValue> locals,
        List<HandlerState> handlers,
        List<FinallyState> finallyStates,
        Map<Integer, LoopState> loops,
        ReturnMode returnMode,
        MooValue receiver,
        MooValue verbLocation,
        String calledVerb,
        String fullVerbName,
        Optional<MooValue> createReturnOverride,
        OptionalLong recycleTarget,
        OptionalLong moveObject,
        OptionalLong moveDestination,
        OptionalLong movePosition,
        long programmer,
        boolean threadMode,
        int instructionPointer) {
      this(
          program,
          operandStack,
          indexCollections,
          locals,
          handlers,
          finallyStates,
          loops,
          returnMode,
          receiver,
          verbLocation,
          calledVerb,
          fullVerbName,
          createReturnOverride,
          recycleTarget,
          moveObject,
          moveDestination,
          movePosition,
          programmer,
          true,
          threadMode,
          instructionPointer,
          Optional.empty());
    }

    public Frame {
      operandStack = List.copyOf(operandStack);
      indexCollections = List.copyOf(indexCollections);
      locals = Collections.unmodifiableMap(new LinkedHashMap<>(locals));
      handlers = List.copyOf(handlers);
      finallyStates = List.copyOf(finallyStates);
      loops = Collections.unmodifiableMap(new LinkedHashMap<>(loops));
      Objects.requireNonNull(calledVerb, "calledVerb");
      Objects.requireNonNull(fullVerbName, "fullVerbName");
      if (instructionPointer < 0 || instructionPointer > program.instructions().size()) {
        throw new IllegalArgumentException("instruction pointer outside program");
      }
    }
  }

  /** One collection-update context held across nested index evaluation. */
  public record IndexState(MooValue collection, Optional<MooValue> key, int operandDepth) {
    public IndexState {
      if (operandDepth < 0) {
        throw new IllegalArgumentException("negative operand depth");
      }
    }
  }

  /** One active exception handler and its current execution phase. */
  public record HandlerState(HandlerSpec specification, int operandDepth, HandlerPhase phase) {
    public HandlerState {
      if (operandDepth < 0) {
        throw new IllegalArgumentException("negative operand depth");
      }
    }
  }

  /** Typed action retained while one finally handler runs. */
  public sealed interface FinallyState
      permits FallThrough, Raise, Uncaught, Return, Exit {}

  /** Continue after a normally completed protected region. */
  public record FallThrough(int target) implements FinallyState {
    public FallThrough {
      if (target < 0) {
        throw new IllegalArgumentException("negative finally fall-through target");
      }
    }
  }

  /** Re-raise Toast's exact four-element exception tuple. */
  public record Raise(ListValue exception) implements FinallyState {
    public Raise {
      Objects.requireNonNull(exception, "exception");
      if (exception.size() != 4 || !(exception.elements().getFirst() instanceof ErrorValue)) {
        throw new IllegalArgumentException("raise continuation requires a full exception tuple");
      }
    }
  }

  /** Continue unwinding an already-recorded uncaught exception. */
  public record Uncaught(MooValue value) implements FinallyState {
    public Uncaught {
      Objects.requireNonNull(value, "value");
    }
  }

  /** Return an arbitrary MOO value after the finally handler. */
  public record Return(MooValue value) implements FinallyState {
    public Return {
      Objects.requireNonNull(value, "value");
    }
  }

  /** Exit to a compiled target after unwinding to the exact operand depth. */
  public record Exit(int operandDepth, int target) implements FinallyState {
    public Exit {
      if (operandDepth < 0 || target < 0) {
        throw new IllegalArgumentException("invalid finally exit target");
      }
    }
  }

  /** Typed state retained by one active collection or range loop. */
  public sealed interface LoopState permits CollectionLoop, RangeLoop {}

  /** Original collection and exact next Toast cursor. */
  public record CollectionLoop(CollectionKind kind, MooValue base, Optional<MooValue> next)
      implements LoopState {
    public CollectionLoop {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(base, "base");
      Objects.requireNonNull(next, "next");
      switch (kind) {
        case LIST -> {
          if (!(base instanceof ListValue list)) {
            throw new IllegalArgumentException("list loop requires a list base");
          }
          validateIndexedCollection(next, list.size());
        }
        case STRING -> {
          if (!(base instanceof StringValue string)) {
            throw new IllegalArgumentException("string loop requires a string base");
          }
          validateIndexedCollection(next, string.length());
        }
        case MAP -> {
          if (!(base instanceof MapValue map)) {
            throw new IllegalArgumentException("map loop requires a map base");
          }
          if (next.isPresent() && map.get(next.orElseThrow()).isEmpty()) {
            throw new IllegalArgumentException("map loop cursor is not a key in its base");
          }
        }
      }
    }

    private static void validateIndexedCollection(Optional<MooValue> next, int length) {
      if (next.isEmpty() || !(next.orElseThrow() instanceof IntegerValue cursor)) {
        throw new IllegalArgumentException("indexed loop requires an integer cursor");
      }
      if (cursor.value() < 1 || cursor.value() > Math.addExact((long) length, 1L)) {
        throw new IllegalArgumentException("indexed loop cursor outside collection");
      }
    }
  }

  /** Exact next and inclusive end values retained by one range loop. */
  public record RangeLoop(RangeKind kind, MooValue next, MooValue end) implements LoopState {
    public RangeLoop {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(next, "next");
      Objects.requireNonNull(end, "end");
      if (kind == RangeKind.INTEGER
          && (!(next instanceof IntegerValue) || !(end instanceof IntegerValue))) {
        throw new IllegalArgumentException("integer range requires integer bounds");
      }
      if (kind == RangeKind.OBJECT
          && (!(next instanceof ObjectValue) || !(end instanceof ObjectValue))) {
        throw new IllegalArgumentException("object range requires object bounds");
      }
    }
  }

  public enum CollectionKind {
    LIST,
    STRING,
    MAP
  }

  public enum RangeKind {
    INTEGER,
    OBJECT
  }

  /** One child task request captured at a fork boundary. */
  public record Fork(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      MooValue verbLocation,
      String calledVerb,
      String fullVerbName,
      boolean debug,
      double delaySeconds) {
    public Fork(
        BytecodeProgram program,
        Map<String, MooValue> locals,
        long programmer,
        MooValue verbLocation,
        String calledVerb,
        String fullVerbName,
        double delaySeconds) {
      this(
          program,
          locals,
          programmer,
          verbLocation,
          calledVerb,
          fullVerbName,
          true,
          delaySeconds);
    }

    public Fork {
      locals = Collections.unmodifiableMap(new LinkedHashMap<>(locals));
      Objects.requireNonNull(calledVerb, "calledVerb");
      Objects.requireNonNull(fullVerbName, "fullVerbName");
    }
  }

  /** One value-only builtin invocation held until its publication ticket owns the turn. */
  public record PendingBuiltin(
      String name,
      List<MooValue> arguments,
      long programmer,
      MooValue taskLocal,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers) {
    public PendingBuiltin {
      arguments = List.copyOf(arguments);
    }
  }

  /** Return routing for a frame. */
  public enum ReturnMode {
    ROOT,
    EVAL,
    VERB
  }

  /** Current phase of an active exception handler. */
  public enum HandlerPhase {
    TRY,
    CATCH
  }

}
