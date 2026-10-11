# Row 42 Toast authority checkpoint

- Sole target: `task_scheduling_toast_oracle::audit_server_options_bg_ticks_runtime`.
- Pinned checkout `/root/src/toaststunt` was re-verified at commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- No interaction has occurred with Toast PID 19 or port 9482.
- Initial source search identifies the fork path through `src/execute.cc` `enqueue_forked_task2()`, `src/tasks.cc` fork queue structures/enqueueing and `run_ready_tasks()`, then `src/execute.cc` `do_forked_task()`.
- `run_interpreter()` selects uncached `server_int_option("bg_ticks", DEFAULT_BG_TICKS)` for non-foreground execution at `src/execute.cc:3076-3083` and initializes `ticks_remaining` with a minimum of 100 at `src/execute.cc:3054-3061`.
- Remaining work: capture exact queue/interpreter timing lines, establish `load_server_options()` irrelevance and integer/default semantics, and calculate the literal row predicate accounting for value 23456.

## Queue and sampling trace

- `src/execute.cc:2084-2111` handles `fork`, computes its delay, and calls `enqueue_forked_task2()`.
- `src/tasks.cc:1257-1292` allocates the task id, copies the runtime environment, computes the start time, and enqueues the fork. It does not read `bg_ticks`.
- `src/tasks.cc:1207-1232` records the fork activation/body/start time and places it in the waiting queue. `src/tasks.cc:1635-1645` moves due waiting tasks into the owning background queue.
- `src/tasks.cc:1673-1681,1772-1783` dequeues the background task, creates its task-local map, and calls `do_forked_task()`.
- `src/execute.cc:3374-3383` marks the fork as background by calling `do_task(..., 0/*bg*/, ...)`; only the resulting `run_interpreter()` call reads `server_int_option("bg_ticks", DEFAULT_BG_TICKS)` at `src/execute.cc:3076-3083`.
- Therefore `bg_ticks` is sampled at interpreter start after the fork becomes runnable and is dequeued, not when the parent executes `fork (0)` or when the child is put in either queue.
- `load_server_options()` at `src/functions.cc:527-550` populates only the enumerated caches in `src/include/server.h:197-244`; `bg_ticks` is absent and the interpreter uses uncached `server_int_option()`.
- `src/server.cc:1420-1427` accepts only `TYPE_INT`, otherwise returning the default `DEFAULT_BG_TICKS = 30000` from `src/include/options.h:127-128`. `src/execute.cc:3060` clamps all values below 100 to 100; 23456 remains unchanged.

## Literal probe received

The asserted task is:

```moo
fork (0)
  #0.audit_bg_ticks = ticks_left();
endfork
suspend(0);
return #0.audit_bg_ticks <= 23456 && #0.audit_bg_ticks > 23000;
```

Setup initializes `#0.audit_bg_ticks` to zero, assigns `$server_options.bg_ticks = 23456`, and calls `load_server_options()`.

- The child starts with 23456 ticks. Its `ticks_left()` builtin-call opcode consumes one tick before the builtin returns, so the value to be assigned is 23455.
- `src/code_gen.cc:838-917` shows property assignment pushes its lvalue, evaluates the RHS, then emits ticked `OP_PUT_PROP`; that property-write tick occurs after `ticks_left()` has already produced 23455, so it cannot change the stored value.
- Remaining work: capture the exact `OP_PUT_PROP` execution lines and account for the parent suspend/resumption/predicate only as needed to prove the returned assertion.

## Final accounting and causal ordering

- `double_to_start_tv()` calls `gettimeofday()` for each zero-delay enqueue at `src/tasks.cc:102-112`. The child fork is enqueued first during `OP_FORK`; the parent is enqueued later when `suspend(0)` returns a suspension package (`src/execute.cc:3520-3539`, `src/execute.cc:222-238`, `src/tasks.cc:1295-1318`).
- `enqueue_waiting()` orders by strict `<` at `src/tasks.cc:1182-1205`, so even identical timestamps retain insertion order: child before parent.
- Due waiting tasks are transferred in list order at `src/tasks.cc:1635-1645`. `enqueue_bg_task()` appends and `dequeue_bg_task()` removes from the head at `src/tasks.cc:505-527`, so the per-owner background queue is FIFO. `run_ready_tasks()` executes only one selected task per pass (`did_one`) at `src/tasks.cc:1648-1653`; the child therefore runs and writes the property before the suspended parent resumes.
- Child bytecode accounting: lvalue object/property literals are unticked; `OP_BI_FUNC_CALL` decrements 23456 to 23455 before `ticks_left()` returns it (`src/execute.cc:966-988,3668-3675`); subsequent ticked `OP_PUT_PROP` stores that already-produced RHS (`src/code_gen.cc:838-917`, `src/execute.cc:1959-2074`). Exact stored value is 23455.
- Parent resumption is explicitly background and freshly samples `bg_ticks` again at `src/execute.cc:3253-3272,3076-3083`. Its predicate reads 23455 twice. Both `23455 <= 23456` and `23455 > 23000` are true, so the asserted result is 1.
- Predicate tick accounting after parent resumption: first `OP_GET_PROP` leaves 23455, `OP_LE` leaves 23454, `OP_AND` leaves 23453, second `OP_GET_PROP` leaves 23452, and `OP_GT` leaves 23451. Those decrements affect only the resumed parent's budget, not the stored child result.
- No tracked files were edited, no commit was made, and PID 19/port 9482 was not touched.
