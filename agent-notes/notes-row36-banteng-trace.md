# Row 36 Banteng WAIF trace

## Scope

- Read-only trace for `audit_waif_callers_preserve_this_and_verb_location`.
- No source edits or tests were run.

## Observed

- The row creates a child of `$waif`, adds `:audit_a`, `:audit_b`, and `:audit_c`, calls `obj:new()`, then calls the returned WAIF and inspects `callers()` entries.
- The stock fixture's `$waif` is object `#7`, `Waif Class`, with inherited executable verb `new`; its source calls `set_task_perms(caller_perms())`, assigns `player = caller_perms()`, then returns `new_waif()` (`LambdaMooV4ReaderTest.java:99-104,161-170`).
- `set_task_perms` exists, but `caller_perms`, `new_waif`, and `callers` have no `BuiltinCatalog` dispatch cases. The first missing call reached by `obj:new()` is therefore `caller_perms()`, which falls through to `E_VERBNF` at `BuiltinCatalog.java:458`; `new_waif()` is not reached.
- `MooValue` is a closed sealed family with integer, float, string, object, error, list, and map only; it has no WAIF value or WAIF type tag (`MooValue.java:14-45`).
- `VmState.Frame` currently stores program, locals, handlers, return mode, optional recycle target, programmer, and instruction pointer. It has no defining verb location or WAIF receiver metadata (`VmState.java:280-317`).

## Remaining read-only work

- Trace identifier/type constant handling and ordinary verb-frame caller metadata.
- Identify the smallest complete regression seam covering the exact row without proposing unverified representation details.
- No blocker.
