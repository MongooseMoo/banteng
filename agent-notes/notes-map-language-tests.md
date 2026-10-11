# MAP language conformance test handoff

## Scope

- Target repo: `C:\Users\Q\code\moo-conformance-tests`
- Only intended tracked file: `src/moo_conformance/_tests/language/map_authority.yaml`
- No commit, Barn edit, or managed Toast execution is authorized.

## Findings and observations

- `docs/reports/map-value-authority.md` authorizes five focused language-test groups: INT comparator width, ANON key surfaces, reverse-insertion BOOL/WAIF/ANON topology, literal value-before-key evaluation, and detailed multi-key `mapdelete` failure.
- Existing rows already cover ordinary MAP behavior, BOOL key reachability across both insertion orders, one-direction WAIF key identity, generic multi-key deletion E_RANGE, and OBJ comparator collision. The new file must avoid duplicating those settled checks.
- Pinned Toast source makes INT keys `0` and `4294967296` comparator-equal because same-type INT comparison narrows subtraction to `int`; replacement stores the last key/value.
- Pinned Toast source accepts ANON keys through VM literal/index/mapvalues paths, while `maphaskey` and `mapdelete` reject ANON keys with E_TYPE.
- Distinct BOOL, WAIF, and ANON values compare positive in both directions, so reverse insertion can change comparator-tree traversal and equality behavior.
- Pinned `bf_mapdelete` raises E_RANGE for a missing key in a multi-key list with message `Key 99 not found in map` and raised value `99`.

## State

- The target repo is on `main`, ahead of `origin/main` by 23 commits, with no tracked changes and many unrelated untracked artifacts that must remain untouched.
- Created `src/moo_conformance/_tests/language/map_authority.yaml` with seven focused rows covering the five authorized decision groups.
- A static name scan confirms every new test name occurs only in the new file.
- The repository linter/collection entry point could not start because both `uv run` and `uv run --no-sync` fail while trying to remove the pre-existing `.venv\lib64` entry with access denied.
- No Toast server has been started.

## Blocker

- Static Python/YAML collection is blocked by the existing local `uv` environment failure. Expectations remain provisional until the parent runs the pinned Toast oracle.

## Next action

- Parent should review the new YAML, run collection in a working environment, and adjudicate the provisional expectations against pinned Toast.
