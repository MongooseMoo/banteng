package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

final class WaifIndexedAssignmentTest {
  @TempDir Path temporary;

  @Test
  void readAndEverySetterStageKeepOwnerExpressionAndOnceOnlyCallsThroughV17Resume() throws Exception {
    List<String> cases = List.of(
        "result = w[\"key\"]; return {result == {1,2}, #1.events == {{\"_index\", {\"key\"}}}};",
        "result = (w[\"key\"] = 42); return {w == original && result == 42, #1.events == {{\"_set_index\", {\"key\",42}}}};",
        "result = (w[\"key\"][1] = 9); return {w == original && result == 9, #1.events == {{\"_index\",{\"key\"}}, {\"_set_index\",{\"key\",{9,2}}}}};",
        "#1.scalar = 1; result = (w[\"key\"] = 42); return {w == 17 && result == 42, #1.events == {{\"_set_index\",{\"key\",42}}}};",
        "#1.container = w; result = (#1.container[\"key\"][1] = 9); return {#1.container == original && result == 9, #1.events == {{\"_index\",{\"key\"}}, {\"_set_index\",{\"key\",{9,2}}}}};",
        "#1.scalar = 1; w = {w}; result = (w[1][\"key\"] = 42); return {w == {17} && result == 42, #1.events == {{\"_set_index\",{\"key\",42}}}};",
        "#1.lookup = inner; result = (w[\"key\"][\"other\"] = 9); return {w == original && result == 9, #1.events == {{\"_index\",{\"key\"}}, {\"_set_index\",{\"other\",9}}, {\"_set_index\",{\"key\",inner}}}};",
        "result = (w[\"key\"][1..1] = {9}); return {w == original && result == {9}, #1.events == {{\"_index\",{\"key\"}}, {\"_set_index\",{\"key\",{9,2}}}}};");
    for (boolean pause : List.of(false, true)) {
      for (String body : cases) {
        WorldTxn world = world(pause).begin();
        try {
          MooValue waif = world.createWaif(7, 1);
          MooValue inner = world.createWaif(7, 1);
          VmState state = new VmState(new TaskOrigin(Map.of("this", new ObjectValue(1),
              "player", new ObjectValue(1), "w", waif, "inner", inner),
              1, new ObjectValue(1), "drive", "drive", true), new TaskBudget(60_000, 5, 50));
          BytecodeProgram program = new MooCompiler().compile("original = w; " + body);
          run(program, state, world);
          int suspensions = 0;
          while (state.outcome() == VmState.Outcome.SUSPENDED) {
            VmSnapshot snapshot = state.snapshot();
            var durable = DurableTaskCodec.durableSuspension(12, 0, snapshot, new IntegerValue(0));
            Path dump = temporary.resolve("waif.db");
            var codec = new LambdaMooV17Codec();
            codec.writeAtomic(dump, world.snapshot(), List.of(durable), List.of());
            var restored = codec.read(dump);
            var task = (SuspendedTask) restored.tasks().getFirst();
            List<VmSnapshot.Frame> frames = new ArrayList<>();
            for (int index = 0; index < task.activations().size(); index++) {
              frames.add(DurableTaskCodec.importActivation(task.activations().get(index), -1, index == 0));
            }
            Collections.reverse(frames);
            for (int index = 0; index < frames.size(); index++) {
              assertEquals(snapshot.frames().get(index).calledVerb(), frames.get(index).calledVerb());
              assertEquals(snapshot.frames().get(index).fullVerbName(), frames.get(index).fullVerbName());
            }
            assertEquals(StringValue.of("changed"), frames.getFirst().locals().get("verb"));
            snapshot = withFrames(snapshot, frames);
            world.close();
            world = restored.world().begin();
            state = VmState.restore(snapshot);
            state.resume(new IntegerValue(0));
            run(program, state, world);
            assertTrue(++suspensions <= 3, body);
          }
          assertEquals(VmState.Outcome.RETURNED, state.outcome(), body);
          assertEquals(new ListValue(List.of(new IntegerValue(1), new IntegerValue(1))),
              state.returnValue().orElseThrow(), body);
          assertEquals(pause, suspensions > 0, body);
        } finally {
          world.close();
        }
      }
    }
  }

  @Test
  void stagedReplacementCostsMatchToastIndexsetStoreAndFreePushRefRangeTempOpcodes() {
    for (String source : List.of("l = {1,2}; result = (l[1] = 9); return result;",
        "l = {1,2}; result = (l[1..1] = {9}); return result;",
        "l = {{1,2}}; result = (l[1][1] = 9); return result;")) {
      try (WorldTxn world = world(false).begin()) {
        var program = new MooCompiler().compile(source);
        var state = new VmState(new TaskOrigin(Map.of(), 1, new ObjectValue(1), "drive", "drive", true),
            new TaskBudget(60_000, 5, 50));
        run(program, state, world);
        assertEquals(VmState.Outcome.RETURNED, state.outcome());
        assertEquals(source.contains("{{") ? 59_993 : 59_995, state.snapshot().remainingTicks(), source);
      }
    }
  }

