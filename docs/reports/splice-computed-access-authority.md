# Call splicing and computed access authority

## Scope

This record freezes the exact contract needed to complete the managed
`splice::` family. The active gaps are argument splicing in builtin calls,
computed property reads and writes, computed verb calls, handler operand-stack
restoration, and the direct world mutations required by those rows: `max`,
`recycle`, `add_verb`, `delete_verb`, and `set_verb_code`.

This slice does not authorize a runtime splice value, argument adapter,
property resolver, call-context interface, verb-dispatch service, mutable world
facade, sender, or alternate evaluator. Static and computed access must share
the existing concrete owners.

## Verified identities

- Banteng committed base for the active source slice:
  `d29107c372d7fb5b5e977ee1677e86045ce75a79`.
- Banteng oracle procedure:
  `docs/reports/toast-oracle-identity-2026-07-14.md`.
- Toast source and executable: `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`, executable
  `/root/src/toaststunt/build-release/moo`.
- Barn implementation reference at audit time:
  `6400748c69a2b5fe8eb1f90fbcfa28b34c0590e9`.
- Durable conformance authority after the focused additions and corrections:
  `../moo-conformance-tests` at
  `4de57abc69614ccac71ae8fb0848a0771fde4ea2`.
- Stock profile: `../barn/profiles/toast/stock-wsl-testdb.json`.

## Normative Barn specification

- `../barn/spec/operators.md:224-247` and
  `../barn/spec/grammar.md:180-200` define `@expression` in list and call
  argument construction, left-to-right flattening, and `E_TYPE` for a
  non-LIST splice operand.
- `../barn/spec/operators.md:815-839`, `../barn/spec/objects.md:139-153`, and
  `../barn/spec/builtins/properties.md:9-26` define static and computed
  property read/write syntax and errors.
- `../barn/spec/operators.md:129-147` specifies that assignment returns its
  assigned right-hand value.
- `../barn/spec/operators.md:878-892`, `../barn/spec/objects.md:219-229`, and
  `../barn/spec/builtins/verbs.md:9-23` define computed verb-call syntax and
  ordinary call errors.
- `../barn/spec/objects.md:250-268` defines inherited verb dispatch.
- `../barn/spec/statements.md:493-500,693-728` defines handler selection and
  propagation but does not state operand-stack restoration at a catch or
  finally boundary.
- `../barn/spec/builtins/math.md:50-58,461-472` defines `max()` over a
  homogeneous INT or homogeneous FLOAT argument sequence and `E_ARGS` for an
  empty sequence.
- `../barn/spec/builtins/objects.md:70-96` defines the `recycle()` hook,
  owner-or-wizard permission, topology destruction, invalidation, and zero
  result. It does not state what happens to a hook error.
- `../barn/spec/builtins/objects.md:100-122` defines `valid(object)` as a
  non-throwing existence check for object references. The implementation and
  Toast source below correct the spec's omission of `E_TYPE` for non-object
  values.

The Barn specification does not state the computed-name type contract, name
case behavior, or expression evaluation order. Those gaps are frozen below by
Toast source and managed rows rather than intuition.

## Current Barn implementation path

Argument splice:

- `../barn/compiler/compiler.go:37-55` is the public compile entry.
- `../barn/parser/parser.go:286-298` and `../barn/verb/ir.go:256-262` own
  splice syntax and IR.
- `../barn/bytecode/compiler.go:1273-1369` builds one argument LIST with the
  ordinary list construction/append/extend opcodes and marks list-argument
  call mode.
- `../barn/vm/op_list.go:78-100` and `../barn/vm/op_misc.go:10-24` perform
  runtime flattening and type checking.

Computed property access:

- `../barn/parser/parser.go:460-495,623-633` and
  `../barn/verb/ir.go:226-234,303-312` own reads and assignment targets.
- `../barn/bytecode/compiler.go:966-985,1481-1506` lowers dynamic property
  operations.
- `../barn/vm/op_property.go:15-84,137-223` requires a STRING name, raises
  `E_TYPE` or `E_PROPNF`, and returns the RHS after assignment.
- `../barn/db/store/store_properties.go:43-56,69-121,428-458` owns
  case-insensitive lookup and replacement.

Computed verb access:

- `../barn/parser/parser.go:497-557` and
  `../barn/verb/ir.go:236-245` own syntax and IR.
- `../barn/bytecode/compiler.go:1509-1579` lowers the call.
- `../barn/vm/op_verb.go:30-130` requires a STRING name and invokes normal
  callable dispatch.
- `../barn/db/store/store_verbs.go:9-65,168-263` owns case-insensitive,
  inherited callable lookup.

Barn agrees with Toast on splice flattening/errors, property name types and
errors, assignment result, name case behavior, and computed verb dispatch.
It disagrees on two observable evaluation orders: Barn lowers a computed
property RHS before its target object/name, and lowers computed verb arguments
before the computed verb name. Neither ordering is copied into Banteng.

For the adjacent contracts required by this family:

- `../barn/vm/control.go:173-265` and `../barn/vm/vm.go:710-785` own handler
  installation and error routing. Barn does not provide the Toast marker-based
  operand-stack truncation used by the frozen row.
- `../barn/builtins/math.go:75-105` implements homogeneous INT and homogeneous
  FLOAT `max()` and rejects mixed numeric kinds with `E_TYPE`.
- `../barn/builtins/objects.go:305-382` owns `recycle()`, and
  `../barn/db/store/store_lifecycle.go:145-238` owns destruction. Barn invokes
  the hook before destruction but explicitly discards its error. That differs
  from managed Toast, where the original hook error continues propagating
  after destruction.
- `../barn/builtins/objects.go:384-415` owns `valid()`: one object-flavored
  argument returns INT 1 or 0, wrong arity raises `E_ARGS`, and a non-object
  value raises `E_TYPE`.
- `../barn/builtins/verbs.go:352-540,670-768` owns `add_verb`, `delete_verb`,
  and `set_verb_code`. It corroborates validation and compile-before-install,
  but its delete permission check is incomplete and its set-code lookup can
  select an inherited verb. Toast controls those disagreements.
