# DRY / duplication audit — banteng

Scope: `src/main/java/moo/` only (62 files, 34,878 LOC). Tests out of scope.
Read-only audit — nothing was edited. All counts are `grep -c` on the file named.

Ranked by payoff = (lines removed x divergence risk) / refactor risk.

---

## 1. Triplicated scanner primitives across the three DB readers — HIGH

~60 lines, byte-for-byte identical, in all three of:

- `src/main/java/moo/persistence/LambdaMooV4Reader.java:320-379`
- `src/main/java/moo/persistence/LambdaMooV5Reader.java:459-527`
- `src/main/java/moo/persistence/LambdaMooV17Codec.java:1765-1853`

Methods: `readCount`, `parseCount`, `readInt(BufferedReader)`, `readLong`, `parseLong`,
`readDouble` (V5 + V17), `requireExact`, `requiredLine`, `malformed(String)`,
`malformed(String, Throwable)`.

I diffed the three blocks. The *only* differences are the private name of the string-to-int
helper — V4 calls it `parseInt` (:336), V5 `readInt` (:475), V17 `readParsedInt` (:1800) — and
V4 lacking `readDouble`. Bodies are otherwise character-identical, including error message
formatting (`"invalid " + field + ": " + text`).

V17 also has `readPendingFinalizationCount` (:1769) which is exactly `readSectionCount` (:1779)
with the suffix string hardcoded.

**Consolidation:** extract package-private `moo.persistence.DbScanner` wrapping the
`BufferedReader` (instance methods drop the repeated `input` parameter too). Collapse
`readPendingFinalizationCount` into a `readSectionCount` call. Zero behavioural risk: the
implementations are already identical, so there is no semantic choice to make.

## 2. `encode`/`decode` ISO-8859-1 helpers redefined in six classes — HIGH (cheapest win)

The identical two-line helper is privately redeclared in six unrelated classes:

- `src/main/java/moo/builtin/BuiltinCatalog.java:6783` (`decode`) and `:6787` (`encode`)
- `src/main/java/moo/persistence/LambdaMooV17Codec.java:1014` (`latin1` — same body as `decode`, different name)
- `src/main/java/moo/runtime/MooRuntime.java:3192` (`encode`)
- `src/main/java/moo/server/MooServer.java:331` (`encode`)
- `src/main/java/moo/vm/MooVm.java:2631` (`encode`)

On top of that, the raw idiom is still inlined rather than routed through those helpers:
30 occurrences of `new String(<x>.bytes(), StandardCharsets.ISO_8859_1)` and 22 of
`getBytes(StandardCharsets.ISO_8859_1)` across 17 files. `MooRuntime.java` alone has 20
`StandardCharsets.ISO_8859_1` references.

**Consolidation:** put `String text()` and `static StringValue of(String)` on
`MooValue.StringValue` itself (`src/main/java/moo/value/MooValue.java`), delete all six private
helpers, and replace the inlined idioms. This is mechanical and the charset is invariant across
the whole codebase — the DB format, the wire protocol and the VM all use Latin-1.

## 3. BuiltinSpec registration boilerplate — HIGH (largest raw volume)

`src/main/java/moo/builtin/BuiltinCatalog.java` has 203 `entries.add(new BuiltinSpec(...))`
sites, each 8-12 lines. Within those:

- 143 pass `BuiltinPermissionRule.ANY`
- 167 pass `BuiltinCostRule.fixed(0)`
- 182 use the ten-parameter ignored-argument lambda `(a, w, p, t, id, rt, rs, r, cp, c) ->`
- 20 repeat the literal `List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))` verbatim

Representative sites: `:751-761` (`length`), `:787-795` (`acos`), `:860-868` (`cbrt`).

