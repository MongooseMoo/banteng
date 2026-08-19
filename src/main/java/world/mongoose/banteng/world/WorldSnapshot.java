package world.mongoose.banteng.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.WaifValue;

/** An immutable public view of one committed or transaction-local world revision. */
public record WorldSnapshot(
    long revision,
    List<Long> players,
    Map<Long, WorldObject> objects,
    long lastUsedObjectId,
    Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects,
    Map<WaifValue, WorldWaif> waifs,
    List<MooValue> pendingFinalization) {
  /** Takes immutable, insertion-preserving copies of the ordered world records. */
  public WorldSnapshot {
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

  /** Creates a snapshot without anonymous objects for legacy permanent-object callers. */
  public WorldSnapshot(long revision, List<Long> players, Map<Long, WorldObject> objects) {
    this(revision, players, objects, greatestObjectId(objects), Map.of(), Map.of(), List.of());
  }

  /** Creates a snapshot without WAIF bodies for existing anonymous-object callers. */
  public WorldSnapshot(
      long revision,
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

  /** Creates a snapshot without pending-finalization roots. */
  public WorldSnapshot(
      long revision,
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

  /** Creates a snapshot whose last-used boundary is derived from its live objects. */
  public WorldSnapshot(
      long revision,
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

  private static long greatestObjectId(Map<Long, WorldObject> objects) {
    long greatest = -1;
    for (long objectId : objects.keySet()) {
      greatest = Math.max(greatest, objectId);
    }
    return greatest;
  }
}
