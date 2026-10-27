package world.mongoose.banteng.vm;

import java.util.List;
import java.util.Locale;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import world.mongoose.banteng.vm.VmState.Frame;
import world.mongoose.banteng.world.Access;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldResult;
import world.mongoose.banteng.world.WorldTxn;

/** Local and world-property operations. */
final class PropertyOps {
  private PropertyOps() {}

  static void loadLocal(Frame frame, int slot, boolean clear, VmState state, WorldTxn world) {
    MooValue value = frame.locals[slot];
    if (value == null) {
      ErrorOps.raise(state, ErrorValue.E_VARNF,
          new ListValue(List.of(StringValue.of("Variable not found: "
              + frame.program.instructions().get(frame.instructionPointer).text()
                  .orElse(frame.program.variableNames().get(slot))), new IntegerValue(0))), world);
      return;
    }
    frame.operandStack.push(value);
    if (clear) frame.locals[slot] = null;
    frame.instructionPointer++;
  }

  static void getProperty(Frame frame, VmState state, WorldTxn world) {
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
        if (!Access.canRead(world, programmer, property.owner(), property.perms())) {
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
        if (!Access.canRead(world, programmer, property.owner(), property.perms())) {
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
      if (!Access.canRead(world, programmer, property.owner(), property.perms())) {
        ErrorOps.raise(state, ErrorValue.E_PERM, world);
        return;
      }
    }
    MooValue value = world.readObjectProperty(object.value(), nameText).orElse(null);
    if (value == null) {
      ErrorOps.raise(state, ErrorValue.E_PROPNF,
          new ListValue(List.of(StringValue.of("Property not found: " + receiver.toLiteral()
              + "." + nameText), new IntegerValue(0))), world);
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
