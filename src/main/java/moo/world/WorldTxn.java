package moo.world;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
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
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;

/** The concrete transaction path for all runtime-visible world reads and writes. */
public final class WorldTxn implements AutoCloseable {
  private static final int PLAYER_FLAG = 1;
  private static final int PROGRAMMER_FLAG = 2;
  private static final int WIZARD_FLAG = 4;
  private static final int ANONYMOUS_FLAG = 1 << 8;
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
  private final Set<AnonymousObjectValue> anonymousReads = new LinkedHashSet<>();
  private final Set<AnonymousObjectValue> anonymousWrites = new LinkedHashSet<>();
  private final Set<WaifValue> waifWrites = new LinkedHashSet<>();
  private final Set<ScanPredicate> scanPredicates = new LinkedHashSet<>();
  private final List<MooValue> stagedEffects = new ArrayList<>();
  private World working;
  private boolean playersWritten;
  private boolean pendingFinalizationWritten;
  private boolean completed;

  /** Creates the committed world owner from immutable snapshots of the supplied records. */
  public WorldTxn(List<Long> players, List<WorldObject> objects) {
    this(players, objects, Map.of(), Map.of(), List.of());
  }

  /** Creates the committed world owner with permanent and anonymous records. */
  public WorldTxn(
      List<Long> players,
      List<WorldObject> objects,
      Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects) {
    this(players, objects, anonymousObjects, Map.of(), List.of());
  }

  /** Creates the committed world owner with permanent, anonymous, and WAIF records. */
  public WorldTxn(
      List<Long> players,
      List<WorldObject> objects,
      Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects,
      Map<WaifValue, WorldWaif> waifs) {
    this(players, objects, anonymousObjects, waifs, List.of());
  }

  /** Creates the committed world owner with every v17 world-state section. */
  public WorldTxn(
      List<Long> players,
      List<WorldObject> objects,
      Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects,
      Map<WaifValue, WorldWaif> waifs,
      List<MooValue> pendingFinalization) {
    this(
        players,
        objects,
        anonymousObjects,
        waifs,
        pendingFinalization,
        greatestObjectId(objects));
  }

