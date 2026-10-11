# Issue #23 delivery checkpoint

- Issue: `Small DRY consolidations: ownership preamble, unary-float template, server-message fallback, firstIndex/lastIndex`.
- Scope has four independently committed consolidation families: ownership preamble in `add_verb`/`add_property`; unary-float builtin factory after PROMOTE_NUMBERS; five server-message fallback copies; `firstIndex`/`lastIndex` twins.
- Intended mechanism: OpenRewrite declarative before/after recipes for items 1-3 where sufficiently uniform, hand edits otherwise; item 4 by hand. Old-pattern grep counts must reach zero.
- Isolated worktree: `C:\Users\Q\code\banteng-agent-issue-23`, branch `agent/issue-23-small-dry`, previously observed at old base `4880f025...`.
- Required base: exact `origin/master` normal-merge commit `e0bf554579a2b37d8800858e9072a88bc3dd95e9` after issue #7.
- Preserve every other worktree and all dirty/untracked primary-checkout content. Do not stage, commit, or push this notes file.
- Workflow: focused failing structural/behavior contracts first, separately committed from each narrow production consolidation; no local Gradle builds or tests.
- Publication gate: show the exact PR title/body before `gh pr create`; PR must close #23 and state CI will verify. After creation, continuously watch exact-head checks/reviews and fix only branch-owned failures. Never merge.
- Current blocker: none known.
- Worktree verification: clean branch `agent/issue-23-small-dry`; no remote branch existed; fast-forwarded from `4880f025...` to exact `e0bf554579a2b37d8800858e9072a88bc3dd95e9`. No repository-local `AGENTS.md` was found.
- Rewrite mechanism: `rewrite.yml` currently contains only the existing `moo.builtin.ExtractBuiltinResult` declarative recipe; `scripts/rewrite.sh` drives Gradle OpenRewrite dry-run/apply. There is no `buildSrc` custom recipe infrastructure on this base.
- Ownership duplication: `addVerb` and `addProperty` each resolve object/anonymous receiver to owner/flags and perform the same wizard/writable/new-owner permission rule; ordering differs. A single private result record/helper can preserve typed error returns and both callers.
- Unary-float inventory: the exact 18 promotion-aware unary builtins are `acos`, `acosh`, `asin`, `asinh`, `atanh`, `cbrt`, `ceil`, `cos`, `cosh`, `exp`, `floor`, `log`, `log10`, `sin`, `sinh`, `sqrt`, `tan`, `tanh`. `round` and `trunc` are float-only and not in this family; `atan` is optional-binary and `atan2` is binary. Existing tests already pin Mongoose integer promotion for the 18 and error semantics such as `sqrt(-1) -> E_INVARG`, `sqrt(NaN) -> E_FLOAT`, `atanh(1) -> E_FLOAT`.
- Unary-float risk: a bare `DoubleUnaryOperator` cannot itself encode the existing distinction between domain `E_INVARG` and non-finite `E_FLOAT`. The factory must preserve the current validation paths, likely by keeping domain-aware unary operations outside the shared coerce/apply/isFinite tail or by using a narrowly scoped internal error signal.
- Server-message inventory: exactly five duplicated blocks cover `redirect_from_msg`, `redirect_to_msg`, `timeout_msg`, `boot_msg`, and `recycle_msg`. Each first checks the connection's listener handler `server_options`, then root `$server_options` when the handler is nonzero, then accepts a string or list of strings, otherwise uses the default. The helper therefore needs the listener-handler context in addition to the issue's conceptual `name, fallback` parameters unless it is scoped around a connection object.
- VM inventory: `firstIndex`/`lastIndex` differ only in direction for map key traversal and returned list/string index; empty map/list/string and unsupported-type errors are structurally identical. Existing end-to-end VM tests cover first/last across map/list/string.
- Test strategy under the no-local-test constraint: add focused source-level structural contracts that fail on the existing duplicate spellings, in separate RED commits, while relying on the already broad behavior tests to pin no behavior change. Then add each narrow production consolidation in its own commit and verify old-pattern grep counts plus `git diff --check` only.
- Current blocker: none known; architecture for the unary domain-error preservation needs a narrow design before editing production.
- Implemented as eight TDD-boundary commits on `agent/issue-23-small-dry`:
  - `a89f765` test ownership RED; `4009995` production shared ownership preamble.
  - `d796dbf` test unary factory RED; `9406cd6` production 18-site unary-float factory.
  - `9e89a3a` test server-message RED; `41e8bf7` production listener-aware fallback helper.
  - `3d5bdf0` test boundary-index RED; `8162da2` production first/last shared implementation.
