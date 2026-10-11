# Row 24 Banteng implementation trace

## 2026-07-15 restart checkpoint

- Assigned a read-only production trace for lifecycle row 24.
- The exact managed row sends split bytes `6961632d`, `ff`, `ff`, `2d6c6f67696e0d0a` and expects `do_login_command` args and argstr to equal `iac--login`.
- Current full-profile evidence says a raw `0xff` is ultimately exposed in a response and the harness cannot decode it.
- Required trace scope: socket read, Telnet parsing and split-state behavior, runtime line delivery, Latin-1/string representation, output encoding, and existing focused tests.
- Required result: exact cause, smallest existing change site without a new interface/helper/adapter/sender, and a focused JUnit regression that is red on current committed code.
- Boundaries: no tracked edits, no servers, and no Barn or Toast inspection.
- Observed so far: no production files have been inspected in this restarted subagent turn.
- Tried so far: nothing beyond recording this mandatory checkpoint.
- What worked: the prior row 23 work and repository state are owned by the parent; this row 24 trace can proceed independently and read-only.
- What did not work: not applicable yet.
- Current blocker: none.
- Next step: inspect current Git state and then trace the exact row 24 input path through existing production and tests.

## 2026-07-15 first production finding

- Git is on `master`, 13 commits ahead of `origin/master`, at committed row 23 slice `6fdd142 feat: dispatch user created login hook`.
- Tracked state is clean. Existing untracked notes and `pyghidra_mcp_projects/` remain outside this task.
- `MooServer.handleConnection` wraps the socket input in an ISO-8859-1 `InputStreamReader` and `BufferedReader`, then calls `readLine()` and passes the resulting Java `String` directly to `MooRuntime.executeLine` (`MooServer.java:98-129`).
- There is no byte-oriented Telnet parser or IAC state machine in the socket ingress path.
- Consequently the row's split `ff`, `ff` bytes are independently decoded as two U+00FF Java characters. They cannot be recognized as Telnet's escaped-IAC pair after this direct line-reader path, and they reach the runtime as two literal bytes when converted back through ISO-8859-1.
- `MooServer.writeLines` sends returned Java strings through an ISO-8859-1 `OutputStreamWriter` without Telnet output escaping (`MooServer.java:143-149`). If the captured malformed login string is printed, each U+00FF is emitted as raw `0xff`, explaining why the conformance harness encounters undecodable response bytes.
- Existing server tests use ISO-8859-1 character readers and writers. The initial search found no Telnet-focused production class or test class, so a focused regression likely belongs in `MooServerTest` and must use raw socket byte streams to prove state is preserved across separate writes.
- Current blocker: none. The exact runtime conversion and the narrowest inline change shape still need inspection.
- Next step: trace `executeLine` and login argument construction, inspect the relevant server-test setup, and search for any existing raw-byte socket assertions before reporting the minimal change and red test.

## 2026-07-15 completed read-only trace

### Exact path and cause

- Socket ingress loses Telnet semantics at `MooServer.java:101-128`: an ISO-8859-1 `InputStreamReader` plus `BufferedReader.readLine()` turns every octet into a Java character before any Telnet interpretation. There is no current Telnet parser anywhere under `src/main/java`.
- For row 24, the first split `0xff` becomes U+00FF and remains buffered as ordinary line data; the second split `0xff` also becomes U+00FF. The pair is therefore delivered as two data bytes instead of Telnet `IAC IAC`, which denotes one literal `0xff` data byte.
- `MooRuntime.executeLine` sends an unauthenticated line to `executeLogin` (`MooRuntime.java:115-123`). `executeLogin` tokenizes the Java string character by character (`:707-737`) and constructs both `args` and `argstr` with `encode`, which uses ISO-8859-1 (`:738-745`, `:1336-1337`). This faithfully preserves the already-wrong two bytes; it is not the defect site.
- `MooValue.StringValue` owns raw bytes and `toLiteral()` maps each unsigned byte back to the same Java character (`MooValue.java:143-183`). This representation is already correct for one literal `0xff` and needs no change.
- Task output is returned as Java `String` lines. `MooServer.writeLines` currently writes those via an ISO-8859-1 `BufferedWriter` (`MooServer.java:143-149`), so any diagnostic containing the captured malformed value emits raw `0xff` bytes. There is no Telnet egress quoting. That explains the harness decode failure after the semantic mismatch exposes the captured string.

