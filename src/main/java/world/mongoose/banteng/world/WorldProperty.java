package world.mongoose.banteng.world;

import java.util.Locale;
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

  /** Reports whether an intrinsic object property reserves this name. */
  public static boolean isReservedName(String name) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "name", "owner", "programmer", "wizard", "r", "w", "f", "a", "location",
          "last_move", "contents" -> true;
      default -> false;
    };
  }
}
