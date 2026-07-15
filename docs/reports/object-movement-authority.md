# Object movement authority

This report is the mandatory slice evidence record for the active exact-
convergence movement row. It records only the contract proven before Banteng
API design or production edits.

## Active durable row

The active row is
`../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:884-909`,
`audit_move_wizard_calls_accept_but_overrides_false`. It was introduced by
conformance commit `81d1157 Add move and chparent trust Toast oracles`.

The row creates `thing` and `dest`, installs executable `dest:accept` code
that records `args[1]` and returns false, and invokes `move(thing, dest)` as
Wizard. It independently requires both the recorded argument and final
location to match: `{accept_seen == thing, thing.location == dest}` must be
`{1, 1}`.

Committed Banteng `95764ba` reaches this row after 15 preceding family rows
pass and returns `[0, 1]`. Relocation already succeeds, but the destination
verb side effect is absent. The row therefore distinguishes a wizard path
that skips `accept` from one that calls `accept` and overrides only its false
result.

## Barn normative surface

Barn's normative prose is incomplete for the asserted behavior.

- `../barn/spec/builtins/objects.md:316-340` describes physical relocation,
  `exitfunc`, `enterfunc`, and `E_PERM`, `E_RECMOVE`, and `E_INVIND`.
- `../barn/spec/objects.md:347-370` repeats physical relocation and hooks with
  only `E_RECMOVE` and `E_PERM`.
- `../barn/spec/errors.md:378-389` describes `E_NACC` generically but does not
  connect it to movement refusal.

None of those sections mentions destination `accept`, its argument, missing-
verb handling, `E_NACC` from a false result, or the wizard override. The
absence is recorded rather than filled from intuition. The generated
signature at
`../barn/builtins/function_signatures_generated.go:125` accepts two or three
arguments, while the first prose section names only two; that discrepancy is
outside this row.

## Current Barn implementation

Barn registers `move` in `../barn/builtins/registry.go:154` and dispatches it
through the explicit registry path at `../barn/builtins/registry.go:399-435`.
The semantic owner is
`../barn/builtins/objects_movement.go:11-69`.

For every destination except `$nothing`, lines 52-60 call
`destination:accept(what)` before `Store.MoveObject`. An exception other than
`E_VERBNF` propagates. A missing verb is ignored by current Barn. A normal
false result raises `E_NACC` only when the original task context is not
wizard; a wizard keeps the verb's observable side effects and proceeds to the
move at lines 63-64.

`../barn/builtins/registry.go:494-500`,
`../barn/scheduler/scheduler.go:69-78`, and
`../barn/scheduler/call_verb.go:21-162` carry the synchronous verb call through
lookup, compilation, a fresh VM/task activation, and result return.
`../barn/db/store/store_relationships.go:25-52` then removes old contents
membership, updates location, and inserts new contents under one lock.

Current Barn agrees with the active row. Its movement tests at
`../barn/builtins/objects_movement_test.go:36-105` cover invalid references,
not `accept` or wizard behavior. Barn's current omission of `exitfunc` and
`enterfunc` is explicitly marked TODO and is outside this row. Historical
notes about missing `accept` for nonwizards are likewise outside the asserted
surface.

## Pinned Toast implementation

The authoritative source and executable checkout was freshly identified by:

```powershell
wsl -d Debian -u root -e git -C /root/src/toaststunt rev-parse HEAD
```

It returned `aecc51e9449c6e7c95272f0f044b5ba38948459e`.

At that commit, `/root/src/toaststunt/src/objects.cc:1368-1370` registers
`move` with two or three arguments of types `OBJ`, `OBJ`, and optional `INT`.
`bf_move` at `src/objects.cc:212-231` snapshots those arguments and drives
`do_move` at `src/objects.cc:117-209`.

The exact control flow is:

1. `src/objects.cc:127-134` validates references, position, control of the
   moved object, no-op destinations, and positioned-destination control.
2. `src/objects.cc:47-57,136-149` calls `where:accept(what)` with destination
   as `this` and the moved object as the sole argument. `E_VERBNF` becomes a
   false decision; `E_MAXREC` propagates.
3. `src/objects.cc:153-158` interprets the returned value. Only a nonwizard
   receives `E_NACC` when it is false; a wizard deliberately overrides false.
4. `src/objects.cc:160-168` revalidates state and rejects recursive
   containment with `E_RECMOVE`.
5. `src/objects.cc:170-204` calls `db_change_location` before later movement
   hooks.
6. `src/db_objects.cc:1190-1225` removes old contents membership, inserts the
   object into destination contents, updates `.location`, and records
   last-move metadata.

`src/objects.cc:35-39` defines control as wizard or owner, and
`src/db_objects.cc:1276-1280` defines a wizard as a valid object carrying
`FLAG_WIZARD`. Normal callable dispatch in `src/execute.cc:641-674,717-782`
creates the destination `accept` activation with its verb owner as programmer
and its actual defining object as verb location.

