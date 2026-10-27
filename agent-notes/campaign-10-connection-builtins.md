# Connection builtin extraction

## Goal
Move the twenty connection registrations in four existing manifest groups, fifteen-method closure and seven existing nested contracts into ConnectionBuiltins through the existing mover. Move the standalone empty registry directly into its sole consumer BuiltinHosts.

## State
The mover/configuration, generated source migration and authored fixture are complete. The normal migration completed with explicit WSL JDK 25 and --no-daemon; reviewed output was applied through apply_patch. The focused MoveBuiltinFamilyTest file passed. Runtime integration now preserves the callback/switch repair bodies and metadata described below. No publication is authorized for this worker.

## Decisions and why
The smallest change relocates roughly 600–650 family lines and eighty default-registry lines. Keep the catalog's existing Optional<ListenerControl>, pass it directly to the static implementations that use it, and continue taking hosts and staged output projection from each invocation. Keep request records, enum constants, listener lifecycle methods, option validation, permissions and flags unchanged; downstream code only changes type references.

Read current BuiltinCatalog connection methods, all seven contracts and standalone defaults; BuiltinHosts builder and ConnectionRegistryAccess; MooRuntime host composition and shutdown/panic effects; MooServer buffered output/shutdown/panic and transport cleanup; MooVm result staging; ConnectionIo option/notification/input consumers; EffectOutbox staging/projection; VmSnapshot request storage/sizing; FinalizationCollector request value reachability. These consumers carry or consume the existing contracts directly.

## Surprises
Task extraction established that the OpenRewrite plugin retains recipe classes at unchanged jar URLs in a long-lived Gradle daemon. Use --no-daemon on this migration invocation; the shared runner correction remains owned by the Task commit and is not duplicated here.

The first generated patch also renamed BuiltinCatalog to ConnectionBuiltins during nested type retargeting. Nothing from that patch was applied. Installed ChangeType JavaChangeTypeVisitor bytecode shows ignoreDefinition=true skips ChangeClassDefinition while still updating references. Set that existing option on the seven reference migrations; the family mover already owns moving the declarations.

Review of generated output caught substring matching of listen inside unlisten, which duplicated its listener argument. The existing replacement now uses a word boundary. BuiltinHosts is reparsed while moving its default enum, so its known ConnectionOption import is retargeted directly in that transformation. The authored fixture exercises both cases. Its first execution could not find the catalog result by filename; making the fixture outer classes public and its supplier type explicit to match production resolved the lookup, with no production source change.

## Next step
Complete the normal merge of the latest independently advanced runtime ancestry, preserving only Connection contract type retargets in its new files. Parent owns independent source verification, publication, Claude review and PR CI. Full compilation/conformance of the migrated production source has not run locally. The initial Claude review is still pending quota; none was launched for this integration.

## Draft PR body
Moves twenty connection builtin registrations and their existing implementations into ConnectionBuiltins. Preserves the existing optional listener transport and invocation hosts, including staged buffered output projection. Relocates the seven transport/request contracts directly and updates their consumers; BuiltinHosts owns its standalone empty registry.

Validation: WSL JDK 25 ./gradlew --no-daemon rewriteDryRun --warning-mode summary -Drewrite.activeRecipe=world.mongoose.banteng.builtin.ExtractConnectionBuiltins completed with normal prerequisites and existing plugin configuration-cache/classpath warnings. :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest passed after source edits finished. git diff --check passed. Compared all fifteen implementation bodies and seven contract bodies against their original source ignoring whitespace; all unchanged. Exactly twenty registrations moved; no old nested contract references remain in src. No broad local CI steps ran. Full conformance and required checks belong to PR CI.

## Runtime integration

Merged the coordinator's callback-command-locals branch at its activation-fixture adaptation tip. There were two conflict hunks, both in BuiltinCatalog: the switch registration and implementation. Kept the existing family registration group and moved the runtime's current BuiltinCall validation/delegation body into ConnectionBuiltins, changing only static ownership and its method reference. It delegates to call.hosts().switchPlayer().invoke(call), preserving the per-attempt ConnectionIo owner. No SwitchPlayer result or switchedPlayer snapshot channel remains.

BuiltinHosts keeps its current switchPlayer record component, validation, builder field/setter and build argument alongside this extraction's EmptyConnectionRegistry relocation. Current saved receiver/player, lag/outcome, continuation, snapshot sizing, durable metadata and tests remain intact. Compared the eight runtime/VM/server files differing from the captured runtime parent after removing imports and normalizing only the seven relocated contract qualifiers: no other body differences. Compared the moved switch implementation to that captured current catalog body ignoring whitespace/static ownership: equal. Updated the newly introduced SwitchPlayerCallbacksTest transport import; its body is unchanged.

The existing mover now retargets this::switchPlayer to ConnectionBuiltins::switchPlayer. Its existing authored fixture uses the current BuiltinCall producer/delegation, checks the family method reference and ensures the BuiltinHosts handler survives default-registry relocation. Existing seven ChangeType entries retain ignoreDefinition=true; no new alias or recipe was added.

Focused integration validation passed after source freeze: `wsl env JAVA_HOME=/opt/java/25 PATH=/opt/java/25/bin:/usr/bin:/bin ./gradlew --no-daemon :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest`. No migration rerun, root test suite, conformance run or Claude review was performed. The runtime branch advanced with WAIF assignment while this focused run completed; adopt that ancestry through a subsequent normal merge, preserving the new runtime bodies and retargeting only the relocated contracts.

Draft PR body amendment: preserves the integrated runtime callback repairs, including the current switch_player host delegation and saved activation metadata, while moving only the existing Connection family and its seven contracts. Validation remains the authored mover fixture and the original normal migration; production CI remains PR-owned.
