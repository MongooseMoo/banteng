package moo.world;

import java.util.Objects;

/** An immutable persisted verb and its exact program source. */
public record WorldVerb(
    String names, long owner, int permissions, int preposition, String programSource) {
  /** Creates a verb with its exact persisted metadata and source. */
  public WorldVerb {
    Objects.requireNonNull(names, "names");
    Objects.requireNonNull(programSource, "programSource");
  }
}
