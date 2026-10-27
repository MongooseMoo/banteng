package world.mongoose.banteng.vm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.vm.VmState.Frame;
import world.mongoose.banteng.vm.VmState.ReturnMode;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

/** Resumable accept, exit, and entry hooks for move(). */
final class MoveOps {
  private MoveOps() {}

  static void start(BuiltinResult.Move move, VmState state, WorldTxn world) {
    WorldObject moving = world.object(move.object()).orElseThrow();
    if (move.destination() == -1 || move.destination() == moving.location()) {
      accepted(move, true, state, world);
    } else if (!callHook(move.destination(), "accept", ReturnMode.MOVE_ACCEPT, move, state, world)) {
      accepted(move, false, state, world);
    }
  }

  static void returned(Frame frame, MooValue value, VmState state, WorldTxn world) {
    BuiltinResult.Move move = new BuiltinResult.Move(
        frame.moveObject.orElseThrow(), frame.moveDestination.orElseThrow(),
        frame.movePosition.orElseThrow());
    state.unwindChildFrame();
    switch (frame.returnMode) {
      case MOVE_ACCEPT -> accepted(move, value.isTruthy(), state, world);
      case MOVE_EXIT -> enter(move, state, world);
      case MOVE_ENTER -> finish(state);
      default -> throw new IllegalStateException("invalid move continuation");
    }
  }

  private static void accepted(
      BuiltinResult.Move move, boolean accepts, VmState state, WorldTxn world) {
    boolean wizard = world.isWizard(state.currentFrame().programmer);
    if (!accepts && !wizard) {
      ErrorOps.raise(state, ErrorValue.E_NACC, world, false);
      return;
    }
    WorldObject moving = world.object(move.object()).orElse(null);
    if (moving == null || (move.destination() != -1 && world.object(move.destination()).isEmpty())
        || (moving.location() == move.destination() && move.position() == 0)) {
      finish(state);
      return;
    }
    long oldLocation = moving.location();
    for (long location = move.destination(); location != -1;
        location = world.object(location).orElseThrow().location()) {
      if (location == move.object()) {
        ErrorOps.raise(state, ErrorValue.E_RECMOVE, world, false);
        return;
      }
    }
    if (!ErrorOps.propagateWorldFailure(
        world.move(move.object(), move.destination(), move.position()), state, world)) {
      return;
    }
    if (oldLocation == move.destination()) {
      finish(state);
    } else if (!callHook(oldLocation, "exitfunc", ReturnMode.MOVE_EXIT, move, state, world)) {
      enter(move, state, world);
    }
  }

  private static void enter(BuiltinResult.Move move, VmState state, WorldTxn world) {
    WorldObject moving = world.object(move.object()).orElse(null);
    if (moving != null && moving.location() == move.destination()
        && world.object(move.destination()).isPresent()
        && callHook(move.destination(), "enterfunc", ReturnMode.MOVE_ENTER, move, state, world)) {
      return;
    }
    finish(state);
  }

  private static boolean callHook(
      long receiver, String name, ReturnMode phase, BuiltinResult.Move move,
      VmState state, WorldTxn world) {
    WorldVerb hook = world.verb(receiver, name).orElse(null);
    if (hook == null) {
      return false;
    }
    BytecodeProgram program;
    try {
      program = new MooCompiler().compile(hook.programSource());
    } catch (IllegalArgumentException error) {
      ErrorOps.raise(state, ErrorValue.E_INVARG, world, false);
      return true;
    }
    Frame caller = state.currentFrame();
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("this", new ObjectValue(receiver));
    locals.put("player", caller.locals.getOrDefault("player", new ObjectValue(-1)));
    locals.put("caller", caller.receiver);
    locals.put("verb", StringValue.of(name));
    locals.put("args", new ListValue(List.of(new ObjectValue(move.object()))));
    locals.put("argstr", StringValue.of(""));
    long location = world.verbLocation(receiver, name, true).orElse(receiver);
    if (!state.pushMoveFrame(program, locals, hook, receiver, location, phase, move)) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
    }
    return true;
  }

  private static void finish(VmState state) {
    state.currentFrame().operandStack.push(new IntegerValue(0));
  }
}
