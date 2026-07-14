package moo.builtin;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
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
import org.sqlite.SQLiteConnection;

/** The explicit builtin catalog required by the first managed runtime path. */
public final class BuiltinCatalog {
  private static final int DEFAULT_SQLITE_FLAGS = 6;

  private final Map<Integer, SqliteHandle> sqliteHandles = new LinkedHashMap<>();
  private int nextSqliteHandle = 1;

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
      case "typeof" -> typeOf(arguments);
      case "suspend" -> suspend(arguments);
      case "call_function" -> callFunction(arguments, world, programmer);
      case "sqlite_open" -> sqliteOpen(arguments, world, programmer);
      case "sqlite_close" -> sqliteClose(arguments, world, programmer);
      case "sqlite_handles" -> sqliteHandles(arguments, world, programmer);
      case "sqlite_info" -> sqliteInfo(arguments, world, programmer);
      case "sqlite_query" -> sqliteQuery(arguments, world, programmer);
      case "sqlite_execute" -> sqliteExecute(arguments, world, programmer);
      case "sqlite_last_insert_row_id" -> sqliteLastInsertRowId(arguments, world, programmer);
      case "sqlite_limit" -> sqliteLimit(arguments, world, programmer);
      case "sqlite_interrupt" -> sqliteInterrupt(arguments, world, programmer);
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
      case "length", "mapkeys", "max", "toliteral", "eval", "raise", "typeof" -> EffectClass.PURE;
      case "valid" -> EffectClass.TRANSACTION_READ;
      case "sqlite_handles", "sqlite_info", "sqlite_last_insert_row_id" ->
          EffectClass.EXTERNAL_READ;
      case "sqlite_query", "sqlite_execute" -> EffectClass.SUSPENDING_HOST;
      case "create",
          "recycle",
          "call_function",
          "sqlite_open",
          "sqlite_close",
          "sqlite_limit",
          "sqlite_interrupt" ->
          EffectClass.IRREVOCABLE;
      case "add_verb", "delete_verb", "set_verb_code", "set_player_flag", "move", "add_property" ->
          EffectClass.TRANSACTION_WRITE;
      case "notify", "switch_player", "set_task_perms", "suspend" -> EffectClass.DEFERRED_EFFECT;
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

  private static Result typeOf(List<MooValue> arguments) {
    if (arguments.size() != 1) {
      return Result.error(ErrorValue.E_ARGS);
    }
    return Result.value(new IntegerValue(arguments.getFirst().type().code()));
  }

  private static Result suspend(List<MooValue> arguments) {
    if (arguments.size() != 1) {
      return Result.error(ErrorValue.E_ARGS);
    }
    double seconds;
    if (arguments.getFirst() instanceof IntegerValue integer) {
      seconds = integer.value();
    } else if (arguments.getFirst() instanceof FloatValue floating) {
      seconds = floating.value();
    } else {
      return Result.error(ErrorValue.E_TYPE);
    }
    if (seconds < 0.0) {
      return Result.error(ErrorValue.E_INVARG);
    }
    return Result.delay(seconds);
  }

