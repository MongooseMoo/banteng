# Banteng row 31 restart note

## Completed prior slice

- Row 30 `audit_output_order_disconnect` was committed on `master` as `5160457` (`fix: preserve output order during player boot`).
- Its focused regression, exact managed row, substantial family gate through the first nine rows, final `clean check installDist`, required plan rereads, and exact process cleanup all completed before commit.
- The tracked worktree was clean after commit. Existing `notes-*.md` files and `pyghidra_mcp_projects/` remain untracked and must never be staged.

## Active slice

- The only active exact-convergence target is `audit_property_flags_deny_nonowner_read_and_write` in `../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:750+`.
- The preceding substantial managed Banteng run proved the current failure: the first non-owner property read expected `E_PERM` but Banteng returned `E_TYPE`.
- The durable setup creates an object as Wizard, clears its public-write flag with `obj.w = 0`, and adds `audit_secret` owned by `#0` with no property flags. Subsequent programmer read and write steps are the asserted surface.
- Three read-only subagents are currently tracing Barn, pinned Toast, and committed Banteng. They were told not to edit or run managed servers.
- No row-31 tracked file, test, production, or evidence edit has occurred.

## Current blocker and next step

- No blocker currently exists; the mandatory authority gate is incomplete.
- Next action: read the full durable row and corresponding normative Barn property specification, then personally trace Barn and Toast public property access through permission/error ownership, write the repository-local evidence section, run the exact managed Toast row, and only then freeze Java design and prove a focused red regression.

## Initial row and spec evidence

- The full durable row is `gap_followups_toast_oracle.yaml:750-770`. Wizard creates `obj = create($nothing)`, sets object public-write flag `obj.w = 0`, adds `audit_secret = 10` with property info `{#0, ""}`, and captures the object. A programmer read `return obj.audit_secret` and programmer write `obj.audit_secret = 11` must each raise `E_PERM`; cleanup recycles as Wizard.
- Barn has no `spec/properties.md`; the corresponding normative surfaces are `spec/objects.md` and `spec/builtins/properties.md`.
- `spec/objects.md:146-170` defines dotted/dynamic property access and assignment, and defines property `r`, `w`, and `c` bits. It does not in that passage state error precedence.
- Search results identify additional normative passages at `spec/objects.md:450-476` and `spec/builtins/properties.md:250-310,320-390`, which remain to be read personally.
- No tracked row-31 edit or managed run has occurred. Current blocker: none; authority gate incomplete.
- Immediate next action: read those remaining normative passages, then trace current Barn implementation.

## Barn and Toast property-permission trace

- Barn normative `spec/objects.md:451-480` says a property read requires owner or `r`, a write requires owner or `w`, and Wizard bypasses. `spec/builtins/properties.md:250-310` identifies `.r`/`.w`, maps permission denial to `E_PERM`, and describes read/write permission; its wording sometimes says object owner while current implementation and Toast use the defining property owner, so this disagreement must be recorded.
- Current Barn public VM owner is `vm/op_property.go`. `executeGetProp` validates property-name/object types and object validity, resolves the defined/inherited property, then calls `checkPropertyReadPerm`. `executeSetProp` follows the same type/validity/lookup ordering, then calls `checkPropertyWritePerm` before storing.
- Barn `checkPropertyReadPerm` and `checkPropertyWritePerm` allow missing context, Wizard, or exact property owner; otherwise absence of `PropRead`/`PropWrite` returns `E_PERM`. Thus the row's programmer, which is neither Wizard nor property owner `#0`, gets `E_PERM` for both operations.
- Pinned Toast bytecode owner is `/root/src/toaststunt/src/execute.cc:1855-1953,1960-2080`. `OP_GET_PROP` and `OP_PUSH_GET_PROP` validate object/name types (`E_TYPE`), object validity (`E_INVIND`), and lookup (`E_PROPNF`) before applying `db_property_allows(..., RUN_ACTIV.progr, PF_READ)` and returning `E_PERM` on denial. `OP_PUT_PROP` applies the analogous `PF_WRITE` check and returns `E_PERM` before any store.
- `/root/src/toaststunt/src/db_properties.cc:747-753` defines permission exactly as flag present, programmer equals property owner, or programmer is Wizard.
- The row's value and name types are valid, the object is live, and the property exists, so those earlier Toast errors do not compete; `E_PERM` is the direct permission result.
- A couple of broad `rg` commands included nonexistent wildcard/path arguments and printed harmless path errors while still locating source; they changed no files and are not evidence substitutes.
- No tracked row-31 edit or managed run has occurred. Current blocker: none; authority gate remains incomplete until setup `add_property`/programmer context, committed Banteng cause, evidence record, and live Toast row are complete.
- Immediate next action: finish Toast `bf_add_prop`/task programmer context, collect subagent results, then personally trace Banteng.

