package world.mongoose.banteng.builtin;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Objects;
import java.util.function.Supplier;
import world.mongoose.banteng.host.Crypt;
import world.mongoose.banteng.builtin.family.ConnectionBuiltins.ConnectionOption;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.ValueSemantics;

/** Host-owned operations and configuration supplied to one builtin catalog. */
public record BuiltinHosts(
    ValueSemantics valueSemantics,
    BuiltinHandler queuedTasks,
    BuiltinHandler killTask,
    BuiltinHandler read,
    BuiltinHandler threadPool,
    BuiltinHandler threads,
    BuiltinHandler connectionOptions,
    BuiltinHandler idleSeconds,
    BuiltinHandler connectedSeconds,
    ConnectionOptionSetter setConnectionOption,
    BuiltinHandler connectionNameLookup,
    BuiltinHandler dbDiskSize,
    BuiltinHandler flushInput,
    BuiltinHandler outputDelimiters,
    BuiltinHandler queueInfo,
    BuiltinHandler switchPlayer,
    BuiltinHandler taskStack,
    BuiltinHandler resumeTask,
    ServerLog serverLog,
    Supplier<ConnectionRegistryAccess> connections,
    Crypt crypt,
    OutboundConnectionRegistrar registerOutboundConnection) {
  /** Rejects incomplete host composition. */
  public BuiltinHosts {
    Objects.requireNonNull(valueSemantics, "valueSemantics");
    Objects.requireNonNull(queuedTasks, "queuedTasks");
    Objects.requireNonNull(killTask, "killTask");
    Objects.requireNonNull(read, "read");
    Objects.requireNonNull(threadPool, "threadPool");
    Objects.requireNonNull(threads, "threads");
    Objects.requireNonNull(connectionOptions, "connectionOptions");
    Objects.requireNonNull(idleSeconds, "idleSeconds");
    Objects.requireNonNull(connectedSeconds, "connectedSeconds");
    Objects.requireNonNull(setConnectionOption, "setConnectionOption");
    Objects.requireNonNull(connectionNameLookup, "connectionNameLookup");
    Objects.requireNonNull(dbDiskSize, "dbDiskSize");
    Objects.requireNonNull(flushInput, "flushInput");
    Objects.requireNonNull(outputDelimiters, "outputDelimiters");
    Objects.requireNonNull(queueInfo, "queueInfo");
    Objects.requireNonNull(switchPlayer, "switchPlayer");
    Objects.requireNonNull(taskStack, "taskStack");
    Objects.requireNonNull(resumeTask, "resumeTask");
    Objects.requireNonNull(serverLog, "serverLog");
    Objects.requireNonNull(connections, "connections");
    Objects.requireNonNull(crypt, "crypt");
    Objects.requireNonNull(registerOutboundConnection, "registerOutboundConnection");
  }

  /** Starts one host composition with behavior-preserving standalone defaults. */
  public static Builder builder() {
    return new Builder();
  }

  /** Incrementally configures the host boundary. */
  public static final class Builder {
    private ValueSemantics valueSemantics = ValueSemantics.STANDARD;
    private BuiltinHandler queuedTasks =
        call ->
            BuiltinResult.value(
                call.arguments().size() == 2 && call.arguments().get(1).isTruthy()
                    ? new IntegerValue(0)
                    : new ListValue(List.of()));
    private BuiltinHandler killTask = Builder::invalidArgument;
    private BuiltinHandler read = Builder::invalidArgument;
    private BuiltinHandler threadPool = Builder::invalidArgument;
    private BuiltinHandler threads =
        call -> BuiltinResult.value(new ListValue(List.of()));
    private BuiltinHandler connectionOptions = Builder::invalidArgument;
    private BuiltinHandler idleSeconds = Builder::invalidArgument;
    private BuiltinHandler connectedSeconds = Builder::invalidArgument;
    private ConnectionOptionSetter setConnectionOption = BuiltinResult.SetConnectionOption::new;
    private BuiltinHandler connectionNameLookup = Builder::invalidArgument;
    private BuiltinHandler dbDiskSize =
        call -> BuiltinResult.value(new IntegerValue(0));
    private BuiltinHandler flushInput =
        call -> BuiltinResult.value(new IntegerValue(0));
    private BuiltinHandler outputDelimiters = Builder::invalidArgument;
    private BuiltinHandler queueInfo =
        call -> BuiltinResult.value(new ListValue(List.of()));
    private BuiltinHandler taskStack = Builder::invalidArgument;
    private BuiltinHandler switchPlayer = Builder::invalidArgument;
    private BuiltinHandler resumeTask = Builder::invalidArgument;
    private ServerLog serverLog = ServerLog.stderr(System.Logger.Level.INFO);
    private Supplier<ConnectionRegistryAccess> connections =
        () -> EmptyConnectionRegistry.INSTANCE;
    private Crypt crypt = Crypt.system();
    private OutboundConnectionRegistrar registerOutboundConnection = (id, handler, info) -> {};

    private Builder() {}

    public Builder valueSemantics(ValueSemantics value) {
      valueSemantics = value;
      return this;
    }

    public Builder queuedTasks(BuiltinHandler value) {
      queuedTasks = value;
      return this;
    }

    public Builder killTask(BuiltinHandler value) {
      killTask = value;
      return this;
    }

    public Builder read(BuiltinHandler value) {
      read = value;
      return this;
    }

    public Builder threadPool(BuiltinHandler value) {
      threadPool = value;
      return this;
    }

    public Builder threads(BuiltinHandler value) {
      threads = value;
      return this;
    }

    public Builder connectionOptions(BuiltinHandler value) {
      connectionOptions = value;
      return this;
    }

    public Builder idleSeconds(BuiltinHandler value) {
      idleSeconds = value;
      return this;
    }

    public Builder connectedSeconds(BuiltinHandler value) {
      connectedSeconds = value;
      return this;
    }

    public Builder setConnectionOption(ConnectionOptionSetter value) {
      setConnectionOption = value;
      return this;
    }

    public Builder connectionNameLookup(BuiltinHandler value) {
      connectionNameLookup = value;
      return this;
    }

    public Builder dbDiskSize(BuiltinHandler value) {
      dbDiskSize = value;
      return this;
    }

    public Builder flushInput(BuiltinHandler value) {
      flushInput = value;
      return this;
    }

    public Builder outputDelimiters(BuiltinHandler value) {
      outputDelimiters = value;
      return this;
    }

    public Builder queueInfo(BuiltinHandler value) {
      queueInfo = value;
      return this;
    }

    public Builder taskStack(BuiltinHandler value) {
      taskStack = value;
      return this;
    }

    public Builder switchPlayer(BuiltinHandler value) {
      switchPlayer = value;
      return this;
    }

    public Builder resumeTask(BuiltinHandler value) {
      resumeTask = value;
      return this;
    }

    public Builder serverLog(ServerLog value) {
      serverLog = value;
      return this;
    }

    public Builder connections(Supplier<ConnectionRegistryAccess> value) {
      connections = value;
      return this;
    }

    public Builder crypt(Crypt value) {
      crypt = value;
      return this;
    }

    public Builder registerOutboundConnection(OutboundConnectionRegistrar value) {
      registerOutboundConnection = value;
      return this;
    }

    public BuiltinHosts build() {
      return new BuiltinHosts(
          valueSemantics,
          queuedTasks,
          killTask,
          read,
          threadPool,
          threads,
          connectionOptions,
          idleSeconds,
          connectedSeconds,
          setConnectionOption,
          connectionNameLookup,
          dbDiskSize,
          flushInput,
          outputDelimiters,
          queueInfo,
          switchPlayer,
          taskStack,
          resumeTask,
          serverLog,
          connections,
          crypt,
          registerOutboundConnection);
    }

    private static BuiltinResult invalidArgument(BuiltinCall call) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
  }

  /** Registers a successfully opened socket in the invocation's isolated attempt. */
  @FunctionalInterface
  public interface OutboundConnectionRegistrar {
    void register(long connectionId, long listenerHandler, MapValue connectionInfo);
  }

  /** Applies one validated connection option inside the current isolated runtime attempt. */
  @FunctionalInterface
  public interface ConnectionOptionSetter {
    BuiltinResult set(long target, ConnectionOption option, MooValue value);
  }

  private enum EmptyConnectionRegistry implements ConnectionRegistryAccess {
    INSTANCE;

    @Override
    public List<Long> connectionIds() {
      return List.of();
    }

    @Override
    public ConnectionRegistryAccess copy() {
      return this;
    }

    @Override
    public void replaceWith(ConnectionRegistryAccess source) {
      if (!Objects.requireNonNull(source, "source").connectionIds().isEmpty()) {
        throw new IllegalStateException("connection registry is unavailable");
      }
    }

    @Override
    public boolean sameState(ConnectionRegistryAccess other) {
      return Objects.requireNonNull(other, "other").connectionIds().isEmpty();
    }

    @Override
    public void openConnection(long connectionId) {
      throw new IllegalStateException("connection registry is unavailable");
    }

    @Override
    public void openConnection(long connectionId, MapValue info) {
      throw new IllegalStateException("connection registry is unavailable");
    }

    @Override
    public void closeConnection(long connectionId) {}

    @Override
    public OptionalLong connectionPlayer(long connectionId) {
      return OptionalLong.empty();
    }

    @Override
    public List<Long> connectedPlayers(boolean showAll) {
      return List.of();
    }

    @Override
    public Optional<MapValue> connectionInfo(long objectId) {
      return Optional.empty();
    }

    @Override
    public boolean rewriteConnectionName(
        long connectionId, String expectedIp, String resolvedName) {
      return false;
    }

    @Override
    public OptionalLong connectionId(long objectId) {
      return OptionalLong.empty();
    }

    @Override
    public Optional<ListValue> intrinsicCommands(long objectId) {
      return Optional.empty();
    }

    @Override
    public boolean setIntrinsicCommands(long objectId, ListValue commands) {
      return false;
    }

    @Override
    public boolean switchConnectionPlayer(long connectionId, long playerId) {
      return false;
    }
  }
}