  @Test
  void originalAnonymousRhsRemainsReachableWhenSetterDropsArgsAndSuspends() throws Exception {
    long connection = -51;
    try (SchedulerTestHarness harness = SchedulerTestHarness.open(1, connection)) {
      long player = harness.runtime.connectionPlayer(connection).orElseThrow();
      try (WorldTxn world = harness.root.begin()) {
        long type = world.createObject(-1, player).id();
        world.addVerb(type, ":_set_index", player, new VerbPerms(12), ArgSpec.THIS, -1, ArgSpec.THIS);
        assertTrue(world.setVerbCode(type, 0, "args = {}; suspend(0.01); return 17;").isOk());
        assertTrue(world.addProperty(0, "waif_container", world.createWaif(type, player), player, new PropertyPerms(3)).isOk());
        assertTrue(world.addProperty(0, "waif_rhs_valid", new IntegerValue(0), player, new PropertyPerms(3)).isOk());
        assertTrue(world.commit().isCommitted());
      }
      harness.runtime.executeLine(connection,
          "; w = #0.waif_container; rhs = create(#-1, player, 1); result = (w[\"key\"] = rhs); #0.waif_rhs_valid = valid(result) && w == 17; return 1;");
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      MooValue valid;
      do {
        try (WorldTxn world = harness.root.begin()) {
          valid = world.readObjectProperty(0, "waif_rhs_valid").orElseThrow();
        }
        Thread.onSpinWait();
      } while (valid.equals(new IntegerValue(0)) && System.nanoTime() < deadline);
      assertEquals(new IntegerValue(1), valid);
    }
  }

  private static void run(BytecodeProgram program, VmState state, WorldTxn world) {
    var catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    var vm = new MooVm();
    vm.execute(program, state, world, catalog, catalog.hosts(), 1);
    while (state.outcome() == VmState.Outcome.PENDING_BUILTIN) {
      vm.authorizePendingBuiltin(state, world, catalog, catalog.hosts(), 1);
      vm.execute(program, state, world, catalog, catalog.hosts(), 1);
    }
  }

  private static WorldTxn world(boolean pause) {
    String handler = "name = verb; verb = \"changed\"; " + (pause ? "suspend(0); " : "")
        + "#1.events = {@#1.events, {name,args}}; return name == \"_index\" ? #1.lookup | #1.scalar ? 17 | this;";
    List<WorldProperty> properties = List.of(
        property("events", new ListValue(List.of())), property("scalar", new IntegerValue(0)),
        property("container", new IntegerValue(0)),
        property("lookup", new ListValue(List.of(new IntegerValue(1), new IntegerValue(2)))));
    WorldObject wizard = new WorldObject(1, "wizard", 6, 1, -1, -1, List.of(), List.of(), List.of(), properties);
    WorldObject type = new WorldObject(7, "waif class", 0, 1, -1, -1, List.of(), List.of(),
        List.of(verb(":_index :_read_alias", handler), verb(":_set_index :_write_alias", handler)), List.of());
    return new WorldTxn(List.of(1L), List.of(wizard, type));
  }

  private static WorldProperty property(String name, MooValue value) {
    return new WorldProperty(name, value, 1, new PropertyPerms(3), false, true);
  }

  private static WorldVerb verb(String name, String source) {
    return new WorldVerb(name, 1, new VerbPerms(12), ArgSpec.THIS, -1, ArgSpec.THIS, source);
  }

  private static VmSnapshot withFrames(VmSnapshot s, List<VmSnapshot.Frame> frames) {
    return new VmSnapshot(frames.getLast().locals(), s.initialProgrammer(), s.initialVerbLocation(),
        s.initialCalledVerb(), s.initialFullVerbName(), frames, s.output(), s.connectionOptionRequests(),
        s.bootPlayerTargets(), s.forcedInputRequests(), s.notificationRequests(), s.checkpointRequests(),
        s.anonymousCollectionDeferrals(), s.outcome(), s.returnValue(), s.pendingError(), s.uncaughtError(),
        s.switchedPlayer(), s.forkRequest(), s.suspensionDelaySeconds(), s.awaitingHostResult(),
        s.pendingBuiltin(), s.taskLocal(), s.remainingTicks(), s.elapsedCpuNanos(), s.remainingCpuNanos(),
        s.maxStackDepth(), s.initialPlayer());
  }
}
