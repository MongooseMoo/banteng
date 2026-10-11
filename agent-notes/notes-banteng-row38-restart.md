# Banteng row 38 restart handoff

## Active target

- Exact row: `audit_chparent_nonwizard_requires_fertile_parent` in `gap_followups_toast_oracle.yaml:911-936`.
- Current managed Banteng result: `E_TYPE`; durable expectation: `E_PERM`.
- Row 37 is complete and committed as `0bec65f`.
- Row 38 is the sole active exact-convergence slice. Do not mix later rows or broader `chparent` semantics into it.

## Authority completed

- Used Codex subagents for bounded read-only Barn and Toast source traces; main agent personally reread every cited owner.
- Barn spec: `../barn/spec/builtins/objects.md:208-217` is incomplete for this row. It says not-owner/wizard is `E_PERM` but lists non-fertile under `E_INVARG` without the composite parent-allowance rule.
- Barn public entry/owner: `../barn/builtins/registry.go:141-154`, `../barn/builtins/objects_hierarchy.go:127-235`. Nonwizard may use a real parent only if they own it or it has `FlagFertile`; otherwise `E_PERM`. Mutation owner `Store.ChangeParents` has no permission policy.
- Pinned Toast WSL identity personally reverified: `aecc51e9449c6e7c95272f0f044b5ba38948459e` at `/root/src/toaststunt`.
- Toast public entry/owner: `src/objects.cc:1352-1355,553-601`; `controls2` at 41-45; `db_object_allows` at `src/db_objects.cc:1268-1274`; `FLAG_FERTILE` at `src/include/db.h:252-262`; mutation at `src/db_objects.cc:1030-1146`.
- Toast returns `E_PERM` because Programmer controls the child but is not wizard, does not own the proposed parent, and the parent lacks `FLAG_FERTILE`. Mutation is not called.
- Ordering disagreement recorded: Toast permission precedes recursion/database integrity; Barn checks ancestry/property conflicts before parent permission. Active row does not resolve that disagreement.
- Exact managed Toast row passed: 1 selected / 11,518 deselected in 4.07s. Managed process exited; unrelated PID 19 `/tmp/td.db ... 9482` remained untouched.
- Row provenance: conformance commit `81d1157 Add move and chparent trust Toast oracles`.
- Required evidence record committed as `18a5920 docs: freeze nonfertile parent permissions`, file `docs/reports/object-parent-authority.md` only.

## Current Banteng trace

- `BuiltinCatalog.java:123-164` requires two `ObjectValue` arguments, validates references/cycles, then currently rejects every nonwizard with `E_PERM`; it is too strict for later successful owner/fertile cases, but the active denial should already be `E_PERM` if reached.
- `WorldTxn.changeParent` owns only topology mutation.
- The conformance harness preserves captured object literals: transport parsing retains `#N`, `_value_to_moo` emits `#N` unquoted, and runner substitution uses that conversion.
- `BuiltinCatalog.create($nothing)` has no permission gate, so the obvious setup should return an object. The preserved managed server log was empty and did not expose the executed literal.
- Therefore the exact source of Banteng's observed `E_TYPE` remains unresolved. Do not patch `chparent` based only on the downstream failure.

## Next exact action

Create a focused Java regression that reproduces the durable row's actual multi-user setup and asserts `E_PERM`, then run it on committed Banteng. It must fail for the same `E_TYPE` boundary before production design. The test may reveal whether the failure is in Programmer creation, capture-equivalent literal construction, login task permissions, or `chparent` dispatch.

No production edit is authorized until that valid focused red identifies the boundary and the smallest existing-owner design is appended to the committed evidence report.

Keep this file, `notes-row38-barn-authority.md`, `notes-row38-toast-authority.md`, all other `notes-*.md`, and `pyghidra_mcp_projects/` untracked and unstaged.

## Corrected row-38 boundary and implementation state

