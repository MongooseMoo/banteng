package moo.vm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import moo.builtin.BuiltinCatalog;
import moo.builtin.BuiltinResult;
import moo.bytecode.BytecodeProgram.Instruction;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.vm.VmState.Frame;
import moo.world.WorldTxn;

/** List and map construction operations. */
final class ListOps {
  enum Operation {
    BUILD_LIST,
    LIST_APPEND,
    LIST_EXTEND,
    BUILD_MAP
  }

  private ListOps() {}

  static boolean execute(
      Operation operation, VmState state, Instruction instruction, Frame frame, WorldTxn world) {
    return switch (operation) {
      case BUILD_LIST -> {
        buildList(frame, Math.toIntExact(instruction.operand().orElseThrow()));
        yield true;
      }
      case LIST_APPEND -> {
        appendList(frame, state, world);
        yield true;
      }
      case LIST_EXTEND -> {
        extendList(frame, state, world);
        yield true;
      }
      case BUILD_MAP -> {
        buildMap(frame, state, world, Math.toIntExact(instruction.operand().orElseThrow()));
        yield true;
      }
    };
  }

  private static void buildList(Frame frame, int count) {
    List<MooValue> elements = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      elements.addFirst(frame.operandStack.pop());
    }
    frame.operandStack.push(new ListValue(elements));
    frame.instructionPointer++;
  }

  private static void appendList(Frame frame, VmState state, WorldTxn world) {
    MooValue value = frame.operandStack.pop();
    MooValue collection = frame.operandStack.pop();
    if (!(collection instanceof ListValue list)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    List<MooValue> elements = new ArrayList<>(list.elements());
    elements.add(value);
    pushCheckedList(frame, state, world, new ListValue(elements));
  }

  private static void extendList(Frame frame, VmState state, WorldTxn world) {
    MooValue extension = frame.operandStack.pop();
    MooValue collection = frame.operandStack.pop();
    if (!(collection instanceof ListValue list) || !(extension instanceof ListValue appended)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    List<MooValue> elements = new ArrayList<>(list.elements());
    elements.addAll(appended.elements());
    pushCheckedList(frame, state, world, new ListValue(elements));
  }

  private static void pushCheckedList(
      Frame frame, VmState state, WorldTxn world, ListValue value) {
    BuiltinResult checked = BuiltinCatalog.enforceListValueLimit(value, world);
    if (checked instanceof BuiltinResult.Value checkedValue) {
      frame.operandStack.push(checkedValue.value());
      frame.instructionPointer++;
      return;
    }
    MooVm.applyBuiltinResult(checked, frame, state, world);
  }

  private static void buildMap(Frame frame, VmState state, WorldTxn world, int count) {
    List<MooValue> keys = new ArrayList<>(count);
    List<MooValue> values = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      keys.addFirst(frame.operandStack.pop());
      values.addFirst(frame.operandStack.pop());
    }
    final MapValue map;
    try {
      map = mapFrom(keys, values);
    } catch (IllegalArgumentException error) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    frame.operandStack.push(map);
    frame.instructionPointer++;
  }

  private static MapValue mapFrom(List<MooValue> keys, List<MooValue> values) {
    MapValue map = new MapValue(Map.of());
    for (int index = 0; index < keys.size(); index++) {
      map = map.with(keys.get(index), values.get(index));
    }
    return map;
  }
}
