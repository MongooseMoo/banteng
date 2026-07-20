package moo.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import moo.server.MooServer;
import moo.value.MooValue.AnonymousObjectValue;
import moo.world.WorldAnonymousObject;
import moo.world.WorldSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AnonymousObjectPersistenceTest {
  private static final Path STARTUP_FIXTURES =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "startup");

  @Test
  void anon1PreservesOnePendingAnonymousRootAcrossTwoProductionBoots(
      @TempDir Path temporaryDirectory) throws Exception {
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    Path first = temporaryDirectory.resolve("Anon1.db.new");

    runUntilFixtureShutdown(codec.read(STARTUP_FIXTURES.resolve("Anon1.db")), first);

    String firstText = Files.readString(first, StandardCharsets.ISO_8859_1);
    assertTrue(firstText.contains("1 values pending finalization\n12\n4\n"));
    LambdaMooV17Codec.Checkpoint firstCheckpoint = codec.read(first);
    WorldSnapshot firstWorld = firstCheckpoint.world().snapshot();
    assertEquals(1, firstWorld.pendingFinalization().size());
    AnonymousObjectValue pending =
        assertInstanceOf(AnonymousObjectValue.class, firstWorld.pendingFinalization().getFirst());
    assertTrue(firstWorld.anonymousObjects().containsKey(pending));
    WorldAnonymousObject pendingBody = firstWorld.anonymousObjects().get(pending);
    assertEquals(1, pendingBody.verbs().size());
    assertEquals("recycle", pendingBody.verbs().getFirst().names());
    assertEquals(
        "server_log(\"recycle called\");\n", pendingBody.verbs().getFirst().programSource());

    Path second = temporaryDirectory.resolve("Anon1.db.second");
    runUntilFixtureShutdown(firstCheckpoint, second);

    assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
  }

  @Test
  void anon2PreservesOnePendingRootForARecursiveCycleAcrossTwoProductionBoots(
      @TempDir Path temporaryDirectory) throws Exception {
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    Path first = temporaryDirectory.resolve("Anon2.db.new");

    runUntilFixtureShutdown(codec.read(STARTUP_FIXTURES.resolve("Anon2.db")), first);
    LambdaMooV17Codec.Checkpoint firstCheckpoint = codec.read(first);
    WorldSnapshot firstWorld = firstCheckpoint.world().snapshot();

    Path second = temporaryDirectory.resolve("Anon2.db.second");
    runUntilFixtureShutdown(firstCheckpoint, second);
    LambdaMooV17Codec.Checkpoint secondCheckpoint = codec.read(second);

    assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    for (WorldSnapshot world :
        List.of(firstWorld, secondCheckpoint.world().snapshot())) {
      assertEquals(1, world.pendingFinalization().size());
      assertEquals(2, world.anonymousObjects().size());
      var a =
          world.anonymousObjects().entrySet().stream()
              .filter(entry -> entry.getValue().name().equals("A"))
              .findFirst()
              .orElseThrow();
      var b =
          world.anonymousObjects().entrySet().stream()
              .filter(entry -> entry.getValue().name().equals("B"))
              .findFirst()
              .orElseThrow();
      assertSame(a.getKey(), world.pendingFinalization().getFirst());
      assertSame(
          b.getKey(),
          a.getValue().properties().stream()
              .filter(property -> property.name().equals("next"))
              .findFirst()
              .orElseThrow()
              .value());
      assertSame(
          a.getKey(),
          b.getValue().properties().stream()
              .filter(property -> property.name().equals("next"))
              .findFirst()
              .orElseThrow()
              .value());
      assertEquals("recycle", a.getValue().verbs().getFirst().names());
      assertEquals(
          "server_log(\"recycle called on A\");\n",
          a.getValue().verbs().getFirst().programSource());
      assertEquals("recycle", b.getValue().verbs().getFirst().names());
      assertEquals(
          "server_log(\"recycle called on B\");\n",
          b.getValue().verbs().getFirst().programSource());
    }
  }

  private static void runUntilFixtureShutdown(
      LambdaMooV17Codec.Checkpoint checkpoint, Path output) throws Exception {
    MooServer server = new MooServer("127.0.0.1", 0, checkpoint.world(), output);
    Thread serving = Thread.startVirtualThread(server::serve);
    try {
      assertTrue(serving.join(Duration.ofSeconds(5)), "fixture did not shut down the server");
    } finally {
      server.close();
      serving.join(Duration.ofSeconds(5));
    }
    assertTrue(Files.exists(output), "fixture shutdown did not write a checkpoint");
  }
}
