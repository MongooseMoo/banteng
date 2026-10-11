# Row 31 Barn authority trace

- Task: read-only trace for `audit_property_flags_deny_nonowner_read_and_write`.
- Required evidence: exact durable conformance row, normative Barn spec, and current Barn implementation from public property read/write entry points through permission and flag ownership.
- Boundaries: do not edit tracked files, do not run managed servers, do not propose Java design.
- Current findings:
  - Durable row is `moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:750-770`. Wizard creates an object, clears its object `w` flag, defines `audit_secret` with owner `#0` and empty property permissions, then the authenticated Programmer must receive `E_PERM` on both direct read and direct write.
  - Normative Barn contract is `spec/objects.md:139-192` and `:453-485`, `spec/operators.md:53-88` and `:813-840`, plus `spec/errors.md:117-168`: property owner or wizard bypasses; otherwise `r` gates reads and `w` gates writes; invalid object precedes property existence on reads; missing property is `E_PROPNF`; denied existing property is `E_PERM`.
  - Public syntax compiles to `OP_GET_PROP` / `OP_SET_PROP` in `bytecode/compiler.go:966-988` and `:1481-1506`; dispatch is `vm/vm.go:461-465`.
  - `vm/op_property.go:15-83` validates type/object/existence, resolves the property, then calls `checkPropertyReadPerm`; `:137-222` does the analogous local/inherited write path; `:248-281` allows wizard or property owner and otherwise requires `PropRead` / `PropWrite`, returning `E_PERM`.
  - The store is sole mutable owner of property metadata (`db/store/object.go:82-132`); flag bits are `PropRead`, `PropWrite`, `PropChown` at `:256-267`; `FindProperty` returns the owner/perms view at `db/store/store_properties.go:68-122`.
  - Row setup is represented exactly by `builtins/properties.go:197-322`: empty permissions parse to zero; explicit `#0` is stored as owner; only a wizard may select another owner. The object-level `.w = 0` is separate from the property-level empty permission flags.
  - Managed eval supplies a non-nil context with `Programmer = authenticated player` and `IsWizard` derived from its wizard flag in `scheduler/eval.go:55-79` and `scheduler/scheduler.go:250-252`.
  - Static agreement: current Barn code should already return `E_PERM` for both operations in this exact row. No managed server was run, as required.
  - Uncertainty: no focused Barn unit test was found for these two VM permission branches; this is source authority, not an execution receipt. `checkProperty*Perm` bypasses when VM context is nil, but the managed eval route supplies a context, so that does not affect this row.
- Current blocker: none.
- Next step: report the exact authority trace to the parent agent; no Java design or file changes.

## Follow-up: causally prior `obj.w = 0`

- Normative Barn spec: `spec/objects.md:196-215` defines `.w` as a writable INT built-in object property; zero clears it and any nonzero value sets it. `spec/objects.md:453-485` says object modification requires target owner or wizard and wizards bypass. `spec/builtins/objects.md:253-285` identifies `.w` as the Writable object flag but does not separately spell out its principal; its nearest general rule is owner-or-wizard object modification.
- Public implementation route: `bytecode/compiler.go:728-736` duplicates the assigned value so a successful assignment evaluates to that value, and `:966-988` emits `OP_SET_PROP`; `vm/vm.go:461-465` dispatches to `executeSetProp`.
- `vm/op_property.go:137-191` validates property-name type, receiver type, store, and live object before calling `setBuiltinProperty`. `builtins.IsBuiltinProperty` includes `w` at `builtins/properties.go:507-515`.
- Current `.w` branch is `vm/op_property.go:359-426`, specifically `:422-426`: for an INT it calls `Store.SetObjectFlag(objID, FlagWrite, value.Int() != 0)` and returns success. It does not consult `ctx`, target owner, caller/programmer, wizard status, or the flag's prior value. Thus current Barn has no permission principal for `.w`, contrary to the nearest normative owner-or-wizard rule; the exact row still succeeds because setup runs as wizard.
- Exact state effect: `db/store/object.go:226-248` defines `FlagWrite = 1 << 5` (32) on `Object.flags`; `db/store/store_core.go:182-195` clears that bit for false/zero and sets it for true/nonzero. This is distinct from ordinary property `PropWrite = 1 << 1` in `db/store/object.go:256-267`, stored in `Property.perms` (`:82-105`).
- Successful `obj.w = 0` leaves the original integer 0 as the assignment expression result and clears object flag bit 32. A store error is surfaced by `executeSetProp` as that error. Wrong-value type does not produce a direct built-in type error: `setBuiltinProperty` returns `(false, E_NONE)`, the VM falls through to ordinary-property lookup, and because `w` cannot be defined as an ordinary property it ends at `E_PROPNF`. The spec states INT but does not explicitly name the wrong-value error here.
- Relation to row target: none at the implementation permission decision. Ordinary read/write checks inspect `PropertyView.Perms.Has(PropRead/PropWrite)` in `vm/op_property.go:248-281`; `.w` changes `Object.flags.FlagWrite` only. The row's `{#0, ""}` independently creates zero ordinary property permission bits.
- Current blocker: none. No managed server was run and no tracked file was edited.
