package moo.syntax;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Canonically renders the complete MOO syntax tree accepted by {@link MooParser}. */
public final class MooUnparser {
  private static final int ASSIGNMENT_PRECEDENCE = 1;
  private static final int TERNARY_PRECEDENCE = 2;
  private static final int OR_PRECEDENCE = 3;
  private static final int AND_PRECEDENCE = 4;
  private static final int COMPARISON_PRECEDENCE = 5;
  private static final int ADDITIVE_PRECEDENCE = 6;
  private static final int MULTIPLICATIVE_PRECEDENCE = 7;
  private static final int POWER_PRECEDENCE = 8;
  private static final int UNARY_PRECEDENCE = 9;
  private static final int POSTFIX_PRECEDENCE = 10;
  private static final int PRIMARY_PRECEDENCE = 11;
  private static final Set<String> KEYWORDS =
      Set.of(
          "if",
          "elseif",
          "else",
          "endif",
          "while",
          "endwhile",
          "for",
          "in",
          "endfor",
          "fork",
          "endfork",
          "try",
          "except",
          "finally",
          "endtry",
          "return",
          "any");

  private MooUnparser() {}

  /** Returns deterministic line-oriented MOO source for {@code program}. */
  public static String unparse(Ast.Program program) {
    Objects.requireNonNull(program, "program");
    Renderer renderer = new Renderer();
    renderer.statements(program.statements(), 0);
    return renderer.text.toString();
  }

  private static final class Renderer {
    private final StringBuilder text = new StringBuilder();

    private void statements(List<Ast.Statement> statements, int indentation) {
      for (Ast.Statement statement : statements) {
        statement(statement, indentation);
      }
    }

    private void statement(Ast.Statement statement, int indentation) {
      if (statement instanceof Ast.ExpressionStatement expressionStatement) {
        line(
            indentation, expression(expressionStatement.expression(), ASSIGNMENT_PRECEDENCE) + ";");
        return;
      }
      if (statement instanceof Ast.Return returnStatement) {
        line(
            indentation,
            returnStatement.value().isPresent()
                ? "return "
                    + expression(returnStatement.value().orElseThrow(), ASSIGNMENT_PRECEDENCE)
                    + ";"
                : "return;");
        return;
      }
      if (statement instanceof Ast.If ifStatement) {
        line(
            indentation, "if (" + expression(ifStatement.condition(), ASSIGNMENT_PRECEDENCE) + ")");
        statements(ifStatement.body(), indentation + 1);
        for (Ast.ElseIf elseIf : ifStatement.elseIfs()) {
          line(
              indentation,
              "elseif (" + expression(elseIf.condition(), ASSIGNMENT_PRECEDENCE) + ")");
          statements(elseIf.body(), indentation + 1);
        }
        if (!ifStatement.elseBody().isEmpty()) {
          line(indentation, "else");
          statements(ifStatement.elseBody(), indentation + 1);
        }
        line(indentation, "endif");
        return;
      }
      if (statement instanceof Ast.While whileStatement) {
        line(
            indentation,
            "while (" + expression(whileStatement.condition(), ASSIGNMENT_PRECEDENCE) + ")");
        statements(whileStatement.body(), indentation + 1);
        line(indentation, "endwhile");
        return;
      }
      if (statement instanceof Ast.For forStatement) {
        line(
            indentation,
            "for "
                + forStatement.variable()
                + forStatement.indexVariable().map(index -> ", " + index).orElse("")
                + " in ("
                + expression(forStatement.iterable(), ASSIGNMENT_PRECEDENCE)
                + ")");
        statements(forStatement.body(), indentation + 1);
        line(indentation, "endfor");
        return;
      }
      if (statement instanceof Ast.Break breakStatement) {
        line(
            indentation,
            breakStatement.loopVariable().map(name -> "break " + name + ";").orElse("break;"));
        return;
      }
      if (statement instanceof Ast.Fork forkStatement) {
        line(
            indentation, "fork (" + expression(forkStatement.delay(), ASSIGNMENT_PRECEDENCE) + ")");
        statements(forkStatement.body(), indentation + 1);
        line(indentation, "endfork");
        return;
      }
      if (statement instanceof Ast.Try tryStatement) {
        line(indentation, "try");
        statements(tryStatement.body(), indentation + 1);
        for (Ast.ExceptClause clause : tryStatement.exceptClauses()) {
          line(
              indentation,
              "except"
                  + clause.variable().map(variable -> " " + variable).orElse("")
                  + " ("
                  + errors(clause.errors())
                  + ")");
          statements(clause.body(), indentation + 1);
        }
        if (tryStatement.finallyClause().isPresent()) {
          line(indentation, "finally");
          statements(tryStatement.finallyClause().orElseThrow().body(), indentation + 1);
        }
        line(indentation, "endtry");
        return;
      }
      throw new IllegalArgumentException("unsupported statement: " + statement);
    }

    private void line(int indentation, String line) {
      if (!text.isEmpty()) {
        text.append('\n');
      }
      text.append("  ".repeat(indentation)).append(line);
    }
  }

