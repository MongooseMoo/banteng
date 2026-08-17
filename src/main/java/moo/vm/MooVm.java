package moo.vm;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import moo.builtin.BuiltinCatalog;
import moo.builtin.BuiltinCatalog.ConnectionOptionRequest;
import moo.builtin.BuiltinCatalog.ForcedInputRequest;
import moo.builtin.BuiltinResult;
import moo.builtin.BuiltinSpec;
import moo.builtin.CheckpointRequest;
import moo.builtin.EffectClass;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.BytecodeProgram.Instruction;
import moo.bytecode.MooCompiler;
import moo.value.MooValue;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.BooleanValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;
import moo.vm.VmState.ActiveHandler;
import moo.vm.VmState.CollectionCursor;
import moo.vm.VmState.Frame;
import moo.vm.VmState.HandlerPhase;
import moo.vm.VmState.IndexContext;
import moo.vm.VmState.LoopCursor;
import moo.vm.VmState.RangeCursor;
import moo.world.WorldObject;
import moo.world.WorldAnonymousObject;
import moo.world.WorldProperty;
import moo.world.WorldTxn;
import moo.world.WorldVerb;

/** Iterative executor for the authorized explicit bytecode state. */
public final class MooVm {
  /** Executes a pure program for package-level VM tests without publishing world state or effects. */
  void execute(BytecodeProgram program, VmState state) {
    WorldTxn root = new WorldTxn(List.of(), List.of());
    try (WorldTxn transaction = root.begin()) {
      execute(program, state, transaction, new BuiltinCatalog(), 0L);
      if (state.outcome() == VmState.Outcome.PENDING_BUILTIN) {
        throw new IllegalStateException("pure VM execution reached an irrevocable builtin");
      }
    }
  }

  /** Executes with the one concrete world transaction, builtin catalog, and scheduler task ID. */
  public void execute(
      BytecodeProgram program,
      VmState state,
      WorldTxn world,
      BuiltinCatalog builtins,
      long taskId) {
    state.beginSegment();
    state.ensureRoot(program);
    while (state.outcome() == VmState.Outcome.RUNNING) {
      Frame frame = state.currentFrame();
      if (frame.instructionPointer >= frame.program.instructions().size()) {
        routeReturn(state, new IntegerValue(0), world);
        continue;
      }
      executeInstruction(
          frame.program.instructions().get(frame.instructionPointer),
          state,
          world,
          builtins,
          taskId);
    }
  }

