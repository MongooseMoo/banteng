package world.mongoose.banteng.builtin.family;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinRegistrar;

import static world.mongoose.banteng.builtin.BuiltinResult.mutationResult;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import static world.mongoose.banteng.world.Access.resolveOwnershipPreamble;
import world.mongoose.banteng.world.Access;
import world.mongoose.banteng.world.ObjectFlags;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldResult;
import world.mongoose.banteng.world.WorldTxn;

/** Owns builtin registrations and implementations for this family. */
public final class PropertyBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> STRING = Set.of(ArgType.STRING);

    private PropertyBuiltins() {
    }

    public static void register(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("add_property", List.of(
                new CallShape(
                        List.of(ANY, STRING, ANY, Set.of(ArgType.LIST)),
                        List.of(),
                        Optional.empty()))).implementation(call -> addProperty(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("properties", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call -> properties(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("property_info", List.of(new CallShape(List.of(ANY, STRING), List.of(), Optional.empty()))).implementation(call -> propertyInfo(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("is_clear_property", List.of(new CallShape(List.of(ANY, STRING), List.of(), Optional.empty()))).implementation(call -> isClearProperty(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("clear_property", List.of(new CallShape(List.of(ANY, STRING), List.of(), Optional.empty()))).implementation(call -> clearProperty(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("delete_property", List.of(new CallShape(List.of(ANY, STRING), List.of(), Optional.empty()))).implementation(call -> deleteProperty(call.arguments(), call.world(), call.programmer()));
    }

    private static BuiltinResult addProperty(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        ListValue info = (ListValue) arguments.get(3);
        if (info.size() != 2
                || !(info.elements().get(0) instanceof ObjectValue owner)
                || !(info.elements().get(1) instanceof StringValue permissionValue)) {
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
                        case 'c' -> 4;
                        default -> -1;
                    };
            if (permissions < 0) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
        }
        MooValue receiver = arguments.get(0);
        Optional<ErrorValue> ownershipError =
                resolveOwnershipPreamble(receiver, owner.value(), world, programmer);
        if (ownershipError.isPresent()) {
            return BuiltinResult.error(ownershipError.orElseThrow());
        }
        String name = ((StringValue) arguments.get(1)).text();
        WorldResult<Boolean> added =
                receiver instanceof ObjectValue object
                        ? world.addProperty(
                        object.value(), name, arguments.get(2), owner.value(), new PropertyPerms(permissions))
                        : world.addProperty(
                        (AnonymousObjectValue) receiver,
                        name,
                        arguments.get(2),
                        owner.value(),
                        new PropertyPerms(permissions));
        return mutationResult(added, new IntegerValue(0));
    }

    private static BuiltinResult properties(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        if (arguments.getFirst() instanceof WaifValue) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        if (!(arguments.getFirst() instanceof ObjectValue object)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        WorldObject target = world.object(object.value()).orElse(null);
        if (target == null) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        boolean wizard = world.isWizard(programmer);
        if (target.owner() != programmer && !wizard && !ObjectFlags.isReadable(target.flags())) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        List<MooValue> names =
                target.properties().stream()
                        .filter(WorldProperty::defined)
                        .map(WorldProperty::name)
                        .map(StringValue::of)
                        .map(MooValue.class::cast)
                        .toList();
        return BuiltinResult.value(new ListValue(names));
    }

    private static BuiltinResult isClearProperty(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        MooValue receiver = arguments.getFirst();
        if (!(receiver instanceof ObjectValue || receiver instanceof AnonymousObjectValue)) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        boolean invalid = switch (receiver) {
            case ObjectValue object -> world.object(object.value()).isEmpty();
            case AnonymousObjectValue anonymous -> world.anonymousObject(anonymous).isEmpty();
            default -> true;
        };
        if (invalid) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        String name = ((StringValue) arguments.get(1)).text();
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.equals("name")
                || normalized.equals("location")
                || normalized.equals("contents")
                || normalized.equals("owner")
                || normalized.equals("programmer")
                || normalized.equals("wizard")
                || normalized.equals("r")
                || normalized.equals("w")
                || normalized.equals("f")) {
            return BuiltinResult.value(new IntegerValue(0));
        }
        WorldProperty property = receiver instanceof ObjectValue object
                ? world.property(object.value(), name).orElse(null)
                : world.property((AnonymousObjectValue) receiver, name).orElse(null);
        if (property == null) {
            return BuiltinResult.error(ErrorValue.E_PROPNF);
        }
        if (!Access.canRead(world, programmer, property.owner(), property.perms())) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        return BuiltinResult.value(new IntegerValue(property.clear() ? 1 : 0));
    }

    private static BuiltinResult propertyInfo(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        if (arguments.getFirst() instanceof WaifValue) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        MooValue receiver = arguments.getFirst();
        if (!(receiver instanceof ObjectValue || receiver instanceof AnonymousObjectValue)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        boolean invalid = switch (receiver) {
            case ObjectValue object -> world.object(object.value()).isEmpty();
            case AnonymousObjectValue anonymous -> world.anonymousObject(anonymous).isEmpty();
            default -> true;
        };
        if (invalid) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        String name = ((StringValue) arguments.get(1)).text();
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.equals("name")
                || normalized.equals("location")
                || normalized.equals("contents")
                || normalized.equals("owner")
                || normalized.equals("programmer")
                || normalized.equals("wizard")
                || normalized.equals("r")
                || normalized.equals("w")
                || normalized.equals("f")) {
            return BuiltinResult.error(ErrorValue.E_PROPNF);
        }
        WorldProperty property = receiver instanceof ObjectValue object
                ? world.property(object.value(), name).orElse(null)
                : world.property((AnonymousObjectValue) receiver, name).orElse(null);
        if (property == null) {
            return BuiltinResult.error(ErrorValue.E_PROPNF);
        }
        if (!Access.canRead(world, programmer, property.owner(), property.perms())) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        String permissions = "";
        if (property.perms().readable()) {
            permissions += "r";
        }
        if (property.perms().writable()) {
            permissions += "w";
        }
        if (property.perms().chownable()) {
            permissions += "c";
        }
        return BuiltinResult.value(
                new ListValue(List.of(new ObjectValue(property.owner()), StringValue.of(permissions))));
    }

    private static BuiltinResult clearProperty(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        if (!(arguments.getFirst() instanceof ObjectValue object)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        WorldObject target = world.object(object.value()).orElse(null);
        if (target == null) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        String name = ((StringValue) arguments.get(1)).text();
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.equals("name")
                || normalized.equals("location")
                || normalized.equals("contents")
                || normalized.equals("owner")
                || normalized.equals("programmer")
                || normalized.equals("wizard")
                || normalized.equals("r")
                || normalized.equals("w")
                || normalized.equals("f")) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        WorldProperty property = world.property(object.value(), name).orElse(null);
        if (property == null) {
            return BuiltinResult.error(ErrorValue.E_PROPNF);
        }
        if (!Access.canWrite(world, programmer, property.owner(), property.perms())) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        WorldProperty local =
                target.properties().stream()
                        .filter(candidate -> candidate.name().equalsIgnoreCase(name))
                        .findFirst()
                        .orElse(null);
        if (local == null || local.defined()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        return mutationResult(world.clearProperty(object.value(), name), new IntegerValue(0));
    }

    private static BuiltinResult deleteProperty(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        if (!(arguments.getFirst() instanceof ObjectValue object)) {
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
        String name = ((StringValue) arguments.get(1)).text();
        boolean defined =
                target.properties().stream()
                        .anyMatch(property -> property.defined() && property.name().equalsIgnoreCase(name));
        if (!defined) {
            return BuiltinResult.error(ErrorValue.E_PROPNF);
        }
        return mutationResult(world.deleteProperty(object.value(), name), new IntegerValue(0));
    }
}
