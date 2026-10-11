# Phase 4 execution

## Baseline

- Exact pinned Toast Phase 4 gate: 70 passed, 1 declared skip.
- Exact Banteng Phase 4 gate: 6 passed, 1 declared skip, 55 failed, 9 dependency errors.
- First causal failure: `queued_tasks()` returns `E_VERBNF`; fork suites then error because their declared `queued_tasks` provider failed.

## Plan repair

- The plan required deletion of a `queued_tasks()` placeholder, but no such production or test artifact exists.
- Corrected the Phase 4 prerequisite to install and register the real task-registry-backed implementation before other source work.
- Committed as `a50b8c5 docs: fix Phase 4 queued tasks prerequisite`.

## First source slice

- Keep the target on anonymous fork publication, scheduler-owned live task registry, parent/child response independence, and the real registry-backed `queued_tasks()` builtin.
- Do not include named fork parsing/binding, `task_id()`, or `kill_task()` in this slice.
- Exact affected managed rows are the four queued_tasks capability rows, three fork_observation rows, and anonymous fork timing rows.

## Blocker

- None.

## Next action

- Implement the first source slice with direct scheduler and manifest tests, run the targeted Java and managed gates, then commit or fully revert it before starting named task identity.

## Pinned Toast queued_tasks authority

- Pinned source is `/root/src/toaststunt` at `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- `queued_tasks` accepts zero through two integer arguments; default returns visible task rows, one truthy argument adds variables, and a truthy second argument returns the visible count.
- Default rows contain exactly 10 fields: task id, scheduled-start epoch seconds, obsolete clock id 0, background tick budget, programmer, verb location, verb name, source line, `this`, and approximate bytes.
- Wizard sees all rows; nonwizard sees only reading tasks for its player and forked/suspended tasks programmed by it.
- The exact first-slice managed rows require correct integer id, future integer start time, integer zero clock id, task discoverability, and count growth.
- Production should emit the complete 10-field Toast row now, not a proprietary short row.

## Current implementation state

- Coding agent has designed but not yet edited the slice.
- The sole registry will remain owned by `PublicationScheduler`; parent ingress completion will no longer wait for or aggregate child output/failure.
- Exact next action is to give the coding agent this Toast row authority and review its first tracked diff before tests.

## Direct implementation composition

- The delegated coding turn produced no tracked edits and was interrupted.
- Implement `moo.runtime.TaskRegistry` as the named Phase 4 artifact; `PublicationScheduler` is its sole mutator and owns its field.
- `MooRuntime` creates the one registry instance and passes its existing `BuiltinHandler` method reference into `BuiltinCatalog`; this avoids a new adapter interface and avoids a forbidden builtin-to-runtime package dependency cycle.
- The registry will emit complete 10-field Toast rows and apply wizard/programmer visibility.
- Parent completion will be keyed only by the ingress task ID; fork child terminal output and failure will not complete, delay, or contaminate the parent future.

## Exact next action

- Add `TaskRegistry`, wire it through `BuiltinCatalog`, `MooRuntime`, and `PublicationScheduler`, delete the obsolete parent/child aggregation maps, add focused tests, then run targeted gates before deciding to keep or revert the slice.

## First source slice in progress

- Added `moo.runtime.TaskRegistry` with scheduler mutation, programmer/wizard filtering, exact zero-through-two argument modes, and complete 10-field Toast rows.
- Added `BuiltinOwner.TASK` and the manifest `queued_tasks` entry using the existing `BuiltinHandler` contract.
- Wired one registry instance from `MooRuntime` into both the catalog handler and `PublicationScheduler`; scheduler is the sole writer.
- `PublicationScheduler` now registers every published fork child with its assigned ID, rounded scheduled-start epoch, programmer, verb location, locals, and `this`.
- No named fork, `task_id`, `kill_task`, listener, fixture, or plan surface is in this source slice.

## Remaining work in this slice

- Replace the obsolete root/child completion aggregation with exact ingress-task completion and registry removal.
- Add focused registry/catalog/scheduler tests, compile, run targeted managed rows, and either commit the kept slice or fully restore it.

## First test attempt

- Completed the source wiring, deleted the root/child aggregation path, and added `TaskRegistryTest`, `BuiltinCatalogTest` coverage, and a parent-independence `PublicationSchedulerTest`.
- The first targeted Gradle invocation produced only task-graph calculation and then no task output for more than three minutes, far beyond the observed two-second cached baseline.
- The exact wrapper PID was `66871`; it was terminated with `SIGTERM` after the timeout condition. No source was changed in response.
- WSL has one long-lived Gradle daemon and many old worker daemons, so the hang is not yet evidence of a code/test defect.

## Exact next action

- Run compile-only with a bounded external timeout to distinguish Gradle/daemon state from Java compilation, then inspect the exact compiler or test failure before patching.

## Gradle diagnosis

- First compile exposed only two concrete issues: `BuiltinCatalog.Result.value` was package-private and `PublicationSchedulerTest` lacked the `TimeUnit` import. Both were corrected.
- Bounded `compileTestJava` then passed in 31 seconds.
- The same targeted test selection twice timed out during Gradle task-graph calculation before any test task or test result.
- Therefore there is still no semantic test failure. The active diagnostic is Gradle configuration-cache/daemon behavior.
- Project instructions explicitly permit `--no-configuration-cache --offline` for intermediate targeted runs.

## Exact next action

- Run the unchanged targeted class selection with `--no-configuration-cache --offline` and a bounded timeout, then address only an actual compiler/test result.

## First source slice implementation inventory

- The tracked worktree is clean on `master`; existing changes are untracked notes and tooling artifacts only.
- `PublicationScheduler` currently assigns child IDs in `enqueuePendingForks`, records child-to-root ownership, waits for `pendingChildren` before completing ingress, and combines child output into the parent response.
- No production task registry is exposed to the builtin path. `BuiltinHandler` receives execution/world values only, and the manifest contains `suspend` but not `queued_tasks`.
- The implementation must remove the parent/descendant aggregation state, register each published fork child under its assigned scheduler ID and scheduled start time, preserve that registry entry across ready/delayed/suspended states, and remove it at terminal completion.
- The real `queued_tasks` manifest handler must read that scheduler-owned registry; it must not create a second registry.
- Direct tests will prove parent completion and output independence, live registry state and removal, and manifest dispatch. Named fork binding, `task_id`, `kill_task`, listener/lifecycle work, conformance fixtures, and the plan remain out of scope.

## Wiring observation

- `MooRuntime` currently constructs `BuiltinCatalog` before `PublicationScheduler`; focused VM uses also construct a standalone catalog.
- The kept slice must preserve standalone catalog construction while production catalog dispatch reads the concrete runtime scheduler registry. The scheduler remains the sole mutable registry owner; the builtin receives only a read operation supplied by runtime composition.
- The existing scheduler root aggregation maps (`rootTaskByTask`, `pendingChildren`, `rootOutput`, `childOutput`, `returnedRoots`) exist solely to wait for descendants and combine their output. This slice should delete that aggregation path and complete only an ingress entry's own future.
- Next action: patch scheduler registry/lifecycle and production builtin wiring, then add focused scheduler and manifest tests.

## Named fork syntax/compiler slice

- `Ast.Fork` now carries an optional task-ID variable; the parser accepts the optional identifier between `fork` and the delay, and the unparser preserves it.
- The compiler uses only existing opcodes: named forks emit `STORE_LOCAL <variable>` after `FORK`, while anonymous forks emit `POP` to consume the scheduler-supplied ID.
- Focused parser, unparser, and compiler tests cover named syntax/rendering/lowering and anonymous stack balance.
- VM/runtime files are outside this slice and remain unchanged here.
- Next action: run the exact affected Java test classes under WSL Java 25, then report the uncommitted diff and results.

## Named fork syntax/compiler verification

- The exact Java 25 WSL run selected `MooParserTest`, `MooUnparserTest`, and `MooCompilerTest`.
- Gradle completed configuration but produced no task output afterward; on parent direction, the exact wrapper process PID 6950 was terminated rather than waited further.
- Verification is blocked by that known Gradle configuration hang; no test failure was reported before termination.

## Targeted authority result after implementation

- The interrupted test edit is intact and `git diff --check` passes.
- `compileTestJava` passes with the current source slice.
- The targeted Java authority ran 39 tests and failed exactly the two new scheduler lifecycle tests.
- Both parent futures timed out after three seconds, including the case whose fork child suspends for five seconds. This proves the parent still does not complete independently; it is a real runtime failure, not the earlier narrow-test timing problem.
- The current diff has removed the explicit root/child aggregation maps, so the remaining cause is elsewhere in publication scheduling or runtime submission/completion.

## Exact next action

- Trace the scheduler ticket/publication sequence for the failing parent and child, identify why the parent ingress future is not completed at its own terminal publication, patch only that cause, and rerun the unchanged targeted authority.

## Proven scheduler failure cause

- The JUnit XML captured the worker-thread exception hidden behind both future timeouts: `enqueuePendingForks` called `frames().getFirst()` on a freshly created fork snapshot whose frame list is intentionally empty.
- The resulting `NoSuchElementException` escaped publication before `finishSuccess`, stranding the parent ingress future.
- The fork snapshot already carries the authoritative `initialProgrammer`, `initialVerbLocation`, and `initialLocals`, so the correction reads those fields directly rather than inventing another metadata path.
- The first corrected compile failed only because the local name `child` collided with the existing `SuspendedWork child` local in the same method.

## Exact next action

- Rename the snapshot local without changing behavior, rerun the unchanged 39-test targeted authority, then reread Phase 4 if it passes.

## First kept-metric candidate result

- After the one-name compile correction, the unchanged targeted Java authority passed all 39 tests in 45 seconds.
- The controlling Phase 4 section was reread before the managed run.
- The exact seven-file Banteng Phase 4 authority improved from the recorded baseline of 6 passed / 55 failed / 9 dependency errors / 1 skipped to 10 passed / 60 failed / 1 skipped.
- All four `queued_tasks` capability rows now pass, so the source slice has a measured improvement.
- Every fork observation/timing row still fails by transport timeout when a live fork exists. The temporary managed-run server directory was already removed, so its log cannot be read after completion.

## Exact next action

- Rerun the exact `fork_observation.yaml` target surface alone to preserve a manageable failure report and identify the live-task path exception; do not widen to named fork, kill, listener, or scheduling work before this slice is reconciled.

## Registry slice reconciliation

- Running `fork_observation.yaml` alone correctly produced dependency errors because its declared `queued_tasks` provider was absent. Rerunning with the exact provider preserved that dependency.
- The provider-plus-consumer run produced 4 provider passes and 3 consumer failures: `fork_creates_task` returned 0, `fork_with_delay_shows_future_start` returned `E_VERBNF`, and `multiple_forks_all_visible` returned 0.
- The zero/multiple results prove forks collected during a parent VM segment are not registered until that segment publishes, so `queued_tasks()` later in the same parent cannot see them. This belongs to the next fork-boundary/task-ID slice, not the completed registry manifest/row slice.
- Exact `./gradlew check` passed in 31 seconds, then the Phase 4 plan was reread. `git diff --check` passes.
- Pinned Toast `src/tasks.cc` confirms the implemented argument modes exactly: variables only when there is exactly one truthy argument; count only when there are exactly two arguments and the second is truthy. It also confirms the 10/11-field fork row layout.
- The kept slice currently has the intended six tracked modifications plus untracked `TaskRegistry.java` and `TaskRegistryTest.java`; all `notes-*.md`, `prompts/`, and `pyghidra_mcp_projects/` remain untracked and must not be staged.

## Exact next action

- Stage only the eight intended source/test files, commit the measured registry/parent-independence slice, verify the tracked worktree is clean, then begin the fork publication-boundary and task-ID binding slice required for same-task observability.

## Registry slice committed

- Staged only the eight intended source/test files; no notes or tooling artifacts were staged.
- Committed `f325ab4 feat: add scheduler-owned task registry` with 8 files, 394 insertions, and 54 deletions.
- The tracked worktree is clean after the commit.

## Fork-boundary slice inventory

- Two existing subagents are performing read-only compiler/VM and scheduler boundary reviews.
- Current `Ast.Fork` stores only delay and body; `MooParser.parseFork` accepts only `fork (delay)`, so named `fork id (delay)` is absent.
- `MooCompiler` emits delay then one `FORK` opcode whose operand is the child-vector index; there is no named-variable operand or post-boundary assignment instruction.
- `MooVm.fork` validates numeric nonnegative delay, increments the parent instruction pointer, and records `ForkRequest`; the VM already stops with `Outcome.FORKED`.
- `PublicationScheduler.executeSegment` currently defeats that existing boundary by collecting the request, calling `continueAfterFork()`, and continuing the parent within the same worker segment. That is why the scheduler cannot assign/register the child before the parent executes `queued_tasks()`.

## Exact next action

- Inspect the existing local-variable write opcode/state operation and snapshot resume path, then implement the smallest parser/compiler/VM/scheduler change that publishes the fork boundary, registers and queues the child in ticket order, resumes the same parent task, and writes the assigned ID only for named forks.

## Fork-boundary design confirmed and in progress

- Both read-only subagent audits independently confirmed the same defect and publication-boundary correction. The syntax/compiler audit identified the existing operand stack plus `STORE_LOCAL`/`POP` as the smallest binding path; no new opcode, interface, helper, fork-request field, or snapshot schema is needed.
- The syntax/compiler agent is now implementing only Ast/parser/unparser/compiler changes and focused tests; it is not touching VM/runtime files.
- `VmState.continueAfterFork` now accepts an assigned `IntegerValue`, pushes it on the parent operand stack, clears the request, and returns to RUNNING.
- `PublicationScheduler.executeSegment` now returns the existing `SegmentResult.boundary` at `FORKED` instead of continuing the parent speculatively.
- The existing publication switch now handles `FORKED` directly: allocate/register one child under the monitor, advance the completed fork-boundary publication ticket exactly once, enqueue the same parent task under a fresh ticket with the child ID as wake value, enqueue/time the child, then dispatch only after the registry and queues are complete. The obsolete general pending-fork enqueue method was deleted.
- Parent is assigned the first fresh ready ticket and a zero-delay child the next ticket; delayed children receive a ticket only when their timer wakes. Registry insertion occurs before either dispatch.
- `MooVmTest` now resumes the anonymous parent with a scheduler ID and proves it reaches its own return after the fork boundary.

## Exact next action

- Integrate the agent's syntax/compiler edit, add the scheduler proof that a named fork returns the same ID visible in `queued_tasks()`, compile, and run the focused parser/compiler/VM/scheduler authority before any managed row.

## Fork-boundary slice verification

- The syntax/compiler subagent completed the seven intended parser/compiler/test files and made no VM/runtime edits. Its isolated Gradle wrapper hung during configuration and was terminated without a test result; the combined root-run authority superseded it.
- The combined Java 25 authority for parser, unparser, compiler, VM, scheduler, registry, catalog, and architecture passed in 1 minute 45 seconds.
- The Phase 4 plan was reread before each managed target run.
- Provider plus `fork_observation.yaml` improved from 4 passed / 3 failed to 6 passed / 1 failed. `fork_creates_task` and `multiple_forks_all_visible` now pass. The sole remaining row fails at absent `time()` with `E_VERBNF`, not fork behavior.
- Provider plus `fork_timing.yaml` now passes 9 of 10 rows. Zero-delay execution, delayed queue visibility, named fork ID binding, float delay, and zero-delay queue visibility all pass. The sole remaining row fails at absent `kill_task()` with `E_VERBNF`.
- These results directly prove scheduler-assigned named IDs, same-parent queued-task visibility, parent-before-child publication behavior, delayed registration, float delays, and actual zero-delay child execution.

## Exact next action

- Run exact `./gradlew check` and `git diff --check`, audit and commit this measured fork-boundary slice, then implement `time()` as the next manifest builtin slice and rerun the unfinished observation row before moving to `kill_task()`.

## Fork-boundary slice committed

- Exact `./gradlew check` passed in 29 seconds, the Phase 4 plan was reread, and `git diff --check` passed.
- Audited and staged only the 11 syntax/compiler/VM/scheduler test and production files.
- Committed `39af548 feat: publish fork task boundaries`; the tracked worktree is clean afterward.

## `time()` builtin authority

- The next exact failing observation row calls `time()` and receives `E_VERBNF`.
- Pinned Toast `/root/src/toaststunt/src/numbers.cc` defines `bf_time` as a zero-argument builtin returning an integer from `time(nullptr)` and registers it with minimum 0, maximum 0 arguments.
- Banteng has no `time` manifest entry or implementation. Its nondeterministic clock read belongs on the existing irrevocable builtin path, analogous to `random`, so conflicts cannot expose/repeat a speculative clock value.
- The first patch attempt changed nothing because its import context omitted existing imports; current tracked state remains clean.

## Exact next action

- Add the exact zero-argument irrevocable VM-owned `time` manifest entry returning epoch seconds, add focused catalog coverage, run that Java authority and the provider-plus-observation managed surface, then commit or revert the slice before `kill_task()`.

## `time()` slice result

- Added only the exact zero-argument `time` manifest entry, VM owner, fixed zero cost, ANY permission, irrevocable effect, returning current epoch seconds as an integer.
- Added focused catalog coverage for the manifest contract and a before/after epoch-second value bound.
- Focused `BuiltinCatalogTest` plus `ArchitectureTest` passed in 1 minute 27 seconds.
- The Phase 4 plan was reread, then provider plus `fork_observation.yaml` passed all 7 rows; the previously failing future-start row now passes.
- The Phase 4 plan was reread again, and exact `./gradlew check` passed in 30 seconds.

## Exact next action

- Run `git diff --check`, audit and commit only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`, verify tracked-clean state, then derive and implement exact `kill_task()` cancellation semantics for the one remaining fork-timing row.