- `../barn/spec/builtins/tasks.md:618-668` and
  `../barn/builtins/tasks.go:399-434` incorrectly narrow `raise()` to an ERR
  code. The active row requires only that narrow `raise(E_DIV)` path; full
  arbitrary-code payload support is explicitly deferred below.

## Current Toast implementation path

Argument splice:

- `src/parser.y:700-740` owns argument syntax.
- `src/code_gen.cc:534-553` builds one argument LIST; splices use the ordinary
  list check/append path.
- `src/execute.cc:1277-1284` raises `E_TYPE` for a non-LIST splice operand.

Computed properties:

- `src/parser.y:389-420` represents both static and computed names as
  expressions.
- `src/code_gen.cc:584-590,707-714,883-907` lowers reads and writes.
- `src/execute.cc:1855-1910,1959-2081` requires a STRING name, returns
  `E_TYPE` or `E_PROPNF`, and pushes the RHS after a successful write.
- `src/db_properties.cc:492-575` owns case-insensitive built-in and ordinary
  lookup.
- Write evaluation order is object, name, then RHS.

Computed verbs:

- `src/parser.y:421-440` uses one verb-call expression for static and computed
  names.
- `src/code_gen.cc:814-819` evaluates object, verb name, then arguments.
- `src/execute.cc:2115-2193` requires a STRING name and LIST arguments.
- `src/execute.cc:662-782` and `src/db_verbs.cc:473-528` own normal callable
  dispatch, ancestry, and executable filtering.
- `src/db_verbs.cc:227-237` and `src/utils.cc:76-109` own case-insensitive
  wildcard name matching.

Handler restoration, `max()`, and `recycle()`:

- `src/execute.cc:245-304` installs stack markers and removes every partial
  operand above the active marker while routing catch and finally control.
- `src/numbers.cc:483-510` and its registration accept one or more numeric
  arguments, preserve a homogeneous INT or FLOAT result kind, and reject mixed
  numeric kinds with `E_TYPE`.
- `src/objects.cc:788-949` validates `recycle()`, checks `controls2()`, invokes
  inherited `:recycle`, resumes at builtin continuation 2, removes contents and
  location links, reparents children, and destroys the object.
- `src/execute.cc:331-395` resumes a builtin continuation during both normal
  return and error unwinding. On hook error, continuation 2 still destroys the
  object and the original error keeps unwinding.
- `src/objects.cc:283-299,1351` registers `valid` with arity one and `TYPE_ANY`;
  the body returns INT 1 or 0 for an object-flavored value and `E_TYPE`
  otherwise.
- `src/verbs.cc:76-210,668-669` owns `add_verb`: it validates the info and
  argument-spec lists, target, owner, permissions, and names; enforces object
  write permission plus owner assignment permission; and returns the new
  one-based local verb index.
- `src/verbs.cc:215-268,670-671` owns `delete_verb`: descriptors are a string or
  positive integer, lookup is local-only, missing verbs raise `E_VERBNF`, and
  success returns zero.
- `src/verbs.cc:504-549,674-675` owns `set_verb_code`: it validates every source
  line, resolves a local descriptor, requires programmer and verb-write
  permission, compiles before installation, returns diagnostics without
  mutation on failure, and returns an empty LIST on success.
- `src/db_verbs.cc:195-214` and `src/include/db.h:488-500` freeze persistent
  verb packing: `r/w/x/d` are bits 1/2/4/8, direct and indirect argument specs
  are packed at shifts 4 and 6 using none/any/this values 0/1/2, and the
  preposition is stored separately as none `-1`, any `-2`, or its table index.
- `src/execute.cc:3499-3516,3777` registers `raise` for one through three
  arguments and carries arbitrary code, message, and value. The current VM's
  `ErrorValue` channel cannot yet represent that full payload.

## Durable conformance evidence

The original 17 rows are in
`../moo-conformance-tests/src/moo_conformance/_tests/language/splice.yaml`.
They cover list splice construction/errors, source escapes, one builtin-call
splice, computed property read/equivalence/missing/write, and a computed verb
call.

Conformance commit `8a79771` adds seven discriminators:

- `splice::splice_non_list_in_function_call` proves `E_TYPE` at the argument
  splice boundary;
- `splice::computed_property_non_string_name` proves `E_TYPE`;
- `splice::computed_property_assignment_returns_rhs` proves both the
  expression result and stored value;
- `splice::computed_property_assignment_evaluation_order` proves Toast's
  object/name-before-RHS order and rejects Barn's lowering;
- `splice::computed_verb_non_string_name` proves `E_TYPE`;
- `splice::computed_verb_uses_callable_dispatch` proves case-insensitive
  inherited dispatch skips a non-executable child match;
- `splice::computed_verb_evaluation_order` proves Toast's
  object/name-before-arguments order and rejects Barn's lowering.

Conformance commit `4de57ab` adds or corrects three further discriminators:

- `splice::caught_error_discards_partial_guarded_expression_operands` proves
  that catch routing discards partial call, property-name, and nested-splice
  operands while preserving enclosing operands;
- `recycle::programmer_cannot_recycle_object_owned_by_another_programmer`
  replaces a skipped placeholder and proves owner-or-wizard permission;
- `recycle::recycle_hook_error_propagates_after_recycling_permanent_object`
  proves the non-obvious Toast contract that the hook error propagates but the
  permanent object is already invalid afterward.

Existing durable rows separately cover computed property case folding in
`objects/property_lookup.yaml:8-25`, static verb inheritance in
`builtins/objects.yaml:717-736`, and executable ancestor selection in
`audit/verb_dispatch_toast_oracle.yaml:150-186`.
`builtins/objects.yaml:1697-1721`, `builtins/recycle.yaml:213-221`, and
`generated_builtins/valid.yaml:22-40` already durably cover a created object,
recycled object, `#-1`, and wrong arity. The managed hook row above directly
uses the recycled-object result; no duplicate `valid()` row is needed.

Existing durable verb rows cover the active mutation dependencies without a
duplicate addition:

- `generated_builtins/add_verb.yaml:22-52`,
  `builtins/add_verb_call_shapes.yaml:9-21`, and
  `builtins/verbs.yaml:22-140` cover signature, shape errors, and the returned
  one-based index;
