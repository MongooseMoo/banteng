package world.mongoose.banteng.world;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import world.mongoose.banteng.value.MooValue;

/** The durable state needed to resume one VM. */
public record SuspendedTask(
    long taskId,
    long scheduledEpochSecond,
    MooValue resumeValue,
    MooValue taskLocal,
    int rootActivationVector,
    long functionId,
    long maxStackDepth,
    Optional<String> interruptionStatus,
    List<SuspendedActivation> activations) implements DurableTask {
  /** Takes immutable copies of task-owned state. */
  public SuspendedTask {
    Objects.requireNonNull(resumeValue, "resumeValue");
    Objects.requireNonNull(taskLocal, "taskLocal");
    Objects.requireNonNull(interruptionStatus, "interruptionStatus");
    activations = List.copyOf(activations);
    if (activations.isEmpty()) {
      throw new IllegalArgumentException("suspended task requires an activation");
    }
  }
}
