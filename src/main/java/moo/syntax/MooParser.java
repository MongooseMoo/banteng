package moo.syntax;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import moo.syntax.MooLexer.Token;
import moo.syntax.MooLexer.TokenKind;

/** Concrete entry point for parsing one MOO verb body. */
public final class MooParser {
  private static final int ASSIGNMENT_PRECEDENCE = 1;
  private static final int OR_PRECEDENCE = 2;
  private static final int AND_PRECEDENCE = 3;
  private static final int COMPARISON_PRECEDENCE = 4;
  private static final int ADDITIVE_PRECEDENCE = 5;
  private static final int MULTIPLICATIVE_PRECEDENCE = 6;
  private static final int POWER_PRECEDENCE = 7;
  private static final int UNARY_PRECEDENCE = 8;
  private static final int POSTFIX_PRECEDENCE = 9;

  private final MooLexer lexer;
  private Token current;

  private MooParser(String source) {
    lexer = new MooLexer(source);
    current = lexer.next();
  }

  /** Parses a complete stored or dynamically compiled MOO verb body. */
  public static Ast.Program parse(String source) {
    return new MooParser(source).parseProgram();
  }

  private Ast.Program parseProgram() {
    List<Ast.Statement> statements = parseStatementsUntil(TokenKind.EOF);
    expect(TokenKind.EOF, "end of source");
    return new Ast.Program(statements);
  }

  private List<Ast.Statement> parseStatementsUntil(TokenKind... terminators) {
    List<Ast.Statement> statements = new ArrayList<>();
    while (!isTerminator(terminators)) {
      if (current.kind() == TokenKind.EOF) {
        throw error("unexpected end of source");
      }
      statements.add(parseStatement());
    }
    return List.copyOf(statements);
  }

  private boolean isTerminator(TokenKind... terminators) {
    for (TokenKind terminator : terminators) {
      if (current.kind() == terminator) {
        return true;
      }
    }
    return false;
  }

  private Ast.Statement parseStatement() {
    return switch (current.kind()) {
      case IF -> parseIf();
      case WHILE -> parseWhile();
      case FOR -> parseFor();
      case TRY -> parseTry();
      case RETURN -> parseReturn();
      default -> parseExpressionStatement();
    };
  }

  private Ast.If parseIf() {
    advance();
    Ast.Expression condition = parseParenthesizedExpression("if");
    List<Ast.Statement> body =
        parseStatementsUntil(TokenKind.ELSEIF, TokenKind.ELSE, TokenKind.ENDIF);

    List<Ast.ElseIf> elseIfs = new ArrayList<>();
    while (match(TokenKind.ELSEIF)) {
      Ast.Expression elseIfCondition = parseParenthesizedExpression("elseif");
      List<Ast.Statement> elseIfBody =
          parseStatementsUntil(TokenKind.ELSEIF, TokenKind.ELSE, TokenKind.ENDIF);
      elseIfs.add(new Ast.ElseIf(elseIfCondition, elseIfBody));
    }

    List<Ast.Statement> elseBody = List.of();
    if (match(TokenKind.ELSE)) {
      elseBody = parseStatementsUntil(TokenKind.ENDIF);
    }
    expectAndAdvance(TokenKind.ENDIF, "endif");
    return new Ast.If(condition, body, elseIfs, elseBody);
  }

  private Ast.While parseWhile() {
    advance();
    Ast.Expression condition = parseParenthesizedExpression("while");
    List<Ast.Statement> body = parseStatementsUntil(TokenKind.ENDWHILE);
    expectAndAdvance(TokenKind.ENDWHILE, "endwhile");
    return new Ast.While(condition, body);
  }

  private Ast.For parseFor() {
    advance();
    String variable = expect(TokenKind.IDENTIFIER, "loop variable").lexeme();
    advance();
    expectAndAdvance(TokenKind.IN, "in");
    Ast.Expression iterable = parseParenthesizedExpression("for");
    List<Ast.Statement> body = parseStatementsUntil(TokenKind.ENDFOR);
    expectAndAdvance(TokenKind.ENDFOR, "endfor");
    return new Ast.For(variable, iterable, body);
  }

