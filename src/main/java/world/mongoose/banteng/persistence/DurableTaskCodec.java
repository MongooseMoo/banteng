package world.mongoose.banteng.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.BytecodeProgram.Instruction;
import world.mongoose.banteng.bytecode.BytecodeProgram.Opcode;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.ContinuationSite;
import world.mongoose.banteng.world.QueuedTask;
import world.mongoose.banteng.world.SuspendedActivation;
import world.mongoose.banteng.world.SuspendedStackSlot;
import world.mongoose.banteng.world.SuspendedTask;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.vm.VmSnapshot;
import world.mongoose.banteng.vm.VmState;

/** Converts durable task records and VM activation state. */
public final class DurableTaskCodec {
  private DurableTaskCodec() {}

  public static BytecodeProgram restoreQueuedProgram(QueuedTask restored) {
    BytecodeProgram compiled = new MooCompiler().compile(restored.programSource());
    if (compiled.instructions().isEmpty()) {
      throw new IllegalArgumentException("queued task has no executable instruction");
    }
    int offset = Math.subtractExact(restored.firstSourceLine(), compiled.sourceLine(0));
    return offset == 0 ? compiled : shiftSourceLines(compiled, offset);
  }

  private static BytecodeProgram shiftSourceLines(BytecodeProgram program, int offset) {
    List<Instruction> instructions =
        program.instructions().stream()
            .map(
                instruction ->
                    new Instruction(
                        instruction.opcode(),
                        instruction.operand(),
                        instruction.text(),
                        instruction.handler(),
                        Math.addExact(instruction.sourceLine(), offset),
                        instruction.astPath(), instruction.localAux(), instruction.localSlots()))
            .toList();
    List<BytecodeProgram> forkVectors =
        program.forkVectors().stream()
            .map(vector -> shiftSourceLines(vector, offset))
            .toList();
    return new BytecodeProgram(instructions, forkVectors, program.source(), program.variableNames());
  }

  public static VmSnapshot.Frame importActivation(
      SuspendedActivation activation,
      int vector,
      boolean rootActivation,
      ToastV17ProgramLayout layout) {
    if (activation.languageVersion() < 4 || activation.languageVersion() > 17) {
      throw new IllegalArgumentException(
          "unsupported suspended activation language version " + activation.languageVersion());
    }
    layout = new ToastV17ProgramLayout(activation.languageVersion());
    BuiltinState builtin = importBuiltin(activation, rootActivation);

    BytecodeProgram compiled = new MooCompiler().compile(activation.programSource());
    BytecodeProgram selected =
        vector < 0
            ? compiled
            : compiled.forkVectors().get(vector);
    ToastV17ProgramModel.CallBoundary boundary =
        layout.resolve(
            activation.programSource(),
            vector,
            activation.errorCounter(),
            activation.programCounter());

    ContinuationSite site = layout.resolveContinuation(selected, boundary);
    ToastV17ProgramModel.StructuralStackShape structural =
        layout.resolveStructuralStack(
            activation.programSource(), vector, selected, boundary);
    ImportedActivationStack imported =
        importStructuralStack(structural, activation.operandStack());
    Map<String, MooValue> locals = VmState.constantLocals(selected);
    activation.locals().forEach((name, value) -> {
      String normalized = name.toLowerCase(java.util.Locale.ROOT);
      if (value.isPresent()) locals.put(normalized, value.orElseThrow());
      else locals.remove(normalized);
    });
    return new VmSnapshot.Frame(
        site.call().program(),
        imported.operandStack(),
        imported.indices(),
        locals,
        imported.handlers(),
        imported.finallyStates(),
        imported.loops(),
        builtin.mode(),
        activation.receiver(),
        activation.verbLocation(),
        activation.verb(),
        activation.verbNames(),
        builtin.create(),
        builtin.recycle(),
        builtin.move(),
        builtin.destination(),
        builtin.position(),
        activation.programmer(),
        activation.debug(),
        activation.threadMode(),
        site.call().resumeInstructionPointer(),
        Optional.empty());
  }

  private record BuiltinState(VmSnapshot.ReturnMode mode, Optional<MooValue> create,
      OptionalLong recycle, OptionalLong move, OptionalLong destination, OptionalLong position) {}

