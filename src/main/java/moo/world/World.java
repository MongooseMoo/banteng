package moo.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.WaifValue;

/** One immutable committed world revision. */
record World(
    WorldRevision revision,
    List<Long> players,
    Map<Long, WorldObject> objects,
    Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects,
    Map<WaifValue, WorldWaif> waifs) {
  World {
    Objects.requireNonNull(revision, "revision");
    players = List.copyOf(players);
    objects = Collections.unmodifiableMap(new LinkedHashMap<>(objects));
    anonymousObjects =
        Collections.unmodifiableMap(new LinkedHashMap<>(anonymousObjects));
    waifs = Collections.unmodifiableMap(new LinkedHashMap<>(waifs));
  }

  World(WorldRevision revision, List<Long> players, Map<Long, WorldObject> objects) {
    this(revision, players, objects, Map.of(), Map.of());
  }

  World(
      WorldRevision revision,
      List<Long> players,
      Map<Long, WorldObject> objects,
      Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects) {
    this(revision, players, objects, anonymousObjects, Map.of());
  }

  WorldSnapshot snapshot() {
    return new WorldSnapshot(revision.value(), players, objects, anonymousObjects, waifs);
  }
}
