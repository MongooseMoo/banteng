package world.mongoose.banteng.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.VerbPerms;
import world.mongoose.banteng.world.WorldTxn.ResolvedVerb;
import world.mongoose.banteng.world.WorldVerb;

final class CallbackCommandLocalsTest {
  private static final WorldVerb CALLBACK = new WorldVerb("callback", 7, new VerbPerms(12),
      ArgSpec.NONE, -1, ArgSpec.NONE,
      "return {this, player, caller, verb, args, argstr, dobj, dobjstr, prepstr, iobj, iobjstr};");
  private static final Map<String, MooValue> COMMAND = Map.of(
      "argstr", StringValue.of("kept input"), "dobj", new ObjectValue(11),
      "dobjstr", StringValue.of("direct"), "prepstr", StringValue.of("with"),
      "iobj", new ObjectValue(12), "iobjstr", StringValue.of("indirect"));

  @Test
  void freshServerCallbackHasNothingAndEmptyCommandFields() {
    VmState state = state(Map.of());
    var root = new MooVm.RootVerbCall(0, -1, -1, "server_started", new ListValue(List.of()), "");
    assertTrue(MooVm.callVerb(state, new ObjectValue(0), new ResolvedVerb(CALLBACK, new ObjectValue(0)),
        root.arguments(), root.verb(), MooVm.VerbCallKind.ROOT, Optional.empty(), Optional.empty(), Optional.of(root), new world.mongoose.banteng.world.WorldTxn(List.of(), List.of())));
    Map<String, MooValue> locals = state.snapshot().frames().getFirst().locals();
    assertEquals(new ObjectValue(-1), locals.get("dobj"));
    assertEquals(new ObjectValue(-1), locals.get("iobj"));
    for (String field : List.of("argstr", "dobjstr", "prepstr", "iobjstr")) {
      assertEquals(StringValue.of(""), locals.get(field), field);
    }
  }

  @Test
  void rootInputCommandKeepsItsExplicitParsedFields() {
    VmState state = state(Map.of());
    var root = new MooVm.RootVerbCall(new ObjectValue(0), 8, 8, "command", new ListValue(List.of()),
        "kept input", Optional.of(new MooVm.CommandArguments(11, "direct", "with", 12, "indirect")));
    assertTrue(MooVm.callVerb(state, root.receiver(), new ResolvedVerb(CALLBACK, new ObjectValue(0)),
        root.arguments(), root.verb(), MooVm.VerbCallKind.ROOT, Optional.empty(), Optional.empty(), Optional.of(root), new world.mongoose.banteng.world.WorldTxn(List.of(), List.of())));
    assertCommandFields(state);
  }

  @Test
  void nestedCallbackKindsInheritCommandFieldsAcrossSnapshotRestoration() {
    for (MooVm.VerbCallKind kind : List.of(MooVm.VerbCallKind.NORMAL, MooVm.VerbCallKind.PROTECTED,
        MooVm.VerbCallKind.HOOK_ACCEPT, MooVm.VerbCallKind.HOOK_EXIT, MooVm.VerbCallKind.HOOK_ENTER,
        MooVm.VerbCallKind.HOOK_RECYCLE, MooVm.VerbCallKind.HOOK_ANONYMOUS_RECYCLE,
        MooVm.VerbCallKind.HOOK_INITIALIZE)) {
      var locals = new java.util.LinkedHashMap<>(COMMAND);
      locals.put("this", new ObjectValue(20));
      locals.put("player", new ObjectValue(21));
      VmState state = state(locals);
      state.ensureRoot(new MooCompiler().compile("return {argstr, dobj, dobjstr, prepstr, iobj, iobjstr};"));
      MooValue receiver = kind == MooVm.VerbCallKind.HOOK_ANONYMOUS_RECYCLE
          ? new AnonymousObjectValue() : new ObjectValue(30);
      assertTrue(MooVm.callVerb(state, receiver, new ResolvedVerb(CALLBACK, new ObjectValue(31)),
          new ListValue(List.of()), "callback", kind, Optional.of(receiver),
          Optional.of(new BuiltinResult.Move(30, 31, 0)), Optional.empty(),
          new world.mongoose.banteng.world.WorldTxn(List.of(), List.of())), kind.name());
      assertCommandFields(state);
      assertCommandFields(VmState.restore(state.snapshot()));
      assertEquals(receiver, state.snapshot().frames().getFirst().receiver());
      assertEquals(new ObjectValue(31), state.snapshot().frames().getFirst().verbLocation());
    }
  }

  private static VmState state(Map<String, MooValue> locals) {
    return new VmState(new TaskOrigin(locals, 8, new ObjectValue(20), "driver", "driver", true),
        new TaskBudget(1000, 5, 50));
  }

  private static void assertCommandFields(VmState state) {
    Map<String, MooValue> locals = state.snapshot().frames().getFirst().locals();
    COMMAND.forEach((field, expected) -> assertEquals(expected, locals.get(field), field));
  }
}
