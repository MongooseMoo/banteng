# Row 26 committed-path trace

## Current assignment

- Read-only trace at committed Banteng revision `a2ae743` for exact lifecycle row `audit_telnet_iac_delivered_as_oob_command`.
- The row sends bytes `FF` followed by `FB 01`, expects the OOB command bytes to render as `~FF~FB~01`, and currently observes `{}`.
- Trace the existing `afterIac` / `afterNegotiation` receive path and committed `executeTransportOutOfBand` behavior.
- Identify the smallest extension in existing concrete owners plus a focused real-socket regression.
- No new class, interface, helper, adapter, sender, or general encoder.
- No edits to production/test code, no servers, and no Barn or Toast work.

## Findings and observations

- None yet; the checkpoint arrived before the first repository inspection in this restarted subagent turn.

## What has been tried

- No code or tests have been inspected or run yet.

## Current state and blocker

- State: ready to begin the authorized read-only committed trace.
- Blocker: none.

## Next step

- Verify the branch/revision and tracked-file state, then inspect only the exact receive/OOB path and existing real-socket tests needed to answer the assigned design question.
