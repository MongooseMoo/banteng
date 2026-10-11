# Phase 5 next missing numeric builtin after `random_bytes`: `ctime`

## Scope

- Read-only future audit; no tracked edits, Gradle/conformance runs, commit, or push.
- This untracked note is the only write.

## Exact registration position and contract

- Both pinned trees have the sequence `round`, `random_bytes`, `time`, `ctime`, `ftime`.
- Therefore `ctime` is the next missing numeric registration after the future `random_bytes` slice because `time` is already implemented; it is not literally adjacent to `random_bytes`. Add it immediately after the existing `time` entry.
- Registration is `register_function("ctime", 0, 1, bf_ctime, TYPE_INT)`: zero or one integer argument, no permission check, and a string on success.
- Wrong arity is `E_ARGS`; a supplied non-integer is `E_TYPE` before the body runs.
- Both checked-out source HEADs match their manifests: stock `aecc51e9449c6e7c95272f0f044b5ba38948459e` and Mongoose `72e3c7f96ce7a41fdeba793aef8818dc4408072e`.

## Pinned body semantics

- With no argument, the body samples `time(nullptr)` once and formats that epoch second.
- With an argument, it computes `min(argument, 2147483647 * 31536000)`. The exact upper clamp is `67723044291792000` seconds. Values above it do not raise merely for being above it; they all pass the same clamped value to `localtime`.
- There is no lower clamp. Negative timestamps are accepted when the platform `localtime` can represent them.
- `localtime` converts in the process-local timezone. A null result returns `E_INVARG`.
- `strftime` uses the exact format `%a %b %d %H:%M:%S %Y %Z` in a 128-byte buffer. A zero return, including insufficient buffer space, returns `E_INVARG`.
- If the day-of-month tens position is `0`, the body changes that byte to a space. A single-digit day therefore looks like `Jan  1`, not `Jan 01`.
- The successful result has no newline and includes the local timezone abbreviation/name after the year, for example `Thu Jan  1 00:00:00 1970 UTC` under UTC.
- Neither pinned tree calls `setlocale`, so C starts and remains in the C locale: weekday and month abbreviations are English. Timezone is different: both `localtime` and `%Z` use the process-local zone.
- The two pinned implementations are identical on all of these points.

## Managed timezone authority

- Neither `scripts/run_toast_wsl.sh` nor `scripts/run_banteng_wsl.sh` sets `TZ`, `LC_TIME`, `LANG`, or a Java `user.timezone` property. Both targets inherit the same WSL process environment.
- At this audit the WSL localtime file resolves to `America/Denver`, `date` reports `MDT -0600`, and the shell locale is `en_US.UTF-8`. Those are current environment observations, not manifest-pinned guarantees.
- Implementation must therefore use the JVM process-local zone (`ZoneId.systemDefault()`), not UTC and not the Windows host zone. Hard-coding `America/Denver`, `MST`, or `MDT` would be wrong.
- Formatting should use `Locale.US` to reproduce Toast's unchanged C-locale English day/month names rather than Java's mutable default formatting locale.

## Active YAML authority

- `generated_builtins/ctime.yaml` fixes `function_info("ctime") == {"ctime", 0, 1, {INT}}`, `E_ARGS` for two arguments, and `E_TYPE` for a string argument.
- `builtins/math.yaml` requires `ctime()` to return a string matching its date prefix, requires `ctime(time())` to return a string, and checks `E_TYPE` for float and string arguments.
- `audit/time_server_builtins_toast_oracle.yaml` requires the complete time-of-day/year/timezone-token shape and requires `ctime(2147483647)` to succeed. Its test name says `extreme_timestamp_rejected`, but its description and expected regex require acceptance; the name is stale, not the oracle outcome.
- Active YAML does not fix an exact zone, an exact current-time string, the upper-clamp behavior, a negative timestamp, or a timestamp outside Java's `Instant` range.

## Errors and invalid-time boundary

- The body itself produces only `E_INVARG`, and only when `localtime` returns null or `strftime` returns zero. The exact representable timestamp range is consequently a property of the pinned 64-bit Linux C runtime, not a numeric range stated by the builtin.
- The explicit upper clamp exists to keep the computed calendar year within the C `tm_year` integer field. It is not an instruction to reject timestamps above the clamp.
- A direct Java implementation must catch `DateTimeException` and map it to `E_INVARG`; it must not leak a host exception.
- Important mismatch: Java `Instant`/`ZonedDateTime` supports roughly plus or minus one billion years, while Toast clamps positive input near 2.147 billion 365-day years before calling the 64-bit Linux `localtime`. Thus `Instant.ofEpochSecond` is exact for active YAML and ordinary timestamps but is not proof of full pinned-source equivalence for all 64-bit integers. No live extreme-time probe was run because this task forbids tests.