  private static String expression(Ast.Expression expression, int minimumPrecedence) {
    int precedence = precedence(expression);
    String rendered;
    if (expression instanceof Ast.Identifier identifier) {
      rendered = identifier.name();
    } else if (expression instanceof Ast.IntegerLiteral integer) {
      rendered = Long.toString(integer.value());
    } else if (expression instanceof Ast.FloatLiteral floating) {
      if (!Double.isFinite(floating.value())) {
        throw new IllegalArgumentException("non-finite float literal");
      }
      rendered = Double.toString(floating.value());
    } else if (expression instanceof Ast.StringLiteral string) {
      rendered = quote(string.value());
    } else if (expression instanceof Ast.ObjectLiteral object) {
      rendered = "#" + object.value();
    } else if (expression instanceof Ast.ErrorLiteral error) {
      rendered = error.name();
    } else if (expression instanceof Ast.ListLiteral list) {
      rendered = "{" + joinExpressions(list.elements()) + "}";
    } else if (expression instanceof Ast.MapLiteral map) {
      StringBuilder entries = new StringBuilder();
      for (Ast.MapEntry entry : map.entries()) {
        if (!entries.isEmpty()) {
          entries.append(", ");
        }
        entries
            .append(expression(entry.key(), ASSIGNMENT_PRECEDENCE))
            .append(" -> ")
            .append(expression(entry.value(), ASSIGNMENT_PRECEDENCE));
      }
      rendered = "[" + entries + "]";
    } else if (expression instanceof Ast.Splice splice) {
      rendered = "@" + expression(splice.value(), UNARY_PRECEDENCE);
    } else if (expression instanceof Ast.Call call) {
      rendered = call.name() + "(" + joinExpressions(call.arguments()) + ")";
    } else if (expression instanceof Ast.VerbCall verbCall) {
      Optional<String> name = staticName(verbCall.name());
      rendered =
          expression(verbCall.object(), POSTFIX_PRECEDENCE)
              + ":"
              + (name.isEmpty()
                  ? "(" + expression(verbCall.name(), ASSIGNMENT_PRECEDENCE) + ")"
                  : name.orElseThrow())
              + "("
              + joinExpressions(verbCall.arguments())
              + ")";
    } else if (expression instanceof Ast.Assignment assignment) {
      rendered =
          assignmentTarget(assignment.target())
              + " = "
              + expression(assignment.value(), ASSIGNMENT_PRECEDENCE);
    } else if (expression instanceof Ast.PropertyAccess property) {
      Optional<String> name = staticName(property.property());
      rendered =
          expression(property.object(), POSTFIX_PRECEDENCE)
              + "."
              + (name.isEmpty()
                  ? "(" + expression(property.property(), ASSIGNMENT_PRECEDENCE) + ")"
                  : name.orElseThrow());
    } else if (expression instanceof Ast.IndexAccess index) {
      rendered =
          expression(index.collection(), POSTFIX_PRECEDENCE)
              + "["
              + expression(index.index(), ASSIGNMENT_PRECEDENCE)
              + "]";
    } else if (expression instanceof Ast.RangeAccess range) {
      rendered =
          expression(range.collection(), POSTFIX_PRECEDENCE)
              + "["
              + expression(range.start(), ASSIGNMENT_PRECEDENCE)
              + ".."
              + expression(range.end(), ASSIGNMENT_PRECEDENCE)
              + "]";
    } else if (expression instanceof Ast.FirstIndex) {
      rendered = "^";
    } else if (expression instanceof Ast.LastIndex) {
      rendered = "$";
    } else if (expression instanceof Ast.Unary unary) {
      rendered =
          (unary.operator() == Ast.UnaryOperator.NEGATE ? "-" : "!")
              + expression(unary.operand(), UNARY_PRECEDENCE);
    } else if (expression instanceof Ast.Binary binary) {
      int operatorPrecedence = binaryPrecedence(binary.operator());
      boolean rightAssociative = binary.operator() == Ast.BinaryOperator.POWER;
      rendered =
          expression(binary.left(), operatorPrecedence + (rightAssociative ? 1 : 0))
              + " "
              + binaryOperator(binary.operator())
              + " "
              + expression(binary.right(), operatorPrecedence + (rightAssociative ? 0 : 1));
    } else if (expression instanceof Ast.Ternary ternary) {
      rendered =
          expression(ternary.condition(), TERNARY_PRECEDENCE + 1)
              + " ? "
              + expression(ternary.trueExpression(), ASSIGNMENT_PRECEDENCE)
              + " | "
              + expression(ternary.falseExpression(), ASSIGNMENT_PRECEDENCE);
    } else if (expression instanceof Ast.Catch catchExpression) {
      rendered =
          "`"
              + expression(catchExpression.guarded(), ASSIGNMENT_PRECEDENCE)
              + " ! "
              + errors(catchExpression.errors())
              + catchExpression
                  .fallback()
                  .map(value -> " => " + expression(value, ASSIGNMENT_PRECEDENCE))
                  .orElse("")
              + "'";
    } else {
      throw new IllegalArgumentException("unsupported expression: " + expression);
    }
    return precedence < minimumPrecedence ? "(" + rendered + ")" : rendered;
  }

