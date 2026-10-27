package world.mongoose.banteng.rewrite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

final class MoveFinalizationTracingTest {
  @Test
  void movesRecursiveClosureAndRetargetsItsRuntimeCaller() {
    var context = new InMemoryExecutionContext(failure -> { throw new AssertionError(failure); });
    List<SourceFile> sources = JavaParser.fromJavaVersion().build().parse(context, """
        package world.mongoose.banteng.runtime;
        import java.util.Set;
        final class MooRuntime {
          void collect() { markReachableAnonymous(1); }
          private static void collectWaifReferences(int n) { if (n > 0) collectWaifReferences(n - 1); }
          private static void collectAnonymousReferences(int n) { collectWaifReferences(n); }
          private static void markReachableAnonymous(int n) { collectAnonymousReferences(n); }
          private static void markFinalizingAnonymous(int n) { markReachableAnonymous(n); }
        }
        """).toList();
    List<Result> results = new MoveFinalizationTracing()
        .run(new InMemoryLargeSourceSet(sources), context).getChangeset().getAllResults();
    String runtime = results.stream().map(Result::getAfter)
        .filter(file -> file.getSourcePath().endsWith("MooRuntime.java"))
        .findFirst().orElseThrow().printAll();
    String collector = results.stream().map(Result::getAfter)
        .filter(file -> file.getSourcePath().endsWith("FinalizationCollector.java"))
        .findFirst().orElseThrow().printAll();
    assertTrue(runtime.contains("FinalizationCollector.markReachableAnonymous(1)"));
    assertFalse(runtime.contains("static void collectWaifReferences"));
    assertTrue(collector.contains("collectWaifReferences(n - 1)"));
    assertTrue(collector.contains("import java.util.Set;"));
    assertTrue(collector.contains("static void markFinalizingAnonymous"));
    List<SourceFile> after = results.stream().map(Result::getAfter).toList();
    assertTrue(new MoveFinalizationTracing().run(new InMemoryLargeSourceSet(after), context)
        .getChangeset().getAllResults().isEmpty());
  }
}
