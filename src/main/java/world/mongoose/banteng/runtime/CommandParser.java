package world.mongoose.banteng.runtime;

import java.util.ArrayList;
import java.util.List;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.Prepositions;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldTxn;

/** Read-only command parsing, following ToastStunt parse_cmd.cc and match.cc. */
public final class CommandParser {
  private CommandParser() {}

  /** Port of parse_into_words: only spaces delimit, and escaping consumes the next character. */
  public static List<String> words(String input) {
    List<String> words = new ArrayList<>();
    boolean inQuotes = false;
    int index = 0;
    while (index < input.length() && input.charAt(index) == ' ') {
      index++;
    }
    while (index < input.length()) {
      StringBuilder word = new StringBuilder();
      while (index < input.length() && (inQuotes || input.charAt(index) != ' ')) {
        char character = input.charAt(index++);
        if (character == '"') {
          inQuotes = !inQuotes;
        } else if (character == '\\') {
          if (index < input.length()) {
            word.append(input.charAt(index++));
          }
        } else {
          word.append(character);
        }
      }
      while (index < input.length() && input.charAt(index) == ' ') {
        index++;
      }
      words.add(word.toString());
    }
    return List.copyOf(words);
  }

  public record ParsedCommand(
      String verb, String argstr, List<String> words,
      String dobjstr, String prepstr, String iobjstr, int preposition) {}

  /** Includes command shortcuts; words for do_command must be obtained before calling this. */
  public static ParsedCommand parse(String line) {
    int start = 0;
    while (start < line.length() && line.charAt(start) == ' ') {
      start++;
    }
    String verb = start == line.length() ? null : switch (line.charAt(start)) {
      case '"' -> "say";
      case ':' -> "emote";
      case ';' -> "eval";
      default -> null;
    };
    String argstr;
    List<String> words;
    if (verb != null) {
      argstr = line.substring(start + 1);
      words = words(verb + " " + argstr);
    } else {
      int end = start;
      boolean quoted = false;
      while (end < line.length() && (quoted || line.charAt(end) != ' ')) {
        char character = line.charAt(end++);
        if (character == '"') {
          quoted = !quoted;
        } else if (character == '\\' && end < line.length()) {
          end++;
        }
      }
      while (end < line.length() && line.charAt(end) == ' ') {
        end++;
      }
      argstr = line.substring(end);
      words = words(line);
    }
    if (words.isEmpty()) {
      return new ParsedCommand("", argstr, words, "", "", "", -1);
    }
    int first = words.size();
    int last = first;
    int prep = -1;
    scan:
    for (int index = 1; index < words.size(); index++) {
      for (int code = 0; code < Prepositions.ALIASES.size(); code++) {
        for (String alias : Prepositions.ALIASES.get(code)) {
          List<String> phrase = words(alias);
          if (index + phrase.size() > words.size()) {
            continue;
          }
          for (int part = 0; part < phrase.size(); part++) {
            if (!phrase.get(part).equalsIgnoreCase(words.get(index + part))) {
              break;
            }
            if (part == phrase.size() - 1) {
              first = index;
              last = index + phrase.size();
              prep = code;
              break scan;
            }
          }
        }
      }
    }
    return new ParsedCommand(words.getFirst(), argstr, words,
        String.join(" ", words.subList(1, first)),
        String.join(" ", words.subList(first, last)),
        String.join(" ", words.subList(last, words.size())), prep);
  }

  /** Port of match_object/match_contents; aliases and names share identity-based matches. */
  public static long matchObject(long player, String name, WorldTxn world) {
    if (name.isEmpty()) {
      return -1;
    }
    if (name.startsWith("#")) {
      try {
        String literal = name.substring(1);
        // strtol accepts leading ASCII whitespace and a sign, but no trailing characters.
        int first = 0;
        while (first < literal.length() && " \t\n\r\f\u000b".indexOf(literal.charAt(first)) >= 0) {
          first++;
        }
        String digits = literal.substring(first);
        if (!literal.isEmpty() && !digits.matches("[+-]?[0-9]+")) {
          return -3;
        }
        long object = literal.isEmpty() ? 0 : Long.parseLong(digits);
        return world.object(object).isPresent() ? object : -3;
      } catch (NumberFormatException ignored) {
        return -3;
      }
    }
    WorldObject actor = world.object(player).orElse(null);
    if (actor == null) {
      return -3;
    }
    if (name.equalsIgnoreCase("me")) {
      return player;
    }
    if (name.equalsIgnoreCase("here")) {
      return actor.location();
    }
    StringValue requestedName = StringValue.of(name);
    long exact = -1;
    long partial = -3;
    for (long container : new long[] {player, actor.location()}) {
      WorldObject contents = world.object(container).orElse(null);
      if (contents == null) {
        continue;
      }
      for (long candidate : contents.contents()) {
        WorldObject object = world.object(candidate).orElse(null);
        if (object == null) {
          continue;
        }
        List<String> names = new ArrayList<>();
        names.add(object.name());
        MooValue aliasesValue = world.readObjectProperty(candidate, "aliases").orElse(null);
        if (aliasesValue instanceof ListValue aliases) {
          for (MooValue alias : aliases.elements()) {
            if (alias instanceof StringValue string) {
              names.add(string.text());
            }
          }
        }
        for (String candidateName : names) {
          if (StringValue.of(candidateName).indexOfIgnoringCase(requestedName) == 0) {
            if (candidateName.length() == name.length()) {
              if (exact != -1 && exact != candidate) {
                return -2;
              }
              exact = candidate;
            } else {
              partial = partial == -3 || partial == candidate ? candidate : -2;
            }
          }
        }
      }
    }
    return exact != -1 ? exact : partial;
  }
}
