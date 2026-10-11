# Phase 2 test inventory

## Current state

- Task: read-only comparison of current code and tests against every named Phase 2 deliverable and gate.
- No production or test files will be edited.
- Required surfaces: `V17RoundTripTest`, `WorldTxnTest`, `WorldTxnPropertyTest`, `PublicationSchedulerTest`, `ArchitectureTest`, `WorldAccessArchitectureTest`, `DurableTaskStateArchitectureTest`, `VmSnapshotTest`, and `jcstress`.

## Findings and observations

- Current branch is `master`, 142 commits ahead of `origin/master`; tracked files were clean when the inventory began. Existing untracked notes and analysis artifacts were left untouched.
- Of the named Phase 2 JUnit classes, only `moo.ArchitectureTest` exists. `V17RoundTripTest`, `WorldTxnTest`, `WorldTxnPropertyTest`, `PublicationSchedulerTest`, `WorldAccessArchitectureTest`, `DurableTaskStateArchitectureTest`, and `VmSnapshotTest` are absent.
- The existing `ArchitectureTest` checks package cycles and package-level allowed dependencies. It does not enforce the Phase 2 rule that committed `World`/revision implementations are package-private or reject access from outside `moo.world`.
- The only jcstress source is `VolatilePublicationTest`, a generic volatile-field harness smoke test. The Gradle `jcstress` task filters exclusively to it. It does not exercise committed world publication, and the Phase 2 plan's prose does not name an exact Phase 2 jcstress class.
- `WorldTxn` is a mutable owner of one `World` field. It has no base revision, read set, write set, predicate set, validation, commit/publication descriptor, conflict result, or version-retention protocol. Existing nearby JetCheck coverage models direct create/recycle behavior, not transaction conflict semantics.
- `MooRuntime` has one shared `WorldTxn`; public ingress methods are synchronized. No ticket, executor, publication scheduler, validation/retry, ordered-effect journal, or revision publication implementation was found in production. JFR event definition classes mention those concepts but are observability shells, not scheduler production logic.
- Persistence contains only `LambdaMooV4Reader`; there is no v17 reader/writer or checkpoint implementation to support `V17RoundTripTest`.
- `VmState` is itself live mutable VM state and includes `Optional<CompletableFuture<MooValue>> hostResult` plus `processCpuAnchorNanos`. No durable snapshot type was found. This directly conflicts with the planned durable-state architecture and elapsed-budget proof.
- `MooRuntime.executeStored` implements a single-threaded in-method runnable/timer/future loop over the shared mutable `WorldTxn`. Fork children are run from an `ArrayList`; delayed tasks use `System.nanoTime`; host suspension stores `CompletableFuture` directly. This is not the planned final executor/ticket/publication architecture.
- The application accepts `--checkpoint` but marks it unused and says checkpoint writing is deferred. `LambdaMooV4Reader` accepts only the exact v4 header and rejects v17. There is no `dump_database` builtin entry or `BuiltinSpec`/`CallShape` manifest; the current builtin effect classification is a large independent switch.
- `JetCheckAcceptanceTest.statefulWorldTxnCommandsStayInSyncWithModel` is a nearby property test, but it checks only direct `createObject`/`recycleObject` behavior against an object-ID list. It does not test reads, writes, predicates, conflicts, validation, revisions, or atomic publication, so it cannot substitute for `WorldTxnPropertyTest`.
- `JfrEventDefinitionsTest` constructs and records ticket/commit/conflict/retry/checkpoint event objects with hard-coded values. Those event definitions have no backing production scheduler/checkpointer and cannot substitute for any Phase 2 behavioral test.
- The literal Phase 2 `jcstress` gate is runnable, but it currently proves only that a volatile flag publishes one integer payload. The plan does not name a Phase 2 world jcstress class; it names `WorldPublicationTest` only in Phase 6. Therefore the existing gate must not be reported as Phase 2 world-publication evidence, and inventing a Phase 2 jcstress class would require a plan correction.

## Blocker

- The named gates cannot run because their test classes do not exist. More importantly, most tests cannot be honestly authored yet because their required production abstractions are absent.

## Next action

- Report the exact dependency order and misleading substitutes to the parent agent. No production or test code was edited.

## V17 persistence authority research

