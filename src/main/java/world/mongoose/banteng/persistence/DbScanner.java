package world.mongoose.banteng.persistence;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;
import world.mongoose.banteng.value.MooValue.StringValue;

/** Shared line-oriented primitives for LambdaMOO database readers. */
final class DbScanner implements Closeable {
  private final InputStream input;
  private final byte[] buffer = new byte[64 * 1024];
  private int position;
  private int limit;
  private boolean skipLineFeed;

  DbScanner(InputStream input) {
    this.input = input;
  }

  @Override
  public void close() throws IOException {
    input.close();
  }

  @Nullable String readLine() throws IOException {
    if (!startLine()) return null;
    int end = lineEnd();
    if (end < limit) {
      String text = text(position, end);
      consumeEnd(end);
      return text;
    }
    StringBuilder text = new StringBuilder(limit - position);
    while (true) {
      text.append(text(position, end));
      if (end < limit) {
        consumeEnd(end);
        return text.toString();
      }
      if (!fill()) return text.toString();
      end = lineEnd();
    }
  }

  private boolean startLine() throws IOException {
    if (position >= limit && !fill()) return false;
    if (skipLineFeed) {
      skipLineFeed = false;
      if (buffer[position] == '\n') {
        position++;
        if (position == limit && !fill()) return false;
      }
    }
    return true;
  }

  private int lineEnd() {
    int end = position;
    while (end < limit && buffer[end] != '\r' && buffer[end] != '\n') end++;
    return end;
  }

  private void consumeEnd(int end) {
    skipLineFeed = buffer[end] == '\r';
    position = end + 1;
  }

  private boolean fill() throws IOException {
    position = 0;
    limit = input.read(buffer);
    return limit > 0;
  }

  private String text(int from, int to) {
    return new String(buffer, from, to - from, StringValue.charset());
  }

  private long readDecimal(String field, boolean integer) throws IOException {
    if (!startLine()) throw malformed("unexpected end of file while reading " + field);
    int end = position;
    boolean negative = buffer[end] == '-';
    if (negative || buffer[end] == '+') end++;
    int start = end;
    long magnitude = 0;
    // Longer numbers use the JDK parser, including the signed 64-bit limits.
    while (end < limit && end - start < 18) {
      int digit = buffer[end] - '0';
      if (digit < 0 || digit > 9) break;
      magnitude = magnitude * 10 + digit;
      end++;
    }
    long value = negative ? -magnitude : magnitude;
    if (end > start && end < limit && (buffer[end] == '\r' || buffer[end] == '\n')
        && (!integer || (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE))) {
      consumeEnd(end);
      return value;
    }
    String line = requiredLine(this, field);
    return integer ? parseInt(line, field) : parseLong(line, field);
  }

  StringValue readStringValue() throws IOException {
    if (!startLine()) throw malformed("unexpected end of file while reading string value");
    int end = lineEnd();
    if (end == limit) return StringValue.of(requiredLine(this, "string value"));
    StringValue value = StringValue.of(buffer, position, end);
    consumeEnd(end);
    return value;
  }

  static int readCount(DbScanner input, String field) throws IOException {
    return count(readInt(input, field), field);
  }

  static int parseCount(String text, String field) throws IOException {
    return count(parseInt(text, field), field);
  }

  private static int count(int value, String field) throws IOException {
    if (value < 0) {
      throw malformed(field + " must not be negative");
    }
    return value;
  }

  static int readInt(DbScanner input, String field) throws IOException {
    return (int) input.readDecimal(field, true);
  }

  static int parseInt(String text, String field) throws IOException {
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException error) {
      throw malformed("invalid " + field + ": " + text, error);
    }
  }

  static long readLong(DbScanner input, String field) throws IOException {
    return input.readDecimal(field, false);
  }

  static long parseLong(String text, String field) throws IOException {
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException error) {
      throw malformed("invalid " + field + ": " + text, error);
    }
  }

  static double readDouble(DbScanner input, String field) throws IOException {
    String text = requiredLine(input, field);
    try {
      return Double.parseDouble(text);
    } catch (NumberFormatException error) {
      throw malformed("invalid " + field + ": " + text, error);
    }
  }

  static void requireExact(DbScanner input, String expected, String field) throws IOException {
    String actual = requiredLine(input, field);
    if (!actual.equals(expected)) {
      throw malformed("invalid " + field + ": " + actual);
    }
  }

  static String requiredLine(DbScanner input, String field) throws IOException {
    String line = input.readLine();
    if (line == null) {
      throw malformed("unexpected end of file while reading " + field);
    }
    return line;
  }

  static IOException malformed(String message) {
    return new IOException(message);
  }

  static IOException malformed(String message, Throwable cause) {
    return new IOException(message, cause);
  }
}
