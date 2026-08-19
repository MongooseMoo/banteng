package world.mongoose.banteng.bytecode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import world.mongoose.banteng.bytecode.BytecodeProgram.AstPath;
import world.mongoose.banteng.bytecode.BytecodeProgram.HandlerSpec;
import world.mongoose.banteng.bytecode.BytecodeProgram.Opcode;

/** Typed data exchanged by the Toast v17 layout compiler and query facade. */
public final class ToastV17ProgramModel {
  private ToastV17ProgramModel() {}

  /** The call opcode kind at one serialized continuation boundary. */
  public enum CallKind {
    BUILTIN,
    VERB
  }

  /** One exact Toast byte boundary and its stable parser AST identity. */
  public record CallBoundary(
      int vector, int errorProgramCounter, int programCounter, AstPath astPath, CallKind kind) {
    public CallBoundary {
      Objects.requireNonNull(astPath, "astPath");
      Objects.requireNonNull(kind, "kind");
    }
  }

  /** Unique Banteng owner and resume boundary for one resolved parser AST call path. */
  public record BantengCallSite(
      BytecodeProgram program, int callInstructionIndex, int resumeInstructionPointer) {
    public BantengCallSite {
      Objects.requireNonNull(program, "program");
      if (callInstructionIndex < 0 || resumeInstructionPointer != callInstructionIndex + 1) {
        throw new IllegalArgumentException("invalid Banteng call boundary");
      }
    }
  }

  /** Execution phase for one structurally active handler. */
  public enum HandlerPhase {
    TRY
  }

  /** One active Banteng handler at an imported continuation boundary. */
  public record EnclosingHandler(
      int instructionIndex,
      HandlerSpec specification,
      AstPath astPath,
      int operandDepth,
      HandlerPhase phase) {
    public EnclosingHandler {
      Objects.requireNonNull(specification, "specification");
      Objects.requireNonNull(astPath, "astPath");
      Objects.requireNonNull(phase, "phase");
      if (instructionIndex < 0 || operandDepth < 0) {
        throw new IllegalArgumentException("invalid enclosing handler");
      }
    }
  }

  /** One innermost active Banteng for-loop at an imported continuation boundary. */
  public record EnclosingIterate(
      int instructionIndex,
      Opcode opcode,
      int exitTarget,
      String variables,
      AstPath astPath) {
    public EnclosingIterate {
      Objects.requireNonNull(opcode, "opcode");
      Objects.requireNonNull(variables, "variables");
      Objects.requireNonNull(astPath, "astPath");
      if (instructionIndex < 0
          || exitTarget < 0
          || (opcode != Opcode.ITERATE && opcode != Opcode.ITERATE_RANGE)) {
        throw new IllegalArgumentException("invalid enclosing iterate");
      }
    }
  }

  /** Exact Banteng call, handlers, and optional loop for one imported continuation. */
  public record ContinuationSite(
      BantengCallSite call,
      List<EnclosingHandler> handlers,
      Optional<EnclosingIterate> iterate) {
    public ContinuationSite {
      Objects.requireNonNull(call, "call");
      handlers = List.copyOf(handlers);
      Objects.requireNonNull(iterate, "iterate");
    }
  }

  /** Toast selector payload paired with one saved catch-handler label. */
  public record ToastErrorSelector(boolean catchesAny, List<String> errors) {
    public ToastErrorSelector {
      errors = List.copyOf(errors);
      if (catchesAny && !errors.isEmpty()) {
        throw new IllegalArgumentException("ANY selector cannot list errors");
      }
    }
  }

  /** One Toast catch clause in source and runtime-stack order. */
  public record ToastHandlerClause(
      AstPath astPath, ToastErrorSelector selector, int handlerLabelProgramCounter) {
    public ToastHandlerClause {
      Objects.requireNonNull(astPath, "astPath");
      Objects.requireNonNull(selector, "selector");
      if (handlerLabelProgramCounter < 0) {
        throw new IllegalArgumentException("negative Toast handler label");
      }
    }
  }

  /** One Toast catch marker: owner followed by clauses in source order. */
  public record ToastHandlerGroup(AstPath ownerPath, List<ToastHandlerClause> clauses) {
    public ToastHandlerGroup {
      Objects.requireNonNull(ownerPath, "ownerPath");
      clauses = List.copyOf(clauses);
      if (clauses.isEmpty()) {
        throw new IllegalArgumentException("Toast handler group requires a clause");
      }
    }
  }

  /** One exact Toast finally-handler label for a structural try owner. */
  public record ToastFinallyLabel(AstPath ownerPath, int handlerLabelProgramCounter) {
    public ToastFinallyLabel {
      Objects.requireNonNull(ownerPath, "ownerPath");
      if (handlerLabelProgramCounter < 0) {
        throw new IllegalArgumentException("negative Toast finally label");
      }
    }
  }

