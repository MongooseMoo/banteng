# Jazzer Java 25 and JUnit 6 proof

## Decision

Keep `com.code-intelligence:jazzer-junit:0.30.0` for explicit and nightly
coverage-guided fuzzing. Banteng's pinned build successfully runs Jazzer's JUnit
integration on Java 25 with all Jazzer-requested JUnit 5.9.0 and Platform 1.9.0
artifacts constrained to JUnit 6.1.2 by the existing BOM.

Upstream tag `v0.30.0` is commit
`dd9416b74d263b56fc848cd782b061baf5f962a6`. That release builds against the
JUnit 5.9-era API and does not itself establish Java 25/JUnit 6 compatibility;
the Banteng runs below are the compatibility proof for this pinned build.

## Tasks and target

Ordinary `test` forces `JAZZER_FUZZ=0`, so every `@FuzzTest` runs its checked-in
inputs as deterministic regressions. The explicit `fuzzTest` task selects only
`moo.syntax.MooParserFuzzTest.parsesArbitraryLatin1`, forces `JAZZER_FUZZ=1`,
uses one fork, and is
never considered up to date. It is not attached to the ordinary `check`
lifecycle.

`MooParserFuzzTest` decodes arbitrary bytes as ISO-8859-1 and calls the existing
`MooParser.parse(String)` entry point. A `MooParser.ParseException` is normal
malformed-input rejection; any other uncaught failure is a Jazzer finding. The
checked-in `return 1;` seed lives at Jazzer's standard regression-input path.

Generated coverage inputs are written under `.cifuzz-corpus/` and ignored.
Jazzer writes a finding to the target's inputs directory; the required workflow
is to minimize it, promote the smallest input there as a checked-in regression,
prove that regression red, and only then change production code.

## Verified commands

Both commands ran through the Gradle wrapper under WSL Java 25 with dependency
locking and checksum verification enabled:

```text
./gradlew test --tests moo.syntax.MooParserFuzzTest
./gradlew fuzzTest
```

Regression mode passed. The bounded coverage-guided run loaded the checked-in
seed, executed 28,250 inputs in seven seconds, reached 488 coverage counters and
1,540 features, retained 369 corpus entries, and completed without a finding.
