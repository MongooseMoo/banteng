package moo.persistence;

import java.io.BufferedReader;
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
import moo.logging.ServerLog;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.world.WorldObject;
import moo.world.WorldProperty;
import moo.world.WorldTxn;
import moo.world.WorldVerb;
import org.jspecify.annotations.Nullable;

/** Reader and topology repairer for LambdaMOO's first next-generation database format. */
public final class LambdaMooV5Reader {
  private static final String HEADER = "** LambdaMOO Database, Format Version 5 **";
  private final ServerLog serverLog;

  /** Creates a reader that emits validation diagnostics to standard error. */
  public LambdaMooV5Reader() {
    this(ServerLog.stderr(System.Logger.Level.INFO));
  }

  /** Creates a reader that emits validation diagnostics through the supplied server log. */
  public LambdaMooV5Reader(ServerLog serverLog) {
    this.serverLog = Objects.requireNonNull(serverLog, "serverLog");
  }

  /** Reads a v5 world, applying the same three topology-repair phases as LambdaMOO. */
  public WorldTxn read(Path database) throws IOException {
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
      Map<ProgramSlot, String> programs = readPrograms(input, programCount, rawObjects);
      requireExact(input, "0 clocks", "clocks tail");
      requireExact(input, "0 queued tasks", "queued-tasks tail");
      requireExact(input, "0 suspended tasks", "suspended-tasks tail");
      if (input.readLine() != null) {
        throw malformed("unsupported v5 tail after suspended tasks");
      }

      validatePlayers(players, rawObjects);
      Map<Long, RepairedObject> repaired = repairTopology(rawObjects);
      List<WorldObject> objects = restoreObjects(rawObjects, repaired, programs);
      return new WorldTxn(
          players, objects, Map.of(), Map.of(), List.of(), objectSlotCount - 1L);
    }
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
    MooValue location = readValue(input);
    MooValue contents = readValue(input);
    MooValue parents = readValue(input);
    MooValue children = readValue(input);

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
              tag == 5 ? null : readValue(input, tag),
              tag == 5,
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

  private static Map<ProgramSlot, String> readPrograms(
      BufferedReader input, int programCount, Map<Long, RawObject> objects) throws IOException {
    Map<ProgramSlot, String> programs = new LinkedHashMap<>();
    for (int count = 0; count < programCount; count++) {
      String header = requiredLine(input, "program header");
      int colon = header.indexOf(':');
      if (!header.startsWith("#") || colon <= 1 || colon != header.lastIndexOf(':')) {
        throw malformed("invalid program header: " + header);
      }
      long objectId = parseLong(header.substring(1, colon), "program object");
      int verbIndex = parseCount(header.substring(colon + 1), "program verb index");
      RawObject object = objects.get(objectId);
      if (object == null || verbIndex >= object.verbs().size()) {
        throw malformed("program references missing verb #" + objectId + ":" + verbIndex);
      }
      StringBuilder source = new StringBuilder();
      while (true) {
        String line = requiredLine(input, "program source");
        if (line.equals(".")) {
          break;
        }
        source.append(line).append('\n');
      }
      if (programs.putIfAbsent(new ProgramSlot(objectId, verbIndex), source.toString()) != null) {
        throw malformed("duplicate program #" + objectId + ":" + verbIndex);
      }
    }
    return programs;
  }

