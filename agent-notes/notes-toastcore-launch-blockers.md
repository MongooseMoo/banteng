# ToastCore launch blockers

## Base repair merged and PR #66 rerun checkpoint 2026-08-20

### Observed

- PR #72 exact head `958574a6ef278ae9a33aab302ca669265f33c1c1`
  completed six successful checks: workflow static analysis, static analysis and
  tests, dependency review, managed MOO conformance, and both CodeQL entries.
- The final pre-merge audit confirmed base `master`, exact head, two scoped
  commits, 15 expected files, `Closes #71`, clean/mergeable state, no local
  changes, and no `git diff --check` output.
- Executed `gh pr ready 72`, re-read the successful exact-head gate, then
  executed exactly `gh pr merge 72 --merge`.
- PR #72 is merged as `6d3e175b75a22d576b55f12e441e57138f5e1ccd`.
  The merge commit has exactly two parents: prior master `7134dc0` and feature
  head `958574a`. The feature head is an ancestor of remote master.
- Issue #71 closed automatically at `2026-08-21T04:04:02Z`.

### Tried

- Merged repaired remote master into `pr/toast-connection-name-lookup` with a
  normal local merge, producing `0ca52a862a7b21da737b9dd18ddaf461f9468141`;
  no rebase or force push was used.
- The combined focused gate for DNS lookup, registry rewrite, real-socket
  no-flush buffering, closed result shape, compilation, and static subprojects
  passed with `BUILD SUCCESSFUL in 41s`.
- Pushed the normal merge commit to the existing PR #66 branch.

### Worked

- Remote branch and PR #66 now both resolve to exact head `0ca52a8`.
- PR #66's new CI run is `32445696735`. Workflow static analysis and dependency
  review passed at the latest readback; CodeQL, static/tests, and managed MOO
  conformance were still running.

### Did not work

- PR #66's old red head `bc088f6` is not valid verification evidence.
- No ready or merge action has run for PR #66.

### Current blocker

- The exact-head PR #66 managed conformance result must prove the previously
  failing network row before merge.

### Next action

- Watch run `32445696735` to terminal state. If all checks pass, re-audit PR #66
  files, feature commit plus normal base merge topology, issue #60 scope,
  `Closes #60`, and exact command `gh pr merge 66 --merge`, then ready and
  normally merge it.

## PR #72 exact-head repair checkpoint 2026-08-20

### Observed

- Draft PR #72 was created exactly as shown: `Implement no-flush notification
  buffering`, base `master`, head `fix/buffered-output`, label `bug`, and body
  ending `Closes #71`.
- Initial exact head `db1bb0adc30b080519a935b2c68890df3f599446`
  passed workflow static analysis and dependency review, but its static/test job
  failed after 2m14s. Merge was held.
- The job artifact `9433772182` contained exactly one failing XML suite:
  `BuiltinResultTest.resultHierarchyIsClosedAndEachEffectCarriesOnlyItsOwnState`.
  The expected closed variant set and component map omitted the new `Notify`
  record; the actual implementation exposed that record as intended.

### Tried

- GitHub would not provide the failed job log while the overall workflow was
  still running, so the uploaded Java test-report artifact was downloaded into
  ignored `build/ci-32444950518` and searched directly.
- Updated only `BuiltinResultTest` to register `Notify` and its exact components
  `connectionId`, `line`, `noFlush`, and `noNewline`.
- Ran the focused `BuiltinResultTest`; it passed with `BUILD SUCCESSFUL in 21s`.

### Worked

- The test-only repair is a separate commit,
  `958574a6ef278ae9a33aab302ca669265f33c1c1` (`test: register notification
  result shape`), preserving the implementation/test-fix boundary.
- The updated PR head contains exactly two commits and is mergeable while still
  draft.
- A new CI run, `32445208030`, started on exact head `958574a`; all five primary
  checks were queued or running at the last readback.

### Did not work

- Initial head `db1bb0a` is red and cannot be used as verification evidence.
- No PR ready or merge command has run.

### Current blocker

- All required checks must finish successfully on exact head `958574a` before
  PR #72 can be readied and normally merged.

### Next action

- Watch run `32445208030` to terminal state. If green, re-audit files, both
  commits, issue scope, `Closes #71`, required checks, and exact command
  `gh pr merge 72 --merge` before readying and merging.

## Buffered-output publication checkpoint 2026-08-20

### Current findings and observations

- Issue #71 now exists with title
  `Implement no-flush notify buffering and buffered_output_length`, label
  `bug`, and the exact reviewed body.
- The first issue-create command accidentally preserved doubled apostrophes in
  the body. Immediate readback caught this; `gh issue edit --body-file` replaced
  it with the exact shown body, and the subsequent readback is exact.
- The runtime keeps ordinary current-player `notify(player, line)` on the
  established response-output path. Flagged or cross-connection notifications
  use ordered commit-time transport effects, preventing conflicted attempts
  from writing prematurely.
- A staged queue-length projection makes same-task `buffered_output_length`
  reads see no-flush notifications without publishing them early.

### Tried

- A broad affected run initially found 20 `PublicationSchedulerTest` failures
  because all ordinary notifications had been moved to listener-only effects.
  That routing was corrected in implementation rather than changing the stable
  tests.
- Three representative scheduler regressions plus the real-socket and snapshot
  tests then passed together: five tests, `BUILD SUCCESSFUL in 34s`.
- The full affected selection covering `PublicationSchedulerTest`,
  `MooRuntimeTest`, `MooVmTest`, `VmSnapshotTest`, and the focused server/catalog
  contracts passed with `BUILD SUCCESSFUL in 23s`.
- Forced root compilation plus both static-check subprojects executed all ten
  tasks and passed with `BUILD SUCCESSFUL in 45s`.

### Worked

- Stable current-player output behavior is restored without weakening the new
  no-flush transport contract.
- `git diff --check` emitted no output before commit.
- The repair is one local commit, `db1bb0a` (`fix: implement no-flush
  notification buffering`), on `fix/buffered-output` based on exact merged
  master `7134dc070adbb4e28b861c644da4af1ce606fb71`.
- The commit changes 14 files with 424 insertions and 52 deletions; the larger
  shape reflects value-only VM snapshot retention, scheduler effects, transport
  buffering, and focused contract coverage.

### Did not work

- The full mixed gate still includes the four known environment-dependent
  `BuiltinCatalogTest` failures and a missing sibling conformance `Test.db` for
  the original broad `MooServerTest`; those are separate from the affected
  selection and static gates.
- The repair branch has not been pushed and no PR exists yet.

### Current blocker

- Exact PR metadata must be shown before publication, followed by exact-head CI
  and normal merge of the base repair.

### Next action

- Show the exact PR title/base/head/label/body/closing phrase/commit/files, push
  `fix/buffered-output`, create the draft PR, and monitor its exact head.
- After normal merge and #71 closure, merge updated `master` into PR #66 and
  require the previously failing managed row to pass.

## Buffered-output focused-green checkpoint 2026-08-20

### Current findings and observations

- Commit-time notification effects preserve scheduler retry safety, but the
  same task must see its staged no-flush bytes before those effects publish.
- The implementation now projects the task's ordered staged notifications over
  the live transport queue for `buffered_output_length` reads. A later staged
  flushing notify resets the projected length to zero.
- `RuntimeEffect` now keeps binary mode and no-flush state in separate fields.
- The socket transport owns an explicit Latin-1 pending buffer, so large
  no-flush output cannot leak merely because a `BufferedWriter` fills.

### Tried

- The first green attempt still reported `{0, 0}` because the external read
  saw only committed transport state. This was corrected with the attempt-local
  projection rather than publishing output early.
- The next focused run reported the correct `{0, 6}` but showed the actual
  response ordering as prefix, then queued bytes joined to the result line,
  then suffix. The test expectation was corrected to that observed transport
  order; no implementation behavior was weakened.
- Added a catalog contract test for wizard/self authorization, live connection
  resolution, and preservation of `no_flush` and `no_newline`.

### Worked

- A combined focused run executed the real-socket queue/drain test, the new
  notify catalog contract, and the existing buffered-output permission test.
  All three passed with `BUILD SUCCESSFUL in 17s`.
- The real-socket result contained `{1, {0, 6}}` during the command and a later
  `{1, 0}` after the response flushed the queue.

### Did not work

- No broad root/static/conformance gate has run on this implementation yet.
- No issue, commit, push, or PR has been published for the defect.

### Current blocker

- Broad verification and exact publication review remain before the repair can
  be merged into `master` and PR #66 can be updated.

### Next action

- Add snapshot/retry coverage for staged notifications, run affected runtime
  and server suites plus static checks, inspect the final diff, then show the
  exact issue title/label/body before creating it.

## Buffered-output implementation checkpoint 2026-08-20

### Current findings and observations

- ToastStunt source confirms `notify(player, string [, no_flush [, no_newline]])`
  checks wizard-or-self permission, queues output when `no_flush` is truthy,
  omits the line terminator when `no_newline` is truthy, and returns the
  transport result. `buffered_output_length(connection)` reports the transport
  queue's exact byte length.
- Banteng's current `notify` implementation discards its target and both
  optional flags, stages only a plain response line, and always pushes one.
- Banteng's `MooServer.bufferedOutputLength` returns zero unconditionally.
- A clean worktree now exists at
  `C:\Users\Q\code\banteng-buffered-output` on `fix/buffered-output`, based on
  merged remote master `7134dc070adbb4e28b861c644da4af1ce606fb71`.
- A real-socket focused test now requires six no-newline bytes to remain queued
  during the MOO command, then requires the command response to flush them and
  leave a zero queue length.

### Tried

- The first Gradle invocation omitted the root task qualifier and failed in the
  `errorprone-checks` subproject because that subproject has no matching test.
- The corrected `:test --tests ...` invocation executed the new test and failed
  at its expected output assertion, establishing the feature red.
- Initial implementation work introduced a commit-time notification request,
  snapshot retention, a runtime notification effect, and an explicit
  per-connection pending output buffer.
- A compile-only gate found two local wiring errors: a missing
  `NotificationRequest` import in `MooVm`, and an attempt by `MooRuntime` to
  call package-private `VmState.stageOutput` for its no-listener fallback.

### Worked

- The focused red is behavioral rather than a missing-test or compilation red:
  the existing implementation produces immediate line output and reports zero
  queued bytes.
- The source trace rules out DNS and wrong-connection selection as the first
  cause of PR #66's failing step.

### Did not work

- The initial implementation does not compile yet due to the two explicit
  wiring errors above.
- No issue, branch, commit, push, or PR for this defect has been published.

### Current blocker

- Finish the local implementation and prove the focused test green before
  drafting exact publication metadata.

### Next action

- Add the missing import, expose a narrowly named runtime fallback method, and
  give the runtime effect separate `binary` and `noFlush` fields rather than
  overloading one boolean.
- Rerun compile and the focused real-socket test, then add catalog permission
  and snapshot/retry coverage before broader static and test gates.

## PR #66 CI investigation checkpoint 2026-08-20

### Observed

- PRs #67, #68, #69, and #70 completed all six reported checks successfully.
- PR #66 completed static analysis, tests, CodeQL, dependency review, and
  workflow analysis successfully, but its managed MOO conformance job failed.
- The exact failure is
  `builtins/network_matrix.yaml::open_network_connection_loopback_records_outbound_state_and_boots_cleanly`:
  the no-flush notify step expected `buffered_output_length` to increase but
  received zero. The suite reported one failure, 9,492 passes, and 2,118 skips.
