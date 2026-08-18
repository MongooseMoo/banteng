package moo.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;
import org.junit.jupiter.api.Test;

final class WorldTxnTest {
  @Test
  void moveRecordsProtectedMetadataAndUnrelatedEditsPreserveIt() {
    WorldTxn root = root(object(0, "moved"), object(1, "destination"));
    try (WorldTxn transaction = root.begin()) {
      long before = System.currentTimeMillis() / 1_000L;

      assertTrue(transaction.move(0, 1).isOk());

      long after = System.currentTimeMillis() / 1_000L;
      MapValue lastMove =
          assertInstanceOf(
              MapValue.class,
              transaction.readObjectProperty(0, "last_move").orElseThrow());
      assertEquals(new ObjectValue(-1), lastMove.entries().get(string("source")));
      long movedAt =
          assertInstanceOf(IntegerValue.class, lastMove.entries().get(string("time"))).value();
      assertTrue(movedAt >= before);
      assertTrue(movedAt <= after);

      assertTrue(transaction.writeObjectProperty(0, "name", string("renamed")).isOk());
      assertEquals(lastMove, transaction.readObjectProperty(0, "last_move").orElseThrow());
    }
  }

  @Test
  void movePositionReordersContentsAndZeroAppends() {
    WorldObject room =
        new WorldObject(
            0,
            "room",
            0,
            0,
            -1,
            -1,
            List.of(1L, 2L, 3L),
            List.of(),
            List.of(),
            List.of());
    WorldObject first = locatedObject(1, 0);
    WorldObject second = locatedObject(2, 0);
    WorldObject third = locatedObject(3, 0);
    try (WorldTxn transaction = root(room, first, second, third).begin()) {
      assertTrue(transaction.move(3, 0, 1).isOk());
      assertEquals(List.of(3L, 1L, 2L), transaction.object(0).orElseThrow().contents());

      assertTrue(transaction.move(3, 0, 0).isOk());
      assertEquals(List.of(1L, 2L, 3L), transaction.object(0).orElseThrow().contents());

      assertTrue(transaction.move(1, 0, Long.MAX_VALUE).isOk());
      assertEquals(List.of(2L, 3L, 1L), transaction.object(0).orElseThrow().contents());
    }
  }

  @Test
  void stagesRecordsAndEffectsUntilOneAtomicPublication() {
    WorldTxn root = root(object(0, "before"), object(1, "destination"));
    WorldTxn transaction = root.begin();

    assertTrue(
        transaction.writeObjectProperty(0, "name", string("after")).isOk());
    assertTrue(transaction.move(0, 1).isOk());
    transaction.stageEffect(new IntegerValue(37));

    assertEquals("before", snapshotObject(root.snapshot(), 0).name());
    assertEquals(-1, snapshotObject(root.snapshot(), 0).location());
    assertEquals("after", snapshotObject(transaction.snapshot(), 0).name());
    assertEquals(1, snapshotObject(transaction.snapshot(), 0).location());

    WorldTxn.CommitResult result = transaction.commit();

    assertTrue(result.isCommitted());
    assertEquals(1, result.revision());
    assertEquals(List.of(new IntegerValue(37)), result.effects());
    assertEquals("after", snapshotObject(root.snapshot(), 0).name());
    assertEquals(1, snapshotObject(root.snapshot(), 0).location());
    assertEquals(List.of(0L), snapshotObject(root.snapshot(), 1).contents());
  }

  @Test
  void fixedSnapshotProvidesRepeatableReadsAndExactRecordConflict() {
    WorldTxn root = root(object(0, "base"));
    WorldTxn stale = root.begin();
    WorldTxn winner = root.begin();

    assertEquals("base", stale.object(0).orElseThrow().name());
    assertTrue(winner.writeObjectProperty(0, "name", string("winner")).isOk());
    assertTrue(winner.commit().isCommitted());

    assertEquals("base", stale.object(0).orElseThrow().name());
    assertTrue(stale.writeObjectProperty(0, "name", string("stale")).isOk());
    WorldTxn.CommitResult result = stale.commit();

    assertEquals(WorldTxn.Status.CONFLICT, result.status());
    assertEquals(Set.of(0L), result.conflictingRecords());
    assertEquals(Set.of(), result.conflictingPredicates());
    assertEquals(List.of(), result.effects());
    assertEquals("winner", snapshotObject(root.snapshot(), 0).name());
  }