  private Ast.Try parseTry() {
    advance();
    List<Ast.Statement> body =
        parseStatementsUntil(TokenKind.EXCEPT, TokenKind.FINALLY, TokenKind.ENDTRY);
    List<Ast.ExceptClause> exceptClauses = new ArrayList<>();
    while (match(TokenKind.EXCEPT)) {
      Optional<String> variable = Optional.empty();
      if (current.kind() == TokenKind.IDENTIFIER) {
        variable = Optional.of(current.lexeme());
        advance();
      }
      expectAndAdvance(TokenKind.LEFT_PAREN, "'(' after except");
      Ast.ErrorSelector errors = parseErrorSelector();
      expectAndAdvance(TokenKind.RIGHT_PAREN, "')' after except errors");
      List<Ast.Statement> exceptBody =
          parseStatementsUntil(TokenKind.EXCEPT, TokenKind.FINALLY, TokenKind.ENDTRY);
      exceptClauses.add(new Ast.ExceptClause(variable, errors, exceptBody));
    }

    Optional<Ast.FinallyClause> finallyClause = Optional.empty();
    if (match(TokenKind.FINALLY)) {
      finallyClause = Optional.of(new Ast.FinallyClause(parseStatementsUntil(TokenKind.ENDTRY)));
    }
    if (exceptClauses.isEmpty() && finallyClause.isEmpty()) {
      throw error("try requires except or finally");
    }
    expectAndAdvance(TokenKind.ENDTRY, "endtry");
    return new Ast.Try(body, exceptClauses, finallyClause);
  }

  private Ast.Return parseReturn() {
    advance();
    Optional<Ast.Expression> value = Optional.empty();
    if (current.kind() != TokenKind.SEMICOLON) {
      value = Optional.of(parseExpression(ASSIGNMENT_PRECEDENCE));
    }
    expectAndAdvance(TokenKind.SEMICOLON, "';' after return");
    return new Ast.Return(value);
  }

  private Ast.ExpressionStatement parseExpressionStatement() {
    Ast.Expression expression = parseExpression(ASSIGNMENT_PRECEDENCE);
    expectAndAdvance(TokenKind.SEMICOLON, "';' after expression");
    return new Ast.ExpressionStatement(expression);
  }

  private Ast.Expression parseParenthesizedExpression(String owner) {
    expectAndAdvance(TokenKind.LEFT_PAREN, "'(' after " + owner);
    Ast.Expression expression = parseExpression(ASSIGNMENT_PRECEDENCE);
    expectAndAdvance(TokenKind.RIGHT_PAREN, "')' after " + owner + " expression");
    return expression;
  }

  private Ast.Expression parseExpression(int minimumPrecedence) {
    Ast.Expression left = parsePrefix();
    while (true) {
      if (isPostfix(current.kind()) && POSTFIX_PRECEDENCE >= minimumPrecedence) {
        left = parsePostfix(left);
        continue;
      }
      if (current.kind() == TokenKind.EQUAL && ASSIGNMENT_PRECEDENCE >= minimumPrecedence) {
        advance();
        Ast.Expression value = parseExpression(ASSIGNMENT_PRECEDENCE);
        left = new Ast.Assignment(toAssignmentTarget(left), value);
        continue;
      }

      int precedence = binaryPrecedence(current.kind());
      if (precedence < minimumPrecedence) {
        return left;
      }
      TokenKind operator = current.kind();
      advance();
      int rightPrecedence = operator == TokenKind.CARET ? precedence : Math.addExact(precedence, 1);
      Ast.Expression right = parseExpression(rightPrecedence);
      left = new Ast.Binary(left, binaryOperator(operator), right);
    }
  }

