package moo.builtin;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;

/** Consumer-owned access to server connection identity and metadata state. */
public interface ConnectionRegistryAccess {
  /** Returns the registered connection IDs in insertion order. */
  List<Long> connectionIds();

  /** Returns an independent attempt-local copy. */
  ConnectionRegistryAccess copy();

  /** Replaces this registry's contents from an independent registry. */
  void replaceWith(ConnectionRegistryAccess source);

  /** Returns whether both registries contain the same ordered connection state. */
  boolean sameState(ConnectionRegistryAccess other);

  /** Registers one negative pre-login connection object. */
  void openConnection(long connectionId);

  /** Registers one negative connection and its immutable network metadata. */
  void openConnection(long connectionId, MapValue info);

  /** Removes one connection record. */
  void closeConnection(long connectionId);

  /** Returns the player currently attached to a connection. */
  OptionalLong connectionPlayer(long connectionId);

  /** Returns attached players in newest-connection-first order. */
  List<Long> connectedPlayers(boolean showAll);

  /** Returns network metadata for a connection object or its attached player. */
  Optional<MapValue> connectionInfo(long objectId);

  /** Resolves a live connection object or its attached player to the connection ID. */
  OptionalLong connectionId(long objectId);

  /** Returns the enabled intrinsic command table for a live connection or attached player. */
  Optional<ListValue> intrinsicCommands(long objectId);

  /** Replaces the intrinsic command table for a live connection or attached player. */
  boolean setIntrinsicCommands(long objectId, ListValue commands);

  /** Stages a player switch on an existing connection. */
  boolean switchConnectionPlayer(long connectionId, long playerId);
}