  @Test
  void validatesExactFootprintWithoutPublishingOrCompletingTheTransaction() {
    WorldTxn root = root(object(0, "base"));
    try (WorldTxn candidate = root.begin()) {
      assertEquals("base", candidate.object(0).orElseThrow().name());
      assertTrue(candidate.writeObjectProperty(0, "name", string("candidate")).isOk());

      WorldTxn.ValidationResult current = candidate.validate();

      assertTrue(current.isValid());
      assertEquals(0, current.revision());
      assertEquals("base", snapshotObject(root.snapshot(), 0).name());

      try (WorldTxn winner = root.begin()) {
        assertTrue(winner.writeObjectProperty(0, "name", string("winner")).isOk());
        assertTrue(winner.commit().isCommitted());
      }

      WorldTxn.ValidationResult stale = candidate.validate();

      assertFalse(stale.isValid());
      assertEquals(1, stale.revision());
      assertEquals(Set.of(0L), stale.conflictingRecords());
      assertEquals(Set.of(), stale.conflictingPredicates());
      assertEquals("candidate", candidate.object(0).orElseThrow().name());
      assertEquals("winner", snapshotObject(root.snapshot(), 0).name());
    }
  }

  @Test
  void objectScanConflictsWhenAnotherTransactionChangesItsMembership() {
    WorldTxn root = root(object(0, "base"));
    WorldTxn scanner = root.begin();
    WorldTxn creator = root.begin();

    assertEquals(1, scanner.objectCount());
    assertEquals(1, creator.createObject(-1, 0).id());
    assertTrue(creator.commit().isCommitted());

    WorldTxn.CommitResult result = scanner.commit();

    assertFalse(result.isCommitted());
    assertEquals(Set.of(WorldTxn.ScanPredicate.OBJECT_IDS), result.conflictingPredicates());
    assertEquals(2, root.snapshot().objects().size());
  }

  @Test
  void anonymousAllocationCommitsAndRollsBackOutsidePermanentObjectTopology() {
    WorldTxn root = root(object(0, "parent"));
    AnonymousObjectValue committed;
    try (WorldTxn transaction = root.begin()) {
      committed = transaction.createAnonymousObject(0, 0);

      assertEquals(1, transaction.objectCount());
      assertEquals(0, transaction.maximumObjectId());
      assertEquals(List.of(), transaction.object(0).orElseThrow().children());
      WorldAnonymousObject body = transaction.anonymousObject(committed).orElseThrow();
      assertEquals(0, body.owner());
      assertEquals(0, body.parent());
      assertEquals(body, transaction.snapshot().anonymousObjects().get(committed));
      assertTrue(transaction.commit().isCommitted());
    }

    try (WorldTxn view = root.begin()) {
      assertTrue(view.anonymousObject(committed).isPresent());
      assertEquals(1, view.objectCount());
      assertEquals(List.of(), view.object(0).orElseThrow().children());
    }

    AnonymousObjectValue rolledBack;
    try (WorldTxn transaction = root.begin()) {
      rolledBack = transaction.createAnonymousObject(0, 0);
      assertTrue(transaction.anonymousObject(rolledBack).isPresent());
    }
    try (WorldTxn view = root.begin()) {
      assertTrue(view.anonymousObject(rolledBack).isEmpty());
      assertEquals(1, view.snapshot().anonymousObjects().size());
    }
  }

