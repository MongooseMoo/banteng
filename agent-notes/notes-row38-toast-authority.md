# Row 38 Toast authority handoff

## Current state

- Assigned surface: `audit_chparent_nonwizard_requires_fertile_parent` only.
- Required source: pinned WSL tree `/root/src/toaststunt` at identity `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Required trace: builtin registration/public entry through the semantic owner, including exact validation and permission ordering for a nonwizard child changing to an unowned non-fertile parent and the resulting `E_PERM`.
- Prohibited actions: do not run the oracle, do not edit tracked files, and do not inspect Windows Toast source.
- Observed so far: no source inspection or command has yet been performed in this restarted subagent turn.
- Tried so far: only recording this checkpoint state before beginning the authority trace.
- What worked: task scope and constraints are explicit and internally consistent.
- What did not work: nothing attempted yet.
- Current blocker: none.
- Next step: report this completed authority trace to the parent agent; no source or oracle work remains in this subtask.

## Findings

- Source identity was verified directly in WSL: `/root/src/toaststunt` is at `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Registration is in `/root/src/toaststunt/src/objects.cc:1352-1355`. Both names share `bf_chparent_chparents`; `chparents` is registered for two arguments with a list second argument, while `chparent` is registered for two arguments with an object second argument.
- The public entry/policy owner is `bf_chparent_chparents` in `/root/src/toaststunt/src/objects.cc:553-601`:
  1. Lines 560-563 reject wrong runtime types with `E_TYPE`.
  2. Lines 565-568 reject a nonwizard's extra third argument with `E_PERM` (the registered two-argument `chparent` row does not use this extension).
  3. Lines 570-577 reject an invalid child or invalid parent with `E_INVARG`.
  4. Lines 578-585 perform one composite permission gate: the programmer must control the child, and every real new parent must allow the programmer under `FLAG_FERTILE`; failure of either condition returns `E_PERM`.
  5. Lines 586-590 check recursive ancestry only after permissions and return `E_RECMOVE`.
  6. Lines 591-599 call `db_change_parents`; a database-integrity/property failure maps to `E_INVARG`, otherwise the builtin succeeds with no value.
- Child control is defined by `controls2` in `/root/src/toaststunt/src/objects.cc:41-45`: wizard or owner (`db_object_owner2`). Thus the row's nonwizard child owner passes the child-control half.
- Parent permission is defined by `db_object_allows` in `/root/src/toaststunt/src/db_objects.cc:1268-1274`: it is true iff the programmer is a wizard, owns the parent, or the parent has the requested flag. `FLAG_FERTILE` is the permanent object flag enumerated in `/root/src/toaststunt/src/include/db.h:252-262`; the public contract comment for `db_object_allows` is at `include/db.h:289-295`.
- Therefore an unowned, non-fertile destination parent denies this nonwizard: all three `db_object_allows(parent, programmer, FLAG_FERTILE)` alternatives are false. Its negation at `objects.cc:579-580` makes the composite permission branch true, so `bf_chparent_chparents` returns `E_PERM` at lines 583-584.
- This rejection occurs after type/validity validation but before recursive ancestry checking and before `db_change_parents`, so no parent links or properties are mutated.
- The mutation/integrity owner is `db_change_parents` in `/root/src/toaststunt/src/db_objects.cc:1030-1146`. It checks duplicates, anonymous-child consistency, and property compatibility at lines 1033-1060, then changes parent/child links and repairs inherited properties. It does not own the fertile permission decision because the public builtin rejects the row before calling it.

## Execution record

- Observed: the WSL identity matched exactly; all cited policy and mutation paths were read from that WSL tree only.
- Tried: symbol search followed by line-numbered reads of the registration, public builtin, permission predicates, flag declaration, and database mutation routine.
- Worked: the source gives an unambiguous ordered explanation for `E_PERM`.
- Did not run: no Toast oracle/server execution and no conformance test.
- Did not inspect: no Windows Toast source.
- Did not edit: no tracked file was changed.
- Blocker: none.
