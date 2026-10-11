# Row 34 Toast authority subagent

- Scope: read-only trace for `audit_command_dispatch_ignores_execute_flag` in pinned Toast source `/root/src/toaststunt` at identity `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Observed: no source inspection, oracle run, edit, Git action, or Ward action has occurred yet.
- Tried: nothing yet; this file was required by the checkpoint before continuing.
- Worked: task scope and pinned source identity are available from the parent assignment.
- Did not work: not applicable yet.
- Blocker: none currently.
- Next step: read the applicable reverse-engineering skill instructions, verify the pinned source identity read-only, then trace command ingress through verb matching and invocation with special attention to the verb execute permission flag.

## Checkpoint after identity verification

- Observed: `/root/src/toaststunt` is exactly at `aecc51e9449c6e7c95272f0f044b5ba38948459e` (`git rev-parse HEAD`).
- Tried: read the RE skill and verified source identity.
- Worked: source identity matches the assignment, so source citations from this tree are authoritative for the pinned target.
- Did not work: the RE skill requires per-function `NOTES.md` edits, which conflicts with the parent assignment's read-only/no-notes boundary. I will use its evidence discipline without further note edits except when a higher-priority checkpoint mandates them.
- Blocker: none for the source trace.
- Next step: locate the conformance row's command setup and then trace the corresponding Toast command ingress, verb matching, permission checks, and invocation path.

## Checkpoint during semantic trace

- Observed: `server_receive_line()` in `src/server.cc:1534-1541` forwards the received line to `new_input_task()`.
- Observed: `run_ready_tasks()` in `src/tasks.cc:1683-1770` dispatches an ordinary in-band input task to `do_command_task()` for a connected player.
- Observed: `do_command_task()` parses the command, offers it to the handler's `do_command`, then searches player, location, direct object, and indirect object with `find_verb_on()` (`src/tasks.cc:812-871`).
- Observed: `find_verb_on()` calls `db_find_command_verb()` (`src/tasks.cc:751-767`).
- Observed: `db_find_command_verb()` matches verb name and argument/preposition specs only; it never reads `v->perms` except for argument bits, and it never tests `VF_EXEC` (`src/db_verbs.cc:302-343`).
- Observed: the separate callable lookup helper `find_verbdef_by_name()` explicitly gates on `VF_EXEC` when `check_x_bit` is true (`src/db_verbs.cc:227-238`), establishing that execute-bit enforcement belongs to callable lookup, not command lookup.
- Observed: once a command verb handle is found, `do_input_task()` loads the program and invokes `do_task()`; it reads only `VF_DEBUG`, not `VF_EXEC` (`src/execute.cc:3339-3372`).
- Tried: exact symbol searches and line-numbered reads of `server.cc`, `tasks.cc`, `db_verbs.cc`, and `execute.cc`.
- Worked: the semantic path already proves the conformance expectation: ordinary command dispatch ignores the execute flag.
- Did not work: no failed source inspection; no oracle was run by assignment.
- Blocker: none.
- Next step: inspect the queue creation functions and the callable lookup implementation/header contract to complete exact ingress and contrast citations, then report directly to the parent.

## Completed Toast source trace

- Source identity: `/root/src/toaststunt` is pinned at `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Permission encoding: `VF_EXEC` is bit `04` (`src/include/db.h:486-491`); verb-info parsing maps `x`/`X` to `VF_EXEC` (`src/verbs.cc:92-105`), and verb-info serialization emits `x` for that bit (`src/verbs.cc:298-313`).
- Network ingress: `server_receive_line()` updates activity and calls `new_input_task()` (`src/server.cc:1534-1541`).
- Input queue: `new_input_task()` calls `enqueue_input_task()` (`src/tasks.cc:1170-1180`); ordinary nonbinary, nontelnet, non-OOB input becomes `TASK_INBAND` (`src/tasks.cc:1074-1095`).
- Scheduler dispatch: `run_ready_tasks()` dequeues input (`src/tasks.cc:1648-1683`) and routes connected-player `TASK_INBAND` input to `do_command_task()` (`src/tasks.cc:1691-1769`).
- Command parsing/search: `do_command_task()` parses the command, allows the connection handler's `do_command`, then searches player, location, direct object, and indirect object via `find_verb_on()`; a hit goes to `do_input_task()` (`src/tasks.cc:812-871`).
- Command lookup: `find_verb_on()` calls `db_find_command_verb()` (`src/tasks.cc:751-767`). `db_find_command_verb()` checks name, direct-object spec, preposition spec, and indirect-object spec; it does not check `VF_EXEC` (`src/db_verbs.cc:302-343`). The public contract likewise lists only those matching criteria (`src/include/db.h:564-579`).
- Invocation: `do_input_task()` loads the matched program, builds the activation, reads `VF_DEBUG` only, and calls `do_task()` (`src/execute.cc:3339-3372`). There is no execute-bit gate between the command lookup and invocation.
- Callable contrast: `find_verbdef_by_name()` tests `VF_EXEC` when `check_x_bit` is set (`src/db_verbs.cc:227-238`); `find_callable_verbdef()` calls it with `1` on the starting object and ancestors (`src/db_verbs.cc:473-524`); `db_find_callable_verb()` uses that helper (`src/db_verbs.cc:526-668`). The header explicitly says callable lookup requires `VF_EXEC` (`src/include/db.h:581-590`).
- Semantic conclusion: Toast intentionally ignores the verb execute flag for ordinary parsed command dispatch. Clearing `x` must not prevent the command verb from matching or executing. The execute bit governs programmatic/callable verb lookup instead.
- Tried: exact line-numbered source reads across `server.cc`, `tasks.cc`, `db_verbs.cc`, `execute.cc`, `verbs.cc`, and `include/db.h`.
- Worked: the full source path answers the row without an oracle run.
- Did not work: no failed inspection and no source ambiguity.
- Blocker: none.
- Next step: hand the exact citations and semantic conclusion to the parent; make no repository, oracle, Git, Ward, or production changes.