- The earlier attribution of managed `E_TYPE` to `chparent` was wrong. The durable row fails earlier at Wizard setup `parent_obj.f = 0`; `chparent()` is not reached with valid object arguments.
- Existing `BuiltinCatalog.chparent` already returns `E_PERM` and preserves topology for the active nonwizard/non-fertile denial when setup succeeds. Per issue-invalidity discipline, no `chparent` regression or production edit was made.
- The user’s standing full-suite scope authorizes continuing on the actual prerequisite surface: intrinsic fertile-flag assignment.
- Main agent personally verified `WorldTxn.readObjectProperty`/`writeObjectProperty`: `f` was absent; fresh objects start with flags `0`.
- Barn authoritative surface: `../barn/spec/objects.md:198-215`; `.f` is writable integer and zero clears. Barn owner `../barn/vm/op_property.go:128-223,352-439` routes built-in `f` to `Store.SetObjectFlag(FlagFertile, value.Int() != 0)`; store owner `store_core.go:182-196`, bit 128 in `object.go:230-239`.
- Toast authoritative surface at pinned `aecc51e...`: `src/execute.cc:1959-2080` `OP_PUT_PROP` permits Wizard for `BP_F`; `src/db_properties.cc:620-685` maps `BP_F` to `FLAG_FERTILE` and clears on false; bit declaration `src/include/db.h:252-262`.
- Toast accepts general truthy/falsey values while Barn restricts to integer; active row assigns integer zero, so broader type behavior is excluded.
- Updated and committed the correction as `7dd9ebd docs: correct parent row failure boundary`.
- Valid focused regression `MooRuntimeTest.writesIntrinsicFertileFlagAsIntegerZero` edits only `MooRuntimeTest.java`: Wizard creates object and returns `object.f = 0`; Java asserts bit 128 clear. Valid red was expected `{1, 0}`, actual `{2, {E_PROPNF}}`; no `.f` readback or `chparent`.
- Froze smallest design in report and committed `12fd190 docs: design fertile flag setup slice`: one `WorldTxn.writeObjectProperty` branch for normalized `f`, `IntegerValue`, existing `replaceFlags`, bit 128; no helper/VM/read/permission/chparent changes.
- Production implementation now adds exactly that seven-line branch in `WorldTxn.java`.
- Exact focused Java 25 regression passed before and after `spotlessApply`.
- Reread immutable plan in full after focused green; did not edit it.
- Current tracked ledger: only `WorldTxn.java` and `MooRuntimeTest.java`, 35 insertions total, `git diff --check` clean; branch ahead 6; all notes/artifacts untracked.
- Current blocker: none. Next exact action is Java 25 `clean installDist`, exact managed Banteng row, exact process cleanup, substantial fail-fast family frontier, immutable-plan reread, full Java gate, receipt update, and row-38 implementation commit before the next row.

## Row-38 verification receipts

- Java 25 `clean installDist` succeeded for the corrected implementation.
- Exact managed Banteng row `audit_chparent_nonwizard_requires_fertile_parent` passed: 1 selected / 11,518 deselected in 4.04s.
- Exact managed process PID 373596 tied to `moo_conformance_4fs314qz/Test.db` was stopped; re-inventory was empty.
- Full fail-fast `gap_followups_toast_oracle` family passed all 17 selected rows / 11,502 deselected in 30.31s. This closes the entire currently selected family.
- Family-run process PID 364508 tied to `moo_conformance_mtfq6g4o/Test.db` was stopped; re-inventory was empty.
- Reread the immutable implementation plan in full after the substantial family pass; did not edit it.
- Full Java 25 `gradlew clean check installDist` passed in 16s, including Spotless and the full test suite.
- Reread the immutable implementation plan in full again after that full passing gate; did not edit it.
- Current blocker: none. Next exact action is append observed green receipts to `docs/reports/object-parent-authority.md`, review the complete tracked diff, stage exactly report + `WorldTxn.java` + `MooRuntimeTest.java`, commit the kept row-38 slice, then identify the next failing conformance family without mixing it into this commit.
## Row 39 Toast authority subagent checkpoint (2026-07-15)

