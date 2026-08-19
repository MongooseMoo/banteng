package world.mongoose.banteng.vm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.BytecodeProgram.Instruction;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.vm.VmState.ActiveHandler;
import world.mongoose.banteng.vm.VmState.CollectionCursor;
import world.mongoose.banteng.vm.VmState.Frame;
import world.mongoose.banteng.vm.VmState.LoopCursor;
import world.mongoose.banteng.vm.VmState.RangeCursor;
import world.mongoose.banteng.world.WorldResult;
import world.mongoose.banteng.world.WorldTxn;

/** Fork, branch, handler, loop, scatter, and return operations. */
final class LoopOps {
  enum Operation {
    FORK,
    JUMP,
    JUMP_IF_FALSE,
    JUMP_IF_TRUE,
    ENTER_HANDLER,
    LEAVE_HANDLER,
    END_FINALLY,
    ITERATE,
    ITERATE_RANGE,
    LEAVE_LOOP,
    SCATTER,
    RETURN
  }

  private LoopOps() {}

  static boolean execute(
      Operation operation, VmState state, Instruction instruction, Frame frame, WorldTxn world) {
    return switch (operation) {
      case FORK -> {
        fork(instruction, frame, state, world);
        yield true;
      }
      case JUMP -> {
        frame.instructionPointer = target(instruction);
        yield true;
      }
      case JUMP_IF_FALSE -> {
        conditionalJump(instruction, frame, false);
        yield true;
      }
      case JUMP_IF_TRUE -> {
        conditionalJump(instruction, frame, true);
        yield true;
      }
      case ENTER_HANDLER -> {
        frame.handlers.push(
            new ActiveHandler(instruction.handler().orElseThrow(), frame.operandStack.size()));
        frame.instructionPointer++;
        yield true;
      }
      case LEAVE_HANDLER -> {
        leaveHandler(frame);
        yield true;
      }
      case END_FINALLY -> {
        endFinally(state, world);
        yield true;
      }
      case ITERATE -> {
        iterate(instruction, frame, state, world);
        yield true;
      }
      case ITERATE_RANGE -> {
        iterateRange(instruction, frame, state, world);
        yield true;
      }
      case LEAVE_LOOP -> {
        frame.loops.remove(Math.toIntExact(instruction.operand().orElseThrow()));
        frame.instructionPointer++;
        yield true;
      }
      case SCATTER -> {
        scatter(instruction, frame, state, world);
        yield true;
      }
      case RETURN -> {
        MooValue value = frame.operandStack.pop();
        frame.instructionPointer++;
        routeReturn(state, value, world);
        yield true;
      }
    };
  }

  static void routeReturn(VmState state, MooValue value, WorldTxn world) {
    routeReturnInternal(state, value, world);
  }

