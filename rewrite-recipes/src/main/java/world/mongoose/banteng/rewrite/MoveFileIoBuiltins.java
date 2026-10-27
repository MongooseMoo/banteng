package world.mongoose.banteng.rewrite;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

/** Moves an existing stateful file or SQLite owner and its registrations together. */
public final class MoveFileIoBuiltins extends ScanningRecipe<MoveFileIoBuiltins.Members> {
  private static final String PACKAGE = "world.mongoose.banteng.builtin";
  private final String family;
  public MoveFileIoBuiltins() { this("FileIo"); }
  @JsonCreator public MoveFileIoBuiltins(@JsonProperty("family") String family) {
    this.family = java.util.Objects.requireNonNull(family);
  }
  @Override public MoveFileIoBuiltins clone() {
    // Preserve immutable configuration while giving the clone its own scan accumulator key.
    return (MoveFileIoBuiltins) super.clone();
  }
  private boolean sqlite() { return family.equals("Sqlite"); }
  private String owner() { return sqlite() ? "sqlite" : "fileIo"; }
  public static final class Members {
    String registrations;
    String manifest;
    String helper;
  }
  @Override public String getDisplayName() { return "Move existing file or SQLite builtins"; }
  @Override public String getDescription() { return "Moves the existing service owner into the family package."; }
  @Override public Members getInitialValue(ExecutionContext context) { return new Members(); }
  @Override public TreeVisitor<?, ExecutionContext> getScanner(Members members) {
    return new JavaIsoVisitor<ExecutionContext>() {
      @Override public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
          ExecutionContext context) {
        if (!getCursor().firstEnclosingOrThrow(J.CompilationUnit.class).getSourcePath()
            .endsWith("BuiltinCatalog.java")) { return method; }
        if (method.getSimpleName().equals("add" + family + "Manifest")) {
          members.manifest = method.printTrimmed(getCursor());
          StringBuilder registrations = new StringBuilder();
          for (var statement : method.getBody().getStatements()) {
            var arguments = ((J.MethodInvocation) ((J.MethodInvocation) statement)
                .getArguments().getFirst()).getArguments();
            String handler = arguments.get(sqlite() ? 3 : 2).printTrimmed(getCursor());
            if (sqlite()) { handler = handler.replace("sqlite.handles(call.arguments())", "sqlite.handles()"); }
            String handlerName = handler.substring(handler.indexOf("::") + 2);
            registrations.append("    registrar.builtin(")
                .append(arguments.get(0).printTrimmed(getCursor())).append(", List.of(")
                .append(arguments.get(1).printTrimmed(getCursor()))
                .append(")).permission(BuiltinPermissionRule.WIZARD_ONLY)");
            if (!sqlite() || arguments.get(2).printTrimmed(getCursor()).equals("true")) {
              registrations.append(".irrevocable()");
            }
            registrations.append(".implementation(")
                .append(sqlite() ? handler.replace("sqlite.", "")
                    : "call -> " + handlerName + (handlerName.equals("handles") ? "()" : "(call.arguments())"))
                .append(");\n");
          }
          members.registrations = registrations.toString();
        } else if (method.getSimpleName().equals(owner() + "Spec")) {
          members.helper = method.printTrimmed(getCursor());
        }
        return method;
      }
    };
  }
  @Override public TreeVisitor<?, ExecutionContext> getVisitor(Members members) {
    return new JavaIsoVisitor<ExecutionContext>() {
      @Override public J.CompilationUnit visitCompilationUnit(J.CompilationUnit unit, ExecutionContext context) {
        if (members.registrations == null || members.helper == null) { return unit; }
        String source = unit.printAll();
        Path path = unit.getSourcePath();
        switch (path.getFileName().toString()) {
          case "FileIoService.java", "SqliteService.java" -> {
            if (!path.getFileName().toString().equals(family + "Service.java")) { return unit; }
            StringBuilder imports = new StringBuilder("package " + PACKAGE + ".family;\nimport java.util.Optional;\n");
            if (sqlite()) { imports.append("import java.util.Set;\n"); }
            for (String name : new String[] {"ArgType", "BuiltinRegistrar", "BuiltinPermissionRule",
                "BuiltinResult", "BuiltinSpec", "CallShape", "ConfinedFileRoot"}) {
              imports.append("import ").append(PACKAGE).append('.').append(name).append(";\n");
            }
            StringBuilder constants = new StringBuilder();
            for (String name : new String[] {"ANY", "INTEGER", "STRING"}) {
              constants.append("  private static final Set<ArgType> ").append(name)
                  .append(" = Set.of(ArgType.").append(name).append(");\n");
            }
            source = source.replace("package " + PACKAGE + ";", imports.toString())
                .replace("final class " + family + "Service {", "public final class " + family + "Builtins {\n" + constants
                    + "  public void register(List<BuiltinSpec> entries) {\n"
                    + "    BuiltinRegistrar registrar = new BuiltinRegistrar(entries);\n" + members.registrations + "  }\n")
                .replace("  " + family + "Service(", "  public " + family + "Builtins(")
                .replace("  synchronized BuiltinResult ", "  private synchronized BuiltinResult ")
                .replace("\n  BuiltinResult ", "\n  private BuiltinResult ");
            source = source.replace("BuiltinResult handles(List<MooValue> arguments)", "BuiltinResult handles()");
            path = path.getParent().resolve("family/" + family + "Builtins.java");
          }
          case "BuiltinCatalog.java" -> {
            J.CompilationUnit cleaned = (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
              @Override public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                  ExecutionContext unused) {
                return method.getSimpleName().equals("add" + family + "Manifest")
                    || method.getSimpleName().equals(owner() + "Spec") ? null : method;
              }
            }.visitNonNull(unit, context);
            source = cleaned.printAll()
                .replace(sqlite() ? "package " + PACKAGE + ";" : "import java.util.function.Function;",
                    (sqlite() ? "package " + PACKAGE + ";\n" : "")
                        + "import " + PACKAGE + ".family." + family + "Builtins;")
                .replace(family + "Service", family + "Builtins")
                .replace("add" + family + "Manifest(entries)", owner() + ".register(entries)");
          }
          case "ConfinedFileRoot.java" -> {
            if (sqlite()) { return unit; }
            source = source
              .replace("final class ConfinedFileRoot", "public final class ConfinedFileRoot")
              .replace("  ConfinedFileRoot(", "  public ConfinedFileRoot(")
              .replace("  @Nullable Path resolve(", "  public @Nullable Path resolve(");
          }
          default -> { return unit; }
        }
        return ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
            .findFirst().orElseThrow()).withId(unit.getId()).withSourcePath(path);
      }
    };
  }
}
