package moo.persistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import moo.value.MooValue.ObjectValue;
import moo.world.WorldObject;
import moo.world.WorldProperty;
import moo.world.WorldTxn;
import moo.world.WorldVerb;

/** A streaming ISO-8859-1 reader for the authoritative LambdaMOO v4 slice. */
public final class LambdaMooV4Reader {
  private static final String HEADER = "** LambdaMOO Database, Format Version 4 **";

  /** Reads and validates the exact supported v4 database contract. */
  public WorldTxn read(Path database) throws IOException {
    Objects.requireNonNull(database, "database");
    try (BufferedReader input = Files.newBufferedReader(database, StandardCharsets.ISO_8859_1)) {
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
        rawObjects.put(object.id(), object);
      }

      Map<ProgramSlot, String> programs = readPrograms(input, programCount, rawObjects);
      requireExact(input, "0 clocks", "clocks tail");
      requireExact(input, "0 queued tasks", "queued-tasks tail");
      requireExact(input, "0 suspended tasks", "suspended-tasks tail");
      if (input.readLine() != null) {
        throw malformed("unsupported v4 tail after suspended tasks");
      }

      validatePlayers(players, rawObjects);
      Map<Long, List<Long>> children = deriveChildren(rawObjects);
      Map<Long, List<Long>> contents = deriveContents(rawObjects);
      validateRelations(rawObjects, children, contents);
      return buildWorld(players, rawObjects, programs, children, contents);
    }
  }

  private static RawObject readObject(BufferedReader input, int slot) throws IOException {
    requireExact(input, "#" + slot, "object slot " + slot);
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
    if (propertySlotCount != propertyNameCount) {
      throw malformed(
          "object #"
              + slot
              + " has "
              + propertyNameCount
              + " property names but "
              + propertySlotCount
              + " slots");
    }
    List<WorldProperty> properties = new ArrayList<>(propertySlotCount);
    for (int index = 0; index < propertySlotCount; index++) {
      int tag = readInt(input, "object #" + slot + " property tag");
      if (tag != 1) {
        throw malformed("unsupported persisted value tag " + tag);
      }
      properties.add(
          new WorldProperty(
              propertyNames.get(index),
              new ObjectValue(readLong(input, "object-reference value")),
              readLong(input, "property owner"),
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
        properties);
  }

  private static Map<ProgramSlot, String> readPrograms(
      BufferedReader input, int programCount, Map<Long, RawObject> rawObjects) throws IOException {
    Map<ProgramSlot, String> programs = new HashMap<>();
    for (int count = 0; count < programCount; count++) {
      String header = requiredLine(input, "program header");
      int colon = header.indexOf(':');
      if (!header.startsWith("#") || colon <= 1 || colon != header.lastIndexOf(':')) {
        throw malformed("invalid program header " + header);
      }
      long objectId = parseLong(header.substring(1, colon), "program object");
      int verbIndex = parseCount(header.substring(colon + 1), "program verb index");
      RawObject object = rawObjects.get(objectId);
      if (object == null) {
        throw malformed("program references missing object #" + objectId);
      }
      if (verbIndex >= object.verbs().size()) {
        throw malformed("program references missing verb #" + objectId + ":" + verbIndex);
      }

      StringBuilder source = new StringBuilder();
      while (true) {
        String line = requiredLine(input, "program #" + objectId + ":" + verbIndex);
        if (line.equals(".")) {
          break;
        }
        source.append(line).append('\n');
      }
      ProgramSlot slot = new ProgramSlot(objectId, verbIndex);
      if (programs.putIfAbsent(slot, source.toString()) != null) {
        throw malformed("duplicate program slot #" + objectId + ":" + verbIndex);
      }
    }
    return programs;
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
    Set<Long> linkedChildren = flatten(children);
    Set<Long> linkedContents = flatten(contents);
    for (RawObject object : objects.values()) {
      if (!objects.containsKey(object.owner())) {
        throw malformed("object #" + object.id() + " has missing owner #" + object.owner());
      }
      if (object.parent() == -1) {
        if (object.sibling() != -1 || linkedChildren.contains(object.id())) {
          throw malformed("parentless object #" + object.id() + " has a child-chain link");
        }
      } else if (!objects.containsKey(object.parent()) || !linkedChildren.contains(object.id())) {
        throw malformed("object #" + object.id() + " has an invalid parent relation");
      }
      if (object.location() == -1) {
        if (object.nextContent() != -1 || linkedContents.contains(object.id())) {
          throw malformed("locationless object #" + object.id() + " has a contents-chain link");
        }
      } else if (!objects.containsKey(object.location()) || !linkedContents.contains(object.id())) {
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
      Map<Long, List<Long>> contents)
      throws IOException {
    List<WorldObject> objects = new ArrayList<>(rawObjects.size());
    for (RawObject raw : rawObjects.values()) {
      List<WorldVerb> verbs = new ArrayList<>(raw.verbs().size());
      for (int index = 0; index < raw.verbs().size(); index++) {
        RawVerb rawVerb = raw.verbs().get(index);
        String source = programs.get(new ProgramSlot(raw.id(), index));
        if (source == null) {
          throw malformed("missing program for verb #" + raw.id() + ":" + index);
        }
        verbs.add(
            new WorldVerb(
                rawVerb.names(),
                rawVerb.owner(),
                rawVerb.permissions(),
                rawVerb.preposition(),
                source));
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
              raw.properties()));
    }
    return new WorldTxn(players, objects);
  }

  private static int readCount(BufferedReader input, String field) throws IOException {
    return parseCount(requiredLine(input, field), field);
  }

  private static int parseCount(String text, String field) throws IOException {
    int value = parseInt(text, field);
    if (value < 0) {
      throw malformed(field + " must not be negative");
    }
    return value;
  }

  private static int readInt(BufferedReader input, String field) throws IOException {
    return parseInt(requiredLine(input, field), field);
  }

  private static int parseInt(String text, String field) throws IOException {
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
      List<WorldProperty> properties) {
    private RawObject {
      verbs = List.copyOf(verbs);
      properties = List.copyOf(properties);
    }
  }
}
