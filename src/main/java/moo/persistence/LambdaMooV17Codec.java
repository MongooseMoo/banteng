package moo.persistence;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static moo.persistence.DbScanner.malformed;
import static moo.persistence.DbScanner.parseCount;
import static moo.persistence.DbScanner.parseInt;
import static moo.persistence.DbScanner.parseLong;
import static moo.persistence.DbScanner.readCount;
import static moo.persistence.DbScanner.readInt;
import static moo.persistence.DbScanner.readLong;
import static moo.persistence.DbScanner.requireExact;
import static moo.persistence.DbScanner.requiredLine;

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

/** Streaming Latin-1 reader and atomic writer for LambdaMOO format version 17. */
public final class LambdaMooV17Codec {
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

  /** A durable v17 task record. */
  public sealed interface DurableTask permits QueuedTask, SuspendedTask {}

  /** One connection that was active when a v17 checkpoint was written. */
  public record ActiveConnection(long player, long listener) {}

  /** A restored committed world, durable tasks, and checkpointed connections. */
  public record Checkpoint(
      WorldTxn world, List<DurableTask> tasks, List<ActiveConnection> activeConnections) {
    /** Takes an immutable snapshot of the restored task list. */
    public Checkpoint {
      Objects.requireNonNull(world, "world");
      tasks = List.copyOf(tasks);
      activeConnections = List.copyOf(activeConnections);
    }
  }

  /** The durable state needed to restart one delayed fork. */
  public record QueuedTask(
      long taskId,
      long scheduledEpochSecond,
      int firstSourceLine,
      String calledVerb,
      String fullVerbName,
      String programSource,
      Map<String, MooValue> initialLocals,
      long programmer,
      MooValue verbLocation,
      long taskPlayer,
      boolean debug,
      boolean threadMode) implements DurableTask {
    public QueuedTask(
        long taskId,
        long scheduledEpochSecond,
        int firstSourceLine,
        String calledVerb,
        String fullVerbName,
        String programSource,
        Map<String, MooValue> initialLocals,
        long programmer,
        MooValue verbLocation,
        long taskPlayer,
        boolean threadMode) {
      this(
          taskId,
          scheduledEpochSecond,
          firstSourceLine,
          calledVerb,
          fullVerbName,
          programSource,
          initialLocals,
          programmer,
          verbLocation,
          taskPlayer,
          true,
          threadMode);
    }

    /** Takes immutable copies of task-owned state. */
    public QueuedTask {
      if (firstSourceLine < 1) {
        throw new IllegalArgumentException("queued-task first source line must be positive");
      }
      Objects.requireNonNull(calledVerb, "calledVerb");
      Objects.requireNonNull(fullVerbName, "fullVerbName");
      Objects.requireNonNull(programSource, "programSource");
      Objects.requireNonNull(initialLocals, "initialLocals");
      Objects.requireNonNull(verbLocation, "verbLocation");
      initialLocals =
          Collections.unmodifiableMap(new LinkedHashMap<>(initialLocals));
    }
  }

  /** One ordinary MOO value or one Toast control marker on a suspended runtime stack. */
  public record SuspendedStackSlot(
      Optional<MooValue> value, int controlTag, long controlValue) {
    /** Requires exactly one supported slot representation. */
    public SuspendedStackSlot {
      Objects.requireNonNull(value, "value");
      if (value.isPresent() == (controlTag >= 0)
          || (controlTag >= 0 && controlTag != 6 && controlTag != 7 && controlTag != 8)
          || (controlTag == 6 && controlValue != 0)) {
        throw new IllegalArgumentException("invalid suspended stack slot");
      }
    }
  }

