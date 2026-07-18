package moo.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import moo.builtin.BuiltinCatalog.ConnectionOption;
import moo.builtin.BuiltinCatalog.ConnectionOptionRequest;
import moo.builtin.BuiltinCatalog.ForcedInputRequest;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.BytecodeProgram.HandlerSpec;
import moo.bytecode.BytecodeProgram.Instruction;
import moo.bytecode.BytecodeProgram.Opcode;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import org.junit.jupiter.api.Test;

final class VmSnapshotTest {
  @Test
  void roundTripsEveryExplicitFrameAndPendingTaskValue() {
    BytecodeProgram program =
        new BytecodeProgram(List.of(new Instruction(Opcode.PUSH_INTEGER, 7), new Instruction(Opcode.RETURN)));
    BytecodeProgram forkProgram =
        new BytecodeProgram(List.of(new Instruction(Opcode.PUSH_INTEGER, 9), new Instruction(Opcode.RETURN)));
    VmState state =
        new VmState(
            Map.of("THIS", new ObjectValue(5), "seed", new IntegerValue(11)),
            17,
            new ObjectValue(19),
            321,
            10,
            64);
    state.beginSegment(1_000);
    state.ensureRoot(program);

    VmState.Frame frame = state.currentFrame();
    frame.instructionPointer = 1;
    frame.operandStack.push(new IntegerValue(23));
    frame.operandStack.push(text("top"));
    frame.indexCollections.push(
        new VmState.IndexContext(
            new ListValue(List.of(new IntegerValue(1), new IntegerValue(2))),
            Optional.of(new IntegerValue(1)),
            2));
    HandlerSpec handlerSpec =
        new HandlerSpec(1, Optional.of("caught"), true, List.of(), false, 1, 2);
    VmState.ActiveHandler handler = new VmState.ActiveHandler(handlerSpec, 2);
    handler.phase = VmState.HandlerPhase.CATCH;
    frame.handlers.push(handler);
    frame.finallyContinuations.push(
        new VmState.FinallyContinuation(
            VmState.ContinuationKind.RETURN,
            -1,
            Optional.of(new IntegerValue(29)),
            Optional.empty()));
    VmState.LoopCursor loop =
        new VmState.LoopCursor(
            new ListValue(List.of(new IntegerValue(31), new IntegerValue(37))),
            Optional.of(new ListValue(List.of(text("a"), text("b")))));
    loop.nextIndex = 1;
    frame.loops.put(1, loop);

    state.stageOutput("one");
    state.stageConnectionOptionRequest(
        new ConnectionOptionRequest(41, ConnectionOption.HOLD_INPUT, new IntegerValue(1)));
    state.stageBootPlayerTarget(43);
    state.stageForcedInputRequest(new ForcedInputRequest(47, "look"));
    state.switchPlayer(53);
    state.setTaskLocal(new ListValue(List.of(new IntegerValue(59))));
    state.beginError(ErrorValue.E_INVARG);
    state.requestFork(forkProgram, 2.5);

    VmSnapshot snapshot = state.snapshot(1_250);
    VmState restored = VmState.restore(snapshot);

    assertEquals(250, snapshot.elapsedCpuNanos());
    assertEquals(TimeUnit.SECONDS.toNanos(10) - 250, snapshot.remainingCpuNanos());
    assertEquals(snapshot, restored.snapshot(9_000));
    assertSame(program, restored.currentFrame().program);
    assertSame(forkProgram, restored.forkRequest().orElseThrow().program());
  }

  @Test
  void restoredInstructionBoundaryContinuesThroughTheSameProgram() {
    BytecodeProgram program =
        new BytecodeProgram(
            List.of(
                new Instruction(Opcode.PUSH_INTEGER, 40),
                new Instruction(Opcode.PUSH_INTEGER, 2),
                new Instruction(Opcode.ADD),
                new Instruction(Opcode.RETURN)));
    VmState state = new VmState();
    state.ensureRoot(program);
    state.currentFrame().operandStack.push(new IntegerValue(40));
    state.currentFrame().operandStack.push(new IntegerValue(2));
    state.currentFrame().instructionPointer = 2;

    VmState restored = VmState.restore(state.snapshot(0));
    new MooVm().execute(program, restored);

    assertEquals(new IntegerValue(42), restored.returnValue().orElseThrow());
  }

  @Test
  void hostSuspensionCapturesOnlyItsDurablePendingOutcome() {
    BytecodeProgram program =
        new BytecodeProgram(List.of(new Instruction(Opcode.PUSH_INTEGER, 0), new Instruction(Opcode.RETURN)));
    VmState state = new VmState();
    state.ensureRoot(program);
    state.suspend(OptionalDouble.empty(), Optional.of(new CompletableFuture<>()));

    VmSnapshot snapshot = state.snapshot(0);
    VmState restored = VmState.restore(snapshot);

    assertTrue(snapshot.awaitingHostResult());
    assertEquals(VmState.Outcome.SUSPENDED, restored.outcome());
    assertTrue(restored.hostResult().isEmpty());
    assertFalse(restored.suspensionDelaySeconds().isPresent());
    assertEquals(snapshot, restored.snapshot(0));
  }

  private static StringValue text(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }
}