## `time()` committed; `kill_task()` authority and implementation state

- `git diff --check` passed; committed only the two intended time files as `819fd23 feat: add Toast-compatible time builtin`; tracked state was clean afterward.
- Pinned Toast `src/tasks.cc` defines `kill_task(INT)` with these results: `E_INVARG` for no matching task, `E_PERM` for a nonwizard killing another programmer's task, zero/no-value success for an allowed external task, and current-task abort when the target is the caller.
- The active exact row needs delayed-fork cancellation: named delayed child, wizard `kill_task(id)` returns 0, timer elapses, and child world mutation never occurs.
- `TaskRegistry` now has authoritative cancellation state, validates existence and programmer/wizard permission, removes a killed task from queued visibility, marks it canceled, and returns zero; terminal removal also clears cancellation state.
- `BuiltinCatalog` now accepts the real kill handler alongside the existing queued-task handler, registers exact one-INT `kill_task` as ANY/fixed-zero/IRREVOCABLE/TASK, and exposes `Result.error` to the concrete runtime owner. Default focused catalogs return `E_INVARG`.
- MooRuntime wiring, scheduler timer discard, and tests are not yet patched; no compilation or test has run for this slice.

## Exact next action

- Wire the same TaskRegistry kill handler into both production runtime constructors, discard canceled delayed/suspended work before it receives a new ticket, add registry/catalog/scheduler tests, then run the focused Java authority and exact provider-plus-fork-timing surface.

## `kill_task()` focused authority

- MooRuntime now supplies the same scheduler-owned TaskRegistry's queued and kill handlers to both production catalog constructors.
- PublicationScheduler drops canceled delayed or suspended work in the existing enqueue-ready/enqueue-wake paths before assigning a fresh ticket.
- Catalog tests prove the exact one-INT shape, TASK owner, ANY permission, irrevocable effect, handler dispatch, and type/arity errors.
- Registry tests prove nonwizard cross-owner `E_PERM`, missing `E_INVARG`, wizard success zero, immediate visibility removal, and timer cancellation consumption.
- Scheduler integration proves a named delayed fork killed by its parent returns zero, never mutates world state after its timer, and leaves no live registry row.
- Focused catalog/registry/scheduler/architecture authority passed in 1 minute 34 seconds; `git diff --check` passed before the run.

## Exact next action

- Reread Phase 4, run exact provider-plus-`fork_timing.yaml`, then if all 10 rows pass run `./gradlew check`, audit and commit this seven-file kill slice before returning to the full Phase 4 failure list.

## `kill_task()` committed and full Phase 4 rerun

- Exact provider plus `fork_timing.yaml` passed all 10 rows in 9.20 seconds.
- The Phase 4 plan was reread; exact `./gradlew check` passed in 30 seconds; the plan was reread again; `git diff --check` passed.
- Audited and committed only the seven intended kill files as `76323fc feat: cancel delayed fork tasks`; tracked state is clean.
- The exact seven-file Banteng Phase 4 authority then completed with 11 passed, 59 failed, and 1 declared skip in 253.09 seconds, versus the previous full-run 10 passed, 60 failed, and 1 skip.
- Full-run ordering still causes later fork rows to time out after earlier lifecycle/scheduling failures; isolated exact provider+fork runs remain fully passing. This is not evidence to change fork code.
- The first remaining causal failure is the first connection-lifecycle audit setup step, which returns `E_VERBNF` while clearing its audit properties. The next slice must identify the exact missing builtin invoked by that setup rather than acting on later timeout fallout.

## Exact next action

- Read the first connection-lifecycle fixture setup and current manifest to identify the exact missing builtin causing its `E_VERBNF`, derive its Toast contract, and implement only that first causal dependency slice.

## First lifecycle dependency identified: `listen`

- The first audit step executes `add_property`, `create`, `add_verb`, `set_verb_code`, then `listen(handler, port, ["print-messages" -> 0])`. All preceding builtins are present; `listen` is the first absent manifest entry and therefore the exact `E_VERBNF` cause.
- The fixture's declared connection-lifecycle dependencies also show later missing handlers, but they are not the active slice.
- Pinned Toast `src/server.cc` registers `listen` with 2 required and 1 optional argument: OBJECT handler, ANY descriptor, optional MAP options.
- Toast permits only wizards, rejects an invalid handler or duplicate descriptor with `E_INVARG`, reads truthy `print-messages` from the options map, starts the listener, and returns the listener descriptor. The active fixture uses an integer port descriptor and disables print messages.
- Banteng already has the concrete `ListenerControl.listen(long handler, int port, boolean printMessages)` and `MooServer.listen`; constructors currently accept ListenerControl but do not register a listen manifest handler.

## Exact next action

- Add only the concrete `listen` manifest handler with exact shape/permission/effect/owner, translate the active integer descriptor and `print-messages` option to ListenerControl, add focused catalog/server coverage, and rerun the first lifecycle row before touching `unlisten` or later dependencies.

## `listen` slice concrete mapping

- The read-only subagent independently confirmed `listen` is the exact first absent call and verified pinned Toast HEAD `aecc51e9449c6e7c95272f0f044b5ba38948459e` plus source lines.
- All Phase 4 `listen` fixture calls use an integer allocated TCP port and an optional map containing only `print-messages`; the existing ListenerControl integer-port contract covers this exact path.
- The manifest contract is required OBJECT handler, required ANY descriptor, optional MAP; WIZARD_ONLY; fixed zero cost; IRREVOCABLE host effect; SERVER owner.
- Concrete translation: reject a nonexistent handler or noninteger descriptor with `E_INVARG`; decode truthiness of `print-messages`; call existing `ListenerControl.listen`; map duplicate/invalid runtime arguments to `E_INVARG`, bind/start IOException to `E_QUOTA`, and return the integer descriptor.
- BuiltinCatalog currently has no retained ListenerControl field or reusable listener test double in `BuiltinCatalogTest`; those are the exact code/test additions now required.

## Exact next action

- Patch BuiltinCatalog to retain its already-supplied ListenerControl and register/implement only `listen`, add one focused test double and contract test, run focused Java authority, then run the first lifecycle row with its exact fixture path.

## `listen` slice measured result

- Focused `BuiltinCatalogTest` plus `ArchitectureTest` passed in 1 minute 31 seconds.
- The managed `connection_lifecycle_toast_oracle.yaml` authority completed with 5 passed and 18 failed in 56.86 seconds.
- The first two exact rows, `audit_listener_handler_do_login_command` and `audit_listener_handler_do_command`, now pass, proving the concrete dynamic listener and handler dispatch path.
- The additional passing rows were `audit_listener_print_messages_suppresses_connect_msg`, `audit_login_timeout_message`, and `audit_oob_prefix_dispatches_do_out_of_band_command`.
- The first remaining ordered row, `audit_listener_handler_do_blank_command`, fails during its setup loop with `E_VERBNF`; it is outside the retained `listen` slice and will be the next exact target after this slice is gated and committed.
- The Phase 4 plan was reread after the passing managed run.

## Exact next action

- Run exact `JAVA_HOME=/opt/java/25 ./gradlew check`, reread Phase 4 after it passes, run `git diff --check`, audit and commit only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`, then inspect the exact blank-command setup call that produced `E_VERBNF`.

## `listen` full Java gate

- Exact `JAVA_HOME=/opt/java/25 ./gradlew check` passed in 33 seconds.
- The Phase 4 plan was reread after the passing full Java gate; the next required work remains completion of the exact Phase 4 server/connection surface.

## Exact next action

- Run `git diff --check`, audit the exact two-file source/test diff, commit it, verify tracked-clean state, then begin the first remaining blank-command row.

## `listen` committed

- `git diff --check` passed and the audited staged set contained only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`.
- Committed the kept slice as `4fe3566 feat: add dynamic listen builtin`.
- Tracked state is clean after the commit; the existing untracked notes and tooling artifacts remain uncommitted.

## Exact next action

- Read only `audit_listener_handler_do_blank_command` and its setup in the exact Phase 4 fixture, identify the first absent call producing `E_VERBNF`, and derive that operation's pinned Toast contract before editing.

## Blank-command row first dependency: `connection_info`

