package world.mongoose.banteng.persistence;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static world.mongoose.banteng.persistence.DbScanner.malformed;
import static world.mongoose.banteng.persistence.DbScanner.parseCount;
import static world.mongoose.banteng.persistence.DbScanner.parseInt;
import static world.mongoose.banteng.persistence.DbScanner.parseLong;
import static world.mongoose.banteng.persistence.DbScanner.readCount;
import static world.mongoose.banteng.persistence.DbScanner.readInt;
import static world.mongoose.banteng.persistence.DbScanner.readLong;
import static world.mongoose.banteng.persistence.DbScanner.requireExact;
import static world.mongoose.banteng.persistence.DbScanner.requiredLine;

import world.mongoose.banteng.world.DurableTask;
import world.mongoose.banteng.world.ActiveConnection;
import world.mongoose.banteng.world.Checkpoint;
import world.mongoose.banteng.world.QueuedEnvironment;
import world.mongoose.banteng.world.QueuedTask;
import world.mongoose.banteng.world.SuspendedStackSlot;
import world.mongoose.banteng.world.SuspendedActivation;
import world.mongoose.banteng.world.SuspendedTask;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import jdk.jfr.FlightRecorder;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.ValueSemantics;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.BooleanValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldSnapshot;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;
import world.mongoose.banteng.world.WorldWaif;
import org.jspecify.annotations.Nullable;

/** Streaming Latin-1 reader and atomic writer for LambdaMOO format version 17. */
public final class LambdaMooV17Codec implements DbCodec {
  private static final String HEADER = "** LambdaMOO Database, Format Version 17 **";
  private final AtomicPromoter promoter;

