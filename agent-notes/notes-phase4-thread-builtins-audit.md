# Phase 4 Toast thread builtin audit

## Current findings

- `AGENTS.md` is absent from `C:\Users\Q\code\banteng`; the task-supplied instructions remain controlling.
- Phase 4 is `docs/reports/banteng-implementation-plan.md:495-624`.
- `scripts/run_managed_wsl.sh:23-37` resolves `toast stock` to `profiles/toast/stock-wsl-testdb.json` and `scripts/run_toast_wsl.sh`.
- `profiles/toast/stock-wsl-testdb.json:4-15` pins source `/root/src/toaststunt` at commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`, binary `/root/src/toaststunt/build-release/moo`, and config `/root/src/toaststunt/src/include/options.h`.
- The live WSL checkout HEAD matches the pinned commit.
- The production implementations are in `/root/src/toaststunt/src/background.cc`: pool lookup near line 114, `bf_threads` near line 274, `bf_thread_pool` near line 295, and registration near lines 356-357.
- `threads` is registered unconditionally with arity `0..0`; `thread_pool` is registered unconditionally with arity `2..3` and argument types `STR, STR, INT` (`background.cc:351-360`). The generic dispatcher raises `E_ARGS` and `E_TYPE` for arity/type mismatches (`functions.cc:239-275`).
- Both builtins require a wizard. `threads()` otherwise returns a list of integer `uint16_t` handles by iterating the background-process map (`background.cc:273-287`).
- `thread_pool()` accepts only pool `MAIN` in production code and only function `INIT`; an invalid pool raises `E_INVARG` with message `Invalid thread pool` and the pool string, an invalid function raises `E_INVARG` with `Invalid function` and the function string, and a negative size raises `E_INVARG` with `Invalid number of threads` and the integer. Valid sizes destroy the current pool, set null for zero, initialize a new pool for positive values, and return integer `1` (`background.cc:289-321`).
- Static `background.cc` state owns the one `background_pool`, `background_process_table<uint16_t, background_waiter*>`, and next handle. The waiter holds the VM, callback data, pipe, handle, and atomic active flag (`background.cc:43-45`; `src/include/background.h:15-26`).
- `options.h:515-528` pins two worker threads and default thread mode true. It does not define `BACKGROUND_TEST`. `background.cc`, `register_background`, `threads`, and `thread_pool` are unconditionally built/registered; only `background_test` is inside `#ifdef BACKGROUND_TEST` (`CMakeLists.txt:249`, `functions.cc:72`, `background.cc:326-360`).
- Relevant conformance rows are `background_threads.yaml:57-100` for `thread_pool` and `:102-142` for `threads`. They require programmer `E_PERM`; invalid pool/function/negative size `E_INVARG`; zero-disable then positive-restore returning `{1, 1}`; and, only when conditional `background_test` exists, a nonempty integer-handle list while callback completion remains pending.

## State and blocker

- The original audit remains read-only except for this explicitly authorized checkpoint note.
- No tracked file has been edited, and this note must remain untracked and unstaged.
- No current blocker after authorization to create this note.

## Next action

Finish checking exact call-shape dispatch and queued-task/thread-row semantics, verify the note is untracked and unstaged, then report the production contracts and minimum Phase 4 behavior with exact paths and lines.
