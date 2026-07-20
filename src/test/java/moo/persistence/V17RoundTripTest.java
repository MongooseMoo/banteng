package moo.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import moo.value.MooValue;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.BooleanValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;
import moo.world.WorldAnonymousObject;
import moo.world.WorldObject;
import moo.world.WorldProperty;
import moo.world.WorldSnapshot;
import moo.world.WorldTxn;
import moo.world.WorldVerb;
import moo.world.WorldWaif;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class V17RoundTripTest {
  @Test
  void restoresWorldAndTaskStateWithByteStableAtomicOutput(@TempDir Path temporaryDirectory)
      throws IOException {
    WorldSnapshot expected = world().snapshot();
    Path first = temporaryDirectory.resolve("first.db");
    Files.writeString(first, "old checkpoint", StandardCharsets.ISO_8859_1);
    LambdaMooV17Codec codec = new LambdaMooV17Codec();

    codec.writeAtomic(first, expected, List.of());
    LambdaMooV17Codec.Checkpoint restored = codec.read(first);

    assertEquals(expected.players(), restored.world().snapshot().players());
    assertEquals(expected.objects(), restored.world().snapshot().objects());
    assertEquals(List.of(), restored.tasks());

    Path second = temporaryDirectory.resolve("second.db");
    codec.writeAtomic(second, restored.world().snapshot(), restored.tasks());
    assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));

    LambdaMooV17Codec.Checkpoint repeated = codec.read(first);
    assertEquals(restored.world().snapshot(), repeated.world().snapshot());
    assertEquals(restored.tasks(), repeated.tasks());
  }

  @Test
  void preservesWaifAliasesAndAnonymousAliasesAndCycles(@TempDir Path temporaryDirectory)
      throws IOException {
    WaifValue firstWaif = new WaifValue(new ObjectValue(0), new ObjectValue(0));
    WaifValue secondWaif = new WaifValue(new ObjectValue(0), new ObjectValue(0));
    AnonymousObjectValue firstAnonymous = new AnonymousObjectValue();
    AnonymousObjectValue secondAnonymous = new AnonymousObjectValue();
    Map<MooValue, MooValue> identityMap = new LinkedHashMap<>();
    identityMap.put(firstWaif, string("first waif"));
    identityMap.put(secondWaif, string("second waif"));
    identityMap.put(firstAnonymous, string("first anonymous"));
    identityMap.put(secondAnonymous, string("second anonymous"));
    ListValue graph =
        new ListValue(
            List.of(
                firstWaif,
                secondWaif,
                firstAnonymous,
                secondAnonymous,
                new MapValue(identityMap)));
    WorldObject root =
        new WorldObject(
            0,
            "root",
            0,
            0,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(property("graph", graph), property("other", new IntegerValue(0))));
    Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects = new LinkedHashMap<>();
    anonymousObjects.put(
        firstAnonymous,
        new WorldAnonymousObject(
            "first",
            0,
            0,
            0,
            List.of(new WorldVerb("ping", 0, 173, -1, "return 1;\n")),
            List.of(
                new WorldProperty("graph", graph, 0, 1, true, false),
                new WorldProperty("other", secondAnonymous, 0, 1, false, false))));
    anonymousObjects.put(
        secondAnonymous,
        new WorldAnonymousObject(
            "second",
            0,
            0,
            0,
            List.of(),
            List.of(
                new WorldProperty("graph", graph, 0, 1, true, false),
                new WorldProperty("other", firstAnonymous, 0, 1, false, false))));
    WorldSnapshot world =
        new WorldSnapshot(0, List.of(0L), Map.of(0L, root), anonymousObjects);
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    Path first = temporaryDirectory.resolve("first.db");

    codec.writeAtomic(first, world, List.of());
    WorldSnapshot restored = codec.read(first).world().snapshot();

    ListValue restoredGraph =
        assertInstanceOf(
            ListValue.class,
            Objects.requireNonNull(restored.objects().get(0L)).properties().getFirst().value());
    WaifValue restoredFirstWaif =
        assertInstanceOf(WaifValue.class, restoredGraph.elements().get(0));
    WaifValue restoredSecondWaif =
        assertInstanceOf(WaifValue.class, restoredGraph.elements().get(1));
    AnonymousObjectValue restoredFirstAnonymous =
        assertInstanceOf(AnonymousObjectValue.class, restoredGraph.elements().get(2));
    AnonymousObjectValue restoredSecondAnonymous =
        assertInstanceOf(AnonymousObjectValue.class, restoredGraph.elements().get(3));
    MapValue restoredMap = assertInstanceOf(MapValue.class, restoredGraph.elements().get(4));
    assertEquals(string("first waif"), restoredMap.get(restoredFirstWaif).orElseThrow());
    assertEquals(string("second waif"), restoredMap.get(restoredSecondWaif).orElseThrow());
    assertEquals(
        string("first anonymous"), restoredMap.get(restoredFirstAnonymous).orElseThrow());
    assertEquals(
        string("second anonymous"), restoredMap.get(restoredSecondAnonymous).orElseThrow());
    assertSame(
        restoredSecondAnonymous,
        Objects.requireNonNull(restored.anonymousObjects().get(restoredFirstAnonymous))
            .properties()
            .get(1)
            .value());
    assertSame(
        restoredFirstAnonymous,
        Objects.requireNonNull(restored.anonymousObjects().get(restoredSecondAnonymous))
            .properties()
            .get(1)
            .value());
    assertEquals(
        "return 1;\n",
        Objects.requireNonNull(restored.anonymousObjects().get(restoredFirstAnonymous))
            .verbs()
            .getFirst()
            .programSource());
  }

  @Test
  void preservesWaifPropertySlotsAndBackreferences(@TempDir Path temporaryDirectory)
      throws IOException {
    WaifValue waif = new WaifValue(new ObjectValue(7), new ObjectValue(1));
    ListValue aliases = new ListValue(List.of(new ObjectValue(7), waif, waif));
    WorldObject root =
        new WorldObject(
            1,
            "root",
            4,
            1,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(property("values", aliases)));
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
    WorldWaif body =
        new WorldWaif(
            List.of(new WorldProperty(":marker", new IntegerValue(7), 1, 0, false, false)));
    WorldSnapshot world =
        new WorldSnapshot(
            0,
            List.of(1L),
            Map.of(1L, root, 7L, waifClass),
            Map.of(),
            Map.of(waif, body));
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    Path first = temporaryDirectory.resolve("waif-first.db");

    codec.writeAtomic(first, world, List.of());
    LambdaMooV17Codec.Checkpoint firstCheckpoint = codec.read(first);
    WorldSnapshot firstRestored = firstCheckpoint.world().snapshot();
    ListValue firstAliases =
        assertInstanceOf(
            ListValue.class,
            Objects.requireNonNull(firstRestored.objects().get(1L)).properties().getFirst().value());
    WaifValue firstReference =
        assertInstanceOf(WaifValue.class, firstAliases.elements().get(1));
    assertSame(firstReference, firstAliases.elements().get(2));
    assertEquals(new ObjectValue(7), firstReference.classObject());
    assertEquals(
        new IntegerValue(7),
        Objects.requireNonNull(firstRestored.waifs().get(firstReference))
            .properties()
            .getFirst()
            .value());

    try (WorldTxn transaction = firstCheckpoint.world().begin()) {
      transaction.writeWaifProperty(firstReference, "marker", new IntegerValue(42));
      assertEquals(new IntegerValue(42), transaction.readWaifProperty(firstReference, "marker").orElseThrow());
      assertEquals(true, transaction.commit().isCommitted());
    }
    Path second = temporaryDirectory.resolve("waif-second.db");
    codec.writeAtomic(second, firstCheckpoint.world().snapshot(), List.of());
    WorldSnapshot secondRestored = codec.read(second).world().snapshot();
    ListValue secondAliases =
        assertInstanceOf(
            ListValue.class,
            Objects.requireNonNull(secondRestored.objects().get(1L)).properties().getFirst().value());
    WaifValue secondReference =
        assertInstanceOf(WaifValue.class, secondAliases.elements().get(1));
    assertSame(secondReference, secondAliases.elements().get(2));
    assertEquals(
        new IntegerValue(42),
        Objects.requireNonNull(secondRestored.waifs().get(secondReference))
            .properties()
            .getFirst()
            .value());
  }

  @Test
  void reinsertsNonTotalMapKeysAcrossV17Read(@TempDir Path temporaryDirectory)
      throws IOException {
    WaifValue firstWaif = new WaifValue(new ObjectValue(0), new ObjectValue(0));
    WaifValue secondWaif = new WaifValue(new ObjectValue(0), new ObjectValue(0));
    AnonymousObjectValue firstAnonymous = new AnonymousObjectValue();
    AnonymousObjectValue secondAnonymous = new AnonymousObjectValue();

    MapValue booleanForward =
        new MapValue(Map.of())
            .with(BooleanValue.FALSE, string("false"))
            .with(BooleanValue.TRUE, string("true"));
    MapValue booleanReverse =
        new MapValue(Map.of())
            .with(BooleanValue.TRUE, string("true"))
            .with(BooleanValue.FALSE, string("false"));
    MapValue waifForward =
        new MapValue(Map.of())
            .with(firstWaif, string("first"))
            .with(secondWaif, string("second"));
    MapValue waifReverse =
        new MapValue(Map.of())
            .with(secondWaif, string("second"))
            .with(firstWaif, string("first"));
    MapValue anonymousForward =
        new MapValue(Map.of())
            .with(firstAnonymous, string("first"))
            .with(secondAnonymous, string("second"));
    MapValue anonymousReverse =
        new MapValue(Map.of())
            .with(secondAnonymous, string("second"))
            .with(firstAnonymous, string("first"));

    assertEquals(
        List.of(BooleanValue.TRUE, BooleanValue.FALSE),
        List.copyOf(booleanForward.entries().keySet()));
    assertEquals(
        List.of(BooleanValue.FALSE, BooleanValue.TRUE),
        List.copyOf(booleanReverse.entries().keySet()));
    assertEquals(List.of(secondWaif, firstWaif), List.copyOf(waifForward.entries().keySet()));
    assertEquals(List.of(firstWaif, secondWaif), List.copyOf(waifReverse.entries().keySet()));
    assertEquals(
        List.of(secondAnonymous, firstAnonymous),
        List.copyOf(anonymousForward.entries().keySet()));
    assertEquals(
        List.of(firstAnonymous, secondAnonymous),
        List.copyOf(anonymousReverse.entries().keySet()));

    ListValue state =
        new ListValue(
            List.of(
                firstWaif,
                secondWaif,
                firstAnonymous,
                secondAnonymous,
                booleanForward,
                booleanReverse,
                waifForward,
                waifReverse,
                anonymousForward,
                anonymousReverse));
    WorldObject root =
        new WorldObject(
            0,
            "root",
            0,
            0,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(property("state", state)));
    Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects = new LinkedHashMap<>();
    anonymousObjects.put(
        firstAnonymous,
        new WorldAnonymousObject(
            "first",
            0,
            0,
            0,
            List.of(),
            List.of(new WorldProperty("state", new IntegerValue(0), 0, 1, true, false))));
    anonymousObjects.put(
        secondAnonymous,
        new WorldAnonymousObject(
            "second",
            0,
            0,
            0,
            List.of(),
            List.of(new WorldProperty("state", new IntegerValue(0), 0, 1, true, false))));
    WorldSnapshot world =
        new WorldSnapshot(0, List.of(0L), Map.of(0L, root), anonymousObjects);
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    Path checkpoint = temporaryDirectory.resolve("topology.db");

    codec.writeAtomic(checkpoint, world, List.of());
    WorldSnapshot restored = codec.read(checkpoint).world().snapshot();

    ListValue restoredState =
        assertInstanceOf(
            ListValue.class,
            Objects.requireNonNull(restored.objects().get(0L)).properties().getFirst().value());
    WaifValue restoredFirstWaif =
        assertInstanceOf(WaifValue.class, restoredState.elements().get(0));
    WaifValue restoredSecondWaif =
        assertInstanceOf(WaifValue.class, restoredState.elements().get(1));
    AnonymousObjectValue restoredFirstAnonymous =
        assertInstanceOf(AnonymousObjectValue.class, restoredState.elements().get(2));
    AnonymousObjectValue restoredSecondAnonymous =
        assertInstanceOf(AnonymousObjectValue.class, restoredState.elements().get(3));
    MapValue restoredBooleanForward =
        assertInstanceOf(MapValue.class, restoredState.elements().get(4));
    MapValue restoredBooleanReverse =
        assertInstanceOf(MapValue.class, restoredState.elements().get(5));
    MapValue restoredWaifForward =
        assertInstanceOf(MapValue.class, restoredState.elements().get(6));
    MapValue restoredWaifReverse =
        assertInstanceOf(MapValue.class, restoredState.elements().get(7));
    MapValue restoredAnonymousForward =
        assertInstanceOf(MapValue.class, restoredState.elements().get(8));
    MapValue restoredAnonymousReverse =
        assertInstanceOf(MapValue.class, restoredState.elements().get(9));

    assertEquals(
        List.of(BooleanValue.FALSE, BooleanValue.TRUE),
        List.copyOf(restoredBooleanForward.entries().keySet()));
    assertEquals(
        List.of(BooleanValue.TRUE, BooleanValue.FALSE),
        List.copyOf(restoredBooleanReverse.entries().keySet()));
    assertEquals(
        List.of(restoredFirstWaif, restoredSecondWaif),
        List.copyOf(restoredWaifForward.entries().keySet()));
    assertEquals(
        List.of(restoredSecondWaif, restoredFirstWaif),
        List.copyOf(restoredWaifReverse.entries().keySet()));
    assertEquals(
        List.of(restoredFirstAnonymous, restoredSecondAnonymous),
        List.copyOf(restoredAnonymousForward.entries().keySet()));
    assertEquals(
        List.of(restoredSecondAnonymous, restoredFirstAnonymous),
        List.copyOf(restoredAnonymousReverse.entries().keySet()));
    assertNotEquals(booleanForward.toLiteral(), restoredBooleanForward.toLiteral());
    assertNotEquals(booleanReverse.toLiteral(), restoredBooleanReverse.toLiteral());
    assertNotEquals(waifForward.toLiteral(), restoredWaifForward.toLiteral());
    assertNotEquals(waifReverse.toLiteral(), restoredWaifReverse.toLiteral());
    assertNotEquals(anonymousForward.toLiteral(), restoredAnonymousForward.toLiteral());
    assertNotEquals(anonymousReverse.toLiteral(), restoredAnonymousReverse.toLiteral());
  }

  private static WorldTxn world() {
    Map<MooValue, MooValue> map = new LinkedHashMap<>();
    map.put(string("key"), new IntegerValue(37));
    WorldObject object =
        new WorldObject(
            0,
            "Latin-1 \u00ff object",
            7,
            0,
            -1,
            -1,
            List.of(),
            List.of(1L),
            List.of(new WorldVerb("evaluate", 0, 173, -1, "return \"caf\u00e9\";\n")),
            List.of(
                property("integer", new IntegerValue(Long.MIN_VALUE)),
                property("float", new FloatValue(-17.25)),
                property("string", new StringValue(new byte[] {(byte) 0xff, 0x41})),
                property("object", new ObjectValue(0)),
                property("error", ErrorValue.E_PERM),
                property(
                    "list", new ListValue(List.of(new IntegerValue(1), string("two")))),
                property("map", new MapValue(map))));
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
            List.of(
                new WorldProperty(
                    "local",
                    new ListValue(List.of(BooleanValue.TRUE, BooleanValue.FALSE)),
                    0,
                    1,
                    false,
                    true),
                new WorldProperty(
                    "integer", new IntegerValue(Long.MIN_VALUE), 0, 1, true, false),
                new WorldProperty("float", new FloatValue(3.5), 0, 1, false, false),
                new WorldProperty(
                    "string", new StringValue(new byte[] {(byte) 0xff, 0x41}), 0, 1, true, false),
                new WorldProperty("object", new ObjectValue(0), 0, 1, true, false),
                new WorldProperty("error", ErrorValue.E_PERM, 0, 1, true, false),
                new WorldProperty(
                    "list",
                    new ListValue(List.of(new IntegerValue(1), string("two"))),
                    0,
                    1,
                    true,
                    false),
                new WorldProperty("map", new MapValue(map), 0, 1, true, false)));
    return new WorldTxn(List.of(0L), List.of(object, child));
  }

  private static WorldProperty property(String name, MooValue value) {
    return new WorldProperty(name, value, 0, 1, false, true);
  }

  private static StringValue string(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }
}
