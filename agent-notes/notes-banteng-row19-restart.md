# Banteng row 19 restarted-process checkpoint

## Current state

- Branch `master` was ahead of `origin/master` by eight commits at restart.
- The tracked worktree was clean before the row-19 authority record; only
  `notes-banteng-conformance-handoff.md` and `pyghidra_mcp_projects/` were
  untracked.
- Committed frontier is `27eb88c feat: dispatch textual out-of-band input`.
- The immutable implementation plan was read once in this restarted process.
- Active exact-convergence target remains lifecycle row 19,
  `audit_connection_name_method0_hostname`.

## Findings and observations

- The active row runs as Wizard, calls `connection_name(player, 0)`, and checks
  only that the result is a nonempty string.
- The row description is imprecise: Toast method `0` returns the full legacy
  connection string, not the bare saved hostname.
- Pinned Toast source `server.cc:2819-2850` registers one object plus an optional
  integer. No method returns the saved name, method `1` returns numeric peer IP,
  and every other integer including `0` returns the legacy string.
- Toast's legacy owner in `network.cc:1573-1633` formats inbound connections as
  `port <source-port> from <saved-name> [<destination-ip>], port
  <destination-port>`.
- Toast permits only Wizard or the queried player; missing active connections
  return `E_INVARG` after the permission check.
- Barn's generated public signature agrees, and its active inbound formatting
  agrees, but Barn omits the Wizard/self permission check and always formats
  direction as `from`.
- Barn specs conflict: `spec/server.md` identifies method `0` as legacy while
  `spec/builtins/network.md` incorrectly documents string method names.
- The exact managed Toast row passed: one selected, 11,504 deselected, in 3.27
  seconds.
- Banteng already owns all active-row metadata in `MooServer` and resolves it by
  player through `WorldTxn.connectionInfo`; no new helper, interface, adapter,
  sender, metadata representation, server method, world method, or VM operation
  is needed.
- A row-19 authority section was appended to
  `docs/reports/connection-lifecycle-authority.md`; the patch command returned
  success, but its resulting diff still needs verification after this required
  checkpoint.

## What was tried

- Verified branch and tracked state.
- Read the immutable plan.
- Read the Barn spec and implementation, pinned Toast builtin and network
  semantic owner, and the active conformance row.
- Ran the exact managed Toast row successfully.
- Appended the mandatory authority evidence section. The first patch attempt
  failed only because its EOF context wrapped differently; the second was
  accepted.

## Blocker and next step

- There is no known semantic blocker.
- First verify the authority-report diff and Git state after the checkpoint.
- Then add one focused runtime regression with synthetic connection metadata,
  prove it red at committed production code, implement the single
  `BuiltinCatalog` slice, prove focused and managed row green, run the full
  lifecycle gate, reread the plan, run clean Java 25 gates, and commit the kept
  slice before selecting the next row.

## Focused-red checkpoint

- Added the focused `MooRuntimeTest` regression with exact synthetic legacy
  metadata and expected string; production remains unchanged.
- The first focused run failed, but not at `connection_name`: it expected the
  explicit `connect Wizard` banner and instead received no output.
- The metadata-bearing `MooRuntime.openConnection(connectionId, handler,
  printMessages, info)` overload immediately executes empty login input. Using
  listener `#0` with `printMessages = false` can therefore authenticate or
  otherwise consume the later explicit login differently from the plain
  `openConnection(connectionId)` fixture path.
- This is not a valid red proof and must be corrected before production work.
- Existing `WorldTxn.openConnection(connectionId, MapValue)` owns metadata but
  cannot be called a second time for the same connection. The next step is to
  use the existing runtime overload with the same default login behavior as
  `openConnection(connectionId)` (`handler = #0`, `printMessages = true`) or
  otherwise match the established authenticated fixture exactly, then rerun
  the focused test and confirm its failure is `E_VERBNF`.
- Current blocker is only the test setup mismatch; no semantic or production
  blocker is known.

## Focused implementation checkpoint

- Corrected the test to use the established default login behavior while still
  supplying synthetic metadata: listener `#0`, `printMessages = true`.
- The corrected focused test failed for the intended committed frontier:
  expected the exact legacy string but received `{2, {E_VERBNF}}`.
- Added one inline `connection_name` case to the existing `BuiltinCatalog`
  dispatcher and added it to the existing `EXTERNAL_READ` classification.
- The case enforces 1-2 arguments, object plus optional integer types,
  wizard-or-self permission, active-connection lookup, no-method saved name,
  method `1` numeric IP, and all other integers as the legacy connection string.
- It reuses `WorldTxn.connectionInfo` and the existing encode/decode functions.
  No helper, interface, adapter, sender, server method, world method, VM
  operation, or metadata owner was added.
- The focused regression also asserts `EXTERNAL_READ` and passed after the
  production edit: `BUILD SUCCESSFUL` in 6 seconds.
- Current tracked slice consists only of the authority report,
  `BuiltinCatalog.java`, and `MooRuntimeTest.java`. This needs Git verification
  before the managed row.
- No blocker is currently known. Next step is verify the diff, run formatting if
  needed, build `installDist`, run the exact managed Banteng row, then the full
  lifecycle family if the exact row passes.

## Managed-gate checkpoint

- `spotlessApply` passed and the current Java 25 `installDist` built
  successfully.
- The first managed-row attempt inherited Java 17 in the restarted shell and
  exited before server startup with `UnsupportedClassVersionError` (class file
  69 versus runtime maximum 61). No Banteng process survived that setup error.
- Reran the identical managed row with `JAVA_HOME` and `PATH` set to the pinned
  Java 25 installation. The exact row passed: one selected, 11,504 deselected,
  in 3.35 seconds.
- Stopped only that exact run's verified Banteng PID 82524.
- The full `connection_lifecycle_toast_oracle` category then ran with `-x` and
  established the new frontier: 19 passed; row 20,
  `audit_connection_info_source_fields`, failed first with `E_VARNF`; 11,482
  were deselected; elapsed time was 44.62 seconds.
- The broader run left Banteng PID 380940, tied to temp database
  `moo_conformance_iy1v4mn5/Test.db`, on port 42374. It has been inventoried but
  not yet stopped because this checkpoint fired immediately afterward.
- Row 19 is a kept improvement. Before any row-20 work, stop only PID 380940,
  reread the immutable plan, append the row-19 verification receipt to the
  authority report, run the clean Java 25 gate, verify/stage only the three
  tracked slice files, and commit.
- No row-19 blocker is known. Row 20 is not yet an active implementation slice.

## Row-19 commit checkpoint

- Appended the final Java 25 `clean check installDist` receipt to the authority
  report; that gate passed in 13 seconds.
- Reread the immutable plan after the passing full Gradle suite, as required.
- `git diff --check` and `git diff --cached --check` both passed.
- Staged exactly the authority report, `BuiltinCatalog.java`, and
  `MooRuntimeTest.java`; both notes and `pyghidra_mcp_projects/` remained
  untracked and unstaged.
- Committed the kept row-19 slice as `35102ee feat: implement connection_name`.
- Post-commit tracked worktree is clean. Branch `master` is nine commits ahead
  of `origin/master`; only the two note files and `pyghidra_mcp_projects/` are
  untracked.
- Row 20, `audit_connection_info_source_fields`, is now the first unchecked
  lifecycle target. Its observed managed failure is `E_VARNF` after 19 prior
  rows passed. No row-20 authority work or edits have begun.
- No current blocker is known. Next step is row 20's mandatory Barn spec, Barn
  implementation, Toast source, durable-row, and managed-oracle authority gate
  before any Java design or production edit.

## Row-20 authority checkpoint

- Active row `audit_connection_info_source_fields` calls
  `connection_info(player)`, requires the eight canonical keys, and checks that
  source address is `STR` while source and destination ports are `INT`.
- Exact managed pinned Toast proof passed: one selected, 11,504 deselected, in
  3.31 seconds.
- Barn `spec/builtins/network.md:46-63` is stale: it documents unrelated
  telemetry-style fields. `spec/server.md:264-279` correctly identifies
  `source_port`, destination address fields, and `protocol` for WebSocket
  connections but is not a complete map contract.
- Barn's public generated signature is exactly one OBJ. Its implementation at
  `builtins/network.go:1161-1222` returns source address/IP/port, destination
  address/IP/port, protocol, and outbound; active inbound values agree with the
  row.
- Pinned Toast `bf_connection_info` at `src/server.cc:3031-3075`, registered at
  line 3300, enforces OBJ arity/type, active connection, wizard-or-self
  permission, and inserts exactly the eight canonical fields (plus conditional
  TLS data when built with TLS).
- Toast's network semantic owner at `src/network.cc:1635-1679` maps source
  address/IP/port, destination peer port, and protocol family (`IPv4`, `IPv6`,
  or `unknown`) directly from the network handle.
- Banteng already populated these eight fields before row 20 and its
  `connection_info` builtin already returns the metadata map. Earlier lifecycle
  rows successfully index `destination_ip` and `source_port`, so the row-20
  `E_VARNF` is not evidence of missing metadata.
- Compiler and VM inspection shows `for` compiles to `ITERATE`, and `MooVm`
  stores each loop value in the named local before executing the body. A search
  found no production or test registration for MOO type constants `STR` or
  `INT`. The strongest current causal hypothesis is therefore that the final
  comparison raises `E_VARNF` on an undefined type constant after the key loop
  succeeds.
- Two read-only subagents are still tracing authority and Banteng path. No
  row-20 tracked file has been edited.
- Current blocker before the evidence record/design is exact confirmation of
  which identifier raises `E_VARNF` and the authority contract for built-in type
  constants. Next step is receive/verify the subagent traces and isolate that
  expression without changing production.

## Row-20 focused implementation checkpoint

- The earlier connection-metadata premise was wrong: committed Banteng already
  satisfies the `connection_info` map contract. The valid row fails later on
  predefined `STR`; `INT` would fail next.
- Existing durable Banteng diagnostic `types::constant_STR_value` reproduced
  the identical `E_VARNF`. Its exact managed child PID 374132 was inventoried
  against temp database `moo_conformance_hihxyxof/Test.db` and stopped alone.
- Toast `sym_table.cc:80-116` owns predefined identifier slots;
  `eval_env.cc:75-120` fills `INT` with 0 and `STR` with 2. Barn agrees in
  `vm/environment.go:12-32`. Barn has no normative runtime-constant section.
- Live Toast durable constant proof passed: `constant_INT_value` and
  `constant_STR_value`, two selected, 11,503 deselected, in 3.51 seconds.
- Appended the required row-20 evidence section to
  `docs/reports/connection-lifecycle-authority.md`, explicitly correcting the
  premise and deferring other type constants.
- Added focused `MooVmTest` regression
  `resolvesIntegerAndStringTypeConstantsAfterLocalLookup`. It was red with VM
  outcome `ERRORED` at the committed local-miss path.
- Added only `INT` and `STR` branches beside the existing `LIST` branch in
  `MooVm.loadLocal`, preserving frame-local lookup first. No helper, interface,
  parser, compiler, server, world, builtin, or environment owner was added.
- The focused regression passed after the production edit in three seconds.
- `spotlessApply` passed after the edit.
- No current blocker is known. Next step is build `installDist`, run exact
  managed row 20, stop only its verified child, then run the full lifecycle
  category if row 20 passes.

## Row-20 managed and clean-gate checkpoint

- Current Java 25 `installDist` built successfully.
- Exact managed row 20 passed: one selected, 11,504 deselected, in 3.35
  seconds. Its verified child PID 374916 for temp database
  `moo_conformance_izmn89el/Test.db` was stopped alone.
- The full lifecycle category then passed rows 1 through 20 and failed first at
  row 21, `audit_do_login_command_argstr_original`: 20 passed, one failed,
  11,482 deselected, in 45.83 seconds.
