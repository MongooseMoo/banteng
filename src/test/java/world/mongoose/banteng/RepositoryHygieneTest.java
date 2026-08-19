package world.mongoose.banteng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import world.mongoose.banteng.bytecode.BytecodeProgram.AstPath;
import world.mongoose.banteng.bytecode.LayoutCompiler;
import world.mongoose.banteng.bytecode.ToastV17ProgramModel;
import world.mongoose.banteng.persistence.ToastV17ProgramLayout;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.WorldTxn;
import org.junit.jupiter.api.Test;

final class RepositoryHygieneTest {
  private static final Path REPOSITORY = Path.of("").toAbsolutePath();

  @Test
  void agentScratchAndGeneratedRootsAreIgnored() throws IOException {
    List<String> ignored = Files.readAllLines(REPOSITORY.resolve(".gitignore"));

    assertTrue(ignored.contains("/notes-*.md"));
    assertTrue(ignored.contains("/prompts/"));
    assertTrue(ignored.contains("/reports/"));
    assertTrue(ignored.contains("/pyghidra_mcp_projects/"));
    assertTrue(ignored.contains("/world/"));
  }

  @Test
  void resumeAfterSuspendInvestigationIsPartOfTheRepository() {
    assertTrue(
        Files.isRegularFile(
            REPOSITORY.resolve("investigations/resume-after-suspend-background-limits.md")));
  }

  @Test
  void obsoleteAndTestOnlySurfaceIsNotPublic() throws NoSuchMethodException {
    assertThrows(
        NoSuchMethodException.class,
        () -> StringValue.class.getDeclaredMethod("equalsCaseSensitively", StringValue.class));
    assertFalse(
        Modifier.isPublic(
            WorldTxn.class.getDeclaredMethod("stageEffect", MooValue.class).getModifiers()));
    assertFalse(
        Modifier.isPublic(
            ToastV17ProgramLayout.class
                .getDeclaredMethod("resolveToastFinallyLabel", String.class, int.class, AstPath.class)
                .getModifiers()));
  }

  @Test
  void toastV17ProgramLayoutIsDecomposedAcrossItsOwnedLayers()
      throws IOException, ClassNotFoundException {
    Class<?> vectorBuilder = Class.forName("world.mongoose.banteng.bytecode.VectorBuilder");

    assertFalse(LayoutCompiler.class.isMemberClass());
    assertEquals("world.mongoose.banteng.bytecode", LayoutCompiler.class.getPackageName());
    assertFalse(vectorBuilder.isMemberClass());
    assertEquals("world.mongoose.banteng.bytecode", vectorBuilder.getPackageName());
    assertEquals(
        ToastV17ProgramModel.class,
        ToastV17ProgramModel.CallBoundary.class.getDeclaringClass());
    assertTrue(
        Arrays.stream(ToastV17ProgramLayout.class.getDeclaredClasses())
            .noneMatch(type -> Modifier.isPublic(type.getModifiers())));
    assertTrue(
        Arrays.stream(ToastV17ProgramLayout.class.getDeclaredClasses())
            .noneMatch(
                type ->
                    type.getSimpleName().equals("LayoutCompiler")
                        || type.getSimpleName().equals("VectorBuilder")));

    int facadeLines =
        Files.readAllLines(
                REPOSITORY.resolve(
                    "src/main/java/world/mongoose/banteng/persistence/ToastV17ProgramLayout.java"))
            .size();
    assertTrue(facadeLines <= 500, "query facade has " + facadeLines + " lines");
  }
}