- `generated_builtins/delete_verb.yaml:22-41`,
  `builtins/delete_verb_call_shapes.yaml:9-62`, and
  `builtins/verbs.yaml:141-204` cover descriptors, zero success, `E_INVARG`,
  and `E_VERBNF`;
- `generated_builtins/set_verb_code.yaml:22-46`,
  `builtins/set_verb_code_call_shapes.yaml:9-33`, and
  `builtins/verbs.yaml:533-599` cover source types, stored compilation, and
  empty-LIST success;
- `generated_builtins/raise.yaml:22-47` and
  `builtins/raise_call_shapes.yaml:9-65` freeze the eventual full `raise`
  signature. The active recycle discriminator itself proves the currently
  required one-argument ErrorValue path.

## Managed oracle and Banteng baseline

Every Toast command used the exact managed procedure from
`docs/reports/toast-oracle-identity-2026-07-14.md`, from
`C:\Users\Q\code\barn`, changing only `-k`. Toast was never run directly
against the tracked fixture.

- The original family passed `17 passed, 11478 deselected`.
- The exact seven-row addition passed `7 passed, 11495 deselected`.
- The first expanded family passed `24 passed, 11478 deselected` out of 11,502
  collected.
- The three new or corrected rows passed `3 passed, 11501 deselected` against
  managed stock Toast.
- The final frozen family passed `25 passed, 11479 deselected` out of 11,504
  collected.

Before the additions, managed Banteng selected 17 rows and produced
`11 passed, 6 failed, 11478 deselected`. The failures were the one builtin-call
splice and all five computed access rows. All six stopped in parsing and
returned `E_INVARG`, so downstream world and verb prerequisites were not yet
executed. After the initial uncommitted implementation, managed Banteng passed
20 rows and failed only the four computed-verb rows. The final 25-row family is
the focused gate for the single active Java source slice.

## Frozen smallest Java representation

- Reuse the existing `Ast.Splice`. Permit it directly in `Ast.Call` argument
  lists; never add a runtime splice value.
- Make the existing builtin `CALL` consume one constructed `ListValue` plus
  its existing static builtin-name text operand. Remove compile-time numeric
  arity. Compile every argument list through existing `BUILD_LIST`,
  `LIST_APPEND`, and `LIST_EXTEND`, preserving left-to-right evaluation.
- Add `max` directly to the existing `BuiltinCatalog` switch.
- Implement `max` inline by branching on the first argument: all INT returns
  the greatest INT, all FLOAT returns the greatest FLOAT, mixed numeric kinds
  and nonnumeric values raise `E_TYPE`, and no arguments raises `E_ARGS`.
- Change existing `Ast.PropertyAccess` and `Ast.PropertyTarget` property fields
  from static strings to expressions. Static `.foo` becomes
  `StringLiteral("foo")`; computed `.(expression)` stores that expression.
  Do not add computed-property variants.
- Make existing `GET_PROPERTY` and `SET_PROPERTY` consume the name from the
  operand stack. Their exact stack contracts are object/name to value and
  object/name/RHS to RHS. The VM directly requires a `StringValue` and keeps
  calling existing `WorldTxn` read/write methods.
- Follow Toast write evaluation order: object, name, then RHS.
- Add `.name` directly to `WorldTxn.readObjectProperty()` using the existing
  `WorldObject.name()` field.
- Add only one unavoidable concrete
  `Ast.VerbCall(object, nameExpression, arguments)` and one `CALL_VERB` opcode.
  Static and computed verb names use the same form; the compiler evaluates
  object, name, then one constructed argument LIST.
- `CALL_VERB` directly type-checks the receiver and name, resolves an inherited
  executable verb through `WorldTxn`, compiles its stored program through the
  existing parser/compiler, and pushes one concrete verb frame. Do not add a
  dispatcher or call-context object.
- Extend the existing concrete frame return mode with VERB. Normal verb return
  pushes the raw return value into the caller; unlike EVAL, it is not wrapped
  as `{1, value}`. Error unwinding crosses both eval and verb frames.
- Move task permissions onto the existing concrete frame: ROOT receives the
  initial programmer, EVAL inherits it, VERB receives the resolved verb owner,
  and `set_task_perms` changes only the current frame. Popping a frame thereby
  restores its caller's programmer without a second permission stack.
- Populate callee locals directly with the existing runtime shape: `this`,
  `player`, `caller`, `verb`, `args`, and `argstr`.
- Add the captured operand-stack depth directly to the existing
  `VmState.ActiveHandler`. Capture it at `ENTER_HANDLER` and trim inline before
  catch, error-finally, and return-finally routing; do not add a restoration
  helper or put runtime depth in `HandlerSpec`.
- Keep `recycle` classified `IRREVOCABLE`. Validate arity, object type,
  validity, and owner-or-wizard permission directly in `BuiltinCatalog`.
- Add `TRANSACTION_READ` to the existing concrete effect enum and add `valid`
  directly to the catalog: one argument is required, a non-`ObjectValue`
  raises `E_TYPE`, and an `ObjectValue` returns INT 1 when present in the
  current `WorldTxn` snapshot or INT 0 otherwise.
- Extend the existing concrete `BuiltinCatalog.Result` with only the recycle
  target needed by the VM continuation. The VM resolves and runs inherited
  executable `:recycle` before destruction. The concrete verb frame carries a
  recycle continuation target; both normal-return and error-unwind paths call
  `WorldTxn.recycleObject()` before returning zero or continuing the original
  error. Do not add a callback, sender, dispatcher, or lifecycle interface.
- Existing `WorldTxn.verb(object, name)` remains the only lookup owner. It must
  skip non-executable local matches and continue through ancestry using Toast's
  case-insensitive wildcard matching; do not add a second recycle-specific
  lookup path.
- If no callable hook exists, recycle immediately and return zero. On normal
  hook return, a failed continuation recycle raises `E_INVARG`; while unwinding
  an existing hook error, a failed continuation recycle is squelched and the
  original error continues, matching Toast's builtin unwind rule.
- `WorldTxn.recycleObject()` performs one immutable map rewrite: remove the
  target from its location contents, move its contents to `#-1`, reparent its
  children to its parent in order, remove it from the parent's children, and
  remove the target and player index in one publication.