- Row 21 expected args `{"auditlogin", "foo bar", "baz"}` from input
  `auditlogin foo\\ bar  baz`; Banteng produced
  `{"auditlogin", "foo\\", "bar", "baz"}` while preserving the exact original
  `argstr`. This is a separate unchecked parsing slice.
- The broader run's verified child PID 371528 for temp database
  `moo_conformance_9625ryrw/Test.db` was stopped alone.
- Reread the immutable plan after the substantial lifecycle gate.
- Appended the row-20 verification receipt to the authority report.
- Final Java 25 `clean check installDist` passed in 13 seconds.
- Reread the immutable plan again because `check` included the passing full
  Gradle suite.
- Added the clean-gate receipt to the evidence record. No source or test changes
  occurred after the passing clean gate.
- Row 20 is a kept slice ready for Git verification and commit. The tracked
  files should be exactly the authority report, `MooVm.java`, and
  `MooVmTest.java`; no blocker is known.
## 2026-07-15 restart checkpoint: row 21 before focused red

- This is a new Codex process. The full immutable `docs/reports/banteng-implementation-plan.md` was reread before resuming work.
- The active exact-convergence target remains lifecycle row 21, `audit_do_login_command_argstr_original`; no target switch is authorized.
- Current committed baseline is row 20 at `039fda1 feat: resolve integer and string type constants`; the tracked worktree was clean after that commit.
- Row 21 live pinned WSL Toast evidence passes. Toast returns parsed words `{"auditlogin", "foo bar", "baz"}` while preserving the exact original arg string `auditlogin foo\\ bar  baz`.
- Committed Banteng fails the managed row because `executeLogin` uses `StringTokenizer`: it returns `{"auditlogin", "foo\\", "bar", "baz"}`. Its `argstr` is already correct and must remain unchanged.
- The mandatory authority evidence for row 21 has already been appended to tracked `docs/reports/connection-lifecycle-authority.md`. It identifies Toast's ASCII-space, backslash-escape, and quote-grouping behavior and the minimal owned fix in `MooRuntime.executeLogin` only.
- A focused JUnit regression, `MooRuntimeTest.tokenizesEscapedLoginWordsWhilePreservingOriginalArgstr`, has been added but has not yet been run in this process. Production code has not been edited for row 21.
- Next required decision-changing action is to run only that focused test under Java 25 and prove that the expected login-word parsing discrepancy is red. If it fails for the intended reason, the next production action is the inline minimal scanner replacement in `executeLogin`; no helper, interface, adapter, or parser extraction is authorized.
- Current blocker: none. The checkpoint hook required this notes update before the focused red test.

## 2026-07-15 row 21 implementation and broader-gate checkpoint

- The focused regression failed for the intended reason before production changed: expected parsed words `{"auditlogin", "foo bar", "baz"}` but committed Banteng produced `{"auditlogin", "foo\\", "bar", "baz"}`; both sides preserved the same original `argstr`.
- The isolated production change replaced only the `StringTokenizer` login-word loop inside `MooRuntime.executeLogin` with an inline scanner. It uses ASCII-space boundaries outside quotes, removes quote delimiters, removes backslashes while copying the following character, drops a trailing backslash, and still passes the untouched `loginLine` as `argstr`. No helper, interface, adapter, or parser owner was added.
- `MooRuntimeTest.tokenizesEscapedLoginWordsWhilePreservingOriginalArgstr` then passed under Java 25. `spotlessApply` and `installDist` passed.
- The exact managed Banteng row `audit_do_login_command_argstr_original` passed: 1 selected, 11,504 deselected, in 4.59 seconds. Its exact remaining Banteng child PID 144040, tied to `moo_conformance_522shsd9/Test.db`, was stopped.
- The complete managed `connection_lifecycle_toast_oracle` fail-fast gate advanced the kept frontier: 21 rows passed before row 22 failed, 11,482 deselected, in 46.09 seconds.
- The new first failure is `audit_connection_hold_and_oob_options`. Its evaluated cleanup/inspection step expected success and received `E_VERBNF`. That row is the next separate target only after row 21 is documented, clean-gated, and committed.
- Immediate next required action after this checkpoint is to inventory and stop only the exact Banteng child left by the full lifecycle run. Then reread the full immutable plan, append the row 21 verification receipt to the authority report, run the final Java 25 clean gate, reread the plan again, append the clean receipt, check the diff, stage only the three row 21 tracked files, and commit the kept row 21 slice.
- Current blocker: none. Row 21 is a measured kept improvement but is not yet committed; do not begin row 22 before its ledger commit.

## 2026-07-15 row 21 pre-commit checkpoint

- After the 21-row lifecycle frontier, the full immutable implementation plan was reread. The row 21 authority report now contains the focused red/green proof, exact managed pass, lifecycle frontier, and final clean-gate receipt.
- Java 25 `clean check installDist` passed in 15 seconds. The full immutable plan was reread again immediately afterward.
- `git diff --check` passed. Final tracked diff is exactly three files: `docs/reports/connection-lifecycle-authority.md`, `src/main/java/moo/runtime/MooRuntime.java`, and `src/test/java/moo/runtime/MooRuntimeTest.java`; 156 insertions and 4 deletions.
- Only those three tracked files are staged. `git diff --cached --check` passed. Untracked `notes-banteng-conformance-handoff.md`, `notes-banteng-row19-restart.md`, and `pyghidra_mcp_projects/` are not staged.
- Immediate next required action is the row 21 kept-slice commit. After it, verify the tracked worktree is clean before beginning the separate row 22 authority gate.
- Current blocker: none. The checkpoint hook required this note update before the commit.

## 2026-07-15 row 22 authority and focused-red checkpoint

- Row 21 was committed as `9ccf880 feat: parse login command words`. Tracked and staged state was verified clean afterward; the branch became 11 commits ahead of `origin/master`.
- The active separate target is lifecycle row 22, `audit_connection_hold_and_oob_options`, lines 1860-2017 of the durable lifecycle YAML. It requires `{1,1,1,1,0}` across forced prelogin association, held ordinary input, release, OOB bypass, disabled OOB holding, and release-as-in-band.
- Mandatory authority gate is complete and recorded in tracked `docs/reports/connection-lifecycle-authority.md`:
  - Barn specs name the relevant builtins/options but do not define the combined lifecycle, negative targets, OOB/hold interaction, or exact scheduling.
  - Barn implementation derives `{1,1,1,1,0}` through its existing input processor, option state, held queue, login remap, OOB classification, and scheduler paths.
  - Pinned Toast source derives the same sequence: held in-band stays inactive; enabled OOB bypasses hold; disabled OOB remains inactive while held and is converted to in-band on dequeue after release.
  - The exact managed row passed pinned WSL Toast `aecc51e9449c6e7c95272f0f044b5ba38948459e`: 1 selected, 11,504 deselected, in 3.66 seconds.
- Banteng trace corrected the earlier hunch: `set_connection_option` is already registered, but `force_input` is absent and is the first `E_VERBNF`. `disable-oob` is also absent. Existing `ConnectionState.pendingInput`, login mapping, OOB hook, listener control, and VM effect boundary are the owners to extend; no second queue, helper, interface, adapter, or sender is needed.
- Frozen minimal design: add a concrete two-argument forced-input deferred effect through `BuiltinCatalog.Result`, `VmState`, `MooVm`, and the existing `MooRuntime` VM boundary; resolve negative connection and positive attached-player targets through existing maps; extend existing connection option/state with `disable-oob`; classify enabled textual OOB before hold; replay the existing held FIFO on true-to-false hold transition while disabled-OOB remains set. `at_front` and adjacent option/transport surfaces remain out of scope.
- The exact managed Banteng row was proven red on committed `9ccf880`: 1 failed, 11,504 deselected, in 3.94 seconds, with `E_VERBNF` in the first post-login forced-input step.
- The red run left exactly one inventoried Banteng child: PID 77468, tied to `moo_conformance_7e3lst7h/Test.db`. It has not yet been stopped because this checkpoint interrupted immediately after inventory.
- Next required action is to stop only PID 77468. Then inspect the existing held-input and OOB JUnit seams, add one row-owned focused regression, prove it red at `force_input`, and only then edit production.
- Current blocker: none. The checkpoint hook required this note update before stopping the identified child.

## 2026-07-15 row 22 focused JUnit red and implementation checkpoint

- The exact red-run child PID 77468 was stopped after the previous checkpoint.
- Existing JUnit seams were inspected: `flushesHeldPendingInputWithExactFifoFeedbackBeforeRelease` owns the current held FIFO and option setup; `dispatchesTextualOutOfBandInputToTheConnectionListener` owns current OOB listener dispatch; zero-duration scheduler tests prove continuation mutations are visible before `executeLine` returns.
- Added one tracked focused regression, `MooRuntimeTest.holdsForcedInputAndReleasesDisabledOutOfBandInputAsInBand`, mirroring the durable row in a fresh runtime. Its first `force_input` catches `E_VERBNF` only to store that exact error, then the passing path must assert login association and `{1,1,1,1,0}`.
- The focused Java 25 test was proven red: expected `{1, 1, 1, 1, 0}` but actual was exactly `E_VERBNF`. Production was still unchanged at that proof.
- Production implementation is now authorized as one row 22 slice. A Codex subagent is editing only `BuiltinCatalog.java`, `VmState.java`, and `MooVm.java` to add the concrete two-argument `force_input` deferred-effect plumbing. It was explicitly forbidden to add helpers/interfaces/adapters/senders, implement `at_front`, edit runtime/tests/docs, or commit.
- Root owns only `MooRuntime.java` for the connection/OOB/replay behavior. First runtime read observed:
  - `executeLine` resolves current connection/player and handles prelogin first.
  - `flush-command` runs before hold.
  - `holdInput` currently queues every line before the existing textual OOB dispatch, so enabled OOB cannot bypass hold.
  - existing `pendingInput` is the only required FIFO.
  - existing OOB dispatch already calls the listener's `do_out_of_band_command` with parsed words and exact line.
- Next required action after this checkpoint is to inspect the existing option-application/effect-publication and connection-state blocks, wait for the subagent's exact forced-input API contract, then make the smallest `MooRuntime` edit. No production compile should run until both halves are present.
- Current blocker: none. The checkpoint hook required this notes update before further runtime inspection.

## 2026-07-15 row 22 effect-plumbing review checkpoint

- The coding subagent completed the bounded effect plumbing without committing. Changed only tracked `BuiltinCatalog.java`, `VmState.java`, and `MooVm.java`, plus a mandatory append to the untracked handoff note.
- Concrete API now present:
  - `BuiltinCatalog.ForcedInputRequest(long target, String line)`
  - `BuiltinCatalog.Result.forcedInputRequest(): Optional<ForcedInputRequest>`
  - `VmState.drainForcedInputRequests(): List<ForcedInputRequest>` in task execution order
  - internal `VmState.stageForcedInputRequest`, invoked from the existing `MooVm` result staging path.
- `force_input` now validates exactly two arguments (object target, string line), requires wizard flag `(flags & 4) != 0`, returns integer 0 with one staged request, and is classified `DEFERRED_EFFECT`. Live-target validation and `at_front` are intentionally not in the builtin.
- The subagent's narrow verification passed under Java 25: `spotlessApply`, `compileJava`, `MooVmTest`, and scoped `git diff --check`.
- Diff review confirmed the changes reuse the existing `BuiltinCatalog.Result` effect shape and `VmState` request lists; no new helper/interface/adapter/sender/framework exists.
- The requested one-file follow-up is already visible in the shared diff: `ConnectionOption.DISABLE_OOB` and exact case-insensitive `"disable-oob"` recognition were added to the existing `setConnectionOption` switch.
- Root runtime inspection confirmed:
  - option effects are applied immediately after each `vm.execute` segment and fork continuation, before suspension scheduling;
  - `ListenerControl.writeConnection(connectionId, lines)` is the existing output path;
  - target resolution patterns already map either a connection ID or attached player to `ConnectionState`;
  - `ConnectionState` has exactly one `pendingInput` FIFO and needs only a `disableOob` boolean.
