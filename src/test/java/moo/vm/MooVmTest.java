package moo.vm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.MooCompiler;
import moo.syntax.MooParser;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.StringValue;
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

  @Test
  void concatenatesOwnedStringBytesBeforeEquality() {
    BytecodeProgram program =
        new MooCompiler().compile(MooParser.parse("return \"10\" == \"1\" + \"0\";"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(1), state.returnValue().orElseThrow());
  }

  @Test
  void splicesListsIntoListConstructionBeforeRecursiveEquality() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "x = {1, 2.0}; y = {#3, \"four\"}; "
                        + "return {1, 2.0, #3, \"four\"} == {@x, @y};"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(1), state.returnValue().orElseThrow());
  }

  @Test
  void constructsUpdatesAndRecursivelyComparesMaps() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "x = []; x[1] = 2.0; x[#3] = \"four\"; "
                        + "return [1 -> 2.0, #3 -> \"four\"] == x;"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(1), state.returnValue().orElseThrow());
  }

  @Test
  void keepsMapsAndListsUnequal() {
    BytecodeProgram program = new MooCompiler().compile(MooParser.parse("return [] == {};"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(0), state.returnValue().orElseThrow());
  }

  @Test
  void usesSignedZeroAndAdjacentFloatMapKeyIdentity() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "zeroes = [0.0 -> \"positive\"]; zeroes[-0.0] = \"negative\"; "
                        + "adjacent = [1.0 -> \"one\", 1.0000000000000002 -> \"next\"]; "
                        + "return {0.0 == -0.0, 0.0 <= -0.0, 0.0 >= -0.0, "
                        + "-0.0 && 1 || 0, toliteral(-0.0), length(mapkeys(zeroes)), "
                        + "zeroes[0.0], zeroes[-0.0], 1.0 == 1.0000000000000002, "
                        + "length(mapkeys(adjacent)), adjacent[1.0], adjacent[1.0000000000000002]};"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new IntegerValue(1),
                new IntegerValue(1),
                new IntegerValue(1),
                new IntegerValue(0),
                new StringValue("0.0".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(1),
                new StringValue("negative".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("negative".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(0),
                new IntegerValue(2),
                new StringValue("one".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("next".getBytes(StandardCharsets.ISO_8859_1)))),
        state.returnValue().orElseThrow());
  }

  @Test
  void recursivelyComparesNestedListsAndMaps() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "return {{1, [\"Key\" -> {\"Alpha\"}]} "
                        + "== {1, [\"key\" -> {\"alpha\"}]}, {1, {2}} == {1, {3}}};"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(List.of(new IntegerValue(1), new IntegerValue(0))),
        state.returnValue().orElseThrow());
  }

  @Test
  void preservesFoldedStringAndDistinctNumericMapKeyIdentity() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "keys = [\"Key\" -> \"first\", 1 -> \"int\", 1.0 -> \"float\"]; "
                        + "keys[\"kEY\"] = \"second\"; "
                        + "return {length(mapkeys(keys)), keys[\"KEY\"], keys[1], keys[1.0]};"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new IntegerValue(3),
                new StringValue("second".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("int".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("float".getBytes(StandardCharsets.ISO_8859_1)))),
        state.returnValue().orElseThrow());
  }

  @Test
  void duplicateLiteralAndIndexedUpdatePreserveTheReplacementKeyObject() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "literal = [\"Key\" -> 1, \"kEY\" -> 2]; "
                        + "updated = [\"First\" -> 1]; updated[\"fIRST\"] = 2; "
                        + "return {mapkeys(literal)[1], mapkeys(updated)[1]};"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    ListValue keys = (ListValue) state.returnValue().orElseThrow();
    assertArrayEquals(
        "kEY".getBytes(StandardCharsets.ISO_8859_1),
        ((StringValue) keys.elements().get(0)).bytes());
    assertArrayEquals(
        "fIRST".getBytes(StandardCharsets.ISO_8859_1),
        ((StringValue) keys.elements().get(1)).bytes());
  }

  @Test
  void indexedAssignmentReturnsItsValueAndLeavesAliasedMapUnchanged() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "original = [\"Key\" -> 1]; updated = original; "
                        + "assigned = updated[\"kEY\"] = 2; "
                        + "return {assigned, mapkeys(original)[1], original[\"key\"], "
                        + "mapkeys(updated)[1], updated[\"key\"]};"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new IntegerValue(2),
                new StringValue("Key".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(1),
                new StringValue("kEY".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(2))),
        state.returnValue().orElseThrow());
    ListValue result = (ListValue) state.returnValue().orElseThrow();
    assertArrayEquals(
        "Key".getBytes(StandardCharsets.ISO_8859_1),
        ((StringValue) result.elements().get(1)).bytes());
    assertArrayEquals(
        "kEY".getBytes(StandardCharsets.ISO_8859_1),
        ((StringValue) result.elements().get(3)).bytes());
  }

  @Test
  void invalidSpliceOperandsAndCollectionMapKeysRaiseTypeError() {
    for (String source :
        new String[] {
          "return `{@1} ! ANY';",
          "return `[{} -> 1] ! ANY';",
          "map = []; return `map[{}] = 1 ! ANY';"
        }) {
      BytecodeProgram program = new MooCompiler().compile(MooParser.parse(source));
      VmState state = new VmState();

      new MooVm().execute(program, state);

      assertEquals(VmState.Outcome.RETURNED, state.outcome(), source);
      assertEquals(ErrorValue.E_TYPE, state.returnValue().orElseThrow(), source);
    }
  }
}
