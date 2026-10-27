package world.mongoose.banteng.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import world.mongoose.banteng.builtin.*;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.*;
import world.mongoose.banteng.vm.*;
import world.mongoose.banteng.world.*;

final class LegacyDatabaseRestorationTest {
  private static Path fixture(String name) throws Exception {
    return Path.of(Objects.requireNonNull(LegacyDatabaseRestorationTest.class.getResource(
        "/world/mongoose/banteng/persistence/legacy/" + name + ".db")).toURI());
  }

  @Test void toastBrokenFixturesHaveTheSameAdmission() throws Exception {
    var reader = new LambdaMooV5Reader(ServerLog.stderr(System.Logger.Level.OFF), new LambdaMooV17Codec());
    reader.read(fixture("Broken1"));
    for (String name : List.of("Broken2", "Broken3", "Broken4", "Broken5")) {
      assertThrows(IOException.class, () -> reader.read(fixture(name)), name);
    }
  }

  @Test void toastSuspendedEvalResumesAndCanCheckpointBeforeResuming(@TempDir Path directory) throws Exception {
    var codec = new LambdaMooV17Codec();
    Checkpoint checkpoint = new LambdaMooV4Reader(codec).read(fixture("Suspended"));
    assertEquals(List.of(new ActiveConnection(3, 0)), checkpoint.activeConnections());
    SuspendedTask task = assertInstanceOf(SuspendedTask.class, checkpoint.tasks().getFirst());
    assertEquals(2147483647L, task.scheduledEpochSecond());
    assertEquals(2, task.activations().size());
    VmState state = restored(task);
    assertEquals(VmSnapshot.ReturnMode.EVAL, state.snapshot().frames().getFirst().returnMode());
    SuspendedTask exported = DurableTaskCodec.durableSuspension(task.taskId(), 0,
        state.snapshot(), new IntegerValue(42));
    Path canonical = directory.resolve("canonical.db");
    codec.writeAtomic(canonical, checkpoint.world().snapshot(), List.of(exported), checkpoint.activeConnections());
    Checkpoint reread = codec.read(canonical);
    VmState resumed = restored((SuspendedTask) reread.tasks().getFirst());
    resumed.resume(new IntegerValue(42));
    execute(resumed.snapshot().frames().getFirst().program(), resumed, checkpoint.world(), task.taskId());
    assertEquals(VmState.Outcome.RETURNED, resumed.outcome());
    assertEquals(List.of("=> 42"), resumed.snapshot().output());
  }

  @Test void inheritedClearSlotsAndRecycledNumbersSurviveCanonicalOutput(@TempDir Path directory) throws Exception {
    for (int version : List.of(4, 5)) {
      String parentHeader = version == 4 ? "root\n\n0\n0\n-1\n-1\n-1\n-1\n1\n-1\n"
          : "root\n0\n0\n1\n-1\n4\n0\n1\n-1\n4\n1\n1\n1\n";
      String childHeader = version == 4 ? "child\n\n0\n0\n-1\n-1\n-1\n0\n-1\n-1\n"
          : "child\n0\n0\n1\n-1\n4\n0\n1\n0\n4\n0\n";
      String text = "** LambdaMOO Database, Format Version " + version + " **\n3\n0\n0\n0\n"
          + "#0\n" + parentHeader + "1\nunprogrammed\n0\n15\n-1\n1\nx\n1\n0\n7\n0\n7\n"
          + "#1\n" + childHeader + "0\n0\n1\n5\n0\n3\n#2 recycled\n0 clocks\n0 queued tasks\n";
      Path path = directory.resolve("v" + version + ".db");
      Files.writeString(path, text, StringValue.charset());
      var codec = new LambdaMooV17Codec();
      DbCodec reader = version == 4 ? new LambdaMooV4Reader(codec)
          : new LambdaMooV5Reader(ServerLog.stderr(System.Logger.Level.OFF), codec);
      Checkpoint read = reader.read(path);
      WorldProperty inherited = Objects.requireNonNull(read.world().snapshot().objects().get(1L)).properties().getFirst();
      assertTrue(inherited.clear()); assertFalse(inherited.defined());
      assertEquals(new IntegerValue(7), inherited.value());
      assertEquals(0, inherited.owner()); assertEquals(new PropertyPerms(3), inherited.perms());
      assertEquals("", Objects.requireNonNull(read.world().snapshot().objects().get(0L)).verbs().getFirst().programSource());
      Path output = directory.resolve("v" + version + "-canonical.db");
      codec.writeAtomic(output, read.world().snapshot(), read.tasks(), read.activeConnections());
      assertEquals(read.world().snapshot(), codec.read(output).world().snapshot());
      assertEquals(2, read.world().snapshot().lastUsedObjectId());
      Files.writeString(path, text.replace("\n5\n0\n3\n#2 recycled", "\n0\n11\n0\n3\n#2 recycled"), StringValue.charset());
      Checkpoint overridden = reader.read(path);
      WorldProperty override = Objects.requireNonNull(overridden.world().snapshot().objects().get(1L)).properties().getFirst();
      assertFalse(override.clear()); assertFalse(override.defined());
      assertEquals(new IntegerValue(11), override.value());
      codec.writeAtomic(output, overridden.world().snapshot(), overridden.tasks(), overridden.activeConnections());
      assertEquals(overridden.world().snapshot(), codec.read(output).world().snapshot());
      String values = """
          4
          6
          0
          -7
          1
          -1
          2
          café
          3
          0
          4
          1
          0
          9
          9
          1.25
          """;
      Files.writeString(path, text.replace("x\n1\n0\n7\n0\n7\n", "x\n1\n" + values + "0\n7\n"), StringValue.charset());
      Checkpoint allValues = reader.read(path);
      ListValue value = assertInstanceOf(ListValue.class,
          Objects.requireNonNull(allValues.world().snapshot().objects().get(1L)).properties().getFirst().value());
      assertEquals(List.of(new IntegerValue(-7), new ObjectValue(-1), StringValue.of("café"),
          ErrorValue.E_NONE, new ListValue(List.of(new IntegerValue(9))), new FloatValue(1.25)), value.elements());
    }
  }

