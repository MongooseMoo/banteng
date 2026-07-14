package moo.bytecode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import moo.bytecode.BytecodeProgram.HandlerSpec;
import moo.bytecode.BytecodeProgram.Instruction;
import moo.bytecode.BytecodeProgram.Opcode;
import moo.syntax.Ast;

/** Lowers the authorized syntax slice directly into executable bytecode. */
public final class MooCompiler {
  private int catchSequence;

  /** Compiles one parsed MOO verb body. */
  public BytecodeProgram compile(Ast.Program program) {
    List<Instruction> instructions = new ArrayList<>();
    catchSequence = 0;
    for (Ast.Statement statement : program.statements()) {
      compileStatement(statement, instructions);
    }
    if (program.statements().isEmpty() || !(program.statements().getLast() instanceof Ast.Return)) {
      instructions.add(new Instruction(Opcode.PUSH_INTEGER, 0));
      instructions.add(new Instruction(Opcode.RETURN));
    }
    return new BytecodeProgram(instructions);
  }

  private void compileStatement(Ast.Statement statement, List<Instruction> instructions) {
    if (statement instanceof Ast.Return returnStatement) {
      if (returnStatement.value().isPresent()) {
        compileExpression(returnStatement.value().orElseThrow(), instructions);
      } else {
        instructions.add(new Instruction(Opcode.PUSH_INTEGER, 0));
      }
      instructions.add(new Instruction(Opcode.RETURN));
      return;
    }
    if (statement instanceof Ast.ExpressionStatement expressionStatement) {
      compileExpression(expressionStatement.expression(), instructions);
      instructions.add(new Instruction(Opcode.POP));
      return;
    }
    if (statement instanceof Ast.If ifStatement) {
      compileIf(ifStatement, instructions);
      return;
    }
    if (statement instanceof Ast.While whileStatement) {
      int conditionTarget = instructions.size();
      compileExpression(whileStatement.condition(), instructions);
      int exitJump = addJump(Opcode.JUMP_IF_FALSE, instructions);
      compileStatements(whileStatement.body(), instructions);
      instructions.add(new Instruction(Opcode.JUMP, conditionTarget));
      patchJump(exitJump, instructions.size(), instructions);
      return;
    }
    if (statement instanceof Ast.For forStatement) {
      compileExpression(forStatement.iterable(), instructions);
      int iterate = instructions.size();
      instructions.add(new Instruction(Opcode.ITERATE, -1, forStatement.variable()));
      compileStatements(forStatement.body(), instructions);
      instructions.add(new Instruction(Opcode.JUMP, iterate));
      patchNumericOperand(iterate, instructions.size(), instructions);
      return;
    }
    if (statement instanceof Ast.Try tryStatement) {
      compileTry(tryStatement, instructions);
      return;
    }
    throw new IllegalArgumentException("unsupported statement in bytecode slice: " + statement);
  }

  private void compileIf(Ast.If statement, List<Instruction> instructions) {
    List<Integer> endJumps = new ArrayList<>();
    compileExpression(statement.condition(), instructions);
    int nextBranch = addJump(Opcode.JUMP_IF_FALSE, instructions);
    compileStatements(statement.body(), instructions);
    endJumps.add(addJump(Opcode.JUMP, instructions));
    patchJump(nextBranch, instructions.size(), instructions);

    for (Ast.ElseIf elseIf : statement.elseIfs()) {
      compileExpression(elseIf.condition(), instructions);
      nextBranch = addJump(Opcode.JUMP_IF_FALSE, instructions);
      compileStatements(elseIf.body(), instructions);
      endJumps.add(addJump(Opcode.JUMP, instructions));
      patchJump(nextBranch, instructions.size(), instructions);
    }
    compileStatements(statement.elseBody(), instructions);
    int endTarget = instructions.size();
    for (int jump : endJumps) {
      patchJump(jump, endTarget, instructions);
    }
  }

