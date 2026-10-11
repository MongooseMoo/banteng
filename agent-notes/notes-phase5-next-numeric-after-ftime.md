# Phase 5 next numeric builtin after `ftime`: `floatstr`

## Scope and current state

- Read-only future audit; no tracked edits, tests, commits, or push.
- This untracked note is the only write.
- Both pinned source bodies, every active YAML occurrence, the pinned compiler precision macro, glibc symbols/locale constants, and Java 25 formatter behavior have been checked. The exact implementation decision is resolved below.

## Verified pinned contract

- Stock `/root/src/toaststunt/src/numbers.cc:838-859` and Mongoose `/root/src/toaststunt-mongoose/src/numbers.cc:1193-1214` are identical.
- Both register `floatstr` immediately after `ftime` as two required arguments and one optional argument: `TYPE_FLOAT`, `TYPE_INT`, `TYPE_ANY`.
- The body forms either `%.<precision>f` or `%.<precision>e`, calls C `sprintf`, and returns the resulting string.
- The third argument is optional. When absent it selects fixed notation. When present, general Toast `is_true` truthiness selects scientific notation; false selects fixed notation.
- Precision is first narrowed from the 64-bit MOO `Num` to C `int`, then capped at the build's `__DECIMAL_DIG__`, and only then checked for negativity.
- Both pinned builds use `/usr/bin/c++`, whose current `__DECIMAL_DIG__` is exactly `21`.
- A narrowed precision above `21` becomes `21`; a narrowed negative precision returns `E_INVARG`.
- Wrong arity returns `E_ARGS`; a non-float first argument or non-integer second argument returns `E_TYPE`. The `TYPE_ANY` third argument never causes a type error.

## Verified flag truthiness

- True: nonzero integers; nonzero floats (including NaN); nonempty strings; nonempty lists; nonempty maps; boolean true.
- False: zero integers; positive or negative float zero; empty strings/lists/maps; boolean false; objects; errors; anonymous objects; waifs and other unhandled types.
- Banteng's existing `MooValue.isTruthy()` definitions match this Toast truth table for represented value types, so the handler should use the existing method directly.

## Verified active YAML

- Generated YAML fixes `function_info("floatstr") == {"floatstr", 2, 3, {FLOAT, INT, ANY}}`, too-few/too-many `E_ARGS`, and first/second argument `E_TYPE`.
- `builtins/math.yaml` requires: `floatstr(3.14159, 2) == "3.14"`; precision zero returns `"3"`; precision ten returns `"3.1415900000"`; truthy third argument returns `"3.14e+00"`; precision `-1` is `E_INVARG`.
- `builtins/floatstr_call_shapes.yaml` proves the third argument accepts float, object, string, error, list, and map values, but checks only result type rather than the truthy/falsy notation selected.
- Active YAML does not cover precision cap `21`, 64-to-32-bit precision narrowing, false third arguments, negative zero, exponent-width edges, infinity, NaN, or exact rounding boundaries.

## Exact format and precision semantics

- False/omitted third argument uses C `%f`: ordinary fixed decimal with exactly `precision` digits after the decimal point. At precision zero there is no decimal point.
- Truthy third argument uses lowercase C `%e`: one digit before the decimal point, exactly `precision` fractional digits, lowercase `e`, an explicit exponent sign, and at least two exponent digits.
- Default C-locale punctuation is used: ASCII digits, `.` decimal separator, no grouping, and no localized signs.
- The sign of negative zero is preserved: examples are `-0.00` and `-0.00e+00` at precision two.
- Finite results follow pinned glibc `printf` rounding under the process's unchanged default `FE_TONEAREST` mode, including ties-to-even. For example, precision-zero fixed formatting yields `2` for `2.5`, `4` for `3.5`, and `-2` for `-2.5`.
- Non-finite values are successful strings, not errors: lowercase `nan`, `inf`, and `-inf` for the normal glibc cases. Precision and fixed/scientific selection do not add digits or an exponent to them.
- The fixed worst case is a negative maximum finite double: sign, 309 integer digits, decimal point, and at most 21 fractional digits, plus the terminator. The source's 500-byte buffer is therefore sufficient; no stream/quota error path exists.

## 64-bit precision narrowing

- The source assignment to C `int` occurs before either validation or capping. On the pinned GCC/x86-64 builds, out-of-range signed values narrow to the low 32 bits interpreted as two's-complement signed `int`.
- Java `(int) longValue` has exactly that pinned behavior and must occur first.
- Consequences: `22` caps to `21`; `4294967296` narrows to `0`; `4294967317` narrows to `21`; `2147483648` narrows to `-2147483648` and raises `E_INVARG`; `Long.MAX_VALUE` narrows to `-1` and raises `E_INVARG`.
- Do not validate the original 64-bit value and do not use `Math.toIntExact`; either changes pinned behavior.

## Why Java `Formatter` is not exact

- Java 25 specifies `HALF_UP` rounding for `%f` and `%e` floating conversion. Pinned glibc uses the active floating-point rounding mode, which remains default ties-to-even in both sources. The `2.5`/precision-zero case already distinguishes them.
- Java emits `NaN`, `Infinity`, and `-Infinity`; pinned lowercase C conversions emit `nan`, `inf`, and `-inf`.
- Post-processing Java output would fix casing but not the finite midpoint rule. A hand-built exact-binary `BigDecimal` formatter could reproduce it, but would add substantial exponent/normalization/special-value code that duplicates the pinned libc authority.

