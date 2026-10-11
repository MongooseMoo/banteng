# Row 22 Toast authority subagent checkpoint

- Scope: read-only Toast source trace for durable lifecycle row `audit_connection_hold_and_oob_options`.
- Exact source authority requested: pinned WSL tree `/root/src/toaststunt` only.
- Required semantics: `force_input`, `set_connection_option`, option names/defaults, hold-input, disable-oob, OOB classification/dispatch, queue/release ordering, negative prelogin connection targeting/login association, and `suspend(0)` scheduling.
- Current observation: no Toast source has yet been inspected and neither managed server/oracle has been run.
- Current state: authority investigation is about to begin.
- Current blocker: none.

## Scheduling and result derivation

- `src/execute.cc:3520-3539` implements `suspend(seconds)` by returning a suspend package; zero is accepted and passed to `enqueue_suspended_task`. `src/execute.cc:221-239,330-359` snapshots the VM and marks the current task blocked after successful enqueue.
- `src/tasks.cc:1295-1321` converts `suspend(0)` into a `TASK_SUSPENDED` whose start time is current time. `src/tasks.cc:1628-1646` moves due suspended tasks into their programmer's runnable background queue on the next scheduler pass. `src/tasks.cc:1673-1799` runs one selected input or background task per pass and resumes suspended VMs through the background-task branch.
- `src/tasks.cc:382-420` keeps active queues ordered by usage; newly runnable queues enter the active set. The row's zero-duration suspensions yield to the scheduler, but they do not override `hold-input`; a held ordinary input queue remains inactive until hold is cleared.
- Exact first pair: with `hold-input = 1`, forced `auditq` is stored as `TASK_INBAND` but not activated, so the first `suspend(0)` continuation observes `{}` and `held_blocked = 1`. Setting hold to zero sees pending input and activates the queue; after the next yield, the command runs with empty `argstr`, producing `{\"\"}`, so `held_released = 1`.
- Exact middle pair: with hold set again and OOB enabled, forced `#$#audit-oob-free` is classified `TASK_OOB` and activated despite hold. The next yield dispatches it to `do_out_of_band_command` with exact `argstr`, so `oob_bypassed_hold = 1`. After `disable-oob = 1`, a second forced `#$#` line is still stored as `TASK_OOB` but is not activated under hold; the next continuation sees no OOB record, so `disabled_oob_blocked = 1`.
- Exact final value: clearing hold while `disable-oob` remains true activates the pending queue. At dequeue, `src/tasks.cc:540-545,583-592` changes `TASK_OOB` to `TASK_INBAND`; it follows normal player-command dispatch rather than `do_out_of_band_command`. Therefore the OOB audit list remains `{}`, `disabled_oob_released_as_oob = 0`, and the full result is `{1, 1, 1, 1, 0}`.
- `src/server.cc:1657-1725` confirms successful prelogin association mutates the same shandle to the returned player, sets connection time, and runs created/connected notifier behavior. This row creates a new player, so no preexisting-handle redirect is expected.
- `src/include/server.h:345-360` matches connection-option names case-insensitively. `src/include/options.h:78-97` defines textual OOB prefix `#$#` and quote prefix `#$\"`.
- `src/network.cc:385-468,515-566` also marks telnet negotiation byte sequences as transport OOB and submits them with `out_of_band = true`; ordinary completed lines are submitted false. This is a separate route from the row's forced textual prefix.
- Barn comparison received from the parent: Barn derives the same five values and has the same negative prelogin remap, hold/release, forced-prefix OOB bypass, disabled-OOB fallthrough, and zero-duration yield at the asserted surface. Barn's spec is incomplete for the combined semantics. Barn-specific differences outside this row include singular getter naming mismatch, map versus sorted-pair option listing, ignored `at_front` for ordinary live input, and transport-OOB dropping while forced-prefix OOB falls through ordinary dispatch.
- Current unresolved questions: whether the durable row intentionally leaves the final normal-command side effects unasserted; whether option state inheritance across login is an intended contract or incidental same-queue behavior; whether transport OOB under disable/hold should be conformed despite being outside this forced-input row; and whether scheduling fairness beyond the observed zero-yield ordering is required.
- Current blocker: none. The source trace is complete enough to report; only a final exact line-range inventory and concise boundary statement remain.
- Next step: read the exact durable conformance row as evidence, then trace its public Toast entry points to semantic owners without inspecting Banteng or editing tracked project state.

