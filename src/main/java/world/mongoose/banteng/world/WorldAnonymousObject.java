package world.mongoose.banteng.world;

import java.util.List;
import java.util.Objects;

/** An immutable anonymous object body whose identity is carried by its value key. */
public record WorldAnonymousObject(
    String name,
    int flags,
    long owner,
    List<Long> parents,
    List<WorldVerb> verbs,
    List<WorldProperty> properties) implements PropertyHolder {
  /** Creates an anonymous object by taking immutable snapshots of its ordered members. */
  public WorldAnonymousObject {
    Objects.requireNonNull(name, "name");
    parents = List.copyOf(parents);
    verbs = List.copyOf(verbs);
    properties = List.copyOf(properties);
  }

  /** Adapts a singular parent supplied by older callers and database formats. */
  public WorldAnonymousObject(
      String name,
      int flags,
      long owner,
      long parent,
      List<WorldVerb> verbs,
      List<WorldProperty> properties) {
    this(
        name,
        flags,
        owner,
        parent == -1 ? List.of() : List.of(parent),
        verbs,
        properties);
  }

  /** Returns the deprecated singular view: the first parent, or #-1. */
  public long parent() {
    return parents.isEmpty() ? -1 : parents.getFirst();
  }
}
