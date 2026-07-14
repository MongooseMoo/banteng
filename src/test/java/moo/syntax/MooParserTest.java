package moo.syntax;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class MooParserTest {
  private static final Path FIXTURE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db")
          .toAbsolutePath()
          .normalize();

  @Test
  void parsesEveryStoredProgramInExactVersionFourFixture() throws Exception {
    byte[] fixtureBytes = Files.readAllBytes(FIXTURE);
    assertEquals(
        "1a3f23ebb549e02ccf5341668425118fcdc935b977096add87bc2a8ef29d408e",
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fixtureBytes)));

    Map<String, String> programs = exactFixturePrograms(Files.readAllLines(FIXTURE));
    assertEquals(List.of("#0:0", "#0:1", "#0:2", "#2:0", "#7:0"), List.copyOf(programs.keySet()));

    programs.forEach((name, source) -> assertDoesNotThrow(() -> MooParser.parse(source), name));
  }

  @Test
  void parsesDynamicIntegerAddition() {
    Ast.Program program = MooParser.parse("return 1 + 1;");

    assertEquals(1, program.statements().size());
    Ast.Return returnStatement =
        assertInstanceOf(Ast.Return.class, program.statements().getFirst());
    Ast.Binary addition = assertInstanceOf(Ast.Binary.class, returnStatement.value().orElseThrow());
    assertEquals(Ast.BinaryOperator.ADD, addition.operator());
    assertEquals(new Ast.IntegerLiteral(1), addition.left());
    assertEquals(new Ast.IntegerLiteral(1), addition.right());
  }

  @Test
  void parsesToastFloatLiteralForms() {
    Map<String, Double> forms =
        Map.of(".5", 0.5, "1.", 1.0, "1e2", 100.0, "1E+2", 100.0, "11.0", 11.0);

    forms.forEach(
        (source, expected) -> {
          Ast.Program program = MooParser.parse("return " + source + ";");
          Ast.Return returnStatement =
              assertInstanceOf(Ast.Return.class, program.statements().getFirst());
          Ast.FloatLiteral literal =
              assertInstanceOf(Ast.FloatLiteral.class, returnStatement.value().orElseThrow());
          assertEquals(expected, literal.value(), source);
        });
  }

  private static Map<String, String> exactFixturePrograms(List<String> lines) {
    Map<String, String> programs = new LinkedHashMap<>();
    int lineIndex = 0;
    while (lineIndex < lines.size()) {
      String line = lines.get(lineIndex);
      lineIndex++;
      if (!line.matches("#\\d+:\\d+")) {
        continue;
      }

      List<String> sourceLines = new ArrayList<>();
      while (lineIndex < lines.size() && !lines.get(lineIndex).equals(".")) {
        sourceLines.add(lines.get(lineIndex));
        lineIndex++;
      }
      lineIndex++;
      programs.put(line, String.join("\n", sourceLines));
    }
    return Collections.unmodifiableMap(programs);
  }
}
