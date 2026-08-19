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
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.RandomizeIdVisitor;
import org.openrewrite.java.TypeUtils;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

/** Deletes the dead WorldTxn surface and moves its exact static property-layout closure. */
public final class DecomposeWorldTxn extends ScanningRecipe<DecomposeWorldTxn.Accumulator> {
  private static final String SOURCE_TYPE = "moo.world.WorldTxn";
  private static final String TARGET_SIMPLE_NAME = "PropertyLayoutEngine";
  private static final String TARGET_FILE_NAME = TARGET_SIMPLE_NAME + ".java";
  private static final Set<String> ENGINE_IMPORTS =
      Set.of(
          "java.util.ArrayList",
          "java.util.LinkedHashMap",
          "java.util.LinkedHashSet",
          "java.util.List",
          "java.util.Locale",
          "java.util.Map",
          "java.util.Objects",
          "java.util.Optional",
          "java.util.Set");
  private static final List<MethodSpec> MOVED_METHODS =
      List.of(
          moved("ancestryFromParents", true, "java.util.List", "java.util.Map"),
          moved(
              "collectAncestry",
              false,
              "long",
              "java.util.Map",
              "java.util.Set",
              "java.util.Set",
              "java.util.List"),
          moved(
              "inheritedProperties", true, "java.util.List", "long", "java.util.Map"),
          moved(
              "rebuildPropertyLayouts",
              true,
              "java.util.Map",
              "java.util.Map",
              "java.util.Set"),
          moved(
              "rebuildPropertyLayout",
              false,
              "long",
              "java.util.Map",
              "java.util.Map",
              "java.util.Map",
              "java.util.Set",
              "java.util.Set"),
          moved("oldPropertySlots", false, "moo.world.WorldObject", "java.util.Map"),
          moved("descendantsOf", true, "java.util.Set", "java.util.Map"),
          moved(
              "directParentProperty",
              false,
              "java.util.List",
              "java.lang.String",
              "java.util.Map"),
          moved(
              "usesAffectedAncestor",
              true,
              "java.util.List",
              "java.util.Map",
              "java.util.Set"),
          moved(
              "rebuiltAnonymousProperties",
              true,
              "moo.world.WorldAnonymousObject",
              "java.util.List",
              "java.util.Map",
              "moo.world.WorldAnonymousObject",
              "java.util.List",
              "java.util.Map"));
  private static final Map<MethodKey, MethodSpec> MOVED_BY_KEY = index(MOVED_METHODS);
  private static final Set<String> MOVED_NAMES =
      MOVED_METHODS.stream().map(MethodSpec::name).collect(java.util.stream.Collectors.toSet());
  private static final List<MethodKey> DEAD_METHODS =
      List.of(
          key("baseRevision"),
          key("changeParent", "long", "long"),
          key("restoreIntrinsicCommands", "long"));
  private static final Set<String> DEAD_NAMES =
      DEAD_METHODS.stream().map(MethodKey::name).collect(java.util.stream.Collectors.toSet());
  private static final MethodKey CHANGE_PARENT = key("changeParent", "long", "long");

  @Override
  public String getDisplayName() {
    return "Decompose WorldTxn";
  }

