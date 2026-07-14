# Call splicing and computed access authority

## Scope

This record freezes the exact contract needed to complete the managed
`splice::` family. The active gaps are argument splicing in builtin calls,
computed property reads and writes, computed verb calls, and the direct world
mutations required by those rows: `max`, `recycle`, `add_verb`, and
`set_verb_code`.

This slice does not authorize a runtime splice value, argument adapter,
property resolver, call-context interface, verb-dispatch service, mutable world
facade, sender, or alternate evaluator. Static and computed access must share
the existing concrete owners.

## Verified identities

- Banteng before this slice:
  `4c7f53a6cbf6d40b818c08bbe1a9d01a3ce4d373`.
- Banteng oracle procedure:
  `docs/reports/toast-oracle-identity-2026-07-14.md`.
- Toast source and executable: `/root/src/toaststunt` at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e`, executable
  `/root/src/toaststunt/build-release/moo`.
- Barn implementation reference at audit time:
  `6400748c69a2b5fe8eb1f90fbcfa28b34c0590e9`.
- Durable conformance authority after the focused additions:
  `../moo-conformance-tests` at
  `8a79771be0b1e4db240ea8065f4c84386e08005a`.
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
- The expanded family passed `24 passed, 11478 deselected` out of 11,502
  collected.

Before the additions, managed Banteng selected 17 rows and produced
`11 passed, 6 failed, 11478 deselected`. The failures were the one builtin-call
splice and all five computed access rows. All six stopped in parsing and
returned `E_INVARG`, so downstream world and verb prerequisites were not yet
executed. The expanded 24-row family is the focused red gate for the Java
slice.

## Frozen smallest Java representation

- Reuse the existing `Ast.Splice`. Permit it directly in `Ast.Call` argument
  lists; never add a runtime splice value.
- Make the existing builtin `CALL` consume one constructed `ListValue` plus
  its existing static builtin-name text operand. Remove compile-time numeric
  arity. Compile every argument list through existing `BUILD_LIST`,
  `LIST_APPEND`, and `LIST_EXTEND`, preserving left-to-right evaluation.
- Add `max` directly to the existing `BuiltinCatalog` switch.
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
- Populate callee locals directly with the existing runtime shape: `this`,
  `player`, `caller`, `verb`, `args`, and `argstr`.
- Add direct `BuiltinCatalog` cases and immutable `WorldTxn` snapshot
  mutations for `recycle`, `add_verb`, and `set_verb_code`. Reuse existing
  `WorldObject` and `WorldVerb` records; do not add mutable wrappers.

Focused Java tests must first prove all 13 missing/expanded rows red through
parser, deterministic bytecode, VM, and stored runtime seams. The kept slice
must then pass those focused tests, `gradlew check`, `installDist`, the managed
24-row `splice::` family, a read-only review, and a fresh plan reread before
commit.
