# Command parser authority

## Scope and decision

This record freezes Banteng's ordinary logged-in command slices against Barn's
normative specifications, current Barn, and the pinned stock WSL Toast source.
The first implementation target was exactly:

- `command_parser_toast_oracle::audit_tokenizer_backslash_escapes`; and
- `command_parser_toast_oracle::audit_tokenizer_midword_quotes`.

The second implementation target is exactly
`command_parser_toast_oracle::audit_preposition_scan_uses_leftmost_position`.
It authorizes the pinned position-first preposition partition and the resulting
command-string locals, but not general object matching, argspec-aware lookup,
`huh`, `.program`, `pass()`, a command service, tokenizer or parser helper,
interface, adapter, sender, stored command model, or alternate execution path.
The exact managed 18-row oracle execution and its pinned identity are recorded
below.

## Authorities

- Conformance rows:
  `../moo-conformance-tests/src/moo_conformance/_tests/audit/command_parser_toast_oracle.yaml`.
- Barn package ownership: `../barn/spec/go-design.md:13-28` assigns player
  tokenization and command-verb lookup to `command`, ingress/output to
  `server`, and execution to the existing VM/task owners.
- Barn server contract: `../barn/spec/server.md:167-177` distinguishes login
  input from logged-in command parsing, dispatch, and `notify()` output.
- Barn command contract: `../barn/spec/login.md:144-177` requires parse, verb
  lookup, foreground execution, and command locals in that causal order.
- Barn mutation and context contracts:
  `../barn/spec/objects.md:240-288` and
  `../barn/spec/builtins/verbs.md:227-334`.
- Current Barn entry path:
  `../barn/server/input_processor.go:120-155,196-230,480-550`,
  `../barn/command/command.go:114-221`,
  `../barn/command/verbs.go:97-192`, and
  `../barn/scheduler/task_runtime.go:172-188,422-464`.
- Banteng oracle identity record:
  `toast-oracle-identity-2026-07-14.md:35-49`.

## Managed oracle evidence

The exact full selector `-k command_parser_toast_oracle` was run against the
managed pinned WSL Toast at HEAD
`aecc51e9449c6e7c95272f0f044b5ba38948459e`. It passed with
`18 passed, 11486 deselected in 17.15s`.

The managed command was run from `C:\Users\Q\code\barn` with the stock WSL
Toast profile as both oracle and target:

```powershell
$wslIp = (wsl -d Debian -u root -e hostname -I).Trim()
uv run --project ..\moo-conformance-tests moo-conformance `
  --moo-host $wslIp `
  --server-command "wsl -d Debian -u root -e env TOAST_MOO=/root/src/toaststunt/build-release/moo bash /mnt/c/Users/Q/code/banteng/scripts/run_toast_wsl.sh {db} {port}" `
  --server-db C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_db/Test.db `
  --oracle-profile-manifest C:/Users/Q/code/banteng/profiles/toast/stock-wsl-testdb.json `
  --target-profile-manifest C:/Users/Q/code/banteng/profiles/toast/stock-wsl-testdb.json `
  -k command_parser_toast_oracle
```

The WSL source checkout and required source pin are both
`aecc51e9449c6e7c95272f0f044b5ba38948459e`. At that exact commit:

- `src/parse_cmd.cc:33-78` owns word tokenization;
- `src/parse_cmd.cc:109-123` exposes the complete word list;
- `src/parse_cmd.cc:127-237` parses the command and assigns `args`;
- `src/tasks.cc:812-869,1075-1094,1761-1769` preserves raw input and orders
  `do_command` before normal command-verb dispatch;
- `src/execute.cc:3340-3371` installs command locals and executes the selected
  verb; and
- `src/server.cc:2920-2956` sends `notify()` output to the connection.

## `tostr` authority and current family limit

- Barn's normative surface is `../barn/spec/builtins/types.md:45-79`.
- Barn's current zero-or-more concatenation and value conversion are
  `../barn/builtins/types.go:24-45,49+`.
- Pinned Toast converts values in `src/list.cc:377-408`, concatenates every
  supplied argument in `src/list.cc:1162-1182`, and registers `tostr` with
  zero-or-more arguments at `src/list.cc:1783`.
- Pinned Toast's exact error messages are `src/unparse.cc:35-82`, including
  `E_FILE` as `File error`.

For Banteng's current closed `MooValue` family--`STR`, `INT`, `FLOAT`, `OBJ`,
`ERR`, `LIST`, and `MAP`--current Barn and pinned Toast agree on zero-or-more
concatenation, string identity, decimal integers, literal floats, `#N`
objects, human-readable error messages, `{list}`, and `[map]`. Barn's spec
example also renders `E_TYPE` as `Type mismatch`; its table's "Error name"
label does not describe the implementation output. Barn and Toast additionally
support value variants such as booleans and anonymous objects that are not in
Banteng's current closed family, so those variants are outside this slice.

