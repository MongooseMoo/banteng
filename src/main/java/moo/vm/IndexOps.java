package moo.vm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.BytecodeProgram.Instruction;
import moo.bytecode.MooCompiler;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;
import moo.vm.VmState.Frame;
import moo.vm.VmState.IndexContext;
import moo.world.ObjectFlags;
import moo.world.WorldObject;
import moo.world.WorldTxn;
import moo.world.WorldVerb;

/** Indexing, slicing, and indexed-assignment operations. */
final class IndexOps {
  enum Operation {
    ENTER_INDEX,
    INDEX,
    RANGE,
    FIRST,
    LAST,
    SET_INDEX_LOCAL,
    SET_INDEX_PROPERTY,
    SET_RANGE_LOCAL
  }

  private IndexOps() {}

  static void execute(
      Operation operation, VmState state, Instruction instruction, Frame frame, WorldTxn world) {
    switch (operation) {
      case ENTER_INDEX -> {
        frame.indexCollections.push(
            new IndexContext(
                frame.operandStack.getFirst(), Optional.empty(), frame.operandStack.size()));
        frame.instructionPointer++;
      }
      case INDEX ->
          index(frame, state, world, Math.toIntExact(instruction.operand().orElse(0)));
      case RANGE -> range(frame, state, world);
      case FIRST -> boundaryIndex(frame, state, world, false);
      case LAST -> boundaryIndex(frame, state, world, true);
      case SET_INDEX_LOCAL ->
          setIndexedLocal(
              frame,
              state,
              world,
              instruction.text().orElseThrow(),
              Math.toIntExact(instruction.operand().orElse(0)));
      case SET_INDEX_PROPERTY -> setIndexedProperty(frame, state, world);
      case SET_RANGE_LOCAL ->
          setRangeLocal(
              frame,
              state,
              world,
              instruction.text().orElseThrow(),
              Math.toIntExact(instruction.operand().orElse(0)));
    }
  }

  private static void index(Frame frame, VmState state, WorldTxn world, int parentDepth) {
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

  private static void boundaryIndex(
      Frame frame, VmState state, WorldTxn world, boolean last) {
    MooValue collection = frame.indexCollections.getFirst().collection();
    if (collection instanceof MapValue map) {
      var keys = map.entries().keySet().iterator();
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
    if (size == 0) {
      ErrorOps.raise(state, ErrorValue.E_RANGE, world);
      return;
    }
    frame.operandStack.push(new IntegerValue(last ? size : 1));
    frame.instructionPointer++;
  }

  private static void range(Frame frame, VmState state, WorldTxn world) {
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
      List<MooValue> keys = new ArrayList<>(map.entries().keySet());
      int firstPosition = keys.indexOf(start);
      int lastPosition = keys.indexOf(end);
      if (firstPosition < 0 || lastPosition < 0) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return;
      }
      Map<MooValue, MooValue> selected = new LinkedHashMap<>();
      for (int position = firstPosition; position <= lastPosition; position++) {
        MooValue key = keys.get(position);
        selected.put(key, map.entries().get(key));
      }
      frame.operandStack.push(new MapValue(selected));
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

  private static void setIndexedLocal(
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
    MooValue updatedCollection = replacement.orElseThrow();

    for (int depth = 0; depth < parentDepth; depth++) {
      IndexContext parentContext = frame.indexCollections.pop();
      MooValue parentKey = parentContext.key().orElseThrow();
      MooValue parent = parentContext.collection();
      replacement = replaceIndex(parent, parentKey, updatedCollection, state, world);
      if (replacement.isEmpty()) {
        return;
      }
      updatedCollection = replacement.orElseThrow();
    }
    frame.locals.put(PropertyOps.normalize(owner), updatedCollection);
    frame.operandStack.push(value);
    frame.instructionPointer++;
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
    WorldObject classOwner = world.object(waifClass.owner()).orElse(null);
    if (classOwner == null || !ObjectFlags.isWizard(classOwner.flags())) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    WorldVerb verb = world.verb(waif.classObject().value(), verbName).orElse(null);
    OptionalLong location = world.verbLocation(waif.classObject().value(), verbName, true);
    if (verb == null || location.isEmpty()) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    BytecodeProgram program;
    try {
      program = new MooCompiler().compile(verb.programSource());
    } catch (IllegalArgumentException error) {
      ErrorOps.raise(state, ErrorValue.E_INVARG, world);
      return;
    }
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("this", waif);
    locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
    locals.put("caller", frame.receiver);
    locals.put("verb", StringValue.of(verbName));
    locals.put("args", arguments);
    locals.put("argstr", frame.locals.getOrDefault("argstr", StringValue.of("")));
    locals.put("dobj", frame.locals.getOrDefault("dobj", new ObjectValue(-1)));
    locals.put("dobjstr", frame.locals.getOrDefault("dobjstr", StringValue.of("")));
    locals.put("prepstr", frame.locals.getOrDefault("prepstr", StringValue.of("")));
    locals.put("iobj", frame.locals.getOrDefault("iobj", new ObjectValue(-1)));
    locals.put("iobjstr", frame.locals.getOrDefault("iobjstr", StringValue.of("")));
    frame.instructionPointer++;
    if (!state.pushVerbFrame(
        program,
        locals,
        verb.owner(),
        waif,
        new ObjectValue(location.orElseThrow()),
        OptionalLong.empty(),
        OptionalLong.empty(),
        OptionalLong.empty(),
        verbName,
        verb.names(),
        (verb.permissions() & 8) != 0)) {
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
        && value instanceof StringValue replacement
        && replacement.length() == 1) {
      if (index.value() < 1 || index.value() > string.length()) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return Optional.empty();
      }
      byte[] replaced = string.bytes();
      replaced[Math.toIntExact(index.value() - 1)] = replacement.bytes()[0];
      return Optional.of(StringValue.of(replaced));
    }
    ErrorOps.raise(state, ErrorValue.E_TYPE, world);
    return Optional.empty();
  }

  private static void setIndexedProperty(Frame frame, VmState state, WorldTxn world) {
    frame.indexCollections.pop();
    MooValue value = frame.operandStack.pop();
    MooValue key = frame.operandStack.pop();
    MooValue collection = frame.operandStack.pop();
    MooValue updatedCollection;
    if (collection instanceof MapValue map) {
      try {
        updatedCollection = map.with(key, value);
      } catch (IllegalArgumentException error) {
        ErrorOps.raise(state, ErrorValue.E_TYPE, world);
        return;
      }
    } else if (collection instanceof ListValue list && key instanceof IntegerValue index) {
      if (index.value() < 1 || index.value() > list.size()) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return;
      }
      List<MooValue> replaced = new ArrayList<>(list.elements());
      replaced.set(Math.toIntExact(index.value() - 1), value);
      updatedCollection = new ListValue(replaced);
    } else if (collection instanceof StringValue string
        && key instanceof IntegerValue index
        && value instanceof StringValue replacement
        && replacement.length() == 1) {
      if (index.value() < 1 || index.value() > string.length()) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return;
      }
      byte[] replaced = string.bytes();
      replaced[Math.toIntExact(index.value() - 1)] = replacement.bytes()[0];
      updatedCollection = StringValue.of(replaced);
    } else {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    frame.operandStack.push(updatedCollection);
    int instructionPointer = frame.instructionPointer;
    setProperty(frame, state, world);
    if (frame.instructionPointer == instructionPointer + 1) {
      frame.operandStack.pop();
      frame.operandStack.push(value);
    }
  }

