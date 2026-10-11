# Phase 2 persistence/world inventory

- State: implementation is complete and ready to commit. The exact transaction/property WSL gate passes; the separate `WorldAccessArchitectureTest` gate passes; `git diff --check` is clean. The finite JetCheck domain now runs 63 generated cases without exhaustion.
- Findings/observations:
  - The selected external `Test.db` exists, is LambdaMOO v4, is 2035 bytes, and matches plan SHA-256 `1a3f23ebb549e02ccf5341668425118fcdc935b977096add87bc2a8ef29d408e`.
  - `LambdaMooV4Reader` is the only database codec. It requires zero clocks, queued tasks, and suspended tasks, and returns `WorldTxn`.
  - No v17 reader, v17 writer, checkpoint writer, atomic replacement, database restart, task snapshot, or snapshot reload production implementation exists.
  - `Banteng.--checkpoint` is explicitly unused. `CheckpointEvent` and the runtime/world JFR event classes are definitions only; production never emits them.
  - `World`, `WorldObject`, `WorldProperty`, and `WorldVerb` are immutable records/snapshots. `WorldTxn`, however, owns one mutable `World` field and publishes every mutation immediately through `replaceWorld`; it has no fixed snapshot, revision, read/write/predicate sets, validation, conflict, commit, or rollback.
  - `MooRuntime` is synchronized and executes a local serialized runnable/timed/future loop. It passes the single shared `WorldTxn` directly into the VM and applies connection/effect requests immediately. There is no production executor, ticket queue, ordered publication, retry, or effect journal.
  - `VmState` holds a live `CompletableFuture` and `processCpuAnchorNanos`; no separate durable snapshot type exists.
  - Missing named Phase 2 tests: `V17RoundTripTest`, `WorldTxnTest`, `WorldTxnPropertyTest`, `WorldAccessArchitectureTest`, `DurableTaskStateArchitectureTest`, `VmSnapshotTest`, and `PublicationSchedulerTest`.
- Blocker: none.
- Next action: stage only `src/main/java/moo/world/**` and the three named `src/test/java/moo/world/*Test.java` files, verify the staged path list, commit the kept slice, and report the commit plus the exact API runtime must consume.

## 2026-07-18 Phase 2 builtin-manifest read-only inventory

- State: read-only trace in progress; no production or manifest files changed. The committed conformance row is `dc913a9` and declares `requires.builtins: [dump_database]` while exercising Wizard login, two arithmetic returns, `dump_database()`, managed restart, reconnect, and a final arithmetic return.
- Findings/observations: the plan defines the Phase 2 manifest entry for `dump_database` exactly and says Phase 3 expands handlers used by its suites while Phase 5 completes canonical coverage. The current Banteng stock target manifest points at v4 `Test.db`, has `login_commands: []`, and selects the whole conformance tree. The harness defaults Wizard permission to raw `connect Wizard`, sends each run as `; <code>`, and reconnects the same user after restart. Candidate direct Test.db-path builtin names still being verified are `length`, `create`, `set_player_flag`, `move`, `switch_player`, `set_task_perms`, `notify`, `toliteral`, `eval`, and `dump_database`; the managed dump rows may additionally require `function_info` and `typeof`.
- Blocker: no external blocker yet. Exact completeness cannot be claimed until the stored Test.db login/eval verb sources and Banteng runtime continuation path are fully traced; runtime-dependent branches may require running the committed row.
- Next action: finish the exact stored-verb and production runtime trace, distinguish directly invoked builtins from parser/opcode behavior, and report the minimal exact name set with file-and-line evidence and any run-only uncertainty.

## 2026-07-18 post-ae03f40 checkpoint/restart read-only inventory

- State: source inventory at `ae03f40` is in progress. No source, test, plan, manifest, or Git state has been changed by this task.
- Findings/observations:
  - The earlier top-level inventory above is stale after `a360c86`, `359b401`, and `ae03f40`: immutable/versioned `WorldTxn`, `VmSnapshot`, and `PublicationScheduler` now exist.
  - `WorldTxn.snapshot()` exposes an immutable `WorldSnapshot(revision, players, objects)`, and `VmState.snapshot()` / `VmState.restore()` round-trip explicit value-only VM state including frames, limits, pending outcomes, forks, suspensions, and checkpoint requests.
  - `dump_database()` currently returns `0` plus a value-only `CheckpointRequest`; `MooVm` stages it in `VmState`. `MooRuntime.publishVmState()` does not drain or act on checkpoint requests, so the request has no production consumer.
  - `PublicationScheduler` owns ready/completed queues, task IDs, tickets, ingress futures, fork ancestry/output bookkeeping, and suspended-work/timer scheduling. Those task structures are private and expose no durable snapshot/restore or checkpoint-quiescence API.
  - `Banteng.--checkpoint` remains explicitly unused; `--database` always loads with `LambdaMooV4Reader`; `MooServer` receives only a `WorldTxn`. There is no v17 reader/writer, checkpoint aggregate, atomic file replacement implementation, or restart loader path.
- Blocker: no external blocker; the missing work is direct integration and persistence code.
- Next action: finish tracing scheduler/runtime ownership and tests, then report precise missing integration points and the smallest direct checkpoint/restart implementation sequence to the parent agent.

### Publication-order and durable-task detail

- `dump_database` is `DEFERRED_COMMIT`, not `IRREVOCABLE`. The worker invokes it immediately, advances the VM, and stores an empty `CheckpointRequest` in the VM snapshot. Only `IRREVOCABLE` builtins yield for authorization.
- A checkpoint cannot be written directly from `MooRuntime.publishVmState()`: that method runs before `WorldTxn.commit()`, and its attempt may still lose validation and retry. The request must first move into attempt-local deferred state so conflicts discard it, then be consumed only after the owning ticket commits.
- The scheduler currently has no stable checkpoint cut. `ready` and `completed` are private, dispatched entries are not tracked in an in-flight map, and delayed/suspended work exists only in virtual-thread closures. `Entry` does carry the durable segment-start `BytecodeProgram`, `VmSnapshot`, task/player IDs, and optional runtime continuation, but there is no public/package snapshot or restore API.
- Runtime ownership is separate: `MooRuntime` owns published connection/session state and attempt-local copies/effects/spawned steps. Those are session/socket concerns rather than durable queued-task ownership. The scheduler owns task IDs, publication tickets, forks, suspension scheduling, and root/child bookkeeping.
- Restart composition is absent end to end: no v17 codec or `V17RoundTripTest`; v4 loading explicitly rejects a v17 header and requires zero queued/suspended tasks; `Banteng` never uses `--checkpoint`; `MooServer` and `MooRuntime` receive no checkpoint path or restored task state; no atomic-move code exists; `CheckpointEvent` is never emitted.
- Smallest safe direction: carry checkpoint requests in `AttemptContext`; after successful ticket commit and materialization of its spawned/fork/boundary state, capture one committed `WorldSnapshot` plus scheduler-owned value-only task entries; synchronously write deterministic v17 to a sibling temp, force, close, then atomic replace with no fallback. Startup must select v4 versus v17, pass restored tasks and counters into the same scheduler, and use the configured checkpoint target for subsequent dumps.
