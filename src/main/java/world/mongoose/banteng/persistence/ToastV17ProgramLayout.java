package world.mongoose.banteng.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.BytecodeProgram.AstPath;
import world.mongoose.banteng.bytecode.BytecodeProgram.Opcode;
import world.mongoose.banteng.bytecode.LayoutCompiler;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.BantengCallSite;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.CallBoundary;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.ContinuationSite;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.EnclosingHandler;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.EnclosingIterate;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.HandlerPhase;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.StructuralStackShape;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.ToastControlLabels;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.ToastFinallyLabel;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.ToastHandlerGroup;

/** Queries pinned Toast compiler v17 program-vector layouts for suspended-call import. */
public final class ToastV17ProgramLayout {
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
    return new LayoutCompiler(source).callBoundaries(vector);
  }

  /** Resolves active Toast catch/finally labels at one structural continuation path. */
  public ToastControlLabels resolveToastControls(
      String source, int vector, AstPath continuationPath) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(continuationPath, "continuationPath");
    return new LayoutCompiler(source).resolveToastControls(vector, continuationPath);
  }

  /** Resolves the unique Toast catch marker owned by an exact structural path. */
  public ToastHandlerGroup resolveToastHandlerGroup(
      String source, int vector, AstPath ownerPath) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(ownerPath, "ownerPath");
    return new LayoutCompiler(source).resolveToastHandlerGroup(vector, ownerPath);
  }

  /** Resolves the unique Toast finally label owned by an exact structural path. */
  ToastFinallyLabel resolveToastFinallyLabel(String source, int vector, AstPath ownerPath) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(ownerPath, "ownerPath");
    return new LayoutCompiler(source).resolveToastFinallyLabel(vector, ownerPath);
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
    return new LayoutCompiler(source).resolveStructuralStack(vector, boundary, call.program());
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
}
