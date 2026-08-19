package moo.rewrite;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RandomizeIdVisitor;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

/** Moves MooVm opcode-family methods and their dependencies into dedicated package owners. */
public final class MoveMooVmOpcodeFamilies
    extends ScanningRecipe<MoveMooVmOpcodeFamilies.Accumulator> {
  private static final String SOURCE_TYPE = "moo.vm.MooVm";
  private static final Map<String, Set<String>> METHODS_BY_FAMILY =
      Map.of(
          "ListOps",
          Set.of(
              "buildList", "appendList", "extendList", "pushCheckedList", "buildMap", "mapFrom"),
          "PropertyOps",
          Set.of(
              "loadLocal",
              "containsAnonymousOrWaifReference",
              "isFinalStraightLineLocalRead",
              "getProperty",
              "setProperty",
              "normalize"),
          "IndexOps",
          Set.of(
              "index",
              "boundaryIndex",
              "range",
              "setIndexedLocal",
              "dispatchWaifIndexHandler",
              "replaceIndex",
              "setIndexedProperty",
              "setRangeLocal"),
          "ArithmeticOps",
          Set.of(
              "membership",
              "unaryNegate",
              "bitwiseComplement",
              "bitwise",
              "arithmetic",
              "integerPower",
              "equality",
              "mooEquals",
              "comparison",
              "isMixedIntegerFloat",
              "promoteInteger",
              "numericDouble"),
          "LoopOps",
          Set.of(
              "fork",
              "conditionalJump",
              "leaveHandler",
              "endFinally",
              "iterate",
              "nextCollectionElement",
              "iterateRange",
              "scalar",
              "scalarValue",
              "scatter",
              "routeReturn",
              "target"),
          "ErrorOps",
          Set.of(
              "raiseError",
              "propagateWorldFailure",
              "exceptionTuple",
              "completeErrorDetails",
              "traceback",
              "tracebackFrame",
              "tracebackReference",
              "catches"));
  private static final Set<String> INSTANCE_METHODS =
      Set.of("membership", "arithmetic", "equality", "mooEquals", "comparison");
  private static final String NESTED_LOOP_DEPENDENCY = "CollectionElement";

  @Override
  public String getDisplayName() {
    return "Move MooVm opcode families";
  }

  @Override
  public String getDescription() {
    return "Moves a complete MooVm method closure, promotes instance arithmetic semantics, and "
        + "retargets callers.";
  }

  @Override
  public Accumulator getInitialValue(ExecutionContext context) {
    return new Accumulator();
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getScanner(Accumulator accumulator) {
    return new JavaIsoVisitor<ExecutionContext>() {
      @Override
      public J.CompilationUnit visitCompilationUnit(
          J.CompilationUnit compilationUnit, ExecutionContext context) {
        String fileName = compilationUnit.getSourcePath().getFileName().toString();
        if (METHODS_BY_FAMILY.keySet().stream()
            .map(name -> name + ".java")
            .anyMatch(fileName::equals)) {
          accumulator.collisions.add(fileName);
        }
        for (J.ClassDeclaration candidate : compilationUnit.getClasses()) {
          if (candidate.getType() == null
              || !SOURCE_TYPE.equals(candidate.getType().getFullyQualifiedName())) {
            continue;
          }
          if (accumulator.source != null) {
            accumulator.invalid = true;
            continue;
          }
          accumulator.source = compilationUnit;
          accumulator.sourceClassId = candidate.getId();
          accumulator.sourceClass = candidate;
          captureMembers(candidate, accumulator);
        }
        return compilationUnit;
      }
    };
  }

  @Override
  public Collection<SourceFile> generate(Accumulator accumulator, ExecutionContext context) {
    if (!accumulator.ready()) {
      return emptyList();
    }
    List<SourceFile> generated = new ArrayList<>();
    for (String family : METHODS_BY_FAMILY.keySet().stream().sorted().toList()) {
      generated.add(generatedFamily(accumulator, family, context));
    }
    return generated;
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator accumulator) {
    if (!accumulator.ready()) {
      return TreeVisitor.noop();
    }
    return new InvocationRetargetingVisitor(accumulator) {
      @Override
      public J.CompilationUnit visitCompilationUnit(
          J.CompilationUnit compilationUnit, ExecutionContext context) {
        if (!compilationUnit.getId().equals(accumulator.source.getId())) {
          return compilationUnit;
        }
        return super.visitCompilationUnit(compilationUnit, context);
      }

      @Override
      public J.ClassDeclaration visitClassDeclaration(
          J.ClassDeclaration classDeclaration, ExecutionContext context) {
        J.ClassDeclaration visited = super.visitClassDeclaration(classDeclaration, context);
        if (!visited.getId().equals(accumulator.sourceClassId)) {
          return visited;
        }
        List<Statement> retained =
            visited.getBody().getStatements().stream()
                .filter(statement -> !accumulator.movedIds.contains(statement.getId()))
                .toList();
        return visited.withBody(visited.getBody().withStatements(retained));
      }
    };
  }

  private static void captureMembers(J.ClassDeclaration source, Accumulator accumulator) {
    for (Statement statement : source.getBody().getStatements()) {
      if (statement instanceof J.ClassDeclaration nested
          && NESTED_LOOP_DEPENDENCY.equals(nested.getSimpleName())) {
        if (accumulator.loopDependency != null) {
          accumulator.invalid = true;
        }
        accumulator.loopDependency = nested;
        accumulator.movedIds.add(nested.getId());
        continue;
      }
      if (!(statement instanceof J.MethodDeclaration method)) {
        continue;
      }
      captureValueSemanticsParameter(method, accumulator);
      String family = familyFor(method.getSimpleName());
      if (family == null) {
        continue;
      }
      accumulator.methods.get(family).add(method);
      accumulator.movedIds.add(method.getId());
      method.getModifiers().stream()
          .filter(modifier -> modifier.getType() == J.Modifier.Type.Static)
          .findFirst()
          .ifPresent(modifier -> accumulator.staticModifier = modifier);
    }
  }

  private static void captureValueSemanticsParameter(
      J.MethodDeclaration method, Accumulator accumulator) {
    if (!method.isConstructor()) {
      return;
    }
    for (Statement parameter : method.getParameters()) {
      if (!(parameter instanceof J.VariableDeclarations variables)
          || variables.getVariables().size() != 1
          || !"valueSemantics".equals(variables.getVariables().getFirst().getSimpleName())
          || !(variables.getTypeExpression() instanceof J.Identifier type)
          || !"ValueSemantics".equals(type.getSimpleName())) {
        continue;
      }
      if (accumulator.valueSemanticsParameter != null) {
        accumulator.invalid = true;
      }
      accumulator.valueSemanticsParameter = variables;
    }
  }

  private static String familyFor(String methodName) {
    for (Map.Entry<String, Set<String>> family : METHODS_BY_FAMILY.entrySet()) {
      if (family.getValue().contains(methodName)) {
        return family.getKey();
      }
    }
    return null;
  }

  private static SourceFile generatedFamily(
      Accumulator accumulator, String family, ExecutionContext context) {
    J.ClassDeclaration sourceClass = accumulator.sourceClass;
    List<Statement> members = new ArrayList<>();
    for (J.MethodDeclaration method : accumulator.methods.get(family)) {
      members.add(promotedMethod(method, accumulator, context));
    }
    if (family.equals("LoopOps")) {
      members.add(accumulator.loopDependency);
    }
    J.ClassDeclaration helper =
        sourceClass
            .withId(randomId())
            .withName(sourceClass.getName().withSimpleName(family).withType(null))
            .withType(null)
            .withModifiers(
                sourceClass.getModifiers().stream()
                    .filter(modifier -> modifier.getType() != J.Modifier.Type.Public)
                    .toList())
            .withBody(
                sourceClass
                    .getBody()
                    .withStatements(members));
    Path target = accumulator.source.getSourcePath().resolveSibling(family + ".java");
    J.CompilationUnit generated =
        accumulator.source.withId(randomId()).withSourcePath(target).withClasses(List.of(helper));
    generated =
        (J.CompilationUnit)
            new RandomizeIdVisitor<ExecutionContext>().visitNonNull(generated, context);
    return (SourceFile)
        new AutoFormatVisitor<ExecutionContext>(null).visitNonNull(generated, context);
  }

  private static J.MethodDeclaration promotedMethod(
      J.MethodDeclaration method, Accumulator accumulator, ExecutionContext context) {
    List<J.Modifier> modifiers =
        new ArrayList<>(
            method.getModifiers().stream()
                .filter(modifier -> modifier.getType() != J.Modifier.Type.Private)
                .toList());
    List<Statement> parameters = new ArrayList<>(method.getParameters());
    if (INSTANCE_METHODS.contains(method.getSimpleName())) {
      modifiers.add(accumulator.staticModifier.withId(randomId()));
      parameters.add(
          (J.VariableDeclarations)
              new RandomizeIdVisitor<ExecutionContext>()
                  .visitNonNull(accumulator.valueSemanticsParameter, context));
    }
    J.MethodDeclaration promoted =
        method.withModifiers(modifiers).withParameters(parameters).withMethodType(null);
    return (J.MethodDeclaration)
        new InvocationRetargetingVisitor(accumulator).visitNonNull(promoted, context);
  }

  private static class InvocationRetargetingVisitor extends JavaIsoVisitor<ExecutionContext> {
    private final Accumulator accumulator;

    private InvocationRetargetingVisitor(Accumulator accumulator) {
      this.accumulator = accumulator;
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(
        J.MethodInvocation method, ExecutionContext context) {
      J.MethodInvocation visited = super.visitMethodInvocation(method, context);
      String family = familyFor(visited.getSimpleName());
      if (family == null || visited.getSelect() != null) {
        return visited;
      }
      J.Identifier owner =
          accumulator
              .sourceClass
              .getName()
              .withId(randomId())
              .withSimpleName(family)
              .withPrefix(Space.EMPTY)
              .withType(null);
      List<Expression> arguments = new ArrayList<>(visited.getArguments());
      if (INSTANCE_METHODS.contains(visited.getSimpleName())) {
        arguments.add(
            accumulator
                .valueSemanticsParameter
                .getVariables()
                .getFirst()
                .getName()
                .withId(randomId())
                .withPrefix(Space.SINGLE_SPACE)
                .withType(null));
      }
      return visited
          .withSelect(owner)
          .withArguments(arguments)
          .withName(visited.getName().withType(null))
          .withMethodType(null);
    }
  }

  static final class Accumulator {
    private J.CompilationUnit source;
    private J.ClassDeclaration sourceClass;
    private UUID sourceClassId;
    private final Map<String, List<J.MethodDeclaration>> methods = new LinkedHashMap<>();
    private final Set<UUID> movedIds = new LinkedHashSet<>();
    private final Set<String> collisions = new LinkedHashSet<>();
    private J.VariableDeclarations valueSemanticsParameter;
    private J.Modifier staticModifier;
    private J.ClassDeclaration loopDependency;
    private boolean invalid;

    private Accumulator() {
      METHODS_BY_FAMILY.keySet().stream()
          .sorted()
          .forEach(name -> methods.put(name, new ArrayList<>()));
    }

    boolean ready() {
      return !invalid
          && source != null
          && sourceClass != null
          && sourceClassId != null
          && valueSemanticsParameter != null
          && staticModifier != null
          && loopDependency != null
          && collisions.isEmpty()
          && hasExactMethodClosure();
    }

    private boolean hasExactMethodClosure() {
      for (Map.Entry<String, Set<String>> family : METHODS_BY_FAMILY.entrySet()) {
        Map<String, Long> actual =
            methods.get(family.getKey()).stream()
                .collect(
                    java.util.stream.Collectors.groupingBy(
                        J.MethodDeclaration::getSimpleName,
                        java.util.stream.Collectors.counting()));
        for (String method : family.getValue()) {
          long expected = method.equals("raiseError") ? 4 : 1;
          if (actual.getOrDefault(method, 0L) != expected) {
            return false;
          }
        }
        if (!actual.keySet().equals(family.getValue())) {
          return false;
        }
      }
      return true;
    }
  }
}
