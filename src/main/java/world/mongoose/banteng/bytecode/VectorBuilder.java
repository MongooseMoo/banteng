package world.mongoose.banteng.bytecode;

import static world.mongoose.banteng.bytecode.LayoutCompiler.isBodyDescendant;
import static world.mongoose.banteng.bytecode.LayoutCompiler.referenceWidth;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import world.mongoose.banteng.bytecode.BytecodeProgram.AstPath;
import world.mongoose.banteng.bytecode.LayoutCompiler.ActiveLoopTarget;
import world.mongoose.banteng.bytecode.LayoutCompiler.GlobalState;
import world.mongoose.banteng.bytecode.LayoutCompiler.LabelReference;
import world.mongoose.banteng.bytecode.LayoutCompiler.LiteralKey;
import world.mongoose.banteng.bytecode.LayoutCompiler.PendingCall;
import world.mongoose.banteng.bytecode.LayoutCompiler.PendingCatchGroup;
import world.mongoose.banteng.bytecode.LayoutCompiler.PendingProtectedFinally;
import world.mongoose.banteng.bytecode.LayoutCompiler.PendingStructuralEntry;
import world.mongoose.banteng.bytecode.LayoutCompiler.PendingToastClause;
import world.mongoose.banteng.bytecode.LayoutCompiler.PendingToastExitTarget;
import world.mongoose.banteng.bytecode.LayoutCompiler.PendingToastFinally;
import world.mongoose.banteng.bytecode.LayoutCompiler.PendingToastHandlerGroup;
import world.mongoose.banteng.bytecode.LayoutCompiler.UnitKind;
import world.mongoose.banteng.bytecode.LayoutCompiler.VectorLayout;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.CallKind;

/** Builds one compiled Toast v17 vector layout. */
final class VectorBuilder {
  private final GlobalState global;
  private final List<UnitKind> units = new ArrayList<>();
  private final List<PendingCall> calls = new ArrayList<>();
  private final List<PendingToastHandlerGroup> handlerGroups = new ArrayList<>();
  private final List<PendingToastFinally> finallyLabels = new ArrayList<>();
  private final List<PendingStructuralEntry> activeStructures = new ArrayList<>();
  private final List<ActiveLoopTarget> activeLoops = new ArrayList<>();
  private final List<PendingToastExitTarget> exitTargets = new ArrayList<>();
  private int maximumLiteral;
  private int maximumFork;
  private int maximumVariable;
  private int currentStack;
  private int maximumStack;

  VectorBuilder(GlobalState global) {
    this.global = global;
  }

  void bytecode() {
    units.add(UnitKind.BYTE);
  }

  void extended() {
    bytecode();
    bytecode();
  }

  void literal(LiteralKey value) {
    int index = global.addLiteral(value);
    maximumLiteral = Math.max(maximumLiteral, index);
    bytecode();
    units.add(UnitKind.LITERAL);
    push(1);
  }

  void variable(int slot) {
    bytecode();
    if (slot >= 32) {
      units.add(UnitKind.VARIABLE);
      maximumVariable = Math.max(maximumVariable, slot);
      global.totalVariableReferences++;
    }
  }

  void variableOperand(int slot) {
    units.add(UnitKind.VARIABLE);
    maximumVariable = Math.max(maximumVariable, slot);
    global.totalVariableReferences++;
  }

  void label() {
    units.add(UnitKind.LABEL);
  }

  LabelReference labelReference() {
    LabelReference label = new LabelReference();
    label(label);
    return label;
  }

  LabelReference newLabel() {
    return new LabelReference();
  }

  void label(LabelReference label) {
    Objects.requireNonNull(label, "label");
    units.add(UnitKind.LABEL);
  }

  void define(LabelReference label) {
    Objects.requireNonNull(label, "label");
    if (label.targetUnit >= 0) {
      throw new IllegalStateException("Toast label target was already defined");
    }
    label.targetUnit = units.size();
  }

  PendingToastHandlerGroup handlerGroup(
      AstPath ownerPath, List<PendingToastClause> clauses) {
    PendingToastHandlerGroup group =
        new PendingToastHandlerGroup(ownerPath, List.copyOf(clauses));
    handlerGroups.add(group);
    return group;
  }

  PendingToastFinally finallyLabel(AstPath ownerPath, LabelReference label) {
    PendingToastFinally pending = new PendingToastFinally(ownerPath, label);
    finallyLabels.add(pending);
    return pending;
  }

  void stack() {
    units.add(UnitKind.STACK);
  }

  void fork(int index) {
    maximumFork = Math.max(maximumFork, index);
    units.add(UnitKind.FORK);
  }