  private Map<Long, RepairedObject> repairTopology(Map<Long, RawObject> objects) {
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
    for (long objectId : parentCycles) {
      RepairedObject object = Objects.requireNonNull(repaired.get(objectId));
      repaired.put(objectId, object.withParents(List.of()));
    }
    for (long objectId : locationCycles) {
      RepairedObject object = Objects.requireNonNull(repaired.get(objectId));
      repaired.put(objectId, object.withLocation(-1));
    }

    serverLog.info("VALIDATE: checking object relationships ...");
    for (long objectId : objects.keySet()) {
      RepairedObject object = Objects.requireNonNull(repaired.get(objectId));
      if (object.location() != -1
          && !Objects.requireNonNull(repaired.get(object.location()))
              .contents()
              .contains(objectId)) {
        serverLog.error(
            "*** VALIDATE: #"
                + objectId
                + " not in it's location's (#"
                + object.location()
                + ") contents.");
        object = object.withLocation(-1);
      }
      List<Long> retainedParents = new ArrayList<>();
      for (long parentId : object.parents()) {
        if (Objects.requireNonNull(repaired.get(parentId)).children().contains(objectId)) {
          retainedParents.add(parentId);
        } else {
          serverLog.error(
              "*** VALIDATE: #"
                  + objectId
                  + " not in it's parent's (#"
                  + parentId
                  + ") children.");
        }
      }
      repaired.put(objectId, object.withParents(retainedParents));
    }
    for (long objectId : objects.keySet()) {
      RepairedObject object = Objects.requireNonNull(repaired.get(objectId));
      List<Long> retainedChildren = new ArrayList<>();
      for (long childId : object.children()) {
        if (Objects.requireNonNull(repaired.get(childId)).parents().contains(objectId)) {
          retainedChildren.add(childId);
        } else {
          serverLog.error(
              "*** VALIDATE: #"
                  + objectId
                  + " not in it's child's (#"
                  + childId
                  + ") parents.");
        }
      }
      List<Long> retainedContents = new ArrayList<>();
      for (long contentId : object.contents()) {
        if (Objects.requireNonNull(repaired.get(contentId)).location() == objectId) {
          retainedContents.add(contentId);
        } else {
          serverLog.error(
              "*** VALIDATE: #"
                  + objectId
                  + " not in it's content's (#"
                  + contentId
                  + ") location.");
        }
      }
      repaired.put(objectId, object.withChildren(retainedChildren).withContents(retainedContents));
    }
    return repaired;
  }

  private long repairLocation(RawObject object, Map<Long, RawObject> objects) {
    if (!(object.location() instanceof ObjectValue location)) {
      serverLog.error(
          "*** VALIDATE: #" + object.id() + ".location is not an object.");
      return -1;
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

  private List<Long> repairParents(RawObject object, Map<Long, RawObject> objects) {
    if (object.parents() instanceof ObjectValue parent) {
      return parent.value() == -1
          ? List.of()
          : repairReferences(object.id(), List.of(parent), "parent", objects);
    }
    if (object.parents() instanceof ListValue parents) {
      if (parents.elements().stream().anyMatch(value -> !(value instanceof ObjectValue))) {
        serverLog.error(
            "*** VALIDATE: #"
                + object.id()
                + ".parents is not an object or list of objects.");
        return List.of();
      }
      return repairReferences(object.id(), parents.elements(), "parent", objects);
    }
    serverLog.error(
        "*** VALIDATE: #" + object.id() + ".parents is not an object or list of objects.");
    return List.of();
  }

  private List<Long> repairObjectList(
      RawObject object,
      MooValue value,
      String field,
      String member,
      Map<Long, RawObject> objects) {
    if (!(value instanceof ListValue list)) {
      serverLog.error("*** VALIDATE: #" + object.id() + "." + field + " is not a list of objects.");
      return List.of();
    }
    if (list.elements().stream().anyMatch(element -> !(element instanceof ObjectValue))) {
      serverLog.error("*** VALIDATE: #" + object.id() + "." + field + " is not a list of objects.");
      return List.of();
    }
    return repairReferences(object.id(), list.elements(), member, objects);
  }

  private List<Long> repairReferences(
      long owner, List<MooValue> values, String field, Map<Long, RawObject> objects) {
    Set<Long> retained = new LinkedHashSet<>();
    for (MooValue value : values) {
      if (!(value instanceof ObjectValue reference)) {
        continue;
      }
      long objectId = reference.value();
      if (!objects.containsKey(objectId)) {
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
      Map<ProgramSlot, String> programs) {
    List<WorldObject> objects = new ArrayList<>(rawObjects.size());
    for (RawObject raw : rawObjects.values()) {
      List<WorldVerb> verbs = new ArrayList<>(raw.verbs().size());
      for (int index = 0; index < raw.verbs().size(); index++) {
        RawVerb verb = raw.verbs().get(index);
        verbs.add(
            new WorldVerb(
                verb.names(),
                verb.owner(),
                verb.permissions(),
                verb.preposition(),
                programs.getOrDefault(new ProgramSlot(raw.id(), index), "")));
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
              restoreLocalProperties(raw)));
    }
    return List.copyOf(objects);
  }

  private static List<WorldProperty> restoreLocalProperties(RawObject object) {
    int localCount = Math.min(object.propertyNames().size(), object.propertySlots().size());
    List<WorldProperty> properties = new ArrayList<>(localCount);
    for (int index = 0; index < localCount; index++) {
      RawPropertySlot slot = object.propertySlots().get(index);
      if (slot.clear() || slot.value() == null) {
        continue;
      }
      properties.add(
          new WorldProperty(
              object.propertyNames().get(index),
              slot.value(),
              slot.owner(),
              slot.permissions(),
              false,
              true));
    }
    return List.copyOf(properties);
  }

  private static MooValue readValue(BufferedReader input) throws IOException {
    return readValue(input, readInt(input, "value tag"));
  }

  private static MooValue readValue(BufferedReader input, int tag) throws IOException {
    return switch (tag) {
      case 0 -> new IntegerValue(readLong(input, "integer value"));
      case 1 -> new ObjectValue(readLong(input, "object value"));
      case 2 ->
          StringValue.of(requiredLine(input, "string value"));
      case 3 -> {
        long code = readLong(input, "error value");
        yield ErrorValue.fromCode(code & 0xffff_ffffL)
            .orElseThrow(() -> malformed("unsupported error value " + code));
      }
      case 4 -> {
        int count = readCount(input, "list count");
        List<MooValue> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
          values.add(readValue(input));
        }
        yield new ListValue(values);
      }
      case 9 -> new FloatValue(readDouble(input, "float value"));
      default -> throw malformed("unsupported v5 value tag " + tag);
    };
  }

  private static void validatePlayers(List<Long> players, Map<Long, RawObject> objects)
      throws IOException {
    for (long player : players) {
      if (!objects.containsKey(player)) {
        throw malformed("players list references missing object #" + player);
      }
    }
  }

  private static int readCount(BufferedReader input, String field) throws IOException {
    return parseCount(requiredLine(input, field), field);
  }

  private static int parseCount(String text, String field) throws IOException {
    int value = readInt(text, field);
    if (value < 0) {
      throw malformed(field + " must not be negative");
    }
    return value;
  }

  private static int readInt(BufferedReader input, String field) throws IOException {
    return readInt(requiredLine(input, field), field);
  }

  private static int readInt(String text, String field) throws IOException {
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException error) {
      throw malformed("invalid " + field + ": " + text, error);
    }
  }

  private static long readLong(BufferedReader input, String field) throws IOException {
    return parseLong(requiredLine(input, field), field);
  }

  private static long parseLong(String text, String field) throws IOException {
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException error) {
      throw malformed("invalid " + field + ": " + text, error);
    }
  }

