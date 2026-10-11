# VM structural-state slice

## Current findings and observations

- This slice is limited to `VmSnapshot`, `VmState`, `MooVm`, and their focused tests.
- The pinned Toast v17 grammar requires typed finally reasons and exact loop state for list, string, map, integer-range, and object-range loops.
- Existing runtime behavior must remain intact while the snapshot model gains sufficient typed state for imported suspended activations.
- Pinned Toast `execute.cc` defines durable finally reasons in fixed order: 0 fall-through, 1 raise, 2 uncaught, 3 return, 4 abort, and 5 exit. Raise carries the four-element exception tuple; exit carries runtime-stack depth plus a bytecode target. Reason 4 never enters a finally and must be rejected by the importer.
- Collection loop stacks retain the original list/string/map plus the exact next cursor. List/string cursors are one-based and may be `length + 1`; map cursors serialize as the next key or `TYPE_CLEAR` when exhausted.
- Range loop stacks retain the exact next value and inclusive end. Integer and object ranges are distinct; the maximum-value edge is represented by decrementing the end after yielding the maximum, so relative indexes are lossy.
- Existing tracked changes in these files belong to concurrent multiple-inheritance/anonymous-object work and must be preserved while patching the structural-state sections.

## Current state

- `VmSnapshot` now has sealed typed finally variants (`FallThrough`, full-tuple `Raise`, `Uncaught`, arbitrary-value `Return`, depth/target `Exit`) and sealed typed loop variants (`CollectionLoop`, `RangeLoop`) with distinct collection/range kinds and constructor validation.
- `VmState` snapshot/restore now carries those typed values directly and has typed mutable collection/range runtime cursors.
- `MooVm` is now migrated: finally completion dispatches every typed reason; collection execution retains exact list/string/map base and next cursor; range execution supports integer/object types and Toast's maximum-value end-decrement edge.
- The first WSL compile used Java 25 correctly after two environment-only command failures. Javac reported no error in the three assigned VM source files. It is currently stopped by five concurrent `ToastV17ProgramLayout` constructor-arity errors plus legacy API calls in `MooRuntime` and `PublicationScheduler`; exact locations were sent to the parent importer agent and root.
- Focused tests now cover all five durable finally reasons, exact full raise tuple, exit operand cleanup, list/string/map cursor forms, map next-key/exhaustion, list `length + 1`, integer/object range forms, the object maximum edge, and two independent nested loop cursors.
- `git diff --check` passes for the assigned files and no legacy finally/loop state names remain in the VM sources or focused tests.
- After the scheduler and layout consumers were migrated, shared Debian Java 25 `compileJava compileTestJava` passed.
- The serialized focused gate `./gradlew --no-daemon test --tests moo.vm.VmSnapshotTest --tests moo.vm.MooVmTest` passed in 13 seconds: 7 actionable tasks, 1 executed and 6 up-to-date.
- The parent implementation is concurrently building the v17 task importer; this slice supplies the VM representation and execution support it needs.

## Blocker

- None for this assigned slice. Consumer compilation and focused execution tests are green.

## Next action

- Keep the assigned VM files stable and hand the green typed-state slice back to the parent importer. Do not stage or commit this notes file.
