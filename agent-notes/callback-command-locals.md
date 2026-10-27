# Goal
Repair command locals on fresh and nested callback activations, preserving the Toast callback contract.

# State
Published as PR138. The coordinator integrated command context, root/nested WAIF naming, startup dispatch, builtin continuation identity, error/checkpoint outcomes, lag callbacks, saved activation player/timeout stacks, switch-player/prelogin notifications, and WAIF indexed-assignment with durable temporary state. Hosted MOO conformance passed before the latest Java-test repairs. Standalone login now projects writes addressed to its own physical connection into the existing output when no transport is attached; focused tests passed. The pinned layout test selects its original builtin boundary without changing its numeric assertions; its focused method passed. Both repairs are integrated for fresh combined CI. Claude review awaits its session reset; no merge or deployment.

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
Restores callback command context, saved activation identities and builtin continuation frames; adds raw startup-script dispatch and lag callbacks; repairs error/checkpoint outcomes and switch-player/prelogin notifications. Connection notifiers resolve the next hook after the previous hook's first execution slice. WAIF indexed assignments preserve their owner, expression result and parent writeback through suspension and v17 persistence using the existing activation temporary register.

Focused authored tests passed. Combined required CI is running, and Claude review is pending its session reset.

# Next step
Diagnose remaining hosted failures, complete Claude review, and normal-merge once required checks and findings are resolved. Reuse completed reviews on unchanged family PRs when resuming their merges. No release or deployment performed here.
