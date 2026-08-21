package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import world.mongoose.banteng.builtin.BuiltinCatalog.ListenerControl;
import world.mongoose.banteng.builtin.BuiltinCatalog.ListenerDescription;
import world.mongoose.banteng.persistence.LambdaMooV4Reader;
import world.mongoose.banteng.server.ConnectionRegistry;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldTxn;

/** Shared production-runtime fixture for the Phase 6 scheduler proofs. */
final class SchedulerTestHarness implements AutoCloseable {
  private static final Path FIXTURE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");

  final WorldTxn root;
  final MooRuntime runtime;
  final PublicationScheduler scheduler;

  static SchedulerTestHarness open(int workers, long... connectionIds) throws IOException {
    WorldTxn root = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime =
        new MooRuntime(root, new NoOpListener(), workers, new ConnectionRegistry());
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
              : transaction.addProperty(
                      0, "scheduler_counter", new IntegerValue(value), 0, 3)
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
              : transaction.addProperty(
                      serverOptions.value(), name, new IntegerValue(value), 0, 3)
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

  private static final class NoOpListener implements ListenerControl {
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
    public List<ListenerDescription> listeners() {
      return List.of();
    }

    @Override
    public boolean unlisten(int description, boolean ipv6) {
      return true;
    }

    @Override
    public long openNetworkConnection(String host, int port, boolean ipv6, long listenerHandler) {
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
