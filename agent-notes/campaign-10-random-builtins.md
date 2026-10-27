# Issue 10: Random builtin family

## Goal
Extract random, reseed_random, frandom and random_bytes through the existing OpenRewrite mover, preserving order, flags and generator lifetimes.

## State
Implemented after the parent confirmed Time conformance. The existing mover generated RandomBuiltins, the in-place catalog call, the unchanged string limit helper in ValueLimits and direct ArithmeticOps/IndexOps retargeting. Reviewed and applied the migration patch through apply_patch. The authored MoveBuiltinFamilyTest file passes. Ready for parent publication, required CI and external review; no push or publication performed here.

## Decisions and why
The smallest approved change moves about 119 catalog lines into a 100–115 line RandomBuiltins family, extending the existing mover by roughly 60–85 lines and YAML by 10–12 lines. Pass two standard Supplier<Random> values so each invocation reads the catalog's existing integer/floating generator, and pass the existing process-wide SecureRandom directly. Do not create a generator or facade. Move stringLengthError unchanged into existing ValueLimits and retarget ArithmeticOps and IndexOps directly.

## Surprises
Existing tests replace both final Random fields reflectively after manifest construction, so capturing generator values at registration would change observed behavior.

The migration runner completed successfully after emitting OpenRewrite/Gradle classpath-resolution and configuration-cache warnings. Its ordinary prerequisites compiled the unchanged application and test sources before the recipe ran. This does not establish compilation of the resulting moved application code; PR CI must do that.

## Next step
Parent reviews the committed family and publishes it, retains the required external review and PR CI, and merges only after their required checks pass.

## Draft PR body
Moves random, reseed_random, frandom and random_bytes registrations and implementations into RandomBuiltins through the existing OpenRewrite mover. Catalog-owned integer/floating generators remain shared with their existing callers and are read through suppliers on invocation; random_bytes uses the same process-wide secure generator. Moves the existing string length check to ValueLimits with direct VM callers.

Validation: the JDK 25 migration dry-run completed and its generated patch was reviewed and applied; `:rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest` passed. Application checks and conformance belong to PR CI.
