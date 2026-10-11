# Ordered multiple-inheritance read-only audit

## Current findings

- The exact `fixtures/mongoose.db` object `#8` is `generic container`, flags `136`, owner `#2`, location `#874`.
- Its v17 parents field is the ordered list `{#5, #20679}` and its children field is `{#2201, #3037, #1054}`.
- `LambdaMooV17Codec.readObject()` passes the parents value to `requireSingleParent()`, which rejects any list longer than one. The writer emits one object reference rather than the original ordered list.
- `WorldObject` and `WorldAnonymousObject` each store one `long parent`. `WorldTxn` lookup, creation, reparenting, recycling, inherited-property propagation, and anonymous ancestry are linear single-parent implementations.
- `WorldHistory.validateTopology()` checks only one reciprocal parent relation per object.
- `MooVm` verb-definition/pass lookup and `MooRuntime` one command lookup path walk one `.parent()` chain.
- Pinned Toast source identity is `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Pinned Toast stores an object reference or ordered list for parents. Its canonical ancestor traversal is depth-first preorder in stored parent order, suppressing later duplicate visits. `parent()` returns the first direct parent (or `#-1`), while `parents()` returns the complete ordered list and `children()` returns the ordered reciprocal child list.

## State and blocker

- Audit is in progress; no production or test file has been edited by this audit.
- The immediate fixture-read blocker is the single-parent persistence/model boundary, but changing only the codec would corrupt lookup, topology, mutation, and round-trip behavior.

## Next action

- Finish the exact source-line change surface for persistence, world topology, lookup/runtime, builtins, validation, and tests, then report the minimal implementation order to the parent agent.
## Read-only adversarial review checkpoint (2026-07-21)

- Scope: uncommitted ordered multiple-inheritance correction; no production or test edits.
- Current state: branch `master` is ahead of origin by four commits and has a large mixed tracked diff plus untracked review artifacts. The review is inspecting the assigned world, codec, builtin, VM, runtime, build, and test surfaces against pinned Toast `aecc51e` semantics.
- Observed so far: permanent-object ancestry is implemented as depth-first, first-visit traversal; property layouts are rebuilt eagerly across the full permanent graph and all anonymous bodies; permanent reciprocal parent/child edges are validated, while anonymous parents have no reciprocal child index; anonymous-object reads/writes are not represented in `WorldHistory.conflictingRecords()`.
- Candidate defects still requiring Toast/source confirmation: incorrect multiple-inheritance linearization or property-slot collision policy; non-serializable/incorrect anonymous child handling; transaction conflict loss for concurrent anonymous reparenting; and builtin error/permission mismatches.
- Blocker: none. The current source reads were partially truncated and must be completed with bounded line-range reads.
- Next action: finish the exact `WorldTxn` create/recycle/reparent ranges, inspect codec canonical encoding and tests, then compare each behavior directly with the pinned Toast source before reporting evidence-ranked findings to the parent agent.

### Confirmed findings in progress

- Critical builtin authority mismatch: Banteng registers `chparent`/`chparents` as `ANY` and directly mutates. Pinned Toast checks target control, every new parent's fertile permission, valid arguments, and returns `E_RECMOVE` for descendants before `db_change_parents()`.
- High `pass()` mismatch: pinned Toast begins lookup independently at each direct parent of the current verb location. Banteng advances through the receiver's flattened ancestry after the current location, so a later sibling branch can be called after the current location's own parent chain is exhausted.
- High stale anonymous layout: permanent `changeParents()` rebuilds permanent layouts but does not rebuild anonymous property layouts; `recycleObject()` rebuilds only anonymous objects that directly name the recycled object. Toast recursively fixes permanent descendants and gathered anonymous children at every affected level.
- High transaction isolation gap: anonymous reads/writes are not included in `WorldHistory` conflict comparison, so concurrent anonymous reparent/property mutations can publish over each other.
- Codec audit observation: Banteng stores parents only as `List<Long>` and writes empty/singleton lists canonically as `#-1`/one object, while pinned Toast writes the original `Var` representation. This can destroy byte identity for valid v17 databases that use empty or singleton list representations; fixture prevalence and required tests remain to be checked.
- Next action: confirm the exact property-slot ordering/fallback behavior and v17 error decoding, inspect missing regression coverage, then return ranked file/line findings.