- Assigned read-only authority trace for `task_scheduling_toast_oracle::audit_default_fg_ticks`.
- Required source is pinned WSL Toast at `/root/src/toaststunt`, expected identity `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Scope is registration and implementation of `ticks_left()` plus foreground-task default tick assignment, including constants/config interactions.
- No source inspection or oracle execution has occurred yet in this subagent turn.
- Current blocker: none; next step is to verify the pinned identity and inspect the exact Toast source paths read-only.

### Toast `ticks_left()` builtin trace

- Pinned source identity verified: `/root/src/toaststunt` is exactly `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- `/root/src/toaststunt/src/execute.cc:3770-3784`, `register_execute()`: `register_function("ticks_left", 0, 0, bf_ticks_left)` exposes a zero-argument builtin.
- `/root/src/toaststunt/src/execute.cc:3668-3676`, `bf_ticks_left()`: constructs a `TYPE_INT`, assigns the global `ticks_remaining`, frees the empty argument list, and returns it via `make_var_pack()`.
- This builtin performs no wizard/ownership check and does not itself derive or reset the budget; its semantic value is whatever the active VM execution path placed in `ticks_remaining`.
- Related but distinct: `/root/src/toaststunt/src/execute.cc:3542-3577`, `bf_yield_if_needed()`, compares `ticks_remaining` with its default threshold 2000 and validates that threshold against `server_int_option("fg_ticks", DEFAULT_FG_TICKS)`; that is not the initialization path for `ticks_left()`.
- Next step: trace the declaration, writes, save/restore behavior, and public foreground task entry that assigns `ticks_remaining`.

### Toast foreground task tick-budget trace

- `/root/src/toaststunt/src/tasks.cc:1825-1837`, public `run_server_task()`, calls the centralized `run_server_task_setting_id()` and then cleans up the task-local state.
- `/root/src/toaststunt/src/tasks.cc:1839-1871`, `run_server_task_setting_id()`, is documented as the usual new-task entry point. It allocates a task ID/local map, resolves the callable verb, and calls `do_server_verb_task()`.
- `/root/src/toaststunt/src/execute.cc:3278-3286`, `do_server_verb_task()`, forwards the resolved verb definition/program to `do_server_program_task()`.
- `/root/src/toaststunt/src/execute.cc:3289-3337`, `do_server_program_task()`, constructs the root activation and calls `do_task(..., 1 /*fg*/, ...)`.
- `/root/src/toaststunt/src/execute.cc:3225-3248`, `do_task()`, initializes the VM root vector/stack and forwards `is_fg` to `run_interpreter()`.
- `/root/src/toaststunt/src/execute.cc:3064-3083`, `run_interpreter()`, is the semantic selection point: for `is_fg`, it calls `setup_task_execution_limits(server_int_option("fg_seconds", DEFAULT_FG_SECONDS), server_int_option("fg_ticks", DEFAULT_FG_TICKS))`.
- `/root/src/toaststunt/src/execute.cc:3048-3062`, `setup_task_execution_limits()`, is the write owner for the observable global and assigns `ticks_remaining = (ticks < 100 ? 100 : ticks)`. Thus a configured integer below 100 is clamped to 100; other integers, including nonpositive ones, are not treated as absent.
- `/root/src/toaststunt/src/include/options.h:99-131` defines `DEFAULT_FG_TICKS` as `60000` and documents `$server_options.fg_ticks` as its database override. Foreground means player-input/server-initiative tasks that have never suspended; forked or resumed tasks use background limits instead.
- `/root/src/toaststunt/src/server.cc:1420-1428`, `server_int_option()`, reads through `get_server_option(SYSTEM_OBJECT, name, &v)` and returns the property only when it is `TYPE_INT`; absent or wrong-typed values fall back to the supplied default.
- `/root/src/toaststunt/src/server.cc:529-542`, `get_server_option()`, resolves a valid `server_options` object property and then its named property. With `fg_ticks` deleted, `server_int_option()` therefore returns `60000`.
- Parsed commands have an equivalent foreground entry: `/root/src/toaststunt/src/tasks.cc:811-871`, `do_command_task()`, either starts the handler through `run_server_task_setting_id()` or calls `do_input_task()`; `/root/src/toaststunt/src/execute.cc:3339-3372`, `do_input_task()`, likewise calls `do_task(..., 1 /*fg*/, ...)`.
- `/root/src/toaststunt/src/execute.cc:51-65` declares `ticks_remaining` as execution-global state explicitly reinitialized after suspension rather than stored in the VM. `/root/src/toaststunt/src/execute.cc:972-987` decrements it before each tick-counted opcode and aborts at zero, so an in-task `ticks_left()` result is normally slightly below the initial 60000.
- The audit’s `load_server_options()` call does not control `fg_ticks`: `/root/src/toaststunt/src/functions.cc:524-550` refreshes protected builtins/properties and the `SERVER_OPTIONS_CACHED_MISC` set, and `/root/src/toaststunt/src/include/server.h:197-245` does not include `fg_ticks`. The interpreter uses uncached `server_int_option("fg_ticks", ...)` for every interpreter run.
- Next step: verify suspended/resumed and forked paths only to delimit the foreground semantics and record remaining uncertainties; do not run the oracle.

