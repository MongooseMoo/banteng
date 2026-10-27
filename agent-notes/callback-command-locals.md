# Goal
Repair command locals on fresh and nested callback activations, preserving the Toast callback contract.

# State
Implemented in an isolated tree from origin/master. The coordinator authorized this bounded red-check repair. MooVm.callVerb now initializes fresh command fields and inherits them for nested callbacks. No publication by this agent.

# Decisions and why
Current target source already routes runtime and move hooks through MooVm.callVerb. Smallest repair is therefore about eight net production lines in that existing owner: supply the five fresh root defaults, and let existing nested-call inheritance apply to callback kinds as well as NORMAL. Keep PASS behavior, receiver/caller/player/args/owner/definer, WAIF naming, callerProgrammer and builtin stack metadata unchanged. No new abstraction or guard. The original diagnosis used the historical coordination checkout; this plan is based on the actual target tree's unified owner.

Toast execute.cc:750-763 copies argstr/dobj/dobjstr/prepstr/iobj/iobjstr for nested calls; 3325-3336 supplies fresh root defaults. The real conformance initialize/protected/server-started fixtures read these exact fields.

# Surprises
The original root caller_perms allegation was withdrawn after reading its actual builtin consumer, which already returns #-1 with no callers. No fallback change belongs here.
I initially invoked unqualified Gradle test, which selected subproject test tasks too. The exact failure was :errorprone-checks:test reporting no tests found for the CallbackCommandLocalsTest include filter. Corrected the shared governing AGENTS authored-test sentence first to require explicit root/subproject task paths, waited for compilation to finish, then ran explicit :test. No unrelated suite was rerun.

# Validation
`wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin ./gradlew --no-daemon :test --tests world.mongoose.banteng.vm.CallbackCommandLocalsTest` passed after source edits finished and remained frozen through compilation.
The authored test covers fresh server defaults, explicit parsed command fields, eight ordinary/nested callback kinds, and preservation through snapshot restoration. It checks fixed Toast-derived field values and retains receiver/defining-location assertions. No broad local CI or conformance run.
`git diff --check` passed. Required CI/conformance remains for the published PR.

# Draft PR body
Fresh root callbacks now receive dobj/iobj as #-1 and empty command strings; explicit parsed input still overrides those defaults. Nested callback kinds inherit argstr, dobj, dobjstr, prepstr, iobj and iobjstr through the existing callVerb owner, matching ordinary nested calls and Toast's activation contract. Other activation metadata remains unchanged.

Adds focused coverage for fresh/parsed roots, callback kinds and snapshot restoration. Validation: authored CallbackCommandLocalsTest. Full CI/conformance remains for this PR.

# Next step
Parent to verify the scoped diff, obtain review, publish and read required CI/conformance. Reassess the independent WAIF naming/builtin-stack defects and unexplained failures separately. No release or deployment performed here.
