package moo.syntax;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable syntax tree for the MOO source accepted by this server slice. */
public sealed interface Ast
    permits Ast.Program,
        Ast.Statement,
        Ast.Expression,
        Ast.AssignmentTarget,
        Ast.ErrorSelector,
        Ast.ElseIf,
        Ast.ExceptClause,
        Ast.FinallyClause,
        Ast.MapEntry,
        Ast.SourceSpan {

  /** Half-open source offsets plus the one-based start position. */
  record SourceSpan(int startOffset, int endOffset, int line, int column) implements Ast {
    public SourceSpan {
      if (startOffset < 0 || endOffset < startOffset || line < 1 || column < 1) {
        throw new IllegalArgumentException("invalid source span");
      }
    }
  }

  /** A complete verb body. */
  record Program(List<Statement> statements) implements Ast {
    public Program {
      statements = List.copyOf(statements);
    }
  }

  /** The closed statement family. */
  sealed interface Statement extends Ast
      permits If, While, For, Fork, Try, Return, ExpressionStatement {}

  record If(
      Expression condition,
      List<Statement> body,
      List<ElseIf> elseIfs,
      List<Statement> elseBody,
      Optional<SourceSpan> span)
      implements Statement {
    public If {
      body = List.copyOf(body);
      elseIfs = List.copyOf(elseIfs);
      elseBody = List.copyOf(elseBody);
    }

    public If(
        Expression condition,
        List<Statement> body,
        List<ElseIf> elseIfs,
        List<Statement> elseBody) {
      this(condition, body, elseIfs, elseBody, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other
          || (other instanceof If that
              && condition.equals(that.condition)
              && body.equals(that.body)
              && elseIfs.equals(that.elseIfs)
              && elseBody.equals(that.elseBody));
    }

    @Override
    public int hashCode() {
      return Objects.hash(condition, body, elseIfs, elseBody);
    }
  }

  record ElseIf(Expression condition, List<Statement> body) implements Ast {
    public ElseIf {
      body = List.copyOf(body);
    }
  }

  record While(Expression condition, List<Statement> body) implements Statement {
    public While {
      body = List.copyOf(body);
    }
  }

  record For(String variable, Expression iterable, List<Statement> body) implements Statement {
    public For {
      body = List.copyOf(body);
    }
  }

  record Fork(Expression delay, List<Statement> body) implements Statement {
    public Fork {
      body = List.copyOf(body);
    }
  }

  record Try(
      List<Statement> body, List<ExceptClause> exceptClauses, Optional<FinallyClause> finallyClause)
      implements Statement {
    public Try {
      body = List.copyOf(body);
      exceptClauses = List.copyOf(exceptClauses);
    }
  }

  record ExceptClause(Optional<String> variable, ErrorSelector errors, List<Statement> body)
      implements Ast {
    public ExceptClause {
      body = List.copyOf(body);
    }
  }

  record FinallyClause(List<Statement> body) implements Ast {
    public FinallyClause {
      body = List.copyOf(body);
    }
  }

  record Return(Optional<Expression> value, Optional<SourceSpan> span) implements Statement {
    public Return(Optional<Expression> value) {
      this(value, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other || (other instanceof Return that && value.equals(that.value));
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }

  record ExpressionStatement(Expression expression, Optional<SourceSpan> span)
      implements Statement {
    public ExpressionStatement(Expression expression) {
      this(expression, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other
          || (other instanceof ExpressionStatement that
              && expression.equals(that.expression));
    }

    @Override
    public int hashCode() {
      return Objects.hash(expression);
    }
  }

  /** The closed expression family. */
  sealed interface Expression extends Ast
      permits Identifier,
          IntegerLiteral,
          FloatLiteral,
          StringLiteral,
          ObjectLiteral,
          ErrorLiteral,
          ListLiteral,
          MapLiteral,
          Splice,
          Call,
          VerbCall,
          Assignment,
          PropertyAccess,
          IndexAccess,
          Unary,
          Binary,
          Ternary,
          Catch {}

  record Identifier(String name, Optional<SourceSpan> span) implements Expression {
    public Identifier(String name) {
      this(name, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other || (other instanceof Identifier that && name.equals(that.name));
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }
  }

  record IntegerLiteral(long value, Optional<SourceSpan> span) implements Expression {
    public IntegerLiteral(long value) {
      this(value, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other || (other instanceof IntegerLiteral that && value == that.value);
    }

    @Override
    public int hashCode() {
      return Long.hashCode(value);
    }
  }

  record FloatLiteral(double value, Optional<SourceSpan> span) implements Expression {
    public FloatLiteral(double value) {
      this(value, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other
          || (other instanceof FloatLiteral that
              && Double.doubleToLongBits(value) == Double.doubleToLongBits(that.value));
    }

    @Override
    public int hashCode() {
      return Double.hashCode(value);
    }
  }

  record StringLiteral(String value, Optional<SourceSpan> span) implements Expression {
    public StringLiteral(String value) {
      this(value, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other || (other instanceof StringLiteral that && value.equals(that.value));
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }
  }

  record ObjectLiteral(long value, Optional<SourceSpan> span) implements Expression {
    public ObjectLiteral(long value) {
      this(value, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other || (other instanceof ObjectLiteral that && value == that.value);
    }

    @Override
    public int hashCode() {
      return Long.hashCode(value);
    }
  }

  record ErrorLiteral(String name, Optional<SourceSpan> span) implements Expression {
    public ErrorLiteral(String name) {
      this(name, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other || (other instanceof ErrorLiteral that && name.equals(that.name));
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }
  }

  record ListLiteral(List<Expression> elements, Optional<SourceSpan> span) implements Expression {
    public ListLiteral {
      elements = List.copyOf(elements);
    }

    public ListLiteral(List<Expression> elements) {
      this(elements, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other || (other instanceof ListLiteral that && elements.equals(that.elements));
    }

    @Override
    public int hashCode() {
      return Objects.hash(elements);
    }
  }

  record MapLiteral(List<MapEntry> entries, Optional<SourceSpan> span) implements Expression {
    public MapLiteral {
      entries = List.copyOf(entries);
    }

    public MapLiteral(List<MapEntry> entries) {
      this(entries, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other || (other instanceof MapLiteral that && entries.equals(that.entries));
    }

    @Override
    public int hashCode() {
      return Objects.hash(entries);
    }
  }

  record MapEntry(Expression key, Expression value) implements Ast {}

  record Splice(Expression value) implements Expression {}

  record Call(String name, List<Expression> arguments) implements Expression {
    public Call {
      arguments = List.copyOf(arguments);
    }
  }

  record VerbCall(Expression object, Expression name, List<Expression> arguments)
      implements Expression {
    public VerbCall {
      arguments = List.copyOf(arguments);
    }
  }

  record Assignment(AssignmentTarget target, Expression value, Optional<SourceSpan> span)
      implements Expression {
    public Assignment(AssignmentTarget target, Expression value) {
      this(target, value, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other
          || (other instanceof Assignment that
              && target.equals(that.target)
              && value.equals(that.value));
    }

    @Override
    public int hashCode() {
      return Objects.hash(target, value);
    }
  }

  record PropertyAccess(Expression object, Expression property) implements Expression {}

  record IndexAccess(Expression collection, Expression index) implements Expression {}

  record Unary(UnaryOperator operator, Expression operand, Optional<SourceSpan> span)
      implements Expression {
    public Unary(UnaryOperator operator, Expression operand) {
      this(operator, operand, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other
          || (other instanceof Unary that
              && operator == that.operator
              && operand.equals(that.operand));
    }

    @Override
    public int hashCode() {
      return Objects.hash(operator, operand);
    }
  }

  record Binary(
      Expression left, BinaryOperator operator, Expression right, Optional<SourceSpan> span)
      implements Expression {
    public Binary(Expression left, BinaryOperator operator, Expression right) {
      this(left, operator, right, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other
          || (other instanceof Binary that
              && left.equals(that.left)
              && operator == that.operator
              && right.equals(that.right));
    }

    @Override
    public int hashCode() {
      return Objects.hash(left, operator, right);
    }
  }

  record Ternary(
      Expression condition,
      Expression trueExpression,
      Expression falseExpression,
      Optional<SourceSpan> span)
      implements Expression {
    public Ternary(
        Expression condition, Expression trueExpression, Expression falseExpression) {
      this(condition, trueExpression, falseExpression, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
      return this == other
          || (other instanceof Ternary that
              && condition.equals(that.condition)
              && trueExpression.equals(that.trueExpression)
              && falseExpression.equals(that.falseExpression));
    }

    @Override
    public int hashCode() {
      return Objects.hash(condition, trueExpression, falseExpression);
    }
  }

  record Catch(Expression guarded, ErrorSelector errors, Optional<Expression> fallback)
      implements Expression {}

  /** The closed assignment-target family. */
  sealed interface AssignmentTarget extends Ast
      permits VariableTarget, PropertyTarget, IndexTarget, ScatterTarget {}

  record VariableTarget(String name) implements AssignmentTarget {}

  record PropertyTarget(Expression object, Expression property) implements AssignmentTarget {}

  record IndexTarget(Expression collection, Expression index) implements AssignmentTarget {}

  record ScatterTarget(List<String> variables) implements AssignmentTarget {
    public ScatterTarget {
      variables = List.copyOf(variables);
    }
  }

  /** An exception selector used by both catch expressions and except clauses. */
  sealed interface ErrorSelector extends Ast permits AnyErrors, ErrorList {}

  record AnyErrors() implements ErrorSelector {}

  record ErrorList(List<String> names) implements ErrorSelector {
    public ErrorList {
      names = List.copyOf(names);
    }
  }

  enum UnaryOperator {
    NEGATE,
    NOT
  }

  enum BinaryOperator {
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    REMAINDER,
    POWER,
    EQUAL,
    NOT_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    IN,
    AND,
    OR
  }
}
