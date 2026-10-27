# Goal
Extract simplex_noise for issue 10 using the existing family mover, keeping its numeric implementation unchanged.

# State
Published as PR134. Prepared the normal merge of latest committed campaign/callback-command-locals. The catalog merged mechanically without conflicts. NoiseBuiltins, relocated SimplexNoise, the family mover, authored recipe fixture and rewrite.yml are unchanged from the existing Noise head. Incoming runtime/VM/persistence/bytecode/app, BuiltinHosts/BuiltinResult and runtime tests are preserved unchanged. No actual Noise implementation/mover delta, local CI rerun or repeat review.

Implemented in an isolated Noise tree from origin/master. NoiseBuiltins owns the registration and private wrapper; package-private SimplexNoise is relocated with only its package declaration changed. No publication by this agent.

# Decisions and why
Smallest change: move the one registration and fifteen-line private wrapper into NoiseBuiltins, then use the existing ChangeType recipe to relocate the package-private SimplexNoise beside it. Existing generic mover already handles this closure; no mover implementation extension is needed. Preserve float-coordinate validation, returned dimension error value, algorithms and constants. About twenty scaffold lines and the existing 200-line implementation relocate; no new numeric behavior or public helper.

# Surprises
The shared Gradle daemon can retain recipe classes at unchanged classpath URLs. The Task worker owns the runner-script correction; this task invokes the same normal migration task with --no-daemon directly, without editing that script.
The first direct invocation left the dotted -D argument unquoted through PowerShell, which lost its prefix and caused Gradle task selection to fail. Quoted the argument and the normal migration completed. It emitted the existing configuration-cache and subproject classpath-lock warnings. Reviewed and applied its three-file output through apply_patch.

# Validation
`wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin ./gradlew --no-daemon :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest` passed with the authored Noise fixture covering registration position, private wrapper and helper ownership, numeric fixture preservation and idempotence.
`wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin ./gradlew --no-daemon rewriteDryRun --warning-mode summary '-Drewrite.activeRecipe=world.mongoose.banteng.builtin.ExtractNoiseBuiltins'` completed successfully with normal compilation prerequisites and source frozen.
Compared the relocated SimplexNoise against the original: only its package declaration differs. `git diff --check` passed. Application CI/conformance remains for the published PR; no broad local CI ran.

# Draft PR body
Moves simplex_noise registration and its private wrapper into NoiseBuiltins at the existing manifest position. Relocates the package-private SimplexNoise implementation beside it with only the package declaration changed. Float-coordinate validation and the dimension error value retain their existing behavior.

Uses the existing family mover and ChangeType recipe without new migration machinery. Adds focused coverage for manifest position, private ownership, numeric fixture preservation and idempotence. Validation: authored MoveBuiltinFamilyTest and normal migration dry run; application CI/conformance remains for this PR.

# Next step
Parent owns publication and required PR CI/conformance after runtime lands. Preserve existing review coverage because Noise extraction/mover code is unchanged; this ancestry integration adds no code to re-review. No publication, release or deployment performed by this worker.

## Login and layout repair ancestry
Merged the latest coordinator runtime login-output and pinned-layout-boundary repairs normally, without conflicts or family source adaptations. Family implementations, services, dependencies, extraction mover, authored fixtures and YAML are unchanged. No local tests, CI, migration or repeated review ran for this mechanical integration. Parent owns publication, hosted checks and merge.

## Independent Claude Noise review (limited coverage)
Claude reviewed only the Noise-specific delta on top of the runtime dependency head: task note, authored mover fixture, rewrite.yml entry, catalog insertion/removal and imports, NoiseBuiltins, and the full relocated SimplexNoise against its original committed source. Result: no findings in two passes. The runtime dependency was not re-reviewed; its coverage stays in the PR138 review ledger. This is not a full current-PR gate or merge approval. The report and ledger were returned to the parent, which saves them. Next: the parent runs the final full-PR gate after runtime lands.

## Queue prerequisite integration
Normally merged the published Task tip and its reviewed prerequisite families. YAML resolution retains the entire Noise recipe, including SimplexNoise relocation, alongside all incoming recipe documents. Reused fixture/catalog resolutions retain the original Noise fixture and registration position before string search, matching final assembly. NoiseBuiltins, SimplexNoise and incoming family owners remain byte-identical. Preserved the pre-existing limited Claude coverage note in this substantive merge. No local tests, repeated review or publication; parent owns hosted validation and merge.