  /** Active Toast controls in serialized runtime-stack order, outermost first. */
  public record ToastControlLabels(
      List<ToastHandlerGroup> handlersOuterToInner,
      List<ToastFinallyLabel> finallyLabelsOuterToInner) {
    public ToastControlLabels {
      handlersOuterToInner = List.copyOf(handlersOuterToInner);
      finallyLabelsOuterToInner = List.copyOf(finallyLabelsOuterToInner);
    }
  }

  /** Structural execution region containing one suspended call. */
  public enum StructuralPhase {
    PROTECTED,
    EXCEPT_CLAUSE,
    EXPRESSION_FALLBACK,
    FINALLY_HANDLER
  }

  /** Runtime collection kind accepted by Toast's shared collection-loop opcode. */
  public enum CollectionKind {
    LIST,
    STRING,
    MAP
  }

  /** Runtime scalar kind accepted by Toast's range-loop opcode. */
  public enum RangeKind {
    INTEGER,
    OBJECT
  }

  /** Loop transfer encoded by Toast's persisted FIN_EXIT continuation. */
  public enum ExitAction {
    BREAK,
    CONTINUE
  }

  /** Exact Banteng jump corresponding to one source break or continue. */
  public record BantengExitControl(
      int targetInstructionPointer,
      AstPath targetLoopPath,
      ExitAction action) {
    public BantengExitControl {
      Objects.requireNonNull(targetLoopPath, "targetLoopPath");
      Objects.requireNonNull(action, "action");
      if (targetInstructionPointer < 0) {
        throw new IllegalArgumentException("invalid Banteng exit control");
      }
    }
  }

  /** One invertible Toast FIN_EXIT raw pair and its exact Banteng continuation. */
  public record ToastExitTarget(
      int targetStackDepth,
      int bantengOperandDepth,
      int targetProgramCounter,
      BantengExitControl bantengControl) {
    public ToastExitTarget {
      Objects.requireNonNull(bantengControl, "bantengControl");
      if (targetStackDepth < 0 || bantengOperandDepth < 0 || targetProgramCounter < 0) {
        throw new IllegalArgumentException("invalid Toast FIN_EXIT target");
      }
    }
  }

  /** Exact Banteng handler instruction corresponding to one Toast structural control. */
  public record BantengHandlerControl(
      int instructionIndex, HandlerSpec specification, AstPath astPath) {
    public BantengHandlerControl {
      Objects.requireNonNull(specification, "specification");
      Objects.requireNonNull(astPath, "astPath");
      if (instructionIndex < 0) {
        throw new IllegalArgumentException("negative Banteng handler instruction");
      }
    }
  }

  /** One typed structural entry in Toast base-to-top runtime-stack order. */
  public sealed interface StructuralStackEntry
      permits CatchGroup, ProtectedFinally, FinallyContinuation, CollectionLoop, RangeLoop {
    AstPath ownerPath();

    int baseDepth();
  }

  /** Catch structure, including raw marker placement and Banteng controls for synthesis. */
  public record CatchGroup(
      AstPath ownerPath,
      List<ToastHandlerClause> clauses,
      int baseDepth,
      OptionalInt markerDepth,
      BantengHandlerControl ownerControl,
      List<BantengHandlerControl> clauseControls,
      StructuralPhase phase,
      OptionalInt activeClauseIndex)
      implements StructuralStackEntry {
    public CatchGroup {
      Objects.requireNonNull(ownerPath, "ownerPath");
      clauses = List.copyOf(clauses);
      Objects.requireNonNull(markerDepth, "markerDepth");
      Objects.requireNonNull(ownerControl, "ownerControl");
      clauseControls = List.copyOf(clauseControls);
      Objects.requireNonNull(phase, "phase");
      Objects.requireNonNull(activeClauseIndex, "activeClauseIndex");
      if (baseDepth < 0 || clauses.isEmpty() || clauses.size() != clauseControls.size()) {
        throw new IllegalArgumentException("invalid catch stack shape");
      }
      if ((phase == StructuralPhase.PROTECTED) != markerDepth.isPresent()) {
        throw new IllegalArgumentException("catch marker does not match structural phase");
      }
      if ((phase == StructuralPhase.PROTECTED) == activeClauseIndex.isPresent()
          || (activeClauseIndex.isPresent()
              && (activeClauseIndex.getAsInt() < 0
                  || activeClauseIndex.getAsInt() >= clauses.size()))) {
        throw new IllegalArgumentException("invalid active catch clause");
      }
    }
  }

  /** One protected try/finally marker and its Banteng owner control. */
  public record ProtectedFinally(
      AstPath ownerPath,
      int baseDepth,
      int markerDepth,
      int handlerLabelProgramCounter,
      BantengHandlerControl ownerControl)
      implements StructuralStackEntry {
    public ProtectedFinally {
      Objects.requireNonNull(ownerPath, "ownerPath");
      Objects.requireNonNull(ownerControl, "ownerControl");
      if (baseDepth < 0 || markerDepth != baseDepth || handlerLabelProgramCounter < 0) {
        throw new IllegalArgumentException("invalid protected finally stack shape");
      }
    }
  }

