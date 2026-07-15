package moo.builtin;

import java.io.IOException;
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
import moo.syntax.MooUnparser;
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

  private final Optional<ListenerControl> listenerControl;
  private final Map<Integer, SqliteHandle> sqliteHandles = new LinkedHashMap<>();
  private int nextSqliteHandle = 1;

  /** Creates a catalog without host listener access for focused VM execution. */
  public BuiltinCatalog() {
    listenerControl = Optional.empty();
  }

  /** Creates the production catalog with the concrete server listener owner. */
  public BuiltinCatalog(ListenerControl listenerControl) {
    this.listenerControl = Optional.of(listenerControl);
  }

  /** Invokes one named builtin without reflection or hidden world access. */
  public Result invoke(
      String name, List<MooValue> arguments, WorldTxn world, long programmer, MooValue taskLocal) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "length" -> length(arguments);
      case "listappend" -> {
        if (arguments.size() != 2) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        if (!(arguments.getFirst() instanceof ListValue list)) {
          yield Result.error(ErrorValue.E_TYPE);
        }
        List<MooValue> elements = new ArrayList<>(list.elements());
        elements.add(arguments.get(1));
        yield Result.value(new ListValue(elements));
      }
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
      case "parent" -> {
        if (arguments.size() != 1) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        if (!(arguments.getFirst() instanceof ObjectValue object)) {
          yield Result.error(ErrorValue.E_TYPE);
        }
        if (object.value() < 0) {
          yield Result.error(ErrorValue.E_INVARG);
        }
        WorldObject target = world.object(object.value()).orElse(null);
        if (target == null) {
          yield Result.error(ErrorValue.E_INVIND);
        }
        yield Result.value(new ObjectValue(target.parent()));
      }
      case "chparent" -> {
        if (arguments.size() != 2) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        if (!(arguments.get(0) instanceof ObjectValue object)
            || !(arguments.get(1) instanceof ObjectValue newParent)) {
          yield Result.error(ErrorValue.E_TYPE);
        }
        if (object.value() < 0) {
          yield Result.error(ErrorValue.E_INVARG);
        }
        WorldObject target = world.object(object.value()).orElse(null);
        if (target == null) {
          yield Result.error(ErrorValue.E_INVIND);
        }
        if (object.value() == newParent.value()) {
          yield Result.error(ErrorValue.E_RECMOVE);
        }
        if (newParent.value() < -1) {
          yield Result.error(ErrorValue.E_INVARG);
        }
        if (newParent.value() != -1 && world.object(newParent.value()).isEmpty()) {
          yield Result.error(ErrorValue.E_INVARG);
        }
        long ancestor = newParent.value();
        while (ancestor != -1) {
          if (ancestor == object.value()) {
            yield Result.error(ErrorValue.E_RECMOVE);
          }
          WorldObject ancestorObject = world.object(ancestor).orElse(null);
          if (ancestorObject == null) {
            yield Result.error(ErrorValue.E_INVARG);
          }
          ancestor = ancestorObject.parent();
        }
        WorldObject permissions = world.object(programmer).orElse(null);
        if (permissions == null || (permissions.flags() & 4) == 0) {
          yield Result.error(ErrorValue.E_PERM);
        }
        yield world.changeParent(object.value(), newParent.value())
            ? Result.zero()
            : Result.error(ErrorValue.E_INVARG);
      }
      case "listen" -> {
        if (arguments.size() < 2 || arguments.size() > 3) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        if (!(arguments.get(0) instanceof ObjectValue handler)
            || !(arguments.get(1) instanceof IntegerValue port)) {
          yield Result.error(ErrorValue.E_TYPE);
        }
        MapValue options;
        if (arguments.size() == 3) {
          if (!(arguments.get(2) instanceof MapValue suppliedOptions)) {
            yield Result.error(ErrorValue.E_TYPE);
          }
          options = suppliedOptions;
        } else {
          options = new MapValue(Map.of());
        }
        WorldObject permissions = world.object(programmer).orElse(null);
        if (permissions == null || (permissions.flags() & 4) == 0) {
          yield Result.error(ErrorValue.E_PERM);
        }
        if (world.object(handler.value()).isEmpty() || port.value() < 1 || port.value() > 65_535) {
          yield Result.error(ErrorValue.E_INVARG);
        }
        ListenerControl control = listenerControl.orElse(null);
        if (control == null) {
          yield Result.error(ErrorValue.E_PERM);
        }
        boolean printMessages =
            options.get(encode("print-messages")).map(MooValue::isTruthy).orElse(false);
        try {
          yield Result.value(
              new IntegerValue(control.listen(handler.value(), (int) port.value(), printMessages)));
        } catch (IllegalArgumentException error) {
          yield Result.error(ErrorValue.E_INVARG);
        } catch (IOException error) {
          yield Result.error(ErrorValue.E_QUOTA);
        }
      }
      case "unlisten" -> {
        if (arguments.size() != 1) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        if (!(arguments.getFirst() instanceof IntegerValue port)) {
          yield Result.error(ErrorValue.E_TYPE);
        }
        WorldObject permissions = world.object(programmer).orElse(null);
        if (permissions == null || (permissions.flags() & 4) == 0) {
          yield Result.error(ErrorValue.E_PERM);
        }
        ListenerControl control = listenerControl.orElse(null);
        if (control == null || port.value() < 1 || port.value() > 65_535) {
          yield Result.error(ErrorValue.E_INVARG);
        }
        yield control.unlisten((int) port.value())
            ? Result.zero()
            : Result.error(ErrorValue.E_INVARG);
      }
      case "connected_players" -> {
        if (arguments.size() > 1) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        boolean showAll = !arguments.isEmpty() && arguments.getFirst().isTruthy();
        List<MooValue> players = new ArrayList<>();
        for (long player : world.connectedPlayers(showAll)) {
          players.add(new ObjectValue(player));
        }
        yield Result.value(new ListValue(players));
      }
      case "connection_info" -> {
        if (arguments.size() != 1) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        if (!(arguments.getFirst() instanceof ObjectValue connection)) {
          yield Result.error(ErrorValue.E_TYPE);
        }
        MapValue info = world.connectionInfo(connection.value()).orElse(null);
        if (info == null) {
          yield Result.error(ErrorValue.E_INVARG);
        }
        WorldObject permissions = world.object(programmer).orElse(null);
        if (connection.value() != programmer
            && (permissions == null || (permissions.flags() & 4) == 0)) {
          yield Result.error(ErrorValue.E_PERM);
        }
        yield Result.value(info);
      }
      case "connection_name" -> {
        if (arguments.isEmpty() || arguments.size() > 2) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        if (!(arguments.getFirst() instanceof ObjectValue connection)) {
          yield Result.error(ErrorValue.E_TYPE);
        }
        IntegerValue method = null;
        if (arguments.size() == 2) {
          if (!(arguments.get(1) instanceof IntegerValue requestedMethod)) {
            yield Result.error(ErrorValue.E_TYPE);
          }
          method = requestedMethod;
        }
        MapValue info = world.connectionInfo(connection.value()).orElse(null);
        if (info == null) {
          yield Result.error(ErrorValue.E_INVARG);
        }
        WorldObject permissions = world.object(programmer).orElse(null);
        if (connection.value() != programmer
            && (permissions == null || (permissions.flags() & 4) == 0)) {
          yield Result.error(ErrorValue.E_PERM);
        }
        MooValue ipValue = info.get(encode("destination_ip")).orElse(null);
        if (!(ipValue instanceof StringValue destinationIp)) {
          yield Result.error(ErrorValue.E_INVARG);
        }
        MooValue nameValue = info.get(encode("destination_address")).orElse(destinationIp);
        if (!(nameValue instanceof StringValue destinationName)) {
          yield Result.error(ErrorValue.E_INVARG);
        }
        if (method == null) {
          yield Result.value(destinationName);
        }
        if (method.value() == 1) {
          yield Result.value(destinationIp);
        }
        MooValue sourcePortValue = info.get(encode("source_port")).orElse(null);
        MooValue destinationPortValue = info.get(encode("destination_port")).orElse(null);
        if (!(sourcePortValue instanceof IntegerValue sourcePort)
            || !(destinationPortValue instanceof IntegerValue destinationPort)) {
          yield Result.error(ErrorValue.E_INVARG);
        }
        MooValue outboundValue = info.get(encode("outbound")).orElse(new IntegerValue(0));
        String direction =
            outboundValue instanceof IntegerValue outbound && outbound.value() != 0 ? "to" : "from";
        yield Result.value(
            encode(
                "port "
                    + sourcePort.value()
                    + " "
                    + direction
                    + " "
                    + decode(destinationName)
                    + " ["
                    + decode(destinationIp)
                    + "], port "
                    + destinationPort.value()));
      }
      case "connection_options" -> connectionOptions(arguments, world, programmer);
      case "set_connection_option" -> setConnectionOption(arguments, world, programmer);
      case "boot_player" -> bootPlayer(arguments, world, programmer);
      case "force_input" -> forceInput(arguments, world, programmer);
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
      case "verb_info" -> verbInfo(arguments, world, programmer);
      case "verb_args" -> verbArgs(arguments, world, programmer);
      case "verb_code" -> verbCode(arguments, world, programmer);
      case "set_verb_info" -> setVerbInfo(arguments, world, programmer);
      case "set_verb_args" -> setVerbArgs(arguments, world, programmer);
      case "set_verb_code" -> setVerbCode(arguments, world, programmer);
      case "set_player_flag" -> setPlayerFlag(arguments, world);
      case "move" -> move(arguments, world);
      case "switch_player" -> switchPlayer(arguments);
      case "add_property" -> addProperty(arguments, world);
      case "delete_property" -> deleteProperty(arguments, world, programmer);
      case "task_perms" ->
          arguments.isEmpty()
              ? Result.value(new ObjectValue(programmer))
              : Result.error(ErrorValue.E_ARGS);
      case "set_task_perms" -> setTaskPerms(arguments);
      case "task_local" -> {
        if (!arguments.isEmpty()) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        WorldObject permissions = world.object(programmer).orElse(null);
        if (permissions == null || (permissions.flags() & 4) == 0) {
          yield Result.error(ErrorValue.E_PERM);
        }
        yield Result.value(taskLocal);
      }
      case "set_task_local" -> {
        if (arguments.size() != 1) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        WorldObject permissions = world.object(programmer).orElse(null);
        if (permissions == null || (permissions.flags() & 4) == 0) {
          yield Result.error(ErrorValue.E_PERM);
        }
        yield Result.taskLocal(arguments.getFirst());
      }
      case "notify" -> notifyLine(arguments);
      case "tostr" -> {
        StringBuilder text = new StringBuilder();
        for (MooValue argument : arguments) {
          text.append(
              switch (argument) {
                case StringValue string -> decode(string);
                case IntegerValue integer -> Long.toString(integer.value());
                case FloatValue floating -> floating.toLiteral();
                case ObjectValue object -> object.toLiteral();
                case ErrorValue error ->
                    switch (error) {
                      case E_NONE -> "No error";
                      case E_TYPE -> "Type mismatch";
                      case E_DIV -> "Division by zero";
                      case E_PERM -> "Permission denied";
                      case E_PROPNF -> "Property not found";
                      case E_VERBNF -> "Verb not found";
                      case E_VARNF -> "Variable not found";
                      case E_INVIND -> "Invalid indirection";
                      case E_RECMOVE -> "Recursive move";
                      case E_MAXREC -> "Too many verb calls";
                      case E_RANGE -> "Range error";
                      case E_ARGS -> "Incorrect number of arguments";
                      case E_NACC -> "Move refused by destination";
                      case E_INVARG -> "Invalid argument";
                      case E_QUOTA -> "Resource limit exceeded";
                      case E_FLOAT -> "Floating-point arithmetic error";
                      case E_FILE -> "File error";
                      case E_EXEC -> "Exec error";
                    };
                case ListValue ignored -> "{list}";
                case MapValue ignored -> "[map]";
              });
        }
        yield Result.value(encode(text.toString()));
      }
      case "toliteral" -> toLiteral(arguments);
      case "eval" -> dynamicEval(arguments);
      case "typeof" -> typeOf(arguments);
      case "function_info" -> {
        if (arguments.size() > 1) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        ListValue functionInfoDescription =
            new ListValue(
                List.of(
                    encode("function_info"),
                    new IntegerValue(0),
                    new IntegerValue(1),
                    new ListValue(List.of(new IntegerValue(2)))));
        ListValue queuedTasksDescription =
            new ListValue(
                List.of(
                    encode("queued_tasks"),
                    new IntegerValue(0),
                    new IntegerValue(2),
                    new ListValue(List.of(new IntegerValue(0), new IntegerValue(0)))));
        if (arguments.isEmpty()) {
          yield Result.value(
              new ListValue(List.of(functionInfoDescription, queuedTasksDescription)));
        }
        if (!(arguments.getFirst() instanceof StringValue requestedName)) {
          yield Result.error(ErrorValue.E_TYPE);
        }
        yield switch (decode(requestedName)) {
          case "function_info" -> Result.value(functionInfoDescription);
          case "queued_tasks" -> Result.value(queuedTasksDescription);
          default -> Result.error(ErrorValue.E_INVARG);
        };
      }
      case "queued_tasks" -> {
        if (arguments.size() > 2) {
          yield Result.error(ErrorValue.E_ARGS);
        }
        for (MooValue argument : arguments) {
          if (!(argument instanceof IntegerValue)) {
            yield Result.error(ErrorValue.E_TYPE);
          }
        }
        if (arguments.size() == 2 && arguments.get(1).isTruthy()) {
          yield Result.value(new IntegerValue(0));
        }
        yield Result.value(new ListValue(List.of()));
      }
      case "suspend" -> suspend(arguments);
      case "call_function" -> callFunction(arguments, world, programmer, taskLocal);
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
      case "length",
          "listappend",
          "mapkeys",
          "max",
          "tostr",
          "toliteral",
          "eval",
          "raise",
          "task_perms",
          "task_local",
          "typeof",
          "function_info" ->
          EffectClass.PURE;
      case "valid", "parent", "verb_info", "verb_args", "verb_code" -> EffectClass.TRANSACTION_READ;
      case "connected_players",
          "connection_info",
          "connection_name",
          "connection_options",
          "queued_tasks",
          "sqlite_handles",
          "sqlite_info",
          "sqlite_last_insert_row_id" ->
          EffectClass.EXTERNAL_READ;
      case "sqlite_query", "sqlite_execute" -> EffectClass.SUSPENDING_HOST;
      case "create",
          "recycle",
          "call_function",
          "listen",
          "unlisten",
          "sqlite_open",
          "sqlite_close",
          "sqlite_limit",
          "sqlite_interrupt" ->
          EffectClass.IRREVOCABLE;
      case "add_verb",
          "delete_verb",
          "set_verb_info",
          "set_verb_args",
          "set_verb_code",
          "set_player_flag",
          "move",
          "add_property",
          "delete_property",
          "chparent" ->
          EffectClass.TRANSACTION_WRITE;
      case "notify",
          "switch_player",
          "set_task_perms",
          "set_task_local",
          "suspend",
          "set_connection_option",
          "boot_player",
          "force_input" ->
          EffectClass.DEFERRED_EFFECT;
      default -> EffectClass.UNIMPLEMENTED;
    };
  }

  private static Result bootPlayer(List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.size() != 1) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.getFirst() instanceof ObjectValue target)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject permissions = world.object(programmer).orElse(null);
    if (target.value() != programmer && (permissions == null || (permissions.flags() & 4) == 0)) {
      return Result.error(ErrorValue.E_PERM);
    }
    return Result.bootPlayer(target.value());
  }

  private static Result setConnectionOption(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.size() != 3) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.get(0) instanceof ObjectValue target)
        || !(arguments.get(1) instanceof StringValue optionName)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject permissions = world.object(programmer).orElse(null);
    if (target.value() != programmer && (permissions == null || (permissions.flags() & 4) == 0)) {
      return Result.error(ErrorValue.E_PERM);
    }
    if (world.connectionInfo(target.value()).isEmpty()) {
      return Result.error(ErrorValue.E_INVARG);
    }
    String normalizedName = decode(optionName).toLowerCase(Locale.ROOT);
    if (normalizedName.equals("intrinsic-commands")) {
      MooValue value = arguments.get(2);
      if (value instanceof ListValue commands) {
        for (MooValue command : commands.elements()) {
          if (!(command instanceof StringValue commandName)
              || switch (decode(commandName)) {
                case ".program", "PREFIX", "SUFFIX", "OUTPUTPREFIX", "OUTPUTSUFFIX" -> false;
                default -> true;
              }) {
            return Result.error(ErrorValue.E_INVARG);
          }
        }
        return world.setIntrinsicCommands(target.value(), commands)
            ? Result.zero()
            : Result.error(ErrorValue.E_INVARG);
      }
      if (value instanceof IntegerValue enabled && enabled.isTruthy()) {
        return world.restoreIntrinsicCommands(target.value())
            ? Result.zero()
            : Result.error(ErrorValue.E_INVARG);
      }
      return Result.error(ErrorValue.E_INVARG);
    }
    ConnectionOption option =
        switch (normalizedName) {
          case "hold-input" -> ConnectionOption.HOLD_INPUT;
          case "flush-command" -> ConnectionOption.FLUSH_COMMAND;
          case "disable-oob" -> ConnectionOption.DISABLE_OOB;
          case "binary" -> ConnectionOption.BINARY;
          default -> null;
        };
    if (option == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    return Result.connectionOption(
        new ConnectionOptionRequest(target.value(), option, arguments.get(2)));
  }

  private static Result connectionOptions(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.size() != 2) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.get(0) instanceof ObjectValue target)
        || !(arguments.get(1) instanceof StringValue optionName)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    ListValue commands = world.intrinsicCommands(target.value()).orElse(null);
    if (commands == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    WorldObject permissions = world.object(programmer).orElse(null);
    if (target.value() != programmer && (permissions == null || (permissions.flags() & 4) == 0)) {
      return Result.error(ErrorValue.E_PERM);
    }
    if (!decode(optionName).equalsIgnoreCase("intrinsic-commands")) {
      return Result.error(ErrorValue.E_INVARG);
    }
    return Result.value(commands);
  }

  private static Result forceInput(List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.size() != 2) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.get(0) instanceof ObjectValue target)
        || !(arguments.get(1) instanceof StringValue line)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject permissions = world.object(programmer).orElse(null);
    if (permissions == null || (permissions.flags() & 4) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    return Result.forcedInput(new ForcedInputRequest(target.value(), decode(line)));
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

  private static Result verbInfo(List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.size() != 2) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.get(0) instanceof ObjectValue object)
        || !(arguments.get(1) instanceof StringValue descriptor)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    String requestedName = decode(descriptor).toLowerCase(Locale.ROOT);
    WorldVerb verb = null;
    if (requestedName.indexOf('*') < 0) {
      for (WorldVerb candidate : target.verbs()) {
        StringTokenizer names = new StringTokenizer(candidate.names());
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
            verb = candidate;
            break;
          }
        }
        if (verb != null) {
          break;
        }
      }
    }
    if (verb == null) {
      return Result.error(ErrorValue.E_VERBNF);
    }
    WorldObject permissions = world.object(programmer).orElse(null);
    boolean wizard = permissions != null && (permissions.flags() & 4) != 0;
    if (verb.owner() != programmer && !wizard && (verb.permissions() & 1) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    StringBuilder permissionText = new StringBuilder(4);
    if ((verb.permissions() & 1) != 0) {
      permissionText.append('r');
    }
    if ((verb.permissions() & 2) != 0) {
      permissionText.append('w');
    }
    if ((verb.permissions() & 4) != 0) {
      permissionText.append('x');
    }
    if ((verb.permissions() & 8) != 0) {
      permissionText.append('d');
    }
    return Result.value(
        new ListValue(
            List.of(
                new ObjectValue(verb.owner()),
                encode(permissionText.toString()),
                encode(verb.names()))));
  }

  private static Result verbArgs(List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.size() != 2) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.get(0) instanceof ObjectValue object)
        || !(arguments.get(1) instanceof StringValue descriptor)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    String requestedName = decode(descriptor).toLowerCase(Locale.ROOT);
    WorldVerb verb = null;
    if (requestedName.indexOf('*') < 0) {
      for (WorldVerb candidate : target.verbs()) {
        StringTokenizer names = new StringTokenizer(candidate.names());
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
            verb = candidate;
            break;
          }
        }
        if (verb != null) {
          break;
        }
      }
    }
    if (verb == null) {
      return Result.error(ErrorValue.E_VERBNF);
    }
    WorldObject permissions = world.object(programmer).orElse(null);
    boolean wizard = permissions != null && (permissions.flags() & 4) != 0;
    if (verb.owner() != programmer && !wizard && (verb.permissions() & 1) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    int direct = (verb.permissions() >> 4) & 3;
    int indirect = (verb.permissions() >> 6) & 3;
    String directText =
        switch (direct) {
          case 0 -> "none";
          case 1 -> "any";
          case 2 -> "this";
          default -> throw new IllegalStateException("invalid direct-object specification");
        };
    String indirectText =
        switch (indirect) {
          case 0 -> "none";
          case 1 -> "any";
          case 2 -> "this";
          default -> throw new IllegalStateException("invalid indirect-object specification");
        };
    String prepositionText =
        switch (verb.preposition()) {
          case -2 -> "any";
          case -1 -> "none";
          case 0 -> "with/using";
          case 1 -> "at/to";
          case 2 -> "in front of";
          case 3 -> "in/inside/into";
          case 4 -> "on top of/on/onto/upon";
          case 5 -> "out of/from inside/from";
          case 6 -> "over";
          case 7 -> "through";
          case 8 -> "under/underneath/beneath";
          case 9 -> "behind";
          case 10 -> "beside";
          case 11 -> "for/about";
          case 12 -> "is";
          case 13 -> "as";
          case 14 -> "off/off of";
          default -> throw new IllegalStateException("invalid preposition specification");
        };
    return Result.value(
        new ListValue(List.of(encode(directText), encode(prepositionText), encode(indirectText))));
  }

  private static Result verbCode(List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.size() != 2) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.get(0) instanceof ObjectValue object)
        || !(arguments.get(1) instanceof StringValue descriptor)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    String requestedName = decode(descriptor).toLowerCase(Locale.ROOT);
    WorldVerb verb = null;
    if (requestedName.indexOf('*') < 0) {
      for (WorldVerb candidate : target.verbs()) {
        StringTokenizer names = new StringTokenizer(candidate.names());
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
            verb = candidate;
            break;
          }
        }
        if (verb != null) {
          break;
        }
      }
    }
    if (verb == null) {
      return Result.error(ErrorValue.E_VERBNF);
    }
    WorldObject permissions = world.object(programmer).orElse(null);
    boolean wizard = permissions != null && (permissions.flags() & 4) != 0;
    if (verb.owner() != programmer && !wizard && (verb.permissions() & 1) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    String canonical = MooUnparser.unparse(MooParser.parse(verb.programSource()));
    List<MooValue> lines = new ArrayList<>();
    if (!canonical.isEmpty()) {
      for (String line : canonical.split("\n", -1)) {
        lines.add(encode(line));
      }
    }
    return Result.value(new ListValue(lines));
  }

  private static Result setVerbInfo(List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.size() != 3) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.get(0) instanceof ObjectValue object)
        || !(arguments.get(2) instanceof ListValue information)
        || information.size() != 3
        || !(information.elements().get(0) instanceof ObjectValue owner)
        || !(information.elements().get(1) instanceof StringValue permissions)
        || !(information.elements().get(2) instanceof StringValue names)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null || world.object(owner.value()).isEmpty()) {
      return Result.error(ErrorValue.E_INVARG);
    }
    MooValue descriptor = arguments.get(1);
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return Result.error(ErrorValue.E_INVARG);
    }
    if (!(descriptor instanceof IntegerValue) && !(descriptor instanceof StringValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    int verbIndex = -1;
    if (descriptor instanceof IntegerValue integer) {
      if (integer.value() <= target.verbs().size()) {
        verbIndex = Math.toIntExact(integer.value() - 1);
      }
    } else if (descriptor instanceof StringValue string) {
      String requestedName = decode(string).toLowerCase(Locale.ROOT);
      for (int index = 0; index < target.verbs().size() && verbIndex < 0; index++) {
        StringTokenizer verbNames = new StringTokenizer(target.verbs().get(index).names());
        while (verbNames.hasMoreTokens()) {
          String pattern = verbNames.nextToken().toLowerCase(Locale.ROOT);
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
        || (verb.owner() != programmer && !wizard && (verb.permissions() & 2) == 0)
        || (owner.value() != programmer && !wizard)) {
      return Result.error(ErrorValue.E_PERM);
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
    String newNames = decode(names);
    int firstNameCharacter = 0;
    while (firstNameCharacter < newNames.length() && newNames.charAt(firstNameCharacter) == ' ') {
      firstNameCharacter++;
    }
    newNames = newNames.substring(firstNameCharacter);
    if (newNames.isEmpty()) {
      return Result.error(ErrorValue.E_INVARG);
    }
    return world.setVerbInfo(object.value(), verbIndex, newNames, owner.value(), permissionBits)
        ? Result.zero()
        : Result.error(ErrorValue.E_VERBNF);
  }

  private static Result setVerbArgs(List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.size() != 3) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.get(0) instanceof ObjectValue object)
        || !(arguments.get(2) instanceof ListValue argumentsSpec)
        || argumentsSpec.size() != 3
        || !(argumentsSpec.elements().get(0) instanceof StringValue directText)
        || !(argumentsSpec.elements().get(1) instanceof StringValue prepositionText)
        || !(argumentsSpec.elements().get(2) instanceof StringValue indirectText)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    MooValue descriptor = arguments.get(1);
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return Result.error(ErrorValue.E_INVARG);
    }
    if (!(descriptor instanceof IntegerValue) && !(descriptor instanceof StringValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    int verbIndex = -1;
    if (descriptor instanceof IntegerValue integer) {
      if (integer.value() <= target.verbs().size()) {
        verbIndex = Math.toIntExact(integer.value() - 1);
      }
    } else if (descriptor instanceof StringValue string) {
      String requestedName = decode(string).toLowerCase(Locale.ROOT);
      for (int index = 0; index < target.verbs().size() && verbIndex < 0; index++) {
        StringTokenizer verbNames = new StringTokenizer(target.verbs().get(index).names());
        while (verbNames.hasMoreTokens()) {
          String pattern = verbNames.nextToken().toLowerCase(Locale.ROOT);
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
        || (verb.owner() != programmer && !wizard && (verb.permissions() & 2) == 0)) {
      return Result.error(ErrorValue.E_PERM);
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
          default -> Integer.MIN_VALUE;
        };
    if (direct < 0 || indirect < 0 || preposition == Integer.MIN_VALUE) {
      return Result.error(ErrorValue.E_INVARG);
    }
    return world.setVerbArgs(object.value(), verbIndex, direct, preposition, indirect)
        ? Result.zero()
        : Result.error(ErrorValue.E_VERBNF);
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
    if (permissionText.contains("w")) {
      permissionBits |= 2;
    }
    if (permissionText.contains("c")) {
      permissionBits |= 4;
    }
    return world.addProperty(
            object.value(), propertyName, arguments.get(2), owner.value(), permissionBits)
        ? Result.zero()
        : Result.error(ErrorValue.E_INVARG);
  }

  private static Result deleteProperty(List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.size() != 2) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (!(arguments.get(0) instanceof ObjectValue object)
        || !(arguments.get(1) instanceof StringValue name)) {
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
    return world.deleteProperty(object.value(), decode(name))
        ? Result.zero()
        : Result.error(ErrorValue.E_PROPNF);
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

  private Result callFunction(
      List<MooValue> arguments, WorldTxn world, long programmer, MooValue taskLocal) {
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
        programmer,
        taskLocal);
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

  /** Concrete host capability required by the MOO listener builtins. */
  public interface ListenerControl {
    /** Binds and starts one listener, returning its integer descriptor. */
    int listen(long handler, int port, boolean printMessages) throws IOException;

    /** Closes one dynamic listener selected by its integer descriptor. */
    boolean unlisten(int port);

    /** Writes ordered lines to one accepted connection selected by runtime ID. */
    void writeConnection(long connectionId, List<String> lines);

    /** Writes final lines and closes one accepted connection selected by runtime ID. */
    void bootConnection(long connectionId, List<String> lines);

    /** Selects delimiter-free binary reads for one accepted connection. */
    void setConnectionBinary(long connectionId, boolean binary);
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

  /** One validated mutation of the closed connection-option surface. */
  public record ConnectionOptionRequest(long target, ConnectionOption option, MooValue value) {}

  /** One validated line to inject into a live connection's input stream. */
  public record ForcedInputRequest(long target, String line) {}

  /** The connection options authorized by the held-input slice. */
  public enum ConnectionOption {
    HOLD_INPUT,
    FLUSH_COMMAND,
    DISABLE_OOB,
    BINARY
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
      Optional<CompletableFuture<MooValue>> hostResult,
      Optional<ConnectionOptionRequest> connectionOptionRequest,
      OptionalLong bootPlayerTarget,
      Optional<ForcedInputRequest> forcedInputRequest,
      Optional<MooValue> taskLocal) {
    private Result(
        Optional<MooValue> value,
        Optional<ErrorValue> error,
        Optional<String> dynamicSource,
        Optional<String> output,
        OptionalLong switchedPlayer,
        OptionalLong programmer,
        OptionalLong recycleTarget,
        OptionalDouble delaySeconds,
        Optional<CompletableFuture<MooValue>> hostResult,
        Optional<ConnectionOptionRequest> connectionOptionRequest,
        OptionalLong bootPlayerTarget,
        Optional<ForcedInputRequest> forcedInputRequest) {
      this(
          value,
          error,
          dynamicSource,
          output,
          switchedPlayer,
          programmer,
          recycleTarget,
          delaySeconds,
          hostResult,
          connectionOptionRequest,
          bootPlayerTarget,
          forcedInputRequest,
          Optional.empty());
    }

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
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty());
    }

    static Result zero() {
      return value(new IntegerValue(0));
    }

    static Result taskLocal(MooValue value) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty(),
          Optional.of(value));
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
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
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
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
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
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
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
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
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
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
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
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
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
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
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
          Optional.of(future),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty());
    }

    static Result connectionOption(ConnectionOptionRequest request) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.of(request),
          OptionalLong.empty(),
          Optional.empty());
    }

    static Result bootPlayer(long target) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.of(target),
          Optional.empty());
    }

    static Result forcedInput(ForcedInputRequest request) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.of(request));
    }
  }
}
