# Phase 5 list/string builtin family audit

Date: 2026-07-21
Initial mode: read-only future-slice audit against Banteng `d668c56`; another agent owned the worktree. The later `setremove` implementation slice was explicitly delegated on `de4edd6`. No sibling-repository or extraction-fixture modification was performed.

## Controlling Phase 5 requirement

`docs/reports/banteng-implementation-plan.md:544-561` requires completion of the single production `BuiltinSpec` table and canonical pinned-profile name coverage before the later extraction and full conformance gates at lines 563-578.

Current Banteng authority:

- `src/main/java/moo/builtin/BuiltinCatalog.java:117-877` builds the manifest. At `d668c56` it contains 76 `BuiltinSpec` entries.
- `src/test/java/moo/builtin/BuiltinCatalogTest.java:37-131` still compares that manifest to the hand-maintained `REACHABLE_NAMES` set, not to a pinned Toast fixture.
- None of the 13 names inventoried below occurs in the current `BuiltinCatalog` or `BuiltinCatalogTest`.

Pinned Toast authority:

- Exact source object: commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`, `src/list.cc:1755-1800` (`register_list`).
- That function registers 29 canonical list/string-family names. Sixteen are already present in Banteng: `decode_binary`, `encode_binary`, `chr`, `length`, `setadd`, `listappend`, `listinsert`, `listdelete`, `listset`, `equal`, `tostr`, `toliteral`, `index`, `rindex`, `strcmp`, and `strsub`.

## Missing canonical registrations

All 13 missing names, exact Toast contracts, and the exact active `builtins` / `generated_builtins` YAML surfaces are:

| Coherent family | Missing canonical name | Pinned Toast registration | Exact contract | Active YAML authority |
|---|---|---:|---|---|
| value sizing | `value_bytes` | `src/list.cc:1758` | 1..1, `ANY` | `builtins/value_bytes.yaml:1-33`; `builtins/value_bytes_call_shapes.yaml:1-21`; `generated_builtins/value_bytes.yaml:1-40` |
| set completion | `setremove` | `src/list.cc:1767` | 2..2, `LIST, ANY` | `builtins/setremove_call_shapes.yaml:1-23`; `generated_builtins/setremove.yaml:1-46` |
| list transforms | `explode` | `src/list.cc:1776` | 1..3, `STR, [STR, INT]` | `builtins/explode.yaml:1-86`; `generated_builtins/explode.yaml:1-57` |
| list transforms | `reverse` | `src/list.cc:1777` | 1..1, `ANY` | `builtins/reverse.yaml:1-63`; `builtins/reverse_call_shapes.yaml:1-19`; `generated_builtins/reverse.yaml:1-40` |
| list transforms | `slice` | `src/list.cc:1778` | 1..3, `LIST, [ANY, ANY]` | `builtins/slice_call_shapes.yaml:1-105`; `generated_builtins/slice.yaml:1-47` |
| list transforms | `sort` | `src/list.cc:1779` | 1..4, `LIST, [LIST, INT, INT]` | `builtins/sort_call_shapes.yaml:1-31`; `generated_builtins/sort.yaml:1-63` |
| membership | `all_members` | `src/list.cc:1780` | 2..2, `ANY, LIST` | `builtins/all_members_call_shapes.yaml:1-23`; `generated_builtins/all_members.yaml:1-46` |
| pattern matching | `match` | `src/list.cc:1786` | 2..3, `STR, STR, [ANY]` | `builtins/match_call_shapes.yaml:1-22`; `builtins/match_rmatch.yaml:1-93`; `builtins/match_percent_escapes.yaml:1-131`; `generated_builtins/match.yaml:1-52` |
| pattern matching | `rmatch` | `src/list.cc:1787` | 2..3, `STR, STR, [ANY]` | `builtins/rmatch_call_shapes.yaml:1-22`; shared `match_rmatch.yaml:1-93` and `match_percent_escapes.yaml:1-131`; `generated_builtins/rmatch.yaml:1-52` |
| pattern replacement | `substitute` | `src/list.cc:1788` | 2..2, `STR, LIST` | `builtins/substitute.yaml:1-61`; `builtins/substitute_validation.yaml:1-29`; `generated_builtins/substitute.yaml:1-51` |
| string translation | `strtr` | `src/list.cc:1796-1797` | 3..4, `STR, STR, STR, [ANY]` | `builtins/strtr_call_shapes.yaml:1-23`; `generated_builtins/strtr.yaml:1-58` |
| ANSI parsing | `parse_ansi` | `src/list.cc:1798` | 1..1, `STR` | `generated_builtins/parse_ansi.yaml:1-45` |
| ANSI parsing | `remove_ansi` | `src/list.cc:1799` | 1..1, `STR` | `generated_builtins/remove_ansi.yaml:1-45` |

Square brackets in the contract column denote positional optional arguments, not a MOO list argument. These contracts come from the pinned source registration itself; the generated YAML independently encodes the corresponding `function_info` shape and generic arity/type failures.

## Smallest coherent next family: `setremove`

The smallest non-substituted next family is the one-name set-completion family, `setremove`. It completes the already-landed `setadd` pair without pulling in sorting, pattern matching, ANSI parsing, value sizing, or another abstraction.

### Exact manifest contract

- Name: `setremove`.
- Required arguments: exactly `LIST, ANY`; no optional or variadic arguments (`src/list.cc:1767`).
- Permission/effect/owner representation consistent with the adjacent current `setadd` entry: `BuiltinPermissionRule.ANY`, fixed zero cost, `EffectClass.PURE`, `BuiltinOwner.VM` (`BuiltinCatalog.java:240-247`).
- `generated_builtins/setremove.yaml:22-31` requires `function_info("setremove") == {"setremove", 2, 2, {4, -1}}`.
- `generated_builtins/setremove.yaml:32-46` requires `E_ARGS` for too few/many arguments and `E_TYPE` when argument 1 is not a list.
- `builtins/setremove_call_shapes.yaml:9-23` requires argument 2 to accept integer, float, object, string, error, list, and map values and always return a list for those rows.

### Exact pinned semantics and edge cases

- `src/list.cc:158-168` calls `ismember(value, list, 0)`. The zero means case does not matter.
- `src/collection.cc:45-57` scans in one-based order and returns the first matching position. Therefore only the first equal member is removed; later duplicates remain and relative order is preserved.
- If no member matches, including an empty input list, Toast returns the original list unchanged (`src/list.cc:163-167`). There is no `E_RANGE` path.
- Membership uses `equality(..., case_matters=0)` (`src/utils.cc:444-492`): same-type integers, objects, errors, floats, and booleans compare by value; strings compare case-insensitively; lists and maps compare recursively with the same case rule; anonymous objects and waifs compare by identity; and boolean/integer cross-type values compare equal when the integer is `0` or `1` matching the boolean.
- The wrapper at `src/list.cc:678-692` applies `value_bytes(result) <= max_list_value_bytes` even to the no-op result. Over-limit output becomes `E_QUOTA` when `max_concat_catchable` is enabled, otherwise an `ABORT_SECONDS` package through `make_space_pack()` (`src/list.cc:620-627`). The current focused YAML does not exercise this limit.

### Current reusable Banteng surfaces

- Direct manifest pattern: adjacent `setadd` at `BuiltinCatalog.java:240-247` already supplies the correct call-shape/effect/owner shape. Add the canonical entry directly to this manifest; do not add another dispatcher, sender, adapter, or interface.
- Immutable list access/construction: `MooValue.ListValue.elements()` at `MooValue.java:480-482`, the snapshot constructor at lines 475-477, and the direct copy/remove pattern already used by `BuiltinCatalog.listDelete()` at `BuiltinCatalog.java:1377-1385` are sufficient to preserve order without a new list abstraction.
- Partial equality primitives: `StringValue.equals()` at `MooValue.java:251-272`, `ListValue.equals()` at lines 536-539, and `MapValue.equals()` at lines 635-636 provide current recursive case-insensitive value equality. `MooVm.equality()` at `MooVm.java:1551-1565` additionally contains the required boolean/integer cross-type rule.
- No single current `BuiltinCatalog` method is a semantically exact reusable membership predicate. `BuiltinCatalog.exactlyEqual()` at lines 2813-2849 is deliberately case-sensitive for strings and is wrong for `setremove`; current `setAdd()` at lines 1454-1457 delegates to Java `List.contains`, which does not itself add the VM's boolean/integer cross-type rule. The next slice should keep the scan/removal directly in the existing catalog owner and prove the recursive case-insensitive plus boolean/integer boundary, rather than treating either nearby method as an exact substitute.

## Audit result

- Canonical pinned registrations in this source family: 29.
- Present in Banteng after `d668c56`: 16.
- Missing: the 13 exact names listed above.
- Recommended next implementation grouping: only `setremove`, with the exact source/YAML contract above.
- No Gradle or managed conformance command was run; this is source/YAML audit evidence only.

## State

The audit is complete. On top of `de4edd6`, the delegated working slice adds only canonical `setremove` to `BuiltinCatalog.java` and its direct catalog tests to `BuiltinCatalogTest.java`. The working manifest now has `setremove`; 12 names from the original table remain missing.

The two-file kept slice is commit `8a31e5b` (`feat: implement setremove builtin`), pushed to `origin/master` (`de4edd6..8a31e5b`). This note remains untracked and outside that commit.

The implementation uses the existing single manifest/dispatch path and a direct iterative recursive-equality scan inside `setRemove`; it adds no helper, interface, adapter, sender, alias, or placeholder. The focused regression proves case-insensitive nested map/list equality, nested boolean/integer compatibility, first-occurrence-only removal, unchanged absent return, `E_ARGS`, and `E_TYPE`. The existing pure-VM contract test now includes exact `LIST, ANY` `setremove` shape and the catalog name set includes `setremove`.

Verification, in the required order:

- One focused cached WSL Java 25 offline test: `./gradlew --offline test --tests 'moo.builtin.BuiltinCatalogTest.setRemoveUsesRecursiveCaseInsensitiveMooEqualityAndRemovesOnlyTheFirstMatch'` — `BUILD SUCCESSFUL` in 1m20s.
- Phase 5 reread.
- One full cached WSL Java 25 offline class test: `./gradlew --offline test --tests 'moo.builtin.BuiltinCatalogTest'` — `BUILD SUCCESSFUL` in 6s.
- `git diff --check` — exit 0.

## Blocker

None. Required focused/full tests and diff hygiene pass.

## Next action

Parent should reread Phase 5 and choose the next missing canonical builtin slice. This agent will not start another slice; leave this note untracked.
