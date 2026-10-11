# String builtin Toast semantics audit

## Scope

Read-only audit of `C:\Users\Q\code\moo-conformance-tests\src\moo_conformance\_tests\basic\string.yaml` for `strsub`, `index`, `rindex`, `strcmp`, `decode_binary`, and `encode_binary` against primary ToastStunt source in WSL.

## Current findings

- Verified `/root/src/toaststunt` HEAD is exactly `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Read the complete 270-line YAML family and identified the rows concerning the six named builtins.
- Primary registrations are in `src/list.cc:1760-1762,1789-1795`; implementations are `bf_strsub`, `bf_strcmp`, `bf_index`, `bf_rindex`, `bf_decode_binary`, and `bf_encode_binary` in `src/list.cc`.
- `strsub`, `index`, and `rindex` default `case_matters` to false; a truthy optional flag makes matching case-sensitive. Several YAML row names describe this backwards even though their expected values match Toast.
- `strcmp` applies C `strcmp` and normalizes its result to `-1`, `0`, or `1`.
- Binary encoding is byte-oriented with no character-set transcoding: integers must be `0..255`, lists recurse, strings contribute their stored bytes, printable ASCII/space is literal except `~`, and other bytes become uppercase `~XX`.
- Binary decoding accepts two case-insensitive hex digits after each `~`; malformed escapes return `E_INVARG`. Fully mode returns unsigned byte integers. Default mode groups printable ASCII/space/tab into strings and emits other bytes, including NUL and high bytes in the C locale, as integers.
- Every relevant YAML expectation agrees with these implementations; the misleading case-mode names are metadata defects, not expectation defects. The bcrypt row containing `index` is unconditionally skipped and therefore has no runtime result in this family.

## State and blocker

- Source audit is complete and ready to report.
- No blocker.
- No implementation files have been edited.

## Next action

Report the row-by-row mapping, exact call contracts, and source locations to the requesting parent agent.
