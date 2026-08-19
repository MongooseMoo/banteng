package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import world.mongoose.banteng.persistence.LambdaMooV4Reader;
import world.mongoose.banteng.server.ConnectionRegistry;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import world.mongoose.banteng.world.WorldTxn;
import org.junit.jupiter.api.Test;

final class FinalizationControlTest {
  private static final Path FIXTURE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");
  private static final long CONNECTION_ID = -47;
  private static final long SECOND_CONNECTION_ID = -48;
  private static final long THIRD_CONNECTION_ID = -49;
  private static final String ANONYMOUS_MARKER = "__banteng_anonymous_finalization__";
  private static final String WAIF_MARKER = "__banteng_waif_finalization__";

  @Test
  void validatesTypedFinalizationKindAgainstItsTarget() {
    AnonymousObjectValue anonymous = new AnonymousObjectValue();
    WaifValue waif = new WaifValue(new ObjectValue(7), new ObjectValue(1));

    MooRuntime.FinalizationControl anonymousControl =
        new MooRuntime.FinalizationControl(MooRuntime.FinalizationKind.ANONYMOUS, anonymous);
    MooRuntime.FinalizationControl waifControl =
        new MooRuntime.FinalizationControl(MooRuntime.FinalizationKind.WAIF, waif);

    assertSame(anonymous, anonymousControl.target());
    assertSame(waif, waifControl.target());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new MooRuntime.FinalizationControl(
                MooRuntime.FinalizationKind.ANONYMOUS, waif));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MooRuntime.FinalizationControl(MooRuntime.FinalizationKind.WAIF, anonymous));
  }

  @Test
  void oldMarkerShapedPendingListsRemainOrdinaryMooValues() {
    AnonymousObjectValue anonymous = new AnonymousObjectValue();
    WaifValue waif = new WaifValue(new ObjectValue(7), new ObjectValue(1));
    ListValue anonymousList = marker(ANONYMOUS_MARKER, anonymous);
    ListValue waifList = marker(WAIF_MARKER, waif);
    WorldTxn world =
        new WorldTxn(
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            List.of(anonymousList, waifList),
            -1);

    try (MooRuntime runtime = new MooRuntime(world, new ConnectionRegistry())) {
      runtime.startServer();
      assertEquals(List.of(anonymousList, waifList), world.snapshot().pendingFinalization());
    }
  }

  @Test
  void mooReturnedMarkerShapeCannotImpersonateFinalizationControl() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    try (MooRuntime runtime = connectedRuntime(world)) {
      List<String> output =
          runtime.executeLine(
              CONNECTION_ID,
              "; class = create(#-1); "
                  + "add_property(class, \"recycle_called\", 0, {player, \"\"}); "
                  + "add_verb(class, {player, \"xd\", \"recycle\"}, "
                  + "{\"this\", \"none\", \"this\"}); "
                  + "set_verb_code(class, \"recycle\", {"
                  + "tostr(class) + \".recycle_called = \" + tostr(class) "
                  + "+ \".recycle_called + 1;\"}); "
                  + "value = create(class, 1); "
                  + "add_property(#0, \"typed_marker_class\", class, {player, \"rw\"}); "
                  + "add_property(#0, \"typed_marker_payload\", "
                  + "{\"__banteng_anonymous_finalization__\", value}, {player, \"rw\"}); "
                  + "return #0.typed_marker_payload;");
      long anonymousClass = objectPropertyId(world, "typed_marker_class");

      assertTrue(output.toString().contains(ANONYMOUS_MARKER), output.toString());
      assertContains(
          runtime.executeLine(
              CONNECTION_ID,
              "; suspend(0); return {#"
                  + anonymousClass
                  + ".recycle_called, valid(#0.typed_marker_payload[2])};"),
          "{1, {0, 1}}");
      assertTrue(world.snapshot().pendingFinalization().isEmpty());
    }
  }

  @Test
  void suspendedAnonymousHookKeepsItsRawRootWithoutDuplicateHooks() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    try (MooRuntime runtime = connectedRuntime(world)) {
      runtime.executeLine(
          CONNECTION_ID,
          "; class = create(#-1); "
              + "add_property(class, \"subject\", 0, {player, \"\"}); "
              + "add_property(class, \"recycle_called\", 0, {player, \"\"}); "
              + "add_verb(class, {player, \"xd\", \"recycle\"}, {\"this\", \"none\", \"this\"}); "
              + "set_verb_code(class, \"recycle\", {"
              + "tostr(class) + \".recycle_called = \" + tostr(class) "
              + "+ \".recycle_called + 1;\", "
              + "\"suspend();\"}); "
              + "class.subject = create(class, 1); "
              + "add_property(#0, \"typed_anon_class\", class, {player, \"rw\"}); return 0;");
      long anonymousClass = objectPropertyId(world, "typed_anon_class");

      runtime.executeLine(
          CONNECTION_ID, "; #0.typed_anon_class.subject = 0; run_gc(); return 0;");
      awaitProperty(world, anonymousClass, "recycle_called", new IntegerValue(1));
      AnonymousObjectValue pending =
          assertInstanceOf(
              AnonymousObjectValue.class, world.snapshot().pendingFinalization().getFirst());

      runtime.executeLine(CONNECTION_ID, "; run_gc(); return 0;");
      assertEquals(List.of(pending), world.snapshot().pendingFinalization());
      assertEquals(
          new IntegerValue(1), readProperty(world, anonymousClass, "recycle_called"));
    }
  }

  @Test
  void suspendedWaifHookKeepsItsRawRootWithoutDuplicateHooks() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    try (MooRuntime runtime = connectedRuntime(world)) {
      runtime.executeLine(
          CONNECTION_ID,
          "; class = create($waif); "
              + "add_property(class, \"recycle_called\", 0, {player, \"\"}); "
              + "add_verb(class, {player, \"xd\", \":recycle\"}, {\"this\", \"none\", \"this\"}); "
              + "set_verb_code(class, \":recycle\", {"
              + "\"this.class.recycle_called = this.class.recycle_called + 1;\", "
              + "\"suspend();\"}); "
              + "add_property(#0, \"typed_waif_class\", class, {player, \"rw\"}); "
              + "add_property(#0, \"typed_waif_subject\", class:new(), {player, \"rw\"}); return 0;");
      long waifClass = objectPropertyId(world, "typed_waif_class");

      runtime.executeLine(CONNECTION_ID, "; #0.typed_waif_subject = 0; run_gc(); return 0;");
      awaitProperty(world, waifClass, "recycle_called", new IntegerValue(1));
      WaifValue pending =
          assertInstanceOf(WaifValue.class, world.snapshot().pendingFinalization().getFirst());

      runtime.executeLine(CONNECTION_ID, "; run_gc(); return 0;");
      assertEquals(List.of(pending), world.snapshot().pendingFinalization());
      assertEquals(new IntegerValue(1), readProperty(world, waifClass, "recycle_called"));
    }
  }

  @Test
  void anonymousControlSurvivesForkWakeIrrevocableRetryAndZeroDelayWake() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    try (MooRuntime runtime = connectedRuntime(world)) {
      runtime.executeLine(
          CONNECTION_ID,
          "; class = create(#-1); "
              + "add_property(class, \"subject\", 0, {player, \"\"}); "
              + "add_property(class, \"recycle_started\", 0, {player, \"\"}); "
              + "add_property(class, \"recycle_finished\", 0, {player, \"\"}); "
              + "add_verb(class, {player, \"xd\", \"recycle\"}, {\"this\", \"none\", \"this\"}); "
              + "set_verb_code(class, \"recycle\", {"
              + "tostr(class) + \".recycle_started = \" + tostr(class) "
              + "+ \".recycle_started + 1;\", "
              + "\"fork (0)\", "
              + "\"  return 0;\", "
              + "\"endfork\", "
              + "\"random(1);\", "
              + "\"suspend(0);\", "
              + "tostr(class) + \".recycle_finished = \" + tostr(class) "
              + "+ \".recycle_finished + 1;\"}); "
              + "class.subject = create(class, 1); "
              + "add_property(#0, \"typed_boundary_class\", class, {player, \"rw\"}); return 0;");
      long anonymousClass = objectPropertyId(world, "typed_boundary_class");

      runtime.executeLine(
          CONNECTION_ID, "; #0.typed_boundary_class.subject = 0; run_gc(); return 0;");

      assertContains(
          runtime.executeLine(
              CONNECTION_ID,
              "; suspend(0); return {#"
                  + anonymousClass
                  + ".recycle_started, #"
                  + anonymousClass
                  + ".recycle_finished};"),
          "{1, {1, 1}}");
      assertContains(
          runtime.executeLine(
              CONNECTION_ID,
              "; suspend(0); return {#"
                  + anonymousClass
                  + ".recycle_started, #"
                  + anonymousClass
                  + ".recycle_finished};"),
          "{1, {1, 1}}");
      assertTrue(world.snapshot().pendingFinalization().isEmpty());
    }
  }

  @Test
  void ordinaryZeroDelayWakeWaitsForActiveAnonymousFinalizer() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    try (MooRuntime runtime = connectedRuntime(world)) {
      connect(runtime, SECOND_CONNECTION_ID);
      connect(runtime, THIRD_CONNECTION_ID);
      runtime.executeLine(
          CONNECTION_ID,
          "; class = create(#-1); "
              + "add_property(class, \"subject\", 0, {player, \"\"}); "
              + "add_property(class, \"reader_player\", player, {player, \"\"}); "
              + "add_property(class, \"recycle_started\", 0, {player, \"\"}); "
              + "add_property(class, \"recycle_finished\", 0, {player, \"\"}); "
              + "add_property(class, \"observer_entered\", 0, {player, \"\"}); "
              + "add_property(class, \"observer_woke\", 0, {player, \"\"}); "
              + "add_property(class, \"observer_saw_finished\", -1, {player, \"\"}); "
              + "add_verb(class, {player, \"xd\", \"recycle\"}, {\"this\", \"none\", \"this\"}); "
              + "set_verb_code(class, \"recycle\", {"
              + "tostr(class) + \".recycle_started = 1;\", "
              + "\"read(\" + tostr(class) + \".reader_player);\", "
              + "tostr(class) + \".recycle_finished = 1;\"}); "
              + "class.subject = create(class, 1); "
              + "add_property(#0, \"typed_order_class\", class, {player, \"rw\"}); return 0;");
      long anonymousClass = objectPropertyId(world, "typed_order_class");

      runtime.executeLine(
          CONNECTION_ID, "; #0.typed_order_class.subject = 0; run_gc(); return 0;");
      assertContains(
          runtime.executeLine(
              THIRD_CONNECTION_ID,
              "; random(1); return {#"
                  + anonymousClass
                  + ".recycle_started, #"
                  + anonymousClass
                  + ".recycle_finished, threads()};"),
          "{1, {1, 0, {1}}}");

      runtime.executeLine(
          SECOND_CONNECTION_ID,
          "; class = #0.typed_order_class; "
              + "fork observer_task (0) "
              + "  class.observer_entered = 1; "
              + "  suspend(0); "
              + "  class.observer_saw_finished = class.recycle_finished; "
              + "  class.observer_woke = 1; "
              + "endfork "
              + "fork barrier_task (0) "
              + "  return 0; "
              + "endfork "
              + "return {observer_task, barrier_task};");

      assertContains(
          runtime.executeLine(
              THIRD_CONNECTION_ID,
              "; return {#"
                  + anonymousClass
                  + ".observer_entered, #"
                  + anonymousClass
                  + ".observer_woke, #"
                  + anonymousClass
                  + ".observer_saw_finished, #"
                  + anonymousClass
                  + ".recycle_finished, threads()};"),
          "{1, {1, 0, -1, 0, {1}}}");
      assertFalse(world.snapshot().pendingFinalization().isEmpty());

      assertEquals(List.of(), runtime.executeLine(CONNECTION_ID, "release"));
      assertContains(
          runtime.executeLine(
              THIRD_CONNECTION_ID,
              "; while (#"
                  + anonymousClass
                  + ".observer_woke == 0) suspend(0); endwhile "
                  + "return {#"
                  + anonymousClass
                  + ".recycle_finished, #"
                  + anonymousClass
                  + ".observer_woke, #"
                  + anonymousClass
                  + ".observer_saw_finished};"),
          "{1, {1, 1, 1}}");
      assertTrue(world.snapshot().pendingFinalization().isEmpty());
    }
  }

  @Test
  void erroredAnonymousFinalizerIsTerminalAndRunsOnce() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    try (MooRuntime runtime = connectedRuntime(world)) {
      runtime.executeLine(
          CONNECTION_ID,
          "; class = create(#-1); "
              + "add_property(class, \"subject\", 0, {player, \"\"}); "
              + "add_property(class, \"recycle_called\", 0, {player, \"\"}); "
              + "add_verb(class, {player, \"xd\", \"recycle\"}, {\"this\", \"none\", \"this\"}); "
              + "set_verb_code(class, \"recycle\", {"
              + "tostr(class) + \".recycle_called = \" + tostr(class) "
              + "+ \".recycle_called + 1;\", "
              + "\"return 1 / 0;\"}); "
              + "class.subject = create(class, 1); "
              + "add_property(#0, \"typed_error_class\", class, {player, \"rw\"}); return 0;");
      long anonymousClass = objectPropertyId(world, "typed_error_class");

      runtime.executeLine(
          CONNECTION_ID, "; #0.typed_error_class.subject = 0; run_gc(); return 0;");

      assertContains(
          runtime.executeLine(
              CONNECTION_ID,
              "; suspend(0); return #" + anonymousClass + ".recycle_called;"),
          "{1, 1}");
      assertContains(
          runtime.executeLine(
              CONNECTION_ID,
              "; suspend(0); return #" + anonymousClass + ".recycle_called;"),
          "{1, 1}");
      assertTrue(world.snapshot().pendingFinalization().isEmpty());
    }
  }

  @Test
  void erroredWaifFinalizerIsTerminalAndRunsOnce() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    try (MooRuntime runtime = connectedRuntime(world)) {
      runtime.executeLine(
          CONNECTION_ID,
          "; class = create($waif); "
              + "add_property(class, \"recycle_called\", 0, {player, \"\"}); "
              + "add_verb(class, {player, \"xd\", \":recycle\"}, {\"this\", \"none\", \"this\"}); "
              + "set_verb_code(class, \":recycle\", {"
              + "\"this.class.recycle_called = this.class.recycle_called + 1;\", "
              + "\"return 1 / 0;\"}); "
              + "add_property(#0, \"typed_error_waif_class\", class, {player, \"rw\"}); "
              + "add_property(#0, \"typed_error_waif\", class:new(), {player, \"rw\"}); return 0;");
      long waifClass = objectPropertyId(world, "typed_error_waif_class");

      runtime.executeLine(CONNECTION_ID, "; #0.typed_error_waif = 0; run_gc(); return 0;");
      awaitProperty(world, waifClass, "recycle_called", new IntegerValue(1));

      assertTrue(world.snapshot().pendingFinalization().isEmpty());
      runtime.executeLine(CONNECTION_ID, "; run_gc(); return 0;");
      assertEquals(new IntegerValue(1), readProperty(world, waifClass, "recycle_called"));
    }
  }

  @Test
  void anonConstantEvaluatesToItsSymbolicTypeCode() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    try (MooRuntime runtime = connectedRuntime(world)) {
      assertContains(
          runtime.executeLine(CONNECTION_ID, "; return ANON;"),
          "{1, " + MooValue.Type.ANONYMOUS.code() + "}");
    }
  }

  private static MooRuntime connectedRuntime(WorldTxn world) throws Exception {
    MooRuntime runtime = new MooRuntime(world, new ConnectionRegistry());
    connect(runtime, CONNECTION_ID);
    return runtime;
  }

  private static void connect(MooRuntime runtime, long connectionId) throws Exception {
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
  }

  private static ListValue marker(String name, MooValue target) {
    return new ListValue(
        List.of(StringValue.of(name), target));
  }

  private static void assertContains(List<String> output, String expected) {
    assertTrue(output.contains(expected), output.toString());
  }

  private static void awaitProperty(
      WorldTxn world, long objectId, String name, MooValue expected) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (!expected.equals(readProperty(world, objectId, name))
        && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    assertEquals(expected, readProperty(world, objectId, name));
  }

  private static MooValue readProperty(WorldTxn world, long objectId, String name) {
    try (WorldTxn transaction = world.begin()) {
      return transaction.readObjectProperty(objectId, name).orElseThrow();
    }
  }

  private static long objectPropertyId(WorldTxn world, String name) {
    return assertInstanceOf(ObjectValue.class, readProperty(world, 0, name)).value();
  }
}
