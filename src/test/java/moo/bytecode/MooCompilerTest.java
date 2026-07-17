package moo.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import moo.persistence.LambdaMooV4Reader;
import moo.syntax.Ast;
import moo.syntax.MooParser;
import moo.world.WorldObject;
import moo.world.WorldTxn;
import moo.world.WorldVerb;
import org.junit.jupiter.api.Test;

final class MooCompilerTest {
  @Test
  void lowersIntegerAdditionAndReturnToDeterministicBytecode() {
    Ast.Program syntax = MooParser.parse("return 1 + 1;");
    MooCompiler compiler = new MooCompiler();

    BytecodeProgram first = compiler.compile(syntax);
    BytecodeProgram second = compiler.compile(syntax);

    assertEquals(first, second);
    assertEquals(
        """
        0 PUSH_INTEGER 1
        1 PUSH_INTEGER 1
        2 ADD
        3 RETURN""",
        first.disassemble());
    assertEquals(first.disassemble(), second.disassemble());
  }

  @Test
  void lowersFloatLiteralsToExplicitRawBitPushes() {
    Ast.Program syntax = MooParser.parse("return 11.0 - 5.5;");
    MooCompiler compiler = new MooCompiler();

    BytecodeProgram first = compiler.compile(syntax);
    BytecodeProgram second = compiler.compile(syntax);

    assertEquals(first, second);
    assertEquals(
        "0 PUSH_FLOAT "
            + Double.doubleToRawLongBits(11.0)
            + "\n1 PUSH_FLOAT "
            + Double.doubleToRawLongBits(5.5)
            + "\n2 SUBTRACT\n3 RETURN",
        first.disassemble());
    assertEquals(first.disassemble(), second.disassemble());
  }

