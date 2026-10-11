# Row 28 Banteng read-only trace

- Task: trace `audit_gmcp_subnegotiation_delivered_across_reads` on committed Banteng `81bc3bb`.
- Scope: inspect the exact YAML and current out-of-band runtime/command parsing only.
- Required result: explain why `argstr` matches while `args` contains one whole string; identify the semantic owner, smallest existing seam, and focused regression location.
- Constraints: do not edit production or test files and do not run servers.
- Initial state: no repository inspection has been performed in this subtask yet.
- Current blocker: none.
- Next step: verify branch/tracked state, then inspect the named row and directly involved code paths.

## Trace findings

- Verified branch `master`, HEAD `81bc3bb`; tracked files are clean. Only known untracked notes and `pyghidra_mcp_projects/` are present.
- Exact row is `moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:490-570`.
- The row sends IAC SB option C9, then `Core.Hello {"client":"audit"}`, then IAC SE in separate writes. It expects `args == {"~FF~FA~C9Core.Hello", "{client:audit}~FF~F0"}` and the original fully escaped frame in `argstr`.
- `MooServer.java:124-155` preserves subnegotiation state across reads and constructs the complete escaped command. Its dispatch at lines 148-150 passes that complete string to the runtime, so byte framing is not the source of the args mismatch.
- `MooRuntime.executeTransportOutOfBand()` at lines 663-682 supplies `new ListValue(List.of(encode(command)))` as `args`, while supplying `command` unchanged as `argstr`.
- `verbLocals()` at lines 1334-1348 simply installs those independently supplied values. Therefore `argstr` matches but `args` is one whole string by construction.
- Existing normal/OOB line parsing at `MooRuntime.java:200-255` tokenizes whitespace and honors quoting/backslash escapes before dispatch. That parser path currently belongs to `executeLine`, not transport OOB dispatch.
- Current blocker: none. Remaining inspection is limited to identifying the narrowest existing semantic seam and exact focused regression placement without proposing a new helper/type.

## Owner and regression placement

- The semantic owner is `MooRuntime.executeTransportOutOfBand`, not `MooServer`: the server has already assembled the correct complete transport command, and the runtime decides the MOO `args`/`argstr` call frame.
- The smallest existing seam is the `arguments` value passed to `verbLocals` at `MooRuntime.java:675-681`. The singleton construction at line 680 is the entire mismatch; `verbLocals` itself is generic and correct.
- The already-established tokenization semantics are the inline word scan in `MooRuntime.executeLine` at lines 200-239: whitespace separates words outside quotes, quotes are removed, and backslash escapes preserve the following character. Applied to the GMCP frame, that yields exactly `~FF~FA~C9Core.Hello` and `{client:audit}~FF~F0`, while the unmodified command remains `argstr`.
- No existing test calls `executeTransportOutOfBand` directly. The focused runtime regression belongs in `src/test/java/moo/runtime/MooRuntimeTest.java`, immediately after `dispatchesTextualOutOfBandInputToTheConnectionListener` (currently lines 1286-1311), because that adjacent test establishes the listener call-frame contract for textual OOB input.
- `src/test/java/moo/server/MooServerTest.java:342-396` already covers subnegotiation framing/state across separate writes. A new server-level test would duplicate transport coverage and obscure that the failing semantic seam is runtime argument construction.
- Current blocker: none. Read-only trace is complete; no production/test files were edited and no server was run.

## Binary-mode row trace