- Next required action is the single-file `MooRuntime.java` patch: import `ForcedInputRequest`; dispatch enabled textual OOB before hold; add `disableOob`; apply forced requests after connection options at both VM boundaries; replay the existing FIFO when hold clears; write forced-input output via existing listener control. Then wait for the subagent follow-up completion and run the focused test.
- Current blocker: none. The checkpoint hook required this notes update before the runtime patch.

## 2026-07-15 row 22 managed-path mismatch checkpoint

- Combined production slice was patched and the focused JUnit regression passed under Java 25 in 5 seconds. `spotlessApply` and `installDist` passed.
- Exact managed Banteng row did not yet pass. Expected `{1,1,1,1,0}`, actual `{1,0,0,1,0}` in 3.68 seconds. Login association, initial hold blocking, disabled-OOB blocking, and final not-as-OOB behavior work; held release and enabled-OOB dispatch do not work in the managed path.
- The failed managed run's sole child PID 35728, tied to `moo_conformance_9v690ldn/Test.db`, was inventoried and stopped.
- This proves the initial JUnit seam was insufficiently production-shaped even though it uses the same `MooRuntime` logic. No new production change is authorized until the managed/JUnit difference is diagnosed.
- `MooServer` construction passes itself as the concrete `ListenerControl`; each socket has a virtual-thread `handleConnection` loop that synchronously calls `runtime.executeLine` and writes returned lines. `writeConnection` only looks up the same connection's existing buffered writer and synchronously writes lines; it does not enqueue input or mutate runtime state. The runtime itself remains synchronized, so reader concurrency alone does not yet explain the two missing effects.
- Existing tests do not construct `MooRuntime(world, ListenerControl)`, so the focused regression does not cover the production constructor. The listener interface already exists; any production-shaped regression may use it directly but must not add an adapter surface.
- Current blocker: diagnosis is incomplete. The exact causal difference producing `{1,0,0,1,0}` is not yet proven. Next step is to inspect current live-row server state/logs or reproduce with the production constructor in the focused test only if that can change the diagnosis; do not patch semantics from a hunch.

## 2026-07-15 row 22 managed dispatch diagnostic checkpoint

- Temporary stderr diagnostics were added only at the existing connection-option and forced-input effect appliers, then a diagnostic `installDist` and exact managed row rerun reproduced `{1,0,0,1,0}`. These diagnostics are not part of the kept design and must be removed before any final test or commit.
- Captured server log proves effect staging and target resolution are correct:
  - positive player `#11` consistently resolves to connection `-6`;
  - `hold-input=1` applies before forced `auditq`, which is queued (`pending=1`);
  - clearing hold sees and drains that one pending line (`pending` is 0 at the next option);
  - enabled `#$#audit-oob-free` reaches forced execution with hold true, disable false, and is not queued;
  - setting `disable-oob=1` applies before forced `#$#audit-oob-held`, which is queued;
  - clearing hold drains the disabled-OOB line.
- Immediately after forced `auditq`, `audit_queue_seen` is still `{}` because it is correctly held. After enabled OOB execution, `audit_queue_oob` is still `{}` even though the line bypassed hold. The existing diagnostics did not print the property immediately after hold-release replay, but later state proves it did not become `{\"\"}`.
- Therefore the failure is no longer target resolution, request ordering, hold state, or FIFO release. It is downstream command dispatch in the managed database state.
- Plausible remaining alternatives are now narrow and both have exact inspection points: the target connection's `listenerHandler` may not resolve the row-installed OOB verb, and ordinary `auditq` may be consumed by an existing truthy `do_command` before player-verb lookup. No corrective patch is authorized until these are observed.
- The diagnostic rerun left exactly one inventoried child, PID 13096, tied to `moo_conformance_8ehhxh6n/Test.db`. It has not yet been stopped because this checkpoint interrupted immediately after inventory.
- Next required actions: stop only PID 13096; add temporary observations at existing OOB and `do_command` dispatch points; rerun the exact row once; read the captured log; then remove all temporary diagnostics and cash the finding into the smallest kept correction or revert the slice if it cannot improve the row.
- Current blocker: exact dispatch cause is not yet proven. The checkpoint hook required this note update before process cleanup.

## 2026-07-15 row 22 downstream builtin cause checkpoint

- Diagnostic PID 13096 was stopped. A final diagnostic run added observations only at existing OOB, `do_command`, and player-verb dispatch points, rebuilt, and reproduced `{1,0,0,1,0}`. Its sole child PID 32192, tied to `moo_conformance_lem667kj/Test.db`, was stopped.
- Final captured evidence proves runtime dispatch is correct:
  - hold release selects player `#11`'s `auditq` verb with the exact row source `#0.audit_queue_seen = listappend(#0.audit_queue_seen, argstr); return 1;`;
  - enabled OOB selects listener handler `#0`'s exact row `do_out_of_band_command` source using `listappend`;
  - disabled OOB release is treated as ordinary input and finds no matching player/room command, as expected.
- Despite selecting the correct two row verbs, both audit properties remain `{}`. The newly proven cause is the unimplemented `listappend` builtin inside both verb bodies, not connection targeting, queue state, scheduler order, listener selection, or player-verb lookup.
- The original focused JUnit regression masked this because it used list-splice syntax (`{@list, value}`) instead of the durable row's `listappend(list, value)`. This was a test defect.
- All temporary `System.err` diagnostics were removed from `MooRuntime.java`; `rg -n ROW22 src/main/java src/test/java` returned no matches.
- The focused regression was corrected in both verb bodies to use exact `listappend` calls. It has not yet been rerun after correction.
- The row 22 connection/force-input production work remains a measured partial improvement from `E_VERBNF` to `{1,0,0,1,0}`, but the slice is not keepable or committable until the exact row is green.
- Current blocker: `listappend` is a newly exposed semantic surface and must complete the mandatory Barn spec, Barn implementation, Toast implementation, repository-local evidence, and oracle gate before production implementation. Do not implement it from intuition or merely from the row.
- Next required action is to run the corrected focused regression and prove its managed-shaped `{1,0,0,1,0}` failure locally, then begin the ordered `listappend(list, value)` authority gate.

## 2026-07-15 row 22 two-argument listappend authority checkpoint

- The corrected focused runtime regression was rerun and now reproduces the managed result exactly: expected `{1,1,1,1,0}`, actual `{1,0,0,1,0}`. This confirms the remaining failure is local and durable.
- Mandatory authority gate for exact normal-size `listappend(list, value)` is complete and recorded in tracked `docs/reports/connection-lifecycle-authority.md`:
  - Barn spec and implementation agree: 2-arg form accepts LIST and ANY, returns original elements followed by the value, leaves the original outer list unchanged, shallowly shares nested values, is pure, and uses normal E_ARGS/E_TYPE validation.
  - Pinned Toast source agrees on the active contract and may only physically reuse an unaliased temporary, which is not observable. No permission, suspension, I/O, or deferred effect exists.
  - Oversize behavior and optional-index clamping differ or remain separate and are explicitly out of scope.
- Direct managed WSL Toast `listappend_to_end` passed pinned commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`: 1 selected, 11,504 deselected, in 3.51 seconds.
- Existing Banteng `ListValue` already snapshots its supplied outer list. Frozen smallest Java representation: one inline `BuiltinCatalog.invoke` case requiring exactly 2 args and a `ListValue` first argument, local copy of elements, append second value, return new `ListValue`; add name to existing PURE classification. No helper or value-owner change.
- A clean no-diagnostics distribution was rebuilt. Direct managed Banteng `listappend_to_end` was proven red: 1 failed, 11,504 deselected, in 3.45 seconds, with exact `E_VERBNF`.
- The direct red run may have left a Banteng child; it has not yet been inventoried because this checkpoint interrupted immediately after the failure.
- Next required action is to inventory and stop only the direct-red child, then implement the inline catalog case and rerun the direct row plus corrected focused lifecycle regression.
- Current blocker: none. The nested semantic contract and red proof are complete; checkpoint recording was required before production.

## 2026-07-15 row 22 focused-green checkpoint

- Direct-red Banteng child PID 330648, tied to `moo_conformance__6uh3lhw/Test.db`, was stopped.
- Implemented the frozen normal-size two-argument `listappend` inline in `BuiltinCatalog.invoke`: exact arity 2, `ListValue` first argument, copy outer elements, append any second value, return a new `ListValue`; added `listappend` to existing PURE classification. No helper, optional index, quota branch, or value-layer change was added.
- The corrected focused lifecycle JUnit regression passed under Java 25 in 4 seconds.
- `spotlessApply` and `installDist` passed.
- Direct managed Banteng `listappend_to_end` passed: 1 selected, 11,504 deselected, in 3.47 seconds. Its exact child PID 337444, tied to `moo_conformance_4i7abp7b/Test.db`, was stopped.
- Exact managed Banteng lifecycle row 22 `audit_connection_hold_and_oob_options` passed: 1 selected, 11,504 deselected, in 3.73 seconds. Its exact child PID 86060, tied to `moo_conformance_84kcsxq6/Test.db`, was stopped.
- No temporary diagnostics remain. The active tracked row 22 slice currently includes authority documentation, `BuiltinCatalog`, `VmState`, `MooVm`, `MooRuntime`, and the corrected runtime regression.
- Next required action is the complete managed `connection_lifecycle_toast_oracle` fail-fast gate. After it, inventory/stop only its child, reread the full immutable plan, append the row 22 verification receipt, run final Java 25 clean gate, reread the plan, and commit before row 23.
- Current blocker: none. The checkpoint hook required this note update before the broader gate.

## 2026-07-15 row 22 pre-stage checkpoint

- Complete managed lifecycle fail-fast gate advanced the frontier: 22 rows passed before row 23 `audit_user_created_hook_for_new_login_object` failed, with 11,482 deselected, in 47.03 seconds. Row 23 expected `[1,1,1]` and produced `[4,1,0]`; it remains a separate unchecked slice.
- The full-gate child PID 26904, tied to `moo_conformance_bfeauuyz/Test.db`, was stopped.
- The full immutable implementation plan was reread after the substantial managed run. Row 22's full verification receipt was appended to the authority report.
- Final Java 25 `clean check installDist` passed in 13 seconds, including the full Gradle test suite. The full immutable plan was reread again immediately afterward, and the clean receipt was appended.
- `git diff --check` passed.
- Final tracked slice is exactly six files: `docs/reports/connection-lifecycle-authority.md`, `BuiltinCatalog.java`, `MooRuntime.java`, `MooVm.java`, `VmState.java`, and `MooRuntimeTest.java`; 426 insertions and 16 deletions.
- Full production/test diff review found no temporary diagnostics, extra queue, helper, interface, adapter, sender, or unrelated API. Authority-report diff matches the source/oracle/verification record.
- Untracked `notes-banteng-conformance-handoff.md`, `notes-banteng-row19-restart.md`, `notes-row22-toast-authority.md`, and `pyghidra_mcp_projects/` remain outside the intended commit.
- Next required actions: stage exactly the six tracked row 22 files, verify cached diff/check, commit the kept slice, and verify tracked state clean before beginning row 23.
- Current blocker: none. The checkpoint hook required this note update before staging.









## Row 22 Barn authority subagent restart

- Assigned read-only Barn authority gate for lifecycle row `audit_connection_hold_and_oob_options`.
- Scope is the row at `../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:1860-2017`, then corresponding Barn normative specification and implementation paths for `force_input`, connection options, held input, disabled OOB handling, queues, and suspend ordering.
- No row-22 files or processes have been inspected in this subagent turn yet.
- No current technical blocker. The only immediate gate was this mandatory checkpoint-note update.
- Next step: inspect the exact conformance row, then Barn specification, then Barn implementation, without editing tracked files or running server/oracle processes.

### Row 22 Barn specification findings in progress

- Exact row contract: a fresh negative prelogin connection receives `force_input(neg, "audit-queue-login")`; after `suspend(0)` it must be logged into the prepared player. The next wizard run calls `set_connection_option` for `hold-input` and `disable-oob`, injects ordinary and `#$#` lines with `force_input`, and uses `suspend(0)` after each mutation to observe queue scheduling.
- Expected observations are `{1, 1, 1, 1, 0}`: ordinary input is blocked while held then released; OOB bypasses hold while OOB is enabled; with OOB disabled that same line is held; releasing hold must not later deliver it as OOB.
- `spec/builtins/network.md:120-129` and `spec/builtins/tasks.md:519-532` only say `force_input` queues/injects a line, accepts optional front insertion, and is wizard-only. They do not define negative prelogin targets, login dispatch, queue scheduling, OOB classification, or interaction with connection options.
- `spec/builtins/network.md:224-243` names `set_connection_option`, `hold-input`, `disable-oob`, and `connection_options`, but gives only one-line descriptions. It does not specify `get_connection_option`, option lookup/error behavior, state transitions, queue release, OOB bypass, or disabled-OOB disposition.
- `spec/server.md:321-329` says `#$#` lines are application text on WebSocket and `flush-command` matches complete messages, but does not define general TCP input/OOB dispatch, `do_out_of_band_command`, `hold-input`, or `disable-oob` semantics.
- `spec/tasks.md:403-426` is the only precise scheduling authority found so far: runnable input is selected before FIFO background work unless input is held; one runnable task is chosen per `run_ready_tasks()` call. `spec/server.md:42-51` is weaker and calls scheduler choice implementation-defined.
- `spec/builtins/tasks.md:172-213` defines `suspend(0)` only as a zero-duration suspension returning zero; it does not state precisely how that yields relative to queued input.
- No Barn spec occurrence of `get_connection_option` or `do_out_of_band_command` has been found.
- Current blocker: none. Implementation inspection has not begun; next step is tracing only the Barn public builtin/input entry points and semantic owners for this row.

