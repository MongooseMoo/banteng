package world.mongoose.banteng.rewrite;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.RandomizeIdVisitor;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

/** Moves one contiguous registration block and its declared static method closure. */
public final class MoveBuiltinFamily extends ScanningRecipe<MoveBuiltinFamily.Members> {
  private static final String SOURCE = "world.mongoose.banteng.builtin.BuiltinCatalog";
  private final String family;
  private final Set<String> builtins;
  private final Set<String> methods;

  public MoveBuiltinFamily() {
    this("MathBuiltins",
        Set.of("min", "max", "abs", "acos", "acosh", "asin", "asinh", "atan", "atan2",
            "atanh", "cbrt", "ceil", "cos", "cosh", "distance", "exp", "floatstr", "floor",
            "log", "log10", "relative_heading", "round", "sin", "sinh", "sqrt", "tan",
            "tanh", "trunc"),
        Set.of("minimum", "maximum", "absoluteValue", "unaryFloatBuiltin", "acos", "acosh",
            "asin", "asinh", "atan", "atan2", "atanh", "cosine", "log", "log10", "round",
            "sine", "sqrt", "tangent", "trunc", "numericDouble", "distance", "floatstr",
            "relativeHeading"));
  }

  @JsonCreator
  public MoveBuiltinFamily(@JsonProperty("family") String family,
      @JsonProperty("builtins") Set<String> builtins,
      @JsonProperty("methods") Set<String> methods) {
    this.family = family;
    this.builtins = Set.copyOf(builtins);
    this.methods = Set.copyOf(methods);
  }

  @Override public MoveBuiltinFamily clone() {
    // Configuration is immutable; the superclass gives the clone its own scan accumulator key.
    return (MoveBuiltinFamily) super.clone();
  }

  @Override public String getDisplayName() { return "Move builtin family"; }
  @Override public String getDescription() {
    return "Moves a builtin registration block and its static implementations to a family owner.";
  }
  @Override public Members getInitialValue(ExecutionContext context) { return new Members(); }

