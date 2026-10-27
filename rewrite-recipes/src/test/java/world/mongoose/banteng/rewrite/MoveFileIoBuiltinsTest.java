package world.mongoose.banteng.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

final class MoveFileIoBuiltinsTest {
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
