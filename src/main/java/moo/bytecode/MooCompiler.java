package moo.bytecode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import moo.bytecode.BytecodeProgram.HandlerSpec;
import moo.bytecode.BytecodeProgram.Instruction;
import moo.bytecode.BytecodeProgram.Opcode;
import moo.syntax.Ast;
import moo.syntax.MooParser;
import moo.syntax.MooUnparser;

/** Lowers the authorized syntax slice directly into executable bytecode. */
public final class MooCompiler {
  private int catchSequence;
  private final List<List<String>> activeLoopVariables = new ArrayList<>();
  private final List<Integer> activeLoopStarts = new ArrayList<>();
  private final List<List<Integer>> activeLoopBreaks = new ArrayList<>();

  /** Parses and compiles one MOO verb body. */
  public BytecodeProgram compile(String source) {
    return compile(MooParser.parse(source));
  }

  /** Compiles one parsed MOO verb body. */
  public BytecodeProgram compile(Ast.Program program) {
    List<Instruction> instructions = new ArrayList<>();
    List<BytecodeProgram> forkVectors = new ArrayList<>();
    catchSequence = 0;
    activeLoopVariables.clear();
    activeLoopStarts.clear();
    activeLoopBreaks.clear();
    for (Ast.Statement statement : program.statements()) {
      compileStatement(statement, instructions, forkVectors);
    }
    if (program.statements().isEmpty() || !(program.statements().getLast() instanceof Ast.Return)) {
      instructions.add(new Instruction(Opcode.PUSH_INTEGER, 0));
      instructions.add(new Instruction(Opcode.RETURN));
    }
    return new BytecodeProgram(instructions, forkVectors, MooUnparser.unparse(program));
  }

