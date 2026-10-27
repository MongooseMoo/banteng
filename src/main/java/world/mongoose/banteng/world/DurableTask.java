package world.mongoose.banteng.world;


/** A durable runtime task record. */
public sealed interface DurableTask permits QueuedTask, SuspendedTask {}