  private static BuiltinState importBuiltin(SuspendedActivation activation, boolean root) {
    VmSnapshot.ReturnMode mode = root ? VmSnapshot.ReturnMode.ROOT : VmSnapshot.ReturnMode.VERB;
    Optional<MooValue> create = Optional.empty();
    OptionalLong recycle = OptionalLong.empty(), move = OptionalLong.empty();
    OptionalLong destination = OptionalLong.empty(), position = OptionalLong.empty();
    if (activation.builtinFunctionCounter() != 0) {
      SuspendedActivation.BuiltinContinuation payload = activation.builtinContinuation().orElseThrow();
      while (payload instanceof SuspendedActivation.CallFunctionContinuation call) payload = call.function();
      switch (payload) {
        case SuspendedActivation.EvalContinuation ignored -> mode = VmSnapshot.ReturnMode.EVAL;
        case SuspendedActivation.CreateContinuation created ->
            create = Optional.of(new MooValue.ObjectValue(created.object()));
        case SuspendedActivation.RecycleContinuation recycled -> recycle = OptionalLong.of(recycled.object());
        case SuspendedActivation.MoveContinuation moved -> {
          move = OptionalLong.of(moved.object()); destination = OptionalLong.of(moved.destination());
          position = OptionalLong.of(moved.position());
          mode = switch (Math.toIntExact(activation.builtinFunctionCounter())) {
            case 2 -> VmSnapshot.ReturnMode.MOVE_ACCEPT;
            case 3 -> VmSnapshot.ReturnMode.MOVE_EXIT;
            case 4 -> VmSnapshot.ReturnMode.MOVE_ENTER;
            default -> throw new IllegalArgumentException("invalid move continuation counter");
          };
        }
        case SuspendedActivation.CallFunctionContinuation ignored -> throw new AssertionError();
      }
    }
    return new BuiltinState(mode, create, recycle, move, destination, position);
  }

  private static ImportedActivationStack importStructuralStack(
      ToastV17ProgramModel.StructuralStackShape shape,
      List<SuspendedStackSlot> topToBase) {
    if (shape.postArgumentDepth() != topToBase.size()) {
      throw new IllegalArgumentException(
          "v17 runtime stack depth "
              + topToBase.size()
              + " disagrees with structural depth "
              + shape.postArgumentDepth());
    }
    List<SuspendedStackSlot> baseToTop = new ArrayList<>(topToBase);
    Collections.reverse(baseToTop);
    boolean[] consumed = new boolean[baseToTop.size()];
    List<ImportedHandlerGroup> handlerGroups = new ArrayList<>();
    List<ImportedFinally> finallyStates = new ArrayList<>();
    Map<Integer, VmSnapshot.LoopState> loops = new LinkedHashMap<>();

    for (ToastV17ProgramModel.StructuralStackEntry entry : shape.entriesBaseToTop()) {
      switch (entry) {
        case ToastV17ProgramModel.CatchGroup group ->
            importCatchGroup(group, baseToTop, consumed, handlerGroups);
        case ToastV17ProgramModel.ProtectedFinally protectedFinally -> {
          requireControl(
              baseToTop,
              consumed,
              protectedFinally.markerDepth(),
              V17Format.ValueTag.FINALLY.code(),
              protectedFinally.handlerLabelProgramCounter(),
              "protected finally");
          handlerGroups.add(
              new ImportedHandlerGroup(
                  protectedFinally.baseDepth(),
                  List.of(
                      new ImportedHandler(
                          protectedFinally.ownerControl(),
                          VmSnapshot.HandlerPhase.TRY))));
        }
        case ToastV17ProgramModel.FinallyContinuation continuation -> {
          MooValue reason =
              requireValue(
                  baseToTop,
                  consumed,
                  continuation.reasonDepth(),
                  "finally reason");
          MooValue value =
              requireValue(
                  baseToTop,
                  consumed,
                  continuation.valueDepth(),
                  "finally value");
          finallyStates.add(
              new ImportedFinally(
                  continuation.baseDepth(),
                  importFinallyState(continuation, reason, value)));
        }
        case ToastV17ProgramModel.CollectionLoop loop ->
            loops.put(
                loop.control().instructionIndex(),
                importCollectionLoop(loop, baseToTop, consumed));
        case ToastV17ProgramModel.RangeLoop loop ->
            loops.put(
                loop.control().instructionIndex(),
                importRangeLoop(loop, baseToTop, consumed));
        case ToastV17ProgramModel.IndexContext index -> {
          if (index.keyDepth().isPresent()) {
            requireValue(baseToTop, consumed, index.baseDepth(), "indexed parent collection");
            requireValue(baseToTop, consumed, index.keyDepth().getAsInt(), "indexed parent key");
          }
        }
      }
    }

    handlerGroups.sort(
        java.util.Comparator.comparingInt(ImportedHandlerGroup::baseDepth).reversed());
    List<VmSnapshot.HandlerState> handlers =
        handlerGroups.stream()
            .flatMap(
                group -> {
                  int operandDepth = translatedDepth(consumed, group.baseDepth());
                  return group.handlers().stream()
                      .map(
                          handler ->
                              handlerState(
                                  handler.control(), operandDepth, handler.phase()));
                })
            .toList();
    finallyStates.sort(
        java.util.Comparator.comparingInt(ImportedFinally::baseDepth).reversed());
    List<VmSnapshot.FinallyState> finalizers =
        finallyStates.stream().map(ImportedFinally::state).toList();

    List<MooValue> ordinary = new ArrayList<>();
    for (int topIndex = 0; topIndex < topToBase.size(); topIndex++) {
      int baseDepth = topToBase.size() - topIndex - 1;
      if (!consumed[baseDepth]) {
        SuspendedStackSlot slot = topToBase.get(topIndex);
        if (slot.value().isEmpty()) {
          throw new IllegalArgumentException(
              "unclaimed v17 runtime control tag " + V17Format.stackTag(slot.kind()));
        }
        ordinary.add(slot.value().orElseThrow());
      }
    }
    List<VmSnapshot.IndexState> indices = new ArrayList<>();
    for (ToastV17ProgramModel.StructuralStackEntry entry : shape.entriesBaseToTop()) {
      if (entry instanceof ToastV17ProgramModel.IndexContext index) {
        indices.addFirst(new VmSnapshot.IndexState(
            baseToTop.get(index.baseDepth()).value().orElseThrow(),
            index.keyDepth().isPresent()
                ? baseToTop.get(index.keyDepth().getAsInt()).value() : Optional.empty(),
            translatedDepth(consumed, index.baseDepth()) + 1));
      }
    }
    return new ImportedActivationStack(ordinary, handlers, finalizers, loops, indices);
  }

