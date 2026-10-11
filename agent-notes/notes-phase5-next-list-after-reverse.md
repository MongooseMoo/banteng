# Phase 5 next list slice after `reverse`

Date: 2026-07-21
Mode: read-only audit. No tracked file was edited, and no Gradle, managed-conformance, commit, or push command was run.

## Live and requested boundary

- Banteng `HEAD` at audit time is `2920594` (`Implement value_bytes builtin`) and matches `origin/master`.
- Another agent owns the current two-file tracked `reseed_random` diff; this audit did not modify it.
- `reverse` is not yet present at this live HEAD. This note answers the requested future boundary: immediately after the one-name `reverse` slice lands, the exact next missing `register_list()` builtin is only `slice`.
- Phase 5 at `docs/reports/banteng-implementation-plan.md:544-561` requires the canonical name, complete contract, and direct implementation to remain in the single production `BuiltinSpec` manifest.

## Pinned Toast authority

Selected Phase 5 profiles:

- stock `profiles/toast/stock-wsl-testdb.json`: implementation commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`;
- Mongoose `profiles/toast/mongoose-wsl-mongoose.json`: implementation commit `72e3c7f96ce7a41fdeba793aef8818dc4408072e`.

In both exact commits, `src/list.cc:1777` registers `reverse`, and the immediately following line registers:

```c
register_function("slice", 1, 3, bf_slice, TYPE_LIST, TYPE_ANY, TYPE_ANY);
```

The complete `bf_slice` body at `src/list.cc:860-943` is identical between the two pinned commits.

## Exact contract and ownership

- Canonical name: `slice`.
- Arity: one through three arguments.
- Required argument: `LIST`.
- Optional positional arguments: `ANY`, `ANY`.
- Exact call shape: `new CallShape(List.of(LIST), List.of(ANY, ANY), Optional.empty())`.
- `function_info("slice")`: `{"slice", 1, 3, {4, -1, -1}}`.
- Permission: `BuiltinPermissionRule.ANY`.
- Tick cost: `BuiltinCostRule.fixed(0)`; the VM call opcode owns the ordinary tick.
- Effect: `EffectClass.PURE`.
- Owner: `BuiltinOwner.VM`.

Both optional positions must remain `ANY`. Toast intentionally validates the selector inside `bf_slice`, so narrowing argument 2 in the manifest would incorrectly turn required `E_INVARG` results into dispatch-level `E_TYPE`. Argument 3 is an unrestricted default value.

## Exact selector validation

The second argument defaults to integer `1` when omitted. When supplied, it must be one of:

- a positive integer;
- a string map key;
- a nonempty list containing only positive integers.

Errors are exact:

- selector of any other type: `E_INVARG`;
- empty selector list: `E_RANGE`;
- non-integer member in a selector list: `E_INVARG`;
- zero or negative integer selector or selector-list member: `E_RANGE`.

Validation occurs even when the required outer list is empty. Thus `slice({}, 1)`, `slice({}, "x")`, and `slice({}, {1})` return `{}`, while `slice({}, 1.5)` returns `E_INVARG` and `slice({}, {})` returns `E_RANGE`.

## Exact projection semantics

`slice` walks the required outer list in order and builds one result list.

### Integer selector

- Every outer element must be a list or string; a map or any scalar element produces `E_INVARG` for the whole call.
- Indexing is one-based.
- A list row contributes the selected value without recursively copying it.
- A string row contributes a one-byte string at that stored-byte position; this is byte indexing, not Unicode code-point indexing.
- If the index exceeds any row's length, the whole call returns `E_RANGE` and no partial result.
- Argument 3 is ignored for integer selectors; it does not replace an out-of-range value.

### List-of-integers selector

- Every outer element must be a list or string.
- For each row, produce an inner list containing the selected positions in the selector's order. Duplicate positions remain duplicated.
- List-row values are shallow references; string-row values are one-byte strings.
- If any selected position exceeds any row's length, the whole call returns `E_RANGE`.
- Argument 3 is ignored for selector lists.

### String selector

- Every outer element must be a map; a list, string, or scalar row produces `E_INVARG` for the whole call.
- Map-key lookup is case-insensitive because Toast calls `maplookup(..., case_matters=0)`.
- A found key contributes its mapped value.
- A missing key contributes argument 3 when supplied.
- A missing key contributes nothing when argument 3 is absent, so the result can be shorter than the outer list.
- The default is unrestricted and is copied as a value; it may be an integer, float, object, string, error, list, or map.

There is no output-size/quota branch in `bf_slice`.

## Active YAML authority

- `../moo-conformance-tests/src/moo_conformance/_tests/builtins/slice_call_shapes.yaml:9-105` proves valid empty-input selectors (`INT`, `STR`, list-of-`INT`), `E_INVARG` for float/object/error/map selectors, every valid-selector plus unrestricted-default type combination, and the same invalid selectors with every default type.
- `../moo-conformance-tests/src/moo_conformance/_tests/generated_builtins/slice.yaml:22-47` proves `function_info("slice") == {"slice", 1, 3, {4, -1, -1}}`, `E_ARGS` for zero and four arguments, and `E_TYPE` when argument 1 is not a list.
- No separate active `builtins/slice.yaml` exists; the deterministic production test must therefore preserve the source-only projection semantics above rather than pretending the call-shape YAML proves them.

## Smallest direct implementation slice

Modify only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`:

1. Add one canonical `BuiltinSpec("slice", ...)` entry with the exact contract and ownership above.
2. Dispatch directly to one required `slice(List<MooValue>)` handler in the existing catalog.
3. Inside that handler, validate the selector first, then iterate the outer `ListValue` once:
   - use `ListValue.elements()` and one-based-to-zero-based conversion for list rows;
   - use `StringValue.bytes()` and construct one-byte `StringValue` results for string rows;
   - use existing `MapValue.get(StringValue)`, whose string comparison is case-insensitive, for map rows;
   - accumulate directly into `ArrayList<MooValue>` and return one `ListValue`.
4. Return immediately with the exact error when selector validation, row-type compatibility, or bounds validation fails; never return a partial result.
5. Add no helper, interface, adapter, sender, alias, recursive traversal, Unicode conversion, alternate index operator, or second dispatch path.

The existing value APIs are sufficient. Reusing VM indexing machinery would broaden ownership and risks changing the exact error boundary.

## Deterministic direct tests

In `BuiltinCatalogTest.java`:

1. Add `"slice"` to `REACHABLE_NAMES`.
2. Extend the pure-VM contract test with `new CallShape(List.of(LIST), List.of(ANY, ANY), Optional.empty())`, retaining permission `ANY`, fixed-zero cost, effect `PURE`, and owner `VM`.
3. Add a focused selector/projection regression that asserts:
   - `slice({{1, 2}, {3, 4}})` returns `{1, 3}`;
   - `slice({{10, 20}, "ab"}, 2)` returns `{20, "b"}`;
   - raw high-bit string bytes are selected without character decoding;
   - `slice({{10, 20, 30}, "abc"}, {3, 1, 3})` returns `{{30, 10, 30}, {"c", "a", "c"}}`, proving order, duplication, byte behavior, and shallow list selection;
   - case-insensitive key lookup over maps with keys `"Name"` and `"name"` works for selector `"NAME"`;
   - a missing map key is omitted without a default and inserts the supplied value with a default;
   - each unrestricted default kind from active YAML (integer, float, object, string, error, list, map) is accepted;
   - an integer or list selector applied to a map row returns `E_INVARG`;
   - a string selector applied to a list or string row returns `E_INVARG`;
   - a scalar outer element returns `E_INVARG`;
   - an out-of-range integer or selector-list member returns `E_RANGE`, even when argument 3 is present.
4. Add selector-validation assertions matching the YAML boundary:
   - float, object, error, and map selectors return `E_INVARG`;
   - an empty selector list returns `E_RANGE`;
   - a selector list containing a non-integer returns `E_INVARG`;
   - selector `0`, selector `-1`, and selector-list members `0` or `-1` return `E_RANGE`;
   - valid integer/string/list selectors over an empty outer list return `{}`.
5. Assert dispatch behavior exactly:
   - zero arguments and four arguments return `E_ARGS`;
   - a non-list first argument returns `E_TYPE`;
   - `function_info("slice")` returns `{"slice", 1, 3, {4, -1, -1}}`.

These tests cover every active YAML contract row and the pinned implementation's otherwise-uncovered projection, byte-indexing, shallow-reference, default, compatibility, and error semantics.
