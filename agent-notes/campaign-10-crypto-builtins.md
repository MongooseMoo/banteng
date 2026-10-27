# Goal
Extract issue 10's ten Crypto registrations and their full private implementation closure through the existing family mover.

# State
Implemented in an isolated branch based on the completed Binary extraction. CryptoBuiltins owns the ten registrations, fifteen methods and two nested types. The catalog calls register at the original position with the existing secure generator. No publication by this agent.

# Decisions and why
Smallest change: move approximately 360 implementation lines and 40 registration lines, including the existing hash-input loop and Argon2Settings/Hasher nested types. Pass the existing secure generator and invocation host crypt explicitly. Use StringValue's binary decoder directly. Move the package-private failure-injection test to the family package, without a public seam or forwarding method. Extend the existing mover only for the actual loop, instance methods and nested types.

# Surprises
The AST foreach variable has the Statement interface type, so the scanner matches VariableDeclarations explicitly. Printing a detached loop block lacked an enclosing SourceFile; loop detection now matches its two actual input-plus-suffix AST expressions. Printing the control directly did not produce a valid foreach header; generation prints its variable and iterable explicitly. These defects were diagnosed by compilation and the authored fixture before applying any runtime migration.
The migration completed with existing Gradle/OpenRewrite configuration-cache and subproject classpath-lock warnings. It generated exactly the expected two files, reviewed and applied through apply_patch. Source remained frozen during the successful migration's normal compilation prerequisites.

# Validation
`wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin ./gradlew :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest` passed after the complete recipe edits. The focused fixture covers the real loop order, flags, explicit existing Crypt/secureRandom dependencies, nested type ownership, package-private verifier and idempotence.
`wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin bash scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractCryptoBuiltins` completed successfully using normal prerequisites, without excluded tasks.
`git diff --check` passed. Application CI/conformance and the relocated runtime test await the published PR; no broad local CI was run.

# Draft PR body
Moves crypt, salt, the six hash/HMAC registrations and Argon2/verify into CryptoBuiltins at their existing manifest position. The existing hash-input loop, call shapes, permissions and irrevocable flag remain intact. Crypto receives the existing secure generator and invocation host crypt explicitly; shared binary decoding calls StringValue directly.

Moves the existing package-private Argon2 failure-injection test alongside its implementation, retaining mismatch, quota-error and log assertions. Adds the extraction recipe and focused mover coverage for the real loop, dependencies, nested types and idempotence.

Validation: normal migration dry run and authored MoveBuiltinFamilyTest. Application CI/conformance remains for this PR.

# Next step
Parent to inspect the substantive commit, integrate updated Binary/Random ancestry as needed, obtain Claude review, publish and read required CI/conformance before merge. No release or deployment performed here.
