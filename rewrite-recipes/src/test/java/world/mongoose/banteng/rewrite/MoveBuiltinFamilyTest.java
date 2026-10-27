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
  void movesRegexGroupsAndServicesWithOneCapturedInstancePerGroup() {
    var context = new InMemoryExecutionContext(failure -> { throw new AssertionError(failure); });
    var files = JavaParser.fromJavaVersion().build().parse(context, """
        package world.mongoose.banteng.builtin;
        import java.util.List;
        class BuiltinCatalog {
          private final PcreService pcre = new PcreService();
          private final ToastRegexService toastRegex = new ToastRegexService();
          void buildManifest() {
            before();
            entries.add(new BuiltinSpec("match", List.of(), BuiltinPermissionRule.ANY, false, call -> toastMatch(call.arguments(), false)));
            entries.add(new BuiltinSpec("rmatch", List.of(), BuiltinPermissionRule.ANY, false, call -> toastMatch(call.arguments(), true)));
            between();
            entries.add(new BuiltinSpec("pcre_match", List.of(), BuiltinPermissionRule.ANY, true, call -> pcre.match(call.arguments())));
            entries.add(new BuiltinSpec("pcre_replace", List.of(), BuiltinPermissionRule.ANY, false, call -> pcre.replace(call.arguments())));
            entries.add(new BuiltinSpec("pcre_cache_stats", List.of(), BuiltinPermissionRule.WIZARD_ONLY, false, call -> pcre.cacheStats()));
            after();
          }
          private BuiltinResult toastMatch(List<MooValue> arguments, boolean reverse) {
            return toastRegex.match(arguments, reverse);
          }
        }
        """, """
        package world.mongoose.banteng.builtin;
        final class PcreService {
          synchronized BuiltinResult cacheStats() { return null; }
        }
        """, """
        package world.mongoose.banteng.builtin;
        final class ToastRegexService {
          BuiltinResult match() { return null; }
        }
        """).map(org.openrewrite.java.tree.J.CompilationUnit.class::cast)
        .map(file -> file.withSourcePath(java.nio.file.Path.of(
            "src/main/java/world/mongoose/banteng/builtin/" + file.getClasses().getFirst().getSimpleName() + ".java")))
        .map(SourceFile.class::cast).toList();
    var recipe = new MoveBuiltinFamily("RegexBuiltins",
        Set.of("match", "rmatch", "pcre_match", "pcre_replace", "pcre_cache_stats"), Set.of("toastMatch"));
    var changes = recipe.run(new InMemoryLargeSourceSet(files), context).getChangeset().getAllResults();
    String catalog = changedFile(changes, "BuiltinCatalog.java");
    String family = changedFile(changes, "RegexBuiltins.java");
    assertTrue(catalog.indexOf("registerLegacy(entries)") < catalog.indexOf("between()"));
    assertTrue(catalog.indexOf("between()") < catalog.indexOf("registerPcre(entries)"));
    assertFalse(catalog.contains("new PcreService"));
    assertFalse(catalog.contains("new ToastRegexService"));
    assertTrue(family.indexOf("new PcreService()") == family.lastIndexOf("new PcreService()"));
    assertTrue(family.indexOf("new ToastRegexService()") == family.lastIndexOf("new ToastRegexService()"));
    assertTrue(family.contains("toastMatch(toastRegex, call.arguments(), false)"));
    assertTrue(family.contains("private static BuiltinResult toastMatch(ToastRegexService toastRegex,"));
    assertTrue(family.contains(".irrevocable().implementation(call -> pcre.match"));
    assertTrue(family.contains(".permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> pcre.cacheStats"));
    for (String service : List.of("PcreService.java", "ToastRegexService.java")) {
      String moved = changedFile(changes, service);
      assertTrue(moved.contains("package world.mongoose.banteng.builtin.family;"));
      assertTrue(moved.contains("import world.mongoose.banteng.builtin.BuiltinResult;"));
    }
    assertTrue(changedFile(changes, "PcreService.java").contains("synchronized BuiltinResult cacheStats()"));
    assertTrue(recipe.clone().run(new InMemoryLargeSourceSet(changes.stream().map(Result::getAfter).toList()), context)
        .getChangeset().getAllResults().isEmpty());
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