## Effect and owner

- Permission: `BuiltinPermissionRule.ANY`.
- Tick cost: fixed `0`, matching the current numeric manifest convention.
- Effect: `EffectClass.PURE`. With the C numeric locale fixed per call, output is determined only by the three arguments; the native call reads no changing external state and the temporary thread-local locale is restored.
- Owner: `BuiltinOwner.VM`. Formatting has no world, server, task, connection, persistence, clock, or I/O owner and never touches `WorldTxn`.

## Smallest exact Java implementation

- Assuming the immediately preceding exact `ftime` slice has already established Java 25 FFM and `--enable-native-access=ALL-UNNAMED`, this returns to the normal two-file slice: `BuiltinCatalog.java` and `BuiltinCatalogTest.java`. Do not add another public interface, adapter, constructor, dependency, or source file.
- Register `floatstr` immediately after `ftime` and add it to `REACHABLE_NAMES` with required `{FLOAT, INTEGER}`, optional `{ANY}`, `ANY`, fixed `0`, `PURE`, and `VM`.
- Reuse the existing native linker boundary from `ftime`; add cached downcall handles for glibc `snprintf`, `newlocale`, and `uselocale`. The pinned glibc exports all three.
- Create one process-lifetime C numeric locale with `newlocale(LC_NUMERIC_MASK, "C", NULL)`. The pinned glibc constant is `LC_NUMERIC_MASK == 2`. A null result is an unsupported-runtime startup failure, not a MOO error.
- In the inline handler, narrow precision with `(int) suppliedLong`, cap values above `21`, and return `E_INVARG` for narrowed values below zero. Use `arguments.get(2).isTruthy()` only when the optional argument is present.
- Build the tiny ASCII format string `%.<precision>f` or `%.<precision>e`.
- Allocate a 500-byte confined native output segment. Call `uselocale(cNumericLocale)`, invoke variadic `snprintf(output, 500, format, doubleValue)` with `Linker.Option.firstVariadicArg(3)`, and restore the prior locale in `finally`. `uselocale` is thread-local, so concurrent builtin calls do not mutate global locale state.
- Read the NUL-terminated ASCII result and return it as an ISO-8859-1 `StringValue`.
- A negative `snprintf` result or an impossible length at least 500 is an internal/profile failure. Do not invent a MOO error absent from the pinned body.
- Do not call plain process-locale `snprintf`: JVM native startup may adopt the environment locale, whereas Toast never calls `setlocale` and therefore formats in the C locale.
- Do not use `String.format`, `DecimalFormat`, or `BigDecimal` under a claim of exact pinned equivalence.

## Deterministic isolated tests

- Assert the exact required `{FLOAT, INTEGER}` plus optional `{ANY}` shape, `ANY`, cost `0`, `PURE`, `VM`, `REACHABLE_NAMES`, and exact `function_info("floatstr") == {"floatstr", 2, 3, {9, 0, -1}}`.
- Assert zero/one/four arguments yield `E_ARGS`; integer first argument and float/string second arguments yield `E_TYPE`. Assert representative third arguments of every value family are accepted.
- Preserve the active exact rows: `3.14159,2 -> "3.14"`; precision zero -> `"3"`; precision ten -> `"3.1415900000"`; truthy flag -> `"3.14e+00"`; precision `-1` -> `E_INVARG`.
- Prove truthiness selection, not merely acceptance: false values `0`, `0.0`, `-0.0`, `""`, `{}`, empty map, `#0`, and `E_ARGS` use fixed notation; true values `1`, `-1`, `1.5`, `"x"`, `{1}`, and a nonempty map use scientific notation.
- Prove the cap with `floatstr(1.0, 21) == "1.000000000000000000000"` and equality of precision `21` and `22` outcomes.
- Prove narrowing order: precision `4294967296` behaves as zero; `4294967317` behaves as 21; `2147483648` and `Long.MAX_VALUE` return `E_INVARG`.
- Prove ties-to-even independently of Java Formatter: `floatstr(2.5,0) == "2"`, `floatstr(3.5,0) == "4"`, and `floatstr(-2.5,0) == "-2"`.
- Prove signed zero: fixed `floatstr(-0.0,2) == "-0.00"`; scientific truthy flag returns `"-0.00e+00"`.
- Prove scientific exponent shape with exact powers such as `1.0e100 -> "1.00e+100"` and `1.0e-100 -> "1.00e-100"` at precision two.
- Directly construct `FloatValue` instances for positive/negative infinity and NaN; assert pinned lowercase `inf`, `-inf`, and `nan` for both format modes.
- Temporarily change Java's default locale to a comma-decimal locale and restore it in `finally`; assert output still uses `.`. This proves Java locale is irrelevant. The C-locale handle itself is fixed by construction, so do not mutate process-global C locale in tests.
- Do not add probabilistic, clock-based, or tolerance assertions; every expected result is an exact byte string or exact structured error.

## Blocker

- None after the exact `ftime` native-access prerequisite is committed.
- If `ftime` is implemented without the FFM/native-access boundary, `floatstr` cannot remain an exact two-file slice. Stop for authority rather than substitute Java `Formatter` and conceal its known rounding/special-value differences.

## Next action

- After the isolated exact `ftime` slice is committed, implement only this two-file `floatstr` slice and run the parent's exact active gates.
