package moo.builtin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.StringValue;
import org.jspecify.annotations.Nullable;

/** Toast-compatible PCRE surface backed by Java's bounded regular-expression engine. */
final class PcreService {
  private static final int MAX_CACHE_ENTRIES = 256;
  private final LinkedHashMap<CacheKey, CacheEntry> cache = new LinkedHashMap<>();

  synchronized BuiltinResult match(List<MooValue> arguments) {
    String subject = ((StringValue) arguments.get(0)).text();
    String source = ((StringValue) arguments.get(1)).text();
    if (source.isEmpty()) {
      return invalidArgument();
    }
    boolean caseMatters = arguments.size() >= 3 && arguments.get(2).isTruthy();
    boolean findAll = arguments.size() < 4 || ((IntegerValue) arguments.get(3)).value() != 0;
    NamedPattern named = translateNamedGroups(source);
    Pattern pattern;
    try {
      pattern = cached(named.source, caseMatters);
    } catch (PatternSyntaxException failure) {
      return invalidArgument();
    }

    Matcher matcher = pattern.matcher(subject);
    List<MooValue> matches = new ArrayList<>();
    while (matcher.find()) {
      Map<MooValue, MooValue> groups = new LinkedHashMap<>();
      for (int group = 0; group <= matcher.groupCount(); group++) {
        if (matcher.start(group) < 0) {
          continue;
        }
        String key = named.names.getOrDefault(group, Integer.toString(group));
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
    }
    return BuiltinResult.value(new ListValue(matches));
  }

  synchronized BuiltinResult replace(List<MooValue> arguments) {
    String subject = ((StringValue) arguments.get(0)).text();
    Substitution substitution =
        Substitution.parse(((StringValue) arguments.get(1)).text());
    if (substitution == null) {
      return invalidArgument();
    }
    NamedPattern named = translateNamedGroups(substitution.pattern);
    final Pattern pattern;
    try {
      pattern =
          Pattern.compile(
              named.source,
              substitution.insensitive
                  ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                  : 0);
    } catch (PatternSyntaxException failure) {
      return invalidArgument();
    }
    String replacement = substitution.replacement.replace("$&", "$0");
    Matcher matcher = pattern.matcher(subject);
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

  synchronized BuiltinResult cacheStats() {
    List<MooValue> entries = new ArrayList<>();
    for (Map.Entry<CacheKey, CacheEntry> entry : cache.entrySet()) {
      entries.add(
          new ListValue(
              List.of(StringValue.of(entry.getKey().pattern), new IntegerValue(entry.getValue().hits))));
    }
    return BuiltinResult.value(new ListValue(entries));
  }

  private Pattern cached(String source, boolean caseMatters) {
    CacheKey key = new CacheKey(source, caseMatters);
    CacheEntry entry = cache.get(key);
    if (entry != null) {
      entry.hits++;
      return entry.pattern;
    }
    Pattern compiled =
        Pattern.compile(
            source, caseMatters ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    if (cache.size() >= MAX_CACHE_ENTRIES) {
      CacheKey first = cache.keySet().iterator().next();
      cache.remove(first);
    }
    cache.put(key, new CacheEntry(compiled));
    return compiled;
  }

  private static NamedPattern translateNamedGroups(String source) {
    StringBuilder translated = new StringBuilder(source.length());
    Map<Integer, String> names = new LinkedHashMap<>();
    boolean escaped = false;
    int group = 0;
    for (int index = 0; index < source.length(); index++) {
      char current = source.charAt(index);
      if (escaped) {
        translated.append(current);
        escaped = false;
        continue;
      }
      if (current == '\\') {
        translated.append(current);
        escaped = true;
        continue;
      }
      if (current != '(') {
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

  private static BuiltinResult invalidArgument() {
    return BuiltinResult.error(ErrorValue.E_INVARG);
  }

  private record CacheKey(String pattern, boolean caseMatters) {}

  private static final class CacheEntry {
    private long hits;
    private final Pattern pattern;

    private CacheEntry(Pattern pattern) {
      this.pattern = pattern;
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
