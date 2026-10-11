# Phase 5 next list slice after `slice`

Date: 2026-07-21
Mode: read-only audit in progress. This note must remain untracked. No tracked file was edited, and no Gradle, managed-conformance, commit, or push command was run.

## Current findings

- Banteng `HEAD` at audit start is `a8c1e23` (`Implement reseed_random builtin`), matching `origin/master`, with clean tracked state.
- During the audit, another agent began the expected two-file tracked `explode` slice. This audit identified that ownership and left the diff untouched.
- `slice` is not yet present at this live HEAD. At the requested future boundary immediately after `slice` lands, both pinned profile sources identify the next registration as `sort`.
- Stock commit `aecc51e9449c6e7c95272f0f044b5ba38948459e` and Mongoose commit `72e3c7f96ce7a41fdeba793aef8818dc4408072e` have identical `sort_callback` / `bf_sort` source at `src/list.cc:945-1026`.
- Exact registration: `register_function("sort", 1, 4, bf_sort, TYPE_LIST, TYPE_LIST, TYPE_INT, TYPE_INT)`.
- Exact function contract: required `LIST`; optional `LIST`, `INTEGER`, `INTEGER`; `function_info("sort") == {"sort", 1, 4, {4, 4, 0, 0}}`.
- Permission is `ANY`, fixed builtin tick cost is zero, and owner is `VM`.
- The effect must be `SUSPENDING_HOST`, not `PURE`: `bf_sort` delegates to Toast `background_thread`. Both pinned profiles compile `TOTAL_BACKGROUND_THREADS 2` and `DEFAULT_THREAD_MODE true`. In the default mode the package suspends the MOO task and can return `E_QUOTA` if a worker or pipe cannot be created; a verb can explicitly disable thread mode and make the same callback execute synchronously.
- Active YAML files are `builtins/sort_call_shapes.yaml:9-31` and `generated_builtins/sort.yaml:22-63`.

## Observed semantic outline

- With no nonempty second list, sort argument 1 directly.
- A nonempty argument 2 is a parallel key list: it must have the same length as argument 1, and argument 1 values are permuted according to the sorted key indices.
- Sortable input/key lists must be homogeneous. Mixed types and homogeneous list/map/anonymous/waif values return `E_TYPE`.
- Explicit comparisons cover integer, float, object id, error code, and string values.
- Strings sort case-insensitively. Nonzero argument 3 enables case-insensitive natural ordering. Nonzero argument 4 reverses the final sorted index order.
- A nonempty key list of the wrong length returns `E_INVARG`.
- Registration supplies `E_ARGS` and argument-position `E_TYPE` errors.

## Exact contract and ownership

- Canonical name: `sort`.
- Arity: one through four arguments.
- Required argument: `LIST` of values to return in sorted order.
- Optional argument 2: `LIST` of parallel sort keys.
- Optional argument 3: `INTEGER` natural-sort flag.
- Optional argument 4: `INTEGER` reverse-result flag.
- Exact call shape: `new CallShape(List.of(LIST), List.of(LIST, INTEGER, INTEGER), Optional.empty())`.
- `function_info("sort")`: `{"sort", 1, 4, {4, 4, 0, 0}}`.
- Permission: `BuiltinPermissionRule.ANY`.
- Tick cost: `BuiltinCostRule.fixed(0)`; the VM call opcode owns the ordinary tick.
- Effect: `EffectClass.SUSPENDING_HOST`.
- Owner: `BuiltinOwner.VM`.

The background callback does not read or mutate world state, but default suspension is observable task behavior and moves the sort work outside the task's ordinary execution segment. Calling it `PURE` would erase the pinned package boundary.

## Exact input-selection and validation semantics

The active comparison list is selected before validation:

- If argument 2 is absent or empty, argument 1 is both the values list and comparison list.
- If argument 2 is nonempty, it is the comparison list; argument 1 remains the values returned after applying the sorted key indices.
- A nonempty comparison-key list must have exactly the same length as argument 1 or the callback returns `E_INVARG`.
- An empty active comparison list returns `{}` immediately. Consequently `sort({})` and `sort({}, {})` return `{}`, while `sort({}, {1})` returns `E_INVARG`.
- An empty argument 2 is only a sentinel meaning "sort argument 1 directly"; it is not a mismatched parallel key list.

