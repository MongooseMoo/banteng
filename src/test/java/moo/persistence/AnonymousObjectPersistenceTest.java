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
import java.util.Objects;
import moo.server.MooServer;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
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

  @Test
  void anon3LeavesNoAnonymousGarbageAcrossTwoProductionBoots(
      @TempDir Path temporaryDirectory) throws Exception {
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    Path input = STARTUP_FIXTURES.resolve("Anon3.db");
    byte[] inputBytes = Files.readAllBytes(input);
    Path first = temporaryDirectory.resolve("Anon3.db.new");

    runUntilFixtureShutdown(codec.read(input), first);
    LambdaMooV17Codec.Checkpoint firstCheckpoint = codec.read(first);
    assertTrue(firstCheckpoint.world().snapshot().pendingFinalization().isEmpty());
    assertTrue(firstCheckpoint.world().snapshot().anonymousObjects().isEmpty());
    assertArrayEquals(inputBytes, Files.readAllBytes(first));

    Path second = temporaryDirectory.resolve("Anon3.db.second");
    runUntilFixtureShutdown(firstCheckpoint, second);
    LambdaMooV17Codec.Checkpoint secondCheckpoint = codec.read(second);
    assertTrue(secondCheckpoint.world().snapshot().pendingFinalization().isEmpty());
    assertTrue(secondCheckpoint.world().snapshot().anonymousObjects().isEmpty());
    assertArrayEquals(inputBytes, Files.readAllBytes(second));
  }

  @Test
  void anon4PreservesThenCollectsAnAnonymousChainAcrossThreeProductionBoots(
      @TempDir Path temporaryDirectory) throws Exception {
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    Path input = STARTUP_FIXTURES.resolve("Anon4.db");
    byte[] inputBytes = Files.readAllBytes(input);
    Path first = temporaryDirectory.resolve("Anon4.db.new");

    runUntilFixtureShutdown(codec.read(input), first);
    LambdaMooV17Codec.Checkpoint firstCheckpoint = codec.read(first);
    WorldSnapshot firstWorld = firstCheckpoint.world().snapshot();
    assertTrue(firstWorld.pendingFinalization().isEmpty());
    assertEquals(1, firstWorld.anonymousObjects().size());
    var firstOneProperty =
        Objects.requireNonNull(firstWorld.objects().get(0L)).properties().stream()
            .filter(property -> property.name().equals("one"))
            .findFirst()
            .orElseThrow();
    AnonymousObjectValue firstOne =
        assertInstanceOf(AnonymousObjectValue.class, firstOneProperty.value());
    WorldAnonymousObject firstOneBody =
        Objects.requireNonNull(firstWorld.anonymousObjects().get(firstOne));
    assertEquals("One One One!", firstOneBody.name());
    assertEquals(1, firstOneBody.parent());
    assertTrue(firstOneBody.verbs().isEmpty());
    var firstTwoProperty =
        firstOneBody.properties().stream()
            .filter(property -> property.name().equals("two"))
            .findFirst()
            .orElseThrow();
    AnonymousObjectValue firstTwo =
        assertInstanceOf(AnonymousObjectValue.class, firstTwoProperty.value());
    assertTrue(!firstWorld.anonymousObjects().containsKey(firstTwo));

    Path second = temporaryDirectory.resolve("Anon4.db.second");
    runUntilFixtureShutdown(firstCheckpoint, second);
    LambdaMooV17Codec.Checkpoint secondCheckpoint = codec.read(second);
    WorldSnapshot secondWorld = secondCheckpoint.world().snapshot();
    assertEquals(1, secondWorld.pendingFinalization().size());
    assertEquals(1, secondWorld.anonymousObjects().size());
    AnonymousObjectValue pending =
        assertInstanceOf(AnonymousObjectValue.class, secondWorld.pendingFinalization().getFirst());
    WorldAnonymousObject pendingBody =
        Objects.requireNonNull(secondWorld.anonymousObjects().get(pending));
    assertEquals("One One One!", pendingBody.name());
    assertEquals(1, pendingBody.parent());
    assertTrue(pendingBody.verbs().isEmpty());
    var pendingTwoProperty =
        pendingBody.properties().stream()
            .filter(property -> property.name().equals("two"))
            .findFirst()
            .orElseThrow();
    AnonymousObjectValue pendingTwo =
        assertInstanceOf(AnonymousObjectValue.class, pendingTwoProperty.value());
    assertTrue(!secondWorld.anonymousObjects().containsKey(pendingTwo));

    Path third = temporaryDirectory.resolve("Anon4.db.third");
    runUntilFixtureShutdown(secondCheckpoint, third);
    LambdaMooV17Codec.Checkpoint thirdCheckpoint = codec.read(third);
    WorldSnapshot thirdWorld = thirdCheckpoint.world().snapshot();
    assertTrue(thirdWorld.pendingFinalization().isEmpty());
    assertTrue(thirdWorld.anonymousObjects().isEmpty());
    assertTrue(
        Objects.requireNonNull(thirdWorld.objects().get(0L)).properties().stream()
            .noneMatch(property -> property.name().equals("one")));
    assertTrue(
        Objects.requireNonNull(thirdWorld.objects().get(1L)).properties().stream()
            .noneMatch(property -> property.name().equals("two")));
    assertArrayEquals(inputBytes, Files.readAllBytes(third));
  }

  @Test
  void anon5PreservesAnIndirectSelfReferentialAnonymousObjectAcrossThreeProductionBoots(
      @TempDir Path temporaryDirectory) throws Exception {
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    Path first = temporaryDirectory.resolve("Anon5.db.new");

    runUntilFixtureShutdown(codec.read(STARTUP_FIXTURES.resolve("Anon5.db")), first);
    LambdaMooV17Codec.Checkpoint firstCheckpoint = codec.read(first);
    WorldSnapshot firstWorld = firstCheckpoint.world().snapshot();

    Path second = temporaryDirectory.resolve("Anon5.db.second");
    runUntilFixtureShutdown(firstCheckpoint, second);
    LambdaMooV17Codec.Checkpoint secondCheckpoint = codec.read(second);
    WorldSnapshot secondWorld = secondCheckpoint.world().snapshot();

    Path third = temporaryDirectory.resolve("Anon5.db.third");
    runUntilFixtureShutdown(secondCheckpoint, third);
    LambdaMooV17Codec.Checkpoint thirdCheckpoint = codec.read(third);
    WorldSnapshot thirdWorld = thirdCheckpoint.world().snapshot();

    assertArrayEquals(Files.readAllBytes(second), Files.readAllBytes(third));
    for (WorldSnapshot world : List.of(firstWorld, secondWorld, thirdWorld)) {
      assertTrue(world.pendingFinalization().isEmpty());
      assertEquals(1, world.anonymousObjects().size());

      var oneProperty =
          Objects.requireNonNull(world.objects().get(0L)).properties().stream()
              .filter(property -> property.name().equals("one"))
              .findFirst()
              .orElseThrow();
      ObjectValue outer = assertInstanceOf(ObjectValue.class, oneProperty.value());
      var outerBody = Objects.requireNonNull(world.objects().get(outer.value()));
      assertEquals("", outerBody.name());
      assertEquals(0, outerBody.flags());
      assertEquals(3, outerBody.owner());
      assertEquals(-1, outerBody.parent());
      assertTrue(outerBody.verbs().isEmpty());

      var twoProperty =
          outerBody.properties().stream()
              .filter(property -> property.name().equals("two"))
              .findFirst()
              .orElseThrow();
      MapValue indirect = assertInstanceOf(MapValue.class, twoProperty.value());
      assertEquals(1, indirect.size());
      AnonymousObjectValue anonymous =
          assertInstanceOf(
              AnonymousObjectValue.class,
              indirect
                  .get(new StringValue("foo".getBytes(StandardCharsets.ISO_8859_1)))
                  .orElseThrow());
      WorldAnonymousObject anonymousBody =
          Objects.requireNonNull(world.anonymousObjects().get(anonymous));
      assertEquals("", anonymousBody.name());
      assertEquals(0, anonymousBody.flags());
      assertEquals(3, anonymousBody.owner());
      assertEquals(1, anonymousBody.parent());
      assertTrue(anonymousBody.verbs().isEmpty());

      var fooProperty =
          anonymousBody.properties().stream()
              .filter(property -> property.name().equals("foo"))
              .findFirst()
              .orElseThrow();
      assertSame(anonymous, fooProperty.value());
      assertTrue(!fooProperty.defined());
      assertTrue(!fooProperty.clear());
    }
  }

  @Test
  void anon6RemovesAnInvalidPendingAnonymousValueInOneProductionBoot(
      @TempDir Path temporaryDirectory) throws Exception {
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    Path output = temporaryDirectory.resolve("Anon6.db.new");

    runUntilFixtureShutdown(codec.read(STARTUP_FIXTURES.resolve("Anon6.db")), output);

    WorldSnapshot world = codec.read(output).world().snapshot();
    assertTrue(world.pendingFinalization().isEmpty());
    assertTrue(world.anonymousObjects().isEmpty());
    assertEquals(4, world.objects().size());
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