  private void compileStatement(
      Ast.Statement statement, List<Instruction> instructions, List<BytecodeProgram> forkVectors) {
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
      compileIf(ifStatement, instructions, forkVectors);
      return;
    }
    if (statement instanceof Ast.While whileStatement) {
      int conditionTarget = instructions.size();
      compileExpression(whileStatement.condition(), instructions);
      int exitJump = addJump(Opcode.JUMP_IF_FALSE, instructions);
      List<Integer> breakJumps = new ArrayList<>();
      activeLoopVariables.add(
          whileStatement.loopVariable().map(List::of).orElseGet(List::of));
      activeLoopStarts.add(conditionTarget);
      activeLoopBreaks.add(breakJumps);
      compileStatements(whileStatement.body(), instructions, forkVectors);
      activeLoopVariables.removeLast();
      activeLoopStarts.removeLast();
      activeLoopBreaks.removeLast();
      instructions.add(new Instruction(Opcode.JUMP, conditionTarget));
      int loopExit = instructions.size();
      patchJump(exitJump, loopExit, instructions);
      for (int breakJump : breakJumps) {
        patchJump(breakJump, loopExit, instructions);
      }
      return;
    }
    if (statement instanceof Ast.For forStatement) {
      compileExpression(forStatement.iterable(), instructions);
      forStatement.rangeEnd().ifPresent(end -> compileExpression(end, instructions));
      int iterate = instructions.size();
      String variables =
          forStatement
              .indexVariable()
              .map(index -> forStatement.variable() + "," + index)
              .orElse(forStatement.variable());
      instructions.add(
          new Instruction(
              forStatement.rangeEnd().isPresent() ? Opcode.ITERATE_RANGE : Opcode.ITERATE,
              -1,
              variables));
      List<Integer> breakJumps = new ArrayList<>();
      activeLoopVariables.add(
          forStatement
              .indexVariable()
              .map(index -> List.of(forStatement.variable(), index))
              .orElseGet(() -> List.of(forStatement.variable())));
      activeLoopStarts.add(iterate);
      activeLoopBreaks.add(breakJumps);
      compileStatements(forStatement.body(), instructions, forkVectors);
      activeLoopVariables.removeLast();
      activeLoopStarts.removeLast();
      activeLoopBreaks.removeLast();
      instructions.add(new Instruction(Opcode.JUMP, iterate));
      int loopExit = instructions.size();
      instructions.add(new Instruction(Opcode.LEAVE_LOOP, iterate));
      patchNumericOperand(iterate, loopExit, instructions);
      for (int breakJump : breakJumps) {
        patchJump(breakJump, loopExit, instructions);
      }
      return;
    }
    if (statement instanceof Ast.Break breakStatement) {
      int loopIndex = activeLoopVariables.size() - 1;
      if (breakStatement.loopVariable().isPresent()) {
        String loopVariable = breakStatement.loopVariable().orElseThrow();
        loopIndex = -1;
        for (int index = activeLoopVariables.size() - 1; index >= 0; index--) {
          if (activeLoopVariables.get(index).contains(loopVariable)) {
            loopIndex = index;
            break;
          }
        }
      }
      if (loopIndex < 0) {
        throw new IllegalArgumentException(
            "unknown loop variable: " + breakStatement.loopVariable().orElse("<unnamed>"));
      }
      int breakJump = addJump(Opcode.JUMP, instructions);
      activeLoopBreaks.get(loopIndex).add(breakJump);
      return;
    }
    if (statement instanceof Ast.Continue continueStatement) {
      int loopIndex = activeLoopVariables.size() - 1;
      if (continueStatement.loopVariable().isPresent()) {
        String loopVariable = continueStatement.loopVariable().orElseThrow();
        loopIndex = -1;
        for (int index = activeLoopVariables.size() - 1; index >= 0; index--) {
          if (activeLoopVariables.get(index).contains(loopVariable)) {
            loopIndex = index;
            break;
          }
        }
      }
      if (loopIndex < 0) {
        throw new IllegalArgumentException(
            "Invalid loop name: " + continueStatement.loopVariable().orElse("<unnamed>"));
      }
      instructions.add(new Instruction(Opcode.JUMP, activeLoopStarts.get(loopIndex)));
      return;
    }
    if (statement instanceof Ast.Fork forkStatement) {
      int vectorIndex = forkVectors.size();
      compileExpression(forkStatement.delay(), instructions);
      List<Instruction> childInstructions = new ArrayList<>();
      List<BytecodeProgram> childForkVectors = new ArrayList<>();
      compileStatements(forkStatement.body(), childInstructions, childForkVectors);
      if (forkStatement.body().isEmpty()
          || !(forkStatement.body().getLast() instanceof Ast.Return)) {
        childInstructions.add(new Instruction(Opcode.PUSH_INTEGER, 0));
        childInstructions.add(new Instruction(Opcode.RETURN));
      }
      forkVectors.add(
          new BytecodeProgram(
              childInstructions,
              childForkVectors,
              MooUnparser.unparse(new Ast.Program(forkStatement.body()))));
      instructions.add(new Instruction(Opcode.FORK, vectorIndex));
      instructions.add(
          forkStatement
              .taskIdVariable()
              .<Instruction>map(variable -> new Instruction(Opcode.STORE_LOCAL, variable))
              .orElseGet(() -> new Instruction(Opcode.POP)));
      return;
    }
    if (statement instanceof Ast.Try tryStatement) {
      compileTry(tryStatement, instructions, forkVectors);
      return;
    }
    throw new IllegalArgumentException("unsupported statement in bytecode slice: " + statement);
  }

  private void compileIf(
      Ast.If statement, List<Instruction> instructions, List<BytecodeProgram> forkVectors) {
    List<Integer> endJumps = new ArrayList<>();
    compileExpression(statement.condition(), instructions);
    int nextBranch = addJump(Opcode.JUMP_IF_FALSE, instructions);
    compileStatements(statement.body(), instructions, forkVectors);
    endJumps.add(addJump(Opcode.JUMP, instructions));
    patchJump(nextBranch, instructions.size(), instructions);

    for (Ast.ElseIf elseIf : statement.elseIfs()) {
      compileExpression(elseIf.condition(), instructions);
      nextBranch = addJump(Opcode.JUMP_IF_FALSE, instructions);
      compileStatements(elseIf.body(), instructions, forkVectors);
      endJumps.add(addJump(Opcode.JUMP, instructions));
      patchJump(nextBranch, instructions.size(), instructions);
    }
    compileStatements(statement.elseBody(), instructions, forkVectors);
    int endTarget = instructions.size();
    for (int jump : endJumps) {
      patchJump(jump, endTarget, instructions);
    }
  }

  private void compileTry(
      Ast.Try statement, List<Instruction> instructions, List<BytecodeProgram> forkVectors) {
    int ownerEnter = instructions.size();
    instructions.add(new Instruction(Opcode.JUMP, -1));

    int clauseCount = statement.exceptClauses().size();
    int[] clauseEnters = new int[clauseCount];
    for (int index = clauseCount - 1; index >= 0; index--) {
      clauseEnters[index] = instructions.size();
      instructions.add(new Instruction(Opcode.JUMP, -1));
    }

    compileStatements(statement.body(), instructions, forkVectors);

    int[] normalCleanupTargets = new int[clauseCount];
    for (int index = 0; index < clauseCount; index++) {
      normalCleanupTargets[index] = instructions.size();
      instructions.add(new Instruction(Opcode.LEAVE_HANDLER));
    }
    int ownerCleanupTarget = instructions.size();
    instructions.add(new Instruction(Opcode.LEAVE_HANDLER));

    int[] catchTargets = new int[clauseCount];
    for (int index = 0; index < clauseCount; index++) {
      Ast.ExceptClause clause = statement.exceptClauses().get(index);
      catchTargets[index] = instructions.size();
      compileStatements(clause.body(), instructions, forkVectors);
      instructions.add(new Instruction(Opcode.LEAVE_HANDLER));
    }

    int finallyTarget = -1;
    if (statement.finallyClause().isPresent()) {
      finallyTarget = instructions.size();
      compileStatements(statement.finallyClause().orElseThrow().body(), instructions, forkVectors);
      instructions.add(new Instruction(Opcode.END_FINALLY));
    }
    int endTarget = instructions.size();
    instructions.set(
        ownerEnter,
        new Instruction(
            new HandlerSpec(
                -1, Optional.empty(), false, List.of(), false, finallyTarget, endTarget)));

    for (int index = 0; index < clauseCount; index++) {
      Ast.ExceptClause clause = statement.exceptClauses().get(index);
      boolean catchesAny = clause.errors() instanceof Ast.AnyErrors;
      List<String> caughtErrors =
          clause.errors() instanceof Ast.ErrorList errors ? errors.names() : List.of();
      int nextCleanupTarget =
          index + 1 < clauseCount ? normalCleanupTargets[index + 1] : ownerCleanupTarget;
      instructions.set(
          clauseEnters[index],
          new Instruction(
              new HandlerSpec(
                  catchTargets[index],
                  clause.variable(),
                  catchesAny,
                  caughtErrors,
                  true,
                  -1,
                  nextCleanupTarget)));
    }
  }

  private void compileStatements(
      List<Ast.Statement> statements,
      List<Instruction> instructions,
      List<BytecodeProgram> forkVectors) {
    for (Ast.Statement statement : statements) {
      compileStatement(statement, instructions, forkVectors);
    }
  }

  private void compileExpression(Ast.Expression expression, List<Instruction> instructions) {
    if (expression instanceof Ast.IntegerLiteral integer) {
      instructions.add(new Instruction(Opcode.PUSH_INTEGER, integer.value()));
      return;
    }
    if (expression instanceof Ast.FloatLiteral floating) {
      instructions.add(
          new Instruction(Opcode.PUSH_FLOAT, Double.doubleToRawLongBits(floating.value())));
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
      instructions.add(new Instruction(Opcode.BUILD_LIST, 0));
      for (Ast.Expression element : list.elements()) {
        if (element instanceof Ast.Splice splice) {
          compileExpression(splice.value(), instructions);
          instructions.add(new Instruction(Opcode.LIST_EXTEND));
        } else {
          compileExpression(element, instructions);
          instructions.add(new Instruction(Opcode.LIST_APPEND));
        }
      }
      return;
    }
    if (expression instanceof Ast.MapLiteral map) {
      for (Ast.MapEntry entry : map.entries()) {
        compileExpression(entry.value(), instructions);
        compileExpression(entry.key(), instructions);
      }
      instructions.add(new Instruction(Opcode.BUILD_MAP, map.entries().size()));
      return;
    }
    if (expression instanceof Ast.Call call) {
      instructions.add(new Instruction(Opcode.BUILD_LIST, 0));
      for (Ast.Expression argument : call.arguments()) {
        if (argument instanceof Ast.Splice splice) {
          compileExpression(splice.value(), instructions);
          instructions.add(new Instruction(Opcode.LIST_EXTEND));
        } else {
          compileExpression(argument, instructions);
          instructions.add(new Instruction(Opcode.LIST_APPEND));
        }
      }
      instructions.add(new Instruction(Opcode.CALL, call.name()));
      return;
    }
    if (expression instanceof Ast.VerbCall call) {
      compileExpression(call.object(), instructions);
      compileExpression(call.name(), instructions);
      instructions.add(new Instruction(Opcode.BUILD_LIST, 0));
      for (Ast.Expression argument : call.arguments()) {
        if (argument instanceof Ast.Splice splice) {
          compileExpression(splice.value(), instructions);
          instructions.add(new Instruction(Opcode.LIST_EXTEND));
        } else {
          compileExpression(argument, instructions);
          instructions.add(new Instruction(Opcode.LIST_APPEND));
        }
      }
      instructions.add(new Instruction(Opcode.CALL_VERB));
      return;
    }
    if (expression instanceof Ast.PropertyAccess property) {
      compileExpression(property.object(), instructions);
      compileExpression(property.property(), instructions);
      instructions.add(new Instruction(Opcode.GET_PROPERTY));
      return;
    }
    if (expression instanceof Ast.IndexAccess index) {
      compileExpression(index.collection(), instructions);
      instructions.add(new Instruction(Opcode.ENTER_INDEX));
      compileExpression(index.index(), instructions);
      instructions.add(new Instruction(Opcode.INDEX));
      return;
    }
    if (expression instanceof Ast.RangeAccess range) {
      compileExpression(range.collection(), instructions);
      instructions.add(new Instruction(Opcode.ENTER_INDEX));
      compileExpression(range.start(), instructions);
      compileExpression(range.end(), instructions);
      instructions.add(new Instruction(Opcode.RANGE));
      return;
    }
    if (expression instanceof Ast.FirstIndex) {
      instructions.add(new Instruction(Opcode.FIRST));
      return;
    }
    if (expression instanceof Ast.LastIndex) {
      instructions.add(new Instruction(Opcode.LAST));
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
    if (expression instanceof Ast.Ternary ternary) {
      compileTernary(ternary, instructions);
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
      compileExpression(property.property(), instructions);
      compileExpression(assignment.value(), instructions);
      instructions.add(new Instruction(Opcode.SET_PROPERTY));
      return;
    }
    if (assignment.target() instanceof Ast.IndexTarget index) {
      if (index.collection() instanceof Ast.Identifier owner) {
        instructions.add(new Instruction(Opcode.LOAD_LOCAL, owner.name()));
        instructions.add(new Instruction(Opcode.ENTER_INDEX));
        compileExpression(index.index(), instructions);
        compileExpression(assignment.value(), instructions);
        instructions.add(new Instruction(Opcode.SET_INDEX_LOCAL, owner.name()));
        return;
      }
      if (index.collection() instanceof Ast.IndexAccess parent
          && parent.collection() instanceof Ast.Identifier owner) {
        instructions.add(new Instruction(Opcode.LOAD_LOCAL, owner.name()));
        instructions.add(new Instruction(Opcode.ENTER_INDEX));
        compileExpression(parent.index(), instructions);
        instructions.add(new Instruction(Opcode.INDEX, 1));
        instructions.add(new Instruction(Opcode.ENTER_INDEX));
        compileExpression(index.index(), instructions);
        compileExpression(assignment.value(), instructions);
        instructions.add(new Instruction(Opcode.SET_INDEX_LOCAL, 1, owner.name()));
        return;
      }
      throw new IllegalArgumentException("indexed assignment requires a local owner");
    }
    if (assignment.target() instanceof Ast.RangeTarget range) {
      if (range.collection() instanceof Ast.Identifier owner) {
        instructions.add(new Instruction(Opcode.LOAD_LOCAL, owner.name()));
        instructions.add(new Instruction(Opcode.ENTER_INDEX));
        compileExpression(range.start(), instructions);
        compileExpression(range.end(), instructions);
        compileExpression(assignment.value(), instructions);
        instructions.add(new Instruction(Opcode.SET_RANGE_LOCAL, owner.name()));
        return;
      }
      if (range.collection() instanceof Ast.IndexAccess parent
          && parent.collection() instanceof Ast.Identifier owner) {
        instructions.add(new Instruction(Opcode.LOAD_LOCAL, owner.name()));
        instructions.add(new Instruction(Opcode.ENTER_INDEX));
        compileExpression(parent.index(), instructions);
        instructions.add(new Instruction(Opcode.INDEX, 1));
        instructions.add(new Instruction(Opcode.ENTER_INDEX));
        compileExpression(range.start(), instructions);
        compileExpression(range.end(), instructions);
        compileExpression(assignment.value(), instructions);
        instructions.add(new Instruction(Opcode.SET_RANGE_LOCAL, 1, owner.name()));
        return;
      }
      throw new IllegalArgumentException("range assignment requires a local owner");
    }
    if (assignment.target() instanceof Ast.ScatterTarget scatter) {
      compileExpression(assignment.value(), instructions);
      List<String> encodedElements = new ArrayList<>();
      for (Ast.ScatterElement element : scatter.elements()) {
        element.defaultValue().ifPresent(value -> compileExpression(value, instructions));
        encodedElements.add(
            (element.rest()
                    ? "@"
                    : element.defaultValue().isPresent() ? "$" : element.optional() ? "?" : "")
                + element.name());
      }
      instructions.add(
          new Instruction(
              Opcode.SCATTER, scatter.elements().size(), String.join(",", encodedElements)));
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

  private void compileTernary(Ast.Ternary ternary, List<Instruction> instructions) {
    compileExpression(ternary.condition(), instructions);
    int falseJump = addJump(Opcode.JUMP_IF_FALSE, instructions);
    compileExpression(ternary.trueExpression(), instructions);
    int endJump = addJump(Opcode.JUMP, instructions);
    patchJump(falseJump, instructions.size(), instructions);
    compileExpression(ternary.falseExpression(), instructions);
    patchJump(endJump, instructions.size(), instructions);
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
                catchTarget,
                Optional.of(caughtLocal),
                catchesAny,
                caughtErrors,
                false,
                -1,
                endTarget)));
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
      case IN -> Opcode.IN;
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
