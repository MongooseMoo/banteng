# Latest MOO conformance

## Goal

Make Banteng pass the latest MOO conformance tests under its existing managed CI profiles.

## State

PR #140 is open. The complete stock run reduced failures from 232 to 219, then 155, then 78. The fifth run passed all other jobs and reported 78 failures, 12,565 passes and 1,831 declared skips. Its stock failure skipped promotion. GitHub canceled the fourth conformance job when the fifth commit reached the PR.

The sixth batch covers input and Telnet buffer bounds, cached output capacity, separate try grammar forms, `is_member()`, PCRE2 counted quantifiers, the anonymous reference in a permanent object's `last_move`, and eleven verified integer-or-boolean builtin signatures. The existing Java assertions track the Toast diagnostics, ordering and call contracts. A clean Linux distribution build and Java test compilation both passed for this batch.

The sixth managed run completed with 50 failures and all four admission probes passed. Its Java report identified two fixtures that use the combined `except` and `finally` grammar. The fixtures now use nested statements and retain their disassembly and round-trip checks. The seventh batch reuses the computed-selector and checkpoint work from the original checkout, selecting only relevant changes. It fixes first-argument and first-splice quota checks, uses the existing formatters for verb-code float display and timeout traceback output, registers SQLite's regex function with the existing pattern translator, and reduces large string copies. The final focused test run passed. Hosted stock reported 32 failures, 12,986 passes, and 1,456 declared skips. Promotion was skipped after the stock failure. Hosted Java reported one architecture violation in the new unparser dependency.

The eighth batch supplies the float formatter from the verb builtin, accounts for decoded binary notification bytes and no-flush queue limits, keeps raw carriage returns in stored property names, preserves and reports pre-existing reserved definitions while blocking new definitions, and sets SQLite connection defaults for SQL text and function arguments. The complete patch passed a clean `installDist compileTestJava` and is on PR #140. The hosted architecture check passed. `WorldResultTest` rejected the added public boolean method in `WorldTxn`. Stock finished with 19 failures, 12,999 passes, and 1,456 declared skips. Promotion was skipped after the stock failure.

The ninth batch is on PR #140. It moves the pure reserved-name lookup to `WorldProperty`, accepts empty source when exporting a compiled queued fork, and preserves the VM's enclosing handler phase when exporting and importing suspended catch and except bodies. The empty-fork and catch-handler regressions reproduced the original failures and passed with the fixes. The empty-string comparison fast path made the exact shared argument-list setup pass within its three-second socket timeout in a focused diagnostic test. Hosted CI passed the four other jobs and reported 16 stock failures, 13,002 passes and 1,456 declared skips. All three checkpoint cases passed. Stock failure prevented promotion.

The tenth batch is on PR #140. It applies `boot_player()` through the scheduler's existing synchronous host path, advances past a non-debug list quota error, checks exhaustion before running any charged instruction, handles empty SQLite statements before JDBC registers a null statement pointer, and times out an active outbound connect when its socket timeout or `SIGALRM` fires. Before the fixes, focused tests reproduced the non-debug operand-stack underflow and the duplicate JDBC statement pointer. The complete batch passed `installDist compileTestJava` and the edited `MooVmTest`. Hosted stock reported 10 failures, 13,008 passes, and 1,456 declared skips. All four admission probes passed. Stock failure prevented promotion. Hosted Java found one stale scheduling assertion. The test-only correction for `boot_player()` passed its focused test.

## Decisions and why

- Use Banteng's existing managed stock Test.db with admission and the separate promotion run. Keep the suite expectations, profiles, and strict skip handling.
- Keep the original dirty checkout separate. Publish the fixes in PR #140 and leave it open.
- Use GitHub's `conformance-results` and `java-test-reports` artifacts for verification. Let each hosted run finish before pushing the next group.

## Surprises

- Suite main now includes a pinned ToastCore database submodule. The runner prepares that database before feature checks. Initializing the submodule and recognizing a system property inside an index allows the five ToastCore cases to receive their declared stock-profile skip.
- The verified Toast v17 activation writer passes a two-word location value to one integer slot. The slot read as the debug flag contains the location type. Keep the existing nonzero interpretation for older Banteng checkpoints. Live queued forks separately preserve their actual debug state in VM snapshots.
- The latest Java tests passed after aligning older assertions with verified boolean and map ordering, parser diagnostics, integer boundaries, and suspended-only `task_stack()` visibility.
- The seventh stock report still has checkpoint failures at `dump_database()`, PCRE lookbehind differences, SQLite execution and library limits, argument-list timeouts, database property metadata, and connection output mismatches.
- The seventh run's argument-list timeouts occur in shared fixture setup before the assertions. The exact setup reproduced the timeout during `#0.arglist_parent:build()`. A worker thread dump captured `StringValue.equals` decoding and Unicode-folding a large string while comparing it to the previous empty property value. The eighth hosted run executed the argument-list assertions and found two semantic failures. For the empty-fork fixture, the compiler produces a valid program with empty source, which the queued-task exporter previously rejected.
- `ErrorOps` sets the selected expression clause to `CATCH` and leaves its enclosing handler in `TRY`. The checkpoint codec now preserves that phase in catch fallbacks and except bodies.
- The pinned Toast dispatcher aborts on tick exhaustion for charged extended opcodes as well as ordinary opcodes. A different Toast checkout contained the old dispatch, so compare against the suite's pinned source.
- Banteng's outbound socket uses a blocking `Socket.connect` without the configured timeout, and queues `SIGALRM` behind the same authorized builtin. Pinned Toast installs a connect timer and raises `E_INVARG` with `Connection failure` for timeout.
- The reference build linked SQLite 3.45.1 and PCRE2 10.42. SQLite raised the minimum length limit from 1 to 30 in version 3.48. Banteng's bundled SQLite uses 30, which the ninth run confirmed. The stock assertions expect 1 and behavior below 30.

## Next step

1. Verify the corrected Java assertion in hosted CI.
2. The stock report still has six PCRE2 lookbehind failures, three SQLite failures covering native length limits and multi-statement execution, and one missing `set_property_info()` failure. Verify both managed profiles after resolving those differences.