## Managed oracle resolution

The exact durable row was rerun with the pinned stock-Toast profile:

```powershell
$wslIp=(wsl -d Debian -u root -e hostname -I).Trim()
uv run --project C:\Users\Q\code\moo-conformance-tests moo-conformance `
  --moo-host $wslIp `
  --server-command "wsl -d Debian -u root -e env TOAST_MOO=/root/src/toaststunt/build-release/moo bash /mnt/c/Users/Q/code/banteng/scripts/run_toast_wsl.sh {db} {port}" `
  --server-db C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_db/Test.db `
  --oracle-profile-manifest C:/Users/Q/code/banteng/profiles/toast/stock-wsl-testdb.json `
  --target-profile-manifest C:/Users/Q/code/banteng/profiles/toast/stock-wsl-testdb.json `
  -k "audit_move_wizard_calls_accept_but_overrides_false"
```

Result: one passed, 11,518 deselected, in 3.62 seconds. No managed Toast
process leaked; unrelated PID 19 remained untouched.

## Frozen contract and exclusions

For this slice, a wizard moving an object to a different valid destination
must call the destination's `accept` verb with the moved object as its sole
argument before relocation. The call's side effects remain observable. If the
verb returns false normally, wizard status overrides only the refusal and the
move still occurs.

This row does not decide missing-`accept` behavior, nonwizard refusal,
positioned movement, object-control checks, recursive containment, movement
revalidation, `exitfunc`, `enterfunc`, last-move metadata, or any other error
path. Those surfaces require their own durable rows and authority gates. No
Banteng Java representation or production edit is authorized by this report
beyond the active row until the existing Banteng owner is traced.

## Banteng owner trace and smallest representation

Committed Banteng dispatches `move` from
`src/main/java/moo/builtin/BuiltinCatalog.java:353` to the direct catalog
method at lines 1436-1444. That method validates exactly two object arguments
and immediately calls `WorldTxn.move`, returning zero or `E_INVARG`.
`src/main/java/moo/world/WorldTxn.java:579-598` already performs the physical
relocation by updating old contents, destination contents, and object
location. The missing behavior is therefore the pre-move verb activation, not
topology mutation.

The existing concrete builtin-to-verb continuation is recycle.
`BuiltinCatalog.Result` carries a `recycleTarget`; `MooVm.invokeBuiltin` finds
and compiles the hook, creates the verb locals, and pushes an ordinary verb
frame; `MooVm.routeReturn` performs the mutation only after that frame returns.
An error unwinds the hook frame without performing the normal-return action.

The smallest row-only representation extends those exact owners with two
explicit optional IDs: moved object and destination. `BuiltinCatalog.move`
keeps its existing argument and object validation. For a wizard programmer it
returns those IDs without mutating the world; the existing VM builtin result
path then looks up destination `accept`. Missing `accept` preserves Banteng's
current immediate successful move. A present verb is compiled and pushed as
an ordinary frame with destination `this`, the moved object as the sole
argument, inherited player, original receiver as caller, verb owner as
programmer, and the destination as the row's direct defining object. On a
normal return, the continuation ignores the value and invokes the existing
`WorldTxn.move`; an error unwinds without relocation.

Only the wizard path receives this continuation in the active slice. The
current nonwizard path remains unchanged because false-result refusal and
missing-verb behavior are explicitly outside the durable row. The existing
`move` transaction-write effect classification remains correct. This design
adds no request record, interface, helper owner, adapter, alternate builtin
dispatcher, world method, or mutation path. The focused runtime regression
must execute the durable row body and first observe `{0, 1}` on committed
Banteng before any production edit.

## Banteng implementation receipts

The focused Java regression
`MooRuntimeTest.invokesDestinationAcceptWhenMoveReturnsFalse` was added before
the production edit and proved the committed behavior red: the setup and
relocation succeeded, but the observed result was `{1, {0, 1}}` instead of
`{1, {1, 1}}`. After the implementation, the same regression passed under
Java 25, including after the pinned formatter was applied.

The exact managed Banteng row was then run against the rebuilt application
distribution with the same durable selector used for Toast. It passed one
selected row with 11,518 deselected in 3.56 seconds. The substantial
fail-fast `gap_followups_toast_oracle` category passed its first 16 rows,
including this movement row, and stopped at the next separately owned row,
`audit_chparent_nonwizard_requires_fertile_parent`, where Banteng returned
`E_TYPE` instead of the expected `E_PERM`. Both managed Banteng processes were
identified by their exact temporary database paths, stopped, and followed by
empty Banteng process inventories.

Finally, the Java 25 `clean check installDist` gate passed in 17 seconds. The
kept source slice changes only the existing builtin result, VM frame, VM
continuation path, and focused runtime regression described above.
