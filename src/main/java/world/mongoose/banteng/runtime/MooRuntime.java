package world.mongoose.banteng.runtime;

import world.mongoose.banteng.world.DurableTask;
import world.mongoose.banteng.vm.TaskOrigin;
import world.mongoose.banteng.vm.TaskBudget;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import world.mongoose.banteng.builtin.BuiltinCall;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.CheckpointRequest;
import world.mongoose.banteng.builtin.ConnectionRegistryAccess;
import world.mongoose.banteng.builtin.family.ConnectionBuiltins;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.persistence.DbCodec;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.BooleanValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import world.mongoose.banteng.value.ValueSemantics;
import world.mongoose.banteng.world.Access;
import world.mongoose.banteng.world.ActiveConnection;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.ObjectFlags;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldSnapshot;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;
import world.mongoose.banteng.world.WorldWaif;
import world.mongoose.banteng.vm.MooVm;
import world.mongoose.banteng.vm.MooVm.RootVerbCall;
import world.mongoose.banteng.vm.MooVm.CommandArguments;
import world.mongoose.banteng.world.WorldTxn.ResolvedVerb;
import world.mongoose.banteng.vm.VmSnapshot;
import world.mongoose.banteng.vm.VmState;

/** Concrete session and command owner backed by the sole production publication scheduler. */
public final class MooRuntime implements AutoCloseable, SchedulerRuntime {
  static final long DEFAULT_FOREGROUND_TICKS = 60_000;
  static final long DEFAULT_FOREGROUND_SECONDS = 5;
  static final long DEFAULT_BACKGROUND_TICKS = 30_000;
  static final long DEFAULT_BACKGROUND_SECONDS = 3;
  static final long DEFAULT_MAX_STACK_DEPTH = 50;
  static final long DEFAULT_CONNECT_TIMEOUT_SECONDS = 300;
  private final FinalizationCollector finalizationCollector = new FinalizationCollector(this);
  private final LoginFlow loginFlow;
  private final ConnectionIo connectionIo;
  private final BuiltinCatalog builtins;
  private final ValueSemantics valueSemantics;
  private final TaskRegistry taskRegistry;
  private final ServerLog serverLog;
  private final Optional<ConnectionBuiltins.ListenerControl> listenerControl;
  private final Optional<Path> database;
  private final Optional<Path> checkpoint;
  private final WorldTxn committedWorld;
  @GuardedBy("this")
  private final ConnectionRegistryAccess publishedConnectionRegistry;
  private final DbCodec checkpointCodec;
  private final LongSupplier nanoTime;
  private final ConnectionNameResolver connectionNameResolver;
  private final MooVm vm;
  private final PublicationScheduler scheduler;
  private final List<ActiveConnection> checkpointedConnections;
  @GuardedBy("this")
  private final Map<Long, ConnectionState> publishedConnections = new LinkedHashMap<>();
  private final AtomicLong connectionGenerations = new AtomicLong();
  private volatile boolean checkpointAttempted;
  @GuardedBy("this")
  private long sessionRevision;

  /** Receives the concrete collaborators assembled by the application. */
  public MooRuntime(
      WorldTxn world,
      ValueSemantics valueSemantics,
      ConnectionRegistryAccess connections,
      LongSupplier nanoTime,
      ConnectionNameResolver connectionNameResolver,
      Optional<ConnectionBuiltins.ListenerControl> listenerControl,
      Optional<Path> database,
      Optional<Path> checkpoint,
      List<ActiveConnection> activeConnections,
      ServerLog serverLog,
      MooVm vm,
      TaskRegistry taskRegistry,
      PublicationScheduler scheduler,
      BuiltinCatalog builtins,
      DbCodec checkpointCodec) {
    committedWorld = Objects.requireNonNull(world, "world");
    publishedConnectionRegistry = Objects.requireNonNull(connections, "connections");
    this.valueSemantics = Objects.requireNonNull(valueSemantics, "valueSemantics");
    this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    this.connectionNameResolver = Objects.requireNonNull(connectionNameResolver, "connectionNameResolver");
    this.listenerControl = Objects.requireNonNull(listenerControl, "listenerControl");
    this.database = Objects.requireNonNull(database, "database");
    this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
    this.serverLog = Objects.requireNonNull(serverLog, "serverLog");
    this.vm = Objects.requireNonNull(vm, "vm");
    this.taskRegistry = Objects.requireNonNull(taskRegistry, "taskRegistry");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.builtins = Objects.requireNonNull(builtins, "builtins");
    this.checkpointCodec = Objects.requireNonNull(checkpointCodec, "checkpointCodec");
    checkpointedConnections = List.copyOf(activeConnections);
    loginFlow = new LoginFlow(this, scheduler, listenerControl);
    connectionIo = new ConnectionIo(this, scheduler, listenerControl);
    try (WorldTxn initialOptions = committedWorld.begin()) {
      builtins.reloadProtectedFunctions(initialOptions);
    }
  }

  @Override
  public BuiltinHosts builtinHosts(AttemptContext context) {
    return BuiltinHosts.builder()
        .valueSemantics(valueSemantics)
        .queuedTasks(taskRegistry::queuedTasks)
        .killTask(taskRegistry::killTask)
        .read(call -> connectionIo.read(context, call))
        .threadPool(scheduler::threadPool)
        .threads(taskRegistry::threads)
        .connectionOptions(
            call -> connectionIo.connectionOptions(context, call.arguments(), call.world(), call.programmer()))
        .idleSeconds(call -> connectionIo.connectionSeconds(context, call, false))
        .connectedSeconds(call -> connectionIo.connectionSeconds(context, call, true))
        .setConnectionOption((target, option, value) ->
            connectionIo.applyConnectionOption(context, target, option, value))
        .connectionNameLookup(call -> connectionNameLookup(context, call))
        .dbDiskSize(call -> dbDiskSize())
        .flushInput(call -> connectionIo.flushInput(context, call.arguments(), call.world(), call.programmer()))
        .outputDelimiters(
            call -> connectionIo.outputDelimiters(context, call.arguments(), call.world(), call.programmer()))
        .queueInfo(
            call -> connectionIo.queueInfo(context, call.arguments(), call.world(), call.programmer(), taskRegistry))
        .switchPlayer(call -> connectionIo.switchPlayer(context, call))
        .taskStack(taskRegistry::taskStack)
        .resumeTask(scheduler::resumeTask)
        .serverLog(serverLog)
        .registerOutboundConnection((id, handler, info) -> registerOutboundConnection(context, id, handler, info))
        .connections(() -> context.connections)
        .build();
  }

  /** Runs the database's server_started verb through the production scheduler. */
  public void startServer() {
    for (ActiveConnection connection : checkpointedConnections) {
      scheduler.enqueueDetached(RuntimeRequest.checkpointDisconnect(connection));
    }
    scheduler.submit(RuntimeRequest.startup());
    scheduler.activateRestoredTasks();
  }

  /** Passes raw startup arguments to the database hook without evaluating them. */
  public void startScript(ListValue arguments) {
    scheduler.submit(RuntimeRequest.startScript(arguments));
  }

  /** Publishes the final shutdown checkpoint without checkpoint notification hooks. */
  public void requestGracefulShutdown() {
    scheduler.submit(RuntimeRequest.gracefulShutdown());
  }

  /** Delivers a process signal through the same scheduler as server tasks. */
  public void handleSignal(int signal) {
    scheduler.enqueueDetached(RuntimeRequest.signal(signal));
  }

  private RuntimeStep signalNow(AttemptContext context, int number) {
    if (number == 14) {
      return signalDefault(context, "SIGALRM");
    }
    if (number != 10 && number != 12) {
      for (long connectionId : context.sessions.keySet()) {
        context.effects.add(RuntimeEffect.write(connectionId,
            List.of("*** Shutting down: server panic ***")));
      }
      context.effects.add(RuntimeEffect.panic("Caught signal " + number));
      return RuntimeStep.returned(List.of());
    }
    String signal = number == 10 ? "SIGUSR1" : "SIGUSR2";
    ResolvedVerb handler = context.world.resolveVerb(0, "handle_signal", true).orElse(null);
    if (handler == null) {
      return signalDefault(context, signal);
    }
    return startStored(context,
        handler,
        new RootVerbCall(0, -1, -1, "handle_signal",
            new ListValue(List.of(StringValue.of(signal))), ""),
        RuntimeContinuation.after(
            RuntimeTransition.SIGNAL_RETURN, 0, signal, List.of(), 0, 0, false));
  }

  private RuntimeStep signalDefault(AttemptContext context, String signal) {
    if (signal.equals("SIGUSR1")) {
      context.effects.add(RuntimeEffect.reopenLog());
      return RuntimeStep.returned(List.of());
    }
    ResolvedVerb started = context.world.resolveVerb(0, "checkpoint_started", true).orElse(null);
    String reason = signal.equals("SIGALRM") ? "due to timer." : "due to remote request signal.";
    if (started == null) {
      context.effects.add(RuntimeEffect.checkpoint(false, reason));
      return RuntimeStep.returned(List.of());
    }
    return startStored(context,
        started,
        new RootVerbCall(0, -1, -1, "checkpoint_started", new ListValue(List.of()), ""),
        RuntimeContinuation.after(
            RuntimeTransition.CHECKPOINT_STARTED_RETURN, 0, reason,
            List.of(), 0, 0, false));
  }

  /** Stops runtime-owned executor work. */
  @Override
  public void close() {
    scheduler.close();
  }

