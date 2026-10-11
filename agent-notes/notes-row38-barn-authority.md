# Row 38 Barn authority trace

## Assigned scope

- Conformance row: `audit_chparent_nonwizard_requires_fertile_parent`.
- Read only the normative Barn specification and current Barn implementation from the public `chparent` builtin entry through its semantic owner.
- Determine why a nonwizard changing an object's parent to an unowned non-fertile parent returns `E_PERM`, including exact argument and permission ordering.
- Do not edit tracked files, run Banteng or Toast, or broaden to other `chparent` behavior.

## Current state

- The normative builtin spec is `C:\Users\Q\code\barn\spec\builtins\objects.md:208-217`:
  `chparent(object, new_parent)` changes the single parent and records `E_PERM`
  for a caller who is not the owner/wizard, plus invalid-parent/cycle errors.
- The public registry maps `"chparent"` directly to `builtinChparent` at
  `C:\Users\Q\code\barn\builtins\registry.go:141-154` (registration at line
  152).
- `builtinChparent` is in
  `C:\Users\Q\code\barn\builtins\objects_hierarchy.go:125-235`.
- Its ordering is currently:
  1. exact arity (`E_ARGS`, lines 130-134);
  2. first and second argument object-reference types (`E_TYPE`, lines 136-144);
  3. invalid/absent target object checks (`E_INVARG`/`E_INVIND`, lines 146-153);
  4. self-cycle before new-parent existence (`E_RECMOVE`, lines 155-159);
  5. invalid or absent new parent (`E_INVARG`, lines 161-171);
  6. descendant cycle (`E_RECMOVE`, lines 173-176);
  7. direct and chparent-descendant property conflicts (`E_INVARG` or propagated
     store errors, lines 178-205);
  8. only then the nonwizard new-parent permission check (lines 207-220);
  9. relationship mutation through `store.ChangeParents` (lines 225-233).
- The permission predicate is exact at lines 207-220: for a nonwizard and a
  parent other than `$nothing`, read the new parent's owner and fertile flag;
  if `ownerID != ctx.Programmer` and the fertile flag is absent, return
  `E_PERM`. Thus an unowned, non-fertile new parent fails because neither of
  the two nonwizard authorization alternatives holds. Wizards bypass this
  branch; `$nothing` also bypasses it.
- Store metadata access definitions were located but not yet fully read:
  `db/store/store_core.go:209-217` (`ObjectOwner`) returns the live object's
  owner or `E_INVIND`; `:231-239` (`HasObjectFlag`) returns whether the live
  object's flags contain the requested bit or `E_INVIND`.
- `db/store/object.go:230-239` defines `FlagFertile` as bit 128 and describes it
  as "Can be used as parent".
- `db/store/store_relationships.go:271-295` (`ChangeParents`) owns the actual
  hierarchy mutation: it validates the target object, detaches old parent
  links, replaces `obj.parents`, attaches new parent links as chparent-added,
  and reseeds inherited properties. It performs no caller/owner/fertile
  authorization. Therefore the semantic owner of this row's permission rule is
  `builtinChparent`, while `Store` owns metadata access and the later mutation.
- `kernel/context.go:11-22,40-41` identifies `TaskContext.Programmer` as
  effective permissions and `TaskContext.IsWizard` as whether the current
  programmer has wizard permissions. These are exactly the two caller-context
  inputs used by `builtinChparent`'s permission branch.
- No tracked files have been edited.

## Current blocker

- None. The Barn authority trace is complete.

## Concise conclusion

The normative Barn rule says `chparent(object, new_parent)` returns `E_PERM`
when the caller is neither owner nor wizard
(`spec/builtins/objects.md:208-217`). The registered public entry is
`builtinChparent` (`builtins/registry.go:141-154`), and that builtin owns the
permission predicate (`builtins/objects_hierarchy.go:207-220`): after all
arity, type, reference, cycle, and property-conflict checks, a nonwizard may
use a real new parent only if `newParent.owner == ctx.Programmer` OR the new
parent has `FlagFertile`. Consequently an unowned non-fertile parent returns
`E_PERM`. `$nothing` and wizards bypass this particular check. Only after it
passes does the builtin call `Store.ChangeParents`
(`builtins/objects_hierarchy.go:225-233`), whose implementation mutates the
relationship but performs no permission check
(`db/store/store_relationships.go:271-295`).
