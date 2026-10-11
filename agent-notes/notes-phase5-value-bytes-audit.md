# Phase 5 `value_bytes` audit

## Scope and constraints

- Read-only future-slice audit while another agent owns the worktree.
- The only authorized write is this untracked notes file.
- Do not run Gradle, commit, push, or touch sibling repositories.
- Authority target: stock Toast source at commit
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`.

## Current verified state

- `master` tracks `origin/master`; the tracked worktree was clean at audit start.
  Many pre-existing untracked notes and investigation artifacts are present.
- `profiles/toast/stock-wsl-testdb.json` pins source
  `/root/src/toaststunt` and implementation ref
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- `git -C /root/src/toaststunt rev-parse HEAD` returned that exact commit.
- Phase 5 in `docs/reports/banteng-implementation-plan.md` requires completing
  the one production `BuiltinSpec` table and deleting independent dispatch-name
  and effect-class switches, Toast-absent aliases, and placeholder results.
- The plan's builtin contract gives each entry: name, call shapes, permission,
  dynamic tick-cost rule, effect class, owner, and production handler. Dispatch
  must use those fields directly, with no independent contract or dispatch
  switch.

## Toast facts verified so far

- `src/list.cc:1758` registers `value_bytes` with exactly one required argument,
  maximum one argument, handler `bf_value_bytes`, and allowed type `TYPE_ANY`.
- `src/list.cc:1461-1469` implements the builtin as a direct call to
  `value_bytes(arglist.v.list[1])`, returns the result as `TYPE_INT`, frees the
  argument list, and has no builtin-local permission check, quota check, catch,
  suspension, or explicit error branch.
- The underlying `value_bytes(Var)` is in `src/utils.cc:609-630`. It starts with
  `sizeof(Var)` for every value. It then adds:
  - string: `memo_strlen(v.v.str) + 1`;
  - float: `sizeof(double)`;
  - list: `list_sizeof(v.v.list)`;
  - map: `map_sizeof(v.v.tree)`;
  - waif: `waif_bytes(v.v.waif)`;
  - every other type: nothing beyond `sizeof(Var)`.
- `src/list.cc:525-548` shows list recursion: `list_sizeof` starts with another
  `sizeof(Var)` for the list length slot and adds `value_bytes()` for each list
  element. Under `MEMO_SIZE`, it caches the computed size in collection
  metadata.
- Other uses of `value_bytes` enforce max-list/max-map value sizes during
  collection construction/mutation, but `bf_value_bytes` itself does not apply
  those quota gates.
- `src/include/structures.h:99-119` defines the user-visible types as integer,
  object, string, error, list, float, map, anonymous object, waif, and boolean.
  Its internal-only/sentinel types are clear, none, catch, finally, and map
  iterator. `value_bytes` has no cases for any of those simple or internal
  types, nor for anonymous objects; each therefore contributes only
  `sizeof(Var)`.
- The pinned profile is explicitly 64-bit. `Var` is one pointer/double-sized
  union plus a `var_type` enum and alignment padding, so its concrete layout is
  16 bytes on this target. Therefore the direct scalar results are 16 bytes for
  integer, object, error, boolean, and anonymous-object reference; float is 24
  bytes because the function adds `sizeof(double)` to the already counted
  `Var`. String is `17 + Latin-1 byte length` including its terminating NUL.
- `src/map.cc:645-676` computes a map as `sizeof(rbtree)` plus, per node,
  `sizeof(rbnode) - 2 * sizeof(Var)` plus recursive `value_bytes(key)` and
  `value_bytes(value)`. On the 64-bit layout that is a 16-byte tree plus 24
  bytes of node links/color/alignment, with the key and value footprints added
  recursively. The outer `value_bytes` also adds its 16-byte `Var`.
- `src/waif.cc:829-849` calls `update_waif_propdefs`, counts `sizeof(Waif)`, and
  recursively adds each mapped property value. It explicitly excludes shared
  class property definitions. The outer `value_bytes` also adds its 16-byte
  `Var`.
- Waif property writes reject direct or nested references back to the waif with
  `E_RECMOVE` (`src/waif.cc:802-812`), traversing lists, maps, and waif property
  values. Database load validation invalidates self-referential waifs. This is
  the relevant source-level cycle prevention; `value_bytes` itself has no
  visited set, depth bound, overflow check, or error path.
- Repeated references are charged repeatedly: list/map/waif recursion invokes
  `value_bytes` for every occurrence. Optional `MEMO_SIZE` metadata caches a
  collection's computed integer size but does not deduplicate it from a parent
  total.

## Exact conformance YAML read so far

- `src/moo_conformance/_tests/builtins/value_bytes.yaml` contains three weak
  relational assertions only:
  1. `value_bytes(42) > 0` returns `1`;
  2. `value_bytes("hello") > value_bytes("")` returns `1`;
  3. `value_bytes({1, 2, 3}) > value_bytes({})` returns `1`.
- It declares only the `value_bytes` builtin requirement. It does not pin exact
  byte counts, arity/type errors, nested maps/waifs/anonymous values, quota,
  recursion depth, cycles, sharing, overflow, permission, or tick cost.
- `src/moo_conformance/_tests/generated_builtins/value_bytes.yaml` names
  `register_function`, `src/list.cc`, handler `bf_value_bytes`, and one
  `TYPE_ANY` argument. Its runnable assertions are exactly:
  - Wizard `function_info("value_bytes")` returns
    `{"value_bytes", 1, 1, {{-1}}}`;
  - zero arguments raises `E_ARGS`;
  - two arguments raise `E_ARGS`.
- The generated metadata has no inferred success type, no inferred raised error
  code, and records `make_var_pack(r)` as an unresolved return. The direct Toast
  implementation establishes the successful result as `TYPE_INT` despite that
  generator limitation.

## Current Banteng surfaces

- `MooValue` is already a sealed ten-case family matching every user-visible
  Toast type: integer, boolean, float, Latin-1 byte string, object, anonymous
  object, WAIF, error, list, and map. Existing accessors are sufficient for all
  scalar/string/list/map calculations: `StringValue.length()`,
  `ListValue.elements()`, and `MapValue.entries()`.
- `ListValue` and `MapValue` take immutable snapshots, so ordinary nested value
  graphs cannot acquire a direct Java collection cycle through their public
  constructors. Repeated references remain possible and must be counted per
  occurrence to match Toast.
- `WaifValue` carries only identity, class, and owner. Its property body is the
  existing `WorldWaif` record behind `WorldTxn.waif(WaifValue)`. Each slot is an
  existing `WorldProperty`; `clear == false` identifies an allocated override,
  matching the propvals that Toast's `waif_bytes` traverses. This means exact
  WAIF sizing must use the current transaction. No new helper, adapter, sender,
  or public value API is required.
- `BuiltinSpec`, `CallShape`, `ArgType.ANY`, `BuiltinPermissionRule.ANY`,
  `BuiltinCostRule.fixed(0)`, `EffectClass`, `BuiltinOwner`, and
  `BuiltinHandler` already express the entire contract and implementation
  entry. `BuiltinCatalog.Result.value(new IntegerValue(...))` is the existing
  success surface.
- `BuiltinCatalog` currently has no `value_bytes` manifest entry or
  implementation.

## Effect / owner decision

- Permission is `ANY`; dynamic tick charge is fixed zero; call shape is exactly
  one required `ANY`; there are no optional or variadic arguments.
- The source operation is read-only, but Banteng cannot calculate Toast's WAIF
  property footprint from `WaifValue` alone. It must read `WorldTxn.waif(...)`.
  Therefore the honest manifest classification in the current architecture is
  `EffectClass.TRANSACTION_READ` and `BuiltinOwner.WORLD`, not `PURE`/`VM`.
  Scalar, string, list, map, and anonymous cases remain computation-only inside
  that one handler; splitting them behind a helper/adapter would violate the
  single-entry deletion-first shape.

## Exact stock 64-bit formulas

The pinned build has `ONLY_32_BITS` undefined and `MEMO_SIZE` enabled. Its
x86-64 layout gives `sizeof(Var) == 16`, `sizeof(double) == 8`,
`sizeof(rbtree) == 16`, `sizeof(rbnode) == 56`, and `sizeof(Waif) == 56`.

| Value type | Exact stock result |
| --- | --- |
| integer, boolean, object, error | 16 |
| anonymous object | 16; do not traverse its object body |
| float | 24 (`Var` plus an additional `double`) |
| string | 17 plus Latin-1 byte length |
| list | 32 plus each element's recursive `value_bytes` |
| map | 32 plus, per entry, 24 bytes node overhead and recursive key/value sizes |
| WAIF | 72 plus each allocated/mapped override's recursive size; exclude shared class definitions and clear/inherited slots |

Repeated/shared nested values are charged once per occurrence. `MEMO_SIZE`
caches a collection's size but does not deduplicate a parent's total.

## Recursion, cycles, quota, and errors

- Toast recursively descends lists, map keys/values, and mapped WAIF overrides.
  It has no visited set, depth bound, overflow check, or error conversion.
- Copy-on-write values are acyclic; WAIF writes reject any nested back-reference
  with `E_RECMOVE`, and load validation invalidates self-referential WAIFs.
  `value_bytes` assumes this invariant rather than handling cycles.
- Deep valid nesting can exhaust the native stack. Accumulation uses signed
  C++ `int`; overflow has no defined builtin error behavior. Banteng must not
  invent `E_MAXREC`, `E_QUOTA`, cycle deduplication, or saturation.
- Collection limits are enforced by value construction/mutation operations.
  `value_bytes` itself does not consult them and never raises `E_QUOTA`.
- Dispatcher-generated `E_ARGS` for arity other than one is the only specified
  error. `TYPE_ANY` means no argument-type `E_TYPE`; the handler has no semantic
  or permission error return.
- Banteng currently does not enforce Toast's recursive WAIF self-reference
  rejection in `WorldTxn.writeWaifProperty`. A list containing a WAIF can be
  written back into that WAIF. `value_bytes` must not hide this earlier
  invariant gap with a visited-set policy. The bounded Phase 5 builtin is exact
  for source-valid states; full exactness also requires that owning earlier
  slice to repair WAIF write validation.

## Smallest exact Phase 5 slice

Only two tracked files belong in this builtin slice:

1. `src/main/java/moo/builtin/BuiltinCatalog.java`: add one manifest entry with
   name `value_bytes`, one required `ANY`, no optional/variadic args,
   permission `ANY`, fixed dynamic cost `0`, effect `TRANSACTION_READ`, owner
   `WORLD`, and the direct catalog handler. Put the actual recursive private
   implementation in `BuiltinCatalog`; use the existing sealed value cases,
   accessors, `WorldTxn.waif()`, and `Result.value(new IntegerValue(...))`.
   This method is the builtin implementation, not a new helper surface.
2. `src/test/java/moo/builtin/BuiltinCatalogTest.java`: add `value_bytes` to
   `REACHABLE_NAMES` and add one focused test for exact manifest fields; exact
   scalar/string/list/map/anonymous/WAIF formulas; repeated-reference charging;
   zero/two-argument `E_ARGS`; and absence of world mutation.

Do not add a `MooValue` method, utility/helper class, adapter, interface,
sender, alternate registry, effect switch, or dispatch switch. The exact
managed rows are `builtins/value_bytes.yaml` and
`generated_builtins/value_bytes.yaml`; those YAML files need no edit.

## Open questions / incomplete reads

- None for the bounded Phase 5 builtin contract.

## Blocker

- No blocker for source-valid world states.
- Full exactness for every currently Banteng-constructible state is blocked by
  the earlier missing recursive WAIF self-reference rejection.

## Next action

Implementation was authorized on `master` at exact HEAD `8a31e5b`; tracked
state was clean before editing and Phase 5 was reread. The current slice changes
only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`:

- the sole manifest now owns the exact one-`ANY`, `ANY` permission, fixed-zero,
  `TRANSACTION_READ`/`WORLD` contract;
- the catalog directly computes the audited stock 64-bit formulas and reads
  only mapped WAIF overrides through the current `WorldTxn`;
- the catalog test ledger and one focused test cover every value family,
  repeated-reference charging, arity errors, the valid WAIF path, and unchanged
  transaction/committed snapshots.

Next, run the one authorized cached WSL Java 25 offline focused test. If it
passes, reread Phase 5, then run the one authorized full `BuiltinCatalogTest`,
followed by `git diff --check`. Keep this note untracked.

## Implementation verification

- Cached WSL Java 25 offline focused test passed:
  `BuiltinCatalogTest.valueBytesUsesTheExactStock64BitLayoutWithoutMutatingCommittedWorld`.
- Phase 5 was reread after the focused pass.
- The one full cached WSL Java 25 offline `BuiltinCatalogTest` run passed.
- Phase 5 was reread again after the full class passed.
- `git diff --check` passed.
- Only the two catalog files were staged and committed as `2920594`
  (`Implement value_bytes builtin`).
- `master` was pushed to `origin/master` from `8a31e5b` through `2920594`.
- This note remains untracked.
