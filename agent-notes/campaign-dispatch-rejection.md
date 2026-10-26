# Goal

Preserve ready publication tickets when the executor rejects a dispatch during the authorized campaign repair.

# State

Based on origin/master at 9a1bfd6. Isolated branch campaign/dispatch-rejection. Coordinator published PR103. Claude verified the code repair; its note finding is corrected below. CI failed the preexisting host-rejection test setup at line1002, whose repair is PR101. Conformance was pending at this update.

# Decisions and why

Smallest change: keep the ready queue head until executor acceptance; catch RejectedExecutionException and return with that entry still queued (about seven added lines). Reuse existing completion and wake dispatch calls. No new worker, queue, timeout, or test guard.

Source: base PublicationScheduler.java:1321-1332 removes an entry before execute and does not catch rejection. The capacity predicate treats getActiveCount() < workers as available capacity, even though a full executor queue can reject when all worker slots are allocated. The failing ConcurrentSchedulerStressTest.java:24-67 submits arithmetic VM work and blockers, not host work. complete and drainPublications call dispatch at base lines1605 and1619. Host submission also shares the executor outside the monitor, but that is not the observed producer's cause.

# Surprises

No repository AGENTS.md exists in this checkout; the task includes Q's global instructions directly.

Progress paths checked in source: a full executor queue contains VM attempts or host FutureTasks. VM attempts call complete and dispatch (1605); host terminal success enqueues a wake (1918), failure calls failWaiting, and cancellation calls cancelWaiting, which dispatches (2075). The dispatch monitor also guards queue head removal, and executeAttempt immediately acquires that monitor, so accepted work cannot complete/requeue before removal. close sets closed under the same monitor before shutdownNow (2195-2208), preventing shutdown races with dispatch. This is source reasoning, not a runtime liveness proof.

# Draft PR description

The scheduler's approximate capacity check can pass while its bounded executor rejects a VM attempt. Previously the publication ticket was already removed from the ready queue. Keep the entry queued until execute accepts it and return on rejection so existing completion/wake dispatch calls can retry it.

Validation: source inspection and diff review only; PR CI remains pending. Existing stress failure evidence was supplied by the parent agent (run 40411960009, artifact 12694136402); this agent has not independently downloaded it. No local CI steps run.

# Next step

Coordinator will obtain updated Claude review and green CI, incorporating the separately reviewed PR101 test setup repair before retrying this failed check. No local CI suite was run.
