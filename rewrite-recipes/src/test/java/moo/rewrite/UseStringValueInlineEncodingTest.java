package moo.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;

final class UseStringValueInlineEncodingTest {
  private static final String STRING_VALUE_SOURCE =
      """
      package moo.value;

      public interface MooValue {
        final class StringValue {
          public StringValue(byte[] bytes) {}
          public static StringValue of(String text) { return null; }
          public byte[] bytes() { return null; }
          public String text() { return null; }
        }
      }
      """;

  @Test
  void rewritesInlineLatin1EncodeAndDecodeIdioms() {
    String before =
        """
        package example;

        import java.nio.charset.StandardCharsets;
        import moo.value.MooValue.StringValue;

        class EncodingSites {
          StringValue encode(String text) {
            return new StringValue(text.getBytes(StandardCharsets.ISO_8859_1));
          }

          String decode(StringValue value) {
            return new String(value.bytes(), StandardCharsets.ISO_8859_1);
          }
        }
        """;
    InMemoryExecutionContext context =
        new InMemoryExecutionContext(
            failure -> {
              throw new AssertionError(failure);
            });
    List<SourceFile> sources =
        JavaParser.fromJavaVersion().build().parse(context, STRING_VALUE_SOURCE, before).toList();
    List<Result> results =
        new UseStringValueInlineEncoding()
            .run(new InMemoryLargeSourceSet(sources), context)
            .getChangeset()
            .getAllResults();

    assertEquals(1, results.size());
    String rewritten = results.getFirst().getAfter().printAll();
    assertTrue(rewritten.contains("return StringValue.of(text);"), rewritten);
    assertTrue(rewritten.contains("return value.text();"), rewritten);
    assertFalse(rewritten.contains("new StringValue(text.getBytes"), rewritten);
    assertFalse(rewritten.contains("new String(value.bytes()"), rewritten);
    assertFalse(rewritten.contains("StandardCharsets.ISO_8859_1"), rewritten);
    assertFalse(rewritten.contains("import java.nio.charset.StandardCharsets;"), rewritten);
  }
}
