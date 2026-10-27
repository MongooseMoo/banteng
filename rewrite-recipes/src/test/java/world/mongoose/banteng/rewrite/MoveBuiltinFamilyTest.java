package world.mongoose.banteng.rewrite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.config.Environment;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

final class MoveBuiltinFamilyTest {
  @Test
  void movesServerGroupsWithCatalogOwnedLoggingAndLiveCatalogLookup() {
    var context = new InMemoryExecutionContext(failure -> { throw new AssertionError(failure); });
    var files = JavaParser.fromJavaVersion().build().parse(context, """
        package world.mongoose.banteng.builtin;
        import java.util.List;
        import java.util.Locale;
        import world.mongoose.banteng.logging.ServerLog;
        import static world.mongoose.banteng.builtin.ValueLimits.valueBytes;
        class BuiltinCatalog {
          private static final String SERVER_VERSION = "0.1.0-SNAPSHOT";
          private List<BuiltinSpec> manifest;
          private Map<String, BuiltinSpec> specs;
          void buildManifest() {
            entries.add(new BuiltinSpec("value_bytes", List.of(ANY), BuiltinPermissionRule.ANY, false, call -> valueBytes(call.arguments(), call.world())));
            middle();
            entries.add(new BuiltinSpec("server_version", List.of(), BuiltinPermissionRule.ANY, false, call -> serverVersion()));
            entries.add(new BuiltinSpec("function_info", List.of(), BuiltinPermissionRule.ANY, false, call -> functionInfo(call.arguments())));
            files();
            entries.add(new BuiltinSpec("server_log", List.of(STRING), BuiltinPermissionRule.WIZARD_ONLY, true, call -> serverLog(call.arguments())));
            entries.add(new BuiltinSpec("log_cache_stats", List.of(), BuiltinPermissionRule.WIZARD_ONLY, true, call -> logCacheStats(call.world())));
            entries.add(new BuiltinSpec("load_server_options", List.of(), BuiltinPermissionRule.WIZARD_ONLY, true, call -> { reloadProtectedFunctions(call.world()); return null; }));
            tasks();
            entries.add(new BuiltinSpec("db_disk_size", List.of(), BuiltinPermissionRule.ANY, false, call -> call.hosts().dbDiskSize().invoke(call)));
            entries.add(new BuiltinSpec("dump_database", List.of(), BuiltinPermissionRule.WIZARD_ONLY, false, BuiltinCatalog::dumpDatabase));
          }
          private BuiltinResult functionInfo(List<MooValue> arguments) {
            manifest.stream().map(BuiltinCatalog::functionInfoDescription);
            StringValue requestedName = (StringValue) arguments.getFirst();
            BuiltinSpec requested = specs.get(requestedName.text().toLowerCase(Locale.ROOT));
            return null;
          }
          private static int functionInfoDescription(BuiltinSpec spec) { return switch (spec.type()) { case INTEGER -> 0; case NUMBER -> -2; default -> -1; }; }
          private static String serverVersion() { return SERVER_VERSION; }
          private BuiltinResult serverLog(List<MooValue> arguments) { hosts.serverLog().info("message"); return null; }
          private BuiltinResult logCacheStats(WorldTxn world) { hosts.serverLog().info("cache"); return null; }
          private static BuiltinResult dumpDatabase(BuiltinCall call) { return new BuiltinResult.Checkpoint(); }
          public void reloadProtectedFunctions(WorldTxn world) { protectedFunctions = null; }
        }
        """).toList();
    var recipe = new MoveBuiltinFamily("ServerBuiltins",
        Set.of("value_bytes", "server_version", "function_info", "server_log", "log_cache_stats", "load_server_options", "db_disk_size", "dump_database"),
        Set.of("functionInfo", "functionInfoDescription", "serverVersion", "serverLog", "logCacheStats", "dumpDatabase"));
    var changes = recipe.run(new InMemoryLargeSourceSet(files), context).getChangeset().getAllResults();
    String catalog = changedFile(changes, "BuiltinCatalog.java");
    String family = changedFile(changes, "ServerBuiltins.java");
    assertTrue(catalog.indexOf("registerValueSizes(entries)") < catalog.indexOf("middle()"));
    assertTrue(catalog.indexOf("middle()") < catalog.indexOf("registerVersionAndFunctions(entries, this)"));
    assertTrue(catalog.indexOf("files()") < catalog.indexOf("registerDiagnostics(entries, this, hosts.serverLog())"));
    assertTrue(catalog.indexOf("tasks()") < catalog.indexOf("registerShutdownAndDatabase(entries)"));
    assertTrue(catalog.contains("private List<BuiltinSpec> manifest"));
    assertTrue(catalog.contains("private Map<String, BuiltinSpec> specs"));
    assertTrue(catalog.contains("void reloadProtectedFunctions"));
    assertFalse(catalog.contains("String SERVER_VERSION"));
    assertTrue(family.contains("private static final String SERVER_VERSION"));
    assertTrue(family.contains("catalog.manifest().stream()"));
    assertTrue(family.contains("catalog.spec(requestedName.text()).orElse(null)"));
    assertTrue(family.contains("ServerBuiltins::functionInfoDescription"));
    assertFalse(family.contains("Set<ArgType> INTEGER"));
    assertFalse(family.contains("Set<ArgType> NUMBER"));
    assertTrue(family.contains("catalog.reloadProtectedFunctions(call.world())"));
    assertTrue(family.contains("serverLog(call.arguments(), serverLog)"));
    assertTrue(family.contains("logCacheStats(call.world(), serverLog)"));
    assertTrue(family.contains("private static BuiltinResult serverLog(List<MooValue> arguments, ServerLog log)"));
    assertTrue(family.contains("log.info(\"message\")"));
    assertFalse(family.contains("hosts.serverLog()"));
    assertTrue(family.contains("call.hosts().dbDiskSize().invoke(call)"));
    assertTrue(family.contains("import world.mongoose.banteng.builtin.BuiltinCall;"));
    assertTrue(family.contains("ServerBuiltins::dumpDatabase"));
    assertTrue(family.contains(".irrevocable()"));
    assertTrue(family.contains("import static world.mongoose.banteng.builtin.ValueLimits.valueBytes;"));
    assertTrue(recipe.run(new InMemoryLargeSourceSet(changes.stream().map(Result::getAfter).toList()), context)
        .getChangeset().getAllResults().isEmpty());
  }