  @Override
  public String getDescription() {
    return "Deletes exact dead WorldTxn methods, generates PropertyLayoutEngine from the captured "
        + "static closure, and migrates the retained caller.";
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
        if (compilationUnit.getSourcePath().getFileName().toString().equals(TARGET_FILE_NAME)) {
          accumulator.targetCollision = true;
        }
        for (J.ClassDeclaration candidate : compilationUnit.getClasses()) {
          if (candidate.getType() == null
              || !SOURCE_TYPE.equals(candidate.getType().getFullyQualifiedName())) {
            continue;
          }
          if (accumulator.source != null
              || !compilationUnit.getSourcePath().getFileName().toString().equals("WorldTxn.java")) {
            accumulator.invalid = true;
            continue;
          }
          accumulator.source = compilationUnit;
          accumulator.sourceClass = candidate;
          accumulator.sourceClassId = candidate.getId();
          captureMembers(candidate, accumulator);
        }
        return super.visitCompilationUnit(compilationUnit, context);
      }

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext context) {
        J.MethodInvocation visited = super.visitMethodInvocation(method, context);
        MethodKey methodKey = key(visited.getMethodType());
        if (CHANGE_PARENT.equals(methodKey) && isWorldTxnMethod(visited.getMethodType())) {
          accumulator.changeParentCallers++;
          if (!(visited.getSelect() instanceof J.Identifier)
              || visited.getArguments().size() != 2
              || !(visited.getArguments().get(0) instanceof J.Identifier)
              || !(visited.getArguments().get(1) instanceof J.Identifier)) {
            accumulator.invalid = true;
          }
        }
        return visited;
      }
    };
  }

  @Override
  public Collection<SourceFile> generate(
      Accumulator accumulator,
      Collection<SourceFile> generatedInThisCycle,
      ExecutionContext context) {
    boolean targetGenerated =
        generatedInThisCycle.stream()
            .anyMatch(
                source -> source.getSourcePath().getFileName().toString().equals(TARGET_FILE_NAME));
    if (!accumulator.ready() || targetGenerated) {
      return emptyList();
    }
    return List.of(generatedEngine(accumulator, context));
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator accumulator) {
    if (!accumulator.ready()) {
      return TreeVisitor.noop();
    }
    return new JavaIsoVisitor<ExecutionContext>() {
      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext context) {
        J.MethodInvocation visited = super.visitMethodInvocation(method, context);
        MethodKey methodKey = key(visited.getMethodType());
        if (CHANGE_PARENT.equals(methodKey) && isWorldTxnMethod(visited.getMethodType())) {
          maybeAddImport("java.util.List");
          return JavaTemplate.builder("#{any()}.changeParents(#{any()}, List.of(#{any()}))")
              .imports("java.util.List")
              .contextSensitive()
              .build()
              .apply(
                  updateCursor(visited),
                  visited.getCoordinates().replace(),
                  visited.getSelect(),
                  visited.getArguments().get(0),
                  visited.getArguments().get(1));
        }
        if (MOVED_BY_KEY.containsKey(methodKey)
            && isWorldTxnMethod(visited.getMethodType())
            && sourceClass(getCursor(), accumulator)) {
          J.Identifier owner =
              accumulator
                  .sourceClass
                  .getName()
                  .withId(randomId())
                  .withSimpleName(TARGET_SIMPLE_NAME)
                  .withPrefix(Space.EMPTY)
                  .withType(null);
          return visited
              .withSelect(owner)
              .withName(visited.getName().withType(null))
              .withMethodType(null);
        }
        return visited;
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
                .filter(statement -> !accumulator.removedIds.contains(statement.getId()))
                .toList();
        return visited.withBody(visited.getBody().withStatements(retained));
      }
    };
  }

  private static void captureMembers(J.ClassDeclaration source, Accumulator accumulator) {
    for (Statement statement : source.getBody().getStatements()) {
      if (statement instanceof J.MethodDeclaration method) {
        if (method.isConstructor() && accumulator.sourceConstructor == null) {
          accumulator.sourceConstructor = method;
        }
        MethodKey methodKey = key(method.getMethodType());
        if (methodKey == null) {
          if (MOVED_NAMES.contains(method.getSimpleName()) || DEAD_NAMES.contains(method.getSimpleName())) {
            accumulator.invalid = true;
          }
          continue;
        }
        MethodSpec moved = MOVED_BY_KEY.get(methodKey);
        if (moved != null) {
          if (!method.hasModifier(J.Modifier.Type.Private)
              || !method.hasModifier(J.Modifier.Type.Static)
              || accumulator.moved.putIfAbsent(methodKey, method) != null) {
            accumulator.invalid = true;
            continue;
          }
          accumulator.movedStatements.add(method);
          accumulator.removedIds.add(method.getId());
          if (accumulator.privateModifier == null) {
            accumulator.privateModifier =
                method.getModifiers().stream()
                    .filter(modifier -> modifier.getType() == J.Modifier.Type.Private)
                    .findFirst()
                    .orElse(null);
          }
          continue;
        }
        if (MOVED_NAMES.contains(method.getSimpleName())
            && method.hasModifier(J.Modifier.Type.Static)) {
          accumulator.invalid = true;
          continue;
        }
        if (DEAD_METHODS.contains(methodKey)) {
          if (accumulator.dead.putIfAbsent(methodKey, method.getId()) != null) {
            accumulator.invalid = true;
          } else {
            accumulator.removedIds.add(method.getId());
          }
        } else if (DEAD_NAMES.contains(method.getSimpleName())) {
          accumulator.invalid = true;
        }
      } else if (statement instanceof J.ClassDeclaration nested
          && nested.getSimpleName().equals("PropertyDefinition")) {
        if (accumulator.propertyDefinition != null
            || !isExactPropertyDefinition(nested)) {
          accumulator.invalid = true;
        } else {
          accumulator.propertyDefinition = nested;
          accumulator.movedStatements.add(nested);
          accumulator.removedIds.add(nested.getId());
        }
      }
    }
  }

  private static boolean isExactPropertyDefinition(J.ClassDeclaration declaration) {
    List<Statement> components = declaration.getPrimaryConstructor();
    return declaration.hasModifier(J.Modifier.Type.Private)
        && declaration.getKind() == J.ClassDeclaration.Kind.Type.Record
        && components.size() == 2
        && isRecordComponent(components.get(0), "long", "objectId")
        && isRecordComponent(components.get(1), "java.lang.String", "name")
        && declaration.getBody().getStatements().isEmpty();
  }

  private static boolean isRecordComponent(
      Statement component, String expectedType, String expectedName) {
    return component instanceof J.VariableDeclarations variables
        && variables.getVariables().size() == 1
        && variables.getVariables().getFirst().getSimpleName().equals(expectedName)
        && (variables.getType() == JavaType.Primitive.String
            ? expectedType.equals("java.lang.String")
            : TypeUtils.isOfClassType(variables.getType(), expectedType));
  }

  private static SourceFile generatedEngine(Accumulator accumulator, ExecutionContext context) {
    List<Statement> members = new ArrayList<>();
    members.add(privateConstructor(accumulator));
    for (Statement statement : accumulator.movedStatements) {
      if (statement instanceof J.MethodDeclaration method) {
        MethodSpec spec = MOVED_BY_KEY.get(key(method.getMethodType()));
        if (spec != null && spec.packageVisible()) {
          statement = withoutPrivate(method);
        }
      } else if (statement instanceof J.ClassDeclaration nested) {
        statement = withoutPrivate(nested);
      }
      members.add(statement);
    }

    J.ClassDeclaration sourceClass = accumulator.sourceClass;
    J.ClassDeclaration engine =
        sourceClass
            .withId(randomId())
            .withPrefix(Space.format("\n\n"))
            .withLeadingAnnotations(emptyList())
            .withName(
                sourceClass
                    .getName()
                    .withId(randomId())
                    .withSimpleName(TARGET_SIMPLE_NAME)
                    .withType(null))
            .withType(null)
            .withModifiers(
                sourceClass.getModifiers().stream()
                    .filter(modifier -> modifier.getType() != J.Modifier.Type.Public)
                    .toList())
            .withExtends(null)
            .withImplements(emptyList())
            .withBody(sourceClass.getBody().withStatements(members));
    Path target = accumulator.source.getSourcePath().resolveSibling(TARGET_FILE_NAME);
    J.CompilationUnit generated =
        accumulator
            .source
            .withId(randomId())
            .withSourcePath(target)
            .withImports(
                accumulator.source.getImports().stream()
                    .filter(anImport -> ENGINE_IMPORTS.contains(anImport.getTypeName()))
                    .toList())
            .withClasses(List.of(engine));
    generated =
        (J.CompilationUnit)
            new RandomizeIdVisitor<ExecutionContext>().visitNonNull(generated, context);
    return (SourceFile)
        new AutoFormatVisitor<ExecutionContext>(null).visitNonNull(generated, context);
  }

  private static J.MethodDeclaration privateConstructor(Accumulator accumulator) {
    J.MethodDeclaration constructor = accumulator.sourceConstructor;
    J.Modifier privateModifier =
        accumulator.privateModifier.withId(randomId()).withPrefix(Space.EMPTY);
    return constructor
        .withId(randomId())
        .withPrefix(Space.format("\n\n  "))
        .withLeadingAnnotations(emptyList())
        .withModifiers(List.of(privateModifier))
        .withName(
            constructor
                .getName()
                .withId(randomId())
                .withSimpleName(TARGET_SIMPLE_NAME)
                .withType(null))
        .withParameters(emptyList())
        .withThrows(emptyList())
        .withBody(constructor.getBody().withStatements(emptyList()))
        .withMethodType(null);
  }

  private static J.MethodDeclaration withoutPrivate(J.MethodDeclaration method) {
    List<J.Modifier> modifiers =
        new ArrayList<>(
            method.getModifiers().stream()
                .filter(modifier -> modifier.getType() != J.Modifier.Type.Private)
                .toList());
    if (!modifiers.isEmpty()) {
      modifiers.set(0, modifiers.get(0).withPrefix(Space.EMPTY));
    }
    return method.withModifiers(modifiers);
  }

  private static J.ClassDeclaration withoutPrivate(J.ClassDeclaration declaration) {
    List<J.Modifier> modifiers =
        new ArrayList<>(
            declaration.getModifiers().stream()
                .filter(modifier -> modifier.getType() != J.Modifier.Type.Private)
                .toList());
    J.ClassDeclaration promoted = declaration.withModifiers(modifiers);
    if (!modifiers.isEmpty()) {
      modifiers.set(0, modifiers.get(0).withPrefix(Space.EMPTY));
      return promoted.withModifiers(modifiers);
    }
    return promoted
        .getPadding()
        .withKind(promoted.getPadding().getKind().withPrefix(Space.EMPTY));
  }

  private static boolean sourceClass(org.openrewrite.Cursor cursor, Accumulator accumulator) {
    J.ClassDeclaration owner = cursor.firstEnclosing(J.ClassDeclaration.class);
    return owner != null && owner.getId().equals(accumulator.sourceClassId);
  }

  private static boolean isWorldTxnMethod(JavaType.Method methodType) {
    return methodType != null && TypeUtils.isOfClassType(methodType.getDeclaringType(), SOURCE_TYPE);
  }

  private static MethodKey key(JavaType.Method methodType) {
    if (methodType == null) {
      return null;
    }
    List<String> parameters = new ArrayList<>();
    for (JavaType parameter : methodType.getParameterTypes()) {
      JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(parameter);
      if (fullyQualified != null) {
        parameters.add(fullyQualified.getFullyQualifiedName());
      } else if (parameter == JavaType.Primitive.String) {
        parameters.add("java.lang.String");
      } else if (parameter instanceof JavaType.Primitive primitive) {
        parameters.add(primitive.getKeyword());
      } else {
        parameters.add(parameter.toString());
      }
    }
    return new MethodKey(methodType.getName(), List.copyOf(parameters));
  }

  private static MethodSpec moved(
      String name, boolean packageVisible, String... parameterTypes) {
    return new MethodSpec(name, List.of(parameterTypes), packageVisible);
  }

  private static MethodKey key(String name, String... parameterTypes) {
    return new MethodKey(name, List.of(parameterTypes));
  }

  private static Map<MethodKey, MethodSpec> index(List<MethodSpec> methods) {
    Map<MethodKey, MethodSpec> indexed = new LinkedHashMap<>();
    for (MethodSpec method : methods) {
      indexed.put(new MethodKey(method.name(), method.parameterTypes()), method);
    }
    return Map.copyOf(indexed);
  }

  private record MethodKey(String name, List<String> parameterTypes) {}

  private record MethodSpec(
      String name, List<String> parameterTypes, boolean packageVisible) {}

  static final class Accumulator {
    private J.CompilationUnit source;
    private J.ClassDeclaration sourceClass;
    private UUID sourceClassId;
    private J.MethodDeclaration sourceConstructor;
    private J.Modifier privateModifier;
    private J.ClassDeclaration propertyDefinition;
    private final Map<MethodKey, J.MethodDeclaration> moved = new LinkedHashMap<>();
    private final Map<MethodKey, UUID> dead = new LinkedHashMap<>();
    private final List<Statement> movedStatements = new ArrayList<>();
    private final Set<UUID> removedIds = new LinkedHashSet<>();
    private int changeParentCallers;
    private boolean targetCollision;
    private boolean invalid;

    boolean ready() {
      return !invalid
          && !targetCollision
          && source != null
          && sourceClass != null
          && sourceClassId != null
          && sourceConstructor != null
          && privateModifier != null
          && propertyDefinition != null
          && moved.keySet().equals(MOVED_BY_KEY.keySet())
          && dead.keySet().equals(Set.copyOf(DEAD_METHODS))
          && changeParentCallers == 1;
    }
  }
}
