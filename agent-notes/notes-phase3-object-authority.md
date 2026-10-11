# Phase 3 object authority audit

## Scope

Read-only audit of only these eight `basic/object.yaml` rows: `parent_of_newly_created_object`, `contents_of_newly_created_object`, `r_flag_of_newly_created_object`, `w_flag_of_newly_created_object`, `f_flag_of_newly_created_object`, `is_player_of_newly_created_object`, `valid_nothing`, and `valid_system_object`.

## Current findings

- The rows are at `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/basic/object.yaml:5-9,23-27,41-73`.
- Fresh `C:/Users/Q/src/toastcore` is clean at `1887eacd591d97fdc55d258a76e2167899b1951d`; its `toastcore.db` is LambdaMOO v17 and contains object markers `#0` and `#2`.
- Barn registers `create`, `valid`, `parent`, and `is_player` in `builtins/registry.go:142-155`.
- Barn `create` validates arguments/parents/owner/permissions, then calls the store object allocator; `db/store/object.go:332-345` establishes empty contents, `$nothing` location, and zero flags.
- Barn serves `.contents`, `.r`, `.w`, and `.f` as server-maintained properties in `vm/op_property.go:327-442`. Flag bits are `read=16`, `write=32`, `fertile=128` in `db/store/object.go:233-245`.
- Barn `parent`, `valid`, and `is_player` owner implementations are `builtins/objects_hierarchy.go:12-41`, `builtins/objects.go:443-473`, and `builtins/objects_players.go:33-74`.
- Banteng registers `create` at `src/main/java/moo/builtin/BuiltinCatalog.java:253-261`, but no `parent`, `valid`, or `is_player` manifest entries were found.
- Banteng `WorldTxn.createObject` already creates `name=""`, flags `0`, location `-1`, requested parent, and empty contents/children/verbs/properties at `src/main/java/moo/world/WorldTxn.java:419-449`.
- Banteng `WorldTxn.readObjectProperty` currently exposes name/location/owner/programmer/wizard but not contents/r/w/f at `src/main/java/moo/world/WorldTxn.java:321-337`. Its writer already handles w/f but not r at `:340-417`.
- Barn routes built-in property reads directly before inherited-property lookup in `vm/op_property.go:35-84`; `getBuiltinProperty` returns contents and flag bits as integer booleans at `:327-442`.
- Banteng's VM performs ordinary-property permission checks first and then delegates all reads to `WorldTxn.readObjectProperty`; a missing built-in currently becomes `E_PROPNF` at `src/main/java/moo/vm/MooVm.java:520-557`.
- Fresh ToastCore is a clean v17 source database (`toastcore.db:1`) at commit `1887eacd591d97fdc55d258a76e2167899b1951d` dated 2026-06-05. Its `#0` marker is at `toastcore.db:1118` and `#2` at `:2319`; Barn's v17 reader documents the record field order at `db/format/reader_object.go:23-160`.
- Banteng's builtin manifest names confirm the absence of `parent`, `valid`, and `is_player`; the existing owner for builtin registration/dispatch is `BuiltinCatalog`, while object state reads belong to `WorldTxn`.

## State and blocker

No production or test files have been edited. No external blocker is known. The final task is to decode only the relevant fresh ToastCore record fields and state exact narrow recommendations.

## Next action

Hand the following narrow recommendations to the implementing agent:

1. Extend only `WorldTxn.readObjectProperty` for `contents`, `r`, `w`, and `f`. Return object values in persisted contents order and integer booleans from flag bits 16, 32, and 128. The newly created object already has empty contents and flags zero.
2. Add only three transaction-read, WORLD-owned, ANY-permission, one-OBJECT-argument manifest entries in `BuiltinCatalog`: `parent`, `valid`, and `is_player`.
3. Implement those entries directly through existing `WorldTxn.object` / `WorldObject` state: `parent(valid object)` returns its stored parent; `valid` returns integer 1 iff the object lookup is present (therefore `#-1` is 0 and live `#0` is 1); `is_player(valid object)` returns the player flag bit as an integer boolean. Do not add an interface, adapter, or new ownership layer.
4. Preserve complete Barn/Toast error distinctions outside the eight-row happy paths when broadening later: `parent` rejects wrong type and invalid objects; `valid` accepts object-flavored values and returns false for negative/missing/recycled ids; `is_player` returns E_TYPE for waifs/anonymous values and E_INVARG for `$nothing` or invalid/recycled objects.

Fresh ToastCore decode: `#0` is a live record at `toastcore.db:1118-1131` (name, flags 24, owner #2, location #-1, empty contents, parent #1, empty children). `#2` is live at `:2319-2341` (flags 7, owner #2, location #15, empty contents, parent #57, empty children). Thus wizard #2 may create from #2 despite its non-fertile flag, and the created object's stored parent is exactly #2.
