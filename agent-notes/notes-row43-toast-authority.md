# Row 43 Toast authority checkpoint

- Pinned checkout identity remains `aecc51e9449c6e7c95272f0f044b5ba38948459e`.
- Adversarial question: whether `/root/src/toaststunt/build-release/moo` compiled the `ITIMER_VIRTUAL` CPU-time branch or the wall-clock fallback.
- Actual generated `build-release/config.h:65` says `/* #undef IS_WSL */`.
- Actual `build-release/CMakeFiles/moo.dir/flags.make:12-16` has no `CXX_DEFINES`; include order is source headers, dependencies, then `/root/src/toaststunt/build-release`, so `timers.cc` includes that generated `config.h` and receives no command-line `IS_WSL` override.
- `build-release/CMakeFiles/3.25.1/CMakeSystem.cmake:9-11` records system name `Linux` and version `6.6.114.1-microsoft-standard-WSL2`.
- The CMake source tests `string(FIND ${CMAKE_SYSTEM} "Microsoft" IS_WSL)`. The generated outcome proves this test did not find the mixed-case string it searched for; likely because the actual `CMAKE_SYSTEM` representation contains lowercase `microsoft` in the version. CMake `string(FIND)` is case-sensitive.
- The existing timer object and executable are present ELF artifacts, with `timers.cc.o` unstripped and `moo` unstripped. Remaining work: inspect relocations/disassembly/symbol calls to prove whether the object references `setitimer/getitimer/SIGVTALRM` (virtual branch) or only `alarm/time` (fallback branch).
- No tracked edits, process interaction, or PID 19/port 9482 access occurred.

## Compiled-artifact confirmation

- `nm -C -u build-release/CMakeFiles/moo.dir/src/timers.cc.o` shows undefined imports `getitimer` and `setitimer`; `nm -C` shows the compiled local `virtual_wakeup_call(int)` symbol.
- `nm -D build-release/moo` shows dynamic imports `getitimer@GLIBC_2.2.5` and `setitimer@GLIBC_2.2.5`.
- Disassembly of compiled `set_virtual_timer(...)` calls `setitimer` with immediate first argument `1`, Linux `ITIMER_VIRTUAL`, and installs signal number `0x1a`, `SIGVTALRM`.
- Disassembly of compiled `timer_wakeup_interval(int)` first checks the virtual-timer slot and, when the id matches, calls `getitimer` with immediate first argument `1`, then returns the `tv_sec` field. Its wall-clock `time()` scan remains only as the general-timer fallback for non-virtual timer IDs.
- Decisive result: the pinned `build-release/moo` executable uses the virtual CPU-time branch for task execution limits. The contrary wall-clock claim inferred intended WSL detection from CMake source, but the case-sensitive test did not match lowercase `microsoft` in the actual `CMAKE_SYSTEM` version, so `IS_WSL` was not defined in this build.
