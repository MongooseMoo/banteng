package world.mongoose.banteng.rewrite;

import static org.openrewrite.Tree.randomId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Cursor;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

/** Moves the closed static tracing family before the runtime's instance collector extraction. */
public final class MoveFinalizationTracing
    extends ScanningRecipe<MoveFinalizationTracing.Accumulator> {
  private static final String SOURCE = "world.mongoose.banteng.runtime.MooRuntime";
  private static final Set<String> METHODS = Set.of(
      "collectWaifReferences", "collectAnonymousReferences",
      "markReachableAnonymous", "markFinalizingAnonymous");

  @Override
  public String getDisplayName() {
    return "Move finalization tracing";
  }

  @Override
  public String getDescription() {
    return "Moves the complete static tracing closure into FinalizationCollector and retargets callers.";
  }

  @Override
  public Accumulator getInitialValue(ExecutionContext context) {
    return new Accumulator();
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getScanner(Accumulator state) {
    return new JavaIsoVisitor<ExecutionContext>() {
      @Override
      public J.CompilationUnit visitCompilationUnit(J.CompilationUnit unit, ExecutionContext ctx) {
        if (unit.getSourcePath().getFileName().toString().equals("FinalizationCollector.java")) {
          state.collision = true;
        }
        for (J.ClassDeclaration owner : unit.getClasses()) {
          if (!TypeUtils.isOfClassType(owner.getType(), SOURCE)) {
            continue;
          }
          state.source = unit;
          state.owner = owner.getId();
          for (Statement member : owner.getBody().getStatements()) {
            if (member instanceof J.MethodDeclaration method
                && METHODS.contains(method.getSimpleName())
                && method.hasModifier(J.Modifier.Type.Static)
                && method.hasModifier(J.Modifier.Type.Private)) {
              state.methods.put(method.getSimpleName(), method);
            }
          }
        }
        return unit;
      }
    };
  }

  @Override
  public Collection<SourceFile> generate(Accumulator state, ExecutionContext ctx) {
    if (!state.ready()) {
      return List.of();
    }
    StringBuilder source = new StringBuilder("package world.mongoose.banteng.runtime;\n\n");
    for (J.Import imported : state.source.getImports()) {
      source.append(imported.printTrimmed(new Cursor(null, state.source))).append(";\n");
    }
    source.append("\nfinal class FinalizationCollector {\n  private FinalizationCollector() {}\n");
    for (J.MethodDeclaration method : state.methods.values()) {
      source.append(method.printTrimmed(new Cursor(null, state.source))
          .replaceFirst("private static", "static")).append('\n');
    }
    source.append("}\n");
    SourceFile generated = JavaParser.fromJavaVersion().build().parse(ctx, source.toString())
        .findFirst().orElseThrow();
    return List.of(generated.withSourcePath(
        state.source.getSourcePath().resolveSibling("FinalizationCollector.java")));
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator state) {
    if (!state.ready()) {
      return TreeVisitor.noop();
    }
    return new JavaIsoVisitor<ExecutionContext>() {
      @Override
      public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
        if (visited.getMethodType() == null
            || !TypeUtils.isOfClassType(visited.getMethodType().getDeclaringType(), SOURCE)
            || !METHODS.contains(visited.getSimpleName())) {
          return visited;
        }
        J.Identifier select = state.source.getClasses().getFirst().getName()
            .withId(randomId()).withSimpleName("FinalizationCollector")
            .withPrefix(Space.EMPTY).withType(null);
        return visited.withSelect(select).withMethodType(null)
            .withName(visited.getName().withType(null));
      }

      @Override
      public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration owner, ExecutionContext ctx) {
        J.ClassDeclaration visited = super.visitClassDeclaration(owner, ctx);
        if (!visited.getId().equals(state.owner)) {
          return visited;
        }
        List<Statement> retained = new ArrayList<>();
        Set<UUID> removed = state.methods.values().stream().map(J.MethodDeclaration::getId)
            .collect(java.util.stream.Collectors.toSet());
        for (Statement member : visited.getBody().getStatements()) {
          if (!removed.contains(member.getId())) {
            retained.add(member);
          }
        }
        return visited.withBody(visited.getBody().withStatements(retained));
      }
    };
  }

  static final class Accumulator {
    private J.CompilationUnit source;
    private UUID owner;
    private final Map<String, J.MethodDeclaration> methods = new LinkedHashMap<>();
    private boolean collision;

    boolean ready() {
      return !collision && source != null && methods.keySet().equals(METHODS);
    }
  }
}
