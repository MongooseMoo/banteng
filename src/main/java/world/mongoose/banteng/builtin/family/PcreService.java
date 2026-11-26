package world.mongoose.banteng.builtin.family;

import world.mongoose.banteng.builtin.BuiltinCall;
import world.mongoose.banteng.builtin.BuiltinResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.WorldTxn;
import org.jspecify.annotations.Nullable;

/** Toast-compatible PCRE surface backed by Java's bounded regular-expression engine. */
final class PcreService {
  private static final int MAX_CACHE_ENTRIES = 20;
  private static final int MAX_REPLACEMENT_REFERENCES = 32;
  private static final int MAX_QUANTIFIER_COUNT = 65_535;
  private static final Pattern COUNTED_QUANTIFIER = Pattern.compile("\\{([0-9]+)(?:,([0-9]*))?\\}");
  private final LinkedHashMap<CacheKey, CacheEntry> cache = new LinkedHashMap<>();

  synchronized BuiltinResult match(BuiltinCall call) {
    List<MooValue> arguments = call.arguments();
    String subject = ((StringValue) arguments.get(0)).text();
    String source = ((StringValue) arguments.get(1)).text();
    if (source.isEmpty()) {
      return invalidArgument();
    }
    boolean caseMatters = arguments.size() >= 3 && arguments.get(2).isTruthy();
    boolean findAll = arguments.size() < 4 || arguments.get(3).isTruthy();
    CacheEntry cachedPattern;
    try {
      cachedPattern = cached(source, caseMatters);
    } catch (PatternSyntaxException failure) {
      return invalidArgument();
    }

    Matcher matcher = cachedPattern.pattern.matcher(subject);
    List<MooValue> matches = new ArrayList<>();
    int limit = matchIterationLimit(call.world());
    int offset = 0;
    for (int iteration = 1; offset < subject.length() && matcher.find(offset); iteration++) {
      if (iteration >= limit) {
        return BuiltinResult.raised(ErrorValue.E_MAXREC,
            StringValue.of("Too many iterations of matching loop: " + iteration), new IntegerValue(0));
      }
      Map<MooValue, MooValue> groups = new LinkedHashMap<>();
      for (int group = 0; group <= matcher.groupCount(); group++) {
        if (matcher.start(group) < 0) {
          continue;
        }
        String key = cachedPattern.names.getOrDefault(group, Integer.toString(group));
        Map<MooValue, MooValue> detail = new LinkedHashMap<>();
        detail.put(
            StringValue.of("position"),
            new ListValue(
                List.of(
                    new IntegerValue(matcher.start(group) + 1),
                    new IntegerValue(matcher.end(group)))));
        detail.put(StringValue.of("match"), StringValue.of(matcher.group(group)));
        groups.put(StringValue.of(key), new MapValue(detail));
      }
      matches.add(new MapValue(groups));
      if (!findAll) {
        break;
      }
      offset = matcher.end();
    }
    return BuiltinResult.value(new ListValue(matches));
  }

  private static int matchIterationLimit(WorldTxn world) {
    if (world.readObjectProperty(0, "server_options").orElse(null) instanceof ObjectValue options
        && world.readObjectProperty(options.value(), "pcre_match_max_iterations").orElse(null)
            instanceof IntegerValue configured) {
      long limit = Integer.toUnsignedLong((int) configured.value());
      return (int) Math.clamp(limit, 100L, 100_000_000L);
    }
    return 1_000;
  }

  synchronized BuiltinResult replace(List<MooValue> arguments) {
    String subject = ((StringValue) arguments.get(0)).text();
    Substitution substitution =
        Substitution.parse(((StringValue) arguments.get(1)).text());
    if (substitution == null) {
      return invalidArgument();
    }
    final Pattern pattern;
    try {
      NamedPattern named = translatePattern(substitution.pattern);
      pattern =
          Pattern.compile(
              named.source,
              substitution.insensitive
                  ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                  : 0);
    } catch (PatternSyntaxException failure) {
      return invalidArgument();
    }
    Matcher matcher = pattern.matcher(subject);
    String replacement = translateReplacement(substitution.replacement, matcher.groupCount());
    if (replacement == null) {
      return invalidArgument();
    }
    final String replaced;
    try {
      replaced =
          substitution.global
              ? matcher.replaceAll(replacement)
              : matcher.replaceFirst(replacement);
    } catch (IllegalArgumentException failure) {
      return invalidArgument();
    }
    return BuiltinResult.value(StringValue.of(replaced));
  }

  private static @Nullable String translateReplacement(String source, int groupCount) {
    StringBuilder translated = new StringBuilder(source.length());
    int references = 0;
    for (int index = 0; index < source.length(); index++) {
      char current = source.charAt(index);
      if (current == '\\' && index + 1 < source.length()) {
        translated.append('\\').append(source.charAt(++index));
        continue;
      }
      if (current == '$' && index + 1 < source.length()) {
        char next = source.charAt(index + 1);
        long group = switch (next) {
          case '&' -> 0;
          case '+' -> groupCount;
          default -> -1;
        };
        if (isDecimalDigit(next)) {
          group = 0;
          while (index + 1 < source.length() && isDecimalDigit(source.charAt(index + 1))) {
            group = 10 * group + source.charAt(++index) - '0';
            if (group > groupCount) return null;
          }
        } else if (group >= 0) {
          index++;
        }
        if (group >= 0) {
          if (++references > MAX_REPLACEMENT_REFERENCES) return null;
          translated.append('$').append(group);
          continue;
        }
      }
      // Quoting digits also keeps them from extending a preceding group number.
      if (current == '$' || current == '\\' || isDecimalDigit(current)) {
        translated.append('\\');
      }
      translated.append(current);
    }
    return translated.toString();
  }

