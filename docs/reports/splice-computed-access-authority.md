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

Focused Java tests must prove the missing rows through parser, deterministic
bytecode, VM, handler, and stored runtime seams. The kept slice must pass those
focused tests, `gradlew check`, `installDist`, the managed 25-row `splice::`
family, the two focused recycle rows, a read-only review, and a fresh plan
reread before commit.
