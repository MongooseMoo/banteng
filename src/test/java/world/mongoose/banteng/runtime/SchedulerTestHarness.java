package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import world.mongoose.banteng.builtin.ConnectionRegistryAccess;
import world.mongoose.banteng.builtin.family.ConnectionBuiltins;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.persistence.LambdaMooV17Codec;
import world.mongoose.banteng.persistence.LambdaMooV4Reader;
import world.mongoose.banteng.server.ConnectionRegistry;
import world.mongoose.banteng.server.MooServer;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.ValueSemantics;
import world.mongoose.banteng.vm.MooVm;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.DurableTask;
import world.mongoose.banteng.world.ActiveConnection;

/** Shared production-runtime fixture for the Phase 6 scheduler proofs. */
public final class SchedulerTestHarness implements AutoCloseable {
  private static final Path FIXTURE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");

  final WorldTxn root;
  final MooRuntime runtime;
  final PublicationScheduler scheduler;

  public static MooRuntime runtime(
      WorldTxn world,
      ConnectionRegistryAccess connections,
      int workers, LongSupplier nanoTime,
      MooRuntime.ConnectionNameResolver resolver,
      Optional<ConnectionBuiltins.ListenerControl> listener,
      Optional<Path> database, Optional<Path> checkpoint,
      List<DurableTask> tasks, List<ActiveConnection> activeConnections) {
    var semantics = ValueSemantics.STANDARD;
    var log = ServerLog.stderr(System.Logger.Level.INFO);
    var vm = new MooVm(semantics);
    var registry = new TaskRegistry();
    var reference = new AtomicReference<MooRuntime>();
    var scheduler = new PublicationScheduler(
        world, () -> java.util.Objects.requireNonNull(reference.get()), workers, registry);
    var hosts = BuiltinHosts.builder().valueSemantics(semantics).serverLog(log).build();
    var catalog = listener.map(control -> new BuiltinCatalog(control, hosts))
        .orElseGet(() -> new BuiltinCatalog(hosts));
    MooRuntime runtime = new MooRuntime(
        world, semantics, connections, nanoTime, resolver,
        listener, database, checkpoint,
        activeConnections, log, vm, registry, scheduler, catalog,
        new LambdaMooV17Codec());
    reference.set(runtime);
    scheduler.restoreTasks(tasks);
    return runtime;
  }

  public static MooServer server(
      WorldTxn world, Optional<Path> database, Path checkpoint,
      List<DurableTask> tasks, List<ActiveConnection> activeConnections) throws IOException {
    var connections = new ConnectionRegistry();
    var reference = new AtomicReference<MooRuntime>();
    var server = new MooServer(
        "127.0.0.1", 0, () -> java.util.Objects.requireNonNull(reference.get()));
    MooRuntime runtime = runtime(world, connections, Math.max(2, Runtime.getRuntime().availableProcessors()),
        System::nanoTime, MooRuntime::resolveCanonicalConnectionName,
        Optional.of(server), database, Optional.of(checkpoint), tasks, activeConnections);
    reference.set(runtime);
    return server;
  }

  static SchedulerTestHarness open(int workers, long... connectionIds) throws IOException {
    WorldTxn root = new LambdaMooV4Reader(new LambdaMooV17Codec()).read(FIXTURE).world();
    MooRuntime runtime =
        runtime(root, new ConnectionRegistry(), workers, System::nanoTime,
            MooRuntime::resolveCanonicalConnectionName, Optional.of(new NoOpListener()),
            Optional.empty(), Optional.empty(), List.of(), List.of());
    PublicationScheduler scheduler = field(runtime, "scheduler", PublicationScheduler.class);
    SchedulerTestHarness harness = new SchedulerTestHarness(root, runtime, scheduler);
    for (long connectionId : connectionIds) {
      harness.connectWizard(connectionId);
    }
    harness.setCounter(0);
    harness.setServerOption("fg_ticks", 20_000_000);
    return harness;
  }

  private SchedulerTestHarness(
      WorldTxn root, MooRuntime runtime, PublicationScheduler scheduler) {
    this.root = root;
    this.runtime = runtime;
    this.scheduler = scheduler;
  }

  void connectWizard(long connectionId) {
    runtime.openConnection(connectionId, 0, true, new MapValue(Map.of()));
    runtime.executeLine(connectionId, "connect Wizard");
  }

  CompletableFuture<List<String>> lineAsync(long connectionId, String source) {
    return CompletableFuture.supplyAsync(() -> runtime.executeLine(connectionId, source));
  }

  void setCounter(long value) {
    try (WorldTxn transaction = root.begin()) {
      boolean written =
          transaction.property(0, "scheduler_counter").isPresent()
              ? transaction.writeObjectProperty(
                      0, "scheduler_counter", new IntegerValue(value))
                  .isOk()
              : transaction.addProperty(0, "scheduler_counter", new IntegerValue(value), 0, new PropertyPerms(3))
                  .isOk();
      assertTrue(written);
      assertTrue(transaction.commit().isCommitted());
    }
  }

  long counter() {
    try (WorldTxn transaction = root.begin()) {
      WorldProperty property = transaction.property(0, "scheduler_counter").orElseThrow();
      return ((IntegerValue) property.value()).value();
    }
  }

  void setServerOption(String name, long value) {
    try (WorldTxn transaction = root.begin()) {
      ObjectValue serverOptions =
          (ObjectValue) transaction.readObjectProperty(0, "server_options").orElseThrow();
      boolean written =
          transaction.property(serverOptions.value(), name).isPresent()
              ? transaction.writeObjectProperty(
                      serverOptions.value(), name, new IntegerValue(value))
                  .isOk()
              : transaction.addProperty(serverOptions.value(), name, new IntegerValue(value), 0, new PropertyPerms(3))
                  .isOk();
      assertTrue(written);
      assertTrue(transaction.commit().isCommitted());
    }
  }

  int readySize() {
    synchronized (scheduler) {
      return field(scheduler, "ready", Queue.class).size();
    }
  }

  static <T> T field(Object owner, String name, Class<T> type) {
    try {
      Field field = owner.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return type.cast(field.get(owner));
    } catch (ReflectiveOperationException error) {
      throw new LinkageError(error.getMessage(), error);
    }
  }

  @Override
  public void close() {
    scheduler.close();
  }

  private static final class NoOpListener implements ConnectionBuiltins.ListenerControl {
    @Override
    public int listen(
        long handler,
        int description,
        boolean ipv6,
        boolean printMessages,
        String interfaceAddress) {
      return 77;
    }

    @Override
    public List<ConnectionBuiltins.ListenerDescription> listeners() {
      return List.of();
    }

    @Override
    public boolean unlisten(int description, boolean ipv6) {
      return true;
    }

    @Override
    public long openNetworkConnection(
        String host, int port, boolean ipv6, long listenerHandler,
        world.mongoose.banteng.builtin.BuiltinHosts.OutboundConnectionRegistrar registration) {
      return -77;
    }

    @Override
    public void writeConnection(long connectionId, List<String> lines) {}

    @Override
    public void notifyConnection(
        long connectionId, String line, boolean noFlush, boolean noNewline) {}

    @Override
    public void bootConnection(long connectionId, List<String> lines) {}

    @Override
    public void setConnectionBinary(long connectionId, boolean binary) {}

    @Override
    public long bufferedOutputLength(long connectionId) {
      return 0;
    }

    @Override
    public void shutdown() {}

    @Override
    public void panic() {}
  }
}
