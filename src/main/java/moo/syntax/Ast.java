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
      Expression condition, List<Statement> body, List<ElseIf> elseIfs, List<Statement> elseBody)
      implements Statement {
    public If {
      body = List.copyOf(body);
      elseIfs = List.copyOf(elseIfs);
      elseBody = List.copyOf(elseBody);
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

  record ExpressionStatement(Expression expression) implements Statement {}

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
          Catch {}

  record Identifier(String name) implements Expression {}

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

  record StringLiteral(String value) implements Expression {}

  record ObjectLiteral(long value) implements Expression {}

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

  record ListLiteral(List<Expression> elements) implements Expression {
    public ListLiteral {
      elements = List.copyOf(elements);
    }
  }

  record MapLiteral(List<MapEntry> entries) implements Expression {
    public MapLiteral {
      entries = List.copyOf(entries);
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

  record Assignment(AssignmentTarget target, Expression value) implements Expression {}

  record PropertyAccess(Expression object, Expression property) implements Expression {}

  record IndexAccess(Expression collection, Expression index) implements Expression {}

  record Unary(UnaryOperator operator, Expression operand) implements Expression {}

  record Binary(Expression left, BinaryOperator operator, Expression right) implements Expression {}

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