  private static void importCatchGroup(
      ToastV17ProgramModel.CatchGroup group,
      List<SuspendedStackSlot> baseToTop,
      boolean[] consumed,
      List<ImportedHandlerGroup> handlerGroups) {
    List<ImportedHandler> handlers = new ArrayList<>();
    switch (group.phase()) {
      case PROTECTED -> {
        for (int index = 0; index < group.clauses().size(); index++) {
          ToastV17ProgramModel.ToastHandlerClause clause = group.clauses().get(index);
          requireExactValue(
              baseToTop,
              consumed,
              group.baseDepth() + index * 2,
              toastSelectorValue(clause.selector()),
              "catch selector");
          requireExactValue(
              baseToTop,
              consumed,
              group.baseDepth() + index * 2 + 1,
              new IntegerValue(clause.handlerLabelProgramCounter()),
              "catch handler label");
          handlers.add(
              new ImportedHandler(
                  group.clauseControls().get(index), VmSnapshot.HandlerPhase.TRY));
        }
        requireControl(
            baseToTop,
            consumed,
            group.markerDepth().orElseThrow(),
            V17Format.ValueTag.CATCH.code(),
            group.clauses().size(),
            "catch marker");
        handlers.add(
            new ImportedHandler(group.ownerControl(), VmSnapshot.HandlerPhase.TRY));
      }
      case EXCEPT_CLAUSE ->
          handlers.add(
              new ImportedHandler(group.ownerControl(), VmSnapshot.HandlerPhase.CATCH));
      case EXPRESSION_FALLBACK -> {
        int active = group.activeClauseIndex().orElseThrow();
        handlers.add(
            new ImportedHandler(
                group.clauseControls().get(active), VmSnapshot.HandlerPhase.CATCH));
        handlers.add(
            new ImportedHandler(group.ownerControl(), VmSnapshot.HandlerPhase.CATCH));
      }
      case FINALLY_HANDLER -> throw new IllegalArgumentException("catch group is in finally phase");
    }
    handlerGroups.add(new ImportedHandlerGroup(group.baseDepth(), handlers));
  }

  private static VmSnapshot.HandlerState handlerState(
      ToastV17ProgramModel.BantengHandlerControl control,
      int operandDepth,
      VmSnapshot.HandlerPhase phase) {
    return new VmSnapshot.HandlerState(control.specification(), operandDepth, phase);
  }

  private static MooValue toastSelectorValue(
      ToastV17ProgramModel.ToastErrorSelector selector) {
    if (selector.catchesAny()) {
      return new IntegerValue(0);
    }
    return new ListValue(
        selector.errors().stream()
            .map(name -> ErrorValue.valueOf(name.toUpperCase(java.util.Locale.ROOT)))
            .toList());
  }

  private static VmSnapshot.FinallyState importFinallyState(
      ToastV17ProgramModel.FinallyContinuation continuation,
      MooValue reasonValue,
      MooValue value) {
    if (!(reasonValue instanceof IntegerValue reason)) {
      throw new IllegalArgumentException("v17 finally reason is not an integer");
    }
    return switch (Math.toIntExact(reason.value())) {
      case 0 -> new VmSnapshot.FallThrough(continuation.ownerControl().specification().endTarget());
      case 1 ->
          value instanceof ListValue exception
              ? new VmSnapshot.Raise(exception)
              : throwFinally("v17 raise continuation is not a tuple");
      case 2 -> new VmSnapshot.Uncaught(value);
      case 3 -> new VmSnapshot.Return(value);
      case 4 -> throw new IllegalArgumentException("v17 FIN_ABORT continuation is unsupported");
      case 5 -> importFinallyExit(continuation, value);
      default -> throw new IllegalArgumentException("unknown v17 finally reason " + reason.value());
    };
  }

