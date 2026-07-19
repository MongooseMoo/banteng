package moo.builtin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
          "decode_binary",
          "dump_database",
          "encode_binary",
          "equal",
          "eval",
          "function_info",
          "index",
          "length",
          "listappend",
          "listdelete",
          "listinsert",
          "listset",
          "move",
          "notify",
          "rindex",
          "set_player_flag",
          "set_task_perms",
          "setadd",
          "strcmp",
          "strsub",
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
  void exposesTheExactPureStringContracts() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    Set<ArgType> any = Set.of(ArgType.ANY);
    Set<ArgType> integer = Set.of(ArgType.INTEGER);
    Set<ArgType> string = Set.of(ArgType.STRING);

    assertPureVmContract(
        catalog,
        "strsub",
        new CallShape(List.of(string, string, string), List.of(any), Optional.empty()));
    for (String name : List.of("index", "rindex")) {
      assertPureVmContract(
          catalog,
          name,
          new CallShape(List.of(string, string), List.of(any, integer), Optional.empty()));
    }
    assertPureVmContract(
        catalog,
        "strcmp",
        new CallShape(List.of(string, string), List.of(), Optional.empty()));
    assertPureVmContract(
        catalog,
        "decode_binary",
        new CallShape(List.of(string), List.of(any), Optional.empty()));
    assertPureVmContract(
        catalog,
        "encode_binary",
        new CallShape(List.of(), List.of(), Optional.of(any)));
  }

  @Test
  void stringBuiltinsPreserveToastSearchSubstitutionAndComparisonSemantics() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    try (WorldTxn transaction = world().begin()) {
      assertString(
          "bazBarbaz",
          invoke(
              catalog,
              catalog.spec("strsub").orElseThrow(),
              List.of(string("FooBarFoo"), string("foo"), string("baz")),
              transaction,
              1));
      assertString(
          "FooBarbaz",
          invoke(
              catalog,
              catalog.spec("strsub").orElseThrow(),
              List.of(
                  string("FooBarfoo"),
                  string("foo"),
                  string("baz"),
                  new IntegerValue(1)),
              transaction,
              1));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  catalog.spec("strsub").orElseThrow(),
                  List.of(string("foo"), string(""), string("x")),
                  transaction,
                  1)
              .error());

      assertEquals(
          Optional.of(new IntegerValue(4)),
          invoke(
                  catalog,
                  catalog.spec("index").orElseThrow(),
                  List.of(string("fooBar"), string("bar")),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(new IntegerValue(0)),
          invoke(
                  catalog,
                  catalog.spec("index").orElseThrow(),
                  List.of(string("fooBar"), string("bar"), new IntegerValue(1)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(new IntegerValue(7)),
          invoke(
                  catalog,
                  catalog.spec("rindex").orElseThrow(),
                  List.of(string("bazbarBazfoo"), string("baz")),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(new IntegerValue(1)),
          invoke(
                  catalog,
                  catalog.spec("rindex").orElseThrow(),
                  List.of(string("bazbarBazfoo"), string("baz"), new IntegerValue(1)),
                  transaction,
                  1)
              .value());

      assertEquals(
          Optional.of(new IntegerValue(1)),
          invoke(
                  catalog,
                  catalog.spec("strcmp").orElseThrow(),
                  List.of(string("abc"), string("ABC")),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(new IntegerValue(-1)),
          invoke(
                  catalog,
                  catalog.spec("strcmp").orElseThrow(),
                  List.of(string("abc"), string("abcd")),
                  transaction,
                  1)
              .value());
    }
  }

  @Test
  void binaryBuiltinsPreserveToastByteGroupingEscapesAndErrors() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    try (WorldTxn transaction = world().begin()) {
      Result decoded =
          invoke(
              catalog,
              catalog.spec("decode_binary").orElseThrow(),
              List.of(string("foo~0D~0A")),
              transaction,
              1);
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(string("foo"), new IntegerValue(13), new IntegerValue(10)))),
          decoded.value());
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      new IntegerValue(102),
                      new IntegerValue(111),
                      new IntegerValue(111),
                      new IntegerValue(13),
                      new IntegerValue(10)))),
          invoke(
                  catalog,
                  catalog.spec("decode_binary").orElseThrow(),
                  List.of(string("foo~0D~0A"), new IntegerValue(1)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  catalog.spec("decode_binary").orElseThrow(),
                  List.of(string("~ZZ")),
                  transaction,
                  1)
              .error());

      assertString(
          "foo~0Abar~0D",
          invoke(
              catalog,
              catalog.spec("encode_binary").orElseThrow(),
              List.of(
                  new ListValue(List.of(string("foo"), new IntegerValue(10))),
                  new ListValue(List.of(string("bar"), new IntegerValue(13)))),
              transaction,
              1));
      assertString(
          "",
          invoke(
              catalog,
              catalog.spec("encode_binary").orElseThrow(),
              List.of(),
              transaction,
              1));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  catalog.spec("encode_binary").orElseThrow(),
                  List.of(new IntegerValue(256)),
                  transaction,
                  1)
              .error());
    }
  }

  @Test
  void exposesTheExactPureListContracts() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    Set<ArgType> any = Set.of(ArgType.ANY);
    Set<ArgType> integer = Set.of(ArgType.INTEGER);
    Set<ArgType> list = Set.of(ArgType.LIST);

    for (String name : List.of("listappend", "listinsert")) {
      assertPureVmContract(
          catalog,
          name,
          new CallShape(List.of(list, any), List.of(integer), Optional.empty()));
    }
    assertPureVmContract(
        catalog,
        "listdelete",
        new CallShape(List.of(list, integer), List.of(), Optional.empty()));
    assertPureVmContract(
        catalog,
        "listset",
        new CallShape(List.of(list, any, integer), List.of(), Optional.empty()));
    assertPureVmContract(
        catalog,
        "setadd",
        new CallShape(List.of(list, any), List.of(), Optional.empty()));
  }

  @Test
  void listBuiltinsPreserveToastInsertionMutationSetAndRangeSemantics() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    ListValue oneTwo = new ListValue(List.of(new IntegerValue(1), new IntegerValue(2)));
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(new IntegerValue(1), new IntegerValue(2), new IntegerValue(3)))),
          invoke(
                  catalog,
                  catalog.spec("listappend").orElseThrow(),
                  List.of(oneTwo, new IntegerValue(3)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(new IntegerValue(1), new IntegerValue(3), new IntegerValue(2)))),
          invoke(
                  catalog,
                  catalog.spec("listappend").orElseThrow(),
                  List.of(oneTwo, new IntegerValue(3), new IntegerValue(1)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(new IntegerValue(3), new IntegerValue(1), new IntegerValue(2)))),
          invoke(
                  catalog,
                  catalog.spec("listinsert").orElseThrow(),
                  List.of(oneTwo, new IntegerValue(3), new IntegerValue(1)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(new ListValue(List.of(new IntegerValue(1), new IntegerValue(3)))),
          invoke(
                  catalog,
                  catalog.spec("listdelete").orElseThrow(),
                  List.of(
                      new ListValue(
                          List.of(
                              new IntegerValue(1),
                              new IntegerValue(2),
                              new IntegerValue(3))),
                      new IntegerValue(2)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(new IntegerValue(1), new IntegerValue(4), new IntegerValue(3)))),
          invoke(
                  catalog,
                  catalog.spec("listset").orElseThrow(),
                  List.of(
                      new ListValue(
                          List.of(
                              new IntegerValue(1),
                              new IntegerValue(2),
                              new IntegerValue(3))),
                      new IntegerValue(4),
                      new IntegerValue(2)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(new IntegerValue(1), new IntegerValue(2), new IntegerValue(3)))),
          invoke(
                  catalog,
                  catalog.spec("setadd").orElseThrow(),
                  List.of(oneTwo, new IntegerValue(3)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(oneTwo),
          invoke(
                  catalog,
                  catalog.spec("setadd").orElseThrow(),
                  List.of(oneTwo, new IntegerValue(2)),
                  transaction,
                  1)
              .value());

      for (String name : List.of("listdelete", "listset")) {
        List<MooValue> arguments =
            name.equals("listdelete")
                ? List.of(oneTwo, new IntegerValue(0))
                : List.of(oneTwo, new IntegerValue(9), new IntegerValue(0));
        assertEquals(
            Optional.of(ErrorValue.E_RANGE),
            invoke(
                    catalog,
                    catalog.spec(name).orElseThrow(),
                    arguments,
                    transaction,
                    1)
                .error(),
            name);
      }
    }
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

  private static StringValue string(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  private static void assertString(String expected, Result actual) {
    StringValue value = (StringValue) actual.value().orElseThrow();
    assertArrayEquals(expected.getBytes(StandardCharsets.ISO_8859_1), value.bytes());
  }

  private static WorldTxn world() {
    WorldObject wizard =
        new WorldObject(1, "Wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject programmer =
        new WorldObject(2, "Programmer", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    return new WorldTxn(List.of(), List.of(wizard, programmer));
  }
}
