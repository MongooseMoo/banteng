# Row 32 Barn authority audit

Scope: only `audit_intrinsic_command_table_roundtrip` from
`C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:772-802`.

## Current findings

- The row calls `connection_options(player, "intrinsic-commands")`, sets the option to
  `{"PREFIX", "SUFFIX"}`, reads it back, sets it to integer `1`, and reads it back again.
- The expected values are, in order:
  1. `{ ".program", "PREFIX", "SUFFIX", "OUTPUTPREFIX", "OUTPUTSUFFIX" }`
  2. `{ "PREFIX", "SUFFIX" }`
  3. `{ ".program", "PREFIX", "SUFFIX", "OUTPUTPREFIX", "OUTPUTSUFFIX" }`
- Cleanup repeats `set_connection_option(player, "intrinsic-commands", 1)` as wizard.
- The likely normative Barn authority is
  `C:/Users/Q/code/barn/spec/builtins/network.md`, especially sections 6.1
  `set_connection_option` and 6.2 `connection_options` beginning at lines 224 and 239.
- The complete relevant normative spec text is sparse:
  - `spec/builtins/network.md:224-236` declares
    `set_connection_option(player, option, value) -> none`, but lists only
    `hold-input`, `disable-oob`, and `binary`, all with `0/1` values.
  - `spec/builtins/network.md:239-244` declares
    `connection_options(player) -> MAP`; it does not document the two-argument
    single-option form used by this row.
  - `spec/builtins/network.md:265-272` gives only general network error conditions.
  - The spec does not mention `intrinsic-commands`, its accepted value types, ordering,
    case, integer-1 restore behavior, or option-specific errors.
- `C:/Users/Q/code/barn/builtins/signatures.go:606-652`
  (`builtinConnectionOptions`) is the query entry:
  - accepts 1 or 2 arguments;
  - requires an object reference target;
  - permits non-wizards only for `ctx.Player`;
  - requires a live resolved connection;
  - in the two-argument form requires a string, validates the option, and returns the
    stored value directly;
  - in the one-argument form returns a sorted list of `{name, value}` pairs, not a MAP.
- `C:/Users/Q/code/barn/builtins/network.go:1252-1317`
  (`builtinSetConnectionOption`) is the mutation entry:
  - requires exactly 3 arguments;
  - parses the connection target, resolves it, and permits non-wizards only for self;
  - requires a string option name and validates it;
  - for `intrinsic-commands`, a truthy LIST is accepted only if every element is a
    string exactly equal to one of `.program`, `PREFIX`, `SUFFIX`, `OUTPUTPREFIX`, or
    `OUTPUTSUFFIX`; otherwise it returns `E_INVARG`;
  - any other truthy type is replaced with `defaultIntrinsicCommands()`;
  - after storing, it returns integer `0`, despite the spec saying `none`.
- The state helpers `defaultIntrinsicCommands`, `getConnectionOptions`, and
  `setConnectionOption` are all in `builtins/network.go`:
  - `network.go:101-106` owns process-global state as a mutex-protected
    `map[ObjID]map[string]Value`, keyed by player, rather than on the `Connection`.
  - `network.go:169-177` validates option names case-sensitively; only the exact
    lowercase `intrinsic-commands` spelling is recognized.
  - `network.go:179-187` builds the exact default list, in row-expected order and case:
    `.program`, `PREFIX`, `SUFFIX`, `OUTPUTPREFIX`, `OUTPUTSUFFIX`.
  - `network.go:189-198` includes that list in the default option map.
  - `network.go:201-213` returns a shallow copy of an existing per-player option map or
    a fresh default map when absent.
  - `network.go:215-225` creates the per-player default map on first mutation and then
    stores the supplied value directly under the option name.
- A scoped search found no production deletion/reset of `connectionOptionState.byPlayer`;
  only tests delete entries. This is a state-lifetime concern but does not alter the
  within-row round trip.
- `network.go:138-146` accepts OBJ, ANON, or INT connection-target values for the setter;
  `signatures.go:611-614` accepts only `isObjectRef` for `connection_options`, whose exact
  definition still needs reading if target-type comparison is included.
- `network.go:149-167` resolves a target through `hostOf(ctx).ConnManager`, with a
  compatibility fallback that can return any connected player when resolving self.
- Exact accepted value types for `intrinsic-commands` depend on
  `types.Value.Truthy()` (`types/value.go:138-157`). It establishes this exact matrix:
  - nonzero INT (including `1`), nonzero FLOAT, true BOOL, nonempty STR, and nonempty MAP
    are truthy non-lists and therefore reset to the canonical default table;
  - a nonempty LIST is validated element-by-element; every element must be a STR exactly
    matching the five-name whitelist; order and duplicates are accepted and preserved;
  - zero INT/FLOAT, false BOOL, empty STR/LIST/MAP are falsey and stored unchanged;
  - OBJ, ANON, ERR, and WAIF are always falsey by Barn truthiness and are stored unchanged.
  Thus the third value has no declared type restriction at the public signature, and the
  only option-specific rejection is a nonempty LIST with a non-string or non-whitelisted
  element (`E_INVARG`).
