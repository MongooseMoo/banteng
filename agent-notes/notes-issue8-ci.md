# Issue 8 and CI working note

## Current state

- Repository: `MongooseMoo/banteng`; default branch `master`; no existing PRs and no branch protection.
- Primary checkout is on `master` at local commit `de14645` and is one commit ahead of `origin/master` (`bcff86b`). The local commit adds the OpenRewrite plugin and `scripts/rewrite.sh`; issue #8 explicitly requires that mechanism.
- The primary checkout has many unrelated untracked notes, reports, prompts, and investigation files. Preserve them and use linked worktrees with explicit staging.
- Issue #8 is open: "Extract BuiltinCatalog.Result into a sealed BuiltinResult effect hierarchy." Its literal contract is a top-level sealed `moo.builtin.BuiltinResult`, one record per current factory/effect shape, migration of consumers, and an exhaustive `MooVm.applyBuiltinResult` switch.

## CI findings

- Banteng currently has no `.github` directory or workflow.
- Barn's current `origin/master:.github/workflows/ci.yml` has separate `Build & unit tests` and `MOO conformance suite` jobs. The conformance job checks out `MongooseMoo/moo-conformance-tests` at `main`, runs the server in managed mode, requires admission/conformance, uses strict markers and unexpected-skip failure, and uploads JUnit/admission evidence.
- Banteng already makes Java static analysis blocking through Java 25 `-Xlint:all -Werror`, Error Prone 2.50.0, NullAway 0.13.7/JSpecify, and ArchUnit tests. `docs/reports/java25-static-analysis-decision.md` says to retain these and not add a second nullness framework.
- All checked-in shell scripts pass `shellcheck scripts/*.sh` in Debian WSL.
- Current official action releases inspected: checkout v7.0.1, setup-java v5.7.0, gradle/actions v6.3.0, setup-uv v10.0.1, upload-artifact v7.0.1. Full tag commit SHAs were resolved for pinning.
- Stock conformance profiles point to WSL-specific absolute fixture paths, so CI needs workspace-relative fixture paths and a CI target manifest (or an equivalent generated manifest) while preserving Banteng's profile identity and feature flags. The managed server command must use the installed Java distribution and write the `{manifest}` target evidence expected by the conformance admission protocol.

## Publication guard

- User requested two likely deliverables: CI first if independently landable, then issue #8 developed test-first and PR'd to close the issue.
- Before push/PR creation, show the user the exact final diff/commit scope and exact PR title/body. Do not publish text under the user's name before they read it.
- Never squash. If later authorized to merge, use `gh pr merge <number> --merge` after auditing scope, commits, issue closing phrase, required checks, and exact command.

## Next action

1. Create an isolated CI worktree from `origin/master` on `agent/ci-conformance-static-checks`.
2. Implement a pinned, least-privilege workflow with separate Java/static and managed conformance jobs; locally validate YAML, shell scripts, Gradle gate, and managed conformance command/profile behavior.
3. Commit locally only, then present the exact PR draft and scope for publication approval.
4. Create a separate issue #8 worktree from `de14645`, write and capture a failing architectural/behavioral test, then implement the sealed hierarchy and exhaustive dispatch.

## Checkpoint 2

- Created isolated worktrees:
  - `C:\Users\Q\code\banteng-ci`, branch `agent/ci-conformance-static-checks`, from `origin/master`/`bcff86b`.
  - `C:\Users\Q\code\moo-conformance-ci`, detached clean `origin/main`/`6528b8c`, for exact local conformance validation.
  - `C:\Users\Q\code\banteng-issue-8`, branch `agent/issue-8-builtin-result`, from local OpenRewrite commit `de14645`.
- CI worktree changes currently consist only of `.github/workflows/ci.yml` and strict Gradle dependency lock mode in `build.gradle.kts`.
- Workflow jobs are `Static analysis & tests`, `CodeQL`, `Dependency review`, and `MOO conformance suite`. Official actions are pinned to full SHAs and all checkouts use `persist-credentials: false`.
- The Java job runs ShellCheck, then separate Gradle invocations for `clean check`, `fuzzTest`, `schedulerStress`, `jcstress`, and `jmh`. Separate invocations are intentional isolation.
- A first local invocation combined all Gradle tasks. With `org.gradle.parallel=true`, ordinary tests overlapped fuzz/stress work and `OperationsCommandTest` failed because a documented server exited 143 after SIGTERM (`436 tests completed, 1 failed`). Do not change the stable test; CI sequencing caused the contamination. The workflow now runs each gate in its own Gradle invocation, and standalone `clean check` is running.
- `actionlint` accepts the workflow. `zizmor` initially reported five `artipacked` warnings; after disabling persisted checkout credentials it reports `No findings to report. Good job! (9 suppressed)`. ShellCheck and `git diff --check` emit no findings.
- The exact managed stock conformance run against the clean conformance worktree is running. A first local command failed before execution due Windows/WSL nested quoting of jq; this was a local command-construction error, not a conformance result. The workflow's native bash jq quoting is valid.
- Issue #8 code inspection confirms `MooVm.applyBuiltinResult` is a 250-line Optional-probing dispatcher, including an explicit three-Optional consistency check for move. The new test should lock the top-level sealed hierarchy and factory-to-variant mapping before implementation; the implementation must replace the dispatcher with a compiler-exhaustive pattern switch and preserve behavior.

### Next action

1. Wait for standalone `clean check` and managed conformance raw results; fix only CI-owned defects.
2. Enumerate every current `BuiltinCatalog.Result` factory and data shape from the exact source block.
3. Add the issue #8 structural/factory contract test, run it red against the missing `BuiltinResult`, and commit the red proof separately if the repository's test-first history supports test-only commits.

## Checkpoint 3

