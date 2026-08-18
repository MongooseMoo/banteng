package moo.rewrite;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

/** Replaces the uniform unary-float manifest lambdas with the shared handler factory. */
public final class ConsolidateUnaryFloatHandlers extends Recipe {
  private static final Map<String, String> OPERATORS =
      Map.ofEntries(
          Map.entry("acos", "BuiltinCatalog::acos"),
          Map.entry("acosh", "BuiltinCatalog::acosh"),
          Map.entry("asin", "BuiltinCatalog::asin"),
          Map.entry("asinh", "BuiltinCatalog::asinh"),
          Map.entry("atanh", "BuiltinCatalog::atanh"),
          Map.entry("cbrt", "Math::cbrt"),
          Map.entry("ceil", "Math::ceil"),
          Map.entry("cosine", "BuiltinCatalog::cosine"),
          Map.entry("cosh", "Math::cosh"),
          Map.entry("exp", "Math::exp"),
          Map.entry("floor", "Math::floor"),
          Map.entry("log", "BuiltinCatalog::log"),
          Map.entry("log10", "BuiltinCatalog::log10"),
          Map.entry("sine", "BuiltinCatalog::sine"),
          Map.entry("sinh", "Math::sinh"),
          Map.entry("sqrt", "BuiltinCatalog::sqrt"),
          Map.entry("tangent", "BuiltinCatalog::tangent"),
          Map.entry("tanh", "Math::tanh"));

  @Override
  public String getDisplayName() {
    return "Consolidate unary-float builtin handlers";
  }

  @Override
  public String getDescription() {
    return "Replaces the 18 uniform unary-float manifest lambdas with unaryFloatBuiltin factory calls.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaVisitor<>() {
      @Override
      public boolean isAcceptable(SourceFile sourceFile, ExecutionContext context) {
        return sourceFile
            .getSourcePath()
            .endsWith(Path.of("moo", "builtin", "BuiltinCatalog.java"));
      }

      @Override
      public J visitLambda(J.Lambda lambda, ExecutionContext context) {
        J visited = super.visitLambda(lambda, context);
        if (!(visited instanceof J.Lambda candidate)
            || candidate.getParameters().getParameters().size() != 10
            || !(candidate.getBody() instanceof J.MethodInvocation invocation)) {
          return visited;
        }
        J.MethodDeclaration enclosing =
            getCursor().firstEnclosing(J.MethodDeclaration.class);
        if (enclosing == null || !enclosing.getSimpleName().equals("buildManifest")) {
          return candidate;
        }
        String operator = OPERATORS.get(invocation.getSimpleName());
        List<Expression> arguments = invocation.getArguments();
        if (operator == null
            || invocation.getSelect() != null
            || arguments.size() != 1
            || !(arguments.getFirst() instanceof J.Identifier argument)
            || !argument.getSimpleName().equals("a")) {
          return candidate;
        }
        return JavaTemplate.builder("unaryFloatBuiltin(" + operator + ")")
            .contextSensitive()
            .build()
            .apply(updateCursor(candidate), candidate.getCoordinates().replace());
      }
    };
  }

  static int operatorCount() {
    return OPERATORS.size();
  }
}
