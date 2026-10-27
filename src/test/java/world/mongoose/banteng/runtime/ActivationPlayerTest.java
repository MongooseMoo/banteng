package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.persistence.DurableTaskCodec;
import world.mongoose.banteng.persistence.LambdaMooV17Codec;
import world.mongoose.banteng.server.ConnectionRegistry;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.vm.MooVm;
import world.mongoose.banteng.vm.TaskBudget;
import world.mongoose.banteng.vm.TaskOrigin;
import world.mongoose.banteng.vm.VmSnapshot;
import world.mongoose.banteng.vm.VmState;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.SuspendedTask;
import world.mongoose.banteng.world.VerbPerms;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

final class ActivationPlayerTest {
  @TempDir Path temporary;

  @Test
  void ordinaryAndPassCallsUseWizardObjectLocalOrSavedPlayer() {
    for (boolean wizard : List.of(false, true)) {
      for (String assigned : List.of("#3", "\"not an object\"")) {
        for (boolean pass : List.of(false, true)) {
          String target = pass ? "player = " + assigned + "; return pass();" : "return player;";
          try (WorldTxn world = world(wizard, List.of(verb("target", target)),
              List.of(verb("target", "return player;"))).begin()) {
            var program = new MooCompiler().compile((pass ? "" : "player = " + assigned + "; ")
                + "return #2:target();");
            VmState state = state(60_000);
            run(program, state, world);
            assertEquals(VmState.Outcome.RETURNED, state.outcome());
            assertEquals(new ObjectValue(wizard && assigned.equals("#3") ? 3 : 1),
                state.returnValue().orElseThrow());
          }
        }
      }
    }
  }

  @Test
  void evalAlwaysUsesSavedPlayerEvenForWizardWithAlternateLocal() {
    try (WorldTxn world = world(true, List.of(), List.of()).begin()) {
      var program = new MooCompiler().compile("player = #3; return eval(\"return player;\");");
      VmState state = state(60_000);
      run(program, state, world);
      assertEquals(new ListValue(List.of(new IntegerValue(1), new ObjectValue(1))),
          state.returnValue().orElseThrow());
    }
  }

  @Test
  void initializeRowsAndDurableResumeKeepSavedPlayersSeparateFromLocals() throws Exception {
    try (WorldTxn world = world(true, List.of(verb("initialize",
        "player = #4; suspend(0); return 0;")), List.of()).begin()) {
      var program = new MooCompiler().compile("player = #3; ignored = create(#2); return player;");
      VmState state = state(60_000);
      run(program, state, world);
      VmSnapshot snapshot = state.snapshot();
      assertEquals(VmState.Outcome.SUSPENDED, snapshot.outcome());
      assertEquals(3, snapshot.frames().getFirst().player());
      assertEquals(new ObjectValue(4), snapshot.frames().getFirst().locals().get("player"));
      assertEquals(1, snapshot.frames().getLast().player());
      assertEquals(new ObjectValue(3), snapshot.frames().getLast().locals().get("player"));
      assertStackPlayers(snapshot, world);

      var durable = DurableTaskCodec.durableSuspension(12, 0, snapshot, new IntegerValue(0));
      assertEquals(1, durable.activations().getFirst().taskPlayer());
      assertEquals(3, durable.activations().getLast().taskPlayer());
      var codec = new LambdaMooV17Codec();
      Path dump = temporary.resolve("players.db");
      codec.writeAtomic(dump, world.snapshot(), List.of(durable), List.of());
      var restored = (SuspendedTask) codec.read(dump).tasks().getFirst();
      List<VmSnapshot.Frame> frames = new ArrayList<>();
      for (int index = 0; index < restored.activations().size(); index++) {
        frames.add(DurableTaskCodec.importActivation(restored.activations().get(index), -1, index == 0));
      }
      Collections.reverse(frames);
      snapshot = withFrames(snapshot, frames);
      assertStackPlayers(snapshot, world);
      state = VmState.restore(snapshot);
      state.resume(new IntegerValue(0));
      run(program, state, world);
      assertEquals(new ObjectValue(3), state.returnValue().orElseThrow());
    }
  }

  @Test
  void tracebackAndCallersUseSavedPlayerAfterLocalAssignment() {
    for (boolean error : List.of(false, true)) {
      String target = "player = #4; " + (error ? "raise(E_INVARG);" : "return callers();");
      try (WorldTxn world = world(true, List.of(verb("target", target)), List.of()).begin()) {
        var program = new MooCompiler().compile("player = #3; " + (error
            ? "try #2:target(); except e (ANY) return e[4]; endtry"
            : "return #2:target();"));
        VmState state = state(60_000);
        run(program, state, world);
        ListValue rows = (ListValue) state.returnValue().orElseThrow();
        if (error) assertEquals(new ObjectValue(3), row(rows, 0).elements().get(4));
        assertEquals(new ObjectValue(1), row(rows, error ? 1 : 0).elements().get(4));
      }
    }
  }

