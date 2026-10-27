# Goal
Extract the five Host builtins for issue 10 using the existing family mover.

# State
Implemented in an isolated tree from Binary with its integrated Random ancestry. HostBuiltins owns five registrations in two groups and all seven private implementation methods. No publication by this agent.

# Decisions and why
Smallest change: move getenv's inline registration and the separate curl/spellcheck/url_encode/url_decode group with the seven-method private closure (curl, serverIntegerOption, curlTransfer, curlError, spellcheck, urlEncode, urlDecode). About 115 implementation and 40 registration lines move into roughly 160–200 family lines. Existing NativeCurl/Aspell ownership, host-work staging, errors, timeout bounds, URL bytes/NUL handling and direct StringValue.encodeBinary calls stay intact. Only the existing mover's group names need adaptation; no new interface or service.

# Surprises
The normal migration completed while the conversation was interrupted. Resumed by inspecting that same session's completion, without starting a duplicate run. It succeeded with the existing Gradle/OpenRewrite configuration-cache and subproject classpath-lock warnings. Reviewed and applied exactly its two generated changes through apply_patch.

# Validation
`wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin ./gradlew :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest` passed after completed recipe edits. The authored fixture covers both manifest positions, inline environment encoding, existing hostWork closure, flags, private implementation ownership and idempotence.
`wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin bash scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractHostBuiltins` succeeded with normal compilation prerequisites and source frozen through completion; no excluded tasks.
`git diff --check` passed. Application CI/conformance was not run locally and remains for the published PR.

# Draft PR body
Moves getenv, curl, spellcheck, url_encode and url_decode into HostBuiltins, preserving their two manifest positions, call shapes, permissions and flags. The existing private transfer, server-option, spelling and URL algorithms move unchanged. Environment and curl output keep calling StringValue.encodeBinary directly; NativeCurl, Aspell, hostWork staging and error ownership remain intact.

Adds the declarative migration and focused mover fixture for separated groups, inline environment behavior, private transfer closure and idempotence. Validation: authored MoveBuiltinFamilyTest and normal migration dry run; application CI/conformance remains for this PR.

# Next step
Parent to inspect the substantive commit, integrate ancestry and shared recipe changes as needed, obtain Claude review, publish and read required CI/conformance before merge. No release or deployment performed here.