## Toast setup and Banteng owner location

- Toast `property.cc:207-234` `bf_add_prop` validates `{owner, flags}`, object type/validity, object write permission, and requested owner permission before creating the property. In the row, Wizard bypasses object `.w = 0` and may assign owner `#0`; empty flags produce neither `PF_READ` nor `PF_WRITE`.
- Therefore the later programmer read/write encounters a valid existing property whose owner/flags are exactly those asserted by the permission checks.
- Committed Banteng property bytecodes are compiled at `MooCompiler.java:266,308`, dispatched at `MooVm.java:98-99`, and implemented in the `MooVm` methods around lines 317-345. Current world access owners are `WorldTxn.readObjectProperty` around line 194 and `writeObjectProperty` around line 213.
- The observed managed error is `E_TYPE`, so the next evidence question is whether the VM collapses world read/write failure to `E_TYPE` without programmer/property metadata, rather than representing permission denial.
- No tracked row-31 edit or managed run has occurred. Current blocker: none; authority gate incomplete.
- Immediate next action: read those exact Banteng methods and property record metadata/storage.

## Causal correction: displayed E_TYPE is secondary

- The earlier premise that the managed `E_TYPE` came from ordinary property permission handling was wrong.
- Committed Banteng does not implement built-in object property `.w`. `WorldTxn.readObjectProperty` handles only name/location/owner/programmer/wizard; `writeObjectProperty` handles only name/owner/programmer/wizard and ordinary local slots. Therefore setup `obj.w = 0` fails with `E_PROPNF` through `MooVm.setProperty`.
- The conformance runner captures that failed setup result as the variable `obj` instead of stopping the row. Substitution then makes the next expression effectively `return E_PROPNF.audit_secret;`; `MooVm.getProperty` sees an error value receiver and correctly raises the displayed secondary `E_TYPE`.
- Once `.w` setup succeeds, a second defect remains: `WorldTxn.readObjectProperty` returns ordinary values and `writeObjectProperty` mutates them without consulting the active programmer or `WorldProperty.owner/permissions`, so the programmer would wrongly read/write instead of receiving `E_PERM`.
- The active row remains valid and the target is still exactly one row, but its mandatory authority gate must now cover the causally prior built-in `.w` setup contract before ordinary property permission design.
- Barn/Toast authority subagents received follow-up tasks for exact built-in `.w` semantics. No production/design action is authorized until those return and the live row is proven.
- Barn normative `spec/objects.md:197-220` states `.w` is a writable integer built-in object flag; flag properties treat zero as clear and any non-zero value as true. Permission and error precedence still require implementation/Toast evidence.
- No tracked row-31 edit or managed run has occurred. Current blocker: none; expanded authority gate incomplete.
- Immediate next action: personally trace Barn and Toast `.w` public write/flag storage, then write the complete row evidence record and run the exact pinned Toast row.

## Restart continuation: focused regression red

- Verified the Git ledger before editing: branch `master` was ahead of origin by 20; the only tracked pre-existing modification was `docs/reports/splice-computed-access-authority.md`. All `notes-*.md` files and `pyghidra_mcp_projects/` remained untracked and must not be staged.
- Added one focused JUnit regression, `MooVmTest.clearsObjectWriteFlagThenDeniesUnpermittedPropertyAccess`, without introducing a helper or fixture layer.
- The regression exactly performs the row sequence: Wizard #3 creates an object, clears `obj.w`, adds `audit_secret` with owner #0 and empty permissions, then Programmer #4 attempts read and write. It also freezes the unchanged value after denied write.
- Ran `gradlew test --tests moo.vm.MooVmTest.clearsObjectWriteFlagThenDeniesUnpermittedPropertyAccess` with the required JDK. It failed in 6 seconds at `MooVmTest.java:769`, the first setup-outcome assertion. This is the expected causal red surface for the currently missing `.w` built-in write path; the precise actual assertion value has not yet been inspected after the checkpoint.
- No production source has been edited for row 31 yet.
- Current blocker: none. The immediate next step after this checkpoint is to inspect the focused test failure report only to confirm the red is `ERRORED`/`E_PROPNF`, then add the already-frozen minimal `.w` write case in `WorldTxn.writeObjectProperty` and rerun the same focused test. The expected next red after that is missing ordinary-property read permission enforcement.

## Row 31 implementation and exact managed green