  private void compileTry(Ast.Try statement, List<Instruction> instructions) {
    if (statement.exceptClauses().size() > 1) {
      throw new IllegalArgumentException("multiple except clauses are outside this bytecode slice");
    }
    int enter = instructions.size();
    instructions.add(new Instruction(Opcode.JUMP, -1));
    compileStatements(statement.body(), instructions);
    instructions.add(new Instruction(Opcode.LEAVE_HANDLER));

    int catchTarget = -1;
    Optional<String> catchVariable = Optional.empty();
    boolean catchesAny = false;
    List<String> caughtErrors = List.of();
    if (!statement.exceptClauses().isEmpty()) {
      Ast.ExceptClause clause = statement.exceptClauses().getFirst();
      catchTarget = instructions.size();
      catchVariable = clause.variable();
      catchesAny = clause.errors() instanceof Ast.AnyErrors;
      if (clause.errors() instanceof Ast.ErrorList errors) {
        caughtErrors = errors.names();
      }
      compileStatements(clause.body(), instructions);
      instructions.add(new Instruction(Opcode.LEAVE_HANDLER));
    }

    int finallyTarget = -1;
    if (statement.finallyClause().isPresent()) {
      finallyTarget = instructions.size();
      compileStatements(statement.finallyClause().orElseThrow().body(), instructions);
      instructions.add(new Instruction(Opcode.END_FINALLY));
    }
    int endTarget = instructions.size();
    instructions.set(
        enter,
        new Instruction(
            new HandlerSpec(
                catchTarget, catchVariable, catchesAny, caughtErrors, finallyTarget, endTarget)));
  }

  private void compileStatements(List<Ast.Statement> statements, List<Instruction> instructions) {
    for (Ast.Statement statement : statements) {
      compileStatement(statement, instructions);
    }
  }

  private void compileExpression(Ast.Expression expression, List<Instruction> instructions) {
    if (expression instanceof Ast.IntegerLiteral integer) {
      instructions.add(new Instruction(Opcode.PUSH_INTEGER, integer.value()));
      return;
    }
    if (expression instanceof Ast.StringLiteral string) {
      instructions.add(new Instruction(Opcode.PUSH_STRING, string.value()));
      return;
    }
    if (expression instanceof Ast.ObjectLiteral object) {
      instructions.add(new Instruction(Opcode.PUSH_OBJECT, object.value()));
      return;
    }
    if (expression instanceof Ast.ErrorLiteral error) {
      instructions.add(new Instruction(Opcode.PUSH_ERROR, error.name()));
      return;
    }
    if (expression instanceof Ast.Identifier identifier) {
      instructions.add(new Instruction(Opcode.LOAD_LOCAL, identifier.name()));
      return;
    }
    if (expression instanceof Ast.ListLiteral list) {
      for (Ast.Expression element : list.elements()) {
        compileExpression(element, instructions);
      }
      instructions.add(new Instruction(Opcode.BUILD_LIST, list.elements().size()));
      return;
    }
    if (expression instanceof Ast.Call call) {
      for (Ast.Expression argument : call.arguments()) {
        compileExpression(argument, instructions);
      }
      instructions.add(new Instruction(Opcode.CALL, call.arguments().size(), call.name()));
      return;
    }
    if (expression instanceof Ast.PropertyAccess property) {
      compileExpression(property.object(), instructions);
      instructions.add(new Instruction(Opcode.GET_PROPERTY, property.property()));
      return;
    }
    if (expression instanceof Ast.IndexAccess index) {
      compileExpression(index.collection(), instructions);
      compileExpression(index.index(), instructions);
      instructions.add(new Instruction(Opcode.INDEX));
      return;
    }
    if (expression instanceof Ast.Assignment assignment) {
      compileAssignment(assignment, instructions);
      return;
    }
    if (expression instanceof Ast.Unary unary) {
      compileExpression(unary.operand(), instructions);
      instructions.add(
          new Instruction(
              unary.operator() == Ast.UnaryOperator.NEGATE ? Opcode.NEGATE : Opcode.NOT));
      return;
    }
    if (expression instanceof Ast.Binary binary) {
      compileBinary(binary, instructions);
      return;
    }
    if (expression instanceof Ast.Catch catchExpression) {
      compileCatch(catchExpression, instructions);
      return;
    }
    throw new IllegalArgumentException("unsupported expression in bytecode slice: " + expression);
  }

