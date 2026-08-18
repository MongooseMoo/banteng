package moo.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    String after =
        """
        package example;

        import moo.value.MooValue.StringValue;

        class EncodingSites {
          StringValue encode(String text) {
            return StringValue.of(text);
          }

          String decode(StringValue value) {
            return value.text();
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
    assertEquals(after, results.getFirst().getAfter().printAll());
  }
}
