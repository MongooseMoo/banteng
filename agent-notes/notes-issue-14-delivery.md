# Issue 14 delivery notes

## State

- Isolated worktree: `C:\Users\Q\code\banteng-agent-issue-14`
- Branch: `agent/issue-14-worldtxn-decomposition`
- Exact base: `c278f236e5c619dfc7d46540bf14fe8fa71db8fa`
- Live issue: `#14 Decompose WorldTxn: extract ConnectionRegistry and PropertyLayoutEngine, unify object/anonymous property handling, delete dead methods`
- No local Gradle, build, test, or OpenRewrite execution. Nothing pushed or published.

## Architecture findings

- Current `WorldTxn` still owns three live connection maps and every connection lookup/mutation method.
- `MooRuntime` already owns attempt-local copied `ConnectionState` maps, but seeds their player/info/intrinsic subset into each `WorldTxn` and reads it back after execution. `ConnectionRegistry` should replace that seed/readback path, not become another world field.
- Static connection builtins currently call `WorldTxn`; production can route them through an attempt-local registry supplier owned by `MooRuntime`/`BuiltinCatalog` without changing the VM's generic builtin signature.
- Property layout algorithms occupy the end of `WorldTxn`; the anonymous-layout wrapper mutates transaction storage, while its graph/layout calculations are pure and can move to `PropertyLayoutEngine`.
- `WorldObject` and `WorldAnonymousObject` expose the six fields named by the issue and can implement one sealed `PropertyHolder` contract with covariant replacement methods.
- Current rewrite infrastructure is the single `rewrite-recipes` subproject plus declarative `rewrite.yml`; CI `check` runs its executable recipe tests.

## RED commit

- `47e1ab8 test: define WorldTxn decomposition boundaries`
- Adds `ConnectionRegistryTest`, `WorldTxnDecompositionTest`, and `DecomposeWorldTxnTest` only.
- Contracts require no connection/dead methods or fields in `WorldTxn`, static layout ownership in `PropertyLayoutEngine`, sealed holder ownership, at most two public `WorldTxn` constructors, attempt-local registry copy behavior, and executable dead-delete/static-move recipe proof.
- The commit was created without running it locally, per instruction.

## Next action

1. Add `moo.server.ConnectionRegistry` and route runtime attempt state plus connection builtins through it.
2. Remove the connection surface and dead methods from `WorldTxn`.
3. Add `PropertyLayoutEngine`, move pure graph/layout logic, and leave only transaction write-back orchestration in `WorldTxn`.
4. Add `PropertyHolder`, consolidate mirrored operations, reduce constructors/callers.
5. Implement and wire the OpenRewrite recipe/proof, statically audit, then send the exact PR packet before any push.

## 2026-08-18 implementation checkpoint

- Worktree remains clean at RED commit `47e1ab8`; no production commit exists yet.
- The exact retry boundary is `MooRuntime.AttemptContext`: it owns the copied `ConnectionState` map, while `openAttempt()` currently seeds connection player/info/intrinsics into `WorldTxn` and `finishAttempt()` reads them back. The registry must be copied once into the attempt and survive `replaceAttemptWorld()` unchanged.
- `BuiltinCatalog` has two terminal constructor families (standalone and listener-backed). Both currently initialize connection builtins against the invocation's `WorldTxn`; the narrow route is a `Supplier<ConnectionRegistry>` supplied by `MooRuntime`, plus one standalone registry for ordinary catalog tests.
- Runtime still needs its richer `ConnectionState` map for transport buffers/options. Publication comparison remains on that map; `finishAttempt()` must synchronize only player/intrinsics from the attempt registry before publish.
- Highest current blocker/risk: changing the generic `BuiltinHandler` signature would fan out across the VM and hundreds of lambdas. Do not do that; inject registry ownership into `BuiltinCatalog` instead.
- Immediate next action: add the registry class, wire catalog/runtime access, remove the world connection surface, and update focused connection tests before beginning property-layout extraction.

## 2026-08-18 connection extraction checkpoint

