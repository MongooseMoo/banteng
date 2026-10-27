package world.mongoose.banteng.persistence;

import org.jspecify.annotations.Nullable;
import world.mongoose.banteng.world.SuspendedStackSlot.Kind;

/** Persisted Toast value tags and activation compatibility markers. */
final class V17Format {
  static final long ACTIVATION_PLACEHOLDER = -111;
  static final int LEGACY_DOBJ = -7;
  static final int LEGACY_IOBJ = -8;
  static final int LEGACY_PREPOSITION = -9;
  static final int LEGACY_VERB = -10;

  private V17Format() {}

  static Kind stackKind(int tag) {
    if (tag == -1) { return Kind.VALUE; }
    if (tag == ValueTag.NONE.code()) { return Kind.NONE; }
    if (tag == ValueTag.CATCH.code()) { return Kind.CATCH; }
    if (tag == ValueTag.FINALLY.code()) { return Kind.FINALLY; }
    throw new IllegalArgumentException("unsupported suspended stack tag: " + tag);
  }

  static int stackTag(Kind kind) {
    return switch (kind) {
      case VALUE -> -1;
      case NONE -> ValueTag.NONE.code();
      case CATCH -> ValueTag.CATCH.code();
      case FINALLY -> ValueTag.FINALLY.code();
    };
  }

  enum ValueTag {
    INTEGER(0), OBJECT(1), STRING(2), ERROR(3), LIST(4), CLEAR(5), NONE(6),
    CATCH(7), FINALLY(8), FLOAT(9), MAP(10), ITERATOR(11), ANONYMOUS(12),
    WAIF(13), BOOLEAN(14);

    private final int code;

    ValueTag(int code) {
      this.code = code;
    }

    int code() {
      return code;
    }

    static @Nullable ValueTag fromCode(int code) {
      return switch (code) {
        case 0 -> INTEGER;
        case 1 -> OBJECT;
        case 2 -> STRING;
        case 3 -> ERROR;
        case 4 -> LIST;
        case 5 -> CLEAR;
        case 6 -> NONE;
        case 7 -> CATCH;
        case 8 -> FINALLY;
        case 9 -> FLOAT;
        case 10 -> MAP;
        case 11 -> ITERATOR;
        case 12 -> ANONYMOUS;
        case 13 -> WAIF;
        case 14 -> BOOLEAN;
        default -> null;
      };
    }
  }
}
