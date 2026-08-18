package moo.rewrite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

/** Replaces the legacy ten-parameter builtin lambda protocol with {@code BuiltinCall}. */
public final class UseBuiltinCall extends Recipe {
  private static final String BUILTIN_CALL_TYPE = "moo.builtin.BuiltinCall";
  private static final String BUILTIN_HANDLER_TYPE = "moo.builtin.BuiltinHandler";
  private static final List<String> ACCESSORS =
      List.of(
          "arguments",
          "world",
          "programmer",
          "taskLocal",
          "taskId",
          "remainingTicks",
          "remainingSeconds",
          "receiver",
          "callerProgrammer",
          "callers");

  @Override
  public String getDisplayName() {
    return "Use BuiltinCall for builtin handlers";
  }

  @Override
  public String getDescription() {
    return "Collapses legacy ten-parameter builtin-handler lambdas to a BuiltinCall parameter.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaVisitor<ExecutionContext>() {
      private final JavaTemplate callParameter =
          JavaTemplate.builder("call").contextSensitive().build();

      @Override
      public J visitLambda(J.Lambda lambda, ExecutionContext context) {
        List<J> parameters = lambda.getParameters().getParameters();
        if (parameters.size() != ACCESSORS.size()
            || !TypeUtils.isAssignableTo(BUILTIN_HANDLER_TYPE, lambda.getType())) {
          return super.visitLambda(lambda, context);
        }

        Map<String, String> parameterAccessors = new LinkedHashMap<>();
        Map<JavaType.Variable, String> typedAccessors = new LinkedHashMap<>();
        for (int index = 0; index < parameters.size(); index++) {
          J parameter = parameters.get(index);
          String name = parameterName(parameter);
          if (name == null) {
            return super.visitLambda(lambda, context);
          }
          parameterAccessors.put(name, ACCESSORS.get(index));
          JavaType.Variable variableType = parameterType(parameter);
          if (variableType != null) {
            typedAccessors.put(variableType, ACCESSORS.get(index));
          }
        }

        J rewrittenBody =
            new JavaVisitor<ExecutionContext>() {
              @Override
              public J visitIdentifier(J.Identifier identifier, ExecutionContext ignored) {
                String accessor = typedAccessors.get(identifier.getFieldType());
                if (accessor == null && identifier.getFieldType() == null) {
                  accessor = parameterAccessors.get(identifier.getSimpleName());
                }
                if (accessor == null) {
                  return super.visitIdentifier(identifier, ignored);
                }
                J.Identifier call =
                    identifier
                        .withSimpleName("call")
                        .withType(JavaType.ShallowClass.build(BUILTIN_CALL_TYPE))
                        .withFieldType(null);
                return JavaTemplate.builder(
                        "#{any(" + BUILTIN_CALL_TYPE + ")}." + accessor + "()")
                    .contextSensitive()
                    .build()
                    .apply(getCursor(), identifier.getCoordinates().replace(), call);
              }
            }.visitNonNull(lambda.getBody(), context);

        J.Lambda.Parameters rewrittenParameters =
            callParameter.apply(
                getCursor(), lambda.getParameters().getCoordinates().replace());
        return lambda.withParameters(rewrittenParameters).withBody(rewrittenBody);
      }
    };
  }

  private static String parameterName(J parameter) {
    if (parameter instanceof J.VariableDeclarations declarations
        && declarations.getVariables().size() == 1) {
      return declarations.getVariables().getFirst().getSimpleName();
    }
    if (parameter instanceof J.Identifier identifier) {
      return identifier.getSimpleName();
    }
    return null;
  }

  private static JavaType.Variable parameterType(J parameter) {
    if (parameter instanceof J.VariableDeclarations declarations
        && declarations.getVariables().size() == 1) {
      return declarations.getVariables().getFirst().getVariableType();
    }
    if (parameter instanceof J.Identifier identifier) {
      return identifier.getFieldType();
    }
    return null;
  }
}
