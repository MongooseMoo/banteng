package moo.builtin;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.StringTokenizer;
import moo.bytecode.MooCompiler;
import moo.syntax.MooParser;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.world.WorldObject;
import moo.world.WorldTxn;
import moo.world.WorldVerb;

/** The explicit builtin catalog required by the first managed runtime path. */
public final class BuiltinCatalog {
  /** Invokes one named builtin without reflection or hidden world access. */
  public Result invoke(String name, List<MooValue> arguments, WorldTxn world, long programmer) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "length" -> length(arguments);
      case "mapkeys" -> mapKeys(arguments);
      case "max" -> {
        if (arguments.isEmpty()) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        if (arguments.getFirst() instanceof IntegerValue first) {
          long maximum = first.value();
          for (MooValue argument : arguments) {
            if (!(argument instanceof IntegerValue integer)) {
              yield Result.error(ErrorValue.E_TYPE);
            }
            maximum = Math.max(maximum, integer.value());
          }
          yield Result.value(new IntegerValue(maximum));
        }
        if (arguments.getFirst() instanceof FloatValue first) {
          double maximum = first.value();
          for (MooValue argument : arguments) {
            if (!(argument instanceof FloatValue floating)) {
              yield Result.error(ErrorValue.E_TYPE);
            }
            maximum = Math.max(maximum, floating.value());
          }
          yield Result.value(new FloatValue(maximum));
        }
        yield Result.error(ErrorValue.E_TYPE);
      }
      case "create" -> create(arguments, world, programmer);
      case "recycle" -> {
        if (arguments.size() != 1) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        if (!(arguments.getFirst() instanceof ObjectValue object)) {
          yield Result.error(ErrorValue.E_TYPE);
        }
        WorldObject target = world.object(object.value()).orElse(null);
        if (target == null) {
          yield Result.error(ErrorValue.E_INVARG);
        }
        WorldObject permissions = world.object(programmer).orElse(null);
        if (target.owner() != programmer
            && (permissions == null || (permissions.flags() & 4) == 0)) {
          yield Result.error(ErrorValue.E_PERM);
        }
        yield Result.recycle(object.value());
      }
      case "valid" -> {
        if (arguments.size() != 1) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        if (!(arguments.getFirst() instanceof ObjectValue object)) {
          yield Result.error(ErrorValue.E_TYPE);
        }
        yield Result.value(new IntegerValue(world.object(object.value()).isPresent() ? 1 : 0));
      }
      case "add_verb" -> addVerb(arguments, world, programmer);
      case "delete_verb" -> deleteVerb(arguments, world, programmer);
      case "set_verb_code" -> setVerbCode(arguments, world, programmer);
      case "set_player_flag" -> setPlayerFlag(arguments, world);
      case "move" -> move(arguments, world);
      case "switch_player" -> switchPlayer(arguments);
      case "add_property" -> addProperty(arguments, world);
      case "set_task_perms" -> setTaskPerms(arguments);
      case "notify" -> notifyLine(arguments);
      case "toliteral" -> toLiteral(arguments);
      case "eval" -> dynamicEval(arguments);
      case "raise" -> {
        if (arguments.size() != 1) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        if (!(arguments.getFirst() instanceof ErrorValue error)) {
          yield Result.error(ErrorValue.E_TYPE);
        }
        yield Result.error(error);
      }
      default -> Result.error(ErrorValue.E_VERBNF);
    };
  }

  /** Returns the fixed effect class for a builtin enabled in this slice. */
  public EffectClass effectClass(String name) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "length", "mapkeys", "max", "toliteral", "eval", "raise" -> EffectClass.PURE;
      case "valid" -> EffectClass.TRANSACTION_READ;
      case "create", "recycle" -> EffectClass.IRREVOCABLE;
      case "add_verb", "delete_verb", "set_verb_code", "set_player_flag", "move", "add_property" ->
          EffectClass.TRANSACTION_WRITE;
      case "notify", "switch_player", "set_task_perms" -> EffectClass.DEFERRED_EFFECT;
      default -> EffectClass.UNIMPLEMENTED;
    };
  }

  private static Result length(List<MooValue> arguments) {
    if (arguments.size() != 1) {
      return Result.error(ErrorValue.E_ARGS);
    }
    MooValue value = arguments.getFirst();
    if (value instanceof StringValue string) {
      return Result.value(new IntegerValue(string.length()));
    }
    if (value instanceof ListValue list) {
      return Result.value(new IntegerValue(list.size()));
    }
    return Result.error(ErrorValue.E_TYPE);
  }

  private static Result mapKeys(List<MooValue> arguments) {
    if (arguments.size() != 1) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.getFirst() instanceof MapValue map)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    return Result.value(new ListValue(List.copyOf(map.entries().keySet())));
  }

  private static Result create(List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.size() != 1 || !(arguments.getFirst() instanceof ObjectValue parent)) {
      return Result.error(ErrorValue.E_ARGS);
    }
    try {
      WorldObject created = world.createObject(parent.value(), programmer);
      return Result.value(new ObjectValue(created.id()));
    } catch (IllegalArgumentException error) {
      return Result.error(ErrorValue.E_INVARG);
    }
  }

  private static Result setPlayerFlag(List<MooValue> arguments, WorldTxn world) {
    if (arguments.size() != 2
        || !(arguments.get(0) instanceof ObjectValue object)
        || !(arguments.get(1) instanceof IntegerValue enabled)) {
      return Result.error(ErrorValue.E_ARGS);
    }
    return world.setPlayerFlag(object.value(), enabled.isTruthy())
        ? Result.zero()
        : Result.error(ErrorValue.E_INVARG);
  }

  private static Result addVerb(List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.size() != 3) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.get(1) instanceof ListValue information)
        || information.size() != 3
        || !(information.elements().get(0) instanceof ObjectValue owner)
        || !(information.elements().get(1) instanceof StringValue permissions)
        || !(information.elements().get(2) instanceof StringValue names)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    if (world.object(owner.value()).isEmpty()) {
      return Result.error(ErrorValue.E_INVARG);
    }
    int permissionBits = 0;
    String permissionText = decode(permissions).toLowerCase(Locale.ROOT);
    for (int index = 0; index < permissionText.length(); index++) {
      permissionBits |=
          switch (permissionText.charAt(index)) {
            case 'r' -> 1;
            case 'w' -> 2;
            case 'x' -> 4;
            case 'd' -> 8;
            default -> -1;
          };
      if (permissionBits < 0) {
        return Result.error(ErrorValue.E_INVARG);
      }
    }
    String verbNames = decode(names);
    int firstNameCharacter = 0;
    while (firstNameCharacter < verbNames.length() && verbNames.charAt(firstNameCharacter) == ' ') {
      firstNameCharacter++;
    }
    verbNames = verbNames.substring(firstNameCharacter);
    if (verbNames.isEmpty()) {
      return Result.error(ErrorValue.E_INVARG);
    }
    if (!(arguments.get(2) instanceof ListValue argumentsSpec)
        || argumentsSpec.size() != 3
        || !(argumentsSpec.elements().get(0) instanceof StringValue directText)
        || !(argumentsSpec.elements().get(1) instanceof StringValue prepositionText)
        || !(argumentsSpec.elements().get(2) instanceof StringValue indirectText)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    int direct =
        switch (decode(directText).toLowerCase(Locale.ROOT)) {
          case "none" -> 0;
          case "any" -> 1;
          case "this" -> 2;
          default -> -1;
        };
    int indirect =
        switch (decode(indirectText).toLowerCase(Locale.ROOT)) {
          case "none" -> 0;
          case "any" -> 1;
          case "this" -> 2;
          default -> -1;
        };
    String prepositionName = decode(prepositionText).toLowerCase(Locale.ROOT);
    int preposition =
        switch (prepositionName) {
          case "none" -> -1;
          case "any" -> -2;
          case "with", "using" -> 0;
          case "at", "to" -> 1;
          case "in front of" -> 2;
          case "in", "inside", "into" -> 3;
          case "on top of", "on", "onto", "upon" -> 4;
          case "out of", "from inside", "from" -> 5;
          case "over" -> 6;
          case "through" -> 7;
          case "under", "underneath", "beneath" -> 8;
          case "behind" -> 9;
          case "beside" -> 10;
          case "for", "about" -> 11;
          case "is" -> 12;
          case "as" -> 13;
          case "off", "off of" -> 14;
          default -> {
            String numeric =
                prepositionName.startsWith("#") ? prepositionName.substring(1) : prepositionName;
            if (numeric.isEmpty()) {
              yield Integer.MIN_VALUE;
            }
            int numericPreposition = 0;
            for (int index = 0; index < numeric.length(); index++) {
              char digit = numeric.charAt(index);
              if (digit < '0' || digit > '9') {
                numericPreposition = -1;
                break;
              }
              numericPreposition = numericPreposition * 10 + digit - '0';
              if (numericPreposition > 14) {
                numericPreposition = -1;
                break;
              }
            }
            yield numericPreposition < 0 ? Integer.MIN_VALUE : numericPreposition;
          }
        };
    if (direct < 0 || indirect < 0 || preposition == Integer.MIN_VALUE) {
      return Result.error(ErrorValue.E_INVARG);
    }
    if (!(arguments.get(0) instanceof ObjectValue object)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    WorldObject programmerObject = world.object(programmer).orElse(null);
    boolean wizard = programmerObject != null && (programmerObject.flags() & 4) != 0;
    if ((target.owner() != programmer && !wizard && (target.flags() & 32) == 0)
        || (owner.value() != programmer && !wizard)) {
      return Result.error(ErrorValue.E_PERM);
    }
    int packedPermissions = permissionBits | (direct << 4) | (indirect << 6);
    return Result.value(
        new IntegerValue(
            world.addVerb(
                object.value(), verbNames, owner.value(), packedPermissions, preposition)));
  }

  private static Result deleteVerb(List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.size() != 2) {
      return Result.error(ErrorValue.E_ARGS);
    }
    MooValue descriptor = arguments.get(1);
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return Result.error(ErrorValue.E_INVARG);
    }
    if (!(descriptor instanceof IntegerValue) && !(descriptor instanceof StringValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    if (!(arguments.get(0) instanceof ObjectValue object)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    WorldObject programmerObject = world.object(programmer).orElse(null);
    boolean wizard = programmerObject != null && (programmerObject.flags() & 4) != 0;
    if (target.owner() != programmer && !wizard && (target.flags() & 32) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    int verbIndex = -1;
    if (descriptor instanceof IntegerValue integer) {
      if (integer.value() <= target.verbs().size()) {
        verbIndex = Math.toIntExact(integer.value() - 1);
      }
    } else if (descriptor instanceof StringValue string) {
      String requestedName = decode(string).toLowerCase(Locale.ROOT);
      for (int index = 0; index < target.verbs().size() && verbIndex < 0; index++) {
        StringTokenizer names = new StringTokenizer(target.verbs().get(index).names());
        while (names.hasMoreTokens()) {
          String pattern = names.nextToken().toLowerCase(Locale.ROOT);
          int wildcard = pattern.indexOf('*');
          boolean matches;
          if (wildcard < 0) {
            matches = pattern.equals(requestedName);
          } else if (pattern.equals("*")) {
            matches = true;
          } else if (wildcard == pattern.length() - 1) {
            matches = requestedName.startsWith(pattern.substring(0, wildcard));
          } else {
            String requiredPrefix = pattern.substring(0, wildcard);
            String fullName = requiredPrefix + pattern.substring(wildcard + 1);
            matches =
                requestedName.startsWith(requiredPrefix) && fullName.startsWith(requestedName);
          }
          if (matches) {
            verbIndex = index;
            break;
          }
        }
      }
    }
    if (verbIndex < 0 || !world.deleteVerb(object.value(), verbIndex)) {
      return Result.error(ErrorValue.E_VERBNF);
    }
    return Result.zero();
  }

  private static Result setVerbCode(List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.size() != 3) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.get(2) instanceof ListValue lines)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    StringBuilder source = new StringBuilder();
    for (MooValue line : lines.elements()) {
      if (!(line instanceof StringValue text)) {
        return Result.error(ErrorValue.E_TYPE);
      }
      if (!source.isEmpty()) {
        source.append('\n');
      }
      source.append(decode(text));
    }
    if (!(arguments.get(0) instanceof ObjectValue object)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    MooValue descriptor = arguments.get(1);
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return Result.error(ErrorValue.E_INVARG);
    }
    if (!(descriptor instanceof IntegerValue) && !(descriptor instanceof StringValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    int verbIndex = -1;
    if (descriptor instanceof IntegerValue integer) {
      if (integer.value() <= target.verbs().size()) {
        verbIndex = Math.toIntExact(integer.value() - 1);
      }
    } else if (descriptor instanceof StringValue string) {
      String requestedName = decode(string).toLowerCase(Locale.ROOT);
      for (int index = 0; index < target.verbs().size() && verbIndex < 0; index++) {
        StringTokenizer names = new StringTokenizer(target.verbs().get(index).names());
        while (names.hasMoreTokens()) {
          String pattern = names.nextToken().toLowerCase(Locale.ROOT);
          int wildcard = pattern.indexOf('*');
          boolean matches;
          if (wildcard < 0) {
            matches = pattern.equals(requestedName);
          } else if (pattern.equals("*")) {
            matches = true;
          } else if (wildcard == pattern.length() - 1) {
            matches = requestedName.startsWith(pattern.substring(0, wildcard));
          } else {
            String requiredPrefix = pattern.substring(0, wildcard);
            String fullName = requiredPrefix + pattern.substring(wildcard + 1);
            matches =
                requestedName.startsWith(requiredPrefix) && fullName.startsWith(requestedName);
          }
          if (matches) {
            verbIndex = index;
            break;
          }
        }
      }
    }
    if (verbIndex < 0) {
      return Result.error(ErrorValue.E_VERBNF);
    }
    WorldObject programmerObject = world.object(programmer).orElse(null);
    WorldVerb verb = target.verbs().get(verbIndex);
    boolean wizard = programmerObject != null && (programmerObject.flags() & 4) != 0;
    if (programmerObject == null
        || (programmerObject.flags() & 2) == 0
        || (verb.owner() != programmer && !wizard && (verb.permissions() & 2) == 0)) {
      return Result.error(ErrorValue.E_PERM);
    }
    try {
      new MooCompiler().compile(MooParser.parse(source.toString()));
    } catch (IllegalArgumentException error) {
      String diagnostic = error.getMessage();
      if (diagnostic == null) {
        diagnostic = error.getClass().getSimpleName();
      }
      return Result.value(new ListValue(List.of(encode(diagnostic))));
    }
    if (!world.setVerbCode(object.value(), verbIndex, source.toString())) {
      return Result.error(ErrorValue.E_VERBNF);
    }
    return Result.value(new ListValue(List.of()));
  }

  private static Result move(List<MooValue> arguments, WorldTxn world) {
    if (arguments.size() != 2
        || !(arguments.get(0) instanceof ObjectValue object)
        || !(arguments.get(1) instanceof ObjectValue destination)) {
      return Result.error(ErrorValue.E_ARGS);
    }
    return world.move(object.value(), destination.value())
        ? Result.zero()
        : Result.error(ErrorValue.E_INVARG);
  }

  private static Result switchPlayer(List<MooValue> arguments) {
    if (arguments.size() != 2
        || !(arguments.get(0) instanceof ObjectValue)
        || !(arguments.get(1) instanceof ObjectValue player)) {
      return Result.error(ErrorValue.E_ARGS);
    }
    return Result.switchPlayer(player.value());
  }

  private static Result addProperty(List<MooValue> arguments, WorldTxn world) {
    if (arguments.size() != 4
        || !(arguments.get(0) instanceof ObjectValue object)
        || !(arguments.get(1) instanceof StringValue name)
        || !(arguments.get(3) instanceof ListValue information)
        || information.size() != 2
        || !(information.elements().get(0) instanceof ObjectValue owner)
        || !(information.elements().get(1) instanceof StringValue permissions)) {
      return Result.error(ErrorValue.E_ARGS);
    }
    String propertyName = decode(name);
    String permissionText = decode(permissions).toLowerCase(Locale.ROOT);
    int permissionBits = 0;
    if (permissionText.contains("r")) {
      permissionBits |= 1;
    }
    if (permissionText.contains("c")) {
      permissionBits |= 4;
    }
    return world.addProperty(
            object.value(), propertyName, arguments.get(2), owner.value(), permissionBits)
        ? Result.zero()
        : Result.error(ErrorValue.E_INVARG);
  }

  private static Result setTaskPerms(List<MooValue> arguments) {
    if (arguments.size() != 1 || !(arguments.getFirst() instanceof ObjectValue programmer)) {
      return Result.error(ErrorValue.E_ARGS);
    }
    return Result.programmer(programmer.value());
  }

  private static Result notifyLine(List<MooValue> arguments) {
    if (arguments.size() != 2
        || !(arguments.get(0) instanceof ObjectValue)
        || !(arguments.get(1) instanceof StringValue line)) {
      return Result.error(ErrorValue.E_ARGS);
    }
    return Result.output(decode(line));
  }

  private static Result toLiteral(List<MooValue> arguments) {
    if (arguments.size() != 1) {
      return Result.error(ErrorValue.E_ARGS);
    }
    return Result.value(encode(arguments.getFirst().toLiteral()));
  }

  private static Result dynamicEval(List<MooValue> arguments) {
    if (arguments.size() != 1 || !(arguments.getFirst() instanceof StringValue source)) {
      return Result.error(ErrorValue.E_ARGS);
    }
    return Result.dynamicEval(decode(source));
  }

  private static String decode(StringValue value) {
    return new String(value.bytes(), StandardCharsets.ISO_8859_1);
  }

  private static StringValue encode(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  /** Observable effect class for the explicitly enabled catalog. */
  public enum EffectClass {
    PURE,
    TRANSACTION_READ,
    TRANSACTION_WRITE,
    DEFERRED_EFFECT,
    IRREVOCABLE,
    UNIMPLEMENTED
  }

  /** One explicit builtin value, MOO error, dynamic call, or staged effect. */
  public record Result(
      Optional<MooValue> value,
      Optional<ErrorValue> error,
      Optional<String> dynamicSource,
      Optional<String> output,
      OptionalLong switchedPlayer,
      OptionalLong programmer,
      OptionalLong recycleTarget) {
    static Result value(MooValue value) {
      return new Result(
          Optional.of(value),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty());
    }

    static Result zero() {
      return value(new IntegerValue(0));
    }

    static Result error(ErrorValue error) {
      return new Result(
          Optional.empty(),
          Optional.of(error),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty());
    }

    static Result dynamicEval(String source) {
      return new Result(
          Optional.empty(),
          Optional.empty(),
          Optional.of(source),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty());
    }

    static Result output(String line) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.of(line),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty());
    }

    static Result switchPlayer(long player) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.of(player),
          OptionalLong.empty(),
          OptionalLong.empty());
    }

    static Result programmer(long programmer) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.of(programmer),
          OptionalLong.empty());
    }

    static Result recycle(long target) {
      return new Result(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.of(target));
    }
  }
}
