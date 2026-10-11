# Architecture audit — banteng

Scope: macro architecture of `src/main/java/moo` (34,878 LOC, 62 files, 10 packages).
Read-only audit. No files under src/ were modified.

Sizes: builtin/BuiltinCatalog.java 7370, runtime/MooRuntime.java 3617, vm/MooVm.java 2634,
world/WorldTxn.java 2494, runtime/PublicationScheduler.java 2328,
persistence/ToastV17ProgramLayout.java 2229, persistence/LambdaMooV17Codec.java 1923,
vm/VmState.java 1210, bytecode/MooCompiler.java 979, value/MooValue.java 944.

---

## 0. Headline: the layering is genuinely clean; the problems are inside the layers

Package dependency graph (computed from `import moo.*` and from fully-qualified
`moo.pkg.Type` references — both produce the identical edge set, so there are no
hidden dependencies smuggled in via FQNs):

```
app        -> persistence, server
server     -> builtin, persistence, runtime, value, world
runtime    -> builtin, bytecode, persistence, value, vm, world
persistence-> bytecode, syntax, value, world
vm         -> builtin, bytecode, value, world
builtin    -> bytecode, syntax, value, world
bytecode   -> syntax
world      -> value
syntax     -> (nothing)
value      -> (nothing)
```

**There are no package-level cycles and no upward dependencies.** `value/` imports
nothing; `world/` imports only `value/`; `syntax/` imports nothing. A valid
topological order exists: `syntax, value -> world -> bytecode -> builtin -> vm ->
persistence -> runtime -> server -> app`. Notably `runtime -> server` does *not*
exist, because the server-facing capability is inverted through an interface
(see Finding 6).

Encapsulation via package-private visibility is also applied deliberately and well:
PublicationScheduler.java has exactly **1** public member, MooVm.java has **3**
(execute :59, authorizePendingBuiltin :1474, resumeWithError :1746),
TaskRegistry.java has **0**. Internals really are hidden.

Every package has `@NullMarked` package-info (jspecify) — consistent null discipline.

So the "unfuck" work is not about untangling a dependency ball of mud. It is about
four specific things: a God union type, a God transaction interface, a missing
persistence abstraction, and an undocumented/inconsistent error channel.

---

## Finding 1 — `BuiltinCatalog.Result` is a 22-component Optional union (worst offender)

Declared at **BuiltinCatalog.java:6864-6886**. Components:

```
value, error, dynamicSource, output, switchedPlayer, programmer, recycleTarget,
delaySeconds, hostWork, connectionOptionRequest, bootPlayerTarget, forcedInputRequest,
taskLocal, moveObject, moveDestination, movePosition, errorDetails, checkpointRequest,
initializeRequest, threadMode, abortSeconds, anonymousRecycleTarget
```

21 of the 22 are `Optional` / `OptionalLong` / `OptionalDouble`. Three of the
components (`moveObject`, `moveDestination`, `movePosition`) are a single logical
value shredded into three parallel optionals that must be set together
(**BuiltinCatalog.java:7292-7311**, `Result.move`) and are read back with three
`orElseThrow()` calls at **MooVm.java:1559-1561** — an invariant the type cannot enforce.

Four telescoping private constructors exist purely to pad the tail with `Optional.empty()`
(**BuiltinCatalog.java:6888, 6934, 6980, ~7030**), plus roughly 22 static factories, each
one an explicit column of `Optional.empty()`. Examples:
`Result.suspend` **:7121**, `Result.hostWork` **:7152**, `Result.threadMode` **:7168**,
`Result.secondsAbort` **:7189**, `Result.raised` **:7213**, `Result.move` **:7292**,
`Result.recycle(AnonymousObjectValue)` **:7333**. The `Result` block runs
**BuiltinCatalog.java:6864-7370** — about 507 lines, nearly all of it boilerplate.

Consumption is broad: `Result` appears 41 times in MooRuntime.java, 10 in MooVm.java,
10 in VmState.java, 3 in VmSnapshot.java.

