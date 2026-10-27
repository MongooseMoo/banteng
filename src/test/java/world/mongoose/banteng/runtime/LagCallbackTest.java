package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.VerbPerms;
import world.mongoose.banteng.world.WorldTxn;

final class LagCallbackTest {
  private static final long CONNECTION = -41;
  private static final String BUSY = "deadline = ftime(1) + 0.12; while (ftime(1) < deadline) endwhile ";

  @Test
  void reportsCompletedAbortedAndBlockedSlicesWithFreshContextAndToastPayload() throws Exception {
    for (String tail : List.of("return 37;", "raise(E_INVARG);", "suspend(0); #0.lag_done = 1; return 37;")) {
      try (SchedulerTestHarness harness = SchedulerTestHarness.open(1, CONNECTION)) {
        long player = harness.runtime.connectionPlayer(CONNECTION).orElseThrow();
        long owner = configure(harness, new FloatValue(0.1), "return 1;", tail);
        harness.runtime.executeLine(CONNECTION, "cb_lag");
        if (tail.startsWith("suspend")) {
          await(harness, "lag_done");
        }
        ListValue events = (ListValue) property(harness, "lag_events");
        assertEquals(1, events.size(), tail);
        ListValue event = (ListValue) events.elements().getFirst();
        List<MooValue> context = new ArrayList<>(event.elements());
        context.remove(4);
        assertEquals(List.of(StringValue.of("handle_lagging_task"), new ObjectValue(0),
            new ObjectValue(-1), new ObjectValue(player), StringValue.of(""), new ObjectValue(-1),
            StringValue.of(""), StringValue.of(""), new ObjectValue(-1), StringValue.of(""),
            new ObjectValue(owner), new ObjectValue(-1), new ListValue(List.of())), context);
        ListValue arguments = (ListValue) event.elements().get(4);
        assertEquals(2, arguments.size());
        assertTrue(assertInstanceOf(FloatValue.class, arguments.elements().get(1)).value() >= 0.1);
        if (tail.startsWith("return")) {
          assertEquals(new ListValue(List.of(new ObjectValue(-1), StringValue.of("cb_lag cb_lag_alias"),
              new ObjectValue(-1), new ObjectValue(player), new ObjectValue(-1), new ObjectValue(-1))),
              arguments.elements().getFirst());
        } else {
          ListValue frame = (ListValue) ((ListValue) arguments.elements().getFirst()).elements().getFirst();
          assertEquals(List.of(new ObjectValue(player), StringValue.of("cb_lag"), new ObjectValue(player),
              new ObjectValue(player), new ObjectValue(player)), frame.elements().subList(0, 5));
          assertTrue(assertInstanceOf(IntegerValue.class, frame.elements().get(5)).value() > 0);
        }
      }
    }
  }

  @Test
  void finishesLagFirstSliceBeforeOriginalErrorAndDetachesTimerOrHostSuspension() throws Exception {
    for (String callback : List.of("raise(E_INVARG);", "suspend(0.01); #0.lag_hook_done = 1;",
        "#0.lag_sorted = sort({3, 1, 2}); #0.lag_hook_done = 1;")) {
      try (SchedulerTestHarness harness = SchedulerTestHarness.open(1, CONNECTION)) {
        configure(harness, new FloatValue(0.1), callback, "raise(E_INVARG);");
        harness.runtime.executeLine(CONNECTION, "; fork (0) player:cb_lag(); endfork return 1;");
        await(harness, "lag_error_done");
        assertEquals(new ListValue(List.of(StringValue.of("lag"), StringValue.of("error"))),
            property(harness, "lag_order"));
        if (!callback.startsWith("raise")) {
          await(harness, "lag_hook_done");
        }
        if (callback.contains("sort")) {
          assertEquals(new ListValue(List.of(new IntegerValue(1), new IntegerValue(2), new IntegerValue(3))),
              property(harness, "lag_sorted"));
        }
      }
    }
  }

  @Test
  void retainsOriginalSuspensionWhenLagHandlerAlsoSuspends() throws Exception {
    for (String tail : List.of("#0.lag_hook_done = 1; return 1;", "suspend(0.01); #0.lag_hook_done = 1;")) {
      try (SchedulerTestHarness harness = SchedulerTestHarness.open(1, CONNECTION)) {
        long owner = configure(harness, new FloatValue(0.1), "notify(player, \"lag output\"); " + tail,
            "anon = create(#-1, player, 1); suspend(0.01); #0.lag_done = valid(anon); return 37;");
        try (WorldTxn world = harness.root.begin()) {
          assertTrue(world.writeObjectProperty(owner, "wizard", new IntegerValue(1)).isOk());
          assertTrue(world.commit().isCommitted());
        }
        assertEquals(List.of("lag output"), harness.runtime.executeLine(CONNECTION, "cb_lag"));
        await(harness, "lag_hook_done");
        await(harness, "lag_done");
        assertEquals(1, ((ListValue) property(harness, "lag_events")).size());
      }
    }
  }

