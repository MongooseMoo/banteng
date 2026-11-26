# ToastStunt startup performance

## Goal

Reduce the time from Java process launch to a usable greeting on a
ToastStunt-format checkpoint.

## State

The startup work includes a buffered Latin-1 database reader, direct numeric
and topology decoding, bulk map construction, indexed property restoration,
concurrent compilation of stored verbs, and less repeated compiler work.
The runtime shares immutable world records and reusable reachability data,
indexes property lookup, and bounds the number of due timers admitted ahead
of input. Startup notifications keep their publication order and a suspended
startup hook releases the listener after its first published slice.

## Decisions and why

Measure an actual application greeting. The listener binds before startup
callbacks and can accept a TCP handshake before the application can read
input. Keep malformed-checkpoint validation and exact stored source. Keep raw
profiles and copied database files outside Git.

## Surprises

The baseline on the 248 MiB Mongoose checkpoint missed a 150-second greeting
deadline. The development build's median was 19.90 seconds over three fresh
JVM launches. Native ToastStunt's median was 8.52 seconds on the same input.
A bounded short-string cache slowed two of three paired runs and was removed.
The final development profile still placed 68% of main-thread samples in
database object and value decoding.

The PR build reached the greeting in 14.892 seconds on the same checkpoint.
Compiler and VM disassembly fixtures now reflect the omitted empty-list
instruction for nonempty lists and argument vectors. VM result assertions
remain in place.

## Next step

Continue profiling database decoding to reach the ten-second Mongoose
target.
