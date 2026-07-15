# Object parent authority

## Active slice

This record freezes only the contract asserted by
`../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:911-936`,
`audit_chparent_nonwizard_requires_fertile_parent`. The durable row was added
by conformance commit `81d1157` (`Add move and chparent trust Toast oracles`).

The row creates a non-fertile object as Wizard, creates a separate child as
Programmer, and has Programmer call `chparent(child, parent_obj)`. It expects
`E_PERM` because Programmer does not own the proposed parent and that parent
does not carry the fertile flag.

## Normative Barn specification

`../barn/spec/builtins/objects.md:208-217` specifies
`chparent(object, new_parent)`, with `E_PERM` for a caller who is not the
owner or a wizard. The same section currently lists a non-fertile new parent
under `E_INVARG`; that wording is incomplete for the active row because both
current implementations return `E_PERM` when a nonwizard neither owns the
parent nor receives its fertile allowance.

No other Barn specification section found during this slice states the exact
composite rule. The live Toast oracle and implementation below resolve the
omission for this row.

## Current Barn implementation

`../barn/builtins/registry.go:141-154` registers `chparent` directly to
`builtinChparent`. The semantic owner is
`../barn/builtins/objects_hierarchy.go:127-235`:

1. lines 130-144 validate arity and object-reference argument types;
2. lines 146-176 validate the target, proposed parent, and ancestry;
3. lines 178-205 reject direct and descendant property conflicts;
4. lines 207-220 apply the active permission rule; and
5. lines 225-233 call the store mutation only after authorization succeeds.

For a nonwizard and a proposed parent other than `$nothing`, Barn reads the
parent owner and `dbstore.FlagFertile`. It returns `E_PERM` unless the current
effective programmer owns the parent or the parent is fertile. Programmer in
the active row satisfies neither alternative.

`../barn/kernel/context.go:11-22,40-41` owns the effective `Programmer` and
wizard status used by the builtin. `../barn/db/store/object.go:230-239`
defines `FlagFertile` as bit 128, meaning the object can be used as a parent.
`../barn/db/store/store_relationships.go:271-295` owns the later hierarchy
mutation and contains no permission policy.

## Pinned Toast implementation

The authoritative WSL checkout was freshly identified with:

```powershell
wsl -d Debian -u root -e git -C /root/src/toaststunt rev-parse HEAD
```

It returned `aecc51e9449c6e7c95272f0f044b5ba38948459e`.

At that commit, `/root/src/toaststunt/src/objects.cc:1352-1355` registers
`chparent` with two arguments and dispatches it to
`bf_chparent_chparents`. The semantic owner at `src/objects.cc:553-601`:

1. returns `E_TYPE` for non-object inputs at lines 560-563;
2. validates object references at lines 570-577;
3. applies the composite permission check at lines 578-585;
4. checks recursive parenting at lines 586-590; and
5. calls `db_change_parents` only after those checks at lines 591-599.

`controls2` at `src/objects.cc:41-45` permits a wizard or the child's owner.
The active Programmer owns the child, so that half of the composite check
passes. `db_object_allows` at `src/db_objects.cc:1268-1274` permits a wizard,
the proposed parent's owner, or a caller granted the requested flag.
`FLAG_FERTILE` is the permanent fertile flag in `src/include/db.h:252-262`.
The active Programmer is not a wizard, does not own the proposed parent, and
the parent is not fertile, so `db_object_allows` is false and the builtin
returns `E_PERM` before mutation.

`db_change_parents` at `src/db_objects.cc:1030-1146` owns integrity checks and
the parent/children topology update. It is not reached for this denial.

## Agreements and disagreement

Barn and Toast agree on the row's observable result and the policy owner:
authorization occurs in the `chparent` builtin, and hierarchy mutation is not
attempted after denial. Both permit the active nonwizard to control their own
child and deny the unowned, non-fertile proposed parent with `E_PERM`.

Their broader validation order differs. Toast performs its composite
permission check before recursive-parent and database integrity checks. Barn
performs explicit ancestry and property-conflict checks before its parent
permission check. The active row contains no cycle or property conflict, so it
does not resolve that ordering disagreement. Banteng must not generalize this
slice into those uncovered cases.

## Managed oracle resolution

The exact durable row was rerun against the pinned stock-Toast profile:

```powershell
$wslIp=(wsl -d Debian -u root -e hostname -I).Trim()
uv run --project C:\Users\Q\code\moo-conformance-tests moo-conformance `
  --moo-host $wslIp `
  --server-command "wsl -d Debian -u root -e env TOAST_MOO=/root/src/toaststunt/build-release/moo bash /mnt/c/Users/Q/code/barn/scripts/run_toast_wsl.sh {db} {port}" `
  --server-db C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_db/Test.db `
  --oracle-profile-manifest C:/Users/Q/code/barn/profiles/toast/stock-wsl-testdb.json `
  --target-profile-manifest C:/Users/Q/code/barn/profiles/toast/stock-wsl-testdb.json `
  -k "audit_chparent_nonwizard_requires_fertile_parent"
```

Result: one passed, 11,518 deselected, in 4.07 seconds. The managed Toast
process exited. The unrelated long-running Toast PID 19 for `/tmp/td.db` on
port 9482 remained untouched.

The preceding managed Banteng family run passed its first 16 rows and then
stopped at this row: Banteng returned `E_TYPE` where the durable row expects
`E_PERM`. That observation is a focused red signal, not yet an explanation of
which setup or builtin boundary produced the earlier error.

## Frozen contract and exclusions

For this slice, a nonwizard who controls the child but does not own the
different, valid proposed parent may use that parent only when it is fertile.
When the proposed parent is not fertile, `chparent(child, proposed_parent)`
must raise `E_PERM` and must not change either object's parent/children
topology.

