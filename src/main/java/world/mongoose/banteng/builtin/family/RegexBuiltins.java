package world.mongoose.banteng.builtin.family;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinPermissionRule;
import world.mongoose.banteng.builtin.BuiltinRegistrar;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.StringValue;

/** Owns builtin registrations and implementations for this family. */
public final class RegexBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
    private static final Set<ArgType> STRING = Set.of(ArgType.STRING);

    private RegexBuiltins() {
    }

    public static void registerLegacy(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        ToastRegexService toastRegex = new ToastRegexService();
        registrar.builtin("match", List.of(new CallShape(List.of(STRING, STRING), List.of(ANY), Optional.empty()))).implementation(call -> toastMatch(toastRegex, call.arguments(), false));
        registrar.builtin("rmatch", List.of(new CallShape(List.of(STRING, STRING), List.of(ANY), Optional.empty()))).implementation(call -> toastMatch(toastRegex, call.arguments(), true));
    }

    public static void registerPcre(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        PcreService pcre = new PcreService();
        registrar.builtin("pcre_match", List.of(
                new CallShape(
                        List.of(STRING, STRING), List.of(INTEGER, INTEGER), Optional.empty()))).irrevocable().implementation(call -> pcre.match(call.arguments()));
        registrar.builtin("pcre_replace", List.of(new CallShape(List.of(STRING, STRING), List.of(), Optional.empty()))).implementation(call -> pcre.replace(call.arguments()));
        registrar.builtin("pcre_cache_stats", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> pcre.cacheStats());
    }

    private static BuiltinResult toastMatch(ToastRegexService toastRegex, List<MooValue> arguments, boolean reverse) {
        return toastRegex.match(
                (StringValue) arguments.get(0),
                (StringValue) arguments.get(1),
                arguments.size() == 3 && arguments.get(2).isTruthy(),
                reverse);
    }
}