- The exact row reaches `connection_info(player)["destination_ip"]` after already-present property/object/verb operations; `connection_info` is absent from Banteng's manifest and is the first `E_VERBNF` cause.
- The stock Toast preflight passed and pinned source HEAD is exactly `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Toast registers exact one-OBJECT `connection_info`. A missing/disconnected target returns `E_INVARG`; reading another programmer's connection returns `E_PERM` unless the caller is Wizard; reading the caller's own live connection is allowed.
- Success returns the live connection MAP with `source_address`, `source_ip`, `source_port`, `destination_address`, `destination_ip`, `destination_port`, `protocol`, and `outbound`. Stock Toast also includes `TLS -> ["active" -> 0]` for Banteng's non-TLS sockets.
- Pinned Toast headers and implementation define `source_*` as the local listener interface and `destination_*` as the remote endpoint; Banteng's current socket map uses that same endpoint orientation.
- The exact manifest classification is ANY permission with the argument-dependent check in the implementation, fixed zero cost, EXTERNAL_READ effect, and CONNECTION owner.
- No new interface or adapter is required: `WorldTxn.connectionInfo(objectId)` already returns the attempt's live connection map for either the negative connection object or its attached player, and `WorldTxn.isWizard(programmer)` supplies the conditional permission check.
- Current tracked state is clean at commit `4fe3566`; no new source slice has started. There is no blocker.

## Exact next action

- Add only the `connection_info` manifest entry and direct WorldTxn-backed implementation, add the non-TLS `TLS.active` field to the existing MooServer connection map, add focused contract/metadata tests, then run the focused Java authority and exact blank-command managed row.

## `connection_info` focused authority

- Added one-OBJECT `connection_info` with ANY manifest permission, the exact argument-dependent own-or-Wizard check, fixed zero cost, EXTERNAL_READ effect, CONNECTION owner, and direct `WorldTxn.connectionInfo` lookup.
- MooServer's existing eight endpoint fields already match Toast's local-source/remote-destination orientation; the production map now also carries stock Toast's non-TLS `TLS -> ["active" -> 0]` value.
- Catalog tests cover shape, classification, own access, Wizard cross-player access, nonwizard `E_PERM`, missing target `E_INVARG`, and type/arity errors. The real-socket test evaluates all endpoint/protocol/outbound/TLS fields through production MOO code.
- The first focused compile attempt failed because `WorldTxn.isWizard` does not exist. Replaced that call with the plan's existing shared `BuiltinPermissionRule.WIZARD_ONLY` predicate; no API or helper was added.
- Focused `BuiltinCatalogTest`, `MooServerTest`, and `ArchitectureTest` then passed in 1 minute 30 seconds.
- The Phase 4 plan was reread after the passing substantial targeted run.

## Exact next action

- Run the exact managed `connection_lifecycle_toast_oracle.yaml` fixture and confirm `audit_listener_handler_do_blank_command` moves from `E_VERBNF` to green before deciding whether to keep this source slice.

## `connection_info` managed result

- Exact managed `connection_lifecycle_toast_oracle.yaml` completed with 8 passed and 15 failed in 57.49 seconds, improving from the committed `listen` baseline of 5 passed and 18 failed.
- `audit_listener_handler_do_blank_command` now passes, proving its first missing dependency is fixed.
- `audit_connection_info_source_fields` also passes directly, and `audit_user_reconnected_cross_listener_hooks` moved to green because its setup can now establish trusted connection metadata.
- The first remaining ordered failure is `audit_proxy_command_clears_login_input`: setup succeeds, but the trusted PROXY line does not invoke `do_login_command`, leaving `audit_proxy_seen` as its initial empty list.
- The Phase 4 plan was reread after the substantial managed improvement.

## Exact next action

- Run exact `JAVA_HOME=/opt/java/25 ./gradlew check`; after it passes reread Phase 4, audit and commit the four-file `connection_info` slice, then inspect only the PROXY-command row and production input parser.

## `connection_info` full Java gate

- Exact `JAVA_HOME=/opt/java/25 ./gradlew check` passed in 31 seconds.
- The Phase 4 plan was reread after the passing full Java gate; its unfinished connection lifecycle remains the next required work.

## Exact next action

- Run `git diff --check`, audit and commit exactly the four `connection_info` source/test files, verify tracked-clean state, then begin the ordered PROXY-command failure.

## `connection_info` committed

- `git diff --check` passed; the audited and staged set contained only the four intended catalog/server source/test files.
- Committed the kept slice as `c0c55a7 feat: expose live connection metadata`.
- Tracked state is clean after the commit; existing untracked notes/tooling remain uncommitted.

## Exact next action

- Read only `audit_proxy_command_clears_login_input`, inspect Banteng's existing socket/login parsing path, and derive pinned Toast's exact trusted PROXY parsing/dispatch semantics before editing.

## Corrected causal order for the apparent PROXY failure

- The earlier premise that socket PROXY parsing was the next defect was wrong.
- Banteng already contains the active row's trusted six-token PROXY clear-to-empty login path in `MooRuntime.executeLoginAuthentication`.
- A direct production-runtime regression mirroring the fixture failed earlier, during setup, with `E_VERBNF` at `set_verb_info()`; `BuiltinCatalog` has neither `set_verb_info` nor `set_verb_args`.
- The managed fixture's setup step has no explicit expectation, so the conformance runner did not surface that setup error. It continued to the final property assertion, where the unchanged initial value misleadingly looked like ignored PROXY input.
- The direct test attempt was rejected and fully restored. Tracked state is clean again at `c0c55a7`; no PROXY source or test change remains.
- Pinned Toast PROXY authority was still captured read-only in untracked `notes-phase4-proxy-toast-authority.md`; no Barn read or source edit occurred.
- The exact first missing unhandled setup call is `set_verb_info`; it precedes `set_verb_args` and the socket input.

## Exact next action

- Derive pinned Toast's exact `set_verb_info` contract, map it to the existing `WorldTxn` verb mutation path, implement and test only `set_verb_info`, then rerun the exact lifecycle fixture before touching `set_verb_args` or PROXY handling.

## `set_verb_info` pinned contract and active slice state

- Stock Toast preflight passed again at exact HEAD `aecc51e9449c6e7c95272f0f044b5ba38948459e`; Barn was not inspected.
- Toast registers exactly three args with outer types ANY, ANY, LIST. Validation order is descriptor, permanent-object receiver, valid receiver, exact `{owner OBJ, flags STR, names STR}` structure, valid owner, flag/name contents, local descriptor lookup, then write/owner-change permission.
- Contrary to the initial local inference, Toast's `is_obj()` here accepts only permanent `TYPE_OBJ`; anonymous objects and WAIFs return `E_TYPE`. The just-added anonymous `WorldTxn.setVerbInfo` overload is unauthorized and must be removed before testing.
- Flags accept only case-insensitive `r`, `w`, `x`, `d`; leading ASCII spaces are stripped from names; empty names return `E_INVARG`. Success changes only owner, low permission bits, and names, preserves args/code, and returns integer zero.
- Exact manifest classification is ANY, fixed zero, TRANSACTION_WRITE, WORLD.
- Current uncommitted tracked slice touches `BuiltinCatalog.java`, `WorldTxn.java`, and `BuiltinCatalogTest.java`. The catalog entry/implementation and focused permanent-object test are present; the anonymous receiver path/overload still need removal. No test has run yet. There is no blocker.

## Exact next action

- Remove the unauthorized anonymous `WorldTxn` overload and anonymous dispatch branch, verify Toast validation/error precedence in the focused test, run `git diff --check`, then run focused catalog/world/architecture tests.

## `set_verb_info` focused authority

- Removed the unauthorized anonymous overload and branch completely; `WorldTxn.java` is no longer part of the diff.
- The final implementation accepts only permanent objects and follows Toast's exact descriptor, object, info, lookup, and permission order.
- Focused coverage proves the three-argument ANY/ANY/LIST shape, manifest classification, flag/name mutation, nonwizard owner-transfer `E_PERM`, Wizard owner transfer used by the fixture, invalid owner/flags, missing verb, descriptor precedence, and malformed info.
- The first focused run failed only because the Wizard-transfer assertion was accidentally inserted into the existing `listen` test by an ambiguous patch context. That test edit was moved into the intended `setVerbInfo` test; no production correction was needed.
- `BuiltinCatalogTest` plus `ArchitectureTest` then passed in 27 seconds.
- The Phase 4 plan was reread after the passing substantial targeted run.

## Exact next action

- Run the exact managed `connection_lifecycle_toast_oracle.yaml` fixture and determine whether the apparent PROXY row now advances to the next missing setup call, `set_verb_args`.

## `set_verb_info` managed experiment

- Exact managed lifecycle rerun remained 8 passed and 15 failed in 57.61 seconds.
- This is the expected same-row dependency chain: the unchecked setup can now complete `set_verb_info` but immediately reaches absent `set_verb_args`, so the row's final observation remains its initial empty value.
- Because there is no measured row reduction yet, `set_verb_info` is not being committed as a partial no-improvement slice. The same uncommitted `audit_proxy_command_clears_login_input` code-change attempt remains active.
- Tracked diff still contains only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`; there is no blocker.

## Exact next action

- Derive pinned Toast's exact `set_verb_args` contract, implement it through existing `WorldTxn.setVerbArgs`, extend the same focused catalog test surface, and rerun the exact lifecycle fixture so the active row can reach its PROXY input and produce a measurable result.

## Valid lifecycle fixture dependency closure

- Pinned Toast authority confirmed `set_verb_args` as exact ANY/ANY/LIST with permanent-object-only receiver, descriptor-first validation, complete direct/preposition/indirect parsing, local-verb lookup, verb write permission, and mutation through the existing argument fields.
- Added `set_verb_args` beside the active `set_verb_info` dependency without adding an interface, adapter, or new world mutation path. Focused `BuiltinCatalogTest` plus `ArchitectureTest` passed under WSL Java 25 in 1m28s.
- The exact managed `connection_lifecycle_toast_oracle.yaml` rerun now executes and passes `audit_proxy_command_clears_login_input`, proving that its setup is no longer silently aborted by missing fixture dependencies.
- The valid setup exposes the suite's real remaining server behavior: 4 passed and 19 failed in 191.83s. The apparent prior 8-pass count included rows whose unchecked setup had not installed the behavior they intended to observe.
- The active exact-convergence row improved and the two-file source slice is eligible to keep. No fixture file was changed; the fixture itself was repaired by implementing its exact production dependencies.

## Exact next action

- Run the exact Phase 4 Java gate, reread the plan, audit and commit the two-file kept slice, then continue with the first remaining lifecycle row `audit_user_connected_hook_on_first_login`.

## Verb-metadata fixture validity continuation

- The valid PROXY row and exact production dependencies were committed as `18e4cca feat: add verb metadata mutation builtins`; tracked state was clean afterward and notes remained untracked.
- `audit_user_connected_hook_on_first_login` passes alone in 7.07s, so its login-hook runtime is not the current defect. The earlier premise that the hook itself was failing was wrong.
- The exact ordered pair `audit_proxy_command_clears_login_input` then `audit_user_connected_hook_on_first_login` reproduces the failure: PROXY passes, then the hook row times out. The first five ordered rows reproduce the same boundary.
- Current Banteng lacks `verb_info`, `verb_args`, and `delete_verb`. The fixture catches their errors, which makes setup/cleanup appear to continue without actually saving and removing the installed handler. This is the concrete invalid-fixture dependency family and explains why isolated and ordered results differ.
- Pinned Toast source confirms `verb_info`, `verb_args`, and `delete_verb` are exact two-argument builtins. `WorldTxn` already owns local lookup and `deleteVerb`; no new interface/helper/world mutation path is needed.
- Two read-only agents are deriving the remaining exact Toast contracts and Banteng owner/test surfaces. No tracked edit has started for this slice, so Git remains trustworthy.

## Exact next action

- Finish the pinned authority audit, implement `verb_info`, `verb_args`, and `delete_verb` directly in `BuiltinCatalog` through existing `WorldTxn` operations, add focused catalog coverage, then rerun the exact PROXY-plus-user-connected sequence to prove teardown validity before keeping or reverting the slice.

## Kept verb-metadata fixture validity result

- Implemented exact permanent-object Phase 4 paths for `verb_info`, `verb_args`, and `delete_verb` directly in `BuiltinCatalog`; existing `WorldTxn` lookup and `deleteVerb` remained the only state owners.
- Focused `BuiltinCatalogTest` plus `ArchitectureTest` passed in 1m29s. The exact ordered PROXY-plus-user-connected reproduction then passed both rows in 8.33s.
- The full managed connection lifecycle suite now reaches 18 passed and 5 failed in 46.15s, versus 4 passed and 19 failed before the metadata/teardown dependency slice. The remaining failures are the first real behavior rows: flush command, boot player messages, redirect messages, connection_name method 0, and connection hold/OOB options.
- Exact `JAVA_HOME=/opt/java/25 ./gradlew check` passed in 31s. Phase 4 was reread after the passing full Java gate.
- Tracked slice remains exactly `BuiltinCatalog.java` and `BuiltinCatalogTest.java`; notes remain untracked. There is no blocker.