  @Test
  void disablesBelowMinimumAndDefaultsWrongTypeWithoutRequiringOptionReload() throws Exception {
    for (MooValue threshold : List.of(new FloatValue(0.05), new IntegerValue(0))) {
      try (SchedulerTestHarness harness = SchedulerTestHarness.open(1, CONNECTION)) {
        configure(harness, threshold, "return 1;", "return 37;");
        harness.runtime.executeLine(CONNECTION, "cb_lag");
        assertEquals(new ListValue(List.of()), property(harness, "lag_events"));
      }
    }
  }

  @Test
  void ignoresMissingAndNonexecutableLagHandlers() throws Exception {
    for (boolean missing : List.of(false, true)) {
      try (SchedulerTestHarness harness = SchedulerTestHarness.open(1, CONNECTION)) {
        long owner = configure(harness, new FloatValue(0.1), "return 1;", "return 37;");
        try (WorldTxn world = harness.root.begin()) {
          var handler = world.verb(0, "handle_lagging_task", false).orElseThrow();
          int index = world.object(0).orElseThrow().verbs().indexOf(handler);
          assertTrue(missing ? world.deleteVerb(0, index).isOk()
              : world.setVerbInfo(0, index, "handle_lagging_task", owner, new VerbPerms(0)).isOk());
          assertTrue(world.commit().isCommitted());
        }
        harness.runtime.executeLine(CONNECTION, "cb_lag");
        assertEquals(new ListValue(List.of()), property(harness, "lag_events"));
      }
    }
  }

  private static long configure(SchedulerTestHarness harness, MooValue threshold, String callback, String tail) {
    long player = harness.runtime.connectionPlayer(CONNECTION).orElseThrow();
    try (WorldTxn world = harness.root.begin()) {
      long owner = world.createObject(-1, player).id();
      long options = ((ObjectValue) world.readObjectProperty(0, "server_options").orElseThrow()).value();
      set(world, options, "task_lag_threshold", threshold);
      set(world, options, "fg_ticks", new IntegerValue(Integer.MAX_VALUE));
      set(world, options, "bg_ticks", new IntegerValue(Integer.MAX_VALUE));
      for (String name : List.of("lag_events", "lag_order")) {
        set(world, 0, name, new ListValue(List.of()));
      }
      for (String name : List.of("lag_done", "lag_hook_done", "lag_sorted", "lag_error_done")) {
        set(world, 0, name, new IntegerValue(0));
      }
      verb(world, 0, owner, "handle_lagging_task",
          "$server_options.task_lag_threshold = 0.0; "
              + "#0.lag_events = {@#0.lag_events, {verb, this, caller, player, args, argstr, dobj, dobjstr, prepstr, iobj, iobjstr, task_perms(), caller_perms(), callers()}}; "
              + "#0.lag_order = {@#0.lag_order, \"lag\"}; " + callback);
      verb(world, 0, player, "handle_uncaught_error",
          "#0.lag_order = {@#0.lag_order, \"error\"}; #0.lag_error_done = 1; return 1;");
      verb(world, player, player, "cb_lag cb_lag_alias", BUSY + tail);
      assertTrue(world.commit().isCommitted());
      return owner;
    }
  }

  private static void set(WorldTxn world, long object, String name, MooValue value) {
    assertTrue(world.property(object, name).isPresent()
        ? world.writeObjectProperty(object, name, value).isOk()
        : world.addProperty(object, name, value, object, new PropertyPerms(3)).isOk());
  }

  private static void verb(WorldTxn world, long object, long owner, String names, String source) {
    int separator = names.indexOf(' ');
    var previous = world.verb(object, separator < 0 ? names : names.substring(0, separator), false).orElse(null);
    int index = previous == null
        ? world.addVerb(object, names, owner, new VerbPerms(12), ArgSpec.NONE, -1, ArgSpec.NONE) - 1
        : world.object(object).orElseThrow().verbs().indexOf(previous);
    assertTrue(world.setVerbInfo(object, index, names, owner, new VerbPerms(12)).isOk());
    assertTrue(world.setVerbCode(object, index, source).isOk());
  }

  private static MooValue property(SchedulerTestHarness harness, String name) {
    try (WorldTxn world = harness.root.begin()) {
      return world.readObjectProperty(0, name).orElseThrow();
    }
  }

  private static void await(SchedulerTestHarness harness, String name) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (property(harness, name).equals(new IntegerValue(0)) && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    assertEquals(new IntegerValue(1), property(harness, name));
  }
}