- Read-only follow-up requested against `../barn/spec`, `../../src/toaststunt`, and the current Banteng ToastCore fixture/profile.
- Banteng profiles pin ToastStunt commit `aecc51e9449c6e7c95272f0f044b5ba38948459e` and ToastCore commit `1887eacd591d97fdc55d258a76e2167899b1951d`; the local ToastStunt checkout is currently at `e8a353665a106244f5e01edb67239c90411ae584`, so source drift must be reported and pinned-source claims must be verified through Git objects rather than attributed to current HEAD.
- The ToastCore fixture hash matches the profile: `8013b703c61a9894866f836f2b934eada7118cdf0b3cd56181e4bf9205b2f557`. It is v17 and begins with six players, zero pending finalizations, zero clocks, four queued fork tasks.
- Exact owners found: `src/db_file.cc` for top-level ordering/object/program sections; `src/db_io.cc` for line/tagged-value/program encoding; `src/tasks.cc` for queued/suspended/interrupted task records; `src/eval_vm.cc` for durable VM records.
- No blocker in the format research itself. Next step is to inspect pinned-source functions and the fixture's exact section/value/task usage, then report the minimum required grammar without editing implementation files.

## VmSnapshot implementation slice

- Owned production/test scope: `src/main/java/moo/vm/**`, `src/test/java/moo/vm/VmSnapshotTest.java`, and `src/test/java/moo/runtime/DurableTaskStateArchitectureTest.java` only.
- Added the concrete value-only `VmSnapshot` record and began adapting `VmState` CPU accounting so snapshots contain elapsed and remaining CPU nanoseconds instead of a live anchor.
- Live host futures remain only on active `VmState`; the durable snapshot records the host-wait condition as a boolean and never retains the future.
- Work is incomplete and untested. Next action: finish capture/restore of every frame and pending-state component, add the two named tests, run the exact WSL gates, reread the plan, and commit only owned paths.
- No implementation blocker currently known.
- The first WSL gate compiled production and failed test compilation only on a missing `Locale.ROOT`; that exact warning was fixed.
- The rerun compiled both new tests, then `MooVmTest` reported 17 failures because commit `a360c86` landed concurrently and changed `WorldTxn` to require `begin()` before reads/writes. This is outside the snapshot diff but affects the required gate on current HEAD.
- Next action: reconcile the owned VM execution entry with the now-current transaction contract only if the parent confirms that integration is within this slice; otherwise the exact `MooVmTest` gate is externally blocked by the concurrent commit.
- Parent explicitly prohibited transaction ownership in production `MooVm` and expanded only `MooVmTest` scope. The test harness now has child-transaction helpers: one always closes without publication and one explicitly commits. Pure explicit-world call sites and the first read-only world call sites have been converted.
- Remaining work is mechanical but behavior-sensitive: convert each mutation test to commit its child before inspecting the root, keep connection-only state inside one abandoned child, preserve all assertions, then rerun the named gates.
- Converted the pure explicit-world tests to abandoned child transactions; converted computed property, object permission, verb dispatch, recycle, validity, and add-verb tests to explicit child ownership with commits only for expected publication. Connection-option assertions remain within the same abandoned child because connection state is deliberately not part of the versioned root yet.
- Still unconverted: set-verb-info/args, delete-verb, and set-verb-code groups. No assertions have been removed, weakened, skipped, or renamed.
- Restored the required `DurableTaskStateArchitectureTest` after parent clarified that the user's removal instruction concerned the already-removed conformance cleanliness gate, not the Phase 2 item 7 test.
- Completed the authorized `MooVmTest` child-transaction reconciliation, including set-verb-info/args, delete-verb, and set-verb-code. Production `MooVm` still does not begin or commit a `WorldTxn`.
- The first completed run exposed only an invalid pre-existing recycle fixture: parent #3 named absent children #6/#7 and location #10 named absent contents #8/#9. With parent authorization, added reciprocal placeholder records while preserving the test name and every assertion.
- Exact WSL gate passed on 2026-07-18: `JAVA_HOME=/opt/java/25 ./gradlew test --tests moo.vm.VmSnapshotTest --tests moo.runtime.DurableTaskStateArchitectureTest --tests moo.vm.MooVmTest --console=plain` (`BUILD SUCCESSFUL`, 115 tests).
- No current blocker. Next action: run `git diff --check`, reread the Phase 2 plan after the passing gate, inspect/stage only the authorized VM and test paths, commit the kept slice, and report the commit and scheduler snapshot API.
