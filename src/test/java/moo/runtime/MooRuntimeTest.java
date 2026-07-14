package moo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
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
