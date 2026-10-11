# Phase 5 gate validation checkpoint

- Assignment: read-only validation of every Phase 5 named path, profile, manifest, source directory, test directory, gate prerequisite, and the pinned profile schema needed by `extract_toast_builtin_names_wsl.sh`.
- Current state: no Phase 5 repository inspection has begun in this subtask.
- Findings/observations: none yet.
- Blocker: none known; repository-local evidence still needs inspection.
- Next action: inspect the controlling Phase 5 plan text and each artifact it names, without editing tracked project files and without using notes as authority.

## Validation findings

- Phase 4 is committed at `e8bf5ab`; at the initial status check there were no tracked source modifications.
- Both Phase 5 Toast manifests exist and pass the current `scripts/verify_toast_profile_wsl.sh` preflight.
- `scripts/run_managed_wsl.sh`, Java 25, Gradle wrapper, `jq`, `git`, and `sha256sum` are available; the named builtin and generated-builtin conformance directories exist in the sibling conformance repository.
- The extraction script and checked-in builtin-name fixtures do not yet exist, but Phase 5 explicitly creates them.
- Confirmed blocker: current Toast manifests/verifier pin `src/include/options.h` but do not pin generated `build-release/config.h`. Optional builtin registration depends on macros in that generated header, while raw source parsing would also wrongly include conditionally absent `background_test`.
- Current generated config paths are `/root/src/toaststunt/build-release/config.h` and `/root/src/toaststunt-mongoose/build-release/config.h`; both currently hash to `fe460a03f467e79293e5855bd604a44b2961921f155f711d256cd9bb65cf37d7`.
- Both generated headers plus the source-option header are sufficient for current source preprocessing: all 29 registration-bearing C++ source files in each pinned source checkout preprocess successfully with the exact build include paths, and the CMake compile flags add no registration-affecting `-D` definitions.
- The controlling plan has now been edited concurrently to make the previously missing Phase 0 generated-configuration authority and Phase 5 preprocessing requirement explicit. The implementation artifacts still need to be repaired to match that plan.
- Next action: finish the exact minimal field/verifier/test/extractor implications and report them to the parent agent. Do not stage or commit this note.

This checkpoint file must remain untracked and uncommitted.

## Object hierarchy authority checkpoint

- New read-only assignment: derive pinned Toast/YAML semantics for `ancestors`, `anon`, `children`, `chparent`, `chparents`, `descendants`, `isa`, and `parents`, and map them to existing Banteng APIs/tests.
- Both pinned profiles use the identical `src/objects.cc` blob `8375dd9f80278318621dd598705aa35cff7b1dbd`.
- `anon` is not a production registration: its implementation and registration are guarded by `#ifndef NDEBUG`, while both verified Release builds use `-DNDEBUG`. It must be excluded from the canonical fixture and Banteng manifest.
- The seven production registrations are `chparents(ANY, LIST)`, `chparent(ANY, OBJ)`, `parents(ANY)`, `children(ANY)`, `ancestors(ANY[, ANY])`, `descendants(ANY[, ANY])`, and `isa(ANY, ANY[, INT])`.
- Current Banteng `WorldObject`/v17 model stores only one `long parent`; `WorldTxn.changeParent` supports only one permanent-object parent. `LambdaMooV17Codec.requireSingleParent` explicitly rejects multiple inheritance. This cannot satisfy exact `chparents`, ordered `parents`, multiple-parent `children`, or traversal semantics.
- Existing usable APIs include `WorldTxn.object`, `anonymousObject`, `createAnonymousObject`, `changeParent`, and immutable `WorldObject.parent/children`; no complete multiple-inheritance or anonymous-child topology API exists.
- Next action: finish source/YAML semantic ordering, permission, error, and test requirements and report them to the parent. Do not stage or commit this note.

### Hierarchy details

- Toast `db_ancestors`/`db_descendants` perform ordered depth-first pre-order traversal over each object's ordered parent/child list, suppressing duplicate nodes in DAGs. Truthy optional arg prepends the subject itself; false/omitted excludes it.
- `isa` considers self a match, walks the parent graph, returns `0` for invalid subjects/parents instead of `E_INVARG`, accepts an ordered list of candidate parents, and in return-object mode returns the first matching candidate or `$nothing`.
- `chparent`/`chparents` validate subject/parent types, validity, control, fertile permission, cycles, duplicates, and inherited-property conflicts; success returns integer zero. Error ordering is type, non-wizard extra internal args, validity, permission, recursive move, then property/duplicate `E_INVARG`.
- The current single-parent world model and v17 codec are the blocking mismatch for exact multiple-inheritance semantics; extending only `BuiltinCatalog` cannot satisfy the pinned rows.