  @Test
  void movesConversionGroupsAndRetargetsTheRealRemainingHelperCaller() {
    var context = new InMemoryExecutionContext(failure -> { throw new AssertionError(failure); });
    var files = JavaParser.fromJavaVersion().build().parse(context, """
        package world.mongoose.banteng.builtin;
        import java.util.List;
        class BuiltinCatalog {
          void buildManifest() {
            entries.add(new BuiltinSpec("tostr", List.of(), BuiltinPermissionRule.ANY, false, call -> toStringValue()));
            dynamicEval();
            entries.add(new BuiltinSpec("typeof", List.of(), BuiltinPermissionRule.ANY, false, call -> typeOf()));
          }
          private static int toStringValue() { return 1; }
          private static int typeOf() { return 2; }
          private static int raise() { return toStringValue(); }
        }
        """).toList();
    var recipe = new MoveBuiltinFamily("ConversionBuiltins", Set.of("tostr", "typeof"),
        Set.of("toStringValue", "typeOf"));
    var changes = recipe.run(new InMemoryLargeSourceSet(files), context).getChangeset().getAllResults();
    String catalog = changedFile(changes, "BuiltinCatalog.java");
    String family = changedFile(changes, "ConversionBuiltins.java");
    assertTrue(catalog.indexOf("registerConversions(entries)") < catalog.indexOf("dynamicEval()"));
    assertTrue(catalog.indexOf("dynamicEval()") < catalog.indexOf("registerTypeOf(entries)"));
    assertTrue(catalog.contains("return ConversionBuiltins.toStringValue()"));
    assertFalse(catalog.contains("static int toStringValue"));
    assertTrue(family.contains("public static int toStringValue()"));
    assertTrue(family.contains("private static int typeOf()"));
    List<SourceFile> after = changes.stream().map(Result::getAfter).toList();
    assertTrue(recipe.run(new InMemoryLargeSourceSet(after), context).getChangeset().getAllResults().isEmpty());
  }

