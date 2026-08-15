package moo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import moo.builtin.BuiltinCatalog;
import org.junit.jupiter.api.Test;

/** Seeded scheduler property proof for conflicts, effects, task IDs, and PRNG order. */
final class ConcurrentSchedulerPropertyTest {
  private static final Pattern RESULT = Pattern.compile("\\{1, \\{(\\d+), (\\d+)}}$");

  @Test
  void generatedConcurrentTasksMatchReadyOrderAndPublishEveryEffectOnce() throws Exception {
    int taskCount = 12;
    long[] connections = new long[taskCount];
    for (int index = 0; index < taskCount; index++) {
      connections[index] = -100L - index;
    }
    try (SchedulerTestHarness harness = SchedulerTestHarness.open(4, connections)) {
      harness.runtime.executeLine(connections[0], "; reseed_random(); return 1;");
      long seed = 0x5eed_600dL;
      BuiltinCatalog catalog = harness.runtime.builtins();
      SchedulerTestHarness.field(catalog, "random", Random.class).setSeed(seed);

      Random generator = new Random(0x600d_f00dL);
      List<CompletableFuture<List<String>>> pending = new ArrayList<>();
      for (int index = 0; index < taskCount; index++) {
        int delay = generator.nextInt(4_000);
        String effect = "phase6-effect-" + index;
        String source =
            "; i = 0; while (i < "
                + delay
                + ") i = i + 1; endwhile "
                + "#0.scheduler_counter = #0.scheduler_counter + 1; "
                + "notify(player, \""
                + effect
                + "\"); return {task_id(), random(1000000)};";
        pending.add(harness.lineAsync(connections[index], source));
      }

      List<TaskResult> results = new ArrayList<>();
      for (int index = 0; index < pending.size(); index++) {
        List<String> output = pending.get(index).get(20, TimeUnit.SECONDS);
        String effect = "phase6-effect-" + index;
        assertEquals(1, output.stream().filter(effect::equals).count(), output.toString());
        results.add(parseResult(output));
      }

      assertEquals(taskCount, harness.counter());
      results.sort(Comparator.comparingLong(TaskResult::taskId));
      assertEquals(taskCount, results.stream().map(TaskResult::taskId).distinct().count());
      Random expected = new Random(seed);
      for (TaskResult result : results) {
        assertEquals(1 + expected.nextLong(1_000_000L), result.randomValue());
      }
      long quiescenceDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (harness.scheduler.nextTicket() != harness.scheduler.nextPublicationTicket()
          && System.nanoTime() < quiescenceDeadline) {
        Thread.onSpinWait();
      }
      assertEquals(harness.scheduler.nextTicket(), harness.scheduler.nextPublicationTicket());
    }
  }

  private static TaskResult parseResult(List<String> output) {
    for (String line : output) {
      Matcher matcher = RESULT.matcher(line);
      if (matcher.find()) {
        return new TaskResult(
            Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)));
      }
    }
    throw new AssertionError("missing task result: " + output);
  }

  private record TaskResult(long taskId, long randomValue) {}
}
