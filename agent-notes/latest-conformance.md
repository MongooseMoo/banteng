# Latest MOO conformance

## Goal

Make Banteng pass the latest tests on MongooseMoo/moo-conformance-tests main.
Q authorized implementation and a PR on 2026-10-25 with "Make it so!".
Necessary redesign/cleanup is in scope; weakening tests or skipping failures is not.

## State

- Isolated branch `fix/latest-conformance` starts at Banteng master `2ccad0597d7a6889e1607174cd2e6513a2032542`.
- PR: https://github.com/MongooseMoo/banteng/pull/82. Worktree: `C:/Users/Q/code/banteng-latest-conformance`.
- Current pushed implementation head: `a8f2390`; CI40289133756 completed. No merge or release.
- Suite main refreshed to `3d51b089017a1985b42ab5ee71159ebad3fff2b6` on October26. CI checks out main on every run.
- Current behavioral evidence:a8f2390 / CI40289133756, tested merge1eca5fd against suite main3d51b08. Java/static/stress/benchmarks, CodeQL, workflow lint and dependency review passed. Conformance:1 failure,11558 passed,1771 declared skips. Only failed_dump_selects_existing_output remains (expected5, actual2356 after a successful checkpoint).
- Fork/suspend and waif task_stack cases passed on the output-order repair. The source race is fixed; this single CI run does not prove every possible intermittent timeout is eliminated. No local CI/test/build runs.
- Q additionally requested PR comments addressed. Original two are fixed. Callback cleanup is pushed; match-all-pattern false positives have JLS-backed replies. No review or alert was dismissed.
- Q approved the upstream fixture correction ("yeax fix in conformance tests"). Controller PR157 adds confined chmod; dependent fixture is local until that controller is trusted on main. See the controller/data task notes in their isolated conformance worktrees.

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

Finish the ordinary checkpoint-failure repair below, read both repositories'
PR CI, and land the approved upstream fixture correction through the controller
then data dependency. No separate merge go-ahead has been received. Keep original
checkouts untouched. Historical implementation slices and evidence follow below.

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

83043f5 pushed the scheduler slice. CI 40277110685 stopped before compilation:
Maven Central returned HTTP 429 fetching rewrite-bom 8.89.0. No scheduler result.

Next smallest compiler/VM repair (~90 lines): charge list construction by its
syntactic first element, not the runtime list length; fold negative literals as
Toast parser.y:597-615 does; make FIRST/LAST free and count both index update and
owner assignment (opcode.h:49-129). Property range assignment currently throws
IllegalArgumentException in MooCompiler; add its missing operation, sharing the
existing range replacement implementation and PropertyOps write permission path.
This explains the lost connection beginning at tick_property_write_and_indexed_writes;
the exact server exception is not available in the current CI artifact.

CI 40277890765 reached compilation and rejected VmState.continueAfterFork's local
type: forkRequest holds ForkRequest, not VmSnapshot.Fork. Corrected that local
declaration; the returned immutable snapshot still carries the copied environment.

Utility slice (~65 lines): implement string/binary/value HMAC with the existing
Bouncy Castle dependency and shared binary decoder, SHA1/SHA256 only, uppercase
hex or all-byte ~XX output (Toast crypto.cc:207-230,520-699). Preserve empty keys.
SQLite duplicate open must carry the existing handle and exact message, per
sqlite.cc:247-255; use the existing raised-error result (~6 lines).

Salt (~65 lines) uses existing prefix validation and binary decoder, with the
documented crypt alphabets and byte grouping from Toast's dependencies/crypt/
crypt_gensalt.c and crypt_blowfish.c:881-904. No new native dependency.
String concatenation must use the existing random_bytes quota rule before
allocation (execute.cc:1482); extract that rule for reuse (~25 changed lines).