- The standalone `clean check` on the CI branch repeated the base failure: `OperationsCommandTest` expected exit 0 but got 143 (`436 tests completed, 1 failed`). A later focused run on clean `origin/master` passed, confirming a startup race rather than a deterministic CI-command contamination.
- Root cause: `MooServer` binds its primary `ServerSocket` in its constructor. `Banteng.call()` constructs that publicly reachable server before `SignalRegistration.install("TERM", ...)`. A probe can connect during the gap, terminate the process, and receive the JVM default SIGTERM exit 143.
- Created `C:\Users\Q\code\banteng-shutdown-readiness` on `agent/install-sigterm-before-bind` from `origin/master`. Added a focused repeated immediate-SIGTERM regression to make the race reproducible. This prerequisite stays separate from the CI and issue #8 branches.
- The Java CI job also needs the conformance repository as a sibling because existing Banteng tests intentionally resolve `../moo-conformance-tests/.../Test.db`. The workflow now checks Banteng out to `banteng/`, conformance to sibling `moo-conformance-tests/`, and runs Gradle from `banteng/`; it does not change stable fixture tests.
- The first managed conformance command reached collection but failed before admission because the local validation shell omitted `JAVA_HOME`; the installed distribution reported no Java. GitHub `setup-java` supplies `JAVA_HOME`, so this is a local command-environment error. Rerun locally with `JAVA_HOME=/opt/java/25`.
- Issue #8 red proof is captured in `BuiltinResultTest`: `compileTestJava` failed with missing `moo.builtin.BuiltinResult`. The test locks a sealed hierarchy with 21 effect-specific record shapes and verifies `Move(object, destination, position)` is atomic.
- Added initial `BuiltinResult.java` and a declarative OpenRewrite `ChangeType` recipe in `rewrite.yml`. The first `scripts/rewrite.sh --apply` attempt failed because the rewrite configuration resolved 15 metadata artifacts absent from dependency verification and because the rewrite task is not configuration-cache serializable. A direct `rewriteRun --no-configuration-cache --write-verification-metadata sha256` progressed to Java compilation, proving the dependency graph can be recorded.
- Error Prone then rejected a nested record named `Error` (`AvoidCommonTypeNames`, clashes with `java.lang.Error`) under `-Werror`. Renamed it to `ErrorResult`; do not suppress the static check.
- Public-surface caution: interface static methods are implicitly public. Preserve only the four previously public factories (`value`, `error`, `hostWork`, `raised`) and migrate package-private effect construction to record constructors rather than widening all former factories.

### Next action

1. Finish the startup-race red run, implement pre-bind SIGTERM coordination, and validate the isolated base fix.
2. Rerun OpenRewrite ChangeType after the `ErrorResult` rename, inspect every changed file, then remove the old nested `Result` and convert factory-by-factory.
3. Rerun managed conformance with Java exported and finish CI workflow validation.

## Checkpoint 4

- Issue #8 red contract is preserved as local commit `ba91eaf` (`test: define sealed builtin result contract`) on top of `de14645`. The commit contains only `BuiltinResultTest.java`; production work remains uncommitted.
- The red output was a compile failure for missing `BuiltinResult` (10 errors, including the expected missing type references). The contract now avoids requiring a newly public `move` factory: it constructs `BuiltinResult.Move` directly, preserving the narrower old visibility boundary.
- OpenRewrite `org.openrewrite.java.ChangeType` completed successfully with the declarative `moo.builtin.ExtractBuiltinResult` recipe. It migrated 15 production/test consumer files from `moo.builtin.BuiltinCatalog.Result` to `moo.builtin.BuiltinResult` and reported `BUILD SUCCESSFUL`.
- OpenRewrite renamed the old nested record declaration as well as its consumers. The obsolete 507-line Optional/telescoping-constructor block at the end of `BuiltinCatalog.java` has now been removed, leaving the new top-level sealed interface as the sole result type.
- Gradle's verification-metadata write converted the legacy XML from CRLF to LF but made no semantic XML changes. Converted it back with `unix2dos`; `git diff` for `gradle/verification-metadata.xml` is empty. Preserve that byte discipline in subsequent runs.
- The first base-fix implementation hit NullAway because `AtomicReference<MooServer>` was initialized/compared with null. Changed the type to `AtomicReference<@Nullable MooServer>` and requires non-null after the attachment latch. Focused lifecycle tests are running.
- The repeated immediate-SIGTERM regression happened to pass red on one focused run despite the earlier two full-suite exit-143 failures; the race is timing-sensitive. Do not claim that focused run proved the base was healthy. The raw full-suite failures and constructor/handler ordering establish the defect.

### Next action

1. Convert every remaining `BuiltinResult.zero/checkpoint/...` call and the three invalid `new BuiltinResult(...)` sites to concrete records; keep only the four formerly public factory methods.
2. Replace `MooVm.applyBuiltinResult` Optional probing with an exhaustive pattern switch and effect-specific helpers.
3. Compile/fix consumer assumptions, run focused hierarchy/VM/runtime tests, then the full Java and conformance gates.
## Checkpoint 5

- Current finding: the OpenRewrite `ChangeType` migration completed across production and tests, but the follow-up `ConvertBuiltinResultFactories` recipe did not change any call sites; `rg` still finds the old package-private factory names.
- Current finding: the removed `BuiltinCatalog.Result` record had a `taskLocal` optional field but no factory or call site populated it. Issue #8's required sealed hierarchy therefore has no task-local variant, and the exhaustive dispatcher should not preserve unreachable optional-slot probing.
- Current state: issue branch has local red-contract commit `ba91eaf`; uncommitted work includes the top-level sealed `BuiltinResult`, ChangeType output, and rewrite recipe. No issue implementation has been published.
- Current state: the separate listener-readiness branch has the early SIGTERM registration implementation and focused tests passing, but still needs a full `clean check`, diff audit, and local commit.
- Blocker: CI cannot become green against current base because the existing operations test exposes exit 143 during immediate shutdown; that base repair must remain a separate prerequisite PR.
- Next action: mechanically convert the old factory calls with `apply_patch`, replace `MooVm.applyBuiltinResult` with the exhaustive sealed switch, then compile to drive remaining consumer and test migrations.
## Checkpoint 6

- Current finding: `compileJava` succeeds under managed Debian WSL with Java 25 after replacing `MooVm.applyBuiltinResult` with an exhaustive switch across all 21 required variants and migrating `PublicationScheduler` wake handling.
- Raw production compile endpoint: `BUILD SUCCESSFUL in 1m 46s`, one `compileJava` task executed.
- Current finding: `compileTestJava` reaches javac's 100-error cap because `BuiltinCatalogTest` and later tests still call removed optional-slot accessors such as `value()`, `error()`, `hostWork()`, `checkpointRequest()`, `threadMode()`, and effect request accessors.
- Current state: no compatibility accessors were added to production. The correct next step is to migrate stable assertions to concrete record variants, not recreate the union protocol.
- Current state: old factory names in the remaining `BuiltinCatalog` call sites were converted explicitly; production consumers now compile.
- Blocker: test migration is broad and compiler output is capped, so it must proceed in focused mechanical passes followed by repeated `compileTestJava` runs.
- Next action: add test-local variant assertion helpers where they improve readability, mechanically migrate value/error assertions, then convert effect-specific assertions directly to `assertInstanceOf` records.
## Checkpoint 7

