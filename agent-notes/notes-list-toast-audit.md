# LIST Toast Authority Audit

## Current findings

- Pinned Toast source `/root/src/toaststunt` is exactly at `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- The Toast worktree has pre-existing untracked files, including `build-release/`; this audit will not modify them.
- `C:/Users/Q/code/moo-conformance-tests` is at `cc1871ecb22f26a87fa394a6f647146c91334cfe`, ahead of `origin/main` by 22 commits, with many pre-existing untracked artifacts and no tracked modifications reported by `git status --short --branch`.
- Banteng `master` is ahead of `origin/master` by 52 commits and has many pre-existing untracked notes/artifacts; this file is the only permitted audit-side write.

## Observations

- Audit scope is Toast LIST authority plus existing managed conformance rows only.
- Barn is explicitly out of scope and has not been inspected.
- No LIST semantic claims have been concluded yet; source and row tracing remains pending.

## State

- Read-only authority audit in progress.

## Blocker

- None.

## Next action

- Inventory the exact managed LIST rows, then trace pinned Toast symbols and line-level behavior across representation, construction/splice, conversion, comparison/hash/map-key validity, truth/indexing/ranges, mutation/copy/COW/aliasing, literal formatting, persistence, quotas/encoding, and errors; deduplicate before proposing the smallest missing oracle rows.

## Bounded builtin audit checkpoint: listappend/listinsert/listdelete/listset/setadd

### Current findings

- Reverified WSL `/root/src/toaststunt` HEAD as `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- The active YAML rows are in `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/basic/list.yaml`; the five named builtins account for eight rows after the separate `length` row.
- Primary definitions and wrappers are in `/root/src/toaststunt/src/list.cc`: core operations around lines 149-270, builtin wrappers around lines 659-783, and registration/call arities around lines 1766-1774.
- `listappend` and `listinsert` share `insert_or_append`; omitted indices default respectively to after the last element and before the first element. Explicit `listappend` inserts after its index; explicit `listinsert` inserts at its index. Both clamp insertion positions and do not return `E_RANGE` for an integer index.
- `listdelete` and `listset` require an in-range 1-based index and return `E_RANGE` otherwise.
- `setadd` calls `ismember(value, list, 0)`: it returns the original list when a matching member exists and appends otherwise. The exact meaning of `case_matters == 0` is the next source lookup.
- `ismember` iterates the list in `/root/src/toaststunt/src/collection.cc:45-57` and applies `equality(lhs, member, 0)`. In `/root/src/toaststunt/src/utils.cc:444-492`, this means case-insensitive string equality, recursive list/map equality with the same flag, exact same-type scalar equality, identity equality for anon/waif values, and boolean/integer equality when their numeric truth values match; otherwise differing types are unequal.
- Generic builtin dispatch in `/root/src/toaststunt/src/functions.cc:239-275` raises `E_ARGS` outside registered arity and `E_TYPE` when an argument fails the registered prototype.
- Stock Toast's own `test/tests/basic/list/test.in` lines 2-9 exactly match the eight managed YAML expressions, and `test.out` lines 2-9 give the same eight expected list values.

### Observations

- All eight current YAML expected values agree with the wrapper/core control flow inspected so far.
- Function registration constrains list arguments and integer indices before the wrappers; type mismatches therefore belong to builtin argument validation rather than the core list helpers.
- Successful results are subject to `value_bytes(result) <= max_list_value_bytes`; oversized results go through `make_space_pack()`.
- `/root/src/toaststunt/src/list.cc:620-627` makes oversized results `E_QUOTA` when `max_concat_catchable` is enabled and otherwise returns an `ABORT_SECONDS` abort package.

### State

- Read-only bounded audit source tracing is complete; no implementation files have been edited.

### Blocker

- None.

### Next action

- Report the exact five call shapes, index/equality/return/error semantics, and all eight YAML outcomes to the parent agent.
