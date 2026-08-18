package moo;

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
}
