package moo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ThreadingContractTest {
  private static final Path REPOSITORY = Path.of("").toAbsolutePath();

  @Test
  void guardedByIsACompilerEnforcedMainDependency() throws IOException {
    String build = source("build.gradle.kts");

    assertTrue(build.contains("error(\"GuardedBy\")"));
    assertTrue(build.contains("com.google.errorprone:error_prone_annotations:2.50.0"));
  }

  @Test
  void monitorOwnedFieldsDeclareTheirGuard() throws IOException {
    assertGuardedFields(
        "src/main/java/moo/runtime/PublicationScheduler.java",
        List.of(
            "ready",
            "completed",
            "ingress",
            "lastInputTasks",
            "timedWork",
            "checkpointingWork",
            "finalizationBlocked",
            "activeFinalizations",
            "nextTicket",
            "nextTaskId",
            "nextPublicationTicket",
            "publicationDraining",
            "restoredTasksActivated",
            "closed"));
    assertGuardedFields(
        "src/main/java/moo/runtime/TaskRegistry.java",
        List.of(
            "tasks",
            "hostWork",
            "cancellationActions",
            "canceled",
            "nextHostHandle",
            "nextQueueSequence"));
    assertGuardedFields(
        "src/main/java/moo/world/WorldHistory.java",
        List.of("revisions", "activeTransactions", "verbCache", "current"));
    assertGuardedFields(
        "src/main/java/moo/runtime/MooRuntime.java",
        List.of("publishedConnections", "pendingReads", "sessionRevision"));
  }

  @Test
  void packageAndVmDocumentationStateOwnershipAndLockOrdering() throws IOException {
    String runtime = source("src/main/java/moo/runtime/package-info.java");
    String world = source("src/main/java/moo/world/package-info.java");
    String vmState = source("src/main/java/moo/vm/VmState.java");

    assertTrue(runtime.contains("Lock ordering"));
    assertTrue(runtime.contains("single-owner"));
    assertTrue(world.contains("single-owner"));
    assertTrue(world.contains("WorldHistory"));
    assertTrue(vmState.contains("single-owner"));
  }

  private static void assertGuardedFields(String relativePath, List<String> fields)
      throws IOException {
    String source = source(relativePath);
    assertTrue(source.contains("import com.google.errorprone.annotations.concurrent.GuardedBy;"));
    for (String field : fields) {
      assertTrue(
          source.matches(
              "(?s).*@GuardedBy\\(\"this\"\\)\\s+private[^;=\\n]*\\b"
                  + field
                  + "\\b.*"),
          () -> relativePath + " does not guard " + field);
    }
  }

  private static String source(String relativePath) throws IOException {
    return Files.readString(REPOSITORY.resolve(relativePath));
  }
}