  @Test
  void anonymousReadsAndWritesConflictOnTheExactIdentity() {
    WorldTxn root = root(object(0, "parent"));
    AnonymousObjectValue identity;
    try (WorldTxn creator = root.begin()) {
      identity = creator.createAnonymousObject(0, 0);
      assertTrue(creator.commit().isCommitted());
    }
    WorldTxn stale = root.begin();
    try (WorldTxn winner = root.begin()) {
      assertEquals("", stale.anonymousObject(identity).orElseThrow().name());
      assertTrue(winner.writeObjectProperty(identity, "name", string("winner")).isOk());
      assertTrue(winner.commit().isCommitted());
    }

    WorldTxn.CommitResult result = stale.commit();

    assertEquals(WorldTxn.Status.CONFLICT, result.status());
    assertEquals(Set.of(identity), result.conflictingAnonymousRecords());
    assertEquals(Set.of(), result.conflictingRecords());
  }

  @Test
  void waifPropertyStateCommitsAndRollsBackByReferenceIdentity() {
    WorldObject waifClass =
        new WorldObject(
            7,
            "waif class",
            0,
            1,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(new WorldProperty(":marker", new IntegerValue(0), 1, 0, false, true)));
    WorldTxn root = root(waifClass);
    WaifValue committed;

    try (WorldTxn transaction = root.begin()) {
      committed = transaction.createWaif(7, 1);
      assertEquals(
          new IntegerValue(0), transaction.readWaifProperty(committed, "marker").orElseThrow());
      assertTrue(transaction.writeWaifProperty(committed, "marker", new IntegerValue(7)).isOk());
      assertEquals(
          new IntegerValue(7), transaction.readWaifProperty(committed, "marker").orElseThrow());
      assertTrue(transaction.commit().isCommitted());
    }

    try (WorldTxn view = root.begin()) {
      assertEquals(new IntegerValue(7), view.readWaifProperty(committed, "marker").orElseThrow());
      assertEquals(1, view.snapshot().waifs().size());
    }

    try (WorldTxn rolledBack = root.begin()) {
      assertTrue(rolledBack.writeWaifProperty(committed, "marker", new IntegerValue(42)).isOk());
      assertEquals(
          new IntegerValue(42), rolledBack.readWaifProperty(committed, "marker").orElseThrow());
    }
    try (WorldTxn view = root.begin()) {
      assertEquals(new IntegerValue(7), view.readWaifProperty(committed, "marker").orElseThrow());
    }
  }

  @Test
  void propertyDefinitionsPropagateToAnonymousDescendants() {
    WorldTxn root = root(object(0, "parent"));
    AnonymousObjectValue identity;

    try (WorldTxn transaction = root.begin()) {
      identity = transaction.createAnonymousObject(0, 0);
      assertTrue(transaction.addProperty(0, "later", new IntegerValue(17), 0, 1).isOk());

      WorldProperty inherited =
          transaction.anonymousObject(identity).orElseThrow().properties().getFirst();
      assertEquals("later", inherited.name());
      assertEquals(new IntegerValue(17), inherited.value());
      assertTrue(inherited.clear());
      assertFalse(inherited.defined());
      assertTrue(transaction.commit().isCommitted());
    }

    try (WorldTxn view = root.begin()) {
      assertEquals(
          "later",
          view.anonymousObject(identity).orElseThrow().properties().getFirst().name());
    }
  }

  @Test
  void playerScanAndPlayerWriteConflictAsOnePredicate() {
    WorldTxn root = root(object(0, "player"), object(1, "candidate"));
    WorldTxn scanner = root.begin();
    WorldTxn writer = root.begin();

    assertEquals(List.of(), scanner.players());
    assertTrue(writer.setPlayerFlag(1, true).isOk());
    assertTrue(writer.commit().isCommitted());

    WorldTxn.CommitResult result = scanner.commit();

    assertEquals(Set.of(WorldTxn.ScanPredicate.PLAYERS), result.conflictingPredicates());
    assertEquals(List.of(1L), root.snapshot().players());
  }

