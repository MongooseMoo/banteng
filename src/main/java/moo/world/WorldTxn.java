package moo.world;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.StringTokenizer;
import moo.value.MooValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;

/** The concrete transaction path for all runtime-visible world reads and writes. */
public final class WorldTxn implements AutoCloseable {
  private static final int PLAYER_FLAG = 1;
  private static final int PROGRAMMER_FLAG = 2;
  private static final int WIZARD_FLAG = 4;
  private static final ListValue DEFAULT_INTRINSIC_COMMANDS =
      new ListValue(
          List.of(
              new StringValue(".program".getBytes(StandardCharsets.ISO_8859_1)),
              new StringValue("PREFIX".getBytes(StandardCharsets.ISO_8859_1)),
              new StringValue("SUFFIX".getBytes(StandardCharsets.ISO_8859_1)),
              new StringValue("OUTPUTPREFIX".getBytes(StandardCharsets.ISO_8859_1)),
              new StringValue("OUTPUTSUFFIX".getBytes(StandardCharsets.ISO_8859_1))));

  private final Map<Long, Long> connections = new LinkedHashMap<>();
  private final Map<Long, MapValue> connectionInfo = new LinkedHashMap<>();
  private final Map<Long, ListValue> intrinsicCommands = new LinkedHashMap<>();
  private final WorldHistory history;
  private final boolean transaction;
  private final World base;
  private final Set<Long> recordReads = new LinkedHashSet<>();
  private final Set<Long> recordWrites = new LinkedHashSet<>();
  private final Set<ScanPredicate> scanPredicates = new LinkedHashSet<>();
  private final List<MooValue> stagedEffects = new ArrayList<>();
  private World working;
  private boolean playersWritten;
  private boolean completed;

  /** Creates the committed world owner from immutable snapshots of the supplied records. */
  public WorldTxn(List<Long> players, List<WorldObject> objects) {
    history = new WorldHistory(players, objects);
    transaction = false;
    base = history.current();
    working = base;
  }

  private WorldTxn(WorldHistory history, World base) {
    this.history = history;
    transaction = true;
    this.base = base;
    working = base;
  }

  /** Begins one fixed-snapshot transaction against the current committed revision. */
  public WorldTxn begin() {
    ensureRoot();
    return new WorldTxn(history, history.retainCurrent());
  }

  /** Returns an immutable committed snapshot on the root or local snapshot on a transaction. */
  public WorldSnapshot snapshot() {
    return transaction ? working.snapshot() : history.snapshot();
  }

  /** Returns this transaction's fixed base revision. */
  public long baseRevision() {
    ensureActiveTransaction();
    return base.revision().value();
  }

  /** Stages one immutable effect value for publication with this transaction. */
  public void stageEffect(MooValue effect) {
    ensureActiveTransaction();
    stagedEffects.add(Objects.requireNonNull(effect, "effect"));
  }

  /** Atomically validates and publishes this transaction or returns an explicit conflict. */
  public CommitResult commit() {
    ensureActiveTransaction();
    CommitResult result = history.publish(this);
    completed = true;
    history.release(base);
    return result;
  }

  /** Abandons an unpublished transaction and releases its retained revision. */
  @Override
  public void close() {
    if (transaction && !completed) {
      completed = true;
      history.release(base);
    }
  }

  /** Returns the players in persisted order. */
  public List<Long> players() {
    ensureActiveTransaction();
    scanPredicates.add(ScanPredicate.PLAYERS);
    return working.players();
  }

  /** Returns the number of live objects. */
  public int objectCount() {
    ensureActiveTransaction();
    scanPredicates.add(ScanPredicate.OBJECT_IDS);
    return working.objects().size();
  }

  /** Returns the greatest live object number, or {@code -1} when the world is empty. */
  public long maximumObjectId() {
    ensureActiveTransaction();
    scanPredicates.add(ScanPredicate.OBJECT_IDS);
    long maximum = -1;
    for (long objectId : working.objects().keySet()) {
      maximum = Math.max(maximum, objectId);
    }
    return maximum;
  }

