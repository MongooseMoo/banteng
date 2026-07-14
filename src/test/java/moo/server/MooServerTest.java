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
import java.util.List;
import moo.persistence.LambdaMooV4Reader;
import moo.runtime.MooRuntime;
import org.junit.jupiter.api.Test;

final class MooServerTest {
  private static final Path TEST_DATABASE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");
  private static final String CONNECTION_PREFIX = "-=!-^-!=-";
  private static final String CONNECTION_SUFFIX = "-=!-v-!=-";

  @Test
  void servesTheFirstManagedRowOverRealSockets() throws Exception {
    MooRuntime runtime = new MooRuntime(new LambdaMooV4Reader().read(TEST_DATABASE));
    MooServer server = new MooServer("127.0.0.1", 0, runtime);
    Thread serving = Thread.startVirtualThread(server::serve);
    try {

      try (Socket readiness = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
        assertFalse(readiness.isClosed());
      }

      try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port());
          BufferedReader input =
              new BufferedReader(
                  new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
          BufferedWriter output =
              new BufferedWriter(
                  new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1))) {
        socket.setSoTimeout((int) Duration.ofSeconds(5).toMillis());

        writeLine(output, "connect Wizard");
        assertEquals("*** Connected ***", input.readLine());

        writeLine(output, "PREFIX " + CONNECTION_PREFIX);
        writeLine(output, "SUFFIX " + CONNECTION_SUFFIX);

        executeSetup(output, input, "object", "#1");
        executeSetup(output, input, "anonymous", "#5");
        executeSetup(output, input, "anon", "#5");
        executeSetup(output, input, "sysobj", "#0");
        executeSetup(output, input, "nothing", "#-1");

        writeLine(output, "; return 1 + 1;");
        assertEquals(
            List.of(
                CONNECTION_PREFIX,
                CONNECTION_PREFIX,
                "{1, 2}",
                CONNECTION_SUFFIX,
                CONNECTION_SUFFIX),
            readLines(input, 5));
      }

    } finally {
      server.close();
      serving.join(Duration.ofSeconds(5));
      assertFalse(serving.isAlive());
    }
  }

  private static void executeSetup(
      BufferedWriter output, BufferedReader input, String name, String value) throws Exception {
    writeLine(
        output,
        "; try add_property(#0, \""
            + name
            + "\", "
            + value
            + ", {#0, \"rc\"}); except (ANY) return 0; endtry");
    assertEquals(
        List.of(
            CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 0}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
        readLines(input, 5));
  }

  private static void writeLine(BufferedWriter output, String line) throws Exception {
    output.write(line);
    output.write("\r\n");
    output.flush();
  }

  private static List<String> readLines(BufferedReader input, int count) throws Exception {
    List<String> lines = new java.util.ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      lines.add(input.readLine());
    }
    return List.copyOf(lines);
  }
}
