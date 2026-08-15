package moo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Production-executor proof that independent VM segments execute concurrently. */
final class ConcurrentExecutionTest {
  @Test
  void independentCpuSegmentsOverlapOnDistinctProductionWorkers(@TempDir Path temporaryDirectory)
      throws Exception {
    try (SchedulerTestHarness harness = SchedulerTestHarness.open(2, -61, -62);
        Recording recording = new Recording()) {
      recording.enable("moo.TaskSegment").withThreshold(Duration.ZERO);
      recording.start();

      String work =
          "; i = 0; while (i < 250000) i = i + 1; endwhile return i;";
      CompletableFuture<List<String>> first = harness.lineAsync(-61, work);
      CompletableFuture<List<String>> second = harness.lineAsync(-62, work);

      assertTrue(first.get(15, TimeUnit.SECONDS).contains("{1, 250000}"));
      assertTrue(second.get(15, TimeUnit.SECONDS).contains("{1, 250000}"));
      recording.stop();
      Path events = temporaryDirectory.resolve("concurrent-execution.jfr");
      recording.dump(events);

      List<RecordedEvent> segments =
          RecordingFile.readAllEvents(events).stream()
              .filter(event -> event.getEventType().getName().equals("moo.TaskSegment"))
              .filter(event -> event.getThread() != null)
              .filter(event -> event.getThread().getJavaName().startsWith("moo-vm-"))
              .toList();
      assertTrue(segments.size() >= 2, segments.toString());
      assertTrue(
          segments.stream().map(event -> event.getThread().getJavaName()).distinct().count() >= 2,
          segments.toString());
      assertTrue(overlapExists(segments), segments.toString());
      assertEquals(harness.scheduler.nextTicket(), harness.scheduler.nextPublicationTicket());
    }
  }

  private static boolean overlapExists(List<RecordedEvent> segments) {
    for (int left = 0; left < segments.size(); left++) {
      for (int right = left + 1; right < segments.size(); right++) {
        RecordedEvent first = segments.get(left);
        RecordedEvent second = segments.get(right);
        if (!first.getThread().getJavaName().equals(second.getThread().getJavaName())
            && first.getStartTime().isBefore(second.getEndTime())
            && second.getStartTime().isBefore(first.getEndTime())) {
          return true;
        }
      }
    }
    return false;
  }
}