  private static String assignmentTarget(Ast.AssignmentTarget target) {
    if (target instanceof Ast.VariableTarget variable) {
      return variable.name();
    }
    if (target instanceof Ast.PropertyTarget property) {
      Optional<String> name = staticName(property.property());
      return expression(property.object(), POSTFIX_PRECEDENCE)
          + "."
          + (name.isEmpty()
              ? "(" + expression(property.property(), ASSIGNMENT_PRECEDENCE) + ")"
              : name.orElseThrow());
    }
    if (target instanceof Ast.IndexTarget index) {
      return expression(index.collection(), POSTFIX_PRECEDENCE)
          + "["
          + expression(index.index(), ASSIGNMENT_PRECEDENCE)
          + "]";
    }
    if (target instanceof Ast.RangeTarget range) {
      return expression(range.collection(), POSTFIX_PRECEDENCE)
          + "["
          + expression(range.start(), ASSIGNMENT_PRECEDENCE)
          + ".."
          + expression(range.end(), ASSIGNMENT_PRECEDENCE)
          + "]";
    }
    if (target instanceof Ast.ScatterTarget scatter) {
      return "{" + String.join(", ", scatter.variables()) + "}";
    }
    throw new IllegalArgumentException("unsupported assignment target: " + target);
  }

  private static String joinExpressions(List<Ast.Expression> expressions) {
    StringBuilder joined = new StringBuilder();
    for (Ast.Expression expression : expressions) {
      if (!joined.isEmpty()) {
        joined.append(", ");
      }
      joined.append(expression(expression, ASSIGNMENT_PRECEDENCE));
    }
    return joined.toString();
  }

  private static Optional<String> staticName(Ast.Expression expression) {
    if (!(expression instanceof Ast.StringLiteral string) || !isIdentifier(string.value())) {
      return Optional.empty();
    }
    return Optional.of(string.value());
  }

  private static boolean isIdentifier(String value) {
    if (value.isEmpty()
        || !(Character.isLetter(value.charAt(0)) || value.charAt(0) == '_')
        || value.startsWith("E_")
        || KEYWORDS.contains(value.toLowerCase(Locale.ROOT))) {
      return false;
    }
    for (int index = 1; index < value.length(); index++) {
      char character = value.charAt(index);
      if (!(Character.isLetterOrDigit(character) || character == '_')) {
        return false;
      }
    }
    return true;
  }

  private static String errors(Ast.ErrorSelector selector) {
    if (selector instanceof Ast.AnyErrors) {
      return "ANY";
    }
    if (selector instanceof Ast.ErrorList errors) {
      return String.join(", ", errors.names());
    }
    throw new IllegalArgumentException("unsupported error selector: " + selector);
  }

  private static int precedence(Ast.Expression expression) {
    if (expression instanceof Ast.Assignment) {
      return ASSIGNMENT_PRECEDENCE;
    }
    if (expression instanceof Ast.Ternary) {
      return TERNARY_PRECEDENCE;
    }
    if (expression instanceof Ast.Binary binary) {
      return binaryPrecedence(binary.operator());
    }
    if (expression instanceof Ast.Unary || expression instanceof Ast.Splice) {
      return UNARY_PRECEDENCE;
    }
    if (expression instanceof Ast.PropertyAccess
        || expression instanceof Ast.IndexAccess
        || expression instanceof Ast.VerbCall
        || expression instanceof Ast.Call) {
      return POSTFIX_PRECEDENCE;
    }
    return PRIMARY_PRECEDENCE;
  }

  private static int binaryPrecedence(Ast.BinaryOperator operator) {
    return switch (operator) {
      case OR -> OR_PRECEDENCE;
      case AND -> AND_PRECEDENCE;
      case EQUAL,
          NOT_EQUAL,
          LESS_THAN,
          LESS_THAN_OR_EQUAL,
          GREATER_THAN,
          GREATER_THAN_OR_EQUAL,
          IN ->
          COMPARISON_PRECEDENCE;
      case ADD, SUBTRACT -> ADDITIVE_PRECEDENCE;
      case MULTIPLY, DIVIDE, REMAINDER -> MULTIPLICATIVE_PRECEDENCE;
      case POWER -> POWER_PRECEDENCE;
    };
  }

  private static String binaryOperator(Ast.BinaryOperator operator) {
    return switch (operator) {
      case ADD -> "+";
      case SUBTRACT -> "-";
      case MULTIPLY -> "*";
      case DIVIDE -> "/";
      case REMAINDER -> "%";
      case POWER -> "^";
      case EQUAL -> "==";
      case NOT_EQUAL -> "!=";
      case LESS_THAN -> "<";
      case LESS_THAN_OR_EQUAL -> "<=";
      case GREATER_THAN -> ">";
      case GREATER_THAN_OR_EQUAL -> ">=";
      case IN -> "in";
      case AND -> "&&";
      case OR -> "||";
    };
  }

  private static String quote(String value) {
    StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character == '"' || character == '\\') {
        quoted.append('\\');
      }
      quoted.append(character);
    }
    return quoted.append('"').toString();
  }
}