- Scoped raw counts before publication: caller-local ownership trichotomies 0 and helper references 3; unary factory manifest uses 18; old server `MooValue message = null;` declarations 0 and helper references 6 including declaration; first/last method declarations 0 and boundary helper opcode calls 2. `git diff --check e0bf554..HEAD` produced no output.
- OpenRewrite audit: the existing declarative mechanism has type/method retargeting recipes, but these extractions change control flow and signatures and are not uniform declarative substitutions; they were hand-edited under the issue's `where uniform enough` allowance. No new recipe or build mechanism was added.
- Exact PR packet was shown to Q before creation. PR #47: `https://github.com/MongooseMoo/banteng/pull/47`, non-draft, `Closes #23`, exact initial head `8162da2e6871da8bd36bbcc01dda5119469819f9`.
- Initial watcher snapshot: open, mergeable, UNSTABLE while checks run; 1 passed, 4 pending, 0 failed; no review items. Continuous watcher session 15206 polls every 30 seconds.
- No local Gradle/build/test command has run.
- Current blocker: none. Next: continue exact-head watcher, inspect any failures/reviews, and fix only branch-owned findings. Never merge.

## Mechanism repair and base update

- Parent scope gate rejected the absence of an OpenRewrite artifact even if the initial exact-head CI became green. Do not treat old-head green as completion.
- Family audit:
  - Ownership preambles are not uniform: actor lookup ordering drifted, verb/property permission parsing and surrounding control flow differ, and extraction changes return/error flow. Hand edit remains justified.
  - Unary-float manifest handlers are exactly uniform at 18 sites. This family requires an executable OpenRewrite transformation.
  - Server-message blocks share intent but use different listener variables, output accumulators, fallback text, and surrounding transition flow. The helper extraction and signature change are not a safe declarative substitution; hand edit remains justified.
- Added RED commit `f73ce9d` (`test: prove unary float rewrite recipe`): independent build-only `buildSrc` test harness, an 18-site before/after `RewriteTest`, and CI invocation `./gradlew --no-daemon -p buildSrc test` with report upload paths. No local build/test was run.
- Production changes are currently unstaged: `buildSrc/src/main/java/moo/rewrite/ConsolidateUnaryFloatHandlers.java` implements the exact 18-lambda transformation; `rewrite.yml` exposes `moo.builtin.ConsolidateUnaryFloatHandlers` declaratively. The recipe is scoped to `BuiltinCatalog.buildManifest`, ten-parameter lambdas, one `a` argument, and the exact operator map.
- PR #47 old remote head remains `8162da2e6871da8bd36bbcc01dda5119469819f9`; old watcher session 15206 is still active but will become obsolete after push.
- Master advanced through normally merged PR #44 to exact `bc58a8296dce96782fdc089320d7828623e5411a`, containing GuardedBy work. After committing the recipe production half, fetch and rebase all ten issue #23 commits onto this exact base, resolve overlaps to preserve both GuardedBy and #23, verify old remote head before force-with-lease, push, and restart exact-head watcher. Do not merge.
- Current blocker: none. Next: source-level inspect/diff-check recipe implementation, commit it without local builds/tests, then perform the exact-base rebase/update.

## Recipe CI diagnosis

