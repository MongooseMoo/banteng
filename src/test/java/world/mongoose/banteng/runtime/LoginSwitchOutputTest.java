package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.builtin.BuiltinCatalog.ListenerControl;
import world.mongoose.banteng.persistence.LambdaMooV17Codec;
import world.mongoose.banteng.persistence.LambdaMooV4Reader;
import world.mongoose.banteng.server.ConnectionRegistry;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.VerbPerms;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

final class LoginSwitchOutputTest {
  @Test
  void storedConformanceLoginReturnsItsConnectMessageWithoutTransport() throws Exception {
    WorldTxn world = new LambdaMooV4Reader(new LambdaMooV17Codec()).read(
        Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db")).world();
    try (MooRuntime runtime = runtime(world, new ConnectionRegistry(), Optional.empty())) {
      assertEquals(List.of(), runtime.openConnection(-47));
      assertEquals(List.of("*** Connected ***"), runtime.executeLine(-47, "connect Wizard"));
    }
  }

  @Test
  void configuredSilentNoPrintAndOtherConnectionWritesKeepTheirDestination() {
    for (int silent : List.of(0, 1)) {
      for (boolean print : List.of(false, true)) {
        ConnectionRegistry connections = new ConnectionRegistry();
        try (MooRuntime runtime = runtime(world(silent, ""), connections, Optional.empty())) {
          runtime.openConnection(-11, 0, print, new MapValue(Map.of()));
          runtime.openConnection(-12, 0, true, new MapValue(Map.of()));
          assertEquals(List.of(), runtime.executeLine(-11, "other"));
          assertEquals(2, connections.connectionPlayer(-12).orElseThrow());
          assertEquals(print && silent == 0 ? List.of("custom connected", "second line") : List.of(),
              runtime.executeLine(-11, "self"));
        }
      }
    }
  }

  @Test
  void authorizationAndActualSuspensionReturnTheMessageOnce() {
    try (MooRuntime runtime = runtime(world(0, "ftime(); suspend(0);"), new ConnectionRegistry(), Optional.empty())) {
      runtime.openConnection(-11);
      org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
          assertEquals(List.of("custom connected", "second line"), runtime.executeLine(-11, "self")));
    }
  }

  @Test
  void attachedTransportReceivesTheWriteOnceWithoutDuplicateReturnOutput() {
    List<String> writes = new CopyOnWriteArrayList<>();
    ListenerControl listener = (ListenerControl) Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[] {ListenerControl.class}, (proxy, method, args) -> {
          if (method.getName().equals("writeConnection")) writes.add(args[0] + ":" + args[1]);
          if (method.getReturnType() == long.class) return 0L;
          if (method.getReturnType() == int.class) return 0;
          if (method.getReturnType() == boolean.class) return false;
          return null;
        });
    try (MooRuntime runtime = runtime(world(0, ""), new ConnectionRegistry(), Optional.of(listener))) {
      runtime.openConnection(-11);
      assertEquals(List.of(), runtime.executeLine(-11, "self"));
      assertEquals(List.of("-11:[custom connected, second line]"), writes);
    }
  }

  private static MooRuntime runtime(WorldTxn world, ConnectionRegistry connections, Optional<ListenerControl> listener) {
    return SchedulerTestHarness.runtime(world, connections, 2, System::nanoTime,
        MooRuntime::resolveCanonicalConnectionName, listener, Optional.empty(), Optional.empty(), List.of(), List.of());
  }

  private static WorldTxn world(int silent, String tail) {
    WorldVerb login = new WorldVerb("do_login_command", 0, new VerbPerms(12), ArgSpec.NONE, -1, ArgSpec.NONE,
        "if (!args) return 0; endif if (args[1] == \"other\") switch_player(#-12, #2, " + silent + "); return 0; endif "
            + "switch_player(player, #1, " + silent + "); " + tail + " return;");
    return new WorldTxn(List.of(1L, 2L), List.of(
        object(0, 4, List.of(login), List.of(property("server_options", new ObjectValue(3)))),
        object(1, 0, List.of(), List.of()), object(2, 0, List.of(), List.of()),
        object(3, 0, List.of(), List.of(property("connect_msg",
            new ListValue(List.of(StringValue.of("custom connected"), StringValue.of("second line"))))))));
  }

  private static WorldObject object(long id, int flags, List<WorldVerb> verbs, List<WorldProperty> properties) {
    return new WorldObject(id, "object" + id, flags, id, -1, List.of(), List.of(), List.of(), verbs, properties);
  }

  private static WorldProperty property(String name, MooValue value) {
    return new WorldProperty(name, value, 0, new PropertyPerms(3), false, true);
  }
}
