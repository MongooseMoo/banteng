# Java and OTS library review

## Question

Which current Java platform features and off-the-shelf libraries should Banteng
use, which behaviors must remain Banteng-owned for Toast conformance, and what
changes does that imply for the implementation process?

This review was performed on 2026-07-13. Version numbers are a verified starting
snapshot, not permission to upgrade without rerunning the relevant compatibility
gate.

## Selection rule

Use an OTS dependency when it implements commodity infrastructure whose behavior
is outside MOO's observable contract. Keep a concrete Banteng owner when the
behavior defines MOO values, syntax, execution, scheduling, persistence, or a
Toast builtin contract. Wrap a selected library only at that owned semantic
boundary; do not introduce generic adapters or provider interfaces.

## Verified Java baseline

- Java 25 is GA and is the current LTS-era baseline. Preview features remain out
  of scope. OpenJDK: <https://openjdk.org/projects/jdk/25/>.
- Gradle 9.6.1 runs on Java 25; Java 25 runtime support begins with Gradle 9.1.0.
  Use the wrapper, a Java 25 toolchain, `--release 25`, a pinned distribution
  checksum, dependency locking, and dependency verification.
  <https://docs.gradle.org/current/userguide/compatibility.html>
- Virtual threads are for connection and blocking-host-operation scale, not for
  CPU speed. CPU-bound MOO segments still require a bounded platform-thread
  executor. The durable task remains explicit VM data.
- `System.Logger`, `java.net.http.HttpClient`, `java.util.Base64`, JCA/JCE,
  `SecureRandom`, NIO, `FileChannel`, and `java.util.concurrent` cover the basic
  host facilities without third-party frameworks.

## Adopt in the skeleton

| Need | Choice | Boundary and reason |
| --- | --- | --- |
| Build | Gradle Wrapper 9.6.1 | One project initially; wrapper-only build and pinned verification metadata. |
| Unit/integration tests | JUnit 6.1.2 | JUnit 6 is the current generation; use Jupiter APIs and no Vintage engine. <https://docs.junit.org/current/user-guide/> |
| Hypothesis-like properties | JetCheck 0.3.0 | Automatic minimization, exact recheck tokens/seeds, recursive generators, and single-threaded stateful command tests. It is framework-independent and its own CI runs Temurin Java 25. <https://github.com/JetBrains/jetCheck> |
| Package rules | ArchUnit 1.4.2 | Enforce the ownership map and package-cycle rules in ordinary tests. <https://www.archunit.org/> |
| Formatting | Spotless plus google-java-format 1.35.0 | One deterministic formatter and import policy. Do not use Java `///` Markdown documentation comments until google-java-format's open JDK 23+ handling issue is resolved. <https://github.com/diffplug/spotless> |
| CLI | picocli 4.7.7 | Concrete `moo.app` command parsing, validation, usage, and exit codes. It is a library at the composition root, not a runtime framework. <https://picocli.info/> |
| Microbenchmarks | OpenJDK JMH 1.37 | Forked benchmarks only; do not treat in-process timings or JUnit timings as performance evidence. <https://central.sonatype.com/artifact/org.openjdk.jmh/jmh-core> |
| Memory-model tests | OpenJDK jcstress 0.16 | Publication primitives and commit visibility only. It is distinct from generated model tests. <https://central.sonatype.com/artifact/org.openjdk.jcstress/jcstress-core> |
| Fuzzing | Jazzer JUnit integration 0.30.0 | Nightly or explicit coverage-guided fuzzing for lexer/parser, Telnet bytes, v4/v17 input, and JSON boundaries. Minimized crash inputs become checked-in regressions. <https://github.com/CodeIntelligenceTesting/jazzer/releases/latest> |

JetCheck must first pass a real Banteng spike: generate nested MOO values and a
stateful `WorldTxn` command sequence, demonstrate minimization, reproduce the
failure from the emitted token, and run under the Gradle/JUnit 6 build. Its own
documentation says the generator catalog is intentionally not feature-complete;
the spike decides whether its API is sufficient before it becomes permanent.

Do not use jqwik. Starting with 1.10, its maintainers explicitly state that AI
coding agents must not use the library and intentionally emit an anti-agent line
on every test-engine invocation. <https://jqwik.net/docs/current/user-guide>

QuickTheories is not the fallback: its latest tagged release remains 0.25 and it
does not displace JetCheck on the required state-machine/minimization/recheck
spike. `junit-quickcheck` publishes no GitHub releases. If JetCheck fails the
spike, record the failed requirement and reassess maintained libraries then;
do not preserve momentum by building a Banteng property framework.
<https://github.com/quicktheories/QuickTheories/releases/latest>
<https://github.com/pholser/junit-quickcheck/releases>

## Static analysis

Keep `javac -Xlint:all -Werror`, explicit suppressions, and ArchUnit. Phase 1
proved Error Prone 2.50.0 on the pinned Java 25/Gradle toolchain and retained it
as a blocking check. Error Prone currently requires JDK 21+ and hooks javac
internals.
<https://errorprone.info/docs/installation>
<https://central.sonatype.com/artifact/com.google.errorprone/error_prone_core/versions>

JSpecify 1.0.0 annotations plus NullAway 0.13.7 are retained as a blocking gate
after representative Java 25 compilation of sealed types, records, nested
generics, package annotations, and explicit nullable fields. NullAway's broader
JSpecify support remains in progress, so this approval applies only to the
pinned graph and observed Banteng surface. Do not introduce `Optional` for
every nullable internal field. The exact decision and proof are recorded in
`java25-static-analysis-decision.md`. <https://github.com/jspecify/jspecify/releases>
<https://github.com/uber/NullAway/releases/latest>

