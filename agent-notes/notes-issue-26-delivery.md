# Issue 26 delivery notes

## State

- Active isolated worktree: `C:\Users\Q\code\banteng-agent-issue-26`
- Branch: `agent/issue-26-v5-tail`
- Exact clean base/head: `c278f236e5c619dfc7d46540bf14fe8fa71db8fa` (`origin/master` when created)
- Live issue: `#26 Match Toast v5 property restoration, task tails, and hierarchy admission`
- No local Gradle, build, test, or OpenRewrite execution. Nothing pushed or published.

## Findings

- `LambdaMooV5Reader.read(Path)` returns only `WorldTxn`.
- It requires literal `0 clocks`, `0 queued tasks`, and `0 suspended tasks`, then EOF. Therefore it cannot restore any v5 task or active-connection tail.
- `Banteng` wraps a v5 world in `StartupState` with empty task and active-connection lists.
- `LambdaMooV17Codec` already owns reusable runtime snapshot records: `Checkpoint`, `DurableTask`, `QueuedTask`, `SuspendedTask`, and `ActiveConnection`.
- V5 property restoration currently pairs only the first `min(propertyNames, propertySlots)` entries and skips clear slots, losing inherited overrides and clear state.
- V5 topology validation currently repairs invalid hierarchy field types, cycles, and reciprocal-link errors instead of rejecting them; it also de-duplicates reference lists.
- Existing `LambdaMooV5ReaderTest` expects all Broken1-5 fixtures to be repaired and admitted, which contradicts the live issue acceptance contract.
- Pinned Toast authority is `/root/src/toaststunt` at `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Toast source routes this database family through `read_task_queue()` and then `read_active_connections()` after programs. `test/tests/Suspended.db` has one suspended task and one active connection; Broken1-5 are v5 fixtures.
- Format 5 is `DBV_NextGen`. It predates `DBV_TaskLocal`, `DBV_This`, `DBV_Anon`, `DBV_Interrupt`, and `DBV_Threaded`.
- Authority defaults for absent v5 fields are an empty task-local map, maximum stack depth `50`, and thread mode `true`. Legacy `this` is the numeric receiver and legacy verb location is the compatibility object number.
- Toast's committed `Suspended.db` is format 4, not format 5, so issue 26 needs a dedicated v5 task-tail fixture rather than relabeling or reusing that file.
- No pinned Toast test fixture currently combines a v5 header with nonzero queued/suspended tasks; a focused byte-authored v5 fixture will be needed from the documented writer/reader grammar.
- Current runtime import accepts only suspended activation language version 17 and rejects nonzero saved builtin continuations. A v5 import can normalize the version/default-only fields for the existing runtime model, but a fixture with builtin continuation state would expose a model gap shared with open issue 25/19. The issue-26 fixture should still cover complete ordinary queued and suspended records plus active connections; do not silently consume unsupported builtin state.
- The live issue text was re-read from GitHub and explicitly requires all three families: property-slot state, complete task/connection tail, and Toast-equivalent hierarchy admission with Broken1-5 fixture tests.

## Blocker / open design question

- The exact legacy v5 queued/suspended activation grammar and its lossless mapping to the existing v17 runtime task records still need to be derived from pinned Toast source. Do not invent a parallel task model or compatibility shim until this is established.
- The issue scope is broader than tails alone: property-slot restoration and Toast-equivalent hierarchy admission must also be covered to satisfy the live acceptance criteria, while avoiding v4 and v17 behavior changes.

## Next action

1. Read pinned Toast `tasks.cc`, `execute.cc`, `eval_vm.cc`, `server.cc`, and `db_file.cc` at the v5 dispatch boundary.
2. Inspect exact Broken1-5 and Suspended fixtures plus current Banteng startup/task consumers.
3. Add focused v5 RED fixture contracts first and commit tests separately without running them.
4. Implement only v5 reader/startup changes, statically audit the diff, and prepare the exact PR title/body plus scope evidence for Q before any push or PR creation.

## RED commit

- Tests-only commit: `a4c1f13 test: cover complete v5 checkpoint restoration`
- Changed only `LambdaMooV5ReaderTest` and three new test resources under `src/test/resources/moo/persistence/v5/`.
- Contracts cover Broken1 repair, Broken2-5 rejection, local/inherited/override/clear slots, one queued task, one suspended task, listener-tagged connections, and the legacy no-listener connection form.
- The v5 task fixture was authored directly from pinned Toast `read_task_queue`, `read_vm`, `read_activ`, `read_activ_as_pi`, and `read_active_connections` grammar. It uses only ordinary (non-builtin-continuation) task state and the format-5 defaults documented above.
- Commit was made without running local Gradle, builds, tests, or OpenRewrite. Worktree was clean immediately after the commit.

## Immediate production action

- Change `LambdaMooV5Reader.read` to return the existing `LambdaMooV17Codec.Checkpoint` model.
- Parse the v5-only task grammar after programs, including absent-field defaults and active-connection EOF compatibility.
- Restore all property slots by canonical ancestry and reject invalid type/cycle/reciprocity cases while repairing only missing references.
- Update only the v5 branch in `Banteng` to consume the returned checkpoint directly.

## Uncommitted production checkpoint

- `LambdaMooV5Reader.read` now returns the existing checkpoint type and parses obsolete clocks, queued tasks, suspended tasks, and both active-connection encodings.
- Format-5 defaults implemented in the reader: empty task-local map, max stack 50 when absent, legacy receiver/verb-location object values, and thread mode true.
- The reader currently rejects nonzero saved builtin continuations with the same unsupported boundary already present in the v17 codec/runtime model; no new runtime field or compatibility surface has been invented.
- Full property restoration is implemented by canonical self-then-ancestor slot layout, including inherited override metadata and clear fallback values.
- Hierarchy admission now follows Toast phase order: validate all field types first; only then repair missing references while preserving `#-1` list entries and duplicates; then reject parent/location cycles and all four reciprocity failures.
- `Banteng`'s v5 switch arm now consumes the returned checkpoint instead of manufacturing empty task/connection lists.
- Uncommitted production paths are only `src/main/java/moo/persistence/LambdaMooV5Reader.java` and `src/main/java/moo/app/Banteng.java`.
- No local build/test/OpenRewrite execution has occurred.

