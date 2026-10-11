# Row 31 Banteng read-only investigation

- Task: trace `audit_property_flags_deny_nonowner_read_and_write` from its durable conformance row through committed Banteng property read/write, VM/world, and error creation.
- Scope: read-only investigation; do not edit production/tests/docs and do not run managed conformance.
- Required result: explain why a non-owner returns `E_TYPE` rather than `E_PERM`, and identify the smallest existing owned seam with exact paths and lines.
- Current state: investigation has not started in this restarted process.
- Observed: prior context reports the exact failure as `E_TYPE` where the row expects `E_PERM`; this is not yet independently verified from repo evidence in this process.
- Tried: no source or conformance inspection yet.
- Worked: checkpoint state recorded before investigation.
- Did not work: nothing attempted yet.
- Blocker: none known.
- Next step: locate the exact durable row, then follow the committed read/write execution paths and error construction without changing files.

## Investigation checkpoint

- Durable row is `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:750-770`.
- Setup creates an object as Wizard, clears its object public-write flag (`obj.w = 0`), and adds `audit_secret` with value `10`, owner `#0`, and empty property permission flags. Programmer read and write each expect `E_PERM`.
- Existing run receipt at `notes-banteng-row30-restart.md:91-98` proves setup and the preceding nine rows completed; the first non-owner read expected `E_PERM` but returned `E_TYPE`. Therefore the observed type error is not from `add_property` setup.
- `BuiltinCatalog.addProperty` at `src/main/java/moo/builtin/BuiltinCatalog.java:1349-1371` accepts the exact fourth-argument shape `{OBJ, STR}`, parses `r` and `c`, and stores empty flags as permission bits `0` through `WorldTxn.addProperty`.
- `WorldTxn.addProperty` at `src/main/java/moo/world/WorldTxn.java:542-554` stores owner and permissions in the existing `WorldProperty`.
- VM `GET_PROPERTY` at `src/main/java/moo/vm/MooVm.java:313-331` only raises `E_TYPE` when receiver/name operands are not OBJ/STR, calls `WorldTxn.readObjectProperty`, and otherwise only raises `E_PROPNF`; it passes no programmer context.
- VM `SET_PROPERTY` at `src/main/java/moo/vm/MooVm.java:333-348` has the analogous operand `E_TYPE`, calls `WorldTxn.writeObjectProperty`, and maps false only to `E_PROPNF`; it passes no programmer context.
- `WorldTxn.readObjectProperty` at `src/main/java/moo/world/WorldTxn.java:193-210` returns ordinary property values directly without inspecting owner/permissions.
- `WorldTxn.writeObjectProperty` at `src/main/java/moo/world/WorldTxn.java:212-275` replaces ordinary property values without inspecting owner/permissions; its comment says authorized but the method has no actor argument.
- Current open question: the exact origin of the read-side `E_TYPE`, because committed GET_PROPERTY source would return `10` if it received OBJ/STR. Next step is to trace capture interpolation, compilation/bytecode ordering, frame programmer ownership, and error construction to identify the causal mismatch and smallest existing seam.
- Current blocker: none.

## Causal finding

- The managed failure's `E_TYPE` is secondary, not the property-permission result.
- The setup is one captured run. It executes `obj.w = 0` before `add_property` and `return obj` (`gap_followups_toast_oracle.yaml:753-759`).
- Banteng `WorldTxn.readObjectProperty` only recognizes built-in `name`, `location`, `owner`, `programmer`, and `wizard` (`WorldTxn.java:193-209`). `WorldTxn.writeObjectProperty` only recognizes `name`, `owner`, `programmer`, and `wizard`, then searches ordinary stored properties (`WorldTxn.java:212-274`). There is no built-in object `w` case.
- Therefore `obj.w = 0` reaches `WorldTxn.writeObjectProperty`, returns false, and `MooVm.setProperty` maps false to `E_PROPNF` (`MooVm.java:333-345`). The rest of the setup does not execute.
- The conformance runner intentionally captures a failed captured step as `result.error` (`runner.py:420-450`). Variable substitution converts `E_*` strings back to unquoted MOO error literals (`runner.py:506-523`; `schema.py:440-454`). Thus the next source becomes effectively `return E_PROPNF.audit_secret;`.
- Parser/compiler correctly lower that property access, but `MooVm.getProperty` sees an ErrorValue receiver instead of ObjectValue and raises `E_TYPE` at `MooVm.java:313-319`. This is exactly why the displayed first read mismatch is `E_TYPE`.
- Once the missing built-in `w` setup path is repaired, a second independent defect remains: ordinary property reads/writes ignore `WorldProperty.owner()` and `WorldProperty.permissions()` (`WorldTxn.java:193-210,262-271`). Without authorization they would return `10` and write `11`, not `E_PERM`.
- Existing ownership seam: `MooVm` GET_PROPERTY/SET_PROPERTY already owns operand validation and exact MOO error raising (`MooVm.java:313-348`), and it already receives `VmState`; `VmState.programmer()` exposes the active frame's task programmer (`VmState.java:108-118`). `MooVm.invokeBuiltin` demonstrates existing use of that actor at `MooVm.java:399-415`. `WorldTxn.property` exposes persisted `WorldProperty` metadata at `WorldTxn.java:175-190`; `WorldProperty` already stores value, owner, permissions (`WorldProperty.java:6-12`). No interface/helper/adapter/sender is needed to join these existing owners.
- Property permission parsing also currently omits the `w` bit: `BuiltinCatalog.addProperty` recognizes `r` and `c` only (`BuiltinCatalog.java:1349-1371`). This does not affect the active row's empty flags, but it is direct evidence relevant to the same read/write flag surface.
- Git verification: tracked worktree is clean at the investigation checkpoint; only the known untracked notes and `pyghidra_mcp_projects/` are present.
- Attempt that did not work: one final `rg` for the eval path failed because a double-quoted PowerShell regex split at the semicolon. It made no changes and is unnecessary to the established cause.
- Current blocker: none. Next step is only to pin active eval/programmer construction if needed, then send the read-only evidence to the parent.
