package world.mongoose.banteng.builtin.family;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.host.Libc;
import world.mongoose.banteng.host.NativeCallException;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.StringValue;

/** Owns builtin registrations and implementations for this family. */
public final class TimeBuiltins {
    private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);

    private TimeBuiltins() {
    }

    public static void register(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("time", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).irrevocable().implementation(call ->
                BuiltinResult.value(new IntegerValue(Instant.now().getEpochSecond())));
        registrar.builtin("ctime", List.of(new CallShape(List.of(), List.of(INTEGER), Optional.empty()))).irrevocable().implementation(call -> ctime(call.arguments()));
        registrar.builtin("ftime", List.of(new CallShape(List.of(), List.of(INTEGER), Optional.empty()))).irrevocable().implementation(call -> ftime(call.arguments()));
    }

    private static BuiltinResult ctime(List<MooValue> arguments) {
        long epochSecond =
                arguments.isEmpty()
                        ? Instant.now().getEpochSecond()
                        : Math.min(((IntegerValue) arguments.getFirst()).value(), CTIME_MAX_SECONDS);
        try {
            return Libc.ctime(epochSecond)
                    .map(text -> BuiltinResult.value(StringValue.of(text)))
                    .orElseGet(() -> BuiltinResult.error(ErrorValue.E_INVARG));
        } catch (NativeCallException failure) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
    }

    private static BuiltinResult ftime(List<MooValue> arguments) {
        int clockId = CLOCK_REALTIME;
        if (!arguments.isEmpty()) {
            clockId =
                    ((IntegerValue) arguments.getFirst()).value() == 2
                            ? CLOCK_MONOTONIC_RAW
                            : CLOCK_MONOTONIC;
        }
        try {
            return BuiltinResult.value(new FloatValue(Libc.clockTime(clockId)));
        } catch (NativeCallException failure) {
            return BuiltinResult.error(ErrorValue.E_FLOAT);
        }
    }
    private static final int CLOCK_REALTIME = 0;
    private static final int CLOCK_MONOTONIC = 1;
    private static final int CLOCK_MONOTONIC_RAW = 4;
    private static final long CTIME_MAX_SECONDS = (long) Integer.MAX_VALUE * 31_536_000L;
}