- Follow-up test-only commit `8c199fa test: correct connected player filtering contract` fixes the RED expectation to match Toast/current behavior: default `connected_players()` includes attached nonnegative players, while the truthy optional argument also includes negative pre-login identities.
- Uncommitted production work now adds `moo.server.ConnectionRegistry`, removes all connection fields/methods plus `baseRevision()` and singular `changeParent()` from `WorldTxn`, routes connection builtins through an injected registry supplier, and places the registry in `MooRuntime.AttemptContext` beside copied transport sessions.
- `replaceAttemptWorld()` now changes only the MVCC world; the attempt registry survives retry. `finishAttempt()` synchronizes player/intrinsic state back into runtime `ConnectionState` before publication.
- Login-driven player switching uses a runtime helper that preserves the removed `WorldTxn` validation by checking that the destination object exists before mutating the registry.
- Focused `BuiltinCatalogTest` connection setup now owns a registry explicitly. Static reference search shows no remaining WorldTxn connection call sites. `git diff --check` is clean.
- No Gradle, build, test, formatter, or OpenRewrite command has run.
- Next action: manually normalize newly introduced formatting, commit the connection production slice, then extract property algorithms and consolidate property-holder operations.

## 2026-08-18 property-engine checkpoint

- Connection production commit is `63c1cdf refactor: extract attempt-local connection registry`; worktree was clean immediately after it.
- Uncommitted property work adds `moo.world.PropertyLayoutEngine` containing the pure inheritance traversal, parent validation, inherited-slot construction, permanent layout rebuild, descendant discovery, direct-parent lookup, affected-ancestor lookup, and anonymous layout rebuild algorithms.
- WorldTxn call sites are being retargeted to the engine. Its renamed `validatedParents()` orchestration still invokes transaction `object()`/`ancestry()` reads before calling the pure engine, preserving the prior MVCC read footprint instead of bypassing conflict tracking through `working.objects()`.
- The old static WorldTxn algorithm bodies are not all deleted yet; this is the current incomplete state and therefore not commit-ready.
- No local Gradle, build, test, formatter, or OpenRewrite execution has run.
- Next action: finish deleting every moved static body, make anonymous write-back a thin differently named transaction method, then statically audit exact method ownership before the property-holder consolidation.

## 2026-08-18 completed local implementation checkpoint

- Property/holder production commit: `db4888b refactor: extract property layout ownership`.
- Recipe/wiring commit: `5601d39 build: codify WorldTxn decomposition recipe`.
- `PropertyHolder` is sealed to `WorldObject` and `WorldAnonymousObject`; shared name/owner/property/verb replacement paths converge through private `replacePropertyHolder()` and `copyPropertyHolder()`.
- `WorldTxn` now has exactly two public constructors (two-argument convenience and complete six-argument durable-state form); affected anonymous/pending-finalization tests were migrated to the complete form.
- `DecomposeWorldTxn` is a scanning OpenRewrite recipe in the existing `rewrite-recipes` subproject. It collects WorldTxn static layout declarations, deletes the three dead methods and moved declarations, retargets remaining invocations, appends the declarations/nested property key to `PropertyLayoutEngine`, and is exposed as `moo.world.DecomposeWorldTxn` in `rewrite.yml`.
- The recipe's RED test is already in the original `47e1ab8` test commit, so CI `check` will execute it through the existing `:rewrite-recipes:check` dependency. No separate toolchain was introduced.
- Worktree was clean after `5601d39`. No push or PR exists, and no local Gradle/build/test/formatter/OpenRewrite execution has run.
- Current blocker: none known, but the first constructor-arity audit command emitted no output unexpectedly and must be rerun with a simpler static command before packet publication.
- Next action: rerun the static scope audit, inspect exact diff/commit/file inventory against the live issue, update this note with raw evidence, then send the exact PR title/body packet to root for Q to read.

## 2026-08-18 post-rebase holder-convergence checkpoint

- The unpublished branch was rebased cleanly onto `origin/master` `2920a6a45b4ac32652ec1478660b94d9aeff4ec0`; rewritten commits are `4349c39`, `2882bf8`, `3c377b0`, `127089d`, and `fd1b9d9`.
- Follow-up test-only commit `04d1823 test: enforce shared property holder operations` requires the permanent and anonymous public overloads to delegate through shared private holder operations for verb lookup/location, property lookup/read/write/authorization, property creation, verb creation, and verb-code replacement.
- The matching production change is currently uncommitted and confined to `src/main/java/moo/world/WorldTxn.java`: `236 insertions(+), 407 deletions(-)`. `git diff --check` is clean.
- Static review confirms the remaining separate permanent/anonymous topology methods have materially different topology/write-back semantics; pure shared ancestry/layout algorithms remain in `PropertyLayoutEngine`.
- No local Gradle, build, test, formatter, or OpenRewrite execution has run. Nothing is pushed or published.
- Publication blocker: PR #51 must land first because it owns the shared `rewrite.yml`/`rewrite-recipes` infrastructure. After its exact merge commit is known, rebase once, resolve only the shared recipe surface, repeat the static scope audit, and then send the exact PR packet before any push/creation.
- Immediate next action: finish the manual behavior/compile audit of the shared holder helpers, commit only `WorldTxn.java` as the production mate to `04d1823`, then hold the clean branch unchanged for the post-#51 rebase.