  @Test
  void movesStringGroupsWithSharedRandomAndExistingValueSemanticsOwner() {
    var context = new InMemoryExecutionContext(failure -> { throw new AssertionError(failure); });
    var files = JavaParser.fromJavaVersion().build().parse(context, """
        package world.mongoose.banteng.builtin;
        import java.util.List;
        import java.util.Random;
        class BuiltinCatalog {
          private final Random random = new Random();
          void buildManifest() {
            entries.add(new BuiltinSpec("explode", List.of(STRING), BuiltinPermissionRule.ANY, false, call -> explode()));
            reverse();
            entries.add(new BuiltinSpec("parse_ansi", List.of(STRING), BuiltinPermissionRule.ANY, true, call -> parseAnsi(call.arguments())));
            simplexNoise();
            entries.add(new BuiltinSpec("index", List.of(STRING), BuiltinPermissionRule.ANY, false, call -> stringIndex()));
          }
          private static int explode() { return 1; }
          private BuiltinResult parseAnsi(List<MooValue> arguments) { random.nextInt(6); return null; }
          private static int stringIndex() { return matchesAt() ? 1 : 0; }
          private static boolean matchesAt() { return true; }
          private boolean locateByName() { return matchesAt(); }
          private record AnsiReplacement(String tag, String code) {}
        }
        """, "package world.mongoose.banteng.value; public record ValueSemantics(boolean promoteNumbers) {}")
        .toList();
    var recipe = new MoveBuiltinFamily("StringBuiltins", Set.of("explode", "parse_ansi", "index"),
        Set.of("explode", "parseAnsi", "stringIndex"));
    var changes = recipe.run(new InMemoryLargeSourceSet(files), context).getChangeset().getAllResults();
    String catalog = changedFile(changes, "BuiltinCatalog.java");
    String family = changedFile(changes, "StringBuiltins.java");
    assertTrue(catalog.indexOf("registerExplode(entries)") < catalog.indexOf("reverse()"));
    assertTrue(catalog.indexOf("reverse()") < catalog.indexOf("registerSubstitutionAndAnsi(entries, random)"));
    assertTrue(catalog.indexOf("registerSubstitutionAndAnsi(entries, random)") < catalog.indexOf("simplexNoise()"));
    assertTrue(catalog.indexOf("simplexNoise()") < catalog.indexOf("registerSearchAndCompare(entries)"));
    assertTrue(family.contains("private static BuiltinResult parseAnsi(List<MooValue> arguments, Random random)"));
    assertTrue(family.contains("parseAnsi(call.arguments(), random)"));
    assertTrue(family.contains(".irrevocable()"));
    assertTrue(family.contains("private record AnsiReplacement"));
    assertTrue(family.contains("Set.of(ArgType.STRING)"));
    assertFalse(catalog.contains("record AnsiReplacement"));
    assertTrue(catalog.contains("import static world.mongoose.banteng.value.ValueSemantics.matchesAt;"));
    assertTrue(changedFile(changes, "ValueSemantics.java").contains("public static boolean matchesAt"));
    List<SourceFile> after = changes.stream().map(Result::getAfter).toList();
    assertTrue(recipe.run(new InMemoryLargeSourceSet(after), context).getChangeset().getAllResults().isEmpty());
  }