  private static void setRangeLocal(
      Frame frame, VmState state, WorldTxn world, String owner, int parentDepth) {
    frame.indexCollections.pop();
    MooValue value = frame.operandStack.pop();
    MooValue end = frame.operandStack.pop();
    MooValue start = frame.operandStack.pop();
    MooValue collection = frame.operandStack.pop();
    MooValue updatedCollection;
    if (collection instanceof StringValue string && value instanceof StringValue replacement) {
      if (!(start instanceof IntegerValue first) || !(end instanceof IntegerValue last)) {
        ErrorOps.raise(state, ErrorValue.E_TYPE, world);
        return;
      }
      if (last.value() == first.value() - 1
          && first.value() >= 1
          && first.value() <= string.length() + 1L) {
        byte[] original = string.bytes();
        byte[] inserted = replacement.bytes();
        int insertionPoint = Math.toIntExact(first.value() - 1);
        byte[] replaced = new byte[original.length + inserted.length];
        System.arraycopy(original, 0, replaced, 0, insertionPoint);
        System.arraycopy(inserted, 0, replaced, insertionPoint, inserted.length);
        System.arraycopy(
            original,
            insertionPoint,
            replaced,
            insertionPoint + inserted.length,
            original.length - insertionPoint);
        updatedCollection = StringValue.of(replaced);
      } else if (last.value() < first.value()
          && first.value() >= 1
          && first.value() <= string.length()
          && last.value() >= 1
          && last.value() <= string.length()) {
        byte[] original = string.bytes();
        byte[] inserted = replacement.bytes();
        int prefixLength = Math.toIntExact(first.value() - 1);
        int suffixStart = Math.toIntExact(last.value());
        byte[] replaced = new byte[prefixLength + inserted.length + original.length - suffixStart];
        System.arraycopy(original, 0, replaced, 0, prefixLength);
        System.arraycopy(inserted, 0, replaced, prefixLength, inserted.length);
        System.arraycopy(
            original,
            suffixStart,
            replaced,
            prefixLength + inserted.length,
            original.length - suffixStart);
        updatedCollection = StringValue.of(replaced);
      } else if (first.value() == string.length() + 1L && last.value() >= first.value()) {
        byte[] original = string.bytes();
        byte[] inserted = replacement.bytes();
        byte[] appended = Arrays.copyOf(original, original.length + inserted.length);
        System.arraycopy(inserted, 0, appended, original.length, inserted.length);
        updatedCollection = StringValue.of(appended);
      } else {
        if (first.value() < 1 || last.value() < first.value() || last.value() > string.length()) {
          ErrorOps.raise(state, ErrorValue.E_RANGE, world);
          return;
        }
        byte[] original = string.bytes();
        byte[] inserted = replacement.bytes();
        int prefixLength = Math.toIntExact(first.value() - 1);
        int suffixStart = Math.toIntExact(last.value());
        byte[] replaced = new byte[prefixLength + inserted.length + original.length - suffixStart];
        System.arraycopy(original, 0, replaced, 0, prefixLength);
        System.arraycopy(inserted, 0, replaced, prefixLength, inserted.length);
        System.arraycopy(
            original,
            suffixStart,
            replaced,
            prefixLength + inserted.length,
            original.length - suffixStart);
        updatedCollection = StringValue.of(replaced);
      }
    } else if (collection instanceof ListValue list && value instanceof ListValue replacement) {
      if (!(start instanceof IntegerValue first) || !(end instanceof IntegerValue last)) {
        ErrorOps.raise(state, ErrorValue.E_TYPE, world);
        return;
      }
      if (last.value() == first.value() - 1
          && first.value() >= 1
          && first.value() <= list.size() + 1L) {
        int insertionPoint = Math.toIntExact(first.value() - 1);
        List<MooValue> inserted = new ArrayList<>();
        inserted.addAll(list.elements().subList(0, insertionPoint));
        inserted.addAll(replacement.elements());
        inserted.addAll(list.elements().subList(insertionPoint, list.size()));
        updatedCollection = new ListValue(inserted);
      } else if (last.value() < first.value()
          && first.value() >= 1
          && first.value() <= list.size()
          && last.value() >= 1
          && last.value() <= list.size()) {
        List<MooValue> replaced = new ArrayList<>();
        replaced.addAll(list.elements().subList(0, Math.toIntExact(first.value() - 1)));
        replaced.addAll(replacement.elements());
        replaced.addAll(list.elements().subList(Math.toIntExact(last.value()), list.size()));
        updatedCollection = new ListValue(replaced);
      } else {
        if (first.value() < 1 || last.value() < first.value() || last.value() > list.size()) {
          ErrorOps.raise(state, ErrorValue.E_RANGE, world);
          return;
        }
        List<MooValue> replaced = new ArrayList<>();
        replaced.addAll(list.elements().subList(0, Math.toIntExact(first.value() - 1)));
        replaced.addAll(replacement.elements());
        replaced.addAll(list.elements().subList(Math.toIntExact(last.value()), list.size()));
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
      List<MooValue> keys = new ArrayList<>(map.entries().keySet());
      int firstPosition = keys.indexOf(start);
      int lastPosition = keys.indexOf(end);
      if (firstPosition < 0 || lastPosition < 0) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return;
      }
      Map<MooValue, MooValue> replaced = new LinkedHashMap<>();
      if (lastPosition < firstPosition) {
        replaced.putAll(map.entries());
        replaced.putAll(replacement.entries());
      } else {
        for (int position = 0; position < firstPosition; position++) {
          MooValue key = keys.get(position);
          replaced.put(key, map.entries().get(key));
        }
        replaced.putAll(replacement.entries());
        for (int position = lastPosition + 1; position < keys.size(); position++) {
          MooValue key = keys.get(position);
          replaced.put(key, map.entries().get(key));
        }
      }
      updatedCollection = new MapValue(replaced);
    } else {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    if (parentDepth == 0) {
      frame.locals.put(PropertyOps.normalize(owner), updatedCollection);
    } else {
      IndexContext parentContext = frame.indexCollections.pop();
      MooValue parentKey = parentContext.key().orElseThrow();
      MooValue parent = parentContext.collection();
      if (!(parent instanceof ListValue list) || !(parentKey instanceof IntegerValue index)) {
        ErrorOps.raise(state, ErrorValue.E_TYPE, world);
        return;
      }
      if (index.value() < 1 || index.value() > list.size()) {
        ErrorOps.raise(state, ErrorValue.E_RANGE, world);
        return;
      }
      List<MooValue> replaced = new ArrayList<>(list.elements());
      replaced.set(Math.toIntExact(index.value() - 1), updatedCollection);
      frame.locals.put(PropertyOps.normalize(owner), new ListValue(replaced));
    }
    frame.operandStack.push(value);
    frame.instructionPointer++;
  }

}
