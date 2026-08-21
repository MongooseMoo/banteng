package world.mongoose.banteng.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import world.mongoose.banteng.builtin.ConnectionRegistryAccess;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import org.junit.jupiter.api.Test;

final class ConnectionRegistryTest {
  @Test
  void ownsConnectionIdentityMetadataAndIntrinsicCommandsOutsideTheWorld() {
    ConnectionRegistry registry = new ConnectionRegistry();
    MapValue info = new MapValue(Map.of(StringValue.of("remote"), StringValue.of("example")));
    ListValue commands = new ListValue(List.of(StringValue.of("PREFIX")));

    registry.openConnection(-2, info);
    registry.openConnection(-3);
    assertTrue(registry.switchConnectionPlayer(-2, 7));
    assertTrue(registry.setIntrinsicCommands(7, commands));

    assertEquals(7, registry.connectionPlayer(-2).orElseThrow());
    assertEquals(List.of(7L), registry.connectedPlayers(false));
    assertEquals(List.of(-3L, 7L), registry.connectedPlayers(true));
    assertEquals(-2, registry.connectionId(7).orElseThrow());
    assertEquals(info, registry.connectionInfo(7).orElseThrow());
    assertEquals(commands, registry.intrinsicCommands(7).orElseThrow());
    assertEquals(5, registry.intrinsicCommands(-3).orElseThrow().size());

    registry.closeConnection(-2);
    assertTrue(registry.connectionPlayer(-2).isEmpty());
    assertTrue(registry.connectionInfo(7).isEmpty());
    assertFalse(registry.switchConnectionPlayer(-2, 9));
  }

  @Test
  void rejectsNonnegativeAndDuplicateConnectionIds() {
    ConnectionRegistry registry = new ConnectionRegistry();

    assertThrows(IllegalArgumentException.class, () -> registry.openConnection(0));
    registry.openConnection(-2);
    assertThrows(IllegalArgumentException.class, () -> registry.openConnection(-2));
  }

  @Test
  void copyIsIndependentAttemptLocalState() {
    ConnectionRegistryAccess original = new ConnectionRegistry();
    original.openConnection(-2);
    ConnectionRegistryAccess copy = original.copy();

    assertTrue(copy.switchConnectionPlayer(-2, 4));

    assertEquals(-2, original.connectionPlayer(-2).orElseThrow());
    assertEquals(4, copy.connectionPlayer(-2).orElseThrow());

    original.replaceWith(copy);

    assertEquals(4, original.connectionPlayer(-2).orElseThrow());
  }

  @Test
  void rewritesOnlyTheSameLiveConnectionsRemoteName() {
    ConnectionRegistry registry = new ConnectionRegistry();
    MapValue info =
        new MapValue(
            Map.of(
                StringValue.of("destination_address"), StringValue.of("198.51.100.25"),
                StringValue.of("destination_ip"), StringValue.of("198.51.100.25"),
                StringValue.of("protocol"), StringValue.of("IPv4")));
    registry.openConnection(-2, info);

    assertFalse(registry.rewriteConnectionName(-2, "203.0.113.8", "wrong.example"));
    assertEquals(info, registry.connectionInfo(-2).orElseThrow());
    assertTrue(registry.rewriteConnectionName(-2, "198.51.100.25", "client.example"));
    assertEquals(
        StringValue.of("client.example"),
        registry
            .connectionInfo(-2)
            .orElseThrow()
            .get(StringValue.of("destination_address"))
            .orElseThrow());
    assertEquals(
        StringValue.of("198.51.100.25"),
        registry
            .connectionInfo(-2)
            .orElseThrow()
            .get(StringValue.of("destination_ip"))
            .orElseThrow());
    assertEquals(
        StringValue.of("IPv4"),
        registry
            .connectionInfo(-2)
            .orElseThrow()
            .get(StringValue.of("protocol"))
            .orElseThrow());

    registry.closeConnection(-2);
    assertFalse(registry.rewriteConnectionName(-2, "198.51.100.25", "late.example"));
  }
}