  private Ast.Expression parsePrefix() {
    Token token = current;
    return switch (token.kind()) {
      case IDENTIFIER -> {
        advance();
        yield new Ast.Identifier(token.lexeme());
      }
      case INTEGER -> {
        advance();
        try {
          yield new Ast.IntegerLiteral(Long.parseLong(token.lexeme()));
        } catch (NumberFormatException exception) {
          throw error(token, "invalid integer literal", exception);
        }
      }
      case FLOAT -> {
        advance();
        try {
          double value = Double.parseDouble(token.lexeme());
          if (!Double.isFinite(value)) {
            throw error(
                token, "non-finite float literal", new NumberFormatException(token.lexeme()));
          }
          yield new Ast.FloatLiteral(value);
        } catch (NumberFormatException exception) {
          throw error(token, "invalid float literal", exception);
        }
      }
      case STRING -> {
        advance();
        yield new Ast.StringLiteral(token.lexeme());
      }
      case OBJECT -> {
        advance();
        try {
          yield new Ast.ObjectLiteral(Long.parseLong(token.lexeme().substring(1)));
        } catch (NumberFormatException exception) {
          throw error(token, "invalid object literal", exception);
        }
      }
      case ERROR -> {
        advance();
        yield new Ast.ErrorLiteral(token.lexeme());
      }
      case DOLLAR -> parseSystemProperty();
      case LEFT_PAREN -> {
        advance();
        Ast.Expression expression = parseExpression(ASSIGNMENT_PRECEDENCE);
        expectAndAdvance(TokenKind.RIGHT_PAREN, "')'");
        yield expression;
      }
      case LEFT_BRACE -> parseListLiteral();
      case LEFT_BRACKET -> parseMapLiteral();
      case MINUS -> {
        advance();
        yield new Ast.Unary(Ast.UnaryOperator.NEGATE, parseExpression(UNARY_PRECEDENCE));
      }
      case BANG -> {
        advance();
        yield new Ast.Unary(Ast.UnaryOperator.NOT, parseExpression(UNARY_PRECEDENCE));
      }
      case BACKTICK -> parseCatch();
      default -> throw error("expected expression");
    };
  }

  private Ast.Expression parseSystemProperty() {
    advance();
    String property = expect(TokenKind.IDENTIFIER, "system property name").lexeme();
    advance();
    return new Ast.PropertyAccess(new Ast.ObjectLiteral(0), new Ast.StringLiteral(property));
  }

  private Ast.ListLiteral parseListLiteral() {
    advance();
    List<Ast.Expression> elements = new ArrayList<>();
    if (current.kind() != TokenKind.RIGHT_BRACE) {
      do {
        boolean splice = match(TokenKind.AT);
        Ast.Expression element = parseExpression(ASSIGNMENT_PRECEDENCE);
        elements.add(splice ? new Ast.Splice(element) : element);
      } while (match(TokenKind.COMMA));
    }
    expectAndAdvance(TokenKind.RIGHT_BRACE, "'}' after list literal");
    return new Ast.ListLiteral(elements);
  }

  private Ast.MapLiteral parseMapLiteral() {
    advance();
    List<Ast.MapEntry> entries = new ArrayList<>();
    if (current.kind() != TokenKind.RIGHT_BRACKET) {
      do {
        Ast.Expression key = parseExpression(ASSIGNMENT_PRECEDENCE);
        expectAndAdvance(TokenKind.THIN_ARROW, "'->' in map literal");
        Ast.Expression value = parseExpression(ASSIGNMENT_PRECEDENCE);
        entries.add(new Ast.MapEntry(key, value));
      } while (match(TokenKind.COMMA));
    }
    expectAndAdvance(TokenKind.RIGHT_BRACKET, "']' after map literal");
    return new Ast.MapLiteral(entries);
  }

  private Ast.Catch parseCatch() {
    advance();
    Ast.Expression guarded = parseExpression(ASSIGNMENT_PRECEDENCE);
    expectAndAdvance(TokenKind.BANG, "'!' in catch expression");
    Ast.ErrorSelector errors = parseErrorSelector();
    Optional<Ast.Expression> fallback = Optional.empty();
    if (match(TokenKind.FAT_ARROW)) {
      fallback = Optional.of(parseExpression(ASSIGNMENT_PRECEDENCE));
    }
    expectAndAdvance(TokenKind.APOSTROPHE, "closing apostrophe in catch expression");
    return new Ast.Catch(guarded, errors, fallback);
  }

