package moo.vm;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import moo.value.MooValue;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.vm.VmState.ActiveHandler;
import moo.vm.VmState.Frame;
import moo.vm.VmState.HandlerPhase;
import moo.world.WorldResult;
import moo.world.WorldTxn;

/** Error routing and traceback operations shared by every opcode family. */
final class ErrorOps {
  private ErrorOps() {}

  static void raise(VmState state, ErrorValue error, WorldTxn world) {
    raise(state, error, world, true);
  }

  static boolean propagateWorldFailure(WorldResult<?> result, VmState state, WorldTxn world) {
    if (result instanceof WorldResult.Failed<?> failed) {
      raise(state, failed.reason().value(), world);
      return false;
    }
    return true;
  }

  static void raise(
      VmState state, ErrorValue error, WorldTxn world, boolean advanceInstruction) {
    raise(
        state,
        error,
        new ListValue(List.of(StringValue.of(error.description()), new IntegerValue(0))),
        world,
        advanceInstruction);
  }

  private static ListValue exceptionTuple(ErrorValue error, ListValue details) {
    List<MooValue> normalized = new ArrayList<>(4);
    normalized.add(error);
    normalized.add(details.size() > 0 ? details.elements().get(0) : StringValue.of(""));
    normalized.add(details.size() > 1 ? details.elements().get(1) : new IntegerValue(0));
    normalized.add(details.size() > 2 ? details.elements().get(2) : new ListValue(List.of()));
    return new ListValue(normalized);
  }

  static void raise(VmState state, ErrorValue error, ListValue details, WorldTxn world) {
    raise(state, error, details, world, true);
  }

  static void raise(
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
          frame.finallyContinuations.push(new VmSnapshot.Raise(exceptionTuple(error, details)));
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
        details.size() > 0 ? details.elements().get(0) : StringValue.of(error.description());
    MooValue value = details.size() > 1 ? details.elements().get(1) : new IntegerValue(0);
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
            evalFrame ? StringValue.of("") : frame.locals.getOrDefault("verb", StringValue.of("")),
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
}
