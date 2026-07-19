package moo.builtin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
  private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
  private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
  private static final Set<ArgType> STRING = Set.of(ArgType.STRING);
  private static final Set<ArgType> OBJECT = Set.of(ArgType.OBJECT);

  private final List<BuiltinSpec> manifest;
  private final Map<String, BuiltinSpec> specs;

  /** Creates a catalog without host listener access for focused VM execution. */
  public BuiltinCatalog() {
    manifest = buildManifest();
    specs = indexManifest(manifest);
  }

  /** Creates the production catalog with the concrete server listener owner. */
  public BuiltinCatalog(ListenerControl listenerControl) {
    Objects.requireNonNull(listenerControl, "listenerControl");
    manifest = buildManifest();
    specs = indexManifest(manifest);
  }

  private List<BuiltinSpec> buildManifest() {
    List<BuiltinSpec> entries = new ArrayList<>();
    entries.add(
        new BuiltinSpec(
            "length",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.STRING, ArgType.LIST)),
                    List.of(),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> length(a)));
    entries.add(
        new BuiltinSpec(
            "create",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> create(a, w, p)));
    entries.add(
        new BuiltinSpec(
            "set_player_flag",
            List.of(new CallShape(List.of(OBJECT, INTEGER), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> setPlayerFlag(a, w)));
    entries.add(
        new BuiltinSpec(
            "move",
            List.of(new CallShape(List.of(OBJECT, OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> move(a, w, p)));
    entries.add(
        new BuiltinSpec(
            "switch_player",
            List.of(new CallShape(List.of(OBJECT, OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.CONNECTION,
            (a, w, p, t, rt, rs, r, cp, c) -> switchPlayer(a)));
    entries.add(
        new BuiltinSpec(
            "set_task_perms",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> setTaskPerms(a)));
    entries.add(
        new BuiltinSpec(
            "notify",
            List.of(new CallShape(List.of(OBJECT, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.CONNECTION,
            (a, w, p, t, rt, rs, r, cp, c) -> notifyLine(a)));
    entries.add(
        new BuiltinSpec(
            "toliteral",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> toLiteral(a)));
    entries.add(
        new BuiltinSpec(
            "eval",
            List.of(new CallShape(List.of(STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> dynamicEval(a)));
    entries.add(
        new BuiltinSpec(
            "typeof",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> typeOf(a)));
    entries.add(
        new BuiltinSpec(
            "function_info",
            List.of(new CallShape(List.of(), List.of(STRING), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> functionInfo(a)));
    entries.add(
        new BuiltinSpec(
            "dump_database",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.SERVER,
            BuiltinCatalog::dumpDatabase));
    return List.copyOf(entries);
  }

  private static Map<String, BuiltinSpec> indexManifest(List<BuiltinSpec> manifest) {
    Map<String, BuiltinSpec> indexed = new LinkedHashMap<>();
    for (BuiltinSpec spec : manifest) {
      if (indexed.putIfAbsent(spec.name(), spec) != null) {
        throw new IllegalStateException("duplicate builtin manifest entry: " + spec.name());
      }
    }
    return Map.copyOf(indexed);
  }

  /** Returns the immutable production manifest in registration order. */
  public List<BuiltinSpec> manifest() {
    return manifest;
  }

  /** Finds one canonical builtin contract case-insensitively. */
  public Optional<BuiltinSpec> spec(String name) {
    return Optional.ofNullable(specs.get(name.toLowerCase(Locale.ROOT)));
  }

  /** Invokes one named builtin through its manifest entry. */
  public Result invoke(
      String name,
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers) {
    Optional<BuiltinSpec> selected = spec(name);
    if (selected.isEmpty()) {
      return Result.error(ErrorValue.E_VERBNF);
    }
    return invoke(
        selected.orElseThrow(),
        arguments,
        world,
        programmer,
        taskLocal,
        remainingTicks,
        remainingSeconds,
        receiver,
        callerProgrammer,
        callers);
  }

  /** Validates and invokes one exact manifest entry. */
  public Result invoke(
      BuiltinSpec spec,
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers) {
    if (spec.callShapes().stream().noneMatch(shape -> shape.acceptsArity(arguments.size()))) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (spec.callShapes().stream().noneMatch(shape -> shape.accepts(arguments))) {
      return Result.error(ErrorValue.E_TYPE);
    }
    if (!spec.permission().allows(world, programmer)) {
      return Result.error(ErrorValue.E_PERM);
    }
    return spec.implementation()
        .invoke(
            arguments,
            world,
            programmer,
            taskLocal,
            remainingTicks,
            remainingSeconds,
            receiver,
            callerProgrammer,
            callers);
  }

  private Result functionInfo(List<MooValue> arguments) {
    if (arguments.size() > 1) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (arguments.isEmpty()) {
      return Result.value(
          new ListValue(
              manifest.stream()
                  .map(BuiltinCatalog::functionInfoDescription)
                  .map(MooValue.class::cast)
                  .toList()));
    }
    if (!(arguments.getFirst() instanceof StringValue requestedName)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    BuiltinSpec requested = specs.get(decode(requestedName).toLowerCase(Locale.ROOT));
    return requested == null
        ? Result.error(ErrorValue.E_INVARG)
        : Result.value(functionInfoDescription(requested));
  }

  private static ListValue functionInfoDescription(BuiltinSpec spec) {
    CallShape shape = spec.callShapes().getFirst();
    List<MooValue> argumentTypes = new ArrayList<>();
    shape.required().forEach(types -> argumentTypes.add(new IntegerValue(typeCode(types))));
    shape.optional().forEach(types -> argumentTypes.add(new IntegerValue(typeCode(types))));
    int maximumArguments =
        shape.variadic().isPresent()
            ? -1
            : shape.required().size() + shape.optional().size();
    return new ListValue(
        List.of(
            encode(spec.name()),
            new IntegerValue(shape.required().size()),
            new IntegerValue(maximumArguments),
            new ListValue(argumentTypes)));
  }

  private static int typeCode(Set<ArgType> types) {
    if (types.size() != 1) {
      return -1;
    }
    return switch (types.iterator().next()) {
      case ANY -> -1;
      case NUMBER -> -2;
      case INTEGER -> 0;
      case OBJECT -> 1;
      case STRING -> 2;
      case ERROR -> 3;
      case LIST -> 4;
      case FLOAT -> 9;
      case MAP -> 10;
      case WAIF -> 13;
    };
  }

  private static Result dumpDatabase(
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers) {
    return Result.checkpoint();
  }

  private static Result length(List<MooValue> arguments) {
    MooValue value = arguments.getFirst();
    if (value instanceof StringValue string) {
      return Result.value(new IntegerValue(string.length()));
    }
    if (value instanceof ListValue list) {
      return Result.value(new IntegerValue(list.size()));
    }
    return Result.error(ErrorValue.E_TYPE);
  }

  private static Result create(List<MooValue> arguments, WorldTxn world, long programmer) {
    ObjectValue parent = (ObjectValue) arguments.getFirst();
    try {
      WorldObject created = world.createObject(parent.value(), programmer);
      return Result.value(new ObjectValue(created.id()));
    } catch (IllegalArgumentException error) {
      return Result.error(ErrorValue.E_INVARG);
    }
  }

  private static Result setPlayerFlag(List<MooValue> arguments, WorldTxn world) {
    ObjectValue object = (ObjectValue) arguments.get(0);
    IntegerValue enabled = (IntegerValue) arguments.get(1);
    return world.setPlayerFlag(object.value(), enabled.isTruthy())
        ? Result.zero()
        : Result.error(ErrorValue.E_INVARG);
  }

  private static Result move(List<MooValue> arguments, WorldTxn world, long programmer) {
    ObjectValue object = (ObjectValue) arguments.get(0);
    ObjectValue destination = (ObjectValue) arguments.get(1);
    if (world.object(object.value()).isEmpty() || world.object(destination.value()).isEmpty()) {
      return Result.error(ErrorValue.E_INVARG);
    }
    WorldObject programmerObject = world.object(programmer).orElse(null);
    if (programmerObject != null && (programmerObject.flags() & 4) != 0) {
      return Result.move(object.value(), destination.value());
    }
    return world.move(object.value(), destination.value())
        ? Result.zero()
        : Result.error(ErrorValue.E_INVARG);
  }

  private static Result switchPlayer(List<MooValue> arguments) {
    ObjectValue player = (ObjectValue) arguments.get(1);
    return Result.switchPlayer(player.value());
  }

  private static Result setTaskPerms(List<MooValue> arguments) {
    ObjectValue programmer = (ObjectValue) arguments.getFirst();
    return Result.programmer(programmer.value());
  }

  private static Result notifyLine(List<MooValue> arguments) {
    StringValue line = (StringValue) arguments.get(1);
    return Result.output(decode(line));
  }

  private static Result toLiteral(List<MooValue> arguments) {
    return Result.value(encode(arguments.getFirst().toLiteral()));
  }

  private static Result dynamicEval(List<MooValue> arguments) {
    StringValue source = (StringValue) arguments.getFirst();
    return Result.dynamicEval(decode(source));
  }

  private static Result typeOf(List<MooValue> arguments) {
    return Result.value(new IntegerValue(arguments.getFirst().type().code()));
  }

  private static String decode(StringValue value) {
    return new String(value.bytes(), StandardCharsets.ISO_8859_1);
  }

  private static StringValue encode(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
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
      Optional<MooValue> taskLocal,
      OptionalLong moveObject,
      OptionalLong moveDestination,
      Optional<ListValue> errorDetails,
      Optional<CheckpointRequest> checkpointRequest) {
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
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          Optional.empty(),
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

    static Result checkpoint() {
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
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          Optional.empty(),
          Optional.of(new CheckpointRequest()));
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

    static Result move(long object, long destination) {
      return new Result(
          Optional.empty(),
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
          Optional.empty(),
          OptionalLong.of(object),
          OptionalLong.of(destination),
          Optional.empty(),
          Optional.empty());
    }
  }
}
