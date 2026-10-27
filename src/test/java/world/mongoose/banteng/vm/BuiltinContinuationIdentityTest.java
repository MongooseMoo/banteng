package world.mongoose.banteng.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.persistence.DurableTaskCodec;
import world.mongoose.banteng.persistence.LambdaMooV17Codec;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.SuspendedTask;
import world.mongoose.banteng.world.VerbPerms;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

final class BuiltinContinuationIdentityTest {
  @TempDir Path temporary;
  private static final String CAPTURE =
      "suspend(0); #1.seen = {callers(), callers(1), #1:observe()}; return 99;";

  @Test
  void realInitializeProducersKeepIdentityThroughSnapshotAndPermanentDumpResume() throws Exception {
    for (String operation : List.of("create(#2)", "recreate(#3,#2)", "create(#2,1)",
        "call_function(\"recreate\",#3,#2)", "call_function(\"call_function\",\"create\",#2)")) {
      boolean anonymous = operation.equals("create(#2,1)");
      String expected = operation.startsWith("call_function") ? "call_function"
          : operation.startsWith("recreate") ? "recreate" : "create";
      try (WorldTxn world = world(List.of(verb("initialize", CAPTURE)), List.of(), false).begin()) {
        var program = new MooCompiler().compile("recycle(#3); ignored = " + operation + "; return #1.seen;");
        VmState state = state();
        run(program, state, world);
        assertEquals(VmState.Outcome.SUSPENDED, state.outcome(), operation);
        var snapshot = state.snapshot();
        var callback = snapshot.frames().getFirst();
        ListValue stackRow = callback.builtinStackFrame(snapshot.frames().get(1), true, true).orElseThrow();
        assertEquals(StringValue.of(expected), stackRow.elements().get(1));
        assertEquals(new IntegerValue(2), stackRow.elements().get(5));
        assertTrue(stackRow.elements().get(6) instanceof MooValue.MapValue);
        if (!anonymous) {
          var durable = DurableTaskCodec.durableSuspension(12, 0, snapshot, 1, new IntegerValue(0));
          Path dump = temporary.resolve("callbacks.db");
          var codec = new LambdaMooV17Codec();
          codec.writeAtomic(dump, world.snapshot(), List.of(durable), List.of());
          var restored = (SuspendedTask) codec.read(dump).tasks().getFirst();
          assertEquals(durable.activations().getLast().builtinContinuation(),
              restored.activations().getLast().builtinContinuation());
          List<VmSnapshot.Frame> frames = new ArrayList<>();
          for (int index = 0; index < restored.activations().size(); index++) {
            frames.add(DurableTaskCodec.importActivation(restored.activations().get(index), -1, index == 0));
          }
          Collections.reverse(frames);
          assertEquals(callback.builtinContinuation(), frames.getFirst().builtinContinuation());
          snapshot = withFrames(snapshot, frames);
        }
        state = VmState.restore(snapshot);
        state.resume(new IntegerValue(0));
        run(program, state, world);
        assertEquals(VmState.Outcome.RETURNED, state.outcome(), operation);
        ListValue seen = (ListValue) state.returnValue().orElseThrow();
        assertEquals(row(expected), ((ListValue) seen.elements().get(0)).elements().getFirst(), operation);
        assertEquals(StringValue.of("drive"), ((ListValue) ((ListValue) seen.elements().get(0)).elements().get(1)).elements().get(1));
        assertEquals(StringValue.of("initialize"), ((ListValue) seen.elements().get(2)).elements().get(1));
        assertEquals(new IntegerValue(2), ((ListValue) ((ListValue) seen.elements().get(1)).elements().getFirst()).elements().get(5));
      }
    }
  }

  @Test
  void recycleExitfuncAndMovePhasesKeepOuterIdentityAfterEverySuspension() {
    for (String operation : List.of("recycle(#3)", "move(#3,#2)", "call_function(\"move\",#3,#2)")) {
      String source = "suspend(0); #1.seen = {@#1.seen, {callers(1)[1][2], callers(1)[1][6]}}; return 1;";
      try (WorldTxn world = world(List.of(verb("accept", source), verb("enterfunc", source)),
          List.of(verb("exitfunc", source)), true).begin()) {
        var program = new MooCompiler().compile(operation + "; return #1.seen;");
        VmState state = state();
        run(program, state, world);
        while (state.outcome() == VmState.Outcome.SUSPENDED) {
          state = VmState.restore(state.snapshot());
          state.resume(new IntegerValue(0));
          run(program, state, world);
        }
        String name = operation.startsWith("call_function") ? "call_function"
            : operation.startsWith("recycle") ? "recycle" : "move";
        List<MooValue> expected = new ArrayList<>();
        for (long counter : operation.startsWith("recycle") ? List.of(2L) : List.of(2L, 3L, 4L)) {
          expected.add(new ListValue(List.of(StringValue.of(name), new IntegerValue(counter))));
        }
        assertEquals(VmState.Outcome.RETURNED, state.outcome(), operation);
        assertEquals(new ListValue(expected), state.returnValue().orElseThrow(), operation);
      }
    }
  }

  private static ListValue row(String name) {
    return new ListValue(List.of(new ObjectValue(-1), StringValue.of(name), new ObjectValue(-1),
        new ObjectValue(-1), new ObjectValue(1)));
  }