**Consolidation:** two changes that compose.
(a) Add a `BuiltinHandler` sub-interface taking only `List<MooValue>` (the existing interface at
`src/main/java/moo/builtin/BuiltinHandler.java` already demonstrates the default-method
widening trick), which removes the ten-parameter header from 182 lambdas.
(b) Add a small fluent registrar — `pure(name, shape, fn)` defaulting to ANY/`fixed(0)`/PURE/VM —
plus named CallShape constants (`FLOAT1`, `FLOAT2`, `ANY1`, `STR1`). Roughly 1,600 lines of the
7,370-line file are this boilerplate.

## 4. Type-dispatch arms in `MooVm.comparison` — HIGH (best ratio of lines removed to risk)

`src/main/java/moo/vm/MooVm.java:2040-2140`. Six-plus arms, each the same seven lines with only
the accessor swapped:

```java
if (leftValue instanceof IntegerValue left && rightValue instanceof IntegerValue right) {
  boolean result = switch (instruction.opcode()) {
    case LESS_THAN -> left.value() < right.value();
    case LESS_THAN_OR_EQUAL -> left.value() <= right.value();
    case GREATER_THAN -> left.value() > right.value();
    case GREATER_THAN_OR_EQUAL -> left.value() >= right.value();
    default -> throw new AssertionError(instruction.opcode());
  };
  frame.operandStack.push(new IntegerValue(result ? 1 : 0));
  frame.instructionPointer++;
  return;
}
```

Arms at `:2055` (Integer), `:2068` (Object), `:2081` (Float), `:2095` (Error, via `code()`),
`:2107` (String, via `compareIgnoringCase`). The Object and Float arms are textually identical
to the Integer arm.

**Consolidation:** reduce each type to an `int` three-way comparison, then apply one shared
opcode switch: `Integer cmp = threeWay(leftValue, rightValue); if (cmp == null) raise E_TYPE;`.
The String arm already computes an `int comparison`, so it is the shape the others should adopt.
~100 lines to ~25.

## 5. Duplicated `readValue` tag decoder — MEDIUM-HIGH

- `src/main/java/moo/persistence/LambdaMooV5Reader.java:422-447`
- `src/main/java/moo/persistence/LambdaMooV17Codec.java:1623-1681`

V5's switch (tags 0, 1, 2, 3, 4, 9) is a strict character-for-character subset of V17's,
including the `code & 0xffff_ffffL` error masking and the ISO-8859-1 string construction. V17
adds tags 10 (map), 12 (anonymous), 13 (waif), 14 (boolean) and threads a `ReadContext`.

Also duplicated between the readers: the raw record declarations
`ProgramSlot(long objectId, int verbIndex)` (V4 `:380`, V5 `:528`) and
`RawVerb(String names, long owner, int permissions, int preposition)` (V4 `:382`, V5 `:530`),
which are identical declarations. `RawObject` exists in all three with overlapping fields.

**Consolidation:** one decoder on the shared scanner from finding #1, taking a nullable
`ReadContext`; V5 passes none and the extended tags fall through to the existing
"unsupported value tag" error. Promote `ProgramSlot`/`RawVerb` to package-private top-level
records.

## 6. Inline wizard check competing with an existing helper — MEDIUM-HIGH (correctness risk, not just volume)

`src/main/java/moo/builtin/BuiltinCatalog.java` defines `isWizard(WorldTxn, long)` at `:5341`
and `WIZARD_FLAG = 4` at `:116`, but:

- `WIZARD_FLAG` is referenced only twice in the whole file
- `isWizard(world, programmer)` is called 9 times
- the hand-inlined equivalent `(actor.flags() & 4) != 0` appears 13 times with the magic literal,
  preceded 14 times by `WorldObject actor = world.object(programmer).orElse(null);`
  — e.g. `:5030`, `:5583`, `:5670`, `:5704`, `:5743`, `:5774`, `:5815`, `:5860`, `:5887`, `:6368`

So three spellings of one predicate coexist. Related magic numbers: `(targetFlags & 32) != 0`
for the write flag at `:5604` and `:5671`, with no named constant anywhere.