This is the type that grows every time a builtin needs a new effect, and it is the
type that makes both the VM and the runtime hard to read. It wants to be a
`sealed interface BuiltinEffect` with one record per case
(`Value`, `Raise`, `Suspend`, `Move`, `Recycle`, `HostWork`, `Checkpoint`, ...),
which would let the VM's dispatch be an exhaustive `switch` instead of a chain of
`isPresent()` tests.

## Finding 2 — `WorldTxn` is a God interface, and it owns connection/session state

**81 public members** on world/WorldTxn.java (2494 lines, 145 methods total). The
public surface bundles at least six responsibilities:

- transaction lifecycle: `begin` :128, `validate` :180, `commit` :186, `close` :196,
  `stageEffect` :174, `retainSnapshot` :139
- object graph: `createObject` :869/:874, `recreateObject` :920, `recycleObject` :1108,
  `changeParents` :1293, `move` :1437/:1442, `ancestry` :460
- property store: `readObjectProperty` :609/:651, `writeObjectProperty` :677/:779,
  `addProperty` :1486/:1513, `deleteProperty` :1546, `clearProperty` :1577
- verb store: `verb` (5 overloads) :413-:515, `addVerb` :1605/:1630, `deleteVerb` :1657,
  `setVerbCode` :1681/:1710, `setVerbInfo` :1737, `setVerbArgs` :1771
- waif + anonymous-object stores: `createWaif` :992, `readWaifProperty` :1015,
  `writeWaifProperty` :1041, `createAnonymousObject` :976/:981, `removeAnonymousObject` :360
- **connection/session registry**: `openConnection` :225/:230, `closeConnection` :243,
  `connectionPlayer` :251, `connectedPlayers` :258, `connectionInfo` :270,
  `connectionId` :284, `intrinsicCommands` :298, `setIntrinsicCommands` :312,
  `restoreIntrinsicCommands` :329, `switchConnectionPlayer` :335

The last group is the clearest misplacement in the codebase: live network-session
state is transacted through the same MVCC object store as persistent world records.

## Finding 3 — no persistence-format abstraction; codec DTOs are the runtime's working types

`persistence/` contains four concrete classes and **zero interfaces**:
LambdaMooV4Reader, LambdaMooV5Reader, LambdaMooV17Codec, ToastV17ProgramLayout.
Format selection is a hardcoded if/else on version in **app/Banteng.java:86-91**.

Worse, `LambdaMooV17Codec`'s nested *serialization* records are used directly as the
runtime's in-memory task and connection model, so the on-disk format's vocabulary is
baked into the scheduler and the socket server:

- **PublicationScheduler.java:32-36** imports `DurableTask`, `QueuedTask`,
  `SuspendedActivation`, `SuspendedStackSlot`, `SuspendedTask`;
  **PublicationScheduler.java:668** exposes `synchronized List<DurableTask> durableTasks()`.
- **MooRuntime.java:32-33** imports the codec and `ActiveConnection`;
  **MooRuntime.java:129, 147, 176** take `List<LambdaMooV17Codec.DurableTask>` parameters.
- **MooServer.java:70-71, 90-91, 109-110, 116-118** carry
  `List<LambdaMooV17Codec.DurableTask>` and `List<LambdaMooV17Codec.ActiveConnection>`
  in its own method signatures.

Adding or changing an on-disk format therefore requires editing the scheduler and the
network server. A `DatabaseReader`/`Checkpointer` interface plus runtime-owned domain
records would confine it.

## Finding 4 — there is no composition root; MooRuntime is one by accident

`app/package-info.java` describes app as the *"future* concrete composition root."
It is not one yet. `MooRuntime` constructs its own collaborators:

- **MooRuntime.java:74** `private final MooVm vm = new MooVm();`
- **MooRuntime.java:73** `private final LambdaMooV17Codec checkpointCodec = new LambdaMooV17Codec();`
- **MooRuntime.java:93 / :185** `new PublicationScheduler(...)`
- **MooRuntime.java:97 / :191** `new BuiltinCatalog(...)`

The only injected collaborator in the whole system is `ListenerControl`
(**MooRuntime.java:69, 113, 127, 144, 159, 172**). Neither the VM, the builtin set,
the scheduler, nor the checkpoint format is swappable, which is what forces
integration-style testing everywhere.

## Finding 5 — `vm -> builtin` binds the VM to the 7370-line catalog