### Row 22 Barn implementation findings in progress

- `builtins/registry.go:197-208` registers `connection_options`, `set_connection_option`, `connection_option` (singular), and `force_input`. There is no `get_connection_option` registration.
- `builtins/signatures.go:550-575` is the public `force_input` builtin: it validates arity/types/permissions, accepts an optional front flag, and delegates to the host `InputForcer`; it does not require a positive/logged player.
- `server/server.go:130-133` wires the server `InputProcessor` as that `InputForcer`.
- `server/connection_manager.go:399-445` maps fresh connections under negative object IDs and resolves both positive and negative connection targets.
- `server/input_processor.go:269-314` is the force-input semantic owner. OOB-enabled `#$#` lines bypass held-input handling; ordinary lines and OOB-disabled lines go through `HandleHeldInput`; live negative connections are dispatched through normal prelogin processing; positive connections enqueue a normal `InputEvent`.
- `builtins/network.go:169-248` defines option names/defaults and state. Defaults are `hold-input=0`, `disable-oob=0`, and empty `flush-command`.
- `builtins/network.go:562-619` owns held commands: matching `flush-command` clears them; held lines are appended/prepended; ordinary processing stops.
- `builtins/network.go:1251-1348` owns option mutation/read. Turning `hold-input` off first stores the false value, drains held lines in order, and re-injects each through the same `InputForcer`. Public read builtin is `connection_option`, not `get_connection_option`.
- `server/input_processor.go:199-280` classifies input before command/login dispatch. Enabled OOB goes directly to listener `do_out_of_band_command`; OOB-disabled text falls through held-input and then ordinary command dispatch. Thus an OOB-disabled line held under `hold-input=1`, then released after hold clears, is processed as an ordinary command and must not call the OOB verb.
- `server/input_processor.go:421-470` dispatches a live negative connection's input to listener `do_login_command`; a returned positive user object remaps/logs in the connection.
- Scheduling agreement: `task/manager.go:147-165` makes `suspend(0)` immediately queued; `scheduler/task_runtime.go:231-252` stamps the yield moment and queues the suspender. The input loop's ticker path rechecks `inputQueue` before `ProcessReadyTasks` (`server/input_processor.go:174-195`), so an event enqueued by `force_input` is processed before the suspending caller resumes.
- The row's expected sequence follows directly: ordinary held input blocks then runs after hold release; enabled OOB bypasses hold; disabled OOB is held; after hold release it is ordinary text, not OOB.
- Likely immediate Banteng failure reason, based only on row order: the first builtin invoked in the post-login run is `set_connection_option`; an `E_VERBNF` there most directly means Banteng has not registered/implemented that builtin. This needs confirmation against Banteng's actual failure location by the parent agent.
- Current blocker: none. Remaining read-only work is exact login remap/range confirmation and consolidation for the parent; Toast has not been inspected.

### Row 22 Banteng path subagent checkpoint

- Assignment: read-only Banteng trace for the frozen `audit_connection_hold_and_oob_options` row, covering builtin registration/signatures, connection identity, input queues and hold/OOB state, OOB dispatch, `suspend(0)` scheduling, and existing JUnit seams.
- Observed: `BuiltinCatalog.invoke()` has `set_connection_option` but no `force_input`; its default returns `E_VERBNF`, source-confirming the row's first failure. `effectClass()` likewise lacks `force_input`.
- Observed: `setConnectionOption()` currently recognizes only `hold-input` and `flush-command`; `ConnectionOption` has only those two values, so `disable-oob` is also absent.
- Observed: `MooRuntime.ConnectionState` already owns `holdInput`, `flushCommand`, and FIFO `pendingInput`; `executeLine()` applies flush and hold before its existing `#$#` dispatch, so enabled OOB currently cannot bypass hold. `applyConnectionOptionRequests()` changes hold state but does not drain pending input when hold is released.
- Observed: the existing textual OOB path invokes listener `do_out_of_band_command` with parsed words and exact `argstr`. Existing JUnit seams independently cover held/flush input and OOB dispatch.
- Observed: login remaps the persistent connection ID to a positive player through `WorldTxn.switchConnectionPlayer`; runtime option lookup already accepts either connection ID or associated player.
- Tried/worked: read-only symbol searches and bounded source/test reads; no managed server or tracked edit.
- Did not work: one initial `rg` pattern had a quoting error and was immediately replaced with a correctly quoted read-only search.
- Current blocker: none. Next step is finish the exact login/remap, task-scheduling, and minimal force-input effect trace, then report one focused red regression design to the parent.
## Row 22 effect-plumbing subagent checkpoint (2026-07-15)

- Assigned bounded scope: implement only the production-side `force_input(target, line)` builtin and existing VM deferred-effect plumbing in `BuiltinCatalog.java`, `VmState.java`, and `MooVm.java`.
- Authority research and the focused red proof were completed by the parent agent before delegation.
- Required behavior: two arguments (object target and string line), wizard permission following existing conventions, `DEFERRED_EFFECT`, and a concrete `ForcedInputRequest` carried through the existing result/stage/drain path.
- Explicit exclusions: no runtime edit, no test edit, no authority/plan edit, no `at_front`, and no new helper/interface/adapter/sender/framework.
- No implementation action has been taken yet in this subtask. There is no known blocker; the next decision-changing action is to inspect the three named production files and mirror their existing effect-request patterns exactly.

### Row 22 effect-plumbing partial implementation state

- Verified the exact assigned paths exist: `src/main/java/moo/builtin/BuiltinCatalog.java`, `src/main/java/moo/vm/VmState.java`, and `src/main/java/moo/vm/MooVm.java`.
- Observed the existing concrete deferred-effect patterns: `ConnectionOptionRequest` is carried by `BuiltinCatalog.Result`, staged by `MooVm.invokeBuiltin`, stored in an ordered `VmState` list, and exposed through a destructive public drain method; boot-player targets use the same ordered staging model.
- Added only the first `BuiltinCatalog` hunk so far: invoke dispatch for `force_input`, `DEFERRED_EFFECT` classification, and argument/permission validation that constructs `ForcedInputRequest(target, decodedLine)`.
- The permission check is wizard-only using the existing `(flags() & 4) != 0` convention. Argument count is checked before object/string types. Live-target validity is intentionally deferred to runtime as assigned.
- The file is intentionally incomplete at this checkpoint: the `ForcedInputRequest` record, `Result` field/factory, VM state storage/drain, and `MooVm` staging still need to be added. Compilation will fail until those bounded pieces are completed.
- No blocker is known. The next action is to finish the concrete result payload in `BuiltinCatalog.java`, then thread it through the two named VM files.

### Row 22 `listappend(list, value)` Barn authority subagent checkpoint

- Restarted Codex process received a bounded read-only authority-gate assignment for the newly exposed exact surface `listappend(list, value)` used by lifecycle row 22.
- Required scope: read the normative Barn specification for the two-argument append form, then trace Barn builtin registration/signature through its semantic owner; report exact files/symbols/line ranges, agreements/disagreements, unresolved questions, and related durable conformance rows.
- Explicit exclusions: no Toast inspection, no server/oracle run, no tracked-file edit, and no Git-state change.
- Work has not begun beyond recording this checkpoint. Current blocker: none. Next decision-changing action is to locate and read the Barn `listappend` specification before inspecting its implementation.

### Row 22 `listappend(list, value)` Barn authority findings

- Normative owner read: Barn `spec/builtins/lists.md:1-60`, with collection-copy context in `spec/types.md:436-460`.
- The normative call accepts 2 or 3 arguments: first is LIST, second is any value, optional third is INT. With two arguments it returns a new logical list with the value appended at the end; collection mutation is shallow copy-on-write, so the input list remains unchanged while nested values may remain shared.
- Normative errors explicitly list E_TYPE when the first argument is not a list and E_RANGE when an explicit index is below 0 or above `length(list)`. The spec does not explicitly state the wrong-count E_ARGS result, the non-INT optional-index E_TYPE result, or the size-limit E_QUOTA result; those are specification omissions.
- Barn registers `listappend` to `builtinListappend` at `builtins/registry.go:87-98`. Generated signature metadata at `builtins/function_signatures_generated.go:104` is `{minArg:2,maxArg:3,argTypes:{LIST,ANY,INT}}`; shared validation at `builtins/signatures.go:55-62,85-125` returns E_ARGS for wrong count and E_TYPE for wrong typed positions before the builtin body.
- Semantic owner `builtins/lists.go:15-51` independently validates the same count/first type, defaults index to `list.Len()`, calls `list.InsertAt(index+1,value)`, checks the server list-size limit, and returns the resulting list. `types/list.go:188-202` allocates a new top-level slice and preserves the source; `builtins/limits.go:293-301` adds E_QUOTA when the configured byte limit is reached.
- Active two-argument form agrees with the specification. A separate optional-index disagreement exists: implementation accepts index `length(list)+1` (`builtins/lists.go:37`) and then clamps it to append in `InsertAt`, while the normative maximum is `length(list)`.
- One read-only command intended to locate `CheckListLimit` failed from a PowerShell parsing error caused by combining an assignment after `&&`; it changed no state and was replaced with separate searches.
- Current blocker: none. Remaining read-only work is to inspect the registry dispatch methods for the exact signature-validation path and locate durable conformance rows that exercise `listappend`, then report to the parent. Toast remains uninspected as required.
## 2026-07-15 row 22 Toast authority subagent

- Assigned a read-only authority trace for exact two-argument `listappend(list, value)` used by lifecycle row 22.
- Scope is pinned WSL Toast source at `/root/src/toaststunt` only: registration/signature, semantic list owner, return ordering, mutation/copy and sharing behavior, errors/quota, and tick/effect classification.
- Managed oracle execution, Banteng inspection/editing, and Git changes are explicitly out of scope.
- No source observations have been made yet in this restarted subagent turn; there is no blocker. The next step is to locate `listappend` registration and implementation in the pinned WSL tree.

