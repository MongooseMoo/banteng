# Issue 10: Time builtin family

## Goal
Move time, ctime and ftime registrations and implementations into TimeBuiltins using the existing OpenRewrite recipe.

## State
TimeBuiltins owns the three registrations, two implementations and four constants. The existing OpenRewrite runner generated the two production-file changes, applied through apply_patch. JSON conformance passed before this work began. No publication; root owns PR CI and Claude review.

## Decisions and why
Smallest change: move the three adjacent registrations, two static implementations and four exact clock/ctime constants. About 55 catalog lines become a 65-80 line family owner, with roughly 10-15 mover lines and 10-12 YAML lines. Preserve manifest order, irrevocable flags, clock selectors, error mapping and overflow cap. No instance state or new service.

## Surprises
The first runner invocation inherited Java 17 for Gradle, so loading the Java 25 recipe failed with UnsupportedClassVersionError (69.0 versus runtime maximum 61.0), and it produced no migration. Corrected the existing runner's invocation documentation to explicitly select JDK 25 for Gradle itself before retrying.

## Next step
Root checks source, publishes the branch against master, obtains Claude review and waits for PR CI including conformance before merge.

## Validation
- WSL Debian: JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin bash scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractTimeBuiltins completed successfully. Source remained frozen during normal compilation and migration generation.
- Read-only comparison confirmed ctime and ftime unchanged ignoring whitespace. Clock constants and cap retain their original values. time retains its original expression; all three shapes, order, ANY permission and irrevocable flags are preserved.
- Existing catalog tests cover ctime expanded years and cap plus ftime selectors; no local CI rerun. Production extraction awaits PR CI.
- git diff --check.
- Correct-runtime runner logged existing subproject runtimeClasspath resolution and configuration-cache diagnostics, then parsed root sources and produced precisely the planned production changes.

## Draft PR body
Continues #10 by moving time, ctime and ftime registrations, implementations and their four constants into TimeBuiltins. BuiltinCatalog delegates at the original manifest position; call shapes, irrevocable flags, clock selectors, overflow cap and native error mappings are preserved.

Generated with the existing OpenRewrite runner under JDK 25 and applied through apply_patch. Source comparison confirms both moved methods are unchanged apart from formatting. Validation: git diff --check; PR CI and conformance pending. The existing runner documentation now specifies JDK 25 for Gradle itself as well as compilation.
