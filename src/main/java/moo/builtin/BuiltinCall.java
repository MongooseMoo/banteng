package moo.builtin;

import java.util.List;
import java.util.Objects;
import moo.value.MooValue;
import moo.value.MooValue.ListValue;
import moo.world.WorldTxn;

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
    boolean threadMode) {
  /** Takes immutable ownership of the invocation context. */
  public BuiltinCall {
    arguments = List.copyOf(arguments);
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(taskLocal, "taskLocal");
    Objects.requireNonNull(receiver, "receiver");
    Objects.requireNonNull(callers, "callers");
  }
}