## Exact next action

- Run `git diff --check`, audit and commit the two-file kept slice, then select only the first remaining lifecycle target `audit_flush_command_flushes_pending_input`.

## First real lifecycle behavior target

- The verb-metadata fixture-validity slice was committed as `03c2e87 feat: add verb metadata query builtins`; tracked state was clean after commit.
- Active exact target is now only `audit_flush_command_flushes_pending_input`.
- The row creates/logs in a player, sets `hold-input=1` and `flush-command=".flush"`, sends two held lines, then sends case-insensitive `.FlUsH`. Toast expects four exact diagnostic lines listing and removing the two pending inputs; after releasing hold, only a later empty `auditflush` invocation may execute.
- Banteng returned `I couldn't understand that.` for `.FlUsH`, proving the configured flush command was not active on the connection. Existing `MooRuntime.executeLineNow` already contains the exact case-insensitive flush recognition and exact output formatting, plus pending-input clearing.
- `MooRuntime.applyConnectionOptionRequests` also already contains hold/release and flush-command state mutation, but repository search found no current `set_connection_option` manifest handler or producer of `ConnectionOptionRequest`; only consumers and snapshot plumbing exist. The fixture setup statement did not assert each call separately, so the apparent continued setup did not prove those builtins executed.
- Two read-only agents are auditing pinned Toast semantics and the current Banteng path. No tracked edit has started for the flush slice; Git remains trustworthy.

## Exact next action

- Complete the authority reports, confirm the first causal missing handler, implement only the exact `set_connection_option` production path through the existing request/effect plumbing with focused tests, then rerun the one flush row before deciding whether to keep the slice.

## Flush-command kept-slice result

- Pinned Toast authority confirmed the flush trigger is a case-insensitive whole-line match before enqueue/hold parsing; it drains pending input FIFO and emits the four exact direct-notification lines.
- Implemented only the missing `set_connection_option` catalog producer on the existing `Result -> VmState -> MooRuntime` request path. No runtime, server, interface, adapter, or world change was added.
- Focused catalog plus architecture tests passed in 1m43s. The exact flush row then passed in 12.50s.
- The whole connection lifecycle suite improved from 18 passed / 5 failed to 19 passed / 4 failed in 49.89s. Remaining exact rows are boot-player messages, redirect messages, connection-name method 0, and connection hold/OOB options.
- Exact `JAVA_HOME=/opt/java/25 ./gradlew check` passed in 36s, and Phase 4 was reread afterward. The measured slice is eligible to keep; no blocker exists.

## Exact next action

- Run `git diff --check`, audit and commit the two-file `set_connection_option` slice, then select only `audit_boot_player_messages` as the first remaining lifecycle target.

## Boot-player kept-slice result

- The connection-option slice was committed as `b40e05c feat: configure live connection options`; tracked state was clean afterward.
- Pinned Toast authority confirmed `boot_player` is one OBJECT argument, self-or-Wizard authorized, returns zero, and treats invalid/unconnected targets as successful no-ops. The default printed boot line is exactly `*** Disconnected ***` before physical close.
- Added only the missing catalog producer for the existing `bootPlayerTarget -> VmState -> MooRuntime -> ListenerControl` path, plus focused contract/permission/staging coverage. No runtime/server/interface/adapter change was made.
- Focused catalog plus architecture tests passed in 1m42s. The exact managed boot row passed in 7.53s and therefore supplies the production-socket message proof; a redundant new Java socket test would not change the keep decision and was not added.
- Whole connection lifecycle improved to 20 passed / 3 failed in 50.40s. Exact Java `check` passed in 35s and Phase 4 was reread afterward. Remaining rows are redirect messages, connection-name method 0, and connection hold/OOB options.

## Exact next action

- Run `git diff --check`, audit and commit the two-file boot-player slice, then select only `audit_redirect_messages`.

## Redirect row fixture-query target

- The boot-player slice was committed as `82fa2d8 feat: boot active player connections`; tracked state was clean afterward.
- Active target is only `audit_redirect_messages`. Read-only local audit proved the existing `MooRuntime`/`MooServer` redirect path already publishes the default old/new messages, replacement connection, old socket boot, and optional reconnect hook in the required order.
- The row's first actual blocker is its final `connected_players(1)` observation. That builtin was absent, so the final aggregate expression raised E_VERBNF after both socket observations had already occurred.
- Added only the `connected_players` manifest producer through existing `WorldTxn.connectedPlayers(boolean)`, with focused coverage for default authenticated-only filtering, truthy show-all including negative connections, newest-first order, and E_ARGS.
- Focused `BuiltinCatalogTest` plus `ArchitectureTest` passed in 1m40s. No runtime/server/interface/helper/adapter change exists in this slice.

## Exact next action

- Reread Phase 4, run the exact managed redirect row, then run the entire lifecycle suite if the row passes; keep or revert the two-file slice based on that measured result.

## Redirect-query kept-slice result

- The exact managed redirect row passed in 8.17s with no runtime/server changes, proving the existing two-socket redirect behavior was already correct once `connected_players(1)` could execute.
- The complete connection lifecycle suite improved to 21 passed / 2 failed in 50.10s. Remaining rows are only `audit_connection_name_method0_hostname` and `audit_connection_hold_and_oob_options`.
- Exact `JAVA_HOME=/opt/java/25 ./gradlew check` passed in 35s, and Phase 4 was reread afterward. The two-file catalog/query slice is eligible to keep; there is no blocker.

## Exact next action

- Run `git diff --check`, audit and commit the two-file `connected_players` slice, then select only `audit_connection_name_method0_hostname`.

## Connection-name target

- The connected-players slice was committed as `bd33165 feat: expose connected players`; tracked state was clean afterward.
- Active target is only `audit_connection_name_method0_hostname`; its first operation raised E_VERBNF because `connection_name` was absent. There is no setup dependency.
- Pinned Toast authority and local ownership agree that method 0 returns the saved legacy string `port <source_port> from|to <destination_address> [<destination_ip>], port <destination_port>`; omitted method returns destination address and method 1 returns destination IP.
- The semantic trap is endpoint naming: current `MooServer` intentionally stores local listener data in `source_*` and remote peer data in `destination_*`. This slice must not swap them or add DNS lookup.
- Added only the `connection_name` catalog producer using existing `WorldTxn.connectionInfo`, plus focused synthetic metadata coverage for all three method forms, exact formatting, live target, permission, and type errors. Tracked diff is exactly `BuiltinCatalog.java` and `BuiltinCatalogTest.java`; no runtime/server/world/helper/interface change exists.

## Exact next action

- Run `git diff --check` and focused catalog/architecture tests, reread Phase 4 after a pass, then run the exact managed connection-name row.

## Connection-name fixture correction

- The earlier row premise was invalid: pinned Toast returns the full legacy connection string for explicit method `0`; only the omitted method returns the saved hostname/address.
- Corrected the sibling conformance row to `audit_connection_name_method0_legacy_format` and replaced the weak nonempty-string assertion with checks for the legacy prefix, inbound direction, saved name, numeric IP, and remote-port delimiter.
- The corrected row passed against pinned Toast: 1 selected, 1 passed, 22 deselected. Phase 4 was reread after the pass.

## Exact next action

- Commit only the corrected conformance YAML in the sibling fixture repository, then validate the uncommitted Banteng `connection_name` slice with focused Java tests and the corrected managed row.

## Connection-name kept-slice result

- The corrected conformance fixture was committed in the sibling repository as `a968886 fix: assert Toast connection name method zero`; its tracked state was clean afterward.
- Focused `BuiltinCatalogTest` plus `ArchitectureTest` passed. The corrected managed Banteng row passed: 1 selected, 1 passed, 22 deselected.
- The complete connection lifecycle suite improved to 22 passed / 1 failed. Only `audit_connection_hold_and_oob_options` remains.
- Exact Java `check` passed in 36s, and Phase 4 was reread afterward. The two-file `connection_name` catalog slice is eligible to keep.

## Exact next action

- Run `git diff --check`, audit and commit only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`, verify tracked-clean state, then select only `audit_connection_hold_and_oob_options`.

## Hold-input and OOB target

- The `connection_name` slice was committed as `f8cc000 feat: expose connection names`; Banteng tracked state was clean afterward.
- Active target is only `audit_connection_hold_and_oob_options`. Its first observed E_VERBNF is in the negative-connection login step; local manifest inspection shows `force_input` is absent.
- Existing production ownership is already present: `ForcedInputRequest` is part of `BuiltinCatalog.Result`, `MooVm` stages it in `VmState`, `MooRuntime.publishVmState` applies it in publication order, and `RuntimeEffect.INPUT` enqueues the detached line task. Existing connection state already implements `hold-input`, `disable-oob`, pending-input release, and OOB dispatch.
- Checked-in generated Toast authority declares `force_input` with 2 required arguments `(OBJ, STR)`, one optional `ANY`, and integer success. The optional value is the at-front selector; exact permission/error precedence is still being confirmed from pinned Toast by the read-only authority audit.
- Two read-only subagents are active: one on exact pinned Toast semantics and row validity, one on Banteng ownership and the smallest literal source slice. No source edits have started for this target.

## Exact next action

- Wait for both exact audits, reconcile them with current code, then add only the missing `force_input` catalog producer and focused contract test if the authorities agree that no deeper runtime change is required.

## Hold-input and OOB kept-slice result

- Pinned Toast at `aecc51e9449c6e7c95272f0f044b5ba38948459e` confirmed the row is valid and returns exactly `{1, 1, 1, 1, 0}`. It also confirmed `force_input(OBJ, STR [, ANY])`, self-or-Wizard permission, no target-validity requirement, rear insertion when the optional argument is omitted, and integer-zero success.
- Added only the missing `force_input` catalog producer through the existing `ForcedInputRequest` result field, plus focused catalog coverage. No runtime, server, VM, world, interface, helper, or adapter changed.
- Focused `BuiltinCatalogTest` plus `ArchitectureTest` passed in 1m42s. The exact managed row passed: 1 selected, 1 passed, 22 deselected.
- The complete connection lifecycle suite now passes 23/23 in 49.93s. Exact Java `check` passed in 36s, and Phase 4 was reread after both passing gates.

## Exact next action

- Run `git diff --check`, audit and commit only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`, verify tracked-clean state, then measure the remaining exact Phase 4 path list to select the next single failing family.

## Exact Phase 4 Banteng measurement after lifecycle completion

- The `force_input` slice was committed as `bdec8aa feat: inject connection input`; tracked state was clean afterward.
- The exact seven-path Banteng command collected 71 cases and finished in 254.11s: 32 passed, 38 failed, 1 declared skip.
- All 4 queued-task capability rows and all 23 connection-lifecycle rows passed in that exact run.
- The first direct remaining failure is `task_scheduling_toast_oracle::audit_default_fg_ticks`, which raises E_VERBNF at `ticks_left()`. The next row also depends on task-limit observation; later transport timeouts are downstream/cascade evidence and are not separate target selections.

## Exact next action

- Select only `audit_default_fg_ticks`; audit pinned Toast `ticks_left()` semantics and current Banteng VM budget ownership in parallel before editing. Do not switch to later scheduling, parsing, lifecycle, or fork rows until this first target is finished.

## Default foreground ticks target

- Pinned Toast at `aecc51e9449c6e7c95272f0f044b5ba38948459e` confirms the row is valid. A fresh foreground task defaults to 60,000 ticks after `fg_ticks` is absent, counted opcodes decrement before execution, and `ticks_left()` is zero-argument, unrestricted, integer-valued, and returns the current remainder.
- The exact row invokes two currently absent producers: `load_server_options()` in its setup and `ticks_left()` in its assertion. The managed failure is reported at `ticks_left()` because the setup step has no explicit expectation, but both operations belong to the row and must exist.
- Banteng already reads `#0.server_options.fg_ticks` live for every new foreground task, falling back to 60,000 and clamping to at least 100. Therefore `load_server_options()` has no cache to reload and must be a truthful Wizard-only zero-return acknowledgment, with no runtime cache or new owner.
- `VmState.remainingTicks` already owns, decrements, snapshots, and restores the budget. `MooVm` already passes the current remainder into `BuiltinHandler`; `ticks_left()` needs only a pure catalog producer returning that existing argument.
- Historical `08e3d18` confirms the earlier `ticks_left` implementation was exactly the remaining-tick passthrough. No current source edit has begun; tracked state is clean.

## Exact next action

- Add only `load_server_options` and `ticks_left` manifest producers in `BuiltinCatalog`, plus focused catalog contract tests and reachability names. Do not modify runtime, VM, world, interfaces, helpers, or adapters.

## Foreground tick kept-slice result

