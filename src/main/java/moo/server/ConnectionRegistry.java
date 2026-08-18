package moo.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.StringValue;

/** Attempt-local connection identity, metadata, and intrinsic-command state. */
public final class ConnectionRegistry {
  private static final ListValue DEFAULT_INTRINSIC_COMMANDS =
      new ListValue(
          List.of(
              StringValue.of(".program"),
              StringValue.of("PREFIX"),
              StringValue.of("SUFFIX"),
              StringValue.of("OUTPUTPREFIX"),
              StringValue.of("OUTPUTSUFFIX")));

  private final Map<Long, Long> players;
  private final Map<Long, MapValue> connectionInfo;
  private final Map<Long, ListValue> intrinsicCommands;

  /** Creates an empty registry. */
  public ConnectionRegistry() {
    this(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
  }

  private ConnectionRegistry(
      Map<Long, Long> players,
      Map<Long, MapValue> connectionInfo,
      Map<Long, ListValue> intrinsicCommands) {
    this.players = players;
    this.connectionInfo = connectionInfo;
    this.intrinsicCommands = intrinsicCommands;
  }

  /** Returns an independent attempt-local copy. */
  public ConnectionRegistry copy() {
    return new ConnectionRegistry(
        new LinkedHashMap<>(players),
        new LinkedHashMap<>(connectionInfo),
        new LinkedHashMap<>(intrinsicCommands));
  }

  /** Registers one negative pre-login connection object. */
  public void openConnection(long connectionId) {
    openConnection(connectionId, new MapValue(Map.of()));
  }

  /** Registers one negative connection and its immutable network metadata. */
  public void openConnection(long connectionId, MapValue info) {
    if (connectionId >= 0) {
      throw new IllegalArgumentException("connection object must be negative");
    }
    if (players.putIfAbsent(connectionId, connectionId) != null) {
      throw new IllegalArgumentException("duplicate connection #" + connectionId);
    }
    connectionInfo.put(connectionId, Objects.requireNonNull(info, "info"));
    intrinsicCommands.put(connectionId, DEFAULT_INTRINSIC_COMMANDS);
  }

  /** Removes one connection record. */
  public void closeConnection(long connectionId) {
    players.remove(connectionId);
    connectionInfo.remove(connectionId);
    intrinsicCommands.remove(connectionId);
  }

  /** Returns the player currently attached to a connection. */
  public OptionalLong connectionPlayer(long connectionId) {
    Long player = players.get(connectionId);
    return player == null ? OptionalLong.empty() : OptionalLong.of(player);
  }

  /** Returns attached players in newest-connection-first order. */
  public List<Long> connectedPlayers(boolean showAll) {
    List<Long> connected = new ArrayList<>();
    for (long player : players.values()) {
      if (showAll || player >= 0) {
        connected.addFirst(player);
      }
    }
    return List.copyOf(connected);
  }

  /** Returns network metadata for a connection object or its attached player. */
  public Optional<MapValue> connectionInfo(long objectId) {
    OptionalLong connectionId = connectionId(objectId);
    return connectionId.isEmpty()
        ? Optional.empty()
        : Optional.ofNullable(connectionInfo.get(connectionId.orElseThrow()));
  }

  /** Resolves a live connection object or its attached player to the connection ID. */
  public OptionalLong connectionId(long objectId) {
    if (players.containsKey(objectId)) {
      return OptionalLong.of(objectId);
    }
    for (Map.Entry<Long, Long> connection : players.entrySet()) {
      if (connection.getValue() == objectId) {
        return OptionalLong.of(connection.getKey());
      }
    }
    return OptionalLong.empty();
  }

  /** Returns the enabled intrinsic command table for a live connection or attached player. */
  public Optional<ListValue> intrinsicCommands(long objectId) {
    OptionalLong connectionId = connectionId(objectId);
    return connectionId.isEmpty()
        ? Optional.empty()
        : Optional.ofNullable(intrinsicCommands.get(connectionId.orElseThrow()));
  }

  /** Replaces the intrinsic command table for a live connection or attached player. */
  public boolean setIntrinsicCommands(long objectId, ListValue commands) {
    Objects.requireNonNull(commands, "commands");
    OptionalLong connectionId = connectionId(objectId);
    if (connectionId.isEmpty()) {
      return false;
    }
    intrinsicCommands.put(connectionId.orElseThrow(), commands);
    return true;
  }

  /** Stages a player switch on an existing connection. */
  public boolean switchConnectionPlayer(long connectionId, long playerId) {
    if (!players.containsKey(connectionId)) {
      return false;
    }
    players.put(connectionId, playerId);
    return true;
  }
}