  private void compileAssignment(Ast.Assignment assignment, List<Instruction> instructions) {
    if (assignment.target() instanceof Ast.VariableTarget variable) {
      compileExpression(assignment.value(), instructions);
      instructions.add(new Instruction(Opcode.DUP));
      instructions.add(new Instruction(Opcode.STORE_LOCAL, variable.name()));
      return;
    }
    if (assignment.target() instanceof Ast.PropertyTarget property) {
      compileExpression(property.object(), instructions);
      compileExpression(assignment.value(), instructions);
      instructions.add(new Instruction(Opcode.SET_PROPERTY, property.property()));
      return;
    }
    if (assignment.target() instanceof Ast.ScatterTarget scatter) {
      compileExpression(assignment.value(), instructions);
      instructions.add(
          new Instruction(
              Opcode.SCATTER, scatter.variables().size(), String.join(",", scatter.variables())));
      return;
    }
    throw new IllegalArgumentException("unsupported assignment target: " + assignment.target());
  }

  private void compileBinary(Ast.Binary binary, List<Instruction> instructions) {
    compileExpression(binary.left(), instructions);
    if (binary.operator() == Ast.BinaryOperator.AND || binary.operator() == Ast.BinaryOperator.OR) {
      instructions.add(new Instruction(Opcode.DUP));
      Opcode jumpOpcode =
          binary.operator() == Ast.BinaryOperator.AND ? Opcode.JUMP_IF_FALSE : Opcode.JUMP_IF_TRUE;
      int endJump = addJump(jumpOpcode, instructions);
      instructions.add(new Instruction(Opcode.POP));
      compileExpression(binary.right(), instructions);
      patchJump(endJump, instructions.size(), instructions);
      return;
    }
    compileExpression(binary.right(), instructions);
    instructions.add(new Instruction(binaryOpcode(binary.operator())));
  }

  private void compileCatch(Ast.Catch expression, List<Instruction> instructions) {
    String caughtLocal = "$caught" + catchSequence++;
    int enter = instructions.size();
    instructions.add(new Instruction(Opcode.JUMP, -1));
    compileExpression(expression.guarded(), instructions);
    instructions.add(new Instruction(Opcode.LEAVE_HANDLER));
    int catchTarget = instructions.size();
    if (expression.fallback().isPresent()) {
      compileExpression(expression.fallback().orElseThrow(), instructions);
    } else {
      instructions.add(new Instruction(Opcode.LOAD_LOCAL, caughtLocal));
    }
    instructions.add(new Instruction(Opcode.LEAVE_HANDLER));
    int endTarget = instructions.size();
    boolean catchesAny = expression.errors() instanceof Ast.AnyErrors;
    List<String> caughtErrors =
        expression.errors() instanceof Ast.ErrorList errors ? errors.names() : List.of();
    instructions.set(
        enter,
        new Instruction(
            new HandlerSpec(
                catchTarget, Optional.of(caughtLocal), catchesAny, caughtErrors, -1, endTarget)));
  }

  private static Opcode binaryOpcode(Ast.BinaryOperator operator) {
    return switch (operator) {
      case ADD -> Opcode.ADD;
      case SUBTRACT -> Opcode.SUBTRACT;
      case MULTIPLY -> Opcode.MULTIPLY;
      case DIVIDE -> Opcode.DIVIDE;
      case REMAINDER -> Opcode.REMAINDER;
      case POWER -> Opcode.POWER;
      case EQUAL -> Opcode.EQUAL;
      case NOT_EQUAL -> Opcode.NOT_EQUAL;
      case LESS_THAN -> Opcode.LESS_THAN;
      case LESS_THAN_OR_EQUAL -> Opcode.LESS_THAN_OR_EQUAL;
      case GREATER_THAN -> Opcode.GREATER_THAN;
      case GREATER_THAN_OR_EQUAL -> Opcode.GREATER_THAN_OR_EQUAL;
      case AND, OR -> throw new AssertionError("short-circuit operator reached direct lowering");
    };
  }

  private static int addJump(Opcode opcode, List<Instruction> instructions) {
    int index = instructions.size();
    instructions.add(new Instruction(opcode, -1));
    return index;
  }

  private static void patchJump(int instructionIndex, int target, List<Instruction> instructions) {
    patchNumericOperand(instructionIndex, target, instructions);
  }

  private static void patchNumericOperand(
      int instructionIndex, int target, List<Instruction> instructions) {
    Instruction previous = instructions.get(instructionIndex);
    Instruction replacement =
        previous.text().isPresent()
            ? new Instruction(previous.opcode(), target, previous.text().orElseThrow())
            : new Instruction(previous.opcode(), target);
    instructions.set(instructionIndex, replacement);
  }
}
