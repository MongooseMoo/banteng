# Command parser authority

## Scope and decision

This record freezes Banteng's first ordinary logged-in command slice against
Barn's normative specifications, current Barn, and the pinned stock WSL Toast
source. The first implementation target is exactly:

- `command_parser_toast_oracle::audit_tokenizer_backslash_escapes`; and
- `command_parser_toast_oracle::audit_tokenizer_midword_quotes`.

The slice does not authorize object matching, preposition parsing, argspec
matching, `huh`, `.program`, `pass()`, a command service, tokenizer helper,
interface, adapter, sender, or alternate execution path. The exact managed
18-row oracle execution and its pinned identity are recorded below.

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

## Current Banteng absences and disagreements

- `MooRuntime.java:55-79` handles login, `PREFIX`, `SUFFIX`, and semicolon eval,
  but drops every ordinary logged-in command. The first current failure occurs
  before tokenization; it is not evidence of a Banteng backslash-state-machine
  disagreement.
- `BuiltinCatalog` has no `tostr` dispatch. The installed audit verb therefore
  cannot execute its required `tostr(length(args))` expression until the
  existing concrete builtin switch handles Toast/Barn zero-or-more
  concatenation directly.
- Banteng has no full command object, preposition scan, object matcher,
  argspec-aware command lookup, `huh` fallback, or command programming mode.
  Those are later rows, not prerequisites for the first two.
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
| `audit_tokenizer_backslash_escapes` | Backslash protects the following character inside a word. | Active first target. |
| `audit_tokenizer_midword_quotes` | Quotes toggle midword and quoted spaces remain in that word. | Active second target. |
| `audit_argstr_preserves_internal_spacing` | `argstr` keeps raw internal spacing after verb separation. | Raw-line preservation is frozen; row remains deferred. |
| `audit_preposition_scan_uses_leftmost_position` | Earliest preposition position wins. | Deferred; no preposition scan authorized. |
| `audit_negative_object_literals_are_failed_match` | Negative object literals resolve to failed match. | Deferred; no object matcher authorized. |
| `audit_name_alias_exact_match_is_ambiguous` | Exact name and alias candidates share one ambiguity pool. | Deferred. |
| `audit_name_alias_partial_match_is_ambiguous` | Partial name and alias candidates share one ambiguity pool. | Deferred. |
| `audit_player_name_matches_room_contents` | The player participates in room-content matching. | Deferred. |
| `audit_do_command_runs_before_semicolon_eval` | `do_command` runs before eval dispatch. | Ordering is frozen now; this row remains a later gate. |
| `audit_huh_runs_after_argspec_mismatch` | Argspec mismatch falls through to `huh`. | Deferred; no argspec matching or `huh` authorized. |
| `audit_say_shortcut_reparses_preposition` | Leading quote rewrites to `say` then fully reparses. | Deferred. |
| `audit_emote_shortcut_reparses_preposition` | Leading colon rewrites to `emote` then fully reparses. | Deferred. |
| `audit_eval_shortcut_reparses_preposition` | Leading semicolon rewrites to `eval` then fully reparses. | Deferred beyond the existing eval fallback. |
| `audit_do_command_receives_quoted_backslash_wordlist` | `do_command` receives the same escaped/quoted complete word list. | Mechanism frozen; row remains a later gate. |
| `audit_dot_program_intrinsic_installs_verb_code` | `.program` captures source and installs verb code. | Deferred. |
| `audit_inherited_command_caller_is_player` | Inherited command sees player as caller. | Caller rule frozen; row remains deferred. |
| `audit_deep_inherited_command_caller_is_player` | Deep inherited command still sees player as caller. | Deferred. |
| `audit_inherited_command_pass_preserves_player_caller` | Command and `pass()` target retain player caller. | Deferred; `pass()` is not authorized. |

## Frozen first representation

No new stored command model is required for rows one and two. Keep the raw
`String` and one transient ordered `List<String>` in the existing concrete
runtime path. Element zero is the command verb; the remaining elements become
the existing `ListValue args`. Preserve the raw substring after the command
word as `argstr`, removing only the leading separation.

Use the existing concrete `verbLocals`, `executeStored`, `WorldTxn.verb`,
`BuiltinCatalog`, `VmState` staged output, and `MooServer` writer. Do not add a
parser interface, command helper, dispatch service, adapter, sender, facade, or
alternate evaluator.

## Open questions deliberately outside this slice

- Which concrete representation will carry dobj/iobj/preposition state when a
  later proven row first needs it?
- How will inherited command lookup retain both receiver and defining object
  once a row observes that distinction?
- Which existing owner will enforce packed argspec bits and construct `huh`
  context when those rows become active?
- How will `notify()` select among multiple live player connections when a
  later server row observes connection routing?

None of these questions can change the first two tokenizer decisions.
