# Banteng issue queue dependency checkpoint

## Current findings and observations — 2026-08-18

- Issue #3 is locally complete and independently approved for publication at tests-first commit `e7d2bb70bfe7d5d2f99c8db49352cda8b3cad4b7` plus production commit `2f08d195bb609a36d57fe64b4514a1ab48329294`; its exact PR packet is waiting for Q's approval and remains unpublished.
- Issue #25 research found two exact pinned Toast v4 fixtures: `test/Test.db` SHA-256 `1a3f23ebb549e02ccf5341668425118fcdc935b977096add87bc2a8ef29d408e` and `test/tests/Suspended.db` SHA-256 `aa515098a639fa724a0dae2cacedfb217de300a5c4aa96bcdd19713fee5e31d5` at Toast SHA `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- `Suspended.db` contains one suspended task with two v4 activations and active connection `(3,0)`; the second activation carries a persisted builtin `eval` continuation not faithfully represented/imported by current v17-nested DTOs.
- Issue #25 cannot be implemented honestly before issue #19 moves checkpoint/task DTOs out of `LambdaMooV17Codec` and defines the format-neutral codec/composition boundary. The issue #25 worktree remains untouched at base `4880f025b2e396548bce7cbeb93a670a46b4f2e6`.
- Issue #19 itself literally requires `BuiltinHosts`, which is owned by open issue #9. Issue #9 overlaps issue #3 in `BuiltinCatalog`, so implementation should wait until #3 lands to avoid unnecessary conflict.
- Issue #16 is an independent direct prerequisite for #25: shared `DbScanner`, value decoder, and record declarations must exist before expanding v4 without duplicating reader infrastructure.
- Worktree `C:\Users\Q\code\banteng-agent-issue-19` exists on `agent/issue-19-persistence-composition-root` at exact base `4880f02`; it is research-only so far.
- Worktree `C:\Users\Q\code\banteng-agent-issue-16` now exists on `agent/issue-16-shared-db-reader` at exact base `4880f02`.
- No local Gradle build or test has been run. No new branch has been pushed and no PR has been created.

## State

- Active implementation target is issue #16.
- Issue #19 architecture research continues read-only.
- Issue #25 is deliberately paused behind #16/#19 rather than receiving a v4-to-v17 compatibility shim or lossy defaults.

## Blocker

- No blocker for issue #16.
- Issue #3 publication is blocked only on Q approving the exact packet already shown.
- Issue #25 implementation is blocked on the tracked prerequisite issues, not on missing authority evidence.

## Next action

- Dispatch issue #16 tests-first implementation and independent source inventory in its isolated worktree. Preserve the issue's required OpenRewrite mechanism, do not run local builds/tests, audit the final two-commit stack, and prepare its exact PR packet without publishing before approval.

## Issue #16 and architecture refinement — 2026-08-18

- Issue #16 tests-first commit is `8b1f4868fa968ba3c883101665765af757f1610c test: define shared persistence reader contracts`; production is still uncommitted and currently consists of four new shared files under `src/main/java/moo/persistence`.
- Independent inventory found the issue's byte-identical claim is only partial. Scanner primitives and records are mostly exact/alpha-equivalent, but v17 value decoding owns identity-bearing `ReadContext`, WAIF/anonymous tables, maps, booleans, and recursive extension dispatch.
- Safe #16 boundary: shared callback-driven tags 0-4/9 core for v5/v17; v17 keeps private context and extension orchestration; v5 continues rejecting tags 10-14; v4 remains tag-1-only and must not use `ValueTagDecoder` before #25.
- The worker was directed to remove a premature `readV4ObjectReference` API/test, add v17-extension-inside-shared-recursion coverage, and add structural ownership/use contracts before committing production.
- No #16 behavior expansion for #25/#26/#28 is authorized. The issue's claimed ~400-line deletion is not evidence-backed; correctness and ownership take precedence over that estimate.
- Issue #19 research found a deeper tracker conflict. #9 is a named prerequisite for `BuiltinHosts`; #11 and #19 are mutually dependent because #11 needs runtime-owned task types from #19 while #19 needs the DurableTaskCodec/cycle break from #11.
- The constructor wording in #19 is not literally constructible today: PublicationScheduler and BuiltinCatalog host hooks require runtime callbacks before MooRuntime exists, while #19 says Banteng constructs those collaborators first and passes them into one MooRuntime constructor. A holder/supplier or compatibility bridge would be a rejected shim.
- Moving records alone also fails #19's semantic goal because scheduler import/export still depends on ToastV17ProgramLayout and wire control tags. A shared prerequisite or combined #11/#19 design will need explicit tracker clarification before implementation.

## Updated state

- #16 remains the active implementation and has no external blocker.
- #19 remains read-only; no implementation will invent a construction cycle workaround.
- #25 remains untouched behind #16 and the unresolved #11/#19 model boundary.

## Updated blocker and next action

- Immediate blocker: none for #16. Finish the corrected RED contract, shared implementation, OpenRewrite dry-run/review/apply, and structural audit without local tests.
- Later blocker: #11/#19 tracker architecture requires Q-approved clarification or a combined prerequisite design; do not publish tracker edits or implementation under Q's name before that packet is shown.
