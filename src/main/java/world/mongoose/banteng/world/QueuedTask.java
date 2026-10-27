package world.mongoose.banteng.world;

import java.util.Map;
import java.util.Objects;
import world.mongoose.banteng.value.MooValue;

/** The durable state needed to restart one delayed fork. */
public record QueuedTask(
    long taskId,
    long scheduledEpochSecond,
    int firstSourceLine,
    String calledVerb,
    String fullVerbName,
    String programSource,
    QueuedEnvironment environment,
    long programmer,
    MooValue verbLocation,
    long taskPlayer,
    boolean debug,
    boolean threadMode) implements DurableTask {
  public QueuedTask(
      long taskId,
      long scheduledEpochSecond,
      int firstSourceLine,
      String calledVerb,
      String fullVerbName,
      String programSource,
      Map<String, MooValue> initialLocals,
      long programmer,
      MooValue verbLocation,
      long taskPlayer,
      boolean debug,
      boolean threadMode) {
    this(
        taskId,
        scheduledEpochSecond,
        firstSourceLine,
        calledVerb,
        fullVerbName,
        programSource,
        QueuedEnvironment.assigned(initialLocals),
        programmer,
        verbLocation,
        taskPlayer,
        debug,
        threadMode);
  }

  public QueuedTask(
      long taskId,
      long scheduledEpochSecond,
      int firstSourceLine,
      String calledVerb,
      String fullVerbName,
      String programSource,
      Map<String, MooValue> initialLocals,
      long programmer,
      MooValue verbLocation,
      long taskPlayer,
      boolean threadMode) {
    this(
        taskId,
        scheduledEpochSecond,
        firstSourceLine,
        calledVerb,
        fullVerbName,
        programSource,
        initialLocals,
        programmer,
        verbLocation,
        taskPlayer,
        true,
        threadMode);
  }

  /** Takes immutable copies of task-owned state. */
  public QueuedTask {
    if (firstSourceLine < 1) {
      throw new IllegalArgumentException("queued-task first source line must be positive");
    }
    Objects.requireNonNull(calledVerb, "calledVerb");
    Objects.requireNonNull(fullVerbName, "fullVerbName");
    Objects.requireNonNull(programSource, "programSource");
    Objects.requireNonNull(environment, "environment");
    Objects.requireNonNull(verbLocation, "verbLocation");
  }

  /** Returns only initialized variables for VM restoration and queued_tasks(). */
  public Map<String, MooValue> initialLocals() {
    return environment.initializedLocals();
  }
}