  @Test void indexedCallRestoresExistingIndexStateAndExports() {
    String source = "return {10, 20}[suspend()];\n";
    var layout = new ToastV17ProgramLayout(4);
    var boundary = layout.callBoundaries(source, -1).getFirst();
    SuspendedActivation activation = new SuspendedActivation(4, source, Map.of(),
        List.of(new SuspendedStackSlot(Optional.of(new ListValue(List.of(new IntegerValue(10),
            new IntegerValue(20)))), SuspendedStackSlot.Kind.VALUE, 0)),
        new ObjectValue(-1), new ObjectValue(-1), false, 0, 0, true, "", "",
        Optional.empty(), boundary.programCounter(), 0, boundary.errorProgramCounter());
    SuspendedTask task = new SuspendedTask(8, 0, new IntegerValue(2), new MapValue(Map.of()),
        -1, 0, 50, Optional.empty(), List.of(activation));
    VmState state = restored(task);
    assertEquals(1, state.snapshot().frames().getFirst().indexCollections().size());
    DurableTaskCodec.durableSuspension(8, 0, state.snapshot(), new IntegerValue(2));
    state.resume(new IntegerValue(2));
    execute(state.snapshot().frames().getFirst().program(), state, new WorldTxn(List.of(), List.of()), 8);
    assertEquals(Optional.of(new IntegerValue(20)), state.returnValue());
  }

  @Test void oldQueuedHeadersClocksAndConnectionsRestoreForBothVersions() throws Exception {
    String tail = """
        1 clocks
        1 2 3
        1 queued tasks
        9 17 1000 91
        0
        -111
        0 -7 -8 0 -9 0 0 -10 8
        No
        More
        Parse
        Infos
        queued
        queued
        1 variables
        this
        1
        0
        return 42;
        .
        0 suspended tasks
        1 active connections
        0
        """;
    for (int version : List.of(4, 5)) {
      var result = new TaskSectionReader(version, (input, tag, location) -> ValueTagDecoder.readV5(input, tag))
          .read(new java.io.BufferedReader(new java.io.StringReader(tail)));
      QueuedTask queued = (QueuedTask) result.tasks().getFirst();
      assertEquals(91, queued.taskId()); assertTrue(queued.debug());
      assertEquals(17, DurableTaskCodec.restoreQueuedProgram(queued).sourceLine(0));
      assertEquals(List.of(new ActiveConnection(0, 0)), result.connections());
      var program = DurableTaskCodec.restoreQueuedProgram(queued);
      VmState state = new VmState(new TaskOrigin(queued.initialLocals(), queued.programmer(),
          queued.verbLocation(), queued.calledVerb(), queued.fullVerbName(), queued.debug()),
          new TaskBudget(10000, 5, 50));
        execute(program, state, new WorldTxn(List.of(), List.of()), queued.taskId());
      assertEquals(Optional.of(new IntegerValue(42)), state.returnValue());
    }
  }

