package moo.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.StringValue;
import org.junit.jupiter.api.Test;

final class WorldTxnTest {
  @Test
  void stagesRecordsAndEffectsUntilOneAtomicPublication() {
    WorldTxn root = root(object(0, "before"), object(1, "destination"));
    WorldTxn transaction = root.begin();

    assertTrue(
        transaction.writeObjectProperty(0, "name", string("after")));
    assertTrue(transaction.move(0, 1));
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
    assertTrue(winner.writeObjectProperty(0, "name", string("winner")));
    assertTrue(winner.commit().isCommitted());

    assertEquals("base", stale.object(0).orElseThrow().name());
    assertTrue(stale.writeObjectProperty(0, "name", string("stale")));
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
      assertTrue(candidate.writeObjectProperty(0, "name", string("candidate")));

      WorldTxn.ValidationResult current = candidate.validate();

      assertTrue(current.isValid());
      assertEquals(0, current.revision());
      assertEquals("base", snapshotObject(root.snapshot(), 0).name());

      try (WorldTxn winner = root.begin()) {
        assertTrue(winner.writeObjectProperty(0, "name", string("winner")));
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
  void playerScanAndPlayerWriteConflictAsOnePredicate() {
    WorldTxn root = root(object(0, "player"), object(1, "candidate"));
    WorldTxn scanner = root.begin();
    WorldTxn writer = root.begin();

    assertEquals(List.of(), scanner.players());
    assertTrue(writer.setPlayerFlag(1, true));
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
            16 | 32 | 128,
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
    }
  }

  @Test
  void retainsAReferencedRevisionAndReclaimsItAfterTransactionEnds() {
    WorldTxn root = root(object(0, "base"));
    WorldTxn retained = root.begin();
    WorldTxn writer = root.begin();

    assertTrue(writer.writeObjectProperty(0, "name", string("next")));
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
    assertTrue(transaction.writeObjectProperty(1, "name", string("changed")));
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

  private static WorldTxn root(WorldObject... objects) {
    return new WorldTxn(List.of(), List.of(objects));
  }

  private static WorldObject object(long id, String name) {
    return new WorldObject(
        id, name, 0, id, -1, -1, List.of(), List.of(), List.of(), List.of());
  }

  private static StringValue string(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  private static WorldObject snapshotObject(WorldSnapshot snapshot, long objectId) {
    return Objects.requireNonNull(snapshot.objects().get(objectId));
  }
}
