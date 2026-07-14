package moo.vm;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import moo.builtin.BuiltinCatalog;
import moo.builtin.BuiltinCatalog.Result;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.BytecodeProgram.Instruction;
import moo.syntax.MooParser;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.vm.VmState.ActiveHandler;
import moo.vm.VmState.ContinuationKind;
import moo.vm.VmState.FinallyContinuation;
import moo.vm.VmState.Frame;
import moo.vm.VmState.HandlerPhase;
import moo.vm.VmState.LoopCursor;
import moo.world.WorldTxn;

/** Iterative executor for the authorized explicit bytecode state. */
public final class MooVm {
  /** Executes a pure program from the supplied explicit state. */
  public void execute(BytecodeProgram program, VmState state) {
    execute(program, state, new WorldTxn(List.of(), List.of()), new BuiltinCatalog());
  }

  /** Executes with the one concrete world transaction and builtin catalog. */
  public void execute(
      BytecodeProgram program, VmState state, WorldTxn world, BuiltinCatalog builtins) {
    state.ensureRoot(program);
    while (state.outcome() == VmState.Outcome.RUNNING) {
      Frame frame = state.currentFrame();
      if (frame.instructionPointer >= frame.program.instructions().size()) {
        routeReturn(state, new IntegerValue(0));
        continue;
      }
      executeInstruction(
          frame.program.instructions().get(frame.instructionPointer), state, world, builtins);
    }
  }

  private static void executeInstruction(
      Instruction instruction, VmState state, WorldTxn world, BuiltinCatalog builtins) {
    Frame frame = state.currentFrame();
    switch (instruction.opcode()) {
      case PUSH_INTEGER -> {
        frame.operandStack.push(new IntegerValue(instruction.operand().orElseThrow()));
        frame.instructionPointer++;
      }
      case PUSH_FLOAT -> {
        frame.operandStack.push(
            new FloatValue(Double.longBitsToDouble(instruction.operand().orElseThrow())));
        frame.instructionPointer++;
      }
      case PUSH_STRING -> {
        frame.operandStack.push(encode(instruction.text().orElseThrow()));
        frame.instructionPointer++;
      }
      case PUSH_OBJECT -> {
        frame.operandStack.push(new ObjectValue(instruction.operand().orElseThrow()));
        frame.instructionPointer++;
      }
      case PUSH_ERROR -> {
        frame.operandStack.push(ErrorValue.valueOf(instruction.text().orElseThrow()));
        frame.instructionPointer++;
      }
      case BUILD_LIST -> buildList(frame, Math.toIntExact(instruction.operand().orElseThrow()));
      case LOAD_LOCAL -> loadLocal(frame, instruction.text().orElseThrow(), state);
      case STORE_LOCAL -> {
        frame.locals.put(normalize(instruction.text().orElseThrow()), frame.operandStack.pop());
        frame.instructionPointer++;
      }
      case DUP -> {
        frame.operandStack.push(frame.operandStack.getFirst());
        frame.instructionPointer++;
      }
      case POP -> {
        frame.operandStack.pop();
        frame.instructionPointer++;
      }
      case GET_PROPERTY -> getProperty(frame, instruction.text().orElseThrow(), state, world);
      case SET_PROPERTY -> setProperty(frame, instruction.text().orElseThrow(), state, world);
      case INDEX -> index(frame, state);
      case CALL -> invokeBuiltin(instruction, frame, state, world, builtins);
      case NEGATE -> unaryNegate(frame, state);
      case NOT -> {
        MooValue value = frame.operandStack.pop();
        frame.operandStack.push(new IntegerValue(value.isTruthy() ? 0 : 1));
        frame.instructionPointer++;
      }
      case ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, POWER ->
          arithmetic(instruction, frame, state);
      case EQUAL, NOT_EQUAL -> equality(instruction, frame);
      case LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL ->
          comparison(instruction, frame, state);
      case JUMP -> frame.instructionPointer = target(instruction);
      case JUMP_IF_FALSE -> conditionalJump(instruction, frame, false);
      case JUMP_IF_TRUE -> conditionalJump(instruction, frame, true);
      case ENTER_HANDLER -> {
        frame.handlers.push(new ActiveHandler(instruction.handler().orElseThrow()));
        frame.instructionPointer++;
      }
      case LEAVE_HANDLER -> leaveHandler(frame);
      case END_FINALLY -> endFinally(state);
      case ITERATE -> iterate(instruction, frame, state);
      case SCATTER -> scatter(instruction, frame, state);
      case RETURN -> {
        MooValue value = frame.operandStack.pop();
        frame.instructionPointer++;
        routeReturn(state, value);
      }
    }
  }

