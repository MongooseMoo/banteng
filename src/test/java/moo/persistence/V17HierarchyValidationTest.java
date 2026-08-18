package moo.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import moo.world.WorldObject;
import moo.world.WorldSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class V17HierarchyValidationTest {
  private static final String FIXTURE_ROOT = "/moo/persistence/v17-hierarchy/";

  @Test
  void rejectsNonObjectLocation(@TempDir Path temporaryDirectory) throws IOException {
    assertRejected(
        "phase1-location-type.db",
        "object #0 location must be an object reference",
        temporaryDirectory);
  }

  @Test
  void rejectsNonListContents(@TempDir Path temporaryDirectory) throws IOException {
    assertRejected(
        "phase1-contents-type.db", "object #0 contents must be a list", temporaryDirectory);
  }

  @Test
  void rejectsNonObjectContentsElement(@TempDir Path temporaryDirectory) throws IOException {
    assertRejected(
        "phase1-contents-element-type.db",
        "object #0 contents must be an object reference",
        temporaryDirectory);
  }

  @Test
  void rejectsNonObjectOrListParents(@TempDir Path temporaryDirectory) throws IOException {
    assertRejected(
        "phase1-parents-type.db", "object #0 parents must be a list", temporaryDirectory);
  }

  @Test
  void rejectsNonListChildren(@TempDir Path temporaryDirectory) throws IOException {
    assertRejected(
        "phase1-children-type.db", "object #0 children must be a list", temporaryDirectory);
  }

  @Test
  void repairsMissingHierarchyReferences(@TempDir Path temporaryDirectory) throws IOException {
    WorldSnapshot restored = readFixture("phase1-missing-references.db", temporaryDirectory);
    WorldObject object = Objects.requireNonNull(restored.objects().get(0L));

    assertEquals(-1, object.location());
    assertEquals(List.of(), object.contents());
    assertEquals(List.of(), object.parents());
    assertEquals(List.of(), object.children());
  }

  @Test
  void preservesNothingSentinelsInHierarchyLists(@TempDir Path temporaryDirectory)
      throws IOException {
    WorldSnapshot restored = readFixture("phase1-nothing-sentinels.db", temporaryDirectory);
    WorldObject object = Objects.requireNonNull(restored.objects().get(0L));

    assertEquals(List.of(-1L), object.parents());
    assertEquals(List.of(-1L), object.contents());
    assertEquals(List.of(-1L), object.children());
  }

  @Test
  void rejectsParentCycle(@TempDir Path temporaryDirectory) throws IOException {
    assertRejected(
        "phase2-parent-cycle.db", "cyclic parent hierarchy at object #0", temporaryDirectory);
  }

  @Test
  void rejectsLocationCycle(@TempDir Path temporaryDirectory) throws IOException {
    assertRejected(
        "phase2-location-cycle.db", "cyclic location hierarchy at object #0", temporaryDirectory);
  }

  @Test
  void rejectsParentWithoutReciprocalChild(@TempDir Path temporaryDirectory) throws IOException {
    assertRejected(
        "phase3-parent-without-child.db",
        "object #1 is absent from parent #0 children",
        temporaryDirectory);
  }

  @Test
  void rejectsChildWithoutReciprocalParent(@TempDir Path temporaryDirectory) throws IOException {
    assertRejected(
        "phase3-child-without-parent.db",
        "object #1 has non-reciprocal inheritance parent #0",
        temporaryDirectory);
  }

  @Test
  void rejectsLocationWithoutReciprocalContent(@TempDir Path temporaryDirectory)
      throws IOException {
    assertRejected(
        "phase3-location-without-content.db",
        "object #1 is absent from location #0 contents",
        temporaryDirectory);
  }

  @Test
  void rejectsContentWithoutReciprocalLocation(@TempDir Path temporaryDirectory)
      throws IOException {
    assertRejected(
        "phase3-content-without-location.db",
        "object #1 has non-reciprocal content location #0",
        temporaryDirectory);
  }

  @Test
  void preservesDuplicateReciprocalHierarchyLinks(@TempDir Path temporaryDirectory)
      throws IOException {
    WorldSnapshot restored =
        readFixture("phase3-duplicate-reciprocal-links.db", temporaryDirectory);
    WorldObject child = Objects.requireNonNull(restored.objects().get(0L));
    WorldObject parent = Objects.requireNonNull(restored.objects().get(1L));

    assertEquals(1, child.location());
    assertEquals(List.of(1L, 1L), child.parents());
    assertEquals(List.of(0L, 0L), parent.contents());
    assertEquals(List.of(0L, 0L), parent.children());
  }

  private static void assertRejected(
      String fixtureName, String expectedMessage, Path temporaryDirectory) throws IOException {
    Path fixture = copyFixture(fixtureName, temporaryDirectory);
    IOException error =
        assertThrows(IOException.class, () -> new LambdaMooV17Codec().read(fixture));
    String message = Objects.requireNonNull(error.getMessage());
    assertTrue(message.contains(expectedMessage), message);
  }

  private static WorldSnapshot readFixture(String fixtureName, Path temporaryDirectory)
      throws IOException {
    return new LambdaMooV17Codec()
        .read(copyFixture(fixtureName, temporaryDirectory))
        .world()
        .snapshot();
  }

  private static Path copyFixture(String fixtureName, Path temporaryDirectory) throws IOException {
    byte[] fixture;
    try (var input =
        V17HierarchyValidationTest.class.getResourceAsStream(FIXTURE_ROOT + fixtureName)) {
      if (input == null) {
        throw new IOException("missing fixture " + fixtureName);
      }
      fixture = input.readAllBytes();
    }
    Path database = temporaryDirectory.resolve(fixtureName);
    Files.write(database, fixture);
    return database;
  }
}
