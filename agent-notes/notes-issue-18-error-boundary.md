# Issue #18 error-boundary notes

## Current state

- Isolated worktree: `C:\Users\Q\code\banteng-agent-issue-18`
- Branch: `agent/issue-18-error-boundary`
- Previous base: `64c2ce3b7f49f5292b47722a1c04a26d5df1993a`; current exact master is `31fb441844ce5b75a02985f3037936e3d7cb70a4`.
- Final rebase preserved separate main commits: `27ddadc test: specify typed error boundaries` and `5e085fd feat: add typed world error boundary`.
- PR: `#48`; exact current head is `f7417a366bbb916572562d83fe4ccd3022c4d8ae`.
- No local Gradle, build, or test task has been run; CI remains the execution gate.

## Findings and observations

- `WorldTxn` originally exposed 25 public primitive-boolean mutation methods. The pure `valueRefersToWaif` predicate is the only authorized primitive-boolean survivor.
- The tests-first contract introduces world-owned `MooError`, sealed `WorldResult<T>`, exact property/move failure assertions, an ArchUnit ban on `Throwable` dependencies in `moo.builtin`, and a custom Error Prone checker contract for narrow `IllegalArgumentException` catches.
- Production work currently adds `MooError` and `WorldResult`, migrates every public `WorldTxn` mutation signature, adds programmer-aware property-write entry points, and is migrating VM callers to propagate `Failed.reason()` rather than guessing an error.
- Existing mutation callers remain in `BuiltinCatalog`, `MooRuntime`, and tests. These must all consume the typed result with no compatibility alias or shim.
- Builtin native method-handle calls currently require a deliberate narrow boundary because `MethodHandle.invokeExact` declares `Throwable`; JVM `Error` values must propagate and `moo.builtin` may not catch or depend on `Throwable`.
- A leaf `moo.host.NativeCalls` boundary now owns the unavoidable method-handle `Throwable` channel, explicitly rethrows JVM `Error`, and translates only non-`Error` linkage failures. `BuiltinCatalog` no longer catches or directly depends on `Throwable`.
- The custom Error Prone checker and service registration are now implemented in `errorprone-checks`; the root production compile loads it as a project dependency. It rejects any try block catching `IllegalArgumentException` (or a supertype) unless the guarded block has exactly one top-level operation.
- Broad builtin listener, map, and object-creation catches are being split so each expected conversion/library failure surrounds only its exact operation.
- All boolean-context mutation callers found in `BuiltinCatalog`, `MooRuntime`, `MooVm`, and existing tests now consume `WorldResult`; VM mutation failures use `Failed.reason().value()` instead of hard-coded fallback errors.
- `moo.host` has its own `@NullMarked` package boundary. The ArchUnit package allowlist was extended only for the new leaf and its builtin consumer.
- The checker recognizes direct and union-declared `IllegalArgumentException` catches. It deliberately does not reinterpret every unrelated `RuntimeException` boundary as an IAE parse catch.
- First exact-head CI reached the checker compile and failed because `buildDescription` exposes `com.sun.tools.javac.tree.JCTree`, while the checker compile task exported only `javac.code`. Static tests and conformance both stopped at this same upstream compile failure; conformance did not execute.
- Rebase conflict in root `build.gradle.kts` was resolved by retaining both current-master `error("GuardedBy")` and #18's `NarrowIllegalArgumentCatch` enablement.
- Replacement exact-head CI jobs `95860182457` and `95860182465` both compile the checker successfully, then stop in root `compileJava`: `NativeCallException` extends `IllegalStateException` without `serialVersionUID`, and `-Xlint:serial -Werror` promotes that branch-owned warning to an error. Neither static tests nor conformance executes beyond compilation.
- The `MemorySegment.reinterpret(long)` restricted-method warning is also present but is not the error reported by the failed compile task.
- After the serial field was added, the restricted `MemorySegment.reinterpret(long)` warning became the sole `-Werror` compile failure. The method-local `@SuppressWarnings("restricted")` was restored on `nativeCrypt`; no global suppression was added.
- Exact head `e450e1a` compiled production and reached `compileTestJava`. Its nine errors were: missing `MooValue`/`ErrorValue` imports in the new `WorldTxnTest` contract, two current-master `WorldTxnPropertyTest` callers still passing `WorldResult<MooValue>` to `assertTrue`, and an unsupported ArchUnit fluent method (`areEquivalentTo`).
- Commit `32ee26b fix: compile typed error boundary contracts` adds only those imports, asserts both typed `Ok` values and exact written values, and uses ArchUnit's supported `JavaClass.Predicates.equivalentTo(Throwable.class)` dependency predicate.
- That direct-Throwable ArchUnit dependency predicate produced 11 false positives, all compiler-generated `Throwable.addSuppressed` calls from try-with-resources. It was replaced with a supported real ownership rule forbidding `moo.builtin..` dependencies on `java.lang.invoke..`.
- The checker itself originally enforced only IAE narrowing. Separate tests-first commit `2944cd1` now requires direct `catch (Throwable)` rejection in `moo.builtin` while permitting the intentional `moo.host` boundary; production commit `f45b907` implements that compiler-AST rule.
- Rebase onto `ef6931e3` preserved issue #46's StringValue promotion: `.text()`, `StringValue.of(...)`, and `StringValue.charset()` remain in conflict sites while #18's NativeCalls, narrow catch boundaries, typed WorldResult propagation, and dynamic-eval behavior remain intact.
- Final exact-head CI run `32185058738` is terminal with six successful checks: Workflow static analysis, Static analysis & tests, workflow CodeQL, Dependency review, MOO conformance suite, and the separate CodeQL check. The watcher reports passed 6, pending 0, failed 0, `CLEAN`, `MERGEABLE`, PR open, and no actionable review items.
- Literal issue review found the ArchUnit ownership substitute insufficient. ArchUnit 1.4.2 exposes imported try/catch blocks directly, so tests-first commit `1fb3911` adds a focused `moo.builtin` Throwable-catching fixture and rejection assertions; `fb8c858` adds a custom ArchCondition using `JavaClass.getCodeUnits()`, `JavaCodeUnit.getTryCatchBlocks()`, and `TryCatchBlock.getCaughtThrowables()` with owner/source location diagnostics. The Error Prone guard remains in place.
- Exact-head CI job `95869475405` reached `compileTestJava` and found one NullAway error: `AssertionError.getMessage()` is nullable in the focused test. No architecture test executed yet.
- Commit `9e2a7d6` fixes only that NullAway diagnostic with `Objects.requireNonNull`; its CI run was superseded before completion because PR #49 advanced master to `64c2ce3b`.
- Rebase onto exact master `64c2ce3b7f49f5292b47722a1c04a26d5df1993a` completed without conflicts and preserved the tests-first/production boundaries plus all narrow CI repair commits.
- Exact-head CI job `95871088473` executed the architecture test. The focused direct-catch fixture was rejected as intended; the production scan also reported 20 compiler-generated try-with-resources `Throwable` handlers in `BuiltinCatalog`, `FileIoService`, and `SqliteService`.
- Commit `985b56c fix: ignore synthetic resource catch blocks` uses imported bytecode evidence only: a `Throwable` block is ignored only when its owning code unit calls `java.lang.Throwable.addSuppressed`. It retains the focused ordinary direct-catch fixture and the Error Prone source-level catch checker.
- Current exact-head watcher snapshot on `985b56c`: two checks passed, three pending, zero failed; PR open/non-draft, `MERGEABLE`, merge state `UNSTABLE` while CI runs.
- After PR #47 merged, the 13-commit #18 series rebased cleanly with zero conflicts onto exact master `31fb441844ce5b75a02985f3037936e3d7cb70a4`. The remote update used an explicit lease against old head `985b56c`; new exact head is `f7417a366bbb916572562d83fe4ccd3022c4d8ae`.
- Replacement run `32187056015` is attached to exact head `f7417a3`. Current babysit snapshot: one check passed, four pending, zero failed; PR remains open, `MERGEABLE`, merge state `UNSTABLE` while CI runs.

## Blocker

- No external blocker; replacement exact-head CI is running.

## Next action

- Watch exact head `f7417a3` to terminal, diagnose only branch-owned failures, and do not merge.
