# ANON value conformance groups 1-2

## Scope

- Authority: `docs/reports/anonymous-value-authority.md`.
- Work repo: `C:\Users\Q\code\moo-conformance-tests`.
- Only intended tracked file: `src/moo_conformance/_tests/language/anonymous_value_authority.yaml`.
- No commits, Barn edits, managed Toast, or server startup are authorized.

## Current findings

- The authority leaves exactly two relevant managed decision groups: one scalar row and one object/index/range row.
- Group 1 must cover false truth; same/copy versus distinct identity; direct relational results; E_TYPE from `toint`, `toobj`, and `tofloat`; the shared `*anonymous*` result from `tostr` and `toliteral`; and equal `value_hash` results for distinct identities.
- Group 2 must cover exact `properties(anon)` and `verbs(anon)` query results; direct indexed read and write; and ANON as a range endpoint.
- Existing `language/anonymous.yaml` already covers inherited property reads, inherited verb dispatch, and structural add-property/add-verb rejection. The new object row must not duplicate those.
- Existing `language/map_authority.yaml` already covers ANON map keys and insertion-dependent map topology. The new scalar row must only use direct scalar equality, relational, conversion, formatting, truth, and hash observations.
- The conformance repo is on `main`, ahead of `origin/main` by 24 commits, with no tracked modifications and many unrelated untracked artifacts. Those artifacts must remain untouched.
- The read-only Toast source checkout at `/root/src/toaststunt` is currently exactly the pinned authority commit `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Existing authority rows use catch expressions such as `` `expression ! ANY' `` to return an error value without aborting the row, and `try ... finally` is an established language feature for cleanup.
- Existing ANON tests recycle ANON values inside the MOO statement because ANON values cannot be safely round-tripped through the step capture/literal substitution mechanism.
- Pinned `src/execute.cc` gives direct ANON relational operators the default zero comparison: `<` and `>` are false, while `<=` and `>=` are true, including for two distinct ANON identities and in both operand orders.
- Pinned direct index read/write execution rejects an ANON base with E_TYPE.
- Pinned map range execution explicitly permits ANON endpoints; a same-key singleton range avoids the already-controlled insertion-topology behavior.
- Pinned `properties()` and `verbs()` enumerate only definitions directly on the queried object. A controlled ANON child has no local definitions even when its regular parent defines one property and one verb, so both exact query results are empty lists.

## State

- The only tracked change is the new `src/moo_conformance/_tests/language/anonymous_value_authority.yaml`.
- It contains exactly two rows: the scalar group and the object/query/index/range group.
- Both rows use `try ... finally` to recycle created ANON values; the object row also recycles its regular parent.
- No Toast process or server has been started.
- Expected results are provisional until the parent runs the pinned managed Toast oracle.
- `uv run --isolated --frozen pytest --collect-only -q src/moo_conformance/test_conformance.py -k anonymous_value_authority` succeeded: 11,554 total items discovered, 11,552 deselected, and exactly the 2 new ANON authority rows selected and collected.

## Blocker

- Managed Toast results remain intentionally unverified in this subtask. Normal and no-sync uv collection still hit the pre-existing `.venv\lib64` access error, but isolated uv collection succeeded without modifying that project environment.

## Next action

- Parent should run the two collected rows through the pinned managed Toast oracle, then replace any provisional expectation that the managed observation disproves.
