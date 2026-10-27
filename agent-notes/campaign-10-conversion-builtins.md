# Conversion builtin extraction

## Goal
Move the seven conversion registrations and their ten-method closure through the existing OpenRewrite mover, preserving the two manifest groups around eval.

## State
Published as PR124 with completed Claude review and green initial PR CI. Integrated the independently reviewed Random prerequisite to resolve the shared recipe/YAML conflict, retaining both implementations and their completed reviews. Fresh PR CI checks the integrated result; normal queue merge follows.

## Decisions and why
The smallest change moves the existing algorithms unchanged into ConversionBuiltins. Its toStringValue alone is public because raise uses it to construct a default message; raise calls the family directly. Equality retains the invocation host's ValueSemantics dependency. Expected size is about 180 implementation lines and 57 registration lines moved, 35–50 scaffold lines, and 15–25 mover/configuration lines.

## Surprises
The existing OpenRewrite runner emitted Gradle configuration-cache/classpath resolution warnings for subprojects but completed successfully and generated only the family and catalog migration. No broad local CI steps were run.

## Next step
Parent verifies the resulting source and completes review/publication/CI. Keep Conversion as a separate family commit and PR when integrating concurrent Random work.

## Draft PR body
Moves tostr, tofloat, toint, toliteral, toobj, equal and typeof registrations and their existing implementations to ConversionBuiltins. Keeps eval between the registration groups and preserves the host ValueSemantics dependency. raise directly uses the moved toStringValue implementation for its default message.

Adds the Conversion configuration to the existing OpenRewrite mover and focused recipe coverage for registration order, the remaining helper caller, visibility and idempotency.

Validation: WSL JDK 25 scripts/rewrite.sh world.mongoose.banteng.builtin.ExtractConversionBuiltins; :rewrite-recipes:test --tests world.mongoose.banteng.rewrite.MoveBuiltinFamilyTest. Full conformance and required checks belong to PR CI.
