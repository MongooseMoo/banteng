# Root WAIF names and nested recycle caller

## Goal

Repair the two verified callback-entry defects in the existing MooVm.callVerb owner, preserving ROOT/PASS and existing continuation/player plumbing.

## State

Both scoped expressions and focused regressions are complete. The authored classes and exact reported CI fixture cases passed with completed production source unchanged through compilation and execution. Isolated campaign/callback-entry starts from the completed saved-player branch. Parent owns integration, review, CI and publication.

## Smallest plan and source

- Preserve a ROOT verb's raw name in local verb. Toast server.cc:661–682 invokes deferred WAIF :recycle as a server task; execute.cc:3310–3337 copies that raw activation verb to SLOT_VERB. Nested call_verb2 instead strips the colon at execute.cc:774–780. Current MooVm.callVerb strips it for ROOT too. Add kind != ROOT to the existing condition (one line), with a root :recycle case in existing WaifCallbackNamesTest.
- Set nested recycle caller from parent.receiver, like other nested calls. Toast objects.cc:818–849 invokes call_verb for both permanent and anonymous explicit recycle; execute.cc:651–660 forwards to call_verb2, which sets SLOT_CALLER from CALLER_ACTIV._this at :750–754. Current MooVm.callVerb:94–99 copies mutable slot 6 instead. LayoutCompiler.java:418 identifies slot 6 as this, slot 7 as caller; parent was notified of that correction before source edits. Remove that recycle special branch (about four lines), with a real assignment changing parent local this and caller while its saved receiver remains distinct, for both recycle hook kinds in existing CallbackCommandLocalsTest. The latest external recycle fixture does not mutate this, so this source correction alone is not evidence that its first failing result is resolved.

No new callback owner, abstraction, protocol, flag, or format. Expected production change: two existing expressions, approximately five lines removed and two inserted. Focused cases approximately 35–55 lines across the two existing test files.

## Decisions and why

Root server task naming differs from nested WAIF dispatch; preserve that source distinction. Explicit nested recycle has the same caller receiver rule as other nested calls; delete the unsupported exception.

## Surprises

The first focused run reproduced the existing tests' missing transactions after saved-player entry began reading wizard status: WorldTxn requires begin() before reads. Corrected the direct call fixtures in CallbackCommandLocalsTest and CompiledVerbTest to own proper transactions, including the newly authored recycle case. Hosted CI also exposed PublicationSchedulerTest's reflective binding to obsolete exportActivation(Frame,long); updated the exact signature and invocation to exportActivation(Frame), without a production compatibility method. Its existing stack/continuation assertions remain intact.

CompiledVerbTest deliberately mutates local this and expected recycle to copy that value; updated that one expectation to the source-backed saved receiver, preserving the lifecycle checks. The root WAIF fixture initially inspected verb after its last read had consumed it: MooVm:275–276 delegates PUSH_CLEAR to PropertyOps:24–35, which nulls the slot. The fixture now uses verb after resume too, retaining the same raw-name assertion across suspension rather than assuming a dead local remains live.

Saved-player and continuation work are separate substantive commits. This follow-up owns the exact reported test adaptations as well as the two tiny entry expressions.

## Next step

Parent integrates this local commit and reads current PR CI; await the next assignment rather than expanding the repair. Successful frozen invocation used explicit WSL JAVA_HOME=/opt/java/25 and PATH=/opt/java/25/bin:/usr/bin:/bin. Root :test selected only authored WaifCallbackNamesTest and CallbackCommandLocalsTest, the three adapted CompiledVerbTest methods (normalAndPassKeepTheirDifferentLocalPolicies, hookOriginsKeepNamedLifecycleAndCallerPolicies, rootEntryRetainsExplicitContextLimitsAndStoredProgram), and the three reported PublicationSchedulerTest methods (roundTripsEverySupportedFinallyReasonAndRejectsFinAbort, roundTripsProtectedNestedCatchFinallyLoopAndCatchHandlerPhases, roundTripsEveryCollectionAndRangeCursorIncludingNestedLoops). git diff --check also passed. No publication or broad local CI.

## Draft PR description

Preserve raw WAIF callback names for root server entry while retaining nested colon stripping, and set nested recycle caller from the saved parent receiver. Focused regressions cover root suspend/resume and mutable this/caller locals. Adapt the existing saved-player fixtures to begun transactions and the current durable export signature. Current external explicit-recycle first-result failure is not claimed resolved by these source-backed corrections.
