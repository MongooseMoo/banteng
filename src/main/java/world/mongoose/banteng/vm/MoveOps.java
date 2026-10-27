package world.mongoose.banteng.vm;

import java.util.List;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.vm.VmState.Frame;
import world.mongoose.banteng.vm.VmState.ReturnMode;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldTxn;

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
    WorldTxn.ResolvedVerb hook = world.resolveVerb(receiver, name, true).orElse(null);
    if (hook == null) {
      return false;
    }
    MooVm.VerbCallKind kind = switch (phase) {
      case MOVE_ACCEPT -> MooVm.VerbCallKind.HOOK_ACCEPT;
      case MOVE_EXIT -> MooVm.VerbCallKind.HOOK_EXIT;
      case MOVE_ENTER -> MooVm.VerbCallKind.HOOK_ENTER;
      default -> throw new IllegalArgumentException("invalid move hook phase");
    };
    if (!MooVm.callVerb(state, new ObjectValue(receiver), hook,
        new ListValue(List.of(new ObjectValue(move.object()))), name, kind,
        java.util.Optional.empty(), java.util.Optional.of(move), java.util.Optional.empty())) {
      ErrorOps.raise(state, ErrorValue.E_MAXREC, world, false);
    }
    return true;
  }

  private static void finish(VmState state) {
    state.currentFrame().operandStack.push(new IntegerValue(0));
  }
}
