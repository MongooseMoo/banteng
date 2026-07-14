package moo.world;

import java.util.List;
import java.util.Objects;

/** An immutable object with validated topology and persisted member order. */
public record WorldObject(
    long id,
    String name,
    int flags,
    long owner,
    long location,
    long parent,
    List<Long> contents,
    List<Long> children,
    List<WorldVerb> verbs,
    List<WorldProperty> properties) {
  /** Creates an object by taking immutable snapshots of all ordered members. */
  public WorldObject {
    Objects.requireNonNull(name, "name");
    contents = List.copyOf(contents);
    children = List.copyOf(children);
    verbs = List.copyOf(verbs);
    properties = List.copyOf(properties);
  }
}
