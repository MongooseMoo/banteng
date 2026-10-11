# Phase 5 next numeric builtin after `frandom`: `round`

## Scope

- Read-only future-slice audit; do not implement, run Gradle/conformance, commit, push, or edit tracked files.
- This untracked note is the only write.

## Pinned source facts

- Both pinned stock and Mongoose `src/numbers.cc` register `round` immediately after `frandom` and before `random_bytes`.
- Both registrations are exactly one required argument, maximum one argument, strict `TYPE_FLOAT`.
- Both bodies are identical: read the argument's double value, call the C/C++ `round(double)` function, free the argument list, and return `Var::new_float(result)`.
- The body contains no permission check, world access, explicit validation branch, or explicit error return.

## Active YAML inventory

- Exact `round(...)` rows exist in `generated_builtins/round.yaml`, `builtins/math.yaml`, and the weaker presence-only `builtins/builtin_presence_core.yaml`.

## Exact contract, effect, and owner

- Canonical name: `round`; exactly one required `FLOAT`, no optional or variadic arguments.
- Permission: `BuiltinPermissionRule.ANY`.
- Tick cost: fixed `0`.
- Effect: `EffectClass.PURE`; the result depends only on the argument and consumes no mutable host state.
- Owner: `BuiltinOwner.VM`; the body does not access `WorldTxn`.
- Result type is always `FLOAT`, not integer.

## Exact semantics and errors

- Return the nearest integral binary64 value while preserving the float type.
- Values exactly halfway between integers round away from zero: `2.5 -> 3.0`, `-2.5 -> -3.0`.
- Non-half values round to the nearest integer: `2.3 -> 2.0`, `2.7 -> 3.0`, `-2.3 -> -2.0`, `-2.7 -> -3.0`.
- Integral values remain unchanged. C `round()` preserves the sign of zero, so `-0.0` remains negative zero at the binary64 payload level even though MOO equality treats both zeros equal.
- Arity zero or two produces `E_ARGS`; any non-float argument, including an integer, produces `E_TYPE` before the handler runs.
- The Toast body has no explicit error return. It does not raise `E_INVARG` or `E_FLOAT` for a valid float argument.

## Active YAML details

- Generated YAML fixes `function_info("round") == {"round", 1, 1, {FLOAT}}`, the two arity errors, strict type rejection, and float success type.
- `builtins/math.yaml` fixes positive/negative above-half, below-half, exact-half, zero, and integer-type-error rows.
- Presence YAML adds no stronger semantic fact.

## Smallest direct implementation

- Edit only `src/main/java/moo/builtin/BuiltinCatalog.java` and `src/test/java/moo/builtin/BuiltinCatalogTest.java`.
- Add one `round` manifest entry immediately after `frandom` and add `round` to `REACHABLE_NAMES`.
- Use one inline handler; do not add a helper, adapter, constructor, owner, or transaction path.
- Do **not** use Java `Math.round(double)`: it returns `long` and gives `-2.5 -> -2`, contradicting Toast's half-away-from-zero float result.
- Do **not** use `copySign(floor(abs(x) + 0.5), x)`: adding `0.5` can round `Math.nextDown(0.5)` to `1.0` before `floor`, producing the wrong result.
- Boundary-safe direct formula: take `base = x < 0.0 ? Math.ceil(x) : Math.floor(x)`; take `fraction = x - base`; add `1.0` when `fraction >= 0.5`, subtract `1.0` when `fraction <= -0.5`, otherwise return `base`. This preserves binary64 result type, negative zero, infinities, and NaN in the same no-MOO-error shape as C `round()`.

## Deterministic tests

- Use `assertPureVmContract` for the exact one-`FLOAT` call shape, `ANY`, cost `0`, `PURE`, and `VM`.
- Cover every active math row: `2.3`, `2.7`, `2.5`, `-2.3`, `-2.7`, `-2.5`, and `0.0` with exact expected `FloatValue` results.
- Assert zero and two arguments produce `E_ARGS`; assert integer and string arguments produce `E_TYPE`.
- Add boundary regressions that distinguish the exact algorithm from tempting Java substitutions: `Math.nextDown(0.5) -> 0.0`, `0.5 -> 1.0`, `Math.nextUp(-0.5) -> -0.0`, and `-0.5 -> -1.0`.
- Assert the raw result bits for `round(-0.0)` and `round(Math.nextUp(-0.5))` equal negative zero; `FloatValue.equals` alone cannot prove the sign.
- Assert an already-integral large binary64 value such as `0x1.0p52` is unchanged. No randomized/property test is needed for this fixed scalar contract.

## Blocker

- None.

## Next action

- After the isolated `frandom` slice is committed, implement only this two-file `round` slice and run the parent's exact active gates.
