package world.mongoose.banteng.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import world.mongoose.banteng.persistence.LambdaMooV17Codec.QueuedTask;
import world.mongoose.banteng.persistence.LambdaMooV17Codec.SuspendedActivation;
import world.mongoose.banteng.persistence.LambdaMooV17Codec.SuspendedStackSlot;
import world.mongoose.banteng.persistence.LambdaMooV17Codec.SuspendedTask;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.WorldSnapshot;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldTxn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QueuedTaskV17CodecTest {
  @Test
  void roundTripsOneCompleteSuspendedVm(@TempDir Path temporaryDirectory) throws IOException {
    SuspendedTask task =
        new SuspendedTask(
            73,
            1_700_000_321,
            new IntegerValue(0),
            new MapValue(Map.of()),
            -1,
            0,
            50,
            Optional.empty(),
            List.of(
                new SuspendedActivation(
                    17,
                    "suspend();\n",
                    Map.of(
                        "this", Optional.of(new ObjectValue(7)),
                        "unset", Optional.empty()),
                    List.of(
                        new SuspendedStackSlot(Optional.empty(), 6, 0),
                        new SuspendedStackSlot(
                            Optional.of(new IntegerValue(4)), -1, 0)),
                    new ObjectValue(7),
                    new ObjectValue(8),
                    true,
                    9,
                    3,
                    true,
                    "tick",
                    "tick pulse",
                    Optional.empty(),
                    20,
                    0,
                    18)));
    Path checkpoint = temporaryDirectory.resolve("suspended.db");

    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    codec.writeAtomic(checkpoint, emptyWorld(), List.of(task), List.of());

    assertEquals(List.of(task), codec.read(checkpoint).tasks());
  }

  @Test
  void roundTripsOneQueuedTaskUsingPinnedToastGrammar(@TempDir Path temporaryDirectory)
      throws IOException {
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    QueuedTask task = task();
    Path checkpoint = temporaryDirectory.resolve("queued.db");

    codec.writeAtomic(checkpoint, emptyWorld(), List.of(task), List.of());

    LambdaMooV17Codec.Checkpoint restored = codec.read(checkpoint);
    assertEquals(List.of(task), restored.tasks());
    assertEquals(expectedDatabase(), Files.readString(checkpoint, StringValue.charset()));
  }

  @Test
  void restoredQueuedTaskWritesByteForByte(@TempDir Path temporaryDirectory)
      throws IOException {
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    Path first = temporaryDirectory.resolve("first.db");
    Path second = temporaryDirectory.resolve("second.db");
    codec.writeAtomic(first, emptyWorld(), List.of(task()), List.of());

    LambdaMooV17Codec.Checkpoint restored = codec.read(first);
    codec.writeAtomic(
        second, restored.world().snapshot(), restored.tasks(), restored.activeConnections());

    assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
  }

  @Test
  void preservesUninitializedQueuedTaskLocals(@TempDir Path temporaryDirectory)
      throws IOException {
    String fixture =
        expectedDatabase()
            .replace(
                "4 variables\nthis\n1\n7\nplayer\n1\n9\nverb\n2\ntick\nmarker\n",
                "5 variables\nthis\n1\n7\nplayer\n1\n9\nverb\n2\ntick\nunset\n6\nmarker\n");
    Path input = temporaryDirectory.resolve("toast.db");
    Path output = temporaryDirectory.resolve("banteng.db");
    Files.writeString(input, fixture, StringValue.charset());

    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    LambdaMooV17Codec.Checkpoint restored = codec.read(input);
    QueuedTask task = assertInstanceOf(QueuedTask.class, restored.tasks().getFirst());
    assertEquals(Optional.empty(), task.environment().slots().get("unset"));
    assertFalse(task.initialLocals().containsKey("unset"));
    codec.writeAtomic(
        output, restored.world().snapshot(), restored.tasks(), restored.activeConnections());

    assertArrayEquals(Files.readAllBytes(input), Files.readAllBytes(output));
  }

  @Test
  void preservesQueuedTaskSourceAndDispatchFieldsFromToastFixture(
      @TempDir Path temporaryDirectory) throws IOException {
    byte[] fixture;
    try (var input =
        QueuedTaskV17CodecTest.class.getResourceAsStream(
            "/world/mongoose/banteng/persistence/queued-task-source-and-dispatch.db")) {
      fixture = input == null ? new byte[0] : input.readAllBytes();
    }
    assertTrue(fixture.length > 0);
    Path input = temporaryDirectory.resolve("toast.db");
    Path output = temporaryDirectory.resolve("banteng.db");
    Files.write(input, fixture);

    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    LambdaMooV17Codec.Checkpoint restored = codec.read(input);
    QueuedTask task = assertInstanceOf(QueuedTask.class, restored.tasks().getFirst());
    assertEquals(4, task.firstSourceLine());
    assertEquals("audit_t", task.calledVerb());
    assertEquals("audit_task_aliases audit_t*", task.fullVerbName());
    codec.writeAtomic(
        output, restored.world().snapshot(), restored.tasks(), restored.activeConnections());

    assertArrayEquals(fixture, Files.readAllBytes(output));
  }

  @Test
  void queuedTaskRetainsAnAnonymousVerbLocationIdentity(@TempDir Path temporaryDirectory)
      throws IOException {
    AnonymousObjectValue identity = new AnonymousObjectValue();
    WorldObject parent =
        new WorldObject(0, "parent", 0, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldAnonymousObject body =
        new WorldAnonymousObject("anonymous", 0, 0, List.of(0L), List.of(), List.of());
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("this", identity);
    locals.put("player", new ObjectValue(0));
    locals.put("verb", string("tick"));
    QueuedTask task =
        new QueuedTask(
            7, 12, 1, "tick", "tick", "return 0;\n", locals, 0, identity, 0, true);
    WorldSnapshot world =
        new WorldTxn(
                List.of(),
                List.of(parent),
                Map.of(identity, body),
                Map.of(),
                List.of(),
                0)
            .snapshot();
    Path checkpoint = temporaryDirectory.resolve("anonymous-queued.db");

    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    codec.writeAtomic(checkpoint, world, List.of(task), List.of());
    QueuedTask restored =
        assertInstanceOf(QueuedTask.class, codec.read(checkpoint).tasks().getFirst());
    AnonymousObjectValue restoredLocation =
        assertInstanceOf(AnonymousObjectValue.class, restored.verbLocation());

    assertSame(restoredLocation, restored.initialLocals().get("this"));
  }

  @Test
  void roundTripsDisabledDebugAndThreadModesWithoutNormalizingThem(
      @TempDir Path temporaryDirectory)
      throws IOException {
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    QueuedTask disabled =
        new QueuedTask(
            task().taskId(),
            task().scheduledEpochSecond(),
            task().firstSourceLine(),
            task().calledVerb(),
            task().fullVerbName(),
            task().programSource(),
            task().initialLocals(),
            task().programmer(),
            task().verbLocation(),
            task().taskPlayer(),
            false,
            false);
    Path checkpoint = temporaryDirectory.resolve("disabled.db");

    codec.writeAtomic(checkpoint, emptyWorld(), List.of(disabled), List.of());

    assertEquals(List.of(disabled), codec.read(checkpoint).tasks());
    assertEquals(
        0,
        Files.readString(checkpoint, StringValue.charset())
            .lines()
            .skip(12)
            .findFirst()
            .map(Integer::parseInt)
            .orElseThrow());
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
        1,
        "tick",
        "tick",
        "#0.audit_restart = marker;\n",
        locals,
        3,
        new ObjectValue(8),
        9,
        true);
  }

  private static WorldSnapshot emptyWorld() {
    return new WorldTxn(List.of(), List.of()).snapshot();
  }

  private static StringValue string(String value) {
    return StringValue.of(value);
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
