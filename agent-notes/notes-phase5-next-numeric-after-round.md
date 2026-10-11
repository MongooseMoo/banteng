# Phase 5 next numeric builtin after `round`: `random_bytes`

## Scope

- Read-only future audit; no tracked edits, Gradle/conformance runs, commit, or push.
- This untracked note is the only write.

## Pinned source contract

- Both pinned stock and Mongoose trees register `random_bytes` immediately after `round` as exactly one strict `TYPE_INT` argument.
- The body accepts counts `0..10000` inclusive. Counts below zero or above 10000 raise `E_INVARG` with message `Invalid count` and the supplied count as the raised value.
- Valid calls draw exactly `count` bytes from the process-global Sosemanuk `run_context`, then encode those raw bytes with Toast's binary-string encoding and return `TYPE_STR`.
- The body has no permission check and no committed-world access.

## Entropy and stream ownership

- At server startup, both pinned trees read a 32-byte key from `/dev/urandom` (unless compiled with `-DTEST`, which the managed release profiles are not), schedule Sosemanuk with that key and an empty IV, and consume `sizeof(long)` stream bytes to seed the separate `SRANDOM` facility.
- Managed profiles are 64-bit, so the first 8 Sosemanuk bytes are consumed at startup. `random_bytes` continues from the remaining stream.
- Successive positive-count calls advance one continuous dedicated stream by exactly their requested raw-byte counts. `random`, `reseed_random`, and `frandom` use different state and do not reseed or advance Sosemanuk.
- A zero-count call does not advance Sosemanuk state.

## Exact byte-string encoding

- Raw ASCII graphic bytes `0x21..0x7E` and space `0x20` pass through unchanged except tilde `0x7E`.
- Tilde and every other byte are encoded as three ASCII bytes: `~` followed by two uppercase hexadecimal digits (`~00` through `~FF`).
- Therefore returned string length is between `count` and `3 * count`; the return is printable binary encoding, not a raw arbitrary-byte `StringValue`.
- Banteng's existing `encodeBinary(List<MooValue>)` implements this exact encoding and can encode one `StringValue` containing the raw bytes without a new helper.

## Active YAML

- Generated YAML fixes `function_info("random_bytes") == {"random_bytes", 1, 1, {INT}}`, `E_ARGS` for zero/two arguments, and `E_TYPE` for a non-integer.
- `builtins/math.yaml` fixes `random_bytes(0) == ""`, string result type for count `1`, `E_INVARG` for `-1` and `10001`, and `E_TYPE` for a float.

## Remaining source error edge

- Encoding catches `stream_too_big`: it raises `E_QUOTA` when `$server_options.max_concat_catchable` is enabled, otherwise aborts the task as out of seconds. The compiled default maximum string concatenation is 64,537,861 bytes, above the worst valid encoded length of 30,000; neither pinned fixture contains a textual override. This path is not exercised by active `random_bytes` YAML but remains source-level behavior under a deliberately lower runtime limit.

## Effect and owner

- Permission: `BuiltinPermissionRule.ANY`.
- Tick cost: fixed `0`, matching the current numeric manifest convention.
- Effect: `EffectClass.IRREVOCABLE`. A successful positive-count call consumes non-rewindable cryptographic stream state and must not be repeated after speculative transaction failure.
- Owner: `BuiltinOwner.VM`. The state belongs to the runtime/catalog and never reads or mutates `WorldTxn`; no server, connection, task, or persistence owner participates in a call.

## Smallest direct Java implementation

- Edit only `src/main/java/moo/builtin/BuiltinCatalog.java` and `src/test/java/moo/builtin/BuiltinCatalogTest.java`.
- Add `random_bytes` immediately after `round` in the existing manifest and add its canonical name to `REACHABLE_NAMES`.
- Add one dedicated private-final `SecureRandom` field initialized with the catalog's other VM-owned PRNG state. Do not share `random`, the separate `frandom` state, or reseed it from `reseed_random`.
- Use one inline handler: read the integer count; return structured `Result.raised(E_INVARG, "Invalid count", count)` outside `0..10000`; return the empty `StringValue` directly for zero so no stream method is called; otherwise allocate exactly `count` raw bytes and call the dedicated source's `nextBytes` once.
- Return `encodeBinary(List.of(new StringValue(rawBytes)))`. This reuses the existing exact binary encoder without a new encoding helper or per-byte `MooValue` allocation.
- Do not return `new StringValue(rawBytes)` directly: Toast's result is the printable binary encoding, not raw bytes.
- A JDK `SecureRandom` is the smallest available entropy-backed continuous VM state satisfying the active observable contract. It is not a byte-for-byte Sosemanuk implementation; because Toast's 256-bit startup key is fresh and unavailable, neither active YAML nor a caller can require a fixed byte sequence. If algorithm identity itself is later made a conformance target, that would require a separately authorized Sosemanuk state implementation rather than pretending `SecureRandom` is identical.

## Deterministic tests

- Assert the exact one-`INTEGER` call shape, `ANY`, cost `0`, `IRREVOCABLE`, and `VM`.
- Add `random_bytes` to `REACHABLE_NAMES`.
- Assert zero/two arguments yield `E_ARGS`; float/string arguments yield `E_TYPE`; all fail before the entropy source is touched.
- Assert `-1` and `10001` produce `E_INVARG` plus exact raised details `{"Invalid count", supplied_count, {}}`, and do not consume entropy.
- Reflectively inject a test-only deterministic `SecureRandom` subclass; do not add a production injection API.
- For zero, assert `""` and zero `nextBytes` calls. For one and a normal positive count, assert exactly one call with exactly the requested array length.
- Have the deterministic source emit raw bytes `20 21 7D 7E 00 09 FF`; assert the exact returned Latin-1 bytes spell ` !}~7E~00~09~FF`. This proves pass-through space/graphics, mandatory tilde escaping, nonprintable escaping, uppercase hex, and unsigned high-byte handling.
- Use a cursor-based deterministic source across two positive calls and assert the second starts immediately after the first; insert a zero call between them and assert it does not move the cursor.
- Assert count `10000` is accepted using a deterministic source that fills printable `A`, yielding a 10,000-byte string; do not use uncontrolled randomness or assert that outputs differ.
- Assert `reseed_random`, `random`, and `frandom` do not invoke or reset the random-bytes source, and a `random_bytes` call does not consume their state.

## Blocker

- None for the active-profile/YAML slice. The configurable low-concatenation-limit path and byte-for-byte Sosemanuk algorithm identity are broader authority surfaces not represented in the current catalog contract/YAML.

## Next action

- After the isolated `round` slice is committed, implement only this two-file `random_bytes` slice and run the parent's exact active gates.
