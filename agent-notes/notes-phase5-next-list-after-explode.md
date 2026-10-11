# Phase 5 next list slice after `explode`

Date: 2026-07-21
Mode: read-only audit. No tracked file was edited, and no Gradle, managed-conformance, commit, or push command was run.

## Live boundary

- At audit start, Banteng `HEAD` was `2920594` (`Implement value_bytes builtin`), matched `origin/master`, and had clean tracked state.
- During this audit, another agent began an unrelated two-file tracked `reseed_random` slice. This audit did not inspect beyond identifying its ownership and did not modify it; the `reverse` conclusion remains based on `HEAD 2920594`, the requested post-`explode` boundary, and pinned source/YAML.
- Neither `explode` nor `reverse` is present in the live `BuiltinCatalog.java` or `BuiltinCatalogTest.java` at that HEAD.
- Therefore the live first missing `register_list()` name remains `explode`. This note answers the requested future boundary: once the one-name `explode` slice lands, the exact next missing registration is only `reverse`.
- Phase 5 at `docs/reports/banteng-implementation-plan.md:544-561` requires each canonical name to live in the single `BuiltinSpec` manifest with a complete contract and direct implementation.

## Pinned Toast authority

Selected Phase 5 profiles:

- stock: `profiles/toast/stock-wsl-testdb.json`, implementation commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`;
- Mongoose: `profiles/toast/mongoose-wsl-mongoose.json`, implementation commit `72e3c7f96ce7a41fdeba793aef8818dc4408072e`.

In both exact commits, `src/list.cc:1776` registers `explode` and the immediately following line registers:

```c
register_function("reverse", 1, 1, bf_reverse, TYPE_ANY);
```

The complete `bf_reverse` block at `src/list.cc:827-857` is byte-identical between the two pinned commits.

## Exact contract and ownership

- Canonical name: `reverse`.
- Arity: exactly one argument.
- Accepted registration type: `ANY`, not a string/list union at dispatch time.
- `function_info("reverse")`: `{"reverse", 1, 1, {-1}}`.
- Permission: `BuiltinPermissionRule.ANY`.
- Tick cost: `BuiltinCostRule.fixed(0)`; the VM call opcode owns the ordinary tick.
- Effect: `EffectClass.PURE`.
- Owner: `BuiltinOwner.VM`.
- Required Banteng call shape: `new CallShape(List.of(ANY), List.of(), Optional.empty())`.

`ANY` is essential to the Toast contract: unsupported value types reach the handler and produce `E_INVARG`; narrowing the manifest to string/list would incorrectly produce `E_TYPE` before dispatch.

## Exact semantics and errors

`bf_reverse` has only three paths:

1. List input: allocate a list of the same length and copy element references from last to first. Reversal is shallow: nested lists, maps, objects, waifs, and other elements retain their values/identity and are not recursively reversed. Empty and singleton lists remain value-equal to their input.
2. String input: reverse the string one stored byte at a time. This is byte reversal, not Unicode code-point or grapheme reversal. Empty and one-byte strings remain value-equal to their input.
3. Every other value type: `E_INVARG`.

There is no `E_TYPE`, `E_RANGE`, or semantic `E_ARGS` branch in `bf_reverse`, and no list-size/quota check. The registration layer supplies `E_ARGS` for zero or two-or-more arguments. Because the registered argument type is `ANY`, all correctly shaped calls enter the handler.

## Active YAML authority

- `../moo-conformance-tests/src/moo_conformance/_tests/builtins/reverse.yaml:15-63` requires ordinary, empty, singleton, palindrome, list, mixed-list, integer-error, and map-error behavior.
- `../moo-conformance-tests/src/moo_conformance/_tests/builtins/reverse_call_shapes.yaml:9-19` requires float, object, and error inputs to produce `E_INVARG`.
- `../moo-conformance-tests/src/moo_conformance/_tests/generated_builtins/reverse.yaml:22-40` requires `function_info("reverse") == {"reverse", 1, 1, {-1}}`, plus `E_ARGS` for zero and two arguments.

## Smallest direct implementation slice

Modify only the existing production manifest/handler file and its direct catalog test:

1. Add one `BuiltinSpec("reverse", ...)` entry to `BuiltinCatalog.buildManifest()` with the exact contract and ownership above.
2. Dispatch directly to one required `reverse(List<MooValue>)` handler in `BuiltinCatalog`.
3. In that handler:
   - for `ListValue`, snapshot `list.elements()` in reverse order into one new `ListValue`;
   - for `StringValue`, obtain `bytes()`, swap bytes in place from the ends inward, and return one new `StringValue`;
   - otherwise return `Result.error(ErrorValue.E_INVARG)`.
4. Add no helper, interface, adapter, sender, alias, recursive traversal, Unicode conversion, or second dispatch path.

The current `StringValue.bytes()` defensive copy and `ListValue` snapshot constructor are sufficient. Java `String`, regex, and `StringBuilder.reverse()` are the wrong representation because Toast reverses stored bytes.

## Deterministic direct tests

In `BuiltinCatalogTest.java`:

1. Add `"reverse"` to `REACHABLE_NAMES`.
2. Extend the pure-VM contract test with `new CallShape(List.of(ANY), List.of(), Optional.empty())`, and assert permission `ANY`, fixed-zero cost, effect `PURE`, and owner `VM` through the existing contract helper.
3. Add one focused regression that invokes the manifest entry and asserts:
   - raw bytes `[0x41, 0xE9, 0xFF]` become `[0xFF, 0xE9, 0x41]`;
   - empty and one-byte strings remain equal;
   - `"hello"` becomes `"olleh"` and `"abcba"` remains `"abcba"`;
   - `{1, 2, 3}` becomes `{3, 2, 1}`;
   - `{}` and `{42}` retain their values;
   - `{"a", 1, #0, {2, 3}}` becomes `{{2, 3}, #0, 1, "a"}`, proving shallow reversal;
   - integer, float, object, error, and map inputs each return `E_INVARG`;
   - zero arguments and two arguments each return `E_ARGS`;
   - no correctly shaped unsupported-type call returns `E_TYPE`.
4. Invoke `function_info("reverse")` and assert the exact value `{"reverse", 1, 1, {-1}}`.

These assertions cover every active YAML row and the source-critical byte-reversal and shallow-list boundaries without inventing another abstraction.
