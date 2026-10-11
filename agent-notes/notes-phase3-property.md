# Phase 3 basic/property checkpoint

- Tracked ledger was clean at `dc2ed5b` before this family.
- Exact Toast stock result for `basic/property.yaml`: 16 passed.
- Exact Banteng stock baseline: 7 passed, 9 failed.
- Passing Banteng rows establish that `add_property`, `create`, parent lookup, and inherited reads already work for a definition on the parent.
- Failing rows are `is_clear_property` before/after assignment, child override assignment/read, `properties` on parent/child, `clear_property`, and two `delete_property` cleanup calls.
- Toast source (`src/db_properties.cc` and `src/property.cc`) proves that property definitions and per-object value slots are distinct. Every descendant has a slot for an inherited definition; a slot can contain internal `TYPE_CLEAR` (tag 5); reads walk through clear slots; `properties(obj)` lists definitions only.
- Current Banteng `WorldProperty(name, value, owner, permissions)` and `WorldObject.properties()` conflate definitions with value slots and cannot represent an explicitly clear inherited slot.
- Current `WorldTxn.writeObjectProperty` updates only a local list entry and therefore cannot override an inherited property. Current `addProperty`/`deleteProperty` do not propagate slots to descendants.
- Current v17 codec incorrectly requires property-value count to equal local property-name count. Exact Toast format permits inherited value slots, so that restriction must be removed when the property-slot model enters persistence.
- A shortcut where absence means clear is rejected: it would lose explicit child overrides and misencode `properties()`/restart state.
- External Barn/Toast authority audit for this family is active.
- Current blocker: none.
- Next action: finish the exact Barn representation audit, then add red tests for definition-vs-slot state, clear/override transitions, builtin contracts, and v17 clear-slot round trip before production changes.