- The failure occurs before the row calls the new `connection_name_lookup`
  builtin. PR #66 admits the row by adding that builtin; the other campaign
  PRs skip the row and therefore remain green.
- `MooServer.bufferedOutputLength(long connectionId)` is currently a hard-coded
  zero even though the test calls `notify(connection, text, 1, 1)` with a
  truthy no-flush argument.

### Tried

- Read back the terminal check rollups for PRs #66 through #70.
- Inspected the failing conformance artifact and the exact network-matrix row.
- Compared the failure point with Banteng's current server implementation and
  the documented Toast/Barn no-flush transport contract.
- Opened `investigations/pr66-buffered-output.md` with three competing
  hypotheses and discriminating checks.

### Worked

- The result isolates #66's red check from the DNS feature itself: it exposes
  an independent pre-existing buffered-output defect.
- The separate defect can be repaired on current `master` without contaminating
  PR #66's issue scope or commit boundary.

### Did not work

- PR #66 cannot be merged while the conformance check is red.
- A hard-coded zero cannot satisfy the no-flush queue-length contract.

### Current blocker

- The buffered-output defect needs its own reviewed issue and normally merged
  base-repair PR before #66 can be updated and re-run.

### Next action

- Trace notify and transport publication end to end, write a focused red test,
  implement per-connection queued output and draining semantics, then show the
  exact new issue and PR contents before publishing under Q's name.
- Merge that base repair normally, merge current master into PR #66 without
  rewriting history, and require exact-head green CI before merging #66.

## State

- Implementation is in progress in isolated worktree `C:\Users\Q\code\banteng-issue-59` on branch `fix/59-queued-task-type-none` from exact `origin/master` head `df08c69535d0aaf6bd73d5faddffd05e251ce96d`.
- GitHub issue #59 was created with the exact user-approved title, `bug` label, and body.
- `run-banteng-toastcore.bat` remains an untracked launcher created for the user.

## Current findings

- `cmd.exe /d /c "run-banteng-toastcore.bat"` reaches Banteng through Gradle's Java 25 toolchain and exits 1 while reading `C:\Users\Q\src\toastcore\toastcore.db`.
- The first failure is `java.io.IOException: unsupported v17 value tag 6 at queued task #1135514940 variable i`, from `LambdaMooV17Codec.readValue()` via `readQueuedTask()`.
- The database has four queued tasks. The first task's saved runtime environment contains multiple tag-6 entries, including `i`, `b`, `mnums`, and `rn`; later queued-task environments contain more tag-6 entries.
- ToastStunt commit `aecc51e9449c6e7c95272f0f044b5ba38948459e` defines numeric tag 6 as `TYPE_NONE`, used for uninitialized MOO variables. `new_rt_env()` initializes every runtime slot to `TYPE_NONE`, and `dbio_read_var()` / `dbio_write_var()` accept it without a payload.
- Banteng's shared `ValueTagDecoder` handles tags 0, 1, 2, 3, 4, and 9; the v17 extension handles 10, 12, 13, and 14. Tag 6 falls into the unsupported default.
- Banteng's durable `QueuedTask.initialLocals` is `Map<String, MooValue>`, so it has no explicit representation for persisted uninitialized runtime slots. Treating tag 6 as user-visible `E_NONE` would be incorrect.
- GitHub issue and PR searches across all states found no title/body mentioning `TYPE_NONE`, `value tag 6`, or the queued-task uninitialized-local gap. Closed issue #27 covers queued-task first-line and verb-name fidelity, not this defect.
- Prior memory and the current suspended-task model agree on the required shape: ordered `Map<String, Optional<MooValue>>` slots, with `Optional.empty()` representing persisted `TYPE_NONE`.
- A focused test now creates a queued-task v17 fixture with an interleaved `unset` tag-6 slot, requires that slot to remain absent from initialized VM locals, and requires byte-for-byte reserialization.
- TDD red: `:test --tests world.mongoose.banteng.persistence.QueuedTaskV17CodecTest.preservesUninitializedQueuedTaskLocals` failed with `IOException` at the codec read.
- Implementation adds `QueuedEnvironment`, retains all ordered slots as `Optional<MooValue>`, filters initialized locals for VM/task-list consumers, decodes tag 6 only in the queued environment context, and writes empty slots back as tag 6.
- TDD green: the same focused root test passed with `BUILD SUCCESSFUL in 20s`.
- The complete `QueuedTaskV17CodecTest` passed, followed by focused `RepeatedRestartTest`, `PublicationSchedulerTest`, and `TaskRegistryTest` coverage.
- Runtime coverage now proves an imported empty slot is absent from `queued_tasks(1)` and raises catchable `E_VARNF` until assignment.
- The exact ToastCore fixture was copied into the isolated worktree's ignored `build` directory; its SHA-256 remained `8013B703C61A9894866F836F2B934EADA7118CDF0B3CD56181E4BF9205B2F557`.
- Banteng loaded that fixture and listened on `127.0.0.1:17777`; an unauthenticated TCP connection succeeded and read 146 banner bytes. This proves the prior tag-6 startup failure is gone.
- The first banner is a separate runtime/login failure: `#10:connection_name_lookup (this == #10), line 10: Verb not found`, called from `#0:do_login_command`. It is not part of issue #59 and has not been diagnosed or reported yet.
- The temporary server stopped after `Ctrl-C`; port 17777 was no longer listening and no checkpoint file was created because the Gradle batch termination did not take Banteng's graceful checkpoint path.
- Full feature-worktree `check` ran 571 root tests and failed 6. The same full gate on clean tracked `master` at the identical base ran 569 root tests and failed the exact same 6 test names: two `OperationsCommandTest` cases and four `BuiltinCatalogTest` cases. The feature introduced two passing tests and no additional full-gate failure.
- A package-wide root gate for `world.mongoose.banteng.persistence.*` and `world.mongoose.banteng.runtime.*` passed 161 tests in 22 suites with zero failures, errors, or skips.
- The codec now routes both queued and suspended variable slots through one `readRuntimeVariable` / `writeRuntimeVariable` implementation so tag-6 handling cannot drift between the two durable-task forms.
- Post-refactor targeted gate passed 54 tests across `QueuedTaskV17CodecTest`, `RepeatedRestartTest`, `PublicationSchedulerTest`, and `TaskRegistryTest` with zero failures, errors, or skips.
- Root `compileJava`, `errorprone-checks:check`, and `rewrite-recipes:check` passed after the final refactor.
- A package-wide rerun exposed `ConcurrentSchedulerStressTest.saturationConflictsAndRetriesRemainBoundedAndEventuallyProgress` as load-sensitive: it timed out/failed twice in the 161-test package selection but passed when rerun alone. The full feature and clean-base `check` runs did not list this stress test among their six failures.
- The final exact ToastCore boot repeated on the final code: hash matched, Banteng listened on `127.0.0.1:17777`, and a TCP connection succeeded. The same separate `connection_name_lookup` banner traceback remains.
- Final commit is `a82e7866b13eb21e8f8a0dcc6b0f212296b9842c` (`fix: restore uninitialized queued task locals`), one commit ahead of exact `origin/master` base `df08c69535d0aaf6bd73d5faddffd05e251ce96d`.
- The isolated worktree is clean, port 17777 is no longer listening, issue #59 remains open, and no branch or commit was pushed.
- ToastStunt registers `connection_name_lookup(OBJ [, ANY])`. Its implementation permits only a wizard or the queried connection/player itself, rejects a missing live connection with `E_INVARG`, runs reverse DNS as background work, and optionally replaces the connection name when the second argument is truthy.
- Banteng has no `connection_name_lookup` manifest entry or implementation. Existing `BuiltinResult.HostWork` and `PublicationScheduler` support bounded asynchronous host work, so DNS does not need to block the scheduler.
- Banteng's `ConnectionRegistryAccess` currently exposes immutable connection metadata through `connectionInfo()` plus live identity resolution through `connectionId()`. The concrete registry has no operation for replacing the connection name/metadata after DNS completes.
- `MooServer.connectionInfo()` currently records numeric local and remote addresses under `source_address`/`source_ip` and `destination_address`/`destination_ip`; the remote `destination_address` is the reverse-lookup input for an inbound connection.
- A fresh forced rerun of the four issue-59 test classes executed all tasks and passed 54 tests with zero failures, errors, or skips (`BUILD SUCCESSFUL in 51s`).
- A fresh live boot against the exact fixture hash again accepted TCP and returned exactly 146 bytes containing the `connection_name_lookup` missing-verb traceback. After `Ctrl-C`, port 17777 was not listening and the diagnostic checkpoint did not exist.
- The exact draft #59 PR metadata and exact new `connection_name_lookup` issue metadata have been shown to Q. Neither has been published; approval is pending. The proposed PR is draft, base `master`, head `fix/59-queued-task-type-none`, title `Restore TYPE_NONE queued-task locals`, label `bug`, and closes #59.
- ToastStunt's callback rechecks that the connection is live before DNS, always returns the lookup string when the server is not shutting down, and performs the optional rewrite only when lookup status is zero. A failed rewrite replaces the result with structured `E_INVARG` (`Failed to rewrite connection name.`).
- Local compatibility work now lives in a second isolated worktree, `C:\Users\Q\code\banteng-connection-name-lookup`, on unpushed branch `fix/connection-name-lookup` stacked from issue-59 commit `a82e786` so the exact database can exercise both fixes together.
- TDD red for the builtin contract was a missing `BuiltinHosts.Builder.connectionNameLookup` symbol. The implemented catalog slice registers `connection_name_lookup(OBJ [, ANY])` as `SUSPENDING_HOST`, enforces wizard-or-self permission before live-target validation, and delegates authorized live calls to an injected host handler. Its focused test is green (`BUILD SUCCESSFUL in 32s`).
- TDD red for registry publication was a missing `rewriteConnectionName` symbol. The implemented registry slice rewrites only `destination_address` for the same live connection ID and unchanged `destination_ip`, retaining the numeric IP and other metadata. Its focused test is green (`BUILD SUCCESSFUL in 30s`).
- The end-to-end runtime test now compiles and is red at its output assertion because the runtime has not yet wired a host implementation; the builtin reaches the default host handler and returns `E_INVARG`. The first compile attempt also exposed and corrected an Error Prone `AddressSelection` warning in the test.
- The runtime host implementation now captures the live connection ID, numeric remote IP, current name, and rewrite flag from the validated attempt; reverse DNS runs in `HostWork`; optional publication compares the live connection ID and original IP under the runtime session lock and increments `sessionRevision` after a successful rewrite.
- Deterministic race coverage blocks DNS, closes the original connection, reopens the same negative object ID with a different IP, and then releases DNS. The old task receives catchable `E_INVARG`, and the replacement metadata remains unchanged (`BUILD SUCCESSFUL in 13s`).
- A forced affected-scope gate executed 45 tests across the catalog contract/composition, connection registry, runtime, real socket server, queued-task codec, and repeated restart tests with zero failures, errors, or skips (`BUILD SUCCESSFUL in 46s`, 11 tasks executed).
- A broader 141-test gate exposed five `BuiltinCatalogTest` failures: the one new authoritative-manifest count failure was fixed by adding `connection_name_lookup`; the remaining four are the known baseline environment failures (`crypt`, `usage`, `memory_usage`, and `numbers`).
- Exact ToastCore boot with both local fixes accepted TCP and returned 217 bytes. The prior `connection_name_lookup` traceback is gone. The next independent banner failure is `#20:is_integer is_numeric (this == #20), line 5: Verb not found`, called from `#10:wel*come @wel*come` line 13 and `#0:do_login_command` line 33. The banner begins with `~FF~FBF`.
- The forced static gate for `connection_name_lookup` passed with 12 of 12 tasks executed (`BUILD SUCCESSFUL in 43s`). The full forced `check` ran 575 tests and failed the same six baseline test names as the 571-test issue-59 branch; the four additional tests passed.
- The DNS compatibility change is committed locally as `1a7898f` (`feat: add asynchronous connection name lookup`) on clean unpushed branch `fix/connection-name-lookup`, with exactly eight files and 387 insertions. It remains stacked on `a82e786` only for local exact-database verification.
- Exact ToastCore source identifies the new missing call as `match(args[1], "^ *[-+]?[0-9]+ *$")` in object #20 verb `is_integer is_numeric` line 5. Repository and all-state GitHub search found no Banteng `match`/`rmatch` builtin or existing issue.
- ToastCore documentation and ToastStunt source define `match` and `rmatch` together: two required strings plus optional truthy case flag, case-insensitive default, first-vs-last match selection, exact `{start, end, nine capture pairs, subject}` result, empty list for no match, `E_INVARG` for malformed patterns, and `E_QUOTA` for bounded abort.
- The exact third issue draft (`Implement Toast match and rmatch builtins for ToastCore login`, label `bug`) has been shown to Q and is waiting at the same publication approval gate.
- Local regex work is in a third isolated worktree, `C:\Users\Q\code\banteng-match-rmatch`, on unpushed branch `fix/match-rmatch` stacked from `1a7898f` for real-database iteration.
- The initial catalog contract test pins Toast's published `match`/`rmatch` examples, case behavior, captures, empty result, malformed-pattern error, and call-shape errors. TDD red is `NoSuchElementException` at missing catalog spec lookup (`1 test completed, 1 failed`, `BUILD FAILED in 28s`).
- A dedicated byte-oriented `ToastRegexService` now translates Toast percent groups, alternation, backreferences, word constructs, literal non-Toast metacharacters, anchors, and character classes to bounded Java matching without changing `PcreService`. It implements reverse matching by scanning candidate starts from the end so Toast's documented `rmatch("foobar", "o*b") => {4, 4, ...}` holds.
- The regex service returns exact one-based inclusive bounds plus nine capture pairs, uses `{0, -1}` for unmatched groups, defaults to ASCII-insensitive matching, maps syntax errors to `E_INVARG`, and uses a shared one-million-character-read budget that maps pathological backtracking to `E_QUOTA`.
- Focused regex coverage is green (`BUILD SUCCESSFUL in 18s`) for documented forward/reverse results, captures/backreferences, word boundaries, literal parentheses/pipe, percent in character classes, case mode, malformed patterns, and quota exhaustion.
- Exact ToastCore boot with all three local fixes now produces a 247-byte usable welcome prompt: `Welcome to the ToastCore database.` and `Type 'connect wizard' to log in.` The previous match traceback is gone.
- Submitting `connect wizard` on a second live socket produces a new 142-byte traceback: `#10:maybe_limit_commands`, line 18, `Range error`, from `#0:do_login_command` line 31.
- Source diagnosis: `maybe_limit_commands` calls `typeof(`idle_seconds(p) ! ANY')`; because Banteng lacks `idle_seconds`, ToastCore catches the error and deletes the active connection key, then line 18 indexes the deleted key and raises `E_RANGE`. Banteng also lacks the paired `connected_seconds` builtin; ToastCore uses both extensively.
- ToastStunt registers `idle_seconds(OBJ)` and `connected_seconds(OBJ)`. `idle_seconds` returns wall-clock seconds since the live connection's last activity for pre-login or logged-in connections; `connected_seconds` requires a completed login timestamp. Both return `E_INVARG` when the target is not an eligible live connection.
- The diagnostic server on port 17779 was stopped; the port is not listening and no diagnostic checkpoint exists.
- Final regex verification: the three focused catalog tests passed with all 11 tasks executed (`BUILD SUCCESSFUL in 52s`); the forced static gate passed all 12 tasks (`BUILD SUCCESSFUL in 43s`); after updating `NamespaceOwnershipTest`'s main-source count from 91 to 92, full `check` ran 577 tests and failed only the same six baseline names.
- The regex slice is committed locally as `3f01a2f` (`feat: add bounded Toast regular expression matching`) on clean unpushed branch `fix/match-rmatch`; exactly four files changed with 386 insertions and one deletion.
- The exact fourth issue draft (`Implement idle_seconds and connected_seconds connection timers`, label `bug`) has been shown to Q and is waiting at the publication gate.
- Timer work is in a fourth isolated worktree, `C:\Users\Q\code\banteng-connection-timers`, on unpushed branch `fix/connection-timers` stacked from `3f01a2f` for real-database iteration.
- TDD red for the timer catalog contract was the missing `BuiltinHosts.Builder.idleSeconds` symbol. The catalog now registers both one-object `EXTERNAL_READ` connection builtins and delegates to runtime-owned handlers; its focused contract test is green (`BUILD SUCCESSFUL in 31s`).
- A deterministic runtime test pins pre-login idle time, post-login connected time, activity reset on ordinary input, cross-player idle observation, and `E_INVARG` for unconnected/missing targets. Its TDD red is the intentionally missing `MooRuntime(WorldTxn, ConnectionRegistryAccess, LongSupplier)` constructor (`BUILD FAILED in 13s`).
- In-progress runtime code injects a monotonic nanosecond supplier, records open/activity/login timestamps in attempt-local `ConnectionState`, copies and compares the new timestamp state, resets login time at `switchConnectionPlayer`, and updates activity on normal and transport out-of-band input.
- The timer runtime test went green (`BUILD SUCCESSFUL in 18s`). A forced affected gate then passed 32 tests across catalog contracts/composition and all `MooRuntimeTest` cases with zero failures (`BUILD SUCCESSFUL in 44s`).
- Exact ToastCore boot with timers retains the 247-byte welcome prompt. `connect wizard` advances past `maybe_limit_commands` and exposes the next independent 199-byte traceback: `#52:isa`, line 3, `Verb not found`, called from `$login:connect` line 23 and `#0:do_login_command` line 34.
- The exact fifth issue draft (`Implement Toast isa builtin for ToastCore login`, label `bug`) has been shown to Q and is waiting at the publication gate. ToastCore #52 line 3 is exactly `return isa(@args);`; no existing issue or implementation was found.
- Timer forced static checks passed all 12 tasks (`BUILD SUCCESSFUL in 43s`). The first forced full `check` ran 579 tests and had the six baseline failures plus five `PublicationSchedulerTest` conflict-scenario failures and the known load-sensitive `ConcurrentSchedulerStressTest` failure.
- The five scheduler failures were timer-feature-caused: treating every `lastActivityNanos` change as revisioned session state made concurrent commands retry on session state before producing the world conflict those tests require. This was an isolation design defect, not a stable-test problem.
- The correction removes activity time from structural `ConnectionState.sameState`; attempt-local activity remains immediately readable, while publication merges it monotonically by maximum into the same live connection without incrementing `sessionRevision`. Login time remains structural because player association changes the registry in the same publication.
- After the correction, a forced combined run of all 40 `PublicationSchedulerTest` cases plus the deterministic timer runtime test passed with all 11 tasks executed (`BUILD SUCCESSFUL in 43s`).

