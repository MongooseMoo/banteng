# Phase 1 Java skeleton proof

## Decision

Phase 1's Java skeleton and architectural guardrails are complete on committed
`04666a0`. Continue with the first unchecked Phase 2 authority artifact; do not
reopen Phase 1 without a failing named gate or an explicit plan change.

## Deliverable evidence

- `gradle/wrapper/gradle-wrapper.properties`, `build.gradle.kts`,
  `gradle.lockfile`, and `gradle/verification-metadata.xml` pin Gradle 9.6.1,
  its distribution checksum, Java 25, `--release 25`, dependency locks, and
  dependency SHA-256 values without preview features.
- `moo.app.Banteng` is the concrete picocli composition root with the planned
  database, checkpoint, listener, port, and log-level surface plus validated
  usage and stable picocli exit codes.
- Package annotations and `ArchitectureTest` establish the ownership map,
  allowed dependency directions, and cycle rejection.
- The wrapper owns JUnit 6.1.2, accepted JetCheck 0.3.0 properties, bounded
  Jazzer 0.30.0 fuzzing, isolated jcstress 0.16 publication tests, a forked JMH
  1.37 artifact, and concrete owner-local JFR event definitions. Detailed
  acceptance records are in `jetcheck-acceptance-spike.md`,
  `jazzer-java25-junit6-proof.md`, `jcstress-java25-proof.md`, and
  `jmh-java25-proof.md`.
- Spotless pins google-java-format 1.35.0 and enforces import order, no wildcard
  imports, trailing-whitespace removal, and final newlines.
- `java25-static-analysis-decision.md` accepts the exact blocking Error Prone,
  JSpecify, and NullAway graph after representative Java 25 compilation.
- `BantengExecutableSmokeTest` starts the installed application script as a
  separate process, verifies the exact version, and requires exit zero within
  ten seconds without a database.

No framework, storage abstraction, plugin system, dependency-injection
container, or reactive networking stack was introduced.

## Reproducible distribution proof

Two independent forced builds used:

```text
JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin ./gradlew distZip --rerun-tasks
```

Both `build/distributions/banteng-0.1.0-SNAPSHOT.zip` artifacts had SHA-256:

```text
c19898f41777e3ab0168cbe792cc67797fe792e3e64213724c79ca8b8a16fcd1
```

The distribution includes the main JAR, runtime dependency set, and generated
start scripts, so the identical ZIP proves the complete application
distribution was byte-for-byte reproducible across forced rebuilds.

## Clean-checkout wrapper gate

A detached sibling Git worktree at committed `04666a0` preserved the plan's
declared `../moo-conformance-tests` fixture topology. No untracked Banteng
notes or primary-worktree build output was present. The wrapper commands were
run separately:

```text
./gradlew clean
./gradlew check
```

Both passed on OpenJDK 25.0.3. The clean `check` rebuilt or restored from the
verified Gradle cache the Java compilation, JAR, start scripts, installed
distribution, test compilation, all JUnit tests, the separate-process
executable smoke, blocking Error Prone/NullAway checks, javac
`-Xlint:all -Werror`, and Spotless checks. Dependency verification remained
enabled. The temporary worktree was removed through Git after the pass.

An earlier attempt nested the clean worktree below Banteng's `build` directory
and therefore broke the declared sibling fixture topology. Its fixture-path
failures were invalid setup evidence and caused no source change; the correctly
located clean sibling worktree is the gate authority.
