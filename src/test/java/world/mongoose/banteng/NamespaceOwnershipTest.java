package world.mongoose.banteng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class NamespaceOwnershipTest {
  private static final Path REPOSITORY = Path.of("").toAbsolutePath();
  private static final Path OWNED_PATH = Path.of("world", "mongoose", "banteng");
  private static final String OWNED_PACKAGE = "world.mongoose.banteng";
  private static final Pattern MOO_WORD =
      Pattern.compile("(^|[^A-Za-z0-9_])moo(?![A-Za-z0-9_])");
  private static final Pattern OLD_NAMESPACE_SHAPE =
      Pattern.compile("(^|[^A-Za-z0-9_])moo(?:\\\\?\\.|/)");
  private static final List<Path> JAVA_ROOTS =
      List.of(
          Path.of("src", "main", "java"),
          Path.of("src", "test", "java"),
          Path.of("src", "jmh", "java"),
          Path.of("src", "jcstress", "java"),
          Path.of("errorprone-checks", "src", "main", "java"),
          Path.of("errorprone-checks", "src", "test", "java"),
          Path.of("rewrite-recipes", "src", "main", "java"),
          Path.of("rewrite-recipes", "src", "test", "java"));
  private static final Map<Path, Integer> JAVA_SOURCE_COUNTS =
      Map.of(
          Path.of("src", "main", "java"), 91,
          Path.of("src", "test", "java"), 59,
          Path.of("src", "jmh", "java"), 1,
          Path.of("src", "jcstress", "java"), 2,
          Path.of("errorprone-checks", "src", "main", "java"), 1,
          Path.of("errorprone-checks", "src", "test", "java"), 1,
          Path.of("rewrite-recipes", "src", "main", "java"), 7,
          Path.of("rewrite-recipes", "src", "test", "java"), 9);
  private static final Set<String> PACKAGE_RESOURCES =
      Set.of(
          "persistence/queued-task-source-and-dispatch.db",
          "persistence/v17-hierarchy/phase1-children-type.db",
          "persistence/v17-hierarchy/phase1-contents-element-type.db",
          "persistence/v17-hierarchy/phase1-contents-type.db",
          "persistence/v17-hierarchy/phase1-location-type.db",
          "persistence/v17-hierarchy/phase1-missing-references.db",
          "persistence/v17-hierarchy/phase1-nothing-sentinels.db",
          "persistence/v17-hierarchy/phase1-parents-type.db",
          "persistence/v17-hierarchy/phase2-location-cycle.db",
          "persistence/v17-hierarchy/phase2-parent-cycle.db",
          "persistence/v17-hierarchy/phase3-child-without-parent.db",
          "persistence/v17-hierarchy/phase3-content-without-location.db",
          "persistence/v17-hierarchy/phase3-duplicate-reciprocal-links.db",
          "persistence/v17-hierarchy/phase3-location-without-content.db",
          "persistence/v17-hierarchy/phase3-parent-without-child.db",
          "syntax/MooParserFuzzTestInputs/parsesArbitraryLatin1/return-one");
  private static final Set<String> HISTORICAL_EVIDENCE_REPORTS =
      Set.of(
          "docs/reports/arithmetic-float-eval-authority.md",
          "docs/reports/background-tick-budget-authority.md",
          "docs/reports/connection-lifecycle-authority.md",
          "docs/reports/foreground-tick-budget-authority.md",
          "docs/reports/java-ots-library-review.md",
          "docs/reports/java25-static-analysis-decision.md",
          "docs/reports/jazzer-java25-junit6-proof.md",
          "docs/reports/jcstress-java25-proof.md",
          "docs/reports/jetcheck-acceptance-spike.md",
          "docs/reports/jmh-java25-proof.md",
          "docs/reports/jvm-moo-architecture-research.md",
          "docs/reports/object-movement-authority.md",
          "docs/reports/object-parent-authority.md",
          "docs/reports/phase1-java-skeleton-proof.md",
          "docs/reports/splice-computed-access-authority.md",
          "docs/reports/telnet-transport-authority.md");

  @Test
  void everyJavaSourceUsesTheOwnedNamespaceAndPath() throws IOException {
    for (Path relativeRoot : JAVA_ROOTS) {
      Path root = REPOSITORY.resolve(relativeRoot);
      List<Path> sources;
      try (Stream<Path> paths = Files.walk(root)) {
        sources = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
      }
      assertEquals(JAVA_SOURCE_COUNTS.get(relativeRoot), sources.size(), relativeRoot.toString());
      for (Path source : sources) {
        Path relativeSource = root.relativize(source);
        assertTrue(relativeSource.startsWith(OWNED_PATH), relativeSource.toString());
        String declaration =
            Files.readAllLines(source).stream()
                .filter(line -> line.startsWith("package "))
                .findFirst()
                .orElse("");
        assertTrue(
            declaration.equals("package " + OWNED_PACKAGE + ";")
                || declaration.startsWith("package " + OWNED_PACKAGE + "."),
            source.toString());
      }
    }
    assertFalse(Files.exists(REPOSITORY.resolve("src/main/java/moo")));
    assertFalse(Files.exists(REPOSITORY.resolve("src/test/java/moo")));
    assertFalse(Files.exists(REPOSITORY.resolve("src/jmh/java/moo")));
    assertFalse(Files.exists(REPOSITORY.resolve("src/jcstress/java/moo")));
    assertFalse(Files.exists(REPOSITORY.resolve("errorprone-checks/src/main/java/moo")));
    assertFalse(Files.exists(REPOSITORY.resolve("errorprone-checks/src/test/java/moo")));
    assertFalse(Files.exists(REPOSITORY.resolve("rewrite-recipes/src/main/java/moo")));
    assertFalse(Files.exists(REPOSITORY.resolve("rewrite-recipes/src/test/java/moo")));
    assertFalse(Files.exists(REPOSITORY.resolve("src/main/java/module-info.java")));
    String ignored = Files.readString(REPOSITORY.resolve(".gitignore"));
    assertFalse(ignored.contains("/moo/"));
    assertTrue(ignored.contains("/world/"));
  }

  @Test
  void buildLaunchersAndResourcesUseTheOwnedIdentity() throws IOException {
    String build = Files.readString(REPOSITORY.resolve("build.gradle.kts"));
    String settings = Files.readString(REPOSITORY.resolve("settings.gradle.kts"));
    String launcher = Files.readString(REPOSITORY.resolve("scripts/run_banteng_wsl.sh"));
    String managedRunner =
        Files.readString(REPOSITORY.resolve("scripts/test_managed_runners_wsl.sh"));
    String errorProneProvider =
        Files.readString(
                REPOSITORY.resolve(
                    "errorprone-checks/src/main/resources/META-INF/services/"
                        + "com.google.errorprone.bugpatterns.BugChecker"))
            .strip();

    assertTrue(build.contains("group = \"world.mongoose\""));
    assertTrue(build.contains("mainClass = \"world.mongoose.banteng.app.Banteng\""));
    assertTrue(build.contains("world.mongoose.banteng.syntax.MooParserFuzzTest"));
    assertTrue(build.contains("world.mongoose.banteng.runtime.ConcurrentSchedulerStressTest"));
    assertTrue(build.contains("^world\\\\.mongoose\\\\.banteng\\\\.jcstress\\\\."));
    assertTrue(build.contains("^world\\\\.mongoose\\\\.banteng\\\\.benchmark\\\\."));
    assertEquals(1, occurrences(settings, "rootProject.name = \"banteng\""));
    assertTrue(launcher.contains("world.mongoose.banteng.app.Banteng"));
    assertTrue(managedRunner.contains("$tmp/java/world/mongoose/banteng/app/Banteng.java"));
    assertTrue(managedRunner.contains("package world.mongoose.banteng.app;"));
    assertEquals(
        "world.mongoose.banteng.errorprone.NarrowIllegalArgumentCatch", errorProneProvider);

    Path resources = REPOSITORY.resolve("src/test/resources");
    assertFalse(Files.exists(resources.resolve("moo")));
    for (String resource : PACKAGE_RESOURCES) {
      assertTrue(Files.isRegularFile(resources.resolve(OWNED_PATH).resolve(resource)), resource);
    }
  }

  @Test
  void namedRewriteRecipeIsExecutableAndVerifiedAtTheFixedPoint() throws IOException {
    String yaml = Files.readString(REPOSITORY.resolve("rewrite.yml"));
    String workflow = Files.readString(REPOSITORY.resolve(".github/workflows/ci.yml"));
    String rewriteScript = Files.readString(REPOSITORY.resolve("scripts/rewrite.sh"));
    String rootBuild = Files.readString(REPOSITORY.resolve("build.gradle.kts"));
    String errorProneBuild =
        Files.readString(REPOSITORY.resolve("errorprone-checks/build.gradle.kts"));
    String recipesBuild =
        Files.readString(REPOSITORY.resolve("rewrite-recipes/build.gradle.kts"));

    assertTrue(yaml.contains("name: world.mongoose.banteng.ChangePackage"));
    assertTrue(yaml.contains("- org.openrewrite.java.ChangePackage:"));
    assertTrue(yaml.contains("oldPackageName: moo"));
    assertTrue(yaml.contains("newPackageName: world.mongoose.banteng"));
    assertTrue(yaml.contains("recursive: true"));
    assertTrue(rootBuild.contains("id(\"org.openrewrite.rewrite\") version \"7.39.0\""));
    assertFalse(errorProneBuild.contains("id(\"org.openrewrite.rewrite\")"));
    assertFalse(recipesBuild.contains("id(\"org.openrewrite.rewrite\")"));
    assertTrue(rewriteScript.contains("./gradlew \"$task\""));
    assertTrue(workflow.contains("scripts/rewrite.sh world.mongoose.banteng.ChangePackage"));
    assertTrue(
        workflow.contains(
            "errorprone-checks/src/test/java/moo/namespace_rewrite_proof/"
                + "ErrorProneRewriteProof.java"));
    assertTrue(
        workflow.contains(
            "rewrite-recipes/src/test/java/moo/namespace_rewrite_proof/"
                + "RecipeRewriteProof.java"));
    assertTrue(
        workflow.contains(
            "grep --fixed-strings 'errorprone-checks/src/test/java/"
                + "world/mongoose/banteng/namespace_rewrite_proof/"
                + "ErrorProneRewriteProof.java' build/reports/rewrite/rewrite.patch"));
    assertTrue(
        workflow.contains(
            "grep --fixed-strings 'rewrite-recipes/src/test/java/"
                + "world/mongoose/banteng/namespace_rewrite_proof/"
                + "RecipeRewriteProof.java' build/reports/rewrite/rewrite.patch"));
    assertTrue(workflow.contains("test ! -s build/reports/rewrite/rewrite.patch"));
  }

  @Test
  void jfrTelemetryUsesTheOwnedContract() throws IOException {
    String configuration =
        Files.readString(REPOSITORY.resolve("src/main/resources/jfr/banteng-production.jfc"));
    for (String event :
        List.of(
            "TaskSegment",
            "WorldCommit",
            "WorldConflict",
            "TaskRetry",
            "TaskFallback",
            "Checkpoint",
            "VersionRetention")) {
      assertEquals(
          1,
          occurrences(configuration, "event name=\"world.mongoose.banteng." + event + "\""),
          event);
    }
    assertFalse(configuration.contains("event name=\"moo."));
  }

  @Test
  void everyRetainedMooReferenceHasAnExplicitReason() throws IOException {
    List<String> unclassified = new ArrayList<>();
    for (String rootName :
        List.of(
            ".gitignore",
            ".github",
            "build.gradle.kts",
            "errorprone-checks",
            "rewrite.yml",
            "docs",
            "profiles",
            "scripts",
            "src",
            "rewrite-recipes")) {
      Path root = REPOSITORY.resolve(rootName);
      try (Stream<Path> paths = Files.isDirectory(root) ? Files.walk(root) : Stream.of(root)) {
        for (Path file : paths.filter(Files::isRegularFile).toList()) {
          String normalized = REPOSITORY.relativize(file).toString().replace('\\', '/');
          if (!isAuditedTextFile(normalized) || isGenerated(file) || isAuditImplementation(normalized)) {
            continue;
          }
          List<String> lines = Files.readAllLines(file);
          for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (MOO_WORD.matcher(line).find() && !isClassified(normalized, line)) {
              unclassified.add(normalized + ":" + (index + 1) + ":" + line.strip());
            }
          }
        }
      }
    }
    assertEquals(List.of(), unclassified);
    assertTrue(
        Files.isRegularFile(REPOSITORY.resolve("docs/reports/banteng-namespace-residuals.md")));
  }

  private static boolean isClassified(String path, String line) {
    if (isHistoricalEvidence(path, line)) {
      return true;
    }
    if (path.equals(
        "rewrite-recipes/src/test/java/world/mongoose/banteng/rewrite/ChangePackageTest.java")) {
      return true;
    }
    if (path.equals(".github/workflows/ci.yml")
        && (line.contains("namespace_rewrite_proof")
            || line.contains("package moo.namespace_rewrite_proof"))) {
      return true;
    }
    if (OLD_NAMESPACE_SHAPE.matcher(line).find()) {
      return isAllowedNamespaceShape(path, line);
    }
    if (path.equals("rewrite.yml")) {
      return line.contains("from moo to world.mongoose.banteng")
          || line.strip().equals("oldPackageName: moo");
    }
    if (path.startsWith("docs/") || path.startsWith("profiles/")) {
      return isExternalMooTerm(line);
    }
    if (path.equals(".github/workflows/ci.yml")
        || path.equals("scripts/run_managed_wsl.sh")
        || path.equals("scripts/test_managed_runners_wsl.sh")) {
      return isExternalMooTerm(line);
    }
    if (path.equals("scripts/test_verify_toast_profile_wsl.sh")) {
      return true;
    }
    if (path.startsWith("src/main/java/world/mongoose/banteng/runtime/")) {
      return isRuntimeThreadName(line);
    }
    if (path.startsWith("src/test/java/world/mongoose/banteng/")) {
      return isExternalMooTerm(line) || isRuntimeThreadName(line);
    }
    return false;
  }

  private static boolean isAllowedNamespaceShape(String path, String line) {
    if (path.equals("rewrite.yml")) {
      return line.strip().equals("oldPackageName: moo");
    }
    if (path.equals("profiles/toast/stock-wsl-testdb.json")
        || path.equals("profiles/toast/stock-wsl-toastcore.json")
        || path.equals("scripts/test_verify_toast_profile_wsl.sh")) {
      return line.contains("CMakeFiles/moo.dir");
    }
    if (path.equals("scripts/test_managed_runners_wsl.sh")) {
      return line.contains("src/moo_conformance")
          || line.contains("--moo-")
          || line.contains("BANTENG_MOO_");
    }
    if (path.equals(".github/workflows/ci.yml")) {
      return line.contains("moo-conformance-tests")
          || line.contains("src/moo_conformance")
          || line.contains("--moo-");
    }
    return false;
  }

  private static boolean isHistoricalEvidence(String path, String line) {
    if (!HISTORICAL_EVIDENCE_REPORTS.contains(path)) {
      return false;
    }
    return OLD_NAMESPACE_SHAPE.matcher(line).find()
        || isExternalMooTerm(line)
        || line.contains("CMakeFiles/moo.dir")
        || line.contains("`moo`")
        || line.contains("mvcc-concurrent-moo:");
  }

  private static boolean isExternalMooTerm(String line) {
    return line.contains("moo-conformance")
        || line.contains("moo_conformance")
        || line.contains("--moo-")
        || line.contains("/moo")
        || line.contains("```moo")
        || line.contains("moo_interp")
        || line.contains("moo --version");
  }

  private static boolean isRuntimeThreadName(String line) {
    return line.contains("moo-connect-timeout-")
        || line.contains("moo-vm-")
        || line.contains("moo-host-wake-")
        || line.contains("moo-timer-wake-");
  }

  private static boolean isAuditedTextFile(String path) {
    return path.equals(".gitignore")
        || path.endsWith(".java")
        || path.endsWith(".jfc")
        || path.endsWith(".json")
        || path.endsWith(".kts")
        || path.endsWith(".md")
        || path.endsWith(".sh")
        || path.endsWith(".xml")
        || path.endsWith(".yaml")
        || path.endsWith(".yml");
  }

  private static boolean isGenerated(Path file) {
    for (Path part : REPOSITORY.relativize(file)) {
      if (part.toString().equals("build") || part.toString().equals(".gradle")) {
        return true;
      }
    }
    return false;
  }

  private static boolean isAuditImplementation(String path) {
    return path.endsWith("/NamespaceOwnershipTest.java")
        || path.equals("docs/reports/banteng-namespace-residuals.md");
  }

  private static int occurrences(String source, String text) {
    int count = 0;
    int offset = 0;
    while ((offset = source.indexOf(text, offset)) >= 0) {
      count++;
      offset += text.length();
    }
    return count;
  }
}