Only the active comparison list is type-validated. Argument 1 may contain mixed or otherwise unsortable values when a nonempty valid key list is supplied, because those values are only permuted.

For a nonempty active comparison list:

- every element must have exactly the same Toast type as the first element;
- mixed types, including mixed integer/float numbers, return `E_TYPE`;
- homogeneous list, map, anonymous-object, or waif comparison values return `E_TYPE`;
- explicit comparisons exist for integer value, float value, object id, error-code ordinal, and string bytes;
- string comparison is case-insensitive in both ordinary and natural modes;
- argument 3 is truth-tested: zero selects ordinary case-insensitive lexical order, any nonzero integer selects case-insensitive natural order (`strnatcasecmp`), so numeric runs such as `2` and `10` compare numerically;
- argument 4 is truth-tested: any nonzero integer reverses the fully sorted index vector.

Toast uses `std::sort`, not a stable sort. Equal comparison keys have no guaranteed relative order, and reverse mode reverses the whole index vector, including equal-key groups. Deterministic tests must use distinct keys unless they assert only a valid permutation. Case-only string differences are equal keys for this purpose.

The source permits homogeneous boolean comparison lists through initial validation but the comparator has no boolean case; it logs an unknown-type diagnostic and treats comparisons as false. The pinned source therefore supplies no meaningful true/false ordering. Do not invent one or add a deterministic boolean-order assertion without a managed Toast row explicitly deciding that boundary.

## Exact result behavior

- Sort an index vector derived from the active comparison list.
- Return a new list containing argument 1's values at those sorted indices.
- With a nonempty parallel key list, keys are never returned.
- Values are copied shallowly; nested lists/maps and identity-bearing values are not recursively copied or compared when keys are active.
- Integer, float, object, and error comparisons are ascending by their stored numeric/ordinal representation.
- String comparisons are byte-string comparisons using Toast's case-folding comparator; no Unicode collation is involved.
- Natural mode affects only strings. For other supported types it produces the same order as ordinary mode.
- Reverse mode is implemented after ascending sort, not through a separate descending comparator.
- No output-size or list-quota check appears in `sort_callback`.

## Exact errors and suspension boundary

Registration supplies:

- `E_ARGS` for zero or five-or-more arguments;
- `E_TYPE` when argument 1 or 2 is not a list;
- `E_TYPE` when argument 3 or 4 is not an integer.

The callback supplies:

- `E_INVARG` for a nonempty key-list length mismatch;
- `E_TYPE` for mixed active comparison types or homogeneous list/map/anonymous/waif comparison values;
- no `E_RANGE` path.

`background_thread` adds a host boundary:

- in the pinned default thread mode, successful worker setup returns a suspend package and resumes the task with the callback's value or MOO error;
- failure to create background work or its pipe returns `E_QUOTA` before callback validation;
- with thread mode disabled, the callback runs synchronously and its value/error is returned directly.

This ordering means host `E_QUOTA` may precede callback `E_INVARG`/`E_TYPE` in threaded mode, while registration arity/type errors still occur before `bf_sort` is entered.

## Active YAML authority

- `../moo-conformance-tests/src/moo_conformance/_tests/builtins/sort_call_shapes.yaml:9-31` proves one-, two-, three-, and four-argument integer sorting; empty argument 2 means direct sorting, zero natural mode preserves ordinary ordering, and nonzero argument 4 reverses it.
- `../moo-conformance-tests/src/moo_conformance/_tests/generated_builtins/sort.yaml:22-63` proves `function_info("sort") == {"sort", 1, 4, {4, 4, 0, 0}}`, `E_ARGS` for zero and five arguments, and dispatch-level `E_TYPE` at each typed argument position.
- No separate active semantic `builtins/sort.yaml` exists. Keyed sorting, type validation, natural ordering, stability, background suspension, and callback errors are source-only authority in this audit.

## Current Banteng prerequisite gap

The comparison algorithm itself fits in `BuiltinCatalog`, but exact Toast execution does not currently fit a catalog-only synchronous handler:

