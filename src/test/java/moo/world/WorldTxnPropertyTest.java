package moo.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import moo.value.MooValue.StringValue;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.jupiter.api.Test;

final class WorldTxnPropertyTest {
  @Test
  void generatedOverlappingWritesConflictWithoutPartialPublication() {
    Generator<WritePair> writes =
        Generator.zipWith(
            Generator.integers(0, 7), Generator.integers(0, 7), WritePair::new);

    PropertyChecker.customized()
        .silent()
        .withIterationCount(63)
        .forAll(writes, WorldTxnPropertyTest::writePairPublishesAtomically);
  }

  private static boolean writePairPublishesAtomically(WritePair pair) {
    WorldTxn root = root(8);
    WorldTxn first = root.begin();
    WorldTxn second = root.begin();

    assertTrue(
        first.writeObjectProperty(pair.first(), "name", string("first-" + pair.first())));
    assertTrue(
        second.writeObjectProperty(pair.second(), "name", string("second-" + pair.second())));
    assertTrue(first.commit().isCommitted());
    WorldTxn.CommitResult secondResult = second.commit();

    if (pair.first() == pair.second()) {
      assertEquals(WorldTxn.Status.CONFLICT, secondResult.status());
      assertEquals(
          "first-" + pair.first(), snapshotObject(root.snapshot(), pair.first()).name());
    } else {
      assertTrue(secondResult.isCommitted());
      assertEquals(
          "first-" + pair.first(), snapshotObject(root.snapshot(), pair.first()).name());
      assertEquals(
          "second-" + pair.second(), snapshotObject(root.snapshot(), pair.second()).name());
    }
    for (long objectId = 0; objectId < 8; objectId++) {
      if (objectId != pair.first() && objectId != pair.second()) {
        assertEquals("object-" + objectId, snapshotObject(root.snapshot(), objectId).name());
      }
    }
    return true;
  }

  private static WorldTxn root(int objectCount) {
    List<WorldObject> objects = new ArrayList<>();
    for (long objectId = 0; objectId < objectCount; objectId++) {
      objects.add(
          new WorldObject(
              objectId,
              "object-" + objectId,
              0,
              objectId,
              -1,
              -1,
              List.of(),
              List.of(),
              List.of(),
              List.of()));
    }
    return new WorldTxn(List.of(), objects);
  }

  private static StringValue string(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  private static WorldObject snapshotObject(WorldSnapshot snapshot, long objectId) {
    return Objects.requireNonNull(snapshot.objects().get(objectId));
  }

  private record WritePair(int first, int second) {}
}