  private Ast.ErrorSelector parseErrorSelector() {
    if (match(TokenKind.ANY)) {
      return new Ast.AnyErrors();
    }
    List<String> errors = new ArrayList<>();
    do {
      errors.add(expect(TokenKind.ERROR, "error name or ANY").lexeme());
      advance();
    } while (match(TokenKind.COMMA));
    return new Ast.ErrorList(errors);
  }

  private Ast.Expression parsePostfix(Ast.Expression receiver) {
    return switch (current.kind()) {
      case LEFT_PAREN -> parseCall(receiver);
      case DOT -> parseProperty(receiver);
      case COLON -> {
        advance();
        Ast.Expression name;
        if (match(TokenKind.LEFT_PAREN)) {
          name = parseExpression(ASSIGNMENT_PRECEDENCE);
          expectAndAdvance(TokenKind.RIGHT_PAREN, "')' after computed verb name");
        } else {
          String staticName = expect(TokenKind.IDENTIFIER, "verb name").lexeme();
          advance();
          name = new Ast.StringLiteral(staticName);
        }
        expectAndAdvance(TokenKind.LEFT_PAREN, "'(' before verb arguments");
        List<Ast.Expression> arguments = new ArrayList<>();
        if (current.kind() != TokenKind.RIGHT_PAREN) {
          do {
            boolean splice = match(TokenKind.AT);
            Ast.Expression argument = parseExpression(ASSIGNMENT_PRECEDENCE);
            arguments.add(splice ? new Ast.Splice(argument) : argument);
          } while (match(TokenKind.COMMA));
        }
        expectAndAdvance(TokenKind.RIGHT_PAREN, "')' after verb arguments");
        yield new Ast.VerbCall(receiver, name, arguments);
      }
      case LEFT_BRACKET -> parseIndex(receiver);
      default -> throw error("expected postfix expression");
    };
  }

  private Ast.Call parseCall(Ast.Expression receiver) {
    if (!(receiver instanceof Ast.Identifier identifier)) {
      throw error("only named functions can be called");
    }
    advance();
    List<Ast.Expression> arguments = new ArrayList<>();
    if (current.kind() != TokenKind.RIGHT_PAREN) {
      do {
        boolean splice = match(TokenKind.AT);
        Ast.Expression argument = parseExpression(ASSIGNMENT_PRECEDENCE);
        arguments.add(splice ? new Ast.Splice(argument) : argument);
      } while (match(TokenKind.COMMA));
    }
    expectAndAdvance(TokenKind.RIGHT_PAREN, "')' after call arguments");
    return new Ast.Call(identifier.name(), arguments);
  }

  private Ast.PropertyAccess parseProperty(Ast.Expression receiver) {
    advance();
    Ast.Expression property;
    if (match(TokenKind.LEFT_PAREN)) {
      property = parseExpression(ASSIGNMENT_PRECEDENCE);
      expectAndAdvance(TokenKind.RIGHT_PAREN, "')' after computed property name");
    } else {
      String name = expect(TokenKind.IDENTIFIER, "property name").lexeme();
      advance();
      property = new Ast.StringLiteral(name);
    }
    return new Ast.PropertyAccess(receiver, property);
  }

  private Ast.IndexAccess parseIndex(Ast.Expression receiver) {
    advance();
    Ast.Expression index = parseExpression(ASSIGNMENT_PRECEDENCE);
    expectAndAdvance(TokenKind.RIGHT_BRACKET, "']' after index");
    return new Ast.IndexAccess(receiver, index);
  }