e3e0d7f Java artifact12631200561 gives the checkpoint cause: the newly registered
foreground suspension remains visible while its resumed task is executing. It
appears in its own queued_tasks() (fixtures kill it), and its stale suspension
enters its own checkpoint. Exclude the executing caller from queued_tasks and
checkpoint durable tasks, reusing durableTasksExcluding already used by panic.
Keep single opcode decoding by passing the decoded opcode into tick accounting.
Updated existing bytecode snapshots and changed-semantics expectations only.

Float literals (~10 lines): Toast code_gen.cc:245 interns equal typed constants
across a program and its fork vectors. Preserve the first equal float's payload
in compiler state using existing FloatValue equality; this accounts for signed
zero reuse without changing standalone negative-zero rendering.

Error text (~25 lines): use existing structured ErrorOps.raise for missing
property names and scatter/string-add type mismatches, matching execute.cc:918
and 1510 and unparse.cc:193 type names. No new exception representation.

CI 40278071766 conformance completed (90 failures,11226 passes,1963 skips).
Earlier tick literal/list/index cases now pass; remaining tick failures cover
break/continue, named fork, exception/scatter charges and extended-op exhaustion.
Fork ID binding and error-valued resume now pass. Zero-fork observation and expired
resume still fail. Waif queued_tasks self-kill causes a new connection-loss cascade;
startup checkpoint failures match the Java evidence addressed above.

Decompiler repair (~25 functional lines plus moving the expression helpers into
the existing Renderer): honor verb_code's parenthesize flag per unparse.cc:350-374,
use the existing FloatValue literal renderer, and split canonical source on LF
only. Java String.lines() treats literal CR bytes as source line breaks in both
set_verb_code and verb_code; the roundtrip fixture explicitly contains chr(13).

Remaining Java checkpoint cause (artifact12631711672): Anon5/Anon6 execute
shutdown(); kill_task(task_id());. TaskRegistry marks the current task canceled,
so publishAttempt discards its shutdown effect. Toast tasks.cc:2774-2785 returns
ABORT_KILL for self-kill. Add a killed terminal result/outcome (~25 lines), commit
prior effects and suppress timeout handling. Other-task cancellation stays as is.
PropertyLayoutEngine's three chown checks use 8; db.h:317 and our permission
parser both define c as 4. Correct those checks (~3 lines).

f55f619 CI40279191363 completed:54 failures; utility, error-text, signed-zero,
queued-waif and most startup checkpoint cases now pass. Java has only Anon5/6
checkpoint failures. Fork timing fixtures call missing string_hash, so their
stored verbs never compile; implement the hash family (~45 lines sharing HMAC's
input/hex conversion) before changing scheduler behavior further. Toast
crypto.cc:395-516 defines the algorithms and binary flag. Fix bcrypt prefix
minimum length (parse_prefix requires >=7 when a cost separator is present).

05f55a6 Java CI has one failure: MooUnparser's new dependency on FloatValue
violates the existing independent syntax/value package rule. Remove that import;
render source floats with Java numeric formatting directly, expanding exponents
in the ordinary fixed-format range while preserving source roundtrip precision.

Connection repair (~100 lines): finish socket input at its first suspension,
drop the server's login/command completion callback, and keep the suspended MOO
activation as an ordinary task. Return prefix/output/suffix at that boundary;
later task output goes through existing listener writes. This lets the same
socket reader consume read()'s next line. Toast tasks.cc:838-860,914-919 ignores
completion values after suspension and writes command suffix immediately.
Route OOB before login/read, strip #$" quoting once, mark initial login and
in-band tasks as last-input tasks, and use caller #-1. New players get only
user_created (tasks.cc:956 player_connected classification), not user_connected.

05f55a6 CI40280281199 conformance completed:39 failures; decompiler/CR roundtrip,
anonymous slot owners, self-kill startup checkpoints, hash-dependent fork and
expired-resume fixtures now pass. Java's only failure is the syntax dependency
already corrected locally. Input lifecycle changes are ready for CI.

