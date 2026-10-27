package world.mongoose.banteng.persistence;

import org.jspecify.annotations.Nullable;
import world.mongoose.banteng.value.MooValue;

/** One raw v5/v17 property value slot before inheritance restoration. */
record RawPropertySlot(@Nullable MooValue value, boolean clear, long owner, int permissions) {}
