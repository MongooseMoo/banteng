package moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class DuplicationConsolidationTest {
  private static final Path PRODUCTION = Path.of("src", "main", "java", "moo");

  @Test
  void verbAndPropertyMutationShareTheOwnershipPreamble() throws IOException {
    String source = source("builtin", "BuiltinCatalog.java");
    String addVerb = between(source, "private static BuiltinResult addVerb(", "private static BuiltinResult addProperty(");
    String addProperty = between(source, "private static BuiltinResult addProperty(", "private static BuiltinResult properties(");

    assertTrue(source.contains("private static Optional<ErrorValue> resolveOwnershipPreamble("));
    for (String mutation : new String[] {addVerb, addProperty}) {
      assertTrue(mutation.contains("resolveOwnershipPreamble("));
      assertFalse(mutation.contains("WorldObject actor = world.object(programmer).orElse(null);"));
      assertFalse(mutation.contains("if (receiver instanceof ObjectValue object) {"));
    }
  }

  @Test
  void promotionAwareUnaryFloatBuiltinsShareOneFactory() throws IOException {
    String source = source("builtin", "BuiltinCatalog.java");
    String manifest =
        between(
            source,
            "private List<BuiltinSpec> buildManifest()",
            "private static BuiltinSpec fileIoSpec(");

    assertTrue(
        source.contains(
            "private static BuiltinHandler unaryFloatBuiltin(DoubleUnaryOperator operator)"));
    assertEquals(18, occurrences(manifest, "unaryFloatBuiltin("));
  }

  @Test
  void runtimeServerMessagesShareListenerAwareFallbackResolution() throws IOException {
    String source = source("runtime", "MooRuntime.java");
    String compactSource = source.replaceAll("\\s+", "");

    assertTrue(
        compactSource.contains(
            "privateList<String>serverMessage(longlistenerHandler,Stringname,Stringfallback)"));
    assertEquals(6, occurrences(source, "serverMessage("));
    assertEquals(0, occurrences(source, "MooValue message = null;"));
  }

  @Test
  void firstAndLastIndexOpcodesShareOneDirectionalImplementation() throws IOException {
    String source = source("vm", "IndexOps.java");

    assertTrue(
        source.contains(
            "private static void boundaryIndex(\n"
                + "      Frame frame, VmState state, WorldTxn world, boolean last)"));
    assertEquals(2, occurrences(source, "boundaryIndex(frame, state, world,"));
    assertFalse(source.contains("private static void firstIndex("));
    assertFalse(source.contains("private static void lastIndex("));
  }

  private static String source(String... path) throws IOException {
    return Files.readString(PRODUCTION.resolve(Path.of("", path)));
  }

  private static String between(String source, String start, String end) {
    int startIndex = source.indexOf(start);
    int endIndex = source.indexOf(end, startIndex);
    assertTrue(startIndex >= 0, start);
    assertTrue(endIndex > startIndex, end);
    return source.substring(startIndex, endIndex);
  }

  private static int occurrences(String source, String text) {
    int count = 0;
    int offset = 0;
    while ((offset = source.indexOf(text, offset)) >= 0) {
      count++;
      offset += text.length();
    }
    return count;
  }
}