## Proven tokenizer behavior

Toast's `parse_into_words()` keeps one mutable current word and one quote-state
bit. A backslash consumes the following byte into the current word. A double
quote toggles quote state wherever it occurs and is not emitted. A space ends a
word only outside quotes. Current Barn implements the same state machine in
`command/command.go:114-150`.

Consequently:

- `audit_words foo\ bar baz` becomes
  `{"audit_words", "foo bar", "baz"}`; and
- `audit_words ab"c d"ef zz` becomes
  `{"audit_words", "abc def", "zz"}`.

The command verb receives the tail as `args`, so both rows require length two.
The complete word list, including the verb, is passed to inherited
`#0:do_command` first. A truthy result stops normal handling. An unhandled
semicolon command then follows the existing eval path; an unhandled ordinary
command then performs inherited player-verb lookup and stored-verb execution.
For a player-entered command, `caller` is the player.

## Second-slice final authority trace

Barn's normative command sequence remains `../barn/spec/login.md:144-177`:
parse `verb dobj prep iobj`, select the command verb, create its foreground
task, and install `args`, raw `argstr`, `dobj`, `dobjstr`, `iobj`, `iobjstr`,
and `prepstr`. `../barn/spec/builtins/verbs.md:136-174,317-333` defines the
`{dobj, prep, iobj}` argument specification, `none`/`any` object and
preposition meanings, and the same command locals. The prose table abbreviates
the accepted preposition family; it is not the exact persisted code table.

Current Barn supplies that exact table and algorithm in
`../barn/command/command.go:9-111,168-247`. Its accepted aliases, in persisted
code order, are:

| Code | Accepted aliases, in scan order |
| ---: | --- |
| 0 | `with`, `using` |
| 1 | `at`, `to` |
| 2 | `in front of` |
| 3 | `in`, `inside`, `into` |
| 4 | `on top of`, `on`, `onto`, `upon` |
| 5 | `out of`, `from inside`, `from` |
| 6 | `over` |
| 7 | `through` |
| 8 | `under`, `underneath`, `beneath` |
| 9 | `behind` |
| 10 | `beside` |
| 11 | `for`, `about` |
| 12 | `is` |
| 13 | `as` |
| 14 | `off`, `off of` |

Current Barn's `findPreposition()` loops argument positions outermost, then
codes, then aliases. It returns immediately on the first match. Its
`ParseCommand()` joins tokenized words before and after that match into
`dobjstr` and `iobjstr`, while leaving `Args` as the complete argument-word
tail and `Argstr` as the raw substring after the command word. The concrete
player path resolves nonempty object strings and installs all six command
locals through `../barn/command/player.go:8-25` and
`../barn/scheduler/task_runtime.go:151-185,441-451`.

The pinned WSL Toast checkout was re-read at exact HEAD
`aecc51e9449c6e7c95272f0f044b5ba38948459e`. Its authoritative source is
`src/db_verbs.cc:42-65,77-147` and `src/parse_cmd.cc:160-237`:

- `prep_list` is the same 15-entry persisted table above;
- `db_find_prep()` loops token position `i` before table code `j` and alias,
  compares case-insensitively, and returns the first match;
- `parse_command()` passes only the argument words to that scan, preserves the
  already-built `args` list and raw `argstr`, and builds `dobjstr`, `prepstr`,
  and `iobjstr` around the returned span; and
- nonempty object strings are passed to `match_object`, while empty object
  slots are `NOTHING`.

Therefore the active input tokenizes as
`{auditprep, book, out, of, bag, in, front, of, chair}`. Position 1 in the
argument tail matches code 5 alias `out of` and wins immediately; the later
code 2 alias `in front of` cannot replace it. The exact partition is
`dobjstr = "book"`, `prepstr = "out of"`, and
`iobjstr = "bag in front of chair"`.

There is no Barn/Toast disagreement on this row. Banteng's existing
`BuiltinCatalog.addVerb()` also accepts the same table and stores
`{"any", "any", "any"}` as direct spec 1, preposition `-2`, and indirect
spec 1. With verb permissions `xd`, the stored permission integer is
`12 | (1 << 4) | (1 << 6) = 92`. `WorldTxn.verb()` already finds the one local
executable `auditprep`, so this row does not prove overloaded argspec-aware
lookup or defining-object retention.