## Source-backed findings after first trace pass

- Durable row lines 1860-2017 create a negative prelogin connection, find its negative object in `connected_players(1)`, `force_input()` a login line, and use `suspend(0)` before testing that the returned login player is now connected. The main assertion then toggles `hold-input` and `disable-oob` around forced in-band and `#$#` OOB lines and expects `{1, 1, 1, 1, 0}`.
- `src/tasks.cc:55-64` defines `TASK_INBAND`, `TASK_OOB`, `TASK_QUOTED`, and `TASK_BINARY`. `src/tasks.cc:142-219` establishes that negative queue players are prelogin identities, successful login replaces that identity, and the task queue owns `hold_input` and `disable_oob`.
- `src/tasks.cc:431-475` initializes new queues with `hold_input = 0`, `disable_oob = 0`, and the configured default flush command (`.flush` fallback from lines 423-429).
- `src/tasks.cc:1015-1058` recognizes the task-owned connection options `flush-command`, `hold-input`, and `disable-oob`. Clearing `hold-input` reactivates a nonempty queue. `disable-oob` is booleanized with `is_true`.
- `src/tasks.cc:1074-1130` classifies `#$#` input as `TASK_OOB` when it is enqueued. `hold-input` suppresses activation for ordinary input, but an OOB task still activates while OOB handling is enabled. Forced input uses this same enqueue path.
- `src/tasks.cc:532-596` shows the decisive deferred interpretation: if `disable_oob` is set when dequeue occurs, `DQ_OOB` returns nothing and a dequeued `TASK_OOB` is converted to `TASK_INBAND`. Thus classification is stored at enqueue, but dispatch semantics depend on the option value at dequeue.
- `src/tasks.cc:1648-1811` selects only `DQ_OOB` while `hold_input && !reading`; otherwise it selects the queue head. OOB tasks call `do_out_of_band_command`; in-band tasks go to `do_command_task` for nonnegative players or `do_login_task` for negative players.
- `src/tasks.cc:969-974` dispatches an OOB task to listener handler `do_out_of_band_command` with parsed words and the exact command as `argstr`.
- `src/tasks.cc:2880-2897` implements wizard-authorized `force_input(conn, string [, at_front])` by finding/creating the target queue and calling `enqueue_input_task(... nonbinary, nontelnet)`; the textual `#$#` prefix therefore determines OOB classification.
- `src/tasks.cc:877-966` runs prelogin `do_login_command`; if its immediate result is a valid player object, it changes the same queue's `player` from the negative ID to that player and calls `player_connected(old_player, new_player, ...)`.
- `src/server.cc:933-945` resolves a connection option target by current `shandle.player`. `src/server.cc:1657-1668` changes the matching network handle from the negative ID to the logged-in player ID. `src/server.cc:2751-2778` shows `connected_players(1)` includes prelogin handles and returns their current player IDs.
- `src/server.cc:2973-2995` implements `set_connection_option`: wizard or self authorization, exact live handle lookup, then server/task/network option tables in order; unknown or disconnected targets produce `E_INVARG`.
- Neither managed Toast nor Banteng has been run. Banteng has not been inspected. No tracked project file has been edited by this subagent.
- Current unresolved work: trace exact `suspend(0)` scheduler timing, complete the `player_connected` association path, inspect option-name matching macros and OOB constants, and map the exact five-value event sequence including why the final value is `0`.
- Current blocker: none.
