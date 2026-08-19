package world.mongoose.banteng.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OperationsCommandTest {
  private static final Pattern BASH_BLOCK = Pattern.compile("```bash\\R(.*?)\\R```", Pattern.DOTALL);
  private static final Path FIXTURE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");

  @Test
  void sigtermIsHandledAsSoonAsTheListenerIsReachable(@TempDir Path directory) throws Exception {
    for (int attempt = 0; attempt < 12; attempt++) {
      Path database = directory.resolve("database-" + attempt + ".db");
      Path checkpoint = directory.resolve("checkpoint-" + attempt + ".db");
      Files.copy(FIXTURE, database);
      int port = availablePort();
      Map<String, String> environment =
          Map.of(
              "BANTENG_DATABASE", database.toString(),
              "BANTENG_CHECKPOINT", checkpoint.toString(),
              "BANTENG_PORT", Integer.toString(port));
      Process server =
          start(
              launchCommand(),
              environment,
              directory.resolve("immediate-sigterm-" + attempt + ".log"));
      try {
        awaitListener(port, server);
        server.destroy();
        assertTrue(server.waitFor(30, TimeUnit.SECONDS), "server did not exit after SIGTERM");
        assertEquals(0, server.exitValue(), "attempt " + attempt);
        assertTrue(Files.isRegularFile(checkpoint));
      } finally {
        if (server.isAlive()) {
          server.destroyForcibly();
          assertTrue(server.waitFor(30, TimeUnit.SECONDS));
        }
      }
    }
  }

  @Test
  void everyDocumentedCommandExecutesAndServerCommandsShutdownCleanly(@TempDir Path directory)
      throws Exception {
    List<String> commands = fencedCommands(Files.readString(Path.of("docs", "operations.md")));
    assertEquals(4, commands.size());

    for (int index = 0; index < commands.size(); index++) {
      Path database = directory.resolve("database-" + index + ".db");
      Path checkpoint = directory.resolve("checkpoint-" + index + ".db");
      Path recovery = directory.resolve("recovery-" + index + ".db");
      Path recoveryCheckpoint = directory.resolve("recovery-checkpoint-" + index + ".db");
      Path recording = directory.resolve("recording-" + index + ".jfr");
      Files.copy(FIXTURE, database);
      Files.copy(FIXTURE, recovery);
      int port = availablePort();
      Map<String, String> environment =
          Map.of(
              "BANTENG_DATABASE", database.toString(),
              "BANTENG_CHECKPOINT", checkpoint.toString(),
              "BANTENG_PORT", Integer.toString(port),
              "BANTENG_JFR", recording.toString(),
              "BANTENG_RECOVERY_DATABASE", recovery.toString(),
              "BANTENG_RECOVERY_CHECKPOINT", recoveryCheckpoint.toString());
      String command = commands.get(index);
      if (command.contains("build/install/banteng/bin/banteng")) {
        Path expectedCheckpoint =
            command.contains("BANTENG_RECOVERY_CHECKPOINT") ? recoveryCheckpoint : checkpoint;
        Process server = start(command, environment, directory.resolve("server-" + index + ".log"));
        awaitListener(port, server);
        server.destroy();
        assertTrue(server.waitFor(30, TimeUnit.SECONDS), "server did not exit after SIGTERM");
        assertEquals(0, server.exitValue());
        assertTrue(Files.isRegularFile(expectedCheckpoint));
        if (command.contains("StartFlightRecording")) {
          assertTrue(Files.isRegularFile(recording));
        }
      } else {
        Process server =
            start(
                launchCommand(),
                environment,
                directory.resolve("checkpoint-server-" + index + ".log"));
        awaitListener(port, server);
        Process client = start(command, environment, directory.resolve("client-" + index + ".log"));
        assertTrue(client.waitFor(15, TimeUnit.SECONDS));
        assertEquals(0, client.exitValue());
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!Files.isRegularFile(checkpoint) && System.nanoTime() < deadline) {
          Thread.onSpinWait();
        }
        assertTrue(Files.isRegularFile(checkpoint));
        server.destroy();
        assertTrue(server.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, server.exitValue());
      }
    }
  }

  private static Process start(String command, Map<String, String> environment, Path log)
      throws IOException {
    ProcessBuilder builder = new ProcessBuilder("bash", "-lc", command);
    builder.directory(Path.of("").toAbsolutePath().toFile());
    builder.environment().putAll(environment);
    builder.redirectErrorStream(true);
    builder.redirectOutput(log.toFile());
    return builder.start();
  }

  private static void awaitListener(int port, Process process) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    while (System.nanoTime() < deadline) {
      if (!process.isAlive()) {
        throw new AssertionError("documented server exited " + process.exitValue());
      }
      try (Socket socket = new Socket()) {
        socket.connect(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), port),
            100);
        return;
      } catch (IOException unavailable) {
        Thread.sleep(25);
      }
    }
    throw new AssertionError("documented listener did not accept a connection");
  }

  private static int availablePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    }
  }

  private static List<String> fencedCommands(String document) {
    Matcher matcher = BASH_BLOCK.matcher(document);
    List<String> commands = new ArrayList<>();
    while (matcher.find()) {
      commands.add(matcher.group(1).strip());
    }
    return List.copyOf(commands);
  }

  private static String launchCommand() {
    return "JAVA_HOME=/opt/java/25 build/install/banteng/bin/banteng "
        + "--database \"${BANTENG_DATABASE}\" "
        + "--checkpoint \"${BANTENG_CHECKPOINT}\" "
        + "--listen-address 127.0.0.1 --port \"${BANTENG_PORT}\"";
  }
}
