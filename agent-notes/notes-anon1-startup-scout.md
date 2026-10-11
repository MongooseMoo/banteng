# Anon1 startup scout

## Current findings

- The unchanged managed row sleeps 500 ms immediately after a no-port-wait `Popen`, then requires `Anon1.db.new` to contain `1 values pending finalization`.
- Production startup is `Banteng.call()` database load -> `MooServer.serve()` -> `MooRuntime.startServer()` -> `PublicationScheduler.submit()` -> `MooRuntime.startServerNow()` -> `startStored()` compilation -> VM/publication -> checkpoint effect.
- The investigation's direct zero-delay suspension requeue next action is stale because that attempt was subsequently tried and fully reverted.
- Earlier isolated removals of Picocli annotation work, JVM flag tuning, JFR event initialization, randomized temp-name initialization, and direct zero-delay timer startup did not make the unchanged row pass.
- The current worktree already contains a large, unrelated/uncommitted Phase 3 production slice; this scout is making no source changes.

## State and blocker

- No single remaining source change is yet justified by measured evidence as sufficient for the 500 ms deadline.
- Need to finish inspecting scheduler submission/publication and codec serialization costs, plus exact fixture/startup verb shape, to determine whether duplicated startup work remains or whether launch/class-loading dominates beyond source-removable cost.

## Next action

- Continue read-only call-chain inspection and return concise file/method evidence to the parent agent. Do not edit source, conformance, launcher, fixtures, or harness.

## 2026-07-20 Phase 4 flush-command authority audit

### Current findings

- Verified `/root/src/toaststunt` is clean at pinned SHA `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- The exact YAML row sets `hold-input` to `1` and `flush-command` to `.flush`, sends `auditflush first`, `auditflush second`, then `.FlUsH`, and expects the two held lines listed FIFO between the flush header and done footer. After releasing hold, a later bare `auditflush` must leave `audit_flush_seen == {""}`.
- `new_input_task()` compares the complete received line to `flush_cmd` with `strcasecmp()` before classification/enqueue; a match calls `flush_input(..., 1)` and returns, so `.FlUsH` is neither queued nor executed.
- Held ordinary in-band lines are rear-enqueued FIFO and are not activated while `hold_input` is true. The ready loop permits only OOB input while held and not servicing `read()`.
- `flush_input()` repeatedly dequeues `DQ_FIRST`, subtracts each line length from queue accounting, notifies the player with exact header / `>>     <line>` / footer formatting, and frees each task. It affects the input queue, not background/waiting tasks.
- `set_connection_option` requires the target to be the programmer or the programmer to be a wizard; it also requires a live, non-disconnecting connection handle. The YAML uses the wizard and a logged-in live player.

### State and blocker

- Read-only authority inspection is complete enough to answer the requested trigger, ordering, queue, output, player/connection, and permission semantics. No blocker.
- No source, fixture, YAML, Barn, Git index, commit, or remote state was changed. This checkpoint note is intentionally uncommitted.

### Next action

- Return the exact source-function and line evidence to the parent agent, distinguishing the direct flush trigger from later normal command parsing.

## 2026-07-20 task-id suspend/fork authority audit

### Current findings

- Verified pinned Toast SHA `aecc51e9449c6e7c95272f0f044b5ba38948459e`; the Toast checkout is tracked-clean.
- Inspected only `audit_background_task_id_stable_across_suspend_and_fork` in `task_scheduling_toast_oracle.yaml` (lines 250-297). The row is valid: its named zero-delay fork returns the exact generated child task ID into `heartbeat_id`, and the child sees that same ID through both zero-delay suspensions.
- `task_id` is registered with arity 0 and returns `current_task_id` as an integer. It has no builtin-specific permission check or native error path; generic argument validation rejects extra arguments.
- Named `fork` allocates one nonzero random task ID, stores it in the parent variable before copying the child environment, and queues the child with that same ID. Fork delay type, negative delay, and task quota failures produce `E_TYPE`, `E_INVARG`, and `E_QUOTA` respectively.
- Suspension serializes the running VM with `current_task_id`; resumption restores `current_task_id` from that VM. No new task ID is allocated during suspend/resume.
- The nested zero-delay fork is enqueued before the parent enqueues its second suspension. Waiting tasks preserve this order even on equal timestamps, and the programmer background queue is FIFO, so the nested child increments the fork counter before the parent resumes and records `{same_id, forks}`.
- `new_task_id()` rejects zero but does not collision-check against live IDs. This does not invalidate the row because it never asserts that the unnamed nested child's ID differs from the named child's ID.

### State and blocker

- Read-only authority inspection is complete for the sole active row. No blocker.
- No source, fixture, YAML, Barn, Git index, commit, or remote state was changed. This checkpoint note is intentionally uncommitted.

### Next action

- Return the exact registration, binding, VM restoration, ordering, error/permission, and row-validity evidence to the parent agent without inspecting later rows.

## 2026-07-20 input-vs-ready-background fairness authority audit

### Current findings

- Inspected only `audit_input_admitted_between_ready_background_tasks` beginning at line 326 of the pinned conformance YAML; the next row was not printed or inspected.
- The setup raises `$server_options.bg_ticks` to 10,000,000 and `bg_seconds` to 30, calls `load_server_options()`, then queues six zero-delay forks. Each fork increments `#0.audit_input_fairness_started` before a one-million-iteration loop.
- After 25 ms, a separate wizard input returns whether `audit_input_fairness_started < 6`; expected value is `1`. The exact observable is therefore admission/execution of fresh input before all six already-ready background tasks have begun, not completion ordering or a latency bound for each background task.
- Pinned Toast is at `aecc51e9449c6e7c95272f0f044b5ba38948459e`. The conformance checkout is at `a9688861f26565bdcc5a623301aff0921678c2f1`; both have pre-existing untracked files but no tracked changes were reported.
- Initial source search located `enqueue_input_task()` at `src/tasks.cc:1075`, `run_ready_tasks()` at `src/tasks.cc:1629`, its server-loop caller at `src/server.cc:856`, and background tick option use at `src/execute.cc:3082`.