- Current finding: `BuiltinCatalogTest` contained 389 old `value()`/`error()` result-slot assertions plus effect-slot assertions. A chunked, overlap-safe mechanical pass rewrote 407 accessor calls to test-local projection function form.
- Current state: the mechanical pass used `apply_patch` only and preserved line count; no production compatibility API was introduced.
- Current state: projection functions have not yet been added, so test sources are intentionally between migration steps and are not expected to compile yet.
- Observation: the projection helpers will map only concrete sealed variants. `Value` and error projections will return present only for `Value`, `ErrorResult`, and `RaisedError`; effect projections will be explicit record matches. This keeps failures visible where an old test wrongly expected an effect record to also carry a synthetic return value.
- Blocker: nested host-work result calls and chunk-boundary expressions may need a small second mechanical pass after compilation.
- Next action: add the test-local sealed-variant projections, run `compileTestJava`, and directly update effect assertions whose old optional-union expectations no longer represent the new contract.
## Checkpoint 8

- Current finding: focused `BuiltinResultTest`, `BuiltinCatalogTest`, and `TaskRegistryTest` initially produced four behavioral failures where old assertions expected effect results (`BootPlayer`, `SetConnectionOption`, `ForceInput`, `Checkpoint`) to also contain a synthetic zero value.
- Current state: those four assertions now compare the concrete effect records directly; the focused test command subsequently completed successfully in 52 seconds.
- Current finding: the obsolete `BuiltinCatalog.InitializeRequest` carrier is no longer used by production after the sealed migration. Create tests now assert `BuiltinResult.Initialize` directly, and the old carrier has been removed.
- Current state: redundant same-package `BuiltinResult` imports and unused `OptionalDouble`/`Callable` imports were removed. No production optional-union compatibility API exists.
- Blocker: the full test suite and higher static checks have not yet run against the final cleanup; additional consumer behavior failures may still surface.
- Next action: run `compileTestJava` after cleanup, then the full `test` task and static checks; address only sealed-result regressions before auditing the issue diff and commit boundaries.
## Checkpoint 9

- Current finding: the pinned workflow passes `actionlint` and `zizmor` with no findings; ShellCheck emitted no findings. Windows-side `git diff --check` is clean for the CI branch.
- Current finding: the exact managed `admission or conformance` selection completed against clean Banteng `bcff86b` with strict markers and fail-on-unexpected-skip, but is baseline red: `11 failed, 9468 passed, 2132 skipped, 1 warning in 624.20s`.
- Failure groups: anonymous recycle property reference count (1), `set_task_perms` programmer permission (1), reverse-inserted MAP key topology for bool/waif/anonymous keys (3), panic suspended-root serialization (1), and missing startup repair output databases for Anon1/2/3/4/7 (5).
- Current state: no skips, allow-failure settings, profile downgrades, or stable-test changes were added. The CI branch remains uncommitted and unpublished.
- Current state: separate base SIGTERM readiness commit is `a693ef4`; issue #8 commits are `de14645`, `ba91eaf`, and `19b205e`. None are published.
- Blocker: a CI-only PR would be knowingly red on both the now-fixed SIGTERM race (until its base PR lands) and the 11 conformance defects. Guardrails require base repairs or an evidence-backed declared capability boundary before CI can be merged green.
- Next action: compare Barn's conformance workflow/profile selection with Banteng's current profile and inspect the 11 failures as distinct base contracts; do not hide failures or fold them into issue #8.
## Checkpoint 10

- Current finding: Barn's current master CI is green and runs the same full `admission or conformance` selection; Banteng's 11 failures are genuine implementation gaps, not a Barn-style capability/profile exclusion.
- TDD red proof on new `agent/conformance-baseline`: focused run completed with exactly three new failures: ordered MAP equality, non-wizard `set_task_perms` to another programmer, and initial checkpoint publication.
- Current state: red tests are committed separately as `f311eba test: pin conformance baseline gaps`.
- Current implementation: MAP Java equality/hash now follow entry topology; `set_task_perms` allows only self unless the current programmer is a wizard; startup submits a separate `INITIAL_CHECKPOINT` task after the startup task publishes, so finalization/garbage collection precedes serialization.
- Observation: anonymous recycle's extra finalization comes from `MooVm` explicitly collecting defined property values into VM collection deferrals. The conformance authority says that nested property value is not recycled in this case.
- Blocker: the three green implementations and recycle adjustment have not yet been compiled or tested. Panic suspended-root serialization remains undiagnosed after these changes.
- Next action: remove only the anonymous-recycle property-value deferral/helper, rerun focused Java tests and the four relevant conformance cases, then isolate the remaining panic serialization failure.

## Checkpoint 11

- Current finding: the corrected managed focused selection included capability admission plus 13 affected conformance cases. It completed with `3 failed, 11 passed, 11597 deselected in 207.97s`.
- Remaining failures are exact: `recycling_anonymous_recycles_property_values_on_object` still observes two recycle-hook calls instead of one; `startup_repair_anon4` and `startup_repair_anon5` do not produce their expected `.db.new` checkpoint files.
- Current finding: the MAP topology, `set_task_perms`, startup Anon1/2/3/6/7, anonymous parent-property recycle, and admission cases in that selection pass with the uncommitted implementation.
- Observation: removing the VM's explicit property-value collection deferral was insufficient for the anonymous object-defined-property case because the runtime's post-publication anonymous sweep still identifies the now-unclaimed nested value as a separate finalization root.
- Observation: the initial startup checkpoint resolves several original missing-output cases but Anon4 remains red and Anon5 newly lacks output in this focused run; direct fixture startup logs/checkpoint effects must be inspected before changing scheduler ordering.
- Current state: the conformance-baseline branch still has red-test commit `f311eba` plus uncommitted production changes in `MooValue`, `BuiltinCatalog`, `MooRuntime`, and `MooVm`. No branch has been published.
- Blocker: the exact startup failure mode for Anon4/Anon5 and the panic suspended-root mismatch are not yet diagnosed; the conformance repair cannot be committed green yet.
- Next action: run Anon4/Anon5 directly from unique WSL temporary directories and inspect process output/checkpoint files, then add a narrowly scoped recycle ownership regression and fix the runtime sweep without changing the external conformance tests.