The flag constants themselves are duplicated across packages: `WIZARD_FLAG = 4` and
`ANONYMOUS_FLAG = 1 << 8` are declared in both `BuiltinCatalog.java:116,118` and
`src/main/java/moo/world/WorldTxn.java:32,33`; `WorldTxn` additionally declares `PLAYER_FLAG`
and `PROGRAMMER_FLAG` that `BuiltinCatalog` lacks.

**Consolidation:** one public `moo.world.ObjectFlags` holding the bit constants plus
`isWizard`/`isPlayer`/`isReadable`/`isWritable` predicates. Route all 13 inline sites through it.
This is the finding where duplication is actively a bug risk: a change to the wizard rule today
has to be made in three places and one of them uses a bare `4`.

## 7. Duplicated permission preamble in property/verb builtins — MEDIUM

`src/main/java/moo/builtin/BuiltinCatalog.java:5584-5607` (`add_verb`) and `:5649-5674`
(`add_property`) contain a ~20-line block that is identical apart from statement order:
resolve `receiver` to `(targetOwner, targetFlags)` across the `ObjectValue` /
`AnonymousObjectValue` / else-E_TYPE trichotomy, then compute
`writable = targetOwner == programmer || wizard || (targetFlags & 32) != 0` and return E_PERM.

Note the ordering difference is itself suspicious: `add_verb` reads `actor`/`wizard` *before*
resolving the receiver (`:5582`), `add_property` reads it *after* (`:5669`). Same outcome today,
but it means the two copies have already drifted structurally.

Broader context in the same file: 255 `return Result.error(ErrorValue.E_*)` statements, 32 of
them E_PERM; 59 `world.object(...)` lookups of which 21 are immediately followed by a
null-check returning E_INVARG.

**Consolidation:** `private static Result<TargetInfo> resolveTarget(MooValue receiver, WorldTxn)`
returning owner+flags or the error, plus `requireWritable(target, programmer)`.

## 8. Parallel object / anonymous-object method families in WorldTxn — MEDIUM

`src/main/java/moo/world/WorldTxn.java` carries 13 methods taking `AnonymousObjectValue identity`
that mirror an `long objectId` sibling: `addProperty` (`:1486` vs `:1513`), `addVerb` (`:1605` vs
`:1630`), `setVerbCode` (`:1681` vs `:1710`), `changeParents` (`:1293` vs `:1382`), plus
`property`, `verb`, `readObjectProperty`, `writeObjectProperty`.

`addProperty(long, ...)` at `:1486` and `addProperty(AnonymousObjectValue, ...)` at `:1513` share
an identical middle: the null-and-already-present guard, the
`while (propertyIndex < properties.size() && properties.get(propertyIndex).defined())` scan, and
the `properties.add(propertyIndex, new WorldProperty(name, value, owner, permissions, false, true))`
insert. Only the lookup and the write-back differ. The full six-field
`new WorldAnonymousObject(name, flags, owner, parents, verbs, properties)` rebuild is spelled out
11 times because the record has no `with*` helpers, mirroring 11 uses of `copyObject` on the
object side.

The root cause is that `WorldObject` (`src/main/java/moo/world/WorldObject.java:10-21`) and
`WorldAnonymousObject` (`src/main/java/moo/world/WorldAnonymousObject.java:7-13`) share six field
names — `name`, `flags`, `owner`, `parents`, `verbs`, `properties` — but no common supertype.

**Consolidation:** a sealed interface `PropertyHolder` over those six accessors lets the shared
middle become one generic method parameterised by a lookup and a write-back function. Add
`withProperties(...)` to both records to kill the 11 full-field rebuilds. Higher refactor cost
than #1-#5, so rank it below them, but it removes the most *semantic* duplication.

---

## Lower-priority observations

