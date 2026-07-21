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
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jdk.jfr.FlightRecorder;
import moo.value.MooValue;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.BooleanValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;
import moo.world.WorldObject;
import moo.world.WorldAnonymousObject;
import moo.world.WorldProperty;
import moo.world.WorldSnapshot;
import moo.world.WorldTxn;
import moo.world.WorldVerb;
import moo.world.WorldWaif;
import org.jspecify.annotations.Nullable;

/** Streaming Latin-1 reader and atomic writer for the Phase 2 LambdaMOO v17 slice. */
public final class LambdaMooV17Codec {
  private static final String HEADER = "** LambdaMOO Database, Format Version 17 **";

  /** A restored committed world and its durable queued tasks. */
  public record Checkpoint(WorldTxn world, List<QueuedTask> tasks) {
    /** Takes an immutable snapshot of the restored task list. */
    public Checkpoint {
      Objects.requireNonNull(world, "world");
      tasks = List.copyOf(tasks);
    }
  }

  /** The durable state needed to restart one delayed fork. */
  public record QueuedTask(
      long taskId,
      long scheduledEpochSecond,
      String programSource,
      Map<String, MooValue> initialLocals,
      long programmer,
      ObjectValue verbLocation,
      long taskPlayer) {
    /** Takes immutable copies of task-owned state. */
    public QueuedTask {
      Objects.requireNonNull(programSource, "programSource");
      Objects.requireNonNull(initialLocals, "initialLocals");
      Objects.requireNonNull(verbLocation, "verbLocation");
      initialLocals =
          Collections.unmodifiableMap(new LinkedHashMap<>(initialLocals));
    }
  }