## Observations

- The batch file itself is reaching the intended application and database. The observed failure is a Banteng v17 queued-task restoration gap, not a batch dispatch or Java-selection failure.
- Toast's `queued_tasks(1)` presentation excludes `TYPE_NONE` variables via `make_rt_var_map()`, but the database runtime environment retains every named slot. The persistence model and user-visible task listing therefore need different representations.

## Blocker

- External publication is waiting on Q's exact-content approval.
- The local compatibility implementation still requires a narrow, concurrency-safe connection-name update contract. The background DNS callback must verify the connection is still live before publishing a rewrite, rather than mutating an attempt-local registry captured before suspension.
- The first four compatibility slices have no focused red test. The timer slice needs final static/full reruns and a clean local commit. The current actual-login blocker is missing `isa`.

## Next action

- Await approval of the already-shown exact #59 draft-PR and new-issue metadata before pushing or creating either publication.
- Implement a dedicated bounded Toast regex service and register both builtins without changing PCRE behavior. Translate Toast percent grouping/alternation/backreferences/word constructs, preserve byte indices, and search reverse starts explicitly for `rmatch` semantics.
- Rerun the timer forced static and full gates after the activity-publication correction; distinguish the known stress flake if it recurs.
- Commit the timer slice separately, then implement `isa` in a fifth isolated worktree/commit using ToastStunt's exact return-object and list-order contract.
- Repeat exact ToastCore welcome and `connect wizard` until login succeeds or another independent blocker is exposed.

## Checkpoint 2026-08-20: ISA slice in progress

### Current findings and observations

- Timer work is committed locally as `52c852c` (`feat: track connection idle and login time`) on clean unpushed branch `fix/connection-timers`.
- The corrected timer static gate passed with all 12 tasks executed. Its forced full `check` ran 579 tests and failed only the same six baseline test names: two `OperationsCommandTest` cases and four `BuiltinCatalogTest` environment-dependent cases.
- Exact ToastCore startup with all four stacked fixes still produces the full welcome prompt. `connect wizard` advances through queued-task restoration, DNS lookup, Toast regular expressions, and connection timers, then fails at missing `#52:isa`.
- ISA work is isolated in `C:\Users\Q\code\banteng-isa` on unpushed branch `fix/isa`, stacked from `52c852c` only for exact-database iteration.
- The focused contract test initially failed with `NoSuchElementException` because `isa` was absent from the catalog. The implementation now registers `isa(ANY, ANY [, INT])`, validates Toast object-like inputs, treats waifs as their class, preserves candidate-list order, traverses Banteng multiple ancestry, and supports boolean or matching-parent return modes.
- The forced focused ISA test now passes with all 11 tasks executed (`BUILD SUCCESSFUL in 41s`).

### Current blocker

- The ISA implementation has not yet passed the authoritative manifest, static, full, or exact ToastCore gates, and has not been committed.
- External publication still waits on Q's approval of the exact draft PR and issue text already shown; nothing new has been pushed or opened.

### Next action

- Add `isa` to the authoritative reachable builtin-name contract, then run the focused manifest and ISA tests.
- Run forced static and full gates, preserving raw output and separating the six known baseline failures from feature regressions.
- Commit the ISA slice separately if its gates hold, boot the exact fixture on a fresh port, submit `connect wizard`, and diagnose the next concrete blocker or prove a usable login.

## Checkpoint 2026-08-20: Empty-sequence `$` blocker

### Current findings and observations

