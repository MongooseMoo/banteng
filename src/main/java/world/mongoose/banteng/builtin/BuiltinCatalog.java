package world.mongoose.banteng.builtin;
import world.mongoose.banteng.builtin.family.SqliteBuiltins;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import world.mongoose.banteng.world.SuspendedActivation;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import java.util.Set;
import java.util.function.LongBinaryOperator;
import java.util.function.Supplier;
import world.mongoose.banteng.builtin.family.ConversionBuiltins;
import world.mongoose.banteng.builtin.family.ConnectionBuiltins;

import world.mongoose.banteng.builtin.family.BinaryBuiltins;
import world.mongoose.banteng.builtin.family.HostBuiltins;
import world.mongoose.banteng.builtin.family.CryptoBuiltins;
import world.mongoose.banteng.builtin.family.FileIoBuiltins;
import world.mongoose.banteng.builtin.family.JsonBuiltins;
import world.mongoose.banteng.builtin.family.ListBuiltins;
import world.mongoose.banteng.builtin.family.MapBuiltins;
import world.mongoose.banteng.builtin.family.MathBuiltins;
import world.mongoose.banteng.builtin.family.NoiseBuiltins;
import world.mongoose.banteng.builtin.family.RandomBuiltins;
import world.mongoose.banteng.builtin.family.RegexBuiltins;
import world.mongoose.banteng.builtin.family.ObjectBuiltins;
import world.mongoose.banteng.builtin.family.PropertyBuiltins;
import world.mongoose.banteng.builtin.family.ServerBuiltins;
import world.mongoose.banteng.builtin.family.StringBuiltins;
import world.mongoose.banteng.builtin.family.TaskBuiltins;
import world.mongoose.banteng.builtin.family.TimeBuiltins;
import world.mongoose.banteng.builtin.family.VerbBuiltins;
import world.mongoose.banteng.bytecode.MooCompiler;
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
import world.mongoose.banteng.value.MooValue.WaifValue;

import world.mongoose.banteng.value.ValueSemantics;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

/** The explicit builtin catalog required by the first managed runtime path. */
public final class BuiltinCatalog {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
  private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
  private static final Set<ArgType> STRING = Set.of(ArgType.STRING);
  private static final Set<ArgType> OBJECT = Set.of(ArgType.OBJECT);

  private final List<BuiltinSpec> manifest;
  private final FileIoBuiltins fileIo;
  private final BuiltinHosts hosts;
  private final Optional<ConnectionBuiltins.ListenerControl> listenerControl;
  private final Random random;
  private final SqliteBuiltins sqlite;
  private final Random floatingRandom;
  private final Map<String, BuiltinSpec> specs;
  private volatile Set<String> protectedFunctions = Set.of();

  /** Creates a catalog from one complete standalone host composition. */
  public BuiltinCatalog(BuiltinHosts hosts) {
    this.hosts = Objects.requireNonNull(hosts, "hosts");
    listenerControl = Optional.empty();
    ConfinedFileRoot files = new ConfinedFileRoot(Path.of("files"));
    fileIo = new FileIoBuiltins(files);
    sqlite = new SqliteBuiltins(files);
    random = new Random();
    floatingRandom = new Random();
    manifest = buildManifest();
    specs = indexManifest(manifest);
  }

  /** Creates a catalog from listener ownership and one complete host composition. */
  public BuiltinCatalog(ConnectionBuiltins.ListenerControl listenerControl, BuiltinHosts hosts) {
    this.hosts = Objects.requireNonNull(hosts, "hosts");
    this.listenerControl = Optional.of(Objects.requireNonNull(listenerControl, "listenerControl"));
    ConfinedFileRoot files = new ConfinedFileRoot(Path.of("files"));
    fileIo = new FileIoBuiltins(files);
    sqlite = new SqliteBuiltins(files);
    random = new Random();
    floatingRandom = new Random();
    manifest = buildManifest();
    specs = indexManifest(manifest);
  }

  /** Returns the standalone invocation composition retained by this catalog. */
  public BuiltinHosts hosts() {
    return hosts;
  }

