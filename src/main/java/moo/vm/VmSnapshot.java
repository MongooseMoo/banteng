package moo.vm;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import moo.builtin.BuiltinCatalog.ConnectionOptionRequest;
import moo.builtin.BuiltinCatalog.ForcedInputRequest;
import moo.builtin.CheckpointRequest;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.BytecodeProgram.HandlerSpec;
import moo.value.MooValue;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.BooleanValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;

/** Value-only durable state for one MOO task at an execution boundary. */
public record VmSnapshot(
    Map<String, MooValue> initialLocals,
    long initialProgrammer,
    ObjectValue initialVerbLocation,
    List<Frame> frames,
    List<String> output,
    List<ConnectionOptionRequest> connectionOptionRequests,
    List<Long> bootPlayerTargets,
    List<ForcedInputRequest> forcedInputRequests,
    List<CheckpointRequest> checkpointRequests,
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
    frames = List.copyOf(frames);
    output = List.copyOf(output);
    connectionOptionRequests = List.copyOf(connectionOptionRequests);
    bootPlayerTargets = List.copyOf(bootPlayerTargets);
    forcedInputRequests = List.copyOf(forcedInputRequests);
    checkpointRequests = List.copyOf(checkpointRequests);
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
      size = add(size, Byte.BYTES);
      size = add(size, Integer.BYTES);
      size = add(size, optionalValueSize(state.returnValue()));
      size = add(size, optionalValueSize(state.error().map(error -> error)));
    }
    size = add(size, Integer.BYTES);
    for (Map.Entry<Integer, LoopState> entry : frame.loops().entrySet()) {
      size = add(size, Integer.BYTES);
      size = add(size, loopSize(entry.getValue()));
    }
    size = add(size, Byte.BYTES);
    size = add(size, valueSize(frame.receiver()));
    size = add(size, valueSize(frame.verbLocation()));
    size = add(size, optionalLongSize(frame.recycleTarget()));
    size = add(size, optionalLongSize(frame.moveObject()));
    size = add(size, optionalLongSize(frame.moveDestination()));
    size = add(size, Long.BYTES);
    size = add(size, Byte.BYTES);
    return add(size, Integer.BYTES);
  }

  private static long loopSize(LoopState loop) {
    long size = valueSize(loop.values());
    size = add(size, Byte.BYTES);
    if (loop.secondaryValues().isPresent()) {
      size = add(size, valueSize(loop.secondaryValues().orElseThrow()));
    }
    size = add(size, Long.BYTES);
    return add(size, Byte.BYTES);
  }

  private static long forkSize(Fork fork) {
    long size = programSize(fork.program());
    size = add(size, stringValueMapSize(fork.locals()));
    size = add(size, Long.BYTES);
    size = add(size, valueSize(fork.verbLocation()));
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
      ObjectValue verbLocation,
      OptionalLong recycleTarget,
      OptionalLong moveObject,
      OptionalLong moveDestination,
      long programmer,
      boolean threadMode,
      int instructionPointer) {
    public Frame {
      operandStack = List.copyOf(operandStack);
      indexCollections = List.copyOf(indexCollections);
      locals = Collections.unmodifiableMap(new LinkedHashMap<>(locals));
      handlers = List.copyOf(handlers);
      finallyStates = List.copyOf(finallyStates);
      loops = Collections.unmodifiableMap(new LinkedHashMap<>(loops));
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

  /** One explicit pending action after a finally block. */
  public record FinallyState(
      FinallyKind kind,
      int normalTarget,
      Optional<MooValue> returnValue,
      Optional<ErrorValue> error) {}

  /** One resumable loop cursor. */
  public record LoopState(
      ListValue values, Optional<ListValue> secondaryValues, long nextIndex, boolean range) {
    public LoopState {
      if (nextIndex < 0 || (!range && nextIndex > values.size())) {
        throw new IllegalArgumentException("loop cursor outside values");
      }
      if (range
          && (values.size() != 2
              || !(values.elements().get(0) instanceof IntegerValue)
              || !(values.elements().get(1) instanceof IntegerValue)
              || secondaryValues.isPresent())) {
        throw new IllegalArgumentException("invalid range loop cursor");
      }
    }
  }

  /** One child task request captured at a fork boundary. */
  public record Fork(
      BytecodeProgram program,
      Map<String, MooValue> locals,
      long programmer,
      ObjectValue verbLocation,
      double delaySeconds) {
    public Fork {
      locals = Collections.unmodifiableMap(new LinkedHashMap<>(locals));
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

  /** Action to take after a finally block. */
  public enum FinallyKind {
    NORMAL,
    RETURN,
    ERROR
  }
}