- Added only the `load_server_options` zero-return acknowledgment and `ticks_left` remaining-budget producer, plus focused catalog contracts. The tracked diff remains exactly `BuiltinCatalog.java` and `BuiltinCatalogTest.java`.
- The first cold focused run hit its 150s timeout during compilation without a test result; the unchanged rerun passed in 54s. The exact managed `audit_default_fg_ticks` row passed in 5.75s.
- The complete scheduling family improved from 4 passing rows to 8: default foreground/background ticks and foreground/background runtime tick overrides now pass. Current family result is 8 passed / 15 failed in 91.53s; the first remaining blocker is `audit_server_options_fg_seconds_runtime` at absent `seconds_left()`.
- The first exact Java `check` was interrupted by a JetCheck generator failure in unrelated `WorldTxnPropertyTest`; that exact failed test passed unchanged in 6s, and the full exact Java `check` then passed in 35s. Phase 4 was reread afterward.

## Exact next action

- Run `git diff --check`, audit and commit only the two catalog files, verify tracked-clean state, then select only `audit_server_options_fg_seconds_runtime`.

## Foreground seconds target

- The tick-budget slice was committed as `c7f8064 feat: expose task tick budgets`; tracked state was clean afterward.
- Pinned Toast confirms `audit_server_options_fg_seconds_runtime` is valid: a fresh foreground task samples integer `fg_seconds=12`, and zero-argument unrestricted `seconds_left()` returns the active timer's whole remaining seconds, normally 12 or 11 and safely above 8 for the expression.
- Banteng already implements the plan-required process-CPU semantics: runtime samples `fg_seconds` for each new task; `VmState` owns nanosecond CPU remainder and computes whole remaining seconds from process CPU; snapshots preserve it; `MooVm` passes it into every builtin handler.
- Added only the missing pure VM manifest producer returning the existing `remainingSeconds` argument, plus reachability and exact passthrough/E_ARGS contract coverage. Current tracked diff is exactly `BuiltinCatalog.java` and `BuiltinCatalogTest.java`; no runtime, VM, timer, loader, interface, helper, or adapter changed.

## Exact next action

- Run the focused catalog/architecture gate, reread Phase 4 after a pass, then run only the exact managed `audit_server_options_fg_seconds_runtime` row.

## Foreground seconds kept-slice result

- Focused `BuiltinCatalogTest` plus `ArchitectureTest` passed in 1m46s. The exact managed foreground-seconds row passed in 8.08s.
- The complete scheduling family improved from 8 passed / 15 failed to 10 passed / 13 failed in 90.55s. Both foreground and background seconds rows now pass through the one shared `seconds_left` producer.
- Exact Java `check` passed in 34s, and Phase 4 was reread afterward. The two-file catalog slice is eligible to keep.

## Exact next action

- Run `git diff --check`, audit and commit only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`, verify tracked-clean state, then select only `audit_background_task_id_stable_across_suspend_and_fork`.

## Background task-ID stability target

- The seconds slice was committed as `7f1a21c feat: expose task CPU budgets`; tracked state was clean afterward.
- Pinned Toast confirms the row is valid: the named fork binding is the actual child task ID; `task_id()` is zero-argument/unrestricted; suspension preserves the same ID; and FIFO ready ordering lets the nested child run before the parent's second resume.
- Banteng's scheduler already owns the exact semantics: immutable `Entry.taskId` is assigned at ingress/fork, retained through `SuspendedWork` wake/resume, and the named fork expression receives the assigned child ID. The first blocker is absent `task_id()` in the child.
- The missing transport is explicit: `PublicationScheduler` has `start.taskId()`, but `MooVm`, `BuiltinCatalog.invoke`, and `BuiltinHandler` do not carry it. Do not duplicate it into `VmState`/`VmSnapshot`, use thread-local state, alter `TaskRegistry`, or change scheduling.
- Smallest production boundary is mechanical task-ID transport through existing handler/catalog/VM calls plus a pure zero-argument `task_id` producer. Focused tests must prove catalog passthrough and scheduler stability across both suspends and nested fork ordering.

## Exact next action

- Have the local audit subagent implement only the four production transport files without committing; inspect that diff, then add the focused catalog/VM/scheduler tests and run the exact row.

## Task-ID transport compile boundary correction

- The delegated four-file transport edit is complete but correctly stopped before compile: changing the existing `BuiltinHandler` SAM from nine to ten arguments makes the exact `TaskRegistry::queuedTasks` and `TaskRegistry::killTask` method references in `MooRuntime` incompatible, and direct test lambdas/calls must also carry the parameter.
- This is not authority for an adapter, overload, thread-local, duplicated state, or alternate transport. It is the mechanical completion of the same explicit handler contract through its existing producers and consumers.
- The corrected minimal boundary therefore includes the four edited production files plus exact signature/call-site updates in `MooRuntime`, `TaskRegistry`, and directly affected tests. No behavior change is authorized in registry lookup, task allocation, state/snapshot ownership, or scheduling order.

## Exact next action

- Audit the current four-file diff, then update only the exact compile consumers and focused task-ID contract. Run compilation/focused tests before the managed row.
## 2026-07-20 task_id slice restart

- Active exact target: `audit_background_task_id_stable_across_suspend_and_fork` in `audit/task_scheduling_toast_oracle.yaml`.
- Toast authority and local scheduler tracing established that `PublicationScheduler.Entry.taskId` is the authoritative ID and must be transported through `PublicationScheduler -> MooVm -> BuiltinCatalog -> BuiltinHandler`.
- The in-progress tracked diff adds zero-argument pure VM builtin `task_id`, carries `Entry.taskId` through execution and pending-builtin authorization, and adds the required handler parameter.
- Removed newly introduced compatibility overloads that supplied `0`; production invocation now requires an explicit task ID.
- Mechanically updated `TaskRegistry` handler signatures and direct catalog/registry/VM test callers. No helper, adapter, thread-local, duplicate task-ID state, allocator change, or ordering change was added.
- `git diff --check` passed before the consumer updates. The current expanded diff has not yet been compiled.
- Blocker: none currently. Next action is bounded signature search plus the focused Gradle tests; any compiler-reported consumer will receive only the same explicit-ID mechanical update.
- Keep decision remains the exact managed conformance row. If it passes, run the full scheduling file, exact Java gate, update this untracked note, and commit the kept slice before beginning another source slice.

### task_id slice result

- Focused Gradle contracts passed: `BuiltinCatalogTest`, `TaskRegistryTest`, and `ArchitectureTest`.
- Exact managed row passed: `audit_background_task_id_stable_across_suspend_and_fork` (1 passed, 22 deselected).
- Full scheduling family improved from 10 pass / 13 fail to 11 pass / 12 fail.
- Exact `JAVA_HOME=/opt/java/25 ./gradlew check` passed in 34 seconds.
- `git diff --check` passed; final tracked diff is exactly eight task-ID boundary/contract files.
- Next unchecked scheduling target after this commit: `audit_input_admitted_between_ready_background_tasks`.

### input-fairness fixture correction

- The original row was not portable: six finite million-iteration tasks plus a 25 ms harness wait could false-fail any conforming implementation that completed the batch before the fresh input existed.
- Pinned Toast passed the old row, and Toast source confirms it polls network at each task boundary and prioritizes input over background work. That proves the intended behavior but does not make the finite wall-clock assertion implementation-independent.
- Stopped the planned `PublicationScheduler` source edit before any tracked Banteng change. Both repositories were tracked-clean at that stop.
- Repaired only the conformance YAML: continuously ready zero-delay tasks now yield on `suspend(0)` until a fresh input sets a release property. The row proves non-starvation without measuring CPU throughput.
- Corrected exact row passed pinned Toast and current Banteng. Therefore the earlier Banteng production-failure premise was invalid; no scheduler source slice is authorized.
- Full corrected Toast scheduling file passed 23/23. Next action is commit only the YAML correction in `moo-conformance-tests`, then rerun the full Banteng scheduling file to identify the next genuine first failure.

### corrected fixture commit and next target

- Committed the deterministic fairness repair in sibling `moo-conformance-tests` as `3db3c88 fix: make input fairness fixture deterministic`.
- Both Banteng and conformance repositories were tracked-clean after that commit.
- Full corrected Banteng scheduling file is now 12 pass / 11 fail.
- Active exact target is only `audit_read_unrelated_player_denied`: Toast expects `read(other_player, 1)` by a non-wizard to raise `E_PERM`; Banteng currently returns `E_VERBNF` because `read` is absent from the builtin manifest.
- Current local evidence: `MooRuntime` owns published connection/session state and pending held input; `VmState` and `PublicationScheduler` already carry an explicit `CompletableFuture<MooValue>` host-wake suspension path. No source edit has started.
- Two read-only subagent audits are active: exact pinned Toast `read` authority and the existing Banteng host-input ownership path.
- Next action after those exact reports is to choose the smallest direct `read` vertical slice for this row, verify branch/tracked state again, then edit only the existing owners. No second queue, adapter, helper owner, or alternate scheduler is authorized.

### read explicit-target permission slice

- Pinned Toast exact row passed and source authority established `read` arity 0..2, optional OBJECT then ANY, with unrelated nonwizard explicit-target denial before queue lookup, suspension, or host effect.
- Current tracked slice touches only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`: manifest entry `read` is ANY/fixed-0/SUSPENDING_HOST/CONNECTION, and its inline handler returns `E_PERM` for invalid or non-owned explicit targets by nonwizards. Non-denied successful behavior remains intentionally unimplemented for the next row.
- No runtime handler injection, queue, future, helper, adapter, constructor dependency, VM change, or scheduler change was added.
- Focused `BuiltinCatalogTest` passed in 1m40s.
- Exact managed Banteng row `audit_read_unrelated_player_denied` passed (1 selected, 22 deselected) in 6.04s.
- Blocker: none. Next action is exact Java `check`, plan reread, diff audit, note update, stage only these two files, and commit before beginning successful nonblocking read behavior.
- Exact Java `check` passed in 35 seconds. Final diff check passed and the tracked diff remains exactly the two authorized catalog files.

### read nonblocking empty-queue slice

- Active row `audit_read_nonblocking_from_fork_returns_zero` passed pinned Toast and established: Wizard fork preserves `player`/programmer, explicit permission succeeds, live connected empty in-band queue returns integer 0 immediately, and child property publication precedes parent zero-delay resume.
- Implemented one injected existing `BuiltinHandler` from `BuiltinCatalog` to `MooRuntime`; production constructors require it, while focused/default catalogs retain the E_INVARG fallback.
- `MooRuntime.read` resolves the existing connection ID or attached player, uses only attempt-owned `ConnectionState.pendingInput`, consumes FIFO as Latin-1 when present, and returns 0 when empty. No second queue, future, effect, VM/snapshot, world, or scheduler change.
- Focused catalog + architecture tests passed in 1m38s. Exact managed row passed in 5.48s.
- Full scheduling family improved to 14 pass / 9 fail. Next first failure is `audit_read_no_arg_from_fork_denied`.
- Exact Java `check` passed in 31 seconds. Next action is diff audit and commit exactly the three read-handler files before the no-argument permission slice.

### read no-argument last-input-task slice

- Pinned Toast passed `audit_read_no_arg_from_fork_denied` and established the exact implicit permission predicate: the programmer must be a wizard and the current scheduler task ID must equal the attached player's latest input task ID. A fork inherits player/programmer identity but has a distinct task ID, so it receives `E_PERM` before any read effect.
- The first implementation stored the association in transactional `ConnectionState`. The exact row passed, but exact Java `check` deterministically failed five `PublicationSchedulerTest` conflict authorities because concurrent lines on distinct connections conflicted on the global session revision before world validation. That implementation was not kept.
- The corrected implementation owns the player-to-latest-input-task association in `PublicationScheduler`, matching Toast's task-queue ownership. LINE admission records the already-allocated scheduler task ID before dispatch; detached forced input uses the same path; fork creation never updates the association. `MooRuntime.read` asks the scheduler whether its current task ID is still a latest input task. No task-ID allocator, registry, transactional connection state, VM snapshot, helper, adapter, or alternate execution path was added.
- `BuiltinCatalog` independently enforces the Toast wizard requirement for implicit `read()`. Focused catalog/runtime tests prove nonwizard denial and fork denial without asserting the currently incomplete blocking-read result.
- Focused WSL Java tests, including all 12 `PublicationSchedulerTest` methods, passed. The exact managed row passed in 5.94 seconds. The full scheduling family is now 15 pass / 8 fail; the first remaining failure is `audit_tick_exhaustion_not_catchable`.
- Exact `JAVA_HOME=/opt/java/25 ./gradlew check` passed in 34 seconds. `git diff --check` passed. The kept diff is exactly the five catalog/runtime/scheduler files and their focused tests.

### Exact next action

- Commit only the five authorized files, verify tracked-clean state, then select only `audit_tick_exhaustion_not_catchable` and prove its exact pinned-Toast behavior before source work.

### tick-exhaustion abort slice

