package world.mongoose.banteng.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinCatalog.ConnectionOption;
import world.mongoose.banteng.builtin.BuiltinCatalog.ConnectionOptionRequest;
import world.mongoose.banteng.builtin.BuiltinCatalog.ForcedInputRequest;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.BytecodeProgram.HandlerSpec;
import world.mongoose.banteng.bytecode.BytecodeProgram.Instruction;
import world.mongoose.banteng.bytecode.BytecodeProgram.Opcode;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
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
        new VmSnapshot.Return(new IntegerValue(29)));
    VmState.CollectionCursor loop =
        new VmState.CollectionCursor(
            VmSnapshot.CollectionKind.LIST,
            new ListValue(List.of(new IntegerValue(31), new IntegerValue(37))),
            Optional.of(new IntegerValue(2)));
    frame.loops.put(1, loop);

    state.stageOutput("one");
    state.stageConnectionOptionRequest(
        new ConnectionOptionRequest(41, ConnectionOption.HOLD_INPUT, new IntegerValue(1)));
    state.stageBootPlayerTarget(43);
    state.stageForcedInputRequest(new ForcedInputRequest(47, "look"));
    AnonymousObjectValue deferred = new AnonymousObjectValue();
    state.deferAnonymousCollection(List.of(deferred));
    state.switchPlayer(53);
    state.setTaskLocal(new ListValue(List.of(new IntegerValue(59))));
    state.setThreadMode(false);
    state.beginError(ErrorValue.E_INVARG);
    state.requestFork(forkProgram, 2.5);

    VmSnapshot snapshot = state.snapshot(1_250);
    VmState restored = VmState.restore(snapshot);

    assertEquals(250, snapshot.elapsedCpuNanos());
    assertEquals(TimeUnit.SECONDS.toNanos(10) - 250, snapshot.remainingCpuNanos());
    assertEquals(snapshot, restored.snapshot(9_000));
    assertEquals(List.of(deferred), restored.anonymousCollectionDeferrals());
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
  void createInitializeReturnOverrideIsTypedDurableAndAbsentFromOrdinaryFrames() {
    BytecodeProgram rootProgram =
        new BytecodeProgram(List.of(new Instruction(Opcode.RETURN)));
    BytecodeProgram verbProgram =
        new BytecodeProgram(List.of(new Instruction(Opcode.RETURN)));
    VmState state = new VmState();
    state.ensureRoot(rootProgram);
    ObjectValue created = new ObjectValue(7);
    Map<String, world.mongoose.banteng.value.MooValue> collidingLocals =
        Map.of("__banteng_create_result", new IntegerValue(77));
    assertTrue(
        state.pushCreateInitializeFrame(
            verbProgram, collidingLocals, 1, created, new ObjectValue(1), created));

    VmState restored = VmState.restore(state.snapshot(0));

    assertEquals(Optional.of(created), restored.currentFrame().createReturnOverride);
    restored.finishFrame(new IntegerValue(99));
    assertEquals(created, restored.currentFrame().operandStack.pop());
    assertTrue(
        restored.pushVerbFrame(
            verbProgram,
            collidingLocals,
            1,
            new ObjectValue(1),
            new ObjectValue(1),
            OptionalLong.empty(),
            OptionalLong.empty(),
            OptionalLong.empty()));
    assertTrue(restored.currentFrame().createReturnOverride.isEmpty());
    restored.finishFrame(new IntegerValue(99));
    assertEquals(new IntegerValue(99), restored.currentFrame().operandStack.pop());
  }

  @Test
  void hostSuspensionCapturesOnlyItsDurablePendingOutcome() {
    BytecodeProgram program =
        new BytecodeProgram(List.of(new Instruction(Opcode.PUSH_INTEGER, 0), new Instruction(Opcode.RETURN)));
    VmState state = new VmState();
    state.ensureRoot(program);
    state.suspend(
        OptionalDouble.empty(),
        Optional.of(() -> BuiltinResult.value(new IntegerValue(41))));

    VmSnapshot snapshot = state.snapshot(0);
    VmState restored = VmState.restore(snapshot);

    assertTrue(snapshot.awaitingHostResult());
    assertEquals(VmState.Outcome.SUSPENDED, restored.outcome());
    assertTrue(restored.hostWork().isEmpty());
    assertFalse(restored.suspensionDelaySeconds().isPresent());
    assertTrue(restored.threadMode());
    assertEquals(snapshot, restored.snapshot(0));
  }

  @Test
  void byteSizeCountsRecursiveBinaryPayloadRatherThanInstructionPointerOrTextRendering() {
    BytecodeProgram program =
        new BytecodeProgram(List.of(new Instruction(Opcode.RETURN)));
    VmState smaller = new VmState();
    smaller.ensureRoot(program);
    smaller
        .currentFrame()
        .operandStack
        .push(
            new ListValue(
                List.of(
                    new MapValue(
                        Map.of(
                            new IntegerValue(1),
                            StringValue.of(new byte[] {'"'}))))));
    smaller.currentFrame().instructionPointer = 0;

    VmState larger = new VmState();
    larger.ensureRoot(program);
    larger
        .currentFrame()
        .operandStack
        .push(
            new ListValue(
                List.of(
                    new MapValue(
                        Map.of(
                            new IntegerValue(1),
                            StringValue.of(new byte[] {'"', '\\', 'x', 'y', 'z'}))))));
    larger.currentFrame().instructionPointer = 1;

    VmSnapshot smallerSnapshot = smaller.snapshot(0);
    VmSnapshot largerSnapshot = larger.snapshot(0);

    assertTrue(smallerSnapshot.byteSize() > 0);
    assertEquals(smallerSnapshot.byteSize() + 4, largerSnapshot.byteSize());
    assertEquals(largerSnapshot.byteSize(), largerSnapshot.byteSize());
  }

  @Test
  void byteSizeCountsAnonymousObjectIdentityPayload() {
    BytecodeProgram program =
        new BytecodeProgram(List.of(new Instruction(Opcode.RETURN)));
    VmState withoutAnonymous = new VmState();
    withoutAnonymous.ensureRoot(program);
    VmState withAnonymous = new VmState();
    withAnonymous.ensureRoot(program);
    withAnonymous.currentFrame().operandStack.push(new AnonymousObjectValue());

    assertEquals(
        withoutAnonymous.snapshot(0).byteSize() + Byte.BYTES + Long.BYTES,
        withAnonymous.snapshot(0).byteSize());
  }

  @Test
  void roundTripsEveryTypedFinallyReasonAndMultipleTypedLoopCursors() {
    BytecodeProgram program =
        new BytecodeProgram(List.of(new Instruction(Opcode.RETURN)));
    VmState state = new VmState();
    state.ensureRoot(program);
    VmState.Frame frame = state.currentFrame();
    ListValue exception =
        new ListValue(
            List.of(
                ErrorValue.E_TYPE,
                text("bad type"),
                new IntegerValue(17),
                new ListValue(List.of(text("trace")))));
    frame.finallyContinuations.addLast(new VmSnapshot.FallThrough(1));
    frame.finallyContinuations.addLast(new VmSnapshot.Raise(exception));
    frame.finallyContinuations.addLast(new VmSnapshot.Uncaught(new IntegerValue(0)));
    frame.finallyContinuations.addLast(new VmSnapshot.Return(text("returned")));
    frame.finallyContinuations.addLast(new VmSnapshot.Exit(2, 9));
    LinkedHashMap<world.mongoose.banteng.value.MooValue, world.mongoose.banteng.value.MooValue> entries = new LinkedHashMap<>();
    entries.put(text("a"), new IntegerValue(1));
    entries.put(text("b"), new IntegerValue(2));
    frame.loops.put(
        1,
        new VmState.CollectionCursor(
            VmSnapshot.CollectionKind.LIST,
            new ListValue(List.of(new IntegerValue(1), new IntegerValue(2))),
            Optional.of(new IntegerValue(3))));
    frame.loops.put(
        2,
        new VmState.CollectionCursor(
            VmSnapshot.CollectionKind.STRING, text("xy"), Optional.of(new IntegerValue(2))));
    frame.loops.put(
        3,
        new VmState.CollectionCursor(
            VmSnapshot.CollectionKind.MAP,
            new MapValue(entries),
            Optional.of(text("b"))));
    frame.loops.put(
        4,
        new VmState.RangeCursor(
            VmSnapshot.RangeKind.INTEGER,
            new IntegerValue(Long.MAX_VALUE),
            new IntegerValue(Long.MAX_VALUE - 1)));
    frame.loops.put(
        5,
        new VmState.RangeCursor(
            VmSnapshot.RangeKind.OBJECT, new ObjectValue(7), new ObjectValue(11)));

    VmSnapshot snapshot = state.snapshot(0);
    VmState restored = VmState.restore(snapshot);

    assertEquals(snapshot, restored.snapshot(0));
    assertEquals(5, snapshot.frames().getFirst().finallyStates().size());
    assertEquals(5, snapshot.frames().getFirst().loops().size());
    assertEquals(
        Optional.empty(),
        new VmSnapshot.CollectionLoop(
                VmSnapshot.CollectionKind.MAP, new MapValue(entries), Optional.empty())
            .next());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VmSnapshot.Raise(
                new ListValue(List.of(ErrorValue.E_TYPE, text("incomplete")))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new VmSnapshot.CollectionLoop(
                VmSnapshot.CollectionKind.LIST,
                new ListValue(List.of(new IntegerValue(1))),
                Optional.of(new IntegerValue(3))));
  }

  @Test
  void restoredCollectionCursorsResumeAtExactNextElementOrExhaustion() {
    BytecodeProgram indexed =
        new BytecodeProgram(
            List.of(
                new Instruction(Opcode.ITERATE, 7, "value,index"),
                new Instruction(Opcode.BUILD_LIST, 0),
                new Instruction(Opcode.LOAD_LOCAL, "value"),
                new Instruction(Opcode.LIST_APPEND),
                new Instruction(Opcode.LOAD_LOCAL, "index"),
                new Instruction(Opcode.LIST_APPEND),
                new Instruction(Opcode.RETURN),
                new Instruction(Opcode.PUSH_INTEGER, 99),
                new Instruction(Opcode.RETURN)));
    LinkedHashMap<world.mongoose.banteng.value.MooValue, world.mongoose.banteng.value.MooValue> entries = new LinkedHashMap<>();
    entries.put(text("a"), new IntegerValue(1));
    entries.put(text("b"), new IntegerValue(2));
    VmState map = new VmState();
    map.ensureRoot(indexed);
    map.currentFrame()
        .loops
        .put(
            0,
            new VmState.CollectionCursor(
                VmSnapshot.CollectionKind.MAP,
                new MapValue(entries),
                Optional.of(text("b"))));

    VmState restoredMap = VmState.restore(map.snapshot(0));
    new MooVm().execute(indexed, restoredMap);

    assertEquals(
        new ListValue(List.of(new IntegerValue(2), text("b"))),
        restoredMap.returnValue().orElseThrow());

    VmState exhausted = new VmState();
    exhausted.ensureRoot(indexed);
    exhausted.currentFrame()
        .loops
        .put(
            0,
            new VmState.CollectionCursor(
                VmSnapshot.CollectionKind.LIST,
                new ListValue(List.of(new IntegerValue(1), new IntegerValue(2))),
                Optional.of(new IntegerValue(3))));

    VmState restoredExhausted = VmState.restore(exhausted.snapshot(0));
    new MooVm().execute(indexed, restoredExhausted);

    assertEquals(new IntegerValue(99), restoredExhausted.returnValue().orElseThrow());
  }

  @Test
  void restoredObjectRangePreservesMaximumEdgeAndThenExhausts() {
    BytecodeProgram program =
        new BytecodeProgram(
            List.of(
                new Instruction(Opcode.ITERATE_RANGE, 4, "object"),
                new Instruction(Opcode.LOAD_LOCAL, "object"),
                new Instruction(Opcode.RETURN),
                new Instruction(Opcode.JUMP, 0),
                new Instruction(Opcode.PUSH_INTEGER, 99),
                new Instruction(Opcode.RETURN)));
    VmState state = new VmState();
    state.ensureRoot(program);
    state.currentFrame()
        .loops
        .put(
            0,
            new VmState.RangeCursor(
                VmSnapshot.RangeKind.OBJECT,
                new ObjectValue(Long.MAX_VALUE),
                new ObjectValue(Long.MAX_VALUE)));

    VmState restored = VmState.restore(state.snapshot(0));
    new MooVm().execute(program, restored);

    assertEquals(new ObjectValue(Long.MAX_VALUE), restored.returnValue().orElseThrow());
    VmSnapshot.RangeLoop afterMaximum =
        (VmSnapshot.RangeLoop)
            Objects.requireNonNull(restored.snapshot(0).frames().getFirst().loops().get(0));
    assertEquals(new ObjectValue(Long.MAX_VALUE), afterMaximum.next());
    assertEquals(new ObjectValue(Long.MAX_VALUE - 1), afterMaximum.end());

    VmState exhausted = new VmState();
    exhausted.ensureRoot(program);
    exhausted.currentFrame()
        .loops
        .put(
            0,
            new VmState.RangeCursor(
                VmSnapshot.RangeKind.OBJECT, afterMaximum.next(), afterMaximum.end()));
    VmState restoredExhausted = VmState.restore(exhausted.snapshot(0));
    new MooVm().execute(program, restoredExhausted);

    assertEquals(new IntegerValue(99), restoredExhausted.returnValue().orElseThrow());
  }

  @Test
  void restoredNestedLoopCursorsRemainIndependent() {
    BytecodeProgram program =
        new BytecodeProgram(
            List.of(
                new Instruction(Opcode.ITERATE, 12, "outer"),
                new Instruction(Opcode.ITERATE, 9, "inner"),
                new Instruction(Opcode.BUILD_LIST, 0),
                new Instruction(Opcode.LOAD_LOCAL, "outer"),
                new Instruction(Opcode.LIST_APPEND),
                new Instruction(Opcode.LOAD_LOCAL, "inner"),
                new Instruction(Opcode.LIST_APPEND),
                new Instruction(Opcode.RETURN),
                new Instruction(Opcode.JUMP, 1),
                new Instruction(Opcode.LEAVE_LOOP, 1),
                new Instruction(Opcode.JUMP, 0),
                new Instruction(Opcode.PUSH_INTEGER, -1),
                new Instruction(Opcode.PUSH_INTEGER, 99),
                new Instruction(Opcode.RETURN)));
    ListValue values =
        new ListValue(List.of(new IntegerValue(1), new IntegerValue(2)));
    VmState state =
        new VmState(
            Map.of("outer", new IntegerValue(1), "inner", new IntegerValue(1)), 0);
    state.ensureRoot(program);
    state.currentFrame().instructionPointer = 8;
    state.currentFrame()
        .loops
        .put(
            0,
            new VmState.CollectionCursor(
                VmSnapshot.CollectionKind.LIST,
                values,
                Optional.of(new IntegerValue(2))));
    state.currentFrame()
        .loops
        .put(
            1,
            new VmState.CollectionCursor(
                VmSnapshot.CollectionKind.LIST,
                values,
                Optional.of(new IntegerValue(2))));

    VmState restored = VmState.restore(state.snapshot(0));
    new MooVm().execute(program, restored);

    assertEquals(
        new ListValue(List.of(new IntegerValue(1), new IntegerValue(2))),
        restored.returnValue().orElseThrow());
    assertEquals(2, restored.currentFrame().loops.size());
    assertEquals(
        Optional.of(new IntegerValue(2)),
        ((VmSnapshot.CollectionLoop)
                Objects.requireNonNull(
                    restored.snapshot(0).frames().getFirst().loops().get(0)))
            .next());
    assertEquals(
        Optional.of(new IntegerValue(3)),
        ((VmSnapshot.CollectionLoop)
                Objects.requireNonNull(
                    restored.snapshot(0).frames().getFirst().loops().get(1)))
            .next());
  }

  @Test
  void executesEveryRestoredTypedFinallyReason() {
    BytecodeProgram fallThroughProgram =
        new BytecodeProgram(
            List.of(
                new Instruction(Opcode.END_FINALLY),
                new Instruction(Opcode.PUSH_INTEGER, 42),
                new Instruction(Opcode.RETURN)));
    VmState fallThrough = new VmState();
    fallThrough.ensureRoot(fallThroughProgram);
    fallThrough.currentFrame().finallyContinuations.push(new VmSnapshot.FallThrough(1));
    VmState restoredFallThrough = VmState.restore(fallThrough.snapshot(0));
    new MooVm().execute(fallThroughProgram, restoredFallThrough);
    assertEquals(new IntegerValue(42), restoredFallThrough.returnValue().orElseThrow());

    BytecodeProgram terminalProgram =
        new BytecodeProgram(List.of(new Instruction(Opcode.END_FINALLY)));
    VmState returned = new VmState();
    returned.ensureRoot(terminalProgram);
    returned.currentFrame().finallyContinuations.push(new VmSnapshot.Return(text("done")));
    VmState restoredReturned = VmState.restore(returned.snapshot(0));
    new MooVm().execute(terminalProgram, restoredReturned);
    assertEquals(text("done"), restoredReturned.returnValue().orElseThrow());

    VmState raised = new VmState();
    raised.ensureRoot(terminalProgram);
    raised
        .currentFrame()
        .finallyContinuations
        .push(
            new VmSnapshot.Raise(
                new ListValue(
                    List.of(
                        ErrorValue.E_TYPE,
                        text("bad type"),
                        new IntegerValue(17),
                        new ListValue(List.of())))));
    VmState restoredRaised = VmState.restore(raised.snapshot(0));
    new MooVm().execute(terminalProgram, restoredRaised);
    assertEquals(VmState.Outcome.ERRORED, restoredRaised.outcome());
    assertEquals(ErrorValue.E_TYPE, restoredRaised.uncaughtError().orElseThrow());

    VmState uncaught = new VmState();
    uncaught.ensureRoot(terminalProgram);
    uncaught.currentFrame().finallyContinuations.push(new VmSnapshot.Uncaught(ErrorValue.E_QUOTA));
    VmState restoredUncaught = VmState.restore(uncaught.snapshot(0));
    new MooVm().execute(terminalProgram, restoredUncaught);
    assertEquals(VmState.Outcome.ERRORED, restoredUncaught.outcome());
    assertEquals(ErrorValue.E_QUOTA, restoredUncaught.uncaughtError().orElseThrow());
  }

  @Test
  void restoredFinallyExitTrimsOperandsAndRemovesExitedNestedLoops() {
    BytecodeProgram program =
        new BytecodeProgram(
            List.of(
                new Instruction(Opcode.END_FINALLY),
                new Instruction(Opcode.ITERATE, 5, "outer"),
                new Instruction(Opcode.ITERATE, 7, "inner"),
                new Instruction(Opcode.PUSH_INTEGER, -1),
                new Instruction(Opcode.RETURN),
                new Instruction(Opcode.PUSH_INTEGER, -2),
                new Instruction(Opcode.RETURN),
                new Instruction(Opcode.PUSH_INTEGER, -3),
                new Instruction(Opcode.PUSH_INTEGER, 42),
                new Instruction(Opcode.RETURN)));
    VmState state = new VmState();
    state.ensureRoot(program);
    state.currentFrame().operandStack.push(new IntegerValue(1));
    state.currentFrame().operandStack.push(new IntegerValue(2));
    state.currentFrame().finallyContinuations.push(new VmSnapshot.Exit(1, 8));
    ListValue values = new ListValue(List.of(new IntegerValue(1)));
    state.currentFrame()
        .loops
        .put(
            1,
            new VmState.CollectionCursor(
                VmSnapshot.CollectionKind.LIST,
                values,
                Optional.of(new IntegerValue(2))));
    state.currentFrame()
        .loops
        .put(
            2,
            new VmState.CollectionCursor(
                VmSnapshot.CollectionKind.LIST,
                values,
                Optional.of(new IntegerValue(2))));

    VmState restored = VmState.restore(state.snapshot(0));
    new MooVm().execute(program, restored);

    assertEquals(new IntegerValue(42), restored.returnValue().orElseThrow());
    assertEquals(List.of(new IntegerValue(1)), List.copyOf(restored.currentFrame().operandStack));
    assertTrue(restored.currentFrame().loops.isEmpty());
  }

  private static StringValue text(String value) {
    return StringValue.of(value);
  }
}
