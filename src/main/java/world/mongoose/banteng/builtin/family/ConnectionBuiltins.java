package world.mongoose.banteng.builtin.family;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.LongBinaryOperator;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinCall;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.builtin.BuiltinPermissionRule;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.builtin.ConnectionRegistryAccess;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldTxn;

/** Owns builtin registrations and implementations for this family. */
public final class ConnectionBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
    private static final Set<ArgType> STRING = Set.of(ArgType.STRING);
    private static final Set<ArgType> OBJECT = Set.of(ArgType.OBJECT);

    private ConnectionBuiltins() {
    }

    public static void registerSwitchPlayer(List<BuiltinSpec> entries, Optional<ListenerControl> listenerControl) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("switch_player", List.of(new CallShape(List.of(OBJECT, OBJECT), List.of(INTEGER), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(ConnectionBuiltins::switchPlayer);
    }

    public static void registerInventory(List<BuiltinSpec> entries, Optional<ListenerControl> listenerControl) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("read", List.of(new CallShape(List.of(), List.of(OBJECT, ANY), Optional.empty()))).implementation(call -> {
            if (call.arguments().isEmpty() && !BuiltinPermissionRule.WIZARD_ONLY.allows(call.world(), call.programmer())) {
                return BuiltinResult.error(ErrorValue.E_PERM);
            }
            if (!call.arguments().isEmpty() && !BuiltinPermissionRule.WIZARD_ONLY.allows(call.world(), call.programmer())) {
                WorldObject target = call.world().object(((ObjectValue) call.arguments().getFirst()).value()).orElse(null);
                if (target == null || target.owner() != call.programmer()) {
                    return BuiltinResult.error(ErrorValue.E_PERM);
                }
            }
            return call.hosts().read().invoke(call);
        });
        registrar.builtin("connected_players", List.of(new CallShape(List.of(), List.of(ANY), Optional.empty()))).implementation(call ->
                BuiltinResult.value(
                        new ListValue(
                                connections(call.hosts())
                                        .connectedPlayers(
                                                !call.arguments().isEmpty()
                                                        && call.arguments().getFirst().isTruthy())
                                        .stream()
                                        .map(ObjectValue::new)
                                        .map(MooValue.class::cast)
                                        .toList())));
        registrar.builtin("idle_seconds", List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty()))).implementation(call -> call.hosts().idleSeconds().invoke(call));
        registrar.builtin("connected_seconds", List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty()))).implementation(call -> call.hosts().connectedSeconds().invoke(call));
        registrar.builtin("buffered_output_length", List.of(new CallShape(List.of(), List.of(OBJECT), Optional.empty()))).implementation(call ->
                bufferedOutputLength(listenerControl, call.hosts(),
                        call.arguments(),
                        call.world(),
                        call.programmer(),
                        call.stagedBufferedOutputLength()));
        registrar.builtin("boot_player", List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty()))).implementation(call -> bootPlayer(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("connection_info", List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty()))).implementation(call -> connectionInfo(call.hosts(), call.arguments(), call.world(), call.programmer()));
        registrar.builtin("connection_name", List.of(new CallShape(List.of(OBJECT), List.of(INTEGER), Optional.empty()))).implementation(call -> connectionName(call.hosts(), call.arguments(), call.world(), call.programmer()));
    }

    public static void registerOptionsAndListeners(List<BuiltinSpec> entries, Optional<ListenerControl> listenerControl) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("connection_name_lookup", List.of(new CallShape(List.of(OBJECT), List.of(ANY), Optional.empty()))).implementation(ConnectionBuiltins::connectionNameLookup);
        registrar.builtin("connection_options", List.of(new CallShape(List.of(OBJECT), List.of(STRING), Optional.empty()))).implementation(call -> call.hosts().connectionOptions().invoke(call));
        registrar.builtin("output_delimiters", List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty()))).implementation(call -> call.hosts().outputDelimiters().invoke(call));
        registrar.builtin("set_connection_option", List.of(new CallShape(List.of(OBJECT, STRING, ANY), List.of(), Optional.empty()))).implementation(call -> setConnectionOption(call.hosts(), call.arguments(), call.world(), call.programmer()));
        registrar.builtin("flush_input", List.of(new CallShape(List.of(OBJECT), List.of(ANY), Optional.empty()))).implementation(call -> call.hosts().flushInput().invoke(call));
        registrar.builtin("force_input", List.of(new CallShape(List.of(OBJECT, STRING), List.of(ANY), Optional.empty()))).implementation(call -> forceInput(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("listen", List.of(
                new CallShape(
                        List.of(OBJECT, ANY), List.of(Set.of(ArgType.MAP)), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> listen(listenerControl, call.arguments(), call.world()));
        registrar.builtin("listeners", List.of(new CallShape(List.of(), List.of(ANY), Optional.empty()))).implementation(call -> listeners(listenerControl, call.arguments()));
        registrar.builtin("unlisten", List.of(new CallShape(List.of(ANY), List.of(ANY), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> unlisten(listenerControl, call.arguments()));
        registrar.builtin("open_network_connection", List.of(
                new CallShape(
                        List.of(STRING, INTEGER),
                        List.of(Set.of(ArgType.MAP)),
                        Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> openNetworkConnection(listenerControl, call));
    }

    public static void registerNotify(List<BuiltinSpec> entries, Optional<ListenerControl> listenerControl) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("notify", List.of(new CallShape(List.of(OBJECT, STRING), List.of(ANY, ANY), Optional.empty()))).implementation(call -> notifyLine(call.hosts(), call.arguments(), call.world(), call.programmer()));
    }

    private static ConnectionRegistryAccess connections(BuiltinHosts invocationHosts) {
        return Objects.requireNonNull(invocationHosts.connections().get(), "hosts.connections().get()");
    }

    private static BuiltinResult connectionInfo(BuiltinHosts invocationHosts,
                                         List<MooValue> arguments, WorldTxn world, long programmer) {
        long target = ((ObjectValue) arguments.getFirst()).value();
        Optional<MapValue> info = connections(invocationHosts).connectionInfo(target);
        if (info.isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        if (target != programmer && !world.isWizard(programmer)) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        return BuiltinResult.value(info.orElseThrow());
    }

    private static BuiltinResult bufferedOutputLength(Optional<ListenerControl> listenerControl, BuiltinHosts invocationHosts,
                                               List<MooValue> arguments,
                                               WorldTxn world,
                                               long programmer,
                                               LongBinaryOperator stagedBufferedOutputLength) {
        if (arguments.isEmpty()) {
            return BuiltinResult.value(new IntegerValue(DEFAULT_MAX_QUEUED_OUTPUT));
        }
        long target = ((ObjectValue) arguments.getFirst()).value();
        OptionalLong connectionId = connections(invocationHosts).connectionId(target);
        if (connectionId.isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        if (programmer != target && !world.isWizard(programmer)) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        long queuedBytes =
                listenerControl
                        .map(control -> control.bufferedOutputLength(connectionId.orElseThrow()))
                        .orElse(0L);
        return BuiltinResult.value(
                new IntegerValue(
                        stagedBufferedOutputLength.applyAsLong(
                                connectionId.orElseThrow(), queuedBytes)));
    }

    private static BuiltinResult connectionName(BuiltinHosts invocationHosts,
                                         List<MooValue> arguments, WorldTxn world, long programmer) {
        long target = ((ObjectValue) arguments.getFirst()).value();
        if (target != programmer && !world.isWizard(programmer)) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        MapValue info = connections(invocationHosts).connectionInfo(target).orElse(null);
        if (info == null) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        MooValue destinationAddress = info.get(StringValue.of("destination_address")).orElse(null);
        MooValue destinationIp = info.get(StringValue.of("destination_ip")).orElse(null);
        if (!(destinationAddress instanceof StringValue address)
                || !(destinationIp instanceof StringValue ip)) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        if (arguments.size() == 1) {
            return BuiltinResult.value(address);
        }
        if (((IntegerValue) arguments.get(1)).value() == 1) {
            return BuiltinResult.value(ip);
        }
        MooValue sourcePort = info.get(StringValue.of("source_port")).orElse(null);
        MooValue destinationPort = info.get(StringValue.of("destination_port")).orElse(null);
        MooValue outbound = info.get(StringValue.of("outbound")).orElse(null);
        if (!(sourcePort instanceof IntegerValue source)
                || !(destinationPort instanceof IntegerValue destination)
                || outbound == null) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        return BuiltinResult.value(
                StringValue.of(
                        "port %d %s %s [%s], port %d"
                                .formatted(
                                        source.value(),
                                        outbound.isTruthy() ? "to" : "from",
                                        address.text(),
                                        ip.text(),
                                        destination.value())));
    }

    private static BuiltinResult connectionNameLookup(BuiltinCall call) {
        long target = ((ObjectValue) call.arguments().getFirst()).value();
        if (target != call.programmer()
                && !BuiltinPermissionRule.WIZARD_ONLY.allows(call.world(), call.programmer())) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        if (connections(call.hosts()).connectionInfo(target).isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        return call.hosts().connectionNameLookup().invoke(call);
    }

    private static BuiltinResult bootPlayer(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        long target = ((ObjectValue) arguments.getFirst()).value();
        if (target != programmer && !world.isWizard(programmer)) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        return new BuiltinResult.BootPlayer(target);
    }

    private static BuiltinResult setConnectionOption(BuiltinHosts invocationHosts,
                                              List<MooValue> arguments, WorldTxn world, long programmer) {
        long target = ((ObjectValue) arguments.get(0)).value();
        if (target != programmer && !world.isWizard(programmer)) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        if (connections(invocationHosts).connectionInfo(target).isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        ConnectionOption option =
                switch (((StringValue) arguments.get(1)).text().toLowerCase(Locale.ROOT)) {
                    case "hold-input" -> ConnectionOption.HOLD_INPUT;
                    case "flush-command" -> ConnectionOption.FLUSH_COMMAND;
                    case "disable-oob" -> ConnectionOption.DISABLE_OOB;
                    case "binary" -> ConnectionOption.BINARY;
                    case "keep-alive" -> ConnectionOption.KEEP_ALIVE;
                    case "intrinsic-commands" -> ConnectionOption.INTRINSIC_COMMANDS;
                    default -> null;
                };
        if (option == null) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        MooValue value = arguments.get(2);
        if (option == ConnectionOption.KEEP_ALIVE
                && !(value instanceof IntegerValue || value instanceof MapValue)) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        if (option == ConnectionOption.INTRINSIC_COMMANDS) {
            Optional<ListValue> normalized = normalizeIntrinsicCommands(value);
            if (normalized.isEmpty()) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
            value = normalized.orElseThrow();
            connections(invocationHosts).setIntrinsicCommands(target, (ListValue) value);
        }
        return invocationHosts.setConnectionOption().set(target, option, value);
    }

    private static Optional<ListValue> normalizeIntrinsicCommands(MooValue value) {
        List<String> enabled = new ArrayList<>();
        if (value instanceof IntegerValue integer) {
            if (integer.isTruthy()) {
                enabled.addAll(List.of(".program", "PREFIX", "SUFFIX", "OUTPUTPREFIX", "OUTPUTSUFFIX"));
            }
        } else if (value instanceof ListValue list) {
            for (MooValue element : list.elements()) {
                if (!(element instanceof StringValue string)) {
                    return Optional.empty();
                }
                String requested = string.text();
                String canonical =
                        List.of(".program", "PREFIX", "SUFFIX", "OUTPUTPREFIX", "OUTPUTSUFFIX").stream()
                                .filter(name -> name.equalsIgnoreCase(requested))
                                .findFirst()
                                .orElse(null);
                if (canonical == null) {
                    return Optional.empty();
                }
                if (!enabled.contains(canonical)) {
                    enabled.add(canonical);
                }
            }
        } else {
            return Optional.empty();
        }
        List<MooValue> commands = new ArrayList<>();
        for (String canonical : List.of(".program", "PREFIX", "SUFFIX", "OUTPUTPREFIX", "OUTPUTSUFFIX")) {
            if (enabled.contains(canonical)) {
                commands.add(StringValue.of(canonical));
            }
        }
        return Optional.of(new ListValue(commands));
    }

    private static BuiltinResult forceInput(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        long target = ((ObjectValue) arguments.get(0)).value();
        if (target != programmer && !world.isWizard(programmer)) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        return new BuiltinResult.ForceInput(
                target, ((StringValue) arguments.get(1)).text());
    }

    private static BuiltinResult listen(Optional<ListenerControl> listenerControl, List<MooValue> arguments, WorldTxn world) {
        ObjectValue handler = (ObjectValue) arguments.getFirst();
        if (!(arguments.get(1) instanceof IntegerValue descriptor)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        if (world.object(handler.value()).isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        boolean printMessages =
                arguments.size() == 3
                        && ((MapValue) arguments.get(2))
                        .get(StringValue.of("print-messages"))
                        .map(MooValue::isTruthy)
                        .orElse(false);
        boolean ipv6 =
                arguments.size() == 3
                        && ((MapValue) arguments.get(2))
                        .get(StringValue.of("ipv6"))
                        .map(MooValue::isTruthy)
                        .orElse(false);
        String interfaceAddress =
                arguments.size() == 3
                        ? ((MapValue) arguments.get(2))
                        .get(StringValue.of("interface"))
                        .filter(StringValue.class::isInstance)
                        .map(StringValue.class::cast)
                        .map(StringValue::text)
                        .orElse("")
                        : "";
        if (listenerControl.isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        final int port;
        try {
            port = Math.toIntExact(descriptor.value());
        } catch (ArithmeticException invalid) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        try {
            return BuiltinResult.value(
                    new IntegerValue(
                            listenerControl
                                    .orElseThrow()
                                    .listen(handler.value(), port, ipv6, printMessages, interfaceAddress)));
        } catch (IllegalArgumentException invalid) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        } catch (IOException bindFailure) {
            return BuiltinResult.error(ErrorValue.E_QUOTA);
        }
    }

    private static BuiltinResult listeners(Optional<ListenerControl> listenerControl, List<MooValue> arguments) {
        if (listenerControl.isEmpty()) {
            return BuiltinResult.value(new ListValue(List.of()));
        }
        MooValue filter = arguments.isEmpty() ? null : arguments.getFirst();
        List<MooValue> values = new ArrayList<>();
        for (ListenerDescription listener : listenerControl.orElseThrow().listeners()) {
            if (filter instanceof ObjectValue object && object.value() != listener.handler()) {
                continue;
            }
            if (filter instanceof IntegerValue integer
                    && integer.value() != listener.description()
                    && integer.value() != listener.port()) {
                continue;
            }
            Map<MooValue, MooValue> fields = new LinkedHashMap<>();
            fields.put(StringValue.of("object"), new ObjectValue(listener.handler()));
            fields.put(StringValue.of("port"), new IntegerValue(listener.port()));
            fields.put(StringValue.of("ipv6"), new IntegerValue(listener.ipv6() ? 1 : 0));
            fields.put(StringValue.of("print-messages"), new IntegerValue(listener.printMessages() ? 1 : 0));
            fields.put(StringValue.of("interface"), StringValue.of(listener.interfaceAddress()));
            values.add(new MapValue(fields));
        }
        return BuiltinResult.value(new ListValue(values));
    }

    private static BuiltinResult unlisten(Optional<ListenerControl> listenerControl, List<MooValue> arguments) {
        if (!(arguments.getFirst() instanceof IntegerValue descriptor)) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        boolean ipv6 = false;
        if (arguments.size() == 2) {
            if (!(arguments.get(1) instanceof IntegerValue flag)) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
            ipv6 = flag.isTruthy();
        }
        if (listenerControl.isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        final int description;
        try {
            description = Math.toIntExact(descriptor.value());
        } catch (ArithmeticException invalid) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        try {
            return listenerControl.orElseThrow().unlisten(description, ipv6)
                    ? BuiltinResult.value(new IntegerValue(0))
                    : BuiltinResult.error(ErrorValue.E_INVARG);
        } catch (IllegalArgumentException invalid) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
    }

    private static BuiltinResult openNetworkConnection(Optional<ListenerControl> listenerControl, BuiltinCall call) {
        List<MooValue> arguments = call.arguments();
        if (listenerControl.isEmpty()) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        String host = ((StringValue) arguments.get(0)).text();
        long rawPort = ((IntegerValue) arguments.get(1)).value();
        boolean ipv6 = false;
        long listenerHandler = 0;
        if (arguments.size() == 3) {
            MapValue options = (MapValue) arguments.get(2);
            ipv6 = options.get(StringValue.of("ipv6")).map(MooValue::isTruthy).orElse(false);
            MooValue listener = options.get(StringValue.of("listener")).orElse(null);
            if (listener != null && !(listener instanceof ObjectValue)) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
            if (listener instanceof ObjectValue object) {
                listenerHandler = object.value();
            }
        }
        final int port;
        try {
            port = Math.toIntExact(rawPort);
        } catch (ArithmeticException invalid) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        try {
            return BuiltinResult.value(
                    new ObjectValue(
                            listenerControl
                                    .orElseThrow()
                                    .openNetworkConnection(host, port, ipv6, listenerHandler, call.hosts().registerOutboundConnection())));
        } catch (IllegalArgumentException invalid) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        } catch (IOException unavailable) {
            return BuiltinResult.error(ErrorValue.E_QUOTA);
        }
    }

    private static BuiltinResult switchPlayer(BuiltinCall call) {
        long oldPlayer = ((ObjectValue) call.arguments().getFirst()).value();
        long newPlayer = ((ObjectValue) call.arguments().get(1)).value();
        if (oldPlayer == newPlayer
                || connections(call.hosts()).connectionId(oldPlayer).isEmpty()
                || !call.world().players().contains(newPlayer)) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        return call.hosts().switchPlayer().invoke(call);
    }

    private static BuiltinResult notifyLine(BuiltinHosts invocationHosts,
                                     List<MooValue> arguments, WorldTxn world, long programmer) {
        long target = ((ObjectValue) arguments.getFirst()).value();
        if (target != programmer && !world.isWizard(programmer)) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        StringValue line = (StringValue) arguments.get(1);
        boolean noFlush = arguments.size() > 2 && arguments.get(2).isTruthy();
        boolean noNewline = arguments.size() > 3 && arguments.get(3).isTruthy();
        if (target == programmer && !noFlush && !noNewline) {
            return new BuiltinResult.Output(line.text());
        }
        OptionalLong connectionId = connections(invocationHosts).connectionId(target);
        if (connectionId.isEmpty()) {
            return BuiltinResult.value(new IntegerValue(1));
        }
        return new BuiltinResult.Notify(
                connectionId.orElseThrow(), line.text(), noFlush, noNewline);
    }
    private static final int DEFAULT_MAX_QUEUED_OUTPUT = 65_536;

    /** Concrete host capability required by the MOO listener builtins. */
    public interface ListenerControl {
        /** Binds and starts one listener, returning its integer descriptor. */
        int listen(
                long handler, int description, boolean ipv6, boolean printMessages, String interfaceAddress)
                throws IOException;

        /** Returns the stable inventory of active listeners. */
        List<ListenerDescription> listeners();

        /** Closes one dynamic listener selected by its integer descriptor. */
        boolean unlisten(int description, boolean ipv6);

        /** Opens and registers one outbound network connection. */
        long openNetworkConnection(
                String host, int port, boolean ipv6, long listenerHandler,
                BuiltinHosts.OutboundConnectionRegistrar registration)
                throws IOException;

        /** Writes ordered lines to one accepted connection selected by runtime ID. */
        void writeConnection(long connectionId, List<String> lines);

        /** Writes or queues one notification for a live connection. */
        void notifyConnection(
                long connectionId, String line, boolean noFlush, boolean noNewline);

        /** Writes final lines and closes one accepted connection selected by runtime ID. */
        void bootConnection(long connectionId, List<String> lines);

        /** Selects delimiter-free binary reads for one accepted connection. */
        void setConnectionBinary(long connectionId, boolean binary);

        /** Applies TCP keep-alive state after the owning runtime attempt commits. */
        default void setConnectionKeepAlive(long connectionId, KeepAliveOptions options) {
        }

        /** Returns the number of bytes currently queued for one live connection. */
        long bufferedOutputLength(long connectionId);

        /** Stops the production server after its committed shutdown checkpoint is published. */
        void shutdown();

        /** Terminates the production server with SIGABRT after its panic dump is published. */
        void panic();
    }

    /** One observable listener row returned by {@code listeners()}. */
    public record ListenerDescription(
            long handler,
            int description,
            int port,
            boolean ipv6,
            boolean printMessages,
            String interfaceAddress) {
        public ListenerDescription {
            Objects.requireNonNull(interfaceAddress, "interfaceAddress");
        }
    }

    /** One validated mutation of the closed connection-option surface. */
    public record ConnectionOptionRequest(long target, ConnectionOption option, MooValue value) {
    }

    /** One validated line to inject into a live connection's input stream. */
    public record ForcedInputRequest(long target, String line) {
    }

    /** One authorized notification captured for commit-time transport publication. */
    public record NotificationRequest(
            long connectionId, String line, boolean noFlush, boolean noNewline) {
    }

    /** The connection options authorized by the held-input slice. */
    public enum ConnectionOption {
        HOLD_INPUT,
        FLUSH_COMMAND,
        DISABLE_OOB,
        BINARY,
        KEEP_ALIVE,
        INTRINSIC_COMMANDS
    }

    /** Toast's observable TCP keep-alive state for one live connection. */
    public record KeepAliveOptions(boolean enabled, long idle, long interval, long count) {
        private static final KeepAliveOptions DEFAULT = new KeepAliveOptions(false, 300, 120, 5);

        /** Returns Toast's default socket keep-alive settings. */
        public static KeepAliveOptions defaults() {
            return DEFAULT;
        }

        /** Applies Toast's integer-or-map update contract over the current settings. */
        public KeepAliveOptions with(MooValue value) {
            if (value instanceof IntegerValue integer) {
                return new KeepAliveOptions(integer.isTruthy(), idle, interval, count);
            }
            MapValue map = (MapValue) value;
            return new KeepAliveOptions(
                    !map.entries().isEmpty(),
                    positiveOption(map, "idle", idle),
                    positiveOption(map, "interval", interval),
                    positiveOption(map, "count", count));
        }

        /** Returns the public connection-options map in Toast field order. */
        public MapValue toValue() {
            Map<MooValue, MooValue> values = new LinkedHashMap<>();
            values.put(StringValue.of("enabled"), new IntegerValue(enabled ? 1 : 0));
            values.put(StringValue.of("idle"), new IntegerValue(idle));
            values.put(StringValue.of("interval"), new IntegerValue(interval));
            values.put(StringValue.of("count"), new IntegerValue(count));
            return new MapValue(values);
        }

        private static long positiveOption(MapValue map, String name, long fallback) {
            MooValue value = map.get(StringValue.of(name)).orElse(null);
            return value instanceof IntegerValue integer && integer.value() > 0
                    ? integer.value()
                    : fallback;
        }
    }
}
