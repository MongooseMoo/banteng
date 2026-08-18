package moo.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.BytecodeProgram.AstPath;
import moo.bytecode.BytecodeProgram.HandlerSpec;
import moo.bytecode.BytecodeProgram.Opcode;
import moo.syntax.Ast;
import moo.syntax.MooParser;

/** Pinned Toast compiler v17 program-vector layout for suspended-call import. */
public final class ToastV17ProgramLayout {
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

  /** Resolves one v17 serialized call boundary, rejecting absent or ambiguous matches. */
  public CallBoundary resolve(
      String source, int vector, long errorProgramCounter, long programCounter) {
    Objects.requireNonNull(source, "source");
    int errorPc = Math.toIntExact(errorProgramCounter);
    int pc = Math.toIntExact(programCounter);
    List<CallBoundary> matches =
        callBoundaries(source, vector).stream()
            .filter(
                boundary ->
                    boundary.errorProgramCounter() == errorPc
                        && boundary.programCounter() == pc)
            .toList();
    if (matches.size() != 1) {
      throw new IllegalArgumentException(
          "v17 call boundary "
              + errorPc
              + ".."
              + pc
              + " in vector "
              + vector
              + " resolved "
              + matches.size()
              + " times");
    }
    return matches.getFirst();
  }

  /** Returns every exact call boundary in one pinned Toast v17 program vector. */
  public List<CallBoundary> callBoundaries(String source, int vector) {
    Objects.requireNonNull(source, "source");
    LayoutCompiler compiler = new LayoutCompiler(MooParser.parse(source));
    return compiler.compile().vector(vector).boundaries(vector);
  }