## Effect and owner

- Permission: `BuiltinPermissionRule.ANY`.
- Tick cost: fixed `0`, matching the current numeric manifest convention.
- Effect: `EffectClass.IRREVOCABLE`. The zero-argument shape reads the non-repeatable wall clock, and one manifest entry must conservatively classify both shapes. The explicit-argument shape alone is deterministic for a fixed process timezone, but cannot receive a separate effect.
- Owner: `BuiltinOwner.VM`. Formatting uses runtime clock/timezone state and never reads or mutates `WorldTxn`; no world, server, connection, task, or persistence owner participates.

## Smallest active-YAML Java implementation

- Edit only `src/main/java/moo/builtin/BuiltinCatalog.java` and `src/test/java/moo/builtin/BuiltinCatalogTest.java`.
- Add `ctime` immediately after the existing `time` manifest entry and add its canonical name to `REACHABLE_NAMES`.
- Use one inline handler; do not add a clock interface, formatter helper, sender, adapter, or new constructor surface.
- Select seconds once: `Instant.now().getEpochSecond()` for zero arguments, otherwise the supplied integer clamped with `Math.min(value, 67723044291792000L)`.
- Convert with `Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault())` and format with a `DateTimeFormatter` pattern `EEE MMM dd HH:mm:ss uuuu z` and `Locale.US`. Use proleptic-year `uuuu`, not year-of-era `yyyy`, so representable pre-AD values are not silently relabeled.
- Replace a `0` at string index 8 with a space, encode the result as ISO-8859-1 `StringValue` bytes, and return it.
- Catch `DateTimeException` and return `Result.error(E_INVARG)`.
- This is the smallest implementation satisfying every active YAML case. It must be described honestly as limited by Java's date range; do not claim exact all-`long` parity without resolving the extreme-range issue above.

## Deterministic isolated tests

- Assert the exact zero-required/one-optional-`INTEGER` call shape, `ANY`, cost `0`, `IRREVOCABLE`, and `VM`; add `ctime` to `REACHABLE_NAMES`.
- Assert `function_info("ctime")` is exactly `{"ctime", 0, 1, {0}}`.
- Assert two arguments yield `E_ARGS`, and float/string arguments yield `E_TYPE`.
- Save `TimeZone.getDefault()`, set the default to UTC inside `try`, and restore it in `finally`. The Gradle test task has one fork and JUnit method parallelism is not enabled; restoration is still mandatory because default timezone is process-global.
- Under UTC, assert exact explicit results: `ctime(0) == "Thu Jan  1 00:00:00 1970 UTC"`, `ctime(-1) == "Wed Dec 31 23:59:59 1969 UTC"`, and `ctime(2147483647) == "Tue Jan 19 03:14:07 2038 UTC"`. These prove epoch conversion, negative acceptance, C-style day padding, English abbreviations, time-of-day, year, and timezone suffix without reading the clock.
- Assert a zero-argument call returns a string matching the complete active YAML shape while UTC is installed. Do not assert an exact current timestamp and do not add production clock injection solely for this test.
- Assert `Long.MAX_VALUE` and `67723044291792000L` produce identical outcomes after the required upper clamp. With the smallest `java.time` implementation both are `E_INVARG`; this proves clamp ordering but not pinned-glibc full-range parity.
- If full source parity is authorized later, first add pinned-oracle boundary cases around Java's maximum `Instant` second and Toast's clamp. Those oracle cases, not a locally chosen range, must decide whether a broader calendar/timezone implementation is required.

## Blocker

- None for the active-YAML two-file slice.
- Full 64-bit pinned-source equivalence is not established: the source delegates invalidity to 64-bit Linux `localtime`, while the smallest Java implementation rejects timestamps outside the narrower `Instant` range. Resolving that requires an explicitly authorized pinned-oracle boundary audit before claiming exact all-input parity.

## Next action

- After the isolated `random_bytes` slice is committed, implement only this two-file `ctime` slice after the existing `time` entry and run the parent's exact active gates.
