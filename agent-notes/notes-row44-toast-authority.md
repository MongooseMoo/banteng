# Row 44 pinned Toast authority: `audit_server_options_bg_seconds_runtime`

## Identity and durable row

- Verified command:

  ```powershell
  wsl -u root -- git -C /root/src/toaststunt rev-parse HEAD
  ```

- Result: `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Exact committed row: `C:\Users\Q\code\moo-conformance-tests\src\moo_conformance\_tests\audit\task_scheduling_toast_oracle.yaml:160-190`.
- Setup assigns `$server_options.bg_seconds = 9`, creates `#0.audit_bg_seconds`, calls `load_server_options()`, then a distinct task executes:

  ```moo
  fork (0)
    #0.audit_bg_seconds = seconds_left();
  endfork
  suspend(0);
  return #0.audit_bg_seconds <= 9 && #0.audit_bg_seconds > 5;
  ```

- Expected value is integer `1`; cleanup deletes both properties and calls `load_server_options()`.

## Exact managed command and receipt

Run from `C:\Users\Q\code\barn` exactly as recorded in `docs/reports/toast-oracle-identity-2026-07-14.md:55-72`, replacing only the selector:

```powershell
$wslIp = (wsl -d Debian -u root -e hostname -I).Trim()
uv run --project ..\moo-conformance-tests moo-conformance `
  --moo-host $wslIp `
  --server-command "wsl -d Debian -u root -e env TOAST_MOO=/root/src/toaststunt/build-release/moo bash /mnt/c/Users/Q/code/barn/scripts/run_toast_wsl.sh {db} {port}" `
  --server-db C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_db/Test.db `
  --oracle-profile-manifest C:/Users/Q/code/barn/profiles/toast/stock-wsl-testdb.json `
  --target-profile-manifest C:/Users/Q/code/barn/profiles/toast/stock-wsl-testdb.json `
  -k audit_server_options_bg_seconds_runtime
```

Result: exit code 0; `1 passed, 11518 deselected in 3.86s`.

Post-run read-only inventory:

```powershell
wsl -d Debian -u root -e bash -lc 'ps -eo pid,lstart,args | rg ''/root/src/toaststunt/build-release/moo'' || true'
```

No managed process remained. Only unrelated PID 19 from July 13 remained on `/tmp/td.db ... 9482`; it was not touched.

## Pinned source owner chain

- `src/execute.cc:2084-2111`, `OP_FORK`: validates the delay and calls `enqueue_forked_task2()`.
- `src/tasks.cc:1257-1292`, `enqueue_forked_task2()`: creates the task id, copies the runtime environment, computes the start time, and enqueues. It does not read `bg_seconds`.
- `src/tasks.cc:1207-1232`, `enqueue_forked()`, and `src/tasks.cc:1182-1205`, `enqueue_waiting()`: store the fork and put it on the background queue owned by `a.progr`. No seconds limit is sampled.
- `src/tasks.cc:1628-1645,1673-1681,1772-1783`, `run_ready_tasks()`: moves a due fork to its owner queue, dequeues it, creates `current_local`, and calls `do_forked_task()`.
- `src/execute.cc:3374-3383`, `do_forked_task()`: calls `do_task(..., 0/*bg*/, ...)`.
- `src/execute.cc:3227-3247`, `do_task()`: initializes the fork vector and enters `run_interpreter()`.
- `src/execute.cc:3076-3083`, `run_interpreter()`: only now samples uncached `server_int_option("bg_seconds", DEFAULT_BG_SECONDS)`.
- Consequence: `bg_seconds` is sampled when the forked interpreter starts after dequeue, not when `fork` is encountered or the child is queued. A property change while a fork waits would affect that fork when it eventually starts.

## Option semantics and timer ownership

- `src/server.cc:529-541`, `get_server_option()`, resolves the current `$server_options` object/property.
- `src/server.cc:1420-1427`, `server_int_option()`, accepts only `TYPE_INT`; missing or non-integer values use the supplied default.
- `src/include/options.h:127-131`: `DEFAULT_BG_SECONDS` is `3`.
- `src/execute.cc:3054-3061`, `setup_task_execution_limits()`: clamps every seconds value below 1 to 1; 9 remains 9. There is no explicit maximum clamp.
- `src/execute.cc:61-65`: `task_alarm_id` is interpreter-global state reinitialized after suspension, not part of a saved VM.
- `src/execute.cc:3110-3120`: the timer covers `run()` and is canceled when that interpreter run returns.
- `src/execute.cc:3658-3666`, `bf_seconds_left()`: returns `timer_wakeup_interval(task_alarm_id)` as a MOO integer.

## Cache and clock semantics

- `src/functions.cc:524-550`, `load_server_options()`, refreshes builtin protection and `SERVER_OPTIONS_CACHED_MISC` only.
- The complete cache list at `src/include/server.h:197-244` contains no `bg_seconds`; cached accessors are separately defined at `src/include/server.h:266-275`.
- Therefore `load_server_options()` is irrelevant to `bg_seconds`; the row would expose the same new-child limit without either reload call.
- `src/timers.cc:181-205`, `set_virtual_timer()`, uses `ITIMER_VIRTUAL` when compiled, otherwise wall clock.
- The pinned build has `build-release/config.h:65` `/* #undef IS_WSL */`. Existing compiled-object proof shows `timers.cc.o` imports `setitimer` and `getitimer`, defines `virtual_wakeup_call`, and calls both with Linux `ITIMER_VIRTUAL` value 1. The pinned executable therefore uses process virtual/user CPU time, not wall time.
- Reproducible compiled-artifact command:

  ```powershell
  wsl -u root -- bash -lc 'nm -C -u /root/src/toaststunt/build-release/CMakeFiles/moo.dir/src/timers.cc.o | sort; nm -C /root/src/toaststunt/build-release/CMakeFiles/moo.dir/src/timers.cc.o | rg ''set_virtual_timer|timer_wakeup_interval|virtual_wakeup_call|virtual_timer_available''; nm -D /root/src/toaststunt/build-release/moo | rg ''getitimer|setitimer'''
  ```

- Result includes undefined object imports `U getitimer` and `U setitimer`, compiled symbols `set_virtual_timer(...)`, `timer_wakeup_interval(int)`, `virtual_timer_available()`, local `virtual_wakeup_call(int)`, and executable imports `getitimer@GLIBC_2.2.5` plus `setitimer@GLIBC_2.2.5`.
- `src/timers.cc:137-153` arms the CPU timer; `src/timers.cc:223-230` reads it with `getitimer()` and returns only `it_value.tv_sec`, discarding microseconds.
- `seconds_left()` has no explicit one-second decrement. Its builtin-call opcode costs a MOO tick, while the seconds timer advances independently with consumed user CPU.

## Observable row result

- The live managed result proves the child stored an integer satisfying `5 < #0.audit_bg_seconds <= 9` and the parent returned 1.
- The exact stored integer is intentionally not exposed by this row and cannot be derived exactly from source because it depends on elapsed CPU time and kernel timer resolution. For a newly started 9-second timer, an immediate whole-second read is ordinarily 8, while 9 is also permitted by timer resolution; the row accepts either and modest scheduling/runtime variance down through 6.
- No tracked file was edited and no commit was made.
