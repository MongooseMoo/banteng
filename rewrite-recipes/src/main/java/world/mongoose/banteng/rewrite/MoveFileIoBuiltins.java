package world.mongoose.banteng.rewrite;

import java.nio.file.Path;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

/** Moves the existing stateful file owner and its registrations together. */
public final class MoveFileIoBuiltins extends ScanningRecipe<MoveFileIoBuiltins.Members> {
  private static final String PACKAGE = "world.mongoose.banteng.builtin";
  public static final class Members {
    String registrations;
    String manifest;
    String helper;
  }
  @Override public String getDisplayName() { return "Move File I/O builtins"; }
  @Override public String getDescription() { return "Moves the existing file owner into the family package."; }
  @Override public Members getInitialValue(ExecutionContext context) { return new Members(); }
  @Override public TreeVisitor<?, ExecutionContext> getScanner(Members members) {
    return new JavaIsoVisitor<ExecutionContext>() {
      @Override public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
          ExecutionContext context) {
        if (!getCursor().firstEnclosingOrThrow(J.CompilationUnit.class).getSourcePath()
            .endsWith("BuiltinCatalog.java")) { return method; }
        if (method.getSimpleName().equals("addFileIoManifest")) {
          members.manifest = method.printTrimmed(getCursor());
          StringBuilder registrations = new StringBuilder();
          for (var statement : method.getBody().getStatements()) {
            var arguments = ((J.MethodInvocation) ((J.MethodInvocation) statement)
                .getArguments().getFirst()).getArguments();
            String handler = arguments.get(2).printTrimmed(getCursor());
            registrations.append("    registrar.builtin(")
                .append(arguments.get(0).printTrimmed(getCursor())).append(", List.of(")
                .append(arguments.get(1).printTrimmed(getCursor()))
                .append(")).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable()")
                .append(".implementation(call -> ").append(handler.substring(handler.indexOf("::") + 2))
                .append("(call.arguments()));\n");
          }
          members.registrations = registrations.toString();
        } else if (method.getSimpleName().equals("fileIoSpec")) {
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
          case "FileIoService.java" -> {
            StringBuilder imports = new StringBuilder("package " + PACKAGE + ".family;\nimport java.util.Optional;\n");
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
                .replace("final class FileIoService {", "public final class FileIoBuiltins {\n" + constants
                    + "  public void register(List<BuiltinSpec> entries) {\n"
                    + "    BuiltinRegistrar registrar = new BuiltinRegistrar(entries);\n" + members.registrations + "  }\n")
                .replace("  FileIoService(", "  public FileIoBuiltins(")
                .replace("  synchronized BuiltinResult ", "  private synchronized BuiltinResult ");
            path = path.getParent().resolve("family/FileIoBuiltins.java");
          }
          case "BuiltinCatalog.java" -> {
            J.CompilationUnit cleaned = (J.CompilationUnit) new JavaIsoVisitor<ExecutionContext>() {
              @Override public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                  ExecutionContext unused) {
                return method.getSimpleName().equals("addFileIoManifest")
                    || method.getSimpleName().equals("fileIoSpec") ? null : method;
              }
            }.visitNonNull(unit, context);
            source = cleaned.printAll()
                .replace("import java.util.function.Function;", "import " + PACKAGE + ".family.FileIoBuiltins;")
                .replace("FileIoService", "FileIoBuiltins")
                .replace("addFileIoManifest(entries)", "fileIo.register(entries)");
          }
          case "ConfinedFileRoot.java" -> source = source
              .replace("final class ConfinedFileRoot", "public final class ConfinedFileRoot")
              .replace("  ConfinedFileRoot(", "  public ConfinedFileRoot(")
              .replace("  @Nullable Path resolve(", "  public @Nullable Path resolve(");
          default -> { return unit; }
        }
        return ((J.CompilationUnit) JavaParser.fromJavaVersion().build().parse(context, source)
            .findFirst().orElseThrow()).withId(unit.getId()).withSourcePath(path);
      }
    };
  }
}
