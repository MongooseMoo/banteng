package world.mongoose.banteng.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.VerbPerms;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldTxn.ResolvedVerb;
import world.mongoose.banteng.world.WorldVerb;
import org.junit.jupiter.api.Test;

final class CompiledVerbTest {
  @Test
  void setVerbCodeStoresInstructionLinesFromCanonicalSource() {
    BuiltinCatalog catalog = new BuiltinCatalog(BuiltinHosts.builder().build());
    BytecodeProgram install = new MooCompiler().compile(
        "return set_verb_code(#1, 1, {\"x = 1; return x;\"});");
    try (WorldTxn transaction = world(verb("work", "return 0;")).begin()) {
      VmState state = new VmState(
          new TaskOrigin(Map.of(), 1, new ObjectValue(-1), "", "", true),
          new TaskBudget(60_000, 5, 50));
      new MooVm().execute(install, state, transaction, catalog, catalog.hosts(), 1);
      assertEquals(VmState.Outcome.RETURNED, state.outcome());
      assertEquals(new ListValue(List.of()), state.returnValue().orElseThrow());
      WorldVerb installed = transaction.verb(1, 0).orElseThrow();
      assertEquals("x = 1;\nreturn x;", installed.programSource());
      BytecodeProgram reloaded = new MooCompiler().compile(installed.programSource());
      assertEquals(reloaded.instructions().stream().map(BytecodeProgram.Instruction::sourceLine).toList(),
          installed.program().instructions().stream().map(BytecodeProgram.Instruction::sourceLine).toList());
      assertTrue(installed.program().instructions().stream()
          .anyMatch(instruction -> instruction.opcode() == BytecodeProgram.Opcode.RETURN
              && instruction.sourceLine() == 2));
    }
  }

  @Test
  void codeInstallationReusesValidatedProgramAndMetadataEditsKeepIt() {
    WorldVerb original = verb("work", "  return 1; /* exact spelling */");
    WorldTxn owner = world(original);
    assertEquals("  return 1; /* exact spelling */", original.programSource());
    BytecodeProgram replacement = new MooCompiler().compileResult("return 2;").program().orElseThrow();
    try (WorldTxn transaction = owner.begin()) {
      assertTrue(transaction.setVerbCode(1, 0, " return 2; ", replacement).isOk());
      assertTrue(transaction.setVerbInfo(1, 0, "renamed", 1, new VerbPerms(12)).isOk());
      assertTrue(transaction.setVerbArgs(1, 0, ArgSpec.ANY, -2, ArgSpec.THIS).isOk());
      WorldVerb edited = transaction.verb(1, 0).orElseThrow();
      assertSame(replacement, edited.program());
      assertEquals(" return 2; ", edited.programSource());
      assertTrue(transaction.commit().isCommitted());
    }
    try (WorldTxn transaction = owner.begin()) {
      assertSame(replacement, transaction.resolveVerb(1, "renamed", true).orElseThrow().verb().program());
    }
  }

  @Test
  void cachedResolutionReadsCodeFromEachTransactionSnapshot() {
    WorldVerb original = verb("work", "return 1;");
    WorldTxn owner = world(original);
    BytecodeProgram replacement = new MooCompiler().compile("return 2;");
    try (WorldTxn old = owner.begin()) {
      assertSame(original.program(), old.resolveVerb(1, "work", true).orElseThrow().verb().program());
      try (WorldTxn writer = owner.begin()) {
        assertTrue(writer.setVerbCode(1, 0, "return 2;", replacement).isOk());
        assertTrue(writer.commit().isCommitted());
      }
      try (WorldTxn current = owner.begin()) {
        assertSame(replacement, current.resolveVerb(1, "work", true).orElseThrow().verb().program());
      }
      assertSame(original.program(), old.resolveVerb(1, "work", true).orElseThrow().verb().program());
      assertFalse(old.commit().isCommitted());
    }
  }

