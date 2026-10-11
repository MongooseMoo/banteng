# Issue 25 v4 reader survey checkpoint

## State

- Read-only survey target: `C:\Users\Q\code\banteng-agent-issue-25`.
- Exact Banteng base confirmed as `4880f025b2e396548bce7cbeb93a670a46b4f2e6`; worktree was clean when checked.
- No builds, tests, commits, pushes, or tracker mutations have been performed.

## Current findings

- `LambdaMooV4Reader` is the frozen bootstrap subset: all slots live, local-name count equals slot count, tag 1 only, every verb must have a program, and zero task tails followed by EOF.
- `LambdaMooV5Reader` already handles recycled slots, v5 value tags, clear-slot parsing, and missing program bodies, but its property restoration intentionally drops inherited/clear slots and its task tail is also zero-only.
- `LambdaMooV17Codec` owns the reusable runtime checkpoint records: `Checkpoint`, `DurableTask`, `QueuedTask`, `SuspendedTask`, suspended activations/stack slots, and `ActiveConnection`.
- Banteng commits no v4 `.db` fixture. The existing v4 test reads adjacent `moo-conformance-tests/.../_db/Test.db`. Toast commit `e8a353665a106244f5e01edb67239c90411ae584` commits `test/tests/Suspended.db` and `test/Test.db`.
- Issue 16 plans shared scanner/value-decoder/record extraction; issue 26 overlaps property and tail semantics; issue 28 owns topology validation/repair and should remain separate.
- The implementation plan and `first-server-v4-authority.md` currently scope v4 to permanent-object bootstrap and explicitly exclude task records/connections, while issue 25 requires restoring both into the runtime checkpoint model. This needs an explicit authority/plan reconciliation before calling the implementation architecturally complete.
- Toast v4 is database version enum value 4 (`DBV_BFBugFixed`). Its object reader accepts `#N recycled`, keeps singular parent/location link chains, and reads a property-slot count independent of local property-definition count. The v4 upgrade sets the slot count to local plus every ancestor definition in self-to-parent order.
- The v4 value grammar at this version includes ordinary integer/object/string/error/list/float values; tag 5 is the clear-property marker. Tags 6/7/8 are VM-only none/catch/finally states and are used in suspended-task data rather than ordinary runtime `MooValue` properties. Maps and later value types are not v4.
- `WorldProperty` already models `clear` and `defined` separately while requiring a non-null fallback value. `LambdaMooV17Codec.restoreProperties()` already maps ordered local/inherited slots to definition names and supplies direct-parent fallback values for clear slots; its version-neutral property-layout algorithm is the best existing implementation to reuse.
- Toast `Suspended.db` is a committed 252-line v4 fixture, SHA-256 `aa515098a639fa724a0dae2cacedfb217de300a5c4aa96bcdd19713fee5e31d5`. It contains 4 live objects, 3 programs, no queued tasks, one suspended task with two language-version-4 activations, and one active connection (`3 0`).
- The suspended fixture exposes a material representation mismatch: Banteng's public durable task records are nested in `LambdaMooV17Codec`, and `SuspendedActivation` expects modern typed activation fields such as a separate receiver/verb-location/thread-mode sequence. The v4 fixture's serialized activation is the older `read_vm` grammar and cannot simply be fed to `readSuspendedTask()`.

## Blocker / open questions

- Need to finish exact old `read_activ_as_pi`, `read_rt_env`, and `read_vm` field mapping and Banteng runtime restoration tracing.
- Need to determine which v4 fields can map losslessly to existing task records and which require a version-neutral extension. The current nested v17 ownership is architecturally suspect for an issue that requires a shared runtime checkpoint model.

## Next action

- Continue read-only inspection of old Toast activation/VM grammar, `Banteng` dispatch/runtime restoration, and v17 codec tests; then produce a dependency inventory and tests-first slice order.
