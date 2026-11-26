package world.mongoose.banteng.bytecode;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.persistence.LambdaMooV17Codec;
import world.mongoose.banteng.persistence.LambdaMooV4Reader;
import world.mongoose.banteng.syntax.Ast;
import world.mongoose.banteng.syntax.MooParser;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

final class MooCompilerTest {
  @Test
  void compilesMixedCaseLoopExitsInExecutableAndToastLayouts() {
    for (String exit : List.of("break", "continue")) {
      for (String source :
          List.of(
              "for X in ({1, 2}) EXIT X; endfor",
              "for X in [1..2] EXIT X; endfor",
              "for Value, X in ({1, 2}) EXIT X; endfor",
              "while X (1) EXIT X; endwhile",
              "for X in ({1}) while Y (1) EXIT X; endwhile endfor",
              "while X (1) for Y in ({1}) EXIT X; endfor endwhile",
              "while X (1) while x (1) EXIT X; endwhile endwhile")) {
        String sameCase = source.replace("EXIT", exit);
        String mixedCase = sameCase.replace(exit + " X;", exit + " x;");
        MooCompiler.CompilationResult expected = new MooCompiler().compileResult(sameCase);
        MooCompiler.CompilationResult actual = new MooCompiler().compileResult(mixedCase);

        assertTrue(expected.diagnostics().isEmpty(), sameCase);
        assertTrue(actual.diagnostics().isEmpty(), mixedCase);
        assertEquals(
            expected.program().orElseThrow().instructions(),
            actual.program().orElseThrow().instructions(),
            mixedCase);
        assertDoesNotThrow(() -> new LayoutCompiler(mixedCase).callBoundaries(-1), mixedCase);
      }
    }
  }

  @Test
  void reportsLoopNamesThatDoNotMatchLocalVariableCaseFolding() {
    for (String exit : List.of("break", "continue")) {
      for (String source :
          List.of("for I in ({1}) EXIT ı; endfor", "while I (1) EXIT ı; endwhile")) {
        MooCompiler.CompilationResult result =
            new MooCompiler().compileResult(source.replace("EXIT", exit));

        assertTrue(result.program().isEmpty());
        assertEquals(
            List.of(new MooCompiler.Diagnostic(1, "Invalid loop name in `" + exit + "' statement: ı")),
            result.diagnostics());
      }
    }
  }

