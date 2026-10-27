# Switch-player and prelogin callbacks

## Goal
Repair the existing switch_player connection owner and negative prelogin close callbacks. The coordinator verified and approved the source-backed plan in the root callback-conformance note before implementation.

## State
Implemented in the isolated campaign/switch-player-callbacks tree. The authored focused runtime test file passes. No broad local CI or conformance run, no publication authorized. Coordinator owns integration and review.

## Decisions and why
Use the existing BuiltinHosts handler pattern and ConnectionIo speculative attempt so connection identity and timer changes are visible to subsequent builtins in the same task. Carry Toast's pending switch marker and its active notification phase in ConnectionState; defer notifier roots until a genuine task boundary. The existing runtime continuation advances after disconnect's first return/error/block, re-reads the player and resolves the next hook then; it clears the marker after the final hook's first boundary. Existing spawnedSteps own scheduling. Silent affects messages only. Removed the old VM result/snapshot switchedPlayer channel and made login inspect actual connection association.

Source authority: Toast tasks.cc:912-957/2957-3020 and server.cc:917-922/1544-1558/1665-1778; conformance origin/main server/connection_callback_context.yaml connected, unused, silent, prelogin switch and prelogin close cases. TaskRegistry.queuePlayers/backgroundTaskCount associate background work with its programmer; preserve that existing ownership rather than transferring background tasks to the new player.

## Surprises
publishVmState also runs at irrevocable authorization and fork boundaries; RUNNING/PENDING_BUILTIN/FORKED must retain the pending marker. The source session timer is read directly by connected_seconds, so a deferred-only VM result is insufficient.

## Next step
Coordinator integration/review, then ordinary PR CI. Preserve independent saved-player/lag fields when integrating the mechanical VmSnapshot constructor deletions. No push or publication from this task.

## First-slice lookup correction plan
The first authored run reached test execution after fixing removed-channel constructor references. All five tests failed during fixture construction: parent relation is not reciprocal for #1. The fixture added parent #0 for inherited cross-listener hooks without adding its children; fix that fixture topology before interpreting any runtime result.

Coordinator verified Toast server.cc:917-922 re-reads the current player and resolves the second hook after disconnect's first slice. Two roots resolved eagerly preserve ordinary event order but miss a disconnect hook that switches the source again or replaces the connected verb. Correct this within the existing continuation owner: one SWITCH_PLAYER_NOTIFIER_RETURN transition, using its existing flag for disconnect versus final notifier phase, and a ConnectionState switchNotifying boolean copied/compared beside the marker. Set the boolean before dispatch; leave the marker live so callback-initiated switches update it without recursive dispatch.

At disconnect's first return/error/abort boundary, re-read the actual source session/player/marker, then resolve and stage connected/reconnected using the existing root helper. At its first boundary clear marker/boolean, matching Toast's final clear after both first slices. If a hook is absent, advance that phase directly. If source was removed, stop. At first actual suspension call the same phase advance from suspendedInputOutput and return present output so the existing scheduler drops this continuation while retaining the hook's snapshot; late resume cannot repeat the phase. The completed error path must advance before resumeRuntime's existing ERRORED formatting, preserving its error output. RUNNING/PENDING_BUILTIN/FORKED do not advance. Existing ordered spawnedSteps still own scheduling; no queue/worker/protocol is added. Expected incremental production size approximately30-50 lines.

Coordinator approved and the adjustment was implemented. Focused cases prove a disconnect hook changes the source player before the second hook lookup, replacement of the connected hook, and switch within connected hook being cleared after its first slice. Error, suspend(60), suspend(0), missing hook, login and physical prelogin-close cases pass. Source remained frozen through each compilation and test run.

## Validation and corrections
Passed: `wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin ./gradlew --no-daemon :test --tests world.mongoose.banteng.runtime.SwitchPlayerCallbacksTest`.

Only that authored file ran. Existing result-shape/catalog/snapshot/restoration tests received mechanical removals of the obsolete channel; they compiled with the root test source set and were not separately run. The catalog keeps its existing invalid-argument/type/permission checks before runtime delegation.

Initial compilations found remaining removed-channel constructor arguments in scheduler restore and legacy restoration plus one test mutator call; those were removed. Fixture topology corrections required reading WorldObject's actual parents/contents/children order. Opening a connection invokes the blank login hook immediately; the test now returns zero on that initial call. A mixed login/switch assertion incorrectly expected the deferred switch hook first: Toast tasks.cc:912-957 invokes player_connected for the returned user before server.cc:917-922 processes switch markers, so the corrected assertion preserves the observed returned-user-first order. These were fixture/compiler failures, not evidence that runtime paths passed before the final run.

## Draft PR body
Apply switch_player to the requested old connection immediately within its speculative runtime attempt, including occupied-target redirects, silent messages and connected-time reset. Defer lifecycle callbacks through their first actual boundaries, preserving fresh root context and re-reading the player/verb after disconnect before starting the connected hook. Remove the obsolete login-only VM switch channel and notify physical prelogin closes with their negative identity.

Validation: the authored SwitchPlayerCallbacksTest passes through explicit root :test. Full conformance and PR CI remain coordinator-owned.
