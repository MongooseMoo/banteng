package world.mongoose.banteng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NamespaceOwnershipTest {
  @Test
  void buildLaunchersAndResourcesUseTheOwnedIdentity() throws IOException {
    String build = Files.readString(Path.of("build.gradle.kts"));
    String settings = Files.readString(Path.of("settings.gradle.kts"));
    String launcher = Files.readString(Path.of("scripts/run_banteng_wsl.sh"));
    String managedRunner = Files.readString(Path.of("scripts/test_managed_runners_wsl.sh"));
    String errorProneProvider =
        Files.readString(
                Path.of(
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

    Path resources = Path.of("src/test/resources");
    assertFalse(Files.exists(resources.resolve("moo")));
    for (String resource :
        List.of(
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
            "syntax/MooParserFuzzTestInputs/parsesArbitraryLatin1/return-one")) {
      assertTrue(
          Files.isRegularFile(resources.resolve("world/mongoose/banteng").resolve(resource)),
          resource);
    }
  }

  @Test
  void jfrTelemetryUsesTheOwnedContract() throws IOException {
    String configuration = Files.readString(Path.of("src/main/resources/jfr/banteng-production.jfc"));
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
