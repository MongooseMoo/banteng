# Immutable activation player and timeout stack

## Goal

Preserve the player saved when an activation starts independently of its mutable `player` local, and use the existing stack projection for timeout handler payloads. This is the next bounded callback-conformance repair; publication remains with the parent.

## State

Approved implementation complete. The authored ActivationPlayerTest passed after completed source compiled with explicit JDK 25 and unchanged source through completion. Isolated branch `campaign/activation-player` starts from current `campaign/callback-command-locals`, including startup and builtin-continuation repairs. Parent owns integration and publication.

## Source evidence

- Toast `src/execute.cc:765–770` copies the caller's mutable player only when its programmer is wizard and the local is an object; otherwise it uses saved `CALLER_ACTIV.player`. The new activation saves that selected player. `make_stack_list` uses saved players for actual and synthetic rows at `480–483` and `502–505`.
- Toast eval uses saved caller player for both its environment and activation at `execute.cc:3399–3413`. Fork copies the activation separately from its environment at `tasks.cc:1258–1291`; a mutable local therefore must not replace its saved player.
- Current `MooVm.java:66–124` owns verb entry, but nested entry copies mutable slot 5 unconditionally, and PASS preserves that local without the wizard/type restriction. Eval repeats the unconditional copy at `651–669`.
- `VmState.java:183–207` owns frame construction; Frame has no saved player. `callers()` uses mutable slot 5 at `236`; snapshot and restore copy frame metadata at `533–580`. ForkRequest/continueAfterFork at `315–349` and `781–799` carry locals without saved player. `PublicationScheduler.java:620–629` creates the child origin from those locals.
- `VmSnapshot.java:407–481`, `TaskRegistry.java:361–368`, and `ErrorOps.java:194–211` project players from mutable locals. Synthetic builtin rows use that same mutable source.
- Existing `SuspendedActivation.taskPlayer` already represents a saved per-activation player. `DurableTaskCodec.java:95–117` currently omits it when importing a frame; export at `882–945` substitutes the task-wide argument for every activation. No format addition is needed.
- Current external fixture `builtins/builtin_callback_context.yaml:69,85–86` mutates a wizard driver's local, expects its own stack row to retain original player, and the initialize activation and synthetic row to use the alternate player. Read via `git show origin/main` in the conformance repository.
- `server/error_callback_context.yaml:123–164` has real forked ticks/seconds timeout producers and requires a positive source line in the actual frame. `MooRuntime.java:940–966` instead builds a separate stack with line zero.
- Toast timeout payload at `execute.cc:593–595` calls its common projection with lines enabled, runtime variables controlled by the existing server option, and viewer NOTHING. Current `TaskRegistry.projectStackFrames` at `324–337` already supplies source lines, synthetic builtin rows, optional variables, and anonymous-reference projection.

## Smallest plan and ownership

1. Add a saved `long player` to existing runtime Frame and snapshot Frame; carry it through existing FrameOrigin, snapshot/restore, size accounting, and durable activation import/export. Use saved player for actual and synthetic stack rows in VmState, VmSnapshot, ErrorOps, and TaskRegistry.
2. At existing verb-entry owner, select player using the real WorldTxn wizard check and ObjectValue type; apply this to PASS as well as ordinary/builtin/protected calls. Pass the already-available WorldTxn through actual call sites in MooVm, MoveOps, IndexOps, and MooRuntime. Eval always uses saved caller player.
3. Carry saved player in existing TaskOrigin/ForkRequest/Fork metadata and the scheduler's child-origin construction, preserving mutable fork locals. Existing ordinary TaskOrigin construction should infer its saved initial player from initial locals; an explicit canonical record component permits fork entry to retain its saved value. This avoids rewriting roughly 198 existing origin call sites merely to restate the same initial value. No new frame kind, service, persistence format, or protocol.
4. Replace timeout's duplicate nonempty stack loop with `TaskRegistry.projectStackFrames(..., true, includeVariables, context.world, -1)`. The lag worker confirmed their direct `include_rt_vars` read convention; timeout uses that same existing options-property convention without a helper. Preserve the existing empty-frame fallback; do not invent a source line for it.

