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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

/** Moves MooVm's static opcode-family methods into their dedicated package owners. */
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
              "setProperty"),
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

  @Override
  public String getDisplayName() {
    return "Move MooVm opcode families";
  }

  @Override
  public String getDescription() {
    return "Moves the existing static MooVm method bodies into six dedicated opcode-family files.";
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
          captureStaticMethods(candidate, accumulator);
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
    return new JavaIsoVisitor<ExecutionContext>() {
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

  private static void captureStaticMethods(
      J.ClassDeclaration source, Accumulator accumulator) {
    for (Statement statement : source.getBody().getStatements()) {
      if (!(statement instanceof J.MethodDeclaration method)
          || !method.hasModifier(J.Modifier.Type.Static)) {
        continue;
      }
      String family = familyFor(method.getSimpleName());
      if (family == null) {
        continue;
      }
      accumulator.methods.get(family).add(method);
      accumulator.movedIds.add(method.getId());
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
                    .withStatements(new ArrayList<>(accumulator.methods.get(family))));
    Path target = accumulator.source.getSourcePath().resolveSibling(family + ".java");
    J.CompilationUnit generated =
        accumulator.source.withId(randomId()).withSourcePath(target).withClasses(List.of(helper));
    generated =
        (J.CompilationUnit)
            new RandomizeIdVisitor<ExecutionContext>().visitNonNull(generated, context);
    return (SourceFile)
        new AutoFormatVisitor<ExecutionContext>(null).visitNonNull(generated, context);
  }

  static final class Accumulator {
    private J.CompilationUnit source;
    private J.ClassDeclaration sourceClass;
    private UUID sourceClassId;
    private final Map<String, List<J.MethodDeclaration>> methods = new LinkedHashMap<>();
    private final Set<UUID> movedIds = new LinkedHashSet<>();
    private final Set<String> collisions = new LinkedHashSet<>();
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
          && collisions.isEmpty()
          && methods.values().stream().noneMatch(List::isEmpty);
    }
  }
}
