package world.mongoose.banteng.value;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** The closed value family required by the first server slice. */
public sealed interface MooValue
    permits MooValue.IntegerValue,
        MooValue.BooleanValue,
        MooValue.FloatValue,
        MooValue.StringValue,
        MooValue.ObjectValue,
        MooValue.AnonymousObjectValue,
        MooValue.WaifValue,
        MooValue.ErrorValue,
        MooValue.ListValue,
        MooValue.MapValue {

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
    FLOAT(9),
    MAP(10),
    ANONYMOUS(12),
    WAIF(13),
    BOOLEAN(14);

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

  /** A distinct Toast boolean value with integer-compatible equality at the VM boundary. */
  enum BooleanValue implements MooValue {
    FALSE(false),
    TRUE(true);

    private final boolean value;

    BooleanValue(boolean value) {
      this.value = value;
    }

    /** Returns the canonical value for {@code value}. */
    public static BooleanValue of(boolean value) {
      return value ? TRUE : FALSE;
    }

    /** Returns the primitive truth payload. */
    public boolean value() {
      return value;
    }

    @Override
    public Type type() {
      return Type.BOOLEAN;
    }

    @Override
    public boolean isTruthy() {
      return value;
    }

    @Override
    public String toLiteral() {
      return value ? "true" : "false";
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
      String formatted = String.format(Locale.ROOT, "%.15g", new BigDecimal(value));
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
    private static final Charset CHARSET = StandardCharsets.ISO_8859_1;
    private final byte[] bytes;

    /** Creates a string by taking a defensive copy of {@code bytes}. */
    private StringValue(byte[] bytes) {
      this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    /** Creates a binary string by taking a defensive copy of {@code bytes}. */
    public static StringValue of(byte[] bytes) {
      return new StringValue(bytes);
    }

    /** Encodes {@code text} as one Latin-1 byte per character. */
    public static StringValue of(String text) {
      return new StringValue(text.getBytes(CHARSET));
    }

    /** Returns the one encoding used by MOO strings and their external streams. */
    public static Charset charset() {
      return CHARSET;
    }

    /** Returns a defensive copy of the binary string contents. */
    public byte[] bytes() {
      return Arrays.copyOf(bytes, bytes.length);
    }

    /** Decodes the binary string as Latin-1 text without losing any byte values. */
    public String text() {
      return new String(bytes, CHARSET);
    }

    /** Returns the byte length of this string. */
    public int length() {
      return bytes.length;
    }

    /** Compares two strings by their case-folded unsigned byte contents. */
    public int compareIgnoringCase(StringValue other) {
      int commonLength = Math.min(bytes.length, other.bytes.length);
      for (int index = 0; index < commonLength; index++) {
        int comparison =
            Integer.compare(
                Byte.toUnsignedInt(foldAscii(bytes[index])),
                Byte.toUnsignedInt(foldAscii(other.bytes[index])));
        if (comparison != 0) {
          return comparison;
        }
      }
      return Integer.compare(bytes.length, other.bytes.length);
    }

    /** Compares two strings by their exact unsigned byte contents. */
    public int compareCaseSensitively(StringValue other) {
      int commonLength = Math.min(bytes.length, other.bytes.length);
      for (int index = 0; index < commonLength; index++) {
        int comparison =
            Integer.compare(Byte.toUnsignedInt(bytes[index]), Byte.toUnsignedInt(other.bytes[index]));
        if (comparison != 0) {
          return comparison;
        }
      }
      return Integer.compare(bytes.length, other.bytes.length);
    }

    /** Returns the zero-based case-insensitive byte position of {@code needle}, or -1. */
    public int indexOfIgnoringCase(StringValue needle) {
      if (needle.bytes.length == 0) {
        return 0;
      }
      int lastStart = bytes.length - needle.bytes.length;
      for (int start = 0; start <= lastStart; start++) {
        int offset = 0;
        while (offset < needle.bytes.length
            && foldAscii(bytes[start + offset]) == foldAscii(needle.bytes[offset])) {
          offset++;
        }
        if (offset == needle.bytes.length) {
          return start;
        }
      }
      return -1;
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
      if (!(other instanceof StringValue string)) {
        return false;
      }
      Optional<String> decoded = decodeValidUtf8(bytes);
      Optional<String> otherDecoded = decodeValidUtf8(string.bytes);
      if (decoded.isPresent() && otherDecoded.isPresent()) {
        return foldUnicode(decoded.orElseThrow()).equals(foldUnicode(otherDecoded.orElseThrow()));
      }
      if (bytes.length != string.bytes.length) {
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
      Optional<String> decoded = decodeValidUtf8(bytes);
      if (decoded.isPresent()) {
        return foldUnicode(decoded.orElseThrow()).hashCode();
      }
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

    private static Optional<String> decodeValidUtf8(byte[] value) {
      try {
        return Optional.of(
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value))
                .toString());
      } catch (CharacterCodingException error) {
        return Optional.empty();
      }
    }

    private static String foldUnicode(String value) {
      StringBuilder folded = new StringBuilder(value.length());
      value
          .codePoints()
          .map(codePoint -> Character.toLowerCase(Character.toUpperCase(codePoint)))
          .forEach(folded::appendCodePoint);
      return folded.toString();
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

  /** An anonymous MOO object whose runtime identity is its Java reference. */
  final class AnonymousObjectValue implements MooValue {
    @Override
    public Type type() {
      return Type.ANONYMOUS;
    }

    @Override
    public boolean isTruthy() {
      return false;
    }

    @Override
    public String toLiteral() {
      return "*anonymous*";
    }

    @Override
    public String toString() {
      return toLiteral();
    }
  }

  /** A MOO WAIF with identity semantics and immutable class and owner references. */
  final class WaifValue implements MooValue {
    private final ObjectValue classObject;
    private final ObjectValue owner;

    public WaifValue(ObjectValue classObject, ObjectValue owner) {
      this.classObject = classObject;
      this.owner = owner;
    }

    /** Returns this WAIF's class object. */
    public ObjectValue classObject() {
      return classObject;
    }

    /** Returns this WAIF's owner. */
    public ObjectValue owner() {
      return owner;
    }

    @Override
    public Type type() {
      return Type.WAIF;
    }

    @Override
    public boolean isTruthy() {
      return false;
    }

    @Override
    public String toLiteral() {
      return "[[class = " + classObject + ", owner = " + owner + "]]";
    }

    @Override
    public String toString() {
      return "[[waif]]";
    }
  }

  /** The supported Toast MOO error values. */
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
    E_EXEC(17),
    E_INTRPT(18);

    private final int code;

    ErrorValue(int code) {
      this.code = code;
    }

    /** Returns the database error code. */
    public int code() {
      return code;
    }

    /** Returns Toast's user-facing description for this error. */
    public String description() {
      return switch (this) {
        case E_NONE -> "No error";
        case E_TYPE -> "Type mismatch";
        case E_DIV -> "Division by zero";
        case E_PERM -> "Permission denied";
        case E_PROPNF -> "Property not found";
        case E_VERBNF -> "Verb not found";
        case E_VARNF -> "Variable not found";
        case E_INVIND -> "Invalid indirection";
        case E_RECMOVE -> "Recursive move";
        case E_MAXREC -> "Too many verb calls";
        case E_RANGE -> "Range error";
        case E_ARGS -> "Incorrect number of arguments";
        case E_NACC -> "Move refused by destination";
        case E_INVARG -> "Invalid argument";
        case E_QUOTA -> "Resource limit exceeded";
        case E_FLOAT -> "Floating-point arithmetic error";
        case E_FILE -> "File error";
        case E_EXEC -> "Exec error";
        case E_INTRPT -> "Interrupted";
      };
    }

    /** Returns the supported error for {@code code}, if present. */
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

    /** Returns this list followed by every element of {@code other}. */
    public ListValue concatenate(ListValue other) {
      List<MooValue> concatenated = new ArrayList<>(elements.size() + other.elements.size());
      concatenated.addAll(elements);
      concatenated.addAll(other.elements);
      return new ListValue(concatenated);
    }

    /** Returns this list with {@code value} appended. */
    public ListValue append(MooValue value) {
      List<MooValue> appended = new ArrayList<>(elements.size() + 1);
      appended.addAll(elements);
      appended.add(value);
      return new ListValue(appended);
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

  /** An immutable MOO map whose entry traversal follows Toast map insertion topology. */
  final class MapValue implements MooValue {
    private final Map<MooValue, MooValue> entries;
    private final @Nullable MapNode lookupRoot;

    /** Creates a map by taking an immutable insertion-preserving snapshot of {@code entries}. */
    public MapValue(Map<? extends MooValue, ? extends MooValue> entries) {
      LinkedHashMap<MooValue, MooValue> owned = new LinkedHashMap<>();
      MapNode root = null;
      for (Map.Entry<? extends MooValue, ? extends MooValue> entry : entries.entrySet()) {
        requireScalarKey(entry.getKey());
        owned.put(entry.getKey(), entry.getValue());
        root = insertLookupNode(root, entry.getKey(), entry.getValue());
      }
      this.entries = Collections.unmodifiableMap(owned);
      this.lookupRoot = root;
    }

    private MapValue(Map<MooValue, MooValue> entries, @Nullable MapNode lookupRoot) {
      this.entries = Collections.unmodifiableMap(entries);
      this.lookupRoot = lookupRoot;
    }

    /** Returns the immutable insertion-preserving entry snapshot. */
    public Map<MooValue, MooValue> entries() {
      return entries;
    }

    /** Returns the number of entries. */
    public int size() {
      return entries.size();
    }

    /** Returns the value for an equal scalar key, if present. */
    public Optional<MooValue> get(MooValue key) {
      return get(key, false);
    }

    /** Returns the value for an equal scalar key using the requested string-case rule. */
    public Optional<MooValue> get(MooValue key, boolean caseMatters) {
      requireScalarKey(key);
      if (key instanceof AnonymousObjectValue || key instanceof WaifValue) {
        for (Map.Entry<MooValue, MooValue> entry : entries.entrySet()) {
          if (sameIdentityKey(entry.getKey(), key)) {
            return Optional.of(entry.getValue());
          }
        }
        return Optional.empty();
      }
      MapNode current = lookupRoot;
      while (current != null) {
        int comparison = lookupComparison(current.key, key, caseMatters);
        if (comparison == 0) {
          return Optional.of(current.value);
        }
        if (caseMatters) {
          comparison = compareKeys(current.key, key);
        }
        current = comparison < 0 ? current.right : current.left;
      }
      return Optional.empty();
    }

    private static boolean sameIdentityKey(MooValue stored, MooValue requested) {
      return switch (requested) {
        case AnonymousObjectValue anonymous ->
            stored instanceof AnonymousObjectValue storedAnonymous
                && storedAnonymous == anonymous;
        case WaifValue waif -> stored instanceof WaifValue storedWaif && storedWaif == waif;
        default -> false;
      };
    }

    /** Returns an immutable map with {@code key} bound to {@code value}. */
    public MapValue with(MooValue key, MooValue value) {
      requireScalarKey(key);
      LinkedHashMap<MooValue, MooValue> replacement = new LinkedHashMap<>();
      boolean inserted = false;
      boolean replacedExisting = false;
      for (Map.Entry<MooValue, MooValue> entry : entries.entrySet()) {
        int comparison = compareKeys(entry.getKey(), key);
        if (!inserted && comparison >= 0) {
          replacement.put(key, value);
          inserted = true;
        }
        if (comparison != 0) {
          replacement.put(entry.getKey(), entry.getValue());
        } else {
          replacedExisting = true;
        }
      }
      if (!inserted) {
        replacement.put(key, value);
      }
      MapNode root;
      if (replacedExisting) {
        root = null;
        for (Map.Entry<MooValue, MooValue> entry : replacement.entrySet()) {
          root = insertLookupNode(root, entry.getKey(), entry.getValue());
        }
      } else {
        root = insertLookupNode(copyLookupTree(lookupRoot), key, value);
      }
      return new MapValue(replacement, root);
    }

    @Override
    public Type type() {
      return Type.MAP;
    }

    @Override
    public boolean isTruthy() {
      return !entries.isEmpty();
    }

    @Override
    public String toLiteral() {
      StringBuilder literal = new StringBuilder("[");
      int index = 0;
      for (Map.Entry<MooValue, MooValue> entry : entries.entrySet()) {
        if (index++ != 0) {
          literal.append(", ");
        }
        literal
            .append(entry.getKey().toLiteral())
            .append(" -> ")
            .append(entry.getValue().toLiteral());
      }
      return literal.append(']').toString();
    }

    @Override
    public boolean equals(Object other) {
      return this == other || (other instanceof MapValue map && entries.equals(map.entries));
    }

    @Override
    public int hashCode() {
      return entries.hashCode();
    }

    @Override
    public String toString() {
      return toLiteral();
    }

    private static void requireScalarKey(MooValue key) {
      if (key instanceof ListValue || key instanceof MapValue) {
        throw new IllegalArgumentException("MOO map keys must be scalar");
      }
    }

    private static int lookupComparison(MooValue stored, MooValue requested, boolean caseMatters) {
      if (caseMatters
          && stored instanceof StringValue storedString
          && requested instanceof StringValue requestedString) {
        return storedString.compareCaseSensitively(requestedString);
      }
      return compareKeys(stored, requested);
    }

    private static @Nullable MapNode copyLookupTree(@Nullable MapNode node) {
      if (node == null) {
        return null;
      }
      MapNode copy = new MapNode(node.key, node.value);
      copy.red = node.red;
      copy.left = copyLookupTree(node.left);
      copy.right = copyLookupTree(node.right);
      return copy;
    }

    private static MapNode insertLookupNode(
        @Nullable MapNode root, MooValue key, MooValue value) {
      if (root == null) {
        MapNode inserted = new MapNode(key, value);
        inserted.red = false;
        return inserted;
      }

      MapNode head = new MapNode(key, value);
      @Nullable MapNode grandparent = null;
      @Nullable MapNode parent = null;
      @Nullable MapNode iterator = root;
      MapNode greatGrandparent = head;
      head.right = root;
      int direction = 0;
      int lastDirection = 0;

      while (true) {
        if (iterator == null) {
          iterator = new MapNode(key, value);
          Objects.requireNonNull(parent).setLink(direction, iterator);
        } else if (isRed(iterator.left) && isRed(iterator.right)) {
          iterator.red = true;
          Objects.requireNonNull(iterator.left).red = false;
          Objects.requireNonNull(iterator.right).red = false;
        }

        if (isRed(iterator) && isRed(parent)) {
          int grandparentDirection = greatGrandparent.right == grandparent ? 1 : 0;
          MapNode nonNullParent = Objects.requireNonNull(parent);
          MapNode nonNullGrandparent = Objects.requireNonNull(grandparent);
          if (iterator == nonNullParent.link(lastDirection)) {
            greatGrandparent.setLink(
                grandparentDirection, rotateSingle(nonNullGrandparent, 1 - lastDirection));
          } else {
            greatGrandparent.setLink(
                grandparentDirection, rotateDouble(nonNullGrandparent, 1 - lastDirection));
          }
        }

        int comparison = compareKeys(iterator.key, key);
        if (comparison == 0) {
          iterator.value = value;
          break;
        }

        lastDirection = direction;
        direction = comparison < 0 ? 1 : 0;
        if (grandparent != null) {
          greatGrandparent = grandparent;
        }
        grandparent = parent;
        parent = iterator;
        iterator = iterator.link(direction);
      }

      root = head.right;
      root.red = false;
      return root;
    }

    private static boolean isRed(@Nullable MapNode node) {
      return node != null && node.red;
    }

    private static MapNode rotateSingle(MapNode root, int direction) {
      MapNode saved = Objects.requireNonNull(root.link(1 - direction));
      root.setLink(1 - direction, saved.link(direction));
      saved.setLink(direction, root);
      root.red = true;
      saved.red = false;
      return saved;
    }

    private static MapNode rotateDouble(MapNode root, int direction) {
      root.setLink(
          1 - direction,
          rotateSingle(Objects.requireNonNull(root.link(1 - direction)), 1 - direction));
      return rotateSingle(root, direction);
    }

    private static final class MapNode {
      private final MooValue key;
      private MooValue value;
      private boolean red = true;
      private @Nullable MapNode left;
      private @Nullable MapNode right;

      private MapNode(MooValue key, MooValue value) {
        this.key = key;
        this.value = value;
      }

      private @Nullable MapNode link(int direction) {
        return direction == 0 ? left : right;
      }

      private void setLink(int direction, @Nullable MapNode node) {
        if (direction == 0) {
          left = node;
        } else {
          right = node;
        }
      }
    }

    /** Compares two scalar keys using the map lookup and replacement relation. */
    public static int compareKeys(MooValue left, MooValue right) {
      int leftRank =
          switch (left.type()) {
            case INTEGER -> 0;
            case OBJECT -> 1;
            case ERROR -> 2;
            case FLOAT -> 3;
            case BOOLEAN -> 4;
            case STRING -> 5;
            case ANONYMOUS -> 6;
            case WAIF -> 7;
            case LIST, MAP -> throw new IllegalArgumentException("collection map key");
          };
      int rightRank =
          switch (right.type()) {
            case INTEGER -> 0;
            case OBJECT -> 1;
            case ERROR -> 2;
            case FLOAT -> 3;
            case BOOLEAN -> 4;
            case STRING -> 5;
            case ANONYMOUS -> 6;
            case WAIF -> 7;
            case LIST, MAP -> throw new IllegalArgumentException("collection map key");
          };
      if (leftRank != rightRank) {
        return Integer.compare(leftRank, rightRank);
      }
      return switch (left) {
        case IntegerValue integer ->
            (int) (integer.value() - ((IntegerValue) right).value());
        case ObjectValue object -> (int) (object.value() - ((ObjectValue) right).value());
        case ErrorValue error -> Integer.compare(error.code(), ((ErrorValue) right).code());
        case FloatValue floating -> {
          double leftValue = floating.value();
          double rightValue = ((FloatValue) right).value();
          yield leftValue == rightValue ? 0 : (leftValue - rightValue < 0.0 ? -1 : 1);
        }
        case BooleanValue bool -> bool == right ? 0 : 1;
        case StringValue string -> string.compareIgnoringCase((StringValue) right);
        case AnonymousObjectValue anonymous -> anonymous == right ? 0 : 1;
        case WaifValue waif -> waif == right ? 0 : 1;
        case ListValue ignored -> throw new IllegalArgumentException("list map key");
        case MapValue ignored -> throw new IllegalArgumentException("map map key");
      };
    }
  }
}
