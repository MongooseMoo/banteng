# Phase 5 `reseed_random` read-only audit

## Scope and state

- Read-only future-slice audit requested while another agent owns the tracked worktree.
- Current branch/HEAD observed: `master` at `8a31e5b94eb3386a697a1d2863e1f7677c6a0b34`.
- No tracked file was edited. No Gradle command was run. No commit, push, or sibling-repository mutation was performed.
- Controlling text: `docs/reports/banteng-implementation-plan.md`, Phase 5. The builtin must be added to the existing single `BuiltinSpec` manifest/implementation path; no new dispatcher, adapter, or public production API is authorized.

## Pinned Toast authority

The WSL Toast checkout reports exact HEAD
`aecc51e9449c6e7c95272f0f044b5ba38948459e`, matching the required stock pin.
At that commit, `src/numbers.cc` has:

- registration at line 939:
  `register_function("reseed_random", 0, 0, bf_reseed_random);`
- `bf_reseed_random` at lines 742-750: free the empty arglist; return `E_PERM`
  unless `is_wizard(progr)`; call `reseed_rng()`; return `no_var_pack()`.
- `reseed_rng` at lines 52-60: obtain entropy from `std::random_device`, fill a
  `std::mt19937::state_size` seed array, construct `std::seed_seq`, and reseed the
  same global `std::mt19937 rng` used by `random`.
- `register_numbers` calls `reseed_rng()` at startup before registering `random`
  and `reseed_random`.

Therefore the exact externally visible contract is: no arguments; wizard only;
mutate the shared integer-random generator from fresh host entropy; return integer
zero. It does not accept a caller-provided seed and does not touch committed world
state.

## Exact YAML authority

`../moo-conformance-tests/src/moo_conformance/_tests/generated_builtins/reseed_random.yaml`
requires:

- `function_info("reseed_random") == {"reseed_random", 0, 0, {}}`;
- `reseed_random(1)` raises `E_ARGS` for a wizard;
- a wizard `reseed_random()` returns an `INT` (the Toast body returns the normal
  zero value).

`../moo-conformance-tests/src/moo_conformance/_tests/builtins/math.yaml` adds the
semantic rows:

- programmer `reseed_random()` raises `E_PERM`;
- wizard `reseed_random(); return 1;` returns `1`.

The presence-only row in `builtins/builtin_presence_core.yaml` merely distinguishes
`E_VERBNF` from any registered outcome and is weaker than the two files above.

## Current Banteng random/permission/effect surfaces

- `BuiltinCatalog` owns one `private final java.util.Random random` per catalog and
  initializes it with `new Random()` in both constructor funnels.
- `random` is one manifest entry with 0..2 integer arguments,
  `BuiltinPermissionRule.ANY`, zero ticks, `EffectClass.IRREVOCABLE`,
  `BuiltinOwner.VM`, and handler `randomInteger` consuming that same field.
- `BuiltinCatalog.invoke` validates arity, then argument types, then the manifest
  permission rule before invoking the handler. `WIZARD_ONLY` checks flag bit 4 on
  the programmer object; the existing test world uses object 1 as wizard and
  object 2 as programmer.
- `MooVm` yields every `IRREVOCABLE` builtin as a pending builtin rather than
  executing it speculatively. Thus using the same effect class as `random` keeps
  reseeding on the current ordered irrevocable/publication path and outside
  `WorldTxn`; no new transaction/effect surface is needed.

## Smallest real implementation

Edit only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`:

1. Import `java.security.SecureRandom`.
2. Add `reseed_random` immediately after `random` in the existing manifest with
   exactly one zero-argument `CallShape`, `WIZARD_ONLY`, fixed cost 0,
   `IRREVOCABLE`, `VM`.
3. Use an inline handler (no helper/API): reseed the existing `random` field with
   `new SecureRandom().nextLong()`, then return `Result.zero()`.
4. Add `reseed_random` to `REACHABLE_NAMES`.

`SecureRandom` is the JDK entropy-backed counterpart to Toast's
`std::random_device`. Because Banteng already selected the 48-bit `Random` engine,
one entropy-derived `long` is enough to reset all of that engine's state. Replacing
the field, adding a second PRNG, adding a seed argument, or putting this into
`WorldTxn` would be broader and would not match the current `random` owner/effect
surface.

## Deterministic tests without production API change

Add one focused catalog test that deterministically proves:

- exact zero-argument call shape;
- `WIZARD_ONLY`, tick cost 0, `IRREVOCABLE`, `VM`;
- one argument returns `E_ARGS`;
- programmer 2 with zero arguments returns `E_PERM`;
- wizard 1 with zero arguments returns integer `0`.

Those assertions cover every exact generated/math YAML contract without depending
on random output.

To prove that the handler performs a real reseed rather than merely returning zero,
keep production API unchanged and use a test-only `RecordingRandom extends Random`
whose overridden `setSeed(long)` increments a counter. In the test, reflectively
replace the catalog's ordinary private-final `random` instance with the recorder,
reset the counter after recorder construction, invoke `reseed_random` as wizard,
and assert exactly one `setSeed` call. Do not assert that two random outputs differ:
that is probabilistic and therefore is not a deterministic regression. This narrow
reflection is preferable to adding a production constructor/helper solely for the
test.

## Blocker

None for this bounded future slice. The implementation must remain on the existing
catalog-owned irrevocable PRNG path.

## Next action

When the parent reaches this Phase 5 slice, make only the two-file implementation
and deterministic regression above, then run the exact focused and plan gates under
the parent's active workflow.