## Production dependencies by boundary

### Use or profile-gate

| Surface | Choice | Constraint |
| --- | --- | --- |
| JSON builtins | Jackson 3.1 LTS `jackson-core` streaming API | Map tokens explicitly to MOO values. Do not use reflective databind or serialize domain objects. <https://github.com/FasterXML/jackson/wiki/Jackson-Releases> |
| SQLite builtins | xerial `sqlite-jdbc` 3.53.2.0 | Enable only in a selected profile. Own handle lifecycle, permissions, MOO conversions, suspension/effect classification, and resource limits. The driver includes native libraries. <https://github.com/xerial/sqlite-jdbc> |
| Password crypto | Bouncy Castle Java 1.84 provider/lightweight API | Profile-gated Argon2 and OpenBSD bcrypt support; keep Toast's exact encodings, options, errors, and limits in Banteng. <https://www.bouncycastle.org/> |
| Unix `crypt()` variants | Apache Commons Codec 1.22.0 | Candidate for DES, MD5, SHA-256, and SHA-512 crypt variants; prove byte encoding and output against Toast before enabling. It does not replace bcrypt/Argon2. <https://commons.apache.org/proper/commons-codec/apidocs/> |
| PCRE builtins | Native PCRE2 through a narrow Java 25 FFM binding | Toast calls PCRE2 directly (`../../src/toaststunt/src/pcre_moo.cc:3-18,91-126,135-317`). Java `Pattern`, RE2/J, and Joni are not semantic substitutes. If native packaging is not proven, exclude the PCRE profile rather than approximate it. |

### Prefer the JDK

- Blocking sockets plus virtual threads and a Banteng-owned byte Telnet state
  machine. Netty, reactive streams, and generic Telnet frameworks add a second
  lifecycle without owning Toast's split-IAC and output-order semantics.
- `HttpClient` for HTTP builtins, with the builtin owner enforcing redirects,
  timeouts, byte/string conversion, suspension, and permission behavior.
- JCA/JCE and `SecureRandom` for standard hashes, MACs, and secure bytes.
- `System.Logger`/JUL plus JFR initially. A logging facade/backend is unnecessary
  until an operator requirement that JUL cannot meet is recorded. Do not place
  task correctness or MOO-visible logs in an asynchronous logging backend.
- NIO and `FileChannel` for database/checkpoint I/O. Atomic replacement must fail
  clearly when the filesystem cannot provide the promised operation; do not
  silently degrade to a non-atomic move.
- The Gradle application distribution rather than a shaded fat JAR. This avoids
  service-resource and native-library shading surprises.

## Keep Banteng-owned

- MOO tagged values, equality, ordering, hashing, truth, literal form, and 1-based
  collection behavior.
- MOO lexer/parser/compiler/bytecode/VM. ANTLR, ASM, host continuations, Java
  serialization, and expression engines do not own the language contract.
- Legacy `match()`/`rmatch()` patterns. Toast's PCRE2 extension is a separate
  builtin family (`../barn/spec/builtins/regex.md:276-295`).
- Immutable committed world records, `WorldTxn`, validation, scheduler tickets,
  effect journals, retry rules, and checkpoints.
- v4/v17 streaming codecs and explicit suspended-task state. Jackson, Kryo,
  protobuf, SQL, and object serialization cannot represent the authority.
- Telnet byte parsing and connection output ordering.

## Collection representation

Do not adopt Vavr, PCollections, Guava immutable collections, or a primitive-map
library at project start. MOO list/map semantics and the world publication shape
must be measured first. Vavr 1.0.1 is current and now stable, but it imports a
large functional vocabulary and does not by itself prove the desired allocation
or commit behavior. <https://github.com/vavr-io/vavr/releases/latest>

Start with concrete Banteng value types and JDK storage. Benchmark candidate
persistent maps or primitive collections only when a measured copy/allocation
site identifies the exact replacement boundary. A kept dependency must improve
the named workload without leaking its types across the owning package.

`MooString` should be an immutable Latin-1 byte value, not an unconstrained Java
`String`. A record containing `byte[]` is not deeply immutable and gets reference
equality for the array, so byte-owning and array-owning values should be final
classes with defensive ownership and explicit equality/hash/order. Records remain
appropriate for genuinely immutable scalar carriers and AST nodes.

## Process corrections applied to the implementation plan

1. Phase 2 now uses thin language slices that cross lexer, AST, compiler,
   disassembler, and VM together instead of completing those layers
   horizontally. The order starts with literals/return, then variables and
   arithmetic, collections, control flow, calls/errors, and serializable fork
   state.
2. Phase 3 now starts with the simplest correct fixed-snapshot revision
   publication and atomic ticket-ordered commit. Commit descriptors remain out
   until the Phase 6 benchmark shows a material kept gain.
3. The first managed end-to-end row now precedes broad codec, world, and server
   expansion: boot the disposable bundled fixture, connect, evaluate, checkpoint,
   restart, and evaluate again through the same production path.
4. The plan now assigns distinct proof obligations to examples, JetCheck
   properties/models, managed Toast differential rows, Jazzer, jcstress, and
   JMH. A narrower tool cannot substitute for a later named gate.

## Recommended dependency policy

- Centralize versions, lock dependencies, verify checksums/signatures where the
  build supports them, and commit the wrapper checksum and verification metadata.
- No dynamic versions, snapshots, transitive logging backends, or automatic
  runtime plugin discovery.
- Every production dependency needs a recorded owner, profile, license,
  MOO-observable behavior review, and removal criterion.
- Reverify the pinned version during the Phase 1 spike. Current-version research
  is not a substitute for compiling and running the exact dependency graph.
