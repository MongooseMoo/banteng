# Phase 5 next numeric builtin audit

## Scope and constraints

- Read-only audit for the exact smallest missing numeric builtin slice after `value_bytes`.
- Do not edit tracked files, run Gradle or conformance, commit, or push.
- This untracked note is the only permitted write.

## Current findings

- `AGENTS.md` is not present as a filesystem file at the repository root; the supplied session instructions remain controlling.
- `master` matches `origin/master`; there are no tracked worktree changes. Numerous unrelated untracked notes and artifacts are present and must be preserved.
- Relevant prior memory says Phase 5 must stay one canonical builtin at a time and identifies the earlier sequence through `value_bytes`; it is historical context only and must be re-verified against current source.
- Phase 5 begins at line 544 and Phase 6 begins at line 586 of `docs/reports/banteng-implementation-plan.md`.
- Phase 5 requires the existing single `BuiltinSpec` manifest/implementation path, complete exact contracts and canonical names, and both pinned stock and Mongoose profile gates.
- Current production contains `value_bytes`, `min`, `max`, `abs`, `random`, `time`, `toint`, and `tofloat`; it does not contain `reseed_random` or any later missing numeric-family registration.
- Current `BuiltinCatalogTest.REACHABLE_NAMES` contains the implemented numeric names above and omits `reseed_random`. Existing tests prove `value_bytes`, `min`, `max`, `abs`, and `random` contracts/behavior.
- `profiles/toast/stock-wsl-testdb.json` pins `/root/src/toaststunt` to `aecc51e9449c6e7c95272f0f044b5ba38948459e`; live `git rev-parse HEAD` matches.
- Stock `src/numbers.cc` registers `reseed_random` immediately after `random` as exactly 0 arguments. Its body returns `E_PERM` for a non-wizard, calls `reseed_rng()` for a wizard, and returns the normal zero value. `reseed_rng()` seeds the same global `std::mt19937` used by `random` from `std::random_device` through a full `std::seed_seq`.
- `profiles/toast/mongoose-wsl-mongoose.json` pins `/root/src/toaststunt-mongoose` to `72e3c7f96ce7a41fdeba793aef8818dc4408072e`; live `git rev-parse HEAD` matches.
- Pinned Mongoose `src/numbers.cc` has the same `reseed_random` 0-argument registration and the same wizard check, shared-generator reseed, and zero return as stock.
- Generated YAML requires `function_info("reseed_random") == {"reseed_random", 0, 0, {}}`, `E_ARGS` for one argument, and an integer success result. `builtins/math.yaml` requires `E_PERM` for a programmer and success for a wizard.

## Exact smallest next slice

- Canonical name: `reseed_random`. It is the first missing registration immediately after current `random` in both pinned numeric manifests; `frandom` and later numeric names remain deferred.
- Files: only `src/main/java/moo/builtin/BuiltinCatalog.java` and `src/test/java/moo/builtin/BuiltinCatalogTest.java`.
- Manifest contract: one `CallShape` with no required, optional, or variadic arguments; `BuiltinPermissionRule.WIZARD_ONLY`; fixed tick cost `0`; `EffectClass.IRREVOCABLE`; `BuiltinOwner.VM`.
- Semantics: draw fresh host entropy, call `setSeed(long)` on the catalog's existing `random` field, and return `Result.zero()`. Use an inline handler and `java.security.SecureRandom`; do not add a helper, adapter, constructor, seed argument, second PRNG, or `WorldTxn` path.
- Errors: one or more arguments fail arity validation with `E_ARGS`; zero arguments from a non-wizard fail permission validation with `E_PERM`; there is no argument-type error surface.
- Effect/owner rationale: the operation mutates the same VM-owned generator consumed by `random`, reads host entropy, and changes no committed world state. `IRREVOCABLE` preserves the existing ordered non-speculative path used by `random`.

## Deterministic regressions

- Add `reseed_random` to `REACHABLE_NAMES`.
- Assert the exact empty call shape, `WIZARD_ONLY`, tick cost `0`, `IRREVOCABLE`, and `VM`.
- Assert one argument as wizard returns `E_ARGS`.
- Assert zero arguments as programmer object `2` returns `E_PERM`.
- Assert zero arguments as wizard object `1` returns integer `0`.
- Prove the handler really reseeds without probabilistic output assertions: test-only `RecordingRandom extends Random` counts `setSeed(long)` calls; reflectively replace the catalog's private-final `random`, reset the counter after construction, invoke as wizard, and assert exactly one call. Do not change production API solely for injection.

## Blocker

- None. The mandatory notes checkpoint required this file before further reading.

## Next action

- Parent may implement only this `reseed_random` two-file slice, then run the active workflow's exact focused and Phase 5 gates.
