# Row 32 Toast authority: `audit_intrinsic_command_table_roundtrip`

Pinned source: `/root/src/toaststunt` at `aecc51e9449c6e7c95272f0f044b5ba38948459e` (verified with `git rev-parse HEAD`). Source trace only; no oracle run.

## Row contract

`C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:772-802` reads `connection_options(player, "intrinsic-commands")`, narrows it with `set_connection_option(player, "intrinsic-commands", {"PREFIX", "SUFFIX"})`, reads it, restores with integer `1`, and reads it again. Expected values are the five canonical names, the two-name canonical subset, and the same five canonical names.

## Public builtins

- `/root/src/toaststunt/src/server.cc:2974-2995`, `bf_set_connection_option`: takes `(conn, option, value)`. It checks owner/wizard permission first, so a non-owner/non-wizard gets `E_PERM` even if the connection is absent. After permission succeeds, a missing/disconnecting connection, unknown option, or option-specific invalid value gets `E_INVARG`. It tries server, task, then network option owners; `intrinsic-commands` is accepted by the task owner.
- `/root/src/toaststunt/src/server.cc:2998-3029`, `bf_connection_options`: with an option-name argument, returns that option's bare value. It checks connection validity before permission, so a missing/disconnecting connection gives `E_INVARG` even to a non-owner; a valid other user's connection gives a non-wizard `E_PERM`; an unknown option gives `E_INVARG`. Without a name, it returns all `{name, value}` pairs from server, task, then network owners.
- `/root/src/toaststunt/src/server.cc:3296-3299`: registrations are `set_connection_option(OBJ, STR, ANY)` and `connection_options(OBJ [, STR])`.
- `/root/src/toaststunt/src/functions.cc:219-275`, `call_bi_func`: registration count/type mismatches raise `E_ARGS`/`E_TYPE` before entering the builtin.

## Connection-option plumbing

- `/root/src/toaststunt/src/include/server.h:296-318,345-372`: shared connection-option macros implement get/set/list. Option-name matching uses `strcasecmp`, so `intrinsic-commands` itself is ASCII-case-insensitive. Listing all options emits canonical source-spelled names.
- `/root/src/toaststunt/src/tasks.cc:1015-1070`: task option table declares `intrinsic-commands` as a returned `TYPE_LIST`; get materializes `icmd_list(tq->icmds)`, set calls `icmd_set_flags`, and invalid input makes this owner return false so the public builtin raises `E_INVARG`.

## Intrinsic table and list setter/getter

- `/root/src/toaststunt/src/tasks.cc:114-123`: mask bits are `SUFFIX=1`, `OUTPUTSUFFIX=2`, `OUTPUTPREFIX=3`, `PREFIX=4`, `.program=5`; `ICMD_ALL_CMDS` sets all five bits.
- `/root/src/toaststunt/src/tasks.cc:263-318`: the canonical iteration and getter order is exactly `.program`, `PREFIX`, `SUFFIX`, `OUTPUTPREFIX`, `OUTPUTSUFFIX`, independent of bit-number order and setter list order. Returned spelling/case is canonical. `.program` matching uses `verbcasecmp(".pr*ogram", value)`; delimiter names use exact `strcmp` against uppercase canonical names.
- `/root/src/toaststunt/src/utils.cc:77-110`, `verbcasecmp`: case-insensitive abbreviation matching means `.pr`, `.pro`, `.prog`, `.progr`, `.progra`, and `.program`, in any ASCII case, select the `.program` bit. `.p`, longer strings, and unrelated strings do not.
- `/root/src/toaststunt/src/tasks.cc:321-345`, `icmd_set_flags`: integer inputs use truthiness (`0` => empty mask; any nonzero integer => all five). A list starts an empty candidate mask, requires every element to be a string and every string to map to an intrinsic, ORs duplicates, and assigns only after full validation. Thus empty list is accepted, duplicates collapse, output is canonical-order rather than input-order, and a bad element/name rejects atomically without changing the old mask. Any non-integer, non-list value (including float `1.0`) is rejected.
- `/root/src/toaststunt/src/utils.cc:380-400`, `is_true`: integer truth is nonzero, establishing integer `1` as restore-all and all other nonzero integers as equivalent.
- `/root/src/toaststunt/docs/Legacy/ChangeLogs/ChangeLog-LambdaMOO.txt:2410-2422` documents the externally intended five-name list and truthy/falsy numeric enable/disable model. At the pinned revision, executable source is narrower than that historical wording: `icmd_set_flags` accepts `TYPE_INT`, not `TYPE_FLOAT`.