- Confirmed the first regression red was `expected RETURNED but was ERRORED` at the setup outcome.
- Added only the frozen `w` case in `WorldTxn.writeObjectProperty`: integer RHS toggles existing object flag bit 32 through `replaceFlags`.
- Reran the focused regression. Setup and metadata assertions passed; the test advanced to the programmer read and failed `expected ERRORED but was RETURNED`, proving the second row-relevant defect.
- Added inline ordinary-property authorization at the existing VM get/set owners. The checks use `WorldTxn.property`, the current `VmState.programmer`, programmer-object wizard bit 4, property owner, and permission bits 1 for read and 2 for write. No helper, adapter, interface, or alternate mutation path was added. Built-in properties remain on the existing world path because they do not resolve as ordinary `WorldProperty` metadata.
- The focused JUnit regression passed in 3 seconds. The immutable implementation plan was then reread in full as required.
- `gradlew spotlessApply` passed in 1 second.
- `gradlew installDist` passed in 2 seconds.
- Exact managed Banteng row `audit_property_flags_deny_nonowner_read_and_write` passed: 1 selected, 11,504 deselected, in 3.73 seconds.
- Post-run process inventory found exactly one managed Banteng Java process: PID 379984, parent PID 20368, database `C:/Users/Q/AppData/Local/Temp/moo_conformance_qnejofph/Test.db`, checkpoint sibling `.new`, port 4221.
- Current blocker: none. The next literal action is to stop only PID 379984, then re-inventory to prove no managed Banteng process remains. After cleanup, run the substantial `gap_followups_toast_oracle` category, clean up its exact process, reread the plan, and continue the row 31 gates.

## Broader family and full-gate regression

- Stopped exact-row PID 379984 and confirmed the managed Banteng process inventory was empty.
- Ran `gap_followups_toast_oracle` with fail-fast. The first 10 rows passed, retaining row 31; the next unchecked row `audit_intrinsic_command_table_roundtrip` failed because `connection_options(...)` produced `E_VERBNF`. Receipt: 10 passed, 1 failed, 11,488 deselected in 28.65 seconds.
- Inventoried the family process as exact PID 24712 using temp database `moo_conformance_pre95hjl/Test.db` on port 55207, stopped only that PID, and confirmed the managed Banteng inventory was empty.
- Reread the immutable implementation plan in full after the substantial category.
- Ran `gradlew clean check installDist`. Formatting and distribution tasks passed, but the full JUnit suite failed 1 of 144 tests at `MooRuntimeTest.continuesUserConnectedConfuncsAfterForkSuspendAndTaskPermissionChange`.
- Exact mismatch: expected the post-`set_task_perms(user)` connection-function state to be fully populated, but all later write-dependent values remained empty.
- Traced the failure to an existing defect in the same property-permission representation: `BuiltinCatalog.addProperty` parsed `r` to bit 1 and `c` to bit 4 but silently dropped `w`; therefore `add_property(#0, ..., {#0, "rw"})` persisted read-only permissions. The new correct VM write check then denied the writes after task permissions switched away from Wizard.
- This is not a wrong-programmer or inherited-property defect. The already-read Barn/Toast authority freezes ordinary property write permission as bit 2.
- Added the missing in-place `w` to bit 2 mapping in `BuiltinCatalog.addProperty`. No helper or alternate path was introduced.
- Current blocker: none. Immediate next step is to rerun only `MooRuntimeTest.continuesUserConnectedConfuncsAfterForkSuspendAndTaskPermissionChange`; if green, rerun the focused row 31 regression, then the clean full gate. Row 31 remains uncommitted and no next slice has begun.

## Final row 31 gates and staged commit state

- The exact runtime regression `MooRuntimeTest.continuesUserConnectedConfuncsAfterForkSuspendAndTaskPermissionChange` passed in 5 seconds after restoring ordinary property write bit 2 parsing.
- Reread the immutable implementation plan in full after that substantial targeted green.
- The focused row 31 JUnit regression passed again in 1 second.
- The repeated `gradlew clean check installDist` gate passed all 144 tests, formatting, checks, and distribution in 15 seconds.
- Reread the immutable implementation plan in full after the passing full suite.
- Updated the tracked row 31 evidence section to remove the now-false statement that ordinary `w` parsing remained deferred, record the full-gate discovery/correction, and record the focused, managed, family, and full-suite receipts.
- `git diff --check` passed.
- Inspected the entire exact diff. It is limited to five tracked paths: the authority report, `BuiltinCatalog.java`, `MooVm.java`, `WorldTxn.java`, and `MooVmTest.java`.
- Staged exactly those five tracked paths. `git status --short` shows all five staged and only the known notes and `pyghidra_mcp_projects/` untracked; no note or analysis artifact is staged.
- Current blocker: none. The immediate next literal action is to commit this kept row 31 slice before beginning row 32. Proposed commit message: `fix: enforce ordinary property permissions`.
