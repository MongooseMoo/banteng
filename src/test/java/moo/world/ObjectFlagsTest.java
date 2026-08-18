package moo.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class ObjectFlagsTest {
  private static final Pattern NUMERIC_OBJECT_FLAG_MASK =
      Pattern.compile(
          "(?i)\\b\\w*flags(?:\\(\\))?\\b[^\\r\\n]*&\\s*(?:1|2|4|8|16|32|64|128|256|512|1024)\\b");
  private static final Pattern LEGACY_FLAG_CONSTANT =
      Pattern.compile(
          "\\b(?:USER|PLAYER|PROGRAMMER|WIZARD|OBSOLETE_[12]|READ|WRITE|FERTILE|ANONYMOUS|INVALID|RECYCLED)_FLAG\\b");
  private static final Pattern NUMERIC_REPLACE_FLAG_ARGUMENT =
      Pattern.compile(
          "\\breplaceFlags\\s*\\([^,]+,\\s*(?:1|2|4|8|16|32|64|128|256|512|1024)\\b");

  @Test
  void permanentBitsMatchTheToastObjectFlagLayout() {
    assertEquals(1, ObjectFlags.FLAG_USER);
    assertEquals(1 << 1, ObjectFlags.FLAG_PROGRAMMER);
    assertEquals(1 << 2, ObjectFlags.FLAG_WIZARD);
    assertEquals(1 << 3, ObjectFlags.FLAG_OBSOLETE_1);
    assertEquals(1 << 4, ObjectFlags.FLAG_READ);
    assertEquals(1 << 5, ObjectFlags.FLAG_WRITE);
    assertEquals(1 << 6, ObjectFlags.FLAG_OBSOLETE_2);
    assertEquals(1 << 7, ObjectFlags.FLAG_FERTILE);
    assertEquals(1 << 8, ObjectFlags.FLAG_ANONYMOUS);
    assertEquals(1 << 9, ObjectFlags.FLAG_INVALID);
    assertEquals(1 << 10, ObjectFlags.FLAG_RECYCLED);
  }

  @Test
  void predicatesTestTheirNamedBits() {
    int allFlags =
        ObjectFlags.FLAG_PROGRAMMER
            | ObjectFlags.FLAG_WIZARD
            | ObjectFlags.FLAG_READ
            | ObjectFlags.FLAG_WRITE
            | ObjectFlags.FLAG_FERTILE;

    assertTrue(ObjectFlags.isProgrammer(allFlags));
    assertTrue(ObjectFlags.isWizard(allFlags));
    assertTrue(ObjectFlags.isReadable(allFlags));
    assertTrue(ObjectFlags.isWritable(allFlags));
    assertTrue(ObjectFlags.isFertile(allFlags));
    assertFalse(ObjectFlags.isProgrammer(allFlags & ~ObjectFlags.FLAG_PROGRAMMER));
    assertFalse(ObjectFlags.isWizard(allFlags & ~ObjectFlags.FLAG_WIZARD));
    assertFalse(ObjectFlags.isReadable(allFlags & ~ObjectFlags.FLAG_READ));
    assertFalse(ObjectFlags.isWritable(allFlags & ~ObjectFlags.FLAG_WRITE));
    assertFalse(ObjectFlags.isFertile(allFlags & ~ObjectFlags.FLAG_FERTILE));
  }

  @Test
  void productionUsesTheCanonicalObjectFlagVocabulary() throws IOException {
    Path productionRoot = Path.of("src", "main", "java");
    List<String> violations = new ArrayList<>();
    try (var paths = Files.walk(productionRoot)) {
      for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
        if (path.getFileName().toString().equals("ObjectFlags.java")) {
          continue;
        }
        String source = Files.readString(path);
        Matcher numericMutation = NUMERIC_REPLACE_FLAG_ARGUMENT.matcher(source);
        if (numericMutation.find()) {
          long line =
              source.substring(0, numericMutation.start()).chars().filter(c -> c == '\n').count()
                  + 1;
          violations.add(path + ":" + line + ":numeric object flag passed to replaceFlags");
        }
        List<String> lines = source.lines().toList();
        for (int index = 0; index < lines.size(); index++) {
          String line = lines.get(index);
          if (NUMERIC_OBJECT_FLAG_MASK.matcher(line).find()
              || LEGACY_FLAG_CONSTANT.matcher(line).find()) {
            violations.add(path + ":" + (index + 1) + ":" + line.strip());
          }
        }
      }
    }

    assertEquals(List.of(), violations);
  }
}
