package world.mongoose.banteng.world;

import java.util.Objects;
import world.mongoose.banteng.value.MooValue;

/** An immutable persisted property definition or inherited value slot. */
public record WorldProperty(
    String name,
    MooValue value,
    long owner,
    PropertyPerms perms,
    boolean clear,
    boolean defined) {
  /** Creates a property slot with its exact persisted metadata. */
  public WorldProperty {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(perms, "perms");
  }
}