  /** Registers one negative pre-login connection object. */
  public void openConnection(long connectionId) {
    openConnection(connectionId, new MapValue(Map.of()));
  }

  /** Registers one negative connection and its immutable network metadata. */
  public void openConnection(long connectionId, MapValue info) {
    ensureActiveTransaction();
    if (connectionId >= 0) {
      throw new IllegalArgumentException("connection object must be negative");
    }
    if (connections.putIfAbsent(connectionId, connectionId) != null) {
      throw new IllegalArgumentException("duplicate connection #" + connectionId);
    }
    connectionInfo.put(connectionId, Objects.requireNonNull(info, "info"));
    intrinsicCommands.put(connectionId, DEFAULT_INTRINSIC_COMMANDS);
  }

  /** Removes one connection record. */
  public void closeConnection(long connectionId) {
    ensureActiveTransaction();
    connections.remove(connectionId);
    connectionInfo.remove(connectionId);
    intrinsicCommands.remove(connectionId);
  }

  /** Returns the player currently attached to a connection. */
  public OptionalLong connectionPlayer(long connectionId) {
    ensureActiveTransaction();
    Long player = connections.get(connectionId);
    return player == null ? OptionalLong.empty() : OptionalLong.of(player);
  }

  /** Returns attached players in newest-connection-first order. */
  public List<Long> connectedPlayers(boolean showAll) {
    ensureActiveTransaction();
    List<Long> players = new ArrayList<>();
    for (long player : connections.values()) {
      if (showAll || player >= 0) {
        players.addFirst(player);
      }
    }
    return List.copyOf(players);
  }

  /** Returns network metadata for a connection object or its attached player. */
  public Optional<MapValue> connectionInfo(long objectId) {
    ensureActiveTransaction();
    if (connections.containsKey(objectId)) {
      return Optional.ofNullable(connectionInfo.get(objectId));
    }
    for (Map.Entry<Long, Long> connection : connections.entrySet()) {
      if (connection.getValue() == objectId) {
        return Optional.ofNullable(connectionInfo.get(connection.getKey()));
      }
    }
    return Optional.empty();
  }

  /** Returns the enabled intrinsic command table for a live connection or attached player. */
  public Optional<ListValue> intrinsicCommands(long objectId) {
    ensureActiveTransaction();
    if (connections.containsKey(objectId)) {
      return Optional.ofNullable(intrinsicCommands.get(objectId));
    }
    for (Map.Entry<Long, Long> connection : connections.entrySet()) {
      if (connection.getValue() == objectId) {
        return Optional.ofNullable(intrinsicCommands.get(connection.getKey()));
      }
    }
    return Optional.empty();
  }

  /** Replaces the intrinsic command table for a live connection or attached player. */
  public boolean setIntrinsicCommands(long objectId, ListValue commands) {
    ensureActiveTransaction();
    Objects.requireNonNull(commands, "commands");
    if (connections.containsKey(objectId)) {
      intrinsicCommands.put(objectId, commands);
      return true;
    }
    for (Map.Entry<Long, Long> connection : connections.entrySet()) {
      if (connection.getValue() == objectId) {
        intrinsicCommands.put(connection.getKey(), commands);
        return true;
      }
    }
    return false;
  }

  /** Restores every intrinsic command for a live connection or attached player. */
  public boolean restoreIntrinsicCommands(long objectId) {
    ensureActiveTransaction();
    return setIntrinsicCommands(objectId, DEFAULT_INTRINSIC_COMMANDS);
  }

  /** Stages a player switch on an existing connection. */
  public boolean switchConnectionPlayer(long connectionId, long playerId) {
    ensureActiveTransaction();
    if (!connections.containsKey(connectionId) || object(playerId).isEmpty()) {
      return false;
    }
    connections.put(connectionId, playerId);
    return true;
  }