## 2026-08-18 clean unpublished branch checkpoint

- Production mate committed as `b476da1 refactor: unify property holder operations`; it changes only `WorldTxn.java` (`236 insertions`, `407 deletions`).
- The branch is clean and seven commits ahead of `origin/master` `2920a6a`: `4349c39`, `2882bf8`, `3c377b0`, `127089d`, `fd1b9d9`, `04d1823`, `b476da1`.
- Static scope inventory is 17 files, `1,563 insertions`, `1,111 deletions`. `WorldTxn` has none of the forbidden connection/dead surface and exactly two public constructors. Each of the eleven holder convergence helper names occurs exactly once.
- The recipe is present in the sole `rewrite-recipes` project, declared in `rewrite.yml`, and root `check` depends on `:rewrite-recipes:check` at `build.gradle.kts:126`.
- Live PR #51 is still OPEN at head `57de08d83f70b8ab32a122461f7526e5d9ee898e` on base `2920a6a`; it is the publication blocker because its v17 decomposition also owns the shared recipe infrastructure.
- No local Gradle, build, test, formatter, or OpenRewrite execution has run. Nothing is pushed or published.
- Next action: finish the static OpenRewrite move inventory audit, then leave the branch untouched until #51 normal-merges; rebase once onto its exact merge commit and repeat scope evidence before sending the PR packet.

## 2026-08-18 executable-recipe audit checkpoint

- The static move inventory exposed a compile-level recipe defect: captured `private static` methods stayed private after being moved, while rewritten `WorldTxn` call sites referenced them through `PropertyLayoutEngine`; the target engine also received no imports for the moved `java.util` types.
- RED commit `a2a0eec test: require usable moved layout methods` requires the generated engine's `java.util.List` import and rejects a private moved `ancestryFromParents` entry method.
- Production commit `475d2d1 fix: preserve usable moved layout methods` strips `private` from the six cross-owner entry algorithms and schedules the nine exact `java.util` imports used by the complete moved region. Internal helpers remain private.
- Official OpenRewrite v8.89.0 source confirms `JavaVisitor.maybeAddImport(String fullyQualifiedName)` and `J.Modifier.Type.Private` are available; no local compiler, Gradle, test, or recipe execution was used.
- The clean branch is now nine commits ahead of `2920a6a`; aggregate scope remains 17 files and is now `1,594 insertions`, `1,111 deletions`.
- PR #51 is still OPEN and has advanced independently; do not diagnose or publish #14. Wait for its exact merge commit, fetch, rebase once, then migrate the named-recipe proof to any resulting shared test-resource convention if required.

## 2026-08-18 architecture-cycle repair in progress

- Static review of the existing `ArchitectureTest` found the local connection extraction invalid: production `runtime` and `builtin` packages are not allowed to depend on `server`, and `MooServer -> MooRuntime -> ConnectionRegistry` formed a forbidden package cycle.
- This is a pre-publication blocker; the branch must not be called complete or published in this state. Existing ArchUnit tests would be RED, but were not run locally under the no-build instruction.
- RED commit `65b3d49 test: require injectable connection registry ownership` requires the concrete registry to satisfy a consumer-owned copy/replace access contract.
- Uncommitted repair adds `moo.builtin.ConnectionRegistryAccess`; `moo.server.ConnectionRegistry` implements it; `BuiltinCatalog` depends only on the consumer port; `MooServer` owns the concrete instance and injects it; `MooRuntime` is being changed to depend only on the port and publish attempt copies back to the server-owned registry.
- Test call-site migration is incomplete. An apply-patch loop replaced all identical `new MooRuntime(world)` occurrences in `MooRuntimeTest` with `runtimeFor(world)`, but the helper and remaining constructor call sites in other tests still need to be added/updated.
- No Gradle, build, test, formatter, or OpenRewrite execution has run. Nothing is pushed or published. PR #51 remains the external publication/rebase gate after this local architecture repair is complete.
- Next action: finish the explicit test constructor migration, statically verify zero `builtin/runtime -> server` production imports and no constructor mismatches, then commit one narrow production/API repair paired with `65b3d49`.

## 2026-08-18 clean architecture repair checkpoint