  private static VmSnapshot.FinallyState importFinallyExit(
      ToastV17ProgramModel.FinallyContinuation continuation, MooValue value) {
    if (!(value instanceof ListValue exit)
        || exit.size() != 2
        || !(exit.elements().get(0) instanceof IntegerValue depth)
        || !(exit.elements().get(1) instanceof IntegerValue programCounter)) {
      throw new IllegalArgumentException("v17 exit continuation requires [depth, pc]");
    }
    ToastV17ProgramModel.ToastExitTarget target =
        continuation.resolveToastExitTarget(
            depth.value(), programCounter.value());
    return new VmSnapshot.Exit(
        target.bantengOperandDepth(),
        target.bantengControl().targetInstructionPointer());
  }

  private static VmSnapshot.FinallyState throwFinally(String message) {
    throw new IllegalArgumentException(message);
  }

  private static VmSnapshot.LoopState importCollectionLoop(
      ToastV17ProgramModel.CollectionLoop loop,
      List<SuspendedStackSlot> baseToTop,
      boolean[] consumed) {
    MooValue base =
        requireValue(baseToTop, consumed, loop.baseDepth(), "collection loop base");
    SuspendedStackSlot iterator =
        requireSlot(baseToTop, consumed, loop.iteratorDepth(), "collection loop iterator");
    VmSnapshot.CollectionKind kind = collectionKind(loop, base);
    Optional<MooValue> next;
    if (kind == VmSnapshot.CollectionKind.MAP && V17Format.stackTag(iterator.kind()) == V17Format.ValueTag.NONE.code()) {
      next = Optional.empty();
    } else if (iterator.value().isPresent()) {
      next = iterator.value();
    } else {
      throw new IllegalArgumentException("invalid v17 collection loop iterator");
    }
    return new VmSnapshot.CollectionLoop(kind, base, next);
  }

  private static VmSnapshot.CollectionKind collectionKind(
      ToastV17ProgramModel.CollectionLoop loop, MooValue base) {
    VmSnapshot.CollectionKind actual =
        switch (base) {
          case ListValue ignored -> VmSnapshot.CollectionKind.LIST;
          case world.mongoose.banteng.value.MooValue.StringValue ignored -> VmSnapshot.CollectionKind.STRING;
          case world.mongoose.banteng.value.MooValue.MapValue ignored -> VmSnapshot.CollectionKind.MAP;
          default -> throw new IllegalArgumentException("unsupported v17 collection loop base");
        };
    if (loop.staticallyKnownKind().isPresent()
        && !loop.staticallyKnownKind().orElseThrow().name().equals(actual.name())) {
      throw new IllegalArgumentException("v17 collection loop kind disagrees with its base");
    }
    return actual;
  }

  private static VmSnapshot.LoopState importRangeLoop(
      ToastV17ProgramModel.RangeLoop loop,
      List<SuspendedStackSlot> baseToTop,
      boolean[] consumed) {
    MooValue next = requireValue(baseToTop, consumed, loop.nextDepth(), "range next");
    MooValue end = requireValue(baseToTop, consumed, loop.endDepth(), "range end");
    VmSnapshot.RangeKind kind =
        switch (next) {
          case IntegerValue ignored when end instanceof IntegerValue -> VmSnapshot.RangeKind.INTEGER;
          case world.mongoose.banteng.value.MooValue.ObjectValue ignored
              when end instanceof world.mongoose.banteng.value.MooValue.ObjectValue -> VmSnapshot.RangeKind.OBJECT;
          default -> throw new IllegalArgumentException("v17 range values have different kinds");
        };
    if (loop.staticallyKnownKind().isPresent()
        && !loop.staticallyKnownKind().orElseThrow().name().equals(kind.name())) {
      throw new IllegalArgumentException("v17 range kind disagrees with its values");
    }
    return new VmSnapshot.RangeLoop(kind, next, end);
  }

  private static void requireControl(
      List<SuspendedStackSlot> baseToTop,
      boolean[] consumed,
      int depth,
      int tag,
      long controlValue,
      String description) {
    SuspendedStackSlot actual = requireSlot(baseToTop, consumed, depth, description);
    if (V17Format.stackTag(actual.kind()) != tag || actual.controlValue() != controlValue) {
      throw new IllegalArgumentException(description + " disagrees with structural layout");
    }
  }

