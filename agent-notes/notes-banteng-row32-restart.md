# Banteng row 32 restart notes

## Ledger and active target

- Row 31 is committed on `master` as `0317673 fix: enforce ordinary property permissions`.
- Post-commit tracked state is clean. The branch is ahead of `origin/master` by 21. Only the known `notes-*.md` files and `pyghidra_mcp_projects/` are untracked; never stage them.
- Active exact-convergence target remains the `gap_followups_toast_oracle` family, now row 32: `audit_intrinsic_command_table_roundtrip` at `../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:772-802`.
- The row runs as Wizard and requires `connection_options(player, "intrinsic-commands")` to return `{ ".program", "PREFIX", "SUFFIX", "OUTPUTPREFIX", "OUTPUTSUFFIX" }` in that exact order/case, requires a valid list assignment `{"PREFIX", "SUFFIX"}` to round-trip immediately inside the same task, and requires integer `1` to restore the exact five-name list. Cleanup repeats integer `1`.
- The prior substantial family run proved row 31 and failed row 32 because Banteng returned `E_VERBNF` at the first `connection_options(...)` call.

## Mandatory authority gate state

- Read the entire normative Barn `spec/builtins/network.md`. Its section 6 is stale/incomplete for this surface: it lists only hold-input/disable-oob/binary for `set_connection_option` and describes one-argument `connection_options(player) -> MAP`; it does not specify `intrinsic-commands`, the two-argument value query, the five names, or integer restore semantics.
- Generated durable signature evidence names Toast `server.cc`: `connection_options` accepts 1-2 args of OBJ and optional STR and may raise `E_INVARG`/`E_PERM`; `set_connection_option` accepts exactly OBJ, STR, ANY.
- Dispatched two read-only Codex subagents: `row32_barn_authority` for normative/current Barn and `row32_toast_authority` for pinned WSL Toast source. They have not yet returned. They may create untracked notes; never stage those.

## Current Barn source observed directly

- `builtins/function_signatures_generated.go:36,171` registers exact 1-2 and 3-argument signatures; `builtins/registry.go:197,203-204` registers `connection_options`, `set_connection_option`, and singular `connection_option`.
- `builtins/network.go:169-223` recognizes `intrinsic-commands`, defines the exact default list in row order/case, includes it in default per-player option state, copies state on read, and mutates the same state synchronously.
- `builtins/network.go:1251-1309` validates connection and self/Wizard permission. For `intrinsic-commands`, a truthy LIST must contain only exact string members of the five-name set; any other truthy value becomes the full default list; falsey values remain falsey as currently written. It stores synchronously and returns integer zero.
- `builtins/network.go:1320-1349` (singular `connection_option`) resolves the same connection and permission, validates the option, and returns the stored value synchronously. Current Barn therefore has immediate read-your-write semantics for the row.
- Current Barn appears to implement the value query under singular `connection_option`, while the durable row and Toast use plural `connection_options` with a second argument. The subagent must trace whether plural aliases/delegates or disagrees.

## Current Banteng source observed directly

- `BuiltinCatalog` has `set_connection_option` but no `connection_options` switch case, producing the observed `E_VERBNF`.
- `BuiltinCatalog.setConnectionOption` supports only hold-input, flush-command, disable-oob, and binary; `intrinsic-commands` is rejected as `E_INVARG`.
- Existing set-option results are staged as `ConnectionOptionRequest` and applied by `MooRuntime.applyConnectionOptionRequests` only after VM execution. That existing path cannot satisfy the row's set-then-get operations within one MOO task.
- `MooRuntime.ConnectionState` owns hold-input, disable-oob, flush command, pending input, PREFIX/SUFFIX delimiters, and programming mode. `MooRuntime.executeLine` currently handles `.program`, PREFIX, and SUFFIX directly. No `OUTPUTPREFIX`/`OUTPUTSUFFIX` identifier exists in Banteng production source.
- `WorldTxn` already owns active connection identity and immutable network-info maps, including resolution by negative connection object or attached player. This is the only current concrete state visible to builtins without introducing a new interface/callback.
- No row 32 evidence record, live Toast run, focused regression, or production edit exists yet.

## Next step and blocker

- Current blocker: the mandatory source gate is not yet complete because both delegated Barn/Toast traces are still running and pinned Toast source details have not yet been reconciled.
- Next step: receive and independently verify their exact source paths/symbols, append the required tracked evidence section, run the exact row against the pinned managed WSL Toast oracle, then freeze the smallest Java design. Do not edit production before those steps.

## Completed authority gate and frozen design

