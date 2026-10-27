# Goal

Owner correction: the initial extraction incorrectly used invocation-host Crypt. Q authorized recovery after the parent corrected the governing plan. The implementation and recipe now capture the catalog-owned hosts.crypt(), matching original nativeCrypt. See C:/Users/Q/code/banteng/agent-notes/issue-campaign.md, off-design handoff, for the source evidence and recovery history.
Extract issue 10's ten Crypto registrations and their full private implementation closure through the existing family mover.

# State
Implemented in an isolated branch based on the completed Binary extraction. CryptoBuiltins owns the ten registrations, fifteen methods and two nested types. The catalog calls register at the original position with the existing secure generator. No publication by this agent.

Latest Binary ancestry, including runtime/callback/WAIF repairs, is now merged locally without conflicts. The reviewed Crypto implementation, relocated test, mover/fixture and YAML are unchanged. The catalog still passes its own hosts.crypt() explicitly; no dependency owner changed during this integration.

# Decisions and why
Smallest change: move approximately 360 implementation lines and 40 registration lines, including the existing hash-input loop and Argon2Settings/Hasher nested types. Pass the existing secure generator and catalog-owned hosts.crypt() explicitly. Argon2 verification retains invocation-host serverLog. Use StringValue's binary decoder directly. Move the package-private failure-injection test to the family package, without a public seam or forwarding method. Extend the existing mover only for the actual loop, instance methods and nested types.

# Surprises
The AST foreach variable has the Statement interface type, so the scanner matches VariableDeclarations explicitly. Printing a detached loop block lacked an enclosing SourceFile; loop detection now matches its two actual input-plus-suffix AST expressions. Printing the control directly did not produce a valid foreach header; generation prints its variable and iterable explicitly. These defects were diagnosed by compilation and the authored fixture before applying any runtime migration.
The migration completed with existing Gradle/OpenRewrite configuration-cache and subproject classpath-lock warnings. It generated exactly the expected two files, reviewed and applied through apply_patch. Source remained frozen during the successful migration's normal compilation prerequisites.

# Validation
`wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin ./gradlew :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest` passed after the complete recipe edits. The focused fixture covers the real loop order, flags, explicit existing Crypt/secureRandom dependencies, nested type ownership, package-private verifier and idempotence.
`wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin bash scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractCryptoBuiltins` completed successfully using normal prerequisites, without excluded tasks.
`git diff --check` passed. Application CI/conformance and the relocated runtime test await the published PR; no broad local CI was run.
After the owner correction, reran the same authored MoveBuiltinFamilyTest successfully with completed source frozen. Its existing fixture now asserts register captures the catalog-owned Crypt and the generated lambda uses that captured instance. The historical migration result above predates this direct repair; no migration or broad CI rerun was performed for the repair.

# Draft PR body
Moves crypt, salt, the six hash/HMAC registrations and Argon2/verify into CryptoBuiltins at their existing manifest position. The existing hash-input loop, call shapes, permissions and irrevocable flag remain intact. Crypto receives the existing secure generator and catalog-owned Crypt explicitly; Argon2 verification continues reading invocation-host serverLog. Shared binary decoding calls StringValue directly.

Moves the existing package-private Argon2 failure-injection test alongside its implementation, retaining mismatch, quota-error and log assertions. Adds the extraction recipe and focused mover coverage for the real loop, dependencies, nested types and idempotence.

Validation: normal migration dry run and authored MoveBuiltinFamilyTest. Application CI/conformance remains for this PR.

# Next step
Parent to inspect the substantive commit, integrate updated Binary/Random ancestry as needed, obtain Claude review, publish and read required CI/conformance before merge. No release or deployment performed here.

# Binary/runtime ancestry integration
Normal merge of the latest campaign/10-binary-builtins completed without conflicts or source adaptations. Compared CryptoBuiltins, its relocated family test, the rewrite subproject and rewrite.yml against the prior Crypto head: no differences. Runtime, VM, persistence, bytecode and app sources match the captured Binary merge parent exactly. Crypto registration remains `CryptoBuiltins.register(entries, SECURE_RANDOM, hosts.crypt())`; the family still captures its explicit Crypt parameter, while Argon2 verification retains invocation-host logging.

No local CI/test/migration or repeated review was run for unchanged code/ancestry. git diff --check passed. Parent owns publication, required CI and merge; Binary must land before parent retargets Crypto to master and merges it. This local preparation does not claim Binary has landed or conformance has accepted the integrated source.

## Login and layout repair ancestry
Merged the latest coordinator runtime login-output and pinned-layout-boundary repairs normally, without conflicts or family source adaptations. Family implementations, services, dependencies, extraction mover, authored fixtures and YAML are unchanged. No local tests, CI, migration or repeated review ran for this mechanical integration. Parent owns publication, hosted checks and merge.

## Queue prerequisite integration
Normally merged the published Host tip, including reviewed Regex, Binary and SQLite prerequisites. Additive mover resolution retains both Crypto instance methods and the Regex toastMatch method; fixture resolution retains all family methods unchanged. Reused catalog/YAML resolutions retain the combined family registrations and original catalog-owned hosts.crypt dependency; removed the unused ServerLog import left by the combined catalog imports. These resolutions follow final assembly. CryptoBuiltins and its focused family test remain byte-identical. No local tests, repeated review or publication; parent owns hosted validation and merge.
