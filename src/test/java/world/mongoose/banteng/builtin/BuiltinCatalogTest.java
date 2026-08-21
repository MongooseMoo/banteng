package world.mongoose.banteng.builtin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import world.mongoose.banteng.builtin.BuiltinCatalog.ConnectionOption;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.server.ConnectionRegistry;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.BooleanValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import world.mongoose.banteng.value.ValueSemantics;
import world.mongoose.banteng.world.ObjectFlags;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuiltinCatalogTest {
  private static final Set<String> REACHABLE_NAMES =
      Set.of(
          "abs",
          "acos",
          "acosh",
          "add_property",
          "add_verb",
          "all_members",
          "ancestors",
          "asin",
          "asinh",
          "atan",
          "atan2",
          "atanh",
          "argon2",
          "argon2_verify",
          "boot_player",
          "buffered_output_length",
          "call_function",
          "caller_perms",
          "callers",
          "children",
          "chparent",
          "chparents",
          "cbrt",
          "ceil",
          "chr",
          "connection_info",
          "connection_name",
          "connection_name_lookup",
          "connection_options",
          "connected_players",
          "cos",
          "cosh",
          "create",
          "crypt",
          "ctime",
          "clear_property",
          "db_disk_size",
          "decode_binary",
          "delete_verb",
          "delete_property",
          "disassemble",
          "distance",
          "dump_database",
          "encode_binary",
          "equal",
          "eval",
          "exp",
          "explode",
          "file_chmod",
          "file_close",
          "file_count_lines",
          "file_eof",
          "file_flush",
          "file_grep",
          "file_handles",
          "file_last_access",
          "file_last_change",
          "file_last_modify",
          "file_list",
          "file_mkdir",
          "file_mode",
          "file_name",
          "file_open",
          "file_openmode",
          "file_read",
          "file_readline",
          "file_readlines",
          "file_remove",
          "file_rename",
          "file_rmdir",
          "file_seek",
          "file_size",
          "file_stat",
          "file_tell",
          "file_type",
          "file_write",
          "file_writeline",
          "floatstr",
          "floor",
          "flush_input",
          "force_input",
          "frandom",
          "ftime",
          "function_info",
          "generate_json",
          "gc_stats",
          "index",
          "is_player",
          "is_clear_property",
          "length",
          "kill_task",
          "listen",
          "listeners",
          "load_server_options",
          "locate_by_name",
          "locations",
          "log",
          "log10",
          "log_cache_stats",
          "listappend",
          "listdelete",
          "listinsert",
          "listset",
          "mapdelete",
          "maphaskey",
          "mapkeys",
          "mapvalues",
          "max",
          "max_object",
          "memory_usage",
          "min",
          "move",
          "new_waif",
          "next_recycled_object",
          "notify",
          "open_network_connection",
          "output_delimiters",
          "owned_objects",
          "parent",
          "parents",
          "parse_ansi",
          "parse_json",
          "pcre_cache_stats",
          "pcre_match",
          "pcre_replace",
          "properties",
          "property_info",
          "queue_info",
          "queued_tasks",
          "random",
          "random_bytes",
          "reseed_random",
          "raise",
          "read",
          "recreate",
          "recycle",
          "recycled_objects",
          "reverse",
          "relative_heading",
          "remove_ansi",
          "reset_max_object",
          "resume",
          "rindex",
          "round",
          "run_gc",
          "seconds_left",
          "set_connection_option",
          "set_player_flag",
          "set_task_perms",
          "set_thread_mode",
          "set_verb_args",
          "set_verb_code",
          "set_verb_info",
          "setadd",
          "setremove",
          "server_log",
          "server_version",
          "shutdown",
          "simplex_noise",
          "sin",
          "sinh",
          "sort",
          "sqlite_close",
          "sqlite_execute",
          "sqlite_handles",
          "sqlite_info",
          "sqlite_interrupt",
          "sqlite_last_insert_row_id",
          "sqlite_limit",
          "sqlite_open",
          "sqlite_query",
          "sqrt",
          "strcmp",
          "strsub",
          "strtr",
          "suspend",
          "switch_player",
          "tan",
          "tanh",
          "task_id",
          "task_perms",
          "task_stack",
          "thread_pool",
          "threads",
          "ticks_left",
          "time",
          "tofloat",
          "toint",
          "toliteral",
          "toobj",
          "tostr",
          "trunc",
          "typeof",
          "unlisten",
          "usage",
          "valid",
          "value_bytes",
          "verbs",
          "waif_stats",
          "verb_args",
          "verb_cache_stats",
          "verb_code",
          "verb_info",
          "yin");

  @Test
  void argon2VerificationSeparatesMismatchFromInternalFailure(@TempDir Path directory)
      throws Exception {
    List<MooValue> arguments =
        List.of(
            string("$argon2id$v=19$m=8,t=1,p=1$c2FsdA$AQID"), string("password"));
    Path logFile = directory.resolve("server.log");
    try (ServerLog log =
        ServerLog.open(System.Logger.Level.ERROR, Optional.of(logFile))) {
      BuiltinResult mismatch =
          BuiltinCatalog.argon2Verify(
              arguments,
              log,
              (password, salt, iterations, memory, parallelism, length) -> new byte[length]);
      assertEquals(Optional.of(new IntegerValue(0)), value(mismatch));

      BuiltinResult internalFailure =
          BuiltinCatalog.argon2Verify(
              arguments,
              log,
              (password, salt, iterations, memory, parallelism, length) -> {
                throw new IllegalStateException("engine failed");
              });
      assertEquals(Optional.of(ErrorValue.E_QUOTA), error(internalFailure));
    }

    String logged = Files.readString(logFile);
    assertTrue(logged.contains("ARGON2 VERIFY: internal failure: IllegalStateException"));
    assertFalse(logged.contains("password"));
  }

  @Test
  void registersEveryReachableBuiltinExactlyOnceWithCompleteContracts() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
      assertTrue(setup.writeWaifProperty(waif, "marker", new IntegerValue(42)).isOk());
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
            value(invoke(catalog, spec, List.of(entry.getKey()), transaction, 1)),
            entry.getKey().type().name());
      }

      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(catalog, spec, List.of(), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(1), new IntegerValue(2)),
                  transaction,
                  1)
              ));
      assertEquals(transactionBefore, transaction.snapshot());
    }
    assertEquals(committedBefore, root.snapshot());
  }

  @Test
  void reseedRandomUsesEntropyToMutateTheSharedWizardOnlyGenerator()
      throws ReflectiveOperationException {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
    RecordingRandom floatingRandom = new RecordingRandom();
    floatingRandom.resetSeedCalls();
    Field floatingRandomField = BuiltinCatalog.class.getDeclaredField("floatingRandom");
    floatingRandomField.setAccessible(true);
    floatingRandomField.set(catalog, floatingRandom);

    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, spec, List.of(), transaction, 2)));
      assertEquals(0, recordingRandom.seedCalls());

      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(catalog, spec, List.of(), transaction, 1)));
      assertEquals(1, recordingRandom.seedCalls());
      assertEquals(0, floatingRandom.seedCalls());
    }
  }

  @Test
  void explodePreservesToastByteDelimiterEmptyFieldAndErrorSemantics() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
          value(invoke(catalog, spec, List.of(string(" alpha  beta ")), transaction, 1)));
      assertEquals(
          Optional.of(new ListValue(List.of(string("a"), string("b")))),
          value(invoke(
                  catalog,
                  spec,
                  List.of(string("::a::b:"), string(":"), new IntegerValue(0)),
                  transaction,
                  1)
              ));
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
          value(invoke(
                  catalog,
                  spec,
                  List.of(string("::a::b:"), string(":"), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new ListValue(List.of(string("a"), string("b;c")))),
          value(invoke(catalog, spec, List.of(string("a,b;c"), string(",;")), transaction, 1)));
      assertEquals(
          Optional.of(new ListValue(List.of(string("a"), string("b")))),
          value(invoke(catalog, spec, List.of(string("a b"), string("")), transaction, 1)));
      assertEquals(
          Optional.of(new ListValue(List.of())),
          value(invoke(catalog, spec, List.of(string("")), transaction, 1)));
      assertEquals(
          Optional.of(new ListValue(List.of(string("")))),
          value(invoke(
                  catalog,
                  spec,
                  List.of(string(""), string(":"), new IntegerValue(1)),
                  transaction,
                  1)
              ));

      StringValue highBitSource =
          StringValue.of(new byte[] {(byte) 0xe9, (byte) ':', (byte) 0xff});
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      StringValue.of(new byte[] {(byte) 0xe9}),
                      StringValue.of(new byte[] {(byte) 0xff})))),
          value(invoke(catalog, spec, List.of(highBitSource, string(":")), transaction, 1)));

      assertEquals(
          Optional.of(ErrorValue.E_ARGS), error(invoke(catalog, spec, List.of(), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(
                  catalog,
                  spec,
                  List.of(string("a"), string(":"), new IntegerValue(0), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, spec, List.of(string("a"), new IntegerValue(1)), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, spec, List.of(string("a"), string(":"), string("1")), transaction, 1)
              ));

      BuiltinResult functionInfo =
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
          value(functionInfo));
    }
  }

  @Test
  void reversePreservesToastBytewiseShallowAndUnsupportedValueSemantics() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("reverse").orElseThrow();
    WorldTxn root = world();
    var committedBefore = root.snapshot();

    try (WorldTxn transaction = root.begin()) {
      var transactionBefore = transaction.snapshot();

      StringValue raw = StringValue.of(new byte[] {0x41, (byte) 0xe9, (byte) 0xff});
      StringValue rawReversed =
          (StringValue)
              value(invoke(catalog, spec, List.of(raw), transaction, 1)).orElseThrow();
      assertArrayEquals(new byte[] {(byte) 0xff, (byte) 0xe9, 0x41}, rawReversed.bytes());
      assertEquals(
          Optional.of(string("")),
          value(invoke(catalog, spec, List.of(string("")), transaction, 1)));
      assertEquals(
          Optional.of(StringValue.of(new byte[] {(byte) 0xe9})),
          value(invoke(
                  catalog,
                  spec,
                  List.of(StringValue.of(new byte[] {(byte) 0xe9})),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(string("olleh")),
          value(invoke(catalog, spec, List.of(string("hello")), transaction, 1)));
      assertEquals(
          Optional.of(string("abcba")),
          value(invoke(catalog, spec, List.of(string("abcba")), transaction, 1)));

      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(new IntegerValue(3), new IntegerValue(2), new IntegerValue(1)))),
          value(invoke(
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
              ));
      assertEquals(
          Optional.of(new ListValue(List.of())),
          value(invoke(catalog, spec, List.of(new ListValue(List.of())), transaction, 1)));
      assertEquals(
          Optional.of(new ListValue(List.of(new IntegerValue(42)))),
          value(invoke(
                  catalog,
                  spec,
                  List.of(new ListValue(List.of(new IntegerValue(42)))),
                  transaction,
                  1)
              ));

      ListValue nested = new ListValue(List.of(new IntegerValue(2), new IntegerValue(3)));
      ListValue mixed =
          new ListValue(
              List.of(string("a"), new IntegerValue(1), new ObjectValue(0), nested));
      ListValue mixedReversed =
          (ListValue)
              value(invoke(catalog, spec, List.of(mixed), transaction, 1)).orElseThrow();
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
        BuiltinResult result = invoke(catalog, spec, List.of(unsupported), transaction, 1);
        assertEquals(Optional.of(ErrorValue.E_INVARG), error(result));
        assertTrue(error(result).filter(ErrorValue.E_TYPE::equals).isEmpty());
      }

      assertEquals(
          Optional.of(ErrorValue.E_ARGS), error(invoke(catalog, spec, List.of(), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(catalog, spec, List.of(string("a"), string("b")), transaction, 1)));

      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      string("reverse"),
                      new IntegerValue(1),
                      new IntegerValue(1),
                      new ListValue(List.of(new IntegerValue(-1)))))),
          value(invoke(
                  catalog,
                  catalog.spec("function_info").orElseThrow(),
                  List.of(string("reverse")),
                  transaction,
                  1)
              ));

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
        new BuiltinCatalog(
            BuiltinHosts.builder()
                .queuedTasks(call -> BuiltinResult.value(tasks))
                .build());
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
      assertEquals(Optional.of(tasks), value(invoke(catalog, spec, List.of(), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, spec, List.of(string("bad")), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(0), new IntegerValue(0), new IntegerValue(0)),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void timeReturnsCurrentEpochSecondsThroughTheIrrevocableVmOwner() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("time").orElseThrow();
    long before = Instant.now().getEpochSecond();

    try (WorldTxn transaction = world().begin()) {
      BuiltinResult result = invoke(catalog, spec, List.of(), transaction, 1);
      long after = Instant.now().getEpochSecond();
      long value = ((IntegerValue) value(result).orElseThrow()).value();

      assertEquals(
          List.of(new CallShape(List.of(), List.of(), Optional.empty())), spec.callShapes());
      assertSame(BuiltinPermissionRule.ANY, spec.permission());
      assertEquals(EffectClass.IRREVOCABLE, spec.effect());
      assertEquals(BuiltinOwner.VM, spec.owner());
      assertTrue(value >= before && value <= after);
    }
  }

  @Test
  void switchPlayerAcceptsAnOptionalIntegerSilentFlagAndRejectsOtherTypes() {
    ConnectionRegistry connections = new ConnectionRegistry();
    BuiltinCatalog catalog =
        new BuiltinCatalog(BuiltinHosts.builder().connections(() -> connections).build());
    BuiltinSpec spec = catalog.spec("switch_player").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.OBJECT), Set.of(ArgType.OBJECT)),
                List.of(Set.of(ArgType.INTEGER)),
                Optional.empty())),
        spec.callShapes());
    WorldObject wizard =
        new WorldObject(1, "Wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject oldPlayer =
        new WorldObject(2, "Old", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject newPlayer =
        new WorldObject(3, "New", 0, 3, -1, -1, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction =
        new WorldTxn(List.of(2L, 3L), List.of(wizard, oldPlayer, newPlayer)).begin()) {
      connections.openConnection(-17, new MapValue(Map.of()));
      assertTrue(connections.switchConnectionPlayer(-17, 2));
      assertEquals(
          OptionalLong.of(3),
          switchedPlayer(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new ObjectValue(3), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new ObjectValue(3), string("bad")),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new ObjectValue(3)),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new ObjectValue(2)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(999), new ObjectValue(3)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new ObjectValue(999)),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void killTaskUsesTheRegisteredTaskOwnerWithOneIntegerArgument() {
    BuiltinCatalog catalog =
        new BuiltinCatalog(
            BuiltinHosts.builder()
                .queuedTasks(call -> BuiltinResult.value(new ListValue(List.of())))
                .killTask(call -> BuiltinResult.value(new IntegerValue(23)))
                .read(call -> BuiltinResult.error(ErrorValue.E_INVARG))
                .threadPool(call -> BuiltinResult.error(ErrorValue.E_INVARG))
                .threads(call -> BuiltinResult.value(new ListValue(List.of())))
                .build());
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
          value(invoke(catalog, spec, List.of(new IntegerValue(17)), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, spec, List.of(string("bad")), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(catalog, spec, List.of(), transaction, 1)));
    }
  }

  @Test
  void readDeclaresSuspendingConnectionContractAndDeniesAnUnrelatedProgrammer() {
    BuiltinCatalog catalog =
        new BuiltinCatalog(
            BuiltinHosts.builder()
                .queuedTasks(call -> BuiltinResult.value(new ListValue(List.of())))
                .killTask(call -> BuiltinResult.error(ErrorValue.E_INVARG))
                .read(call -> BuiltinResult.value(new IntegerValue(0)))
                .threadPool(call -> BuiltinResult.error(ErrorValue.E_INVARG))
                .threads(call -> BuiltinResult.value(new ListValue(List.of())))
                .build());
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
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(1), new IntegerValue(1)),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(1), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, spec, List.of(), transaction, 2)));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(catalog, spec, List.of(), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(1), new IntegerValue(1), new IntegerValue(1)),
                  transaction,
                  2)
              ));
    }
  }

  @Test
  void connectionInfoReadsTheLiveConnectionWithToastPermissions() {
    ConnectionRegistry connections = new ConnectionRegistry();
    BuiltinCatalog catalog =
        new BuiltinCatalog(BuiltinHosts.builder().connections(() -> connections).build());
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
      connections.openConnection(-2, info);
      connections.switchConnectionPlayer(-2, 2);
      connections.openConnection(-3, info);
      connections.switchConnectionPlayer(-3, 1);

      assertEquals(
          Optional.of(info),
          value(invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 2)));
      assertEquals(
          Optional.of(info),
          value(invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, spec, List.of(new ObjectValue(1)), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, spec, List.of(new ObjectValue(99)), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, spec, List.of(new IntegerValue(2)), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS), error(invoke(catalog, spec, List.of(), transaction, 1)));
    }
  }

  @Test
  void connectionNameUsesSavedRemoteAddressAndToastLegacyFormatting() {
    ConnectionRegistry connections = new ConnectionRegistry();
    BuiltinCatalog catalog =
        new BuiltinCatalog(BuiltinHosts.builder().connections(() -> connections).build());
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
      connections.openConnection(-2, info);
      connections.switchConnectionPlayer(-2, 2);

      assertEquals(
          Optional.of(string("client.example")),
          value(invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 2)));
      assertEquals(
          Optional.of(string("198.51.100.25")),
          value(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new IntegerValue(1)),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(
              string("port 7777 from client.example [198.51.100.25], port 4242")),
          value(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new IntegerValue(0)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, spec, List.of(new ObjectValue(1)), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, spec, List.of(new ObjectValue(99)), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, spec, List.of(new ObjectValue(2), string("0")), transaction, 2)));
    }
  }

  @Test
  void connectionNameLookupUsesToastSignaturePermissionsAndHostWork() throws Exception {
    ConnectionRegistry connections = new ConnectionRegistry();
    BuiltinCatalog catalog =
        new BuiltinCatalog(
            BuiltinHosts.builder()
                .connections(() -> connections)
                .connectionNameLookup(
                    call -> BuiltinResult.hostWork(() -> BuiltinResult.value(string("resolved.example"))))
                .build());
    BuiltinSpec spec = catalog.spec("connection_name_lookup").orElseThrow();
    MapValue info =
        new MapValue(
            Map.of(
                string("destination_address"), string("198.51.100.25"),
                string("destination_ip"), string("198.51.100.25")));

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.OBJECT)),
                List.of(Set.of(ArgType.ANY)),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.SUSPENDING_HOST, spec.effect());
    assertEquals(BuiltinOwner.CONNECTION, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      connections.openConnection(-2, info);
      connections.switchConnectionPlayer(-2, 2);
      connections.openConnection(-3, info);
      connections.switchConnectionPlayer(-3, 1);

      BuiltinResult self =
          invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 2);
      assertEquals(
          Optional.of(string("resolved.example")),
          value(hostWork(self).orElseThrow().call()));
      BuiltinResult wizard =
          invoke(
              catalog,
              spec,
              List.of(new ObjectValue(2), new IntegerValue(1)),
              transaction,
              1);
      assertEquals(
          Optional.of(string("resolved.example")),
          value(hostWork(wizard).orElseThrow().call()));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, spec, List.of(new ObjectValue(1)), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, spec, List.of(new ObjectValue(99)), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, spec, List.of(new IntegerValue(2)), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(catalog, spec, List.of(), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new IntegerValue(1), new IntegerValue(1)),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void connectedPlayersReturnsNewestConnectionsWithOptionalNegativePlayers() {
    ConnectionRegistry connections = new ConnectionRegistry();
    BuiltinCatalog catalog =
        new BuiltinCatalog(BuiltinHosts.builder().connections(() -> connections).build());
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
      connections.openConnection(-2);
      connections.switchConnectionPlayer(-2, 2);
      connections.openConnection(-3);

      assertEquals(
          Optional.of(new ListValue(List.of(new ObjectValue(2)))),
          value(invoke(catalog, spec, List.of(), transaction, 2)));
      assertEquals(
          Optional.of(new ListValue(List.of(new ObjectValue(-3), new ObjectValue(2)))),
          value(invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(1), new IntegerValue(1)),
                  transaction,
                  2)
              ));
    }
  }

  @Test
  void bufferedOutputLengthUsesToastSignaturePermissionsAndLiveConnectionOwner() {
    RecordingListener listener = new RecordingListener();
    listener.bufferedOutputLength = 37;
    ConnectionRegistry connections = new ConnectionRegistry();
    BuiltinCatalog catalog =
        new BuiltinCatalog(
            listener, BuiltinHosts.builder().connections(() -> connections).build());
    BuiltinSpec spec = catalog.spec("buffered_output_length").orElseThrow();

    assertEquals(
        List.of(new CallShape(List.of(), List.of(Set.of(ArgType.OBJECT)), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.EXTERNAL_READ, spec.effect());
    assertEquals(BuiltinOwner.CONNECTION, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      connections.openConnection(-2);
      connections.switchConnectionPlayer(-2, 2);
      connections.openConnection(-3);
      connections.switchConnectionPlayer(-3, 1);

      assertEquals(
          Optional.of(new IntegerValue(65_536)),
          value(invoke(catalog, spec, List.of(), transaction, 2)));
      assertEquals(
          Optional.of(new IntegerValue(37)),
          value(invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 2)));
      assertEquals(-2, listener.bufferedOutputConnectionId);
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, spec, List.of(new ObjectValue(1)), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, spec, List.of(new ObjectValue(99)), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, spec, List.of(string("x")), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new ObjectValue(2)),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void notifyPreservesToastTargetPermissionAndFlushFlags() {
    ConnectionRegistry connections = new ConnectionRegistry();
    BuiltinCatalog catalog =
        new BuiltinCatalog(BuiltinHosts.builder().connections(() -> connections).build());
    BuiltinSpec spec = catalog.spec("notify").orElseThrow();

    try (WorldTxn transaction = world().begin()) {
      connections.openConnection(-2);
      connections.switchConnectionPlayer(-2, 2);
      connections.openConnection(-3);
      connections.switchConnectionPlayer(-3, 1);

      BuiltinResult.Notify notification =
          assertInstanceOf(
              BuiltinResult.Notify.class,
              invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      string("queued"),
                      new IntegerValue(1),
                      new IntegerValue(1)),
                  transaction,
                  2));
      assertEquals(-2, notification.connectionId());
      assertEquals("queued", notification.line());
      assertTrue(notification.noFlush());
      assertTrue(notification.noNewline());

      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(
              invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(1), string("forbidden")),
                  transaction,
                  2)));
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(
              invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(99), string("offline")),
                  transaction,
                  99)));
    }
  }

  @Test
  void callFunctionRecursesThroughTheOneManifestWithoutAddingAFrame() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("call_function").orElseThrow();
    ListValue callers = new ListValue(List.of(new ListValue(List.of(new ObjectValue(7)))));

    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.STRING)), List.of(), Optional.of(Set.of(ArgType.ANY)))),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.IRREVOCABLE, spec.effect());
    assertEquals(BuiltinOwner.VM, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  spec,
                  List.of(string("typeof"), new IntegerValue(42)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(callers),
          value(catalog.invoke(
                  spec,
                  List.of(string("callers")),
                  transaction,
                  1,
                  new IntegerValue(0),
                  9,
                  100,
                  5,
                  new ObjectValue(1),
                  1,
                  callers)
              ));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  spec,
                  List.of(string("call_function"), string("typeof"), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(catalog, spec, List.of(string("typeof")), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, spec, List.of(string("")), transaction, 1)));
    }
  }

  @Test
  void setConnectionOptionStagesOneAuthorizedDeferredConnectionMutation() {
    ConnectionRegistry connections = new ConnectionRegistry();
    BuiltinCatalog catalog =
        new BuiltinCatalog(BuiltinHosts.builder().connections(() -> connections).build());
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
      connections.openConnection(-2, info);
      connections.switchConnectionPlayer(-2, 2);
      connections.openConnection(-3, info);
      connections.switchConnectionPlayer(-3, 1);

      BuiltinResult held =
          invoke(
              catalog,
              spec,
              List.of(new ObjectValue(2), string("HoLd-InPuT"), new IntegerValue(1)),
              transaction,
              2);
      assertEquals(
          new BuiltinResult.SetConnectionOption(
              2, ConnectionOption.HOLD_INPUT, new IntegerValue(1)),
          held);

      BuiltinResult flush =
          invoke(
              catalog,
              spec,
              List.of(new ObjectValue(2), string("flush-command"), string(".flush")),
              transaction,
              1);
      assertEquals(
          new BuiltinResult.SetConnectionOption(
              2, ConnectionOption.FLUSH_COMMAND, string(".flush")),
          flush);
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(1), string("hold-input"), new IntegerValue(1)),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(99), string("hold-input"), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), string("unknown"), new IntegerValue(1)),
                  transaction,
                  2)
              ));
    }
  }

  @Test
  void forceInputStagesToastCompatibleConnectionInputWithoutTargetValidation() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
      BuiltinResult negative =
          invoke(
              catalog,
              spec,
              List.of(new ObjectValue(-2), string("audit-queue-login")),
              transaction,
              1);
      assertEquals(
          new BuiltinResult.ForceInput(-2, "audit-queue-login"), negative);

      BuiltinResult self =
          invoke(
              catalog,
              spec,
              List.of(new ObjectValue(2), string("auditq"), new IntegerValue(1)),
              transaction,
              2);
      assertEquals(new BuiltinResult.ForceInput(2, "auditq"), self);
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(1), string("auditq")),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new IntegerValue(1)),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 2)));
    }
  }

  @Test
  void exposesCurrentTaskIdentityAndBudgetsAndAcknowledgesLiveServerOptions() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
          value(invoke(catalog, load, List.of(), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, load, List.of(), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(catalog, load, List.of(new IntegerValue(1)), transaction, 1)));

      BuiltinResult identity =
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
      assertEquals(Optional.of(new IntegerValue(8_123)), value(identity));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(catalog, taskId, List.of(new IntegerValue(1)), transaction, 2)));

      BuiltinResult remaining =
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
      assertEquals(Optional.of(new IntegerValue(59_321)), value(remaining));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(catalog, ticks, List.of(new IntegerValue(1)), transaction, 2)));

      BuiltinResult remainingSeconds =
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
      assertEquals(Optional.of(new IntegerValue(11)), value(remainingSeconds));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(catalog, seconds, List.of(new IntegerValue(1)), transaction, 2)));
    }
  }

  @Test
  void yinUsesToastThresholdsAndSuspensionContract() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
      BuiltinResult tickYield =
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
      assertEquals(OptionalDouble.of(0), delaySeconds(tickYield));

      BuiltinResult secondYield =
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
      assertEquals(OptionalDouble.of(0), delaySeconds(secondYield));

      BuiltinResult noYield =
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
      assertEquals(Optional.of(new IntegerValue(0)), value(noYield));
      assertTrue(delaySeconds(noYield).isEmpty());

      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(
                      new IntegerValue(0),
                      new IntegerValue(60_000),
                      new IntegerValue(4)),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(
                      new IntegerValue(-1),
                      new IntegerValue(59_999),
                      new IntegerValue(4)),
                  transaction,
                  2)
              ));
    }
  }

  @Test
  void bootPlayerStagesOneAuthorizedConnectionClosureWithoutTargetValidation() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("boot_player").orElseThrow();

    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.OBJECT)), List.of(), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.DEFERRED_COMMIT, spec.effect());
    assertEquals(BuiltinOwner.CONNECTION, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      BuiltinResult self =
          invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 2);
      assertEquals(new BuiltinResult.BootPlayer(2), self);

      BuiltinResult wizardMissing =
          invoke(catalog, spec, List.of(new ObjectValue(99)), transaction, 1);
      assertEquals(new BuiltinResult.BootPlayer(99), wizardMissing);
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, spec, List.of(new ObjectValue(1)), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, spec, List.of(new IntegerValue(2)), transaction, 2)));
    }
  }

  @Test
  void setPlayerFlagAcceptsAnyValueAfterValidatingTheObject() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("set_player_flag").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.OBJECT), Set.of(ArgType.ANY)),
                List.of(),
                Optional.empty())),
        spec.callShapes());
    assertSame(EffectClass.TRANSACTION_WRITE, spec.effect());
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(99), new FloatValue(1.5)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), string("enabled")),
                  transaction,
                  1)
              ));
      assertEquals(List.of(2L), transaction.players());
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new ListValue(List.of())),
                  transaction,
                  1)
              ));
      assertEquals(List.of(), transaction.players());
    }
  }

  @Test
  void listenBindsTheWizardSelectedHandlerPortAndPrintOption() {
    RecordingListener listener = new RecordingListener();
    BuiltinCatalog catalog =
        new BuiltinCatalog(listener, BuiltinHosts.builder().build());
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
      BuiltinResult result =
          invoke(
              catalog,
              spec,
              List.of(
                  new ObjectValue(2),
                  new IntegerValue(12345),
                  new MapValue(Map.of(string("print-messages"), new IntegerValue(1)))),
              transaction,
              1);

      assertEquals(Optional.of(new IntegerValue(12345)), value(result));
      assertEquals(2, listener.handler);
      assertEquals(12345, listener.port);
      assertTrue(listener.printMessages);
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new IntegerValue(23456)),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(99), new IntegerValue(23456)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), new FloatValue(1.5)),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void listenerAdmissionBuiltinsExposeTheToastCallShapes() {
    BuiltinCatalog catalog =
        new BuiltinCatalog(new RecordingListener(), BuiltinHosts.builder().build());

    BuiltinSpec listeners = catalog.spec("listeners").orElseThrow();
    assertEquals(
        List.of(new CallShape(List.of(), List.of(Set.of(ArgType.ANY)), Optional.empty())),
        listeners.callShapes());
    assertSame(BuiltinPermissionRule.ANY, listeners.permission());
    assertEquals(EffectClass.EXTERNAL_READ, listeners.effect());

    BuiltinSpec unlisten = catalog.spec("unlisten").orElseThrow();
    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.ANY)),
                List.of(Set.of(ArgType.ANY)),
                Optional.empty())),
        unlisten.callShapes());
    assertSame(BuiltinPermissionRule.WIZARD_ONLY, unlisten.permission());
    assertEquals(EffectClass.IRREVOCABLE, unlisten.effect());

    BuiltinSpec open = catalog.spec("open_network_connection").orElseThrow();
    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.STRING), Set.of(ArgType.INTEGER)),
                List.of(Set.of(ArgType.MAP)),
                Optional.empty())),
        open.callShapes());
    assertSame(BuiltinPermissionRule.WIZARD_ONLY, open.permission());
    assertEquals(EffectClass.IRREVOCABLE, open.effect());
  }

  @Test
  void setVerbInfoReplacesOwnerFlagsAndNamesWithToastPermissions() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
      BuiltinResult result =
          invoke(
              catalog,
              spec,
              List.of(
                  new ObjectValue(2),
                  string("old-name"),
                  new ListValue(List.of(new ObjectValue(2), string("rxd"), string("  new-name")))),
              transaction,
              2);

      assertEquals(Optional.of(new IntegerValue(0)), value(result));
      WorldVerb updated = transaction.verb(2, 0).orElseThrow();
      assertEquals("new-name", updated.names());
      assertEquals(2, updated.owner());
      assertEquals(13, updated.permissions() & 15);
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      new IntegerValue(1),
                      new ListValue(List.of(new ObjectValue(1), string("r"), string("new-name")))),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      new IntegerValue(1),
                      new ListValue(
                          List.of(new ObjectValue(1), string("rxd"), string("new-name")))),
                  transaction,
                  1)
              ));
      assertEquals(1, transaction.verb(2, 0).orElseThrow().owner());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      new IntegerValue(1),
                      new ListValue(List.of(new ObjectValue(99), string("r"), string("new-name")))),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      new IntegerValue(1),
                      new ListValue(List.of(new ObjectValue(1), string("q"), string("new-name")))),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_VERBNF),
          error(invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      new IntegerValue(2),
                      new ListValue(List.of(new ObjectValue(2), string("r"), string("new-name")))),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(
                      new IntegerValue(2),
                      new IntegerValue(0),
                      new ListValue(List.of(new ObjectValue(1), string("r"), string("new-name")))),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      string("new-name"),
                      new ListValue(List.of(new ObjectValue(2), new IntegerValue(1), string("x")))),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void setVerbArgsReplacesOnlyArgumentSpecificationsWithToastValidationOrder() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
      BuiltinResult result =
          invoke(
              catalog,
              spec,
              List.of(
                  new ObjectValue(2),
                  string("target"),
                  new ListValue(List.of(string("this"), string("none"), string("this")))),
              transaction,
              2);

      assertEquals(Optional.of(new IntegerValue(0)), value(result));
      WorldVerb updated = transaction.verb(2, 0).orElseThrow();
      assertEquals("target", updated.names());
      assertEquals(2, updated.owner());
      assertEquals(3 | (2 << 4) | (2 << 6), updated.permissions());
      assertEquals(-1, updated.preposition());
      assertEquals("", updated.programSource());

      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      new IntegerValue(1),
                      new ListValue(List.of(string("any"), string("with/using"), string("none")))),
                  transaction,
                  2)
              ));
      updated = transaction.verb(2, 0).orElseThrow();
      assertEquals(3 | (1 << 4), updated.permissions());
      assertEquals(0, updated.preposition());

      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      string("missing"),
                      new ListValue(List.of(string("this"), new IntegerValue(0), string("this")))),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      string("missing"),
                      new ListValue(List.of(string("this"), string("nowhere"), string("this")))),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      string("target"),
                      new ListValue(List.of(string("this"), string("+1"), string("this")))),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_VERBNF),
          error(invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      string("missing"),
                      new ListValue(List.of(string("this"), string("none"), string("this")))),
                  transaction,
                  2)
              ));

      assertEquals(2, transaction.addVerb(2, "private", 1, 1, -1));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  spec,
                  List.of(
                      new ObjectValue(2),
                      string("private"),
                      new ListValue(List.of(string("this"), string("none"), string("this")))),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(
                      new IntegerValue(2),
                      new IntegerValue(0),
                      new ListValue(List.of(string("this"), string("none"), string("this")))),
                  transaction,
                  2)
              ));
    }
  }

  @Test
  void verbsReturnsLocalNamesInDefinitionOrderWithToastReadAuthority() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("verbs").orElseThrow();
    WorldVerb first = new WorldVerb("first alias", 2, 4, -1, "return 1;");
    WorldVerb second = new WorldVerb("second", 2, 4, -1, "return 2;");
    WorldObject reader =
        new WorldObject(1, "reader", 0, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject target =
        new WorldObject(
            2,
            "target",
            0,
            2,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(first, second),
            List.of());

    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(reader, target)).begin()) {
      assertEquals(
          Optional.of(new ListValue(List.of(string("first alias"), string("second")))),
          value(invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, spec, List.of(new IntegerValue(2)), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, spec, List.of(new ObjectValue(99)), transaction, 2)));
    }
  }

  @Test
  void verbInfoAndVerbArgsReturnCanonicalLocalMetadataWithToastReadAuthority() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
          value(invoke(
                  catalog,
                  infoSpec,
                  List.of(new ObjectValue(3), string("alpha")),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(
              new ListValue(List.of(string("this"), string("with/using"), string("any")))),
          value(invoke(
                  catalog,
                  argsSpec,
                  List.of(new ObjectValue(3), new IntegerValue(1)),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  infoSpec,
                  List.of(new ObjectValue(3), string("private")),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_VERBNF),
          error(invoke(
                  catalog,
                  argsSpec,
                  List.of(new ObjectValue(3), string("missing")),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  infoSpec,
                  List.of(new IntegerValue(3), new IntegerValue(0)),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  argsSpec,
                  List.of(new ObjectValue(3), new IntegerValue(0)),
                  transaction,
                  2)
              ));
    }
  }

  @Test
  void deleteVerbRequiresObjectWriteAuthorityAndRemovesOneLocalDefinition() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
          value(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(3), string("first")),
                  transaction,
                  2)
              ));
      assertEquals("second", transaction.verb(3, 0).orElseThrow().names());
      assertEquals(Optional.empty(), transaction.verb(3, 1));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(4), string("missing")),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_VERBNF),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(3), string("missing")),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(3), new IntegerValue(0)),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(3), string("first")),
                  transaction,
                  2)
              ));
    }
  }

  @Test
  void addVerbValidatesAndStagesOneCompleteWorldVerb() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
      BuiltinResult result =
          invoke(
              catalog,
              spec,
              List.of(
                  new ObjectValue(3),
                  new ListValue(List.of(new ObjectValue(1), string("xd"), string("foobar"))),
                  new ListValue(List.of(string("this"), string("none"), string("this")))),
              transaction,
              1);

      assertEquals(Optional.of(new IntegerValue(1)), value(result));
      assertEquals(
          new WorldVerb("foobar", 1, 12 | (2 << 4) | (2 << 6), -1, ""),
          transaction.verb(3, 0).orElseThrow());
    }
  }

  @Test
  void addPropertyValidatesAndStagesOneCompleteWorldProperty() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
      BuiltinResult result =
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

      assertEquals(Optional.of(new IntegerValue(0)), value(result));
      assertEquals(
          Optional.of(new IntegerValue(99)),
          transaction.readObjectProperty(3, "foo"));
      assertEquals(7, transaction.property(3, "foo").orElseThrow().permissions());
    }
  }

  @Test
  void propertyBuiltinsExposeDefinitionsAndClearInheritedSlots() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
          value(invoke(
                  catalog,
                  catalog.spec("properties").orElseThrow(),
                  List.of(new ObjectValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new ListValue(List.of())),
          value(invoke(
                  catalog,
                  catalog.spec("properties").orElseThrow(),
                  List.of(new ObjectValue(2)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(invoke(
                  catalog,
                  catalog.spec("is_clear_property").orElseThrow(),
                  List.of(new ObjectValue(2), string("test")),
                  transaction,
                  1)
              ));

      assertTrue(transaction.writeObjectProperty(2, "test", new IntegerValue(2)).isOk());
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  catalog.spec("is_clear_property").orElseThrow(),
                  List.of(new ObjectValue(2), string("test")),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  catalog.spec("clear_property").orElseThrow(),
                  List.of(new ObjectValue(2), string("test")),
                  transaction,
                  1)
              ));
      assertEquals(Optional.of(new IntegerValue(1)), transaction.readObjectProperty(2, "test"));

      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  catalog.spec("delete_property").orElseThrow(),
                  List.of(new ObjectValue(1), string("test")),
                  transaction,
                  1)
              ));
      assertTrue(transaction.property(2, "test").isEmpty());
    }
  }

  @Test
  void objectPropertyIntrospectionTreatsWaifsAsInvalidObjects() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    WaifValue waif = new WaifValue(new ObjectValue(2), new ObjectValue(1));
    WorldObject wizard =
        new WorldObject(1, "Wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());

    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(wizard)).begin()) {
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  catalog.spec("properties").orElseThrow(),
                  List.of(waif),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  catalog.spec("property_info").orElseThrow(),
                  List.of(waif, string("alpha")),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  catalog.spec("is_clear_property").orElseThrow(),
                  List.of(waif, string("alpha")),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  catalog.spec("properties").orElseThrow(),
                  List.of(new IntegerValue(0)),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void propertyInfoResolvesCaseInsensitivelyAndReturnsCanonicalMetadata() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
          value(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), string("caseprobe")),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_PROPNF),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), string("name")),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(2), string("private")),
                  transaction,
                  2)
              ));
    }
  }

  @Test
  void mapBuiltinsPreserveToastLookupDeletionAndErrorDetails() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec hasKey = catalog.spec("maphaskey").orElseThrow();
    BuiltinSpec values = catalog.spec("mapvalues").orElseThrow();
    BuiltinSpec delete = catalog.spec("mapdelete").orElseThrow();
    MapValue map =
        new MapValue(Map.of())
            .with(string("Foo"), new IntegerValue(1))
            .with(new IntegerValue(2), string("two"));
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(invoke(catalog, hasKey, List.of(map, string("foo")), transaction, 1)));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  hasKey,
                  List.of(map, string("foo"), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, hasKey, List.of(map, new AnonymousObjectValue()), transaction, 1)
              ));

      assertEquals(
          Optional.of(new ListValue(List.of(string("two"), new IntegerValue(1)))),
          value(invoke(catalog, values, List.of(map), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_RANGE),
          error(invoke(catalog, values, List.of(map, string("foo")), transaction, 1)));

      BuiltinResult missing =
          invoke(
              catalog,
              delete,
              List.of(
                  map,
                  new ListValue(List.of(new IntegerValue(2), new IntegerValue(99)))),
              transaction,
              1);
      assertEquals(Optional.of(ErrorValue.E_RANGE), error(missing));
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      string("Key 99 not found in map"),
                      new IntegerValue(99),
                      new ListValue(List.of())))),
          errorDetails(missing));
    }
  }

  @Test
  void mapKeysReturnsToastCanonicalScalarOrderWithoutCollapsingAdjacentFloats() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
          value(invoke(catalog, spec, List.of(new MapValue(entries)), transaction, 1)));
    }
  }

  @Test
  void mapHasKeyUsesToastTreeNavigationForMixedBooleanAndIntegerKeys() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec hasKey = catalog.spec("maphaskey").orElseThrow();
    MapValue map =
        new MapValue(Map.of())
            .with(BooleanValue.TRUE, string("boolean one"))
            .with(new IntegerValue(1), string("integer one"))
            .with(BooleanValue.FALSE, string("boolean zero"))
            .with(new IntegerValue(0), string("integer zero"));

    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(catalog, hasKey, List.of(map, BooleanValue.TRUE), transaction, 1)));
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(invoke(catalog, hasKey, List.of(map, BooleanValue.FALSE), transaction, 1)));
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(invoke(catalog, hasKey, List.of(map, new IntegerValue(1)), transaction, 1)));
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(invoke(catalog, hasKey, List.of(map, new IntegerValue(0)), transaction, 1)));
    }
  }

  @Test
  void setVerbCodeCompilesAndStagesSourceOnOneDefinedVerb() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
      BuiltinResult result =
          invoke(
              catalog,
              spec,
              List.of(
                  new ObjectValue(3),
                  string("foobar"),
                  new ListValue(List.of(string("return \"foobar\"[^..$];")))),
              transaction,
              1);

      assertEquals(Optional.of(new ListValue(List.of())), value(result));
      assertEquals(
          "return \"foobar\"[^..$];", transaction.verb(3, 0).orElseThrow().programSource());
    }
  }

  @Test
  void setVerbCodeStoresTheToastCanonicalCompiledProgramRendering() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("set_verb_code").orElseThrow();
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
            List.of(new WorldVerb("server_started", 1, 0, -1, "")),
            List.of());
    List<MooValue> sourceLines =
        List.of(
            string("class = create($waif);"),
            string("waif = class:new();"),
            string("anon = create($anonymous, 3);"),
            string("fork holder (0)"),
            string("  state = {waif, anon};"),
            string("  suspend(60);"),
            string("endfork"));

    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(wizard, target)).begin()) {
      BuiltinResult result =
          invoke(
              catalog,
              spec,
              List.of(new ObjectValue(3), string("server_started"), new ListValue(sourceLines)),
              transaction,
              1);

      assertEquals(Optional.of(new ListValue(List.of())), value(result));
      assertEquals(
          """
          class = create($waif);
          WAIF = class:new();
          ANON = create($anonymous, 3);
          fork holder (0)
          state = {WAIF, ANON};
          suspend(60);
          endfork""",
          transaction.verb(3, 0).orElseThrow().programSource());
    }
  }

  @Test
  void verbCodeReadsOneLocalVerbAsCanonicalSourceLines() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
      BuiltinResult result =
          invoke(catalog, spec, List.of(new ObjectValue(3), string("test")), transaction, 2);

      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      string("for i, j in ({})"),
                      string("  break j;"),
                      string("  continue i;"),
                      string("endfor")))),
          value(result));
    }
  }

  @Test
  void recycleAuthorizesOneObjectForTheExistingVmOutcome() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
      BuiltinResult result =
          invoke(catalog, spec, List.of(new ObjectValue(3)), transaction, 2);

      assertEquals(OptionalLong.of(3), recycleTarget(result));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, spec, List.of(new IntegerValue(3)), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, spec, List.of(new ObjectValue(999)), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, spec, List.of(new ObjectValue(3)), transaction, 1)));
    }
  }

  @Test
  void objectQueriesReadTheExistingTransactionalWorldState() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec parent = catalog.spec("parent").orElseThrow();
    BuiltinSpec isPlayer = catalog.spec("is_player").orElseThrow();
    BuiltinSpec valid = catalog.spec("valid").orElseThrow();
    BuiltinSpec maxObject = catalog.spec("max_object").orElseThrow();

    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.ANY)), List.of(), Optional.empty())),
        parent.callShapes());
    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.OBJECT)), List.of(), Optional.empty())),
        isPlayer.callShapes());
    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.ANY)), List.of(), Optional.empty())),
        valid.callShapes());
    assertEquals(
        List.of(new CallShape(List.of(), List.of(), Optional.empty())), maxObject.callShapes());
    for (BuiltinSpec spec : List.of(parent, isPlayer, valid, maxObject)) {
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
          value(invoke(catalog, parent, List.of(new ObjectValue(3)), transaction, 1)));
      assertEquals(
          Optional.of(new ObjectValue(3)),
          value(invoke(catalog, maxObject, List.of(), transaction, 1)));
      transaction.createAnonymousObject(List.of(2L), 1);
      assertEquals(
          Optional.of(new ObjectValue(3)),
          value(invoke(catalog, maxObject, List.of(), transaction, 1)));
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(invoke(catalog, isPlayer, List.of(new ObjectValue(1)), transaction, 1)));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(catalog, isPlayer, List.of(new ObjectValue(3)), transaction, 1)));
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(invoke(catalog, valid, List.of(new ObjectValue(0)), transaction, 1)));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(catalog, valid, List.of(new ObjectValue(-1)), transaction, 1)));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  valid,
                  List.of(new WaifValue(new ObjectValue(2), new ObjectValue(1))),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, parent, List.of(new ObjectValue(99)), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, isPlayer, List.of(new ObjectValue(99)), transaction, 1)));
    }
  }

  @Test
  void locateByNameSearchesPermanentNamesInObjectOrderWithToastCaseRules() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("locate_by_name").orElseThrow();
    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.STRING)),
                List.of(Set.of(ArgType.INTEGER)),
                Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.WIZARD_ONLY, spec.permission());
    assertEquals(EffectClass.TRANSACTION_READ, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());

    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          new ListValue(List.of(new ObjectValue(2))),
          value(invoke(catalog, spec, List.of(string("gram")), transaction, 1)
              )
              .orElseThrow());
      assertEquals(
          new ListValue(List.of()),
          value(invoke(
                  catalog,
                  spec,
                  List.of(string("program"), new IntegerValue(1)),
                  transaction,
                  1)
              )
              .orElseThrow());
      assertEquals(
          ErrorValue.E_PERM,
          error(invoke(catalog, spec, List.of(string("Program")), transaction, 2)
              )
              .orElseThrow());
    }
  }

  @Test
  void locationsWalksTheContainmentChainAndStopsBeforeTheSelectedBase() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("locations").orElseThrow();
    WorldObject base =
        new WorldObject(1, "base", 4, 1, -1, -1, List.of(2L), List.of(), List.of(), List.of());
    WorldObject room =
        new WorldObject(2, "room", 0, 1, 1, -1, List.of(3L), List.of(), List.of(), List.of());
    WorldObject item =
        new WorldObject(3, "item", 0, 1, 2, -1, List.of(), List.of(), List.of(), List.of());

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.OBJECT)),
                List.of(Set.of(ArgType.OBJECT), Set.of(ArgType.INTEGER)),
                Optional.empty())),
        spec.callShapes());
    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(base, room, item)).begin()) {
      assertEquals(
          new ListValue(List.of(new ObjectValue(2), new ObjectValue(1))),
          value(invoke(catalog, spec, List.of(new ObjectValue(3)), transaction, 1)
              )
              .orElseThrow());
      assertEquals(
          new ListValue(List.of(new ObjectValue(2))),
          value(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(3), new ObjectValue(1)),
                  transaction,
                  1)
              )
              .orElseThrow());
      assertEquals(
          ErrorValue.E_INVIND,
          error(invoke(catalog, spec, List.of(new ObjectValue(99)), transaction, 1)
              )
              .orElseThrow());
    }
  }

  @Test
  void multipleInheritanceBuiltinsPreserveOrderedParentAndChildForms() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec parent = catalog.spec("parent").orElseThrow();
    BuiltinSpec parents = catalog.spec("parents").orElseThrow();
    BuiltinSpec ancestors = catalog.spec("ancestors").orElseThrow();
    BuiltinSpec children = catalog.spec("children").orElseThrow();
    BuiltinSpec create = catalog.spec("create").orElseThrow();
    BuiltinSpec chparents = catalog.spec("chparents").orElseThrow();
    WorldObject first =
        new WorldObject(
            2, "first", 0, 1, -1, List.of(), List.of(), List.of(4L), List.of(), List.of());
    WorldObject second =
        new WorldObject(
            3, "second", 0, 1, -1, List.of(), List.of(), List.of(4L), List.of(), List.of());
    WorldObject child =
        new WorldObject(
            4,
            "child",
            0,
            1,
            -1,
            List.of(2L, 3L),
            List.of(),
            List.of(),
            List.of(),
            List.of());
    WorldObject programmer =
        new WorldObject(1, "programmer", 0, 1, -1, -1, List.of(), List.of(), List.of(), List.of());

    try (WorldTxn transaction =
        new WorldTxn(List.of(), List.of(programmer, first, second, child)).begin()) {
      ListValue orderedParents =
          new ListValue(List.of(new ObjectValue(2), new ObjectValue(3)));
      assertEquals(
          Optional.of(new ObjectValue(2)),
          value(invoke(catalog, parent, List.of(new ObjectValue(4)), transaction, 1)));
      assertEquals(
          Optional.of(orderedParents),
          value(invoke(catalog, parents, List.of(new ObjectValue(4)), transaction, 1)));
      assertEquals(
          Optional.of(orderedParents),
          value(invoke(catalog, ancestors, List.of(new ObjectValue(4)), transaction, 1)));
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(new ObjectValue(4), new ObjectValue(2), new ObjectValue(3)))),
          value(invoke(
                  catalog,
                  ancestors,
                  List.of(new ObjectValue(4), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, ancestors, List.of(new IntegerValue(4)), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, ancestors, List.of(new ObjectValue(99)), transaction, 1)));
      assertEquals(
          Optional.of(new ListValue(List.of(new ObjectValue(4)))),
          value(invoke(catalog, children, List.of(new ObjectValue(2)), transaction, 1)));
      WaifValue waif = new WaifValue(new ObjectValue(2), new ObjectValue(1));
      for (BuiltinSpec hierarchyQuery : List.of(parent, parents, children)) {
        assertEquals(
            Optional.of(ErrorValue.E_INVARG),
            error(invoke(catalog, hierarchyQuery, List.of(waif), transaction, 1)));
      }

      assertEquals(
          Optional.of(new ObjectValue(5)),
          value(invoke(catalog, create, List.of(orderedParents), transaction, 1)));
      assertEquals(List.of(2L, 3L), transaction.object(5).orElseThrow().parents());

      ListValue reversed = new ListValue(List.of(new ObjectValue(3), new ObjectValue(2)));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  chparents,
                  List.of(new ObjectValue(4), reversed),
                  transaction,
                  1)
              ));
      assertEquals(List.of(3L, 2L), transaction.object(4).orElseThrow().parents());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  chparents,
                  List.of(
                      new ObjectValue(4),
                      new ListValue(List.of(new ObjectValue(2), new ObjectValue(2)))),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void parentMutationDistinguishesPermissionRecursionAndInvalidArguments() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec chparents = catalog.spec("chparents").orElseThrow();
    WorldObject programmer =
        new WorldObject(1, "programmer", 0, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject fertile =
        new WorldObject(2, "fertile", 128, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject closed =
        new WorldObject(3, "closed", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject controlled =
        new WorldObject(
            4, "controlled", 0, 1, -1, -1, List.of(), List.of(6L), List.of(), List.of());
    WorldObject foreign =
        new WorldObject(5, "foreign", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject descendant =
        new WorldObject(6, "descendant", 0, 1, -1, 4, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction =
        new WorldTxn(List.of(), List.of(programmer, fertile, closed, controlled, foreign, descendant))
            .begin()) {
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  chparents,
                  List.of(new ObjectValue(5), new ObjectValue(2)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  chparents,
                  List.of(new ObjectValue(4), new ObjectValue(3)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_RECMOVE),
          error(invoke(
                  catalog,
                  chparents,
                  List.of(new ObjectValue(4), new ObjectValue(6)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  chparents,
                  List.of(new ObjectValue(4), new ObjectValue(6)),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  chparents,
                  List.of(new ObjectValue(99), new ObjectValue(2)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  chparents,
                  List.of(new ObjectValue(4), new ObjectValue(2)),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void gcStatsReportsTheCompletedCollectorStateToWizardsOnly() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec gcStats = catalog.spec("gc_stats").orElseThrow();
    assertEquals(
        List.of(new CallShape(List.of(), List.of(), Optional.empty())), gcStats.callShapes());
    assertSame(BuiltinPermissionRule.WIZARD_ONLY, gcStats.permission());

    WorldObject wizard =
        new WorldObject(1, "wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject programmer =
        new WorldObject(2, "programmer", 2, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(wizard, programmer)).begin()) {
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, gcStats, List.of(), transaction, 2)));
      MapValue stats =
          (MapValue) value(invoke(catalog, gcStats, List.of(), transaction, 1)).orElseThrow();
      for (String color :
          List.of("green", "yellow", "black", "gray", "white", "purple", "pink")) {
        assertEquals(Optional.of(new IntegerValue(0)), stats.get(string(color)));
      }
    }
  }

  @Test
  void evalRequiresProgrammerAndPreservesEverySourceArgument() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec eval = catalog.spec("eval").orElseThrow();
    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.STRING)), List.of(), Optional.of(Set.of(ArgType.STRING)))),
        eval.callShapes());
    assertSame(BuiltinPermissionRule.PROGRAMMER_ONLY, eval.permission());

    WorldObject programmer =
        new WorldObject(1, "programmer", 2, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject ordinary =
        new WorldObject(2, "ordinary", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject wizardOnly =
        new WorldObject(3, "wizard", 4, 3, -1, -1, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction =
        new WorldTxn(List.of(), List.of(programmer, ordinary, wizardOnly)).begin()) {
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, eval, List.of(string("return 5;")), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, eval, List.of(string("return 5;")), transaction, 3)));
      assertEquals(
          Optional.of("x = 1;\nreturn x + 1;"),
          dynamicSource(invoke(
                  catalog,
                  eval,
                  List.of(string("x = 1;"), string("return x + 1;")),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void createValidatesOptionalArgumentTypesBeforeParentValidity() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec create = catalog.spec("create").orElseThrow();
    WorldObject wizard =
        new WorldObject(1, "wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(wizard)).begin()) {
      for (MooValue invalidParent :
          List.of(
              new ObjectValue(999999),
              new ListValue(List.of(new ObjectValue(999999))))) {
        assertEquals(
            Optional.of(ErrorValue.E_TYPE),
            error(invoke(
                    catalog,
                    create,
                    List.of(invalidParent, new FloatValue(1.5)),
                    transaction,
                    1)
                ));
        assertEquals(
            Optional.of(ErrorValue.E_INVARG),
            error(invoke(
                    catalog,
                    create,
                    List.of(invalidParent, new IntegerValue(1)),
                    transaction,
                    1)
                ));
      }
    }
  }

  @Test
  void createParsesOwnerAnonymousAndInitializerInEveryAuthorizedPosition() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec create = catalog.spec("create").orElseThrow();
    WorldVerb initialize = new WorldVerb("initialize", 1, 4, -1, "return 0;");
    WorldObject programmer =
        new WorldObject(1, "programmer", 0, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject ordinaryParent =
        new WorldObject(
            2,
            "ordinary",
            ObjectFlags.FLAG_FERTILE,
            2,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(initialize),
            List.of());
    WorldObject anonymousParent =
        new WorldObject(
            3,
            "anonymous",
            ObjectFlags.FLAG_ANONYMOUS,
            2,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(initialize),
            List.of());
    WorldObject owner =
        new WorldObject(4, "owner", 0, 4, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject userOnlyParent =
        new WorldObject(
            5,
            "user",
            ObjectFlags.FLAG_USER,
            5,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(initialize),
            List.of());
    WorldObject wizard =
        new WorldObject(
            9,
            "wizard",
            ObjectFlags.FLAG_WIZARD,
            9,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of());
    ListValue initializer = new ListValue(List.of(new IntegerValue(42)));
    try (WorldTxn transaction =
        new WorldTxn(
                List.of(),
                List.of(
                    programmer,
                    ordinaryParent,
                    anonymousParent,
                    owner,
                    userOnlyParent,
                    wizard))
            .begin()) {
      BuiltinResult ordinary =
          invoke(catalog, create, List.of(new ObjectValue(2), initializer), transaction, 1);
      BuiltinResult.Initialize ordinaryInitialize =
          assertInstanceOf(BuiltinResult.Initialize.class, ordinary);
      assertEquals(initializer, ordinaryInitialize.arguments());
      assertInstanceOf(ObjectValue.class, ordinaryInitialize.created());

      BuiltinResult anonymous =
          invoke(
              catalog,
              create,
              List.of(new ObjectValue(3), new IntegerValue(1), initializer),
              transaction,
              1);
      BuiltinResult.Initialize anonymousInitialize =
          assertInstanceOf(BuiltinResult.Initialize.class, anonymous);
      assertEquals(initializer, anonymousInitialize.arguments());
      assertInstanceOf(
          AnonymousObjectValue.class, anonymousInitialize.created());

      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(
              invoke(
                  catalog,
                  create,
                  List.of(new ObjectValue(5), new IntegerValue(1)),
                  transaction,
                  1)));

      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  create,
                  List.of(new ObjectValue(2), new ObjectValue(4)),
                  transaction,
                  1)
              ));
      BuiltinResult wizardOwned =
          invoke(
              catalog,
              create,
              List.of(
                  new ObjectValue(3),
                  new ObjectValue(4),
                  initializer,
                  new IntegerValue(1)),
              transaction,
              9);
      AnonymousObjectValue created =
          assertInstanceOf(
              AnonymousObjectValue.class,
              assertInstanceOf(BuiltinResult.Initialize.class, wizardOwned).created());
      assertEquals(4, transaction.anonymousObject(created).orElseThrow().owner());
    }
  }

  @Test
  void createDecrementsEveryValidOwnersQuotaAndExhaustionAllocatesNothing() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec create = catalog.spec("create").orElseThrow();
    WorldProperty programmerQuota =
        new WorldProperty("ownership_quota", new IntegerValue(2), 1, 0, false, true);
    WorldProperty delegatedQuota =
        new WorldProperty("ownership_quota", new IntegerValue(1), 4, 0, false, true);
    WorldProperty wizardQuota =
        new WorldProperty("ownership_quota", new IntegerValue(1), 9, 0, false, true);
    WorldObject programmer =
        new WorldObject(
            1,
            "programmer",
            0,
            1,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(programmerQuota));
    WorldObject parent =
        new WorldObject(
            2,
            "parent",
            ObjectFlags.FLAG_FERTILE | ObjectFlags.FLAG_ANONYMOUS,
            2,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of());
    WorldObject delegatedOwner =
        new WorldObject(
            4,
            "delegated owner",
            0,
            4,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(delegatedQuota));
    WorldObject wizard =
        new WorldObject(
            9,
            "wizard",
            ObjectFlags.FLAG_WIZARD,
            9,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(wizardQuota));
    try (WorldTxn transaction =
        new WorldTxn(List.of(), List.of(programmer, parent, delegatedOwner, wizard)).begin()) {
      assertInstanceOf(
          ObjectValue.class,
          value(invoke(catalog, create, List.of(new ObjectValue(2)), transaction, 1)
              )
              .orElseThrow());
      assertEquals(
          new IntegerValue(1),
          transaction.readObjectProperty(1, "ownership_quota").orElseThrow());

      assertInstanceOf(
          AnonymousObjectValue.class,
          value(invoke(
                  catalog,
                  create,
                  List.of(new ObjectValue(2), new IntegerValue(1)),
                  transaction,
                  1)
              )
              .orElseThrow());
      assertEquals(
          new IntegerValue(0),
          transaction.readObjectProperty(1, "ownership_quota").orElseThrow());

      assertInstanceOf(
          ObjectValue.class,
          value(invoke(
                  catalog,
                  create,
                  List.of(new ObjectValue(2), new ObjectValue(4)),
                  transaction,
                  9)
              )
              .orElseThrow());
      assertEquals(
          new IntegerValue(0),
          transaction.readObjectProperty(4, "ownership_quota").orElseThrow());

      assertInstanceOf(
          ObjectValue.class,
          value(invoke(catalog, create, List.of(new ObjectValue(2)), transaction, 9)
              )
              .orElseThrow());
      assertEquals(
          new IntegerValue(0),
          transaction.readObjectProperty(9, "ownership_quota").orElseThrow());

      int permanentCount = transaction.objectCount();
      int anonymousCount = transaction.snapshot().anonymousObjects().size();
      BuiltinResult exhaustedPermanent =
          invoke(catalog, create, List.of(new ObjectValue(2)), transaction, 1);
      BuiltinResult exhaustedAnonymous =
          invoke(
              catalog,
              create,
              List.of(new ObjectValue(2), new IntegerValue(1)),
              transaction,
              1);
      assertEquals(Optional.of(ErrorValue.E_QUOTA), error(exhaustedPermanent));
      assertEquals(Optional.of(ErrorValue.E_QUOTA), error(exhaustedAnonymous));
      assertEquals(permanentCount, transaction.objectCount());
      assertEquals(anonymousCount, transaction.snapshot().anonymousObjects().size());
    }
  }

  @Test
  void raiseProducesTheExistingStructuredErrorOutcome() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
      BuiltinResult basic =
          invoke(catalog, spec, List.of(ErrorValue.E_INVARG), transaction, 1);
      assertEquals(Optional.of(ErrorValue.E_INVARG), error(basic));
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(string("E_INVARG"), new IntegerValue(0), new ListValue(List.of())))),
          errorDetails(basic));

      ListValue customValue = new ListValue(List.of(new IntegerValue(1), new IntegerValue(2)));
      BuiltinResult custom =
          invoke(
              catalog,
              spec,
              List.of(ErrorValue.E_TYPE, string("custom message"), customValue),
              transaction,
              1);
      assertEquals(Optional.of(ErrorValue.E_TYPE), error(custom));
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(string("custom message"), customValue, new ListValue(List.of())))),
          errorDetails(custom));
    }
  }

  @Test
  void numbersFamilyMatchesPinnedContractsAndSemantics() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    CallShape unaryFloat =
        new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty());
    for (String name :
        List.of(
            "acos",
            "acosh",
            "asin",
            "asinh",
            "atanh",
            "cbrt",
            "ceil",
            "cos",
            "cosh",
            "exp",
            "floor",
            "log",
            "log10",
            "round",
            "sin",
            "sinh",
            "sqrt",
            "tan",
            "tanh",
            "trunc")) {
      assertPureVmContract(catalog, name, unaryFloat);
    }
    assertPureVmContract(
        catalog,
        "atan",
        new CallShape(
            List.of(Set.of(ArgType.FLOAT)),
            List.of(Set.of(ArgType.FLOAT)),
            Optional.empty()));
    assertPureVmContract(
        catalog,
        "atan2",
        new CallShape(
            List.of(Set.of(ArgType.FLOAT), Set.of(ArgType.FLOAT)),
            List.of(),
            Optional.empty()));
    CallShape twoLists =
        new CallShape(
            List.of(Set.of(ArgType.LIST), Set.of(ArgType.LIST)),
            List.of(),
            Optional.empty());
    assertPureVmContract(catalog, "distance", twoLists);
    assertPureVmContract(catalog, "relative_heading", twoLists);
    assertPureVmContract(
        catalog,
        "floatstr",
        new CallShape(
            List.of(Set.of(ArgType.FLOAT), Set.of(ArgType.INTEGER)),
            List.of(Set.of(ArgType.ANY)),
            Optional.empty()));

    BuiltinSpec frandom = catalog.spec("frandom").orElseThrow();
    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.FLOAT)),
                List.of(Set.of(ArgType.FLOAT)),
                Optional.empty())),
        frandom.callShapes());
    BuiltinSpec randomBytes = catalog.spec("random_bytes").orElseThrow();
    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.INTEGER)), List.of(), Optional.empty())),
        randomBytes.callShapes());
    for (BuiltinSpec spec : List.of(frandom, randomBytes)) {
      assertSame(BuiltinPermissionRule.ANY, spec.permission());
      assertEquals(0, spec.tickCost().charge(List.of()));
      assertEquals(EffectClass.IRREVOCABLE, spec.effect());
      assertEquals(BuiltinOwner.VM, spec.owner());
    }
    for (String name : List.of("ctime", "ftime")) {
      BuiltinSpec spec = catalog.spec(name).orElseThrow();
      assertEquals(
          List.of(
              new CallShape(
                  List.of(), List.of(Set.of(ArgType.INTEGER)), Optional.empty())),
          spec.callShapes());
      assertSame(BuiltinPermissionRule.ANY, spec.permission());
      assertEquals(0, spec.tickCost().charge(List.of()));
      assertEquals(EffectClass.IRREVOCABLE, spec.effect());
      assertEquals(BuiltinOwner.VM, spec.owner());
    }

    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(new FloatValue(0.0)),
          value(invoke(catalog, catalog.spec("acos").orElseThrow(), List.of(new FloatValue(1.0)), transaction, 1)
              ));
      assertEquals(
          Optional.of(new FloatValue(0.0)),
          value(invoke(catalog, catalog.spec("acosh").orElseThrow(), List.of(new FloatValue(1.0)), transaction, 1)
              ));
      BuiltinResult largeAcosh =
          invoke(
              catalog,
              catalog.spec("acosh").orElseThrow(),
              List.of(new FloatValue(Double.MAX_VALUE)),
              transaction,
              1);
      assertTrue(((FloatValue) value(largeAcosh).orElseThrow()).value() > 700.0);
      BuiltinResult largeNegativeAsinh =
          invoke(
              catalog,
              catalog.spec("asinh").orElseThrow(),
              List.of(new FloatValue(-Double.MAX_VALUE)),
              transaction,
              1);
      assertTrue(((FloatValue) value(largeNegativeAsinh).orElseThrow()).value() < -700.0);
      assertEquals(
          Optional.of(new FloatValue(1.0e-20)),
          value(invoke(
                  catalog,
                  catalog.spec("atanh").orElseThrow(),
                  List.of(new FloatValue(1.0e-20)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new FloatValue(-2.0)),
          value(invoke(catalog, catalog.spec("cbrt").orElseThrow(), List.of(new FloatValue(-8.0)), transaction, 1)
              ));
      assertEquals(
          Optional.of(new FloatValue(3.0)),
          value(invoke(catalog, catalog.spec("round").orElseThrow(), List.of(new FloatValue(2.5)), transaction, 1)
              ));
      assertEquals(
          Optional.of(new FloatValue(-3.0)),
          value(invoke(catalog, catalog.spec("round").orElseThrow(), List.of(new FloatValue(-2.5)), transaction, 1)
              ));
      assertEquals(
          Optional.of(new FloatValue(0.0)),
          value(invoke(
                  catalog,
                  catalog.spec("round").orElseThrow(),
                  List.of(new FloatValue(0.49999999999999994)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, catalog.spec("sqrt").orElseThrow(), List.of(new FloatValue(-1.0)), transaction, 1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_FLOAT),
          error(invoke(
                  catalog,
                  catalog.spec("sqrt").orElseThrow(),
                  List.of(new FloatValue(Double.NaN)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_FLOAT),
          error(invoke(catalog, catalog.spec("atanh").orElseThrow(), List.of(new FloatValue(1.0)), transaction, 1)
              ));
      assertEquals(
          Optional.of(string("3.14")),
          value(invoke(
                  catalog,
                  catalog.spec("floatstr").orElseThrow(),
                  List.of(new FloatValue(3.14159), new IntegerValue(2)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(string("2")),
          value(invoke(
                  catalog,
                  catalog.spec("floatstr").orElseThrow(),
                  List.of(new FloatValue(2.5), new IntegerValue(0)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(string("2e+00")),
          value(invoke(
                  catalog,
                  catalog.spec("floatstr").orElseThrow(),
                  List.of(new FloatValue(2.5), new IntegerValue(0), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new FloatValue(5.0)),
          value(invoke(
                  catalog,
                  catalog.spec("distance").orElseThrow(),
                  List.of(
                      new ListValue(List.of(new IntegerValue(0), new IntegerValue(0))),
                      new ListValue(List.of(new IntegerValue(3), new IntegerValue(4)))),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  catalog.spec("distance").orElseThrow(),
                  List.of(
                      new ListValue(List.of(new IntegerValue(1), new IntegerValue(2))),
                      new ListValue(List.of(new IntegerValue(1)))),
                  transaction,
                  1)
              ));
      MooValue infiniteDistance =
          value(invoke(
                  catalog,
                  catalog.spec("distance").orElseThrow(),
                  List.of(
                      new ListValue(List.of(new FloatValue(-Double.MAX_VALUE))),
                      new ListValue(List.of(new FloatValue(Double.MAX_VALUE)))),
                  transaction,
                  1)
              )
              .orElseThrow();
      assertTrue(Double.isInfinite(((FloatValue) infiniteDistance).value()));
      assertEquals(
          Optional.of(
              new ListValue(List.of(new IntegerValue(89), new IntegerValue(0)))),
          value(invoke(
                  catalog,
                  catalog.spec("relative_heading").orElseThrow(),
                  List.of(
                      new ListValue(
                          List.of(new FloatValue(0.0), new FloatValue(0.0), new FloatValue(0.0))),
                      new ListValue(
                          List.of(new FloatValue(0.0), new FloatValue(1.0), new FloatValue(0.0)))),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(string("")),
          value(invoke(catalog, randomBytes, List.of(new IntegerValue(0)), transaction, 1)));
      BuiltinResult invalidRandomBytes =
          invoke(catalog, randomBytes, List.of(new IntegerValue(10_001)), transaction, 1);
      assertEquals(Optional.of(ErrorValue.E_INVARG), error(invalidRandomBytes));
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      string("Invalid count"),
                      new IntegerValue(10_001),
                      new ListValue(List.of())))),
          errorDetails(invalidRandomBytes));
      MooValue floatingRandom =
          value(invoke(catalog, frandom, List.of(new FloatValue(5.0), new FloatValue(10.0)), transaction, 1)
              )
              .orElseThrow();
      assertTrue(floatingRandom instanceof FloatValue);
      assertTrue(((FloatValue) floatingRandom).value() >= 5.0);
      assertTrue(((FloatValue) floatingRandom).value() <= 10.0);
      assertTrue(
          value(invoke(catalog, catalog.spec("ctime").orElseThrow(), List.of(new IntegerValue(0)), transaction, 1)
                  )
                  .orElseThrow()
              instanceof StringValue);
      String expandedYear =
          decode(
              (StringValue)
                  value(invoke(
                          catalog,
                          catalog.spec("ctime").orElseThrow(),
                          List.of(new IntegerValue(253_402_387_200L)),
                          transaction,
                          1)
                      )
                      .orElseThrow());
      assertTrue(expandedYear.contains("10000"), expandedYear);
      assertFalse(expandedYear.contains("+10000"));
      assertEquals(
          value(invoke(
                  catalog,
                  catalog.spec("ctime").orElseThrow(),
                  List.of(new IntegerValue((long) Integer.MAX_VALUE * 31_536_000L)),
                  transaction,
                  1)
              ),
          value(invoke(
                  catalog,
                  catalog.spec("ctime").orElseThrow(),
                  List.of(new IntegerValue(Long.MAX_VALUE)),
                  transaction,
                  1)
              ));
      MooValue fineTime =
          value(invoke(catalog, catalog.spec("ftime").orElseThrow(), List.of(), transaction, 1)
              )
              .orElseThrow();
      assertTrue(fineTime instanceof FloatValue);
      assertTrue(((FloatValue) fineTime).value() > 0.0);
      for (long selector : List.of(1L, 2L, 99L)) {
        MooValue monotonicTime =
            value(invoke(
                    catalog,
                    catalog.spec("ftime").orElseThrow(),
                    List.of(new IntegerValue(selector)),
                    transaction,
                    1)
                )
                .orElseThrow();
        assertTrue(monotonicTime instanceof FloatValue);
        assertTrue(((FloatValue) monotonicTime).value() > 0.0);
      }
    }
  }

  @Test
  void promotesIntegersForMongooseMathBuiltinsWhenConfigured() {
    BuiltinCatalog catalog =
        new BuiltinCatalog(
            BuiltinHosts.builder().valueSemantics(new ValueSemantics(true)).build());
    CallShape unaryNumber =
        new CallShape(List.of(Set.of(ArgType.NUMBER)), List.of(), Optional.empty());
    for (String name :
        List.of(
            "acos",
            "acosh",
            "asin",
            "asinh",
            "atanh",
            "cbrt",
            "ceil",
            "cos",
            "cosh",
            "exp",
            "floor",
            "log",
            "log10",
            "sin",
            "sinh",
            "sqrt",
            "tan",
            "tanh")) {
      assertPureVmContract(catalog, name, unaryNumber);
    }
    assertPureVmContract(
        catalog,
        "atan",
        new CallShape(
            List.of(Set.of(ArgType.NUMBER)),
            List.of(Set.of(ArgType.NUMBER)),
            Optional.empty()));
    assertPureVmContract(
        catalog,
        "atan2",
        new CallShape(
            List.of(Set.of(ArgType.NUMBER), Set.of(ArgType.NUMBER)),
            List.of(),
            Optional.empty()));

    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(new FloatValue(3.0)),
          value(
              invoke(
                  catalog,
                  catalog.spec("sqrt").orElseThrow(),
                  List.of(new IntegerValue(9)),
                  transaction,
                  1)));
      assertEquals(
          Optional.of(new FloatValue(Math.atan2(1.0, 2.0))),
          value(
              invoke(
                  catalog,
                  catalog.spec("atan").orElseThrow(),
                  List.of(new IntegerValue(1), new IntegerValue(2)),
                  transaction,
                  1)));
      assertEquals(
          Optional.of(new FloatValue(Math.atan2(1.0, 2.0))),
          value(
              invoke(
                  catalog,
                  catalog.spec("atan2").orElseThrow(),
                  List.of(new IntegerValue(1), new IntegerValue(2)),
                  transaction,
                  1)));
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(
              invoke(
                  catalog,
                  catalog.spec("equal").orElseThrow(),
                  List.of(new IntegerValue(1), new FloatValue(1.0)),
                  transaction,
                  1)));
    }
  }

  @Test
  void numericRandomAndConstructionLimitsMatchPinnedToast() throws ReflectiveOperationException {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    Field floatingRandomField = BuiltinCatalog.class.getDeclaredField("floatingRandom");
    floatingRandomField.setAccessible(true);
    floatingRandomField.set(catalog, new UpperEndpointRandom());

    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(new FloatValue(10.0)),
          value(invoke(
                  catalog,
                  catalog.spec("frandom").orElseThrow(),
                  List.of(new FloatValue(5.0), new FloatValue(10.0)),
                  transaction,
                  1)
              ));
    }

    WorldObject system =
        new WorldObject(
            0,
            "System",
            0,
            1,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new WorldProperty(
                    "server_options", new ObjectValue(3), 1, 0, false, true)));
    WorldObject wizard =
        new WorldObject(1, "Wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject catchableOptions =
        new WorldObject(
            3,
            "Options",
            0,
            1,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new WorldProperty(
                    "max_string_concat", new IntegerValue(1_021), 1, 0, false, true),
                new WorldProperty(
                    "max_concat_catchable", new IntegerValue(1), 1, 0, false, true)));
    BuiltinSpec randomBytes = catalog.spec("random_bytes").orElseThrow();
    try (WorldTxn transaction =
        new WorldTxn(List.of(), List.of(system, wizard, catchableOptions)).begin()) {
      assertEquals(
          Optional.of(ErrorValue.E_QUOTA),
          error(invoke(catalog, randomBytes, List.of(new IntegerValue(10_000)), transaction, 1)
              ));
    }

    WorldObject uncatchableOptions =
        new WorldObject(
            3,
            "Options",
            0,
            1,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new WorldProperty(
                    "max_string_concat", new IntegerValue(1_021), 1, 0, false, true),
                new WorldProperty(
                    "max_concat_catchable", new IntegerValue(0), 1, 0, false, true)));
    try (WorldTxn transaction =
        new WorldTxn(List.of(), List.of(system, wizard, uncatchableOptions)).begin()) {
      assertTrue(
          abortSeconds(invoke(catalog, randomBytes, List.of(new IntegerValue(10_000)), transaction, 1)
              ));
    }
  }

  @Test
  void minUsesTheCanonicalPureVmContractAndSelectsTheSmallestHomogeneousNumber() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
          value(invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(3), new IntegerValue(1), new IntegerValue(7)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new FloatValue(1.5)),
          value(invoke(
                  catalog,
                  spec,
                  List.of(new FloatValue(3.5), new FloatValue(1.5)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(1), new FloatValue(2.0)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, spec, List.of(string("x")), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(catalog, spec, List.of(), transaction, 1)));
    }
  }

  @Test
  void maxSelectsTheLargestHomogeneousNumericArgumentWithoutPromotion() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    try (WorldTxn transaction = world().begin()) {
      BuiltinSpec spec = catalog.spec("max").orElseThrow();

      assertEquals(
          Optional.of(new IntegerValue(7)),
          value(invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(3), new IntegerValue(7), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new FloatValue(7.5)),
          value(invoke(
                  catalog,
                  spec,
                  List.of(new FloatValue(3.5), new FloatValue(7.5)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(3), new FloatValue(7.5)),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void absPreservesTheNumericTypeAndReturnsItsMagnitude() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    assertPureVmContract(
        catalog,
        "abs",
        new CallShape(List.of(Set.of(ArgType.NUMBER)), List.of(), Optional.empty()));

    try (WorldTxn transaction = world().begin()) {
      BuiltinSpec spec = catalog.spec("abs").orElseThrow();
      assertEquals(
          Optional.of(new IntegerValue(7)),
          value(invoke(catalog, spec, List.of(new IntegerValue(-7)), transaction, 1)));
      assertEquals(
          Optional.of(new FloatValue(7.5)),
          value(invoke(catalog, spec, List.of(new FloatValue(-7.5)), transaction, 1)));
    }
  }

  @Test
  void randomUsesTheIrrevocableVmContractAndInclusiveIntegerBounds() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
          value(invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 1)));
      for (int invocation = 0; invocation < 64; invocation++) {
        long value =
            ((IntegerValue)
                    value(invoke(catalog, spec, List.of(new IntegerValue(7)), transaction, 1))
                        .orElseThrow())
                .value();
        assertTrue(value >= 1 && value <= 7);
      }
      long ranged =
          ((IntegerValue)
                  value(
                          invoke(
                              catalog,
                              spec,
                              List.of(new IntegerValue(5), new IntegerValue(10)),
                              transaction,
                              1))
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
            error(invoke(catalog, spec, arguments, transaction, 1)));
      }
    }
  }

  @Test
  void disassembleReadsOneDefinedVerbAndReturnsDeterministicBytecodeLines() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
      BuiltinResult result =
          invoke(catalog, spec, List.of(new ObjectValue(3), string("foobar")), transaction, 2);
      ListValue lines = (ListValue) value(result).orElseThrow();
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
          error(invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(3), string("foobar")),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(3), new FloatValue(1.5)),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(999), string("foobar")),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ObjectValue(3), new IntegerValue(0)),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_VERBNF),
          error(invoke(catalog, spec, List.of(new ObjectValue(3), string("missing")), transaction, 2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, spec, List.of(new ObjectValue(4), string("secret")), transaction, 2)
              ));
      assertTrue(
          value(invoke(catalog, spec, List.of(new ObjectValue(4), string("secret")), transaction, 1)
              )
              .isPresent());
    }
  }

  @Test
  void exposesTheExactDeferredDumpDatabaseContract() {
    BuiltinSpec spec = new BuiltinCatalog(BuiltinHosts.builder().build()).spec("dump_database").orElseThrow();

    assertEquals(
        List.of(new CallShape(List.of(), List.of(), Optional.empty())), spec.callShapes());
    assertSame(BuiltinPermissionRule.WIZARD_ONLY, spec.permission());
    assertEquals(0, spec.tickCost().charge(List.of()));
    assertEquals(EffectClass.DEFERRED_COMMIT, spec.effect());
    assertEquals(BuiltinOwner.SERVER, spec.owner());
  }

  @Test
  void dbDiskSizeExposesTheUnrestrictedExternalServerReadContract() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("db_disk_size").orElseThrow();

    assertEquals(
        List.of(new CallShape(List.of(), List.of(), Optional.empty())), spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.EXTERNAL_READ, spec.effect());
    assertEquals(BuiltinOwner.SERVER, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          new IntegerValue(0),
          value(invoke(catalog, spec, List.of(), transaction, 1)).orElseThrow());
      assertEquals(
          ErrorValue.E_ARGS,
          error(invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 1)
              )
              .orElseThrow());
    }
  }

  @Test
  void outputDelimitersExposesTheExactConnectionReadContract() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("output_delimiters").orElseThrow();

    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.OBJECT)), List.of(), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.EXTERNAL_READ, spec.effect());
    assertEquals(BuiltinOwner.CONNECTION, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          ErrorValue.E_ARGS,
          error(invoke(catalog, spec, List.of(), transaction, 2)).orElseThrow());
      assertEquals(
          ErrorValue.E_TYPE,
          error(invoke(catalog, spec, List.of(string("x")), transaction, 2)
              )
              .orElseThrow());
    }
  }

  @Test
  void memoryUsageExposesToastStatmShapeToEveryProgrammer() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("memory_usage").orElseThrow();

    assertEquals(
        List.of(new CallShape(List.of(), List.of(), Optional.empty())), spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.EXTERNAL_READ, spec.effect());
    assertEquals(BuiltinOwner.SERVER, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      BuiltinResult result = invoke(catalog, spec, List.of(), transaction, 2);
      ListValue usage = assertInstanceOf(ListValue.class, value(result).orElseThrow());
      assertEquals(5, usage.size());
      assertTrue(usage.elements().stream().allMatch(FloatValue.class::isInstance));
      assertEquals(
          ErrorValue.E_ARGS,
          error(invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 2)
              )
              .orElseThrow());
    }
  }

  @Test
  void usageExposesToastResourceShapeToWizardsOnly() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("usage").orElseThrow();
    assertEquals(
        List.of(new CallShape(List.of(), List.of(), Optional.empty())), spec.callShapes());
    assertSame(BuiltinPermissionRule.WIZARD_ONLY, spec.permission());
    assertEquals(EffectClass.EXTERNAL_READ, spec.effect());
    assertEquals(BuiltinOwner.SERVER, spec.owner());
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          ErrorValue.E_PERM,
          error(invoke(catalog, spec, List.of(), transaction, 2)).orElseThrow());
      ListValue result =
          assertInstanceOf(
              ListValue.class,
              value(invoke(catalog, spec, List.of(), transaction, 1)).orElseThrow());
      assertEquals(10, result.size());
      assertEquals(3, assertInstanceOf(ListValue.class, result.elements().getFirst()).size());
      assertInstanceOf(FloatValue.class, result.elements().get(1));
      assertInstanceOf(FloatValue.class, result.elements().get(2));
      assertTrue(result.elements().subList(3, 10).stream().allMatch(IntegerValue.class::isInstance));
    }
  }

  @Test
  void verbCacheStatsAndLogExposeTheLiveToastCountersAndHistogram() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("verb_cache_stats").orElseThrow();
    assertEquals(
        List.of(new CallShape(List.of(), List.of(), Optional.empty())), spec.callShapes());
    assertSame(BuiltinPermissionRule.WIZARD_ONLY, spec.permission());
    assertEquals(EffectClass.EXTERNAL_READ, spec.effect());
    assertEquals(BuiltinOwner.SERVER, spec.owner());
    WorldVerb look = new WorldVerb("look", 1, 4, -1, "return 1;");
    WorldObject wizard =
        new WorldObject(
            1,
            "Wizard",
            4,
            1,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(look),
            List.of());
    WorldObject programmer =
        new WorldObject(2, "Programmer", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldTxn root = new WorldTxn(List.of(), List.of(wizard, programmer));
    try (WorldTxn transaction = root.begin()) {
      assertEquals(
          ErrorValue.E_PERM,
          error(invoke(catalog, spec, List.of(), transaction, 2)).orElseThrow());
      assertTrue(transaction.verb(1, "look").isPresent());
      assertTrue(transaction.verb(1, "LOOK").isPresent());
      ListValue stats =
          assertInstanceOf(
              ListValue.class,
              value(invoke(catalog, spec, List.of(), transaction, 1)).orElseThrow());
      assertEquals(
          new ListValue(
              List.of(
                  new IntegerValue(1),
                  new IntegerValue(0),
                  new IntegerValue(1),
                  new IntegerValue(0),
                  new ListValue(
                      List.of(
                          new IntegerValue(7_506),
                          new IntegerValue(1),
                          new IntegerValue(0),
                          new IntegerValue(0),
                          new IntegerValue(0),
                          new IntegerValue(0),
                          new IntegerValue(0),
                          new IntegerValue(0),
                          new IntegerValue(0),
                          new IntegerValue(0),
                          new IntegerValue(0),
                          new IntegerValue(0),
                          new IntegerValue(0),
                          new IntegerValue(0),
                          new IntegerValue(0),
                          new IntegerValue(0),
                          new IntegerValue(0))))),
          stats);
    }
  }

  @Test
  void logCacheStatsPrintsEveryToastDepthFromTheLiveCache() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec logSpec = catalog.spec("log_cache_stats").orElseThrow();
    WorldVerb look = new WorldVerb("look", 1, 4, -1, "return 1;");
    WorldObject wizard =
        new WorldObject(
            1,
            "Wizard",
            4,
            1,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(look),
            List.of());
    WorldTxn root = new WorldTxn(List.of(), List.of(wizard));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream original = System.err;
    try (WorldTxn transaction = root.begin();
        PrintStream captured = new PrintStream(output, true, StandardCharsets.UTF_8)) {
      assertTrue(transaction.verb(1, "look").isPresent());
      assertTrue(transaction.verb(1, "LOOK").isPresent());
      System.setErr(captured);
      assertEquals(
          new IntegerValue(0),
          value(invoke(catalog, logSpec, List.of(), transaction, 1)).orElseThrow());
    } finally {
      System.setErr(original);
    }

    StringBuilder expected =
        new StringBuilder("Verb cache stat summary: 1 hits, 1 misses, 0 generations\n")
            .append("Depth   Count\n");
    for (int depth = 0; depth < 17; depth++) {
      expected.append(
          String.format(
              Locale.ROOT,
              "%-5d   %-5d%n",
              depth,
              depth == 0 ? 7_506 : depth == 1 ? 1 : 0));
    }
    expected.append("---\n");
    String payloads =
        output.toString(StandardCharsets.UTF_8).lines()
            .map(line -> line.replaceFirst("^[A-Z][a-z]{2} \\d{2} \\d{2}:\\d{2}:\\d{2}: ", ""))
            .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
    assertEquals(expected.toString().replace(System.lineSeparator(), "\n"), payloads);
  }

  @Test
  void waifStatsCountsLiveWaifsByClassWithoutWizardRestriction() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("waif_stats").orElseThrow();
    assertEquals(
        List.of(new CallShape(List.of(), List.of(), Optional.empty())), spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.TRANSACTION_READ, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());
    WorldObject programmer =
        new WorldObject(2, "Programmer", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject waifClass =
        new WorldObject(7, "Waif class", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(programmer, waifClass)).begin()) {
      transaction.createWaif(7, 2);
      transaction.createWaif(7, 2);
      MapValue stats =
          assertInstanceOf(
              MapValue.class,
              value(invoke(catalog, spec, List.of(), transaction, 2)).orElseThrow());
      assertEquals(new IntegerValue(2), stats.get(string("total")).orElseThrow());
      assertEquals(new IntegerValue(0), stats.get(string("pending_recycle")).orElseThrow());
      assertEquals(new IntegerValue(2), stats.get(new ObjectValue(7)).orElseThrow());
    }
  }

  @Test
  void nextRecycledObjectScansInclusivelyBelowToastLastObjectBoundary() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("next_recycled_object").orElseThrow();
    assertEquals(
        List.of(new CallShape(List.of(), List.of(Set.of(ArgType.OBJECT)), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.TRANSACTION_READ, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());

    WorldObject zero =
        new WorldObject(0, "System", 4, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject last =
        new WorldObject(3, "Last", 0, 3, -1, -1, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction =
        new WorldTxn(List.of(), List.of(zero, last), Map.of(), Map.of(), List.of(), 5).begin()) {
      assertEquals(
          new ObjectValue(1),
          value(invoke(catalog, spec, List.of(), transaction, 2)).orElseThrow());
      assertEquals(
          new ObjectValue(2),
          value(invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 2)
              )
              .orElseThrow());
      assertEquals(
          new ObjectValue(4),
          value(invoke(catalog, spec, List.of(new ObjectValue(4)), transaction, 2)
              )
              .orElseThrow());
      assertEquals(
          ErrorValue.E_INVARG,
          error(invoke(catalog, spec, List.of(new ObjectValue(-1)), transaction, 2)
              )
              .orElseThrow());
      assertEquals(
          new IntegerValue(0),
          value(invoke(catalog, spec, List.of(new ObjectValue(5)), transaction, 2)
              )
              .orElseThrow());
      assertEquals(
          ErrorValue.E_INVARG,
          error(invoke(catalog, spec, List.of(new ObjectValue(6)), transaction, 2)
              )
              .orElseThrow());
    }
  }

  @Test
  void recycledObjectsReturnsEveryHoleThroughToastLastObjectBoundary() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("recycled_objects").orElseThrow();
    assertEquals(
        List.of(new CallShape(List.of(), List.of(), Optional.empty())), spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.TRANSACTION_READ, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());

    WorldObject zero =
        new WorldObject(0, "System", 4, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject live =
        new WorldObject(3, "Live", 0, 3, -1, -1, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction =
        new WorldTxn(List.of(), List.of(zero, live), Map.of(), Map.of(), List.of(), 5).begin()) {
      assertEquals(
          new ListValue(
              List.of(
                  new ObjectValue(1),
                  new ObjectValue(2),
                  new ObjectValue(4),
                  new ObjectValue(5))),
          value(invoke(catalog, spec, List.of(), transaction, 2)).orElseThrow());
      assertEquals(
          ErrorValue.E_ARGS,
          error(invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 2)
              )
              .orElseThrow());
    }
  }

  @Test
  void resetMaxObjectDropsTrailingRecycledSlotsForWizardsOnly() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("reset_max_object").orElseThrow();
    assertEquals(
        List.of(new CallShape(List.of(), List.of(), Optional.empty())), spec.callShapes());
    assertSame(BuiltinPermissionRule.WIZARD_ONLY, spec.permission());
    assertEquals(EffectClass.TRANSACTION_WRITE, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());

    WorldObject programmer =
        new WorldObject(2, "Programmer", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject wizard =
        new WorldObject(3, "Wizard", 4, 3, -1, -1, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction =
        new WorldTxn(List.of(), List.of(programmer, wizard), Map.of(), Map.of(), List.of(), 8)
            .begin()) {
      assertEquals(
          ErrorValue.E_PERM,
          error(invoke(catalog, spec, List.of(), transaction, 2)).orElseThrow());
      assertEquals(
          new IntegerValue(0),
          value(invoke(catalog, spec, List.of(), transaction, 3)).orElseThrow());
      assertEquals(3, transaction.maximumObjectId());
      assertEquals(4, transaction.createObject(-1, 3).id());
    }
  }

  @Test
  void ownedObjectsScansLiveObjectsInNumericOrderAndRejectsInvalidOwners() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("owned_objects").orElseThrow();
    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.OBJECT)), List.of(), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.TRANSACTION_READ, spec.effect());
    assertEquals(BuiltinOwner.WORLD, spec.owner());

    WorldObject owner =
        new WorldObject(1, "Owner", 0, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject first =
        new WorldObject(3, "First", 0, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject other =
        new WorldObject(4, "Other", 0, 4, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject second =
        new WorldObject(7, "Second", 0, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    try (WorldTxn transaction =
        new WorldTxn(List.of(), List.of(second, other, owner, first), Map.of(), Map.of(), List.of(), 8)
            .begin()) {
      assertEquals(
          new ListValue(List.of(new ObjectValue(1), new ObjectValue(3), new ObjectValue(7))),
          value(invoke(catalog, spec, List.of(new ObjectValue(1)), transaction, 2)
              )
              .orElseThrow());
      assertEquals(
          ErrorValue.E_INVIND,
          error(invoke(catalog, spec, List.of(new ObjectValue(8)), transaction, 2)
              )
              .orElseThrow());
    }
  }

  @Test
  void exposesTheExactPureValueConversionContracts() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());

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
  void toobjSaturatesDecimalOverflowAndKeepsMalformedStringsAtZero() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("toobj").orElseThrow();
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          new ObjectValue(Long.MAX_VALUE),
          value(invoke(catalog, spec, List.of(string("9223372036854775808")), transaction, 1)
              )
              .orElseThrow());
      assertEquals(
          new ObjectValue(Long.MIN_VALUE),
          value(invoke(catalog, spec, List.of(string("#-9223372036854775809")), transaction, 1)
              )
              .orElseThrow());
      assertEquals(
          new ObjectValue(0),
          value(invoke(catalog, spec, List.of(string("9223372036854775808x")), transaction, 1)
              )
              .orElseThrow());
      assertEquals(
          new ObjectValue(0),
          value(invoke(catalog, spec, List.of(string("#")), transaction, 1)
              )
              .orElseThrow());
    }
  }

  @Test
  void exposesTheExactPureStringContracts() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    Set<ArgType> any = Set.of(ArgType.ANY);
    Set<ArgType> integer = Set.of(ArgType.INTEGER);
    Set<ArgType> string = Set.of(ArgType.STRING);

    assertPureVmContract(
        catalog,
        "strsub",
        new CallShape(List.of(string, string, string), List.of(any), Optional.empty()));
    assertPureVmContract(
        catalog,
        "strtr",
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
  void parseAnsiPreservesToastTagTableCaseAndUnknownText() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("parse_ansi").orElseThrow();
    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.STRING)), List.of(), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.IRREVOCABLE, spec.effect());
    assertEquals(BuiltinOwner.VM, spec.owner());

    try (WorldTxn transaction = world().begin()) {
      assertString(
          "\u001b[31mhello\u001b[0m\u0007",
          invoke(
              catalog,
              spec,
              List.of(string("[RED]hello[normal][beep][null]")),
              transaction,
              2));
      assertString(
          "[unknown]plain",
          invoke(catalog, spec, List.of(string("[unknown]plain")), transaction, 2));
      StringValue randomValue =
          (StringValue)
              value(invoke(catalog, spec, List.of(string("[random]")), transaction, 2)
                  )
                  .orElseThrow();
      assertTrue(
          Set.of("\u001b[31m", "\u001b[32m", "\u001b[33m", "\u001b[34m", "\u001b[35m")
              .contains(decode(randomValue)));
    }
  }

  @Test
  void removeAnsiStripsOnlyToastTagsCaseInsensitively() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("remove_ansi").orElseThrow();
    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.STRING)), List.of(), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.PURE, spec.effect());
    assertEquals(BuiltinOwner.VM, spec.owner());

    try (WorldTxn transaction = world().begin()) {
      assertString(
          "hello world",
          invoke(
              catalog,
              spec,
              List.of(string("[RED]hello[normal] [b:cyan]world[null]")),
              transaction,
              2));
      assertString(
          "[unknown]text",
          invoke(catalog, spec, List.of(string("[unknown]text")), transaction, 2));
      assertString(
          "text",
          invoke(
              catalog,
              spec,
              List.of(string("[bold][unbright][beep][random][grey]text")),
              transaction,
              2));
    }
  }

  @Test
  void simplexNoiseUsesToastFloatOnlyDimensionsAndDeterministicValues() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("simplex_noise").orElseThrow();
    assertEquals(
        List.of(new CallShape(List.of(Set.of(ArgType.LIST)), List.of(), Optional.empty())),
        spec.callShapes());
    assertSame(BuiltinPermissionRule.ANY, spec.permission());
    assertEquals(EffectClass.PURE, spec.effect());
    assertEquals(BuiltinOwner.VM, spec.owner());

    try (WorldTxn transaction = world().begin()) {
      BuiltinResult first =
          invoke(
              catalog,
              spec,
              List.of(new ListValue(List.of(new FloatValue(0.25)))),
              transaction,
              2);
      BuiltinResult second =
          invoke(
              catalog,
              spec,
              List.of(new ListValue(List.of(new FloatValue(0.25)))),
              transaction,
              2);
      assertEquals(value(first), value(second));
      assertInstanceOf(FloatValue.class, value(first).orElseThrow());
      assertEquals(
          ErrorValue.E_TYPE,
          error(invoke(
                  catalog,
                  spec,
                  List.of(new ListValue(List.of(new IntegerValue(1)))),
                  transaction,
                  2)
              )
              .orElseThrow());
      assertEquals(
          ErrorValue.E_TYPE,
          value(invoke(catalog, spec, List.of(new ListValue(List.of())), transaction, 2)
              )
              .orElseThrow());
      assertInstanceOf(
          FloatValue.class,
          value(invoke(
                  catalog,
                  spec,
                  List.of(
                      new ListValue(
                          List.of(
                              new FloatValue(0),
                              new FloatValue(0),
                              new FloatValue(0),
                              new FloatValue(0)))),
                  transaction,
                  2)
              )
              .orElseThrow());
    }
  }

  @Test
  void stringBuiltinsPreserveToastSearchSubstitutionAndComparisonSemantics() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
          error(invoke(
                  catalog,
                  catalog.spec("strsub").orElseThrow(),
                  List.of(string("foo"), string(""), string("x")),
                  transaction,
                  1)
              ));

      assertString(
          "FxXbar",
          invoke(
              catalog,
              catalog.spec("strtr").orElseThrow(),
              List.of(string("FoObar"), string("o"), string("x")),
              transaction,
              1));
      assertString(
          "FxObar",
          invoke(
              catalog,
              catalog.spec("strtr").orElseThrow(),
              List.of(string("FoObar"), string("o"), string("x"), new IntegerValue(1)),
              transaction,
              1));
      assertString(
          "fbbar",
          invoke(
              catalog,
              catalog.spec("strtr").orElseThrow(),
              List.of(string("foobar"), string("ob"), string("b")),
              transaction,
              1));
      assertString(
          "4444",
          invoke(
              catalog,
              catalog.spec("strtr").orElseThrow(),
              List.of(string("xXxX"), string("xXxX"), string("1234")),
              transaction,
              1));

      assertEquals(
          Optional.of(new IntegerValue(4)),
          value(invoke(
                  catalog,
                  catalog.spec("index").orElseThrow(),
                  List.of(string("fooBar"), string("bar")),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  catalog.spec("index").orElseThrow(),
                  List.of(string("fooBar"), string("bar"), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new IntegerValue(7)),
          value(invoke(
                  catalog,
                  catalog.spec("rindex").orElseThrow(),
                  List.of(string("bazbarBazfoo"), string("baz")),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(invoke(
                  catalog,
                  catalog.spec("rindex").orElseThrow(),
                  List.of(string("bazbarBazfoo"), string("baz"), new IntegerValue(1)),
                  transaction,
                  1)
              ));

      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(invoke(
                  catalog,
                  catalog.spec("strcmp").orElseThrow(),
                  List.of(string("abc"), string("ABC")),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new IntegerValue(-1)),
          value(invoke(
                  catalog,
                  catalog.spec("strcmp").orElseThrow(),
                  List.of(string("abc"), string("abcd")),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void cryptPreservesTheStockUnixVectorAndUnsupportedPrefixMarker() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("crypt").orElseThrow();
    assertEquals(EffectClass.IRREVOCABLE, spec.effect());
    try (WorldTxn transaction = world().begin()) {
      assertString(
          "SAEmC5UwrAl2A",
          invoke(catalog, spec, List.of(string("foobar"), string("SA")), transaction, 1));
      assertString(
          "*0",
          invoke(
              catalog,
              spec,
              List.of(string("password"), string("$2b$05$1234567890123456")),
              transaction,
              1));
      String salt = "$2x$05$KRGxLBS0Lxe3KBCwKxOzLe";
      assertString(
          "$2x$05$KRGxLBS0Lxe3KBCwKxOzLeUBmcrGSTvDf3LosTOIeZOfIAiEwGhRq",
          invoke(
              catalog,
              spec,
              List.of(StringValue.of(new byte[] {(byte) 128}), string(salt)),
              transaction,
              1));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  spec,
                  List.of(string("foobar"), string("$2y$10$KRGxLBS0Lxe3KBCwKxOzLe")),
                  transaction,
                  2)
              ));
    }
  }

  @Test
  void argon2PreservesTheStockVectorVerificationAndWizardBoundary() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec hash = catalog.spec("argon2").orElseThrow();
    BuiltinSpec verify = catalog.spec("argon2_verify").orElseThrow();
    String expected =
        "$argon2id$v=19$m=1024,t=2,p=1$c2FsdHNhbHQ$gg7MDf+O0u4Yh4jYvTjXJps6cRjBIQvJ4r7MQOv1A58";
    try (WorldTxn transaction = world().begin()) {
      assertString(
          expected,
          invoke(
              catalog,
              hash,
              List.of(
                  string("password"),
                  string("saltsalt"),
                  new IntegerValue(2),
                  new IntegerValue(1024),
                  new IntegerValue(1)),
              transaction,
              1));
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(invoke(catalog, verify, List.of(string(expected), string("password")), transaction, 1)
              ));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(catalog, verify, List.of(string(expected), string("wrong")), transaction, 1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  hash,
                  List.of(string("password"), string("saltsalt")),
                  transaction,
                  2)
              ));
    }
  }

  @Test
  void binaryBuiltinsPreserveToastByteGroupingEscapesAndErrors() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    try (WorldTxn transaction = world().begin()) {
      BuiltinResult decoded =
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
          value(decoded));
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      new IntegerValue(102),
                      new IntegerValue(111),
                      new IntegerValue(111),
                      new IntegerValue(13),
                      new IntegerValue(10)))),
          value(invoke(
                  catalog,
                  catalog.spec("decode_binary").orElseThrow(),
                  List.of(string("foo~0D~0A"), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  catalog.spec("decode_binary").orElseThrow(),
                  List.of(string("~ZZ")),
                  transaction,
                  1)
              ));

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
          error(invoke(
                  catalog,
                  catalog.spec("encode_binary").orElseThrow(),
                  List.of(new IntegerValue(256)),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void chrPreservesToastVariadicRecursiveAndProgrammerRangeSemantics() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("chr").orElseThrow();
    assertEquals(
        List.of(new CallShape(List.of(), List.of(), Optional.of(Set.of(ArgType.ANY)))),
        spec.callShapes());
    try (WorldTxn transaction = world().begin()) {
      assertString("", invoke(catalog, spec, List.of(), transaction, 2));
      assertString(
          "Hello",
          invoke(
              catalog,
              spec,
              List.of(
                  new IntegerValue(72),
                  string("ell"),
                  new ListValue(List.of(new IntegerValue(111)))),
              transaction,
              2));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, spec, List.of(new IntegerValue(10)), transaction, 2)));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, spec, List.of(new IntegerValue(255)), transaction, 2)));
      assertString("\n", invoke(catalog, spec, List.of(new IntegerValue(10)), transaction, 1));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, spec, List.of(new FloatValue(65)), transaction, 1)));
    }
  }

  @Test
  void exposesTheExactListContracts() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    Set<ArgType> any = Set.of(ArgType.ANY);
    Set<ArgType> integer = Set.of(ArgType.INTEGER);
    Set<ArgType> list = Set.of(ArgType.LIST);

    Map<String, CallShape> worldBackedShapes =
        Map.of(
            "listappend",
            new CallShape(List.of(list, any), List.of(integer), Optional.empty()),
            "listinsert",
            new CallShape(List.of(list, any), List.of(integer), Optional.empty()),
            "listdelete",
            new CallShape(List.of(list, integer), List.of(), Optional.empty()),
            "listset",
            new CallShape(List.of(list, any, integer), List.of(), Optional.empty()),
            "setadd",
            new CallShape(List.of(list, any), List.of(), Optional.empty()),
            "setremove",
            new CallShape(List.of(list, any), List.of(), Optional.empty()));
    for (Map.Entry<String, CallShape> entry : worldBackedShapes.entrySet()) {
      BuiltinSpec spec = catalog.spec(entry.getKey()).orElseThrow();
      assertEquals(List.of(entry.getValue()), spec.callShapes());
      assertSame(BuiltinPermissionRule.ANY, spec.permission());
      assertEquals(0, spec.tickCost().charge(List.of()));
      assertEquals(EffectClass.TRANSACTION_READ, spec.effect());
      assertEquals(BuiltinOwner.WORLD, spec.owner());
    }
    assertPureVmContract(
        catalog, "reverse", new CallShape(List.of(any), List.of(), Optional.empty()));
  }

  @Test
  void listBuiltinsPreserveToastInsertionMutationSetAndRangeSemantics() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    ListValue oneTwo = new ListValue(List.of(new IntegerValue(1), new IntegerValue(2)));
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(new IntegerValue(1), new IntegerValue(2), new IntegerValue(3)))),
          value(invoke(
                  catalog,
                  catalog.spec("listappend").orElseThrow(),
                  List.of(oneTwo, new IntegerValue(3)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(new IntegerValue(1), new IntegerValue(3), new IntegerValue(2)))),
          value(invoke(
                  catalog,
                  catalog.spec("listappend").orElseThrow(),
                  List.of(oneTwo, new IntegerValue(3), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(new IntegerValue(3), new IntegerValue(1), new IntegerValue(2)))),
          value(invoke(
                  catalog,
                  catalog.spec("listinsert").orElseThrow(),
                  List.of(oneTwo, new IntegerValue(3), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new ListValue(List.of(new IntegerValue(1), new IntegerValue(3)))),
          value(invoke(
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
              ));
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(new IntegerValue(1), new IntegerValue(4), new IntegerValue(3)))),
          value(invoke(
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
              ));
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(new IntegerValue(1), new IntegerValue(2), new IntegerValue(3)))),
          value(invoke(
                  catalog,
                  catalog.spec("setadd").orElseThrow(),
                  List.of(oneTwo, new IntegerValue(3)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(oneTwo),
          value(invoke(
                  catalog,
                  catalog.spec("setadd").orElseThrow(),
                  List.of(oneTwo, new IntegerValue(2)),
                  transaction,
                  1)
              ));

      for (String name : List.of("listdelete", "listset")) {
        List<MooValue> arguments =
            name.equals("listdelete")
                ? List.of(oneTwo, new IntegerValue(0))
                : List.of(oneTwo, new IntegerValue(9), new IntegerValue(0));
        assertEquals(
            Optional.of(ErrorValue.E_RANGE),
            error(invoke(
                    catalog,
                    catalog.spec(name).orElseThrow(),
                    arguments,
                    transaction,
                    1)
                ),
            name);
      }
    }
  }

  @Test
  void setaddEnforcesTheConfiguredListValueByteLimit() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    WorldObject system =
        new WorldObject(
            0,
            "System",
            0,
            1,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new WorldProperty(
                    "server_options", new ObjectValue(3), 1, 0, false, true)));
    WorldObject wizard =
        new WorldObject(1, "Wizard", 4, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject options =
        new WorldObject(
            3,
            "Options",
            0,
            1,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new WorldProperty(
                    "max_list_value_bytes", new IntegerValue(1_021), 1, 0, false, true),
                new WorldProperty(
                    "max_concat_catchable", new IntegerValue(1), 1, 0, false, true)));
    List<MooValue> elements = new ArrayList<>();
    for (int value = 1; value <= 61; value++) {
      elements.add(new IntegerValue(value));
    }
    ListValue withinLimit = new ListValue(elements);
    ListValue alreadyOverLimit = withinLimit.append(new IntegerValue(62));

    try (WorldTxn transaction = new WorldTxn(List.of(), List.of(system, wizard, options)).begin()) {
      assertEquals(
          Optional.of(ErrorValue.E_QUOTA),
          error(invoke(
                  catalog,
                  catalog.spec("setadd").orElseThrow(),
                  List.of(withinLimit, new IntegerValue(62)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_QUOTA),
          error(invoke(
                  catalog,
                  catalog.spec("setadd").orElseThrow(),
                  List.of(alreadyOverLimit, new IntegerValue(62)),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void setRemoveUsesRecursiveCaseInsensitiveMooEqualityAndRemovesOnlyTheFirstMatch() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
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
          value(invoke(catalog, spec, List.of(source, sought), transaction, 1)));
      assertSame(
          absent,
          value(invoke(catalog, spec, List.of(absent, string("missing")), transaction, 1)
              )
              .orElseThrow());
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(catalog, spec, List.of(), transaction, 1)));
      assertEquals(
          Optional.of(ErrorValue.E_ARGS),
          error(invoke(
                  catalog,
                  spec,
                  List.of(absent, new IntegerValue(1), new IntegerValue(2)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(1), new IntegerValue(1)),
                  transaction,
                  1)
              ));
    }
  }

  @Test
  void equalUsesToastBooleanIntegerRelationRecursivelyAndCaseSensitiveStrings() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("equal").orElseThrow();
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(0), BooleanValue.FALSE),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(invoke(
                  catalog,
                  spec,
                  List.of(BooleanValue.TRUE, new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(
                  catalog,
                  spec,
                  List.of(new IntegerValue(0), BooleanValue.TRUE),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(invoke(
                  catalog,
                  spec,
                  List.of(
                      new ListValue(List.of(new IntegerValue(0))),
                      new ListValue(List.of(BooleanValue.FALSE))),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(catalog, spec, List.of(string("alpha"), string("ALPHA")), transaction, 1)
              ));
    }
  }

  @Test
  void setThreadModeAndAllMembersExposeCanonicalContractsAndExecutionModes() throws Exception {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec setThreadMode = catalog.spec("set_thread_mode").orElseThrow();
    BuiltinSpec allMembers = catalog.spec("all_members").orElseThrow();
    ListValue source =
        new ListValue(List.of(string("A"), new IntegerValue(7), string("a")));

    assertEquals(
        List.of(new CallShape(List.of(), List.of(Set.of(ArgType.INTEGER)), Optional.empty())),
        setThreadMode.callShapes());
    assertSame(BuiltinPermissionRule.ANY, setThreadMode.permission());
    assertEquals(0, setThreadMode.tickCost().charge(List.of()));
    assertEquals(EffectClass.DEFERRED_COMMIT, setThreadMode.effect());
    assertEquals(BuiltinOwner.VM, setThreadMode.owner());
    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.ANY), Set.of(ArgType.LIST)),
                List.of(),
                Optional.empty())),
        allMembers.callShapes());
    assertSame(BuiltinPermissionRule.ANY, allMembers.permission());
    assertEquals(0, allMembers.tickCost().charge(List.of()));
    assertEquals(EffectClass.SUSPENDING_HOST, allMembers.effect());
    assertEquals(BuiltinOwner.VM, allMembers.owner());

    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(new IntegerValue(1)),
          value(invoke(catalog, setThreadMode, List.of(), transaction, 1, true)));
      assertEquals(
          Optional.of(false),
          threadMode(invoke(
                  catalog,
                  setThreadMode,
                  List.of(new IntegerValue(0)),
                  transaction,
                  1,
                  true)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(catalog, setThreadMode, List.of(string("no")), transaction, 1, true)));

      BuiltinResult threaded =
          invoke(catalog, allMembers, List.of(string("a"), source), transaction, 1, true);
      assertTrue(value(threaded).isEmpty());
      assertEquals(
          Optional.of(new ListValue(List.of(new IntegerValue(1), new IntegerValue(3)))),
          value(hostWork(threaded).orElseThrow().call()));
      assertEquals(
          Optional.of(new ListValue(List.of(new IntegerValue(1), new IntegerValue(3)))),
          value(invoke(catalog, allMembers, List.of(string("a"), source), transaction, 1, false)
              ));
    }
  }

  @Test
  void sortExposesPinnedHostContractAndToastOrdering() throws Exception {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec sort = catalog.spec("sort").orElseThrow();
    ListValue integers =
        new ListValue(List.of(new IntegerValue(3), new IntegerValue(1), new IntegerValue(2)));
    ListValue ascending =
        new ListValue(List.of(new IntegerValue(1), new IntegerValue(2), new IntegerValue(3)));

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.LIST)),
                List.of(
                    Set.of(ArgType.LIST),
                    Set.of(ArgType.INTEGER),
                    Set.of(ArgType.INTEGER)),
                Optional.empty())),
        sort.callShapes());
    assertSame(BuiltinPermissionRule.ANY, sort.permission());
    assertEquals(0, sort.tickCost().charge(List.of()));
    assertEquals(EffectClass.SUSPENDING_HOST, sort.effect());
    assertEquals(BuiltinOwner.VM, sort.owner());

    try (WorldTxn transaction = world().begin()) {
      BuiltinResult threaded = invoke(catalog, sort, List.of(integers), transaction, 1, true);
      assertTrue(value(threaded).isEmpty());
      assertEquals(Optional.of(ascending), value(hostWork(threaded).orElseThrow().call()));
      assertEquals(
          Optional.of(ascending),
          value(invoke(catalog, sort, List.of(integers), transaction, 1, false)));
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(new IntegerValue(3), new IntegerValue(2), new IntegerValue(1)))),
          value(invoke(
                  catalog,
                  sort,
                  List.of(
                      integers,
                      new ListValue(List.of()),
                      new IntegerValue(0),
                      new IntegerValue(1)),
                  transaction,
                  1,
                  false)
              ));

      ListValue values =
          new ListValue(List.of(string("third"), string("first"), string("second")));
      ListValue keys =
          new ListValue(List.of(new IntegerValue(30), new IntegerValue(10), new IntegerValue(20)));
      assertEquals(
          Optional.of(
              new ListValue(List.of(string("first"), string("second"), string("third")))),
          value(invoke(catalog, sort, List.of(values, keys), transaction, 1, false)));
      assertEquals(
          Optional.of(new ListValue(List.of(string("x2"), string("x10")))),
          value(invoke(
                  catalog,
                  sort,
                  List.of(
                      new ListValue(List.of(string("x10"), string("x2"))),
                      new ListValue(List.of()),
                      new IntegerValue(1)),
                  transaction,
                  1,
                  false)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  sort,
                  List.of(values, new ListValue(List.of(new IntegerValue(1)))),
                  transaction,
                  1,
                  false)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  sort,
                  List.of(new ListValue(List.of(new FloatValue(2.0), new IntegerValue(1)))),
                  transaction,
                  1,
                  false)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_TYPE),
          error(invoke(
                  catalog,
                  sort,
                  List.of(
                      new ListValue(
                          List.of(
                              new WaifValue(new ObjectValue(1), new ObjectValue(1)),
                              new WaifValue(new ObjectValue(1), new ObjectValue(1))))),
                  transaction,
                  1,
                  false)
              ));
    }
  }

  @Test
  void threadPoolAndThreadsExposePinnedTaskContractsAndValidation() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec threadPool = catalog.spec("thread_pool").orElseThrow();
    BuiltinSpec threads = catalog.spec("threads").orElseThrow();

    assertEquals(
        List.of(
            new CallShape(
                List.of(Set.of(ArgType.STRING), Set.of(ArgType.STRING)),
                List.of(Set.of(ArgType.INTEGER)),
                Optional.empty())),
        threadPool.callShapes());
    assertSame(BuiltinPermissionRule.WIZARD_ONLY, threadPool.permission());
    assertEquals(0, threadPool.tickCost().charge(List.of()));
    assertEquals(EffectClass.IRREVOCABLE, threadPool.effect());
    assertEquals(BuiltinOwner.TASK, threadPool.owner());
    assertEquals(
        List.of(new CallShape(List.of(), List.of(), Optional.empty())), threads.callShapes());
    assertSame(BuiltinPermissionRule.WIZARD_ONLY, threads.permission());
    assertEquals(0, threads.tickCost().charge(List.of()));
    assertEquals(EffectClass.IRREVOCABLE, threads.effect());
    assertEquals(BuiltinOwner.TASK, threads.owner());

    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(
                  catalog,
                  threadPool,
                  List.of(string("INIT"), string("MAIN"), new IntegerValue(2)),
                  transaction,
                  2)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  threadPool,
                  List.of(string("INIT"), string("NOPE"), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  threadPool,
                  List.of(string("RESET"), string("MAIN"), new IntegerValue(1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(
                  catalog,
                  threadPool,
                  List.of(string("INIT"), string("MAIN"), new IntegerValue(-1)),
                  transaction,
                  1)
              ));
      assertEquals(
          Optional.of(ErrorValue.E_PERM),
          error(invoke(catalog, threads, List.of(), transaction, 2)));
    }
  }

  @Test
  void functionInfoDescribesDumpDatabaseFromTheManifest() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    try (WorldTxn transaction = world().begin()) {
      BuiltinResult result =
          invoke(
              catalog,
              catalog.spec("function_info").orElseThrow(),
              List.of(StringValue.of("dump_database")),
              transaction,
              1);

      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      StringValue.of("dump_database"),
                      new IntegerValue(0),
                      new IntegerValue(0),
                      new ListValue(List.of())))),
          value(result));
    }
  }

  @Test
  void setTaskPermsAllowsOnlySelfOrWizardSelectedProgrammers() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("set_task_perms").orElseThrow();
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          new BuiltinResult.Programmer(2),
          invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 2));
      assertEquals(
          new BuiltinResult.ErrorResult(ErrorValue.E_PERM),
          invoke(catalog, spec, List.of(new ObjectValue(1)), transaction, 2));
      assertEquals(
          new BuiltinResult.Programmer(2),
          invoke(catalog, spec, List.of(new ObjectValue(2)), transaction, 1));
    }
  }

  @Test
  void serverVersionExposesToastCompatibleVersionMetadataWithoutExtraFeatures() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("server_version").orElseThrow();
    try (WorldTxn transaction = world().begin()) {
      assertEquals(
          Optional.of(string("0.1.0-SNAPSHOT")),
          value(invoke(catalog, spec, List.of(), transaction, 1)));
      assertEquals(
          Optional.of(new ListValue(List.of())),
          value(invoke(catalog, spec, List.of(string("features")), transaction, 1)));
      assertEquals(
          Optional.of(new IntegerValue(0)),
          value(invoke(catalog, spec, List.of(string("major")), transaction, 1)));
      assertEquals(
          Optional.of(string("0.1.0-SNAPSHOT")),
          value(invoke(catalog, spec, List.of(string("string")), transaction, 1)));
      assertInstanceOf(
          ListValue.class,
          value(invoke(catalog, spec, List.of(string("")), transaction, 1)).orElseThrow());
      assertInstanceOf(
          ListValue.class,
          value(invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 1)
              )
              .orElseThrow());
      assertEquals(
          Optional.of(ErrorValue.E_INVARG),
          error(invoke(catalog, spec, List.of(string("missing")), transaction, 1)));

      BuiltinResult functionInfo =
          invoke(
              catalog,
              catalog.spec("function_info").orElseThrow(),
              List.of(string("server_version")),
              transaction,
              1);
      assertEquals(
          Optional.of(
              new ListValue(
                  List.of(
                      string("server_version"),
                      new IntegerValue(0),
                      new IntegerValue(1),
                      new ListValue(List.of(new IntegerValue(-1)))))),
          value(functionInfo));
    }
  }

  @Test
  void dumpDatabaseReturnsZeroAndAValueOnlyCheckpointRequestForWizards() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    try (WorldTxn transaction = world().begin()) {
      BuiltinResult result =
          invoke(
              catalog,
              catalog.spec("dump_database").orElseThrow(),
              List.of(),
              transaction,
              1);

      assertEquals(new BuiltinResult.Checkpoint(), result);
    }
  }

  @Test
  void dumpDatabaseRejectsArgumentsAndNonWizardProgrammersBeforeRequestingCheckpoint() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("dump_database").orElseThrow();
    try (WorldTxn transaction = world().begin()) {
      BuiltinResult arguments = invoke(catalog, spec, List.of(new IntegerValue(1)), transaction, 1);
      BuiltinResult permission = invoke(catalog, spec, List.of(), transaction, 2);

      assertEquals(Optional.of(ErrorValue.E_ARGS), error(arguments));
      assertTrue(checkpointRequest(arguments).isEmpty());
      assertEquals(Optional.of(ErrorValue.E_PERM), error(permission));
      assertTrue(checkpointRequest(permission).isEmpty());
    }
  }

  @Test
  void shutdownTruthFlagSelectsPanicDumpWhileFalseRemainsClean() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BuiltinSpec spec = catalog.spec("shutdown").orElseThrow();
    try (WorldTxn transaction = world().begin()) {
      BuiltinResult panic =
          invoke(
              catalog,
              spec,
              List.of(string("panic reason"), new IntegerValue(1)),
              transaction,
              1);
      BuiltinResult clean =
          invoke(
              catalog,
              spec,
              List.of(string("clean reason"), new IntegerValue(0)),
              transaction,
              1);

      assertEquals(Optional.of(CheckpointRequest.panic("panic reason")), checkpointRequest(panic));
      assertEquals(Optional.of(new CheckpointRequest(true)), checkpointRequest(clean));
      assertTrue(error(panic).isEmpty());
      assertTrue(error(clean).isEmpty());
    }
  }

  private static Optional<MooValue> value(BuiltinResult result) {
    return result instanceof BuiltinResult.Value value
        ? Optional.of(value.value())
        : Optional.empty();
  }

  private static Optional<ErrorValue> error(BuiltinResult result) {
    return switch (result) {
      case BuiltinResult.ErrorResult error -> Optional.of(error.error());
      case BuiltinResult.RaisedError raised -> Optional.of(raised.error());
      default -> Optional.empty();
    };
  }

  private static Optional<String> dynamicSource(BuiltinResult result) {
    return result instanceof BuiltinResult.DynamicEval dynamic
        ? Optional.of(dynamic.source())
        : Optional.empty();
  }

  private static OptionalLong switchedPlayer(BuiltinResult result) {
    return result instanceof BuiltinResult.SwitchPlayer switched
        ? OptionalLong.of(switched.player())
        : OptionalLong.empty();
  }

  private static OptionalLong recycleTarget(BuiltinResult result) {
    return result instanceof BuiltinResult.Recycle recycle
        ? OptionalLong.of(recycle.object())
        : OptionalLong.empty();
  }

  private static OptionalDouble delaySeconds(BuiltinResult result) {
    return result instanceof BuiltinResult.Suspend suspend
        ? OptionalDouble.of(suspend.seconds())
        : OptionalDouble.empty();
  }

  private static Optional<Callable<BuiltinResult>> hostWork(BuiltinResult result) {
    return result instanceof BuiltinResult.HostWork hostWork
        ? Optional.of(hostWork.work())
        : Optional.empty();
  }

  private static Optional<ListValue> errorDetails(BuiltinResult result) {
    return result instanceof BuiltinResult.RaisedError raised
        ? Optional.of(raised.details())
        : Optional.empty();
  }

  private static Optional<CheckpointRequest> checkpointRequest(BuiltinResult result) {
    return switch (result) {
      case BuiltinResult.Checkpoint _ -> Optional.of(new CheckpointRequest(false));
      case BuiltinResult.Shutdown _ -> Optional.of(new CheckpointRequest(true));
      case BuiltinResult.Panic panic -> Optional.of(CheckpointRequest.panic(panic.message()));
      default -> Optional.empty();
    };
  }

  private static Optional<Boolean> threadMode(BuiltinResult result) {
    return result instanceof BuiltinResult.ThreadMode mode
        ? Optional.of(mode.enabled())
        : Optional.empty();
  }

  private static boolean abortSeconds(BuiltinResult result) {
    return result instanceof BuiltinResult.SecondsAbort;
  }

  private static BuiltinResult invoke(
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

  private static BuiltinResult invoke(
      BuiltinCatalog catalog,
      BuiltinSpec spec,
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      boolean threadMode) {
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
        new ListValue(List.of()),
        threadMode);
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
    return StringValue.of(value);
  }

  private static String decode(StringValue value) {
    return value.text();
  }

  private static void assertString(String expected, BuiltinResult actual) {
    StringValue value = (StringValue) value(actual).orElseThrow();
    assertArrayEquals(StringValue.of(expected).bytes(), value.bytes());
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

  private static final class UpperEndpointRandom extends Random {
    private static final long serialVersionUID = 1L;

    @Override
    public int nextInt() {
      return -1;
    }
  }

  private static final class RecordingListener implements BuiltinCatalog.ListenerControl {
    private long handler;
    private int port;
    private boolean printMessages;
    private long bufferedOutputLength;
    private long bufferedOutputConnectionId;

    @Override
    public int listen(
        long handler,
        int port,
        boolean ipv6,
        boolean printMessages,
        String interfaceAddress) {
      this.handler = handler;
      this.port = port;
      this.printMessages = printMessages;
      return port;
    }

    @Override
    public List<BuiltinCatalog.ListenerDescription> listeners() {
      return List.of(
          new BuiltinCatalog.ListenerDescription(handler, port, port, false, printMessages, "127.0.0.1"));
    }

    @Override
    public boolean unlisten(int port, boolean ipv6) {
      return false;
    }

    @Override
    public long openNetworkConnection(String host, int port, boolean ipv6, long listenerHandler) {
      return -77;
    }

    @Override
    public void writeConnection(long connectionId, List<String> output) {}

    @Override
    public void notifyConnection(
        long connectionId, String line, boolean noFlush, boolean noNewline) {}

    @Override
    public void bootConnection(long connectionId, List<String> output) {}

    @Override
    public void setConnectionBinary(long connectionId, boolean binary) {}

    @Override
    public long bufferedOutputLength(long connectionId) {
      bufferedOutputConnectionId = connectionId;
      return bufferedOutputLength;
    }

    @Override
    public void shutdown() {}

    @Override
    public void panic() {}
  }
}
