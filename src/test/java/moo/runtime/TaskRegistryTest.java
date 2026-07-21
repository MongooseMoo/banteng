package moo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import moo.builtin.BuiltinCatalog.Result;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.world.WorldObject;
import moo.world.WorldTxn;
import org.junit.jupiter.api.Test;

final class TaskRegistryTest {
  @Test
  void returnsCompleteToastRowsWithVisibilityVariablesAndCountModes() {
    TaskRegistry registry = new TaskRegistry();
    registry.registerFork(
        17,
        1234,
        2,
        new ObjectValue(7),
        Map.of("verb", string("alpha"), "this", new ObjectValue(8), "x", new IntegerValue(9)));
    registry.registerFork(
        18,
        2345,
        3,
        new ObjectValue(9),
        Map.of("verb", string("beta"), "this", new ObjectValue(10)));

    try (WorldTxn transaction = world().begin()) {
      ListValue programmerRows = value(registry, List.of(), transaction, 2);
      assertEquals(1, programmerRows.size());
      ListValue row = assertInstanceOf(ListValue.class, programmerRows.get(1).orElseThrow());
      assertEquals(10, row.size());
      assertEquals(new IntegerValue(17), row.get(1).orElseThrow());
      assertEquals(new IntegerValue(1234), row.get(2).orElseThrow());
      assertEquals(new IntegerValue(0), row.get(3).orElseThrow());
      assertEquals(new IntegerValue(30_000), row.get(4).orElseThrow());
      assertEquals(new ObjectValue(2), row.get(5).orElseThrow());
      assertEquals(new ObjectValue(7), row.get(6).orElseThrow());
      assertEquals(string("alpha"), row.get(7).orElseThrow());
      assertEquals(new ObjectValue(8), row.get(9).orElseThrow());

      ListValue variableRows =
          value(registry, List.of(new IntegerValue(1)), transaction, 2);
      ListValue variableRow =
          assertInstanceOf(ListValue.class, variableRows.get(1).orElseThrow());
      assertEquals(11, variableRow.size());
      assertInstanceOf(MapValue.class, variableRow.get(11).orElseThrow());

      assertEquals(
          new IntegerValue(2),
          result(registry, List.of(new IntegerValue(0), new IntegerValue(1)), transaction, 1));
      assertEquals(2, value(registry, List.of(), transaction, 1).size());

      assertEquals(
          ErrorValue.E_PERM,
          killResult(registry, 18, transaction, 2).error().orElseThrow());
      assertEquals(
          ErrorValue.E_INVARG,
          killResult(registry, 99, transaction, 1).error().orElseThrow());
      assertEquals(
          new IntegerValue(0),
          killResult(registry, 18, transaction, 1).value().orElseThrow());
      assertEquals(1, registry.size());
      assertEquals(true, registry.discardIfCanceled(18));
    }

    registry.remove(17);
    assertEquals(0, registry.size());
  }

  private static Result killResult(
      TaskRegistry registry, long taskId, WorldTxn world, long programmer) {
    return registry.killTask(
        List.of(new IntegerValue(taskId)),
        world,
        programmer,
        new MapValue(Map.of()),
        0,
        60_000,
        5,
        new ObjectValue(programmer),
        programmer,
        new ListValue(List.of()));
  }

  private static ListValue value(
      TaskRegistry registry, List<MooValue> arguments, WorldTxn world, long programmer) {
    return assertInstanceOf(ListValue.class, result(registry, arguments, world, programmer));
  }

  private static MooValue result(
      TaskRegistry registry, List<MooValue> arguments, WorldTxn world, long programmer) {
    Result result =
        registry.queuedTasks(
            arguments,
            world,
            programmer,
            new MapValue(Map.of()),
            0,
            60_000,
            5,
            new ObjectValue(programmer),
            programmer,
            new ListValue(List.of()));
    return result.value().orElseThrow();
  }

  private static StringValue string(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  private static WorldTxn world() {
    return new WorldTxn(
        List.of(),
        List.of(
            object(1, 4),
            object(2, 2),
            object(3, 2)));
  }

  private static WorldObject object(long id, int flags) {
    return new WorldObject(
        id,
        "Object " + id,
        flags,
        id,
        -1,
        -1,
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