- The ISA catalog/behavior pair passed a forced focused gate with all 11 tasks executed (`BUILD SUCCESSFUL in 41s`).
- The ISA forced static gate passed all 10 executed tasks (`BUILD SUCCESSFUL in 39s`).
- The ISA forced full `check` executed 580 tests and failed exactly the six known baseline cases: the two `OperationsCommandTest` cases plus `BuiltinCatalogTest` crypt, usage, memory_usage, and numbers. No ISA-specific test failed.
- ISA is committed locally as `78539c4` (`feat: add Toast isa builtin`) on clean unpushed branch `fix/isa`, with exactly two files and 190 insertions.
- The exact fixture hash remained `8013B703C61A9894866F836F2B934EADA7118CDF0B3CD56181E4BF9205B2F557`. Banteng listened on `127.0.0.1:17781`, returned the 247-byte welcome prompt, and accepted `connect wizard` on the same TCP session.
- The missing `isa` traceback is gone. Login now fails independently with a 212-byte traceback at `#10:record_connection`, line 13, `Range error`.
- ToastCore's failing line is `plyr.all_connect_places = {chost, @acp[1..min($, 15)]};`, immediately after `acp = setremove(plyr.all_connect_places, chost);`.
- Banteng's range implementation already returns an empty list when the end is below the start. Its sequence `$` implementation instead raises `E_RANGE` when the sequence size is zero, so the empty `acp` cannot produce the intended `1..0` slice.
- The diagnostic server was stopped; port 17781 is not listening and no diagnostic checkpoint exists.
- The first explicit WSL authority command named a nonexistent `Ubuntu` distribution and failed with `WSL_E_DISTRO_NOT_FOUND`; the installed/default distribution still needs discovery before reference-source confirmation.

### Current blocker

- Empty list/string `$` behavior must be confirmed against ToastStunt and corrected without changing empty-map last-key behavior or masking genuine direct-index range errors.
- A new exact issue draft for this separate compatibility defect must be shown to Q before publication.
- External publication of every prior draft remains paused pending Q's exact-content approval.

### Next action

- Discover the installed WSL distribution, inspect ToastStunt's last-index/range evaluation, and pin the exact empty-sequence contract.
- Create a sixth isolated branch/worktree stacked from `78539c4`, add a red VM test proving empty list/string range use and direct indexing behavior, then implement the smallest contextual fix.
- Repeat focused/static/full gates and exact ToastCore login; continue to the next independent blocker or a usable session.

## Checkpoint 2026-08-20: Exact ToastCore login achieved

### Final local findings and observations

- The installed authoritative WSL distribution is `Debian`; the earlier explicit `Ubuntu` name was wrong.
- ToastStunt `src/execute.cc` confirms `EOP_FIRST` returns `0` for empty strings/lists and `1` otherwise, while `EOP_LAST` returns the exact string/list length, including `0`. Its range execution accepts `from > to` and delegates to empty `substr`/`sublist` results.
- Empty-sequence work is isolated on unpushed branch `fix/empty-sequence-boundaries`, stacked from ISA commit `78539c4` only for exact-database iteration.
- TDD red was `return {}[1..$];` ending `ERRORED` instead of `RETURNED` (`1 test completed, 1 failed`, `BUILD FAILED in 50s`).
- The implementation changes the shared list/string boundary opcode to push zero for either empty `^` or `$`; map handling stays in its separate preceding branch and is unchanged.
- The focused VM test covers empty list/string `1..$`, empty `^..-1`, non-empty `^..$`, direct empty-sequence index `E_RANGE`, and empty-map boundary `E_RANGE`. It passed with all 11 tasks executed (`BUILD SUCCESSFUL in 43s`).
- The forced static gate passed all 10 executed tasks (`BUILD SUCCESSFUL in 42s`).
- The forced full `check` executed 581 tests and failed exactly the six known baseline cases: the two `OperationsCommandTest` cases plus `BuiltinCatalogTest` crypt, usage, memory_usage, and numbers. No boundary-specific test failed.
- The boundary fix is committed locally as `61a89a5` (`fix: preserve empty sequence boundary indices`) with exactly two files, 37 insertions, and five deletions.
- The exact fixture hash again matched `8013B703C61A9894866F836F2B934EADA7118CDF0B3CD56181E4BF9205B2F557`; Banteng listened on `127.0.0.1:17782`.
- On one TCP session the database returned its 247-byte welcome banner, accepted `connect wizard`, returned `*** Connected ***`, and processed a subsequent command. A second session repeated `*** Connected ***`; `help` returned the complete 800-byte ToastCore help index.
- The diagnostic server was stopped. Port 17782 is not listening, no diagnostic checkpoint exists, and the boundary worktree is clean.
- The exact sixth issue draft (`Return zero for empty sequence boundary indices`, label `bug`) has been shown to Q. It remains unpublished with every other draft.

### Remaining blocker

- No branch has been pushed, no PR has been created, and the five newly discovered issue drafts remain unpublished because the exact-content publication approval gate is still pending.
- The six local commits are intentionally separate but stacked for end-to-end database proof. Clean master-based publication branches/PRs should be prepared after their issue numbers exist so each PR has a narrow diff and exact closing phrase.

### Next action

- On Q's explicit publication approval, create the five shown compatibility issues, push the issue-59 branch, and create the shown draft PR for #59.
- Then prepare each later commit as a clean master-based branch, rerun its scoped gate independently, show its exact PR title/label/body with the created issue number, and publish only after the corresponding approval.
- Do not merge any PR unless Q separately authorizes a normal merge commit after literal scope/check verification.

## Checkpoint 2026-08-20: Clean publication branches in progress

### Current findings and observations

- A fresh local/GitHub audit confirms `origin/master` and local `master` are both `df08c69535d0aaf6bd73d5faddffd05e251ce96d`; issue #59 remains the only issue from this compatibility campaign and no campaign PR exists.
- The primary checkout still has only the user-requested untracked `run-banteng-toastcore.bat`. Several unrelated historical worktrees contain user changes; none were touched.
- The six exact-database proof branches remain clean, local, and unpushed. They are intentionally stacked from #59 through boundary behavior.
- Clean master-based DNS publication branch `pr/toast-connection-name-lookup` now contains one commit `bc088f6`, exactly eight files and 387 insertions, with no #59 persistence changes.
- Clean master-based regex publication branch `pr/toast-match-rmatch` now contains one commit `4a9fa4f`, exactly four files, 386 insertions, and one deletion. The cherry-pick conflict was resolved by excluding all parent-only DNS catalog/test code while preserving the regex field, manifest entries, service, tests, and namespace source-count update.
- The regex clean-branch staged and source diffs had identical per-file insertion/deletion counts. Their patch IDs differed because the manifest/test insertions necessarily sit beside different neighbors after the DNS parent is removed.
- Timer extraction is in `C:\Users\Q\code\banteng-pr-timers` on `pr/toast-connection-timers` at clean master. A mechanical cherry-pick was aborted because its constructor and host-composition contexts included prior DNS/regex code.
- Timer is being reapplied directly to master's current constructor/host APIs. `BuiltinHosts` now has only the timer-owned `idleSeconds` and `connectedSeconds` handlers/builders; no DNS handler was imported.
- The original timer commit also added a `BuiltinCompositionTest` assertion for the prior DNS handler. That line is parent-scope contamination and will be excluded from the clean timer PR; only the two timer handler assertions belong.

### Current blocker

- External issue/PR publication remains at the exact-content authorization gate.
- The clean timer branch is temporarily dirty while the master-native timer adaptation is in progress; it has not yet compiled, been tested, or been committed.

### Next action

- Complete the master-native timer catalog/runtime/test changes with an injected monotonic clock and the previously verified non-structural activity merge semantics.
- Run the clean timer focused/static gates and inspect its literal diff before committing.
- Extract clean ISA and boundary branches, verify all five clean publication branches independently, and prepare exact PR bodies after issue numbers exist.

## Checkpoint 2026-08-20: Clean timer committed; ISA extraction

### Current findings and observations

- Clean timer branch `pr/toast-connection-timers` is committed at `3166ee4` (`feat: track connection idle and login time`), directly based on master with exactly six files, 188 insertions, and 11 deletions.
- The clean timer diff contains no DNS resolver/handler, regex service/manifest entry, queued-task codec change, ISA builtin, or boundary-opcode change.
- The master-native timer implementation injects `LongSupplier` only through a package-private test constructor, uses `System::nanoTime` in both normal constructor bodies, records open/login/activity times, and retains monotonic non-structural activity publication.
- Clean timer catalog/composition/runtime focused tests passed with all 11 tasks executed (`BUILD SUCCESSFUL in 41s`).
- All `PublicationSchedulerTest` cases plus the deterministic timer runtime test passed with all 11 tasks executed (`BUILD SUCCESSFUL in 44s`), confirming the activity clock does not contaminate world/session conflict ordering.
- Clean timer static checks passed all 10 executed tasks (`BUILD SUCCESSFUL in 42s`).
- Clean ISA extraction is in `C:\Users\Q\code\banteng-pr-isa` on `pr/toast-isa`. Production `BuiltinCatalog.java` applied without conflict.
- The only ISA conflict was the authoritative-name list beside its timer parent. It has been resolved to add only `isa`, explicitly excluding parent-only `idle_seconds`.

### Current blocker

- External issue/PR publication still awaits Q's exact-content authorization.
- The clean ISA cherry-pick remains in progress until its resolved test file is staged, checked, and continued.

### Next action

- Finish the clean ISA cherry-pick, verify its focused/static gates, and inspect the master-relative two-file diff.
- Extract and verify the independent boundary branch.
- Run clean-branch verification for DNS and regex, then prepare publication-ready exact metadata without pushing.

## Checkpoint 2026-08-20: All clean branches verified; publication gate remains

### Current findings and observations

- All six deliverable branches are clean, exactly one commit above common merge base `df08c69535d0aaf6bd73d5faddffd05e251ce96d`, and remain unpushed.
- Issue #59: `fix/59-queued-task-type-none` at `a82e7866b13eb21e8f8a0dcc6b0f212296b9842c`; three files, 184 insertions, 39 deletions.
- DNS: `pr/toast-connection-name-lookup` at `bc088f690fc60633b3cd7bebd41b9c75207a5da6`; eight files, 387 insertions.
- Regex: `pr/toast-match-rmatch` at `4a9fa4f7480d2b8e722375219b06e7a15c255ad0`; four files, 386 insertions, one deletion.
- Timers: `pr/toast-connection-timers` at `3166ee4a925467bc48cf36f3f24799e327bb81f9`; six files, 188 insertions, 11 deletions.
- ISA: `pr/toast-isa` at `1debd5f2d8427f6dbd87826b74bb3649d1d4d2a8`; two files, 190 insertions.
- Boundaries: `pr/empty-sequence-boundaries` at `1a5115c5c89868f7b6faed077f9bf1ac535e85d6`; two files, 37 insertions, five deletions.
- Fresh #59 focused gate passed with all 11 tasks executed (`BUILD SUCCESSFUL in 48s`); its fresh static gate passed all 10 tasks (`BUILD SUCCESSFUL in 38s`).
- Clean DNS focused catalog/composition/runtime/registry gate passed with all 11 tasks executed (`BUILD SUCCESSFUL in 1m 1s`); its static gate passed all 10 tasks (`BUILD SUCCESSFUL in 58s`).
- Clean regex focused contract/quota/manifest/namespace gate passed with all 11 tasks executed (`BUILD SUCCESSFUL in 43s`); its static gate passed all 10 tasks (`BUILD SUCCESSFUL in 58s`).
- Clean ISA focused behavior/manifest gate passed with all 11 tasks executed (`BUILD SUCCESSFUL in 42s`); its static gate passed all 10 tasks (`BUILD SUCCESSFUL in 50s`).
- Clean boundary focused VM gate passed with all 11 tasks executed (`BUILD SUCCESSFUL in 43s`); its static gate passed all 10 tasks (`BUILD SUCCESSFUL in 50s`).
- The clean timer focused and static results remain as recorded in the preceding checkpoint.
- Fresh exact-database integration proof used SHA-256 `8013B703C61A9894866F836F2B934EADA7118CDF0B3CD56181E4BF9205B2F557` on port 17783. One TCP socket received the 247-byte welcome banner, `*** Connected ***` after `connect wizard`, and the complete 800-byte help index after `help`.
- The refreshed diagnostic server was stopped; port 17783 is not listening and no diagnostic checkpoint exists.