  private static void buildList(Frame frame, int count) {
    List<MooValue> elements = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      elements.addFirst(frame.operandStack.pop());
    }
    frame.operandStack.push(new ListValue(elements));
    frame.instructionPointer++;
  }

  private static void loadLocal(Frame frame, String name, VmState state) {
    MooValue value = frame.locals.get(normalize(name));
    if (value == null) {
      raiseError(state, ErrorValue.E_VARNF);
      return;
    }
    frame.operandStack.push(value);
    frame.instructionPointer++;
  }

  private static void getProperty(Frame frame, String name, VmState state, WorldTxn world) {
    MooValue receiver = frame.operandStack.pop();
    if (!(receiver instanceof ObjectValue object)) {
      raiseError(state, ErrorValue.E_TYPE);
      return;
    }
    MooValue value = world.readObjectProperty(object.value(), name).orElse(null);
    if (value == null) {
      raiseError(state, ErrorValue.E_PROPNF);
      return;
    }
    frame.operandStack.push(value);
    frame.instructionPointer++;
  }

  private static void setProperty(Frame frame, String name, VmState state, WorldTxn world) {
    MooValue value = frame.operandStack.pop();
    MooValue receiver = frame.operandStack.pop();
    if (!(receiver instanceof ObjectValue object)) {
      raiseError(state, ErrorValue.E_TYPE);
      return;
    }
    if (!world.writeObjectProperty(object.value(), name, value)) {
      raiseError(state, ErrorValue.E_PROPNF);
      return;
    }
    frame.operandStack.push(value);
    frame.instructionPointer++;
  }

  private static void index(Frame frame, VmState state) {
    MooValue index = frame.operandStack.pop();
    MooValue collection = frame.operandStack.pop();
    if (!(collection instanceof ListValue list) || !(index instanceof IntegerValue integer)) {
      raiseError(state, ErrorValue.E_TYPE);
      return;
    }
    MooValue value = list.get(integer.value()).orElse(null);
    if (value == null) {
      raiseError(state, ErrorValue.E_RANGE);
      return;
    }
    frame.operandStack.push(value);
    frame.instructionPointer++;
  }

  private static void invokeBuiltin(
      Instruction instruction,
      Frame frame,
      VmState state,
      WorldTxn world,
      BuiltinCatalog builtins) {
    int argumentCount = Math.toIntExact(instruction.operand().orElseThrow());
    List<MooValue> arguments = new ArrayList<>(argumentCount);
    for (int index = 0; index < argumentCount; index++) {
      arguments.addFirst(frame.operandStack.pop());
    }
    frame.instructionPointer++;
    Result result =
        builtins.invoke(instruction.text().orElseThrow(), arguments, world, state.programmer());
    if (result.error().isPresent()) {
      raiseError(state, result.error().orElseThrow());
      return;
    }
    result.output().ifPresent(state::stageOutput);
    if (result.switchedPlayer().isPresent()) {
      state.switchPlayer(result.switchedPlayer().orElseThrow());
    }
    if (result.programmer().isPresent()) {
      state.setProgrammer(result.programmer().orElseThrow());
    }
    if (result.dynamicSource().isPresent()) {
      try {
        BytecodeProgram dynamicProgram =
            new moo.bytecode.MooCompiler()
                .compile(MooParser.parse(result.dynamicSource().orElseThrow()));
        state.pushEvalFrame(dynamicProgram);
      } catch (MooParser.ParseException error) {
        raiseError(state, ErrorValue.E_INVARG);
      }
      return;
    }
    frame.operandStack.push(result.value().orElseThrow());
  }

  private static void unaryNegate(Frame frame, VmState state) {
    MooValue operand = frame.operandStack.pop();
    if (operand instanceof IntegerValue integer) {
      frame.operandStack.push(new IntegerValue(-integer.value()));
      frame.instructionPointer++;
      return;
    }
    if (operand instanceof FloatValue floating) {
      double result = -floating.value();
      if (!Double.isFinite(result)) {
        raiseError(state, ErrorValue.E_FLOAT);
        return;
      }
      frame.operandStack.push(new FloatValue(result));
      frame.instructionPointer++;
      return;
    }
    raiseError(state, ErrorValue.E_TYPE);
  }

  private static void arithmetic(Instruction instruction, Frame frame, VmState state) {
    MooValue rightValue = frame.operandStack.pop();
    MooValue leftValue = frame.operandStack.pop();
    if (leftValue instanceof IntegerValue left && rightValue instanceof IntegerValue right) {
      if ((instruction.opcode() == BytecodeProgram.Opcode.DIVIDE
              || instruction.opcode() == BytecodeProgram.Opcode.REMAINDER)
          && right.value() == 0) {
        raiseError(state, ErrorValue.E_DIV);
        return;
      }
      long result =
          switch (instruction.opcode()) {
            case ADD -> left.value() + right.value();
            case SUBTRACT -> left.value() - right.value();
            case MULTIPLY -> left.value() * right.value();
            case DIVIDE -> left.value() / right.value();
            case REMAINDER -> left.value() % right.value();
            case POWER -> integerPower(left.value(), right.value());
            default -> throw new AssertionError(instruction.opcode());
          };
      frame.operandStack.push(new IntegerValue(result));
      frame.instructionPointer++;
      return;
    }
    if (instruction.opcode() == BytecodeProgram.Opcode.POWER
        && leftValue instanceof FloatValue left
        && rightValue instanceof IntegerValue right) {
      if (left.value() == 0.0 && right.value() < 0) {
        raiseError(state, ErrorValue.E_DIV);
        return;
      }
      double result = Math.pow(left.value(), (double) right.value());
      if (!Double.isFinite(result)) {
        raiseError(state, ErrorValue.E_FLOAT);
        return;
      }
      frame.operandStack.push(new FloatValue(result));
      frame.instructionPointer++;
      return;
    }
    if (leftValue instanceof FloatValue left && rightValue instanceof FloatValue right) {
      if ((instruction.opcode() == BytecodeProgram.Opcode.DIVIDE
              || instruction.opcode() == BytecodeProgram.Opcode.REMAINDER)
          && right.value() == 0.0) {
        raiseError(state, ErrorValue.E_DIV);
        return;
      }
      if (instruction.opcode() == BytecodeProgram.Opcode.POWER
          && left.value() == 0.0
          && right.value() < 0.0) {
        raiseError(state, ErrorValue.E_DIV);
        return;
      }
      double result =
          switch (instruction.opcode()) {
            case ADD -> left.value() + right.value();
            case SUBTRACT -> left.value() - right.value();
            case MULTIPLY -> left.value() * right.value();
            case DIVIDE -> left.value() / right.value();
            case REMAINDER -> {
              double remainder = left.value() % right.value();
              if (remainder != 0.0
                  && Math.copySign(1.0, remainder) != Math.copySign(1.0, right.value())) {
                remainder += right.value();
              }
              yield remainder == 0.0 ? Math.copySign(0.0, right.value()) : remainder;
            }
            case POWER -> Math.pow(left.value(), right.value());
            default -> throw new AssertionError(instruction.opcode());
          };
      if (!Double.isFinite(result)) {
        raiseError(state, ErrorValue.E_FLOAT);
        return;
      }
      frame.operandStack.push(new FloatValue(result));
      frame.instructionPointer++;
      return;
    }
    raiseError(state, ErrorValue.E_TYPE);
  }

  private static long integerPower(long base, long exponent) {
    if (exponent < 0) {
      return 0;
    }
    long result = 1;
    long factor = base;
    long remaining = exponent;
    while (remaining != 0) {
      if ((remaining & 1) != 0) {
        result *= factor;
      }
      factor *= factor;
      remaining >>>= 1;
    }
    return result;
  }

  private static void equality(Instruction instruction, Frame frame) {
    MooValue right = frame.operandStack.pop();
    MooValue left = frame.operandStack.pop();
    boolean equal = left.equals(right);
    if (instruction.opcode() == BytecodeProgram.Opcode.NOT_EQUAL) {
      equal = !equal;
    }
    frame.operandStack.push(new IntegerValue(equal ? 1 : 0));
    frame.instructionPointer++;
  }

  private static void comparison(Instruction instruction, Frame frame, VmState state) {
    MooValue rightValue = frame.operandStack.pop();
    MooValue leftValue = frame.operandStack.pop();
    if (leftValue instanceof IntegerValue left && rightValue instanceof IntegerValue right) {
      boolean result =
          switch (instruction.opcode()) {
            case LESS_THAN -> left.value() < right.value();
            case LESS_THAN_OR_EQUAL -> left.value() <= right.value();
            case GREATER_THAN -> left.value() > right.value();
            case GREATER_THAN_OR_EQUAL -> left.value() >= right.value();
            default -> throw new AssertionError(instruction.opcode());
          };
      frame.operandStack.push(new IntegerValue(result ? 1 : 0));
      frame.instructionPointer++;
      return;
    }
    if (leftValue instanceof FloatValue left && rightValue instanceof FloatValue right) {
      boolean result =
          switch (instruction.opcode()) {
            case LESS_THAN -> left.value() < right.value();
            case LESS_THAN_OR_EQUAL -> left.value() <= right.value();
            case GREATER_THAN -> left.value() > right.value();
            case GREATER_THAN_OR_EQUAL -> left.value() >= right.value();
            default -> throw new AssertionError(instruction.opcode());
          };
      frame.operandStack.push(new IntegerValue(result ? 1 : 0));
      frame.instructionPointer++;
      return;
    }
    raiseError(state, ErrorValue.E_TYPE);
  }

  private static void conditionalJump(Instruction instruction, Frame frame, boolean truth) {
    MooValue condition = frame.operandStack.pop();
    frame.instructionPointer =
        condition.isTruthy() == truth ? target(instruction) : frame.instructionPointer + 1;
  }

  private static void leaveHandler(Frame frame) {
    ActiveHandler handler = frame.handlers.pop();
    if (handler.specification.finallyTarget() >= 0) {
      frame.finallyContinuations.push(
          new FinallyContinuation(
              ContinuationKind.NORMAL,
              handler.specification.endTarget(),
              java.util.Optional.empty(),
              java.util.Optional.empty()));
      frame.instructionPointer = handler.specification.finallyTarget();
    } else {
      frame.instructionPointer = handler.specification.endTarget();
    }
  }

  private static void endFinally(VmState state) {
    Frame frame = state.currentFrame();
    FinallyContinuation continuation = frame.finallyContinuations.pop();
    switch (continuation.kind()) {
      case NORMAL -> frame.instructionPointer = continuation.normalTarget();
      case RETURN -> routeReturn(state, continuation.returnValue().orElseThrow());
      case ERROR -> raiseError(state, continuation.error().orElseThrow());
    }
  }

  private static void iterate(Instruction instruction, Frame frame, VmState state) {
    int instructionIndex = frame.instructionPointer;
    LoopCursor cursor = frame.loops.get(instructionIndex);
    if (cursor == null) {
      MooValue iterable = frame.operandStack.pop();
      if (!(iterable instanceof ListValue list)) {
        raiseError(state, ErrorValue.E_TYPE);
        return;
      }
      cursor = new LoopCursor(list);
      frame.loops.put(instructionIndex, cursor);
    }
    if (cursor.nextIndex >= cursor.values.size()) {
      frame.loops.remove(instructionIndex);
      frame.instructionPointer = target(instruction);
      return;
    }
    frame.locals.put(
        normalize(instruction.text().orElseThrow()),
        cursor.values.elements().get(cursor.nextIndex++));
    frame.instructionPointer++;
  }

  private static void scatter(Instruction instruction, Frame frame, VmState state) {
    MooValue source = frame.operandStack.pop();
    if (!(source instanceof ListValue list)) {
      raiseError(state, ErrorValue.E_TYPE);
      return;
    }
    String[] names = instruction.text().orElseThrow().split(",", -1);
    if (names.length != instruction.operand().orElseThrow() || names.length != list.size()) {
      raiseError(state, ErrorValue.E_ARGS);
      return;
    }
    for (int index = 0; index < names.length; index++) {
      frame.locals.put(normalize(names[index]), list.elements().get(index));
    }
    frame.operandStack.push(source);
    frame.instructionPointer++;
  }

  private static void routeReturn(VmState state, MooValue value) {
    Frame frame = state.currentFrame();
    if (!frame.finallyContinuations.isEmpty()) {
      frame.finallyContinuations.pop();
    }
    while (!frame.handlers.isEmpty()) {
      ActiveHandler handler = frame.handlers.pop();
      if (handler.specification.finallyTarget() >= 0) {
        frame.finallyContinuations.push(
            new FinallyContinuation(
                ContinuationKind.RETURN,
                -1,
                java.util.Optional.of(value),
                java.util.Optional.empty()));
        frame.instructionPointer = handler.specification.finallyTarget();
        return;
      }
    }
    state.finishFrame(value);
  }

  private static void raiseError(VmState state, ErrorValue error) {
    state.beginError(error);
    while (true) {
      Frame frame = state.currentFrame();
      while (!frame.handlers.isEmpty()) {
        ActiveHandler handler = frame.handlers.getFirst();
        if (handler.phase == HandlerPhase.TRY
            && handler.specification.catchTarget() >= 0
            && catches(handler, error)) {
          handler.phase = HandlerPhase.CATCH;
          handler
              .specification
              .catchVariable()
              .ifPresent(
                  name ->
                      frame.locals.put(
                          name,
                          handler.specification.structuredCatchBinding()
                              ? new ListValue(List.of(error))
                              : error));
          state.clearPendingError();
          frame.instructionPointer = handler.specification.catchTarget();
          return;
        }
        frame.handlers.pop();
        if (handler.specification.finallyTarget() >= 0) {
          frame.finallyContinuations.push(
              new FinallyContinuation(
                  ContinuationKind.ERROR,
                  -1,
                  java.util.Optional.empty(),
                  java.util.Optional.of(error)));
          state.clearPendingError();
          frame.instructionPointer = handler.specification.finallyTarget();
          return;
        }
      }
      if (!state.unwindEvalFrame()) {
        state.failUncaught(error);
        return;
      }
    }
  }

  private static boolean catches(ActiveHandler handler, ErrorValue error) {
    return handler.specification.catchesAny()
        || handler.specification.caughtErrors().contains(error.name());
  }

  private static int target(Instruction instruction) {
    return Math.toIntExact(instruction.operand().orElseThrow());
  }

  private static String normalize(String name) {
    return name.toLowerCase(Locale.ROOT);
  }

  private static StringValue encode(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }
}
