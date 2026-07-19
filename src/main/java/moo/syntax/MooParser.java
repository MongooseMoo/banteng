package moo.syntax;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import moo.syntax.MooLexer.Token;
import moo.syntax.MooLexer.TokenKind;

/** Concrete entry point for parsing one MOO verb body. */
public final class MooParser {
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

  private final MooLexer lexer;
  private Token current;
  private int previousEndOffset;
  private int indexDepth;

  private MooParser(String source) {
    lexer = new MooLexer(source);
    current = lexer.next();
  }

  /** Parses a complete stored or dynamically compiled MOO verb body. */
  public static Ast.Program parse(String source) {
    return new MooParser(source).parseProgram();
  }

  /** Parses one ISO-8859-1 MOO source byte sequence. */
  public static Ast.Program parse(byte[] source) {
    return parse(new String(source, StandardCharsets.ISO_8859_1));
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
      if (current.kind() == TokenKind.SEMICOLON) {
        advance();
        continue;
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
      case BREAK -> parseBreak();
      case CONTINUE -> parseContinue();
      case FORK -> parseFork();
      case TRY -> parseTry();
      case RETURN -> parseReturn();
      default -> parseExpressionStatement();
    };
  }

  private Ast.If parseIf() {
    Token firstToken = current;
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
    Token endIf = current;
    expectAndAdvance(TokenKind.ENDIF, "endif");
    return new Ast.If(
        condition,
        body,
        elseIfs,
        elseBody,
        Optional.of(
            new Ast.SourceSpan(
                firstToken.startOffset(),
                endIf.endOffset(),
                firstToken.line(),
                firstToken.column())));
  }

  private Ast.While parseWhile() {
    advance();
    Optional<String> loopVariable = Optional.empty();
    if (current.kind() == TokenKind.IDENTIFIER) {
      loopVariable = Optional.of(current.lexeme());
      advance();
    }
    Ast.Expression condition = parseParenthesizedExpression("while");
    List<Ast.Statement> body = parseStatementsUntil(TokenKind.ENDWHILE);
    expectAndAdvance(TokenKind.ENDWHILE, "endwhile");
    return new Ast.While(loopVariable, condition, body);
  }

  private Ast.For parseFor() {
    advance();
    String variable = expect(TokenKind.IDENTIFIER, "loop variable").lexeme();
    advance();
    Optional<String> indexVariable = Optional.empty();
    if (match(TokenKind.COMMA)) {
      indexVariable = Optional.of(expect(TokenKind.IDENTIFIER, "loop index variable").lexeme());
      advance();
    }
    expectAndAdvance(TokenKind.IN, "in");
    Ast.Expression iterable = parseParenthesizedExpression("for");
    List<Ast.Statement> body = parseStatementsUntil(TokenKind.ENDFOR);
    expectAndAdvance(TokenKind.ENDFOR, "endfor");
    return new Ast.For(variable, indexVariable, iterable, body);
  }

  private Ast.Break parseBreak() {
    advance();
    Optional<String> loopVariable = Optional.empty();
    if (current.kind() == TokenKind.IDENTIFIER) {
      loopVariable = Optional.of(current.lexeme());
      advance();
    }
    expectAndAdvance(TokenKind.SEMICOLON, "';' after break");
    return new Ast.Break(loopVariable);
  }

  private Ast.Continue parseContinue() {
    advance();
    Optional<String> loopVariable = Optional.empty();
    if (current.kind() == TokenKind.IDENTIFIER) {
      loopVariable = Optional.of(current.lexeme());
      advance();
    }
    expectAndAdvance(TokenKind.SEMICOLON, "';' after continue");
    return new Ast.Continue(loopVariable);
  }

  private Ast.Fork parseFork() {
    advance();
    Ast.Expression delay = parseParenthesizedExpression("fork");
    List<Ast.Statement> body = parseStatementsUntil(TokenKind.ENDFORK);
    expectAndAdvance(TokenKind.ENDFORK, "endfork");
    return new Ast.Fork(delay, body);
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
    Token returnToken = current;
    advance();
    Optional<Ast.Expression> value = Optional.empty();
    if (current.kind() != TokenKind.SEMICOLON) {
      value = Optional.of(parseExpression(ASSIGNMENT_PRECEDENCE));
    }
    Token semicolon = current;
    expectAndAdvance(TokenKind.SEMICOLON, "';' after return");
    return new Ast.Return(
        value,
        Optional.of(
            new Ast.SourceSpan(
                returnToken.startOffset(),
                semicolon.endOffset(),
                returnToken.line(),
                returnToken.column())));
  }

  private Ast.ExpressionStatement parseExpressionStatement() {
    Token firstToken = current;
    Ast.Expression expression = parseExpression(ASSIGNMENT_PRECEDENCE);
    Token semicolon = current;
    expectAndAdvance(TokenKind.SEMICOLON, "';' after expression");
    return new Ast.ExpressionStatement(
        expression,
        Optional.of(
            new Ast.SourceSpan(
                firstToken.startOffset(),
                semicolon.endOffset(),
                firstToken.line(),
                firstToken.column())));
  }

