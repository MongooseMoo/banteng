# Latest MOO conformance

## Goal

Make Banteng pass the latest MOO conformance tests under its existing managed CI profiles.

## State

PR #140 is open. The complete stock run reduced failures from 232 to 219, then 155, then 78. The fifth run passed all other jobs and reported 78 failures, 12,565 passes and 1,831 declared skips. Its stock failure skipped promotion. GitHub canceled the fourth conformance job when the fifth commit reached the PR.

The sixth batch covers input and Telnet buffer bounds, cached output capacity, separate try grammar forms, `is_member()`, PCRE2 counted quantifiers, the anonymous reference in a permanent object's `last_move`, and eleven verified integer-or-boolean builtin signatures. The existing Java assertions track the Toast diagnostics, ordering and call contracts. A clean Linux distribution build and Java test compilation both passed for this batch.

The sixth managed run completed with 50 failures and all four admission probes passed. Its Java report identified two fixtures that use the combined `except` and `finally` grammar. The fixtures now use nested statements and retain their disassembly and round-trip checks. The seventh batch reuses the computed-selector and checkpoint work from the original checkout, selecting only relevant changes. It fixes first-argument and first-splice quota checks, uses the existing formatters for verb-code float display and timeout traceback output, registers SQLite's regex function with the existing pattern translator, and reduces large string copies. The final focused test run passed. Hosted stock reported 32 failures, 12,986 passes, and 1,456 declared skips. Promotion was skipped after the stock failure. Hosted Java reported one architecture violation in the new unparser dependency.

The eighth batch supplies the float formatter from the verb builtin, accounts for decoded binary notification bytes and no-flush queue limits, keeps raw carriage returns in stored property names, preserves and reports pre-existing reserved definitions while blocking new definitions, and sets SQLite connection defaults for SQL text and function arguments. The complete patch passed a clean `installDist compileTestJava`.

## Decisions and why

- Use Banteng's existing managed stock Test.db with admission and the separate promotion run. Keep the suite expectations, profiles, and strict skip handling.
- Keep the original dirty checkout separate. Publish the fixes in PR #140 and leave it open.
- Use GitHub's `conformance-results` and `java-test-reports` artifacts for verification. Let each hosted run finish before pushing the next group.

## Surprises

- Suite main now includes a pinned ToastCore database submodule. The runner prepares that database before feature checks. Initializing the submodule and recognizing a system property inside an index allows the five ToastCore cases to receive their declared stock-profile skip.
- The verified Toast v17 activation writer passes a two-word location value to one integer slot. The slot read as the debug flag contains the location type. Keep the existing nonzero interpretation for older Banteng checkpoints. Live queued forks separately preserve their actual debug state in VM snapshots.
- The latest Java tests passed after aligning older assertions with verified boolean and map ordering, parser diagnostics, integer boundaries, and suspended-only `task_stack()` visibility.
- The seventh stock report still has checkpoint failures at `dump_database()`, PCRE lookbehind differences, SQLite execution and library limits, argument-list timeouts, database property metadata, and connection output mismatches.

## Next step

1. Verify the eighth batch in hosted Java and managed stock CI.
2. Fix the remaining mismatches in their owning runtime paths and verify both managed profiles and admission.