  @Test
  void movesListGroupsHandlersAndSharedOwnersWithoutReorderingInterleavedEntries() {
    var context = new InMemoryExecutionContext(failure -> { throw new AssertionError(failure); });
    String catalogSource = """
        package world.mongoose.banteng.builtin;
        import java.util.List;
        class BuiltinCatalog {
          private static final long DEFAULT_MAX_LIST_VALUE_BYTES = 64_537_861L;
          private static final long MIN_LIST_VALUE_BYTES_LIMIT = 1_021L;
          private static final long MAX_LIST_VALUE_BYTES_LIMIT = 2_147_482_626L;
          void buildManifest() {
            BuiltinHandler allMembers = call -> call.threadMode() ? hostWork() : allMembers();
            BuiltinHandler sort = call -> call.threadMode() ? hostWork() : sortValues();
            entries.add(new BuiltinSpec("length", List.of(), BuiltinPermissionRule.ANY, false, call -> length()));
            math();
            entries.add(new BuiltinSpec("listappend", List.of(), BuiltinPermissionRule.ANY, false, call -> listInsert()));
            entries.add(new BuiltinSpec("listinsert", List.of(), BuiltinPermissionRule.ANY, false, call -> listInsert()));
            entries.add(new BuiltinSpec("listdelete", List.of(), BuiltinPermissionRule.ANY, false, call -> listDelete()));
            entries.add(new BuiltinSpec("listset", List.of(), BuiltinPermissionRule.ANY, false, call -> listSet()));
            mapAndJson();
            entries.add(new BuiltinSpec("setadd", List.of(), BuiltinPermissionRule.ANY, false, call -> setAdd()));
            entries.add(new BuiltinSpec("setremove", List.of(), BuiltinPermissionRule.ANY, false, call -> setRemove()));
            entries.add(new BuiltinSpec("all_members", List.of(), BuiltinPermissionRule.ANY, false, allMembers));
            entries.add(new BuiltinSpec("sort", List.of(), BuiltinPermissionRule.ANY, false, sort));
            explode();
            entries.add(new BuiltinSpec("reverse", List.of(), BuiltinPermissionRule.ANY, false, call -> reverse()));
            after();
          }
          private static int length() { return 1; }
          private static int listInsert() { return enforceListValueLimit(); }
          private static int listDelete() { return 2; }
          private static int listSet() { return 3; }
          private static int setAdd() { return 4; }
          private static int setRemove() { return 5; }
          private static int allMembers() { return 6; }
          private static int sortValues() { return foldAscii(65); }
          private static int reverse() { return 7; }
          private static long valueBytes() { return 16L; }
          public static int enforceListValueLimit() { return (int) valueBytes(); }
          private static int foldAscii(int value) { return value >= 'A' && value <= 'Z' ? value + 32 : value; }
        }
        """;
    var files = JavaParser.fromJavaVersion().build().parse(context, catalogSource,
        "package world.mongoose.banteng.value; public record ValueSemantics(boolean promoteNumbers) {}",
        "package world.mongoose.banteng.vm; import world.mongoose.banteng.builtin.BuiltinCatalog; "
            + "class ListOps { int checked() { return BuiltinCatalog.enforceListValueLimit(); } }").toList();
    var changes = new MoveBuiltinFamily("ListBuiltins",
        Set.of("length", "listappend", "listinsert", "listdelete", "listset", "setadd",
            "setremove", "all_members", "sort", "reverse"),
        Set.of("length", "listInsert", "listDelete", "listSet", "setAdd", "setRemove",
            "allMembers", "sortValues", "reverse"))
        .run(new InMemoryLargeSourceSet(files), context).getChangeset().getAllResults();
    String catalog = changedFile(changes, "BuiltinCatalog.java");
    String family = changedFile(changes, "ListBuiltins.java");
    assertTrue(catalog.indexOf("registerLength(entries)") < catalog.indexOf("math()"));
    assertTrue(catalog.indexOf("math()") < catalog.indexOf("registerListEdits(entries)"));
    assertTrue(catalog.indexOf("registerListEdits(entries)") < catalog.indexOf("mapAndJson()"));
    assertTrue(catalog.indexOf("mapAndJson()") < catalog.indexOf("registerSetsAndSort(entries)"));
    assertTrue(catalog.indexOf("registerSetsAndSort(entries)") < catalog.indexOf("explode()"));
    assertTrue(catalog.indexOf("explode()") < catalog.indexOf("registerReverse(entries)"));
    assertFalse(catalog.contains("BuiltinHandler allMembers"));
    assertTrue(family.contains("BuiltinHandler allMembers"));
    assertTrue(family.contains("call.threadMode() ? hostWork() : sortValues()"));
    assertFalse(family.contains("public static int enforceListValueLimit"));
    assertTrue(family.contains("import static world.mongoose.banteng.builtin.ValueLimits.enforceListValueLimit;"));
    assertTrue(changedFile(changes, "ValueLimits.java").contains("public static long valueBytes()"));
    assertTrue(changedFile(changes, "ValueSemantics.java").contains("public static int foldAscii"));
    assertTrue(changes.stream().filter(result -> result.getAfter().getSourcePath().getFileName()
        .toString().equals("ValueSemantics.java")).allMatch(result -> result.getBefore() != null));
    assertTrue(changedFile(changes, "ListOps.java").contains("ValueLimits.enforceListValueLimit()"),
        changedFile(changes, "ListOps.java"));
  }

  private static String changedFile(List<Result> changes, String name) {
    return changes.stream().map(Result::getAfter)
        .filter(file -> file.getSourcePath().getFileName().toString().equals(name))
        .findFirst().orElseThrow().printAll();
  }

