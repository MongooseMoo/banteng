# Phase 5 next numeric builtin after `reseed_random`

## Scope and live state

- Read-only next-step audit; no tracked edits, Gradle/conformance runs, commit, or push.
- Live `master`/`origin/master` HEAD is `29205949d4c1b65f5da476989e96a0cbb7d7cf3b` (`Implement value_bytes builtin`). Tracked worktree is clean; unrelated untracked artifacts are preserved.
- Current `BuiltinCatalog` and `BuiltinCatalogTest.REACHABLE_NAMES` contain `random` but neither `reseed_random` nor `frandom`, matching the requested pre-reseed live state.
- End-of-audit scope check: a concurrent owner has since modified both tracked catalog files for the reseed slice. This audit did not create, inspect, or alter that diff; the facts above describe the requested clean `29205949` pre-reseed snapshot.

## Exact next canonical builtin

- Both pinned stock and Mongoose `numbers.cc` register `frandom` immediately after `reseed_random`, before `round`.
- Registration is `frandom`, minimum arity 1, maximum arity 2, with both argument slots strictly `TYPE_FLOAT`.
- Both implementations are identical: one argument means interval `0.0` to argument 1; two arguments mean argument 1 to argument 2. They compute `u = (double) rand() / RAND_MAX`, then return the float `min + u * (max - min)`.
- Neither pinned source calls `srand`; `frandom` uses the C library `rand()` state, not the `std::mt19937` state used by Toast `random`/`reseed_random`.

## Active generated YAML read so far

- `function_info("frandom")` must report `{"frandom", 1, 2, {FLOAT, FLOAT}}` (type code 9).
- Zero or three arguments produce `E_ARGS`.
- A non-float in either slot produces `E_TYPE`; integer arguments are not promoted by the builtin registration itself.

## Active semantic YAML

- `builtins/math.yaml` requires a one-argument result of type float.
- `frandom(10.0)` must lie in the inclusive range `[0.0, 10.0]`.
- `frandom(5.0, 10.0)` must lie in the inclusive range `[5.0, 10.0]`.
- `frandom(10)` must produce `E_TYPE`.
- `builtin_presence_core.yaml` only proves registration rather than additional semantics.

## Exact contract, errors, effect, and owner

- Manifest call shape: one required `FLOAT`, one optional `FLOAT`, no variadic arguments.
- Permission: `BuiltinPermissionRule.ANY`; the Toast body has no permission check.
- Tick cost: fixed `0`, consistent with the existing numeric VM builtins.
- Effect: `EffectClass.IRREVOCABLE`. Each call consumes mutable pseudo-random state and therefore must not be speculatively replayed after a transaction conflict.
- Owner: `BuiltinOwner.VM`; the builtin reads or mutates no `WorldTxn` state.
- Validation errors are only `E_ARGS` for arity outside 1..2 and `E_TYPE` for a non-float in either supplied position. The body has no explicit `E_INVARG`, `E_FLOAT`, permission, or ordering check.
- One argument uses `minimum = 0.0` and `maximum = arg1`. Two arguments use `minimum = arg1` and `maximum = arg2`. Descending and equal endpoints are accepted. Return `minimum + unit * (maximum - minimum)` as `FloatValue`, where `unit` is in the closed interval `[0.0, 1.0]`.

## Smallest direct implementation

- Edit only `src/main/java/moo/builtin/BuiltinCatalog.java` and `src/test/java/moo/builtin/BuiltinCatalogTest.java`.
- Add `frandom` immediately after `reseed_random` in the existing manifest and add its canonical name to `REACHABLE_NAMES`.
- Use one inline handler; do not add a helper, adapter, constructor, public injection seam, or world path.
- Add a separate private catalog-owned generator field for `frandom`; do **not** reuse the existing `random` field. Derive an endpoint-inclusive unit value from that separate field and apply the pinned interpolation formula.
- The separate state is required semantics, not optional hardening. Toast `random` and `reseed_random` access only `std::mt19937 rng`; Toast `frandom` accesses only libc `rand()`. Sharing one Java field would make integer-random calls advance the float-random sequence and make `reseed_random` reset it, neither of which happens in either pinned Toast source.
- A separate `java.util.Random` fixes the cross-call coupling defect but does not reproduce the pinned Linux libc `rand()` sequence. Active YAML does not assert exact sequence values. If bit-for-bit startup/restart sequence identity is part of the exact-convergence target, the dedicated field must use a pinned libc-`rand`-compatible state machine rather than ordinary Java `Random`; that is a distinct implementation choice the current YAML does not decide.

## Deterministic tests

- Assert the exact call shape, `ANY`, fixed cost `0`, `IRREVOCABLE`, and `VM`.
- Assert zero and three arguments produce `E_ARGS`.
- Assert an integer first argument and a non-float second argument produce `E_TYPE`.
- Use test-only reflection to inject controlled integer and float generators independently; do not add a production injection API.
- Inject a test-only float generator overriding the chosen draw method with controlled values. The minimum draw must map one-argument `10.0` to `0.0`; the maximum draw must map it to `10.0`. The same endpoints must map two-argument `5.0, 10.0` to `5.0` and `10.0`.
- Deterministically cover descending inputs: controlled `0` maps `10.0, 5.0` to `10.0`, and `Integer.MAX_VALUE` maps it to `5.0`.
- Assert equal endpoints always return that endpoint. Do not depend on uncontrolled random output or assert that successive outputs differ.
- Add cross-stream ownership regressions: invoking `random` must not consume the float generator; invoking `frandom` must not consume the integer generator; invoking wizard `reseed_random` must call `setSeed` only on the integer generator and must leave the float generator's seed/draw state untouched.

## Adversarial recheck conclusion

