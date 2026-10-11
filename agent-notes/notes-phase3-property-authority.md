# Phase 3 property authority audit

## Scope

Read-only audit of only the nine failing `basic/property.yaml` rows: clear-state before and after assignment, inherited override read, parent/child `properties()`, `clear_property`, inherited read after clearing, and the two `delete_property` cleanup rows.

## Current findings

- Exact family source is `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/basic/property.yaml`; the nine failing rows are `is_clear_property_on_child` (41-45), `is_clear_property_after_set` (53-57), `read_overridden_property` (59-63), `properties_on_parent` (65-69), `properties_on_child` (71-75), `clear_property_on_child` (77-81), `read_property_after_clear` (83-87), `delete_property_temp` (89-93), and `delete_property_temp0` (95-99). `set_property_on_child` is setup, not part of the nine-row audit.
- Barn owners are `builtins/properties.go` for `properties`, `delete_property`, `clear_property`, and `is_clear_property`, registered at `builtins/registry.go:164-170`; store-level clear/override inheritance is under `db/store`.
- ToastStunt primary implementations and registrations are in `C:/Users/Q/src/toaststunt/src/property.cc` (`bf_properties`, `bf_delete_prop`, `bf_clear_prop`, `bf_is_clear_prop`, registrations near 335-347).
- Banteng currently has `WorldProperty`, inherited lookup/read/write in `WorldTxn`, `add_property` in `BuiltinCatalog`, and `WorldTxn.deleteProperty`, but no manifest entries were found yet for the four missing property builtins.
- ToastStunt `properties` validates object type, validity, and object read permission, then lists only locally defined property names (`src/property.cc:41-61`).
- ToastStunt `delete_property` requires an ordinary object, a valid target, object write permission, and a locally defined property; it returns no value and the database removes the definition plus descendant inherited slots (`src/property.cc:238-260`).
- ToastStunt `clear_property` finds the inherited property, rejects built-ins or non-writable properties, rejects a definition on the target with `E_INVARG`, and stores a `TYPE_CLEAR` local slot (`src/property.cc:262-296`).
- ToastStunt `is_clear_property` returns integer 1 only when the resolved non-built-in property slot on that object is `TYPE_CLEAR`; it returns 0 for built-ins (`src/property.cc:298-330`).
- Barn models `clear` and `defined` explicitly in its store-owned property slot (`db/store/object.go:87-125`), creates a local non-defined override on inherited assignment (`db/store/store_properties.go:434-469`), lists only `defined` slots (`:130-150`), clears an override (`:547-565`), and deletes definitions plus descendant inherited slots (`:513-545`).
- Banteng `WorldProperty` has only name/value/owner/permissions (`WorldProperty.java:6-12`); it cannot distinguish a local definition, clear inherited slot, or non-clear inherited override. `WorldTxn.property` walks ancestors but `writeObjectProperty` updates local slots only, and `deleteProperty` removes only the exact object's slot.
- For these nine single-parent runtime rows, Banteng does not need a new persisted field: dynamic ancestry lets local absence plus an ancestor definition represent clear inheritance; a local slot whose name exists upstream is an override; a local slot with no upstream definition is a definition. This avoids changing `WorldProperty` and the codecs.
- Toast object permission is wizard OR object owner OR requested object flag (`src/db_objects.cc:1293-1299`); property permission is requested bit OR property owner OR wizard (`src/db_properties.cc:746-752`).
- Toast assignment checks the resolved property's write permission and writes through the target object's resolved slot (`src/execute.cc:1959-2074`). Banteng's VM already checks resolved metadata at `MooVm.java:560-580`, but `WorldTxn.writeObjectProperty` must create a local override when the resolved property is inherited.
- Current Barn diverges from Toast in two ways: `builtinProperties` and `builtinDeleteProperty` omit Toast's permission checks, and Barn's markdown claims inherited delete is a no-op/removes an override while Barn code and Toast `db_delete_propdef` require a local definition and otherwise return `E_PROPNF`. The wizard cleanup rows exercise only locally defined properties.
- Toast `db_add_propdef` installs a definition and descendant value slots (`src/db_properties.cc:187-228`); `db_delete_propdef` removes the definition and slots recursively (`:350-399`). Banteng's dynamic lookup makes descendant cleanup automatic once the root local definition is removed.
- Tracked Toast source audited at commit `e8a353665a106244f5e01edb67239c90411ae584`; relevant source files have no tracked diff, although the checkout contains unrelated untracked artifacts.

## State and blocker

No production or test code has been edited by this audit. No blocker is known. Only the final exact manifest shapes, row mapping, and error table remain to document.

## Next action

Hand the implementing agent this narrow owner-preserving path:

1. In `BuiltinCatalog`, add `properties(ANY)` and `is_clear_property(ANY, STRING)` as `TRANSACTION_READ`, plus `clear_property(ANY, STRING)` and `delete_property(ANY, STRING)` as `TRANSACTION_WRITE`; all are `BuiltinOwner.WORLD` and `BuiltinPermissionRule.ANY` because their handlers perform target-specific Toast permission checks.
2. Keep argument type `ANY` for the first position so the handlers can preserve Toast's distinct errors: `properties`, `clear_property`, and `delete_property` use `E_TYPE` for a non-object, while `is_clear_property` uses `E_INVARG`.
3. In the existing `WorldTxn.writeObjectProperty` default path, when no local slot exists but `property(parent, name)` resolves, append a local `WorldProperty` override copying the resolved name/owner/permissions and using the assigned value. The VM already enforces resolved-property write permission and returns the RHS.
4. Implement `properties` directly from the target's ordered local `WorldProperty` list, including only names absent from its parent ancestry. This yields `{"test"}` on `$temp` and `{}` on `$temp0` even after `$temp0` has a local override.
5. Implement `is_clear_property` structurally: built-ins return 0; missing property returns `E_PROPNF`; resolved property permission is checked; local override/definition returns 0; local absence with an ancestor definition returns 1.
6. Implement `clear_property` structurally: reject a local definition with `E_INVARG`; for a local inherited override call existing `WorldTxn.deleteProperty`; for an already-absent inherited slot succeed without mutation. Reads then fall through to the ancestor value.
7. Implement `delete_property` only for a locally defined property, after Toast object-write permission; call existing `WorldTxn.deleteProperty`. In Banteng's dynamic-inheritance model, removing the root definition automatically removes descendant visibility, so no descendant rewrite is needed for the two cleanup rows.
8. Do not add an interface, adapter, helper layer, or new `WorldProperty` field for this family.

Source-accurate errors:

- `properties`: non-object `E_TYPE`; invalid `E_INVARG`; not wizard/owner/readable object `E_PERM`.
- inherited assignment/read: wrong receiver/name `E_TYPE`; invalid receiver `E_INVIND`; missing property `E_PROPNF`; denied property access `E_PERM`.
- `is_clear_property`: non-object or invalid `E_INVARG`; missing `E_PROPNF`; denied property read `E_PERM`; built-in property returns 0.
- `clear_property`: non-object `E_TYPE`; invalid `E_INVARG`; missing `E_PROPNF`; built-in or denied property write `E_PERM`; definition on target `E_INVARG`; already clear succeeds.
- `delete_property`: non-numbered-object `E_TYPE`; invalid `E_INVARG`; denied object write `E_PERM`; no local definition `E_PROPNF`.

Row outcomes: child clear state is 1 before assignment and 0 after; override read is 2; `properties(parent)` is `{"test"}` and `properties(child)` is `{}`; clear succeeds and restores inherited value 1; both root cleanup deletes succeed and the enclosing `eval` returns `{1, 0}`.