  private Ast.Expression parseParenthesizedExpression(String owner) {
    expectAndAdvance(TokenKind.LEFT_PAREN, "'(' after " + owner);
    Ast.Expression expression = parseExpression(ASSIGNMENT_PRECEDENCE);
    expectAndAdvance(TokenKind.RIGHT_PAREN, "')' after " + owner + " expression");
    return expression;
  }

  private Ast.Expression parseExpression(int minimumPrecedence) {
    Token firstToken = current;
    Ast.Expression left = parsePrefix();
    while (true) {
      if (isPostfix(current.kind()) && POSTFIX_PRECEDENCE >= minimumPrecedence) {
        left = parsePostfix(left, firstToken);
        continue;
      }
      if (current.kind() == TokenKind.EQUAL && ASSIGNMENT_PRECEDENCE >= minimumPrecedence) {
        advance();
        Ast.Expression value = parseExpression(ASSIGNMENT_PRECEDENCE);
        left =
            new Ast.Assignment(
                toAssignmentTarget(left),
                value,
                Optional.of(
                    new Ast.SourceSpan(
                        firstToken.startOffset(),
                        previousEndOffset,
                        firstToken.line(),
                        firstToken.column())));
        continue;
      }
      if (current.kind() == TokenKind.QUESTION && TERNARY_PRECEDENCE >= minimumPrecedence) {
        advance();
        Ast.Expression trueExpression = parseExpression(ASSIGNMENT_PRECEDENCE);
        expectAndAdvance(TokenKind.PIPE, "'|' in ternary expression");
        Ast.Expression falseExpression = parseExpression(ASSIGNMENT_PRECEDENCE);
        left =
            new Ast.Ternary(
                left,
                trueExpression,
                falseExpression,
                Optional.of(
                    new Ast.SourceSpan(
                        firstToken.startOffset(),
                        previousEndOffset,
                        firstToken.line(),
                        firstToken.column())));
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
      left =
          new Ast.Binary(
              left,
              binaryOperator(operator),
              right,
              Optional.of(
                  new Ast.SourceSpan(
                      firstToken.startOffset(),
                      previousEndOffset,
                      firstToken.line(),
                      firstToken.column())));
    }
  }

  private Ast.Expression parsePrefix() {
    Token token = current;
    return switch (token.kind()) {
      case IDENTIFIER -> {
        advance();
        yield new Ast.Identifier(
            token.lexeme(),
            Optional.of(
                new Ast.SourceSpan(
                    token.startOffset(), token.endOffset(), token.line(), token.column())));
      }
      case INTEGER -> {
        advance();
        try {
          yield new Ast.IntegerLiteral(
              new BigInteger(token.lexeme()).longValue(),
              Optional.of(
                  new Ast.SourceSpan(
                      token.startOffset(),
                      token.endOffset(),
                      token.line(),
                      token.column())));
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
          yield new Ast.FloatLiteral(
              value,
              Optional.of(
                  new Ast.SourceSpan(
                      token.startOffset(),
                      token.endOffset(),
                      token.line(),
                      token.column())));
        } catch (NumberFormatException exception) {
          throw error(token, "invalid float literal", exception);
        }
      }
      case STRING -> {
        advance();
        yield new Ast.StringLiteral(
            token.lexeme(),
            Optional.of(
                new Ast.SourceSpan(
                    token.startOffset(),
                    token.endOffset(),
                    token.line(),
                    token.column())));
      }
      case OBJECT -> {
        advance();
        try {
          yield new Ast.ObjectLiteral(
              new BigInteger(token.lexeme().substring(1)).longValue(),
              Optional.of(
                  new Ast.SourceSpan(
                      token.startOffset(),
                      token.endOffset(),
                      token.line(),
                      token.column())));
        } catch (NumberFormatException exception) {
          throw error(token, "invalid object literal", exception);
        }
      }
      case ERROR -> {
        advance();
        yield new Ast.ErrorLiteral(
            token.lexeme(),
            Optional.of(
                new Ast.SourceSpan(
                    token.startOffset(),
                    token.endOffset(),
                    token.line(),
                    token.column())));
      }
      case DOLLAR -> {
        if (indexDepth == 0) {
          yield parseSystemProperty();
        }
        advance();
        yield new Ast.LastIndex(
            Optional.of(
                new Ast.SourceSpan(
                    token.startOffset(), token.endOffset(), token.line(), token.column())));
      }
      case LEFT_PAREN -> {
        advance();
        Ast.Expression expression = parseExpression(ASSIGNMENT_PRECEDENCE);
        expectAndAdvance(TokenKind.RIGHT_PAREN, "')'");
        yield expression;
      }
      case LEFT_BRACE -> parseListLiteral();
      case LEFT_BRACKET -> parseMapLiteral();
      case CARET -> {
        if (indexDepth == 0) {
          throw error("'^' is only valid inside an index expression");
        }
        advance();
        yield new Ast.FirstIndex(
            Optional.of(
                new Ast.SourceSpan(
                    token.startOffset(), token.endOffset(), token.line(), token.column())));
      }
      case MINUS -> {
        advance();
        yield new Ast.Unary(Ast.UnaryOperator.NEGATE, parseExpression(UNARY_PRECEDENCE));
      }
      case BANG -> {
        advance();
        Ast.Expression operand = parseExpression(UNARY_PRECEDENCE);
        yield new Ast.Unary(
            Ast.UnaryOperator.NOT,
            operand,
            Optional.of(
                new Ast.SourceSpan(
                    token.startOffset(), previousEndOffset, token.line(), token.column())));
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
    Token leftBrace = current;
    advance();
    List<Ast.Expression> elements = new ArrayList<>();
    if (current.kind() != TokenKind.RIGHT_BRACE) {
      do {
        boolean splice = match(TokenKind.AT);
        boolean optional = !splice && match(TokenKind.QUESTION);
        Ast.Expression element;
        if (optional) {
          Token identifier = expect(TokenKind.IDENTIFIER, "optional scatter variable");
          advance();
          Optional<Ast.Expression> defaultValue = Optional.empty();
          if (match(TokenKind.EQUAL)) {
            defaultValue = Optional.of(parseExpression(ASSIGNMENT_PRECEDENCE));
          }
          element = new Ast.ScatterElement(identifier.lexeme(), false, true, defaultValue);
        } else {
          element = parseExpression(ASSIGNMENT_PRECEDENCE);
        }
        elements.add(splice ? new Ast.Splice(element) : element);
      } while (match(TokenKind.COMMA));
    }
    Token rightBrace = current;
    expectAndAdvance(TokenKind.RIGHT_BRACE, "'}' after list literal");
    return new Ast.ListLiteral(
        elements,
        Optional.of(
            new Ast.SourceSpan(
                leftBrace.startOffset(),
                rightBrace.endOffset(),
                leftBrace.line(),
                leftBrace.column())));
  }

  private Ast.MapLiteral parseMapLiteral() {
    Token leftBracket = current;
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
    Token rightBracket = current;
    expectAndAdvance(TokenKind.RIGHT_BRACKET, "']' after map literal");
    return new Ast.MapLiteral(
        entries,
        Optional.of(
            new Ast.SourceSpan(
                leftBracket.startOffset(),
                rightBracket.endOffset(),
                leftBracket.line(),
                leftBracket.column())));
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

  private Ast.Expression parsePostfix(Ast.Expression receiver, Token firstToken) {
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
      case LEFT_BRACKET -> parseIndex(receiver, firstToken);
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

  private Ast.Expression parseIndex(Ast.Expression receiver, Token firstToken) {
    advance();
    indexDepth++;
    try {
      Ast.Expression index = parseExpression(ASSIGNMENT_PRECEDENCE);
      if (match(TokenKind.RANGE)) {
        Ast.Expression end = parseExpression(ASSIGNMENT_PRECEDENCE);
        Token rightBracket = current;
        expectAndAdvance(TokenKind.RIGHT_BRACKET, "']' after range");
        return new Ast.RangeAccess(
            receiver,
            index,
            end,
            Optional.of(
                new Ast.SourceSpan(
                    firstToken.startOffset(),
                    rightBracket.endOffset(),
                    firstToken.line(),
                    firstToken.column())));
      }
      Token rightBracket = current;
      expectAndAdvance(TokenKind.RIGHT_BRACKET, "']' after index");
      return new Ast.IndexAccess(
          receiver,
          index,
          Optional.of(
              new Ast.SourceSpan(
                  firstToken.startOffset(),
                  rightBracket.endOffset(),
                  firstToken.line(),
                  firstToken.column())));
    } finally {
      indexDepth--;
    }
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
    if (expression instanceof Ast.RangeAccess range) {
      return new Ast.RangeTarget(range.collection(), range.start(), range.end());
    }
    if (expression instanceof Ast.ListLiteral list) {
      List<Ast.ScatterElement> elements = new ArrayList<>();
      for (Ast.Expression element : list.elements()) {
        if (element instanceof Ast.Identifier identifier) {
          elements.add(
              new Ast.ScatterElement(identifier.name(), false, false, Optional.empty()));
        } else if (element instanceof Ast.ScatterElement optional) {
          elements.add(optional);
        } else if (element instanceof Ast.Splice splice
            && splice.value() instanceof Ast.Identifier identifier
            && elements.stream().noneMatch(Ast.ScatterElement::rest)) {
          elements.add(new Ast.ScatterElement(identifier.name(), true, false, Optional.empty()));
        } else {
          throw error("scatter assignment requires variable targets");
        }
      }
      if (elements.isEmpty()) {
        throw error("scatter assignment requires at least one target");
      }
      return new Ast.ScatterTarget(elements);
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
          GREATER_THAN_OR_EQUAL,
          IN ->
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
      case IN -> Ast.BinaryOperator.IN;
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
    previousEndOffset = current.endOffset();
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
