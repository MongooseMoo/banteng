package world.mongoose.banteng.persistence;

import static world.mongoose.banteng.persistence.DbScanner.malformed;
import static world.mongoose.banteng.persistence.DbScanner.readCount;
import static world.mongoose.banteng.persistence.DbScanner.readInt;
import static world.mongoose.banteng.persistence.DbScanner.readLong;
import static world.mongoose.banteng.persistence.DbScanner.requireExact;
import static world.mongoose.banteng.persistence.DbScanner.requiredLine;
import static world.mongoose.banteng.persistence.ValueTagDecoder.readV5;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.world.ActiveConnection;
import world.mongoose.banteng.world.Checkpoint;
import world.mongoose.banteng.world.DurableTask;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldSnapshot;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

/** Reader and topology repairer for LambdaMOO's first next-generation database format. */
public final class LambdaMooV5Reader implements DbCodec {
  private static final String HEADER = "** LambdaMOO Database, Format Version 5 **";
  private final ServerLog serverLog;

  private final DbCodec output;

  /** Receives the shared log and the application's canonical checkpoint output codec. */
  public LambdaMooV5Reader(ServerLog serverLog, DbCodec output) {
    this.serverLog = Objects.requireNonNull(serverLog, "serverLog");
    this.output = Objects.requireNonNull(output, "output");
  }

  /** Reads a v5 world, applying the same three topology-repair phases as LambdaMOO. */
  @Override
  public Checkpoint read(Path database) throws IOException {
    Objects.requireNonNull(database, "database");
    try (BufferedReader input =
        Files.newBufferedReader(database, StringValue.charset())) {
      requireExact(input, HEADER, "v5 header");
      int objectSlotCount = readCount(input, "object slot count");
      int programCount = readCount(input, "program count");
      int dummyCount = readCount(input, "dummy count");
      if (dummyCount != 0) {
        throw malformed("dummy count must be zero");
      }

      int playerCount = readCount(input, "player count");
      List<Long> players = new ArrayList<>(playerCount);
      for (int index = 0; index < playerCount; index++) {
        players.add(readLong(input, "player object"));
      }

      Map<Long, RawObject> rawObjects = new LinkedHashMap<>();
      for (long objectId = 0; objectId < objectSlotCount; objectId++) {
        Optional<RawObject> object = readObject(input, objectId);
        object.ifPresent(raw -> rawObjects.put(raw.id(), raw));
      }
      Map<ProgramSlot, String> programs = ProgramSectionReader.read(input, programCount,
          objectId -> {
            RawObject object = rawObjects.get(objectId);
            return object == null ? -1 : object.verbs().size();
          });
      TaskSectionReader.Tail tail = new TaskSectionReader(5,
          (reader, tag, location) -> ValueTagDecoder.readV5(reader, tag)).read(input);
      if (input.readLine() != null) throw malformed("unexpected data after task tail");

      validatePlayers(players, rawObjects);
      Map<Long, RepairedObject> repaired = repairTopology(rawObjects);
      List<WorldObject> objects = restoreObjects(rawObjects, repaired, programs);
      return new Checkpoint(new WorldTxn(
          players, objects, Map.of(), Map.of(), List.of(), objectSlotCount - 1L),
          tail.tasks(), tail.connections());
    }
  }

  @Override
  public void write(BufferedWriter writer, WorldSnapshot world,
      List<? extends DurableTask> tasks, List<ActiveConnection> activeConnections)
      throws IOException {
    output.write(writer, world, tasks, activeConnections);
  }

  @Override
  public void writeAtomic(Path checkpoint, WorldSnapshot world,
      List<? extends DurableTask> tasks, List<ActiveConnection> activeConnections)
      throws IOException {
    output.writeAtomic(checkpoint, world, tasks, activeConnections);
  }

  @Override
  public void writePanic(Path checkpoint, WorldSnapshot world,
      List<? extends DurableTask> tasks, List<ActiveConnection> activeConnections)
      throws IOException {
    output.writePanic(checkpoint, world, tasks, activeConnections);
  }