- Recipe production commit `d6434ff` and RED commit `4e7d670` were rebased with the other eight commits onto exact master `bc58a8296dce96782fdc089320d7828623e5411a`; GuardedBy imports/annotations were verified present. Old remote head `8162da2...` was verified immediately before force-with-lease to new head `d6434ffd9c423bb35c4d2a5015cd2bc11bdcce7e`.
- Exact-head run `32183069530` failed both Static analysis & tests and MOO conformance because build-only recipe infrastructure was initialized in both jobs.
- Uploaded XML exact cause for the test: `ExceptionInInitializerError` from `org.openrewrite.java.Assertions`, caused by `IllegalStateException: Unable to create a Java parser instance`; the buildSrc test runtime lacked `rewrite-java-25`.
- Conformance failed during `:buildSrc:compileJava` dependency verification for four artifacts: `jackson-base-2.21.5.pom`, `jackson-dataformats-binary-2.21.5.pom`, `jackson-modules-java8-2.21.5.pom`, and `junit-bom-5.14.1.module`.
- Local Gradle cache supplied measured SHA-256 values for those exact files. A repair commit adds `testRuntimeOnly("org.openrewrite:rewrite-java-25:8.89.0")` and the four metadata entries.
- EOL caution: `gradle/verification-metadata.xml` is legacy mixed-EOL. A whole-file `unix2dos`/`dos2unix` experiment showed broad unrelated churn and must not be kept. Restore the file byte-for-byte from the repair commit's parent, then reapply only the four entries with their surrounding line endings preserved. Amend the unpushed repair commit afterward.
- Cross-PR constraint: PR #46 is introducing the sole repo-level `rewrite-recipes` subproject. PR #47 must not merge before #46; after #46 lands, rebase and move recipe/test into that project, deleting all #47 `buildSrc` infrastructure and duplicate CI/toolchain wiring.
- Current blocker: verification metadata EOL repair is in progress; #46 has not landed. Next: restore narrow metadata diff without whole-file normalization, amend, push, and watch the interim exact head while waiting for #46.

## Exact-head CI repair at b70448f

- The narrow metadata/EOL repair was completed and pushed as `a5c6351fd2f2829eae931dda14c82f3541d63711`; its exact-head run `32183732207` reached two branch-owned failures.
- Static analysis job `95862500514` uploaded recipe-test XML showing that all 18 unary handlers were transformed correctly. The only assertion difference was formatting: generated replacement lines were indented four spaces more deeply than the expected source. Commit `b70448f` now constructs the before source at indent 8 and the expected after source at indent 12.
- MOO conformance job `95862500599` reported two additional `:buildSrc:buildScriptClasspath` verification omissions. Commit `b70448f` adds only those artifacts: `error_prone_annotations-2.10.0.jar` SHA-256 `a249d4d25dfb86d41e6c82fc335df580189f0c9feeabdc53233fc1e5060724a1` and `junit-bom-5.13.4.module` SHA-256 `e959288fde1b1b050d9bc082fc786789128da5d2853091468fca504104bdf400`.
- Exact current PR #47 head is `b70448fe0c40013394885b9c1e731cfd531c473b`. Raw latest check state: four pass, two pending (`Static analysis & tests`, `MOO conformance suite`), zero failed; PR is open and GitHub reports mergeable/UNSTABLE while checks run. Watcher session 26438 is active.
- No local Gradle/build/test command has run. A downloaded checksum-only temporary JAR remains at `C:\Users\Q\AppData\Local\Temp\banteng-error_prone_annotations-2.10.0.jar` because the command safety layer rejected cleanup; it is outside every repository/worktree.
- Current blocker: PR #46 remains open at `f6ba2d28cb05232240ba1c219ca3ae18e4de239c`; its shared `rewrite-recipes` project has not landed. Even if the current head becomes green, #47 is not merge-ready until rebased after #46 and its temporary `buildSrc` recipe surface is moved into that sole infrastructure.
- Next action: continue exact-head watcher; diagnose/fix only branch-owned failures. After #46 lands, fetch new master, consolidate recipe/test/CI wiring into `rewrite-recipes`, preserve logical test/production boundaries, push with exact old-head protection if history is rewritten, and restart CI. Never merge.

## Shared recipe project unblocked