This row does not decide argument-type coercion, invalid references,
`$nothing`, self-parenting, descendant cycles, property conflicts, anonymous
objects, multiple parents, `chparents`, ownership of the child, successful
fertile parenting, successful parent-owner parenting, wizard behavior, or the
relative error precedence among those surfaces. Each requires its own durable
row and authority gate.

The remaining pre-design question is why the current managed Banteng row
surfaces `E_TYPE` despite `BuiltinCatalog`'s direct two-object `chparent` path
already ending in a wizard-only `E_PERM` check. A focused Banteng regression
must reproduce the durable row's setup and prove the exact failing boundary
before the smallest Java change is chosen.

## Corrected failing boundary: intrinsic fertile flag assignment

The earlier attribution of Banteng's `E_TYPE` to `chparent` was wrong. A
focused trace of the durable row proved that execution fails during its first
Wizard setup step at `parent_obj.f = 0`; the later Programmer `chparent` call
is never reached with two object references. No misleading `chparent`
regression or production edit was made.

The current Banteng source confirms the boundary.
`src/main/java/moo/vm/MooVm.java:423-447` routes object property assignment to
`WorldTxn.writeObjectProperty`. The owner at
`src/main/java/moo/world/WorldTxn.java:244-335` recognizes intrinsic writes
for `programmer`, `wizard`, and `w`, but not `f`. The missing name falls
through to ordinary-property lookup and returns false, which the VM surfaces
as a property error. Fresh objects already have flag bits zero, so omitting
the assignment makes the existing direct `chparent` path return the expected
`E_PERM`; that would not reproduce the durable row's actual failure.

### Barn fertile-property authority

`../barn/spec/objects.md:198-215` defines `.f` as a writable integer built-in
property and states that flag properties treat any nonzero integer as true
and only zero as false. The active row uses integer zero.

Barn's direct-property entry is
`../barn/vm/op_property.go:128-223`. It checks for a built-in assignment
before ordinary property lookup and delegates to `setBuiltinProperty` at
lines 352-439. The `f` case at lines 427-430 accepts an integer and calls
`Store.SetObjectFlag` with `FlagFertile` and `value.Int() != 0`.
`../barn/db/store/store_core.go:182-196` owns the flag mutation, and
`../barn/db/store/object.go:230-239` assigns `FlagFertile` bit 128.

### Toast fertile-property authority

At the pinned Toast identity already recorded above, object assignment enters
`OP_PUT_PROP` at `/root/src/toaststunt/src/execute.cc:1959-2080`. For built-in
`BP_F`, lines 2045-2055 permit a wizard or an unprotected owner and then call
`db_set_property_value`. The active setup caller is Wizard.

`/root/src/toaststunt/src/db_properties.cc:620-685` maps `BP_F` to
`FLAG_FERTILE` and clears the bit when the assigned value is false.
`src/include/db.h:252-262` defines that permanent flag. Toast accepts general
falsey values for this built-in, while Barn's current implementation accepts
only integers. The active row assigns integer zero, so the row does not
resolve that broader disagreement.

### Corrected frozen contract and exclusions

For the setup surface covered by the durable row, a Wizard assignment
`object.f = 0` on a valid ordinary object must succeed, evaluate to the
assigned integer zero, and leave the object's fertile flag clear. It must not
fall through to ordinary-property lookup or raise a property/type error.

This corrected slice does not authorize changing `chparent`: its active
nonwizard denial is already correct once setup succeeds. It also does not
decide `.f` readback, setting `.f` true, nonwizard `.f` permissions,
non-integer truth values, protected objects, anonymous objects, invalid
objects, or any other intrinsic flag property. Those surfaces require their
own durable rows and authority gates.

## Focused red and smallest Banteng representation

The focused runtime regression
`MooRuntimeTest.writesIntrinsicFertileFlagAsIntegerZero` executes one logged-in
Wizard task that creates an ordinary object and returns the value of
`object.f = 0`. Java separately observes that the fresh object's existing flag
bits have bit 128 clear. On committed Banteng the state assertion passes, but
the task returns `{2, {E_PROPNF}}` instead of successful `{1, 0}`. This is a
valid red for intrinsic assignment recognition, with no `.f` readback and no
`chparent` call.

The smallest row-only implementation remains in the existing
`WorldTxn.writeObjectProperty` owner. After its current `w` intrinsic branch,
recognize normalized name `f`, require the active row's `IntegerValue`, call
the existing `replaceFlags` mutation with bit 128 and `enabled.isTruthy()`,
and return true. A different value type continues to fall through unchanged.

This representation adds no helper, interface, adapter, request, VM branch,
read path, permission owner, alternate mutation path, or `chparent` change.
It does not introduce a general flag-property implementation beyond the one
durable write asserted by the active row.

## Banteng implementation receipts

After the one-branch implementation, the focused Java 25 regression passed
before and after the pinned formatter. The rebuilt managed distribution then
passed `audit_chparent_nonwizard_requires_fertile_parent`: one selected row,
11,518 deselected, in 4.04 seconds.

The substantial fail-fast `gap_followups_toast_oracle` category passed all 17
selected rows with 11,502 deselected in 30.31 seconds, closing the entire
currently selected family. The exact managed Banteng processes for both runs
were identified by their temporary database paths, stopped, and followed by
empty Banteng process inventories.

Finally, the Java 25 `clean check installDist` gate passed in 16 seconds,
including formatting checks and the full test suite. The kept implementation
changes only the existing `WorldTxn` intrinsic-write owner and the focused
runtime regression.