- Pinned Toast passed `audit_tick_exhaustion_not_catchable` in 5.29 seconds. Its counted-opcode path decrements before dispatch and aborts at zero through `ABORT_TICKS`; `FIN_ABORT` bypasses both MOO `except` and `finally`, rolls back the task, and emits literal `Task ran out of ticks`.
- Banteng's existing counted-opcode site decremented the budget but never checked exhaustion, so the row's infinite loop ran below zero forever. Added terminal `VmState.Outcome.ABORTED`; reaching zero stages the exact line and ends the VM without calling the catchable `raiseError` path.
- `PublicationScheduler` carries the terminal snapshot through the existing runtime continuation so the managed prefix/suffix frame completes, but marks the segment aborted. Publication closes the transaction and discards its session/effect context while completing the ingress normally with the framed abort output.
- Focused tests prove zero-before-dispatch, `except (ANY)` cannot catch the abort, and a world mutation made before exhaustion is rolled back while the connection receives the framed message.
- The first Java gate exposed five stale conflict tests whose two-million-iteration foreground tasks now correctly exhausted the real default 60,000-tick budget before producing their intended conflicts. Those exact scenarios now set a test-local 20,000,000 foreground budget; all 13 `PublicationSchedulerTest` methods pass.
- The exact managed row passed in 5.39 seconds. The full scheduling family improved from 15 pass / 8 fail to 17 pass / 6 fail; `audit_long_suspended_background_task_can_yield_after_wake` also became green under the same tick-exhaustion semantics. The first remaining row is `audit_handle_task_timeout_invoked`.
- Exact `JAVA_HOME=/opt/java/25 ./gradlew check` passed in 30 seconds and `git diff --check` passed. The kept slice is exactly three production VM/scheduler files and two focused test files.

### Exact next action

- Commit only those five files, verify tracked-clean state, then select only `audit_handle_task_timeout_invoked` and prove its pinned-Toast handler contract before source work.

### handle_task_timeout trace handoff

- Current delegated action is read-only tracing of only `audit_handle_task_timeout_invoked`: inspect the exact fixture and Banteng ABORTED publication path to identify the first semantic mismatch and smallest existing owner boundary.
- The fixture installs `#0:handle_task_timeout`, forks an infinite background loop under `bg_ticks = 100`, waits one second, and requires `{"ticks", LIST, LIST, 1, 1}`: a `ticks` resource, nonempty traceback list, and a formatted first line containing `Task ran out of ticks`.
- The first Banteng mismatch is in `PublicationScheduler.executeSegment`: the ABORTED `VmSnapshot` still has frames, locals, programmer, verb location, instruction pointer, task player, and the exact abort output, but it is reduced to a returned `SegmentResult` carrying only output plus an `aborted` boolean. `publishAttempt` then closes the transaction and returns no spawned work; `finishSuccess` removes the child. No system-verb lookup or activation occurs, so the property remains `{}`.
- The correct existing-owner boundary is scheduler-orchestrated rollback followed by a distinct, fresh root/server task. Preserve the aborted snapshot as timeout metadata before it is discarded; after rollback enqueue a new scheduler entry/task ID whose fresh attempt lets `MooRuntime` use its existing world verb lookup, `verbLocals`, compilation, and `startStored` path for `#0:handle_task_timeout`.
- Do not dispatch from `VmState`/`MooVm`, do not run the handler in the aborted transaction, and do not replace the aborted task inline: foreground aborts must still resume their original runtime continuation so the exact abort line reaches the connection.
- No source edits or test runs were made. Current blocker is none; next action is the parent implementation decision for this exact boundary.
## 2026-07-21 restart authority subtask

- Active exact target: `audit_suspended_task_survives_restart` in the sibling `moo-conformance-tests` repository.
- Assigned work is read-only: establish the pinned Toast live result and source/persistence contract, including durable task state, checkpoint timing, restart restore/wake behavior, task IDs/timing/transaction behavior, and exact fixture steps.
- Current blocker: the exact fixture path, pinned Toast invocation, and relevant Toast persistence sources have not yet been inspected in this subtask.
- Next action: verify the Banteng branch/tracked state, then read only the exact fixture and pinned Toast authority needed for this one row; do not edit source or run broader suites.
## 2026-07-21 delegated restart-survival trace

- Scope: read-only trace of Phase 4 target `audit_suspended_task_survives_restart`.
- Required evidence: exact fixture row; checkpoint writer/reader; suspended scheduler state; startup restoration; whether queued/suspended tasks are serialized.
- Fixture evidence: `task_scheduling_toast_oracle.yaml:578-605` commits `#0.audit_restart_task_seen = "before"`, queues a four-second delayed fork that writes `"after"`, calls `dump_database()`, waits 1.5 seconds, restarts, waits 4.5 seconds, and requires `"after"`.
- Harness evidence: a plain managed restart preserves the working DB and promotes common checkpoint outputs into the next input, so the restart is using the checkpointed state rather than refreshing the original fixture.
- Scheduler evidence: the fork child is a full initial `VmSnapshot` plus program/task player held in private `SuspendedWork`; a virtual timer thread sleeps for only a relative delay. `TaskRegistry` keeps only task-listing metadata and cannot resume the task.
- First semantic mismatch: checkpoint publication unconditionally calls `checkpointCodec.writeAtomic(target, committedWorld, List.of())`. The v17 codec rejects nonempty task lists, writes literal `0 suspended tasks`, requires the same literal on read, and returns `Checkpoint(..., List.of())`. Therefore the checkpoint contains `"before"` but no delayed child.
- Startup evidence: `Banteng` calls `new LambdaMooV17Codec().read(databasePath).world()`, discarding even the codec's nominal `Checkpoint.tasks()` surface, and constructs `MooServer`/`MooRuntime`/`PublicationScheduler` without restored tasks. No timer or queued entry is reconstructed.
- Answer: queued and suspended tasks are not currently serialized. The smallest existing owner boundary is `PublicationScheduler`, because it alone owns the resumable `SuspendedWork`; `TaskRegistry`, `MooVm`, and the world do not. The persistence and composition paths must carry that scheduler-owned durable work rather than inventing a parallel task owner.
- What was tried: read-only inspection of the exact fixture, managed restart path, v17 writer/reader, runtime checkpoint effect, scheduler fork/timer path, application startup, VM snapshot, and task registry. No source edits or tests were run.
- What worked: the code path gives a single causal explanation for the observed `"before"`: world persistence succeeds, task persistence is explicitly empty, and startup has no restoration input.
- What did not work: there is no existing queued/suspended-task serialization or restoration path to reuse as implemented; only the in-memory VM snapshot substrate exists.
- Current blocker: none for diagnosis. Implementation requires extending the active v17 task section and threading restored scheduler work through the existing composition root.
- Next action: parent implements one restart-persistence slice at the `PublicationScheduler` ownership boundary, with a regression that checkpoints a delayed fork and proves it wakes after reconstruction.

## 2026-07-21 pinned Toast restart authority findings

- Exact live command: `PYTEST_ADDOPTS="-k audit_suspended_task_survives_restart" scripts/run_managed_wsl.sh toast stock src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml` under WSL from `/mnt/c/Users/Q/code/banteng`. Pinned Toast `aecc51e9449c6e7c95272f0f044b5ba38948459e` passed: `1 passed, 22 deselected in 12.87s`.
- The row is a delayed `fork (4)` task, not a suspended VM: it writes `#0.audit_restart_task_seen = "before"`, forks the later assignment to `"after"`, requests `dump_database()`, waits 1500 ms, restarts with 500 ms post-reconnect wait, waits 4500 ms, and expects `"after"`.
- Toast assigns the fork task ID when enqueuing, computes an absolute wall-clock `start_tv`, and retains program, activation, copied runtime environment, fork vector, start time, and ID in `forked_task` (`src/tasks.cc:66-73, 1258-1292`).
- Checkpoint serialization writes all waiting and active forked tasks. Each fork record persists rounded scheduled epoch seconds, task ID, activation, runtime environment, and forked program (`src/tasks.cc:1903-1912, 1924-1956`). Reload reconstructs those fields, preserves the task ID, and inserts the task into the time-ordered waiting queue (`src/tasks.cc:2030-2069`).
- `dump_database()` only sets `checkpoint_requested`; after the current command returns, the server loop starts the checkpoint, and the forked checkpointer writes/renames the output DB (`src/server.cc:829-843, 2616-2625`; `src/db_file.cc:1117-1202`). The fixture's 1500 ms wait allows this asynchronous checkpoint to finish before managed restart.
- On startup, `read_task_queue()` restores the absolute scheduled time. `run_ready_tasks()` moves it to the programmer queue only when `start_tv <= now`, then executes it under the same stored ID (`src/tasks.cc:1629-1646, 1772-1783`). If restart finishes before the deadline it waits out the remainder; if after it, it is immediately eligible.
- Current blocker: none for the Toast authority report. Next action: inspect the managed runner restart file-selection boundary only far enough to state which checkpoint file becomes the next input DB, then report to the parent without source edits or broader tests.

### Managed restart boundary and scope caveat

- Banteng's Toast launcher starts Toast with the managed input DB and `${database}.new` as its output DB (`scripts/run_toast_wsl.sh:14-17`). The conformance runner preserves the temp directory, stops the process, promotes the newest recognized output (including `.db.new`) over the managed input DB, and starts from that input (`moo-conformance-tests/src/moo_conformance/server.py:180-228`).
- `ManagedServer.stop()` uses process termination and waits up to five seconds (`server.py:140-154`). Toast handles SIGTERM as clean shutdown and then calls `db_shutdown()`, which performs another full `DUMP_SHUTDOWN` to the same output DB (`src/server.cc:362-365, 472-473, 798, 2351-2361`; `src/db_file.cc:1296-1300`).
- Therefore this live pass proves a queued fork survives the fixture's managed restart and reload. It does not isolate the earlier asynchronous `dump_database()` checkpoint as the sole durability source, because graceful managed stop writes a later shutdown snapshot before `.new` is promoted. Both snapshots include the same still-pending 4-second fork in this timing window.
- Authority work is complete. No production/conformance source was edited and no broader suite was run; only this required uncommitted notes record was updated.

## 2026-07-21 delayed-fork v17 textual grammar follow-up

- Pinned Toast reaches `write_forked_task` -> `write_activ_as_pi` -> `write_rt_env` -> `dbio_write_forked_program`. The matching reader reaches `read_activ_as_pi` -> `read_rt_env` -> `dbio_read_program`, reorders the environment against the reparsed program, and enqueues it with the stored ID and start time.
- One fork begins with `0 <first-line> <rounded-start-epoch> <task-id>`. V17 then writes activation values and compatibility fields, `<N> variables` plus each name/value pair, and the unparsed fork-vector source terminated by a line containing only `.`.
- `dbio_write_forked_program` does not serialize Toast bytecode. It calls `unparse_program(..., f_index)` and emits the selected fork body as ordinary MOO source lines; `dbio_read_program` reparses those lines until the leading-dot terminator.
- Banteng's current `LambdaMooV17Codec.read` requires the literal queue tail `0 queued tasks`, `0 suspended tasks`, `0 interrupted tasks`; it cannot currently reach or retain a nonzero fork record. Its `MooParser`/`MooCompiler` can compile ordinary MOO source, so the fork-body program representation is reconstructible once extracted, but the current v17 codec does not parse the surrounding fork header, activation, environment, or queue record.
- Next action: finish the exact line grammar with the v17 activation and `Var` tag encodings, verify whether the fork vector is emitted with a wrapping `fork` or only its body, then report without edits.

## 2026-07-21 recursive suspended VM-state byte sizing

- Active delegated scope is only `VmSnapshot.byteSize()` plus proof in `VmSnapshotTest`; the parent will consume the public positive-`long` API from `TaskRegistry`.
- Phase 4 steps 5 and 7 require the host-wait row's suspended VM byte size and explicitly reject instruction-pointer size, Java identity size, serialized-text length, and zero as substitutes.
- Both owned files already have uncommitted Phase 4 thread-mode/host-work changes; preserve them while adding this slice.
- Current finding: no existing VM-state byte sizing API exists. `VmSnapshot` owns the complete durable payload graph, and MOO strings expose exact binary byte length while lists/maps expose recursive contents.
- Current blocker: none.
- Next action: define deterministic recursive payload accounting in `VmSnapshot.byteSize()`, add exact nested-value/frame growth assertions to `VmSnapshotTest`, then run only `VmSnapshotTest` under WSL Java 25.

## 2026-07-21 real Mongoose startup blocker and restoration review