Before the second slice, Banteng had accepted rows one through three and failed
first at `audit_preposition_scan_uses_leftmost_position` because its command
frame omitted `dobjstr`, `prepstr`, and `iobjstr`. After the focused row passed,
the managed family receipt reached five passing rows and failed first at row
six, `audit_name_alias_exact_match_is_ambiguous`. This second slice therefore
proves position-first partitioning and the required command locals. Row five
also passes because negative object literals retain `FAILED_MATCH` (`#-3`), but
that result does not authorize or prove a general object matcher.

## Third-slice final authority trace

The third slice is exactly rows five through eight: retain the accepted
negative-object-literal result, then implement exact name/alias ambiguity,
partial name/alias ambiguity, and matching the current player through the
literal Toast candidate scope. It has two causal prerequisites: the setup's
Wizard `STR` writes to built-in `name`, then direct-object matching.

### Wizard `STR` built-in name mutation

At pinned Toast HEAD `aecc51e9449c6e7c95272f0f044b5ba38948459e`,
`src/include/db.h:374-401` declares `name` as a built-in property.
`src/execute.cc:2001-2019,2067-2078` requires a `STR` right-hand side and
permits a Wizard to write the built-in name, including a player object's name.
`src/db_properties.cc:637-654` performs that write through the existing object
name field. The conformance setup runs as Wizard and supplies only `STR`
values. This trace therefore authorizes exactly a case-insensitive built-in
`name` branch in Banteng's existing `WorldTxn.writeObjectProperty()`, changing
only `WorldObject.name`. It does not prove a new API, non-Wizard authorization,
or any other built-in property mutation.

Current Barn agrees: `../barn/vm/op_property.go:362-364` routes `name` writes
to the existing `Store.SetObjectName`, and `../barn/db/store/store_core.go:146+`
owns the concrete object mutation. Banteng already reads `name` from its
`WorldObject` record but currently falls through to ordinary-property lookup
when writing it, so row-six setup raises `E_PROPNF` before matching can occur.

### Aliases, candidate scope, pooling, and sentinels

Pinned Toast `src/match.cc:30-42` reads inherited property `aliases`; only a
list participates, and non-string list elements are ignored. Lines 50-81 pool
the primary object name and every string alias together. A matching object ID
is counted once even if more than one of its strings matches. Exact matches and
raw-prefix partial matches are accumulated across all candidates; two distinct
exact IDs immediately yield `AMBIGUOUS`, otherwise any exact ID wins over the
partial pool, and the partial pool yields its one ID, `AMBIGUOUS`, or
`FAILED_MATCH`.

The literal candidate enumeration is fixed by `src/match.cc:84-113`:

1. enumerate `contents(player)`;
2. enumerate `contents(player.location)`;
3. do not add the player independently.

The player in row eight matches because the logged-in player is already in the
room's contents. Candidate IDs must still be deduplicated so the same object is
never counted twice if topology exposes it more than once. `src/match.cc:115-134`
also fixes the sentinels: empty text is `NOTHING` (`#-1`); a complete valid
nonnegative `#N` names that object; malformed, negative, or missing object
literals are `FAILED_MATCH` (`#-3`); `me` is the player; and `here` is the
player's location.

Current Barn `../barn/command/matcher.go:11-132` has the same inventory-then-room
scope, special forms, exact-before-prefix phases, combined primary-name/alias
pools, and per-object deduplication. The active strings are ASCII, so the
proven comparison boundary is ASCII case-insensitivity; this slice does not
claim broader Unicode folding semantics.

Banteng's existing owners already contain the required state and mutations:
`WorldObject` has `name`, `location`, and ordered `contents`;
`WorldTxn.readObjectProperty()` exposes inherited `aliases` as the stored
`ListValue`; `create`, `add_property`, and `move` already populate the row-six
and row-seven inventory. After a fresh fixture login, the focused runtime has
player `#8` at room `#2`, room contents `{#3, #4, #8}`, and the two created
objects become `#9` and `#10` in `contents(#8)`. These numbers are fixture
evidence only; production uses the current player and topology dynamically.

After the object-matching slice, the managed family receipt reached nine
passing rows and stopped first at row ten,
`audit_huh_runs_after_argspec_mismatch`. Rows six through eight now prove the
direct-object resolution described here, and row nine independently confirms
the existing `do_command`-before-semicolon ordering. This slice leaves
indirect-object matching, argspec-aware verb lookup, `huh`, and broader command
dispatch untouched. Row eight's cleanup also calls the currently absent
`delete_property`; cleanup is best-effort and that adjacent builtin is not
authorized by these assertions.

## Fourth-slice final authority trace

