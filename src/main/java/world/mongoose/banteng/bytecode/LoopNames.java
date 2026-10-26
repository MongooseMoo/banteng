package world.mongoose.banteng.bytecode;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/** Case folding and innermost-first lookup for compiler loop names. */
final class LoopNames {
  private LoopNames() {}

  static List<String> normalize(List<String> names) {
    return names.stream().map(name -> name.toLowerCase(Locale.ROOT)).toList();
  }

  static <T> int find(
      List<T> loops, Function<T, List<String>> names, Optional<String> requested) {
    if (requested.isEmpty()) {
      return loops.size() - 1;
    }
    String name = requested.orElseThrow().toLowerCase(Locale.ROOT);
    for (int index = loops.size() - 1; index >= 0; index--) {
      if (names.apply(loops.get(index)).contains(name)) {
        return index;
      }
    }
    throw new IllegalArgumentException("unknown loop variable: " + requested.orElseThrow());
  }
}
