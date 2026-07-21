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
import moo.persistence.LambdaMooV17Codec.QueuedTask;
import moo.value.MooValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.world.WorldSnapshot;
import moo.world.WorldTxn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QueuedTaskV17CodecTest {
  @Test
  void roundTripsOneQueuedTaskUsingPinnedToastGrammar(@TempDir Path temporaryDirectory)
      throws IOException {
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    QueuedTask task = task();
    Path checkpoint = temporaryDirectory.resolve("queued.db");

    codec.writeAtomic(checkpoint, emptyWorld(), List.of(task));

    LambdaMooV17Codec.Checkpoint restored = codec.read(checkpoint);
    assertEquals(List.of(task), restored.tasks());
    assertEquals(expectedDatabase(), Files.readString(checkpoint, StandardCharsets.ISO_8859_1));
  }

  @Test
  void restoredQueuedTaskWritesByteForByte(@TempDir Path temporaryDirectory)
      throws IOException {
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    Path first = temporaryDirectory.resolve("first.db");
    Path second = temporaryDirectory.resolve("second.db");
    codec.writeAtomic(first, emptyWorld(), List.of(task()));

    LambdaMooV17Codec.Checkpoint restored = codec.read(first);
    codec.writeAtomic(second, restored.world().snapshot(), restored.tasks());

    assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
  }

  private static QueuedTask task() {
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("this", new ObjectValue(7));
    locals.put("player", new ObjectValue(9));
    locals.put("verb", string("tick"));
    locals.put("marker", new IntegerValue(42));
    return new QueuedTask(
        41,
        1_700_000_123,
        "#0.audit_restart = marker;\n",
        locals,
        3,
        new ObjectValue(8),
        9);
  }

  private static WorldSnapshot emptyWorld() {
    return new WorldTxn(List.of(), List.of()).snapshot();
  }

  private static StringValue string(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  private static String expectedDatabase() {
    return """
        ** LambdaMOO Database, Format Version 17 **
        0
        0 values pending finalization
        0 clocks
        1 queued tasks
        0 1 1700000123 41
        0
        -111
        1
        7
        1
        8
        1
        7 -7 -8 9 -9 3 8 -10 1
        No
        More
        Parse
        Infos
        tick
        tick
        4 variables
        this
        1
        7
        player
        1
        9
        verb
        2
        tick
        marker
        0
        42
        #0.audit_restart = marker;
        .
        0 suspended tasks
        0 interrupted tasks
        0 active connections with listeners
        0
        0
        0
        """;
  }
}
