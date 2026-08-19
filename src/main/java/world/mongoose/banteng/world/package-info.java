/**
 * Immutable versioned world records and the concrete transactional publication path.
 *
 * <p>{@code WorldHistory} guards committed revisions, transaction reference counts, and verb-cache
 * state with its own monitor. Each {@code WorldTxn} is mutable single-owner task state: it must not
 * be shared between task workers, and it reaches {@code WorldHistory} only for synchronized
 * validation, publication, and revision lifetime operations.
 */
@NullMarked
package world.mongoose.banteng.world;

import org.jspecify.annotations.NullMarked;