- `reseed_random` does not affect Toast `frandom` in either pinned source. It calls `reseed_rng()`, which seeds only `std::mt19937 rng`; `frandom` calls libc `rand()`. Repository-wide source searches find no `srand()` call in either source tree.
- The independence is externally observable: interleaving `random()` calls cannot advance Toast's `frandom()` stream, and calling `reseed_random()` cannot reset it. A shared Banteng field would change both relationships.
- No active generated, math, or presence YAML combines `random`, `reseed_random`, and `frandom`; current YAML checks signatures, errors, result type, and ranges only. The missing regression does not make shared state conformant to the pinned implementation.
- Exact owner semantics therefore require separate catalog state for `frandom`. The earlier recommendation to reuse `BuiltinCatalog.random` is retracted.

## Complete libc consumer and startup audit

- Complete `git grep` searches over both pinned trees find exactly one compiled-runtime `rand()`/`srand()` consumer: `bf_frandom` in `src/numbers.cc`. All other tree matches are Ruby test-process calls and cannot advance the server process's libc stream. Neither tree contains `srand()`.
- Disassembly of both pinned executables shows exactly one direct `rand@plt` call site, inside `bf_frandom`; there is no direct startup or other-builtin call site.
- The current executable's dynamic dependency graph has one additional `rand` importer: `/lib/x86_64-linux-gnu/librtmp.so.1`. Its disassembled call sites are inside RTMP operational functions such as `RTMP_UpdateBufferMS` and `RTMP_ReadPacket`, not library initialization.
- Toast's `src/curl.cc` restricts `http_request` protocols to `http,https,dict`, so the managed builtin path cannot select RTMP. On the pinned Toast source and supported protocol surface, startup and other builtins therefore do not advance the libc stream; only successful `frandom` body execution does.
- This conclusion is about the pinned Toast executable and supported paths. The libc state is process-global, so a future native dependency path that calls libc `rand()` would share and advance it.

## Exact normalization and current libc authority

- Both pinned `bf_frandom` bodies compute exactly `unit = (double) rand() / RAND_MAX`, followed by `minimum + unit * (maximum - minimum)`. The cast occurs before division. GNU libc's `rand()` result and `RAND_MAX` are integers, with `RAND_MAX == 2147483647`, so both endpoints are representable by the normalization.
- The managed WSL environment currently reports Debian 12 Bookworm and `libc6:amd64 2.36-9+deb12u14` (`glibc 2.36`). Both pinned binaries dynamically link `/lib/x86_64-linux-gnu/libc.so.6`.
- Debian's exact glibc source has `rand()` delegate to `__random()`. Default state is the 128-byte TYPE_3 generator as if initialized with seed `1`, polynomial degree `31`, separation `3`. Each draw adds the rear 32-bit state word into the front word with unsigned 32-bit wrap, returns the sum logically shifted right one bit, and advances both positions around the 31-word ring.
- GNU's manual confirms that calling `rand()` without `srand()` uses default seed `1`, returns `0..RAND_MAX`, and that a seed's sequence is library/machine-specific rather than portable.
- Primary authority: [Debian glibc 2.36 rand.c](https://sources.debian.org/src/glibc/2.36-9%2Bdeb12u14/stdlib/rand.c), [random.c default TYPE_3 state](https://sources.debian.org/src/glibc/2.36-9%2Bdeb12u14/stdlib/random.c), [random_r.c step algorithm](https://sources.debian.org/src/glibc/2.36-9%2Bdeb12u14/stdlib/random_r.c), and [GNU libc ISO random manual](https://sourceware.org/glibc/manual/latest/html_node/ISO-Random.html).

## Smallest exact Java state representation

- To reproduce the current managed Debian/glibc observable sequence, ordinary `java.util.Random` is insufficient. The minimum natural state is 31 signed 32-bit words plus one ring cursor; the other cursor is derived at fixed separation `3`. Initialize with glibc's canonical post-warmup seed-1 state, update with modulo-2^32 addition, return the logical-right-shifted 31-bit result, and divide by exactly `2147483647.0` before interpolation.
- This dedicated state must be separate from Banteng's integer `random` field. `random()` and `reseed_random()` must not read, advance, or reseed it. It resets to the canonical seed-1 state on process/catalog startup and is not committed-world state.
- Current production `MooRuntime` owns one persistent `BuiltinCatalog` per runtime and passes it into VM execution, so catalog-owned state has the required server-lifetime continuity. The package-private convenience VM path constructs a fresh catalog and is not the production runtime path.
- Exact numeric sequence values **are fixed for the current Debian 12 / glibc 2.36-9+deb12u14 environment**, because Toast never calls `srand()` and no supported startup/other-builtin path consumes the stream.
- Exact numeric sequence values are **not durable profile authority**: the stock and Mongoose manifests pin Toast commits and binary checksums and identify Linux/WSL Debian, but pin no Debian release, libc package version, libc checksum, or rand-sequence fixture. A libc update or different C library may legally change the series while preserving `rand()`'s range/default-seed interface.
- Therefore the source/profile-stable contract is independent process-global stream ownership, no `reseed_random` coupling, one draw per successful `frandom`, closed normalization by that runtime's `RAND_MAX`, and the interpolation formula. Treat glibc 2.36 sequence values as a current-oracle snapshot unless the profile is extended to pin libc or active YAML records the sequence.

## Managed-oracle decision

- No existing active YAML or exact managed command selects first-call/cross-call `frandom` sequence values; current suites check signature, errors, type, and range only. Running an invented expression or adding a temporary suite would violate the requested workflow, so no Toast oracle process was launched.

## Blocker

- None.

## Next action

- After the isolated `reseed_random` slice is committed, implement only this two-file `frandom` slice and run the parent's exact active gates.
