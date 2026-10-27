package world.mongoose.banteng.world;

import java.util.List;
import java.util.Objects;

/** A restored committed world, durable tasks, and checkpointed connections. */
public record Checkpoint(
    WorldTxn world, List<DurableTask> tasks, List<ActiveConnection> activeConnections) {
  /** Takes an immutable snapshot of the restored task list. */
  public Checkpoint {
    Objects.requireNonNull(world, "world");
    tasks = List.copyOf(tasks);
    activeConnections = List.copyOf(activeConnections);
  }
}
