package moo.rewrite;

import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/** Adds the enclosing Toast v17 model import for nested model types being retargeted. */
public final class AddToastV17ProgramModelImport extends Recipe {
  private static final String MODEL_TYPE = "moo.bytecode.ToastV17ProgramModel";
  private static final Set<String> MODEL_MEMBER_NAMES =
      Set.of(
          "BantengCallSite",
          "BantengExitControl",
          "BantengHandlerControl",
          "CallBoundary",
          "CallKind",
          "CatchGroup",
          "CollectionKind",
          "CollectionLoop",
          "ContinuationSite",
          "EnclosingHandler",
          "EnclosingIterate",
          "ExitAction",
          "FinallyContinuation",
          "HandlerPhase",
          "ProtectedFinally",
          "RangeKind",
          "RangeLoop",
          "StructuralPhase",
          "StructuralStackEntry",
          "StructuralStackShape",
          "ToastControlLabels",
          "ToastErrorSelector",
          "ToastExitTarget",
          "ToastFinallyLabel",
          "ToastHandlerClause",
          "ToastHandlerGroup");

  @Override
  public String getDisplayName() {
    return "Add Toast v17 program model import";
  }

  @Override
  public String getDescription() {
    return "Adds the enclosing ToastV17ProgramModel import when legacy or retargeted nested types "
        + "use it.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<ExecutionContext>() {
      private boolean modelOwnerFound;

      @Override
      public J.CompilationUnit visitCompilationUnit(
          J.CompilationUnit compilationUnit, ExecutionContext context) {
        modelOwnerFound = false;
        J.CompilationUnit visited = super.visitCompilationUnit(compilationUnit, context);
        if (!modelOwnerFound) {
          return visited;
        }
        return visited.withMarkers(
            visited.getMarkers().addIfAbsent(PromoteToastV17LayoutCompilers.BANTENG_FORMAT));
      }

      @Override
      public J.FieldAccess visitFieldAccess(
          J.FieldAccess fieldAccess, ExecutionContext context) {
        J.FieldAccess visited = super.visitFieldAccess(fieldAccess, context);
        if (MODEL_MEMBER_NAMES.contains(visited.getName().getSimpleName())
            && isRetargetedModelMember(visited.getName().getType())) {
          modelOwnerFound = true;
          visited = visited.withTarget(typedModelOwner(visited.getTarget()));
          maybeAddImport(MODEL_TYPE);
        }
        return visited;
      }
    };
  }

  private static boolean isRetargetedModelMember(JavaType type) {
    JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
    return fullyQualified != null
        && fullyQualified.getFullyQualifiedName().startsWith(MODEL_TYPE + '$');
  }

  private static Expression typedModelOwner(Expression target) {
    if (target instanceof J.Identifier identifier
        && identifier.getSimpleName().equals("ToastV17ProgramModel")
        && !TypeUtils.isOfClassType(identifier.getType(), MODEL_TYPE)) {
      return identifier.withType(JavaType.ShallowClass.build(MODEL_TYPE));
    }
    return target;
  }
}
