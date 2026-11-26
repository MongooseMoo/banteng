package world.mongoose.banteng.vm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.ValueLimits;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.vm.VmState.Frame;
import world.mongoose.banteng.world.WorldTxn;

/** List and map construction operations. */
final class ListOps {
  private ListOps() {}

  static void buildList(Frame frame, int count) {
    List<MooValue> elements = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      elements.addFirst(frame.operandStack.pop());
    }
    frame.operandStack.push(new ListValue(elements));
    frame.instructionPointer++;
  }

  static void appendList(Frame frame, VmState state, WorldTxn world, boolean first) {
    MooValue value = frame.operandStack.pop();
    if (first) {
      frame.operandStack.push(new ListValue(List.of(value)));
      frame.instructionPointer++;
      return;
    }
    if (!(frame.operandStack.pop() instanceof ListValue list)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    pushCheckedList(frame, state, world, list.append(value));
  }

  static void extendList(Frame frame, VmState state, WorldTxn world, boolean first) {
    MooValue extension = frame.operandStack.pop();
    if (!(extension instanceof ListValue appended)) {
      if (!first) frame.operandStack.pop();
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    if (first) {
      frame.operandStack.push(appended);
      frame.instructionPointer++;
      return;
    }
    if (!(frame.operandStack.pop() instanceof ListValue list)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    pushCheckedList(frame, state, world, list.concatenate(appended));
  }

  private static void pushCheckedList(
      Frame frame, VmState state, WorldTxn world, ListValue value) {
    BuiltinResult checked = ValueLimits.enforceListValueLimit(value, world);
    if (checked instanceof BuiltinResult.Value checkedValue) {
      frame.operandStack.push(checkedValue.value());
      frame.instructionPointer++;
      return;
    }
    MooVm.applyBuiltinResult(checked, frame, state, world);
  }

  static void buildMap(Frame frame, VmState state, WorldTxn world, int count) {
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
