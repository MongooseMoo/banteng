# Issue 10: Math builtin extraction

## Goal
Move the first builtin family through the existing OpenRewrite mechanism, preserving manifest order and behavior.

## State
Recipe, registrar, and first Math-family migration implemented in the isolated campaign worktree. Focused MoveBuiltinFamilyTest passes under WSL JDK 25. Final existing-runner dry run completed successfully and produced exactly MathBuiltins.java and BuiltinCatalog.java; that patch was applied through apply_patch. The final campaign/87-value-semantics dependency repair is merged normally without conflicts; Math extraction files were untouched by the merge. No publication. Parent owns primary-source review, PR, and CI.

## Decisions and why
The smallest first family is the contiguous min through trunc registrations and the static minimum through relativeHeading closure. Random registrations follow that block and retain their current instance lifetime for a later family. The registrar defaults only the fields still present in BuiltinSpec: ANY permission and irrevocable=false. No cost API is reintroduced.

The recipe captures declared static methods and registration AST nodes, builds their direct family owner, and replaces the original block in place. Existing shared services stay with their owners. The first implementation is about 240 recipe lines and 45 registrar lines, plus moved implementations. Its default configuration is Math; the constructor accepts the family name, registration names, and declared static method closure for later extractions. Argument-type constants currently cover Math's ANY, INTEGER, and NUMBER; later families must supply their actual constants and runtime dependencies rather than assuming this first configuration covers them.

Source comparisons after applying the patch establish all 28 Math names retain their order; all 23 moved method bodies match the previous catalog ignoring whitespace; the rest of the catalog code matches after removing import lines and replacing the moved registration block with MathBuiltins.register(entries). Random state and all non-Math implementations remain in their current owner.

## Surprises
PR CI rejected the new family package because its classes lacked explicit nullness marking. Added the same package-level NullMarked declaration as the existing builtin package; the existing compiler check remains unchanged. The recipe moves classes into this now-declared package.

Issue text still describes removed cost fields. Current BuiltinSpec has five fields; source governs the registrar.

The initial focused compilation rejected deprecated zero-argument printTrimmed calls. The recipe now uses the cursor-taking API. Scoped import ordering initially used an outdated constructor arity; current OpenRewrite requires Boolean and String arguments. Both authoring errors were repaired without changing compiler protection.

The existing dry-run runner logged Gradle IllegalResolutionException while resolving subproject runtimeClasspath without an exclusive lock. It nevertheless parsed root sources and completed successfully. Disabling configuration cache for the invocation removed the cache serialization diagnostics but did not remove the subproject classpath diagnostic; no claim is made that the plugin compatibility defect is repaired. No runner script was changed. The final migration output is bounded to the two intended production files.

The first YAML composition applied import ordering globally and its dry run proposed unrelated files. Before applying anything, the composition was narrowed to the mover and import cleanup was scoped inside it. Because generated methods retain incomplete type attribution, generated imports are selected from identifier usage rather than relying on RemoveUnusedImports. The focused fixture exercises the final mover and its second application. Formatting expanded its one-line fixture helper; that assertion now checks the declaration and return statement independently of line layout.

## Validation
- WSL JDK 25: ./gradlew :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest --console=plain
- WSL JDK 25, GRADLE_OPTS=-Dorg.gradle.configuration-cache=false: bash scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractMathBuiltins
- Read-only source comparisons described above; git diff --check.
- No broad local CI or conformance suite was run. The runner's existing compilation prerequisites execute as part of the migration program.

## Draft PR text
Title: Extract Math builtins through the family-moving recipe

Moves the contiguous min-through-trunc registrations and their static implementations from BuiltinCatalog into MathBuiltins. Adds the fluent registrar with ANY permission and irrevocable=false defaults using the five current BuiltinSpec fields, plus the OpenRewrite mover and focused fixture. Registration order and implementation bodies are preserved.

Partial delivery of #10. Random and other builtin families remain; this does not close the issue or claim the final catalog size. Validation: focused MoveBuiltinFamilyTest and existing rewrite dry run under WSL JDK 25; required PR checks and conformance remain with CI. The dry-run plugin still logs subproject classpath lock diagnostics, documented above.

## Next step
Parent reviews and publishes this first family and obtains required PR checks before merge. Later families wait for that published check result. This is partial issue 10 delivery; later families and the under-300-line catalog end state remain. There are no deployment settings or postmerge activation steps for the Math move and no user-owned prerequisite identified. Required primary-source review and PR checks belong to the parent. This worker stops after the first family commit and requested dependency integration; no tests or review were repeated for ancestry alone.