### Current blocker

- External publication is the only remaining blocker. Q has been shown the exact #59 draft PR plus the five exact issue drafts, but has not supplied the requested `publish all shown drafts` authorization after the latest boundary draft.

## Checkpoint 2026-08-20: Reviewed drafts published

### Current findings and observations

- Q authorized `publish all`; this was interpreted exactly as the previously shown five issue drafts plus the one shown #59 draft PR, with no merge and no unseen later-fix PR bodies.
- Before publication, all six clean publication worktrees were re-audited: each branch was one commit directly above remote `master` `df08c69535d0aaf6bd73d5faddffd05e251ce96d`, each status and `git diff --check` was clean, all six remote heads were absent, and no duplicate campaign issue or PR existed.
- Published issue #60: `Implement connection_name_lookup for ToastCore login` (`bug`).
- Published issue #61: `Implement Toast match and rmatch builtins for ToastCore login` (`bug`).
- Published issue #62: `Implement idle_seconds and connected_seconds connection timers` (`bug`).
- Published issue #63: `Implement Toast isa builtin for ToastCore login` (`bug`).
- Published issue #64: `Return zero for empty sequence boundary indices` (`bug`).
- Pushed `fix/59-queued-task-type-none` at exact commit `a82e7866b13eb21e8f8a0dcc6b0f212296b9842c` and opened draft PR #65, `Restore TYPE_NONE queued-task locals`, from that head to `master`, label `bug`, closing #59.
- GitHub readback confirmed every published title and body matches the reviewed source after trailing-newline normalization. PR #65 is draft/open with exactly one commit and the expected three files. Its initial check rollup had dependency review successful and four jobs in progress.
- Fresh post-publication integration proof ran the clean stacked branch `fix/empty-sequence-boundaries` at `61a89a56edd7bb9c6c6923fc9087f00c81c99da8` against the exact ToastCore fixture SHA-256 `8013B703C61A9894866F836F2B934EADA7118CDF0B3CD56181E4BF9205B2F557` on `127.0.0.1:17784`.
- The fresh socket received the 247-byte welcome banner, `*** Connected ***` after `connect wizard`, and the complete 800-byte help index after `help`.
- The diagnostic Gradle server was interrupted after proof; the command ended with exit code 1 because `Ctrl+C` terminated the batch job.
- The primary checkout still has the user's untracked `run-banteng-toastcore.bat`; it was not staged or modified.

### State

- External drafts authorized in this publication wave are live as issues #60-#64 and draft PR #65.
- No merge was requested or performed.
- The five later clean publication branches remain local and unpushed because their PR metadata/body drafts have not been shown to Q.

### Blocker

- None for the authorized publication wave.

### Next action

- Verify port 17784 is no longer listening and no diagnostic checkpoint was written.
- Perform one final GitHub readback of PR #65 and issues #60-#64, then report the exact URLs and current check state.

## Final handoff 2026-08-20

### Observed

- GitHub issues #60 through #64 are open with label `bug` and the exact reviewed titles.
- Draft PR #65 is open from `fix/59-queued-task-type-none` to `master`, contains only commit `a82e7866b13eb21e8f8a0dcc6b0f212296b9842c`, changes the expected three files, and closes #59.
- Remote `master` remained `df08c69535d0aaf6bd73d5faddffd05e251ce96d`; the published head resolves to the exact local #59 commit.
- GitHub-normalized issue and PR bodies match all six reviewed source files exactly after trimming the trailing newline.
- The primary checkout still reports only the user's untracked `run-banteng-toastcore.bat`; the #59 and stacked integration worktrees are clean.

### Tried

- Audited every clean publication worktree for exact branch, commit, merge base, one-commit topology, file list, status, and `git diff --check` before changing GitHub state.
- Checked for duplicate campaign issues, PRs, and remote heads before creation.
- Created the five approved issues, pushed only the approved #59 branch, and created the approved draft PR.
- Compared GitHub bodies directly with the approved local body files. The first raw comparison reported false because GitHub retained a trailing newline; a normalized character-by-character comparison reported exact matches with no mismatch position.
- Ran Banteng from the clean stacked integration branch against the exact ToastCore database on port 17784, connected as wizard, requested help, then interrupted the diagnostic Gradle process.

### Worked

- Exact fixture SHA-256 was `8013B703C61A9894866F836F2B934EADA7118CDF0B3CD56181E4BF9205B2F557`.
- The live TCP session received a 247-byte welcome banner, a 19-byte `*** Connected ***` response, and the complete 800-byte help index.
- Final cleanup readback showed port 17784 not listening and no `toastcore-publish-proof.new` checkpoint.
- Initial/current PR readbacks showed successful workflow static analysis, CodeQL, and dependency review; static analysis/tests and the MOO conformance suite were still running at the last readback.

### Did not work

- A direct raw string comparison of local and GitHub bodies was not a valid equality test because of trailing-newline handling; normalized comparison was required.
- Interrupting the Windows Gradle batch process required answering its `Terminate batch job (Y/N)?` prompt and produced exit code 1 by design.

### Current blocker

- None for the authorized publication wave. No merge was requested or performed.
- PR #65 CI was still in progress at the last readback; monitoring it would be a separate follow-on action.
- The later five clean fix branches remain local and unpushed because no PR bodies for them were shown or approved.

### Next action

- If requested, monitor draft PR #65 checks to terminal state and report raw output for the exact head.
- Before publishing any of the five later fixes as PRs, draft and show their exact title, base/head, labels, body, issue-closing phrase, commit, and file list for approval.

## Merge audit handoff 2026-08-20

### Observed

- Q requested `mergje all the rest of the prs`.
- The live repository inventory contains exactly one open ToastCore campaign PR: #65, `Restore TYPE_NONE queued-task locals`.
- Issues #60 through #64 are open, but their five corresponding clean local branches remain absent from the remote and have no PRs.
- PR #65 remains a draft. It is `MERGEABLE` with `mergeStateStatus=CLEAN`.
- All six reported PR #65 check runs completed successfully: workflow static analysis, static analysis and tests, the CI CodeQL run, dependency review, MOO conformance, and the additional CodeQL run.
- PR #65 still has exactly commit `a82e7866b13eb21e8f8a0dcc6b0f212296b9842c`, closes #59, and changes only the expected three files with 184 insertions and 39 deletions.
- The exact remote base/head audit was `master` at `df08c69535d0aaf6bd73d5faddffd05e251ce96d` and `fix/59-queued-task-type-none` at `a82e7866b13eb21e8f8a0dcc6b0f212296b9842c`.
- The #59 worktree was clean and `git diff --check master...HEAD` emitted no errors.

### Tried

- Inventoried all open PRs rather than treating unrelated repository work as part of `the rest`.
- Re-ran the mandatory pre-merge audit: files, commit list, issue scope, closing phrase, checks, draft state, mergeability, exact base/head, and worktree status.
- Identified the required normal-merge command as `gh pr merge 65 --merge`.

### Worked

- The audit established that PR #65 is technically ready for a normal merge after its draft state is removed.
- No unrelated PR, branch, issue, or dirty worktree content was included in the proposed merge scope.

### Did not work

- The requested plural PR set does not exist; only #65 is a PR.
- PR #65 cannot be merged while it remains draft.
- No ready or merge mutation was attempted because readying a draft is a separate authorization gate and the five later PR metadata/body drafts have not been shown to Q.

### Current blocker

- Awaiting explicit authorization to mark PR #65 ready before executing the already-authorized normal merge.
- Publishing the later five fixes would require a separate exact-metadata review before creating their PRs.

### Next action

- On `ready and merge #65`, run `gh pr ready 65`, immediately re-read the exact PR head/state/checks, then run `gh pr merge 65 --merge` and verify the merge commit has two parents, #59 closed, and remote `master` contains the exact feature commit.
- If Q intended the later five local fixes, first prepare and show their exact PR metadata and bodies; do not create or merge them silently.

## Fresh functionality check 2026-08-20

### Observed

- Q asked whether it works, requiring a distinction between the complete local integration and the published repository state.
- Live GitHub readback showed PR #65 still open and draft with `mergeStateStatus=CLEAN`; all six check runs were complete and successful.
- Remote branch readback contained only `master` at `df08c69535d0aaf6bd73d5faddffd05e251ce96d` and `fix/59-queued-task-type-none` at `a82e7866b13eb21e8f8a0dcc6b0f212296b9842c`. The five later fix branches remained absent.
- The clean local integration branch remained `fix/empty-sequence-boundaries` at `61a89a56edd7bb9c6c6923fc9087f00c81c99da8`.
- The exact ToastCore database again hashed to `8013B703C61A9894866F836F2B934EADA7118CDF0B3CD56181E4BF9205B2F557`.

### Tried

- Started Banteng through Gradle on the clean six-fix integration branch with the exact database, a distinct diagnostic checkpoint, `--promote-numbers=false`, and `127.0.0.1:17785`.
- Opened a real TCP connection, read the welcome banner, sent `connect wizard`, read the login response, sent `help`, and read the help index.
- Interrupted the Windows Gradle batch process after proof and answered its termination prompt.

### Worked

- The server listened on port 17785.
- The socket received a 247-byte banner containing `Welcome to the ToastCore database.`.
- `connect wizard` returned the 19-byte `*** Connected ***` response.
- `help` returned the complete 800-byte help index with its expected first and final lines.
- Cleanup readback showed port 17785 no longer listening, no diagnostic checkpoint, and a clean integration worktree.

### Did not work

- The complete working combination is not present on remote `master`.
- Draft PR #65 contains only the first queued-task fix; it does not contain the later DNS, regex, connection-timer, `isa`, or empty-sequence fixes.
- The five later fixes cannot be described as published or mergeable PRs because their remote branches and PRs do not exist.

### Current blocker

- Functionality is locally proven, but repository delivery is incomplete.
- PR #65 needs explicit ready authorization before its normal merge.
- Issues #60 through #64 need exact PR drafts shown and approved before their clean branches can be pushed or PRs created.

### Next action

- Decide whether to ready and normally merge #65 first.
- Then prepare exact publication metadata for the remaining five fixes in issue dependency order so the complete working combination can be delivered without collapsing commit boundaries.

## Completion campaign checkpoint 2026-08-20

### Current findings and observations

