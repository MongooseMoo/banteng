package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.builtin.BuiltinCatalog.ListenerControl;
import world.mongoose.banteng.server.ConnectionRegistry;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.*;
import world.mongoose.banteng.world.*;

final class SwitchPlayerCallbacksTest {
  @Test
  void switchesTheRequestedConnectionAndPublishesFreshOrderedHooks() throws Exception {
    for (boolean occupied : List.of(false, true)) {
      for (boolean prelogin : List.of(false, true)) {
        for (int silent : List.of(0, 1)) {
          String old = prelogin ? "#-11" : "#1";
          String script = "switch_player(" + old + ", #3, " + silent + "); "
              + "#0.observed = {connected_players(), connected_seconds(#3)};";
          try (Fixture f = new Fixture(script, "return toobj(args[1]);", "")) {
            if (!prelogin) f.login(-11, 1);
            if (occupied) f.login(-12, 3);
            f.clear();
            f.clock.set(120_000_000_000L);
            f.runtime.startScript(new ListValue(List.of()));
            List<String> expected = occupied ? List.of("user_reconnected:3")
                : prelogin ? List.of("user_connected:3") : List.of("user_disconnected:1", "user_connected:3");
            f.await(expected);
            assertEquals(3, f.connections.connectionPlayer(-11).orElseThrow());
            if (occupied) assertTrue(f.connections.connectionPlayer(-12).isEmpty());
            ListValue observed = (ListValue) f.read("observed");
            assertTrue(((ListValue) observed.elements().getFirst()).elements().contains(new ObjectValue(3)));
            assertEquals(new IntegerValue(0), observed.elements().get(1));
            assertEquals(silent == 0, f.transport.toString().contains("***"));
            if (occupied) assertTrue(f.transport.contains("boot:-12"));
          }
        }
      }
    }
  }

  @Test
  void disconnectFirstSliceDoesNotWaitForItsSuspensionOrError() throws Exception {
    for (String tail : List.of("suspend(60);", "suspend(0);", "raise(E_INVARG);")) {
      try (Fixture f = new Fixture("switch_player(#1, #3, 1);", "return toobj(args[1]);", tail)) {
        f.login(-11, 1);
        f.clear();
        f.runtime.startScript(new ListValue(List.of()));
        f.await(List.of("user_disconnected:1", "user_connected:3"));
      }
    }
  }

  @Test
  void secondHookUsesPlayerAndVerbAfterDisconnectsFirstSlice() throws Exception {
    try (Fixture f = new Fixture("switch_player(#1, #3, 1);", "return toobj(args[1]);",
        "switch_player(#3, #2, 1);")) {
      f.login(-11, 1);
      f.clear();
      f.runtime.startScript(new ListValue(List.of()));
      f.await(List.of("user_disconnected:1", "user_connected:2"));
      assertEquals(2, f.connections.connectionPlayer(-11).orElseThrow());
    }
    String replacement = "#0.events = {@#0.events, \"replacement\"}; switch_player(#3, #2, 1);";
    String replace = "set_verb_code(#0, \"user_connected\", {\""
        + replacement.replace("\\", "\\\\").replace("\"", "\\\"") + "\"});";
    try (Fixture f = new Fixture("switch_player(#1, #3, 1);", "return toobj(args[1]);", replace)) {
      f.login(-11, 1);
      f.clear();
      f.runtime.startScript(new ListValue(List.of()));
      f.await(List.of("user_disconnected:1", "replacement"));
      assertEquals(2, f.connections.connectionPlayer(-11).orElseThrow());
      f.runtime.startServer();
      assertEquals(2, ((ListValue) f.read("events")).elements().size());
    }
  }

  @Test
  void absentHooksAdvanceWithoutLeavingTheMarkerInProgress() throws Exception {
    for (String absent : List.of("user_disconnected", "user_connected")) {
      try (Fixture f = new Fixture("delete_verb(#0, \"" + absent + "\"); switch_player(#1, #3, 1);",
          "return toobj(args[1]);", "")) {
        f.login(-11, 1);
        f.clear();
        f.runtime.startScript(new ListValue(List.of()));
        f.await(List.of(absent.equals("user_disconnected") ? "user_connected:3" : "user_disconnected:1"));
      }
    }
  }

  @Test
  void repeatedSwitchesCollapseAcrossHostAuthorizationAndForkContinuations() throws Exception {
    try (Fixture f = new Fixture("switch_player(#1, #2, 1); ftime(); fork (60) endfork switch_player(#2, #3, 1);",
        "return toobj(args[1]);", "")) {
      f.login(-11, 1);
      f.clear();
      f.runtime.startScript(new ListValue(List.of()));
      f.await(List.of("user_disconnected:2", "user_connected:3"));
    }
  }

