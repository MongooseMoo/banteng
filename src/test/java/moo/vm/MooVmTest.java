package moo.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import moo.bytecode.BytecodeProgram;
import moo.bytecode.MooCompiler;
import moo.syntax.MooParser;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
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

  @Test
  void subtractsTwoFloatsWithoutNumericPromotion() {
    BytecodeProgram program = new MooCompiler().compile(MooParser.parse("return 11.0 - 5.5;"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new FloatValue(5.5), state.returnValue().orElseThrow());
  }

  @Test
  void rejectsMixedFloatIntegerArithmeticAndOrdering() {
    for (String source : new String[] {"return 1.0 + 1;", "return 5.5 > 5;"}) {
      BytecodeProgram program = new MooCompiler().compile(MooParser.parse(source));
      VmState state = new VmState();

      new MooVm().execute(program, state);

      assertEquals(VmState.Outcome.ERRORED, state.outcome(), source);
      assertEquals(ErrorValue.E_TYPE, state.uncaughtError().orElseThrow(), source);
    }
  }

  @Test
  void floatDivisionAndModuloByZeroRaiseDivisionError() {
    for (String source : new String[] {"return 1.0 / 0.0;", "return 1.0 % -0.0;"}) {
      BytecodeProgram program = new MooCompiler().compile(MooParser.parse(source));
      VmState state = new VmState();

      new MooVm().execute(program, state);

      assertEquals(VmState.Outcome.ERRORED, state.outcome(), source);
      assertEquals(ErrorValue.E_DIV, state.uncaughtError().orElseThrow(), source);
    }
  }

  @Test
  void floatModuloUsesDivisorSign() {
    for (String source : new String[] {"return -15.0 % 4.0;", "return 15.0 % -4.0;"}) {
      BytecodeProgram program = new MooCompiler().compile(MooParser.parse(source));
      VmState state = new VmState();

      new MooVm().execute(program, state);

      double expected = source.contains("-15.0") ? 1.0 : -1.0;
      assertEquals(VmState.Outcome.RETURNED, state.outcome(), source);
      assertEquals(new FloatValue(expected), state.returnValue().orElseThrow(), source);
    }
  }

  @Test
  void floatBaseIntegerPowerUsesFloatErrorBoundaries() {
    BytecodeProgram finite = new MooCompiler().compile(MooParser.parse("return 2.0 ^ 3;"));
    VmState finiteState = new VmState();

    new MooVm().execute(finite, finiteState);

    assertEquals(VmState.Outcome.RETURNED, finiteState.outcome());
    assertEquals(new FloatValue(8.0), finiteState.returnValue().orElseThrow());

    String[] sources = {"return 0.0 ^ -1;", "return 1.0e308 ^ 2;", "return 2 ^ 3.0;"};
    ErrorValue[] errors = {ErrorValue.E_DIV, ErrorValue.E_FLOAT, ErrorValue.E_TYPE};
    for (int index = 0; index < sources.length; index++) {
      BytecodeProgram program = new MooCompiler().compile(MooParser.parse(sources[index]));
      VmState state = new VmState();

      new MooVm().execute(program, state);

      assertEquals(VmState.Outcome.ERRORED, state.outcome(), sources[index]);
      assertEquals(errors[index], state.uncaughtError().orElseThrow(), sources[index]);
    }
  }

  @Test
  void catchExpressionStillBindsBareErrorValue() {
    BytecodeProgram program = new MooCompiler().compile(MooParser.parse("return `1.0 + 1 ! ANY';"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(ErrorValue.E_TYPE, state.returnValue().orElseThrow());
  }
}
