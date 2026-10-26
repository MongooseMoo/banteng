# Latest MOO conformance

## Goal

Make Banteng pass the latest tests on MongooseMoo/moo-conformance-tests main.
Q authorized implementation and a PR on 2026-10-25 with "Make it so!".
Necessary redesign/cleanup is in scope; weakening tests or skipping failures is not.

## State

- Isolated branch `fix/latest-conformance` starts at Banteng master `2ccad0597d7a6889e1607174cd2e6513a2032542`.
- Existing master CI run 32452595044 passed against suite `6528b8cf6d72c065c722469b070fea16d626e602` in August; it is not current evidence.
- Suite main observed as `8dbe801819c41529122cfb1f98f68e9b709db383` at task start and is actively changing.
- No implementation changes yet. Publish this note to obtain the existing PR CI baseline.

## Decisions and why

Smallest initial change: this task note only, roughly 30 lines. The existing CI
already checks out suite main, runs admission/conformance with strict markers and
unexpected-skip failure, and uploads results. No new runner or validation gate is
needed. Implementation scope and line estimate will follow actual failure evidence.
Use PR CI, not a local rerun of CI commands. Preserve unrelated worktrees/files.
No merge or release has been requested explicitly; deliver the reviewed passing PR
and resolve any remaining publication decision with Q.

## Surprises

The primary checkout was behind remote master and contains an unrelated untracked
investigation. It remains untouched.

## Next step

Open the PR, inspect the suite checkout SHA and conformance failures, and trace
each failure into current tests and Banteng source before changing behavior.

## Implementation: verb-name whitespace

PR #82 baseline CI is run 40271391375. While it runs, source confirms a mismatch:
`verb_setter_error_precedence.yaml:set_verb_info_tab_only_names` requires a tab to
remain a callable name; Toast `src/verbs.cc:116` strips only spaces, whereas
Banteng `BuiltinCatalog` uses `stripLeading` in both setters and `WorldTxn`
uses the default whitespace tokenizer. Smallest complete fix: strip spaces in
both metadata entry points and tokenize aliases on spaces only, about 12 lines.
Existing upstream cases supply regression coverage; no new guard is needed.

The runner's `plugin.py:405` requires a `{log}` placeholder to run named-log
signal cases. Add the existing Banteng `--log-file {log}` option to the existing
managed suite command (one line), enabling those tests without a new CI step.

## Baseline and next repair group

CI 40271391375 / conformance job 121404769359 tested suite 8dbe801 and reported
112 failures, 10867 passes, 2012 skips. SIGUSR1 terminated the process and the
managed lifecycle check stopped the run; tests after that point are unverified.
All other CI jobs passed. Full raw failure details remain in GitHub job logs.

Smallest next changes, about 35 lines across existing value/builtin classes:
restore full-width integer/object map comparisons (Toast utils.cc:412), signed
zero formatting, JSON 64-bit integers and first duplicate key retention
(json.cc:250, handle_end_map reverse insertion), C-string truncation in chr
(list.cc:1600), SHA salt validation (crypto.cc:parse_prefix), hex tofloat input,
and exp underflow errors. Existing conformance cases demonstrate each mismatch.
Do not alter the failed-dump test or manufacture a failure: its blocked temporary
paths name Toast/Barn, whereas Banteng uses a PID-qualified temporary file. This
requires separate analysis of the portable test contract.

Activation thread mode: VmState inherits the caller's mode into eval/verb frames;
Toast execute.cc initializes each activation to DEFAULT_THREAD_MODE. Reset new
frames to true while retaining snapshot restoration (three lines). Correct
chparent/chparents metadata argument types in their existing CallShapes (two
lines); the new function_info cases require OBJECT and LIST respectively.

Move lifecycle cause: BuiltinCatalog bypasses hooks for non-wizards; MooVm calls
only accept; LoopOps ignores its result, moves, and returns without exitfunc or
enterfunc. Toast objects.cc:117-210 defines accept -> relocation -> exitfunc ->
conditional enterfunc, including suspension. Smallest complete repair: retain
the existing move fields and frame/snapshot machinery, add explicit move return
phases, and put that lifecycle in MoveOps (about 180 lines replacing the current
accept-only implementation). Phases must be typed, never inferred from verb names.

CI 40272399950: 96 failures (16 baseline cases fixed), still aborts at SIGUSR1.
Java failures reflect stale map/thread-mode expectations and a test passing an
object to chparents instead of chparent; update those existing tests to the
source-confirmed contract. SQLite next: retain the existing service but obey
activation thread mode and remove JDBC's documented exception prefix from the
returned SQLite error text (~30 lines). Xerial DB.newSQLException builds
`resultCode + " (" + errmsg + ")"`; Toast sqlite.cc returns sqlite3_errmsg alone.

Parser group: reuse existing MooCompiler.compileResult from both set_verb_code
and eval (~25 lines replacing fail-fast exception conversion). Remove lexer
support for // comments and recognize only actual error constants. Preserve
ordinary syntax fail-fast while recovering independent semantic diagnostics.
No new parser or diagnostic layer. Baseline cases identify these exact gaps.

CI 40273084042 tested the move/SQLite commit: 81 failures, 10939 passes,
2012 skips, again stopped after SIGUSR1 killed the managed process. Move and
SQLite cases no longer appear in the failure list. Java's sole failure was
NamespaceOwnershipTest's hard-coded source-file count after adding MoveOps;
remove that count while retaining its per-file package/path assertions.

Parser implementation now compiles dynamic eval at the builtin boundary and
passes the compiled program to the VM. Both programming entry points validate
calls against the active builtin catalog. Existing compiler recovery retains
semantic errors before the first syntax error; diagnostics after that syntax
error are suppressed. Toast parser.y:463-495 also requires indexed assignment
roots to be variables/properties and treats optional scatter assignment as an
operand; parser.y:1083-1121 validates simple targets before duplicate-rest checks.
Use these existing AST paths (~30 lines), with no new parsing layer.

