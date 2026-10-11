# God-class decomposition audit

Scope: the 7 largest classes total **21,882 of 34,878 lines = 63%** of `src/main/java`.
The other 55 files average 236 lines. Read-only audit; nothing was modified.

Recurring theme across all seven: **the abstractions already exist and are clean**
(`BuiltinSpec`, `BuiltinHandler`, `CallShape`, records everywhere). What is missing is
not design — it is *file boundaries*. Most of the recommended work is moving already-
cohesive, already-static code out of a host class, not redesigning it.

---

## 1. `BuiltinCatalog.Result` — 506-line record with 22 Optional components
`src/main/java/moo/builtin/BuiltinCatalog.java:6864-7370`

The single worst construct in the repo, and the easiest to fix.

- 22 record components (`BuiltinCatalog.java:6864-6885`), 21 of which are
  `Optional`/`OptionalLong`/`OptionalDouble`; only `abortSeconds` is a plain field.
- 3 telescoping *private* constructors (`:6887`, `:6931`, and a third) that each
  re-list 18-21 parameters positionally purely to default trailing components.
- 19 static factories (`Result.value` `:7011`, `.error` `:7138`, `.hostWork` `:7155`,
  `.suspend` `:7122`, `.move` `:7307`, `.recycle` `:7329`/`:7345`, …), each spelling
  out all 22 components positionally.
- **261 literal `*.empty()` calls inside this one 506-line block.**

It is `public`, referenced by 9 files across `runtime`, `vm`, `persistence`, and
`server` — so every consumer of the VM's builtin protocol imports the 7370-line
god class to name its core return type.

**Action:** move to `moo.builtin.BuiltinResult` as its own file; replace the
telescoping constructors and positional factories with a builder or `withX()`
withers. Collapses ~450 of the 506 lines, deletes ~260 `empty()` literals, and
decouples 9 files from `BuiltinCatalog`. Pure mechanical refactor, no behavior change.
**Highest maintainability-per-risk in the codebase.**

## 2. `BuiltinCatalog` — 20 telescoping public constructors
`BuiltinCatalog.java:147-581` (~435 lines, 6% of the file)

Two parallel families of 10, differing only in whether the first parameter is a
`ListenerControl`:
- `:147`, `:157`, `:167`, `:183`, `:201`, `:221`, `:243`, `:267`, `:293`, `:321`
- `:357`, `:368`, `:379`, `:397`, `:417`, `:439`, `:463`, `:489`, `:517`, `:547`

They feed 20 instance fields (`:125-144`), 14 of which are `BuiltinHandler` host
hooks (`connectionOptions`, `dbDiskSize`, `flushInput`, `killTask`,
`outputDelimiters`, `queueInfo`, `queuedTasks`, `resumeTask`, `taskStack`, `read`,
`threadPool`, `threads`, …). The javadoc has degenerated — four separate
constructors are documented "Creates a catalog with every transient host operation
and reader" (`:243`, `:267`, `:463`, `:489`).

**Action:** one `BuiltinHosts` parameter object (record, all fields defaulting to
no-op handlers) plus a builder. 20 constructors collapse to 1-2.

## 3. `BuiltinCatalog.buildManifest()` — 1781 lines in one method
`BuiltinCatalog.java:583-2364`

Largest single method in the codebase; registers 203 entries. **The good news: it
is already grouped by builtin family**, with crisp contiguous boundaries:

| family | line range |
|---|---|
| value/length | 743-783 |
| math | 784-1037 |
| random | 1038-1080 |
| time | 1081-1108 |
| error/raise | 1109-1117 |
| list | 1118-1167 |
| map | 1168-1209 |
| json | 1210-1227 |
| set | 1228-1272 |
| string | 1273-1393 |
| binary/codec | 1394-1429 |
| property | 1430-1509 |
| object lifecycle | 1510-1585 |
| object query | 1586-1667 |
| verb | 1668-1742 |
| move/recycle | 1743-1760 |
| waif | 1761-1797 |
| task/perms | 1798-1902 |
| connection | 1903-1998 |
| listener | 1999-2040 |
| task control | 2041-2094 |
| conversion | 2095-2175 |
| server info | 2176-2224 |
| logging/stats | 2225-2308 |
| suspend/shutdown | 2309-2363 |

