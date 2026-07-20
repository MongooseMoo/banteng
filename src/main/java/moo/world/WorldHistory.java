package moo.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import moo.value.MooValue;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.WaifValue;

/** Owns committed revisions; all record access remains on {@link WorldTxn}. */
final class WorldHistory {
  private final NavigableMap<Long, World> revisions = new TreeMap<>();
  private final Map<Long, Integer> activeTransactions = new HashMap<>();
  private World current;

  WorldHistory(List<Long> players, List<WorldObject> objects) {
    this(players, objects, Map.of(), Map.of(), List.of());
  }

  WorldHistory(
      List<Long> players,
      List<WorldObject> objects,
      Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects) {
    this(players, objects, anonymousObjects, Map.of(), List.of());
  }

  WorldHistory(
      List<Long> players,
      List<WorldObject> objects,
      Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects,
      Map<WaifValue, WorldWaif> waifs) {
    this(players, objects, anonymousObjects, waifs, List.of());
  }

  WorldHistory(
      List<Long> players,
      List<WorldObject> objects,
      Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects,
      Map<WaifValue, WorldWaif> waifs,
      List<MooValue> pendingFinalization) {
    Objects.requireNonNull(players, "players");
    Objects.requireNonNull(objects, "objects");
    Objects.requireNonNull(anonymousObjects, "anonymousObjects");
    Objects.requireNonNull(waifs, "waifs");
    Objects.requireNonNull(pendingFinalization, "pendingFinalization");
    Map<Long, WorldObject> objectsById = new LinkedHashMap<>();
    for (WorldObject object : objects) {
      Objects.requireNonNull(object, "object");
      if (objectsById.putIfAbsent(object.id(), object) != null) {
        throw new IllegalArgumentException("duplicate object #" + object.id());
      }
    }
    current =
        new World(
            new WorldRevision(0),
            players,
            objectsById,
            anonymousObjects,
            waifs,
            pendingFinalization);
    validateTopology(current);
    revisions.put(0L, current);
  }

  synchronized World retainCurrent() {
    long revision = current.revision().value();
    activeTransactions.merge(revision, 1, Math::addExact);
    return current;
  }

  synchronized World current() {
    return current;
  }

  synchronized WorldSnapshot snapshot() {
    return current.snapshot();
  }

  synchronized WorldTxn.ValidationResult validate(WorldTxn transaction) {
    World base = transaction.baseWorld();
    Set<Long> conflictingRecords = conflictingRecords(transaction, base, current);
    Set<WorldTxn.ScanPredicate> conflictingPredicates =
        conflictingPredicates(transaction, base, current);
    return new WorldTxn.ValidationResult(
        current.revision().value(), conflictingRecords, conflictingPredicates);
  }

  synchronized WorldTxn.CommitResult publish(WorldTxn transaction) {
    WorldTxn.ValidationResult validation = validate(transaction);
    if (!validation.isValid()) {
      return WorldTxn.CommitResult.conflict(
          validation.revision(),
          validation.conflictingRecords(),
          validation.conflictingPredicates());
    }

    Map<Long, WorldObject> objects = new LinkedHashMap<>(current.objects());
    for (long objectId : transaction.recordWrites()) {
      WorldObject replacement = transaction.workingWorld().objects().get(objectId);
      if (replacement == null) {
        objects.remove(objectId);
      } else {
        objects.put(objectId, replacement);
      }
    }
    Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects =
        new LinkedHashMap<>(current.anonymousObjects());
    for (AnonymousObjectValue identity : transaction.anonymousWrites()) {
      WorldAnonymousObject replacement =
          transaction.workingWorld().anonymousObjects().get(identity);
      if (replacement == null) {
        anonymousObjects.remove(identity);
      } else {
        anonymousObjects.put(identity, replacement);
      }
    }
    Map<WaifValue, WorldWaif> waifs = new LinkedHashMap<>(current.waifs());
    for (WaifValue identity : transaction.waifWrites()) {
      WorldWaif replacement = transaction.workingWorld().waifs().get(identity);
      if (replacement == null) {
        waifs.remove(identity);
      } else {
        waifs.put(identity, replacement);
      }
    }
    List<Long> players =
        transaction.playersWritten() ? transaction.workingWorld().players() : current.players();
    List<MooValue> pendingFinalization =
        transaction.pendingFinalizationWritten()
            ? transaction.workingWorld().pendingFinalization()
            : current.pendingFinalization();
    World replacement =
        new World(
            new WorldRevision(Math.incrementExact(current.revision().value())),
            players,
            objects,
            anonymousObjects,
            waifs,
            pendingFinalization);
    validateTopology(replacement);
    current = replacement;
    revisions.put(replacement.revision().value(), replacement);
    return WorldTxn.CommitResult.committed(
        replacement.revision().value(), transaction.stagedEffects());
  }

