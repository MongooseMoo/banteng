package moo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import moo.persistence.LambdaMooV4Reader;
import moo.world.WorldObject;
import moo.world.WorldTxn;
import moo.world.WorldVerb;
import org.junit.jupiter.api.Test;

final class MooRuntimeTest {
  private static final Path FIXTURE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");
  private static final String CONNECTION_PREFIX = "-=!-^-!=-";
  private static final String CONNECTION_SUFFIX = "-=!-v-!=-";

  @Test
  void executesTheFirstManagedRowThroughStoredVerbsAndOneWorldTxn() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    WorldObject wizard = readObject(world, 8).orElseThrow();
    assertEquals(8, wizard.owner());
    assertEquals(7, wizard.flags());
    assertEquals(2, wizard.location());
    assertEquals(List.of(3L, 4L, 8L), readObject(world, 2).orElseThrow().contents());
    assertEquals(List.of(3L, 4L, 8L), world.snapshot().players());
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 42}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return 6 * 7;"));
  }

  @Test
  void noArgumentReadFromForkIsNotTheLastInputTask() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, E_PERM}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; try add_property(#0, \"runtime_read_error\", $nothing, {#0, \"rw\"}); "
                + "except (E_INVARG) endtry "
                + "fork (0) #0.runtime_read_error = `read() ! E_PERM => E_PERM'; endfork "
                + "suspend(0); return #0.runtime_read_error;"));
  }

  @Test
  void blockingReadResumesWithForcedInputForImplicitAndExplicitPlayer() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, \"implicit-line\"}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; fork (0) force_input(player, \"implicit-line\"); endfork return read();"));
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, \"explicit-line\"}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; fork (0) force_input(player, \"explicit-line\"); endfork "
                + "return read(player);"));
  }

  @Test
  void flushInputUsesToastSelfOrWizardPermissionWithoutRequiringALiveTarget() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long wizardConnection = -47;
    long programmerConnection = -48;

    assertEquals(List.of(), runtime.openConnection(wizardConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(wizardConnection, "connect Wizard"));
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 0}", CONNECTION_SUFFIX),
        runtime.executeLine(wizardConnection, "; return flush_input(#999999, 1);"));

    assertEquals(List.of(), runtime.openConnection(programmerConnection));
    assertEquals(
        List.of("*** Connected ***"),
        runtime.executeLine(programmerConnection, "connect Programmer"));
    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            "{2, {E_PERM, \"Permission denied\", 0, "
                + "{{#-1, \"\", #-1, #-1, #9, 1}, {#2, \"eval\", #9, #2, #9, 5}}}}",
            CONNECTION_SUFFIX),
        runtime.executeLine(programmerConnection, "; return flush_input(#0);"));
  }

  @Test
  void outputDelimitersReturnsLiveSessionValuesWithToastPermissionOrder() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long programmerConnection = -47;
    long wizardConnection = -48;

    assertEquals(List.of(), runtime.openConnection(programmerConnection));
    assertEquals(
        List.of("*** Connected ***"),
        runtime.executeLine(programmerConnection, "connect Programmer"));
    assertEquals(List.of(), runtime.executeLine(programmerConnection, "PREFIX PRE"));
    assertEquals(List.of(), runtime.executeLine(programmerConnection, "SUFFIX POST"));
    assertEquals(
        List.of(
            "PRE",
            CONNECTION_PREFIX,
            "{1, {\"PRE\", \"POST\"}}",
            CONNECTION_SUFFIX,
            "POST"),
        runtime.executeLine(
            programmerConnection, "; return output_delimiters(player);"));
    assertEquals(
        List.of(
            "PRE",
            CONNECTION_PREFIX,
            "{2, {E_PERM, \"Permission denied\", 0, "
                + "{{#-1, \"\", #-1, #-1, #8, 1}, {#2, \"eval\", #8, #2, #8, 5}}}}",
            CONNECTION_SUFFIX,
            "POST"),
        runtime.executeLine(programmerConnection, "; return output_delimiters(#0);"));

    assertEquals(List.of(), runtime.openConnection(wizardConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(wizardConnection, "connect Wizard"));
    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            "{2, {E_INVARG, \"Invalid argument\", 0, "
                + "{{#-1, \"\", #-1, #-1, #9, 1}, {#2, \"eval\", #9, #2, #9, 5}}}}",
            CONNECTION_SUFFIX),
        runtime.executeLine(wizardConnection, "; return output_delimiters(#999999);"));
  }

  @Test
  void queueInfoReadsTheExistingConnectionAndTaskOwners() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long programmerConnection = -47;
    long wizardConnection = -48;

    assertEquals(List.of(), runtime.openConnection(programmerConnection));
    assertEquals(
        List.of("*** Connected ***"),
        runtime.executeLine(programmerConnection, "connect Programmer"));
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, {4, 0, 0, 1}}", CONNECTION_SUFFIX),
        runtime.executeLine(
            programmerConnection,
            "; return {typeof(queue_info()), typeof(queue_info(player)), "
                + "queue_info(player), (player in queue_info()) > 0};"));

    assertEquals(List.of(), runtime.openConnection(wizardConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(wizardConnection, "connect Wizard"));
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, {10, #9, true, 0, 0}}", CONNECTION_SUFFIX),
        runtime.executeLine(
            wizardConnection,
            "; info = queue_info(player); "
                + "return {typeof(info), info[\"player\"], info[\"connected\"], "
                + "info[\"num_bg_tasks\"], queue_info(#999999)};"));
  }

  @Test
  void writesIntrinsicFertileFlagAsIntegerZero() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long object = world.snapshot().objects().size();

    try {
      List<String> output =
          runtime.executeLine(
              connectionId,
              """
              ; object = create(#-1);
              return object.f = 0;
              """);

      assertEquals(0, readObject(world, object).orElseThrow().flags() & 128);
      assertEquals(List.of(CONNECTION_PREFIX, "{1, 0}", CONNECTION_SUFFIX), output);
    } finally {
      if (readObject(world, object).isPresent()) {
        runtime.executeLine(connectionId, "; recycle(#" + object + "); return 1;");
      }
    }
  }

  @Test
  void dispatchesMatchingCommandVerbWithoutExecutePermission() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    int verbIndex;
    try (WorldTxn transaction = world.begin()) {
      int verbNumber = transaction.addVerb(player, "auditnoexec", player, 8, -1);
      assertTrue(verbNumber > 0);
      verbIndex = verbNumber - 1;
      assertTrue(
          transaction.setVerbCode(player, verbIndex, "notify(player, \"EXECUTED\");"));
      assertTrue(transaction.commit().isCommitted());
    }
    assertEquals(8, readVerb(world, player, verbIndex).orElseThrow().permissions());

    assertEquals(List.of("EXECUTED"), runtime.executeLine(connectionId, "auditnoexec"));
  }

  @Test
  void dispatchesUnknownCommandsThroughSelectedHuhVerb() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; try
                delete_property($server_options, "player_huh");
              except (ANY)
              endtry
              add_verb(player, {player, "xd", "huh"}, {"none", "none", "none"});
              set_verb_code(player, "huh", {"notify(player, \\\"PLAYER_HUH\\\");"});
              room = player.location;
              add_verb(room, {player, "xd", "huh"}, {"none", "none", "none"});
              set_verb_code(room, "huh", {"notify(player, \\\"LOCATION_HUH\\\");"});
              return 1;
            """));

    assertEquals(List.of("LOCATION_HUH"), runtime.executeLine(connectionId, "zip"));
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; add_property($server_options, \"player_huh\", 1, {player, \"r\"}); return 1;"));
    assertEquals(List.of("PLAYER_HUH"), runtime.executeLine(connectionId, "zap"));
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; $server_options.player_huh = 0; return 1;"));
    assertEquals(List.of("LOCATION_HUH"), runtime.executeLine(connectionId, "zop"));
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId, "; $server_options.player_huh = \"yes\"; return 1;"));
    assertEquals(List.of("LOCATION_HUH"), runtime.executeLine(connectionId, "zaz"));
  }

  @Test
  void evalRuntimeErrorUnwindsIntoPersistedCallerExceptAndFinally() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{2, {E_TYPE, \"Type mismatch\", 0, "
                + "{{#-1, \"\", #-1, #-1, #8, 1}, {#2, \"eval\", #8, #2, #8, 5}}}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return 1.0 + 1;"));
  }

  @Test
  void evalCompileErrorReturnsParseDiagnosticThroughStoredCaller() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    List<String> output =
        runtime.executeLine(
            connectionId,
            "; x = {}; for in ({\"1\", \"2\", \"3\", \"4\", \"5\"}) endfor return x;");

    assertTrue(output.stream().anyMatch(line -> line.contains("Parse error")), output::toString);
  }

  @Test
  void evalInvalidContinueLoopNameReturnsToastDiagnosticThroughStoredCaller() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    List<String> output =
        runtime.executeLine(
            connectionId,
            "; x = {}; for i in ({\"1\", \"2\", \"3\", \"4\", \"5\"}) continue x; endfor return x;");

    assertTrue(output.stream().anyMatch(line -> line.contains("Invalid loop name")), output::toString);
  }

  @Test
  void executesEqualityCollectionsThroughStoredEvalRuntime() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 0, {1, 0}}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; return {\"10\" == \"1\" + \"0\", [] == {}, {\"A\" == \"a\", \"À\" == \"à\"}};"));
  }

  @Test
  void anonymousRecycleHookFinishesBeforeZeroDelayTaskResumes() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long anonymousClass = world.snapshot().objects().size();

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 0}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; class = create(#-1);
            add_property(class, "next", 0, {player, ""});
            add_property(class, "subject", 0, {player, ""});
            add_property(class, "stash", 0, {player, ""});
            add_property(class, "recycle_called", 0, {player, ""});
            add_verb(class, {player, "xd", "recycle"}, {"this", "none", "this"});
            set_verb_code(class, "recycle", {
              tostr(class) + ".recycle_called = " + tostr(class) + ".recycle_called + 1;",
              tostr(class) + ".stash = create(" + tostr(class) + ", 1);"
            });
            orphan = create(class, 1);
            orphan.next = orphan;
            class.subject = orphan;
            return 0;
            """));
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 0}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; #" + anonymousClass + ".subject = 0; run_gc(); return 0;"));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, {1, 1}}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; suspend(0); return {#"
                + anonymousClass
                + ".recycle_called, valid(#"
                + anonymousClass
                + ".stash)};"));
  }

  @Test
  void locallyDefinedAnonymousPropertyDoesNotJoinTheSameRecycleWave() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long anonymousClass = world.snapshot().objects().size();

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, #" + anonymousClass + "}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; class = create(#-1);
            add_property(class, "recycle_called", 0, {player, ""});
            add_verb(class, {player, "xd", "recycle"}, {"this", "none", "this"});
            set_verb_code(class, "recycle", {
              tostr(class) + ".recycle_called = " + tostr(class) + ".recycle_called + 1;"
            });
            add_verb(class, {player, "xd", "go"}, {"this", "none", "this"});
            set_verb_code(class, "go", {
              "x = create(" + tostr(class) + ", 1);",
              "add_property(x, \\\"next\\\", 0, {player, \\\"\\\"});",
              "x.next = create(" + tostr(class) + ", 1);",
              "args || recycle(x);"
            });
            return class;
            """));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 0}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; #" + anonymousClass + ".recycle_called = 0; return #" + anonymousClass + ":go();"));
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return #" + anonymousClass + ".recycle_called;"));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 0}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; #" + anonymousClass + ".recycle_called = 0; return #" + anonymousClass + ":go(1);"));
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return #" + anonymousClass + ".recycle_called;"));
  }

  private static Optional<WorldObject> readObject(WorldTxn root, long objectId) {
    try (WorldTxn transaction = root.begin()) {
      return transaction.object(objectId);
    }
  }

  private static Optional<WorldVerb> readVerb(WorldTxn root, long objectId, int verbIndex) {
    try (WorldTxn transaction = root.begin()) {
      return transaction.verb(objectId, verbIndex);
    }
  }
}