- Q made the completion scope explicit: the launcher must work against ToastCore from merged `master`, every fix must be PR'd and normally merged, and issues #59 through #64 must actually be closed.
- The active delivery plan is: merge #65; publish #60-#64 PRs; shepherd exact-head CI and normally merge in dependency order; deliver the batch launcher if it is not already covered; run the merged launcher against the exact database; verify final tracker state.
- A fresh pre-merge audit of PR #65 confirmed one expected commit, three expected files, `Closes #59`, six successful checks, clean/mergeable state, a clean worktree, and no `git diff --check` output.
- Q's explicit full-completion instruction authorized moving PR #65 from draft to ready and normally merging it.
- Executed exactly `gh pr ready 65` followed by `gh pr merge 65 --merge`; no squash or rebase merge was used.
- PR #65 is now `MERGED` with merge commit `7134dc070adbb4e28b861c644da4af1ce606fb71`.
- The merge commit has exactly two parents: prior `master` `df08c69535d0aaf6bd73d5faddffd05e251ce96d` and feature commit `a82e7866b13eb21e8f8a0dcc6b0f212296b9842c`.
- The feature commit is an ancestor of remote `master`.
- Issue #59 closed automatically at `2026-08-21T03:21:08Z`.

### State

- PR #65 merged normally and issue #59 closed.
- Issues #60 through #64 remain open and their five clean publication branches remain local pending the next publication step.
- The user's `run-banteng-toastcore.bat` remains untracked and untouched in the primary checkout.

### Blocker

- No external blocker. Remaining work is publication, CI, sequential normal merges, launcher delivery, and merged-head runtime proof.

### Next action

- Rebase-equivalent history rewriting is not needed: compare the clean #60 branch against the new remote `master`, resolve only if Git reports a real conflict, publish its exact PR metadata, wait for exact-head CI, then normally merge and verify #60 closure.
- Repeat in dependency order for #61 through #64, preserving each feature commit and using normal merge commits.
- No remote branch, issue, or PR has been created by this campaign except the already-existing issue #59.

### Next action

- Await the literal publication authorization. Then create the five shown issues, push `fix/59-queued-task-type-none`, and create the exact shown draft PR for #59 only, respecting the GitHub skill's one-PR limit.
- Read back every created issue and the PR, verify labels/body/base/head/state, and leave all merges untouched pending separate normal-merge authorization.

## Fix-forward investigation checkpoint 2026-08-20

### Current findings and observations

- Q explicitly rejected rollback and required a diagnosis of what differs between the tests run for PR #72 and the tests run after it landed before any further mutation.
- The previously interrupted push/create command completed far enough to publish branch `revert/pr-72-notify-buffering` at `2c4415ea3cdcb333b1ffa33749853ed58434a8b2` and open draft PR #73, `Revert no-flush notification buffering`.
- PR #73 remains open and draft. It is not merged. Its CI run `32446202094` started automatically. No further action has been taken on it after Q's instruction.
- PR #72 merged normally as `6d3e175b75a22d576b55f12e441e57138f5e1ccd`; its isolated PR run `32445208030` was green at head `958574a6ef278ae9a33aab302ca669265f33c1c1`.
- The merged-master push run `32445597515` was also green at `6d3e175b75a22d576b55f12e441e57138f5e1ccd`.
- PR #66's combined run `32445696735` failed only `Static analysis & tests` at head `0ca52a862a7b21da737b9dd18ddaf461f9468141`; its conformance, CodeQL, dependency review, and workflow analysis jobs passed.
- The CI workflow blob is identical across pre-#72 master, PR #72, merged #72 master, and the combined PR #66 head. The Java job runs the same `./gradlew --no-daemon clean check` command in each context.
- The material difference is source/test inventory: PR #66 adds `MooRuntimeTest.connectionNameLookupRejectsARewriteAfterTheConnectionCloses`, which is absent from PR #72 and merged master. That integration test closes the authenticated connection during asynchronous host work, reuses the negative connection ID, and then expects the original task's ordinary `notify(player, ...)` result and framing output.
- PR #72's `notifyLine` now performs `connections().connectionId(target)` before the ordinary current-player `BuiltinResult.Output` path. After the original connection closes, the player mapping is gone, so it returns integer `1` and drops the task output. The PR #66 integration test exposes that behavior.

### Tried

- Performed only read-only Git/GitHub checks after Q's instruction: PR/run metadata, workflow blob identities, test-file deltas, current implementation ordering, and worktree status.
- Did not edit code, update branches, close/open issues, close PR #73, ready a PR, or merge anything.

### Worked

- Established that GitHub did not run a smaller or different Gradle test command for PR #72 or merged master. Both were green because neither tree contained the later DNS race regression test.
- Established that PR #66 ran the same workflow with an expanded test tree and exposed a real cross-feature acceptance regression.

### Did not work

- The attempted rollback publication was not actually cancelled before remote mutation; draft PR #73 exists and its automatic CI is running.
- PR #72's own tests covered no-flush buffering and ordinary connected-player output, but did not cover ordinary task output after the originating connection disappears during suspended host work.

### Current blocker

- No technical uncertainty remains about the test discrepancy, but the exact synthetic checkout SHAs and executed test counts still need to be extracted from run logs for a complete evidence table.
- Q requires fix-forward, so PR #73 must not be merged. Any cleanup of the now-unwanted draft PR will happen only after the requested diagnosis is presented.

### Next action

- Finish the read-only comparison using the two CI logs/test XML summaries, then report the exact difference with raw output.
- After that evidence is complete, close the unwanted draft PR #73 without merging it, create an isolated fix-forward branch from current master, add a base-level regression test for ordinary current-player output after disconnection, and move that output path before live connection lookup.

## Fix-forward implementation checkpoint 2026-08-20

### Current findings and observations

- Exact artifact counts established the discrepancy: PR #72 and merged master each ran 573 root Java tests with zero failures; combined PR #66 ran 577, adding exactly four connection-name-lookup tests.
- The only combined failure was `MooRuntimeTest.connectionNameLookupRejectsARewriteAfterTheConnectionCloses`.
- GitHub PR #72 checked out synthetic merge `d22144c` (`958574a` into `7134dc0`); PR #66 checked out synthetic merge `0f3a2c7` (`0ca52a8` into `6d3e175`). All relevant commits use the identical CI workflow blob `d3208905fe2f4f11ce3932e6b2ec5af1023a5435`.
- Draft rollback PR #73 was closed unmerged. Readback: `state=CLOSED`, `mergedAt=null`. Its branch remains but will not be used or merged.
- Created isolated worktree `C:\Users\Q\code\banteng-notify-after-disconnect` on branch `fix/notify-after-disconnect` from current remote master `6d3e175`.

### Tried

- Added a base-level regression test `BuiltinCatalogTest.notifyPreservesOrdinaryCurrentPlayerOutputAfterConnectionCloses` before changing production code.
- The first Gradle command accidentally selected matching tests in every subproject and failed in `:errorprone-checks:test` with `No tests found`; it did not execute the Banteng product test.
- Reran the root project task explicitly with `:test`.
- Moved the ordinary current-player `BuiltinResult.Output` path before the live connection lookup in `BuiltinCatalog.notifyLine`.
- Retained the offline-other-target behavior by changing that existing assertion to use wizard programmer object `#1` targeting disconnected object `#99`.

### Worked

- Red proof: root `:test` executed exactly the new regression and failed at `BuiltinCatalogTest.java:1174`, `1 test completed, 1 failed`.
- Green proof: root `:test --tests world.mongoose.banteng.builtin.BuiltinCatalogTest.notify*` completed successfully in 26 seconds after the production change.
- Fix scope is currently two files: `BuiltinCatalog.java` and `BuiltinCatalogTest.java`.

### Did not work

- The unqualified multi-project Gradle test filter was not a valid focused command because subprojects with no matching test treat the filter as an error.

### Current blocker

- The repair still needs direct proof against PR #66's exact DNS race test, then full local gates and diff review before any commit or publication.

### Next action

- Build a temporary combined validation from the fix-forward branch plus PR #66 feature history without pushing it, run the exact DNS race and focused notify tests, then run proportionate full gates on the clean base repair.

## Forward repair publication checkpoint 2026-08-20

### Current findings and observations

- Temporary detached combined validation worktree: `C:\Users\Q\code\banteng-notify-combined` at PR #66 head `0ca52a8` with only the forward `BuiltinCatalog.notifyLine` ordering change applied locally.
- Exact previously failing DNS race passed there: `MooRuntimeTest.connectionNameLookupRejectsARewriteAfterTheConnectionCloses`, `BUILD SUCCESSFUL in 15s`.
- Base repair diff audit showed exactly two files and no `git diff --check` errors.
- Base repair commit: `64f4acb14f1f1f1d289c3e6c03bd4f8b6165af8a`, `fix: preserve notify output after disconnect`.
- Local Windows `clean check` ran 574 tests and failed six platform-dependent baseline cases: two documented-server startup cases, native `libcrypt.so.1`, two resulting `NativeCalls` initialization failures, and missing `/proc`-style memory usage data. This full local gate is red and was disclosed in the PR body; it is not being called a pass.
- Created bug issue #74, `Preserve ordinary notify output after a connection closes`, with the exact shown body and acceptance criteria.
- Published draft PR #75, `Preserve ordinary notify output after disconnect`, base `master`, head `fix/notify-after-disconnect`, label `bug`, body `Closes #74`.
- PR #75 readback shows one commit, two files, head `64f4acb14f1f1f1d289c3e6c03bd4f8b6165af8a`, draft/open, mergeable.
- Exact-head CI run `32446927804` is in progress. Workflow static analysis and dependency review have passed. Static analysis/tests, managed conformance, and CodeQL are actively running.

### Worked

- The missing acceptance condition is pinned on the base repair and proven against the original combined failure without importing the repair into PR #66.
- Rollback draft PR #73 is closed and unmerged.

### Current blocker

- PR #75 cannot be readied or merged until every required exact-head check reaches a successful terminal state and the final merge audit is repeated.

### Next action

- Continue watching exact-head CI run `32446927804`; if green, verify files, commit, issue scope, closing phrase, head SHA, mergeability, and exact normal merge command before readying and merging PR #75.

## Forward repair and DNS merge checkpoint 2026-08-20

### Completed

- PR #75 exact-head CI run `32446927804` completed with all six check runs successful.
- Final PR #75 audit confirmed one commit, two expected files, `Closes #74`, clean/mergeable state, and exact normal merge command `gh pr merge 75 --merge`.
- PR #75 merged normally as `178f4e6a2ce41f6839474204b781b070237f29ea` with parents `6d3e175b75a22d576b55f12e441e57138f5e1ccd` and `64f4acb14f1f1f1d289c3e6c03bd4f8b6165af8a`.
- Issue #74 closed automatically. The forward-fix feature commit is an ancestor of remote master.
- Merged repaired master into PR #66 using an ordinary local merge commit `3a18bd1f7755fc72027cc6909c25cd8e93b1b0a0`, parents `0ca52a8` and `178f4e6`.
- Focused local PR #66 validation passed all notify and connection-name-lookup tests in 22 seconds.
- PR #66 exact-head CI run `32447363704` completed with all six check runs successful, including static/tests in 4m01s and managed conformance in 4m33s.
- Final PR #66 audit confirmed the expected eight feature files, feature commit `bc088f6`, `Closes #60`, clean/mergeable state, and exact normal merge command `gh pr merge 66 --merge`.
- PR #66 merged normally as `447ae74dd0db4c7ee55f65f2eda5a5656ae614f1` with parents `178f4e6a2ce41f6839474204b781b070237f29ea` and `3a18bd1f7755fc72027cc6909c25cd8e93b1b0a0`.
- Issue #60 closed automatically. Feature commit `bc088f6` is an ancestor of remote master.

### Current state

- Valid merged fixes: PR #65, PR #72 plus forward repair PR #75, and PR #66.
- Closed campaign issues: #59, #60, #71, #74.
- PRs #67 through #70 remain open/draft and unmerged. The launcher remains untracked in the primary checkout.
- Rollback PR #73 remains closed and unmerged.

