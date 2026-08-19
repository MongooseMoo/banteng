package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Saturation and retry stress proof for the bounded production scheduler. */
final class ConcurrentSchedulerStressTest {
  @Test
  void saturationConflictsAndRetriesRemainBoundedAndEventuallyProgress(
      @TempDir Path temporaryDirectory) throws Exception {
    int taskCount = 16;
    long[] connections = new long[taskCount + 1];
    for (int index = 0; index < connections.length; index++) {
      connections[index] = -200L - index;
    }
    try (SchedulerTestHarness harness = SchedulerTestHarness.open(2, connections);
        Recording recording = new Recording()) {
      ThreadPoolExecutor executor =
          SchedulerTestHarness.field(harness.scheduler, "executor", ThreadPoolExecutor.class);
      CountDownLatch entered = new CountDownLatch(2);
      CountDownLatch release = new CountDownLatch(1);
      for (int index = 0; index < 2; index++) {
        executor.execute(
            () -> {
              entered.countDown();
              await(release);
            });
      }
      assertTrue(entered.await(3, TimeUnit.SECONDS));
      recording.enable("world.mongoose.banteng.TaskSegment").withThreshold(Duration.ZERO);
      recording.enable("world.mongoose.banteng.WorldConflict").withThreshold(Duration.ZERO);
      recording.start();

      List<CompletableFuture<List<String>>> pending = new ArrayList<>();
      for (int index = 0; index < taskCount; index++) {
        pending.add(
            harness.lineAsync(
                connections[index],
                "; i = 0; while (i < 2000) i = i + 1; endwhile "
                    + "#0.scheduler_counter = #0.scheduler_counter + 1; return task_id();"));
      }
      long saturationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (harness.readySize() == 0 && System.nanoTime() < saturationDeadline) {
        Thread.onSpinWait();
      }
      assertEquals(harness.scheduler.queueCapacity(), executor.getQueue().size());
      assertTrue(harness.readySize() > 0);
      release.countDown();

      for (CompletableFuture<List<String>> future : pending) {
        assertTrue(future.get(30, TimeUnit.SECONDS).stream().anyMatch(line -> line.startsWith("{1, ")));
      }
      assertEquals(taskCount, harness.counter());
      assertTrue(
          harness
              .runtime
              .executeLine(
                  connections[taskCount],
                  "; #0.scheduler_counter = #0.scheduler_counter + 1; return #0.scheduler_counter;")
              .contains("{1, 17}"));

      recording.stop();
      Path events = temporaryDirectory.resolve("scheduler-stress.jfr");
      recording.dump(events);
      List<RecordedEvent> recorded = RecordingFile.readAllEvents(events);
      long conflicts =
          recorded.stream()
              .filter(event -> event.getEventType().getName().equals("world.mongoose.banteng.WorldConflict"))
              .count();
      assertTrue(conflicts > 0, recorded.toString());
      Map<Long, Long> attemptsByTicket =
          recorded.stream()
              .filter(event -> event.getEventType().getName().equals("world.mongoose.banteng.TaskSegment"))
              .collect(
                  Collectors.groupingBy(
                      event -> event.getLong("ticket"), Collectors.counting()));
      assertTrue(
          attemptsByTicket.values().stream().allMatch(attempts -> attempts <= taskCount + 1L),
          attemptsByTicket.toString());
      assertEquals(0, harness.readySize());
      assertEquals(harness.scheduler.nextTicket(), harness.scheduler.nextPublicationTicket());
    }
  }

  private static void await(CountDownLatch release) {
    try {
      release.await();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("scheduler stress blocker interrupted", interrupted);
    }
  }
}