The fourth slice is exactly row ten,
`audit_huh_runs_after_argspec_mismatch`. Its setup installs executable
`none/none/none` verbs named `auditmismatch` on the player and `huh` on the
player's room. The raw command is `auditmismatch object`. The pre-slice managed
receipt was nine preceding rows passing, followed by this first failure: Banteng
runs the player verb and emits `BAD_MATCH` instead of rejecting its argument
specification and emitting `HUH:auditmismatch` then `ARGSTR:object` from the
room's `huh` program. The setup and both stored programs therefore complete;
the failure is causally in ordinary-command selection, not mutation or code
installation. After the fourth slice, the managed family receipt reached ten
passing rows and stopped first at row eleven,
`audit_say_shortcut_reparses_preposition`.

The exact YAML setup first evaluates `room = player.location`. Pinned Toast
represents `location` as a built-in object property and returns the object's
current location object number. Banteng's immutable `WorldObject` already owns
that signed location, but `WorldTxn.readObjectProperty()` does not yet expose
it. This setup authorizes exactly one case-insensitive branch in that existing
method returning `new ObjectValue(object.location())` for a valid receiver. It
does not authorize a helper, API change, write support, permission expansion,
or any other built-in property.

Pinned Toast `src/include/db.h:487-500` assigns argument-specification values
`none=0`, `any=1`, and `this=2`. `src/db_verbs.cc:303-342` reads the direct
specification from packed permission bits 4-5 and the indirect specification
from bits 6-7. A stored `any` accepts every parsed classification; otherwise
the stored and parsed classifications must be equal. The preposition is stored
separately and likewise accepts either `PREP_ANY` or exact equality.

Pinned Toast `src/tasks.cc:752-766` classifies each object separately for each
candidate receiver: equal to that receiver is `this`, `#-1` is `none`, and
every other value is `any`. Thus the player verb's stored direct `none` rejects
the parsed failed match `#-3` as class `any`; its stored preposition `none`
matches the absent preposition, and its stored indirect `none` matches `#-1`.
There is no coercion from failed match to nothing.

After command-verb failure, pinned Toast `src/tasks.cc:844-856` uses the
callable `huh` on the location when `player_huh` is false, without applying the
`huh` verb's own argument specification. The stock fixture represents
`#0.server_options=#6`, but object `#6` has no `player_huh` property, and the
pinned build leaves the compile-time option disabled. This row therefore
authorizes only the room fallback, not a server-option implementation.

Pinned Toast `src/execute.cc:3340-3369` executes the selected program with
`this` equal to its receiver, `player` and `caller` equal to the initiating
player, and every parsed command local preserved. In particular, `verb` remains
the original parsed command word `auditmismatch`; it is never rewritten to
`huh`. Current Barn agrees in `../barn/command/verbs.go:55-93,97-125,195-208`,
`../barn/server/input_processor.go:528-550`, and
`../barn/scheduler/task_runtime.go:422-451`.

Banteng's existing `WorldVerb` already retains packed permissions and the
separate preposition. Existing `WorldTxn.verb()` can select the one executable
player name candidate and later select callable `huh` from the room; the latter
lookup intentionally ignores `huh` argspec. The row does not observe overloaded
same-name command candidates or defining-object retention, so neither a new
lookup API nor a stored command model is authorized. The implementation remains
inline in the existing ordinary-command branch and reuses its parsed locals.

## Fifth-slice final authority trace

The fifth slice is exactly rows eleven through thirteen: leading quote rewrites
to `say`, leading colon rewrites to `emote`, and leading semicolon rewrites to
`eval`. The pre-slice managed receipt is ten passing rows and the first failure
is row eleven, `audit_say_shortcut_reparses_preposition`. Banteng currently
tokenizes the unmatched opening quote as one word, finds no player verb by that
word, and emits none of the expected `say` notifications. After the fifth
slice, the managed family receipt reached fourteen passing rows and stopped
first at row fifteen, `audit_dot_program_intrinsic_installs_verb_code`. Row
fourteen therefore also confirms that `do_command` still receives the original
quoted/backslash word list.

Pinned Toast `src/parse_cmd.cc:126-193` first skips leading spaces, replaces the
first marker with the corresponding verb plus one space and the untouched tail,
then tokenizes and performs the normal preposition partition. This is a dispatch
rewrite, not a separate evaluator: `say widget in auditbox`, `emote wave at me`,
and `eval widget in auditevalbox` produce the same ordinary command locals as if
those expanded texts had been entered directly.

The `do_command` input is intentionally different. Pinned Toast
`src/tasks.cc:820-856` parses the rewritten command for later dispatch, but
calls `parse_into_wordlist(command)` on the untouched original line before any
command-verb lookup. Therefore Banteng must retain its current original-line
tokenization and raw `argstr` for `do_command`, return immediately when that
verb is truthy, and derive a second dispatch text only after false or absent
`do_command`.

