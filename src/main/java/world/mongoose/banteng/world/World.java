package world.mongoose.banteng.world;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.WaifValue;

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
    objects = WorldRecords.copyOf(objects);
    if (lastUsedObjectId < greatestObjectId(objects)) {
      throw new IllegalArgumentException("last used object ID precedes a live object");
    }
    players = List.copyOf(players);
    anonymousObjects = WorldRecords.copyOf(anonymousObjects);
    waifs = WorldRecords.copyOf(waifs);
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
    return WorldRecords.greatestObjectId(objects);
  }
}