  private static Optional<RawObject> readObject(BufferedReader input, long expectedId)
      throws IOException {
    String header = requiredLine(input, "object #" + expectedId + " header");
    if (header.equals("#" + expectedId + " recycled")
        || header.equals("# " + expectedId + " recycled")) {
      return Optional.empty();
    }
    if (!header.equals("#" + expectedId)) {
      throw malformed("invalid object #" + expectedId + " header: " + header);
    }
    String name = requiredLine(input, "object name");
    int flags = readInt(input, "object flags");
    long owner = readLong(input, "object owner");
    MooValue location = readV5(input);
    MooValue contents = readV5(input);
    MooValue parents = readV5(input);
    MooValue children = readV5(input);

    int verbCount = readCount(input, "verb count");
    List<RawVerb> verbs = new ArrayList<>(verbCount);
    for (int index = 0; index < verbCount; index++) {
      verbs.add(
          new RawVerb(
              requiredLine(input, "verb names"),
              readLong(input, "verb owner"),
              readInt(input, "verb permissions"),
              readInt(input, "verb preposition")));
    }

    int propertyNameCount = readCount(input, "property-name count");
    List<String> propertyNames = new ArrayList<>(propertyNameCount);
    for (int index = 0; index < propertyNameCount; index++) {
      propertyNames.add(requiredLine(input, "property name"));
    }
    int propertySlotCount = readCount(input, "property-slot count");
    List<RawPropertySlot> propertySlots = new ArrayList<>(propertySlotCount);
    for (int index = 0; index < propertySlotCount; index++) {
      int tag = readInt(input, "property value tag");
      propertySlots.add(
          new RawPropertySlot(
              tag == V17Format.ValueTag.CLEAR.code() ? null : readV5(input, tag),
              tag == V17Format.ValueTag.CLEAR.code(),
              readLong(input, "property owner"),
              readInt(input, "property permissions")));
    }
    return Optional.of(
        new RawObject(
            expectedId,
            name,
            flags,
            owner,
            location,
            contents,
            parents,
            children,
            verbs,
            propertyNames,
            propertySlots));
  }


  private Map<Long, RepairedObject> repairTopology(Map<Long, RawObject> objects) throws IOException {
    serverLog.info("VALIDATE: checking object references ...");
    Map<Long, RepairedObject> repaired = new LinkedHashMap<>();
    for (RawObject object : objects.values()) {
      repaired.put(
          object.id(),
          new RepairedObject(
              repairLocation(object, objects),
              repairObjectList(object, object.contents(), "contents", "content", objects),
              repairParents(object, objects),
              repairObjectList(object, object.children(), "children", "child", objects)));
    }

    serverLog.info("VALIDATE: checking inheritance graph for cycles ...");
    Set<Long> parentCycles = new LinkedHashSet<>();
    Set<Long> locationCycles = new LinkedHashSet<>();
    for (long objectId : objects.keySet()) {
      if (hasParentCycle(objectId, repaired, new LinkedHashSet<>())) {
        serverLog.error("*** VALIDATE: Cycle in parent chain of #" + objectId + ".");
        parentCycles.add(objectId);
      }
      if (hasLocationCycle(objectId, repaired)) {
        serverLog.error("*** VALIDATE: Cycle in location chain of #" + objectId + ".");
        locationCycles.add(objectId);
      }
    }
    if (!parentCycles.isEmpty() || !locationCycles.isEmpty()) {
      throw malformed("cyclic parent or location hierarchy");
    }
    for (Map.Entry<Long, RepairedObject> entry : repaired.entrySet()) {
      long id = entry.getKey();
      RepairedObject object = entry.getValue();
      if (object.location() != -1 && !Objects.requireNonNull(repaired.get(object.location())).contents().contains(id)) {
        throw malformed("object #" + id + " is absent from its location contents");
      }
      for (long parent : object.parents()) {
        if (parent == -1) continue;
        if (!Objects.requireNonNull(repaired.get(parent)).children().contains(id)) {
          throw malformed("object #" + id + " is absent from parent #" + parent + " children");
        }
      }
      for (long child : object.children()) {
        if (child == -1) continue;
        if (!Objects.requireNonNull(repaired.get(child)).parents().contains(id)) {
          throw malformed("child #" + child + " does not name parent #" + id);
        }
      }
      for (long content : object.contents()) {
        if (content == -1) continue;
        if (Objects.requireNonNull(repaired.get(content)).location() != id) {
          throw malformed("content #" + content + " does not name location #" + id);
        }
      }
    }
    return repaired;
  }

  private long repairLocation(RawObject object, Map<Long, RawObject> objects) throws IOException {
    if (!(object.location() instanceof ObjectValue location)) {
      throw malformed("object #" + object.id() + ".location is not an object");
    }
    long objectId = location.value();
    if (objectId != -1 && !objects.containsKey(objectId)) {
      serverLog.error(
          "*** VALIDATE: #"
              + object.id()
              + ".location = #"
              + objectId
              + " <invalid> ... fixed.");
      return -1;
    }
    return objectId;
  }