  /** Looks up an object by its signed object number. */
  public Optional<WorldObject> object(long objectId) {
    ensureActiveTransaction();
    recordReads.add(objectId);
    return Optional.ofNullable(working.objects().get(objectId));
  }

  /** Looks up a zero-based verb slot on an object. */
  public Optional<WorldVerb> verb(long objectId, int verbIndex) {
    Optional<WorldObject> object = object(objectId);
    if (object.isEmpty() || verbIndex < 0 || verbIndex >= object.get().verbs().size()) {
      return Optional.empty();
    }
    return Optional.of(object.get().verbs().get(verbIndex));
  }

  /** Finds a named verb locally and then through the parent chain. */
  public Optional<WorldVerb> verb(long objectId, String verbName) {
    return verb(objectId, verbName, true);
  }

  /** Finds a named verb, optionally requiring its executable permission. */
  public Optional<WorldVerb> verb(long objectId, String verbName, boolean requireExecutable) {
    Objects.requireNonNull(verbName, "verbName");
    String requestedName = verbName.toLowerCase(Locale.ROOT);
    long current = objectId;
    while (current != -1) {
      Optional<WorldObject> candidate = object(current);
      if (candidate.isEmpty()) {
        return Optional.empty();
      }
      for (WorldVerb verb : candidate.orElseThrow().verbs()) {
        StringTokenizer names = new StringTokenizer(verb.names());
        while (names.hasMoreTokens()) {
          String pattern = names.nextToken().toLowerCase(Locale.ROOT);
          int wildcard = pattern.indexOf('*');
          boolean matches;
          if (wildcard < 0) {
            matches = pattern.equals(requestedName);
          } else if (pattern.equals("*")) {
            matches = true;
          } else if (wildcard == pattern.length() - 1) {
            matches = requestedName.startsWith(pattern.substring(0, wildcard));
          } else {
            String requiredPrefix = pattern.substring(0, wildcard);
            String fullName = requiredPrefix + pattern.substring(wildcard + 1);
            matches =
                requestedName.startsWith(requiredPrefix) && fullName.startsWith(requestedName);
          }
          if (matches && (!requireExecutable || (verb.permissions() & 4) != 0)) {
            return Optional.of(verb);
          }
        }
      }
      current = candidate.orElseThrow().parent();
    }
    return Optional.empty();
  }

  /** Looks up a local or inherited property name. */
  public Optional<WorldProperty> property(long objectId, String propertyName) {
    Objects.requireNonNull(propertyName, "propertyName");
    long current = objectId;
    while (current != -1) {
      Optional<WorldObject> candidate = object(current);
      if (candidate.isEmpty()) {
        return Optional.empty();
      }
      Optional<WorldProperty> property = findProperty(candidate.orElseThrow(), propertyName);
      if (property.isPresent()) {
        return property;
      }
      current = candidate.orElseThrow().parent();
    }
    return Optional.empty();
  }

  /** Reads an ordinary or built-in object property. */
  public Optional<MooValue> readObjectProperty(long objectId, String propertyName) {
    Optional<WorldObject> candidate = object(objectId);
    if (candidate.isEmpty()) {
      return Optional.empty();
    }
    WorldObject object = candidate.orElseThrow();
    return switch (propertyName.toLowerCase(Locale.ROOT)) {
      case "name" ->
          Optional.of(new StringValue(object.name().getBytes(StandardCharsets.ISO_8859_1)));
      case "location" -> Optional.of(new ObjectValue(object.location()));
      case "owner" -> Optional.of(new ObjectValue(object.owner()));
      case "programmer" ->
          Optional.of(new IntegerValue((object.flags() & PROGRAMMER_FLAG) == 0 ? 0 : 1));
      case "wizard" -> Optional.of(new IntegerValue((object.flags() & WIZARD_FLAG) == 0 ? 0 : 1));
      default -> property(objectId, propertyName).map(WorldProperty::value);
    };
  }

