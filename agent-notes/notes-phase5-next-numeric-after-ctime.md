# Phase 5 next numeric builtin after `ctime`: `ftime`

## Scope

- Read-only future audit; no tracked edits, Gradle/conformance runs, commit, or push.
- This untracked note is the only write.
- The existing tracked `BuiltinCatalog.java` modification was already present and was not touched by this audit.

## Pinned source authority

- Stock `/root/src/toaststunt/src/numbers.cc:685-721` and Mongoose `/root/src/toaststunt-mongoose/src/numbers.cc:1040-1076` have identical `bf_ftime` bodies.
- Both register `register_function("ftime", 0, 1, bf_ftime, TYPE_INT)` immediately after `ctime` and before `floatstr` (stock line 945; Mongoose line 1300).
- Contract: zero or one integer argument, `TYPE_FLOAT` success, no permission check, and no committed-world access.
- Wrong arity is dispatcher `E_ARGS`; a supplied non-integer is dispatcher `E_TYPE` before the body runs.
- The body raises no MOO error.

## Exact Linux clock-selection semantics

- No argument selects `CLOCK_REALTIME`.
- Any supplied integer changes the call to a monotonic clock selector; the integer is not a timestamp.
- Exactly `2` selects `CLOCK_MONOTONIC_RAW`.
- Every other supplied integer selects `CLOCK_MONOTONIC`, including `0`, `1`, negative values, values above `2`, `Long.MIN_VALUE`, and `Long.MAX_VALUE`. There is no `E_INVARG` selector range.
- In the pinned x86-64 WSL headers the exact native IDs are realtime `0`, monotonic `1`, and monotonic-raw `4`.
- `CLOCK_REALTIME` is Unix-epoch wall time and can move when the wall clock is corrected.
- `CLOCK_MONOTONIC` is a non-settable monotonic Linux clock; it is not a Unix timestamp and is subject to the kernel's frequency adjustments.
- `CLOCK_MONOTONIC_RAW` is the raw hardware-based monotonic clock without those NTP frequency adjustments. Selector `2` must therefore remain observably distinct from selector `1`; mapping both to one Java clock is not exact.

## Local time and formatting language

- `ftime` never calls `localtime`, `strftime`, or any string formatter.
- It returns a number of seconds, not a formatted date. Process timezone, `TZ`, locale, `LC_TIME`, weekday/month language, DST names, and UTC offsets have no effect.
- This differs from adjacent `ctime`; no timezone or locale implementation should be shared with `ftime`.

## Native range and floating-point semantics

- On the pinned 64-bit Linux ABI, `timespec.tv_sec` and `timespec.tv_nsec` are signed 64-bit C `long` fields. For successful `clock_gettime`, nanoseconds are in `0..999999999`.
- The exact returned expression is `(double) tv_sec + (double) tv_nsec / 1000000000.0`.
- The result intentionally has binary64 precision, not an exact nanosecond integer. At current epoch magnitudes some low nanosecond bits are rounded away; Banteng must preserve that same cast/divide/add order rather than promise nanosecond-exact output.
- Java `double` can represent the same finite binary64 result, and modern Java's always-strict floating-point evaluation preserves the source operation order when written directly.
- The source ignores the `clock_gettime` return code. The three fixed clock IDs are supported by both pinned Linux profiles, so the defined profile outcome is a float. A missing native symbol or failed call in Banteng is a deployment/profile failure, not authority to invent `E_INVARG` or another MOO error.

## Active YAML

- `generated_builtins/ftime.yaml` fixes `function_info("ftime") == {"ftime", 0, 1, {INT}}`, `E_ARGS` for two arguments, `E_TYPE` for a string, and float success for `ftime()`.
- `builtins/math.yaml` requires `ftime()` to be a positive float, `ftime(1)` to be a float, and `E_TYPE` for a float argument.
- `audit/time_server_builtins_toast_oracle.yaml` proves both `ftime(1)` and `ftime(2)` return floats, with the monotonic value positive and the raw value nonnegative, and explicitly identifies the arguments as clock selectors rather than timestamp formatters.
- Active YAML does not distinguish the adjustment behavior or origins of selectors `1` and `2`, does not cover `ftime(0)` versus `ftime()`, and does not cover arbitrary integer selectors. Those omissions do not weaken the pinned source contract above.

## Java representability decision

- `Instant.now()` can model the realtime clock domain but does not provide the other two native clock domains.
- `System.currentTimeMillis()` is additionally too coarse because it discards the source's sub-millisecond clock result.
- `System.nanoTime()` is only specified as an arbitrary-origin elapsed-time source. Its absolute value is exposed by `ftime`, so origin is observable; it also has no standard Java raw-monotonic companion.
- Therefore a pure `Instant.now()`/`System.nanoTime()` implementation is not exact. In particular, using `System.nanoTime()` for both `ftime(1)` and `ftime(2)` silently deletes a source-visible clock distinction.
- Java 25's standard Foreign Function and Memory API can call glibc `clock_gettime` directly. The pinned glibc exports `clock_gettime@@GLIBC_2.17`, so no JNI library or third-party dependency is needed.
- Java 25 classifies linker downcalls as restricted native access. The local Java 25 runtime defaults illegal native access to `warn`; production should explicitly pass `--enable-native-access=ALL-UNNAMED` rather than emitting a warning or relying on a temporary default.