### Row 22 Toast observations

- `src/list.cc:1755-1771` registers `listappend` with 2 to 3 arguments and prototype `(TYPE_LIST, TYPE_ANY, TYPE_INT)`.
- `src/functions.cc:239-275` centrally enforces count and type: wrong count raises `E_ARGS`; a non-list first argument or non-integer third argument raises `E_TYPE`; the second argument accepts any value.
- `src/list.cc:695-721` owns both append/insert behavior. For exactly two arguments, append position is `length + 1`; it refs the input list and element before freeing the argument list, then calls `doinsert` and returns the result.
- `src/list.cc:196-250` shows append ordering: existing elements remain in order and the supplied value becomes the final element.
- Copy/mutation semantics are copy-on-write at the backing-storage level: `doinsert` may realloc in place only when the consumed list backing is uniquely referenced and append is at the end; otherwise it allocates a new outer list and `var_ref`s existing elements. Because the builtin first takes a reference to the argument list value, the ordinary MOO-visible input remains separately referenced, so the returned append does not mutate the caller-visible original list. Existing nested values are shallowly shared/refcounted rather than recursively cloned.
- `src/list.cc:716-720` rejects a result above `max_list_value_bytes`; `src/list.cc:620-627` maps that to catchable `E_QUOTA` when `max_concat_catchable` is enabled, otherwise an `ABORT_SECONDS` task abort.
- `src/execute.cc:972-988,2224-2281` plus `src/include/opcode.h:86-93` show a builtin call is an ordinary tick-counted opcode (`OP_BI_FUNC_CALL` is not in the no-tick sets). `listappend` itself has no suspend/call/I/O path and returns or raises synchronously, so its semantic effect is pure value construction aside from resource accounting.
- Optional third-index behavior is adjacent and not yet fully summarized for the parent: `insert_or_append` computes `third + 1` for listappend and clamps to `[1, length + 1]`. Exact edge agreement with Barn is still to be reported, but row 22 is specifically the two-argument form.
- No managed oracle was run; no Banteng source was inspected or edited; no Git state was changed. No blocker is present.
## 2026-07-15 row 23 Barn authority subagent checkpoint

- Assigned read-only authority work for durable row `audit_user_created_hook_for_new_login_object` in `../moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml` lines 2019-2130.
- Required scope is Barn only: read the normative login and hook specification, then trace login input through new-player detection, association, and `user_created` invocation.
- No Barn or Banteng source inspection has yet occurred in this subagent turn.
- No files other than this untracked checkpoint note have been changed by this subagent.
- Current blocker: none; the next action is to locate and read the exact Barn specification and implementation surfaces without editing or running an oracle.

### Row 23 Barn findings after normative and implementation read

- Exact managed row is `audit_user_created_hook_for_new_login_object` at `moo-conformance-tests/src/moo_conformance/_tests/audit/connection_lifecycle_toast_oracle.yaml:2019-2130`. Its `do_login_command` creates an object, sets its player flag, returns it, and expects `user_created(args[1])` to receive that same object.
- Barn normative login surfaces are `spec/login.md:25-140`, `spec/server.md:129-177`, and `spec/objects.md:491-516`. They define `do_login_command`, successful player association, and `user_connected`, but contain no occurrence or contract for `user_created`.
- The normative context and permission sections are `spec/login.md:163-177`, `spec/objects.md:250-274`, `spec/objects.md:453-484`, and `spec/tasks.md:371-395`. They describe generic `this`, `player`, `caller`, `args`, `argstr`, programmer/owner permissions, but do not bind those fields or permissions specifically for lifecycle hooks.
- Barn implementation entry is `server/input_processor.go:368-464`: the listener's `do_login_command` runs with pre-login player/conn ID; `maxBeforeLogin := store.MaxObject()` is captured before dispatch; completion calls `loginPlayer(conn, player, player > maxBeforeLogin)`.
- Login acceptance is in `server/input_login.go:37-133`: the returned value must be a positive OBJ/ANON with the user flag, or an already-associated player via `switch_player`.
- Association and hook ordering are in `server/input_login.go:237-313`: the negative prelogin mapping is removed and the connection is associated before any hook; connect message is sent before `user_created`; `user_created` is called before `user_connected` on fresh newly-created login.
- Hook metadata is produced by `server/input_login.go:202-224` plus `scheduler/task_factory.go:61-105` and `scheduler/task_runtime.go:124-185`: handler is `this`; player is both `player` and root `caller`; `args` is `{player}`; `argstr` defaults to empty; task permissions are the hook verb owner.
- Current likely semantic risk: the new-player predicate is only `returnedPlayer > maxObjectCapturedBeforeLogin`, not provenance that this object was created by this specific login task. The exact row does satisfy that predicate.
- No source, test, plan, or authority report was edited. Only this untracked checkpoint note was updated. No server or oracle was run.
- Current blocker: none for the Barn-only report; the remaining action is to verify exact relevant tests/owners and return the evidence to the parent without inspecting Toast.
## Row 23 Toast authority delegation (2026-07-15)

- Delegated read-only task: trace pinned WSL Toast `/root/src/toaststunt` for `audit_user_created_hook_for_new_login_object` from login input through returned-object detection, player association, `user_created`, `user_connected`, and message ordering.
- Required output: exact source files, symbols, line ranges, trigger predicate, hook metadata and permissions, exception/suspension behavior, Barn/row agreement, and adjacent unresolved behavior.
- Constraints: do not run the oracle; do not inspect or edit Banteng implementation; do not change Git state.
- Observed so far: no source inspection has begun in this delegated turn.
- Current blocker: none; the mandatory notes checkpoint had to be recorded before source inspection.
- Next step: read the applicable reverse-engineering skill, then inspect only pinned WSL Toast source.

### Row 23 pinned Toast findings in progress

- Verified pinned WSL source commit: `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Input path: `src/server.cc:1534-1541` queues the unchanged line; `src/tasks.cc:1762-1769` selects `do_login_task` while the queue player is negative.
- Login hook: `src/tasks.cc:878-916` snapshots `db_last_used_objid()` before `do_login_command`, invokes it on the listener with the anonymous negative player, parsed words, and original argstr.
- Trigger: `src/tasks.cc:921-958` requires the connection still open, queue player still negative, normal result value of object type, and `is_user(result)`; newly-created status is exactly `returned_obj > old_max_object`.
- The durable row's create-plus-`set_player_flag` sequence satisfies `is_user`: `src/objects.cc:1002-1023` requires wizard task permissions and sets `FLAG_USER`; `src/db_objects.cc:1289-1292` defines `is_user` as valid object plus that flag.
- Association happens before notification: queue player is changed at `src/tasks.cc:927`, then `player_connected` sets the network handle's player and connection time at `src/server.cc:1658-1668`.
- For a genuinely new object with no existing connection, `src/server.cc:1707-1724` logs `CREATED`, sends `create_msg`, then synchronously calls listener `user_created`; it does not call `user_connected` in that branch.
- `src/server.cc:518-527` constructs notifier args as the one-element list containing the exact returned object and passes empty argstr.
- `src/execute.cc:3278-3336` establishes notifier metadata: `this` is listener, `player` is returned object, `caller` is `#-1`, `args` is `{returned_object}`, `argstr` is `""`; task permissions are the located verb owner's object.
- `src/include/execute.h:94-99`, `src/execute.cc:2199-2218,2241-2277`, and `src/tasks.cc:1825-1870` show normal, aborted, and blocked outcomes. A blocked or aborted `do_login_command` leaves the preinitialized integer result, so the login predicate fails. The `user_created` outcome is discarded after association/message; missing notifier is simulated as an empty successful verb.
- Message ordering source: `src/server.cc:1714-1723` places `create_msg` before `user_created`; `src/server.cc:545-567` sends configured/default message lines directly to the network queue.
- Current blocker: none. Remaining work is to pin notification exception/suspension consequences and existing-connection adjacent behavior precisely, then report the read-only trace to the parent.

## 2026-07-15 row 23 implementation checkpoint

- Row 22 is committed as `7137d8c feat: implement held forced input lifecycle`; tracked state was clean afterward and `master` was ahead of origin by 12.
- Row 23 `audit_user_created_hook_for_new_login_object` fails committed Banteng with `[4, 1, 0]`: the returned user is an object, but the `user_created` hook never runs and the seen property remains its initial list.
- Barn and pinned Toast both snapshot the greatest object ID immediately before `do_login_command`, classify a returned player above that snapshot as new, associate it, and invoke listener `user_created` with the exact returned player as `args[1]`.
- The exact managed row passed pinned WSL Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`: 1 passed, 11,504 deselected, 4.18 seconds.
- Banteng's defect is in `MooRuntime.executeLogin`: it associates the returned player and invokes only `user_connected`; there is no `user_created` lookup or dispatch.
- `WorldTxn.objectCount()` cannot represent the high-water mark because recycled worlds can have ID holes. Allocation scans live IDs for the maximum.
- Added a focused JUnit regression that creates and flags the player inside `do_login_command`, verifies association through `connection_info(args[1])`, exact lifecycle locals, and ordering `{"created", "connected"}`. It failed red at the missing-hook assertion in `MooRuntimeTest.java:237`.
- Added the production `WorldTxn.maximumObjectId()` query returning `-1` for an empty world. The required `MooRuntime` snapshot and direct post-association `user_created` dispatch have not yet been patched.
- Current tracked slice consists of the row-23 authority report, focused test, and `WorldTxn` query. No blocker is known. Next exact action is the `MooRuntime.executeLogin` patch, followed by the same focused test.

## 2026-07-15 row 23 focused-test checkpoint

- `MooRuntime.executeLogin` now snapshots `world.maximumObjectId()` immediately before `executeStored(do_login_command)` and directly dispatches listener `user_created` after successful fresh association and before existing `user_connected` when the returned player ID exceeds that snapshot.
- The direct hook locals are listener `this`, returned-player `player`, `caller = #-1`, verb `user_created`, `{returned player}` arguments, and empty `argstr`.
- The first post-production focused run did not reach hook assertions: it failed because the test used `objectCount()` to infer the created handler in a world with ID holes.
- An initial mechanical replacement touched an earlier existing test; it was caught before a run and restored. Only the row-23 regression was then changed.
- Using `maximumObjectId()` and simplifying the setup property loop still selected an object without `do_login_command`; both runs failed at `MooRuntime.executeLogin:714` before the production hook path.
- The focused setup now records its exact created handler in `#0.audit_created_handler`, and the Java test reads that object directly. This change has been applied but not yet run because the checkpoint fired immediately afterward.
- No managed server has been started in this checkpoint. No Java process cleanup is needed. There is no known production blocker; the next exact action is rerunning the single focused JUnit test to determine whether the implemented hook path is green.

## 2026-07-15 row 23 green-regression checkpoint

- The focused setup failure was caused by nested MOO string literals using Java `\"` escapes, which Java consumed; the stored verb source therefore contained unescaped inner quotes and returned `{2, {E_INVARG}}`. The durable test now uses `\\\"...\\\"`, matching existing passing tests.
- Temporary diagnostic setup assertions and two accidental matches in the preceding similar test were removed/restored before the final run. The earlier `authenticatesReturnedPlayerAndDispatchesCommandToListenerHandler` method is back to its original setup shape.
- The exact focused regression `MooRuntimeTest.callsUserCreatedAfterAssociatingNewReturnedPlayerAndBeforeUserConnected` now passes under Java 25: 1 test, Gradle `BUILD SUCCESSFUL`, 3 seconds.
- The passing regression proves the new player is associated before `user_created`, the hook receives the exact player in `args[1]` with listener `this`, player `player`, `caller = #-1`, and empty `argstr`, and existing Banteng ordering remains `user_created` then `user_connected`.
- After that substantial targeted pass, the immutable `docs/reports/banteng-implementation-plan.md` was reread in full. It was not edited.
- The active plan remains Phase 8 exact convergence, row 23. The next required actions are formatting/building the distribution, running the exact managed row, cleaning its exact Banteng process, then running the full lifecycle category and rereading the plan again.
- No blocker is known. No managed Java server is currently running from this slice.

