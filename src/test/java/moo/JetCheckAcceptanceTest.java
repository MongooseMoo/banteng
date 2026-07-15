package moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.world.WorldObject;
import moo.world.WorldTxn;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.ImperativeCommand;
import org.jetbrains.jetCheck.IntDistribution;
import org.jetbrains.jetCheck.PropertyChecker;
import org.jetbrains.jetCheck.PropertyFalsified;
import org.junit.jupiter.api.Test;

final class JetCheckAcceptanceTest {
  private static final String RECHECK_TOKEN = "AOudqRMBCgMI";
  private static final String MINIMAL_COUNTEREXAMPLE = "{10, {E_RECMOVE}}";

  @Test
  // JetCheck exposes deterministic seed and replay only through its deprecated diagnostic API.
  @SuppressWarnings("deprecation")
  void nestedValueFailureIsMinimizedAndReplayable() {
    PropertyFalsified falsified =
        assertThrows(
            PropertyFalsified.class,
            () ->
                PropertyChecker.customized()
                    .silent()
                    .withSeed(20_260_715L)
                    .withIterationCount(200)
                    .forAll(minimizableNestedValues(), value -> nestingDepth(value) < 2));

    assertEquals(MINIMAL_COUNTEREXAMPLE, falsified.getBreakingValue().toString());
    assertEquals(1, falsified.getFailure().getShrinkingStageCount());
    assertEquals(1, falsified.getFailure().getTotalShrinkingExampleCount());
    assertEquals(
        RECHECK_TOKEN, falsified.getFailure().getMinimalCounterexample().getSerializedData());

    PropertyFalsified replayed =
        assertThrows(
            PropertyFalsified.class,
            () ->
                PropertyChecker.customized()
                    .silent()
                    .rechecking(RECHECK_TOKEN)
                    .forAll(minimizableNestedValues(), value -> nestingDepth(value) < 2));

    assertEquals(falsified.getBreakingValue(), replayed.getBreakingValue());
  }

  @Test
  void statefulWorldTxnCommandsStayInSyncWithModel() {
    PropertyChecker.customized()
        .withIterationCount(100)
        .silent()
        .checkScenarios(JetCheckAcceptanceTest::worldScenario);
  }

  private static ImperativeCommand worldScenario() {
    WorldTxn world = new WorldTxn(List.of(), List.of());
    List<Long> liveObjectIds = new ArrayList<>();
    return environment -> {
      ImperativeCommand create =
          commandEnvironment -> {
            WorldObject created = world.createObject(-1, -1);
            liveObjectIds.add(created.id());
            assertWorldMatchesModel(world, liveObjectIds);
          };
      ImperativeCommand recycle =
          commandEnvironment -> {
            int index =
                commandEnvironment.generateValue(
                    Generator.integers(0, liveObjectIds.size() - 1), "recycle model index %s");
            long objectId = liveObjectIds.remove(index);
            assertTrue(world.recycleObject(objectId));
            assertTrue(world.object(objectId).isEmpty());
            assertWorldMatchesModel(world, liveObjectIds);
          };
      Generator<ImperativeCommand> commands =
          Generator.from(
              data ->
                  liveObjectIds.isEmpty()
                      ? create
                      : data.generate(Generator.sampledFrom(create, recycle)));
      environment.executeCommands(IntDistribution.uniform(1, 30), commands);
    };
  }

  private static void assertWorldMatchesModel(WorldTxn world, List<Long> liveObjectIds) {
    assertEquals(liveObjectIds.size(), world.objectCount());
    for (long objectId : liveObjectIds) {
      assertTrue(world.object(objectId).isPresent());
    }
  }

  private static Generator<MooValue> minimizableNestedValues() {
    return Generator.zipWith(
        Generator.integers(10, 100),
        nestedValues(),
        (marker, value) ->
            new ListValue(
                List.of(new IntegerValue(marker.longValue()), new ListValue(List.of(value)))));
  }

  private static Generator<MooValue> nestedValues() {
    Generator<MooValue> scalar =
        Generator.anyOf(
            Generator.integers(-100, 100).map(value -> new IntegerValue(value.longValue())),
            Generator.stringsOf(Generator.asciiLetters())
                .map(value -> new StringValue(value.getBytes(StandardCharsets.ISO_8859_1))),
            Generator.integers(-10, 10).map(value -> new ObjectValue(value.longValue())),
            Generator.sampledFrom(ErrorValue.values()));

    return Generator.<MooValue>recursive(
            self ->
                Generator.frequency(
                    5,
                    scalar,
                    2,
                    Generator.listsOf(IntDistribution.uniform(0, 3), self).map(ListValue::new),
                    1,
                    Generator.zipWith(
                        scalar, self, (key, value) -> new MapValue(Map.of(key, value)))))
        .withBase(scalar);
  }

  private static int nestingDepth(MooValue value) {
    if (value instanceof ListValue list) {
      return 1
          + list.elements().stream()
              .map(JetCheckAcceptanceTest::nestingDepth)
              .max(Comparator.naturalOrder())
              .orElse(0);
    }
    if (value instanceof MapValue map) {
      return 1
          + map.entries().values().stream()
              .map(JetCheckAcceptanceTest::nestingDepth)
              .max(Comparator.naturalOrder())
              .orElse(0);
    }
    return 0;
  }
}
