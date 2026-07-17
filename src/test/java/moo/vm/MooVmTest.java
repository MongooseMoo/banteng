package moo.vm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import moo.builtin.BuiltinCatalog;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.MooCompiler;
import moo.syntax.Ast;
import moo.syntax.MooParser;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.world.WorldObject;
import moo.world.WorldTxn;
import moo.world.WorldVerb;
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
  void returnsInterruptErrorThroughTheCompleteLiteralPipeline() {
    byte[] source = "return E_INTRPT;".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.ErrorLiteral errorLiteral =
        assertInstanceOf(Ast.ErrorLiteral.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 16, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 15, 1, 8), errorLiteral.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    VmState state = new VmState();

    assertEquals("0 PUSH_ERROR E_INTRPT\n1 RETURN", program.disassemble());

    new MooVm().execute(program, state);

    MooValue returned = state.returnValue().orElseThrow();
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(MooValue.Type.ERROR, returned.type());
    assertEquals(18, ((ErrorValue) returned).code());
    assertEquals("E_INTRPT", returned.toLiteral());
    assertFalse(returned.isTruthy());
  }

  @Test
  void returnsIntegerThroughTheCompleteLiteralPipeline() {
    byte[] source = "return 42;".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.IntegerLiteral integerLiteral =
        assertInstanceOf(Ast.IntegerLiteral.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 10, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 9, 1, 8), integerLiteral.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals("0 PUSH_INTEGER 42\n1 RETURN", program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    MooValue returned = state.returnValue().orElseThrow();
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(MooValue.Type.INTEGER, returned.type());
    assertEquals("42", returned.toLiteral());
    assertTrue(returned.isTruthy());
  }

  @Test
  void returnsFloatThroughTheCompleteLiteralPipeline() {
    byte[] source = "return 3.5;".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.FloatLiteral floatLiteral =
        assertInstanceOf(Ast.FloatLiteral.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 11, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 10, 1, 8), floatLiteral.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        "0 PUSH_FLOAT " + Double.doubleToRawLongBits(3.5) + "\n1 RETURN",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    MooValue returned = state.returnValue().orElseThrow();
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(MooValue.Type.FLOAT, returned.type());
    assertEquals("3.5", returned.toLiteral());
    assertTrue(returned.isTruthy());
  }

  @Test
  void exposesTheLiveForegroundTickRemainderAndRejectsArguments() {
    BytecodeProgram program =
        new MooCompiler().compile(MooParser.parse("return {ticks_left(), ticks_left()};"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertTrue(
        state.uncaughtError().isEmpty(), () -> "unexpected MOO error: " + state.uncaughtError());
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(List.of(new IntegerValue(59_999), new IntegerValue(59_997))),
        state.returnValue().orElseThrow());

    VmState invalid = new VmState();
    new MooVm()
        .execute(new MooCompiler().compile(MooParser.parse("return ticks_left(1);")), invalid);

    assertEquals(VmState.Outcome.ERRORED, invalid.outcome());
    assertEquals(ErrorValue.E_ARGS, invalid.uncaughtError().orElseThrow());
    assertEquals(BuiltinCatalog.EffectClass.PURE, new BuiltinCatalog().effectClass("ticks_left"));
  }

  @Test
  void exposesTheLiveForegroundSecondsRemainderAndRejectsArguments() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(MooParser.parse("return {seconds_left(), call_function(\"seconds_left\")};"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertTrue(
        state.uncaughtError().isEmpty(), () -> "unexpected MOO error: " + state.uncaughtError());
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    ListValue values = (ListValue) state.returnValue().orElseThrow();
    assertEquals(2, values.elements().size());
    assertTrue(values.elements().get(0) instanceof IntegerValue);
    assertTrue(values.elements().get(1) instanceof IntegerValue);
    long direct = ((IntegerValue) values.elements().get(0)).value();
    long indirect = ((IntegerValue) values.elements().get(1)).value();
    assertTrue(direct >= 0 && direct <= 5);
    assertTrue(indirect >= 0 && indirect <= 5);
    assertTrue(indirect <= direct);

    VmState invalid = new VmState();
    new MooVm()
        .execute(new MooCompiler().compile(MooParser.parse("return seconds_left(1);")), invalid);

    assertEquals(VmState.Outcome.ERRORED, invalid.outcome());
    assertEquals(ErrorValue.E_ARGS, invalid.uncaughtError().orElseThrow());
    assertEquals(BuiltinCatalog.EffectClass.PURE, new BuiltinCatalog().effectClass("seconds_left"));
  }

  @Test
  void evaluatesExactSqliteTypePrerequisiteExpression() {
    BytecodeProgram program =
        new MooCompiler().compile(MooParser.parse("return typeof({}) == LIST;"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(1), state.returnValue().orElseThrow());
  }

  @Test
  void resolvesIntegerAndStringTypeConstantsAfterLocalLookup() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(MooParser.parse("return {typeof(1) == INT, typeof(\"x\") == STR};"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(List.of(new IntegerValue(1), new IntegerValue(1))),
        state.returnValue().orElseThrow());
  }

  @Test
  void resolvesFloatTypeConstantAfterLocalLookup() {
    BytecodeProgram program =
        new MooCompiler().compile(MooParser.parse("return typeof(1.0) == FLOAT;"));
    VmState state = new VmState();

    new MooVm().execute(program, state, new WorldTxn(List.of(), List.of()), new BuiltinCatalog());

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(1), state.returnValue().orElseThrow());
  }

  @Test
  void resolvesObjectTypeConstantAfterLocalLookup() {
    BytecodeProgram program =
        new MooCompiler().compile(MooParser.parse("return typeof(#0) == OBJ;"));
    VmState state = new VmState();

    new MooVm().execute(program, state, new WorldTxn(List.of(), List.of()), new BuiltinCatalog());

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(1), state.returnValue().orElseThrow());
  }

  @Test
  void concatenatesZeroOrMoreTostrArgumentsAcrossTheClosedValueFamily() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "return {tostr(), tostr(\"value=\", 42, 3.0, #0, E_TYPE, E_INTRPT, {1}, [\"a\" -> 1])};"));
    VmState state = new VmState();

    new MooVm().execute(program, state, new WorldTxn(List.of(), List.of()), new BuiltinCatalog());

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new StringValue(new byte[0]),
                new StringValue(
                    "value=423.0#0Type mismatchInterrupted{list}[map]"
                        .getBytes(StandardCharsets.ISO_8859_1)))),
        state.returnValue().orElseThrow());
    assertEquals(BuiltinCatalog.EffectClass.PURE, new BuiltinCatalog().effectClass("tostr"));
  }

  @Test
  void describesTheFrozenQueuedTasksCatalogRowsInOrder() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "return {function_info(), function_info(\"function_info\"), "
                        + "function_info(\"queued_tasks\")};"));
    VmState state = new VmState();

    new MooVm().execute(program, state, new WorldTxn(List.of(), List.of()), new BuiltinCatalog());

    ListValue functionInfoDescription =
        new ListValue(
            List.of(
                new StringValue("function_info".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(0),
                new IntegerValue(1),
                new ListValue(List.of(new IntegerValue(2)))));
    ListValue queuedTasksDescription =
        new ListValue(
            List.of(
                new StringValue("queued_tasks".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(0),
                new IntegerValue(2),
                new ListValue(List.of(new IntegerValue(0), new IntegerValue(0)))));
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new ListValue(List.of(functionInfoDescription, queuedTasksDescription)),
                functionInfoDescription,
                queuedTasksDescription)),
        state.returnValue().orElseThrow());

    String[] failures = {
      "return function_info(\"queued_tasks\", \"extra\");",
      "return function_info(0);",
      "return function_info(\"missing\");"
    };
    ErrorValue[] errors = {ErrorValue.E_ARGS, ErrorValue.E_TYPE, ErrorValue.E_INVARG};
    for (int index = 0; index < failures.length; index++) {
      VmState failure = new VmState();

      new MooVm()
          .execute(
              new MooCompiler().compile(MooParser.parse(failures[index])),
              failure,
              new WorldTxn(List.of(), List.of()),
              new BuiltinCatalog());

      assertEquals(VmState.Outcome.ERRORED, failure.outcome(), failures[index]);
      assertEquals(errors[index], failure.uncaughtError().orElseThrow(), failures[index]);
    }
    assertEquals(
        BuiltinCatalog.EffectClass.PURE, new BuiltinCatalog().effectClass("function_info"));
  }

  @Test
  void exposesTheFrozenEmptyQueuedTasksListAndCountShapes() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "return {queued_tasks(), queued_tasks(0), queued_tasks(1), "
                        + "queued_tasks(0, 0), queued_tasks(1, 0), "
                        + "queued_tasks(0, 1), queued_tasks(1, 1)};"));
    VmState state = new VmState();

    new MooVm().execute(program, state, new WorldTxn(List.of(), List.of()), new BuiltinCatalog());

    ListValue empty = new ListValue(List.of());
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(empty, empty, empty, empty, empty, new IntegerValue(0), new IntegerValue(0))),
        state.returnValue().orElseThrow());

    String[] failures = {
      "return queued_tasks(0, 0, 0);",
      "return queued_tasks(\"not an int\");",
      "return queued_tasks(0, \"not an int\");"
    };
    ErrorValue[] errors = {ErrorValue.E_ARGS, ErrorValue.E_TYPE, ErrorValue.E_TYPE};
    for (int index = 0; index < failures.length; index++) {
      VmState failure = new VmState();

      new MooVm()
          .execute(
              new MooCompiler().compile(MooParser.parse(failures[index])),
              failure,
              new WorldTxn(List.of(), List.of()),
              new BuiltinCatalog());

      assertEquals(VmState.Outcome.ERRORED, failure.outcome(), failures[index]);
      assertEquals(errors[index], failure.uncaughtError().orElseThrow(), failures[index]);
    }
    assertEquals(
        BuiltinCatalog.EffectClass.EXTERNAL_READ, new BuiltinCatalog().effectClass("queued_tasks"));
  }

  @Test
  void evaluatesStructuralListMembershipAndRejectsNonListRightOperand() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "return {{1, {2}} in {{0}, {1, {2}}}, " + "{1, {3}} in {{0}, {1, {2}}}};"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(List.of(new IntegerValue(2), new IntegerValue(0))),
        state.returnValue().orElseThrow());

    BytecodeProgram invalid = new MooCompiler().compile(MooParser.parse("return 1 in 1;"));
    VmState invalidState = new VmState();

    new MooVm().execute(invalid, invalidState);

    assertEquals(VmState.Outcome.ERRORED, invalidState.outcome());
    assertEquals(ErrorValue.E_TYPE, invalidState.uncaughtError().orElseThrow());
  }

  @Test
  void stopsAtForkBoundaryBeforeParentReturnAndCapturesChildDelayAndLocals() {
    ListValue captured =
        new ListValue(
            List.of(
                new IntegerValue(7),
                new StringValue("captured".getBytes(StandardCharsets.ISO_8859_1))));
    BytecodeProgram program =
        new MooCompiler().compile(MooParser.parse("fork (1) return marker; endfork return 99;"));
    VmState state = new VmState(Map.of("marker", captured), 8);

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.FORKED, state.outcome());
    assertTrue(state.returnValue().isEmpty());
    VmState.ForkRequest request = state.forkRequest().orElseThrow();
    assertEquals(program.forkVectors().getFirst(), request.program());
    assertEquals(1.0, request.delaySeconds());
    assertEquals(Map.of("marker", captured), request.locals());
    assertEquals(8, request.programmer());

    VmState child = new VmState(request.locals(), request.programmer());
    new MooVm().execute(request.program(), child);
    assertEquals(VmState.Outcome.RETURNED, child.outcome());
    assertEquals(captured, child.returnValue().orElseThrow());
  }

  @Test
  void timedSuspendYieldsExplicitSuspendedBoundaryWithoutSleeping() {
    BytecodeProgram program = new MooCompiler().compile(MooParser.parse("return suspend(1);"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.SUSPENDED, state.outcome());
    assertEquals(1.0, state.suspensionDelaySeconds().orElseThrow());
    assertTrue(state.hostResult().isEmpty());
    assertTrue(state.returnValue().isEmpty());
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
  void orderedStatementHandlersSelectFirstMatchAndLaterClause() {
    BytecodeProgram firstMatch =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "try raise(E_TYPE); "
                        + "except error (E_TYPE) return error[1]; "
                        + "except (ANY) return E_NONE; endtry"));
    VmState firstState = new VmState();

    new MooVm().execute(firstMatch, firstState);

    assertEquals(VmState.Outcome.RETURNED, firstState.outcome());
    assertEquals(ErrorValue.E_TYPE, firstState.returnValue().orElseThrow());

    BytecodeProgram laterMatch =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "try raise(E_ARGS); "
                        + "except (E_TYPE) return 1; "
                        + "except (E_ARGS) return 2; "
                        + "except (ANY) return 3; endtry"));
    VmState laterState = new VmState();

    new MooVm().execute(laterMatch, laterState);

    assertEquals(VmState.Outcome.RETURNED, laterState.outcome());
    assertEquals(new IntegerValue(2), laterState.returnValue().orElseThrow());
  }

  @Test
  void exactNineArgumentSqliteOpenPresenceShapeReachesLaterAnyClause() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "try sqlite_open(\"\", 0, 0, 0, 0, 0, 0, 0, 0); "
                        + "except (E_VERBNF) return 0; "
                        + "except (ANY) return 1; endtry"));
    VmState state = new VmState(Map.of(), 0);
    WorldObject wizard =
        new WorldObject(0, "wizard", 6, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldTxn world = new WorldTxn(List.of(0L), List.of(wizard));

    new MooVm().execute(program, state, world, new BuiltinCatalog());

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(1), state.returnValue().orElseThrow());
  }

  @Test
  void errorRaisedBySelectedHandlerEscapesLaterSiblingClause() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "try raise(E_TYPE); "
                        + "except (E_TYPE) raise(E_ARGS); "
                        + "except (E_ARGS) return 99; endtry"));
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.ERRORED, state.outcome());
    assertEquals(ErrorValue.E_ARGS, state.uncaughtError().orElseThrow());
  }

  @Test
  void statementHandlersShareFinallyAfterNormalBodyAndSelectedHandler() {
    String[] sources = {
      "x = 0; try x = 1; except (ANY) x = 2; finally x = x + 10; endtry return x;",
      "x = 0; try raise(E_TYPE); except (E_TYPE) x = 2; finally x = x + 10; endtry return x;"
    };
    long[] expected = {11, 12};

    for (int index = 0; index < sources.length; index++) {
      BytecodeProgram program = new MooCompiler().compile(MooParser.parse(sources[index]));
      VmState state = new VmState();

      new MooVm().execute(program, state);

      assertEquals(VmState.Outcome.RETURNED, state.outcome(), sources[index]);
      assertEquals(
          new IntegerValue(expected[index]), state.returnValue().orElseThrow(), sources[index]);
    }
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

  @Test
  void splicesBuiltinArgumentsAndRejectsANonListSplice() {
    BytecodeProgram success =
        new MooCompiler().compile(MooParser.parse("args = {1, 2, 3}; return max(@args);"));
    VmState successState = new VmState();

    new MooVm().execute(success, successState);

    assertEquals(VmState.Outcome.RETURNED, successState.outcome());
    assertEquals(new IntegerValue(3), successState.returnValue().orElseThrow());

    BytecodeProgram failure = new MooCompiler().compile(MooParser.parse("return max(@5);"));
    VmState failureState = new VmState();

    new MooVm().execute(failure, failureState);

    assertEquals(VmState.Outcome.ERRORED, failureState.outcome());
    assertEquals(ErrorValue.E_TYPE, failureState.uncaughtError().orElseThrow());
  }

  @Test
  void readsStaticAndComputedBuiltInPropertiesThroughTheSameOwner() {
    WorldObject systemObject =
        new WorldObject(
            0, "System Object", 7, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldTxn world = new WorldTxn(List.of(0L), List.of(systemObject));
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse("name = \"name\"; return {#0.(name), #0.(\"name\") == #0.name};"));
    VmState state = new VmState();

    new MooVm().execute(program, state, world, new BuiltinCatalog());

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new StringValue("System Object".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(1))),
        state.returnValue().orElseThrow());
  }

  @Test
  void computedPropertyNamesRaiseTheFrozenErrors() {
    WorldObject systemObject =
        new WorldObject(
            0, "System Object", 7, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    String[] sources = {"return #0.(\"nonexistent_prop_xyz\");", "return #0.(1);"};
    ErrorValue[] errors = {ErrorValue.E_PROPNF, ErrorValue.E_TYPE};

    for (int index = 0; index < sources.length; index++) {
      WorldTxn world = new WorldTxn(List.of(0L), List.of(systemObject));
      BytecodeProgram program = new MooCompiler().compile(MooParser.parse(sources[index]));
      VmState state = new VmState();

      new MooVm().execute(program, state, world, new BuiltinCatalog());

      assertEquals(VmState.Outcome.ERRORED, state.outcome(), sources[index]);
      assertEquals(errors[index], state.uncaughtError().orElseThrow(), sources[index]);
    }
  }

  @Test
  void computedPropertyAssignmentsReturnWriteAndUseToastEvaluationOrder() {
    WorldObject systemObject =
        new WorldObject(
            0, "System Object", 7, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    String[] sources = {
      "o = create(#-1); add_property(o, \"foo\", 0, {player, \"\"}); "
          + "result = (o.(\"foo\") = 99); value = o.foo; recycle(o); "
          + "return {result, value};",
      "o = create(#-1); add_property(o, \"first\", \"initial\", {player, \"\"}); "
          + "add_property(o, \"second\", \"unchanged\", {player, \"\"}); name = \"first\"; "
          + "o.(name) = (name = \"second\"); result = {o.first, o.second, name}; "
          + "recycle(o); return result;",
      "o = create(#-1); add_property(o, \"foo\", 0, {player, \"\"}); name = \"foo\"; "
          + "o.(name) = 99; result = o.foo; recycle(o); return result;"
    };
    List<MooValue> expected =
        List.of(
            new ListValue(List.of(new IntegerValue(99), new IntegerValue(99))),
            new ListValue(
                List.of(
                    new StringValue("second".getBytes(StandardCharsets.ISO_8859_1)),
                    new StringValue("unchanged".getBytes(StandardCharsets.ISO_8859_1)),
                    new StringValue("second".getBytes(StandardCharsets.ISO_8859_1)))),
            new IntegerValue(99));

    for (int index = 0; index < sources.length; index++) {
      WorldTxn world = new WorldTxn(List.of(0L), List.of(systemObject));
      BytecodeProgram program = new MooCompiler().compile(MooParser.parse(sources[index]));
      VmState state = new VmState(Map.of("player", new ObjectValue(0)), 0);

      new MooVm().execute(program, state, world, new BuiltinCatalog());

      assertEquals(VmState.Outcome.RETURNED, state.outcome(), sources[index]);
      assertEquals(expected.get(index), state.returnValue().orElseThrow(), sources[index]);
      assertEquals(1, world.objectCount(), sources[index]);
    }
  }

  @Test
  void clearsObjectWriteFlagThenDeniesUnpermittedPropertyAccess() {
    WorldObject system =
        new WorldObject(0, "System", 7, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject wizard =
        new WorldObject(3, "Wizard", 7, 3, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject programmer =
        new WorldObject(4, "Programmer", 3, 4, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldTxn world = new WorldTxn(List.of(3L, 4L), List.of(system, wizard, programmer));
    VmState setup = new VmState(Map.of("player", new ObjectValue(3)), 3);

    new MooVm()
        .execute(
            new MooCompiler()
                .compile(
                    MooParser.parse(
                        "obj = create(#-1); obj.w = 0; "
                            + "add_property(obj, \"audit_secret\", 10, {#0, \"\"}); "
                            + "return obj;")),
            setup,
            world,
            new BuiltinCatalog());

    assertEquals(VmState.Outcome.RETURNED, setup.outcome());
    ObjectValue object = (ObjectValue) setup.returnValue().orElseThrow();
    assertEquals(0, world.object(object.value()).orElseThrow().flags() & 32);
    assertEquals(0, world.property(object.value(), "audit_secret").orElseThrow().owner());
    assertEquals(0, world.property(object.value(), "audit_secret").orElseThrow().permissions());

    VmState read = new VmState(Map.of("player", new ObjectValue(4)), 4);
    new MooVm()
        .execute(
            new MooCompiler()
                .compile(MooParser.parse("return #" + object.value() + ".audit_secret;")),
            read,
            world,
            new BuiltinCatalog());

    assertEquals(VmState.Outcome.ERRORED, read.outcome());
    assertEquals(ErrorValue.E_PERM, read.uncaughtError().orElseThrow());

    VmState write = new VmState(Map.of("player", new ObjectValue(4)), 4);
    new MooVm()
        .execute(
            new MooCompiler()
                .compile(MooParser.parse("#" + object.value() + ".audit_secret = 11;")),
            write,
            world,
            new BuiltinCatalog());

    assertEquals(VmState.Outcome.ERRORED, write.outcome());
    assertEquals(ErrorValue.E_PERM, write.uncaughtError().orElseThrow());
    assertEquals(
        new IntegerValue(10), world.property(object.value(), "audit_secret").orElseThrow().value());
  }

  @Test
  void roundTripsIntrinsicCommandConnectionOptionWithinOneTask() {
    WorldObject wizard =
        new WorldObject(3, "Wizard", 7, 3, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldTxn world = new WorldTxn(List.of(3L), List.of(wizard));
    world.openConnection(-47, new MapValue(Map.of()));
    assertTrue(world.switchConnectionPlayer(-47, 3));
    VmState state = new VmState(Map.of("player", new ObjectValue(3)), 3);

    new MooVm()
        .execute(
            new MooCompiler()
                .compile(
                    MooParser.parse(
                        "before = connection_options(player, \"intrinsic-commands\"); "
                            + "set_connection_option(player, \"intrinsic-commands\", "
                            + "{\"PREFIX\", \"SUFFIX\"}); "
                            + "subset = connection_options(player, \"intrinsic-commands\"); "
                            + "set_connection_option(player, \"intrinsic-commands\", 1); "
                            + "restored = connection_options(player, \"intrinsic-commands\"); "
                            + "return {before, subset, restored};")),
            state,
            world,
            new BuiltinCatalog());

    ListValue all =
        new ListValue(
            List.of(
                new StringValue(".program".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("PREFIX".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("SUFFIX".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("OUTPUTPREFIX".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("OUTPUTSUFFIX".getBytes(StandardCharsets.ISO_8859_1))));
    ListValue subset =
        new ListValue(
            List.of(
                new StringValue("PREFIX".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("SUFFIX".getBytes(StandardCharsets.ISO_8859_1))));

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new ListValue(List.of(all, subset, all)), state.returnValue().orElseThrow());
  }

  @Test
  void rejectsUnknownIntrinsicCommandWithoutChangingTable() {
    WorldObject wizard =
        new WorldObject(3, "Wizard", 7, 3, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldTxn world = new WorldTxn(List.of(3L), List.of(wizard));
    world.openConnection(-47, new MapValue(Map.of()));
    assertTrue(world.switchConnectionPlayer(-47, 3));
    VmState state = new VmState(Map.of("player", new ObjectValue(3)), 3);

    new MooVm()
        .execute(
            new MooCompiler()
                .compile(
                    MooParser.parse(
                        "return set_connection_option(player, \"intrinsic-commands\", "
                            + "{\"PREFIX\", \"NOT_A_TOAST_INTRINSIC\"});")),
            state,
            world,
            new BuiltinCatalog());

    assertEquals(VmState.Outcome.ERRORED, state.outcome());
    assertEquals(ErrorValue.E_INVARG, state.uncaughtError().orElseThrow());
    assertEquals(
        new ListValue(
            List.of(
                new StringValue(".program".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("PREFIX".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("SUFFIX".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("OUTPUTPREFIX".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("OUTPUTSUFFIX".getBytes(StandardCharsets.ISO_8859_1)))),
        world.intrinsicCommands(3).orElseThrow());
  }

  @Test
  void maxPreservesHomogeneousNumericKindsAndRejectsEveryFrozenError() {
    String[] successes = {"return max(1, 9, 3);", "return max(1.5, 9.25, 3.0);"};
    MooValue[] values = {new IntegerValue(9), new FloatValue(9.25)};
    for (int index = 0; index < successes.length; index++) {
      VmState state = new VmState();

      new MooVm()
          .execute(
              new MooCompiler().compile(MooParser.parse(successes[index])),
              state,
              new WorldTxn(List.of(), List.of()),
              new BuiltinCatalog());

      assertEquals(VmState.Outcome.RETURNED, state.outcome(), successes[index]);
      assertEquals(values[index], state.returnValue().orElseThrow(), successes[index]);
    }

    String[] failures = {"return max();", "return max(1, 2.0);", "return max(\"x\");"};
    ErrorValue[] errors = {ErrorValue.E_ARGS, ErrorValue.E_TYPE, ErrorValue.E_TYPE};
    for (int index = 0; index < failures.length; index++) {
      VmState state = new VmState();

      new MooVm()
          .execute(
              new MooCompiler().compile(MooParser.parse(failures[index])),
              state,
              new WorldTxn(List.of(), List.of()),
              new BuiltinCatalog());

      assertEquals(VmState.Outcome.ERRORED, state.outcome(), failures[index]);
      assertEquals(errors[index], state.uncaughtError().orElseThrow(), failures[index]);
    }
  }

  @Test
  void caughtErrorsDiscardOnlyPartialGuardedOperands() {
    String source =
        """
        return {
          `max(1, @5) ! E_TYPE => 11',
          `#0.(max(@5)) ! E_TYPE => 22',
          {1, @`max(@5) ! E_TYPE => {2, 3}', 4}
        };
        """;
    VmState state = new VmState();

    new MooVm()
        .execute(
            new MooCompiler().compile(MooParser.parse(source)),
            state,
            new WorldTxn(List.of(), List.of()),
            new BuiltinCatalog());

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new IntegerValue(11),
                new IntegerValue(22),
                new ListValue(
                    List.of(
                        new IntegerValue(1),
                        new IntegerValue(2),
                        new IntegerValue(3),
                        new IntegerValue(4))))),
        state.returnValue().orElseThrow());
  }

  @Test
  void computedVerbCallsUseDirectMutationsAndToastEvaluationOrder() {
    WorldObject systemObject =
        new WorldObject(0, "System", 6, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldTxn world = new WorldTxn(List.of(0L), List.of(systemObject));
    String source =
        """
        o = create(#-1);
        add_verb(o, {player, "xd", "target"}, {"this", "none", "this"});
        set_verb_code(o, "target", {"return args[1];"});
        name = "target";
        result = o:(name)((name = "missing"));
        delete_verb(o, "target");
        recycle(o);
        return {result, name};
        """;
    VmState state = new VmState(Map.of("player", new ObjectValue(0)), 0);

    new MooVm()
        .execute(
            new MooCompiler().compile(MooParser.parse(source)), state, world, new BuiltinCatalog());

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new StringValue("missing".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("missing".getBytes(StandardCharsets.ISO_8859_1)))),
        state.returnValue().orElseThrow());
    assertEquals(1, world.objectCount());

    VmState typeError = new VmState();
    new MooVm()
        .execute(
            new MooCompiler().compile(MooParser.parse("return #0:(1)();")),
            typeError,
            world,
            new BuiltinCatalog());
    assertEquals(ErrorValue.E_TYPE, typeError.uncaughtError().orElseThrow());
  }

  @Test
  void verbFramesSkipNonExecutableMatchesPopulateLocalsAndScopeProgrammer() {
    WorldVerb executable =
        new WorldVerb(
            "Mi*XeD",
            7,
            4,
            -1,
            "set_task_perms(#9); "
                + "return {this, player, caller, verb, args, argstr, create(#-1)};");
    WorldVerb nonExecutable = new WorldVerb("mixed", 8, 8, -1, "return 0;");
    WorldObject parent =
        new WorldObject(
            1, "parent", 0, 1, -1, -1, List.of(), List.of(2L), List.of(executable), List.of());
    WorldObject child =
        new WorldObject(
            2, "child", 0, 2, -1, 1, List.of(), List.of(), List.of(nonExecutable), List.of());
    WorldObject player =
        new WorldObject(3, "player", 0, 3, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject caller =
        new WorldObject(4, "caller", 0, 4, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldTxn world = new WorldTxn(List.of(), List.of(parent, child, player, caller));
    VmState state =
        new VmState(Map.of("player", new ObjectValue(3), "this", new ObjectValue(4)), 3);

    new MooVm()
        .execute(
            new MooCompiler()
                .compile(
                    MooParser.parse(
                        "result = #2:(\"MIXED\")(42); after = create(#-1); "
                            + "return {result, after};")),
            state,
            world,
            new BuiltinCatalog());

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new ListValue(
                    List.of(
                        new ObjectValue(2),
                        new ObjectValue(3),
                        new ObjectValue(4),
                        new StringValue("MIXED".getBytes(StandardCharsets.ISO_8859_1)),
                        new ListValue(List.of(new IntegerValue(42))),
                        new StringValue(new byte[0]),
                        new ObjectValue(5))),
                new ObjectValue(6))),
        state.returnValue().orElseThrow());
    assertEquals(9, world.object(5).orElseThrow().owner());
    assertEquals(3, world.object(6).orElseThrow().owner());
  }

  @Test
  void recycleObjectRewritesEveryTopologyRecordInOneSnapshot() {
    WorldObject target =
        new WorldObject(1, "target", 1, 0, 10, 3, List.of(2L), List.of(4L), List.of(), List.of());
    WorldObject content =
        new WorldObject(2, "content", 0, 0, 1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject parent =
        new WorldObject(
            3, "parent", 0, 0, -1, -1, List.of(), List.of(7L, 1L, 6L), List.of(), List.of());
    WorldObject child =
        new WorldObject(4, "child", 0, 0, -1, 1, List.of(), List.of(), List.of(), List.of());
    WorldObject location =
        new WorldObject(
            10, "location", 0, 0, -1, -1, List.of(8L, 1L, 9L), List.of(), List.of(), List.of());
    WorldTxn world = new WorldTxn(List.of(1L), List.of(target, content, parent, child, location));

    assertTrue(world.recycleObject(1));

    assertTrue(world.object(1).isEmpty());
    assertEquals(List.of(), world.players());
    assertEquals(-1, world.object(2).orElseThrow().location());
    assertEquals(3, world.object(4).orElseThrow().parent());
    assertEquals(List.of(8L, 9L), world.object(10).orElseThrow().contents());
    assertEquals(List.of(7L, 4L, 6L), world.object(3).orElseThrow().children());
  }

  @Test
  void recycleContinuationsDestroyBeforeReturningOrPropagatingHookErrors() {
    String[] hookSources = {
      "return 99;",
      "raise(E_DIV);",
      "delete_verb(this, \"recycle\"); recycle(this); return 1;",
      "delete_verb(this, \"recycle\"); recycle(this); raise(E_DIV);"
    };
    String[] rootSources = {
      "return recycle(#2);",
      "return `recycle(#2) ! E_DIV => 1';",
      "return `recycle(#2) ! E_INVARG => 1';",
      "return `recycle(#2) ! E_DIV => 1';"
    };
    for (int index = 0; index < hookSources.length; index++) {
      WorldVerb hook = new WorldVerb("recycle", 0, 4, -1, hookSources[index]);
      WorldObject parent =
          new WorldObject(1, "parent", 0, 0, -1, -1, List.of(), List.of(2L), List.of(), List.of());
      WorldObject target =
          new WorldObject(2, "target", 0, 0, -1, 1, List.of(), List.of(), List.of(hook), List.of());
      WorldTxn world = new WorldTxn(List.of(), List.of(parent, target));
      VmState state = new VmState(Map.of("player", new ObjectValue(0)), 0);

      new MooVm()
          .execute(
              new MooCompiler().compile(MooParser.parse(rootSources[index])),
              state,
              world,
              new BuiltinCatalog());

      assertEquals(VmState.Outcome.RETURNED, state.outcome(), hookSources[index]);
      assertEquals(
          new IntegerValue(index == 0 ? 0 : 1),
          state.returnValue().orElseThrow(),
          hookSources[index]);
      assertTrue(world.object(2).isEmpty(), hookSources[index]);
    }
  }

  @Test
  void recycleChecksValidityAndOwnerOrWizardPermission() {
    WorldObject programmer =
        new WorldObject(3, "programmer", 2, 3, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject target =
        new WorldObject(4, "target", 0, 4, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldTxn world = new WorldTxn(List.of(), List.of(programmer, target));
    VmState denied = new VmState(Map.of(), 3);

    new MooVm()
        .execute(
            new MooCompiler().compile(MooParser.parse("return `recycle(#4) ! E_PERM => 1';")),
            denied,
            world,
            new BuiltinCatalog());

    assertEquals(new IntegerValue(1), denied.returnValue().orElseThrow());
    assertTrue(world.object(4).isPresent());

    VmState invalid = new VmState(Map.of(), 3);
    new MooVm()
        .execute(
            new MooCompiler().compile(MooParser.parse("return `recycle(#99) ! E_INVARG => 1';")),
            invalid,
            world,
            new BuiltinCatalog());
    assertEquals(new IntegerValue(1), invalid.returnValue().orElseThrow());
  }

  @Test
  void validReadsTheCurrentWorldSnapshotAndRaisesTheFrozenArgumentErrors() {
    WorldObject object =
        new WorldObject(0, "object", 0, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldTxn world = new WorldTxn(List.of(), List.of(object));
    VmState values = new VmState();

    new MooVm()
        .execute(
            new MooCompiler().compile(MooParser.parse("return {valid(#0), valid(#-1)};")),
            values,
            world,
            new BuiltinCatalog());

    assertEquals(
        new ListValue(List.of(new IntegerValue(1), new IntegerValue(0))),
        values.returnValue().orElseThrow());
    assertEquals(
        BuiltinCatalog.EffectClass.TRANSACTION_READ, new BuiltinCatalog().effectClass("valid"));

    String[] failures = {"return valid();", "return valid(1);", "return valid(#0, #0);"};
    ErrorValue[] errors = {ErrorValue.E_ARGS, ErrorValue.E_TYPE, ErrorValue.E_ARGS};
    for (int index = 0; index < failures.length; index++) {
      VmState state = new VmState();

      new MooVm()
          .execute(
              new MooCompiler().compile(MooParser.parse(failures[index])),
              state,
              world,
              new BuiltinCatalog());

      assertEquals(VmState.Outcome.ERRORED, state.outcome(), failures[index]);
      assertEquals(errors[index], state.uncaughtError().orElseThrow(), failures[index]);
    }
  }

  @Test
  void addVerbValidatesPacksAppendsAndReturnsOneBasedIndices() {
    WorldObject programmer =
        new WorldObject(0, "programmer", 6, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject target =
        new WorldObject(1, "target", 0, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject owner =
        new WorldObject(2, "owner", 0, 2, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldTxn world = new WorldTxn(List.of(), List.of(programmer, target, owner));
    VmState state = new VmState(Map.of(), 0);

    new MooVm()
        .execute(
            new MooCompiler()
                .compile(
                    MooParser.parse(
                        "return {"
                            + "add_verb(#1, {#0, \"rWxD\", \"  foo*bar\"}, "
                            + "{\"any\", \"any\", \"this\"}), "
                            + "add_verb(#1, {#0, \"\", \"second\"}, "
                            + "{\"none\", \"none\", \"none\"}), "
                            + "add_verb(#1, {#0, \"\", \"inside\"}, "
                            + "{\"this\", \"in\", \"this\"}), "
                            + "add_verb(#1, {#0, \"\", \"upon\"}, "
                            + "{\"this\", \"on\", \"this\"}), "
                            + "add_verb(#1, {#0, \"\", \"numeric\"}, "
                            + "{\"this\", \"14\", \"this\"}), "
                            + "add_verb(#1, {#0, \"\", \"hash_numeric\"}, "
                            + "{\"this\", \"#0\", \"this\"})};")),
            state,
            world,
            new BuiltinCatalog());

    assertEquals(
        new ListValue(
            List.of(
                new IntegerValue(1),
                new IntegerValue(2),
                new IntegerValue(3),
                new IntegerValue(4),
                new IntegerValue(5),
                new IntegerValue(6))),
        state.returnValue().orElseThrow());
    WorldVerb first = world.verb(1, 0).orElseThrow();
    assertEquals("foo*bar", first.names());
    assertEquals(0, first.owner());
    assertEquals(159, first.permissions());
    assertEquals(-2, first.preposition());
    assertEquals("second", world.verb(1, 1).orElseThrow().names());
    assertEquals(3, world.verb(1, 2).orElseThrow().preposition());
    assertEquals(4, world.verb(1, 3).orElseThrow().preposition());
    assertEquals(14, world.verb(1, 4).orElseThrow().preposition());
    assertEquals(0, world.verb(1, 5).orElseThrow().preposition());

    String[] failures = {
      "return add_verb(#1, {#99, \"x\", \"bad\"}, {\"this\", \"none\", \"this\"});",
      "return add_verb(#1, {#0, \"q\", \"bad\"}, {\"this\", \"none\", \"this\"});",
      "return add_verb(#1, {#0, \"x\", \"   \"}, {\"this\", \"none\", \"this\"});",
      "return add_verb(#1, {#0, \"x\", \"bad\"}, {\"other\", \"none\", \"this\"});",
      "return add_verb(#1, {#0, \"x\", \"bad\"}, {\"this\", \"near\", \"this\"});",
      "return add_verb(#1, 1, {\"this\", \"none\", \"this\"});"
    };
    ErrorValue[] errors = {
      ErrorValue.E_INVARG,
      ErrorValue.E_INVARG,
      ErrorValue.E_INVARG,
      ErrorValue.E_INVARG,
      ErrorValue.E_INVARG,
      ErrorValue.E_TYPE
    };
    for (int index = 0; index < failures.length; index++) {
      VmState failure = new VmState(Map.of(), 0);
      new MooVm()
          .execute(
              new MooCompiler().compile(MooParser.parse(failures[index])),
              failure,
              world,
              new BuiltinCatalog());
      assertEquals(errors[index], failure.uncaughtError().orElseThrow(), failures[index]);
    }
  }

  @Test
  void setVerbInfoAndArgsReplaceExistingLocalLoginMetadata() {
    String source = "return #1;";
    WorldVerb login = new WorldVerb("do_login_command", 9, 85, -2, source);
    WorldObject wizard =
        new WorldObject(0, "wizard", 6, 0, -1, -1, List.of(), List.of(), List.of(login), List.of());
    WorldObject oldOwner =
        new WorldObject(9, "old owner", 0, 9, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldTxn world = new WorldTxn(List.of(), List.of(wizard, oldOwner));
    VmState state = new VmState(Map.of(), 0);

    new MooVm()
        .execute(
            new MooCompiler()
                .compile(
                    MooParser.parse(
                        "return {"
                            + "set_verb_info(#0, \"do_login_command\", "
                            + "{#0, \"rxd\", \"do_login_command\"}), "
                            + "set_verb_args(#0, \"do_login_command\", "
                            + "{\"this\", \"none\", \"this\"})};")),
            state,
            world,
            new BuiltinCatalog());

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(List.of(new IntegerValue(0), new IntegerValue(0))),
        state.returnValue().orElseThrow());
    WorldVerb changed = world.verb(0, 0).orElseThrow();
    assertEquals("do_login_command", changed.names());
    assertEquals(0, changed.owner());
    assertEquals(173, changed.permissions());
    assertEquals(-1, changed.preposition());
    assertEquals(source, changed.programSource());
    assertEquals(
        BuiltinCatalog.EffectClass.TRANSACTION_WRITE,
        new BuiltinCatalog().effectClass("set_verb_info"));
    assertEquals(
        BuiltinCatalog.EffectClass.TRANSACTION_WRITE,
        new BuiltinCatalog().effectClass("set_verb_args"));
  }

  @Test
  void deleteVerbUsesLocalStringOrPositiveIntegerDescriptorsAndObjectWritePermission() {
    WorldVerb parentVerb = new WorldVerb("parent", 0, 4, -1, "return 3;");
    WorldVerb firstVerb = new WorldVerb("fi*rst", 0, 4, -1, "return 1;");
    WorldVerb secondVerb = new WorldVerb("second", 0, 4, -1, "return 2;");
    WorldObject programmer =
        new WorldObject(0, "programmer", 6, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject target =
        new WorldObject(
            1,
            "target",
            0,
            0,
            -1,
            2,
            List.of(),
            List.of(),
            List.of(firstVerb, secondVerb),
            List.of());
    WorldObject parent =
        new WorldObject(
            2, "parent", 0, 0, -1, -1, List.of(), List.of(1L), List.of(parentVerb), List.of());
    WorldTxn world = new WorldTxn(List.of(), List.of(programmer, target, parent));
    VmState state = new VmState(Map.of(), 0);

    new MooVm()
        .execute(
            new MooCompiler()
                .compile(
                    MooParser.parse("return {delete_verb(#1, 2), delete_verb(#1, \"first\")};")),
            state,
            world,
            new BuiltinCatalog());

    assertEquals(
        new ListValue(List.of(new IntegerValue(0), new IntegerValue(0))),
        state.returnValue().orElseThrow());
    assertTrue(world.object(1).orElseThrow().verbs().isEmpty());

    String[] failures = {"return delete_verb(#1, \"parent\");", "return delete_verb(#1, 0);"};
    ErrorValue[] errors = {ErrorValue.E_VERBNF, ErrorValue.E_INVARG};
    for (int index = 0; index < failures.length; index++) {
      VmState failure = new VmState(Map.of(), 0);
      new MooVm()
          .execute(
              new MooCompiler().compile(MooParser.parse(failures[index])),
              failure,
              world,
              new BuiltinCatalog());
      assertEquals(errors[index], failure.uncaughtError().orElseThrow(), failures[index]);
    }

    WorldObject ordinaryProgrammer =
        new WorldObject(3, "ordinary", 2, 3, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject protectedTarget =
        new WorldObject(
            4,
            "protected",
            0,
            4,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(new WorldVerb("verb", 4, 4, -1, "return 1;")),
            List.of());
    WorldTxn protectedWorld = new WorldTxn(List.of(), List.of(ordinaryProgrammer, protectedTarget));
    VmState denied = new VmState(Map.of(), 3);
    new MooVm()
        .execute(
            new MooCompiler().compile(MooParser.parse("return delete_verb(#4, 1);")),
            denied,
            protectedWorld,
            new BuiltinCatalog());
    assertEquals(ErrorValue.E_PERM, denied.uncaughtError().orElseThrow());
  }

  @Test
  void setVerbCodeCompilesBeforeLocalIndexedMutationAndReturnsDiagnosticLists() {
    WorldObject programmer =
        new WorldObject(0, "programmer", 2, 0, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldVerb local = new WorldVerb("local", 0, 0, -1, "return 1;");
    WorldVerb writable = new WorldVerb("writable", 9, 2, -1, "return 2;");
    WorldVerb inherited = new WorldVerb("inherited", 0, 2, -1, "return 3;");
    WorldObject target =
        new WorldObject(
            1, "target", 0, 1, -1, 2, List.of(), List.of(), List.of(local, writable), List.of());
    WorldObject parent =
        new WorldObject(
            2, "parent", 0, 2, -1, -1, List.of(), List.of(1L), List.of(inherited), List.of());
    WorldTxn world = new WorldTxn(List.of(), List.of(programmer, target, parent));
    VmState success = new VmState(Map.of(), 0);

    new MooVm()
        .execute(
            new MooCompiler()
                .compile(MooParser.parse("return set_verb_code(#1, 1, {\"return 7;\"});")),
            success,
            world,
            new BuiltinCatalog());

    assertEquals(new ListValue(List.of()), success.returnValue().orElseThrow());
    assertEquals("return 7;", world.verb(1, 0).orElseThrow().programSource());

    VmState writableSuccess = new VmState(Map.of(), 0);
    new MooVm()
        .execute(
            new MooCompiler()
                .compile(
                    MooParser.parse("return set_verb_code(#1, \"writable\", {\"return 8;\"});")),
            writableSuccess,
            world,
            new BuiltinCatalog());
    assertEquals(new ListValue(List.of()), writableSuccess.returnValue().orElseThrow());
    assertEquals("return 8;", world.verb(1, 1).orElseThrow().programSource());

    VmState diagnostic = new VmState(Map.of(), 0);
    new MooVm()
        .execute(
            new MooCompiler()
                .compile(MooParser.parse("return set_verb_code(#1, 1, {\"return 9;\", \"if\"});")),
            diagnostic,
            world,
            new BuiltinCatalog());
    assertTrue(
        diagnostic.returnValue().orElseThrow() instanceof ListValue errors
            && errors.size() == 1
            && errors.elements().getFirst() instanceof StringValue);
    assertEquals("return 7;", world.verb(1, 0).orElseThrow().programSource());

    String[] failures = {
      "return set_verb_code(#1, \"inherited\", {\"return 4;\"});",
      "return set_verb_code(#1, 1, {1});"
    };
    ErrorValue[] errors = {ErrorValue.E_VERBNF, ErrorValue.E_TYPE};
    for (int index = 0; index < failures.length; index++) {
      VmState failure = new VmState(Map.of(), 0);
      new MooVm()
          .execute(
              new MooCompiler().compile(MooParser.parse(failures[index])),
              failure,
              world,
              new BuiltinCatalog());
      assertEquals(errors[index], failure.uncaughtError().orElseThrow(), failures[index]);
    }

    WorldObject nonProgrammer =
        new WorldObject(
            3, "not programmer", 0, 3, -1, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject writableTarget =
        new WorldObject(
            4,
            "target",
            0,
            4,
            -1,
            -1,
            List.of(),
            List.of(),
            List.of(new WorldVerb("verb", 4, 2, -1, "return 1;")),
            List.of());
    WorldTxn deniedWorld = new WorldTxn(List.of(), List.of(nonProgrammer, writableTarget));
    VmState denied = new VmState(Map.of(), 3);
    new MooVm()
        .execute(
            new MooCompiler()
                .compile(MooParser.parse("return set_verb_code(#4, 1, {\"return 2;\"});")),
            denied,
            deniedWorld,
            new BuiltinCatalog());
    assertEquals(ErrorValue.E_PERM, denied.uncaughtError().orElseThrow());
  }
}
