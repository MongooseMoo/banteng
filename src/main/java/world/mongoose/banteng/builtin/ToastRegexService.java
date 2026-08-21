package world.mongoose.banteng.builtin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.StringValue;

/** Bounded byte-oriented implementation of Toast's legacy regular-expression dialect. */
final class ToastRegexService {
  private static final long MAX_CHARACTER_READS = 1_000_000L;
  private static final String WORD = "[A-Za-z0-9]";
  private static final String NOT_WORD = "[^A-Za-z0-9]";
  private static final String WORD_START = "(?<![A-Za-z0-9])(?=[A-Za-z0-9])";
  private static final String WORD_END = "(?<=[A-Za-z0-9])(?![A-Za-z0-9])";
  private static final String WORD_BOUNDARY = "(?:" + WORD_START + "|" + WORD_END + ")";
  private static final String NOT_WORD_BOUNDARY =
      "(?:(?<=[A-Za-z0-9])(?=[A-Za-z0-9])|(?<![A-Za-z0-9])(?![A-Za-z0-9]))";

  BuiltinResult match(
      StringValue subject, StringValue toastPattern, boolean caseMatters, boolean reverse) {
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(toastPattern, "toastPattern");
    String source = new String(toastPattern.bytes(), StandardCharsets.ISO_8859_1);
    Pattern pattern;
    try {
      pattern = Pattern.compile(translate(source), caseMatters ? 0 : Pattern.CASE_INSENSITIVE);
    } catch (IllegalArgumentException failure) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    String bytes = new String(subject.bytes(), StandardCharsets.ISO_8859_1);
    MatchBudget budget = new MatchBudget(MAX_CHARACTER_READS);
    BoundedCharSequence input = new BoundedCharSequence(bytes, 0, bytes.length(), budget);
    try {
      Optional<Matcher> matched = reverse ? lastMatch(pattern, input) : firstMatch(pattern, input);
      return matched.isEmpty()
          ? BuiltinResult.value(new ListValue(List.of()))
          : BuiltinResult.value(result(matched.orElseThrow(), subject));
    } catch (MatchQuotaExceeded exhausted) {
      return BuiltinResult.error(ErrorValue.E_QUOTA);
    }
  }

  private static Optional<Matcher> firstMatch(Pattern pattern, CharSequence input) {
    Matcher matcher = pattern.matcher(input);
    matcher.useAnchoringBounds(false);
    return matcher.find() ? Optional.of(matcher) : Optional.empty();
  }

  private static Optional<Matcher> lastMatch(Pattern pattern, CharSequence input) {
    Matcher matcher = pattern.matcher(input);
    matcher.useAnchoringBounds(false);
    for (int start = input.length(); start >= 0; start--) {
      matcher.reset(input);
      matcher.region(start, input.length());
      matcher.useAnchoringBounds(false);
      if (matcher.lookingAt()) {
        return Optional.of(matcher);
      }
    }
    return Optional.empty();
  }

  private static ListValue result(Matcher matcher, StringValue subject) {
    List<MooValue> captures = new ArrayList<>(9);
    for (int group = 1; group <= 9; group++) {
      int start = group <= matcher.groupCount() ? matcher.start(group) : -1;
      int end = group <= matcher.groupCount() ? matcher.end(group) : -1;
      captures.add(
          new ListValue(
              List.of(
                  new IntegerValue(start < 0 ? 0 : start + 1),
                  new IntegerValue(end < 0 ? -1 : end))));
    }
    return new ListValue(
        List.of(
            new IntegerValue(matcher.start() + 1L),
            new IntegerValue(matcher.end()),
            new ListValue(captures),
            subject));
  }

  private static String translate(String source) {
    StringBuilder translated = new StringBuilder(source.length() * 2);
    boolean inClass = false;
    boolean firstClassMember = false;
    for (int index = 0; index < source.length(); index++) {
      char current = source.charAt(index);
      if (inClass) {
        if (current == ']' && !firstClassMember) {
          translated.append(current);
          inClass = false;
          continue;
        }
        if (current == '^' && firstClassMember) {
          translated.append(current);
          continue;
        }
        if (current == '\\' || current == '[' || current == '&') {
          translated.append('\\');
        }
        translated.append(current);
        firstClassMember = false;
        continue;
      }
      if (current == '%') {
        if (++index >= source.length()) {
          throw new PatternSyntaxException("trailing percent escape", source, index - 1);
        }
        appendPercentEscape(translated, source.charAt(index));
        continue;
      }
      switch (current) {
        case '[' -> {
          translated.append(current);
          inClass = true;
          firstClassMember = true;
        }
        case '^' -> translated.append("\\A");
        case '$' -> translated.append("\\z");
        case '(', ')', '{', '}', '|', '\\' -> translated.append('\\').append(current);
        default -> translated.append(current);
      }
    }
    if (inClass) {
      throw new PatternSyntaxException("unclosed character class", source, source.length());
    }
    return translated.toString();
  }

  private static void appendPercentEscape(StringBuilder translated, char escaped) {
    switch (escaped) {
      case '|' -> translated.append('|');
      case '(' -> translated.append('(');
      case ')' -> translated.append(')');
      case 'b' -> translated.append(WORD_BOUNDARY);
      case 'B' -> translated.append(NOT_WORD_BOUNDARY);
      case '<' -> translated.append(WORD_START);
      case '>' -> translated.append(WORD_END);
      case 'w' -> translated.append(WORD);
      case 'W' -> translated.append(NOT_WORD);
      case '1', '2', '3', '4', '5', '6', '7', '8', '9' ->
          translated.append('\\').append(escaped);
      default -> translated.append(Pattern.quote(String.valueOf(escaped)));
    }
  }

  private static final class MatchBudget {
    private long remaining;

    private MatchBudget(long remaining) {
      this.remaining = remaining;
    }

    private void consume() {
      if (--remaining < 0) {
        throw new MatchQuotaExceeded();
      }
    }
  }

  private record BoundedCharSequence(
      String source, int start, int end, MatchBudget budget) implements CharSequence {
    private BoundedCharSequence {
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(budget, "budget");
    }

    @Override
    public int length() {
      return end - start;
    }

    @Override
    public char charAt(int index) {
      if (index < 0 || index >= length()) {
        throw new IndexOutOfBoundsException(index);
      }
      budget.consume();
      return source.charAt(start + index);
    }

    @Override
    public CharSequence subSequence(int from, int to) {
      if (from < 0 || to < from || to > length()) {
        throw new IndexOutOfBoundsException(from + ".." + to);
      }
      return new BoundedCharSequence(source, start + from, start + to, budget);
    }

    @Override
    public String toString() {
      return source.substring(start, end);
    }
  }

  private static final class MatchQuotaExceeded extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }
}
