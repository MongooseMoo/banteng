# Goal

Preserve ready publication tickets when the executor rejects a dispatch during the authorized campaign repair.

# State

Based on origin/master at 9a1bfd6. Isolated branch campaign/dispatch-rejection. Implemented acceptance-before-removal in dispatch. No publication authorized for this agent.

# Decisions and why

Smallest change: keep the ready queue head until executor acceptance; catch RejectedExecutionException and return with that entry still queued (about seven added lines). Reuse existing completion and wake dispatch calls. No new worker, queue, timeout, or test guard.

Source: PublicationScheduler.java:1321-1332 removes an entry before execute and does not catch rejection. complete and drainPublications call dispatch at 1605 and 1619. Host work shares the executor at 1897, outside the scheduler monitor, so the capacity snapshot does not reserve acceptance.

# Surprises

No repository AGENTS.md exists in this checkout; the task includes Q's global instructions directly.

Progress paths checked in source: a full executor queue contains VM attempts or host FutureTasks. VM attempts call complete and dispatch (1605); host terminal success enqueues a wake (1918), failure calls failWaiting, and cancellation calls cancelWaiting, which dispatches (2075). The dispatch monitor also guards queue head removal, and executeAttempt immediately acquires that monitor, so accepted work cannot complete/requeue before removal. close sets closed under the same monitor before shutdownNow (2195-2208), preventing shutdown races with dispatch. This is source reasoning, not a runtime liveness proof.

# Draft PR description

When executor capacity changes between the capacity snapshot and execute, dispatch can reject a VM attempt after its publication ticket has already been removed from the ready queue. Keep the entry queued until execute accepts it and return on rejection so existing completion/wake dispatch calls can retry it.

Validation: source inspection and diff review only; PR CI remains pending. Existing stress failure evidence was supplied by the parent agent (run 40411960009, artifact 12694136402); this agent has not independently downloaded it. No local CI steps run.

# Next step

Commit candidate for parent review and PR CI. No local CI steps or publication. Parent should review current source and obtain CI on the candidate head before merging.
