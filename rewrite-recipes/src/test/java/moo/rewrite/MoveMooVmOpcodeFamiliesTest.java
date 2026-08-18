package moo.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

final class MoveMooVmOpcodeFamiliesTest {
  @Test
  void movesRealMethodBodiesIntoAllSixOpcodeFamilyOwners() {
    String before =
        """
        package moo.vm;

        final class MooVm {
          private static int buildList(int value) { return value + 11; }
          private static int loadLocal(int value) { return value + 22; }
          private static int index(int value) { return value + 33; }
          private static int arithmetic(int value) { return value + 44; }
          private static int iterate(int value) { return value + 55; }
          private static int raiseError(int value) { return value + 66; }
        }
        """;
    InMemoryExecutionContext context =
        new InMemoryExecutionContext(
            failure -> {
              throw new AssertionError(failure);
            });
    List<SourceFile> sources = JavaParser.fromJavaVersion().build().parse(context, before).toList();
    List<Result> results =
        new MoveMooVmOpcodeFamilies()
            .run(new InMemoryLargeSourceSet(sources), context)
            .getChangeset()
            .getAllResults();
    Map<String, String> rewritten =
        results.stream()
            .filter(result -> result.getAfter() != null)
            .map(Result::getAfter)
            .collect(
                Collectors.toMap(
                    source -> source.getSourcePath().getFileName().toString(),
                    SourceFile::printAll));

    assertEquals(
        List.of(
            "ArithmeticOps.java",
            "ErrorOps.java",
            "IndexOps.java",
            "ListOps.java",
            "LoopOps.java",
            "MooVm.java",
            "PropertyOps.java"),
        rewritten.keySet().stream().sorted().toList());
    assertFalse(rewritten.get("MooVm.java").contains("static int"));
    assertMoved(rewritten, "ListOps.java", "buildList", "value + 11");
    assertMoved(rewritten, "PropertyOps.java", "loadLocal", "value + 22");
    assertMoved(rewritten, "IndexOps.java", "index", "value + 33");
    assertMoved(rewritten, "ArithmeticOps.java", "arithmetic", "value + 44");
    assertMoved(rewritten, "LoopOps.java", "iterate", "value + 55");
    assertMoved(rewritten, "ErrorOps.java", "raiseError", "value + 66");
  }

  @Test
  void leavesNonMooVmSourcesAndExistingFamilyFilesUntouched() {
    String unrelated = "package sample; final class MooVm { private static void index() {} }";
    String candidate =
        "package moo.vm; final class MooVm { private static void buildList() {} }";
    String collision = "package moo.vm; final class ListOps {}";
    InMemoryExecutionContext context =
        new InMemoryExecutionContext(
            failure -> {
              throw new AssertionError(failure);
            });
    List<SourceFile> sources =
        JavaParser.fromJavaVersion()
            .build()
            .parse(context, unrelated, candidate, collision)
            .toList();

    List<Result> results =
        new MoveMooVmOpcodeFamilies()
            .run(new InMemoryLargeSourceSet(sources), context)
            .getChangeset()
            .getAllResults();

    assertTrue(results.isEmpty());
  }

  @Test
  void declarativeRecipeActivatesTheExecutableMove() throws Exception {
    String yaml = Files.readString(Path.of("..", "rewrite.yml"));
    assertTrue(yaml.contains("name: moo.vm.DecomposeMooVmOpcodeFamilies"));
    assertTrue(yaml.contains("- moo.rewrite.MoveMooVmOpcodeFamilies"));
  }

  private static void assertMoved(
      Map<String, String> rewritten, String file, String method, String uniqueBody) {
    String source = rewritten.get(file);
    assertTrue(source.contains("static int " + method + "(int value)"), file);
    assertTrue(source.contains(uniqueBody), file);
  }
}
