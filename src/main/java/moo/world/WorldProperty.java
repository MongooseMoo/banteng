package moo.world;

import java.util.Objects;
import moo.value.MooValue;

/** An immutable persisted property definition or inherited value slot. */
public record WorldProperty(
    String name,
    MooValue value,
    long owner,
    int permissions,
    boolean clear,
    boolean defined) {
  /** Creates a property slot with its exact persisted metadata. */
  public WorldProperty {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
  }
}