`addFileIoManifest` (`:2366`) and `addSqliteManifest` (`:2412`) already demonstrate
the extraction pattern — those two families were pulled out and delegate to
`FileIoService`/`SqliteService`. **The seam is proven; it just was not applied to
the other 25 families.**

The 196 `private static Result xxx(...)` implementations (`:2489-6790`) partition
along exactly the same family lines (e.g. all math at `:3414-3620`).

**Action:** one class per family implementing a `registerInto(List<BuiltinSpec>)`,
following the existing `addFileIoManifest` precedent. ~25 files of 150-300 lines.
Mechanical and independently testable family by family.

## 4. `BuiltinHandler`'s 10-parameter signature forces 34-line anonymous classes
`BuiltinHandler.java:11-21`; instances at `BuiltinCatalog.java:585-617`, `:618-650`,
`:651-681`, `:682+`

Because the interface takes 10 positional parameters, every handler that needs the
two-arg `threadMode` overload must be a full anonymous class re-declaring both
10-parameter signatures — ~34 lines of pure boilerplate to route to a one-line call.
`setThreadMode` (`:585`), `allMembers` (`:618`), and `sort` (`:651`) each spend
~33 lines to delegate to a single method call. The signature is repeated 14 times in
`BuiltinCatalog`, and also in `MooRuntime`, `PublicationScheduler` (x2),
`TaskRegistry` (x4), `VmSnapshot`.

**Action:** replace the 10 parameters with a single `BuiltinCall` record. Every
anonymous class collapses to a lambda; the `threadMode` overload becomes a field
read on the record.

## 5. `PublicationScheduler` is ~40% task-serialization codec, not scheduling
`src/main/java/moo/runtime/PublicationScheduler.java:176-1193`

Roughly **1000 of 2328 lines are Toast durable-task snapshot import/export**, which
is persistence work living inside the scheduler:

- import side: `restoreQueuedTask` `:176`, `restoreSuspendedTask` `:213`,
  `importActivation` `:284`, `importStructuralStack` `:344`, `importCatchGroup` `:444`,
  `handlerState` `:496`, `importFinallyState` `:514`, `importCollectionLoop` `:555`,
  `importRangeLoop` `:591`
- export side: `durableTask` `:679`, `durableSuspension` `:744`,
  `exportStructuralStack` `:779`, `exportedCatchHandlers` `:878`,
  `fillOrdinarySlots` `:932`, `exportFinallyState` `:1001`,
  `exportCollectionLoop` `:1054`, `exportRangeLoop` `:1088`, `exportActivation` `:1102`
- 7 supporting `private record`s: `ImportedActivationStack` `:2119`,
  `ImportedHandler` `:2132`, `ImportedHandlerGroup` `:2136`, `ImportedFinally` `:2142`

**Every method from `:284` to `:1193` is `private static`** — pure functions that
touch no scheduler state. They can move to a `DurableTaskCodec` class by
cut-and-paste plus a visibility change. Nothing else in the file changes.

The genuine scheduler (`dispatch` `:1222`, `executeAttempt` `:1236`,
`executeSegment` `:1269`, `drainPublications` `:1477`, `publishAttempt` `:1569`,
timer management `:1807-1932`) is ~800 lines and reasonably cohesive once the codec
leaves. **Second-highest value-per-risk after `Result`.**

Note the layering inversion: `PublicationScheduler` holds a `MooRuntime runtime`
field (`:53`) while `MooRuntime` holds a `PublicationScheduler scheduler` field
(`MooRuntime.java:75`) — a hard bidirectional dependency.

## 6. `ToastV17ProgramLayout` — three unrelated things in one file
`src/main/java/moo/persistence/ToastV17ProgramLayout.java` (2229)

