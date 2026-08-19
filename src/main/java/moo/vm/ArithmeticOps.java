package moo.vm;

import java.util.Map;
import java.util.Optional;
import moo.bytecode.BytecodeProgram.Instruction;
import moo.value.MooValue;
import moo.value.MooValue.BooleanValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;
import moo.value.ValueSemantics;
import moo.vm.VmState.Frame;
import moo.world.WorldTxn;

/** Numeric, bitwise, equality, ordering, and membership operations. */
final class ArithmeticOps {
  enum Operation {
    NEGATE,
    NOT,
    COMPLEMENT,
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    REMAINDER,
    POWER,
    BITOR,
    BITAND,
    BITXOR,
    BITSHL,
    BITSHR,
    EQUAL,
    NOT_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    IN
  }

  private enum NumericOperation {
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    REMAINDER,
    POWER
  }

  private enum BitwiseOperation {
    OR,
    AND,
    XOR,
    SHIFT_LEFT,
    SHIFT_RIGHT
  }

  private enum EqualityOperation {
    EQUAL,
    NOT_EQUAL
  }

  private enum ComparisonOperation {
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL
  }

  private enum Ordering {
    LESS,
    EQUAL,
    GREATER,
    UNORDERED
  }

  private ArithmeticOps() {}

  static boolean execute(
      Operation operation,
      VmState state,
      Instruction instruction,
      Frame frame,
      WorldTxn world,
      ValueSemantics valueSemantics) {
    return switch (operation) {
      case NEGATE -> {
        unaryNegate(frame, state, world);
        yield true;
      }
      case NOT -> {
        logicalNot(frame);
        yield true;
      }
      case COMPLEMENT -> {
        bitwiseComplement(frame, state, world);
        yield true;
      }
      case ADD -> {
        arithmetic(NumericOperation.ADD, frame, state, world, valueSemantics);
        yield true;
      }
      case SUBTRACT -> {
        arithmetic(NumericOperation.SUBTRACT, frame, state, world, valueSemantics);
        yield true;
      }
      case MULTIPLY -> {
        arithmetic(NumericOperation.MULTIPLY, frame, state, world, valueSemantics);
        yield true;
      }
      case DIVIDE -> {
        arithmetic(NumericOperation.DIVIDE, frame, state, world, valueSemantics);
        yield true;
      }
      case REMAINDER -> {
        arithmetic(NumericOperation.REMAINDER, frame, state, world, valueSemantics);
        yield true;
      }
      case POWER -> {
        arithmetic(NumericOperation.POWER, frame, state, world, valueSemantics);
        yield true;
      }
      case BITOR -> {
        bitwise(BitwiseOperation.OR, frame, state, world);
        yield true;
      }
      case BITAND -> {
        bitwise(BitwiseOperation.AND, frame, state, world);
        yield true;
      }
      case BITXOR -> {
        bitwise(BitwiseOperation.XOR, frame, state, world);
        yield true;
      }
      case BITSHL -> {
        bitwise(BitwiseOperation.SHIFT_LEFT, frame, state, world);
        yield true;
      }
      case BITSHR -> {
        bitwise(BitwiseOperation.SHIFT_RIGHT, frame, state, world);
        yield true;
      }
      case EQUAL -> {
        equality(EqualityOperation.EQUAL, frame, valueSemantics);
        yield true;
      }
      case NOT_EQUAL -> {
        equality(EqualityOperation.NOT_EQUAL, frame, valueSemantics);
        yield true;
      }
      case LESS_THAN -> {
        comparison(ComparisonOperation.LESS_THAN, frame, state, world, valueSemantics);
        yield true;
      }
      case LESS_THAN_OR_EQUAL -> {
        comparison(ComparisonOperation.LESS_THAN_OR_EQUAL, frame, state, world, valueSemantics);
        yield true;
      }
      case GREATER_THAN -> {
        comparison(ComparisonOperation.GREATER_THAN, frame, state, world, valueSemantics);
        yield true;
      }
      case GREATER_THAN_OR_EQUAL -> {
        comparison(
            ComparisonOperation.GREATER_THAN_OR_EQUAL, frame, state, world, valueSemantics);
        yield true;
      }
      case IN -> {
        membership(frame, state, world, valueSemantics);
        yield true;
      }
    };
  }

  private static void logicalNot(Frame frame) {
    MooValue value = frame.operandStack.pop();
    frame.operandStack.push(new IntegerValue(value.isTruthy() ? 0 : 1));
    frame.instructionPointer++;
  }