### State and blocker

- Exact source-path inspection and a single-row Toast result remain unfinished.
- `uv run moo-conformance --help` did not reach the CLI because uv failed to remove the existing Windows `.venv/lib64` path with access denied. No fallback command has been substituted.
- No source, fixture, YAML, Git index, commit, or remote state was changed. This checkpoint note is intentionally uncommitted.

### Next action

- Inspect only the located Toast queue-admission and server-loop code, determine the fixture's minimal scheduling requirement, and identify an exact existing single-row runner command without mutating the environment or inspecting later rows.

### Additional exact authority evidence

- `src/server.cc:799-856` computes the next task wakeup, calls `network_process_io(useconds_left)`, and only then calls `run_ready_tasks()` once per server-loop iteration.
- `src/tasks.cc:1433-1454` makes `next_task_start()` return zero whenever any active tqueue has input or a background task, so pending background work causes nonblocking network polling rather than blocking input admission.
- `src/tasks.cc:1629-1809` moves every due waiting fork into its programmer tqueue, but its `did_one` control executes at most one runnable task per invocation. Within a tqueue it always tries `dequeue_input_task()` before `dequeue_bg_task()`.
- `src/tasks.cc:1075-1124` rear-enqueues new input and calls `ensure_usage()` when input is runnable. `src/tasks.cc:505-526` keeps the background queue FIFO.
- Together these paths establish the intended fairness mechanism: between background-task dispatches, Toast polls the network; once the fresh wizard input is admitted to the same tqueue, it is selected ahead of the remaining background queue.
- The option override only prevents each million-iteration background loop from hitting the normal background tick/time limits: `src/execute.cc:3068-3087` chooses `bg_seconds` and `bg_ticks` for non-foreground execution. It is not itself the fairness mechanism.

### Updated state and blocker

- No Toast process named `moo` was found by `pgrep -a moo`; no exact live instance was identified for the single-row run. Numerous unrelated listening ports exist and were not probed or treated as substitutes.
- The checkout contains `.venv-wsl` and `.venv-win`; exact usability has not yet been checked. Windows `uv run` remains blocked by the existing `.venv/lib64` access error.

### Updated next action

- Check only the existing WSL virtual environment and exact pytest node-id selection path. If no pinned Toast instance is available, report source-proven validity separately from the unavailable live result rather than substituting another server.

## 2026-07-20 unrelated-player read permission authority audit

### Current findings