### Current blocker

- No known technical blocker. Each remaining PR must be updated from the latest master, tested on its exact head, audited, and normally merged in dependency order.

### Next action

- Merge current remote master into PR #67 with a normal merge commit, run focused checks, push, wait for exact-head CI, then audit and normally merge it before moving to PR #68.

## Regex merge and timer update checkpoint 2026-08-20

### Completed

- Updating PR #67 from master produced additive conflicts in `BuiltinCatalog.java` and `BuiltinCatalogTest.java`; resolution retained both regex and connection-name-lookup catalog entries, methods, and tests.
- Focused regex plus DNS tests passed in 37 seconds with no conflict markers.
- PR #67 update commit `f54d9eddb4e143917874eeeccd57e73597bc8b98` is a normal merge with parents `4a9fa4f` and `447ae74`.
- PR #67 exact-head CI run `32447816148` completed with all six checks successful.
- Final PR #67 audit showed the expected four regex files, `Closes #61`, clean/mergeable state, and exact normal merge command `gh pr merge 67 --merge`.
- PR #67 merged normally as `548126aefe0c915e07d1644613edcc2a06dee8ca` with parents `447ae74` and `f54d9ed`; issue #61 closed automatically; feature commit `4a9fa4f` is an ancestor of master.
- Updating PR #68 from this master produced additive conflicts in `BuiltinHosts`, `MooRuntime`, `BuiltinCatalogTest`, and `MooRuntimeTest`.
- Resolution retained both runtime dependencies (`LongSupplier nanoTime` and `ConnectionNameResolver`) and all three host handlers (`idleSeconds`, `connectedSeconds`, `connectionNameLookup`).
- Focused timer, DNS race, and regex tests passed together in 38 seconds with no conflict markers.
- PR #68 update commit `b2d681e079114d12201b314cd84c15c82dc6df8b` is a normal merge with parents `3166ee4` and `548126a`.
- PR #68 diff against current master remains the six expected timer feature/test files and has no diff-check errors. The head is pushed.

### Current state

- PR #68 is open/draft at `b2d681e`; its exact-head CI is about to be observed.
- PRs #69 and #70 remain open/draft and unmerged.

### Next action

- Wait for PR #68 exact-head CI, audit and normally merge if green, then update PR #69 from the resulting master.

## PR #68 exact-head failure investigation checkpoint 2026-08-20

### Observed

- PR #68 exact-head CI run `32448325249` checked out head `b2d681e079114d12201b314cd84c15c82dc6df8b`.
- Workflow static analysis, dependency review, and both CodeQL check runs passed.
- The static-analysis-and-tests job failed only in `schedulerStress`: `ConcurrentSchedulerStressTest.saturationConflictsAndRetriesRemainBoundedAndEventuallyProgress(Path)` timed out at line 67. The preceding `clean check` portion completed successfully.
- Managed conformance ran the same suite commit used by green PR #67, `6528b8cf6d72c065c722469b070fea16d626e602`, and failed four connection-state assertions: buffered output stayed at zero after notify; `keep-alive` returned `E_INVARG`; flush-command read back `.testflush` instead of `.myroundtrip`; and the keep-alive option map update returned `E_INVARG`. The remaining conformance result was 9694 passed and 1913 skipped.
- The PR diff against merged master still contains only the six timer feature/test files. Focused timer, DNS, and regex tests had passed locally before publication.

### Tried

- Confirmed the conformance failure is not explained by a moving conformance branch: PR #67 and PR #68 used the identical conformance commit.
- Began tracing `SetConnectionOption`, staged notification requests, `publishVmState`, `publishAttempt`, and `ConnectionState.sameState` to identify an interaction introduced by the timer state.

### Worked

- The failure is narrowed to exact-head integration behavior rather than the earlier additive merge resolution or a conformance-suite revision change.

### Did not work

- The first broad `rg` command used a PowerShell-incompatible `test*` path argument. It still returned the relevant matches under `src`, but the trace needs narrower source reads.

### Current blocker

- PR #68 cannot be merged. Its exact head has one scheduler-stress timeout and four reproducible managed-conformance failures whose common connection-state publication cause is not yet proven.

### Next action

- Read the exact option/notification publication and attempt-copy code, reproduce the conformance interaction with a local regression test, then implement and verify the smallest forward fix on PR #68.

## PR versus master test-selection diagnosis 2026-08-20

### Findings

- Downloaded and parsed conformance artifacts from green PR #67 run `32447816148` and red PR #68 run `32448325249`.
- PR #67: `11611` collected, `0` failures, `2055` skipped. `connected_seconds_returns_int`, `buffered_output_length_increases_after_notify`, `set_connection_option_keep_alive_accepted`, and `connection_option_flush_command_roundtrip` were all skipped with the exact reason `Requires builtin: connected_seconds`.
- PR #68: the same `11611` tests were collected, but only `1913` were skipped. `connected_seconds_returns_int` passed, while the buffered-output test and three connection-option tests failed.
- The `server_admin.yaml` suite has a suite-level `requires.builtins` list containing both `connected_seconds` and `idle_seconds`. PR #67 lacks those two builtins, so the complete suite is skipped. PR #68 adds both and therefore activates 142 previously skipped cases.
- Both runs used conformance commit `6528b8cf6d72c065c722469b070fea16d626e602`; the difference is target capability admission, not a changed external suite.
- The newly exposed gaps predate the timer PR: `keep-alive` is absent from Banteng's closed connection-option enum; `set_connection_option` defers mutations until VM completion, so a same-task `connection_options` read sees the old flush command; and normal `notify` flushes synchronously, leaving `buffered_output_length` at zero.

### Current state

- PR #68 remains unchanged at `b2d681e079114d12201b314cd84c15c82dc6df8b`, open/draft and unmergeable by policy because exact-head CI is red.
- Created isolated worktree `C:\Users\Q\code\banteng-connection-options` on local branch `fix/toast-connection-options` from current merged master `548126aefe0c915e07d1644613edcc2a06dee8ca`.
- No rollback is planned or authorized. No new issue, PR, commit, or push has yet been made for these exposed gaps.

### Blocker

- The timer PR cannot go green until the newly activated connection-option and buffered-output contracts are repaired on master without expanding PR #68's issue scope.

### Next action

- Add focused red tests and implement a narrow connection-option base repair (Toast `keep-alive` plus read-your-writes), publish its exact proposed issue/PR metadata for Q to read, then open and normally merge it after exact-head CI. Handle buffered-output semantics as a separate base repair before updating PR #68.

## Connection-option forward-repair implementation checkpoint 2026-08-20

### Observed and proven

- Added two focused runtime tests on `fix/toast-connection-options`.
- Red proof for same-task flush-command readback: expected `.myroundtrip`, got the prior empty value.
- Red proof for Toast keep-alive forms: the runtime returned `E_INVARG` for `set_connection_option(player, "keep-alive", 1)`.
- After the implementation, the focused result XML reports `tests=2`, `failures=0`, `errors=0`; both new tests pass.

### Implemented locally

- Added a runtime-owned connection-option setter host boundary so production calls mutate only the current isolated attempt and therefore provide read-your-writes without performing transport effects before commit.
- Preserved the existing deferred `BuiltinResult.SetConnectionOption` default for standalone catalog/VM composition.
- Added Toast `keep-alive` integer/map validation, default state (`enabled=0`, `idle=300`, `interval=120`, `count=5`), state copying/equality/publication, and a commit-time listener effect.
- Added portable `Socket.setKeepAlive` application in `MooServer` while retaining the requested idle/interval/count values in runtime state for `connection_options` readback.
- Extended the keep-alive test to verify the state survives publication into a subsequent command.

### Did not work

- Attempted to run `spotlessApply`, but this repository has no Spotless task. Gradle failed during task selection before running the requested tests. No source changes were made by that failed command.

### Current state

- Five tracked files are modified in the isolated connection-options worktree; nothing is committed, pushed, or published.
- The source previously passed the two new focused tests before the added persistence assertion. The persistence assertion and existing catalog regression still need a fresh run.

### Blocker

- Formatting/check task names must be taken from this repository rather than assumed. The complete scoped regression set and diff audit are still pending.

### Next action

- Inspect available formatting/check tasks, run the focused runtime and catalog regressions, add listener-level keep-alive coverage if needed, then run the repository's actual static/test gates before preparing exact issue/PR metadata.

## Connection-options publication and buffered-output repair checkpoint 2026-08-20

### Connection options

- Focused current-source verification passed: all `MooRuntimeTest`, all `MooServerTest`, and the existing catalog connection-option authorization test completed with `BUILD SUCCESSFUL in 11s`.
- A broader 132-test runtime/server/catalog selection failed only four known Windows platform cases: native crypt initialization, its dependent usage/numbers cases, and absent `/proc` memory data. It is not being called green.
- Published issue #76 with the exact shown title/body/bug label: `Complete Toast keep-alive and connection-option read-your-writes`.
- Commit `a9971b997d5e115f6b0b8c1b4cec39b121445777`, `fix: complete Toast connection options`, contains exactly five implementation/test files.
- Published draft PR #77 with the exact shown title/body/bug label and `Closes #76`. Readback confirms the expected commit, five files, clean mergeability, and exact head SHA.
- Exact-head CI run `32449782776` has started; it was still in progress at the last readback.

### Buffered-output diagnosis and local repair

- Toast source proves `notify` calls `network_send_line(..., !no_flush, ...)`, whose `enqueue_output` always increments `output_length`; the `flush_ok` argument only authorizes overflow handling and does not synchronously empty the queue.
- Banteng's attempt-local projection incorrectly reset queued bytes to zero for every ordinary notification (`noFlush == false`). That is why the newly admitted conformance assertion saw no increase.
- Created isolated worktree `C:\Users\Q\code\banteng-buffered-notify` on branch `fix/buffered-notify-projection` from merged master `548126a`.
- Added regression `VmSnapshotTest.ordinaryNotificationsRemainVisibleInAttemptLocalBufferedOutput`.
- Red proof: expected base 7 plus `abc` plus CRLF = 12, but current code returned 0.
- Implemented the one-branch repair by removing the incorrect ordinary-notify reset from `VmState.stagedBufferedOutputLength`.

### Current blocker

- The buffered-output test has not yet been rerun green after the one-line implementation. PR #77 exact-head CI is still pending.

### Next action

- Rerun the buffered-output regression and relevant VM/runtime tests, then publish exact issue/PR metadata for that separate base repair. Continue watching PR #77 and normally merge it only after the full exact-head audit is green.

## Base repair merge and buffered projection publication checkpoint 2026-08-20

### Completed

