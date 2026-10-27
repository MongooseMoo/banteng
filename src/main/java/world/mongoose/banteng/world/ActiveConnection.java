package world.mongoose.banteng.world;


/** One connection that was active when a checkpoint was written. */
public record ActiveConnection(long player, long listener) {}
