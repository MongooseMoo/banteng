package moo.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.style.ImportLayoutStyle;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.style.Style;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

final class DecomposeToastV17ProgramLayoutTest implements RewriteTest {
  private static final String LEGACY_OWNER = "moo.persistence.ToastV17ProgramLayout";
  private static final String MODEL_TYPE = "moo.bytecode.ToastV17ProgramModel";
  private static final List<TypeMove> TYPE_MOVES =
      List.of(
          move("LayoutCompiler", "moo.bytecode.LayoutCompiler"),
          move("VectorBuilder", "moo.bytecode.VectorBuilder"),
          model("CallKind"),
          model("CallBoundary"),
          model("BantengCallSite"),
          model("HandlerPhase"),
          model("EnclosingHandler"),
          model("EnclosingIterate"),
          model("ContinuationSite"),
          model("ToastErrorSelector"),
          model("ToastHandlerClause"),
          model("ToastHandlerGroup"),
          model("ToastFinallyLabel"),
          model("ToastControlLabels"),
          model("StructuralPhase"),
          model("CollectionKind"),
          model("RangeKind"),
          model("ExitAction"),
          model("BantengExitControl"),
          model("ToastExitTarget"),
          model("BantengHandlerControl"),
          model("StructuralStackEntry"),
          model("CatchGroup"),
          model("ProtectedFinally"),
          model("FinallyContinuation"),
          model("CollectionLoop"),
          model("RangeLoop"),
          model("StructuralStackShape"));

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources("moo.persistence.DecomposeToastV17ProgramLayout")
        .parser(
            JavaParser.fromJavaVersion()
                .dependsOn(
                    legacyLayoutTypes(),
                    targetModelTypes(),
                    targetLayoutCompiler(),
                    targetVectorBuilder()));
  }

  @Test
  void retargetsAllTwentyEightNestedTypes() {
    assertEquals(28, TYPE_MOVES.size());
    rewriteRun(
        spec -> spec.cycles(3).expectedCyclesThatMakeChanges(1),
        java(
            consumerBefore(),
            consumerAfter(),
            source -> source.afterRecipe(DecomposeToastV17ProgramLayoutTest::assertFinalConsumer)),
        java(
            """
            package unrelated;

            import java.util.List;

            final class Unrelated {}
            """,
            """
            package unrelated;

            final class Unrelated {}
            """));
  }

  private static void assertFinalConsumer(J.CompilationUnit compilationUnit) {
    assertEquals(3, compilationUnit.getImports().size());
    assertTrue(
        compilationUnit.getImports().stream()
            .noneMatch(anImport -> anImport.getQualid().getSimpleName().equals("*")));

    List<J.FieldAccess> modelMembers = new ArrayList<>();
    new JavaIsoVisitor<List<J.FieldAccess>>() {
      @Override
      public J.FieldAccess visitFieldAccess(
          J.FieldAccess fieldAccess, List<J.FieldAccess> members) {
        J.FieldAccess visited = super.visitFieldAccess(fieldAccess, members);
        JavaType.FullyQualified fieldType = TypeUtils.asFullyQualified(visited.getName().getType());
        if (fieldType != null
            && fieldType.getFullyQualifiedName().startsWith(MODEL_TYPE + '$')) {
          members.add(visited);
        }
        return visited;
      }
    }.visit(compilationUnit, modelMembers);

    assertEquals(26, modelMembers.size());
    for (J.FieldAccess modelMember : modelMembers) {
      assertTrue(modelMember.getTarget() instanceof J.Identifier);
      J.Identifier owner = (J.Identifier) modelMember.getTarget();
      assertEquals("ToastV17ProgramModel", owner.getSimpleName());
      assertTrue(TypeUtils.isOfClassType(owner.getType(), MODEL_TYPE));
    }

    ImportLayoutStyle importStyle =
        Objects.requireNonNull(Style.from(ImportLayoutStyle.class, compilationUnit));
    assertEquals(Integer.MAX_VALUE, importStyle.getClassCountToUseStarImport());
    assertEquals(Integer.MAX_VALUE, importStyle.getNameCountToUseStarImport());
  }

  private static TypeMove move(String nestedName, String targetType) {
    return new TypeMove(LEGACY_OWNER + "$" + nestedName, targetType);
  }

  private static TypeMove model(String nestedName) {
    return move(nestedName, "moo.bytecode.ToastV17ProgramModel$" + nestedName);
  }

  private static String legacyLayoutTypes() {
    StringBuilder source =
        new StringBuilder(
            """
            package moo.persistence;

            public final class ToastV17ProgramLayout {
            """);
    for (TypeMove move : TYPE_MOVES) {
      source.append("  public static final class ").append(move.oldSimpleName()).append(" {}\n");
    }
    return source.append("}\n").toString();
  }

  private static String targetModelTypes() {
    StringBuilder source =
        new StringBuilder(
            """
            package moo.bytecode;

            public final class ToastV17ProgramModel {
            """);
    for (TypeMove move : TYPE_MOVES.subList(2, TYPE_MOVES.size())) {
      source.append("  public static final class ").append(move.oldSimpleName()).append(" {}\n");
    }
    return source.append("}\n").toString();
  }

  private static String targetLayoutCompiler() {
    return """
        package moo.bytecode;

        public final class LayoutCompiler {}
        """;
  }

  private static String targetVectorBuilder() {
    return """
        package moo.bytecode;

        public final class VectorBuilder {}
        """;
  }

  private static String consumerBefore() {
    StringBuilder source =
        new StringBuilder(
            """
            package example;

            import java.util.Set;

            final class LayoutConsumer {
            """);
    for (TypeMove move : TYPE_MOVES) {
      source
          .append("  ")
          .append(move.oldType().replace('$', '.'))
          .append(' ')
          .append(move.fieldName())
          .append(";\n");
    }
    return source.append("}\n").toString();
  }

  private static String consumerAfter() {
    StringBuilder source =
        new StringBuilder(
            """
            package example;

            import moo.bytecode.LayoutCompiler;
            import moo.bytecode.ToastV17ProgramModel;
            import moo.bytecode.VectorBuilder;

            final class LayoutConsumer {
            """);
    for (TypeMove move : TYPE_MOVES) {
      source
          .append("  ")
          .append(move.targetReference())
          .append(' ')
          .append(move.fieldName())
          .append(";\n");
    }
    return source.append("}\n").toString();
  }

  private record TypeMove(String oldType, String targetType) {
    String oldSimpleName() {
      return oldType.substring(oldType.indexOf('$') + 1);
    }

    String targetReference() {
      int packageBoundary = targetType.lastIndexOf('.');
      return targetType.substring(packageBoundary + 1).replace('$', '.');
    }

    String fieldName() {
      String name = oldSimpleName();
      return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
  }
}
