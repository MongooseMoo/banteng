package world.mongoose.banteng.world;

import java.util.Objects;
import java.util.Optional;
import world.mongoose.banteng.value.MooValue;

/** One ordinary MOO value or one Toast control marker on a suspended runtime stack. */
public record SuspendedStackSlot(
    Optional<MooValue> value, Kind kind, long controlValue) {
  /** Runtime meaning of a saved stack slot, independent of database wire tags. */
  public enum Kind { VALUE, NONE, CATCH, FINALLY }
  /** Requires exactly one supported slot representation. */
  public SuspendedStackSlot {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(kind, "kind");
    if (value.isPresent() != (kind == Kind.VALUE)
        || (kind == Kind.NONE && controlValue != 0)) {
      throw new IllegalArgumentException("invalid suspended stack slot");
    }
  }
}
