# Phase 4 Toast PROXY authority

## Verified identity

- Banteng's owned stock Toast preflight completed successfully with exit 0.
- Pinned source `/root/src/toaststunt` is clean at `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Executable `/root/src/toaststunt/build-release/moo` matches SHA-256 `72fb1cf96cb303647a8ee72808e7c1ff62a491ecf44f547e6757e71ba2402bde`.

## Accepted findings

- Login-time PROXY recognition occurs only for a currently trusted proxy connection and any command whose first five bytes are exactly uppercase `PROXY`.
- The parser tokenizes six space-delimited tokens, ignores token 2, and therefore does not explicitly validate `TCP4`, `TCP6`, `UNKNOWN`, or a PROXY protocol version marker.
- Trust requires the listener's resolved `server_options.trusted_proxies` to be a list containing a string exactly equal to the connection's current peer IP.
- A successful rewrite changes connection name/destination IP to the address resolved from token 3 and changes source port to token 6. Other endpoint metadata remains unchanged.
- On successful rewrite the PROXY line is swallowed for login dispatch: `do_login_command` receives an empty args list and empty `argstr`. Parse/rewrite failure leaves the original command for ordinary login dispatch.

## State and next action

- Toast source inspection only; Barn was not inspected and no production files were edited.
- Report exact file and line authorities to the parent agent.
