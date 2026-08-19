package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import world.mongoose.banteng.persistence.LambdaMooV4Reader;
import world.mongoose.banteng.server.ConnectionRegistry;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.world.ObjectFlags;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;
import org.junit.jupiter.api.Test;

final class MooRuntimeTest {
  private static final Path FIXTURE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");
  private static final String CONNECTION_PREFIX = "-=!-^-!=-";
  private static final String CONNECTION_SUFFIX = "-=!-v-!=-";

  @Test
  void publishesConnectionIdentityIntoTheInjectedServerRegistry() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    ConnectionRegistry connections = new ConnectionRegistry();
    MooRuntime runtime = new MooRuntime(world, connections);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    assertEquals(8, connections.connectionPlayer(connectionId).orElseThrow());
    assertTrue(connections.connectionInfo(connectionId).isPresent());
  }

  @Test
  void runtimeTransportStateDoesNotMirrorServerRegistryFields() {
    List<String> fieldNames =
        Arrays.stream(MooRuntime.ConnectionState.class.getDeclaredFields())
            .map(field -> field.getName())
            .toList();

    assertTrue(
        Collections.disjoint(
            fieldNames, List.of("connectionInfo", "player", "intrinsicCommands")),
        fieldNames.toString());
  }

  @Test
  void executesTheFirstManagedRowThroughStoredVerbsAndOneWorldTxn() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = runtimeFor(world);
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
    MooRuntime runtime = runtimeFor(world);
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
    MooRuntime runtime = runtimeFor(world);
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
    MooRuntime runtime = runtimeFor(world);
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
    MooRuntime runtime = runtimeFor(world);
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
    MooRuntime runtime = runtimeFor(world);
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
    MooRuntime runtime = runtimeFor(world);
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

      assertEquals(
          0, readObject(world, object).orElseThrow().flags() & ObjectFlags.FLAG_FERTILE);
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
    MooRuntime runtime = runtimeFor(world);
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
          transaction
              .setVerbCode(player, verbIndex, "notify(player, \"EXECUTED\");")
              .isOk());
      assertTrue(transaction.commit().isCommitted());
    }
    assertEquals(8, readVerb(world, player, verbIndex).orElseThrow().permissions());

    assertEquals(List.of("EXECUTED"), runtime.executeLine(connectionId, "auditnoexec"));
  }

  @Test
  void dispatchesUnknownCommandsThroughSelectedHuhVerb() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = runtimeFor(world);
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
    MooRuntime runtime = runtimeFor(world);
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
    MooRuntime runtime = runtimeFor(world);
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
    MooRuntime runtime = runtimeFor(world);
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
    MooRuntime runtime = runtimeFor(world);
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
    MooRuntime runtime = runtimeFor(world);
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
    MooRuntime runtime = runtimeFor(world);
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

  @Test
  void dotProgramReportsToastFeedbackAndOnlyInstallsValidSource() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = runtimeFor(world);
    long connectionId = -47;
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    assertEquals(
        List.of("Usage:  .program object:verb"),
        runtime.executeLine(connectionId, ".program"));
    assertEquals(
        List.of("I don't see \"#999999\" here."),
        runtime.executeLine(connectionId, ".program #999999:missing"));
    assertEquals(
        List.of("That object does not have that verb definition."),
        runtime.executeLine(connectionId, ".program #0:does_not_exist"));
    assertEquals(
        List.of("I don't see \"#not-a-number\" here."),
        runtime.executeLine(connectionId, ".program #not-a-number:missing"));
    assertEquals(
        List.of("That object does not have that verb definition."),
        runtime.executeLine(connectionId, ".program me:does_not_exist"));
    assertEquals(
        List.of("That object does not have that verb definition."),
        runtime.executeLine(connectionId, ".program $server_options:does_not_exist"));
    assertEquals(
        List.of("Now programming The First Room:eval.  Use \".\" to end."),
        runtime.executeLine(connectionId, ".program here:eval"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "return ("));
    assertEquals(
        List.of("Line 2:  syntax error", "1 error(s).", "Verb not programmed."),
        runtime.executeLine(connectionId, "."));

    String originalSource = readVerb(world, 0, "do_login_command").orElseThrow().programSource();
    assertEquals(
        List.of("Now programming System Object:do_login_command.  Use \".\" to end."),
        runtime.executeLine(connectionId, ".program #0:do_login_command"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "return ^;"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "return ^;"));
    assertEquals(
        List.of(
            "Line 1:  Illegal context for `^' expression.",
            "Line 2:  Illegal context for `^' expression.",
            "2 error(s).",
            "Verb not programmed."),
        runtime.executeLine(connectionId, "."));
    assertEquals(
        originalSource, readVerb(world, 0, "do_login_command").orElseThrow().programSource());

    assertEquals(
        List.of("Now programming System Object:do_login_command.  Use \".\" to end."),
        runtime.executeLine(connectionId, ".program #0:do_login_command"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "return 0;"));
    assertEquals(
        List.of("0 error(s).", "Verb programmed."), runtime.executeLine(connectionId, "."));
    assertEquals(
        "return 0;\n", readVerb(world, 0, "do_login_command").orElseThrow().programSource());
  }

  @Test
  void dotProgramUsesRequestedObjectForDisplayAndDefiningObjectForStorage() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = runtimeFor(world);
    long connectionId = -47;
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    WorldObject requested = readObject(world, player).orElseThrow();
    long definingObject;

    try (WorldTxn transaction = world.begin()) {
      WorldObject parent = transaction.createObject(requested.parents(), player);
      definingObject = parent.id();
      assertTrue(transaction.changeParents(player, List.of(definingObject)).isOk());
      assertTrue(transaction.addVerb(definingObject, "inherited_program", player, 6, -1) > 0);
      assertTrue(transaction.commit().isCommitted());
    }

    assertEquals(
        List.of("Now programming " + requested.name() + ":inherited_program.  Use \".\" to end."),
        runtime.executeLine(connectionId, ".program me:inherited_program"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "return 17;"));
    assertEquals(
        List.of("0 error(s).", "Verb programmed."), runtime.executeLine(connectionId, "."));
    assertEquals(
        "return 17;\n",
        readVerb(world, definingObject, "inherited_program").orElseThrow().programSource());
    assertTrue(
        readObject(world, player).orElseThrow().verbs().stream()
            .noneMatch(verb -> verb.names().equals("inherited_program")));
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 17}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return player:inherited_program();"));
  }

  @Test
  void dotProgramRejectsEmptyObjectsAndVerbsWithoutWritePermission() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = runtimeFor(world);
    long connectionId = -47;
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Programmer"));

    assertEquals(
        List.of("\"\" is not a valid object."),
        runtime.executeLine(connectionId, ".program :do_login_command"));
    try (WorldTxn transaction = world.begin()) {
      assertTrue(transaction.addVerb(0, "locked_program", 0, 0, -1) > 0);
      assertTrue(transaction.commit().isCommitted());
    }
    assertEquals(
        List.of("Permission denied."),
        runtime.executeLine(connectionId, ".program #0:locked_program"));
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 2}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return 1 + 1;"));
  }

  @Test
  void dotProgramFallsThroughAsAnUnknownCommandWhenActorIsNotAProgrammer() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = runtimeFor(world);
    long connectionId = -47;
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();

    try (WorldTxn transaction = world.begin()) {
      assertTrue(
          transaction.writeObjectProperty(player, "programmer", new IntegerValue(0)).isOk());
      assertTrue(transaction.commit().isCommitted());
    }
    int actorFlags = readObject(world, player).orElseThrow().flags();
    assertEquals(0, actorFlags & ObjectFlags.FLAG_PROGRAMMER);
    assertEquals(ObjectFlags.FLAG_WIZARD, actorFlags & ObjectFlags.FLAG_WIZARD);

    assertEquals(
        List.of("I couldn't understand that."),
        runtime.executeLine(connectionId, ".program #0:do_login_command"));
    assertEquals(
        List.of("I couldn't understand that."),
        runtime.executeLine(connectionId, "return 5;"));
  }

  @Test
  void dotProgramReresolvesReorderedVerbAndReportsDisappearanceBeforeCompilation()
      throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = runtimeFor(world);
    long connectionId = -47;
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    WorldObject requested = readObject(world, player).orElseThrow();
    int anchorIndex;
    int targetIndex;

    try (WorldTxn transaction = world.begin()) {
      anchorIndex = transaction.addVerb(player, "program_anchor", player, 2, -1) - 1;
      targetIndex = transaction.addVerb(player, "program_reordered", player, 6, -1) - 1;
      assertTrue(anchorIndex >= 0);
      assertTrue(targetIndex > anchorIndex);
      assertTrue(transaction.commit().isCommitted());
    }
    assertEquals(
        List.of(
            "Now programming " + requested.name() + ":program_reordered.  Use \".\" to end."),
        runtime.executeLine(connectionId, ".program me:program_reordered"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "return 23;"));
    try (WorldTxn transaction = world.begin()) {
      assertTrue(transaction.deleteVerb(player, anchorIndex).isOk());
      assertTrue(transaction.commit().isCommitted());
    }
    assertEquals(
        List.of("0 error(s).", "Verb programmed."), runtime.executeLine(connectionId, "."));
    assertEquals(
        "return 23;\n",
        readVerb(world, player, "program_reordered").orElseThrow().programSource());

    assertEquals(
        List.of(
            "Now programming " + requested.name() + ":program_reordered.  Use \".\" to end."),
        runtime.executeLine(connectionId, ".program me:program_reordered"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "return \"unterminated;"));
    try (WorldTxn transaction = world.begin()) {
      WorldObject object = transaction.object(player).orElseThrow();
      int currentIndex = object.verbs().indexOf(transaction.verb(player, "program_reordered", false).orElseThrow());
      assertTrue(transaction.deleteVerb(player, currentIndex).isOk());
      assertTrue(transaction.commit().isCommitted());
    }
    assertEquals(
        List.of("That verb appears to have disappeared ..."),
        runtime.executeLine(connectionId, "."));
  }

  @Test
  void dotProgramReportsDefiningObjectDisappearanceBeforeCompilation() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = runtimeFor(world);
    long connectionId = -47;
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    long objectId;

    try (WorldTxn transaction = world.begin()) {
      WorldObject object = transaction.createObject(-1, player);
      objectId = object.id();
      assertTrue(transaction.addVerb(objectId, "vanishing_program", player, 2, -1) > 0);
      assertTrue(transaction.commit().isCommitted());
    }
    assertTrue(
        runtime.executeLine(connectionId, ".program #" + objectId + ":vanishing_program")
            .getFirst()
            .endsWith(":vanishing_program.  Use \".\" to end."));
    assertEquals(List.of(), runtime.executeLine(connectionId, "return \"unterminated;"));
    try (WorldTxn transaction = world.begin()) {
      assertTrue(transaction.recycleObject(objectId).isOk());
      assertTrue(transaction.commit().isCommitted());
    }
    assertEquals(
        List.of("That object appears to have disappeared ..."),
        runtime.executeLine(connectionId, "."));
  }

  private static MooRuntime runtimeFor(WorldTxn world) {
    return new MooRuntime(world, new ConnectionRegistry());
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

  private static Optional<WorldVerb> readVerb(WorldTxn root, long objectId, String verbName) {
    try (WorldTxn transaction = root.begin()) {
      return transaction.verb(objectId, verbName);
    }
  }
}
