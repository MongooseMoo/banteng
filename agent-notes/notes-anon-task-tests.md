# ANON task-reference conformance handoff

## Scope

- Implement only managed ANON decision group 3 from
  `docs/reports/anonymous-value-authority.md`.
- Intended tracked file is only
  `C:/Users/Q/code/moo-conformance-tests/src/moo_conformance/_tests/language/anonymous_task_authority.yaml`.
- No managed Toast run, server start, fixture edit, Barn edit, commit, or other
  tracked-file edit is authorized.

## Current findings

- `language/anonymous.yaml` has skipped/no-op placeholders for `callers()`,
  `task_stack()`, `queued_tasks()`, and error traceback ANON visibility.
- Working harness patterns exist for multi-step wizard/programmer execution,
  fork/suspend inspection, cleanup with `kill_task()`, and caught exception
  traceback inspection through exception element 4.
- Pinned Toast source at
  `aecc51e9449c6e7c95272f0f044b5ba38948459e` confirms:
  - `anonymizing_var_ref()` preserves an ANON for a wizard or its owner and
    otherwise returns a null-pointer value that still has type `ANON`.
  - `callers()` and `task_stack()` apply that function to frame fields 1
    (`this`) and 4 (`verb location`).
  - `queued_tasks()` applies it to fields 6 (`verb location`) and 9 (`this`).
  - caught error traceback construction passes `NOTHING` as the viewer, so an
    ANON `this` is redacted even when the catcher is the ANON owner or a wizard.
- The harness exposes wizard and programmer actors. Owner, wizard, and
  other-viewer observations remain executable without a fixture change by
  creating the ANON/task as programmer, inspecting it as programmer and
  wizard, then having wizard change the ANON owner before programmer inspects
  the same still-owned task as an other viewer.
- ANON verbs must be inherited from a regular class; structural `add_verb()` on
  the ANON itself is not suitable for this authority row.

## State

- Target repository branch is `main`.
- Tracked worktree was clean before editing; many unrelated untracked artifacts
  exist and must remain untouched.
- The intended YAML now exists with three executable scenarios:
  1. `callers()` owner, wizard, and other-viewer handling;
  2. combined `task_stack()` and `queued_tasks()` owner, wizard, and
     other-viewer handling on one suspended task;
  3. error traceback handling for owner, wizard, and other viewer.
- Each redacted value is checked with `typeof()`, `valid()`, and `toliteral()`.
- `git diff --name-only` is empty because the new target file is untracked;
  targeted status reports only
  `?? src/moo_conformance/_tests/language/anonymous_task_authority.yaml`.
- Ruby YAML parsing succeeded, and the whitespace check reported no errors.
- Parent ran pinned managed Toast: 5 selected, 4 passed. The callers and error
  traceback scenarios pass. The only failure is the final programmer step of
  `suspended_task_views_preserve_owner_and_wizard_but_redact_other`, after the
  wizard changes `class.anon.owner`, where evaluation raises `E_PERM` before
  returning the intended summaries.

## Blocker

- Static pytest collection could not begin. Both
  `uv run pytest --collect-only ...` and its `--no-sync` form stopped while uv
  tried to remove the repository's existing `.venv/lib64` entry and received
  Windows access denied. No server or managed Toast process was started.
- Expectations remain source-derived and provisional until the parent runs
  pinned managed Toast.
- The exact denied call in the suspended-task other-viewer step is under
  source inspection. The likely gate is `task_stack()` checking the current
  suspended task programmer before constructing/redacting its stack, but the
  pinned `progr_of_cur_verb()` and `queued_tasks()` paths still need exact
  confirmation.
- Pinned source now confirms the denied call is `task_stack(id)`: it is the
  only expression in that step with an `E_PERM` path, and `bf_task_stack()`
  checks `!is_wizard(progr) && progr != owner` before calling
  `make_stack_list()` and its ANON redaction. Therefore a genuine non-owner
  cannot observe a redacted `task_stack()` ANON field; the observable contract
  is `E_PERM`.
- `queued_tasks()` has no `E_PERM` return in this path. It filters task
  visibility and applies `anonymizing_var_ref()` to fields 6 and 9 while
  constructing visible rows. The queue-only other-viewer case remains the
  next source/test adjustment.

## Next action

- Inspect pinned Toast's suspended-task programmer calculation and queue
  filtering. Then change only the suspended-task scenario: encode `task_stack`
  permission denial if the gate makes redaction unobservable, while retaining
  independent `queued_tasks()` redaction if source proves it observable.
- Replace the failing combined other-viewer expression with an explicit caught
  `task_stack()` `E_PERM` assertion and a separate queue-only observation that
  does not let the earlier gate prevent evaluation.

## Correction completed

- Changed only the suspended-task scenario in
  `anonymous_task_authority.yaml`; the passing callers and error-traceback
  scenarios are unchanged.
- Setup now also defines inherited ANON verb `queue_delay`, whose delayed fork
  snapshots the programmer and ANON `this` in `forked.a`.
- Owner and wizard still observe valid ANON values from suspended
  `task_stack()` and the delayed fork's `queued_tasks()` row.
- After ANON ownership changes, the programmer assertion now expects:
  - caught `E_PERM` from `task_stack()`; and
  - type 12, invalid, `*anonymous*` from delayed fork field 9 in
    `queued_tasks()`.
- Cleanup kills both the suspended task and delayed fork.
- YAML parsing and trailing-whitespace checks pass. No managed Toast run or
  commit was performed. The target remains the only intended new repository
  file.
