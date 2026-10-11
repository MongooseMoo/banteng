# Row 44 Barn authority: `audit_server_options_bg_seconds_runtime`

Scope: read-only authority trace. No tracked files or production code were changed. This note must remain untracked and unstaged.

## Exact conformance row and observable

`C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml:160-190`

- Setup directly defines or sets `$server_options.bg_seconds = 9`, defines `#0.audit_bg_seconds`, calls `load_server_options()`, and returns.
- A separate foreground eval executes `fork (0)`, whose child stores `seconds_left()` in `#0.audit_bg_seconds`; the parent then calls `suspend(0)` and asserts `#0.audit_bg_seconds <= 9 && #0.audit_bg_seconds > 5`.
- The precise asserted observable is boolean `1`, permitting an integer captured value in the inclusive range `6..9`. It does not assert countdown behavior, type directly, load necessity, fractional handling, or sampling during a creation/start gap.

## Normative Barn specification

- `C:/Users/Q/code/barn/spec/tasks.md:31-39`: forked and resumed tasks are background tasks, defaulting to 3 seconds; `$server_options.bg_seconds` overrides the default.
- `C:/Users/Q/code/barn/spec/tasks.md:156-168`: background default is 3 seconds and `seconds_left()` reports remaining seconds.
- `C:/Users/Q/code/barn/spec/tasks.md:178-185,215-223`: `yin()` compares against remaining seconds, and resumed tasks receive fresh background limits.
- `C:/Users/Q/code/barn/spec/server.md:141-150`: documents `bg_seconds` as `INT`, default `3`.
- The builtin specs conflict: `spec/builtins/time.md:98-104` says `seconds_left() -> INT`, while `spec/builtins/tasks.md:250-256` says `FLOAT`. Current Barn and Toast both return `INT`; the latter Barn passage is stale.

## Current Barn implementation: entry point to semantic owner

1. `builtins/system.go:609-627`, `builtinLoadServerOptions`: wizard-only entry point calls `LoadServerOptionsFromStore` and returns a count of loaded recognized properties.
2. `builtins/limits.go:22-25,35-54`: global cache initializes `bgSeconds` to `3.0`.
3. `builtins/limits.go:92-102,195-199,208-230`, `LoadServerOptionsFromStore` / `numericSeconds`: every reload starts from default `3.0`; a directly defined positive `INT` or `FLOAT` becomes the cached value; missing, wrong-type, zero, or negative values leave the default. There is no positive minimum clamp.
4. `builtins/limits.go:66-73`, `GetTaskLimits(true)`: exposes the cached background seconds.
5. `scheduler/task_runtime.go:408-419`, `drainForks`, calls `scheduler/task_factory.go:197-282`, `CreateForkedTask`, when the parent VM yields the fork.
6. `scheduler/task_factory.go:215-216`: the child snapshots cached background ticks and seconds at fork-child construction, before `QueueTask` at line 281.
7. `scheduler/task_runtime.go:94-115`: a first-run forked child uses its preconfigured VM and does not resample at execution start.
8. `scheduler/task_runtime.go:81-88`: the child's hard deadline is wall-clock `max(StartTime, time.Now()) + SecondsLimit`; for a ready zero-delay fork this is effectively actual run start plus the snapshotted limit.
9. `builtins/system.go:161-180`, `builtinSecondsLeft`, calls `task/task.go:333-337`, `Task.SecondsLeft`, casts `SecondsLimit - SecondsUsed` to `int64`, and returns an `INT`. Current Barn has no nonzero update to `SecondsUsed`; the observable is therefore the constant truncated limit. For this row, the child records exactly `9`.
10. `scheduler/eval.go:104-175`: the parent's `suspend(0)` drives ready scheduler tasks before resuming, so the child writes `#0.audit_bg_seconds` before the parent reads it.

### Barn value and timing semantics

- Default: `3.0`.
- Accepted property types: positive `INT` and positive `FLOAT`, despite the normative `INT` table.
- Wrong type, missing, zero, or negative: silently use `3.0`; current code does not raise the spec's advertised `E_INVARG`.
- Minimum: none for positive values. A value such as `0.5` is cached exactly, but `seconds_left()` truncates it to zero and then returns its fallback `1000`, an additional divergence.
- Load relevance: Barn requires `load_server_options()` to publish property changes into its cache.
- Sampling: a fresh fork snapshots `bg_seconds` at child construction/enqueue, not at child start. A cache reload between creation and first run does not affect it.
- Resumption differs: `scheduler/task_runtime.go:56-69` resamples the current background cache and resets the stored seconds limit when a suspended VM resumes.
- Deadline/observable disconnect: the hard deadline uses wall clock, while `seconds_left()` never consults that deadline and does not count down.

## Toast authority and disagreements

Toast source confirms that Barn is a divergent implementation reference, not semantic authority:

- `C:/Users/Q/src/toaststunt/src/execute.cc:3374-3384`, `do_forked_task`, marks the child background and reaches `run_interpreter` only when the queued child actually starts.
- `src/execute.cc:3055-3083`, `setup_task_execution_limits` / `run_interpreter`, samples `server_int_option("bg_seconds", DEFAULT_BG_SECONDS)` at interpreter start and clamps values below one to an effective one second.
- `src/server.cc:1461-1470`, `server_int_option`, accepts only `INT`; missing or wrong-type (including `FLOAT`) uses default `3`. Zero/negative integers reach the execution-time clamp and become `1`.
- `src/include/server.h:183-248` and `src/functions.cc:527-562`: `bg_seconds` is not a cached `load_server_options()` option. Toast reads it directly at child start, so the row's reload call is irrelevant to Toast.
- `src/execute.cc:3659-3665`, `bf_seconds_left`, returns `INT` from the active task timer.
- `src/timers.cc:181-203,218-237`: on the Linux `ITIMER_VIRTUAL` path it counts virtual CPU time and returns whole remaining seconds by dropping the fractional component; fallback builds use a wall-clock timer.

Agreements for the tested value: default documented as 3; a positive integer 9 becomes the effective background duration; `seconds_left()` is an integer; the immediate row observation lies in `6..9`.

Disagreements:

- Barn samples at fork creation; Toast samples at fork execution start.
- Barn requires reload; Toast does not for `bg_seconds`.
- Barn accepts floats; Toast accepts integers only.
- Barn defaults nonpositive values to 3 and has no positive clamp; Toast clamps integer values below one to 1.
- Barn `seconds_left()` is a constant/truncated stored limit; Toast is a live whole-second timer countdown.
- Barn uses a wall-clock context deadline checked after a VM segment; Toast's normal Linux timer is a virtual CPU-time timer integrated with interpreter timeout state.

Unknown/not verified live in this subtask: the managed Toast row was not rerun, and Barn was not executed. The conclusions above are current-source and durable-row evidence. Under Banteng's authority order, Toast plus the Toast-proven durable row are authoritative; Barn is divergent and must not define Banteng semantics where it differs.
