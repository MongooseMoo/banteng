package moo.builtin;

import java.util.List;
import moo.value.MooValue;

/** Dynamic tick charge attached directly to one builtin manifest entry. */
@FunctionalInterface
public interface BuiltinCostRule {
  /** Returns the dynamic tick charge after ordinary VM call-opcode charging. */
  long charge(List<MooValue> arguments);

  /** Constructs a non-negative fixed dynamic charge. */
  static BuiltinCostRule fixed(long charge) {
    if (charge < 0) {
      throw new IllegalArgumentException("negative builtin tick charge");
    }
    return arguments -> charge;
  }
}
