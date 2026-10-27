package world.mongoose.banteng.builtin.family;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.StringValue;

/** Owns builtin registrations and implementations for this family. */
public final class JsonBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> STRING = Set.of(ArgType.STRING);

    private JsonBuiltins() {
    }

    public static void register(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("generate_json", List.of(new CallShape(List.of(ANY), List.of(STRING, ANY), Optional.empty()))).implementation(call -> generateJson(call.arguments()));
        registrar.builtin("parse_json", List.of(new CallShape(List.of(STRING), List.of(STRING), Optional.empty()))).implementation(call -> parseJson(call.arguments()));
    }

    private static BuiltinResult generateJson(List<MooValue> arguments) {
        JsonCodec.Mode mode = JsonCodec.Mode.COMMON_SUBSET;
        if (arguments.size() >= 2) {
            mode = JsonCodec.Mode.parse((StringValue) arguments.get(1)).orElse(null);
            if (mode == null) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
        }
        try {
            return BuiltinResult.value(
                    JsonCodec.generate(
                            arguments.getFirst(), mode, arguments.size() >= 3 && arguments.get(2).isTruthy()));
        } catch (IllegalArgumentException error) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
    }

    private static BuiltinResult parseJson(List<MooValue> arguments) {
        JsonCodec.Mode mode = JsonCodec.Mode.COMMON_SUBSET;
        if (arguments.size() == 2) {
            mode = JsonCodec.Mode.parse((StringValue) arguments.get(1)).orElse(null);
            if (mode == null) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
        }
        try {
            return BuiltinResult.value(JsonCodec.parse((StringValue) arguments.getFirst(), mode));
        } catch (IllegalArgumentException error) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
    }
}
