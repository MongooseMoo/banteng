package world.mongoose.banteng.builtin.family;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinRegistrar;

import static world.mongoose.banteng.builtin.BuiltinResult.mutationResult;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.syntax.MooParser;
import world.mongoose.banteng.syntax.MooUnparser;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import static world.mongoose.banteng.world.Access.resolveOwnershipPreamble;
import world.mongoose.banteng.world.Access;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.ObjectFlags;
import world.mongoose.banteng.world.Prepositions;
import world.mongoose.banteng.world.VerbPerms;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldResult;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

/** Owns builtin registrations and implementations for this family. */
public final class VerbBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> STRING = Set.of(ArgType.STRING);

    private VerbBuiltins() {
    }

    public static void registerRespondTo(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("respond_to", List.of(new CallShape(List.of(ANY, STRING), List.of(), Optional.empty()))).implementation(call -> respondTo(call.arguments(), call.world(), call.programmer()));
    }

    public static void registerDisassemble(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("disassemble", List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty()))).implementation(call -> disassemble(call.arguments(), call.world(), call.programmer()));
    }

    public static void registerDefinitionEdits(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("delete_verb", List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty()))).implementation(call -> deleteVerb(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("add_verb", List.of(
                new CallShape(
                        List.of(ANY, Set.of(ArgType.LIST), Set.of(ArgType.LIST)),
                        List.of(),
                        Optional.empty()))).implementation(call -> addVerb(call.arguments(), call.world(), call.programmer()));
    }

    public static void registerMetadataAndCode(List<BuiltinSpec> entries, BuiltinCatalog catalog) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("set_verb_code", List.of(
                new CallShape(
                        List.of(ANY, ANY, Set.of(ArgType.LIST)),
                        List.of(),
                        Optional.empty()))).implementation(call -> setVerbCode(catalog, call.arguments(), call.world(), call.programmer()));
        registrar.builtin("set_verb_args", List.of(
                new CallShape(
                        List.of(ANY, ANY, Set.of(ArgType.LIST)),
                        List.of(),
                        Optional.empty()))).implementation(call -> setVerbArgs(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("set_verb_info", List.of(
                new CallShape(
                        List.of(ANY, ANY, Set.of(ArgType.LIST)),
                        List.of(),
                        Optional.empty()))).implementation(call -> setVerbInfo(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("verbs", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call -> verbs(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("verb_args", List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty()))).implementation(call -> verbArgs(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("verb_code", List.of(new CallShape(List.of(ANY, ANY), List.of(ANY, ANY), Optional.empty()))).implementation(call -> verbCode(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("verb_info", List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty()))).implementation(call -> verbInfo(call.arguments(), call.world(), call.programmer()));
    }

    private static BuiltinResult respondTo(List<MooValue> arguments, WorldTxn world, long programmer) {
        MooValue target = arguments.getFirst();
        String name = ((StringValue) arguments.get(1)).text();
        WorldVerb verb;
        MooValue location;
        long owner;
        int flags;
        if (target instanceof ObjectValue object) {
            WorldObject body = world.object(object.value()).orElse(null);
            if (body == null) return BuiltinResult.error(ErrorValue.E_INVARG);
            verb = world.verb(object.value(), name).orElse(null);
            if (verb == null) return BuiltinResult.value(new IntegerValue(0));
            location = new ObjectValue(world.verbLocation(object.value(), name, true).orElseThrow());
            owner = body.owner();
            flags = body.flags();
        } else if (target instanceof AnonymousObjectValue anonymous) {
            WorldAnonymousObject body = world.anonymousObject(anonymous).orElse(null);
            if (body == null) return BuiltinResult.error(ErrorValue.E_INVARG);
            verb = world.verb(anonymous, name, true).orElse(null);
            if (verb == null) return BuiltinResult.value(new IntegerValue(0));
            location = world.verbLocation(anonymous, name, true).orElseThrow();
            owner = body.owner();
            flags = body.flags();
        } else {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        return owner == programmer || world.isWizard(programmer) || ObjectFlags.isReadable(flags)
                ? BuiltinResult.value(new ListValue(List.of(location, StringValue.of(verb.names()))))
                : BuiltinResult.value(new IntegerValue(1));
    }

    private static BuiltinResult disassemble(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        if (!(arguments.get(0) instanceof ObjectValue object)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        MooValue descriptor = arguments.get(1);
        if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        WorldObject target = world.object(object.value()).orElse(null);
        if (target == null) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }

        WorldVerb verb;
        if (descriptor instanceof IntegerValue integer) {
            long index = integer.value() - 1;
            if (index >= target.verbs().size()) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verb = target.verbs().get((int) index);
        } else {
            WorldVerb candidate =
                    world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null);
            if (candidate == null || !target.verbs().contains(candidate)) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verb = candidate;
        }

        if (!Access.canRead(world, programmer, verb.owner(), verb.perms())) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        List<MooValue> lines = new ArrayList<>();
        lines.add(StringValue.of("Main code vector:"));
        verb.program().disassemble().lines()
                .map(StringValue::of)
                .map(MooValue.class::cast)
                .forEach(lines::add);
        return BuiltinResult.value(new ListValue(lines));
    }

    private static BuiltinResult addVerb(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        ListValue info = (ListValue) arguments.get(1);
        if (info.size() != 3
                || !(info.elements().get(0) instanceof ObjectValue owner)
                || !(info.elements().get(1) instanceof StringValue permissionValue)
                || !(info.elements().get(2) instanceof StringValue namesValue)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        if (world.object(owner.value()).isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        int permissions = 0;
        String permissionText = permissionValue.text();
        for (int index = 0; index < permissionText.length(); index++) {
            permissions |=
                    switch (Character.toLowerCase(permissionText.charAt(index))) {
                        case 'r' -> 1;
                        case 'w' -> 2;
                        case 'x' -> 4;
                        case 'd' -> 8;
                        default -> -1;
                    };
            if (permissions < 0) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
        }
        String names = namesValue.text();
        int firstName = 0;
        while (firstName < names.length() && names.charAt(firstName) == ' ') {
            firstName++;
        }
        names = names.substring(firstName);
        if (names.isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }

        ListValue verbArguments = (ListValue) arguments.get(2);
        if (verbArguments.size() != 3
                || verbArguments.elements().stream().anyMatch(value -> !(value instanceof StringValue))) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        String directText = ((StringValue) verbArguments.elements().get(0)).text();
        String prepositionText = ((StringValue) verbArguments.elements().get(1)).text();
        String indirectText = ((StringValue) verbArguments.elements().get(2)).text();
        ArgSpec direct =
                switch (directText.toLowerCase(Locale.ROOT)) {
                    case "none" -> ArgSpec.NONE;
                    case "any" -> ArgSpec.ANY;
                    case "this" -> ArgSpec.THIS;
                    default -> null;
                };
        ArgSpec indirect =
                switch (indirectText.toLowerCase(Locale.ROOT)) {
                    case "none" -> ArgSpec.NONE;
                    case "any" -> ArgSpec.ANY;
                    case "this" -> ArgSpec.THIS;
                    default -> null;
                };
        int preposition = parsePreposition(prepositionText);
        if (direct == null || indirect == null || preposition == Integer.MIN_VALUE) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }

        MooValue receiver = arguments.get(0);
        Optional<ErrorValue> ownershipError =
                resolveOwnershipPreamble(receiver, owner.value(), world, programmer);
        if (ownershipError.isPresent()) {
            return BuiltinResult.error(ownershipError.orElseThrow());
        }

        int slot =
                receiver instanceof ObjectValue object
                        ? world.addVerb(
                        object.value(), names, owner.value(), new VerbPerms(permissions), direct, preposition, indirect)
                        : world.addVerb(
                        (AnonymousObjectValue) receiver,
                        names,
                        owner.value(),
                        new VerbPerms(permissions), direct, preposition, indirect);
        return BuiltinResult.value(
                new IntegerValue(slot));
    }

    private static BuiltinResult setVerbInfo(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        MooValue descriptor = arguments.get(1);
        if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }

        MooValue receiver = arguments.get(0);
        if (!(receiver instanceof ObjectValue object)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        WorldObject target = world.object(object.value()).orElse(null);
        if (target == null) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        List<WorldVerb> targetVerbs = target.verbs();

        ListValue info = (ListValue) arguments.get(2);
        if (info.size() != 3
                || !(info.elements().get(0) instanceof ObjectValue owner)
                || !(info.elements().get(1) instanceof StringValue permissionValue)
                || !(info.elements().get(2) instanceof StringValue namesValue)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        if (world.object(owner.value()).isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        int permissions = 0;
        String permissionText = permissionValue.text();
        for (int index = 0; index < permissionText.length(); index++) {
            permissions |=
                    switch (Character.toLowerCase(permissionText.charAt(index))) {
                        case 'r' -> 1;
                        case 'w' -> 2;
                        case 'x' -> 4;
                        case 'd' -> 8;
                        default -> -1;
                    };
            if (permissions < 0) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
        }
        String names = namesValue.text();
        int firstName = 0;
        while (firstName < names.length() && names.charAt(firstName) == ' ') {
            firstName++;
        }
        names = names.substring(firstName);
        if (names.isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }

        WorldVerb verb;
        int verbIndex;
        if (descriptor instanceof IntegerValue integer) {
            long index = integer.value() - 1;
            if (index >= targetVerbs.size()) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verbIndex = (int) index;
            verb = targetVerbs.get(verbIndex);
        } else {
            WorldVerb candidate =
                    world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null);
            if (candidate == null || !targetVerbs.contains(candidate)) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verb = candidate;
            verbIndex = targetVerbs.indexOf(verb);
        }

        boolean wizard = world.isWizard(programmer);
        if (!Access.canWrite(world, programmer, verb.owner(), verb.perms())
                || (!wizard && verb.owner() != owner.value())) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        WorldResult<Boolean> updated =
                world.setVerbInfo(object.value(), verbIndex, names, owner.value(), new VerbPerms(permissions));
        return mutationResult(updated, new IntegerValue(0));
    }

    private static BuiltinResult verbs(List<MooValue> arguments, WorldTxn world, long programmer) {
        MooValue receiver = arguments.getFirst();
        long owner;
        int flags;
        List<WorldVerb> verbs;
        if (receiver instanceof ObjectValue object) {
            WorldObject target = world.object(object.value()).orElse(null);
            if (target == null) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
            owner = target.owner();
            flags = target.flags();
            verbs = target.verbs();
        } else if (receiver instanceof AnonymousObjectValue anonymous) {
            WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
            if (target == null) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
            owner = target.owner();
            flags = target.flags();
            verbs = target.verbs();
        } else {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        if (owner != programmer && !world.isWizard(programmer) && !ObjectFlags.isReadable(flags)) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        return BuiltinResult.value(
                new ListValue(verbs.stream().map(WorldVerb::names).map(StringValue::of).toList()));
    }

    private static BuiltinResult verbInfo(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        MooValue receiver = arguments.get(0);
        if (!(receiver instanceof ObjectValue object)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        MooValue descriptor = arguments.get(1);
        if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        WorldObject target = world.object(object.value()).orElse(null);
        if (target == null) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }

        WorldVerb verb;
        if (descriptor instanceof IntegerValue integer) {
            long index = integer.value() - 1;
            if (index >= target.verbs().size()) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verb = target.verbs().get((int) index);
        } else {
            WorldVerb candidate =
                    world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null);
            if (candidate == null || !target.verbs().contains(candidate)) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verb = candidate;
        }

        if (!Access.canRead(world, programmer, verb.owner(), verb.perms())) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        StringBuilder flags = new StringBuilder(4);
        if (verb.perms().readable()) {
            flags.append('r');
        }
        if (verb.perms().writable()) {
            flags.append('w');
        }
        if (verb.perms().executable()) {
            flags.append('x');
        }
        if (verb.perms().debug()) {
            flags.append('d');
        }
        return BuiltinResult.value(
                new ListValue(
                        List.of(new ObjectValue(verb.owner()), StringValue.of(flags.toString()), StringValue.of(verb.names()))));
    }

    private static BuiltinResult verbArgs(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        MooValue receiver = arguments.get(0);
        if (!(receiver instanceof ObjectValue object)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        MooValue descriptor = arguments.get(1);
        if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        WorldObject target = world.object(object.value()).orElse(null);
        if (target == null) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }

        WorldVerb verb;
        if (descriptor instanceof IntegerValue integer) {
            long index = integer.value() - 1;
            if (index >= target.verbs().size()) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verb = target.verbs().get((int) index);
        } else {
            WorldVerb candidate =
                    world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null);
            if (candidate == null || !target.verbs().contains(candidate)) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verb = candidate;
        }

        if (!Access.canRead(world, programmer, verb.owner(), verb.perms())) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        String direct =
                switch (verb.dobj()) {
                    case NONE -> "none";
                    case ANY -> "any";
                    case THIS -> "this";
                    default -> throw new IllegalStateException("invalid direct-object verb specification");
                };
        String indirect =
                switch (verb.iobj()) {
                    case NONE -> "none";
                    case ANY -> "any";
                    case THIS -> "this";
                    default -> throw new IllegalStateException("invalid indirect-object verb specification");
                };
        String preposition = Prepositions.format(verb.preposition());
        return BuiltinResult.value(
                new ListValue(List.of(StringValue.of(direct), StringValue.of(preposition), StringValue.of(indirect))));
    }

    private static BuiltinResult deleteVerb(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        MooValue descriptor = arguments.get(1);
        if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        MooValue receiver = arguments.get(0);
        if (!(receiver instanceof ObjectValue object)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        WorldObject target = world.object(object.value()).orElse(null);
        if (target == null) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }

        boolean wizard = world.isWizard(programmer);
        if (target.owner() != programmer && !wizard && !ObjectFlags.isWritable(target.flags())) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }

        int verbIndex;
        if (descriptor instanceof IntegerValue integer) {
            long index = integer.value() - 1;
            if (index >= target.verbs().size()) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verbIndex = (int) index;
        } else {
            WorldVerb candidate =
                    world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null);
            if (candidate == null || !target.verbs().contains(candidate)) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verbIndex = target.verbs().indexOf(candidate);
        }
        return mutationResult(world.deleteVerb(object.value(), verbIndex), new IntegerValue(0));
    }

    private static BuiltinResult setVerbArgs(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        MooValue descriptor = arguments.get(1);
        if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }

        MooValue receiver = arguments.get(0);
        if (!(receiver instanceof ObjectValue object)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        WorldObject target = world.object(object.value()).orElse(null);
        if (target == null) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }

        ListValue verbArguments = (ListValue) arguments.get(2);
        if (verbArguments.size() != 3
                || verbArguments.elements().stream().anyMatch(value -> !(value instanceof StringValue))) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        String directText = ((StringValue) verbArguments.elements().get(0)).text();
        String prepositionText = ((StringValue) verbArguments.elements().get(1)).text();
        String indirectText = ((StringValue) verbArguments.elements().get(2)).text();
        ArgSpec direct =
                switch (directText.toLowerCase(Locale.ROOT)) {
                    case "none" -> ArgSpec.NONE;
                    case "any" -> ArgSpec.ANY;
                    case "this" -> ArgSpec.THIS;
                    default -> null;
                };
        ArgSpec indirect =
                switch (indirectText.toLowerCase(Locale.ROOT)) {
                    case "none" -> ArgSpec.NONE;
                    case "any" -> ArgSpec.ANY;
                    case "this" -> ArgSpec.THIS;
                    default -> null;
                };
        int preposition = parsePreposition(prepositionText);
        if (direct == null || indirect == null || preposition == Integer.MIN_VALUE) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }

        List<WorldVerb> targetVerbs = target.verbs();
        WorldVerb verb;
        int verbIndex;
        if (descriptor instanceof IntegerValue integer) {
            long index = integer.value() - 1;
            if (index >= targetVerbs.size()) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verbIndex = (int) index;
            verb = targetVerbs.get(verbIndex);
        } else {
            WorldVerb candidate =
                    world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null);
            if (candidate == null || !targetVerbs.contains(candidate)) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verb = candidate;
            verbIndex = targetVerbs.indexOf(verb);
        }

        if (!Access.canWrite(world, programmer, verb.owner(), verb.perms())) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        WorldResult<Boolean> updated =
                world.setVerbArgs(object.value(), verbIndex, direct, preposition, indirect);
        return mutationResult(updated, new IntegerValue(0));
    }

    private static int parsePreposition(String prepositionText) {
        return Prepositions.parse(prepositionText);
    }

    private static BuiltinResult setVerbCode(
            BuiltinCatalog catalog, List<MooValue> arguments, WorldTxn world, long programmer) {
        ListValue code = (ListValue) arguments.get(2);
        if (code.elements().stream().anyMatch(value -> !(value instanceof StringValue))) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        MooValue receiver = arguments.get(0);
        if (!(receiver instanceof ObjectValue) && !(receiver instanceof AnonymousObjectValue)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        MooValue descriptor = arguments.get(1);
        if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        List<WorldVerb> targetVerbs;
        if (receiver instanceof ObjectValue object) {
            WorldObject target = world.object(object.value()).orElse(null);
            if (target == null) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
            targetVerbs = target.verbs();
        } else if (receiver instanceof AnonymousObjectValue anonymous) {
            WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
            if (target == null) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
            targetVerbs = target.verbs();
        } else {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        WorldVerb verb;
        int verbIndex;
        if (descriptor instanceof IntegerValue integer) {
            long index = integer.value() - 1;
            if (index >= targetVerbs.size()) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verbIndex = (int) index;
            verb = targetVerbs.get(verbIndex);
        } else {
            WorldVerb candidate =
                    receiver instanceof ObjectValue object
                            ? world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null)
                            : world
                            .verb(
                                    (AnonymousObjectValue) receiver,
                                    ((StringValue) descriptor).text(),
                                    false)
                            .orElse(null);
            if (candidate == null || !targetVerbs.contains(candidate)) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verb = candidate;
            verbIndex = targetVerbs.indexOf(verb);
        }

        boolean wizard = world.isWizard(programmer);
        WorldObject actor = world.object(programmer).orElse(null);
        boolean programmerFlag = actor != null && ObjectFlags.isProgrammer(actor.flags());
        if ((!programmerFlag && !wizard)
                || !Access.canWrite(world, programmer, verb.owner(), verb.perms())) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }

        String suppliedSource =
                code.elements().stream()
                        .map(StringValue.class::cast)
                        .map(StringValue::text)
                        .collect(java.util.stream.Collectors.joining("\n"));
        MooCompiler.CompilationResult compiled =
                new MooCompiler().compileResult(suppliedSource, name -> catalog.spec(name).isPresent());
        if (!compiled.diagnostics().isEmpty()) {
            return BuiltinResult.value(new ListValue(compiled.diagnostics().stream()
                    .map(diagnostic -> (MooValue) StringValue.of(diagnostic.display())).toList()));
        }
        String canonicalSource =
                Arrays.stream(compiled.program().orElseThrow().source().split("\n", -1))
                        .map(String::stripLeading)
                        .collect(java.util.stream.Collectors.joining("\n"));
        WorldResult<Boolean> updated =
                receiver instanceof ObjectValue object
                        ? world.setVerbCode(object.value(), verbIndex, canonicalSource)
                        : world.setVerbCode((AnonymousObjectValue) receiver, verbIndex, canonicalSource);
        return mutationResult(updated, new ListValue(List.of()));
    }

    private static BuiltinResult verbCode(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        if (!(arguments.get(0) instanceof ObjectValue object)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        MooValue descriptor = arguments.get(1);
        if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        WorldObject target = world.object(object.value()).orElse(null);
        if (target == null) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }

        WorldVerb verb;
        if (descriptor instanceof IntegerValue integer) {
            long index = integer.value() - 1;
            if (index >= target.verbs().size()) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verb = target.verbs().get((int) index);
        } else {
            WorldVerb candidate =
                    world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null);
            if (candidate == null || !target.verbs().contains(candidate)) {
                return BuiltinResult.error(ErrorValue.E_VERBNF);
            }
            verb = candidate;
        }

        if (!Access.canRead(world, programmer, verb.owner(), verb.perms())) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }

        boolean indent = arguments.size() < 4 || arguments.get(3).isTruthy();
        if (verb.programSource().isEmpty()) {
            return BuiltinResult.value(new ListValue(List.of()));
        }
        List<MooValue> lines =
                Arrays.stream(MooUnparser.unparse(MooParser.parse(verb.programSource()),
                        arguments.size() >= 3 && arguments.get(2).isTruthy()).split("\n", -1))
                        .map(line -> indent ? line : line.stripLeading())
                        .map(StringValue::of)
                        .map(MooValue.class::cast)
                        .toList();
        return BuiltinResult.value(new ListValue(lines));
    }
}
