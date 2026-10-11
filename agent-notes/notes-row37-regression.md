# Row 37 regression handoff

- Scope: edit only `src/test/java/moo/runtime/MooRuntimeTest.java`; no production, docs, staging, or commit.
- Required durable behavior: a logged-in Wizard creates a thing and destination, installs executable `accept` on the destination that records the moved object and returns `0`, calls `move`, then evaluates both the observation and final location.
- Required exact result: `{1, {1, 1}}` (using the existing connection-marker convention or its exact equivalent).
- Required red: committed production must report `accept` observation `0` while the location assertion remains `1`; setup or parsing failures are invalid.
- Implemented `invokesDestinationAcceptWhenMoveReturnsFalse` in the required test file only.
- Java 25 command: `.\\gradlew.bat test --tests moo.runtime.MooRuntimeTest.invokesDestinationAcceptWhenMoveReturnsFalse` with `JAVA_HOME=C:\\Users\\Q\\scoop\\apps\\openjdk\\current`.
- Valid committed-production red: expected marker payload `{1, {1, 1}}`; actual `{1, {0, 1}}`. The leading `1` proves eval success, the `0` proves `accept` did not observe the moved object, and the final `1` proves the object's location still changed to the destination.
- An initial invalid test shape duplicated the seam's success marker; it was corrected before production work. The valid rerun is the result above.
- `git diff --check -- src/test/java/moo/runtime/MooRuntimeTest.java` is clean.
- Tracked diff is only `src/test/java/moo/runtime/MooRuntimeTest.java`; existing untracked notes and `pyghidra_mcp_projects/` remain untouched apart from this required handoff note.
- Nothing was staged or committed.
- Current blocker: production does not dispatch the destination's executable `accept` hook during `move`; the parent agent owns the production slice.
