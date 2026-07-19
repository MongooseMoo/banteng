package moo.builtin;

import java.util.List;
import moo.value.MooValue;
import moo.value.MooValue.ListValue;
import moo.world.WorldTxn;

/** Production callable stored directly in one builtin manifest entry. */
@FunctionalInterface
public interface BuiltinHandler {
  BuiltinCatalog.Result invoke(
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers);
}
