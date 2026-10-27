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
import world.mongoose.banteng.builtin.BuiltinCatalog.NotificationRequest;
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
  void taskOriginOwnsCopiedNormalizedLocalsAndRootMergePreservesMetadata() {
    Map<String, world.mongoose.banteng.value.MooValue> locals = new LinkedHashMap<>();
    locals.put("SEED", new IntegerValue(7));
    TaskOrigin origin = new TaskOrigin(locals, 17, new ObjectValue(19), "alias", "full alias", false);
    locals.clear();
    assertEquals(Map.of("seed", new IntegerValue(7)), origin.locals());
    assertThrows(UnsupportedOperationException.class,
        () -> origin.locals().put("other", new IntegerValue(8)));
    VmState state = new VmState(origin, new TaskBudget(321, 0, 64));
    BytecodeProgram program = snapshotProgram(List.of(new Instruction(Opcode.RETURN)));
    state.pushFrame(program, Map.of("VERB", text("entered")), new VmState.FrameOrigin(
        VmState.ReturnMode.ROOT, 23, new ObjectValue(29), new ObjectValue(31), "entered",
        "entered full", Optional.empty(), OptionalLong.empty(), OptionalLong.empty(),
        OptionalLong.empty(), OptionalLong.empty(), Optional.empty(), true));
    VmSnapshot snapshot = state.snapshot(0);
    assertEquals(Map.of("seed", new IntegerValue(7), "verb", text("entered")), snapshot.initialLocals());
    assertEquals(17, snapshot.initialProgrammer());
    assertEquals(new ObjectValue(19), snapshot.initialVerbLocation());
    assertEquals("alias", snapshot.initialCalledVerb());
    assertEquals("full alias", snapshot.initialFullVerbName());
    assertEquals(Map.of("seed", new IntegerValue(7)), origin.locals());
  }

  @Test
  void budgetCaptureIsObservationalAndRestoreStartsANewSegment() {
    VmState state = new VmState(
        new TaskOrigin(Map.of(), 0, new ObjectValue(-1), "", "", true),
        new TaskBudget(12, 5, 50));
    state.budget().beginSegment(1_000);
    VmSnapshot first = state.snapshot(1_250);
    VmSnapshot second = state.snapshot(1_500);
    assertEquals(250, first.elapsedCpuNanos());
    assertEquals(500, second.elapsedCpuNanos());
    assertEquals(TimeUnit.SECONDS.toNanos(5) - 500, second.remainingCpuNanos());
    VmState restored = VmState.restore(second);
    assertEquals(second, restored.snapshot(9_000));
    restored.budget().beginSegment(10_000);
    assertEquals(750, restored.snapshot(10_250).elapsedCpuNanos());
    VmSnapshot background = VmState.restoreBackground(second, 99, 10, 75).snapshot(20_000);
    assertEquals(99, background.remainingTicks());
    assertEquals(0, background.elapsedCpuNanos());
    assertEquals(TimeUnit.SECONDS.toNanos(10), background.remainingCpuNanos());
    assertEquals(75, background.maxStackDepth());
  }

  @Test
  void outboxDrainsKeepOrderedCopiesAndAnonymousIdentityDeduplication() {
    EffectOutbox outbox = new EffectOutbox();
    outbox.stageBootPlayerTarget(7);
    outbox.stageBootPlayerTarget(9);
    List<Long> drained = outbox.drainBootPlayerTargets();
    assertEquals(List.of(7L, 9L), drained);
    assertEquals(List.of(), outbox.drainBootPlayerTargets());
    outbox.stageBootPlayerTarget(11);
    assertEquals(List.of(7L, 9L), drained);
    AnonymousObjectValue first = new AnonymousObjectValue();
    AnonymousObjectValue second = new AnonymousObjectValue();
    outbox.deferAnonymousCollection(List.of(first, second, first));
    assertEquals(List.of(first, second), outbox.anonymousCollectionDeferrals());
    outbox.stageNotificationRequest(new NotificationRequest(-7, "abc", false, false));
    outbox.stageNotificationRequest(new NotificationRequest(-8, "ignored", false, false));
    outbox.stageNotificationRequest(new NotificationRequest(-7, "d", false, true));
    assertEquals(13, outbox.stagedBufferedOutputLength(-7, 7));
  }

  @Test
  void roundTripsEveryExplicitFrameAndPendingTaskValue() {
    BytecodeProgram program =
        snapshotProgram(List.of(new Instruction(Opcode.PUSH_INTEGER, 7), new Instruction(Opcode.RETURN)));
    BytecodeProgram forkProgram =
        snapshotProgram(List.of(new Instruction(Opcode.PUSH_INTEGER, 9), new Instruction(Opcode.RETURN)));
    VmState state =
        new VmState(new TaskOrigin(Map.of("THIS", new ObjectValue(5), "seed", new IntegerValue(11)), 17, new ObjectValue(19),
        TaskOrigin.calledVerb(Map.of("THIS", new ObjectValue(5), "seed", new IntegerValue(11))), TaskOrigin.calledVerb(Map.of("THIS", new ObjectValue(5), "seed", new IntegerValue(11))), true), new TaskBudget(321, 10, 64));
    state.budget().beginSegment(1_000);
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

    state.effects().stageOutput("one");
    state.effects().stageConnectionOptionRequest(
        new ConnectionOptionRequest(41, ConnectionOption.HOLD_INPUT, new IntegerValue(1)));
    state.effects().stageBootPlayerTarget(43);
    state.effects().stageForcedInputRequest(new ForcedInputRequest(47, "look"));
    state.effects().stageNotificationRequest(new NotificationRequest(-7, "abc", true, true));
    AnonymousObjectValue deferred = new AnonymousObjectValue();
    state.effects().deferAnonymousCollection(List.of(deferred));
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
    assertEquals(3, restored.effects().stagedBufferedOutputLength(-7, 0));
    assertEquals(
        List.of(new NotificationRequest(-7, "abc", true, true)),
        restored.effects().drainNotificationRequests());
    assertEquals(List.of(deferred), restored.effects().anonymousCollectionDeferrals());
    assertSame(program, restored.currentFrame().program);
    assertSame(forkProgram, restored.forkRequest().orElseThrow().program());
  }

  @Test
  void ordinaryNotificationsRemainVisibleInAttemptLocalBufferedOutput() {
    VmState state = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
    state.effects().stageNotificationRequest(new NotificationRequest(-7, "abc", false, false));

    assertEquals(12, state.effects().stagedBufferedOutputLength(-7, 7));
  }

  @Test
  void restoredInstructionBoundaryContinuesThroughTheSameProgram() {
    BytecodeProgram program =
        snapshotProgram(
            List.of(
                new Instruction(Opcode.PUSH_INTEGER, 40),
                new Instruction(Opcode.PUSH_INTEGER, 2),
                new Instruction(Opcode.ADD),
                new Instruction(Opcode.RETURN)));
    VmState state = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
    state.ensureRoot(program);
    state.currentFrame().operandStack.push(new IntegerValue(40));
    state.currentFrame().operandStack.push(new IntegerValue(2));
    state.currentFrame().instructionPointer = 2;

    VmState restored = VmState.restore(state.snapshot(0));
    new PureVmFixture().execute(program, restored);

    assertEquals(new IntegerValue(42), restored.returnValue().orElseThrow());
  }

  @Test
  void createInitializeReturnOverrideIsTypedDurableAndAbsentFromOrdinaryFrames() {
    BytecodeProgram rootProgram =
        snapshotProgram(List.of(new Instruction(Opcode.RETURN)));
    BytecodeProgram verbProgram =
        snapshotProgram(List.of(new Instruction(Opcode.RETURN)));
    VmState state = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
    state.ensureRoot(rootProgram);
    ObjectValue created = new ObjectValue(7);
    Map<String, world.mongoose.banteng.value.MooValue> collidingLocals =
        Map.of("__banteng_create_result", new IntegerValue(77));
    assertTrue(
        state.pushFrame(verbProgram, collidingLocals, new VmState.FrameOrigin(
            VmState.ReturnMode.VERB, 1, created, new ObjectValue(1), "", "", Optional.of(created),
            OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(),
            Optional.empty(), true)));

    VmState restored = VmState.restore(state.snapshot(0));

    assertEquals(Optional.of(created), restored.currentFrame().createReturnOverride);
    restored.finishFrame(new IntegerValue(99));
    assertEquals(created, restored.currentFrame().operandStack.pop());
    assertTrue(
        restored.pushFrame(verbProgram, collidingLocals, new VmState.FrameOrigin(
            VmState.ReturnMode.VERB, 1, new ObjectValue(1), new ObjectValue(1), "", "",
            Optional.empty(), OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(),
            OptionalLong.empty(), Optional.empty(), true)));
    assertTrue(restored.currentFrame().createReturnOverride.isEmpty());
    restored.finishFrame(new IntegerValue(99));
    assertEquals(new IntegerValue(99), restored.currentFrame().operandStack.pop());
  }

  @Test
  void hostSuspensionCapturesOnlyItsDurablePendingOutcome() {
    BytecodeProgram program =
        snapshotProgram(List.of(new Instruction(Opcode.PUSH_INTEGER, 0), new Instruction(Opcode.RETURN)));
    VmState state = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
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
        snapshotProgram(List.of(new Instruction(Opcode.RETURN)));
    VmState smaller = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
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

    VmState larger = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
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
        snapshotProgram(List.of(new Instruction(Opcode.RETURN)));
    VmState withoutAnonymous = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
    withoutAnonymous.ensureRoot(program);
    VmState withAnonymous = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
    withAnonymous.ensureRoot(program);
    withAnonymous.currentFrame().operandStack.push(new AnonymousObjectValue());

    assertEquals(
        withoutAnonymous.snapshot(0).byteSize() + Byte.BYTES + Long.BYTES,
        withAnonymous.snapshot(0).byteSize());
  }

  @Test
  void roundTripsEveryTypedFinallyReasonAndMultipleTypedLoopCursors() {
    BytecodeProgram program =
        snapshotProgram(List.of(new Instruction(Opcode.RETURN)));
    VmState state = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
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
        snapshotProgram(
            List.of(
                loopInstruction(Opcode.ITERATE, 7, "value,index", List.of(25, 26)),
                new Instruction(Opcode.BUILD_LIST, 0),
                new Instruction(Opcode.LOAD_LOCAL, 25),
                new Instruction(Opcode.LIST_APPEND),
                new Instruction(Opcode.LOAD_LOCAL, 26),
                new Instruction(Opcode.LIST_APPEND),
                new Instruction(Opcode.RETURN),
                new Instruction(Opcode.PUSH_INTEGER, 99),
                new Instruction(Opcode.RETURN)));
    LinkedHashMap<world.mongoose.banteng.value.MooValue, world.mongoose.banteng.value.MooValue> entries = new LinkedHashMap<>();
    entries.put(text("a"), new IntegerValue(1));
    entries.put(text("b"), new IntegerValue(2));
    VmState map = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
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
    new PureVmFixture().execute(indexed, restoredMap);

    assertEquals(
        new ListValue(List.of(new IntegerValue(2), text("b"))),
        restoredMap.returnValue().orElseThrow());

    VmState exhausted = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
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
    new PureVmFixture().execute(indexed, restoredExhausted);

    assertEquals(new IntegerValue(99), restoredExhausted.returnValue().orElseThrow());
  }

  @Test
  void restoredObjectRangePreservesMaximumEdgeAndThenExhausts() {
    BytecodeProgram program =
        snapshotProgram(
            List.of(
                loopInstruction(Opcode.ITERATE_RANGE, 4, "object", List.of(27)),
                new Instruction(Opcode.LOAD_LOCAL, 27),
                new Instruction(Opcode.RETURN),
                new Instruction(Opcode.JUMP, 0),
                new Instruction(Opcode.PUSH_INTEGER, 99),
                new Instruction(Opcode.RETURN)));
    VmState state = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
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
    new PureVmFixture().execute(program, restored);

    assertEquals(new ObjectValue(Long.MAX_VALUE), restored.returnValue().orElseThrow());
    VmSnapshot.RangeLoop afterMaximum =
        (VmSnapshot.RangeLoop)
            Objects.requireNonNull(restored.snapshot(0).frames().getFirst().loops().get(0));
    assertEquals(new ObjectValue(Long.MAX_VALUE), afterMaximum.next());
    assertEquals(new ObjectValue(Long.MAX_VALUE - 1), afterMaximum.end());

    VmState exhausted = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
    exhausted.ensureRoot(program);
    exhausted.currentFrame()
        .loops
        .put(
            0,
            new VmState.RangeCursor(
                VmSnapshot.RangeKind.OBJECT, afterMaximum.next(), afterMaximum.end()));
    VmState restoredExhausted = VmState.restore(exhausted.snapshot(0));
    new PureVmFixture().execute(program, restoredExhausted);

    assertEquals(new IntegerValue(99), restoredExhausted.returnValue().orElseThrow());
  }

  @Test
  void restoredNestedLoopCursorsRemainIndependent() {
    BytecodeProgram program =
        snapshotProgram(
            List.of(
                loopInstruction(Opcode.ITERATE, 12, "outer", List.of(28)),
                loopInstruction(Opcode.ITERATE, 9, "inner", List.of(29)),
                new Instruction(Opcode.BUILD_LIST, 0),
                new Instruction(Opcode.LOAD_LOCAL, 28),
                new Instruction(Opcode.LIST_APPEND),
                new Instruction(Opcode.LOAD_LOCAL, 29),
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
        new VmState(new TaskOrigin(Map.of("outer", new IntegerValue(1), "inner", new IntegerValue(1)), 0, TaskOrigin.inferVerbLocation(Map.of("outer", new IntegerValue(1), "inner", new IntegerValue(1))),
        TaskOrigin.calledVerb(Map.of("outer", new IntegerValue(1), "inner", new IntegerValue(1))), TaskOrigin.calledVerb(Map.of("outer", new IntegerValue(1), "inner", new IntegerValue(1))), true), new TaskBudget(60_000, 5, 50));
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
    new PureVmFixture().execute(program, restored);

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
        snapshotProgram(
            List.of(
                new Instruction(Opcode.END_FINALLY),
                new Instruction(Opcode.PUSH_INTEGER, 42),
                new Instruction(Opcode.RETURN)));
    VmState fallThrough = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
    fallThrough.ensureRoot(fallThroughProgram);
    fallThrough.currentFrame().finallyContinuations.push(new VmSnapshot.FallThrough(1));
    VmState restoredFallThrough = VmState.restore(fallThrough.snapshot(0));
    new PureVmFixture().execute(fallThroughProgram, restoredFallThrough);
    assertEquals(new IntegerValue(42), restoredFallThrough.returnValue().orElseThrow());

    BytecodeProgram terminalProgram =
        snapshotProgram(List.of(new Instruction(Opcode.END_FINALLY)));
    VmState returned = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
    returned.ensureRoot(terminalProgram);
    returned.currentFrame().finallyContinuations.push(new VmSnapshot.Return(text("done")));
    VmState restoredReturned = VmState.restore(returned.snapshot(0));
    new PureVmFixture().execute(terminalProgram, restoredReturned);
    assertEquals(text("done"), restoredReturned.returnValue().orElseThrow());

    VmState raised = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
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
    new PureVmFixture().execute(terminalProgram, restoredRaised);
    assertEquals(VmState.Outcome.ERRORED, restoredRaised.outcome());
    assertEquals(ErrorValue.E_TYPE, restoredRaised.uncaughtError().orElseThrow());

    VmState uncaught = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
    uncaught.ensureRoot(terminalProgram);
    uncaught.currentFrame().finallyContinuations.push(new VmSnapshot.Uncaught(ErrorValue.E_QUOTA));
    VmState restoredUncaught = VmState.restore(uncaught.snapshot(0));
    new PureVmFixture().execute(terminalProgram, restoredUncaught);
    assertEquals(VmState.Outcome.ERRORED, restoredUncaught.outcome());
    assertEquals(ErrorValue.E_QUOTA, restoredUncaught.uncaughtError().orElseThrow());
  }

  @Test
  void restoredFinallyExitTrimsOperandsAndRemovesExitedNestedLoops() {
    BytecodeProgram program =
        snapshotProgram(
            List.of(
                new Instruction(Opcode.END_FINALLY),
                loopInstruction(Opcode.ITERATE, 5, "outer", List.of(28)),
                loopInstruction(Opcode.ITERATE, 7, "inner", List.of(29)),
                new Instruction(Opcode.PUSH_INTEGER, -1),
                new Instruction(Opcode.RETURN),
                new Instruction(Opcode.PUSH_INTEGER, -2),
                new Instruction(Opcode.RETURN),
                new Instruction(Opcode.PUSH_INTEGER, -3),
                new Instruction(Opcode.PUSH_INTEGER, 42),
                new Instruction(Opcode.RETURN)));
    VmState state = new VmState(new TaskOrigin(java.util.Map.of(), 0, new world.mongoose.banteng.value.MooValue.ObjectValue(-1),
        "", "", true), new TaskBudget(60_000, 5, 50));
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
    new PureVmFixture().execute(program, restored);

    assertEquals(new IntegerValue(42), restored.returnValue().orElseThrow());
    assertEquals(List.of(new IntegerValue(1)), List.copyOf(restored.currentFrame().operandStack));
    assertTrue(restored.currentFrame().loops.isEmpty());
  }

  private static BytecodeProgram snapshotProgram(List<Instruction> instructions) {
    return new BytecodeProgram(instructions, List.of(), "", List.of(
        "num", "obj", "str", "list", "err", "player", "this", "caller", "verb", "args", "argstr",
        "dobj", "dobjstr", "prepstr", "iobj", "iobjstr", "int", "float", "map", "anon", "waif",
        "bool", "true", "false", "seed", "value", "index", "object", "outer", "inner", "caught",
        "__banteng_create_result"));
  }

  private static Instruction loopInstruction(Opcode opcode, int target, String names, List<Integer> slots) {
    return new Instruction(opcode, OptionalLong.of(target), Optional.of(names), Optional.empty(),
        0, Optional.empty(), 0, slots);
  }

  private static StringValue text(String value) {
    return StringValue.of(value);
  }
}