  /** One complete v17 activation retained for a suspended VM. */
  public record SuspendedActivation(
      int languageVersion,
      String programSource,
      Map<String, Optional<MooValue>> locals,
      List<SuspendedStackSlot> operandStack,
      MooValue receiver,
      MooValue verbLocation,
      boolean threadMode,
      long taskPlayer,
      long programmer,
      boolean debug,
      String verb,
      String verbNames,
      Optional<MooValue> temporary,
      long programCounter,
      long builtinFunctionCounter,
      long errorCounter) {
    /** Takes immutable copies of activation-owned state. */
    public SuspendedActivation {
      Objects.requireNonNull(programSource, "programSource");
      Objects.requireNonNull(locals, "locals");
      Objects.requireNonNull(operandStack, "operandStack");
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(verbLocation, "verbLocation");
      Objects.requireNonNull(verb, "verb");
      Objects.requireNonNull(verbNames, "verbNames");
      Objects.requireNonNull(temporary, "temporary");
      locals = Collections.unmodifiableMap(new LinkedHashMap<>(locals));
      operandStack = List.copyOf(operandStack);
    }
  }

  /** The durable state needed to resume one v17 VM. */
  public record SuspendedTask(
      long taskId,
      long scheduledEpochSecond,
      MooValue resumeValue,
      MooValue taskLocal,
      int rootActivationVector,
      long functionId,
      long maxStackDepth,
      Optional<String> interruptionStatus,
      List<SuspendedActivation> activations) implements DurableTask {
    /** Takes immutable copies of task-owned state. */
    public SuspendedTask {
      Objects.requireNonNull(resumeValue, "resumeValue");
      Objects.requireNonNull(taskLocal, "taskLocal");
      Objects.requireNonNull(interruptionStatus, "interruptionStatus");
      activations = List.copyOf(activations);
      if (activations.isEmpty()) {
        throw new IllegalArgumentException("suspended task requires an activation");
      }
    }
  }

  /** Writes a byte-stable v17 checkpoint through an atomic same-directory replacement. */
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
      requireExact(input, "0 clocks", "clocks count");
      int queuedTaskCount = readSectionCount(input, " queued tasks", "queued-task count");
      List<DurableTask> tasks = new ArrayList<>(queuedTaskCount);
      for (int index = 0; index < queuedTaskCount; index++) {
        tasks.add(readQueuedTask(input, context));
      }
      int suspendedTaskCount =
          readSectionCount(input, " suspended tasks", "suspended-task count");
      for (int index = 0; index < suspendedTaskCount; index++) {
        tasks.add(readSuspendedTask(input, context));
      }
      int interruptedTaskCount =
          readSectionCount(input, " interrupted tasks", "interrupted-task count");
      for (int index = 0; index < interruptedTaskCount; index++) {
        tasks.add(readInterruptedTask(input, context));
      }
      int activeConnectionCount =
          readSectionCount(
              input,
              " active connections with listeners",
              "active-connection count");
      List<ActiveConnection> activeConnections = new ArrayList<>(activeConnectionCount);
      for (int index = 0; index < activeConnectionCount; index++) {
        String connectionLine = requiredLine(input, "active connection");
        String[] connectionFields = connectionLine.split(" ", -1);
        if (connectionFields.length != 2) {
          throw malformed("invalid active connection: " + connectionLine);
        }
        activeConnections.add(
            new ActiveConnection(
                parseLong(connectionFields[0], "active connection player"),
                parseLong(connectionFields[1], "active connection listener")));
      }

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

