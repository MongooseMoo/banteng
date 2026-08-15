package moo.world;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import moo.value.MooValue;
import moo.value.MooValue.MapValue;

/** An immutable object with validated topology and persisted member order. */
public record WorldObject(
    long id,
    String name,
    int flags,
    long owner,
    long location,
    MooValue lastMove,
    List<Long> parents,
    List<Long> contents,
    List<Long> children,
    List<WorldVerb> verbs,
    List<WorldProperty> properties) {
  /** Creates an object by taking immutable snapshots of all ordered members. */
  public WorldObject {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(lastMove, "lastMove");
    parents = List.copyOf(parents);
    contents = List.copyOf(contents);
    children = List.copyOf(children);
    verbs = List.copyOf(verbs);
    properties = List.copyOf(properties);
  }

  /** Creates a permanent object whose server-maintained move history is initially empty. */
  public WorldObject(
      long id,
      String name,
      int flags,
      long owner,
      long location,
      List<Long> parents,
      List<Long> contents,
      List<Long> children,
      List<WorldVerb> verbs,
      List<WorldProperty> properties) {
    this(
        id,
        name,
        flags,
        owner,
        location,
        new MapValue(Map.of()),
        parents,
        contents,
        children,
        verbs,
        properties);
  }

  /** Adapts the singular parent representation used by LambdaMOO v4. */
  public WorldObject(
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
    this(
        id,
        name,
        flags,
        owner,
        location,
        new MapValue(Map.of()),
        parent == -1 ? List.of() : List.of(parent),
        contents,
        children,
        verbs,
        properties);
  }

  /** Returns the deprecated singular view: the first parent, or #-1. */
  public long parent() {
    return parents.isEmpty() ? -1 : parents.getFirst();
  }
}
