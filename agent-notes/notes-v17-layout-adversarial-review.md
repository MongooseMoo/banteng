# Toast v17 layout adversarial review

## Current findings

- The implementation is being reviewed read-only against Phase 4 prerequisite item 2 and pinned Toast commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Toast fork vectors are one global flattened table: compiling a fork body can add nested fork vectors to the same global state before the enclosing fork is added. `ToastV17ProgramLayout` models that order, but `MooCompiler` stores nested fork vectors inside each child `BytecodeProgram`. Therefore Toast vector numbers and `BytecodeProgram.forkVectors()` indices diverge for nested forks; the fixture test does not cover this.
- The plan changed concurrently during review from "committed Mongoose fixture" to "checksum-pinned local Mongoose fixture." `ToastV17ProgramLayoutTest` opens `fixtures/mongoose.db` directly but performs no SHA-256 assertion, so the test itself does not prove that its ignored local input has the Phase 0 canonical checksum.
- The current fixture test hard-codes ten expected continuation rows and validates only those distinct mappings. It does not exercise width thresholds or general nested-fork vector identity.
- Confirmed pinned-width mismatch: Toast computes `numbytes_label` before computing `numbytes_stack`, and its pre-label `bc.size` excludes stack-reference widening (`code_gen.cc` 1246-1259). `ToastV17ProgramLayout.VectorBuilder.finish()` computes `stackWidth` first and includes it in `sizeWithoutLabelExpansion` at Java lines 237-247. For a vector with maximum stack above 256 and a stack fixup, Banteng can choose a wider label encoding than pinned Toast, shifting all later call boundaries.

## State and blocker

- No production, test, plan, index, or history changes were made by this review.
- This notes file is an untracked checkpoint artifact required by the active hook and must remain uncommitted.
- Review is still in progress; compiler-width edge cases and AST-path completeness remain to be checked against pinned Toast source.

## Next action

- Finish source-by-source comparison of reference widths, statement/expression emission, AST-path uniqueness, and test authority, then report concrete file/line findings to the parent agent.
