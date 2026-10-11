# Row 30 Banteng read-only trace

## Assignment

- Read-only investigation of `audit_output_order_disconnect`.
- Do not edit production/test/report files and do not run managed conformance.
- Inspect the durable row and committed Banteng source/tests from command evaluation through notify/output boot/disconnect.
- Identify the exact causal path for observing only disconnect and the smallest owned semantic seam, with exact paths, lines, and symbols.
- Do not propose new interfaces, helpers, adapters, or senders.

## Current state

- No source inspection has been performed in this subagent turn yet.
- Inherited evidence says the targeted `gap_followups_toast_oracle` run reached `audit_output_order_disconnect` and failed with expected `audit-before-boot`, then `*** Disconnected ***`, but actual output contained only `*** Disconnected ***`.
- No blocker is known. The next step is literal read-only inspection of the conformance row and current committed Banteng execution/output/disconnect path.