  private static void executeInstruction(
      Instruction instruction,
      VmState state,
      WorldTxn world,
      BuiltinCatalog builtins,
      long taskId) {
    Frame frame = state.currentFrame();
    if (isCountedInstruction(instruction, frame)) {
      state.decrementRemainingTicks();
      if (state.remainingTicks() == 0) {
        state.abortTickExhaustion();
        return;
      }
    }
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
      case LIST_APPEND -> appendList(frame, state, world);
      case LIST_EXTEND -> extendList(frame, state, world);
      case BUILD_MAP ->
          buildMap(frame, state, world, Math.toIntExact(instruction.operand().orElseThrow()));
      case LOAD_LOCAL -> loadLocal(frame, instruction.text().orElseThrow(), state, world);
      case STORE_LOCAL -> {
        frame.locals.put(normalize(instruction.text().orElseThrow()), frame.operandStack.pop());
        frame.instructionPointer++;
      }
      case DUP -> {
        frame.operandStack.push(frame.operandStack.getFirst());
        frame.instructionPointer++;
      }
      case DUP_PAIR -> {
        MooValue first = frame.operandStack.removeFirst();
        MooValue second = frame.operandStack.getFirst();
        frame.operandStack.addFirst(first);
        frame.operandStack.push(second);
        frame.operandStack.push(first);
        frame.instructionPointer++;
      }
      case POP -> {
        frame.operandStack.pop();
        frame.instructionPointer++;
      }
      case GET_PROPERTY -> getProperty(frame, state, world);
      case SET_PROPERTY -> setProperty(frame, state, world);
      case ENTER_INDEX -> {
        frame.indexCollections.push(
            new IndexContext(
                frame.operandStack.getFirst(), Optional.empty(), frame.operandStack.size()));
        frame.instructionPointer++;
      }
      case INDEX ->
          index(
              frame,
              state,
              world,
              Math.toIntExact(instruction.operand().orElse(0)));
      case RANGE -> range(frame, state, world);
      case FIRST -> firstIndex(frame, state, world);
      case LAST -> lastIndex(frame, state, world);
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
      case CALL -> {
        String callName = instruction.text().orElseThrow();
        if (!callName.equalsIgnoreCase("pass")) {
          invokeBuiltin(instruction, frame, state, world, builtins, taskId);
        } else {
          MooValue argumentValue = frame.operandStack.pop();
          MooValue thisValue = frame.locals.get("this");
          MooValue verbValue = frame.locals.get("verb");
          if (!(argumentValue instanceof ListValue arguments)
              || thisValue == null
              || (!(thisValue instanceof ObjectValue)
                  && !(thisValue instanceof AnonymousObjectValue)
                  && primitivePrototypeProperty(thisValue).isEmpty())
              || !(verbValue instanceof StringValue verbNameValue)) {
            raiseError(state, ErrorValue.E_TYPE, world);
            return;
          }

          String verbName = new String(verbNameValue.bytes(), StandardCharsets.ISO_8859_1);
          MooValue receiver = thisValue;
          List<Long> directParents;
          if (frame.verbLocation instanceof ObjectValue currentLocation) {
            WorldObject location = world.object(currentLocation.value()).orElse(null);
            if (location == null) {
              raiseError(state, ErrorValue.E_INVIND, world);
              return;
            }
            directParents = location.parents();
          } else if (frame.verbLocation instanceof AnonymousObjectValue currentLocation) {
            WorldAnonymousObject location = world.anonymousObject(currentLocation).orElse(null);
            if (location == null) {
              raiseError(state, ErrorValue.E_INVIND, world);
              return;
            }
            directParents = location.parents();
          } else {
            raiseError(state, ErrorValue.E_INVIND, world);
            return;
          }
          if (directParents.isEmpty()) {
            raiseError(state, ErrorValue.E_INVIND, world);
            return;
          }
          WorldVerb target = null;
          long targetLocation = -1;
          for (long directParent : directParents) {
            OptionalLong location = world.verbLocation(directParent, verbName, true);
            if (location.isPresent()) {
              target = world.verb(directParent, verbName, true).orElseThrow();
              targetLocation = location.orElseThrow();
              break;
            }
          }
          if (target == null) {
            raiseError(state, ErrorValue.E_VERBNF, world);
            return;
          }
          BytecodeProgram targetProgram;
          try {
            targetProgram = new MooCompiler().compile(target.programSource());
          } catch (IllegalArgumentException error) {
            raiseError(state, ErrorValue.E_INVARG, world);
            return;
          }

          frame.instructionPointer++;
          Map<String, MooValue> locals = new LinkedHashMap<>(frame.locals);
          locals.put("caller", receiver);
          locals.put("args", arguments);
          if (!state.pushVerbFrame(
              targetProgram,
              locals,
              target.owner(),
              receiver,
              new ObjectValue(targetLocation),
              OptionalLong.empty(),
              OptionalLong.empty(),
              OptionalLong.empty(),
              (target.permissions() & 8) != 0)) {
            raiseError(state, ErrorValue.E_MAXREC, world, false);
            return;
          }
        }
      }
      case CALL_VERB -> {
        MooValue argumentsValue = frame.operandStack.pop();
        MooValue nameValue = frame.operandStack.pop();
        MooValue receiverValue = frame.operandStack.pop();
        if (!(argumentsValue instanceof ListValue arguments)
            || !(nameValue instanceof StringValue name)) {
          raiseError(state, ErrorValue.E_TYPE, world);
          return;
        }
        String verbName = new String(name.bytes(), StandardCharsets.ISO_8859_1);
        WorldVerb verb;
        MooValue definingLocation;
        String lookupName;
        if (receiverValue instanceof ObjectValue receiver) {
          lookupName = verbName;
          verb = world.verb(receiver.value(), lookupName).orElse(null);
          OptionalLong location = world.verbLocation(receiver.value(), lookupName, true);
          definingLocation = location.isPresent() ? new ObjectValue(location.orElseThrow()) : null;
        } else if (receiverValue instanceof AnonymousObjectValue anonymous) {
          lookupName = verbName;
          verb = world.verb(anonymous, lookupName, true).orElse(null);
          definingLocation = world.verbLocation(anonymous, lookupName, true).orElse(null);
        } else if (receiverValue instanceof WaifValue waif) {
          lookupName = verbName.startsWith(":") ? verbName : ":" + verbName;
          verb = world.verb(waif.classObject().value(), lookupName).orElse(null);
          OptionalLong location =
              world.verbLocation(waif.classObject().value(), lookupName, true);
          definingLocation = location.isPresent() ? new ObjectValue(location.orElseThrow()) : null;
        } else {
          Optional<String> prototypeProperty = primitivePrototypeProperty(receiverValue);
          MooValue prototypeValue =
              prototypeProperty
                  .flatMap(property -> world.readObjectProperty(0, property))
                  .orElse(null);
          if (!(prototypeValue instanceof ObjectValue prototype)) {
            raiseError(state, ErrorValue.E_TYPE, world);
            return;
          }
          lookupName = verbName;
          verb = world.verb(prototype.value(), lookupName).orElse(null);
          OptionalLong location = world.verbLocation(prototype.value(), lookupName, true);
          definingLocation = location.isPresent() ? new ObjectValue(location.orElseThrow()) : null;
        }
        if (verb == null || definingLocation == null) {
          raiseError(state, ErrorValue.E_VERBNF, world);
          return;
        }
        BytecodeProgram verbProgram;
        try {
          verbProgram = new MooCompiler().compile(verb.programSource());
        } catch (IllegalArgumentException error) {
          raiseError(state, ErrorValue.E_INVARG, world);
          return;
        }
        frame.instructionPointer++;
        Map<String, MooValue> locals = new LinkedHashMap<>();
        locals.put("this", receiverValue);
        locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
        locals.put("caller", frame.receiver);
        locals.put("verb", encode(lookupName));
        locals.put("args", arguments);
        locals.put("argstr", frame.locals.getOrDefault("argstr", encode("")));
        locals.put("dobj", frame.locals.getOrDefault("dobj", new ObjectValue(-1)));
        locals.put("dobjstr", frame.locals.getOrDefault("dobjstr", encode("")));
        locals.put("prepstr", frame.locals.getOrDefault("prepstr", encode("")));
        locals.put("iobj", frame.locals.getOrDefault("iobj", new ObjectValue(-1)));
        locals.put("iobjstr", frame.locals.getOrDefault("iobjstr", encode("")));
        if (!state.pushVerbFrame(
            verbProgram,
            locals,
            verb.owner(),
            receiverValue,
            definingLocation,
            OptionalLong.empty(),
            OptionalLong.empty(),
            OptionalLong.empty(),
            (verb.permissions() & 8) != 0)) {
          raiseError(state, ErrorValue.E_MAXREC, world, false);
          return;
        }
      }
      case NEGATE -> unaryNegate(frame, state, world);
      case NOT -> {
        MooValue value = frame.operandStack.pop();
        frame.operandStack.push(new IntegerValue(value.isTruthy() ? 0 : 1));
        frame.instructionPointer++;
      }
      case COMPLEMENT -> bitwiseComplement(frame, state, world);
      case ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, POWER ->
          arithmetic(instruction, frame, state, world);
      case BITOR, BITAND, BITXOR, BITSHL, BITSHR ->
          bitwise(instruction, frame, state, world);
      case EQUAL, NOT_EQUAL -> equality(instruction, frame);
      case LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL ->
          comparison(instruction, frame, state, world);
      case IN -> membership(frame, state, world);
      case FORK -> fork(instruction, frame, state, world);
      case JUMP -> frame.instructionPointer = target(instruction);
      case JUMP_IF_FALSE -> conditionalJump(instruction, frame, false);
      case JUMP_IF_TRUE -> conditionalJump(instruction, frame, true);
      case ENTER_HANDLER -> {
        frame.handlers.push(
            new ActiveHandler(instruction.handler().orElseThrow(), frame.operandStack.size()));
        frame.instructionPointer++;
      }
      case LEAVE_HANDLER -> leaveHandler(frame);
      case END_FINALLY -> endFinally(state, world);
      case ITERATE -> iterate(instruction, frame, state, world);
      case ITERATE_RANGE -> iterateRange(instruction, frame, state, world);
      case LEAVE_LOOP -> {
        frame.loops.remove(Math.toIntExact(instruction.operand().orElseThrow()));
        frame.instructionPointer++;
      }
      case SCATTER -> scatter(instruction, frame, state, world);
      case RETURN -> {
        MooValue value = frame.operandStack.pop();
        frame.instructionPointer++;
        routeReturn(state, value, world);
      }
    }
  }

  private static boolean isCountedInstruction(Instruction instruction, Frame frame) {
    return switch (instruction.opcode()) {
      case LIST_APPEND -> {
        var operands = frame.operandStack.iterator();
        if (operands.hasNext()) {
          operands.next();
        }
        yield operands.hasNext()
            && operands.next() instanceof ListValue list
            && list.elements().isEmpty();
      }
      case LIST_EXTEND,
          STORE_LOCAL,
          GET_PROPERTY,
          SET_PROPERTY,
          INDEX,
          RANGE,
          FIRST,
          LAST,
          SET_INDEX_LOCAL,
          SET_INDEX_PROPERTY,
          SET_RANGE_LOCAL,
          CALL,
          CALL_VERB,
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
          IN,
          FORK,
          JUMP_IF_FALSE,
          JUMP_IF_TRUE,
          ENTER_HANDLER,
          ITERATE,
          ITERATE_RANGE,
          SCATTER ->
          true;
      default -> false;
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
      raiseError(state, ErrorValue.E_TYPE, world);
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
      raiseError(state, ErrorValue.E_TYPE, world);
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
    applyBuiltinResult(checked, frame, state, world);
  }

  private static void buildMap(Frame frame, VmState state, WorldTxn world, int count) {
    List<MooValue> keys = new ArrayList<>(count);
    List<MooValue> values = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      keys.addFirst(frame.operandStack.pop());
      values.addFirst(frame.operandStack.pop());
    }
    try {
      MapValue map = new MapValue(Map.of());
      for (int index = 0; index < count; index++) {
        map = map.with(keys.get(index), values.get(index));
      }
      frame.operandStack.push(map);
      frame.instructionPointer++;
    } catch (IllegalArgumentException error) {
      raiseError(state, ErrorValue.E_TYPE, world);
    }
  }

  private static void loadLocal(Frame frame, String name, VmState state, WorldTxn world) {
    String normalized = normalize(name);
    MooValue value = frame.locals.get(normalized);
    if (value == null) {
      if (name.equalsIgnoreCase("INT") || name.equalsIgnoreCase("NUM")) {
        frame.operandStack.push(new IntegerValue(MooValue.Type.INTEGER.code()));
        frame.instructionPointer++;
        return;
      }
      if (name.equalsIgnoreCase("STR")) {
        frame.operandStack.push(new IntegerValue(MooValue.Type.STRING.code()));
        frame.instructionPointer++;
        return;
      }
      if (name.equalsIgnoreCase("FLOAT")) {
        frame.operandStack.push(new IntegerValue(MooValue.Type.FLOAT.code()));
        frame.instructionPointer++;
        return;
      }
      if (name.equalsIgnoreCase("OBJ")) {
        frame.operandStack.push(new IntegerValue(MooValue.Type.OBJECT.code()));
        frame.instructionPointer++;
        return;
      }
      if (name.equalsIgnoreCase("LIST")) {
        frame.operandStack.push(new IntegerValue(MooValue.Type.LIST.code()));
        frame.instructionPointer++;
        return;
      }
      if (name.equalsIgnoreCase("WAIF")) {
        frame.operandStack.push(new IntegerValue(MooValue.Type.WAIF.code()));
        frame.instructionPointer++;
        return;
      }
      if (name.equalsIgnoreCase("ERR")) {
        frame.operandStack.push(new IntegerValue(MooValue.Type.ERROR.code()));
        frame.instructionPointer++;
        return;
      }
      if (name.equalsIgnoreCase("MAP")) {
        frame.operandStack.push(new IntegerValue(MooValue.Type.MAP.code()));
        frame.instructionPointer++;
        return;
      }
      if (name.equalsIgnoreCase("ANON")) {
        frame.operandStack.push(new IntegerValue(12));
        frame.instructionPointer++;
        return;
      }
      if (name.equalsIgnoreCase("BOOL")) {
        frame.operandStack.push(new IntegerValue(MooValue.Type.BOOLEAN.code()));
        frame.instructionPointer++;
        return;
      }
      if (name.equalsIgnoreCase("true")) {
        frame.operandStack.push(BooleanValue.TRUE);
        frame.instructionPointer++;
        return;
      }
      if (name.equalsIgnoreCase("false")) {
        frame.operandStack.push(BooleanValue.FALSE);
        frame.instructionPointer++;
        return;
      }
      raiseError(state, ErrorValue.E_VARNF, world);
      return;
    }
    frame.operandStack.push(value);
    if ((normalized.equals("waif") || normalized.equals("anon"))
        && isFinalStraightLineLocalRead(frame, normalized)) {
      frame.locals.remove(normalized);
    }
    frame.instructionPointer++;
  }

  private static boolean isFinalStraightLineLocalRead(Frame frame, String name) {
    List<Instruction> instructions = frame.program.instructions();
    for (int index = frame.instructionPointer + 1; index < instructions.size(); index++) {
      Instruction instruction = instructions.get(index);
      switch (instruction.opcode()) {
        case LOAD_LOCAL -> {
          if (normalize(instruction.text().orElseThrow()).equals(name)) {
            return false;
          }
        }
        case STORE_LOCAL -> {
          if (normalize(instruction.text().orElseThrow()).equals(name)) {
            return true;
          }
        }
        case SET_INDEX_LOCAL, SET_RANGE_LOCAL -> {
          if (normalize(instruction.text().orElseThrow()).equals(name)) {
            return false;
          }
        }
        case CALL -> {
          if (instruction.text().orElseThrow().equalsIgnoreCase("eval")) {
            return false;
          }
        }
        case FORK,
            JUMP,
            JUMP_IF_FALSE,
            JUMP_IF_TRUE,
            ENTER_HANDLER,
            LEAVE_HANDLER,
            END_FINALLY,
            ITERATE,
            ITERATE_RANGE,
            LEAVE_LOOP,
            SCATTER -> {
          return false;
        }
        default -> {
          // This instruction neither reads nor changes control flow for this local.
        }
      }
    }
    return true;
  }

  private static void getProperty(Frame frame, VmState state, WorldTxn world) {
    MooValue name = frame.operandStack.pop();
    MooValue receiver = frame.operandStack.pop();
    if (!(name instanceof StringValue propertyName)) {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    String nameText = new String(propertyName.bytes(), StandardCharsets.ISO_8859_1);
    if (receiver instanceof WaifValue waif) {
      if (nameText.equalsIgnoreCase("class")) {
        frame.operandStack.push(
            world.object(waif.classObject().value()).isPresent()
                ? waif.classObject()
                : new ObjectValue(-1));
        frame.instructionPointer++;
        return;
      }
      if (nameText.equalsIgnoreCase("owner")) {
        frame.operandStack.push(waif.owner());
        frame.instructionPointer++;
        return;
      }
      if (nameText.equalsIgnoreCase("wizard") || nameText.equalsIgnoreCase("programmer")) {
        frame.operandStack.push(new IntegerValue(0));
        frame.instructionPointer++;
        return;
      }
      WorldProperty property = world.waifProperty(waif, nameText).orElse(null);
      if (property != null) {
        long programmer = state.programmer();
        WorldObject programmerObject = world.object(programmer).orElse(null);
        boolean wizard = programmerObject != null && (programmerObject.flags() & 4) != 0;
        if (property.owner() != programmer && !wizard && (property.permissions() & 1) == 0) {
          raiseError(state, ErrorValue.E_PERM, world);
          return;
        }
      }
      MooValue value = world.readWaifProperty(waif, nameText).orElse(null);
      if (value == null) {
        raiseError(state, ErrorValue.E_PROPNF, world);
        return;
      }
      frame.operandStack.push(value);
      frame.instructionPointer++;
      return;
    }
    if (receiver instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject body = world.anonymousObject(anonymous).orElse(null);
      if (body == null) {
        raiseError(state, ErrorValue.E_INVIND, world);
        return;
      }
      if (nameText.equalsIgnoreCase("owner")) {
        frame.operandStack.push(new ObjectValue(body.owner()));
        frame.instructionPointer++;
        return;
      }
      if (nameText.equalsIgnoreCase("programmer") || nameText.equalsIgnoreCase("wizard")) {
        frame.operandStack.push(new IntegerValue(0));
        frame.instructionPointer++;
        return;
      }
      WorldProperty property = world.property(anonymous, nameText).orElse(null);
      if (property != null) {
        long programmer = state.programmer();
        WorldObject programmerObject = world.object(programmer).orElse(null);
        boolean wizard = programmerObject != null && (programmerObject.flags() & 4) != 0;
        if (property.owner() != programmer && !wizard && (property.permissions() & 1) == 0) {
          raiseError(state, ErrorValue.E_PERM, world);
          return;
        }
      }
      MooValue value = world.readObjectProperty(anonymous, nameText).orElse(null);
      if (value == null) {
        raiseError(state, ErrorValue.E_PROPNF, world);
        return;
      }
      frame.operandStack.push(value);
      frame.instructionPointer++;
      return;
    }
    if (!(receiver instanceof ObjectValue object)) {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    WorldProperty property = world.property(object.value(), nameText).orElse(null);
    if (property != null) {
      long programmer = state.programmer();
      WorldObject programmerObject = world.object(programmer).orElse(null);
      boolean wizard = programmerObject != null && (programmerObject.flags() & 4) != 0;
      if (property.owner() != programmer && !wizard && (property.permissions() & 1) == 0) {
        raiseError(state, ErrorValue.E_PERM, world);
        return;
      }
    }
    MooValue value = world.readObjectProperty(object.value(), nameText).orElse(null);
    if (value == null) {
      raiseError(state, ErrorValue.E_PROPNF, world);
      return;
    }
    frame.operandStack.push(value);
    frame.instructionPointer++;
  }

  private static void setProperty(Frame frame, VmState state, WorldTxn world) {
    MooValue value = frame.operandStack.pop();
    MooValue name = frame.operandStack.pop();
    MooValue receiver = frame.operandStack.pop();
    if (!(name instanceof StringValue propertyName)) {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    String nameText = new String(propertyName.bytes(), StandardCharsets.ISO_8859_1);
    if (receiver instanceof WaifValue waif) {
      if (nameText.equalsIgnoreCase("class")
          || nameText.equalsIgnoreCase("owner")
          || nameText.equalsIgnoreCase("wizard")
          || nameText.equalsIgnoreCase("programmer")) {
        raiseError(state, ErrorValue.E_PERM, world);
        return;
      }
      WorldProperty property = world.waifProperty(waif, nameText).orElse(null);
      if (property != null) {
        long programmer = state.programmer();
        WorldObject programmerObject = world.object(programmer).orElse(null);
        boolean wizard = programmerObject != null && (programmerObject.flags() & 4) != 0;
        if (property.owner() != programmer && !wizard && (property.permissions() & 2) == 0) {
          raiseError(state, ErrorValue.E_PERM, world);
          return;
        }
        if (world.valueRefersToWaif(value, waif)) {
          raiseError(state, ErrorValue.E_RECMOVE, world);
          return;
        }
      }
      if (!world.writeWaifProperty(waif, nameText, value)) {
        raiseError(state, ErrorValue.E_PROPNF, world);
        return;
      }
      frame.operandStack.push(value);
      frame.instructionPointer++;
      return;
    }
    if (receiver instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject body = world.anonymousObject(anonymous).orElse(null);
      if (body == null) {
        raiseError(state, ErrorValue.E_INVIND, world);
        return;
      }
      long programmer = state.programmer();
      WorldObject programmerObject = world.object(programmer).orElse(null);
      boolean wizard = programmerObject != null && (programmerObject.flags() & 4) != 0;
      if (nameText.equalsIgnoreCase("owner")) {
        if (!(value instanceof ObjectValue)) {
          raiseError(state, ErrorValue.E_TYPE, world);
          return;
        }
        if (!wizard) {
          raiseError(state, ErrorValue.E_PERM, world);
          return;
        }
        world.writeObjectProperty(anonymous, nameText, value);
        frame.operandStack.push(value);
        frame.instructionPointer++;
        return;
      }
      if (nameText.equalsIgnoreCase("programmer") || nameText.equalsIgnoreCase("wizard")) {
        raiseError(state, wizard ? ErrorValue.E_INVARG : ErrorValue.E_PERM, world);
        return;
      }
      WorldProperty property = world.property(anonymous, nameText).orElse(null);
      if (property != null) {
        if (property.owner() != programmer && !wizard && (property.permissions() & 2) == 0) {
          raiseError(state, ErrorValue.E_PERM, world);
          return;
        }
      }
      if (!world.writeObjectProperty(anonymous, nameText, value)) {
        raiseError(state, ErrorValue.E_PROPNF, world);
        return;
      }
      frame.operandStack.push(value);
      frame.instructionPointer++;
      return;
    }
    if (!(receiver instanceof ObjectValue object)) {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    if (nameText.equalsIgnoreCase("programmer") || nameText.equalsIgnoreCase("wizard")) {
      WorldObject programmerObject = world.object(state.programmer()).orElse(null);
      if (programmerObject == null || (programmerObject.flags() & 4) == 0) {
        raiseError(state, ErrorValue.E_PERM, world);
        return;
      }
    }
    if (nameText.equalsIgnoreCase("last_move")) {
      raiseError(state, ErrorValue.E_PERM, world);
      return;
    }
    WorldProperty property = world.property(object.value(), nameText).orElse(null);
    if (property != null) {
      long programmer = state.programmer();
      WorldObject programmerObject = world.object(programmer).orElse(null);
      boolean wizard = programmerObject != null && (programmerObject.flags() & 4) != 0;
      if (property.owner() != programmer && !wizard && (property.permissions() & 2) == 0) {
        raiseError(state, ErrorValue.E_PERM, world);
        return;
      }
    }
    if (!world.writeObjectProperty(object.value(), nameText, value)) {
      raiseError(state, ErrorValue.E_PROPNF, world);
      return;
    }
    frame.operandStack.push(value);
    frame.instructionPointer++;
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
        raiseError(state, ErrorValue.E_RANGE, world);
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
        raiseError(state, ErrorValue.E_RANGE, world);
        return;
      }
      if (parentDepth == 1) {
        frame.indexCollections.push(
            new IndexContext(collection, Optional.of(index), context.operandDepth()));
      }
      byte[] bytes = string.bytes();
      frame.operandStack.push(
          new StringValue(new byte[] {bytes[Math.toIntExact(integer.value() - 1)]}));
      frame.instructionPointer++;
      return;
    }
    if (collection instanceof MapValue map) {
      MooValue value;
      try {
        value = map.get(index).orElse(null);
      } catch (IllegalArgumentException error) {
        raiseError(state, ErrorValue.E_TYPE, world);
        return;
      }
      if (value == null) {
        raiseError(state, ErrorValue.E_RANGE, world);
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
    raiseError(state, ErrorValue.E_TYPE, world);
  }

  private static void firstIndex(Frame frame, VmState state, WorldTxn world) {
    MooValue collection = frame.indexCollections.getFirst().collection();
    if (collection instanceof MapValue map) {
      if (map.entries().isEmpty()) {
        raiseError(state, ErrorValue.E_RANGE, world);
        return;
      }
      frame.operandStack.push(map.entries().keySet().iterator().next());
      frame.instructionPointer++;
      return;
    }
    if (collection instanceof ListValue list) {
      if (list.size() == 0) {
        raiseError(state, ErrorValue.E_RANGE, world);
        return;
      }
      frame.operandStack.push(new IntegerValue(1));
      frame.instructionPointer++;
      return;
    }
    if (collection instanceof StringValue string) {
      if (string.length() == 0) {
        raiseError(state, ErrorValue.E_RANGE, world);
        return;
      }
      frame.operandStack.push(new IntegerValue(1));
      frame.instructionPointer++;
      return;
    }
    raiseError(state, ErrorValue.E_TYPE, world);
  }

  private static void lastIndex(Frame frame, VmState state, WorldTxn world) {
    MooValue collection = frame.indexCollections.getFirst().collection();
    if (collection instanceof MapValue map) {
      if (map.entries().isEmpty()) {
        raiseError(state, ErrorValue.E_RANGE, world);
        return;
      }
      MooValue last = null;
      for (MooValue key : map.entries().keySet()) {
        last = key;
      }
      frame.operandStack.push(Objects.requireNonNull(last));
      frame.instructionPointer++;
      return;
    }
    if (collection instanceof ListValue list) {
      if (list.size() == 0) {
        raiseError(state, ErrorValue.E_RANGE, world);
        return;
      }
      frame.operandStack.push(new IntegerValue(list.size()));
      frame.instructionPointer++;
      return;
    }
    if (collection instanceof StringValue string) {
      if (string.length() == 0) {
        raiseError(state, ErrorValue.E_RANGE, world);
        return;
      }
      frame.operandStack.push(new IntegerValue(string.length()));
      frame.instructionPointer++;
      return;
    }
    raiseError(state, ErrorValue.E_TYPE, world);
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
        frame.operandStack.push(new StringValue(new byte[0]));
        frame.instructionPointer++;
        return;
      }
      if (first.value() < 1 || last.value() > bytes.length) {
        raiseError(state, ErrorValue.E_RANGE, world);
        return;
      }
      frame.operandStack.push(
          new StringValue(
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
        raiseError(state, ErrorValue.E_TYPE, world);
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
        raiseError(state, ErrorValue.E_RANGE, world);
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
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    if (last.value() < first.value()) {
      frame.operandStack.push(new ListValue(List.of()));
      frame.instructionPointer++;
      return;
    }
    if (first.value() < 1 || last.value() > list.size()) {
      raiseError(state, ErrorValue.E_RANGE, world);
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
    frame.locals.put(normalize(owner), updatedCollection);
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
      raiseError(state, ErrorValue.E_INVIND, world);
      return;
    }
    WorldObject classOwner = world.object(waifClass.owner()).orElse(null);
    if (classOwner == null || (classOwner.flags() & 4) == 0) {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    WorldVerb verb = world.verb(waif.classObject().value(), verbName).orElse(null);
    OptionalLong location = world.verbLocation(waif.classObject().value(), verbName, true);
    if (verb == null || location.isEmpty()) {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    BytecodeProgram program;
    try {
      program = new MooCompiler().compile(verb.programSource());
    } catch (IllegalArgumentException error) {
      raiseError(state, ErrorValue.E_INVARG, world);
      return;
    }
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("this", waif);
    locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
    locals.put("caller", frame.receiver);
    locals.put("verb", encode(verbName));
    locals.put("args", arguments);
    locals.put("argstr", frame.locals.getOrDefault("argstr", encode("")));
    locals.put("dobj", frame.locals.getOrDefault("dobj", new ObjectValue(-1)));
    locals.put("dobjstr", frame.locals.getOrDefault("dobjstr", encode("")));
    locals.put("prepstr", frame.locals.getOrDefault("prepstr", encode("")));
    locals.put("iobj", frame.locals.getOrDefault("iobj", new ObjectValue(-1)));
    locals.put("iobjstr", frame.locals.getOrDefault("iobjstr", encode("")));
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
        (verb.permissions() & 8) != 0)) {
      raiseError(state, ErrorValue.E_MAXREC, world, false);
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
        raiseError(state, ErrorValue.E_TYPE, world);
        return Optional.empty();
      }
    }
    if (collection instanceof ListValue list && key instanceof IntegerValue index) {
      if (index.value() < 1 || index.value() > list.size()) {
        raiseError(state, ErrorValue.E_RANGE, world);
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
        raiseError(state, ErrorValue.E_RANGE, world);
        return Optional.empty();
      }
      byte[] replaced = string.bytes();
      replaced[Math.toIntExact(index.value() - 1)] = replacement.bytes()[0];
      return Optional.of(new StringValue(replaced));
    }
    raiseError(state, ErrorValue.E_TYPE, world);
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
        raiseError(state, ErrorValue.E_TYPE, world);
        return;
      }
    } else if (collection instanceof ListValue list && key instanceof IntegerValue index) {
      if (index.value() < 1 || index.value() > list.size()) {
        raiseError(state, ErrorValue.E_RANGE, world);
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
        raiseError(state, ErrorValue.E_RANGE, world);
        return;
      }
      byte[] replaced = string.bytes();
      replaced[Math.toIntExact(index.value() - 1)] = replacement.bytes()[0];
      updatedCollection = new StringValue(replaced);
    } else {
      raiseError(state, ErrorValue.E_TYPE, world);
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
        raiseError(state, ErrorValue.E_TYPE, world);
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
        updatedCollection = new StringValue(replaced);
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
        updatedCollection = new StringValue(replaced);
      } else if (first.value() == string.length() + 1L && last.value() >= first.value()) {
        byte[] original = string.bytes();
        byte[] inserted = replacement.bytes();
        byte[] appended = Arrays.copyOf(original, original.length + inserted.length);
        System.arraycopy(inserted, 0, appended, original.length, inserted.length);
        updatedCollection = new StringValue(appended);
      } else {
        if (first.value() < 1 || last.value() < first.value() || last.value() > string.length()) {
          raiseError(state, ErrorValue.E_RANGE, world);
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
        updatedCollection = new StringValue(replaced);
      }
    } else if (collection instanceof ListValue list && value instanceof ListValue replacement) {
      if (!(start instanceof IntegerValue first) || !(end instanceof IntegerValue last)) {
        raiseError(state, ErrorValue.E_TYPE, world);
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
          raiseError(state, ErrorValue.E_RANGE, world);
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
        raiseError(state, ErrorValue.E_TYPE, world);
        return;
      }
      List<MooValue> keys = new ArrayList<>(map.entries().keySet());
      int firstPosition = keys.indexOf(start);
      int lastPosition = keys.indexOf(end);
      if (firstPosition < 0 || lastPosition < 0) {
        raiseError(state, ErrorValue.E_RANGE, world);
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
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    if (parentDepth == 0) {
      frame.locals.put(normalize(owner), updatedCollection);
    } else {
      IndexContext parentContext = frame.indexCollections.pop();
      MooValue parentKey = parentContext.key().orElseThrow();
      MooValue parent = parentContext.collection();
      if (!(parent instanceof ListValue list) || !(parentKey instanceof IntegerValue index)) {
        raiseError(state, ErrorValue.E_TYPE, world);
        return;
      }
      if (index.value() < 1 || index.value() > list.size()) {
        raiseError(state, ErrorValue.E_RANGE, world);
        return;
      }
      List<MooValue> replaced = new ArrayList<>(list.elements());
      replaced.set(Math.toIntExact(index.value() - 1), updatedCollection);
      frame.locals.put(normalize(owner), new ListValue(replaced));
    }
    frame.operandStack.push(value);
    frame.instructionPointer++;
  }

  private static void invokeBuiltin(
      Instruction instruction,
      Frame frame,
      VmState state,
      WorldTxn world,
      BuiltinCatalog builtins,
      long taskId) {
    MooValue argumentValue = frame.operandStack.pop();
    if (!(argumentValue instanceof ListValue arguments)) {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    String name = instruction.text().orElseThrow();
    if (name.equalsIgnoreCase("yin") && arguments.elements().isEmpty()) {
      frame.instructionPointer++;
      if (state.remainingTicks() < 2_000 || state.remainingSeconds() < 2) {
        state.suspend(java.util.OptionalDouble.of(0), Optional.empty());
      } else {
        frame.operandStack.push(new IntegerValue(0));
      }
      return;
    }
    BuiltinSpec spec = builtins.spec(name).orElse(null);
    if (spec == null) {
      raiseError(state, ErrorValue.E_VERBNF, world);
      return;
    }
    frame.instructionPointer++;
    if (spec.effect() == EffectClass.IRREVOCABLE) {
      state.yieldBuiltin(
          new VmSnapshot.PendingBuiltin(
              spec.name(),
              arguments.elements(),
              state.programmer(),
              state.taskLocal(),
              state.remainingTicks(),
              state.remainingSeconds(),
              frame.receiver,
              state.callerProgrammer(),
              state.callers()));
      return;
    }
    ListValue callerFrames =
        name.equals("callers")
                && !arguments.elements().isEmpty()
                && arguments.elements().getFirst().isTruthy()
            ? state.callers(true)
            : state.callers();
    BuiltinResult result =
        builtins.invoke(
            spec,
            arguments.elements(),
            world,
            state.programmer(),
            state.taskLocal(),
            taskId,
            state.remainingTicks(),
            state.remainingSeconds(),
            frame.receiver,
            state.callerProgrammer(),
            callerFrames,
            state.threadMode());
    applyBuiltinResult(result, frame, state, world);
  }

  private static Optional<String> primitivePrototypeProperty(MooValue value) {
    if (value instanceof IntegerValue) {
      return Optional.of("int_proto");
    }
    if (value instanceof FloatValue) {
      return Optional.of("float_proto");
    }
    if (value instanceof StringValue) {
      return Optional.of("str_proto");
    }
    if (value instanceof ErrorValue) {
      return Optional.of("err_proto");
    }
    if (value instanceof ListValue) {
      return Optional.of("list_proto");
    }
    if (value instanceof MapValue) {
      return Optional.of("map_proto");
    }
    return Optional.empty();
  }

  /** Authorizes and applies the exact builtin request for its scheduler task ID. */
  public void authorizePendingBuiltin(
      VmState state, WorldTxn world, BuiltinCatalog builtins, long taskId) {
    VmSnapshot.PendingBuiltin request = state.authorizePendingBuiltin();
    BuiltinSpec spec = builtins.spec(request.name()).orElseThrow();
    BuiltinResult result =
        builtins.invoke(
            spec,
            request.arguments(),
            world,
            request.programmer(),
            request.taskLocal(),
            taskId,
            request.remainingTicks(),
            request.remainingSeconds(),
            request.receiver(),
            request.callerProgrammer(),
            request.callers(),
            state.threadMode());
    applyBuiltinResult(result, state.currentFrame(), state, world);
  }

  private static void applyBuiltinResult(
      BuiltinResult result, Frame frame, VmState state, WorldTxn world) {
    switch (result) {
      case BuiltinResult.SecondsAbort _ -> state.abortSecondsExhaustion();
      case BuiltinResult.ErrorResult error ->
          raiseError(state, error.error(), world, false);
      case BuiltinResult.RaisedError raised ->
          raiseError(state, raised.error(), raised.details(), world, false);
      case BuiltinResult.Value value -> frame.operandStack.push(value.value());
      case BuiltinResult.Initialize initialize ->
          applyInitialize(initialize, frame, state, world);
      case BuiltinResult.Checkpoint _ -> {
        state.stageCheckpointRequest(new CheckpointRequest(false));
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.Shutdown _ -> {
        state.stageCheckpointRequest(new CheckpointRequest(true));
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.Panic panic -> {
        state.stageCheckpointRequest(CheckpointRequest.panic(panic.message()));
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.Suspend suspend ->
          state.suspend(OptionalDouble.of(suspend.seconds()), Optional.empty());
      case BuiltinResult.HostWork hostWork ->
          state.suspend(OptionalDouble.empty(), Optional.of(hostWork.work()));
      case BuiltinResult.ThreadMode threadMode -> {
        state.setThreadMode(threadMode.enabled());
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.DynamicEval dynamicEval ->
          applyDynamicEval(dynamicEval.source(), frame, state, world);
      case BuiltinResult.Output output -> {
        state.stageOutput(output.line());
        frame.operandStack.push(new IntegerValue(1));
      }
      case BuiltinResult.SwitchPlayer switchPlayer -> {
        state.switchPlayer(switchPlayer.player());
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.Programmer programmer -> {
        state.setProgrammer(programmer.programmer());
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.Move move -> applyMove(move, frame, state, world);
      case BuiltinResult.Recycle recycle ->
          applyRecycle(recycle.object(), frame, state, world);
      case BuiltinResult.RecycleAnonymous recycle ->
          applyAnonymousRecycle(recycle.object(), frame, state, world);
      case BuiltinResult.BootPlayer bootPlayer -> {
        state.stageBootPlayerTarget(bootPlayer.target());
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.SetConnectionOption request -> {
        state.stageConnectionOptionRequest(
            new ConnectionOptionRequest(request.target(), request.option(), request.value()));
        frame.operandStack.push(new IntegerValue(0));
      }
      case BuiltinResult.ForceInput request -> {
        state.stageForcedInputRequest(new ForcedInputRequest(request.target(), request.input()));
        frame.operandStack.push(new IntegerValue(0));
      }
    }
  }

  private static void applyDynamicEval(
      String source, Frame frame, VmState state, WorldTxn world) {
    try {
      BytecodeProgram dynamicProgram = new MooCompiler().compile(source);
      if (!state.pushEvalFrame(dynamicProgram)) {
        raiseError(state, ErrorValue.E_MAXREC, world);
      }
    } catch (IllegalArgumentException error) {
      String diagnostic = error.getMessage();
      if (diagnostic == null) {
        diagnostic = error.getClass().getSimpleName();
      }
      frame.operandStack.push(
          new ListValue(
              List.of(
                  new IntegerValue(0),
                  new ListValue(List.of(encode("Parse error: " + diagnostic))))));
    }
  }

  private static void applyMove(
      BuiltinResult.Move move, Frame frame, VmState state, WorldTxn world) {
    WorldVerb hook = world.verb(move.destination(), "accept").orElse(null);
    if (hook == null) {
      if (!world.move(move.object(), move.destination(), move.position())) {
        raiseError(state, ErrorValue.E_INVARG, world);
        return;
      }
      frame.operandStack.push(new IntegerValue(0));
      return;
    }
    BytecodeProgram hookProgram;
    try {
      hookProgram = new MooCompiler().compile(hook.programSource());
    } catch (IllegalArgumentException error) {
      raiseError(state, ErrorValue.E_INVARG, world);
      return;
    }
    Map<String, MooValue> locals = new LinkedHashMap<>();
    ObjectValue destination = new ObjectValue(move.destination());
    locals.put("this", destination);
    locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
    locals.put("caller", frame.receiver);
    locals.put("verb", encode("accept"));
    locals.put("args", new ListValue(List.of(new ObjectValue(move.object()))));
    locals.put("argstr", encode(""));
    if (!state.pushVerbFrame(
        hookProgram,
        locals,
        hook.owner(),
        destination,
        destination,
        OptionalLong.empty(),
        OptionalLong.of(move.object()),
        OptionalLong.of(move.destination()),
        OptionalLong.of(move.position()),
        (hook.permissions() & 8) != 0)) {
      raiseError(state, ErrorValue.E_MAXREC, world, false);
    }
  }

  private static void applyRecycle(
      long recycleTarget, Frame frame, VmState state, WorldTxn world) {
    WorldVerb hook = world.verb(recycleTarget, "recycle").orElse(null);
    if (hook == null) {
      if (!world.recycleObject(recycleTarget)) {
        raiseError(state, ErrorValue.E_INVARG, world);
        return;
      }
      frame.operandStack.push(new IntegerValue(0));
      return;
    }
    BytecodeProgram hookProgram;
    try {
      hookProgram = new MooCompiler().compile(hook.programSource());
    } catch (IllegalArgumentException error) {
      raiseError(state, ErrorValue.E_INVARG, world);
      return;
    }
    Map<String, MooValue> locals = new LinkedHashMap<>();
    ObjectValue target = new ObjectValue(recycleTarget);
    locals.put("this", target);
    locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
    locals.put("caller", frame.locals.getOrDefault("this", new ObjectValue(-1)));
    locals.put("verb", encode("recycle"));
    locals.put("args", new ListValue(List.of()));
    locals.put("argstr", encode(""));
    OptionalLong definingLocation = world.verbLocation(recycleTarget, "recycle", true);
    if (!state.pushVerbFrame(
        hookProgram,
        locals,
        hook.owner(),
        target,
        new ObjectValue(definingLocation.orElse(recycleTarget)),
        OptionalLong.of(recycleTarget),
        OptionalLong.empty(),
        OptionalLong.empty(),
        (hook.permissions() & 8) != 0)) {
      raiseError(state, ErrorValue.E_MAXREC, world, false);
    }
  }

  private static void applyAnonymousRecycle(
      AnonymousObjectValue recycleTarget, Frame frame, VmState state, WorldTxn world) {
    WorldAnonymousObject recycleBody = world.anonymousObject(recycleTarget).orElse(null);
    if (recycleBody == null) {
      raiseError(state, ErrorValue.E_INVARG, world);
      return;
    }
    WorldVerb hook = world.verb(recycleTarget, "recycle", true).orElse(null);
    if (hook == null) {
      if (!world.removeAnonymousObject(recycleTarget)) {
        raiseError(state, ErrorValue.E_INVARG, world);
        return;
      }
      frame.operandStack.push(new IntegerValue(0));
      return;
    }
    BytecodeProgram hookProgram;
    try {
      hookProgram = new MooCompiler().compile(hook.programSource());
    } catch (IllegalArgumentException error) {
      raiseError(state, ErrorValue.E_INVARG, world);
      return;
    }
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("this", recycleTarget);
    locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
    locals.put("caller", frame.locals.getOrDefault("this", new ObjectValue(-1)));
    locals.put("verb", encode("recycle"));
    locals.put("args", new ListValue(List.of()));
    locals.put("argstr", encode(""));
    MooValue definingLocation =
        world.verbLocation(recycleTarget, "recycle", true).orElse(recycleTarget);
    List<AnonymousObjectValue> collectionDeferrals = new ArrayList<>();
    for (WorldProperty property : recycleBody.properties()) {
      if (property.defined()) {
        collectAnonymousReferences(property.value(), collectionDeferrals);
      }
    }
    state.deferAnonymousCollection(collectionDeferrals);
    if (!world.removeAnonymousObject(recycleTarget)) {
      raiseError(state, ErrorValue.E_INVARG, world);
      return;
    }
    if (!state.pushAnonymousRecycleFrame(
        hookProgram,
        locals,
        hook.owner(),
        recycleTarget,
        definingLocation,
        (hook.permissions() & 8) != 0)) {
      raiseError(state, ErrorValue.E_MAXREC, world, false);
    }
  }

  private static void applyInitialize(
      BuiltinResult.Initialize request, Frame frame, VmState state, WorldTxn world) {
    MooValue created = request.created();
    WorldVerb initialize;
    MooValue definingLocation;
    if (created instanceof ObjectValue object) {
      initialize = world.verb(object.value(), "initialize").orElse(null);
      OptionalLong location = world.verbLocation(object.value(), "initialize", true);
      definingLocation = new ObjectValue(location.orElse(object.value()));
    } else if (created instanceof AnonymousObjectValue anonymous) {
      initialize = world.verb(anonymous, "initialize", true).orElse(null);
      definingLocation = world.verbLocation(anonymous, "initialize", true).orElse(anonymous);
    } else {
      raiseError(state, ErrorValue.E_INVARG, world);
      return;
    }
    if (initialize == null) {
      frame.operandStack.push(created);
      return;
    }
    BytecodeProgram initializeProgram;
    try {
      initializeProgram = new MooCompiler().compile(initialize.programSource());
    } catch (IllegalArgumentException error) {
      raiseError(state, ErrorValue.E_INVARG, world);
      return;
    }
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("this", created);
    locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
    locals.put("caller", frame.receiver);
    locals.put("verb", encode("initialize"));
    locals.put("args", request.arguments());
    locals.put("argstr", encode(""));
    if (!state.pushCreateInitializeFrame(
        initializeProgram,
        locals,
        initialize.owner(),
        created,
        definingLocation,
        created,
        (initialize.permissions() & 8) != 0)) {
      raiseError(state, ErrorValue.E_MAXREC, world, false);
    }
  }
  /** Resumes a suspended builtin by routing its MOO error through this activation's handlers. */
  public void resumeWithError(VmState state, BuiltinResult completion, WorldTxn world) {
    state.resumeError();
    switch (completion) {
      case BuiltinResult.RaisedError raised ->
          raiseError(state, raised.error(), raised.details(), world, false);
      case BuiltinResult.ErrorResult error ->
          raiseError(state, error.error(), world, false);
      default -> throw new IllegalArgumentException("completion is not a MOO error");
    }
  }

  private static void membership(Frame frame, VmState state, WorldTxn world) {
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
        if (requested.equals(value)) {
          position = index;
          break;
        }
      }
      frame.operandStack.push(new IntegerValue(position));
      frame.instructionPointer++;
      return;
    }
    if (!(collection instanceof ListValue list)) {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    long position = 0;
    for (int index = 0; index < list.elements().size(); index++) {
      if (requested.equals(list.elements().get(index))) {
        position = index + 1L;
        break;
      }
    }
    frame.operandStack.push(new IntegerValue(position));
    frame.instructionPointer++;
  }

  private static void fork(Instruction instruction, Frame frame, VmState state, WorldTxn world) {
    MooValue delay = frame.operandStack.pop();
    double seconds;
    if (delay instanceof IntegerValue integer) {
      seconds = integer.value();
    } else if (delay instanceof FloatValue floating) {
      seconds = floating.value();
    } else {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    if (seconds < 0) {
      raiseError(state, ErrorValue.E_INVARG, world);
      return;
    }
    BytecodeProgram child =
        frame.program.forkVectors().get(Math.toIntExact(instruction.operand().orElseThrow()));
    frame.instructionPointer++;
    state.requestFork(child, seconds);
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
        raiseError(state, ErrorValue.E_FLOAT, world);
        return;
      }
      frame.operandStack.push(new FloatValue(result));
      frame.instructionPointer++;
      return;
    }
    raiseError(state, ErrorValue.E_TYPE, world);
  }

  private static void bitwiseComplement(Frame frame, VmState state, WorldTxn world) {
    MooValue operand = frame.operandStack.pop();
    if (!(operand instanceof IntegerValue integer)) {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    frame.operandStack.push(new IntegerValue(~integer.value()));
    frame.instructionPointer++;
  }

  private static void bitwise(
      Instruction instruction, Frame frame, VmState state, WorldTxn world) {
    MooValue rightValue = frame.operandStack.pop();
    MooValue leftValue = frame.operandStack.pop();
    if (!(leftValue instanceof IntegerValue left)
        || !(rightValue instanceof IntegerValue right)) {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }

    long result;
    if (instruction.opcode() == BytecodeProgram.Opcode.BITSHL
        || instruction.opcode() == BytecodeProgram.Opcode.BITSHR) {
      long distance = right.value();
      if (distance < 0 || distance > Long.SIZE) {
        raiseError(state, ErrorValue.E_INVARG, world, false);
        return;
      }
      if (distance == Long.SIZE) {
        result = 0;
      } else if (instruction.opcode() == BytecodeProgram.Opcode.BITSHL) {
        result = left.value() << distance;
      } else {
        result = left.value() >>> distance;
      }
    } else {
      result =
          switch (instruction.opcode()) {
            case BITOR -> left.value() | right.value();
            case BITAND -> left.value() & right.value();
            case BITXOR -> left.value() ^ right.value();
            default -> throw new AssertionError(instruction.opcode());
          };
    }
    frame.operandStack.push(new IntegerValue(result));
    frame.instructionPointer++;
  }

  private static void arithmetic(
      Instruction instruction, Frame frame, VmState state, WorldTxn world) {
    MooValue rightValue = frame.operandStack.pop();
    MooValue leftValue = frame.operandStack.pop();
    if (instruction.opcode() == BytecodeProgram.Opcode.ADD
        && leftValue instanceof StringValue left
        && rightValue instanceof StringValue right) {
      byte[] leftBytes = left.bytes();
      byte[] rightBytes = right.bytes();
      byte[] concatenated = new byte[Math.addExact(leftBytes.length, rightBytes.length)];
      System.arraycopy(leftBytes, 0, concatenated, 0, leftBytes.length);
      System.arraycopy(rightBytes, 0, concatenated, leftBytes.length, rightBytes.length);
      frame.operandStack.push(new StringValue(concatenated));
      frame.instructionPointer++;
      return;
    }
    if (instruction.opcode() == BytecodeProgram.Opcode.ADD
        && leftValue instanceof ListValue left
        && rightValue instanceof ListValue right) {
      frame.operandStack.push(left.concatenate(right));
      frame.instructionPointer++;
      return;
    }
    if (instruction.opcode() == BytecodeProgram.Opcode.ADD
        && leftValue instanceof ListValue left) {
      frame.operandStack.push(left.append(rightValue));
      frame.instructionPointer++;
      return;
    }
    if (leftValue instanceof IntegerValue left && rightValue instanceof IntegerValue right) {
      if ((instruction.opcode() == BytecodeProgram.Opcode.DIVIDE
              || instruction.opcode() == BytecodeProgram.Opcode.REMAINDER)
          && right.value() == 0) {
        raiseError(state, ErrorValue.E_DIV, world);
        return;
      }
      if (instruction.opcode() == BytecodeProgram.Opcode.POWER
          && left.value() == 0
          && right.value() < 0) {
        raiseError(state, ErrorValue.E_DIV, world);
        return;
      }
      long result =
          switch (instruction.opcode()) {
            case ADD -> left.value() + right.value();
            case SUBTRACT -> left.value() - right.value();
            case MULTIPLY -> left.value() * right.value();
            case DIVIDE ->
                left.value() == -Long.MAX_VALUE && right.value() == -1
                    ? -Long.MAX_VALUE
                    : left.value() / right.value();
            case REMAINDER ->
                (left.value() % right.value() + right.value()) % right.value();
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
        raiseError(state, ErrorValue.E_DIV, world);
        return;
      }
      double result = Math.pow(left.value(), (double) right.value());
      if (!Double.isFinite(result)) {
        raiseError(state, ErrorValue.E_FLOAT, world);
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
        raiseError(state, ErrorValue.E_DIV, world);
        return;
      }
      if (instruction.opcode() == BytecodeProgram.Opcode.POWER
          && left.value() == 0.0
          && right.value() < 0.0) {
        raiseError(state, ErrorValue.E_DIV, world);
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
        raiseError(state, ErrorValue.E_FLOAT, world);
        return;
      }
      frame.operandStack.push(new FloatValue(result));
      frame.instructionPointer++;
      return;
    }
    raiseError(state, ErrorValue.E_TYPE, world);
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
    boolean equal;
    equal = mooEquals(left, right);
    if (instruction.opcode() == BytecodeProgram.Opcode.NOT_EQUAL) {
      equal = !equal;
    }
    frame.operandStack.push(new IntegerValue(equal ? 1 : 0));
    frame.instructionPointer++;
  }

  private static boolean mooEquals(MooValue left, MooValue right) {
    if (left instanceof BooleanValue bool && right instanceof IntegerValue integer) {
      return integer.value() == (bool.value() ? 1 : 0);
    }
    if (left instanceof IntegerValue integer && right instanceof BooleanValue bool) {
      return integer.value() == (bool.value() ? 1 : 0);
    }
    if (left instanceof ListValue leftList && right instanceof ListValue rightList) {
      if (leftList.size() != rightList.size()) {
        return false;
      }
      for (int index = 0; index < leftList.size(); index++) {
        if (!mooEquals(leftList.elements().get(index), rightList.elements().get(index))) {
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
        if (!mooEquals(leftEntry.getKey(), rightEntry.getKey())
            || !mooEquals(leftEntry.getValue(), rightEntry.getValue())) {
          return false;
        }
      }
      return true;
    }
    return left.equals(right);
  }

  private static void comparison(
      Instruction instruction, Frame frame, VmState state, WorldTxn world) {
    MooValue rightValue = frame.operandStack.pop();
    MooValue leftValue = frame.operandStack.pop();
    if ((leftValue instanceof BooleanValue && rightValue instanceof BooleanValue)
        || (leftValue instanceof WaifValue && rightValue instanceof WaifValue)) {
      boolean result =
          switch (instruction.opcode()) {
            case LESS_THAN, GREATER_THAN -> false;
            case LESS_THAN_OR_EQUAL, GREATER_THAN_OR_EQUAL -> true;
            default -> throw new AssertionError(instruction.opcode());
          };
      frame.operandStack.push(new IntegerValue(result ? 1 : 0));
      frame.instructionPointer++;
      return;
    }
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
    if (leftValue instanceof ObjectValue left && rightValue instanceof ObjectValue right) {
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
    if (leftValue instanceof ErrorValue left && rightValue instanceof ErrorValue right) {
      boolean result =
          switch (instruction.opcode()) {
            case LESS_THAN -> left.code() < right.code();
            case LESS_THAN_OR_EQUAL -> left.code() <= right.code();
            case GREATER_THAN -> left.code() > right.code();
            case GREATER_THAN_OR_EQUAL -> left.code() >= right.code();
            default -> throw new AssertionError(instruction.opcode());
          };
      frame.operandStack.push(new IntegerValue(result ? 1 : 0));
      frame.instructionPointer++;
      return;
    }
    if (leftValue instanceof StringValue left && rightValue instanceof StringValue right) {
      int comparison = left.compareIgnoringCase(right);
      boolean result =
          switch (instruction.opcode()) {
            case LESS_THAN -> comparison < 0;
            case LESS_THAN_OR_EQUAL -> comparison <= 0;
            case GREATER_THAN -> comparison > 0;
            case GREATER_THAN_OR_EQUAL -> comparison >= 0;
            default -> throw new AssertionError(instruction.opcode());
          };
      frame.operandStack.push(new IntegerValue(result ? 1 : 0));
      frame.instructionPointer++;
      return;
    }
    raiseError(state, ErrorValue.E_TYPE, world);
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
        raiseError(
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
      case VmSnapshot.Return returned -> routeReturn(state, returned.value(), world);
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
        raiseError(state, ErrorValue.E_TYPE, world);
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
    frame.locals.put(normalize(variables[0]), element.value());
    if (variables.length == 2) {
      frame.locals.put(normalize(variables[1]), element.indexOrKey());
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
      value = new StringValue(new byte[] {character});
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
        raiseError(state, ErrorValue.E_TYPE, world);
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
    frame.locals.put(normalize(variables[0]), range.next);
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
      raiseError(state, ErrorValue.E_TYPE, world);
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
      raiseError(state, ErrorValue.E_ARGS, world);
      return;
    }
    int sourceIndex = 0;
    for (int index = 0; index < names.length; index++) {
      String encodedName = names[index];
      String name = normalize(encodedName.substring(encodedName.startsWith("@")
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

  private static void routeReturn(VmState state, MooValue value, WorldTxn world) {
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
      if (!world.recycleObject(recycleTarget)) {
        raiseError(state, ErrorValue.E_INVARG, world, false);
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
      if (!world.move(moveObject, moveDestination, movePosition)) {
        raiseError(state, ErrorValue.E_INVARG, world);
        return;
      }
      state.currentFrame().operandStack.push(new IntegerValue(0));
      return;
    }
    state.finishFrame(value);
  }

  private static void raiseError(VmState state, ErrorValue error, WorldTxn world) {
    raiseError(state, error, world, true);
  }

  private static void raiseError(
      VmState state, ErrorValue error, WorldTxn world, boolean advanceInstruction) {
    raiseError(
        state,
        error,
        new ListValue(List.of(encode(error.description()), new IntegerValue(0))),
        world,
        advanceInstruction);
  }

  private static ListValue exceptionTuple(ErrorValue error, ListValue details) {
    List<MooValue> normalized = new ArrayList<>(4);
    normalized.add(error);
    normalized.add(details.size() > 0 ? details.elements().get(0) : encode(""));
    normalized.add(details.size() > 1 ? details.elements().get(1) : new IntegerValue(0));
    normalized.add(
        details.size() > 2 ? details.elements().get(2) : new ListValue(List.of()));
    return new ListValue(normalized);
  }

  private static void raiseError(
      VmState state, ErrorValue error, ListValue details, WorldTxn world) {
    raiseError(state, error, details, world, true);
  }

  private static void raiseError(
      VmState state,
      ErrorValue error,
      ListValue details,
      WorldTxn world,
      boolean advanceInstruction) {
    Frame origin = state.currentFrame();
    if (!origin.debug) {
      origin.operandStack.push(error);
      if (advanceInstruction) {
        origin.instructionPointer++;
      }
      return;
    }
    details = completeErrorDetails(state, error, details);
    state.beginError(error);
    while (true) {
      Frame frame = state.currentFrame();
      while (!frame.handlers.isEmpty()) {
        ActiveHandler handler = frame.handlers.getFirst();
        if (handler.phase == HandlerPhase.TRY
            && handler.specification.catchTarget() >= 0
            && catches(handler, error)) {
          while (frame.operandStack.size() > handler.operandDepth) {
            frame.operandStack.pop();
          }
          while (!frame.indexCollections.isEmpty()
              && frame.indexCollections.getFirst().operandDepth() > handler.operandDepth) {
            frame.indexCollections.pop();
          }
          if (handler.specification.structuredCatchBinding()) {
            frame.handlers.pop();
            while (!frame.handlers.isEmpty()
                && frame.handlers.getFirst().specification.structuredCatchBinding()) {
              frame.handlers.pop();
            }
          } else {
            handler.phase = HandlerPhase.CATCH;
          }
          MooValue catchValue = error;
          if (handler.specification.structuredCatchBinding()) {
            List<MooValue> elements = new ArrayList<>();
            elements.add(error);
            elements.addAll(details.elements());
            catchValue = new ListValue(elements);
          }
          MooValue boundValue = catchValue;
          handler
              .specification
              .catchVariable()
              .ifPresent(name -> frame.locals.put(name, boundValue));
          state.clearPendingError();
          frame.instructionPointer = handler.specification.catchTarget();
          return;
        }
        frame.handlers.pop();
        if (handler.specification.finallyTarget() >= 0) {
          while (frame.operandStack.size() > handler.operandDepth) {
            frame.operandStack.pop();
          }
          while (!frame.indexCollections.isEmpty()
              && frame.indexCollections.getFirst().operandDepth() > handler.operandDepth) {
            frame.indexCollections.pop();
          }
          frame.finallyContinuations.push(
              new VmSnapshot.Raise(exceptionTuple(error, details)));
          state.clearPendingError();
          frame.instructionPointer = handler.specification.finallyTarget();
          return;
        }
      }
      OptionalLong recycleTarget = frame.recycleTarget;
      if (!state.unwindChildFrame()) {
        state.failUncaught(error, exceptionTuple(error, details));
        return;
      }
      if (recycleTarget.isPresent()) {
        world.recycleObject(recycleTarget.orElseThrow());
      }
    }
  }

  private static ListValue completeErrorDetails(
      VmState state, ErrorValue error, ListValue details) {
    MooValue message =
        details.size() > 0 ? details.elements().get(0) : encode(error.description());
    MooValue value =
        details.size() > 1 ? details.elements().get(1) : new IntegerValue(0);
    ListValue traceback =
        details.size() > 2 && details.elements().get(2) instanceof ListValue existing
                && existing.size() > 0
            ? existing
            : traceback(state, error);
    return new ListValue(List.of(message, value, traceback));
  }

  private static ListValue traceback(VmState state, ErrorValue error) {
    List<MooValue> frames = new ArrayList<>();
    boolean origin = true;
    for (Frame frame : state.activeFrames()) {
      frames.add(tracebackFrame(frame, origin));
      boolean catchesHere =
          frame.handlers.stream()
              .anyMatch(
                  handler ->
                      handler.phase == HandlerPhase.TRY
                          && handler.specification.catchTarget() >= 0
                          && catches(handler, error));
      if (catchesHere) {
        break;
      }
      origin = false;
    }
    return new ListValue(frames);
  }

  private static ListValue tracebackFrame(Frame frame, boolean origin) {
    boolean evalFrame =
        frame.returnMode == VmState.ReturnMode.EVAL
            || (frame.returnMode == VmState.ReturnMode.ROOT
                && frame.receiver.equals(new ObjectValue(-1)));
    int instructionIndex =
        Math.max(
            0,
            Math.min(
                frame.instructionPointer - (origin ? 0 : 1),
                frame.program.instructions().size() - 1));
    return new ListValue(
        List.of(
            evalFrame ? new ObjectValue(-1) : tracebackReference(frame.receiver),
            evalFrame ? encode("") : frame.locals.getOrDefault("verb", encode("")),
            evalFrame ? new ObjectValue(-1) : new ObjectValue(frame.programmer),
            evalFrame ? new ObjectValue(-1) : tracebackReference(frame.verbLocation),
            frame.locals.getOrDefault("player", new ObjectValue(-1)),
            new IntegerValue(frame.program.instructions().get(instructionIndex).sourceLine())));
  }

  private static MooValue tracebackReference(MooValue value) {
    return value instanceof AnonymousObjectValue ? new AnonymousObjectValue() : value;
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

  private static void collectAnonymousReferences(
      MooValue value, List<AnonymousObjectValue> references) {
    if (value instanceof AnonymousObjectValue anonymous) {
      if (!references.contains(anonymous)) {
        references.add(anonymous);
      }
      return;
    }
    if (value instanceof ListValue list) {
      for (MooValue element : list.elements()) {
        collectAnonymousReferences(element, references);
      }
      return;
    }
    if (value instanceof MapValue map) {
      for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
        collectAnonymousReferences(entry.getKey(), references);
        collectAnonymousReferences(entry.getValue(), references);
      }
    }
  }

  private static StringValue encode(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }
}
