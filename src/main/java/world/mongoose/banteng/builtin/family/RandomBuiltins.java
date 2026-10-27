package world.mongoose.banteng.builtin.family;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinPermissionRule;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.builtin.ValueLimits;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.WorldTxn;

/** Owns builtin registrations and implementations for this family. */
public final class RandomBuiltins {
    private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);

    private RandomBuiltins() {
    }

    public static void register(List<BuiltinSpec> entries, java.util.function.Supplier<Random> random, java.util.function.Supplier<Random> floatingRandom, SecureRandom secureRandom) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("random", List.of(new CallShape(List.of(), List.of(INTEGER, INTEGER), Optional.empty()))).irrevocable().implementation(call -> randomInteger(call.arguments(), random.get()));
        registrar.builtin("reseed_random", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> {
            random.get().setSeed(secureRandom.nextLong());
            return BuiltinResult.value(new IntegerValue(0));
        });
        registrar.builtin("frandom", List.of(
                new CallShape(
                        List.of(Set.of(ArgType.FLOAT)),
                        List.of(Set.of(ArgType.FLOAT)),
                        Optional.empty()))).irrevocable().implementation(call -> floatingRandom(call.arguments(), floatingRandom.get()));
        registrar.builtin("random_bytes", List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty()))).irrevocable().implementation(call -> randomBytes(call.arguments(), call.world(), secureRandom));
    }

    private static BuiltinResult floatingRandom(List<MooValue> arguments, Random floatingRandom) {
        double minimum = arguments.size() == 2 ? ((FloatValue) arguments.get(0)).value() : 0.0;
        double maximum =
                ((FloatValue) arguments.get(arguments.size() == 2 ? 1 : 0)).value();
        double fraction = (floatingRandom.nextInt() >>> 1) / (double) Integer.MAX_VALUE;
        return BuiltinResult.value(new FloatValue(minimum + fraction * (maximum - minimum)));
    }

    private static BuiltinResult randomBytes(List<MooValue> arguments, WorldTxn world, SecureRandom secureRandom) {
        long requested = ((IntegerValue) arguments.getFirst()).value();
        if (requested < 0 || requested > 10_000) {
            return BuiltinResult.raised(
                    ErrorValue.E_INVARG, StringValue.of("Invalid count"), arguments.getFirst());
        }
        byte[] random = new byte[(int) requested];
        secureRandom.nextBytes(random);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream(random.length);
        for (byte current : random) {
            int value = Byte.toUnsignedInt(current);
            if (value != '~' && ((value >= 0x21 && value <= 0x7e) || value == ' ')) {
                encoded.write(value);
            } else {
                encoded.write('~');
                encoded.write(Character.toUpperCase(Character.forDigit(value >>> 4, 16)));
                encoded.write(Character.toUpperCase(Character.forDigit(value & 0x0f, 16)));
            }
        }
        Optional<BuiltinResult> error = ValueLimits.stringLengthError(encoded.size(), world);
        return error.orElseGet(() -> BuiltinResult.value(StringValue.of(encoded.toByteArray())));
    }

    private static BuiltinResult randomInteger(List<MooValue> arguments, Random random) {
        long lower = 1;
        long upper = Long.MAX_VALUE;
        if (arguments.size() == 1) {
            upper = ((IntegerValue) arguments.getFirst()).value();
            if (upper <= 0) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
        } else if (arguments.size() == 2) {
            lower = ((IntegerValue) arguments.get(0)).value();
            upper = ((IntegerValue) arguments.get(1)).value();
            if (upper < lower) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
        }
        if (lower == upper) {
            return BuiltinResult.value(new IntegerValue(lower));
        }
        long width = upper - lower + 1;
        if (width > 0) {
            return BuiltinResult.value(new IntegerValue(lower + random.nextLong(width)));
        }
        long value;
        do {
            value = random.nextLong();
        } while (value < lower || value > upper);
        return BuiltinResult.value(new IntegerValue(value));
    }
}
