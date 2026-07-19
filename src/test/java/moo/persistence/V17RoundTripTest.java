package moo.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.world.WorldObject;
import moo.world.WorldProperty;
import moo.world.WorldSnapshot;
import moo.world.WorldTxn;
import moo.world.WorldVerb;
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
                new WorldProperty("local", new IntegerValue(9), 0, 1, false, true),
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