  /** Writes a byte-stable v17 checkpoint through an atomic same-directory replacement. */
  public void writeAtomic(Path checkpoint, WorldSnapshot world, List<QueuedTask> tasks)
      throws IOException {
    Objects.requireNonNull(checkpoint, "checkpoint");
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(tasks, "tasks");

    Path target = checkpoint.toAbsolutePath().normalize();
    Path directory = Objects.requireNonNull(target.getParent(), "checkpoint parent directory");
    Files.createDirectories(directory);
    Path temporary =
        target.resolveSibling(
            target.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");
    Files.deleteIfExists(temporary);
    FileAttribute<?>[] attributes =
        Files.getFileStore(directory).supportsFileAttributeView("posix")
            ? new FileAttribute<?>[] {
              PosixFilePermissions.asFileAttribute(
                  PosixFilePermissions.fromString("rw-------"))
            }
            : new FileAttribute<?>[0];
    @Nullable CheckpointEvent event = null;
    if (FlightRecorder.isInitialized()) {
      event = new CheckpointEvent();
      event.revision = world.revision();
      event.objectCount = world.objects().size();
      event.taskCount = tasks.size();
      event.begin();
    }
    boolean promoted = false;
    try {
      try (FileChannel channel =
              FileChannel.open(
                  temporary,
                  Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                  attributes);
          BufferedWriter output =
              new BufferedWriter(
                  new OutputStreamWriter(
                      Channels.newOutputStream(channel), StandardCharsets.ISO_8859_1))) {
        write(output, world, tasks);
        output.flush();
        channel.force(true);
      }
      Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING);
      promoted = true;
      try (FileChannel directoryChannel = FileChannel.open(directory, StandardOpenOption.READ)) {
        directoryChannel.force(true);
      }
      if (event != null) {
        event.bytesWritten = Files.size(target);
        event.success = true;
      }
    } finally {
      if (!promoted) {
        Files.deleteIfExists(temporary);
      }
      if (event != null) {
        event.commit();
      }
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

      ReadContext context = new ReadContext();
      int pendingCount = readPendingFinalizationCount(input);
      List<MooValue> pendingFinalization = new ArrayList<>(pendingCount);
      for (int index = 0; index < pendingCount; index++) {
        pendingFinalization.add(readValue(input, context));
      }
      requireExact(input, "0 clocks", "clocks count");
      int queuedTaskCount = readSectionCount(input, " queued tasks", "queued-task count");
      List<QueuedTask> tasks = new ArrayList<>(queuedTaskCount);
      for (int index = 0; index < queuedTaskCount; index++) {
        tasks.add(readQueuedTask(input, context));
      }
      requireExact(input, "0 suspended tasks", "suspended-task count");
      requireExact(input, "0 interrupted tasks", "interrupted-task count");
      requireExact(input, "0 active connections with listeners", "active-connection count");

      Map<Long, RawObject> objects = new LinkedHashMap<>();
      long expectedObjectId = 0;
      int permanentSlotCount = readCount(input, "permanent object batch count");
      for (int index = 0; index < permanentSlotCount; index++) {
        RawObject object = readObject(input, expectedObjectId++, context);
        if (object != null && objects.putIfAbsent(object.id(), object) != null) {
          throw malformed("duplicate object #" + object.id());
        }
      }
      while (true) {
        int batchCount = readCount(input, "anonymous object batch count");
        if (batchCount == 0) {
          break;
        }
        for (int index = 0; index < batchCount; index++) {
          long objectId = expectedObjectId++;
          if (!context.anonymousById.containsKey(objectId)) {
            throw malformed("anonymous object body has no preceding reference #" + objectId);
          }
          RawObject object = readObject(input, objectId, context);
          if (object == null) {
            throw malformed("anonymous object body is recycled #" + objectId);
          }
          if (objects.putIfAbsent(object.id(), object) != null) {
            throw malformed("duplicate object #" + object.id());
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

      List<WorldObject> restored = restoreObjects(objects, programs, permanentSlotCount);
      Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects =
          restoreAnonymousObjects(objects, programs, context, permanentSlotCount);
      Map<WaifValue, WorldWaif> waifs = restoreWaifs(restored, context);
      return new Checkpoint(
          new WorldTxn(players, restored, anonymousObjects, waifs, pendingFinalization), tasks);
    }
  }

  private static void write(
      BufferedWriter output, WorldSnapshot world, List<QueuedTask> tasks) throws IOException {
    List<WorldObject> objects =
        world.objects().values().stream()
            .sorted(Comparator.comparingLong(WorldObject::id))
            .toList();
    long maximumObjectId = objects.isEmpty() ? -1 : objects.getLast().id();
    if (maximumObjectId > Integer.MAX_VALUE - 1L) {
      throw new IOException("v17 object slot count exceeds supported range");
    }
    WriteContext context = new WriteContext(world, maximumObjectId + 1);

    line(output, HEADER);
    line(output, world.players().size());
    for (long player : world.players()) {
      line(output, player);
    }
    line(output, world.pendingFinalization().size() + " values pending finalization");
    for (MooValue pending : world.pendingFinalization()) {
      writeValue(output, pending, context);
    }
    line(output, "0 clocks");
    line(output, tasks.size() + " queued tasks");
    for (QueuedTask task : tasks) {
      writeQueuedTask(output, task, context);
    }
    line(output, "0 suspended tasks");
    line(output, "0 interrupted tasks");
    line(output, "0 active connections with listeners");

    line(output, maximumObjectId + 1);
    int objectIndex = 0;
    for (long objectId = 0; objectId <= maximumObjectId; objectId++) {
      if (objectIndex >= objects.size() || objects.get(objectIndex).id() != objectId) {
        line(output, "#" + objectId + " recycled");
      } else {
        writeObject(output, objects.get(objectIndex++), context);
      }
    }
    int anonymousIndex = 0;
    while (anonymousIndex < context.anonymousOrder.size()) {
      int batchEnd = context.anonymousOrder.size();
      line(output, batchEnd - anonymousIndex);
      while (anonymousIndex < batchEnd) {
        AnonymousObjectValue identity = context.anonymousOrder.get(anonymousIndex++);
        WorldAnonymousObject object = world.anonymousObjects().get(identity);
        if (object == null) {
          throw new IOException("anonymous value has no world object body");
        }
        writeAnonymousObject(
            output,
            Objects.requireNonNull(context.anonymousIds.get(identity)),
            object,
            context);
      }
    }
    line(output, 0);

    int programCount =
        Math.toIntExact(
            objects.stream()
                .flatMap(object -> object.verbs().stream())
                .filter(verb -> !verb.programSource().isEmpty())
                .count());
    for (AnonymousObjectValue identity : context.anonymousOrder) {
      programCount =
          Math.addExact(
              programCount,
              Math.toIntExact(
                  Objects.requireNonNull(world.anonymousObjects().get(identity)).verbs().stream()
                      .filter(verb -> !verb.programSource().isEmpty())
                      .count()));
    }
    line(output, programCount);
    for (WorldObject object : objects) {
      for (int verbIndex = 0; verbIndex < object.verbs().size(); verbIndex++) {
        String source = object.verbs().get(verbIndex).programSource();
        if (source.isEmpty()) {
          continue;
        }
        line(output, "#" + object.id() + ":" + verbIndex);
        output.write(source);
        if (!source.isEmpty() && source.charAt(source.length() - 1) != '\n') {
          output.write('\n');
        }
        line(output, ".");
      }
    }
    for (AnonymousObjectValue identity : context.anonymousOrder) {
      WorldAnonymousObject object = Objects.requireNonNull(world.anonymousObjects().get(identity));
      long objectId = Objects.requireNonNull(context.anonymousIds.get(identity));
      for (int verbIndex = 0; verbIndex < object.verbs().size(); verbIndex++) {
        String source = object.verbs().get(verbIndex).programSource();
        if (source.isEmpty()) {
          continue;
        }
        line(output, "#" + objectId + ":" + verbIndex);
        output.write(source);
        if (!source.isEmpty() && source.charAt(source.length() - 1) != '\n') {
          output.write('\n');
        }
        line(output, ".");
      }
    }
  }

  private static void writeQueuedTask(
      BufferedWriter output, QueuedTask task, WriteContext context) throws IOException {
    line(output, "0 1 " + task.scheduledEpochSecond() + " " + task.taskId());
    writeValue(output, new IntegerValue(-111), context);
    MooValue receiver =
        task.initialLocals().getOrDefault("this", new ObjectValue(-1));
    writeValue(output, receiver, context);
    writeValue(output, task.verbLocation(), context);
    line(output, 1);
    long receiverObject = receiver instanceof ObjectValue object ? object.value() : -1;
    line(
        output,
        receiverObject
            + " -7 -8 "
            + task.taskPlayer()
            + " -9 "
            + task.programmer()
            + " "
            + task.verbLocation().value()
            + " -10 1");
    line(output, "No");
    line(output, "More");
    line(output, "Parse");
    line(output, "Infos");
    String verb = taskVerb(task.initialLocals());
    lineString(output, verb, "queued-task verb");
    lineString(output, verb, "queued-task verb names");
    line(output, task.initialLocals().size() + " variables");
    for (Map.Entry<String, MooValue> local : task.initialLocals().entrySet()) {
      lineString(output, local.getKey(), "queued-task variable name");
      writeValue(output, local.getValue(), context);
    }
    writeProgramSource(output, task.programSource(), "queued-task program");
  }

  private static QueuedTask readQueuedTask(BufferedReader input, ReadContext context)
      throws IOException {
    String header = requiredLine(input, "queued-task header");
    String[] fields = header.split(" ", -1);
    if (fields.length != 4 || !fields[0].equals("0")) {
      throw malformed("invalid queued-task header: " + header);
    }
    int firstLine = readParsedInt(fields[1], "queued-task first line");
    if (firstLine != 1) {
      throw malformed("unsupported queued-task first line: " + firstLine);
    }
    long scheduledEpochSecond = parseLong(fields[2], "queued-task scheduled epoch second");
    long taskId = parseLong(fields[3], "queued-task id");

    MooValue sentinel = readValue(input, context);
    if (!sentinel.equals(new IntegerValue(-111))) {
      throw malformed("invalid queued-task activation sentinel");
    }
    MooValue receiver = readValue(input, context);
    MooValue typedVerbLocation = readValue(input, context);
    if (!(typedVerbLocation instanceof ObjectValue verbLocation)) {
      throw malformed("queued-task verb location must be an object reference");
    }
    if (readLong(input, "queued-task thread mode") != 1) {
      throw malformed("unsupported queued-task thread mode");
    }

    String compatibility = requiredLine(input, "queued-task compatibility fields");
    String[] compatibilityFields = compatibility.split(" ", -1);
    if (compatibilityFields.length != 9) {
      throw malformed("invalid queued-task compatibility fields: " + compatibility);
    }
    long receiverObject = parseLong(compatibilityFields[0], "queued-task receiver");
    if (!compatibilityFields[1].equals("-7")
        || !compatibilityFields[2].equals("-8")
        || !compatibilityFields[4].equals("-9")
        || !compatibilityFields[7].equals("-10")
        || !compatibilityFields[8].equals("1")) {
      throw malformed("invalid queued-task compatibility sentinels: " + compatibility);
    }
    long taskPlayer = parseLong(compatibilityFields[3], "queued-task player");
    long programmer = parseLong(compatibilityFields[5], "queued-task programmer");
    long oldVerbLocation =
        parseLong(compatibilityFields[6], "queued-task compatibility verb location");
    if (oldVerbLocation != verbLocation.value()) {
      throw malformed("queued-task verb-location encodings disagree");
    }

    requireExact(input, "No", "queued-task obsolete argstr");
    requireExact(input, "More", "queued-task obsolete dobjstr");
    requireExact(input, "Parse", "queued-task obsolete iobjstr");
    requireExact(input, "Infos", "queued-task obsolete prepstr");
    String verb = requiredLine(input, "queued-task verb");
    String verbNames = requiredLine(input, "queued-task verb names");
    if (!verbNames.equals(verb)) {
      throw malformed("unsupported queued-task verb aliases: " + verbNames);
    }

    int variableCount = readSectionCount(input, " variables", "queued-task variable count");
    Map<String, MooValue> locals = new LinkedHashMap<>();
    for (int index = 0; index < variableCount; index++) {
      String name = requiredLine(input, "queued-task variable name");
      if (locals.putIfAbsent(name, readValue(input, context)) != null) {
        throw malformed("duplicate queued-task variable: " + name);
      }
    }
    MooValue localReceiver = locals.get("this");
    if (receiver instanceof ObjectValue object
        && object.value() != receiverObject) {
      throw malformed("queued-task receiver encodings disagree");
    }
    if (localReceiver != null && !localReceiver.equals(receiver)) {
      throw malformed("queued-task activation and runtime receivers disagree");
    }
    if (locals.get("verb") instanceof StringValue localVerb
        && !latin1(localVerb).equals(verb)) {
      throw malformed("queued-task activation and runtime verbs disagree");
    }

    return new QueuedTask(
        taskId,
        scheduledEpochSecond,
        readProgramSource(input, "queued-task program"),
        locals,
        programmer,
        verbLocation,
        taskPlayer);
  }

  private static String taskVerb(Map<String, MooValue> locals) {
    return locals.get("verb") instanceof StringValue verb ? latin1(verb) : "";
  }

  private static String latin1(StringValue value) {
    return new String(value.bytes(), StandardCharsets.ISO_8859_1);
  }

  private static void writeProgramSource(
      BufferedWriter output, String source, String field) throws IOException {
    if (source.indexOf('\r') >= 0) {
      throw new IOException(field + " cannot contain carriage returns");
    }
    for (String sourceLine : source.split("\\n", -1)) {
      if (sourceLine.equals(".")) {
        throw new IOException(field + " cannot contain a standalone dot line");
      }
    }
    output.write(source);
    if (!source.isEmpty() && source.charAt(source.length() - 1) != '\n') {
      output.write('\n');
    }
    line(output, ".");
  }

  private static String readProgramSource(BufferedReader input, String field) throws IOException {
    StringBuilder source = new StringBuilder();
    while (true) {
      String sourceLine = requiredLine(input, field);
      if (sourceLine.equals(".")) {
        return source.toString();
      }
      source.append(sourceLine).append('\n');
    }
  }

  private static void writeObject(
      BufferedWriter output, WorldObject object, WriteContext context) throws IOException {
    line(output, "#" + object.id());
    lineString(output, object.name(), "object name");
    line(output, object.flags());
    line(output, object.owner());
    writeValue(output, new ObjectValue(object.location()), context);
    writeValue(output, new IntegerValue(0), context);
    writeObjectList(output, object.contents(), context);
    writeValue(output, new ObjectValue(object.parent()), context);
    writeObjectList(output, object.children(), context);

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
        writeValue(output, property.value(), context);
      }
      line(output, property.owner());
      line(output, property.permissions());
    }
  }

  private static void writeAnonymousObject(
      BufferedWriter output,
      long objectId,
      WorldAnonymousObject object,
      WriteContext context)
      throws IOException {
    line(output, "#" + objectId);
    lineString(output, object.name(), "anonymous object name");
    line(output, object.flags());
    line(output, object.owner());
    writeValue(output, new ObjectValue(-1), context);
    writeValue(output, new IntegerValue(0), context);
    writeObjectList(output, List.of(), context);
    writeValue(output, new ObjectValue(object.parent()), context);
    writeObjectList(output, List.of(), context);

    line(output, object.verbs().size());
    for (WorldVerb verb : object.verbs()) {
      lineString(output, verb.names(), "anonymous verb names");
      line(output, verb.owner());
      line(output, verb.permissions());
      line(output, verb.preposition());
    }

    long definitionCount = object.properties().stream().filter(WorldProperty::defined).count();
    line(output, definitionCount);
    for (WorldProperty property : object.properties()) {
      if (property.defined()) {
        lineString(output, property.name(), "anonymous property name");
      }
    }
    line(output, object.properties().size());
    for (WorldProperty property : object.properties()) {
      if (property.clear()) {
        line(output, 5);
      } else {
        writeValue(output, property.value(), context);
      }
      line(output, property.owner());
      line(output, property.permissions());
    }
  }

  private static void writeObjectList(
      BufferedWriter output, List<Long> objectIds, WriteContext context)
      throws IOException {
    List<MooValue> values = objectIds.stream().map(ObjectValue::new).map(MooValue.class::cast).toList();
    writeValue(output, new ListValue(values), context);
  }

  private static void writeValue(BufferedWriter output, MooValue value, WriteContext context)
      throws IOException {
    line(output, value.type().code());
    switch (value) {
      case IntegerValue integer -> line(output, integer.value());
      case BooleanValue booleanValue -> line(output, booleanValue.value() ? 1 : 0);
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
          writeValue(output, element, context);
        }
      }
      case MapValue map -> {
        line(output, map.size());
        for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
          writeValue(output, entry.getKey(), context);
          writeValue(output, entry.getValue(), context);
        }
      }
      case AnonymousObjectValue anonymous -> line(output, context.anonymousId(anonymous));
      case WaifValue waif -> {
        Integer existing = context.waifIds.get(waif);
        if (existing != null) {
          line(output, "r " + existing);
          line(output, ".");
        } else {
          int index = context.waifIds.size();
          context.waifIds.put(waif, index);
          line(output, "c " + index);
          line(output, waif.classObject().value());
          line(output, waif.owner().value());
          WorldWaif body = context.world.waifs().get(waif);
          line(output, body == null ? 0 : body.properties().size());
          if (body != null) {
            for (int propertyIndex = 0;
                propertyIndex < body.properties().size();
                propertyIndex++) {
              WorldProperty property = body.properties().get(propertyIndex);
              if (!property.clear()) {
                line(output, propertyIndex);
                writeValue(output, property.value(), context);
              }
            }
          }
          line(output, -1);
          line(output, ".");
        }
      }
      default -> throw new IOException("unsupported Phase 2 v17 value: " + value.type());
    }
  }

  private static @Nullable RawObject readObject(
      BufferedReader input, long expectedId, ReadContext context)
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
    long location =
        requireObject(readValue(input, context), "object #" + expectedId + " location");
    readValue(input, context); // last_move is not represented in the Phase 2 world record.
    List<Long> contents =
        requireObjectList(
            readValue(input, context), "object #" + expectedId + " contents");
    long parent =
        requireSingleParent(
            readValue(input, context), "object #" + expectedId + " parents");
    List<Long> children =
        requireObjectList(
            readValue(input, context), "object #" + expectedId + " children");

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
              tag == 5 ? null : readValue(input, tag, context),
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
      Map<Long, RawObject> objects,
      Map<ProgramSlot, String> programs,
      int permanentSlotCount)
      throws IOException {
    Map<Long, List<WorldProperty>> restoredProperties = new LinkedHashMap<>();
    for (RawObject object : objects.values()) {
      restoreProperties(object, objects, restoredProperties, new ArrayList<>());
    }
    List<WorldObject> restored = new ArrayList<>(objects.size());
    for (RawObject object : objects.values()) {
      if (object.id() >= permanentSlotCount) {
        continue;
      }
      List<WorldVerb> verbs = new ArrayList<>(object.verbs().size());
      for (int index = 0; index < object.verbs().size(); index++) {
        RawVerb verb = object.verbs().get(index);
        String source = programs.getOrDefault(new ProgramSlot(object.id(), index), "");
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

  private static Map<AnonymousObjectValue, WorldAnonymousObject> restoreAnonymousObjects(
      Map<Long, RawObject> objects,
      Map<ProgramSlot, String> programs,
      ReadContext context,
      int permanentSlotCount)
      throws IOException {
    Map<Long, List<WorldProperty>> restoredProperties = new LinkedHashMap<>();
    for (RawObject object : objects.values()) {
      restoreProperties(object, objects, restoredProperties, new ArrayList<>());
    }
    Map<AnonymousObjectValue, WorldAnonymousObject> restored = new LinkedHashMap<>();
    for (Map.Entry<Long, AnonymousObjectValue> entry : context.anonymousById.entrySet()) {
      if (entry.getKey() < permanentSlotCount) {
        throw malformed("anonymous reference reuses permanent object #" + entry.getKey());
      }
      RawObject object = objects.get(entry.getKey());
      if (object == null) {
        throw malformed("anonymous reference has no object body #" + entry.getKey());
      }
      List<WorldVerb> verbs = new ArrayList<>(object.verbs().size());
      for (int index = 0; index < object.verbs().size(); index++) {
        RawVerb verb = object.verbs().get(index);
        String source = programs.getOrDefault(new ProgramSlot(object.id(), index), "");
        verbs.add(
            new WorldVerb(
                verb.names(), verb.owner(), verb.permissions(), verb.preposition(), source));
      }
      restored.put(
          entry.getValue(),
          new WorldAnonymousObject(
              object.name(),
              object.flags(),
              object.owner(),
              object.parent(),
              verbs,
              Objects.requireNonNull(restoredProperties.get(object.id()))));
    }
    return restored;
  }

  private static Map<WaifValue, WorldWaif> restoreWaifs(
      List<WorldObject> objects, ReadContext context) throws IOException {
    Map<Long, WorldObject> objectsById = new LinkedHashMap<>();
    for (WorldObject object : objects) {
      objectsById.put(object.id(), object);
    }
    Map<WaifValue, WorldWaif> restored = new LinkedHashMap<>();
    for (WaifValue waif : context.waifs.values()) {
      RawWaif raw = context.waifBodies.get(waif);
      if (raw == null) {
        throw malformed("WAIF creation has no property body");
      }
      WorldObject waifClass = objectsById.get(waif.classObject().value());
      if (waifClass == null) {
        throw malformed("WAIF names missing class #" + waif.classObject().value());
      }
      List<WorldProperty> classProperties =
          waifClass.properties().stream().filter(property -> property.name().startsWith(":"))
              .toList();
      if (raw.propertyCount() != classProperties.size()) {
        throw malformed(
            "WAIF property count "
                + raw.propertyCount()
                + " does not match class #"
                + waif.classObject().value()
                + " layout size "
                + classProperties.size());
      }
      List<WorldProperty> properties = new ArrayList<>(classProperties.size());
      for (int index = 0; index < classProperties.size(); index++) {
        WorldProperty classProperty = classProperties.get(index);
        MooValue override = raw.overrides().get(index);
        properties.add(
            new WorldProperty(
                classProperty.name(),
                override == null ? classProperty.value() : override,
                classProperty.owner(),
                classProperty.permissions(),
                override == null,
                false));
      }
      restored.put(waif, new WorldWaif(properties));
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

  private static MooValue readValue(BufferedReader input, ReadContext context) throws IOException {
    int tag = readInt(input, "value tag");
    return readValue(input, tag, context);
  }

  private static MooValue readValue(BufferedReader input, int tag, ReadContext context)
      throws IOException {
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
          values.add(readValue(input, context));
        }
        yield new ListValue(values);
      }
      case 9 -> new FloatValue(readDouble(input, "float value"));
      case 10 -> {
        int count = readCount(input, "map count");
        MapValue values = new MapValue(Map.of());
        for (int index = 0; index < count; index++) {
          MooValue key = readValue(input, context);
          MooValue value = readValue(input, context);
          if (values.get(key).isPresent()) {
            throw malformed("duplicate map key in v17 value");
          }
          values = values.with(key, value);
        }
        yield values;
      }
      case 12 -> {
        long objectId = readLong(input, "anonymous object reference");
        if (objectId == -1) {
          yield context.invalidAnonymous;
        }
        if (objectId < 0) {
          throw malformed("invalid anonymous object reference #" + objectId);
        }
        yield context.anonymousById.computeIfAbsent(
            objectId, ignored -> new AnonymousObjectValue());
      }
      case 13 -> readWaif(input, context);
      case 14 -> BooleanValue.of(readLong(input, "boolean value") != 0);
      default -> throw malformed("unsupported Phase 2 v17 value tag " + tag);
    };
  }

  private static WaifValue readWaif(BufferedReader input, ReadContext context)
      throws IOException {
    String header = requiredLine(input, "WAIF identity header");
    if (header.length() < 3 || header.charAt(1) != ' ') {
      throw malformed("invalid WAIF identity header: " + header);
    }
    char kind = header.charAt(0);
    int index = parseCount(header.substring(2), "WAIF identity index");
    if (kind == 'r') {
      requireExact(input, ".", "WAIF reference terminator");
      WaifValue existing = context.waifs.get(index);
      if (existing == null) {
        throw malformed("WAIF reference precedes creation " + index);
      }
      return existing;
    }
    if (kind != 'c' || index != context.waifs.size()) {
      throw malformed("invalid WAIF creation index " + index);
    }
    WaifValue waif =
        new WaifValue(
            new ObjectValue(readLong(input, "WAIF class")),
            new ObjectValue(readLong(input, "WAIF owner")));
    context.waifs.put(index, waif);
    int propertyCount = readCount(input, "WAIF property count");
    Map<Integer, MooValue> overrides = new LinkedHashMap<>();
    while (true) {
      int propertyIndex = readInt(input, "WAIF property index");
      if (propertyIndex == -1) {
        break;
      }
      if (propertyIndex < 0 || propertyIndex >= propertyCount) {
        throw malformed("WAIF property index is out of range: " + propertyIndex);
      }
      MooValue value = readValue(input, context);
      if (overrides.putIfAbsent(propertyIndex, value) != null) {
        throw malformed("duplicate WAIF property index " + propertyIndex);
      }
    }
    context.waifBodies.put(waif, new RawWaif(propertyCount, overrides));
    requireExact(input, ".", "WAIF creation terminator");
    return waif;
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

  private static int readPendingFinalizationCount(BufferedReader input) throws IOException {
    String line = requiredLine(input, "pending-finalization count");
    String suffix = " values pending finalization";
    if (!line.endsWith(suffix)) {
      throw malformed("invalid pending-finalization count: " + line);
    }
    return parseCount(
        line.substring(0, line.length() - suffix.length()), "pending-finalization count");
  }

  private static int readSectionCount(BufferedReader input, String suffix, String field)
      throws IOException {
    String line = requiredLine(input, field);
    if (!line.endsWith(suffix)) {
      throw malformed("invalid " + field + ": " + line);
    }
    return parseCount(line.substring(0, line.length() - suffix.length()), field);
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

  private static final class WriteContext {
    private final List<AnonymousObjectValue> anonymousOrder = new ArrayList<>();
    private final IdentityHashMap<AnonymousObjectValue, Long> anonymousIds =
        new IdentityHashMap<>();
    private final IdentityHashMap<WaifValue, Integer> waifIds = new IdentityHashMap<>();
    private final WorldSnapshot world;
    private long nextObjectId;

    private WriteContext(WorldSnapshot world, long nextObjectId) {
      this.world = world;
      this.nextObjectId = nextObjectId;
    }

    private long anonymousId(AnonymousObjectValue identity) {
      if (!world.anonymousObjects().containsKey(identity)) {
        return -1;
      }
      Long existing = anonymousIds.get(identity);
      if (existing != null) {
        return existing;
      }
      long assigned = nextObjectId++;
      anonymousIds.put(identity, assigned);
      anonymousOrder.add(identity);
      return assigned;
    }
  }

  private static final class ReadContext {
    private final Map<Long, AnonymousObjectValue> anonymousById = new LinkedHashMap<>();
    private final AnonymousObjectValue invalidAnonymous = new AnonymousObjectValue();
    private final Map<Integer, WaifValue> waifs = new LinkedHashMap<>();
    private final IdentityHashMap<WaifValue, RawWaif> waifBodies = new IdentityHashMap<>();
  }

  private record ProgramSlot(long objectId, int verbIndex) {}

  private record RawVerb(String names, long owner, int permissions, int preposition) {}

  private record RawPropertySlot(
      @Nullable MooValue value, boolean clear, long owner, int permissions) {}

  private record RawWaif(int propertyCount, Map<Integer, MooValue> overrides) {
    private RawWaif {
      overrides = Map.copyOf(overrides);
    }
  }

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