- Add direct `BuiltinCatalog` cases and immutable `WorldTxn` snapshot
  mutations for `add_verb`, `delete_verb`, and `set_verb_code`. Reuse existing
  `WorldObject` and `WorldVerb` records; do not add mutable wrappers.
- For `add_verb`, validate and pack info/argument specs inline, pass the current
  programmer, enforce target write and owner-assignment permission, append one
  existing `WorldVerb`, and return its one-based index.
- For delete and set-code, validate a string or positive-integer descriptor in
  `BuiltinCatalog`, resolve only the target's existing local verb list, and
  pass the concrete zero-based index to the existing `WorldTxn` mutation.
  Delete enforces target write permission, returns zero, and maps a missing
  local descriptor to `E_VERBNF`. Set-code enforces programmer plus verb-write
  permission, parses and compiles before mutation, returns a one-string
  diagnostic LIST on local parse failure, and returns an empty LIST on
  success. Do not add a descriptor or lookup abstraction.
- Keep only the active `raise(E_DIV)` minimum in this slice: one ErrorValue
  argument routes through the existing concrete error channel. Other arities
  and arbitrary code/message/value payloads remain failing, explicitly
  deferred work requiring a separately authorized raised-error representation;
  they must not be claimed conformant by this slice.

Focused Java tests must prove the missing rows through parser, deterministic
bytecode, VM, handler, and stored runtime seams. The kept slice must pass those
focused tests, `gradlew check`, `installDist`, the managed 25-row `splice::`
family, the two focused recycle rows, a read-only review, and a fresh plan
reread before commit.

## Object `.w` setup and ordinary property permission enforcement

The active durable row is
`../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:750-770`,
`audit_property_flags_deny_nonowner_read_and_write`. Wizard creates an object,
clears its built-in public-write flag with `obj.w = 0`, and adds direct ordinary
property `audit_secret` with value 10 and metadata `{#0, ""}`. The harness then
logs in as Programmer and requires both a read and a write of that existing
property to raise `E_PERM`. The row covers two distinct flag domains: built-in
object `.w` is causally required setup, while the later denial uses the
ordinary property's owner and `w` permission bit.

Barn's `../barn/spec/objects.md:139-170,197-220,451-485` defines property
syntax, ordinary `r`/`w` permission bits, built-in integer `.w`, zero as clear,
owner-or-flag read/write access, and Wizard bypass.
`../barn/spec/operators.md:53-88,813-840` and
`../barn/spec/errors.md:117-168` put type and validity errors before lookup,
`E_PROPNF` on absence, and `E_PERM` on a present but denied property.
`../barn/spec/builtins/properties.md:51-92,121-142,250-310` defines
`{owner, perms}`, empty permissions, and the add-property form. Its prose
sometimes says object owner where current Barn and Toast use the defining
property owner; source and live evidence, not that imprecise phrase, control
this row.

Current Barn lowers public property read and assignment at
`../barn/bytecode/compiler.go:966-988,1481-1506` and dispatches their opcodes at
`../barn/vm/vm.go:461-465`. `../barn/vm/op_property.go:15-83,137-222` validates
the property name and receiver, validates the object, resolves the direct or
inherited property, and only then checks read or write permission before
exposure or mutation. Its permission owners at lines 248-282 allow a Wizard,
the `PropertyView.Owner`, or the corresponding `PropRead`/`PropWrite` bit;
otherwise they return `E_PERM`.

Barn's built-in-property path at
`../barn/vm/op_property.go:300-337,359-427` maps `.w` to
`dbstore.FlagWrite`, accepts this row's integer zero, and clears the bit before
returning the assignment value. `../barn/db/store/object.go:226-252` assigns
that object flag value 32. That flag is separate from ordinary-property
`PropWrite` at lines 256-267. `../barn/builtins/properties.go:197-322,518-534`
stores explicit owner `#0` with zero property permission bits for the empty
string, and `../barn/db/store/store_properties.go:68-122,346-360,429-455`
retains those metadata through lookup and mutation. Managed eval supplies the
authenticated player as programmer through `../barn/scheduler/eval.go:55-79`
and derives Wizard status from the object flag at
`../barn/scheduler/scheduler.go:250-252`.

Pinned Toast source is
`aecc51e9449c6e7c95272f0f044b5ba38948459e`. The stock Test database and
harness identify Wizard as `#3` with Wizard status and Programmer as `#4`
without it. The stock eval command calls `set_task_perms(player)`; pinned Toast
`/root/src/toaststunt/src/execute.cc:3694-3706,3386-3425` and
`/root/src/toaststunt/src/verbs.cc:604-652` therefore execute the tested
expressions with the authenticated activation programmer, `#3` during setup
and `#4` during the assertions.

For setup, `/root/src/toaststunt/src/code_gen.cc:893-906` emits `OP_PUT_PROP`.
`/root/src/toaststunt/src/execute.cc:1959-1999,2048-2077` validates operands,
validity, and lookup before the built-in `BP_W` permission branch. Wizard `#3`
bypasses the object-owner/protection check. The mutation owner at
`/root/src/toaststunt/src/db_properties.cc:638-694` maps `BP_W` to
`FLAG_WRITE`, treats this row's integer zero as false, clears the object flag,
and returns the original zero assignment value. This built-in branch never
uses ordinary `PF_WRITE`.

`/root/src/toaststunt/src/property.cc:108-148,207-235` validates and installs
the row's ordinary property with owner `#0` and flags zero; Wizard setup may
select that different owner even after clearing object `.w`. Ordinary reads
compile through `/root/src/toaststunt/src/code_gen.cc:707-714` and execute at
`/root/src/toaststunt/src/execute.cc:1855-1911`. Ordinary writes use
`OP_PUT_PROP` at lines 1959-2082. Both paths validate operand types, object
validity, and property existence before calling
`db_property_allows` for `PF_READ` or `PF_WRITE`; a false result becomes
`E_PERM` before the value is exposed or changed.
`/root/src/toaststunt/src/include/db.h:314-318` defines the ordinary property
bits, and `/root/src/toaststunt/src/db_properties.cc:494-607,697-751` owns
case-insensitive lookup, defining-property owner/flags, and the exact
flag-or-owner-or-Wizard predicate. For programmer `#4`, owner `#0`, and flags
zero, both checks are false.

