package world.mongoose.banteng.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

final class MoveFileIoBuiltinsTest {
  @Test void relocatesSqliteWithThreadModeAndSelectiveIrrevocability() {
    var context = new InMemoryExecutionContext(failure -> { throw new AssertionError(failure); });
    var files = JavaParser.fromJavaVersion().build().parse(context, """
        package world.mongoose.banteng.builtin;
        class BuiltinCatalog {
          SqliteService sqlite;
          BuiltinCatalog() {
            ConfinedFileRoot files = new ConfinedFileRoot(null);
            sqlite = new SqliteService(files);
          }
          void buildManifest() { before(); addSqliteManifest(entries); after(); }
          private void addSqliteManifest(List<BuiltinSpec> entries) {
            entries.add(sqliteSpec("sqlite_open", new CallShape(List.of(STRING), List.of(INTEGER), Optional.empty()), true, call -> sqlite.open(call.arguments())));
            entries.add(sqliteSpec("sqlite_handles", new CallShape(List.of(), List.of(), Optional.empty()), false, call -> sqlite.handles(call.arguments())));
            entries.add(sqliteSpec("sqlite_query", new CallShape(List.of(INTEGER, STRING), List.of(ANY), Optional.empty()), false, call -> sqlite.query(call.arguments(), call.threadMode())));
            entries.add(sqliteSpec("sqlite_execute", new CallShape(List.of(INTEGER, STRING, Set.of(ArgType.LIST)), List.of(), Optional.empty()), false, call -> sqlite.execute(call.arguments(), call.threadMode())));
            entries.add(sqliteSpec("sqlite_interrupt", new CallShape(List.of(INTEGER), List.of(), Optional.empty()), true, call -> sqlite.interrupt(call.arguments())));
          }
          private static BuiltinSpec sqliteSpec(String name, CallShape shape, boolean irrevocable, BuiltinHandler handler) { return null; }
        }
        """, """
        package world.mongoose.banteng.builtin;
        import java.util.List;
        final class SqliteService {
          private final ConfinedFileRoot files;
          SqliteService(ConfinedFileRoot files) { this.files = files; }
          synchronized BuiltinResult open(List<MooValue> arguments) { return unchanged(arguments); }
          synchronized BuiltinResult handles(List<MooValue> arguments) { return unchanged(); }
          synchronized BuiltinResult query(List<MooValue> arguments, boolean threaded) { return unchanged(arguments); }
          synchronized BuiltinResult execute(List<MooValue> arguments, boolean threaded) { return unchanged(arguments); }
          BuiltinResult interrupt(List<MooValue> arguments) { return unchanged(arguments); }
        }
        """, """
        package world.mongoose.banteng.builtin;
        public final class ConfinedFileRoot { }
        """).toList();
    var recipe = Environment.builder().scanRuntimeClasspath().build()
        .activateRecipes("world.mongoose.banteng.builtin.ExtractSqliteBuiltins");
    var mover = (MoveFileIoBuiltins) recipe.getRecipeList().getFirst();
    var changes = mover.clone().run(new InMemoryLargeSourceSet(files), context)
        .getChangeset().getAllResults();
    assertEquals(2, changes.size());
    for (var change : changes) {
      assertTrue(change.getBefore() != null);
      String source = change.getAfter().printAll();
      if (change.getAfter().getSourcePath().endsWith("SqliteBuiltins.java")) {
        assertTrue(source.contains("public SqliteBuiltins(ConfinedFileRoot files)"));
        assertTrue(source.contains("private synchronized BuiltinResult query"));
        assertTrue(source.contains("private synchronized BuiltinResult handles()"));
        assertTrue(source.contains(".WIZARD_ONLY).implementation(call -> handles())"));
        assertTrue(source.contains("private BuiltinResult interrupt"));
        assertTrue(source.contains(".irrevocable().implementation(call -> open(call.arguments()))"));
        assertTrue(source.contains(".irrevocable().implementation(call -> interrupt(call.arguments()))"));
        assertTrue(source.contains(".WIZARD_ONLY).implementation(call -> query(call.arguments(), call.threadMode()))"));
        assertTrue(source.contains(".WIZARD_ONLY).implementation(call -> execute(call.arguments(), call.threadMode()))"));
        assertTrue(source.contains("List.of(INTEGER, STRING, Set.of(ArgType.LIST))"));
        assertTrue(source.indexOf("\"sqlite_open\"") < source.indexOf("\"sqlite_query\""));
        assertTrue(source.indexOf("\"sqlite_query\"") < source.indexOf("\"sqlite_execute\""));
        assertTrue(source.indexOf("\"sqlite_execute\"") < source.indexOf("\"sqlite_interrupt\""));
      } else {
        assertTrue(source.contains("before(); sqlite.register(entries); after();"));
        assertTrue(source.contains("new SqliteBuiltins(files)"));
        assertFalse(source.contains("sqliteSpec"));
        assertFalse(source.contains("SqliteService"));
      }
    }
  }