Verb setup causes: WorldTxn.writePropertyFor omits intrinsic r although reads
recognize it (Toast db_properties.cc:687 writes FLAG_READ), and add_verb accepts
only at/in prepositions while set_verb_args already parses the full vocabulary.
Add the missing flag case and reuse that existing parser (~8 changed lines plus
moving its 40-line body into a shared method). These failures left captures as
error tuples or failed to create the verb, causing later introspection errors.

218c622 Java CI exposed the synchronous embedding contract: executeLine callers
await a complete result, while MooServer's socket reader must be released at the
first suspension. Keep that choice in the existing ingress entry (~45 lines):
future, socket-completion flag, accumulated output. Server uses receiveLine and
acceptConnection; ordinary executeLine/openConnection retain completion waiting.
The suspended MOO task still loses its server callback, matching Toast semantics.

218c622 CI40281328573 conformance completed:32 failures. Initial login caller,
new-user hook selection, prelogin OOB/quote, read-driven commands, delayed login
and proxy read login now pass. Input completion reaches its queue_info assertion;
one hold/OOB option case times out. Java's changed embedding contract failures
are addressed by the ingress completion choice above; stress timeout still needs
its own cause diagnosis.

Control-flow tick repair (~55 lines): source opcode.h distinguishes extended
charges (decrement only) from ordinary charges (abort when remaining <=0).
Break/continue JUMPs already carry AST paths, so count those as EOP_EXIT.
FORK's following STORE_LOCAL is part of OP_FORK_WITH_ID and costs no extra tick.
Exception owner costs one extended tick; each specific error-code list costs
one ordinary tick, ANY costs zero. Catch-variable binding and catch-expression
code extraction cost ordinary ticks (code_gen.cc:920-949,1080-1120). Scatter's
used default assignment costs one ordinary tick. Preserve existing opcode owners.

Range replacement cleanup (~40 lines replacing 120): Toast execute.cc:2354 and
list.cc:302,551 accept from<=1 and to>=length, rejecting only from>length+1 or
to<0. Build prefix + replacement + suffix directly, which also handles 0..0
and inverted ranges without the current duplicated special cases.

Seconds timeout cause: VmState tracks CPU budget but MooVm never checks it;
startTaskTimeout always labels every abort as ticks. Poll the existing CPU
budget at calls and each 1024 charged ticks, and distinguish the seconds terminal
outcome (~30 lines). No additional timer/thread. Existing zero-second embedding
states are unlimited (both elapsed and remaining CPU budget are zero).
Shutdown race: a serve thread can submit startup after gracefulShutdown closed
the scheduler. Late STARTUP/TIMEOUT work is obsolete; return no output when
already closed, and skip restored timer activation after closure (~8 lines).

Small introspection repairs (~25 lines): verb_code of an empty program is {},
disassemble identifies its main code vector (disassemble.cc:226), source/vcs
identifies this Git-backed source tree, and queue_info reads the existing last
input task registry. Unknown native commands clear that ID at publication as
tasks.cc:859 does, through an existing runtime effect delivery path.

Next slice: object_bytes (~40 lines) validates object type, existence, then
wizard permission in Toast objects.cc:1037-1060 order. Account for the object
record, verb names/source and defined/inherited property slots using the existing
value_bytes layout. Verb source is Banteng's owned program representation;
this is logical accounting, not a measurement of resident JVM allocations.
Update the existing seconds-exhaustion assertion to its explicit terminal kind.

URL codec slice (~50 lines): implement byte-oriented percent escaping and
unescaping directly, preserving literal plus, malformed escapes and C-string
NUL truncation (curl.cc:772-828). Reuse StringValue's byte representation;
neither Java form encoding nor Unicode URL conversion matches this contract.

Spellcheck slice (~110 lines): bind the same Aspell en_US API as Toast
spellcheck.cc, with one synchronized process-lifetime speller and confined
per-call word/enumeration storage. Native library failures return E_INVARG.
Install the library and English dictionary in the two existing CI environments
that execute the server. No substitute dictionary or signature-only builtin.

