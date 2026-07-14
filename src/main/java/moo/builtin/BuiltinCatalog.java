package moo.builtin;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import moo.value.MooValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.world.WorldObject;
import moo.world.WorldTxn;

/** The explicit builtin catalog required by the first managed runtime path. */
public final class BuiltinCatalog {
  /** Invokes one named builtin without reflection or hidden world access. */
  public Result invoke(String name, List<MooValue> arguments, WorldTxn world, long programmer) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "length" -> length(arguments);
      case "mapkeys" -> mapKeys(arguments);
      case "create" -> create(arguments, world, programmer);
      case "set_player_flag" -> setPlayerFlag(arguments, world);
      case "move" -> move(arguments, world);
      case "switch_player" -> switchPlayer(arguments);
      case "add_property" -> addProperty(arguments, world);
      case "set_task_perms" -> setTaskPerms(arguments);
      case "notify" -> notifyLine(arguments);
      case "toliteral" -> toLiteral(arguments);
      case "eval" -> dynamicEval(arguments);
      default -> Result.error(ErrorValue.E_VERBNF);
    };
  }

  /** Returns the fixed effect class for a builtin enabled in this slice. */
  public EffectClass effectClass(String name) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "length", "mapkeys", "toliteral", "eval" -> EffectClass.PURE;
      case "create" -> EffectClass.IRREVOCABLE;
      case "set_player_flag", "move", "add_property" -> EffectClass.TRANSACTION_WRITE;
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
      OptionalLong programmer) {
    static Result value(MooValue value) {
      return new Result(
          Optional.of(value),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
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
          OptionalLong.empty());
    }

    static Result dynamicEval(String source) {
      return new Result(
          Optional.empty(),
          Optional.empty(),
          Optional.of(source),
          Optional.empty(),
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
          OptionalLong.empty());
    }

    static Result switchPlayer(long player) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.of(player),
          OptionalLong.empty());
    }

    static Result programmer(long programmer) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.of(programmer));
    }
  }
}
