package moo.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import moo.persistence.LambdaMooV17Codec;
import moo.persistence.LambdaMooV4Reader;
import moo.world.WorldTxn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MooServerTest {
  private static final Path TEST_DATABASE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");
  private static final String CONNECTION_PREFIX = "-=!-^-!=-";
  private static final String CONNECTION_SUFFIX = "-=!-v-!=-";

  @Test
  void servesTheFirstManagedRowOverRealSockets(@TempDir Path temporaryDirectory) throws Exception {
    Path checkpoint = temporaryDirectory.resolve("Test.db.new");
    MooServer first =
        new MooServer(
            "127.0.0.1", 0, new LambdaMooV4Reader().read(TEST_DATABASE), checkpoint);
    Thread firstServing = Thread.startVirtualThread(first::serve);
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), first.port());
        BufferedReader input =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
        BufferedWriter output =
            new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1))) {
      socket.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
      writeLine(output, "connect Wizard");
      assertEquals("*** Connected ***", input.readLine());
      writeLine(output, "; return 6 * 7;");
      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, 42}", CONNECTION_SUFFIX), readLines(input, 3));
      writeLine(output, "; return dump_database();");
      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, 0}", CONNECTION_SUFFIX), readLines(input, 3));
    } finally {
      first.close();
      firstServing.join(Duration.ofSeconds(5));
      assertFalse(firstServing.isAlive());
    }

    WorldTxn restored = new LambdaMooV17Codec().read(checkpoint).world();
    MooServer restarted =
        new MooServer(
            "127.0.0.1", 0, restored, temporaryDirectory.resolve("Test.db.next"));
    Thread restartedServing = Thread.startVirtualThread(restarted::serve);
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), restarted.port());
        BufferedReader input =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
        BufferedWriter output =
            new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1))) {
      socket.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
      writeLine(output, "connect Wizard");
      assertEquals("*** Connected ***", input.readLine());
      writeLine(output, "; return 40 + 2;");
      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, 42}", CONNECTION_SUFFIX), readLines(input, 3));
    } finally {
      restarted.close();
      restartedServing.join(Duration.ofSeconds(5));
      assertFalse(restartedServing.isAlive());
    }
  }

  private static void writeLine(BufferedWriter output, String line) throws Exception {
    output.write(line);
    output.write("\r\n");
    output.flush();
  }

  private static List<String> readLines(BufferedReader input, int count) throws Exception {
    List<String> lines = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      lines.add(input.readLine());
    }
    return List.copyOf(lines);
  }
}