  void call(AstPath path, CallKind kind) {
    int errorUnit = units.size();
    bytecode();
    if (kind == CallKind.BUILTIN) {
      bytecode();
    }
    int consumedOperands = kind == CallKind.BUILTIN ? 1 : 3;
    int postArgumentDepth = currentStack - consumedOperands;
    if (postArgumentDepth < 0) {
      throw new IllegalStateException("invalid Toast call operand depth");
    }
    calls.add(
        new PendingCall(
            errorUnit,
            units.size(),
            path,
            kind,
            postArgumentDepth,
            List.copyOf(activeStructures)));
  }

  void enterStructure(PendingStructuralEntry structure) {
    activeStructures.add(Objects.requireNonNull(structure, "structure"));
  }

  void leaveStructure(PendingStructuralEntry structure) {
    if (activeStructures.isEmpty() || !activeStructures.getLast().equals(structure)) {
      throw new IllegalStateException("Toast structural regions are not properly nested");
    }
    activeStructures.removeLast();
  }

  void enterLoop(ActiveLoopTarget loop) {
    activeLoops.add(Objects.requireNonNull(loop, "loop"));
  }

  void leaveLoop(ActiveLoopTarget loop) {
    if (activeLoops.isEmpty() || !activeLoops.getLast().equals(loop)) {
      throw new IllegalStateException("Toast loops are not properly nested");
    }
    activeLoops.removeLast();
  }

  ActiveLoopTarget loopTarget(Optional<String> variable) {
    if (activeLoops.isEmpty()) {
      throw new IllegalArgumentException("loop exit outside a loop");
    }
    if (variable.isEmpty()) {
      return activeLoops.getLast();
    }
    String name = variable.orElseThrow();
    for (int index = activeLoops.size() - 1; index >= 0; index--) {
      ActiveLoopTarget loop = activeLoops.get(index);
      if (loop.variableNames().contains(name)) {
        return loop;
      }
    }
    throw new IllegalArgumentException("unknown loop variable: " + name);
  }

  void exitTarget(PendingToastExitTarget target) {
    exitTargets.add(Objects.requireNonNull(target, "target"));
  }

  List<PendingToastExitTarget> exitTargetsForFinally(AstPath ownerPath, int baseDepth) {
    return exitTargets.stream()
        .filter(target -> isBodyDescendant(ownerPath, target.exitPath(), 0))
        .filter(target -> target.targetStackDepth() <= baseDepth)
        .toList();
  }

  void push(int count) {
    currentStack += count;
    maximumStack = Math.max(maximumStack, currentStack);
  }

  void pop(int count) {
    currentStack -= count;
    if (currentStack < 0) {
      throw new IllegalStateException("invalid Toast stack model");
    }
  }

  int stackDepth() {
    return currentStack;
  }

  int ordinaryStackDepth() {
    int structuralSlots = 0;
    for (PendingStructuralEntry structure : activeStructures) {
      if (structure instanceof PendingCatchGroup caught) {
        if (caught.markerDepth().isPresent()) {
          structuralSlots += 2 * caught.group().clauses().size() + 1;
        }
      } else if (structure instanceof PendingProtectedFinally) {
        structuralSlots++;
      } else {
        structuralSlots += 2;
      }
    }
    int ordinaryDepth = currentStack - structuralSlots;
    if (ordinaryDepth < 0) {
      throw new IllegalStateException("invalid Toast ordinary stack depth");
    }
    return ordinaryDepth;
  }

  VectorLayout finish() {
    if (!activeStructures.isEmpty() || !activeLoops.isEmpty()) {
      throw new IllegalStateException("Toast vector ended inside a structural region");
    }
    bytecode();
    int literalWidth = referenceWidth(Math.max(maximumLiteral, global.literals.size()));
    int forkWidth = referenceWidth(Math.max(maximumFork, global.forks.size()));
    int variableWidth =
        referenceWidth(Math.max(maximumVariable, global.totalVariableReferences));
    int stackWidth = referenceWidth(maximumStack);
    int sizeWithoutLabelExpansion = 0;
    for (UnitKind unit : units) {
      sizeWithoutLabelExpansion +=
          switch (unit) {
            case LITERAL -> literalWidth;
            case FORK -> forkWidth;
            case VARIABLE -> variableWidth;
            default -> 1;
          };
    }
    int labelWidth =
        sizeWithoutLabelExpansion <= 256
            ? 1
            : sizeWithoutLabelExpansion
                        + units.stream().filter(unit -> unit == UnitKind.LABEL).count()
                    <= 65_536
                ? 2
                : 4;
    return new VectorLayout(
        List.copyOf(units),
        List.copyOf(calls),
        List.copyOf(handlerGroups),
        List.copyOf(finallyLabels),
        literalWidth,
        forkWidth,
        variableWidth,
        labelWidth,
        stackWidth);
  }
}