  @Test
  void movesRandomStateReadsAndSharedStringLimitToTheirOwners() {
    var context = new InMemoryExecutionContext(failure -> { throw new AssertionError(failure); });
    var files = JavaParser.fromJavaVersion().build().parse(context, """
        package world.mongoose.banteng.builtin;
        import java.util.List;
        import java.util.Random;
        import java.security.SecureRandom;
        import java.util.Optional;
        class BuiltinCatalog {
          private final Random random = new Random();
          private final Random floatingRandom = new Random();
          private static final SecureRandom SECURE_RANDOM = new SecureRandom();
          List<Object> buildManifest() {
            List<Object> entries = new java.util.ArrayList<>();
            entries.add(new BuiltinSpec("random", List.of(), BuiltinPermissionRule.ANY, true, call -> randomInteger(call.arguments())));
            entries.add(new BuiltinSpec("reseed_random", List.of(), BuiltinPermissionRule.WIZARD_ONLY, true, call -> { random.setSeed(SECURE_RANDOM.nextLong()); return null; }));
            entries.add(new BuiltinSpec("frandom", List.of(), BuiltinPermissionRule.ANY, true, call -> floatingRandom(call.arguments())));
            entries.add(new BuiltinSpec("random_bytes", List.of(), BuiltinPermissionRule.ANY, true, call -> randomBytes(call.arguments(), call.world())));
            return entries;
          }
          private BuiltinResult randomInteger(List<MooValue> arguments) { return random.nextLong(); }
          private BuiltinResult floatingRandom(List<MooValue> arguments) { return floatingRandom.nextInt(); }
          private static BuiltinResult randomBytes(List<MooValue> arguments, WorldTxn world) {
            SECURE_RANDOM.nextBytes(new byte[1]);
            return stringLengthError(encoded.size(), world);
          }
          public static Optional<BuiltinResult> stringLengthError(long length, WorldTxn world) { return Optional.empty(); }
        }
        """, "package world.mongoose.banteng.builtin; public final class ValueLimits {}",
        "package world.mongoose.banteng.vm; import world.mongoose.banteng.builtin.BuiltinCatalog; class ArithmeticOps { Object checked() { return BuiltinCatalog.stringLengthError(1, null); } }",
        "package world.mongoose.banteng.vm; import world.mongoose.banteng.builtin.BuiltinCatalog; class IndexOps { Object checked() { return BuiltinCatalog.stringLengthError(1, null); } }").toList();
    var changes = new MoveBuiltinFamily("RandomBuiltins",
        Set.of("random", "reseed_random", "frandom", "random_bytes"),
        Set.of("randomInteger", "floatingRandom", "randomBytes"))
        .run(new InMemoryLargeSourceSet(files), context).getChangeset().getAllResults();
    String catalog = changedFile(changes, "BuiltinCatalog.java");
    String family = changedFile(changes, "RandomBuiltins.java");
    assertTrue(catalog.contains("RandomBuiltins.register(entries, () -> random, () -> floatingRandom, SECURE_RANDOM)"));
    assertTrue(catalog.contains("private final Random random"));
    assertFalse(catalog.contains("stringLengthError"));
    assertFalse(catalog.contains("private BuiltinResult"));
    assertTrue(family.contains("randomInteger(call.arguments(), random.get())"));
    assertTrue(family.contains("floatingRandom(call.arguments(), floatingRandom.get())"));
    assertTrue(family.contains("random.get().setSeed(secureRandom.nextLong())"));
    assertTrue(family.contains("secureRandom.nextBytes"));
    assertTrue(family.contains("ValueLimits.stringLengthError(encoded.size(), world)"));
    assertTrue(family.contains("private static BuiltinResult randomInteger(List<MooValue> arguments, Random random)"));
    assertTrue(changedFile(changes, "ValueLimits.java").contains("public static Optional<BuiltinResult> stringLengthError"));
    assertTrue(changedFile(changes, "ArithmeticOps.java").contains("ValueLimits.stringLengthError(1, null)"));
    assertTrue(changedFile(changes, "IndexOps.java").contains("ValueLimits.stringLengthError(1, null)"));
  }

