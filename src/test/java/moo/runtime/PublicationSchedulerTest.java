package moo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import moo.builtin.BuiltinCatalog.ListenerControl;
import moo.persistence.LambdaMooV4Reader;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.world.WorldProperty;
import moo.world.WorldTxn;
import org.junit.jupiter.api.Test;

final class PublicationSchedulerTest {
  private static final Path FIXTURE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");
  private static final long CONNECTION_ID = -47;
  private static final long SECOND_CONNECTION_ID = -48;
  private static final String CONNECTION_PREFIX = "-=!-^-!=-";
  private static final String CONNECTION_SUFFIX = "-=!-v-!=-";

  @Test
  void assignsMonotonicallyIncreasingTicketsInReadyOrder() throws Exception {
    try (Harness harness = Harness.open(2, new RecordingListener())) {
      long first = harness.scheduler.nextTicket();

      harness.line("; return 1;");
      long second = harness.scheduler.nextTicket();
      harness.line("; return 2;");
      long third = harness.scheduler.nextTicket();
      harness.line("; return 3;");
      long after = harness.scheduler.nextTicket();

      assertEquals(first + 1, second);
      assertEquals(second + 1, third);
      assertEquals(third + 1, after);
      assertEquals(after, harness.scheduler.nextPublicationTicket());
    }
  }

  @Test
  void usesFixedWorkersAndWorkersTimesFourBoundedQueue() throws Exception {
    WorldTxn root = new LambdaMooV4Reader().read(FIXTURE);
    MooRuntime runtime = new MooRuntime(root);
    try (PublicationScheduler scheduler = new PublicationScheduler(root, runtime, 3)) {
      ThreadPoolExecutor executor = field(scheduler, "executor", ThreadPoolExecutor.class);

      assertEquals(3, scheduler.workers());
      assertEquals(3, executor.getCorePoolSize());
      assertEquals(3, executor.getMaximumPoolSize());
      assertInstanceOf(ArrayBlockingQueue.class, executor.getQueue());
      assertEquals(12, scheduler.queueCapacity());
      assertEquals(12, executor.getQueue().remainingCapacity());
    } finally {
      scheduler(runtime).close();
    }
  }

  @Test
  void keepsOverflowInReadyQueueUntilExecutorCapacityReturns() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      ThreadPoolExecutor executor =
          field(harness.scheduler, "executor", ThreadPoolExecutor.class);
      executor.execute(
          () -> {
            entered.countDown();
            awaitRelease(release);
          });
      entered.await();

      long firstOverflowTicket = harness.scheduler.nextTicket();
      List<CompletableFuture<List<String>>> overflow = new ArrayList<>();
      for (int index = 0; index < 5; index++) {
        overflow.add(harness.lineAsync("; return " + index + ";"));
      }
      while (harness.scheduler.nextTicket() != firstOverflowTicket + 5) {
        Thread.onSpinWait();
      }