- Public registration/signature/dispatch:
  - `builtins/registry.go:197` registers `connection_options` and line 203 registers
    `set_connection_option`.
  - `builtins/function_signatures_generated.go:36` declares query args as 1-2, OBJ then
    optional STR; line 171 declares setter args as exactly 3, OBJ, STR, ANY.
  - `builtins/registry.go:349-377` attaches those signatures and
    `registry.go:418-435` validates before calling the concrete builtin (after any
    protected-builtin redirect).
  - Consequently, the public builtin path accepts only a regular OBJ first argument;
    `parseConnectionTarget`'s INT/ANON acceptance in the setter is unreachable through
    ordinary unredirected public dispatch.

## Exact permissions and errors on the real builtin path

- `connection_options`:
  - wrong count -> `E_ARGS`; non-OBJ target or non-STR option -> `E_TYPE` at signature
    validation;
  - for a non-wizard querying anyone other than self -> `E_PERM` before connection
    resolution (`signatures.go:615-619`);
  - missing live connection -> `E_INVARG`;
  - unknown/case-mismatched option -> `E_INVARG` (`signatures.go:627-635`).
- `set_connection_option`:
  - wrong count -> `E_ARGS`; non-OBJ target or non-STR option -> `E_TYPE` at signature
    validation;
  - target connection is resolved before the self/wizard permission check, so a missing
    connection -> `E_INVARG`, while a connected other player for a non-wizard ->
    `E_PERM` (`network.go:1257-1267`);
  - unknown/case-mismatched option -> `E_INVARG` (`network.go:1269-1275`);
  - invalid nonempty intrinsic list element -> `E_INVARG` (`network.go:1283-1298`);
  - success returns concrete INT `0` (`network.go:1316`); the spec labels the result
    `none` but does not define that label further in this section.
- `registry.go:418-473` adds a configuration-dependent protected-builtin layer before
  argument validation: a configured `#0:bf_<name>` can receive the raw call; absent a
  wrapper, a protected call by a non-wizard is `E_PERM`, while a wizard falls through.
  No row-specific database configuration was inspected, so this remains a conditional
  public-dispatch fact rather than an asserted row outcome.

## Round-trip agreement and ownership

- For the row's connected wizard and regular player object, Barn produces exactly:
  1. `{ ".program", "PREFIX", "SUFFIX", "OUTPUTPREFIX", "OUTPUTSUFFIX" }` from the
     canonical default;
  2. `{ "PREFIX", "SUFFIX" }` because the valid nonempty list is stored verbatim;
  3. the canonical five-element default again because INT `1` is truthy and non-list.
- The row does not observe the setter's INT `0` return, and cleanup repeats the same
  successful reset. Therefore there is no Barn disagreement with the row's asserted
  value/order/case.
- Barn does differ from the normative spec surface: the spec omits this option and the
  query overload, and says the one-argument query returns MAP while the implementation
  returns a sorted LIST of `{name, value}` pairs (`signatures.go:638-651`). These spec
  gaps are outside the row's two-argument asserted value.
- `command/intrinsic.go:22-53` independently classifies `.program` abbreviations and
  case-insensitive PREFIX/OUTPUTPREFIX/SUFFIX/OUTPUTSUFFIX; actual command handling is
  `server/input_processor.go:570-587`. A production-only caller search shows neither
  path reads the stored `intrinsic-commands` option. Therefore the option table is not
  the semantic authority for actual intrinsic dispatch in Barn.
- Smallest current owner of this row's observable semantics is
  `builtins/network.go`: `defaultIntrinsicCommands`, `defaultConnectionOptions`,
  `getConnectionOptions`, `setConnectionOption`, and the option-specific branch in
  `builtinSetConnectionOption`, with `builtinConnectionOptions` in
  `builtins/signatures.go` as the public reader. There is no narrower shared intrinsic
  table owner in current Barn.
- `spec/go-design.md:19-32` nominally assigns intrinsic commands to package `command`,
  which underscores the current split: dispatch belongs to `command`, while the
  connection-option table lives separately in `builtins`.

## Constraints and state

- No tracked files edited.
- Ward has not been run.
- No runtime/oracle execution has been performed.
- No blocker. The requested static trace is complete. No Barn tests specifically mention
  `intrinsic-commands`; no runtime command or oracle was run, so the row outcome above is
  a direct code-path determination rather than an executed result.