e2badd2 CI40284242157: conformance finished with 13 failures. Tick control,
seconds timeout, range replacement, empty verb/header/source metadata and
queue_info fixes pass. Java only failed the old seconds terminal-kind assertion,
updated here. Remaining: curl registration/implementation, three recycle hook
cases, three protected valid redirects, portable failed-dump fixture mismatch,
and a task_stack waif-owner timeout whose cause is not yet established.
Current object_bytes/URL/Aspell changes are ready for PR CI, not yet verified.

bff08d4 CI40285380542 stopped at compilation: Aspell.check's native string
reinterpretation lacked the restricted-FFM annotation used by other host calls.
Add it at that method boundary; no behavioral verification ran for this slice.

Protected dispatch (~75 lines): reload the existing protect_<name> options at
runtime initialization and load_server_options. That reload mutates host flags,
so use the existing IRREVOCABLE publication boundary. Before argument validation,
shared catalog dispatch returns a wrapper-verb call when this != #0 and the
cached flag is set (functions.cc:219-239). The VM pushes an ordinary #0 verb
frame; absent wrappers allow wizard fallback and otherwise E_PERM. This also
covers call_function because it uses the same catalog dispatch.

ea9e44f CI40285555364 compilation reached Error Prone, which rejected the
assignment inside Aspell's while condition. Split the call from the condition.

Recycle slice (~40 changed lines): retain existing recycleTarget continuation
on every recycle/exit hook frame. On return, repeatedly detach the first content
or the object itself to #-1, running exitfunc before continuing; then remove it.
Toast objects.cc:759-775,850-875 restarts this scan after every hook. Reaching
the hook recursion limit falls through into removal, rather than E_MAXREC
(objects.cc:843). Outer continuations then see the missing object and E_INVARG.

Q requested PR82 comment repairs. Current inline comments are CodeQL169 on
MooUnparser's 22-type chain and CodeQL168 on KillAbort's unnamed binding.
Smallest repair: convert only that chain into an exhaustive sealed-type switch
(~50 changed structural lines, same rendering bodies), and use KillAbort()'s
empty record pattern (~1 line). No visitor abstraction or behavior change.

db268ff CI40285958878 admission failed before listening: constructor reload read
the committed WorldTxn root, which forbids direct reads. Open/close an initial
read transaction for that reload. Existing Java manifest/result shape assertions
also need their new builtin names, ProtectedCall record, and load-options effect.

