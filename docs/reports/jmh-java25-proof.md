# JMH 1.37 Java 25 proof

## Decision

Keep `org.openjdk.jmh:jmh-core:1.37` and
`org.openjdk.jmh:jmh-generator-annprocess:1.37` in an isolated `jmh` source set
for forked performance evidence. JMH does not replace correctness, property,
managed Toast conformance, fuzz, or Java Memory Model tests.

Upstream tag `1.37` is commit
`2effa2c8310e1d3ad03c8ee02024edca9252b46a`. It predates Java 25 and does not
certify that runtime. Banteng's bounded wrapper run below is the compatibility
proof for this pinned build.

## Build boundary

`jmh-core` is present on `jmhImplementation`, and the generator is present on
`jmhAnnotationProcessor`. Annotation processing generates the benchmark
harness classes plus `META-INF/BenchmarkList` and `META-INF/CompilerHints`.
The reproducible `jmhJar` task packages main classes and the complete JMH
source-set output, including both metadata files. Runtime dependencies remain
separate on the runner classpath.

JMH 1.37 does not mark its generated harness classes as generated. Error Prone
is therefore disabled only for `compileJmhJava`, with the reason stated in
`build.gradle.kts`. Mandatory javac `-Xlint:all -Werror` remains active for that
compilation task; all other Java compilation retains the blocking Error Prone
and NullAway gate.

Two forced `jmhJar` builds produced the same SHA-256:
`69498bb9b9d6f3c3aa6090b9bdc183d6aee2e899a7ea7729f29027ec6ab33ed8`.
The artifact contains both required metadata files, the benchmark class, and
all generated harness classes.

## Acceptance benchmark

`ParserBenchmark.parse` invokes the existing production entry point
`MooParser.parse("return 1;")` and returns the parsed program to JMH. The
explicit runner selects only
`^moo\.benchmark\.ParserBenchmark\.parse$`, uses one fork, one 100 ms warmup,
one 100 ms measurement, a 5-second cooperative iteration timeout, and
fail-on-error.

The verified WSL command imposed a separate 120-second wall-clock bound:

```text
timeout 120s env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin ./gradlew jmh
```

The run used JMH 1.37 on OpenJDK 25.0.3, started exactly one fork, completed
successfully, and reported 2,881,516.002 operations per second. That value is
only smoke evidence that measurement executed; it is not a comparative
performance claim. The separate full Java 25 `check` gate also passed.

Upstream build, generated-output, and runner contracts:

- <https://github.com/openjdk/jmh/blob/1.37/README.md>
- <https://github.com/openjdk/jmh/blob/1.37/jmh-core/src/main/java/org/openjdk/jmh/generators/core/BenchmarkGenerator.java>
- <https://github.com/openjdk/jmh/blob/1.37/jmh-core/src/main/java/org/openjdk/jmh/runner/BenchmarkList.java>
- <https://github.com/openjdk/jmh/blob/1.37/jmh-core/src/main/java/org/openjdk/jmh/runner/CompilerHints.java>
- <https://github.com/openjdk/jmh/blob/1.37/jmh-core/src/main/java/org/openjdk/jmh/Main.java>
- <https://github.com/openjdk/jmh/blob/1.37/jmh-core/src/main/java/org/openjdk/jmh/runner/options/CommandLineOptions.java>
