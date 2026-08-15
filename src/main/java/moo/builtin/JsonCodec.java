package moo.builtin;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import moo.value.MooValue;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.BooleanValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;

/** Toast-compatible JSON translation for the two JSON builtins. */
final class JsonCodec {
  enum Mode {
    COMMON_SUBSET,
    EMBEDDED_TYPES;

    static Optional<Mode> parse(StringValue value) {
      String name = new String(value.bytes(), StandardCharsets.ISO_8859_1);
      return switch (name.toLowerCase(Locale.ROOT)) {
        case "common-subset" -> Optional.of(COMMON_SUBSET);
        case "embedded-types" -> Optional.of(EMBEDDED_TYPES);
        default -> Optional.empty();
      };
    }
  }

  private JsonCodec() {}

  static StringValue generate(MooValue value, Mode mode, boolean disableBinaryEscapes) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    generateValue(output, value, mode, disableBinaryEscapes);
    return new StringValue(output.toByteArray());
  }

  static MooValue parse(StringValue source, Mode mode) {
    Parser parser = new Parser(source.bytes(), mode);
    MooValue value = parser.parseValue(0);
    parser.skipWhitespace();
    if (!parser.atEnd()) {
      if (!parser.rootNumber || parser.peek() == '.') {
        throw new IllegalArgumentException("invalid trailing JSON input");
      }
    }
    return value;
  }

  private static void generateValue(
      ByteArrayOutputStream output,
      MooValue value,
      Mode mode,
      boolean disableBinaryEscapes) {
    if (value instanceof IntegerValue integer) {
      ascii(output, Long.toString(integer.value()));
    } else if (value instanceof FloatValue floating) {
      if (!Double.isFinite(floating.value())) {
        throw new IllegalArgumentException("JSON cannot represent a non-finite float");
      }
      ascii(output, Double.toString(floating.value()));
    } else if (value instanceof BooleanValue bool) {
      ascii(output, bool.value() ? "true" : "false");
    } else if (value instanceof ObjectValue object) {
      quoted(
          output,
          bytes("#" + object.value() + (mode == Mode.EMBEDDED_TYPES ? "|obj" : "")),
          disableBinaryEscapes);
    } else if (value instanceof ErrorValue error) {
      quoted(
          output,
          bytes(error.name() + (mode == Mode.EMBEDDED_TYPES ? "|err" : "")),
          disableBinaryEscapes);
    } else if (value instanceof StringValue string) {
      byte[] bytes = string.bytes();
      if (mode == Mode.EMBEDDED_TYPES && hasTypeSuffix(bytes)) {
        ByteArrayOutputStream typed = new ByteArrayOutputStream(bytes.length + 4);
        typed.writeBytes(bytes);
        typed.writeBytes(bytes("|str"));
        bytes = typed.toByteArray();
      }
      quoted(output, bytes, disableBinaryEscapes);
    } else if (value instanceof ListValue list) {
      output.write('[');
      for (int index = 0; index < list.elements().size(); index++) {
        if (index != 0) {
          output.write(',');
        }
        generateValue(output, list.elements().get(index), mode, disableBinaryEscapes);
      }
      output.write(']');
    } else if (value instanceof MapValue map) {
      output.write('{');
      int index = 0;
      for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
        if (index++ != 0) {
          output.write(',');
        }
        generateKey(output, entry.getKey(), mode, disableBinaryEscapes);
        output.write(':');
        generateValue(output, entry.getValue(), mode, disableBinaryEscapes);
      }
      output.write('}');
    } else if (value instanceof AnonymousObjectValue || value instanceof WaifValue) {
      throw new IllegalArgumentException("JSON cannot represent this MOO value");
    } else {
      throw new IllegalArgumentException("unsupported MOO value");
    }
  }

  private static void generateKey(
      ByteArrayOutputStream output,
      MooValue key,
      Mode mode,
      boolean disableBinaryEscapes) {
    if (key instanceof StringValue string) {
      byte[] bytes = string.bytes();
      if (mode == Mode.EMBEDDED_TYPES && hasTypeSuffix(bytes)) {
        ByteArrayOutputStream typed = new ByteArrayOutputStream(bytes.length + 4);
        typed.writeBytes(bytes);
        typed.writeBytes(bytes("|str"));
        bytes = typed.toByteArray();
      }
      quoted(output, bytes, disableBinaryEscapes);
      return;
    }
    String suffix = "";
    if (mode == Mode.EMBEDDED_TYPES) {
      suffix =
          key instanceof IntegerValue
              ? "|int"
              : key instanceof FloatValue
                  ? "|float"
                  : key instanceof ObjectValue
                      ? "|obj"
                      : key instanceof ErrorValue ? "|err" : "";
    }
    if (key instanceof IntegerValue integer) {
      quoted(output, bytes(Long.toString(integer.value()) + suffix), disableBinaryEscapes);
    } else if (key instanceof FloatValue floating) {
      quoted(output, bytes(Double.toString(floating.value()) + suffix), disableBinaryEscapes);
    } else if (key instanceof ObjectValue object) {
      quoted(output, bytes("#" + object.value() + suffix), disableBinaryEscapes);
    } else if (key instanceof ErrorValue error) {
      quoted(output, bytes(error.name() + suffix), disableBinaryEscapes);
    } else {
      throw new IllegalArgumentException("JSON object key is not representable");
    }
  }

  private static void quoted(
      ByteArrayOutputStream output, byte[] bytes, boolean disableBinaryEscapes) {
    output.write('"');
    for (int index = 0; index < bytes.length; index++) {
      int value = Byte.toUnsignedInt(bytes[index]);
      boolean decodedBinary = false;
      if (!disableBinaryEscapes && value == '~' && index + 2 < bytes.length) {
        int high = Character.digit((char) Byte.toUnsignedInt(bytes[index + 1]), 16);
        int low = Character.digit((char) Byte.toUnsignedInt(bytes[index + 2]), 16);
        int decoded = (high << 4) | low;
        if (high >= 0 && low >= 0 && decoded < 0x20) {
          value = decoded;
          decodedBinary = true;
          index += 2;
        }
      }
      switch (value) {
        case '"' -> ascii(output, "\\\"");
        case '\\' -> ascii(output, "\\\\");
        case '\b' -> ascii(output, "\\b");
        case '\f' -> ascii(output, "\\f");
        case '\n' -> ascii(output, "\\n");
        case '\r' -> ascii(output, "\\r");
        case '\t' -> ascii(output, decodedBinary ? "\\u0009" : "\\t");
        default -> {
          if (value < 0x20 && !disableBinaryEscapes) {
            ascii(output, String.format(Locale.ROOT, "\\u%04X", value));
          } else {
            output.write(value);
          }
        }
      }
    }
    output.write('"');
  }

  private static boolean hasTypeSuffix(byte[] value) {
    return endsWith(value, "|obj")
        || endsWith(value, "|int")
        || endsWith(value, "|float")
        || endsWith(value, "|err")
        || endsWith(value, "|str");
  }

  private static boolean endsWith(byte[] value, String suffix) {
    byte[] expected = bytes(suffix);
    if (expected.length > value.length) {
      return false;
    }
    for (int index = 1; index <= expected.length; index++) {
      if (value[value.length - index] != expected[expected.length - index]) {
        return false;
      }
    }
    return true;
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.ISO_8859_1);
  }

  private static void ascii(ByteArrayOutputStream output, String value) {
    output.writeBytes(bytes(value));
  }

  private static final class Parser {
    private static final long TOAST_MIN_INTEGER = Integer.MIN_VALUE;
    private static final long TOAST_MAX_INTEGER = Integer.MAX_VALUE;
    private final byte[] input;
    private final Mode mode;
    private int position;
    private boolean rootNumber;

    Parser(byte[] input, Mode mode) {
      this.input = input.clone();
      this.mode = mode;
    }

    MooValue parseValue(int depth) {
      if (depth >= 100) {
        throw new IllegalArgumentException("JSON nesting is too deep");
      }
      skipWhitespace();
      if (atEnd()) {
        throw new IllegalArgumentException("missing JSON value");
      }
      return switch (peek()) {
        case '"' -> typedString(parseString());
        case '[' -> parseArray(depth + 1);
        case '{' -> parseObject(depth + 1);
        case 't' -> literal("true", BooleanValue.TRUE);
        case 'f' -> literal("false", BooleanValue.FALSE);
        case 'n' -> literal("null", ErrorValue.E_NONE);
        default -> parseNumber(depth == 0);
      };
    }

    private MooValue parseArray(int depth) {
      consume('[');
      skipWhitespace();
      List<MooValue> values = new ArrayList<>();
      if (consumeIf(']')) {
        return new ListValue(values);
      }
      while (true) {
        values.add(parseValue(depth));
        skipWhitespace();
        if (consumeIf(']')) {
          return new ListValue(values);
        }
        consume(',');
      }
    }

    private MooValue parseObject(int depth) {
      consume('{');
      skipWhitespace();
      MapValue map = new MapValue(Map.of());
      if (consumeIf('}')) {
        return map;
      }
      while (true) {
        skipWhitespace();
        if (atEnd() || peek() != '"') {
          throw new IllegalArgumentException("JSON object key must be a string");
        }
        MooValue key = typedString(parseString());
        skipWhitespace();
        consume(':');
        MooValue value = parseValue(depth);
        map = map.with(key, value);
        skipWhitespace();
        if (consumeIf('}')) {
          return map;
        }
        consume(',');
      }
    }

    private MooValue parseNumber(boolean root) {
      int start = position;
      consumeIf('-');
      int integerStart = position;
      if (consumeIf('0')) {
        // A leading zero is a complete JSON integer token.
      } else {
        consumeDigits();
      }
      if (position == integerStart) {
        throw new IllegalArgumentException("invalid JSON number");
      }
      boolean floating = false;
      if (consumeIf('.')) {
        floating = true;
        int fractionStart = position;
        consumeDigits();
        if (position == fractionStart) {
          throw new IllegalArgumentException("invalid JSON fraction");
        }
      }
      if (consumeIf('e') || consumeIf('E')) {
        floating = true;
        consumeIf('+');
        consumeIf('-');
        int exponentStart = position;
        consumeDigits();
        if (position == exponentStart) {
          throw new IllegalArgumentException("invalid JSON exponent");
        }
      }
      String number =
          new String(input, start, position - start, StandardCharsets.ISO_8859_1);
      rootNumber = root;
      if (!floating) {
        try {
          long integer = Long.parseLong(number);
          if (integer >= TOAST_MIN_INTEGER && integer <= TOAST_MAX_INTEGER) {
            return new IntegerValue(integer);
          }
        } catch (NumberFormatException ignored) {
          // Toast falls back to a float when the integer conversion overflows.
        }
      }
      double value;
      try {
        value = Double.parseDouble(number);
      } catch (NumberFormatException error) {
        throw new IllegalArgumentException("invalid JSON number", error);
      }
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("JSON number is outside float range");
      }
      return new FloatValue(value);
    }

    private StringValue parseString() {
      consume('"');
      ByteArrayOutputStream value = new ByteArrayOutputStream();
      while (!atEnd()) {
        int current = take();
        if (current == '"') {
          return new StringValue(value.toByteArray());
        }
        if (current < 0x20) {
          throw new IllegalArgumentException("unescaped control byte in JSON string");
        }
        if (current != '\\') {
          value.write(current);
          continue;
        }
        if (atEnd()) {
          throw new IllegalArgumentException("unfinished JSON escape");
        }
        switch (take()) {
          case '"' -> value.write('"');
          case '\\' -> value.write('\\');
          case '/' -> value.write('/');
          case 'b' -> binaryByte(value, '\b');
          case 'f' -> binaryByte(value, '\f');
          case 'n' -> binaryByte(value, '\n');
          case 'r' -> binaryByte(value, '\r');
          case 't' -> value.write('\t');
          case 'u' -> writeUnicode(value);
          default -> throw new IllegalArgumentException("invalid JSON escape");
        }
      }
      throw new IllegalArgumentException("unterminated JSON string");
    }

    private void writeUnicode(ByteArrayOutputStream output) {
      int first = readHexCodeUnit();
      int codePoint = first;
      if (Character.isHighSurrogate((char) first)) {
        if (position + 2 > input.length || take() != '\\' || take() != 'u') {
          throw new IllegalArgumentException("unpaired JSON surrogate");
        }
        int second = readHexCodeUnit();
        if (!Character.isLowSurrogate((char) second)) {
          throw new IllegalArgumentException("unpaired JSON surrogate");
        }
        codePoint = Character.toCodePoint((char) first, (char) second);
      } else if (Character.isLowSurrogate((char) first)) {
        throw new IllegalArgumentException("unpaired JSON surrogate");
      }
      for (byte encoded :
          new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8)) {
        binaryByte(output, Byte.toUnsignedInt(encoded));
      }
    }

    private static void binaryByte(ByteArrayOutputStream output, int value) {
      if (value >= 0x20 && value < 0x7f) {
        output.write(value);
        return;
      }
      output.write('~');
      output.write(Character.toUpperCase(Character.forDigit(value >>> 4, 16)));
      output.write(Character.toUpperCase(Character.forDigit(value & 0x0f, 16)));
    }

    private int readHexCodeUnit() {
      if (position + 4 > input.length) {
        throw new IllegalArgumentException("short JSON unicode escape");
      }
      int value = 0;
      for (int index = 0; index < 4; index++) {
        int digit = Character.digit((char) take(), 16);
        if (digit < 0) {
          throw new IllegalArgumentException("invalid JSON unicode escape");
        }
        value = (value << 4) | digit;
      }
      return value;
    }

    private MooValue typedString(StringValue string) {
      if (mode != Mode.EMBEDDED_TYPES) {
        return string;
      }
      byte[] value = string.bytes();
      if (endsWith(value, "|obj")) {
        return new ObjectValue(parseLongPrefix(value, 4));
      }
      if (endsWith(value, "|int")) {
        return new IntegerValue(parseLongPrefix(value, 4));
      }
      if (endsWith(value, "|float")) {
        return new FloatValue(parseDoublePrefix(value, 6));
      }
      if (endsWith(value, "|err")) {
        String name = prefix(value, 4);
        try {
          return ErrorValue.valueOf(name);
        } catch (IllegalArgumentException unknown) {
          return ErrorValue.E_NONE;
        }
      }
      if (endsWith(value, "|str")) {
        return new StringValue(prefix(value, 4).getBytes(StandardCharsets.ISO_8859_1));
      }
      return string;
    }

    private static long parseLongPrefix(byte[] value, int suffixLength) {
      String raw = prefix(value, suffixLength);
      if (raw.startsWith("#")) {
        raw = raw.substring(1);
      }
      int length = numericPrefixLength(raw, false);
      if (length == 0 || (length == 1 && (raw.charAt(0) == '-' || raw.charAt(0) == '+'))) {
        return 0;
      }
      try {
        return Long.parseLong(raw.substring(0, length));
      } catch (NumberFormatException ignored) {
        return 0;
      }
    }

    private static double parseDoublePrefix(byte[] value, int suffixLength) {
      String raw = prefix(value, suffixLength);
      int length = numericPrefixLength(raw, true);
      if (length == 0 || (length == 1 && (raw.charAt(0) == '-' || raw.charAt(0) == '+'))) {
        return 0.0;
      }
      try {
        return Double.parseDouble(raw.substring(0, length));
      } catch (NumberFormatException ignored) {
        return 0.0;
      }
    }

    private static int numericPrefixLength(String value, boolean floating) {
      int index = 0;
      if (index < value.length() && (value.charAt(index) == '-' || value.charAt(index) == '+')) {
        index++;
      }
      while (index < value.length() && Character.isDigit(value.charAt(index))) {
        index++;
      }
      if (floating && index < value.length() && value.charAt(index) == '.') {
        index++;
        while (index < value.length() && Character.isDigit(value.charAt(index))) {
          index++;
        }
      }
      return index;
    }

    private static String prefix(byte[] value, int suffixLength) {
      return new String(
          value, 0, value.length - suffixLength, StandardCharsets.ISO_8859_1);
    }

    private MooValue literal(String expected, MooValue value) {
      for (int index = 0; index < expected.length(); index++) {
        if (atEnd() || take() != expected.charAt(index)) {
          throw new IllegalArgumentException("invalid JSON literal");
        }
      }
      return value;
    }

    private void consumeDigits() {
      while (!atEnd() && peek() >= '0' && peek() <= '9') {
        position++;
      }
    }

    private void skipWhitespace() {
      while (!atEnd()) {
        int current = peek();
        if (current != ' ' && current != '\t' && current != '\r' && current != '\n') {
          return;
        }
        position++;
      }
    }

    private void consume(int expected) {
      skipWhitespace();
      if (atEnd() || take() != expected) {
        throw new IllegalArgumentException("unexpected JSON token");
      }
    }

    private boolean consumeIf(int expected) {
      if (!atEnd() && peek() == expected) {
        position++;
        return true;
      }
      return false;
    }

    private int take() {
      return Byte.toUnsignedInt(input[position++]);
    }

    private int peek() {
      return Byte.toUnsignedInt(input[position]);
    }

    private boolean atEnd() {
      return position >= input.length;
    }
  }
}