Current Barn preserves original words separately from rewritten dispatch in
`../barn/command/command.go:153-197`, but disagrees with Toast on the exact
shortcut word list: `CommandWordList()` splits the leading marker into its own
element before tokenizing the tail. `../barn/server/input_processor.go:516-520`
passes that Barn list to `do_command`. The managed family is pinned to Toast,
so Banteng must keep Toast's untouched raw tokenizer result rather than adopt
Barn's marker-separated list.

All three stored player verbs have executable/debug permissions `xd`, direct
`any`, indirect `any`, and one exact preposition. Their packed permissions are
`12 | (1 << 4) | (1 << 6) = 92`; `say` and player `eval` store preposition code
3 (`in`), while `emote` stores code 1 (`at`). The exact command frames are:

- quote: `verb="say"`, `args={"widget", "in", "auditbox"}`,
  `argstr="widget in auditbox"`, `dobjstr="widget"`, `prepstr="in"`, and
  `iobjstr="auditbox"`;
- colon: `verb="emote"`, `args={"wave", "at", "me"}`,
  `argstr="wave at me"`, `dobjstr="wave"`, `prepstr="at"`,
  `iobjstr="me"`, and `iobj=player`; and
- semicolon: `verb="eval"`, `args={"widget", "in", "auditevalbox"}`,
  `argstr="widget in auditevalbox"`, `dobjstr="widget"`, `prepstr="in"`, and
  `iobjstr="auditevalbox"`.

Pinned Toast `src/parse_cmd.cc:216-233` applies the same `match_object()` to
both object strings. The created `auditbox` and `auditevalbox` are in the
player's contents, while `me` is the player sentinel. Banteng must therefore
apply its already-proven inventory-then-room, exact-before-prefix, name/alias
matcher to the indirect string as well as the direct string, inline in the same
runtime owner; no matcher helper or new command model is authorized.

Normal command selection remains player, then location, then later targets.
These rows require the first two receivers. The fixture room `#2:eval` has
packed permissions 88 (`d`, `any/any`) and preposition `-2` (`any`). Its absent
executable bit means `WorldTxn.verb(room, "eval")` does not expose it, although
pinned Toast command lookup does not require that bit. The existing indexed
`WorldTxn.verb(room, 0)` exposes the exact fixture verb; this slice authorizes
using that existing path only after confirming its stored name is exactly
`eval`. Eval setup arrives as `; <code>` before the player audit verb exists,
so it must use the room verb. After setup, the raw row-thirteen command matches
the player's preposition-`in` verb. Cleanup also arrives as `; <code>`; the
player verb then fails its preposition and lookup must continue to the room eval
rather than jump directly to `huh`. Each selected frame keeps `player` and
`caller` equal to the initiating player, `this` equal to the selected receiver,
and `verb` equal to the rewritten command name.

## Sixth-slice final authority trace

The sixth slice is exactly row fifteen,
`audit_dot_program_intrinsic_installs_verb_code`. The current managed receipt
is fourteen preceding rows passing and this row failing first. The failure is
causal: Banteng has no connection-local programming state, so `.program`,
`return 4242;`, and `.` each enter the ordinary `do_command` and command
dispatch path. The setup succeeds and leaves one empty local `auditprog` verb,
but none of those three raw inputs changes its source; the final direct call
therefore returns `0` instead of `4242`.

The exact YAML setup creates one object with parent `$nothing`, names it
`audit program target`, and adds local verb `auditprog` with permissions `rxd`
and argument specification `this none none`. Banteng's existing
`BuiltinCatalog.addVerb()` stores that as permissions
`13 | (2 << 4) = 45`, preposition `-1`, and empty source. The row then sends
the three `command:` values through raw-command transport, in order:
`.program #N:auditprog`, `return 4242;`, and exact terminator `.`. Only after
those inputs does eval call `#N:auditprog()` and require integer `4242`.

Pinned Toast keeps `program_stream`, `program_verb`, and `program_object` on
the connection task queue in `src/tasks.cc`. Its active-program branch owns
the raw input before command parsing: exact `.` ends the stream, while every
other input appends the untouched line followed by `\n`. Termination resolves
the captured verb, parses the complete stream, and installs code only after a
successful parse. This fixes both ordering and representation: programming
input must not reach `PREFIX`, `SUFFIX`, tokenization, `do_command`, shortcut
rewriting, or ordinary dispatch, and the stored one-line source is exactly
`"return 4242;\n"`.

