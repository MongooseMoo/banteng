# Restart queued-task codec subtask

## Scope

- Modify only `src/main/java/moo/persistence/LambdaMooV17Codec.java` and one new focused persistence test file.
- Replace the checkpoint task placeholder with an immutable queued-task DTO sufficient for a delayed fork.
- Implement the pinned-Toast v17 queued-task textual grammar, preserving zero suspended/interrupted/connections and empty-task behavior.
- Add focused round-trip and byte-stability tests; do not commit.

## Current state

- Added `LambdaMooV17Codec.QueuedTask(long taskId, long scheduledEpochSecond, String programSource, Map<String, MooValue> initialLocals, long programmer, ObjectValue verbLocation, long taskPlayer)` and changed checkpoint task lists to that DTO.
- The codec now reads and writes nonzero queued forks using the pinned Toast order: queue header, integer `-111` activation sentinel, typed `this` and verb location, thread mode, compatibility line/strings, named runtime environment, standalone child source, and dot terminator. Suspended/interrupted/connections remain exactly zero; an empty task list retains the previous bytes.
- Added `QueuedTaskV17CodecTest` for the exact emitted one-task grammar, semantic DTO round trip, and write-read-write byte identity.
- Replaced the test's initial `String.join` expected fixture with a Java text block after Error Prone reported `StringConcatToTextBlock` under `-Werror`.
- Pinned source `/root/src/toaststunt` was verified at `aecc51e9449c6e7c95272f0f044b5ba38948459e`. The assigned DTO omits arbitrary activation first-line/thread/debug/verbname metadata, so this slice emits canonical values for Banteng-created delayed forks rather than claiming arbitrary external Toast activation byte preservation.
- `git diff --check` passes for the two owned source paths.

## Blocker

- The first focused Gradle run was blocked by an unrelated in-progress `PublicationScheduler` Error Prone warning, which the parent fixed.
- The rerun was blocked before codec compilation by a stale shared incremental-build artifact: missing `build/classes/java/main/moo/app/package-info.class`. The parent directed this subtask not to clean while another compiler run owns shared build outputs.
- Shared build-directory interference was confirmed. Root now exclusively owns every Gradle/build/clean command and declared this code subtask complete pending root verification.

## Next action

- Root runs the focused persistence verification and reports any codec/test failure back for a narrowly owned correction. Do not run Gradle or clean from this subtask.