  private static void write(
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
    line(
        output,
        "0 "
            + task.firstSourceLine()
            + " "
            + task.scheduledEpochSecond()
            + " "
            + task.taskId());
    writeValue(output, new IntegerValue(-111), context);
    MooValue receiver =
        task.initialLocals().getOrDefault("this", new ObjectValue(-1));
    writeValue(output, receiver, context);
    writeValue(output, task.verbLocation(), context);
    line(output, task.threadMode() ? 1 : 0);
    long receiverObject = encodedObjectNumber(receiver, context);
    line(
        output,
        receiverObject
            + " -7 -8 "
            + task.taskPlayer()
            + " -9 "
            + task.programmer()
            + " "
            + encodedObjectNumber(task.verbLocation(), context)
            + " -10 "
            + (task.debug() ? 1 : 0));
    line(output, "No");
    line(output, "More");
    line(output, "Parse");
    line(output, "Infos");
    lineString(output, task.calledVerb(), "queued-task verb");
    lineString(output, task.fullVerbName(), "queued-task verb names");
    line(output, task.initialLocals().size() + " variables");
    for (Map.Entry<String, MooValue> local : task.initialLocals().entrySet()) {
      lineString(output, local.getKey(), "queued-task variable name");
      writeValue(output, local.getValue(), context);
    }
    writeProgramSource(output, task.programSource(), "queued-task program");
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
    if (activation.builtinFunctionCounter() != 0) {
      throw new IOException("unsupported suspended builtin continuation");
    }
    line(output, "language version " + activation.languageVersion());
    writeProgramSource(output, activation.programSource(), "suspended activation program");
    line(output, activation.locals().size() + " variables");
    for (Map.Entry<String, Optional<MooValue>> local : activation.locals().entrySet()) {
      lineString(output, local.getKey(), "suspended activation variable name");
      if (local.getValue().isPresent()) {
        writeValue(output, local.getValue().orElseThrow(), context);
      } else {
        line(output, 6);
      }
    }
    line(output, activation.operandStack().size() + " rt_stack slots in use");
    for (int index = activation.operandStack().size() - 1; index >= 0; index--) {
      SuspendedStackSlot slot = activation.operandStack().get(index);
      if (slot.value().isPresent()) {
        writeValue(output, slot.value().orElseThrow(), context);
      } else {
        line(output, slot.controlTag());
        if (slot.controlTag() != 6) {
          line(output, slot.controlValue());
        }
      }
    }
    writeValue(output, new IntegerValue(-111), context);
    writeValue(output, activation.receiver(), context);
    writeValue(output, activation.verbLocation(), context);
    line(output, activation.threadMode() ? 1 : 0);
    long receiverObject =
        activation.receiver() instanceof ObjectValue object ? object.value() : -1;
    long verbLocationObject =
        activation.verbLocation() instanceof ObjectValue object ? object.value() : -1;
    line(
        output,
        receiverObject
            + " -7 -8 "
            + activation.taskPlayer()
            + " -9 "
            + activation.programmer()
            + " "
            + verbLocationObject
            + " -10 "
            + (activation.debug() ? 1 : 0));
    line(output, "No");
    line(output, "More");
    line(output, "Parse");
    line(output, "Infos");
    lineString(output, activation.verb(), "suspended activation verb");
    lineString(output, activation.verbNames(), "suspended activation verb names");
    if (activation.temporary().isPresent()) {
      writeValue(output, activation.temporary().orElseThrow(), context);
    } else {
      line(output, 6);
    }
    line(
        output,
        activation.programCounter()
            + " "
            + activation.builtinFunctionCounter()
            + " "
            + activation.errorCounter());
  }

