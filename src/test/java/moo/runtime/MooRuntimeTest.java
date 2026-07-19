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
            "{2, {E_TYPE, \"\"}}",
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