- PR #47 exact head `b70448fe0c40013394885b9c1e731cfd531c473b` completed six checks successfully with zero failures/pending, GitHub CLEAN/MERGEABLE, no reviews. This green head is obsolete for completion because it still carries duplicate `buildSrc` recipe infrastructure.
- PR #46 normal-merged as exact commit `ef6931e3c742775cf7748934c4c054eac50b255b`; issue #17 closed. The single repository-owned `rewrite-recipes` project is now available on `origin/master`.
- Old exact-head watcher session 26438 was stopped after its terminal snapshot. PR #47 remains open and unmerged.
- Current blocker: none. Next action: fetch exact master `ef6931e`, inspect the landed project, rebase #47, delete temporary `buildSrc` recipe/toolchain wiring, move `ConsolidateUnaryFloatHandlers` and its RED before/after proof into `rewrite-recipes`, preserve test/production logical boundaries, verify remote old head `b70448f`, force-with-lease after history rewrite, and restart the exact-head watcher. No local Gradle/build/tests; never merge.

## Rebase in progress

- Fetched and verified exact `origin/master` `ef6931e3c742775cf7748934c4c054eac50b255b`. The landed project is `rewrite-recipes`, included from root `settings.gradle.kts`; root `clean check` already exercises its JUnit tests. It owns the Java 25 OpenRewrite dependencies and parser runtime.
- Rebase of the twelve #47 commits from old base `bc58a8296dce96782fdc089320d7828623e5411a` onto `ef6931e` reached commit 6/12, `3e632e9 refactor: share server message fallback`, with one expected conflict in `MooRuntime.java`.
- Conflict cause: #46 converted each duplicated inline `StringValue` decode to `.text()`, while #23 replaces those five entire message blocks with `serverMessage`. The resolution must keep #23's five helper calls and implement the helper using `.text()` so both changes survive.
- The conflicted file was restored to rebase `ours` (exact new-master plus the first five successfully rebased #23 commits) before applying a narrow helper/call-site resolution. No unrelated file is conflicted.
- Current blocker: manual conflict resolution only. Next action: add listener-aware `serverMessage` with `.text()`, replace the five copied blocks, stage that one file, continue rebase, and handle recipe-project conflicts without recreating duplicate infrastructure. No local Gradle/build/tests; never merge.

## Shared-project consolidation and latest base

- The #46 consolidation produced two clean recipe commits in the sole `rewrite-recipes` project: the CI-executed exact 18-site before/after proof, then the production visitor plus YAML registration. All temporary tracked `buildSrc`, duplicate CI invocation/report paths, dependency metadata additions, settings changes, and build-file changes were removed.
- A rebase conflict in `MooRuntime` was resolved by keeping #23's five `serverMessage` call sites and implementing the helper with #46's owned `StringValue.text()` API. A first CI run exposed only a brittle structural test expecting one line break in the helper signature; the final test strips whitespace and still requires the exact return type, name, parameter types/names/order, six references, and zero old declarations. The formatter-compatible production signature is unchanged.
- Exact head `bc09969036af73f8bac875b0722a18417e09c628` completed all six checks successfully before master advanced again.
- PR #49 normal-merged as exact master `64c2ce3b7f49f5292b47722a1c04a26d5df1993a`. PR #47 was rebased onto that commit. The only conflict was `rewrite.yml`; the resolution retains #49's full `moo.persistence.ShareDbReaderInfrastructure` recipe and appends #23's unary-float recipe.
- Current exact PR #47 head is `4e3a2cf3db40332880ab35083edf96224af6cd60`, force-with-lease updated from measured old remote `bc09969036af73f8bac875b0722a18417e09c628`. Merge-base is exact master `64c2ce3`.
- Final diff names are limited to #23: unary recipe/test and `rewrite.yml`; `BuiltinCatalog`, `MooRuntime`, `MooVm`; `DuplicationConsolidationTest`. Raw counts: 18 recipe operators; 19 unary factory references including declaration; zero old server-message declarations; six server helper refs including declaration; three boundary refs including declaration. `git diff --check` had no output.
- No local Gradle/build/test command has run. Current watcher session 29004 is bound to exact head `4e3a2cf`; first raw state was two passed, three pending, zero failed, open/MERGEABLE/UNSTABLE, no reviews.
- Current blocker: exact-head CI remains in progress. Next action: continue watcher, diagnose/fix only branch-owned failures, keep PR open, and never merge.
