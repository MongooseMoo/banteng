package world.mongoose.banteng.builtin.family;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.WorldTxn;

/** Owns builtin registrations and implementations for this family. */
public final class BinaryBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> STRING = Set.of(ArgType.STRING);

    private BinaryBuiltins() {
    }

    public static void registerDecode(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("decode_binary", List.of(new CallShape(List.of(STRING), List.of(ANY), Optional.empty()))).implementation(call -> decodeBinary(call.arguments()));
    }

    public static void registerEncodeAndChr(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("encode_binary", List.of(new CallShape(List.of(), List.of(), Optional.of(ANY)))).implementation(call -> encodeBinary(call.arguments()));
        registrar.builtin("chr", List.of(new CallShape(List.of(), List.of(), Optional.of(ANY)))).implementation(call -> chr(call.arguments(), call.world(), call.programmer()));
    }

    private static BuiltinResult decodeBinary(List<MooValue> arguments) {
        Optional<byte[]> bytes = ((StringValue) arguments.getFirst()).binaryBytes();
        if (bytes.isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        byte[] decoded = bytes.orElseThrow();
        if (arguments.size() == 2 && arguments.get(1).isTruthy()) {
            return BuiltinResult.value(
                    new ListValue(
                            java.util.stream.IntStream.range(0, decoded.length)
                                    .mapToObj(index -> new IntegerValue(Byte.toUnsignedInt(decoded[index])))
                                    .map(MooValue.class::cast)
                                    .toList()));
        }

        List<MooValue> values = new ArrayList<>();
        ByteArrayOutputStream printable = new ByteArrayOutputStream();
        for (byte current : decoded) {
            int value = Byte.toUnsignedInt(current);
            if ((value >= 0x21 && value <= 0x7e) || value == ' ' || value == '\t') {
                printable.write(value);
            } else {
                if (printable.size() != 0) {
                    values.add(StringValue.of(printable.toByteArray()));
                    printable.reset();
                }
                values.add(new IntegerValue(value));
            }
        }
        if (printable.size() != 0) {
            values.add(StringValue.of(printable.toByteArray()));
        }
        return BuiltinResult.value(new ListValue(values));
    }

    private static BuiltinResult encodeBinary(List<MooValue> arguments) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (MooValue argument : arguments) {
            if (!appendBinaryBytes(raw, argument, 0, 255)) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
        }

        return BuiltinResult.value(StringValue.of(raw.toByteArray()).encodeBinary());
    }

    private static BuiltinResult chr(List<MooValue> arguments, WorldTxn world, long programmer) {
        int minimum = world.isWizard(programmer) ? 0 : 32;
        int maximum = world.isWizard(programmer) ? 255 : 254;
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (MooValue argument : arguments) {
            if (!appendBinaryBytes(raw, argument, minimum, maximum)) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
        }
        byte[] bytes = raw.toByteArray();
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            length++;
        }
        return BuiltinResult.value(StringValue.of(Arrays.copyOf(bytes, length)));
    }

    private static boolean appendBinaryBytes(
            ByteArrayOutputStream output, MooValue value, int minimum, int maximum) {
        if (value instanceof StringValue string) {
            output.writeBytes(string.bytes());
            return true;
        }
        if (value instanceof IntegerValue integer) {
            if (integer.value() < minimum || integer.value() > maximum) {
                return false;
            }
            output.write((int) integer.value());
            return true;
        }
        if (value instanceof ListValue list) {
            for (MooValue element : list.elements()) {
                if (!appendBinaryBytes(output, element, minimum, maximum)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
