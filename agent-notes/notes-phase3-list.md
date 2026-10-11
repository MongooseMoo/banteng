# Phase 3 `basic/list.yaml` slice

## Current state

- `basic/string.yaml` is committed as `0e12e28`; tracked state was clean afterward.
- Pinned Toast passes `basic/list.yaml` 21/21.
- Banteng passes 13/21 and fails 8/21.

## Observed

- Every Banteng failure is `E_VERBNF`.
- The exact absent builtins are `listappend`, `listinsert`, `listdelete`, `listset`, and `setadd`.
- Existing list `+` concatenation/appending already passes all eight applicable operator rows, so no VM arithmetic change is authorized by this family.
- Existing `ListValue` owns immutable elements and already supports end append and list concatenation; indexed replacement/insertion/deletion are not currently methods on that value.

## Tried

- Ran exact managed Toast and Banteng commands for the family.
- Read the active Phase 3 plan after the passing Toast run.
- Started two bounded read-only Codex audits for pinned Toast semantics and Banteng ownership/history.

## Blocker

- None.

## Next action

- Primary Toast confirms `listappend(list, value [, index])` inserts after the explicit index and defaults to the end; `listinsert` inserts at the explicit index and defaults to the front; both clamp to valid insertion positions.
- `listdelete` and `listset` raise `E_RANGE` outside one-based list bounds.
- `setadd` returns the original value sequence when an equal element exists and otherwise appends.
- Added focused manifest and handler tests first; they failed only because the five specs were absent.
- Added exactly the five pure-VM specs and handlers to the existing `BuiltinCatalog` owner. No other production file changed.
- Focused `BuiltinCatalogTest` passes.

## Next action

- Both Codex audits completed and confirmed the pinned Toast contracts, the single `BuiltinCatalog` owner, and the existing immutable `ListValue` APIs. Their required handoff notes remain untracked.
- Managed Banteng `basic/list.yaml` now matches pinned Toast at 21/21.
- Full `JAVA_HOME=/opt/java/25 ./gradlew check` passed.
- `git diff --check` passed before a line-wrap-only correction.
- The exact tracked diff contains only `BuiltinCatalog.java` and `BuiltinCatalogTest.java`.
- The full Gradle check passed again after the line-wrap-only correction.

## Next action

- The final `git diff --check` passed; only `BuiltinCatalog.java` and `BuiltinCatalogTest.java` were staged.
- The kept list slice was committed as `4c7f55f feat: add phase 3 list builtins`.
- Tracked state was clean after the commit; all notes remained unstaged.
- The next path, `language/error_authority.yaml`, already passes both pinned Toast and Banteng at 4/4, so it requires no source slice or commit.

## Next action

- Reread the active Phase 3 plan after the passing Banteng error-authority run, then start `language/control_flow.yaml` with pinned Toast followed by Banteng.
