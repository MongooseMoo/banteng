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
          if (family.equals("RegexBuiltins") && statement instanceof J.VariableDeclarations field
              && field.getVariables().stream().anyMatch(variable ->
                  Set.of("pcre", "toastRegex").contains(variable.getSimpleName()))) {
            members.regexFields.add(field);
          }
          if (family.equals("ServerBuiltins") && statement instanceof J.VariableDeclarations field
              && field.getVariables().stream().anyMatch(variable -> variable.getSimpleName().equals("SERVER_VERSION"))) {
            members.fields.add(field);
          }
          if (family.equals("ConnectionBuiltins")) {
            if (statement instanceof J.VariableDeclarations field && field.getVariables().stream()
                .anyMatch(variable -> variable.getSimpleName().equals("DEFAULT_MAX_QUEUED_OUTPUT"))) {
              members.fields.add(field);
            }
            if (statement instanceof J.ClassDeclaration nested) {
              if (nested.getSimpleName().equals("EmptyConnectionRegistry")) { members.emptyConnections = nested; }
              else if (Set.of("ListenerControl", "ListenerDescription", "ConnectionOptionRequest",
                  "ForcedInputRequest", "NotificationRequest", "ConnectionOption", "KeepAliveOptions")
                  .contains(nested.getSimpleName())) { members.nested.add(nested); }
            }
          }
          if (family.equals("TimeBuiltins") && statement instanceof J.VariableDeclarations field
              && field.getVariables().stream().anyMatch(variable -> Set.of(
                  "CLOCK_REALTIME", "CLOCK_MONOTONIC", "CLOCK_MONOTONIC_RAW",
                  "CTIME_MAX_SECONDS").contains(variable.getSimpleName()))) {
            members.fields.add(field);
          }
          if (statement instanceof J.ClassDeclaration nested
              && (family.equals("StringBuiltins") && nested.getSimpleName().equals("AnsiReplacement")
                  || family.equals("CryptoBuiltins") && Set.of("Argon2Settings", "Argon2Hasher").contains(nested.getSimpleName())
                  || family.equals("ObjectBuiltins") && nested.getSimpleName().equals("ParentArgument"))) {
            members.nested.add(nested);
          }
          if (family.equals("ListBuiltins") && statement instanceof J.VariableDeclarations field
              && field.getVariables().stream().anyMatch(variable -> Set.of(
                  "DEFAULT_MAX_LIST_VALUE_BYTES", "MIN_LIST_VALUE_BYTES_LIMIT",
                  "MAX_LIST_VALUE_BYTES_LIMIT").contains(variable.getSimpleName()))) {
            members.limits.add(field);
          }
          if (statement instanceof J.MethodDeclaration method) {
            if (family.equals("RandomBuiltins") && method.getSimpleName().equals("stringLengthError")) {
              members.stringLengthError = method;
            }
            if (family.equals("BinaryBuiltins") && method.getSimpleName().equals("binaryBytes")) {
              members.binaryBytes = method;
            }
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
            if (family.equals("PropertyBuiltins") && method.getSimpleName().equals("resolveOwnershipPreamble")) {
              members.ownershipPreamble = method;
            }
            if (family.equals("ObjectBuiltins") && method.getSimpleName().equals("mutationResult")) {
              members.mutationResult = method;
            }
            if (family.equals("ConnectionBuiltins") && method.getSimpleName().equals("standaloneConnections")) {
              members.standaloneConnections = method;
            }
            if (methods.contains(method.getSimpleName()) && method.getModifiers().stream()
                .anyMatch(modifier -> modifier.getType() == J.Modifier.Type.Static)
                || family.equals("StringBuiltins") && method.getSimpleName().equals("parseAnsi")
                || Set.of("RandomBuiltins", "TaskBuiltins", "ConnectionBuiltins").contains(family) && methods.contains(method.getSimpleName())
                || family.equals("CryptoBuiltins") && Set.of("crypt", "nativeCrypt").contains(method.getSimpleName())
                || family.equals("RegexBuiltins") && method.getSimpleName().equals("toastMatch")
                || family.equals("VerbBuiltins") && method.getSimpleName().equals("setVerbCode")
                || family.equals("ServerBuiltins") && Set.of("functionInfo", "serverLog", "logCacheStats").contains(method.getSimpleName())) {
              members.implementations.add(method);
            }
            if (method.getSimpleName().equals("buildManifest") && method.getBody() != null) {
              members.manifest = method.getId();
              boolean previousRegistration = false;
              for (Statement entry : method.getBody().getStatements()) {
                J.NewClass spec = registration(entry);
                boolean cryptoLoop = cryptoLoop(entry);
                if (spec != null || cryptoLoop) {
                  if (!previousRegistration) { members.groups.add(new ArrayList<>()); }
                  members.groups.getLast().add(entry);
                  members.registrations.add(entry);
                }
                previousRegistration = spec != null || cryptoLoop;
                if ((family.equals("ListBuiltins") || family.equals("TaskBuiltins")) && entry instanceof J.VariableDeclarations variables
                    && variables.getVariables().stream().anyMatch(variable ->
                        Set.of("allMembers", "sort", "setThreadMode", "callFunction").contains(variable.getSimpleName()))) {
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

  private boolean cryptoLoop(Statement statement) {
    return family.equals("CryptoBuiltins") && statement instanceof J.ForEachLoop loop
        && loop.getControl().getVariable() instanceof J.VariableDeclarations variable
        && variable.getVariables().getFirst().getSimpleName().equals("input")
        && loop.getBody() instanceof J.Block block && block.getStatements().size() == 2
        && loopRegistration(block.getStatements().getFirst(), "_hash")
        && loopRegistration(block.getStatements().getLast(), "_hmac");
  }

  private static boolean loopRegistration(Statement statement, String suffix) {
    return statement instanceof J.MethodInvocation add && add.getSimpleName().equals("add")
        && add.getArguments().size() == 1 && add.getArguments().getFirst() instanceof J.NewClass spec
        && spec.getArguments().size() == 5 && spec.getArguments().getFirst() instanceof J.Binary name
        && name.getLeft() instanceof J.Identifier input && input.getSimpleName().equals("input")
        && name.getRight() instanceof J.Literal literal && suffix.equals(literal.getValue());
  }

  @Override public Collection<SourceFile> generate(Members members, ExecutionContext context) {
    if (!members.ready()) { return List.of(); }
    Set<String> referencedNames = new HashSet<>();
    JavaIsoVisitor<ExecutionContext> references = new JavaIsoVisitor<>() {
      @Override public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext unused) {
        if (getCursor().getParentTreeCursor().getValue() instanceof J.Case clause
            && clause.getCaseLabels().stream().anyMatch(label -> label.getId().equals(identifier.getId()))) {
          return identifier;
        }
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
    for (String name : List.of("ANY", "INTEGER", "NUMBER", "STRING", "OBJECT")) {
      if (referencedNames.contains(name)) {
        constants.append("  private static final Set<ArgType> ").append(name)
            .append(" = Set.of(ArgType.").append(name).append(");\n");
      }
    }
    StringBuilder body = new StringBuilder();
    for (int group = 0; group < members.groups.size(); group++) {
      body.append("  public static void ").append(registerMethod(group))
          .append("(List<BuiltinSpec> entries" + serverParameters(group, true) + (stringAnsiGroup(group) ? ", Random random" : verbCodeGroup(group) ? ", BuiltinCatalog catalog" : "")
              + (family.equals("CryptoBuiltins") ? ", SecureRandom secureRandom, Crypt crypt" : "")
              + (family.equals("TaskBuiltins") ? ", BuiltinCatalog catalog" : "")
              + (family.equals("ConnectionBuiltins") ? ", Optional<ListenerControl> listenerControl" : "")
              + (family.equals("RandomBuiltins")
                  ? ", java.util.function.Supplier<Random> random, java.util.function.Supplier<Random> floatingRandom, SecureRandom secureRandom" : "") + ") {\n")
          .append("    BuiltinRegistrar registrar = new BuiltinRegistrar(entries);\n");
      if (family.equals("RegexBuiltins")) {
        body.append(group == 0 ? "    ToastRegexService toastRegex = new ToastRegexService();\n"
            : "    PcreService pcre = new PcreService();\n");
      }
      for (Statement handler : family.equals("TaskBuiltins") ? List.<Statement>of() : members.handlers) {
        String name = ((J.VariableDeclarations) handler).getVariables().getFirst().getSimpleName();
        if (members.groups.get(group).stream().anyMatch(statement ->
            registration(statement).getArguments().get(4) instanceof J.Identifier identifier
                && identifier.getSimpleName().equals(name))) {
          body.append(handler.printTrimmed(new Cursor(null, members.source))).append(";\n");
        }
      }
      for (Statement statement : members.groups.get(group)) {
      if (cryptoLoop(statement)) {
        J.ForEachLoop loop = (J.ForEachLoop) statement;
        body.append("    for (")
            .append(loop.getControl().getVariable().printTrimmed(new Cursor(null, members.source)))
            .append(" : ").append(loop.getControl().getIterable().printTrimmed(new Cursor(null, members.source)))
            .append(") {\n");
        for (Statement entry : ((J.Block) loop.getBody()).getStatements()) {
          J.NewClass spec = (J.NewClass) ((J.MethodInvocation) entry).getArguments().getFirst();
          List<Expression> args = spec.getArguments();
          body.append("      registrar.builtin(").append(args.get(0).printTrimmed(new Cursor(null, members.source)))
              .append(", ").append(args.get(1).printTrimmed(new Cursor(null, members.source)))
              .append(").implementation(").append(args.get(4).printTrimmed(new Cursor(null, members.source)))
              .append(");\n");
        }
        body.append("    }\n");
        continue;
      }
      List<Expression> arguments = registration(statement).getArguments();
      body.append("    registrar.builtin(").append(arguments.get(0).printTrimmed(new Cursor(null, members.source)))
          .append(", ").append(arguments.get(1).printTrimmed(new Cursor(null, members.source))).append(')');
      String permission = arguments.get(2).printTrimmed(new Cursor(null, members.source));
      if (!permission.equals("BuiltinPermissionRule.ANY")) {
        body.append(".permission(").append(permission).append(')');
      }
      if (!arguments.get(3).printTrimmed(new Cursor(null, members.source)).equals("false")) { body.append(".irrevocable()"); }
      body.append(".implementation(")
           .append(registrationImplementation(arguments.get(4).printTrimmed(new Cursor(null, members.source)))
               .replace("toastMatch(call.arguments(),", "toastMatch(toastRegex, call.arguments(),")
               .replace("setVerbCode(call.arguments(), call.world(), call.programmer())",
                   family.equals("VerbBuiltins") ? "setVerbCode(catalog, call.arguments(), call.world(), call.programmer())"
                       : "setVerbCode(call.arguments(), call.world(), call.programmer())"))
          .append(");\n");
      }
      body.append("  }\n");
    }
    J.CompilationUnit skeleton = (J.CompilationUnit) JavaParser.fromJavaVersion().build()
        .parse(context, "package world.mongoose.banteng.builtin.family;\n"
            + "import java.util.List; import java.util.Set;\n"
            + "import world.mongoose.banteng.builtin.ArgType;\n"
            + "import world.mongoose.banteng.builtin.BuiltinCatalog;\n"
            + "import world.mongoose.banteng.builtin.BuiltinCall;\n"
            + "import world.mongoose.banteng.builtin.BuiltinHosts;\n"
            + "import world.mongoose.banteng.builtin.ConnectionRegistryAccess;\n"
            + "import world.mongoose.banteng.builtin.BuiltinRegistrar;\n"
            + "import world.mongoose.banteng.builtin.BuiltinSpec;\n"
            + "import world.mongoose.banteng.builtin.BuiltinHandler;\n"
            + "import world.mongoose.banteng.builtin.BuiltinPermissionRule;\n"
            + "import world.mongoose.banteng.builtin.BuiltinResult;\n"
            + "import world.mongoose.banteng.builtin.CallShape;\n"
            + (family.equals("CryptoBuiltins") ? "import world.mongoose.banteng.host.Crypt;\n" : "")
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
      if (imports.stream().noneMatch(existing -> existing.getTypeName().equals(imported.getTypeName())
          && existing.getQualid().getSimpleName().equals(imported.getQualid().getSimpleName())
          && existing.isStatic() == imported.isStatic())) {
        imports.add(imported);
      }
    }
    J.CompilationUnit generated = skeleton.withImports(imports)
        .withClasses(List.of(type.withBody(type.getBody().withStatements(statements))))
        .withSourcePath(Path.of("src/main/java/world/mongoose/banteng/builtin/family/" + family + ".java"));
    generated = (J.CompilationUnit) new RandomizeIdVisitor<ExecutionContext>().visitNonNull(generated, context);
    generated = (J.CompilationUnit) new AutoFormatVisitor<ExecutionContext>(null).visitNonNull(generated, context);
    if (family.equals("CryptoBuiltins")) {
      String source = generated.printAll()
          .replace("crypt(call.arguments(), call.world(), call.programmer())", "crypt(call.arguments(), call.world(), call.programmer(), secureRandom, crypt)")
          .replace("private BuiltinResult crypt(List<MooValue> arguments, WorldTxn world, long programmer)", "private static BuiltinResult crypt(List<MooValue> arguments, WorldTxn world, long programmer, SecureRandom secureRandom, Crypt crypt)")
          .replace("SECURE_RANDOM.nextInt", "secureRandom.nextInt")
          .replace("nativeCrypt(password, StringValue.of(salt).bytes())", "nativeCrypt(password, StringValue.of(salt).bytes(), crypt)")
          .replace("private String nativeCrypt(byte[] password, byte[] salt)", "private static String nativeCrypt(byte[] password, byte[] salt, Crypt crypt)")
          .replace("hosts.crypt().hash(password, salt)", "crypt.hash(password, salt)");
      generated = reparse(generated, source, context);
    }
    if (family.equals("BinaryBuiltins")) {
      String source = generated.printAll();
      int start = source.indexOf("ByteArrayOutputStream encoded =", source.indexOf("private static BuiltinResult encodeBinary"));
      int end = source.indexOf("return BuiltinResult.value(StringValue.of(encoded.toByteArray()));", start)
          + "return BuiltinResult.value(StringValue.of(encoded.toByteArray()));".length();
      source = source.substring(0, start)
          + "return BuiltinResult.value(StringValue.of(raw.toByteArray()).encodeBinary());"
          + source.substring(end);
      source = binaryCalls(source);
      generated = ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
          .findFirst().orElseThrow()).withSourcePath(generated.getSourcePath());
    }
    if (family.equals("VerbBuiltins")) {
      String source = generated.printAll()
          .replace("private BuiltinResult setVerbCode(", "private static BuiltinResult setVerbCode(")
          .replaceAll("(private static BuiltinResult setVerbCode\\(\\s*)List", "$1BuiltinCatalog catalog, List")
          .replace("name -> spec(name).isPresent()", "name -> catalog.spec(name).isPresent()");
      generated = ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
          .findFirst().orElseThrow()).withSourcePath(generated.getSourcePath());
    }
    if (family.equals("ObjectBuiltins")) {
      generated = objectSharedImports(generated, context);
    }
    if (family.equals("PropertyBuiltins")) {
      generated = propertySharedImports(generated, context);
    }
    if (family.equals("ServerBuiltins")) {
      String source = generated.printAll()
          .replace("private BuiltinResult functionInfo(", "private static BuiltinResult functionInfo(")
          .replaceAll("(private static BuiltinResult functionInfo\\(\\s*List<MooValue> arguments)\\)", "$1, BuiltinCatalog catalog)")
          .replace("manifest.stream()", "catalog.manifest().stream()")
          .replace("BuiltinCatalog::functionInfoDescription", "ServerBuiltins::functionInfoDescription")
          .replace("specs.get(requestedName.text().toLowerCase(Locale.ROOT))", "catalog.spec(requestedName.text()).orElse(null)")
          .replace("private BuiltinResult serverLog(List<MooValue> arguments)",
              "private static BuiltinResult serverLog(List<MooValue> arguments, ServerLog log)")
          .replace("private BuiltinResult logCacheStats(WorldTxn world)",
              "private static BuiltinResult logCacheStats(WorldTxn world, ServerLog log)")
          .replace("hosts.serverLog().", "log.");
      generated = ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
          .findFirst().orElseThrow()).withSourcePath(generated.getSourcePath());
    }
    if (family.equals("ConnectionBuiltins")) {
      String source = generated.printAll().replace("private BuiltinResult ", "private static BuiltinResult ");
      for (String name : List.of("bufferedOutputLength", "listen", "listeners", "unlisten", "openNetworkConnection")) {
        source = source.replace("private static BuiltinResult " + name + "(",
            "private static BuiltinResult " + name + "(Optional<ListenerControl> listenerControl, ");
      }
      generated = ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
          .findFirst().orElseThrow()).withSourcePath(generated.getSourcePath());
    }
    if (family.equals("StringBuiltins")) {
      String source = generated.printAll().replace("private BuiltinResult parseAnsi(List<MooValue> arguments)",
          "private static BuiltinResult parseAnsi(List<MooValue> arguments, Random random)");
      generated = ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
          .findFirst().orElseThrow()).withSourcePath(generated.getSourcePath());
      generated = stringSharedImports(generated, context);
    }
    if (family.equals("TaskBuiltins")) {
      String source = generated.printAll()
          .replace("private BuiltinResult configureThreadPool(", "private static BuiltinResult configureThreadPool(")
          .replace("private BuiltinResult callFunction(BuiltinCall call)", "private static BuiltinResult callFunction(BuiltinCall call, BuiltinCatalog catalog)")
          .replace("BuiltinSpec target = specs.get(name);", "BuiltinSpec target = catalog.spec(name).orElse(null);")
          .replace(" invoke(target,", " catalog.invoke(target,")
          .replace("private BuiltinResult dynamicEval(List<MooValue> arguments)", "private static BuiltinResult dynamicEval(List<MooValue> arguments, BuiltinCatalog catalog)")
          .replace("name -> spec(name).isPresent()", "name -> catalog.spec(name).isPresent()");
      generated = ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
          .findFirst().orElseThrow()).withSourcePath(generated.getSourcePath());
    }
    if (family.equals("RandomBuiltins")) {
      String source = generated.printAll()
          .replace("private BuiltinResult randomInteger(List<MooValue> arguments)",
              "private static BuiltinResult randomInteger(List<MooValue> arguments, Random random)")
          .replace("private BuiltinResult floatingRandom(List<MooValue> arguments)",
              "private static BuiltinResult floatingRandom(List<MooValue> arguments, Random floatingRandom)")
          .replace("randomBytes(List<MooValue> arguments, WorldTxn world)",
              "randomBytes(List<MooValue> arguments, WorldTxn world, SecureRandom secureRandom)")
          .replace("SECURE_RANDOM.nextBytes", "secureRandom.nextBytes")
          .replace("stringLengthError(encoded.size(), world)", "ValueLimits.stringLengthError(encoded.size(), world)")
          .replace("import java.util.List;", "import java.util.List;\nimport world.mongoose.banteng.builtin.ValueLimits;");
      generated = ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
          .findFirst().orElseThrow()).withSourcePath(generated.getSourcePath());
    }
    if (family.equals("RegexBuiltins")) {
      String source = generated.printAll().replace(
          "private BuiltinResult toastMatch(List<MooValue> arguments, boolean reverse)",
          "private static BuiltinResult toastMatch(ToastRegexService toastRegex, List<MooValue> arguments, boolean reverse)");
      generated = ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
          .findFirst().orElseThrow()).withSourcePath(generated.getSourcePath());
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
    if (family.equals("VerbBuiltins")) {
      return List.of("registerRespondTo", "registerDisassemble", "registerDefinitionEdits", "registerMetadataAndCode").get(group);
    }
    if (family.equals("TaskBuiltins")) { return "registerGroup" + group; }
    if (family.equals("ServerBuiltins")) {
      return List.of("registerValueSizes", "registerVersionAndFunctions", "registerDiagnostics", "registerShutdownAndDatabase").get(group);
    }
    if (family.equals("ConnectionBuiltins")) {
      return List.of("registerSwitchPlayer", "registerInventory", "registerOptionsAndListeners", "registerNotify").get(group);
    }
    if (family.equals("ConversionBuiltins")) {
      return List.of("registerConversions", "registerTypeOf").get(group);
    }
    if (family.equals("HostBuiltins")) {
      return List.of("registerEnvironment", "registerTransferAndText").get(group);
    }
    if (family.equals("BinaryBuiltins")) {
      return List.of("registerDecode", "registerEncodeAndChr").get(group);
    }
    if (family.equals("RegexBuiltins")) { return group == 0 ? "registerLegacy" : "registerPcre"; }
    if (family.equals("ObjectBuiltins")) {
      return List.of("registerLifecycleAndTopology", "registerMovementAndWaifs", "registerResetMaximum").get(group);
    }
    if (family.equals("StringBuiltins")) {
      return List.of("registerExplode", "registerSubstitutionAndAnsi", "registerSearchAndCompare").get(group);
    }
    return family.equals("ListBuiltins")
        ? List.of("registerLength", "registerListEdits", "registerSetsAndSort", "registerReverse").get(group)
        : "register";
  }

  private boolean verbCodeGroup(int group) {
    return family.equals("VerbBuiltins") && group == 3;
  }

  private String serverParameters(int group, boolean declaration) {
    if (!family.equals("ServerBuiltins")) { return ""; }
    String catalog = group == 1 || group == 2 ? (declaration ? ", BuiltinCatalog catalog" : ", this") : "";
    return catalog + (group == 2 ? (declaration ? ", ServerLog serverLog" : ", hosts.serverLog()") : "");
  }
  private String registrationImplementation(String implementation) {
    String result = implementation.replace("BuiltinCatalog::", family + "::")
        .replace("parseAnsi(call.arguments())", "parseAnsi(call.arguments(), random)");
    if (family.equals("ServerBuiltins")) {
      result = result.replace("functionInfo(call.arguments())", "functionInfo(call.arguments(), catalog)")
          .replace("reloadProtectedFunctions(call.world())", "catalog.reloadProtectedFunctions(call.world())")
          .replace("serverLog(call.arguments())", "serverLog(call.arguments(), serverLog)")
          .replace("logCacheStats(call.world())", "logCacheStats(call.world(), serverLog)");
    }
    if (family.equals("RandomBuiltins")) {
      result = result.replace("randomInteger(call.arguments())", "randomInteger(call.arguments(), random.get())")
          .replace("floatingRandom(call.arguments())", "floatingRandom(call.arguments(), floatingRandom.get())")
          .replace("randomBytes(call.arguments(), call.world())", "randomBytes(call.arguments(), call.world(), secureRandom)")
          .replace("random.setSeed(SECURE_RANDOM.nextLong())", "random.get().setSeed(secureRandom.nextLong())");
    }
    if (family.equals("TaskBuiltins")) {
      result = switch (result) {
        case "callFunction" -> "call -> callFunction(call, catalog)";
        case "setThreadMode" -> "call -> setThreadMode(call.arguments(), call.threadMode())";
        case "this::configureThreadPool" -> "TaskBuiltins::configureThreadPool";
        default -> result.replace("dynamicEval(call.arguments())", "dynamicEval(call.arguments(), catalog)");
      };
    }
    if (family.equals("ConnectionBuiltins")) {
      result = result.replace("this::connectionNameLookup", "ConnectionBuiltins::connectionNameLookup")
          .replace("this::switchPlayer", "ConnectionBuiltins::switchPlayer")
          .replace("this::openNetworkConnection", "call -> openNetworkConnection(listenerControl, call)");
      for (String name : List.of("bufferedOutputLength", "listen", "listeners", "unlisten")) {
        result = result.replaceAll("\\b" + name + "\\(", name + "(listenerControl, ");
      }
    }
    return result;
  }

  private boolean stringAnsiGroup(int group) {
    return family.equals("StringBuiltins") && group == 1;
  }

  private static String binaryCalls(String source) {
    return source.replace("binaryBytes((StringValue) arguments.getFirst())", "((StringValue) arguments.getFirst()).binaryBytes()")
        .replace("binaryBytes((StringValue) arguments.get(1))", "((StringValue) arguments.get(1)).binaryBytes()")
        .replace("binaryBytes((StringValue) value)", "((StringValue) value).binaryBytes()")
        .replace("encodeBinary(List.of(StringValue.of(value)))", "BuiltinResult.value(StringValue.of(value).encodeBinary())")
        .replace("encodeBinary(List.of(StringValue.of(result.body())))", "BuiltinResult.value(StringValue.of(result.body()).encodeBinary())");
  }

  private static J.CompilationUnit reparse(J.CompilationUnit unit, String source, ExecutionContext context) {
    return ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
        .findFirst().orElseThrow()).withId(unit.getId()).withSourcePath(unit.getSourcePath());
  }

  private static J.CompilationUnit propertySharedImports(J.CompilationUnit unit, ExecutionContext context) {
    return (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
      @Override public J.CompilationUnit visitCompilationUnit(J.CompilationUnit source, ExecutionContext unused) {
        maybeAddImport("world.mongoose.banteng.world.Access", "resolveOwnershipPreamble", false);
        return super.visitCompilationUnit(source, unused);
      }
    }.visitNonNull(unit, context);
  }
  private static J.CompilationUnit objectSharedImports(J.CompilationUnit unit, ExecutionContext context) {
    return (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
      @Override public J.CompilationUnit visitCompilationUnit(J.CompilationUnit source, ExecutionContext unused) {
        maybeAddImport("world.mongoose.banteng.builtin.BuiltinResult", "mutationResult", false);
        return super.visitCompilationUnit(source, unused);
      }
    }.visitNonNull(unit, context);
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
        if (family.equals("ConnectionBuiltins") && unit.getSourcePath().endsWith("builtin/BuiltinHosts.java")) {
          J.ClassDeclaration type = unit.getClasses().getFirst();
          List<Statement> body = new ArrayList<>(type.getBody().getStatements());
          body.add(members.emptyConnections);
          String source = unit.withClasses(List.of(type.withBody(type.getBody().withStatements(body)))).printAll()
              .replace("BuiltinCatalog.standaloneConnections()", "() -> EmptyConnectionRegistry.INSTANCE")
              .replace("import world.mongoose.banteng.builtin.BuiltinCatalog.ConnectionOption;",
                  "import world.mongoose.banteng.builtin.family.ConnectionBuiltins.ConnectionOption;")
              .replace("import java.util.List;", "import java.util.List;\nimport java.util.Optional;\nimport java.util.OptionalLong;");
          return ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
              .findFirst().orElseThrow()).withId(unit.getId()).withSourcePath(unit.getSourcePath());
        }
        if (family.equals("RandomBuiltins")) {
          if (unit.getSourcePath().endsWith("builtin/ValueLimits.java")) {
            J.ClassDeclaration type = unit.getClasses().getFirst();
            List<Statement> body = new ArrayList<>(type.getBody().getStatements());
            body.add(members.stringLengthError);
            maybeAddImport("java.util.Optional", false);
            return super.visitCompilationUnit(unit.withClasses(
                List.of(type.withBody(type.getBody().withStatements(body)))), context);
          }
          if (unit.getSourcePath().endsWith("vm/ArithmeticOps.java")
              || unit.getSourcePath().endsWith("vm/IndexOps.java")) {
            maybeRemoveImport(SOURCE);
            maybeAddImport("world.mongoose.banteng.builtin.ValueLimits", false);
            return super.visitCompilationUnit(unit, context);
          }
        }
        if (family.equals("BinaryBuiltins") && unit.getSourcePath().endsWith("value/MooValue.java")) {
          String decoder = members.binaryBytes.printTrimmed(new Cursor(null, members.source))
              .replace("private static Optional<byte[]> binaryBytes(StringValue encoded)", "public Optional<byte[]> binaryBytes()")
              .replace("byte[] binary = encoded.bytes();", "byte[] binary = bytes();").strip().indent(4);
          J.MethodDeclaration encode = members.implementations.stream()
              .filter(method -> method.getSimpleName().equals("encodeBinary")).findFirst().orElseThrow();
          String original = encode.printTrimmed(new Cursor(null, members.source));
          String encoder = "public StringValue encodeBinary() {\n"
              + original.substring(original.indexOf("ByteArrayOutputStream encoded ="))
                  .replace("raw.size()", "bytes.length").replace("raw.toByteArray()", "bytes")
                  .replace("return BuiltinResult.value(StringValue.of(encoded.toByteArray()));", "return StringValue.of(encoded.toByteArray());");
          encoder = encoder.replace("{\nByteArrayOutputStream", "{\n  ByteArrayOutputStream").indent(4);
          String source = unit.printAll().replace("import java.math.BigDecimal;", "import java.io.ByteArrayOutputStream;\nimport java.math.BigDecimal;")
              .replace("final class StringValue implements MooValue {", "final class StringValue implements MooValue {\n"
                  + "    /** Decodes Toast binary escapes, rejecting malformed escape sequences. */\n" + decoder + "\n"
                  + "    /** Escapes this string as Toast binary input. */\n" + encoder + "\n");
          return reparse(unit, source, context);
        }
        if (family.equals("RegexBuiltins") && (unit.getSourcePath().endsWith("builtin/PcreService.java")
            || unit.getSourcePath().endsWith("builtin/ToastRegexService.java"))) {
          String source = unit.printAll().replace("package world.mongoose.banteng.builtin;",
              "package world.mongoose.banteng.builtin.family;\n\n"
                  + "import world.mongoose.banteng.builtin.BuiltinResult;");
          return ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
              .findFirst().orElseThrow()).withId(unit.getId()).withFileAttributes(unit.getFileAttributes()).withSourcePath(
                  unit.getSourcePath().getParent().resolve("family").resolve(unit.getSourcePath().getFileName()));
        }
        if (family.equals("PropertyBuiltins") && unit.getSourcePath().endsWith("world/Access.java")) {
          J.ClassDeclaration type = unit.getClasses().getFirst();
          List<Statement> body = new ArrayList<>(type.getBody().getStatements());
          body.add(publicMethod(members.ownershipPreamble));
          for (String imported : List.of("java.util.Optional", "world.mongoose.banteng.value.MooValue",
              "world.mongoose.banteng.value.MooValue.ObjectValue",
              "world.mongoose.banteng.value.MooValue.AnonymousObjectValue",
              "world.mongoose.banteng.value.MooValue.ErrorValue")) {
            maybeAddImport(imported, false);
          }
          return super.visitCompilationUnit(unit.withClasses(List.of(type.withBody(type.getBody().withStatements(body)))), context);
        }
        if (family.equals("ObjectBuiltins") && unit.getSourcePath().endsWith("builtin/BuiltinResult.java")) {
          J.ClassDeclaration type = unit.getClasses().getFirst();
          List<Statement> body = new ArrayList<>(type.getBody().getStatements());
          body.add(publicMethod(members.mutationResult));
          maybeAddImport("world.mongoose.banteng.world.WorldResult", false);
          return super.visitCompilationUnit(unit.withClasses(List.of(type.withBody(type.getBody().withStatements(body)))), context);
        }
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
        if (family.equals("BinaryBuiltins")) { changed = reparse(changed, binaryCalls(changed.printAll()), context); }
        if (family.equals("ObjectBuiltins")) { changed = objectSharedImports(changed, context); }
        if (family.equals("PropertyBuiltins")) { changed = propertySharedImports(changed, context); }
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
        if (family.equals("RandomBuiltins") && changed.getSimpleName().equals("stringLengthError")
            && changed.getSelect() instanceof J.Identifier identifier
            && identifier.getSimpleName().equals("BuiltinCatalog")) {
          return changed.withSelect(identifier.withSimpleName("ValueLimits").withType(null));
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
                .filter(statement -> members.regexFields.stream().noneMatch(member -> member.getId().equals(statement.getId())))
                .filter(statement -> members.limits.stream().noneMatch(member -> member.getId().equals(statement.getId())))
                .filter(statement -> members.fields.stream().noneMatch(member -> member.getId().equals(statement.getId())))
                .filter(statement -> !(family.equals("TaskBuiltins") && statement instanceof J.VariableDeclarations field
                    && field.getVariables().stream().anyMatch(variable -> variable.getSimpleName().equals("NUMBER"))))
                 .filter(statement -> members.foldAscii == null || !members.foldAscii.getId().equals(statement.getId()))
                 .filter(statement -> members.ownershipPreamble == null || !members.ownershipPreamble.getId().equals(statement.getId()))
                 .filter(statement -> members.mutationResult == null || !members.mutationResult.getId().equals(statement.getId()))
                 .filter(statement -> members.matchesAt == null || !members.matchesAt.getId().equals(statement.getId()))
                 .filter(statement -> members.stringLengthError == null || !members.stringLengthError.getId().equals(statement.getId()))
                 .filter(statement -> members.binaryBytes == null || !members.binaryBytes.getId().equals(statement.getId()))
                 .filter(statement -> members.emptyConnections == null || !members.emptyConnections.getId().equals(statement.getId()))
                 .filter(statement -> members.standaloneConnections == null || !members.standaloneConnections.getId().equals(statement.getId()))
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
                 + "(entries" + serverParameters(group, false) + (stringAnsiGroup(group) ? ", random" : verbCodeGroup(group) ? ", this" : "")
                 + (family.equals("CryptoBuiltins") ? ", SECURE_RANDOM, hosts.crypt()" : "")
                 + (family.equals("TaskBuiltins") ? ", this" : "")
                 + (family.equals("ConnectionBuiltins") ? ", listenerControl" : "")
                 + (family.equals("RandomBuiltins") ? ", () -> random, () -> floatingRandom, SECURE_RANDOM" : "") + "); } }")
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
    private final List<Statement> regexFields = new ArrayList<>();
    private final List<Statement> fields = new ArrayList<>();
    private J.ClassDeclaration emptyConnections;
    private J.MethodDeclaration standaloneConnections;
    private J.MethodDeclaration foldAscii;
    private J.MethodDeclaration matchesAt;
    private J.MethodDeclaration stringLengthError;
    private J.MethodDeclaration binaryBytes;
    private J.MethodDeclaration mutationResult;
    private J.MethodDeclaration ownershipPreamble;
    private final List<Statement> nested = new ArrayList<>();
    private boolean ready() {
      return !collision && source != null && registrations.size() == builtins.size() - (family.equals("CryptoBuiltins") ? 5 : 0)
          && implementations.size() == methods.size();
    }
  }
}
