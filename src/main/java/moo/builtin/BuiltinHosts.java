package moo.builtin;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import moo.logging.ServerLog;
import moo.server.ConnectionRegistry;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.ValueSemantics;

/** Host-owned operations and configuration supplied to one builtin catalog. */
public record BuiltinHosts(
    ValueSemantics valueSemantics,
    BuiltinHandler queuedTasks,
    BuiltinHandler killTask,
    BuiltinHandler read,
    BuiltinHandler threadPool,
    BuiltinHandler threads,
    BuiltinHandler connectionOptions,
    BuiltinHandler dbDiskSize,
    BuiltinHandler flushInput,
    BuiltinHandler outputDelimiters,
    BuiltinHandler queueInfo,
    BuiltinHandler taskStack,
    BuiltinHandler resumeTask,
    ServerLog serverLog,
    Supplier<ConnectionRegistry> connections) {
  /** Rejects incomplete host composition. */
  public BuiltinHosts {
    Objects.requireNonNull(valueSemantics, "valueSemantics");
    Objects.requireNonNull(queuedTasks, "queuedTasks");
    Objects.requireNonNull(killTask, "killTask");
    Objects.requireNonNull(read, "read");
    Objects.requireNonNull(threadPool, "threadPool");
    Objects.requireNonNull(threads, "threads");
    Objects.requireNonNull(connectionOptions, "connectionOptions");
    Objects.requireNonNull(dbDiskSize, "dbDiskSize");
    Objects.requireNonNull(flushInput, "flushInput");
    Objects.requireNonNull(outputDelimiters, "outputDelimiters");
    Objects.requireNonNull(queueInfo, "queueInfo");
    Objects.requireNonNull(taskStack, "taskStack");
    Objects.requireNonNull(resumeTask, "resumeTask");
    Objects.requireNonNull(serverLog, "serverLog");
    Objects.requireNonNull(connections, "connections");
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
    private BuiltinHandler dbDiskSize =
        call -> BuiltinResult.value(new IntegerValue(0));
    private BuiltinHandler flushInput =
        call -> BuiltinResult.value(new IntegerValue(0));
    private BuiltinHandler outputDelimiters = Builder::invalidArgument;
    private BuiltinHandler queueInfo =
        call -> BuiltinResult.value(new ListValue(List.of()));
    private BuiltinHandler taskStack = Builder::invalidArgument;
    private BuiltinHandler resumeTask = Builder::invalidArgument;
    private ServerLog serverLog = ServerLog.stderr(System.Logger.Level.INFO);
    private final ConnectionRegistry standaloneConnections = new ConnectionRegistry();
    private Supplier<ConnectionRegistry> connections = () -> standaloneConnections;

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

    public Builder resumeTask(BuiltinHandler value) {
      resumeTask = value;
      return this;
    }

    public Builder serverLog(ServerLog value) {
      serverLog = value;
      return this;
    }

    public Builder connections(Supplier<ConnectionRegistry> value) {
      connections = value;
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
          dbDiskSize,
          flushInput,
          outputDelimiters,
          queueInfo,
          taskStack,
          resumeTask,
          serverLog,
          connections);
    }

    private static BuiltinResult invalidArgument(BuiltinCall call) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
  }
}
