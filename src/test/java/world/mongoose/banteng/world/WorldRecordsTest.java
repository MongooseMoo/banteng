package world.mongoose.banteng.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.WaifValue;

final class WorldRecordsTest {
  @Test
  void publicationSharesUnchangedMapsAndMergesOnlyWrittenRecords() {
    WorldTxn root = new WorldTxn(List.of(), List.of(object(0, "zero"), object(1, "one")));
    WorldSnapshot before = root.snapshot();
    try (WorldTxn readOnly = root.begin()) {
      assertTrue(readOnly.commit().isCommitted());
    }
    WorldSnapshot afterRead = root.snapshot();
    assertSame(before.objects(), afterRead.objects());
    assertSame(before.anonymousObjects(), afterRead.anonymousObjects());
    assertSame(before.waifs(), afterRead.waifs());
    try (WorldTxn first = root.begin(); WorldTxn second = root.begin()) {
      assertTrue(first.writeObjectProperty(0, "name", StringValue.of("first")).isOk());
      assertTrue(second.writeObjectProperty(1, "name", StringValue.of("second")).isOk());
      assertTrue(first.commit().isCommitted());
      assertTrue(second.commit().isCommitted());
    }
    WorldSnapshot after = root.snapshot();
    assertEquals("first", Objects.requireNonNull(after.objects().get(0L)).name());
    assertEquals("second", Objects.requireNonNull(after.objects().get(1L)).name());
    assertSame(before.anonymousObjects(), after.anonymousObjects());
    assertSame(before.waifs(), after.waifs());
    var removed = WorldRecords.updated(after.objects(), Set.of(1L), Map.of());
    assertEquals(0, WorldRecords.greatestObjectId(removed));
    assertEquals(List.of(0L), List.copyOf(removed.keySet()));
    assertEquals(List.of(0L, 1L), List.copyOf(after.objects().keySet()));
  }

  @Test
  void waifEditsBatchUntilExposureAndPreserveInterleavedSnapshots() {
    WorldProperty slot = new WorldProperty(":value", new IntegerValue(0), 0,
        new PropertyPerms(0), false, true);
    WorldObject waifClass = new WorldObject(0, "class", 0, 0, -1,
        List.of(), List.of(), List.of(), List.of(), List.of(slot));
    WaifValue existing = new WaifValue(new ObjectValue(0), new ObjectValue(0));
    WorldTxn root = new WorldTxn(List.of(), List.of(waifClass), Map.of(),
        Map.of(existing, new WorldWaif(List.of(slot))), List.of(), 0);
    try (WorldTxn transaction = root.begin()) {
      WorldSnapshot before = transaction.snapshot();
      for (int value = 1; value <= 1000; value++) {
        assertTrue(transaction.writeWaifProperty(existing, "value", new IntegerValue(value)).isOk());
      }
      assertTrue(transaction.hasPendingWaifEdits());
      assertTrue(transaction.resolveVerb(0, "missing", true).isEmpty());
      assertTrue(transaction.hasPendingWaifEdits());
      assertEquals(new IntegerValue(1000), transaction.readWaifProperty(existing, "value").orElseThrow());
      assertEquals(new IntegerValue(0), Objects.requireNonNull(before.waifs().get(existing))
          .properties().getFirst().value());
      assertTrue(transaction.writeObjectProperty(0, "name", StringValue.of("renamed")).isOk());
      transaction.replacePendingFinalization(List.of(existing));
      assertTrue(transaction.hasPendingWaifEdits());
      WaifValue added = transaction.createWaif(0, 0);
      WorldSnapshot between = transaction.snapshot();
      assertFalse(transaction.hasPendingWaifEdits());
      assertEquals(List.of(existing, added), List.copyOf(between.waifs().keySet()));
      assertEquals("renamed", Objects.requireNonNull(between.objects().get(0L)).name());
      assertEquals(List.of(existing), between.pendingFinalization());
      transaction.removeWaifs(Set.of(existing));
      assertTrue(transaction.waif(existing).isEmpty());
      WaifValue last = transaction.createWaif(0, 0);
      assertTrue(transaction.writeWaifProperty(added, "value", new IntegerValue(42)).isOk());
      assertTrue(transaction.commit().isCommitted());
      assertEquals(List.of(added, last), List.copyOf(root.snapshot().waifs().keySet()));
      assertEquals(List.of(existing, added), List.copyOf(between.waifs().keySet()));
      assertEquals(new IntegerValue(0), Objects.requireNonNull(between.waifs().get(added))
          .properties().getFirst().value());
    }
    try (WorldTxn transaction = root.begin()) {
      WaifValue added = transaction.snapshot().waifs().keySet().iterator().next();
      assertTrue(transaction.writeWaifProperty(added, "value", new IntegerValue(43)).isOk());
      assertTrue(transaction.writeWaifProperty(added, "value", new IntegerValue(42)).isOk());
      assertTrue(transaction.waifWrites().isEmpty());
      assertTrue(transaction.commit().isCommitted());
    }
  }

