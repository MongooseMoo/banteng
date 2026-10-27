package world.mongoose.banteng.builtin.family;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinHandler;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.StringValue;

/** Owns math builtin registrations and implementations. */
public final class MathBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
    private static final Set<ArgType> NUMBER = Set.of(ArgType.NUMBER);

    private MathBuiltins() {
    }

    public static void register(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("min", List.of(new CallShape(List.of(NUMBER), List.of(), Optional.of(ANY)))).implementation(call -> minimum(call.arguments()));
        registrar.builtin("max", List.of(new CallShape(List.of(NUMBER), List.of(), Optional.of(ANY)))).implementation(call -> maximum(call.arguments()));
        registrar.builtin("abs", List.of(new CallShape(List.of(NUMBER), List.of(), Optional.empty()))).implementation(call -> absoluteValue(call.arguments()));
        registrar.builtin("acos", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(MathBuiltins::acos));
        registrar.builtin("acosh", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(MathBuiltins::acosh));
        registrar.builtin("asin", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(MathBuiltins::asin));
        registrar.builtin("asinh", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(MathBuiltins::asinh));
        registrar.builtin("atan", List.of(
                new CallShape(
                        List.of(Set.of(ArgType.FLOAT)),
                        List.of(Set.of(ArgType.FLOAT)),
                        Optional.empty()))).implementation(call -> atan(call.arguments()));
        registrar.builtin("atan2", List.of(
                new CallShape(
                        List.of(Set.of(ArgType.FLOAT), Set.of(ArgType.FLOAT)),
                        List.of(),
                        Optional.empty()))).implementation(call -> atan2(call.arguments()));
        registrar.builtin("atanh", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(MathBuiltins::atanh));
        registrar.builtin("cbrt", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(Math::cbrt));
        registrar.builtin("ceil", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(Math::ceil));
        registrar.builtin("cos", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(MathBuiltins::cosine));
        registrar.builtin("cosh", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(Math::cosh));
        registrar.builtin("distance", List.of(
                new CallShape(
                        List.of(Set.of(ArgType.LIST), Set.of(ArgType.LIST)),
                        List.of(),
                        Optional.empty()))).implementation(call -> distance(call.arguments()));
        registrar.builtin("exp", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(value -> {
            double result = Math.exp(value);
            return result < Double.MIN_NORMAL ? Double.NaN : result;
        }));
        registrar.builtin("floatstr", List.of(
                new CallShape(
                        List.of(Set.of(ArgType.FLOAT), INTEGER),
                        List.of(ANY),
                        Optional.empty()))).implementation(call -> floatstr(call.arguments()));
        registrar.builtin("floor", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(Math::floor));
        registrar.builtin("log", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(MathBuiltins::log));
        registrar.builtin("log10", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(MathBuiltins::log10));
        registrar.builtin("relative_heading", List.of(
                new CallShape(
                        List.of(Set.of(ArgType.LIST), Set.of(ArgType.LIST)),
                        List.of(),
                        Optional.empty()))).implementation(call -> relativeHeading(call.arguments()));
        registrar.builtin("round", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(call -> round(call.arguments()));
        registrar.builtin("sin", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(MathBuiltins::sine));
        registrar.builtin("sinh", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(Math::sinh));
        registrar.builtin("sqrt", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(MathBuiltins::sqrt));
        registrar.builtin("tan", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(MathBuiltins::tangent));
        registrar.builtin("tanh", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(unaryFloatBuiltin(Math::tanh));
        registrar.builtin("trunc", List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty()))).implementation(call -> trunc(call.arguments()));
    }

    private static BuiltinResult minimum(List<MooValue> arguments) {
        MooValue first = arguments.getFirst();
        if (first instanceof IntegerValue integer) {
            long minimum = integer.value();
            for (MooValue argument : arguments) {
                if (!(argument instanceof IntegerValue value)) {
                    return BuiltinResult.error(ErrorValue.E_TYPE);
                }
                minimum = Math.min(minimum, value.value());
            }
            return BuiltinResult.value(new IntegerValue(minimum));
        }
        if (first instanceof FloatValue floating) {
            double minimum = floating.value();
            for (MooValue argument : arguments) {
                if (!(argument instanceof FloatValue value)) {
                    return BuiltinResult.error(ErrorValue.E_TYPE);
                }
                minimum = Math.min(minimum, value.value());
            }
            return BuiltinResult.value(new FloatValue(minimum));
        }
        return BuiltinResult.error(ErrorValue.E_TYPE);
    }

    private static BuiltinResult maximum(List<MooValue> arguments) {
        MooValue first = arguments.getFirst();
        if (first instanceof IntegerValue integer) {
            long maximum = integer.value();
            for (MooValue argument : arguments) {
                if (!(argument instanceof IntegerValue value)) {
                    return BuiltinResult.error(ErrorValue.E_TYPE);
                }
                maximum = Math.max(maximum, value.value());
            }
            return BuiltinResult.value(new IntegerValue(maximum));
        }
        if (first instanceof FloatValue floating) {
            double maximum = floating.value();
            for (MooValue argument : arguments) {
                if (!(argument instanceof FloatValue value)) {
                    return BuiltinResult.error(ErrorValue.E_TYPE);
                }
                maximum = Math.max(maximum, value.value());
            }
            return BuiltinResult.value(new FloatValue(maximum));
        }
        return BuiltinResult.error(ErrorValue.E_TYPE);
    }

    private static BuiltinResult absoluteValue(List<MooValue> arguments) {
        MooValue argument = arguments.getFirst();
        if (argument instanceof IntegerValue integer) {
            return BuiltinResult.value(new IntegerValue(Math.abs(integer.value())));
        }
        return BuiltinResult.value(new FloatValue(Math.abs(((FloatValue) argument).value())));
    }

    private static BuiltinHandler unaryFloatBuiltin(DoubleUnaryOperator operator) {
        DoubleUnaryOperator operation = Objects.requireNonNull(operator, "operator");
        return call -> {
            final double result;
            try {
                result = operation.applyAsDouble(numericDouble(call.arguments().getFirst()));
            } catch (IllegalArgumentException _) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
            return Double.isFinite(result)
                    ? BuiltinResult.value(new FloatValue(result))
                    : BuiltinResult.error(ErrorValue.E_FLOAT);
        };
    }

    private static double acos(double value) {
        if (value < -1.0 || value > 1.0) {
            throw new IllegalArgumentException("acos domain");
        }
        return Math.acos(value);
    }

    private static double acosh(double value) {
        if (value < 1.0) {
            throw new IllegalArgumentException("acosh domain");
        }
        return Math.log(value)
                + Math.log1p(Math.sqrt(1.0 - 1.0 / value) * Math.sqrt(1.0 + 1.0 / value));
    }

    private static double asin(double value) {
        if (value < -1.0 || value > 1.0) {
            throw new IllegalArgumentException("asin domain");
        }
        return Math.asin(value);
    }

    private static double asinh(double value) {
        double absolute = Math.abs(value);
        double magnitude =
                absolute > Math.sqrt(Double.MAX_VALUE)
                        ? Math.log(absolute) + Math.log(2.0)
                        : Math.log1p(
                        absolute + absolute * absolute / (1.0 + Math.hypot(1.0, absolute)));
        return Math.copySign(magnitude, value);
    }

    private static BuiltinResult atan(List<MooValue> arguments) {
        double y = numericDouble(arguments.getFirst());
        double result =
                arguments.size() == 2
                        ? Math.atan2(y, numericDouble(arguments.get(1)))
                        : Math.atan(y);
        return Double.isFinite(result)
                ? BuiltinResult.value(new FloatValue(result))
                : BuiltinResult.error(ErrorValue.E_FLOAT);
    }

    private static BuiltinResult atan2(List<MooValue> arguments) {
        double y = numericDouble(arguments.get(0));
        double x = numericDouble(arguments.get(1));
        return BuiltinResult.value(new FloatValue(Math.atan2(y, x)));
    }

    private static double atanh(double value) {
        if (Math.abs(value) > 1.0) {
            throw new IllegalArgumentException("atanh domain");
        }
        return 0.5 * (Math.log1p(value) - Math.log1p(-value));
    }

    private static double cosine(double value) {
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException("cos domain");
        }
        return Math.cos(value);
    }

    private static double log(double value) {
        if (value < 0.0) {
            throw new IllegalArgumentException("log domain");
        }
        return Math.log(value);
    }

    private static double log10(double value) {
        if (value < 0.0) {
            throw new IllegalArgumentException("log10 domain");
        }
        return Math.log10(value);
    }

    private static BuiltinResult round(List<MooValue> arguments) {
        double value = ((FloatValue) arguments.getFirst()).value();
        double truncated = value < 0.0 ? Math.ceil(value) : Math.floor(value);
        double result =
                Math.abs(value - truncated) >= 0.5
                        ? truncated + Math.copySign(1.0, value)
                        : truncated;
        return BuiltinResult.value(new FloatValue(result));
    }

    private static double sine(double value) {
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException("sin domain");
        }
        return Math.sin(value);
    }

    private static double sqrt(double value) {
        if (value < 0.0) {
            throw new IllegalArgumentException("sqrt domain");
        }
        return Math.sqrt(value);
    }

    private static double tangent(double value) {
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException("tan domain");
        }
        return Math.tan(value);
    }

    private static BuiltinResult trunc(List<MooValue> arguments) {
        double value = ((FloatValue) arguments.getFirst()).value();
        double result = value < 0.0 ? Math.ceil(value) : Math.floor(value);
        return Double.isFinite(result)
                ? BuiltinResult.value(new FloatValue(result))
                : BuiltinResult.error(ErrorValue.E_FLOAT);
    }

    private static double numericDouble(MooValue value) {
        return value instanceof IntegerValue integer
                ? integer.value()
                : ((FloatValue) value).value();
    }

    private static BuiltinResult distance(List<MooValue> arguments) {
        ListValue from = (ListValue) arguments.get(0);
        ListValue to = (ListValue) arguments.get(1);
        if (to.size() < from.size()) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        double squared = 0.0;
        for (int index = 0; index < from.size(); index++) {
            MooValue fromValue = from.elements().get(index);
            MooValue toValue = to.elements().get(index);
            if (!(fromValue instanceof IntegerValue || fromValue instanceof FloatValue)
                    || !(toValue instanceof IntegerValue || toValue instanceof FloatValue)) {
                return BuiltinResult.error(ErrorValue.E_TYPE);
            }
            double left =
                    fromValue instanceof IntegerValue integer ? integer.value() : ((FloatValue) fromValue).value();
            double right =
                    toValue instanceof IntegerValue integer ? integer.value() : ((FloatValue) toValue).value();
            double difference = right - left;
            squared += difference * difference;
        }
        return BuiltinResult.value(new FloatValue(Math.sqrt(squared)));
    }

    private static BuiltinResult floatstr(List<MooValue> arguments) {
        double value = ((FloatValue) arguments.get(0)).value();
        long requestedPrecision = ((IntegerValue) arguments.get(1)).value();
        if (requestedPrecision < 0) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        int precision = (int) Math.min(requestedPrecision, 21);
        boolean scientific = arguments.size() == 3 && arguments.get(2).isTruthy();
        StringBuilder pattern = new StringBuilder("0");
        if (precision > 0) {
            pattern.append('.').append("0".repeat(precision));
        }
        if (scientific) {
            pattern.append("E00");
        }
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        symbols.setNaN("nan");
        symbols.setInfinity("inf");
        DecimalFormat formatter = new DecimalFormat(pattern.toString(), symbols);
        formatter.setGroupingUsed(false);
        formatter.setRoundingMode(RoundingMode.HALF_EVEN);
        String formatted = formatter.format(value);
        if (scientific) {
            int exponent = formatted.indexOf('E');
            char sign = exponent + 1 < formatted.length() ? formatted.charAt(exponent + 1) : '+';
            formatted =
                    formatted.substring(0, exponent)
                            + 'e'
                            + (sign == '-' ? "" : "+")
                            + formatted.substring(exponent + 1);
        }
        return BuiltinResult.value(StringValue.of(formatted));
    }

    private static BuiltinResult relativeHeading(List<MooValue> arguments) {
        ListValue from = (ListValue) arguments.get(0);
        ListValue to = (ListValue) arguments.get(1);
        if (from.size() < 3 || to.size() < 3) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        for (int index = 0; index < 3; index++) {
            if (!(from.elements().get(index) instanceof FloatValue)
                    || !(to.elements().get(index) instanceof FloatValue)) {
                return BuiltinResult.error(ErrorValue.E_TYPE);
            }
        }
        double dx = ((FloatValue) to.elements().get(0)).value() - ((FloatValue) from.elements().get(0)).value();
        double dy = ((FloatValue) to.elements().get(1)).value() - ((FloatValue) from.elements().get(1)).value();
        double dz = ((FloatValue) to.elements().get(2)).value() - ((FloatValue) from.elements().get(2)).value();
        double horizontal = Math.atan2(dy, dx) * 57.2957795130823;
        if (horizontal < 0.0) {
            horizontal += 360.0;
        }
        double vertical = Math.atan2(dz, Math.sqrt(dx * dx + dy * dy)) * 57.2957795130823;
        return BuiltinResult.value(
                new ListValue(
                        List.of(new IntegerValue((long) horizontal), new IntegerValue((long) vertical))));
    }
}