  synchronized void release(World revision) {
    long value = revision.revision().value();
    Integer count = activeTransactions.get(value);
    if (count == null) {
      throw new IllegalStateException("world revision is not retained: " + value);
    }
    if (count == 1) {
      activeTransactions.remove(value);
    } else {
      activeTransactions.put(value, count - 1);
    }
    reclaimVersions();
  }

  synchronized int retainedRevisionCount() {
    return revisions.size();
  }

  synchronized List<Long> retainedRevisions() {
    return List.copyOf(revisions.navigableKeySet());
  }

  private void reclaimVersions() {
    long currentRevision = current.revision().value();
    revisions.keySet().removeIf(
        revision -> revision != currentRevision && !activeTransactions.containsKey(revision));
  }

  private static Set<Long> conflictingRecords(
      WorldTxn transaction, World base, World current) {
    Set<Long> checked = new LinkedHashSet<>(transaction.recordReads());
    checked.addAll(transaction.recordWrites());
    Set<Long> conflicts = new LinkedHashSet<>();
    for (long objectId : checked) {
      if (!Objects.equals(base.objects().get(objectId), current.objects().get(objectId))) {
        conflicts.add(objectId);
      }
    }
    return conflicts;
  }

  private static Set<WorldTxn.ScanPredicate> conflictingPredicates(
      WorldTxn transaction, World base, World current) {
    Set<WorldTxn.ScanPredicate> conflicts = new LinkedHashSet<>();
    Set<WorldTxn.ScanPredicate> checked = new LinkedHashSet<>(transaction.scanPredicates());
    if (transaction.playersWritten()) {
      checked.add(WorldTxn.ScanPredicate.PLAYERS);
    }
    if (transaction.pendingFinalizationWritten()) {
      checked.add(WorldTxn.ScanPredicate.PENDING_FINALIZATION);
    }
    for (WorldTxn.ScanPredicate predicate : checked) {
      boolean unchanged =
          switch (predicate) {
            case OBJECT_IDS -> base.objects().keySet().equals(current.objects().keySet());
            case PLAYERS -> base.players().equals(current.players());
            case PENDING_FINALIZATION ->
                base.pendingFinalization().equals(current.pendingFinalization());
          };
      if (!unchanged) {
        conflicts.add(predicate);
      }
    }
    return conflicts;
  }

  private static void validateTopology(World world) {
    Map<Long, WorldObject> objects = world.objects();
    for (long player : world.players()) {
      if (!objects.containsKey(player)) {
        throw new IllegalStateException("player index names missing object #" + player);
      }
    }
    for (Map.Entry<Long, WorldObject> entry : objects.entrySet()) {
      long objectId = entry.getKey();
      WorldObject object = entry.getValue();
      if (object.id() != objectId) {
        throw new IllegalStateException("object key does not match record #" + objectId);
      }
      requireUnique(object.contents(), "contents", objectId);
      requireUnique(object.children(), "children", objectId);
      if (object.location() != -1) {
        WorldObject location = objects.get(object.location());
        if (location == null || !location.contents().contains(objectId)) {
          throw new IllegalStateException("location relation is not reciprocal for #" + objectId);
        }
      }
      if (object.parent() != -1) {
        WorldObject parent = objects.get(object.parent());
        if (parent == null || !parent.children().contains(objectId)) {
          throw new IllegalStateException("parent relation is not reciprocal for #" + objectId);
        }
      }
      for (long contentId : object.contents()) {
        WorldObject content = objects.get(contentId);
        if (content == null || content.location() != objectId) {
          throw new IllegalStateException("contents relation is not reciprocal for #" + objectId);
        }
      }
      for (long childId : object.children()) {
        WorldObject child = objects.get(childId);
        if (child == null || child.parent() != objectId) {
          throw new IllegalStateException("children relation is not reciprocal for #" + objectId);
        }
      }
    }
    for (WorldAnonymousObject object : world.anonymousObjects().values()) {
      if (object.parent() != -1 && !objects.containsKey(object.parent())) {
        throw new IllegalStateException(
            "anonymous object names missing parent #" + object.parent());
      }
    }
  }

  private static void requireUnique(List<Long> values, String relation, long objectId) {
    if (new HashSet<>(values).size() != values.size()) {
      throw new IllegalStateException(relation + " contains duplicates for #" + objectId);
    }
  }
}