Barn and Toast agree on every asserted observation: Wizard may clear this
object's built-in public-write flag; integer zero clears it; Wizard may then
add a property owned by `#0` with no permissions; authenticated Programmer is
neither Wizard nor property owner; the present-property read and write each
raise `E_PERM` before exposure or mutation. They also agree that object
`FLAG_WRITE` and ordinary property `PF_WRITE` are independent. Barn's current
built-in setter accepts only integer values while Toast applies general truth
to `BP_W`; cross-type assignments are outside this row.

Committed Banteng `5160457` fails earlier than the displayed managed symptom.
`src/main/java/moo/world/WorldTxn.java:193-274` omits built-in `.w`, so setup
assignment returns false and `src/main/java/moo/vm/MooVm.java:333-345` raises
`E_PROPNF`. The conformance runner captures that failed setup result at
`../moo-conformance-tests/src/moo_conformance/runner.py:420-450,506-523` and
substitutes it as `obj`; the later read is effectively
`E_PROPNF.audit_secret`, whose non-object receiver produces the observed
secondary `E_TYPE` at `MooVm.java:313-319`. The earlier interpretation of that
`E_TYPE` as the direct permission result was wrong.

After that setup barrier, Banteng has a second independent disagreement:
`WorldTxn.readObjectProperty` exposes ordinary values and
`writeObjectProperty` changes them without an active programmer or the stored
`WorldProperty.owner/permissions`. `MooVm` therefore has no branch that can
produce the required `E_PERM`. The parser and compiler already emit the
correct property operations at
`src/main/java/moo/syntax/MooParser.java:380-407` and
`src/main/java/moo/bytecode/MooCompiler.java:263-266,304-308`.

The exact managed row passed pinned WSL Toast commit
`aecc51e9449c6e7c95272f0f044b5ba38948459e`: one selected, 11,504
deselected, in 3.90 seconds. The causally prior `.w` clear and both exact
`E_PERM` results are therefore frozen. Process inventory found only the
unrelated July 13 `/tmp/td.db` process, which was left untouched.

The smallest Java change keeps every existing concrete owner and signature.
`WorldTxn.writeObjectProperty` recognizes built-in `.w` before ordinary local
slots, requires this row's existing `IntegerValue`, and calls its existing
`replaceFlags` with object flag 32 and integer truth. The row does not read
`.w`, assign another value kind, or test a non-Wizard setter, so those surfaces
are not added here.

For ordinary properties, the existing VM GET and SET operations already have
the active `VmState` and therefore its current `programmer()`. Each operation
uses the existing `WorldTxn.property(object, name)` once to obtain the direct
or inherited `WorldProperty` metadata. When metadata exists, it checks the
current programmer's existing object flag 4 for Wizard status, exact property
ownership, and existing permission bit 1 for read or bit 2 for write. A failed
check raises existing `E_PERM` before `readObjectProperty` or
`writeObjectProperty`; otherwise the unchanged world operation supplies or
mutates the value. Built-in properties continue through their existing world
path because `WorldTxn.property` returns only ordinary property metadata.

This adds no class, record, interface, helper, adapter, permission service,
result wrapper, alternate property lookup, or `WorldTxn` signature change.
Wrong-type `.w`, built-in flag authorization beyond this Wizard setup,
invalid-object precedence, and inherited/clear metadata subtleties remain
separate observable surfaces outside this row.

The first clean full-suite gate exposed one existing representation defect in
this same permission surface. `BuiltinCatalog.addProperty` recognized `r` and
`c` but omitted the already-authorized ordinary `w` bit. Consequently an
existing runtime regression that creates `{#0, "rw"}` properties and later
changes task permissions began receiving correct `E_PERM` denials because the
stored metadata was accidentally read-only. The established Barn and Toast
sources above both assign ordinary write permission bit 2, so the kept change
also maps `w` to bit 2 in the existing inline parser. The exact runtime
regression then passed without changing its fixture or weakening the VM check.

The focused JUnit regression first failed during setup (`RETURNED` expected,
`ERRORED` actual). After adding `.w`, it advanced to the programmer read and
failed with `ERRORED` expected but `RETURNED` actual. After the inline VM
permission checks, the complete regression passed. The exact managed Banteng
row passed with one selected and 11,504 deselected in 3.73 seconds. The
substantial `gap_followups_toast_oracle` family then passed its first ten rows
and advanced to the next unchecked row,
`audit_intrinsic_command_table_roundtrip`, which fails independently with
`E_VERBNF` from `connection_options(...)`. The final
`gradlew clean check installDist` gate passed all 144 JUnit tests, formatting,
checks, and the application distribution in 15 seconds.

## Intrinsic command connection-option round trip

The active durable row is
`../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:772-802`,
`audit_intrinsic_command_table_roundtrip`. On one connected Wizard session it
queries `connection_options(player, "intrinsic-commands")`, sets the option to
`{"PREFIX", "SUFFIX"}`, queries it again in the same task, sets integer `1`,
and queries once more. It requires the exact three values
`{{".program", "PREFIX", "SUFFIX", "OUTPUTPREFIX", "OUTPUTSUFFIX"},
{"PREFIX", "SUFFIX"}, {".program", "PREFIX", "SUFFIX", "OUTPUTPREFIX",
"OUTPUTSUFFIX"}}`. Conformance commit
`019bc6944c05d5eef2ca4ee847a385bc1b5ea5c0` added this and the separate
unknown-name row as “Toast oracles” on May 5, 2026, but its commit contains no
live receipt; the managed pinned Toast run remains required below.

Barn's complete normative network builtin document at
`../barn/spec/builtins/network.md:224-244,265-272` is incomplete and wrong for
this exact surface. It omits `intrinsic-commands`, the accepted value forms,
canonical order/case, and reset behavior; it documents only a one-argument
`connection_options(player) -> MAP`. `../barn/spec/go-design.md:19-32` merely
assigns intrinsic command classification to package `command`. Neither text
defines the row contract.

