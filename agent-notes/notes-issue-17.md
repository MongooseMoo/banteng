# Issue #17 working notes

## Current state

- Isolated worktree: `C:\Users\Q\code\banteng-agent-issue-17`
- Branch: `agent/issue-17-string-encoding`
- Target base: `e0bf554579a2b37d8800858e9072a88bc3dd95e9` (normal merge of PR #45 / issue #7)
- Current pushed exact head: `f6ba2d28cb05232240ba1c219ca3ae18e4de239c`
- Current exact base/merge-base: `bc58a8296dce96782fdc089320d7828623e5411a`
- Rebased RED test commit on the preceding base: `d31ca0f957ecf79cfe23aa9e771d0845bd901ccf test: define StringValue Latin-1 ownership`
- No local Gradle build or test has been run, per instruction.
- Production migration and the narrow `J.Empty` recipe correction are committed and pushed.
- `rewrite.yml` now has a declarative `moo.value.UseStringValueFactories` recipe using `ChangeMethodTargetToStatic` plus method renames for the current private encoder helpers.

## Findings and observations

- Live issue #17 requires StringValue to own ISO-8859-1 encoding, deletion of private helper copies, a declarative OpenRewrite recipe, and zero ISO_8859_1 references outside StringValue.
- Parent resolved the API literally as `StringValue.text()`, `StringValue.of(String)`, and defensive-copying `StringValue.of(byte[])`.
- The constructor has been narrowed to private and all in-scope Java construction sites are being migrated to factories.
- Direct Java String encodes are being rewritten to `StringValue.of(String)` and StringValue decodes to `text()`.
- Current grep has only the owner declaration of `ISO_8859_1` left in `MooValue.StringValue`.
- Legacy helper methods in BuiltinCatalog, MooRuntime, MooServer, MooVm, TaskRegistry, FileIoService, SqliteService, and PcreService have been removed or are being eliminated from callers.
- A bulk unused-import cleanup accidentally removed StandardCharsets imports even from files that still use UTF_8 or the StringValue owner constant. Those imports must be restored in exactly the files with remaining `StandardCharsets.` references.
- The required StandardCharsets imports were restored, but a subsequent regex intended only to simplify generated casts over-captured enclosing calls in three files. The known malformed lines are confined to `FileIoService`, `SqliteService`, and `PcreService`; they are uncommitted and enumerated by `rg -n "\(\(StringValue\)"`.
- All known malformed cast rewrites were repaired explicitly. Current `git diff --check` is clean.
- Production Latin-1 Java-string encodes now use `StringValue.of(String)`; raw byte inputs use `StringValue.of(byte[])`; decodes use `StringValue.text()`.
- Remaining stream/parser Charset boundaries use `StringValue.charset()`. Greps for `.getBytes(StringValue.charset())` and `StringValue.of(...getBytes(StringValue.charset()))` are empty across `src`.
- The only `ISO_8859_1` source reference is the owner constant in `MooValue.StringValue`; the only `new StringValue(...)` calls are inside its two factories.

## Blocker

- Exact recipe head `e1c2ad2fcafeba853cc4079e6a33ca41fe4977e2` compiled both recipe source and tests, but the decode assertion failed at line 65 while encode passed.
- Root cause: OpenRewrite represents the empty `value.bytes()` argument list as a singleton `J.Empty` in this AST. Commit `f6ba2d2 fix: rewrite zero-argument StringValue decode` now accepts either a truly empty list or exactly one `J.Empty`, while retaining receiver type and charset checks.
- The first watcher start on `f6ba2d2` exited because GitHub had not registered any checks yet. This is a registration delay, not a CI result; retry after checks appear.
- Declarative YAML composites cannot capture the receiver/expression needed for the two inline constructor idioms. The implemented repair is a build-only OpenRewrite recipe subproject, activated by `rewrite.yml` alongside `ChangeMethodTargetToStatic`, with ordinary JUnit before/after proof wired into root `check`.

## Published PR and CI

- PR: `MongooseMoo/banteng#46` (non-draft), exact requested title/body, no merge authorization.
- Initial exact head `d9304eec5fe4f3ee7e8290e3cba3e9835f805c32` failed both build jobs because eight legacy method references remained after deleting helpers.
- Commit `2d75f6cacd628774f38e8b168ca54389017dd66a` migrated `BuiltinCatalog::encode/decode` and `MooRuntime::encode` to `StringValue::of/text`. On that head, compilation, conformance, and all ordinary tests except one ArchUnit rule passed.
- The exact ArchUnit failure was `moo.syntax.MooParser.parse(byte[])` depending on `moo.value.MooValue.StringValue.charset()`.
- The architecture test was not weakened. Commit `c5878cac0513312e4fb6efa64261892c7bcbd2b9` removed the byte-array parser overload, moved byte decoding to `StringValue` at test callers, and preserved the syntax package boundary.
- The preceding rebased head was `c6b80b549ed3268c58fa1d46ff34ed1ddcf90b75` on base `f507d1bd7f2a0b8401cd83c2815991ce52bbbd13`.
- Its first CI attempt had five successful checks and one unrelated-looking `ConcurrentSchedulerStressTest.saturationConflictsAndRetriesRemainBoundedAndEventuallyProgress` timeout. A same-head failed-check rerun was started, then intentionally abandoned when the base advanced because only current-base CI is merge evidence.
- Remote head was verified as exact `c6b80b549ed3268c58fa1d46ff34ed1ddcf90b75` immediately before the current rebase. Do not merge.
- The completed rebase produced head `662baeb9c36094ad9ef40153fa287abae28e494c` on exact base/merge-base `e0bf554579a2b37d8800858e9072a88bc3dd95e9`; it was pushed using an explicit force-with-lease against `c6b80b5`.
- The new base introduced three test-side `ISO_8859_1` sites. Commit `662baeb fix: migrate promoted StringValue call sites` migrated them to `StringValue.charset()` and `StringValue.of(byte[]).text()`.
- Exact head `662baeb` reached six successful checks, zero pending, zero failed, and GitHub reported CLEAN/MERGEABLE. That evidence is obsolete once the recipe contract repair changes the head.
- Commit `b48a7d8 test: require executable StringValue rewrite` adds the `rewrite-recipes` subproject, an exact before/after inline encode/decode test, and root `check` dependency. It is deliberately RED without the recipe class.
- Commit `3c09fa3 build: make StringValue rewrite executable` adds the build-only OpenRewrite visitor, makes the root rewrite configuration load the subproject artifact, and activates it from `rewrite.yml` before the existing helper retargeting recipes.
- No local Gradle, build, or test command has been run; CI must validate the OpenRewrite API/build wiring.
- The two recipe commits were pushed normally from remote `662baeb` to `3c09fa3`; exact-head CI started and the branch remained mergeable.
- The branch was rebased again onto normal-merge base `bc58a82` for GuardedBy issue #20. Only PublicationScheduler and TaskRegistry import blocks conflicted; both retained `@GuardedBy` and removed duplicate `StandardCharsets` ownership.
- Dependency verification was repaired in two measured waves: four compile-classpath metadata artifacts, then `error_prone_annotations-2.10.0.jar` and `junit-bom-5.13.4.module` for test runtime.
- Commit `e1c2ad2 test: assert StringValue rewrite semantics` replaced whole-file formatting equality with explicit encode/decode after statements and zero-residual before-idiom assertions. CI proved encode transforms but exposed the zero-arg decode AST mismatch.

## Next action

1. Wait for GitHub to register checks on exact head `f6ba2d2`, then restart the watcher.
2. Diagnose and fix only branch-owned recipe/test failures.
3. Re-run scope proof and report without merging.