## Checkpoint 12

- Current finding: the anonymous recycle distinction is driven by property ownership. A nested anonymous value in a property defined on the recycled anonymous object must be deferred for one publication sweep; the same value through an inherited parent property must be collected. The original `property.defined()` deferral encodes this and has been restored.
- Current finding: after rebuilding `installDist`, a managed five-node slice completed with four passes and only the panic case failing: admission, defined-property recycle, startup Anon4, and startup Anon5 passed.
- Raw endpoint for that slice: `1 failed, 4 passed, 11606 deselected in 215.57s`.
- Remaining panic mismatch is exact: the anonymous suspended-task root remains object `10`, but the waif root is emitted as a reference (`13`, `r 0`, `.`) instead of an owned waif definition carrying class `9` and owner `3`; the conformance parser therefore observes `(None, None, 10)` instead of `(9, 3, 10)`.
- Observation: a preserved direct panic dump shows the same waif is first defined in a queued `server_started` activation local and then referenced from the actual suspended holder task's `state` local. Panic serialization shares one global waif identity table across durable tasks/activations, causing the ownership-defining occurrence to precede the live suspended root.
- Observation: direct Anon4/Anon5 runs create checkpoints near the 750 ms assertion boundary, but both passed in the rebuilt managed slice. Do not add a synthetic pre-repair file copy or weaken the conformance timeout.
- Current state: `MooRuntime` still contains the synthetic `INITIAL_CHECKPOINT`; its necessity and interaction with natural shutdown need reevaluation after all semantic failures are green.
- Blocker: panic serialization must make live suspended task roots own their waif encoding without promoting them to world pending-finalization roots or losing cross-reference identity.
- Next action: inspect `PublicationScheduler.durableTasks()` ordering and `LambdaMooV17Codec.writePanic` context lifetime, add a local codec regression for the exact duplicate-waif-across-durable-task shape, then implement the narrow panic-only ownership ordering/context fix.

## Checkpoint 13

- Current finding: panic dumps now exclude the currently publishing panic task from durable work. A preserved direct dump changed from one stale queued panic task plus one suspended holder to `0 queued tasks` and `1 suspended task`; the holder's state owns waif class `9`, owner `3`, and anonymous object `10`.
- Exact managed panic plus admission endpoint is green: `2 passed, 11609 deselected in 203.55s`.
- Full managed conformance endpoint after all current changes is still red: `12 failed, 9467 passed, 2132 skipped in 633.69s`.
- One full-suite failure is directly caused by the synthetic `INITIAL_CHECKPOINT`: `audit_checkpoint_hooks_order_status_and_boundary` observes an unsolicited `checkpoint_finished(1)` before the test-requested checkpoint boundary. The synthetic checkpoint must be removed.
- The other eleven full-suite failures are startup/shutdown lifecycle failures: three fixture-provenance recipes and two pending-waif startup fixtures exit `1`; startup Anon1/2/3/4/6/7 miss their `.db.new` file at the 750 ms assertion.
- Observation: direct natural or SIGTERM shutdown logs show `IllegalStateException: publication scheduler is closed` from `MooServer.shutdown`/`MooRuntime.close` while a shutdown effect is still being published. Closing scheduler ownership from inside its own publication worker is the likely shared mechanism behind exit `1` and late/missing checkpoint evidence.
- Current state: semantic MAP, permission, recycle-ownership, and panic-root repairs pass their focused managed cases; the full gate proves the startup lifecycle is not yet correct.
- Blocker: `MooServer.shutdown` currently closes runtime-owned scheduling during effect publication. Shutdown must request listener closure and let the outer server lifecycle own runtime close/await, while preserving checkpoint completion before exit.
- Next action: remove `INITIAL_CHECKPOINT`, inspect and test `MooServer.shutdown`/`close`/`gracefulShutdown` ownership, make close idempotent without swallowing real errors, rebuild, and rerun the natural-shutdown fixture slice before another full conformance run.

## Checkpoint 14

- Current finding: removing `INITIAL_CHECKPOINT` eliminates the unsolicited `checkpoint_finished(1)` lifecycle event; no startup checkpoint shim remains.
- Current finding: `MooServer.shutdown()` now closes transport only. Runtime scheduler shutdown remains owned by outer `close()`, while explicit `gracefulShutdown()` closes the runtime only after its synchronous shutdown task completes. Focused Java graceful-shutdown and panic tests pass.
- The lifecycle slice without startup JVM tuning reduced failures to the original five deadline misses: `5 failed, 6 passed, 11600 deselected in 220.08s`; all natural exit-code and checkpoint-hook failures were fixed.
- Cold-start measurements on the same Debian WSL and Linux `/tmp`: default Anon1 process completes in `real 0.83`; `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC` completes in `real 0.69`. The conformance contract asserts file evidence after 750 ms.
- Decision: do not impose reduced-tier compilation or Serial GC as Banteng production defaults. Scope them to the CI conformance job, whose short-lived fixture JVMs intentionally need deterministic sub-750-ms cold starts.
- Exact managed lifecycle selection under that CI JVM environment is green: `14 passed, 11597 deselected in 215.14s`.
- Current state: the conformance-baseline implementation has semantic fixes for MAP topology, task permission, anonymous recycle ownership, panic current-task exclusion, and shutdown ownership. It still needs full Java and full managed conformance validation and a commit audit.
- Next action: add the conformance-only `JAVA_OPTS` to `.github/workflows/ci.yml`, rerun the complete managed `admission or conformance` selection under the exact CI environment, then run the Java gate and split/commit the conformance repair without importing the separate pre-bind SIGTERM branch.

## Checkpoint 15

- Current finding: `.github/workflows/ci.yml` now scopes `JAVA_OPTS: -XX:TieredStopAtLevel=1 -XX:+UseSerialGC` to the conformance job only, with a comment tying it to the suite's 750 ms short-lived startup evidence boundary.
- Exact full managed CI selection is green under that environment: `9479 passed, 2132 skipped in 613.30s (0:10:13)`.
- Current finding: no unexpected skip or marker relaxation was used; the command retained `admission or conformance`, `--fail-on-unexpected-skip`, and `--strict-markers`.
- Current state: the conformance-baseline branch still has red-test commit `f311eba` plus uncommitted implementation. The implementation now modifies `MooValue`, `BuiltinCatalog`, `MooRuntime`, `PublicationScheduler`, and `MooServer`; the earlier experimental `MooVm` change has been restored to the base behavior and should not remain in the final diff.
- Current state: the CI branch remains uncommitted and unpublished; it includes the workflow, strict dependency lock mode, and the conformance-only JVM environment.
- Blocker: the conformance repair still needs the full Java/static gate, diff audit, and focused commit. The Java gate may still expose the separately prepared pre-bind SIGTERM race from current base; do not import that fix into this branch.
- Next action: run `clean check` on the conformance-baseline branch, inspect exact failures and diff, commit only the conformance repair if scoped, then locally compose prerequisite branches in dependency order for final CI validation without rewriting their preserved commits.

