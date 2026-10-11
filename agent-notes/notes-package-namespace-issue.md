# Package namespace issue draft handoff

## Current findings

- Target repository: `MongooseMoo/banteng`; default branch is `master`.
- Current GitHub `master` is `c2374b2a60fc7068356368f98c871b1537852b4c`.
- Local `master` is `de14645ccece48b20f18c4ee88e2e6be00fc15b8`, ahead by the local-only commit `build: add OpenRewrite for CLI-driven refactoring` and behind GitHub `master` by eight commits.
- GitHub `master` has 106 tracked Java files beneath `src/*/java/moo` across main, test, jmh, and jcstress.
- No existing issue matches the package namespace rename. There is no issue template.
- Available label selected for the draft: `refactor`.
- Intended identity: Gradle/Maven group `world.mongoose`, artifact `banteng`, Java root package `world.mongoose.banteng`.

## Scope observations

- Structural rename must cover package declarations, imports, fully qualified types, and source paths in all four Java source sets.
- Non-structural references exist in `build.gradle.kts`, ArchUnit string rules, JFR event names/tests, WSL launch scripts, generated Java in `scripts/test_managed_runners_wsl.sh`, and active documentation/test commands.
- JFR names such as `moo.TaskSegment` are externally observable telemetry identifiers. The proposed issue draft renames them to `world.mongoose.banteng.*`; Q must approve this explicit contract change before publication.
- Unrelated MOO/Toast identities must remain unchanged, including `src/moo_conformance`, `pcre_moo.cc`, and Toast's `CMakeFiles/moo.dir`.
- No compatibility aliases, shims, duplicate packages, module descriptor, or unrelated refactor should be introduced.
- Historical report references need classification: update current/normative names and runnable commands; retain genuinely historical spelling only when it remains accurate and explicit.

## Tooling state

- The local-only commit adds OpenRewrite plugin 7.39.0, rewrite-java 8.89.0, and `scripts/rewrite.sh`.
- The wrapper accepts recipe names only. `ChangePackage` requires configured old/new/recursive options, so the implementation needs a named declarative recipe before use.
- Use OpenRewrite for the structural Java change, inspect a dry-run patch, then make curated build/script/documentation updates. Do not use a blind repository-wide replacement.

## Verification surface

- GitHub CI runs `./gradlew --no-daemon clean check`, `fuzzTest`, `schedulerStress`, `jcstress`, and `jmh`.
- The issue should also require the managed WSL runner/conformance gates appropriate to Banteng and a residual-reference audit with explicit exemptions.

## State and blocker

- No GitHub issue has been created.
- Q's guardrail prohibits publishing text under his name before he reads it.
- The exact title, body, and `refactor` label must be shown for approval first.

## Next action

- Present the exact issue draft to Q.
- After explicit approval, create the issue through the GitHub connector, then re-fetch it and report the exact number, URL, title, body, and label state.

