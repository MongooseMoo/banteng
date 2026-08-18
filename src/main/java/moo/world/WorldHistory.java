package moo.world;

import com.google.errorprone.annotations.concurrent.GuardedBy;
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
  @GuardedBy("this") private final NavigableMap<Long, World> revisions = new TreeMap<>();
  @GuardedBy("this") private final Map<Long, Integer> activeTransactions = new HashMap<>();
  @GuardedBy("this") private final VerbCache verbCache = new VerbCache();
  @GuardedBy("this") private World current;

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
    this(
        players,
        objects,
        anonymousObjects,
        waifs,
        pendingFinalization,
        greatestObjectId(objects));
  }

  WorldHistory(
      List<Long> players,
      List<WorldObject> objects,
      Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects,
      Map<WaifValue, WorldWaif> waifs,
      List<MooValue> pendingFinalization,
      long lastUsedObjectId) {
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
            lastUsedObjectId,
            anonymousObjects,
            waifs,
            pendingFinalization);
    validateTopology(current);
    revisions.put(0L, current);
  }

  synchronized World retainCurrent() {
    long revision = current.revision().value();
    activeTransactions.merge(revision, 1, Math::addExact);
    emitRetention();
    return current;
  }

  synchronized World current() {
    return current;
  }

  synchronized WorldSnapshot snapshot() {
    return current.snapshot();
  }

  @SuppressWarnings("ReferenceEquality")
  synchronized VerbCache.Resolution findCallableVerb(
      WorldTxn transaction, Object receiver, String verbName) {
    if (transaction.baseWorld() != current
        || transaction.workingWorld() != transaction.baseWorld()) {
      return transaction.findCallableVerbUncached(receiver, verbName);
    }
    return transaction.findCallableVerbCached(receiver, verbName, verbCache);
  }

  synchronized WorldTxn.VerbCacheStats verbCacheStats() {
    return verbCache.stats();
  }

  synchronized WorldTxn.ValidationResult validate(WorldTxn transaction) {
    World base = transaction.baseWorld();
    Set<Long> conflictingRecords = conflictingRecords(transaction, base, current);
    Set<AnonymousObjectValue> conflictingAnonymousRecords =
        conflictingAnonymousRecords(transaction, base, current);
    Set<WorldTxn.ScanPredicate> conflictingPredicates =
        conflictingPredicates(transaction, base, current);
    return new WorldTxn.ValidationResult(
        current.revision().value(),
        conflictingRecords,
        conflictingAnonymousRecords,
        conflictingPredicates);
  }

  synchronized WorldTxn.CommitResult publish(WorldTxn transaction) {
    WorldTxn.ValidationResult validation = validate(transaction);
    if (!validation.isValid()) {
      return WorldTxn.CommitResult.conflict(
          validation.revision(),
          validation.conflictingRecords(),
          validation.conflictingAnonymousRecords(),
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
            Math.max(current.lastUsedObjectId(), transaction.workingWorld().lastUsedObjectId()),
            anonymousObjects,
            waifs,
            pendingFinalization);
    if (changesTopology(transaction, current)) {
      validateTopology(replacement);
    }
    if (affectsCallableVerbLookup(transaction, current)) {
      verbCache.invalidate();
    }
    current = replacement;
    revisions.put(replacement.revision().value(), replacement);
    emitRetention();
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
    emitRetention();
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

  private void emitRetention() {
    VersionRetentionEvent event = new VersionRetentionEvent();
    event.currentRevision = current.revision().value();
    event.oldestRetainedRevision = revisions.firstKey();
    event.retainedRevisionCount = revisions.size();
    event.activeSnapshotCount =
        activeTransactions.values().stream().mapToLong(Integer::longValue).sum();
    event.commit();
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

  private static Set<AnonymousObjectValue> conflictingAnonymousRecords(
      WorldTxn transaction, World base, World current) {
    Set<AnonymousObjectValue> checked = new LinkedHashSet<>(transaction.anonymousReads());
    checked.addAll(transaction.anonymousWrites());
    Set<AnonymousObjectValue> conflicts = new LinkedHashSet<>();
    for (AnonymousObjectValue identity : checked) {
      if (!Objects.equals(
          base.anonymousObjects().get(identity), current.anonymousObjects().get(identity))) {
        conflicts.add(identity);
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
    Map<Long, VisitState> inheritanceState = new HashMap<>();
    for (Map.Entry<Long, WorldObject> entry : objects.entrySet()) {
      long objectId = entry.getKey();
      WorldObject object = entry.getValue();
      if (object.id() != objectId) {
        throw new IllegalStateException("object key does not match record #" + objectId);
      }
      requireUnique(object.contents(), "contents", objectId);
      requireUnique(object.children(), "children", objectId);
      requireUnique(object.parents(), "parents", objectId);
      if (object.location() != -1) {
        WorldObject location = objects.get(object.location());
        if (location == null || !location.contents().contains(objectId)) {
          throw new IllegalStateException("location relation is not reciprocal for #" + objectId);
        }
      }
      for (long parentId : object.parents()) {
        WorldObject parent = objects.get(parentId);
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
        if (child == null || !child.parents().contains(objectId)) {
          throw new IllegalStateException("children relation is not reciprocal for #" + objectId);
        }
      }
      validateAcyclic(objectId, objects, inheritanceState);
    }
    for (WorldAnonymousObject object : world.anonymousObjects().values()) {
      requireUnique(object.parents(), "anonymous parents", -1);
      for (long parentId : object.parents()) {
        if (!objects.containsKey(parentId)) {
          throw new IllegalStateException(
              "anonymous object names missing parent #" + parentId);
        }
      }
    }
  }

  private static boolean changesTopology(WorldTxn transaction, World current) {
    if (transaction.playersWritten()) {
      return true;
    }
    World working = transaction.workingWorld();
    for (long objectId : transaction.recordWrites()) {
      WorldObject before = current.objects().get(objectId);
      WorldObject after = working.objects().get(objectId);
      if (before == null
          || after == null
          || before.id() != after.id()
          || before.location() != after.location()
          || !before.parents().equals(after.parents())
          || !before.contents().equals(after.contents())
          || !before.children().equals(after.children())) {
        return true;
      }
    }
    for (AnonymousObjectValue identity : transaction.anonymousWrites()) {
      WorldAnonymousObject before = current.anonymousObjects().get(identity);
      WorldAnonymousObject after = working.anonymousObjects().get(identity);
      if (before == null || after == null || !before.parents().equals(after.parents())) {
        return true;
      }
    }
    return false;
  }

  private static boolean affectsCallableVerbLookup(WorldTxn transaction, World current) {
    World working = transaction.workingWorld();
    for (long objectId : transaction.recordWrites()) {
      WorldObject before = current.objects().get(objectId);
      WorldObject after = working.objects().get(objectId);
      if (before != null && after == null) {
        return true;
      }
      if (before == null || after == null) {
        continue;
      }
      if (!before.parents().equals(after.parents())
          || callableVerbFieldsChanged(before.verbs(), after.verbs())) {
        return true;
      }
    }
    for (AnonymousObjectValue identity : transaction.anonymousWrites()) {
      WorldAnonymousObject before = current.anonymousObjects().get(identity);
      WorldAnonymousObject after = working.anonymousObjects().get(identity);
      if (before != null && after == null) {
        return true;
      }
      if (before == null || after == null) {
        continue;
      }
      if (!before.parents().equals(after.parents())
          || callableVerbFieldsChanged(before.verbs(), after.verbs())) {
        return true;
      }
    }
    return false;
  }

  private static boolean callableVerbFieldsChanged(
      List<WorldVerb> before, List<WorldVerb> after) {
    if (before.size() != after.size()) {
      return true;
    }
    for (int index = 0; index < before.size(); index++) {
      WorldVerb oldVerb = before.get(index);
      WorldVerb newVerb = after.get(index);
      if (!oldVerb.names().equals(newVerb.names())
          || oldVerb.permissions() != newVerb.permissions()
          || oldVerb.preposition() != newVerb.preposition()) {
        return true;
      }
    }
    return false;
  }

  private static void validateAcyclic(
      long objectId, Map<Long, WorldObject> objects, Map<Long, VisitState> state) {
    VisitState existing = state.get(objectId);
    if (existing == VisitState.COMPLETE) {
      return;
    }
    if (existing == VisitState.VISITING) {
      throw new IllegalStateException("inheritance cycle at #" + objectId);
    }
    state.put(objectId, VisitState.VISITING);
    WorldObject object = objects.get(objectId);
    if (object == null) {
      throw new IllegalStateException("inheritance names missing object #" + objectId);
    }
    for (long parentId : object.parents()) {
      validateAcyclic(parentId, objects, state);
    }
    state.put(objectId, VisitState.COMPLETE);
  }

  private enum VisitState {
    VISITING,
    COMPLETE
  }

  private static void requireUnique(List<Long> values, String relation, long objectId) {
    if (new HashSet<>(values).size() != values.size()) {
      throw new IllegalStateException(relation + " contains duplicates for #" + objectId);
    }
  }

  private static long greatestObjectId(List<WorldObject> objects) {
    long greatest = -1;
    for (WorldObject object : objects) {
      greatest = Math.max(greatest, object.id());
    }
    return greatest;
  }
}
