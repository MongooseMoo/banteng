package moo.world;

import java.util.List;
import java.util.Objects;

/** An immutable anonymous object body whose identity is carried by its value key. */
public record WorldAnonymousObject(
    String name,
    int flags,
    long owner,
    long parent,
    List<WorldVerb> verbs,
    List<WorldProperty> properties) {
  /** Creates an anonymous object by taking immutable snapshots of its ordered members. */
  public WorldAnonymousObject {
    Objects.requireNonNull(name, "name");
    verbs = List.copyOf(verbs);
    properties = List.copyOf(properties);
  }
}
