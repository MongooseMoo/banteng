package moo.builtin;

import java.util.List;
import moo.value.MooValue;
import moo.value.MooValue.ListValue;
import moo.world.WorldTxn;

/** Production callable stored directly in one builtin manifest entry. */
@FunctionalInterface
public interface BuiltinHandler {
  BuiltinResult invoke(
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long taskId,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers);

  /** Invokes this builtin with the current activation's background-thread mode. */
  default BuiltinResult invoke(
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
    return invoke(
        arguments,
        world,
        programmer,
        taskLocal,
        taskId,
        remainingTicks,
        remainingSeconds,
        receiver,
        callerProgrammer,
        callers);
  }
}
