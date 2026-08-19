package moo.rewrite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

final class DecomposeWorldTxnTest {
  @Test
  void deletesDeadMethodsAndMovesStaticLayoutAlgorithms() {
    String worldBefore =
        """
        package moo.world;

        import java.util.List;
        import java.util.Map;
        import java.util.Set;

        public class WorldTxn {
          public long baseRevision() { return 0; }
          public void changeParent(long object, long parent) {}
          public void restoreIntrinsicCommands(long object) {}
          List<Long> ancestry(long id, Set<Long> visited, List<Long> result) {
            collectAncestry(id, visited, result);
            return result;
          }
          private void collectAncestry(long id, Set<Long> visited, List<Long> result) {}
          static List<Long> ancestryFromParents(List<Long> parents) {
            collectAncestry(0, Map.of(), Set.of(), parents);
            return parents;
          }
          private static void collectAncestry(
              long id, Map<Long, String> objects, Set<Long> visited, List<Long> result) {}
          static List<Long> descendantsOf(List<Long> roots) { return roots; }
          List<Long> use(List<Long> roots) { return descendantsOf(roots); }
        }
        """;
    String engineBefore =
        """
        package moo.world;

        final class PropertyLayoutEngine {}
        """;
    InMemoryExecutionContext context =
        new InMemoryExecutionContext(
            failure -> {
              throw new AssertionError(failure);
            });
    List<SourceFile> sources =
        JavaParser.fromJavaVersion().build().parse(context, worldBefore, engineBefore).toList();
    List<Result> results =
        new DecomposeWorldTxn()
            .run(new InMemoryLargeSourceSet(sources), context)
            .getChangeset()
            .getAllResults();
    Map<Path, String> rewritten =
        results.stream()
            .filter(result -> result.getAfter() != null)
            .collect(
                Collectors.toMap(
                    result -> result.getAfter().getSourcePath(),
                    result -> result.getAfter().printAll()));
    String world =
        rewritten.entrySet().stream()
            .filter(entry -> entry.getKey().endsWith("WorldTxn.java"))
            .findFirst()
            .orElseThrow()
            .getValue();
    String engine =
        rewritten.entrySet().stream()
            .filter(entry -> entry.getKey().endsWith("PropertyLayoutEngine.java"))
            .findFirst()
            .orElseThrow()
            .getValue();

    assertFalse(world.contains("baseRevision("), world);
    assertFalse(world.contains("changeParent("), world);
    assertFalse(world.contains("restoreIntrinsicCommands("), world);
    assertFalse(world.contains("static List<Long> ancestryFromParents"), world);
    assertTrue(world.contains("private void collectAncestry("), world);
    assertTrue(world.contains("collectAncestry(id, visited, result)"), world);
    assertFalse(world.contains("private static void collectAncestry("), world);
    assertTrue(world.contains("PropertyLayoutEngine.descendantsOf(roots)"), world);
    assertTrue(engine.contains("import java.util.List;"), engine);
    assertFalse(engine.contains("private static List<Long> ancestryFromParents"), engine);
    assertTrue(engine.contains("static List<Long> ancestryFromParents"), engine);
    assertTrue(engine.contains("private static void collectAncestry("), engine);
    assertTrue(engine.contains("static List<Long> descendantsOf"), engine);

    List<SourceFile> rewrittenSources =
        results.stream().map(result -> Objects.requireNonNull(result.getAfter())).toList();
    List<Result> fixedPointResults =
        new DecomposeWorldTxn()
            .run(new InMemoryLargeSourceSet(rewrittenSources), context)
            .getChangeset()
            .getAllResults();
    assertTrue(fixedPointResults.isEmpty(), fixedPointResults.toString());
  }
}