  @Test
  void snapshotOwnsCallerMapsAndSharesOnlyOwnedImmutableMaps() {
    Map<Long, WorldObject> supplied = new LinkedHashMap<>();
    supplied.put(8L, object(8, "eight"));
    supplied.put(2L, object(2, "two"));
    WorldSnapshot snapshot = new WorldSnapshot(0, List.of(), Collections.unmodifiableMap(supplied));
    supplied.clear();
    assertEquals(List.of(8L, 2L), List.copyOf(snapshot.objects().keySet()));
    assertThrows(UnsupportedOperationException.class, () -> snapshot.objects().clear());
    assertThrows(UnsupportedOperationException.class,
        () -> snapshot.objects().entrySet().iterator().next().setValue(object(8, "changed")));
    World world = new World(new WorldRevision(0), snapshot.players(), snapshot.objects(),
        snapshot.lastUsedObjectId(), snapshot.anonymousObjects(), snapshot.waifs(), List.of());
    WorldSnapshot again = world.snapshot();
    assertSame(snapshot.objects(), again.objects());
    assertSame(snapshot.anonymousObjects(), again.anonymousObjects());
    assertSame(snapshot.waifs(), again.waifs());
    assertEquals(snapshot, again);
    assertThrows(IllegalArgumentException.class,
        () -> new WorldSnapshot(0, List.of(), snapshot.objects(), 7, Map.of(), Map.of(), List.of()));
  }

  @Test
  void replacementPreservesOldSnapshotAndRecordOrder() {
    Map<Long, WorldObject> supplied = new LinkedHashMap<>();
    supplied.put(8L, object(8, "eight"));
    supplied.put(2L, object(2, "two"));
    var old = WorldRecords.copyOf(supplied);
    var replaced = WorldRecords.with(old, 8L, object(8, "changed"));
    var added = WorldRecords.with(replaced, 10L, object(10, "ten"));
    assertEquals("eight", Objects.requireNonNull(old.get(8L)).name());
    assertEquals("changed", Objects.requireNonNull(replaced.get(8L)).name());
    assertEquals(List.of(8L, 2L, 10L), List.copyOf(added.keySet()));
    assertEquals(8, WorldRecords.greatestObjectId(old));
    assertEquals(10, WorldRecords.greatestObjectId(added));
  }

  @Test
  void findsTheFirstPropertyWithTheSameCaseInsensitiveName() {
    WorldProperty first = new WorldProperty("Name", new IntegerValue(1), 0,
        new PropertyPerms(0), false, true);
    WorldProperty second = new WorldProperty("nAmE", new IntegerValue(2), 0,
        new PropertyPerms(0), false, true);
    assertSame(first, WorldProperties.copyOf(List.of(first, second)).findByName("name"));
  }

  @Test
  void replacementsKeepOrderAcrossCompactionAndReinsertion() {
    Map<Long, WorldObject> original = new LinkedHashMap<>();
    for (long id = 0; id < 300; id++) original.put(id, object(id, "before"));
    var baseline = WorldRecords.copyOf(original);
    var current = baseline;
    for (long id = 0; id < 200; id++) {
      current = WorldRecords.with(current, id, object(id, "after"));
    }
    assertEquals(List.copyOf(original.keySet()), List.copyOf(current.keySet()));
    assertEquals("before", Objects.requireNonNull(baseline.get(199L)).name());
    assertEquals("after", Objects.requireNonNull(current.get(199L)).name());
    assertEquals("after", current.entrySet().stream()
        .filter(entry -> entry.getKey().equals(199L)).findFirst().orElseThrow().getValue().name());

    var removed = WorldRecords.updated(current, Set.of(1L), Map.of());
    var reinserted = WorldRecords.with(removed, 1L, object(1, "reinserted"));
    assertEquals(300, reinserted.size());
    assertEquals(1L, List.copyOf(reinserted.keySet()).getLast());
    assertEquals("reinserted", Objects.requireNonNull(reinserted.get(1L)).name());
  }

  @Test
  void singleObjectWritesRetainExactFootprintAndSnapshotIsolation() {
    WorldTxn root = new WorldTxn(List.of(), List.of(object(0, "before"), object(1, "other")));
    try (WorldTxn transaction = root.begin()) {
      WorldSnapshot before = transaction.snapshot();
      assertTrue(transaction.writeObjectProperty(0, "name", StringValue.of("after")).isOk());
      WorldSnapshot after = transaction.snapshot();
      assertEquals(Set.of(0L), transaction.recordWrites());
      assertEquals("before", Objects.requireNonNull(before.objects().get(0L)).name());
      assertEquals("after", Objects.requireNonNull(after.objects().get(0L)).name());
      assertNotSame(before.objects(), after.objects());
      assertSame(before.waifs(), after.waifs());
      assertSame(before.anonymousObjects(), after.anonymousObjects());
      assertTrue(transaction.writeObjectProperty(0, "name", StringValue.of("before")).isOk());
      assertTrue(transaction.recordWrites().isEmpty());
      assertEquals("after", Objects.requireNonNull(after.objects().get(0L)).name());
      assertTrue(transaction.commit().isCommitted());
    }
  }

  private static WorldObject object(long id, String name) {
    return new WorldObject(id, name, 0, id, -1, List.of(), List.of(), List.of(), List.of(), List.of());
  }
}
