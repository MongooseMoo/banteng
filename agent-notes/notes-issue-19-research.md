# Issue 19 architecture research checkpoint

## State

- Research target: `C:\Users\Q\code\banteng-agent-issue-19` at exact base `4880f025b2e396548bce7cbeb93a670a46b4f2e6`.
- Task remains read-only in the issue worktree. No build, test, implementation, commit, push, or publication has been performed.
- Live issues read: #19, #11, #16, #22, #25, #33.

## Findings and observations

- `Banteng` branches directly on v4/v5/v17 headers and wraps v4/v5 worlds in `LambdaMooV17Codec.Checkpoint`.
- V4 and V5 are readers only and reject non-empty task tails. V17 alone loads, atomically checkpoints, and writes panic dumps.
- The controlling implementation plan says read v4/v17 and write deterministic v17, so an interface requiring format-preserving dump/checkpoint from V4/V5 conflicts with current architecture and capability.
- V17 owns nested `Checkpoint`, `DurableTask`, `ActiveConnection`, `QueuedTask`, `SuspendedTask`, `SuspendedActivation`, and `SuspendedStackSlot`; scheduler, runtime, server, app, and tests reference them.
- Moving task types to `moo.runtime` while runtime directly depends on `moo.persistence.DbCodec` would create a forbidden package cycle. A runtime-owned checkpoint-output port implemented by `DbCodec` can invert that direction, but the issue needs to say so.
- `PublicationScheduler` has a concrete `MooRuntime` back-reference and calls many runtime methods. `MooRuntime` owns the scheduler. Therefore `Banteng` cannot literally instantiate a scheduler and pass it to a single `MooRuntime` constructor until the cycle-breaking portion of issue #11 is completed or issue #19 adopts an explicit factory/attachment design.
- `MooServer` also constructs `MooRuntime` while implementing the listener port that runtime/builtins need, so making `Banteng` the sole composition root also requires splitting socket binding from runtime injection.
- `BuiltinHosts` does not exist at the exact base. `BuiltinCatalog` has two telescoping constructor families ending in 12 host handlers, with or without `ListenerControl`.
- Current `ArchitectureTest` rejects package cycles and allows runtime -> persistence, not persistence -> runtime.
- `rewrite.yml` currently contains only `moo.builtin.ExtractBuiltinResult`; `scripts/rewrite.sh` invokes named recipes in dry-run or apply mode.

## Blocker

Issue #19 is not feasible as one narrow PR without either pre-solving part of #11 or revising its composition-root wording. Its all-codecs load/dump/checkpoint requirement also conflicts with issue #25 and the v17-only output rule in the controlling plan.

## Next action

- Finish exact line evidence for tests and rewrite recipes.
- Produce a dependency order, honest RED contract list, issue-conflict matrix, and sequential implementation ownership split for the parent agent.