  @Test
  void readsContentsAndObjectPermissionFlagsFromTheObjectRecord() {
    WorldObject flagged =
        new WorldObject(
            0,
            "flagged",
            ObjectFlags.FLAG_READ
                | ObjectFlags.FLAG_WRITE
                | ObjectFlags.FLAG_FERTILE
                | ObjectFlags.FLAG_ANONYMOUS,
            0,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of());
    try (WorldTxn transaction = root(flagged).begin()) {
      assertEquals(Optional.of(new ListValue(List.of())), transaction.readObjectProperty(0, "contents"));
      assertEquals(Optional.of(new IntegerValue(1)), transaction.readObjectProperty(0, "r"));
      assertEquals(Optional.of(new IntegerValue(1)), transaction.readObjectProperty(0, "w"));
      assertEquals(Optional.of(new IntegerValue(1)), transaction.readObjectProperty(0, "f"));
      assertEquals(Optional.of(new IntegerValue(1)), transaction.readObjectProperty(0, "a"));
      assertTrue(transaction.writeObjectProperty(0, "a", new IntegerValue(0)).isOk());
      assertEquals(Optional.of(new IntegerValue(0)), transaction.readObjectProperty(0, "a"));
    }
  }

  @Test
  void inheritedPropertySlotsOverrideAndClearWithoutLosingTheDefinition() {
    WorldProperty definition =
        new WorldProperty("test", new IntegerValue(1), 0, 7, false, true);
    WorldProperty inherited =
        new WorldProperty("test", new IntegerValue(1), 0, 7, true, false);
    WorldObject parent =
        new WorldObject(
            0,
            "parent",
            0,
            0,
            -1,
            -1,
            List.of(),
            List.of(1L),
            List.of(),
            List.of(definition));
    WorldObject child =
        new WorldObject(
            1,
            "child",
            0,
            0,
            -1,
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of(inherited));

    try (WorldTxn transaction = root(parent, child).begin()) {
      assertEquals(Optional.of(new IntegerValue(1)), transaction.readObjectProperty(1, "test"));
      assertTrue(transaction.property(1, "test").orElseThrow().clear());

      assertTrue(transaction.writeObjectProperty(1, "test", new IntegerValue(2)).isOk());
      assertEquals(Optional.of(new IntegerValue(2)), transaction.readObjectProperty(1, "test"));
      assertFalse(transaction.property(1, "test").orElseThrow().clear());
      assertFalse(transaction.property(1, "test").orElseThrow().defined());

      assertTrue(transaction.clearProperty(1, "test").isOk());
      assertEquals(Optional.of(new IntegerValue(1)), transaction.readObjectProperty(1, "test"));
      assertTrue(transaction.property(1, "test").orElseThrow().clear());
      assertFalse(transaction.property(1, "test").orElseThrow().defined());
    }
  }

  @Test
  void propertyDefinitionsPropagateToCreatedDescendantsAndDeleteAsOneIndexMutation() {
    WorldObject root =
        new WorldObject(
            0,
            "root",
            0,
            0,
            -1,
            -1,
            List.of(),
            List.of(1L),
            List.of(),
            List.of());
    WorldObject child =
        new WorldObject(
            1,
            "child",
            0,
            0,
            -1,
            0,
            List.of(),
            List.of(),
            List.of(),
            List.of());

    try (WorldTxn transaction = root(root, child).begin()) {
      assertTrue(transaction.addProperty(0, "test", new IntegerValue(1), 0, 7).isOk());
      WorldProperty definition = transaction.object(0).orElseThrow().properties().getFirst();
      WorldProperty inherited = transaction.object(1).orElseThrow().properties().getFirst();
      assertTrue(definition.defined());
      assertFalse(definition.clear());
      assertFalse(inherited.defined());
      assertTrue(inherited.clear());

      WorldObject grandchild = transaction.createObject(1, 0);
      WorldProperty grandchildSlot = grandchild.properties().getFirst();
      assertFalse(grandchildSlot.defined());
      assertTrue(grandchildSlot.clear());
      assertEquals(Optional.of(new IntegerValue(1)), transaction.readObjectProperty(2, "test"));

      assertTrue(transaction.deleteProperty(0, "test").isOk());
      assertTrue(transaction.object(0).orElseThrow().properties().isEmpty());
      assertTrue(transaction.object(1).orElseThrow().properties().isEmpty());
      assertTrue(transaction.object(2).orElseThrow().properties().isEmpty());
    }
  }

