package moo.vm;

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
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.ObjectValue;

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
      ListValue values, Optional<ListValue> secondaryValues, int nextIndex) {
    public LoopState {
      if (nextIndex < 0 || nextIndex > values.size()) {
        throw new IllegalArgumentException("loop cursor outside values");
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
