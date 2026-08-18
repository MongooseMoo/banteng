package moo.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import moo.persistence.LambdaMooV17Codec.DurableTask;
import moo.persistence.LambdaMooV17Codec.QueuedTask;
import moo.persistence.LambdaMooV17Codec.SuspendedActivation;
import moo.persistence.LambdaMooV17Codec.SuspendedStackSlot;
import moo.persistence.LambdaMooV17Codec.SuspendedTask;
import moo.value.MooValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.world.WorldTxn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepeatedRestartTest {
  @Test
  void oneHundredCheckpointRestartCyclesPreserveWorldTasksAndOrderedBytes(
      @TempDir Path directory) throws IOException {
    LambdaMooV17Codec codec = new LambdaMooV17Codec();
    Path previous = directory.resolve("cycle-0.db");
    List<DurableTask> tasks = List.of(queuedTask(), suspendedTask());
    codec.writeAtomic(
        previous, new WorldTxn(List.of(), List.of()).snapshot(), tasks, List.of());

    for (int cycle = 1; cycle <= 100; cycle++) {
      LambdaMooV17Codec.Checkpoint before = codec.read(previous);
      byte[] ordered = Files.readAllBytes(previous);
      Path next = directory.resolve("cycle-" + cycle + ".db");
      codec.writeAtomic(next, before.world().snapshot(), before.tasks(), before.activeConnections());
      LambdaMooV17Codec.Checkpoint after = codec.read(next);

      assertEquals(before.world().snapshot(), after.world().snapshot());
      assertEquals(before.tasks(), after.tasks());
      assertEquals(List.of(41L, 73L), after.tasks().stream().map(RepeatedRestartTest::taskId).toList());
      assertArrayEquals(ordered, Files.readAllBytes(next));
      previous = next;
    }
  }

  private static long taskId(DurableTask task) {
    return switch (task) {
      case QueuedTask queued -> queued.taskId();
      case SuspendedTask suspended -> suspended.taskId();
    };
  }

  private static QueuedTask queuedTask() {
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("this", new ObjectValue(0));
    locals.put("player", new ObjectValue(0));
    locals.put("verb", string("queued"));
    return new QueuedTask(
        41,
        2_000_000_000,
        1,
        "queued",
        "queued",
        "return 0;\n",
        locals,
        0,
        new ObjectValue(0),
        0,
        true);
  }

  private static SuspendedTask suspendedTask() {
    return new SuspendedTask(
        73,
        2_000_000_001,
        new IntegerValue(42),
        new MapValue(Map.of()),
        -1,
        0,
        50,
        Optional.empty(),
        List.of(
            new SuspendedActivation(
                17,
                "suspend();\n",
                Map.of("this", Optional.of(new ObjectValue(0))),
                List.of(new SuspendedStackSlot(Optional.empty(), 6, 0)),
                new ObjectValue(0),
                new ObjectValue(0),
                true,
                0,
                0,
                true,
                "suspend",
                "suspend",
                Optional.empty(),
                20,
                0,
                18)));
  }

  private static StringValue string(String value) {
    return StringValue.of(value);
  }
}
