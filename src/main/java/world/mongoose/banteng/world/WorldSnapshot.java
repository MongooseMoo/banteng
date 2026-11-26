package world.mongoose.banteng.world;

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
    objects = WorldRecords.copyOf(objects);
    if (lastUsedObjectId < greatestObjectId(objects)) {
      throw new IllegalArgumentException("last used object ID precedes a live object");
    }
    players = List.copyOf(players);
    anonymousObjects = WorldRecords.copyOf(anonymousObjects);
    waifs = WorldRecords.copyOf(waifs);
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
    return WorldRecords.greatestObjectId(objects);
  }

  /** Returns the permanent property roots in their first occurrence order. */
  public List<MooValue> permanentCollectibleRoots() {
    return WorldRecords.collectibleRoots(objects);
  }

  /** Identities of the immutable property edges that can affect reachability. */
  public record CollectibleGraph(Object permanent, Object anonymous, Object waifs) {}

  public CollectibleGraph collectibleGraph() {
    return new CollectibleGraph(
        WorldRecords.collectibleGraph(objects), WorldRecords.collectibleGraph(anonymousObjects),
        WorldRecords.collectibleGraph(waifs));
  }
}
