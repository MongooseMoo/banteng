package world.mongoose.banteng.vm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.BytecodeProgram.Opcode;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;

final class SlotLocalsTest {
  @Test
  void missingVariableDiagnosticUsesFirstSourceSpelling() {
    BytecodeProgram program = new MooCompiler().compile(
        "if (0) E_ASSERT = 1; endif try return e_assert; except caught (ANY) return caught[2]; endtry");
    VmState state = new VmState(new TaskOrigin(Map.of(), 0,
        new world.mongoose.banteng.value.MooValue.ObjectValue(-1), "", "", true), new TaskBudget(60000, 5, 50));
    new PureVmFixture().execute(program, state);
    assertEquals(world.mongoose.banteng.value.MooValue.StringValue.of(
        "Variable not found: E_ASSERT"), state.returnValue().orElseThrow());
    BytecodeProgram clearedRead = new MooCompiler().compile("return E_ASSERT;");
    assertTrue(clearedRead.instructions().stream().anyMatch(i -> i.opcode() == Opcode.PUSH_CLEAR
        && i.text().orElseThrow().equals("E_ASSERT")));
    assertFalse(clearedRead.disassemble().contains("E_ASSERT"));
  }

  @Test
  void activationImportRestoresOldAbsentConstantsButPreservesExplicitClears() {
    BytecodeProgram program = new MooCompiler().compile("value = 9; suspend(60); return {value, INT};");
    VmState state = new VmState(new TaskOrigin(Map.of(), 0,
        new world.mongoose.banteng.value.MooValue.ObjectValue(-1), "", "", true), new TaskBudget(60000, 5, 50));
    new PureVmFixture().execute(program, state);
    var saved = world.mongoose.banteng.persistence.DurableTaskCodec.durableSuspension(
        1, 0, state.snapshot(0), 0, new IntegerValue(0)).activations().getFirst();
    Map<String, Optional<world.mongoose.banteng.value.MooValue>> locals = new LinkedHashMap<>();
    locals.put("VALUE", Optional.of(new IntegerValue(9)));
    locals.put("NUM", Optional.of(new IntegerValue(77)));
    locals.put("FLOAT", Optional.empty());
    var old = new world.mongoose.banteng.world.SuspendedActivation(
        saved.languageVersion(), saved.programSource(), locals, saved.operandStack(), saved.receiver(),
        saved.verbLocation(), saved.threadMode(), saved.taskPlayer(), saved.programmer(), saved.debug(),
        saved.verb(), saved.verbNames(), saved.temporary(), saved.programCounter(),
        saved.builtinFunctionCounter(), saved.errorCounter());
    var imported = world.mongoose.banteng.persistence.DurableTaskCodec.importActivation(old, -1, true,
        new world.mongoose.banteng.persistence.ToastV17ProgramLayout());
    assertEquals(new IntegerValue(9), imported.locals().get("value"));
    assertEquals(new IntegerValue(77), imported.locals().get("num"));
    assertEquals(new IntegerValue(0), imported.locals().get("int"));
    assertFalse(imported.locals().containsKey("float"));
  }

  @Test
  void suspendedForkExportsCatchBindingsAgainstItsSharedSlotTable() {
    BytecodeProgram enclosing = new MooCompiler().compile(
        "parent = 7; fork (0) try suspend(60); except caught (ANY) return caught; endtry endfork");
    BytecodeProgram fork = enclosing.forkVectors().getFirst();
    assertNotEquals(new MooCompiler().compile(fork.source()).variableNames().indexOf("caught"),
        fork.variableNames().indexOf("caught"));
    VmState state = new VmState(new TaskOrigin(Map.of("parent", new IntegerValue(7)), 0,
        new world.mongoose.banteng.value.MooValue.ObjectValue(-1), "", "", true), new TaskBudget(60000, 5, 50));
    new PureVmFixture().execute(fork, state);
    assertEquals(VmState.Outcome.SUSPENDED, state.outcome());
    assertDoesNotThrow(() -> world.mongoose.banteng.persistence.DurableTaskCodec.durableSuspension(
        1, 0, state.snapshot(0), 0, new IntegerValue(0)));
  }

