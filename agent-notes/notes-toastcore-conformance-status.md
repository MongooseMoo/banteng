Key finding: fixture gating makes all three generic Toast matrices pass, but the ledger requires every packaged case to pass in at least one CI profile.
Current state: PR #85 head ece0eeb has green quality and three green Toast matrices; Full suite rejects five never-executed ToastCore cases.
Verification: managed pinned ToastCore and Banteng each passed 7/7; stock Test.db strictly declared-skipped the five gated cases.
Blocker: CI has no ToastCore fixture profile, so the data PR cannot satisfy the execution ledger and must not merge.
Next action: obtain approval for a controller-first CI profile using pinned lisdude/toastcore, merge it, then update and rerun PR #85.
