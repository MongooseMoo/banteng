# Goal
Repair foreground uncaught-error and checkpoint-started callback outcomes within the approved red-check scope.

# State
Implemented in an isolated tree from current callback-command-locals, including its startup integration. Foreground error dispatch lives in MooRuntime.resumeRuntime, so the scheduler terminal/lag routing is unchanged. No publication.

# Decisions and why
Smallest change uses existing runtime continuations and publication boundaries: foreground selected-command errors dispatch the existing uncaught hook; handler true/block suppresses original traceback, false/absent/error preserves the correct fallback. Reuse existing continuation output/first split and text/flag optional suffix to retain prefix/user output independently from the original traceback. Keep a cleared existing handler continuation after first block to prevent recursive handler dispatch if it later errors. Checkpoint-started return/error/first block publishes its checkpoint once; signal errors keep suppressing signal defaults. Estimated 50–90 production lines, no new service/record/worker.
Source evidence and real fixture paths are in root agent-notes/callback-conformance.md, PR138 remaining-failure diagnosis. Toast execute.cc:3162-3196 controls handler outcomes; server.cc:829-847 continues checkpoint after any hook outcome. Existing scheduler suspendedInputOutput clears continuations on publication; this repair needs a narrow retained handler marker only. Lag worker owns measurement near executeSegment; its early LAG_HANDLER_RETURN handling must be preserved at integration.

# Surprises
The first test used serialized executeLine for a blocked callback and timed out: that API intentionally waits through suspension. The actual socket entry is receiveLine. Corrected the blocked row to use receiveLine and kept production behavior unchanged for this fixture mistake. Socket output streams through the listener; its returned list is empty at the first boundary, so the focused blocked assertion checks that boundary and invocation count. Prefix/suffix/fallback output is directly asserted for the completed/error outcomes.

# Validation
`wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin ./gradlew --no-daemon :test --tests world.mongoose.banteng.runtime.CallbackOutcomeRoutingTest` passed after completed source edits with source frozen.
The authored file covers absent/nonexecutable/false/true/error/blocked handler outcomes, original-versus-handler traceback order, prefix/suffix retention, a resumed blocked handler that raises without recursive dispatch, checkpoint hook return/error/60-second block, and zero-delay resume without duplicate checkpoint completion. It uses real runtime/scheduler producers and actual checkpoint files. No broad local CI/conformance.
`git diff --check` passed. Lag worker was informed of the narrow continuation-clearing hunk; preserve its independent early LAG_HANDLER_RETURN branch when integrating.

# Draft PR body
Foreground selected-command errors now reach handle_uncaught_error while preserving command output and delimiters. Truthy returns and blocking handlers suppress the original traceback; false/absent/error outcomes preserve fallback, with handler error output preceding it. A resumed blocked handler retains a cleared continuation marker so its later error cannot recursively dispatch itself.

Checkpoint-started hooks now allow checkpoint completion after an error or first actual block, as well as return. Dropping that checkpoint continuation at the block prevents duplicate completion when it resumes. Signal-error default suppression remains intact. Adds focused runtime and real-checkpoint coverage; authored-file tests passed, required CI/conformance remains for the PR.

# Next step
Parent to verify scoped diff/output envelope, integrate independent lag and continuation changes, obtain review, publish and read required CI/conformance. Next assigned reading is switch_player/prelogin-close contracts; no connection implementation belongs in this commit.
