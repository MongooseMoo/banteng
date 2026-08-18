/**
 * Serialized MOO task ingress and ordered effect publication.
 *
 * <p>{@code PublicationScheduler} and {@code TaskRegistry} guard their mutable scheduling state
 * with their own monitors. {@code MooRuntime} likewise guards published connection and pending-read
 * state with its monitor. Task attempts own their {@code WorldTxn} and {@code VmState} instances
 * under a single-owner rule and never share those mutable values between workers.
 *
 * <p>Lock ordering is scheduler monitor before runtime monitor. Publication callbacks that can
 * enter the runtime run outside the scheduler monitor, as encoded by
 * {@code publishVmCompletionOutsideMonitor}; runtime code must not call scheduler operations while
 * holding the runtime monitor.
 */
@NullMarked
package moo.runtime;

import org.jspecify.annotations.NullMarked;