- Additional RED commit `b2f829a test: require server-owned runtime connections` updates explicit runtime construction and proves a connected player's published identity is visible in the injected concrete registry.
- Production commit `4edff94 refactor: inject server-owned connection registry` completes the port inversion. `MooServer` owns `private final ConnectionRegistry connections`; `MooRuntime` and `BuiltinCatalog` depend only on `moo.builtin.ConnectionRegistryAccess`; attempt copies publish back through `replaceWith`.
- Static import audit reports no `moo.server` imports under production `moo.runtime` or `moo.builtin`, so the prior server/runtime cycle and both forbidden dependency edges are removed without changing `ArchitectureTest`.
- The branch is clean and twelve commits ahead of `2920a6a`. Aggregate scope is 22 files, `1,869 insertions`, `1,173 deletions`. `git diff --check origin/master...HEAD` returned no output.
- No Gradle, build, test, formatter, or OpenRewrite execution has run. Nothing is pushed or published.
- PR #51 remains OPEN and is still the sole external publication gate. After it normal-merges, fetch and rebase once onto its exact merge commit, resolve only shared recipe files, then repeat the complete static/commit/scope audit before sending the exact PR packet.

## 2026-08-18 hold checkpoint

- Local head remains clean at `4edff94`; `git rev-list --left-right --count origin/master...HEAD` returned `0 12`.
- Final constructor inventory is coherent by static signature review: standalone runtime construction takes `(WorldTxn, ConnectionRegistryAccess)`, listener forms take the registry port last, and every repository `new MooRuntime(...)` call was migrated explicitly. No legacy production caller remains outside `MooServer`.
- The existing no-op connection methods in focused test/listener fixtures establish that an empty no-op method body is accepted repository style; the private standalone `BuiltinCatalog.EmptyConnectionRegistry.closeConnection` follows that pattern.
- Live PR #51 remains OPEN, currently observed at head `c49b09d158430f796705322835811f06c5c74029`; its Static analysis & tests check is still failing, so no rebase/publish action is authorized yet.
- No local Gradle, build, test, formatter, or OpenRewrite execution has run. Next action remains waiting for #51's exact normal-merge commit, then one measured rebase and full post-rebase static audit.

## 2026-08-18 final static ownership audit

- Follow-up RED/production pairs since the prior checkpoint are `4a4fad2`/`eb7d414` for shared parent-change dispatch and `3e31974`/`4734427` for preserving the live instance `collectAncestry` overload while the recipe moves only static layout methods.
- RED `b5f07c5 test: forbid mirrored connection registry fields` exposed residual duplicate ownership in `MooRuntime.ConnectionState`: `connectionInfo`, `player`, and `intrinsicCommands` still mirrored the server-owned registry.
- The uncommitted production mate now removes those fields and routes queue/flush lookup, published identity/info queries, intrinsic-command state, and checkpoint active-player state through `ConnectionRegistryAccess`. Registry state comparison is included in session publication so registry-only changes are not lost.
- Static review then found a separate compile-level blocker in `MooServer`: the injected concrete `ConnectionRegistry connections` collides with the pre-existing socket `Map<Long, Socket> connections` field. This survived because no local compilation is permitted.
- Current local head is `b5f07c5`; four production files are modified for the authoritative-registry mate. No Gradle, build, test, formatter, or OpenRewrite execution has run. Nothing is pushed or published.
- Next action: commit the authoritative-registry production mate, add a focused RED contract requiring a distinct `MooServer.connectionRegistry` owner, rename only the registry field/injection sites, then repeat the residual surface and package-edge audit. PR #51 remains OPEN and is still the publication/rebase gate.

## 2026-08-18 clean final-audit hold

- Authoritative-registry production mate: `265c5f5 refactor: make connection registry authoritative`.
- Compile-collision RED/production pair: `5485d36 test: distinguish registry from socket ownership` then `f3f28ba refactor: distinguish registry from sockets`.
- Residual deleted-API caller fix: `4cb8b8d test: use retained plural parent API`; import-order cleanup: `ee17984 test: keep migrated imports ordered`.
- Static counts now report zero forbidden dead methods, zero WorldTxn connection APIs, zero runtime mirror field declarations, one declaration for each of the ten shared holder helpers, two public WorldTxn constructors, no production runtime/builtin imports from server, and empty `git diff --check` output.
- Clean unpublished head is `ee1798479f528a878ed25c90e383ccb3d2170a7e`, 22 commits ahead of local `origin/master`, across 23 files. PR #51 is still OPEN at observed head `5d3e5e9d02489bab1534f0fa5623d5239f790038`.
- No Gradle, build, test, formatter, or OpenRewrite execution has run. No push or PR exists. Hold until #51 normal-merges, then fetch/rebase onto its exact merge commit and repeat static evidence before preparing the Q-visible PR packet.
