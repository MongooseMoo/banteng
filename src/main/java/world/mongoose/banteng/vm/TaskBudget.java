package world.mongoose.banteng.vm;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;

/** Single-owner resource metering for one task. */
public final class TaskBudget {
  private static final ThreadMXBean THREAD_CPU = ManagementFactory.getThreadMXBean();
  private long remainingTicks;
  private long elapsedCpuNanos;
  private long remainingCpuNanos;
  private final long maxStackDepth;
  private long segmentCpuAnchorNanos = -1;

  public TaskBudget(long remainingTicks, long secondsLimit, long maxStackDepth) {
    this.remainingTicks = remainingTicks;
    remainingCpuNanos = Math.max(0L, TimeUnit.SECONDS.toNanos(secondsLimit));
    this.maxStackDepth = maxStackDepth;
  }

  long remainingTicks() {
    return remainingTicks;
  }

  long remainingSeconds() {
    long elapsedNanos = segmentElapsedCpuNanos(currentThreadCpuNanos());
    long remainingNanos =
        Math.max(0L, remainingCpuNanos - Math.min(remainingCpuNanos, elapsedNanos));
    return TimeUnit.NANOSECONDS.toSeconds(remainingNanos);
  }

  void decrementRemainingTicks() {
    remainingTicks--;
  }

  boolean secondsExhausted() {
    return (remainingCpuNanos != 0 || elapsedCpuNanos != 0)
        && Math.max(0L, currentThreadCpuNanos() - segmentCpuAnchorNanos) >= remainingCpuNanos;
  }

  void beginSegment() {
    beginSegment(currentThreadCpuNanos());
  }

  void beginSegment(long currentThreadCpuNanos) {
    if (segmentCpuAnchorNanos < 0) {
      segmentCpuAnchorNanos = currentThreadCpuNanos;
    }
  }

  private long segmentElapsedCpuNanos(long currentThreadCpuNanos) {
    return segmentCpuAnchorNanos < 0
        ? 0
        : Math.max(0L, currentThreadCpuNanos - segmentCpuAnchorNanos);
  }

  static long currentThreadCpuNanos() {
    long nanos = THREAD_CPU.getCurrentThreadCpuTime();
    if (nanos < 0) {
      throw new IllegalStateException("current-thread CPU time is unavailable");
    }
    return nanos;
  }

  private static long saturatedAdd(long left, long right) {
    return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
  }

  long maxStackDepth() { return maxStackDepth; }

  record Capture(long remainingTicks, long elapsedCpuNanos, long remainingCpuNanos, long maxStackDepth) {}

  Capture capture(long currentThreadCpuNanos) {
    long charged = Math.min(remainingCpuNanos, segmentElapsedCpuNanos(currentThreadCpuNanos));
    return new Capture(remainingTicks, saturatedAdd(elapsedCpuNanos, charged), remainingCpuNanos - charged, maxStackDepth);
  }

  static TaskBudget restore(long ticks, long elapsed, long remaining, long stackDepth) {
    TaskBudget budget = new TaskBudget(ticks, 0, stackDepth);
    budget.elapsedCpuNanos = elapsed;
    budget.remainingCpuNanos = remaining;
    return budget;
  }
}