- `BuiltinCatalog.Result` and `VmState` contain a dormant `hostResult` / `CompletableFuture<MooValue>` suspension route, and the VM/scheduler can suspend and resume from it.
- No current builtin constructs a `hostResult`, and `Result` has no direct host-result factory.
- `CompletableFuture<MooValue>` can resume with a value but cannot represent "resume by raising this MOO error"; Toast callback errors occur after suspension.
- Banteng has no live per-task `set_thread_mode` state. Its v17 reader currently accepts only queued-task thread-mode value `1`, corresponding to the pinned default enabled state.
- There is no catalog-owned bounded background worker whose rejection can map to Toast `E_QUOTA`.

Therefore a synchronous `PURE` implementation would be a semantic substitution: it would consume task-segment CPU, never suspend, never produce host `E_QUOTA`, and validate callback errors at the wrong scheduling boundary. Do not label such a handler complete.

The smallest exact production slice must first use an already-authorized host suspension/error continuation and bounded-worker owner. If the parent cannot point to that existing owner, `sort` is blocked on an explicit plan/user decision; this audit does not authorize inventing a new executor, adapter, future wrapper, or alternate scheduler.

Once that exact owner exists, keep the sort-specific code direct:

1. Add one canonical `BuiltinSpec("sort", ...)` entry with the exact contract, `SUSPENDING_HOST`, and owner `VM`.
2. Validate registration-level types through the existing call shape.
3. Submit one immutable snapshot of arguments to the authorized bounded host worker and return the existing host suspension result.
4. In the one sort callback/handler, select and validate the comparison list, sort indices, and build one shallow `ListValue` from argument 1.
5. Use existing `StringValue.compareIgnoringCase` for ordinary strings. Implement the pinned natural numeric-run comparison directly in the sort owner; do not use locale collation or Java `String` conversion.
6. Add no interface, adapter, sender, alternate scheduler, world access, recursive copy, or public helper.

## Deterministic tests

Manifest/direct contract tests:

1. Add `"sort"` to `REACHABLE_NAMES`.
2. Assert the exact call shape, permission `ANY`, fixed-zero cost, effect `SUSPENDING_HOST`, and owner `VM`.
3. Assert `function_info("sort") == {"sort", 1, 4, {4, 4, 0, 0}}`.
4. Assert zero/five arguments produce `E_ARGS`; wrong types at arguments 1 through 4 independently produce `E_TYPE`.

Deterministic comparison tests, using distinct keys:

5. `sort({3, 1, 2})` and `sort({3, 1, 2}, {})` both return `{1, 2, 3}`.
6. Four-argument reverse mode returns `{3, 2, 1}`; use a negative nonzero flag too, proving truth-testing rather than equality with `1`.
7. A nonempty key list reorders arbitrary mixed values without comparing them, e.g. values `{"three", #1, E_ARGS}` with keys `{3, 1, 2}` return `{#1, E_ARGS, "three"}`.
8. A nonempty key list with mismatched length returns `E_INVARG`, including `sort({}, {1})`.
9. Direct mixed comparison types and homogeneous list/map/anonymous/waif comparison values return `E_TYPE`; the same values are accepted in argument 1 when sorted by distinct valid keys.
10. Cover ascending integers, floats, object ids, errors, and case-insensitive strings.
11. Ordinary strings `{"x10", "X2", "x1"}` sort lexically as `{"x1", "x10", "X2"}`; natural mode sorts them as `{"x1", "X2", "x10"}`.
12. Do not assert relative order for equal keys or boolean lists.

Suspension/owner tests required before calling the slice exact:

13. Under pinned default thread mode, invocation reaches a suspended VM state with a host continuation rather than returning synchronously.
14. Completion resumes the same VM with the sorted value; callback `E_TYPE` and `E_INVARG` resume by raising those MOO errors, not by pushing error values.
15. Bounded-worker rejection maps to `E_QUOTA` without running the callback.
16. Explicit disabled thread mode runs the callback synchronously with the same value/error.
17. Verify argument/value snapshots isolate background work from later mutation and that sorting performs no world read/write.

## State

Audit complete. The next canonical registration after `slice` is `sort`, but exact implementation is not currently a simple two-file pure-VM slice because pinned default execution is a suspending host operation and Banteng lacks a complete error-bearing host continuation/thread-mode owner. No build or conformance command was run.
