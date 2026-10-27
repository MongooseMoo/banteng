package world.mongoose.banteng.persistence;

import static world.mongoose.banteng.persistence.DbScanner.malformed;
import static world.mongoose.banteng.persistence.DbScanner.readCount;
import static world.mongoose.banteng.persistence.DbScanner.readInt;
import static world.mongoose.banteng.persistence.DbScanner.readLong;
import static world.mongoose.banteng.persistence.DbScanner.requireExact;
import static world.mongoose.banteng.persistence.DbScanner.requiredLine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import world.mongoose.banteng.world.ActiveConnection;
import world.mongoose.banteng.world.Checkpoint;
import world.mongoose.banteng.world.DurableTask;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldSnapshot;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

/** A streaming ISO-8859-1 reader for the authoritative LambdaMOO v4 slice. */
public final class LambdaMooV4Reader implements DbCodec {
  private static final String HEADER = "** LambdaMOO Database, Format Version 4 **";

  private final DbCodec output;

  /** Receives the application's canonical checkpoint output codec. */
  public LambdaMooV4Reader(DbCodec output) {
    this.output = Objects.requireNonNull(output, "output");
  }

  /** Reads and validates the exact supported v4 database contract. */
  @Override
  public Checkpoint read(Path database) throws IOException {
    Objects.requireNonNull(database, "database");
    try (BufferedReader input = Files.newBufferedReader(database, StringValue.charset())) {
      requireExact(input, HEADER, "v4 header");
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
      for (int slot = 0; slot < objectSlotCount; slot++) {
        RawObject object = readObject(input, slot);
        if (object != null) rawObjects.put(object.id(), object);
      }

      Map<ProgramSlot, String> programs = ProgramSectionReader.read(input, programCount,
          objectId -> {
            RawObject object = rawObjects.get(objectId);
            return object == null ? -1 : object.verbs().size();
          });
      TaskSectionReader.Tail tail = new TaskSectionReader(4,
          (reader, tag, location) -> ValueTagDecoder.readV5(reader, tag)).read(input);
      if (input.readLine() != null) throw malformed("unexpected data after task tail");

      repairMissingReferences(rawObjects);
      validatePlayers(players, rawObjects);
      Map<Long, List<Long>> children = deriveChildren(rawObjects);
      Map<Long, List<Long>> contents = deriveContents(rawObjects);
      validateRelations(rawObjects, children, contents);
      return new Checkpoint(buildWorld(players, rawObjects, programs, children, contents, objectSlotCount),
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

  private static @org.jspecify.annotations.Nullable RawObject readObject(BufferedReader input, int slot) throws IOException {
    String header = requiredLine(input, "object slot " + slot);
    if (header.equals("#" + slot + " recycled")) return null;
    if (!header.equals("#" + slot)) throw malformed("invalid object slot " + slot);
    String name = requiredLine(input, "object #" + slot + " name");
    requiredLine(input, "object #" + slot + " handles");
    int flags = readInt(input, "object #" + slot + " flags");
    long owner = readLong(input, "object #" + slot + " owner");
    long location = readLong(input, "object #" + slot + " location");
    long firstContent = readLong(input, "object #" + slot + " contents head");
    long nextContent = readLong(input, "object #" + slot + " contents link");
    long parent = readLong(input, "object #" + slot + " parent");
    long firstChild = readLong(input, "object #" + slot + " child head");
    long sibling = readLong(input, "object #" + slot + " sibling link");

    int verbCount = readCount(input, "object #" + slot + " verb count");
    List<RawVerb> verbs = new ArrayList<>(verbCount);
    for (int index = 0; index < verbCount; index++) {
      verbs.add(
          new RawVerb(
              requiredLine(input, "object #" + slot + " verb names"),
              readLong(input, "object #" + slot + " verb owner"),
              readInt(input, "object #" + slot + " verb permissions"),
              readInt(input, "object #" + slot + " verb preposition")));
    }

    int propertyNameCount = readCount(input, "object #" + slot + " property-name count");
    List<String> propertyNames = new ArrayList<>(propertyNameCount);
    for (int index = 0; index < propertyNameCount; index++) {
      propertyNames.add(requiredLine(input, "object #" + slot + " property name"));
    }

    int propertySlotCount = readCount(input, "object #" + slot + " property-slot count");
    List<RawPropertySlot> properties = new ArrayList<>(propertySlotCount);
    for (int index = 0; index < propertySlotCount; index++) {
      int tag = readInt(input, "property value tag");
      properties.add(new RawPropertySlot(
          tag == V17Format.ValueTag.CLEAR.code() ? null : ValueTagDecoder.readV5(input, tag),
          tag == V17Format.ValueTag.CLEAR.code(), readLong(input, "property owner"),
          readInt(input, "property permissions")));
    }

    return new RawObject(
        slot,
        name,
        flags,
        owner,
        location,
        firstContent,
        nextContent,
        parent,
        firstChild,
        sibling,
        verbs,
        propertyNames,
        properties);
  }


  private static void repairMissingReferences(Map<Long, RawObject> objects) {
    for (RawObject raw : List.copyOf(objects.values())) {
      long location = existing(raw.location(), objects);
      objects.put(raw.id(), new RawObject(raw.id(), raw.name(), raw.flags(), raw.owner(),
          location, existing(raw.firstContent(), objects),
          raw.location() == -1 ? -1 : existing(raw.nextContent(), objects),
          existing(raw.parent(), objects), existing(raw.firstChild(), objects),
          existing(raw.sibling(), objects), raw.verbs(), raw.propertyNames(), raw.properties()));
    }
  }

  private static long existing(long id, Map<Long, RawObject> objects) {
    return objects.containsKey(id) ? id : -1;
  }

  private static Map<Long, List<Long>> deriveChildren(Map<Long, RawObject> objects)
      throws IOException {
    Map<Long, List<Long>> result = new HashMap<>();
    Set<Long> linked = new HashSet<>();
    for (RawObject parent : objects.values()) {
      List<Long> children = new ArrayList<>();
      Set<Long> chain = new HashSet<>();
      long childId = parent.firstChild();
      while (childId != -1) {
        RawObject child = objects.get(childId);
        if (child == null) {
          throw malformed("object #" + parent.id() + " has missing child #" + childId);
        }
        if (!chain.add(childId)) {
          throw malformed("child chain for #" + parent.id() + " contains a cycle");
        }
        if (!linked.add(childId)) {
          throw malformed("object #" + childId + " occurs in multiple child chains");
        }
        if (child.parent() != parent.id()) {
          throw malformed("child #" + childId + " does not name parent #" + parent.id());
        }
        children.add(childId);
        childId = child.sibling();
      }
      result.put(parent.id(), List.copyOf(children));
    }
    return result;
  }

  private static Map<Long, List<Long>> deriveContents(Map<Long, RawObject> objects)
      throws IOException {
    Map<Long, List<Long>> result = new HashMap<>();
    Set<Long> linked = new HashSet<>();
    for (RawObject container : objects.values()) {
      List<Long> contents = new ArrayList<>();
      Set<Long> chain = new HashSet<>();
      long contentId = container.firstContent();
      while (contentId != -1) {
        RawObject content = objects.get(contentId);
        if (content == null) {
          throw malformed("object #" + container.id() + " contains missing object #" + contentId);
        }
        if (!chain.add(contentId)) {
          throw malformed("contents chain for #" + container.id() + " contains a cycle");
        }
        if (!linked.add(contentId)) {
          throw malformed("object #" + contentId + " occurs in multiple contents chains");
        }
        if (content.location() != container.id()) {
          throw malformed("content #" + contentId + " does not name location #" + container.id());
        }
        contents.add(contentId);
        contentId = content.nextContent();
      }
      result.put(container.id(), List.copyOf(contents));
    }
    return result;
  }

  private static void validatePlayers(List<Long> players, Map<Long, RawObject> objects)
      throws IOException {
    Set<Long> unique = new HashSet<>();
    for (long player : players) {
      if (!objects.containsKey(player)) {
        throw malformed("players list references missing object #" + player);
      }
      if (!unique.add(player)) {
        throw malformed("players list repeats object #" + player);
      }
    }
  }

  private static void validateRelations(
      Map<Long, RawObject> objects, Map<Long, List<Long>> children, Map<Long, List<Long>> contents)
      throws IOException {
    for (RawObject raw : objects.values()) {
      Set<Long> parents = new HashSet<>();
      for (long id = raw.id(); id != -1; id = Objects.requireNonNull(objects.get(id)).parent()) {
        if (!parents.add(id)) throw malformed("cyclic parent hierarchy at object #" + raw.id());
      }
      Set<Long> locations = new HashSet<>();
      for (long id = raw.id(); id != -1; id = Objects.requireNonNull(objects.get(id)).location()) {
        if (!locations.add(id)) throw malformed("cyclic location hierarchy at object #" + raw.id());
      }
    }
    Set<Long> linkedChildren = flatten(children);
    Set<Long> linkedContents = flatten(contents);
    for (RawObject object : objects.values()) {
      if (object.parent() != -1 && !linkedChildren.contains(object.id())) {
        throw malformed("object #" + object.id() + " has an invalid parent relation");
      }
      if (object.location() != -1 && !linkedContents.contains(object.id())) {
        throw malformed("object #" + object.id() + " has an invalid location relation");
      }
    }
  }

  private static Set<Long> flatten(Map<Long, List<Long>> chains) {
    Set<Long> result = new HashSet<>();
    for (List<Long> chain : chains.values()) {
      result.addAll(chain);
    }
    return result;
  }

  private static WorldTxn buildWorld(
      List<Long> players,
      Map<Long, RawObject> rawObjects,
      Map<ProgramSlot, String> programs,
      Map<Long, List<Long>> children,
      Map<Long, List<Long>> contents, int objectSlotCount)
      throws IOException {
    Map<Long, PropertySlotRestorer.Input> inputs = new LinkedHashMap<>();
    for (RawObject raw : rawObjects.values()) {
      inputs.put(raw.id(), new PropertySlotRestorer.Input(raw.id(),
          raw.parent() == -1 ? List.of() : List.of(raw.parent()), raw.propertyNames(), raw.properties()));
    }
    Map<Long, List<WorldProperty>> properties = PropertySlotRestorer.restore(inputs);
    List<WorldObject> objects = new ArrayList<>(rawObjects.size());
    for (RawObject raw : rawObjects.values()) {
      List<WorldVerb> verbs = new ArrayList<>(raw.verbs().size());
      for (int index = 0; index < raw.verbs().size(); index++) {
        RawVerb rawVerb = raw.verbs().get(index);
        String source = programs.get(new ProgramSlot(raw.id(), index));
        if (source == null) source = "";
        verbs.add(
            rawVerb.compile(source));
      }
      objects.add(
          new WorldObject(
              raw.id(),
              raw.name(),
              raw.flags(),
              raw.owner(),
              raw.location(),
              raw.parent(),
              Objects.requireNonNull(contents.get(raw.id())),
              Objects.requireNonNull(children.get(raw.id())),
              verbs,
              Objects.requireNonNull(properties.get(raw.id()))));
    }
    return new WorldTxn(players, objects, Map.of(), Map.of(), List.of(), objectSlotCount - 1L);
  }

  private record RawObject(
      long id,
      String name,
      int flags,
      long owner,
      long location,
      long firstContent,
      long nextContent,
      long parent,
      long firstChild,
      long sibling,
      List<RawVerb> verbs,
      List<String> propertyNames,
      List<RawPropertySlot> properties) {
    private RawObject {
      verbs = List.copyOf(verbs);
      properties = List.copyOf(properties);
    }
  }
}