Current Barn registers the plural reader and setter at
`../barn/builtins/registry.go:189-204`; generated signatures at
`../barn/builtins/function_signatures_generated.go:36,171` specify
`connection_options(OBJ [, STR])` and `set_connection_option(OBJ, STR, ANY)`.
The plural reader at `../barn/builtins/signatures.go:606-652` enforces self or
Wizard access and a live connection, then returns the stored bare value for a
named option. Its one-argument form returns a sorted LIST of `{name, value}`
pairs, not the spec's MAP, but that form is outside this row.

Barn's option state at `../barn/builtins/network.go:101-225` is a synchronous
process-global per-player map. `defaultIntrinsicCommands` at lines 179-187
constructs the exact five strings in row order and case. The setter at lines
1252-1317 validates the connection, permission, option, and supplied list,
stores the value immediately, and returns integer zero. A truthy non-LIST,
including this row's integer `1`, becomes the full default list; a truthy LIST
must contain only exact strings from the five-name set. The following getter
in the same MOO task therefore sees the write. Barn agrees with every asserted
row value.

Barn differs from pinned Toast outside this row. Its truthy-non-LIST branch
accepts value kinds Toast rejects, and option-name matching is case-sensitive.
Also, Barn's actual intrinsic classifier and dispatch at
`../barn/command/intrinsic.go:22-53` and
`../barn/server/input_processor.go:570-587` do not consume the stored table.
The durable row proves only state round-trip behavior; those differences must
not broaden this slice.

Pinned Toast source identity is
`aecc51e9449c6e7c95272f0f044b5ba38948459e` at
`/root/src/toaststunt`. `/root/src/toaststunt/src/server.cc:3296-3299`
registers exactly `set_connection_option(OBJ, STR, ANY)` and
`connection_options(OBJ [, STR])`; generic dispatch at
`/root/src/toaststunt/src/functions.cc:219-275` supplies `E_ARGS` and `E_TYPE`
for signature failures. The setter at
`/root/src/toaststunt/src/server.cc:2974-2995` authorizes self or Wizard,
resolves the live connection, and routes the option to server, task-queue, or
network state. The reader at lines 2998-3029 returns one bare option value when
given a name. Option names are matched case-insensitively by
`/root/src/toaststunt/src/include/server.h:296-318,345-372`.

The semantic owner is the live task queue's `icmds` mask in
`/root/src/toaststunt/src/tasks.cc`. Its enumeration at lines 114-123 and
table at lines 263-345 define exactly `.program`, `PREFIX`, `SUFFIX`,
`OUTPUTPREFIX`, and `OUTPUTSUFFIX`. `icmd_list` emits enabled mask bits in
that declaration order and canonical spelling. `icmd_set_flags` accepts an
integer, where zero disables all and any nonzero integer enables all, or a
LIST whose elements must all be valid intrinsic strings; it validates into a
new mask before replacing the old mask. Integer `1` therefore restores all
five. `TASK_CO_TABLE` at lines 1015-1070 makes setter and getter access that
same mask synchronously.

Toast initializes every new task queue to the all-command mask at
`/root/src/toaststunt/src/tasks.cc:431-475`. Its command path at lines
770-797,812-858 consults that mask before ordinary `do_command` and verb
lookup, so unlike Barn the state controls dispatch. The mask lives with the
physical connection/task queue, persists across tasks and player switches,
and is not checkpointed. The active row observes only the fresh/current mask,
immediate subset, and integer restore.

Pinned Toast accepts exact uppercase delimiter names and the documented
case-insensitive `.pr*ogram` abbreviations inside LIST values; invalid names
or non-string list elements produce `E_INVARG` atomically. Top-level values
other than INT or LIST also produce `E_INVARG`. Those rejection and dispatch
surfaces are outside this row; the adjacent
`audit_intrinsic_command_table_rejects_unknown` row separately covers one of
them and remains the next slice.

Committed Banteng `0317673` fails before any option semantics:
`src/main/java/moo/builtin/BuiltinCatalog.java` has no `connection_options`
dispatch case, so the first query returns `E_VERBNF`. Its existing
`setConnectionOption` at lines 533-562 recognizes only hold-input,
flush-command, disable-oob, and binary. Those changes are represented by a
`ConnectionOptionRequest` and applied by
`src/main/java/moo/runtime/MooRuntime.java:1208-1252` only after VM execution,
so that path cannot provide the row's same-task read-your-write behavior.

`src/main/java/moo/world/WorldTxn.java:65-116` already owns active connection
identity, network metadata, and resolution by negative connection object or
attached player. `MooRuntime.ConnectionState` owns the actual input controls
and direct `.program`, `PREFIX`, and `SUFFIX` handling. No Java design is
frozen yet: the exact managed pinned Toast row must pass first. Any design must
keep immediate per-live-connection state, use the existing concrete owners,
and add no interface, callback, adapter, or alternate connection lookup.

The exact durable row passed the managed pinned WSL Toast oracle at source
identity `aecc51e9449c6e7c95272f0f044b5ba38948459e`: one selected, 11,504
deselected, in 3.59 seconds. Post-run process inventory found only the
unrelated July 13 `/tmp/td.db` Toast process, which was left untouched. This
freezes the fresh five-name default, canonical getter order/case, immediate
valid LIST round trip, and integer-`1` restore for this slice.

The smallest Java design extends the existing concrete live-connection state
in `WorldTxn`, keyed by its physical negative connection ID. Opening a
connection initializes the exact five-name `ListValue`; closing removes it;
reads and writes resolve either that connection object or its currently
attached player through the same existing connection map. This preserves the
Toast-observed lifetime across tasks and player switches without creating a
second connection lookup owner.

`BuiltinCatalog` adds only the named two-argument `connection_options` path
required by this row. It reuses the existing self-or-Wizard and live-connection
checks, requires `"intrinsic-commands"`, and returns the existing world list.
The existing `setConnectionOption` recognizes this option before constructing
a deferred runtime request: a valid LIST is stored synchronously and a
truthy integer stores the exact full list, so later bytecode in the same VM
task reads the new value. Other connection options remain on their current
deferred-effect path.