  private Result callFunction(List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.isEmpty()) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.getFirst() instanceof StringValue functionName)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    return invoke(
        decode(functionName),
        List.copyOf(arguments.subList(1, arguments.size())),
        world,
        programmer);
  }

  private Result sqliteOpen(List<MooValue> arguments, WorldTxn world, long programmer) {
    WorldObject permissions = world.object(programmer).orElse(null);
    if (permissions == null || (permissions.flags() & 4) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    if (arguments.size() < 1 || arguments.size() > 2) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.getFirst() instanceof StringValue pathValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    int flags = DEFAULT_SQLITE_FLAGS;
    if (arguments.size() == 2) {
      if (!(arguments.get(1) instanceof IntegerValue requestedFlags)) {
        return Result.error(ErrorValue.E_TYPE);
      }
      try {
        flags = Math.toIntExact(requestedFlags.value());
      } catch (ArithmeticException error) {
        return Result.error(ErrorValue.E_INVARG);
      }
    }
    String path = decode(pathValue);
    try {
      SQLiteConnection connection =
          (SQLiteConnection) DriverManager.getConnection("jdbc:sqlite:" + path);
      synchronized (this) {
        int handle = nextSqliteHandle++;
        sqliteHandles.put(handle, new SqliteHandle(connection, path, flags));
        return Result.value(new IntegerValue(handle));
      }
    } catch (SQLException error) {
      return Result.error(ErrorValue.E_FILE);
    }
  }

  private Result sqliteClose(List<MooValue> arguments, WorldTxn world, long programmer) {
    WorldObject permissions = world.object(programmer).orElse(null);
    if (permissions == null || (permissions.flags() & 4) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    if (arguments.size() != 1) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.getFirst() instanceof IntegerValue handleValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    synchronized (this) {
      SqliteHandle handle = sqliteHandles.get((int) handleValue.value());
      if (handle == null) {
        return Result.error(ErrorValue.E_INVARG);
      }
      if (handle.activeLocks != 0) {
        return Result.error(ErrorValue.E_PERM);
      }
      try {
        handle.connection.close();
      } catch (SQLException error) {
        return Result.error(ErrorValue.E_FILE);
      }
      sqliteHandles.remove((int) handleValue.value());
      if (sqliteHandles.isEmpty()) {
        nextSqliteHandle = 1;
      }
      return Result.zero();
    }
  }

  private Result sqliteHandles(List<MooValue> arguments, WorldTxn world, long programmer) {
    WorldObject permissions = world.object(programmer).orElse(null);
    if (permissions == null || (permissions.flags() & 4) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    if (!arguments.isEmpty()) {
      return Result.error(ErrorValue.E_ARGS);
    }
    synchronized (this) {
      List<MooValue> handles = new ArrayList<>(sqliteHandles.size());
      for (int handle : sqliteHandles.keySet()) {
        handles.add(new IntegerValue(handle));
      }
      return Result.value(new ListValue(handles));
    }
  }

  private Result sqliteInfo(List<MooValue> arguments, WorldTxn world, long programmer) {
    WorldObject permissions = world.object(programmer).orElse(null);
    if (permissions == null || (permissions.flags() & 4) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    if (arguments.size() != 1) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.getFirst() instanceof IntegerValue handleValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    synchronized (this) {
      SqliteHandle handle = sqliteHandles.get((int) handleValue.value());
      if (handle == null) {
        return Result.error(ErrorValue.E_INVARG);
      }
      Map<MooValue, MooValue> information = new LinkedHashMap<>();
      information.put(encode("path"), encode(handle.path));
      information.put(encode("parse_types"), new IntegerValue((handle.flags & 2) == 0 ? 0 : 1));
      information.put(encode("parse_objects"), new IntegerValue((handle.flags & 4) == 0 ? 0 : 1));
      information.put(
          encode("sanitize_strings"), new IntegerValue((handle.flags & 8) == 0 ? 0 : 1));
      information.put(encode("locks"), new IntegerValue(handle.activeLocks));
      return Result.value(new MapValue(information));
    }
  }

  private Result sqliteQuery(List<MooValue> arguments, WorldTxn world, long programmer) {
    WorldObject permissions = world.object(programmer).orElse(null);
    if (permissions == null || (permissions.flags() & 4) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    if (arguments.size() < 2 || arguments.size() > 3) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.get(0) instanceof IntegerValue handleValue)
        || !(arguments.get(1) instanceof StringValue sqlValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    SqliteHandle handle;
    synchronized (this) {
      handle = sqliteHandles.get((int) handleValue.value());
      if (handle != null) {
        handle.activeLocks++;
      }
    }
    if (handle == null) {
      return Result.value(ErrorValue.E_INVARG);
    }
    String sql = decode(sqlValue);
    boolean includeHeaders = arguments.size() == 3 && arguments.get(2).isTruthy();
    CompletableFuture<MooValue> future = new CompletableFuture<>();
    Thread.ofVirtual()
        .start(
            () -> {
              MooValue completed;
              try (Statement statement = handle.connection.createStatement()) {
                if (!statement.execute(sql)) {
                  completed = new ListValue(List.of());
                } else {
                  try (ResultSet resultSet = statement.getResultSet()) {
                    ResultSetMetaData metadata = resultSet.getMetaData();
                    int columnCount = metadata.getColumnCount();
                    List<MooValue> resultRows = new ArrayList<>();
                    while (resultSet.next()) {
                      List<MooValue> row = new ArrayList<>(columnCount);
                      for (int column = 1; column <= columnCount; column++) {
                        String text = resultSet.getString(column);
                        MooValue value;
                        if (text == null) {
                          value = encode("NULL");
                        } else {
                          if ((handle.flags & 8) != 0) {
                            text = text.replace('\n', '\t');
                          }
                          value = encode(text);
                          if ((handle.flags & 4) != 0 && text.startsWith("#")) {
                            try {
                              value = new ObjectValue(Long.parseLong(text.substring(1)));
                            } catch (NumberFormatException error) {
                              value = encode(text);
                            }
                          }
                          if ((handle.flags & 2) != 0 && value instanceof StringValue) {
                            try {
                              value = new IntegerValue(Long.parseLong(text));
                            } catch (NumberFormatException integerError) {
                              try {
                                value = new FloatValue(Double.parseDouble(text));
                              } catch (NumberFormatException floatError) {
                                value = encode(text);
                              }
                            }
                          }
                        }
                        if (includeHeaders) {
                          row.add(
                              new ListValue(
                                  List.of(encode(metadata.getColumnLabel(column)), value)));
                        } else {
                          row.add(value);
                        }
                      }
                      resultRows.add(new ListValue(row));
                    }
                    completed = new ListValue(resultRows);
                  }
                }
              } catch (SQLException | RuntimeException error) {
                String message = error.getMessage();
                completed = encode(message == null ? error.getClass().getSimpleName() : message);
              } finally {
                synchronized (this) {
                  handle.activeLocks--;
                }
              }
              future.complete(completed);
            });
    return Result.hostResult(future);
  }

  private Result sqliteExecute(List<MooValue> arguments, WorldTxn world, long programmer) {
    WorldObject permissions = world.object(programmer).orElse(null);
    if (permissions == null || (permissions.flags() & 4) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    if (arguments.size() != 3) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.get(0) instanceof IntegerValue handleValue)
        || !(arguments.get(1) instanceof StringValue sqlValue)
        || !(arguments.get(2) instanceof ListValue bindings)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    SqliteHandle handle;
    synchronized (this) {
      handle = sqliteHandles.get((int) handleValue.value());
      if (handle != null) {
        handle.activeLocks++;
      }
    }
    if (handle == null) {
      return Result.value(ErrorValue.E_INVARG);
    }
    String sql = decode(sqlValue);
    CompletableFuture<MooValue> future = new CompletableFuture<>();
    Thread.ofVirtual()
        .start(
            () -> {
              MooValue completed;
              try (PreparedStatement statement = handle.connection.prepareStatement(sql)) {
                for (int index = 0; index < bindings.size(); index++) {
                  MooValue binding = bindings.elements().get(index);
                  int parameter = index + 1;
                  if (binding instanceof StringValue string) {
                    statement.setString(parameter, decode(string));
                  } else if (binding instanceof IntegerValue integer) {
                    statement.setInt(parameter, (int) integer.value());
                  } else if (binding instanceof FloatValue floating) {
                    statement.setDouble(parameter, floating.value());
                  } else if (binding instanceof ObjectValue object) {
                    statement.setString(parameter, "#" + object.value());
                  } else {
                    statement.setNull(parameter, Types.NULL);
                  }
                }
                if (!statement.execute()) {
                  completed = new ListValue(List.of());
                } else {
                  try (ResultSet resultSet = statement.getResultSet()) {
                    ResultSetMetaData metadata = resultSet.getMetaData();
                    int columnCount = metadata.getColumnCount();
                    List<MooValue> resultRows = new ArrayList<>();
                    while (resultSet.next()) {
                      List<MooValue> row = new ArrayList<>(columnCount);
                      for (int column = 1; column <= columnCount; column++) {
                        String text = resultSet.getString(column);
                        MooValue value;
                        if (text == null) {
                          value = encode("NULL");
                        } else {
                          if ((handle.flags & 8) != 0) {
                            text = text.replace('\n', '\t');
                          }
                          value = encode(text);
                          if ((handle.flags & 4) != 0 && text.startsWith("#")) {
                            try {
                              value = new ObjectValue(Long.parseLong(text.substring(1)));
                            } catch (NumberFormatException error) {
                              value = encode(text);
                            }
                          }
                          if ((handle.flags & 2) != 0 && value instanceof StringValue) {
                            try {
                              value = new IntegerValue(Long.parseLong(text));
                            } catch (NumberFormatException integerError) {
                              try {
                                value = new FloatValue(Double.parseDouble(text));
                              } catch (NumberFormatException floatError) {
                                value = encode(text);
                              }
                            }
                          }
                        }
                        row.add(value);
                      }
                      resultRows.add(new ListValue(row));
                    }
                    completed = new ListValue(resultRows);
                  }
                }
              } catch (SQLException | RuntimeException error) {
                String message = error.getMessage();
                completed = encode(message == null ? error.getClass().getSimpleName() : message);
              } finally {
                synchronized (this) {
                  handle.activeLocks--;
                }
              }
              future.complete(completed);
            });
    return Result.hostResult(future);
  }

  private Result sqliteLastInsertRowId(List<MooValue> arguments, WorldTxn world, long programmer) {
    WorldObject permissions = world.object(programmer).orElse(null);
    if (permissions == null || (permissions.flags() & 4) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    if (arguments.size() != 1) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.getFirst() instanceof IntegerValue handleValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    SqliteHandle handle;
    synchronized (this) {
      handle = sqliteHandles.get((int) handleValue.value());
    }
    if (handle == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    try (Statement statement = handle.connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT last_insert_rowid()")) {
      return rows.next()
          ? Result.value(new IntegerValue(rows.getLong(1)))
          : Result.error(ErrorValue.E_FILE);
    } catch (SQLException error) {
      return Result.error(ErrorValue.E_FILE);
    }
  }

  private Result sqliteLimit(List<MooValue> arguments, WorldTxn world, long programmer) {
    WorldObject permissions = world.object(programmer).orElse(null);
    if (permissions == null || (permissions.flags() & 4) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    if (arguments.size() != 3) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.get(0) instanceof IntegerValue handleValue)
        || !(arguments.get(2) instanceof IntegerValue limitValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    int category;
    if (arguments.get(1) instanceof IntegerValue integerCategory) {
      if (integerCategory.value() < 0 || integerCategory.value() > 11) {
        return Result.error(ErrorValue.E_INVARG);
      }
      category = (int) integerCategory.value();
    } else if (arguments.get(1) instanceof StringValue stringCategory) {
      category =
          switch (decode(stringCategory)) {
            case "LIMIT_LENGTH" -> 0;
            case "LIMIT_SQL_LENGTH" -> 1;
            case "LIMIT_COLUMN" -> 2;
            case "LIMIT_EXPR_DEPTH" -> 3;
            case "LIMIT_COMPOUND_SELECT" -> 4;
            case "LIMIT_VDBE_OP" -> 5;
            case "LIMIT_FUNCTION_ARG" -> 6;
            case "LIMIT_ATTACHED" -> 7;
            case "LIMIT_LIKE_PATTERN_LENGTH" -> 8;
            case "LIMIT_VARIABLE_NUMBER" -> 9;
            case "LIMIT_TRIGGER_DEPTH" -> 10;
            case "LIMIT_WORKER_THREADS" -> 11;
            default -> -1;
          };
      if (category < 0) {
        return Result.error(ErrorValue.E_INVARG);
      }
    } else {
      return Result.error(ErrorValue.E_TYPE);
    }
    int newValue;
    try {
      newValue = Math.toIntExact(limitValue.value());
    } catch (ArithmeticException error) {
      return Result.error(ErrorValue.E_INVARG);
    }
    SqliteHandle handle;
    synchronized (this) {
      handle = sqliteHandles.get((int) handleValue.value());
    }
    if (handle == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    try {
      return Result.value(
          new IntegerValue(handle.connection.getDatabase().limit(category, newValue)));
    } catch (SQLException error) {
      return Result.error(ErrorValue.E_FILE);
    }
  }

  private Result sqliteInterrupt(List<MooValue> arguments, WorldTxn world, long programmer) {
    WorldObject permissions = world.object(programmer).orElse(null);
    if (permissions == null || (permissions.flags() & 4) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    if (arguments.size() != 1) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.getFirst() instanceof IntegerValue handleValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    SqliteHandle handle;
    synchronized (this) {
      handle = sqliteHandles.get((int) handleValue.value());
    }
    if (handle == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    try {
      handle.connection.getDatabase().interrupt();
      return Result.zero();
    } catch (SQLException error) {
      return Result.error(ErrorValue.E_FILE);
    }
  }

  private static String decode(StringValue value) {
    return new String(value.bytes(), StandardCharsets.ISO_8859_1);
  }

  private static StringValue encode(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  private static final class SqliteHandle {
    private final SQLiteConnection connection;
    private final String path;
    private final int flags;
    private int activeLocks;

    private SqliteHandle(SQLiteConnection connection, String path, int flags) {
      this.connection = connection;
      this.path = path;
      this.flags = flags;
    }
  }

  /** Observable effect class for the explicitly enabled catalog. */
  public enum EffectClass {
    PURE,
    TRANSACTION_READ,
    TRANSACTION_WRITE,
    DEFERRED_EFFECT,
    EXTERNAL_READ,
    SUSPENDING_HOST,
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
      OptionalLong recycleTarget,
      OptionalDouble delaySeconds,
      Optional<CompletableFuture<MooValue>> hostResult) {
    static Result value(MooValue value) {
      return new Result(
          Optional.of(value),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty());
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
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty());
    }

    static Result dynamicEval(String source) {
      return new Result(
          Optional.empty(),
          Optional.empty(),
          Optional.of(source),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty());
    }

    static Result output(String line) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.of(line),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty());
    }

    static Result switchPlayer(long player) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.of(player),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty());
    }

    static Result programmer(long programmer) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.of(programmer),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty());
    }

    static Result recycle(long target) {
      return new Result(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.of(target),
          OptionalDouble.empty(),
          Optional.empty());
    }

    static Result delay(double seconds) {
      return new Result(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.of(seconds),
          Optional.empty());
    }

    static Result hostResult(CompletableFuture<MooValue> future) {
      return new Result(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.of(future));
    }
  }
}
