package moo.vm;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
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
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;
import moo.vm.VmState.ActiveHandler;
import moo.vm.VmState.ContinuationKind;
import moo.vm.VmState.FinallyContinuation;
import moo.vm.VmState.Frame;
import moo.vm.VmState.HandlerPhase;
import moo.vm.VmState.LoopCursor;
import moo.world.WorldObject;
import moo.world.WorldProperty;
import moo.world.WorldTxn;
import moo.world.WorldVerb;

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
        routeReturn(state, new IntegerValue(0), world);
        continue;
      }
      executeInstruction(
          frame.program.instructions().get(frame.instructionPointer), state, world, builtins);
    }
  }

  private static void executeInstruction(
      Instruction instruction, VmState state, WorldTxn world, BuiltinCatalog builtins) {
    Frame frame = state.currentFrame();
    if (isCountedInstruction(instruction, frame)) {
      state.decrementRemainingTicks();
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
      case POP -> {
        frame.operandStack.pop();
        frame.instructionPointer++;
      }
      case GET_PROPERTY -> getProperty(frame, state, world);
      case SET_PROPERTY -> setProperty(frame, state, world);
      case INDEX -> index(frame, state, world);
      case SET_INDEX_LOCAL ->
          setIndexedLocal(frame, state, world, instruction.text().orElseThrow());
      case CALL -> {
        String callName = instruction.text().orElseThrow();
        if (!callName.equalsIgnoreCase("pass")) {
          invokeBuiltin(instruction, frame, state, world, builtins);
        } else {
          MooValue argumentValue = frame.operandStack.pop();
          MooValue thisValue = frame.locals.get("this");
          MooValue verbValue = frame.locals.get("verb");
          if (!(argumentValue instanceof ListValue arguments)
              || !(thisValue instanceof ObjectValue receiver)
              || !(verbValue instanceof StringValue verbNameValue)
              || !(frame.locals.get("player") instanceof ObjectValue)
              || !(frame.locals.get("caller") instanceof ObjectValue)) {
            raiseError(state, ErrorValue.E_TYPE, world);
            return;
          }

          String verbName = new String(verbNameValue.bytes(), StandardCharsets.ISO_8859_1);
          WorldVerb currentVerb = world.verb(receiver.value(), verbName).orElse(null);
          if (currentVerb == null) {
            raiseError(state, ErrorValue.E_VERBNF, world);
            return;
          }

          WorldObject definingObject = null;
          long ancestor = receiver.value();
          while (ancestor != -1 && definingObject == null) {
            WorldObject candidate = world.object(ancestor).orElse(null);
            if (candidate == null) {
              raiseError(state, ErrorValue.E_VERBNF, world);
              return;
            }
            if (candidate.verbs().contains(currentVerb)) {
              definingObject = candidate;
            } else {
              ancestor = candidate.parent();
            }
          }
          if (definingObject == null || definingObject.parent() == -1) {
            raiseError(state, ErrorValue.E_VERBNF, world);
            return;
          }

          WorldVerb target = world.verb(definingObject.parent(), verbName).orElse(null);
          if (target == null) {
            raiseError(state, ErrorValue.E_VERBNF, world);
            return;
          }
          WorldObject targetDefiningObject = null;
          ancestor = definingObject.parent();
          while (ancestor != -1 && targetDefiningObject == null) {
            WorldObject candidate = world.object(ancestor).orElse(null);
            if (candidate == null) {
              raiseError(state, ErrorValue.E_VERBNF, world);
              return;
            }
            if (candidate.verbs().contains(target)) {
              targetDefiningObject = candidate;
            } else {
              ancestor = candidate.parent();
            }
          }
          if (targetDefiningObject == null) {
            raiseError(state, ErrorValue.E_VERBNF, world);
            return;
          }
          BytecodeProgram targetProgram;
          try {
            targetProgram =
                new moo.bytecode.MooCompiler().compile(MooParser.parse(target.programSource()));
          } catch (MooParser.ParseException error) {
            raiseError(state, ErrorValue.E_INVARG, world);
            return;
          }

          frame.instructionPointer++;
          Map<String, MooValue> locals = new LinkedHashMap<>(frame.locals);
          locals.put("caller", receiver);
          locals.put("args", arguments);
          state.pushVerbFrame(
              targetProgram,
              locals,
              target.owner(),
              receiver,
              new ObjectValue(targetDefiningObject.id()),
              OptionalLong.empty(),
              OptionalLong.empty(),
              OptionalLong.empty());
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
        long lookupObject;
        String lookupName;
        if (receiverValue instanceof ObjectValue receiver) {
          lookupObject = receiver.value();
          lookupName = verbName;
        } else if (receiverValue instanceof WaifValue waif) {
          lookupObject = waif.classObject().value();
          lookupName = verbName.startsWith(":") ? verbName : ":" + verbName;
        } else {
          raiseError(state, ErrorValue.E_TYPE, world);
          return;
        }
        WorldVerb verb = world.verb(lookupObject, lookupName).orElse(null);
        if (verb == null) {
          raiseError(state, ErrorValue.E_VERBNF, world);
          return;
        }
        WorldObject definingObject = null;
        long ancestor = lookupObject;
        while (ancestor != -1 && definingObject == null) {
          WorldObject candidate = world.object(ancestor).orElse(null);
          if (candidate == null) {
            raiseError(state, ErrorValue.E_VERBNF, world);
            return;
          }
          if (candidate.verbs().contains(verb)) {
            definingObject = candidate;
          } else {
            ancestor = candidate.parent();
          }
        }
        if (definingObject == null) {
          raiseError(state, ErrorValue.E_VERBNF, world);
          return;
        }
        BytecodeProgram verbProgram;
        try {
          verbProgram =
              new moo.bytecode.MooCompiler().compile(MooParser.parse(verb.programSource()));
        } catch (MooParser.ParseException error) {
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
        locals.put("argstr", encode(""));
        state.pushVerbFrame(
            verbProgram,
            locals,
            verb.owner(),
            receiverValue,
            new ObjectValue(definingObject.id()),
            OptionalLong.empty(),
            OptionalLong.empty(),
            OptionalLong.empty());
      }
      case NEGATE -> unaryNegate(frame, state, world);
      case NOT -> {
        MooValue value = frame.operandStack.pop();
        frame.operandStack.push(new IntegerValue(value.isTruthy() ? 0 : 1));
        frame.instructionPointer++;
      }
      case ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, POWER ->
          arithmetic(instruction, frame, state, world);
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
          SET_INDEX_LOCAL,
          CALL,
          CALL_VERB,
          NEGATE,
          NOT,
          ADD,
          SUBTRACT,
          MULTIPLY,
          DIVIDE,
          REMAINDER,
          POWER,
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
    frame.operandStack.push(new ListValue(elements));
    frame.instructionPointer++;
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
    frame.operandStack.push(new ListValue(elements));
    frame.instructionPointer++;
  }

  private static void buildMap(Frame frame, VmState state, WorldTxn world, int count) {
    List<MooValue> keys = new ArrayList<>(count);
    List<MooValue> values = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      values.addFirst(frame.operandStack.pop());
      keys.addFirst(frame.operandStack.pop());
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
    MooValue value = frame.locals.get(normalize(name));
    if (value == null) {
      if (name.equalsIgnoreCase("INT")) {
        frame.operandStack.push(new IntegerValue(MooValue.Type.INTEGER.code()));
        frame.instructionPointer++;
        return;
      }
      if (name.equalsIgnoreCase("STR")) {
        frame.operandStack.push(new IntegerValue(MooValue.Type.STRING.code()));
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
      raiseError(state, ErrorValue.E_VARNF, world);
      return;
    }
    frame.operandStack.push(value);
    frame.instructionPointer++;
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
        frame.operandStack.push(waif.classObject());
        frame.instructionPointer++;
      } else {
        raiseError(state, ErrorValue.E_PROPNF, world);
      }
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
    if (!(receiver instanceof ObjectValue object) || !(name instanceof StringValue propertyName)) {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    String nameText = new String(propertyName.bytes(), StandardCharsets.ISO_8859_1);
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

  private static void index(Frame frame, VmState state, WorldTxn world) {
    MooValue index = frame.operandStack.pop();
    MooValue collection = frame.operandStack.pop();
    if (collection instanceof ListValue list && index instanceof IntegerValue integer) {
      MooValue value = list.get(integer.value()).orElse(null);
      if (value == null) {
        raiseError(state, ErrorValue.E_RANGE, world);
        return;
      }
      frame.operandStack.push(value);
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
      frame.operandStack.push(value);
      frame.instructionPointer++;
      return;
    }
    raiseError(state, ErrorValue.E_TYPE, world);
  }

  private static void setIndexedLocal(Frame frame, VmState state, WorldTxn world, String owner) {
    MooValue value = frame.operandStack.pop();
    MooValue key = frame.operandStack.pop();
    MooValue collection = frame.operandStack.pop();
    if (!(collection instanceof MapValue map)) {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    try {
      frame.locals.put(normalize(owner), map.with(key, value));
      frame.operandStack.push(value);
      frame.instructionPointer++;
    } catch (IllegalArgumentException error) {
      raiseError(state, ErrorValue.E_TYPE, world);
    }
  }

  private static void invokeBuiltin(
      Instruction instruction,
      Frame frame,
      VmState state,
      WorldTxn world,
      BuiltinCatalog builtins) {
    MooValue argumentValue = frame.operandStack.pop();
    if (!(argumentValue instanceof ListValue arguments)) {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    frame.instructionPointer++;
    Result result =
        builtins.invoke(
            instruction.text().orElseThrow(),
            arguments.elements(),
            world,
            state.programmer(),
            state.taskLocal(),
            state.remainingTicks(),
            state.remainingSeconds(),
            frame.receiver,
            state.callerProgrammer(),
            state.callers());
    if (result.error().isPresent()) {
      raiseError(state, result.error().orElseThrow(), world);
      return;
    }
    result.taskLocal().ifPresent(state::setTaskLocal);
    result.output().ifPresent(state::stageOutput);
    result.connectionOptionRequest().ifPresent(state::stageConnectionOptionRequest);
    result.forcedInputRequest().ifPresent(state::stageForcedInputRequest);
    if (result.bootPlayerTarget().isPresent()) {
      state.stageBootPlayerTarget(result.bootPlayerTarget().orElseThrow());
    }
    if (result.switchedPlayer().isPresent()) {
      state.switchPlayer(result.switchedPlayer().orElseThrow());
    }
    if (result.programmer().isPresent()) {
      state.setProgrammer(result.programmer().orElseThrow());
    }
    if (result.delaySeconds().isPresent() || result.hostResult().isPresent()) {
      state.suspend(result.delaySeconds(), result.hostResult());
      return;
    }
    if (result.dynamicSource().isPresent()) {
      try {
        BytecodeProgram dynamicProgram =
            new moo.bytecode.MooCompiler()
                .compile(MooParser.parse(result.dynamicSource().orElseThrow()));
        state.pushEvalFrame(dynamicProgram);
      } catch (MooParser.ParseException error) {
        raiseError(state, ErrorValue.E_INVARG, world);
      }
      return;
    }
    if (result.moveObject().isPresent() != result.moveDestination().isPresent()) {
      raiseError(state, ErrorValue.E_INVARG, world);
      return;
    }
    if (result.moveObject().isPresent()) {
      long moveObject = result.moveObject().orElseThrow();
      long moveDestination = result.moveDestination().orElseThrow();
      WorldVerb hook = world.verb(moveDestination, "accept").orElse(null);
      if (hook == null) {
        if (!world.move(moveObject, moveDestination)) {
          raiseError(state, ErrorValue.E_INVARG, world);
          return;
        }
        frame.operandStack.push(new IntegerValue(0));
        return;
      }
      BytecodeProgram hookProgram;
      try {
        hookProgram = new moo.bytecode.MooCompiler().compile(MooParser.parse(hook.programSource()));
      } catch (MooParser.ParseException error) {
        raiseError(state, ErrorValue.E_INVARG, world);
        return;
      }
      Map<String, MooValue> locals = new LinkedHashMap<>();
      ObjectValue destination = new ObjectValue(moveDestination);
      locals.put("this", destination);
      locals.put("player", frame.locals.getOrDefault("player", new ObjectValue(-1)));
      locals.put("caller", frame.receiver);
      locals.put("verb", encode("accept"));
      locals.put("args", new ListValue(List.of(new ObjectValue(moveObject))));
      locals.put("argstr", encode(""));
      state.pushVerbFrame(
          hookProgram,
          locals,
          hook.owner(),
          destination,
          destination,
          OptionalLong.empty(),
          OptionalLong.of(moveObject),
          OptionalLong.of(moveDestination));
      return;
    }
    if (result.recycleTarget().isPresent()) {
      long recycleTarget = result.recycleTarget().orElseThrow();
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
        hookProgram = new moo.bytecode.MooCompiler().compile(MooParser.parse(hook.programSource()));
      } catch (MooParser.ParseException error) {
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
      WorldObject definingObject = null;
      long ancestor = recycleTarget;
      while (ancestor != -1 && definingObject == null) {
        WorldObject candidate = world.object(ancestor).orElse(null);
        if (candidate == null) {
          break;
        }
        if (candidate.verbs().contains(hook)) {
          definingObject = candidate;
        } else {
          ancestor = candidate.parent();
        }
      }
      state.pushVerbFrame(
          hookProgram,
          locals,
          hook.owner(),
          target,
          new ObjectValue(definingObject == null ? recycleTarget : definingObject.id()),
          OptionalLong.of(recycleTarget),
          OptionalLong.empty(),
          OptionalLong.empty());
      return;
    }
    frame.operandStack.push(result.value().orElseThrow());
  }

  private static void membership(Frame frame, VmState state, WorldTxn world) {
    MooValue collection = frame.operandStack.pop();
    MooValue requested = frame.operandStack.pop();
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
    if (leftValue instanceof IntegerValue left && rightValue instanceof IntegerValue right) {
      if ((instruction.opcode() == BytecodeProgram.Opcode.DIVIDE
              || instruction.opcode() == BytecodeProgram.Opcode.REMAINDER)
          && right.value() == 0) {
        raiseError(state, ErrorValue.E_DIV, world);
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
    boolean equal = left.equals(right);
    if (instruction.opcode() == BytecodeProgram.Opcode.NOT_EQUAL) {
      equal = !equal;
    }
    frame.operandStack.push(new IntegerValue(equal ? 1 : 0));
    frame.instructionPointer++;
  }

  private static void comparison(
      Instruction instruction, Frame frame, VmState state, WorldTxn world) {
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

  private static void endFinally(VmState state, WorldTxn world) {
    Frame frame = state.currentFrame();
    FinallyContinuation continuation = frame.finallyContinuations.pop();
    switch (continuation.kind()) {
      case NORMAL -> frame.instructionPointer = continuation.normalTarget();
      case RETURN -> routeReturn(state, continuation.returnValue().orElseThrow(), world);
      case ERROR -> raiseError(state, continuation.error().orElseThrow(), world);
    }
  }

  private static void iterate(Instruction instruction, Frame frame, VmState state, WorldTxn world) {
    int instructionIndex = frame.instructionPointer;
    LoopCursor cursor = frame.loops.get(instructionIndex);
    if (cursor == null) {
      MooValue iterable = frame.operandStack.pop();
      if (!(iterable instanceof ListValue list)) {
        raiseError(state, ErrorValue.E_TYPE, world);
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

  private static void scatter(Instruction instruction, Frame frame, VmState state, WorldTxn world) {
    MooValue source = frame.operandStack.pop();
    if (!(source instanceof ListValue list)) {
      raiseError(state, ErrorValue.E_TYPE, world);
      return;
    }
    String[] names = instruction.text().orElseThrow().split(",", -1);
    if (names.length != instruction.operand().orElseThrow() || names.length != list.size()) {
      raiseError(state, ErrorValue.E_ARGS, world);
      return;
    }
    for (int index = 0; index < names.length; index++) {
      frame.locals.put(normalize(names[index]), list.elements().get(index));
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
            new FinallyContinuation(
                ContinuationKind.RETURN,
                -1,
                java.util.Optional.of(value),
                java.util.Optional.empty()));
        frame.instructionPointer = handler.specification.finallyTarget();
        return;
      }
    }
    if (frame.recycleTarget.isPresent()) {
      long recycleTarget = frame.recycleTarget.orElseThrow();
      state.unwindChildFrame();
      if (!world.recycleObject(recycleTarget)) {
        raiseError(state, ErrorValue.E_INVARG, world);
        return;
      }
      state.currentFrame().operandStack.push(new IntegerValue(0));
      return;
    }
    if (frame.moveObject.isPresent() && frame.moveDestination.isPresent()) {
      long moveObject = frame.moveObject.orElseThrow();
      long moveDestination = frame.moveDestination.orElseThrow();
      state.unwindChildFrame();
      if (!world.move(moveObject, moveDestination)) {
        raiseError(state, ErrorValue.E_INVARG, world);
        return;
      }
      state.currentFrame().operandStack.push(new IntegerValue(0));
      return;
    }
    state.finishFrame(value);
  }

  private static void raiseError(VmState state, ErrorValue error, WorldTxn world) {
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
          if (handler.specification.structuredCatchBinding()) {
            frame.handlers.pop();
            while (!frame.handlers.isEmpty()
                && frame.handlers.getFirst().specification.structuredCatchBinding()) {
              frame.handlers.pop();
            }
          } else {
            handler.phase = HandlerPhase.CATCH;
          }
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
          while (frame.operandStack.size() > handler.operandDepth) {
            frame.operandStack.pop();
          }
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
      OptionalLong recycleTarget = frame.recycleTarget;
      if (!state.unwindChildFrame()) {
        state.failUncaught(error);
        return;
      }
      if (recycleTarget.isPresent()) {
        world.recycleObject(recycleTarget.orElseThrow());
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