Curl slice (~180 lines): implement the tested legacy curl(url, headers, timeout)
contract through the system libcurl, retaining HTTP/HTTPS/DICT allowlist, TLS
verification, bounded response bytes and native error maps (curl.cc:511-639).
Resolve existing server timeout/size options before handing pure request data
to existing HostWork. Bind Linux 64-bit libcurl using curl/curl.h enum values
(https://raw.githubusercontent.com/curl/curl/master/include/curl/curl.h).
The newer options-map extension is not part of this slice; no signature stub.

2952abd CI40286262997 compiler rejected FirstIndex()/LastIndex() because those
records carry Optional<SourceSpan>. Match the ignored component with (_) in
both arms. The KillAbort record is empty and its pattern remains KillAbort().
PR comment replies link 2952abd and explicitly state CI was still running;
threads have not been resolved or any review dismissed.

14ae742 CI40286554933 rejected NativeCurl.Result's byte[] component under
ArrayRecordComponent. Store immutable Latin-1 String instead, preserving bytes.

Disk-size repair (~15 lines): Toast db_file.cc:1128 increments dump_generation
before attempting output; db_disk_size:1285-1294 then stats output and falls
back to input if unavailable. Its internal -1 is translated by the public
bf_db_disk_size (server.cc:2638-2642) into structured E_QUOTA. Replace
checkpointPublished with checkpointAttempted and follow that selection order.
This fixes real selection semantics but does not make the suite's Toast/Barn
temporary filename blockers prevent Banteng's PID-named checkpoint from writing.

3176fc0 CodeQL completed successfully; original PR comments168/169 are fixed
and their threads are resolved by current GitHub state. New comments171-173:
171's required unused C callback context is removed from the Java method and
discarded by its existing method-handle binding. 172/173 misinterpret (_) as a
readable variable: JLS25 14.30.1 states a match-all pattern declares no pattern
variables. Reply with that source; keep the exhaustive switch, no alert dismissal.
Correct object_bytes base count to160: Object152 plus pointer8 in the 64-bit
db_private.h layout. Verb/source and property accounting remain unchanged.

CodeQL170 points to a real pair of separately synchronized runtime reads:
startsInputTask followed by connectionPlayer. A concurrent connection switch
can separate the input classification from its owner. Replace startsInputTask
with one synchronized OptionalLong-returning inputTaskPlayer and consume that
snapshot in both scheduler ingress paths (~25 changed lines, no extra lock).
Also move set_verb_code descriptor validation ahead of object existence, after
code-line and receiver-type validation (verbs.cc:515-528, ~8 moved lines).

3176fc0 conformance CI finished: two failures, failed-dump fixture and
fork_timing:suspend_zero_read_only_parent_observes_ready_child timeout. Waif
task_stack passed this time; no claim yet that its timeout is fixed.

Output-order cause found in primary source: publishSegmentResultOutsideMonitor
enqueues the zero-delay continuation before completing ingress.future; MooServer
writes returned prefix/output/suffix only after receiveLine returns. Notifications
to the task player are state.output (MooRuntime.applyNotificationRequests:3063),
so a fast continuation can write result/suffix through writeDetachedOutput
before the original prefix. Transport._receive:543 ignores data while looking
for a prefix, then waits forever for a result already discarded.
Smallest repair (~25 lines): keep the socket connection ID in existing Ingress,
write socket output on publication before queuing children/wakes, and complete
socket futures with no output. Embedding futures still accumulate their results.
No extra queue, lock or gate; the publication owner also owns output order.

7398e6c pushed these repairs; CI40288810612 is running. PR comment171 has a
reply describing the callback binding change; 172/173 have JLS-backed replies.
Q scope question remains pending: change the upstream failed-dump fixture to
induce write failure independently of Toast/Barn temp names, or keep main
unchanged. No upstream fixture edits have been made while that choice is open.

7398e6c CI40288810612 compilation rejected both ifPresent callbacks under
GuardedBy: the checker cannot transfer the surrounding monitor ownership into
those lambdas. Use direct OptionalLong presence checks within the existing
synchronized blocks. No runtime verification ran on that head.

a8f2390 CI40289133756 completed: Java/static/fuzz/schedulerStress/jcstress/jmh
and namespace rewrite all passed, as did CodeQL/workflow/dependency jobs.
Conformance main3d51b08: only failed_dump_selects_existing_output failed,
expected5 versus2356;11558 passed and1771 declared skips. The fork/suspend
timeout case passed. Original comments168/169 and callback171 are fixed;
172/173 remain explained match-all-pattern false positives, not dismissed.
Current code is published. This final status-note update is committed locally
without re-pushing unchanged implementation while upstream scope is pending.

Q approved the fixture fix upstream. Conformance controller PR157 (38d9686)
candidate quality passed; six Toast jobs are running. Its separate trusted-head
probe failed on GitHub HTTP502, not a candidate test. No check was overridden.

Ordinary checkpoint-failure repair (~7 lines): Toast bf_dump_database
(server.cc:2618-2627) queues the checkpoint and returns zero; its later dump
failure does not raise into that completed task. Banteng publishEffect currently
rethrows every checkpoint IOException after task commit, dropping the response.
For an ordinary dump, log the failure and keep the existing checkpoint_finished
false notification. Preserve shutdown's throwing path. Also correct dbDiskSize's
unavailable-file result to structured E_QUOTA: the earlier change read the
internal db_disk_size helper but missed its public builtin wrapper.