  @Test
  void cachedInheritedLookupReplaysReceiverReads() {
    WorldVerb inherited = verb("work", "return 1;");
    WorldTxn owner = new WorldTxn(List.of(), List.of(
        new WorldObject(1, "parent", 4, 1, -1, -1, List.of(), List.of(2L), List.of(inherited), List.of()),
        new WorldObject(2, "child", 4, 1, -1, 1, List.of(), List.of(), List.of(), List.of())));
    try (WorldTxn warm = owner.begin()) {
      assertEquals(new ObjectValue(1), warm.resolveVerb(2, "work", true).orElseThrow().location());
    }
    try (WorldTxn reader = owner.begin()) {
      assertEquals(new ObjectValue(1), reader.resolveVerb(2, "work", true).orElseThrow().location());
      try (WorldTxn writer = owner.begin()) {
        writer.addVerb(2, "work", 1, new VerbPerms(12), ArgSpec.NONE, -1, ArgSpec.NONE);
        assertTrue(writer.commit().isCommitted());
      }
      assertFalse(reader.commit().isCommitted());
    }
  }

  @Test
  void normalAndPassKeepTheirDifferentLocalPolicies() {
    WorldVerb target = verb("work", "return {this, player, caller, args, argstr, dobjstr, subject};");
    ResolvedVerb resolution = new ResolvedVerb(target, new ObjectValue(1));
    Map<String, MooValue> locals = Map.of(
        "this", new ObjectValue(3), "player", new ObjectValue(4),
        "verb", StringValue.of("work"), "argstr", StringValue.of("raw command"),
        "dobjstr", StringValue.of("direct"), "subject", new IntegerValue(99), "caller_only", new IntegerValue(42));
    for (MooVm.VerbCallKind kind : List.of(MooVm.VerbCallKind.NORMAL, MooVm.VerbCallKind.PASS)) {
      VmState state = new VmState(new TaskOrigin(locals, 3, TaskOrigin.inferVerbLocation(locals),
        TaskOrigin.calledVerb(locals), TaskOrigin.calledVerb(locals), true), new TaskBudget(60_000, 5, 50));
      state.ensureRoot(new MooCompiler().compile("return {subject, caller_only};"));
      ListValue args = new ListValue(List.of(new IntegerValue(7)));
      assertTrue(MooVm.callVerb(state, new ObjectValue(3), resolution, args, "work", kind,
          Optional.empty(), Optional.empty(), Optional.empty(), world(target)));
      assertSame(target.program(), state.currentFrame().program);
      assertEquals(StringValue.of("raw command"), state.currentFrame().locals[10]);
      assertEquals(StringValue.of("direct"), state.currentFrame().locals[12]);
      assertEquals(kind == MooVm.VerbCallKind.PASS,
          state.currentFrame().namedLocals().containsKey("subject"));
      assertEquals(new ObjectValue(3), state.currentFrame().locals[7]);
      assertEquals(args, state.currentFrame().locals[9]);
      assertFalse(state.currentFrame().namedLocals().containsKey("caller_only"));
    }
  }

