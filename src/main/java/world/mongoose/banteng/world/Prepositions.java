package world.mongoose.banteng.world;

import java.util.List;
import java.util.Locale;

/** Persistent preposition codes, in ToastStunt db_verbs.cc order. Never reorder. */
public final class Prepositions {
  private Prepositions() {}

  public static final List<List<String>> ALIASES = List.of(
      List.of("with", "using"), List.of("at", "to"), List.of("in front of"),
      List.of("in", "inside", "into"), List.of("on top of", "on", "onto", "upon"),
      List.of("out of", "from inside", "from"), List.of("over"), List.of("through"),
      List.of("under", "underneath", "beneath"), List.of("behind"), List.of("beside"),
      List.of("for", "about"), List.of("is"), List.of("as"), List.of("off", "off of"));

  public static String format(int code) {
    return code == -1 ? "none" : code == -2 ? "any" : String.join("/", ALIASES.get(code));
  }

  public static int parse(String text) {
    String normalized = text.toLowerCase(Locale.ROOT);
    int slash = normalized.indexOf('/');
    if (slash >= 0) {
      normalized = normalized.substring(0, slash);
    }
    if (normalized.equals("none")) {
      return -1;
    }
    if (normalized.equals("any")) {
      return -2;
    }
    for (int code = 0; code < ALIASES.size(); code++) {
      if (ALIASES.get(code).contains(normalized)) {
        return code;
      }
    }
    String numeric = normalized.startsWith("#") ? normalized.substring(1) : normalized;
    if (numeric.isEmpty()
        || numeric.chars().anyMatch(character -> character < '0' || character > '9')) {
      return Integer.MIN_VALUE;
    }
    try {
      int code = Integer.parseInt(numeric);
      return code >= 0 && code < ALIASES.size() ? code : Integer.MIN_VALUE;
    } catch (NumberFormatException ignored) {
      return Integer.MIN_VALUE;
    }
  }
}