  @Override public TreeVisitor<?, ExecutionContext> getScanner(Members members) {
    return new JavaIsoVisitor<ExecutionContext>() {
      @Override public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration type,
          ExecutionContext context) {
        if (type.getType() != null && type.getType().getFullyQualifiedName()
            .equals("world.mongoose.banteng.builtin.family." + family)) {
          members.collision = true;
        }
        if (type.getType() == null || !SOURCE.equals(type.getType().getFullyQualifiedName())) {
          return type;
        }
        members.source = getCursor().firstEnclosingOrThrow(J.CompilationUnit.class);
        members.owner = type.getId();
        for (Statement statement : type.getBody().getStatements()) {
          if (statement instanceof J.MethodDeclaration method) {
            if (methods.contains(method.getSimpleName()) && method.getModifiers().stream()
                .anyMatch(modifier -> modifier.getType() == J.Modifier.Type.Static)) {
              members.implementations.add(method);
            }
            if (method.getSimpleName().equals("buildManifest") && method.getBody() != null) {
              members.manifest = method.getId();
              for (Statement entry : method.getBody().getStatements()) {
                J.NewClass spec = registration(entry);
                if (spec != null) { members.registrations.add(entry); }
              }
            }
          }
        }
        return type;
      }
    };
  }

  private J.NewClass registration(Statement statement) {
    if (statement instanceof J.MethodInvocation invocation
        && invocation.getSimpleName().equals("add") && invocation.getArguments().size() == 1
        && invocation.getArguments().getFirst() instanceof J.NewClass spec
        && spec.getArguments().size() == 5
        && spec.getArguments().getFirst() instanceof J.Literal name
        && builtins.contains(name.getValue())) {
      return spec;
    }
    return null;
  }

  @Override public Collection<SourceFile> generate(Members members, ExecutionContext context) {
    if (!members.ready()) { return List.of(); }
    Set<String> referencedNames = new HashSet<>();
    JavaIsoVisitor<ExecutionContext> references = new JavaIsoVisitor<>() {
      @Override public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext unused) {
        if (!(getCursor().getParentTreeCursor().getValue() instanceof J.FieldAccess access
            && access.getName().getId().equals(identifier.getId()))) {
          referencedNames.add(identifier.getSimpleName());
        }
        return identifier;
      }
    };
    for (Statement registration : members.registrations) { references.visitNonNull(registration, context); }
    for (J.MethodDeclaration implementation : members.implementations) { references.visitNonNull(implementation, context); }
    StringBuilder constants = new StringBuilder();
    for (String name : List.of("ANY", "INTEGER", "NUMBER")) {
      if (referencedNames.contains(name)) {
        constants.append("  private static final Set<ArgType> ").append(name)
            .append(" = Set.of(ArgType.").append(name).append(");\n");
      }
    }
    StringBuilder body = new StringBuilder();
    for (Statement statement : members.registrations) {
      List<Expression> arguments = registration(statement).getArguments();
      body.append("    registrar.builtin(").append(arguments.get(0).printTrimmed(new Cursor(null, members.source)))
          .append(", ").append(arguments.get(1).printTrimmed(new Cursor(null, members.source))).append(')');
      String permission = arguments.get(2).printTrimmed(new Cursor(null, members.source));
      if (!permission.equals("BuiltinPermissionRule.ANY")) {
        body.append(".permission(").append(permission).append(')');
      }
      if (!arguments.get(3).printTrimmed(new Cursor(null, members.source)).equals("false")) { body.append(".irrevocable()"); }
      body.append(".implementation(")
          .append(arguments.get(4).printTrimmed(new Cursor(null, members.source)).replace("BuiltinCatalog::", family + "::"))
          .append(");\n");
    }
    J.CompilationUnit skeleton = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
        .parse(context, "package world.mongoose.banteng.builtin.family;\n"
            + "import java.util.List; import java.util.Set;\n"
            + "import world.mongoose.banteng.builtin.ArgType;\n"
            + "import world.mongoose.banteng.builtin.BuiltinRegistrar;\n"
            + "import world.mongoose.banteng.builtin.BuiltinSpec;\n"
            + "import world.mongoose.banteng.builtin.BuiltinHandler;\n"
            + "import world.mongoose.banteng.builtin.BuiltinPermissionRule;\n"
            + "import world.mongoose.banteng.builtin.BuiltinResult;\n"
            + "import world.mongoose.banteng.builtin.CallShape;\n"
            + "/** Owns builtin registrations and implementations for this family. */\n"
            + "public final class " + family + " {\n"
            + constants
            + "  private " + family + "() {}\n"
            + "  public static void register(List<BuiltinSpec> entries) {\n"
            + "    BuiltinRegistrar registrar = new BuiltinRegistrar(entries);\n" + body + "  }\n}")
        .findFirst().orElseThrow();
    J.ClassDeclaration type = skeleton.getClasses().getFirst();
    List<Statement> statements = new ArrayList<>(type.getBody().getStatements());
    statements.addAll(members.implementations);
    List<J.Import> imports = new ArrayList<>(members.source.getImports());
    for (J.Import imported : skeleton.getImports()) {
      if (imports.stream().noneMatch(existing -> existing.getTypeName().equals(imported.getTypeName()))) {
        imports.add(imported);
      }
    }
    J.CompilationUnit generated = skeleton.withImports(imports)
        .withClasses(List.of(type.withBody(type.getBody().withStatements(statements))))
        .withSourcePath(Path.of("src/main/java/world/mongoose/banteng/builtin/family/" + family + ".java"));
    generated = (J.CompilationUnit) new RandomizeIdVisitor<ExecutionContext>().visitNonNull(generated, context);
    generated = (J.CompilationUnit) new AutoFormatVisitor<ExecutionContext>(null).visitNonNull(generated, context);
    return List.of(usedImports(generated, context));
  }

  private static J.CompilationUnit usedImports(J.CompilationUnit unit, ExecutionContext context) {
    Set<String> names = new HashSet<>();
    JavaIsoVisitor<ExecutionContext> collector = new JavaIsoVisitor<>() {
      @Override public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext unused) {
        names.add(identifier.getSimpleName());
        return identifier;
      }
    };
    for (J.ClassDeclaration type : unit.getClasses()) { collector.visitNonNull(type, context); }
    return unit.withImports(unit.getImports().stream()
        .filter(imported -> imported.getQualid().getSimpleName().equals("*")
            || names.contains(imported.getQualid().getSimpleName()))
        .sorted(Comparator.comparing(J.Import::getTypeName)).toList());
  }

  @Override public TreeVisitor<?, ExecutionContext> getVisitor(Members members) {
    if (!members.ready()) { return TreeVisitor.noop(); }
    return new JavaIsoVisitor<ExecutionContext>() {
      @Override public J.CompilationUnit visitCompilationUnit(J.CompilationUnit unit,
          ExecutionContext context) {
        if (!unit.getId().equals(members.source.getId())) { return unit; }
        maybeAddImport("world.mongoose.banteng.builtin.family." + family, false);
        return usedImports(super.visitCompilationUnit(unit, context), context);
      }
      @Override public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration type,
          ExecutionContext context) {
        if (!type.getId().equals(members.owner)) { return type; }
        return super.visitClassDeclaration(type.withBody(type.getBody().withStatements(
            type.getBody().getStatements().stream().filter(statement -> members.implementations
                .stream().noneMatch(method -> method.getId().equals(statement.getId()))).toList())), context);
      }
      @Override public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
          ExecutionContext context) {
        if (!method.getId().equals(members.manifest) || method.getBody() == null) { return method; }
        Statement replacement = ((J.CompilationUnit) JavaParser.fromJavaVersion().build()
            .parse(context, "class Replacement { void register() { " + family + ".register(entries); } }")
            .findFirst().orElseThrow()).getClasses().getFirst().getBody().getStatements().stream()
            .filter(J.MethodDeclaration.class::isInstance).map(J.MethodDeclaration.class::cast)
            .findFirst().orElseThrow().getBody().getStatements().getFirst();
        List<Statement> retained = new ArrayList<>();
        for (Statement statement : method.getBody().getStatements()) {
          if (statement.getId().equals(members.registrations.getFirst().getId())) {
            retained.add(replacement.withPrefix(statement.getPrefix()));
          } else if (members.registrations.stream().noneMatch(entry -> entry.getId().equals(statement.getId()))) {
            retained.add(statement);
          }
        }
        return method.withBody(method.getBody().withStatements(retained));
      }
    };
  }

  final class Members {
    private J.CompilationUnit source;
    private UUID owner;
    private UUID manifest;
    private boolean collision;
    private final List<J.MethodDeclaration> implementations = new ArrayList<>();
    private final List<Statement> registrations = new ArrayList<>();
    private boolean ready() {
      return !collision && source != null && registrations.size() == builtins.size()
          && implementations.size() == methods.size();
    }
  }
}
