# WAIF callback names

## Goal
Match Toast's distinction between the local verb name and activation name. Strip the leading colon from local verb while retaining prefixed lookup/calledVerb, and report immutable calledVerb in callers rather than mutable local verb.

## State
Implemented two production expressions in MooVm.callVerb and VmState.callers. Read current target branch dispatch, IndexOps delegation, frame snapshot/restore and current conformance waif_index_callback_context fixture from origin/main. Read Toast activation setup and make_stack_list.

Validation: Java25 ./gradlew --no-daemon :test --tests world.mongoose.banteng.vm.WaifCallbackNamesTest completed successfully with unchanged source through compilation and execution. Authored cases cover WAIF read/write, ordinary call and explicit prefixed call, each immediately and after snapshot/restore/resume; they mutate local verb before calling an observer that reads callers/callers(1). A separate ordinary caller case mutates local verb and expects its original activation name. Snapshot metadata schema and restore logic remain unchanged. No broad local CI or conformance run.

## Decisions and why
Toast execute.cc:737 retains vname in activation. execute.cc:774-780 strips one leading WAIF_VERB_PREFIX for SLOT_VERB, while make_stack_list:481 uses activation.verb for stack names. Current IndexOps and ordinary dispatch already share MooVm.callVerb, so no separate IndexOps change is needed. Keep existing calledVerb snapshot/resume metadata and all permissions/continuation metadata unchanged.

## Surprises
Coordinator's original line ranges predate existing shared callVerb owner; parent confirmed the two-expression scope against current target source. My first focused Gradle command used an unqualified test task selector, which also selected subproject test tasks and failed at errorprone-checks:test. Corrected the invocation to the explicit root :test path; the focused file passed with its normal prerequisites. No source changed during either compilation run.

## Next step
Parent source verification, required review and PR CI/conformance. Merge alongside the independently owned callback command-local repair, preserving both edits to the shared callVerb helper. Local only; no publication.