  /** Resolves active Toast catch/finally labels at one structural continuation path. */
  public ToastControlLabels resolveToastControls(
      String source, int vector, AstPath continuationPath) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(continuationPath, "continuationPath");
    VectorLayout layout = new LayoutCompiler(MooParser.parse(source)).compile().vector(vector);
    List<ToastHandlerGroup> handlers =
        layout.handlerGroups().stream()
            .filter(group -> isBodyDescendant(group.ownerPath(), continuationPath, 0))
            .sorted(Comparator.comparingInt(group -> group.ownerPath().components().size()))
            .toList();
    List<ToastFinallyLabel> finallyLabels =
        layout.finallyLabels().stream()
            .filter(label -> isBodyDescendant(label.ownerPath(), continuationPath, 0))
            .sorted(Comparator.comparingInt(label -> label.ownerPath().components().size()))
            .toList();
    return new ToastControlLabels(handlers, finallyLabels);
  }

  /** Resolves the unique Toast catch marker owned by an exact structural path. */
  public ToastHandlerGroup resolveToastHandlerGroup(
      String source, int vector, AstPath ownerPath) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(ownerPath, "ownerPath");
    List<ToastHandlerGroup> matches =
        new LayoutCompiler(MooParser.parse(source)).compile().vector(vector).handlerGroups().stream()
            .filter(group -> group.ownerPath().equals(ownerPath))
            .toList();
    if (matches.size() != 1) {
      throw new IllegalArgumentException(
          "Toast handler owner path "
              + ownerPath.components()
              + " resolved "
              + matches.size()
              + " times");
    }
    return matches.getFirst();
  }

  /** Resolves the unique Toast finally label owned by an exact structural path. */
  ToastFinallyLabel resolveToastFinallyLabel(String source, int vector, AstPath ownerPath) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(ownerPath, "ownerPath");
    List<ToastFinallyLabel> matches =
        new LayoutCompiler(MooParser.parse(source)).compile().vector(vector).finallyLabels().stream()
            .filter(label -> label.ownerPath().equals(ownerPath))
            .toList();
    if (matches.size() != 1) {
      throw new IllegalArgumentException(
          "Toast finally owner path "
              + ownerPath.components()
              + " resolved "
              + matches.size()
              + " times");
    }
    return matches.getFirst();
  }

  /** Resolves the complete Toast structural runtime-stack shape at one call boundary. */
  public StructuralStackShape resolveStructuralStack(
      String source, int vector, BytecodeProgram program, CallBoundary boundary) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(program, "program");
    Objects.requireNonNull(boundary, "boundary");
    if (boundary.vector() != vector) {
      throw new IllegalArgumentException("Toast boundary vector does not match requested vector");
    }
    BantengCallSite call = locateBantengCall(program, boundary.astPath());
    VectorLayout layout = new LayoutCompiler(MooParser.parse(source)).compile().vector(vector);
    return layout.structuralStackShape(boundary, call.program());
  }

  /** Finds the unique matching call recursively across Banteng's hierarchical fork programs. */
  public BantengCallSite locateBantengCall(BytecodeProgram program, AstPath astPath) {
    Objects.requireNonNull(program, "program");
    Objects.requireNonNull(astPath, "astPath");
    List<BantengCallSite> matches = new ArrayList<>();
    collectBantengCalls(program, astPath, matches);
    if (matches.size() != 1) {
      throw new IllegalArgumentException(
          "Banteng AST call path " + astPath.components() + " resolved " + matches.size() + " times");
    }
    return matches.getFirst();
  }

  /** Resolves the structural controls enclosing an already-proven Toast call boundary. */
  public ContinuationSite resolveContinuation(BytecodeProgram program, CallBoundary boundary) {
    Objects.requireNonNull(boundary, "boundary");
    BantengCallSite call = locateBantengCall(program, boundary.astPath());
    List<EnclosingHandler> handlers =
        enclosingHandlers(call.program(), boundary.astPath());
    Optional<EnclosingIterate> iterate =
        enclosingIterate(call.program(), boundary.astPath());
    return new ContinuationSite(call, handlers, iterate);
  }

  /** Resolves controls and rejects a mismatch with serialized control-stack requirements. */
  public ContinuationSite resolveContinuation(
      BytecodeProgram program,
      CallBoundary boundary,
      int expectedHandlerCount,
      boolean expectsIterate) {
    if (expectedHandlerCount < 0) {
      throw new IllegalArgumentException("negative expected handler count");
    }
    ContinuationSite site = resolveContinuation(program, boundary);
    if (site.handlers().size() != expectedHandlerCount) {
      throw new IllegalArgumentException(
          "expected "
              + expectedHandlerCount
              + " enclosing handlers but resolved "
              + site.handlers().size());
    }
    if (site.iterate().isPresent() != expectsIterate) {
      throw new IllegalArgumentException(
          expectsIterate ? "required enclosing iterate is absent" : "unexpected enclosing iterate");
    }
    return site;
  }

  private static List<EnclosingHandler> enclosingHandlers(
      BytecodeProgram program, AstPath callPath) {
    List<IndexedControl> controls = new ArrayList<>();
    for (int index = 0; index < program.instructions().size(); index++) {
      BytecodeProgram.Instruction instruction = program.instructions().get(index);
      if (instruction.opcode() != Opcode.ENTER_HANDLER || instruction.astPath().isEmpty()) {
        continue;
      }
      controls.add(new IndexedControl(index, instruction, instruction.astPath().orElseThrow()));
    }
    List<IndexedControl> clauses =
        controls.stream()
            .filter(
                candidate ->
                    controls.stream()
                        .anyMatch(owner -> isClauseOf(owner.path(), candidate.path())))
            .toList();
    List<IndexedControl> owners =
        controls.stream()
            .filter(control -> !clauses.contains(control))
            .filter(control -> isBodyDescendant(control.path(), callPath, 0))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    owners.sort(
        Comparator.comparingInt((IndexedControl control) -> control.path().components().size())
            .reversed());

    List<EnclosingHandler> result = new ArrayList<>();
    for (IndexedControl owner : owners) {
      requireUniquePath(owners, owner.path(), "handler owner");
      List<IndexedControl> ownedClauses =
          clauses.stream()
              .filter(clause -> isClauseOf(owner.path(), clause.path()))
              .sorted(
                  Comparator.comparingInt(
                      clause -> clause.path().components().getLast()))
              .toList();
      for (IndexedControl clause : ownedClauses) {
        requireUniquePath(clauses, clause.path(), "handler clause");
        result.add(enclosingHandler(clause));
      }
      result.add(enclosingHandler(owner));
    }
    return List.copyOf(result);
  }

  private static Optional<EnclosingIterate> enclosingIterate(
      BytecodeProgram program, AstPath callPath) {
    List<IndexedControl> matches = new ArrayList<>();
    for (int index = 0; index < program.instructions().size(); index++) {
      BytecodeProgram.Instruction instruction = program.instructions().get(index);
      if ((instruction.opcode() == Opcode.ITERATE
              || instruction.opcode() == Opcode.ITERATE_RANGE)
          && instruction.astPath().isPresent()
          && isBodyDescendant(instruction.astPath().orElseThrow(), callPath, 2)) {
        matches.add(
            new IndexedControl(index, instruction, instruction.astPath().orElseThrow()));
      }
    }
    if (matches.isEmpty()) {
      return Optional.empty();
    }
    int longest =
        matches.stream().mapToInt(control -> control.path().components().size()).max().orElseThrow();
    List<IndexedControl> innermost =
        matches.stream()
            .filter(control -> control.path().components().size() == longest)
            .toList();
    if (innermost.size() != 1) {
      throw new IllegalArgumentException("enclosing iterate resolved " + innermost.size() + " times");
    }
    IndexedControl control = innermost.getFirst();
    return Optional.of(
        new EnclosingIterate(
            control.index(),
            control.instruction().opcode(),
            Math.toIntExact(control.instruction().operand().orElseThrow()),
            control.instruction().text().orElseThrow(),
            control.path()));
  }

  private static EnclosingHandler enclosingHandler(IndexedControl control) {
    return new EnclosingHandler(
        control.index(),
        control.instruction().handler().orElseThrow(),
        control.path(),
        0,
        HandlerPhase.TRY);
  }

  private static BantengHandlerControl handlerControl(
      BytecodeProgram program, AstPath path) {
    IndexedControl control = uniqueControl(program, path, Opcode.ENTER_HANDLER, "handler");
    return new BantengHandlerControl(
        control.index(), control.instruction().handler().orElseThrow(), path);
  }

  private static EnclosingIterate iterateControl(BytecodeProgram program, AstPath path) {
    List<IndexedControl> matches = new ArrayList<>();
    for (int index = 0; index < program.instructions().size(); index++) {
      BytecodeProgram.Instruction instruction = program.instructions().get(index);
      if ((instruction.opcode() == Opcode.ITERATE
              || instruction.opcode() == Opcode.ITERATE_RANGE)
          && instruction.astPath().filter(path::equals).isPresent()) {
        matches.add(new IndexedControl(index, instruction, path));
      }
    }
    if (matches.size() != 1) {
      throw new IllegalArgumentException(
          "Banteng iterate path " + path.components() + " resolved " + matches.size() + " times");
    }
    IndexedControl control = matches.getFirst();
    return new EnclosingIterate(
        control.index(),
        control.instruction().opcode(),
        Math.toIntExact(control.instruction().operand().orElseThrow()),
        control.instruction().text().orElseThrow(),
        path);
  }

  private static BantengExitControl exitControl(
      BytecodeProgram program, PendingToastExitTarget target) {
    IndexedControl control =
        uniqueControl(program, target.exitPath(), Opcode.JUMP, "loop exit");
    return new BantengExitControl(
        Math.toIntExact(control.instruction().operand().orElseThrow()),
        target.targetLoopPath(),
        target.action());
  }

  private static IndexedControl uniqueControl(
      BytecodeProgram program, AstPath path, Opcode opcode, String description) {
    List<IndexedControl> matches = new ArrayList<>();
    for (int index = 0; index < program.instructions().size(); index++) {
      BytecodeProgram.Instruction instruction = program.instructions().get(index);
      if (instruction.opcode() == opcode && instruction.astPath().filter(path::equals).isPresent()) {
        matches.add(new IndexedControl(index, instruction, path));
      }
    }
    if (matches.size() != 1) {
      throw new IllegalArgumentException(
          "Banteng "
              + description
              + " path "
              + path.components()
              + " resolved "
              + matches.size()
              + " times");
    }
    return matches.getFirst();
  }

  private static boolean isBodyDescendant(AstPath owner, AstPath descendant, int bodyComponent) {
    List<Integer> ownerComponents = owner.components();
    List<Integer> descendantComponents = descendant.components();
    return descendantComponents.size() > ownerComponents.size()
        && descendantComponents.subList(0, ownerComponents.size()).equals(ownerComponents)
        && descendantComponents.get(ownerComponents.size()) == bodyComponent;
  }

  private static boolean isClauseOf(AstPath owner, AstPath clause) {
    List<Integer> ownerComponents = owner.components();
    List<Integer> clauseComponents = clause.components();
    return clauseComponents.size() == ownerComponents.size() + 2
        && clauseComponents.subList(0, ownerComponents.size()).equals(ownerComponents)
        && (clauseComponents.get(ownerComponents.size()) == 1
            || clauseComponents.get(ownerComponents.size()) == 2);
  }

  private static void requireUniquePath(
      List<IndexedControl> controls, AstPath path, String description) {
    long matches = controls.stream().filter(control -> control.path().equals(path)).count();
    if (matches != 1) {
      throw new IllegalArgumentException(description + " path resolved " + matches + " times");
    }
  }

  private record IndexedControl(
      int index, BytecodeProgram.Instruction instruction, AstPath path) {}

  private static void collectBantengCalls(
      BytecodeProgram program, AstPath astPath, List<BantengCallSite> matches) {
    for (int index = 0; index < program.instructions().size(); index++) {
      BytecodeProgram.Instruction instruction = program.instructions().get(index);
      if ((instruction.opcode() == Opcode.CALL || instruction.opcode() == Opcode.CALL_VERB)
          && instruction.astPath().filter(astPath::equals).isPresent()) {
        matches.add(new BantengCallSite(program, index, index + 1));
      }
    }
    for (BytecodeProgram fork : program.forkVectors()) {
      collectBantengCalls(fork, astPath, matches);
    }
  }

  private enum UnitKind {
    BYTE,
    LITERAL,
    FORK,
    VARIABLE,
    LABEL,
    STACK
  }

  private record PendingCall(
      int errorUnit,
      int endUnit,
      AstPath path,
      CallKind kind,
      int postArgumentDepth,
      List<PendingStructuralEntry> structuresBaseToTop) {}

  private static final class LabelReference {
    private int targetUnit = -1;
  }

  private record PendingToastClause(
      AstPath path, ToastErrorSelector selector, LabelReference label) {}

  private record PendingToastHandlerGroup(
      AstPath ownerPath, List<PendingToastClause> clauses) {}

  private record PendingToastFinally(AstPath ownerPath, LabelReference label) {}

  private record PendingToastExitTarget(
      AstPath exitPath,
      AstPath targetLoopPath,
      int targetStackDepth,
      int bantengOperandDepth,
      LabelReference targetLabel,
      ExitAction action) {}

  private record ActiveLoopTarget(
      AstPath ownerPath,
      List<String> variableNames,
      LabelReference topLabel,
      int topStackDepth,
      LabelReference bottomLabel,
      int bottomStackDepth,
      int bantengOperandDepth) {}

  private sealed interface PendingStructuralEntry
      permits PendingCatchGroup,
          PendingProtectedFinally,
          PendingFinallyContinuation,
          PendingCollectionLoop,
          PendingRangeLoop {}

  private record PendingCatchGroup(
      AstPath ownerPath,
      PendingToastHandlerGroup group,
      int baseDepth,
      OptionalInt markerDepth,
      StructuralPhase phase,
      OptionalInt activeClauseIndex)
      implements PendingStructuralEntry {}

  private record PendingProtectedFinally(
      AstPath ownerPath, PendingToastFinally label, int baseDepth, int markerDepth)
      implements PendingStructuralEntry {}

  private record PendingFinallyContinuation(
      AstPath ownerPath,
      PendingToastFinally label,
      int baseDepth,
      int reasonDepth,
      int valueDepth,
      List<PendingToastExitTarget> exitTargets)
      implements PendingStructuralEntry {}

  private record PendingCollectionLoop(
      AstPath ownerPath,
      int baseDepth,
      int iteratorDepth,
      List<String> variableNames,
      Optional<CollectionKind> staticallyKnownKind)
      implements PendingStructuralEntry {}

  private record PendingRangeLoop(
      AstPath ownerPath,
      int baseDepth,
      int nextDepth,
      int endDepth,
      List<String> variableNames,
      Optional<RangeKind> staticallyKnownKind)
      implements PendingStructuralEntry {}

  private record VectorLayout(
      List<UnitKind> units,
      List<PendingCall> calls,
      List<PendingToastHandlerGroup> pendingHandlerGroups,
      List<PendingToastFinally> pendingFinallyLabels,
      int literalWidth,
      int forkWidth,
      int variableWidth,
      int labelWidth,
      int stackWidth) {
    List<CallBoundary> boundaries(int vector) {
      return calls.stream()
          .map(
              call ->
                  new CallBoundary(
                      vector,
                      offset(call.errorUnit()),
                      offset(call.endUnit()),
                      call.path(),
                      call.kind()))
          .toList();
    }

    List<ToastHandlerGroup> handlerGroups() {
      return pendingHandlerGroups.stream()
          .map(this::handlerGroup)
          .toList();
    }

    List<ToastFinallyLabel> finallyLabels() {
      return pendingFinallyLabels.stream()
          .map(label -> new ToastFinallyLabel(label.ownerPath(), labelTarget(label.label())))
          .toList();
    }

    private int labelTarget(LabelReference label) {
      if (label.targetUnit < 0) {
        throw new IllegalStateException("Toast label target was not defined");
      }
      return offset(label.targetUnit);
    }

    StructuralStackShape structuralStackShape(
        CallBoundary boundary, BytecodeProgram bantengProgram) {
      List<PendingCall> matches =
          calls.stream()
              .filter(
                  call ->
                      call.path().equals(boundary.astPath())
                          && call.kind() == boundary.kind()
                          && offset(call.errorUnit()) == boundary.errorProgramCounter()
                          && offset(call.endUnit()) == boundary.programCounter())
              .toList();
      if (matches.size() != 1) {
        throw new IllegalArgumentException(
            "Toast structural call resolved " + matches.size() + " times");
      }
      PendingCall call = matches.getFirst();
      List<StructuralStackEntry> entries =
          call.structuresBaseToTop().stream()
              .map(entry -> structuralEntry(entry, bantengProgram))
              .toList();
      return new StructuralStackShape(call.postArgumentDepth(), entries);
    }

    private StructuralStackEntry structuralEntry(
        PendingStructuralEntry entry, BytecodeProgram bantengProgram) {
      if (entry instanceof PendingCatchGroup caught) {
        ToastHandlerGroup group = handlerGroup(caught.group());
        return new CatchGroup(
            caught.ownerPath(),
            group.clauses(),
            caught.baseDepth(),
            caught.markerDepth(),
            handlerControl(bantengProgram, caught.ownerPath()),
            group.clauses().stream()
                .map(clause -> handlerControl(bantengProgram, clause.astPath()))
                .toList(),
            caught.phase(),
            caught.activeClauseIndex());
      }
      if (entry instanceof PendingProtectedFinally protectedFinally) {
        return new ProtectedFinally(
            protectedFinally.ownerPath(),
            protectedFinally.baseDepth(),
            protectedFinally.markerDepth(),
            labelTarget(protectedFinally.label().label()),
            handlerControl(bantengProgram, protectedFinally.ownerPath()));
      }
      if (entry instanceof PendingFinallyContinuation continuation) {
        return new FinallyContinuation(
            continuation.ownerPath(),
            continuation.baseDepth(),
            continuation.reasonDepth(),
            continuation.valueDepth(),
            handlerControl(bantengProgram, continuation.ownerPath()),
            StructuralPhase.FINALLY_HANDLER,
            continuation.exitTargets().stream()
                .map(
                    target ->
                        new ToastExitTarget(
                            target.targetStackDepth(),
                            target.bantengOperandDepth(),
                            labelTarget(target.targetLabel()),
                            exitControl(bantengProgram, target)))
                .distinct()
                .toList());
      }
      if (entry instanceof PendingCollectionLoop loop) {
        return new CollectionLoop(
            loop.ownerPath(),
            loop.baseDepth(),
            loop.iteratorDepth(),
            loop.variableNames(),
            loop.staticallyKnownKind(),
            iterateControl(bantengProgram, loop.ownerPath()));
      }
      PendingRangeLoop loop = (PendingRangeLoop) entry;
      return new RangeLoop(
          loop.ownerPath(),
          loop.baseDepth(),
          loop.nextDepth(),
          loop.endDepth(),
          loop.variableNames(),
          loop.staticallyKnownKind(),
          iterateControl(bantengProgram, loop.ownerPath()));
    }

    private ToastHandlerGroup handlerGroup(PendingToastHandlerGroup group) {
      return new ToastHandlerGroup(
          group.ownerPath(),
          group.clauses().stream()
              .map(
                  clause ->
                      new ToastHandlerClause(
                          clause.path(), clause.selector(), labelTarget(clause.label())))
              .toList());
    }

    private int offset(int unitIndex) {
      int offset = 0;
      for (int index = 0; index < unitIndex; index++) {
        offset += width(units.get(index));
      }
      return offset;
    }

    private int width(UnitKind kind) {
      return switch (kind) {
        case BYTE -> 1;
        case LITERAL -> literalWidth;
        case FORK -> forkWidth;
        case VARIABLE -> variableWidth;
        case LABEL -> labelWidth;
        case STACK -> stackWidth;
      };
    }
  }

  private record ProgramLayout(VectorLayout main, List<VectorLayout> forks) {
    VectorLayout vector(int vector) {
      if (vector == -1) {
        return main;
      }
      if (vector < 0 || vector >= forks.size()) {
        throw new IllegalArgumentException("v17 program has no fork vector " + vector);
      }
      return forks.get(vector);
    }
  }

  private static final class GlobalState {
    private final LinkedHashSet<LiteralKey> literals = new LinkedHashSet<>();
    private final List<VectorLayout> forks = new ArrayList<>();
    private int totalVariableReferences;

    int addLiteral(LiteralKey literal) {
      literals.add(literal);
      int index = 0;
      for (LiteralKey candidate : literals) {
        if (candidate.equals(literal)) {
          return index;
        }
        index++;
      }
      throw new AssertionError("new literal was not retained");
    }
  }

  private record LiteralKey(String type, Object value) {}

  private static final class VectorBuilder {
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

  private static int referenceWidth(int maximum) {
    if (maximum <= 256) {
      return 1;
    }
    return maximum <= 65_536 ? 2 : 4;
  }

  private static final class LayoutCompiler {
    private static final List<String> PREDEFINED_NAMES =
        List.of(
            "num", "obj", "str", "list", "err", "player", "this", "caller", "verb",
            "args", "argstr", "dobj", "dobjstr", "prepstr", "iobj", "iobjstr", "int",
            "float", "map", "anon", "waif", "bool", "true", "false");

    private final Ast.Program program;
    private final Map<String, Integer> symbols = new LinkedHashMap<>();
    private final GlobalState global = new GlobalState();

    LayoutCompiler(Ast.Program program) {
      this.program = program;
      for (String name : PREDEFINED_NAMES) {
        symbols.put(name, symbols.size());
      }
      collectSymbols(program.statements());
    }

    ProgramLayout compile() {
      VectorLayout main = compileVector(program.statements(), List.of());
      return new ProgramLayout(main, List.copyOf(global.forks));
    }

    private VectorLayout compileVector(List<Ast.Statement> statements, List<Integer> prefix) {
      VectorBuilder vector = new VectorBuilder(global);
      emitStatements(statements, prefix, vector);
      return vector.finish();
    }

    private int symbol(String name) {
      Integer slot = symbols.get(name.toLowerCase(Locale.ROOT));
      if (slot == null) {
        throw new IllegalArgumentException("unindexed v17 variable " + name);
      }
      return slot;
    }

    private void register(String name) {
      symbols.computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> symbols.size());
    }

    private void collectSymbols(List<Ast.Statement> statements) {
      for (Ast.Statement statement : statements) {
        collectStatementSymbols(statement);
      }
    }

    private void collectStatementSymbols(Ast.Statement statement) {
      if (statement instanceof Ast.If conditional) {
        collectExpressionSymbols(conditional.condition());
        collectSymbols(conditional.body());
        for (Ast.ElseIf elseIf : conditional.elseIfs()) {
          collectExpressionSymbols(elseIf.condition());
          collectSymbols(elseIf.body());
        }
        collectSymbols(conditional.elseBody());
        return;
      }
      if (statement instanceof Ast.While loop) {
        loop.loopVariable().ifPresent(this::register);
        collectExpressionSymbols(loop.condition());
        collectSymbols(loop.body());
        return;
      }
      if (statement instanceof Ast.For loop) {
        register(loop.variable());
        loop.indexVariable().ifPresent(this::register);
        collectExpressionSymbols(loop.iterable());
        loop.rangeEnd().ifPresent(this::collectExpressionSymbols);
        collectSymbols(loop.body());
        return;
      }
      if (statement instanceof Ast.Break broken) {
        broken.loopVariable().ifPresent(this::register);
        return;
      }
      if (statement instanceof Ast.Continue continued) {
        continued.loopVariable().ifPresent(this::register);
        return;
      }
      if (statement instanceof Ast.Fork fork) {
        fork.taskIdVariable().ifPresent(this::register);
        collectExpressionSymbols(fork.delay());
        collectSymbols(fork.body());
        return;
      }
      if (statement instanceof Ast.Try guarded) {
        collectSymbols(guarded.body());
        for (Ast.ExceptClause clause : guarded.exceptClauses()) {
          clause.variable().ifPresent(this::register);
          collectSymbols(clause.body());
        }
        guarded.finallyClause().ifPresent(clause -> collectSymbols(clause.body()));
        return;
      }
      if (statement instanceof Ast.Return returned) {
        returned.value().ifPresent(this::collectExpressionSymbols);
        return;
      }
      if (statement instanceof Ast.ExpressionStatement expression) {
        collectExpressionSymbols(expression.expression());
      }
    }

    private void collectExpressionSymbols(Ast.Expression expression) {
      if (expression instanceof Ast.Identifier identifier) {
        register(identifier.name());
        return;
      }
      if (expression instanceof Ast.ListLiteral list) {
        list.elements().forEach(this::collectExpressionSymbols);
        return;
      }
      if (expression instanceof Ast.MapLiteral map) {
        for (Ast.MapEntry entry : map.entries()) {
          collectExpressionSymbols(entry.key());
          collectExpressionSymbols(entry.value());
        }
        return;
      }
      if (expression instanceof Ast.Splice splice) {
        collectExpressionSymbols(splice.value());
        return;
      }
      if (expression instanceof Ast.ScatterElement scatter) {
        register(scatter.name());
        scatter.defaultValue().ifPresent(this::collectExpressionSymbols);
        return;
      }
      if (expression instanceof Ast.Call call) {
        call.arguments().forEach(this::collectExpressionSymbols);
        return;
      }
      if (expression instanceof Ast.VerbCall call) {
        collectExpressionSymbols(call.object());
        collectExpressionSymbols(call.name());
        call.arguments().forEach(this::collectExpressionSymbols);
        return;
      }
      if (expression instanceof Ast.Assignment assignment) {
        collectTargetSymbols(assignment.target());
        collectExpressionSymbols(assignment.value());
        return;
      }
      if (expression instanceof Ast.PropertyAccess property) {
        collectExpressionSymbols(property.object());
        collectExpressionSymbols(property.property());
        return;
      }
      if (expression instanceof Ast.IndexAccess index) {
        collectExpressionSymbols(index.collection());
        collectExpressionSymbols(index.index());
        return;
      }
      if (expression instanceof Ast.RangeAccess range) {
        collectExpressionSymbols(range.collection());
        collectExpressionSymbols(range.start());
        collectExpressionSymbols(range.end());
        return;
      }
      if (expression instanceof Ast.Unary unary) {
        collectExpressionSymbols(unary.operand());
        return;
      }
      if (expression instanceof Ast.Binary binary) {
        collectExpressionSymbols(binary.left());
        collectExpressionSymbols(binary.right());
        return;
      }
      if (expression instanceof Ast.Ternary ternary) {
        collectExpressionSymbols(ternary.condition());
        collectExpressionSymbols(ternary.trueExpression());
        collectExpressionSymbols(ternary.falseExpression());
        return;
      }
      if (expression instanceof Ast.Catch caught) {
        collectExpressionSymbols(caught.guarded());
        caught.fallback().ifPresent(this::collectExpressionSymbols);
      }
    }

    private void collectTargetSymbols(Ast.AssignmentTarget target) {
      if (target instanceof Ast.VariableTarget variable) {
        register(variable.name());
        return;
      }
      if (target instanceof Ast.PropertyTarget property) {
        collectExpressionSymbols(property.object());
        collectExpressionSymbols(property.property());
        return;
      }
      if (target instanceof Ast.IndexTarget index) {
        collectExpressionSymbols(index.collection());
        collectExpressionSymbols(index.index());
        return;
      }
      if (target instanceof Ast.RangeTarget range) {
        collectExpressionSymbols(range.collection());
        collectExpressionSymbols(range.start());
        collectExpressionSymbols(range.end());
        return;
      }
      if (target instanceof Ast.ScatterTarget scatter) {
        for (Ast.ScatterElement element : scatter.elements()) {
          register(element.name());
          element.defaultValue().ifPresent(this::collectExpressionSymbols);
        }
      }
    }

    private void emitStatements(
        List<Ast.Statement> statements, List<Integer> prefix, VectorBuilder vector) {
      for (int index = 0; index < statements.size(); index++) {
        List<Integer> path = append(prefix, index);
        emitStatement(statements.get(index), path, vector);
      }
    }

    private void emitStatement(
        Ast.Statement statement, List<Integer> path, VectorBuilder vector) {
      if (statement instanceof Ast.If conditional) {
        emitExpression(conditional.condition(), append(path, 0), vector);
        vector.bytecode();
        vector.label();
        vector.pop(1);
        emitStatements(conditional.body(), append(path, 1), vector);
        vector.bytecode();
        vector.label();
        for (int index = 0; index < conditional.elseIfs().size(); index++) {
          Ast.ElseIf elseIf = conditional.elseIfs().get(index);
          emitExpression(elseIf.condition(), append(path, 2, index, 0), vector);
          vector.bytecode();
          vector.label();
          vector.pop(1);
          emitStatements(elseIf.body(), append(path, 2, index, 1), vector);
          vector.bytecode();
          vector.label();
        }
        emitStatements(conditional.elseBody(), append(path, 3), vector);
        return;
      }
      if (statement instanceof Ast.While loop) {
        int baseDepth = vector.stackDepth();
        int bantengOperandDepth = vector.ordinaryStackDepth();
        AstPath ownerPath = astPath(path);
        LabelReference topLabel = vector.newLabel();
        LabelReference bottomLabel = vector.newLabel();
        vector.define(topLabel);
        emitExpression(loop.condition(), append(path, 0), vector);
        if (loop.loopVariable().isPresent()) {
          vector.extended();
          vector.variableOperand(symbol(loop.loopVariable().orElseThrow()));
        } else {
          vector.bytecode();
        }
        vector.label(bottomLabel);
        vector.pop(1);
        ActiveLoopTarget loopTarget =
            new ActiveLoopTarget(
                ownerPath,
                loop.loopVariable().map(List::of).orElseGet(List::of),
                topLabel,
                baseDepth,
                bottomLabel,
                baseDepth,
                bantengOperandDepth);
        vector.enterLoop(loopTarget);
        emitStatements(loop.body(), append(path, 1), vector);
        vector.leaveLoop(loopTarget);
        vector.bytecode();
        vector.label(topLabel);
        vector.define(bottomLabel);
        return;
      }
      if (statement instanceof Ast.For loop) {
        int baseDepth = vector.stackDepth();
        int bantengOperandDepth = vector.ordinaryStackDepth();
        AstPath ownerPath = astPath(path);
        List<String> variableNames =
            loop.indexVariable()
                .map(index -> List.of(loop.variable(), index))
                .orElseGet(() -> List.of(loop.variable()));
        if (loop.rangeEnd().isPresent()) {
          emitExpression(loop.iterable(), append(path, 0), vector);
          emitExpression(loop.rangeEnd().orElseThrow(), append(path, 1), vector);
          LabelReference topLabel = vector.newLabel();
          LabelReference bottomLabel = vector.newLabel();
          vector.define(topLabel);
          vector.bytecode();
          vector.variableOperand(symbol(loop.variable()));
          vector.label(bottomLabel);
          PendingRangeLoop structure =
              new PendingRangeLoop(
                  ownerPath,
                  baseDepth,
                  baseDepth,
                  baseDepth + 1,
                  variableNames,
                  rangeKind(loop.iterable(), loop.rangeEnd().orElseThrow()));
          ActiveLoopTarget loopTarget =
              new ActiveLoopTarget(
                  ownerPath,
                  variableNames,
                  topLabel,
                  baseDepth + 2,
                  bottomLabel,
                  baseDepth,
                  bantengOperandDepth);
          vector.enterLoop(loopTarget);
          vector.enterStructure(structure);
          emitStatements(loop.body(), append(path, 2), vector);
          vector.leaveStructure(structure);
          vector.leaveLoop(loopTarget);
          vector.bytecode();
          vector.label(topLabel);
          vector.define(bottomLabel);
          vector.pop(2);
        } else {
          emitExpression(loop.iterable(), append(path, 0), vector);
          vector.literal(new LiteralKey("none", "none"));
          LabelReference topLabel = vector.newLabel();
          LabelReference bottomLabel = vector.newLabel();
          vector.define(topLabel);
          vector.extended();
          vector.variableOperand(symbol(loop.variable()));
          loop.indexVariable().ifPresent(name -> vector.variableOperand(symbol(name)));
          vector.label(bottomLabel);
          PendingCollectionLoop structure =
              new PendingCollectionLoop(
                  ownerPath,
                  baseDepth,
                  baseDepth + 1,
                  variableNames,
                  collectionKind(loop.iterable()));
          ActiveLoopTarget loopTarget =
              new ActiveLoopTarget(
                  ownerPath,
                  variableNames,
                  topLabel,
                  baseDepth + 2,
                  bottomLabel,
                  baseDepth,
                  bantengOperandDepth);
          vector.enterLoop(loopTarget);
          vector.enterStructure(structure);
          emitStatements(loop.body(), append(path, 2), vector);
          vector.leaveStructure(structure);
          vector.leaveLoop(loopTarget);
          vector.bytecode();
          vector.label(topLabel);
          vector.define(bottomLabel);
          vector.pop(2);
        }
        return;
      }
      if (statement instanceof Ast.Break broken) {
        ActiveLoopTarget target = vector.loopTarget(broken.loopVariable());
        vector.extended();
        broken.loopVariable().ifPresent(name -> vector.variableOperand(symbol(name)));
        vector.stack();
        vector.label(target.bottomLabel());
        vector.exitTarget(
            new PendingToastExitTarget(
                astPath(path),
                target.ownerPath(),
                target.bottomStackDepth(),
                target.bantengOperandDepth(),
                target.bottomLabel(),
                ExitAction.BREAK));
        return;
      }
      if (statement instanceof Ast.Continue continued) {
        ActiveLoopTarget target = vector.loopTarget(continued.loopVariable());
        vector.extended();
        continued.loopVariable().ifPresent(name -> vector.variableOperand(symbol(name)));
        vector.stack();
        vector.label(target.topLabel());
        vector.exitTarget(
            new PendingToastExitTarget(
                astPath(path),
                target.ownerPath(),
                target.topStackDepth(),
                target.bantengOperandDepth(),
                target.topLabel(),
                ExitAction.CONTINUE));
        return;
      }
      if (statement instanceof Ast.Fork fork) {
        emitExpression(fork.delay(), append(path, 0), vector);
        vector.bytecode();
        VectorLayout child = compileVector(fork.body(), append(path, 1));
        int forkIndex = global.forks.size();
        global.forks.add(child);
        vector.fork(forkIndex);
        fork.taskIdVariable().ifPresent(name -> vector.variableOperand(symbol(name)));
        vector.pop(1);
        return;
      }
      if (statement instanceof Ast.Try guarded) {
        if (!guarded.exceptClauses().isEmpty()) {
          emitTryExcept(guarded, path, vector);
        } else if (guarded.finallyClause().isPresent()) {
          emitTryFinally(guarded, path, vector);
        } else {
          throw new IllegalArgumentException("v17 try statement has no handler");
        }
        return;
      }
      if (statement instanceof Ast.Return returned) {
        if (returned.value().isPresent()) {
          emitExpression(returned.value().orElseThrow(), append(path, 0), vector);
          vector.bytecode();
          vector.pop(1);
        } else {
          vector.bytecode();
        }
        return;
      }
      if (statement instanceof Ast.ExpressionStatement expression) {
        emitExpression(expression.expression(), append(path, 0), vector);
        vector.bytecode();
        vector.pop(1);
      }
    }

    private void emitTryExcept(Ast.Try guarded, List<Integer> path, VectorBuilder vector) {
      int baseDepth = vector.stackDepth();
      AstPath ownerPath = astPath(path);
      List<PendingToastClause> pendingClauses = new ArrayList<>();
      for (int index = 0; index < guarded.exceptClauses().size(); index++) {
        Ast.ExceptClause clause = guarded.exceptClauses().get(index);
        emitErrorCodes(clause.errors(), vector);
        vector.extended();
        LabelReference handlerLabel = vector.labelReference();
        pendingClauses.add(
            new PendingToastClause(
                astPath(append(path, 1, index)),
                toastSelector(clause.errors()),
                handlerLabel));
        vector.push(1);
      }
      PendingToastHandlerGroup group = vector.handlerGroup(ownerPath, pendingClauses);
      vector.extended();
      vector.bytecode();
      vector.push(1);
      PendingCatchGroup protectedStructure =
          new PendingCatchGroup(
              ownerPath,
              group,
              baseDepth,
              OptionalInt.of(vector.stackDepth() - 1),
              StructuralPhase.PROTECTED,
              OptionalInt.empty());
      vector.enterStructure(protectedStructure);
      emitStatements(guarded.body(), append(path, 0), vector);
      vector.leaveStructure(protectedStructure);
      vector.extended();
      vector.label();
      vector.pop(2 * guarded.exceptClauses().size() + 1);
      for (int index = 0; index < guarded.exceptClauses().size(); index++) {
        Ast.ExceptClause clause = guarded.exceptClauses().get(index);
        vector.define(pendingClauses.get(index).label());
        vector.push(1);
        clause.variable().ifPresent(name -> vector.variable(symbol(name)));
        vector.bytecode();
        vector.pop(1);
        PendingCatchGroup clauseStructure =
            new PendingCatchGroup(
                ownerPath,
                group,
                baseDepth,
                OptionalInt.empty(),
                StructuralPhase.EXCEPT_CLAUSE,
                OptionalInt.of(index));
        vector.enterStructure(clauseStructure);
        emitStatements(clause.body(), append(path, 1, index), vector);
        vector.leaveStructure(clauseStructure);
        if (index + 1 < guarded.exceptClauses().size()) {
          vector.bytecode();
          vector.label();
        }
      }
    }

    private void emitTryFinally(Ast.Try guarded, List<Integer> path, VectorBuilder vector) {
      int baseDepth = vector.stackDepth();
      AstPath ownerPath = astPath(path);
      vector.extended();
      LabelReference handlerLabel = vector.labelReference();
      PendingToastFinally pendingFinally = vector.finallyLabel(ownerPath, handlerLabel);
      vector.push(1);
      PendingProtectedFinally protectedStructure =
          new PendingProtectedFinally(
              ownerPath, pendingFinally, baseDepth, vector.stackDepth() - 1);
      vector.enterStructure(protectedStructure);
      emitStatements(guarded.body(), append(path, 0), vector);
      vector.leaveStructure(protectedStructure);
      vector.extended();
      vector.pop(1);
      vector.define(handlerLabel);
      vector.push(2);
      PendingFinallyContinuation continuation =
          new PendingFinallyContinuation(
              ownerPath,
              pendingFinally,
              baseDepth,
              baseDepth,
              baseDepth + 1,
              vector.exitTargetsForFinally(ownerPath, baseDepth));
      vector.enterStructure(continuation);
      emitStatements(guarded.finallyClause().orElseThrow().body(), append(path, 2), vector);
      vector.leaveStructure(continuation);
      vector.extended();
      vector.pop(2);
    }

    private void emitErrorCodes(Ast.ErrorSelector errors, VectorBuilder vector) {
      if (errors instanceof Ast.AnyErrors) {
        vector.bytecode();
        vector.push(1);
        return;
      }
      Ast.ErrorList list = (Ast.ErrorList) errors;
      for (int index = 0; index < list.names().size(); index++) {
        vector.literal(new LiteralKey("error", list.names().get(index).toLowerCase(Locale.ROOT)));
        vector.bytecode();
        if (index > 0) {
          vector.pop(1);
        }
      }
    }

    private static ToastErrorSelector toastSelector(Ast.ErrorSelector errors) {
      if (errors instanceof Ast.AnyErrors) {
        return new ToastErrorSelector(true, List.of());
      }
      Ast.ErrorList list = (Ast.ErrorList) errors;
      return new ToastErrorSelector(false, list.names());
    }

    private static Optional<CollectionKind> collectionKind(Ast.Expression expression) {
      if (expression instanceof Ast.ListLiteral) {
        return Optional.of(CollectionKind.LIST);
      }
      if (expression instanceof Ast.StringLiteral) {
        return Optional.of(CollectionKind.STRING);
      }
      if (expression instanceof Ast.MapLiteral) {
        return Optional.of(CollectionKind.MAP);
      }
      return Optional.empty();
    }

    private static Optional<RangeKind> rangeKind(
        Ast.Expression start, Ast.Expression end) {
      if (start instanceof Ast.IntegerLiteral && end instanceof Ast.IntegerLiteral) {
        return Optional.of(RangeKind.INTEGER);
      }
      if (start instanceof Ast.ObjectLiteral && end instanceof Ast.ObjectLiteral) {
        return Optional.of(RangeKind.OBJECT);
      }
      return Optional.empty();
    }

    private static List<Integer> append(List<Integer> prefix, int... components) {
      List<Integer> result = new ArrayList<>(prefix);
      for (int component : components) {
        result.add(component);
      }
      return List.copyOf(result);
    }

    private static AstPath astPath(List<Integer> path) {
      return new AstPath(path);
    }

    private void emitExpression(
        Ast.Expression expression, List<Integer> path, VectorBuilder vector) {
      if (expression instanceof Ast.IntegerLiteral integer) {
        if (integer.value() >= -10 && integer.value() <= 132) {
          vector.bytecode();
          vector.push(1);
        } else {
          vector.literal(new LiteralKey("integer", integer.value()));
        }
        return;
      }
      if (expression instanceof Ast.FloatLiteral floating) {
        vector.literal(
            new LiteralKey("float", Double.doubleToRawLongBits(floating.value())));
        return;
      }
      if (expression instanceof Ast.StringLiteral string) {
        vector.literal(new LiteralKey("string", string.value()));
        return;
      }
      if (expression instanceof Ast.ObjectLiteral object) {
        vector.literal(new LiteralKey("object", object.value()));
        return;
      }
      if (expression instanceof Ast.ErrorLiteral error) {
        vector.literal(new LiteralKey("error", error.name().toLowerCase(Locale.ROOT)));
        return;
      }
      if (expression instanceof Ast.Identifier identifier) {
        vector.variable(symbol(identifier.name()));
        vector.push(1);
        return;
      }
      if (expression instanceof Ast.ListLiteral list) {
        emitArguments(list.elements(), path, 0, vector);
        return;
      }
      if (expression instanceof Ast.MapLiteral map) {
        vector.bytecode();
        vector.push(1);
        for (int index = 0; index < map.entries().size(); index++) {
          Ast.MapEntry entry = map.entries().get(index);
          emitExpression(entry.value(), append(path, 0, index, 1), vector);
          emitExpression(entry.key(), append(path, 0, index, 0), vector);
          vector.bytecode();
          vector.pop(2);
        }
        return;
      }
      if (expression instanceof Ast.Splice splice) {
        emitExpression(splice.value(), append(path, 0), vector);
        return;
      }
      if (expression instanceof Ast.ScatterElement scatter) {
        scatter.defaultValue()
            .ifPresent(value -> emitExpression(value, append(path, 0), vector));
        return;
      }
      if (expression instanceof Ast.Call call) {
        emitArguments(call.arguments(), path, 0, vector);
        vector.call(astPath(path), CallKind.BUILTIN);
        return;
      }
      if (expression instanceof Ast.VerbCall call) {
        emitExpression(call.object(), append(path, 0), vector);
        emitExpression(call.name(), append(path, 1), vector);
        emitArguments(call.arguments(), path, 2, vector);
        vector.call(astPath(path), CallKind.VERB);
        vector.pop(2);
        return;
      }
      if (expression instanceof Ast.PropertyAccess property) {
        emitExpression(property.object(), append(path, 0), vector);
        emitExpression(property.property(), append(path, 1), vector);
        vector.bytecode();
        vector.pop(1);
        return;
      }
      if (expression instanceof Ast.IndexAccess index) {
        emitExpression(index.collection(), append(path, 0), vector);
        emitExpression(index.index(), append(path, 1), vector);
        vector.bytecode();
        vector.pop(1);
        return;
      }
      if (expression instanceof Ast.RangeAccess range) {
        emitExpression(range.collection(), append(path, 0), vector);
        emitExpression(range.start(), append(path, 1), vector);
        emitExpression(range.end(), append(path, 2), vector);
        vector.bytecode();
        vector.pop(2);
        return;
      }
      if (expression instanceof Ast.FirstIndex || expression instanceof Ast.LastIndex) {
        vector.extended();
        vector.stack();
        vector.push(1);
        return;
      }
      if (expression instanceof Ast.Assignment assignment) {
        emitAssignment(assignment, path, vector);
        return;
      }
      if (expression instanceof Ast.Unary unary) {
        emitExpression(unary.operand(), append(path, 0), vector);
        vector.bytecode();
        return;
      }
      if (expression instanceof Ast.Binary binary) {
        emitExpression(binary.left(), append(path, 0), vector);
        if (binary.operator() == Ast.BinaryOperator.AND
            || binary.operator() == Ast.BinaryOperator.OR) {
          vector.bytecode();
          vector.label();
          vector.pop(1);
          emitExpression(binary.right(), append(path, 1), vector);
        } else {
          emitExpression(binary.right(), append(path, 1), vector);
          if (binary.operator() == Ast.BinaryOperator.POWER) {
            vector.extended();
          } else {
            vector.bytecode();
          }
          vector.pop(1);
        }
        return;
      }
      if (expression instanceof Ast.Ternary ternary) {
        emitExpression(ternary.condition(), append(path, 0), vector);
        vector.bytecode();
        vector.label();
        vector.pop(1);
        emitExpression(ternary.trueExpression(), append(path, 1), vector);
        vector.bytecode();
        vector.label();
        vector.pop(1);
        emitExpression(ternary.falseExpression(), append(path, 2), vector);
        return;
      }
      if (expression instanceof Ast.Catch caught) {
        int baseDepth = vector.stackDepth();
        AstPath ownerPath = astPath(path);
        emitErrorCodes(caught.errors(), vector);
        vector.extended();
        LabelReference handlerLabel = vector.labelReference();
        PendingToastHandlerGroup group =
            vector.handlerGroup(
                ownerPath,
                List.of(
                    new PendingToastClause(
                        astPath(append(path, 2, 0)),
                        toastSelector(caught.errors()),
                        handlerLabel)));
        vector.push(1);
        vector.extended();
        vector.push(1);
        PendingCatchGroup protectedStructure =
            new PendingCatchGroup(
                ownerPath,
                group,
                baseDepth,
                OptionalInt.of(vector.stackDepth() - 1),
                StructuralPhase.PROTECTED,
                OptionalInt.empty());
        vector.enterStructure(protectedStructure);
        emitExpression(caught.guarded(), append(path, 0), vector);
        vector.leaveStructure(protectedStructure);
        vector.extended();
        vector.label();
        vector.pop(3);
        vector.define(handlerLabel);
        if (caught.fallback().isPresent()) {
          vector.bytecode();
          vector.pop(1);
          PendingCatchGroup fallbackStructure =
              new PendingCatchGroup(
                  ownerPath,
                  group,
                  baseDepth,
                  OptionalInt.empty(),
                  StructuralPhase.EXPRESSION_FALLBACK,
                  OptionalInt.of(0));
          vector.enterStructure(fallbackStructure);
          emitExpression(caught.fallback().orElseThrow(), append(path, 1), vector);
          vector.leaveStructure(fallbackStructure);
        } else {
          vector.bytecode();
          vector.bytecode();
        }
        return;
      }
      throw new IllegalArgumentException("unsupported v17 expression " + expression);
    }

    private void emitArguments(
        List<Ast.Expression> arguments,
        List<Integer> path,
        int childSlot,
        VectorBuilder vector) {
      if (arguments.isEmpty()) {
        vector.bytecode();
        vector.push(1);
        return;
      }
      for (int index = 0; index < arguments.size(); index++) {
        Ast.Expression argument = arguments.get(index);
        Ast.Expression value = argument instanceof Ast.Splice splice ? splice.value() : argument;
        List<Integer> argumentPath = append(path, childSlot, index);
        if (argument instanceof Ast.Splice) {
          argumentPath = append(argumentPath, 0);
        }
        emitExpression(value, argumentPath, vector);
        vector.bytecode();
        if (index > 0) {
          vector.pop(1);
        }
      }
    }

    private void emitAssignment(
        Ast.Assignment assignment, List<Integer> path, VectorBuilder vector) {
      Ast.AssignmentTarget target = assignment.target();
      if (target instanceof Ast.VariableTarget variable) {
        emitExpression(assignment.value(), append(path, 1), vector);
        vector.variable(symbol(variable.name()));
        return;
      }
      if (target instanceof Ast.PropertyTarget property) {
        emitExpression(property.object(), append(path, 0, 0), vector);
        emitExpression(property.property(), append(path, 0, 1), vector);
        emitExpression(assignment.value(), append(path, 1), vector);
        vector.bytecode();
        vector.pop(2);
        return;
      }
      emitLvalue(target, true, append(path, 0), vector);
      emitExpression(assignment.value(), append(path, 1), vector);
      if (target instanceof Ast.IndexTarget || target instanceof Ast.RangeTarget) {
        vector.bytecode();
      }
      Ast.AssignmentTarget current = target;
      boolean indexed = false;
      while (true) {
        if (current instanceof Ast.RangeTarget range) {
          vector.extended();
          vector.pop(3);
          current = targetFor(range.collection());
          indexed = true;
          continue;
        }
        if (current instanceof Ast.IndexTarget index) {
          vector.bytecode();
          vector.pop(2);
          current = targetFor(index.collection());
          indexed = true;
          continue;
        }
        if (current instanceof Ast.VariableTarget variable) {
          vector.variable(symbol(variable.name()));
        } else if (current instanceof Ast.PropertyTarget) {
          vector.bytecode();
          vector.pop(2);
        } else if (current instanceof Ast.ScatterTarget scatter) {
          emitScatter(scatter, append(path, 0), vector);
        } else {
          throw new IllegalArgumentException("unsupported v17 assignment target " + current);
        }
        break;
      }
      if (indexed) {
        vector.bytecode();
        vector.bytecode();
      }
    }

    private void emitLvalue(
        Ast.AssignmentTarget target, boolean indexedAbove, List<Integer> path, VectorBuilder vector) {
      if (target instanceof Ast.VariableTarget variable) {
        if (indexedAbove) {
          vector.variable(symbol(variable.name()));
          vector.push(1);
        }
        return;
      }
      if (target instanceof Ast.PropertyTarget property) {
        emitExpression(property.object(), append(path, 0), vector);
        emitExpression(property.property(), append(path, 1), vector);
        if (indexedAbove) {
          vector.bytecode();
          vector.push(1);
        }
        return;
      }
      if (target instanceof Ast.IndexTarget index) {
        emitLvalue(targetFor(index.collection()), true, append(path, 0), vector);
        emitExpression(index.index(), append(path, 1), vector);
        if (indexedAbove) {
          vector.bytecode();
          vector.push(1);
        }
        return;
      }
      if (target instanceof Ast.RangeTarget range) {
        emitLvalue(targetFor(range.collection()), true, append(path, 0), vector);
        emitExpression(range.start(), append(path, 1), vector);
        emitExpression(range.end(), append(path, 2), vector);
        return;
      }
      if (target instanceof Ast.ScatterTarget) {
        return;
      }
      throw new IllegalArgumentException("unsupported v17 lvalue " + target);
    }

    private static Ast.AssignmentTarget targetFor(Ast.Expression expression) {
      if (expression instanceof Ast.Identifier identifier) {
        return new Ast.VariableTarget(identifier.name());
      }
      if (expression instanceof Ast.PropertyAccess property) {
        return new Ast.PropertyTarget(property.object(), property.property());
      }
      if (expression instanceof Ast.IndexAccess index) {
        return new Ast.IndexTarget(index.collection(), index.index());
      }
      if (expression instanceof Ast.RangeAccess range) {
        return new Ast.RangeTarget(range.collection(), range.start(), range.end());
      }
      throw new IllegalArgumentException("unsupported v17 lvalue base " + expression);
    }

    private void emitScatter(
        Ast.ScatterTarget scatter, List<Integer> path, VectorBuilder vector) {
      vector.extended();
      vector.bytecode();
      vector.bytecode();
      vector.bytecode();
      for (Ast.ScatterElement element : scatter.elements()) {
        vector.variableOperand(symbol(element.name()));
        vector.label();
      }
      vector.label();
      for (int index = 0; index < scatter.elements().size(); index++) {
        Ast.ScatterElement element = scatter.elements().get(index);
        if (element.defaultValue().isPresent()) {
          emitExpression(element.defaultValue().orElseThrow(), append(path, index, 0), vector);
          vector.variable(symbol(element.name()));
          vector.bytecode();
          vector.pop(1);
        }
      }
    }
  }
}
