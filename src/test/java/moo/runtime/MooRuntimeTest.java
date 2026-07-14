package moo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import moo.persistence.LambdaMooV4Reader;
import moo.value.MooValue.ObjectValue;
import moo.world.WorldObject;
import moo.world.WorldTxn;
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

    assertEquals(List.of(), runtime.openConnection(-2));
    runtime.closeConnection(-2);

    long connectionId = -47;
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    WorldObject wizard = world.object(8).orElseThrow();
    assertEquals(8, wizard.owner());
    assertEquals(7, wizard.flags());
    assertEquals(2, wizard.location());
    assertEquals(List.of(3L, 4L, 8L), world.object(2).orElseThrow().contents());
    assertEquals(List.of(3L, 4L, 8L), world.players());

    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    executeSetup(runtime, connectionId, "object", "#1");
    executeSetup(runtime, connectionId, "anonymous", "#5");
    executeSetup(runtime, connectionId, "anon", "#5");
    executeSetup(runtime, connectionId, "sysobj", "#0");
    executeSetup(runtime, connectionId, "nothing", "#-1");

    assertEquals(new ObjectValue(5), world.property(0, "anon").orElseThrow().value());
    assertEquals(new ObjectValue(0), world.property(0, "sysobj").orElseThrow().value());
    assertEquals(5, world.property(0, "anon").orElseThrow().permissions());
    assertEquals(5, world.property(0, "sysobj").orElseThrow().permissions());
    assertEquals(8, world.object(0).orElseThrow().properties().size());

    assertEquals(
        List.of(
            CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 2}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return 1 + 1;"));
  }

  @Test
  void tokenizesBackslashEscapesForAStoredPlayerCommandVerb() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; add_verb(player, {player, "xd", "audit_words"}, {"any", "none", "none"});
            set_verb_code(player, "audit_words", {
              "notify(player, \\"LEN:\\" + tostr(length(args)));",
              "notify(player, \\"ARG1:\\" + args[1]);",
              "notify(player, \\"ARG2:\\" + args[2]);"
            });
            return 1;
            """);
    long player = world.connectionPlayer(connectionId).orElseThrow();
    assertTrue(
        world.verb(player, "audit_words").isPresent(),
        () -> setupOutput + " player=" + player + " object=" + world.object(player));
    assertTrue(
        !world.verb(player, "audit_words").orElseThrow().programSource().isEmpty(),
        setupOutput::toString);

    try {
      assertEquals(
          List.of("LEN:2", "ARG1:foo bar", "ARG2:baz"),
          runtime.executeLine(connectionId, "audit_words foo\\ bar baz"));
    } finally {
      runtime.executeLine(connectionId, "; return delete_verb(player, \"audit_words\");");
    }
  }

  @Test
  void tokenizesMidwordQuotesForAStoredPlayerCommandVerb() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; add_verb(player, {player, "xd", "audit_words"}, {"any", "none", "none"});
            set_verb_code(player, "audit_words", {
              "notify(player, \\"LEN:\\" + tostr(length(args)));",
              "notify(player, \\"ARG1:\\" + args[1]);",
              "notify(player, \\"ARG2:\\" + args[2]);"
            });
            return 1;
            """);
    long player = world.connectionPlayer(connectionId).orElseThrow();
    assertTrue(world.verb(player, "audit_words").isPresent(), setupOutput::toString);

    try {
      assertEquals(
          List.of("LEN:2", "ARG1:abc def", "ARG2:zz"),
          runtime.executeLine(connectionId, "audit_words ab\"c d\"ef zz"));
    } finally {
      runtime.executeLine(connectionId, "; return delete_verb(player, \"audit_words\");");
    }
  }

  @Test
  void passesTheFullWordListToTruthyDoCommandBeforeNormalDispatch() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; try
              add_verb(#0, {player, "rxd", "do_command"}, {"this", "none", "this"});
            except (E_INVARG)
            endtry
            set_verb_code(#0, "do_command", {
              "if (args[1] == \\";\\")",
              "  return 0;",
              "endif",
              "notify(player, \\"DO_COMMAND:\\" + tostr(length(args)) + \\":\\" + args[2]);",
              "return 1;"
            });
              add_verb(player, {player, "xd", "audit_words"}, {"any", "none", "none"});
              set_verb_code(player, "audit_words", {"notify(player, \\"NORMAL_DISPATCH\\");"});
              return 1;
            """);
    assertTrue(world.verb(0, "do_command").isPresent(), setupOutput::toString);

    try {
      assertEquals(List.of(), runtime.executeLine(connectionId, "pReFiX command-prefix"));
      assertEquals(List.of(), runtime.executeLine(connectionId, "sUfFiX command-suffix"));
      assertEquals(
          List.of("command-prefix", "DO_COMMAND:2:foo bar", "command-suffix"),
          runtime.executeLine(connectionId, "audit_words foo\\ bar"));
    } finally {
      runtime.executeLine(
          connectionId,
          "; delete_verb(#0, \"do_command\"); delete_verb(player, \"audit_words\"); return 1;");
    }
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
            "{2, {E_TYPE}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return 1.0 + 1;"));
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
  void executesTheFrozenQueuedTasksSurfaceThroughStoredEvalRuntime() throws Exception {
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
            "{1, {{{\"function_info\", 0, 1, {2}}, "
                + "{\"queued_tasks\", 0, 2, {0, 0}}}, "
                + "{\"function_info\", 0, 1, {2}}, "
                + "{\"queued_tasks\", 0, 2, {0, 0}}, {}, {}, {}, 0}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; return {function_info(), function_info(\"function_info\"), "
                + "function_info(\"queued_tasks\"), queued_tasks(), queued_tasks(1), "
                + "queued_tasks(0, 0), queued_tasks(0, 1)};"));
  }

  @Test
  void executesSqliteExistsInRuntimeThroughStoredEvalRuntime() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; try sqlite_open(1,2,3,4,5,6,7,8,9); return 1; except (E_VERBNF) return 0; except (ANY) return 1; endtry;"));
  }

  @Test
  void executesSqliteManagedSurfaceThroughStoredEvalRuntime() throws Exception {
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
            "{1, {1, 1, 1, 1, 1, 1, 1}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; h = sqlite_open(\":memory:\"); info = sqlite_info(h); "
                + "return {h > 0, h in sqlite_handles(), info[\"path\"] == \":memory:\", "
                + "info[\"parse_types\"] == 1, info[\"parse_objects\"] == 1, "
                + "info[\"sanitize_strings\"] == 0, info[\"locks\"] == 0};"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 1, 1}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; sqlite_query(1, \"CREATE TABLE t(id INTEGER PRIMARY KEY, n INTEGER, "
                + "x REAL, obj TEXT, label TEXT)\"); "
                + "inserted = sqlite_execute(1, \"INSERT INTO t(n, x, obj, label) "
                + "VALUES (?, ?, ?, ?)\", {42, 3.5, #0, \"alpha\"}); "
                + "return {typeof(inserted) == LIST, length(inserted) == 0, "
                + "sqlite_last_insert_row_id(1) == 1};"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {{42, 3.5, #0, \"alpha\"}}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; return sqlite_execute(1, \"SELECT ?, ?, ?, ?\", {42, 3.5, #0, \"alpha\"});"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {{{\"first\", 42}, {\"label\", \"alpha\"}}}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId, "; return sqlite_query(1, \"SELECT n AS first, label FROM t\", 1);"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 1, 1, 1, 1}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; name_limit = sqlite_limit(1, \"LIMIT_COLUMN\", -1); "
                + "number_limit = sqlite_limit(1, 2, -1); "
                + "previous = sqlite_limit(1, 2, name_limit - 1); "
                + "current = sqlite_limit(1, \"LIMIT_COLUMN\", -1); "
                + "restored = sqlite_limit(1, \"LIMIT_COLUMN\", name_limit); "
                + "final = sqlite_limit(1, 2, -1); "
                + "return {name_limit == number_limit, previous == name_limit, "
                + "current == name_limit - 1, restored == name_limit - 1, "
                + "final == name_limit};"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 1, 1, 1, 1, 1, 1}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; query_returned = sqlite_query(999999, \"SELECT 1\") == E_INVARG; "
                + "execute_returned = sqlite_execute(999999, \"SELECT 1\", {}) == E_INVARG; "
                + "close_raised = `sqlite_close(999999) ! E_INVARG => 1'; "
                + "info_raised = `sqlite_info(999999) ! E_INVARG => 1'; "
                + "limit_raised = `sqlite_limit(1, \"LIMIT_NOT_REAL\", -1) "
                + "! E_INVARG => 1'; "
                + "call_raised = `call_function(\"sqlite_info\", 2) ! E_INVARG => 1'; "
                + "interrupt_raised = `sqlite_interrupt(999999) ! E_INVARG => 1'; "
                + "return {query_returned, execute_returned, close_raised, info_raised, "
                + "limit_raised, call_raised, interrupt_raised};"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {0, 0}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; result = sqlite_close(1); return {result, length(sqlite_handles())};"));
  }

  @Test
  void interruptsActiveSqliteQueryAfterExactDelayedForkAndSuspend() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    List<String> output =
        runtime.executeLine(
            connectionId,
            """
            ; h = sqlite_open(":memory:");
            fork (1)
              suspend(1);
              sqlite_interrupt(h);
            endfork
            result = sqlite_query(h, "WITH RECURSIVE cnt(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM cnt WHERE x < 1000) SELECT count(*) FROM cnt AS a, cnt AS b, cnt AS c, cnt AS d;");
            sqlite_close(h);
            return result;
            """);

    assertTrue(
        output.stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains("interrupt")),
        output::toString);
  }

  private static void executeSetup(
      MooRuntime runtime, long connectionId, String name, String value) {
    String source =
        "; try add_property(#0, \""
            + name
            + "\", "
            + value
            + ", {#0, \"rc\"}); except (ANY) return 0; endtry";
    assertEquals(
        List.of(
            CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 0}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, source));
  }
}
