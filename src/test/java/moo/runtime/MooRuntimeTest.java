package moo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import moo.builtin.BuiltinCatalog;
import moo.builtin.EffectClass;
import moo.persistence.LambdaMooV4Reader;
import moo.value.MooValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.world.WorldObject;
import moo.world.WorldProperty;
import moo.world.WorldTxn;
import moo.world.WorldVerb;
import org.junit.jupiter.api.Test;

final class MooRuntimeTest {
  private static final Path FIXTURE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");
  private static final String CONNECTION_PREFIX = "-=!-^-!=-";
  private static final String CONNECTION_SUFFIX = "-=!-v-!=-";

  @Test
  void executesTheFirstManagedRowThroughStoredVerbsAndOneWorldTxn() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);

    assertEquals(List.of(), runtime.openConnection(-2));
    runtime.closeConnection(-2);

    long connectionId = -47;
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    WorldObject wizard = readObject(world, 8).orElseThrow();
    assertEquals(8, wizard.owner());
    assertEquals(7, wizard.flags());
    assertEquals(2, wizard.location());
    assertEquals(List.of(3L, 4L, 8L), readObject(world, 2).orElseThrow().contents());
    assertEquals(List.of(3L, 4L, 8L), world.snapshot().players());

    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    executeSetup(runtime, connectionId, "object", "#1");
    executeSetup(runtime, connectionId, "anonymous", "#5");
    executeSetup(runtime, connectionId, "anon", "#5");
    executeSetup(runtime, connectionId, "sysobj", "#0");
    executeSetup(runtime, connectionId, "nothing", "#-1");

    assertEquals(new ObjectValue(5), readProperty(world, 0, "anon").orElseThrow().value());
    assertEquals(new ObjectValue(0), readProperty(world, 0, "sysobj").orElseThrow().value());
    assertEquals(5, readProperty(world, 0, "anon").orElseThrow().permissions());
    assertEquals(5, readProperty(world, 0, "sysobj").orElseThrow().permissions());
    assertEquals(8, readObject(world, 0).orElseThrow().properties().size());

    assertEquals(
        List.of(
            CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 2}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return 1 + 1;"));
  }

  @Test
  void authorizesDynamicCallFunctionThroughTheProductionScheduler() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 1}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; direct = seconds_left(); indirect = call_function(\"seconds_left\"); "
                + "return {direct >= 0 && direct <= 5, indirect >= 0 && indirect <= direct};"));
    assertEquals(
        EffectClass.IRREVOCABLE,
        new BuiltinCatalog().spec("call_function").orElseThrow().effect());
  }

  @Test
  void formatsLegacyConnectionNameFromAttachedPlayerMetadata() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;
    MapValue connectionInfo =
        new MapValue(
            Map.of(
                new StringValue("source_port".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(7777),
                new StringValue("destination_address".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("client.example".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("destination_ip".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("198.51.100.7".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("destination_port".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(4567)));

    assertEquals(List.of(), runtime.openConnection(connectionId, 0, true, connectionInfo));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, \"port 7777 from client.example [198.51.100.7], port 4567\"}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return connection_name(player, 0);"));
    assertEquals(
        EffectClass.EXTERNAL_READ,
        new BuiltinCatalog().spec("connection_name").orElseThrow().effect());
  }

  @Test
  void readsAndRestoresCanonicalLocalVerbMetadata() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    runtime.executeLine(
        connectionId,
        """
        ; add_property(#0, "audit_verb_getters", {}, {#0, "rw"});
        object = create($nothing);
        add_verb(object, {player, "rxd", "get*ter_probe"}, {"this", "none", "this"});
        set_verb_code(object, "getter_probe", {"1   ;"});
        info = verb_info(object, "getter_probe");
        arguments = verb_args(object, "getter_probe");
        code = verb_code(object, "getter_probe");
        set_verb_info(object, "getter_probe", info);
        set_verb_args(object, "getter_probe", arguments);
        set_verb_code(object, "getter_probe", code);
        result = object:getter_probe();
        #0.audit_verb_getters = {
          info,
          arguments,
          code,
          result,
          verb_info(object, "getter_probe"),
          verb_args(object, "getter_probe"),
          verb_code(object, "getter_probe")
        };
        return 1;
        """);

    assertEquals(
        "{{#8, \"rxd\", \"get*ter_probe\"}, "
            + "{\"this\", \"none\", \"this\"}, {\"1;\"}, 0, "
            + "{#8, \"rxd\", \"get*ter_probe\"}, "
            + "{\"this\", \"none\", \"this\"}, {\"1;\"}}",
        readProperty(world, 0, "audit_verb_getters").orElseThrow().value().toLiteral());
  }

  @Test
  void authenticatesReturnedPlayerAndDispatchesCommandToListenerHandler() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long primaryConnection = -47;

    assertEquals(List.of(), runtime.openConnection(primaryConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(primaryConnection, "connect Wizard"));
    long handler = world.snapshot().objects().size();
    long loginPlayer = handler + 1;
    runtime.executeLine(
        primaryConnection,
        """
        ; add_property(#0, "audit_command_handler", {}, {#0, "rw"});
        add_property(#0, "audit_command_player", {}, {#0, "rw"});
        add_property(#0, "audit_command_seen", {}, {#0, "rw"});
        handler = create($nothing);
        login_player = create($nothing);
        set_player_flag(login_player, 1);
        #0.audit_command_handler = handler;
        #0.audit_command_player = login_player;
        add_verb(handler, {player, "rxd", "do_login_command"}, {"this", "none", "this"});
        set_verb_code(handler, "do_login_command", {"return #0.audit_command_player;"});
        add_verb(handler, {player, "rxd", "do_command"}, {"this", "none", "this"});
        set_verb_code(handler, "do_command", {
          "#0.audit_command_seen = {this, player, args, argstr};",
          "return 0;"
        });
        return 1;
        """);
    assertTrue(readObject(world, handler).isPresent());
    assertTrue(readObject(world, loginPlayer).isPresent());

    long dynamicConnection = -48;
    try {
      assertEquals(List.of(), runtime.openConnection(dynamicConnection, handler, false));
      assertEquals(loginPlayer, runtime.connectionPlayer(dynamicConnection).orElseThrow());
      assertEquals(
          List.of("I couldn't understand that."),
          runtime.executeLine(dynamicConnection, "postlogin alpha beta"));
      assertEquals(
          "{#"
              + handler
              + ", #"
              + loginPlayer
              + ", {\"postlogin\", \"alpha\", \"beta\"}, \"postlogin alpha beta\"}",
          readProperty(world, 0, "audit_command_seen").orElseThrow().value().toLiteral());
    } finally {
      runtime.closeConnection(dynamicConnection);
    }
  }

  @Test
  void callsUserCreatedAfterAssociatingNewReturnedPlayerAndBeforeUserConnected() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long primaryConnection = -47;

    assertEquals(List.of(), runtime.openConnection(primaryConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(primaryConnection, "connect Wizard"));
    runtime.executeLine(
        primaryConnection,
        """
        ; add_property(#0, "audit_created_handler", {}, {#0, "rw"});
          add_property(#0, "audit_created_player", {}, {#0, "rw"});
          add_property(#0, "audit_created_seen", {}, {#0, "rw"});
          add_property(#0, "audit_created_order", {}, {#0, "rw"});
          handler = create($nothing);
          #0.audit_created_handler = handler;
          add_verb(handler, {player, "rxd", "do_login_command"}, {"this", "none", "this"});
          set_verb_code(handler, "do_login_command", {
            "login_player = create($nothing);",
            "set_player_flag(login_player, 1);",
            "#0.audit_created_player = login_player;",
            "return login_player;"
          });
          add_verb(handler, {player, "rxd", "user_created"}, {"this", "none", "this"});
          set_verb_code(handler, "user_created", {
            "info = connection_info(args[1]);",
            "#0.audit_created_seen = {this, player, caller, args, argstr, info[\\\"source_port\\\"]};",
            "#0.audit_created_order = {@#0.audit_created_order, \\\"created\\\"};",
            "return 1;"
          });
          add_verb(handler, {player, "rxd", "user_connected"}, {"this", "none", "this"});
          set_verb_code(handler, "user_connected", {
            "#0.audit_created_order = {@#0.audit_created_order, \\\"connected\\\"};",
            "return 1;"
          });
          return handler;
        """);
    long handler =
        ((ObjectValue) readProperty(world, 0, "audit_created_handler").orElseThrow().value()).value();
    assertTrue(readObject(world, handler).isPresent());

    StringValue sourcePortKey =
        new StringValue("source_port".getBytes(StandardCharsets.ISO_8859_1));
    int sourcePort = 41003;
    MapValue connectionInfo = new MapValue(Map.of(sourcePortKey, new IntegerValue(sourcePort)));
    long dynamicConnection = -48;
    try {
      assertEquals(
          List.of(), runtime.openConnection(dynamicConnection, handler, false, connectionInfo));
      long loginPlayer =
          ((ObjectValue) readProperty(world, 0, "audit_created_player").orElseThrow().value()).value();
      assertEquals(loginPlayer, runtime.connectionPlayer(dynamicConnection).orElseThrow());
      assertEquals(
          "{#"
              + handler
              + ", #"
              + loginPlayer
              + ", #-1, {#"
              + loginPlayer
              + "}, \"\", "
              + sourcePort
              + "}",
          readProperty(world, 0, "audit_created_seen").orElseThrow().value().toLiteral());
      assertEquals(
          "{\"created\", \"connected\"}",
          readProperty(world, 0, "audit_created_order").orElseThrow().value().toLiteral());
    } finally {
      runtime.closeConnection(dynamicConnection);
    }
  }

  @Test
  void callsUserConnectedOnAcceptingHandlerAfterFreshReturnedPlayerLogin() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long primaryConnection = -47;

    assertEquals(List.of(), runtime.openConnection(primaryConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(primaryConnection, "connect Wizard"));
    long handler = world.snapshot().objects().size();
    long loginPlayer = handler + 1;
    runtime.executeLine(
        primaryConnection,
        """
        ; add_property(#0, "audit_connected_player", {}, {#0, "rw"});
        add_property(#0, "audit_connected_seen", {}, {#0, "rw"});
        handler = create($nothing);
        login_player = create($nothing);
        set_player_flag(login_player, 1);
        #0.audit_connected_player = login_player;
        add_verb(handler, {player, "rxd", "do_login_command"}, {"this", "none", "this"});
        set_verb_code(handler, "do_login_command", {"return #0.audit_connected_player;"});
        add_verb(handler, {player, "rxd", "user_connected"}, {"this", "none", "this"});
        set_verb_code(handler, "user_connected", {
          "#0.audit_connected_seen = {this, player, caller, args, argstr};",
          "raise(E_INVARG);"
        });
        return 1;
        """);
    assertTrue(readObject(world, handler).isPresent());
    assertTrue(readObject(world, loginPlayer).isPresent());

    long dynamicConnection = -48;
    try {
      assertEquals(List.of(), runtime.openConnection(dynamicConnection, handler, false));
      assertEquals(loginPlayer, runtime.connectionPlayer(dynamicConnection).orElseThrow());
      assertEquals(
          "{#" + handler + ", #" + loginPlayer + ", #-1, {#" + loginPlayer + "}, \"\"}",
          readProperty(world, 0, "audit_connected_seen").orElseThrow().value().toLiteral());
    } finally {
      runtime.closeConnection(dynamicConnection);
    }
  }

  @Test
  void callsUserClientDisconnectedOnceAfterLogicalDisassociation() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long primaryConnection = -47;

    assertEquals(List.of(), runtime.openConnection(primaryConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(primaryConnection, "connect Wizard"));
    long handler = world.snapshot().objects().size();
    long loginPlayer = handler + 1;
    runtime.executeLine(
        primaryConnection,
        """
        ; add_property(#0, "audit_disconnected_player", {}, {#0, "rw"});
        add_property(#0, "audit_disconnected_seen", {}, {#0, "rw"});
        handler = create($nothing);
        login_player = create($nothing);
        set_player_flag(login_player, 1);
        #0.audit_disconnected_player = login_player;
        add_verb(handler, {player, "rxd", "do_login_command"}, {"this", "none", "this"});
        set_verb_code(handler, "do_login_command", {"return #0.audit_disconnected_player;"});
        add_verb(handler, {player, "rxd", "user_client_disconnected"}, {"this", "none", "this"});
        set_verb_code(handler, "user_client_disconnected", {
          "connection_info_succeeds = 1;",
          "try",
          "  connection_info(player);",
          "except (E_INVARG)",
          "  connection_info_succeeds = 0;",
          "endtry",
          "#0.audit_disconnected_seen = {@#0.audit_disconnected_seen, {this, player, caller, args, argstr, connection_info_succeeds}};",
          "return 1;"
        });
        return 1;
        """);
    assertTrue(readObject(world, handler).isPresent());
    assertTrue(readObject(world, loginPlayer).isPresent());

    long dynamicConnection = -48;
    assertEquals(List.of(), runtime.openConnection(dynamicConnection, handler, false));
    assertEquals(loginPlayer, runtime.connectionPlayer(dynamicConnection).orElseThrow());

    runtime.closeConnection(dynamicConnection);

    assertTrue(runtime.connectionPlayer(dynamicConnection).isEmpty());
    assertEquals(
        "{{#" + handler + ", #" + loginPlayer + ", #-1, {#" + loginPlayer + "}, \"\", 0}}",
        readProperty(world, 0, "audit_disconnected_seen").orElseThrow().value().toLiteral());
  }

  @Test
  void callsOldDisconnectedThenNewConnectedForCrossListenerReturnedPlayerLogin() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long primaryConnection = -47;

    assertEquals(List.of(), runtime.openConnection(primaryConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(primaryConnection, "connect Wizard"));
    runtime.executeLine(
        primaryConnection,
        """
        ; for prop in ({"audit_cross_player", "audit_cross_old_handler", "audit_cross_new_handler", "audit_cross_order", "audit_cross_old_client", "audit_cross_new_connected", "audit_cross_new_reconnected"})
          try
            add_property(#0, prop, {}, {#0, "rw"});
          except (E_INVARG)
          endtry
        endfor
        old_handler = create($nothing);
        new_handler = create($nothing);
        login_player = create($nothing);
        set_player_flag(login_player, 1);
        #0.audit_cross_player = login_player;
        #0.audit_cross_old_handler = old_handler;
        #0.audit_cross_new_handler = new_handler;
        #0.audit_cross_order = {};
        #0.audit_cross_new_reconnected = {};
        add_verb(old_handler, {player, "rxd", "do_login_command"}, {"this", "none", "this"});
        set_verb_code(old_handler, "do_login_command", {"return #0.audit_cross_player;"});
        add_verb(old_handler, {player, "rxd", "user_client_disconnected"}, {"this", "none", "this"});
        set_verb_code(old_handler, "user_client_disconnected", {
          "connection_info_succeeds = 1;",
          "try",
          "  connection_info(args[1]);",
          "except (E_INVARG)",
          "  connection_info_succeeds = 0;",
          "endtry",
          "#0.audit_cross_old_client = {@#0.audit_cross_old_client, {this, player, caller, args, argstr, connection_info_succeeds}};",
          "#0.audit_cross_order = {@#0.audit_cross_order, \\\"old_client\\\"};",
          "return 1;"
        });
        add_verb(new_handler, {player, "rxd", "do_login_command"}, {"this", "none", "this"});
        set_verb_code(new_handler, "do_login_command", {"return #0.audit_cross_player;"});
        add_verb(new_handler, {player, "rxd", "user_connected"}, {"this", "none", "this"});
        set_verb_code(new_handler, "user_connected", {
          "info = connection_info(args[1]);",
          "#0.audit_cross_new_connected = {@#0.audit_cross_new_connected, {this, player, caller, args, argstr, info[\\\"source_port\\\"]}};",
          "#0.audit_cross_order = {@#0.audit_cross_order, \\\"new_connected\\\"};",
          "return 1;"
        });
        add_verb(new_handler, {player, "rxd", "user_reconnected"}, {"this", "none", "this"});
        set_verb_code(new_handler, "user_reconnected", {
          "#0.audit_cross_new_reconnected = args[1];",
          "return 1;"
        });
        return 1;
        """);
    long oldHandler =
        ((ObjectValue) readProperty(world, 0, "audit_cross_old_handler").orElseThrow().value()).value();
    long newHandler =
        ((ObjectValue) readProperty(world, 0, "audit_cross_new_handler").orElseThrow().value()).value();
    long loginPlayer =
        ((ObjectValue) readProperty(world, 0, "audit_cross_player").orElseThrow().value()).value();
    assertTrue(readObject(world, oldHandler).isPresent());
    assertTrue(readObject(world, newHandler).isPresent());
    assertTrue(readObject(world, loginPlayer).isPresent());

    StringValue sourcePortKey =
        new StringValue("source_port".getBytes(StandardCharsets.ISO_8859_1));
    int oldSourcePort = 41001;
    int newSourcePort = 41002;
    MapValue oldConnectionInfo =
        new MapValue(Map.of(sourcePortKey, new IntegerValue(oldSourcePort)));
    MapValue newConnectionInfo =
        new MapValue(Map.of(sourcePortKey, new IntegerValue(newSourcePort)));
    long oldConnection = -48;
    long newConnection = -49;

    assertEquals(
        List.of(), runtime.openConnection(oldConnection, oldHandler, false, oldConnectionInfo));
    assertEquals(
        List.of(), runtime.openConnection(newConnection, newHandler, false, newConnectionInfo));

    assertEquals(
        "{{#" + oldHandler + ", #" + loginPlayer + ", #-1, {#" + loginPlayer + "}, \"\", 0}}",
        readProperty(world, 0, "audit_cross_old_client").orElseThrow().value().toLiteral());
    assertEquals(
        "{{#"
            + newHandler
            + ", #"
            + loginPlayer
            + ", #-1, {#"
            + loginPlayer
            + "}, \"\", "
            + newSourcePort
            + "}}",
        readProperty(world, 0, "audit_cross_new_connected").orElseThrow().value().toLiteral());
    assertEquals(
        "{\"old_client\", \"new_connected\"}",
        readProperty(world, 0, "audit_cross_order").orElseThrow().value().toLiteral());
    assertEquals(
        "{}", readProperty(world, 0, "audit_cross_new_reconnected").orElseThrow().value().toLiteral());
    assertEquals(
        new IntegerValue(newSourcePort),
        runtime.connectionInfo(loginPlayer).orElseThrow().get(sourcePortKey).orElseThrow());
  }

  @Test
  void timesOutAnIdleUnauthenticatedConnectionOnceAfterActivityReset() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long primaryConnection = -47;

    assertEquals(List.of(), runtime.openConnection(primaryConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(primaryConnection, "connect Wizard"));
    runtime.executeLine(
        primaryConnection,
        """
        ; add_property(#0, "audit_timeout_seen", {}, {#0, "rw"});
        try
          add_property(#6, "connect_timeout", 1, {#0, "rw"});
        except (E_INVARG)
          #6.connect_timeout = 1;
        endtry
        try
          add_verb(#0, {player, "rxd", "user_disconnected"}, {"this", "none", "this"});
        except (E_INVARG)
        endtry
        set_verb_info(#0, "user_disconnected", {player, "rxd", "user_disconnected"});
        set_verb_args(#0, "user_disconnected", {"this", "none", "this"});
        set_verb_code(#0, "user_disconnected", {
          "#0.audit_timeout_seen = {@#0.audit_timeout_seen, {this, player, caller, args, argstr}};",
          "return 1;"
        });
        return 1;
        """);

    long timedConnection = -48;
    assertEquals(List.of(), runtime.openConnection(timedConnection, 0, false));
    Thread.sleep(1200);
    assertEquals("{}", readProperty(world, 0, "audit_timeout_seen").orElseThrow().value().toLiteral());

    assertEquals(List.of(), runtime.executeLine(timedConnection, "activity reset"));
    Thread.sleep(1200);
    assertEquals("{}", readProperty(world, 0, "audit_timeout_seen").orElseThrow().value().toLiteral());

    Thread.sleep(1200);
    String expectedFrame =
        "{{#0, #" + timedConnection + ", #-1, {#" + timedConnection + "}, \"\"}}";
    assertEquals(
        expectedFrame, readProperty(world, 0, "audit_timeout_seen").orElseThrow().value().toLiteral());
    assertTrue(runtime.connectionPlayer(timedConnection).isEmpty());

    Thread.sleep(1200);
    assertEquals(
        expectedFrame, readProperty(world, 0, "audit_timeout_seen").orElseThrow().value().toLiteral());
  }

  @Test
  void flushesHeldPendingInputWithExactFifoFeedbackBeforeRelease() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long primaryConnection = -47;

    assertEquals(List.of(), runtime.openConnection(primaryConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(primaryConnection, "connect Wizard"));
    runtime.executeLine(
        primaryConnection,
        """
        ; add_property(#0, "audit_flush_player", {}, {#0, "rw"});
        add_property(#0, "audit_flush_seen", {}, {#0, "rw"});
        login_player = create($nothing);
        set_player_flag(login_player, 1);
        #0.audit_flush_player = login_player;
        add_verb(login_player, {player, "rxd", "auditflush"}, {"none", "none", "none"});
        set_verb_code(login_player, "auditflush", {
          "#0.audit_flush_seen = {@#0.audit_flush_seen, argstr};",
          "return 1;"
        });
        try
          add_verb(#0, {player, "rxd", "do_login_command"}, {"this", "none", "this"});
        except (E_INVARG)
        endtry
        set_verb_info(#0, "do_login_command", {player, "rxd", "do_login_command"});
        set_verb_args(#0, "do_login_command", {"this", "none", "this"});
        set_verb_code(#0, "do_login_command", {"return #0.audit_flush_player;"});
        return 1;
        """);

    long heldConnection = -48;
    assertEquals(List.of(), runtime.openConnection(heldConnection, 0, false));
    runtime.executeLine(
        primaryConnection,
        "; set_connection_option(#0.audit_flush_player, \"hold-input\", 1);"
            + " set_connection_option(#0.audit_flush_player, \"flush-command\", \".flush\");"
            + " return 1;");

    assertEquals(List.of(), runtime.executeLine(heldConnection, "auditflush first"));
    assertEquals(List.of(), runtime.executeLine(heldConnection, "auditflush second"));
    assertEquals(
        List.of(
            ">> Flushing the following pending input:",
            ">>     auditflush first",
            ">>     auditflush second",
            ">> (Done flushing)"),
        runtime.executeLine(heldConnection, ".FlUsH"));

    runtime.executeLine(
        primaryConnection,
        "; set_connection_option(#0.audit_flush_player, \"hold-input\", 0); return 1;");
    assertEquals(List.of(), runtime.executeLine(heldConnection, "auditflush"));
    assertEquals("{\"\"}", readProperty(world, 0, "audit_flush_seen").orElseThrow().value().toLiteral());
  }

  @Test
  void holdsForcedInputAndReleasesDisabledOutOfBandInputAsInBand() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long primaryConnection = -47;

    assertEquals(List.of(), runtime.openConnection(primaryConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(primaryConnection, "connect Wizard"));
    runtime.executeLine(
        primaryConnection,
        """
        ; for prop in ({"audit_queue_seen", "audit_queue_oob", "audit_queue_player", "audit_queue_login_seen", "audit_queue_result"})
            add_property(#0, prop, {}, {#0, "rw"});
          endfor
          login_player = create($nothing);
          set_player_flag(login_player, 1);
          #0.audit_queue_player = login_player;
          add_verb(login_player, {#0, "rxd", "auditq"}, {"none", "none", "none"});
          set_verb_code(login_player, "auditq", {
            "#0.audit_queue_seen = listappend(#0.audit_queue_seen, argstr);",
            "return 1;"
          });
          try
            add_verb(#0, {#0, "rxd", "do_login_command"}, {"this", "none", "this"});
          except (E_INVARG)
          endtry
          set_verb_info(#0, "do_login_command", {#0, "rxd", "do_login_command"});
          set_verb_args(#0, "do_login_command", {"this", "none", "this"});
          set_verb_code(#0, "do_login_command", {"return #0.audit_queue_player;"});
          try
            add_verb(#0, {#0, "rxd", "do_out_of_band_command"}, {"this", "none", "this"});
          except (E_INVARG)
          endtry
          set_verb_info(#0, "do_out_of_band_command", {#0, "rxd", "do_out_of_band_command"});
          set_verb_args(#0, "do_out_of_band_command", {"this", "none", "this"});
          set_verb_code(#0, "do_out_of_band_command", {
            "#0.audit_queue_oob = listappend(#0.audit_queue_oob, argstr);",
            "return 1;"
          });
          return 1;
        """);

    long forcedConnection = -48;
    assertEquals(List.of(), runtime.openConnection(forcedConnection, 0, false));
    runtime.executeLine(
        primaryConnection,
        """
        ; try
            force_input(#-48, "audit-queue-login");
          except (E_VERBNF)
            #0.audit_queue_result = E_VERBNF;
            return 0;
          endtry
          suspend(0);
          #0.audit_queue_login_seen = (#0.audit_queue_player in connected_players(1)) > 0;
          p = #0.audit_queue_player;

          #0.audit_queue_seen = {};
          set_connection_option(p, "hold-input", 1);
          force_input(p, "auditq");
          suspend(0);
          held_blocked = (#0.audit_queue_seen == {});
          set_connection_option(p, "hold-input", 0);
          suspend(0);
          held_released = (#0.audit_queue_seen == {""});

          #0.audit_queue_oob = {};
          set_connection_option(p, "hold-input", 1);
          force_input(p, "#$#audit-oob-free");
          suspend(0);
          oob_bypassed_hold = (#0.audit_queue_oob == {"#$#audit-oob-free"});

          #0.audit_queue_oob = {};
          set_connection_option(p, "disable-oob", 1);
          force_input(p, "#$#audit-oob-held");
          suspend(0);
          disabled_oob_blocked = (#0.audit_queue_oob == {});
          set_connection_option(p, "hold-input", 0);
          suspend(0);
          disabled_oob_released_as_oob = (#0.audit_queue_oob != {});
          #0.audit_queue_result = {held_blocked, held_released, oob_bypassed_hold, disabled_oob_blocked, disabled_oob_released_as_oob};
          return 1;
        """);

    assertEquals(
        "{1, 1, 1, 1, 0}",
        readProperty(world, 0, "audit_queue_result").orElseThrow().value().toLiteral());
    assertEquals(
        "1", readProperty(world, 0, "audit_queue_login_seen").orElseThrow().value().toLiteral());
  }

  @Test
  void runsZeroDelayUserConnectedForkAfterParentWithCapturedLocals() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long primaryConnection = -47;

    assertEquals(List.of(), runtime.openConnection(primaryConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(primaryConnection, "connect Wizard"));
    long handler = world.snapshot().objects().size();
    long loginPlayer = handler + 1;
    runtime.executeLine(
        primaryConnection,
        """
        ; for prop in ({"audit_connected_fork_player", "audit_connected_fork_child", "audit_connected_fork_parent", "audit_connected_fork_order", "audit_connected_fork_marker"})
          try
            add_property(#0, prop, {}, {#0, "rw"});
          except (E_INVARG)
          endtry
        endfor
        handler = create($nothing);
        login_player = create($nothing);
        set_player_flag(login_player, 1);
        #0.audit_connected_fork_player = login_player;
        #0.audit_connected_fork_order = {};
        add_verb(handler, {player, "rxd", "do_login_command"}, {"this", "none", "this"});
        set_verb_code(handler, "do_login_command", {"return #0.audit_connected_fork_player;"});
        add_verb(handler, {player, "rxd", "user_connected"}, {"this", "none", "this"});
        set_verb_code(handler, "user_connected", {
          "marker = \\\"before\\\";",
          "fork (0)",
          "  #0.audit_connected_fork_child = args[1];",
          "  #0.audit_connected_fork_order = {@#0.audit_connected_fork_order, \\\"child\\\"};",
          "  #0.audit_connected_fork_marker = marker;",
          "endfork",
          "marker = \\\"after\\\";",
          "#0.audit_connected_fork_parent = args[1];",
          "#0.audit_connected_fork_order = {@#0.audit_connected_fork_order, \\\"parent\\\"};",
          "return 1;"
        });
        return 1;
        """);
    assertTrue(readObject(world, handler).isPresent());
    assertTrue(readObject(world, loginPlayer).isPresent());

    long dynamicConnection = -48;
    try {
      assertEquals(List.of(), runtime.openConnection(dynamicConnection, handler, false));
      assertEquals(
          "{#" + loginPlayer + ", #" + loginPlayer + ", {\"parent\", \"child\"}, \"before\"}",
          new ListValue(
                  List.of(
                      readProperty(world, 0, "audit_connected_fork_parent").orElseThrow().value(),
                      readProperty(world, 0, "audit_connected_fork_child").orElseThrow().value(),
                      readProperty(world, 0, "audit_connected_fork_order").orElseThrow().value(),
                      readProperty(world, 0, "audit_connected_fork_marker").orElseThrow().value()))
              .toLiteral());
    } finally {
      runtime.closeConnection(dynamicConnection);
    }
  }

  @Test
  void startsForkedTaskWithEmptyTaskLocalState() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    try {
      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, []}", CONNECTION_SUFFIX),
          runtime.executeLine(
              connectionId,
              """
              ; try
                add_property(#0, "audit_task_local_value", {}, {#0, "rw"});
              except (E_INVARG)
              endtry
              set_task_local({"parent", 7});
              fork (0)
                suspend(0);
                #0.audit_task_local_value = task_local();
              endfork
              suspend(0);
              suspend(0);
              return #0.audit_task_local_value;
              """));
      assertEquals(
          "[]", readProperty(world, 0, "audit_task_local_value").orElseThrow().value().toLiteral());
    } finally {
      runtime.executeLine(
          connectionId, "; return delete_property(#0, \"audit_task_local_value\");");
    }
  }

  @Test
  void startsForkedTaskWithTheBackgroundTickBudget() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    try {
      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, 29999}", CONNECTION_SUFFIX),
          runtime.executeLine(
              connectionId,
              """
              ; try
                add_property(#0, "audit_background_ticks", 0, {#0, "rw"});
              except (E_INVARG)
              endtry
              fork (0)
                #0.audit_background_ticks = ticks_left();
              endfork
              suspend(0);
              return #0.audit_background_ticks;
              """));
      assertEquals(
          "29999", readProperty(world, 0, "audit_background_ticks").orElseThrow().value().toLiteral());
    } finally {
      runtime.executeLine(
          connectionId, "; return delete_property(#0, " + "\"audit_background_ticks\");");
    }
  }

  @Test
  void startsNewForegroundTaskWithTheConfiguredTickBudget() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; try
              add_property(#6, "fg_ticks", 12345, {player, "r"});
            except (E_INVARG)
              #6.fg_ticks = 12345;
            endtry
            return 1;
            """));
    assertEquals(new IntegerValue(12_345), readProperty(world, 6, "fg_ticks").orElseThrow().value());

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId, "; return ticks_left() <= 12345 && ticks_left() > 12000;"));
  }

  @Test
  void startsNewForegroundTaskWithTheConfiguredSecondsLimit() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; try
              add_property(#6, "fg_seconds", 12, {player, "r"});
            except (E_INVARG)
              #6.fg_seconds = 12;
            endtry
            return 1;
            """));
    assertEquals(new IntegerValue(12), readProperty(world, 6, "fg_seconds").orElseThrow().value());

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return seconds_left() <= 12 && seconds_left() > 8;"));
  }

  @Test
  void appliesTheConfiguredMaximumStackDepthToNewForegroundTasks() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; try
              add_property(#6, "max_stack_depth", 80, {player, "r"});
            except (E_INVARG)
              #6.max_stack_depth = 80;
            endtry
            try
              add_property(#0, "audit_max_stack_object", #-1, {#0, "rw"});
            except (E_INVARG)
            endtry
            object = create($nothing);
            #0.audit_max_stack_object = object;
            add_verb(object, {player, "rxd", "audit_depth"}, {"this", "none", "this"});
            set_verb_code(object, "audit_depth", {
              "if (args[1] <= 0)",
              "  return 1;",
              "endif",
              "return 1 + this:audit_depth(args[1] - 1);"
            });
            return 1;
            """));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId, "; return #0.audit_max_stack_object:audit_depth(60) == 61;"));
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; try #0.audit_max_stack_object:audit_depth(90); return 0; "
                + "except (E_MAXREC) return 1; endtry;"));
  }

  @Test
  void startsNewBackgroundTaskWithTheConfiguredTickBudget() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; try
              add_property(#6, "bg_ticks", 23456, {player, "r"});
            except (E_INVARG)
              #6.bg_ticks = 23456;
            endtry
            try
              add_property(#0, "audit_bg_ticks", 0, {#0, "rw"});
            except (E_INVARG)
            endtry
            return 1;
            """));
    assertEquals(new IntegerValue(23_456), readProperty(world, 6, "bg_ticks").orElseThrow().value());
    assertEquals(new IntegerValue(0), readProperty(world, 0, "audit_bg_ticks").orElseThrow().value());

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; fork (0)
              #0.audit_bg_ticks = ticks_left();
            endfork
            suspend(0);
            return #0.audit_bg_ticks <= 23456 && #0.audit_bg_ticks > 23000;
            """));
  }

  @Test
  void samplesBackgroundTickBudgetAfterParentMutationBeforeYield() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; try
              add_property(#6, "bg_ticks", 23456, {player, "r"});
            except (E_INVARG)
              #6.bg_ticks = 23456;
            endtry
            try
              add_property(#0, "audit_bg_ticks", 0, {#0, "rw"});
            except (E_INVARG)
            endtry
            return 1;
            """));
    assertEquals(new IntegerValue(23_456), readProperty(world, 6, "bg_ticks").orElseThrow().value());
    assertEquals(new IntegerValue(0), readProperty(world, 0, "audit_bg_ticks").orElseThrow().value());

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; fork (0)
              #0.audit_bg_ticks = ticks_left();
            endfork
            #6.bg_ticks = 12345;
            suspend(0);
            return #0.audit_bg_ticks <= 12345 && #0.audit_bg_ticks > 12000;
            """));
  }

  @Test
  void samplesBackgroundTickBudgetWhenDelayedChildStarts() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; try
              add_property(#6, "bg_ticks", 23456, {player, "r"});
            except (E_INVARG)
              #6.bg_ticks = 23456;
            endtry
            try
              add_property(#0, "audit_bg_ticks", 0, {#0, "rw"});
            except (E_INVARG)
            endtry
            return 1;
            """));
    assertEquals(new IntegerValue(23_456), readProperty(world, 6, "bg_ticks").orElseThrow().value());
    assertEquals(new IntegerValue(0), readProperty(world, 0, "audit_bg_ticks").orElseThrow().value());

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; fork (1)
              #0.audit_bg_ticks = ticks_left();
            endfork
            suspend(0);
            #6.bg_ticks = 12345;
            suspend(2);
            return #0.audit_bg_ticks <= 12345 && #0.audit_bg_ticks > 12000;
            """));
  }

  @Test
  void startsNewBackgroundTaskWithTheConfiguredSecondsLimit() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; try
              add_property(#6, "bg_seconds", 9, {player, "r"});
            except (E_INVARG)
              #6.bg_seconds = 9;
            endtry
            try
              add_property(#0, "audit_bg_seconds", 0, {#0, "rw"});
            except (E_INVARG)
            endtry
            return 1;
            """));
    assertEquals(new IntegerValue(9), readProperty(world, 6, "bg_seconds").orElseThrow().value());
    assertEquals(new IntegerValue(0), readProperty(world, 0, "audit_bg_seconds").orElseThrow().value());

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; fork (0)
              #0.audit_bg_seconds = seconds_left();
            endfork
            suspend(0);
            return #0.audit_bg_seconds <= 9 && #0.audit_bg_seconds > 5;
            """));
  }

  @Test
  void samplesBackgroundSecondsLimitAfterParentMutationBeforeYield() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; try
              add_property(#6, "bg_seconds", 9, {player, "r"});
            except (E_INVARG)
              #6.bg_seconds = 9;
            endtry
            try
              add_property(#0, "audit_bg_seconds", 0, {#0, "rw"});
            except (E_INVARG)
            endtry
            return 1;
            """));
    assertEquals(new IntegerValue(9), readProperty(world, 6, "bg_seconds").orElseThrow().value());
    assertEquals(new IntegerValue(0), readProperty(world, 0, "audit_bg_seconds").orElseThrow().value());

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; fork (0)
              #0.audit_bg_seconds = seconds_left();
            endfork
            #6.bg_seconds = 7;
            suspend(0);
            return #0.audit_bg_seconds <= 7 && #0.audit_bg_seconds > 3;
            """));
  }

  @Test
  void samplesBackgroundSecondsLimitWhenDelayedChildStarts() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; try
              add_property(#6, "bg_seconds", 9, {player, "r"});
            except (E_INVARG)
              #6.bg_seconds = 9;
            endtry
            try
              add_property(#0, "audit_bg_seconds", 0, {#0, "rw"});
            except (E_INVARG)
            endtry
            return 1;
            """));
    assertEquals(new IntegerValue(9), readProperty(world, 6, "bg_seconds").orElseThrow().value());
    assertEquals(new IntegerValue(0), readProperty(world, 0, "audit_bg_seconds").orElseThrow().value());

    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            """
            ; fork (1)
              #0.audit_bg_seconds = seconds_left();
            endfork
            suspend(0);
            #6.bg_seconds = 7;
            suspend(2);
            return #0.audit_bg_seconds <= 7 && #0.audit_bg_seconds > 3;
            """));
  }

  @Test
  void preservesWaifThisAndVerbLocationInCallers() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long waifSubclass = world.snapshot().objects().size();

    try {
      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, #" + waifSubclass + "}", CONNECTION_SUFFIX),
          runtime.executeLine(
              connectionId,
              """
              ; obj = create($waif);
              add_verb(obj, {player, "xd", ":audit_a"}, {"this", "none", "this"});
              set_verb_code(obj, ":audit_a", {"return callers();"});
              add_verb(obj, {player, "xd", ":audit_b"}, {"this", "none", "this"});
              set_verb_code(obj, ":audit_b", {"return this:audit_a();"});
              add_verb(obj, {player, "xd", ":audit_c"}, {"this", "none", "this"});
              set_verb_code(obj, ":audit_c", {
                "c = this:audit_b();",
                "return {{c[1][2], typeof(c[1][1]) == WAIF, c[1][4] == this.class}, {c[2][2], typeof(c[2][1]) == WAIF, c[2][4] == this.class}};"
              });
              return obj;
              """));
      assertEquals(3, readObject(world, waifSubclass).orElseThrow().verbs().size());
      assertTrue(readVerb(world, waifSubclass, "new").isPresent());

      assertEquals(
          List.of(
              CONNECTION_PREFIX,
              "{1, {{\":audit_b\", 1, 1}, {\":audit_c\", 1, 1}}}",
              CONNECTION_SUFFIX),
          runtime.executeLine(
              connectionId,
              "; obj = #"
                  + waifSubclass
                  + "; waif = obj:new();\n"
                  + "result = waif:audit_c();\n"
                  + "recycle(obj);\n"
                  + "return result;"));
    } finally {
      if (readObject(world, waifSubclass).isPresent()) {
        runtime.executeLine(connectionId, "; recycle(#" + waifSubclass + "); return 1;");
      }
    }
  }

  @Test
  void invokesDestinationAcceptWhenMoveReturnsFalse() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long thing = world.snapshot().objects().size();
    long destination = thing + 1;

    try {
      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, {1, 1}}", CONNECTION_SUFFIX),
          runtime.executeLine(
              connectionId,
              """
              ; thing = create($nothing);
              destination = create($nothing);
              add_property(destination, "accept_seen", #-1, {player, "rw"});
              add_verb(destination, {player, "xd", "accept"}, {"this", "none", "this"});
              set_verb_code(destination, "accept", {
                "this.accept_seen = args[1];",
                "return 0;"
              });
              move(thing, destination);
              return {destination.accept_seen == thing, thing.location == destination};
              """));
    } finally {
      if (readObject(world, thing).isPresent()) {
        runtime.executeLine(connectionId, "; recycle(#" + thing + "); return 1;");
      }
      if (readObject(world, destination).isPresent()) {
        runtime.executeLine(connectionId, "; recycle(#" + destination + "); return 1;");
      }
    }
  }

  @Test
  void writesIntrinsicFertileFlagAsIntegerZero() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long object = world.snapshot().objects().size();

    try {
      List<String> output =
          runtime.executeLine(
              connectionId,
              """
              ; object = create(#-1);
              return object.f = 0;
              """);

      assertEquals(0, readObject(world, object).orElseThrow().flags() & 128);
      assertEquals(List.of(CONNECTION_PREFIX, "{1, 0}", CONNECTION_SUFFIX), output);
    } finally {
      if (readObject(world, object).isPresent()) {
        runtime.executeLine(connectionId, "; recycle(#" + object + "); return 1;");
      }
    }
  }

  @Test
  void recordsDefiningObjectForInheritedRootVerbCallerFrame() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long primaryConnection = -47;

    assertEquals(List.of(), runtime.openConnection(primaryConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(primaryConnection, "connect Wizard"));
    long definingParent = world.snapshot().objects().size();
    long childHandler = definingParent + 1;
    long loginPlayer = childHandler + 1;

    runtime.executeLine(
        primaryConnection,
        """
        ; add_property(#0, "audit_root_verb_location", #-1, {#0, "rw"});
        add_property(#0, "audit_root_login_player", #-1, {#0, "rw"});
        parent = create($nothing);
        child = create(parent);
        login_player = create($nothing);
        set_player_flag(login_player, 1);
        #0.audit_root_login_player = login_player;
        add_verb(parent, {player, "rxd", "audit_root_location"}, {"this", "none", "this"});
        set_verb_code(parent, "audit_root_location", {
          "#0.audit_root_verb_location = callers()[1][4];",
          "return 1;"
        });
        add_verb(parent, {player, "rxd", "do_login_command"}, {"this", "none", "this"});
        set_verb_code(parent, "do_login_command", {
          "this:audit_root_location();",
          "return #0.audit_root_login_player;"
        });
        return 1;
        """);

    long inheritedConnection = -48;
    try {
      assertEquals(List.of(), runtime.openConnection(inheritedConnection, childHandler, false));
      assertEquals(loginPlayer, runtime.connectionPlayer(inheritedConnection).orElseThrow());
      assertEquals(
          new ObjectValue(definingParent),
          readProperty(world, 0, "audit_root_verb_location").orElseThrow().value());
    } finally {
      runtime.closeConnection(inheritedConnection);
    }
  }

  @Test
  void continuesUserConnectedConfuncsAfterForkSuspendAndTaskPermissionChange() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long primaryConnection = -47;

    assertEquals(List.of(), runtime.openConnection(primaryConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(primaryConnection, "connect Wizard"));
    long setupWizard = runtime.connectionPlayer(primaryConnection).orElseThrow();
    long handler = world.snapshot().objects().size();
    long loginPlayer = handler + 1;
    long location = loginPlayer + 1;
    runtime.executeLine(
        primaryConnection,
        """
        ; for prop in ({"audit_confunc_player", "audit_confunc_location", "audit_confunc_child", "audit_confunc_after_suspend", "audit_confunc_location_seen", "audit_confunc_user_seen", "audit_confunc_parent"})
          try
            add_property(#0, prop, {}, {#0, "rw"});
          except (E_INVARG)
          endtry
        endfor
        handler = create($nothing);
        login_player = create($nothing);
        location = create($nothing);
        set_player_flag(login_player, 1);
        move(login_player, location);
        #0.audit_confunc_player = login_player;
        #0.audit_confunc_location = location;
        add_verb(location, {login_player, "rxd", "confunc"}, {"this", "none", "this"});
        set_verb_code(location, "confunc", {
          "#0.audit_confunc_location_seen = {this, args[1], task_perms()};",
          "return 1;"
        });
        add_verb(location, {login_player, "rxd", "yield_once"}, {"this", "none", "this"});
        set_verb_code(location, "yield_once", {
          "suspend(0);",
          "#0.audit_confunc_after_suspend = this;",
          "return 1;"
        });
        add_verb(login_player, {login_player, "rxd", "confunc"}, {"this", "none", "this"});
        set_verb_code(login_player, "confunc", {
          "#0.audit_confunc_user_seen = {this, args, task_perms()};",
          "return 1;"
        });
        add_verb(handler, {player, "rxd", "do_login_command"}, {"this", "none", "this"});
        set_verb_code(handler, "do_login_command", {"return #0.audit_confunc_player;"});
        add_verb(handler, {player, "rxd", "user_connected"}, {"this", "none", "this"});
        set_verb_code(handler, "user_connected", {
          "user = args[1];",
          "fork (0)",
          "  #0.audit_confunc_child = {user, task_perms()};",
          "endfork",
          "user.location:yield_once();",
          "set_task_perms(user);",
          "user.location:confunc(user);",
          "user:confunc();",
          "#0.audit_confunc_parent = {user, task_perms()};",
          "return 1;"
        });
        return 1;
        """);
    assertTrue(readObject(world, handler).isPresent());
    assertTrue(readObject(world, loginPlayer).isPresent());
    assertTrue(readObject(world, location).isPresent());

    long dynamicConnection = -48;
    try {
      assertEquals(List.of(), runtime.openConnection(dynamicConnection, handler, false));
      assertEquals(
          "{{#"
              + loginPlayer
              + ", #"
              + setupWizard
              + "}, #"
              + location
              + ", {#"
              + location
              + ", #"
              + loginPlayer
              + ", #"
              + loginPlayer
              + "}, {#"
              + loginPlayer
              + ", {}, #"
              + loginPlayer
              + "}, {#"
              + loginPlayer
              + ", #"
              + loginPlayer
              + "}}",
          new ListValue(
                  List.of(
                      readProperty(world, 0, "audit_confunc_child").orElseThrow().value(),
                      readProperty(world, 0, "audit_confunc_after_suspend").orElseThrow().value(),
                      readProperty(world, 0, "audit_confunc_location_seen").orElseThrow().value(),
                      readProperty(world, 0, "audit_confunc_user_seen").orElseThrow().value(),
                      readProperty(world, 0, "audit_confunc_parent").orElseThrow().value()))
              .toLiteral());
    } finally {
      runtime.closeConnection(dynamicConnection);
    }
  }

  @Test
  void dispatchesTrustedEmptyInputToListenerHandlerBlankVerb() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long primaryConnection = -47;

    assertEquals(List.of(), runtime.openConnection(primaryConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(primaryConnection, "connect Wizard"));
    long handler = world.snapshot().objects().size();
    runtime.executeLine(
        primaryConnection,
        """
        ; add_property(#0, "audit_blank_handler", {}, {#0, "rw"});
        add_property(#0, "audit_blank_seen", {}, {#0, "rw"});
        try
          add_property(#6, "trusted_proxies", {}, {#0, "rw"});
        except (E_INVARG)
        endtry
        #6.trusted_proxies = {"127.0.0.1"};
        handler = create($nothing);
        #0.audit_blank_handler = handler;
        add_verb(handler, {player, "rxd", "do_blank_command"}, {"this", "none", "none"});
        set_verb_code(handler, "do_blank_command", {
          "#0.audit_blank_seen = {this, player, caller, args, argstr};",
          "return 0;"
        });
        return 1;
        """);
    assertEquals(new ObjectValue(6), readProperty(world, 0, "server_options").orElseThrow().value());

    StringValue destinationIp = new StringValue("127.0.0.1".getBytes(StandardCharsets.ISO_8859_1));
    MapValue connectionInfo =
        new MapValue(
            Map.of(
                new StringValue("destination_ip".getBytes(StandardCharsets.ISO_8859_1)),
                destinationIp));
    long dynamicConnection = -48;
    try {
      assertEquals(
          List.of(), runtime.openConnection(dynamicConnection, handler, false, connectionInfo));
      assertEquals(List.of(), runtime.executeLine(dynamicConnection, ""));
      assertEquals(
          "{#" + handler + ", #-48, #-48, {}, \"\"}",
          readProperty(world, 0, "audit_blank_seen").orElseThrow().value().toLiteral());
    } finally {
      runtime.closeConnection(dynamicConnection);
    }
  }

  @Test
  void clearsCanonicalTrustedProxyPreludeBeforeLoginDispatch() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long primaryConnection = -47;

    assertEquals(List.of(), runtime.openConnection(primaryConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(primaryConnection, "connect Wizard"));
    runtime.executeLine(
        primaryConnection,
        """
        ; add_property(#0, "audit_proxy_seen", {}, {#0, "rw"});
        try
          add_property(#6, "trusted_proxies", {}, {#0, "rw"});
        except (E_INVARG)
        endtry
        #6.trusted_proxies = {"127.0.0.1"};
        try
          add_verb(#0, {player, "rxd", "do_login_command"}, {"this", "none", "this"});
        except (E_INVARG)
        endtry
        set_verb_info(#0, "do_login_command", {player, "rxd", "do_login_command"});
        set_verb_args(#0, "do_login_command", {"this", "none", "this"});
        set_verb_code(#0, "do_login_command", {
          "#0.audit_proxy_seen = {args, argstr};",
          "return 0;"
        });
        return 1;
        """);

    StringValue destinationIp = new StringValue("127.0.0.1".getBytes(StandardCharsets.ISO_8859_1));
    MapValue connectionInfo =
        new MapValue(
            Map.of(
                new StringValue("destination_ip".getBytes(StandardCharsets.ISO_8859_1)),
                destinationIp));
    long dynamicConnection = -48;
    try {
      assertEquals(List.of(), runtime.openConnection(dynamicConnection, 0, false, connectionInfo));
      assertEquals(
          List.of(),
          runtime.executeLine(dynamicConnection, "PROXY TCP4 127.0.0.1 198.51.100.9 4242 7777"));
      assertEquals(
          "{{}, \"\"}", readProperty(world, 0, "audit_proxy_seen").orElseThrow().value().toLiteral());
    } finally {
      runtime.closeConnection(dynamicConnection);
    }
  }

  @Test
  void tokenizesEscapedLoginWordsWhilePreservingOriginalArgstr() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long primaryConnection = -47;

    assertEquals(List.of(), runtime.openConnection(primaryConnection));
    assertEquals(
        List.of("*** Connected ***"), runtime.executeLine(primaryConnection, "connect Wizard"));
    long handler = world.snapshot().objects().size();
    runtime.executeLine(
        primaryConnection,
        """
        ; add_property(#0, "audit_login_words", {}, {#0, "rw"});
        handler = create($nothing);
        add_verb(handler, {player, "rxd", "do_login_command"}, {"this", "none", "this"});
        set_verb_code(handler, "do_login_command", {
          "#0.audit_login_words = {args, argstr};",
          "return 0;"
        });
        return handler;
        """);
    assertTrue(readObject(world, handler).isPresent());

    long dynamicConnection = -48;
    try {
      assertEquals(List.of(), runtime.openConnection(dynamicConnection, handler, false));
      assertEquals(List.of(), runtime.executeLine(dynamicConnection, "auditlogin foo\\ bar  baz"));
      assertEquals(
          "{{\"auditlogin\", \"foo bar\", \"baz\"}, \"auditlogin foo\\\\ bar  baz\"}",
          readProperty(world, 0, "audit_login_words").orElseThrow().value().toLiteral());
    } finally {
      runtime.closeConnection(dynamicConnection);
      runtime.executeLine(
          primaryConnection,
          "; delete_property(#0, \"audit_login_words\"); recycle(#" + handler + "); return 1;");
    }
  }

  @Test
  void tokenizesBackslashEscapesForAStoredPlayerCommandVerb() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; add_verb(player, {player, "xd", "audit_words"}, {"any", "none", "none"});
            set_verb_code(player, "audit_words", {
              "notify(player, \\"LEN:\\" + tostr(length(args)));",
              "notify(player, \\"ARG1:\\" + args[1]);",
              "notify(player, \\"ARG2:\\" + args[2]);"
            });
            return 1;
            """);
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    assertTrue(
        readVerb(world, player, "audit_words").isPresent(),
        () -> setupOutput + " player=" + player + " object=" + readObject(world, player));
    assertTrue(
        !readVerb(world, player, "audit_words").orElseThrow().programSource().isEmpty(),
        setupOutput::toString);

    try {
      assertEquals(
          List.of("LEN:2", "ARG1:foo bar", "ARG2:baz"),
          runtime.executeLine(connectionId, "audit_words foo\\ bar baz"));
    } finally {
      runtime.executeLine(connectionId, "; return delete_verb(player, \"audit_words\");");
    }
  }

  @Test
  void tokenizesMidwordQuotesForAStoredPlayerCommandVerb() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; add_verb(player, {player, "xd", "audit_words"}, {"any", "none", "none"});
            set_verb_code(player, "audit_words", {
              "notify(player, \\"LEN:\\" + tostr(length(args)));",
              "notify(player, \\"ARG1:\\" + args[1]);",
              "notify(player, \\"ARG2:\\" + args[2]);"
            });
            return 1;
            """);
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    assertTrue(readVerb(world, player, "audit_words").isPresent(), setupOutput::toString);

    try {
      assertEquals(
          List.of("LEN:2", "ARG1:abc def", "ARG2:zz"),
          runtime.executeLine(connectionId, "audit_words ab\"c d\"ef zz"));
    } finally {
      runtime.executeLine(connectionId, "; return delete_verb(player, \"audit_words\");");
    }
  }

  @Test
  void usesTheLeftmostPrepositionForAStoredPlayerCommandVerb() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; add_verb(player, {player, "xd", "auditprep"}, {"any", "any", "any"});
            set_verb_code(player, "auditprep", {
              "notify(player, \\"DOBJSTR:\\" + dobjstr);",
              "notify(player, \\"PREPSTR:\\" + prepstr);",
              "notify(player, \\"IOBJSTR:\\" + iobjstr);"
            });
            return 1;
            """);
    assertTrue(
        readVerb(world, runtime.connectionPlayer(connectionId).orElseThrow(), "auditprep").isPresent(),
        setupOutput::toString);

    try {
      assertEquals(
          List.of("DOBJSTR:book", "PREPSTR:out of", "IOBJSTR:bag in front of chair"),
          runtime.executeLine(connectionId, "auditprep book out of bag in front of chair"));
    } finally {
      runtime.executeLine(connectionId, "; return delete_verb(player, \"auditprep\");");
    }
  }

  @Test
  void keepsNegativeObjectLiteralsAsFailedMatchesForStoredPlayerCommands() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; add_verb(player, {player, "xd", "auditgrab"}, {"any", "none", "none"});
            set_verb_code(player, "auditgrab", {
              "notify(player, \\"DOBJ:\\" + toliteral(dobj));"
            });
            return 1;
            """);
    assertTrue(
        readVerb(world, runtime.connectionPlayer(connectionId).orElseThrow(), "auditgrab").isPresent(),
        setupOutput::toString);

    try {
      assertEquals(List.of("DOBJ:#-3"), runtime.executeLine(connectionId, "auditgrab #-1"));
      assertEquals(List.of("DOBJ:#-3"), runtime.executeLine(connectionId, "auditgrab #-2"));
      assertEquals(List.of("DOBJ:#-3"), runtime.executeLine(connectionId, "auditgrab #-3"));
    } finally {
      runtime.executeLine(connectionId, "; return delete_verb(player, \"auditgrab\");");
    }
  }

  @Test
  void poolsExactObjectNamesAndAliasesForStoredPlayerCommands() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; first = create($nothing);
            first.name = "auditexact";
            add_property(first, "aliases", {}, {player, "rw"});
            move(first, player);
            second = create($nothing);
            second.name = "other audit exact";
            add_property(second, "aliases", {"auditexact"}, {player, "rw"});
            move(second, player);
            add_verb(player, {player, "xd", "auditlook"}, {"any", "none", "none"});
            set_verb_code(player, "auditlook", {
              "notify(player, \\"DOBJ:\\" + toliteral(dobj));"
            });
            return {first, second};
            """);
    List<Long> inventory = readObject(world, player).orElseThrow().contents();
    assertEquals(2, inventory.size(), setupOutput::toString);
    long first = inventory.get(0);
    long second = inventory.get(1);
    assertEquals("auditexact", readObject(world, first).orElseThrow().name());
    assertEquals("other audit exact", readObject(world, second).orElseThrow().name());
    assertEquals("{}", readObjectProperty(world, first, "aliases").orElseThrow().toLiteral());
    assertEquals(
        "{\"auditexact\"}", readObjectProperty(world, second, "aliases").orElseThrow().toLiteral());
    assertEquals(player, readObject(world, first).orElseThrow().location());
    assertEquals(player, readObject(world, second).orElseThrow().location());
    assertTrue(readVerb(world, player, "auditlook").isPresent(), setupOutput::toString);

    try {
      assertEquals(List.of("DOBJ:#-2"), runtime.executeLine(connectionId, "auditlook auditexact"));
    } finally {
      runtime.executeLine(
          connectionId,
          "; delete_verb(player, \"auditlook\"); recycle(#"
              + first
              + "); recycle(#"
              + second
              + "); return 1;");
    }
  }

  @Test
  void poolsPartialObjectNamesAndAliasesForStoredPlayerCommands() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; first = create($nothing);
            first.name = "auditpartialone";
            add_property(first, "aliases", {}, {player, "rw"});
            move(first, player);
            second = create($nothing);
            second.name = "other audit partial";
            add_property(second, "aliases", {"auditpartialtwo"}, {player, "rw"});
            move(second, player);
            add_verb(player, {player, "xd", "auditlook"}, {"any", "none", "none"});
            set_verb_code(player, "auditlook", {
              "notify(player, \\"DOBJ:\\" + toliteral(dobj));"
            });
            return {first, second};
            """);
    List<Long> inventory = readObject(world, player).orElseThrow().contents();
    assertEquals(2, inventory.size(), setupOutput::toString);
    long first = inventory.get(0);
    long second = inventory.get(1);
    assertEquals("auditpartialone", readObject(world, first).orElseThrow().name());
    assertEquals("other audit partial", readObject(world, second).orElseThrow().name());
    assertEquals("{}", readObjectProperty(world, first, "aliases").orElseThrow().toLiteral());
    assertEquals(
        "{\"auditpartialtwo\"}",
        readObjectProperty(world, second, "aliases").orElseThrow().toLiteral());
    assertEquals(player, readObject(world, first).orElseThrow().location());
    assertEquals(player, readObject(world, second).orElseThrow().location());
    assertTrue(readVerb(world, player, "auditlook").isPresent(), setupOutput::toString);

    try {
      assertEquals(
          List.of("DOBJ:#-2"), runtime.executeLine(connectionId, "auditlook auditpartial"));
    } finally {
      runtime.executeLine(
          connectionId,
          "; delete_verb(player, \"auditlook\"); recycle(#"
              + first
              + "); recycle(#"
              + second
              + "); return 1;");
    }
  }

  @Test
  void matchesTheCurrentPlayerThroughRoomContentsForStoredPlayerCommands() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    String oldName = readObject(world, player).orElseThrow().name();
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; old_name = player.name;
            player.name = "auditplayerunique";
            add_property(player, "audit_old_name", old_name, {player, "rw"});
            add_verb(player, {player, "xd", "auditlook"}, {"any", "none", "none"});
            set_verb_code(player, "auditlook", {
              "notify(player, \\"ISPLAYER:\\" + tostr(dobj == player));"
            });
            return 1;
            """);
    assertEquals("auditplayerunique", readObject(world, player).orElseThrow().name());
    assertEquals(
        "\"" + oldName + "\"",
        readObjectProperty(world, player, "audit_old_name").orElseThrow().toLiteral());
    long location = readObject(world, player).orElseThrow().location();
    assertTrue(readObject(world, location).orElseThrow().contents().contains(player));
    assertTrue(readVerb(world, player, "auditlook").isPresent(), setupOutput::toString);

    try {
      assertEquals(
          List.of("ISPLAYER:1"), runtime.executeLine(connectionId, "auditlook auditplayerunique"));
    } finally {
      runtime.executeLine(
          connectionId,
          "; player.name = player.audit_old_name; "
              + "delete_property(player, \"audit_old_name\"); "
              + "delete_verb(player, \"auditlook\"); return 1;");
    }
  }

  @Test
  void deletesLocalPropertyDefinitions() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    runtime.executeLine(
        connectionId,
        "; add_property(player, \"audit_delete_local\", 1, {player, \"rw\"}); return 1;");
    assertTrue(readObjectProperty(world, player, "audit_delete_local").isPresent());

    runtime.executeLine(
        connectionId, "; delete_property(player, \"audit_delete_local\"); return 1;");

    assertTrue(readObjectProperty(world, player, "audit_delete_local").isEmpty());
    assertEquals(
        EffectClass.TRANSACTION_WRITE,
        new BuiltinCatalog().spec("delete_property").orElseThrow().effect());
  }

  @Test
  void dispatchesTextualOutOfBandInputToTheConnectionListener() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    runtime.executeLine(
        connectionId,
        "; add_property(#0, \"audit_oob_frame\", {}, {#0, \"rw\"});"
            + " add_verb(#0, {#0, \"rxd\", \"do_out_of_band_command\"}, {\"this\", \"none\", \"this\"});"
            + " set_verb_code(#0, \"do_out_of_band_command\","
            + " {\"#0.audit_oob_frame = {this, player, caller, verb, args, argstr};\"});"
            + " return 1;");

    assertEquals(List.of(), runtime.executeLine(connectionId, "#$#audit-oob alpha beta"));
    assertEquals(
        "{#0, #"
            + player
            + ", #-1, \"do_out_of_band_command\","
            + " {\"#$#audit-oob\", \"alpha\", \"beta\"},"
            + " \"#$#audit-oob alpha beta\"}",
        readObjectProperty(world, 0, "audit_oob_frame").orElseThrow().toLiteral());
  }

  @Test
  void parsesTransportOutOfBandWordsWithoutChangingArgstr() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    runtime.executeLine(
        connectionId,
        "; add_property(#0, \"audit_transport_oob_frame\", {}, {#0, \"rw\"});"
            + " add_verb(#0, {#0, \"rxd\", \"do_out_of_band_command\"}, {\"this\", \"none\", \"this\"});"
            + " set_verb_code(#0, \"do_out_of_band_command\","
            + " {\"#0.audit_transport_oob_frame = {args, argstr};\"});"
            + " return 1;");

    assertEquals(
        List.of(),
        runtime.executeTransportOutOfBand(
            connectionId, "~FF~FA~C9Core.Hello {\"client\":\"audit\"}~FF~F0"));
    assertEquals(
        "{{\"~FF~FA~C9Core.Hello\", \"{client:audit}~FF~F0\"},"
            + " \"~FF~FA~C9Core.Hello {\\\"client\\\":\\\"audit\\\"}~FF~F0\"}",
        readObjectProperty(world, 0, "audit_transport_oob_frame").orElseThrow().toLiteral());
  }

  @Test
  void passesTheFullWordListToTruthyDoCommandBeforeNormalDispatch() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; try
              add_verb(#0, {player, "rxd", "do_command"}, {"this", "none", "this"});
            except (E_INVARG)
            endtry
            set_verb_code(#0, "do_command", {
              "if (args[1] == \\";\\")",
              "  return 0;",
              "endif",
              "notify(player, \\"DO_COMMAND:\\" + tostr(length(args)) + \\":\\" + args[2]);",
              "return 1;"
            });
              add_verb(player, {player, "xd", "audit_words"}, {"any", "none", "none"});
              set_verb_code(player, "audit_words", {"notify(player, \\"NORMAL_DISPATCH\\");"});
              return 1;
            """);
    assertTrue(readVerb(world, 0, "do_command").isPresent(), setupOutput::toString);

    try {
      assertEquals(List.of(), runtime.executeLine(connectionId, "pReFiX command-prefix"));
      assertEquals(List.of(), runtime.executeLine(connectionId, "sUfFiX command-suffix"));
      assertEquals(
          List.of("command-prefix", "DO_COMMAND:2:foo bar", "command-suffix"),
          runtime.executeLine(connectionId, "audit_words foo\\ bar"));
    } finally {
      runtime.executeLine(
          connectionId,
          "; delete_verb(#0, \"do_command\"); delete_verb(player, \"audit_words\"); return 1;");
    }
  }

  @Test
  void dispatchesMatchingCommandVerbWithoutExecutePermission() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    int verbIndex;
    try (WorldTxn transaction = world.begin()) {
      int verbNumber = transaction.addVerb(player, "auditnoexec", player, 8, -1);
      assertTrue(verbNumber > 0);
      verbIndex = verbNumber - 1;
      assertTrue(
          transaction.setVerbCode(player, verbIndex, "notify(player, \"EXECUTED\");"));
      assertTrue(transaction.commit().isCommitted());
    }
    assertEquals(8, readVerb(world, player, verbIndex).orElseThrow().permissions());

    assertEquals(List.of("EXECUTED"), runtime.executeLine(connectionId, "auditnoexec"));
  }

  @Test
  void runsRoomHuhAfterStoredCommandArgspecMismatch() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    long room = readObject(world, player).orElseThrow().location();
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; room = player.location;
            add_verb(player, {player, "xd", "auditmismatch"}, {"none", "none", "none"});
            set_verb_code(player, "auditmismatch", {
              "notify(player, \\"BAD_MATCH\\");"
            });
            add_verb(room, {player, "xd", "huh"}, {"none", "none", "none"});
            set_verb_code(room, "huh", {
              "notify(player, \\"HUH:\\" + verb);",
              "notify(player, \\"ARGSTR:\\" + argstr);"
            });
            return room;
            """);
    assertEquals(List.of(CONNECTION_PREFIX, "{1, #" + room + "}", CONNECTION_SUFFIX), setupOutput);
    assertEquals(room, readObject(world, player).orElseThrow().location());
    WorldVerb commandVerb = readVerb(world, player, "auditmismatch").orElseThrow();
    assertEquals(12, commandVerb.permissions());
    assertEquals(-1, commandVerb.preposition());
    assertTrue(!commandVerb.programSource().isEmpty());
    WorldVerb huhVerb = readVerb(world, room, "huh").orElseThrow();
    assertEquals(12, huhVerb.permissions());
    assertEquals(-1, huhVerb.preposition());
    assertTrue(!huhVerb.programSource().isEmpty());

    try {
      assertEquals(
          List.of("HUH:auditmismatch", "ARGSTR:object"),
          runtime.executeLine(connectionId, "auditmismatch object"));
    } finally {
      runtime.executeLine(
          connectionId,
          "; delete_verb(player, \"auditmismatch\"); delete_verb(#"
              + room
              + ", \"huh\"); return 1;");
    }
  }

  @Test
  void reparsesTheLeadingQuoteShortcutAsSay() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; box = create($nothing);
            box.name = "auditbox";
            move(box, player);
            add_verb(player, {player, "xd", "say"}, {"any", "in", "any"});
            set_verb_code(player, "say", {
              "notify(player, \\"DOBJSTR:\\" + dobjstr);",
              "notify(player, \\"PREPSTR:\\" + prepstr);",
              "notify(player, \\"IOBJSTR:\\" + iobjstr);"
            });
            return box;
            """);
    List<Long> inventory = readObject(world, player).orElseThrow().contents();
    assertEquals(1, inventory.size(), setupOutput::toString);
    long box = inventory.getFirst();
    assertEquals(List.of(CONNECTION_PREFIX, "{1, #" + box + "}", CONNECTION_SUFFIX), setupOutput);
    assertEquals("auditbox", readObject(world, box).orElseThrow().name());
    assertEquals(player, readObject(world, box).orElseThrow().location());
    WorldVerb say = readVerb(world, player, "say").orElseThrow();
    assertEquals(92, say.permissions());
    assertEquals(3, say.preposition());
    assertTrue(!say.programSource().isEmpty());

    try {
      assertEquals(
          List.of("DOBJSTR:widget", "PREPSTR:in", "IOBJSTR:auditbox"),
          runtime.executeLine(connectionId, "\"widget in auditbox"));
    } finally {
      runtime.executeLine(
          connectionId, "; delete_verb(player, \"say\"); recycle(#" + box + "); return 1;");
    }
  }

  @Test
  void reparsesTheLeadingColonShortcutAsEmote() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; add_verb(player, {player, "xd", "emote"}, {"any", "at", "any"});
            set_verb_code(player, "emote", {
              "notify(player, \\"DOBJSTR:\\" + dobjstr);",
              "notify(player, \\"PREPSTR:\\" + prepstr);",
              "notify(player, \\"IOBJSTR:\\" + iobjstr);",
              "notify(player, \\"IOBJISPLAYER:\\" + tostr(iobj == player));"
            });
            return 1;
            """);
    assertEquals(List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX), setupOutput);
    WorldVerb emote = readVerb(world, player, "emote").orElseThrow();
    assertEquals(92, emote.permissions());
    assertEquals(1, emote.preposition());
    assertTrue(!emote.programSource().isEmpty());

    try {
      assertEquals(
          List.of("DOBJSTR:wave", "PREPSTR:at", "IOBJSTR:me", "IOBJISPLAYER:1"),
          runtime.executeLine(connectionId, ":wave at me"));
    } finally {
      runtime.executeLine(connectionId, "; return delete_verb(player, \"emote\");");
    }
  }

  @Test
  void reparsesTheLeadingSemicolonShortcutAsEval() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; box = create($nothing);
            box.name = "auditevalbox";
            move(box, player);
            add_verb(player, {player, "xd", "eval"}, {"any", "in", "any"});
            set_verb_code(player, "eval", {
              "notify(player, \\"DOBJSTR:\\" + dobjstr);",
              "notify(player, \\"PREPSTR:\\" + prepstr);",
              "notify(player, \\"IOBJSTR:\\" + iobjstr);"
            });
            return box;
            """);
    List<Long> inventory = readObject(world, player).orElseThrow().contents();
    assertEquals(1, inventory.size(), setupOutput::toString);
    long box = inventory.getFirst();
    assertEquals(List.of(CONNECTION_PREFIX, "{1, #" + box + "}", CONNECTION_SUFFIX), setupOutput);
    assertEquals("auditevalbox", readObject(world, box).orElseThrow().name());
    assertEquals(player, readObject(world, box).orElseThrow().location());
    WorldVerb eval = readVerb(world, player, "eval").orElseThrow();
    assertEquals(92, eval.permissions());
    assertEquals(3, eval.preposition());
    assertTrue(!eval.programSource().isEmpty());

    try {
      assertEquals(
          List.of("DOBJSTR:widget", "PREPSTR:in", "IOBJSTR:auditevalbox"),
          runtime.executeLine(connectionId, ";widget in auditevalbox"));
    } finally {
      runtime.executeLine(
          connectionId, "; delete_verb(player, \"eval\"); recycle(#" + box + "); return 1;");
    }
  }

  @Test
  void installsSubmittedVerbCodeThroughDotProgramIntrinsic() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    long object = world.snapshot().objects().size();
    assertTrue(readObject(world, object).isEmpty());
    List<String> setupOutput =
        runtime.executeLine(
            connectionId,
            """
            ; obj = create($nothing);
            obj.name = "audit program target";
            add_verb(obj, {player, "rxd", "auditprog"}, {"this", "none", "none"});
            return obj;
            """);
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, #" + object + "}", CONNECTION_SUFFIX), setupOutput);
    WorldObject target = readObject(world, object).orElseThrow();
    assertEquals("audit program target", target.name());
    assertEquals(-1, target.parent());
    assertEquals(player, target.owner());
    assertEquals(1, target.verbs().size());
    WorldVerb auditprog = target.verbs().getFirst();
    assertEquals("auditprog", auditprog.names());
    assertEquals(player, auditprog.owner());
    assertEquals(45, auditprog.permissions());
    assertEquals(-1, auditprog.preposition());
    assertEquals("", auditprog.programSource());

    try {
      runtime.executeLine(connectionId, ".program #" + object + ":auditprog");
      assertEquals("", readVerb(world, object, 0).orElseThrow().programSource());
      runtime.executeLine(connectionId, "return 4242;");
      assertEquals("", readVerb(world, object, 0).orElseThrow().programSource());
      runtime.executeLine(connectionId, ".");

      assertEquals(
          List.of(CONNECTION_PREFIX, "{1, 4242}", CONNECTION_SUFFIX),
          runtime.executeLine(connectionId, "; return #" + object + ":auditprog();"));
      assertEquals("return 4242;\n", readVerb(world, object, 0).orElseThrow().programSource());
    } finally {
      runtime.executeLine(connectionId, "; recycle(#" + object + "); return 1;");
    }
  }

  @Test
  void preservesPlayerCallerForAnInheritedCommand() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(
        EffectClass.TRANSACTION_READ, new BuiltinCatalog().spec("parent").orElseThrow().effect());
    assertEquals(
        EffectClass.TRANSACTION_WRITE, new BuiltinCatalog().spec("chparent").orElseThrow().effect());
    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    long oldParent = readObject(world, player).orElseThrow().parent();
    assertEquals(-1, oldParent);
    assertTrue(readObject(world, oldParent).isEmpty());
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, #" + oldParent + "}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return parent(player);"));

    long ancestor = world.snapshot().objects().size();
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, #" + ancestor + "}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return create(#" + oldParent + ");"));
    assertEquals(oldParent, readObject(world, ancestor).orElseThrow().parent());

    try {
      List<String> setupOutput =
          runtime.executeLine(
              connectionId,
              """
              ; add_verb(#%d, {player, "xd", "audit_inherited_caller"}, {"any", "any", "any"});
              set_verb_code(#%d, "audit_inherited_caller", {"notify(player, \\"CALLER_IS_PLAYER:\\" + tostr(caller == player));"});
              chparent(player, #%d);
              return 1;
              """
                  .formatted(ancestor, ancestor, ancestor));
      WorldObject inheritedTarget = readObject(world, ancestor).orElseThrow();
      assertEquals(oldParent, inheritedTarget.parent());
      assertEquals(1, inheritedTarget.verbs().size());
      WorldVerb inherited = inheritedTarget.verbs().getFirst();
      assertEquals("audit_inherited_caller", inherited.names());
      assertEquals(player, inherited.owner());
      assertEquals(92, inherited.permissions());
      assertEquals(-2, inherited.preposition());
      assertEquals(
          "notify(player, \"CALLER_IS_PLAYER:\" + tostr(caller == player));",
          inherited.programSource());
      assertEquals(List.of(player), inheritedTarget.children());
      assertEquals(ancestor, readObject(world, player).orElseThrow().parent());
      assertTrue(readObject(world, oldParent).isEmpty());
      assertEquals(List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX), setupOutput);

      assertEquals(
          List.of("CALLER_IS_PLAYER:1"),
          runtime.executeLine(connectionId, "audit_inherited_caller"));
    } finally {
      runtime.executeLine(
          connectionId,
          "; chparent(player, #" + oldParent + "); recycle(#" + ancestor + "); return 1;");
      assertEquals(oldParent, readObject(world, player).orElseThrow().parent());
      assertTrue(readObject(world, ancestor).isEmpty());
      assertTrue(readObject(world, oldParent).isEmpty());
    }
  }

  @Test
  void preservesPlayerCallerForADeepInheritedCommand() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    long oldParent = readObject(world, player).orElseThrow().parent();
    assertEquals(-1, oldParent);
    assertTrue(readObject(world, oldParent).isEmpty());
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, #" + oldParent + "}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return parent(player);"));

    long definingAncestor = world.snapshot().objects().size();
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, #" + definingAncestor + "}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return create(#" + oldParent + ");"));
    long middleAncestor = world.snapshot().objects().size();
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, #" + middleAncestor + "}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return create(#" + definingAncestor + ");"));
    assertEquals(oldParent, readObject(world, definingAncestor).orElseThrow().parent());
    assertEquals(definingAncestor, readObject(world, middleAncestor).orElseThrow().parent());
    assertEquals(List.of(middleAncestor), readObject(world, definingAncestor).orElseThrow().children());

    try {
      List<String> setupOutput =
          runtime.executeLine(
              connectionId,
              """
              ; add_verb(#%d, {player, "xd", "audit_deep_inherited_caller"}, {"any", "any", "any"});
              set_verb_code(#%d, "audit_deep_inherited_caller", {"notify(player, \\"CALLER_IS_PLAYER:\\" + tostr(caller == player));"});
              chparent(player, #%d);
              return 1;
              """
                  .formatted(definingAncestor, definingAncestor, middleAncestor));
      WorldObject defining = readObject(world, definingAncestor).orElseThrow();
      WorldObject middle = readObject(world, middleAncestor).orElseThrow();
      assertEquals(oldParent, defining.parent());
      assertEquals(List.of(middleAncestor), defining.children());
      assertEquals(1, defining.verbs().size());
      assertEquals(List.of(), middle.verbs());
      WorldVerb inherited = defining.verbs().getFirst();
      assertEquals("audit_deep_inherited_caller", inherited.names());
      assertEquals(player, inherited.owner());
      assertEquals(92, inherited.permissions());
      assertEquals(-2, inherited.preposition());
      assertEquals(
          "notify(player, \"CALLER_IS_PLAYER:\" + tostr(caller == player));",
          inherited.programSource());
      assertEquals(definingAncestor, middle.parent());
      assertEquals(List.of(player), middle.children());
      assertEquals(middleAncestor, readObject(world, player).orElseThrow().parent());
      assertTrue(readObject(world, oldParent).isEmpty());
      assertEquals(List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX), setupOutput);

      assertEquals(
          List.of("CALLER_IS_PLAYER:1"),
          runtime.executeLine(connectionId, "audit_deep_inherited_caller"));
    } finally {
      runtime.executeLine(
          connectionId,
          "; chparent(player, #"
              + oldParent
              + "); recycle(#"
              + middleAncestor
              + "); recycle(#"
              + definingAncestor
              + "); return 1;");
      assertEquals(oldParent, readObject(world, player).orElseThrow().parent());
      assertTrue(readObject(world, middleAncestor).isEmpty());
      assertTrue(readObject(world, definingAncestor).isEmpty());
      assertTrue(readObject(world, oldParent).isEmpty());
    }
  }

  @Test
  void preservesPlayerCallerAcrossInheritedCommandPass() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    long player = runtime.connectionPlayer(connectionId).orElseThrow();
    long oldParent = readObject(world, player).orElseThrow().parent();
    assertEquals(-1, oldParent);
    assertTrue(readObject(world, oldParent).isEmpty());
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, #" + oldParent + "}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return parent(player);"));

    long passTarget = world.snapshot().objects().size();
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, #" + passTarget + "}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return create(#" + oldParent + ");"));
    long passGap = world.snapshot().objects().size();
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, #" + passGap + "}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return create(#" + passTarget + ");"));
    long commandDefiner = world.snapshot().objects().size();
    assertEquals(
        List.of(CONNECTION_PREFIX, "{1, #" + commandDefiner + "}", CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return create(#" + passGap + ");"));
    assertEquals(oldParent, readObject(world, passTarget).orElseThrow().parent());
    assertEquals(List.of(passGap), readObject(world, passTarget).orElseThrow().children());
    assertEquals(passTarget, readObject(world, passGap).orElseThrow().parent());
    assertEquals(List.of(commandDefiner), readObject(world, passGap).orElseThrow().children());
    assertEquals(passGap, readObject(world, commandDefiner).orElseThrow().parent());
    assertEquals(List.of(), readObject(world, commandDefiner).orElseThrow().children());

    try {
      List<String> setupOutput =
          runtime.executeLine(
              connectionId,
              """
              ; add_verb(#%d, {player, "xd", "audit_pass_caller"}, {"any", "any", "any"});
              set_verb_code(#%d, "audit_pass_caller", {"notify(player, \\"PASS_CALLER_IS_PLAYER:\\" + tostr(caller == player));"});
              add_verb(#%d, {player, "xd", "audit_pass_caller"}, {"any", "any", "any"});
              set_verb_code(#%d, "audit_pass_caller", {"notify(player, \\"COMMAND_CALLER_IS_PLAYER:\\" + tostr(caller == player));", "return pass(@args);"});
              chparent(player, #%d);
              return 1;
              """
                  .formatted(
                      passTarget, passTarget, commandDefiner, commandDefiner, commandDefiner));
      WorldObject target = readObject(world, passTarget).orElseThrow();
      WorldObject gap = readObject(world, passGap).orElseThrow();
      WorldObject definer = readObject(world, commandDefiner).orElseThrow();
      assertEquals(oldParent, target.parent());
      assertEquals(List.of(passGap), target.children());
      assertEquals(passTarget, gap.parent());
      assertEquals(List.of(commandDefiner), gap.children());
      assertEquals(List.of(), gap.verbs());
      assertEquals(passGap, definer.parent());
      assertEquals(List.of(player), definer.children());
      assertEquals(commandDefiner, readObject(world, player).orElseThrow().parent());
      assertTrue(readObject(world, oldParent).isEmpty());
      assertEquals(1, target.verbs().size());
      WorldVerb pass = target.verbs().getFirst();
      assertEquals("audit_pass_caller", pass.names());
      assertEquals(player, pass.owner());
      assertEquals(92, pass.permissions());
      assertEquals(-2, pass.preposition());
      assertEquals(
          "notify(player, \"PASS_CALLER_IS_PLAYER:\" + tostr(caller == player));",
          pass.programSource());
      assertEquals(1, definer.verbs().size());
      WorldVerb command = definer.verbs().getFirst();
      assertEquals("audit_pass_caller", command.names());
      assertEquals(player, command.owner());
      assertEquals(92, command.permissions());
      assertEquals(-2, command.preposition());
      assertEquals(
          "notify(player, \"COMMAND_CALLER_IS_PLAYER:\" + tostr(caller == player));\n"
              + "return pass(@args);",
          command.programSource());
      assertEquals(List.of(CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX), setupOutput);

      assertEquals(
          List.of("COMMAND_CALLER_IS_PLAYER:1", "PASS_CALLER_IS_PLAYER:1"),
          runtime.executeLine(connectionId, "audit_pass_caller"));
    } finally {
      runtime.executeLine(
          connectionId,
          "; chparent(player, #"
              + oldParent
              + "); recycle(#"
              + commandDefiner
              + "); recycle(#"
              + passGap
              + "); recycle(#"
              + passTarget
              + "); return 1;");
      assertEquals(oldParent, readObject(world, player).orElseThrow().parent());
      assertTrue(readObject(world, commandDefiner).isEmpty());
      assertTrue(readObject(world, passGap).isEmpty());
      assertTrue(readObject(world, passTarget).isEmpty());
      assertTrue(readObject(world, oldParent).isEmpty());
    }
  }

  @Test
  void evalRuntimeErrorUnwindsIntoPersistedCallerExceptAndFinally() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{2, {E_TYPE, \"\"}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, "; return 1.0 + 1;"));
  }

  @Test
  void evalCompileErrorReturnsParseDiagnosticThroughStoredCaller() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    List<String> output =
        runtime.executeLine(
            connectionId,
            "; x = {}; for in ({\"1\", \"2\", \"3\", \"4\", \"5\"}) endfor return x;");

    assertTrue(output.stream().anyMatch(line -> line.contains("Parse error")), output::toString);
  }

  @Test
  void evalInvalidContinueLoopNameReturnsToastDiagnosticThroughStoredCaller() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    List<String> output =
        runtime.executeLine(
            connectionId,
            "; x = {}; for i in ({\"1\", \"2\", \"3\", \"4\", \"5\"}) continue x; endfor return x;");

    assertTrue(output.stream().anyMatch(line -> line.contains("Invalid loop name")), output::toString);
  }

  @Test
  void executesEqualityCollectionsThroughStoredEvalRuntime() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 0, {1, 0}}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; return {\"10\" == \"1\" + \"0\", [] == {}, {\"A\" == \"a\", \"À\" == \"à\"}};"));
  }

  @Test
  void executesTheFrozenQueuedTasksSurfaceThroughStoredEvalRuntime() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {{{\"function_info\", 0, 1, {2}}, "
                + "{\"queued_tasks\", 0, 2, {0, 0}}}, "
                + "{\"function_info\", 0, 1, {2}}, "
                + "{\"queued_tasks\", 0, 2, {0, 0}}, {}, {}, {}, 0}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; return {function_info(), function_info(\"function_info\"), "
                + "function_info(\"queued_tasks\"), queued_tasks(), queued_tasks(1), "
                + "queued_tasks(0, 0), queued_tasks(0, 1)};"));
  }

  @Test
  void executesSqliteExistsInRuntimeThroughStoredEvalRuntime() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 1}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; try sqlite_open(1,2,3,4,5,6,7,8,9); return 1; except (E_VERBNF) return 0; except (ANY) return 1; endtry;"));
  }

  @Test
  void executesSqliteManagedSurfaceThroughStoredEvalRuntime() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));
    assertEquals(List.of(), runtime.executeLine(connectionId, "PREFIX " + CONNECTION_PREFIX));
    assertEquals(List.of(), runtime.executeLine(connectionId, "SUFFIX " + CONNECTION_SUFFIX));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 1, 1, 1, 1, 1, 1}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; h = sqlite_open(\":memory:\"); info = sqlite_info(h); "
                + "return {h > 0, h in sqlite_handles(), info[\"path\"] == \":memory:\", "
                + "info[\"parse_types\"] == 1, info[\"parse_objects\"] == 1, "
                + "info[\"sanitize_strings\"] == 0, info[\"locks\"] == 0};"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 1, 1}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; sqlite_query(1, \"CREATE TABLE t(id INTEGER PRIMARY KEY, n INTEGER, "
                + "x REAL, obj TEXT, label TEXT)\"); "
                + "inserted = sqlite_execute(1, \"INSERT INTO t(n, x, obj, label) "
                + "VALUES (?, ?, ?, ?)\", {42, 3.5, #0, \"alpha\"}); "
                + "return {typeof(inserted) == LIST, length(inserted) == 0, "
                + "sqlite_last_insert_row_id(1) == 1};"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {{42, 3.5, #0, \"alpha\"}}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; return sqlite_execute(1, \"SELECT ?, ?, ?, ?\", {42, 3.5, #0, \"alpha\"});"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {{{\"first\", 42}, {\"label\", \"alpha\"}}}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId, "; return sqlite_query(1, \"SELECT n AS first, label FROM t\", 1);"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 1, 1, 1, 1}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; name_limit = sqlite_limit(1, \"LIMIT_COLUMN\", -1); "
                + "number_limit = sqlite_limit(1, 2, -1); "
                + "previous = sqlite_limit(1, 2, name_limit - 1); "
                + "current = sqlite_limit(1, \"LIMIT_COLUMN\", -1); "
                + "restored = sqlite_limit(1, \"LIMIT_COLUMN\", name_limit); "
                + "final = sqlite_limit(1, 2, -1); "
                + "return {name_limit == number_limit, previous == name_limit, "
                + "current == name_limit - 1, restored == name_limit - 1, "
                + "final == name_limit};"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {1, 1, 1, 1, 1, 1, 1}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; query_returned = sqlite_query(999999, \"SELECT 1\") == E_INVARG; "
                + "execute_returned = sqlite_execute(999999, \"SELECT 1\", {}) == E_INVARG; "
                + "close_raised = `sqlite_close(999999) ! E_INVARG => 1'; "
                + "info_raised = `sqlite_info(999999) ! E_INVARG => 1'; "
                + "limit_raised = `sqlite_limit(1, \"LIMIT_NOT_REAL\", -1) "
                + "! E_INVARG => 1'; "
                + "call_raised = `call_function(\"sqlite_info\", 2) ! E_INVARG => 1'; "
                + "interrupt_raised = `sqlite_interrupt(999999) ! E_INVARG => 1'; "
                + "return {query_returned, execute_returned, close_raised, info_raised, "
                + "limit_raised, call_raised, interrupt_raised};"));

    assertEquals(
        List.of(
            CONNECTION_PREFIX,
            CONNECTION_PREFIX,
            "{1, {0, 0}}",
            CONNECTION_SUFFIX,
            CONNECTION_SUFFIX),
        runtime.executeLine(
            connectionId,
            "; result = sqlite_close(1); return {result, length(sqlite_handles())};"));
  }

  @Test
  void interruptsActiveSqliteQueryAfterExactDelayedForkAndSuspend() throws Exception {
    WorldTxn world = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(world);
    long connectionId = -47;

    assertEquals(List.of(), runtime.openConnection(connectionId));
    assertEquals(List.of("*** Connected ***"), runtime.executeLine(connectionId, "connect Wizard"));

    List<String> output =
        runtime.executeLine(
            connectionId,
            """
            ; h = sqlite_open(":memory:");
            fork (1)
              suspend(1);
              sqlite_interrupt(h);
            endfork
            result = sqlite_query(h, "WITH RECURSIVE cnt(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM cnt WHERE x < 1000) SELECT count(*) FROM cnt AS a, cnt AS b, cnt AS c, cnt AS d;");
            sqlite_close(h);
            return result;
            """);

    assertTrue(
        output.stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains("interrupt")),
        output::toString);
  }

  private static Optional<WorldObject> readObject(WorldTxn root, long objectId) {
    try (WorldTxn transaction = root.begin()) {
      return transaction.object(objectId);
    }
  }

  private static Optional<WorldProperty> readProperty(
      WorldTxn root, long objectId, String propertyName) {
    try (WorldTxn transaction = root.begin()) {
      return transaction.property(objectId, propertyName);
    }
  }

  private static Optional<WorldVerb> readVerb(
      WorldTxn root, long objectId, String verbName) {
    try (WorldTxn transaction = root.begin()) {
      return transaction.verb(objectId, verbName);
    }
  }

  private static Optional<WorldVerb> readVerb(WorldTxn root, long objectId, int verbIndex) {
    try (WorldTxn transaction = root.begin()) {
      return transaction.verb(objectId, verbIndex);
    }
  }

  private static Optional<MooValue> readObjectProperty(
      WorldTxn root, long objectId, String propertyName) {
    try (WorldTxn transaction = root.begin()) {
      return transaction.readObjectProperty(objectId, propertyName);
    }
  }

  private static void executeSetup(
      MooRuntime runtime, long connectionId, String name, String value) {
    String source =
        "; try add_property(#0, \""
            + name
            + "\", "
            + value
            + ", {#0, \"rc\"}); except (ANY) return 0; endtry";
    assertEquals(
        List.of(
            CONNECTION_PREFIX, CONNECTION_PREFIX, "{1, 0}", CONNECTION_SUFFIX, CONNECTION_SUFFIX),
        runtime.executeLine(connectionId, source));
  }
}
