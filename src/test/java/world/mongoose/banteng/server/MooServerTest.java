package world.mongoose.banteng.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import world.mongoose.banteng.persistence.LambdaMooV17Codec;
import world.mongoose.banteng.persistence.LambdaMooV4Reader;
import world.mongoose.banteng.builtin.ConnectionRegistryAccess;
import world.mongoose.banteng.runtime.MooRuntime;
import world.mongoose.banteng.runtime.SchedulerTestHarness;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.WorldTxn;

final class MooServerTest {
  private static final Path TEST_DATABASE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");
  private static final String CONNECTION_PREFIX = "-=!-^-!=-";
  private static final String CONNECTION_SUFFIX = "-=!-v-!=-";

  @Test
  void runtimeRegistryIsSeparateFromServerSocketConnections() throws Exception {
    assertEquals(
        ConnectionRegistryAccess.class,
        MooRuntime.class.getDeclaredField("publishedConnectionRegistry").getType());
    assertEquals(Map.class, MooServer.class.getDeclaredField("connections").getType());
  }

  @Test
  void servesTheFirstManagedRowOverRealSockets(@TempDir Path temporaryDirectory) throws Exception {
    Path checkpoint = temporaryDirectory.resolve("Test.db.new");
    MooServer first =
        SchedulerTestHarness.server(new LambdaMooV4Reader(new LambdaMooV17Codec()).read(TEST_DATABASE).world(), Optional.of(TEST_DATABASE), checkpoint, List.of(), List.of());
    Thread firstServing = Thread.startVirtualThread(first::serve);
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), first.port());
        BufferedReader input =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StringValue.charset()));
        BufferedWriter output =
            new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StringValue.charset()))) {
      socket.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
      writeLine(output, "connect Wizard");
      assertEquals("*** Connected ***", input.readLine());
      writeLine(output, "; return 6 * 7;");
      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, 42}", CONNECTION_SUFFIX), readLines(input, 3));
      writeLine(output, "; return db_disk_size();");
      assertEquals(
          List.of(
              CONNECTION_PREFIX,
              "{1, " + Files.size(TEST_DATABASE) + "}",
              CONNECTION_SUFFIX),
          readLines(input, 3));
      writeLine(
          output,
          "; info = connection_info(player); return {info[\"source_ip\"], info[\"source_port\"], info[\"destination_ip\"], info[\"destination_port\"], info[\"protocol\"], info[\"outbound\"], info[\"TLS\"][\"active\"]};");
      assertEquals(
          List.of(
              CONNECTION_PREFIX,
              "{1, {\"127.0.0.1\", "
                  + first.port()
                  + ", \"127.0.0.1\", "
                  + socket.getLocalPort()
                  + ", \"IPv4\", 0, 0}}",
              CONNECTION_SUFFIX),
          readLines(input, 3));
      writeLine(output, "; return dump_database();");
      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, 0}", CONNECTION_SUFFIX), readLines(input, 3));
      writeLine(output, "; return db_disk_size();");
      assertEquals(
          List.of(
              CONNECTION_PREFIX, "{1, " + Files.size(checkpoint) + "}", CONNECTION_SUFFIX),
          readLines(input, 3));
    } finally {
      first.close();
      firstServing.join(Duration.ofSeconds(5));
      assertFalse(firstServing.isAlive());
    }

    WorldTxn restored = new LambdaMooV17Codec().read(checkpoint).world();
    MooServer restarted =
        SchedulerTestHarness.server(restored, Optional.empty(), temporaryDirectory.resolve("Test.db.next"), List.of(), List.of());
    Thread restartedServing = Thread.startVirtualThread(restarted::serve);
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), restarted.port());
        BufferedReader input =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StringValue.charset()));
        BufferedWriter output =
            new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StringValue.charset()))) {
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

  @Test
  void filtersUtf8InputBeforeEvaluatingStringsWithoutChangingConstructedBytes(
      @TempDir Path temporaryDirectory) throws Exception {
    MooServer server =
        SchedulerTestHarness.server(new LambdaMooV4Reader(new LambdaMooV17Codec()).read(TEST_DATABASE).world(), Optional.empty(), temporaryDirectory.resolve("Test.db.new"), List.of(), List.of());
    Thread serving = Thread.startVirtualThread(server::serve);
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port());
        BufferedReader input =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StringValue.charset()));
        BufferedWriter output =
            new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
      socket.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
      writeLine(output, "connect Wizard");
      assertEquals("*** Connected ***", input.readLine());

      // The conformance producer sends UTF-8 literal bytes through a normal socket.
      // Pinned Toast filters them before parsing; chr-built bytes remain real MOO strings.
      writeLine(output, """
          ; return {"A" == "a", length("À"), length("à"), "À" == "à", \
          (chr(195)+chr(128)) == (chr(195)+chr(160)), length(chr(195)+chr(128))};\
          """);
      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, {1, 0, 0, 1, 0, 2}}", CONNECTION_SUFFIX),
          readLines(input, 3));
    } finally {
      server.close();
      serving.join(Duration.ofSeconds(5));
      assertFalse(serving.isAlive());
    }
  }

  @Test
  void noFlushNotifyQueuesExactBytesUntilTheNextFlushingWrite(@TempDir Path temporaryDirectory)
      throws Exception {
    MooServer server =
        SchedulerTestHarness.server(new LambdaMooV4Reader(new LambdaMooV17Codec()).read(TEST_DATABASE).world(), Optional.empty(), temporaryDirectory.resolve("Test.db.new"), List.of(), List.of());
    Thread serving = Thread.startVirtualThread(server::serve);
    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.port());
        BufferedReader input =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StringValue.charset()));
        BufferedWriter output =
            new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StringValue.charset()))) {
      socket.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
      writeLine(output, "connect Wizard");
      assertEquals("*** Connected ***", input.readLine());

      writeLine(
          output,
          "; before = buffered_output_length(player); notify(player, \"queued\", 1, 1); after = buffered_output_length(player); return {before, after};");
      assertEquals(
          List.of("queued" + CONNECTION_PREFIX, "{1, {0, 6}}", CONNECTION_SUFFIX),
          readLines(input, 3));

      writeLine(output, "; return buffered_output_length(player);");
      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, 0}", CONNECTION_SUFFIX), readLines(input, 3));
    } finally {
      server.close();
      serving.join(Duration.ofSeconds(5));
      assertFalse(serving.isAlive());
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
