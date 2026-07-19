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
import moo.syntax.MooUnparser;
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
    byte[] source = "return 1 + 1;".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.Binary addition =
        assertInstanceOf(Ast.Binary.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 13, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 12, 1, 8), addition.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    VmState state = new VmState();

    assertEquals("0 PUSH_INTEGER 1\n1 PUSH_INTEGER 1\n2 ADD\n3 RETURN", program.disassemble());
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
  void comparesErrorValuesByTheirNumericCodes() {
    byte[] source = "return E_NONE < E_TYPE;".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.Binary comparison =
        assertInstanceOf(Ast.Binary.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 23, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 22, 1, 8), comparison.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        "0 PUSH_ERROR E_NONE\n1 PUSH_ERROR E_TYPE\n2 LESS_THAN\n3 RETURN",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(1), state.returnValue().orElseThrow());
  }

  @Test
  void executesIfBranchThroughTheCompleteControlFlowPipeline() {
    byte[] source = "if (1) return 2; endif".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.If ifStatement = assertInstanceOf(Ast.If.class, syntax.statements().getFirst());
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, ifStatement.body().getFirst());
    assertEquals(new Ast.SourceSpan(0, 22, 1, 1), ifStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 16, 1, 8), returnStatement.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_INTEGER 1
        1 JUMP_IF_FALSE 5
        2 PUSH_INTEGER 2
        3 RETURN
        4 JUMP 5
        5 PUSH_INTEGER 0
        6 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(2), state.returnValue().orElseThrow());
  }

  @Test
  void executesTernaryThroughTheCompleteControlFlowPipeline() {
    byte[] source = "return 1 ? 2 | 3;".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.Ternary ternary =
        assertInstanceOf(Ast.Ternary.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 17, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 16, 1, 8), ternary.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_INTEGER 1
        1 JUMP_IF_FALSE 4
        2 PUSH_INTEGER 2
        3 JUMP 5
        4 PUSH_INTEGER 3
        5 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(2), state.returnValue().orElseThrow());
  }

  @Test
  void iteratesStringBytesThroughTheCompleteControlFlowPipeline() {
    byte[] source =
        """
        x = {};
        for i in ("12345")
          x = {@x, i};
        endfor
        return x;"""
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.For forStatement = assertInstanceOf(Ast.For.class, syntax.statements().get(1));
    assertEquals("i", forStatement.variable());
    assertInstanceOf(Ast.StringLiteral.class, forStatement.iterable());
    assertInstanceOf(Ast.ExpressionStatement.class, forStatement.body().getFirst());
    assertEquals(
        """
        x = {};
        for i in ("12345")
          x = {@x, i};
        endfor
        return x;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 DUP
        2 STORE_LOCAL x
        3 POP
        4 PUSH_STRING 12345
        5 ITERATE 15 i
        6 BUILD_LIST 0
        7 LOAD_LOCAL x
        8 LIST_EXTEND
        9 LOAD_LOCAL i
        10 LIST_APPEND
        11 DUP
        12 STORE_LOCAL x
        13 POP
        14 JUMP 5
        15 LEAVE_LOOP 5
        16 LOAD_LOCAL x
        17 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new StringValue(new byte[] {'1'}),
                new StringValue(new byte[] {'2'}),
                new StringValue(new byte[] {'3'}),
                new StringValue(new byte[] {'4'}),
                new StringValue(new byte[] {'5'}))),
        state.returnValue().orElseThrow());
  }

  @Test
  void bindsStringBytesAndIndexesThroughTheCompleteControlFlowPipeline() {
    byte[] source =
        """
        x = {};
        for i, j in ("12")
          x = {@x, {i, j}};
        endfor
        return x;"""
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.For forStatement = assertInstanceOf(Ast.For.class, syntax.statements().get(1));
    assertEquals("i", forStatement.variable());
    assertEquals("j", forStatement.indexVariable().orElseThrow());
    assertInstanceOf(Ast.StringLiteral.class, forStatement.iterable());
    assertInstanceOf(Ast.ExpressionStatement.class, forStatement.body().getFirst());
    assertEquals(
        """
        x = {};
        for i, j in ("12")
          x = {@x, {i, j}};
        endfor
        return x;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 DUP
        2 STORE_LOCAL x
        3 POP
        4 PUSH_STRING 12
        5 ITERATE 19 i,j
        6 BUILD_LIST 0
        7 LOAD_LOCAL x
        8 LIST_EXTEND
        9 BUILD_LIST 0
        10 LOAD_LOCAL i
        11 LIST_APPEND
        12 LOAD_LOCAL j
        13 LIST_APPEND
        14 LIST_APPEND
        15 DUP
        16 STORE_LOCAL x
        17 POP
        18 JUMP 5
        19 LEAVE_LOOP 5
        20 LOAD_LOCAL x
        21 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new ListValue(
                    List.of(new StringValue(new byte[] {'1'}), new IntegerValue(1))),
                new ListValue(
                    List.of(new StringValue(new byte[] {'2'}), new IntegerValue(2))))),
        state.returnValue().orElseThrow());
  }

  @Test
  void iteratesMapValuesInKeyOrderThroughTheCompleteControlFlowPipeline() {
    byte[] source =
        """
        x = {};
        for v in (["b" -> 2, "a" -> 1])
          x = {@x, v};
        endfor
        return x;"""
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.For forStatement = assertInstanceOf(Ast.For.class, syntax.statements().get(1));
    assertEquals("v", forStatement.variable());
    assertTrue(forStatement.indexVariable().isEmpty());
    assertInstanceOf(Ast.MapLiteral.class, forStatement.iterable());
    assertInstanceOf(Ast.ExpressionStatement.class, forStatement.body().getFirst());
    assertEquals(
        """
        x = {};
        for v in (["b" -> 2, "a" -> 1])
          x = {@x, v};
        endfor
        return x;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 DUP
        2 STORE_LOCAL x
        3 POP
        4 PUSH_INTEGER 2
        5 PUSH_STRING b
        6 PUSH_INTEGER 1
        7 PUSH_STRING a
        8 BUILD_MAP 2
        9 ITERATE 19 v
        10 BUILD_LIST 0
        11 LOAD_LOCAL x
        12 LIST_EXTEND
        13 LOAD_LOCAL v
        14 LIST_APPEND
        15 DUP
        16 STORE_LOCAL x
        17 POP
        18 JUMP 9
        19 LEAVE_LOOP 9
        20 LOAD_LOCAL x
        21 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(List.of(new IntegerValue(1), new IntegerValue(2))),
        state.returnValue().orElseThrow());
  }

  @Test
  void bindsMapValuesAndKeysThroughTheCompleteControlFlowPipeline() {
    byte[] source =
        """
        x = {};
        for v, k in (["b" -> 2, "a" -> 1])
          x = {@x, {v, k}};
        endfor
        return x;"""
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.For forStatement = assertInstanceOf(Ast.For.class, syntax.statements().get(1));
    assertEquals("v", forStatement.variable());
    assertEquals("k", forStatement.indexVariable().orElseThrow());
    assertInstanceOf(Ast.MapLiteral.class, forStatement.iterable());
    assertInstanceOf(Ast.ExpressionStatement.class, forStatement.body().getFirst());
    assertEquals(
        """
        x = {};
        for v, k in (["b" -> 2, "a" -> 1])
          x = {@x, {v, k}};
        endfor
        return x;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 DUP
        2 STORE_LOCAL x
        3 POP
        4 PUSH_INTEGER 2
        5 PUSH_STRING b
        6 PUSH_INTEGER 1
        7 PUSH_STRING a
        8 BUILD_MAP 2
        9 ITERATE 23 v,k
        10 BUILD_LIST 0
        11 LOAD_LOCAL x
        12 LIST_EXTEND
        13 BUILD_LIST 0
        14 LOAD_LOCAL v
        15 LIST_APPEND
        16 LOAD_LOCAL k
        17 LIST_APPEND
        18 LIST_APPEND
        19 DUP
        20 STORE_LOCAL x
        21 POP
        22 JUMP 9
        23 LEAVE_LOOP 9
        24 LOAD_LOCAL x
        25 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new ListValue(
                    List.of(new IntegerValue(1), new StringValue(new byte[] {'a'}))),
                new ListValue(
                    List.of(new IntegerValue(2), new StringValue(new byte[] {'b'}))))),
        state.returnValue().orElseThrow());
  }

  @Test
  void breaksNamedForLoopThroughTheCompleteControlFlowPipeline() {
    byte[] source =
        """
        x = {};
        for i in ({1, 2, 3})
          if (i > 2)
            break i;
          endif
          x = {@x, i};
        endfor
        return x;"""
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.For forStatement = assertInstanceOf(Ast.For.class, syntax.statements().get(1));
    Ast.If ifStatement = assertInstanceOf(Ast.If.class, forStatement.body().getFirst());
    Ast.Break breakStatement = assertInstanceOf(Ast.Break.class, ifStatement.body().getFirst());
    assertEquals("i", breakStatement.loopVariable().orElseThrow());
    assertEquals(
        """
        x = {};
        for i in ({1, 2, 3})
          if (i > 2)
            break i;
          endif
          x = {@x, i};
        endfor
        return x;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 DUP
        2 STORE_LOCAL x
        3 POP
        4 BUILD_LIST 0
        5 PUSH_INTEGER 1
        6 LIST_APPEND
        7 PUSH_INTEGER 2
        8 LIST_APPEND
        9 PUSH_INTEGER 3
        10 LIST_APPEND
        11 ITERATE 27 i
        12 LOAD_LOCAL i
        13 PUSH_INTEGER 2
        14 GREATER_THAN
        15 JUMP_IF_FALSE 18
        16 JUMP 27
        17 JUMP 18
        18 BUILD_LIST 0
        19 LOAD_LOCAL x
        20 LIST_EXTEND
        21 LOAD_LOCAL i
        22 LIST_APPEND
        23 DUP
        24 STORE_LOCAL x
        25 POP
        26 JUMP 11
        27 LEAVE_LOOP 11
        28 LOAD_LOCAL x
        29 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(List.of(new IntegerValue(1), new IntegerValue(2))),
        state.returnValue().orElseThrow());
  }

  @Test
  void breaksForLoopNamedByItsSecondVariableThroughTheCompleteControlFlowPipeline() {
    byte[] source =
        """
        x = {};
        for i, j in ({"1", "2", "3", "4", "5"})
          if (j > 2)
            break j;
          endif
          x = {@x, i};
        endfor
        return x;"""
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.For forStatement = assertInstanceOf(Ast.For.class, syntax.statements().get(1));
    assertEquals("j", forStatement.indexVariable().orElseThrow());
    Ast.If ifStatement = assertInstanceOf(Ast.If.class, forStatement.body().getFirst());
    Ast.Break breakStatement = assertInstanceOf(Ast.Break.class, ifStatement.body().getFirst());
    assertEquals("j", breakStatement.loopVariable().orElseThrow());
    assertEquals(
        """
        x = {};
        for i, j in ({"1", "2", "3", "4", "5"})
          if (j > 2)
            break j;
          endif
          x = {@x, i};
        endfor
        return x;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 DUP
        2 STORE_LOCAL x
        3 POP
        4 BUILD_LIST 0
        5 PUSH_STRING 1
        6 LIST_APPEND
        7 PUSH_STRING 2
        8 LIST_APPEND
        9 PUSH_STRING 3
        10 LIST_APPEND
        11 PUSH_STRING 4
        12 LIST_APPEND
        13 PUSH_STRING 5
        14 LIST_APPEND
        15 ITERATE 31 i,j
        16 LOAD_LOCAL j
        17 PUSH_INTEGER 2
        18 GREATER_THAN
        19 JUMP_IF_FALSE 22
        20 JUMP 31
        21 JUMP 22
        22 BUILD_LIST 0
        23 LOAD_LOCAL x
        24 LIST_EXTEND
        25 LOAD_LOCAL i
        26 LIST_APPEND
        27 DUP
        28 STORE_LOCAL x
        29 POP
        30 JUMP 15
        31 LEAVE_LOOP 15
        32 LOAD_LOCAL x
        33 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new StringValue(new byte[] {'1'}), new StringValue(new byte[] {'2'}))),
        state.returnValue().orElseThrow());
  }

  @Test
  void continuesNamedForLoopThroughTheCompleteControlFlowPipeline() {
    byte[] source =
        """
        x = {};
        for i in ({1, 2, 3, 4, 5})
          if (i < 3)
            continue i;
          endif
          x = {@x, i};
        endfor
        return x;"""
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.For forStatement = assertInstanceOf(Ast.For.class, syntax.statements().get(1));
    Ast.If ifStatement = assertInstanceOf(Ast.If.class, forStatement.body().getFirst());
    Ast.Continue continueStatement =
        assertInstanceOf(Ast.Continue.class, ifStatement.body().getFirst());
    assertEquals("i", continueStatement.loopVariable().orElseThrow());
    assertEquals(
        """
        x = {};
        for i in ({1, 2, 3, 4, 5})
          if (i < 3)
            continue i;
          endif
          x = {@x, i};
        endfor
        return x;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 DUP
        2 STORE_LOCAL x
        3 POP
        4 BUILD_LIST 0
        5 PUSH_INTEGER 1
        6 LIST_APPEND
        7 PUSH_INTEGER 2
        8 LIST_APPEND
        9 PUSH_INTEGER 3
        10 LIST_APPEND
        11 PUSH_INTEGER 4
        12 LIST_APPEND
        13 PUSH_INTEGER 5
        14 LIST_APPEND
        15 ITERATE 31 i
        16 LOAD_LOCAL i
        17 PUSH_INTEGER 3
        18 LESS_THAN
        19 JUMP_IF_FALSE 22
        20 JUMP 15
        21 JUMP 22
        22 BUILD_LIST 0
        23 LOAD_LOCAL x
        24 LIST_EXTEND
        25 LOAD_LOCAL i
        26 LIST_APPEND
        27 DUP
        28 STORE_LOCAL x
        29 POP
        30 JUMP 15
        31 LEAVE_LOOP 15
        32 LOAD_LOCAL x
        33 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(List.of(new IntegerValue(3), new IntegerValue(4), new IntegerValue(5))),
        state.returnValue().orElseThrow());
  }

  @Test
  void comparesStringOrderingCaseInsensitively() {
    byte[] source = "return \"a\" < \"B\";".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.Binary comparison =
        assertInstanceOf(Ast.Binary.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 17, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 16, 1, 8), comparison.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals("0 PUSH_STRING a\n1 PUSH_STRING B\n2 LESS_THAN\n3 RETURN", program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(1), state.returnValue().orElseThrow());
  }

  @Test
  void concatenatesListsThroughTheCompleteCollectionPipeline() {
    byte[] source = "return {1, 2} + {3, 4};".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.Binary addition =
        assertInstanceOf(Ast.Binary.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 23, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 22, 1, 8), addition.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 PUSH_INTEGER 1
        2 LIST_APPEND
        3 PUSH_INTEGER 2
        4 LIST_APPEND
        5 BUILD_LIST 0
        6 PUSH_INTEGER 3
        7 LIST_APPEND
        8 PUSH_INTEGER 4
        9 LIST_APPEND
        10 ADD
        11 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new IntegerValue(1),
                new IntegerValue(2),
                new IntegerValue(3),
                new IntegerValue(4))),
        state.returnValue().orElseThrow());
  }

  @Test
  void appendsAValueToAListThroughTheCompleteCollectionPipeline() {
    byte[] source = "return {1, 2} + 3;".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.Binary addition =
        assertInstanceOf(Ast.Binary.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 18, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 17, 1, 8), addition.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 PUSH_INTEGER 1
        2 LIST_APPEND
        3 PUSH_INTEGER 2
        4 LIST_APPEND
        5 PUSH_INTEGER 3
        6 ADD
        7 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(List.of(new IntegerValue(1), new IntegerValue(2), new IntegerValue(3))),
        state.returnValue().orElseThrow());
  }

  @Test
  void bindsARestVariableThroughTheCompleteScatterPipeline() {
    byte[] source =
        "{a, b, @c} = {1, 2, 3, 4, 5}; return {a, b, c};"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement assignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().getFirst());
    Ast.Assignment assignment =
        assertInstanceOf(Ast.Assignment.class, assignmentStatement.expression());
    assertInstanceOf(Ast.ScatterTarget.class, assignment.target());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(
                new IntegerValue(1),
                new IntegerValue(2),
                new ListValue(
                    List.of(
                        new IntegerValue(3),
                        new IntegerValue(4),
                        new IntegerValue(5))))),
        state.returnValue().orElseThrow());
  }

  @Test
  void bindsAPresentOptionalVariableThroughTheCompleteScatterPipeline() {
    byte[] source =
        "{a, ?b, c} = {1, 2, 3}; return {a, b, c};"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement assignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().getFirst());
    Ast.Assignment assignment =
        assertInstanceOf(Ast.Assignment.class, assignmentStatement.expression());
    assertInstanceOf(Ast.ScatterTarget.class, assignment.target());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(new IntegerValue(1), new IntegerValue(2), new IntegerValue(3))),
        state.returnValue().orElseThrow());
  }

  @Test
  void bindsADefaultForAMissingOptionalThroughTheCompleteScatterPipeline() {
    byte[] source =
        "{a, ?b = 99, c} = {1, 2}; return {a, b, c};"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement assignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().getFirst());
    Ast.Assignment assignment =
        assertInstanceOf(Ast.Assignment.class, assignmentStatement.expression());
    assertInstanceOf(Ast.ScatterTarget.class, assignment.target());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(new IntegerValue(1), new IntegerValue(99), new IntegerValue(2))),
        state.returnValue().orElseThrow());
  }

  @Test
  void evaluatesMapValuesBeforeKeysThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "trace = 0; mapping = [(trace = 1) -> (trace = 2)]; return trace;"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement mappingStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().get(1));
    Ast.Assignment mappingAssignment =
        assertInstanceOf(Ast.Assignment.class, mappingStatement.expression());
    Ast.MapLiteral map = assertInstanceOf(Ast.MapLiteral.class, mappingAssignment.value());
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().get(2));
    assertEquals(new Ast.SourceSpan(21, 49, 1, 22), map.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(51, 64, 1, 52), returnStatement.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_INTEGER 0
        1 DUP
        2 STORE_LOCAL trace
        3 POP
        4 PUSH_INTEGER 2
        5 DUP
        6 STORE_LOCAL trace
        7 PUSH_INTEGER 1
        8 DUP
        9 STORE_LOCAL trace
        10 BUILD_MAP 1
        11 DUP
        12 STORE_LOCAL mapping
        13 POP
        14 LOAD_LOCAL trace
        15 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(1), state.returnValue().orElseThrow());
  }

  @Test
  void findsAValueInAMapThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "return 2 in [\"a\" -> 1, \"b\" -> 2, \"c\" -> 3];"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.Binary membership =
        assertInstanceOf(Ast.Binary.class, returnStatement.value().orElseThrow());
    Ast.MapLiteral map = assertInstanceOf(Ast.MapLiteral.class, membership.right());
    assertEquals(new Ast.SourceSpan(0, 43, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 42, 1, 8), membership.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(12, 42, 1, 13), map.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_INTEGER 2
        1 PUSH_INTEGER 1
        2 PUSH_STRING a
        3 PUSH_INTEGER 2
        4 PUSH_STRING b
        5 PUSH_INTEGER 3
        6 PUSH_STRING c
        7 BUILD_MAP 3
        8 IN
        9 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(2), state.returnValue().orElseThrow());
  }

  @Test
  void returnsAnInclusiveListRangeThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "return {\"one\", \"two\", \"three\"}[3..3];"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.RangeAccess range =
        assertInstanceOf(Ast.RangeAccess.class, returnStatement.value().orElseThrow());
    Ast.ListLiteral list = assertInstanceOf(Ast.ListLiteral.class, range.collection());
    Ast.IntegerLiteral start = assertInstanceOf(Ast.IntegerLiteral.class, range.start());
    Ast.IntegerLiteral end = assertInstanceOf(Ast.IntegerLiteral.class, range.end());
    assertEquals(new Ast.SourceSpan(0, 37, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 36, 1, 8), range.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 30, 1, 8), list.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(31, 32, 1, 32), start.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(34, 35, 1, 35), end.span().orElseThrow());
    assertEquals(
        "return {\"one\", \"two\", \"three\"}[3..3];", MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 PUSH_STRING one
        2 LIST_APPEND
        3 PUSH_STRING two
        4 LIST_APPEND
        5 PUSH_STRING three
        6 LIST_APPEND
        7 ENTER_INDEX
        8 PUSH_INTEGER 3
        9 PUSH_INTEGER 3
        10 RANGE
        11 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(
            List.of(new StringValue("three".getBytes(StandardCharsets.ISO_8859_1)))),
        state.returnValue().orElseThrow());
  }

  @Test
  void returnsAnEmptyListForAnInvertedRangeThroughTheCompleteCollectionPipeline() {
    byte[] source = "return {1, 2, 3}[17..12];".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.RangeAccess range =
        assertInstanceOf(Ast.RangeAccess.class, returnStatement.value().orElseThrow());
    Ast.ListLiteral list = assertInstanceOf(Ast.ListLiteral.class, range.collection());
    Ast.IntegerLiteral start = assertInstanceOf(Ast.IntegerLiteral.class, range.start());
    Ast.IntegerLiteral end = assertInstanceOf(Ast.IntegerLiteral.class, range.end());
    assertEquals(new Ast.SourceSpan(0, 25, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 24, 1, 8), range.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 16, 1, 8), list.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(17, 19, 1, 18), start.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(21, 23, 1, 22), end.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 PUSH_INTEGER 1
        2 LIST_APPEND
        3 PUSH_INTEGER 2
        4 LIST_APPEND
        5 PUSH_INTEGER 3
        6 LIST_APPEND
        7 ENTER_INDEX
        8 PUSH_INTEGER 17
        9 PUSH_INTEGER 12
        10 RANGE
        11 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new ListValue(List.of()), state.returnValue().orElseThrow());
  }

  @Test
  void replacesAListRangeThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "l = {1, 2, 3}; l[2..3] = {6, 7, 8, 9}; return l;"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement initialAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().getFirst());
    Ast.Assignment initialAssignment =
        assertInstanceOf(Ast.Assignment.class, initialAssignmentStatement.expression());
    Ast.ListLiteral initialList = assertInstanceOf(Ast.ListLiteral.class, initialAssignment.value());
    Ast.ExpressionStatement rangeAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().get(1));
    Ast.Assignment rangeAssignment =
        assertInstanceOf(Ast.Assignment.class, rangeAssignmentStatement.expression());
    Ast.ListLiteral replacement = assertInstanceOf(Ast.ListLiteral.class, rangeAssignment.value());
    Ast.Return returnStatement = assertInstanceOf(Ast.Return.class, syntax.statements().get(2));
    assertEquals("RangeTarget", rangeAssignment.target().getClass().getSimpleName());
    assertEquals(new Ast.SourceSpan(4, 13, 1, 5), initialList.span().orElseThrow());
    assertEquals(
        new Ast.SourceSpan(15, 38, 1, 16), rangeAssignmentStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(25, 37, 1, 26), replacement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(39, 48, 1, 40), returnStatement.span().orElseThrow());
    assertEquals(
        """
        l = {1, 2, 3};
        l[2..3] = {6, 7, 8, 9};
        return l;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 PUSH_INTEGER 1
        2 LIST_APPEND
        3 PUSH_INTEGER 2
        4 LIST_APPEND
        5 PUSH_INTEGER 3
        6 LIST_APPEND
        7 DUP
        8 STORE_LOCAL l
        9 POP
        10 LOAD_LOCAL l
        11 ENTER_INDEX
        12 PUSH_INTEGER 2
        13 PUSH_INTEGER 3
        14 BUILD_LIST 0
        15 PUSH_INTEGER 6
        16 LIST_APPEND
        17 PUSH_INTEGER 7
        18 LIST_APPEND
        19 PUSH_INTEGER 8
        20 LIST_APPEND
        21 PUSH_INTEGER 9
        22 LIST_APPEND
        23 SET_RANGE_LOCAL l
        24 POP
        25 LOAD_LOCAL l
        26 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertTrue(state.uncaughtError().isEmpty());
    assertEquals(
        new ListValue(
            List.of(
                new IntegerValue(1),
                new IntegerValue(6),
                new IntegerValue(7),
                new IntegerValue(8),
                new IntegerValue(9))),
        state.returnValue().orElseThrow());
  }

  @Test
  void insertsAtAnInvertedListRangeThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "l = {1, 6, 7, 8, 9}; l[2..1] = {10, \"foo\"}; return l;"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement initialAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().getFirst());
    Ast.Assignment initialAssignment =
        assertInstanceOf(Ast.Assignment.class, initialAssignmentStatement.expression());
    Ast.ListLiteral initialList = assertInstanceOf(Ast.ListLiteral.class, initialAssignment.value());
    Ast.ExpressionStatement rangeAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().get(1));
    Ast.Assignment rangeAssignment =
        assertInstanceOf(Ast.Assignment.class, rangeAssignmentStatement.expression());
    Ast.ListLiteral replacement = assertInstanceOf(Ast.ListLiteral.class, rangeAssignment.value());
    Ast.Return returnStatement = assertInstanceOf(Ast.Return.class, syntax.statements().get(2));
    assertEquals("RangeTarget", rangeAssignment.target().getClass().getSimpleName());
    assertEquals(new Ast.SourceSpan(4, 19, 1, 5), initialList.span().orElseThrow());
    assertEquals(
        new Ast.SourceSpan(21, 43, 1, 22), rangeAssignmentStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(31, 42, 1, 32), replacement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(44, 53, 1, 45), returnStatement.span().orElseThrow());
    assertEquals(
        """
        l = {1, 6, 7, 8, 9};
        l[2..1] = {10, "foo"};
        return l;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 PUSH_INTEGER 1
        2 LIST_APPEND
        3 PUSH_INTEGER 6
        4 LIST_APPEND
        5 PUSH_INTEGER 7
        6 LIST_APPEND
        7 PUSH_INTEGER 8
        8 LIST_APPEND
        9 PUSH_INTEGER 9
        10 LIST_APPEND
        11 DUP
        12 STORE_LOCAL l
        13 POP
        14 LOAD_LOCAL l
        15 ENTER_INDEX
        16 PUSH_INTEGER 2
        17 PUSH_INTEGER 1
        18 BUILD_LIST 0
        19 PUSH_INTEGER 10
        20 LIST_APPEND
        21 PUSH_STRING foo
        22 LIST_APPEND
        23 SET_RANGE_LOCAL l
        24 POP
        25 LOAD_LOCAL l
        26 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertTrue(state.uncaughtError().isEmpty());
    assertEquals(
        new ListValue(
            List.of(
                new IntegerValue(1),
                new IntegerValue(10),
                new StringValue("foo".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(6),
                new IntegerValue(7),
                new IntegerValue(8),
                new IntegerValue(9))),
        state.returnValue().orElseThrow());
  }

  @Test
  void assignsANestedStringRangeThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "l = {1, 10, \"foo\", 6, 7, 8, 9}; l[3][2..$] = \"u\"; return l;"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement initialAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().getFirst());
    Ast.Assignment initialAssignment =
        assertInstanceOf(Ast.Assignment.class, initialAssignmentStatement.expression());
    Ast.ListLiteral initialList = assertInstanceOf(Ast.ListLiteral.class, initialAssignment.value());
    Ast.ExpressionStatement rangeAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().get(1));
    Ast.Assignment rangeAssignment =
        assertInstanceOf(Ast.Assignment.class, rangeAssignmentStatement.expression());
    Ast.RangeTarget target = assertInstanceOf(Ast.RangeTarget.class, rangeAssignment.target());
    Ast.IndexAccess parent = assertInstanceOf(Ast.IndexAccess.class, target.collection());
    Ast.IntegerLiteral parentIndex = assertInstanceOf(Ast.IntegerLiteral.class, parent.index());
    Ast.IntegerLiteral start = assertInstanceOf(Ast.IntegerLiteral.class, target.start());
    Ast.LastIndex end = assertInstanceOf(Ast.LastIndex.class, target.end());
    Ast.StringLiteral replacement =
        assertInstanceOf(Ast.StringLiteral.class, rangeAssignment.value());
    Ast.Return returnStatement = assertInstanceOf(Ast.Return.class, syntax.statements().get(2));
    assertInstanceOf(Ast.Identifier.class, parent.collection());
    assertEquals(new Ast.SourceSpan(4, 30, 1, 5), initialList.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(32, 49, 1, 33), rangeAssignmentStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(32, 36, 1, 33), parent.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(34, 35, 1, 35), parentIndex.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(37, 38, 1, 38), start.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(40, 41, 1, 41), end.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(45, 48, 1, 46), replacement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(50, 59, 1, 51), returnStatement.span().orElseThrow());
    assertEquals(
        """
        l = {1, 10, "foo", 6, 7, 8, 9};
        l[3][2..$] = "u";
        return l;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 PUSH_INTEGER 1
        2 LIST_APPEND
        3 PUSH_INTEGER 10
        4 LIST_APPEND
        5 PUSH_STRING foo
        6 LIST_APPEND
        7 PUSH_INTEGER 6
        8 LIST_APPEND
        9 PUSH_INTEGER 7
        10 LIST_APPEND
        11 PUSH_INTEGER 8
        12 LIST_APPEND
        13 PUSH_INTEGER 9
        14 LIST_APPEND
        15 DUP
        16 STORE_LOCAL l
        17 POP
        18 LOAD_LOCAL l
        19 ENTER_INDEX
        20 PUSH_INTEGER 3
        21 INDEX 1
        22 ENTER_INDEX
        23 PUSH_INTEGER 2
        24 LAST
        25 PUSH_STRING u
        26 SET_RANGE_LOCAL 1 l
        27 POP
        28 LOAD_LOCAL l
        29 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertTrue(state.uncaughtError().isEmpty());
    assertEquals(
        new ListValue(
            List.of(
                new IntegerValue(1),
                new IntegerValue(10),
                new StringValue("fu".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(6),
                new IntegerValue(7),
                new IntegerValue(8),
                new IntegerValue(9))),
        state.returnValue().orElseThrow());
  }

  @Test
  void assignsAnInvertedListRangeThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "t = {1, 2, 3, 4, 5, 6, 7}; t[7..1] = {\"a\", \"b\", \"c\"}; return t;"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement initialAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().getFirst());
    Ast.Assignment initialAssignment =
        assertInstanceOf(Ast.Assignment.class, initialAssignmentStatement.expression());
    Ast.ListLiteral initialList = assertInstanceOf(Ast.ListLiteral.class, initialAssignment.value());
    Ast.ExpressionStatement rangeAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().get(1));
    Ast.Assignment rangeAssignment =
        assertInstanceOf(Ast.Assignment.class, rangeAssignmentStatement.expression());
    Ast.RangeTarget target = assertInstanceOf(Ast.RangeTarget.class, rangeAssignment.target());
    Ast.IntegerLiteral start = assertInstanceOf(Ast.IntegerLiteral.class, target.start());
    Ast.IntegerLiteral end = assertInstanceOf(Ast.IntegerLiteral.class, target.end());
    Ast.ListLiteral replacement = assertInstanceOf(Ast.ListLiteral.class, rangeAssignment.value());
    Ast.Return returnStatement = assertInstanceOf(Ast.Return.class, syntax.statements().get(2));
    assertInstanceOf(Ast.Identifier.class, target.collection());
    assertEquals(new Ast.SourceSpan(4, 25, 1, 5), initialList.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(27, 53, 1, 28), rangeAssignmentStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(29, 30, 1, 30), start.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(32, 33, 1, 33), end.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(37, 52, 1, 38), replacement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(54, 63, 1, 55), returnStatement.span().orElseThrow());
    assertEquals(
        """
        t = {1, 2, 3, 4, 5, 6, 7};
        t[7..1] = {"a", "b", "c"};
        return t;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 PUSH_INTEGER 1
        2 LIST_APPEND
        3 PUSH_INTEGER 2
        4 LIST_APPEND
        5 PUSH_INTEGER 3
        6 LIST_APPEND
        7 PUSH_INTEGER 4
        8 LIST_APPEND
        9 PUSH_INTEGER 5
        10 LIST_APPEND
        11 PUSH_INTEGER 6
        12 LIST_APPEND
        13 PUSH_INTEGER 7
        14 LIST_APPEND
        15 DUP
        16 STORE_LOCAL t
        17 POP
        18 LOAD_LOCAL t
        19 ENTER_INDEX
        20 PUSH_INTEGER 7
        21 PUSH_INTEGER 1
        22 BUILD_LIST 0
        23 PUSH_STRING a
        24 LIST_APPEND
        25 PUSH_STRING b
        26 LIST_APPEND
        27 PUSH_STRING c
        28 LIST_APPEND
        29 SET_RANGE_LOCAL t
        30 POP
        31 LOAD_LOCAL t
        32 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertTrue(state.uncaughtError().isEmpty());
    assertEquals(
        new ListValue(
            List.of(
                new IntegerValue(1),
                new IntegerValue(2),
                new IntegerValue(3),
                new IntegerValue(4),
                new IntegerValue(5),
                new IntegerValue(6),
                new StringValue("a".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("b".getBytes(StandardCharsets.ISO_8859_1)),
                new StringValue("c".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(2),
                new IntegerValue(3),
                new IntegerValue(4),
                new IntegerValue(5),
                new IntegerValue(6),
                new IntegerValue(7))),
        state.returnValue().orElseThrow());
  }

  @Test
  void appendsAStringRangeThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "s = \"foobar\"; s[7..12] = \"baz\"; return s;"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement initialAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().getFirst());
    Ast.Assignment initialAssignment =
        assertInstanceOf(Ast.Assignment.class, initialAssignmentStatement.expression());
    Ast.StringLiteral initialString =
        assertInstanceOf(Ast.StringLiteral.class, initialAssignment.value());
    Ast.ExpressionStatement rangeAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().get(1));
    Ast.Assignment rangeAssignment =
        assertInstanceOf(Ast.Assignment.class, rangeAssignmentStatement.expression());
    Ast.RangeTarget target = assertInstanceOf(Ast.RangeTarget.class, rangeAssignment.target());
    Ast.IntegerLiteral start = assertInstanceOf(Ast.IntegerLiteral.class, target.start());
    Ast.IntegerLiteral end = assertInstanceOf(Ast.IntegerLiteral.class, target.end());
    Ast.StringLiteral replacement =
        assertInstanceOf(Ast.StringLiteral.class, rangeAssignment.value());
    Ast.Return returnStatement = assertInstanceOf(Ast.Return.class, syntax.statements().get(2));
    assertInstanceOf(Ast.Identifier.class, target.collection());
    assertEquals(new Ast.SourceSpan(4, 12, 1, 5), initialString.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(14, 31, 1, 15), rangeAssignmentStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(16, 17, 1, 17), start.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(19, 21, 1, 20), end.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(25, 30, 1, 26), replacement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(32, 41, 1, 33), returnStatement.span().orElseThrow());
    assertEquals(
        """
        s = "foobar";
        s[7..12] = "baz";
        return s;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_STRING foobar
        1 DUP
        2 STORE_LOCAL s
        3 POP
        4 LOAD_LOCAL s
        5 ENTER_INDEX
        6 PUSH_INTEGER 7
        7 PUSH_INTEGER 12
        8 PUSH_STRING baz
        9 SET_RANGE_LOCAL s
        10 POP
        11 LOAD_LOCAL s
        12 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertTrue(state.uncaughtError().isEmpty());
    assertEquals(
        new StringValue("foobarbaz".getBytes(StandardCharsets.ISO_8859_1)),
        state.returnValue().orElseThrow());
  }

  @Test
  void prependsAStringRangeThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "s = \"fubarbaz\"; s[1..0] = \"test\"; return s;"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement initialAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().getFirst());
    Ast.Assignment initialAssignment =
        assertInstanceOf(Ast.Assignment.class, initialAssignmentStatement.expression());
    Ast.StringLiteral initialString =
        assertInstanceOf(Ast.StringLiteral.class, initialAssignment.value());
    Ast.ExpressionStatement rangeAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().get(1));
    Ast.Assignment rangeAssignment =
        assertInstanceOf(Ast.Assignment.class, rangeAssignmentStatement.expression());
    Ast.RangeTarget target = assertInstanceOf(Ast.RangeTarget.class, rangeAssignment.target());
    Ast.IntegerLiteral start = assertInstanceOf(Ast.IntegerLiteral.class, target.start());
    Ast.IntegerLiteral end = assertInstanceOf(Ast.IntegerLiteral.class, target.end());
    Ast.StringLiteral replacement =
        assertInstanceOf(Ast.StringLiteral.class, rangeAssignment.value());
    Ast.Return returnStatement = assertInstanceOf(Ast.Return.class, syntax.statements().get(2));
    assertInstanceOf(Ast.Identifier.class, target.collection());
    assertEquals(new Ast.SourceSpan(4, 14, 1, 5), initialString.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(16, 33, 1, 17), rangeAssignmentStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(18, 19, 1, 19), start.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(21, 22, 1, 22), end.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(26, 32, 1, 27), replacement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(34, 43, 1, 35), returnStatement.span().orElseThrow());
    assertEquals(
        """
        s = "fubarbaz";
        s[1..0] = "test";
        return s;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_STRING fubarbaz
        1 DUP
        2 STORE_LOCAL s
        3 POP
        4 LOAD_LOCAL s
        5 ENTER_INDEX
        6 PUSH_INTEGER 1
        7 PUSH_INTEGER 0
        8 PUSH_STRING test
        9 SET_RANGE_LOCAL s
        10 POP
        11 LOAD_LOCAL s
        12 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertTrue(state.uncaughtError().isEmpty());
    assertEquals(
        new StringValue("testfubarbaz".getBytes(StandardCharsets.ISO_8859_1)),
        state.returnValue().orElseThrow());
  }

  @Test
  void assignsAnInvertedStringRangeThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "s = \"1234567\"; s[7..1] = \"abc\"; return s;"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement initialAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().getFirst());
    Ast.Assignment initialAssignment =
        assertInstanceOf(Ast.Assignment.class, initialAssignmentStatement.expression());
    Ast.StringLiteral initialString =
        assertInstanceOf(Ast.StringLiteral.class, initialAssignment.value());
    Ast.ExpressionStatement rangeAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().get(1));
    Ast.Assignment rangeAssignment =
        assertInstanceOf(Ast.Assignment.class, rangeAssignmentStatement.expression());
    Ast.RangeTarget target = assertInstanceOf(Ast.RangeTarget.class, rangeAssignment.target());
    Ast.IntegerLiteral start = assertInstanceOf(Ast.IntegerLiteral.class, target.start());
    Ast.IntegerLiteral end = assertInstanceOf(Ast.IntegerLiteral.class, target.end());
    Ast.StringLiteral replacement =
        assertInstanceOf(Ast.StringLiteral.class, rangeAssignment.value());
    Ast.Return returnStatement = assertInstanceOf(Ast.Return.class, syntax.statements().get(2));
    assertInstanceOf(Ast.Identifier.class, target.collection());
    assertEquals(new Ast.SourceSpan(4, 13, 1, 5), initialString.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(15, 31, 1, 16), rangeAssignmentStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(17, 18, 1, 18), start.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(20, 21, 1, 21), end.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(25, 30, 1, 26), replacement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(32, 41, 1, 33), returnStatement.span().orElseThrow());
    assertEquals(
        """
        s = "1234567";
        s[7..1] = "abc";
        return s;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_STRING 1234567
        1 DUP
        2 STORE_LOCAL s
        3 POP
        4 LOAD_LOCAL s
        5 ENTER_INDEX
        6 PUSH_INTEGER 7
        7 PUSH_INTEGER 1
        8 PUSH_STRING abc
        9 SET_RANGE_LOCAL s
        10 POP
        11 LOAD_LOCAL s
        12 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertTrue(state.uncaughtError().isEmpty());
    assertEquals(
        new StringValue("123456abc234567".getBytes(StandardCharsets.ISO_8859_1)),
        state.returnValue().orElseThrow());
  }

  @Test
  void returnsAnInclusiveStringRangeThroughTheCompleteCollectionPipeline() {
    byte[] source = "return \"foobar\"[3..3];".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.RangeAccess range =
        assertInstanceOf(Ast.RangeAccess.class, returnStatement.value().orElseThrow());
    Ast.StringLiteral string = assertInstanceOf(Ast.StringLiteral.class, range.collection());
    Ast.IntegerLiteral start = assertInstanceOf(Ast.IntegerLiteral.class, range.start());
    Ast.IntegerLiteral end = assertInstanceOf(Ast.IntegerLiteral.class, range.end());
    assertEquals(new Ast.SourceSpan(0, 22, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 21, 1, 8), range.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 15, 1, 8), string.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(16, 17, 1, 17), start.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(19, 20, 1, 20), end.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_STRING foobar
        1 ENTER_INDEX
        2 PUSH_INTEGER 3
        3 PUSH_INTEGER 3
        4 RANGE
        5 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new StringValue("o".getBytes(StandardCharsets.ISO_8859_1)),
        state.returnValue().orElseThrow());
  }

  @Test
  void returnsAnEmptyStringForAnInvertedRangeThroughTheCompleteCollectionPipeline() {
    byte[] source = "return \"foobar\"[15..12];".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.RangeAccess range =
        assertInstanceOf(Ast.RangeAccess.class, returnStatement.value().orElseThrow());
    Ast.StringLiteral string = assertInstanceOf(Ast.StringLiteral.class, range.collection());
    Ast.IntegerLiteral start = assertInstanceOf(Ast.IntegerLiteral.class, range.start());
    Ast.IntegerLiteral end = assertInstanceOf(Ast.IntegerLiteral.class, range.end());
    assertEquals(new Ast.SourceSpan(0, 24, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 23, 1, 8), range.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 15, 1, 8), string.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(16, 18, 1, 17), start.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(20, 22, 1, 21), end.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_STRING foobar
        1 ENTER_INDEX
        2 PUSH_INTEGER 15
        3 PUSH_INTEGER 12
        4 RANGE
        5 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new StringValue(new byte[0]), state.returnValue().orElseThrow());
  }

  @Test
  void returnsAFullMapRangeThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "return [1 -> 1, 2 -> 2, 3 -> 3, 4 -> 4, 5 -> 5, 6 -> 6, 7 -> 7][^..$];"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.RangeAccess range =
        assertInstanceOf(Ast.RangeAccess.class, returnStatement.value().orElseThrow());
    Ast.MapLiteral map = assertInstanceOf(Ast.MapLiteral.class, range.collection());
    Ast.FirstIndex first = assertInstanceOf(Ast.FirstIndex.class, range.start());
    Ast.LastIndex last = assertInstanceOf(Ast.LastIndex.class, range.end());
    assertEquals(new Ast.SourceSpan(0, 70, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 69, 1, 8), range.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 63, 1, 8), map.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(64, 65, 1, 65), first.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(67, 68, 1, 68), last.span().orElseThrow());
    assertEquals(
        "return [1 -> 1, 2 -> 2, 3 -> 3, 4 -> 4, 5 -> 5, 6 -> 6, 7 -> 7][^..$];",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_INTEGER 1
        1 PUSH_INTEGER 1
        2 PUSH_INTEGER 2
        3 PUSH_INTEGER 2
        4 PUSH_INTEGER 3
        5 PUSH_INTEGER 3
        6 PUSH_INTEGER 4
        7 PUSH_INTEGER 4
        8 PUSH_INTEGER 5
        9 PUSH_INTEGER 5
        10 PUSH_INTEGER 6
        11 PUSH_INTEGER 6
        12 PUSH_INTEGER 7
        13 PUSH_INTEGER 7
        14 BUILD_MAP 7
        15 ENTER_INDEX
        16 FIRST
        17 LAST
        18 RANGE
        19 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertTrue(state.uncaughtError().isEmpty());
    assertEquals(
        new MapValue(
            Map.of(
                new IntegerValue(1), new IntegerValue(1),
                new IntegerValue(2), new IntegerValue(2),
                new IntegerValue(3), new IntegerValue(3),
                new IntegerValue(4), new IntegerValue(4),
                new IntegerValue(5), new IntegerValue(5),
                new IntegerValue(6), new IntegerValue(6),
                new IntegerValue(7), new IntegerValue(7))),
        state.returnValue().orElseThrow());
  }

  @Test
  void rejectsACollectionValuedMapRangeEndThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "return [1 -> 1, 2 -> 2, 3 -> 3, 4 -> 4, 5 -> 5][1..[]];"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.RangeAccess range =
        assertInstanceOf(Ast.RangeAccess.class, returnStatement.value().orElseThrow());
    assertInstanceOf(Ast.MapLiteral.class, range.collection());
    assertInstanceOf(Ast.IntegerLiteral.class, range.start());
    assertInstanceOf(Ast.MapLiteral.class, range.end());
    assertEquals(
        "return [1 -> 1, 2 -> 2, 3 -> 3, 4 -> 4, 5 -> 5][1..[]];",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_INTEGER 1
        1 PUSH_INTEGER 1
        2 PUSH_INTEGER 2
        3 PUSH_INTEGER 2
        4 PUSH_INTEGER 3
        5 PUSH_INTEGER 3
        6 PUSH_INTEGER 4
        7 PUSH_INTEGER 4
        8 PUSH_INTEGER 5
        9 PUSH_INTEGER 5
        10 BUILD_MAP 5
        11 ENTER_INDEX
        12 PUSH_INTEGER 1
        13 BUILD_MAP 0
        14 RANGE
        15 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.ERRORED, state.outcome());
    assertEquals(ErrorValue.E_TYPE, state.uncaughtError().orElseThrow());
  }

  @Test
  void assignsAnExactMapRangeThroughTheCompleteCollectionPipeline() {
    byte[] source =
        ("t = [1 -> 1, 2 -> 2, 3 -> 3, 4 -> 4, 5 -> 5, 6 -> 6, 7 -> 7]; "
                + "t[2..4] = [2 -> \"two\", 3 -> \"three\", 4 -> \"four\"]; return t;")
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement initialAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().getFirst());
    Ast.Assignment initialAssignment =
        assertInstanceOf(Ast.Assignment.class, initialAssignmentStatement.expression());
    Ast.MapLiteral initialMap = assertInstanceOf(Ast.MapLiteral.class, initialAssignment.value());
    Ast.ExpressionStatement rangeAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().get(1));
    Ast.Assignment rangeAssignment =
        assertInstanceOf(Ast.Assignment.class, rangeAssignmentStatement.expression());
    Ast.MapLiteral replacement = assertInstanceOf(Ast.MapLiteral.class, rangeAssignment.value());
    Ast.Return returnStatement = assertInstanceOf(Ast.Return.class, syntax.statements().get(2));
    assertEquals("RangeTarget", rangeAssignment.target().getClass().getSimpleName());
    assertEquals(new Ast.SourceSpan(4, 60, 1, 5), initialMap.span().orElseThrow());
    assertEquals(
        new Ast.SourceSpan(62, 112, 1, 63), rangeAssignmentStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(72, 111, 1, 73), replacement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(113, 122, 1, 114), returnStatement.span().orElseThrow());
    assertEquals(
        """
        t = [1 -> 1, 2 -> 2, 3 -> 3, 4 -> 4, 5 -> 5, 6 -> 6, 7 -> 7];
        t[2..4] = [2 -> "two", 3 -> "three", 4 -> "four"];
        return t;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_INTEGER 1
        1 PUSH_INTEGER 1
        2 PUSH_INTEGER 2
        3 PUSH_INTEGER 2
        4 PUSH_INTEGER 3
        5 PUSH_INTEGER 3
        6 PUSH_INTEGER 4
        7 PUSH_INTEGER 4
        8 PUSH_INTEGER 5
        9 PUSH_INTEGER 5
        10 PUSH_INTEGER 6
        11 PUSH_INTEGER 6
        12 PUSH_INTEGER 7
        13 PUSH_INTEGER 7
        14 BUILD_MAP 7
        15 DUP
        16 STORE_LOCAL t
        17 POP
        18 LOAD_LOCAL t
        19 ENTER_INDEX
        20 PUSH_INTEGER 2
        21 PUSH_INTEGER 4
        22 PUSH_STRING two
        23 PUSH_INTEGER 2
        24 PUSH_STRING three
        25 PUSH_INTEGER 3
        26 PUSH_STRING four
        27 PUSH_INTEGER 4
        28 BUILD_MAP 3
        29 SET_RANGE_LOCAL t
        30 POP
        31 LOAD_LOCAL t
        32 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertTrue(state.uncaughtError().isEmpty());
    assertEquals(
        new MapValue(
            Map.of(
                new IntegerValue(1), new IntegerValue(1),
                new IntegerValue(2),
                    new StringValue("two".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(3),
                    new StringValue("three".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(4),
                    new StringValue("four".getBytes(StandardCharsets.ISO_8859_1)),
                new IntegerValue(5), new IntegerValue(5),
                new IntegerValue(6), new IntegerValue(6),
                new IntegerValue(7), new IntegerValue(7))),
        state.returnValue().orElseThrow());
  }

  @Test
  void rejectsACollectionValuedMapRangeAssignmentEndThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "t = [1 -> 1]; t[1..[]] = [\"1\" -> \"1\"]; return t;"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement initialAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().getFirst());
    Ast.Assignment initialAssignment =
        assertInstanceOf(Ast.Assignment.class, initialAssignmentStatement.expression());
    assertInstanceOf(Ast.MapLiteral.class, initialAssignment.value());
    Ast.ExpressionStatement rangeAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().get(1));
    Ast.Assignment rangeAssignment =
        assertInstanceOf(Ast.Assignment.class, rangeAssignmentStatement.expression());
    assertEquals("RangeTarget", rangeAssignment.target().getClass().getSimpleName());
    assertInstanceOf(Ast.MapLiteral.class, rangeAssignment.value());
    assertInstanceOf(Ast.Return.class, syntax.statements().get(2));
    assertEquals(
        """
        t = [1 -> 1];
        t[1..[]] = ["1" -> "1"];
        return t;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_INTEGER 1
        1 PUSH_INTEGER 1
        2 BUILD_MAP 1
        3 DUP
        4 STORE_LOCAL t
        5 POP
        6 LOAD_LOCAL t
        7 ENTER_INDEX
        8 PUSH_INTEGER 1
        9 BUILD_MAP 0
        10 PUSH_STRING 1
        11 PUSH_STRING 1
        12 BUILD_MAP 1
        13 SET_RANGE_LOCAL t
        14 POP
        15 LOAD_LOCAL t
        16 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.ERRORED, state.outcome());
    assertEquals(ErrorValue.E_TYPE, state.uncaughtError().orElseThrow());
  }

  @Test
  void appendsAnInvertedMapRangeThroughTheCompleteCollectionPipeline() {
    byte[] source =
        ("t = [1 -> 1, 2 -> 2, 3 -> 3, 4 -> 4, 5 -> 5, 6 -> 6, 7 -> 7]; "
                + "t[7..1] = [\"a\" -> \"a\", \"b\" -> \"b\", \"c\" -> \"c\"]; return t;")
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement initialAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().getFirst());
    Ast.Assignment initialAssignment =
        assertInstanceOf(Ast.Assignment.class, initialAssignmentStatement.expression());
    Ast.MapLiteral initialMap = assertInstanceOf(Ast.MapLiteral.class, initialAssignment.value());
    Ast.ExpressionStatement rangeAssignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().get(1));
    Ast.Assignment rangeAssignment =
        assertInstanceOf(Ast.Assignment.class, rangeAssignmentStatement.expression());
    Ast.MapLiteral replacement = assertInstanceOf(Ast.MapLiteral.class, rangeAssignment.value());
    Ast.Return returnStatement = assertInstanceOf(Ast.Return.class, syntax.statements().get(2));
    assertEquals("RangeTarget", rangeAssignment.target().getClass().getSimpleName());
    assertEquals(new Ast.SourceSpan(4, 60, 1, 5), initialMap.span().orElseThrow());
    assertEquals(
        new Ast.SourceSpan(62, 109, 1, 63), rangeAssignmentStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(72, 108, 1, 73), replacement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(110, 119, 1, 111), returnStatement.span().orElseThrow());
    assertEquals(
        """
        t = [1 -> 1, 2 -> 2, 3 -> 3, 4 -> 4, 5 -> 5, 6 -> 6, 7 -> 7];
        t[7..1] = ["a" -> "a", "b" -> "b", "c" -> "c"];
        return t;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_INTEGER 1
        1 PUSH_INTEGER 1
        2 PUSH_INTEGER 2
        3 PUSH_INTEGER 2
        4 PUSH_INTEGER 3
        5 PUSH_INTEGER 3
        6 PUSH_INTEGER 4
        7 PUSH_INTEGER 4
        8 PUSH_INTEGER 5
        9 PUSH_INTEGER 5
        10 PUSH_INTEGER 6
        11 PUSH_INTEGER 6
        12 PUSH_INTEGER 7
        13 PUSH_INTEGER 7
        14 BUILD_MAP 7
        15 DUP
        16 STORE_LOCAL t
        17 POP
        18 LOAD_LOCAL t
        19 ENTER_INDEX
        20 PUSH_INTEGER 7
        21 PUSH_INTEGER 1
        22 PUSH_STRING a
        23 PUSH_STRING a
        24 PUSH_STRING b
        25 PUSH_STRING b
        26 PUSH_STRING c
        27 PUSH_STRING c
        28 BUILD_MAP 3
        29 SET_RANGE_LOCAL t
        30 POP
        31 LOAD_LOCAL t
        32 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertTrue(state.uncaughtError().isEmpty());
    assertEquals(
        new MapValue(
            Map.ofEntries(
                Map.entry(new IntegerValue(1), new IntegerValue(1)),
                Map.entry(new IntegerValue(2), new IntegerValue(2)),
                Map.entry(new IntegerValue(3), new IntegerValue(3)),
                Map.entry(new IntegerValue(4), new IntegerValue(4)),
                Map.entry(new IntegerValue(5), new IntegerValue(5)),
                Map.entry(new IntegerValue(6), new IntegerValue(6)),
                Map.entry(new IntegerValue(7), new IntegerValue(7)),
                Map.entry(
                    new StringValue("a".getBytes(StandardCharsets.ISO_8859_1)),
                    new StringValue("a".getBytes(StandardCharsets.ISO_8859_1))),
                Map.entry(
                    new StringValue("b".getBytes(StandardCharsets.ISO_8859_1)),
                    new StringValue("b".getBytes(StandardCharsets.ISO_8859_1))),
                Map.entry(
                    new StringValue("c".getBytes(StandardCharsets.ISO_8859_1)),
                    new StringValue("c".getBytes(StandardCharsets.ISO_8859_1))))),
        state.returnValue().orElseThrow());
  }

  @Test
  void returnsAnEmptyMapForAnInvertedRangeThroughTheCompleteCollectionPipeline() {
    byte[] source = "return [1 -> 1][6..2];".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.RangeAccess range =
        assertInstanceOf(Ast.RangeAccess.class, returnStatement.value().orElseThrow());
    Ast.MapLiteral map = assertInstanceOf(Ast.MapLiteral.class, range.collection());
    Ast.IntegerLiteral start = assertInstanceOf(Ast.IntegerLiteral.class, range.start());
    Ast.IntegerLiteral end = assertInstanceOf(Ast.IntegerLiteral.class, range.end());
    assertEquals(new Ast.SourceSpan(0, 22, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 21, 1, 8), range.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 15, 1, 8), map.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(16, 17, 1, 17), start.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(19, 20, 1, 20), end.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_INTEGER 1
        1 PUSH_INTEGER 1
        2 BUILD_MAP 1
        3 ENTER_INDEX
        4 PUSH_INTEGER 6
        5 PUSH_INTEGER 2
        6 RANGE
        7 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new MapValue(Map.of()), state.returnValue().orElseThrow());
  }

  @Test
  void indexesTheFirstMapKeyThroughTheCompleteCollectionPipeline() {
    byte[] source = "return [1 -> 1][^];".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.IndexAccess index =
        assertInstanceOf(Ast.IndexAccess.class, returnStatement.value().orElseThrow());
    Ast.MapLiteral map = assertInstanceOf(Ast.MapLiteral.class, index.collection());
    Ast.FirstIndex first = assertInstanceOf(Ast.FirstIndex.class, index.index());
    assertEquals(new Ast.SourceSpan(0, 19, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 18, 1, 8), index.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 15, 1, 8), map.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(16, 17, 1, 17), first.span().orElseThrow());
    assertEquals("return [1 -> 1][^];", MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_INTEGER 1
        1 PUSH_INTEGER 1
        2 BUILD_MAP 1
        3 ENTER_INDEX
        4 FIRST
        5 INDEX
        6 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(1), state.returnValue().orElseThrow());
  }

  @Test
  void indexesTheLastMapKeyThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "return [1 -> 1, 2 -> 2, 3 -> 3, 4 -> 4, 5 -> 5, 6 -> 6, 7 -> 7][$];"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.IndexAccess index =
        assertInstanceOf(Ast.IndexAccess.class, returnStatement.value().orElseThrow());
    Ast.MapLiteral map = assertInstanceOf(Ast.MapLiteral.class, index.collection());
    Ast.LastIndex last = assertInstanceOf(Ast.LastIndex.class, index.index());
    assertEquals(new Ast.SourceSpan(0, 67, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 66, 1, 8), index.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 63, 1, 8), map.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(64, 65, 1, 65), last.span().orElseThrow());
    assertEquals(
        "return [1 -> 1, 2 -> 2, 3 -> 3, 4 -> 4, 5 -> 5, 6 -> 6, 7 -> 7][$];",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_INTEGER 1
        1 PUSH_INTEGER 1
        2 PUSH_INTEGER 2
        3 PUSH_INTEGER 2
        4 PUSH_INTEGER 3
        5 PUSH_INTEGER 3
        6 PUSH_INTEGER 4
        7 PUSH_INTEGER 4
        8 PUSH_INTEGER 5
        9 PUSH_INTEGER 5
        10 PUSH_INTEGER 6
        11 PUSH_INTEGER 6
        12 PUSH_INTEGER 7
        13 PUSH_INTEGER 7
        14 BUILD_MAP 7
        15 ENTER_INDEX
        16 LAST
        17 INDEX
        18 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(7), state.returnValue().orElseThrow());
  }

  @Test
  void indexesTheFirstListValueThroughTheCompleteCollectionPipeline() {
    byte[] source = "return {1, 2, 3}[^];".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.IndexAccess index =
        assertInstanceOf(Ast.IndexAccess.class, returnStatement.value().orElseThrow());
    Ast.ListLiteral list = assertInstanceOf(Ast.ListLiteral.class, index.collection());
    Ast.FirstIndex first = assertInstanceOf(Ast.FirstIndex.class, index.index());
    assertEquals(new Ast.SourceSpan(0, 20, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 19, 1, 8), index.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 16, 1, 8), list.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(17, 18, 1, 18), first.span().orElseThrow());
    assertEquals("return {1, 2, 3}[^];", MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 PUSH_INTEGER 1
        2 LIST_APPEND
        3 PUSH_INTEGER 2
        4 LIST_APPEND
        5 PUSH_INTEGER 3
        6 LIST_APPEND
        7 ENTER_INDEX
        8 FIRST
        9 INDEX
        10 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(1), state.returnValue().orElseThrow());
  }

  @Test
  void indexesTheLastListValueThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "return {1, 2, 3, 4, 5, 6, 7}[$];".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.IndexAccess index =
        assertInstanceOf(Ast.IndexAccess.class, returnStatement.value().orElseThrow());
    Ast.ListLiteral list = assertInstanceOf(Ast.ListLiteral.class, index.collection());
    Ast.LastIndex last = assertInstanceOf(Ast.LastIndex.class, index.index());
    assertEquals(new Ast.SourceSpan(0, 32, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 31, 1, 8), index.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 28, 1, 8), list.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(29, 30, 1, 30), last.span().orElseThrow());
    assertEquals("return {1, 2, 3, 4, 5, 6, 7}[$];", MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 PUSH_INTEGER 1
        2 LIST_APPEND
        3 PUSH_INTEGER 2
        4 LIST_APPEND
        5 PUSH_INTEGER 3
        6 LIST_APPEND
        7 PUSH_INTEGER 4
        8 LIST_APPEND
        9 PUSH_INTEGER 5
        10 LIST_APPEND
        11 PUSH_INTEGER 6
        12 LIST_APPEND
        13 PUSH_INTEGER 7
        14 LIST_APPEND
        15 ENTER_INDEX
        16 LAST
        17 INDEX
        18 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(7), state.returnValue().orElseThrow());
  }

  @Test
  void indexesTheLastStringByteThroughTheCompleteCollectionPipeline() {
    byte[] source = "return \"foobar\"[$];".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.IndexAccess index =
        assertInstanceOf(Ast.IndexAccess.class, returnStatement.value().orElseThrow());
    Ast.StringLiteral string = assertInstanceOf(Ast.StringLiteral.class, index.collection());
    Ast.LastIndex last = assertInstanceOf(Ast.LastIndex.class, index.index());
    assertEquals(new Ast.SourceSpan(0, 19, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 18, 1, 8), index.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 15, 1, 8), string.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(16, 17, 1, 17), last.span().orElseThrow());
    assertEquals("return \"foobar\"[$];", MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_STRING foobar
        1 ENTER_INDEX
        2 LAST
        3 INDEX
        4 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertTrue(state.uncaughtError().isEmpty());
    assertEquals(
        new StringValue("r".getBytes(StandardCharsets.ISO_8859_1)),
        state.returnValue().orElseThrow());
  }

  @Test
  void indexesTheFirstStringByteThroughTheCompleteCollectionPipeline() {
    byte[] source = "return \"foobar\"[^];".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.IndexAccess index =
        assertInstanceOf(Ast.IndexAccess.class, returnStatement.value().orElseThrow());
    Ast.StringLiteral string = assertInstanceOf(Ast.StringLiteral.class, index.collection());
    Ast.FirstIndex first = assertInstanceOf(Ast.FirstIndex.class, index.index());
    assertEquals(new Ast.SourceSpan(0, 19, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 18, 1, 8), index.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 15, 1, 8), string.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(16, 17, 1, 17), first.span().orElseThrow());
    assertEquals("return \"foobar\"[^];", MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_STRING foobar
        1 ENTER_INDEX
        2 FIRST
        3 INDEX
        4 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new StringValue("f".getBytes(StandardCharsets.ISO_8859_1)),
        state.returnValue().orElseThrow());
  }

  @Test
  void assignsTheFirstStringByteThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "s = \"foobar\"; s[^] = \"x\"; return s;".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement assignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().get(1));
    Ast.Assignment assignment =
        assertInstanceOf(Ast.Assignment.class, assignmentStatement.expression());
    Ast.IndexTarget target = assertInstanceOf(Ast.IndexTarget.class, assignment.target());
    Ast.FirstIndex first = assertInstanceOf(Ast.FirstIndex.class, target.index());
    assertEquals(new Ast.SourceSpan(14, 25, 1, 15), assignmentStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(14, 24, 1, 15), assignment.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(16, 17, 1, 17), first.span().orElseThrow());
    assertEquals(
        """
        s = "foobar";
        s[^] = "x";
        return s;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 PUSH_STRING foobar
        1 DUP
        2 STORE_LOCAL s
        3 POP
        4 LOAD_LOCAL s
        5 ENTER_INDEX
        6 FIRST
        7 PUSH_STRING x
        8 SET_INDEX_LOCAL s
        9 POP
        10 LOAD_LOCAL s
        11 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new StringValue("xoobar".getBytes(StandardCharsets.ISO_8859_1)),
        state.returnValue().orElseThrow());
  }

  @Test
  void assignsTheFirstListValueThroughTheCompleteCollectionPipeline() {
    byte[] source =
        "t = {1, 2, 3}; t[^] = 9; return t;".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement assignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().get(1));
    Ast.Assignment assignment =
        assertInstanceOf(Ast.Assignment.class, assignmentStatement.expression());
    Ast.IndexTarget target = assertInstanceOf(Ast.IndexTarget.class, assignment.target());
    Ast.FirstIndex first = assertInstanceOf(Ast.FirstIndex.class, target.index());
    assertEquals(new Ast.SourceSpan(15, 24, 1, 16), assignmentStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(15, 23, 1, 16), assignment.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(17, 18, 1, 18), first.span().orElseThrow());
    assertEquals(
        """
        t = {1, 2, 3};
        t[^] = 9;
        return t;""",
        MooUnparser.unparse(syntax));

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        """
        0 BUILD_LIST 0
        1 PUSH_INTEGER 1
        2 LIST_APPEND
        3 PUSH_INTEGER 2
        4 LIST_APPEND
        5 PUSH_INTEGER 3
        6 LIST_APPEND
        7 DUP
        8 STORE_LOCAL t
        9 POP
        10 LOAD_LOCAL t
        11 ENTER_INDEX
        12 FIRST
        13 PUSH_INTEGER 9
        14 SET_INDEX_LOCAL t
        15 POP
        16 LOAD_LOCAL t
        17 RETURN""",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(
        new ListValue(List.of(new IntegerValue(9), new IntegerValue(2), new IntegerValue(3))),
        state.returnValue().orElseThrow());
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
  void wrapsIntegerLiteralAtTheSignedBoundaryThroughTheCompleteLiteralPipeline() {
    byte[] source = "return 9223372036854775808;".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.IntegerLiteral integerLiteral =
        assertInstanceOf(Ast.IntegerLiteral.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 27, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 26, 1, 8), integerLiteral.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        "0 PUSH_INTEGER -9223372036854775808\n1 RETURN", program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    MooValue returned = state.returnValue().orElseThrow();
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(MooValue.Type.INTEGER, returned.type());
    assertEquals("-9223372036854775808", returned.toLiteral());
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
  void returnsObjectThroughTheCompleteLiteralPipeline() {
    byte[] source = "return #2;".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.ObjectLiteral objectLiteral =
        assertInstanceOf(Ast.ObjectLiteral.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 10, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 9, 1, 8), objectLiteral.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals("0 PUSH_OBJECT 2\n1 RETURN", program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    MooValue returned = state.returnValue().orElseThrow();
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(MooValue.Type.OBJECT, returned.type());
    assertEquals("#2", returned.toLiteral());
    assertFalse(returned.isTruthy());
  }

  @Test
  void wrapsObjectLiteralAtTheSignedBoundaryThroughTheCompleteLiteralPipeline() {
    byte[] source = "return #9223372036854775808;".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.ObjectLiteral objectLiteral =
        assertInstanceOf(Ast.ObjectLiteral.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 28, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 27, 1, 8), objectLiteral.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        "0 PUSH_OBJECT -9223372036854775808\n1 RETURN", program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    MooValue returned = state.returnValue().orElseThrow();
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(MooValue.Type.OBJECT, returned.type());
    assertEquals("#-9223372036854775808", returned.toLiteral());
    assertFalse(returned.isTruthy());
  }

  @Test
  void returnsLatin1StringThroughTheCompleteLiteralPipeline() {
    byte[] source = "return \"é\";".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.StringLiteral stringLiteral =
        assertInstanceOf(Ast.StringLiteral.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 11, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 10, 1, 8), stringLiteral.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals("0 PUSH_STRING é\n1 RETURN", program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    MooValue returned = state.returnValue().orElseThrow();
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(MooValue.Type.STRING, returned.type());
    assertEquals("\"é\"", returned.toLiteral());
    assertTrue(returned.isTruthy());
    assertArrayEquals(new byte[] {(byte) 0xE9}, ((StringValue) returned).bytes());
  }

  @Test
  void returnsEmptyListThroughTheCompleteLiteralPipeline() {
    byte[] source = "return {};".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.ListLiteral listLiteral =
        assertInstanceOf(Ast.ListLiteral.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 10, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 9, 1, 8), listLiteral.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals("0 BUILD_LIST 0\n1 RETURN", program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    MooValue returned = state.returnValue().orElseThrow();
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(MooValue.Type.LIST, returned.type());
    assertEquals("{}", returned.toLiteral());
    assertFalse(returned.isTruthy());
    assertEquals(new ListValue(List.of()), returned);
  }

  @Test
  void returnsEmptyMapThroughTheCompleteLiteralPipeline() {
    byte[] source = "return [];".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.MapLiteral mapLiteral =
        assertInstanceOf(Ast.MapLiteral.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 10, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 9, 1, 8), mapLiteral.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals("0 BUILD_MAP 0\n1 RETURN", program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    MooValue returned = state.returnValue().orElseThrow();
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(MooValue.Type.MAP, returned.type());
    assertEquals("[]", returned.toLiteral());
    assertFalse(returned.isTruthy());
    assertEquals(new MapValue(Map.of()), returned);
  }

  @Test
  void negatesIntegerTruthThroughTheCompleteLiteralPipeline() {
    byte[] source = "return !0;".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().getFirst());
    Ast.Unary truth =
        assertInstanceOf(Ast.Unary.class, returnStatement.value().orElseThrow());
    Ast.IntegerLiteral integerLiteral =
        assertInstanceOf(Ast.IntegerLiteral.class, truth.operand());
    assertEquals(new Ast.SourceSpan(0, 10, 1, 1), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 9, 1, 8), truth.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(8, 9, 1, 9), integerLiteral.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals("0 PUSH_INTEGER 0\n1 NOT\n2 RETURN", program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    MooValue returned = state.returnValue().orElseThrow();
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(MooValue.Type.INTEGER, returned.type());
    assertEquals("1", returned.toLiteral());
    assertTrue(returned.isTruthy());
  }

  @Test
  void assignsAndLoadsLocalThroughTheCompleteVariablePipeline() {
    byte[] source = "x = 7; return x;".getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.ExpressionStatement assignmentStatement =
        assertInstanceOf(Ast.ExpressionStatement.class, syntax.statements().get(0));
    Ast.Assignment assignment =
        assertInstanceOf(Ast.Assignment.class, assignmentStatement.expression());
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, syntax.statements().get(1));
    Ast.Identifier loaded =
        assertInstanceOf(Ast.Identifier.class, returnStatement.value().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 6, 1, 1), assignmentStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(0, 5, 1, 1), assignment.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(7, 16, 1, 8), returnStatement.span().orElseThrow());
    assertEquals(new Ast.SourceSpan(14, 15, 1, 15), loaded.span().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    assertEquals(
        "0 PUSH_INTEGER 7\n1 DUP\n2 STORE_LOCAL x\n3 POP\n4 LOAD_LOCAL x\n5 RETURN",
        program.disassemble());
    VmState state = new VmState();

    new MooVm().execute(program, state);

    MooValue returned = state.returnValue().orElseThrow();
    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(7), returned);
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

    executeAndClose(program, state, new WorldTxn(List.of(), List.of()), new BuiltinCatalog());

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(1), state.returnValue().orElseThrow());
  }

  @Test
  void resolvesObjectTypeConstantAfterLocalLookup() {
    BytecodeProgram program =
        new MooCompiler().compile(MooParser.parse("return typeof(#0) == OBJ;"));
    VmState state = new VmState();

    executeAndClose(program, state, new WorldTxn(List.of(), List.of()), new BuiltinCatalog());

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertEquals(new IntegerValue(1), state.returnValue().orElseThrow());
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
  void bindsAStringMessageInStructuredExceptionsThroughTheCompletePipeline() {
    byte[] source =
        "try 1 / 0; except error (E_DIV) return error[2]; endtry"
            .getBytes(StandardCharsets.ISO_8859_1);
    Ast.Program syntax = MooParser.parse(source);
    Ast.Try tryStatement = assertInstanceOf(Ast.Try.class, syntax.statements().getFirst());
    assertEquals("error", tryStatement.exceptClauses().getFirst().variable().orElseThrow());

    BytecodeProgram program = new MooCompiler().compile(syntax);
    VmState state = new VmState();

    new MooVm().execute(program, state);

    assertEquals(VmState.Outcome.RETURNED, state.outcome());
    assertInstanceOf(StringValue.class, state.returnValue().orElseThrow());
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

    executeAndClose(program, state, world, new BuiltinCatalog());

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

      executeAndClose(program, state, world, new BuiltinCatalog());

      assertEquals(VmState.Outcome.ERRORED, state.outcome(), sources[index]);
      assertEquals(errors[index], state.uncaughtError().orElseThrow(), sources[index]);
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

    executeAndClose(
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

    executeAndCommit(
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
    try (WorldTxn view = world.begin()) {
      assertEquals(9, view.object(5).orElseThrow().owner());
      assertEquals(3, view.object(6).orElseThrow().owner());
    }
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
    WorldObject siblingSix =
        new WorldObject(6, "six", 0, 0, -1, 3, List.of(), List.of(), List.of(), List.of());
    WorldObject siblingSeven =
        new WorldObject(7, "seven", 0, 0, -1, 3, List.of(), List.of(), List.of(), List.of());
    WorldObject contentEight =
        new WorldObject(8, "eight", 0, 0, 10, -1, List.of(), List.of(), List.of(), List.of());
    WorldObject contentNine =
        new WorldObject(9, "nine", 0, 0, 10, -1, List.of(), List.of(), List.of(), List.of());
    WorldTxn root =
        new WorldTxn(
            List.of(1L),
            List.of(
                target,
                content,
                parent,
                child,
                siblingSix,
                siblingSeven,
                contentEight,
                contentNine,
                location));
    try (WorldTxn world = root.begin()) {
      assertTrue(world.recycleObject(1));

      assertTrue(world.object(1).isEmpty());
      assertEquals(List.of(), world.players());
      assertEquals(-1, world.object(2).orElseThrow().location());
      assertEquals(3, world.object(4).orElseThrow().parent());
      assertEquals(List.of(8L, 9L), world.object(10).orElseThrow().contents());
      assertEquals(List.of(7L, 4L, 6L), world.object(3).orElseThrow().children());
      assertTrue(world.commit().isCommitted());
    }
  }

  private static void executeAndClose(
      BytecodeProgram program, VmState state, WorldTxn root, BuiltinCatalog builtins) {
    try (WorldTxn transaction = root.begin()) {
      executeWithAuthorizedIrrevocables(program, state, transaction, builtins);
    }
  }

  private static void executeAndCommit(
      BytecodeProgram program, VmState state, WorldTxn root, BuiltinCatalog builtins) {
    try (WorldTxn transaction = root.begin()) {
      executeWithAuthorizedIrrevocables(program, state, transaction, builtins);
      assertTrue(transaction.commit().isCommitted());
    }
  }

  private static void executeWithAuthorizedIrrevocables(
      BytecodeProgram program, VmState state, WorldTxn transaction, BuiltinCatalog builtins) {
    MooVm vm = new MooVm();
    vm.execute(program, state, transaction, builtins);
    while (state.outcome() == VmState.Outcome.PENDING_BUILTIN) {
      vm.authorizePendingBuiltin(state, transaction, builtins);
      vm.execute(program, state, transaction, builtins);
    }
  }
}
