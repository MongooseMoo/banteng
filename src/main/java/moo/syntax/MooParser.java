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
  private static final int AND_PRECEDENCE = OR_PRECEDENCE;
  private static final int COMPARISON_PRECEDENCE = 5;
  private static final int BITWISE_PRECEDENCE = 6;
  private static final int SHIFT_PRECEDENCE = 7;
  private static final int ADDITIVE_PRECEDENCE = 8;
  private static final int MULTIPLICATIVE_PRECEDENCE = 9;
  private static final int POWER_PRECEDENCE = 10;
  private static final int UNARY_PRECEDENCE = 11;
  private static final int POSTFIX_PRECEDENCE = 12;
  private static final int MAX_EXPRESSION_DEPTH = 256;

  private final MooLexer lexer;
  private Token current;
  private int previousEndOffset;
  private int indexDepth;
  private int expressionDepth;
  private List<ParseDiagnostic> recoveringDiagnostics;

  private MooParser(String source) {
    lexer = new MooLexer(source);
    current = lexer.next();
  }

  /** Parses a complete stored or dynamically compiled MOO verb body. */
  public static Ast.Program parse(String source) {
    return new MooParser(source).parseProgram();
  }

  /** Parses one verb body while collecting every recoverable source diagnostic. */
  public static ParseResult parseResult(String source) {
    try {
      return new MooParser(source).parseProgramResult();
    } catch (ParseException error) {
      return new ParseResult(
          Optional.of(new Ast.Program(List.of())),
          List.of(new ParseDiagnostic(error.line(), error.column(), error.detail())));
    }
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

  private ParseResult parseProgramResult() {
    List<Ast.Statement> statements = new ArrayList<>();
    List<ParseDiagnostic> diagnostics = new ArrayList<>();
    recoveringDiagnostics = diagnostics;
    while (current.kind() != TokenKind.EOF) {
      if (current.kind() == TokenKind.SEMICOLON) {
        advance();
        continue;
      }
      try {
        statements.add(parseStatement());
      } catch (ParseException error) {
        diagnostics.add(new ParseDiagnostic(error.line(), error.column(), error.detail()));
        recoverAfterStatementError(diagnostics);
      }
    }
    return new ParseResult(Optional.of(new Ast.Program(statements)), diagnostics);
  }

  private void recoverAfterStatementError(List<ParseDiagnostic> diagnostics) {
    while (current.kind() != TokenKind.EOF) {
      if (current.kind() == TokenKind.SEMICOLON) {
        advanceRecoveringLexerErrors(diagnostics);
        return;
      }
      advanceRecoveringLexerErrors(diagnostics);
    }
  }

  private boolean advanceRecoveringLexerErrors(List<ParseDiagnostic> diagnostics) {
    try {
      advance();
      return true;
    } catch (ParseException error) {
      diagnostics.add(new ParseDiagnostic(error.line(), error.column(), error.detail()));
      return false;
    }
  }

  /** One parser-owned, source-located diagnostic before protocol formatting. */
  public record ParseDiagnostic(int line, int column, String message) {}

  /** A parsed partial or complete program and its ordered diagnostics. */
  public record ParseResult(Optional<Ast.Program> program, List<ParseDiagnostic> diagnostics) {
    /** Takes an immutable snapshot of ordered diagnostics. */
    public ParseResult {
      diagnostics = List.copyOf(diagnostics);
    }
  }

  private List<Ast.Statement> parseStatementsUntil(TokenKind... terminators) {
    List<Ast.Statement> statements = new ArrayList<>();
    int diagnosticCountAtStart =
        recoveringDiagnostics == null ? 0 : recoveringDiagnostics.size();
    while (!isTerminator(terminators)) {
      if (current.kind() == TokenKind.EOF) {
        if (recoveringDiagnostics != null) {
          if (recoveringDiagnostics.size() == diagnosticCountAtStart) {
            recoveringDiagnostics.add(
                new ParseDiagnostic(current.line(), current.column(), "unexpected end of source"));
          }
          return List.copyOf(statements);
        }
        throw error("unexpected end of source");
      }
      if (current.kind() == TokenKind.SEMICOLON) {
        advance();
        continue;
      }
      try {
        statements.add(parseStatement());
      } catch (ParseException error) {
        if (recoveringDiagnostics == null) {
          throw error;
        }
        recoveringDiagnostics.add(
            new ParseDiagnostic(error.line(), error.column(), error.detail()));
        recoverAfterStatementError(recoveringDiagnostics);
      }
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
    expectAndAdvanceCompoundEnd(TokenKind.ENDIF, "endif");
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
    expectAndAdvanceCompoundEnd(TokenKind.ENDWHILE, "endwhile");
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
    Ast.Expression iterable;
    Optional<Ast.Expression> rangeEnd = Optional.empty();
    if (match(TokenKind.LEFT_BRACKET)) {
      iterable = parseExpression(ASSIGNMENT_PRECEDENCE);
      expectAndAdvance(TokenKind.RANGE, "'..' in for range");
      rangeEnd = Optional.of(parseExpression(ASSIGNMENT_PRECEDENCE));
      expectAndAdvance(TokenKind.RIGHT_BRACKET, "']' after for range");
    } else {
      iterable = parseParenthesizedExpression("for");
    }
    List<Ast.Statement> body = parseStatementsUntil(TokenKind.ENDFOR);
    expectAndAdvanceCompoundEnd(TokenKind.ENDFOR, "endfor");
    return new Ast.For(variable, indexVariable, iterable, rangeEnd, body);
  }

  private Ast.Break parseBreak() {
    Token breakToken = current;
    advance();
    Optional<String> loopVariable = Optional.empty();
    if (current.kind() == TokenKind.IDENTIFIER) {
      loopVariable = Optional.of(current.lexeme());
      advance();
    }
    Token semicolon = current;
    expectAndAdvanceStatementEnd("';' after break");
    return new Ast.Break(
        loopVariable,
        Optional.of(
            new Ast.SourceSpan(
                breakToken.startOffset(),
                semicolon.endOffset(),
                breakToken.line(),
                breakToken.column())));
  }

  private Ast.Continue parseContinue() {
    Token continueToken = current;
    advance();
    Optional<String> loopVariable = Optional.empty();
    if (current.kind() == TokenKind.IDENTIFIER) {
      loopVariable = Optional.of(current.lexeme());
      advance();
    }
    Token semicolon = current;
    expectAndAdvanceStatementEnd("';' after continue");
    return new Ast.Continue(
        loopVariable,
        Optional.of(
            new Ast.SourceSpan(
                continueToken.startOffset(),
                semicolon.endOffset(),
                continueToken.line(),
                continueToken.column())));
  }

  private Ast.Fork parseFork() {
    advance();
    Optional<String> taskIdVariable = Optional.empty();
    if (current.kind() == TokenKind.IDENTIFIER) {
      taskIdVariable = Optional.of(current.lexeme());
      advance();
    }
    Ast.Expression delay = parseParenthesizedExpression("fork");
    List<Ast.Statement> body = parseStatementsUntil(TokenKind.ENDFORK);
    expectAndAdvanceCompoundEnd(TokenKind.ENDFORK, "endfork");
    return new Ast.Fork(taskIdVariable, delay, body);
  }

  private Ast.Try parseTry() {
    advance();
    List<Ast.Statement> body =
        parseStatementsUntil(TokenKind.EXCEPT, TokenKind.FINALLY, TokenKind.ENDTRY);
    List<Ast.ExceptClause> exceptClauses = new ArrayList<>();
    while (current.kind() == TokenKind.EXCEPT) {
      Token exceptToken = current;
      if (!exceptClauses.isEmpty()
          && exceptClauses.getLast().errors() instanceof Ast.AnyErrors) {
        reportRecoverable(exceptToken, "unreachable except clause");
      } else if (exceptClauses.size() > 255) {
        reportRecoverable(exceptToken, "too many except clauses");
      }
      advance();
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
      if (recoveringDiagnostics == null || current.kind() != TokenKind.EOF) {
        throw error("try requires except or finally");
      }
    }
    expectAndAdvanceCompoundEnd(TokenKind.ENDTRY, "endtry");
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
    expectAndAdvanceStatementEnd("';' after return");
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
    expectAndAdvanceStatementEnd("';' after expression");
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
    if (expressionDepth >= MAX_EXPRESSION_DEPTH) {
      throw error("expression nesting limit exceeded");
    }
    expressionDepth++;
    try {
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
        int rightPrecedence =
            operator == TokenKind.CARET ? precedence : Math.addExact(precedence, 1);
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
    } finally {
      expressionDepth--;
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
      case TILDE -> {
        advance();
        yield new Ast.Unary(Ast.UnaryOperator.COMPLEMENT, parseExpression(UNARY_PRECEDENCE));
      }
      case BACKTICK -> parseCatch();
      default -> throw error("expected expression");
    };
  }

  private Ast.Expression parseSystemProperty() {
    Token dollar = current;
    advance();
    if (current.kind() != TokenKind.IDENTIFIER) {
      throw error(dollar, "'$' is only valid inside an index expression");
    }
    String property = expect(TokenKind.IDENTIFIER, "system property name").lexeme();
    advance();
    if (match(TokenKind.LEFT_PAREN)) {
      List<Ast.Expression> arguments = new ArrayList<>();
      if (current.kind() != TokenKind.RIGHT_PAREN) {
        do {
          boolean splice = match(TokenKind.AT);
          Ast.Expression argument = parseExpression(ASSIGNMENT_PRECEDENCE);
          arguments.add(splice ? new Ast.Splice(argument) : argument);
        } while (match(TokenKind.COMMA));
      }
      Token rightParen = current;
      expectAndAdvance(TokenKind.RIGHT_PAREN, "')' after verb arguments");
      return new Ast.VerbCall(
          new Ast.ObjectLiteral(0),
          new Ast.StringLiteral(property),
          arguments,
          Optional.of(
              new Ast.SourceSpan(
                  dollar.startOffset(),
                  rightParen.endOffset(),
                  dollar.line(),
                  dollar.column())));
    }
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
      case LEFT_PAREN -> parseCall(receiver, firstToken);
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
        Token rightParen = current;
        expectAndAdvance(TokenKind.RIGHT_PAREN, "')' after verb arguments");
        yield new Ast.VerbCall(
            receiver,
            name,
            arguments,
            Optional.of(
                new Ast.SourceSpan(
                    firstToken.startOffset(),
                    rightParen.endOffset(),
                    firstToken.line(),
                    firstToken.column())));
      }
      case LEFT_BRACKET -> parseIndex(receiver, firstToken);
      default -> throw error("expected postfix expression");
    };
  }

  private Ast.Call parseCall(Ast.Expression receiver, Token firstToken) {
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
    Token rightParen = current;
    expectAndAdvance(TokenKind.RIGHT_PAREN, "')' after call arguments");
    return new Ast.Call(
        identifier.name(),
        arguments,
        Optional.of(
            new Ast.SourceSpan(
                firstToken.startOffset(),
                rightParen.endOffset(),
                firstToken.line(),
                firstToken.column())));
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
            && splice.value() instanceof Ast.Identifier identifier) {
          if (elements.stream().anyMatch(Ast.ScatterElement::rest)) {
            reportRecoverable(
                current, "scatter assignment has multiple rest targets");
          }
          elements.add(new Ast.ScatterElement(identifier.name(), true, false, Optional.empty()));
        } else {
          throw error("scatter assignment requires variable targets");
        }
      }
      if (elements.isEmpty()) {
        throw error("scatter assignment requires at least one target");
      }
      if (elements.size() > 255) {
        throw error("scatter assignment has too many targets");
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
      case BIT_OR, BIT_AND, BIT_XOR -> BITWISE_PRECEDENCE;
      case BIT_SHIFT_LEFT, BIT_SHIFT_RIGHT -> SHIFT_PRECEDENCE;
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
      case BIT_OR -> Ast.BinaryOperator.BITOR;
      case BIT_AND -> Ast.BinaryOperator.BITAND;
      case BIT_XOR -> Ast.BinaryOperator.BITXOR;
      case BIT_SHIFT_LEFT -> Ast.BinaryOperator.BITSHL;
      case BIT_SHIFT_RIGHT -> Ast.BinaryOperator.BITSHR;
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

  private void expectAndAdvanceCompoundEnd(TokenKind kind, String expected) {
    if (recoveringDiagnostics != null && current.kind() == TokenKind.EOF) {
      return;
    }
    expectAndAdvance(kind, expected);
  }

  private void expectAndAdvanceStatementEnd(String expected) {
    expect(TokenKind.SEMICOLON, expected);
    if (recoveringDiagnostics == null) {
      advance();
      return;
    }
    if (advanceRecoveringLexerErrors(recoveringDiagnostics)) {
      return;
    }
    while (current.kind() != TokenKind.EOF) {
      if (advanceRecoveringLexerErrors(recoveringDiagnostics)
          && current.kind() == TokenKind.SEMICOLON) {
        advanceRecoveringLexerErrors(recoveringDiagnostics);
        return;
      }
    }
  }

  private void advance() {
    previousEndOffset = current.endOffset();
    current = lexer.next();
  }

  private ParseException error(String message) {
    return new ParseException(current.line(), current.column(), message);
  }

  private static ParseException error(Token token, String message) {
    return new ParseException(token.line(), token.column(), message);
  }

  private void reportRecoverable(Token token, String message) {
    if (recoveringDiagnostics == null) {
      throw error(token, message);
    }
    recoveringDiagnostics.add(new ParseDiagnostic(token.line(), token.column(), message));
  }

  private void reportRecoverable(Optional<Ast.SourceSpan> span, String message) {
    int diagnosticLine = span.map(Ast.SourceSpan::line).orElse(current.line());
    int diagnosticColumn = span.map(Ast.SourceSpan::column).orElse(current.column());
    if (recoveringDiagnostics == null) {
      throw new ParseException(diagnosticLine, diagnosticColumn, message);
    }
    recoveringDiagnostics.add(
        new ParseDiagnostic(diagnosticLine, diagnosticColumn, message));
  }

  private static ParseException error(Token token, String message, RuntimeException cause) {
    return new ParseException(token.line(), token.column(), message, cause);
  }

  /** Source-located syntax failure. */
  public static final class ParseException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final int line;
    private final int column;
    private final String detail;

    ParseException(int line, int column, String message) {
      super("line " + line + ", column " + column + ": " + message);
      this.line = line;
      this.column = column;
      this.detail = message;
    }

    ParseException(int line, int column, String message, RuntimeException cause) {
      super("line " + line + ", column " + column + ": " + message, cause);
      this.line = line;
      this.column = column;
      this.detail = message;
    }

    public int line() {
      return line;
    }

    public int column() {
      return column;
    }

    public String detail() {
      return detail;
    }
  }
}
