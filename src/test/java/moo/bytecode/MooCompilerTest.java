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
  void encodesStatementAndExpressionCatchBindingModesExplicitly() {
    MooCompiler compiler = new MooCompiler();
    BytecodeProgram statement =
        compiler.compile(MooParser.parse("try return 1; except error (ANY) return error; endtry"));
    BytecodeProgram expression = compiler.compile(MooParser.parse("return `missing ! ANY';"));

    assertTrue(statement.disassemble().contains("binding=STRUCTURED"));
    assertTrue(expression.disassemble().contains("binding=ERROR"));
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
