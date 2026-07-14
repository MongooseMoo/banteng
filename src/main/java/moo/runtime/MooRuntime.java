package moo.runtime;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;
import moo.builtin.BuiltinCatalog;
import moo.bytecode.BytecodeProgram;
import moo.bytecode.MooCompiler;
import moo.syntax.MooParser;
import moo.value.MooValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.vm.MooVm;
import moo.vm.VmState;
import moo.world.WorldObject;
import moo.world.WorldTxn;
import moo.world.WorldVerb;

/** The concrete serialized runtime owner used by the first server connection slice. */
public final class MooRuntime {
  private final WorldTxn world;
  private final MooCompiler compiler = new MooCompiler();
  private final BuiltinCatalog builtins = new BuiltinCatalog();
  private final MooVm vm = new MooVm();
  private final Map<Long, ConnectionState> connections = new LinkedHashMap<>();

  /** Creates a runtime over the one concrete world transaction. */
  public MooRuntime(WorldTxn world) {
    this.world = Objects.requireNonNull(world, "world");
  }

  /** Registers a negative connection and executes its initial empty login input. */
  public synchronized List<String> openConnection(long connectionId) {
    world.openConnection(connectionId);
    connections.put(connectionId, new ConnectionState());
    return executeLogin(connectionId, "");
  }

  /** Removes a connection and its intrinsic delimiter state. */
  public synchronized void closeConnection(long connectionId) {
    world.closeConnection(connectionId);
    connections.remove(connectionId);
  }

  /** Executes one serialized input line and returns its ordered output lines. */
  public synchronized List<String> executeLine(long connectionId, String line) {
    Objects.requireNonNull(line, "line");
    ConnectionState connection = requireConnection(connectionId);
    long player = world.connectionPlayer(connectionId).orElseThrow();
    if (player < 0) {
      return executeLogin(connectionId, line);
    }
    if (line.regionMatches(true, 0, "PREFIX ", 0, "PREFIX ".length())) {
      connection.prefix = Optional.of(line.substring("PREFIX ".length()));
      return List.of();
    }
    if (line.regionMatches(true, 0, "SUFFIX ", 0, "SUFFIX ".length())) {
      connection.suffix = Optional.of(line.substring("SUFFIX ".length()));
      return List.of();
    }

    List<String> output = new ArrayList<>();
    connection.prefix.ifPresent(output::add);
    if (line.startsWith(";")) {
      output.addAll(executeEval(player, line.substring(1).stripLeading()));
    }
    connection.suffix.ifPresent(output::add);
    return List.copyOf(output);
  }

  private List<String> executeLogin(long connectionId, String line) {
    WorldVerb login = world.verb(0, "do_login_command").orElseThrow();
    List<MooValue> arguments = new ArrayList<>();
    if (!line.isBlank()) {
      StringTokenizer words = new StringTokenizer(line);
      while (words.hasMoreTokens()) {
        arguments.add(encode(words.nextToken()));
      }
    }
    Map<String, MooValue> locals =
        verbLocals(
            0, connectionId, connectionId, "do_login_command", new ListValue(arguments), line);
    VmState state = executeStored(login, locals);
    if (state.switchedPlayer().isPresent()) {
      long switchedPlayer = state.switchedPlayer().orElseThrow();
      if (!world.switchConnectionPlayer(connectionId, switchedPlayer)) {
        throw new IllegalStateException("stored login switched to a missing player");
      }
      return List.of("*** Connected ***");
    }
    return state.output();
  }

  private List<String> executeEval(long player, String source) {
    WorldObject playerObject = world.object(player).orElseThrow();
    long location = playerObject.location();
    WorldVerb eval = world.verb(location, "eval").orElseThrow();
    Map<String, MooValue> locals =
        verbLocals(location, player, player, "eval", new ListValue(List.of()), source);
    return executeStored(eval, locals).output();
  }

  private VmState executeStored(WorldVerb verb, Map<String, MooValue> locals) {
    BytecodeProgram program = compiler.compile(MooParser.parse(verb.programSource()));
    VmState state = new VmState(locals, verb.owner());
    vm.execute(program, state, world, builtins);
    return state;
  }

  private static Map<String, MooValue> verbLocals(
      long thisObject,
      long player,
      long caller,
      String verb,
      ListValue arguments,
      String argumentString) {
    Map<String, MooValue> locals = new LinkedHashMap<>();
    locals.put("this", new ObjectValue(thisObject));
    locals.put("player", new ObjectValue(player));
    locals.put("caller", new ObjectValue(caller));
    locals.put("verb", encode(verb));
    locals.put("args", arguments);
    locals.put("argstr", encode(argumentString));
    return locals;
  }

  private ConnectionState requireConnection(long connectionId) {
    ConnectionState connection = connections.get(connectionId);
    if (connection == null) {
      throw new IllegalArgumentException("unknown connection #" + connectionId);
    }
    return connection;
  }

  private static StringValue encode(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  private static final class ConnectionState {
    private Optional<String> prefix = Optional.empty();
    private Optional<String> suffix = Optional.empty();
  }
}
