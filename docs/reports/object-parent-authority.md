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
