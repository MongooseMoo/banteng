package world.mongoose.banteng.vm;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import world.mongoose.banteng.bytecode.BytecodeProgram.Instruction;
import world.mongoose.banteng.bytecode.BytecodeProgram.Opcode;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.BooleanValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import world.mongoose.banteng.vm.VmState.Frame;
import world.mongoose.banteng.world.ObjectFlags;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldResult;
import world.mongoose.banteng.world.WorldTxn;

/** Local and world-property operations. */
final class PropertyOps {
  enum Operation {
    LOAD_LOCAL,
    GET_PROPERTY,
    SET_PROPERTY
  }

  private static final Set<Opcode> CONTROL_FLOW =
      Set.of(
          Opcode.FORK,
          Opcode.JUMP,
          Opcode.JUMP_IF_FALSE,
          Opcode.JUMP_IF_TRUE,
          Opcode.ENTER_HANDLER,
          Opcode.LEAVE_HANDLER,
          Opcode.END_FINALLY,
          Opcode.ITERATE,
          Opcode.ITERATE_RANGE,
          Opcode.LEAVE_LOOP,
          Opcode.SCATTER);

  private PropertyOps() {}

  static boolean execute(
      Operation operation, VmState state, Instruction instruction, Frame frame, WorldTxn world) {
    return switch (operation) {
      case LOAD_LOCAL -> {
        loadLocal(frame, instruction.text().orElseThrow(), state, world);
        yield true;
      }
      case GET_PROPERTY -> {
        getProperty(frame, state, world);
        yield true;
      }
      case SET_PROPERTY -> {
        setProperty(frame, state, world);
        yield true;
      }
    };
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
        frame.operandStack.push(new IntegerValue(MooValue.Type.ANONYMOUS.code()));
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
      ErrorOps.raise(state, ErrorValue.E_VARNF, world);
      return;
    }
    frame.operandStack.push(value);
    if (containsAnonymousOrWaifReference(value)
        && isFinalStraightLineLocalRead(frame, normalized)) {
      frame.locals.remove(normalized);
    }
    frame.instructionPointer++;
  }

  private static boolean containsAnonymousOrWaifReference(MooValue value) {
    Objects.requireNonNull(value, "value");
    Set<MooValue> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    ArrayDeque<MooValue> pending = new ArrayDeque<>();
    pending.push(value);
    while (!pending.isEmpty()) {
      MooValue current = pending.pop();
      if (!visited.add(current)) {
        continue;
      }
      if (current instanceof AnonymousObjectValue || current instanceof WaifValue) {
        return true;
      }
      if (current instanceof ListValue list) {
        for (MooValue element : list.elements()) {
          pending.push(element);
        }
      } else if (current instanceof MapValue map) {
        for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
          pending.push(entry.getValue());
          pending.push(entry.getKey());
        }
      }
    }
    return false;
  }

  private static boolean isFinalStraightLineLocalRead(Frame frame, String name) {
    List<Instruction> instructions = frame.program.instructions();
    for (int index = frame.instructionPointer + 1; index < instructions.size(); index++) {
      Instruction future = instructions.get(index);
      Opcode opcode = future.opcode();
      if (opcode == Opcode.LOAD_LOCAL && normalize(future.text().orElseThrow()).equals(name)) {
        return false;
      }
      if (opcode == Opcode.STORE_LOCAL && normalize(future.text().orElseThrow()).equals(name)) {
        return true;
      }
      if ((opcode == Opcode.SET_INDEX_LOCAL || opcode == Opcode.SET_RANGE_LOCAL)
          && normalize(future.text().orElseThrow()).equals(name)) {
        return false;
      }
      if (opcode == Opcode.CALL) {
        String callName = future.text().orElseThrow();
        if (callName.equalsIgnoreCase("eval") || callName.equalsIgnoreCase("pass")) {
          return false;
        }
      }
      if (CONTROL_FLOW.contains(opcode)) {
        return false;
      }
    }
    return true;
  }

  private static void getProperty(Frame frame, VmState state, WorldTxn world) {
    MooValue name = frame.operandStack.pop();
    MooValue receiver = frame.operandStack.pop();
    if (!(name instanceof StringValue propertyName)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    String nameText = propertyName.text();
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
        boolean wizard =
            programmerObject != null && ObjectFlags.isWizard(programmerObject.flags());
        if (property.owner() != programmer && !wizard && (property.permissions() & 1) == 0) {
          ErrorOps.raise(state, ErrorValue.E_PERM, world);
          return;
        }
      }
      MooValue value = world.readWaifProperty(waif, nameText).orElse(null);
      if (value == null) {
        ErrorOps.raise(state, ErrorValue.E_PROPNF, world);
        return;
      }
      frame.operandStack.push(value);
      frame.instructionPointer++;
      return;
    }
    if (receiver instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject body = world.anonymousObject(anonymous).orElse(null);
      if (body == null) {
        ErrorOps.raise(state, ErrorValue.E_INVIND, world);
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
        boolean wizard =
            programmerObject != null && ObjectFlags.isWizard(programmerObject.flags());
        if (property.owner() != programmer && !wizard && (property.permissions() & 1) == 0) {
          ErrorOps.raise(state, ErrorValue.E_PERM, world);
          return;
        }
      }
      MooValue value = world.readObjectProperty(anonymous, nameText).orElse(null);
      if (value == null) {
        ErrorOps.raise(state, ErrorValue.E_PROPNF, world);
        return;
      }
      frame.operandStack.push(value);
      frame.instructionPointer++;
      return;
    }
    if (!(receiver instanceof ObjectValue object)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    WorldProperty property = world.property(object.value(), nameText).orElse(null);
    if (property != null) {
      long programmer = state.programmer();
      WorldObject programmerObject = world.object(programmer).orElse(null);
      boolean wizard =
          programmerObject != null && ObjectFlags.isWizard(programmerObject.flags());
      if (property.owner() != programmer && !wizard && (property.permissions() & 1) == 0) {
        ErrorOps.raise(state, ErrorValue.E_PERM, world);
        return;
      }
    }
    MooValue value = world.readObjectProperty(object.value(), nameText).orElse(null);
    if (value == null) {
      ErrorOps.raise(state, ErrorValue.E_PROPNF, world);
      return;
    }
    frame.operandStack.push(value);
    frame.instructionPointer++;
  }

  static void setProperty(Frame frame, VmState state, WorldTxn world) {
    MooValue value = frame.operandStack.pop();
    MooValue name = frame.operandStack.pop();
    MooValue receiver = frame.operandStack.pop();
    if (!(name instanceof StringValue propertyName)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    String nameText = propertyName.text();
    if (receiver instanceof WaifValue waif) {
      WorldResult<MooValue> result =
          world.writeWaifProperty(waif, nameText, value, state.programmer());
      if (!ErrorOps.propagateWorldFailure(result, state, world)) {
        return;
      }
      frame.operandStack.push(value);
      frame.instructionPointer++;
      return;
    }
    if (receiver instanceof AnonymousObjectValue anonymous) {
      WorldResult<MooValue> result =
          world.writeObjectProperty(anonymous, nameText, value, state.programmer());
      if (!ErrorOps.propagateWorldFailure(result, state, world)) {
        return;
      }
      frame.operandStack.push(value);
      frame.instructionPointer++;
      return;
    }
    if (!(receiver instanceof ObjectValue object)) {
      ErrorOps.raise(state, ErrorValue.E_TYPE, world);
      return;
    }
    WorldResult<MooValue> result =
        world.writeObjectProperty(object.value(), nameText, value, state.programmer());
    if (!ErrorOps.propagateWorldFailure(result, state, world)) {
      return;
    }
    frame.operandStack.push(value);
    frame.instructionPointer++;
  }

  static String normalize(String name) {
    return name.toLowerCase(Locale.ROOT);
  }
}
