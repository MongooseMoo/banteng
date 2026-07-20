package moo.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import moo.value.MooValue.AnonymousObjectValue;

/** An immutable public view of one committed or transaction-local world revision. */
public record WorldSnapshot(
    long revision,
    List<Long> players,
    Map<Long, WorldObject> objects,
    Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects) {
  /** Takes immutable, insertion-preserving copies of the ordered world records. */
  public WorldSnapshot {
    players = List.copyOf(players);
    objects = Collections.unmodifiableMap(new LinkedHashMap<>(objects));
    anonymousObjects =
        Collections.unmodifiableMap(new LinkedHashMap<>(anonymousObjects));
  }

  /** Creates a snapshot without anonymous objects for legacy permanent-object callers. */
  public WorldSnapshot(long revision, List<Long> players, Map<Long, WorldObject> objects) {
    this(revision, players, objects, Map.of());
  }
}