- Inspected only `audit_read_unrelated_player_denied` at `task_scheduling_toast_oracle.yaml:380-389`; it captures the Wizard player object, then expects Programmer's `read(wizard, 1)` to raise `E_PERM`.
- Pinned Toast SHA remains `aecc51e9449c6e7c95272f0f044b5ba38948459e`. The conformance checkout is currently `3db3c88c417caa8bd35ef2e4abf46dd454d442b5`; neither checkout has tracked diffs.
- `src/execute.cc:3780` registers `read` for 0-2 arguments with argument 1 `TYPE_OBJ` and argument 2 `TYPE_ANY`. Generic dispatch raises `E_ARGS` outside the arity and `E_TYPE` for a non-object first argument (`src/functions.cc:242-274`).
- `src/execute.cc:3581-3615` treats the second argument by truthiness. With an explicit object, a non-wizard is allowed only when the object is valid and its database owner equals the task programmer. Programmer does not own the captured Wizard object, so the function returns `E_PERM` before nonblocking connection access.
- The denied row therefore does not call `read_input_now()`, dequeue input, test connection availability, or suspend its task. `nonblocking=1` does not alter permission precedence.
- If permission succeeds, nonblocking `read_input_now()` returns `E_INVARG` for no tqueue or disconnected-and-empty input, integer `0` when no in-band input is queued, or consumes and returns one in-band string (`src/tasks.cc:977-979`, `525-601`, `1341-1359`). Blocking mode instead suspends and installs a reading VM, with `E_INVARG` for no tqueue, an existing reader, or disconnected-and-empty input (`src/tasks.cc:1363-1379`).
- Exact selected-row WSL run against managed pinned Toast passed: 23 collected, 22 deselected, 1 selected; `1 passed, 22 deselected in 5.77s`, exit 0.

### State and blocker

- Audit is complete for the sole row. No blocker.
- No source, fixture, YAML, Git index, commit, or remote state was changed. This checkpoint note is intentionally uncommitted.

### Next action

- Report the exact signature, permission/error precedence, connection/task boundary, no-effect denial, and live selected-row result to the parent without inspecting later rows.

## 2026-07-20 forked nonblocking read-empty authority audit

### Current findings

- Inspected only `audit_read_nonblocking_from_fork_returns_zero` at `task_scheduling_toast_oracle.yaml:391-409`; no later row was printed.
- The Wizard input creates `#0.audit_read_error`, queues one zero-delay fork that assigns `read(player, 1)` to the property, then itself `suspend(0)` and returns the property. Cleanup deletes the property.
- The fork copies the current activation and runtime environment, preserving Wizard as both task programmer and `player`; fork execution is a background task (`src/tasks.cc:1207-1292`, `src/execute.cc:3374-3384`).
- Explicit `read(player, 1)` passes because its programmer is a wizard. Truthy argument 2 selects nonblocking `read_input_now()` (`src/execute.cc:3581-3615`).
- The Wizard tqueue remains connected from the harness input, so it is not out-of-input. With the current command already dequeued and no later harness command sent before this step returns, no in-band input is pending; `read_input_now()` returns integer zero without suspension (`src/tasks.cc:977-979`, `1341-1359`).
- Fork is enqueued before parent suspension. Both use zero-delay timestamps; strict-less waiting insertion preserves the earlier fork first, and same-programmer background FIFO dispatch runs the property assignment before parent resumption (`src/tasks.cc:1179-1232`, `1295-1320`, `1629-1809`).

### State and blocker

- Source authority is complete; exact selected-row managed Toast execution remains to run. No blocker.
- No source, fixture, YAML, Git index, commit, or remote state was changed. This checkpoint note is intentionally uncommitted.

### Next action

- Run only the exact selected row against pinned WSL Toast, then report its task/player/connection state, permission, empty-queue return, property visibility, timing, and result.

## 2026-07-20 PublicationScheduler last-input regression diagnosis

### Current findings

- Current tracked diff changes five files: `BuiltinCatalog.java`, `MooRuntime.java`, `PublicationScheduler.java`, `BuiltinCatalogTest.java`, and `MooRuntimeTest.java`.
- The last-input slice adds mutable `ConnectionState.lastInputTaskId`, assigns it in `executeLineNow(..., taskId)`, copies/compares it in attempt session state, and passes `start.taskId()` from `PublicationScheduler.executeSegment()` into every runtime ingress/continuation execution.
- `PublicationSchedulerTest` has five conflict-scenario tests whose two concurrent LINE attempts intentionally begin from the same committed state and write the same world property. The later ticket must conflict at publication, retry under its original task/ticket identity, and then commit against the earlier publication.
- Active L1 hypothesis: the scheduler reuses each entry's original task ID on retry while the newly published connection snapshot contains the earlier input task's `lastInputTaskId`. The later retry assigns its own task ID during `executeLineNow`, making connection session state differ from the current published snapshot before the intended world-conflict retry can converge. This converts or repeats a session conflict around a piece of task-dispatch bookkeeping that should not own world publication.

### State and blocker

- Diagnosis is not yet complete: exact `AttemptContext` capture, `sessionsAreCurrent()`, `publishAttempt()`, and ConflictScenario connection ownership must be read to prove whether the last-input field causes perpetual session retry or a world conflict at the wrong boundary.
- No source or test file was edited. This checkpoint note is intentionally uncommitted.

### Next action

- Inspect only the ConflictScenario body and MooRuntime attempt/session snapshot publication methods, then identify the smallest direct Toast-faithful ownership move that keeps per-connection last-input authorization without participating in attempt conflict validation.
