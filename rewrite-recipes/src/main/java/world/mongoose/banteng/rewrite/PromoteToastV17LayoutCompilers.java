package world.mongoose.banteng.rewrite;

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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RandomizeIdVisitor;
import org.openrewrite.java.format.ShiftFormat;
import org.openrewrite.java.style.ImportLayoutStyle;
import org.openrewrite.java.style.TabsAndIndentsStyle;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Markers;
import org.openrewrite.style.NamedStyles;

/** Promotes Toast v17's prepared nested compiler classes into bytecode-owned source files. */
public final class PromoteToastV17LayoutCompilers
    extends ScanningRecipe<PromoteToastV17LayoutCompilers.Accumulator> {
  private static final String OUTER_TYPE = "world.mongoose.banteng.persistence.ToastV17ProgramLayout";
  private static final Path OUTER_SUFFIX =
      Path.of(
          "world", "mongoose", "banteng", "persistence", "ToastV17ProgramLayout.java");
  private static final Path COMPILER_SUFFIX =
      Path.of("world", "mongoose", "banteng", "bytecode", "LayoutCompiler.java");
  private static final Path BUILDER_SUFFIX =
      Path.of("world", "mongoose", "banteng", "bytecode", "VectorBuilder.java");
  private static final List<String> SUPPORT_TYPE_NAMES =
      List.of(
          "UnitKind",
          "PendingCall",
          "LabelReference",
          "PendingToastClause",
          "PendingToastHandlerGroup",
          "PendingToastFinally",
          "PendingToastExitTarget",
          "ActiveLoopTarget",
          "PendingStructuralEntry",
          "PendingCatchGroup",
          "PendingProtectedFinally",
          "PendingFinallyContinuation",
          "PendingCollectionLoop",
          "PendingRangeLoop",
          "VectorLayout",
          "ProgramLayout",
          "GlobalState",
          "LiteralKey",
          "IndexedControl");
  private static final List<String> HELPER_NAMES =
      List.of(
          "handlerControl",
          "iterateControl",
          "exitControl",
          "uniqueControl",
          "isBodyDescendant",
          "referenceWidth");
  private static final Set<String> COPIED_MEMBER_NAMES =
      Set.of("IndexedControl", "isBodyDescendant");
  private static final List<String> VECTOR_SUPPORT_IMPORTS =
      List.of(
          "ActiveLoopTarget",
          "GlobalState",
          "LabelReference",
          "LiteralKey",
          "PendingCall",
          "PendingCatchGroup",
          "PendingProtectedFinally",
          "PendingStructuralEntry",
          "PendingToastClause",
          "PendingToastExitTarget",
          "PendingToastFinally",
          "PendingToastHandlerGroup",
          "UnitKind",
          "VectorLayout");
  static final NamedStyles BANTENG_FORMAT =
      new NamedStyles(
          randomId(),
          "banteng-generated-java",
          "Banteng generated Java",
          null,
          Set.of(),
          List.of(
              new TabsAndIndentsStyle(false, 2, 2, 4, false),
              ImportLayoutStyle.builder()
                  .importStaticAllOthers()
                  .blankLine()
                  .importAllOthers()
                  .classCountToUseStarImport(Integer.MAX_VALUE)
                  .nameCountToUseStarImport(Integer.MAX_VALUE)
                  .build()));

  @Override
  public String getDisplayName() {
    return "Promote Toast v17 layout compilers";
  }

  @Override
  public String getDescription() {
    return "Promotes the prepared nested LayoutCompiler and VectorBuilder into bytecode-owned "
        + "files.";
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
        Path sourcePath = compilationUnit.getSourcePath();
        if (sourcePath.endsWith(COMPILER_SUFFIX)) {
          accumulator.compilerCollision = true;
        }
        if (sourcePath.endsWith(BUILDER_SUFFIX)) {
          accumulator.builderCollision = true;
        }

        for (J.ClassDeclaration candidate : compilationUnit.getClasses()) {
          if (candidate.getType() == null
              || !OUTER_TYPE.equals(candidate.getType().getFullyQualifiedName())) {
            continue;
          }
          if (accumulator.source != null || !sourcePath.endsWith(OUTER_SUFFIX)) {
            accumulator.invalid = true;
            continue;
          }
          accumulator.source = compilationUnit;
          accumulator.outerId = candidate.getId();
          captureOuterMembers(candidate, accumulator);
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
    return List.of(
        generatedCompilationUnit(
            accumulator,
            compilerWithClosure(accumulator, context),
            COMPILER_SUFFIX.getFileName(),
            false,
            context),
        generatedCompilationUnit(
            accumulator,
            promoted(accumulator.builder, false, accumulator.source),
            BUILDER_SUFFIX.getFileName(),
            true,
            context));
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
        if (!visited.getId().equals(accumulator.outerId)) {
          return visited;
        }
        List<Statement> retained = new ArrayList<>();
        for (Statement statement : visited.getBody().getStatements()) {
          if (!accumulator.removedMemberIds.contains(statement.getId())) {
            retained.add(statement);
          }
        }
        return visited.withBody(visited.getBody().withStatements(retained));
      }
    };
  }

  private static void captureOuterMembers(J.ClassDeclaration outer, Accumulator accumulator) {
    for (Statement statement : outer.getBody().getStatements()) {
      if (statement instanceof J.ClassDeclaration nested) {
        String name = nested.getSimpleName();
        if (name.equals("LayoutCompiler")) {
          if (accumulator.compiler != null || !hasExpectedModifiers(nested)) {
            accumulator.invalid = true;
          } else {
            accumulator.compiler = nested;
            accumulator.removedMemberIds.add(nested.getId());
          }
        } else if (name.equals("VectorBuilder")) {
          if (accumulator.builder != null || !hasExpectedModifiers(nested)) {
            accumulator.invalid = true;
          } else {
            accumulator.builder = nested;
            accumulator.removedMemberIds.add(nested.getId());
          }
        } else if (SUPPORT_TYPE_NAMES.contains(name)) {
          captureSupportType(nested, accumulator);
        }
      } else if (statement instanceof J.MethodDeclaration method
          && HELPER_NAMES.contains(method.getSimpleName())) {
        captureHelper(method, accumulator);
      }
    }
  }

  private static void captureSupportType(
      J.ClassDeclaration declaration, Accumulator accumulator) {
    String name = declaration.getSimpleName();
    if (!declaration.hasModifier(J.Modifier.Type.Private)
        || accumulator.supportTypes.putIfAbsent(name, declaration) != null) {
      accumulator.invalid = true;
      return;
    }
    if (!COPIED_MEMBER_NAMES.contains(name)) {
      accumulator.removedMemberIds.add(declaration.getId());
    }
  }

  private static void captureHelper(J.MethodDeclaration method, Accumulator accumulator) {
    String name = method.getSimpleName();
    if (!method.hasModifier(J.Modifier.Type.Private)
        || !method.hasModifier(J.Modifier.Type.Static)
        || accumulator.helpers.putIfAbsent(name, method) != null) {
      accumulator.invalid = true;
      return;
    }
    if (!COPIED_MEMBER_NAMES.contains(name)) {
      accumulator.removedMemberIds.add(method.getId());
    }
  }

  private static boolean hasExpectedModifiers(J.ClassDeclaration declaration) {
    return declaration.hasModifier(J.Modifier.Type.Private)
        && declaration.hasModifier(J.Modifier.Type.Static)
        && declaration.hasModifier(J.Modifier.Type.Final)
        && !declaration.hasModifier(J.Modifier.Type.Public)
        && !declaration.hasModifier(J.Modifier.Type.Protected);
  }

  private static J.ClassDeclaration compilerWithClosure(
      Accumulator accumulator, ExecutionContext context) {
    J.ClassDeclaration compiler =
        promoted(accumulator.compiler, true, accumulator.source);
    List<Statement> members = new ArrayList<>();
    boolean firstSupportMember = true;
    for (String name : SUPPORT_TYPE_NAMES) {
      J.ClassDeclaration support = accumulator.supportTypes.get(name);
      if (COPIED_MEMBER_NAMES.contains(name)) {
        support =
            (J.ClassDeclaration)
                new RandomizeIdVisitor<ExecutionContext>().visitNonNull(support, context);
      }
      support = accessibleSupportType(support);
      Space prefix =
          firstSupportMember
              ? singleLineMemberPrefix(support.getPrefix())
              : memberPrefix(support.getPrefix());
      members.add(support.withPrefix(prefix));
      firstSupportMember = false;
    }
    for (String name : HELPER_NAMES) {
      J.MethodDeclaration helper = accumulator.helpers.get(name);
      if (COPIED_MEMBER_NAMES.contains(name)) {
        helper =
            (J.MethodDeclaration)
                new RandomizeIdVisitor<ExecutionContext>().visitNonNull(helper, context);
      }
      if (name.equals("isBodyDescendant") || name.equals("referenceWidth")) {
        helper = withoutPrivate(helper);
      }
      members.add(helper.withPrefix(memberPrefix(helper.getPrefix())));
    }
    boolean firstCompilerMember = true;
    for (Statement statement : compiler.getBody().getStatements()) {
      if (firstCompilerMember) {
        statement = (Statement) statement.withPrefix(blankLineMemberPrefix(statement.getPrefix()));
        firstCompilerMember = false;
      }
      members.add(statement);
    }
    return compiler.withBody(compiler.getBody().withStatements(members));
  }

  private static J.ClassDeclaration accessibleSupportType(J.ClassDeclaration declaration) {
    if (declaration.getSimpleName().equals("IndexedControl")) {
      return declaration;
    }
    J.ClassDeclaration accessible =
        normalizeFirstClassToken(
            declaration.withModifiers(
                declaration.getModifiers().stream()
                    .filter(modifier -> modifier.getType() != J.Modifier.Type.Private)
                    .toList()));
    if (!declaration.getSimpleName().equals("LabelReference")
        && !declaration.getSimpleName().equals("GlobalState")) {
      return accessible;
    }
    List<Statement> members = new ArrayList<>();
    for (Statement statement : accessible.getBody().getStatements()) {
      if (statement instanceof J.VariableDeclarations fields) {
        statement = withoutPrivate(fields);
      }
      members.add(statement);
    }
    return accessible.withBody(accessible.getBody().withStatements(members));
  }

  private static J.MethodDeclaration withoutPrivate(J.MethodDeclaration method) {
    List<J.Modifier> modifiers =
        method.getModifiers().stream()
            .filter(modifier -> modifier.getType() != J.Modifier.Type.Private)
            .toList();
    if (!modifiers.isEmpty()) {
      modifiers = new ArrayList<>(modifiers);
      modifiers.set(0, modifiers.get(0).withPrefix(Space.EMPTY));
    }
    return method.withModifiers(modifiers);
  }

  private static J.VariableDeclarations withoutPrivate(J.VariableDeclarations fields) {
    List<J.Modifier> modifiers =
        fields.getModifiers().stream()
            .filter(modifier -> modifier.getType() != J.Modifier.Type.Private)
            .toList();
    if (!modifiers.isEmpty()) {
      modifiers = new ArrayList<>(modifiers);
      modifiers.set(0, modifiers.get(0).withPrefix(Space.EMPTY));
      return fields.withModifiers(modifiers);
    }
    TypeTree type = fields.getTypeExpression();
    return type == null
        ? fields.withModifiers(modifiers)
        : fields.withModifiers(modifiers).withTypeExpression(type.withPrefix(Space.EMPTY));
  }

  private static J.ClassDeclaration promoted(
      J.ClassDeclaration declaration, boolean makePublic, J.CompilationUnit source) {
    List<J.Modifier> modifiers = new ArrayList<>();
    for (J.Modifier modifier : declaration.getModifiers()) {
      if (modifier.getType() == J.Modifier.Type.Static) {
        continue;
      }
      if (modifier.getType() == J.Modifier.Type.Private) {
        if (makePublic) {
          modifiers.add(modifier.withType(J.Modifier.Type.Public));
        }
        continue;
      }
      modifiers.add(modifier);
    }
    J.ClassDeclaration normalized =
        normalizeFirstClassToken(declaration.withModifiers(modifiers));
    Markers styleMarkers =
        source.getMarkers().removeByType(NamedStyles.class).addIfAbsent(BANTENG_FORMAT);
    J.CompilationUnit styledSource = source.withMarkers(styleMarkers);
    J.ClassDeclaration shifted =
        ShiftFormat.indent(normalized, new Cursor(null, styledSource), -1);
    return shifted.withPrefix(Space.format("\n\n"));
  }

  private static J.ClassDeclaration normalizeFirstClassToken(J.ClassDeclaration declaration) {
    if (!declaration.getModifiers().isEmpty()) {
      List<J.Modifier> modifiers = new ArrayList<>(declaration.getModifiers());
      modifiers.set(0, modifiers.get(0).withPrefix(Space.EMPTY));
      return declaration.withModifiers(modifiers);
    }
    J.ClassDeclaration.Kind kind = declaration.getPadding().getKind().withPrefix(Space.EMPTY);
    return declaration.getPadding().withKind(kind);
  }

  private static Space memberPrefix(Space prefix) {
    List<Comment> comments = new ArrayList<>();
    for (Comment comment : prefix.getComments()) {
      comments.add(comment.withSuffix(withIndent(comment.getSuffix(), 2)));
    }
    return prefix.withWhitespace(withIndent(prefix.getWhitespace(), 2)).withComments(comments);
  }

  private static Space blankLineMemberPrefix(Space prefix) {
    Space normalized = memberPrefix(prefix);
    String newline = prefix.getWhitespace().contains("\r\n") ? "\r\n" : "\n";
    return normalized.withWhitespace(newline + newline + "  ");
  }

  private static Space singleLineMemberPrefix(Space prefix) {
    Space normalized = memberPrefix(prefix);
    String newline = prefix.getWhitespace().contains("\r\n") ? "\r\n" : "\n";
    return normalized.withWhitespace(newline + "  ");
  }

  private static String withIndent(String whitespace, int spaces) {
    int newline = Math.max(whitespace.lastIndexOf('\n'), whitespace.lastIndexOf('\r'));
    if (newline < 0) {
      return whitespace;
    }
    return whitespace.substring(0, newline + 1) + " ".repeat(spaces);
  }

  private static J.CompilationUnit generatedCompilationUnit(
      Accumulator accumulator,
      J.ClassDeclaration promoted,
      Path targetFileName,
      boolean vectorImports,
      ExecutionContext context) {
    J.CompilationUnit source = accumulator.source;
    Path targetPath =
        source
            .getSourcePath()
            .getParent()
            .resolveSibling("bytecode")
            .resolve(targetFileName);
    J.Package targetPackage =
        new J.Package(
            randomId(),
            source.getPackageDeclaration() == null
                ? Space.EMPTY
                : source.getPackageDeclaration().getPrefix(),
            Markers.EMPTY,
            TypeTree.build("world.mongoose.banteng.bytecode").withPrefix(Space.SINGLE_SPACE),
            emptyList());
    J.CompilationUnit generated =
        source
            .withId(randomId())
            .withSourcePath(targetPath)
            .withPackageDeclaration(targetPackage)
            .withMarkers(
                source
                    .getMarkers()
                    .removeByType(NamedStyles.class)
                    .addIfAbsent(BANTENG_FORMAT))
            .withClasses(List.of(promoted));
    if (vectorImports) {
      generated = addVectorImports(generated, context);
    }
    return generated;
  }

  private static J.CompilationUnit addVectorImports(
      J.CompilationUnit generated, ExecutionContext context) {
    J.CompilationUnit imported =
        (J.CompilationUnit)
            new AddImport<ExecutionContext>(
                    "world.mongoose.banteng.bytecode.LayoutCompiler", "isBodyDescendant", false)
                .visitNonNull(generated, context);
    imported =
        (J.CompilationUnit)
            new AddImport<ExecutionContext>(
                    "world.mongoose.banteng.bytecode.LayoutCompiler", "referenceWidth", false)
                .visitNonNull(imported, context);
    for (String nestedType : VECTOR_SUPPORT_IMPORTS) {
      imported =
          (J.CompilationUnit)
              new AddImport<ExecutionContext>(
                      "world.mongoose.banteng.bytecode.LayoutCompiler", nestedType, null, null, false)
                  .visitNonNull(imported, context);
    }
    return imported;
  }

  static final class Accumulator {
    private J.CompilationUnit source;
    private UUID outerId;
    private J.ClassDeclaration compiler;
    private J.ClassDeclaration builder;
    private final Map<String, J.ClassDeclaration> supportTypes = new LinkedHashMap<>();
    private final Map<String, J.MethodDeclaration> helpers = new LinkedHashMap<>();
    private final Set<UUID> removedMemberIds = new LinkedHashSet<>();
    private boolean compilerCollision;
    private boolean builderCollision;
    private boolean invalid;

    boolean ready() {
      return !invalid
          && source != null
          && outerId != null
          && compiler != null
          && builder != null
          && supportTypes.keySet().containsAll(SUPPORT_TYPE_NAMES)
          && supportTypes.size() == SUPPORT_TYPE_NAMES.size()
          && helpers.keySet().containsAll(HELPER_NAMES)
          && helpers.size() == HELPER_NAMES.size()
          && !compilerCollision
          && !builderCollision;
    }
  }
}