  /** Creates the committed world owner with an explicit durable object-number boundary. */
  public WorldTxn(
      List<Long> players,
      List<WorldObject> objects,
      Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects,
      Map<WaifValue, WorldWaif> waifs,
      List<MooValue> pendingFinalization,
      long lastUsedObjectId) {
    history =
        new WorldHistory(
            players,
            objects,
            anonymousObjects,
            waifs,
            pendingFinalization,
            lastUsedObjectId);
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

  /** Retains one committed revision until a checkpoint writer closes the returned lease. */
  public RetainedSnapshot retainSnapshot() {
    ensureRoot();
    return new RetainedSnapshot(history, history.retainCurrent());
  }

  /** Returns this transaction's ordered values pending finalization. */
  public List<MooValue> pendingFinalization() {
    ensureActiveTransaction();
    scanPredicates.add(ScanPredicate.PENDING_FINALIZATION);
    return working.pendingFinalization();
  }

  /** Replaces the ordered pending-finalization roots in this transaction. */
  public void replacePendingFinalization(List<MooValue> values) {
    ensureActiveTransaction();
    List<MooValue> replacement = List.copyOf(values);
    working =
        new World(
            base.revision(),
            working.players(),
            working.objects(),
            working.lastUsedObjectId(),
            working.anonymousObjects(),
            working.waifs(),
            replacement);
    pendingFinalizationWritten = !base.pendingFinalization().equals(replacement);
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

  /** Validates this transaction's exact read, write, and predicate footprint without publishing. */
  public ValidationResult validate() {
    ensureActiveTransaction();
    return history.validate(this);
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

  /** Returns Toast's durable last-used object number, including recycled slots. */
  public long maximumObjectId() {
    ensureActiveTransaction();
    scanPredicates.add(ScanPredicate.OBJECT_IDS);
    return working.lastUsedObjectId();
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

  /** Resolves a live connection object or its attached player to the connection ID. */
  public OptionalLong connectionId(long objectId) {
    ensureActiveTransaction();
    if (connections.containsKey(objectId)) {
      return OptionalLong.of(objectId);
    }
    for (Map.Entry<Long, Long> connection : connections.entrySet()) {
      if (connection.getValue() == objectId) {
        return OptionalLong.of(connection.getKey());
      }
    }
    return OptionalLong.empty();
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

  /** Returns one anonymous object body by reference identity. */
  public Optional<WorldAnonymousObject> anonymousObject(AnonymousObjectValue identity) {
    ensureActiveTransaction();
    AnonymousObjectValue requested = Objects.requireNonNull(identity, "identity");
    anonymousReads.add(requested);
    return Optional.ofNullable(working.anonymousObjects().get(requested));
  }

  /** Removes one anonymous body while preserving the identity of every other body. */
  public boolean removeAnonymousObject(AnonymousObjectValue identity) {
    ensureActiveTransaction();
    Objects.requireNonNull(identity, "identity");
    if (!working.anonymousObjects().containsKey(identity)) {
      return false;
    }
    Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects =
        new LinkedHashMap<>(working.anonymousObjects());
    anonymousObjects.remove(identity);
    World next =
        new World(
            base.revision(),
            working.players(),
            working.objects(),
            working.lastUsedObjectId(),
            anonymousObjects,
            working.waifs(),
            working.pendingFinalization());
    anonymousWrites.add(identity);
    working = next;
    return true;
  }

  /** Returns one WAIF body by reference identity. */
  public Optional<WorldWaif> waif(WaifValue identity) {
    ensureActiveTransaction();
    return Optional.ofNullable(working.waifs().get(Objects.requireNonNull(identity, "identity")));
  }

  /** Removes one WAIF body while preserving the identity of every other body. */
  public boolean removeWaif(WaifValue identity) {
    ensureActiveTransaction();
    Objects.requireNonNull(identity, "identity");
    if (!working.waifs().containsKey(identity)) {
      return false;
    }
    Map<WaifValue, WorldWaif> waifs = new LinkedHashMap<>(working.waifs());
    waifs.remove(identity);
    World next =
        new World(
            base.revision(),
            working.players(),
            working.objects(),
            working.lastUsedObjectId(),
            working.anonymousObjects(),
            waifs,
            working.pendingFinalization());
    waifWrites.add(identity);
    working = next;
    return true;
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
    if (requireExecutable) {
      return resolvedCallableVerb(history.findCallableVerb(this, objectId, verbName));
    }
    String requestedName = verbName.toLowerCase(Locale.ROOT);
    for (long ancestor : ancestry(objectId)) {
      WorldObject candidate = object(ancestor).orElseThrow();
      for (WorldVerb verb : candidate.verbs()) {
        if (matchesVerbName(verb, requestedName)) {
          return Optional.of(verb);
        }
      }
    }
    return Optional.empty();
  }

  /** Returns the defining object selected by ordered verb lookup. */
  public OptionalLong verbLocation(
      long objectId, String verbName, boolean requireExecutable) {
    Objects.requireNonNull(verbName, "verbName");
    if (requireExecutable) {
      VerbCache.Resolution resolution = history.findCallableVerb(this, objectId, verbName);
      replayVerbReads(resolution);
      return resolution.location() instanceof Long location
          ? OptionalLong.of(location)
          : OptionalLong.empty();
    }
    String requestedName = verbName.toLowerCase(Locale.ROOT);
    for (long ancestor : ancestry(objectId)) {
      WorldObject candidate = object(ancestor).orElseThrow();
      for (WorldVerb verb : candidate.verbs()) {
        if (matchesVerbName(verb, requestedName)) {
          return OptionalLong.of(ancestor);
        }
      }
    }
    return OptionalLong.empty();
  }

  /** Returns this object followed by its depth-first, first-visit ordered ancestors. */
  public List<Long> ancestry(long objectId) {
    if (object(objectId).isEmpty()) {
      return List.of();
    }
    List<Long> result = new ArrayList<>();
    collectAncestry(objectId, new LinkedHashSet<>(), result);
    return List.copyOf(result);
  }

  private void collectAncestry(long objectId, Set<Long> visited, List<Long> result) {
    if (!visited.add(objectId)) {
      return;
    }
    WorldObject object = object(objectId).orElseThrow();
    result.add(objectId);
    for (long parentId : object.parents()) {
      collectAncestry(parentId, visited, result);
    }
  }

  /** Finds a named verb on an anonymous body and then through its permanent parent chain. */
  public Optional<WorldVerb> verb(
      AnonymousObjectValue identity, String verbName, boolean requireExecutable) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(verbName, "verbName");
    if (requireExecutable) {
      return resolvedCallableVerb(history.findCallableVerb(this, identity, verbName));
    }
    String requestedName = verbName.toLowerCase(Locale.ROOT);
    WorldAnonymousObject anonymous = anonymousObject(identity).orElse(null);
    if (anonymous == null) {
      return Optional.empty();
    }
    for (WorldVerb verb : anonymous.verbs()) {
      if (matchesVerbName(verb, requestedName)) {
        return Optional.of(verb);
      }
    }
    Set<Long> visited = new LinkedHashSet<>();
    for (long parentId : anonymous.parents()) {
      for (long ancestor : ancestry(parentId)) {
        if (!visited.add(ancestor)) {
          continue;
        }
        WorldObject candidate = object(ancestor).orElseThrow();
        for (WorldVerb verb : candidate.verbs()) {
          if (matchesVerbName(verb, requestedName)) {
            return Optional.of(verb);
          }
        }
      }
    }
    return Optional.empty();
  }

  /** Returns the anonymous body or permanent ancestor defining the selected verb. */
  public Optional<MooValue> verbLocation(
      AnonymousObjectValue identity, String verbName, boolean requireExecutable) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(verbName, "verbName");
    if (requireExecutable) {
      VerbCache.Resolution resolution = history.findCallableVerb(this, identity, verbName);
      replayVerbReads(resolution);
      return Optional.ofNullable(
          resolution.location() instanceof Long location
              ? new ObjectValue(location)
              : (MooValue) resolution.location());
    }
    String requestedName = verbName.toLowerCase(Locale.ROOT);
    WorldAnonymousObject anonymous = anonymousObject(identity).orElse(null);
    if (anonymous == null) {
      return Optional.empty();
    }
    for (WorldVerb verb : anonymous.verbs()) {
      if (matchesVerbName(verb, requestedName)) {
        return Optional.of(identity);
      }
    }
    Set<Long> visited = new LinkedHashSet<>();
    for (long parentId : anonymous.parents()) {
      for (long ancestor : ancestry(parentId)) {
        if (!visited.add(ancestor)) {
          continue;
        }
        WorldObject candidate = object(ancestor).orElseThrow();
        for (WorldVerb verb : candidate.verbs()) {
          if (matchesVerbName(verb, requestedName)) {
            return Optional.of(new ObjectValue(ancestor));
          }
        }
      }
    }
    return Optional.empty();
  }

  private static boolean matchesVerbName(WorldVerb verb, String requestedName) {
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
        matches = requestedName.startsWith(requiredPrefix) && fullName.startsWith(requestedName);
      }
      if (matches) {
        return true;
      }
    }
    return false;
  }

  /** Returns one immutable snapshot of the shared callable-verb cache counters. */
  public VerbCacheStats verbCacheStats() {
    return history.verbCacheStats();
  }

  VerbCache.Resolution findCallableVerbCached(
      Object receiver, String verbName, VerbCache cache) {
    String requestedName = verbName.toLowerCase(Locale.ROOT);
    Deque<Object> pending = new ArrayDeque<>();
    pending.addFirst(receiver);
    while (!pending.isEmpty()) {
      Object candidate = pending.removeFirst();
      Optional<List<WorldVerb>> candidateVerbs = receiverVerbs(candidate);
      if (candidateVerbs.isEmpty()) {
        continue;
      }
      List<WorldVerb> verbs = candidateVerbs.orElseThrow();
      if (verbs.isEmpty()) {
        prependParents(pending, receiverParents(candidate));
        continue;
      }
      VerbCache.Resolution resolution =
          cache.resolve(
              candidate,
              requestedName,
              () -> findCallableVerbFrom(candidate, requestedName));
      replayVerbReads(resolution);
      if (resolution.found()) {
        return resolution;
      }
    }
    return VerbCache.Resolution.missing(List.of(), List.of());
  }

  VerbCache.Resolution findCallableVerbUncached(Object receiver, String verbName) {
    return findCallableVerbFrom(receiver, verbName.toLowerCase(Locale.ROOT));
  }

  private VerbCache.Resolution findCallableVerbFrom(Object receiver, String requestedName) {
    List<Long> permanent = new ArrayList<>();
    List<AnonymousObjectValue> anonymous = new ArrayList<>();
    Deque<Object> pending = new ArrayDeque<>();
    pending.addFirst(receiver);
    while (!pending.isEmpty()) {
      Object candidate = pending.removeFirst();
      List<WorldVerb> verbs;
      List<Long> parents;
      if (candidate instanceof Long objectId) {
        permanent.add(objectId);
        WorldObject object = object(objectId).orElse(null);
        if (object == null) {
          continue;
        }
        verbs = object.verbs();
        parents = object.parents();
      } else if (candidate instanceof AnonymousObjectValue identity) {
        anonymous.add(identity);
        WorldAnonymousObject object = anonymousObject(identity).orElse(null);
        if (object == null) {
          continue;
        }
        verbs = object.verbs();
        parents = object.parents();
      } else {
        throw new IllegalArgumentException("unsupported verb receiver");
      }
      for (int index = 0; index < verbs.size(); index++) {
        WorldVerb verb = verbs.get(index);
        if (matchesVerbName(verb, requestedName) && (verb.permissions() & 4) != 0) {
          return new VerbCache.Resolution(candidate, index, permanent, anonymous);
        }
      }
      prependParents(pending, parents);
    }
    return VerbCache.Resolution.missing(permanent, anonymous);
  }

  private Optional<List<WorldVerb>> receiverVerbs(Object receiver) {
    if (receiver instanceof Long objectId) {
      return object(objectId).map(WorldObject::verbs);
    }
    if (receiver instanceof AnonymousObjectValue identity) {
      return anonymousObject(identity).map(WorldAnonymousObject::verbs);
    }
    throw new IllegalArgumentException("unsupported verb receiver");
  }

  private List<Long> receiverParents(Object receiver) {
    if (receiver instanceof Long objectId) {
      return object(objectId).map(WorldObject::parents).orElse(List.of());
    }
    if (receiver instanceof AnonymousObjectValue identity) {
      return anonymousObject(identity).map(WorldAnonymousObject::parents).orElse(List.of());
    }
    throw new IllegalArgumentException("unsupported verb receiver");
  }

  private static void prependParents(Deque<Object> pending, List<Long> parents) {
    for (int index = parents.size() - 1; index >= 0; index--) {
      pending.addFirst(parents.get(index));
    }
  }

  private Optional<WorldVerb> resolvedCallableVerb(VerbCache.Resolution resolution) {
    replayVerbReads(resolution);
    if (resolution.location() instanceof Long objectId) {
      return verb(objectId, resolution.verbIndex());
    }
    if (resolution.location() instanceof AnonymousObjectValue identity) {
      WorldAnonymousObject object = anonymousObject(identity).orElse(null);
      if (object == null
          || resolution.verbIndex() < 0
          || resolution.verbIndex() >= object.verbs().size()) {
        return Optional.empty();
      }
      return Optional.of(object.verbs().get(resolution.verbIndex()));
    }
    return Optional.empty();
  }

  private void replayVerbReads(VerbCache.Resolution resolution) {
    recordReads.addAll(resolution.permanentReads());
    anonymousReads.addAll(resolution.anonymousReads());
  }

  /** Looks up a local or inherited property name. */
  public Optional<WorldProperty> property(long objectId, String propertyName) {
    Objects.requireNonNull(propertyName, "propertyName");
    for (long ancestor : ancestry(objectId)) {
      Optional<WorldProperty> property =
          findProperty(object(ancestor).orElseThrow(), propertyName);
      if (property.isPresent()) {
        return property;
      }
    }
    return Optional.empty();
  }

  /** Looks up a local or inherited property on an anonymous object. */
  public Optional<WorldProperty> property(
      AnonymousObjectValue identity, String propertyName) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(propertyName, "propertyName");
    WorldAnonymousObject object = anonymousObject(identity).orElse(null);
    if (object == null) {
      return Optional.empty();
    }
    for (WorldProperty property : object.properties()) {
      if (property.name().equalsIgnoreCase(propertyName)) {
        return Optional.of(property);
      }
    }
    for (long parentId : object.parents()) {
      Optional<WorldProperty> inherited = property(parentId, propertyName);
      if (inherited.isPresent()) {
        return inherited;
      }
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
      case "last_move" -> Optional.of(object.lastMove());
      case "contents" ->
          Optional.of(
              new ListValue(
                  object.contents().stream()
                      .map(contentId -> (MooValue) new ObjectValue(contentId))
                      .toList()));
      case "owner" -> Optional.of(new ObjectValue(object.owner()));
      case "programmer" ->
          Optional.of(new IntegerValue((object.flags() & PROGRAMMER_FLAG) == 0 ? 0 : 1));
      case "wizard" -> Optional.of(new IntegerValue((object.flags() & WIZARD_FLAG) == 0 ? 0 : 1));
      case "r" -> Optional.of(new IntegerValue((object.flags() & 16) == 0 ? 0 : 1));
      case "w" -> Optional.of(new IntegerValue((object.flags() & 32) == 0 ? 0 : 1));
      case "f" -> Optional.of(new IntegerValue((object.flags() & 128) == 0 ? 0 : 1));
      case "a" ->
          Optional.of(new IntegerValue((object.flags() & ANONYMOUS_FLAG) == 0 ? 0 : 1));
      default -> {
        Optional<MooValue> value = Optional.empty();
        for (long ancestor : ancestry(objectId)) {
          WorldObject currentObject = object(ancestor).orElseThrow();
          WorldProperty property = findProperty(currentObject, propertyName).orElse(null);
          if (property != null && !property.clear()) {
            value = Optional.of(property.value());
            break;
          }
        }
        yield value;
      }
    };
  }