  private static boolean isDecimalDigit(char value) {
    return value >= '0' && value <= '9';
  }

  synchronized BuiltinResult cacheStats() {
    List<MooValue> entries = new ArrayList<>();
    for (Map.Entry<CacheKey, CacheEntry> entry : cache.entrySet()) {
      entries.add(
          new ListValue(
              List.of(StringValue.of(entry.getKey().pattern), new IntegerValue(entry.getValue().hits))));
    }
    return BuiltinResult.value(new ListValue(entries));
  }

  private CacheEntry cached(String source, boolean caseMatters) {
    CacheKey key = new CacheKey(source, caseMatters);
    CacheEntry entry = cache.get(key);
    if (entry != null) {
      entry.hits++;
      return entry;
    }
    if (cache.size() >= MAX_CACHE_ENTRIES) {
      var leastUsed = cache.entrySet().stream()
          .min(Comparator.comparingLong(candidate -> candidate.getValue().hits)).orElseThrow();
      cache.remove(leastUsed.getKey());
    }
    NamedPattern named = translatePattern(source);
    Pattern compiled =
        Pattern.compile(
            named.source, caseMatters ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    CacheEntry added = new CacheEntry(compiled, named.names);
    cache.put(key, added);
    return added;
  }

  private static NamedPattern translatePattern(String source) {
    StringBuilder translated = new StringBuilder(source.length());
    Map<Integer, String> names = new LinkedHashMap<>();
    Matcher quantifier = COUNTED_QUANTIFIER.matcher(source);
    boolean quoted = false;
    boolean inClass = false;
    int group = 0;
    for (int index = 0; index < source.length(); index++) {
      char current = source.charAt(index);
      if (source.startsWith("\\E", index) && quoted) {
        translated.append("\\E");
        quoted = false;
        index++;
        continue;
      }
      if (quoted) {
        translated.append(current);
        continue;
      }
      if (current == '\\' && index + 1 < source.length()) {
        char escape = source.charAt(index + 1);
        if ((escape == 'x' || escape == 'p' || escape == 'P') && source.startsWith("{", index + 2)) {
          int end = source.indexOf('}', index + 3);
          if (end < 0) throw new PatternSyntaxException("Unclosed escape", source, index);
          translated.append(source, index, end + 1);
          index = end;
          continue;
        }
        translated.append(current).append(escape);
        quoted = escape == 'Q';
        index++;
        continue;
      }
      if (current == '[') inClass = true;
      if (current == ']') inClass = false;
      if (!inClass && current == '{') {
        if (quantifier.region(index, source.length()).lookingAt()) {
          checkQuantifierCount(source, quantifier, 1);
          checkQuantifierCount(source, quantifier, 2);
          translated.append(source, index, quantifier.end());
          index = quantifier.end() - 1;
        } else {
          translated.append("\\{");
        }
        continue;
      }
      if (inClass || current != '(') {
        translated.append(current);
        continue;
      }
      if (source.startsWith("(?P<", index)) {
        int end = source.indexOf('>', index + 4);
        if (end > index + 4) {
          group++;
          names.put(group, source.substring(index + 4, end));
          translated.append("(?<").append(source, index + 4, end + 1);
          index = end;
          continue;
        }
      }
      if (index + 1 >= source.length() || source.charAt(index + 1) != '?') {
        group++;
      }
      translated.append(current);
    }
    return new NamedPattern(translated.toString(), names);
  }

  private static void checkQuantifierCount(String source, Matcher quantifier, int group) {
    int count = 0;
    for (int index = quantifier.start(group); index < quantifier.end(group); index++) {
      count = 10 * count + source.charAt(index) - '0';
      if (count > MAX_QUANTIFIER_COUNT) {
        throw new PatternSyntaxException("Quantifier count exceeds 65535", source, index);
      }
    }
  }

  private static BuiltinResult invalidArgument() {
    return BuiltinResult.error(ErrorValue.E_INVARG);
  }

  private record CacheKey(String pattern, boolean caseMatters) {}

  private static final class CacheEntry {
    private long hits;
    private final Pattern pattern;
    private final Map<Integer, String> names;

    private CacheEntry(Pattern pattern, Map<Integer, String> names) {
      this.pattern = pattern;
      this.names = names;
    }
  }

  private record NamedPattern(String source, Map<Integer, String> names) {}

  private record Substitution(
      String pattern, String replacement, boolean global, boolean insensitive) {
    private static @Nullable Substitution parse(String command) {
      if (command.length() < 4 || command.charAt(0) != 's') {
        return null;
      }
      char delimiter = command.charAt(1);
      int patternEnd = findDelimiter(command, 2, delimiter);
      if (patternEnd < 0) {
        return null;
      }
      int replacementEnd = findDelimiter(command, patternEnd + 1, delimiter);
      if (replacementEnd < 0) {
        return null;
      }
      String flags = command.substring(replacementEnd + 1);
      if (!flags.chars().allMatch(flag -> flag == 'g' || flag == 'i')) {
        return null;
      }
      return new Substitution(
          command.substring(2, patternEnd),
          command.substring(patternEnd + 1, replacementEnd),
          flags.indexOf('g') >= 0,
          flags.indexOf('i') >= 0);
    }

    private static int findDelimiter(String command, int start, char delimiter) {
      boolean escaped = false;
      for (int index = start; index < command.length(); index++) {
        char current = command.charAt(index);
        if (escaped) {
          escaped = false;
        } else if (current == '\\') {
          escaped = true;
        } else if (current == delimiter) {
          return index;
        }
      }
      return -1;
    }
  }
}
