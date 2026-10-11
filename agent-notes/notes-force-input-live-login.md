# Phase 4 force_input live-login slice

## Exact target

- Active row: `audit_force_input_live_login_connection` in `task_scheduling_toast_oracle.yaml`.
- The row installs a `do_login_command` recorder, opens a real connection, finds its negative identity through `connected_players(1)`, forces `audit-force-login`, yields with `suspend(0)`, and requires the recorder to see `{!valid(player), args, argstr}` as `{1, {"audit-force-login"}, "audit-force-login"}`.
- Current Banteng full-family result is `20 passed, 3 failed`; this row returns `{{1, {}, ""}, 0}`.

## Current causal trace

- `MooRuntime.openConnectionNow` registers the connection and synchronously calls `executeLogin(connectionId, "")`.
- The overridden handler therefore records the blank line and returns the new player before `new_connection` completes, so the later search for an invalid connection cannot select that live connection.
- `force_input` itself already stages `ForcedInputRequest`; `MooRuntime.applyForcedInputRequests` publishes an `INPUT` effect; `publishEffect` enqueues a detached `LINE` request. The missing observation occurs before that path because the test's negative target was lost during synchronous open.
- Pinned Toast source/report authority says accepted connections queue an automatic blank input and forced input enters the same live queue; scheduler ordering, rather than synchronous execution inside accept, determines which queued input runs before the yielded audit task resumes.

## State and next action

- Git `master` is clean for tracked files at pushed commit `4e7b262`; existing notes and analysis artifacts remain untracked.
- Two read-only subagents are checking pinned Toast behavior and the Banteng connection path. Neither may edit or run Gradle.
- Next action: run the exact current row against pinned Toast, reconcile its observed queue ordering with source authority, then make only the smallest production/test change proven by that result.

## Fixture invalidity proven

- The exact managed pinned-Toast scheduling family passes all 23 rows in 35.95 seconds, but the target row can pass without exercising a live connection.
- `new_connection` permits Toast's synthetic blank input to run. The replacement login verb returns a player for that blank input, so the real connection can become positive before the row scans `connected_players(1)`.
- The scan leaves `neg = #-1` when it finds no live negative connection and never checks that failure.
- Pinned Toast `bf_force_input` calls `find_tqueue(conn, 1)`, which creates a detached queue for `#-1`; that queue executes the forced login text and produces the expected recorder value even though no network connection was targeted.
- The final assertion that `neg` is absent from `connected_players(1)` is then vacuously true.
- This means the earlier production-defect premise was wrong; no Banteng source edit is authorized for this row until the fixture is valid.

## Sibling repository state and exact correction

- `C:\Users\Q\code\moo-conformance-tests` is on `main`, tracked-clean, at `3db3c88`, and 38 commits ahead of `origin/main`; numerous existing untracked artifacts are unrelated and must remain untouched.
- Banteng and Toast both allocate real accepted connection identities from `#-2` downward, so `#-1` remains a safe not-found sentinel.
- Correct the row so the login verb returns `$nothing` for the synthetic blank input and returns the created player only for `argstr == "audit-force-login"`.
- Add an explicit assertion before `force_input` that a negative connection other than `#-1` was found.
- After the patch, rerun pinned Toast and Banteng for the exact scheduling family to prove that the valid row still passes Toast and now reaches Banteng's real live-connection path.

## Corrected-fixture results

- The fixture now returns `$nothing` for the automatic blank login, returns the created player only for `audit-force-login`, raises `E_INVARG` if the negative scan leaves the `#-1` sentinel, and requires the created player to appear in `connected_players(1)` after the forced login.
- `git diff --check` passes in `moo-conformance-tests`; the YAML file is its only tracked change.
- Corrected fixture on pinned Toast: `23 passed in 33.93s`.
- Corrected fixture on committed Banteng: `21 passed, 2 failed in 35.45s`; `audit_force_input_live_login_connection` passes.
- Therefore the fixture defect alone caused the former Banteng failure. No Banteng production source change is needed or authorized for this row.
- Remaining scheduling targets are `audit_yin_yields_when_thresholds_crossed` and `audit_resume_after_suspend_uses_background_limits`.