- Current active plan target remains Phase 4 v17 Mongoose restoration. The full Java test suite passed 337 tests before the production gate, but the exact managed command `scripts/run_managed_wsl.sh banteng mongoose src/moo_conformance/_tests/generated_builtins/acos.yaml` collected four rows and all errored because Banteng did not open its port within 30 seconds.
- A direct disposable startup from a copy of the checksum-pinned ignored `fixtures/mongoose.db` reached `MooRuntime.startServerNow` and failed while compiling `#0:server_started`: `MooParser$ParseException: line 3, column 13: only named functions can be called`. The listener is constructed only after runtime startup, so this compile failure causally explains the port timeout.
- What was tried: bounded text search found `#0:server_started` only at fixture line 3849150, but inspection proved it was changelog-property data, not the numeric v17 program header. The production codec was then used through generated application classes. Two inline JShell attempts failed due classpath/PowerShell-to-WSL quoting; a temporary `.jsh` script and then `scripts/InspectServerStarted.java` both loaded for about 18 seconds and exited without captured stdout. The Java launcher now writes explicit name/length/source markers to stderr but has not yet been rerun because the mandatory notes checkpoint fired.
- What worked: the production direct-start stack established that the exact fixture loads, restored startup begins, and the first causally blocking defect is parsing the real `server_started` source. The application distribution jar contains `moo/persistence/LambdaMooV17Codec.class`, and Java wildcard classpath loading no longer reports class-not-found errors.
- What did not work: raw grep cannot safely identify the v17 program because program headers use numeric verb indices and the name occurs in arbitrary persisted values. Stdout from the temporary source launcher was empty despite marker output, so it did not yet expose the source.
- Independent read-only restoration review found unresolved Phase 4 defects that must be addressed after the active startup parser target: restored snapshots use zero ticks/CPU and can run with invalid budgets; due work is removed from `timedWork` before the only durable enumeration; `kill_task` leaves `timedWork`; native host suspensions are absent from durable enumeration; interrupted envelopes are written as ordinary suspended tasks; valid exhausted map iterators use `TYPE_CLEAR` tag 5 but the codec/importer expect tag 6; no production-start test resumes all 16 exact Mongoose tasks; activation metadata restrictions/forced fields lose v17 state; active connection sorting destroys file order.
- Current blocker: the exact third source line of `#0:server_started` has not yet been surfaced because the temporary Java launcher output was not captured. There is no external blocker.
- Exact next action: rerun `scripts/InspectServerStarted.java` with stderr markers against `build/install/banteng/lib/*`; once the line is visible, remove the temporary launcher, compare that one syntax form against `MooParser.parseCall` and the pinned Toast parser/compiler, add the smallest regression, implement the real parser/compiler support, and rerun direct startup plus the exact managed Mongoose gate. Keep this notes file untracked and never stage it.

## 2026-07-21 Mongoose startup continuation after parser fix

- The exact restored `#0:server_started` source was surfaced through a new assertion in the existing exact-fixture `QueuedTaskV17CodecTest`. Its third line is `prod = $prod();`, and Banteng rejected it because `$prod` was first lowered as a system property and generic postfix call parsing only accepted `Ast.Identifier`.
- Pinned Toast `src/parser.y` has an exact separate production for `'$' tID '(' arglist ')'` and lowers `$bar(args)` to `#0:("bar")(args)`, an ordinary verb call. This is not a dynamic builtin/function-value call.
- A focused `MooParserTest` regression proved the same initial failure. `MooParser.parseSystemProperty` now preserves ordinary `$name` property behavior but, when `(` immediately follows, parses the existing argument/splice grammar and returns the existing `Ast.VerbCall(new ObjectLiteral(0), new StringLiteral(name), arguments)` with a source span. No AST, compiler, bytecode, VM, helper, or runtime path was added.
- What worked: the focused parser regression passed, then `QueuedTaskV17CodecTest.readsEveryTaskRecordFromPinnedMongooseFixture` passed under WSL Java 25 in 56 seconds, proving the exact restored `server_started` program compiles. The controlling plan was reread after both substantial targeted passes.
- The exact managed `banteng mongoose .../acos.yaml` gate still timed out before port acceptance. An earlier commentary incorrectly interpreted pytest printing the first selected row as proof the listener opened; that was corrected immediately. Session setup had not completed, and all four rows errored from the same 30-second port timeout. The harness deleted its temp log during teardown.
- A direct disposable startup now uses `/tmp/banteng-mongoose-debug.db`, copied from the checksum-pinned ignored fixture, port 55431, and exact `profiles/banteng/mongoose.json`. The first attempt used a nonexistent inferred manifest filename and was rejected before Java startup. With the exact manifest, Java PID 247475 remained alive past 30 seconds with no stderr, while `ss` showed no listener on 55431. `MooServer` binds only after runtime startup, so a restored startup task is now running or blocked before socket construction; this is not a post-bind reachability issue.
- Reviewer correction: imported tasks do not start with zero/negative budgets. They currently receive hard-coded default background limits but bypass current `$server_options` overrides because `startingBackground=false`. Other completed read-only findings remain: durability only enumerates `timedWork`; killed/due/host work can be lost or revived; exhausted map iterators need TYPE_CLEAR tag 5; equal-due file order is lost; activation metadata is incomplete; exact production resume coverage is absent; connection order is sorted incorrectly; due-time rounding differs; interrupted envelopes are not round-tripped.
- Current blocker: the exact startup call/task holding the main construction path has not yet been identified. There is no external blocker.
- Exact next action: before the direct process's 60-second timeout, run `/opt/java/25/bin/jcmd 247475 Thread.print` and inspect only the main/startup and scheduler frames. Then reproduce with a longer disposable ceiling if needed, add the smallest production-start regression for the exact blocking path, fix its real owner, and rerun the managed Mongoose gate. Keep this notes file untracked.

## 2026-07-21 codec startup fix and post-bind transport blocker

- The first 60-second disposable PID expired before attach. A new 180-second direct run produced a live thread dump at 42.6 seconds: the main thread was still in `LambdaMooV17Codec.readValue` tag 10, specifically `MapValue.with` repeatedly copying/rehashing accumulated entries and `StringValue.hashCode/foldUnicode`. The same process subsequently bound port 55431, proving it was slow decode rather than a stuck startup task or network problem.
- The decoder now accumulates map entries once in `LinkedHashMap`, retains the exact existing `MapValue.compareKeys == 0` duplicate relation through a seen-key scan, and constructs one immutable `MapValue` at the end. `MapValue.compareKeys` was made public as the single existing semantic authority; no comparator/tree, builder, alternate value, or writer change was added.
- What worked: the exact `QueuedTaskV17CodecTest.readsEveryTaskRecordFromPinnedMongooseFixture` still passed and its test-only XML duration fell from about 37.5 seconds to 7.8 seconds. Phase 4 was reread. The next exact managed Mongoose run opened its port inside the 30-second harness deadline, eliminating the prior startup timeout.
- The managed gate now fails later in `SocketTransport._ensure_standard_properties`: after default `connect Wizard`, `PREFIX -=!-^-!=-`, and `SUFFIX -=!-v-!=-`, the first exact command `; try add_property(#0, "object", #1, {#0, "rc"}); except (ANY) return 0; endtry` receives no framed response within the transport's three-second socket timeout. All four `acos.yaml` rows error from that single session-setup timeout.
- A new exact disposable direct server uses port 55432 and PID 251128. The same conformance CLI was run against it as an external server with the exact Mongoose oracle/target manifests so the server remained inspectable. During the stall, `jcmd 251128 Thread.print` showed the main thread healthy in `MooServer.acceptConnections` and every visible `moo-vm-*` platform worker parked idle on the executor queue. This rules out listener failure and an actively running platform VM segment. Default `Thread.print` did not expose the relevant virtual session/ready-queue state in the retained output.
- Current blocker: the exact reason the accepted connection's property-eval task does not publish output is not yet identified. There is no external blocker.
- Exact next action: preserve or relaunch the disposable server, reproduce the external transport timeout, and capture virtual-thread/session state using JDK JSON thread dump or targeted existing scheduler/session diagnostics. Trace the accepted line from `MooServer` through ready queue, publication, and socket writer to the first missing owner boundary; add the smallest regression there, fix it, and rerun the exact managed Mongoose gate. Keep this notes file untracked.

## 2026-07-21 publication topology investigation continuation

- A held exact connection plus JDK JSON thread dump proved the accepted socket reader was alive and `moo-connect-timeout--3` was blocked in `PublicationScheduler.submit(...).join()`. One VM worker owned the publication head inside `WorldHistory.validateTopology`, specifically reciprocal `List.contains` checks. The main listener remained healthy and other VM workers were parked.
- `WorldHistory.publish` previously rebuilt and validated the entire committed topology for every successful transaction. A safe topology-projection guard was added: full validation still runs for player-index changes, permanent object existence/id/location/parents/contents/children changes, and anonymous existence/parent changes. Non-structural property/verb/metadata/WAIF/pending-finalization/effect-only commits skip only the unchanged full-graph validation. The validator itself is unchanged.
- What worked: `WorldTxnTest`, `WorldTxnPropertyTest`, and `WorldTxnMultipleInheritanceTest` all passed under WSL Java 25, covering ordinary commits and structural multiple-inheritance/recycle mutations. Phase 4 was reread.
- What did not work: the exact managed Mongoose `acos.yaml` gate still opened its port but timed out on the first standard-property response. Therefore the pre-guard validation stack was either a genuinely structural publication ticket or not the only cause. The earlier optimization was not broadened speculatively.
- A new-build disposable server is PID 255677 on port 55433. An external conformance attempt timed out before the first post-guard JSON dump, so that dump contained only the accept loop. A second exact raw connection is held in terminal session 82265 after sending `connect Wizard`, PREFIX, SUFFIX, and the first `add_property` command. `/tmp/banteng-threads-post-guard-live.json` was captured while that connection was live but has not yet been extracted because this mandatory checkpoint fired.
- Current blocker: the post-guard live stack owner has not yet been read. There is no external blocker.
- Exact next action: extract only Banteng-owned stacks from `/tmp/banteng-threads-post-guard-live.json`. If topology validation remains, identify which exact record topology changed before altering code. If it moved, trace the new owner. Then add the smallest regression at that owner, fix it, rerun focused tests, reread Phase 4, and rerun the exact managed Mongoose gate. Keep this notes file untracked.

## 2026-07-21 exact Mongoose authentication regression

- The post-guard live JSON dump contained no VM/publication owner. The accepted socket reader had consumed all supplied lines and was waiting for more input; `moo-connect-timeout--3` was only sleeping. The connection remained unauthenticated, so PREFIX, SUFFIX, and eval lines were being treated as login inputs and produced no framed output.
- Exact `#0:do_login_command` source was loaded from the fixture. Its successful path returns the authenticated player object and also calls `$telnet_utils:switch_connection(player, ret)`. Banteng `finishLogin` accepts either `VmState.switchedPlayer()` or an object return present in `world.players()`, so the association contract itself matches the fixture. The exact source launcher was removed after inspection.
- Added `MooServerTest.authenticatesWizardFromPinnedMongooseCheckpoint`: it loads the exact fixture, passes world/tasks/connections through the real `MooServer`, serves an ephemeral socket, sends `connect Wizard`, and requires `*** Connected ***`. It fails red with `SocketTimeoutException` at the input read.
- Added `MooRuntimeTest.authenticatesWizardUsingPinnedMongooseLoginVerb`: it loads the same exact world, opens a negative connection through the production scheduler, executes `connect Wizard`, and asserts returned output plus nonnegative player association present in the fixture player index. It fails red with all three facts: output is `[]`, player remains negative, and it is not in the player index. This proves sockets/framing are not the owner; the stored login VM exits before returning/switching a valid player.
- `resumeRuntime(LOGIN_AUTHENTICATE_AND_ASSOCIATE)` calls `finishLogin` even when the VM ended ERRORED; `finishLogin` returns `state.output()` when neither association signal exists, so an uncaught login MOO error is intentionally silent and matches the observed `[]`.
- `executeLogin` exact blank-input behavior was verified: unless the connection is a trusted proxy, `openConnection` immediately invokes `do_login_command` with an empty line before the explicit `connect Wizard`. A controlled source substitution must preserve that first empty call or it can authenticate too early and give false evidence.
- Current blocker: the first unsupported/failing operation inside the exact stored login call graph is not yet identified. There is no external blocker.
- Exact next action: trace the empty-call and `connect Wizard` call graph beginning with `$network:incoming_connection(player)` without changing blank-input ordering. Use a controlled test/diagnostic that records the VM uncaught error or substitutes only after distinguishing empty from explicit args. Identify the first failing builtin/verb, add the smallest production regression, implement its real Phase 4 dependency, and rerun both exact login tests, reread Phase 4, then rerun managed Mongoose `acos.yaml`. Keep notes untracked.
# 2026-07-21 checkpoint: Mongoose login after `callers()`

- Observed and proved that pinned Mongoose `#0:do_login_command` begins with `callers()`, while Banteng had no `callers` manifest entry and therefore raised `E_VERBNF` before login could proceed.
- Added the pinned Toast contract to the single builtin manifest: zero required arguments, one optional `ANY`, VM-owned, pure, returning current-to-root caller rows excluding the current activation.
- Extended `VmState.callers` to emit Toast's five fields (`this`, verb, programmer, verb location, player), with the optional truthy argument adding the call-site source line as field six.
- The focused VM authority regression and complete `BuiltinCatalogTest` now pass in WSL on Java 25.
- The pinned-checkpoint runtime and real-socket login regressions remain red: no output is emitted, the connection remains a negative object, and the socket times out. This proves a later expression in the real Mongoose login call graph is failing after `callers()`.
- Current blocker: the exact later VM error is swallowed at the runtime completion boundary, so its identity has not yet been observed.
- Next action: inspect the existing VM outcome/error surface at `startStored`/runtime completion, expose the failing outcome in the regression without adding a production execution path, then implement only the exact missing pinned semantic and rerun both checkpoint login tests.
# 2026-07-21 checkpoint: second Mongoose login boundary

