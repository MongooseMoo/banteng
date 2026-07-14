package moo.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import moo.bytecode.BytecodeProgram;
import moo.bytecode.MooCompiler;
import moo.syntax.MooParser;
import moo.value.MooValue.IntegerValue;
import org.junit.jupiter.api.Test;

final class MooVmTest {
  @Test
  void executesDynamicSourceThroughParserCompilerAndExplicitVmState() {
    BytecodeProgram program = new MooCompiler().compile(MooParser.parse("return 1 + 1;"));
    VmState state = new VmState();

    assertEquals(0, state.instructionPointer());
    assertTrue(state.operandStack().isEmpty());
    assertEquals(VmState.Outcome.RUNNING, state.outcome());
    assertTrue(state.returnValue().isEmpty());

    new MooVm().execute(program, state);

    assertEquals(4, state.instructionPointer());
    assertTrue(state.operandStack().isEmpty());
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(2), state.returnValue().orElseThrow());
  }
}