  @Test
  void loadsConfiguredFamilyFromDeclarativeRecipeAndClonesConfiguration() {
    var recipe = Environment.builder().scanRuntimeClasspath().build()
        .activateRecipes("world.mongoose.banteng.builtin.ExtractMapBuiltins");
    var mover = (MoveBuiltinFamily) recipe.getRecipeList().getFirst();
    var context = new InMemoryExecutionContext(failure -> { throw new AssertionError(failure); });
    var files = JavaParser.fromJavaVersion().build().parse(context,
        source().replace("\"one\"", "\"mapdelete\"").replace("\"two\"", "\"mapkeys\"")
            .replace("\"mapdelete\", List.of()", "\"mapdelete\", List.of(ANY, INTEGER, ArgType.NUMBER)")
            .replace("after();", "entries.add(new BuiltinSpec(\"mapvalues\", List.of(), BuiltinPermissionRule.ANY, false, BuiltinCatalog::helper));\n"
                + "entries.add(new BuiltinSpec(\"maphaskey\", List.of(), BuiltinPermissionRule.ANY, false, BuiltinCatalog::helper));\n after();")
            .replace("private static int helper() { return 7; }",
                "private static int mapKeys() { return 7; }\n"
                + "private static int mapValues() { return 7; }\n"
                + "private static int mapHasKey() { return 7; }\n"
                + "private static int mapDelete() { return 7; }\n"
                + "private static int deleteMapKey() { return 7; }\n"
                + "private static int invalidMapBuiltinKey() { return 7; }")
            .replace("BuiltinCatalog::helper", "BuiltinCatalog::mapKeys")).toList();
    var changes = mover.clone().run(new InMemoryLargeSourceSet(files), context)
        .getChangeset().getAllResults();
    String family = changes.stream().map(Result::getAfter)
        .filter(file -> file.getSourcePath().getFileName().toString().equals("MapBuiltins.java"))
        .findFirst().orElseThrow().printAll();
    assertTrue(family.contains("private static final Set<ArgType> ANY"));
    assertTrue(family.contains("private static final Set<ArgType> INTEGER"));
    assertFalse(family.contains("private static final Set<ArgType> NUMBER"));
  }

  @Test
  void movesImplementationsAndRegistrationsPreservingDefaultsDeviationsAndOrder() {
    List<Result> changes = rewrite(List.of(source()));
    String catalog = changes.stream().map(Result::getAfter)
        .filter(file -> file.getSourcePath().getFileName().toString().equals("BuiltinCatalog.java"))
        .findFirst().orElseThrow().printAll();
    String family = changes.stream().map(Result::getAfter)
        .filter(file -> file.getSourcePath().getFileName().toString().equals("SampleBuiltins.java"))
        .findFirst().orElseThrow().printAll();
    assertFalse(catalog.contains("private static int helper"));
    assertTrue(catalog.indexOf("before()") < catalog.indexOf("SampleBuiltins.register(entries)"));
    assertTrue(catalog.indexOf("SampleBuiltins.register(entries)") < catalog.indexOf("after()"));
    assertTrue(family.contains(".implementation(SampleBuiltins::helper)"));
    assertTrue(family.contains(".permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable()"));
    assertFalse(family.contains(".permission(BuiltinPermissionRule.ANY)"));
    assertTrue(family.contains("private static int helper()"));
    assertTrue(family.contains("return 7;"));
    assertTrue(rewrite(changes.stream().map(Result::getAfter).toList()).isEmpty());
  }

  private static List<Result> rewrite(List<?> inputs) {
    InMemoryExecutionContext context = new InMemoryExecutionContext(failure -> {
      throw new AssertionError(failure);
    });
    List<SourceFile> files;
    if (inputs.getFirst() instanceof String) {
      files = JavaParser.fromJavaVersion().build()
          .parse(context, inputs.toArray(String[]::new)).toList();
    } else {
      files = inputs.stream().map(SourceFile.class::cast).toList();
    }
    return new MoveBuiltinFamily("SampleBuiltins", Set.of("one", "two"), Set.of("helper"))
        .run(new InMemoryLargeSourceSet(files), context).getChangeset().getAllResults();
  }

  private static String source() {
    return """
        package world.mongoose.banteng.builtin;
        import java.util.ArrayList;
        import java.util.List;
        class BuiltinCatalog {
          List<Object> buildManifest() {
            List<Object> entries = new ArrayList<>();
            before();
            entries.add(new BuiltinSpec("one", List.of(), BuiltinPermissionRule.ANY, false, BuiltinCatalog::helper));
            entries.add(new BuiltinSpec("two", List.of(), BuiltinPermissionRule.WIZARD_ONLY, true, BuiltinCatalog::helper));
            after();
            return entries;
          }
          private static int helper() { return 7; }
          private void before() {}
          private void after() {}
        }
        """;
  }
}
