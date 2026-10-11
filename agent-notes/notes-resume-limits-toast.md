# Resume-after-suspend limit audit

## Current task

- Read-only Phase 4 audit of `audit_resume_after_suspend_uses_background_limits`.
- Authority is the row YAML and pinned Toast commit `aecc51e` source.
- Determine whether the fixture causally proves foreground-before and background-after tick/second budgets, including same-task option snapshot behavior.

## Current state

- The exact row is in sibling `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/task_scheduling_toast_oracle.yaml:820-857`.
- It changes `fg_ticks`, `bg_ticks`, and `bg_seconds`, calls `load_server_options()`, then reads `before_ticks`, suspends the same task, and reads `after_ticks`/`after_seconds`.
- Its pinned expected booleans are `{0, 1, 1}`: the row itself records that the foreground assertion is false while both background assertions are true.
- The current `/root/src/toastcore` worktree is commit `1887eacd591d97fdc55d258a76e2167899b1951d`, not required Toast source commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`; that commit is not present in this checkout's Git object database.
- This was not the implementation checkout: `profiles/toast/stock-wsl-toastcore.json` points to `/root/src/toaststunt`, whose HEAD is exactly `aecc51e9449c6e7c95272f0f044b5ba38948459e`; `/root/src/toastcore` is the fixture-source checkout at `1887eacd`.
- Toast `src/execute.cc:61-65` states execution-limit globals are outside the VM because they are reinitialized after suspend.
- `src/execute.cc:3055-3061` installs seconds/ticks into those globals, and `src/execute.cc:3076-3083` selects foreground or background options when each interpreter segment starts.
- A newly started server task calls `do_task(..., 1 /*fg*/)` at `src/execute.cc:3289-3336`; the current segment therefore already captured its foreground budget before the fixture body changes properties or calls `load_server_options()`.
- A resumed task always calls `run_interpreter(..., 0 /*bg*/)` at `src/execute.cc:3252-3272`; the resumed segment therefore reads the newly installed background options.
- `ticks_left()` and `seconds_left()` merely read the active segment globals at `src/execute.cc:3659-3675`; `load_server_options()` refreshes cached options but does not reset those globals (`src/functions.cc:524-562`).
- No code, fixture, or tracked file has been edited.
- No Gradle command will be run.

## Blocker

- None. The pinned implementation checkout has been found and verified.

## Next action

- Correct the fixture by splitting setup and observation into two YAML `run` steps. The first installs the three options and calls `load_server_options()`. The second begins as a new foreground task, records `before_ticks`, suspends, records the resumed background tick/second budgets, and expects `{1, 1, 1}`. Cleanup remains unchanged.

## Conclusion

- The current row is invalid for the complete stated claim because its expected `{0, 1, 1}` explicitly accepts failure of the configured foreground-budget assertion.
- It does validly demonstrate that the resumed segment uses the newly configured background tick and second budgets.
- The false foreground assertion is caused by same-task option timing: execution limits were installed at interpreter entry before the body changed `$server_options`; `load_server_options()` does not mutate the active segment's execution-limit globals.