### Smallest production change

- Keep `MooRuntime`, `MooValue`, and all public surfaces unchanged.
- In existing `MooServer.handleConnection`, replace character ingress with byte ingress and keep the state as local variables in that method: a line byte buffer, an `afterIac` boolean, and line-ending state. A normal `0xff` sets `afterIac`; `0xff` while `afterIac` appends exactly one `0xff` to the current line and clears the state. Because these locals live for the whole connection loop, the state survives arbitrary socket-read boundaries, including the row's separate writes. Convert only each completed data line to a Java string with ISO-8859-1 before calling the unchanged `runtime.executeLine`.
- Do not add a parser class, interface, adapter, sender, or new helper. This belongs inline at the current invalid `readLine()` change site.
- Change the existing output representation in `MooServer` from `BufferedWriter` to byte output (`BufferedOutputStream` is sufficient), including the existing `outputs` map and existing `writeLines` method. In that already-existing method, encode each line as ISO-8859-1 bytes and write every data `0xff` twice before CRLF. No new method is required. The runtime currently emits only data lines, not server-originated Telnet control frames.
- The row minimally requires escaped-IAC input across reads. The same boundary owns output doubling because otherwise a correct one-byte MOO string cannot safely cross Telnet output. Other Telnet command/subnegotiation states should not be invented for this single row unless the controlling authority for this slice explicitly requires them.

### Focused red JUnit regression

- Add one real-socket method to existing `MooServerTest`; do not create a new test abstraction.
- Retain the loaded `WorldTxn` in the test, start `MooServer`, authenticate a control connection, and use it to install `#0.audit_telnet_login` plus a `#0:do_login_command` that records `{args, argstr}`, calls `notify(player, "DONE")`, and returns `0`.
- Open a second raw socket, read the initial `DONE` caused by Banteng's initial blank login, enable TCP no-delay, then write and flush the chunks exactly as raw bytes: ASCII `iac-` followed by `0xff`; then, in a separate write, `0xff` followed by ASCII `-login\r\n`. A short separation can mirror the existing server test's asynchronous wait; the two explicit flushes make the intended chunk boundary visible.
- Read the second ASCII `DONE` before inspecting the retained `WorldTxn`; that notification is the synchronization proof that the login verb completed, avoiding an unsynchronized property read.
- Assert `#0.audit_telnet_login.toLiteral()` equals `{{"iac-\u00ff-login"}, "iac-\u00ff-login"}`: exactly one U+00FF in the sole word and original argstr. Current committed code is red with two U+00FF characters in both positions; the byte-state fix is green with one.
- If output escaping is included in the same kept slice, add an assertion over raw response bytes from a notification containing one U+00FF and require `ff ff 0d 0a`; an ISO-8859-1 `BufferedReader` must not be used for that assertion because it hides the wire contract.

### Existing tests and state

- Existing `MooServerTest` methods use ISO-8859-1 `BufferedReader`/`BufferedWriter`; none sends raw IAC or proves parser state across writes.
- `MooRuntimeTest.tokenizesEscapedLoginWordsWhilePreservingOriginalArgstr` only verifies MOO backslash tokenization after a Java `String` is already delivered. It does not cover Telnet.
- No tracked files were edited, no server or test was run, and no Barn or Toast source was inspected.
- Current blocker: none. Parent can implement the focused regression and inline socket-boundary change.
