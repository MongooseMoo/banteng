package world.mongoose.banteng.persistence;

import static world.mongoose.banteng.persistence.DbScanner.*;
import java.io.*;
import java.util.*;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.*;
import world.mongoose.banteng.world.*;

/** Version-aware Toast task records; values remain owned by the database reader. */
final class TaskSectionReader {
  interface ValueReader { MooValue read(BufferedReader input, int tag, String location) throws IOException; }
  interface ObjectNumbers { long read(MooValue value) throws IOException; }
  private final ObjectNumbers objectNumbers;
  private final int version;
  private final ValueReader values;
  TaskSectionReader(int version, ValueReader values) { this(version, values, value -> -1); }
  TaskSectionReader(int version, ValueReader values, ObjectNumbers objectNumbers) {
    this.version = version; this.values = values; this.objectNumbers = objectNumbers;
  }
  record Tail(List<DurableTask> tasks, List<ActiveConnection> connections) {}
  Tail read(BufferedReader input) throws IOException {
    int clocks = readSectionCount(input, " clocks", "clock count");
    for (int index = 0; index < clocks; index++) {
      String[] fields = requiredLine(input, "obsolete clock").split(" ", -1);
      if (fields.length != 3) throw malformed("invalid obsolete clock");
      for (String field : fields) parseLong(field, "obsolete clock");
    }
    List<DurableTask> tasks = new ArrayList<>();
    int queued = readSectionCount(input, " queued tasks", "queued task count");
    for (int index = 0; index < queued; index++) tasks.add(readQueuedTask(input));
    input.mark(4096);
    String suspended = input.readLine();
    if (suspended == null) return new Tail(List.copyOf(tasks), List.of());
    input.reset();
    int count = readSectionCount(input, " suspended tasks", "suspended task count");
    for (int index = 0; index < count; index++) tasks.add(readSuspendedTask(input));
    if (version >= 10) {
      int interrupted = readSectionCount(input, " interrupted tasks", "interrupted task count");
      for (int index = 0; index < interrupted; index++) tasks.add(readInterruptedTask(input));
    }
    String connectionHeader = input.readLine();
    if (connectionHeader == null) return new Tail(List.copyOf(tasks), List.of());
    boolean listeners = connectionHeader.endsWith(" active connections with listeners");
    String suffix = listeners ? " active connections with listeners" : " active connections";
    if (!connectionHeader.endsWith(suffix)) throw malformed("invalid active connection header");
    int connections = parseCount(connectionHeader.substring(0, connectionHeader.length() - suffix.length()), "connections");
    List<ActiveConnection> result = new ArrayList<>();
    for (int index = 0; index < connections; index++) {
      String[] fields = requiredLine(input, "active connection").split(" ", -1);
      if (fields.length != (listeners ? 2 : 1)) throw malformed("invalid active connection");
      result.add(new ActiveConnection(parseLong(fields[0], "connection player"),
          listeners ? parseLong(fields[1], "connection listener") : 0));
    }
    return new Tail(List.copyOf(tasks), List.copyOf(result));
  }
  private MooValue readValue(BufferedReader input, String location) throws IOException {
    return readValue(input, readInt(input, location + " tag"), location);
  }
  private MooValue readValue(BufferedReader input, int tag, String location) throws IOException {
    return values.read(input, tag, location);
  }
  private static int readSectionCount(BufferedReader input, String suffix, String field) throws IOException {
    String line = requiredLine(input, field);
    if (!line.endsWith(suffix)) throw malformed("invalid " + field + ": " + line);
    return parseCount(line.substring(0, line.length() - suffix.length()), field);
  }
  private long objectNumber(MooValue value) throws IOException {
    if (value instanceof ObjectValue object) return object.value();
    return objectNumbers.read(value);
  }
  private record ActivationHeader(MooValue receiver, MooValue verbLocation, boolean threadMode,
      long player, long programmer, boolean debug, String verb, String verbNames,
      long legacyReceiver, long legacyVerbLocation, String debugText) {}
  private QueuedTask readQueuedTask(BufferedReader input)
      throws IOException {
    String header = requiredLine(input, "queued-task header");
    String[] fields = header.split(" ", -1);
    if (fields.length != 4) {
      throw malformed("invalid queued-task header: " + header);
    }
    int firstLine = parseInt(fields[1], "queued-task first line");
    if (firstLine < 1) {
      throw malformed("invalid queued-task first line: " + firstLine);
    }
    long scheduledEpochSecond = parseLong(fields[2], "queued-task scheduled epoch second");
    long taskId = parseLong(fields[3], "queued-task id");

    ActivationHeader activationHeader = readActivationHeader(
        input, "queued-task", "queued task #" + taskId);
    MooValue receiver = activationHeader.receiver();
    MooValue typedVerbLocation = activationHeader.verbLocation();
    if (!(typedVerbLocation instanceof ObjectValue)
        && !(typedVerbLocation instanceof AnonymousObjectValue)) {
      throw malformed("queued-task verb location must be an object reference");
    }
    if (activationHeader.legacyVerbLocation() != objectNumber(typedVerbLocation)) {
      throw malformed("queued-task verb-location encodings disagree");
    }
    long receiverObject = activationHeader.legacyReceiver();
    long taskPlayer = activationHeader.player();
    long programmer = activationHeader.programmer();
    String verb = activationHeader.verb();
    String verbNames = activationHeader.verbNames();

    int variableCount = readSectionCount(input, " variables", "queued-task variable count");
    Map<String, Optional<MooValue>> locals = new LinkedHashMap<>();
    for (int index = 0; index < variableCount; index++) {
      String name = requiredLine(input, "queued-task variable name");
      Optional<MooValue> value =
          readRuntimeVariable(
              input, "queued task #" + taskId + " variable " + name);
      if (locals.putIfAbsent(name, value) != null) {
        throw malformed("duplicate queued-task variable: " + name);
      }
    }
    MooValue localReceiver = locals.getOrDefault("this", Optional.empty()).orElse(null);
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
        ProgramSectionReader.readSource(input, "queued-task program"),
        new QueuedEnvironment(locals),
        programmer,
        typedVerbLocation,
        taskPlayer,
        activationHeader.debug(),
        activationHeader.threadMode());
  }

  private ActivationHeader readActivationHeader(
      BufferedReader input, String field, String location) throws IOException {
    MooValue sentinel = readValue(input, location + " sentinel");
    if (!sentinel.equals(new IntegerValue(V17Format.ACTIVATION_PLACEHOLDER))) {
      throw malformed("invalid " + field + " activation sentinel");
    }
    Optional<MooValue> typedReceiver = version >= 11
        ? Optional.of(readValue(input, location + " receiver")) : Optional.empty();
    Optional<MooValue> typedVerbLocation = version >= 13
        ? Optional.of(readValue(input, location + " verb location")) : Optional.empty();
    long threadMode = version >= 16 ? readLong(input, field + " thread mode") : 1;
    if (threadMode != 0 && threadMode != 1) {
      throw malformed("invalid " + field + " thread mode: " + threadMode);
    }
    String compatibility = requiredLine(input, field + " compatibility fields");
    String[] fields = compatibility.split(" ", -1);
    if (fields.length != 9) throw malformed("invalid " + field + " activation fields");
    long legacyReceiver = parseLong(fields[0], field + " receiver");
    long player = parseLong(fields[3], field + " player");
    long programmer = parseLong(fields[5], field + " programmer");
    long legacyVerbLocation = parseLong(fields[6], field + " compatibility verb location");
    long debug = parseLong(fields[8], field + " debug mode");

    for (int index = 0; index < 4; index++) requiredLine(input, field + " obsolete parse info");
    MooValue receiver = typedReceiver.orElseGet(() -> new ObjectValue(legacyReceiver));
    MooValue verbLocation = typedVerbLocation.orElseGet(() -> new ObjectValue(legacyVerbLocation));
    String verb = requiredLine(input, field + " verb");
    String verbNames = requiredLine(input, field + " verb names");
    return new ActivationHeader(receiver, verbLocation, threadMode == 1, player, programmer,
        debug != 0, verb, verbNames, legacyReceiver, legacyVerbLocation, fields[8]);
  }

  private SuspendedTask readSuspendedTask(
      BufferedReader input) throws IOException {
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
                "suspended task #" + taskId + " resume value");
    return readSuspendedVm(
        input, taskId, scheduledEpochSecond < 0 ? Long.MAX_VALUE : scheduledEpochSecond, resumeValue, Optional.empty());
  }

  private SuspendedTask readInterruptedTask(
      BufferedReader input) throws IOException {
    String header = requiredLine(input, "interrupted-task header");
    int separator = header.indexOf(' ');
    if (separator <= 0 || separator == header.length() - 1) {
      throw malformed("invalid interrupted-task header: " + header);
    }
    long taskId = parseLong(header.substring(0, separator), "interrupted-task id");
    return readSuspendedVm(
        input,
        taskId,
        0,
        ErrorValue.E_INTRPT,
        Optional.of(header.substring(separator + 1)));
  }

  private SuspendedTask readSuspendedVm(
      BufferedReader input,
      long taskId,
      long scheduledEpochSecond,
      MooValue resumeValue,
      Optional<String> interruptionStatus)
      throws IOException {
    MooValue taskLocal = version >= 6 ? readValue(input, "suspended task #" + taskId + " task-local") : new MooValue.MapValue(Map.of());
    String vmHeader = requiredLine(input, "suspended VM header");
    String[] fields = vmHeader.split(" ", -1);
    if (fields.length != 3 && fields.length != 4) {
      throw malformed("invalid suspended VM header: " + vmHeader);
    }
    int topActivation = parseInt(fields[0], "suspended VM top activation");
    if (topActivation < 0) {
      throw malformed("negative suspended VM top activation");
    }
    int rootActivationVector =
        parseInt(fields[1], "suspended VM root activation vector");
    long functionId = parseLong(fields[2], "suspended VM function id");
    long maxStackDepth = fields.length == 4 ? parseLong(fields[3], "suspended VM maximum stack depth") : 50;
    if (maxStackDepth < 1) {
      throw malformed("invalid suspended VM maximum stack depth: " + maxStackDepth);
    }
    List<SuspendedActivation> activations = new ArrayList<>(topActivation + 1);
    for (int index = 0; index <= topActivation; index++) {
      activations.add(readSuspendedActivation(input, taskId, index));
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

  private SuspendedActivation readSuspendedActivation(
      BufferedReader input, long taskId, int activationIndex)
      throws IOException {
    String versionLine = requiredLine(input, "suspended activation language version");
    String versionPrefix = "language version ";
    if (!versionLine.startsWith(versionPrefix)) {
      throw malformed("invalid suspended activation language version: " + versionLine);
    }
    int languageVersion =
        parseInt(versionLine.substring(versionPrefix.length()), "activation language version");
    String programSource = ProgramSectionReader.readSource(input, "suspended activation program");

    int variableCount =
        readSectionCount(input, " variables", "suspended activation variable count");
    Map<String, Optional<MooValue>> locals = new LinkedHashMap<>();
    for (int index = 0; index < variableCount; index++) {
      String name = requiredLine(input, "suspended activation variable name");
      Optional<MooValue> value =
          readRuntimeVariable(
              input,
              "suspended task #"
                  + taskId
                  + " activation "
                  + activationIndex
                  + " variable "
                  + name);
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
      if (stackTag == V17Format.ValueTag.NONE.code()) {
        operandStack.add(
            new SuspendedStackSlot(Optional.empty(), V17Format.stackKind(stackTag), 0));
      } else if (stackTag == V17Format.ValueTag.CATCH.code() || stackTag == V17Format.ValueTag.FINALLY.code()) {
        operandStack.add(
            new SuspendedStackSlot(
                Optional.empty(),
                V17Format.stackKind(stackTag),
                readLong(input, "suspended activation runtime control value")));
      } else {
        operandStack.add(
            new SuspendedStackSlot(
                Optional.of(
                    readValue(
                        input,
                        stackTag,
                                "suspended task #"
                            + taskId
                            + " activation "
                            + activationIndex
                            + " stack slot "
                            + index)),
                SuspendedStackSlot.Kind.VALUE,
                0));
      }
    }
    Collections.reverse(operandStack);

    ActivationHeader activationHeader = readActivationHeader(input, "suspended activation",
        "suspended task #" + taskId + " activation " + activationIndex);
    MooValue receiver = activationHeader.receiver();
    MooValue verbLocation = activationHeader.verbLocation();
    if (receiver instanceof ObjectValue object && object.value() != activationHeader.legacyReceiver()) {
      throw malformed("suspended activation receiver encodings disagree");
    }
    if (verbLocation instanceof ObjectValue object
        && object.value() != activationHeader.legacyVerbLocation()) {
      throw malformed("suspended activation verb-location encodings disagree");
    }
    long taskPlayer = activationHeader.player();
    long programmer = activationHeader.programmer();
    String verb = activationHeader.verb();
    String verbNames = activationHeader.verbNames();

    int temporaryTag = readInt(input, "suspended activation temporary tag");
    Optional<MooValue> temporary =
        temporaryTag == V17Format.ValueTag.NONE.code()
            ? Optional.empty()
            : Optional.of(
                readValue(
                    input,
                    temporaryTag,
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
    Optional<SuspendedActivation.BuiltinContinuation> builtin = builtinFunctionCounter == 0
        ? Optional.empty() : Optional.of(readBuiltin(input, requiredLine(input, "builtin name")));

    return new SuspendedActivation(
        languageVersion,
        programSource,
        locals,
        operandStack,
        receiver,
        verbLocation,
        activationHeader.threadMode(),
        taskPlayer,
        programmer,
        activationHeader.debug(),
        verb,
        verbNames,
        temporary,
        programCounter,
        builtinFunctionCounter,
        errorCounter, builtin);
  }

  private Optional<MooValue> readRuntimeVariable(
      BufferedReader input, String location) throws IOException {
    int tag = readInt(input, location + " tag");
    return tag == V17Format.ValueTag.NONE.code()
        ? Optional.empty()
        : Optional.of(readValue(input, tag, location));
  }
  private SuspendedActivation.BuiltinContinuation readBuiltin(BufferedReader input, String name) throws IOException {
    return switch (name) {
      case "eval" -> new SuspendedActivation.EvalContinuation();
      case "create", "recreate" -> new SuspendedActivation.CreateContinuation(name.equals("recreate"),
          readBuiltinNumbers(input, "bf_create data: oid = (-?[0-9]+)", 1)[0]);
      case "recycle" -> new SuspendedActivation.RecycleContinuation(
          readBuiltinNumbers(input, "bf_recycle data: oid = (-?[0-9]+), cont = (-?[0-9]+)", 2)[0]);
      case "move" -> {
        long[] fields = readBuiltinNumbers(input,
            "bf_move data: what = (-?[0-9]+), where = (-?[0-9]+), position = (-?[0-9]+)", 3);
        yield new SuspendedActivation.MoveContinuation(fields[0], fields[1], fields[2]);
      }
      case "call_function" -> {
        String line = requiredLine(input, "call_function data");
        String prefix = "bf_call_function data: fname = ";
        if (!line.startsWith(prefix)) throw malformed("invalid call_function data");
        yield new SuspendedActivation.CallFunctionContinuation(readBuiltin(input, line.substring(prefix.length())));
      }
      default -> throw malformed("unsupported suspended builtin " + name);
    };
  }

  private static long[] readBuiltinNumbers(BufferedReader input, String pattern, int count) throws IOException {
    String line = requiredLine(input, "builtin continuation data");
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(line);
    if (!matcher.matches()) throw malformed("invalid builtin continuation data: " + line);
    long[] fields = new long[count];
    for (int index = 0; index < count; index++) fields[index] = parseLong(matcher.group(index + 1), "builtin data");
    return fields;
  }
}
