package moo.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import moo.persistence.LambdaMooV4Reader;
import moo.world.WorldTxn;
import org.junit.jupiter.api.Test;

final class MooServerTest {
  private static final Path TEST_DATABASE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");
  private static final String CONNECTION_PREFIX = "-=!-^-!=-";
  private static final String CONNECTION_SUFFIX = "-=!-v-!=-";

  @Test
  void servesTheFirstManagedRowOverRealSockets() throws Exception {
    MooServer server = new MooServer("127.0.0.1", 0, new LambdaMooV4Reader().read(TEST_DATABASE));
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

        writeLine(output, "; return connection_info(player)[\"destination_ip\"];");
        assertEquals(
            List.of(
                CONNECTION_PREFIX,
                CONNECTION_PREFIX,
                "{1, \"127.0.0.1\"}",
                CONNECTION_SUFFIX,
                CONNECTION_SUFFIX),
            readLines(input, 5));

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

  @Test
  void dispatchesLoginThroughDynamicListenerHandler() throws Exception {
    int dynamicPort;
    try (ServerSocket reservation = new ServerSocket(0)) {
      dynamicPort = reservation.getLocalPort();
    }

    MooServer server = new MooServer("127.0.0.1", 0, new LambdaMooV4Reader().read(TEST_DATABASE));
    Thread serving = Thread.startVirtualThread(server::serve);
    try (Socket primary = new Socket(InetAddress.getLoopbackAddress(), server.port());
        BufferedReader primaryInput =
            new BufferedReader(
                new InputStreamReader(primary.getInputStream(), StandardCharsets.ISO_8859_1));
        BufferedWriter primaryOutput =
            new BufferedWriter(
                new OutputStreamWriter(primary.getOutputStream(), StandardCharsets.ISO_8859_1))) {
      primary.setSoTimeout((int) Duration.ofSeconds(5).toMillis());

      writeLine(primaryOutput, "connect Wizard");
      assertEquals("*** Connected ***", primaryInput.readLine());
      writeLine(primaryOutput, "PREFIX " + CONNECTION_PREFIX);
      writeLine(primaryOutput, "SUFFIX " + CONNECTION_SUFFIX);
      executeSetup(primaryOutput, primaryInput, "object", "#1");
      executeSetup(primaryOutput, primaryInput, "anonymous", "#5");
      executeSetup(primaryOutput, primaryInput, "anon", "#5");
      executeSetup(primaryOutput, primaryInput, "sysobj", "#0");
      executeSetup(primaryOutput, primaryInput, "nothing", "#-1");

      writeLine(
          primaryOutput,
          "; add_property(#0, \"audit_listener_handler\", {}, {#0, \"rw\"});"
              + " add_property(#0, \"audit_listener_port\", "
              + dynamicPort
              + ", {#0, \"rw\"});"
              + " add_property(#0, \"audit_listener_seen\", {}, {#0, \"rw\"});"
              + " handler = create($nothing);"
              + " #0.audit_listener_handler = handler;"
              + " add_verb(handler, {player, \"rxd\", \"do_login_command\"}, {\"this\", \"none\", \"this\"});"
              + " set_verb_code(handler, \"do_login_command\", {\"#0.audit_listener_seen = {this, args, argstr};\", \"return 0;\"});"
              + " return listen(handler, "
              + dynamicPort
              + ", [\"print-messages\" -> 0]);");
      assertEquals(
          List.of(
              CONNECTION_PREFIX,
              CONNECTION_PREFIX,
              "{1, " + dynamicPort + "}",
              CONNECTION_SUFFIX,
              CONNECTION_SUFFIX),
          readLines(primaryInput, 5));

      try (Socket dynamic = new Socket(InetAddress.getLoopbackAddress(), dynamicPort);
          BufferedWriter dynamicOutput =
              new BufferedWriter(
                  new OutputStreamWriter(dynamic.getOutputStream(), StandardCharsets.ISO_8859_1))) {
        writeLine(dynamicOutput, "listener-login alpha beta");
        Thread.sleep(Duration.ofMillis(250));
      }

      writeLine(
          primaryOutput,
          "; return {#0.audit_listener_seen[1] == #0.audit_listener_handler,"
              + " #0.audit_listener_seen[2], #0.audit_listener_seen[3]};");
      assertEquals(
          List.of(
              CONNECTION_PREFIX,
              CONNECTION_PREFIX,
              "{1, {1, {\"listener-login\", \"alpha\", \"beta\"}, \"listener-login alpha beta\"}}",
              CONNECTION_SUFFIX,
              CONNECTION_SUFFIX),
          readLines(primaryInput, 5));

      writeLine(
          primaryOutput,
          "; unlisten(#0.audit_listener_port);" + " recycle(#0.audit_listener_handler); return 1;");
      assertEquals(
          List.of(
              CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
          readLines(primaryInput, 5));
    } finally {
      server.close();
      serving.join(Duration.ofSeconds(5));
      assertFalse(serving.isAlive());
    }
  }

  @Test
  void stripsEscapedIacSplitAcrossReadsFromLoginInput() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(TEST_DATABASE);
    MooServer server = new MooServer("127.0.0.1", 0, world);
    Thread serving = Thread.startVirtualThread(server::serve);
    try (Socket control = new Socket(InetAddress.getLoopbackAddress(), server.port());
        BufferedReader controlInput =
            new BufferedReader(
                new InputStreamReader(control.getInputStream(), StandardCharsets.ISO_8859_1));
        BufferedWriter controlOutput =
            new BufferedWriter(
                new OutputStreamWriter(control.getOutputStream(), StandardCharsets.ISO_8859_1))) {
      control.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
      writeLine(controlOutput, "connect Wizard");
      assertEquals("*** Connected ***", controlInput.readLine());
      writeLine(controlOutput, "PREFIX " + CONNECTION_PREFIX);
      writeLine(controlOutput, "SUFFIX " + CONNECTION_SUFFIX);

      writeLine(
          controlOutput,
          "; add_property(#0, \"audit_telnet_literal_seen\", {}, {#0, \"rw\"});"
              + " try add_verb(#0, {player, \"rxd\", \"do_login_command\"}, {\"this\", \"none\", \"this\"}); except (E_INVARG) endtry"
              + " set_verb_info(#0, \"do_login_command\", {player, \"rxd\", \"do_login_command\"});"
              + " set_verb_args(#0, \"do_login_command\", {\"this\", \"none\", \"this\"});"
              + " set_verb_code(#0, \"do_login_command\", {"
              + "\"#0.audit_telnet_literal_seen = {args, argstr};\","
              + "\"notify(player, \\\"DONE\\\");\","
              + "\"return 0;\"});"
              + " return 1;");
      assertEquals(
          List.of(
              CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
          readLines(controlInput, 5));

      try (Socket target = new Socket(InetAddress.getLoopbackAddress(), server.port());
          BufferedReader targetInput =
              new BufferedReader(
                  new InputStreamReader(target.getInputStream(), StandardCharsets.ISO_8859_1))) {
        target.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
        OutputStream targetOutput = target.getOutputStream();
        assertEquals("DONE", targetInput.readLine());

        targetOutput.write("iac-".getBytes(StandardCharsets.ISO_8859_1));
        targetOutput.flush();
        targetOutput.write(0xFF);
        targetOutput.flush();
        targetOutput.write(0xFF);
        targetOutput.flush();
        targetOutput.write("-login\r\n".getBytes(StandardCharsets.ISO_8859_1));
        targetOutput.flush();

        assertEquals("DONE", targetInput.readLine());
        assertEquals(
            "{{\"iac--login\"}, \"iac--login\"}",
            world.property(0, "audit_telnet_literal_seen").orElseThrow().value().toLiteral());
      }
    } finally {
      server.close();
      serving.join(Duration.ofSeconds(5));
      assertFalse(serving.isAlive());
    }
  }

  @Test
  void dispatchesTwoByteTelnetCommandSplitAcrossReadsAsOutOfBandInput() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(TEST_DATABASE);
    MooServer server = new MooServer("127.0.0.1", 0, world);
    Thread serving = Thread.startVirtualThread(server::serve);
    try (Socket control = new Socket(InetAddress.getLoopbackAddress(), server.port());
        BufferedReader controlInput =
            new BufferedReader(
                new InputStreamReader(control.getInputStream(), StandardCharsets.ISO_8859_1));
        BufferedWriter controlOutput =
            new BufferedWriter(
                new OutputStreamWriter(control.getOutputStream(), StandardCharsets.ISO_8859_1))) {
      control.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
      writeLine(controlOutput, "connect Wizard");
      assertEquals("*** Connected ***", controlInput.readLine());
      writeLine(controlOutput, "PREFIX " + CONNECTION_PREFIX);
      writeLine(controlOutput, "SUFFIX " + CONNECTION_SUFFIX);

      writeLine(
          controlOutput,
          "; add_property(#0, \"audit_telnet_nop_seen\", {}, {#0, \"rw\"});"
              + " try add_verb(#0, {#0, \"rxd\", \"do_out_of_band_command\"}, {\"this\", \"none\", \"this\"}); except (E_INVARG) endtry"
              + " set_verb_info(#0, \"do_out_of_band_command\", {#0, \"rxd\", \"do_out_of_band_command\"});"
              + " set_verb_args(#0, \"do_out_of_band_command\", {\"this\", \"none\", \"this\"});"
              + " set_verb_code(#0, \"do_out_of_band_command\", {"
              + "\"#0.audit_telnet_nop_seen = {args, argstr};\","
              + "\"return 1;\"});"
              + " return 1;");
      assertEquals(
          List.of(
              CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
          readLines(controlInput, 5));

      try (Socket target = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
        OutputStream targetOutput = target.getOutputStream();
        targetOutput.write(0xFF);
        targetOutput.flush();
        targetOutput.write(0xF1);
        targetOutput.flush();

        Thread.sleep(Duration.ofMillis(500));
        assertEquals(
            "{{\"~FF~F1\"}, \"~FF~F1\"}",
            world.property(0, "audit_telnet_nop_seen").orElseThrow().value().toLiteral());
      }
    } finally {
      server.close();
      serving.join(Duration.ofSeconds(5));
      assertFalse(serving.isAlive());
    }
  }

  @Test
  void dispatchesTelnetNegotiationSplitAcrossReadsAsOutOfBandInput() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(TEST_DATABASE);
    MooServer server = new MooServer("127.0.0.1", 0, world);
    Thread serving = Thread.startVirtualThread(server::serve);
    try (Socket control = new Socket(InetAddress.getLoopbackAddress(), server.port());
        BufferedReader controlInput =
            new BufferedReader(
                new InputStreamReader(control.getInputStream(), StandardCharsets.ISO_8859_1));
        BufferedWriter controlOutput =
            new BufferedWriter(
                new OutputStreamWriter(control.getOutputStream(), StandardCharsets.ISO_8859_1))) {
      control.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
      writeLine(controlOutput, "connect Wizard");
      assertEquals("*** Connected ***", controlInput.readLine());
      writeLine(controlOutput, "PREFIX " + CONNECTION_PREFIX);
      writeLine(controlOutput, "SUFFIX " + CONNECTION_SUFFIX);

      writeLine(
          controlOutput,
          "; add_property(#0, \"audit_telnet_will_seen\", {}, {#0, \"rw\"});"
              + " try add_verb(#0, {#0, \"rxd\", \"do_out_of_band_command\"}, {\"this\", \"none\", \"this\"}); except (E_INVARG) endtry"
              + " set_verb_info(#0, \"do_out_of_band_command\", {#0, \"rxd\", \"do_out_of_band_command\"});"
              + " set_verb_args(#0, \"do_out_of_band_command\", {\"this\", \"none\", \"this\"});"
              + " set_verb_code(#0, \"do_out_of_band_command\", {"
              + "\"#0.audit_telnet_will_seen = {args, argstr};\","
              + "\"return 1;\"});"
              + " return 1;");
      assertEquals(
          List.of(
              CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
          readLines(controlInput, 5));

      try (Socket target = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
        OutputStream targetOutput = target.getOutputStream();
        targetOutput.write(0xFF);
        targetOutput.flush();
        Thread.sleep(Duration.ofMillis(100));
        targetOutput.write(new byte[] {(byte) 0xFB, 0x01});
        targetOutput.flush();

        Thread.sleep(Duration.ofMillis(500));
        assertEquals(
            "{{\"~FF~FB~01\"}, \"~FF~FB~01\"}",
            world.property(0, "audit_telnet_will_seen").orElseThrow().value().toLiteral());
      }
    } finally {
      server.close();
      serving.join(Duration.ofSeconds(5));
      assertFalse(serving.isAlive());
    }
  }

  @Test
  void dispatchesTelnetSubnegotiationSplitAcrossWritesAsOutOfBandInput() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(TEST_DATABASE);
    MooServer server = new MooServer("127.0.0.1", 0, world);
    Thread serving = Thread.startVirtualThread(server::serve);
    try (Socket control = new Socket(InetAddress.getLoopbackAddress(), server.port());
        BufferedReader controlInput =
            new BufferedReader(
                new InputStreamReader(control.getInputStream(), StandardCharsets.ISO_8859_1));
        BufferedWriter controlOutput =
            new BufferedWriter(
                new OutputStreamWriter(control.getOutputStream(), StandardCharsets.ISO_8859_1))) {
      control.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
      writeLine(controlOutput, "connect Wizard");
      assertEquals("*** Connected ***", controlInput.readLine());
      writeLine(controlOutput, "PREFIX " + CONNECTION_PREFIX);
      writeLine(controlOutput, "SUFFIX " + CONNECTION_SUFFIX);

      writeLine(
          controlOutput,
          "; add_property(#0, \"audit_telnet_sb_seen\", {}, {#0, \"rw\"});"
              + " try add_verb(#0, {#0, \"rxd\", \"do_out_of_band_command\"}, {\"this\", \"none\", \"this\"}); except (E_INVARG) endtry"
              + " set_verb_info(#0, \"do_out_of_band_command\", {#0, \"rxd\", \"do_out_of_band_command\"});"
              + " set_verb_args(#0, \"do_out_of_band_command\", {\"this\", \"none\", \"this\"});"
              + " set_verb_code(#0, \"do_out_of_band_command\", {"
              + "\"#0.audit_telnet_sb_seen = {args, argstr};\","
              + "\"return 1;\"});"
              + " return 1;");
      assertEquals(
          List.of(
              CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
          readLines(controlInput, 5));

      try (Socket target = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
        OutputStream targetOutput = target.getOutputStream();
        targetOutput.write(0xFF);
        targetOutput.flush();
        targetOutput.write(new byte[] {(byte) 0xFA, 0x1F, 0x00, 0x50, 0x00, 0x18});
        targetOutput.flush();
        targetOutput.write(0xFF);
        targetOutput.flush();
        targetOutput.write(0xF0);
        targetOutput.flush();

        Thread.sleep(Duration.ofMillis(500));
        assertEquals(
            "{{\"~FF~FA~1F~00P~00~18~FF~F0\"}, \"~FF~FA~1F~00P~00~18~FF~F0\"}",
            world.property(0, "audit_telnet_sb_seen").orElseThrow().value().toLiteral());
      }
    } finally {
      server.close();
      serving.join(Duration.ofSeconds(5));
      assertFalse(serving.isAlive());
    }
  }

  @Test
  void dispatchesBinaryModeChunkWithoutLineTerminator() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(TEST_DATABASE);
    MooServer server = new MooServer("127.0.0.1", 0, world);
    Thread serving = Thread.startVirtualThread(server::serve);
    try (Socket control = new Socket(InetAddress.getLoopbackAddress(), server.port());
        BufferedReader controlInput =
            new BufferedReader(
                new InputStreamReader(control.getInputStream(), StandardCharsets.ISO_8859_1));
        BufferedWriter controlOutput =
            new BufferedWriter(
                new OutputStreamWriter(control.getOutputStream(), StandardCharsets.ISO_8859_1))) {
      control.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
      writeLine(controlOutput, "connect Wizard");
      assertEquals("*** Connected ***", controlInput.readLine());
      writeLine(controlOutput, "PREFIX " + CONNECTION_PREFIX);
      writeLine(controlOutput, "SUFFIX " + CONNECTION_SUFFIX);

      writeLine(
          controlOutput,
          "; add_property(#0, \"audit_binary_player\", {}, {#0, \"rw\"});"
              + " add_property(#0, \"audit_binary_seen\", {}, {#0, \"rw\"});"
              + " login_player = create($nothing);"
              + " set_player_flag(login_player, 1);"
              + " #0.audit_binary_player = login_player;"
              + " #0.audit_binary_seen = {};"
              + " add_verb(login_player, {player, \"rxd\", \"auditbin\"}, {\"none\", \"none\", \"none\"});"
              + " set_verb_code(login_player, \"auditbin\", {"
              + "\"#0.audit_binary_seen = {@#0.audit_binary_seen, argstr};\","
              + "\"return 1;\"});"
              + " try add_verb(#0, {player, \"rxd\", \"do_login_command\"}, {\"this\", \"none\", \"this\"}); except (E_INVARG) endtry"
              + " set_verb_info(#0, \"do_login_command\", {player, \"rxd\", \"do_login_command\"});"
              + " set_verb_args(#0, \"do_login_command\", {\"this\", \"none\", \"this\"});"
              + " set_verb_code(#0, \"do_login_command\", {\"return #0.audit_binary_player;\"});"
              + " return 1;");
      assertEquals(
          List.of(
              CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
          readLines(controlInput, 5));

      try (Socket target = new Socket(InetAddress.getLoopbackAddress(), server.port());
          BufferedReader targetInput =
              new BufferedReader(
                  new InputStreamReader(target.getInputStream(), StandardCharsets.ISO_8859_1))) {
        target.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
        OutputStream targetOutput = target.getOutputStream();
        targetOutput.write("login-binary-mode\r\n".getBytes(StandardCharsets.ISO_8859_1));
        targetOutput.flush();
        assertEquals("*** Connected ***", targetInput.readLine());

        writeLine(
            controlOutput,
            "; set_connection_option(#0.audit_binary_player, \"binary\", 1); return 1;");
        readLines(controlInput, 5);

        targetOutput.write("auditbin".getBytes(StandardCharsets.ISO_8859_1));
        targetOutput.flush();
        Thread.sleep(Duration.ofMillis(500));

        assertEquals(
            "{\"\"}", world.property(0, "audit_binary_seen").orElseThrow().value().toLiteral());
      }
    } finally {
      server.close();
      serving.join(Duration.ofSeconds(5));
      assertFalse(serving.isAlive());
    }
  }

  @Test
  void bootsAnActivePlayerAfterLogicalDisconnectWithDefaultMessage() throws Exception {
    MooServer server = new MooServer("127.0.0.1", 0, new LambdaMooV4Reader().read(TEST_DATABASE));
    Thread serving = Thread.startVirtualThread(server::serve);
    try (Socket control = new Socket(InetAddress.getLoopbackAddress(), server.port());
        BufferedReader controlInput =
            new BufferedReader(
                new InputStreamReader(control.getInputStream(), StandardCharsets.ISO_8859_1));
        BufferedWriter controlOutput =
            new BufferedWriter(
                new OutputStreamWriter(control.getOutputStream(), StandardCharsets.ISO_8859_1))) {
      control.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
      writeLine(controlOutput, "connect Wizard");
      assertEquals("*** Connected ***", controlInput.readLine());
      writeLine(controlOutput, "PREFIX " + CONNECTION_PREFIX);
      writeLine(controlOutput, "SUFFIX " + CONNECTION_SUFFIX);

      writeLine(
          controlOutput,
          "; add_property(#0, \"audit_boot_player\", {}, {#0, \"rw\"});"
              + " add_property(#0, \"audit_boot_disconnected\", {}, {#0, \"rw\"});"
              + " login_player = create($nothing);"
              + " set_player_flag(login_player, 1);"
              + " #0.audit_boot_player = login_player;"
              + " try add_verb(#0, {player, \"rxd\", \"do_login_command\"}, {\"this\", \"none\", \"this\"}); except (E_INVARG) endtry"
              + " set_verb_info(#0, \"do_login_command\", {player, \"rxd\", \"do_login_command\"});"
              + " set_verb_args(#0, \"do_login_command\", {\"this\", \"none\", \"this\"});"
              + " set_verb_code(#0, \"do_login_command\", {\"return #0.audit_boot_player;\"});"
              + " try add_verb(#0, {player, \"rxd\", \"user_disconnected\"}, {\"this\", \"none\", \"this\"}); except (E_INVARG) endtry"
              + " set_verb_info(#0, \"user_disconnected\", {player, \"rxd\", \"user_disconnected\"});"
              + " set_verb_args(#0, \"user_disconnected\", {\"this\", \"none\", \"this\"});"
              + " set_verb_code(#0, \"user_disconnected\", {"
              + "\"connection_info_succeeds = 1;\","
              + "\"try connection_info(player); except (E_INVARG) connection_info_succeeds = 0; endtry\","
              + "\"#0.audit_boot_disconnected = {this, player, caller, args, argstr, connection_info_succeeds};\"});"
              + " return 1;");
      assertEquals(
          List.of(
              CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
          readLines(controlInput, 5));

      try (Socket target = new Socket(InetAddress.getLoopbackAddress(), server.port());
          BufferedReader targetInput =
              new BufferedReader(
                  new InputStreamReader(target.getInputStream(), StandardCharsets.ISO_8859_1))) {
        target.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
        assertEquals("*** Connected ***", targetInput.readLine());

        writeLine(controlOutput, "; boot_player(#0.audit_boot_player); return 1;");
        assertEquals(
            List.of(
                CONNECTION_PREFIX,
                CONNECTION_PREFIX,
                "{1, 1}",
                CONNECTION_SUFFIX,
                CONNECTION_SUFFIX),
            readLines(controlInput, 5));
        assertEquals("*** Disconnected ***", targetInput.readLine());
        assertNull(targetInput.readLine());
      }

      writeLine(
          controlOutput,
          "; seen = #0.audit_boot_disconnected;"
              + " return {seen[1] == #0, seen[2] == #0.audit_boot_player, seen[3] == #-1,"
              + " seen[4] == {#0.audit_boot_player}, seen[5] == \"\", seen[6] == 0};");
      assertEquals(
          List.of(
              CONNECTION_PREFIX,
              CONNECTION_PREFIX,
              "{1, {1, 1, 1, 1, 1, 1}}",
              CONNECTION_SUFFIX,
              CONNECTION_SUFFIX),
          readLines(controlInput, 5));
    } finally {
      server.close();
      serving.join(Duration.ofSeconds(5));
      assertFalse(serving.isAlive());
    }
  }

  @Test
  void recyclesAnActivePlayerWithDefaultMessage() throws Exception {
    MooServer server = new MooServer("127.0.0.1", 0, new LambdaMooV4Reader().read(TEST_DATABASE));
    Thread serving = Thread.startVirtualThread(server::serve);
    try (Socket control = new Socket(InetAddress.getLoopbackAddress(), server.port());
        BufferedReader controlInput =
            new BufferedReader(
                new InputStreamReader(control.getInputStream(), StandardCharsets.ISO_8859_1));
        BufferedWriter controlOutput =
            new BufferedWriter(
                new OutputStreamWriter(control.getOutputStream(), StandardCharsets.ISO_8859_1))) {
      control.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
      writeLine(controlOutput, "connect Wizard");
      assertEquals("*** Connected ***", controlInput.readLine());
      writeLine(controlOutput, "PREFIX " + CONNECTION_PREFIX);
      writeLine(controlOutput, "SUFFIX " + CONNECTION_SUFFIX);

      writeLine(
          controlOutput,
          "; add_property(#0, \"audit_recycle_player\", {}, {#0, \"rw\"});"
              + " login_player = create($nothing);"
              + " set_player_flag(login_player, 1);"
              + " #0.audit_recycle_player = login_player;"
              + " try add_verb(#0, {player, \"rxd\", \"do_login_command\"}, {\"this\", \"none\", \"this\"}); except (E_INVARG) endtry"
              + " set_verb_info(#0, \"do_login_command\", {player, \"rxd\", \"do_login_command\"});"
              + " set_verb_args(#0, \"do_login_command\", {\"this\", \"none\", \"this\"});"
              + " set_verb_code(#0, \"do_login_command\", {\"return #0.audit_recycle_player;\"});"
              + " return 1;");
      assertEquals(
          List.of(
              CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
          readLines(controlInput, 5));

      try (Socket target = new Socket(InetAddress.getLoopbackAddress(), server.port());
          BufferedReader targetInput =
              new BufferedReader(
                  new InputStreamReader(target.getInputStream(), StandardCharsets.ISO_8859_1))) {
        target.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
        assertEquals("*** Connected ***", targetInput.readLine());

        writeLine(controlOutput, "; recycle(#0.audit_recycle_player); return 1;");
        assertEquals(
            List.of(
                CONNECTION_PREFIX,
                CONNECTION_PREFIX,
                "{1, 1}",
                CONNECTION_SUFFIX,
                CONNECTION_SUFFIX),
            readLines(controlInput, 5));
        assertEquals("*** Recycled ***", targetInput.readLine());
        assertNull(targetInput.readLine());
      }
    } finally {
      server.close();
      serving.join(Duration.ofSeconds(5));
      assertFalse(serving.isAlive());
    }
  }

  @Test
  void timesOutUnauthenticatedConnectionWithDefaultMessage() throws Exception {
    MooServer server = new MooServer("127.0.0.1", 0, new LambdaMooV4Reader().read(TEST_DATABASE));
    Thread serving = Thread.startVirtualThread(server::serve);
    try (Socket control = new Socket(InetAddress.getLoopbackAddress(), server.port());
        BufferedReader controlInput =
            new BufferedReader(
                new InputStreamReader(control.getInputStream(), StandardCharsets.ISO_8859_1));
        BufferedWriter controlOutput =
            new BufferedWriter(
                new OutputStreamWriter(control.getOutputStream(), StandardCharsets.ISO_8859_1))) {
      control.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
      writeLine(controlOutput, "connect Wizard");
      assertEquals("*** Connected ***", controlInput.readLine());
      writeLine(controlOutput, "PREFIX " + CONNECTION_PREFIX);
      writeLine(controlOutput, "SUFFIX " + CONNECTION_SUFFIX);

      writeLine(
          controlOutput,
          "; try add_property($server_options, \"connect_timeout\", 1, {player, \"r\"});"
              + " except (E_INVARG) endtry"
              + " $server_options.connect_timeout = 1; return 1;");
      assertEquals(
          List.of(
              CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
          readLines(controlInput, 5));

      try (Socket target = new Socket(InetAddress.getLoopbackAddress(), server.port());
          BufferedReader targetInput =
              new BufferedReader(
                  new InputStreamReader(target.getInputStream(), StandardCharsets.ISO_8859_1))) {
        target.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
        assertEquals("*** Timed-out waiting for login. ***", targetInput.readLine());
        assertNull(targetInput.readLine());
      }
    } finally {
      server.close();
      serving.join(Duration.ofSeconds(5));
      assertFalse(serving.isAlive());
    }
  }

  @Test
  void redirectsSamePlayerLoginToReplacementConnection() throws Exception {
    MooServer server = new MooServer("127.0.0.1", 0, new LambdaMooV4Reader().read(TEST_DATABASE));
    Thread serving = Thread.startVirtualThread(server::serve);
    try (Socket control = new Socket(InetAddress.getLoopbackAddress(), server.port());
        BufferedReader controlInput =
            new BufferedReader(
                new InputStreamReader(control.getInputStream(), StandardCharsets.ISO_8859_1));
        BufferedWriter controlOutput =
            new BufferedWriter(
                new OutputStreamWriter(control.getOutputStream(), StandardCharsets.ISO_8859_1))) {
      control.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
      writeLine(controlOutput, "connect Wizard");
      assertEquals("*** Connected ***", controlInput.readLine());
      writeLine(controlOutput, "PREFIX " + CONNECTION_PREFIX);
      writeLine(controlOutput, "SUFFIX " + CONNECTION_SUFFIX);

      writeLine(
          controlOutput,
          "; for prop in ({\"audit_redirect_player\", \"audit_redirect_ports\", \"audit_redirect_reconnected\"})"
              + " try add_property(#0, prop, {}, {#0, \"rw\"}); except (E_INVARG) endtry endfor"
              + " login_player = create($nothing); set_player_flag(login_player, 1);"
              + " #0.audit_redirect_player = login_player; #0.audit_redirect_ports = {};"
              + " try add_verb(#0, {player, \"rxd\", \"do_login_command\"}, {\"this\", \"none\", \"this\"}); except (E_INVARG) endtry"
              + " set_verb_info(#0, \"do_login_command\", {player, \"rxd\", \"do_login_command\"});"
              + " set_verb_args(#0, \"do_login_command\", {\"this\", \"none\", \"this\"});"
              + " set_verb_code(#0, \"do_login_command\", {\"#0.audit_redirect_ports = {@#0.audit_redirect_ports, connection_info(player)[\\\"source_port\\\"]};\", \"return #0.audit_redirect_player;\"});"
              + " try add_verb(#0, {player, \"rxd\", \"user_reconnected\"}, {\"this\", \"none\", \"this\"}); except (E_INVARG) endtry"
              + " set_verb_info(#0, \"user_reconnected\", {player, \"rxd\", \"user_reconnected\"});"
              + " set_verb_args(#0, \"user_reconnected\", {\"this\", \"none\", \"this\"});"
              + " set_verb_code(#0, \"user_reconnected\", {\"#0.audit_redirect_reconnected = {this, player, caller, args, argstr, connection_info(player)[\\\"source_port\\\"]};\"});"
              + " return 1;");
      assertEquals(
          List.of(
              CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
          readLines(controlInput, 5));

      try (Socket oldConnection = new Socket(InetAddress.getLoopbackAddress(), server.port());
          BufferedReader oldInput =
              new BufferedReader(
                  new InputStreamReader(
                      oldConnection.getInputStream(), StandardCharsets.ISO_8859_1))) {
        oldConnection.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
        assertEquals("*** Connected ***", oldInput.readLine());

        try (Socket replacement = new Socket(InetAddress.getLoopbackAddress(), server.port());
            BufferedReader replacementInput =
                new BufferedReader(
                    new InputStreamReader(
                        replacement.getInputStream(), StandardCharsets.ISO_8859_1));
            BufferedWriter replacementOutput =
                new BufferedWriter(
                    new OutputStreamWriter(
                        replacement.getOutputStream(), StandardCharsets.ISO_8859_1))) {
          replacement.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
          assertEquals(
              "*** Redirecting old connection to this port ***", replacementInput.readLine());
          assertEquals("*** Redirecting connection to new port ***", oldInput.readLine());
          assertNull(oldInput.readLine());

          writeLine(replacementOutput, "redirect-new-command");
          assertEquals("I couldn't understand that.", replacementInput.readLine());

          writeLine(
              controlOutput,
              "; seen = #0.audit_redirect_reconnected; count = 0;"
                  + " for candidate in (connected_players(1))"
                  + " if (candidate == #0.audit_redirect_player) count = count + 1; endif endfor"
                  + " return {{seen[1] == #0, seen[2] == #0.audit_redirect_player,"
                  + " seen[3] == #-1, seen[4] == {#0.audit_redirect_player}, seen[5] == \"\","
                  + " seen[6] == #0.audit_redirect_ports[2]}, count};");
          assertEquals(
              List.of(
                  CONNECTION_PREFIX,
                  CONNECTION_PREFIX,
                  "{1, {{1, 1, 1, 1, 1, 1}, 1}}",
                  CONNECTION_SUFFIX,
                  CONNECTION_SUFFIX),
              readLines(controlInput, 5));
        }
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