Current Barn agrees on connection-local object, verb, and ordered raw-line
state and on compile-before-install. It disagrees only at the terminator edge:
its current programming input trims whitespace before comparing with `.`,
whereas pinned Toast uses exact equality. The managed family is pinned to
Toast, so Banteng must use exact `line.equals(".")` and must not reinterpret a
whitespace-padded source line as the terminator.

Banteng's existing owners are sufficient. `WorldObject.verbs()` exposes the
ordered local slots; `WorldTxn.verb(object, name)` supplies existing executable
name matching; requiring that result in the target object's own verb list
rejects inheritance; `WorldTxn.verb(object, index)` retains the captured local
slot; the existing `MooParser` and `MooCompiler` validate source; and
`WorldTxn.setVerbCode(object, index, source)` performs the immutable source
replacement. The only missing state belongs directly in the existing
`MooRuntime.ConnectionState`. No command model, programming helper, resolver
API, service, adapter, sender, facade, or alternate compiler is authorized.

The proof boundary is the exact active row: one authenticated Wizard, one
valid nonnegative `#N:verb` descriptor naming a local executable verb, one raw
source line, exact-dot termination, successful compilation with a preserved
trailing LF, installation into the captured local slot, and the later direct
call returning `4242`. The row does not prove `.program` abbreviation,
inherited targets, numeric verb descriptors, permission-denial behavior,
compiler diagnostics, status notifications, cancellation, or connection
routing. In particular, the YAML asserts no output for the three raw command
steps, so this slice must not add output-message behavior.

The focused managed row passes. The subsequent managed family receipt reached
fifteen passing rows and stopped first at row sixteen,
`audit_inherited_command_caller_is_player`.

## Seventh-slice final authority trace

The remaining family is rows sixteen through eighteen. This seventh source
slice is exactly rows sixteen and seventeen: one inherited player command and
the same caller rule through one intervening ancestor. Row eighteen is traced
here because it shares the hierarchy setup, but its native `pass()` execution
is a later source slice. The current managed receipt remains fifteen passing
rows and row sixteen first failing.

All three rows first evaluate `parent(player)` as Wizard. Banteng's compiler
already emits an ordinary builtin call, but `BuiltinCatalog` has no `parent`
case, so the current row-sixteen setup raises `E_VERBNF` before creating an
ancestor. After that missing read, each setup uses existing `create(parent)`,
which already writes the child's parent and appends the child to the parent's
ordered `children`. The next missing operation is `chparent(player, ancestor)`;
Banteng has neither the builtin case nor one concrete reciprocal reparent
mutation in `WorldTxn`.

Pinned Toast defines `parent(object)` as a one-object hierarchy read and
`chparent(object, new_parent)` as the hierarchy mutation. The active setup
uses valid, nonnegative, Wizard-owned fresh objects only. Current Barn agrees
in `builtins/objects_hierarchy.go`: invalid arity is `E_ARGS`, non-object
arguments are `E_TYPE`, an invalid target is `E_INVIND`, an invalid new parent
is `E_INVARG`, and self-parenting or a descendant parent is `E_RECMOVE`.
Barn's `db/store/store_relationships.go:271+` owns the actual reciprocal
mutation: remove the target from each old parent's children, replace its
parent, and attach it once to the new parent's children. Banteng has single
inheritance, so this slice needs exactly that one-parent transaction. It does
not authorize writable `.parent`, multiple inheritance, inherited-property
reseed behavior, or a hierarchy helper/service/interface.

After setup, Banteng already agrees with Toast and Barn on the two active
command frames. `WorldTxn.verb(player, name)` walks the complete parent chain
and returns the first inherited executable `WorldVerb`. `MooRuntime` retains
the command receiver as `this = player` and constructs both `player` and
`caller` from the initiating player before `executeStored()` uses the selected
verb owner as task programmer. Therefore the row-sixteen ancestor and the
row-seventeen defining ancestor above an empty middle object both observe
`caller == player`. The defining object is not needed for either assertion;
no command lookup, command frame, `WorldVerb`, compiler, or VM change is
authorized in this slice.

Row eighteen builds `old_parent -> pass_target -> pass_gap -> command_definer`
and reparents the player below `command_definer`. The same existing command
path correctly runs the first local `audit_pass_caller` with player caller.
Its source then executes `pass(@args)`. Banteng's parser and compiler already
accept that syntax as `LIST_EXTEND` followed by ordinary `CALL`, but the call
reaches the absent `BuiltinCatalog` entry and raises `E_VERBNF`; no parent verb
frame runs. Current Barn uses native VM `pass` execution, starts lookup above
the current defining object, skips `pass_gap`, preserves `this`, `player`,
`verb`, and command locals, derives the target `caller` from the current
activation's `this` (the player here), changes programmer to the target verb
owner, and pushes the target frame. That later row is the first one that needs
the current verb's defining object. It remains outside this source slice.