- **Data model, `:29-412` (~380 lines):** 21 public records (`CallBoundary` `:29`,
  `BantengCallSite` `:38`, `EnclosingHandler` `:54`, `ToastHandlerGroup` `:124`,
  `ToastControlLabels` `:145`, `CatchGroup` `:230`, `ProtectedFinally` `:264`,
  `CollectionLoop` `:358`, `RangeLoop` `:378`, `StructuralStackShape` `:402`, …).
  Pure declarations, zero logic.
- **Query facade, `:413-742`:** `resolve` `:413`, `callBoundaries` `:441`,
  `resolveToastControls` `:448`, `resolveStructuralStack` `:507`,
  `locateBantengCall` `:521`, `resolveContinuation` `:534`/`:545`, plus static helpers.
- **A compiler, `:1024-2229` (~1200 lines):** nested `GlobalState` `:1024`,
  `VectorBuilder` `:1044-1293` (~250 lines), and `LayoutCompiler` `:1300-2229`
  (**929 lines**), which owns its own symbol table and 24-entry `PREDEFINED_NAMES`
  list (`:1301-1306`).

`LayoutCompiler` and `VectorBuilder` are already `private static final class` — they
are top-level classes wearing a nested-class costume. Promoting them plus moving the
21 records to their own file leaves a ~350-line query facade.

## 7. `MooVm` — 2634 lines with zero instance fields
`src/main/java/moo/vm/MooVm.java:46`

`public final class MooVm` declares **no instance state whatsoever**. Three instance
methods exist (`execute` `:59`, `authorizePendingBuiltin` `:1474`,
`resumeWithError` `:1746`) and every other one of its ~60 methods is
`private static`. The class is a namespace, not an object.

- `executeInstruction` `:82-382` is a **300-line switch** (`:96`) over 46 opcodes:
  `PUSH_INTEGER`…`PUSH_ERROR`, `BUILD_LIST`/`LIST_APPEND`/`LIST_EXTEND`/`BUILD_MAP`,
  `LOAD_LOCAL`/`STORE_LOCAL`, `DUP`/`DUP_PAIR`/`POP`, `GET_PROPERTY`/`SET_PROPERTY`,
  `ENTER_INDEX`/`INDEX`/`RANGE`/`FIRST`/`LAST`/`SET_INDEX_*`/`SET_RANGE_LOCAL`,
  `CALL`/`CALL_VERB`, arithmetic/bitwise/comparison, `FORK`, jumps, handler ops,
  `ITERATE`/`ITERATE_RANGE`/`LEAVE_LOOP`, `SCATTER`, `RETURN`.
- 95 `case` labels and 14 separate `switch (instruction.opcode())` statements
  (`:96`, `:383`, `:581`, `:1876`, `:1930`, `:1977`, `:2046`, `:2057`, `:2070`,
  `:2083`, `:2096`, `:2110`, `:2144`). The opcode is re-dispatched repeatedly at
  different depths — `arithmetic` `:1887`, `comparison` `:2039`, and `bitwise`
  `:1849` each re-switch on the same opcode they were already dispatched from.
- `applyBuiltinResult` `:1495-1746` is 251 lines of unpacking the 22-component
  `Result` — it shrinks substantially once finding #1 lands.
- 5 overloads of `raiseError` (`:2426`, `:2430`, `:2450`, `:2455`, and the 4-arg
  form) plus `completeErrorDetails` `:2537` / `traceback` `:2551` form a distinct
  ~200-line error-and-traceback concern (`:2426-2600`).

**Action:** because it is already stateless, splitting by opcode family is low risk —
`ListOps`, `PropertyOps`, `IndexOps`, `ArithmeticOps`, `LoopOps`, `ErrorOps`. The
14 redundant re-switches are the main correctness hazard: a new opcode must be added
in several places with no compiler enforcement. Consider making the opcode enum carry
its own arity/behavior instead.

## 8. `WorldTxn` mixes world state with connection/session state
`src/main/java/moo/world/WorldTxn.java`

Five distinct responsibilities:

1. **Connection registry (does not belong here at all):** fields `connections` `:43`,
   `connectionInfo` `:44`, `intrinsicCommands` `:45`; methods `openConnection`
   `:225`/`:230`, `closeConnection` `:243`, `connectionPlayer` `:251`,
   `connectedPlayers` `:258`, `connectionInfo` `:270`, `connectionId` `:284`,
   `intrinsicCommands` `:298`, `setIntrinsicCommands` `:312`,
   `restoreIntrinsicCommands` `:329`, `switchConnectionPlayer` `:335`. This is
   *session* state living in the *world* transaction — ~120 lines that extract to a
   `ConnectionRegistry` with no world-model coupling.
2. **MVCC / conflict tracking:** `recordReads` `:49`, `recordWrites` `:50`,
   `anonymousReads` `:51`, `anonymousWrites` `:52`, `waifWrites` `:53`,
   `scanPredicates` `:54`; `begin` `:128`, `validate` `:180`, `commit` `:186`,
   `close` `:196`.
3. **Object/verb/property CRUD:** `:345-1802`, including `recycleObject`
   `:1108-1288` (180 lines) and `writeObjectProperty` `:677-778` (101 lines).
4. **Inheritance layout engine:** `validateNewParents` `:1812`,
   `inheritedProperties` `:1883`, `rebuildPropertyLayouts` `:1910`,
   `rebuildPropertyLayout` `:1923`, `oldPropertySlots` `:2005`, `descendantsOf`
   `:2034`, `directParentProperty` `:2054`, `rebuildAnonymousPropertyLayouts` `:2070`,
   `rebuiltAnonymousProperties` `:2110`. ~300 lines of graph algorithm, nearly all
   static — a clean `PropertyLayoutEngine` extraction.
5. **Anonymous-object and waif storage:** `:352-412`, `:976-1107`, `:2196-2239`.

Also 5 public constructors (`:62`, `:67`, `:75`, `:84`, `:100`) and paired
`addProperty`/`addVerb`/`setVerbCode`/`changeParents`/`move` overloads
(`:1486`/`:1513`, `:1605`/`:1630`, `:1681`/`:1710`, `:1293`/`:1382`, `:1437`/`:1442`).

## 9. `MooRuntime` — a 626-line method and an embedded garbage collector
`src/main/java/moo/runtime/MooRuntime.java`

- **`executeLineNow` `:427-1053` is 626 lines**, the second-largest method in the
  repo.
- **Reachability GC, `:1785-2280` (~500 lines):** `finalizePendingObjects` `:1785`,
  `finishAnonymousFinalization` `:1849`, `finishWaifFinalization` `:1870`,
  `removeWaifComponent` `:1884`, `recoverInterruptedFinalizations` `:1894`,
  `queueUnreachableAnonymousObjects` `:1950`, `collectWaifReferences` `:2109`,
  `collectAnonymousReferences` `:2158`, `markReachableAnonymous` `:2193`,
  `markFinalizingAnonymous` `:2236`. A tracing collector with mark phases, wholly
  unrelated to connection handling. Most are `private static`. Extracts to
  `FinalizationCollector`.
- **Login/authentication state machine, `:1124-1637`:** `executeLogin` `:1124`,
  `executeLoginAuthentication` `:1175`, `finishLogin` `:1253` (188 lines),
  `associateRedirectedLogin` `:1441`, `continueCreatedLogin` `:1455`,
  `startConnectedHook` `:1464`, `monitorUnauthenticatedConnection` `:1488`,
  `checkLoginTimeoutNow` `:1503`, `finishLoginTimeout` `:1582`.
- **Connection-option/input plumbing, `:2281-2726`:** `read` `:2281`,
  `awaitRead` `:2332`, `applyConnectionOptionRequests` `:2356`, `connectionOptions`
  `:2401`, `outputDelimiters` `:2446`, `queueInfo` `:2467`, `flushInput` `:2539`,
  `applyForcedInputRequests` `:2579`, `applyBootPlayerTargets` `:2597`.