  /** Writes an authorized built-in object property and returns whether it exists. */
  public boolean writeObjectProperty(long objectId, String propertyName, MooValue value) {
    Objects.requireNonNull(propertyName, "propertyName");
    Objects.requireNonNull(value, "value");
    WorldObject object = object(objectId).orElse(null);
    if (object == null) {
      return false;
    }
    String normalizedName = propertyName.toLowerCase(Locale.ROOT);
    if (normalizedName.equals("name")) {
      if (!(value instanceof StringValue name)) {
        return false;
      }
      replaceObject(
          new WorldObject(
              object.id(),
              new String(name.bytes(), StandardCharsets.ISO_8859_1),
              object.flags(),
              object.owner(),
              object.location(),
              object.parent(),
              object.contents(),
              object.children(),
              object.verbs(),
              object.properties()));
      return true;
    }
    if (normalizedName.equals("owner")) {
      if (!(value instanceof ObjectValue owner)) {
        return false;
      }
      replaceObject(
          copyObject(
              object, object.flags(), owner.value(), object.location(), object.properties()));
      return true;
    }
    if (normalizedName.equals("programmer")) {
      if (!(value instanceof IntegerValue enabled)) {
        return false;
      }
      replaceFlags(object, PROGRAMMER_FLAG, enabled.isTruthy());
      return true;
    }
    if (normalizedName.equals("wizard")) {
      if (!(value instanceof IntegerValue enabled)) {
        return false;
      }
      replaceFlags(object, WIZARD_FLAG, enabled.isTruthy());
      return true;
    }
    if (normalizedName.equals("w")) {
      if (!(value instanceof IntegerValue enabled)) {
        return false;
      }
      replaceFlags(object, 32, enabled.isTruthy());
      return true;
    }
    if (normalizedName.equals("f")) {
      if (!(value instanceof IntegerValue enabled)) {
        return false;
      }
      replaceFlags(object, 128, enabled.isTruthy());
      return true;
    }
    List<WorldProperty> properties = new ArrayList<>(object.properties());
    for (int index = 0; index < properties.size(); index++) {
      WorldProperty property = properties.get(index);
      if (property.name().equalsIgnoreCase(propertyName)) {
        properties.set(
            index,
            new WorldProperty(property.name(), value, property.owner(), property.permissions()));
        replaceObject(
            copyObject(object, object.flags(), object.owner(), object.location(), properties));
        return true;
      }
    }
    return false;
  }

  /** Allocates the next object number and returns the new object. */
  public WorldObject createObject(long parentId, long ownerId) {
    if (parentId != -1 && object(parentId).isEmpty()) {
      throw new IllegalArgumentException("missing parent #" + parentId);
    }
    scanPredicates.add(ScanPredicate.OBJECT_IDS);
    long objectId = -1;
    for (long existing : working.objects().keySet()) {
      objectId = Math.max(objectId, existing);
    }
    objectId = Math.incrementExact(objectId);
    WorldObject created =
        new WorldObject(
            objectId, "", 0, ownerId, -1, parentId, List.of(), List.of(), List.of(), List.of());
    replaceObject(created);
    if (parentId != -1) {
      WorldObject parent = object(parentId).orElseThrow();
      List<Long> children = new ArrayList<>(parent.children());
      children.add(objectId);
      replaceObject(
          new WorldObject(
              parent.id(),
              parent.name(),
              parent.flags(),
              parent.owner(),
              parent.location(),
              parent.parent(),
              parent.contents(),
              children,
              parent.verbs(),
              parent.properties()));
    }
    return created;
  }