The rows-sixteen-and-seventeen proof boundary is one authenticated Wizard,
the exact valid fresh-object chains, reciprocal parent/children mutation,
one local `xd`/`any any any` verb with packed permissions `92` and preposition
`-2`, inherited executable lookup through one and two ancestry edges, command
receiver `this = player`, and notification `CALLER_IS_PLAYER:1`. It does not
prove non-Wizard `chparent`, fertile objects, `$nothing` as a new parent,
property conflict handling, recycled-object distinctions, multiple parents,
defining-object retention, chained pass, or any `pass()` behavior. Cleanup
must first restore the player's original parent and then recycle the fresh
objects in the exact leaf-to-root YAML order.

The two focused managed rows pass. The subsequent managed family receipt
reached seventeen passing rows and stopped only at row eighteen,
`audit_inherited_command_pass_preserves_player_caller`.

## Eighth-slice final authority trace

The eighth slice is exactly row eighteen and retains the seventh-slice causal
trace above. The current managed receipt is seventeen passing rows with this
row first failing: setup and the command-definer notification complete, then
ordinary builtin dispatch raises `E_VERBNF` for `pass`, so the pass-target
notification is absent.

The adopted representation remains entirely inside the existing VM `CALL`
branch. The compiler has already built the explicit argument `ListValue` from
`@args`. For call name `pass`, the frame's existing `this` and `verb` locals
re-resolve the currently selected inherited `WorldVerb`; walking `this` and
its ancestry until one local verb list contains that record identifies the
current definer without changing `WorldVerb` or adding a resolver result. The
next lookup starts at that definer's parent, so existing inherited executable
lookup skips the empty `pass_gap` and selects the local pass-target verb.

The target frame uses the existing parser, compiler, and
`VmState.pushVerbFrame()`. It copies the current command locals, replaces only
`args` with the explicit passed list, retains `this`, `player`, `verb`,
`argstr`, `dobj`, `iobj`, and their strings, sets `caller` from the current
activation's `this`, and uses the target verb owner as programmer. The exact
proof boundary is one pass from the first inherited command definer to one
executable same-name ancestor across one empty gap. It does not authorize a
new opcode, builtin-catalog entry, frame field, defining-object model, general
resolver API, chained pass, implicit no-argument inheritance, alternate error
distinctions, or any command lookup change.

The focused row regression, the complete `MooRuntimeTest` class, and the full
Java 25 `check installDist` gate pass. The exact managed row receipt is one
passing row with 11,503 deselected. The subsequent complete command-parser
family receipt is eighteen passing rows with 11,486 deselected. These managed
receipts supersede the older seventeen-pass receipt and accept the eighth
slice.

## Harness ordering and cleanup

`runner.py:404-418` sends a `command:` step through raw-command transport.
`transport.py:393-413` sends the supplied text without an eval prefix and
captures the verb's notification lines. `runner.py:452-463` runs cleanup in a
`finally` block.

For rows one and two the durable sequence is therefore:

1. eval setup calls `add_verb(player, ...)` and `set_verb_code(...)`;
2. the raw command is sent unchanged;
3. `do_command` sees the complete tokenized word list;
4. if unhandled, the inherited player verb executes with the tail as `args`;
5. its three `notify()` calls produce ordered output; and
6. eval cleanup calls `delete_verb(player, "audit_words")`, even after a row
   failure.

## Current Banteng agreements

- `MooServer.java:76-90` already passes the socket line unchanged to the one
  concrete `MooRuntime` owner.
- `BuiltinCatalog.java` and `WorldTxn.java:405-477` already implement direct
  `add_verb`, `set_verb_code`, and `delete_verb` mutation through `WorldTxn`.
- `BuiltinCatalog.java:580-587`, `MooVm.java:337-345`, and
  `VmState.java:93-95,179-181` already stage ordered `notify()` output.
- `MooServer.java:103-109` writes those ordered lines with CRLF framing.
- `WorldTxn.java:95-130` already performs inherited executable named-verb
  lookup.
- `MooRuntime.java:73-103,135-169` now performs the proven escaped/quoted word
  tokenization, preserves raw `argstr`, and dispatches the stored player verb.
- `BuiltinCatalog.java:298-419` already packs the complete accepted
  preposition table and `any` as `-2` into the existing `WorldVerb` record.

## Current Banteng absences and disagreements

- Banteng has no overloaded argspec-aware command lookup, command programming
  mode, or general missing-command `huh` dispatch. Those remain later rows.