## Checkpoint 16

- Current finding: Java `MapValue.equals/hashCode` remains order-independent for internal value/storage contracts. Toast topology-sensitive MAP equality is implemented only in `MooVm.mooEquals`, recursively over lists and insertion-ordered map entries. The three authoritative reverse-topology MAP cases plus admission pass: `4 passed, 11607 deselected in 209.27s`.
- Current finding: the final conformance-branch `clean check` has only the known separate pre-bind SIGTERM failure: `OperationsCommandTest` expected exit `0` but observed `143`; `437 tests completed, 1 failed`. No conformance-repair test remains red.
- Current state: conformance branch is clean with commits `f311eba test: pin conformance baseline gaps` and `d17c28b fix: close managed conformance gaps`. Net scope is seven files, `105 insertions(+), 27 deletions(-)`.
- Current state: CI workflow lint is green after the final conformance environment change. ShellCheck and actionlint emitted no findings; zizmor reported `No findings to report. Good job! (9 suppressed)` in offline mode.
- Current state: CI branch is clean at local commit `1e74199 ci: add static and conformance gates`; it changes only `.github/workflows/ci.yml` and strict dependency-lock mode in `build.gradle.kts`.
- Current state: base SIGTERM readiness remains separate at `a693ef4`; issue #8 remains separate at `de14645`, `ba91eaf`, and `19b205e`. Nothing is pushed or published.
- Blocker: exact combined-head Java, fuzz, stress, jcstress, benchmark, and final conformance validation still need a disposable integration branch containing the three prerequisites. The individual PRs must not absorb one another's non-base commits.
- Next action: create a disposable integration worktree from `origin/master`, apply the base SIGTERM, conformance, and CI commits in dependency order, run the exact CI task sequence and full managed conformance, then audit all four PR drafts for user approval before any push.

## Checkpoint 17

- Current state: disposable `agent/integration-validation` contains only the base SIGTERM readiness, managed conformance repair, and CI commits in dependency order; it is not a delivery branch and will not be published.
- Exact combined-head Gradle sequence completed successfully through `clean check`, `fuzzTest`, `schedulerStress`, `jcstress`, and `jmh`. The terminal raw output retained in-session includes `schedulerStress` success, jcstress `56 planned; 56 passed`, and JMH `BUILD SUCCESSFUL`.
- Current state: `installDist` completed successfully at the same combined head. A Windows-to-WSL quoting error prevented the first two profile-generation attempts from creating artifacts; those attempts did not run conformance and are not test evidence.
- Current state: the target profile was then generated with the conformance checkout's absolute WSL fixture path, and the exact full managed `admission or conformance` command is currently running with strict markers, unexpected-skip failure, JUnit output, admission evidence, and the CI-only JVM startup environment.
- Next action: wait for the managed suite's terminal pytest summary, inspect its JUnit/admission artifacts and repository cleanliness, then compose issue #8 over the validated prerequisite head in a second disposable worktree and run its exact CI gates.

## Checkpoint 18

- Current finding: the first corrected full combined-head managed run completed with one failure: `startup_repair_anon6.yaml::invalid_pending_values_are_removed` did not observe `shazam` at its fixed 750 ms log boundary. Endpoint: `1 failed, 9478 passed, 2132 skipped in 561.38s`.
- Current finding: canonical admission passed in that full run. The preserved evidence records all four probes passed for outbound networking, connectable listener, ephemeral listen, and disabled number promotion; JUnit records 11,611 tests, one failure, zero errors, and 2,132 skips.
- Observation: the Anon6 case is a fixed-time startup observation, not a transport assertion. It sleeps 750 ms, then requires `shazam` in the new process log and `Anon6.db.new` with zero pending-finalization values. The failed full-run log slice was empty at that boundary.
- Diagnostic correction: an initial 20-run direct script used invalid port zero, so all processes exited code 2; those results are invalid and were not used. After allocating valid ephemeral ports, 20 of 20 fresh direct Anon6 boots had both `shazam` and a nonempty checkpoint at the 750 ms boundary and exited code 0.
- Current finding: the exact managed focused selection containing capability admission plus Anon6 completed green: `2 passed, 11609 deselected, 1 warning in 197.23s`. The warning is pytest's existing xunit2 `record_property` warning.
- Current state: no product, conformance test, or CI contract was changed in response. The disposable integration branch remains unpublished; generated evidence and diagnostic script remain untracked only in that worktree.
- Blocker: the combined head still lacks a green terminal result from one uninterrupted exact full managed selection because the first full run encountered the isolated 750 ms Anon6 miss.
- Next action: repeat the exact full strict managed selection once after the 20 direct and focused managed passes. If green, inspect artifacts and compose issue #8 over the prerequisite head; if Anon6 misses again, treat the fixed startup boundary as a CI reliability defect and improve product startup determinism without modifying the external test.

## Checkpoint 19