## 2026-07-15 row 23 managed-gate checkpoint

- Java 25 `spotlessApply` passed, then Java 25 `installDist` passed separately.
- Exact managed Banteng row `audit_user_created_hook_for_new_login_object` passed: 1 selected, 11,504 deselected, 4.30 seconds.
- That exact run left PID 96136 using temp DB `moo_conformance_o87dn9ru/Test.db`; only that PID was stopped, and a subsequent exact process inventory was empty.
- Full managed `connection_lifecycle_toast_oracle` passed all 23 selected rows with 11,482 deselected in 46.89 seconds.
- The lifecycle run left PID 383664 using temp DB `moo_conformance_uegzkyim/Test.db`; only that PID was stopped, and a subsequent exact process inventory was empty.
- After the passing substantial lifecycle gate, the immutable implementation plan was reread in full again and was not edited. Phase 8 requires keeping and committing this slice before row 24.
- No blocker is known. The next exact action is appending the row-23 Banteng verification receipts to the authority report, then running the final Java 25 `clean check installDist` gate and completing diff/staging/commit accountability.

## 2026-07-15 post-row-23 full-profile checkpoint

- Row 23 was staged as exactly four tracked files and committed as `6fdd142 feat: dispatch user created login hook`. A post-commit status showed no tracked changes; only the known notes and `pyghidra_mcp_projects/` remain untracked.
- The full managed stock profile was run fail-fast against the rebuilt committed distribution to select the next exact-convergence target.
- The run passed 48 rows and failed first at `gap_followups_toast_oracle::audit_telnet_escaped_iac_stripped_from_login_input` after 69.80 seconds.
- The immediate harness symptom is `UnicodeDecodeError`: byte `0xff` remained in a returned line at position 11 after the harness's Telnet stripping. This is evidence only; it is not yet proof whether Banteng input parsing, output escaping, or the durable row/harness expectation is wrong.
- The next target family is therefore the Toast-oracle gap follow-ups, beginning with the escaped-IAC login-input row. No source or report edits for this new slice have been made.
- The managed Banteng process from the failed full-profile run may still be live and must be inventoried and cleaned by exact temp-DB command line before authority work begins.
- No semantic blocker is yet known. The next exact action is managed-process inventory/cleanup, then reading the exact durable row and completing its Barn/Toast authority gate before any Java design.

## 2026-07-15 row 24 Barn-authority delegation checkpoint

- Delegated read-only task: trace Barn's normative specification and current implementation for `audit_telnet_escaped_iac_stripped_from_login_input` from socket byte ingress through Telnet parsing into the login line delivered to `do_login_command`.
- The durable row supplies split byte chunks `6961632d`, `ff`, `ff`, `2d6c6f67696e0d0a` and expects both parsed login arguments and `argstr` to represent `iac--login`.
- Required authority details are exact files, symbols, and lines; state carried across split reads; the meaning of Telnet `FF FF`; causally necessary CR/LF handling; and agreements, disagreements, or unresolved items.
- Constraints: do not edit tracked files, run servers, or inspect Toast or Banteng. No source inspection has begun in this delegated turn.
- Current blocker: none. The mandatory checkpoint note was the required first action. The next action is to inspect only the named durable row, Barn spec, and Barn implementation needed for this authority trace.

### Row 24 Barn `TCPTransport.ReadInput` authority

- The only normative Barn text found so far is `spec/server.md:189-200`: native `tcp` and `tls` listeners use a TCP byte stream with "Telnet-style line input". It does not define escaped-IAC behavior precisely.
- `server/transport.go:13-33` defines `IAC = 255` and a five-state Telnet parser: normal, just-saw-IAC, option-command, subnegotiation, and subnegotiation-after-IAC.
- Parser state is connection-owned and persistent: `TCPTransport` stores `tState`, `tCommand`, `lineBuf`, and `lastWasCR` at `server/transport.go:51-61`; construction initializes only `tState` to normal at lines 63-70.
- `TCPTransport.ReadInput` reads one byte at a time from the persistent buffered reader at `server/transport.go:85-101`, so a call can block between chunks while retaining both the partial text and state on the transport.
- In normal state, the first split `FF` changes `tState` to just-saw-IAC and stores the command prefix without returning (`server/transport.go:102-106`). A later second `FF` therefore takes the escaped-IAC branch across the read boundary, resets state and command storage, and deliberately appends no byte to the text line (`server/transport.go:133-140`). In Barn's implemented semantics, `FF FF` is stripped entirely from ordinary input, despite the comment first describing it as a literal `FF`.
- Ordinary printable ASCII is appended at `server/transport.go:123-130`, so the durable chunks form `iac-` + `-login` around the stripped pair.
- CR immediately returns the accumulated line and sets `lastWasCR` (`server/transport.go:107-112`). The following LF is consumed on the next call without returning an empty line (`server/transport.go:113-122`). Thus the row's final `0D 0A` delivers exactly one line, `iac--login`.
- Current blocker: none. Next authority function is the connection reader/dispatcher that consumes `ReadInput` and forwards a normal line to the pre-login processor.

### Row 24 Barn connection-ingress authority

- `InputProcessor.HandleConnection` is the live socket reader at `server/input_processor.go:65-160`. Before login it assigns the negative connection object as `player` (`:119-122`) and, for any transport implementing `InputTransport`, calls the same connection-owned `ReadInput` (`:124-137`).
- A normal completed line is enqueued unchanged as `command.InputEvent.Line`, along with the parser's `isOutOfBand` result (`server/input_processor.go:151-159`). No decoding, byte replacement, trimming, or tokenization occurs at this boundary.
- The serialized input loop invokes `processInput` (`server/input_processor.go:163-187`). Its relevant dispatch order at `:203-229` handles true Telnet/OOB input first, then held/read input, and sends any negative-player ordinary line to `processPreLogin` at `:224-226`.
- The escaped `FF FF` path in `ReadInput` returns no OOB event; it only resumes normal text accumulation. Therefore the durable row reaches `processPreLogin` once, as ordinary line `iac--login`, after CR terminates it. The queued LF remains for the next socket-read iteration and is suppressed by the transport's `lastWasCR` state.
- Exact next symbols are `InputProcessor.processPreLogin` at `server/input_processor.go:368` and `InputProcessor.dispatchLoginCommand` at `:423`; tokenization uses `command.CommandWordList`, and login task creation receives the original line as argstr.
- Current blocker: none. The checkpoint interrupted before those two functions were read; they are the next authority functions.

### Row 24 Barn pre-login delivery authority

- `InputProcessor.processPreLogin` copies `input.Line` to `line` at `server/input_processor.go:368-380`. Except for a trusted `PROXY ` prelude, it does not alter the line; after login-task guards and `shouldCallDoLoginCommand`, it calls `dispatchLoginCommand(conn, line)` at `:396-415`.
- `InputProcessor.dispatchLoginCommand` gets the listener object, tokenizes `line` with `command.CommandWordList`, converts each word directly to a MOO string, and passes both that argument list and the same original `line` to `CreateLoginHookTask` (`server/input_processor.go:423-464`).
- `command.CommandWordList` trims only leading Unicode whitespace and otherwise tokenizes the input at `command/command.go:153-165`. For `iac--login` there are no spaces or quotes, so the result is exactly the one-element list `{"iac--login"}`.
- `Scheduler.CreateLoginHookTask` stores the received original line directly as `Task.Argstr` and copies the supplied argument values into `Task.VerbArgsValues` (`scheduler/task_factory.go:124-151`). Therefore `do_login_command` receives `args == {"iac--login"}` and `argstr == "iac--login"` without another network-layer transformation.
- The durable conformance row itself is `../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:189-265`; its split chunks are at `:223-236`, and its asserted `args`/`argstr` pair is at `:238-244`.
- Current blocker: none. The causal implementation trace is complete. Remaining authority work is to inspect Barn's focused Telnet tests for existing coverage and identify agreements, disagreements, and unresolved normative gaps.

### Row 24 Barn Telnet test coverage and adjudication

- `server/transport_test.go:29-80` proves ordinary text, bare LF, bare CR, and CRLF behavior; specifically `TestReadLineCRLFDoesNotDoubleDeliver` proves CRLF yields one line and the next text line is not preceded by an empty delivery.
- `server/transport_test.go:82-120` proves a full `IAC WILL option` sequence is removed from text and exposed by `ReadInput` as a separate OOB event. `TestReadInputPreservesLineAcrossMidLineIAC` at `:122-144` proves the partial ordinary line survives an intervening OOB Telnet command.
- Other focused coverage at `server/transport_test.go:146-230` proves stripping of WONT/DO/DONT, subnegotiation, multiple commands, and mid-line negotiation. There is no Barn test for `IAC IAC`, and no test drives either byte of a Telnet sequence from separate socket writes.
- Agreement: the durable row and current Barn implementation both require the split `FF FF` pair to disappear from ordinary login text, leaving exactly `iac--login`; tokenization and argstr delivery then match the row's expected one-word args list and exact string.
- Agreement: the implementation's connection-owned parser fields make socket packet/write boundaries semantically irrelevant. After the first `FF`, `ReadInput` blocks for another byte with `tState == telnetStateIAC`; the later `FF` completes the escaped-IAC branch. The partial `lineBuf == "iac-"` is retained throughout.
- Disagreement internal to Barn: `server/transport.go:135-139` comments that `FF FF` denotes a literal `FF`, but the executable branch deliberately does not append that literal byte. The row agrees with executable behavior, not with the first half of the comment.
- Unresolved normative item: `spec/server.md` says only "Telnet-style line input" and does not specify whether escaped IAC is preserved as a literal byte or stripped. The current implementation and durable oracle row resolve this slice operationally, but the normative Barn prose is under-specified.
- Unresolved coverage item: the exact escaped-IAC and split-write state behavior lacks a Barn regression test. This does not make the implementation trace ambiguous, but it leaves the behavior vulnerable to accidental change.
- Current blocker: none. The Barn-only authority trace is complete; no tracked file, server, Toast source, or Banteng source was touched.
## 2026-07-15 row 24 Toast authority subagent restart

- Assigned a read-only source-authority trace for lifecycle row `audit_telnet_escaped_iac_stripped_from_login_input`.
- Required source is pinned WSL Toast `/root/src/toaststunt` at commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Exact input split is `6961632d`, `ff`, `ff`, `2d6c6f67696e0d0a`; expected login `args`/`argstr` is `iac--login`.
- Scope is network receive through the Telnet state machine to the queued login task, including cross-read state and why `IAC IAC` is omitted instead of emitted as literal `FF`.
- No source files have been edited and the live oracle has not been run for this task.
- Current blocker: none; the pinned source trace has not yet begun after this Codex restart.

## 2026-07-15 row 24 authority and red-test checkpoint

