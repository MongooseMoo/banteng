package moo.builtin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import moo.builtin.BuiltinCatalog.Result;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.world.WorldObject;
import moo.world.WorldProperty;
import moo.world.WorldTxn;
import moo.world.WorldVerb;
import org.junit.jupiter.api.Test;

final class BuiltinCatalogTest {
  private static final Set<String> REACHABLE_NAMES =
      Set.of(
          "add_property",
          "add_verb",
          "caller_perms",
          "chr",
          "create",
          "clear_property",
          "decode_binary",
          "delete_property",
          "disassemble",
          "dump_database",
          "encode_binary",
          "equal",
          "eval",
          "function_info",
          "index",
          "is_player",
          "is_clear_property",
          "length",
          "listappend",
          "listdelete",
          "listinsert",
          "listset",
          "mapkeys",
          "max",
          "move",
          "new_waif",
          "notify",
          "parent",
          "properties",
          "property_info",
          "queued_tasks",
          "random",
          "raise",
          "recycle",
          "rindex",
          "run_gc",
          "set_player_flag",
          "set_task_perms",
          "set_verb_code",
          "setadd",
          "server_log",
          "shutdown",
          "strcmp",
          "strsub",
          "suspend",
          "switch_player",
          "task_perms",
          "tofloat",
          "toint",
          "toliteral",
          "toobj",
          "tostr",
          "typeof",
          "valid",
          "verb_code");

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
  void queuedTasksUsesTheRegisteredTaskOwnerWithToastArgumentShapes() {
    ListValue tasks =
        new ListValue(
            List.of(
                new ListValue(
                    List.of(
                        new IntegerValue(17),
                        new IntegerValue(1234),
                        new IntegerValue(0)))));
    BuiltinCatalog catalog =
        new BuiltinCatalog((a, w, p, t, rt, rs, r, cp, c) -> Result.value(tasks));
    BuiltinSpec spec = catalog.spec("queued_tasks").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(),
                List.of(Set.of(ArgType.INTEGER), Set.of(ArgType.INTEGER)),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.IRREVOCABLE, spec.effect());
    assertEquals(BuiltinOwner.TASK, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      assertEquals(Optional.of(tasks), invoke(catalog, spec, List.of(), transaction, 1).value());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(catalog, spec, List.of(string("bad")), transaction, 1).error());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(0), new IntegerValue(0), new IntegerValue(0)),
                  transaction,
                  1)
              .error());
    }
  }

  @Test
  void addVerbValidatesAndStagesOneCompleteWorldVerb() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("add_verb").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.ANY), Set.of(ArgType.LIST), Set.of(ArgType.LIST)),
                List.of(),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.TRANSACTION_WRITE, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());

    WorldObject wizard =
        new WorldObject(1, "Wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject target =
        new WorldObject(3, "Target", 0, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(wizard, target)).begin()) {
      Result result =
          invoke(
              catalog,
              spec,
              List.of(
                  new ObjectValue(3),
                  new ListValue(List.of(new ObjectValue(1), string("xd"), string("foobar"))),
                  new ListValue(List.of(string("this"), string("none"), string("this")))),
              transaction,
              1);

      assertEquals(Optional.of(new IntegerValue(1)), result.value());
      assertEquals(
          new WorldVerb("foobar", 1, 12 | (2 << 4) | (2 << 6), -1, ""),
          transaction.verb(3, 0).orElseThrow());
    }
  }

  @Test
  void addPropertyValidatesAndStagesOneCompleteWorldProperty() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("add_property").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(
                    Set.of(ArgType.ANY),
                    Set.of(ArgType.STRING),
                    Set.of(ArgType.ANY),
                    Set.of(ArgType.LIST)),
                List.of(),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.TRANSACTION_WRITE, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());

    WorldObject wizard =
        new WorldObject(1, "Wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject target =
        new WorldObject(3, "Target", 0, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(wizard, target)).begin()) {
      Result result =
          invoke(
              catalog,
              spec,
              List.of(
                  new ObjectValue(3),
                  string("foo"),
                  new IntegerValue(99),
                  new ListValue(List.of(new ObjectValue(1), string("rwc")))),
              transaction,
              1);

      assertEquals(Optional.of(new IntegerValue(0)), result.value());
      assertEquals(
          Optional.of(new IntegerValue(99)),
          transaction.readObjectProperty(3, "foo"));
      assertEquals(7, transaction.property(3, "foo").orElseThrow().permissions());
    }
  }

  @Test
  void propertyBuiltinsExposeDefinitionsAndClearInheritedSlots() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    assertEquals(EffectClass.TRANSACTION_READ, catalog.spec("properties").orElseThrow().effect());
    assertEquals(
        EffectClass.TRANSACTION_READ,
        catalog.spec("is_clear_property").orElseThrow().effect());
    assertEquals(
        EffectClass.TRANSACTION_WRITE, catalog.spec("clear_property").orElseThrow().effect());
    assertEquals(
        EffectClass.TRANSACTION_WRITE, catalog.spec("delete_property").orElseThrow().effect());

    WorldProperty definition =
        new WorldProperty("test", new IntegerValue(1), 1, 7, false, true);
    WorldProperty inherited =
        new WorldProperty("test", new IntegerValue(1), 1, 7, true, false);
    WorldObject wizard =
        new WorldObject(
            1,
            "Wizard",
            4,
            1,
            -1,
            -1,
            List.of(),
            List.of(2L),
            List.of(),
            List.of(definition));
    WorldObject child =
        new WorldObject(
            2,
            "Child",
            0,
            1,
            -1,
            1,
            List.of(),
            List.of(),
            List.of(),
            List.of(inherited));
    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(wizard, child)).begin()) {
      assertEquals(
          Optional.of(new ListValue(List.of(string("test")))),
          invoke(
                  catalog,
                  catalog.spec("properties").orElseThrow(),
                  List.of(new ObjectValue(1)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(new ListValue(List.of())),
          invoke(
                  catalog,
                  catalog.spec("properties").orElseThrow(),
                  List.of(new ObjectValue(2)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(new IntegerValue(1)),
          invoke(
                  catalog,
                  catalog.spec("is_clear_property").orElseThrow(),
                  List.of(new ObjectValue(2), string("test")),
                  transaction,
                  1)
              .value());

      assertTrue(transaction.writeObjectProperty(2, "test", new IntegerValue(2)));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          invoke(
                  catalog,
                  catalog.spec("is_clear_property").orElseThrow(),
                  List.of(new ObjectValue(2), string("test")),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(new IntegerValue(0)),
          invoke(
                  catalog,
                  catalog.spec("clear_property").orElseThrow(),
                  List.of(new ObjectValue(2), string("test")),
                  transaction,
                  1)
              .value());
      assertEquals(Optional.of(new IntegerValue(1)), transaction.readObjectProperty(2, "test"));

      assertEquals(
          Optional.of(new IntegerValue(0)),
          invoke(
                  catalog,
                  catalog.spec("delete_property").orElseThrow(),
                  List.of(new ObjectValue(1), string("test")),
                  transaction,
                  1)
              .value());
      assertTrue(transaction.property(2, "test").isEmpty());
    }
  }

  @Test
  void propertyInfoResolvesCaseInsensitivelyAndReturnsCanonicalMetadata() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("property_info").orElseThrow();
    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.ANY), Set.of(ArgType.STRING)),
                List.of(),
                Optional.empty())),
        spec.callShapes());
    assertEquals(EffectClass.TRANSACTION_READ, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());

    WorldObject wizard =
        new WorldObject(1, "Wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject target =
        new WorldObject(
            2,
            "Target",
            0,
            2,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new WorldProperty("CaseProbe", new IntegerValue(42), 1, 3, false, true),
                new WorldProperty("Private", new IntegerValue(0), 1, 2, false, true)));
    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(wizard, target)).begin()) {
      assertEquals(
          Optional.of(new ListValue(List.of(new ObjectValue(1), string("rw")))),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), string("caseprobe")),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(ErrorValue.E_PROPNF),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), string("name")),
                  transaction,
                  1)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), string("private")),
                  transaction,
                  2)
              .error());
    }
  }

  @Test
  void mapKeysReturnsToastCanonicalScalarOrderWithoutCollapsingAdjacentFloats() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("mapkeys").orElseThrow();
    assertPureVmContract(
        catalog,
        "mapkeys",
        new CallShape(List.of(Set.of(ArgType.MAP)), List.of(), Optional.empty()));

    FloatValue next = new FloatValue(1.0000000000000002);
    FloatValue one = new FloatValue(1.0);
    LinkedHashMap<MooValue, MooValue> entries = new LinkedHashMap<>();
    entries.put(string("z"), new IntegerValue(1));
    entries.put(next, new IntegerValue(2));
    entries.put(ErrorValue.E_PERM, new IntegerValue(3));
    entries.put(new ObjectValue(5), new IntegerValue(4));
    entries.put(new IntegerValue(10), new IntegerValue(5));
    entries.put(one, new IntegerValue(6));

    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      new IntegerValue(10),
                      new ObjectValue(5),
                      ErrorValue.E_PERM,
                      one,
                      next,
                      string("z")))),
          invoke(catalog, spec, List.of(new MapValue(entries)), transaction, 1).value());
    }
  }

  @Test
  void setVerbCodeCompilesAndStagesSourceOnOneDefinedVerb() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("set_verb_code").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.ANY), Set.of(ArgType.ANY), Set.of(ArgType.LIST)),
                List.of(),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.TRANSACTION_WRITE, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());

    WorldObject wizard =
        new WorldObject(1, "Wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject target =
        new WorldObject(
            3,
            "Target",
            0,
            1,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(new WorldVerb("foobar", 1, 0, -1, "")),
            List.of());
    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(wizard, target)).begin()) {
      Result result =
          invoke(
              catalog,
              spec,
              List.of(
                  new ObjectValue(3),
                  string("foobar"),
                  new ListValue(List.of(string("return \"foobar\"[^..$];")))),
              transaction,
              1);

      assertEquals(Optional.of(new ListValue(List.of())), result.value());
      assertEquals(
          "return \"foobar\"[^..$];", transaction.verb(3, 0).orElseThrow().programSource());
    }
  }

  @Test
  void verbCodeReadsOneLocalVerbAsCanonicalSourceLines() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("verb_code").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.ANY), Set.of(ArgType.ANY)),
                List.of(Set.of(ArgType.ANY), Set.of(ArgType.ANY)),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.TRANSACTION_READ, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());

    WorldObject programmer =
        new WorldObject(2, "Programmer", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject target =
        new WorldObject(
            3,
            "Target",
            0,
            2,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(
                new WorldVerb(
                    "test",
                    2,
                    0,
                    -1,
                    "for i, j in ({})\n  break j;\n  continue i;\nendfor")),
            List.of());
    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(programmer, target)).begin()) {
      Result result =
          invoke(catalog, spec, List.of(new ObjectValue(3), string("test")), transaction, 2);

      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      string("for i, j in ({})"),
                      string("  break j;"),
                      string("  continue i;"),
                      string("endfor")))),
          result.value());
    }
  }

  @Test
  void recycleAuthorizesOneObjectForTheExistingVmOutcome() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("recycle").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(List.of(Set.of(ArgType.ANY)), List.of(), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.TRANSACTION_WRITE, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());

    WorldObject owner =
        new WorldObject(2, "Owner", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject target =
        new WorldObject(3, "Target", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(owner, target)).begin()) {
      Result result =
          invoke(catalog, spec, List.of(new ObjectValue(3)), transaction, 2);

      assertEquals(OptionalLong.of(3), result.recycleTarget());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(catalog, spec, List.of(new IntegerValue(3)), transaction, 2).error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(catalog, spec, List.of(new ObjectValue(999)), transaction, 2).error());
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(catalog, spec, List.of(new ObjectValue(3)), transaction, 1).error());
    }
  }

  @Test
  void objectQueriesReadTheExistingTransactionalWorldState() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec parent = catalog.spec("parent").orElseThrow();
    BuiltinSpec isPlayer = catalog.spec("is_player").orElseThrow();
    BuiltinSpec valid = catalog.spec("valid").orElseThrow();

    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.ANY)), List.of(), Optional.empty())),
        parent.callShapes());
    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.OBJECT)), List.of(), Optional.empty())),
        isPlayer.callShapes());
    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.ANY)), List.of(), Optional.empty())),
        valid.callShapes());
    for (BuiltinSpec spec : List.of(parent, isPlayer, valid)) {
      assertSame(BuiltinPermissionRule.ANY, spec.permission());
      assertEquals(EffectClass.TRANSACTION_READ, spec.effect());
      assertEquals(BuiltinOwner.WORLD, spec.owner());
    }

    WorldObject system =
        new WorldObject(0, "System", 0, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject player =
        new WorldObject(1, "Player", 1, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject parentObject =
        new WorldObject(2, "Parent", 0, 1, -1, -1, List.of(), List.of(3L), List.of(), List.of());
    WorldObject child =
        new WorldObject(3, "Child", 0, 1, -1, 2, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction =
        new WorldTxn(List.of(1L), List.of(system, player, parentObject, child)).begin()) {
      assertEquals(
          Optional.of(new ObjectValue(2)),
          invoke(catalog, parent, List.of(new ObjectValue(3)), transaction, 1).value());
      assertEquals(
          Optional.of(new IntegerValue(1)),
          invoke(catalog, isPlayer, List.of(new ObjectValue(1)), transaction, 1).value());
      assertEquals(
          Optional.of(new IntegerValue(0)),
          invoke(catalog, isPlayer, List.of(new ObjectValue(3)), transaction, 1).value());
      assertEquals(
          Optional.of(new IntegerValue(1)),
          invoke(catalog, valid, List.of(new ObjectValue(0)), transaction, 1).value());
      assertEquals(
          Optional.of(new IntegerValue(0)),
          invoke(catalog, valid, List.of(new ObjectValue(-1)), transaction, 1).value());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(catalog, parent, List.of(new ObjectValue(99)), transaction, 1).error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(catalog, isPlayer, List.of(new ObjectValue(99)), transaction, 1).error());
    }
  }

  @Test
  void raiseProducesTheExistingStructuredErrorOutcome() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("raise").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.ANY)),
                List.of(Set.of(ArgType.STRING), Set.of(ArgType.ANY)),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.PURE, spec.effect());
    assertEquals(BuiltinOwner.VM, spec.owner());

    try (WorldTxn transaction = new WorldTxn(List.of(), List.of()).begin()) {
      Result basic =
          invoke(catalog, spec, List.of(ErrorValue.E_INVARG), transaction, 1);
      assertEquals(Optional.of(ErrorValue.E_INVARG), basic.error());
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(string("E_INVARG"), new IntegerValue(0), new ListValue(List.of())))),
          basic.errorDetails());

      ListValue customValue = new ListValue(List.of(new IntegerValue(1), new IntegerValue(2)));
      Result custom =
          invoke(
              catalog,
              spec,
              List.of(ErrorValue.E_TYPE, string("custom message"), customValue),
              transaction,
              1);
      assertEquals(Optional.of(ErrorValue.E_TYPE), custom.error());
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(string("custom message"), customValue, new ListValue(List.of())))),
          custom.errorDetails());
    }
  }

  @Test
  void maxSelectsTheLargestHomogeneousNumericArgumentWithoutPromotion() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    try (WorldTxn transaction = world().begin()) {
      BuiltinSpec spec = catalog.spec("max").orElseThrow();

      assertEquals(
          Optional.of(new IntegerValue(7)),
          invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(3), new IntegerValue(7), new IntegerValue(1)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(new FloatValue(7.5)),
          invoke(
                  catalog,
                  spec,
                  List.of(new FloatValue(3.5), new FloatValue(7.5)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(3), new FloatValue(7.5)),
                  transaction,
                  1)
              .error());
    }
  }

  @Test
  void randomUsesTheIrrevocableVmContractAndInclusiveIntegerBounds() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("random").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(),
                List.of(Set.of(ArgType.INTEGER), Set.of(ArgType.INTEGER)),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.IRREVOCABLE, spec.effect());
    assertEquals(BuiltinOwner.VM, spec.owner());

    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(new IntegerValue(1)),
          invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 1).value());
      for (int invocation = 0; invocation < 64; invocation++) {
        long value =
            ((IntegerValue)
                    invoke(catalog, spec, List.of(new IntegerValue(7)), transaction, 1)
                        .value()
                        .orElseThrow())
                .value();
        assertTrue(value >= 1 && value <= 7);
      }
      long ranged =
          ((IntegerValue)
                  invoke(
                          catalog,
                          spec,
                          List.of(new IntegerValue(5), new IntegerValue(10)),
                          transaction,
                          1)
                      .value()
                      .orElseThrow())
              .value();
      assertTrue(ranged >= 5 && ranged <= 10);
      for (List<MooValue> arguments :
          List.of(
              List.<MooValue>of(new IntegerValue(0)),
              List.<MooValue>of(new IntegerValue(-1)),
              List.<MooValue>of(new IntegerValue(10), new IntegerValue(5)))) {
        assertEquals(
            Optional.of(ErrorValue.E_INVARG),
            invoke(catalog, spec, arguments, transaction, 1).error());
      }
    }
  }

  @Test
  void disassembleReadsOneDefinedVerbAndReturnsDeterministicBytecodeLines() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("disassemble").orElseThrow();

    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.ANY), Set.of(ArgType.ANY)), List.of(), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.TRANSACTION_READ, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());

    WorldObject wizard =
        new WorldObject(1, "Wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject programmer =
        new WorldObject(2, "Programmer", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject target =
        new WorldObject(
            3,
            "Target",
            0,
            2,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(new WorldVerb("foobar", 2, 0, -1, "return \"foobar\"[^..$];")),
            List.of());
    WorldObject privateTarget =
        new WorldObject(
            4,
            "Private",
            0,
            1,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(new WorldVerb("secret", 1, 0, -1, "return 1;")),
            List.of());
    try (WorldTxn transaction =
        new WorldTxn(List.of(), List.of(wizard, programmer, target, privateTarget)).begin()) {
      Result result =
          invoke(catalog, spec, List.of(new ObjectValue(3), string("foobar")), transaction, 2);
      ListValue lines = (ListValue) result.value().orElseThrow();
      assertTrue(
          lines.elements().stream()
              .map(StringValue.class::cast)
              .map(BuiltinCatalogTest::decode)
              .anyMatch(line -> line.contains("FIRST")));
      assertTrue(
          lines.elements().stream()
              .map(StringValue.class::cast)
              .map(BuiltinCatalogTest::decode)
              .anyMatch(line -> line.contains("LAST")));

      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(3), string("foobar")),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(3), new FloatValue(1.5)),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(999), string("foobar")),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(3), new IntegerValue(0)),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_VERBNF),
          invoke(catalog, spec, List.of(new ObjectValue(3), string("missing")), transaction, 2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(catalog, spec, List.of(new ObjectValue(4), string("secret")), transaction, 2)
              .error());
      assertTrue(
          invoke(catalog, spec, List.of(new ObjectValue(4), string("secret")), transaction, 1)
              .value()
              .isPresent());
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

  private static String decode(StringValue value) {
    return new String(value.bytes(), StandardCharsets.ISO_8859_1);
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
