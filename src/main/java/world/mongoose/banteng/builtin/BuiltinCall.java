package world.mongoose.banteng.builtin;

import java.util.List;
import java.util.Objects;
import java.util.function.LongBinaryOperator;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.world.WorldTxn;

/** The complete context for one builtin invocation. */
public record BuiltinCall(
    List<MooValue> arguments,
    WorldTxn world,
    long programmer,
    MooValue taskLocal,
    long taskId,
    long remainingTicks,
    long remainingSeconds,
    MooValue receiver,
    long callerProgrammer,
    ListValue callers,
    boolean threadMode,
    LongBinaryOperator stagedBufferedOutputLength) {
  /** Creates a call with no attempt-local output projection. */
  public BuiltinCall(
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long taskId,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers,
      boolean threadMode) {
    this(
        arguments,
        world,
        programmer,
        taskLocal,
        taskId,
        remainingTicks,
        remainingSeconds,
        receiver,
        callerProgrammer,
        callers,
        threadMode,
        (_connectionId, queuedBytes) -> queuedBytes);
  }

  /** Takes immutable ownership of the invocation context. */
  public BuiltinCall {
    arguments = List.copyOf(arguments);
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(taskLocal, "taskLocal");
    Objects.requireNonNull(receiver, "receiver");
    Objects.requireNonNull(callers, "callers");
    Objects.requireNonNull(stagedBufferedOutputLength, "stagedBufferedOutputLength");
  }
}
