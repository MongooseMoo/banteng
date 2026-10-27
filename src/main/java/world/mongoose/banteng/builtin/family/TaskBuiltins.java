package world.mongoose.banteng.builtin.family;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import world.mongoose.banteng.builtin.family.ConversionBuiltins;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.FloatValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.SuspendedActivation;

/** Owns builtin registrations and implementations for this family. */
public final class TaskBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
    private static final Set<ArgType> NUMBER = Set.of(ArgType.NUMBER);
    private static final Set<ArgType> STRING = Set.of(ArgType.STRING);
    private static final Set<ArgType> OBJECT = Set.of(ArgType.OBJECT);

    private TaskBuiltins() {
    }

    public static void registerGroup0(List<BuiltinSpec> entries, BuiltinCatalog catalog) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("task_local", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> BuiltinResult.value(call.taskLocal()));
        registrar.builtin("set_task_local", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> new BuiltinResult.TaskLocal(call.arguments().getFirst()));
    }

    public static void registerGroup1(List<BuiltinSpec> entries, BuiltinCatalog catalog) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("raise", List.of(new CallShape(List.of(ANY), List.of(STRING, ANY), Optional.empty()))).implementation(call -> raise(call.arguments()));
    }

    public static void registerGroup2(List<BuiltinSpec> entries, BuiltinCatalog catalog) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("pass", List.of(new CallShape(List.of(), List.of(), Optional.of(ANY)))).implementation(call -> new BuiltinResult.Pass(new ListValue(call.arguments())));
        registrar.builtin("caller_perms", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).implementation(call ->
                BuiltinResult.value(new ObjectValue(call.callers().apply(false).size() == 0 ? -1 : call.callerProgrammer())));
        registrar.builtin("callers", List.of(new CallShape(List.of(), List.of(ANY), Optional.empty()))).implementation(call -> callers(call.callers().apply(
                        !call.arguments().isEmpty() && call.arguments().getFirst().isTruthy()),
                call.world(), call.programmer()));
        registrar.builtin("call_function", List.of(new CallShape(List.of(STRING), List.of(), Optional.of(ANY)))).irrevocable().implementation(call -> callFunction(call, catalog));
        registrar.builtin("queue_info", List.of(new CallShape(List.of(), List.of(OBJECT), Optional.empty()))).implementation(call -> call.hosts().queueInfo().invoke(call));
        registrar.builtin("queued_tasks", List.of(new CallShape(List.of(), List.of(INTEGER, INTEGER), Optional.empty()))).irrevocable().implementation(call -> call.hosts().queuedTasks().invoke(call));
        registrar.builtin("task_stack", List.of(new CallShape(List.of(INTEGER), List.of(ANY, ANY), Optional.empty()))).irrevocable().implementation(call -> call.hosts().taskStack().invoke(call));
        registrar.builtin("kill_task", List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty()))).irrevocable().implementation(call -> call.hosts().killTask().invoke(call));
        registrar.builtin("thread_pool", List.of(
                new CallShape(
                        List.of(STRING, STRING), List.of(INTEGER), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(TaskBuiltins::configureThreadPool);
        registrar.builtin("threads", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).irrevocable().implementation(call -> call.hosts().threads().invoke(call));
    }

    public static void registerGroup3(List<BuiltinSpec> entries, BuiltinCatalog catalog) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("task_perms", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).implementation(call -> BuiltinResult.value(new ObjectValue(call.programmer())));
        registrar.builtin("task_id", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).implementation(call -> BuiltinResult.value(new IntegerValue(call.taskId())));
        registrar.builtin("ticks_left", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).implementation(call -> BuiltinResult.value(new IntegerValue(call.remainingTicks())));
        registrar.builtin("seconds_left", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).implementation(call -> BuiltinResult.value(new IntegerValue(call.remainingSeconds())));
        registrar.builtin("set_task_perms", List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty()))).implementation(call -> setTaskPerms(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("set_thread_mode", List.of(new CallShape(List.of(), List.of(INTEGER), Optional.empty()))).implementation(call -> setThreadMode(call.arguments(), call.threadMode()));
    }

    public static void registerGroup4(List<BuiltinSpec> entries, BuiltinCatalog catalog) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("eval", List.of(new CallShape(List.of(STRING), List.of(), Optional.of(ANY)))).permission(BuiltinPermissionRule.PROGRAMMER_ONLY).implementation(call -> dynamicEval(call.arguments(), catalog));
    }

    public static void registerGroup5(List<BuiltinSpec> entries, BuiltinCatalog catalog) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("suspend", List.of(new CallShape(List.of(), List.of(NUMBER), Optional.empty()))).implementation(call -> suspend(call.arguments()));
        registrar.builtin("resume", List.of(new CallShape(List.of(INTEGER), List.of(ANY), Optional.empty()))).irrevocable().implementation(call -> call.hosts().resumeTask().invoke(call));
        registrar.builtin("yin", List.of(
                new CallShape(
                        List.of(), List.of(NUMBER, INTEGER, INTEGER), Optional.empty()))).implementation(call -> yin(call.arguments(), call.world(), call.remainingTicks(), call.remainingSeconds()));
    }

    private static BuiltinResult configureThreadPool(BuiltinCall call) {
        StringValue function = (StringValue) call.arguments().get(0);
        StringValue pool = (StringValue) call.arguments().get(1);
        long requested =
                call.arguments().size() == 3 ? ((IntegerValue) call.arguments().get(2)).value() : 0;
        if (!pool.text().equals("MAIN")) {
            return BuiltinResult.raised(ErrorValue.E_INVARG, StringValue.of("Invalid thread pool"), pool);
        }
        if (!function.text().equals("INIT")) {
            return BuiltinResult.raised(ErrorValue.E_INVARG, StringValue.of("Invalid function"), function);
        }
        if (requested < 0 || requested > Integer.MAX_VALUE) {
            return BuiltinResult.raised(
                    ErrorValue.E_INVARG,
                    StringValue.of("Invalid number of threads"),
                    new IntegerValue(requested));
        }
        return call.hosts().threadPool().invoke(call);
    }

    private static BuiltinResult callFunction(BuiltinCall call, BuiltinCatalog catalog) {
        List<MooValue> arguments = call.arguments();
        String name = ((StringValue) arguments.getFirst()).text().toLowerCase(Locale.ROOT);
        BuiltinSpec target = catalog.spec(name).orElse(null);
        if (target == null) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        BuiltinResult result = catalog.invoke(target, new BuiltinCall(
                arguments.subList(1, arguments.size()), call.world(), call.programmer(),
                call.taskLocal(), call.taskId(), call.remainingTicks(), call.remainingSeconds(),
                call.receiver(), call.callerProgrammer(), call.callers(), call.threadMode(),
                call.stagedBufferedOutputLength(), call.hosts()));
        return switch (result) {
            case BuiltinResult.Initialize initialized -> new BuiltinResult.Initialize(initialized.created(),
                    initialized.arguments(), initialized.continuation().map(SuspendedActivation.CallFunctionContinuation::new));
            case BuiltinResult.DynamicEval evaluated -> new BuiltinResult.DynamicEval(evaluated.program(),
                    new SuspendedActivation.CallFunctionContinuation(evaluated.continuation()));
            case BuiltinResult.Move moved -> new BuiltinResult.Move(moved.object(), moved.destination(), moved.position(),
                    new SuspendedActivation.CallFunctionContinuation(moved.continuation()));
            case BuiltinResult.Recycle recycled -> new BuiltinResult.Recycle(recycled.object(),
                    new SuspendedActivation.CallFunctionContinuation(recycled.continuation()));
            default -> result;
        };
    }

    private static BuiltinResult suspend(List<MooValue> arguments) {
        if (arguments.isEmpty()) {
            return new BuiltinResult.Suspend(Double.POSITIVE_INFINITY);
        }
        MooValue delay = arguments.getFirst();
        double seconds =
                delay instanceof IntegerValue integer
                        ? integer.value()
                        : ((FloatValue) delay).value();
        return seconds < 0
                ? BuiltinResult.error(ErrorValue.E_INVARG)
                : new BuiltinResult.Suspend(seconds);
    }

    private static BuiltinResult yin(
            List<MooValue> arguments, WorldTxn world, long remainingTicks, long remainingSeconds) {
        if (arguments.isEmpty()) {
            return remainingTicks < 2_000 || remainingSeconds < 2
                    ? new BuiltinResult.Suspend(0)
                    : BuiltinResult.value(new IntegerValue(0));
        }
        double delaySeconds;
        MooValue delay = arguments.getFirst();
        delaySeconds =
                delay instanceof IntegerValue integer
                        ? integer.value()
                        : ((FloatValue) delay).value();
        long minimumTicks =
                arguments.size() >= 2 ? ((IntegerValue) arguments.get(1)).value() : 2_000;
        long minimumSeconds =
                arguments.size() >= 3 ? ((IntegerValue) arguments.get(2)).value() : 2;

        long foregroundTicks = 60_000;
        long foregroundSeconds = 5;
        MooValue serverOptions = world.readObjectProperty(0, "server_options").orElse(null);
        if (serverOptions instanceof ObjectValue options) {
            if (world.readObjectProperty(options.value(), "fg_ticks").orElse(null)
                    instanceof IntegerValue configuredTicks) {
                foregroundTicks = configuredTicks.value();
            }
            if (world.readObjectProperty(options.value(), "fg_seconds").orElse(null)
                    instanceof IntegerValue configuredSeconds) {
                foregroundSeconds = configuredSeconds.value();
            }
        }

        if (delaySeconds < 0
                || minimumTicks <= 0
                || minimumSeconds <= 0
                || minimumTicks >= foregroundTicks
                || minimumSeconds >= foregroundSeconds) {
            return BuiltinResult.error(ErrorValue.E_INVARG);
        }
        return remainingTicks < minimumTicks || remainingSeconds < minimumSeconds
                ? new BuiltinResult.Suspend(delaySeconds)
                : BuiltinResult.value(new IntegerValue(0));
    }

    private static BuiltinResult raise(List<MooValue> arguments) {
        MooValue code = arguments.getFirst();
        StringValue message =
                arguments.size() >= 2 ? (StringValue) arguments.get(1)
                        : (StringValue) ((BuiltinResult.Value) ConversionBuiltins.toStringValue(List.of(code))).value();
        MooValue value = arguments.size() >= 3 ? arguments.get(2) : new IntegerValue(0);
        return BuiltinResult.raised(code, message, value);
    }

    private static BuiltinResult setThreadMode(List<MooValue> arguments, boolean threadMode) {
        if (arguments.isEmpty()) {
            return BuiltinResult.value(new IntegerValue(threadMode ? 1 : 0));
        }
        return new BuiltinResult.ThreadMode(arguments.getFirst().isTruthy());
    }

    private static BuiltinResult callers(ListValue callers, WorldTxn world, long programmer) {
        List<MooValue> frames = new ArrayList<>(callers.size());
        for (MooValue value : callers.elements()) {
            if (!(value instanceof ListValue frame) || frame.size() < 4) {
                frames.add(value);
                continue;
            }
            List<MooValue> fields = new ArrayList<>(frame.elements());
            fields.set(0, anonymizeTaskReference(fields.get(0), world, programmer));
            fields.set(3, anonymizeTaskReference(fields.get(3), world, programmer));
            frames.add(new ListValue(fields));
        }
        return BuiltinResult.value(new ListValue(frames));
    }

    private static MooValue anonymizeTaskReference(
            MooValue value, WorldTxn world, long programmer) {
        if (!(value instanceof AnonymousObjectValue anonymous)) {
            return value;
        }
        WorldAnonymousObject body = world.anonymousObject(anonymous).orElse(null);
        if (body == null) {
            return value;
        }
        boolean wizard = world.isWizard(programmer);
        return wizard || body.owner() == programmer ? value : new AnonymousObjectValue();
    }

    private static BuiltinResult setTaskPerms(
            List<MooValue> arguments, WorldTxn world, long currentProgrammer) {
        ObjectValue requested = (ObjectValue) arguments.getFirst();
        if (requested.value() != currentProgrammer && !world.isWizard(currentProgrammer)) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        return new BuiltinResult.Programmer(requested.value());
    }

    private static BuiltinResult dynamicEval(List<MooValue> arguments, BuiltinCatalog catalog) {
        if (arguments.stream().anyMatch(argument -> !(argument instanceof StringValue))) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        String source =
                arguments.stream()
                        .map(StringValue.class::cast)
                        .map(StringValue::text)
                        .collect(java.util.stream.Collectors.joining("\n"));
        MooCompiler.CompilationResult compiled =
                new MooCompiler().compileResult(source, name -> catalog.spec(name).isPresent());
        if (!compiled.diagnostics().isEmpty()) {
            return BuiltinResult.value(new ListValue(List.of(new IntegerValue(0),
                    new ListValue(compiled.diagnostics().stream()
                            .map(diagnostic -> (MooValue) StringValue.of(diagnostic.display())).toList()))));
        }
        return new BuiltinResult.DynamicEval(compiled.program().orElseThrow());
    }
}