  private static double readDouble(BufferedReader input, String field) throws IOException {
    String text = requiredLine(input, field);
    try {
      return Double.parseDouble(text);
    } catch (NumberFormatException error) {
      throw malformed("invalid " + field + ": " + text, error);
    }
  }

  private static void requireExact(BufferedReader input, String expected, String field)
      throws IOException {
    String actual = requiredLine(input, field);
    if (!actual.equals(expected)) {
      throw malformed("invalid " + field + ": " + actual);
    }
  }

  private static String requiredLine(BufferedReader input, String field) throws IOException {
    String line = input.readLine();
    if (line == null) {
      throw malformed("unexpected end of file while reading " + field);
    }
    return line;
  }

  private static IOException malformed(String message) {
    return new IOException(message);
  }

  private static IOException malformed(String message, Throwable cause) {
    return new IOException(message, cause);
  }

  private record ProgramSlot(long objectId, int verbIndex) {}

  private record RawVerb(String names, long owner, int permissions, int preposition) {}

  private record RawPropertySlot(
      @Nullable MooValue value, boolean clear, long owner, int permissions) {}

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

    RepairedObject withLocation(long value) {
      return new RepairedObject(value, contents, parents, children);
    }

    RepairedObject withContents(List<Long> value) {
      return new RepairedObject(location, value, parents, children);
    }

    RepairedObject withParents(List<Long> value) {
      return new RepairedObject(location, contents, value, children);
    }

    RepairedObject withChildren(List<Long> value) {
      return new RepairedObject(location, contents, parents, value);
    }
  }
}
