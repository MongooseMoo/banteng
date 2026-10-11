# Phase 3 basic/object checkpoint

- Current branch: `master`, ahead of `origin/master` by 159 commits.
- Tracked worktree state is clean at the start of this slice; existing `notes-*.md` files and `pyghidra_mcp_projects/` are untracked and must remain uncommitted.
- The controlling plan's next exact conformance family is `src/moo_conformance/_tests/basic/object.yaml`.
- The managed Toast stock oracle run completed before the process restart: 12 passed and 1 explicit skip (`children_of_system_object`).
- The corresponding managed Banteng run was started before the process restart, but its process/result no longer exists and cannot be recovered.
- Current blocker: none. The missing Banteng measurement must be re-established from the current worktree.
- Next action: run the exact managed Banteng stock command for `src/moo_conformance/_tests/basic/object.yaml`; use that result to decide whether this family requires a source slice.

## Re-established Banteng baseline

- Exact managed Banteng stock result: 4 passed, 8 failed, 1 explicitly skipped in 6.55 seconds.
- Passing rows: new-object `name`, `location`, `programmer`, and `wizard`.
- Failing rows: `parent` (`E_VERBNF`), `contents` (`E_PROPNF`), `r` (`E_PROPNF`), `w` (`E_PROPNF`), `f` (`E_PROPNF`), `is_player` (`E_VERBNF`), `valid(#-1)` (`E_VERBNF`), and `valid(#0)` (`E_VERBNF`).
- `BuiltinCatalog.create` already delegates to `WorldTxn.createObject(parent, programmer)`.
- `WorldTxn.createObject` already stores the requested parent, empty contents, zero flags, owner, and location `#-1`, and updates the parent's child list.
- The existing VM object-property path delegates to `WorldTxn.readObjectProperty`; missing built-in object properties account for the `contents` and `r`/`w`/`f` failures.
- The builtin manifest has no currently observed handlers for `parent`, `is_player`, or `valid`; those calls account for the `E_VERBNF` failures.
- External Barn/Toast authority audit is active and constrained to these eight rows.
- Current blocker: none. Before editing, finish the exact authority trace and identify the smallest slice through existing `BuiltinCatalog`, `WorldTxn`, and VM property owners.

## Authority trace in progress

- Barn `spec/objects.md` confirms object flag bits USER=1, PROGRAMMER=2, WIZARD=4, READ=16, WRITE=32; the remaining fertile flag still needs an exact source-level confirmation.
- Barn documents `parent(obj)` as the first-parent projection and `.contents` as a built-in object property.
- The first Toast search was too broad because it included `toastcore.db`; its results are not being used as source-level authority.
- The next authority action is a path-limited search of the current `~/src/toastcore` C/C++ builtin/object sources for `parent`, `valid`, `is_player`, and built-in object-property flag projections.
- No production or test code has been edited.

## Red-test slice

- The clean `~/src/toastcore` checkout is a database repository containing `README.md`, `toastcore.db`, and `changelog.txt`; it is not the server builtin implementation.
- The managed server source checkout is `~/src/toaststunt`. Its tracked branch is clean; existing untracked build/debug artifacts were not modified.
- Toast `src/objects.cc` proves: `valid` accepts ANY and returns `is_valid` as INT; `parent` accepts ANY, returns the first parent or `#-1`, and returns `E_INVARG` for an invalid object; `is_player` accepts OBJ, returns the player-index result as INT, and returns `E_INVARG` for an invalid object.
- Toast `src/db_properties.cc` proves `.contents` returns the stored contents list and `.r`, `.w`, `.f` project `FLAG_READ`, `FLAG_WRITE`, and `FLAG_FERTILE` as INT.
- Added red tests in `BuiltinCatalogTest` for the three exact manifest contracts and transactional results.
- Added a red test in `WorldTxnTest` for `.contents`, `.r`, `.w`, and `.f` projections.
- No production code has been edited yet.
- Next action: run exactly the two affected Gradle test classes under WSL Java 25. The expected red result must identify only the missing manifest entries/projections before production code is changed.

## Kept implementation candidate

- The behavioral red state was established after fixing one missing test import: `BuiltinCatalogTest` failed for absent manifest entries and `WorldTxnTest` failed for absent built-in projections.
- Production changes are limited to existing owners: `BuiltinCatalog` now registers and implements `parent`, `is_player`, and `valid`; `WorldTxn.readObjectProperty` now projects `contents`, `r`, `w`, and `f`.
- No new interface, helper, sender, or adapter was introduced.
- The exact affected unit-test gate now passes: `moo.builtin.BuiltinCatalogTest` plus `moo.world.WorldTxnTest`, 30 tests total.
- After the required plan reread, the exact managed Banteng `basic/object.yaml` run passed 12 rows and skipped the same explicit `children_of_system_object` row as Toast.
- Current blocker: none.
- Next action: reread Phase 3 after the passing managed run, then execute the plan's named Gradle gate, `./gradlew check`, and `git diff --check` as separate steps. If all pass, inspect/stage only the four source/test files and commit this slice before moving to `basic/property.yaml`.

## Committed result

- Full `JAVA_HOME=/opt/java/25 ./gradlew check` passed.
- `git diff --check` and the staged-index whitespace check passed.
- Exact staged scope was four files: `BuiltinCatalog.java`, `WorldTxn.java`, `BuiltinCatalogTest.java`, and `WorldTxnTest.java`; no notes were staged.
- Commit: `dc2ed5b feat: add phase 3 object queries`.
- The `basic/object.yaml` family is complete against the measured Toast oracle for all non-skipped rows.
- Next exact plan-listed family: run `src/moo_conformance/_tests/basic/property.yaml` once with `toast stock`, then once with `banteng stock`, and use the first Banteng failure as the next single-family slice if the results differ.
