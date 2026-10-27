package world.mongoose.banteng.builtin.family;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.ValueSemantics;

/** Owns builtin registrations and implementations for this family. */
public final class MapBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
    private static final Set<ArgType> NUMBER = Set.of(ArgType.NUMBER);

    private MapBuiltins() {
    }

    public static void register(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("mapdelete", List.of(new CallShape(List.of(Set.of(ArgType.MAP), ANY), List.of(), Optional.empty()))).implementation(call -> mapDelete(call.arguments()));
        registrar.builtin("mapkeys", List.of(
                new CallShape(
                        List.of(Set.of(ArgType.MAP)), List.of(), Optional.empty()))).implementation(call -> mapKeys(call.arguments()));
        registrar.builtin("mapvalues", List.of(
                new CallShape(
                        List.of(Set.of(ArgType.MAP)), List.of(), Optional.of(ANY)))).implementation(call -> mapValues(call.arguments()));
        registrar.builtin("maphaskey", List.of(
                new CallShape(
                        List.of(Set.of(ArgType.MAP), ANY), List.of(INTEGER), Optional.empty()))).implementation(call -> mapHasKey(call.arguments()));
    }

    private static BuiltinResult mapKeys(List<MooValue> arguments) {
        MapValue map = (MapValue) arguments.getFirst();
        return BuiltinResult.value(new ListValue(map.orderedEntries().stream().map(Map.Entry::getKey).toList()));
    }

    private static BuiltinResult mapValues(List<MooValue> arguments) {
        MapValue map = (MapValue) arguments.getFirst();
        List<MooValue> values = new ArrayList<>();
        if (arguments.size() == 1) {
            for (Map.Entry<MooValue, MooValue> entry : map.orderedEntries()) {
                values.add(entry.getValue());
            }
            return BuiltinResult.value(new ListValue(values));
        }
        for (int index = 1; index < arguments.size(); index++) {
            final Optional<MooValue> value;
            try {
                value = map.get(arguments.get(index), true);
            } catch (IllegalArgumentException invalidKey) {
                return BuiltinResult.error(ErrorValue.E_RANGE);
            }
            if (value.isEmpty()) {
                return BuiltinResult.error(ErrorValue.E_RANGE);
            }
            values.add(value.orElseThrow());
        }
        return BuiltinResult.value(new ListValue(values));
    }

    private static BuiltinResult mapHasKey(List<MooValue> arguments) {
        MooValue key = arguments.get(1);
        if (invalidMapBuiltinKey(key)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        MapValue map = (MapValue) arguments.getFirst();
        boolean caseMatters = arguments.size() == 3 && arguments.get(2).isTruthy();
        return BuiltinResult.value(new IntegerValue(map.get(key, caseMatters).isPresent() ? 1 : 0));
    }

    private static BuiltinResult mapDelete(List<MooValue> arguments) {
        MapValue original = (MapValue) arguments.getFirst();
        MooValue requested = arguments.get(1);
        boolean multiple = requested instanceof ListValue;
        List<MooValue> keys =
                multiple ? ((ListValue) requested).elements() : List.of(requested);
        MapValue result = original;
        for (MooValue key : keys) {
            if (invalidMapBuiltinKey(key)) {
                return BuiltinResult.error(ErrorValue.E_TYPE);
            }
            Optional<MapValue> deleted = deleteMapKey(result, key);
            if (deleted.isEmpty()) {
                return multiple
                        ? BuiltinResult.raised(
                        ErrorValue.E_RANGE,
                        StringValue.of("Key " + key.toLiteral() + " not found in map"),
                        key)
                        : BuiltinResult.error(ErrorValue.E_RANGE);
            }
            result = deleted.orElseThrow();
        }
        return BuiltinResult.value(result);
    }

    private static Optional<MapValue> deleteMapKey(MapValue map, MooValue key) {
        Map<MooValue, MooValue> remaining = new LinkedHashMap<>();
        boolean found = false;
        for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
            if (!found && ValueSemantics.STANDARD.compare(entry.getKey(), key, false) == 0) {
                found = true;
            } else {
                remaining.put(entry.getKey(), entry.getValue());
            }
        }
        return found ? Optional.of(new MapValue(remaining)) : Optional.empty();
    }

    private static boolean invalidMapBuiltinKey(MooValue key) {
        return key instanceof ListValue
                || key instanceof MapValue
                || key instanceof AnonymousObjectValue;
    }
}
