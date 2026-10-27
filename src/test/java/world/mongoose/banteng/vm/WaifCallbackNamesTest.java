package world.mongoose.banteng.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.VerbPerms;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

final class WaifCallbackNamesTest {
  @Test
  void rootWaifRecycleRetainsRawLocalAndActivationNameAcrossResume() {
    WorldVerb callback = verb(":recycle", "before = verb; suspend(0); return {before, #1:observe(), verb};");
    try (WorldTxn world = world(List.of(callback)).begin()) {
      var waif = world.createWaif(7, 1);
      VmState state = new VmState(new TaskOrigin(Map.of(), 1, new ObjectValue(7),
          ":recycle", ":recycle", true), new TaskBudget(60000, 5, 50));
      var context = new MooVm.RootVerbCall(waif, -1, -1, ":recycle", new ListValue(List.of()),
          "", java.util.Optional.empty());
      org.junit.jupiter.api.Assertions.assertTrue(MooVm.callVerb(state, waif,
          new WorldTxn.ResolvedVerb(callback, new ObjectValue(7)), context.arguments(), context.verb(),
          MooVm.VerbCallKind.ROOT, java.util.Optional.empty(), java.util.Optional.empty(),
          java.util.Optional.of(context), world));
      var builtins = new BuiltinCatalog(BuiltinHosts.builder().build());
      var vm = new MooVm();
      vm.execute(callback.program(), state, world, builtins, builtins.hosts(), 1);
      assertEquals(VmState.Outcome.SUSPENDED, state.outcome());
      assertEquals(StringValue.of(":recycle"), state.snapshot().frames().getFirst().locals().get("verb"));
      assertEquals(":recycle", state.snapshot().frames().getFirst().calledVerb());
      state = VmState.restore(state.snapshot());
      state.resume(new IntegerValue(0));
      vm.execute(callback.program(), state, world, builtins, builtins.hosts(), 1);
      assertEquals(VmState.Outcome.RETURNED, state.outcome());
      assertEquals(new ListValue(List.of(StringValue.of(":recycle"),
          new ListValue(List.of(StringValue.of(":recycle"), StringValue.of(":recycle"))), StringValue.of(":recycle"))),
          state.returnValue().orElseThrow());
    }
  }

  @Test
  void waifLocalNamesAndActivationNamesSurviveDispatchAndResume() {
    for (String operation : List.of("waif[1]", "(waif[1] = 42)", "waif:probe()", "waif:(\":probe\")()")) {
      String calledName = operation.contains("probe") ? ":probe"
          : operation.contains("42") ? ":_set_index" : ":_index";
      for (boolean suspend : List.of(false, true)) {
        String handler = "before = verb; verb = \"changed\"; "
            + (suspend ? "suspend(0); " : "")
            + "#1.seen = {before, #1:observe()}; return this;";
        WorldTxn root = world(List.of(
            verb(":_index", handler), verb(":_set_index", handler), verb(":probe", handler)));
        try (WorldTxn world = root.begin()) {
          var waif = world.createWaif(7, 1);
          VmState state = new VmState(new TaskOrigin(
              Map.of("this", new ObjectValue(1), "player", new ObjectValue(1), "waif", waif,
                  "verb", StringValue.of("driver")), 1, new ObjectValue(1), "driver", "driver", true),
              new TaskBudget(60_000, 5, 50));
          var program = new MooCompiler().compile("ignored = " + operation + "; return #1.seen;");
          BuiltinCatalog builtins = new BuiltinCatalog(BuiltinHosts.builder().build());
          MooVm vm = new MooVm();
          vm.execute(program, state, world, builtins, builtins.hosts(), 1);
          if (suspend) {
            assertEquals(VmState.Outcome.SUSPENDED, state.outcome(), operation);
            var snapshot = state.snapshot();
            assertEquals(calledName, snapshot.frames().getFirst().calledVerb(), operation);
            assertEquals(StringValue.of("changed"), snapshot.frames().getFirst().locals().get("verb"), operation);
            state = VmState.restore(snapshot);
            state.resume(new IntegerValue(0));
            vm.execute(program, state, world, builtins, builtins.hosts(), 1);
          }
          assertEquals(VmState.Outcome.RETURNED, state.outcome(), operation);
          assertEquals(new ListValue(List.of(StringValue.of(calledName.substring(1)),
              new ListValue(List.of(StringValue.of(calledName), StringValue.of(calledName))))),
              state.returnValue().orElseThrow(), operation);
        }
      }
    }
  }

  @Test
  void callersUsesActivationNameAfterOrdinaryVerbLocalIsAssigned() {
    WorldTxn root = world(List.of());
    VmState state = new VmState(new TaskOrigin(
        Map.of("this", new ObjectValue(1), "player", new ObjectValue(1), "verb", StringValue.of("driver")),
        1, new ObjectValue(1), "driver", "driver", true), new TaskBudget(60_000, 5, 50));
    var program = new MooCompiler().compile("verb = \"changed\"; return #1:observe();");
    BuiltinCatalog builtins = new BuiltinCatalog(BuiltinHosts.builder().build());
    try (WorldTxn world = root.begin()) {
      new MooVm().execute(program, state, world, builtins, builtins.hosts(), 1);
    }
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new ListValue(List.of(StringValue.of("driver"), StringValue.of("driver"))),
        state.returnValue().orElseThrow());
  }

  private static WorldTxn world(List<WorldVerb> handlers) {
    WorldObject wizard = new WorldObject(1, "wizard", 4, 1, -1, -1, List.of(), List.of(),
        List.of(verb("observe", "return {callers()[1][2], callers(1)[1][2]};")),
        List.of(new WorldProperty("seen", new IntegerValue(0), 1, new PropertyPerms(3), false, true)));
    WorldObject waifClass = new WorldObject(7, "waif class", 0, 1, -1, -1, List.of(), List.of(), handlers, List.of());
    return new WorldTxn(List.of(1L), List.of(wizard, waifClass));
  }

  private static WorldVerb verb(String name, String source) {
    return new WorldVerb(name, 1, new VerbPerms(12), ArgSpec.NONE, -1, ArgSpec.NONE, source);
  }
}
