package moo.rewrite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

/** Deletes the dead WorldTxn surface and moves its static property-layout algorithms. */
public final class DecomposeWorldTxn extends ScanningRecipe<DecomposeWorldTxn.Accumulator> {
  private static final Set<String> DEAD_METHODS =
      Set.of("baseRevision", "changeParent", "restoreIntrinsicCommands");
  private static final Set<String> MOVED_METHODS =
      Set.of(
          "ancestryFromParents",
          "collectAncestry",
          "inheritedProperties",
          "rebuildPropertyLayouts",
          "rebuildPropertyLayout",
          "oldPropertySlots",
          "descendantsOf",
          "directParentProperty",
          "usesAffectedAncestor",
          "rebuiltAnonymousProperties");
  private static final Set<String> PACKAGE_VISIBLE_METHODS =
      Set.of(
          "ancestryFromParents",
          "inheritedProperties",
          "rebuildPropertyLayouts",
          "descendantsOf",
          "usesAffectedAncestor",
          "rebuiltAnonymousProperties");
  private static final List<String> ENGINE_IMPORTS =
      List.of(
          "java.util.ArrayList",
          "java.util.LinkedHashMap",
          "java.util.LinkedHashSet",
          "java.util.List",
          "java.util.Locale",
          "java.util.Map",
          "java.util.Objects",
          "java.util.Optional",
          "java.util.Set");

  @Override
  public String getDisplayName() {
    return "Decompose WorldTxn";
  }

  @Override
  public String getDescription() {
    return "Deletes dead WorldTxn methods and moves static property-layout algorithms to "
        + "PropertyLayoutEngine.";
  }

  @Override
  public Accumulator getInitialValue(ExecutionContext context) {
    return new Accumulator();
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getScanner(Accumulator accumulator) {
    return new JavaIsoVisitor<>() {
      @Override
      public J.ClassDeclaration visitClassDeclaration(
          J.ClassDeclaration classDeclaration, ExecutionContext context) {
        if (classDeclaration.getSimpleName().equals("WorldTxn")) {
          for (Statement statement : classDeclaration.getBody().getStatements()) {
            if (statement instanceof J.MethodDeclaration method && isMovedDeclaration(method)) {
              accumulator.moved.putIfAbsent(method.getSimpleName(), method);
            } else if (statement instanceof J.ClassDeclaration nested
                && nested.getSimpleName().equals("PropertyDefinition")) {
              accumulator.propertyDefinition = nested;
            }
          }
        }
        return super.visitClassDeclaration(classDeclaration, context);
      }
    };
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator accumulator) {
    return new JavaIsoVisitor<>() {
      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation invocation, ExecutionContext context) {
        J.MethodInvocation candidate = super.visitMethodInvocation(invocation, context);
        J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
        if (owner == null
            || !owner.getSimpleName().equals("WorldTxn")
            || candidate.getSelect() != null
            || !shouldRetargetInvocation(candidate)) {
          return candidate;
        }
        return JavaTemplate.builder("PropertyLayoutEngine." + candidate.printTrimmed(getCursor()))
            .contextSensitive()
            .build()
            .apply(updateCursor(candidate), candidate.getCoordinates().replace());
      }

      @Override
      public J.ClassDeclaration visitClassDeclaration(
          J.ClassDeclaration classDeclaration, ExecutionContext context) {
        J.ClassDeclaration candidate = super.visitClassDeclaration(classDeclaration, context);
        if (candidate.getSimpleName().equals("WorldTxn")) {
          List<Statement> retained = new ArrayList<>();
          for (Statement statement : candidate.getBody().getStatements()) {
            if (statement instanceof J.MethodDeclaration method
                && (DEAD_METHODS.contains(method.getSimpleName())
                    || isMovedDeclaration(method))) {
              continue;
            }
            if (statement instanceof J.ClassDeclaration nested
                && nested.getSimpleName().equals("PropertyDefinition")) {
              continue;
            }
            retained.add(statement);
          }
          return candidate.withBody(candidate.getBody().withStatements(retained));
        }
        if (candidate.getSimpleName().equals("PropertyLayoutEngine")) {
          for (String engineImport : ENGINE_IMPORTS) {
            maybeAddImport(engineImport);
          }
          List<Statement> statements = new ArrayList<>(candidate.getBody().getStatements());
          for (J.MethodDeclaration method : accumulator.moved.values()) {
            if (PACKAGE_VISIBLE_METHODS.contains(method.getSimpleName())) {
              method =
                  method.withModifiers(
                      method.getModifiers().stream()
                          .filter(modifier -> modifier.getType() != J.Modifier.Type.Private)
                          .toList());
            }
            statements.add(method.withPrefix(Space.format("\n\n  ")));
          }
          if (accumulator.propertyDefinition != null) {
            statements.add(accumulator.propertyDefinition.withPrefix(Space.format("\n\n  ")));
          }
          return candidate.withBody(candidate.getBody().withStatements(statements));
        }
        return candidate;
      }
    };
  }

  private static boolean isMovedDeclaration(J.MethodDeclaration method) {
    return MOVED_METHODS.contains(method.getSimpleName())
        && method.getModifiers().stream()
            .anyMatch(modifier -> modifier.getType() == J.Modifier.Type.Static);
  }

  private static boolean shouldRetargetInvocation(J.MethodInvocation invocation) {
    return MOVED_METHODS.contains(invocation.getSimpleName())
        && !invocation.getSimpleName().equals("collectAncestry");
  }

  static final class Accumulator {
    final Map<String, J.MethodDeclaration> moved = new LinkedHashMap<>();
    J.ClassDeclaration propertyDefinition;
  }
}
