package world.mongoose.banteng.builtin.family;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinPermissionRule;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.host.Aspell;
import world.mongoose.banteng.host.NativeCurl;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.WorldTxn;

/** Owns builtin registrations and implementations for this family. */
public final class HostBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
    private static final Set<ArgType> STRING = Set.of(ArgType.STRING);

    private HostBuiltins() {
    }

    public static void registerEnvironment(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("getenv", List.of(new CallShape(List.of(STRING), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> {
            String value = System.getenv(((StringValue) call.arguments().getFirst()).text());
            return value == null ? BuiltinResult.value(new IntegerValue(0))
                    : BuiltinResult.value(StringValue.of(value).encodeBinary());
        });
    }

    public static void registerTransferAndText(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("curl", List.of(new CallShape(List.of(STRING), List.of(ANY, INTEGER), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> curl(call.arguments(), call.world(), call.threadMode()));
        registrar.builtin("spellcheck", List.of(new CallShape(List.of(STRING), List.of(), Optional.empty()))).implementation(call -> spellcheck((StringValue) call.arguments().getFirst()));
        registrar.builtin("url_encode", List.of(new CallShape(List.of(STRING), List.of(), Optional.empty()))).implementation(call -> urlEncode((StringValue) call.arguments().getFirst()));
        registrar.builtin("url_decode", List.of(new CallShape(List.of(STRING), List.of(), Optional.empty()))).implementation(call -> urlDecode((StringValue) call.arguments().getFirst()));
    }

    private static BuiltinResult curl(List<MooValue> arguments, WorldTxn world, boolean threadMode) {
        long maximumTimeout = Math.max(1, serverIntegerOption(world, "curl_max_timeout", 300));
        long defaultTimeout = Math.clamp(serverIntegerOption(world, "curl_timeout", 60), 1, maximumTimeout);
        long requestedTimeout = arguments.size() > 2 ? ((IntegerValue) arguments.get(2)).value() : defaultTimeout;
        if (requestedTimeout < 1 || requestedTimeout > maximumTimeout) {
            return BuiltinResult.raised(ErrorValue.E_INVARG,
                    StringValue.of("Timeout must be between 1 and $server_options.curl_max_timeout seconds"),
                    new IntegerValue(requestedTimeout));
        }
        long configuredBytes = serverIntegerOption(world, "curl_max_response_bytes", 10 * 1024 * 1024);
        long maximumBytes = configuredBytes < 1 ? 10 * 1024 * 1024 : Math.min(configuredBytes, 256 * 1024 * 1024);
        String url = ((StringValue) arguments.getFirst()).text();
        boolean includeHeaders = arguments.size() > 1 && arguments.get(1).isTruthy();
        return threadMode
                ? BuiltinResult.hostWork(() -> curlTransfer(url, includeHeaders, requestedTimeout, maximumBytes))
                : curlTransfer(url, includeHeaders, requestedTimeout, maximumBytes);
    }

    private static long serverIntegerOption(WorldTxn world, String name, long fallback) {
        if (world.readObjectProperty(0, "server_options").orElse(null) instanceof ObjectValue options
                && world.readObjectProperty(options.value(), name).orElse(null) instanceof IntegerValue integer) {
            return integer.value();
        }
        return fallback;
    }

    private static BuiltinResult curlTransfer(String url, boolean headers, long timeout, long maximumBytes) {
        try {
            NativeCurl.Result result = NativeCurl.get(url, headers, timeout, maximumBytes);
            if (result.status() == 0) return BuiltinResult.value(StringValue.of(result.body()).encodeBinary());
            return curlError(result.overflowed() || result.status() == -1 ? ErrorValue.E_QUOTA : ErrorValue.E_INVARG,
                    result.overflowed() ? "Response exceeded the maximum allowed size" : result.message());
        } catch (IllegalStateException | LinkageError failure) {
            return curlError(ErrorValue.E_INVARG, "Could not perform curl transfer");
        }
    }

    private static BuiltinResult curlError(ErrorValue error, String message) {
        return BuiltinResult.value(new MapValue(Map.of(
                StringValue.of("error"), error, StringValue.of("message"), StringValue.of(message))));
    }

    private static BuiltinResult spellcheck(StringValue value) {
        try {
            Aspell.Result result = Aspell.check(value.bytes());
            return BuiltinResult.value(result.correct() != 0
                    ? new IntegerValue(result.correct())
                    : new ListValue(result.suggestions().stream().map(StringValue::of).map(MooValue.class::cast).toList()));
        } catch (IllegalStateException | LinkageError failure) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
    }

    private static BuiltinResult urlEncode(StringValue value) {
        StringBuilder encoded = new StringBuilder();
        String digits = "0123456789ABCDEF";
        for (byte raw : value.bytes()) {
            int b = Byte.toUnsignedInt(raw);
            if (b == 0) break;
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z')
                    || (b >= '0' && b <= '9') || b == '-' || b == '_' || b == '.' || b == '~') {
                encoded.append((char) b);
            } else {
                encoded.append('%').append(digits.charAt(b >>> 4)).append(digits.charAt(b & 15));
            }
        }
        return BuiltinResult.value(StringValue.of(encoded.toString()));
    }

    private static BuiltinResult urlDecode(StringValue value) {
        byte[] input = value.bytes();
        ByteArrayOutputStream decoded = new ByteArrayOutputStream(input.length);
        for (int i = 0; i < input.length; i++) {
            int b = Byte.toUnsignedInt(input[i]);
            if (b == '%' && i + 2 < input.length) {
                int high = Character.digit((char) Byte.toUnsignedInt(input[i + 1]), 16);
                int low = Character.digit((char) Byte.toUnsignedInt(input[i + 2]), 16);
                if (high >= 0 && low >= 0) {
                    b = high * 16 + low;
                    i += 2;
                }
            }
            if (b == 0) break;
            decoded.write(b);
        }
        return BuiltinResult.value(StringValue.of(decoded.toByteArray()));
    }
}