  @Test
  void reportsContextOnlyExpressionNodesPrecisely() {
    MooCompiler compiler = new MooCompiler();
    Ast.Program spliceProgram =
        new Ast.Program(
            List.of(
                new Ast.ExpressionStatement(new Ast.Splice(new Ast.IntegerLiteral(1)))));
    Ast.Program scatterElementProgram =
        new Ast.Program(
            List.of(
                new Ast.ExpressionStatement(
                    new Ast.ScatterElement("value", false, false, Optional.empty()))));

    assertEquals(
        "splice expression requires list or argument context",
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(spliceProgram))
            .getMessage());
    assertEquals(
        "scatter element requires assignment-target context",
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(scatterElementProgram))
            .getMessage());
  }

  @Test
  void compiledDiagnosticsDoNotExposeBuildProcessVocabulary() throws IOException {
    byte[] bytecode;
    try (var input =
        Objects.requireNonNull(MooCompiler.class.getResourceAsStream("MooCompiler.class"))) {
      bytecode = input.readAllBytes();
    }
    String constants = StringValue.of(bytecode).text();

    assertFalse(constants.contains("bytecode slice"));
  }

  @Test
  void returnsToastFormattedSourceDiagnosticsWithoutThrowing() {
    MooCompiler.CompilationResult result = new MooCompiler().compileResult("return (\n");

    assertTrue(result.program().isEmpty());
    assertEquals(
        List.of(new MooCompiler.Diagnostic(2, "syntax error")), result.diagnostics());
    assertEquals("Line 2:  syntax error", result.diagnostics().getFirst().display());
  }

  @Test
  void returnsEveryRecoverableSourceDiagnosticInProgramOrder() {
    MooCompiler.CompilationResult result =
        new MooCompiler().compileResult("return ^;\nreturn ^;\n");

    assertTrue(result.program().isEmpty());
    assertEquals(
        List.of(
            new MooCompiler.Diagnostic(1, "Illegal context for `^' expression."),
            new MooCompiler.Diagnostic(2, "Illegal context for `^' expression.")),
        result.diagnostics());
  }

  @Test
  void returnsEveryControlFlowCompilationDiagnosticInProgramOrder() {
    MooCompiler.CompilationResult result =
        new MooCompiler().compileResult("break;\ncontinue;\n");

    assertTrue(result.program().isEmpty());
    assertEquals(
        List.of(
            new MooCompiler.Diagnostic(1, "No enclosing loop for `break' statement"),
            new MooCompiler.Diagnostic(2, "No enclosing loop for `continue' statement")),
        result.diagnostics());
  }

  @Test
  void combinesParserAndControlFlowDiagnosticsInSourceOrder() {
    MooCompiler.CompilationResult result =
        new MooCompiler().compileResult("break; #invalid;\ncontinue; 1e+;\n");

    assertTrue(result.program().isEmpty());
    assertEquals(
        List.of(
            new MooCompiler.Diagnostic(1, "No enclosing loop for `break' statement"),
            new MooCompiler.Diagnostic(1, "Malformed object number"),
            new MooCompiler.Diagnostic(2, "No enclosing loop for `continue' statement"),
            new MooCompiler.Diagnostic(2, "Malformed floating-point literal")),
        result.diagnostics());
  }

  @Test
  void preservesNestedControlFlowDiagnosticsBeforeLaterParserFailures() {
    MooCompiler.CompilationResult result =
        new MooCompiler()
            .compileResult(
                """
                if (1)
                  break; return (;
                endif
                while loop (1)
                  break missing; return (;
                endwhile
                for item in ({1})
                  continue missing; return (;
                endfor
                fork (0)
                  continue; return (;
                endfork
                try
                  break; return (;
                except (ANY)
                endtry
                """);

    assertTrue(result.program().isEmpty());
    assertEquals(
        List.of(
            new MooCompiler.Diagnostic(2, "No enclosing loop for `break' statement"),
            new MooCompiler.Diagnostic(2, "syntax error")),
        result.diagnostics());

    MooCompiler.CompilationResult incomplete =
        new MooCompiler().compileResult("if (1)\n  break;\n  return (");
    assertEquals(
        List.of(
            new MooCompiler.Diagnostic(2, "No enclosing loop for `break' statement"),
            new MooCompiler.Diagnostic(3, "syntax error")),
        incomplete.diagnostics());
  }

  @Test
  void preservesToastLexerDiagnosticsThatAreMoreSpecificThanSyntaxError() {
    MooCompiler compiler = new MooCompiler();

    assertEquals(
        List.of(new MooCompiler.Diagnostic(1, "Missing quote")),
        compiler.compileResult("return \"unterminated;\n").diagnostics());
    assertEquals(
        List.of(new MooCompiler.Diagnostic(1, "Malformed object number")),
        compiler.compileResult("return #invalid;\n").diagnostics());
    assertEquals(
        List.of(new MooCompiler.Diagnostic(1, "Malformed floating-point literal")),
        compiler.compileResult("return 1e+;\n").diagnostics());
    assertEquals(
        List.of(new MooCompiler.Diagnostic(1, "Floating-point literal out of range")),
        compiler.compileResult("return 1e9999;\n").diagnostics());
    assertEquals(
        List.of(
            new MooCompiler.Diagnostic(
                1, "Illegal expression on left side of assignment.")),
        compiler.compileResult("1 = 2;\n").diagnostics());
    assertEquals(
        List.of(new MooCompiler.Diagnostic(1, "Empty list in scattering assignment.")),
        compiler.compileResult("{} = {1};\n").diagnostics());
    assertEquals(
        List.of(
            new MooCompiler.Diagnostic(
                1, "Scattering assignment targets must be simple variables.")),
        compiler.compileResult("{1} = {2};\n").diagnostics());
  }

  @Test
  void preservesRemainingToastParserAndLexerDiagnostics() {
    MooCompiler compiler = new MooCompiler();

    assertEquals(
        List.of(new MooCompiler.Diagnostic(1, "Illegal context for `$' expression.")),
        compiler.compileResult("return $;\n").diagnostics());
    assertEquals(
        List.of(
            new MooCompiler.Diagnostic(
                1, "More than one `@' target in scattering assignment.")),
        compiler.compileResult("{@first, @second} = {1, 2};\n").diagnostics());
    assertEquals(
        List.of(
            new MooCompiler.Diagnostic(
                1, "More than one `@' target in scattering assignment."),
            new MooCompiler.Diagnostic(
                1, "More than one `@' target in scattering assignment.")),
        compiler.compileResult("{@first, @second, @third} = {1, 2, 3};\n").diagnostics());

    String targets =
        IntStream.range(0, 256).mapToObj(index -> "target" + index).collect(java.util.stream.Collectors.joining(", "));
    assertEquals(
        List.of(
            new MooCompiler.Diagnostic(1, "Too many targets in scattering assignment.")),
        compiler.compileResult("{" + targets + "} = {};\n").diagnostics());

    assertEquals(
        List.of(new MooCompiler.Diagnostic(5, "Unreachable EXCEPT clause")),
        compiler
            .compileResult(
                """
                try
                  return 0;
                except (ANY)
                  return 1;
                except (E_TYPE)
                  return 2;
                endtry
                """)
            .diagnostics());

    StringBuilder tooManyExcepts = new StringBuilder("try\n  return 0;\n");
    for (int index = 0; index < 257; index++) {
      tooManyExcepts.append("except (E_TYPE)\n  return 0;\n");
    }
    tooManyExcepts.append("endtry\n");
    assertEquals(
        List.of(new MooCompiler.Diagnostic(515, "Too many EXCEPT clauses (max. 255)")),
        compiler.compileResult(tooManyExcepts.toString()).diagnostics());

    assertEquals(
        List.of(new MooCompiler.Diagnostic(1, "End of program while in a comment")),
        compiler.compileResult("return 0; /* unterminated").diagnostics());
    assertEquals(
        List.of(new MooCompiler.Diagnostic(2, "End of program while in a comment")),
        compiler.compileResult("return 0;\n/* unterminated\nstill in comment").diagnostics());
    assertEquals(
        List.of(new MooCompiler.Diagnostic(3, "Illegal context for `$' expression.")),
        compiler
            .compileResult("return 0;\n/* terminated\ncomment */\nreturn $;\n")
            .diagnostics());
  }

  @Test
  void reportsEachExtraScatterRestTargetAtPostRightHandSideLine() {
    MooCompiler.CompilationResult result =
        new MooCompiler()
            .compileResult(
                """
                {@first,
                 @second,
                 @third} =
                 {1,2,3};
                """);

    assertEquals(
        List.of(
            new MooCompiler.Diagnostic(
                4, "More than one `@' target in scattering assignment."),
            new MooCompiler.Diagnostic(
                4, "More than one `@' target in scattering assignment.")),
        result.diagnostics());
  }

  @Test
  void retainsExactOneBasedSourceLineForEveryInstruction() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                """
                value = 1;
                return
                  value + 2;
                """);

    assertEquals(
        List.of(1, 1, 1, 1, 3, 3, 3, 2),
        IntStream.range(0, program.instructions().size()).mapToObj(program::sourceLine).toList());
    assertTrue(program.instructions().stream().allMatch(instruction -> instruction.sourceLine() > 0));

    BytecodeProgram hostCall =
        new MooCompiler()
            .compile(
                """
                return
                  all_members("a", {"A", "b"});
                """);
    int callInstruction =
        IntStream.range(0, hostCall.instructions().size())
            .filter(index -> hostCall.instructions().get(index).opcode() == BytecodeProgram.Opcode.CALL)
            .findFirst()
            .orElseThrow();
    assertEquals(2, hostCall.sourceLine(callInstruction));
  }

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
        2 POP
        3 PUSH_INTEGER 1
        4 LIST_APPEND 1
        5 PUSH_INTEGER 2
        6 LIST_APPEND 1
        7 LIST_APPEND 0
        8 PUSH_INTEGER 0
        9 LIST_APPEND 1
        10 LIST_APPEND 1
        11 PUSH_INTEGER 1
        12 LIST_APPEND 1
        13 PUSH_INTEGER 2
        14 LIST_APPEND 1
        15 LIST_APPEND 0
        16 LIST_APPEND 0
        17 IN
        18 RETURN
        fork 0:
          0 PUSH_INTEGER 1
          1 LIST_APPEND 1
          2 CALL suspend
          3 POP
          4 PUSH_CLEAR 24
          5 LIST_APPEND 1
          6 CALL sqlite_interrupt
          7 POP
          8 PUSH_INTEGER 0
          9 RETURN""",
        program.disassemble());
  }

  @Test
  void lowersNamedForkTaskIdToExistingStoreLocalOpcode() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    "fork task_id (2) return 0; endfork return task_id;"));

    assertEquals(
        """
        0 PUSH_INTEGER 2
        1 FORK 0
        2 STORE_LOCAL 24
        3 PUSH_CLEAR 24
        4 RETURN
        fork 0:
          0 PUSH_INTEGER 0
          1 RETURN""",
        program.disassemble());
  }

  @Test
  void preservesCanonicalSourceForNestedForkVectorRecompilation() {
    BytecodeProgram program =
        new MooCompiler()
            .compile(
                MooParser.parse(
                    """
                    fork (1)
                      fork nested_task (2)
                        return 7;
                      endfork
                    endfork
                    return 0;
                    """));

    BytecodeProgram outerFork = program.forkVectors().getFirst();
    BytecodeProgram nestedFork = outerFork.forkVectors().getFirst();

    assertEquals(
        outerFork.disassemble(), new MooCompiler().compile(outerFork.source()).disassemble());
    assertEquals(
        nestedFork.disassemble(), new MooCompiler().compile(nestedFork.source()).disassemble());
    assertEquals("", new BytecodeProgram(List.of()).source());
    assertEquals("", new BytecodeProgram(List.of(), List.of()).source());
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
        0 ENTER_HANDLER catch=-1:-:,binding=NONE,finally=16,end=21
        1 ENTER_HANDLER catch=13:second:E_ARGS,binding=STRUCTURED,finally=-1,end=9
        2 ENTER_HANDLER catch=10:first:E_TYPE,binding=STRUCTURED,finally=-1,end=8
        3 PUSH_ERROR E_ARGS
        4 LIST_APPEND 1
        5 CALL raise
        6 POP
        7 LEAVE_HANDLER
        8 LEAVE_HANDLER
        9 LEAVE_HANDLER
        10 PUSH_INTEGER 1
        11 RETURN
        12 LEAVE_HANDLER
        13 PUSH_INTEGER 2
        14 RETURN
        15 LEAVE_HANDLER
        16 PUSH_INTEGER 3
        17 DUP
        18 STORE_LOCAL 26
        19 POP
        20 END_FINALLY
        21 PUSH_INTEGER 0
        22 RETURN""",
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
        0 PUSH_INTEGER 1
        1 LIST_APPEND 1
        2 DUP
        3 STORE_LOCAL 24
        4 POP
        5 PUSH_INTEGER 2
        6 PUSH_INTEGER 1
        7 BUILD_MAP 1
        8 DUP
        9 STORE_LOCAL 25
        10 POP
        11 PUSH_CLEAR 25
        12 ENTER_INDEX
        13 PUSH_INTEGER 1
        14 PUSH_INTEGER 3
        15 PUT_TEMP
        16 SET_INDEX
        17 STORE_LOCAL 25
        18 PUSH_TEMP
        19 POP
        20 PUSH_CLEAR 24
        21 LIST_EXTEND 1
        22 PUSH_CLEAR 25
        23 ENTER_INDEX
        24 PUSH_INTEGER 1
        25 INDEX
        26 LIST_APPEND 0
        27 RETURN""",
        program.disassemble());
  }

  @Test
  void lowersEveryBuiltinArgumentThroughOneConstructedList() {
    BytecodeProgram program =
        new MooCompiler().compile(MooParser.parse("args = {1, 2, 3}; return max(@args);"));

    assertEquals(
        """
        0 PUSH_INTEGER 1
        1 LIST_APPEND 1
        2 PUSH_INTEGER 2
        3 LIST_APPEND 0
        4 PUSH_INTEGER 3
        5 LIST_APPEND 0
        6 DUP
        7 STORE_LOCAL 9
        8 POP
        9 PUSH_CLEAR 9
        10 LIST_EXTEND 1
        11 CALL max
        12 RETURN""",
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
        2 STORE_LOCAL 24
        3 POP
        4 PUSH_OBJECT 0
        5 PUSH_CLEAR 24
        6 PUSH_STRING second
        7 DUP
        8 STORE_LOCAL 24
        9 SET_PROPERTY
        10 POP
        11 PUSH_OBJECT 0
        12 PUSH_CLEAR 24
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
        2 STORE_LOCAL 24
        3 POP
        4 PUSH_OBJECT 0
        5 PUSH_CLEAR 24
        6 PUSH_STRING missing
        7 DUP
        8 STORE_LOCAL 24
        9 LIST_APPEND 1
        10 CALL_VERB
        11 RETURN""",
        computed.disassemble());

    BytecodeProgram staticCall =
        new MooCompiler().compile(MooParser.parse("return #0:test(1, @args);"));

    assertEquals(
        """
        0 PUSH_OBJECT 0
        1 PUSH_STRING test
        2 BUILD_LIST 0
        3 PUSH_INTEGER 1
        4 LIST_APPEND 1
        5 PUSH_CLEAR 9
        6 LIST_EXTEND 0
        7 CALL_VERB
        8 RETURN""",
        staticCall.disassemble());
  }

  @Test
  void compilesEveryCompleteStoredVerbIncludingUnexecutedBranches() throws Exception {
    Path fixture =
        Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");
    WorldTxn root = new LambdaMooV4Reader(new LambdaMooV17Codec()).read(fixture).world();
    MooCompiler compiler = new MooCompiler();

    int compiled = 0;
    try (WorldTxn world = root.begin()) {
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
    }

    assertEquals(5, compiled);
  }
}