The adjacent unknown-name row is deliberately not implemented in this slice:
row 32 supplies only valid exact string members. Invalid member rejection,
integer zero, other value kinds, one-argument option enumeration, and actual
command-dispatch filtering remain separate observable surfaces. The focused
regression must reproduce this row through parser, compiler, VM, builtin, and
one `WorldTxn`; it must first fail with current `E_VERBNF`, then prove all
three exact lists. No new interface, helper class, callback, adapter, result
variant, or `MooRuntime.ConnectionState` field is authorized.

The first focused test invocation exposed only a missing existing `MapValue`
test import and was rejected as a behavioral red. After correcting that
test-only import, the same regression compiled and failed with VM outcome
`ERRORED` instead of `RETURNED`; the absent catalog case produced the expected
first-operation `E_VERBNF`. With the concrete world state and two builtin
branches implemented, the focused same-task regression passed in 8 seconds.

The exact managed Banteng row then passed with one selected and 11,504
deselected in 3.51 seconds. Its process used temp database
`moo_conformance_ve4deee1/Test.db`; exact PID 196804 was stopped and the
managed Banteng inventory was empty afterward. The substantial
`gap_followups_toast_oracle` category passed its first 11 rows and stopped at
the separate unknown-name row, where Banteng intentionally returned zero
instead of the required `E_INVARG`. That receipt proves row 32 advanced the
kept prefix without absorbing row 33. Family temp PID 248836 for
`moo_conformance_8ipy7id8/Test.db` was stopped and inventory again proved
empty. The final `gradlew clean check installDist` gate passed all 145 JUnit
tests, formatting, checks, and application distribution in 20 seconds.

## Intrinsic command table unknown-name rejection

The next durable row is
`../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:804-813`,
`audit_intrinsic_command_table_rejects_unknown`. As connected Wizard it calls
`set_connection_option(player, "intrinsic-commands", {"PREFIX",
"NOT_A_TOAST_INTRINSIC"})` and requires `E_INVARG`; cleanup restores all
intrinsics with integer `1`. This row was added by the same conformance commit
`019bc6944c05d5eef2ca4ee847a385bc1b5ea5c0` as the preceding round-trip row.

The normative Barn spec remains silent on intrinsic-command member validation.
Current Barn validates a truthy LIST at
`../barn/builtins/network.go:1283-1303`: every element must be a STR and an
exact member of its five-name map, otherwise the builtin returns `E_INVARG`
before `setConnectionOption` at line 1305. Barn therefore agrees with this
row and leaves its prior option value unchanged.

Pinned Toast owns this decision in
`/root/src/toaststunt/src/tasks.cc:263-345`. `icmd_set_flags` accumulates into
local `newflags`; each LIST element must be a STR whose `icmd_index` is
nonzero. `"PREFIX"` resolves, `"NOT_A_TOAST_INTRINSIC"` does not, so the
function returns zero before assigning `tq->icmds`. `TASK_CO_TABLE` at lines
1015-1070 propagates that failure; `bf_set_connection_option` at
`/root/src/toaststunt/src/server.cc:2974-2995` exhausts the option owners and
returns `E_INVARG`. The mutation is atomic because `tq->icmds = newflags`
occurs only after every element validates.

Committed Banteng `4f40393` deliberately validates only that LIST elements
are strings and then stores the list, so the substantial family receipt shows
success value zero instead of `E_INVARG`. No Java design is frozen until this
exact row passes the managed pinned Toast oracle. The smallest prospective
change is an inline membership check in the existing
`BuiltinCatalog.setConnectionOption` LIST loop; no new state, helper, table,
interface, or result path is needed because `WorldTxn` already owns the
canonical full list and the current setter stores only after its loop.

The exact row passed the managed pinned WSL Toast oracle at source identity
`aecc51e9449c6e7c95272f0f044b5ba38948459e`: one selected, 11,504
deselected, in 3.53 seconds. Post-run inventory again found only unrelated
July 13 PID 19 for `/tmp/td.db`, which was left untouched. The semantic
contract is therefore frozen to `E_INVARG` before mutation for this unknown
string member.

The Java change is one inline condition in the existing LIST loop. Each
`StringValue` must decode to one of `.program`, `PREFIX`, `SUFFIX`,
`OUTPUTPREFIX`, or `OUTPUTSUFFIX`; this row's unknown value fails and returns
existing `E_INVARG`. The existing world store remains after the loop, so no
partial state can escape. Case variants, `.program` abbreviations, duplicate
members, non-string elements, and other top-level value kinds remain outside
this row. No new constant, collection, helper method, interface, state field,
or rollback path is authorized.

The focused regression first failed on the intended behavioral boundary:
the VM returned success instead of the required `E_INVARG`. After adding only
the frozen inline membership condition, the focused regression passed in 4
seconds and also proved that the canonical table remained unchanged after the
rejected call.

The exact managed Banteng row passed with one selected and 11,504 deselected
in 3.52 seconds. Its process used temp database
`moo_conformance_a03x69m4/Test.db`; exact PID 389852 was stopped and the
managed Banteng inventory was empty afterward. The substantial
`gap_followups_toast_oracle` category then passed its first 12 rows and stopped
at the separate execute-flag dispatch row: Banteng produced `I couldn't
understand that.` where the row requires `EXECUTED`. That receipt proves row
33 advanced the kept prefix without absorbing row 34. Family PID 357360 for
`moo_conformance_rr74_y_j/Test.db` was stopped and inventory again proved
empty. The final `gradlew clean check installDist` gate passed all 146 JUnit
tests, formatting, checks, and application distribution in 15 seconds.

## Parsed command dispatch and the execute flag

The next durable row is
`../moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:815-829`,
`audit_command_dispatch_ignores_execute_flag`, introduced by conformance
commit `4c4423799c4505f06f52a587568c7013e77b9c76`. As Wizard it adds a player
command verb named `auditnoexec` with permissions `d` but not `x`, installs a
body that notifies `EXECUTED`, and requires an ordinary input line containing
the verb name to produce that output.