  /** Creates the production codec with mandatory atomic replacement. */
  public LambdaMooV17Codec() {
    this((source, target) -> Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING));
  }

  LambdaMooV17Codec(AtomicPromoter promoter) {
    this.promoter = Objects.requireNonNull(promoter, "promoter");
  }

  @FunctionalInterface
  interface AtomicPromoter {
    void promote(Path source, Path target) throws IOException;
  }

  /** Writes a byte-stable v17 checkpoint through an atomic same-directory replacement. */
  @Override
  public void writeAtomic(
      Path checkpoint,
      WorldSnapshot world,
      List<? extends DurableTask> tasks,
      List<ActiveConnection> activeConnections)
      throws IOException {
    Objects.requireNonNull(checkpoint, "checkpoint");
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(tasks, "tasks");
    Objects.requireNonNull(activeConnections, "activeConnections");

    Path target = checkpoint.toAbsolutePath().normalize();
    Path directory = Objects.requireNonNull(target.getParent(), "checkpoint parent directory");
    Files.createDirectories(directory);
    Path temporary =
        target.resolveSibling(
            target.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");
    Files.deleteIfExists(temporary);
    boolean supportsPosix = Files.getFileStore(directory).supportsFileAttributeView("posix");
    FileAttribute<?>[] attributes =
        supportsPosix
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
                      Channels.newOutputStream(channel), StringValue.charset()))) {
        write(output, world, tasks, activeConnections);
        output.flush();
        channel.force(true);
      }
      promoter.promote(temporary, target);
      promoted = true;
      if (supportsPosix) {
        try (FileChannel directoryChannel = FileChannel.open(directory, StandardOpenOption.READ)) {
          directoryChannel.force(true);
        }
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

  /** Writes a Toast-style panic database directly, without checkpoint promotion or rename. */
  @Override
  public void writePanic(
      Path target,
      WorldSnapshot world,
      List<? extends DurableTask> tasks,
      List<ActiveConnection> activeConnections)
      throws IOException {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(world, "world");
    Objects.requireNonNull(tasks, "tasks");
    Objects.requireNonNull(activeConnections, "activeConnections");

    Path panicTarget = target.toAbsolutePath().normalize();
    Path directory = Objects.requireNonNull(panicTarget.getParent(), "panic parent directory");
    Files.createDirectories(directory);
    FileAttribute<?>[] attributes =
        Files.getFileStore(directory).supportsFileAttributeView("posix")
            ? new FileAttribute<?>[] {
              PosixFilePermissions.asFileAttribute(
                  PosixFilePermissions.fromString("rw-------"))
            }
            : new FileAttribute<?>[0];
    try (FileChannel channel =
            FileChannel.open(
                panicTarget,
                Set.of(
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE),
                attributes);
        BufferedWriter output =
            new BufferedWriter(
                new OutputStreamWriter(
                    Channels.newOutputStream(channel), StringValue.charset()))) {
      write(output, world, tasks, activeConnections);
      output.flush();
      channel.force(true);
    }
  }

  /** Reads the exact minimum v17 checkpoint emitted by {@link #writeAtomic}. */
  @Override
  public Checkpoint read(Path database) throws IOException {
    Objects.requireNonNull(database, "database");
    try (BufferedReader input =
        Files.newBufferedReader(database, StringValue.charset())) {
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
        pendingFinalization.add(
            readValue(input, context, "pending finalization value " + (index + 1)));
      }
      TaskSectionReader.Tail taskTail = new TaskSectionReader(17,
          (reader, tag, location) -> readValue(reader, tag, context, location),
          value -> encodedObjectNumber(value, context)).read(input);
      List<DurableTask> tasks = taskTail.tasks();
      List<ActiveConnection> activeConnections = taskTail.connections();

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
      Map<Long, RawObject> programObjects = objects;
      Map<ProgramSlot, String> programs = ProgramSectionReader.read(input, programCount,
          objectId -> {
            RawObject object = programObjects.get(objectId);
            return object == null ? -1 : object.verbs().size();
          });
      String trailing;
      while ((trailing = input.readLine()) != null) {
        if (!trailing.isEmpty()) {
          throw malformed("unexpected v17 data after program section");
        }
      }

      objects = validateAndRepairHierarchy(objects, permanentSlotCount);
      Map<Long, List<WorldProperty>> restoredProperties = restoreAllProperties(objects);
      List<WorldObject> restored =
          restoreObjects(objects, programs, restoredProperties, permanentSlotCount);
      Map<AnonymousObjectValue, WorldAnonymousObject> anonymousObjects =
          restoreAnonymousObjects(
              objects, programs, restoredProperties, context, permanentSlotCount);
      Map<WaifValue, WorldWaif> waifs = restoreWaifs(restored, context);
      return new Checkpoint(
          new WorldTxn(
              players,
              restored,
              anonymousObjects,
              waifs,
              pendingFinalization,
              permanentSlotCount - 1L),
          tasks,
          activeConnections);
    }
  }

  @Override
  public void write(
      BufferedWriter output,
      WorldSnapshot world,
      List<? extends DurableTask> tasks,
      List<ActiveConnection> activeConnections)
      throws IOException {
    List<WorldObject> objects =
        world.objects().values().stream()
            .sorted(Comparator.comparingLong(WorldObject::id))
            .toList();
    long maximumObjectId = world.lastUsedObjectId();
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
    List<QueuedTask> queuedTasks = new ArrayList<>();
    List<SuspendedTask> suspendedTasks = new ArrayList<>();
    for (DurableTask task : tasks) {
      switch (task) {
        case QueuedTask queued -> queuedTasks.add(queued);
        case SuspendedTask suspended -> suspendedTasks.add(suspended);
      }
    }
    line(output, queuedTasks.size() + " queued tasks");
    for (QueuedTask task : queuedTasks) {
      writeQueuedTask(output, task, context);
    }
    line(output, suspendedTasks.size() + " suspended tasks");
    for (SuspendedTask task : suspendedTasks) {
      writeSuspendedTask(output, task, context);
    }
    line(output, "0 interrupted tasks");
    line(output, activeConnections.size() + " active connections with listeners");
    for (ActiveConnection connection : activeConnections) {
      line(output, connection.player() + " " + connection.listener());
    }

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
        writeProgramSource(output, source, "verb program");
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
        writeProgramSource(output, source, "verb program");
      }
    }
  }

  private static void writeQueuedTask(
      BufferedWriter output, QueuedTask task, WriteContext context) throws IOException {
    line(
        output,
        "0 "
            + task.firstSourceLine()
            + " "
            + task.scheduledEpochSecond()
            + " "
            + task.taskId());
    MooValue receiver = task.initialLocals().getOrDefault("this", new ObjectValue(-1));
    writeActivationHeader(output, receiver, task.verbLocation(), task.threadMode(),
        task.taskPlayer(), task.programmer(), task.debug(), task.calledVerb(),
        task.fullVerbName(), context);
    line(output, task.environment().slots().size() + " variables");
    for (Map.Entry<String, Optional<MooValue>> local : task.environment().slots().entrySet()) {
      lineString(output, local.getKey(), "queued-task variable name");
      writeRuntimeVariable(output, local.getValue(), context);
    }
    writeProgramSource(output, task.programSource(), "queued-task program");
  }

  private static void writeRuntimeVariable(
      BufferedWriter output, Optional<MooValue> variable, WriteContext context) throws IOException {
    if (variable.isPresent()) {
      writeValue(output, variable.orElseThrow(), context);
    } else {
      line(output, V17Format.ValueTag.NONE.code());
    }
  }

  private static void writeSuspendedTask(
      BufferedWriter output, SuspendedTask task, WriteContext context) throws IOException {
    output.write(task.scheduledEpochSecond() + " " + task.taskId() + " ");
    writeValue(output, task.resumeValue(), context);
    writeValue(output, task.taskLocal(), context);
    line(
        output,
        (task.activations().size() - 1)
            + " "
            + task.rootActivationVector()
            + " "
            + task.functionId()
            + " "
            + task.maxStackDepth());
    for (SuspendedActivation activation : task.activations()) {
      writeSuspendedActivation(output, activation, context);
    }
  }

  private static void writeSuspendedActivation(
      BufferedWriter output, SuspendedActivation activation, WriteContext context)
      throws IOException {
    line(output, "language version " + activation.languageVersion());
    writeProgramSource(output, activation.programSource(), "suspended activation program");
    line(output, activation.locals().size() + " variables");
    for (Map.Entry<String, Optional<MooValue>> local : activation.locals().entrySet()) {
      lineString(output, local.getKey(), "suspended activation variable name");
      writeRuntimeVariable(output, local.getValue(), context);
    }
    line(output, activation.operandStack().size() + " rt_stack slots in use");
    for (int index = activation.operandStack().size() - 1; index >= 0; index--) {
      SuspendedStackSlot slot = activation.operandStack().get(index);
      if (slot.value().isPresent()) {
        writeValue(output, slot.value().orElseThrow(), context);
      } else {
        line(output, V17Format.stackTag(slot.kind()));
        if (V17Format.stackTag(slot.kind()) != V17Format.ValueTag.NONE.code()) {
          line(output, slot.controlValue());
        }
      }
    }
    writeActivationHeader(output, activation.receiver(), activation.verbLocation(),
        activation.threadMode(), activation.taskPlayer(), activation.programmer(),
        activation.debug(), activation.verb(), activation.verbNames(), context);
    if (activation.temporary().isPresent()) {
      writeValue(output, activation.temporary().orElseThrow(), context);
    } else {
      line(output, V17Format.ValueTag.NONE.code());
    }
    line(
        output,
        activation.programCounter()
            + " "
            + activation.builtinFunctionCounter()
            + " "
            + activation.errorCounter());
    if (activation.builtinFunctionCounter() != 0) {
      SuspendedActivation.BuiltinContinuation builtin = activation.builtinContinuation()
          .orElseThrow(() -> new IOException("missing builtin continuation payload"));
      line(output, builtin.name());
      writeBuiltinData(output, builtin);
    }
  }

  private static void writeBuiltinData(BufferedWriter output,
      SuspendedActivation.BuiltinContinuation builtin) throws IOException {
    switch (builtin) {
      case SuspendedActivation.EvalContinuation ignored -> { }
      case SuspendedActivation.CreateContinuation create ->
          line(output, "bf_create data: oid = " + create.object());
      case SuspendedActivation.RecycleContinuation recycle ->
          line(output, "bf_recycle data: oid = " + recycle.object() + ", cont = 0");
      case SuspendedActivation.MoveContinuation move ->
          line(output, "bf_move data: what = " + move.object() + ", where = "
              + move.destination() + ", position = " + move.position());
      case SuspendedActivation.CallFunctionContinuation call -> {
        line(output, "bf_call_function data: fname = " + call.function().name());
        writeBuiltinData(output, call.function());
      }
    }
  }


  /** Writes only Toast's common activation header; task-specific tails stay with their callers. */
  private static void writeActivationHeader(
      BufferedWriter output, MooValue receiver, MooValue verbLocation, boolean threadMode,
      long player, long programmer, boolean debug, String verb, String verbNames,
      WriteContext context) throws IOException {
    writeValue(output, new IntegerValue(V17Format.ACTIVATION_PLACEHOLDER), context);
    writeValue(output, receiver, context);
    writeValue(output, verbLocation, context);
    line(output, threadMode ? 1 : 0);
    long legacyReceiver = receiver instanceof ObjectValue object ? object.value() : -1;
    line(output, legacyReceiver + " " + V17Format.LEGACY_DOBJ + " "
        + V17Format.LEGACY_IOBJ + " " + player + " " + V17Format.LEGACY_PREPOSITION + " "
        + programmer + " " + encodedObjectNumber(verbLocation, context) + " "
        + V17Format.LEGACY_VERB + " " + (debug ? 1 : 0));
    line(output, "No");
    line(output, "More");
    line(output, "Parse");
    line(output, "Infos");
    lineString(output, verb, "activation verb");
    lineString(output, verbNames, "activation verb names");
  }



  private static long encodedObjectNumber(MooValue value, WriteContext context) throws IOException {
    if (value instanceof ObjectValue object) {
      return object.value();
    }
    if (value instanceof AnonymousObjectValue anonymous) {
      return context.anonymousId(anonymous);
    }
    return -1;
  }

  private static long encodedObjectNumber(MooValue value, ReadContext context) throws IOException {
    if (value instanceof ObjectValue object) {
      return object.value();
    }
    if (value instanceof AnonymousObjectValue anonymous) {
      for (Map.Entry<Long, AnonymousObjectValue> entry : context.anonymousById.entrySet()) {
        if (entry.getValue() == anonymous) {
          return entry.getKey();
        }
      }
      throw malformed("anonymous queued-task object has no encoded id");
    }
    return -1;
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

  private static void writeObject(
      BufferedWriter output, WorldObject object, WriteContext context) throws IOException {
    line(output, "#" + object.id());
    lineString(output, object.name(), "object name");
    line(output, object.flags());
    line(output, object.owner());
    writeValue(output, new ObjectValue(object.location()), context);
    writeValue(output, object.lastMove(), context);
    writeObjectList(output, object.contents(), context);
    writeParents(output, object.parents(), context);
    writeObjectList(output, object.children(), context);

    line(output, object.verbs().size());
    for (WorldVerb verb : object.verbs()) {
      lineString(output, verb.names(), "verb names");
      line(output, verb.owner());
      line(output, verb.perms().bits() | (verb.dobj().value() << 4) | (verb.iobj().value() << 6));
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
        line(output, V17Format.ValueTag.CLEAR.code());
      } else {
        writeValue(output, property.value(), context);
      }
      line(output, property.owner());
      line(output, property.perms().bits());
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
    writeParents(output, object.parents(), context);
    writeObjectList(output, List.of(), context);

    line(output, object.verbs().size());
    for (WorldVerb verb : object.verbs()) {
      lineString(output, verb.names(), "anonymous verb names");
      line(output, verb.owner());
      line(output, verb.perms().bits() | (verb.dobj().value() << 4) | (verb.iobj().value() << 6));
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
        line(output, V17Format.ValueTag.CLEAR.code());
      } else {
        writeValue(output, property.value(), context);
      }
      line(output, property.owner());
      line(output, property.perms().bits());
    }
  }

  private static void writeObjectList(
      BufferedWriter output, List<Long> objectIds, WriteContext context)
      throws IOException {
    List<MooValue> values = objectIds.stream().map(ObjectValue::new).map(MooValue.class::cast).toList();
    writeValue(output, new ListValue(values), context);
  }

  private static void writeParents(
      BufferedWriter output, List<Long> parents, WriteContext context) throws IOException {
    if (parents.isEmpty()) {
      writeValue(output, new ObjectValue(-1), context);
    } else if (parents.size() == 1) {
      writeValue(output, new ObjectValue(parents.getFirst()), context);
    } else {
      writeObjectList(output, parents, context);
    }
  }

  private static V17Format.ValueTag valueTag(MooValue value) {
    return switch (value.type()) {
      case INTEGER -> V17Format.ValueTag.INTEGER;
      case BOOLEAN -> V17Format.ValueTag.BOOLEAN;
      case OBJECT -> V17Format.ValueTag.OBJECT;
      case STRING -> V17Format.ValueTag.STRING;
      case ERROR -> V17Format.ValueTag.ERROR;
      case FLOAT -> V17Format.ValueTag.FLOAT;
      case LIST -> V17Format.ValueTag.LIST;
      case MAP -> V17Format.ValueTag.MAP;
      case ANONYMOUS -> V17Format.ValueTag.ANONYMOUS;
      case WAIF -> V17Format.ValueTag.WAIF;
    };
  }

  private static void writeValue(BufferedWriter output, MooValue value, WriteContext context)
      throws IOException {
    line(output, valueTag(value).code());
    switch (value) {
      case IntegerValue integer -> line(output, integer.value());
      case BooleanValue booleanValue -> line(output, booleanValue.value() ? 1 : 0);
      case ObjectValue object -> line(output, object.value());
      case StringValue string ->
          lineString(
              output,
              string.text(),
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
        requireObject(
            readValue(input, context, "object #" + expectedId + " location"),
            "object #" + expectedId + " location");
    MooValue lastMove = readValue(input, context, "object #" + expectedId + " last move");
    List<Long> contents =
        requireObjectList(
            readValue(input, context, "object #" + expectedId + " contents"),
            "object #" + expectedId + " contents");
    List<Long> parents =
        requireParents(
            readValue(input, context, "object #" + expectedId + " parents"),
            "object #" + expectedId + " parents");
    List<Long> children =
        requireObjectList(
            readValue(input, context, "object #" + expectedId + " children"),
            "object #" + expectedId + " children");

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
      try {
        int tag = readInt(input, "property value tag");
        propertySlots.add(
            new RawPropertySlot(
                tag == V17Format.ValueTag.CLEAR.code()
                    ? null
                    : readValue(
                        input,
                        tag,
                        context,
                        "object #" + expectedId + " property slot " + index + " value"),
                tag == V17Format.ValueTag.CLEAR.code(),
                readLong(input, "property owner"),
                readInt(input, "property permissions")));
      } catch (IOException error) {
        throw malformed(
            "object #" + expectedId + " property slot " + index + ": " + error.getMessage());
      }
    }
    return new RawObject(
        expectedId,
        name,
        flags,
        owner,
        location,
        lastMove,
        parents,
        contents,
        children,
        verbs,
        propertyNames,
        propertySlots);
  }

  private static List<WorldObject> restoreObjects(
      Map<Long, RawObject> objects,
      Map<ProgramSlot, String> programs,
      Map<Long, List<WorldProperty>> restoredProperties,
      int permanentSlotCount)
      throws IOException {
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
            verb.compile(source));
      }
      restored.add(
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
              Objects.requireNonNull(restoredProperties.get(object.id()))));
    }
    return restored;
  }

  private static Map<AnonymousObjectValue, WorldAnonymousObject> restoreAnonymousObjects(
      Map<Long, RawObject> objects,
      Map<ProgramSlot, String> programs,
      Map<Long, List<WorldProperty>> restoredProperties,
      ReadContext context,
      int permanentSlotCount)
      throws IOException {
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
            verb.compile(source));
      }
      restored.put(
          entry.getValue(),
          new WorldAnonymousObject(
              object.name(),
              object.flags(),
              object.owner(),
              object.parents(),
              verbs,
              Objects.requireNonNull(restoredProperties.get(object.id()))));
    }
    return restored;
  }

  private static Map<Long, List<WorldProperty>> restoreAllProperties(
      Map<Long, RawObject> objects) throws IOException {
    Map<Long, PropertySlotRestorer.Input> inputs = new LinkedHashMap<>();
    for (RawObject object : objects.values()) {
      inputs.put(object.id(), new PropertySlotRestorer.Input(object.id(), object.parents(),
          object.propertyNames(), object.propertySlots()));
    }
    return PropertySlotRestorer.restore(inputs);
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
                classProperty.perms(),
                override == null,
                false));
      }
      restored.put(waif, new WorldWaif(properties));
    }
    return restored;
  }



  private static Map<Long, RawObject> validateAndRepairHierarchy(
      Map<Long, RawObject> objects, int permanentSlotCount) throws IOException {
    Map<Long, RawObject> repaired = new LinkedHashMap<>(objects);
    for (RawObject object : objects.values()) {
      if (object.id() >= permanentSlotCount) {
        continue;
      }
      repaired.put(
          object.id(),
          new RawObject(
              object.id(),
              object.name(),
              object.flags(),
              object.owner(),
              repairLocation(object.location(), objects, permanentSlotCount),
              object.lastMove(),
              repairReferences(object.parents(), objects, permanentSlotCount),
              repairReferences(object.contents(), objects, permanentSlotCount),
              repairReferences(object.children(), objects, permanentSlotCount),
              object.verbs(),
              object.propertyNames(),
              object.propertySlots()));
    }

    Map<Long, RawVisitState> parentState = new LinkedHashMap<>();
    Map<Long, RawVisitState> locationState = new LinkedHashMap<>();
    for (RawObject object : repaired.values()) {
      if (object.id() >= permanentSlotCount) {
        continue;
      }
      validateParentAcyclic(object.id(), repaired, parentState);
      validateLocationAcyclic(object.id(), repaired, locationState);
    }
    validateHierarchyReciprocity(repaired, permanentSlotCount);
    return repaired;
  }

  private static long repairLocation(
      long location, Map<Long, RawObject> objects, int permanentSlotCount) {
    return location == -1 || isPermanentObject(location, objects, permanentSlotCount)
        ? location
        : -1;
  }

  private static List<Long> repairReferences(
      List<Long> references, Map<Long, RawObject> objects, int permanentSlotCount) {
    List<Long> repaired = new ArrayList<>(references.size());
    for (long reference : references) {
      if (reference == -1 || isPermanentObject(reference, objects, permanentSlotCount)) {
        repaired.add(reference);
      }
    }
    return List.copyOf(repaired);
  }

  private static boolean isPermanentObject(
      long objectId, Map<Long, RawObject> objects, int permanentSlotCount) {
    return objectId >= 0 && objectId < permanentSlotCount && objects.containsKey(objectId);
  }

  private static void validateParentAcyclic(
      long objectId, Map<Long, RawObject> objects, Map<Long, RawVisitState> state)
      throws IOException {
    RawVisitState existing = state.get(objectId);
    if (existing == RawVisitState.COMPLETE) {
      return;
    }
    if (existing == RawVisitState.VISITING) {
      throw malformed("cyclic parent hierarchy at object #" + objectId);
    }
    RawObject object = requireHierarchyObject(objectId, objects);
    state.put(objectId, RawVisitState.VISITING);
    for (long parentId : object.parents()) {
      if (parentId != -1) {
        validateParentAcyclic(parentId, objects, state);
      }
    }
    state.put(objectId, RawVisitState.COMPLETE);
  }

  private static void validateLocationAcyclic(
      long objectId, Map<Long, RawObject> objects, Map<Long, RawVisitState> state)
      throws IOException {
    RawVisitState existing = state.get(objectId);
    if (existing == RawVisitState.COMPLETE) {
      return;
    }
    if (existing == RawVisitState.VISITING) {
      throw malformed("cyclic location hierarchy at object #" + objectId);
    }
    RawObject object = requireHierarchyObject(objectId, objects);
    state.put(objectId, RawVisitState.VISITING);
    if (object.location() != -1) {
      validateLocationAcyclic(object.location(), objects, state);
    }
    state.put(objectId, RawVisitState.COMPLETE);
  }

  private static void validateHierarchyReciprocity(
      Map<Long, RawObject> objects, int permanentSlotCount) throws IOException {
    for (RawObject object : objects.values()) {
      if (object.id() >= permanentSlotCount) {
        continue;
      }
      if (object.location() != -1) {
        RawObject location = requireHierarchyObject(object.location(), objects);
        if (!location.contents().contains(object.id())) {
          throw malformed(
              "object #"
                  + object.id()
                  + " is absent from location #"
                  + object.location()
                  + " contents");
        }
      }
      for (long contentId : object.contents()) {
        if (contentId == -1) {
          continue;
        }
        RawObject content = requireHierarchyObject(contentId, objects);
        if (content.location() != object.id()) {
          throw malformed(
              "object #" + contentId + " has non-reciprocal content location #" + object.id());
        }
      }
      for (long parentId : object.parents()) {
        if (parentId == -1) {
          continue;
        }
        RawObject parent = requireHierarchyObject(parentId, objects);
        if (!parent.children().contains(object.id())) {
          throw malformed(
              "object #"
                  + object.id()
                  + " is absent from parent #"
                  + parentId
                  + " children");
        }
      }
      for (long childId : object.children()) {
        if (childId == -1) {
          continue;
        }
        RawObject child = requireHierarchyObject(childId, objects);
        if (!child.parents().contains(object.id())) {
          throw malformed(
              "object #" + childId + " has non-reciprocal inheritance parent #" + object.id());
        }
      }
    }
  }

  private static RawObject requireHierarchyObject(
      long objectId, Map<Long, RawObject> objects) throws IOException {
    RawObject object = objects.get(objectId);
    if (object == null) {
      throw malformed("missing hierarchy object #" + objectId);
    }
    return object;
  }

  private enum RawVisitState {
    VISITING,
    COMPLETE
  }



  private static MooValue readValue(
      BufferedReader input, ReadContext context, String location) throws IOException {
    int tag = readInt(input, location + " tag");
    return readValue(input, tag, context, location);
  }

  private static MooValue readValue(
      BufferedReader input, int tag, ReadContext context, String location) throws IOException {
    Optional<MooValue> common =
        ValueTagDecoder.readCommon(
            input,
            tag,
            (nestedInput, index) ->
                readValue(
                    nestedInput,
                    context,
                    location + " list element " + (index + 1)));
    if (common.isPresent()) {
      return common.orElseThrow();
    }
    return switch (V17Format.ValueTag.fromCode(tag)) {
      case MAP -> {
        int count = readCount(input, "map count");
        List<MooValue> keys = new ArrayList<>(count);
        MapValue values = new MapValue(Map.of());
        for (int index = 0; index < count; index++) {
          MooValue key =
              readValue(input, context, location + " map entry " + (index + 1) + " key");
          MooValue value =
              readValue(input, context, location + " map entry " + (index + 1) + " value");
          for (MooValue existing : keys) {
            if (ValueSemantics.STANDARD.compare(existing, key, false) == 0) {
              throw malformed("duplicate map key in v17 value");
            }
          }
          keys.add(key);
          values = values.with(key, value);
        }
        yield values;
      }
      case ANONYMOUS -> {
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
      case WAIF -> readWaif(input, context, location);
      case BOOLEAN -> BooleanValue.of(readLong(input, "boolean value") != 0);
      case null, default -> throw malformed("unsupported v17 value tag " + tag + " at " + location);
    };
  }

  private static WaifValue readWaif(
      BufferedReader input, ReadContext context, String location) throws IOException {
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
      MooValue value =
          readValue(input, context, location + " WAIF property " + propertyIndex);
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

  private static List<Long> requireParents(MooValue value, String field) throws IOException {
    if (value instanceof ObjectValue object) {
      return object.value() == -1 ? List.of() : List.of(object.value());
    }
    return requireObjectList(value, field);
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

  private static int readPendingFinalizationCount(BufferedReader input) throws IOException {
    String line = requiredLine(input, "pending-finalization count");
    String suffix = " values pending finalization";
    if (!line.endsWith(suffix)) {
      throw malformed("invalid pending-finalization count: " + line);
    }
    return parseCount(
        line.substring(0, line.length() - suffix.length()), "pending-finalization count");
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
      MooValue lastMove,
      List<Long> parents,
      List<Long> contents,
      List<Long> children,
      List<RawVerb> verbs,
      List<String> propertyNames,
      List<RawPropertySlot> propertySlots) {
    private RawObject {
      parents = List.copyOf(parents);
      contents = List.copyOf(contents);
      children = List.copyOf(children);
      verbs = List.copyOf(verbs);
      propertyNames = List.copyOf(propertyNames);
      propertySlots = List.copyOf(propertySlots);
    }
  }
}
