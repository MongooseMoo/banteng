package moo.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class MooUnparserTest {
  @Test
  void canonicalizesExpressionWhitespaceAndStringEscapes() {
    assertEquals("1;", MooUnparser.unparse(MooParser.parse("1   ;")));

    Ast.Program stringProgram =
        new Ast.Program(
            List.of(new Ast.Return(Optional.of(new Ast.StringLiteral("quote: \" slash: \\")))));
    assertEquals("return \"quote: \\\" slash: \\\\\";", MooUnparser.unparse(stringProgram));
    assertEquals(stringProgram, MooParser.parse(MooUnparser.unparse(stringProgram)));
  }

  @Test
  void preservesOperatorPrecedenceAndAssociativity() {
    List<String> sources =
        List.of(
            "return 1 - (2 - 3);",
            "return (1 ^ 2) ^ 3;",
            "return 1 ^ 2 ^ 3;",
            "return -(1 + 2) * 3;",
            "return a || b && c == d + e * f ^ g;");

    for (String source : sources) {
      Ast.Program parsed = MooParser.parse(source);
      assertEquals(parsed, MooParser.parse(MooUnparser.unparse(parsed)), source);
    }
  }

  @Test
  void laysOutEveryStatementAndErrorSelectorDeterministically() {
    String source =
        """
        if(a) while(b) c=1; endwhile
        elseif(d) return 2;
        else return;
        endif
        for item in({1,2}) fork(0) notify(#1,item); endfork endfor
        try danger();
        except problem(E_INVARG,E_TYPE) return problem;
        except(ANY) return 0;
        finally cleanup();
        endtry
        """;
    String expected =
        """
        if (a)
          while (b)
            c = 1;
          endwhile
        elseif (d)
          return 2;
        else
          return;
        endif
        for item in ({1, 2})
          fork (0)
            notify(#1, item);
          endfork
        endfor
        try
          danger();
        except problem (E_INVARG, E_TYPE)
          return problem;
        except (ANY)
          return 0;
        finally
          cleanup();
        endtry""";

    Ast.Program parsed = MooParser.parse(source);
    assertEquals(expected, MooUnparser.unparse(parsed));
    assertEquals(parsed, MooParser.parse(expected));
  }

  @Test
  void rendersAndRoundTripsNamedForkTaskIdVariable() {
    Ast.Program parsed = MooParser.parse("fork task_id(2) return task_id; endfork");
    String expected =
        """
        fork task_id (2)
          return task_id;
        endfork""";

    assertEquals(expected, MooUnparser.unparse(parsed));
    assertEquals(parsed, MooParser.parse(expected));
  }

  @Test
  void roundTripsEveryCurrentExpressionAndAssignmentTargetVariant() {
    String source =
        """
        variable = 1;
        #1.property = 2;
        items[1] = 3;
        {left, right} = {4, 5};
        values = {@items, -1, !0, 1.5, "text", #2, E_INVARG, ["key" -> 1]};
        called = length(@values);
        static_verb = #1:verb(@values);
        computed_verb = #1:("verb")();
        static_property = #1.property;
        computed_property = #1.("property");
        indexed = values[1];
        caught = `danger() ! E_INVARG, E_TYPE => 0';
        caught_any = `danger() ! ANY';
        binary = 1 + 2 - 3 * 4 / 5 % 6 ^ 7 == 8 != 9 < 10 <= 11 > 12 >= 13 in values && left || right;
        return [static_property -> computed_property];
        """;

    Ast.Program parsed = MooParser.parse(source);
    String canonical = MooUnparser.unparse(parsed);
    assertEquals(parsed, MooParser.parse(canonical));
  }
}