  @Test void nestedAssignmentAndRangeCallsRestoreTheirExistingIndexContexts() {
    ListValue inner = new ListValue(List.of(new IntegerValue(10), new IntegerValue(20)));
    ListValue outer = new ListValue(List.of(inner));
    for (boolean assignment : List.of(false, true)) {
      String source = assignment ? "x[1][suspend()] = 9; return x;\n"
          : "return {10, 20}[1..suspend()];\n";
      var layout = new ToastV17ProgramLayout(4);
      var boundary = layout.callBoundaries(source, -1).getFirst();
      List<MooValue> stack = assignment ? List.of(inner, new IntegerValue(1), outer)
          : List.of(new IntegerValue(1), inner);
      List<SuspendedStackSlot> slots = stack.stream().map(value -> new SuspendedStackSlot(
          Optional.of(value), SuspendedStackSlot.Kind.VALUE, 0)).toList();
      SuspendedActivation activation = new SuspendedActivation(4, source,
          assignment ? Map.of("x", Optional.of(outer)) : Map.of(), slots,
          new ObjectValue(-1), new ObjectValue(-1), true, 0, 0, true, "", "", Optional.empty(),
          boundary.programCounter(), 0, boundary.errorProgramCounter());
      SuspendedTask task = new SuspendedTask(9, 0, new IntegerValue(2), new MapValue(Map.of()),
          -1, 0, 50, Optional.empty(), List.of(activation));
      VmState state = restored(task);
      assertEquals(assignment ? 2 : 1, state.snapshot().frames().getFirst().indexCollections().size());
      SuspendedTask canonical = DurableTaskCodec.durableSuspension(9, 0, state.snapshot(), new IntegerValue(2));
      VmState resumed = restored(canonical);
      resumed.resume(new IntegerValue(2));
      execute(resumed.snapshot().frames().getFirst().program(), resumed, new WorldTxn(List.of(), List.of()), 9);
      MooValue expected = assignment ? new ListValue(List.of(new ListValue(List.of(new IntegerValue(10),
          new IntegerValue(9))))) : inner;
      assertEquals(Optional.of(expected), resumed.returnValue());
    }
  }

  @Test void oldOptionalResumeValueVmMaximumAndRecursiveBuiltinData() throws Exception {
    String text = Files.readString(fixture("Suspended"), StringValue.charset());
    text = text.substring(text.indexOf("0 clocks\n"))
        .replace("2147483647 1900608168 0\n0\n", "-1 1900608168\n")
        .replace("1 -1 0 50\n", "1 -1 0\n")
        .replace("3 2 1\neval\n", """
            3 2 1
            call_function
            bf_call_function data: fname = call_function
            bf_call_function data: fname = eval
            """);
    for (int version : List.of(4, 5)) {
      var tail = new TaskSectionReader(version, (input, tag, location) -> ValueTagDecoder.readV5(input, tag))
          .read(new java.io.BufferedReader(new java.io.StringReader(text)));
      SuspendedTask task = (SuspendedTask) tail.tasks().getFirst();
      assertEquals(Long.MAX_VALUE, task.scheduledEpochSecond());
      assertEquals(new IntegerValue(0), task.resumeValue());
      assertEquals(50, task.maxStackDepth());
      assertInstanceOf(SuspendedActivation.CallFunctionContinuation.class,
          task.activations().getLast().builtinContinuation().orElseThrow());
      assertEquals(VmSnapshot.ReturnMode.EVAL, restored(task).snapshot().frames().getFirst().returnMode());
    }
  }