## Command dispatch and state

- `/root/src/toaststunt/src/tasks.cc:770-797`, `do_intrinsic_command`: parses the command verb, resolves its intrinsic index, and handles it only when the corresponding `tq->icmds` bit is enabled. `.program` additionally falls through to normal command dispatch for non-programmers. Prefix variants set the output prefix; suffix variants set the output suffix.
- `/root/src/toaststunt/src/tasks.cc:812-858`, `do_command_task`: calls `do_intrinsic_command` before `do_command` and normal verb/huh resolution. A disabled/rejected intrinsic therefore follows the ordinary command path.
- `/root/src/toaststunt/src/tasks.cc:431-475`, `find_tqueue`: the mask lives in `tqueue::icmds` and is initialized to all five only when a new task queue is allocated.
- `/root/src/toaststunt/src/tasks.cc:986-1013`, `new_task_queue`/`free_task_queue`: connection establishment reuses or creates the player's task queue; disconnect only marks it unconnected.
- `/root/src/toaststunt/src/tasks.cc:1816-1822`, `run_ready_tasks`: an unconnected task queue is freed when it has no input and no background tasks. The mask is in-memory task-queue state and is not among the task data written by `write_task_queue` (`tasks.cc:1924-2005`). It can survive a disconnect only while that same task queue remains alive; a newly allocated queue resets to all five.

## Current row assessment

The row's expected three values agree with pinned Toast source. It observes canonical default/order/case, list narrowing, and integer-`1` restore. It does not observe error cases, case/abbreviation acceptance, duplicate/input-order normalization, atomic rejection, command-path consequences, or cross-disconnect lifetime.

Semantic owner: the task queue (`tqueue::icmds`, `icmd_set_flags`, `icmd_list`, and `do_intrinsic_command`) in `/root/src/toaststunt/src/tasks.cc`. `/root/src/toaststunt/src/server.cc` owns public authorization/error routing; `/root/src/toaststunt/src/include/server.h` owns generic option-name matching.

## Lifecycle confirmation

- `/root/src/toaststunt/src/server.cc:1802-1864`, `write_active_connections`/`read_active_connections`: checkpoint serialization stores only each connection's player and listener object IDs. It does not store `tqueue::icmds` or any task/network connection option, confirming the mask is not checkpoint-persistent.
- `/root/src/toaststunt/src/tasks.cc:900-959`, login completion: the live negative-ID connection task queue is relabeled to the logged-in player, so its `icmds` state travels with that physical connection. If an old task queue already exists for the player, only its queued/background tasks are handled; its option mask is not copied into the new connection queue.
- `/root/src/toaststunt/src/tasks.cc:2950-3005`, `bf_switch_player`: likewise relabels the current connection's task queue and does not replace its `icmds` from a destination player's old queue.
- `/root/src/toaststunt/src/server.cc:1657-1772`, `player_connected`/`player_switched`: relabel the matching live server handle and redirect/close any pre-existing destination connection. This confirms the state follows the surviving current connection/task queue, not the player object as durable world state.
- `/root/src/toaststunt/src/server.cc:1455-1509`, `server_new_connection`: every new physical inbound or outbound connection receives a fresh decreasing negative connection object ID and calls `new_task_queue` for that unique ID. Therefore a genuine reconnect starts with `ICMD_ALL_CMDS`; it does not reuse a prior logged-in player's old option mask.

## Unresolved questions

None for the row's three returned values or the requested source semantics. No manual oracle was needed.
