package moo.persistence;

import java.io.BufferedReader;
import java.io.IOException;

/** Shared line-oriented primitives for LambdaMOO database readers. */
final class DbScanner {
  private DbScanner() {}

  static int readCount(BufferedReader input, String field) throws IOException {
    return parseCount(requiredLine(input, field), field);
  }

  static int parseCount(String text, String field) throws IOException {
    int value = parseInt(text, field);
    if (value < 0) {
      throw malformed(field + " must not be negative");
    }
    return value;
  }

  static int readInt(BufferedReader input, String field) throws IOException {
    return parseInt(requiredLine(input, field), field);
  }

  static int parseInt(String text, String field) throws IOException {
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException error) {
      throw malformed("invalid " + field + ": " + text, error);
    }
  }

  static long readLong(BufferedReader input, String field) throws IOException {
    return parseLong(requiredLine(input, field), field);
  }

  static long parseLong(String text, String field) throws IOException {
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException error) {
      throw malformed("invalid " + field + ": " + text, error);
    }
  }

  static double readDouble(BufferedReader input, String field) throws IOException {
    String text = requiredLine(input, field);
    try {
      return Double.parseDouble(text);
    } catch (NumberFormatException error) {
      throw malformed("invalid " + field + ": " + text, error);
    }
  }

  static void requireExact(BufferedReader input, String expected, String field) throws IOException {
    String actual = requiredLine(input, field);
    if (!actual.equals(expected)) {
      throw malformed("invalid " + field + ": " + actual);
    }
  }

  static String requiredLine(BufferedReader input, String field) throws IOException {
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