  @Test
  void hookOriginsKeepNamedLifecycleAndCallerPolicies() {
    WorldVerb target = verb("hook", "return 7;");
    for (MooVm.VerbCallKind kind : List.of(MooVm.VerbCallKind.HOOK_RECYCLE,
        MooVm.VerbCallKind.HOOK_INITIALIZE, MooVm.VerbCallKind.HOOK_ACCEPT,
        MooVm.VerbCallKind.HOOK_EXIT, MooVm.VerbCallKind.HOOK_ENTER)) {
      VmState state = new VmState(new TaskOrigin(Map.of("this", new ObjectValue(3)), 3, TaskOrigin.inferVerbLocation(Map.of("this", new ObjectValue(3))),
        TaskOrigin.calledVerb(Map.of("this", new ObjectValue(3))), TaskOrigin.calledVerb(Map.of("this", new ObjectValue(3))), true), new TaskBudget(60_000, 5, 50));
      state.ensureRoot(new MooCompiler().compile("return 0;"));
      state.currentFrame().locals[6] = new ObjectValue(8);
      Optional<BuiltinResult.Move> move = switch (kind) {
        case HOOK_ACCEPT, HOOK_EXIT, HOOK_ENTER -> Optional.of(new BuiltinResult.Move(4, 5, 6));
        default -> Optional.empty();
      };
      assertTrue(MooVm.callVerb(state, new ObjectValue(2),
          new ResolvedVerb(target, new ObjectValue(1)), new ListValue(List.of()), "hook", kind,
          Optional.of(new ObjectValue(9)), move, Optional.empty(), world(target)));
      VmState.Frame frame = state.currentFrame();
      assertEquals(kind == MooVm.VerbCallKind.HOOK_RECYCLE ? new ObjectValue(8) : new ObjectValue(3),
          frame.locals[7]);
      assertEquals(kind == MooVm.VerbCallKind.HOOK_RECYCLE, frame.recycleTarget.isPresent());
      assertEquals(kind == MooVm.VerbCallKind.HOOK_INITIALIZE, frame.createReturnOverride.isPresent());
      assertEquals(move.isPresent(), frame.moveObject.isPresent());
      if (move.isPresent()) {
        assertEquals(4, frame.moveObject.orElseThrow());
        assertEquals(5, frame.moveDestination.orElseThrow());
        assertEquals(6, frame.movePosition.orElseThrow());
      }
    }
  }

  @Test
  void rootEntryRetainsExplicitContextLimitsAndStoredProgram() {
    WorldVerb target = verb("work", "return {this, player, caller, args, argstr, dobj, dobjstr, prepstr, iobj, iobjstr};");
    VmState state = new VmState(new TaskOrigin(Map.of(), 1, new ObjectValue(1),
        "", "", true), new TaskBudget(1234, 5, 71));
    ListValue args = new ListValue(List.of(new IntegerValue(6)));
    MooVm.RootVerbCall call = new MooVm.RootVerbCall(new ObjectValue(3), 4, 5, "work", args,
        "raw command", Optional.of(new MooVm.CommandArguments(8, "direct", "with", 9, "indirect")));
    assertTrue(MooVm.callVerb(state, call.receiver(), new ResolvedVerb(target, new ObjectValue(1)),
        args, call.verb(), MooVm.VerbCallKind.ROOT,
        Optional.empty(), Optional.empty(), Optional.of(call), world(target)));
    assertSame(target.program(), state.currentFrame().program);
    assertEquals(VmState.ReturnMode.ROOT, state.currentFrame().returnMode);
    state.snapshot().initialLocals().forEach((name, value) ->
        assertEquals(value, state.currentFrame().namedLocals().get(name)));
    assertEquals(1234, state.budget().remainingTicks());
    try (WorldTxn transaction = world(target).begin()) {
      new MooVm().execute(target.program(), state, transaction,
          new BuiltinCatalog(BuiltinHosts.builder().build()), BuiltinHosts.builder().build(), 1);
    }
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new ListValue(List.of(new ObjectValue(3), new ObjectValue(4), new ObjectValue(5),
        args, StringValue.of("raw command"), new ObjectValue(8), StringValue.of("direct"),
        StringValue.of("with"), new ObjectValue(9), StringValue.of("indirect"))),
        state.returnValue().orElseThrow());
  }

  private static WorldVerb verb(String name, String source) {
    return new WorldVerb(name, 1, new VerbPerms(12), ArgSpec.NONE, -1, ArgSpec.NONE, source);
  }

  private static WorldTxn world(WorldVerb verb) {
    return new WorldTxn(List.of(), List.of(new WorldObject(1, "subject", 4, 1, -1, -1,
        List.of(), List.of(), List.of(verb), List.of())));
  }
}
