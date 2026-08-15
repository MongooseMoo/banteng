package moo.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import moo.value.MooValue;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.WaifValue;

/** One immutable committed world revision. */
record World(
    WorldRevision revision,
    List<Long> players,
    Map<Long, WorldObject> objects,
    long lastUsedObjectId,
    Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects,
    Map<WaifValue, WorldWaif> waifs,
    List<MooValue> pendingFinalization) {
  World {
    Objects.requireNonNull(revision, "revision");
    if (lastUsedObjectId < greatestObjectId(objects)) {
      throw new IllegalArgumentException("last used object ID precedes a live object");
    }
    players = List.copyOf(players);
    objects = Collections.unmodifiableMap(new LinkedHashMap<>(objects));
    anonymousObjects =
        Collections.unmodifiableMap(new LinkedHashMap<>(anonymousObjects));
    waifs = Collections.unmodifiableMap(new LinkedHashMap<>(waifs));
    pendingFinalization = List.copyOf(pendingFinalization);
  }

  World(WorldRevision revision, List<Long> players, Map<Long, WorldObject> objects) {
    this(revision, players, objects, greatestObjectId(objects), Map.of(), Map.of(), List.of());
  }

  World(
      WorldRevision revision,
      List<Long> players,
      Map<Long, WorldObject> objects,
      Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects) {
    this(
        revision,
        players,
        objects,
        greatestObjectId(objects),
        anonymousObjects,
        Map.of(),
        List.of());
  }

  World(
      WorldRevision revision,
      List<Long> players,
      Map<Long, WorldObject> objects,
      Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects,
      Map<WaifValue, WorldWaif> waifs) {
    this(
        revision,
        players,
        objects,
        greatestObjectId(objects),
        anonymousObjects,
        waifs,
        List.of());
  }

  World(
      WorldRevision revision,
      List<Long> players,
      Map<Long, WorldObject> objects,
      Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects,
      Map<WaifValue, WorldWaif> waifs,
      List<MooValue> pendingFinalization) {
    this(
        revision,
        players,
        objects,
        greatestObjectId(objects),
        anonymousObjects,
        waifs,
        pendingFinalization);
  }

  WorldSnapshot snapshot() {
    return new WorldSnapshot(
        revision.value(),
        players,
        objects,
        lastUsedObjectId,
        anonymousObjects,
        waifs,
        pendingFinalization);
  }

  private static long greatestObjectId(Map<Long, WorldObject> objects) {
    long greatest = -1;
    for (long objectId : objects.keySet()) {
      greatest = Math.max(greatest, objectId);
    }
    return greatest;
  }
}