## Effect and owner

- Permission: `BuiltinPermissionRule.ANY`.
- Tick cost: fixed `0`, matching the existing numeric manifest convention.
- Effect: `EffectClass.IRREVOCABLE`. Every shape samples external, non-rewindable clock state and must not be repeated after a speculative transaction failure.
- Owner: `BuiltinOwner.VM`. The runtime clock is VM/host state and the handler never reads or mutates `WorldTxn`; no world, server, task, connection, or persistence owner participates.

## Smallest exact implementation

- This is not honestly a two-file slice. Exact pinned-Linux behavior requires four tracked files: `BuiltinCatalog.java`, `BuiltinCatalogTest.java`, `build.gradle.kts`, and `scripts/run_banteng_wsl.sh`.
- In `BuiltinCatalog`, register `ftime` immediately after `ctime`, add it to `REACHABLE_NAMES`, and add one private native clock-sample boundary. Do not add a public clock interface, sender, adapter, or constructor surface.
- The smallest deterministic seam is a private final `IntFunction<long[]>` field whose production value calls `clock_gettime` and returns exactly `{tv_sec, tv_nsec}`. Tests can replace that private field reflectively, following the catalog's existing private-final PRNG test pattern. This seam is justified by the required deterministic selector/conversion tests; a broader time abstraction is not.
- Cache one FFM downcall handle for `int clock_gettime(int, struct timespec *)`, resolved from `Linker.nativeLinker().defaultLookup()`. Use native C `int` for the clock ID and a two-C-`long` `timespec` layout; allocate one confined native segment per call.
- Map no arguments to native ID `0`; map a supplied value equal to `2` to ID `4`; map every other supplied integer to ID `1`. Do the equality test in MOO's 64-bit integer domain before choosing the native `int` constant.
- Invoke the native reader exactly once, then return `new FloatValue((double) seconds + (double) nanos / 1_000_000_000.0)`.
- Treat symbol-resolution or nonzero native-call status as an unsupported-runtime/internal startup failure. Do not convert it into a MOO error absent from both pinned bodies, and do not fall back to `System.nanoTime()` while claiming exactness.
- In `build.gradle.kts`, add `--enable-native-access=ALL-UNNAMED` to application default JVM arguments and to the ordinary `Test` JVMs. The existing flag applies only to the `jcstress` task and does not cover catalog tests or production launch.
- In `scripts/run_banteng_wsl.sh`, add the same flag to its direct `/opt/java/25/bin/java` invocation; that launcher bypasses the generated application script and therefore does not inherit application default JVM arguments.
- This implementation is exact for the two pinned 64-bit Linux profiles. A future non-Linux production profile needs separately sourced native clock semantics; it must not receive a quiet Java-clock fallback under this slice.

## Deterministic isolated tests

- Assert the exact zero-required/one-optional-`INTEGER` shape, `ANY`, cost `0`, `IRREVOCABLE`, and `VM`; add `ftime` to `REACHABLE_NAMES`.
- Assert `function_info("ftime")` is exactly `{"ftime", 0, 1, {0}}`.
- Reflectively inject a recording `IntFunction<long[]>`; do not add a production test constructor.
- Return `{123, 500000000}` for ID `0`, `{7, 250000000}` for ID `1`, and `{9, 125000000}` for ID `4`. Assert exact results `123.5`, `7.25`, and `9.125`; these fractions are binary-exact and make the test independent of wall time and scheduling.
- Assert `ftime()` calls ID `0` exactly once.
- Assert each of `ftime(0)`, `ftime(1)`, `ftime(-1)`, `ftime(3)`, `ftime(Long.MIN_VALUE)`, and `ftime(Long.MAX_VALUE)` calls ID `1` exactly once.
- Assert only `ftime(2)` calls ID `4` exactly once.
- Assert two arguments yield `E_ARGS` and float/string arguments yield `E_TYPE`, with zero native-reader calls for every rejected invocation.
- Add a direct deterministic conversion case such as `{1700000000, 123456789}` and assert the Java expression's exact binary64 result, ensuring no millisecond truncation or integer division.
- Do not use sleeps, elapsed-time tolerances, current-time bounds, or assertions that two live samples differ. The active managed Toast/Banteng runs provide the native-boundary smoke proof; unit tests should prove selection and conversion with injected samples.

## Blocker

- There is no semantic blocker to an exact pinned-Linux implementation because Java 25 FFM can call the exact clocks.
- There is a scope prerequisite: implementing `ftime` exactly requires the explicitly named build and launcher native-access changes in addition to the normal catalog/test files. If the active slice is constrained to only two tracked files, stop rather than substitute Java clocks; such a slice cannot be exact.

## Next action

- After the isolated `ctime` slice is committed, authorize the four-file native-access boundary above, then implement only `ftime` and run the parent's exact active gates.
