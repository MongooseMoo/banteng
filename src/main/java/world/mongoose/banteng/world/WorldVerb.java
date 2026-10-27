package world.mongoose.banteng.world;

import java.util.Objects;

/** An immutable persisted verb and its exact program source. */
public record WorldVerb(
    String names, long owner, VerbPerms perms, ArgSpec dobj, int preposition, ArgSpec iobj,
    String programSource) {
  /** Creates a verb with its exact persisted metadata and source. */
  public WorldVerb {
    Objects.requireNonNull(names, "names");
    Objects.requireNonNull(perms, "perms");
    Objects.requireNonNull(dobj, "dobj");
    Objects.requireNonNull(iobj, "iobj");
    Objects.requireNonNull(programSource, "programSource");
  }
}
