# Row 38 focused regression

- Scope: edit only `src/test/java/moo/runtime/MooRuntimeTest.java`; no production, docs, plan, other tracked files, staging, or commit.
- Required behavior: use distinct logged-in Wizard and Programmer connections/tasks. Wizard creates a non-fertile parent; Programmer creates a child and calls `chparent(child, parent)`.
- Durable expectation: the Programmer receives `E_PERM`, and the child's parent remains unchanged.
- Required valid red: current production must produce `E_TYPE` at the same permission/type boundary while topology remains unchanged; setup failures are invalid.
- Initial ledger: branch `master` is ahead of origin by four commits with no tracked changes. Existing untracked notes and `pyghidra_mcp_projects/` are preserved.
- Exact durable row read from `../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:911-936`: Wizard creates `parent_obj = create($nothing)`, clears `parent_obj.f`, Programmer creates `child = create($nothing)`, then Programmer executes `chparent(child, parent_obj)` and expects `E_PERM`; Wizard recycles both.
- Test fixture players include Programmer `#4` with flags `3` and Wizard `#8` with flags `7`; the runtime login seam should therefore use separate `connect Wizard` and `connect Programmer` connections.
- Current `BuiltinCatalog` `chparent` validates two object arguments, object existence, recursive topology, and parent validity before testing task permissions. Its final permission check currently returns `E_PERM` for every nonwizard, then changes the parent only for a wizard.
- The managed family receipt nevertheless records actual `E_TYPE` for this exact row, so the focused regression must reproduce the two-task execution path rather than infer behavior from the builtin method alone.
- Existing runtime error marker convention is `{2, {E_TYPE}}` inside the configured connection prefix/suffix.
- Root cause of the managed `E_TYPE` is now proven from current production source: `WorldTxn.writeObjectProperty` implements built-in flag writes for `programmer`, `wizard`, and `w`, but not `f`. Therefore the durable row fails during Wizard setup at `parent_obj.f = 0`; it never reaches Programmer's `chparent` call.
- A newly created object already has flags `0`, so it is non-fertile without that assignment. `BuiltinCatalog`'s direct valid-object `chparent` path then returns the required `E_PERM` for any nonwizard and leaves topology unchanged.
- The requested focused red cannot validly be written against the stated chparent permission boundary: omitting `.f = 0` would exercise already-correct production and pass, while including it would manufacture a setup-failure regression for a different intrinsic-property surface.
- Per the issue-invalidity rule, the earlier attribution of `E_TYPE` to `chparent` is invalid. No tracked file was edited, and no test was run, staged, or committed.
- Current blocker: the active target must be corrected by the parent/user. The failing surface is missing intrinsic fertile-flag property support, not `chparent` permission semantics.

## Corrected focused slice

- Parent corrected the active target to the missing intrinsic fertile-flag write only.
- Required regression: logged-in Wizard creates an ordinary object, evaluates `obj.f = 0`, and returns the assignment result so the runtime marker proves it is integer `0`; Java independently asserts flag bit `128` remains clear. MOO readback of `obj.f` and `chparent` behavior are excluded.
- Initial ledger for the corrected slice: `master` is ahead of origin by five commits with no tracked changes; only the existing untracked notes and `pyghidra_mcp_projects/` are present.
- Current blocker: none. Next action is to add exactly one focused method to `MooRuntimeTest.java` and run only it under Java 25.
- Added focused method `writesIntrinsicFertileFlagAsIntegerZero` in `MooRuntimeTest.java` only.
- The method logs in as Wizard, creates one ordinary object, executes `return object.f = 0;`, captures output, asserts in Java that `flags & 128` is `0`, then expects the successful runtime marker `{1, 0}`. It never reads `object.f` in MOO and does not exercise `chparent`.
- Java 25 focused command: `.\\gradlew.bat test --tests moo.runtime.MooRuntimeTest.writesIntrinsicFertileFlagAsIntegerZero` with `JAVA_HOME=C:\\Users\\Q\\scoop\\apps\\openjdk\\current`.
- Valid red: Java's bit-128 assertion passed, then the output assertion failed. Expected `[-=!-^-!=- , {1, 0}, -=!-v-!=-]` without the display spacing around the marker; actual was `[-=!-^-!=- , {2, {E_PROPNF}}, -=!-v-!=-]`. The exact XML payload is expected `{1, 0}` versus actual `{2, {E_PROPNF}}`.
- `git diff --check -- src/test/java/moo/runtime/MooRuntimeTest.java` is clean. The only tracked change is that test file; existing untracked files are preserved. Nothing was staged or committed.
- Current blocker: production lacks intrinsic `f` write handling. The parent agent owns the production slice.