- PR #77 exact-head CI run `32449782776` completed with all six checks successful. Managed conformance took 4m40s and static analysis/tests took 4m53s.
- Final PR #77 audit confirmed one feature commit, five expected connection-option files, `Closes #76`, `CLEAN`/`MERGEABLE`, and exact normal merge command `gh pr merge 77 --merge`.
- PR #77 merged normally as `d746d8a95544b90ebe45d5697e2331995627ceab` with parents `548126aefe0c915e07d1644613edcc2a06dee8ca` and `a9971b997d5e115f6b0b8c1b4cec39b121445777`.
- Issue #76 closed automatically; feature commit `a9971b9` is an ancestor of remote master.
- Buffered-output regression plus all `VmSnapshotTest` and `MooServerTest` passed in 21s.
- Published issue #78 with exact shown title/body/bug label: `Keep ordinary notify bytes in attempt-local buffered output`.
- Published commit `28f96a7201848841bf2a43454d1190a2c5456886`, `fix: preserve staged notification bytes`, containing only `VmState.java` and `VmSnapshotTest.java`.
- Published draft PR #79 with exact shown title/body/bug label and `Closes #78`.
- Merged repaired master into PR #79 using ordinary merge commit `82aed393c0846245f9492ac7c2fdee543d3514d2`, parents `28f96a7` and `d746d8a`.
- Combined local results after that merge: `VmSnapshotTest` 13/0/0, `MooServerTest` 3/0/0, and the two connection-option runtime regressions 2/0/0. Diff against master remains exactly the two buffered-projection files.

### Current state

- PR #79 head `82aed393c0846245f9492ac7c2fdee543d3514d2` is pushed, clean, and mergeable.
- New exact-head CI run `32450187362` is pending. The obsolete prior-head run `32449989224` was still finishing/canceling when last checked.
- PR #68 remains open/draft and unchanged while these base repairs land.

### Current blocker

- PR #79 needs terminal successful CI on exact head `82aed39` before its final audit and normal merge.

### Next action

- Watch run `32450187362`; on green, audit and normally merge PR #79, then merge resulting master into PR #68 and rerun the now-admitted full conformance suite.

## Buffered repair merge and PR #68 refresh checkpoint 2026-08-20

### Completed

- PR #79 exact-head CI run `32450187362` completed successfully: dependency review, both CodeQL checks, workflow static analysis, managed MOO conformance, and static analysis/tests all passed.
- Final PR #79 audit confirmed only `VmState.java` and `VmSnapshotTest.java`, the feature commit plus ordinary master merge, `Closes #78`, and exact merge command `gh pr merge 79 --merge`.
- PR #79 merged normally as `46dbde8aaf5430d318f380651f97ccdbf93e0a3f`, with parents `d746d8a95544b90ebe45d5697e2331995627ceab` and `82aed393c0846245f9492ac7c2fdee543d3514d2`.
- Issue #78 closed automatically and feature commit `28f96a7` is an ancestor of remote master.
- Merged repaired master into PR #68. Resolved the two expected additive conflicts by retaining timer handlers and the transactional connection-option setter together.
- Combined root test selection passed: `MooRuntimeTest` 28, `MooServerTest` 3, and `VmSnapshotTest` 13, with zero failures, errors, or skips.
- Created ordinary refresh merge commit `4da0ca782c76c019cec1d69dfb043721a3a82b95`, with parents `b2d681e079114d12201b314cd84c15c82dc6df8b` and `46dbde8aaf5430d318f380651f97ccdbf93e0a3f`, and pushed it to PR #68.

### Did not work

- Running filtered tests through the aggregate `test` task applied the filters to auxiliary projects and failed because those projects contain none of the selected root test classes. Rerunning the same filters against the explicit root `:test` task succeeded.

### Current blocker

- GitHub had not yet updated PR #68's displayed head or started exact-head CI immediately after the push.

### Next action

- Refresh PR #68 until head `4da0ca7` and its CI run appear, then require terminal exact-head success, audit its six-file timer scope and `Closes #62`, and normally merge it.

## Timer merge and PR #69 refresh checkpoint 2026-08-20

### Completed

- PR #68 exact-head CI run `32450703629` completed successfully, including managed MOO conformance after both base repairs and Java static analysis/tests.
- Final PR #68 audit confirmed six timer/composition files, the feature commit plus two ordinary master merges, `Closes #62`, mergeability, and exact command `gh pr merge 68 --merge`.
- PR #68 merged normally as `67a3b7a2cf977f5e2005c0f56de9e68897b44403`, with parents `46dbde8aaf5430d318f380651f97ccdbf93e0a3f` and `4da0ca782c76c019cec1d69dfb043721a3a82b95`.
- Issue #62 closed automatically and timer feature commit `3166ee4` is an ancestor of remote master.
- Merged current master into PR #69. The sole conflict was the builtin-name expectation; it was resolved additively and alphabetically with both `idle_seconds` and `isa`.
- The exact `isa` regression and all composition tests passed: 5 tests, zero failures/errors/skips.
- PR #69 refreshed diff remains exactly `BuiltinCatalog.java` and `BuiltinCatalogTest.java`.
- Created and pushed ordinary refresh merge commit `f693c4f09180a19a06e218b93c33d42842786ee0`, with parents `1debd5f2d8427f6dbd87826b74bb3649d1d4d2a8` and `67a3b7a2cf977f5e2005c0f56de9e68897b44403`.

### Did not work

- A broad local `BuiltinCatalogTest` run completed 108 tests with four failures caused by this Windows environment: missing `libcrypt.so.1`, dependent usage/numbers initialization, and absent `/proc` memory data. This is separated from the green feature proof and will be covered by Linux CI.

### Current blocker

- PR #69 exact-head CI run `32451218802` is in progress on head `f693c4f`.

### Next action

- Require terminal success from run `32451218802`, audit PR #69 files/commits/issue/closing phrase, normally merge it, then refresh PR #70 from resulting master.

## Isa merge, PR #70 refresh, and launcher issue checkpoint 2026-08-20

### Completed

- PR #69 exact-head CI run `32451218802` completed successfully, including all Linux catalog/static tests and managed MOO conformance.
- Final PR #69 audit confirmed exactly `BuiltinCatalog.java` and `BuiltinCatalogTest.java`, the feature commit plus ordinary master merge, `Closes #63`, mergeability, and exact command `gh pr merge 69 --merge`.
- PR #69 merged normally as `d85de9b6adcf75e1b8e256e87dab76c025d5e519`, with parents `67a3b7a2cf977f5e2005c0f56de9e68897b44403` and `f693c4f09180a19a06e218b93c33d42842786ee0`.
- Issue #63 closed automatically and feature commit `1debd5f` is an ancestor of remote master.
- Merged current master into PR #70 without conflicts using ordinary merge commit `675efbcbcfdb7fe8b7c0c80aa17f573f66e72d90`, with parents `1a5115c5c89868f7b6faed077f9bf1ac535e85d6` and `d85de9b6adcf75e1b8e256e87dab76c025d5e519`.
- Full `MooVmTest` passed locally: 140 tests, zero failures/errors/skips. PR #70's diff remains exactly `IndexOps.java` and `MooVmTest.java`.
- Pushed PR #70 exact head `675efbc`; CI run `32451683446` is in progress.
- Showed the exact launcher issue and PR metadata to the user, then published enhancement issue #80, `Add a one-command ToastCore Windows launcher`. Readback confirmed exact title/body/label.

### Current blocker

- PR #70 exact-head CI must finish successfully before final audit and normal merge. The launcher branch must start from the resulting master.

### Next action

- Finish PR #70 CI/audit/normal merge and issue #64 closure. Then create an isolated launcher worktree from final master, copy the already reviewed batch content, test missing-database and real ToastCore paths, publish PR closing #80, and normally merge after exact-head CI.

## Campaign merge and launcher publication checkpoint 2026-08-20

### Completed

- PR #70 exact-head CI run `32451683446` completed successfully, including managed MOO conformance and Java static analysis/tests.
- Final PR #70 audit confirmed exactly `IndexOps.java` and `MooVmTest.java`, the feature commit plus ordinary master merge, `Closes #64`, mergeability, and exact command `gh pr merge 70 --merge`.
- PR #70 merged normally as `ecc255cb1bef84a7b8617abe71d571618ebec03a`, with parents `d85de9b6adcf75e1b8e256e87dab76c025d5e519` and `675efbcbcfdb7fe8b7c0c80aa17f573f66e72d90`.
- Issue #64 closed automatically and feature commit `1a5115c` is an ancestor of remote master.
- Created isolated launcher worktree `C:\Users\Q\code\banteng-launcher` from exact final campaign master `ecc255c`, preserving the dirty primary checkout and original untracked launcher.
- Added only `run-banteng-toastcore.bat` with the already reviewed content.
- Missing-database proof returned the clear path error and exit code 66.
- Real launcher proof against database SHA-256 `8013B703C61A9894866F836F2B934EADA7118CDF0B3CD56181E4BF9205B2F557` returned a 247-byte welcome banner, 19-byte connected response, and 800-byte help index.
- Proof cleanup left port 7777 closed, the source database hash unchanged, and no `.new` checkpoint.
- The first proof harness attempt launched and cleaned up correctly but failed while decoding because it wrapped `byte[]` in `object[]`; the corrected harness passed.
- Committed single-file launcher change as `85ecb30028347aa434abf62391d22a4deccd5d56`, `feat: add ToastCore Windows launcher`, and pushed branch `feat/toastcore-windows-launcher`.
- Published draft PR #81 with exact shown title/body/enhancement label, one commit, one file, and `Closes #80`.

### Current blocker

- PR #81 exact-head CI run `32452293482` is in progress on `85ecb30`.

### Next action

- Require terminal success from PR #81 CI, audit the one-file scope/one commit/closing phrase, normally merge with `gh pr merge 81 --merge`, verify #80 closure and two-parent ancestry, then rerun the launcher from merged master and audit all campaign PRs/issues.

## Final merged completion checkpoint 2026-08-21

### Completed

- PR #81 exact-head CI run `32452293482` completed successfully: dependency review, workflow static analysis, Java static analysis/tests, CodeQL, and managed MOO conformance all passed.
- Final PR #81 audit confirmed one file, one commit, enhancement label, `Closes #80`, mergeability, and exact command `gh pr merge 81 --merge`.
- PR #81 merged normally as `2ccad0597d7a6889e1607174cd2e6513a2032542`, with parents `ecc255cb1bef84a7b8617abe71d571618ebec03a` and `85ecb30028347aa434abf62391d22a4deccd5d56`.
- Issue #80 closed automatically and launcher feature head `85ecb30` is an ancestor of remote master.
- Fast-forwarded the isolated launcher worktree to exact merged master `2ccad05` and reran the real batch launcher.
- Final merged proof returned the exact 247-byte ToastCore welcome banner, 19-byte `*** Connected ***` response, and 800-byte help index.
- Final cleanup left port 7777 closed, source database SHA-256 unchanged at `8013B703C61A9894866F836F2B934EADA7118CDF0B3CD56181E4BF9205B2F557`, and no checkpoint file.
- Final live tracker audit found zero open PRs. Campaign issues #59, #60, #61, #62, #63, #64, #71, #74, #76, #78, and #80 are all closed.
- Campaign PRs #65, #66, #67, #68, #69, #70, #72, #75, #77, #79, and #81 are all normally merged, have two-parent merge commits, and their exact heads are ancestors of master.
- Rollback PR #73 remains closed, draft, and unmerged per Q's explicit fix-forward/no-rollback instruction.
- Seven unrelated older backlog issues (#10, #11, #15, #19, #22, #25, #26) remain open and were not silently closed.
- Clean launcher worktree is usable at `C:\Users\Q\code\banteng-launcher`; the primary checkout remains intentionally untouched with Q's untracked `run-banteng-toastcore.bat` and `investigations/pr66-buffered-output.md`.

### Current blocker

- None for the ToastCore launcher campaign.

### Next action

- Run `C:\Users\Q\code\banteng-launcher\run-banteng-toastcore.bat` for the already verified merged-master setup. Handle the unrelated older issue backlog only under separate scope.
