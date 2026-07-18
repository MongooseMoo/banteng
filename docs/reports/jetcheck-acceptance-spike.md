# JetCheck acceptance spike

## Decision

Keep `org.jetbrains:jetCheck:0.3.0` as Banteng's property and single-threaded
state-model test library. It runs inside the pinned JUnit 6.1.2 wrapper build on
Java 25 and satisfies the Phase 1 acceptance requirements without a JetCheck
test engine or a Banteng-owned property framework.

## Nested-value minimization and replay

The temporary red form of `moo.JetCheckAcceptanceTest` generated a recursive
scalar/list/map `MooValue` payload inside a depth-two list and rejected values
with nesting depth two or greater. The committed `JetCheckAcceptanceTest` is
the sole authority for the exact minimized value and serialized replay token.
It catches the expected `PropertyFalsified`, asserts the shrinking result, and
rechecks the identical property. The temporary deliberately failing form is
not part of the normal suite.

## Stateful WorldTxn spike

The same JUnit test runs 100 JetCheck scenarios. Every scenario constructs a
fresh concrete `WorldTxn` and a separate ordered model of live object IDs, then
generates a state-dependent sequence of one to thirty create or recycle
commands. Each command checks object count, membership, and recycled-object
absence immediately after the operation. No production interface or test
framework was added.

## Reproduction

Run through the wrapper under Java 25:

```text
./gradlew test --tests moo.JetCheckAcceptanceTest
```

The acceptance run passes with dependency locking and checksum verification
enabled. JetCheck's seed and serialized-recheck methods are deprecated
diagnostic APIs; the test contains a method-scoped suppression with the Phase 1
replay requirement stated immediately beside it. `-Xlint:all -Werror`, Error
Prone, and NullAway remain enabled.

Official API evidence was read from the JetBrains `jetCheck` repository at
commit `f3e738471d117cf11f596221181d6dd8bb91510a`, whose POM identifies version
0.3.0.