- Current finding: the repeated uninterrupted combined-prerequisite full managed run is green: `9479 passed, 2132 skipped, 1 warning in 562.25s`. Its JUnit records 11,611 tests, zero failures, zero errors, and 2,132 skips; all four canonical admission probes passed.
- Current state: disposable `agent/issue-8-integration-validation` was created at exact prerequisite head `8f2ad73`. The preserved OpenRewrite and issue red commits applied cleanly; the issue green commit had one `BuiltinCatalog` conflict with the conformance task-permission repair.
- Conflict resolution: `set_task_perms` retains the conformance rule that only self or a wizard may select another programmer, while returning sealed `BuiltinResult.ErrorResult`/`Programmer` effects. No compatibility accessors were introduced.
- TDD integration finding: the first composed `clean check` failed at `compileTestJava` because the conformance permission test still invoked removed `programmer()` and zero-argument `error()` optional accessors. Endpoint: three compile errors and `BUILD FAILED in 2m 39s`.
- TDD integration green: the test now asserts `BuiltinResult.Programmer` and `BuiltinResult.ErrorResult` record values directly. `compileTestJava` completed with `BUILD SUCCESSFUL in 1m 23s`; repair is separate local commit `cccc11f test: migrate task permission result assertions`.
- Exact issue-head CI Java endpoints: `clean check` succeeded in 2m 5s; `fuzzTest` succeeded in 26s after 14,843 runs; `schedulerStress` succeeded in 14s; jcstress reported 56 planned and 56 passed with no failed/soft/hard errors and succeeded in 38s; JMH succeeded in 11s.
- Observation: `gradle/verification-metadata.xml` is CRLF-tracked on the base branch, so ordinary `git diff --check` flags every newly generated dependency line. Preserve its established bytes and use a targeted whitespace check excluding that legacy CRLF file; do not normalize it broadly.
- Current state: nothing has been pushed or published. The issue validation branch is disposable and includes the prerequisite commits plus four issue-layer commits.
- Next action: run the exact full strict managed conformance selection against the issue head, inspect JUnit/admission and Git state, then perform issue architecture/diff/commit audits and prepare exact stacked PR titles/bodies for user approval before any push.

## Checkpoint 20

- Exact issue-head managed conformance is green: `9479 passed, 2132 skipped, 1 warning in 594.70s`. JUnit records 11,611 tests, zero failures, zero errors, and 2,132 skips; all four canonical admission probes passed.
- Literal issue audit: `BuiltinResult` is a top-level sealed interface with exactly 21 permitted record variants; its reflection test freezes every variant name and record-component list. Repository search reports no legacy `BuiltinCatalog.Result`, `InitializeRequest`, nested `Result` interface, or record matches.
- Literal issue audit: `MooVm.applyBuiltinResult` is an exhaustive type-pattern switch over all 21 variants and has no `default`; the compiler therefore enforces case coverage. Catalog migration contains 421 result construction/factory sites, and 978 `BuiltinResult` references span the production builtin/runtime/VM consumers.
- Mechanism audit: `rewrite.yml` applies `org.openrewrite.java.ChangeType` from `moo.builtin.BuiltinCatalog.Result` to `moo.builtin.BuiltinResult`; `scripts/rewrite.sh` provides dry-run/apply modes. Java compilation remains `-Xlint:all -Werror` with Error Prone and NullAway.
- Barn comparison: local Barn CI uses formatting, `go vet`, staticcheck, build/test, and the full strict managed conformance selection. Banteng now includes the analogous language-native strict compiler/Error Prone/NullAway gate plus ShellCheck, CodeQL quality/security queries, strict dependency locks, dependency review, fuzz/stress/jcstress/JMH, and full managed conformance.
- CI improvement: added separate `workflow-lint` job with fully pinned `actions/setup-go` v7.0.0 and existing pinned setup-uv. It runs actionlint v1.7.12 and zizmor v1.29.0 offline against the workflow.
- Local workflow-lint evidence: actionlint exited 0 after selecting Go 1.25.13; zizmor reported `No findings to report. Good job! (9 suppressed)`. The CI-only addition is separate commit `0eefc9a ci: lint GitHub Actions workflows` and has been applied to both disposable compositions as `5f833d1` and `705c9f3`.
- Current state: no push, PR, or tracker mutation has occurred. Source CI branch now has two clean local commits; issue validation branch has the prerequisite stack, four issue-layer commits, and the final CI-lint commit.
- Blocker: workflow lint must be rerun at the two new exact heads, generated validation artifacts must be separated from tracked cleanliness, and PR stack topology/diffs/closing phrase/title/body drafts need final audit and user approval before publication.
- Next action: rerun actionlint/zizmor on both exact compositions, run targeted whitespace/status/commit topology audits, prepare scoped stacked delivery branches if needed, then present exact push branches and PR title/body text for user review without publishing anything.

## Checkpoint 21

- User explicitly approved publication of the exact four branch targets and PR title/body drafts with `publish`; no merge was authorized.
- Final delivery topology was built locally and proven byte-identical to the validated compositions: conformance `de4e3dd` equals validated `979e860`, CI `3c5b634` equals validated `5f833d1`, and issue #8 `e57c874` equals validated `705c9f3`; every comparison had matching tree hashes and zero differing files.
- Final scoped diff audit: SIGTERM is one commit/two files; conformance is two commits/seven files; CI is two commits/two files; issue #8 is four commits/22 files. Normal whitespace checks pass for the first three; the issue targeted check excluding only base-established CRLF `gradle/verification-metadata.xml` passes.
- Current GitHub baseline before publication: no existing PRs, no remote branch collisions, and `master` has no branch protection rules. Authenticated account is `ctoth`; remote is `MongooseMoo/banteng` over SSH.
- Published exact branches: `agent/install-sigterm-before-bind` at `a693ef4`, `agent/conformance-baseline-stack` at `de4e3dd`, `agent/ci-conformance-static-checks-stack` at `3c5b634`, and `agent/issue-8-builtin-result-stack` at `e57c874`.
- Opened approved draft PRs in dependency order: #29 `Handle SIGTERM before accepting connections` targets `master`; #30 `Close managed MOO conformance gaps` targets the SIGTERM branch; #31 `Add static analysis and MOO conformance CI` targets the conformance stack branch.
- Current state: issue #8 PR has not yet been opened because the 24-call checkpoint interrupted immediately after PR #31. Temporary `.codex-pr-*.md` body files are untracked only in the delivery worktree and must be removed after remote body verification.
- Next action: open the approved issue #8 draft PR targeting the CI stack with `Closes #8`, fetch and compare all four PR base/head/title/body/file/commit states, remove temporary body files, then watch exact-head CI and diagnose any failure without merging.

## Checkpoint 22