  private static void requireExactValue(
      List<SuspendedStackSlot> baseToTop,
      boolean[] consumed,
      int depth,
      MooValue expected,
      String description) {
    MooValue actual = requireValue(baseToTop, consumed, depth, description);
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException(description + " disagrees with structural layout");
    }
  }

  private static MooValue requireValue(
      List<SuspendedStackSlot> baseToTop,
      boolean[] consumed,
      int depth,
      String description) {
    SuspendedStackSlot slot = requireSlot(baseToTop, consumed, depth, description);
    return slot.value().orElseThrow(
        () -> new IllegalArgumentException(description + " is not a MOO value"));
  }

  private static SuspendedStackSlot requireSlot(
      List<SuspendedStackSlot> baseToTop,
      boolean[] consumed,
      int depth,
      String description) {
    if (depth < 0 || depth >= baseToTop.size() || consumed[depth]) {
      throw new IllegalArgumentException("invalid or overlapping " + description + " depth");
    }
    consumed[depth] = true;
    return baseToTop.get(depth);
  }

  private static int translatedDepth(boolean[] consumed, int rawDepth) {
    int result = 0;
    for (int depth = 0; depth < rawDepth; depth++) {
      if (!consumed[depth]) {
        result++;
      }
    }
    return result;
  }

  public static QueuedTask exportQueuedTask(
      long taskId, long scheduledEpochMilli, BytecodeProgram program, VmSnapshot snapshot,
      long taskPlayer) {
    if (program.source().isEmpty()) {
      throw new IllegalStateException("queued fork has no durable source");
    }
    return new QueuedTask(
        taskId,
        Math.floorDiv(scheduledEpochMilli, 1_000L),
        program.instructions().isEmpty() ? 1 : program.sourceLine(0),
        snapshot.initialCalledVerb(),
        snapshot.initialFullVerbName(),
        program.source(),
        snapshot.initialLocals(),
        snapshot.initialProgrammer(),
        snapshot.initialVerbLocation(),
        taskPlayer,
        snapshot.frames().isEmpty() || snapshot.frames().getFirst().debug(),
        snapshot.frames().isEmpty() || snapshot.frames().getFirst().threadMode());
  }

  public static SuspendedTask durableSuspension(
      long taskId, long scheduledEpochMilli, VmSnapshot snapshot, long taskPlayer,
      MooValue resumeValue) {
    if (!snapshot.anonymousCollectionDeferrals().isEmpty()) {
      throw new IllegalStateException(
          "suspended task has anonymous collection deferrals with no v17 representation");
    }
    if (snapshot.frames().isEmpty()) {
      throw new IllegalStateException("suspended task has no activation frames");
    }
    List<SuspendedActivation> rootToCurrent = new ArrayList<>();
    List<VmSnapshot.Frame> frames = new ArrayList<>(snapshot.frames());
    Collections.reverse(frames);
    for (VmSnapshot.Frame frame : frames) {
      rootToCurrent.add(exportActivation(frame, taskPlayer));
    }
    return new SuspendedTask(
        taskId,
        Math.floorDiv(scheduledEpochMilli, 1_000L),
        resumeValue,
        snapshot.taskLocal(),
        -1,
        0,
        snapshot.maxStackDepth(),
        Optional.empty(),
        rootToCurrent);
  }

  private static List<SuspendedStackSlot> exportStructuralStack(
      ToastV17ProgramModel.StructuralStackShape shape, VmSnapshot.Frame frame) {
    SuspendedStackSlot[] baseToTop = new SuspendedStackSlot[shape.postArgumentDepth()];
    boolean[] structural = new boolean[baseToTop.length];
    List<ImportedHandlerGroup> expectedHandlerGroups = new ArrayList<>();
    List<ToastV17ProgramModel.StructuralStackEntry> innerToOuter =
        new ArrayList<>(shape.entriesBaseToTop());
    innerToOuter.sort(
        java.util.Comparator.comparingInt(
                ToastV17ProgramModel.StructuralStackEntry::baseDepth)
            .reversed());
    int finallyIndex = 0;
    int indexContext = 0;
    Map<Integer, VmSnapshot.LoopState> remainingLoops =
        new LinkedHashMap<>(frame.loops());

    for (ToastV17ProgramModel.StructuralStackEntry entry : innerToOuter) {
      switch (entry) {
        case ToastV17ProgramModel.IndexContext index -> {
          if (indexContext >= frame.indexCollections().size()) {
            throw new IllegalStateException("missing index context");
          }
          VmSnapshot.IndexState saved = frame.indexCollections().get(indexContext++);
          if (index.keyDepth().isPresent()) {
            putValue(baseToTop, structural, index.baseDepth(), saved.collection(), "indexed parent collection");
            putValue(baseToTop, structural, index.keyDepth().getAsInt(), saved.key().orElseThrow(), "indexed parent key");
          }
        }
        case ToastV17ProgramModel.CatchGroup group -> {
          if (group.phase() == ToastV17ProgramModel.StructuralPhase.PROTECTED) {
            for (int index = 0; index < group.clauses().size(); index++) {
              ToastV17ProgramModel.ToastHandlerClause clause = group.clauses().get(index);
              putValue(
                  baseToTop,
                  structural,
                  group.baseDepth() + index * 2,
                  toastSelectorValue(clause.selector()),
                  "catch selector");
              putValue(
                  baseToTop,
                  structural,
                  group.baseDepth() + index * 2 + 1,
                  new IntegerValue(clause.handlerLabelProgramCounter()),
                  "catch handler label");
            }
            putControl(
                baseToTop,
                structural,
                group.markerDepth().orElseThrow(),
                V17Format.ValueTag.CATCH.code(),
                group.clauses().size(),
                "catch marker");
          }
          expectedHandlerGroups.add(exportedCatchHandlers(group));
        }
        case ToastV17ProgramModel.ProtectedFinally protectedFinally -> {
          putControl(
              baseToTop,
              structural,
              protectedFinally.markerDepth(),
              V17Format.ValueTag.FINALLY.code(),
              protectedFinally.handlerLabelProgramCounter(),
              "protected finally");
          expectedHandlerGroups.add(
              new ImportedHandlerGroup(
                  protectedFinally.baseDepth(),
                  List.of(
                      new ImportedHandler(
                          protectedFinally.ownerControl(),
                          VmSnapshot.HandlerPhase.TRY))));
        }
        case ToastV17ProgramModel.FinallyContinuation continuation -> {
          if (finallyIndex >= frame.finallyStates().size()) {
            throw new IllegalStateException("missing Banteng finally continuation");
          }
          exportFinallyState(
              continuation,
              frame.finallyStates().get(finallyIndex++),
              baseToTop,
              structural);
        }
        case ToastV17ProgramModel.CollectionLoop loop -> {
          VmSnapshot.LoopState state = remainingLoops.remove(loop.control().instructionIndex());
          if (!(state instanceof VmSnapshot.CollectionLoop collection)) {
            throw new IllegalStateException("missing Banteng collection-loop state");
          }
          exportCollectionLoop(loop, collection, baseToTop, structural);
        }
        case ToastV17ProgramModel.RangeLoop loop -> {
          VmSnapshot.LoopState state = remainingLoops.remove(loop.control().instructionIndex());
          if (!(state instanceof VmSnapshot.RangeLoop range)) {
            throw new IllegalStateException("missing Banteng range-loop state");
          }
          exportRangeLoop(loop, range, baseToTop, structural);
        }
      }
    }
    if (finallyIndex != frame.finallyStates().size()) {
      throw new IllegalStateException("unclaimed Banteng finally continuation");
    }
    if (!remainingLoops.isEmpty()) {
      throw new IllegalStateException("unclaimed Banteng loop state");
    }
    validateExportedHandlers(expectedHandlerGroups, frame.handlers(), structural,
        frame.program().variableNames());
    fillOrdinarySlots(baseToTop, structural, frame.operandStack());
    List<SuspendedStackSlot> topToBase = new ArrayList<>(List.of(baseToTop));
    Collections.reverse(topToBase);
    return List.copyOf(topToBase);
  }

  private static ImportedHandlerGroup exportedCatchHandlers(
      ToastV17ProgramModel.CatchGroup group) {
    List<ImportedHandler> handlers = new ArrayList<>();
    switch (group.phase()) {
      case PROTECTED -> {
        group.clauseControls().forEach(
            control ->
                handlers.add(
                    new ImportedHandler(control, VmSnapshot.HandlerPhase.TRY)));
        handlers.add(
            new ImportedHandler(group.ownerControl(), VmSnapshot.HandlerPhase.TRY));
      }
      case EXCEPT_CLAUSE ->
          handlers.add(
              new ImportedHandler(group.ownerControl(), VmSnapshot.HandlerPhase.CATCH));
      case EXPRESSION_FALLBACK -> {
        handlers.add(
            new ImportedHandler(
                group.clauseControls().get(group.activeClauseIndex().orElseThrow()),
                VmSnapshot.HandlerPhase.CATCH));
        handlers.add(
            new ImportedHandler(group.ownerControl(), VmSnapshot.HandlerPhase.CATCH));
      }
      case FINALLY_HANDLER -> throw new IllegalStateException("catch group is in finally phase");
    }
    return new ImportedHandlerGroup(group.baseDepth(), handlers);
  }

  private static void validateExportedHandlers(
      List<ImportedHandlerGroup> groups,
      List<VmSnapshot.HandlerState> actualHandlers,
      boolean[] structural,
      List<String> variableNames) {
    groups.sort(
        java.util.Comparator.comparingInt(ImportedHandlerGroup::baseDepth).reversed());
    int actualIndex = 0;
    for (ImportedHandlerGroup group : groups) {
      int operandDepth = translatedDepth(structural, group.baseDepth());
      for (ImportedHandler expected : group.handlers()) {
        if (actualIndex >= actualHandlers.size()) {
          throw new IllegalStateException("missing Banteng handler state");
        }
        VmSnapshot.HandlerState actual = actualHandlers.get(actualIndex++);
        BytecodeProgram.HandlerSpec canonical = expected.control().specification();
        BytecodeProgram.HandlerSpec rebound = new BytecodeProgram.HandlerSpec(
            canonical.catchTarget(), canonical.catchVariable(), canonical.catchesAny(),
            canonical.caughtErrors(), canonical.structuredCatchBinding(), canonical.finallyTarget(),
            canonical.endTarget(), canonical.catchVariable().map(variableNames::indexOf).orElse(-1));
        if (!actual.specification().equals(rebound)
            || actual.operandDepth() != operandDepth
            || actual.phase() != expected.phase()) {
          throw new IllegalStateException("Banteng handler disagrees with v17 structure");
        }
      }
    }
    if (actualIndex != actualHandlers.size()) {
      throw new IllegalStateException("unclaimed Banteng handler state");
    }
  }

  private static void fillOrdinarySlots(
      SuspendedStackSlot[] baseToTop,
      boolean[] structural,
      List<MooValue> operandTopToBase) {
    int ordinaryCount = 0;
    for (boolean occupied : structural) {
      if (!occupied) {
        ordinaryCount++;
      }
    }
    if (ordinaryCount != operandTopToBase.size()) {
      throw new IllegalStateException(
          "Banteng operand depth "
              + operandTopToBase.size()
              + " disagrees with v17 ordinary depth "
              + ordinaryCount);
    }
    int operandIndex = operandTopToBase.size() - 1;
    for (int depth = 0; depth < baseToTop.length; depth++) {
      if (!structural[depth]) {
        baseToTop[depth] =
            new SuspendedStackSlot(
                Optional.of(operandTopToBase.get(operandIndex--)), SuspendedStackSlot.Kind.VALUE, 0);
      }
    }
  }

  private static void putValue(
      SuspendedStackSlot[] baseToTop,
      boolean[] structural,
      int depth,
      MooValue value,
      String description) {
    putSlot(
        baseToTop,
        structural,
        depth,
        new SuspendedStackSlot(Optional.of(value), SuspendedStackSlot.Kind.VALUE, 0),
        description);
  }

  private static void putControl(
      SuspendedStackSlot[] baseToTop,
      boolean[] structural,
      int depth,
      int tag,
      long value,
      String description) {
    putSlot(
        baseToTop,
        structural,
        depth,
        new SuspendedStackSlot(Optional.empty(), V17Format.stackKind(tag), value),
        description);
  }

  private static void putSlot(
      SuspendedStackSlot[] baseToTop,
      boolean[] structural,
      int depth,
      SuspendedStackSlot slot,
      String description) {
    if (depth < 0 || depth >= baseToTop.length || structural[depth]) {
      throw new IllegalStateException("invalid or overlapping " + description + " depth");
    }
    baseToTop[depth] = slot;
    structural[depth] = true;
  }

  private static void exportFinallyState(
      ToastV17ProgramModel.FinallyContinuation continuation,
      VmSnapshot.FinallyState state,
      SuspendedStackSlot[] baseToTop,
      boolean[] structural) {
    long reason;
    MooValue value;
    switch (state) {
      case VmSnapshot.FallThrough fallThrough -> {
        if (fallThrough.target()
            != continuation.ownerControl().specification().endTarget()) {
          throw new IllegalStateException("Banteng finally fall-through target disagrees");
        }
        reason = 0;
        value = new IntegerValue(0);
      }
      case VmSnapshot.Raise raise -> {
        reason = 1;
        value = raise.exception();
      }
      case VmSnapshot.Uncaught uncaught -> {
        reason = 2;
        value = uncaught.value();
      }
      case VmSnapshot.Return returned -> {
        reason = 3;
        value = returned.value();
      }
      case VmSnapshot.Exit exit -> {
        reason = 5;
        ToastV17ProgramModel.ToastExitTarget target =
            continuation.resolveBantengExitTarget(exit.operandDepth(), exit.target());
        value =
            new ListValue(
                List.of(
                    new IntegerValue(target.targetStackDepth()),
                    new IntegerValue(target.targetProgramCounter())));
      }
    }
    putValue(
        baseToTop,
        structural,
        continuation.reasonDepth(),
        new IntegerValue(reason),
        "finally reason");
    putValue(
        baseToTop,
        structural,
        continuation.valueDepth(),
        value,
        "finally value");
  }

  private static void exportCollectionLoop(
      ToastV17ProgramModel.CollectionLoop shape,
      VmSnapshot.CollectionLoop state,
      SuspendedStackSlot[] baseToTop,
      boolean[] structural) {
    if (shape.staticallyKnownKind().isPresent()
        && !shape.staticallyKnownKind().orElseThrow().name().equals(state.kind().name())) {
      throw new IllegalStateException("Banteng collection-loop kind disagrees with v17 structure");
    }
    putValue(
        baseToTop,
        structural,
        shape.baseDepth(),
        state.base(),
        "collection loop base");
    if (state.kind() == VmSnapshot.CollectionKind.MAP && state.next().isEmpty()) {
      putControl(
          baseToTop,
          structural,
          shape.iteratorDepth(),
          V17Format.ValueTag.NONE.code(),
          0,
          "collection loop iterator");
    } else {
      putValue(
          baseToTop,
          structural,
          shape.iteratorDepth(),
          state.next().orElseThrow(
              () -> new IllegalStateException("missing collection-loop cursor")),
          "collection loop iterator");
    }
  }

  private static void exportRangeLoop(
      ToastV17ProgramModel.RangeLoop shape,
      VmSnapshot.RangeLoop state,
      SuspendedStackSlot[] baseToTop,
      boolean[] structural) {
    if (shape.staticallyKnownKind().isPresent()
        && !shape.staticallyKnownKind().orElseThrow().name().equals(state.kind().name())) {
      throw new IllegalStateException("Banteng range-loop kind disagrees with v17 structure");
    }
    putValue(
        baseToTop, structural, shape.nextDepth(), state.next(), "range next");
    putValue(baseToTop, structural, shape.endDepth(), state.end(), "range end");
  }

  private static SuspendedActivation exportActivation(VmSnapshot.Frame frame, long taskPlayer) {
    if (frame.anonymousRecycleTarget().isPresent()) {
      throw new IllegalStateException("VM frame has no direct v17 activation representation");
    }
    int callIndex = Math.subtractExact(frame.instructionPointer(), 1);
    Instruction call = frame.program().instructions().get(callIndex);
    if ((call.opcode() != Opcode.CALL && call.opcode() != Opcode.CALL_VERB)
        || call.astPath().isEmpty()) {
      throw new IllegalStateException("suspended VM is not positioned after a structural call");
    }
    BytecodeProgram canonical = new MooCompiler().compile(frame.program().source());
    if (callIndex >= canonical.instructions().size()) {
      throw new IllegalStateException("suspended call is absent from canonical source");
    }
    Instruction canonicalCall = canonical.instructions().get(callIndex);
    if (canonicalCall.opcode() != call.opcode() || canonicalCall.astPath().isEmpty()) {
      throw new IllegalStateException("suspended call disagrees with canonical source structure");
    }
    ToastV17ProgramLayout layout = new ToastV17ProgramLayout();
    List<ToastV17ProgramModel.CallBoundary> boundaries =
        layout.callBoundaries(frame.program().source(), -1).stream()
            .filter(boundary -> boundary.astPath().equals(canonicalCall.astPath().orElseThrow()))
            .toList();
    if (boundaries.size() != 1) {
      throw new IllegalStateException(
          "suspended call resolved " + boundaries.size() + " v17 boundaries");
    }
    ToastV17ProgramModel.CallBoundary boundary = boundaries.getFirst();
    ToastV17ProgramModel.StructuralStackShape structural =
        layout.resolveStructuralStack(frame.program().source(), -1, canonical, boundary);
    List<SuspendedStackSlot> stack = exportStructuralStack(structural, frame);
    Map<String, Optional<MooValue>> locals = new LinkedHashMap<>();
    for (String name : frame.program().variableNames()) {
      locals.put(name, Optional.ofNullable(frame.locals().get(name)));
    }
    String verb =
        frame.locals().get("verb") instanceof world.mongoose.banteng.value.MooValue.StringValue value
            ? value.text()
            : "";
    Optional<SuspendedActivation.BuiltinContinuation> builtin = exportBuiltin(frame);
    long builtinCounter = builtin.isEmpty() ? 0 : switch (frame.returnMode()) {
      case MOVE_EXIT -> 3;
      case MOVE_ENTER -> 4;
      default -> 2;
    };
    return new SuspendedActivation(
        17,
        frame.program().source(),
        locals,
        stack,
        frame.receiver(),
        frame.verbLocation(),
        frame.threadMode(),
        taskPlayer,
        frame.programmer(),
        true,
        verb,
        verb,
        Optional.empty(),
        boundary.programCounter(),
        builtinCounter,
        boundary.errorProgramCounter(), builtin);
  }

  private static Optional<SuspendedActivation.BuiltinContinuation> exportBuiltin(VmSnapshot.Frame frame) {
    if (frame.returnMode() == VmSnapshot.ReturnMode.EVAL) {
      return Optional.of(new SuspendedActivation.EvalContinuation());
    }
    if (frame.createReturnOverride().isPresent()) {
      MooValue.ObjectValue object = (MooValue.ObjectValue) frame.createReturnOverride().orElseThrow();
      return Optional.of(new SuspendedActivation.CreateContinuation(false, object.value()));
    }
    if (frame.recycleTarget().isPresent()) {
      return Optional.of(new SuspendedActivation.RecycleContinuation(frame.recycleTarget().orElseThrow()));
    }
    if (frame.moveObject().isPresent()) {
      return Optional.of(new SuspendedActivation.MoveContinuation(frame.moveObject().orElseThrow(),
          frame.moveDestination().orElseThrow(), frame.movePosition().orElseThrow()));
    }
    return Optional.empty();
  }

  private record ImportedActivationStack(
      List<MooValue> operandStack,
      List<VmSnapshot.HandlerState> handlers,
      List<VmSnapshot.FinallyState> finallyStates,
      Map<Integer, VmSnapshot.LoopState> loops,
      List<VmSnapshot.IndexState> indices) {
    ImportedActivationStack {
      operandStack = List.copyOf(operandStack);
      handlers = List.copyOf(handlers);
      finallyStates = List.copyOf(finallyStates);
      loops = Collections.unmodifiableMap(new LinkedHashMap<>(loops));
    }
  }

  private record ImportedHandler(
      ToastV17ProgramModel.BantengHandlerControl control,
      VmSnapshot.HandlerPhase phase) {}

  private record ImportedHandlerGroup(int baseDepth, List<ImportedHandler> handlers) {
    ImportedHandlerGroup {
      handlers = List.copyOf(handlers);
    }
  }

  private record ImportedFinally(int baseDepth, VmSnapshot.FinallyState state) {}
}