- Temporary diagnostics proved the repaired login now passes `callers()` and reaches the exact parent call site `$login:maybe_limit_commands()` on source line 22.
- The called verb returns an uncaught `E_INVARG`; the parent `do_login_command` frame remains at instruction pointer 95, immediately before its `CALL_VERB`, and authentication therefore produces no output or player association.
- No later missing builtin was observed; a temporary missing-builtin print produced no output. All temporary production diagnostics were removed after collecting the evidence.
- Current blocker: the exact expression inside the fixture's `maybe_limit_commands` verb that raises `E_INVARG` has not yet been identified.
- Next action: extract that exact verb body from the checksum-pinned v17 fixture, trace its first failing expression against pinned Toast, add the smallest regression for that semantic, implement it, and rerun the checkpoint runtime/socket login tests.
# 2026-07-21 checkpoint: indexed property assignment root cause

- Extracted the exact checksum-pinned `$login:maybe_limit_commands` source. Its first relevant mutation is `this.current_numcommands[iconn] = this.current_numcommands[iconn] + 1`.
- Instrumented the existing `CALL_VERB` compile boundary and proved the called verb never begins: `MooCompiler` throws `indexed assignment requires a local owner`, which `MooVm` maps to `E_INVARG` at the parent call site.
- Existing intrinsic `wizard` property reads and the called verb itself are present; those are not the blocker. The failure is specifically that `compileAssignment` supports indexed l-values rooted in a local variable but rejects an `Ast.PropertyAccess` collection.
- Removed all temporary fixture-source, VM-unwind, and compile-error diagnostics after collecting the evidence.
- Current blocker: the bytecode has only `SET_INDEX_LOCAL`/`SET_RANGE_LOCAL`; it lacks a correct property-rooted collection update that preserves the receiver and property name across index/value evaluation.
- Next action: add the minimal explicit bytecode/VM operation needed for a property-rooted indexed update, prove `this.items[2] = value` both returns the assigned value and writes the property through `WorldTxn`, then rerun the pinned Mongoose runtime and socket login regressions.
# 2026-07-21 checkpoint: post-indexed-assignment login state

- Added a red-to-green VM regression for `assigned = this.numbers[2] = 9`, proving both the scalar expression result and the committed `WorldTxn` property update.
- Implemented the property-rooted indexed l-value with explicit `DUP_PAIR` and `SET_INDEX_PROPERTY` bytecode operations; the receiver and property expression are evaluated once and the existing `setProperty` permission/write path remains authoritative.
- The pinned Mongoose runtime now gets past `$login:maybe_limit_commands`. `openConnection` returns `Oh, dear. something's gone wrong. (unable to display welcome message.` from the blank welcome path instead of the former empty result. The old regression assertion that this fixture must emit nothing on open is invalid.
- The real-socket regression still times out with its five-second read limit; fixture decode plus blank-login execution can exceed that limit, and the server writes initial output before processing `connect Wizard`.
- Ran the exact managed Toast Mongoose `acos.yaml` command. Toast also times out in `_ensure_standard_properties` after the transport's login-output drain; the transport does not assert that `*** Connected ***` was observed, so this gate failure does not establish a successful Toast login and cannot serve as authentication proof.
- Current blocker: authentication after the blank welcome task has not yet been observed directly on either implementation; the runtime regression stops at its now-invalid initial-output assertion before sending `connect Wizard`.
- Next action: retain and assert the nonempty initial Mongoose output separately, execute `connect Wizard`, inspect its exact VM outcome/player association, and give the real-socket regression enough time to traverse the measured fixture startup/login path without changing production timing.
# 2026-07-21 checkpoint: missing `ancestors` builtin

- After removing the invalid Mongoose `openConnection == []` assertion, the explicit `connect Wizard` still returns no output and leaves the connection unauthenticated.
- Temporary unwind diagnostics proved the final uncaught outcome is `E_VERBNF` in the fixture's `ancestors` utility verb, whose entire executable body is `return ancestors(@args);`. Earlier `E_INVARG`/`E_VERBNF` events in welcome and rate-limit helpers are caught by fixture code and are not the final authentication failure.
- This is not another parser/compiler failure: all reached verbs compile after the indexed-property repair. The `ancestors` canonical builtin is absent from Banteng's single manifest.
- Pinned Toast Mongoose authority at `/root/src/toaststunt-mongoose/src/objects.cc` registers `ancestors` with one required and one optional `TYPE_ANY` argument. Its handler requires an object value (`E_TYPE` otherwise), requires a valid object (`E_INVARG` otherwise), and returns `db_ancestors(object, truthy_optional_full_flag)`.
- Removed all temporary runtime/VM diagnostics after collecting the evidence.
- Current blocker: exact `db_ancestors` output ordering and the optional full flag's inclusion behavior still need to be read from the pinned implementation before adding Banteng's manifest handler.
- Next action: read the pinned `db_objects.cc` implementation, add focused contract/ordering tests, register the Phase 4 dependency through `WorldTxn` ancestry, rerun Mongoose login, and continue the same login target.
# 2026-07-21 checkpoint: missing `verbs` builtin

- Implemented `ancestors` in the single manifest with pinned one-required/one-optional `ANY` shape, permanent and anonymous object validation, depth-first first-visit ordering, and the truthy `full` flag that prepends the queried object. Focused ordering and manifest-authority tests pass.
- Re-ran the exact pinned Mongoose runtime login. It advanced beyond `ancestors` but remains unauthenticated.
- Temporary missing-builtin diagnostics proved the final uncaught dependency is now canonical `verbs`. Missing `idle_seconds` and `server_version` were also observed, but their current calls are inside caught rate-limit/welcome paths; they are not the final explicit-login abort.
- Pinned Toast Mongoose `/root/src/toaststunt-mongoose/src/verbs.cc` registers `verbs` with one `TYPE_ANY` argument. It returns `E_TYPE` for non-object, `E_INVARG` for an invalid object, `E_PERM` when the programmer lacks object read authority, and otherwise returns the target's locally defined verb names in definition order.
- Added a focused red `BuiltinCatalogTest` covering ordered names and Toast validation/permission outcomes, plus `verbs` in the exact reachable-name set. All temporary login/missing-builtin diagnostics were removed.
- Current blocker: the manifest handler for `verbs` is not yet implemented.
- Next action: register `verbs` adjacent to the existing verb metadata handlers, use local `WorldObject`/`WorldAnonymousObject` verb lists with owner/wizard/read-flag authority, make the focused test green, then rerun the same Mongoose login target.

# 2026-07-21 checkpoint: Mongoose login command-selection divergence

- Implemented the pinned `verbs` handler and made its focused authority/order regression plus the complete reachable-manifest test pass.
- The explicit pinned-fixture login now completes normally with return value `0`; it no longer terminates on a missing builtin, but the connection remains unauthenticated.
- Temporary dispatch diagnostics prove `#10:parse_command("connect", "Wizard")` returns the fallback verb name `?`. The fallback verb prints the valid-command message and returns `0`, so `$login:connect` is never invoked.
- The exact `parse_command` source accepts the requested verb only when `verb_args(i, verb) == {"any", "none", "any"}` and `verb_info(i, verb)[2]` contains `x`, iterating `{this, @$object_utils:ancestors(this)}`. The first divergence therefore lies in ancestor selection, named verb lookup, returned verb metadata, or the comparison/index execution around those values.
- A diagnostic rerun encountered a generated-class incremental compiler race for `moo/server/package-info.class`; a clean rebuild removed that build-state error. The clean targeted test then reproduced the same semantic login failure.
- Current blocker: the exact metadata value or error for the real fixture's `connect` definition has not yet been observed, so changing `verb_args`, `verb_info`, ancestry, equality, or indexing would be speculative.
- Next action: inspect the checksum-pinned fixture's local/ancestor `connect` definition and directly exercise Banteng's `verb_args` and `verb_info` results for that defining object. Compare those values with the pinned Toast contract, fix only the first proven divergence, remove all temporary diagnostics, and rerun both production login regressions.

# 2026-07-21 checkpoint: valid Mongoose login authority

- The prior `connect Wizard` regression premise is invalid and both uncommitted regressions were removed. All temporary Java login/missing-builtin diagnostics were also removed.
- The conformance harness interprets a missing login script as `connect {user}`. The checked-in Mongoose profiles use an empty command array, so `run_managed_wsl.sh` omits the login-script option and the invalid default is selected.
- Live pinned Toast proved the fixture advertises `NEWLOGIN` and `GUEST`, not executable `CONNECT`/`LOGIN`. `newlogin` enters the account flow: username, password, then account player selection/default-player handling.
- Toast emergency-mode inspection found `$account_login == #1414`, its account manager is `#3843`, and account values are anonymous descendants of `#3842`. The existing `daiverd` account contains wizard player `#2`; no account contains programmer `#3`.
- On a temporary `/tmp` copy only, setting a known password/default player for the wizard account authenticated successfully through exact commands `newlogin`, `daiverd`, `banteng`. A temporary `programmer` account containing `#3` reached login but was rejected until its `email_verified` field was set.
- A second temporary fixture copy now contains dedicated `wizard` and `programmer` accounts, both using password `banteng`, `crypt` validation, verified email state, and default players `#2` and `#3` respectively. The source fixture has not yet been changed.
- Current blocker: the full three-command template `newlogin`, `{user}`, `banteng` has not yet been proven for both temporary accounts and has not yet been run through the managed Toast gate.
- Next action: run the exact three-command sequence against the temporary fixture for both `Wizard` and `Programmer`, then run a managed Toast row using that temporary valid fixture/profile. Only after live proof, apply the account changes to the ignored local fixture, update its pinned checksum and the plan/manifests/tests together, and continue Phase 4.

# 2026-07-21 checkpoint: corrected Mongoose fixture and profile

- Live pinned Toast authenticated the exact `newlogin`, `Wizard`, `banteng` sequence as wizard player `#2`.
- The first temporary `Programmer` mapping to stock object `#3` was invalid for this database because `#3` lacks Mongoose player properties such as `first_connect_time`. Live wizard evaluation identified `#83` (`taco`) as a valid nonwizard programmer with the required player surface.
- After remapping the temporary `programmer` account to `#83`, live pinned Toast authenticated exact `newlogin`, `Programmer`, `banteng` successfully.
- Applied only the proven account data to the ignored local `fixtures/mongoose.db`. Its new SHA-256 is `29ae63e68fb2dd5445ceea58dafb7967da79c9b16a83b776e9e328c039d1ec91`; Git still ignores it via `/fixtures/mongoose.db`.
- Corrected the plan, Toast/Banteng Mongoose manifests, supported-profile authority, v17 layout checksum regression, and managed-runner regression to use the new checksum and `login_commands` `newlogin`, `{user}`, `banteng`.
- `scripts/test_managed_runners_wsl.sh`, the exact Mongoose profile verifier, and a direct fixture checksum check all pass in WSL.
- Current blocker: the corrected profile has not yet completed an actual managed Toast conformance row; that is the next decision gate.
- Next action: reread the active plan after the passing runner/preflight gate, run managed Toast Mongoose `acos.yaml`, then inspect the first actual oracle/setup failure before changing Banteng semantics.
## 2026-07-21 fixture-preservation correction

- The current ignored `fixtures/mongoose.db` at SHA-256 `29ae63e68fb2dd5445ceea58dafb7967da79c9b16a83b776e9e328c039d1ec91` is not a valid authority for the Phase 4 restoration gate. Its v17 preamble contains 6 queued tasks, 16 suspended tasks, 0 interrupted tasks, and 0 active connections.
- The plan requires the original checkpoint state: 14 suspended tasks, 2 interrupted tasks, and 6 active connections, in addition to queued tasks. The pinned fixture test failed first at a queued local encoded as Toast `TYPE_NONE` (tag 6).
- Cause: the account-enabled fixture was produced by starting the original database in pinned Toast emergency mode, adding accounts, and saving. Toast normalized live task and connection state during startup/save, so that procedure destroyed the checkpoint evidence required by Phase 4.
- The exact original fixture remains recoverable at WSL `/tmp/mg_in.db`, SHA-256 `33201970097d3d2d2bfc0d5f875f087d587601bf8255ef31ef19b416d65ac925`. Several other `/tmp/banteng-mongoose-*.db` copies have the same checksum.
- The tracked plan/profile edits that pinned `29ae...` and described the emergency-mode save are therefore incorrect and must not be committed in their current form.
- Current blocker: preserve the original task and active-connection sections while adding valid conformance accounts without booting and resaving the fixture.
- Next action: compare the original and account-enabled v17 world sections, determine whether the account change can be transplanted without startup side effects, then reconstruct and verify a fixture whose task/connection preamble matches the original exactly and whose live login works against pinned Toast.
