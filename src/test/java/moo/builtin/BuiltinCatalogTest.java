package moo.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

final class BuiltinCatalogTest {
  private static final Set<String> REACHABLE_NAMES =
      Set.of(
          "create",
          "dump_database",
          "equal",
          "eval",
          "function_info",
          "length",
          "move",
          "notify",
          "set_player_flag",
          "set_task_perms",
          "switch_player",
          "tofloat",
          "toint",
          "toliteral",
          "toobj",
          "tostr",
          "typeof");

  @Test
  void registersEveryReachableBuiltinExactlyOnceWithCompleteContracts() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    List<BuiltinSpec> manifest = catalog.manifest();

    assertEquals(REACHABLE_NAMES.size(), manifest.size());
    assertEquals(
        REACHABLE_NAMES,
        manifest.stream().map(BuiltinSpec::name).collect(java.util.stream.Collectors.toSet()));
    assertEquals(
        manifest.size(),
        new LinkedHashSet<>(manifest.stream().map(BuiltinSpec::name).toList()).size());
    for (BuiltinSpec spec : manifest) {
      assertTrue(!spec.callShapes().isEmpty(), spec.name());
      assertTrue(spec.tickCost().charge(List.of()) >= 0, spec.name());
      assertSame(spec, catalog.spec(spec.name().toUpperCase(java.util.Locale.ROOT)).orElseThrow());
    }
  }

  @Test
  void exposesTheExactDeferredDumpDatabaseContract() {
    BuiltinSpec spec = new BuiltinCatalog().spec("dump_database").orElseThrow();

    assertEquals(
        List.of(new CallShape(List.of(), List.of(), Optional.empty())), spec.callShapes());
    assertSame(BuiltinPermissionRule.WIZARD_ONLY, spec.permission());
    assertEquals(0, spec.tickCost().charge(List.of()));
    assertEquals(EffectClass.DEFERRED_COMMIT, spec.effect());
    assertEquals(BuiltinOwner.SERVER, spec.owner());
  }

  @Test
  void exposesTheExactPureValueConversionContracts() {
    BuiltinCatalog catalog = new BuiltinCatalog();

    assertPureVmContract(
        catalog,
        "tostr",
        new CallShape(List.of(), List.of(), Optional.of(Set.of(ArgType.ANY))));
    for (String name : List.of("tofloat", "toint", "toobj")) {
      assertPureVmContract(
          catalog,
          name,
          new CallShape(List.of(Set.of(ArgType.ANY)), List.of(), Optional.empty()));
    }
    assertPureVmContract(
        catalog,
        "equal",
        new CallShape(
            List.of(Set.of(ArgType.ANY), Set.of(ArgType.ANY)),
            List.of(),
            Optional.empty()));
  }

  @Test
  void functionInfoDescribesDumpDatabaseFromTheManifest() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    try (WorldTxn transaction = world().begin()) {
      Result result =
          invoke(
              catalog,
              catalog.spec("function_info").orElseThrow(),
              List.of(
                  new StringValue(
                      "dump_database".getBytes(StandardCharsets.ISO_8859_1))),
              transaction,
              1);

      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      new StringValue(
                          "dump_database".getBytes(StandardCharsets.ISO_8859_1)),
                      new IntegerValue(0),
                      new IntegerValue(0),
                      new ListValue(List.of())))),
          result.value());
    }
  }

  @Test
  void dumpDatabaseReturnsZeroAndAValueOnlyCheckpointRequestForWizards() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    try (WorldTxn transaction = world().begin()) {
      Result result =
          invoke(
              catalog,
              catalog.spec("dump_database").orElseThrow(),
              List.of(),
              transaction,
              1);

      assertEquals(Optional.of(new IntegerValue(0)), result.value());
      assertEquals(Optional.of(new CheckpointRequest()), result.checkpointRequest());
      assertTrue(result.error().isEmpty());
    }
  }

  @Test
  void dumpDatabaseRejectsArgumentsAndNonWizardProgrammersBeforeRequestingCheckpoint() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("dump_database").orElseThrow();
    try (WorldTxn transaction = world().begin()) {
      Result arguments = invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 1);
      Result permission = invoke(catalog, spec, List.of(), transaction, 2);

      assertEquals(Optional.of(ErrorValue.E_ARGS), arguments.error());
      assertTrue(arguments.checkpointRequest().isEmpty());
      assertEquals(Optional.of(ErrorValue.E_PERM), permission.error());
      assertTrue(permission.checkpointRequest().isEmpty());
    }
  }

  private static Result invoke(
      BuiltinCatalog catalog,
      BuiltinSpec spec,
      List<MooValue> arguments,
      WorldTxn world,
      long programmer) {
    return catalog.invoke(
        spec,
        arguments,
        world,
        programmer,
        new MapValue(Map.of()),
        60_000,
        5,
        new ObjectValue(programmer),
        programmer,
        new ListValue(List.of()));
  }

  private static void assertPureVmContract(
      BuiltinCatalog catalog, String name, CallShape shape) {
    BuiltinSpec spec = catalog.spec(name).orElseThrow();
    assertEquals(List.of(shape), spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(0, spec.tickCost().charge(List.of()));
    assertEquals(EffectClass.PURE, spec.effect());
    assertEquals(BuiltinOwner.VM, spec.owner());
  }

  private static WorldTxn world() {
    WorldObject wizard =
        new WorldObject(1, "Wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject programmer =
        new WorldObject(2, "Programmer", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    return new WorldTxn(List.of(), List.of(wizard, programmer));
  }
}