Expected production change: roughly 120–180 lines across TaskOrigin, VmState, VmSnapshot, MooVm, MoveOps, IndexOps, DurableTaskCodec, ErrorOps, TaskRegistry, PublicationScheduler, and MooRuntime; constructor adaptation in existing tests as necessary. Focused authored test class approximately 180–250 lines: wizard object/nonobject and nonwizard propagation, PASS, eval, fork distinction, snapshot/durable saved-player round trip, actual/synthetic callers/traceback/task-stack rows, and timeout positive-line projection. Existing command-local and WAIF expressions remain outside this repair.

## Decisions and why

Reuse the existing per-activation durable field and existing shared projection. Player is activation metadata even when local assignments change; retaining it only in a runtime display would fail resume, restart, eval, and fork cases already present in the source.

## Surprises

Eval intentionally differs from ordinary verb calls: it uses saved player even for a wizard with a typed alternate player local. Fork must preserve both values independently. Neither can be fixed by replacing stack-row expressions alone.

A fork's initial snapshot has no frame yet. Added saved initial player to the existing VmSnapshot record as well as Frame so pre-first-instruction restore and queued dump export retain it. Queued restore reads the already-existing persisted taskPlayer field. Removed now-unused task-wide player arguments from both durable export entry points; they write the actual saved origin/activation values.

The first focused invocation compiled completed production source successfully, then failed compileTestJava because the authored timeout assertion named nonexistent Outcome.TICKS_EXHAUSTED. The actual enum uses ABORTED for ticks; corrected that assertion after the invocation completed. Source remained frozen throughout compilation.

The second invocation found the authored timeout fixture dereferenced a nullable args map lookup. Corrected it with the normal nonnull assertion. Toast execute.cc:3172–3175 passes saved root activation player to error/timeout handlers; tasks.cc:1258–1291 and execute.cc:3376–3385 preserve the fork activation as root. Therefore newly forked SuspendedWork uses fork.player rather than the outer task-wide player. Added the real nested-call/fork producer case, where outer saved player, fork saved player, and fork mutable local all differ.

The next invocation compiled and executed the authored cases; initialize errored before suspend. The fixture's target inherited include_rt_vars from its ancestor but omitted the required inherited property slot. PropertyLayoutEngine:99–103 rejects that incomplete direct-parent layout and create returns E_INVARG. Corrected the authored fixture's inherited slot; production remains unchanged by this fixture repair.

Production uses eleven existing source owners, roughly eighty inserted lines with roughly seventy removed. Existing tests receive explicit constructor/signature adaptations. The single authored ActivationPlayerTest covers actual ordinary/PASS producers, wizard/type restrictions, eval, initialize actual/synthetic stack rows, changed locals, supported durable dump/import/resume, caught tracebacks/callers, fork snapshots before their first frame, queued export, and actual timeout payload projection with positive source line and configured runtime variables.

## Next step

Parent integrates this local commit, preserves constructor additions while integrating independent lag/connection changes, and reads required PR CI. Exact successful validation: explicit WSL `JAVA_HOME=/opt/java/25` and `PATH=/opt/java/25/bin:/usr/bin:/bin`; `./gradlew :test --tests world.mongoose.banteng.runtime.ActivationPlayerTest`. No broad local CI, push, or publication. Separate next assigned repair covers root WAIF local names and nested recycle caller receiver, outside this commit.

## Draft PR description

Keep each activation's saved player separate from assignments to its player local. Verb entry applies Toast's wizard/object propagation rule, eval retains the saved caller player, forks retain their own activation player, and snapshots plus existing durable player fields preserve that metadata across suspension and restart. Callers, tracebacks, task stacks, and timeout stacks now display the saved value; timeout payloads reuse existing source-line and optional-variable projection.

Validation: authored ActivationPlayerTest via explicit root :test selector with JDK 25; git diff --check. Broader acceptance belongs to PR CI.