  private List<BuiltinSpec> buildManifest() {
    List<BuiltinSpec> entries = new ArrayList<>();
    TaskBuiltins.registerGroup0(entries, this);
    HostBuiltins.registerEnvironment(entries);
    VerbBuiltins.registerRespondTo(entries);
    ServerBuiltins.registerValueSizes(entries);
    ListBuiltins.registerLength(entries);
    MathBuiltins.register(entries);
    RandomBuiltins.register(entries, () -> random, () -> floatingRandom, SECURE_RANDOM);
    TimeBuiltins.register(entries);
    TaskBuiltins.registerGroup1(entries, this);
    ListBuiltins.registerListEdits(entries);
    MapBuiltins.register(entries);
    JsonBuiltins.register(entries);
    ListBuiltins.registerSetsAndSort(entries);
    StringBuiltins.registerExplode(entries);
    ListBuiltins.registerReverse(entries);
    StringBuiltins.registerSubstitutionAndAnsi(entries, random);
    NoiseBuiltins.register(entries);
    StringBuiltins.registerSearchAndCompare(entries);
    CryptoBuiltins.register(entries, SECURE_RANDOM, hosts.crypt());
    BinaryBuiltins.registerDecode(entries);
    VerbBuiltins.registerDisassemble(entries);
    HostBuiltins.registerTransferAndText(entries);
    BinaryBuiltins.registerEncodeAndChr(entries);
    PropertyBuiltins.register(entries);
    VerbBuiltins.registerDefinitionEdits(entries);
    ObjectBuiltins.registerLifecycleAndTopology(entries);
    VerbBuiltins.registerMetadataAndCode(entries, this);
    ObjectBuiltins.registerMovementAndWaifs(entries);
    ConnectionBuiltins.registerSwitchPlayer(entries, listenerControl);
    TaskBuiltins.registerGroup2(entries, this);
    ConnectionBuiltins.registerInventory(entries, listenerControl);
    RegexBuiltins.registerLegacy(entries);
    ConnectionBuiltins.registerOptionsAndListeners(entries, listenerControl);
    TaskBuiltins.registerGroup3(entries, this);
    ConnectionBuiltins.registerNotify(entries, listenerControl);
    ConversionBuiltins.registerConversions(entries);
    TaskBuiltins.registerGroup4(entries, this);
    ConversionBuiltins.registerTypeOf(entries);
    ServerBuiltins.registerVersionAndFunctions(entries, this);
    fileIo.register(entries);
    RegexBuiltins.registerPcre(entries);
    sqlite.register(entries);
    ServerBuiltins.registerDiagnostics(entries, this, hosts.serverLog());
    ObjectBuiltins.registerResetMaximum(entries);
    TaskBuiltins.registerGroup5(entries, this);
    ServerBuiltins.registerShutdownAndDatabase(entries);
    return List.copyOf(entries);
  }

  private static Map<String, BuiltinSpec> indexManifest(List<BuiltinSpec> manifest) {
    Map<String, BuiltinSpec> indexed = new LinkedHashMap<>();
    for (BuiltinSpec spec : manifest) {
      if (indexed.putIfAbsent(spec.name(), spec) != null) {
        throw new IllegalStateException("duplicate builtin manifest entry: " + spec.name());
      }
    }
    return Map.copyOf(indexed);
  }

  /** Returns the immutable production manifest in registration order. */
  public List<BuiltinSpec> manifest() {
    return manifest;
  }

  /** Finds one canonical builtin contract case-insensitively. */
  public Optional<BuiltinSpec> spec(String name) {
    return Optional.ofNullable(specs.get(name.toLowerCase(Locale.ROOT)));
  }

  /** Validates and invokes one exact manifest entry. */
  public BuiltinResult invoke(BuiltinSpec spec, BuiltinCall call) {
    List<MooValue> arguments = call.arguments();
    WorldTxn world = call.world();
    long programmer = call.programmer();
    MooValue receiver = call.receiver();
    if (!(receiver instanceof ObjectValue object && object.value() == 0)
        && protectedFunctions.contains(spec.name())) {
      String wrapper = "bf_" + spec.name();
      if (world.verb(0, wrapper, true).isPresent()) {
        return new BuiltinResult.ProtectedCall(wrapper, new ListValue(arguments));
      }
      if (!world.isWizard(programmer)) return BuiltinResult.error(ErrorValue.E_PERM);
    }
    if (spec.callShapes().stream().noneMatch(shape -> shape.acceptsArity(arguments.size()))) {
      int minimum = spec.callShapes().stream().mapToInt(shape -> shape.required().size()).min().orElseThrow();
      int maximum = spec.callShapes().stream().anyMatch(shape -> shape.variadic().isPresent()) ? -1
          : spec.callShapes().stream().mapToInt(shape -> shape.required().size() + shape.optional().size()).max().orElseThrow();
      String expected = minimum == maximum ? Integer.toString(minimum) : minimum + "-" + maximum;
      return BuiltinResult.raised(ErrorValue.E_ARGS,
          StringValue.of("Incorrect number of arguments (expected " + expected + "; got " + arguments.size() + ")"),
          new IntegerValue(0));
    }
    boolean promotedFloatMath = call.hosts().valueSemantics().promoteNumbers()
        && switch (spec.name()) {
          case "acos", "acosh", "asin", "asinh", "atan", "atan2", "atanh", "cbrt", "ceil", "cos", "cosh", "exp", "floor", "log", "log10", "sin", "sinh", "sqrt", "tan", "tanh" -> true;
          default -> false;
        }
        && arguments.stream().allMatch(argument -> ArgType.NUMBER.accepts(argument));
    if (!promotedFloatMath
        && spec.callShapes().stream().noneMatch(shape -> shape.accepts(arguments))) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (!spec.permission().allows(world, programmer)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    return spec.implementation().invoke(call);
  }

  /** Loads the builtin protection flags from the published server options. */
  public void reloadProtectedFunctions(WorldTxn world) {
    Set<String> protectedNames = new LinkedHashSet<>();
    if (world.readObjectProperty(0, "server_options").orElse(null) instanceof ObjectValue options) {
      for (BuiltinSpec spec : manifest) {
        if (world.readObjectProperty(options.value(), "protect_" + spec.name())
            .map(MooValue::isTruthy).orElse(false)) {
          protectedNames.add(spec.name());
        }
      }
    }
    protectedFunctions = Set.copyOf(protectedNames);
  }

}
