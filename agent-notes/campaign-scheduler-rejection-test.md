# Goal
Repair the existing host-submission rejection test without changing scheduler behavior.

# State
Implemented in the existing test only; PR101 opened by coordinator. CI run 40410931160 rejected the old test's blocker submission at PublicationSchedulerTest.java:1002 with one active task and four queued tasks. The original harness executor is shut down orderly and awaited before replacement; the harness owns and closes the replacement through its normal scheduler lifecycle. Claude found that I hardcoded the replacement rejection policy and queue capacity, disconnecting the test from production configuration. The replacement now inherits the terminated original executor's rejection handler and empty queue capacity; no assertion or new guard was added.

# Decisions and why
The current activity/ticket observation does not identify the host-producing attempt: PublicationScheduler.java:1493-1495 can return an irrevocable rerun, and dispatch at 1325-1327 submits scheduler work to the same queue. The test can therefore race with an internal submission. The real host submission is a FutureTask at 1880-1897. Smallest repair: a test-owned executor with the existing one-worker limit and a replacement queue of the original capacity fills its queue immediately when that host submission arrives, then invokes the original executor's rejection handler. Replace only this test's executor through its existing reflection-based inspection pattern; remove the slow MOO loop and elevated tick budget. Expected change about 40 lines; no production hook or flag.

# Surprises
The test's initial ingress ticket does not mean the VM attempt that submits host work has started. The first authored run failed because I assumed the queue was empty at host submission; XML stderr located that assertion at test line 992 under publishSuspension:1897. The setup now fills only remaining slots while holding the scheduler monitor, preventing competing dispatch submissions during saturation. Existing scheduler entries retain their ownership of already-occupied slots.

# Next step
The authored method passed after the Claude finding repair with WSL Debian, JDK /opt/java/25, and `bash ./gradlew --max-workers=2 :test --tests world.mongoose.banteng.runtime.PublicationSchedulerTest.mapsRejectedHostSubmissionToCatchableQuotaError`: BUILD SUCCESSFUL in 35s. An initial probe guessed Ubuntu and failed with WSL_E_DISTRO_NOT_FOUND; coordinator supplied the established Debian invocation. No CI suite run locally. Next: coordinator checks the updated draft/source, pushes the repair to PR101, and completes Claude review plus PR CI.

# PR draft
Title: Make scheduler host-rejection test saturation deterministic

The host-submission rejection test filled its executor queue after observing generic scheduler activity. Internal scheduler submissions could consume a slot first, causing the test setup itself to throw RejectedExecutionException.

Fill the remaining slots of a replacement queue with the original executor's capacity from the active worker immediately before its real host FutureTask submission, holding the scheduler monitor against competing dispatch. The original executor's rejection handler rejects that host work, and the test retains the catchable E_QUOTA assertion. Remove the timing loop and elevated foreground tick budget. No production code changes.

Validation: `:test --tests world.mongoose.banteng.runtime.PublicationSchedulerTest.mapsRejectedHostSubmissionToCatchableQuotaError` passed in WSL Debian with JDK25. Coordinator publishes after checking this draft against source and arranges Claude review.