- Published approved draft PR #32 `Extract sealed builtin result effects` at exact head `e57c874`, still stacked on `agent/ci-conformance-static-checks-stack`, with the approved `Closes #8` body. Issue #8 remains open until the PR targets and merges into the default branch.
- User then explicitly authorized the normal-merge delivery sequence. PR #29 merged as two-parent commit `d11c818`; PR #30 was retargeted to `master` and merged as two-parent commit `f12ad680`; PR #31 was retargeted to `master` and merged as two-parent commit `c2374b2`. Each was audited for exact files, commits, scope, checks, closing phrase, and merge command before merge.
- Bootstrap CI run `31985832505` is pinned to exact master head `c2374b2a60fc7068356368f98c871b1537852b4c`. Workflow static analysis passed; dependency review was skipped on push; CodeQL remains in progress at this checkpoint.
- Both Gradle-dependent jobs failed before tests: `MOO conformance suite` at `Build Banteng distribution`, and `Static analysis & tests` at `Run static analysis and test gates`. Raw logs report `./gradlew: Permission denied` and exit code 126.
- Authoritative Git tree evidence is `100644 ... gradlew` on `origin/master`, while the workflow invokes `./gradlew`. The repository must record the wrapper executable bit (`100755`) for Linux CI.
- Separate workflow validation finding: `gradle/actions/setup-gradle@...` warns that `build-root-directory` is an unexpected input. The pinned v6 action does not accept that input; the job already establishes the correct Gradle root through its run working directory and wrapper discovery.
- No repair has been edited. Under the CI-fix workflow, the focused proposed repair is a dedicated branch/PR from current `origin/master` that changes only the `gradlew` mode and removes the invalid setup-gradle input, followed by exact-head CI and a normal merge only after approval and green gates.
- Next action: obtain explicit approval for that focused repair PR and its conditional normal merge, implement it without importing issue #8, watch exact-head CI, then retarget PR #32 to repaired `master` and continue its own exact-head CI/merge audit.

## Checkpoint 23

- User approved the focused CI repair PR text and then authorized the entire recovery/delivery sequence with `do it all`.
- Local CI repair branch `agent/ci-bootstrap-fix` has one clean commit, `8aff8a6 ci: make Gradle wrapper executable`. Its diff is only removal of the unsupported two-line `build-root-directory` action input plus `gradlew` mode `100644 -> 100755`. Local actionlint exited 0 and zizmor 1.29.0 reported no findings with nine suppressions. It remains unpublished.
- A pre-push freshness audit caught concurrent master update `cb33c33`, a two-parent local pull merge of issue build commit `de14645` with merged-CI head `c2374b2`, followed by a direct push under the authenticated account. That accidentally landed four issue-build files directly on `master` outside PR #32.
- Per completion guardrails, publication paused and the user authorized exact rollback first. Rollback branch `agent/revert-accidental-issue8-build` contains one commit, `10a2050`, produced by `git revert -m 2 cb33c33`. Its resulting tree is byte-identical to `c2374b2`: both tree hashes are `af19fc22327f5f4c2576b5953f4dd6a111c80084`.
- Published ready PR #34 `Revert cb33c33 from master` with the user-visible body `Revert merge cb33c33 using parent 2, restoring the pre-accident c2374b2 content.` Temporary body file was removed and remains uncommitted/unpushed.
- PR #34 exact head is `10a20508e2190601294aa92b2b9b5c816509f557`; it is mergeable. Static Java CI fails at the already-authorized bootstrap exception `./gradlew: Permission denied`, exit 126.
- PR #34 also exposed that dependency review cannot run because the repository dependency graph is disabled: action error `Dependency review is not supported on this repository` and job environment `GITHUB_DEPENDENCY_GRAPH_ENABLED: false`.
- An attempted repository PATCH to enable `security_and_analysis.dependency_graph` returned no dependency-graph field, and a follow-up GET also returned no dependency-graph field. Do not claim it enabled; diagnose API/feature availability before deciding whether the workflow should admit unsupported repositories or the setting can be enabled through another endpoint.
- Current blocker: exact rollback PR #34 must be audited and normal-merged under only the explicitly authorized wrapper-bootstrap exception. Dependency-review infrastructure state needs resolution before the CI repair can be considered green.
- Next action: inspect the completed PR #34 run and repository dependency-graph API capability, merge the exact rollback if no additional branch-related failure exists, then rebase/publish `8aff8a6`, ensure dependency review is operational, and babysit the repair PR to exact-head green.

## Checkpoint 24

- GitHub vulnerability-alerts/dependency-graph endpoint now returns HTTP 204. The failed PR #34 dependency-review job predated enablement; a failed-job rerun completed dependency review successfully in five seconds. Workflow static analysis and CodeQL also passed.
- PR #34 pre-merge audit: one revert commit, four exact rollback files, no closing phrase, mergeable, unprotected master, no repository rulesets, and tree hash `af19fc22327f5f4c2576b5953f4dd6a111c80084`, identical to `c2374b2`. Only both known `./gradlew` exit-126 jobs failed under the user's explicit recovery exception.
- PR #34 was normal-merged with exact command `gh pr merge 34 --merge`. Merge commit `69bf7214a07c6a0baf2b1da2c490b7d5302392ce` has two parents (`cb33c33`, `10a2050`) and the same restored tree hash as `c2374b2`.
- Unpublished CI repair commit was rebased onto restored master as `709e355 ci: make Gradle wrapper executable`. Its scope remained `.github/workflows/ci.yml` two deletions plus `gradlew` mode `100644 -> 100755`; actionlint and zizmor reran successfully.
- Published approved draft PR #35 `Fix Gradle wrapper execution in CI` at exact head `709e355ebc9dc8f73292af1f46611f64f345d917`. It is mergeable. Temporary PR body file was removed and remains uncommitted/unpushed.
- PR #35 proved the executable fix: both Gradle jobs advanced beyond exit 126. Both then failed deterministically during dependency verification for missing `com.google.guava:guava-parent:33.5.0-jre` POM checksum. The same root cause affected `compileJava` in Java and `installDist` in conformance.
- Detached clean-cache Gradle metadata generation completed `compileJava` successfully and, ignoring the base-established CRLF bytes, generated exactly two missing records: `guava-parent-33.5.0-jre.pom` SHA-256 `68719e687c6e4c9ff3e0fecbef7bd20896f0f4f7b314743ed33c72f962568215`, and `junit-bom-5.12.2.module` SHA-256 `de70ac5d91a52656d8890a6d23b9e04d99b99b1a0402530decad71bf31b2735e`.
- Applied only those eight XML lines to PR #35's `gradle/verification-metadata.xml`; the ordinary and EOL-insensitive diffs are both eight additions with no broad line-ending rewrite.
- A second independent fresh Gradle user home ran `compileJava` against the focused metadata patch and completed `BUILD SUCCESSFUL in 1m 19s`.
- Current state: PR #35 watcher was stopped on superseded head `709e355` before committing the focused metadata fix. The repair worktree has only the intended unstaged eight-line metadata addition.
- Next action: audit/stage/commit the metadata-only CI fix as `codex: fix CI failure on PR #35`, push, restart continuous PR #35 monitoring immediately, and diagnose any later clean-checkout gate failure without rerunning the obsolete SHA.