  private static void membership(
      Frame frame, VmState state, WorldTxn world, ValueSemantics valueSemantics) {
    MooValue collection = frame.operandStack.pop();
    MooValue requested = frame.operandStack.pop();
    if (collection instanceof StringValue haystack && requested instanceof StringValue needle) {
      int position = haystack.indexOfIgnoringCase(needle);
      frame.operandStack.push(new IntegerValue(position < 0 ? 0 : position + 1L));
      frame.instructionPointer++;
      return;
    }
    if (collection instanceof MapValue map) {
      long position = 0;
      int index = 0;
      for (MooValue value : map.entries().values()) {
        index++;
        if (mooEquals(requested, value, valueSemantics)) {
          position = index;
          break;
        }
      }
      frame.operandStack.push(new IntegerValue(position));
      frame.instructionPointer++;
      return;
    }
    if (!(collection instanceof ListValue list)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    long position = 0;
    for (int index = 0; index < list.elements().size(); index++) {
      if (mooEquals(requested, list.elements().get(index), valueSemantics)) {
        position = index + 1L;
        break;
      }
    }
    frame.operandStack.push(new IntegerValue(position));
    frame.instructionPointer++;
  }

  private static void unaryNegate(Frame frame, VmState state, WorldTxn world) {
    MooValue operand = frame.operandStack.pop();
    if (operand instanceof IntegerValue integer) {
      frame.operandStack.push(new IntegerValue(-integer.value()));
      frame.instructionPointer++;
      return;
    }
    if (operand instanceof FloatValue floating) {
      double result = -floating.value();
      if (!Double.isFinite(result)) {
        ErrorOps.raise(state, ErrorValue.E_FLOAT, world);
        return;
      }
      frame.operandStack.push(new FloatValue(result));
      frame.instructionPointer++;
      return;
    }
    ErrorOps.raise(state, ErrorValue.E_TYPE, world);
  }

  private static void bitwiseComplement(Frame frame, VmState state, WorldTxn world) {
    MooValue operand = frame.operandStack.pop();
    if (!(operand instanceof IntegerValue integer)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    frame.operandStack.push(new IntegerValue(~integer.value()));
    frame.instructionPointer++;
  }

  private static void bitwise(
      BitwiseOperation operation, Frame frame, VmState state, WorldTxn world) {
    MooValue rightValue = frame.operandStack.pop();
    MooValue leftValue = frame.operandStack.pop();
    if (!(leftValue instanceof IntegerValue left)
        || !(rightValue instanceof IntegerValue right)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    if ((operation == BitwiseOperation.SHIFT_LEFT || operation == BitwiseOperation.SHIFT_RIGHT)
        && (right.value() < 0 || right.value() > Long.SIZE)) {
      ErrorOps.raise(state, ErrorValue.E_INVARG, world, false);
      return;
    }
    long result = bitwiseResult(operation, left.value(), right.value());
    frame.operandStack.push(new IntegerValue(result));
    frame.instructionPointer++;
  }

  private static long bitwiseResult(BitwiseOperation operation, long left, long right) {
    return switch (operation) {
      case OR -> left | right;
      case AND -> left & right;
      case XOR -> left ^ right;
      case SHIFT_LEFT -> right == Long.SIZE ? 0 : left << right;
      case SHIFT_RIGHT -> right == Long.SIZE ? 0 : left >>> right;
    };
  }

  private static void arithmetic(
      NumericOperation operation,
      Frame frame,
      VmState state,
      WorldTxn world,
      ValueSemantics valueSemantics) {
    MooValue rightValue = frame.operandStack.pop();
    MooValue leftValue = frame.operandStack.pop();
    if (operation == NumericOperation.ADD
        && leftValue instanceof StringValue left
        && rightValue instanceof StringValue right) {
      byte[] leftBytes = left.bytes();
      byte[] rightBytes = right.bytes();
      byte[] concatenated = new byte[Math.addExact(leftBytes.length, rightBytes.length)];
      System.arraycopy(leftBytes, 0, concatenated, 0, leftBytes.length);
      System.arraycopy(rightBytes, 0, concatenated, leftBytes.length, rightBytes.length);
      frame.operandStack.push(StringValue.of(concatenated));
      frame.instructionPointer++;
      return;
    }
    if (operation == NumericOperation.ADD
        && leftValue instanceof ListValue left
        && rightValue instanceof ListValue right) {
      frame.operandStack.push(left.concatenate(right));
      frame.instructionPointer++;
      return;
    }
    if (operation == NumericOperation.ADD && leftValue instanceof ListValue left) {
      frame.operandStack.push(left.append(rightValue));
      frame.instructionPointer++;
      return;
    }
    if (valueSemantics.promoteNumbers() && isMixedIntegerFloat(leftValue, rightValue)) {
      leftValue = promoteInteger(leftValue);
      rightValue = promoteInteger(rightValue);
    }
    if (leftValue instanceof IntegerValue left && rightValue instanceof IntegerValue right) {
      if (dividesByZero(operation, right.value())) {
        ErrorOps.raise(state, ErrorValue.E_DIV, world);
        return;
      }
      if (operation == NumericOperation.POWER && left.value() == 0 && right.value() < 0) {
        ErrorOps.raise(state, ErrorValue.E_DIV, world);
        return;
      }
      frame.operandStack.push(new IntegerValue(integerResult(operation, left.value(), right.value())));
      frame.instructionPointer++;
      return;
    }
    if (operation == NumericOperation.POWER
        && leftValue instanceof FloatValue left
        && rightValue instanceof IntegerValue right) {
      if (left.value() == 0.0 && right.value() < 0) {
        ErrorOps.raise(state, ErrorValue.E_DIV, world);
        return;
      }
      pushFiniteFloat(frame, state, world, Math.pow(left.value(), (double) right.value()));
      return;
    }
    if (leftValue instanceof FloatValue left && rightValue instanceof FloatValue right) {
      if (dividesByZero(operation, right.value())) {
        ErrorOps.raise(state, ErrorValue.E_DIV, world);
        return;
      }
      if (operation == NumericOperation.POWER && left.value() == 0.0 && right.value() < 0.0) {
        ErrorOps.raise(state, ErrorValue.E_DIV, world);
        return;
      }
      pushFiniteFloat(frame, state, world, floatResult(operation, left.value(), right.value()));
      return;
    }
    ErrorOps.raise(state, ErrorValue.E_TYPE, world);
  }

  private static boolean dividesByZero(NumericOperation operation, double right) {
    return (operation == NumericOperation.DIVIDE || operation == NumericOperation.REMAINDER)
        && right == 0.0;
  }

  private static long integerResult(NumericOperation operation, long left, long right) {
    return switch (operation) {
      case ADD -> left + right;
      case SUBTRACT -> left - right;
      case MULTIPLY -> left * right;
      case DIVIDE -> left == -Long.MAX_VALUE && right == -1 ? -Long.MAX_VALUE : left / right;
      case REMAINDER -> (left % right + right) % right;
      case POWER -> integerPower(left, right);
    };
  }

  private static double floatResult(NumericOperation operation, double left, double right) {
    return switch (operation) {
      case ADD -> left + right;
      case SUBTRACT -> left - right;
      case MULTIPLY -> left * right;
      case DIVIDE -> left / right;
      case REMAINDER -> mooRemainder(left, right);
      case POWER -> Math.pow(left, right);
    };
  }

  private static double mooRemainder(double left, double right) {
    double remainder = left % right;
    if (remainder != 0.0 && Math.copySign(1.0, remainder) != Math.copySign(1.0, right)) {
      remainder += right;
    }
    return remainder == 0.0 ? Math.copySign(0.0, right) : remainder;
  }

  private static void pushFiniteFloat(
      Frame frame, VmState state, WorldTxn world, double result) {
    if (!Double.isFinite(result)) {
      ErrorOps.raise(state, ErrorValue.E_FLOAT, world);
      return;
    }
    frame.operandStack.push(new FloatValue(result));
    frame.instructionPointer++;
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

  private static void equality(
      EqualityOperation operation, Frame frame, ValueSemantics valueSemantics) {
    MooValue right = frame.operandStack.pop();
    MooValue left = frame.operandStack.pop();
    boolean equal = mooEquals(left, right, valueSemantics);
    if (operation == EqualityOperation.NOT_EQUAL) {
      equal = !equal;
    }
    frame.operandStack.push(new IntegerValue(equal ? 1 : 0));
    frame.instructionPointer++;
  }

  private static boolean mooEquals(
      MooValue left, MooValue right, ValueSemantics valueSemantics) {
    if (left instanceof BooleanValue bool && right instanceof IntegerValue integer) {
      return integer.value() == (bool.value() ? 1 : 0);
    }
    if (left instanceof IntegerValue integer && right instanceof BooleanValue bool) {
      return integer.value() == (bool.value() ? 1 : 0);
    }
    if (valueSemantics.promoteNumbers() && isMixedIntegerFloat(left, right)) {
      return numericDouble(left) == numericDouble(right);
    }
    if (left instanceof ListValue leftList && right instanceof ListValue rightList) {
      if (leftList.size() != rightList.size()) {
        return false;
      }
      for (int index = 0; index < leftList.size(); index++) {
        if (!mooEquals(leftList.elements().get(index), rightList.elements().get(index), valueSemantics)) {
          return false;
        }
      }
      return true;
    }
    if (left instanceof MapValue leftMap && right instanceof MapValue rightMap) {
      if (leftMap.size() != rightMap.size()) {
        return false;
      }
      var leftEntries = leftMap.entries().entrySet().iterator();
      var rightEntries = rightMap.entries().entrySet().iterator();
      while (leftEntries.hasNext()) {
        Map.Entry<MooValue, MooValue> leftEntry = leftEntries.next();
        Map.Entry<MooValue, MooValue> rightEntry = rightEntries.next();
        if (!mooEquals(leftEntry.getKey(), rightEntry.getKey(), valueSemantics)
            || !mooEquals(leftEntry.getValue(), rightEntry.getValue(), valueSemantics)) {
          return false;
        }
      }
      return true;
    }
    return left.equals(right);
  }

  private static void comparison(
      ComparisonOperation operation,
      Frame frame,
      VmState state,
      WorldTxn world,
      ValueSemantics valueSemantics) {
    MooValue rightValue = frame.operandStack.pop();
    MooValue leftValue = frame.operandStack.pop();
    if (valueSemantics.promoteNumbers() && isMixedIntegerFloat(leftValue, rightValue)) {
      leftValue = promoteInteger(leftValue);
      rightValue = promoteInteger(rightValue);
    }
    Optional<Ordering> optionalComparison = compare(leftValue, rightValue);
    if (optionalComparison.isEmpty()) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    Ordering comparison = optionalComparison.orElseThrow();
    boolean result = comparisonResult(operation, comparison);
    frame.operandStack.push(new IntegerValue(result ? 1 : 0));
    frame.instructionPointer++;
  }

  private static Optional<Ordering> compare(MooValue leftValue, MooValue rightValue) {
    if ((leftValue instanceof BooleanValue && rightValue instanceof BooleanValue)
        || (leftValue instanceof WaifValue && rightValue instanceof WaifValue)) {
      return Optional.of(Ordering.EQUAL);
    }
    if (leftValue instanceof IntegerValue left && rightValue instanceof IntegerValue right) {
      return Optional.of(ordering(Long.compare(left.value(), right.value())));
    }
    if (leftValue instanceof ObjectValue left && rightValue instanceof ObjectValue right) {
      return Optional.of(ordering(Long.compare(left.value(), right.value())));
    }
    if (leftValue instanceof FloatValue left && rightValue instanceof FloatValue right) {
      return Optional.of(primitiveDoubleOrdering(left.value(), right.value()));
    }
    if (leftValue instanceof ErrorValue left && rightValue instanceof ErrorValue right) {
      return Optional.of(ordering(Integer.compare(left.code(), right.code())));
    }
    if (leftValue instanceof StringValue left && rightValue instanceof StringValue right) {
      return Optional.of(ordering(left.compareIgnoringCase(right)));
    }
    return Optional.empty();
  }

  private static Ordering primitiveDoubleOrdering(double left, double right) {
    if (Double.isNaN(left) || Double.isNaN(right)) {
      return Ordering.UNORDERED;
    }
    if (left < right) {
      return Ordering.LESS;
    }
    return left > right ? Ordering.GREATER : Ordering.EQUAL;
  }

  private static Ordering ordering(int comparison) {
    if (comparison < 0) {
      return Ordering.LESS;
    }
    return comparison > 0 ? Ordering.GREATER : Ordering.EQUAL;
  }

  private static boolean comparisonResult(ComparisonOperation operation, Ordering comparison) {
    return switch (operation) {
      case LESS_THAN -> comparison == Ordering.LESS;
      case LESS_THAN_OR_EQUAL ->
          comparison == Ordering.LESS || comparison == Ordering.EQUAL;
      case GREATER_THAN -> comparison == Ordering.GREATER;
      case GREATER_THAN_OR_EQUAL ->
          comparison == Ordering.GREATER || comparison == Ordering.EQUAL;
    };
  }

  private static boolean isMixedIntegerFloat(MooValue left, MooValue right) {
    return (left instanceof IntegerValue && right instanceof FloatValue)
        || (left instanceof FloatValue && right instanceof IntegerValue);
  }

  private static MooValue promoteInteger(MooValue value) {
    return value instanceof IntegerValue integer ? new FloatValue((double) integer.value()) : value;
  }

  private static double numericDouble(MooValue value) {
    return value instanceof IntegerValue integer
        ? integer.value()
        : ((FloatValue) value).value();
  }
}