- `WorldTxn.verb()` returns the inherited verb but not its defining object and
  does not itself inspect packed argspec bits. Row ten directly inspects its one
  returned player candidate in `MooRuntime`; overloaded candidates and defining
  object retention remain unobserved.
- Row ten proves only room `huh` after a matching player verb name fails its
  argspec. It does not authorize `player_huh`, `huh` after a missing verb name,
  or the default English failure message.
- `notify()` currently stages its string without routing by its object
  argument. The first two rows notify the initiating player, so broader
  connection routing is not authorized here.

## All command-parser oracle rows

| Row | Oracle behavior | Status after this freeze |
| --- | --- | --- |
| `audit_tokenizer_backslash_escapes` | Backslash protects the following character inside a word. | Accepted first slice. |
| `audit_tokenizer_midword_quotes` | Quotes toggle midword and quoted spaces remain in that word. | Accepted first slice. |
| `audit_argstr_preserves_internal_spacing` | `argstr` keeps raw internal spacing after verb separation. | Accepted by current raw-line preservation. |
| `audit_preposition_scan_uses_leftmost_position` | Earliest preposition position wins. | Accepted second slice. |
| `audit_negative_object_literals_are_failed_match` | Negative object literals resolve to failed match. | Accepted by current failed-match default; no general object matcher proven. |
| `audit_name_alias_exact_match_is_ambiguous` | Exact name and alias candidates share one ambiguity pool. | Accepted third slice. |
| `audit_name_alias_partial_match_is_ambiguous` | Partial name and alias candidates share one ambiguity pool. | Accepted third slice. |
| `audit_player_name_matches_room_contents` | The player participates in room-content matching. | Accepted third slice through literal room contents. |
| `audit_do_command_runs_before_semicolon_eval` | `do_command` runs before eval dispatch. | Accepted by existing first-slice ordering. |
| `audit_huh_runs_after_argspec_mismatch` | Argspec mismatch falls through to `huh`. | Accepted fourth slice for one selected player name candidate and room `huh`. |
| `audit_say_shortcut_reparses_preposition` | Leading quote rewrites to `say` then fully reparses. | Accepted fifth slice. |
| `audit_emote_shortcut_reparses_preposition` | Leading colon rewrites to `emote` then fully reparses. | Accepted fifth slice. |
| `audit_eval_shortcut_reparses_preposition` | Leading semicolon rewrites to `eval` then fully reparses. | Accepted fifth slice through normal player-then-room dispatch. |
| `audit_do_command_receives_quoted_backslash_wordlist` | `do_command` receives the same escaped/quoted complete word list. | Accepted by preserved original-input `do_command` path. |
| `audit_dot_program_intrinsic_installs_verb_code` | `.program` captures source and installs verb code. | Accepted sixth slice by managed proof. |
| `audit_inherited_command_caller_is_player` | Inherited command sees player as caller. | Accepted seventh slice by managed proof. |
| `audit_deep_inherited_command_caller_is_player` | Deep inherited command still sees player as caller. | Accepted seventh slice by managed proof. |
| `audit_inherited_command_pass_preserves_player_caller` | Command and `pass()` target retain player caller. | Accepted eighth slice by focused, full Java, exact managed-row, and complete managed-family proof. |

## Frozen direct representation

No new stored command model is required. Keep the raw `String` and transient
ordered `List<String>` in the existing concrete runtime path. Element zero is
the command verb; the remaining elements stay the existing `ListValue args`.
Preserve the raw substring after the command word as `argstr`, removing only
the leading separation. For row four, partition that same token tail directly
inside the existing ordinary-command branch and add the resulting strings to
the mutable map returned by `verbLocals()`. For row ten, classify the parsed
objects against the player receiver directly from the selected `WorldVerb`'s
packed fields. On mismatch only, replace the selected program with room `huh`
and change `this` to the room while retaining the original command word and all
other parsed locals. For rows eleven through thirteen, preserve that entire
original path through `do_command`; only afterward rewrite and retokenize the
dispatch text in place. Apply the existing object matcher to both object strings
and inspect player then room candidate argspecs before using room `huh`.

Use the existing concrete `verbLocals`, `executeStored`, `WorldTxn.verb`,
`BuiltinCatalog`, `VmState` staged output, and `MooServer` writer. Do not add a
parser interface, command helper, dispatch service, adapter, sender, facade, or
alternate evaluator.

## Open questions deliberately outside this slice

- How will inherited command lookup retain both receiver and defining object
  once a row observes that distinction?
- How will later rows continue through overloaded same-name verbs after one
  candidate's argument specification fails?
- How will `notify()` select among multiple live player connections when a
  later server row observes connection routing?

None of these questions can change the first two tokenizer decisions.