  private Ast.AssignmentTarget toAssignmentTarget(Ast.Expression expression) {
    if (expression instanceof Ast.Identifier identifier) {
      return new Ast.VariableTarget(identifier.name());
    }
    if (expression instanceof Ast.PropertyAccess property) {
      return new Ast.PropertyTarget(property.object(), property.property());
    }
    if (expression instanceof Ast.IndexAccess index) {
      return new Ast.IndexTarget(index.collection(), index.index());
    }
    if (expression instanceof Ast.ListLiteral list) {
      List<String> variables = new ArrayList<>();
      for (Ast.Expression element : list.elements()) {
        if (!(element instanceof Ast.Identifier identifier)) {
          throw error("scatter assignment requires variable targets");
        }
        variables.add(identifier.name());
      }
      if (variables.isEmpty()) {
        throw error("scatter assignment requires at least one target");
      }
      return new Ast.ScatterTarget(variables);
    }
    throw error("invalid assignment target");
  }

  private static boolean isPostfix(TokenKind kind) {
    return kind == TokenKind.LEFT_PAREN
        || kind == TokenKind.DOT
        || kind == TokenKind.COLON
        || kind == TokenKind.LEFT_BRACKET;
  }

  private static int binaryPrecedence(TokenKind kind) {
    return switch (kind) {
      case OR_OR -> OR_PRECEDENCE;
      case AND_AND -> AND_PRECEDENCE;
      case EQUAL_EQUAL,
          NOT_EQUAL,
          LESS_THAN,
          LESS_THAN_OR_EQUAL,
          GREATER_THAN,
          GREATER_THAN_OR_EQUAL ->
          COMPARISON_PRECEDENCE;
      case PLUS, MINUS -> ADDITIVE_PRECEDENCE;
      case STAR, SLASH, PERCENT -> MULTIPLICATIVE_PRECEDENCE;
      case CARET -> POWER_PRECEDENCE;
      default -> -1;
    };
  }

  private static Ast.BinaryOperator binaryOperator(TokenKind kind) {
    return switch (kind) {
      case PLUS -> Ast.BinaryOperator.ADD;
      case MINUS -> Ast.BinaryOperator.SUBTRACT;
      case STAR -> Ast.BinaryOperator.MULTIPLY;
      case SLASH -> Ast.BinaryOperator.DIVIDE;
      case PERCENT -> Ast.BinaryOperator.REMAINDER;
      case CARET -> Ast.BinaryOperator.POWER;
      case EQUAL_EQUAL -> Ast.BinaryOperator.EQUAL;
      case NOT_EQUAL -> Ast.BinaryOperator.NOT_EQUAL;
      case LESS_THAN -> Ast.BinaryOperator.LESS_THAN;
      case LESS_THAN_OR_EQUAL -> Ast.BinaryOperator.LESS_THAN_OR_EQUAL;
      case GREATER_THAN -> Ast.BinaryOperator.GREATER_THAN;
      case GREATER_THAN_OR_EQUAL -> Ast.BinaryOperator.GREATER_THAN_OR_EQUAL;
      case AND_AND -> Ast.BinaryOperator.AND;
      case OR_OR -> Ast.BinaryOperator.OR;
      default -> throw new AssertionError("not a binary operator: " + kind);
    };
  }

  private boolean match(TokenKind kind) {
    if (current.kind() != kind) {
      return false;
    }
    advance();
    return true;
  }

  private Token expect(TokenKind kind, String expected) {
    if (current.kind() != kind) {
      throw error("expected " + expected + " but found '" + current.lexeme() + "'");
    }
    return current;
  }

  private void expectAndAdvance(TokenKind kind, String expected) {
    expect(kind, expected);
    advance();
  }

  private void advance() {
    current = lexer.next();
  }

  private ParseException error(String message) {
    return new ParseException(current.line(), current.column(), message);
  }

  private static ParseException error(Token token, String message, RuntimeException cause) {
    return new ParseException(token.line(), token.column(), message, cause);
  }

  /** Source-located syntax failure. */
  public static final class ParseException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final int line;
    private final int column;

    ParseException(int line, int column, String message) {
      super("line " + line + ", column " + column + ": " + message);
      this.line = line;
      this.column = column;
    }

    ParseException(int line, int column, String message, RuntimeException cause) {
      super("line " + line + ", column " + column + ": " + message, cause);
      this.line = line;
      this.column = column;
    }

    public int line() {
      return line;
    }

    public int column() {
      return column;
    }
  }
}
