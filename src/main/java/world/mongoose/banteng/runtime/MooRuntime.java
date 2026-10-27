package world.mongoose.banteng.runtime;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import world.mongoose.banteng.builtin.BuiltinCall;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinCatalog.ConnectionOption;
import world.mongoose.banteng.builtin.BuiltinCatalog.ConnectionOptionRequest;
import world.mongoose.banteng.builtin.BuiltinCatalog.ForcedInputRequest;
import world.mongoose.banteng.builtin.BuiltinCatalog.KeepAliveOptions;
import world.mongoose.banteng.builtin.BuiltinCatalog.NotificationRequest;
import world.mongoose.banteng.builtin.BuiltinCatalog.ListenerControl;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.CheckpointRequest;
import world.mongoose.banteng.builtin.ConnectionRegistryAccess;
import world.mongoose.banteng.bytecode.BytecodeProgram;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.persistence.LambdaMooV17Codec;
import world.mongoose.banteng.persistence.LambdaMooV17Codec.ActiveConnection;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.BooleanValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import world.mongoose.banteng.value.ValueSemantics;
import world.mongoose.banteng.world.Access;
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
  private final ServerLog serverLog;
  private final Optional<ListenerControl> listenerControl;
  private final Optional<Path> database;
  private final Optional<Path> checkpoint;
  private final WorldTxn committedWorld;
  @GuardedBy("this")
  private final ConnectionRegistryAccess publishedConnectionRegistry;
  private final LambdaMooV17Codec checkpointCodec = new LambdaMooV17Codec();
  private final LongSupplier nanoTime;
  private final ConnectionNameResolver connectionNameResolver;
  private final MooVm vm;
  private final PublicationScheduler scheduler;
  private final List<ActiveConnection> checkpointedConnections;
  @GuardedBy("this")
  private final Map<Long, ConnectionState> publishedConnections = new LinkedHashMap<>();
  private static final ThreadLocal<AttemptContext> ATTEMPT = new ThreadLocal<>();
  private final AtomicLong connectionGenerations = new AtomicLong();
  private volatile boolean checkpointAttempted;
  @GuardedBy("this")
  private long sessionRevision;

  /** Creates a runtime over the one concrete world transaction. */
  public MooRuntime(WorldTxn world, ConnectionRegistryAccess connections) {
    this(world, ValueSemantics.STANDARD, connections);
  }

  /** Creates a runtime over one world transaction with selected value semantics. */
  public MooRuntime(
      WorldTxn world,
      ValueSemantics valueSemantics,
      ConnectionRegistryAccess connections) {
    this(
        world,
        valueSemantics,
        connections,
        System::nanoTime,
        MooRuntime::resolveCanonicalConnectionName);
  }

  MooRuntime(WorldTxn world, ConnectionRegistryAccess connections, LongSupplier nanoTime) {
    this(
        world,
        ValueSemantics.STANDARD,
        connections,
        nanoTime,
        MooRuntime::resolveCanonicalConnectionName);
  }

  MooRuntime(
      WorldTxn world,
      ConnectionRegistryAccess connections,
      ConnectionNameResolver connectionNameResolver) {
    this(
        world,
        ValueSemantics.STANDARD,
        connections,
        System::nanoTime,
        connectionNameResolver);
  }

  private MooRuntime(
      WorldTxn world,
      ValueSemantics valueSemantics,
      ConnectionRegistryAccess connections,
      LongSupplier nanoTime,
      ConnectionNameResolver connectionNameResolver) {
    committedWorld = Objects.requireNonNull(world, "world");
    publishedConnectionRegistry = Objects.requireNonNull(connections, "connections");
    this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    this.connectionNameResolver =
        Objects.requireNonNull(connectionNameResolver, "connectionNameResolver");
    ValueSemantics configuredSemantics =
        Objects.requireNonNull(valueSemantics, "valueSemantics");
    vm = new MooVm(configuredSemantics);
    listenerControl = Optional.empty();
    database = Optional.empty();
    checkpoint = Optional.empty();
    serverLog = ServerLog.stderr(System.Logger.Level.INFO);
    TaskRegistry taskRegistry = new TaskRegistry();
    scheduler =
        new PublicationScheduler(
            committedWorld, this, taskRegistry);
    checkpointedConnections = List.of();
    loginFlow = new LoginFlow(this, scheduler, listenerControl);
    connectionIo = new ConnectionIo(this, scheduler, listenerControl);
    builtins = new BuiltinCatalog(builtinHosts(configuredSemantics, taskRegistry));
    try (WorldTxn initialOptions = committedWorld.begin()) {
      builtins.reloadProtectedFunctions(initialOptions);
    }
  }

  /** Creates the production runtime with its concrete listener and checkpoint owners. */
  public MooRuntime(
      WorldTxn world,
      ListenerControl listenerControl,
      Path checkpoint,
      ConnectionRegistryAccess connections) {
    this(
        world,
        listenerControl,
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        Optional.empty(),
        Optional.of(Objects.requireNonNull(checkpoint, "checkpoint")),
        List.of(),
        List.of(),
        ServerLog.stderr(System.Logger.Level.INFO),
        ValueSemantics.STANDARD,
        connections);
  }

  /** Creates the production runtime and restores delayed fork tasks from one checkpoint. */
  public MooRuntime(
      WorldTxn world,
      ListenerControl listenerControl,
      Path checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<ActiveConnection> activeConnections,
      ConnectionRegistryAccess connections) {
    this(
        world,
        listenerControl,
        checkpoint,
        restoredTasks,
        activeConnections,
        ValueSemantics.STANDARD,
        connections);
  }

  /** Creates the production runtime from one checkpoint with selected value semantics. */
  public MooRuntime(
      WorldTxn world,
      ListenerControl listenerControl,
      Path checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<ActiveConnection> activeConnections,
      ValueSemantics valueSemantics,
      ConnectionRegistryAccess connections) {
    this(
        world,
        listenerControl,
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        Optional.empty(),
        Optional.of(Objects.requireNonNull(checkpoint, "checkpoint")),
        restoredTasks,
        activeConnections,
        ServerLog.stderr(System.Logger.Level.INFO),
        valueSemantics,
        connections);
  }

  /** Creates the production runtime with restored state and the shared server log. */
  public MooRuntime(
      WorldTxn world,
      ListenerControl listenerControl,
      Path checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<ActiveConnection> activeConnections,
      ServerLog serverLog,
      ConnectionRegistryAccess connections) {
    this(
        world,
        listenerControl,
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        Optional.empty(),
        Optional.of(Objects.requireNonNull(checkpoint, "checkpoint")),
        restoredTasks,
        activeConnections,
        serverLog,
        ValueSemantics.STANDARD,
        connections);
  }

  /** Creates the production runtime with restored state, shared log, and value semantics. */
  public MooRuntime(
      WorldTxn world,
      ListenerControl listenerControl,
      Path checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<ActiveConnection> activeConnections,
      ServerLog serverLog,
      ValueSemantics valueSemantics,
      ConnectionRegistryAccess connections) {
    this(
        world,
        listenerControl,
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        Optional.empty(),
        Optional.of(Objects.requireNonNull(checkpoint, "checkpoint")),
        restoredTasks,
        activeConnections,
        serverLog,
        valueSemantics,
        connections);
  }

  /** Creates the production runtime with distinct loaded and checkpoint database files. */
  public MooRuntime(
      WorldTxn world,
      ListenerControl listenerControl,
      Path database,
      Path checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<ActiveConnection> activeConnections,
      ConnectionRegistryAccess connections) {
    this(
        world,
        listenerControl,
        database,
        checkpoint,
        restoredTasks,
        activeConnections,
        ValueSemantics.STANDARD,
        connections);
  }

  /** Creates the production runtime with selected value semantics. */
  public MooRuntime(
      WorldTxn world,
      ListenerControl listenerControl,
      Path database,
      Path checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<ActiveConnection> activeConnections,
      ValueSemantics valueSemantics,
      ConnectionRegistryAccess connections) {
    this(
        world,
        listenerControl,
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        Optional.of(Objects.requireNonNull(database, "database")),
        Optional.of(Objects.requireNonNull(checkpoint, "checkpoint")),
        restoredTasks,
        activeConnections,
        ServerLog.stderr(System.Logger.Level.INFO),
        valueSemantics,
        connections);
  }

  /** Creates the production runtime with distinct database files and the shared server log. */
  public MooRuntime(
      WorldTxn world,
      ListenerControl listenerControl,
      Path database,
      Path checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<ActiveConnection> activeConnections,
      ServerLog serverLog,
      ConnectionRegistryAccess connections) {
    this(
        world,
        listenerControl,
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        Optional.of(Objects.requireNonNull(database, "database")),
        Optional.of(Objects.requireNonNull(checkpoint, "checkpoint")),
        restoredTasks,
        activeConnections,
        serverLog,
        ValueSemantics.STANDARD,
        connections);
  }

  /** Creates the production runtime with database files, shared log, and value semantics. */
  public MooRuntime(
      WorldTxn world,
      ListenerControl listenerControl,
      Path database,
      Path checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<ActiveConnection> activeConnections,
      ServerLog serverLog,
      ValueSemantics valueSemantics,
      ConnectionRegistryAccess connections) {
    this(
        world,
        listenerControl,
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        Optional.of(Objects.requireNonNull(database, "database")),
        Optional.of(Objects.requireNonNull(checkpoint, "checkpoint")),
        restoredTasks,
        activeConnections,
        serverLog,
        valueSemantics,
        connections);
  }

  MooRuntime(
      WorldTxn world,
      ListenerControl listenerControl,
      int workers,
      ConnectionRegistryAccess connections) {
    this(
        world,
        listenerControl,
        workers,
        Optional.empty(),
        Optional.empty(),
        List.of(),
        List.of(),
        ServerLog.stderr(System.Logger.Level.INFO),
        ValueSemantics.STANDARD,
        connections);
  }

  private MooRuntime(
      WorldTxn world,
      ListenerControl listenerControl,
      int workers,
      Optional<Path> database,
      Optional<Path> checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<ActiveConnection> activeConnections,
      ServerLog serverLog,
      ValueSemantics valueSemantics,
      ConnectionRegistryAccess connections) {
    committedWorld = Objects.requireNonNull(world, "world");
    publishedConnectionRegistry = Objects.requireNonNull(connections, "connections");
    nanoTime = System::nanoTime;
    connectionNameResolver = MooRuntime::resolveCanonicalConnectionName;
    ValueSemantics configuredSemantics =
        Objects.requireNonNull(valueSemantics, "valueSemantics");
    vm = new MooVm(configuredSemantics);
    this.listenerControl = Optional.of(Objects.requireNonNull(listenerControl, "listenerControl"));
    this.database = Objects.requireNonNull(database, "database");
    this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
    this.serverLog = Objects.requireNonNull(serverLog, "serverLog");
    checkpointedConnections = List.copyOf(activeConnections);
    TaskRegistry taskRegistry = new TaskRegistry();
    scheduler =
        new PublicationScheduler(
            committedWorld,
            this,
            workers,
            taskRegistry);
    loginFlow = new LoginFlow(this, scheduler, this.listenerControl);
    connectionIo = new ConnectionIo(this, scheduler, this.listenerControl);
    builtins =
        new BuiltinCatalog(
            listenerControl, builtinHosts(configuredSemantics, taskRegistry));
    try (WorldTxn initialOptions = committedWorld.begin()) {
      builtins.reloadProtectedFunctions(initialOptions);
    }
    scheduler.restoreTasks(Objects.requireNonNull(restoredTasks, "restoredTasks"));
  }

  private BuiltinHosts builtinHosts(ValueSemantics valueSemantics, TaskRegistry taskRegistry) {
    return BuiltinHosts.builder()
        .valueSemantics(valueSemantics)
        .queuedTasks(taskRegistry::queuedTasks)
        .killTask(taskRegistry::killTask)
        .read(call -> connectionIo.read(requireAttempt(), call))
        .threadPool(scheduler::threadPool)
        .threads(taskRegistry::threads)
        .connectionOptions(
            call -> connectionIo.connectionOptions(requireAttempt(), call.arguments(), call.world(), call.programmer()))
        .idleSeconds(call -> connectionIo.connectionSeconds(requireAttempt(), call, false))
        .connectedSeconds(call -> connectionIo.connectionSeconds(requireAttempt(), call, true))
        .setConnectionOption((target, option, value) ->
            connectionIo.applyConnectionOption(requireAttempt(), target, option, value))
        .connectionNameLookup(this::connectionNameLookup)
        .dbDiskSize(call -> dbDiskSize())
        .flushInput(call -> connectionIo.flushInput(requireAttempt(), call.arguments(), call.world(), call.programmer()))
        .outputDelimiters(
            call -> connectionIo.outputDelimiters(requireAttempt(), call.arguments(), call.world(), call.programmer()))
        .queueInfo(
            call -> connectionIo.queueInfo(requireAttempt(), call.arguments(), call.world(), call.programmer(), taskRegistry))
        .taskStack(taskRegistry::taskStack)
        .resumeTask(scheduler::resumeTask)
        .serverLog(serverLog)
        .connections(this::connectionRegistry)
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

  /** Publishes the final shutdown checkpoint without checkpoint notification hooks. */
  public void requestGracefulShutdown() {
    scheduler.submit(RuntimeRequest.gracefulShutdown());
  }

  /** Delivers a process signal through the same scheduler as server tasks. */
  public void handleSignal(int signal) {
    scheduler.enqueueDetached(RuntimeRequest.signal(signal));
  }

  private RuntimeStep signalNow(int number) {
    if (number == 14) {
      return signalDefault("SIGALRM");
    }
    if (number != 10 && number != 12) {
      for (long connectionId : connections().keySet()) {
        effects().add(RuntimeEffect.write(connectionId,
            List.of("*** Shutting down: server panic ***")));
      }
      effects().add(RuntimeEffect.panic("Caught signal " + number));
      return RuntimeStep.returned(List.of());
    }
    String signal = number == 10 ? "SIGUSR1" : "SIGUSR2";
    ResolvedVerb handler = world().resolveVerb(0, "handle_signal", true).orElse(null);
    if (handler == null) {
      return signalDefault(signal);
    }
    return startStored(
        handler,
        new RootVerbCall(0, -1, -1, "handle_signal",
            new ListValue(List.of(StringValue.of(signal))), ""),
        RuntimeContinuation.after(
            RuntimeTransition.SIGNAL_RETURN, 0, signal, List.of(), 0, 0, false));
  }

  private RuntimeStep signalDefault(String signal) {
    if (signal.equals("SIGUSR1")) {
      effects().add(RuntimeEffect.reopenLog());
      return RuntimeStep.returned(List.of());
    }
    ResolvedVerb started = world().resolveVerb(0, "checkpoint_started", true).orElse(null);
    String reason = signal.equals("SIGALRM") ? "due to timer." : "due to remote request signal.";
    if (started == null) {
      effects().add(RuntimeEffect.checkpoint(false, reason));
      return RuntimeStep.returned(List.of());
    }
    return startStored(
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

  private RuntimeStep checkpointDisconnectNow(long player, long listener) {
    ResolvedVerb disconnected = world().resolveVerb(listener, "user_disconnected", true).orElse(null);
    if (disconnected == null) {
      return RuntimeStep.returned(List.of());
    }
    return startStored(
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

  private RuntimeStep startServerNow() {
    return continueStartupAfterPendingWaifFinalization();
  }

  private RuntimeStep gracefulShutdownNow() {
    for (long connectionId : connections().keySet()) {
      effects().add(RuntimeEffect.write(connectionId,
          List.of("*** Shutting down: shutdown signal received ***")));
    }
    effects().add(RuntimeEffect.checkpoint(true, "shutdown signal received"));
    return RuntimeStep.returned(List.of());
  }

  private RuntimeStep continueStartupAfterPendingWaifFinalization() {
    for (MooValue pending : world().pendingFinalization()) {
      if (!(pending instanceof WaifValue waif)) {
        continue;
      }
      ResolvedVerb recycle = world().resolveVerb(waif.classObject().value(), ":recycle", true).orElse(null);
      if (recycle == null) {
        continue;
      }
      RootVerbCall locals = new RootVerbCall(waif, -1, -1, ":recycle",
          new ListValue(List.of()), "", Optional.empty());
      return startStored(recycle, locals, RuntimeContinuation.waifFinalization(waif));
    }
    ResolvedVerb started = world().resolveVerb(0, "server_started", true).orElse(null);
    if (started == null) {
      return RuntimeStep.returned(List.of());
    }
    return startStored(
        started,
        new RootVerbCall(0, -1, -1, "server_started", new ListValue(List.of()), ""),
        RuntimeContinuation.after(
            RuntimeTransition.SERVER_STARTED_RETURN, 0, "", List.of(), 0, 0, false));
  }

  private RuntimeStep checkpointFinishedNow(boolean success, boolean shutdown) {
    ResolvedVerb finished = world().resolveVerb(0, "checkpoint_finished", true).orElse(null);
    if (finished == null) {
      if (shutdown) {
        effects().add(RuntimeEffect.shutdown());
      }
      return RuntimeStep.returned(List.of());
    }
    ListValue arguments =
        new ListValue(List.of(new IntegerValue(success ? 1 : 0)));
    return startStored(
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

  private RuntimeStep openConnectionNow(
      long connectionId, long listenerHandler, boolean printMessages, MapValue connectionInfo) {
    connectionRegistry().openConnection(connectionId, connectionInfo);
    ConnectionState connection =
        new ConnectionState(
            listenerHandler,
            printMessages,
            connectionGenerations.incrementAndGet(),
            nanoTime.getAsLong());
    connections().put(connectionId, connection);
    try {
      effects().add(RuntimeEffect.startTimeout(connectionId, connection.generation));
      return loginFlow.executeLogin(requireAttempt(), connectionId, "");
    } catch (RuntimeException | Error failure) {
      connections().remove(connectionId);
      connectionRegistry().closeConnection(connectionId);
      throw failure;
    }
  }

  /** Registers an irrevocably opened outbound socket in the current scheduler attempt. */
  public void registerOutboundConnection(
      long connectionId, long listenerHandler, MapValue connectionInfo) {
    connectionRegistry().openConnection(connectionId, connectionInfo);
    ConnectionState connection =
        new ConnectionState(
            listenerHandler, false, connectionGenerations.incrementAndGet(), nanoTime.getAsLong());
    if (connections().putIfAbsent(connectionId, connection) != null) {
      connectionRegistry().closeConnection(connectionId);
      throw new IllegalArgumentException("duplicate connection #" + connectionId);
    }
  }

  /** Removes a connection and its intrinsic delimiter state. */
  public void closeConnection(long connectionId) {
    scheduler.submit(RuntimeRequest.close(connectionId));
  }

  private RuntimeStep closeConnectionNow(long connectionId) {
    ConnectionState connection = connections().get(connectionId);
    OptionalLong disconnectedPlayer = connectionRegistry().connectionPlayer(connectionId);
    connections().remove(connectionId);
    connectionRegistry().closeConnection(connectionId);
    if (connection != null
        && disconnectedPlayer.isPresent()
        && disconnectedPlayer.orElseThrow() >= 0) {
      long player = disconnectedPlayer.orElseThrow();
      Optional<ResolvedVerb> disconnected =
          world().resolveVerb(connection.listenerHandler, "user_client_disconnected", true);
      if (disconnected.isPresent()) {
        return startStored(
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
  public Optional<List<String>> suspendedInputOutput(RuntimeContinuation continuation, VmState state) {
    RuntimeTransition transition = continuation.transition().orElseThrow();
    boolean command = transition == RuntimeTransition.LINE_DO_COMMAND_GATE_THEN_DISPATCH
        || transition == RuntimeTransition.LINE_SELECTED_COMMAND_APPEND_OUTPUT;
    if (!command && transition != RuntimeTransition.LOGIN_AUTHENTICATE_AND_ASSOCIATE
        && transition != RuntimeTransition.LOGIN_BLANK_TRUTH_GATE
        && transition != RuntimeTransition.TRANSPORT_OOB_RETURN_OUTPUT
        && transition != RuntimeTransition.LINE_OOB_RETURN_OUTPUT) {
      return Optional.empty();
    }
    List<String> output = new ArrayList<>(continuation.output());
    ConnectionState connection = connections().get(continuation.connectionId());
    if (connection != null && transition == RuntimeTransition.LINE_DO_COMMAND_GATE_THEN_DISPATCH) {
      connection.prefix.ifPresent(output::add);
    }
    output.addAll(state.output());
    state.clearOutput();
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

  private RuntimeStep executeLineNow(long connectionId, String line) {
    return executeLineNow(connectionId, line, Optional.empty());
  }

  private RuntimeStep executeLineNow(
      long connectionId, String line, Optional<VmState> completedDoCommand) {
    Objects.requireNonNull(line, "line");
    ConnectionState connection = requireConnection(connectionId);
    connection.lastActivityNanos = nanoTime.getAsLong();
    if (!connection.disableOob && line.startsWith("#$#")) {
      return executeTransportOutOfBandNow(connectionId, line);
    }
    if (!connection.disableOob && line.startsWith("#$\"")) {
      line = line.substring(3);
    }
    if (connectionIo.hasPendingRead(connectionId)) {
      effects().add(RuntimeEffect.input(connectionId, line));
      return RuntimeStep.returned(List.of());
    }
    long player = connectionRegistry().connectionPlayer(connectionId).orElseThrow();
    if (player < 0) {
      return loginFlow.executeLogin(requireAttempt(), connectionId, line);
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

    RuntimeStep programming = executeProgrammingOrOutputControl(connection, player, line);
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
    Optional<ResolvedVerb> doCommand = world().resolveVerb(connection.listenerHandler, "do_command", true);
    if (completedDoCommand.isPresent()) {
      VmState state = completedDoCommand.orElseThrow();
      doCommandOutput = state.output();
      handled = state.returnValue().isPresent() && state.returnValue().orElseThrow().isTruthy();
    } else if (doCommand.isPresent()) {
      return startStored(
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
    return dispatchCommand(connectionId, connection, player, line, output);
  }

  private @Nullable RuntimeStep executeProgrammingOrOutputControl(
      ConnectionState connection, long player, String line) {
    if (connection.programmingObject >= 0) {
      if (!line.equals(".")) {
        connection.programmingSource.append(line).append('\n');
        return RuntimeStep.returned(List.of());
      }
      WorldObject definingObject = world().object(connection.programmingObject).orElse(null);
      if (definingObject == null) {
        clearProgramming(connection);
        return RuntimeStep.returned(List.of("That object appears to have disappeared ..."));
      }
      WorldVerb verb =
          world()
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
              && world()
                  .setVerbCode(connection.programmingObject, verbIndex, source, compilation.program().orElseThrow())
                  .isOk();
      output.add(installed ? "Verb programmed." : "Verb not programmed.");
      clearProgramming(connection);
      return RuntimeStep.returned(output);
    }

    CommandParser.ParsedCommand programmingCommand = CommandParser.parse(line);
    List<String> programmingWords = programmingCommand.words();
    boolean programmer =
        world().object(player).map(object -> ObjectFlags.isProgrammer(object.flags())).orElse(false);
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
      long objectId = matchProgramObject(player, objectReference);
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
      OptionalLong definingObject = world().verbLocation(objectId, verbName, false);
      if (definingObject.isEmpty()) {
        return RuntimeStep.returned(
            List.of("That object does not have that verb definition."));
      }
      WorldObject object = world().object(definingObject.orElseThrow()).orElseThrow();
      WorldObject requestedObject = world().object(objectId).orElseThrow();
      WorldVerb verb = world().verb(object.id(), verbName, false).orElseThrow();
      int verbIndex = object.verbs().indexOf(verb);
      if (verbIndex < 0) {
        return RuntimeStep.returned(
            List.of("That object does not have that verb definition."));
      }
      if (!Access.canWrite(world(), player, verb.owner(), verb.perms())) {
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

  private RuntimeStep dispatchCommand(
      long connectionId, ConnectionState connection, long player, String line, List<String> output) {
    CommandParser.ParsedCommand parsed = CommandParser.parse(line);
    List<String> words = parsed.words();
    if (!words.isEmpty()) {
      long room = world().object(player).orElseThrow().location();
      Optional<ResolvedVerb> playerCommandVerb = world().resolveVerb(player, words.getFirst(), false);
      Optional<ResolvedVerb> roomCommandVerb = world().resolveVerb(room, words.getFirst(), false);
      long huhReceiver = room;
      MooValue serverOptions = world().readObjectProperty(0, "server_options").orElse(null);
      if (serverOptions instanceof ObjectValue options
          && world().readObjectProperty(options.value(), "player_huh").orElse(null)
              instanceof IntegerValue playerHuh
          && playerHuh.value() != 0) {
        huhReceiver = player;
      }
      Optional<ResolvedVerb> huhVerb = world().resolveVerb(huhReceiver, "huh", true);
      {
        List<MooValue> arguments = new ArrayList<>();
        for (int index = 1; index < words.size(); index++) {
          arguments.add(StringValue.of(words.get(index)));
        }
        int preposition = parsed.preposition();
        String directObjectString = parsed.dobjstr();
        String prepositionString = parsed.prepstr();
        String indirectObjectString = parsed.iobjstr();
        long directObject = CommandParser.matchObject(player, directObjectString, world());
        long indirectObject = CommandParser.matchObject(player, indirectObjectString, world());
        ResolvedVerb selectedVerb = null;
        long thisObject = -1;
        if (playerCommandVerb.isPresent()) {
          ResolvedVerb candidate = playerCommandVerb.orElseThrow();
          ArgSpec directSpecification = candidate.verb().dobj();
          ArgSpec indirectSpecification = candidate.verb().iobj();
          ArgSpec directClassification = directObject == player ? ArgSpec.THIS : directObject == -1 ? ArgSpec.NONE : ArgSpec.ANY;
          ArgSpec indirectClassification = indirectObject == player ? ArgSpec.THIS : indirectObject == -1 ? ArgSpec.NONE : ArgSpec.ANY;
          boolean argumentSpecificationMatches =
              (directSpecification == ArgSpec.ANY || directSpecification == directClassification)
                  && (candidate.verb().preposition() == -2 || candidate.verb().preposition() == preposition)
                  && (indirectSpecification == ArgSpec.ANY
                      || indirectSpecification == indirectClassification);
          if (argumentSpecificationMatches) {
            selectedVerb = candidate;
            thisObject = player;
          }
        }
        if (selectedVerb == null && roomCommandVerb.isPresent()) {
          ResolvedVerb candidate = roomCommandVerb.orElseThrow();
          ArgSpec directSpecification = candidate.verb().dobj();
          ArgSpec indirectSpecification = candidate.verb().iobj();
          ArgSpec directClassification = directObject == room ? ArgSpec.THIS : directObject == -1 ? ArgSpec.NONE : ArgSpec.ANY;
          ArgSpec indirectClassification = indirectObject == room ? ArgSpec.THIS : indirectObject == -1 ? ArgSpec.NONE : ArgSpec.ANY;
          boolean argumentSpecificationMatches =
              (directSpecification == ArgSpec.ANY || directSpecification == directClassification)
                  && (candidate.verb().preposition() == -2 || candidate.verb().preposition() == preposition)
                  && (indirectSpecification == ArgSpec.ANY
                      || indirectSpecification == indirectClassification);
          if (argumentSpecificationMatches) {
            selectedVerb = candidate;
            thisObject = room;
          }
        }
        if (selectedVerb == null && directObject >= 0) {
          Optional<ResolvedVerb> directCommandVerb =
              world().resolveVerb(directObject, words.getFirst(), false);
          if (directCommandVerb.isPresent()) {
            ResolvedVerb candidate = directCommandVerb.orElseThrow();
            ArgSpec directSpecification = candidate.verb().dobj();
            ArgSpec indirectSpecification = candidate.verb().iobj();
            ArgSpec directClassification = ArgSpec.THIS;
            ArgSpec indirectClassification =
                indirectObject == directObject ? ArgSpec.THIS : indirectObject == -1 ? ArgSpec.NONE : ArgSpec.ANY;
            boolean argumentSpecificationMatches =
                (directSpecification == ArgSpec.ANY || directSpecification == directClassification)
                    && (candidate.verb().preposition() == -2 || candidate.verb().preposition() == preposition)
                    && (indirectSpecification == ArgSpec.ANY
                        || indirectSpecification == indirectClassification);
            if (argumentSpecificationMatches) {
              selectedVerb = candidate;
              thisObject = directObject;
            }
          }
        }
        if (selectedVerb == null && indirectObject >= 0) {
          Optional<ResolvedVerb> indirectCommandVerb =
              world().resolveVerb(indirectObject, words.getFirst(), false);
          if (indirectCommandVerb.isPresent()) {
            ResolvedVerb candidate = indirectCommandVerb.orElseThrow();
            ArgSpec directSpecification = candidate.verb().dobj();
            ArgSpec indirectSpecification = candidate.verb().iobj();
            ArgSpec directClassification =
                directObject == indirectObject ? ArgSpec.THIS : directObject == -1 ? ArgSpec.NONE : ArgSpec.ANY;
            ArgSpec indirectClassification = ArgSpec.THIS;
            boolean argumentSpecificationMatches =
                (directSpecification == ArgSpec.ANY || directSpecification == directClassification)
                    && (candidate.verb().preposition() == -2 || candidate.verb().preposition() == preposition)
                    && (indirectSpecification == ArgSpec.ANY
                        || indirectSpecification == indirectClassification);
            if (argumentSpecificationMatches) {
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
          effects().add(RuntimeEffect.unmatchedInput(player));
          output.add("I couldn't understand that.");
          connection.suffix.ifPresent(output::add);
          return RuntimeStep.returned(output);
        }
        RootVerbCall locals = new RootVerbCall(new ObjectValue(thisObject), player, player,
            words.getFirst(), new ListValue(arguments), parsed.argstr(),
            Optional.of(new CommandArguments(directObject, directObjectString,
                prepositionString, indirectObject, indirectObjectString)));
        return startStored(
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

  private RuntimeStep continueLineAfterDoCommand(
      long connectionId, String line, VmState completedDoCommand) {
    return executeLineNow(connectionId, line, Optional.of(completedDoCommand));
  }

  /** Executes one transport-level out-of-band command on an existing connection. */
  public List<String> executeTransportOutOfBand(long connectionId, String command) {
    Objects.requireNonNull(command, "command");
    return scheduler.submit(RuntimeRequest.outOfBand(connectionId, command));
  }

  private RuntimeStep executeTransportOutOfBandNow(long connectionId, String command) {
    Objects.requireNonNull(command, "command");
    ConnectionState connection = requireConnection(connectionId);
    connection.lastActivityNanos = nanoTime.getAsLong();
    long player = connectionRegistry().connectionPlayer(connectionId).orElseThrow();
    Optional<ResolvedVerb> outOfBand =
        world().resolveVerb(connection.listenerHandler, "do_out_of_band_command", true);
    if (outOfBand.isEmpty()) {
      return RuntimeStep.returned(List.of());
    }
    List<MooValue> arguments = new ArrayList<>();
    for (String word : CommandParser.words(command)) {
      arguments.add(StringValue.of(word));
    }
    return startStored(
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

  private long matchProgramObject(long player, String reference) {
    if (reference.startsWith("$")) {
      MooValue value = world().readObjectProperty(0, reference.substring(1)).orElse(null);
      if (value instanceof ObjectValue object && world().object(object.value()).isPresent()) {
        return object.value();
      }
      return -1;
    }
    return CommandParser.matchObject(player, reference, world());
  }

  private static void clearProgramming(ConnectionState connection) {
    connection.programmingObject = -1;
    connection.programmingVerbName = "";
    connection.programmingSource.setLength(0);
  }

  List<String> serverMessage(long listenerHandler, String name, String fallback) {
    MooValue listenerOptions =
        world().readObjectProperty(listenerHandler, "server_options").orElse(null);
    MooValue message =
        listenerOptions instanceof ObjectValue options
            ? world().readObjectProperty(options.value(), name).orElse(null)
            : null;
    if (message == null && listenerHandler != 0) {
      MooValue rootOptions = world().readObjectProperty(0, "server_options").orElse(null);
      if (rootOptions instanceof ObjectValue options) {
        message = world().readObjectProperty(options.value(), name).orElse(null);
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

  private RuntimeStep startTaskTimeout(RuntimeContinuation continuation) {
    VmSnapshot timedOut = continuation.timeoutSnapshot().orElseThrow();
    String resource = timedOut.outcome() == VmState.Outcome.SECONDS_EXHAUSTED ? "seconds" : "ticks";
    Optional<ResolvedVerb> handler = world().resolveVerb(0, "handle_task_timeout", true);
    if (handler.isEmpty()) {
      return RuntimeStep.returned(continuation.output());
    }

    List<MooValue> stack = new ArrayList<>();
    for (VmSnapshot.Frame frame : timedOut.frames()) {
      MooValue thisValue = frame.locals().getOrDefault("this", frame.receiver());
      MooValue verbValue = frame.locals().getOrDefault("verb", StringValue.of(""));
      MooValue playerValue =
          frame.locals().getOrDefault("player", new ObjectValue(continuation.first()));
      stack.add(
          new ListValue(
              List.of(
                  thisValue,
                  verbValue,
                  new ObjectValue(frame.programmer()),
                  frame.verbLocation(),
                  playerValue,
                  new IntegerValue(0))));
    }
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
    return startStored(
        handler.orElseThrow(),
        new RootVerbCall(0, continuation.first(), -1, "handle_task_timeout", arguments, ""),
        RuntimeContinuation.timeoutReturn(continuation.output()));
  }

  RuntimeStep startStored(
      ResolvedVerb resolution, RootVerbCall call, RuntimeContinuation continuation) {
    MooValue serverOptions = world().readObjectProperty(0, "server_options").orElse(null);
    long foregroundTicks = DEFAULT_FOREGROUND_TICKS;
    long foregroundSeconds = DEFAULT_FOREGROUND_SECONDS;
    long maxStackDepth = DEFAULT_MAX_STACK_DEPTH;
    if (serverOptions instanceof ObjectValue options) {
      if (world().readObjectProperty(options.value(), "fg_ticks").orElse(null)
          instanceof IntegerValue ticks) {
        foregroundTicks = Math.max(100L, ticks.value());
      }
      if (world().readObjectProperty(options.value(), "fg_seconds").orElse(null)
          instanceof IntegerValue seconds) {
        foregroundSeconds = Math.max(1L, seconds.value());
      }
      if (world().readObjectProperty(options.value(), "max_stack_depth").orElse(null)
          instanceof IntegerValue depth) {
        maxStackDepth = Math.max(DEFAULT_MAX_STACK_DEPTH, depth.value());
      }
    }
    VmState root = new VmState(Map.of(), resolution.verb().owner(), resolution.location(),
        foregroundTicks, foregroundSeconds, maxStackDepth, resolution.verb().perms().debug(),
        call.verb(), resolution.verb().names());
    if (!MooVm.callVerb(root, call.receiver(), resolution, call.arguments(), call.verb(),
        MooVm.VerbCallKind.ROOT, Optional.empty(), Optional.empty(), Optional.of(call))) {
      throw new IllegalStateException("root verb exceeds empty task stack");
    }
    return RuntimeStep.vm(resolution.verb().program(), root.snapshot(), call.player(), continuation);
  }

  @Override
  public VmState startBackgroundTask(VmSnapshot initial) {
    long backgroundTicks = DEFAULT_BACKGROUND_TICKS;
    long backgroundSeconds = DEFAULT_BACKGROUND_SECONDS;
    long backgroundMaxStackDepth = DEFAULT_MAX_STACK_DEPTH;
    MooValue serverOptions = world().readObjectProperty(0, "server_options").orElse(null);
    if (serverOptions instanceof ObjectValue options) {
      if (world().readObjectProperty(options.value(), "bg_ticks").orElse(null)
          instanceof IntegerValue ticks) {
        backgroundTicks = Math.max(100L, ticks.value());
      }
      if (world().readObjectProperty(options.value(), "bg_seconds").orElse(null)
          instanceof IntegerValue seconds) {
        backgroundSeconds = Math.max(1L, seconds.value());
      }
      if (world().readObjectProperty(options.value(), "max_stack_depth").orElse(null)
          instanceof IntegerValue depth) {
        backgroundMaxStackDepth = Math.max(DEFAULT_MAX_STACK_DEPTH, depth.value());
      }
    }
    return VmState.restoreBackground(
        initial, backgroundTicks, backgroundSeconds, backgroundMaxStackDepth);
  }

  @Override
  public void publishVmState(
      VmState task,
      long taskPlayer,
      boolean collectAfterCompletion,
      List<VmSnapshot> otherTaskRoots) {
    connectionIo.applyConnectionOptionRequests(requireAttempt(), task);
    connectionIo.applyForcedInputRequests(requireAttempt(), task);
    connectionIo.applyNotificationRequests(requireAttempt(), task, taskPlayer);
    connectionIo.applyBootPlayerTargets(requireAttempt(), task, taskPlayer);
    connectionIo.closeRecycledPlayerConnections(requireAttempt());
    var checkpointRequests = task.drainCheckpointRequests();
    boolean panicPending = checkpointRequests.stream().anyMatch(CheckpointRequest::panic);
    boolean checkpointPending =
        !checkpointRequests.isEmpty()
            || effects().stream().anyMatch(effect -> effect.kind() == RuntimeEffectKind.CHECKPOINT);
    if (task.outcome() == VmState.Outcome.SUSPENDED) {
      if (!checkpointPending) {
        finalizationCollector.finalizePendingObjects(requireAttempt());
      }
      if (!panicPending) {
        finalizationCollector.queueUnreachableAnonymousObjects(requireAttempt(), otherTaskRoots, task.snapshot());
      }
      if (!checkpointPending) {
        finalizationCollector.finalizePendingObjects(requireAttempt());
      }
    } else if (collectAfterCompletion
        && (task.outcome() == VmState.Outcome.RETURNED
            || task.outcome() == VmState.Outcome.ERRORED)) {
      if (!panicPending) {
        if (checkpointPending) {
          finalizationCollector.queueUnreachableAnonymousObjects(requireAttempt(), otherTaskRoots);
        } else {
          finalizationCollector.queueUnreachableAnonymousObjects(requireAttempt(), otherTaskRoots, task.snapshot());
        }
      }
      if (!checkpointPending) {
        finalizationCollector.finalizePendingObjects(requireAttempt());
      }
    }
    for (var request : checkpointRequests) {
      if (request.panic()) {
        effects().add(RuntimeEffect.panic(request.message()));
        continue;
      }
      ResolvedVerb started = world().resolveVerb(0, "checkpoint_started", true).orElse(null);
      if (started == null) {
        effects().add(RuntimeEffect.checkpoint(request.shutdown()));
      } else {
        spawnedSteps()
            .add(
                startStored(
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
  public void collectAfterAnonymousFinalization(
      AnonymousObjectValue target, List<VmSnapshot> otherTaskRoots, boolean startNextFinalizationWave) {
    finalizationCollector.collectAfterAnonymousFinalization(
        requireAttempt(), target, otherTaskRoots, startNextFinalizationWave);
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
  public RuntimeStep execute(
      RuntimeContinuation continuation, Optional<world.mongoose.banteng.vm.VmSnapshot> completedVm) {
    RuntimeRequest request = continuation.ingress().orElse(null);
    if (request != null) {
      if (completedVm.isPresent() || continuation.transition().isPresent()) {
        throw new IllegalArgumentException("ingress continuation has resumed state");
      }
      return switch (request.operation) {
        case STARTUP -> startServerNow();
        case GRACEFUL_SHUTDOWN -> gracefulShutdownNow();
        case SIGNAL -> signalNow(Math.toIntExact(request.generation));
        case CHECKPOINT_DISCONNECT ->
            checkpointDisconnectNow(request.connectionId, request.listenerHandler);
        case OPEN ->
            openConnectionNow(
                request.connectionId,
                request.listenerHandler,
                request.printMessages,
                request.connectionInfo);
        case CLOSE -> closeConnectionNow(request.connectionId);
        case LINE -> executeLineNow(request.connectionId, request.text);
        case OUT_OF_BAND -> executeTransportOutOfBandNow(request.connectionId, request.text);
        case TIMEOUT -> loginFlow.executeLoginTimeout(requireAttempt(), request.connectionId, request.generation);
        case CHECKPOINT_FINISHED -> checkpointFinishedNow(request.printMessages, request.generation != 0);
      };
    }
    if (continuation.transition().orElseThrow() == RuntimeTransition.TASK_TIMEOUT_START) {
      if (completedVm.isPresent()) {
        throw new IllegalArgumentException("timeout start continuation has completed VM state");
      }
      return startTaskTimeout(continuation);
    }
    VmState state = VmState.restore(completedVm.orElseThrow());
    return resumeRuntime(continuation, state);
  }

  private RuntimeStep resumeRuntime(RuntimeContinuation continuation, VmState state) {
    if (state.outcome() == VmState.Outcome.ERRORED) {
      finishErroredFinalization(continuation);
      List<String> output = new ArrayList<>(continuation.output());
      output.addAll(state.output());
      output.addAll(formatUncaughtException(state));
      if (continuation.transition().orElseThrow()
          == RuntimeTransition.LINE_SELECTED_COMMAND_APPEND_OUTPUT) {
        ConnectionState connection = connections().get(continuation.connectionId());
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
              ? RuntimeStep.returned(state.output()) : signalDefault(continuation.text());
      case SERVER_STARTED_RETURN -> RuntimeStep.returned(state.output());
      case CHECKPOINT_USER_DISCONNECTED_RETURN -> RuntimeStep.returned(List.of());
      case CHECKPOINT_STARTED_RETURN -> {
        effects().add(RuntimeEffect.checkpoint(continuation.flag(), continuation.text()));
        yield RuntimeStep.returned(List.of());
      }
      case CHECKPOINT_FINISHED_RETURN -> {
        if (continuation.flag()) {
          effects().add(RuntimeEffect.shutdown());
        }
        yield RuntimeStep.returned(List.of());
      }
      case CLOSE_AFTER_USER_CLIENT_DISCONNECTED -> RuntimeStep.returned(List.of());
      case LINE_OOB_RETURN_OUTPUT, TRANSPORT_OOB_RETURN_OUTPUT ->
          RuntimeStep.returned(state.output());
      case LINE_DO_COMMAND_GATE_THEN_DISPATCH ->
          continueLineAfterDoCommand(continuation.connectionId(), continuation.text(), state);
      case LINE_SELECTED_COMMAND_APPEND_OUTPUT -> {
        List<String> output = new ArrayList<>(continuation.output());
        ConnectionState connection = connections().get(continuation.connectionId());
        if (connection != null) {
          output.addAll(state.output());
          connection.suffix.ifPresent(output::add);
        }
        yield RuntimeStep.returned(output);
      }
      case LOGIN_BLANK_TRUTH_GATE ->
          state.returnValue().isEmpty() || !state.returnValue().orElseThrow().isTruthy()
              ? RuntimeStep.returned(state.output())
              : loginFlow.executeLoginAuthentication(requireAttempt(), continuation.connectionId(), continuation.text());
      case LOGIN_AUTHENTICATE_AND_ASSOCIATE ->
          loginFlow.finishLogin(requireAttempt(),
              continuation.connectionId(), continuation.first(), state);
      case LOGIN_RECONNECTED_HOOK_THEN_RETURN,
          LOGIN_NEW_USER_CONNECTED_THEN_RETURN,
          LOGIN_EXISTING_USER_CONNECTED_THEN_RETURN ->
          RuntimeStep.returned(continuation.flag() ? List.of() : continuation.output());
      case LOGIN_OLD_CLIENT_DISCONNECTED_THEN_ASSOCIATE ->
          loginFlow.associateRedirectedLogin(requireAttempt(), continuation);
      case LOGIN_USER_CREATED_THEN_CONNECTED -> loginFlow.continueCreatedLogin(requireAttempt(), continuation);
      case LOGIN_TIMEOUT_USER_DISCONNECTED_THEN_BOOT ->
          loginFlow.finishLoginTimeout(requireAttempt(), continuation.connectionId(), continuation.output());
      case BOOT_PLAYER_USER_DISCONNECTED_THEN_BOOT ->
          connectionIo.finishBootPlayer(requireAttempt(), continuation.connectionId(), continuation.output());
      case ANONYMOUS_FINALIZATION_RETURN -> RuntimeStep.returned(List.of());
      case WAIF_FINALIZATION_RETURN -> {
        finalizationCollector.finishWaifFinalization(requireAttempt(), (WaifValue) continuation.finalizationControl().orElseThrow().target());
        yield continueStartupAfterPendingWaifFinalization();
      }
      case WAIF_BACKGROUND_FINALIZATION_RETURN -> {
        finalizationCollector.finishWaifFinalization(requireAttempt(), (WaifValue) continuation.finalizationControl().orElseThrow().target());
        yield RuntimeStep.returned(List.of());
      }
      case TASK_TIMEOUT_START ->
          throw new IllegalStateException("timeout start cannot resume completed VM state");
      case TASK_TIMEOUT_RETURN -> {
        List<String> output = new ArrayList<>(state.output());
        if (state.returnValue().isEmpty() || !state.returnValue().orElseThrow().isTruthy()) {
          output.addAll(continuation.output());
        }
        yield RuntimeStep.returned(output);
      }
      case UNCAUGHT_HANDLER_RETURN -> RuntimeStep.returned(state.output());
    };
  }

  private void finishErroredFinalization(RuntimeContinuation continuation) {
    continuation
        .finalizationControl()
        .filter(control -> control.kind() == FinalizationKind.WAIF)
        .ifPresent(control -> finalizationCollector.finishWaifFinalization(requireAttempt(), (WaifValue) control.target()));
  }

  private List<String> formatUncaughtException(VmState state) {
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
          WorldVerb stored = world().verb(definer.value(), verb).orElse(null);
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
  public RuntimeStep startUncaughtError(VmState state) {
    List<String> formatted = formatUncaughtException(state);
    ResolvedVerb handler = world().resolveVerb(0, "handle_uncaught_error", true).orElse(null);
    if (handler == null
        || state.returnValue().isEmpty()
        || !(state.returnValue().orElseThrow() instanceof ListValue exception)
        || exception.size() != 4) {
      formatted.forEach(serverLog::error);
      return RuntimeStep.returned(List.of());
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
    return startStored(
        handler,
        new RootVerbCall(0, player, -1, "handle_uncaught_error", arguments, ""),
        RuntimeContinuation.after(
            RuntimeTransition.UNCAUGHT_HANDLER_RETURN, 0, "", List.of(), 0, 0, false));
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
    ATTEMPT.set(context);
    return context;
  }

  void replaceAttemptWorld(AttemptContext context, WorldTxn transaction) {
    if (ATTEMPT.get() != context) {
      throw new IllegalStateException("cannot replace another runtime attempt");
    }
    context.world = transaction;
  }

  @Override
  public AttemptContext finishAttempt() {
    AttemptContext context = requireAttempt();
    ATTEMPT.remove();
    return context;
  }

  @Override
  public void abandonAttempt() {
    ATTEMPT.remove();
  }

  @Override
  public synchronized boolean sessionsAreCurrent(AttemptContext context) {
    return context.baseSessionRevision == sessionRevision;
  }

  @Override
  public synchronized OptionalLong connectionPlayer(long connectionId) {
    return publishedConnectionRegistry.connectionPlayer(connectionId);
  }

  synchronized Optional<MapValue> connectionInfo(long objectId) {
    return publishedConnectionRegistry.connectionInfo(objectId);
  }

  private BuiltinResult connectionNameLookup(BuiltinCall call) {
    long target = ((ObjectValue) call.arguments().getFirst()).value();
    OptionalLong connectionId = connectionRegistry().connectionId(target);
    MapValue info = connectionRegistry().connectionInfo(target).orElse(null);
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

  private static String resolveCanonicalConnectionName(String address)
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
            listenerControl.ifPresent(ListenerControl::shutdown);
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
      case SHUTDOWN -> listenerControl.ifPresent(ListenerControl::shutdown);
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

  private AttemptContext requireAttempt() {
    AttemptContext context = ATTEMPT.get();
    if (context == null) {
      throw new IllegalStateException("runtime world access is outside a scheduler attempt");
    }
    return context;
  }

  AttemptContext currentAttempt() {
    return requireAttempt();
  }

  WorldTxn world() {
    return requireAttempt().world;
  }

  private ConnectionRegistryAccess connectionRegistry() {
    return requireAttempt().connections;
  }

  boolean switchConnectionPlayer(long connectionId, long playerId) {
    if (world().object(playerId).isEmpty()
        || !connectionRegistry().switchConnectionPlayer(connectionId, playerId)) {
      return false;
    }
    requireConnection(connectionId).connectedNanos = OptionalLong.of(nanoTime.getAsLong());
    return true;
  }

  private Map<Long, ConnectionState> connections() {
    return requireAttempt().sessions;
  }

  private List<RuntimeEffect> effects() {
    return requireAttempt().effects;
  }

  private List<RuntimeStep> spawnedSteps() {
    return requireAttempt().spawnedSteps;
  }

  @Override
  public MooVm vm() {
    return vm;
  }

  @Override
  public BuiltinCatalog builtins() {
    return builtins;
  }

  ConnectionState requireConnection(long connectionId) {
    ConnectionState connection = connections().get(connectionId);
    if (connection == null) {
      throw new IllegalArgumentException("unknown connection #" + connectionId);
    }
    return connection;
  }

  @FunctionalInterface
  interface ConnectionNameResolver {
    String resolve(String address) throws UnknownHostException;
  }

  private record ConnectionNameResolution(String name, boolean resolved) {
    private ConnectionNameResolution {
      Objects.requireNonNull(name, "name");
    }
  }

  enum RuntimeTransition {
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
    UNCAUGHT_HANDLER_RETURN
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
      Optional<LoginTimeoutResult> loginTimeout) {
    RuntimeStep {
      Objects.requireNonNull(program, "program");
      Objects.requireNonNull(snapshot, "snapshot");
      Objects.requireNonNull(continuation, "continuation");
      Objects.requireNonNull(loginTimeout, "loginTimeout");
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
          Optional.empty(), Optional.empty());
    }

    static RuntimeStep returned(List<String> output) {
      return new RuntimeStep(
          Optional.empty(), Optional.empty(), Long.MIN_VALUE, Optional.empty(), Optional.of(output),
          Optional.empty());
    }

    static RuntimeStep loginTimeout(LoginTimeoutResult result, List<String> output) {
      return new RuntimeStep(
          Optional.empty(), Optional.empty(), Long.MIN_VALUE, Optional.empty(), Optional.of(output),
          Optional.of(result));
    }
  }

  static final class ConnectionState {
    final long listenerHandler;
    final boolean printMessages;
    final long generation;
    long lastActivityNanos;
    OptionalLong connectedNanos = OptionalLong.empty();
    boolean holdInput;
    boolean disableOob;
    boolean binary;
    KeepAliveOptions keepAlive = KeepAliveOptions.defaults();
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
      long generation) {
    static RuntimeRequest startup() {
      return new RuntimeRequest(
          Operation.STARTUP, 0, 0, false, new MapValue(Map.of()), "", 0);
    }

    static RuntimeRequest signal(int signal) {
      return new RuntimeRequest(
          Operation.SIGNAL, 0, 0, false, new MapValue(Map.of()), "", signal);
    }

    static RuntimeRequest gracefulShutdown() {
      return new RuntimeRequest(
          Operation.GRACEFUL_SHUTDOWN, 0, 0, false, new MapValue(Map.of()), "", 0);
    }

    static RuntimeRequest checkpointDisconnect(ActiveConnection connection) {
      return new RuntimeRequest(
          Operation.CHECKPOINT_DISCONNECT,
          connection.player(),
          connection.listener(),
          false,
          new MapValue(Map.of()),
          "",
          0);
    }

    static RuntimeRequest open(
        long connectionId, long listenerHandler, boolean printMessages, MapValue connectionInfo) {
      return new RuntimeRequest(
          Operation.OPEN, connectionId, listenerHandler, printMessages, connectionInfo, "", 0);
    }

    static RuntimeRequest close(long connectionId) {
      return new RuntimeRequest(
          Operation.CLOSE, connectionId, 0, false, new MapValue(Map.of()), "", 0);
    }

    static RuntimeRequest line(long connectionId, String line) {
      return new RuntimeRequest(
          Operation.LINE, connectionId, 0, false, new MapValue(Map.of()), line, 0);
    }

    static RuntimeRequest outOfBand(long connectionId, String command) {
      return new RuntimeRequest(
          Operation.OUT_OF_BAND, connectionId, 0, false, new MapValue(Map.of()), command, 0);
    }

    static RuntimeRequest timeout(long connectionId, long generation) {
      return new RuntimeRequest(
          Operation.TIMEOUT, connectionId, 0, false, new MapValue(Map.of()), "", generation);
    }

    static RuntimeRequest checkpointFinished(boolean success, boolean shutdown) {
      return new RuntimeRequest(
          Operation.CHECKPOINT_FINISHED,
          0,
          0,
          success,
          new MapValue(Map.of()),
          "",
          shutdown ? 1 : 0);
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
      KeepAliveOptions keepAlive) {
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
          KeepAliveOptions.defaults());
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

    static RuntimeEffect keepAlive(long connectionId, KeepAliveOptions options) {
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