  @Test void actualBuiltinHookFramesCheckpointAndResume(@TempDir Path directory) throws Exception {
    for (String operation : List.of("create", "recreate", "recycle", "accept", "exitfunc", "enterfunc")) {
      WorldVerb hook = new WorldVerb(operation.equals("create") || operation.equals("recreate")
          ? "initialize" : operation, 0, new VerbPerms(15), ArgSpec.NONE, -1, ArgSpec.NONE,
          "suspend(); return 1;\n");
      List<WorldObject> objects = new ArrayList<>();
      objects.add(new WorldObject(0, "wizard", 7, 0, -1, -1, List.of(), List.of(),
          operation.equals("create") || operation.equals("recreate") ? List.of(hook) : List.of(), List.of()));
      if (!operation.equals("create") && !operation.equals("recreate")) {
        objects.add(new WorldObject(1, "target", 0, 0, operation.equals("exitfunc") ? 3 : -1, -1,
            List.of(), List.of(), operation.equals("recycle") ? List.of(hook) : List.of(), List.of()));
        objects.add(new WorldObject(2, "destination", 0, 0, -1, -1, List.of(), List.of(),
            operation.equals("accept") || operation.equals("enterfunc") ? List.of(hook) : List.of(), List.of()));
        if (operation.equals("exitfunc")) {
          objects.add(new WorldObject(3, "old location", 0, 0, -1, -1, List.of(1L), List.of(), List.of(hook), List.of()));
        }
      }
      WorldTxn world = new WorldTxn(List.of(), objects, Map.of(), Map.of(), List.of(),
          operation.equals("recreate") ? 1 : objects.size() - 1);
      String source = switch (operation) {
        case "create" -> "return create(#0);";
        case "recreate" -> "return recreate(#1, #0);";
        case "recycle" -> "recycle(#1); return valid(#1);";
        default -> "move(#1, #2); return #1.location;";
      };
      var program = new world.mongoose.banteng.bytecode.MooCompiler().compile(source);
      VmState state = new VmState(new TaskOrigin(Map.of("this", new ObjectValue(0), "player", new ObjectValue(0)),
          0, new ObjectValue(0), "test", "test", true), new TaskBudget(10000, 5, 50));
        execute(program, state, world, 77);
      assertEquals(VmState.Outcome.SUSPENDED, state.outcome(), operation);
      SuspendedTask task = DurableTaskCodec.durableSuspension(77, 0, state.snapshot(), new IntegerValue(1));
      var codec = new LambdaMooV17Codec();
      Path path = directory.resolve(operation + ".db");
      codec.writeAtomic(path, world.snapshot(), List.of(task), List.of());
      Checkpoint read = codec.read(path);
      VmState resumed = restored((SuspendedTask) read.tasks().getFirst());
      resumed.resume(new IntegerValue(1));
      execute(resumed.snapshot().frames().getFirst().program(), resumed, read.world(), 77);
      assertEquals(VmState.Outcome.RETURNED, resumed.outcome(), operation);
      MooValue expected = operation.equals("recycle") ? new IntegerValue(0)
          : new ObjectValue(operation.equals("create") || operation.equals("recreate") ? 1 : 2);
      assertEquals(Optional.of(expected), resumed.returnValue(), operation);
    }
  }

  private static void execute(world.mongoose.banteng.bytecode.BytecodeProgram program,
      VmState state, WorldTxn root, long taskId) {
    BuiltinHosts hosts = BuiltinHosts.builder().build();
    BuiltinCatalog builtins = new BuiltinCatalog(hosts);
    MooVm vm = new MooVm();
    try (WorldTxn transaction = root.begin()) {
      vm.execute(program, state, transaction, builtins, hosts, taskId);
      while (state.outcome() == VmState.Outcome.PENDING_BUILTIN) {
        vm.authorizePendingBuiltin(state, transaction, builtins, hosts, taskId);
        vm.execute(program, state, transaction, builtins, hosts, taskId);
      }
      assertTrue(transaction.commit().isCommitted());
    }
  }

  private static VmState restored(SuspendedTask task) {
    List<VmSnapshot.Frame> frames = new ArrayList<>();
    for (int index = 0; index < task.activations().size(); index++) {
      var activation = task.activations().get(index);
      frames.addFirst(DurableTaskCodec.importActivation(activation,
          index == 0 ? task.rootActivationVector() : -1, index == 0));
    }
    VmSnapshot.Frame root = frames.getLast();
    return VmState.restore(new VmSnapshot(root.locals(), root.programmer(), root.verbLocation(),
        root.calledVerb(), root.fullVerbName(), frames, List.of(), List.of(), List.of(), List.of(),
        List.of(), List.of(), List.of(), VmState.Outcome.SUSPENDED, Optional.empty(), Optional.empty(),
        Optional.empty(), OptionalLong.empty(), Optional.empty(), OptionalDouble.of(0), false,
        Optional.empty(), task.taskLocal(), 10000, 0, 5000000000L, task.maxStackDepth(), root.player()));
  }
}