  @Test
  void forkRetainsSavedPlayerBeforeFirstInstructionAndQueuedDump() {
    try (WorldTxn world = world(true, List.of(), List.of()).begin()) {
      var program = new MooCompiler().compile("player = #3; fork (0) suspend(0); endfork return 0;");
      VmState state = state(60_000);
      run(program, state, world);
      assertEquals(VmState.Outcome.FORKED, state.outcome());
      state = VmState.restore(state.snapshot());
      VmSnapshot.Fork fork = state.continueAfterFork(new IntegerValue(12));
      assertEquals(1, fork.player());
      VmState child = new VmState(new TaskOrigin(fork.locals(), fork.programmer(), fork.verbLocation(),
          fork.calledVerb(), fork.fullVerbName(), fork.debug(), fork.player()), new TaskBudget(60000, 5, 50));
      VmSnapshot unstarted = child.snapshot();
      assertEquals(1, unstarted.initialPlayer());
      var queued = DurableTaskCodec.exportQueuedTask(12, 0, fork.program(), unstarted);
      assertEquals(1, queued.taskPlayer());
      assertEquals(new ObjectValue(3), queued.initialLocals().get("player"));
      child = VmState.restore(unstarted);
      run(fork.program(), child, world);
      assertEquals(1, child.snapshot().frames().getFirst().player());
      assertEquals(new ObjectValue(3), child.snapshot().frames().getFirst().locals().get("player"));
    }
  }

  @Test
  void nestedForkKeepsItsActualActivationPlayerRatherThanOuterTaskPlayer() {
    try (WorldTxn world = world(true, List.of(verb("target",
        "player = #4; fork (0) suspend(0); endfork return 0;")), List.of()).begin()) {
      var program = new MooCompiler().compile("player = #3; return #2:target();");
      VmState state = state(60_000);
      run(program, state, world);
      VmSnapshot.Fork fork = state.continueAfterFork(new IntegerValue(12));
      assertEquals(3, fork.player());
      assertEquals(new ObjectValue(4), fork.locals().get("player"));
    }
  }

  @Test
  void actualTimeoutPayloadUsesSourceLinesSavedPlayerAndConfiguredVariables() {
    WorldTxn owner = world(true, List.of(), List.of());
    try (MooRuntime runtime = SchedulerTestHarness.runtime(owner, new ConnectionRegistry(), 1,
        System::nanoTime, MooRuntime::resolveCanonicalConnectionName, Optional.empty(), Optional.empty(),
        Optional.empty(), List.of(), List.of()); WorldTxn world = owner.begin()) {
      var program = new MooCompiler().compile("player = #3;\nwhile (1) endwhile");
      VmState state = state(30);
      run(program, state, world);
      assertEquals(VmState.Outcome.ABORTED, state.outcome());
      var context = new MooRuntime.AttemptContext(world, 12, new java.util.LinkedHashMap<>(),
          new ConnectionRegistry(), 0, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
      var step = runtime.execute(context, MooRuntime.RuntimeContinuation.timeout(state.snapshot(), 1, List.of()),
          Optional.empty());
      var args = (ListValue) java.util.Objects.requireNonNull(
          step.snapshot().orElseThrow().frames().getFirst().locals().get("args"));
      var row = row((ListValue) args.elements().get(1), 0);
      assertEquals(new ObjectValue(1), row.elements().get(4));
      assertTrue(((IntegerValue) row.elements().get(5)).value() > 0);
      assertTrue(row.elements().get(6) instanceof MooValue.MapValue);
    }
  }

  private static void assertStackPlayers(VmSnapshot snapshot, WorldTxn world) {
    ListValue rows = TaskRegistry.projectStackFrames(snapshot.frames(), true, true, world, 1);
    assertEquals(new ObjectValue(3), row(rows, 0).elements().get(4));
    assertEquals(StringValue.of("create"), row(rows, 1).elements().get(1));
    assertEquals(new ObjectValue(3), row(rows, 1).elements().get(4));
    assertEquals(new ObjectValue(1), row(rows, 2).elements().get(4));
  }

  private static ListValue row(ListValue rows, int index) { return (ListValue) rows.elements().get(index); }

  private static VmState state(long ticks) {
    return new VmState(new TaskOrigin(Map.of("this", new ObjectValue(1), "player", new ObjectValue(1)),
        1, new ObjectValue(1), "drive", "drive", true), new TaskBudget(ticks, 5, 50));
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

  private static WorldTxn world(boolean wizard, List<WorldVerb> target, List<WorldVerb> inherited) {
    var system = new WorldObject(0, "system", 0, 1, -1, -1, List.of(), List.of(),
        List.of(verb("handle_task_timeout", "return 1;")),
        List.of(new WorldProperty("server_options", new ObjectValue(4), 1, new PropertyPerms(3), false, true)));
    var actor = new WorldObject(1, "actor", wizard ? 6 : 2, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    var parent = new WorldObject(2, "target", 384, 1, -1, 4, List.of(), List.of(), target,
        List.of(new WorldProperty("include_rt_vars", new IntegerValue(1), 1, new PropertyPerms(3), true, false)));
    var alternate = new WorldObject(3, "alternate", 0, 1, -1, -1, List.of(), List.of(), List.of(), List.of());
    var ancestor = new WorldObject(4, "ancestor", 0, 1, -1, -1, List.of(), List.of(2L), inherited,
        List.of(new WorldProperty("include_rt_vars", new IntegerValue(1), 1, new PropertyPerms(3), false, true)));
    return new WorldTxn(List.of(1L), List.of(system, actor, parent, alternate, ancestor));
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
        s.pendingBuiltin(), s.taskLocal(), s.remainingTicks(), s.elapsedCpuNanos(), s.remainingCpuNanos(),
        s.maxStackDepth(), s.initialPlayer());
  }
}
