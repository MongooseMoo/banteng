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