### Toast builtin startup registration and scope boundary

- `/root/src/toaststunt/src/functions.cc:34-88` places `register_execute` in `bi_function_registries`; `register_bi_functions()` invokes every registry exactly once during server initialization. This is the startup path that reaches `execute.cc:3770-3784` and installs `ticks_left`.
- `/root/src/toaststunt/src/execute.cc:3250-3272`, `resume_from_previous_vm()`, calls `run_interpreter(..., 0 /*bg*/, ...)`; `/root/src/toaststunt/src/execute.cc:3374-3384`, `do_forked_task()`, calls `do_task(..., 0 /*bg*/, ...)`. These paths therefore select `bg_ticks`, not `fg_ticks`, and bound the foreground claim.
- For `audit_default_fg_ticks`, deletion of `$server_options.fg_ticks` makes the uncached lookup fall back to the compiled 60000 initial budget. The row correctly uses a range (`<= 60000 && > 59000`) because VM tick-counted opcodes have already decremented the global before each `ticks_left()` call returns it.
- Uncertainty: source inspection proves the initial allocation and decrement semantics but does not derive the exact bytecode-dependent number observed by this particular expression. Per assignment, no Toast oracle was run.
## Row 39 implementation subagent handoff (2026-07-15)

- Assigned to implement the already committed frozen design in `docs/reports/foreground-tick-budget-authority.md`.
- Required focused proof: add `MooVmTest` regression coverage for two live `ticks_left()` calls and `E_ARGS`, then run only that focused JUnit test and confirm the pre-production failure is specifically `E_VERBNF`.
- Production scope is restricted to existing owners `VmState.java`, `MooVm.java`, and `BuiltinCatalog.java`; no new public abstraction or scheduler API is authorized.
- This subagent must not run managed conformance or commit. No implementation action has been taken yet.
- Current blocker: none; the next action is to verify Git tracked state before editing.

### Row 39 implementation observations

- `git status --short --branch` showed `master` ahead of origin by 9 with no tracked changes; only the known untracked notes and `pyghidra_mcp_projects/` were present.
- The committed frozen design requires a 60,000 mutable remainder in `VmState`, an explicit Toast-counted opcode predicate in `MooVm`, and a primitive remainder threaded through `BuiltinCatalog.invoke` and `callFunction`.
- `VmState` currently has no tick field. `MooVm.executeInstruction` currently dispatches without charging, and its only catalog invocation is `invokeBuiltin`.
- `BuiltinCatalog.invoke` currently has no remainder parameter or `ticks_left` case. `call_function` recursively calls `invoke`, and the existing pure group includes `task_local`.
- No production or test file has been edited yet. Next step: add only the focused `MooVmTest` regression, then run that exact JUnit method to prove the expected `E_VERBNF` red.
## Row 40 Toast background-tick authority checkpoint (2026-07-15)

- Assigned source-only trace for `task_scheduling_toast_oracle::audit_default_bg_ticks`; the oracle must not be run.
- Pinned `/root/src/toaststunt` identity verified again as exactly `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- `/root/src/toaststunt/src/execute.cc:2084-2113` handles compiled `fork` statements as `OP_FORK` or `OP_FORK_WITH_ID`: it pops and type-checks the delay, rejects negative delays, then calls `enqueue_forked_task2(RUN_ACTIV, f_index, when, id-or--1)`.
- No child scheduling or background-limit source has been inspected yet in this row-40 turn.
- Current blocker: none. Next step is the exact callee `enqueue_forked_task2()` and its queued-task execution path to `do_forked_task(..., is_fg=0)`.