Next: push parser group, inspect CI, then implement process signal delivery via
the existing runtime request/continuation machinery. Signal behavior remains
unimplemented beyond TERM and blocks evidence for the later suite cases.

Signal implementation first slice (~110 lines): register USR1/USR2 with the
existing Java signal adapter, deliver them as runtime requests, run handle_signal
with server-origin locals, and use an explicit return continuation for fallback.
Drop that continuation at suspension (the hook itself still resumes). Reopen the
existing ServerLog file owner for USR1; reuse checkpoint_started/finished for USR2.
Toast server.cc:381-400 is the contract. Other reserved JVM signals need separate
source investigation; do not install unsafe Java callbacks as native handlers.

Parser CI 40273937477 failed compilation because OperatorPrecedence requires
explicit parentheses in the new scatter operand condition; corrected it.
Also register INT with TERM's existing shutdown coordinator. Toast server.cc:929
and 2354-2362 plus signals_shutdown.yaml require a shutdown notification and final
dump without checkpoint hooks. Correct that existing shutdown path (~15 lines).

OpenJDK jdk25u src/hotspot/os/posix/jvm_posix.cpp JVM_RegisterSignal rejects
FPE/ILL/SEGV/QUIT; signals_posix.cpp uses SIGUSR2 for VM thread suspend/resume.
The reflection adapter cannot implement the tested POSIX contract safely. Replace
it with a process-owned native bridge (~220 lines C/Java/build integration):
async-signal-safe pipe writes for externally delivered signals, saved JVM handlers
for internally generated signals, blocking pipe read on a Java platform thread.
No Java upcall from signal context. Preserve inherited ignored HUP. Keep bridge
code/descriptors alive until process exit so an in-flight native handler cannot
use an unloaded function or reused descriptor. Existing runtime owns all effects.

CI 40274221191 compiles the parser and Java signal slice. Its Java suite has one
stale expectation for the former "Parse error" text; update the existing assertion
to "Line 1:  syntax error". Conformance still running while native work proceeds.
Native bridge additionally dispatches ALRM to timer checkpoint and panic signals
to existing panic snapshot/abort, preserving inherited HUP ignore. Build uses a
POSIX C compiler and packages the bridge in the application resources.

CI 40274221191 finished on newer suite 000535ab: 83 failures, 10956 passes,
2012 skips, stopped after default HUP shutdown (before native bridge commit).
Local suite worktree advanced to 000535ab. Compiler validation incorrectly
treated VM-owned pass() as an unknown catalog builtin; exempt that existing
language operation, and order unknown-call diagnostics at closing parens so
nested calls report inner first. Log reopening also needs Toast's existing
"LOGFILE: Reopening due to remote request signal." message (server.cc:823).

Next builtin slice (~90 lines): expose existing VmState task-local storage via
task_local/set_task_local (Toast tasks.cc:2924-2956), getenv with binary encoding
and wizard permission (system.cc:44-62), and respond_to using existing callable
verb lookup/read permission (verbs.cc:553-589). Add no backing stores or services.
Correct existing raise default text, unknown-local text, and builtin arity text
from execute.cc:3500 and functions.cc:240-255 (~25 lines).

Native CI 40274951455 built successfully. Java OperationsCommandTest exposed
the listener-bind-before-signal-install race introduced by moving registration
after MooServer construction. Install the native handler before construction;
buffer signals in its existing pipe until serve finishes runtime startup, then
start dispatch (~12 lines). The same CI reports a scheduler stress timeout,
whose cause is not yet established; do not describe it as fixed or rerun around it.

Raise payload repair (~40 changed lines): Toast execute.cc:3500 stores arbitrary
Var code in BI_RAISE. Carry MooValue in the existing raised-result, pending-error,
uncaught-error and snapshot fields, and through finally unwind. Named catches
match ErrorValue codes; ANY catches all values. No parallel exception path.

Anonymous property cases are blocked at builtin entry points, not layout rebuild:
WorldTxn.refreshAnonymousPropertyLayouts already updates descendant slots.
property_info rejects anonymous values with E_TYPE and is_clear_property with
E_INVARG. Use their existing world.property overload and anonymous body metadata
(~20 lines) per Toast property.cc:66-105,299-328.

CI 40275811465 stopped at compilation: Error Prone rejects the anonymous cast
inside the receiver-test ternaries. Replaced those with typed pattern switches.

Fork repair uses existing irrevocable authorization and checkpointingWork, not
a second scheduler (~100 changed lines replacing the fork boundary branch).
Before allocating an ID, rerun the current segment at its publication turn.
Register the child for same-task introspection/persistence, bind its ID into
the copied locals, continue the parent in the same WorldTxn, and make children
runnable only after that segment publishes. Toast tasks.cc:1258-1296 copies the
environment after binding the named ID. Task abort also publishes effects already
performed; only speculative conflicts roll back (architecture research line 30).

Resume repair (~45 lines): updateSuspended must register foreground tasks too.
Keep zero-delay waits in existing checkpointingWork until publication, search
that owner as well as timedWork in resume(), and retain one queued activation.
An attempt whose wake value was changed by an earlier ticket must retry from its
existing snapshot with the new value. ErrorValue resumes raise at suspend.
These are the producer paths in fork_timing.yaml's zero/expired-resume cases.

CI 40276121013 conformance completed: 93 failed, 11223 passed, 1958 skipped.
Java compileTest needs its TaskRegistryTest error helper to assert ErrorValue
after RaisedError was widened; fixed. Native signal startup failures are not
yet reverified by the Java suite because that compilation failure stopped it.