### Completed review conclusions

- High property identity defect: rebuild preservation is keyed only by property name. Reparenting from unrelated ancestor A defining `foo` to unrelated B defining `foo` preserves A's override/owner/perms, whereas Toast preserves only slots whose defining ancestor is present in both old and new ancestor sets; B's slot must be newly clear with B-derived permissions/CHOWN owner.
- High recycle topology defect: if a recycled object's parent is already also a direct parent of one of its children, the manual parent-child splice can append that child twice to the surviving parent's `children`. Toast reparents children one by one through `db_change_parents`, whose set removal/addition keeps the reciprocal list unique.
- High anonymous execution defect: VM `CALL_VERB` accepts permanent objects and WAIFs but rejects anonymous receivers; `pass()` also requires `this` to be a permanent `ObjectValue`. Pinned Toast accepts anonymous objects in normal verb lookup and resolves `pass()` from `db_object_parents2(vloc)`.
- Medium/high performance defect: every permanent reparent rebuilds every permanent object's property layout; topology validation independently DFS-walks ancestry from every object; codec validation/restoration repeats full ancestry traversals. Pinned Toast updates the affected descendant subtree and caches permanent ancestry. The blanket 2 GiB test heap increase masks rather than proves acceptable production cost.
- Codec conclusions: the low-32-bit error decode matches pinned Toast's union behavior and is not a finding. Empty/singleton/multiple parent writing is a deliberate deterministic canonical encoding; representation-level preservation is not currently retained, but the assigned exact Mongoose multiple-parent case is preserved. No additional concrete semantic codec defect was established.
- Missing regressions: unauthorized/non-fertile/recursive `chparents` errors; full `create` owner/anonymous/init/quota/initialize contract; `pass()` after a root reached through the first branch with a later sibling verb; anonymous call/pass; anonymous descendant layout after permanent reparent/recycle; same-name different-definer reparent; concurrent anonymous conflict; redundant-direct-parent recycle uniqueness; bounded large-DAG performance/memory.
- Review complete. No production or test files were edited by this review; only this required untracked note was updated.

## Read-only closure re-review checkpoint (2026-07-21)

- Scope remains read-only: verify the current uncommitted correction against the nine original findings. Ward is prohibited and was not used. No production or test file has been edited.
- Finding 1 is closed in the inspected code and regression: `changeParents` now validates target/parents, applies target-control and fertile-parent authority, distinguishes `E_PERM`, `E_RECMOVE`, and `E_INVARG`, and the builtin test exercises those outcomes and success.
- Finding 2 remains partial: create now parses owner/anonymous/initializer positions, performs authority checks, self-owns permanent objects requested with owner `#-1`, and dispatches `initialize`; however no object-quota check or `E_QUOTA` path was found, and the regression coverage does not exercise that exact contract.
- Finding 3 is closed in the inspected code and regression: exact anonymous identities are tracked in transaction read/write sets, validated against the base/current bodies, and reported through `conflictingAnonymousRecords`; the test proves a stale exact-identity read conflicts.
- Newly observed concrete regression candidate: create initialization is represented by the ordinary local name `__banteng_create_result`, while every verb return in `VmState` substitutes that local when present. A normal user verb that declares that name can therefore return the wrong value even outside create initialization.
- Current blocker: none. Remaining findings 4-9 still require closure inspection before the matrix is final.
- Next action: inspect the current pass/anonymous-call tests, property-definer preservation, recursive anonymous layout rebuild, recycle uniqueness, and bounded graph/1 GiB evidence; then report the numbered closure matrix and any remaining findings.