  /** Waits for all runtime-owned platform workers to terminate. */
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return scheduler.awaitTermination(timeout, unit);
  }

  private RuntimeStep checkpointDisconnectNow(AttemptContext context, long player, long listener) {
    ResolvedVerb disconnected = context.world.resolveVerb(listener, "user_disconnected", true).orElse(null);
    if (disconnected == null) {
      return RuntimeStep.returned(List.of());
    }
    return startStored(context,
        disconnected,
        new RootVerbCall(
            listener,
            player,
            -1,
            "user_disconnected",
            new ListValue(List.of(new ObjectValue(player))),
            ""),
        RuntimeContinuation.after(
            RuntimeTransition.CHECKPOINT_USER_DISCONNECTED_RETURN,
            0,
            "",
            List.of(),
            0,
            0,
            false));
  }

  private RuntimeStep startServerNow(AttemptContext context) {
    return continueStartupAfterPendingWaifFinalization(context);
  }

  private RuntimeStep startScriptNow(AttemptContext context, ListValue arguments) {
    ResolvedVerb hook = context.world.resolveVerb(0, "do_start_script", true).orElse(null);
    if (hook == null) return RuntimeStep.returned(List.of());
    return startStored(context, hook,
        new RootVerbCall(0, -1, -1, "do_start_script", arguments, ""),
        RuntimeContinuation.after(RuntimeTransition.START_SCRIPT_RETURN, 0, "", List.of(), 0, 0, false));
  }

  private RuntimeStep gracefulShutdownNow(AttemptContext context) {
    for (long connectionId : context.sessions.keySet()) {
      context.effects.add(RuntimeEffect.write(connectionId,
          List.of("*** Shutting down: shutdown signal received ***")));
    }
    context.effects.add(RuntimeEffect.checkpoint(true, "shutdown signal received"));
    return RuntimeStep.returned(List.of());
  }

  private RuntimeStep continueStartupAfterPendingWaifFinalization(AttemptContext context) {
    for (MooValue pending : context.world.pendingFinalization()) {
      if (!(pending instanceof WaifValue waif)) {
        continue;
      }
      ResolvedVerb recycle = context.world.resolveVerb(waif.classObject().value(), ":recycle", true).orElse(null);
      if (recycle == null) {
        continue;
      }
      RootVerbCall locals = new RootVerbCall(waif, -1, -1, ":recycle",
          new ListValue(List.of()), "", Optional.empty());
      return startStored(context, recycle, locals, RuntimeContinuation.waifFinalization(waif));
    }
    ResolvedVerb started = context.world.resolveVerb(0, "server_started", true).orElse(null);
    if (started == null) {
      return RuntimeStep.returned(List.of());
    }
    return startStored(context,
        started,
        new RootVerbCall(0, -1, -1, "server_started", new ListValue(List.of()), ""),
        RuntimeContinuation.after(
            RuntimeTransition.SERVER_STARTED_RETURN, 0, "", List.of(), 0, 0, false));
  }

  private RuntimeStep checkpointFinishedNow(AttemptContext context, boolean success, boolean shutdown) {
    ResolvedVerb finished = context.world.resolveVerb(0, "checkpoint_finished", true).orElse(null);
    if (finished == null) {
      if (shutdown) {
        context.effects.add(RuntimeEffect.shutdown());
      }
      return RuntimeStep.returned(List.of());
    }
    ListValue arguments =
        new ListValue(List.of(new IntegerValue(success ? 1 : 0)));
    return startStored(context,
        finished,
        new RootVerbCall(0, -1, -1, "checkpoint_finished", arguments, ""),
        RuntimeContinuation.after(
            RuntimeTransition.CHECKPOINT_FINISHED_RETURN,
            0,
            "",
            List.of(),
            0,
            0,
            shutdown));
  }

  /** Registers a negative connection and executes its initial empty login input. */
  public List<String> openConnection(long connectionId) {
    return openConnection(connectionId, 0, true);
  }

  /** Registers a connection accepted by one concrete listener. */
  public List<String> openConnection(
      long connectionId, long listenerHandler, boolean printMessages) {
    return openConnection(connectionId, listenerHandler, printMessages, new MapValue(Map.of()));
  }

  /** Registers a connection with its listener identity and network metadata. */
  public List<String> openConnection(
      long connectionId, long listenerHandler, boolean printMessages, MapValue connectionInfo) {
    return scheduler.submit(
        RuntimeRequest.open(connectionId, listenerHandler, printMessages, connectionInfo));
  }

  /** Accepts socket input through its first suspension so its reader can continue. */
  public List<String> acceptConnection(
      long connectionId, long listenerHandler, boolean printMessages, MapValue connectionInfo) {
    return scheduler.submit(
        RuntimeRequest.open(connectionId, listenerHandler, printMessages, connectionInfo), true);
  }

  private RuntimeStep openConnectionNow(AttemptContext context,
      long connectionId, long listenerHandler, boolean printMessages, MapValue connectionInfo) {
    context.connections.openConnection(connectionId, connectionInfo);
    ConnectionState connection =
        new ConnectionState(
            listenerHandler,
            printMessages,
            connectionGenerations.incrementAndGet(),
            nanoTime.getAsLong());
    context.sessions.put(connectionId, connection);
    try {
      context.effects.add(RuntimeEffect.startTimeout(connectionId, connection.generation));
      return loginFlow.executeLogin(context, connectionId, "");
    } catch (RuntimeException | Error failure) {
      context.sessions.remove(connectionId);
      context.connections.closeConnection(connectionId);
      throw failure;
    }
  }

  /** Registers an irrevocably opened outbound socket in the current scheduler attempt. */
  public void registerOutboundConnection(AttemptContext context,
      long connectionId, long listenerHandler, MapValue connectionInfo) {
    context.connections.openConnection(connectionId, connectionInfo);
    ConnectionState connection =
        new ConnectionState(
            listenerHandler, false, connectionGenerations.incrementAndGet(), nanoTime.getAsLong());
    if (context.sessions.putIfAbsent(connectionId, connection) != null) {
      context.connections.closeConnection(connectionId);
      throw new IllegalArgumentException("duplicate connection #" + connectionId);
    }
  }

  /** Removes a connection and its intrinsic delimiter state. */
  public void closeConnection(long connectionId) {
    scheduler.submit(RuntimeRequest.close(connectionId));
  }

  private RuntimeStep closeConnectionNow(AttemptContext context, long connectionId) {
    ConnectionState connection = context.sessions.get(connectionId);
    OptionalLong disconnectedPlayer = context.connections.connectionPlayer(connectionId);
    context.sessions.remove(connectionId);
    context.connections.closeConnection(connectionId);
    if (connection != null && disconnectedPlayer.isPresent()) {
      long player = disconnectedPlayer.orElseThrow();
      Optional<ResolvedVerb> disconnected =
          context.world.resolveVerb(connection.listenerHandler, "user_client_disconnected", true);
      if (disconnected.isPresent()) {
        return startStored(context,
            disconnected.orElseThrow(),
            new RootVerbCall(
                connection.listenerHandler,
                player,
                -1,
                "user_client_disconnected",
                new ListValue(List.of(new ObjectValue(player))),
                ""),
            RuntimeContinuation.after(
                RuntimeTransition.CLOSE_AFTER_USER_CLIENT_DISCONNECTED,
                connectionId,
                "",
                List.of(),
                0,
                0,
                false));
      }
    }
    return RuntimeStep.returned(List.of());
  }

  /** Executes one serialized input line and returns its ordered output lines. */
  public List<String> executeLine(long connectionId, String line) {
    Objects.requireNonNull(line, "line");
    return scheduler.submit(RuntimeRequest.line(connectionId, line));
  }

  /** Processes a socket line through its first suspension, then streams later task output. */
  public List<String> receiveLine(long connectionId, String line) {
    return scheduler.submit(RuntimeRequest.line(connectionId, Objects.requireNonNull(line, "line")), true);
  }

  @Override
  public synchronized OptionalLong inputTaskPlayer(long connectionId, String line) {
    ConnectionState connection = publishedConnections.get(connectionId);
    if (connectionIo.hasPendingRead(connectionId)
        || (connection != null && !connection.disableOob && line.startsWith("#$#"))) {
      return OptionalLong.empty();
    }
    long player = publishedConnectionRegistry.connectionPlayer(connectionId).orElse(-1);
    return OptionalLong.of(player >= 0 ? player : connectionId);
  }

  @Override
  public Optional<List<String>> suspendedInputOutput(AttemptContext context, RuntimeContinuation continuation, VmState state) {
    RuntimeTransition transition = continuation.transition().orElseThrow();
    if (transition == RuntimeTransition.SWITCH_PLAYER_NOTIFIER_RETURN) {
      connectionIo.afterPlayerSwitchNotifier(context, continuation.connectionId(), continuation.flag());
      List<String> output = List.copyOf(state.effects().output());
      state.effects().clearOutput();
      return Optional.of(output);
    }
    if (transition == RuntimeTransition.UNCAUGHT_HANDLER_RETURN) {
      List<String> output = uncaughtHandlerOutput(context, continuation, state, false);
      state.effects().clearOutput();
      return Optional.of(output);
    }
    if (transition == RuntimeTransition.START_SCRIPT_RETURN
        || transition == RuntimeTransition.CHECKPOINT_STARTED_RETURN) {
      if (transition == RuntimeTransition.CHECKPOINT_STARTED_RETURN) {
        context.effects.add(RuntimeEffect.checkpoint(continuation.flag(), continuation.text()));
      }
      List<String> output = List.copyOf(state.effects().output());
      state.effects().clearOutput();
      return Optional.of(output);
    }
    boolean command = transition == RuntimeTransition.LINE_DO_COMMAND_GATE_THEN_DISPATCH
        || transition == RuntimeTransition.LINE_SELECTED_COMMAND_APPEND_OUTPUT;
    if (!command && transition != RuntimeTransition.LOGIN_AUTHENTICATE_AND_ASSOCIATE
        && transition != RuntimeTransition.LOGIN_BLANK_TRUTH_GATE
        && transition != RuntimeTransition.TRANSPORT_OOB_RETURN_OUTPUT
        && transition != RuntimeTransition.LINE_OOB_RETURN_OUTPUT
        && transition != RuntimeTransition.LAG_HANDLER_RETURN) {
      return Optional.empty();
    }
    List<String> output = new ArrayList<>(continuation.output());
    ConnectionState connection = context.sessions.get(continuation.connectionId());
    if (connection != null && transition == RuntimeTransition.LINE_DO_COMMAND_GATE_THEN_DISPATCH) {
      connection.prefix.ifPresent(output::add);
    }
    output.addAll(state.effects().output());
    state.effects().clearOutput();
    if (connection != null && command) {
      connection.suffix.ifPresent(output::add);
    }
    return Optional.of(List.copyOf(output));
  }

  @Override
  public void writeDetachedOutput(long player, List<String> output) {
    OptionalLong connection;
    synchronized (this) {
      connection = publishedConnectionRegistry.connectionId(player);
    }
    if (connection.isPresent()) {
      writeConnectionOutput(connection.orElseThrow(), output);
    }
  }

  @Override
  public void writeConnectionOutput(long connectionId, List<String> output) {
    listenerControl.ifPresent(control -> control.writeConnection(connectionId, output));
  }

  RuntimeStep executeLineNow(AttemptContext context, long connectionId, String line) {
    return executeLineNow(context, connectionId, line, Optional.empty());
  }

  RuntimeStep executeLineNow(AttemptContext context,
      long connectionId, String line, Optional<VmState> completedDoCommand) {
    Objects.requireNonNull(line, "line");
    ConnectionState connection = requireConnection(context, connectionId);
    connection.lastActivityNanos = nanoTime.getAsLong();
    if (!connection.disableOob && line.startsWith("#$#")) {
      return executeTransportOutOfBandNow(context, connectionId, line);
    }
    if (!connection.disableOob && line.startsWith("#$\"")) {
      line = line.substring(3);
    }
    if (connectionIo.hasPendingRead(connectionId)) {
      context.effects.add(RuntimeEffect.input(connectionId, line));
      return RuntimeStep.returned(List.of());
    }
    long player = context.connections.connectionPlayer(connectionId).orElseThrow();
    if (player < 0) {
      return loginFlow.executeLogin(context, connectionId, line);
    }

    if (connection.flushCommand.isPresent()
        && !connection.flushCommand.orElseThrow().isEmpty()
        && connection.flushCommand.orElseThrow().equalsIgnoreCase(line)) {
      List<String> output = new ArrayList<>();
      output.add(">> Flushing the following pending input:");
      for (String pendingLine : connection.pendingInput) {
        output.add(">>     " + pendingLine);
      }
      output.add(">> (Done flushing)");
      connection.pendingInput.clear();
      return RuntimeStep.returned(output);
    }
    if (connection.holdInput && (connection.disableOob || !line.startsWith("#$#"))) {
      connection.pendingInput.add(line);
      return RuntimeStep.returned(List.of());
    }

    RuntimeStep programming = executeProgrammingOrOutputControl(context, connection, player, line);
    if (programming != null) {
      return programming;
    }

    List<String> words = CommandParser.words(line);

    if (words.isEmpty()) {
      return RuntimeStep.returned(List.of());
    }

    List<MooValue> commandWords = new ArrayList<>();
    for (String word : words) {
      commandWords.add(StringValue.of(word));
    }
    List<String> doCommandOutput = List.of();
    boolean handled = false;
    Optional<ResolvedVerb> doCommand = context.world.resolveVerb(connection.listenerHandler, "do_command", true);
    if (completedDoCommand.isPresent()) {
      VmState state = completedDoCommand.orElseThrow();
      doCommandOutput = state.effects().output();
      handled = state.returnValue().isPresent() && state.returnValue().orElseThrow().isTruthy();
    } else if (doCommand.isPresent()) {
      return startStored(context,
          doCommand.orElseThrow(),
          new RootVerbCall(
              connection.listenerHandler,
              player,
              -1,
              "do_command",
              new ListValue(commandWords),
              line),
          RuntimeContinuation.after(
              RuntimeTransition.LINE_DO_COMMAND_GATE_THEN_DISPATCH,
              connectionId,
              line,
              List.of(),
              0,
              0,
              false));
    }

    List<String> output = new ArrayList<>();
    connection.prefix.ifPresent(output::add);
    output.addAll(doCommandOutput);
    if (handled) {
      connection.suffix.ifPresent(output::add);
      return RuntimeStep.returned(output);
    }
    return dispatchCommand(context, connectionId, connection, player, line, output);
  }

  @Nullable RuntimeStep executeProgrammingOrOutputControl(AttemptContext context,
      ConnectionState connection, long player, String line) {
    if (connection.programmingObject >= 0) {
      return executeProgramInput(context, connection, line);
    }

    CommandParser.ParsedCommand programmingCommand = CommandParser.parse(line);
    List<String> programmingWords = programmingCommand.words();
    boolean programmer =
        context.world.object(player).map(object -> ObjectFlags.isProgrammer(object.flags())).orElse(false);
    if (programmer && programmingCommand.verb().equals(".program")) {
      if (programmingWords.size() != 2) {
        return RuntimeStep.returned(List.of("Usage:  .program object:verb"));
      }
      String descriptor = programmingWords.get(1);
      int colon = descriptor.indexOf(':');
      if (colon < 0 || colon == descriptor.length() - 1) {
        return RuntimeStep.returned(
            List.of("You must specify a verb; use the format object:verb."));
      }
      String objectReference = descriptor.substring(0, colon);
      long objectId = matchProgramObject(context, player, objectReference);
      if (objectId == -3) {
        return RuntimeStep.returned(List.of("I don't see \"" + objectReference + "\" here."));
      }
      if (objectId == -2) {
        return RuntimeStep.returned(
            List.of("I don't know which \"" + objectReference + "\" you mean."));
      }
      if (objectId < 0) {
        return RuntimeStep.returned(
            List.of("\"" + objectReference + "\" is not a valid object."));
      }
      String verbName = descriptor.substring(colon + 1);
      OptionalLong definingObject = context.world.verbLocation(objectId, verbName, false);
      if (definingObject.isEmpty()) {
        return RuntimeStep.returned(
            List.of("That object does not have that verb definition."));
      }
      WorldObject object = context.world.object(definingObject.orElseThrow()).orElseThrow();
      WorldObject requestedObject = context.world.object(objectId).orElseThrow();
      WorldVerb verb = context.world.verb(object.id(), verbName, false).orElseThrow();
      int verbIndex = object.verbs().indexOf(verb);
      if (verbIndex < 0) {
        return RuntimeStep.returned(
            List.of("That object does not have that verb definition."));
      }
      if (!Access.canWrite(context.world, player, verb.owner(), verb.perms())) {
        return RuntimeStep.returned(List.of("Permission denied."));
      }
      connection.programmingObject = object.id();
      connection.programmingVerbName = verbName;
      connection.programmingSource.setLength(0);
      return RuntimeStep.returned(
          List.of(
              "Now programming "
                  + requestedObject.name()
                  + ":"
                  + verb.names()
                  + ".  Use \".\" to end."));
    }

    if (line.regionMatches(true, 0, "OUTPUTPREFIX ", 0, "OUTPUTPREFIX ".length())) {
      connection.prefix = Optional.of(line.substring("OUTPUTPREFIX ".length()));
      return RuntimeStep.returned(List.of());
    }
    if (line.regionMatches(true, 0, "OUTPUTSUFFIX ", 0, "OUTPUTSUFFIX ".length())) {
      connection.suffix = Optional.of(line.substring("OUTPUTSUFFIX ".length()));
      return RuntimeStep.returned(List.of());
    }
    if (line.regionMatches(true, 0, "PREFIX ", 0, "PREFIX ".length())) {
      connection.prefix = Optional.of(line.substring("PREFIX ".length()));
      return RuntimeStep.returned(List.of());
    }
    if (line.regionMatches(true, 0, "SUFFIX ", 0, "SUFFIX ".length())) {
      connection.suffix = Optional.of(line.substring("SUFFIX ".length()));
      return RuntimeStep.returned(List.of());
    }

    return null;
  }

  RuntimeStep executeProgramInput(AttemptContext context, ConnectionState connection, String line) {
    if (!line.equals(".")) {
      connection.programmingSource.append(line).append('\n');
      return RuntimeStep.returned(List.of());
    }
    WorldObject definingObject = context.world.object(connection.programmingObject).orElse(null);
    if (definingObject == null) {
      clearProgramming(connection);
      return RuntimeStep.returned(List.of("That object appears to have disappeared ..."));
    }
    WorldVerb verb =
        context.world
            .verb(connection.programmingObject, connection.programmingVerbName, false)
            .orElse(null);
    int verbIndex = verb == null ? -1 : definingObject.verbs().indexOf(verb);
    if (verbIndex < 0) {
      clearProgramming(connection);
      return RuntimeStep.returned(List.of("That verb appears to have disappeared ..."));
    }
    String source = connection.programmingSource.toString();
    MooCompiler.CompilationResult compilation = new MooCompiler().compileResult(source);
    List<String> output =
        compilation.diagnostics().stream()
            .map(MooCompiler.Diagnostic::display)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    output.add(compilation.diagnostics().size() + " error(s).");
    boolean installed =
        compilation.program().isPresent()
            && context.world
                .setVerbCode(connection.programmingObject, verbIndex, source, compilation.program().orElseThrow())
                .isOk();
    output.add(installed ? "Verb programmed." : "Verb not programmed.");
    clearProgramming(connection);
    return RuntimeStep.returned(output);
  }

  RuntimeStep dispatchCommand(AttemptContext context,
      long connectionId, ConnectionState connection, long player, String line, List<String> output) {
    CommandParser.ParsedCommand parsed = CommandParser.parse(line);
    List<String> words = parsed.words();
    if (!words.isEmpty()) {
      long room = context.world.object(player).orElseThrow().location();
      Optional<ResolvedVerb> playerCommandVerb = context.world.resolveVerb(player, words.getFirst(), false);
      Optional<ResolvedVerb> roomCommandVerb = context.world.resolveVerb(room, words.getFirst(), false);
      long huhReceiver = room;
      MooValue serverOptions = context.world.readObjectProperty(0, "server_options").orElse(null);
      if (serverOptions instanceof ObjectValue options
          && context.world.readObjectProperty(options.value(), "player_huh").orElse(null)
              instanceof IntegerValue playerHuh
          && playerHuh.value() != 0) {
        huhReceiver = player;
      }
      Optional<ResolvedVerb> huhVerb = context.world.resolveVerb(huhReceiver, "huh", true);
      {
        List<MooValue> arguments = new ArrayList<>();
        for (int index = 1; index < words.size(); index++) {
          arguments.add(StringValue.of(words.get(index)));
        }
        int preposition = parsed.preposition();
        String directObjectString = parsed.dobjstr();
        String prepositionString = parsed.prepstr();
        String indirectObjectString = parsed.iobjstr();
        long directObject = CommandParser.matchObject(player, directObjectString, context.world);
        long indirectObject = CommandParser.matchObject(player, indirectObjectString, context.world);
        ResolvedVerb selectedVerb = null;
        long thisObject = -1;
        if (playerCommandVerb.isPresent()) {
          ResolvedVerb candidate = playerCommandVerb.orElseThrow();
          if (commandArgumentsMatch(candidate, player, directObject, preposition, indirectObject)) {
            selectedVerb = candidate;
            thisObject = player;
          }
        }
        if (selectedVerb == null && roomCommandVerb.isPresent()) {
          ResolvedVerb candidate = roomCommandVerb.orElseThrow();
          if (commandArgumentsMatch(candidate, room, directObject, preposition, indirectObject)) {
            selectedVerb = candidate;
            thisObject = room;
          }
        }
        if (selectedVerb == null && directObject >= 0) {
          Optional<ResolvedVerb> directCommandVerb =
              context.world.resolveVerb(directObject, words.getFirst(), false);
          if (directCommandVerb.isPresent()) {
            ResolvedVerb candidate = directCommandVerb.orElseThrow();
            if (commandArgumentsMatch(candidate, directObject, directObject, preposition, indirectObject)) {
              selectedVerb = candidate;
              thisObject = directObject;
            }
          }
        }
        if (selectedVerb == null && indirectObject >= 0) {
          Optional<ResolvedVerb> indirectCommandVerb =
              context.world.resolveVerb(indirectObject, words.getFirst(), false);
          if (indirectCommandVerb.isPresent()) {
            ResolvedVerb candidate = indirectCommandVerb.orElseThrow();
            if (commandArgumentsMatch(candidate, indirectObject, directObject, preposition, indirectObject)) {
              selectedVerb = candidate;
              thisObject = indirectObject;
            }
          }
        }
        if (selectedVerb == null) {
          selectedVerb = huhVerb.orElse(null);
          thisObject = huhReceiver;
        }
        if (selectedVerb == null) {
          context.effects.add(RuntimeEffect.unmatchedInput(player));
          output.add("I couldn't understand that.");
          connection.suffix.ifPresent(output::add);
          return RuntimeStep.returned(output);
        }
        RootVerbCall locals = new RootVerbCall(new ObjectValue(thisObject), player, player,
            words.getFirst(), new ListValue(arguments), parsed.argstr(),
            Optional.of(new CommandArguments(directObject, directObjectString,
                prepositionString, indirectObject, indirectObjectString)));
        return startStored(context,
            selectedVerb,
            locals,
            RuntimeContinuation.after(
                RuntimeTransition.LINE_SELECTED_COMMAND_APPEND_OUTPUT,
                connectionId,
                line,
                output,
                0,
                0,
                false));
      }
    }
    connection.suffix.ifPresent(output::add);
    return RuntimeStep.returned(output);
  }

  static boolean commandArgumentsMatch(
      ResolvedVerb candidate, long receiver, long directObject, int preposition, long indirectObject) {
    ArgSpec direct = directObject == receiver ? ArgSpec.THIS : directObject == -1 ? ArgSpec.NONE : ArgSpec.ANY;
    ArgSpec indirect = indirectObject == receiver ? ArgSpec.THIS : indirectObject == -1 ? ArgSpec.NONE : ArgSpec.ANY;
    return (candidate.verb().dobj() == ArgSpec.ANY || candidate.verb().dobj() == direct)
        && (candidate.verb().preposition() == -2 || candidate.verb().preposition() == preposition)
        && (candidate.verb().iobj() == ArgSpec.ANY || candidate.verb().iobj() == indirect);
  }

  RuntimeStep continueLineAfterDoCommand(AttemptContext context,
      long connectionId, String line, VmState completedDoCommand) {
    return executeLineNow(context, connectionId, line, Optional.of(completedDoCommand));
  }

  /** Executes one transport-level out-of-band command on an existing connection. */
  public List<String> executeTransportOutOfBand(long connectionId, String command) {
    Objects.requireNonNull(command, "command");
    return scheduler.submit(RuntimeRequest.outOfBand(connectionId, command));
  }

  RuntimeStep executeTransportOutOfBandNow(AttemptContext context, long connectionId, String command) {
    Objects.requireNonNull(command, "command");
    ConnectionState connection = requireConnection(context, connectionId);
    connection.lastActivityNanos = nanoTime.getAsLong();
    long player = context.connections.connectionPlayer(connectionId).orElseThrow();
    Optional<ResolvedVerb> outOfBand =
        context.world.resolveVerb(connection.listenerHandler, "do_out_of_band_command", true);
    if (outOfBand.isEmpty()) {
      return RuntimeStep.returned(List.of());
    }
    List<MooValue> arguments = new ArrayList<>();
    for (String word : CommandParser.words(command)) {
      arguments.add(StringValue.of(word));
    }
    return startStored(context,
        outOfBand.orElseThrow(),
        new RootVerbCall(
            connection.listenerHandler,
            player,
            -1,
            "do_out_of_band_command",
            new ListValue(arguments),
            command),
        RuntimeContinuation.after(
            RuntimeTransition.TRANSPORT_OOB_RETURN_OUTPUT,
            connectionId,
            command,
            List.of(),
            0,
            0,
            false));
  }

  private long matchProgramObject(AttemptContext context, long player, String reference) {
    if (reference.startsWith("$")) {
      MooValue value = context.world.readObjectProperty(0, reference.substring(1)).orElse(null);
      if (value instanceof ObjectValue object && context.world.object(object.value()).isPresent()) {
        return object.value();
      }
      return -1;
    }
    return CommandParser.matchObject(player, reference, context.world);
  }

  private static void clearProgramming(ConnectionState connection) {
    connection.programmingObject = -1;
    connection.programmingVerbName = "";
    connection.programmingSource.setLength(0);
  }

  List<String> serverMessage(AttemptContext context, long listenerHandler, String name, String fallback) {
    MooValue listenerOptions =
        context.world.readObjectProperty(listenerHandler, "server_options").orElse(null);
    MooValue message =
        listenerOptions instanceof ObjectValue options
            ? context.world.readObjectProperty(options.value(), name).orElse(null)
            : null;
    if (message == null && listenerHandler != 0) {
      MooValue rootOptions = context.world.readObjectProperty(0, "server_options").orElse(null);
      if (rootOptions instanceof ObjectValue options) {
        message = context.world.readObjectProperty(options.value(), name).orElse(null);
      }
    }
    if (message == null) {
      return List.of(fallback);
    }
    if (message instanceof StringValue string) {
      return List.of(string.text());
    }
    if (message instanceof ListValue list) {
      List<String> lines = new ArrayList<>();
      for (MooValue element : list.elements()) {
        if (element instanceof StringValue string) {
          lines.add(string.text());
        }
      }
      return lines;
    }
    return List.of();
  }

  sealed interface LoginTimeoutResult {
    record KeepWatching() implements LoginTimeoutResult {}
    record Done() implements LoginTimeoutResult {}
    record TimedOut(List<String> lines) implements LoginTimeoutResult {
      public TimedOut {
        lines = List.copyOf(lines);
      }
    }
  }

  private RuntimeStep startTaskTimeout(AttemptContext context, RuntimeContinuation continuation) {
    VmSnapshot timedOut = continuation.timeoutSnapshot().orElseThrow();
    String resource = timedOut.outcome() == VmState.Outcome.SECONDS_EXHAUSTED ? "seconds" : "ticks";
    Optional<ResolvedVerb> handler = context.world.resolveVerb(0, "handle_task_timeout", true);
    if (handler.isEmpty()) {
      return RuntimeStep.returned(continuation.output());
    }

    MooValue serverOptions = context.world.readObjectProperty(0, "server_options").orElse(null);
    boolean includeVariables = serverOptions instanceof ObjectValue options
        && context.world.readObjectProperty(options.value(), "include_rt_vars")
            .orElse(new IntegerValue(0)).isTruthy();
    List<MooValue> stack = new ArrayList<>(TaskRegistry.projectStackFrames(
        timedOut.frames(), true, includeVariables, context.world, -1).elements());
    if (stack.isEmpty()) {
      stack.add(
          new ListValue(
              List.of(
                  timedOut.initialLocals().getOrDefault("this", new ObjectValue(-1)),
                  timedOut.initialLocals().getOrDefault("verb", StringValue.of("")),
                  new ObjectValue(timedOut.initialProgrammer()),
                  timedOut.initialVerbLocation(),
                  timedOut
                      .initialLocals()
                      .getOrDefault("player", new ObjectValue(continuation.first())),
                  new IntegerValue(0))));
    }
    ListValue traceback =
        new ListValue(
            List.of(StringValue.of("Task ran out of " + resource), StringValue.of("(End of traceback)")));
    ListValue arguments =
        new ListValue(List.of(StringValue.of(resource), new ListValue(stack), traceback));
    return startStored(context,
        handler.orElseThrow(),
        new RootVerbCall(0, continuation.first(), -1, "handle_task_timeout", arguments, ""),
        RuntimeContinuation.timeoutReturn(continuation.output()));
  }

  @Override
  public long nanoTime() {
    return nanoTime.getAsLong();
  }

  @Override
  public Optional<RuntimeStep> startLaggingTask(AttemptContext context,
      VmSnapshot initial, VmSnapshot completed, long player, double elapsedSeconds) {
    MooValue options = context.world.readObjectProperty(0, "server_options").orElse(null);
    MooValue configured = options instanceof ObjectValue object
        ? context.world.readObjectProperty(object.value(), "task_lag_threshold").orElse(null) : null;
    double threshold = configured instanceof FloatValue value ? value.value() : 5.0;
    if (!(threshold >= 0.1 && elapsedSeconds >= threshold)) {
      return Optional.empty();
    }
    ResolvedVerb handler = context.world.resolveVerb(0, "handle_lagging_task", true).orElse(null);
    if (handler == null) {
      return Optional.empty();
    }
    VmSnapshot.Frame entry = initial.frames().isEmpty() ? null : initial.frames().getFirst();
    MooValue location = entry == null ? initial.initialVerbLocation() : entry.verbLocation();
    String name = entry == null ? initial.initialFullVerbName() : entry.fullVerbName();
    long programmer = entry == null ? initial.initialProgrammer() : entry.programmer();
    MooValue includeVariables = options instanceof ObjectValue object
        ? context.world.readObjectProperty(object.value(), "include_rt_vars").orElse(new IntegerValue(0))
        : new IntegerValue(0);
    ListValue stack = completed.outcome() == VmState.Outcome.RETURNED
        ? new ListValue(List.of(new ObjectValue(-1), StringValue.of(name), new ObjectValue(-1),
            location, new ObjectValue(-1), new ObjectValue(-1)))
        : TaskRegistry.projectStackFrames(completed.frames(), true, includeVariables.isTruthy(),
            context.world, programmer);
    ListValue arguments = new ListValue(List.of(stack, new FloatValue(elapsedSeconds)));
    return Optional.of(startStored(context, handler,
        new RootVerbCall(0, player, -1, "handle_lagging_task", arguments, ""),
        RuntimeContinuation.after(RuntimeTransition.LAG_HANDLER_RETURN, 0, "", List.of(), 0, 0, false)));
  }

  RuntimeStep startStored(AttemptContext context,
      ResolvedVerb resolution, RootVerbCall call, RuntimeContinuation continuation) {
    MooValue serverOptions = context.world.readObjectProperty(0, "server_options").orElse(null);
    long foregroundTicks = DEFAULT_FOREGROUND_TICKS;
    long foregroundSeconds = DEFAULT_FOREGROUND_SECONDS;
    long maxStackDepth = DEFAULT_MAX_STACK_DEPTH;
    if (serverOptions instanceof ObjectValue options) {
      if (context.world.readObjectProperty(options.value(), "fg_ticks").orElse(null)
          instanceof IntegerValue ticks) {
        foregroundTicks = Math.max(100L, ticks.value());
      }
      if (context.world.readObjectProperty(options.value(), "fg_seconds").orElse(null)
          instanceof IntegerValue seconds) {
        foregroundSeconds = Math.max(1L, seconds.value());
      }
      if (context.world.readObjectProperty(options.value(), "max_stack_depth").orElse(null)
          instanceof IntegerValue depth) {
        maxStackDepth = Math.max(DEFAULT_MAX_STACK_DEPTH, depth.value());
      }
    }
    VmState root = new VmState(new TaskOrigin(Map.of(), resolution.verb().owner(), resolution.location(),
        call.verb(), resolution.verb().names(), resolution.verb().perms().debug()), new TaskBudget(foregroundTicks, foregroundSeconds, maxStackDepth));
    if (!MooVm.callVerb(root, call.receiver(), resolution, call.arguments(), call.verb(),
        MooVm.VerbCallKind.ROOT, Optional.empty(), Optional.empty(), Optional.of(call), context.world)) {
      throw new IllegalStateException("root verb exceeds empty task stack");
    }
    return RuntimeStep.vm(resolution.verb().program(), root.snapshot(), call.player(), continuation);
  }

  @Override
  public VmState startBackgroundTask(AttemptContext context, VmSnapshot initial) {
    long backgroundTicks = DEFAULT_BACKGROUND_TICKS;
    long backgroundSeconds = DEFAULT_BACKGROUND_SECONDS;
    long backgroundMaxStackDepth = DEFAULT_MAX_STACK_DEPTH;
    MooValue serverOptions = context.world.readObjectProperty(0, "server_options").orElse(null);
    if (serverOptions instanceof ObjectValue options) {
      if (context.world.readObjectProperty(options.value(), "bg_ticks").orElse(null)
          instanceof IntegerValue ticks) {
        backgroundTicks = Math.max(100L, ticks.value());
      }
      if (context.world.readObjectProperty(options.value(), "bg_seconds").orElse(null)
          instanceof IntegerValue seconds) {
        backgroundSeconds = Math.max(1L, seconds.value());
      }
      if (context.world.readObjectProperty(options.value(), "max_stack_depth").orElse(null)
          instanceof IntegerValue depth) {
        backgroundMaxStackDepth = Math.max(DEFAULT_MAX_STACK_DEPTH, depth.value());
      }
    }
    return VmState.restoreBackground(
        initial, backgroundTicks, backgroundSeconds, backgroundMaxStackDepth);
  }

  @Override
  public void publishVmState(AttemptContext context,
      VmState task,
      long taskPlayer,
      boolean collectAfterCompletion,
      List<VmSnapshot> otherTaskRoots) {
    if (listenerControl.isEmpty() && taskPlayer < 0) {
      context.effects.removeIf(effect -> {
        if (effect.kind() != RuntimeEffectKind.WRITE || effect.connectionId() != taskPlayer) {
          return false;
        }
        effect.lines().forEach(task.effects()::stageOutput);
        return true;
      });
    }
    connectionIo.applyConnectionOptionRequests(context, task);
    connectionIo.applyForcedInputRequests(context, task);
    connectionIo.applyNotificationRequests(context, task, taskPlayer);
    connectionIo.applyBootPlayerTargets(context, task, taskPlayer);
    connectionIo.closeRecycledPlayerConnections(context);
    if (task.outcome() != VmState.Outcome.RUNNING
        && task.outcome() != VmState.Outcome.PENDING_BUILTIN
        && task.outcome() != VmState.Outcome.FORKED) {
      connectionIo.publishPlayerSwitches(context);
    }
    var checkpointRequests = task.effects().drainCheckpointRequests();
    boolean panicPending = checkpointRequests.stream().anyMatch(CheckpointRequest::panic);
    boolean checkpointPending =
        !checkpointRequests.isEmpty()
            || context.effects.stream().anyMatch(effect -> effect.kind() == RuntimeEffectKind.CHECKPOINT);
    if (task.outcome() == VmState.Outcome.SUSPENDED) {
      if (!checkpointPending) {
        finalizationCollector.finalizePendingObjects(context);
      }
      if (!panicPending) {
        finalizationCollector.queueUnreachableAnonymousObjects(context, otherTaskRoots, task.snapshot());
      }
      if (!checkpointPending) {
        finalizationCollector.finalizePendingObjects(context);
      }
    } else if (collectAfterCompletion
        && (task.outcome() == VmState.Outcome.RETURNED
            || task.outcome() == VmState.Outcome.ERRORED)) {
      if (!panicPending) {
        if (checkpointPending) {
          finalizationCollector.queueUnreachableAnonymousObjects(context, otherTaskRoots);
        } else {
          finalizationCollector.queueUnreachableAnonymousObjects(context, otherTaskRoots, task.snapshot());
        }
      }
      if (!checkpointPending) {
        finalizationCollector.finalizePendingObjects(context);
      }
    }
    for (var request : checkpointRequests) {
      if (request.panic()) {
        context.effects.add(RuntimeEffect.panic(request.message()));
        continue;
      }
      ResolvedVerb started = context.world.resolveVerb(0, "checkpoint_started", true).orElse(null);
      if (started == null) {
        context.effects.add(RuntimeEffect.checkpoint(request.shutdown()));
      } else {
        context.spawnedSteps
            .add(
                startStored(context,
                    started,
                    new RootVerbCall(
                        0, -1, -1, "checkpoint_started", new ListValue(List.of()), ""),
                    RuntimeContinuation.after(
                        RuntimeTransition.CHECKPOINT_STARTED_RETURN,
                        0,
                        "",
                        List.of(),
                        0,
                        0,
                        request.shutdown())));
      }
    }
  }

  @Override
  public void collectAfterAnonymousFinalization(AttemptContext context,
      AnonymousObjectValue target, List<VmSnapshot> otherTaskRoots, boolean startNextFinalizationWave) {
    finalizationCollector.collectAfterAnonymousFinalization(
        context, target, otherTaskRoots, startNextFinalizationWave);
  }

  long elapsedSeconds(long sinceNanos) {
    long elapsedNanos = nanoTime.getAsLong() - sinceNanos;
    return TimeUnit.NANOSECONDS.toSeconds(Math.max(0L, elapsedNanos));
  }

  private BuiltinResult dbDiskSize() {
    if (checkpointAttempted && checkpoint.isPresent()) {
      try {
        return BuiltinResult.value(new IntegerValue(Files.size(checkpoint.orElseThrow())));
      } catch (IOException ignored) {
        // As in Toast db_disk_size(), stat the input when output is unavailable.
      }
    }
    if (database.isEmpty()) {
      return BuiltinResult.value(new IntegerValue(0));
    }
    try {
      return BuiltinResult.value(new IntegerValue(Files.size(database.orElseThrow())));
    } catch (IOException error) {
      return BuiltinResult.raised(ErrorValue.E_QUOTA,
          StringValue.of("No database file(s) available"), new IntegerValue(0));
    }
  }

  @Override
  public RuntimeStep execute(AttemptContext context,
      RuntimeContinuation continuation, Optional<world.mongoose.banteng.vm.VmSnapshot> completedVm) {
    RuntimeRequest request = continuation.ingress().orElse(null);
    if (request != null) {
      if (completedVm.isPresent() || continuation.transition().isPresent()) {
        throw new IllegalArgumentException("ingress continuation has resumed state");
      }
      return switch (request.operation) {
        case STARTUP -> startServerNow(context);
        case START_SCRIPT -> startScriptNow(context, request.arguments);
        case GRACEFUL_SHUTDOWN -> gracefulShutdownNow(context);
        case SIGNAL -> signalNow(context, Math.toIntExact(request.generation));
        case CHECKPOINT_DISCONNECT ->
            checkpointDisconnectNow(context, request.connectionId, request.listenerHandler);
        case OPEN ->
            openConnectionNow(context,
                request.connectionId,
                request.listenerHandler,
                request.printMessages,
                request.connectionInfo);
        case CLOSE -> closeConnectionNow(context, request.connectionId);
        case LINE -> executeLineNow(context, request.connectionId, request.text);
        case OUT_OF_BAND -> executeTransportOutOfBandNow(context, request.connectionId, request.text);
        case TIMEOUT -> loginFlow.executeLoginTimeout(context, request.connectionId, request.generation);
        case CHECKPOINT_FINISHED -> checkpointFinishedNow(context, request.printMessages, request.generation != 0);
      };
    }
    if (continuation.transition().orElseThrow() == RuntimeTransition.TASK_TIMEOUT_START) {
      if (completedVm.isPresent()) {
        throw new IllegalArgumentException("timeout start continuation has completed VM state");
      }
      return startTaskTimeout(context, continuation);
    }
    VmState state = VmState.restore(completedVm.orElseThrow());
    return resumeRuntime(context, continuation, state);
  }

  private RuntimeStep resumeRuntime(AttemptContext context, RuntimeContinuation continuation, VmState state) {
    if (continuation.transition().orElseThrow() == RuntimeTransition.SWITCH_PLAYER_NOTIFIER_RETURN) {
      connectionIo.afterPlayerSwitchNotifier(context, continuation.connectionId(), continuation.flag());
    }
    if (continuation.transition().orElseThrow() == RuntimeTransition.UNCAUGHT_HANDLER_RETURN) {
      boolean fallback = state.outcome() != VmState.Outcome.RETURNED
          || state.returnValue().filter(MooValue::isTruthy).isEmpty();
      return RuntimeStep.returned(uncaughtHandlerOutput(context, continuation, state, fallback));
    }
    if (continuation.transition().orElseThrow() == RuntimeTransition.LAG_HANDLER_RETURN) {
      return RuntimeStep.returned(state.effects().output());
    }
    if (state.outcome() == VmState.Outcome.ERRORED) {
      if (continuation.transition().orElseThrow() == RuntimeTransition.LINE_SELECTED_COMMAND_APPEND_OUTPUT) {
        List<String> leading = new ArrayList<>(continuation.output());
        leading.addAll(state.effects().output());
        ConnectionState connection = context.sessions.get(continuation.connectionId());
        return startUncaughtError(context, state, leading,
            connection == null ? Optional.empty() : connection.suffix, continuation.connectionId());
      }
      if (continuation.transition().orElseThrow() == RuntimeTransition.CHECKPOINT_STARTED_RETURN) {
        context.effects.add(RuntimeEffect.checkpoint(continuation.flag(), continuation.text()));
      }
      finishErroredFinalization(context, continuation);
      List<String> output = new ArrayList<>(continuation.output());
      output.addAll(state.effects().output());
      output.addAll(formatUncaughtException(context, state));
      if (continuation.transition().orElseThrow()
          == RuntimeTransition.LINE_SELECTED_COMMAND_APPEND_OUTPUT) {
        ConnectionState connection = context.sessions.get(continuation.connectionId());
        if (connection != null) {
          connection.suffix.ifPresent(output::add);
        }
      }
      if (continuation.transition().orElseThrow()
          == RuntimeTransition.LOGIN_TIMEOUT_USER_DISCONNECTED_THEN_BOOT) {
        return RuntimeStep.loginTimeout(new LoginTimeoutResult.KeepWatching(), output);
      }
      return RuntimeStep.returned(output);
    }
    return switch (continuation.transition().orElseThrow()) {
      case SIGNAL_RETURN ->
          state.outcome() != VmState.Outcome.RETURNED
                  || state.returnValue().filter(MooValue::isTruthy).isPresent()
              ? RuntimeStep.returned(state.effects().output()) : signalDefault(context, continuation.text());
      case SERVER_STARTED_RETURN -> RuntimeStep.returned(state.effects().output());
      case START_SCRIPT_RETURN -> RuntimeStep.returned(state.effects().output());
      case CHECKPOINT_USER_DISCONNECTED_RETURN -> RuntimeStep.returned(List.of());
      case CHECKPOINT_STARTED_RETURN -> {
        context.effects.add(RuntimeEffect.checkpoint(continuation.flag(), continuation.text()));
        yield RuntimeStep.returned(List.of());
      }
      case CHECKPOINT_FINISHED_RETURN -> {
        if (continuation.flag()) {
          context.effects.add(RuntimeEffect.shutdown());
        }
        yield RuntimeStep.returned(List.of());
      }
      case CLOSE_AFTER_USER_CLIENT_DISCONNECTED -> RuntimeStep.returned(List.of());
      case SWITCH_PLAYER_NOTIFIER_RETURN -> RuntimeStep.returned(state.effects().output());
      case LINE_OOB_RETURN_OUTPUT, TRANSPORT_OOB_RETURN_OUTPUT ->
          RuntimeStep.returned(state.effects().output());
      case LINE_DO_COMMAND_GATE_THEN_DISPATCH ->
          continueLineAfterDoCommand(context, continuation.connectionId(), continuation.text(), state);
      case LINE_SELECTED_COMMAND_APPEND_OUTPUT -> {
        List<String> output = new ArrayList<>(continuation.output());
        ConnectionState connection = context.sessions.get(continuation.connectionId());
        if (connection != null) {
          output.addAll(state.effects().output());
          connection.suffix.ifPresent(output::add);
        }
        yield RuntimeStep.returned(output);
      }
      case LOGIN_BLANK_TRUTH_GATE ->
          state.returnValue().isEmpty() || !state.returnValue().orElseThrow().isTruthy()
              ? RuntimeStep.returned(state.effects().output())
              : loginFlow.executeLoginAuthentication(context, continuation.connectionId(), continuation.text());
      case LOGIN_AUTHENTICATE_AND_ASSOCIATE ->
          loginFlow.finishLogin(context,
              continuation.connectionId(), continuation.first(), state);
      case LOGIN_RECONNECTED_HOOK_THEN_RETURN,
          LOGIN_NEW_USER_CONNECTED_THEN_RETURN,
          LOGIN_EXISTING_USER_CONNECTED_THEN_RETURN ->
          RuntimeStep.returned(continuation.flag() ? List.of() : continuation.output());
      case LOGIN_OLD_CLIENT_DISCONNECTED_THEN_ASSOCIATE ->
          loginFlow.associateRedirectedLogin(context, continuation);
      case LOGIN_USER_CREATED_THEN_CONNECTED -> loginFlow.continueCreatedLogin(context, continuation);
      case LOGIN_TIMEOUT_USER_DISCONNECTED_THEN_BOOT ->
          loginFlow.finishLoginTimeout(context, continuation.connectionId(), continuation.output());
      case BOOT_PLAYER_USER_DISCONNECTED_THEN_BOOT ->
          connectionIo.finishBootPlayer(context, continuation.connectionId(), continuation.output());
      case ANONYMOUS_FINALIZATION_RETURN -> RuntimeStep.returned(List.of());
      case WAIF_FINALIZATION_RETURN -> {
        finalizationCollector.finishWaifFinalization(context, (WaifValue) continuation.finalizationControl().orElseThrow().target());
        yield continueStartupAfterPendingWaifFinalization(context);
      }
      case WAIF_BACKGROUND_FINALIZATION_RETURN -> {
        finalizationCollector.finishWaifFinalization(context, (WaifValue) continuation.finalizationControl().orElseThrow().target());
        yield RuntimeStep.returned(List.of());
      }
      case TASK_TIMEOUT_START ->
          throw new IllegalStateException("timeout start cannot resume completed VM state");
      case TASK_TIMEOUT_RETURN -> {
        List<String> output = new ArrayList<>(state.effects().output());
        if (state.returnValue().isEmpty() || !state.returnValue().orElseThrow().isTruthy()) {
          output.addAll(continuation.output());
        }
        yield RuntimeStep.returned(output);
      }
      case UNCAUGHT_HANDLER_RETURN, LAG_HANDLER_RETURN -> RuntimeStep.returned(state.effects().output());
    };
  }

  private void finishErroredFinalization(AttemptContext context, RuntimeContinuation continuation) {
    continuation
        .finalizationControl()
        .filter(control -> control.kind() == FinalizationKind.WAIF)
        .ifPresent(control -> finalizationCollector.finishWaifFinalization(context, (WaifValue) control.target()));
  }

  private List<String> formatUncaughtException(AttemptContext context, VmState state) {
    if (state.returnValue().isEmpty()
        || !(state.returnValue().orElseThrow() instanceof ListValue exception)
        || exception.size() != 4
        || !(exception.elements().get(1) instanceof StringValue message)
        || !(exception.elements().get(3) instanceof ListValue traceback)) {
      MooValue code = state.uncaughtError().orElse(ErrorValue.E_NONE);
      return List.of(
          code instanceof ErrorValue error ? error.description() : code.toLiteral(),
          "(End of traceback)");
    }
    List<String> lines = new ArrayList<>();
    String description = message.text();
    for (int index = 0; index < traceback.size(); index++) {
      if (!(traceback.elements().get(index) instanceof ListValue frame) || frame.size() < 6) {
        continue;
      }
      MooValue receiver = frame.elements().get(0);
      String verb =
          frame.elements().get(1) instanceof StringValue name
              ? name.text()
              : "";
      MooValue location = frame.elements().get(3);
      long line =
          frame.elements().get(5) instanceof IntegerValue sourceLine ? sourceLine.value() : 0;
      String label;
      if (verb.isEmpty() && location.equals(new ObjectValue(-1))) {
        label = "#-1:Input to EVAL (this == #-1)";
      } else {
        String storedName = verb;
        if (location instanceof ObjectValue definer) {
          WorldVerb stored = context.world.verb(definer.value(), verb).orElse(null);
          if (stored != null) {
            storedName = stored.names();
          }
        }
        label = location.toLiteral() + ":" + storedName + " (this == " + receiver.toLiteral() + ")";
      }
      if (index == 0) {
        lines.add(label + ", line " + line + ":  " + description);
      } else {
        lines.add("... called from " + label + ", line " + line);
      }
      if (index == 0 && verb.isEmpty() && location.equals(new ObjectValue(-1))) {
        lines.add("... called from built-in function eval()");
      }
    }
    lines.add("(End of traceback)");
    return lines;
  }

  @Override
  public RuntimeStep startUncaughtError(AttemptContext context, VmState state) {
    return startUncaughtError(context, state, state.effects().output(), Optional.empty(), 0);
  }

  private RuntimeStep startUncaughtError(AttemptContext context, VmState state,
      List<String> leading, Optional<String> suffix, long connectionId) {
    List<String> formatted = formatUncaughtException(context, state);
    List<String> fallback = new ArrayList<>(leading);
    fallback.addAll(formatted);
    ResolvedVerb handler = context.world.resolveVerb(0, "handle_uncaught_error", true).orElse(null);
    if (handler == null
        || state.returnValue().isEmpty()
        || !(state.returnValue().orElseThrow() instanceof ListValue exception)
        || exception.size() != 4) {
      formatted.forEach(serverLog::error);
      suffix.ifPresent(fallback::add);
      return RuntimeStep.returned(fallback);
    }
    ListValue arguments =
        new ListValue(
            List.of(
                exception.elements().get(0),
                exception.elements().get(1),
                exception.elements().get(2),
                exception.elements().get(3),
                new ListValue(formatted.stream().map(StringValue::of).toList())));
    long player =
        exception.elements().get(3) instanceof ListValue traceback
                && traceback.size() > 0
                && traceback.elements().get(0) instanceof ListValue frame
                && frame.size() > 4
                && frame.elements().get(4) instanceof ObjectValue object
            ? object.value()
            : -1;
    return startStored(context,
        handler,
        new RootVerbCall(0, player, -1, "handle_uncaught_error", arguments, ""),
        RuntimeContinuation.after(
            RuntimeTransition.UNCAUGHT_HANDLER_RETURN, connectionId, suffix.orElse(""),
            fallback, leading.size(), 0, suffix.isPresent()));
  }

  private List<String> uncaughtHandlerOutput(AttemptContext context,
      RuntimeContinuation continuation, VmState state, boolean fallback) {
    int originalStart = Math.toIntExact(continuation.first());
    List<String> output = new ArrayList<>(continuation.output().subList(0, originalStart));
    output.addAll(state.effects().output());
    if (state.outcome() == VmState.Outcome.ERRORED) output.addAll(formatUncaughtException(context, state));
    if (fallback) output.addAll(continuation.output().subList(originalStart, continuation.output().size()));
    if (continuation.flag()) output.add(continuation.text());
    return output;
  }

  @Override
  public synchronized AttemptContext openAttempt(WorldTxn transaction, long taskId) {
    Map<Long, ConnectionState> sessions = new LinkedHashMap<>();
    publishedConnections.forEach((id, state) -> sessions.put(id, state.copy()));
    ConnectionRegistryAccess connections = publishedConnectionRegistry.copy();
    AttemptContext context =
        new AttemptContext(
            transaction,
            taskId,
            sessions,
            connections,
            sessionRevision,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>());
    return context;
  }

  @Override
  public synchronized boolean sessionsAreCurrent(AttemptContext context) {
    return context.baseSessionRevision == sessionRevision;
  }

  @Override
  public synchronized OptionalLong connectionPlayer(long connectionId) {
    return publishedConnectionRegistry.connectionPlayer(connectionId);
  }

  @Override
  public synchronized boolean loginTimeoutIsCurrent(long connectionId, long generation) {
    ConnectionState connection = publishedConnections.get(connectionId);
    return connection != null && connection.generation == generation
        && publishedConnectionRegistry.connectionPlayer(connectionId).orElse(0) < 0;
  }

  synchronized Optional<MapValue> connectionInfo(long objectId) {
    return publishedConnectionRegistry.connectionInfo(objectId);
  }

  private BuiltinResult connectionNameLookup(AttemptContext context, BuiltinCall call) {
    long target = ((ObjectValue) call.arguments().getFirst()).value();
    OptionalLong connectionId = context.connections.connectionId(target);
    MapValue info = context.connections.connectionInfo(target).orElse(null);
    if (connectionId.isEmpty() || info == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    MooValue destinationIp = info.get(StringValue.of("destination_ip")).orElse(null);
    MooValue destinationAddress = info.get(StringValue.of("destination_address")).orElse(null);
    if (!(destinationIp instanceof StringValue ip)
        || !(destinationAddress instanceof StringValue address)) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    long capturedConnectionId = connectionId.orElseThrow();
    String capturedIp = ip.text();
    String fallbackName = address.text();
    boolean rewrite = call.arguments().size() > 1 && call.arguments().get(1).isTruthy();
    return BuiltinResult.hostWork(
        () -> {
          if (!publishedConnectionMatches(capturedConnectionId, capturedIp)) {
            return BuiltinResult.raised(
                ErrorValue.E_INVARG,
                StringValue.of("Invalid connection"),
                new ObjectValue(target));
          }
          ConnectionNameResolution resolution = resolveConnectionName(capturedIp, fallbackName);
          if (rewrite
              && resolution.resolved()
              && !rewritePublishedConnectionName(
                  capturedConnectionId, capturedIp, resolution.name())) {
            return BuiltinResult.raised(
                ErrorValue.E_INVARG,
                StringValue.of("Failed to rewrite connection name."),
                new ObjectValue(target));
          }
          return BuiltinResult.value(StringValue.of(resolution.name()));
        });
  }

  private synchronized boolean publishedConnectionMatches(long connectionId, String expectedIp) {
    MapValue info = publishedConnectionRegistry.connectionInfo(connectionId).orElse(null);
    if (info == null) {
      return false;
    }
    MooValue destinationIp = info.get(StringValue.of("destination_ip")).orElse(null);
    return destinationIp instanceof StringValue ip && ip.text().equals(expectedIp);
  }

  private synchronized boolean rewritePublishedConnectionName(
      long connectionId, String expectedIp, String resolvedName) {
    if (!publishedConnectionRegistry.rewriteConnectionName(
        connectionId, expectedIp, resolvedName)) {
      return false;
    }
    sessionRevision = Math.incrementExact(sessionRevision);
    return true;
  }

  private ConnectionNameResolution resolveConnectionName(String address, String fallbackName) {
    try {
      return new ConnectionNameResolution(connectionNameResolver.resolve(address), true);
    } catch (UnknownHostException ignored) {
      return new ConnectionNameResolution(fallbackName, false);
    }
  }

  public static String resolveCanonicalConnectionName(String address)
      throws UnknownHostException {
    InetAddress[] candidates = InetAddress.getAllByName(address);
    if (candidates.length == 0) {
      throw new UnknownHostException(address);
    }
    return candidates[0].getCanonicalHostName();
  }

  @Override
  public void publishAttempt(AttemptContext context, WorldSnapshot committedWorld) {
    List<RuntimeEffect> publishedEffects;
    List<ReadRegistration> readRegistrations;
    synchronized (this) {
      if (context.baseSessionRevision != sessionRevision) {
        throw new IllegalStateException("session attempt is stale");
      }
      boolean sessionsChanged =
          context.sessions.size() != publishedConnections.size()
              || !context.connections.sameState(publishedConnectionRegistry);
      if (!sessionsChanged) {
        for (Map.Entry<Long, ConnectionState> entry : context.sessions.entrySet()) {
          ConnectionState published = publishedConnections.get(entry.getKey());
          if (published == null || !entry.getValue().sameState(published)) {
            sessionsChanged = true;
            break;
          }
        }
      }
      if (sessionsChanged) {
        publishedConnections.clear();
        context.sessions.forEach((id, state) -> publishedConnections.put(id, state.copy()));
        publishedConnectionRegistry.replaceWith(context.connections);
        sessionRevision = Math.incrementExact(sessionRevision);
      } else {
        context.sessions.forEach(
            (id, state) ->
                Objects.requireNonNull(
                        publishedConnections.get(id), "published connection")
                    .mergeLastActivity(state));
      }
      context.baseSessionRevision = sessionRevision;
      readRegistrations = List.copyOf(context.readRegistrations);
      context.readRegistrations.clear();
      connectionIo.registerReads(readRegistrations, publishedConnections.keySet());
      publishedEffects = List.copyOf(context.effects);
      context.effects.clear();
    }
    for (RuntimeEffect effect : publishedEffects) {
      publishEffect(effect, committedWorld, context.taskId);
    }
  }

  @Override
  public List<RuntimeStep> takeSpawnedSteps(AttemptContext context) {
    List<RuntimeStep> spawned = List.copyOf(context.spawnedSteps);
    context.spawnedSteps.clear();
    return spawned;
  }

  private void publishEffect(
      RuntimeEffect effect, WorldSnapshot publishedWorld, long currentTaskId) {
    switch (effect.kind) {
      case START_TIMEOUT -> {
        ConnectionState connection;
        synchronized (this) {
          connection = publishedConnections.get(effect.connectionId);
        }
        if (connection != null && connection.generation == effect.generation) {
          Thread.ofVirtual()
              .name("moo-connect-timeout-" + effect.connectionId)
              .start(() -> loginFlow.monitorUnauthenticatedConnection(effect.connectionId, connection));
        }
      }
      case WRITE ->
          listenerControl.ifPresent(
              control -> control.writeConnection(effect.connectionId, effect.lines));
      case NOTIFY ->
          listenerControl.ifPresent(
              control ->
                  control.notifyConnection(
                      effect.connectionId, effect.text, effect.noFlush, effect.noNewline));
      case BOOT ->
          listenerControl.ifPresent(
              control -> control.bootConnection(effect.connectionId, effect.lines));
      case BINARY ->
          listenerControl.ifPresent(
              control -> control.setConnectionBinary(effect.connectionId, effect.binary));
      case KEEP_ALIVE ->
          listenerControl.ifPresent(
              control -> control.setConnectionKeepAlive(effect.connectionId, effect.keepAlive));
      case INPUT -> {
        if (!connectionIo.deliverPendingRead(effect.connectionId, effect.text)) {
          scheduler.enqueueDetached(RuntimeRequest.line(effect.connectionId, effect.text));
        }
      }
      case UNMATCHED_INPUT -> scheduler.clearLastInputTask(effect.connectionId, currentTaskId);
      case CHECKPOINT -> {
        Path target =
            checkpoint.orElseThrow(
                () -> new IllegalStateException("dump_database() requires --checkpoint"));
        if (effect.binary && !effect.text.isEmpty()) {
          serverLog.info("SHUTDOWN: shutdown signal received");
        }
        serverLog.info("CHECKPOINTING " + (effect.text.isEmpty() ? "to " + target : effect.text));
        checkpointAttempted = true;
        try (WorldTxn.RetainedSnapshot retained = committedWorld.retainSnapshot()) {
          checkpointCodec.writeAtomic(
              target, retained.snapshot(), scheduler.durableTasksExcluding(currentTaskId), activeConnections());
          if (effect.binary) {
            listenerControl.ifPresent(ConnectionBuiltins.ListenerControl::shutdown);
          } else {
            scheduler.enqueueDetached(RuntimeRequest.checkpointFinished(true, false));
          }
        } catch (IOException error) {
          scheduler.enqueueDetached(RuntimeRequest.checkpointFinished(false, effect.binary));
          if (effect.binary) {
            throw new UncheckedIOException("checkpoint failed: " + target, error);
          }
          serverLog.error("CHECKPOINTING failed: " + target + ": " + error.getMessage());
        }
      }
      case REOPEN_LOG -> {
        serverLog.info("LOGFILE: Closing due to remote request signal.");
        serverLog.reopen();
        serverLog.info("LOGFILE: Reopening due to remote request signal.");
      }
      case PANIC -> {
        Path target =
            Path.of(
                checkpoint
                        .orElseThrow(
                            () -> new IllegalStateException("panic requires --checkpoint"))
                        .toString()
                    + ".PANIC");
        serverLog.error("PANIC: " + effect.text);
        serverLog.error("PANIC-DUMPING to " + target);
        try {
          checkpointCodec.writePanic(
              target,
              publishedWorld,
              scheduler.durableTasksExcluding(currentTaskId),
              activeConnections());
        } catch (IOException error) {
          throw new UncheckedIOException("panic dump failed: " + target, error);
        }
        listenerControl.orElseThrow().panic();
      }
      case SHUTDOWN -> listenerControl.ifPresent(ConnectionBuiltins.ListenerControl::shutdown);
    }
  }

  private synchronized List<ActiveConnection> activeConnections() {
    List<ActiveConnection> active = new ArrayList<>();
    for (Map.Entry<Long, ConnectionState> entry : publishedConnections.entrySet()) {
      long player =
          publishedConnectionRegistry.connectionPlayer(entry.getKey()).orElse(entry.getKey());
      active.add(new ActiveConnection(player, entry.getValue().listenerHandler));
    }
    active.sort(
        (left, right) -> {
          int playerOrder = Long.compare(left.player(), right.player());
          return playerOrder != 0
              ? playerOrder
              : Long.compare(left.listener(), right.listener());
        });
    return List.copyOf(active);
  }

  boolean switchConnectionPlayer(AttemptContext context, long connectionId, long playerId) {
    if (context.world.object(playerId).isEmpty()
        || !context.connections.switchConnectionPlayer(connectionId, playerId)) {
      return false;
    }
    requireConnection(context, connectionId).connectedNanos = OptionalLong.of(nanoTime.getAsLong());
    return true;
  }

  @Override
  public MooVm vm() {
    return vm;
  }

  @Override
  public BuiltinCatalog builtins() {
    return builtins;
  }

  ConnectionState requireConnection(AttemptContext context, long connectionId) {
    ConnectionState connection = context.sessions.get(connectionId);
    if (connection == null) {
      throw new IllegalArgumentException("unknown connection #" + connectionId);
    }
    return connection;
  }

  @FunctionalInterface
  public interface ConnectionNameResolver {
    String resolve(String address) throws UnknownHostException;
  }

  private record ConnectionNameResolution(String name, boolean resolved) {
    private ConnectionNameResolution {
      Objects.requireNonNull(name, "name");
    }
  }

  enum RuntimeTransition {
    SWITCH_PLAYER_NOTIFIER_RETURN,
    START_SCRIPT_RETURN,
    SIGNAL_RETURN,
    SERVER_STARTED_RETURN,
    CHECKPOINT_USER_DISCONNECTED_RETURN,
    CHECKPOINT_STARTED_RETURN,
    CHECKPOINT_FINISHED_RETURN,
    CLOSE_AFTER_USER_CLIENT_DISCONNECTED,
    LINE_OOB_RETURN_OUTPUT,
    LINE_DO_COMMAND_GATE_THEN_DISPATCH,
    LINE_SELECTED_COMMAND_APPEND_OUTPUT,
    TRANSPORT_OOB_RETURN_OUTPUT,
    LOGIN_BLANK_TRUTH_GATE,
    LOGIN_AUTHENTICATE_AND_ASSOCIATE,
    LOGIN_RECONNECTED_HOOK_THEN_RETURN,
    LOGIN_OLD_CLIENT_DISCONNECTED_THEN_ASSOCIATE,
    LOGIN_NEW_USER_CONNECTED_THEN_RETURN,
    LOGIN_USER_CREATED_THEN_CONNECTED,
    LOGIN_EXISTING_USER_CONNECTED_THEN_RETURN,
    LOGIN_TIMEOUT_USER_DISCONNECTED_THEN_BOOT,
    BOOT_PLAYER_USER_DISCONNECTED_THEN_BOOT,
    ANONYMOUS_FINALIZATION_RETURN,
    WAIF_FINALIZATION_RETURN,
    WAIF_BACKGROUND_FINALIZATION_RETURN,
    TASK_TIMEOUT_START,
    TASK_TIMEOUT_RETURN,
    UNCAUGHT_HANDLER_RETURN,
    LAG_HANDLER_RETURN
  }

  enum FinalizationKind {
    ANONYMOUS,
    WAIF
  }

  record FinalizationControl(FinalizationKind kind, MooValue target) {
    FinalizationControl {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(target, "target");
      boolean valid =
          switch (kind) {
            case ANONYMOUS -> target instanceof AnonymousObjectValue;
            case WAIF -> target instanceof WaifValue;
          };
      if (!valid) {
        throw new IllegalArgumentException(
            "finalization kind " + kind + " does not accept " + target.type());
      }
    }

    static FinalizationControl anonymous(AnonymousObjectValue target) {
      return new FinalizationControl(FinalizationKind.ANONYMOUS, target);
    }

    static FinalizationControl waif(WaifValue target) {
      return new FinalizationControl(FinalizationKind.WAIF, target);
    }
  }

  record RuntimeContinuation(
      Optional<RuntimeRequest> ingress,
      Optional<RuntimeTransition> transition,
      long connectionId,
      String text,
      List<String> output,
      long first,
      long second,
      boolean flag,
      Optional<VmSnapshot> timeoutSnapshot,
      Optional<FinalizationControl> finalizationControl) {
    RuntimeContinuation {
      Objects.requireNonNull(ingress, "ingress");
      Objects.requireNonNull(transition, "transition");
      Objects.requireNonNull(text, "text");
      Objects.requireNonNull(timeoutSnapshot, "timeoutSnapshot");
      Objects.requireNonNull(finalizationControl, "finalizationControl");
      output = List.copyOf(output);
      if (ingress.isPresent() == transition.isPresent()) {
        throw new IllegalArgumentException(
            "runtime continuation requires exactly one ingress or transition");
      }
      boolean finalizationTransition =
          transition
              .filter(
                  value ->
                      value == RuntimeTransition.ANONYMOUS_FINALIZATION_RETURN
                          || value == RuntimeTransition.WAIF_FINALIZATION_RETURN
                          || value == RuntimeTransition.WAIF_BACKGROUND_FINALIZATION_RETURN)
              .isPresent();
      if (finalizationTransition != finalizationControl.isPresent()) {
        throw new IllegalArgumentException(
            "finalization transitions require exactly one typed finalization control");
      }
      finalizationControl.ifPresent(
          control -> {
            boolean valid =
                switch (transition.orElseThrow()) {
                  case ANONYMOUS_FINALIZATION_RETURN ->
                      control.kind() == FinalizationKind.ANONYMOUS;
                  case WAIF_FINALIZATION_RETURN, WAIF_BACKGROUND_FINALIZATION_RETURN ->
                      control.kind() == FinalizationKind.WAIF;
                  default -> false;
                };
            if (!valid) {
              throw new IllegalArgumentException(
                  "runtime transition disagrees with typed finalization control");
            }
          });
    }

    static RuntimeContinuation ingress(RuntimeRequest request) {
      return new RuntimeContinuation(
          Optional.of(request),
          Optional.empty(),
          0,
          "",
          List.of(),
          0,
          0,
          false,
          Optional.empty(),
          Optional.empty());
    }

    static RuntimeContinuation after(
        RuntimeTransition transition,
        long connectionId,
        String text,
        List<String> output,
        long first,
        long second,
        boolean flag) {
      return new RuntimeContinuation(
          Optional.empty(),
          Optional.of(transition),
          connectionId,
          text,
          output,
          first,
          second,
          flag,
          Optional.empty(),
          Optional.empty());
    }

    static RuntimeContinuation timeout(
        VmSnapshot snapshot, long player, List<String> fallbackOutput) {
      return new RuntimeContinuation(
          Optional.empty(),
          Optional.of(RuntimeTransition.TASK_TIMEOUT_START),
          0,
          "",
          fallbackOutput,
          player,
          0,
          false,
          Optional.of(snapshot),
          Optional.empty());
    }

    static RuntimeContinuation timeoutReturn(List<String> fallbackOutput) {
      return new RuntimeContinuation(
          Optional.empty(),
          Optional.of(RuntimeTransition.TASK_TIMEOUT_RETURN),
          0,
          "",
          fallbackOutput,
          0,
          0,
          false,
          Optional.empty(),
          Optional.empty());
    }

    static RuntimeContinuation anonymousFinalization(AnonymousObjectValue target) {
      return new RuntimeContinuation(
          Optional.empty(),
          Optional.of(RuntimeTransition.ANONYMOUS_FINALIZATION_RETURN),
          0,
          "",
          List.of(),
          0,
          0,
          false,
          Optional.empty(),
          Optional.of(FinalizationControl.anonymous(target)));
    }

    static RuntimeContinuation waifFinalization(WaifValue target) {
      return new RuntimeContinuation(
          Optional.empty(),
          Optional.of(RuntimeTransition.WAIF_FINALIZATION_RETURN),
          0,
          "",
          List.of(),
          0,
          0,
          false,
          Optional.empty(),
          Optional.of(FinalizationControl.waif(target)));
    }

    static RuntimeContinuation backgroundWaifFinalization(WaifValue target) {
      return new RuntimeContinuation(
          Optional.empty(),
          Optional.of(RuntimeTransition.WAIF_BACKGROUND_FINALIZATION_RETURN),
          0,
          "",
          List.of(),
          0,
          0,
          false,
          Optional.empty(),
          Optional.of(FinalizationControl.waif(target)));
    }
  }

  record RuntimeStep(
      Optional<BytecodeProgram> program,
      Optional<world.mongoose.banteng.vm.VmSnapshot> snapshot,
      long taskPlayer,
      Optional<RuntimeContinuation> continuation,
      Optional<List<String>> output,
      Optional<LoginTimeoutResult> loginTimeout,
      Optional<Callable<BuiltinResult>> suspendedHostWork) {
    RuntimeStep {
      Objects.requireNonNull(program, "program");
      Objects.requireNonNull(snapshot, "snapshot");
      Objects.requireNonNull(continuation, "continuation");
      Objects.requireNonNull(loginTimeout, "loginTimeout");
      Objects.requireNonNull(suspendedHostWork, "suspendedHostWork");
      output = output.map(List::copyOf);
      boolean vm = program.isPresent() && snapshot.isPresent() && continuation.isPresent();
      boolean returned = output.isPresent();
      if (vm == returned || program.isPresent() != snapshot.isPresent()) {
        throw new IllegalArgumentException("runtime step requires VM work or returned output");
      }
    }

    static RuntimeStep vm(
        BytecodeProgram program,
        world.mongoose.banteng.vm.VmSnapshot snapshot,
        long taskPlayer,
        RuntimeContinuation continuation) {
      return new RuntimeStep(
          Optional.of(program),
          Optional.of(snapshot),
          taskPlayer,
          Optional.of(continuation),
          Optional.empty(), Optional.empty(), Optional.empty());
    }

    static RuntimeStep returned(List<String> output) {
      return new RuntimeStep(
          Optional.empty(), Optional.empty(), Long.MIN_VALUE, Optional.empty(), Optional.of(output),
          Optional.empty(), Optional.empty());
    }

    static RuntimeStep loginTimeout(LoginTimeoutResult result, List<String> output) {
      return new RuntimeStep(
          Optional.empty(), Optional.empty(), Long.MIN_VALUE, Optional.empty(), Optional.of(output),
          Optional.of(result), Optional.empty());
    }
  }

  static final class ConnectionState {
    final long listenerHandler;
    final boolean printMessages;
    final long generation;
    long lastActivityNanos;
    OptionalLong connectedNanos = OptionalLong.empty();
    long switched = 0;
    boolean switchNotifying;
    boolean holdInput;
    boolean disableOob;
    boolean binary;
    ConnectionBuiltins.KeepAliveOptions keepAlive = ConnectionBuiltins.KeepAliveOptions.defaults();
    Optional<String> flushCommand = Optional.empty();
    final List<String> pendingInput = new ArrayList<>();
    Optional<String> prefix = Optional.empty();
    Optional<String> suffix = Optional.empty();
    long programmingObject = -1;
    String programmingVerbName = "";
    final StringBuilder programmingSource = new StringBuilder();

    ConnectionState(
        long listenerHandler, boolean printMessages, long generation, long openedNanos) {
      this.listenerHandler = listenerHandler;
      this.printMessages = printMessages;
      this.generation = generation;
      lastActivityNanos = openedNanos;
    }

    ConnectionState copy() {
      ConnectionState copy =
          new ConnectionState(listenerHandler, printMessages, generation, lastActivityNanos);
      copy.lastActivityNanos = lastActivityNanos;
      copy.connectedNanos = connectedNanos;
      copy.switched = switched;
      copy.switchNotifying = switchNotifying;
      copy.holdInput = holdInput;
      copy.disableOob = disableOob;
      copy.binary = binary;
      copy.keepAlive = keepAlive;
      copy.flushCommand = flushCommand;
      copy.pendingInput.addAll(pendingInput);
      copy.prefix = prefix;
      copy.suffix = suffix;
      copy.programmingObject = programmingObject;
      copy.programmingVerbName = programmingVerbName;
      copy.programmingSource.append(programmingSource);
      return copy;
    }

    boolean sameState(ConnectionState other) {
      return listenerHandler == other.listenerHandler
          && printMessages == other.printMessages
          && generation == other.generation
          && connectedNanos.equals(other.connectedNanos)
          && switched == other.switched
          && switchNotifying == other.switchNotifying
          && holdInput == other.holdInput
          && disableOob == other.disableOob
          && binary == other.binary
          && keepAlive.equals(other.keepAlive)
          && flushCommand.equals(other.flushCommand)
          && pendingInput.equals(other.pendingInput)
          && prefix.equals(other.prefix)
          && suffix.equals(other.suffix)
          && programmingObject == other.programmingObject
          && programmingVerbName.equals(other.programmingVerbName)
          && programmingSource.toString().contentEquals(other.programmingSource);
    }

    void mergeLastActivity(ConnectionState source) {
      lastActivityNanos = Math.max(lastActivityNanos, source.lastActivityNanos);
    }
  }

  enum Operation {
    START_SCRIPT,
    SIGNAL,
    STARTUP,
    GRACEFUL_SHUTDOWN,
    CHECKPOINT_DISCONNECT,
    OPEN,
    CLOSE,
    LINE,
    OUT_OF_BAND,
    TIMEOUT,
    CHECKPOINT_FINISHED
  }

  record RuntimeRequest(
      Operation operation,
      long connectionId,
      long listenerHandler,
      boolean printMessages,
      MapValue connectionInfo,
      String text,
      long generation,
      ListValue arguments) {
    static RuntimeRequest startScript(ListValue arguments) {
      return new RuntimeRequest(Operation.START_SCRIPT, 0, 0, false, new MapValue(Map.of()), "", 0, arguments);
    }
    static RuntimeRequest startup() {
      return new RuntimeRequest(
          Operation.STARTUP, 0, 0, false, new MapValue(Map.of()), "", 0, new ListValue(List.of()));
    }

    static RuntimeRequest signal(int signal) {
      return new RuntimeRequest(
          Operation.SIGNAL, 0, 0, false, new MapValue(Map.of()), "", signal, new ListValue(List.of()));
    }

    static RuntimeRequest gracefulShutdown() {
      return new RuntimeRequest(
          Operation.GRACEFUL_SHUTDOWN, 0, 0, false, new MapValue(Map.of()), "", 0, new ListValue(List.of()));
    }

    static RuntimeRequest checkpointDisconnect(ActiveConnection connection) {
      return new RuntimeRequest(
          Operation.CHECKPOINT_DISCONNECT,
          connection.player(),
          connection.listener(),
          false,
          new MapValue(Map.of()),
          "",
          0, new ListValue(List.of()));
    }

    static RuntimeRequest open(
        long connectionId, long listenerHandler, boolean printMessages, MapValue connectionInfo) {
      return new RuntimeRequest(
          Operation.OPEN, connectionId, listenerHandler, printMessages, connectionInfo, "", 0, new ListValue(List.of()));
    }

    static RuntimeRequest close(long connectionId) {
      return new RuntimeRequest(
          Operation.CLOSE, connectionId, 0, false, new MapValue(Map.of()), "", 0, new ListValue(List.of()));
    }

    static RuntimeRequest line(long connectionId, String line) {
      return new RuntimeRequest(
          Operation.LINE, connectionId, 0, false, new MapValue(Map.of()), line, 0, new ListValue(List.of()));
    }

    static RuntimeRequest outOfBand(long connectionId, String command) {
      return new RuntimeRequest(
          Operation.OUT_OF_BAND, connectionId, 0, false, new MapValue(Map.of()), command, 0, new ListValue(List.of()));
    }

    static RuntimeRequest timeout(long connectionId, long generation) {
      return new RuntimeRequest(
          Operation.TIMEOUT, connectionId, 0, false, new MapValue(Map.of()), "", generation, new ListValue(List.of()));
    }

    static RuntimeRequest checkpointFinished(boolean success, boolean shutdown) {
      return new RuntimeRequest(
          Operation.CHECKPOINT_FINISHED,
          0,
          0,
          success,
          new MapValue(Map.of()),
          "",
          shutdown ? 1 : 0, new ListValue(List.of()));
    }
  }

  static final class AttemptContext {
    WorldTxn world;
    final long taskId;
    final Map<Long, ConnectionState> sessions;
    final ConnectionRegistryAccess connections;
    long baseSessionRevision;
    final List<RuntimeEffect> effects;
    final List<RuntimeStep> spawnedSteps;
    final List<ReadRegistration> readRegistrations;

    AttemptContext(
        WorldTxn world,
        long taskId,
        Map<Long, ConnectionState> sessions,
        ConnectionRegistryAccess connections,
        long baseSessionRevision,
        List<RuntimeEffect> effects,
        List<RuntimeStep> spawnedSteps,
        List<ReadRegistration> readRegistrations) {
      this.world = world;
      this.taskId = taskId;
      this.sessions = sessions;
      this.connections = connections;
      this.baseSessionRevision = baseSessionRevision;
      this.effects = effects;
      this.spawnedSteps = spawnedSteps;
      this.readRegistrations = readRegistrations;
    }
  }

  record ReadRegistration(
      long connectionId, CompletableFuture<BuiltinResult> completion) {}

  enum RuntimeEffectKind {
    REOPEN_LOG,
    START_TIMEOUT,
    WRITE,
    NOTIFY,
    BOOT,
    BINARY,
    KEEP_ALIVE,
    INPUT,
    UNMATCHED_INPUT,
    CHECKPOINT,
    PANIC,
    SHUTDOWN
  }

  record RuntimeEffect(
      RuntimeEffectKind kind,
      long connectionId,
      List<String> lines,
      boolean binary,
      boolean noFlush,
      long generation,
      String text,
      boolean noNewline,
      ConnectionBuiltins.KeepAliveOptions keepAlive) {
    RuntimeEffect {
      lines = List.copyOf(lines);
      Objects.requireNonNull(keepAlive, "keepAlive");
    }

    RuntimeEffect(
        RuntimeEffectKind kind,
        long connectionId,
        List<String> lines,
        boolean binary,
        boolean noFlush,
        long generation,
        String text,
        boolean noNewline) {
      this(
          kind,
          connectionId,
          lines,
          binary,
          noFlush,
          generation,
          text,
          noNewline,
          ConnectionBuiltins.KeepAliveOptions.defaults());
    }

    static RuntimeEffect startTimeout(long connectionId, long generation) {
      return new RuntimeEffect(
          RuntimeEffectKind.START_TIMEOUT,
          connectionId,
          List.of(),
          false,
          false,
          generation,
          "",
          false);
    }

    static RuntimeEffect write(long connectionId, List<String> lines) {
      return new RuntimeEffect(
          RuntimeEffectKind.WRITE, connectionId, lines, false, false, 0, "", false);
    }

    static RuntimeEffect notify(
        long connectionId, String line, boolean noFlush, boolean noNewline) {
      return new RuntimeEffect(
          RuntimeEffectKind.NOTIFY,
          connectionId,
          List.of(),
          false,
          noFlush,
          0,
          line,
          noNewline);
    }

    static RuntimeEffect boot(long connectionId, List<String> lines) {
      return new RuntimeEffect(
          RuntimeEffectKind.BOOT, connectionId, lines, false, false, 0, "", false);
    }

    static RuntimeEffect binary(long connectionId, boolean binary) {
      return new RuntimeEffect(
          RuntimeEffectKind.BINARY, connectionId, List.of(), binary, false, 0, "", false);
    }

    static RuntimeEffect keepAlive(long connectionId, ConnectionBuiltins.KeepAliveOptions options) {
      return new RuntimeEffect(
          RuntimeEffectKind.KEEP_ALIVE,
          connectionId,
          List.of(),
          false,
          false,
          0,
          "",
          false,
          options);
    }

    static RuntimeEffect input(long connectionId, String line) {
      return new RuntimeEffect(
          RuntimeEffectKind.INPUT, connectionId, List.of(), false, false, 0, line, false);
    }

    static RuntimeEffect unmatchedInput(long player) {
      return new RuntimeEffect(
          RuntimeEffectKind.UNMATCHED_INPUT, player, List.of(), false, false, 0, "", false);
    }

    static RuntimeEffect checkpoint(boolean shutdown) {
      return checkpoint(shutdown, "");
    }

    static RuntimeEffect checkpoint(boolean shutdown, String reason) {
      return new RuntimeEffect(
          RuntimeEffectKind.CHECKPOINT, 0, List.of(), shutdown, false, 0, reason, false);
    }

    static RuntimeEffect reopenLog() {
      return new RuntimeEffect(
          RuntimeEffectKind.REOPEN_LOG, 0, List.of(), false, false, 0, "", false);
    }

    static RuntimeEffect panic(String message) {
      return new RuntimeEffect(
          RuntimeEffectKind.PANIC, 0, List.of(), false, false, 0, message, false);
    }

    static RuntimeEffect shutdown() {
      return new RuntimeEffect(
          RuntimeEffectKind.SHUTDOWN, 0, List.of(), false, false, 0, "", false);
    }
  }
}
