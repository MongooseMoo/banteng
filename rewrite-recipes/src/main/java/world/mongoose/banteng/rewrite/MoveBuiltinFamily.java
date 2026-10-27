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

/** Moves registration groups and their declared static method closure. */
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
          if (family.equals("TimeBuiltins") && statement instanceof J.VariableDeclarations field
              && field.getVariables().stream().anyMatch(variable -> Set.of(
                  "CLOCK_REALTIME", "CLOCK_MONOTONIC", "CLOCK_MONOTONIC_RAW",
                  "CTIME_MAX_SECONDS").contains(variable.getSimpleName()))) {
            members.fields.add(field);
          }
          if (family.equals("StringBuiltins") && statement instanceof J.ClassDeclaration nested
              && nested.getSimpleName().equals("AnsiReplacement")) {
            members.nested.add(nested);
          }
          if (family.equals("ListBuiltins") && statement instanceof J.VariableDeclarations field
              && field.getVariables().stream().anyMatch(variable -> Set.of(
                  "DEFAULT_MAX_LIST_VALUE_BYTES", "MIN_LIST_VALUE_BYTES_LIMIT",
                  "MAX_LIST_VALUE_BYTES_LIMIT").contains(variable.getSimpleName()))) {
            members.limits.add(field);
          }
          if (statement instanceof J.MethodDeclaration method) {
            if (family.equals("StringBuiltins") && method.getSimpleName().equals("matchesAt")) {
              members.matchesAt = method;
            }
            if (family.equals("ListBuiltins")) {
              if (Set.of("valueBytes", "enforceListValueLimit").contains(method.getSimpleName())) {
                members.limits.add(method);
              } else if (method.getSimpleName().equals("foldAscii")) {
                members.foldAscii = method;
              }
            }
            if (methods.contains(method.getSimpleName()) && method.getModifiers().stream()
                .anyMatch(modifier -> modifier.getType() == J.Modifier.Type.Static)
                || family.equals("StringBuiltins") && method.getSimpleName().equals("parseAnsi")) {
              members.implementations.add(method);
            }
            if (method.getSimpleName().equals("buildManifest") && method.getBody() != null) {
              members.manifest = method.getId();
              boolean previousRegistration = false;
              for (Statement entry : method.getBody().getStatements()) {
                J.NewClass spec = registration(entry);
                if (spec != null) {
                  if (!previousRegistration) { members.groups.add(new ArrayList<>()); }
                  members.groups.getLast().add(entry);
                  members.registrations.add(entry);
                }
                previousRegistration = spec != null;
                if (family.equals("ListBuiltins") && entry instanceof J.VariableDeclarations variables
                    && variables.getVariables().stream().anyMatch(variable ->
                        Set.of("allMembers", "sort").contains(variable.getSimpleName()))) {
                  members.handlers.add(entry);
                }
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
    for (String name : List.of("ANY", "INTEGER", "NUMBER", "STRING")) {
      if (referencedNames.contains(name)) {
        constants.append("  private static final Set<ArgType> ").append(name)
            .append(" = Set.of(ArgType.").append(name).append(");\n");
      }
    }
    StringBuilder body = new StringBuilder();
    for (int group = 0; group < members.groups.size(); group++) {
      body.append("  public static void ").append(registerMethod(group))
          .append("(List<BuiltinSpec> entries" + (stringAnsiGroup(group) ? ", Random random" : "") + ") {\n")
          .append("    BuiltinRegistrar registrar = new BuiltinRegistrar(entries);\n");
      for (Statement handler : members.handlers) {
        String name = ((J.VariableDeclarations) handler).getVariables().getFirst().getSimpleName();
        if (members.groups.get(group).stream().anyMatch(statement ->
            registration(statement).getArguments().get(4) instanceof J.Identifier identifier
                && identifier.getSimpleName().equals(name))) {
          body.append(handler.printTrimmed(new Cursor(null, members.source))).append(";\n");
        }
      }
      for (Statement statement : members.groups.get(group)) {
      List<Expression> arguments = registration(statement).getArguments();
      body.append("    registrar.builtin(").append(arguments.get(0).printTrimmed(new Cursor(null, members.source)))
          .append(", ").append(arguments.get(1).printTrimmed(new Cursor(null, members.source))).append(')');
      String permission = arguments.get(2).printTrimmed(new Cursor(null, members.source));
      if (!permission.equals("BuiltinPermissionRule.ANY")) {
        body.append(".permission(").append(permission).append(')');
      }
      if (!arguments.get(3).printTrimmed(new Cursor(null, members.source)).equals("false")) { body.append(".irrevocable()"); }
      body.append(".implementation(")
           .append(arguments.get(4).printTrimmed(new Cursor(null, members.source)).replace("BuiltinCatalog::", family + "::")
               .replace("parseAnsi(call.arguments())", "parseAnsi(call.arguments(), random)"))
          .append(");\n");
      }
      body.append("  }\n");
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
            + body + "}\n")
        .findFirst().orElseThrow();
    J.ClassDeclaration type = skeleton.getClasses().getFirst();
    List<Statement> statements = new ArrayList<>(type.getBody().getStatements());
    for (J.MethodDeclaration implementation : members.implementations) {
      statements.add(family.equals("ConversionBuiltins")
          && implementation.getSimpleName().equals("toStringValue")
          ? publicMethod(implementation) : implementation);
    }
    statements.addAll(members.fields);
    statements.addAll(members.nested);
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
    if (family.equals("StringBuiltins")) {
      String source = generated.printAll().replace("private BuiltinResult parseAnsi(List<MooValue> arguments)",
          "private static BuiltinResult parseAnsi(List<MooValue> arguments, Random random)");
      generated = ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
          .findFirst().orElseThrow()).withSourcePath(generated.getSourcePath());
      generated = stringSharedImports(generated, context);
    }
    List<SourceFile> generatedFiles = new ArrayList<>();
    if (family.equals("ListBuiltins")) {
      generated = addSharedImports(generated, context);
      J.CompilationUnit limits = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
          .parse(context, "package world.mongoose.banteng.builtin; public final class ValueLimits {"
              + "private ValueLimits() {} }").findFirst().orElseThrow();
      J.ClassDeclaration limitsType = limits.getClasses().getFirst();
      List<Statement> limitsBody = new ArrayList<>(limitsType.getBody().getStatements());
      for (Statement member : members.limits) {
        if (member instanceof J.MethodDeclaration method) { member = publicMethod(method); }
        limitsBody.add(member);
      }
      limits = limits.withImports(members.source.getImports())
          .withClasses(List.of(limitsType.withBody(limitsType.getBody().withStatements(limitsBody))))
          .withSourcePath(Path.of("src/main/java/world/mongoose/banteng/builtin/ValueLimits.java"));
      limits = (J.CompilationUnit) new RandomizeIdVisitor<ExecutionContext>().visitNonNull(limits, context);
      limits = (J.CompilationUnit) new AutoFormatVisitor<ExecutionContext>(null).visitNonNull(limits, context);
      generatedFiles.add(usedImports(limits, context));
    }
    generatedFiles.add(usedImports(generated, context));
    return generatedFiles;
  }

  private String registerMethod(int group) {
    if (family.equals("ConversionBuiltins")) {
      return List.of("registerConversions", "registerTypeOf").get(group);
    }
    if (family.equals("StringBuiltins")) {
      return List.of("registerExplode", "registerSubstitutionAndAnsi", "registerSearchAndCompare").get(group);
    }
    return family.equals("ListBuiltins")
        ? List.of("registerLength", "registerListEdits", "registerSetsAndSort", "registerReverse").get(group)
        : "register";
  }

  private boolean stringAnsiGroup(int group) {
    return family.equals("StringBuiltins") && group == 1;
  }

  private static J.CompilationUnit stringSharedImports(J.CompilationUnit unit, ExecutionContext context) {
    return (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
      @Override public J.CompilationUnit visitCompilationUnit(J.CompilationUnit source, ExecutionContext unused) {
        maybeAddImport("world.mongoose.banteng.value.ValueSemantics", "matchesAt", false);
        maybeAddImport("world.mongoose.banteng.value.ValueSemantics", "foldAscii", false);
        return super.visitCompilationUnit(source, unused);
      }
    }.visitNonNull(unit, context);
  }

  private static J.MethodDeclaration publicMethod(J.MethodDeclaration method) {
    return method.withModifiers(method.getModifiers().stream().map(modifier ->
        modifier.getType() == J.Modifier.Type.Private
            ? modifier.withType(J.Modifier.Type.Public) : modifier).toList());
  }

  private static J.CompilationUnit addSharedImports(J.CompilationUnit unit, ExecutionContext context) {
    return (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
      @Override public J.CompilationUnit visitCompilationUnit(J.CompilationUnit source,
          ExecutionContext unused) {
        maybeAddImport("world.mongoose.banteng.builtin.ValueLimits", "valueBytes", false);
        maybeAddImport("world.mongoose.banteng.builtin.ValueLimits", "enforceListValueLimit", false);
        maybeAddImport("world.mongoose.banteng.value.ValueSemantics", "foldAscii", false);
        return super.visitCompilationUnit(source, unused);
      }
    }.visitNonNull(unit, context);
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
        if (family.equals("StringBuiltins") && unit.getSourcePath().endsWith("value/ValueSemantics.java")) {
          J.ClassDeclaration type = unit.getClasses().getFirst();
          List<Statement> body = new ArrayList<>(type.getBody().getStatements());
          body.add(publicMethod(members.matchesAt));
          return unit.withClasses(List.of(type.withBody(type.getBody().withStatements(body))));
        }
        if (family.equals("ListBuiltins")) {
          if (unit.getSourcePath().endsWith("value/ValueSemantics.java")) {
            J.ClassDeclaration type = unit.getClasses().getFirst();
            List<Statement> body = new ArrayList<>(type.getBody().getStatements());
            body.add(publicMethod(members.foldAscii));
            String source = unit.withClasses(List.of(type.withBody(type.getBody().withStatements(body))))
                .printAll()
                .replace("if (lhs >= 'A' && lhs <= 'Z') lhs += 'a' - 'A';", "lhs = foldAscii(lhs);")
                .replace("if (rhs >= 'A' && rhs <= 'Z') rhs += 'a' - 'A';", "rhs = foldAscii(rhs);");
            return ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
                .findFirst().orElseThrow()).withId(unit.getId()).withSourcePath(unit.getSourcePath());
          }
          if (unit.getSourcePath().endsWith("vm/ListOps.java")) {
            maybeRemoveImport(SOURCE);
            maybeAddImport("world.mongoose.banteng.builtin.ValueLimits", false);
            return super.visitCompilationUnit(unit, context);
          }
        }
        if (!unit.getId().equals(members.source.getId())) { return unit; }
        maybeAddImport("world.mongoose.banteng.builtin.family." + family, false);
        J.CompilationUnit changed = super.visitCompilationUnit(unit, context);
        if (family.equals("ListBuiltins")) { changed = addSharedImports(changed, context); }
        if (family.equals("StringBuiltins")) { changed = stringSharedImports(changed, context); }
        return usedImports(changed, context);
      }
      @Override public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation,
          ExecutionContext context) {
        J.MethodInvocation changed = super.visitMethodInvocation(invocation, context);
        if (family.equals("ConversionBuiltins") && changed.getSimpleName().equals("toStringValue")) {
          return changed.withSelect(new J.Identifier(java.util.UUID.randomUUID(),
              org.openrewrite.java.tree.Space.EMPTY, org.openrewrite.marker.Markers.EMPTY,
              List.of(), family, null, null));
        }
        if (family.equals("ListBuiltins") && changed.getSimpleName().equals("enforceListValueLimit")
            && changed.getSelect() instanceof J.Identifier identifier
            && identifier.getSimpleName().equals("BuiltinCatalog")) {
          return changed.withSelect(identifier.withSimpleName("ValueLimits").withType(null));
        }
        return changed;
      }
      @Override public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration type,
          ExecutionContext context) {
        if (!type.getId().equals(members.owner)) {
          return super.visitClassDeclaration(type, context);
        }
        return super.visitClassDeclaration(type.withBody(type.getBody().withStatements(
            type.getBody().getStatements().stream().filter(statement -> members.implementations
                .stream().noneMatch(method -> method.getId().equals(statement.getId())))
                .filter(statement -> members.limits.stream().noneMatch(member -> member.getId().equals(statement.getId())))
                .filter(statement -> members.fields.stream().noneMatch(member -> member.getId().equals(statement.getId())))
                 .filter(statement -> members.foldAscii == null || !members.foldAscii.getId().equals(statement.getId()))
                 .filter(statement -> members.matchesAt == null || !members.matchesAt.getId().equals(statement.getId()))
                 .filter(statement -> members.nested.stream().noneMatch(member -> member.getId().equals(statement.getId())))
                .toList())), context);
      }
      @Override public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
          ExecutionContext context) {
        if (!method.getId().equals(members.manifest) || method.getBody() == null) {
          return super.visitMethodDeclaration(method, context);
        }
        List<Statement> retained = new ArrayList<>();
        for (Statement statement : method.getBody().getStatements()) {
          int group = -1;
          for (int index = 0; index < members.groups.size(); index++) {
            if (statement.getId().equals(members.groups.get(index).getFirst().getId())) { group = index; }
          }
          if (group >= 0) {
            Statement replacement = ((J.CompilationUnit) JavaParser.fromJavaVersion().build()
             .parse(context, "class Replacement { void register() { " + family + "." + registerMethod(group)
                 + "(entries" + (stringAnsiGroup(group) ? ", random" : "") + "); } }")
            .findFirst().orElseThrow()).getClasses().getFirst().getBody().getStatements().stream()
            .filter(J.MethodDeclaration.class::isInstance).map(J.MethodDeclaration.class::cast)
            .findFirst().orElseThrow().getBody().getStatements().getFirst();
            retained.add(replacement.withPrefix(statement.getPrefix()));
          } else if (members.registrations.stream().noneMatch(entry -> entry.getId().equals(statement.getId()))
              && members.handlers.stream().noneMatch(entry -> entry.getId().equals(statement.getId()))) {
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
    private final List<List<Statement>> groups = new ArrayList<>();
    private final List<Statement> handlers = new ArrayList<>();
    private final List<Statement> limits = new ArrayList<>();
    private final List<Statement> fields = new ArrayList<>();
    private J.MethodDeclaration foldAscii;
    private J.MethodDeclaration matchesAt;
    private final List<Statement> nested = new ArrayList<>();
    private boolean ready() {
      return !collision && source != null && registrations.size() == builtins.size()
          && implementations.size() == methods.size();
    }
  }
}
