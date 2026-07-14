package moo.world;

import java.util.Objects;
import moo.value.MooValue;

/** An immutable persisted property slot. */
public record WorldProperty(String name, MooValue value, long owner, int permissions) {
  /** Creates a property slot with its exact persisted metadata. */
  public WorldProperty {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
  }
}
