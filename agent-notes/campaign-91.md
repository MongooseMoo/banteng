# Issue 91: remove unused code and type login timeout status

## Goal
Deliver issue #91's four decisions without changing conformance: remove unreachable fixtureEval and un-emitted JFR event definitions, replace login timeout's pending output sentinel with sealed status and named default, move the pure VM test entry point into src/test.

## State
Branch campaign/91-runtime-status starts at parser repair 142ea393; merged origin/master as 66c43f3 before source edits to retain issue #93 compiled architecture. Four scoped changes implemented. Focused modified JFR and promoted-semantics test methods passed before the final verified timeout error/retention patch. Parent owns publication and final-head CI.

## Decisions and why
Smallest change: delete about 120-170 unused lines; add roughly 100-160 lines of typed timeout plumbing through existing RuntimeStep, SegmentResult, ingress future and RootCompletion; relocate about 20-30 fixture lines and mechanically update 108 two-argument VM test calls. No new queue or generic completion framework. Existing submit continues to expose ordinary output lines; timeout submit reads a typed result from the same committed publication completion. Classification yields KeepWatching, Done or TimedOut(lines); user_disconnected continuation still runs before boot. Futures complete outside the scheduler monitor after commit, with cancellation/failure exceptional and shutdown timeout Done. Test fixture must carry the selected ValueSemantics into both VM and builtin hosts and reject pending irrevocable builtins as before.

## Source evidence
- Issue #91 specifies all four removals/changes.
- MooRuntime.java:1522-1610: monitor currently polls list emptiness; classifier emits pending sentinel and invokes disconnected continuation before finishLoginTimeout.
- PublicationScheduler.java:1246-1286,1374-1413,1620-1730,2150-2173,2198-2220,2368-2478,2556-2588: ingress, speculative segment, publication success/failure, shutdown and root completion.
- WorldTxn.java:323-347: locatedVerb searches every named verb and ancestors, so fixtureEval cannot recover a missed verb named eval.
- MooVm.java:100-115: pure helper builds its own world and builtin hosts using selected semantics; MooVmTest.java:3665 uses promoted numeric semantics.
- Toast src/include/options.h:248: DEFAULT_CONNECT_TIMEOUT is 300.
- rg found no production references to TaskFallbackEvent, TaskRetryEvent or WorldCommitEvent outside their declarations; JfrEventDefinitionsTest is the sole consumer.

## Surprises
None affecting scope. First source-search command used bash brace syntax in PowerShell and failed before executing; corrected to explicit paths.
I initially described diagnostic hook output as stopping the old monitor; that was wrong. The old monitor keeps watching on any nonempty list. `resumeRuntime`'s ERRORED path adds a traceback ending in `(End of traceback)`, so it keeps watching without boot. A resource-exhausted disconnect hook reaches finishLoginTimeout first, then the scheduler runs handle_task_timeout; handler output can cause one redundant post-boot poll. After compilation exited, tagged the hook-error producer KeepWatching and retained explicit TimedOut across the resource handler in Ingress only after accepted publication. The monitor now stops after the completed boot regardless of resource-handler diagnostic lines. No inferred missing-status Done fallback.

## Validation
WSL Debian/root Java25: `./gradlew --no-daemon --max-workers=2 :test --tests world.mongoose.banteng.runtime.JfrEventDefinitionsTest.recordsNamedRuntimeWorldAndCheckpointEvents --tests world.mongoose.banteng.vm.MooVmTest.promotesMixedIntegerFloatArithmeticEqualityAndOrderingWhenConfigured` completed `BUILD SUCCESSFUL in 6m 53s`. Source remained frozen throughout compilation. This verified the relocated selected-semantics setup and surviving JFR definitions. The subsequent timeout error-tagging/committed-retention patch was source-inspected but has not been compiled or tested locally; final-head CI is pending. No broad local CI or conformance run. `git diff --check` passed.

Shared PureVmFixture replaces 97 pure callers in MooVmTest and 11 in VmSnapshotTest; all five-argument production calls remain. It preserves selected semantics in both VM and builtin hosts and the irrevocable-builtin rejection. Source search finds no pending string literal, fixtureEval, or deleted event type references in runtime/JFR test.

## Next step
Commit and hand off for parent review/CI/publication. No new guard or conformance test was added. Cancellation and failures still complete exceptionally; shutdown timeout completes Done; accepted publication retains explicit status before task-timeout handler transitions, and RootCompletion completes the future outside the monitor.

PR #109 deletion follow-up on parent-integrated head 114e6d7: the JFR production template and two existing inventories still named the three deleted event classes. Removed only the WorldCommit, TaskRetry and TaskFallback template blocks and their exact entries from JfrTemplateTest and NamespaceOwnershipTest. This completes the existing deletion rather than adding a check. No Java production change, no conformance edit, and no local CI/test rerun; parent owns current-head CI and publication.

## Draft PR
Integrated issue105 nested-property-assignment repair e06c048 in merge 3acbacd with no conflicts. This branch retains World's existing integer property signature, so incoming parser tests need no typed-permission adaptation. Runtime status, JFR deletion and PureVmFixture changes are preserved. No local CI, push or publication; parent owns final verification.

Title: Remove unused runtime code and type login timeout status

Delete the unreachable fixture eval fallback and three JFR definitions with no production emitters. Carry sealed login timeout status through committed scheduler publication, name the 300-second default, and move pure VM execution setup into a shared test fixture while preserving selected value semantics.

Validation: focused surviving-JFR and promoted-numeric-semantics methods passed before the final timeout status-retention patch. Final-head PR CI pending. Conformance unchanged. Closes #91.