- Both read-only subagents completed. Their untracked notes are `notes-row32-barn-authority.md` and `notes-row32-toast-authority.md`; never stage them.
- Independently traced current Barn plural reader to `builtins/signatures.go:606-652`. It returns the bare stored value for the named two-argument form. Barn agrees with all row values but differs outside the row: broader truthy non-list acceptance, case-sensitive option name, and its stored table does not control actual command dispatch.
- Independently traced pinned Toast semantic owner: `server.cc:2974-3029,3296-3299`, `tasks.cc:114-123,263-345,431-475,770-858,1015-1070`, `include/server.h:296-318,345-372`, and generic signature errors in `functions.cc:219-275`.
- Toast's exact physical-connection `tqueue::icmds` mask initializes all five names, setter/getter mutate/read it immediately, getter canonicalizes declaration order/case, and integer 1 restores the full mask. Actual command dispatch consults the mask.
- Row provenance: conformance commit `019bc6944c05d5eef2ca4ee847a385bc1b5ea5c0`, dated May 5, 2026, added row 32 and the adjacent unknown-name row as “Add intrinsic command table Toast oracles”; the commit itself contained no live receipt.
- Added a tracked authority section, `Intrinsic command connection-option round trip`, to `docs/reports/splice-computed-access-authority.md`, recording exact row, provenance, normative gap, Barn/Toast/Banteng paths, agreements/disagreements, and the initial no-design-before-oracle boundary.
- Verified live WSL source identity exactly `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Ran only `audit_intrinsic_command_table_roundtrip` through the exact managed pinned Toast command. It passed: 1 selected, 11,504 deselected, in 3.59 seconds.
- Post-run WSL process inventory found only unrelated PID 19, `/root/src/toaststunt/build-release/moo /tmp/td.db /tmp/td.db.new 9482`, started July 13. It was left untouched.
- Appended the live receipt and frozen minimal Java design to the tracked evidence record.

Frozen row 32 design:

- Extend existing `WorldTxn` live connection state with one exact five-name `ListValue`, keyed by the physical negative connection ID. Initialize on open, remove on close, and resolve reads/writes through the existing connection-object-or-attached-player mapping. This preserves tasks/player switches without a second lookup owner.
- Add only the named two-argument `connection_options` builtin required by the row, reusing existing self/Wizard and live-connection checks, returning the stored list.
- In existing `setConnectionOption`, recognize `intrinsic-commands` before creating a deferred runtime request. Store the row's valid LIST synchronously; a truthy integer stores the exact full list. This provides required same-task read-your-write while leaving other option effects unchanged.
- Deliberately do not implement adjacent row 33 unknown-member rejection in this slice. Integer zero, other kinds, one-argument enumeration, and dispatch filtering are also outside row 32.
- Add no interface, helper class, callback, adapter, result variant, or `MooRuntime.ConnectionState` field.

- No row 32 production edit or focused regression exists yet.
- Current blocker: none. Immediate next step is to add one focused `MooVmTest` that opens a world connection, attaches Wizard, executes the exact set/get sequence in one VM task, and asserts the three lists; then prove the expected current `E_VERBNF` red before production.

## Row 32 regression, implementation, and exact managed green

- Added focused `MooVmTest.roundTripsIntrinsicCommandConnectionOptionWithinOneTask`. It creates Wizard #3, opens physical connection #-47, attaches the player, executes the exact row program in one VM task, and asserts the canonical default, valid subset, and restored default lists.
- The first attempted test run was invalid because the existing `MapValue` import was missing; it failed at test compilation. Corrected only that import before production.
- The corrected focused test produced the valid behavioral red: expected VM `RETURNED`, actual `ERRORED` at the outcome assertion. The catalog's absent `connection_options` case is the exact `E_VERBNF` cause.
- Implemented the frozen concrete state in `WorldTxn`: one exact canonical default `ListValue`, a physical-connection-ID map initialized on open and removed on close, and reads/sets/restores resolving through the existing connection object or attached player map.
- Implemented only the row-required builtin surface in `BuiltinCatalog`: missing named `connection_options`, `EXTERNAL_READ` classification, and synchronous `intrinsic-commands` handling in the existing setter for valid string LISTs and truthy integer restore. Existing other options still use their deferred requests.
- Unknown string members are intentionally stored rather than rejected in row 32, so adjacent row 33 remains a separate red/TDD slice. No command-dispatch filtering was added.
- Added no interface, helper class, callback, adapter, result variant, or `MooRuntime.ConnectionState` field.
- Focused row 32 JUnit regression passed in 8 seconds.
- Reread the complete immutable implementation plan after the focused green.
- `gradlew spotlessApply` passed in 1 second.
- `gradlew installDist` passed in 1 second.
- Exact managed Banteng row `audit_intrinsic_command_table_roundtrip` passed: 1 selected, 11,504 deselected, in 3.51 seconds.
- The managed Banteng process has not yet been inventoried or stopped after this checkpoint.
- Current blocker: none. Immediate next step is to inventory the exact managed Banteng Java process, stop only the PID tied to this row's temp database, and prove inventory empty; then run the substantial `gap_followups_toast_oracle` category.

## Row 32 broader and final gates

- Exact-row managed process was PID 196804, temp database `moo_conformance_ve4deee1/Test.db`, port 10658. Stopped only PID 196804 and confirmed managed Banteng inventory empty.
- Substantial `gap_followups_toast_oracle` fail-fast run passed the first 11 rows and failed at adjacent row 33 `audit_intrinsic_command_table_rejects_unknown`: expected `E_INVARG`, Banteng intentionally succeeded with value 0. Receipt: 11 passed, 1 failed, 11,488 deselected in 29.00 seconds.
- Family process was PID 248836, temp database `moo_conformance_8ipy7id8/Test.db`, port 20157. Stopped only PID 248836 and confirmed managed Banteng inventory empty.
- Reread the complete immutable implementation plan after the substantial category.
- `gradlew clean check installDist` passed all 145 JUnit tests, formatting, checks, and application distribution in 20 seconds.
- Reread the complete immutable implementation plan after the passing full suite.
- Appended all focused, managed, family, process-cleanup, and full-gate receipts to the tracked row 32 authority section.
- `git diff --check` passed.
- Current blocker: none. Immediate next step is to inspect the entire exact row 32 diff and Git ledger, then stage only the authority report, `BuiltinCatalog.java`, `WorldTxn.java`, and `MooVmTest.java`, and commit before row 33. Do not stage any notes or analysis artifacts.