  @Test
  void lowersUnnamedInterruptForkAndNestedListMembershipToForkVector() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    """
                    fork (1)
                      suspend(1);
                      sqlite_interrupt(h);
                    endfork
                    return {1, {2}} in {{0}, {1, {2}}};
                    """));

    assertEquals(1, program.forkVectors().size());
    assertEquals(
        """
        0 PUSH_INTEGER 1
        1 FORK 0
        2 BUILD_LIST 0
        3 PUSH_INTEGER 1
        4 LIST_APPEND
        5 BUILD_LIST 0
        6 PUSH_INTEGER 2
        7 LIST_APPEND
        8 LIST_APPEND
        9 BUILD_LIST 0
        10 BUILD_LIST 0
        11 PUSH_INTEGER 0
        12 LIST_APPEND
        13 LIST_APPEND
        14 BUILD_LIST 0
        15 PUSH_INTEGER 1
        16 LIST_APPEND
        17 BUILD_LIST 0
        18 PUSH_INTEGER 2
        19 LIST_APPEND
        20 LIST_APPEND
        21 LIST_APPEND
        22 IN
        23 RETURN
        fork 0:
          0 BUILD_LIST 0
          1 PUSH_INTEGER 1
          2 LIST_APPEND
          3 CALL suspend
          4 POP
          5 BUILD_LIST 0
          6 LOAD_LOCAL h
          7 LIST_APPEND
          8 CALL sqlite_interrupt
          9 POP
          10 PUSH_INTEGER 0
          11 RETURN""",
        program.disassemble());
  }

  @Test
  void encodesStatementAndExpressionCatchBindingModesExplicitly() {
    MooCompiler compiler = new MooCompiler();
    BytecodeProgram statement =
        compiler.compile(MooParser.parse("try return 1; except error (ANY) return error; endtry"));
    BytecodeProgram expression = compiler.compile(MooParser.parse("return `missing ! ANY';"));

    assertTrue(statement.disassemble().contains("binding=STRUCTURED"));
    assertTrue(expression.disassemble().contains("binding=ERROR"));
  }

  @Test
  void lowersOrderedStatementHandlersDeterministically() {
    Ast.Program syntax =
        MooParser.parse(
            """
            try
              raise(E_ARGS);
            except first (E_TYPE)
              return 1;
            except second (E_ARGS)
              return 2;
            finally
              marker = 3;
            endtry
            """);
    MooCompiler compiler = new MooCompiler();

    BytecodeProgram first = compiler.compile(syntax);
    BytecodeProgram second = compiler.compile(syntax);

    assertEquals(first, second);
    assertEquals(
        """
        0 ENTER_HANDLER catch=-1:-:,binding=NONE,finally=17,end=22
        1 ENTER_HANDLER catch=14:second:E_ARGS,binding=STRUCTURED,finally=-1,end=10
        2 ENTER_HANDLER catch=11:first:E_TYPE,binding=STRUCTURED,finally=-1,end=9
        3 BUILD_LIST 0
        4 PUSH_ERROR E_ARGS
        5 LIST_APPEND
        6 CALL raise
        7 POP
        8 LEAVE_HANDLER
        9 LEAVE_HANDLER
        10 LEAVE_HANDLER
        11 PUSH_INTEGER 1
        12 RETURN
        13 LEAVE_HANDLER
        14 PUSH_INTEGER 2
        15 RETURN
        16 LEAVE_HANDLER
        17 PUSH_INTEGER 3
        18 DUP
        19 STORE_LOCAL marker
        20 POP
        21 END_FINALLY
        22 PUSH_INTEGER 0
        23 RETURN""",
        first.disassemble());
    assertEquals(first.disassemble(), second.disassemble());
  }

  @Test
  void lowersMapConstructionLocalUpdateAndListSplicingToExplicitBytecode() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse("items = {1}; m = [1 -> 2]; m[1] = 3; return {@items, m[1]};"));

    assertEquals(
        """
        0 BUILD_LIST 0
        1 PUSH_INTEGER 1
        2 LIST_APPEND
        3 DUP
        4 STORE_LOCAL items
        5 POP
        6 PUSH_INTEGER 2
        7 PUSH_INTEGER 1
        8 BUILD_MAP 1
        9 DUP
        10 STORE_LOCAL m
        11 POP
        12 LOAD_LOCAL m
        13 PUSH_INTEGER 1
        14 PUSH_INTEGER 3
        15 SET_INDEX_LOCAL m
        16 POP
        17 BUILD_LIST 0
        18 LOAD_LOCAL items
        19 LIST_EXTEND
        20 LOAD_LOCAL m
        21 PUSH_INTEGER 1
        22 INDEX
        23 LIST_APPEND
        24 RETURN""",
        program.disassemble());
  }

  @Test
  void lowersEveryBuiltinArgumentThroughOneConstructedList() {
    BytecodeProgram program =
        new MooCompiler().compile(MooParser.parse("args = {1, 2, 3}; return max(@args);"));

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
        8 STORE_LOCAL args
        9 POP
        10 BUILD_LIST 0
        11 LOAD_LOCAL args
        12 LIST_EXTEND
        13 CALL max
        14 RETURN""",
        program.disassemble());
  }

  @Test
  void lowersComputedPropertyWriteInToastObjectNameRhsOrder() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "name = \"first\"; #0.(name) = (name = \"second\"); return #0.(name);"));

    assertEquals(
        """
        0 PUSH_STRING first
        1 DUP
        2 STORE_LOCAL name
        3 POP
        4 PUSH_OBJECT 0
        5 LOAD_LOCAL name
        6 PUSH_STRING second
        7 DUP
        8 STORE_LOCAL name
        9 SET_PROPERTY
        10 POP
        11 PUSH_OBJECT 0
        12 LOAD_LOCAL name
        13 GET_PROPERTY
        14 RETURN""",
        program.disassemble());
  }

  @Test
  void lowersStaticAndComputedVerbCallsInObjectNameArgumentsOrder() {
    BytecodeProgram computed =
        new MooCompiler()
            .compile(MooParser.parse("name = \"target\"; return #0:(name)((name = \"missing\"));"));

    assertEquals(
        """
        0 PUSH_STRING target
        1 DUP
        2 STORE_LOCAL name
        3 POP
        4 PUSH_OBJECT 0
        5 LOAD_LOCAL name
        6 BUILD_LIST 0
        7 PUSH_STRING missing
        8 DUP
        9 STORE_LOCAL name
        10 LIST_APPEND
        11 CALL_VERB
        12 RETURN""",
        computed.disassemble());

    BytecodeProgram staticCall =
        new MooCompiler().compile(MooParser.parse("return #0:test(1, @args);"));

    assertEquals(
        """
        0 PUSH_OBJECT 0
        1 PUSH_STRING test
        2 BUILD_LIST 0
        3 PUSH_INTEGER 1
        4 LIST_APPEND
        5 LOAD_LOCAL args
        6 LIST_EXTEND
        7 CALL_VERB
        8 RETURN""",
        staticCall.disassemble());
  }

  @Test
  void compilesEveryCompleteStoredVerbIncludingUnexecutedBranches() throws Exception {
    Path fixture =
        Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");
    WorldTxn world = new LambdaMooV4Reader().read(fixture);
    MooCompiler compiler = new MooCompiler();

    int compiled = 0;
    for (long objectId : new long[] {0, 2, 7}) {
      WorldObject object = world.object(objectId).orElseThrow();
      for (WorldVerb verb : object.verbs()) {
        BytecodeProgram first = compiler.compile(MooParser.parse(verb.programSource()));
        BytecodeProgram second = compiler.compile(MooParser.parse(verb.programSource()));
        assertFalse(first.instructions().isEmpty(), "#" + objectId + ":" + verb.names());
        assertEquals(first, second, "#" + objectId + ":" + verb.names());
        assertEquals(first.disassemble(), second.disassemble());
        compiled++;
      }
    }

    assertEquals(5, compiled);
  }
}
