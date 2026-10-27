package world.mongoose.banteng.builtin.family;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.BooleanValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.WaifValue;

import world.mongoose.banteng.value.ValueSemantics;

/** Owns builtin registrations and implementations for this family. */
public final class ConversionBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);

    private ConversionBuiltins() {
    }

    public static void registerConversions(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("tostr", List.of(new CallShape(List.of(), List.of(), Optional.of(ANY)))).implementation(call -> toStringValue(call.arguments()));
        registrar.builtin("tofloat", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call -> toFloat(call.arguments()));
        registrar.builtin("toint", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call -> toInteger(call.arguments()));
        registrar.builtin("toliteral", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call -> toLiteral(call.arguments()));
        registrar.builtin("toobj", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call -> toObject(call.arguments()));
        registrar.builtin("equal", List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty()))).implementation(call ->
                equalValues(call.arguments(), call.hosts().valueSemantics()));
    }

    public static void registerTypeOf(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("typeof", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call -> typeOf(call.arguments()));
    }

    public static BuiltinResult toStringValue(List<MooValue> arguments) {
        StringBuilder text = new StringBuilder();
        for (MooValue argument : arguments) {
            text.append(
                    switch (argument) {
                        case StringValue string -> string.text();
                        case IntegerValue integer -> Long.toString(integer.value());
                        case BooleanValue bool -> bool.toLiteral();
                        case FloatValue floating -> floating.toLiteral();
                        case ObjectValue object -> object.toLiteral();
                        case AnonymousObjectValue anonymous -> anonymous.toString();
                        case WaifValue waif -> waif.toString();
                        case ErrorValue error -> errorDescription(error);
                        case ListValue _ -> "{list}";
                        case MapValue _ -> "[map]";
                    });
        }
        return BuiltinResult.value(StringValue.of(text.toString()));
    }

    private static String errorDescription(ErrorValue error) {
        return error.description();
    }

    private static BuiltinResult toFloat(List<MooValue> arguments) {
        MooValue argument = arguments.getFirst();
        if (argument instanceof FloatValue floating) {
            return BuiltinResult.value(floating);
        }
        if (argument instanceof IntegerValue integer) {
            return BuiltinResult.value(new FloatValue(integer.value()));
        }
        if (argument instanceof ObjectValue object) {
            return BuiltinResult.value(new FloatValue(object.value()));
        }
        if (argument instanceof ErrorValue error) {
            return BuiltinResult.value(new FloatValue(error.code()));
        }
        if (argument instanceof StringValue string) {
            try {
                String text = string.text().strip();
                if (text.matches("[+-]?0[xX][0-9a-fA-F]+(?:\\.[0-9a-fA-F]*)?")) {
                    text += "p0";
                }
                double converted = Double.parseDouble(text);
                return Double.isFinite(converted)
                        ? BuiltinResult.value(new FloatValue(converted))
                        : BuiltinResult.error(ErrorValue.E_INVARG);
            } catch (NumberFormatException error) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
        }
        return BuiltinResult.error(ErrorValue.E_TYPE);
    }

    private static BuiltinResult toInteger(List<MooValue> arguments) {
        MooValue argument = arguments.getFirst();
        if (argument instanceof IntegerValue integer) {
            return BuiltinResult.value(integer);
        }
        if (argument instanceof BooleanValue bool) {
            return BuiltinResult.value(new IntegerValue(bool.value() ? 1 : 0));
        }
        if (argument instanceof FloatValue floating) {
            return Double.isFinite(floating.value())
                    ? BuiltinResult.value(new IntegerValue((long) floating.value()))
                    : BuiltinResult.error(ErrorValue.E_FLOAT);
        }
        if (argument instanceof ObjectValue object) {
            return BuiltinResult.value(new IntegerValue(object.value()));
        }
        if (argument instanceof ErrorValue error) {
            return BuiltinResult.value(new IntegerValue(error.code()));
        }
        if (argument instanceof StringValue string) {
            String text = string.text().strip();
            try {
                return BuiltinResult.value(new IntegerValue(Long.parseLong(text)));
            } catch (NumberFormatException integerError) {
                try {
                    double converted = Double.parseDouble(text);
                    return Double.isFinite(converted)
                            ? BuiltinResult.value(new IntegerValue((long) converted))
                            : BuiltinResult.value(new IntegerValue(0));
                } catch (NumberFormatException floatingError) {
                    return BuiltinResult.value(new IntegerValue(0));
                }
            }
        }
        return BuiltinResult.error(ErrorValue.E_TYPE);
    }

    private static BuiltinResult toLiteral(List<MooValue> arguments) {
        return BuiltinResult.value(StringValue.of(arguments.getFirst().toLiteral()));
    }

    private static BuiltinResult toObject(List<MooValue> arguments) {
        MooValue argument = arguments.getFirst();
        if (argument instanceof ObjectValue object) {
            return BuiltinResult.value(object);
        }
        if (argument instanceof IntegerValue integer) {
            return BuiltinResult.value(new ObjectValue(integer.value()));
        }
        if (argument instanceof BooleanValue bool) {
            return BuiltinResult.value(new ObjectValue(bool.value() ? 1 : 0));
        }
        if (argument instanceof FloatValue floating) {
            return Double.isFinite(floating.value())
                    ? BuiltinResult.value(new ObjectValue((long) floating.value()))
                    : BuiltinResult.error(ErrorValue.E_FLOAT);
        }
        if (argument instanceof ErrorValue error) {
            return BuiltinResult.value(new ObjectValue(error.code()));
        }
        if (argument instanceof StringValue string) {
            return BuiltinResult.value(new ObjectValue(parseToastObjectId(string.text())));
        }
        return BuiltinResult.error(ErrorValue.E_TYPE);
    }

    private static long parseToastObjectId(String text) {
        int index = 0;
        while (index < text.length() && text.charAt(index) == ' ') {
            index++;
        }
        if (index < text.length() && text.charAt(index) == '#') {
            index++;
        }
        while (index < text.length() && isAsciiWhitespace(text.charAt(index))) {
            index++;
        }

        int numberStart = index;
        if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
            index++;
        }
        int digitStart = index;
        while (index < text.length() && text.charAt(index) >= '0' && text.charAt(index) <= '9') {
            index++;
        }
        if (index == digitStart) {
            return 0;
        }
        int numberEnd = index;
        while (index < text.length() && text.charAt(index) == ' ') {
            index++;
        }
        if (index != text.length()) {
            return 0;
        }

        BigInteger parsed = new BigInteger(text.substring(numberStart, numberEnd));
        if (parsed.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return Long.MAX_VALUE;
        }
        if (parsed.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
            return Long.MIN_VALUE;
        }
        return parsed.longValue();
    }

    private static boolean isAsciiWhitespace(char character) {
        return character == ' '
                || character == '\t'
                || character == '\n'
                || character == '\u000B'
                || character == '\f'
                || character == '\r';
    }

    private static BuiltinResult equalValues(
            List<MooValue> arguments, ValueSemantics valueSemantics) {
        return BuiltinResult.value(
                new IntegerValue(
                        valueSemantics.equal(arguments.get(0), arguments.get(1), true) ? 1 : 0));
    }

    private static BuiltinResult typeOf(List<MooValue> arguments) {
        return BuiltinResult.value(new IntegerValue(arguments.getFirst().type().code()));
    }
}