  /** Toast finally-handler continuation values and the Banteng control they replace. */
  public record FinallyContinuation(
      AstPath ownerPath,
      int baseDepth,
      int reasonDepth,
      int valueDepth,
      BantengHandlerControl ownerControl,
      StructuralPhase phase,
      List<ToastExitTarget> exitTargets)
      implements StructuralStackEntry {
    public FinallyContinuation {
      Objects.requireNonNull(ownerPath, "ownerPath");
      Objects.requireNonNull(ownerControl, "ownerControl");
      Objects.requireNonNull(phase, "phase");
      exitTargets = List.copyOf(exitTargets);
      if (baseDepth < 0
          || reasonDepth != baseDepth
          || valueDepth != baseDepth + 1
          || phase != StructuralPhase.FINALLY_HANDLER) {
        throw new IllegalArgumentException("invalid finally continuation stack shape");
      }
      for (int left = 0; left < exitTargets.size(); left++) {
        for (int right = left + 1; right < exitTargets.size(); right++) {
          ToastExitTarget first = exitTargets.get(left);
          ToastExitTarget second = exitTargets.get(right);
          boolean sameToast =
              first.targetStackDepth() == second.targetStackDepth()
                  && first.targetProgramCounter() == second.targetProgramCounter();
          boolean sameBanteng =
              first.bantengOperandDepth() == second.bantengOperandDepth()
                  && first.bantengControl().targetInstructionPointer()
                      == second.bantengControl().targetInstructionPointer();
          if (sameToast || sameBanteng) {
            throw new IllegalArgumentException("ambiguous FIN_EXIT continuation mapping");
          }
        }
      }
    }

    /** Maps one persisted Toast reason-5 raw pair to its unique Banteng continuation. */
    public ToastExitTarget resolveToastExitTarget(
        long targetStackDepth, long targetProgramCounter) {
      int depth = Math.toIntExact(targetStackDepth);
      int programCounter = Math.toIntExact(targetProgramCounter);
      List<ToastExitTarget> matches =
          exitTargets.stream()
              .filter(
                  target ->
                      target.targetStackDepth() == depth
                          && target.targetProgramCounter() == programCounter)
              .toList();
      if (matches.size() != 1) {
        throw new IllegalArgumentException(
            "Toast FIN_EXIT raw pair resolved " + matches.size() + " times");
      }
      return matches.getFirst();
    }

    /** Inverts one Banteng exit continuation to its unique persisted Toast raw pair. */
    public ToastExitTarget resolveBantengExitTarget(
        int operandDepth, int targetInstructionPointer) {
      List<ToastExitTarget> matches =
          exitTargets.stream()
              .filter(
                  target ->
                      target.bantengOperandDepth() == operandDepth
                          && target.bantengControl().targetInstructionPointer()
                              == targetInstructionPointer)
              .toList();
      if (matches.size() != 1) {
        throw new IllegalArgumentException(
            "Banteng FIN_EXIT continuation resolved " + matches.size() + " times");
      }
      return matches.getFirst();
    }
  }

  /** Toast collection base and iterator retained across one loop body. */
  public record CollectionLoop(
      AstPath ownerPath,
      int baseDepth,
      int iteratorDepth,
      List<String> variableNames,
      Optional<CollectionKind> staticallyKnownKind,
      EnclosingIterate control)
      implements StructuralStackEntry {
    public CollectionLoop {
      Objects.requireNonNull(ownerPath, "ownerPath");
      variableNames = List.copyOf(variableNames);
      Objects.requireNonNull(staticallyKnownKind, "staticallyKnownKind");
      Objects.requireNonNull(control, "control");
      if (baseDepth < 0 || iteratorDepth != baseDepth + 1 || variableNames.isEmpty()) {
        throw new IllegalArgumentException("invalid collection loop stack shape");
      }
    }
  }

  /** Toast next and inclusive end values retained across one range-loop body. */
  public record RangeLoop(
      AstPath ownerPath,
      int baseDepth,
      int nextDepth,
      int endDepth,
      List<String> variableNames,
      Optional<RangeKind> staticallyKnownKind,
      EnclosingIterate control)
      implements StructuralStackEntry {
    public RangeLoop {
      Objects.requireNonNull(ownerPath, "ownerPath");
      variableNames = List.copyOf(variableNames);
      Objects.requireNonNull(staticallyKnownKind, "staticallyKnownKind");
      Objects.requireNonNull(control, "control");
      if (baseDepth < 0
          || nextDepth != baseDepth
          || endDepth != baseDepth + 1
          || variableNames.size() != 1) {
        throw new IllegalArgumentException("invalid range loop stack shape");
      }
    }
  }

  /** Exact post-argument call depth and typed Toast structural entries, base to top. */
  public record StructuralStackShape(
      int postArgumentDepth, List<StructuralStackEntry> entriesBaseToTop) {
    public StructuralStackShape {
      entriesBaseToTop = List.copyOf(entriesBaseToTop);
      if (postArgumentDepth < 0) {
        throw new IllegalArgumentException("negative post-argument stack depth");
      }
    }
  }
}