  /** Removes one object from the current immutable world snapshot. */
  public boolean recycleObject(long objectId) {
    WorldObject target = object(objectId).orElse(null);
    if (target == null) {
      return false;
    }
    Map<Long, WorldObject> objects = new LinkedHashMap<>(working.objects());

    if (target.location() != -1) {
      recordReads.add(target.location());
      WorldObject location = objects.get(target.location());
      if (location != null) {
        List<Long> contents = new ArrayList<>(location.contents());
        contents.remove(objectId);
        objects.put(
            location.id(),
            new WorldObject(
                location.id(),
                location.name(),
                location.flags(),
                location.owner(),
                location.location(),
                location.parent(),
                contents,
                location.children(),
                location.verbs(),
                location.properties()));
      }
    }

    for (long contentId : target.contents()) {
      recordReads.add(contentId);
      WorldObject content = objects.get(contentId);
      if (content != null && content.id() != objectId) {
        objects.put(
            content.id(),
            new WorldObject(
                content.id(),
                content.name(),
                content.flags(),
                content.owner(),
                -1,
                content.parent(),
                content.contents(),
                content.children(),
                content.verbs(),
                content.properties()));
      }
    }

    for (long childId : target.children()) {
      recordReads.add(childId);
      WorldObject child = objects.get(childId);
      if (child != null && child.id() != objectId) {
        objects.put(
            child.id(),
            new WorldObject(
                child.id(),
                child.name(),
                child.flags(),
                child.owner(),
                child.location(),
                target.parent(),
                child.contents(),
                child.children(),
                child.verbs(),
                child.properties()));
      }
    }

    if (target.parent() != -1) {
      recordReads.add(target.parent());
      WorldObject parent = objects.get(target.parent());
      if (parent != null) {
        List<Long> children = new ArrayList<>();
        for (long childId : parent.children()) {
          if (childId == objectId) {
            children.addAll(target.children());
          } else {
            children.add(childId);
          }
        }
        objects.put(
            parent.id(),
            new WorldObject(
                parent.id(),
                parent.name(),
                parent.flags(),
                parent.owner(),
                parent.location(),
                parent.parent(),
                parent.contents(),
                children,
                parent.verbs(),
                parent.properties()));
      }
    }

    objects.remove(objectId);
    List<Long> players = new ArrayList<>(working.players());
    players.remove(objectId);
    replaceWorld(players, objects);
    return true;
  }

  /** Changes one object's parent while updating both reciprocal topology records. */
  public boolean changeParent(long objectId, long newParentId) {
    WorldObject target = object(objectId).orElse(null);
    WorldObject newParent = newParentId == -1 ? null : object(newParentId).orElse(null);
    if (target == null
        || newParentId < -1
        || objectId == newParentId
        || (newParentId != -1 && newParent == null)
        || (target.parent() != -1 && object(target.parent()).isEmpty())) {
      return false;
    }

    List<Long> visited = new ArrayList<>();
    long ancestor = newParentId;
    while (ancestor != -1) {
      if (ancestor == objectId || visited.contains(ancestor)) {
        return false;
      }
      visited.add(ancestor);
      WorldObject ancestorObject = object(ancestor).orElse(null);
      if (ancestorObject == null) {
        return false;
      }
      ancestor = ancestorObject.parent();
    }

    Map<Long, WorldObject> objects = new LinkedHashMap<>(working.objects());
    if (target.parent() != -1) {
      WorldObject oldParent = Objects.requireNonNull(objects.get(target.parent()));
      List<Long> oldChildren = new ArrayList<>();
      for (long child : oldParent.children()) {
        if (child != objectId) {
          oldChildren.add(child);
        }
      }
      objects.put(
          oldParent.id(),
          new WorldObject(
              oldParent.id(),
              oldParent.name(),
              oldParent.flags(),
              oldParent.owner(),
              oldParent.location(),
              oldParent.parent(),
              oldParent.contents(),
              oldChildren,
              oldParent.verbs(),
              oldParent.properties()));
    }

    if (newParentId != -1) {
      WorldObject currentNewParent = Objects.requireNonNull(objects.get(newParentId));
      List<Long> newChildren = new ArrayList<>();
      for (long child : currentNewParent.children()) {
        if (child != objectId) {
          newChildren.add(child);
        }
      }
      newChildren.add(objectId);
      objects.put(
          currentNewParent.id(),
          new WorldObject(
              currentNewParent.id(),
              currentNewParent.name(),
              currentNewParent.flags(),
              currentNewParent.owner(),
              currentNewParent.location(),
              currentNewParent.parent(),
              currentNewParent.contents(),
              newChildren,
              currentNewParent.verbs(),
              currentNewParent.properties()));
    }

    objects.put(
        objectId,
        new WorldObject(
            target.id(),
            target.name(),
            target.flags(),
            target.owner(),
            target.location(),
            newParentId,
            target.contents(),
            target.children(),
            target.verbs(),
            target.properties()));
    replaceWorld(working.players(), objects);
    return true;
  }

