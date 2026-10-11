# Phase 4 huh command dispatch

## Current target

- Exact Phase 4 family: `src/moo_conformance/_tests/server/command_parsing.yaml`.
- Corrected full Banteng Phase 4 baseline: 66 passed, 4 failed, 1 skipped.
- All four failures emit `I couldn't understand that.` instead of invoking the selected player/location `huh` verb.

## Authority and findings

- The exact seven-path Toast run passes 70/71 with only the profile-defined OOB skip.
- Pinned Toast source is `/root/src/toaststunt` at `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Toast `src/tasks.cc:844-856` searches normal command verbs, then selects location `huh` when `$server_options.player_huh` is absent/false, or player `huh` when it is true.
- Toast `src/execute.cc:3340-3371` invokes that `huh` program with `this` set to the chosen player/location while retaining the parsed original command locals (`verb`, `args`, `argstr`, direct/indirect object fields).
- Banteng `MooRuntime.executeLineNow` already creates those parsed locals, but its command-dispatch block runs only when a player/location verb matching the unknown command name exists. A genuinely unknown command therefore bypasses the existing room-`huh` lookup.
- Banteng's existing fallback always chooses room `huh` and never reads `$server_options.player_huh`.

## State and next action

- Banteng `master` is clean and synchronized with `origin/master` at `002f489` before this slice; only untracked notes/investigations exist.
- No source edit has been made for this target.
- Next action: incorporate the read-only subagent traces, verify fixture validity, then make one minimal `MooRuntime` source/test slice that selects the Toast-authorized `huh` target and allows unknown commands into the existing stored-verb invocation path.

## Current implementation state

- The first conformance row was invalid: it relied on the starting database's `player_huh = 0` while claiming the property was absent, and it did not assert setup success.
- A first isolation attempt caught only `E_INVARG`; pinned Toast showed that an absent property raises `E_PROPNF`, aborting setup before the verb was installed.
- The corrected row catches `ANY`, asserts setup result `1`, explicitly removes `player_huh`, and verifies Toast's compiled default-0 location dispatch. Pinned Toast passes all four rows in 5.44 seconds.
- The corrected fixture is committed locally in `C:/Users/Q/code/moo-conformance-tests` as `f9f8503 fix: isolate default huh dispatch`; that repo is 42 commits ahead of its remote and has not been pushed.
- Banteng now has one uncommitted source/test slice: `MooRuntime` preselects exactly one `huh` receiver from integer `player_huh`, admits unknown commands into the existing stored-verb path, and `MooRuntimeTest` covers absent, zero, nonzero, and non-integer option values.
- The first red unit run failed at the expected unknown-command assertion. After the source edit, the unit test still failed at that assertion.
- The test was strengthened to assert setup output. Its latest run fails at the setup assertion on `MooRuntimeTest.java:117`, proving the source branch is not yet under test because test setup aborts.

## Current blocker and next action

- Blocker: exact setup error from `build/test-results/test/TEST-moo.runtime.MooRuntimeTest.xml` has not yet been read.
- Next action: read that failure value, correct only the unit-test setup, rerun the focused test, then validate the corrected four-row Banteng family and exact Java gate.

## Verified huh result

- The unit-test setup failure was a Java text-block escaping error; the MOO verb source lost the escaped quotes and failed to parse. The test now asserts setup and every option mutation.
- The focused `MooRuntimeTest.dispatchesUnknownCommandsThroughSelectedHuhVerb` regression passes in 13 seconds.
- The corrected Banteng `command_parsing.yaml` family passes 4/4 in 5.37 seconds.
- The exact full Java gate passed once with all 252 tests in 36 seconds before the final assertion strengthening.
- After the assertion-only test strengthening, the focused regression still passes, but the exact full Java gate again fails only `PublicationSchedulerTest.killedDelayedForkNeverPublishesItsWorldMutation` (`expected 0`, actual `99`).
- That delayed-fork test has now failed in three full-suite runs and passed on exact single-test reruns. It predates and is independent of the two-file huh diff; it is a separate Phase 4 scheduler target, not part of this source slice.
- Current huh slice is exactly `MooRuntime.java` and `MooRuntimeTest.java`; `git diff --check` passed before the final assertion strengthening and must be rerun before commit.
- Next action: commit/push the independently passing huh slice, then begin a separate delayed-fork cancellation slice so the exact Java gate can become reliable.
