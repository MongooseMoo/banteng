# Java 25 static-analysis decision

## Decision

Keep Error Prone 2.50.0, JSpecify 1.0.0, and NullAway 0.13.7 as blocking
checks in Banteng's Java 25 build. Keep `javac -Xlint:all -Werror` independently
blocking. Do not add a second nullness framework or replace nullable values
with indiscriminate `Optional` use.

This decision applies to Banteng-owned main and test Java sources. The only
task-level Error Prone exceptions are generated jcstress 0.16 and JMH 1.37
harness sources. Both exceptions are explicit in `build.gradle.kts`; javac
warnings-as-errors remain enabled for those compilation tasks.

## Representative compatibility surface

The pinned Java 25 build compiles representative language and nullness shapes,
not only trivial classes:

- sealed hierarchies and nested closed families in `moo.value.MooValue` and
  `moo.syntax.Ast`;
- records containing scalar, collection, and optional components in
  `MooValue`, `Ast`, `WorldObject`, `WorldVerb`, and `WorldProperty`;
- nested generic collections and futures throughout `WorldTxn`, `VmState`, and
  `MooRuntime`;
- package-level `@NullMarked` across every production ownership package and
  the shared test packages;
- explicit `@Nullable` picocli-injected fields in `moo.app.Banteng`;
- defensive-copy constructors and collection-return boundaries in the value,
  syntax, world, and persistence owners.

The Gradle Error Prone configuration promotes both `NullAway` and
`RequireExplicitNullMarking` to errors and enables NullAway's JSpecify mode
with `OnlyNullMarked`. Dependency locking and SHA-256 verification cover the
exact compiler/plugin graph.

## Java 25 proof

The accepted graph is:

- `com.google.errorprone:error_prone_core:2.50.0`;
- `com.uber.nullaway:nullaway:0.13.7`;
- `org.jspecify:jspecify:1.0.0`;
- `net.ltgt.errorprone` Gradle plugin 5.1.0.

On OpenJDK 25.0.3, the wrapper compiled main and test sources with
`--release 25`, `-Xlint:all`, `-Werror`, Error Prone, and NullAway enabled. The
full `./gradlew check` gate passed with dependency verification enabled. This
observed representative compilation is the Phase 1 compatibility authority;
future version changes require a new measured decision rather than inheriting
this approval.
