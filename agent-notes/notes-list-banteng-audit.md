# Banteng list builtin audit

## Current state

- Scope is read-only inspection of exactly `listappend`, `listinsert`, `listdelete`, `listset`, and `setadd`.
- Required authorities are the current `BuiltinCatalog` manifest, `ListValue` APIs, relevant tests, and focused Git history.
- No implementation files have been edited.

## Findings and observations

- `src/main/java/moo/builtin/BuiltinCatalog.java` and `src/test/java/moo/builtin/BuiltinCatalogTest.java` exist.
- No checked-in `basic/list.yaml` or other `list.yaml` exists in the repository; the production list type is nested at `src/main/java/moo/value/MooValue.java`.
- Memory registry search returned no relevant entries for these five builtins, `basic/list.yaml`, or `ListValue`.
- The current `BuiltinCatalog` production manifest contains none of the five names.
- The dirty worktree has an existing modification to `BuiltinCatalogTest.java` that adds all five names, exact pure-VM call-shape assertions, success cases, and range-error cases. This audit did not create or modify that test diff.
- Current `ListValue` operations are immutable snapshot construction, `elements`, `size`, one-based `get`, `concatenate`, and append-at-end.
- Current `MooVm` directly implements list indexed replacement and range insertion/replacement by copying `elements` into a new `ListValue`.
- Focused history shows pre-`ae03f40` `BuiltinCatalog` directly owned a two-argument `listappend` implementation; commit `ae03f40` removed it during the manifest rewrite. No helper/interface/adapter owned that operation.
- Exact `git log --all -S` searches under `src/main/java` and `src/test/java` found no history hits for `listinsert`, `listdelete`, `listset`, or `setadd`.
- The only other `listappend` history hit, `7f12c59`, deleted tests containing MOO source strings; it did not supply a production owner.
- Minimal existing production owner is `BuiltinCatalog` itself: direct manifest entries and direct implementation bodies. Existing `ListValue` and VM copy operations supply the needed immutable-list primitives without a new layer.

## Blocker

- None. A workspace checkpoint required this uncommitted note before continuing.

## Next action

- Parent agent has been sent the completed bounded inventory. No implementation action is authorized in this subtask.