  @Test void relocatesExistingOwnerWithOptionalShapeAndSharedRoot() {
    var context = new InMemoryExecutionContext(failure -> { throw new AssertionError(failure); });
    var files = JavaParser.fromJavaVersion().build().parse(context, """
        package world.mongoose.banteng.builtin;
        import java.util.function.Function;
        class BuiltinCatalog {
          FileIoService fileIo;
          BuiltinCatalog() {
            ConfinedFileRoot files = new ConfinedFileRoot(null);
            fileIo = new FileIoService(files);
            sqlite = new SqliteService(files);
          }
          void buildManifest() { before(); addFileIoManifest(entries); after(); }
          private void addFileIoManifest(List<BuiltinSpec> entries) {
            entries.add(fileIoSpec("file_handles", new CallShape(List.of(), List.of(), Optional.empty()), fileIo::handles));
            entries.add(fileIoSpec("file_grep", new CallShape(List.of(INTEGER, STRING), List.of(INTEGER), Optional.empty()), fileIo::grep));
          }
          private static BuiltinSpec fileIoSpec(String name, CallShape shape, Function handler) { return null; }
        }
        """, """
        package world.mongoose.banteng.builtin;
        import java.util.List;
        import java.util.Set;
        final class FileIoService {
          private final ConfinedFileRoot root;
          FileIoService(ConfinedFileRoot root) { this.root = root; }
          synchronized BuiltinResult grep(List<MooValue> arguments) { return unchanged(arguments); }
          synchronized BuiltinResult handles(List<MooValue> arguments) { return inventory(); }
        }
        """, """
        package world.mongoose.banteng.builtin;
        final class ConfinedFileRoot {
          ConfinedFileRoot(Path root) { }
          @Nullable Path resolve(String name) { return null; }
        }
        """).toList();
    var changes = new MoveFileIoBuiltins().run(new InMemoryLargeSourceSet(files), context)
        .getChangeset().getAllResults();
    assertEquals(3, changes.size());
    for (var change : changes) {
      assertTrue(change.getBefore() != null, "Existing files must retain identity");
      String source = change.getAfter().printAll();
      String name = change.getAfter().getSourcePath().getFileName().toString();
      if (name.equals("FileIoBuiltins.java")) {
        assertTrue(source.contains(".permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable()"));
        assertTrue(source.contains("List.of(INTEGER, STRING), List.of(INTEGER), Optional.empty()"));
        assertTrue(source.contains("private synchronized BuiltinResult grep(List<MooValue> arguments) { return unchanged(arguments); }"));
        assertTrue(source.contains("private synchronized BuiltinResult handles() { return inventory(); }"));
        assertTrue(source.contains("implementation(call -> handles())"));
      } else if (name.equals("BuiltinCatalog.java")) {
        assertTrue(source.contains("before(); fileIo.register(entries); after();"));
        assertTrue(source.contains("new FileIoBuiltins(files)"));
        assertTrue(source.contains("new SqliteService(files)"));
        assertFalse(source.contains("fileIoSpec"), source);
      } else {
        assertTrue(source.contains("public final class ConfinedFileRoot"));
        assertTrue(source.contains("public @Nullable Path resolve"));
      }
    }
  }
}
