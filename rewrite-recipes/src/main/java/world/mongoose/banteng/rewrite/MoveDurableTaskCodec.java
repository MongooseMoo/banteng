package world.mongoose.banteng.rewrite;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.RandomizeIdVisitor;
import org.openrewrite.java.RemoveUnusedImports;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

/** Moves the scheduler's static durable conversion closure into persistence. */
public final class MoveDurableTaskCodec extends ScanningRecipe<MoveDurableTaskCodec.Members> {
  private static final String SOURCE = "world.mongoose.banteng.runtime.PublicationScheduler";
  private static final Set<String> METHODS = Set.of(
      "restoreQueuedProgram", "shiftSourceLines", "importActivation", "importStructuralStack",
      "importCatchGroup", "handlerState", "toastSelectorValue", "importFinallyState",
      "importFinallyExit", "throwFinally", "importCollectionLoop", "collectionKind",
      "importRangeLoop", "requireControl", "requireExactValue", "requireValue", "requireSlot",
      "translatedDepth", "exportQueuedTask", "durableSuspension", "exportStructuralStack",
      "exportedCatchHandlers", "validateExportedHandlers", "fillOrdinarySlots", "putValue",
      "putControl", "putSlot", "exportFinallyState", "exportCollectionLoop", "exportRangeLoop",
      "exportActivation");
  private static final Set<String> RECORDS = Set.of(
      "ImportedActivationStack", "ImportedHandler", "ImportedHandlerGroup", "ImportedFinally");
  private static final Set<String> API = Set.of(
      "restoreQueuedProgram", "importActivation", "exportQueuedTask", "durableSuspension");

  @Override public String getDisplayName() { return "Move durable task codec"; }
  @Override public String getDescription() {
    return "Moves static durable task conversion and its private records into persistence.";
  }
  @Override public Members getInitialValue(ExecutionContext context) { return new Members(); }

  @Override public TreeVisitor<?, ExecutionContext> getScanner(Members members) {
    return new JavaIsoVisitor<ExecutionContext>() {
      @Override public J.CompilationUnit visitCompilationUnit(J.CompilationUnit unit,
          ExecutionContext context) {
        for (J.ClassDeclaration type : unit.getClasses()) {
          if (type.getType() == null || !SOURCE.equals(type.getType().getFullyQualifiedName())) {
            continue;
          }
          members.source = unit;
          members.owner = type.getId();
          for (Statement statement : type.getBody().getStatements()) {
            if (statement instanceof J.MethodDeclaration method
                && METHODS.contains(method.getSimpleName())
                && method.getModifiers().stream()
                    .anyMatch(modifier -> modifier.getType() == J.Modifier.Type.Static)) {
              members.statements.add(statement);
              members.names.add(method.getSimpleName());
            } else if (statement instanceof J.ClassDeclaration nested
                && RECORDS.contains(nested.getSimpleName())) {
              members.statements.add(statement);
              members.names.add(nested.getSimpleName());
            }
          }
        }
        return unit;
      }
    };
  }

  @Override public Collection<SourceFile> generate(Members members, ExecutionContext context) {
    if (!members.ready()) { return List.of(); }
    J.CompilationUnit skeleton = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
        .parse(context, "package world.mongoose.banteng.persistence;\n"
            + "/** Converts durable task records and VM activation state. */\n"
            + "public final class DurableTaskCodec { private DurableTaskCodec() {} }")
        .findFirst().orElseThrow();
    J.ClassDeclaration type = skeleton.getClasses().getFirst();
    List<Statement> statements = new ArrayList<>(type.getBody().getStatements());
    for (Statement statement : members.statements) {
      if (statement instanceof J.MethodDeclaration method && API.contains(method.getSimpleName())) {
        statement = method.withModifiers(method.getModifiers().stream()
            .map(modifier -> modifier.getType() == J.Modifier.Type.Private
                ? modifier.withType(J.Modifier.Type.Public) : modifier).toList());
      }
      statements.add(statement);
    }
    J.CompilationUnit generated = skeleton.withImports(members.source.getImports())
        .withClasses(List.of(type.withBody(type.getBody().withStatements(statements))))
        .withSourcePath(Path.of("src/main/java/world/mongoose/banteng/persistence/DurableTaskCodec.java"));
    generated = (J.CompilationUnit) new RandomizeIdVisitor<ExecutionContext>()
        .visitNonNull(generated, context);
    generated = (J.CompilationUnit) new RemoveUnusedImports().getVisitor()
        .visitNonNull(generated, context);
    // importActivation contains OptionalLong.empty() references in the generated class.
    generated = (J.CompilationUnit) new AddImport<ExecutionContext>(
        "java.util.OptionalLong", null, false).visitNonNull(generated, context);
    return List.of(generated);
  }

  @Override public TreeVisitor<?, ExecutionContext> getVisitor(Members members) {
    if (!members.ready()) { return TreeVisitor.noop(); }
    return new JavaIsoVisitor<ExecutionContext>() {
      @Override public J.CompilationUnit visitCompilationUnit(J.CompilationUnit unit,
          ExecutionContext context) {
        if (!unit.getId().equals(members.source.getId())) { return unit; }
        maybeAddImport("world.mongoose.banteng.persistence.DurableTaskCodec", false);
        doAfterVisit(new RemoveUnusedImports().getVisitor());
        return super.visitCompilationUnit(unit, context);
      }
      @Override public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration type,
          ExecutionContext context) {
        if (!type.getId().equals(members.owner)) { return type; }
        J.ClassDeclaration retained = type.withBody(type.getBody().withStatements(type.getBody().getStatements().stream()
            .filter(statement -> members.statements.stream()
                .noneMatch(moved -> moved.getId().equals(statement.getId())))
            .toList()));
        return super.visitClassDeclaration(retained, context);
      }
      @Override public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation,
          ExecutionContext context) {
        J.MethodInvocation visited = super.visitMethodInvocation(invocation, context);
        if (visited.getSelect() != null || !API.contains(visited.getSimpleName())) {
          return visited;
        }
        J.Identifier owner = members.source.getClasses().getFirst().getName()
            .withId(org.openrewrite.Tree.randomId()).withSimpleName("DurableTaskCodec")
            .withPrefix(Space.EMPTY).withType(null);
        return visited.withSelect(owner).withName(visited.getName().withType(null))
            .withMethodType(null);
      }
    };
  }

  static final class Members {
    private J.CompilationUnit source;
    private UUID owner;
    private final List<Statement> statements = new ArrayList<>();
    private final Set<String> names = new java.util.HashSet<>();
    private boolean ready() {
      return source != null && names.containsAll(METHODS) && names.containsAll(RECORDS);
    }
  }
}