  private static QueuedTask readQueuedTask(BufferedReader input, ReadContext context)
      throws IOException {
    String header = requiredLine(input, "queued-task header");
    String[] fields = header.split(" ", -1);
    if (fields.length != 4 || !fields[0].equals("0")) {
      throw malformed("invalid queued-task header: " + header);
    }
    int firstLine = parseInt(fields[1], "queued-task first line");
    if (firstLine < 1) {
      throw malformed("invalid queued-task first line: " + firstLine);
    }
    long scheduledEpochSecond = parseLong(fields[2], "queued-task scheduled epoch second");
    long taskId = parseLong(fields[3], "queued-task id");

    MooValue sentinel = readValue(input, context, "queued task #" + taskId + " sentinel");
    if (!sentinel.equals(new IntegerValue(-111))) {
      throw malformed("invalid queued-task activation sentinel");
    }
    MooValue receiver = readValue(input, context, "queued task #" + taskId + " receiver");
    MooValue typedVerbLocation =
        readValue(input, context, "queued task #" + taskId + " verb location");
    if (!(typedVerbLocation instanceof ObjectValue)
        && !(typedVerbLocation instanceof AnonymousObjectValue)) {
      throw malformed("queued-task verb location must be an object reference");
    }
    long encodedThreadMode = readLong(input, "queued-task thread mode");
    if (encodedThreadMode != 0 && encodedThreadMode != 1) {
      throw malformed("invalid queued-task thread mode: " + encodedThreadMode);
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
        || (!compatibilityFields[8].equals("0") && !compatibilityFields[8].equals("1"))) {
      throw malformed("invalid queued-task compatibility sentinels: " + compatibility);
    }
    long taskPlayer = parseLong(compatibilityFields[3], "queued-task player");
    long programmer = parseLong(compatibilityFields[5], "queued-task programmer");
    long oldVerbLocation =
        parseLong(compatibilityFields[6], "queued-task compatibility verb location");
    if (oldVerbLocation != encodedObjectNumber(typedVerbLocation, context)) {
      throw malformed("queued-task verb-location encodings disagree");
    }

    requireExact(input, "No", "queued-task obsolete argstr");
    requireExact(input, "More", "queued-task obsolete dobjstr");
    requireExact(input, "Parse", "queued-task obsolete iobjstr");
    requireExact(input, "Infos", "queued-task obsolete prepstr");
    String verb = requiredLine(input, "queued-task verb");
    String verbNames = requiredLine(input, "queued-task verb names");

    int variableCount = readSectionCount(input, " variables", "queued-task variable count");
    Map<String, MooValue> locals = new LinkedHashMap<>();
    for (int index = 0; index < variableCount; index++) {
      String name = requiredLine(input, "queued-task variable name");
      if (locals.putIfAbsent(
              name,
              readValue(input, context, "queued task #" + taskId + " variable " + name))
          != null) {
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
    return new QueuedTask(
        taskId,
        scheduledEpochSecond,
        firstLine,
        verb,
        verbNames,
        readProgramSource(input, "queued-task program"),
        locals,
        programmer,
        typedVerbLocation,
        taskPlayer,
        compatibilityFields[8].equals("1"),
        encodedThreadMode == 1);
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

  private static SuspendedTask readSuspendedTask(
      BufferedReader input, ReadContext context) throws IOException {
    String header = requiredLine(input, "suspended-task header");
    int firstSpace = header.indexOf(' ');
    int secondSpace = firstSpace < 0 ? -1 : header.indexOf(' ', firstSpace + 1);
    if (firstSpace <= 0) {
      throw malformed("invalid suspended-task header: " + header);
    }
    long scheduledEpochSecond =
        parseLong(header.substring(0, firstSpace), "suspended-task scheduled epoch second");
    long taskId =
        parseLong(
            header.substring(firstSpace + 1, secondSpace < 0 ? header.length() : secondSpace),
            "suspended-task id");
    MooValue resumeValue =
        secondSpace < 0
            ? new IntegerValue(0)
            : readValue(
                input,
                parseInt(header.substring(secondSpace + 1), "suspended-task resume tag"),
                context,
                "suspended task #" + taskId + " resume value");
    return readSuspendedVm(
        input, context, taskId, scheduledEpochSecond, resumeValue, Optional.empty());
  }

  private static SuspendedTask readInterruptedTask(
      BufferedReader input, ReadContext context) throws IOException {
    String header = requiredLine(input, "interrupted-task header");
    int separator = header.indexOf(' ');
    if (separator <= 0 || separator == header.length() - 1) {
      throw malformed("invalid interrupted-task header: " + header);
    }
    long taskId = parseLong(header.substring(0, separator), "interrupted-task id");
    return readSuspendedVm(
        input,
        context,
        taskId,
        0,
        ErrorValue.E_INTRPT,
        Optional.of(header.substring(separator + 1)));
  }

  private static SuspendedTask readSuspendedVm(
      BufferedReader input,
      ReadContext context,
      long taskId,
      long scheduledEpochSecond,
      MooValue resumeValue,
      Optional<String> interruptionStatus)
      throws IOException {
    MooValue taskLocal = readValue(input, context, "suspended task #" + taskId + " task-local");
    String vmHeader = requiredLine(input, "suspended VM header");
    String[] fields = vmHeader.split(" ", -1);
    if (fields.length != 4) {
      throw malformed("invalid suspended VM header: " + vmHeader);
    }
    int topActivation = parseInt(fields[0], "suspended VM top activation");
    if (topActivation < 0) {
      throw malformed("negative suspended VM top activation");
    }
    int rootActivationVector =
        parseInt(fields[1], "suspended VM root activation vector");
    long functionId = parseLong(fields[2], "suspended VM function id");
    long maxStackDepth = parseLong(fields[3], "suspended VM maximum stack depth");
    if (maxStackDepth < 1) {
      throw malformed("invalid suspended VM maximum stack depth: " + maxStackDepth);
    }
    List<SuspendedActivation> activations = new ArrayList<>(topActivation + 1);
    for (int index = 0; index <= topActivation; index++) {
      activations.add(readSuspendedActivation(input, context, taskId, index));
    }
    return new SuspendedTask(
        taskId,
        scheduledEpochSecond,
        resumeValue,
        taskLocal,
        rootActivationVector,
        functionId,
        maxStackDepth,
        interruptionStatus,
        activations);
  }

  private static SuspendedActivation readSuspendedActivation(
      BufferedReader input, ReadContext context, long taskId, int activationIndex)
      throws IOException {
    String versionLine = requiredLine(input, "suspended activation language version");
    String versionPrefix = "language version ";
    if (!versionLine.startsWith(versionPrefix)) {
      throw malformed("invalid suspended activation language version: " + versionLine);
    }
    int languageVersion =
        parseInt(versionLine.substring(versionPrefix.length()), "activation language version");
    String programSource = readProgramSource(input, "suspended activation program");

    int variableCount =
        readSectionCount(input, " variables", "suspended activation variable count");
    Map<String, Optional<MooValue>> locals = new LinkedHashMap<>();
    for (int index = 0; index < variableCount; index++) {
      String name = requiredLine(input, "suspended activation variable name");
      int localTag = readInt(input, "suspended activation variable tag");
      Optional<MooValue> value =
          localTag == 6
              ? Optional.empty()
              : Optional.of(
                  readValue(
                      input,
                      localTag,
                      context,
                      "suspended task #"
                          + taskId
                          + " activation "
                          + activationIndex
                          + " variable "
                          + name));
      if (locals.putIfAbsent(name, value) != null) {
        throw malformed("duplicate suspended activation variable: " + name);
      }
    }

    int stackCount =
        readSectionCount(
            input, " rt_stack slots in use", "suspended activation runtime stack count");
    List<SuspendedStackSlot> operandStack = new ArrayList<>(stackCount);
    for (int index = 0; index < stackCount; index++) {
      int stackTag = readInt(input, "suspended activation runtime stack tag");
      if (stackTag == 6) {
        operandStack.add(
            new SuspendedStackSlot(Optional.empty(), stackTag, 0));
      } else if (stackTag == 7 || stackTag == 8) {
        operandStack.add(
            new SuspendedStackSlot(
                Optional.empty(),
                stackTag,
                readLong(input, "suspended activation runtime control value")));
      } else {
        operandStack.add(
            new SuspendedStackSlot(
                Optional.of(
                    readValue(
                        input,
                        stackTag,
                        context,
                        "suspended task #"
                            + taskId
                            + " activation "
                            + activationIndex
                            + " stack slot "
                            + index)),
                -1,
                0));
      }
    }
    Collections.reverse(operandStack);

    MooValue sentinel =
        readValue(
            input,
            context,
            "suspended task #" + taskId + " activation " + activationIndex + " sentinel");
    if (!sentinel.equals(new IntegerValue(-111))) {
      throw malformed("invalid suspended activation sentinel");
    }
    MooValue receiver =
        readValue(
            input,
            context,
            "suspended task #" + taskId + " activation " + activationIndex + " receiver");
    MooValue verbLocation =
        readValue(
            input,
            context,
            "suspended task #"
                + taskId
                + " activation "
                + activationIndex
                + " verb location");
    long encodedThreadMode = readLong(input, "suspended activation thread mode");
    if (encodedThreadMode != 0 && encodedThreadMode != 1) {
      throw malformed("invalid suspended activation thread mode: " + encodedThreadMode);
    }

    String compatibility = requiredLine(input, "suspended activation compatibility fields");
    String[] compatibilityFields = compatibility.split(" ", -1);
    if (compatibilityFields.length != 9
        || !compatibilityFields[1].equals("-7")
        || !compatibilityFields[2].equals("-8")
        || !compatibilityFields[4].equals("-9")
        || !compatibilityFields[7].equals("-10")) {
      throw malformed("invalid suspended activation compatibility fields: " + compatibility);
    }
    long encodedReceiver =
        parseLong(compatibilityFields[0], "suspended activation receiver");
    long taskPlayer =
        parseLong(compatibilityFields[3], "suspended activation player");
    long programmer =
        parseLong(compatibilityFields[5], "suspended activation programmer");
    long encodedVerbLocation =
        parseLong(compatibilityFields[6], "suspended activation verb location");
    long encodedDebug =
        parseLong(compatibilityFields[8], "suspended activation debug mode");
    if (encodedDebug != 0 && encodedDebug != 1) {
      throw malformed("invalid suspended activation debug mode: " + encodedDebug);
    }
    if (receiver instanceof ObjectValue object && object.value() != encodedReceiver) {
      throw malformed("suspended activation receiver encodings disagree");
    }
    if (verbLocation instanceof ObjectValue object && object.value() != encodedVerbLocation) {
      throw malformed("suspended activation verb-location encodings disagree");
    }

    requireExact(input, "No", "suspended activation obsolete argstr");
    requireExact(input, "More", "suspended activation obsolete dobjstr");
    requireExact(input, "Parse", "suspended activation obsolete iobjstr");
    requireExact(input, "Infos", "suspended activation obsolete prepstr");
    String verb = requiredLine(input, "suspended activation verb");
    String verbNames = requiredLine(input, "suspended activation verb names");

    int temporaryTag = readInt(input, "suspended activation temporary tag");
    Optional<MooValue> temporary =
        temporaryTag == 6
            ? Optional.empty()
            : Optional.of(
                readValue(
                    input,
                    temporaryTag,
                    context,
                    "suspended task #"
                        + taskId
                        + " activation "
                        + activationIndex
                        + " temporary"));
    String counterLine = requiredLine(input, "suspended activation counters");
    String[] counters = counterLine.split(" ", -1);
    if (counters.length != 2 && counters.length != 3) {
      throw malformed("invalid suspended activation counters: " + counterLine);
    }
    long programCounter = parseLong(counters[0], "suspended activation program counter");
    long builtinFunctionCounter =
        parseLong(counters[1], "suspended activation builtin function counter");
    long errorCounter =
        counters.length == 2
            ? programCounter
            : parseLong(counters[2], "suspended activation error counter");
    if (builtinFunctionCounter != 0) {
      throw malformed(
          "unsupported suspended builtin continuation at program counter "
              + builtinFunctionCounter);
    }

    return new SuspendedActivation(
        languageVersion,
        programSource,
        locals,
        operandStack,
        receiver,
        verbLocation,
        encodedThreadMode == 1,
        taskPlayer,
        programmer,
        encodedDebug == 1,
        verb,
        verbNames,
        temporary,
        programCounter,
        builtinFunctionCounter,
        errorCounter);
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
    writeValue(output, object.lastMove(), context);
    writeObjectList(output, object.contents(), context);
    writeParents(output, object.parents(), context);
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
    writeParents(output, object.parents(), context);
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
                tag == 5
                    ? null
                    : readValue(
                        input,
                        tag,
                        context,
                        "object #" + expectedId + " property slot " + index + " value"),
                tag == 5,
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
            new WorldVerb(
                verb.names(), verb.owner(), verb.permissions(), verb.preposition(), source));
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
    Map<Long, List<WorldProperty>> restored = new LinkedHashMap<>();
    for (RawObject object : objects.values()) {
      restoreProperties(object, objects, restored, new ArrayList<>());
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

    for (long parentId : object.parents()) {
      if (parentId == -1) {
        continue;
      }
      RawObject parent = objects.get(parentId);
      if (parent == null) {
        throw malformed("object #" + object.id() + " has missing parent #" + parentId);
      }
      restoreProperties(parent, objects, restored, ancestry);
    }

    List<Long> canonical = rawAncestry(object.id(), objects);
    int expectedSlots = 0;
    for (long ancestorId : canonical) {
      expectedSlots =
          Math.addExact(
              expectedSlots,
              Objects.requireNonNull(objects.get(ancestorId)).propertyNames().size());
    }
    if (object.propertySlots().size() != expectedSlots) {
      throw malformed(
          "object #"
              + object.id()
              + " has "
              + object.propertyNames().size()
              + " definitions and "
              + object.propertySlots().size()
              + " value slots for "
              + (expectedSlots - object.propertyNames().size())
              + " inherited properties");
    }

    List<WorldProperty> properties = new ArrayList<>(object.propertySlots().size());
    int slotIndex = 0;
    for (long definingId : canonical) {
      RawObject defining = Objects.requireNonNull(objects.get(definingId));
      for (String name : defining.propertyNames()) {
        RawPropertySlot slot = object.propertySlots().get(slotIndex++);
        boolean defined = definingId == object.id();
        if (defined && slot.clear()) {
          throw malformed("object #" + object.id() + " has a clear local property " + name);
        }
        MooValue value = slot.value();
        if (value == null) {
          value = clearFallback(object, name, restored);
        }
        properties.add(
            new WorldProperty(
                name, value, slot.owner(), slot.permissions(), slot.clear(), defined));
      }
    }
    ancestry.removeLast();
    List<WorldProperty> result = List.copyOf(properties);
    restored.put(object.id(), result);
    return result;
  }

  private static MooValue clearFallback(
      RawObject object, String propertyName, Map<Long, List<WorldProperty>> restored)
      throws IOException {
    for (long parentId : object.parents()) {
      if (parentId == -1) {
        continue;
      }
      List<WorldProperty> parentProperties = restored.get(parentId);
      if (parentProperties == null) {
        continue;
      }
      for (WorldProperty property : parentProperties) {
        if (property.name().equalsIgnoreCase(propertyName)) {
          return property.value();
        }
      }
    }
    throw malformed(
        "object #" + object.id() + " has clear property without direct-parent fallback "
            + propertyName);
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

  private static List<Long> rawAncestry(long objectId, Map<Long, RawObject> objects)
      throws IOException {
    List<Long> result = new ArrayList<>();
    collectRawAncestry(objectId, objects, new LinkedHashSet<>(), new LinkedHashSet<>(), result);
    return List.copyOf(result);
  }

  private static void collectRawAncestry(
      long objectId,
      Map<Long, RawObject> objects,
      Set<Long> visiting,
      Set<Long> visited,
      List<Long> result)
      throws IOException {
    if (visited.contains(objectId)) {
      return;
    }
    RawObject object = objects.get(objectId);
    if (object == null) {
      throw malformed("missing inheritance object #" + objectId);
    }
    if (!visiting.add(objectId)) {
      throw malformed("cyclic property ancestry at object #" + objectId);
    }
    visited.add(objectId);
    result.add(objectId);
    for (long parentId : object.parents()) {
      if (parentId != -1) {
        collectRawAncestry(parentId, objects, visiting, visited, result);
      }
    }
    visiting.remove(objectId);
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
    return switch (tag) {
      case 10 -> {
        int count = readCount(input, "map count");
        List<MooValue> keys = new ArrayList<>(count);
        MapValue values = new MapValue(Map.of());
        for (int index = 0; index < count; index++) {
          MooValue key =
              readValue(input, context, location + " map entry " + (index + 1) + " key");
          MooValue value =
              readValue(input, context, location + " map entry " + (index + 1) + " value");
          for (MooValue existing : keys) {
            if (MapValue.compareKeys(existing, key) == 0) {
              throw malformed("duplicate map key in v17 value");
            }
          }
          keys.add(key);
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
      case 13 -> readWaif(input, context, location);
      case 14 -> BooleanValue.of(readLong(input, "boolean value") != 0);
      default -> throw malformed("unsupported v17 value tag " + tag + " at " + location);
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

  private static int readSectionCount(BufferedReader input, String suffix, String field)
      throws IOException {
    String line = requiredLine(input, field);
    if (!line.endsWith(suffix)) {
      throw malformed("invalid " + field + ": " + line);
    }
    return parseCount(line.substring(0, line.length() - suffix.length()), field);
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