  @Test
  void slotAnalysisDoesNotTreatFullWidthNumericLiteralsAsSlots() {
    BytecodeProgram program = new MooCompiler().compile("return {9223372036854775807, 1.5};");
    assertEquals(Long.MAX_VALUE, program.instructions().stream()
        .filter(i -> i.opcode() == Opcode.PUSH_INTEGER).findFirst().orElseThrow().operand().orElseThrow());
    assertEquals(Double.doubleToRawLongBits(1.5), program.instructions().stream()
        .filter(i -> i.opcode() == Opcode.PUSH_FLOAT).findFirst().orElseThrow().operand().orElseThrow());
  }

  @Test
  void compilerAssignsFixedSlotsAndOneInventoryToNestedForks() {
    BytecodeProgram program = new MooCompiler().compile(
        "Outer = 1; fork (0) inner = OUTER; fork (0) nested = inner; endfork endfork return outer;");
    assertEquals(List.of("num", "obj", "str", "list", "err", "player", "this", "caller", "verb",
        "args", "argstr", "dobj", "dobjstr", "prepstr", "iobj", "iobjstr", "int", "float", "map",
        "anon", "waif", "bool", "true", "false"), program.variableNames().subList(0, 24));
    assertEquals(program.variableNames(), program.forkVectors().getFirst().variableNames());
    assertEquals(program.variableNames(), program.forkVectors().getFirst().forkVectors().getFirst().variableNames());
    assertEquals(24, program.instructions().stream().filter(i -> i.opcode() == Opcode.STORE_LOCAL)
        .findFirst().orElseThrow().operand().orElseThrow());
    assertTrue(program.instructions().stream().filter(i -> i.opcode() == Opcode.STORE_LOCAL)
        .allMatch(i -> i.text().isEmpty()));
  }

  @Test
  void lastReadBeforeOverwriteClearsButBranchAndHandlerPathsDoNot() {
    BytecodeProgram straight = new MooCompiler().compile("x = 1; y = x; x = 2; return y;");
    assertTrue(straight.instructions().stream().anyMatch(i -> i.opcode() == Opcode.PUSH_CLEAR
        && i.operand().orElseThrow() == straight.variableNames().indexOf("x")));
    BytecodeProgram branching = new MooCompiler().compile("x = 1; y = x; if (y) x = 2; endif return x;");
    assertEquals(Opcode.LOAD_LOCAL, branching.instructions().get(4).opcode());
    BytecodeProgram guarded = new MooCompiler().compile("x = 1; try y = x; x = 2; except (ANY) return x; endtry return x;");
    assertTrue(guarded.instructions().stream().anyMatch(i -> i.opcode() == Opcode.LOAD_LOCAL
        && i.operand().orElseThrow() == guarded.variableNames().indexOf("x")));
  }

  @Test
  void arrayEnvironmentExecutesConstantsScatterNestedUpdatesAndNamedRoundTrip() {
    BytecodeProgram program = new MooCompiler().compile(
        "{x, @rest} = {3, 4}; items = {{x}}; items[1][1] = 9; return {INT, true, items[1][1], rest};");
    VmState state = new VmState(new TaskOrigin(Map.of(), 0,
        new world.mongoose.banteng.value.MooValue.ObjectValue(-1), "", "", true), new TaskBudget(60000, 5, 50));
    state.ensureRoot(program);
    assertEquals(program.variableNames().size(), state.currentFrame().locals.length);
    VmState restored = VmState.restore(state.snapshot(0));
    assertEquals(state.currentFrame().namedLocals(), restored.currentFrame().namedLocals());
    new PureVmFixture().execute(program, restored);
    assertEquals(new ListValue(List.of(new IntegerValue(0),
        world.mongoose.banteng.value.MooValue.BooleanValue.TRUE, new IntegerValue(9),
        new ListValue(List.of(new IntegerValue(4))))), restored.returnValue().orElseThrow());
  }

  @Test
  void snapshotDoesNotReinitializeClearedConstantSlotsOrKeepUndeclaredNames() {
    BytecodeProgram program = new MooCompiler().compile("return INT;");
    VmState state = new VmState(new TaskOrigin(Map.of("undeclared", new IntegerValue(7)), 0,
        new world.mongoose.banteng.value.MooValue.ObjectValue(-1), "", "", true), new TaskBudget(60000, 5, 50));
    state.ensureRoot(program);
    state.currentFrame().locals[16] = null;
    VmState restored = VmState.restore(state.snapshot(0));
    assertNull(restored.currentFrame().locals[16]);
    assertFalse(restored.currentFrame().namedLocals().containsKey("undeclared"));
  }
}