- New read-only task: trace `audit_binary_mode_dispatches_raw_chunk_without_newline` on committed Banteng `85bc14f`.
- Verified branch `master`, HEAD `85bc14f`; tracked files are clean. Only known untracked notes and `pyghidra_mcp_projects/` are present.
- Exact row begins at `moo-conformance-tests/src/moo_conformance/_tests/audit/gap_followups_toast_oracle.yaml:572`.
- The row creates a fresh player with an `auditbin` command verb that appends `argstr` to `#0.audit_binary_seen`, logs the fresh connection in through `do_login_command`, then calls `set_connection_option(player, "binary", 1)` and sends ASCII `auditbin` as raw bytes without CR/LF.
- Initial code search shows `MooServer` currently calls `input.read()` and dispatches only at CR, LF, or EOF; `MooRuntime.ConnectionState` currently stores `holdInput` and `disableOob` but no visible binary-mode field.
- `BuiltinCatalog` recognizes `set_connection_option`; its exact request/runtime propagation remains to be inspected.
- Current blocker: none.
- Next step: inspect the row expectation and the concrete option-control and server read-loop seams.

### Binary-mode findings

- The row expectation is `{""}` at YAML lines 628-630: exactly one `auditbin` invocation appends its empty argument string. Current `{}` means no invocation occurred, not that the verb ran with a different argument value.
- `BuiltinCatalog.setConnectionOption()` at `src/main/java/moo/builtin/BuiltinCatalog.java:533-560` accepts only `hold-input`, `flush-command`, and `disable-oob`. `"binary"` falls through to `null` and returns `E_INVARG` at lines 554-557, so no deferred option request is staged.
- The closed `ConnectionOption` enum at `BuiltinCatalog.java:1943-1948` likewise has no binary member.
- `MooRuntime.applyConnectionOptionRequests()` at `src/main/java/moo/runtime/MooRuntime.java:1198-1235` applies only those three existing options. `ConnectionState` has no binary flag.
- `MooServer.handleConnection()` at `src/main/java/moo/server/MooServer.java:124-208` owns byte accumulation. After Telnet handling, ordinary bytes are appended at lines 202-203 and dispatched only on CR, LF, or EOF at lines 183-208. The connection remains open and the row sends no delimiter, so `auditbin` is never dispatched.
- This establishes two causally ordered absences: the option is rejected before runtime state can change, and the server has no binary-mode branch that could dispatch the raw available chunk.
- Current blocker: none. Remaining work is exact focused regression placement and precise statement of the existing seams, without proposing a new abstraction.

### Binary-mode owner and regression placement

- Why the expected value is `{""}`: after binary transport dispatches the raw chunk `auditbin`, existing command parsing finds the player verb and supplies the text after the command word as `argstr`; because there is no following text, that substring is empty. The verb appends it once. Actual `{}` proves zero dispatches.
- The transport semantic owner for the active symptom is `MooServer.handleConnection`, because it alone decides when accumulated socket bytes become one runtime command. `MooRuntime.executeLine` already has the correct command/empty-argstr behavior once invoked.
- The option contract is owned by the existing closed `BuiltinCatalog.ConnectionOption` request path and `MooRuntime.applyConnectionOptionRequests`; both currently omit binary mode. The existing runtime-to-server control boundary is the already-present `BuiltinCatalog.ListenerControl`, implemented directly by `MooServer` and retained by `MooRuntime`. No separate helper/interface/adapter/sender is indicated by the current architecture.
- This cannot be corrected at a single parser expression: the smallest existing surfaces are the option-name/enum branch (`BuiltinCatalog.java:549-560, 1943-1948`), the current request application (`MooRuntime.java:1198-1235`), and the existing byte dispatch loop (`MooServer.java:124-208`). Normal command parsing should remain unchanged.
- Focused regression belongs in `src/test/java/moo/server/MooServerTest.java` immediately after `dispatchesTelnetSubnegotiationSplitAcrossWritesAsOutOfBandInput` (currently lines 342-396). It must use a real logged-in target socket, set the target player's binary option through a control connection, write `auditbin` bytes without CR/LF, leave the socket open, and assert one empty argstr was recorded.
- `MooRuntimeTest.java:525-580` is the existing connection-option neighborhood, but a runtime-only test cannot establish delimiter-free socket dispatch because it invokes `executeLine` directly.
- Current blocker: none. Read-only binary-mode trace is complete; no production/test files were edited and no server was run.
