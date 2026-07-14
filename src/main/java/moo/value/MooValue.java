package moo.value;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The closed value family required by the first server slice. */
public sealed interface MooValue
    permits MooValue.IntegerValue,
        MooValue.FloatValue,
        MooValue.StringValue,
        MooValue.ObjectValue,
        MooValue.ErrorValue,
        MooValue.ListValue {

  /** Returns the persisted MOO type tag. */
  Type type();

  /** Returns this value's MOO truth. */
  boolean isTruthy();

  /** Returns source text for this value as a MOO literal. */
  String toLiteral();

  /** Persisted type tags used by the values in this slice. */
  enum Type {
    INTEGER(0),
    OBJECT(1),
    STRING(2),
    ERROR(3),
    LIST(4),
    FLOAT(9);

    private final int code;

    Type(int code) {
      this.code = code;
    }

    /** Returns the database type code. */
    public int code() {
      return code;
    }
  }

  /** A signed 64-bit MOO integer. */
  record IntegerValue(long value) implements MooValue {
    @Override
    public Type type() {
      return Type.INTEGER;
    }

    @Override
    public boolean isTruthy() {
      return value != 0;
    }

    @Override
    public String toLiteral() {
      return Long.toString(value);
    }

    @Override
    public String toString() {
      return toLiteral();
    }
  }

  /** An immutable IEEE-754 binary64 MOO float. */
  final class FloatValue implements MooValue {
    private final double value;

    public FloatValue(double value) {
      this.value = value;
    }

    /** Returns the primitive binary64 payload. */
    public double value() {
      return value;
    }

    @Override
    public Type type() {
      return Type.FLOAT;
    }

    @Override
    public boolean isTruthy() {
      return value != 0.0;
    }

    @Override
    public String toLiteral() {
      if (value == 0.0) {
        return "0.0";
      }
      String formatted = String.format(Locale.ROOT, "%.15g", value);
      int exponent = Math.max(formatted.indexOf('e'), formatted.indexOf('E'));
      String significand = exponent < 0 ? formatted : formatted.substring(0, exponent);
      String suffix = exponent < 0 ? "" : formatted.substring(exponent);
      if (significand.indexOf('.') >= 0) {
        int end = significand.length();
        while (end > 0 && significand.charAt(end - 1) == '0') {
          end--;
        }
        if (end > 0 && significand.charAt(end - 1) == '.') {
          end--;
        }
        significand = significand.substring(0, end);
      }
      if (significand.indexOf('.') < 0 && suffix.isEmpty()) {
        significand += ".0";
      }
      return significand + suffix;
    }

    @Override
    public boolean equals(Object other) {
      return this == other || (other instanceof FloatValue floating && value == floating.value);
    }

    @Override
    public int hashCode() {
      return Double.hashCode(value == 0.0 ? 0.0 : value);
    }

    @Override
    public String toString() {
      return toLiteral();
    }
  }

  /** An immutable binary MOO string represented as owned Latin-1 bytes. */
  final class StringValue implements MooValue {
    private final byte[] bytes;

    /** Creates a string by taking a defensive copy of {@code bytes}. */
    public StringValue(byte[] bytes) {
      this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    /** Returns a defensive copy of the binary string contents. */
    public byte[] bytes() {
      return Arrays.copyOf(bytes, bytes.length);
    }

    /** Returns the byte length of this string. */
    public int length() {
      return bytes.length;
    }

    @Override
    public Type type() {
      return Type.STRING;
    }

    @Override
    public boolean isTruthy() {
      return bytes.length != 0;
    }

    @Override
    public String toLiteral() {
      StringBuilder literal = new StringBuilder(bytes.length + 2);
      literal.append('"');
      for (byte value : bytes) {
        int unsigned = Byte.toUnsignedInt(value);
        if (unsigned == '"' || unsigned == '\\') {
          literal.append('\\');
        }
        literal.append((char) unsigned);
      }
      return literal.append('"').toString();
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof StringValue string) || bytes.length != string.bytes.length) {
        return false;
      }
      for (int index = 0; index < bytes.length; index++) {
        if (foldAscii(bytes[index]) != foldAscii(string.bytes[index])) {
          return false;
        }
      }
      return true;
    }

    @Override
    public int hashCode() {
      int hash = 1;
      for (byte value : bytes) {
        hash = 31 * hash + Byte.toUnsignedInt(foldAscii(value));
      }
      return hash;
    }

    @Override
    public String toString() {
      return toLiteral();
    }

    private static byte foldAscii(byte value) {
      int unsigned = Byte.toUnsignedInt(value);
      if (unsigned >= 'A' && unsigned <= 'Z') {
        return (byte) (unsigned + ('a' - 'A'));
      }
      return value;
    }
  }

  /** A signed 64-bit MOO object reference. */
  record ObjectValue(long value) implements MooValue {
    @Override
    public Type type() {
      return Type.OBJECT;
    }

    @Override
    public boolean isTruthy() {
      return false;
    }

    @Override
    public String toLiteral() {
      return "#" + value;
    }

    @Override
    public String toString() {
      return toLiteral();
    }
  }

  /** The canonical v4 MOO error values. */
  enum ErrorValue implements MooValue {
    E_NONE(0),
    E_TYPE(1),
    E_DIV(2),
    E_PERM(3),
    E_PROPNF(4),
    E_VERBNF(5),
    E_VARNF(6),
    E_INVIND(7),
    E_RECMOVE(8),
    E_MAXREC(9),
    E_RANGE(10),
    E_ARGS(11),
    E_NACC(12),
    E_INVARG(13),
    E_QUOTA(14),
    E_FLOAT(15),
    E_FILE(16),
    E_EXEC(17);

    private final int code;

    ErrorValue(int code) {
      this.code = code;
    }

    /** Returns the database error code. */
    public int code() {
      return code;
    }

    /** Returns the canonical error for {@code code}, if the code is valid in v4. */
    public static Optional<ErrorValue> fromCode(long code) {
      for (ErrorValue error : values()) {
        if (error.code == code) {
          return Optional.of(error);
        }
      }
      return Optional.empty();
    }

    @Override
    public Type type() {
      return Type.ERROR;
    }

    @Override
    public boolean isTruthy() {
      return false;
    }

    @Override
    public String toLiteral() {
      return name();
    }
  }

  /** An immutable heterogeneous MOO list with one-based access. */
  final class ListValue implements MooValue {
    private final List<MooValue> elements;

    /** Creates a list by taking an immutable snapshot of {@code elements}. */
    public ListValue(List<? extends MooValue> elements) {
      this.elements = List.copyOf(elements);
    }

    /** Returns the immutable element snapshot. */
    public List<MooValue> elements() {
      return elements;
    }

    /** Returns the number of elements. */
    public int size() {
      return elements.size();
    }

    /** Returns the one-based element, or empty when the index is outside the list. */
    public Optional<MooValue> get(long oneBasedIndex) {
      if (oneBasedIndex < 1 || oneBasedIndex > elements.size()) {
        return Optional.empty();
      }
      return Optional.of(elements.get((int) oneBasedIndex - 1));
    }

    @Override
    public Type type() {
      return Type.LIST;
    }

    @Override
    public boolean isTruthy() {
      return !elements.isEmpty();
    }

    @Override
    public String toLiteral() {
      StringBuilder literal = new StringBuilder("{");
      for (int index = 0; index < elements.size(); index++) {
        if (index != 0) {
          literal.append(", ");
        }
        literal.append(elements.get(index).toLiteral());
      }
      return literal.append('}').toString();
    }

    @Override
    public boolean equals(Object other) {
      return this == other || (other instanceof ListValue list && elements.equals(list.elements));
    }

    @Override
    public int hashCode() {
      return elements.hashCode();
    }

    @Override
    public String toString() {
      return toLiteral();
    }
  }
}