**MooVm.java:13-17** imports `BuiltinCatalog`, `BuiltinCatalog.Result`,
`BuiltinCatalog.InitializeRequest`, `BuiltinSpec`, `EffectClass` concretely.
**VmState.java:18-21** and **VmSnapshot.java:12-14** additionally import
`BuiltinCatalog.Result`, `ConnectionOptionRequest`, `ForcedInputRequest`,
`CheckpointRequest`.

There is no `BuiltinRegistry` / `BuiltinInvoker` interface, so the VM cannot be
compiled, tested, or run against a subset of builtins. Note this is a *coupling*
problem, not a cycle — `builtin` does not import `vm`.

Related: `BuiltinHandler.invoke` (**BuiltinHandler.java:11-22**) takes **10 positional
parameters** (`arguments, world, programmer, taskLocal, taskId, remainingTicks,
remainingSeconds, receiver, callerProgrammer, callers`), with an 11th-parameter
`threadMode` overload defaulting through it (**BuiltinHandler.java:25-45**). Every
new piece of activation context widens the signature of every builtin. This wants
a `BuiltinContext` record.

## Finding 6 — host adapters live in `builtin/` and return the VM's union type

`builtin/` holds five host adapters, all package-private `final class`:
FileIoService (808 lines, **:31**), JsonCodec (565, **:23**), SqliteService (407, **:33**),
PcreService (224, **:21**), SimplexNoise (175, **:4**), plus ConfinedFileRoot (**:7**).

Three of them import and return the VM boundary type:
**FileIoService.java:22**, **SqliteService.java:19**, **PcreService.java:11** all
`import moo.builtin.BuiltinCatalog.Result`. A SQLite adapter and a filesystem adapter
returning a 22-field VM effect union cannot be exercised without the VM contract.
(JsonCodec **:23** and SimplexNoise **:4** are clean — they deal in `MooValue` or
plain doubles, and would move out easily.)

The counter-example proves the pattern is understood: `ListenerControl`
(**BuiltinCatalog.java:6792-6824**) is a proper host port, implemented by
`MooServer implements AutoCloseable, ListenerControl` (**MooServer.java:44**). That
single inversion is why `runtime -> server` does not exist. It is applied exactly once,
and even then the interface is buried inside the 7370-line catalog file rather than
being a top-level type.

## Finding 7 — concurrency: ownership is actually clear, the discipline is undocumented

Distribution of primitives (sync blocks/methods, volatile, java.util.concurrent, thread creation):

| file | sync | volatile | conc-util | thread creation |
|---|---|---|---|---|
| runtime/PublicationScheduler.java | 33 | 2 | 2 | 14 |
| runtime/TaskRegistry.java | 19 | 0 | 0 | 0 |
| runtime/MooRuntime.java | 9 | 1 | 5 | 8 |
| world/WorldHistory.java | 8 | 0 | 0 | 0 |
| server/MooServer.java | 5 | 0 | 12 | 0 |
| builtin/FileIoService.java | 29 | 0 | 0 | 0 |
| builtin/SqliteService.java | 10 | 1 | 2 | 0 |
| builtin/PcreService.java | 3 | 0 | 0 | 0 |
| world/WorldTxn.java | 0 | 0 | 1 | 0 |

The good news, and it is substantial:

- **WorldHistory** (**world/WorldHistory.java:18**) is a textbook MVCC store —
  package-private, a private `NavigableMap<Long, World>` of *immutable* revisions, an
  explicit `activeTransactions` refcount, and every single accessor synchronized:
  `retainCurrent` **:86**, `current` **:93**, `snapshot` **:97**, `validate` **:101**,
  `publish` **:117**. Mutable world state has exactly one owner.
- **WorldTxn** has zero synchronization, which is correct: it is single-task-owned,
  reads an immutable base revision, and republishes through `history.publish`
  (**WorldTxn.java:190**).