  @Test
  void retainsAReferencedRevisionAndReclaimsItAfterTransactionEnds() {
    WorldTxn root = root(object(0, "base"));
    WorldTxn retained = root.begin();
    WorldTxn writer = root.begin();

    assertTrue(writer.writeObjectProperty(0, "name", string("next")).isOk());
    assertTrue(writer.commit().isCommitted());
    assertEquals(List.of(0L, 1L), root.retainedRevisions());
    assertEquals(2, root.retainedRevisionCount());

    retained.close();

    assertEquals(List.of(1L), root.retainedRevisions());
    assertEquals(1, root.retainedRevisionCount());
  }

  @Test
  void recordsExactReadsWritesAndClosedScans() {
    WorldTxn transaction = root(object(0, "base"), object(1, "other")).begin();

    transaction.object(0);
    assertTrue(transaction.writeObjectProperty(1, "name", string("changed")).isOk());
    transaction.maximumObjectId();

    assertEquals(Set.of(0L, 1L), transaction.recordReads());
    assertEquals(Set.of(1L), transaction.recordWrites());
    assertEquals(Set.of(WorldTxn.ScanPredicate.OBJECT_IDS), transaction.scanPredicates());
    transaction.close();
  }

  @Test
  void rootCannotBypassTheTransactionBoundary() {
    WorldTxn root = root(object(0, "base"));

    assertThrows(IllegalStateException.class, () -> root.object(0));
    assertThrows(IllegalStateException.class, () -> root.writeObjectProperty(0, "name", string("x")));
    assertThrows(IllegalStateException.class, root::commit);
  }

  @Test
  void mutationsCarrySpecificMooFailureReasons() {
    WorldProperty protectedProperty =
        new WorldProperty("protected", new IntegerValue(1), 0, 0, false, true);
    WorldObject target =
        new WorldObject(
            0,
            "target",
            0,
            0,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(protectedProperty));
    WorldObject programmer = object(1, "programmer");
    try (WorldTxn transaction = root(target, programmer).begin()) {
      assertWorldFailure(
          ErrorValue.E_PROPNF,
          transaction.writeObjectProperty(0, "missing", new IntegerValue(2), 0));
      assertWorldFailure(
          ErrorValue.E_TYPE,
          transaction.writeObjectProperty(0, "name", new IntegerValue(2), 0));
      assertWorldFailure(
          ErrorValue.E_PERM,
          transaction.writeObjectProperty(0, "protected", new IntegerValue(2), 1));
      assertWorldFailure(ErrorValue.E_INVARG, transaction.move(99, 0));

      WorldResult<MooValue> result =
          transaction.writeObjectProperty(0, "protected", new IntegerValue(2), 0);
      assertInstanceOf(WorldResult.Ok.class, result);
      WorldResult.Ok<?> written = (WorldResult.Ok<?>) result;
      assertEquals(new IntegerValue(2), written.value());
    }
  }

  private static void assertWorldFailure(ErrorValue expected, WorldResult<?> result) {
    assertInstanceOf(WorldResult.Failed.class, result);
    WorldResult.Failed<?> failed = (WorldResult.Failed<?>) result;
    assertEquals(expected, failed.reason().value());
  }

  private static WorldTxn root(WorldObject... objects) {
    return new WorldTxn(List.of(), List.of(objects));
  }

  private static WorldObject object(long id, String name) {
    return new WorldObject(
        id, name, 0, id, -1, -1, List.of(), List.of(), List.of(), List.of());
  }

  private static WorldObject locatedObject(long id, long location) {
    return new WorldObject(
        id, "object-" + id, 0, id, location, -1, List.of(), List.of(), List.of(), List.of());
  }

  private static StringValue string(String value) {
    return StringValue.of(value);
  }

  private static WorldObject snapshotObject(WorldSnapshot snapshot, long objectId) {
    return Objects.requireNonNull(snapshot.objects().get(objectId));
  }
}
