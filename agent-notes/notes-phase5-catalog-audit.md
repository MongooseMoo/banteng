# Phase 5 catalog audit

Date: 2026-07-21
Mode: read-only audit of production code and tests; no Gradle invocation, commit, or push.

## Controlling requirement

`docs/reports/banteng-implementation-plan.md:544-579` is the controlling Phase 5 text. Its first source requirement is at lines 546-548: complete the single `BuiltinSpec` table, then delete remaining independent dispatch-name/effect-class switches, Toast-absent aliases, and placeholder results. The extraction script and pinned name fixtures follow at lines 550-556; the catalog correspondence/contract/coverage proof follows at lines 558-561.

## First literal incomplete item

The first incomplete item is `docs/reports/banteng-implementation-plan.md:546`: complete the `BuiltinSpec` table.

Evidence:

- `src/main/java/moo/builtin/BuiltinCatalog.java:117-868` constructs the production manifest, currently with 75 `BuiltinSpec` entries, and returns it at line 868.
- `src/test/java/moo/builtin/BuiltinCatalogTest.java:37-113` defines a hand-maintained 75-name `REACHABLE_NAMES` set. Lines 115-131 compare the production manifest only with that local set, check uniqueness, non-empty call shapes, non-negative zero-argument tick charge, and case-insensitive lookup. They do not compare against either pinned Toast profile.
- `profiles/toast/stock-wsl-testdb.json:4-5` pins Toast ref `aecc51e9449c6e7c95272f0f044b5ba38948459e` at `/root/src/toaststunt`. That exact ref registers canonical `abs` at `src/numbers.cc:937` as one `TYPE_NUMERIC` argument, backed by `bf_abs` at `src/numbers.cc:513-526`.
- Neither `src/main/java/moo/builtin/BuiltinCatalog.java` nor `src/test/java/moo/builtin/BuiltinCatalogTest.java` contains an `abs` entry. Thus the current manifest and its self-referential test omit at least one canonical builtin from the pinned stock profile.
- The Phase 5 extraction script and fixtures named at plan lines 550-556 are absent: there is no `scripts/extract_toast_builtin_names_wsl.sh`, `src/test/resources/moo/builtin/toast-builtins-stock.txt`, or `src/test/resources/moo/builtin/toast-builtins-mongoose.txt`.

## Other catalog obligations (later than the first incomplete item)

- Independent builtin-name/effect switches: no independent builtin-name dispatch switch was found. `src/main/java/moo/vm/MooVm.java:1143-1176` resolves a `BuiltinSpec` from `BuiltinCatalog`, consults `spec.effect()` for the irrevocable boundary, and invokes the spec handler. `BuiltinCatalog.java:1036-1053` builds the unique name index directly from the manifest.
- Toast-absent aliases: no proven Toast-absent alias was identified in the current manifest during this audit. For example, the potentially suspicious `eval` entry at `BuiltinCatalog.java:777-784` is registered by the exact pinned stock Toast ref at `src/verbs.cc:678`.
- Placeholder results: `BuiltinCatalog.java:813-820` gives `load_server_options` an unconditional `Result.zero()` handler, and lines 822-829 do the same for `run_gc`. These are direct placeholder candidates to remove after catalog coverage reaches the plan's required completeness; this audit did not reinterpret valid zero-valued implementations elsewhere as placeholders.
- One-to-one mappings/contracts: each current `BuiltinSpec` structurally embeds one non-null implementation (`BuiltinSpec.java:8-29`), and duplicate names fail during indexing (`BuiltinCatalog.java:1036-1043`). The current test does not prove pinned-profile canonical coverage, and its loop at `BuiltinCatalogTest.java:127-131` does not independently assert every contract field named by the plan; constructor non-null checks provide only an internal structural guard.

## Smallest next source slice

Stay on the first incomplete Phase 5 item and add exactly one canonical missing builtin: `abs`.

The slice should add one `BuiltinSpec` named `abs` to `BuiltinCatalog`, with one required `NUMBER` argument, `BuiltinPermissionRule.ANY`, the existing fixed zero tick rule, `EffectClass.PURE`, `BuiltinOwner.VM`, and a real integer/float absolute-value handler matching pinned Toast `bf_abs`. Add `abs` to the catalog name expectation and add exact contract plus integer/float behavior tests in `BuiltinCatalogTest`. Do not start the extraction-script/fixture step until the complete-manifest requirement at line 546 is actually satisfied.

## Kept slice result

- Added the canonical pure-VM `abs(NUMBER)` manifest entry and real integer/float implementation without adding a second dispatch path.
- The focused behavior test failed before the implementation with a missing spec and passed afterward.
- The complete `BuiltinCatalogTest` class passed.
- `git diff --check` passed before commit.
- Next action: commit and push this slice, reread Phase 5, then continue the still-incomplete manifest rather than advancing to extraction fixtures.