- The active row is `gap_followups_toast_oracle::audit_telnet_escaped_iac_stripped_from_login_input`: split chunks `iac-`, `FF`, `FF`, `-login CRLF` must reach `do_login_command` as `args == {"iac--login"}` and `argstr == "iac--login"`.
- Barn spec `spec/server.md:189-200` is silent. Barn's connection-local state at `server/transport.go:51-61,85-140` persists across reads and strips both `FF` bytes; its comment saying literal `0xFF` contradicts executable behavior. Barn then passes the unchanged `iac--login` line through input processing and login task construction.
- Pinned Toast `network.cc:81-117,385-469,471-566,600-627,1445-1479` likewise preserves Telnet state across reads and copies neither byte of `FF FF` into in-band or OOB input. `server.cc:1534-1541`, `tasks.cc:878-916,1075-1129,1171-1180,1648-1770`, and `parse_cmd.cc:108-123` deliver the one-word line unchanged.
- The exact managed row passed pinned WSL Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`: 1 selected, 11,504 deselected, 6.61 seconds.
- Created the required untracked `docs/reports/telnet-transport-authority.md` with source authority, disagreements, the committed Banteng baseline, oracle receipt, and the frozen smallest Java design.
- Banteng's concrete cause is `MooServer.handleConnection` using ISO-8859-1 `BufferedReader.readLine()` with no Telnet state; runtime and value encoding faithfully preserve the already-wrong characters. Output then leaks raw `FF`, causing the later harness UTF-8 decode symptom.
- Frozen design: replace only that reader with an inline byte loop and connection-local line/CR/IAC/option state. Consume `FF FF` completely and consume `FF FB/FC/FD/FE option` for the preceding kept negotiation row. Do not add a class, interface, helper, adapter, sender, alternate runtime path, or egress rewrite. Other Telnet/OOB/binary rows remain separate.
- Added focused `MooServerTest.stripsEscapedIacSplitAcrossReadsFromLoginInput`, which retains `WorldTxn`, uses raw split socket writes, synchronizes with `DONE`, and reads the recorded world property directly.
- The focused test is red on committed production: expected `{{"iac--login"}, "iac--login"}` but observed `{{"iac-ÿÿ-login"}, "iac-ÿÿ-login"}` in 3 seconds. This directly proves the two-byte ingress defect without response-parser substitution.
- Current tracked change is only `MooServerTest.java`; the authority report is untracked and intended for this slice. No production edit has been made. No blocker is known. Next exact action is the bounded `MooServer.handleConnection` byte-loop patch, followed by the same focused test.

## 2026-07-15 row 24 managed-frontier checkpoint

- Implemented the frozen production change only in `MooServer.handleConnection`: `InputStream` plus an inline `ByteArrayOutputStream` line buffer and connection-local CR/IAC/negotiation booleans replaced `BufferedReader.readLine()`.
- The parser consumes `FF FF`, consumes `FF FB/FC/FD/FE` plus one option byte, preserves CR/LF suppression and partial-line-at-EOF behavior, and converts complete lines to ISO-8859-1 only at the existing runtime boundary. No new class/interface/helper/adapter/sender or egress change was added.
- Focused JUnit `MooServerTest.stripsEscapedIacSplitAcrossReadsFromLoginInput` passed under Java 25 in 2 seconds. The immutable implementation plan was reread in full immediately afterward and was not edited.
- Java 25 `spotlessApply` and `installDist` passed separately.
- Exact managed Banteng row `audit_telnet_escaped_iac_stripped_from_login_input` passed: 1 selected, 11,504 deselected, 6.54 seconds.
- That exact run left PID 295260 using temp DB `moo_conformance_yoebqsh6/Test.db`; only that PID was stopped, and the subsequent process inventory was empty.
- The substantial `gap_followups_toast_oracle` fail-fast run passed the first 3 selected rows and stopped at the next distinct row `audit_telnet_two_byte_command_delivered_across_reads`: expected `{{"~FF~F1"}, "~FF~F1"}`, actual `{}`. This is the next OOB slice; it does not invalidate the kept escaped-IAC slice.
- The targeted run left PID 380524 using temp DB `moo_conformance_6_k3ffth/Test.db`; only that exact PID has been stopped. An empty inventory has not yet been verified because the checkpoint fired immediately after stopping it.
- No blocker is known. Next actions: verify empty process inventory, reread the immutable plan, append Banteng receipts to `telnet-transport-authority.md`, run final `clean check installDist`, review/stage/commit the row-24 slice, then begin the separate IAC NOP authority gate.

## 2026-07-15 row 24 commit checkpoint

- The targeted managed process PID 380524 was followed by an empty exact Banteng process inventory.
- The immutable implementation plan was reread in full after the targeted-family run and again after the final full Gradle suite; it was not edited.
- `docs/reports/telnet-transport-authority.md` now includes the focused red/green receipt, exact Banteng managed receipt, targeted-family frontier, cleanup evidence, and final clean gate.
- Final Java 25 `clean check installDist` passed in 13 seconds.
- Diff review and whitespace checks were clean. Exactly three files were staged: the Telnet authority report, `MooServer.java`, and `MooServerTest.java`; notes and `pyghidra_mcp_projects/` were excluded.
- The kept escaped-IAC slice was committed as `bbf9c4f feat: parse escaped telnet input bytes` with 3 files changed, 258 insertions, and 8 deletions.
- A post-commit tracked-state check has not yet run because the checkpoint fired immediately after commit.
- No blocker is known. The next target remains `audit_telnet_two_byte_command_delivered_across_reads`, which requires a separate authority/oracle/design/red-test slice before extending the parser to OOB delivery.
## Row 25 Barn authority subagent restart checkpoint

- Assigned read-only authority trace for `audit_telnet_two_byte_command_delivered_across_reads` in the managed conformance repository.
- Required scope is Barn specification and current Barn implementation only: connection-local Telnet state, split-read persistence, binary escaping, OOB classification/queueing, handler selection, and task `args`/`argstr` locals.
- Forbidden in this subtask: tracked-file edits, server runs, and inspection of Toast or Banteng implementation.
- No Barn files have yet been inspected in this restarted process.
- Current blocker: none. Next step is to locate the exact conformance row and Barn repository guidance/specification files, then trace the implementation read-only.

### Row 25 exact observable contract

- Source row: `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:267-331`.
- The row opens a fresh unauthenticated connection, writes raw byte `FF`, then writes raw byte `F1` in a distinct `send_bytes` step, and waits 500 ms.
- Listener `#0:do_out_of_band_command` stores `{args, argstr}`.
- Required value is `{{"~FF~F1"}, "~FF~F1"}`: one handler argument containing the complete two-byte binary escape, with the same text in `argstr`.

### Barn normative specification surface

- `C:/Users/Q/code/barn/spec/server.md:193-204` defines TCP/TLS as byte streams with “Telnet-style line input.”
- `spec/server.md:165-170` says ordinary unlogged commands route to `#0:do_login_command(conn, line)`.
- The required-hooks table at `spec/server.md:129-140` does not name `do_out_of_band_command`.
- The spec has no normative statement for Telnet command lengths, state persistence across socket reads, `~HH` binary escaping, transport-originated OOB classification, or OOB task locals.
- Therefore the exact row is not fully bound by Barn's current normative prose. The implementation can be traced, but the missing prose remains an unresolved specification gap.

### Barn `TCPTransport.ReadInput` and `formatTelnetCommand`

- `C:/Users/Q/code/barn/server/transport.go:51-70` makes `tState`, `tCommand`, `lineBuf`, and the buffered reader fields of one connection-local `TCPTransport`; initial state is `telnetStateNormal`.
- `server/transport.go:85-99` loops on `reader.ReadByte()`. A `bufio.Reader` refill may cross underlying socket reads without returning from `ReadInput`.
- At `server/transport.go:101-106`, raw `FF` in normal state changes `tState` to `telnetStateIAC` and replaces `tCommand` with `{FF}`. Both remain live while the next `ReadByte()` blocks for a later socket write.
- At `server/transport.go:133-151`, the later `F1` is appended. Because it is neither `FF`, `SB`, nor a WILL/WONT/DO/DONT negotiation introducer, the default branch formats the complete `{FF,F1}`, clears the command, resets state to normal, and returns `(rawCommand, true, nil)` immediately without a line terminator.
- `formatTelnetCommand` at `server/transport.go:190-192` is identity conversion from `[]byte` to Go `string`; it does not create `~HH` escapes. Binary escaping must occur later.
- For this exact split-write row, Barn implementation preserves the partial command correctly even if the second `ReadByte` requires a new underlying socket read. The connection fields would also persist across separate `ReadInput` calls, although ordinary blocking TCP reads keep this case inside one call.

### Barn `InputProcessor.HandleConnection`

- `C:/Users/Q/code/barn/server/input_processor.go:104-137` repeatedly reads one logical input item. For an unlogged TCP connection it selects `InputTransport.ReadInput`, not binary-mode `ReadChunk`.
- `server/input_processor.go:119-122` assigns the negative connection object `-conn.ID` as `player` while unlogged.
- `server/input_processor.go:151-159` enqueues the exact returned string plus `IsOutOfBand` in one `command.InputEvent`, then waits for event completion before reading the next item.
- Thus transport classification is preserved independently of the text prefix and the raw `{FF,F1}` string is serialized onto the input queue for the unauthenticated connection.

### Barn `InputProcessor.processInput`

- `C:/Users/Q/code/barn/server/input_processor.go:196-215` checks transport-originated `input.IsOutOfBand` before held-input handling, `read()` delivery, negative-player login dispatch, or normal command dispatch.
- `input.IsOutOfBand == true` enters `processOutOfBand` unless connection option `disable-oob` is truthy, and always returns afterward.
- Therefore this row bypasses `#0:do_login_command` even though `input.Player` is negative. The only implementation-level suppressor is `disable-oob`; the fresh row never sets it.

### Barn binary string representation

- `C:/Users/Q/code/barn/types/str.go:21-24` shows `types.NewStr` stores the supplied Go string unchanged, so handler-local strings contain raw bytes `FF F1`, not six printable `~`/hex characters.
- `types/str.go:97-125` defines MOO literal rendering: every byte below 32 or above 126 becomes uppercase `~%02X`.
- Consequently the raw handler value `{FF,F1}` is rendered at the MOO/test observation boundary as `"~FF~F1"`. This explains the expected spelling without attributing a transformation to the Telnet parser or argument builder.

### Barn `CommandWordList` and `InputProcessor.processOutOfBand`

- `C:/Users/Q/code/barn/command/command.go:114-150` tokenizes bytewise. It only splits when `unicode.IsSpace(rune(ch))`; byte values `FF` and `F1` are not whitespace, quote, or backslash syntax, so `{FF,F1}` stays one word.
- `command/command.go:153-165` therefore returns exactly one word containing the complete raw command.
- `C:/Users/Q/code/barn/server/input_processor.go:232-248` resolves the live connection by connection ID, selects `conn.ListenerObject()` as handler object, wraps each command word with `types.NewStr`, and calls `do_out_of_band_command`.
- The exact call is `CallVerbWithArgstr(listener, "do_out_of_band_command", {raw FF F1}, negativeConnectionObject, raw FF F1)`.
- Missing connection manager or missing connection drops the event (`input_processor.go:232-241`). A missing hook is silently ignored; only non-`E_VERBNF` exceptions produce a traceback (`input_processor.go:249-257`).

### Barn `Scheduler.CallVerbWithArgstr` task context

- `C:/Users/Q/code/barn/scheduler/call_verb.go:25-64` looks up `do_out_of_band_command` from the listener object, allowing normal inherited verb lookup through `Store.FindVerb`; missing verb yields `E_VERBNF`.
- `scheduler/call_verb.go:80-103` uses the resolved verb owner as programmer and wizard authority, the listener as `this`, the supplied negative connection object as `player`, and marks the task server-initiated.
- `scheduler/call_verb.go:104-116` records `caller == player` in the activation frame.
- `scheduler/call_verb.go:125-134` constructs locals exactly as: `this=listener`, `player=negative connection object`, `caller=negative connection object`, `verb="do_out_of_band_command"`, `args={raw FF F1}`, and `argstr=raw FF F1`.
- Thus the row's stored `{args,argstr}` later renders as `{{"~FF~F1"},"~FF~F1"}`. Handler permissions come from the resolved hook owner, not from the unauthenticated negative player.

### Barn focused test coverage

- `C:/Users/Q/code/barn/server/transport_test.go:95-120` verifies a complete three-byte IAC WILL sequence is returned raw with `isOOB=true`, then normal text is delivered separately.
- `server/transport_test.go:122-144` verifies `lineBuf` survives an IAC event in the middle of a normal line.
- No current Barn test names or constructs a two-byte IAC NOP split across distinct underlying writes/reads. The implementation supports the state transition, but the exact row is not directly regression-tested in Barn.