  @Test
  void evalRowsKeepTheirOuterNameAndCaughtErrorsIncludeBuiltinRows() {
    for (boolean reflected : List.of(false, true)) {
      String name = reflected ? "call_function" : "eval";
      try (WorldTxn world = world(List.of(), List.of(), false).begin()) {
        String body = "\"suspend(0); return callers(1);\"";
        var program = new MooCompiler().compile("return "
            + (reflected ? "call_function(\"eval\"," : "eval(") + body + ");");
        VmState state = state();
        run(program, state, world);
        assertEquals(VmState.Outcome.SUSPENDED, state.outcome());
        state = VmState.restore(state.snapshot());
        state.resume(new IntegerValue(0));
        run(program, state, world);
        var result = (ListValue) state.returnValue().orElseThrow();
        var rows = (ListValue) result.elements().get(1);
        var builtin = (ListValue) rows.elements().getFirst();
        assertEquals(StringValue.of(name), builtin.elements().get(1));
        assertEquals(new IntegerValue(2), builtin.elements().get(5));
      }
      try (WorldTxn world = world(List.of(verb("initialize", "raise(E_INVARG);")), List.of(), false).begin()) {
        String operation = reflected ? "call_function(\"recreate\",#3,#2)" : "create(#2)";
        var program = new MooCompiler().compile("recycle(#3); try ignored = " + operation
            + "; except failure (ANY) return failure[4]; endtry return {};");
        VmState state = state();
        run(program, state, world);
        assertEquals(VmState.Outcome.RETURNED, state.outcome());
        var trace = (ListValue) state.returnValue().orElseThrow();
        assertEquals(StringValue.of("initialize"), ((ListValue) trace.elements().get(0)).elements().get(1));
        var builtin = (ListValue) trace.elements().get(1);
        assertEquals(StringValue.of(reflected ? "call_function" : "create"), builtin.elements().get(1));
        assertEquals(new IntegerValue(2), builtin.elements().get(5));
        assertEquals(StringValue.of("drive"), ((ListValue) trace.elements().get(2)).elements().get(1));
      }
    }
  }

  @Test
  void failedCallbackPushDoesNotAttachItsContinuationToTheCatchingCaller() {
    try (WorldTxn world = world(List.of(verb("initialize", "return 0;")), List.of(), false).begin()) {
      var program = new MooCompiler().compile(
          "try ignored = #1:drive(); except (ANY) ignored = 0; endtry suspend(0); return 1;");
      VmState state = new VmState(new TaskOrigin(Map.of("this", new ObjectValue(1), "player", new ObjectValue(1)),
          1, new ObjectValue(1), "driver", "driver", true), new TaskBudget(60_000, 5, 2));
      run(program, state, world);
      assertEquals(VmState.Outcome.SUSPENDED, state.outcome());
      assertEquals(1, state.snapshot().frames().size());
      assertTrue(state.snapshot().frames().getFirst().builtinContinuation().isEmpty());
    }
  }

  private static VmState state() {
    return new VmState(new TaskOrigin(Map.of("this", new ObjectValue(1), "player", new ObjectValue(1),
        "verb", StringValue.of("drive")), 1, new ObjectValue(1), "drive", "drive", true),
        new TaskBudget(60_000, 5, 50));
  }

  private static void run(BytecodeProgram program, VmState state, WorldTxn world) {
    var builtins = new BuiltinCatalog(BuiltinHosts.builder().build());
    var vm = new MooVm();
    vm.execute(program, state, world, builtins, builtins.hosts(), 1);
    while (state.outcome() == VmState.Outcome.PENDING_BUILTIN) {
      vm.authorizePendingBuiltin(state, world, builtins, builtins.hosts(), 1);
      vm.execute(program, state, world, builtins, builtins.hosts(), 1);
    }
  }

  private static WorldTxn world(List<WorldVerb> handlers, List<WorldVerb> exit, boolean located) {
    var wizard = new WorldObject(1, "wizard", 6, 1, -1, -1, List.of(), List.of(),
        List.of(verb("observe", "return callers()[1];"), verb("drive", "return create(#2);")),
        List.of(new WorldProperty("seen", new ListValue(List.of()), 1, new PropertyPerms(3), false, true)));
    var parent = new WorldObject(2, "parent", 384, 1, -1, -1, List.of(), List.of(), handlers, List.of());
    var slot = new WorldObject(3, "slot", 0, 1, located ? 4 : -1, -1, List.of(), List.of(), List.of(), List.of());
    var old = new WorldObject(4, "old", 0, 1, -1, -1, located ? List.of(3L) : List.of(), List.of(), exit, List.of());
    return new WorldTxn(List.of(1L), List.of(wizard, parent, slot, old));
  }

  private static WorldVerb verb(String name, String source) {
    return new WorldVerb(name, 1, new VerbPerms(12), ArgSpec.NONE, -1, ArgSpec.NONE, source);
  }

  private static VmSnapshot withFrames(VmSnapshot s, List<VmSnapshot.Frame> frames) {
    return new VmSnapshot(s.initialLocals(), s.initialProgrammer(), s.initialVerbLocation(),
        s.initialCalledVerb(), s.initialFullVerbName(), frames, s.output(), s.connectionOptionRequests(),
        s.bootPlayerTargets(), s.forcedInputRequests(), s.notificationRequests(), s.checkpointRequests(),
        s.anonymousCollectionDeferrals(), s.outcome(), s.returnValue(), s.pendingError(), s.uncaughtError(),
        s.forkRequest(), s.suspensionDelaySeconds(), s.awaitingHostResult(),
        s.pendingBuiltin(), s.taskLocal(), s.remainingTicks(), s.elapsedCpuNanos(), s.remainingCpuNanos(), s.maxStackDepth());
  }
}