  @Test
  void loginUsesItsActualAssociationAndPreloginCloseNotifiesNegativeIdentity() throws Exception {
    try (Fixture f = new Fixture("", "switch_player(player, #3, 1); return #2;", "")) {
      f.clear();
      f.runtime.executeLine(-11, "login");
      f.await(List.of("user_connected:3"));
      assertEquals(3, f.connections.connectionPlayer(-11).orElseThrow());
    }
    try (Fixture f = new Fixture("", "switch_player(#-12, #3, 1); return #2;", "")) {
      f.clear();
      f.runtime.executeLine(-11, "login");
      f.await(List.of("user_connected:2", "user_connected:3"));
      assertEquals(2, f.connections.connectionPlayer(-11).orElseThrow());
      assertEquals(3, f.connections.connectionPlayer(-12).orElseThrow());
    }
    try (Fixture f = new Fixture("", "return 0;", "")) {
      f.runtime.closeConnection(-11);
      f.await(List.of("user_client_disconnected:-11"));
    }
  }

  @Test
  void normalCrossListenerLoginRedirectKeepsItsClientDisconnectThenConnect() throws Exception {
    try (Fixture f = new Fixture("", "return toobj(args[1]);", "", 3)) {
      f.login(-11, 1);
      f.clear();
      f.login(-12, 1);
      f.await(List.of("user_client_disconnected:1", "user_connected:1"));
      assertTrue(f.connections.connectionPlayer(-11).isEmpty());
      assertEquals(1, f.connections.connectionPlayer(-12).orElseThrow());
    }
  }

  private static final class Fixture implements AutoCloseable {
    final WorldTxn world;
    final ConnectionRegistry connections = new ConnectionRegistry();
    final AtomicLong clock = new AtomicLong(1_000_000_000L);
    final List<String> transport = new CopyOnWriteArrayList<>();
    final MooRuntime runtime;

    Fixture(String script, String login, String disconnectTail) {
      this(script, login, disconnectTail, 0);
    }

    Fixture(String script, String login, String disconnectTail, long secondListener) {
      List<WorldVerb> verbs = new ArrayList<>();
      verbs.add(verb("do_start_script", script));
      verbs.add(verb("do_login_command", "if (!args) return 0; endif " + login));
      for (String name : List.of("user_connected", "user_reconnected", "user_disconnected", "user_client_disconnected")) {
        verbs.add(verb(name, "if (caller != #-1 || caller_perms() != #-1 || callers() != {} || args != {player} || dobj != #-1 || iobj != #-1) raise(E_INVARG); endif "
            + "#0.events = {@#0.events, verb + \":\" + tostr(toint(player))}; "
            + (name.equals("user_disconnected") ? disconnectTail : "")));
      }
      world = new WorldTxn(List.of(1L, 2L, 3L), List.of(
          new WorldObject(0, "system", 4, 0, -1, List.of(), List.of(), List.of(1L, 2L, 3L), verbs,
              List.of(property("events", new ListValue(List.of())), property("observed", new IntegerValue(0)))),
          user(1), user(2), user(3)));
      ListenerControl listener = (ListenerControl) Proxy.newProxyInstance(getClass().getClassLoader(),
          new Class<?>[] {ListenerControl.class}, (proxy, method, args) -> {
            if (method.getName().equals("writeConnection")) transport.add(args[0] + ":" + args[1]);
            if (method.getName().equals("bootConnection")) transport.add("boot:" + args[0]);
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == boolean.class) return false;
            return null;
          });
      runtime = SchedulerTestHarness.runtime(world, connections, 2, clock::get,
          MooRuntime::resolveCanonicalConnectionName, Optional.of(listener), Optional.empty(),
          Optional.empty(), List.of(), List.of());
      runtime.openConnection(-11, 0, true, new MapValue(Map.of()));
      runtime.openConnection(-12, secondListener, true, new MapValue(Map.of()));
    }

    void login(long connection, long player) { runtime.executeLine(connection, "#" + player); }
    void clear() {
      try (WorldTxn transaction = world.begin()) {
        assertTrue(transaction.writeObjectProperty(0, "events", new ListValue(List.of())).isOk());
        assertTrue(transaction.commit().isCommitted());
      }
      transport.clear();
    }
    MooValue read(String name) {
      try (WorldTxn transaction = world.begin()) { return transaction.readObjectProperty(0, name).orElseThrow(); }
    }
    void await(List<String> expected) {
      assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
        while (true) {
          ListValue events = (ListValue) read("events");
          List<String> actual = events.elements().stream().map(v -> ((StringValue) v).text()).toList();
          if (actual.size() >= expected.size()) { assertEquals(expected, actual); return; }
          Thread.sleep(10);
        }
      });
    }
    @Override public void close() { runtime.close(); }
  }

  private static WorldObject user(long id) {
    return new WorldObject(id, "user" + id, 0, id, -1, List.of(0L), List.of(), List.of(), List.of(), List.of());
  }
  private static WorldVerb verb(String name, String source) {
    return new WorldVerb(name, 0, new VerbPerms(12), ArgSpec.NONE, -1, ArgSpec.NONE, source);
  }
  private static WorldProperty property(String name, MooValue value) {
    return new WorldProperty(name, value, 0, new PropertyPerms(3), false, true);
  }
}