**VM raise-and-return / push-and-advance triads.** `src/main/java/moo/vm/MooVm.java`: 88
`frame.instructionPointer++;`, 105 `raiseError(...); return;` pairs, ~70 push/advance/return
triads. `firstIndex` (`:901-929`) and `lastIndex` (`:931-964`) are structurally identical — same
three `instanceof` arms, same empty-check-then-E_RANGE — differing only in which endpoint is
pushed; they merge into one method taking an endpoint enum. `index` (`:840-899`) repeats the
`if (parentDepth == 1) { push IndexContext } push value; ip++; return;` block three times
(`:857`, `:871`, `:890`). The real fix is opcode handlers returning a `Step` value instead of
mutating and returning `void`, which is a large change; `pushAndAdvance(frame, value)` and
`raiseAndStop(...)` helpers are the cheap partial.

**Arithmetic integer/float arms.** `src/main/java/moo/vm/MooVm.java:1916-1997`. The integer arm
(`:1916`) and float arm (`:1963`) repeat the same divide-by-zero guard and the same
zero-base-negative-exponent guard before diverging into genuinely different `switch` bodies. Only
the ~12 guard lines are worth extracting; the operation bodies legitimately differ (integer
`REMAINDER` uses `(a % b + b) % b`, float uses a `copySign` correction).

**Server-message-with-fallback block.** `src/main/java/moo/runtime/MooRuntime.java` has five
near-identical ~25-line blocks at `:1288`, `:1317`, `:1534`, `:2624`, `:2697`: read the listener's
`server_options`, read a `*_msg` property, fall back to #0's `server_options` when null and
listener != 0, then flatten StringValue-or-ListValue into `List<String>`. Only the property name
and the default sentence differ (`redirect_from_msg`, `redirect_to_msg`, `timeout_msg`). 19
`"server_options"` reads in the file total. Collapses to
`private List<String> serverMessage(long listener, String property, String fallback)`.

**Unary-float math template.** `src/main/java/moo/builtin/BuiltinCatalog.java:3422-3560`. 21 sites
use `((FloatValue) arguments.getFirst()).value()`; 18 end with the identical tail
`Double.isFinite(result) ? Result.value(new FloatValue(result)) : Result.error(ErrorValue.E_FLOAT)`.
`cbrt` (`:3503`), `ceil` (`:3510`), `cosh` (`:3528`), `exp` (`:3535`) are the same five lines with
only the `Math.*` call swapped; `acos` (`:3422`) and `asin` (`:3447`) differ only in `Math.acos`
vs `Math.asin`. A `floatOp(args, DoubleUnaryOperator)` plus a `domainGuard` variant taking a
`DoublePredicate` covers all 18. Folds naturally into finding #3.

**Parameter-object smell in PublicationScheduler.** `src/main/java/moo/runtime/PublicationScheduler.java`
threads the 4-tuple `(List<SuspendedStackSlot> baseToTop, boolean[] consumed, int depth, String
description)` through 9 methods (`:611`, `:624`, `:636`, `:646`, ...). Not duplication as such —
`requireControl`/`requireExactValue`/`requireValue`/`requireSlot` are a well-factored chain — but
the repeated signature wants a small `SlotCursor` object. Low priority.

**Not duplication, noting for completeness.** The `import*`/`export*` mirror pairs in
`PublicationScheduler` (`importActivation` `:284` / `exportActivation` `:1102`,
`importFinallyState` `:514` / `exportFinallyState` `:1001`, `importCollectionLoop` `:555` /
`exportCollectionLoop` `:1054`, `importRangeLoop` `:591` / `exportRangeLoop` `:1088`) are inverse
functions, not copies. They are a divergence risk — an added field must be handled in both — but
consolidating them means a declarative field table, which is a bigger design change than anything
above and probably not worth it.

---

## Suggested order of work

Findings 1, 2 and 5 are mechanical and provably behaviour-preserving (the code being merged is
already byte-identical), so they are the safe opening move and clear roughly 400 lines. Finding 4
is next: contained to one method, big reduction, easy to test. Finding 6 should be done for
correctness reasons regardless of line count. Findings 3, 7 and 8 are larger and want their own
change each.