  private List<Long> repairParents(RawObject object, Map<Long, RawObject> objects) throws IOException {
    if (object.parents() instanceof ObjectValue parent) {
      return parent.value() == -1
          ? List.of()
          : repairReferences(object.id(), List.of(parent), "parent", objects);
    }
    if (object.parents() instanceof ListValue parents) {
      if (parents.elements().stream().anyMatch(value -> !(value instanceof ObjectValue))) {
        throw malformed("object #" + object.id() + ".parents is not an object or list of objects");
      }
      return repairReferences(object.id(), parents.elements(), "parent", objects);
    }
    throw malformed("object #" + object.id() + ".parents is not an object or list of objects");
  }

  private List<Long> repairObjectList(
      RawObject object,
      MooValue value,
      String field,
      String member,
      Map<Long, RawObject> objects) throws IOException {
    if (!(value instanceof ListValue list)) {
      throw malformed("object #" + object.id() + "." + field + " is not a list of objects");
    }
    if (list.elements().stream().anyMatch(element -> !(element instanceof ObjectValue))) {
      throw malformed("object #" + object.id() + "." + field + " is not a list of objects");
    }
    return repairReferences(object.id(), list.elements(), member, objects);
  }

  private List<Long> repairReferences(
      long owner, List<MooValue> values, String field, Map<Long, RawObject> objects) {
    List<Long> retained = new ArrayList<>();
    for (MooValue value : values) {
      if (!(value instanceof ObjectValue reference)) {
        continue;
      }
      long objectId = reference.value();
      if (objectId != -1 && !objects.containsKey(objectId)) {
        serverLog.error(
            "*** VALIDATE: #"
                + owner
                + "."
                + field
                + " = #"
                + objectId
                + " <invalid> ... removed.");
      } else {
        retained.add(objectId);
      }
    }
    return List.copyOf(retained);
  }

  private static boolean hasParentCycle(
      long objectId, Map<Long, RepairedObject> objects, Set<Long> path) {
    if (!path.add(objectId)) {
      return true;
    }
    for (long parent : Objects.requireNonNull(objects.get(objectId)).parents()) {
      if (parent == -1) continue;
      if (hasParentCycle(parent, objects, path)) {
        return true;
      }
    }
    path.remove(objectId);
    return false;
  }

  private static boolean hasLocationCycle(long objectId, Map<Long, RepairedObject> objects) {
    Set<Long> path = new LinkedHashSet<>();
    long cursor = objectId;
    while (cursor != -1) {
      if (!path.add(cursor)) {
        return true;
      }
      cursor = Objects.requireNonNull(objects.get(cursor)).location();
    }
    return false;
  }

  private static List<WorldObject> restoreObjects(
      Map<Long, RawObject> rawObjects,
      Map<Long, RepairedObject> repaired,
      Map<ProgramSlot, String> programs) throws IOException {
    Map<Long, PropertySlotRestorer.Input> inputs = new LinkedHashMap<>();
    for (RawObject raw : rawObjects.values()) {
      inputs.put(raw.id(), new PropertySlotRestorer.Input(raw.id(), Objects.requireNonNull(repaired.get(raw.id())).parents(),
          raw.propertyNames(), raw.propertySlots()));
    }
    Map<Long, List<WorldProperty>> properties = PropertySlotRestorer.restore(inputs);
    List<WorldObject> objects = new ArrayList<>(rawObjects.size());
    for (RawObject raw : rawObjects.values()) {
      List<WorldVerb> verbs = new ArrayList<>(raw.verbs().size());
      for (int index = 0; index < raw.verbs().size(); index++) {
        RawVerb verb = raw.verbs().get(index);
        verbs.add(
            verb.compile(programs.getOrDefault(new ProgramSlot(raw.id(), index), "")));
      }
      RepairedObject topology = Objects.requireNonNull(repaired.get(raw.id()));
      objects.add(
          new WorldObject(
              raw.id(),
              raw.name(),
              raw.flags(),
              raw.owner(),
              topology.location(),
              topology.parents(),
              topology.contents(),
              topology.children(),
              verbs,
              Objects.requireNonNull(properties.get(raw.id()))));
    }
    return List.copyOf(objects);
  }

  private static void validatePlayers(List<Long> players, Map<Long, RawObject> objects)
      throws IOException {
    for (long player : players) {
      if (!objects.containsKey(player)) {
        throw malformed("players list references missing object #" + player);
      }
    }
  }

  private record RawObject(
      long id,
      String name,
      int flags,
      long owner,
      MooValue location,
      MooValue contents,
      MooValue parents,
      MooValue children,
      List<RawVerb> verbs,
      List<String> propertyNames,
      List<RawPropertySlot> propertySlots) {}

  private record RepairedObject(
      long location, List<Long> contents, List<Long> parents, List<Long> children) {
    private RepairedObject {
      contents = List.copyOf(contents);
      parents = List.copyOf(parents);
      children = List.copyOf(children);
    }

  }
}
