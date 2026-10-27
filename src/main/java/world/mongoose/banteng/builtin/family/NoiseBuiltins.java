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
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.ListValue;

/** Owns builtin registrations and implementations for this family. */
public final class NoiseBuiltins {
    private NoiseBuiltins() {
    }

    public static void register(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("simplex_noise", List.of(new CallShape(List.of(Set.of(ArgType.LIST)), List.of(), Optional.empty()))).implementation(call -> simplexNoise(call.arguments()));
    }

    private static BuiltinResult simplexNoise(List<MooValue> arguments) {
        ListValue coordinates = (ListValue) arguments.getFirst();
        double[] values = new double[coordinates.size()];
        for (int index = 0; index < coordinates.size(); index++) {
            MooValue coordinate = coordinates.elements().get(index);
            if (!(coordinate instanceof FloatValue value)) {
                return BuiltinResult.error(ErrorValue.E_TYPE);
            }
            values[index] = value.value();
        }
        if (values.length < 1 || values.length > 4) {
            return BuiltinResult.value(ErrorValue.E_TYPE);
        }
        return BuiltinResult.value(new FloatValue(SimplexNoise.noise(values)));
    }
}