  /** Adds or removes the player flag and keeps the player index in the same transaction. */
  public boolean setPlayerFlag(long objectId, boolean enabled) {
    WorldObject object = object(objectId).orElse(null);
    if (object == null) {
      return false;
    }
    replaceFlags(object, PLAYER_FLAG, enabled);
    List<Long> players = new ArrayList<>(working.players());
    if (enabled && !players.contains(objectId)) {
      players.add(objectId);
    } else if (!enabled) {
      players.remove(objectId);
    }
    replaceWorld(players, working.objects());
    return true;
  }

  /** Moves an object while updating both reciprocal topology records. */
  public boolean move(long objectId, long destinationId) {
    WorldObject object = object(objectId).orElse(null);
    WorldObject destination = object(destinationId).orElse(null);
    if (object == null || destination == null) {
      return false;
    }
    if (object.location() != -1) {
      WorldObject previous = object(object.location()).orElseThrow();
      List<Long> previousContents = new ArrayList<>(previous.contents());
      previousContents.remove(objectId);
      replaceObject(copyContents(previous, previousContents));
    }
    List<Long> destinationContents = new ArrayList<>(destination.contents());
    destinationContents.add(objectId);
    replaceObject(copyContents(destination, destinationContents));
    replaceObject(
        copyObject(object, object.flags(), object.owner(), destinationId, object.properties()));
    return true;
  }

