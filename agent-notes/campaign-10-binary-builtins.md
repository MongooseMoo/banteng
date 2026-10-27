# Goal
Extract issue 10's binary builtins through the existing OpenRewrite family mover.

# State
Published as PR126 with completed Claude review. BinaryBuiltins owns the two registration groups and four private implementation methods. StringValue owns both shared codec methods; all five catalog caller sites retarget directly. Integrated merged Random additions in the shared recipe/YAML, preserving both reviewed changes; fresh PR CI checks the integration and the completed review is retained.

# Decisions and why
Smallest change: move the three registrations in their two existing groups and their four-method closure, around 120 implementation lines, into BinaryBuiltins. Put the shared escape decoder and encoder loop in existing StringValue, and retarget salt/hash/HMAC/getenv/curl directly. Preserve the exact loops, tab distinctions, integer validation versus string bypass and first-NUL chr truncation. No facade or new state.

# Surprises
StringValue is nested in MooValue.java, not a standalone file.
The normal migration dry run completed successfully after compiling its normal prerequisites. It reported existing Gradle/OpenRewrite configuration-cache and subproject classpath lock warnings but generated the three expected files. Applied its reviewed output with apply_patch, preserving existing file modes. Corrected codec indentation in the recipe and generated output.

# Validation
`wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin bash scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractBinaryBuiltins` completed successfully.
`wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin ./gradlew :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest` completed successfully after source edits finished. Focused test covers separated registration order, direct shared-codec caller retargeting and recipe idempotence.
`git diff --check` passed. Full application CI and conformance remain for the published PR; they were not rerun locally.

# Draft PR body
Moves decode_binary, encode_binary and chr into BinaryBuiltins while preserving their two manifest positions, call shapes and flags. Shared binary escaping lives directly on existing StringValue; salt/hash/HMAC/getenv/curl call it directly. Flattening, byte bounds, tab treatment and first-NUL truncation retain their existing algorithms.

Adds the declarative extraction recipe and focused mover coverage for the two registration groups, shared ownership, direct callers and idempotence. Validation: migration dry run and authored MoveBuiltinFamilyTest; application CI/conformance awaits publication.

# Next step
Parent to integrate overlapping recipe/YAML changes as needed, review the substantive commit, publish the PR and read its required CI/conformance before merging. No release or deployment performed here.