  /** Reads an ordinary or built-in property on an anonymous object. */
  public Optional<MooValue> readObjectProperty(
      AnonymousObjectValue identity, String propertyName) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(propertyName, "propertyName");
    WorldAnonymousObject object = anonymousObject(identity).orElse(null);
    if (object == null) {
      return Optional.empty();
    }
    if (propertyName.equalsIgnoreCase("name")) {
      return Optional.of(new StringValue(object.name().getBytes(StandardCharsets.ISO_8859_1)));
    }
    for (WorldProperty property : object.properties()) {
      if (property.name().equalsIgnoreCase(propertyName) && !property.clear()) {
        return Optional.of(property.value());
      }
    }
    for (long parentId : object.parents()) {
      Optional<MooValue> inherited = readObjectProperty(parentId, propertyName);
      if (inherited.isPresent()) {
        return inherited;
      }
    }
    return Optional.empty();
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
              object.lastMove(),
              object.parents(),
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
    if (normalizedName.equals("a")) {
      if (!(value instanceof IntegerValue enabled)) {
        return false;
      }
      replaceFlags(object, ANONYMOUS_FLAG, enabled.isTruthy());
      return true;
    }
    List<WorldProperty> properties = new ArrayList<>(object.properties());
    for (int index = 0; index < properties.size(); index++) {
      WorldProperty property = properties.get(index);
      if (property.name().equalsIgnoreCase(propertyName)) {
        properties.set(
            index,
            new WorldProperty(
                property.name(),
                value,
                property.owner(),
                property.permissions(),
                false,
                property.defined()));
        replaceObject(
            copyObject(object, object.flags(), object.owner(), object.location(), properties));
        return true;
      }
    }
    WorldProperty inherited = property(objectId, propertyName).orElse(null);
    if (inherited == null) {
      return false;
    }
    properties.add(
        new WorldProperty(
            inherited.name(), value, inherited.owner(), inherited.permissions(), false, false));
    replaceObject(
        copyObject(object, object.flags(), object.owner(), object.location(), properties));
    return true;
  }

  /** Writes an ordinary or built-in property on an anonymous object. */
  public boolean writeObjectProperty(
      AnonymousObjectValue identity, String propertyName, MooValue value) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(propertyName, "propertyName");
    Objects.requireNonNull(value, "value");
    WorldAnonymousObject object = anonymousObject(identity).orElse(null);
    if (object == null) {
      return false;
    }
    if (propertyName.equalsIgnoreCase("name")) {
      if (!(value instanceof StringValue name)) {
        return false;
      }
      replaceAnonymousObject(
          identity,
          new WorldAnonymousObject(
              new String(name.bytes(), StandardCharsets.ISO_8859_1),
              object.flags(),
              object.owner(),
              object.parents(),
              object.verbs(),
              object.properties()));
      return true;
    }
    if (propertyName.equalsIgnoreCase("owner")) {
      if (!(value instanceof ObjectValue owner)) {
        return false;
      }
      replaceAnonymousObject(
          identity,
          new WorldAnonymousObject(
              object.name(),
              object.flags(),
              owner.value(),
              object.parents(),
              object.verbs(),
              object.properties()));
      return true;
    }
    List<WorldProperty> properties = new ArrayList<>(object.properties());
    for (int index = 0; index < properties.size(); index++) {
      WorldProperty property = properties.get(index);
      if (property.name().equalsIgnoreCase(propertyName)) {
        properties.set(
            index,
            new WorldProperty(
                property.name(),
                value,
                property.owner(),
                property.permissions(),
                false,
                property.defined()));
        replaceAnonymousObject(
            identity,
            new WorldAnonymousObject(
                object.name(),
                object.flags(),
                object.owner(),
                object.parents(),
                object.verbs(),
                properties));
        return true;
      }
    }
    WorldProperty inherited = null;
    for (long parentId : object.parents()) {
      inherited = property(parentId, propertyName).orElse(null);
      if (inherited != null) {
        break;
      }
    }
    if (inherited == null) {
      return false;
    }
    properties.add(
        new WorldProperty(
            inherited.name(), value, inherited.owner(), inherited.permissions(), false, false));
    replaceAnonymousObject(
        identity,
        new WorldAnonymousObject(
            object.name(),
            object.flags(),
            object.owner(),
            object.parents(),
            object.verbs(),
            properties));
    return true;
  }

  /** Allocates the next object number and returns the new object. */
  public WorldObject createObject(long parentId, long ownerId) {
    return createObject(parentId == -1 ? List.of() : List.of(parentId), ownerId);
  }

  /** Allocates the next object number with ordered direct parents. */
  public WorldObject createObject(List<Long> parentIds, long ownerId) {
    List<Long> parents = validateNewParents(-1, parentIds);
    scanPredicates.add(ScanPredicate.OBJECT_IDS);
    long objectId = Math.incrementExact(working.lastUsedObjectId());
    long effectiveOwner = ownerId == -1 ? objectId : ownerId;
    List<WorldProperty> properties = inheritedProperties(parents, effectiveOwner, working.objects());
    WorldObject created =
        new WorldObject(
            objectId,
            "",
            0,
            effectiveOwner,
            -1,
            parents,
            List.of(),
            List.of(),
            List.of(),
            properties);
    Map<Long, WorldObject> objects = new LinkedHashMap<>(working.objects());
    objects.put(objectId, created);
    for (long parentId : parents) {
      WorldObject parent = Objects.requireNonNull(objects.get(parentId));
      List<Long> children = new ArrayList<>(parent.children());
      if (!children.contains(objectId)) {
        children.add(objectId);
      }
      objects.put(
          parentId,
          new WorldObject(
              parent.id(),
              parent.name(),
              parent.flags(),
              parent.owner(),
              parent.location(),
              parent.lastMove(),
              parent.parents(),
              parent.contents(),
              children,
              parent.verbs(),
              parent.properties()));
    }
    replaceWorld(working.players(), objects, objectId);
    return created;
  }

  /** Recreates one recycled permanent object at its existing durable object number. */
  public WorldObject recreateObject(long objectId, List<Long> parentIds, long ownerId) {
    if (objectId <= 0
        || objectId > working.lastUsedObjectId()
        || working.objects().containsKey(objectId)) {
      throw new IllegalArgumentException("object number is not recycled #" + objectId);
    }
    List<Long> parents = validateNewParents(-1, parentIds);
    scanPredicates.add(ScanPredicate.OBJECT_IDS);
    List<WorldProperty> properties = inheritedProperties(parents, ownerId, working.objects());
    WorldObject created =
        new WorldObject(
            objectId,
            "",
            0,
            ownerId,
            -1,
            parents,
            List.of(),
            List.of(),
            List.of(),
            properties);
    Map<Long, WorldObject> objects = new LinkedHashMap<>(working.objects());
    objects.put(objectId, created);
    for (long parentId : parents) {
      WorldObject parent = Objects.requireNonNull(objects.get(parentId));
      List<Long> children = new ArrayList<>(parent.children());
      if (!children.contains(objectId)) {
        children.add(objectId);
      }
      objects.put(
          parentId,
          new WorldObject(
              parent.id(),
              parent.name(),
              parent.flags(),
              parent.owner(),
              parent.location(),
              parent.lastMove(),
              parent.parents(),
              parent.contents(),
              children,
              parent.verbs(),
              parent.properties()));
    }
    replaceWorld(working.players(), objects);
    return created;
  }

  /** Resets the durable allocation boundary to the greatest currently-live permanent object. */
  public long resetLastUsedObjectId() {
    long maximum = working.objects().keySet().stream().mapToLong(Long::longValue).max().orElse(-1);
    replaceWorld(working.players(), working.objects(), maximum);
    return maximum;
  }

  /** Allocates an anonymous object without changing the permanent object-number topology. */
  public AnonymousObjectValue createAnonymousObject(long parentId, long ownerId) {
    return createAnonymousObject(parentId == -1 ? List.of() : List.of(parentId), ownerId);
  }

  /** Allocates an anonymous object with ordered permanent parents. */
  public AnonymousObjectValue createAnonymousObject(List<Long> parentIds, long ownerId) {
    List<Long> parents = validateNewParents(-1, parentIds);
    List<WorldProperty> properties = inheritedProperties(parents, ownerId, working.objects());
    AnonymousObjectValue identity = new AnonymousObjectValue();
    replaceAnonymousObject(
        identity,
        new WorldAnonymousObject("", 0, ownerId, parents, List.of(), properties));
    return identity;
  }

  /** Allocates a WAIF whose ordered slots derive from its class's colon-prefixed properties. */
  public WaifValue createWaif(long classId, long ownerId) {
    WorldObject waifClass = object(classId).orElseThrow(
        () -> new IllegalArgumentException("missing WAIF class #" + classId));
    List<WorldProperty> properties = new ArrayList<>();
    for (WorldProperty property : waifClass.properties()) {
      if (property.name().startsWith(":")) {
        properties.add(
            new WorldProperty(
                property.name(),
                property.value(),
                property.owner(),
                property.permissions(),
                true,
                false));
      }
    }
    WaifValue identity =
        new WaifValue(new ObjectValue(classId), new ObjectValue(ownerId));
    replaceWaif(identity, new WorldWaif(properties));
    return identity;
  }

  /** Reads a WAIF override or falls back to the corresponding class property value. */
  public Optional<MooValue> readWaifProperty(WaifValue identity, String propertyName) {
    Objects.requireNonNull(propertyName, "propertyName");
    WorldWaif body = waif(identity).orElse(null);
    if (body == null) {
      return Optional.empty();
    }
    String classPropertyName = ":" + propertyName;
    for (WorldProperty property : body.properties()) {
      if (property.name().equalsIgnoreCase(classPropertyName)) {
        if (!property.clear()) {
          return Optional.of(property.value());
        }
        return readObjectProperty(identity.classObject().value(), classPropertyName);
      }
    }
    return Optional.empty();
  }

  /** Returns the class property definition that governs one WAIF property. */
  public Optional<WorldProperty> waifProperty(WaifValue identity, String propertyName) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(propertyName, "propertyName");
    return property(identity.classObject().value(), ":" + propertyName);
  }

  /** Writes one existing WAIF property override and returns whether the slot exists. */
  public boolean writeWaifProperty(WaifValue identity, String propertyName, MooValue value) {
    Objects.requireNonNull(propertyName, "propertyName");
    Objects.requireNonNull(value, "value");
    WorldWaif body = waif(identity).orElse(null);
    if (body == null) {
      return false;
    }
    String classPropertyName = ":" + propertyName;
    List<WorldProperty> properties = new ArrayList<>(body.properties());
    for (int index = 0; index < properties.size(); index++) {
      WorldProperty property = properties.get(index);
      if (property.name().equalsIgnoreCase(classPropertyName)) {
        properties.set(
            index,
            new WorldProperty(
                property.name(),
                value,
                property.owner(),
                property.permissions(),
                false,
                property.defined()));
        replaceWaif(identity, new WorldWaif(properties));
        return true;
      }
    }
    return false;
  }

  /** Returns whether a value graph contains the requested WAIF by reference identity. */
  public boolean valueRefersToWaif(MooValue value, WaifValue requested) {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(requested, "requested");
    Deque<MooValue> pending = new ArrayDeque<>();
    Set<MooValue> expanded = Collections.newSetFromMap(new IdentityHashMap<>());
    pending.push(value);
    while (!pending.isEmpty()) {
      MooValue current = pending.pop();
      if (current instanceof WaifValue waif) {
        if (waif == requested) {
          return true;
        }
        if (!expanded.add(waif)) {
          continue;
        }
        WorldWaif body = waif(waif).orElse(null);
        if (body != null) {
          for (WorldProperty property : body.properties()) {
            pending.push(property.value());
          }
        }
      } else if (current instanceof ListValue list) {
        if (expanded.add(list)) {
          for (MooValue element : list.elements()) {
            pending.push(element);
          }
        }
      } else if (current instanceof MapValue map && expanded.add(map)) {
        for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
          pending.push(entry.getKey());
          pending.push(entry.getValue());
        }
      }
    }
    return false;
  }

  /** Removes one object from the current immutable world snapshot. */
  public boolean recycleObject(long objectId) {
    WorldObject target = object(objectId).orElse(null);
    if (target == null) {
      return false;
    }
    Map<Long, WorldObject> oldObjects = working.objects();
    Set<Long> affectedObjects = descendantsOf(Set.of(objectId), oldObjects);
    Map<Long, WorldObject> objects = new LinkedHashMap<>(oldObjects);

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
                location.lastMove(),
                location.parents(),
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
                content.lastMove(),
                content.parents(),
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
        List<Long> replacementParents = new ArrayList<>();
        for (long parentId : child.parents()) {
          if (parentId == objectId) {
            for (long inheritedParent : target.parents()) {
              if (!replacementParents.contains(inheritedParent)) {
                replacementParents.add(inheritedParent);
              }
            }
          } else if (!replacementParents.contains(parentId)) {
            replacementParents.add(parentId);
          }
        }
        objects.put(
            child.id(),
            new WorldObject(
                child.id(),
                child.name(),
                child.flags(),
                child.owner(),
                child.location(),
                child.lastMove(),
                replacementParents,
                child.contents(),
                child.children(),
                child.verbs(),
                child.properties()));
      }
    }

    for (long parentId : target.parents()) {
      recordReads.add(parentId);
      WorldObject parent = objects.get(parentId);
      if (parent != null) {
        List<Long> children = new ArrayList<>();
        for (long childId : parent.children()) {
          if (childId == objectId) {
            for (long child : target.children()) {
              if (!children.contains(child)) {
                children.add(child);
              }
            }
          } else {
            if (!children.contains(childId)) {
              children.add(childId);
            }
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
                parent.lastMove(),
                parent.parents(),
                parent.contents(),
                children,
                parent.verbs(),
                parent.properties()));
      }
    }

    objects.remove(objectId);
    try {
      objects = rebuildPropertyLayouts(oldObjects, objects, new LinkedHashSet<>(target.children()));
    } catch (IllegalArgumentException error) {
      return false;
    }
    Map<AnonymousObjectValue, WorldAnonymousObject> anonymousReplacements =
        new LinkedHashMap<>();
    for (Map.Entry<AnonymousObjectValue, WorldAnonymousObject> entry :
        working.anonymousObjects().entrySet()) {
      WorldAnonymousObject anonymous = entry.getValue();
      boolean directChild = anonymous.parents().contains(objectId);
      if (!directChild
          && !usesAffectedAncestor(anonymous.parents(), oldObjects, affectedObjects)) {
        continue;
      }
      List<Long> parents = new ArrayList<>();
      for (long parentId : anonymous.parents()) {
        if (parentId == objectId) {
          for (long inheritedParent : target.parents()) {
            if (!parents.contains(inheritedParent)) {
              parents.add(inheritedParent);
            }
          }
        } else if (!parents.contains(parentId)) {
          parents.add(parentId);
        }
      }
      try {
        anonymousReplacements.put(
            entry.getKey(),
            new WorldAnonymousObject(
                anonymous.name(),
                anonymous.flags(),
                anonymous.owner(),
                parents,
                anonymous.verbs(),
                rebuiltAnonymousProperties(
                    anonymous,
                    anonymous.parents(),
                    oldObjects,
                    anonymous,
                    parents,
                    objects)));
      } catch (IllegalArgumentException error) {
        return false;
      }
    }
    List<Long> players = new ArrayList<>(working.players());
    players.remove(objectId);
    replaceWorld(players, objects);
    for (Map.Entry<AnonymousObjectValue, WorldAnonymousObject> entry :
        anonymousReplacements.entrySet()) {
      replaceAnonymousObject(entry.getKey(), entry.getValue());
    }
    return true;
  }

  /** Changes one object's parent while updating both reciprocal topology records. */
  public boolean changeParent(long objectId, long newParentId) {
    return changeParents(objectId, newParentId == -1 ? List.of() : List.of(newParentId));
  }

  /** Changes one object's ordered parents while updating every reciprocal topology record. */
  public boolean changeParents(long objectId, List<Long> newParentIds) {
    WorldObject target = object(objectId).orElse(null);
    if (target == null) {
      return false;
    }
    final List<Long> newParents;
    try {
      newParents = validateNewParents(objectId, newParentIds);
    } catch (IllegalArgumentException error) {
      return false;
    }

    Map<Long, WorldObject> oldObjects = working.objects();
    Map<Long, WorldObject> objects = new LinkedHashMap<>(oldObjects);
    for (long oldParentId : target.parents()) {
      WorldObject oldParent = Objects.requireNonNull(objects.get(oldParentId));
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
              oldParent.lastMove(),
              oldParent.parents(),
              oldParent.contents(),
              oldChildren,
              oldParent.verbs(),
              oldParent.properties()));
    }

    for (long newParentId : newParents) {
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
              currentNewParent.lastMove(),
              currentNewParent.parents(),
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
            target.lastMove(),
            newParents,
            target.contents(),
            target.children(),
            target.verbs(),
            target.properties()));
    try {
      objects = rebuildPropertyLayouts(oldObjects, objects, Set.of(objectId));
    } catch (IllegalArgumentException error) {
      return false;
    }
    Set<Long> affectedObjects = descendantsOf(Set.of(objectId), objects);
    replaceWorld(working.players(), objects);
    rebuildAnonymousPropertyLayouts(oldObjects, objects, affectedObjects);
    return true;
  }

  /** Changes one anonymous object's ordered permanent parents atomically. */
  public boolean changeParents(AnonymousObjectValue identity, List<Long> newParentIds) {
    Objects.requireNonNull(identity, "identity");
    WorldAnonymousObject target = anonymousObject(identity).orElse(null);
    if (target == null) {
      return false;
    }
    final List<Long> newParents;
    try {
      newParents = validateNewParents(-1, newParentIds);
    } catch (IllegalArgumentException error) {
      return false;
    }
    final List<WorldProperty> properties;
    try {
      properties =
          rebuiltAnonymousProperties(
              target,
              target.parents(),
              working.objects(),
              target,
              newParents,
              working.objects());
    } catch (IllegalArgumentException error) {
      return false;
    }
    replaceAnonymousObject(
        identity,
        new WorldAnonymousObject(
            target.name(),
            target.flags(),
            target.owner(),
            newParents,
            target.verbs(),
            properties));
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
    return move(objectId, destinationId, 0);
  }

  /** Moves an object to a one-based contents position; zero appends. */
  public boolean move(long objectId, long destinationId, long position) {
    WorldObject object = object(objectId).orElse(null);
    WorldObject destination = object(destinationId).orElse(null);
    if (object == null || (destinationId != -1 && destination == null) || position < 0) {
      return false;
    }
    if (object.location() != -1) {
      WorldObject previous = object(object.location()).orElseThrow();
      List<Long> previousContents = new ArrayList<>(previous.contents());
      previousContents.remove(objectId);
      replaceObject(copyContents(previous, previousContents));
    }
    if (destinationId != -1) {
      WorldObject currentDestination = object(destinationId).orElseThrow();
      List<Long> destinationContents = new ArrayList<>(currentDestination.contents());
      int insertionIndex =
          position <= 0 || position > destinationContents.size()
              ? destinationContents.size()
              : (int) position - 1;
      destinationContents.add(insertionIndex, objectId);
      replaceObject(copyContents(currentDestination, destinationContents));
    }
    Map<MooValue, MooValue> lastMove =
        object.lastMove() instanceof MapValue map
            ? new LinkedHashMap<>(map.entries())
            : new LinkedHashMap<>();
    lastMove.put(
        new StringValue("time".getBytes(StandardCharsets.ISO_8859_1)),
        new IntegerValue(System.currentTimeMillis() / 1_000L));
    lastMove.put(
        new StringValue("source".getBytes(StandardCharsets.ISO_8859_1)),
        new ObjectValue(object.location()));
    replaceObject(
        copyObject(
            object,
            object.flags(),
            object.owner(),
            destinationId,
            new MapValue(lastMove),
            object.properties()));
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
    Map<Long, WorldObject> oldObjects = working.objects();
    List<WorldProperty> properties = new ArrayList<>(object.properties());
    int propertyIndex = 0;
    while (propertyIndex < properties.size() && properties.get(propertyIndex).defined()) {
      propertyIndex++;
    }
    properties.add(
        propertyIndex, new WorldProperty(name, value, owner, permissions, false, true));
    replaceObject(
        copyObject(object, object.flags(), object.owner(), object.location(), properties));
    Map<Long, WorldObject> newObjects =
        rebuildPropertyLayouts(oldObjects, working.objects(), Set.of(objectId));
    Set<Long> affectedObjects = descendantsOf(Set.of(objectId), newObjects);
    replaceWorld(working.players(), newObjects);
    rebuildAnonymousPropertyLayouts(oldObjects, newObjects, affectedObjects);
    return true;
  }

  /** Adds one local property to an anonymous object. */
  public boolean addProperty(
      AnonymousObjectValue identity,
      String name,
      MooValue value,
      long owner,
      int permissions) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(value, "value");
    WorldAnonymousObject object = anonymousObject(identity).orElse(null);
    if (object == null || property(identity, name).isPresent()) {
      return false;
    }
    List<WorldProperty> properties = new ArrayList<>(object.properties());
    int propertyIndex = 0;
    while (propertyIndex < properties.size() && properties.get(propertyIndex).defined()) {
      propertyIndex++;
    }
    properties.add(
        propertyIndex, new WorldProperty(name, value, owner, permissions, false, true));
    replaceAnonymousObject(
        identity,
        new WorldAnonymousObject(
            object.name(),
            object.flags(),
            object.owner(),
            object.parents(),
            object.verbs(),
            properties));
    return true;
  }

  /** Deletes one property definition local to the exact object. */
  public boolean deleteProperty(long objectId, String name) {
    Objects.requireNonNull(name, "name");
    WorldObject object = object(objectId).orElse(null);
    if (object == null) {
      return false;
    }
    Map<Long, WorldObject> oldObjects = working.objects();
    List<WorldProperty> properties = new ArrayList<>(object.properties());
    int propertyIndex = -1;
    for (int index = 0; index < properties.size(); index++) {
      WorldProperty property = properties.get(index);
      if (property.defined() && property.name().equalsIgnoreCase(name)) {
        propertyIndex = index;
        break;
      }
    }
    if (propertyIndex < 0) {
      return false;
    }
    properties.remove(propertyIndex);
    replaceObject(
        copyObject(object, object.flags(), object.owner(), object.location(), properties));
    Map<Long, WorldObject> newObjects =
        rebuildPropertyLayouts(oldObjects, working.objects(), Set.of(objectId));
    Set<Long> affectedObjects = descendantsOf(Set.of(objectId), newObjects);
    replaceWorld(working.players(), newObjects);
    rebuildAnonymousPropertyLayouts(oldObjects, newObjects, affectedObjects);
    return true;
  }

  /** Clears one inherited property value slot without deleting its definition. */
  public boolean clearProperty(long objectId, String name) {
    Objects.requireNonNull(name, "name");
    WorldObject object = object(objectId).orElse(null);
    if (object == null) {
      return false;
    }
    List<WorldProperty> properties = new ArrayList<>(object.properties());
    for (int index = 0; index < properties.size(); index++) {
      WorldProperty property = properties.get(index);
      if (property.name().equalsIgnoreCase(name) && !property.defined()) {
        properties.set(
            index,
            new WorldProperty(
                property.name(),
                property.value(),
                property.owner(),
                property.permissions(),
                true,
                false));
        replaceObject(
            copyObject(object, object.flags(), object.owner(), object.location(), properties));
        return true;
      }
    }
    return false;
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
            object.lastMove(),
            object.parents(),
            object.contents(),
            object.children(),
            verbs,
            object.properties()));
    return verbs.size();
  }

  /** Adds one local verb to an anonymous object's immutable body. */
  public int addVerb(
      AnonymousObjectValue identity,
      String names,
      long owner,
      int permissions,
      int preposition) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(names, "names");
    WorldAnonymousObject object = anonymousObject(identity).orElse(null);
    if (object == null) {
      return 0;
    }
    List<WorldVerb> verbs = new ArrayList<>(object.verbs());
    verbs.add(new WorldVerb(names, owner, permissions, preposition, ""));
    replaceAnonymousObject(
        identity,
        new WorldAnonymousObject(
            object.name(),
            object.flags(),
            object.owner(),
            object.parents(),
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
            object.lastMove(),
            object.parents(),
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
            object.lastMove(),
            object.parents(),
            object.contents(),
            object.children(),
            verbs,
            object.properties()));
    return true;
  }

  /** Replaces the source of one zero-based local verb on an anonymous object. */
  public boolean setVerbCode(
      AnonymousObjectValue identity, int verbIndex, String programSource) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(programSource, "programSource");
    WorldAnonymousObject object = anonymousObject(identity).orElse(null);
    if (object == null || verbIndex < 0 || verbIndex >= object.verbs().size()) {
      return false;
    }
    List<WorldVerb> verbs = new ArrayList<>(object.verbs());
    WorldVerb verb = verbs.get(verbIndex);
    verbs.set(
        verbIndex,
        new WorldVerb(
            verb.names(), verb.owner(), verb.permissions(), verb.preposition(), programSource));
    replaceAnonymousObject(
        identity,
        new WorldAnonymousObject(
            object.name(),
            object.flags(),
            object.owner(),
            object.parents(),
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
            object.lastMove(),
            object.parents(),
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
            object.lastMove(),
            object.parents(),
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

  private List<Long> validateNewParents(long objectId, List<Long> requestedParents) {
    Objects.requireNonNull(requestedParents, "requestedParents");
    List<Long> parents = List.copyOf(requestedParents);
    if (new LinkedHashSet<>(parents).size() != parents.size()) {
      throw new IllegalArgumentException("duplicate inheritance parent");
    }
    for (long parentId : parents) {
      if (parentId < 0 || object(parentId).isEmpty()) {
        throw new IllegalArgumentException("missing parent #" + parentId);
      }
      if (parentId == objectId || ancestry(parentId).contains(objectId)) {
        throw new IllegalArgumentException("recursive inheritance parent #" + parentId);
      }
    }

    Map<String, Long> definitions = new LinkedHashMap<>();
    if (objectId >= 0) {
      WorldObject target = object(objectId).orElseThrow();
      for (WorldProperty property : target.properties()) {
        if (property.defined()) {
          definitions.put(property.name().toLowerCase(Locale.ROOT), objectId);
        }
      }
    }
    for (long ancestorId : ancestryFromParents(parents, working.objects())) {
      WorldObject ancestor = Objects.requireNonNull(working.objects().get(ancestorId));
      for (WorldProperty property : ancestor.properties()) {
        if (!property.defined()) {
          continue;
        }
        String name = property.name().toLowerCase(Locale.ROOT);
        Long previous = definitions.putIfAbsent(name, ancestorId);
        if (previous != null && previous != ancestorId) {
          throw new IllegalArgumentException("conflicting inherited property " + property.name());
        }
      }
    }
    return parents;
  }

  private static List<Long> ancestryFromParents(
      List<Long> parents, Map<Long, WorldObject> objects) {
    List<Long> result = new ArrayList<>();
    Set<Long> visited = new LinkedHashSet<>();
    for (long parentId : parents) {
      collectAncestry(parentId, objects, new LinkedHashSet<>(), visited, result);
    }
    return List.copyOf(result);
  }

  private static void collectAncestry(
      long objectId,
      Map<Long, WorldObject> objects,
      Set<Long> visiting,
      Set<Long> visited,
      List<Long> result) {
    if (visited.contains(objectId)) {
      return;
    }
    WorldObject object = objects.get(objectId);
    if (object == null || !visiting.add(objectId)) {
      throw new IllegalArgumentException("invalid inheritance graph at #" + objectId);
    }
    visited.add(objectId);
    result.add(objectId);
    for (long parentId : object.parents()) {
      collectAncestry(parentId, objects, visiting, visited, result);
    }
    visiting.remove(objectId);
  }

  private static List<WorldProperty> inheritedProperties(
      List<Long> parents, long owner, Map<Long, WorldObject> objects) {
    List<WorldProperty> properties = new ArrayList<>();
    for (long ancestorId : ancestryFromParents(parents, objects)) {
      WorldObject ancestor = Objects.requireNonNull(objects.get(ancestorId));
      for (WorldProperty property : ancestor.properties()) {
        if (!property.defined()) {
          continue;
        }
        WorldProperty fallback =
            directParentProperty(parents, property.name(), objects).orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "missing direct-parent property " + property.name()));
        properties.add(
            new WorldProperty(
                property.name(),
                fallback.value(),
                (fallback.permissions() & 8) != 0 ? owner : fallback.owner(),
                fallback.permissions(),
                true,
                false));
      }
    }
    return properties;
  }

  private static Map<Long, WorldObject> rebuildPropertyLayouts(
      Map<Long, WorldObject> oldSource,
      Map<Long, WorldObject> newSource,
      Set<Long> roots) {
    Set<Long> affected = descendantsOf(roots, newSource);
    Map<Long, WorldObject> rebuilt = new LinkedHashMap<>(newSource);
    Set<Long> complete = new LinkedHashSet<>();
    for (long objectId : affected) {
      rebuildPropertyLayout(objectId, oldSource, newSource, rebuilt, affected, complete);
    }
    return rebuilt;
  }

  private static void rebuildPropertyLayout(
      long objectId,
      Map<Long, WorldObject> oldSource,
      Map<Long, WorldObject> newSource,
      Map<Long, WorldObject> rebuilt,
      Set<Long> affected,
      Set<Long> complete) {
    if (!complete.add(objectId)) {
      return;
    }
    WorldObject object = Objects.requireNonNull(newSource.get(objectId));
    for (long parentId : object.parents()) {
      if (affected.contains(parentId)) {
        rebuildPropertyLayout(parentId, oldSource, newSource, rebuilt, affected, complete);
      }
    }
    List<WorldProperty> properties = new ArrayList<>();
    for (WorldProperty property : object.properties()) {
      if (property.defined()) {
        properties.add(property);
      }
    }
    WorldObject oldObject = oldSource.get(objectId);
    Map<PropertyDefinition, WorldProperty> old =
        oldObject == null ? Map.of() : oldPropertySlots(oldObject, oldSource);
    Set<String> names = new LinkedHashSet<>();
    for (WorldProperty property : properties) {
      names.add(property.name().toLowerCase(Locale.ROOT));
    }
    for (long ancestorId : ancestryFromParents(object.parents(), newSource)) {
      WorldObject ancestor = Objects.requireNonNull(newSource.get(ancestorId));
      for (WorldProperty definition : ancestor.properties()) {
        if (!definition.defined()) {
          continue;
        }
        String normalized = definition.name().toLowerCase(Locale.ROOT);
        if (!names.add(normalized)) {
          throw new IllegalArgumentException("conflicting inherited property " + definition.name());
        }
        WorldProperty fallback =
            directParentProperty(object.parents(), definition.name(), rebuilt).orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "missing direct-parent property " + definition.name()));
        WorldProperty previous = old.get(new PropertyDefinition(ancestorId, normalized));
        if (previous != null && !previous.defined()) {
          properties.add(
              new WorldProperty(
                  definition.name(),
                  previous.clear() ? fallback.value() : previous.value(),
                  previous.owner(),
                  previous.permissions(),
                  previous.clear(),
                  false));
        } else {
          properties.add(
              new WorldProperty(
                  definition.name(),
                  fallback.value(),
                  (fallback.permissions() & 8) != 0 ? object.owner() : fallback.owner(),
                  fallback.permissions(),
                  true,
                  false));
        }
      }
    }
    WorldObject replacement =
        new WorldObject(
            object.id(),
            object.name(),
            object.flags(),
            object.owner(),
            object.location(),
            object.lastMove(),
            object.parents(),
            object.contents(),
            object.children(),
            object.verbs(),
            properties);
    rebuilt.put(objectId, replacement);
  }

  private static Map<PropertyDefinition, WorldProperty> oldPropertySlots(
      WorldObject object, Map<Long, WorldObject> source) {
    Map<PropertyDefinition, WorldProperty> slots = new LinkedHashMap<>();
    if (object == null) {
      return slots;
    }
    List<PropertyDefinition> definitions = new ArrayList<>();
    for (WorldProperty property : object.properties()) {
      if (property.defined()) {
        definitions.add(
            new PropertyDefinition(object.id(), property.name().toLowerCase(Locale.ROOT)));
      }
    }
    for (long ancestorId : ancestryFromParents(object.parents(), source)) {
      WorldObject ancestor = Objects.requireNonNull(source.get(ancestorId));
      for (WorldProperty property : ancestor.properties()) {
        if (property.defined()) {
          definitions.add(
              new PropertyDefinition(ancestorId, property.name().toLowerCase(Locale.ROOT)));
        }
      }
    }
    int count = Math.min(definitions.size(), object.properties().size());
    for (int index = 0; index < count; index++) {
      slots.put(definitions.get(index), object.properties().get(index));
    }
    return slots;
  }

  private static Set<Long> descendantsOf(
      Set<Long> roots, Map<Long, WorldObject> objects) {
    Set<Long> descendants = new LinkedHashSet<>();
    List<Long> pending = new ArrayList<>(roots);
    for (int index = 0; index < pending.size(); index++) {
      long objectId = pending.get(index);
      if (!descendants.add(objectId)) {
        continue;
      }
      WorldObject object = objects.get(objectId);
      if (object != null) {
        pending.addAll(object.children());
      }
    }
    descendants.retainAll(objects.keySet());
    return descendants;
  }

  private record PropertyDefinition(long objectId, String name) {}

  private static Optional<WorldProperty> directParentProperty(
      List<Long> parents, String name, Map<Long, WorldObject> objects) {
    for (long parentId : parents) {
      WorldObject parent = objects.get(parentId);
      if (parent == null) {
        continue;
      }
      for (WorldProperty property : parent.properties()) {
        if (property.name().equalsIgnoreCase(name)) {
          return Optional.of(property);
        }
      }
    }
    return Optional.empty();
  }

  private void rebuildAnonymousPropertyLayouts(
      Map<Long, WorldObject> oldObjects,
      Map<Long, WorldObject> newObjects,
      Set<Long> affectedObjects) {
    for (Map.Entry<AnonymousObjectValue, WorldAnonymousObject> entry :
        List.copyOf(working.anonymousObjects().entrySet())) {
      WorldAnonymousObject object = entry.getValue();
      if (!usesAffectedAncestor(object.parents(), oldObjects, affectedObjects)
          && !usesAffectedAncestor(object.parents(), newObjects, affectedObjects)) {
        continue;
      }
      List<WorldProperty> properties =
          rebuiltAnonymousProperties(
              object,
              object.parents(),
              oldObjects,
              object,
              object.parents(),
              newObjects);
      replaceAnonymousObject(
          entry.getKey(),
          new WorldAnonymousObject(
              object.name(), object.flags(), object.owner(), object.parents(), object.verbs(), properties));
    }
  }

  private static boolean usesAffectedAncestor(
      List<Long> parents, Map<Long, WorldObject> objects, Set<Long> affectedObjects) {
    for (long parent : parents) {
      if (objects.containsKey(parent)) {
        for (long ancestor : ancestryFromParents(List.of(parent), objects)) {
          if (affectedObjects.contains(ancestor)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static List<WorldProperty> rebuiltAnonymousProperties(
      WorldAnonymousObject oldObject,
      List<Long> oldParents,
      Map<Long, WorldObject> oldObjects,
      WorldAnonymousObject object,
      List<Long> parents,
      Map<Long, WorldObject> objects) {
    List<WorldProperty> properties = new ArrayList<>();
    Map<PropertyDefinition, WorldProperty> old = new LinkedHashMap<>();
    Set<String> names = new LinkedHashSet<>();
    List<PropertyDefinition> oldDefinitions = new ArrayList<>();
    for (WorldProperty property : oldObject.properties()) {
      if (property.defined()) {
        oldDefinitions.add(
            new PropertyDefinition(Long.MIN_VALUE, property.name().toLowerCase(Locale.ROOT)));
      }
    }
    for (long ancestorId : ancestryFromParents(oldParents, oldObjects)) {
      WorldObject ancestor = Objects.requireNonNull(oldObjects.get(ancestorId));
      for (WorldProperty property : ancestor.properties()) {
        if (property.defined()) {
          oldDefinitions.add(
              new PropertyDefinition(ancestorId, property.name().toLowerCase(Locale.ROOT)));
        }
      }
    }
    int oldCount = Math.min(oldDefinitions.size(), oldObject.properties().size());
    for (int index = 0; index < oldCount; index++) {
      old.put(oldDefinitions.get(index), oldObject.properties().get(index));
    }
    for (WorldProperty property : object.properties()) {
      String normalized = property.name().toLowerCase(Locale.ROOT);
      if (property.defined()) {
        names.add(normalized);
        properties.add(property);
      }
    }
    for (long ancestorId : ancestryFromParents(parents, objects)) {
      WorldObject ancestor = Objects.requireNonNull(objects.get(ancestorId));
      for (WorldProperty definition : ancestor.properties()) {
        if (!definition.defined()) {
          continue;
        }
        String normalized = definition.name().toLowerCase(Locale.ROOT);
        if (!names.add(normalized)) {
          throw new IllegalArgumentException("conflicting inherited property " + definition.name());
        }
        WorldProperty fallback =
            directParentProperty(parents, definition.name(), objects).orElseThrow();
        WorldProperty previous = old.get(new PropertyDefinition(ancestorId, normalized));
        if (previous != null && !previous.defined()) {
          properties.add(
              new WorldProperty(
                  definition.name(),
                  previous.clear() ? fallback.value() : previous.value(),
                  previous.owner(),
                  previous.permissions(),
                  previous.clear(),
                  false));
        } else {
          properties.add(
              new WorldProperty(
                  definition.name(),
                  fallback.value(),
                  (fallback.permissions() & 8) != 0 ? object.owner() : fallback.owner(),
                  fallback.permissions(),
                  true,
                  false));
        }
      }
    }
    return List.copyOf(properties);
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

  private void replaceAnonymousObject(
      AnonymousObjectValue identity, WorldAnonymousObject replacement) {
    ensureActiveTransaction();
    Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects =
        new LinkedHashMap<>(working.anonymousObjects());
    anonymousObjects.put(identity, replacement);
    World next =
        new World(
            base.revision(),
            working.players(),
            working.objects(),
            working.lastUsedObjectId(),
            anonymousObjects,
            working.waifs(),
            working.pendingFinalization());
    if (Objects.equals(base.anonymousObjects().get(identity), replacement)) {
      anonymousWrites.remove(identity);
    } else {
      anonymousWrites.add(identity);
    }
    working = next;
  }

  private void replaceWaif(WaifValue identity, WorldWaif replacement) {
    ensureActiveTransaction();
    Map<WaifValue, WorldWaif> waifs = new LinkedHashMap<>(working.waifs());
    waifs.put(identity, replacement);
    World next =
        new World(
            base.revision(),
            working.players(),
            working.objects(),
            working.lastUsedObjectId(),
            working.anonymousObjects(),
            waifs,
            working.pendingFinalization());
    if (Objects.equals(base.waifs().get(identity), replacement)) {
      waifWrites.remove(identity);
    } else {
      waifWrites.add(identity);
    }
    working = next;
  }

  private void replaceWorld(List<Long> players, Map<Long, WorldObject> objects) {
    replaceWorld(players, objects, working.lastUsedObjectId());
  }

  private void replaceWorld(
      List<Long> players, Map<Long, WorldObject> objects, long lastUsedObjectId) {
    ensureActiveTransaction();
    World replacement =
        new World(
            base.revision(),
            players,
            objects,
            lastUsedObjectId,
            working.anonymousObjects(),
            working.waifs(),
            working.pendingFinalization());
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

  Set<AnonymousObjectValue> anonymousWrites() {
    return Collections.unmodifiableSet(anonymousWrites);
  }

  Set<AnonymousObjectValue> anonymousReads() {
    return Collections.unmodifiableSet(anonymousReads);
  }

  Set<WaifValue> waifWrites() {
    return Collections.unmodifiableSet(waifWrites);
  }

  boolean pendingFinalizationWritten() {
    return pendingFinalizationWritten;
  }

  private static long greatestObjectId(List<WorldObject> objects) {
    long greatest = -1;
    for (WorldObject object : objects) {
      greatest = Math.max(greatest, object.id());
    }
    return greatest;
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

  /** One close-once lease over an immutable committed world revision. */
  public static final class RetainedSnapshot implements AutoCloseable {
    private final WorldHistory history;
    private final World revision;
    private boolean closed;

    private RetainedSnapshot(WorldHistory history, World revision) {
      this.history = history;
      this.revision = revision;
    }

    /** Returns the immutable snapshot held by this lease. */
    public WorldSnapshot snapshot() {
      if (closed) {
        throw new IllegalStateException("retained snapshot is closed");
      }
      return revision.snapshot();
    }

    @Override
    public void close() {
      if (!closed) {
        closed = true;
        history.release(revision);
      }
    }
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
        object.lastMove(),
        object.parents(),
        contents,
        object.children(),
        object.verbs(),
        object.properties());
  }

  private static WorldObject copyObject(
      WorldObject object, int flags, long owner, long location, List<WorldProperty> properties) {
    return copyObject(object, flags, owner, location, object.lastMove(), properties);
  }

  private static WorldObject copyObject(
      WorldObject object,
      int flags,
      long owner,
      long location,
      MooValue lastMove,
      List<WorldProperty> properties) {
    return new WorldObject(
        object.id(),
        object.name(),
        flags,
        owner,
        location,
        lastMove,
        object.parents(),
        object.contents(),
        object.children(),
        object.verbs(),
        properties);
  }

  /** Closed scan predicates recorded for validation against a fixed snapshot. */
  public enum ScanPredicate {
    OBJECT_IDS,
    PLAYERS,
    PENDING_FINALIZATION
  }

  /** Toast's positive, negative, miss, generation, and bucket-depth cache statistics. */
  public record VerbCacheStats(
      long hits, long negativeHits, long misses, long generation, List<Integer> histogram) {
    public VerbCacheStats {
      histogram = List.copyOf(histogram);
      if (histogram.size() != 17) {
        throw new IllegalArgumentException("verb cache histogram must contain 17 depths");
      }
    }
  }

  /** The immutable result of non-publishing validation against the current committed revision. */
  public record ValidationResult(
      long revision,
      Set<Long> conflictingRecords,
      Set<AnonymousObjectValue> conflictingAnonymousRecords,
      Set<ScanPredicate> conflictingPredicates) {
    public ValidationResult {
      conflictingRecords = Collections.unmodifiableSet(new LinkedHashSet<>(conflictingRecords));
      conflictingAnonymousRecords =
          Collections.unmodifiableSet(new LinkedHashSet<>(conflictingAnonymousRecords));
      conflictingPredicates =
          Collections.unmodifiableSet(new LinkedHashSet<>(conflictingPredicates));
    }

    /** Returns whether the transaction's exact footprint remains current. */
    public boolean isValid() {
      return conflictingRecords.isEmpty()
          && conflictingAnonymousRecords.isEmpty()
          && conflictingPredicates.isEmpty();
    }
  }

  /** The explicit result of one atomic validation and publication attempt. */
  public record CommitResult(
      Status status,
      long revision,
      Set<Long> conflictingRecords,
      Set<AnonymousObjectValue> conflictingAnonymousRecords,
      Set<ScanPredicate> conflictingPredicates,
      List<MooValue> effects) {
    /** Takes immutable copies of conflict evidence and published effects. */
    public CommitResult {
      Objects.requireNonNull(status, "status");
      conflictingRecords = Collections.unmodifiableSet(new LinkedHashSet<>(conflictingRecords));
      conflictingAnonymousRecords =
          Collections.unmodifiableSet(new LinkedHashSet<>(conflictingAnonymousRecords));
      conflictingPredicates =
          Collections.unmodifiableSet(new LinkedHashSet<>(conflictingPredicates));
      effects = List.copyOf(effects);
    }

    static CommitResult committed(long revision, List<MooValue> effects) {
      return new CommitResult(Status.COMMITTED, revision, Set.of(), Set.of(), Set.of(), effects);
    }

    static CommitResult conflict(
        long revision,
        Set<Long> conflictingRecords,
        Set<AnonymousObjectValue> conflictingAnonymousRecords,
        Set<ScanPredicate> conflictingPredicates) {
      return new CommitResult(
          Status.CONFLICT,
          revision,
          conflictingRecords,
          conflictingAnonymousRecords,
          conflictingPredicates,
          List.of());
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
