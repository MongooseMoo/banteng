# Login switch output regression

## Goal and state
Repair the standalone executeLine login response lost by the switch-player repair, preserving transport routing, silent flags and callback ordering. Verified diagnosis and bounded plan were reported before implementation. The nine-line existing-owner projection and one authored focused test file are complete in isolated fix/login-switch-output; final focused validation passed with source frozen. No broad local CI or publication authorized.

## Verified cause
MooRuntimeTest.java:32-33 loads the sibling moo-conformance-tests Test.db. Its #0:do_login_command program (Test.db:180-190) creates a wizard, calls switch_player(player, o), then returns without a user object; equivalent programmer/player paths are :191-205. MooRuntimeTest:210-214 uses negative connection -47 and expects executeLine to return the connected message.

ConnectionIo.switchPlayer:246-275 now applies the association and stages the connect/redirect message as RuntimeEffect.write to the correct physical connection. LoginFlow.finishLogin:153-158 sees that association and returns state.effects().output(), which is empty for this verb. MooRuntime.publishEffect:1623-1625 delivers WRITE only through listenerControl.ifPresent, so a standalone runtime with Optional.empty transport drops it. This is the source-confirmed loss; restoring the old unconditional connected message in LoginFlow would ignore silent/configured messages and mishandle target routing.

PublicationScheduler.executeSegment:543 onward keeps runtime continuation processing inside the same attempt, and its irrevocable authorization route validates/discards an uncommitted attempt rather than publishing its effects. MooRuntime.publishVmState:1079 onward is the existing per-VM effect/output owner before both terminal return and actual suspension. EffectOutbox.stageOutput:31-34 already stages runtime output without an attached transport; no new channel is needed.

## Smallest repair plan
About8-12 production lines in existing MooRuntime.publishVmState: when no transport is attached and the original task player is a negative prelogin connection identity, move staged WRITE effects addressed to that exact identity into the task's existing EffectOutbox output, removing those effects once consumed. Do this at the existing VM boundary so success/error/first-suspension response paths carry the message naturally, including internal authorization/fork iterations without duplicates. Do not consume writes to other source/destination connections or alter attached-transport publication. Keep ConnectionIo association, timing, marker and notifier continuation logic unchanged. The negative task player remains the actual incoming connection identity even after its player association changes; resolving the mutable new player instead would risk redirecting another connection's output into this response.

One authored focused test file should load the exact Test.db and assert standalone connect Wizard output; cover configured/silent/no-print messages and writes targeted at another prelogin connection using a small stored-login fixture, first suspension/late resume once-only, and attached transport receiving the write once without duplicate return output. Run only explicit root :test --tests for this file after completed source edits freeze. Do not change existing MooRuntimeTest/FinalizationControlTest or infer that GracefulShutdown owned-thread failures need separate fixes; their leak/fallout relationship remains unverified.

## Next step
Parent integrates the local substantive fix, verifies required PR CI and publishes. Do not independently change GracefulShutdown behavior from the unverified fallout hypothesis.

## Implementation and validation
MooRuntime.publishVmState projects only WRITE effects addressed to the original negative task-player connection into its existing EffectOutbox when listenerControl is empty, removing each consumed effect. Other connection writes remain addressed to their actual destinations; attached transport behavior and all ConnectionIo/LoginFlow/switch callback bodies are unchanged. The projection runs before normal terminal/suspension handling, so the existing response path carries the message once.

Passed after final source edits: `wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin ./gradlew --no-daemon :test --tests world.mongoose.banteng.runtime.LoginSwitchOutputTest`. Its first run passed; after compilation completed, removed the unused test import requested by coordinator and ran the same authored file successfully again. No failure or production edit occurred between these two passing runs. Report: build/test-results/test/TEST-world.mongoose.banteng.runtime.LoginSwitchOutputTest.xml in this worktree.

Coverage includes the exact Test.db connect Wizard verb, custom multiline connect messages, silent/no-print login, another prelogin connection's targeted writes, ftime authorization plus actual suspend(0) once-only output, and attached-transport write without duplicate return output. No existing broad test file or conformance suite was run locally. The coordinator separately reported hosted conformance passed; that is coordinator evidence, not a run performed or independently checked in this task.

## Draft PR body
Restore standalone login return output when a stored login verb authenticates through switch_player. Without an attached transport, move writes to that incoming prelogin connection into the existing task output outbox; retain targeted transport effects for other connections and keep attached-transport, silent-message and callback behavior unchanged.

Validation: authored LoginSwitchOutputTest passed through explicit root :test after final source freeze. Required full-suite CI remains PR-owned.