- 5 constructors (`:86`, `:113`, `:125`, `:142`, private `:170`); `openConnection`
  has 3 overloads (`:328`, `:333`, `:339`).
- Uses a `ThreadLocal<AttemptContext> ATTEMPT` (`:80`) with `requireAttempt()`
  (`:3148`) as ambient context — hidden coupling; every method reachable from an
  attempt implicitly depends on it being set.

## 10. `VmState` — three concerns in one mutable bag
`src/main/java/moo/vm/VmState.java`

- **Machine state:** `frames` `:40`, `returnValue` `:49`, `pendingError` `:50`,
  `uncaughtError` `:51`, `outcome` `:48`.
- **Resource metering:** `remainingTicks` `:59`, `elapsedCpuNanos` `:60`,
  `remainingCpuNanos` `:61`, `maxStackDepth` `:62`, `segmentCpuAnchorNanos` `:63`,
  the `THREAD_CPU` MXBean `:38`, `segmentElapsedCpuNanos` `:1015`,
  `currentThreadCpuNanos` `:1021`, `saturatedAdd` `:1029`. A `TickBudget` /
  `CpuMeter` value object.
- **Host-effect outbox:** `output` `:42`, `connectionOptionRequests` `:43`,
  `bootPlayerTargets` `:44`, `forcedInputRequests` `:45`, `checkpointRequests` `:46`,
  `anonymousCollectionDeferrals` `:47`, drained by the copy-pasted family
  `drainConnectionOptionRequests` `:664`, `drainBootPlayerTargets` `:675`,
  `drainForcedInputRequests` `:686`, `drainCheckpointRequests` `:697` — four
  near-identical 11-line methods.

7 telescoping constructors (`:69`, `:74`, `:86`, `:91`, `:100`, `:110`, `:121`) and
paired `pushVerbFrame` overloads (`:485`, `:512`). Roughly 20 mutable fields; the
`initial*` group (`initialLocals` `:41`, `initialProgrammer` `:64`,
`initialVerbLocation` `:65`, `initialDebug` `:66`) is already effectively an
immutable `TaskOrigin` record waiting to be named.

---

## Priority: maintainability per unit of risk

**Tier 1 — mechanical, near-zero risk, large payoff**
1. Extract `Result` to its own file + builder (finding #1). ~450 lines deleted,
   9 files decoupled from the god class, no behavior change.
2. Extract the durable-task codec out of `PublicationScheduler` (finding #5).
   ~900 lines of already-`private static` pure functions; cut, paste, adjust
   visibility.
3. Promote `LayoutCompiler` + `VectorBuilder` and the 21 records out of
   `ToastV17ProgramLayout` (finding #6). ~1500 lines, already nested static classes.

**Tier 2 — repetitive but proven-safe, do incrementally**
4. Split `buildManifest` into per-family classes (finding #3), one family per commit,
   following the existing `addFileIoManifest` precedent at `:2366`.
5. `BuiltinHosts` parameter object to kill the 20 constructors (finding #2) and the
   `BuiltinCall` record to kill the 10-parameter signature (finding #4). Do #4 before
   #3 so the extracted families are written against the good signature.

**Tier 3 — real design work, needs tests as a safety net**
6. `WorldTxn`: pull out `ConnectionRegistry` first (finding #8.1, ~120 lines, no world
   coupling), then `PropertyLayoutEngine` (#8.4, ~300 mostly-static lines).
7. `MooRuntime`: extract `FinalizationCollector` (#9, ~500 lines, mostly static),
   then break up `executeLineNow`.
8. `MooVm`: split by opcode family (#7). Low risk mechanically since the class is
   stateless, but the 14 redundant opcode re-switches need care.
9. `VmState`: extract the effect outbox and the CPU/tick meter (#10).

**Structural issue worth flagging separately:** the `MooRuntime` <-> 
`PublicationScheduler` bidirectional field dependency (`MooRuntime.java:75` /
`PublicationScheduler.java:53`) means neither can be tested or reasoned about in
isolation. Worth an interface seam, but that is a design change, not a move.