  private static void fork(Instruction instruction, Frame frame, VmState state, WorldTxn world) {
    MooValue delay = frame.operandStack.pop();
    double seconds;
    if (delay instanceof IntegerValue integer) {
      seconds = integer.value();
    } else if (delay instanceof FloatValue floating) {
      seconds = floating.value();
    } else {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    if (seconds < 0) {
      ErrorOps.raise(state, ErrorValue.E_INVARG, world);
      return;
    }
    BytecodeProgram child =
        frame.program.forkVectors().get(Math.toIntExact(instruction.operand().orElseThrow()));
    frame.instructionPointer++;
    state.requestFork(child, seconds);
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
          new VmSnapshot.FallThrough(handler.specification.endTarget()));
      frame.instructionPointer = handler.specification.finallyTarget();
    } else {
      frame.instructionPointer = handler.specification.endTarget();
    }
  }

  private static void endFinally(VmState state, WorldTxn world) {
    Frame frame = state.currentFrame();
    VmSnapshot.FinallyState continuation = frame.finallyContinuations.pop();
    switch (continuation) {
      case VmSnapshot.FallThrough fallThrough ->
          frame.instructionPointer = fallThrough.target();
      case VmSnapshot.Raise raise -> {
        List<MooValue> tuple = raise.exception().elements();
        ErrorOps.raise(
            state,
            (ErrorValue) tuple.get(0),
            new ListValue(tuple.subList(1, tuple.size())),
            world);
      }
      case VmSnapshot.Uncaught uncaught ->
          state.failUncaught(
              uncaught.value() instanceof ErrorValue error
                  ? error
                  : state.uncaughtError().orElse(ErrorValue.E_NONE));
      case VmSnapshot.Return returned -> routeReturnInternal(state, returned.value(), world);
      case VmSnapshot.Exit exit -> {
        while (frame.operandStack.size() > exit.operandDepth()) {
          frame.operandStack.pop();
        }
        frame.loops.entrySet().removeIf(
            entry -> {
              Instruction iterate = frame.program.instructions().get(entry.getKey());
              return target(iterate) <= exit.target();
            });
        frame.instructionPointer = exit.target();
      }
    }
  }

  private static void iterate(Instruction instruction, Frame frame, VmState state, WorldTxn world) {
    int instructionIndex = frame.instructionPointer;
    LoopCursor cursor = frame.loops.get(instructionIndex);
    if (cursor == null) {
      MooValue iterable = frame.operandStack.pop();
      if (iterable instanceof ListValue list) {
        cursor =
            new CollectionCursor(
                VmSnapshot.CollectionKind.LIST, list, Optional.of(new IntegerValue(1)));
      } else if (iterable instanceof StringValue string) {
        cursor =
            new CollectionCursor(
                VmSnapshot.CollectionKind.STRING, string, Optional.of(new IntegerValue(1)));
      } else if (iterable instanceof MapValue map) {
        cursor =
            new CollectionCursor(
                VmSnapshot.CollectionKind.MAP,
                map,
                map.entries().keySet().stream().findFirst());
      } else {
        ErrorOps.raise(state, ErrorValue.E_TYPE, world);
        return;
      }
      frame.loops.put(instructionIndex, cursor);
    }
    if (!(cursor instanceof CollectionCursor collection)) {
      throw new IllegalStateException("collection instruction restored with a range cursor");
    }
    String[] variables = instruction.text().orElseThrow().split(",", -1);
    if (variables.length < 1 || variables.length > 2) {
      throw new IllegalStateException("collection loop requires one or two variables");
    }
    Optional<CollectionElement> next = nextCollectionElement(collection);
    if (next.isEmpty()) {
      frame.loops.remove(instructionIndex);
      frame.instructionPointer = target(instruction);
      return;
    }
    CollectionElement element = next.orElseThrow();
    frame.locals.put(PropertyOps.normalize(variables[0]), element.value());
    if (variables.length == 2) {
      frame.locals.put(PropertyOps.normalize(variables[1]), element.indexOrKey());
    }
    frame.instructionPointer++;
  }

  private static Optional<CollectionElement> nextCollectionElement(CollectionCursor cursor) {
    if (cursor.kind == VmSnapshot.CollectionKind.MAP) {
      if (cursor.next.isEmpty()) {
        return Optional.empty();
      }
      MapValue map = (MapValue) cursor.base;
      MooValue requestedKey = cursor.next.orElseThrow();
      List<Map.Entry<MooValue, MooValue>> entries = new ArrayList<>(map.entries().entrySet());
      for (int index = 0; index < entries.size(); index++) {
        Map.Entry<MooValue, MooValue> entry = entries.get(index);
        if (entry.getKey().equals(requestedKey)) {
          cursor.next =
              index + 1 < entries.size()
                  ? Optional.of(entries.get(index + 1).getKey())
                  : Optional.empty();
          return Optional.of(new CollectionElement(entry.getValue(), entry.getKey()));
        }
      }
      throw new IllegalStateException("map loop cursor is not a key in its base");
    }
    long oneBasedIndex = ((IntegerValue) cursor.next.orElseThrow()).value();
    int length =
        cursor.kind == VmSnapshot.CollectionKind.LIST
            ? ((ListValue) cursor.base).size()
            : ((StringValue) cursor.base).length();
    if (oneBasedIndex > length) {
      return Optional.empty();
    }
    MooValue value;
    if (cursor.kind == VmSnapshot.CollectionKind.LIST) {
      value = ((ListValue) cursor.base).elements().get(Math.toIntExact(oneBasedIndex - 1));
    } else {
      byte character = ((StringValue) cursor.base).bytes()[Math.toIntExact(oneBasedIndex - 1)];
      value = StringValue.of(new byte[] {character});
    }
    cursor.next = Optional.of(new IntegerValue(Math.addExact(oneBasedIndex, 1L)));
    return Optional.of(new CollectionElement(value, new IntegerValue(oneBasedIndex)));
  }

  private static void iterateRange(
      Instruction instruction, Frame frame, VmState state, WorldTxn world) {
    int instructionIndex = frame.instructionPointer;
    LoopCursor cursor = frame.loops.get(instructionIndex);
    if (cursor == null) {
      MooValue endValue = frame.operandStack.pop();
      MooValue startValue = frame.operandStack.pop();
      if (startValue instanceof IntegerValue && endValue instanceof IntegerValue) {
        cursor =
            new RangeCursor(VmSnapshot.RangeKind.INTEGER, startValue, endValue);
      } else if (startValue instanceof ObjectValue && endValue instanceof ObjectValue) {
        cursor =
            new RangeCursor(VmSnapshot.RangeKind.OBJECT, startValue, endValue);
      } else {
        ErrorOps.raise(state, ErrorValue.E_TYPE, world);
        return;
      }
      frame.loops.put(instructionIndex, cursor);
    }
    if (!(cursor instanceof RangeCursor range)) {
      throw new IllegalStateException("range instruction restored with a collection cursor");
    }
    long next = scalar(range.next);
    long end = scalar(range.end);
    if (next > end) {
      frame.loops.remove(instructionIndex);
      frame.instructionPointer = target(instruction);
      return;
    }
    String[] variables = instruction.text().orElseThrow().split(",", -1);
    if (variables.length != 1) {
      throw new IllegalStateException("range loop requires exactly one variable");
    }
    frame.locals.put(PropertyOps.normalize(variables[0]), range.next);
    if (next < Long.MAX_VALUE) {
      range.next = scalarValue(range.kind, next + 1L);
    } else {
      range.end = scalarValue(range.kind, end - 1L);
    }
    frame.instructionPointer++;
  }

  private static long scalar(MooValue value) {
    return value instanceof IntegerValue integer
        ? integer.value()
        : ((ObjectValue) value).value();
  }

  private static MooValue scalarValue(VmSnapshot.RangeKind kind, long value) {
    return kind == VmSnapshot.RangeKind.INTEGER
        ? new IntegerValue(value)
        : new ObjectValue(value);
  }

  private record CollectionElement(MooValue value, MooValue indexOrKey) {}

  private static void scatter(Instruction instruction, Frame frame, VmState state, WorldTxn world) {
    String[] names = instruction.text().orElseThrow().split(",", -1);
    MooValue[] defaults = new MooValue[names.length];
    for (int index = names.length - 1; index >= 0; index--) {
      if (names[index].startsWith("$")) {
        defaults[index] = frame.operandStack.pop();
      }
    }
    MooValue source = frame.operandStack.pop();
    if (!(source instanceof ListValue list)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    int requiredValues = 0;
    boolean hasRest = false;
    for (String name : names) {
      if (name.startsWith("@")) {
        hasRest = true;
      } else if (!name.startsWith("?") && !name.startsWith("$")) {
        requiredValues++;
      }
    }
    if (names.length != instruction.operand().orElseThrow()
        || list.size() < requiredValues
        || (!hasRest && list.size() > names.length)) {
      ErrorOps.raise(state, ErrorValue.E_ARGS, world);
      return;
    }
    int sourceIndex = 0;
    for (int index = 0; index < names.length; index++) {
      String encodedName = names[index];
      String name = PropertyOps.normalize(encodedName.substring(encodedName.startsWith("@")
              || encodedName.startsWith("?")
              || encodedName.startsWith("$") ? 1 : 0));
      int requiredAfter = 0;
      for (int later = index + 1; later < names.length; later++) {
        if (!names[later].startsWith("@")
            && !names[later].startsWith("?")
            && !names[later].startsWith("$")) {
          requiredAfter++;
        }
      }
      if (encodedName.startsWith("@")) {
        int end = list.size() - requiredAfter;
        frame.locals.put(
            name, new ListValue(list.elements().subList(sourceIndex, end)));
        sourceIndex = end;
      } else if (encodedName.startsWith("?") || encodedName.startsWith("$")) {
        if (list.size() - sourceIndex > requiredAfter) {
          frame.locals.put(name, list.elements().get(sourceIndex++));
        } else if (defaults[index] != null) {
          frame.locals.put(name, defaults[index]);
        }
      } else {
        frame.locals.put(name, list.elements().get(sourceIndex++));
      }
    }
    frame.operandStack.push(source);
    frame.instructionPointer++;
  }

  private static void routeReturnInternal(VmState state, MooValue value, WorldTxn world) {
    Frame frame = state.currentFrame();
    if (!frame.finallyContinuations.isEmpty()) {
      frame.finallyContinuations.pop();
    }
    while (!frame.handlers.isEmpty()) {
      ActiveHandler handler = frame.handlers.pop();
      if (handler.specification.finallyTarget() >= 0) {
        while (frame.operandStack.size() > handler.operandDepth) {
          frame.operandStack.pop();
        }
        frame.finallyContinuations.push(
            new VmSnapshot.Return(value));
        frame.instructionPointer = handler.specification.finallyTarget();
        return;
      }
    }
    if (frame.recycleTarget.isPresent()) {
      long recycleTarget = frame.recycleTarget.orElseThrow();
      state.unwindChildFrame();
      WorldResult<Boolean> result = world.recycleObject(recycleTarget);
      if (result instanceof WorldResult.Failed<?> failed) {
        ErrorOps.raise(state, failed.reason().value(), world, false);
        return;
      }
      state.currentFrame().operandStack.push(new IntegerValue(0));
      return;
    }
    if (frame.anonymousRecycleTarget.isPresent()) {
      state.unwindChildFrame();
      state.currentFrame().operandStack.push(new IntegerValue(0));
      return;
    }
    if (frame.moveObject.isPresent()
        && frame.moveDestination.isPresent()
        && frame.movePosition.isPresent()) {
      long moveObject = frame.moveObject.orElseThrow();
      long moveDestination = frame.moveDestination.orElseThrow();
      long movePosition = frame.movePosition.orElseThrow();
      state.unwindChildFrame();
      if (!ErrorOps.propagateWorldFailure(
          world.move(moveObject, moveDestination, movePosition), state, world)) {
        return;
      }
      state.currentFrame().operandStack.push(new IntegerValue(0));
      return;
    }
    state.finishFrame(value);
  }

  private static int target(Instruction instruction) {
    return Math.toIntExact(instruction.operand().orElseThrow());
  }

}
