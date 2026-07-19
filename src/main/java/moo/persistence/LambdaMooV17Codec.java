package moo.persistence;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.vm.VmSnapshot;
import moo.world.WorldObject;
import moo.world.WorldProperty;
import moo.world.WorldSnapshot;
import moo.world.WorldTxn;
import moo.world.WorldVerb;
import org.jspecify.annotations.Nullable;

/** Streaming Latin-1 reader and atomic writer for the Phase 2 LambdaMOO v17 slice. */
public final class LambdaMooV17Codec {
  private static final String HEADER = "** LambdaMOO Database, Format Version 17 **";

  /** A restored committed world and its durable task snapshots. */
  public record Checkpoint(WorldTxn world, List<VmSnapshot> tasks) {
    /** Takes an immutable snapshot of the restored task list. */
    public Checkpoint {
      Objects.requireNonNull(world, "world");
      tasks = List.copyOf(tasks);
    }
  }

  /** Writes a byte-stable v17 checkpoint through an atomic same-directory replacement. */
  public void writeAtomic(Path checkpoint, WorldSnapshot world, List<VmSnapshot> tasks)
      throws IOException {
    Objects.requireNonNull(checkpoint, "checkpoint");
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(tasks, "tasks");
    if (!tasks.isEmpty()) {
      throw new IOException("Phase 2 v17 checkpoints require an empty durable task list");
    }

    Path target = checkpoint.toAbsolutePath().normalize();
    Path directory = Objects.requireNonNull(target.getParent(), "checkpoint parent directory");
    Files.createDirectories(directory);
    Path temporary = Files.createTempFile(directory, target.getFileName() + ".", ".tmp");
    CheckpointEvent event = new CheckpointEvent();
    event.revision = world.revision();
    event.objectCount = world.objects().size();
    event.taskCount = tasks.size();
    event.begin();
    boolean promoted = false;
    try {
      try (FileChannel channel =
              FileChannel.open(
                  temporary,
                  StandardOpenOption.WRITE,
                  StandardOpenOption.TRUNCATE_EXISTING);
          BufferedWriter output =
              new BufferedWriter(
                  new OutputStreamWriter(
                      Channels.newOutputStream(channel), StandardCharsets.ISO_8859_1))) {
        write(output, world);
        output.flush();
        channel.force(true);
      }
      Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING);
      promoted = true;
      try (FileChannel directoryChannel = FileChannel.open(directory, StandardOpenOption.READ)) {
        directoryChannel.force(true);
      }
      event.bytesWritten = Files.size(target);
      event.success = true;
    } finally {
      if (!promoted) {
        Files.deleteIfExists(temporary);
      }
      event.commit();
    }
  }

  /** Reads the exact minimum v17 checkpoint emitted by {@link #writeAtomic}. */
  public Checkpoint read(Path database) throws IOException {
    Objects.requireNonNull(database, "database");
    try (BufferedReader input =
        Files.newBufferedReader(database, StandardCharsets.ISO_8859_1)) {
      requireExact(input, HEADER, "v17 header");
      int playerCount = readCount(input, "player count");
      List<Long> players = new ArrayList<>(playerCount);
      for (int index = 0; index < playerCount; index++) {
        players.add(readLong(input, "player object"));
      }

      requireExact(input, "0 values pending finalization", "pending-finalization count");
      requireExact(input, "0 clocks", "clocks count");
      requireExact(input, "0 queued tasks", "queued-task count");
      requireExact(input, "0 suspended tasks", "suspended-task count");
      requireExact(input, "0 interrupted tasks", "interrupted-task count");
      requireExact(input, "0 active connections with listeners", "active-connection count");

      Map<Long, RawObject> objects = new LinkedHashMap<>();
      long expectedObjectId = 0;
      while (true) {
        int batchCount = readCount(input, "object batch count");
        if (batchCount == 0) {
          break;
        }
        for (int index = 0; index < batchCount; index++) {
          RawObject object = readObject(input, expectedObjectId++);
          if (object != null) {
            if (objects.putIfAbsent(object.id(), object) != null) {
              throw malformed("duplicate object #" + object.id());
            }
          }
        }
      }

      int programCount = readCount(input, "program count");
      Map<ProgramSlot, String> programs = new LinkedHashMap<>();
      for (int index = 0; index < programCount; index++) {
        readProgram(input, objects, programs);
      }
      if (input.readLine() != null) {
        throw malformed("unexpected v17 data after program section");
      }

      List<WorldObject> restored = restoreObjects(objects, programs);
      return new Checkpoint(new WorldTxn(players, restored), List.of());
    }
  }

  private static void write(BufferedWriter output, WorldSnapshot world) throws IOException {
    line(output, HEADER);
    line(output, world.players().size());
    for (long player : world.players()) {
      line(output, player);
    }
    line(output, "0 values pending finalization");
    line(output, "0 clocks");
    line(output, "0 queued tasks");
    line(output, "0 suspended tasks");
    line(output, "0 interrupted tasks");
    line(output, "0 active connections with listeners");

    List<WorldObject> objects =
        world.objects().values().stream()
            .sorted(Comparator.comparingLong(WorldObject::id))
            .toList();
    long maximumObjectId = objects.isEmpty() ? -1 : objects.getLast().id();
    if (maximumObjectId > Integer.MAX_VALUE - 1L) {
      throw new IOException("v17 object slot count exceeds supported range");
    }
    line(output, maximumObjectId + 1);
    int objectIndex = 0;
    for (long objectId = 0; objectId <= maximumObjectId; objectId++) {
      if (objectIndex >= objects.size() || objects.get(objectIndex).id() != objectId) {
        line(output, "#" + objectId + " recycled");
      } else {
        writeObject(output, objects.get(objectIndex++));
      }
    }
    line(output, 0);

    int programCount = objects.stream().mapToInt(object -> object.verbs().size()).sum();
    line(output, programCount);
    for (WorldObject object : objects) {
      for (int verbIndex = 0; verbIndex < object.verbs().size(); verbIndex++) {
        line(output, "#" + object.id() + ":" + verbIndex);
        String source = object.verbs().get(verbIndex).programSource();
        output.write(source);
        if (!source.isEmpty() && source.charAt(source.length() - 1) != '\n') {
          output.write('\n');
        }
        line(output, ".");
      }
    }
  }

  private static void writeObject(BufferedWriter output, WorldObject object) throws IOException {
    line(output, "#" + object.id());
    lineString(output, object.name(), "object name");
    line(output, object.flags());
    line(output, object.owner());
    writeValue(output, new ObjectValue(object.location()));
    writeValue(output, new IntegerValue(0));
    writeObjectList(output, object.contents());
    writeValue(output, new ObjectValue(object.parent()));
    writeObjectList(output, object.children());

    line(output, object.verbs().size());
    for (WorldVerb verb : object.verbs()) {
      lineString(output, verb.names(), "verb names");
      line(output, verb.owner());
      line(output, verb.permissions());
      line(output, verb.preposition());
    }

    long definitionCount = object.properties().stream().filter(WorldProperty::defined).count();
    line(output, definitionCount);
    for (WorldProperty property : object.properties()) {
      if (property.defined()) {
        lineString(output, property.name(), "property name");
      }
    }
    line(output, object.properties().size());
    for (WorldProperty property : object.properties()) {
      if (property.clear()) {
        line(output, 5);
      } else {
        writeValue(output, property.value());
      }
      line(output, property.owner());
      line(output, property.permissions());
    }
  }

  private static void writeObjectList(BufferedWriter output, List<Long> objectIds)
      throws IOException {
    List<MooValue> values = objectIds.stream().map(ObjectValue::new).map(MooValue.class::cast).toList();
    writeValue(output, new ListValue(values));
  }

  private static void writeValue(BufferedWriter output, MooValue value) throws IOException {
    line(output, value.type().code());
    switch (value) {
      case IntegerValue integer -> line(output, integer.value());
      case ObjectValue object -> line(output, object.value());
      case StringValue string ->
          lineString(
              output,
              new String(string.bytes(), StandardCharsets.ISO_8859_1),
              "string value");
      case ErrorValue error -> line(output, error.code());
      case FloatValue floating ->
          line(output, String.format(Locale.ROOT, "%.19g", floating.value()));
      case ListValue list -> {
        line(output, list.size());
        for (MooValue element : list.elements()) {
          writeValue(output, element);
        }
      }
      case MapValue map -> {
        line(output, map.size());
        for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
          writeValue(output, entry.getKey());
          writeValue(output, entry.getValue());
        }
      }
      default -> throw new IOException("unsupported Phase 2 v17 value: " + value.type());
    }
  }

  private static @Nullable RawObject readObject(BufferedReader input, long expectedId)
      throws IOException {
    String header = requiredLine(input, "object #" + expectedId + " header");
    if (header.equals("#" + expectedId + " recycled")
        || header.equals("# " + expectedId + " recycled")) {
      return null;
    }
    if (!header.equals("#" + expectedId)) {
      throw malformed("invalid object #" + expectedId + " header: " + header);
    }
    String name = requiredLine(input, "object #" + expectedId + " name");
    int flags = readInt(input, "object #" + expectedId + " flags");
    long owner = readLong(input, "object #" + expectedId + " owner");
    long location = requireObject(readValue(input), "object #" + expectedId + " location");
    readValue(input); // last_move is not represented in the Phase 2 world record.
    List<Long> contents = requireObjectList(readValue(input), "object #" + expectedId + " contents");
    long parent = requireSingleParent(readValue(input), "object #" + expectedId + " parents");
    List<Long> children = requireObjectList(readValue(input), "object #" + expectedId + " children");

    int verbCount = readCount(input, "object #" + expectedId + " verb count");
    List<RawVerb> verbs = new ArrayList<>(verbCount);
    for (int index = 0; index < verbCount; index++) {
      verbs.add(
          new RawVerb(
              requiredLine(input, "verb names"),
              readLong(input, "verb owner"),
              readInt(input, "verb permissions"),
              readInt(input, "verb preposition")));
    }

    int propertyNameCount = readCount(input, "object #" + expectedId + " property-name count");
    List<String> propertyNames = new ArrayList<>(propertyNameCount);
    for (int index = 0; index < propertyNameCount; index++) {
      propertyNames.add(requiredLine(input, "property name"));
    }
    int propertyValueCount = readCount(input, "object #" + expectedId + " property-value count");
    List<RawPropertySlot> propertySlots = new ArrayList<>(propertyValueCount);
    for (int index = 0; index < propertyValueCount; index++) {
      int tag = readInt(input, "property value tag");
      propertySlots.add(
          new RawPropertySlot(
              tag == 5 ? null : readValue(input, tag),
              tag == 5,
              readLong(input, "property owner"),
              readInt(input, "property permissions")));
    }
    return new RawObject(
        expectedId,
        name,
        flags,
        owner,
        location,
        parent,
        contents,
        children,
        verbs,
        propertyNames,
        propertySlots);
  }

  private static void readProgram(
      BufferedReader input,
      Map<Long, RawObject> objects,
      Map<ProgramSlot, String> programs)
      throws IOException {
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
    ProgramSlot slot = new ProgramSlot(objectId, verbIndex);
    if (programs.putIfAbsent(slot, source.toString()) != null) {
      throw malformed("duplicate program #" + objectId + ":" + verbIndex);
    }
  }

  private static List<WorldObject> restoreObjects(
      Map<Long, RawObject> objects, Map<ProgramSlot, String> programs) throws IOException {
    Map<Long, List<WorldProperty>> restoredProperties = new LinkedHashMap<>();
    for (RawObject object : objects.values()) {
      restoreProperties(object, objects, restoredProperties, new ArrayList<>());
    }
    List<WorldObject> restored = new ArrayList<>(objects.size());
    for (RawObject object : objects.values()) {
      List<WorldVerb> verbs = new ArrayList<>(object.verbs().size());
      for (int index = 0; index < object.verbs().size(); index++) {
        RawVerb verb = object.verbs().get(index);
        String source = programs.get(new ProgramSlot(object.id(), index));
        if (source == null) {
          throw malformed("missing program #" + object.id() + ":" + index);
        }
        verbs.add(
            new WorldVerb(
                verb.names(), verb.owner(), verb.permissions(), verb.preposition(), source));
      }
      restored.add(
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
              Objects.requireNonNull(restoredProperties.get(object.id()))));
    }
    return restored;
  }

  private static List<WorldProperty> restoreProperties(
      RawObject object,
      Map<Long, RawObject> objects,
      Map<Long, List<WorldProperty>> restored,
      List<Long> ancestry)
      throws IOException {
    List<WorldProperty> existing = restored.get(object.id());
    if (existing != null) {
      return existing;
    }
    if (ancestry.contains(object.id())) {
      throw malformed("cyclic property ancestry at object #" + object.id());
    }
    ancestry.add(object.id());

    List<WorldProperty> inherited = List.of();
    if (object.parent() != -1) {
      RawObject parent = objects.get(object.parent());
      if (parent == null) {
        throw malformed("object #" + object.id() + " has missing parent #" + object.parent());
      }
      inherited = restoreProperties(parent, objects, restored, ancestry);
    }
    if (object.propertySlots().size() != object.propertyNames().size() + inherited.size()) {
      throw malformed(
          "object #"
              + object.id()
              + " has "
              + object.propertyNames().size()
              + " definitions and "
              + object.propertySlots().size()
              + " value slots for "
              + inherited.size()
              + " inherited properties");
    }

    List<WorldProperty> properties = new ArrayList<>(object.propertySlots().size());
    for (int index = 0; index < object.propertySlots().size(); index++) {
      RawPropertySlot slot = object.propertySlots().get(index);
      boolean defined = index < object.propertyNames().size();
      String name =
          defined
              ? object.propertyNames().get(index)
              : inherited.get(index - object.propertyNames().size()).name();
      if (defined && slot.clear()) {
        throw malformed("object #" + object.id() + " has a clear local property " + name);
      }
      MooValue value = slot.value();
      if (value == null) {
        value = inherited.get(index - object.propertyNames().size()).value();
      }
      properties.add(
          new WorldProperty(
              name, value, slot.owner(), slot.permissions(), slot.clear(), defined));
    }
    ancestry.removeLast();
    List<WorldProperty> result = List.copyOf(properties);
    restored.put(object.id(), result);
    return result;
  }

  private static MooValue readValue(BufferedReader input) throws IOException {
    int tag = readInt(input, "value tag");
    return readValue(input, tag);
  }

  private static MooValue readValue(BufferedReader input, int tag) throws IOException {
    return switch (tag) {
      case 0 -> new IntegerValue(readLong(input, "integer value"));
      case 1 -> new ObjectValue(readLong(input, "object value"));
      case 2 ->
          new StringValue(requiredLine(input, "string value").getBytes(StandardCharsets.ISO_8859_1));
      case 3 ->
          ErrorValue.fromCode(readLong(input, "error value"))
              .orElseThrow(() -> malformed("unsupported error value"));
      case 4 -> {
        int count = readCount(input, "list count");
        List<MooValue> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
          values.add(readValue(input));
        }
        yield new ListValue(values);
      }
      case 9 -> new FloatValue(readDouble(input, "float value"));
      case 10 -> {
        int count = readCount(input, "map count");
        Map<MooValue, MooValue> values = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
          MooValue key = readValue(input);
          MooValue previous = values.put(key, readValue(input));
          if (previous != null) {
            throw malformed("duplicate map key in v17 value");
          }
        }
        yield new MapValue(values);
      }
      default -> throw malformed("unsupported Phase 2 v17 value tag " + tag);
    };
  }

  private static long requireObject(MooValue value, String field) throws IOException {
    if (value instanceof ObjectValue object) {
      return object.value();
    }
    throw malformed(field + " must be an object reference");
  }

  private static long requireSingleParent(MooValue value, String field) throws IOException {
    if (value instanceof ObjectValue object) {
      return object.value();
    }
    List<Long> parents = requireObjectList(value, field);
    if (parents.isEmpty()) {
      return -1;
    }
    if (parents.size() == 1) {
      return parents.getFirst();
    }
    throw malformed(field + " requires unsupported multiple inheritance");
  }

  private static List<Long> requireObjectList(MooValue value, String field) throws IOException {
    if (!(value instanceof ListValue list)) {
      throw malformed(field + " must be a list");
    }
    List<Long> result = new ArrayList<>(list.size());
    for (MooValue element : list.elements()) {
      result.add(requireObject(element, field));
    }
    return List.copyOf(result);
  }

  private static void lineString(BufferedWriter output, String value, String field)
      throws IOException {
    if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
      throw new IOException(field + " cannot contain a line break in a v17 database");
    }
    line(output, value);
  }

  private static void line(BufferedWriter output, Object value) throws IOException {
    output.write(value.toString());
    output.write('\n');
  }

  private static int readCount(BufferedReader input, String field) throws IOException {
    return parseCount(requiredLine(input, field), field);
  }

  private static int parseCount(String text, String field) throws IOException {
    int value = readParsedInt(text, field);
    if (value < 0) {
      throw malformed(field + " must not be negative");
    }
    return value;
  }

  private static int readInt(BufferedReader input, String field) throws IOException {
    return readParsedInt(requiredLine(input, field), field);
  }

  private static int readParsedInt(String text, String field) throws IOException {
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
      long location,
      long parent,
      List<Long> contents,
      List<Long> children,
      List<RawVerb> verbs,
      List<String> propertyNames,
      List<RawPropertySlot> propertySlots) {
    private RawObject {
      contents = List.copyOf(contents);
      children = List.copyOf(children);
      verbs = List.copyOf(verbs);
      propertyNames = List.copyOf(propertyNames);
      propertySlots = List.copyOf(propertySlots);
    }
  }
}
