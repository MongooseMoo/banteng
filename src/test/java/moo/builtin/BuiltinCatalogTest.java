package moo.builtin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Random;
import java.util.Set;
import moo.builtin.BuiltinCatalog.ConnectionOption;
import moo.builtin.BuiltinCatalog.ConnectionOptionRequest;
import moo.builtin.BuiltinCatalog.ForcedInputRequest;
import moo.builtin.BuiltinCatalog.Result;
import moo.value.MooValue;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.BooleanValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;
import moo.world.WorldObject;
import moo.world.WorldProperty;
import moo.world.WorldTxn;
import moo.world.WorldVerb;
import org.junit.jupiter.api.Test;

final class BuiltinCatalogTest {
  private static final Set<String> REACHABLE_NAMES =
      Set.of(
          "abs",
          "add_property",
          "add_verb",
          "boot_player",
          "caller_perms",
          "chr",
          "connection_info",
          "connection_name",
          "connected_players",
          "create",
          "clear_property",
          "decode_binary",
          "delete_verb",
          "delete_property",
          "disassemble",
          "dump_database",
          "encode_binary",
          "equal",
          "eval",
          "explode",
          "force_input",
          "function_info",
          "index",
          "is_player",
          "is_clear_property",
          "length",
          "kill_task",
          "listen",
          "load_server_options",
          "listappend",
          "listdelete",
          "listinsert",
          "listset",
          "mapkeys",
          "max",
          "min",
          "move",
          "new_waif",
          "notify",
          "parent",
          "properties",
          "property_info",
          "queued_tasks",
          "random",
          "reseed_random",
          "raise",
          "read",
          "recycle",
          "reverse",
          "rindex",
          "run_gc",
          "seconds_left",
          "set_connection_option",
          "set_player_flag",
          "set_task_perms",
          "set_verb_args",
          "set_verb_code",
          "set_verb_info",
          "setadd",
          "setremove",
          "server_log",
          "shutdown",
          "strcmp",
          "strsub",
          "suspend",
          "switch_player",
          "task_id",
          "task_perms",
          "ticks_left",
          "time",
          "tofloat",
          "toint",
          "toliteral",
          "toobj",
          "tostr",
          "typeof",
          "valid",
          "value_bytes",
          "verb_args",
          "verb_code",
          "verb_info",
          "yin");

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
  void valueBytesUsesTheExactStock64BitLayoutWithoutMutatingCommittedWorld() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("value_bytes").orElseThrow();

    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.ANY)), List.of(), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(0, spec.tickCost().charge(List.of(new IntegerValue(1))));
    assertEquals(EffectClass.TRANSACTION_READ, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());

    WorldObject wizard =
        new WorldObject(1, "Wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject waifClass =
        new WorldObject(
            7,
            "Waif class",
            0,
            1,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new WorldProperty(":marker", new IntegerValue(0), 1, 0, false, true),
                new WorldProperty(":inherited", string("not counted"), 1, 0, false, true)));
    WorldTxn root = new WorldTxn(List.of(), List.of(wizard, waifClass));
    WaifValue waif;
    try (WorldTxn setup = root.begin()) {
      waif = setup.createWaif(7, 1);
      assertTrue(setup.writeWaifProperty(waif, "marker", new IntegerValue(42)));
      assertTrue(setup.commit().isCommitted());
    }

    var committedBefore = root.snapshot();
    try (WorldTxn transaction = root.begin()) {
      var transactionBefore = transaction.snapshot();
      MapValue map =
          new MapValue(Map.of(new IntegerValue(1), new FloatValue(1.0)));
      ListValue list =
          new ListValue(List.of(new IntegerValue(1), BooleanValue.TRUE, string("x")));

      Map<MooValue, Long> expected = new LinkedHashMap<>();
      expected.put(new IntegerValue(42), 16L);
      expected.put(BooleanValue.TRUE, 16L);
      expected.put(new ObjectValue(7), 16L);
      expected.put(ErrorValue.E_INVARG, 16L);
      expected.put(new AnonymousObjectValue(), 16L);
      expected.put(new FloatValue(1.0), 24L);
      expected.put(string(""), 17L);
      expected.put(string("hello"), 22L);
      expected.put(new ListValue(List.of()), 32L);
      expected.put(list, 82L);
      expected.put(new ListValue(List.of(list, list)), 196L);
      expected.put(new MapValue(Map.of()), 32L);
      expected.put(map, 96L);
      expected.put(waif, 88L);

      for (Map.Entry<MooValue, Long> entry : expected.entrySet()) {
        assertEquals(
            Optional.of(new IntegerValue(entry.getValue())),
            invoke(catalog, spec, List.of(entry.getKey()), transaction, 1).value(),
            entry.getKey().type().name());
      }

      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(catalog, spec, List.of(), transaction, 1).error());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(1), new IntegerValue(2)),
                  transaction,
                  1)
              .error());
      assertEquals(transactionBefore, transaction.snapshot());
    }
    assertEquals(committedBefore, root.snapshot());
  }

  @Test
  void reseedRandomUsesEntropyToMutateTheSharedWizardOnlyGenerator()
      throws ReflectiveOperationException {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("reseed_random").orElseThrow();

    assertEquals(
        List.of(new CallShape(List.of(), List.of(), Optional.empty())), spec.callShapes());
    assertSame(BuiltinPermissionRule.WIZARD_ONLY, spec.permission());
    assertEquals(0, spec.tickCost().charge(List.of()));
    assertEquals(EffectClass.IRREVOCABLE, spec.effect());
    assertEquals(BuiltinOwner.VM, spec.owner());

    RecordingRandom recordingRandom = new RecordingRandom();
    recordingRandom.resetSeedCalls();
    Field randomField = BuiltinCatalog.class.getDeclaredField("random");
    randomField.setAccessible(true);
    randomField.set(catalog, recordingRandom);

    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 1).error());
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(catalog, spec, List.of(), transaction, 2).error());
      assertEquals(0, recordingRandom.seedCalls());

      assertEquals(
          Optional.of(new IntegerValue(0)),
          invoke(catalog, spec, List.of(), transaction, 1).value());
      assertEquals(1, recordingRandom.seedCalls());
    }
  }

  @Test
  void explodePreservesToastByteDelimiterEmptyFieldAndErrorSemantics() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("explode").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.STRING)),
                List.of(Set.of(ArgType.STRING), Set.of(ArgType.INTEGER)),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(0, spec.tickCost().charge(List.of(string("value"))));
    assertEquals(EffectClass.PURE, spec.effect());
    assertEquals(BuiltinOwner.VM, spec.owner());

    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(new ListValue(List.of(string("alpha"), string("beta")))),
          invoke(catalog, spec, List.of(string(" alpha  beta ")), transaction, 1).value());
      assertEquals(
          Optional.of(new ListValue(List.of(string("a"), string("b")))),
          invoke(
                  catalog,
                  spec,
                  List.of(string("::a::b:"), string(":"), new IntegerValue(0)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      string(""),
                      string(""),
                      string("a"),
                      string(""),
                      string("b"),
                      string("")))),
          invoke(
                  catalog,
                  spec,
                  List.of(string("::a::b:"), string(":"), new IntegerValue(1)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(new ListValue(List.of(string("a"), string("b;c")))),
          invoke(catalog, spec, List.of(string("a,b;c"), string(",;")), transaction, 1).value());
      assertEquals(
          Optional.of(new ListValue(List.of(string("a"), string("b")))),
          invoke(catalog, spec, List.of(string("a b"), string("")), transaction, 1).value());
      assertEquals(
          Optional.of(new ListValue(List.of())),
          invoke(catalog, spec, List.of(string("")), transaction, 1).value());
      assertEquals(
          Optional.of(new ListValue(List.of(string("")))),
          invoke(
                  catalog,
                  spec,
                  List.of(string(""), string(":"), new IntegerValue(1)),
                  transaction,
                  1)
              .value());

      StringValue highBitSource =
          new StringValue(new byte[] {(byte) 0xe9, (byte) ':', (byte) 0xff});
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      new StringValue(new byte[] {(byte) 0xe9}),
                      new StringValue(new byte[] {(byte) 0xff})))),
          invoke(catalog, spec, List.of(highBitSource, string(":")), transaction, 1).value());

      assertEquals(
          Optional.of(ErrorValue.E_ARGS), invoke(catalog, spec, List.of(), transaction, 1).error());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(
                  catalog,
                  spec,
                  List.of(string("a"), string(":"), new IntegerValue(0), new IntegerValue(1)),
                  transaction,
                  1)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 1).error());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(catalog, spec, List.of(string("a"), new IntegerValue(1)), transaction, 1).error());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(catalog, spec, List.of(string("a"), string(":"), string("1")), transaction, 1)
              .error());

      Result functionInfo =
          invoke(
              catalog,
              catalog.spec("function_info").orElseThrow(),
              List.of(string("explode")),
              transaction,
              1);
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      string("explode"),
                      new IntegerValue(1),
                      new IntegerValue(3),
                      new ListValue(
                          List.of(
                              new IntegerValue(2),
                              new IntegerValue(2),
                              new IntegerValue(0)))))),
          functionInfo.value());
    }
  }

  @Test
  void reversePreservesToastBytewiseShallowAndUnsupportedValueSemantics() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("reverse").orElseThrow();
    WorldTxn root = world();
    var committedBefore = root.snapshot();

    try (WorldTxn transaction = root.begin()) {
      var transactionBefore = transaction.snapshot();

      StringValue raw = new StringValue(new byte[] {0x41, (byte) 0xe9, (byte) 0xff});
      StringValue rawReversed =
          (StringValue)
              invoke(catalog, spec, List.of(raw), transaction, 1).value().orElseThrow();
      assertArrayEquals(new byte[] {(byte) 0xff, (byte) 0xe9, 0x41}, rawReversed.bytes());
      assertEquals(
          Optional.of(string("")),
          invoke(catalog, spec, List.of(string("")), transaction, 1).value());
      assertEquals(
          Optional.of(new StringValue(new byte[] {(byte) 0xe9})),
          invoke(
                  catalog,
                  spec,
                  List.of(new StringValue(new byte[] {(byte) 0xe9})),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(string("olleh")),
          invoke(catalog, spec, List.of(string("hello")), transaction, 1).value());
      assertEquals(
          Optional.of(string("abcba")),
          invoke(catalog, spec, List.of(string("abcba")), transaction, 1).value());

      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(new IntegerValue(3), new IntegerValue(2), new IntegerValue(1)))),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new ListValue(
                          List.of(
                              new IntegerValue(1),
                              new IntegerValue(2),
                              new IntegerValue(3)))),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(new ListValue(List.of())),
          invoke(catalog, spec, List.of(new ListValue(List.of())), transaction, 1).value());
      assertEquals(
          Optional.of(new ListValue(List.of(new IntegerValue(42)))),
          invoke(
                  catalog,
                  spec,
                  List.of(new ListValue(List.of(new IntegerValue(42)))),
                  transaction,
                  1)
              .value());

      ListValue nested = new ListValue(List.of(new IntegerValue(2), new IntegerValue(3)));
      ListValue mixed =
          new ListValue(
              List.of(string("a"), new IntegerValue(1), new ObjectValue(0), nested));
      ListValue mixedReversed =
          (ListValue)
              invoke(catalog, spec, List.of(mixed), transaction, 1).value().orElseThrow();
      assertEquals(
          new ListValue(List.of(nested, new ObjectValue(0), new IntegerValue(1), string("a"))),
          mixedReversed);
      assertSame(nested, mixedReversed.elements().getFirst());

      for (MooValue unsupported :
          List.of(
              new IntegerValue(1),
              new FloatValue(1.0),
              new ObjectValue(0),
              ErrorValue.E_PERM,
              new MapValue(Map.of()))) {
        Result result = invoke(catalog, spec, List.of(unsupported), transaction, 1);
        assertEquals(Optional.of(ErrorValue.E_INVARG), result.error());
        assertTrue(result.error().filter(ErrorValue.E_TYPE::equals).isEmpty());
      }

      assertEquals(
          Optional.of(ErrorValue.E_ARGS), invoke(catalog, spec, List.of(), transaction, 1).error());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(catalog, spec, List.of(string("a"), string("b")), transaction, 1).error());

      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      string("reverse"),
                      new IntegerValue(1),
                      new IntegerValue(1),
                      new ListValue(List.of(new IntegerValue(-1)))))),
          invoke(
                  catalog,
                  catalog.spec("function_info").orElseThrow(),
                  List.of(string("reverse")),
                  transaction,
                  1)
              .value());

      assertEquals(transactionBefore, transaction.snapshot());
    }
    assertEquals(committedBefore, root.snapshot());
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
        new BuiltinCatalog((a, w, p, t, id, rt, rs, r, cp, c) -> Result.value(tasks));
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
  void timeReturnsCurrentEpochSecondsThroughTheIrrevocableVmOwner() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("time").orElseThrow();
    long before = Instant.now().getEpochSecond();

    try (WorldTxn transaction = world().begin()) {
      Result result = invoke(catalog, spec, List.of(), transaction, 1);
      long after = Instant.now().getEpochSecond();
      long value = ((IntegerValue) result.value().orElseThrow()).value();

      assertEquals(
          List.of(new CallShape(List.of(), List.of(), Optional.empty())), spec.callShapes());
      assertSame(BuiltinPermissionRule.ANY, spec.permission());
      assertEquals(EffectClass.IRREVOCABLE, spec.effect());
      assertEquals(BuiltinOwner.VM, spec.owner());
      assertTrue(value >= before && value <= after);
    }
  }

  @Test
  void killTaskUsesTheRegisteredTaskOwnerWithOneIntegerArgument() {
    BuiltinCatalog catalog =
        new BuiltinCatalog(
            (a, w, p, t, id, rt, rs, r, cp, c) -> Result.value(new ListValue(List.of())),
            (a, w, p, t, id, rt, rs, r, cp, c) -> Result.value(new IntegerValue(23)),
            (a, w, p, t, id, rt, rs, r, cp, c) -> Result.error(ErrorValue.E_INVARG));
    BuiltinSpec spec = catalog.spec("kill_task").orElseThrow();

    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.INTEGER)), List.of(), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.IRREVOCABLE, spec.effect());
    assertEquals(BuiltinOwner.TASK, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(new IntegerValue(23)),
          invoke(catalog, spec, List.of(new IntegerValue(17)), transaction, 1).value());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(catalog, spec, List.of(string("bad")), transaction, 1).error());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(catalog, spec, List.of(), transaction, 1).error());
    }
  }

  @Test
  void readDeclaresSuspendingConnectionContractAndDeniesAnUnrelatedProgrammer() {
    BuiltinCatalog catalog =
        new BuiltinCatalog(
            (a, w, p, t, id, rt, rs, r, cp, c) -> Result.value(new ListValue(List.of())),
            (a, w, p, t, id, rt, rs, r, cp, c) -> Result.error(ErrorValue.E_INVARG),
            (a, w, p, t, id, rt, rs, r, cp, c) -> Result.value(new IntegerValue(0)));
    BuiltinSpec spec = catalog.spec("read").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(),
                List.of(Set.of(ArgType.OBJECT), Set.of(ArgType.ANY)),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(0, spec.tickCost().charge(List.of()));
    assertEquals(EffectClass.SUSPENDING_HOST, spec.effect());
    assertEquals(BuiltinOwner.CONNECTION, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(1), new IntegerValue(1)),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(new IntegerValue(0)),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(1), new IntegerValue(1)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(catalog, spec, List.of(), transaction, 2).error());
      assertEquals(
          Optional.of(new IntegerValue(0)),
          invoke(catalog, spec, List.of(), transaction, 1).value());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 2).error());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(1), new IntegerValue(1), new IntegerValue(1)),
                  transaction,
                  2)
              .error());
    }
  }

  @Test
  void connectionInfoReadsTheLiveConnectionWithToastPermissions() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("connection_info").orElseThrow();
    MapValue info =
        new MapValue(
            Map.of(
                string("destination_ip"), string("127.0.0.1"),
                string("outbound"), new IntegerValue(0)));

    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.OBJECT)), List.of(), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.EXTERNAL_READ, spec.effect());
    assertEquals(BuiltinOwner.CONNECTION, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      transaction.openConnection(-2, info);
      transaction.switchConnectionPlayer(-2, 2);
      transaction.openConnection(-3, info);
      transaction.switchConnectionPlayer(-3, 1);

      assertEquals(
          Optional.of(info),
          invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 2).value());
      assertEquals(
          Optional.of(info),
          invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 1).value());
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(catalog, spec, List.of(new ObjectValue(1)), transaction, 2).error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(catalog, spec, List.of(new ObjectValue(99)), transaction, 1).error());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(catalog, spec, List.of(new IntegerValue(2)), transaction, 1).error());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS), invoke(catalog, spec, List.of(), transaction, 1).error());
    }
  }

  @Test
  void connectionNameUsesSavedRemoteAddressAndToastLegacyFormatting() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("connection_name").orElseThrow();
    MapValue info =
        new MapValue(
            Map.of(
                string("source_address"), string("server.example"),
                string("source_ip"), string("192.0.2.10"),
                string("source_port"), new IntegerValue(7777),
                string("destination_address"), string("client.example"),
                string("destination_ip"), string("198.51.100.25"),
                string("destination_port"), new IntegerValue(4242),
                string("outbound"), new IntegerValue(0)));

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.OBJECT)),
                List.of(Set.of(ArgType.INTEGER)),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.EXTERNAL_READ, spec.effect());
    assertEquals(BuiltinOwner.CONNECTION, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      transaction.openConnection(-2, info);
      transaction.switchConnectionPlayer(-2, 2);

      assertEquals(
          Optional.of(string("client.example")),
          invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 2).value());
      assertEquals(
          Optional.of(string("198.51.100.25")),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new IntegerValue(1)),
                  transaction,
                  2)
              .value());
      assertEquals(
          Optional.of(
              string("port 7777 from client.example [198.51.100.25], port 4242")),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new IntegerValue(0)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(catalog, spec, List.of(new ObjectValue(1)), transaction, 2).error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(catalog, spec, List.of(new ObjectValue(99)), transaction, 1).error());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(catalog, spec, List.of(new ObjectValue(2), string("0")), transaction, 2).error());
    }
  }

  @Test
  void connectedPlayersReturnsNewestConnectionsWithOptionalNegativePlayers() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("connected_players").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(), List.of(Set.of(ArgType.ANY)), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.EXTERNAL_READ, spec.effect());
    assertEquals(BuiltinOwner.CONNECTION, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      transaction.openConnection(-2);
      transaction.switchConnectionPlayer(-2, 2);
      transaction.openConnection(-3);

      assertEquals(
          Optional.of(new ListValue(List.of(new ObjectValue(2)))),
          invoke(catalog, spec, List.of(), transaction, 2).value());
      assertEquals(
          Optional.of(new ListValue(List.of(new ObjectValue(-3), new ObjectValue(2)))),
          invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 2).value());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(1), new IntegerValue(1)),
                  transaction,
                  2)
              .error());
    }
  }

  @Test
  void setConnectionOptionStagesOneAuthorizedDeferredConnectionMutation() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("set_connection_option").orElseThrow();
    MapValue info = new MapValue(Map.of(string("destination_ip"), string("127.0.0.1")));

    assertEquals(
        List.of(
            new CallShape(
                List.of(
                    Set.of(ArgType.OBJECT), Set.of(ArgType.STRING), Set.of(ArgType.ANY)),
                List.of(),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.DEFERRED_COMMIT, spec.effect());
    assertEquals(BuiltinOwner.CONNECTION, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      transaction.openConnection(-2, info);
      transaction.switchConnectionPlayer(-2, 2);
      transaction.openConnection(-3, info);
      transaction.switchConnectionPlayer(-3, 1);

      Result held =
          invoke(
              catalog,
              spec,
              List.of(new ObjectValue(2), string("HoLd-InPuT"), new IntegerValue(1)),
              transaction,
              2);
      assertEquals(Optional.of(new IntegerValue(0)), held.value());
      assertEquals(
          Optional.of(
              new ConnectionOptionRequest(2, ConnectionOption.HOLD_INPUT, new IntegerValue(1))),
          held.connectionOptionRequest());

      Result flush =
          invoke(
              catalog,
              spec,
              List.of(new ObjectValue(2), string("flush-command"), string(".flush")),
              transaction,
              1);
      assertEquals(
          Optional.of(
              new ConnectionOptionRequest(2, ConnectionOption.FLUSH_COMMAND, string(".flush"))),
          flush.connectionOptionRequest());
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(1), string("hold-input"), new IntegerValue(1)),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(99), string("hold-input"), new IntegerValue(1)),
                  transaction,
                  1)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), string("unknown"), new IntegerValue(1)),
                  transaction,
                  2)
              .error());
    }
  }

  @Test
  void forceInputStagesToastCompatibleConnectionInputWithoutTargetValidation() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("force_input").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.OBJECT), Set.of(ArgType.STRING)),
                List.of(Set.of(ArgType.ANY)),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.DEFERRED_COMMIT, spec.effect());
    assertEquals(BuiltinOwner.CONNECTION, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      Result negative =
          invoke(
              catalog,
              spec,
              List.of(new ObjectValue(-2), string("audit-queue-login")),
              transaction,
              1);
      assertEquals(Optional.of(new IntegerValue(0)), negative.value());
      assertEquals(
          Optional.of(new ForcedInputRequest(-2, "audit-queue-login")),
          negative.forcedInputRequest());

      Result self =
          invoke(
              catalog,
              spec,
              List.of(new ObjectValue(2), string("auditq"), new IntegerValue(1)),
              transaction,
              2);
      assertEquals(Optional.of(new ForcedInputRequest(2, "auditq")), self.forcedInputRequest());
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(1), string("auditq")),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new IntegerValue(1)),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 2).error());
    }
  }

  @Test
  void exposesCurrentTaskIdentityAndBudgetsAndAcknowledgesLiveServerOptions() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec load = catalog.spec("load_server_options").orElseThrow();
    BuiltinSpec taskId = catalog.spec("task_id").orElseThrow();
    BuiltinSpec ticks = catalog.spec("ticks_left").orElseThrow();
    BuiltinSpec seconds = catalog.spec("seconds_left").orElseThrow();
    CallShape noArguments = new CallShape(List.of(), List.of(), Optional.empty());

    assertEquals(List.of(noArguments), load.callShapes());
    assertSame(BuiltinPermissionRule.WIZARD_ONLY, load.permission());
    assertEquals(EffectClass.PURE, load.effect());
    assertEquals(BuiltinOwner.SERVER, load.owner());
    assertEquals(List.of(noArguments), taskId.callShapes());
    assertSame(BuiltinPermissionRule.ANY, taskId.permission());
    assertEquals(EffectClass.PURE, taskId.effect());
    assertEquals(BuiltinOwner.VM, taskId.owner());
    assertEquals(List.of(noArguments), ticks.callShapes());
    assertSame(BuiltinPermissionRule.ANY, ticks.permission());
    assertEquals(EffectClass.PURE, ticks.effect());
    assertEquals(BuiltinOwner.VM, ticks.owner());
    assertEquals(List.of(noArguments), seconds.callShapes());
    assertSame(BuiltinPermissionRule.ANY, seconds.permission());
    assertEquals(EffectClass.PURE, seconds.effect());
    assertEquals(BuiltinOwner.VM, seconds.owner());
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(new IntegerValue(0)),
          invoke(catalog, load, List.of(), transaction, 1).value());
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(catalog, load, List.of(), transaction, 2).error());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(catalog, load, List.of(new IntegerValue(1)), transaction, 1).error());

      Result identity =
          catalog.invoke(
              taskId,
              List.of(),
              transaction,
              2,
              new MapValue(Map.of()),
              8_123,
              60_000,
              5,
              new ObjectValue(2),
              2,
              new ListValue(List.of()));
      assertEquals(Optional.of(new IntegerValue(8_123)), identity.value());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(catalog, taskId, List.of(new IntegerValue(1)), transaction, 2).error());

      Result remaining =
          catalog.invoke(
              ticks,
              List.of(),
              transaction,
              2,
              new MapValue(Map.of()),
              0,
              59_321,
              5,
              new ObjectValue(2),
              2,
              new ListValue(List.of()));
      assertEquals(Optional.of(new IntegerValue(59_321)), remaining.value());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(catalog, ticks, List.of(new IntegerValue(1)), transaction, 2).error());

      Result remainingSeconds =
          catalog.invoke(
              seconds,
              List.of(),
              transaction,
              2,
              new MapValue(Map.of()),
              0,
              59_321,
              11,
              new ObjectValue(2),
              2,
              new ListValue(List.of()));
      assertEquals(Optional.of(new IntegerValue(11)), remainingSeconds.value());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(catalog, seconds, List.of(new IntegerValue(1)), transaction, 2).error());
    }
  }

  @Test
  void yinUsesToastThresholdsAndSuspensionContract() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("yin").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(),
                List.of(
                    Set.of(ArgType.NUMBER),
                    Set.of(ArgType.INTEGER),
                    Set.of(ArgType.INTEGER)),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.PURE, spec.effect());
    assertEquals(BuiltinOwner.VM, spec.owner());

    try (WorldTxn transaction = world().begin()) {
      List<MooValue> thresholds =
          List.of(new IntegerValue(0), new IntegerValue(59_999), new IntegerValue(4));
      Result tickYield =
          catalog.invoke(
              spec,
              thresholds,
              transaction,
              2,
              new MapValue(Map.of()),
              17,
              59_998,
              5,
              new ObjectValue(2),
              2,
              new ListValue(List.of()));
      assertEquals(OptionalDouble.of(0), tickYield.delaySeconds());

      Result secondYield =
          catalog.invoke(
              spec,
              thresholds,
              transaction,
              2,
              new MapValue(Map.of()),
              17,
              59_999,
              3,
              new ObjectValue(2),
              2,
              new ListValue(List.of()));
      assertEquals(OptionalDouble.of(0), secondYield.delaySeconds());

      Result noYield =
          catalog.invoke(
              spec,
              thresholds,
              transaction,
              2,
              new MapValue(Map.of()),
              17,
              59_999,
              4,
              new ObjectValue(2),
              2,
              new ListValue(List.of()));
      assertEquals(Optional.of(new IntegerValue(0)), noYield.value());
      assertTrue(noYield.delaySeconds().isEmpty());

      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new IntegerValue(0),
                      new IntegerValue(60_000),
                      new IntegerValue(4)),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new IntegerValue(-1),
                      new IntegerValue(59_999),
                      new IntegerValue(4)),
                  transaction,
                  2)
              .error());
    }
  }

  @Test
  void bootPlayerStagesOneAuthorizedConnectionClosureWithoutTargetValidation() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("boot_player").orElseThrow();

    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.OBJECT)), List.of(), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.DEFERRED_COMMIT, spec.effect());
    assertEquals(BuiltinOwner.CONNECTION, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      Result self =
          invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 2);
      assertEquals(Optional.of(new IntegerValue(0)), self.value());
      assertEquals(OptionalLong.of(2), self.bootPlayerTarget());

      Result wizardMissing =
          invoke(catalog, spec, List.of(new ObjectValue(99)), transaction, 1);
      assertEquals(Optional.of(new IntegerValue(0)), wizardMissing.value());
      assertEquals(OptionalLong.of(99), wizardMissing.bootPlayerTarget());
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(catalog, spec, List.of(new ObjectValue(1)), transaction, 2).error());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(catalog, spec, List.of(new IntegerValue(2)), transaction, 2).error());
    }
  }

  @Test
  void listenBindsTheWizardSelectedHandlerPortAndPrintOption() {
    RecordingListener listener = new RecordingListener();
    BuiltinCatalog catalog = new BuiltinCatalog(listener);
    BuiltinSpec spec = catalog.spec("listen").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.OBJECT), Set.of(ArgType.ANY)),
                List.of(Set.of(ArgType.MAP)),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.WIZARD_ONLY, spec.permission());
    assertEquals(EffectClass.IRREVOCABLE, spec.effect());
    assertEquals(BuiltinOwner.SERVER, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      Result result =
          invoke(
              catalog,
              spec,
              List.of(
                  new ObjectValue(2),
                  new IntegerValue(12345),
                  new MapValue(Map.of(string("print-messages"), new IntegerValue(1)))),
              transaction,
              1);

      assertEquals(Optional.of(new IntegerValue(12345)), result.value());
      assertEquals(2, listener.handler);
      assertEquals(12345, listener.port);
      assertTrue(listener.printMessages);
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new IntegerValue(23456)),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(99), new IntegerValue(23456)),
                  transaction,
                  1)
              .error());
    }
  }

  @Test
  void setVerbInfoReplacesOwnerFlagsAndNamesWithToastPermissions() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("set_verb_info").orElseThrow();

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
    try (WorldTxn transaction = world().begin()) {
      assertEquals(1, transaction.addVerb(2, "old-name", 2, 3, -1));
      Result result =
          invoke(
              catalog,
              spec,
              List.of(
                  new ObjectValue(2),
                  string("old-name"),
                  new ListValue(List.of(new ObjectValue(2), string("rxd"), string("  new-name")))),
              transaction,
              2);

      assertEquals(Optional.of(new IntegerValue(0)), result.value());
      WorldVerb updated = transaction.verb(2, 0).orElseThrow();
      assertEquals("new-name", updated.names());
      assertEquals(2, updated.owner());
      assertEquals(13, updated.permissions() & 15);
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      new IntegerValue(1),
                      new ListValue(List.of(new ObjectValue(1), string("r"), string("new-name")))),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(new IntegerValue(0)),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      new IntegerValue(1),
                      new ListValue(
                          List.of(new ObjectValue(1), string("rxd"), string("new-name")))),
                  transaction,
                  1)
              .value());
      assertEquals(1, transaction.verb(2, 0).orElseThrow().owner());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      new IntegerValue(1),
                      new ListValue(List.of(new ObjectValue(99), string("r"), string("new-name")))),
                  transaction,
                  1)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      new IntegerValue(1),
                      new ListValue(List.of(new ObjectValue(1), string("q"), string("new-name")))),
                  transaction,
                  1)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_VERBNF),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      new IntegerValue(2),
                      new ListValue(List.of(new ObjectValue(2), string("r"), string("new-name")))),
                  transaction,
                  1)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new IntegerValue(2),
                      new IntegerValue(0),
                      new ListValue(List.of(new ObjectValue(1), string("r"), string("new-name")))),
                  transaction,
                  1)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      string("new-name"),
                      new ListValue(List.of(new ObjectValue(2), new IntegerValue(1), string("x")))),
                  transaction,
                  1)
              .error());
    }
  }

  @Test
  void setVerbArgsReplacesOnlyArgumentSpecificationsWithToastValidationOrder() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("set_verb_args").orElseThrow();

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
    try (WorldTxn transaction = world().begin()) {
      assertEquals(1, transaction.addVerb(2, "target", 2, 3, 7));
      Result result =
          invoke(
              catalog,
              spec,
              List.of(
                  new ObjectValue(2),
                  string("target"),
                  new ListValue(List.of(string("this"), string("none"), string("this")))),
              transaction,
              2);

      assertEquals(Optional.of(new IntegerValue(0)), result.value());
      WorldVerb updated = transaction.verb(2, 0).orElseThrow();
      assertEquals("target", updated.names());
      assertEquals(2, updated.owner());
      assertEquals(3 | (2 << 4) | (2 << 6), updated.permissions());
      assertEquals(-1, updated.preposition());
      assertEquals("", updated.programSource());

      assertEquals(
          Optional.of(new IntegerValue(0)),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      new IntegerValue(1),
                      new ListValue(List.of(string("any"), string("with/using"), string("none")))),
                  transaction,
                  2)
              .value());
      updated = transaction.verb(2, 0).orElseThrow();
      assertEquals(3 | (1 << 4), updated.permissions());
      assertEquals(0, updated.preposition());

      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      string("missing"),
                      new ListValue(List.of(string("this"), new IntegerValue(0), string("this")))),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      string("missing"),
                      new ListValue(List.of(string("this"), string("nowhere"), string("this")))),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      string("target"),
                      new ListValue(List.of(string("this"), string("+1"), string("this")))),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_VERBNF),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      string("missing"),
                      new ListValue(List.of(string("this"), string("none"), string("this")))),
                  transaction,
                  2)
              .error());

      assertEquals(2, transaction.addVerb(2, "private", 1, 1, -1));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      string("private"),
                      new ListValue(List.of(string("this"), string("none"), string("this")))),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  spec,
                  List.of(
                      new IntegerValue(2),
                      new IntegerValue(0),
                      new ListValue(List.of(string("this"), string("none"), string("this")))),
                  transaction,
                  2)
              .error());
    }
  }

  @Test
  void verbInfoAndVerbArgsReturnCanonicalLocalMetadataWithToastReadAuthority() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec infoSpec = catalog.spec("verb_info").orElseThrow();
    BuiltinSpec argsSpec = catalog.spec("verb_args").orElseThrow();

    CallShape twoAny =
        new CallShape(
            List.of(Set.of(ArgType.ANY), Set.of(ArgType.ANY)),
            List.of(),
            Optional.empty());
    assertEquals(List.of(twoAny), infoSpec.callShapes());
    assertEquals(List.of(twoAny), argsSpec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, infoSpec.permission());
    assertSame(BuiltinPermissionRule.ANY, argsSpec.permission());
    assertEquals(EffectClass.TRANSACTION_READ, infoSpec.effect());
    assertEquals(EffectClass.TRANSACTION_READ, argsSpec.effect());
    assertEquals(BuiltinOwner.WORLD, infoSpec.owner());
    assertEquals(BuiltinOwner.WORLD, argsSpec.owner());

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
            List.of(
                new WorldVerb("alpha aliases", 2, 15 | (2 << 4) | (1 << 6), 0, "return 1;"),
                new WorldVerb("private", 1, 0, -1, "return 2;")),
            List.of());
    try (WorldTxn transaction =
        new WorldTxn(List.of(), List.of(wizard, programmer, target)).begin()) {
      assertEquals(
          Optional.of(
              new ListValue(List.of(new ObjectValue(2), string("rwxd"), string("alpha aliases")))),
          invoke(
                  catalog,
                  infoSpec,
                  List.of(new ObjectValue(3), string("alpha")),
                  transaction,
                  2)
              .value());
      assertEquals(
          Optional.of(
              new ListValue(List.of(string("this"), string("with/using"), string("any")))),
          invoke(
                  catalog,
                  argsSpec,
                  List.of(new ObjectValue(3), new IntegerValue(1)),
                  transaction,
                  2)
              .value());
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(
                  catalog,
                  infoSpec,
                  List.of(new ObjectValue(3), string("private")),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_VERBNF),
          invoke(
                  catalog,
                  argsSpec,
                  List.of(new ObjectValue(3), string("missing")),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(
                  catalog,
                  infoSpec,
                  List.of(new IntegerValue(3), new IntegerValue(0)),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  argsSpec,
                  List.of(new ObjectValue(3), new IntegerValue(0)),
                  transaction,
                  2)
              .error());
    }
  }

  @Test
  void deleteVerbRequiresObjectWriteAuthorityAndRemovesOneLocalDefinition() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("delete_verb").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.ANY), Set.of(ArgType.ANY)),
                List.of(),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.TRANSACTION_WRITE, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());

    WorldObject wizard =
        new WorldObject(1, "Wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject programmer =
        new WorldObject(2, "Programmer", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject writable =
        new WorldObject(
            3,
            "Writable",
            0,
            2,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(
                new WorldVerb("first", 1, 0, -1, "return 1;"),
                new WorldVerb("second", 1, 0, -1, "return 2;")),
            List.of());
    WorldObject denied =
        new WorldObject(4, "Denied", 0, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction =
        new WorldTxn(List.of(), List.of(wizard, programmer, writable, denied)).begin()) {
      assertEquals(
          Optional.of(new IntegerValue(0)),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(3), string("first")),
                  transaction,
                  2)
              .value());
      assertEquals("second", transaction.verb(3, 0).orElseThrow().names());
      assertEquals(Optional.empty(), transaction.verb(3, 1));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(4), string("missing")),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_VERBNF),
          invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(3), string("missing")),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(3), new IntegerValue(0)),
                  transaction,
                  2)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(3), string("first")),
                  transaction,
                  2)
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
  void minUsesTheCanonicalPureVmContractAndSelectsTheSmallestHomogeneousNumber() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    CallShape shape =
        new CallShape(
            List.of(Set.of(ArgType.NUMBER)),
            List.of(),
            Optional.of(Set.of(ArgType.NUMBER)));
    assertPureVmContract(catalog, "min", shape);

    try (WorldTxn transaction = world().begin()) {
      BuiltinSpec spec = catalog.spec("min").orElseThrow();
      assertEquals(
          Optional.of(new IntegerValue(1)),
          invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(3), new IntegerValue(1), new IntegerValue(7)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(new FloatValue(1.5)),
          invoke(
                  catalog,
                  spec,
                  List.of(new FloatValue(3.5), new FloatValue(1.5)),
                  transaction,
                  1)
              .value());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(1), new FloatValue(2.0)),
                  transaction,
                  1)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(catalog, spec, List.of(string("x")), transaction, 1).error());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(catalog, spec, List.of(), transaction, 1).error());
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
  void absPreservesTheNumericTypeAndReturnsItsMagnitude() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    assertPureVmContract(
        catalog,
        "abs",
        new CallShape(List.of(Set.of(ArgType.NUMBER)), List.of(), Optional.empty()));

    try (WorldTxn transaction = world().begin()) {
      BuiltinSpec spec = catalog.spec("abs").orElseThrow();
      assertEquals(
          Optional.of(new IntegerValue(7)),
          invoke(catalog, spec, List.of(new IntegerValue(-7)), transaction, 1).value());
      assertEquals(
          Optional.of(new FloatValue(7.5)),
          invoke(catalog, spec, List.of(new FloatValue(-7.5)), transaction, 1).value());
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
    for (String name : List.of("setadd", "setremove")) {
      assertPureVmContract(
          catalog, name, new CallShape(List.of(list, any), List.of(), Optional.empty()));
    }
    assertPureVmContract(
        catalog, "reverse", new CallShape(List.of(any), List.of(), Optional.empty()));
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
  void setRemoveUsesRecursiveCaseInsensitiveMooEqualityAndRemovesOnlyTheFirstMatch() {
    BuiltinCatalog catalog = new BuiltinCatalog();
    BuiltinSpec spec = catalog.spec("setremove").orElseThrow();
    MapValue first =
        new MapValue(
            Map.of(
                string("Key"),
                new ListValue(List.of(string("Value"), BooleanValue.TRUE))));
    MapValue duplicate =
        new MapValue(
            Map.of(
                string("KEY"),
                new ListValue(List.of(string("VALUE"), new IntegerValue(1)))));
    MapValue sought =
        new MapValue(
            Map.of(
                string("key"),
                new ListValue(List.of(string("value"), new IntegerValue(1)))));
    ListValue source = new ListValue(List.of(first, new IntegerValue(7), duplicate));
    ListValue absent = new ListValue(List.of(string("present")));

    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(new ListValue(List.of(new IntegerValue(7), duplicate))),
          invoke(catalog, spec, List.of(source, sought), transaction, 1).value());
      assertSame(
          absent,
          invoke(catalog, spec, List.of(absent, string("missing")), transaction, 1)
              .value()
              .orElseThrow());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(catalog, spec, List.of(), transaction, 1).error());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          invoke(
                  catalog,
                  spec,
                  List.of(absent, new IntegerValue(1), new IntegerValue(2)),
                  transaction,
                  1)
              .error());
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(1), new IntegerValue(1)),
                  transaction,
                  1)
              .error());
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
        0,
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

  private static final class RecordingRandom extends Random {
    private static final long serialVersionUID = 1L;

    private int seedCalls;

    @Override
    public synchronized void setSeed(long seed) {
      super.setSeed(seed);
      seedCalls++;
    }

    private int seedCalls() {
      return seedCalls;
    }

    private void resetSeedCalls() {
      seedCalls = 0;
    }
  }

  private static final class RecordingListener implements BuiltinCatalog.ListenerControl {
    private long handler;
    private int port;
    private boolean printMessages;

    @Override
    public int listen(long handler, int port, boolean printMessages) {
      this.handler = handler;
      this.port = port;
      this.printMessages = printMessages;
      return port;
    }

    @Override
    public boolean unlisten(int port) {
      return false;
    }

    @Override
    public void writeConnection(long connectionId, List<String> output) {}

    @Override
    public void bootConnection(long connectionId, List<String> output) {}

    @Override
    public void setConnectionBinary(long connectionId, boolean binary) {}

    @Override
    public void shutdown() {}
  }
}