The gap is documentation, not correctness-by-inspection. `PublicationScheduler` mixes
monitor styles — `synchronized` methods at **:148, 668, 1194, 1209, 1213, 1823, 1863,
1870, 1881, 1895, 1966, 1970** and 21 bare `synchronized (this)` blocks at **:123, 136,
721, 1166, 1462, 1480, 1490, 1502, 1509, 1521, 1545, 1635, 1646, 1709, 1721, 1808, 1839,
1855, 1904, 1919, 1991** — while also owning a `ThreadPoolExecutor` (**:57, :103-110**)
and spawning virtual threads (**:1767, :1833**). Method names such as
`publishVmCompletionOutsideMonitor` (**:1630**) show a real lock-ordering rule exists in
the author's head. It is written down nowhere: all ten `package-info.java` files are a
single sentence with no threading contract, and there is no `@GuardedBy` in the tree.

## Finding 8 — error architecture: excellent in the VM, ad-hoc at the boundaries

**Zero custom exception types exist in the entire codebase.** Throw inventory:

| type | count |
|---|---|
| IllegalArgumentException | 183 |
| IllegalStateException | 96 |
| AssertionError | 13 |
| IOException | 8 |
| UncheckedIOException | 6 |
| CancellationException | 3 |

The VM's own model is clean and should be preserved: MOO errors are *data*, not control
flow. `raiseError(state, ErrorValue.E_TYPE, world)` mutates explicit VM state
(dozens of sites, e.g. **MooVm.java:190, 230, 266, 452, 566, 656, 705, 741**). No Java
exception is used to express a MOO-level error inside the interpreter.

Three inconsistencies sit at the edges of that clean core:

1. **`IllegalArgumentException` doubles as a cross-layer error channel.** 24 catch
   sites convert it to a MOO error, wrapping calls broad enough that a genuine internal
   bug would be silently reported to MOO code as user error:
   **BuiltinCatalog.java:4942** (`JsonCodec.generate` -> E_INVARG),
   **:4957** (`JsonCodec.parse` -> E_INVARG), **:5142** (create -> E_INVARG),
   **:2773, :2825, :2855** (listener ops -> E_INVARG), **:3991** (-> E_RANGE),
   **:4793** (-> E_INVIND), and **MooVm.java:1574** (compile failure -> E_INVARG).
   Most severe: **BuiltinCatalog.java:4832** catches IAE from Argon2 verification and
   returns `IntegerValue(0)` — an internal failure is indistinguishable from
   "wrong password."
2. **`catch (Throwable)` that swallows.** **BuiltinCatalog.java:3234-3236** catches
   `Throwable`, zeroes a buffer, and continues to build a result — this absorbs
   `OutOfMemoryError` and `StackOverflowError` into a normal return value. The other
   Throwable catches at **:3804, :3827, :4760** correctly rethrow as IllegalStateException.
3. **Boolean returns discard the failure reason, forcing callers to guess.**
   `WorldTxn.writeObjectProperty` (**:677**), `move` (**:1437**), `addProperty` (**:1486**),
   `recycleObject` (**:1108**), `changeParents` (**:1293**) all return bare `boolean`.
   Callers therefore hardcode one error code for all causes:
   **MooVm.java:833-835** maps any `writeObjectProperty` failure to `E_PROPNF`;
   **MooVm.java:1564-1566** and **:2416** map any `move` failure to `E_INVARG`.
   This also pushes authorization logic upward — **MooVm.java:826-831** re-implements
   the wizard-flag and property-permission check (`flags() & 4`, `permissions() & 2`)
   that belongs behind the world boundary.

---

## Recommended ordering (highest leverage first)

1. Replace `BuiltinCatalog.Result` with a sealed effect hierarchy (Finding 1) — removes
   ~500 lines of boilerplate and makes VM dispatch exhaustive.
2. Introduce `BuiltinContext` for the 10-arg handler signature (Finding 5).
3. Define runtime-owned task/connection records and a persistence-format interface;
   stop passing `LambdaMooV17Codec.*` through scheduler and server (Finding 3).
4. Split connection/session state out of `WorldTxn` (Finding 2).
5. Write the threading contract into `runtime/package-info.java` and add `@GuardedBy`
   (Finding 7) — cheap, and currently the only thing keeping the scheduler safe is
   author memory.
6. Introduce a small set of domain exceptions and stop catching bare
   `IllegalArgumentException` across module boundaries (Finding 8).
