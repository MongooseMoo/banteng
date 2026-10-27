package world.mongoose.banteng.vm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.ValueSemantics;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import world.mongoose.banteng.vm.VmState.Frame;
import world.mongoose.banteng.vm.VmState.IndexContext;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldTxn;

/** Indexing, slicing, and indexed-assignment operations. */
final class IndexOps {
  private IndexOps() {}

  static void index(Frame frame, VmState state, WorldTxn world, int parentDepth) {
    IndexContext context = frame.indexCollections.pop();
    MooValue index = frame.operandStack.pop();
    MooValue collection = frame.operandStack.pop();
    if (collection instanceof WaifValue waif) {
      dispatchWaifIndexHandler(
          waif, ":_index", new ListValue(List.of(index)), frame, state, world);
      return;
    }
    if (collection instanceof ListValue list && index instanceof IntegerValue integer) {
      MooValue value = list.get(integer.value()).orElse(null);
      if (value == null) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return;
      }
      if (parentDepth == 1) {
        frame.indexCollections.push(
            new IndexContext(collection, Optional.of(index), context.operandDepth()));
      }
      frame.operandStack.push(value);
      frame.instructionPointer++;
      return;
    }
    if (collection instanceof StringValue string && index instanceof IntegerValue integer) {
      if (integer.value() < 1 || integer.value() > string.length()) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return;
      }
      if (parentDepth == 1) {
        frame.indexCollections.push(
            new IndexContext(collection, Optional.of(index), context.operandDepth()));
      }
      byte[] bytes = string.bytes();
      frame.operandStack.push(
          StringValue.of(new byte[] {bytes[Math.toIntExact(integer.value() - 1)]}));
      frame.instructionPointer++;
      return;
    }
    if (collection instanceof MapValue map) {
      MooValue value;
      try {
        value = map.get(index).orElse(null);
      } catch (IllegalArgumentException error) {
        ErrorOps.raise(state, ErrorValue.E_TYPE, world);
        return;
      }
      if (value == null) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return;
      }
      if (parentDepth == 1) {
        frame.indexCollections.push(
            new IndexContext(collection, Optional.of(index), context.operandDepth()));
      }
      frame.operandStack.push(value);
      frame.instructionPointer++;
      return;
    }
    ErrorOps.raise(state, ErrorValue.E_TYPE, world);
  }

  private static int mapKeyPosition(
      List<Map.Entry<MooValue, MooValue>> entries, MooValue requested) {
    for (int index = 0; index < entries.size(); index++) {
      if (ValueSemantics.STANDARD.compare(entries.get(index).getKey(), requested, false) == 0) return index;
    }
    return -1;
  }

  static void boundaryIndex(
      Frame frame, VmState state, WorldTxn world, boolean last) {
    MooValue collection = frame.indexCollections.getFirst().collection();
    if (collection instanceof MapValue map) {
      var keys = map.orderedEntries().stream().map(Map.Entry::getKey).iterator();
      if (!keys.hasNext()) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return;
      }
      MooValue boundary = keys.next();
      if (last) {
        while (keys.hasNext()) {
          boundary = keys.next();
        }
      }
      frame.operandStack.push(boundary);
      frame.instructionPointer++;
      return;
    }
    int size;
    if (collection instanceof ListValue list) {
      size = list.size();
    } else if (collection instanceof StringValue string) {
      size = string.length();
    } else {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    frame.operandStack.push(new IntegerValue(last || size == 0 ? size : 1));
    frame.instructionPointer++;
  }

  static void range(Frame frame, VmState state, WorldTxn world) {
    frame.indexCollections.pop();
    MooValue end = frame.operandStack.pop();
    MooValue start = frame.operandStack.pop();
    MooValue collection = frame.operandStack.pop();
    if (collection instanceof StringValue string
        && start instanceof IntegerValue first
        && end instanceof IntegerValue last) {
      byte[] bytes = string.bytes();
      if (last.value() < first.value()) {
        frame.operandStack.push(StringValue.of(new byte[0]));
        frame.instructionPointer++;
        return;
      }
      if (first.value() < 1 || last.value() > bytes.length) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return;
      }
      frame.operandStack.push(
          StringValue.of(
              Arrays.copyOfRange(
                  bytes, Math.toIntExact(first.value() - 1), Math.toIntExact(last.value()))));
      frame.instructionPointer++;
      return;
    }
    if (collection instanceof MapValue map) {
      if (start instanceof ListValue
          || start instanceof MapValue
          || end instanceof ListValue
          || end instanceof MapValue) {
        ErrorOps.raise(state, ErrorValue.E_TYPE, world);
        return;
      }
      if (start instanceof IntegerValue first
          && end instanceof IntegerValue last
          && last.value() < first.value()) {
        frame.operandStack.push(new MapValue(Map.of()));
        frame.instructionPointer++;
        return;
      }
      List<Map.Entry<MooValue, MooValue>> entries = map.orderedEntries();
      int firstPosition = mapKeyPosition(entries, start);
      int lastPosition = mapKeyPosition(entries, end);
      if (firstPosition < 0 || lastPosition < 0) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return;
      }
      MapValue selected = new MapValue(Map.of());
      for (int position = firstPosition; position <= lastPosition; position++) {
        Map.Entry<MooValue, MooValue> entry = entries.get(position);
        selected = selected.with(entry.getKey(), entry.getValue());
      }
      frame.operandStack.push(selected);
      frame.instructionPointer++;
      return;
    }
    if (!(collection instanceof ListValue list)
        || !(start instanceof IntegerValue first)
        || !(end instanceof IntegerValue last)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    if (last.value() < first.value()) {
      frame.operandStack.push(new ListValue(List.of()));
      frame.instructionPointer++;
      return;
    }
    if (first.value() < 1 || last.value() > list.size()) {
      ErrorOps.raise(state, ErrorValue.E_RANGE, world);
      return;
    }
    frame.operandStack.push(
        new ListValue(
            list.elements().subList(Math.toIntExact(first.value() - 1), Math.toIntExact(last.value()))));
    frame.instructionPointer++;
  }

  static void setIndexedLocal(
      Frame frame, VmState state, WorldTxn world, String owner, int parentDepth) {
    frame.indexCollections.pop();
    MooValue value = frame.operandStack.pop();
    MooValue key = frame.operandStack.pop();
    MooValue collection = frame.operandStack.pop();
    if (collection instanceof WaifValue waif) {
      dispatchWaifIndexHandler(
          waif, ":_set_index", new ListValue(List.of(key, value)), frame, state, world);
      return;
    }
    Optional<MooValue> replacement = replaceIndex(collection, key, value, state, world);
    if (replacement.isEmpty()) {
      return;
    }
    replacement = replaceParents(frame, replacement.orElseThrow(), parentDepth, state, world);
    if (replacement.isEmpty()) {
      return;
    }
    frame.locals.put(PropertyOps.normalize(owner), replacement.orElseThrow());
    frame.operandStack.push(value);
    frame.instructionPointer++;
  }

  private static Optional<MooValue> replaceParents(
      Frame frame, MooValue updatedCollection, int parentDepth, VmState state, WorldTxn world) {
    for (int depth = 0; depth < parentDepth; depth++) {
      IndexContext parentContext = frame.indexCollections.pop();
      MooValue parentKey = parentContext.key().orElseThrow();
      MooValue parent = parentContext.collection();
      Optional<MooValue> replacement =
          replaceIndex(parent, parentKey, updatedCollection, state, world);
      if (replacement.isEmpty()) {
        return Optional.empty();
      }
      updatedCollection = replacement.orElseThrow();
    }
    return Optional.of(updatedCollection);
  }

  private static void dispatchWaifIndexHandler(
      WaifValue waif,
      String verbName,
      ListValue arguments,
      Frame frame,
      VmState state,
      WorldTxn world) {
    WorldObject waifClass = world.object(waif.classObject().value()).orElse(null);
    if (waifClass == null) {
      ErrorOps.raise(state, ErrorValue.E_INVIND, world);
      return;
    }
    if (!world.isWizard(waifClass.owner())) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    WorldTxn.ResolvedVerb verb =
        world.resolveVerb(waif.classObject().value(), verbName, true).orElse(null);
    if (verb == null) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    frame.instructionPointer++;
    if (!MooVm.callVerb(state, waif, verb, arguments, verbName, MooVm.VerbCallKind.NORMAL,
        Optional.empty(), Optional.empty(), Optional.empty())) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
    }
  }

  private static Optional<MooValue> replaceIndex(
      MooValue collection,
      MooValue key,
      MooValue value,
      VmState state,
      WorldTxn world) {
    if (collection instanceof MapValue map) {
      try {
        return Optional.of(map.with(key, value));
      } catch (IllegalArgumentException error) {
        ErrorOps.raise(state, ErrorValue.E_TYPE, world);
        return Optional.empty();
      }
    }
    if (collection instanceof ListValue list && key instanceof IntegerValue index) {
      if (index.value() < 1 || index.value() > list.size()) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return Optional.empty();
      }
      List<MooValue> replaced = new ArrayList<>(list.elements());
      replaced.set(Math.toIntExact(index.value() - 1), value);
      return Optional.of(new ListValue(replaced));
    }
    if (collection instanceof StringValue string
        && key instanceof IntegerValue index
        && value instanceof StringValue replacement) {
      if (index.value() < 1 || index.value() > string.length()) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return Optional.empty();
      }
      if (replacement.length() != 1) {
        ErrorOps.raise(state, ErrorValue.E_INVARG, world);
        return Optional.empty();
      }
      byte[] replaced = string.bytes();
      replaced[Math.toIntExact(index.value() - 1)] = replacement.bytes()[0];
      return Optional.of(StringValue.of(replaced));
    }
    ErrorOps.raise(state, ErrorValue.E_TYPE, world);
    return Optional.empty();
  }

  static void setIndexedProperty(
      Frame frame, VmState state, WorldTxn world, int parentDepth) {
    frame.indexCollections.pop();
    MooValue value = frame.operandStack.pop();
    MooValue key = frame.operandStack.pop();
    MooValue collection = frame.operandStack.pop();
    Optional<MooValue> replacement = replaceIndex(collection, key, value, state, world);
    if (replacement.isEmpty()) {
      return;
    }
    replacement = replaceParents(frame, replacement.orElseThrow(), parentDepth, state, world);
    if (replacement.isEmpty()) {
      return;
    }
    frame.operandStack.push(replacement.orElseThrow());
    int instructionPointer = frame.instructionPointer;
    PropertyOps.setProperty(frame, state, world);
    if (frame.instructionPointer == instructionPointer + 1) {
      frame.operandStack.pop();
      frame.operandStack.push(value);
    }
  }

  static void setRange(
      Frame frame, VmState state, WorldTxn world, Optional<String> owner, int parentDepth) {
    frame.indexCollections.pop();
    MooValue value = frame.operandStack.pop();
    MooValue end = frame.operandStack.pop();
    MooValue start = frame.operandStack.pop();
    MooValue collection = frame.operandStack.pop();
    MooValue updatedCollection;
    if ((collection instanceof StringValue && value instanceof StringValue)
        || (collection instanceof ListValue && value instanceof ListValue)) {
      if (!(start instanceof IntegerValue first) || !(end instanceof IntegerValue last)) {
        ErrorOps.raise(state, ErrorValue.E_TYPE, world);
        return;
      }
      int length = collection instanceof StringValue string ? string.length() : ((ListValue) collection).size();
      if (first.value() > length + 1L || last.value() < 0) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return;
      }
      int prefix = first.value() <= 1 ? 0 : Math.toIntExact(first.value() - 1);
      int suffix = Math.toIntExact(Math.min(length, last.value()));
      if (collection instanceof StringValue string && value instanceof StringValue replacement) {
        byte[] original = string.bytes();
        byte[] inserted = replacement.bytes();
        long size = (long) prefix + inserted.length + length - suffix;
        Optional<BuiltinResult> error = BuiltinCatalog.stringLengthError(size, world);
        if (error.isPresent()) {
          if (error.orElseThrow() instanceof BuiltinResult.ErrorResult quota) {
            ErrorOps.raise(state, quota.error(), world);
          } else {
            state.abortSecondsExhaustion();
          }
          return;
        }
        byte[] replaced = new byte[Math.toIntExact(size)];
        System.arraycopy(original, 0, replaced, 0, prefix);
        System.arraycopy(inserted, 0, replaced, prefix, inserted.length);
        System.arraycopy(original, suffix, replaced, prefix + inserted.length, length - suffix);
        updatedCollection = StringValue.of(replaced);
      } else {
        ListValue list = (ListValue) collection;
        ListValue replacement = (ListValue) value;
        List<MooValue> replaced = new ArrayList<>(list.elements().subList(0, prefix));
        replaced.addAll(replacement.elements());
        replaced.addAll(list.elements().subList(suffix, length));
        updatedCollection = new ListValue(replaced);
      }
    } else if (collection instanceof MapValue map && value instanceof MapValue replacement) {
      if (start instanceof ListValue
          || start instanceof MapValue
          || end instanceof ListValue
          || end instanceof MapValue) {
        ErrorOps.raise(state, ErrorValue.E_TYPE, world);
        return;
      }
      List<Map.Entry<MooValue, MooValue>> entries = map.orderedEntries();
      int firstPosition = mapKeyPosition(entries, start);
      int lastPosition = mapKeyPosition(entries, end);
      if (firstPosition < 0 || lastPosition < 0) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return;
      }
      MapValue replaced = new MapValue(Map.of());
      for (int position = 0; position < firstPosition; position++) {
        Map.Entry<MooValue, MooValue> entry = entries.get(position);
        replaced = replaced.with(entry.getKey(), entry.getValue());
      }
      for (Map.Entry<MooValue, MooValue> entry : replacement.orderedEntries()) {
        replaced = replaced.with(entry.getKey(), entry.getValue());
      }
      for (int position = lastPosition + 1; position < entries.size(); position++) {
        Map.Entry<MooValue, MooValue> entry = entries.get(position);
        replaced = replaced.with(entry.getKey(), entry.getValue());
      }
      updatedCollection = replaced;
    } else {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    Optional<MooValue> replacement =
        replaceParents(frame, updatedCollection, parentDepth, state, world);
    if (replacement.isEmpty()) {
      return;
    }
    updatedCollection = replacement.orElseThrow();
    if (owner.isEmpty()) {
      frame.operandStack.push(updatedCollection);
      int instructionPointer = frame.instructionPointer;
      PropertyOps.setProperty(frame, state, world);
      if (frame.instructionPointer == instructionPointer + 1) {
        frame.operandStack.pop();
        frame.operandStack.push(value);
      }
      return;
    }
    frame.locals.put(PropertyOps.normalize(owner.orElseThrow()), updatedCollection);
    frame.operandStack.push(value);
    frame.instructionPointer++;
  }

}