The normative Barn command-dispatch specification disagrees. At
`../barn/spec/objects.md:250-256`, its dispatch sequence says to check execute
permission after name and argument matching. The general direct-call language
at `../barn/spec/builtins/verbs.md:337-345` also says a verb is callable only
for its owner, a Wizard, or when it has `x`; that is valid for a programmed
verb call but overbroad if read as parsed-command behavior. The dispatch
summary at `../barn/spec/server.md:172-177` does not resolve the flag. Barn's
own authority preamble in `../barn/spec/README.md:3-13,31-33,58-64,75-95`
places verified Toast and proven conformance rows above this unaudited
passage and requires correcting a contradicted specification.

Current Barn already separates the two lookup modes. Network input passes
through `../barn/server/input_processor.go:65-230`; ordinary logged-in input
reaches `processCommand` at lines 480-551, tries the listener `do_command`,
and falls through to `command.FindVerb`. The dispatch lookup in
`../barn/command/verbs.go:146-193` and candidate enumeration in
`../barn/db/store/store_verbs.go:120-147` match names, ancestry, and argument
specifications without reading verb permissions. The selected verb runs
through `../barn/scheduler/task_runtime.go:422-477` without an execute check.
By contrast, programmed calls in `../barn/vm/op_verb.go:112-125` use
`FindCallableVerb`; `../barn/db/store/store_verbs.go:168-263` then requires
`VerbExecute`. Barn's source therefore agrees with the durable row and
disagrees only with its stale normative dispatch step.

Pinned Toast source identity
`aecc51e9449c6e7c95272f0f044b5ba38948459e` owns the same separation.
`server_receive_line` enters `new_input_task` at
`/root/src/toaststunt/src/server.cc:1534-1541`; ordinary input becomes
`TASK_INBAND` in `/root/src/toaststunt/src/tasks.cc:1074-1095,1170-1180`, and
`run_ready_tasks` routes it to `do_command_task` at lines 1648-1769.
`do_command_task` uses `find_verb_on` and then `do_input_task` at lines
751-871. Its `db_find_command_verb` owner at
`/root/src/toaststunt/src/db_verbs.cc:302-343` matches name plus direct-object,
preposition, and indirect-object specifications without checking `VF_EXEC`;
`do_input_task` at `/root/src/toaststunt/src/execute.cc:3339-3372` reads only
`VF_DEBUG` before execution. In contrast, `VF_EXEC` is bit 04 at
`/root/src/toaststunt/src/include/db.h:486-491`, and callable lookup explicitly
requires it through `find_verbdef_by_name(..., 1)` at
`/root/src/toaststunt/src/db_verbs.cc:227-238,473-668`.

Committed Banteng `07eddbf` conflates those lookup modes.
`src/main/java/moo/runtime/MooRuntime.java:352-353` asks
`WorldTxn.verb(object, name)` for player and room command candidates. That
method's exact-name and inheritance scan at
`src/main/java/moo/world/WorldTxn.java:184-218` returns a match only when its
permissions contain bit 4. The row's permissions are only bit 8, so both
command candidates are absent and `MooRuntime.java:631-638` emits
`I couldn't understand that.`. Direct VM calls and `pass()` also use the same
lookup; `src/test/java/moo/vm/MooVmTest.java:987-1034` durably proves they must
skip a nearer non-executable match. A global removal of the filter would
therefore be wrong. The exact managed pinned Toast row remains required before
correcting the Barn specification or freezing the Java design.

The exact row passed the managed pinned WSL Toast oracle at the stated source
identity: one selected, 11,504 deselected, in 3.67 seconds. Post-run WSL
inventory found only unrelated July 13 PID 19 for `/tmp/td.db`, which was left
untouched. The contradicted normative dispatch step was then corrected and
committed separately in Barn as
`6c031a7 docs: correct command verb execute semantics`.

The smallest Java representation keeps `WorldTxn` as the single named-verb
lookup owner and extends that existing lookup with a `requireExecutable` mode.
Its current two-argument entry point retains executable-only behavior for VM
calls, `pass()`, listener hooks, and every existing caller. Only the normal
player and room command-candidate lookups in `MooRuntime.executeLine` request
the mode that ignores bit 4. The scan must otherwise preserve its current
name-pattern, slot-order, and inheritance behavior, so a matching non-`x`
command verb shadows later candidates exactly as Toast's command lookup does.
The `huh` fallback and all other hook paths remain unchanged in this slice.

The focused regression belongs in the existing `MooRuntimeTest` normal-command
dispatch group. It creates the row's exact player verb with `d` permissions,
installs its notify body, confirms the persisted bit value through the existing
indexed lookup, sends ordinary input, and requires only `EXECUTED`. It must
first fail with the current fallback text before the lookup mode is added. No
new interface, adapter, helper class, lookup service, state field, or verb
record is authorized.

The first focused regression setup was rejected because it attempted to
install code through the separately executable-filtered named
`set_verb_code` path; the command verb therefore did not exist and the test
failed before dispatch. The replacement setup used the existing indexed
`WorldTxn` operations. Its first invocation exposed their documented index
conventions and was corrected to convert the one-based result of `addVerb` to
the zero-based `setVerbCode` index. Only then did the focused regression
produce the valid red: expected `[EXECUTED]`, actual
`[I couldn't understand that.]`.

After implementing the frozen mode on the existing owner and changing only
the two normal command-candidate calls, the focused regression passed in 8
seconds. The first managed Banteng attempt was not a semantic gate: an
unexported launcher environment selected Java 17 and rejected Java 25 class
version 69 before the server listened; it left no managed Banteng process.
With Java 25 explicitly exported in the managed command, the exact row passed
with one selected and 11,504 deselected in 3.43 seconds. Its process used temp
database `moo_conformance_wjvs3_0e/Test.db`; exact PID 387192 was stopped and
the managed Banteng inventory was empty afterward.

The substantial `gap_followups_toast_oracle` category then passed its first
13 rows and stopped at the separate task-local fork row, where Banteng returned
`E_VERBNF` instead of the required empty map. That receipt proves row 34
advanced the kept prefix without absorbing the next target: one failed, 13
passed, and 11,488 deselected in 29.56 seconds. Family PID 102420 for
`moo_conformance_ff7ypbbf/Test.db` was stopped and inventory again proved
empty. The final `gradlew clean check installDist` gate passed all 147 JUnit
tests, formatting, checks, and application distribution in 15 seconds.
