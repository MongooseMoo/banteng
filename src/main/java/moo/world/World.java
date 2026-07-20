package moo.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import moo.value.MooValue.AnonymousObjectValue;

/** One immutable committed world revision. */
record World(
    WorldRevision revision,
    List<Long> players,
    Map<Long, WorldObject> objects,
    Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects) {
  World {
    Objects.requireNonNull(revision, "revision");
    players = List.copyOf(players);
    objects = Collections.unmodifiableMap(new LinkedHashMap<>(objects));
    anonymousObjects =
        Collections.unmodifiableMap(new LinkedHashMap<>(anonymousObjects));
  }

  World(WorldRevision revision, List<Long> players, Map<Long, WorldObject> objects) {
    this(revision, players, objects, Map.of());
  }

  WorldSnapshot snapshot() {
    return new WorldSnapshot(revision.value(), players, objects, anonymousObjects);
  }
}
