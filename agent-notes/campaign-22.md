# Issue 22: task state ownership

## Goal
Extract TaskBudget, EffectOutbox and immutable TaskOrigin from VmState; retain the single frame push supplied by issue83 and replace the eight constructors with VmState(TaskOrigin, TaskBudget).

## State
Authorized campaign implementation, isolated worktree based on issue83. TaskOrigin, TaskBudget and EffectOutbox extracted, canonical VmState constructor installed, direct runtime/VM callers and constructor tests migrated. Removed the obsolete stageRuntimeOutput forwarder and moved background budget construction fully into TaskBudget's constructor. Merged issue83's published set_verb_code repair and migrated its added CompiledVerbTest constructor call. The final integrated authored VmSnapshotTest run passed; no broad local CI gate was run.

## Decisions and why
Smallest change is three direct owners and caller migration, approximately 250–400 moved/new production lines plus mechanical constructor migration. No persistence format change, new context protocol or compatibility constructor facade. Keep snapshot fields flattened for existing codecs. Hand-extract field ownership; use existing OpenRewrite mechanisms where accessor renaming/type migration is actually mechanical.

TaskOrigin defensively owns normalized copied locals and immutable initial metadata. VmState may replace its TaskOrigin with a new immutable value on ROOT push, merging previous origin locals with entered ROOT locals and retaining metadata. This directly replaces initialLocals.putAll in VmState without duplicating MooVm.callVerb's centralized ROOT preparation; runtime constructs empty initial locals before that call.

TaskBudget owns tick count, elapsed/remaining CPU nanos, stack limit, segment anchor and MXBean plumbing. Preserve observational snapshot charging and restore/background reset behavior. Preserve distinct constructor defaults: ordinary foreground 60000 ticks/5 seconds/50 stack; explicit-ticks-only uses zero seconds. Preserve existing inferred location and lowercase verb-key behavior during migration.

EffectOutbox owns all seven queues and one generic copy-and-clear drain. Preserve output order, anonymous identity deduplication and staged notification byte projection. Move behavior with its data; avoid forwarding remnants whose only purpose is compatibility.

## Surprises
Issue83 already removed the frame-push overloads; do not recreate or redo that work. Immutable TaskOrigin does not require its owning reference to be final because ROOT construction supplies locals after the initial state exists.

No ChangeType or ChangeMethodName recipe applies: type/method names remain intact while receiver ownership and constructor argument shape change. Existing recipe patterns were read; no no-op recipe was added.

The first patch failed on an extra terminal newline in captured shell output; corrected the patch context. An initially malformed queue initializer from string replacement was corrected before compilation. Source inspection also removed an incorrect effects() retarget on MooRuntime's VmSnapshot task variable. The first WSL shell invocation failed before Gradle due to PATH quoting; direct WSL exec with fixed Linux PATH corrected that. Unqualified Gradle test selected subproject tests with the focused filter, so it was stopped; the active command uses :test and disables configuration cache.

## Next step
Parent publishes, obtains Claude review and answers findings, obtains green PR CI including the required conformance/schedulerStress/jcstress gates, then merges normally after dependencies. No push, PR, comment or release was published by this worker.

Focused command: WSL direct exec with JAVA_HOME=/opt/java/25, fixed Linux PATH and bash ./gradlew :test --tests world.mongoose.banteng.vm.VmSnapshotTest --no-configuration-cache --console=plain. Final result was BUILD SUCCESSFUL. Coverage includes immutable/copied TaskOrigin ROOT merge; observational CPU capture plus restore/background reset; ordered copy-and-clear outbox drains, anonymous identity deduplication and staged byte projection; existing complete snapshot round trips. Full conformance and concurrency gates remain for PR CI.
