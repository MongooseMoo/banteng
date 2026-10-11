# Dead code & scar tissue audit (banteng)

Status: in progress. Read-only audit. No files modified other than this one.

## Method

- `git ls-files` (179 tracked files), `.gitignore` review.
- Grep for TODO/FIXME/HACK/XXX across `src/main` and `src/test`.
- Grep for `System.out`/`System.err`/`printStackTrace`.
- Scripted extraction of every `public`/`protected` method declaration in `src/main`
  (211 found), then reference-counted each name across **all** of `src/` (main, test,
  jmh, jcstress). Candidates with a single occurrence were then verified by hand with
  a direct grep, because the script's regex also matches nested `record` headers.

## Key structural fact that shapes everything below

`build.gradle.kts:74-82` compiles with `-Xlint:all -Werror` plus Error Prone 2.50 and
NullAway in JSpecify mode. **Unused private members cannot exist** — the build would
fail. So classic dead code is confined to non-private surface that Error Prone does
not flag: public/package methods with no callers, and CLI options that are parsed but
never read.

## 1. Confirmed dead public methods (declaration is the only occurrence in src/)

| Symbol | Location | Note |
|---|---|---|
| `WorldTxn.baseRevision()` | `src/main/java/moo/world/WorldTxn.java:168` | no callers in main or test |
| `WorldTxn.changeParent(long, long)` | `src/main/java/moo/world/WorldTxn.java:1288` | single-parent wrapper superseded by `changeParents` |
| `WorldTxn.restoreIntrinsicCommands(long)` | `src/main/java/moo/world/WorldTxn.java:329` | wrapper over `setIntrinsicCommands(id, DEFAULT_INTRINSIC_COMMANDS)` |
| `MooValue.StringValue.equalsCaseSensitively(StringValue)` | `src/main/java/moo/value/MooValue.java:263` | sibling `compareCaseSensitively` IS used (MooValue.java:774) |

`changeParent` is the clearest scar: the `chparent` and `chparents` builtins
(`BuiltinCatalog.java:1568` and `:1577`) both dispatch to `changeParents(a, w, p)`,
so the single-parent convenience wrapper added before multiple inheritance landed
now has no caller.

## 2. Test-only public surface

Public in main, referenced only from tests — candidates for `@VisibleForTesting`-style
narrowing or removal:

- `WorldTxn.stageEffect(MooValue)` — `WorldTxn.java:174`, only caller `src/test/java/moo/world/WorldTxnTest.java:85`.
- `ToastV17ProgramLayout.resolveToastFinallyLabel(...)` — `ToastV17ProgramLayout.java:487`, only caller `src/test/java/moo/persistence/ToastV17ProgramLayoutTest.java:540`.

## 3. Dead CLI surface (self-documented)

`src/main/java/moo/app/Banteng.java` declares picocli options whose fields are never
read, and says so:

- `--log-level` / `logLevel`, `Banteng.java:44-47`:
  `@SuppressWarnings("UnusedVariable") // Retained CLI surface; server logging is not part of this slice.`
- `--promote-numbers` / `promoteNumbers`, `Banteng.java:54-55`:
  `@SuppressWarnings("UnusedVariable") // Profile-selected configuration for the Phase 3 value slice.`

These are advertised in `--help` but do nothing.

## 4. Conformance-test scar tissue

`grep -rn "TODO\|FIXME\|HACK\|XXX"` returns **zero** hits in `src/main` and zero in
`src/test`. The scars are unmarked, so they have to be found by comment text.

The clearest one is `.program` handling in `MooRuntime`:

- `src/main/java/moo/runtime/MooRuntime.java:468` — compile failures on `.program`
  are swallowed: `catch (IllegalArgumentException ignored) { // The active conformance
  row does not observe programming diagnostics. }`
- `src/main/java/moo/runtime/MooRuntime.java:501` — same comment on a
  `NumberFormatException` from a malformed `#obj:verb` descriptor.

Real LambdaMOO emits `Line N: ...` diagnostics and `Verb programmed.`; this
implementation returns `List.of()` on both the success and failure paths
(`MooRuntime.java:473`, `:504`). The comment admits the behaviour is scoped to
whichever conformance row was green at the time.

Phase-numbered language leaking into production identifiers/messages:

- `LambdaMooV17Codec.java:51` — class doc scopes it to "the Phase 2 LambdaMOO v17 slice".
- `LambdaMooV17Codec.java:1203` and `:1679` — user-visible exception text says
  "unsupported Phase 2 v17 value" / "unsupported Phase 2 v17 value tag".
- `MooCompiler.java:413` and `:654` — "unsupported statement/expression in bytecode slice".

These are error strings a real operator could see; "Phase 2" and "slice" are
build-process vocabulary, not domain vocabulary.

## 5. Console writes — mostly deliberate fidelity, not debug leftovers

Every `System.err` write I found looks like intentional emulation of LambdaMOO server
log output rather than stray instrumentation, but they bypass the `System.Logger`
surface the `--log-level` flag pretends to configure (see item 3):

- `src/main/java/moo/persistence/LambdaMooV5Reader.java:164-327` — the whole
  `VALIDATE: Phase 1/2/3 ...` block, reproducing LambdaMOO's db-load validator text.
- `src/main/java/moo/builtin/BuiltinCatalog.java:3149-3157` — `server_log`-style
  `"> " + message` plus a hardcoded verb-cache-stats block.
- `src/main/java/moo/runtime/MooRuntime.java:2921`, `:3101` (`CHECKPOINTING to ...`),
  `:3120` (`PANIC: ...`).

No `printStackTrace` anywhere in main or test. No `System.out`/`System.err` in test.

**Hardcoded constant worth flagging:** `BuiltinCatalog.java:3154-3157` prints
`"Verb cache stat summary: 0 hits, 0 misses, 0 generations"` and a `Depth/Count`
table with a literal `0 0` row. That is a fabricated response with no backing
counters — a canned answer shaped to whatever the oracle printed.

## 6. Repo hygiene

`git ls-files | wc -l` = 179 tracked; `git status --porcelain` shows **198 untracked**
entries. Zero `notes-*.md` files are tracked (`git ls-files | grep -c notes- == 0`),
so the ~180 scratch notes in the repo root are all untracked and permanently dirty the
working tree.

- `.gitignore` covers `.gradle/`, `build/`, `out/`, `*.class`, IDE dirs, hs_err/replay,
  and `/fixtures/mongoose.db`. It does **not** cover `notes-*.md`, `investigations/`,
  `prompts/`, `reports/`, or `pyghidra_mcp_projects/`.
- Tracked docs live under `docs/reports/` (50 authority/design docs) — that is the
  curated set. The root `reports/` directory (5 files, spec-review artifacts) and
  `prompts/` (4 files) duplicate that role without being tracked.
- `investigations/` is half-tracked: `investigations/anon1-managed-startup.md` IS
  tracked, `investigations/resume-after-suspend-background-limits.md` is not.
  Inconsistent.
- **`pyghidra_mcp_projects/` (194K) is unrelated to this project** — a Ghidra reverse
  engineering project dir (`my_project.gpr`, `my_project.rep`,
  `my_project-pyghidra-mcp`). Junk from oracle-binary analysis; should be moved out
  or ignored.
- **Stray `moo/` directory at the repo root** contains compiled `.class` files
  (`moo/bytecode/*.class`, `moo/persistence/*.class`) — the residue of a `javac` run
  without `-d`. Invisible in `git status` only because `*.class` is ignored, but it
  shadows the package layout and is pure garbage.
- `fixtures/` is 97M; `build/` is 30M.

## 7. Overly permissive visibility

Spot check, not exhaustive. The four dead methods in item 1 plus the two test-only
methods in item 2 are the concrete instances found. `WorldTxn` in particular exposes a
very wide public API (2494 lines) of which several accessors have exactly one internal
caller — see `objectCount` (`WorldTxn.java:211`), `maximumObjectId` (`:218`),
`isCommitted` (`:2484`), `isValid` (`:2439`).

## Remaining work

- Sweep for magic constants / literal string matches in the command dispatch path.
- Check `ArchitectureTest` for rules that already encode some of this.

---

# Addendum (audit complete)

## 8. The worst scar: hardcoded variable names in the VM

`src/main/java/moo/vm/MooVm.java:570-572`, in `LOAD_LOCAL` handling:

```java
frame.operandStack.push(value);
if ((normalized.equals("waif") || normalized.equals("anon"))
    && isFinalStraightLineLocalRead(frame, normalized)) {
  frame.locals.remove(normalized);
}
```

After pushing a local's value, the VM deletes the local from the frame — but **only if
the programmer happened to name the variable `waif` or `anon`**. There is no comment
explaining this. The helper `isFinalStraightLineLocalRead` (`MooVm.java:577-618`) is a
genuine last-use liveness analysis, so the mechanism is principled; the *gate on two
literal identifier names* is not. Effect: whether an anonymous object or waif becomes
eligible for finalization depends on the spelling of a local variable. This is
precisely the shape of a fix written to turn one conformance row green — and the
repo's `notes-*waif*`/`notes-*anon*` scratch files corroborate the provenance.

Consequences worth stating plainly: a MOO program using `x` instead of `waif` gets
different recycling behaviour, and a program that legitimately names an unrelated
local `waif` gets it silently deleted from the frame.

## 9. Magic constant inconsistent with its own enum

`src/main/java/moo/vm/MooVm.java:547` — the `ANON` type-constant branch pushes
`new IntegerValue(12)`. Every one of the eight sibling branches in the same
if-chain (`MooVm.java:521-556`: OBJ, LIST, WAIF, ERR, MAP, BOOL, ...) uses
`MooValue.Type.X.code()`. `MooValue.Type.ANONYMOUS(12)` exists at
`src/main/java/moo/value/MooValue.java:50`, so the literal `12` is a bare magic
number where the named constant was available.

## 10. In-band sentinel strings in the value domain

`src/main/java/moo/runtime/MooRuntime.java:62-65`:

```java
private static final String ANONYMOUS_FINALIZATION_MARKER = "__banteng_anonymous_finalization__";
private static final String WAIF_FINALIZATION_MARKER = "__banteng_waif_finalization__";
```

These are wrapped into an ordinary MOO `ListValue` (`MooRuntime.java:1916-1922`) and
detected by string-comparing the first element (`MooRuntime.java:1924-1934`). The
signalling channel is the same value space MOO code can construct, so a MOO program
returning `{"__banteng_anonymous_finalization__", <anon>}` is indistinguishable from
the runtime's own control message. Implementation-namespace identifiers (`__banteng_`)
leaking into observable values is also a fidelity risk against the oracle.

## 11. Architectural rule with a named carve-out

`src/test/java/moo/ArchitectureTest.java:54-64` forbids `moo.persistence..` from
depending on `moo.syntax..` — then exempts exactly one class:

```java
.doNotBelongToAnyOf(ToastV17ProgramLayout.class)
```

The layering rule is real everywhere except the 2229-line class that violates it. That
is an architectural exception encoded as a permanent test exemption rather than a
tracked debt item; there is no comment saying why or when it should go away.

## 12. Other literal-match sites reviewed and judged legitimate

For completeness, these string literals in dispatch paths are faithful MOO/protocol
semantics, not scars:

- `MooRuntime.java:453`, `:556` — `#$#` out-of-band prefix (MOO OOB protocol).
- `MooRuntime.java:459` — `.` terminating `.program` input (MOO semantics).
- `MooRuntime.java:768`, `:854` — `#` object-number prefix in command parsing.
- `MooRuntime.java:1201-1206` — PROXY protocol line, 6-token form, gated on `trusted`.
- `MooVm.java:1429` — `name.equals("callers")` selecting the 1-arg form of the
  `callers()` builtin; slightly smelly (name-based special case inside the generic
  builtin dispatch loop rather than in the builtin itself) but semantically required.
- `BuiltinCatalog.java:4729-4747` — crypt/bcrypt salt prefixes (`$5$rounds=`, `$2a$`).
- `ConfinedFileRoot.java:15-18` — path traversal guard.

## Summary of the audit

Given `-Werror` + Error Prone, the codebase is far cleaner than the ~180 scratch notes
in the root would suggest. There is no commented-out code, no `printStackTrace`, no
TODO/FIXME markers, and no unused private members. What remains is:
four dead public methods, two dead CLI flags, two test-only public methods, three
genuine semantic scars (items 8, 9, 10), one architectural carve-out (item 11), and a
badly littered repo root (item 6).
