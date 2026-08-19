package world.mongoose.banteng.bytecode;

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
import world.mongoose.banteng.bytecode.BytecodeProgram.AstPath;
import world.mongoose.banteng.bytecode.BytecodeProgram.Opcode;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.BantengExitControl;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.BantengHandlerControl;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.CallBoundary;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.CallKind;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.CatchGroup;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.CollectionKind;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.CollectionLoop;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.EnclosingIterate;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.ExitAction;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.FinallyContinuation;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.ProtectedFinally;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.RangeKind;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.RangeLoop;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.StructuralPhase;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.StructuralStackEntry;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.StructuralStackShape;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.ToastControlLabels;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.ToastErrorSelector;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.ToastExitTarget;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.ToastFinallyLabel;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.ToastHandlerClause;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel.ToastHandlerGroup;
import world.mongoose.banteng.syntax.Ast;
import world.mongoose.banteng.syntax.MooParser;

/** Compiles Toast v17 vector and control-stack layout from MOO source. */
public final class LayoutCompiler {
  enum UnitKind {
    BYTE,
    LITERAL,
    FORK,
    VARIABLE,
    LABEL,
    STACK
  }

  record PendingCall(
      int errorUnit,
      int endUnit,
      AstPath path,
      CallKind kind,
      int postArgumentDepth,
      List<PendingStructuralEntry> structuresBaseToTop) {}

  static final class LabelReference {
    int targetUnit = -1;
  }

  record PendingToastClause(
      AstPath path, ToastErrorSelector selector, LabelReference label) {}

  record PendingToastHandlerGroup(
      AstPath ownerPath, List<PendingToastClause> clauses) {}

  record PendingToastFinally(AstPath ownerPath, LabelReference label) {}

  record PendingToastExitTarget(
      AstPath exitPath,
      AstPath targetLoopPath,
      int targetStackDepth,
      int bantengOperandDepth,
      LabelReference targetLabel,
      ExitAction action) {}

  record ActiveLoopTarget(
      AstPath ownerPath,
      List<String> variableNames,
      LabelReference topLabel,
      int topStackDepth,
      LabelReference bottomLabel,
      int bottomStackDepth,
      int bantengOperandDepth) {}

  sealed interface PendingStructuralEntry
      permits PendingCatchGroup,
          PendingProtectedFinally,
          PendingFinallyContinuation,
          PendingCollectionLoop,
          PendingRangeLoop {}

  record PendingCatchGroup(
      AstPath ownerPath,
      PendingToastHandlerGroup group,
      int baseDepth,
      OptionalInt markerDepth,
      StructuralPhase phase,
      OptionalInt activeClauseIndex)
      implements PendingStructuralEntry {}

  record PendingProtectedFinally(
      AstPath ownerPath, PendingToastFinally label, int baseDepth, int markerDepth)
      implements PendingStructuralEntry {}

  record PendingFinallyContinuation(
      AstPath ownerPath,
      PendingToastFinally label,
      int baseDepth,
      int reasonDepth,
      int valueDepth,
      List<PendingToastExitTarget> exitTargets)
      implements PendingStructuralEntry {}

  record PendingCollectionLoop(
      AstPath ownerPath,
      int baseDepth,
      int iteratorDepth,
      List<String> variableNames,
      Optional<CollectionKind> staticallyKnownKind)
      implements PendingStructuralEntry {}

  record PendingRangeLoop(
      AstPath ownerPath,
      int baseDepth,
      int nextDepth,
      int endDepth,
      List<String> variableNames,
      Optional<RangeKind> staticallyKnownKind)
      implements PendingStructuralEntry {}

  record VectorLayout(
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

  record ProgramLayout(VectorLayout main, List<VectorLayout> forks) {
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

  static final class GlobalState {
    final LinkedHashSet<LiteralKey> literals = new LinkedHashSet<>();
    final List<VectorLayout> forks = new ArrayList<>();
    int totalVariableReferences;

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

  record LiteralKey(String type, Object value) {}

  private record IndexedControl(
      int index, BytecodeProgram.Instruction instruction, AstPath path) {}

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

  static boolean isBodyDescendant(AstPath owner, AstPath descendant, int bodyComponent) {
    List<Integer> ownerComponents = owner.components();
    List<Integer> descendantComponents = descendant.components();
    return descendantComponents.size() > ownerComponents.size()
        && descendantComponents.subList(0, ownerComponents.size()).equals(ownerComponents)
        && descendantComponents.get(ownerComponents.size()) == bodyComponent;
  }

  static int referenceWidth(int maximum) {
    if (maximum <= 256) {
      return 1;
    }
    return maximum <= 65_536 ? 2 : 4;
  }

  private static final List<String> PREDEFINED_NAMES =
      List.of(
          "num", "obj", "str", "list", "err", "player", "this", "caller", "verb",
          "args", "argstr", "dobj", "dobjstr", "prepstr", "iobj", "iobjstr", "int",
          "float", "map", "anon", "waif", "bool", "true", "false");

  private final Ast.Program program;
  private final Map<String, Integer> symbols = new LinkedHashMap<>();
  private final GlobalState global = new GlobalState();

  /** Parses one MOO source body for Toast v17 layout queries. */
  public LayoutCompiler(String source) {
    this(MooParser.parse(Objects.requireNonNull(source, "source")));
  }

  private LayoutCompiler(Ast.Program program) {
    this.program = program;
    for (String name : PREDEFINED_NAMES) {
      symbols.put(name, symbols.size());
    }
    collectSymbols(program.statements());
  }

  /** Returns every exact call boundary in one Toast vector. */
  public List<CallBoundary> callBoundaries(int vector) {
    return compile().vector(vector).boundaries(vector);
  }

  /** Resolves active Toast catch and finally labels at one continuation path. */
  public ToastControlLabels resolveToastControls(int vector, AstPath continuationPath) {
    Objects.requireNonNull(continuationPath, "continuationPath");
    VectorLayout layout = compile().vector(vector);
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

  /** Resolves the unique Toast catch marker owned by one structural path. */
  public ToastHandlerGroup resolveToastHandlerGroup(int vector, AstPath ownerPath) {
    Objects.requireNonNull(ownerPath, "ownerPath");
    List<ToastHandlerGroup> matches =
        compile().vector(vector).handlerGroups().stream()
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

  /** Resolves the unique Toast finally label owned by one structural path. */
  public ToastFinallyLabel resolveToastFinallyLabel(int vector, AstPath ownerPath) {
    Objects.requireNonNull(ownerPath, "ownerPath");
    List<ToastFinallyLabel> matches =
        compile().vector(vector).finallyLabels().stream()
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

  /** Resolves the structural Toast stack corresponding to one Banteng call. */
  public StructuralStackShape resolveStructuralStack(
      int vector, CallBoundary boundary, BytecodeProgram bantengProgram) {
    Objects.requireNonNull(boundary, "boundary");
    Objects.requireNonNull(bantengProgram, "bantengProgram");
    return compile().vector(vector).structuralStackShape(boundary, bantengProgram);
  }

  private ProgramLayout compile() {
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
