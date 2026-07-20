package moo.world;

import java.util.List;

/** An immutable WAIF property body whose identity, class, and owner are carried by its value key. */
public record WorldWaif(List<WorldProperty> properties) {
  /** Creates a WAIF body by taking an immutable snapshot of its ordered property slots. */
  public WorldWaif {
    properties = List.copyOf(properties);
  }
}
