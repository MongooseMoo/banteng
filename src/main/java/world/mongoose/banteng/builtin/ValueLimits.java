package world.mongoose.banteng.builtin;
import java.util.Map;
import java.util.Optional;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldTxn;

public final class ValueLimits {
    private ValueLimits() {
    }
    private static final long DEFAULT_MAX_LIST_VALUE_BYTES = 64_537_861L;
    private static final long MIN_LIST_VALUE_BYTES_LIMIT = 1_021L;
    private static final long MAX_LIST_VALUE_BYTES_LIMIT = 2_147_482_626L;

    public static long valueBytes(MooValue value, WorldTxn world) {
        return value.valueBytesWithoutWaifProperties() + waifPropertyBytes(value, world);
    }

    private static long waifPropertyBytes(MooValue value, WorldTxn world) {
        if (!value.containsWaifReferences()) return 0;
        return switch (value) {
            case ListValue list -> {
                long bytes = 0;
                for (MooValue element : list.elements()) {
                    bytes += waifPropertyBytes(element, world);
                }
                yield bytes;
            }
            case MapValue map -> {
                long bytes = 0;
                for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
                    bytes += waifPropertyBytes(entry.getKey(), world);
                    bytes += waifPropertyBytes(entry.getValue(), world);
                }
                yield bytes;
            }
            case WaifValue waif -> {
                long bytes = 0;
                var body = world.waif(waif).orElse(null);
                if (body != null) {
                    for (WorldProperty property : body.properties()) {
                        if (!property.clear()) {
                            bytes += valueBytes(property.value(), world);
                        }
                    }
                }
                yield bytes;
            }
            default -> 0;
        };
    }

    /** Applies Toast's checked-list producer limit to a newly constructed value. */
    public static BuiltinResult enforceListValueLimit(MooValue value, WorldTxn world) {
        long maximumBytes = DEFAULT_MAX_LIST_VALUE_BYTES;
        boolean catchable = false;
        MooValue serverOptions = world.readObjectProperty(0, "server_options").orElse(null);
        if (serverOptions instanceof ObjectValue options) {
            if (world.readObjectProperty(options.value(), "max_list_value_bytes").orElse(null)
                    instanceof IntegerValue configured) {
                long requested = configured.value();
                maximumBytes =
                        requested <= 0 || requested > MAX_LIST_VALUE_BYTES_LIMIT
                                ? MAX_LIST_VALUE_BYTES_LIMIT
                                : Math.max(MIN_LIST_VALUE_BYTES_LIMIT, requested);
            }
            catchable =
                    world.readObjectProperty(options.value(), "max_concat_catchable")
                            .map(MooValue::isTruthy)
                            .orElse(false);
        }
        if (valueBytes(value, world) <= maximumBytes) {
            return BuiltinResult.value(value);
        }
        return catchable
                ? BuiltinResult.error(ErrorValue.E_QUOTA)
                : new BuiltinResult.SecondsAbort();
    }

  /** Checks the configured string limit before the VM allocates a concatenation. */
  public static Optional<BuiltinResult> stringLengthError(long length, WorldTxn world) {
    long maximumLength = 64_537_861L;
    boolean catchable = false;
    MooValue serverOptions = world.readObjectProperty(0, "server_options").orElse(null);
    if (serverOptions instanceof ObjectValue options) {
      if (world.readObjectProperty(options.value(), "max_string_concat").orElse(null)
          instanceof IntegerValue configured) {
        maximumLength =
            configured.value() <= 0
                ? Long.MAX_VALUE
                : Math.max(1_021L, Math.min(2_147_482_626L, configured.value()));
      }
      catchable =
          world.readObjectProperty(options.value(), "max_concat_catchable")
              .map(MooValue::isTruthy)
              .orElse(false);
    }
    if (length > maximumLength) {
      return Optional.of(catchable
          ? BuiltinResult.error(ErrorValue.E_QUOTA)
          : new BuiltinResult.SecondsAbort());
    }
    return Optional.empty();
  }
}