### Barn listener ownership

- `C:/Users/Q/code/barn/server/connection_manager.go:308-329` creates one `TCPTransport` per accepted TCP socket and writes the accepting listener record's object onto the connection.
- `C:/Users/Q/code/barn/server/connection.go:247-258` stores and returns that listener object under the connection lock.
- `InputProcessor.processOutOfBand` calls the hook on this per-connection listener object, not on a globally hard-coded object. The managed default listener is `#0`, which matches the row's installed `#0:do_out_of_band_command`.
- `C:/Users/Q/code/barn/cmd/barn/main.go:354-370` uses `server.DefaultListenSpecs(port)` when no explicit `-listen` is supplied. `server/listen_spec.go:12-16` leaves `ListenerSpec.Object` at its zero value, and `builtins/network.go:25-35` defines that field as `types.ObjID`; therefore the default managed TCP listener object is exactly `#0`.

### Barn fresh-connection option state

- `C:/Users/Q/code/barn/builtins/network.go:189-213` gives any player/connection without stored option state defaults including `disable-oob=0` and `binary=0`.
- `builtins/network.go:245-248` therefore returns false for `ConnectionOptionTruthy(negativeConnectionObject, "disable-oob")` on this fresh connection.
- This confirms the exact row reaches `processOutOfBand`; no unobserved default option suppresses or reroutes the bytes.

### Row 25 Barn authority conclusion

Agreements with the active conformance assertion:

- Parser state and partial command bytes are connection-local and survive a buffered-reader refill between the `FF` and `F1` writes.
- `IAC NOP` is complete at two bytes and is emitted immediately as transport-originated OOB without CR/LF.
- The OOB bit survives queueing and takes precedence over unauthenticated login routing.
- The managed default listener is `#0`, so `#0:do_out_of_band_command` is selected.
- Word tokenization yields one raw-byte word. The hook receives `args={raw FF F1}` and `argstr=raw FF F1`; literal rendering exposes both as `~FF~F1`.
- Task authority is the resolved hook owner; task locals are `this=#0`, `player=caller=negative connection object`, `verb="do_out_of_band_command"`, the one-element `args`, and the complete `argstr`.

Disagreements/gaps:

- Barn's normative `spec/server.md` does not specify transport Telnet command parsing, OOB hook dispatch, binary literal escaping for transport input, split-read persistence, or OOB task locals. The implementation agrees with the row, but the written normative contract is incomplete.
- Barn's focused transport tests cover complete IAC negotiation and line preservation around IAC, but not raw `FF` and `F1` arriving in distinct writes/refills.

Unresolved under this read-only assignment:

- No Barn or Toast server was run, as explicitly forbidden. Current source predicts the exact row passes Barn, but live managed execution was not verified by this subagent.
- “Across reads” in this row means separate client writes and potentially separate underlying socket reads/refills; Barn normally remains inside one blocking `ReadInput` invocation while waiting for the second byte. The observable behavior is the same, but the row does not prove return/re-entry across two `ReadInput` method calls.












## Row 25 Toast authority subagent restart

- Assigned read-only pinned Toast authority trace for `audit_telnet_two_byte_command_delivered_across_reads`.
- Authority is `/root/src/toaststunt` at commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Required causal surface: persistent network/Telnet state across reads, IAC NOP (`FF` then later `F1`) command formation, `~FF~F1` escaping, pre-login out-of-band task routing, handler context, and word parsing.
- Constraints: do not edit production/report files, do not run the oracle, and do not inspect Barn or Banteng.
- Observation at restart: source inspection has not begun in this subtask.
- Current blocker: none.
- Next step: verify the pinned Toast worktree identity, then trace only the causally required source path with exact symbols and line references.

## 2026-07-15 row 25 implementation checkpoint

- Post-row-24 status confirmed commit `bbf9c4f feat: parse escaped telnet input bytes` and no tracked changes.
- Active row is `audit_telnet_two_byte_command_delivered_across_reads`: split `FF`, later `F1`, no newline; expected listener `do_out_of_band_command` observation is `{{"~FF~F1"}, "~FF~F1"}` before login.
- Barn spec is silent. Barn retains raw `{FF,F1}` in connection-local transport state, dispatches OOB before login, passes raw bytes to the listener hook, uses negative connection as player/caller, and renders raw bytes as uppercase `~HH` for the observable result.
- Pinned Toast retains the partial command across reads, converts completed raw `{FF,F1}` to ASCII `~FF~F1` before task creation, dispatches OOB before login, and supplies listener this, negative player, caller `#-1`, one `~FF~F1` argument, and same argstr.
- Barn/Toast agree on every row assertion but differ on escape timing and caller. The exact row passed pinned Toast `aecc51e9449c6e7c95272f0f044b5ba38948459e`: 1 selected, 11,504 deselected, 5.63 seconds.
- Appended the complete row-25 source authority, disagreements, Toast receipt, and frozen design to tracked `docs/reports/telnet-transport-authority.md`.
- Frozen design is one necessary synchronized concrete `MooRuntime.executeTransportOutOfBand` operation plus an existing-loop `F1` branch. No new type/helper/adapter/sender/general encoder/subnegotiation/egress rule.
- Added focused real-socket test `MooServerTest.dispatchesTwoByteTelnetCommandSplitAcrossReadsAsOutOfBandInput`; it uses the durable 500 ms wait and reads the retained world property. Committed production was exactly red: expected `{{"~FF~F1"}, "~FF~F1"}`, actual `{}`.
- Production patches are applied but untested: `MooRuntime` now requires the existing connection, updates activity, resolves the accepting listener OOB verb, and executes it with negative player, caller `#-1`, one exact command argument and argstr; `MooServer` calls it immediately only for `F1` after retained IAC.
- No blocker is known. Next exact action is rerunning the single focused JUnit test.

## 2026-07-15 row 25 managed-frontier checkpoint

- Focused `MooServerTest.dispatchesTwoByteTelnetCommandSplitAcrossReadsAsOutOfBandInput` passed under Java 25 in 5 seconds after the runtime/server patch.
- The immutable implementation plan was reread in full immediately after the focused pass and was not edited.
- Java 25 `spotlessApply` and `installDist` passed separately.
- Exact managed Banteng row `audit_telnet_two_byte_command_delivered_across_reads` passed: 1 selected, 11,504 deselected, 5.51 seconds.
- That exact run left PID 387624 using temp DB `moo_conformance_21yv85n4/Test.db`; only that PID was stopped and the following exact inventory was empty.
- The full `gap_followups_toast_oracle` fail-fast run then passed 4 selected rows and stopped at the next separate contract `audit_telnet_iac_delivered_as_oob_command`: expected `{{"~FF~FB~01"}, "~FF~FB~01"}`, actual `{}` after 17.67 seconds.
- The targeted run left PID 370232 using temp DB `moo_conformance_euuxdvmy/Test.db`; only that PID was stopped and the following exact inventory was empty.
- No blocker is known. Next actions are the mandatory plan reread, append row-25 Banteng receipts to the authority report, final `clean check installDist`, review/stage/commit this slice, then begin a separate authority gate for three-byte IAC negotiation OOB delivery.

## 2026-07-15 row 26 Barn-authority subagent start

- Assigned a read-only Barn authority trace for exact durable row `audit_telnet_iac_delivered_as_oob_command` in `gap_followups_toast_oracle.yaml`.
- The row sends byte `FF`, waits, then sends `FB 01`; expected unauthenticated listener `do_out_of_band_command` observation is `{{"~FF~FB~01"}, "~FF~FB~01"}`.
- Required causal surface is persistent Telnet state across reads, negotiation-option completion, exact OOB emission point, raw/binary rendering, OOB task queue/context, and comparison with the prior simple two-byte IAC command.
- Constraints: no production/report edits, no servers, no Toast, and no Banteng inspection. This notes append is solely the mandatory checkpoint record.
- Observation at start: source inspection has not begun in this subtask.
- Current blocker: none.
- Next step: inspect the named conformance row, Barn spec, and Barn implementation only, with exact symbols and line references.
## 2026-07-15 row 27 read-only subagent restart

- Assigned read-only trace for `audit_telnet_subnegotiation_delivered_across_reads` against committed Banteng `1d1bfff`.
- Required output: frozen row steps and expected value, exact current failure path, smallest plausible local-state seam labeled as unproven design, and focused existing test location.
- Constraints: no production/test edits, no managed Toast or Banteng run, and no new helper/interface/adapter/sender proposal.
- Current blocker: none; repository evidence has not yet been inspected in this restarted subagent turn.

### Row 27 read-only findings

- Verified clean tracked HEAD `1d1bfff5923baef51b7d0b6cdab9ad637b6f7606`; only known untracked notes/projects are present.
- Frozen row sends `ff`, `fa1f00500018`, `ff`, `f0` as separate writes, waits 500 ms, and expects `{args, argstr}` to equal `{{"~FF~FA~1F~00P~00~18~FF~F0"}, "~FF~FA~1F~00P~00~18~FF~F0"}`.
- Current parser discards FA and F0 in the `afterIac` branch, appends the five payload bytes to the normal line buffer, and never invokes `executeTransportOutOfBand` before the assertion.
- Existing runtime OOB dispatch already constructs the expected one-element args list and identical argstr; the missing behavior is transport parsing, not runtime verb dispatch.
- Smallest unproven seam is local subnegotiation accumulation/state beside the existing parser locals, ending on FF F0 and calling the existing runtime operation once. No helper/interface/adapter/sender or runtime change is indicated.
- Most focused test location is `MooServerTest.java` between the negotiation test at line 290 and the next server-lifecycle test at line 343.
- No managed Toast/Banteng run occurred. No production or test file was edited. Current blocker: none for implementation by the coordinating agent.

## 2026-07-15 row 28 Barn authority subagent restart

- Parent assigned a read-only authority pass for `audit_gmcp_subnegotiation_delivered_across_reads` after Banteng commit `81bc3bb`.
- Required evidence: the exact durable conformance row, normative Barn specification, and Barn's completed-Telnet-frame to `do_out_of_band_command` tokenization path, especially the argument and `argstr` representation of `~FF~FA~C9Core.Hello {"client":"audit"}~FF~F0`.
- Scope is evidence only: no server runs, Java design, or project-artifact edits. This checkpoint note is the sole mandatory write before inspection.
- Current blocker: none; exact paths, line ranges, symbols, and agreement/disagreement still need to be established from current files.

## 2026-07-15 binary-mode Barn authority subagent

- The durable row `audit_binary_mode_dispatches_raw_chunk_without_newline` is at `moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:572-663`. It logs a created player in, enables its `binary` option, sends ASCII `auditbin` without CR/LF, and expects exactly one recorded empty `argstr`.
- Barn stores connection options by player. `builtins/network.go:1251-1316` validates and sets `binary`; enabling it wakes a reader blocked in line mode. `server/input_processor.go:119-160` then chooses `BinaryTransport.ReadChunk` only for a logged-in player with the option truthy.
- `server/transport.go:194-209` preserves raw bytes in a Go string but defines a chunk as one blocking byte plus whatever is already in the `bufio.Reader`; it does not wait for CR/LF and does not preserve TCP write/message boundaries.
- The returned chunk enters ordinary logged-in command dispatch, not a special binary callback. Exact ASCII `auditbin` parses as a verb with no arguments and empty `argstr`; the player-owned verb is found first and run synchronously as a registered foreground command task with `this`, `player`, and `caller` all equal to the logged-in player.
- Normative Barn text names binary mode but does not define TCP raw-chunk size, read boundaries, byte encoding, timing, or dispatch behavior. The row depends on the eight sent bytes being buffered together; TCP does not guarantee that, so this is an implementation/timing caveat rather than a normative framing contract.
- Current blocker: none. The bounded read-only authority result is ready to return; no server was run and no project artifact was edited beyond this mandatory checkpoint note.