## Remaining decision and next action

- `PublicationScheduler` rejects suspended activation language markers other than 17. The reader currently preserves v5's marker `5`; decide from architecture evidence whether v5 import must normalize it to current runtime form or whether runtime import support is outside issue 26. Do not modify v17 codec behavior.
- Finish static compile/style/API audits, inspect the exact diff for scope, then commit production separately if coherent.

## Production commit and current base audit

- Production commit: `3a96a13 fix: restore complete v5 checkpoints`.
- Current branch is clean with exactly two commits above master: tests `a4c1f13`, then production `3a96a13`.
- A fresh `git fetch origin master` left `origin/master` unchanged at exact `c278f236e5c619dfc7d46540bf14fe8fa71db8fa`; it is an ancestor of the branch and the branch is `2 0` ahead/behind.
- Exact branch file inventory: `Banteng.java`, `LambdaMooV5Reader.java`, `LambdaMooV5ReaderTest.java`, and three `src/test/resources/moo/persistence/v5/*.db` fixtures. No v4, v17 codec, runtime, build, workflow, or conformance-repository files changed.
- Static `git diff --check` passed before both commits. No local build/test/OpenRewrite command was run.

## Literal completion blocker

- The branch parses and retains ordinary v5 queued/suspended records in the existing checkpoint records, but cannot claim every Toast suspended state.
- Pinned Toast `read_activ` reads a builtin function name and builtin-specific saved state whenever `bi_func_pc != 0`.
- Existing `SuspendedActivation` stores the counter but no builtin name/state, `LambdaMooV17Codec.readSuspendedActivation` rejects any nonzero counter, and `PublicationScheduler.importActivation` also rejects it.
- Existing runtime import additionally accepts only language version 17 and resolves counters through `ToastV17ProgramLayout`; preserving a v5 marker therefore cannot yet activate the parsed ordinary suspended task, and relabeling it as v17 would apply the wrong bytecode layout.
- Completing those cases requires runtime-owned snapshot fields plus version-correct legacy activation import, which is the open issue 19 boundary and overlaps issue 25. Do not silently broaden issue 26 into v17/runtime architecture or publish `Closes #26` while this remains unresolved.