      try {
        assertTrue(readySize(harness.scheduler) > 0);
      } finally {
        release.countDown();
      }
      overflow.forEach(CompletableFuture::join);
      assertEquals(0, readySize(harness.scheduler));
    }
  }

  @Test
  void publishesReverseCompletionsInTicketOrder() throws Exception {
    RecordingListener listener = new RecordingListener();
    try (Harness harness = Harness.open(2, listener)) {
      try (ConflictScenario scenario = startConflictScenario(harness)) {
        assertEquals(scenario.earlierTicket, harness.scheduler.nextPublicationTicket());

        scenario.finish();
        assertEquals(
            List.of(scenario.laterTicket, scenario.earlierTicket),
            scenario.initialCompletionTickets);
        assertTrue(scenario.laterTicket > scenario.earlierTicket);
        assertEquals(2, harness.counter());
        assertEquals(harness.scheduler.nextTicket(), harness.scheduler.nextPublicationTicket());
      }
    }
  }

  @Test
  void validatesOnlyWhenTicketOwnsPublicationTurn() throws Exception {
    RecordingListener listener = new RecordingListener();
    try (Harness harness = Harness.open(2, listener)) {
      try (ConflictScenario scenario = startConflictScenario(harness)) {
        scenario.finish();
        assertEquals(scenario.laterTicket, scenario.initialCompletionTickets.getFirst());
        assertEquals(2, harness.counter());
      }
    }
  }

  @Test
  void restoresAndRetriesConflictUnderSameTicket() throws Exception {
    RecordingListener listener = new RecordingListener();
    try (Harness harness = Harness.open(2, listener)) {
      try (ConflictScenario scenario = startConflictScenario(harness)) {
        scenario.finish();

        assertEquals(scenario.laterTicket, scenario.conflictTicket);
        assertEquals(List.of(scenario.laterTicket), scenario.conflictTickets);
        assertEquals(2, Collections.frequency(scenario.segmentTickets, scenario.laterTicket));
        assertEquals(2, harness.counter());
      }
    }
  }

  @Test
  void discardsEffectsFromConflictedAttempt() throws Exception {
    RecordingListener listener = new RecordingListener();
    try (Harness harness = Harness.open(2, listener)) {
      try (ConflictScenario scenario = startConflictScenario(harness)) {
        List<String> publishedOutput = scenario.finish();

        assertEquals(1, Collections.frequency(publishedOutput, "conflicted-effect"));
        assertEquals(2, harness.counter());
      }
    }
  }

  @Test
  void rollsBackTickAbortedMutationAndCompletesWithFramedOutput() throws Exception {
    try (Harness harness = Harness.open(1, new RecordingListener())) {
      try (WorldTxn transaction = harness.root.begin()) {
        ObjectValue serverOptions =
            (ObjectValue) transaction.readObjectProperty(0, "server_options").orElseThrow();
        boolean configured =
            transaction.property(serverOptions.value(), "fg_ticks").isPresent()
                ? transaction.writeObjectProperty(
                    serverOptions.value(), "fg_ticks", new IntegerValue(100))
                : transaction.addProperty(
                    serverOptions.value(), "fg_ticks", new IntegerValue(100), 0, 3);
        assertTrue(configured);
        assertTrue(transaction.commit().isCommitted());
      }
      harness.line("PREFIX " + CONNECTION_PREFIX);
      harness.line("SUFFIX " + CONNECTION_SUFFIX);

      List<String> output =
          harness.line(
              "; #0.scheduler_counter = 99; "
                  + "try i = 0; while (1) i = i + 1; endwhile "
                  + "except (ANY) return \"caught\"; endtry return \"completed\";");

      assertEquals(
          List.of(
              CONNECTION_PREFIX,
              CONNECTION_PREFIX,
              "Task ran out of ticks",
              CONNECTION_SUFFIX),
          output);
      assertEquals(0, harness.counter());
    }
  }

  @Test
  void releasesEveryChildRevisionAfterCommitConflictAndFailure() throws Exception {
    RecordingListener listener = new RecordingListener();
    try (Harness harness = Harness.open(2, listener)) {
      harness.line("; return 1;");
      try (ConflictScenario scenario = startConflictScenario(harness)) {
        scenario.finish();
      }
      try {
        harness.runtime.executeLine(-999, "; return 1;");
        fail("unknown connection request must fail");
      } catch (IllegalArgumentException expected) {
        assertEquals("unknown connection #-999", expected.getMessage());
      }

      assertEquals(1, retainedRevisionCount(harness.root));
    }
  }

  @Test
  void hasNoSerializedFallbackMode() throws Exception {
    assertFalse(Modifier.isSynchronized(MooRuntime.class.getMethod("openConnection", long.class).getModifiers()));
    assertFalse(
        Modifier.isSynchronized(
            MooRuntime.class.getMethod("executeLine", long.class, String.class).getModifiers()));
    assertFalse(
        Modifier.isSynchronized(MooRuntime.class.getMethod("closeConnection", long.class).getModifiers()));
    assertEquals(
        1,
        List.of(MooRuntime.class.getDeclaredFields()).stream()
            .filter(field -> field.getType() == PublicationScheduler.class)
            .count());
    assertTrue(
        List.of(MooRuntime.class.getDeclaredFields()).stream()
            .noneMatch(field -> field.getName().toLowerCase(Locale.ROOT).contains("serial")));
  }

  @Test
  void completesForkParentBeforeChildAndNeverPublishesChildOutputToParent() throws Exception {
    try (Harness harness = Harness.open(2, new RecordingListener())) {
      TaskRegistry registry = field(harness.scheduler, "taskRegistry", TaskRegistry.class);

      CompletableFuture<List<String>> parent =
          harness.lineAsync(
              "; fork task_id (5) suspend(5); return 99; endfork "
                  + "tasks = queued_tasks(); "
                  + "return length(tasks) > 0 "
                  + "&& tasks[length(tasks)][1] == task_id;");
      List<String> output = parent.get(3, TimeUnit.SECONDS);

      assertTrue(output.stream().noneMatch(line -> line.contains("99")), output.toString());
      assertTrue(output.contains("{1, 1}"), output.toString());
      assertEquals(1, registry.size());
    }
  }

  @Test
  void removesForkFromRegistryAfterChildTerminalCompletion() throws Exception {
    try (Harness harness = Harness.open(2, new RecordingListener())) {
      TaskRegistry registry = field(harness.scheduler, "taskRegistry", TaskRegistry.class);

      harness
          .lineAsync("; fork (0) suspend(0.1); return 99; endfork return 1;")
          .get(3, TimeUnit.SECONDS);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while (registry.size() != 0 && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertEquals(0, registry.size());
    }
  }

  @Test
  void killedDelayedForkNeverPublishesItsWorldMutation() throws Exception {
    try (Harness harness = Harness.open(2, new RecordingListener())) {
      TaskRegistry registry = field(harness.scheduler, "taskRegistry", TaskRegistry.class);

      List<String> output =
          harness.line(
              "; fork task_id (0.2) #0.scheduler_counter = 99; endfork "
                  + "return kill_task(task_id);");
      TimeUnit.MILLISECONDS.sleep(400);

      assertTrue(output.contains("{1, 0}"), output.toString());
      assertEquals(0, harness.counter());
      assertEquals(0, registry.size());
    }
  }

  private static ConflictScenario startConflictScenario(Harness harness) throws IOException {
    try (WorldTxn transaction = harness.root.begin()) {
      ObjectValue serverOptions =
          (ObjectValue) transaction.readObjectProperty(0, "server_options").orElseThrow();
      boolean configured =
          transaction.property(serverOptions.value(), "fg_ticks").isPresent()
              ? transaction.writeObjectProperty(
                  serverOptions.value(), "fg_ticks", new IntegerValue(20_000_000))
              : transaction.addProperty(
                  serverOptions.value(), "fg_ticks", new IntegerValue(20_000_000), 0, 3);
      assertTrue(configured);
      assertTrue(transaction.commit().isCommitted());
    }
    harness.resetCounter();
    Recording events = new Recording();
    events.enable(TaskSegmentEvent.class).withoutThreshold();
    events.enable(WorldConflictEvent.class).withoutThreshold();
    events.start();
    Path eventFile = Files.createTempFile("banteng-publication-", ".jfr");

    long earlierTicket = harness.scheduler.nextTicket();
    CompletableFuture<List<String>> earlierRoot =
        harness.lineAsync(
            CONNECTION_ID,
            "; value = #0.scheduler_counter; "
                + "i = 0; while (i < 2000000) i = i + 1; value = value + 0; endwhile "
                + "#0.scheduler_counter = value + 1; "
                + "return 1;");
    while (harness.scheduler.nextTicket() == earlierTicket) {
      Thread.onSpinWait();
    }
    long laterTicket = harness.scheduler.nextTicket();
    CompletableFuture<List<String>> laterRoot =
        harness.lineAsync(
            SECOND_CONNECTION_ID,
            "; #0.scheduler_counter = #0.scheduler_counter + 1; "
                + "notify(player, \"conflicted-effect\"); "
                + "return 1;");
    while (harness.scheduler.nextTicket() == laterTicket) {
      Thread.onSpinWait();
    }
    return new ConflictScenario(
        earlierTicket,
        laterTicket,
        earlierRoot,
        laterRoot,
        events,
        eventFile);
  }

  private static void awaitRelease(CountDownLatch release) {
    try {
      release.await();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("test executor blocker interrupted", interrupted);
    }
  }

  private static PublicationScheduler scheduler(MooRuntime runtime) {
    return field(runtime, "scheduler", PublicationScheduler.class);
  }

  private static int readySize(PublicationScheduler scheduler) {
    synchronized (scheduler) {
      return field(scheduler, "ready", Queue.class).size();
    }
  }

  private static int retainedRevisionCount(WorldTxn root) {
    try {
      var method = WorldTxn.class.getDeclaredMethod("retainedRevisionCount");
      method.setAccessible(true);
      return (int) method.invoke(root);
    } catch (ReflectiveOperationException error) {
      throw new LinkageError(error.getMessage(), error);
    }
  }

  private static <T> T field(Object owner, String name, Class<T> type) {
    try {
      Field field = owner.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return type.cast(field.get(owner));
    } catch (ReflectiveOperationException error) {
      throw new LinkageError(error.getMessage(), error);
    }
  }

  private static final class ConflictScenario implements AutoCloseable {
    private final long earlierTicket;
    private final long laterTicket;
    private final CompletableFuture<List<String>> earlierRoot;
    private final CompletableFuture<List<String>> laterRoot;
    private final Recording events;
    private final Path eventFile;
    private List<Long> initialCompletionTickets = List.of();
    private List<Long> segmentTickets = List.of();
    private List<Long> conflictTickets = List.of();
    private long conflictTicket = -1;
    private boolean eventsRead;

    private ConflictScenario(
        long earlierTicket,
        long laterTicket,
        CompletableFuture<List<String>> earlierRoot,
        CompletableFuture<List<String>> laterRoot,
        Recording events,
        Path eventFile) {
      this.earlierTicket = earlierTicket;
      this.laterTicket = laterTicket;
      this.earlierRoot = earlierRoot;
      this.laterRoot = laterRoot;
      this.events = events;
      this.eventFile = eventFile;
    }

    List<String> finish() throws IOException {
      earlierRoot.join();
      List<String> output = laterRoot.join();
      readEvents();
      return output;
    }

    private void readEvents() throws IOException {
      if (eventsRead) {
        return;
      }
      events.stop();
      events.dump(eventFile);
      List<RecordedEvent> recorded = RecordingFile.readAllEvents(eventFile);
      List<RecordedEvent> segments =
          recorded.stream()
              .filter(event -> event.getEventType().getName().equals("moo.TaskSegment"))
              .sorted(Comparator.comparing(RecordedEvent::getEndTime))
              .toList();
      segmentTickets = segments.stream().map(event -> event.getLong("ticket")).toList();
      initialCompletionTickets =
          segmentTickets.stream()
              .filter(ticket -> ticket == earlierTicket || ticket == laterTicket)
              .distinct()
              .toList();
      conflictTickets =
          recorded.stream()
              .filter(event -> event.getEventType().getName().equals("moo.WorldConflict"))
              .sorted(Comparator.comparing(RecordedEvent::getEndTime))
              .map(event -> event.getLong("ticket"))
              .toList();
      if (conflictTickets.isEmpty()) {
        throw new AssertionError(
            "no world conflict; segment tickets=" + segmentTickets);
      }
      conflictTicket = conflictTickets.getFirst();
      eventsRead = true;
      events.close();
      Files.delete(eventFile);
    }

    @Override
    public void close() throws IOException {
      events.close();
      Files.deleteIfExists(eventFile);
    }
  }

  private static final class Harness implements AutoCloseable {
    private final WorldTxn root;
    private final MooRuntime runtime;
    private final PublicationScheduler scheduler;

    static Harness open(int workers, ListenerControl listener) throws IOException {
      WorldTxn root = new LambdaMooV4Reader().read(FIXTURE);
      MooRuntime runtime = new MooRuntime(root, listener, workers);
      PublicationScheduler scheduler = scheduler(runtime);
      Harness harness = new Harness(root, runtime, scheduler);
      runtime.openConnection(CONNECTION_ID, 0, true, new MapValue(Map.of()));
      runtime.executeLine(CONNECTION_ID, "connect Wizard");
      runtime.openConnection(SECOND_CONNECTION_ID, 0, true, new MapValue(Map.of()));
      runtime.executeLine(SECOND_CONNECTION_ID, "connect Wizard");
      harness.resetCounter();
      return harness;
    }

    private Harness(WorldTxn root, MooRuntime runtime, PublicationScheduler scheduler) {
      this.root = root;
      this.runtime = runtime;
      this.scheduler = scheduler;
    }

    List<String> line(String source) {
      return runtime.executeLine(CONNECTION_ID, source);
    }

    CompletableFuture<List<String>> lineAsync(String source) {
      return lineAsync(CONNECTION_ID, source);
    }

    CompletableFuture<List<String>> lineAsync(long connectionId, String source) {
      return CompletableFuture.supplyAsync(() -> runtime.executeLine(connectionId, source));
    }

    void resetCounter() {
      try (WorldTxn transaction = root.begin()) {
        boolean written =
            transaction.property(0, "scheduler_counter").isPresent()
                ? transaction.writeObjectProperty(
                    0, "scheduler_counter", new IntegerValue(0))
                : transaction.addProperty(
                    0, "scheduler_counter", new IntegerValue(0), 0, 3);
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

    @Override
    public void close() {
      scheduler.close();
    }
  }

  private static final class RecordingListener implements ListenerControl {
    @Override
    public int listen(long handler, int port, boolean printMessages) {
      return 77;
    }

    @Override
    public boolean unlisten(int port) {
      return true;
    }

    @Override
    public void writeConnection(long connectionId, List<String> output) {}

    @Override
    public void bootConnection(long connectionId, List<String> output) {}

    @Override
    public void setConnectionBinary(long connectionId, boolean binary) {}

    @Override
    public void shutdown() {}
  }
}