## Checkpoint 25

- PR #35 gained focused commit `5a70050` with only the eight independently derived Gradle verification records, then commit `dc0ec1d` adding the documented CI-only `/opt/java/25` symlink required by existing process-launch tests. No stable test or product behavior was weakened.
- At exact head `dc0ec1d`, all six PR checks completed successfully: workflow static analysis, static analysis and tests, CodeQL action, dependency review, MOO conformance suite, and GitHub CodeQL.
- PR #35 final audit found three commits, three files, 13 additions/two deletions, clean/mergeable state, no closing phrase, unprotected master, and no rulesets. It was normal-merged with `gh pr merge 35 --merge` as `733cc355fb2558a9ac1271d0b223966d741f41e9`, whose two parents are restored master `69bf721` and repair head `dc0ec1d`.
- Current issue PR #32 remains draft at exact unchanged head `e57c87411ca55df82294b0947d537b9019f1d770`, with four issue commits and 22 files. Its old stacked base is obsolete; merge-tree inspection against repaired master `733cc355` found no conflicts.
- Topology decision: retarget PR #32 directly to `master` without merge/rebase. This preserves the four issue commits while GitHub's PR merge ref incorporates all merged prerequisite and CI repairs.
- Publication text decision: remove only the stale first body line claiming the PR is stacked; retain the previously reviewed summary, verification list, and `Closes #8` phrase verbatim.
- Current state: a temporary untracked `pr32-body.md` contains the exact user-visible revised body. Task notes and the temporary body must remain uncommitted and must never be pushed.
- Next action: retarget PR #32 to master with the revised body, re-fetch exact remote state and closing issue reference, continuously watch exact-head CI, then audit and normal-merge only after all required gates succeed.

## Checkpoint 26

- PR #32 was retargeted to `master` with only the stale stacked-base sentence removed. GitHub now resolves `Closes #8` to issue #8, but initially reported a real merge conflict in `gradle/verification-metadata.xml`.
- Merged repaired master `733cc355` into the unchanged issue head with history-preserving merge commit `b5522e4a86d3f4ccd872cd6e72e9863baf70f60d`; its two parents are issue head `e57c874` and master `733cc355`.
- Conflict resolution retained the issue branch's OpenRewrite metadata and added only the independently verified master records for Guava parent 33.5.0-jre and JUnit BOM 5.12.2 module. The file parses as XML, its EOL-insensitive issue-parent diff is exactly eight additions, and WSL Java 25 `compileJava` completed successfully.
- Published exact head `b5522e4`; GitHub now reports PR #32 mergeable. Exact-head CI workflow run is `31990850928`.
- Java CI failed only `MooRuntimeTest.locallyDefinedAnonymousPropertyDoesNotJoinTheSameRecycleWave`: expected recycle observations `{1, 1}`, observed `{1, 2}`. The artifact reproduces the exact base flake previously seen on PR #35, which later passed unchanged; do not fold an unrelated runtime/test change into issue #8. One exact-SHA failed-job retry is appropriate after diagnosis.
- CodeQL analysis itself succeeded, but its PR quality check found 27 alerts in changed lines: one possible empty-pattern array bounds error, two one-use SecureRandom warnings, one unused PcreService argument, and 23 unread pattern variables. Many arose because the mechanical migration changed line attribution, but the gate requires explicit resolution rather than weakening CodeQL.
- Static triage finding: Java 25 unnamed patterns can replace the unread `ignored` bindings. Existing static `SECURE_RANDOM` can serve both one-use SecureRandom sites. `replaceAsciiIgnoringCase` needs an explicit empty-search guard/loop condition. The `cacheStats` parameter exists only because the caller passes the builtin argument list and can be removed from this private service method/call.
- Current state: PR watcher remains attached to exact head `b5522e4`; conformance is still pending, three checks pass, Java and CodeQL quality fail. No alert fix has been edited.
- Blocker: address all CodeQL findings in a focused, test-backed branch commit without unrelated behavioral changes; then push a new SHA, restart monitoring, and require every gate to succeed without dismissing alerts or weakening tests.
- Next action: inspect the existing SecureRandom field and call contracts, apply the minimal 27-alert cleanup, run focused/full local static-test gates, publish the exact commit message after user-visible disclosure, push, and restart the watcher immediately.

## Checkpoint 27

- Applied focused four-file CodeQL cleanup: 23 Java 25 unnamed patterns, shared existing `SECURE_RANDOM` at two one-shot sites, an empty-search guard in the private ASCII replacement helper, and removal of the unused private PCRE cache-stats argument. No public compatibility surface or stable test was weakened.
- WSL Java 25 `./gradlew --no-daemon clean check` completed `BUILD SUCCESSFUL in 6m 23s` with all nine tasks executed. Published cleanup commit `09a1ce1936839cf306e63d34d3939b75f80fcc52` as `fix: resolve CodeQL findings`.
- PR #32 exact-head run `31991571362` completed six passing checks: both CodeQL statuses, dependency review, managed MOO conformance, static analysis/tests, and workflow static analysis. The prior flaky Java result was not rerun because the new substantive CodeQL commit produced a new exact head; Java passed there unchanged.
- Final PR audit: ready/open, base `master`, exact head `09a1ce1`, clean/mergeable, six commits, 22 issue-scoped files, closing reference #8, six passing checks, no branch protection, and no rulesets. Exact merge command was `gh pr merge 32 --merge`.
- PR #32 normal-merged as `d50652d1ff4b643a759812e4cd37e4a5ca85b1f9`. Its two parents are previous master `733cc355fb2558a9ac1271d0b223966d741f41e9` and exact PR head `09a1ce1936839cf306e63d34d3939b75f80fcc52`; issue #8 closed automatically from the approved closing phrase.
- Exact merge-commit push run `31991892376` completed successfully: CodeQL 2m33s, static analysis/tests 4m14s, managed MOO conformance 4m36s, workflow static analysis 27s; dependency review correctly skipped on push.
- Current state: requested implementation, CI/static checking, publication, normal PR merge, issue closure, two-parent topology verification, and exact merge-head CI gate are complete. Notes remain untracked and unpushed.
- Blocker: none.
- Next action: provide the concise final handoff with raw verification output and links; do not delete or stage task notes.
