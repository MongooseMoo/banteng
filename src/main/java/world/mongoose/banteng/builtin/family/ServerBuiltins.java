package world.mongoose.banteng.builtin.family;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinCall;
import world.mongoose.banteng.builtin.BuiltinCatalog;
import world.mongoose.banteng.builtin.BuiltinPermissionRule;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;

import static world.mongoose.banteng.builtin.ValueLimits.valueBytes;
import world.mongoose.banteng.host.Libc;
import world.mongoose.banteng.host.NativeCallException;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.PropertyHolder;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

/** Owns builtin registrations and implementations for this family. */
public final class ServerBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> STRING = Set.of(ArgType.STRING);

    private ServerBuiltins() {
    }

    public static void registerValueSizes(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("value_bytes", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call ->
                BuiltinResult.value(new IntegerValue(valueBytes(call.arguments().getFirst(), call.world()))));
        registrar.builtin("object_bytes", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call -> objectBytes(call.arguments().getFirst(), call.world(), call.programmer()));
    }

    public static void registerVersionAndFunctions(List<BuiltinSpec> entries, BuiltinCatalog catalog) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("server_version", List.of(new CallShape(List.of(), List.of(ANY), Optional.empty()))).implementation(call -> serverVersion(call.arguments()));
        registrar.builtin("function_info", List.of(new CallShape(List.of(), List.of(STRING), Optional.empty()))).implementation(call -> functionInfo(call.arguments(), catalog));
    }

    public static void registerDiagnostics(List<BuiltinSpec> entries, BuiltinCatalog catalog, ServerLog serverLog) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("server_log", List.of(new CallShape(List.of(STRING), List.of(ANY), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> serverLog(call.arguments(), serverLog));
        registrar.builtin("log_cache_stats", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> logCacheStats(call.world(), serverLog));
        registrar.builtin("verb_cache_stats", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> verbCacheStats(call.world()));
        registrar.builtin("usage", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> usage());
        registrar.builtin("memory_usage", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).implementation(call -> memoryUsage());
        registrar.builtin("load_server_options", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> {
            catalog.reloadProtectedFunctions(call.world());
            return BuiltinResult.value(new IntegerValue(0));
        });
        registrar.builtin("run_gc", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> BuiltinResult.value(new IntegerValue(0)));
        registrar.builtin("gc_stats", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> gcStats());
    }

    public static void registerShutdownAndDatabase(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("shutdown", List.of(new CallShape(List.of(), List.of(STRING, ANY), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> shutdown(call.arguments()));
        registrar.builtin("db_disk_size", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).implementation(call -> call.hosts().dbDiskSize().invoke(call));
        registrar.builtin("dump_database", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(ServerBuiltins::dumpDatabase);
    }

    private static BuiltinResult functionInfo(List<MooValue> arguments, BuiltinCatalog catalog) {
        if (arguments.isEmpty()) {
            return BuiltinResult.value(
                    new ListValue(
                            catalog.manifest().stream()
                                    .map(ServerBuiltins::functionInfoDescription)
                                    .map(MooValue.class::cast)
                                    .toList()));
        }
        StringValue requestedName = (StringValue) arguments.getFirst();
        BuiltinSpec requested = catalog.spec(requestedName.text()).orElse(null);
        return requested == null
                ? BuiltinResult.error(ErrorValue.E_INVARG)
                : BuiltinResult.value(functionInfoDescription(requested));
    }

    private static BuiltinResult serverVersion(List<MooValue> arguments) {
        if (arguments.isEmpty()) {
            return BuiltinResult.value(StringValue.of(SERVER_VERSION));
        }
        MooValue detail = arguments.getFirst();
        ListValue metadata = serverVersionMetadata();
        if (!(detail instanceof StringValue path) || path.text().isEmpty()) {
            return BuiltinResult.value(metadata);
        }
        Optional<MooValue> value = versionPath(metadata, path.text());
        return value.isEmpty() ? BuiltinResult.error(ErrorValue.E_INVARG) : BuiltinResult.value(value.orElseThrow());
    }

    private static ListValue serverVersionMetadata() {
        return new ListValue(
                List.of(
                        versionPair("major", new IntegerValue(0)),
                        versionPair("minor", new IntegerValue(1)),
                        versionPair("release", new IntegerValue(0)),
                        versionPair("ext", StringValue.of("-SNAPSHOT")),
                        versionPair("string", StringValue.of(SERVER_VERSION)),
                        versionPair("os", StringValue.of(System.getProperty("os.name"))),
                        versionPair("features", new ListValue(List.of())),
                        versionPair(
                                "options",
                                new ListValue(
                                        List.of(
                                                versionPair("OUTBOUND_NETWORK", StringValue.of("ON")),
                                                versionPair("PROMOTE_NUMBERS", StringValue.of("OFF"))))),
                        versionPair("source", new ListValue(List.of(versionPair("vcs", StringValue.of("git")))))));
    }

    private static ListValue versionPair(String name, MooValue value) {
        return new ListValue(List.of(StringValue.of(name), value));
    }

    private static Optional<MooValue> versionPath(ListValue tree, String path) {
        ListValue current = tree;
        int start = 0;
        while (true) {
            int slash = path.indexOf('/', start);
            String component = slash < 0 ? path.substring(start) : path.substring(start, slash);
            MooValue found = null;
            for (MooValue item : current.elements()) {
                if (item instanceof ListValue pair
                        && pair.size() == 2
                        && pair.elements().getFirst() instanceof StringValue key
                        && key.text().equals(component)) {
                    found = pair.elements().get(1);
                    break;
                }
            }
            if (found == null) {
                return Optional.empty();
            }
            if (slash < 0) {
                return Optional.of(found);
            }
            if (!(found instanceof ListValue nested) || slash == path.length() - 1) {
                return Optional.empty();
            }
            current = nested;
            start = slash + 1;
        }
    }

    private static ListValue functionInfoDescription(BuiltinSpec spec) {
        CallShape shape = spec.callShapes().getFirst();
        List<MooValue> argumentTypes = new ArrayList<>();
        shape.required().forEach(types -> argumentTypes.add(new IntegerValue(typeCode(types))));
        shape.optional().forEach(types -> argumentTypes.add(new IntegerValue(typeCode(types))));
        int maximumArguments =
                shape.variadic().isPresent()
                        ? -1
                        : shape.required().size() + shape.optional().size();
        return new ListValue(
                List.of(
                        StringValue.of(spec.name()),
                        new IntegerValue(shape.required().size()),
                        new IntegerValue(maximumArguments),
                        new ListValue(argumentTypes)));
    }

    private static int typeCode(Set<ArgType> types) {
        if (types.size() != 1) {
            return -1;
        }
        return switch (types.iterator().next()) {
            case ANY -> -1;
            case NUMBER -> -2;
            case INTEGER -> 0;
            case OBJECT -> 1;
            case STRING -> 2;
            case ERROR -> 3;
            case LIST -> 4;
            case FLOAT -> 9;
            case MAP -> 10;
            case WAIF -> 13;
        };
    }

    private static BuiltinResult dumpDatabase(BuiltinCall call) {
        return new BuiltinResult.Checkpoint();
    }

    private static BuiltinResult serverLog(List<MooValue> arguments, ServerLog log) {
        String message = ((StringValue) arguments.getFirst()).text();
        log.info("> " + message);
        return BuiltinResult.value(new IntegerValue(0));
    }

    private static BuiltinResult logCacheStats(WorldTxn world, ServerLog log) {
        WorldTxn.VerbCacheStats stats = world.verbCacheStats();
        log.info(
                String.format(
                        Locale.ROOT,
                        "Verb cache stat summary: %d hits, %d misses, %d generations",
                        stats.hits(),
                        stats.misses(),
                        stats.generation()));
        log.info("Depth   Count");
        for (int depth = 0; depth < stats.histogram().size(); depth++) {
            log.info(
                    String.format(Locale.ROOT, "%-5d   %-5d", depth, stats.histogram().get(depth)));
        }
        log.info("---");
        return BuiltinResult.value(new IntegerValue(0));
    }

    private static BuiltinResult verbCacheStats(WorldTxn world) {
        WorldTxn.VerbCacheStats stats = world.verbCacheStats();
        List<MooValue> histogram =
                stats.histogram().stream()
                        .map(Integer::longValue)
                        .map(IntegerValue::new)
                        .map(MooValue.class::cast)
                        .toList();
        return BuiltinResult.value(
                new ListValue(
                        List.of(
                                new IntegerValue(stats.hits()),
                                new IntegerValue(stats.negativeHits()),
                                new IntegerValue(stats.misses()),
                                new IntegerValue(stats.generation()),
                                new ListValue(histogram))));
    }

    private static BuiltinResult memoryUsage() {
        final double[] fields;
        try {
            fields = Libc.memoryUsage();
        } catch (IOException exception) {
            return BuiltinResult.error(ErrorValue.E_FILE);
        } catch (IllegalArgumentException exception) {
            return BuiltinResult.error(ErrorValue.E_NACC);
        }
        List<MooValue> result = new ArrayList<>(5);
        for (double field : fields) result.add(new FloatValue(field));
        return BuiltinResult.value(new ListValue(result));
    }

    private static BuiltinResult usage() {
        List<MooValue> loads = new ArrayList<>(3);
        for (long load : Libc.loadAverages()) loads.add(new IntegerValue(load));

        long[] resource = new long[11];
        try {
            resource = Libc.resourceUsage();
        } catch (NativeCallException failure) {
            Arrays.fill(resource, 0);
        }
        List<MooValue> result = new ArrayList<>(10);
        result.add(new ListValue(loads));
        result.add(new FloatValue(resource[0] + resource[1] / 1_000_000.0));
        result.add(new FloatValue(resource[2] + resource[3] / 1_000_000.0));
        for (int index = 4; index < resource.length; index++) {
            result.add(new IntegerValue(resource[index]));
        }
        return BuiltinResult.value(new ListValue(result));
    }

    private static BuiltinResult shutdown(List<MooValue> arguments) {
        if (arguments.size() == 2 && arguments.get(1).isTruthy()) {
            return new BuiltinResult.Panic(((StringValue) arguments.getFirst()).text());
        }
        return new BuiltinResult.Shutdown();
    }

    private static BuiltinResult objectBytes(MooValue target, WorldTxn world, long programmer) {
        PropertyHolder object;
        if (target instanceof ObjectValue permanent) {
            object = world.object(permanent.value()).orElse(null);
        } else if (target instanceof AnonymousObjectValue anonymous) {
            object = world.anonymousObject(anonymous).orElse(null);
        } else {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        if (object == null) return BuiltinResult.error(ErrorValue.E_INVIND);
        if (!world.isWizard(programmer)) return BuiltinResult.error(ErrorValue.E_PERM);

        // Logical 64-bit object layout, consistent with value_bytes. Banteng owns
        // source text rather than Toast's Program allocation for each stored verb.
        long bytes = 160L + object.name().length() + 1;
        for (WorldVerb verb : object.verbs()) {
            bytes += 40L + verb.names().length() + 1 + verb.programSource().length() + 1;
        }
        for (WorldProperty property : object.properties()) {
            if (property.defined()) bytes += 16L + property.name().length() + 1;
            bytes += 16L + (property.clear() ? 16 : valueBytes(property.value(), world));
        }
        return BuiltinResult.value(new IntegerValue(bytes));
    }

    private static BuiltinResult gcStats() {
        Map<MooValue, MooValue> colors = new LinkedHashMap<>();
        for (String color :
        List.of("green", "yellow", "black", "gray", "white", "purple", "pink")) {
            colors.put(StringValue.of(color), new IntegerValue(0));
        }
        return BuiltinResult.value(new MapValue(colors));
    }
    private static final String SERVER_VERSION = "0.1.0-SNAPSHOT";
}
