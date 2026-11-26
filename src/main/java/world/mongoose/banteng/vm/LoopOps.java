package world.mongoose.banteng.vm;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.BytecodeProgram.Instruction;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.ValueSemantics;
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
import world.mongoose.banteng.world.WorldTxn;

/** Fork, branch, handler, loop, scatter, and return operations. */
final class LoopOps {
  private LoopOps() {}

  static void routeReturn(VmState state, MooValue value, WorldTxn world) {
    routeReturnInternal(state, value, world);
  }

  static void fork(Instruction instruction, Frame frame, VmState state, WorldTxn world) {
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

  static void conditionalJump(Instruction instruction, Frame frame, boolean truth) {
    MooValue condition = frame.operandStack.pop();
    frame.instructionPointer =
        condition.isTruthy() == truth ? target(instruction) : frame.instructionPointer + 1;
  }

  static void leaveHandler(Frame frame) {
    ActiveHandler handler = frame.handlers.pop();
    if (handler.specification.finallyTarget() >= 0) {
      frame.finallyContinuations.push(
          new VmSnapshot.FallThrough(handler.specification.endTarget()));
      frame.instructionPointer = handler.specification.finallyTarget();
    } else {
      frame.instructionPointer = handler.specification.endTarget();
    }
  }

  static void endFinally(VmState state, WorldTxn world) {
    Frame frame = state.currentFrame();
    VmSnapshot.FinallyState continuation = frame.finallyContinuations.pop();
    switch (continuation) {
      case VmSnapshot.FallThrough fallThrough ->
          frame.instructionPointer = fallThrough.target();
      case VmSnapshot.Raise raise -> {
        List<MooValue> tuple = raise.exception().elements();
        ErrorOps.raise(
            state,
            tuple.get(0),
            new ListValue(tuple.subList(1, tuple.size())),
            world);
      }
      case VmSnapshot.Uncaught uncaught ->
          state.failUncaught(uncaught.value());
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

  static void iterate(Instruction instruction, Frame frame, VmState state, WorldTxn world) {
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
                map.orderedEntries().stream().map(Map.Entry::getKey).findFirst());
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
    frame.locals[instruction.localSlots().get(0)] = element.value();
    if (variables.length == 2) {
      frame.locals[instruction.localSlots().get(1)] = element.indexOrKey();
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
      List<Map.Entry<MooValue, MooValue>> entries = map.orderedEntries();
      for (int index = 0; index < entries.size(); index++) {
        Map.Entry<MooValue, MooValue> entry = entries.get(index);
        if (ValueSemantics.STANDARD.compare(entry.getKey(), requestedKey, false) == 0) {
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

  static void iterateRange(
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
    frame.locals[instruction.localSlots().get(0)] = range.next;
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

  static void scatter(Instruction instruction, Frame frame, VmState state, WorldTxn world) {
    int done = Math.toIntExact(instruction.operand().orElseThrow());
    MooValue source = frame.operandStack.getFirst();
    if (!(source instanceof ListValue list)) {
      frame.operandStack.pop();
      ErrorOps.typeMismatch(state, "list", source, world);
      if (!frame.debug) frame.instructionPointer = done;
      return;
    }
    var bindings = instruction.scatterBindings();
    int required = 0;
    boolean hasRest = false;
    for (var binding : bindings) {
      if (binding.rest()) {
        hasRest = true;
      } else if (!binding.optional()) {
        required++;
      }
    }
    if (list.size() < required || (!hasRest && list.size() > bindings.size())) {
      frame.operandStack.pop();
      ErrorOps.raise(state, ErrorValue.E_ARGS, world);
      if (!frame.debug) frame.instructionPointer = done;
      return;
    }

    int optionalValues = list.size() - required;
    int restValues = Math.max(0, list.size() - bindings.size() + 1);
    int nextInstruction = done;
    int sourceIndex = 0;
    for (int index = 0; index < bindings.size(); index++) {
      var binding = bindings.get(index);
      int slot = instruction.localSlots().get(index);
      if (binding.rest()) {
        int end = sourceIndex + restValues;
        frame.locals[slot] = new ListValue(list.elements().subList(sourceIndex, end));
        sourceIndex = end;
      } else if (!binding.optional()) {
        frame.locals[slot] = list.elements().get(sourceIndex++);
      } else if (optionalValues > 0) {
        optionalValues--;
        frame.locals[slot] = list.elements().get(sourceIndex++);
      } else if (nextInstruction == done && binding.defaultTarget() >= 0) {
        nextInstruction = binding.defaultTarget();
      }
    }
    frame.instructionPointer = nextInstruction;
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
      Frame caller = state.currentFrame();
      MooVm.continueRecycle(recycleTarget, state, world);
      if (state.currentFrame() != caller && state.activeFrames().contains(caller)) {
        state.currentFrame().builtinContinuation = frame.builtinContinuation;
      }
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
      MoveOps.returned(frame, value, state, world);
      return;
    }
    state.finishFrame(value);
  }

  static int target(Instruction instruction) {
    return Math.toIntExact(instruction.operand().orElseThrow());
  }

}
