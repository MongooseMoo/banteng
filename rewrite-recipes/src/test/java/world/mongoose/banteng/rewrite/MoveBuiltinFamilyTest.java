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
