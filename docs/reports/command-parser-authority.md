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
  --server-command "wsl -d Debian -u root -e env TOAST_MOO=/root/src/toaststunt/build-release/moo bash /mnt/c/Users/Q/code/barn/scripts/run_toast_wsl.sh {db} {port}" `
  --server-db C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_db/Test.db `
  --oracle-profile-manifest C:/Users/Q/code/barn/profiles/toast/stock-wsl-testdb.json `
  --target-profile-manifest C:/Users/Q/code/barn/profiles/toast/stock-wsl-testdb.json `
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

- `MooRuntime` still has no general object matcher; row-specific nonempty object
  strings currently receive `FAILED_MATCH` after the proven preposition scan.
- `WorldTxn.writeObjectProperty()` reads built-in `name` but cannot yet perform
  the proven Wizard `STR` name write used by rows six through eight.
- Banteng has no general object matcher, argspec-aware command lookup, `huh`
  fallback, or command programming mode. Those remain later rows.
- `WorldTxn.verb()` returns the inherited verb but not its defining object and
  does not inspect packed argspec bits. Neither distinction is observed by the
  first two rows because the audit verb is installed directly on the player
  with `any/none/none`.
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
| `audit_huh_runs_after_argspec_mismatch` | Argspec mismatch falls through to `huh`. | Deferred; no argspec matching or `huh` authorized. |
| `audit_say_shortcut_reparses_preposition` | Leading quote rewrites to `say` then fully reparses. | Deferred. |
| `audit_emote_shortcut_reparses_preposition` | Leading colon rewrites to `emote` then fully reparses. | Deferred. |
| `audit_eval_shortcut_reparses_preposition` | Leading semicolon rewrites to `eval` then fully reparses. | Deferred beyond the existing eval fallback. |
| `audit_do_command_receives_quoted_backslash_wordlist` | `do_command` receives the same escaped/quoted complete word list. | Mechanism frozen; row remains a later gate. |
| `audit_dot_program_intrinsic_installs_verb_code` | `.program` captures source and installs verb code. | Deferred. |
| `audit_inherited_command_caller_is_player` | Inherited command sees player as caller. | Caller rule frozen; row remains deferred. |
| `audit_deep_inherited_command_caller_is_player` | Deep inherited command still sees player as caller. | Deferred. |
| `audit_inherited_command_pass_preserves_player_caller` | Command and `pass()` target retain player caller. | Deferred; `pass()` is not authorized. |

## Frozen direct representation

No new stored command model is required. Keep the raw `String` and transient
ordered `List<String>` in the existing concrete runtime path. Element zero is
the command verb; the remaining elements stay the existing `ListValue args`.
Preserve the raw substring after the command word as `argstr`, removing only
the leading separation. For row four, partition that same token tail directly
inside the existing ordinary-command branch and add the resulting strings to
the mutable map returned by `verbLocals()`.

Use the existing concrete `verbLocals`, `executeStored`, `WorldTxn.verb`,
`BuiltinCatalog`, `VmState` staged output, and `MooServer` writer. Do not add a
parser interface, command helper, dispatch service, adapter, sender, facade, or
alternate evaluator.

## Open questions deliberately outside this slice

- How will inherited command lookup retain both receiver and defining object
  once a row observes that distinction?
- Which existing owner will enforce packed argspec bits and construct `huh`
  context when those rows become active?
- How will `notify()` select among multiple live player connections when a
  later server row observes connection routing?

None of these questions can change the first two tokenizer decisions.