  /** Adds one local property, rejecting a duplicate inherited or local name. */
  public boolean addProperty(
      long objectId, String name, MooValue value, long owner, int permissions) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    WorldObject object = object(objectId).orElse(null);
    if (object == null || property(objectId, name).isPresent()) {
      return false;
    }
    List<WorldProperty> properties = new ArrayList<>(object.properties());
    properties.add(new WorldProperty(name, value, owner, permissions));
    replaceObject(
        copyObject(object, object.flags(), object.owner(), object.location(), properties));
    return true;
  }

  /** Deletes one property definition local to the exact object. */
  public boolean deleteProperty(long objectId, String name) {
    Objects.requireNonNull(name, "name");
    WorldObject object = object(objectId).orElse(null);
    if (object == null) {
      return false;
    }
    List<WorldProperty> properties = new ArrayList<>(object.properties());
    boolean removed = properties.removeIf(property -> property.name().equalsIgnoreCase(name));
    if (!removed) {
      return false;
    }
    replaceObject(
        copyObject(object, object.flags(), object.owner(), object.location(), properties));
    return true;
  }

  /** Adds one local verb using the existing immutable verb record. */
  public int addVerb(long objectId, String names, long owner, int permissions, int preposition) {
    Objects.requireNonNull(names, "names");
    WorldObject object = object(objectId).orElse(null);
    if (object == null) {
      return 0;
    }
    List<WorldVerb> verbs = new ArrayList<>(object.verbs());
    verbs.add(new WorldVerb(names, owner, permissions, preposition, ""));
    replaceObject(
        new WorldObject(
            object.id(),
            object.name(),
            object.flags(),
            object.owner(),
            object.location(),
            object.parent(),
            object.contents(),
            object.children(),
            verbs,
            object.properties()));
    return verbs.size();
  }

  /** Deletes one resolved zero-based local verb from the immutable object record. */
  public boolean deleteVerb(long objectId, int verbIndex) {
    WorldObject object = object(objectId).orElse(null);
    if (object == null || verbIndex < 0 || verbIndex >= object.verbs().size()) {
      return false;
    }
    List<WorldVerb> verbs = new ArrayList<>(object.verbs());
    verbs.remove(verbIndex);
    replaceObject(
        new WorldObject(
            object.id(),
            object.name(),
            object.flags(),
            object.owner(),
            object.location(),
            object.parent(),
            object.contents(),
            object.children(),
            verbs,
            object.properties()));
    return true;
  }

  /** Replaces the source of one resolved zero-based local verb. */
  public boolean setVerbCode(long objectId, int verbIndex, String programSource) {
    Objects.requireNonNull(programSource, "programSource");
    WorldObject object = object(objectId).orElse(null);
    if (object == null || verbIndex < 0 || verbIndex >= object.verbs().size()) {
      return false;
    }
    List<WorldVerb> verbs = new ArrayList<>(object.verbs());
    WorldVerb verb = verbs.get(verbIndex);
    verbs.set(
        verbIndex,
        new WorldVerb(
            verb.names(), verb.owner(), verb.permissions(), verb.preposition(), programSource));
    replaceObject(
        new WorldObject(
            object.id(),
            object.name(),
            object.flags(),
            object.owner(),
            object.location(),
            object.parent(),
            object.contents(),
            object.children(),
            verbs,
            object.properties()));
    return true;
  }

  /** Replaces the information fields of one resolved zero-based local verb. */
  public boolean setVerbInfo(
      long objectId, int verbIndex, String names, long owner, int permissions) {
    Objects.requireNonNull(names, "names");
    WorldObject object = object(objectId).orElse(null);
    if (object == null || verbIndex < 0 || verbIndex >= object.verbs().size()) {
      return false;
    }
    List<WorldVerb> verbs = new ArrayList<>(object.verbs());
    WorldVerb verb = verbs.get(verbIndex);
    verbs.set(
        verbIndex,
        new WorldVerb(
            names,
            owner,
            (verb.permissions() & ~15) | permissions,
            verb.preposition(),
            verb.programSource()));
    replaceObject(
        new WorldObject(
            object.id(),
            object.name(),
            object.flags(),
            object.owner(),
            object.location(),
            object.parent(),
            object.contents(),
            object.children(),
            verbs,
            object.properties()));
    return true;
  }

  /** Replaces the argument fields of one resolved zero-based local verb. */
  public boolean setVerbArgs(
      long objectId, int verbIndex, int direct, int preposition, int indirect) {
    WorldObject object = object(objectId).orElse(null);
    if (object == null || verbIndex < 0 || verbIndex >= object.verbs().size()) {
      return false;
    }
    List<WorldVerb> verbs = new ArrayList<>(object.verbs());
    WorldVerb verb = verbs.get(verbIndex);
    verbs.set(
        verbIndex,
        new WorldVerb(
            verb.names(),
            verb.owner(),
            (verb.permissions() & 15) | (direct << 4) | (indirect << 6),
            preposition,
            verb.programSource()));
    replaceObject(
        new WorldObject(
            object.id(),
            object.name(),
            object.flags(),
            object.owner(),
            object.location(),
            object.parent(),
            object.contents(),
            object.children(),
            verbs,
            object.properties()));
    return true;
  }

  private static Optional<WorldProperty> findProperty(WorldObject object, String propertyName) {
    for (WorldProperty property : object.properties()) {
      if (property.name().equalsIgnoreCase(propertyName)) {
        return Optional.of(property);
      }
    }
    return Optional.empty();
  }

  private void replaceFlags(WorldObject object, int flag, boolean enabled) {
    int flags = enabled ? object.flags() | flag : object.flags() & ~flag;
    replaceObject(
        copyObject(object, flags, object.owner(), object.location(), object.properties()));
  }

  private void replaceObject(WorldObject replacement) {
    Map<Long, WorldObject> objects = new LinkedHashMap<>(working.objects());
    objects.put(replacement.id(), replacement);
    replaceWorld(working.players(), objects);
  }

  private void replaceWorld(List<Long> players, Map<Long, WorldObject> objects) {
    ensureActiveTransaction();
    World replacement = new World(base.revision(), players, objects);
    Set<Long> candidates = new LinkedHashSet<>(working.objects().keySet());
    candidates.addAll(replacement.objects().keySet());
    for (long objectId : candidates) {
      if (!Objects.equals(working.objects().get(objectId), replacement.objects().get(objectId))) {
        if (Objects.equals(base.objects().get(objectId), replacement.objects().get(objectId))) {
          recordWrites.remove(objectId);
        } else {
          recordWrites.add(objectId);
        }
      }
    }
    playersWritten = !base.players().equals(replacement.players());
    working = replacement;
  }

  World baseWorld() {
    return base;
  }

  World workingWorld() {
    return working;
  }

  Set<Long> recordReads() {
    return Collections.unmodifiableSet(recordReads);
  }

  Set<Long> recordWrites() {
    return Collections.unmodifiableSet(recordWrites);
  }

  Set<ScanPredicate> scanPredicates() {
    return Collections.unmodifiableSet(scanPredicates);
  }

  boolean playersWritten() {
    return playersWritten;
  }

  List<MooValue> stagedEffects() {
    return List.copyOf(stagedEffects);
  }

  int retainedRevisionCount() {
    ensureRoot();
    return history.retainedRevisionCount();
  }

  List<Long> retainedRevisions() {
    ensureRoot();
    return history.retainedRevisions();
  }

  private void ensureRoot() {
    if (transaction) {
      throw new IllegalStateException("operation requires committed world owner");
    }
  }

  private void ensureActiveTransaction() {
    if (!transaction) {
      throw new IllegalStateException("begin a transaction before reading or writing world state");
    }
    if (completed) {
      throw new IllegalStateException("transaction is already complete");
    }
  }

  private static WorldObject copyContents(WorldObject object, List<Long> contents) {
    return new WorldObject(
        object.id(),
        object.name(),
        object.flags(),
        object.owner(),
        object.location(),
        object.parent(),
        contents,
        object.children(),
        object.verbs(),
        object.properties());
  }

  private static WorldObject copyObject(
      WorldObject object, int flags, long owner, long location, List<WorldProperty> properties) {
    return new WorldObject(
        object.id(),
        object.name(),
        flags,
        owner,
        location,
        object.parent(),
        object.contents(),
        object.children(),
        object.verbs(),
        properties);
  }

  /** Closed scan predicates recorded for validation against a fixed snapshot. */
  public enum ScanPredicate {
    OBJECT_IDS,
    PLAYERS
  }

  /** The explicit result of one atomic validation and publication attempt. */
  public record CommitResult(
      Status status,
      long revision,
      Set<Long> conflictingRecords,
      Set<ScanPredicate> conflictingPredicates,
      List<MooValue> effects) {
    /** Takes immutable copies of conflict evidence and published effects. */
    public CommitResult {
      Objects.requireNonNull(status, "status");
      conflictingRecords = Collections.unmodifiableSet(new LinkedHashSet<>(conflictingRecords));
      conflictingPredicates =
          Collections.unmodifiableSet(new LinkedHashSet<>(conflictingPredicates));
      effects = List.copyOf(effects);
    }

    static CommitResult committed(long revision, List<MooValue> effects) {
      return new CommitResult(Status.COMMITTED, revision, Set.of(), Set.of(), effects);
    }

    static CommitResult conflict(
        long revision,
        Set<Long> conflictingRecords,
        Set<ScanPredicate> conflictingPredicates) {
      return new CommitResult(
          Status.CONFLICT, revision, conflictingRecords, conflictingPredicates, List.of());
    }

    /** Returns whether this transaction published its records and effects. */
    public boolean isCommitted() {
      return status == Status.COMMITTED;
    }
  }

  /** Terminal publication outcomes. */
  public enum Status {
    COMMITTED,
    CONFLICT
  }
}
