# jcstress 0.16 Java 25 proof

## Decision

Keep `org.openjdk.jcstress:jcstress-core:0.16` in an isolated `jcstress` source
set for Java Memory Model and publication tests. It is not a replacement for
JUnit, JetCheck, managed Toast conformance, or scheduler stress tests.

Upstream tag `0.16` is commit
`1120d412dd0a09d0cb4f7fa00de2a40057b8ed90`. The release improved JDK 19+
compatibility but does not specifically certify Java 25. Banteng's wrapper run
below is the Java 25 compatibility proof for this pinned build.

## Build boundary

`jcstress-core` is present on both `jcstressImplementation` and
`jcstressAnnotationProcessor`. Its `JCStressTestProcessor` generates harness
Java and `META-INF/TestList` in the source-set class output. The reproducible
`jcstressJar` task packages main classes and the complete jcstress output,
including that test list. Runtime dependencies remain separate on the runner
classpath.

The 0.16 generated harness does not pass the repository's Error Prone and
NullAway checks: it produces unused-variable, missing-override, empty-catch,
API-type, and explicit-null-marking findings. Error Prone is therefore disabled
only for `compileJcstressJava`, with the reason stated in `build.gradle.kts`.
Mandatory javac `-Xlint:all -Werror` remains active for that compilation task;
all other Java compilation retains the blocking Error Prone and NullAway gate.

The runner and its forked JVMs use Java 25 without preview features and with
`--enable-native-access=ALL-UNNAMED` for jcstress 0.16's JNA 5.8.0 dependency.
The explicit task selects only
`^moo\.jcstress\.VolatilePublicationTest$`, uses quick mode and one fork, and
writes reports under `build/reports/jcstress`.

## Acceptance test

`VolatilePublicationTest` publishes an integer payload and then a volatile
flag. It accepts observations before publication and the published `1, 42`
state. It forbids `1, 0`, because observing the volatile write must make the
preceding payload write visible.

The verified WSL command was:

```text
JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:$PATH ./gradlew jcstress
```

The run passed all 28 planned VM configurations with zero failed tests, zero
soft errors, and zero hard errors. Global and per-thread affinity, C1, C2,
`@Contended`, `Thread.onSpinWait`, compiler directives, and the configured C2
stress modes all probed successfully. The only unavailable probe was biased
locking, whose removed `UseBiasedLocking` VM option is not supported by Java 25.

Upstream build and runner contracts:

- <https://github.com/openjdk/jcstress/blob/0.16/README.md>
- <https://github.com/openjdk/jcstress/blob/0.16/jcstress-java-test-archetype/src/main/resources/archetype-resources/pom.xml>
- <https://github.com/openjdk/jcstress/blob/0.16/jcstress-core/src/main/java/org/openjdk/jcstress/Options.java>
- <https://mail.openjdk.org/pipermail/jcstress-dev/2023-February/001075.html>
