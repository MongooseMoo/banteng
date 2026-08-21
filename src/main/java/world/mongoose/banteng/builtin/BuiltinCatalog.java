package world.mongoose.banteng.builtin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.CancellationException;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.LongBinaryOperator;
import java.util.function.Supplier;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.host.NativeCalls;
import world.mongoose.banteng.host.NativeCalls.NativeCallException;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.syntax.Ast;
import world.mongoose.banteng.syntax.MooParser;
import world.mongoose.banteng.syntax.MooUnparser;
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
import world.mongoose.banteng.world.ObjectFlags;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldResult;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/** The explicit builtin catalog required by the first managed runtime path. */
public final class BuiltinCatalog {
  private static final int CLOCK_REALTIME = 0;
  private static final int CLOCK_MONOTONIC = 1;
  private static final int CLOCK_MONOTONIC_RAW = 4;
  private static final long CTIME_MAX_SECONDS = (long) Integer.MAX_VALUE * 31_536_000L;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
  private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
  private static final Set<ArgType> NUMBER = Set.of(ArgType.NUMBER);
  private static final Set<ArgType> STRING = Set.of(ArgType.STRING);
  private static final Set<ArgType> OBJECT = Set.of(ArgType.OBJECT);
  private static final int DEFAULT_MAX_QUEUED_OUTPUT = 65_536;
  private static final long DEFAULT_MAX_LIST_VALUE_BYTES = 64_537_861L;
  private static final long MIN_LIST_VALUE_BYTES_LIMIT = 1_021L;
  private static final long MAX_LIST_VALUE_BYTES_LIMIT = 2_147_482_626L;
  private static final String SERVER_VERSION = "0.1.0-SNAPSHOT";

  private final List<BuiltinSpec> manifest;
  private final FileIoService fileIo;
  private final BuiltinHosts hosts;
  private final PcreService pcre = new PcreService();
  private final Optional<ListenerControl> listenerControl;
  private final Random random;
  private final SqliteService sqlite;
  private final ToastRegexService toastRegex = new ToastRegexService();
  private final Random floatingRandom;
  private final Map<String, BuiltinSpec> specs;

  /** Creates a catalog from one complete standalone host composition. */
  public BuiltinCatalog(BuiltinHosts hosts) {
    this.hosts = Objects.requireNonNull(hosts, "hosts");
    listenerControl = Optional.empty();
    ConfinedFileRoot files = new ConfinedFileRoot(Path.of("files"));
    fileIo = new FileIoService(files);
    sqlite = new SqliteService(files);
    random = new Random();
    floatingRandom = new Random();
    manifest = buildManifest();
    specs = indexManifest(manifest);
  }

  /** Creates a catalog from listener ownership and one complete host composition. */
  public BuiltinCatalog(ListenerControl listenerControl, BuiltinHosts hosts) {
    this.hosts = Objects.requireNonNull(hosts, "hosts");
    this.listenerControl = Optional.of(Objects.requireNonNull(listenerControl, "listenerControl"));
    ConfinedFileRoot files = new ConfinedFileRoot(Path.of("files"));
    fileIo = new FileIoService(files);
    sqlite = new SqliteService(files);
    random = new Random();
    floatingRandom = new Random();
    manifest = buildManifest();
    specs = indexManifest(manifest);
  }

  static Supplier<ConnectionRegistryAccess> standaloneConnections() {
    return () -> EmptyConnectionRegistry.INSTANCE;
  }

  private ConnectionRegistryAccess connections() {
    return Objects.requireNonNull(hosts.connections().get(), "hosts.connections().get()");
  }

  private enum EmptyConnectionRegistry implements ConnectionRegistryAccess {
    INSTANCE;

    @Override
    public List<Long> connectionIds() {
      return List.of();
    }

    @Override
    public ConnectionRegistryAccess copy() {
      return this;
    }

    @Override
    public void replaceWith(ConnectionRegistryAccess source) {
      if (!Objects.requireNonNull(source, "source").connectionIds().isEmpty()) {
        throw new IllegalStateException("connection registry is unavailable");
      }
    }

    @Override
    public boolean sameState(ConnectionRegistryAccess other) {
      return Objects.requireNonNull(other, "other").connectionIds().isEmpty();
    }

    @Override
    public void openConnection(long connectionId) {
      throw new IllegalStateException("connection registry is unavailable");
    }

    @Override
    public void openConnection(long connectionId, MapValue info) {
      throw new IllegalStateException("connection registry is unavailable");
    }

    @Override
    public void closeConnection(long connectionId) {}

    @Override
    public OptionalLong connectionPlayer(long connectionId) {
      return OptionalLong.empty();
    }

    @Override
    public List<Long> connectedPlayers(boolean showAll) {
      return List.of();
    }

    @Override
    public Optional<MapValue> connectionInfo(long objectId) {
      return Optional.empty();
    }

    @Override
    public boolean rewriteConnectionName(
        long connectionId, String expectedIp, String resolvedName) {
      return false;
    }

    @Override
    public OptionalLong connectionId(long objectId) {
      return OptionalLong.empty();
    }

    @Override
    public Optional<ListValue> intrinsicCommands(long objectId) {
      return Optional.empty();
    }

    @Override
    public boolean setIntrinsicCommands(long objectId, ListValue commands) {
      return false;
    }

    @Override
    public boolean switchConnectionPlayer(long connectionId, long playerId) {
      return false;
    }
  }

  private List<BuiltinSpec> buildManifest() {
    List<BuiltinSpec> entries = new ArrayList<>();
    Set<ArgType> floatMath =
        hosts.valueSemantics().promoteNumbers() ? NUMBER : Set.of(ArgType.FLOAT);
    BuiltinHandler setThreadMode =
        call -> setThreadMode(call.arguments(), call.threadMode());
    BuiltinHandler allMembers =
        call ->
            call.threadMode()
                ? BuiltinResult.hostWork(() -> allMembers(call.arguments()))
                : allMembers(call.arguments());
    BuiltinHandler sort =
        call ->
            call.threadMode()
                ? BuiltinResult.hostWork(() -> sortValues(call.arguments()))
                : sortValues(call.arguments());
    BuiltinHandler callFunction =
        call ->
            callFunction(
                call.arguments(),
                call.world(),
                call.programmer(),
                call.taskLocal(),
                call.taskId(),
                call.remainingTicks(),
                call.remainingSeconds(),
                call.receiver(),
                call.callerProgrammer(),
                call.callers(),
                call.threadMode());
    entries.add(
        new BuiltinSpec(
            "value_bytes",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call ->
                BuiltinResult.value(new IntegerValue(valueBytes(call.arguments().getFirst(), call.world())))));
    entries.add(
        new BuiltinSpec(
            "length",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.STRING, ArgType.LIST, ArgType.MAP)),
                    List.of(),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> length(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "min",
            List.of(new CallShape(List.of(NUMBER), List.of(), Optional.of(NUMBER))),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> minimum(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "max",
            List.of(new CallShape(List.of(NUMBER), List.of(), Optional.of(NUMBER))),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> maximum(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "abs",
            List.of(new CallShape(List.of(NUMBER), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> absoluteValue(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "acos",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(BuiltinCatalog::acos)));
    entries.add(
        new BuiltinSpec(
            "acosh",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(BuiltinCatalog::acosh)));
    entries.add(
        new BuiltinSpec(
            "asin",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(BuiltinCatalog::asin)));
    entries.add(
        new BuiltinSpec(
            "asinh",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(BuiltinCatalog::asinh)));
    entries.add(
        new BuiltinSpec(
            "atan",
            List.of(
                new CallShape(
                    List.of(floatMath),
                    List.of(floatMath),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> atan(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "atan2",
            List.of(
                new CallShape(
                    List.of(floatMath, floatMath),
                    List.of(),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> atan2(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "atanh",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(BuiltinCatalog::atanh)));
    entries.add(
        new BuiltinSpec(
            "cbrt",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(Math::cbrt)));
    entries.add(
        new BuiltinSpec(
            "ceil",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(Math::ceil)));
    entries.add(
        new BuiltinSpec(
            "cos",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(BuiltinCatalog::cosine)));
    entries.add(
        new BuiltinSpec(
            "cosh",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(Math::cosh)));
    entries.add(
        new BuiltinSpec(
            "distance",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.LIST), Set.of(ArgType.LIST)),
                    List.of(),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> distance(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "exp",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(Math::exp)));
    entries.add(
        new BuiltinSpec(
            "floatstr",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.FLOAT), INTEGER),
                    List.of(ANY),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> floatstr(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "floor",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(Math::floor)));
    entries.add(
        new BuiltinSpec(
            "log",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(BuiltinCatalog::log)));
    entries.add(
        new BuiltinSpec(
            "log10",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(BuiltinCatalog::log10)));
    entries.add(
        new BuiltinSpec(
            "relative_heading",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.LIST), Set.of(ArgType.LIST)),
                    List.of(),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> relativeHeading(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "round",
            List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> round(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "sin",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(BuiltinCatalog::sine)));
    entries.add(
        new BuiltinSpec(
            "sinh",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(Math::sinh)));
    entries.add(
        new BuiltinSpec(
            "sqrt",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(BuiltinCatalog::sqrt)));
    entries.add(
        new BuiltinSpec(
            "tan",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(BuiltinCatalog::tangent)));
    entries.add(
        new BuiltinSpec(
            "tanh",
            List.of(new CallShape(List.of(floatMath), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            unaryFloatBuiltin(Math::tanh)));
    entries.add(
        new BuiltinSpec(
            "trunc",
            List.of(new CallShape(List.of(Set.of(ArgType.FLOAT)), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> trunc(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "random",
            List.of(new CallShape(List.of(), List.of(INTEGER, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.VM,
            call -> randomInteger(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "reseed_random",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.VM,
            call -> {
              random.setSeed(SECURE_RANDOM.nextLong());
              return BuiltinResult.value(new IntegerValue(0));
            }));
    entries.add(
        new BuiltinSpec(
            "frandom",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.FLOAT)),
                    List.of(Set.of(ArgType.FLOAT)),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.VM,
            call -> floatingRandom(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "random_bytes",
            List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.VM,
            call -> randomBytes(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "time",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.VM,
            call ->
                BuiltinResult.value(new IntegerValue(Instant.now().getEpochSecond()))));
    entries.add(
        new BuiltinSpec(
            "ctime",
            List.of(new CallShape(List.of(), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.VM,
            call -> ctime(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "ftime",
            List.of(new CallShape(List.of(), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.VM,
            call -> ftime(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "raise",
            List.of(new CallShape(List.of(ANY), List.of(STRING, ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> raise(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "listappend",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.LIST), ANY),
                    List.of(INTEGER),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> listInsert(call.arguments(), true, call.world())));
    entries.add(
        new BuiltinSpec(
            "listinsert",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.LIST), ANY),
                    List.of(INTEGER),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> listInsert(call.arguments(), false, call.world())));
    entries.add(
        new BuiltinSpec(
            "listdelete",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.LIST), INTEGER), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> listDelete(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "listset",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.LIST), ANY, INTEGER),
                    List.of(),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> listSet(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "mapdelete",
            List.of(new CallShape(List.of(Set.of(ArgType.MAP), ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> mapDelete(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "mapkeys",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.MAP)), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> mapKeys(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "mapvalues",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.MAP)), List.of(), Optional.of(ANY))),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> mapValues(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "maphaskey",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.MAP), ANY), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> mapHasKey(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "generate_json",
            List.of(new CallShape(List.of(ANY), List.of(STRING, ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> generateJson(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "parse_json",
            List.of(new CallShape(List.of(STRING), List.of(STRING), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> parseJson(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "setadd",
            List.of(new CallShape(List.of(Set.of(ArgType.LIST), ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> setAdd(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "setremove",
            List.of(new CallShape(List.of(Set.of(ArgType.LIST), ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> setRemove(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "all_members",
            List.of(
                new CallShape(
                    List.of(ANY, Set.of(ArgType.LIST)), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.SUSPENDING_HOST,
            BuiltinOwner.VM,
            allMembers));
    entries.add(
        new BuiltinSpec(
            "sort",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.LIST)),
                    List.of(
                        Set.of(ArgType.LIST),
                        Set.of(ArgType.INTEGER),
                        Set.of(ArgType.INTEGER)),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.SUSPENDING_HOST,
            BuiltinOwner.VM,
            sort));
    entries.add(
        new BuiltinSpec(
            "explode",
            List.of(new CallShape(List.of(STRING), List.of(STRING, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> explode(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "reverse",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> reverse(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "strsub",
            List.of(new CallShape(List.of(STRING, STRING, STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> stringSubstitute(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "strtr",
            List.of(new CallShape(List.of(STRING, STRING, STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> stringTranslate(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "parse_ansi",
            List.of(new CallShape(List.of(STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.VM,
            call -> parseAnsi(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "remove_ansi",
            List.of(new CallShape(List.of(STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> removeAnsi(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "simplex_noise",
            List.of(new CallShape(List.of(Set.of(ArgType.LIST)), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> simplexNoise(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "index",
            List.of(new CallShape(List.of(STRING, STRING), List.of(ANY, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> stringIndex(call.arguments(), false)));
    entries.add(
        new BuiltinSpec(
            "rindex",
            List.of(new CallShape(List.of(STRING, STRING), List.of(ANY, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> stringIndex(call.arguments(), true)));
    entries.add(
        new BuiltinSpec(
            "strcmp",
            List.of(new CallShape(List.of(STRING, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> stringCompare(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "crypt",
            List.of(new CallShape(List.of(STRING), List.of(STRING), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.VM,
            call -> crypt(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "argon2",
            List.of(
                new CallShape(
                    List.of(STRING, STRING),
                    List.of(INTEGER, INTEGER, INTEGER),
                    Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> argon2(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "argon2_verify",
            List.of(new CallShape(List.of(STRING, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call ->
                argon2Verify(
                    call.arguments(), hosts.serverLog(), BuiltinCatalog::argon2Hash)));
    entries.add(
        new BuiltinSpec(
            "decode_binary",
            List.of(new CallShape(List.of(STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> decodeBinary(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "disassemble",
            List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> disassemble(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "encode_binary",
            List.of(new CallShape(List.of(), List.of(), Optional.of(ANY))),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> encodeBinary(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "chr",
            List.of(new CallShape(List.of(), List.of(), Optional.of(ANY))),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> chr(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "add_property",
            List.of(
                new CallShape(
                    List.of(ANY, STRING, ANY, Set.of(ArgType.LIST)),
                    List.of(),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> addProperty(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "properties",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> properties(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "property_info",
            List.of(new CallShape(List.of(ANY, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> propertyInfo(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "is_clear_property",
            List.of(new CallShape(List.of(ANY, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> isClearProperty(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "clear_property",
            List.of(new CallShape(List.of(ANY, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> clearProperty(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "delete_property",
            List.of(new CallShape(List.of(ANY, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> deleteProperty(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "delete_verb",
            List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> deleteVerb(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "add_verb",
            List.of(
                new CallShape(
                    List.of(ANY, Set.of(ArgType.LIST), Set.of(ArgType.LIST)),
                    List.of(),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> addVerb(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "create",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.OBJECT, ArgType.LIST)),
                    List.of(ANY, ANY, ANY),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> create(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "recreate",
            List.of(new CallShape(List.of(OBJECT, OBJECT), List.of(OBJECT), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> recreate(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "parent",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> parent(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "parents",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> parents(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "ancestors",
            List.of(new CallShape(List.of(ANY), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> ancestors(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "children",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> children(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "chparent",
            List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> changeParents(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "chparents",
            List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> changeParents(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "is_player",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> isPlayer(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "valid",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> valid(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "max_object",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call ->
                BuiltinResult.value(new ObjectValue(call.world().maximumObjectId()))));
    entries.add(
        new BuiltinSpec(
            "locate_by_name",
            List.of(new CallShape(List.of(STRING), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> locateByName(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "locations",
            List.of(new CallShape(List.of(OBJECT), List.of(OBJECT, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> locations(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "recycled_objects",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> recycledObjects(call.world())));
    entries.add(
        new BuiltinSpec(
            "next_recycled_object",
            List.of(new CallShape(List.of(), List.of(OBJECT), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> nextRecycledObject(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "owned_objects",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> ownedObjects(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "set_player_flag",
            List.of(new CallShape(List.of(OBJECT, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> setPlayerFlag(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "set_verb_code",
            List.of(
                new CallShape(
                    List.of(ANY, ANY, Set.of(ArgType.LIST)),
                    List.of(),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> setVerbCode(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "set_verb_args",
            List.of(
                new CallShape(
                    List.of(ANY, ANY, Set.of(ArgType.LIST)),
                    List.of(),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> setVerbArgs(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "set_verb_info",
            List.of(
                new CallShape(
                    List.of(ANY, ANY, Set.of(ArgType.LIST)),
                    List.of(),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> setVerbInfo(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "verbs",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> verbs(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "verb_args",
            List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> verbArgs(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "verb_code",
            List.of(new CallShape(List.of(ANY, ANY), List.of(ANY, ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> verbCode(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "verb_info",
            List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> verbInfo(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "move",
            List.of(new CallShape(List.of(OBJECT, OBJECT), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> move(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "recycle",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> recycle(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "new_waif",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> {
              if (call.receiver() instanceof AnonymousObjectValue anonymous
                  && call.world().anonymousObject(anonymous).isPresent()) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
              }
              if (!(call.receiver() instanceof ObjectValue classObject)
                  || call.world().object(classObject.value()).isEmpty()) {
                return BuiltinResult.error(ErrorValue.E_INVIND);
              }
              return BuiltinResult.value(call.world().createWaif(classObject.value(), call.programmer()));
            }));
    entries.add(
        new BuiltinSpec(
            "waif_stats",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            call -> waifStats(call.world())));
    entries.add(
        new BuiltinSpec(
            "switch_player",
            List.of(new CallShape(List.of(OBJECT, OBJECT), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.CONNECTION,
            call -> switchPlayer(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "caller_perms",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call ->
                BuiltinResult.value(new ObjectValue(call.callers().size() == 0 ? -1 : call.callerProgrammer()))));
    entries.add(
        new BuiltinSpec(
            "callers",
            List.of(new CallShape(List.of(), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> callers(call.callers(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "call_function",
            List.of(new CallShape(List.of(STRING), List.of(), Optional.of(ANY))),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.VM,
            callFunction));
    entries.add(
        new BuiltinSpec(
            "queue_info",
            List.of(new CallShape(List.of(), List.of(OBJECT), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.EXTERNAL_READ,
            BuiltinOwner.TASK,
            hosts.queueInfo()));
    entries.add(
        new BuiltinSpec(
            "queued_tasks",
            List.of(new CallShape(List.of(), List.of(INTEGER, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.TASK,
            hosts.queuedTasks()));
    entries.add(
        new BuiltinSpec(
            "task_stack",
            List.of(new CallShape(List.of(INTEGER), List.of(ANY, ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.TASK,
            hosts.taskStack()));
    entries.add(
        new BuiltinSpec(
            "kill_task",
            List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.TASK,
            hosts.killTask()));
    entries.add(
        new BuiltinSpec(
            "thread_pool",
            List.of(
                new CallShape(
                    List.of(STRING, STRING), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.TASK,
            this::configureThreadPool));
    entries.add(
        new BuiltinSpec(
            "threads",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.TASK,
            hosts.threads()));
    entries.add(
        new BuiltinSpec(
            "read",
            List.of(new CallShape(List.of(), List.of(OBJECT, ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.SUSPENDING_HOST,
            BuiltinOwner.CONNECTION,
            call -> {
              if (call.arguments().isEmpty() && !BuiltinPermissionRule.WIZARD_ONLY.allows(call.world(), call.programmer())) {
                return BuiltinResult.error(ErrorValue.E_PERM);
              }
              if (!call.arguments().isEmpty() && !BuiltinPermissionRule.WIZARD_ONLY.allows(call.world(), call.programmer())) {
                WorldObject target = call.world().object(((ObjectValue) call.arguments().getFirst()).value()).orElse(null);
                if (target == null || target.owner() != call.programmer()) {
                  return BuiltinResult.error(ErrorValue.E_PERM);
                }
              }
              return hosts.read().invoke(call);
            }));
    entries.add(
        new BuiltinSpec(
            "connected_players",
            List.of(new CallShape(List.of(), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.EXTERNAL_READ,
            BuiltinOwner.CONNECTION,
            call ->
                BuiltinResult.value(
                    new ListValue(
                        connections()
                            .connectedPlayers(
                                !call.arguments().isEmpty()
                                    && call.arguments().getFirst().isTruthy())
                            .stream()
                            .map(ObjectValue::new)
                            .map(MooValue.class::cast)
                            .toList()))));
    entries.add(
        new BuiltinSpec(
            "buffered_output_length",
            List.of(new CallShape(List.of(), List.of(OBJECT), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.EXTERNAL_READ,
            BuiltinOwner.CONNECTION,
            call ->
                bufferedOutputLength(
                    call.arguments(),
                    call.world(),
                    call.programmer(),
                    call.stagedBufferedOutputLength())));
    entries.add(
        new BuiltinSpec(
            "boot_player",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.CONNECTION,
            call -> bootPlayer(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "connection_info",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.EXTERNAL_READ,
            BuiltinOwner.CONNECTION,
            call -> connectionInfo(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "connection_name",
            List.of(new CallShape(List.of(OBJECT), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.EXTERNAL_READ,
            BuiltinOwner.CONNECTION,
            call -> connectionName(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "match",
            List.of(new CallShape(List.of(STRING, STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> toastMatch(call.arguments(), false)));
    entries.add(
        new BuiltinSpec(
            "rmatch",
            List.of(new CallShape(List.of(STRING, STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> toastMatch(call.arguments(), true)));
    entries.add(
        new BuiltinSpec(
            "connection_name_lookup",
            List.of(new CallShape(List.of(OBJECT), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.SUSPENDING_HOST,
            BuiltinOwner.CONNECTION,
            this::connectionNameLookup));
    entries.add(
        new BuiltinSpec(
            "connection_options",
            List.of(new CallShape(List.of(OBJECT), List.of(STRING), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.EXTERNAL_READ,
            BuiltinOwner.CONNECTION,
            hosts.connectionOptions()));
    entries.add(
        new BuiltinSpec(
            "output_delimiters",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.EXTERNAL_READ,
            BuiltinOwner.CONNECTION,
            hosts.outputDelimiters()));
    entries.add(
        new BuiltinSpec(
            "set_connection_option",
            List.of(new CallShape(List.of(OBJECT, STRING, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.CONNECTION,
            call -> setConnectionOption(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "flush_input",
            List.of(new CallShape(List.of(OBJECT), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.CONNECTION,
            hosts.flushInput()));
    entries.add(
        new BuiltinSpec(
            "force_input",
            List.of(new CallShape(List.of(OBJECT, STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.CONNECTION,
            call -> forceInput(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "listen",
            List.of(
                new CallShape(
                    List.of(OBJECT, ANY), List.of(Set.of(ArgType.MAP)), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.SERVER,
            call -> listen(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "listeners",
            List.of(new CallShape(List.of(), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.EXTERNAL_READ,
            BuiltinOwner.SERVER,
            call -> listeners(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "unlisten",
            List.of(new CallShape(List.of(ANY), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.SERVER,
            call -> unlisten(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "open_network_connection",
            List.of(
                new CallShape(
                    List.of(STRING, INTEGER),
                    List.of(Set.of(ArgType.MAP)),
                    Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.CONNECTION,
            call -> openNetworkConnection(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "task_perms",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> BuiltinResult.value(new ObjectValue(call.programmer()))));
    entries.add(
        new BuiltinSpec(
            "task_id",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> BuiltinResult.value(new IntegerValue(call.taskId()))));
    entries.add(
        new BuiltinSpec(
            "ticks_left",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> BuiltinResult.value(new IntegerValue(call.remainingTicks()))));
    entries.add(
        new BuiltinSpec(
            "seconds_left",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> BuiltinResult.value(new IntegerValue(call.remainingSeconds()))));
    entries.add(
        new BuiltinSpec(
            "set_task_perms",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.VM,
            call -> setTaskPerms(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "set_thread_mode",
            List.of(new CallShape(List.of(), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.VM,
            setThreadMode));
    entries.add(
        new BuiltinSpec(
            "notify",
            List.of(new CallShape(List.of(OBJECT, STRING), List.of(ANY, ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.CONNECTION,
            call -> notifyLine(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "tostr",
            List.of(new CallShape(List.of(), List.of(), Optional.of(ANY))),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> toStringValue(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "tofloat",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> toFloat(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "toint",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> toInteger(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "toliteral",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> toLiteral(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "toobj",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> toObject(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "equal",
            List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call ->
                equalValues(call.arguments(), hosts.valueSemantics())));
    entries.add(
        new BuiltinSpec(
            "eval",
            List.of(new CallShape(List.of(STRING), List.of(), Optional.of(STRING))),
            BuiltinPermissionRule.PROGRAMMER_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> dynamicEval(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "typeof",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> typeOf(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "server_version",
            List.of(new CallShape(List.of(), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.SERVER,
            call -> serverVersion(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "function_info",
            List.of(new CallShape(List.of(), List.of(STRING), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> functionInfo(call.arguments())));
    addFileIoManifest(entries);
    entries.add(
        new BuiltinSpec(
            "pcre_match",
            List.of(
                new CallShape(
                    List.of(STRING, STRING), List.of(INTEGER, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.SERVER,
            call -> pcre.match(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "pcre_replace",
            List.of(new CallShape(List.of(STRING, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> pcre.replace(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "pcre_cache_stats",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.EXTERNAL_READ,
            BuiltinOwner.SERVER,
            call -> pcre.cacheStats()));
    addSqliteManifest(entries);
    entries.add(
        new BuiltinSpec(
            "server_log",
            List.of(new CallShape(List.of(STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.SERVER,
            call -> serverLog(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "log_cache_stats",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.SERVER,
            call -> logCacheStats(call.world())));
    entries.add(
        new BuiltinSpec(
            "verb_cache_stats",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.EXTERNAL_READ,
            BuiltinOwner.SERVER,
            call -> verbCacheStats(call.world())));
    entries.add(
        new BuiltinSpec(
            "usage",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.EXTERNAL_READ,
            BuiltinOwner.SERVER,
            call -> usage()));
    entries.add(
        new BuiltinSpec(
            "memory_usage",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.EXTERNAL_READ,
            BuiltinOwner.SERVER,
            call -> memoryUsage()));
    entries.add(
        new BuiltinSpec(
            "load_server_options",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.SERVER,
            call -> BuiltinResult.value(new IntegerValue(0))));
    entries.add(
        new BuiltinSpec(
            "run_gc",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> BuiltinResult.value(new IntegerValue(0))));
    entries.add(
        new BuiltinSpec(
            "gc_stats",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> gcStats()));
    entries.add(
        new BuiltinSpec(
            "reset_max_object",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            call -> {
              call.world().resetLastUsedObjectId();
              return BuiltinResult.value(new IntegerValue(0));
            }));
    entries.add(
        new BuiltinSpec(
            "suspend",
            List.of(new CallShape(List.of(), List.of(NUMBER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> suspend(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "resume",
            List.of(new CallShape(List.of(INTEGER), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.TASK,
            hosts.resumeTask()));
    entries.add(
        new BuiltinSpec(
            "yin",
            List.of(
                new CallShape(
                    List.of(), List.of(NUMBER, INTEGER, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            call -> yin(call.arguments(), call.world(), call.remainingTicks(), call.remainingSeconds())));
    entries.add(
        new BuiltinSpec(
            "shutdown",
            List.of(new CallShape(List.of(), List.of(STRING, ANY), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.SERVER,
            call -> shutdown(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "db_disk_size",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.EXTERNAL_READ,
            BuiltinOwner.SERVER,
            hosts.dbDiskSize()));
    entries.add(
        new BuiltinSpec(
            "dump_database",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.SERVER,
            BuiltinCatalog::dumpDatabase));
    return List.copyOf(entries);
  }

  private void addFileIoManifest(List<BuiltinSpec> entries) {
    entries.add(fileIoSpec("file_handles", new CallShape(List.of(), List.of(), Optional.empty()), fileIo::handles));
    entries.add(fileIoSpec("file_open", new CallShape(List.of(STRING, STRING), List.of(), Optional.empty()), fileIo::open));
    entries.add(fileIoSpec("file_close", new CallShape(List.of(INTEGER), List.of(), Optional.empty()), fileIo::close));
    entries.add(fileIoSpec("file_name", new CallShape(List.of(INTEGER), List.of(), Optional.empty()), fileIo::name));
    entries.add(fileIoSpec("file_openmode", new CallShape(List.of(INTEGER), List.of(), Optional.empty()), fileIo::openMode));
    entries.add(fileIoSpec("file_readline", new CallShape(List.of(INTEGER), List.of(), Optional.empty()), fileIo::readLine));
    entries.add(fileIoSpec("file_readlines", new CallShape(List.of(INTEGER, INTEGER, INTEGER), List.of(), Optional.empty()), fileIo::readLines));
    entries.add(fileIoSpec("file_writeline", new CallShape(List.of(INTEGER, STRING), List.of(), Optional.empty()), fileIo::writeLine));
    entries.add(fileIoSpec("file_grep", new CallShape(List.of(INTEGER, STRING), List.of(INTEGER), Optional.empty()), fileIo::grep));
    entries.add(fileIoSpec("file_read", new CallShape(List.of(INTEGER, INTEGER), List.of(), Optional.empty()), fileIo::read));
    entries.add(fileIoSpec("file_write", new CallShape(List.of(INTEGER, STRING), List.of(), Optional.empty()), fileIo::write));
    entries.add(fileIoSpec("file_flush", new CallShape(List.of(INTEGER), List.of(), Optional.empty()), fileIo::flush));
    entries.add(fileIoSpec("file_seek", new CallShape(List.of(INTEGER, INTEGER, STRING), List.of(), Optional.empty()), fileIo::seek));
    entries.add(fileIoSpec("file_tell", new CallShape(List.of(INTEGER), List.of(), Optional.empty()), fileIo::tell));
    entries.add(fileIoSpec("file_eof", new CallShape(List.of(INTEGER), List.of(), Optional.empty()), fileIo::eof));
    entries.add(fileIoSpec("file_count_lines", new CallShape(List.of(INTEGER), List.of(), Optional.empty()), fileIo::countLines));
    entries.add(fileIoSpec("file_list", new CallShape(List.of(STRING), List.of(ANY), Optional.empty()), fileIo::list));
    entries.add(fileIoSpec("file_mkdir", new CallShape(List.of(STRING), List.of(), Optional.empty()), fileIo::mkdir));
    entries.add(fileIoSpec("file_rmdir", new CallShape(List.of(STRING), List.of(), Optional.empty()), fileIo::rmdir));
    entries.add(fileIoSpec("file_remove", new CallShape(List.of(STRING), List.of(), Optional.empty()), fileIo::remove));
    entries.add(fileIoSpec("file_rename", new CallShape(List.of(STRING, STRING), List.of(), Optional.empty()), fileIo::rename));
    entries.add(fileIoSpec("file_chmod", new CallShape(List.of(STRING, STRING), List.of(), Optional.empty()), fileIo::chmod));
    entries.add(fileIoSpec("file_size", new CallShape(List.of(ANY), List.of(), Optional.empty()), fileIo::size));
    entries.add(fileIoSpec("file_mode", new CallShape(List.of(ANY), List.of(), Optional.empty()), fileIo::mode));
    entries.add(fileIoSpec("file_type", new CallShape(List.of(ANY), List.of(), Optional.empty()), fileIo::type));
    entries.add(fileIoSpec("file_last_access", new CallShape(List.of(ANY), List.of(), Optional.empty()), fileIo::lastAccess));
    entries.add(fileIoSpec("file_last_modify", new CallShape(List.of(ANY), List.of(), Optional.empty()), fileIo::lastModify));
    entries.add(fileIoSpec("file_last_change", new CallShape(List.of(ANY), List.of(), Optional.empty()), fileIo::lastChange));
    entries.add(fileIoSpec("file_stat", new CallShape(List.of(ANY), List.of(), Optional.empty()), fileIo::stat));
  }

  private static BuiltinSpec fileIoSpec(
      String name, CallShape shape, Function<List<MooValue>, BuiltinResult> handler) {
    return new BuiltinSpec(
        name,
        List.of(shape),
        BuiltinPermissionRule.WIZARD_ONLY,
        BuiltinCostRule.fixed(0),
        EffectClass.IRREVOCABLE,
        BuiltinOwner.SERVER,
        call ->
            handler.apply(call.arguments()));
  }

  private void addSqliteManifest(List<BuiltinSpec> entries) {
    entries.add(
        sqliteSpec(
            "sqlite_open",
            new CallShape(List.of(STRING), List.of(INTEGER), Optional.empty()),
            EffectClass.IRREVOCABLE,
            sqlite::open));
    entries.add(
        sqliteSpec(
            "sqlite_close",
            new CallShape(List.of(INTEGER), List.of(), Optional.empty()),
            EffectClass.IRREVOCABLE,
            sqlite::close));
    entries.add(
        sqliteSpec(
            "sqlite_handles",
            new CallShape(List.of(), List.of(), Optional.empty()),
            EffectClass.EXTERNAL_READ,
            sqlite::handles));
    entries.add(
        sqliteSpec(
            "sqlite_info",
            new CallShape(List.of(INTEGER), List.of(), Optional.empty()),
            EffectClass.EXTERNAL_READ,
            sqlite::info));
    entries.add(
        sqliteSpec(
            "sqlite_query",
            new CallShape(List.of(INTEGER, STRING), List.of(ANY), Optional.empty()),
            EffectClass.SUSPENDING_HOST,
            sqlite::query));
    entries.add(
        sqliteSpec(
            "sqlite_execute",
            new CallShape(
                List.of(INTEGER, STRING, Set.of(ArgType.LIST)),
                List.of(),
                Optional.empty()),
            EffectClass.SUSPENDING_HOST,
            sqlite::execute));
    entries.add(
        sqliteSpec(
            "sqlite_last_insert_row_id",
            new CallShape(List.of(INTEGER), List.of(), Optional.empty()),
            EffectClass.EXTERNAL_READ,
            sqlite::lastInsertRowId));
    entries.add(
        sqliteSpec(
            "sqlite_limit",
            new CallShape(List.of(INTEGER, ANY, INTEGER), List.of(), Optional.empty()),
            EffectClass.IRREVOCABLE,
            sqlite::limit));
    entries.add(
        sqliteSpec(
            "sqlite_interrupt",
            new CallShape(List.of(INTEGER), List.of(), Optional.empty()),
            EffectClass.IRREVOCABLE,
            sqlite::interrupt));
  }

  private static BuiltinSpec sqliteSpec(
      String name,
      CallShape shape,
      EffectClass effect,
      Function<List<MooValue>, BuiltinResult> handler) {
    return new BuiltinSpec(
        name,
        List.of(shape),
        BuiltinPermissionRule.WIZARD_ONLY,
        BuiltinCostRule.fixed(0),
        effect,
        BuiltinOwner.SERVER,
        call ->
            handler.apply(call.arguments()));
  }

  private BuiltinResult configureThreadPool(BuiltinCall call) {
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
    return hosts.threadPool().invoke(call);
  }

  private BuiltinResult connectionInfo(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    long target = ((ObjectValue) arguments.getFirst()).value();
    Optional<MapValue> info = connections().connectionInfo(target);
    if (info.isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    if (target != programmer && !BuiltinPermissionRule.WIZARD_ONLY.allows(world, programmer)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    return BuiltinResult.value(info.orElseThrow());
  }

  private BuiltinResult bufferedOutputLength(
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      LongBinaryOperator stagedBufferedOutputLength) {
    if (arguments.isEmpty()) {
      return BuiltinResult.value(new IntegerValue(DEFAULT_MAX_QUEUED_OUTPUT));
    }
    long target = ((ObjectValue) arguments.getFirst()).value();
    OptionalLong connectionId = connections().connectionId(target);
    if (connectionId.isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    if (programmer != target && !isWizard(world, programmer)) {
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

  private BuiltinResult connectionName(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    long target = ((ObjectValue) arguments.getFirst()).value();
    if (target != programmer && !BuiltinPermissionRule.WIZARD_ONLY.allows(world, programmer)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    MapValue info = connections().connectionInfo(target).orElse(null);
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

  private BuiltinResult toastMatch(List<MooValue> arguments, boolean reverse) {
    return toastRegex.match(
        (StringValue) arguments.get(0),
        (StringValue) arguments.get(1),
        arguments.size() == 3 && arguments.get(2).isTruthy(),
        reverse);
  }

  private BuiltinResult connectionNameLookup(BuiltinCall call) {
    long target = ((ObjectValue) call.arguments().getFirst()).value();
    if (target != call.programmer()
        && !BuiltinPermissionRule.WIZARD_ONLY.allows(call.world(), call.programmer())) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    if (connections().connectionInfo(target).isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    return hosts.connectionNameLookup().invoke(call);
  }

  private static BuiltinResult bootPlayer(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    long target = ((ObjectValue) arguments.getFirst()).value();
    if (target != programmer && !BuiltinPermissionRule.WIZARD_ONLY.allows(world, programmer)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    return new BuiltinResult.BootPlayer(target);
  }

  private BuiltinResult setConnectionOption(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    long target = ((ObjectValue) arguments.get(0)).value();
    if (target != programmer && !BuiltinPermissionRule.WIZARD_ONLY.allows(world, programmer)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    if (connections().connectionInfo(target).isEmpty()) {
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
      connections().setIntrinsicCommands(target, (ListValue) value);
    }
    return hosts.setConnectionOption().set(target, option, value);
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
    if (target != programmer && !BuiltinPermissionRule.WIZARD_ONLY.allows(world, programmer)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    return new BuiltinResult.ForceInput(
        target, ((StringValue) arguments.get(1)).text());
  }

  private BuiltinResult listen(List<MooValue> arguments, WorldTxn world) {
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

  private BuiltinResult listeners(List<MooValue> arguments) {
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

  private BuiltinResult unlisten(List<MooValue> arguments) {
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

  private BuiltinResult openNetworkConnection(List<MooValue> arguments) {
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
                  .openNetworkConnection(host, port, ipv6, listenerHandler)));
    } catch (IllegalArgumentException invalid) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    } catch (IOException unavailable) {
      return BuiltinResult.error(ErrorValue.E_QUOTA);
    }
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

  /** Invokes one named builtin through its manifest entry. */
  public BuiltinResult invoke(
      String name,
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long taskId,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers) {
    Optional<BuiltinSpec> selected = spec(name);
    if (selected.isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_VERBNF);
    }
    return invoke(
        selected.orElseThrow(),
        arguments,
        world,
        programmer,
        taskLocal,
        taskId,
        remainingTicks,
        remainingSeconds,
        receiver,
        callerProgrammer,
        callers);
  }

  /** Validates and invokes one exact manifest entry. */
  public BuiltinResult invoke(
      BuiltinSpec spec,
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long taskId,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers) {
    return invoke(
        spec,
        arguments,
        world,
        programmer,
        taskLocal,
        taskId,
        remainingTicks,
        remainingSeconds,
        receiver,
        callerProgrammer,
        callers,
        true);
  }

  /** Validates and invokes one exact manifest entry for the current activation mode. */
  public BuiltinResult invoke(
      BuiltinSpec spec,
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long taskId,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers,
      boolean threadMode) {
    return invoke(
        spec,
        arguments,
        world,
        programmer,
        taskLocal,
        taskId,
        remainingTicks,
        remainingSeconds,
        receiver,
        callerProgrammer,
        callers,
        threadMode,
        (_connectionId, queuedBytes) -> queuedBytes);
  }

  /** Validates and invokes one exact manifest entry with attempt-local output projection. */
  public BuiltinResult invoke(
      BuiltinSpec spec,
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long taskId,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers,
      boolean threadMode,
      LongBinaryOperator stagedBufferedOutputLength) {
    if (spec.callShapes().stream().noneMatch(shape -> shape.acceptsArity(arguments.size()))) {
      return BuiltinResult.error(ErrorValue.E_ARGS);
    }
    if (spec.callShapes().stream().noneMatch(shape -> shape.accepts(arguments))) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (!spec.permission().allows(world, programmer)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    return spec.implementation()
        .invoke(
            new BuiltinCall(
                arguments,
                world,
                programmer,
                taskLocal,
                taskId,
                remainingTicks,
                remainingSeconds,
                receiver,
                callerProgrammer,
                callers,
                threadMode,
                stagedBufferedOutputLength));
  }

  private BuiltinResult functionInfo(List<MooValue> arguments) {
    if (arguments.size() > 1) {
      return BuiltinResult.error(ErrorValue.E_ARGS);
    }
    if (arguments.isEmpty()) {
      return BuiltinResult.value(
          new ListValue(
              manifest.stream()
                  .map(BuiltinCatalog::functionInfoDescription)
                  .map(MooValue.class::cast)
                  .toList()));
    }
    if (!(arguments.getFirst() instanceof StringValue requestedName)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    BuiltinSpec requested = specs.get(requestedName.text().toLowerCase(Locale.ROOT));
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
            versionPair("os", StringValue.of("Linux")),
            versionPair("features", new ListValue(List.of())),
            versionPair(
                "options",
                new ListValue(
                    List.of(
                        versionPair("OUTBOUND_NETWORK", StringValue.of("ON")),
                        versionPair("PROMOTE_NUMBERS", StringValue.of("OFF"))))),
            versionPair("source", new ListValue(List.of()))));
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

  private BuiltinResult callFunction(
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long taskId,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers,
      boolean threadMode) {
    String name = ((StringValue) arguments.getFirst()).text().toLowerCase(Locale.ROOT);
    BuiltinSpec target = specs.get(name);
    if (target == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    return invoke(
        target,
        arguments.subList(1, arguments.size()),
        world,
        programmer,
        taskLocal,
        taskId,
        remainingTicks,
        remainingSeconds,
        receiver,
        callerProgrammer,
        callers,
        threadMode);
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

  private BuiltinResult serverLog(List<MooValue> arguments) {
    String message = ((StringValue) arguments.getFirst()).text();
    hosts.serverLog().info("> " + message);
    return BuiltinResult.value(new IntegerValue(0));
  }

  private BuiltinResult logCacheStats(WorldTxn world) {
    WorldTxn.VerbCacheStats stats = world.verbCacheStats();
    hosts.serverLog().info(
        String.format(
            Locale.ROOT,
            "Verb cache stat summary: %d hits, %d misses, %d generations",
            stats.hits(),
            stats.misses(),
            stats.generation()));
    hosts.serverLog().info("Depth   Count");
    for (int depth = 0; depth < stats.histogram().size(); depth++) {
      hosts.serverLog().info(
          String.format(Locale.ROOT, "%-5d   %-5d", depth, stats.histogram().get(depth)));
    }
    hosts.serverLog().info("---");
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
    final String statm;
    try {
      statm = Files.readString(Path.of("/proc/self/statm"), StandardCharsets.US_ASCII);
    } catch (IOException exception) {
      return BuiltinResult.error(ErrorValue.E_FILE);
    }
    StringTokenizer fields = new StringTokenizer(statm);
    if (fields.countTokens() != 7) {
      return BuiltinResult.error(ErrorValue.E_NACC);
    }
    List<MooValue> result = new ArrayList<>(5);
    for (int index = 0; index < 7; index++) {
      String field = fields.nextToken();
      if (index == 4 || index == 6) {
        continue;
      }
      try {
        result.add(new FloatValue(Double.parseDouble(field)));
      } catch (NumberFormatException exception) {
        return BuiltinResult.error(ErrorValue.E_NACC);
      }
    }
    return BuiltinResult.value(new ListValue(result));
  }

  private static BuiltinResult usage() {
    List<MooValue> loads = new ArrayList<>(3);
    try {
      StringTokenizer fields =
          new StringTokenizer(
              Files.readString(Path.of("/proc/loadavg"), StandardCharsets.US_ASCII));
      for (int index = 0; index < 3; index++) {
        loads.add(new IntegerValue((long) (Double.parseDouble(fields.nextToken()) * 65_536.0)));
      }
    } catch (IOException | NumberFormatException | java.util.NoSuchElementException exception) {
      loads.clear();
      loads.addAll(List.of(new IntegerValue(0), new IntegerValue(0), new IntegerValue(0)));
    }

    long[] resource = new long[11];
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment value = arena.allocate(144, 8);
      int result = NativeCalls.getrusage(0, value);
      if (result == 0) {
        resource[0] = value.get(ValueLayout.JAVA_LONG, 0);
        resource[1] = value.get(ValueLayout.JAVA_LONG, 8);
        resource[2] = value.get(ValueLayout.JAVA_LONG, 16);
        resource[3] = value.get(ValueLayout.JAVA_LONG, 24);
        resource[4] = value.get(ValueLayout.JAVA_LONG, 40);
        resource[5] = value.get(ValueLayout.JAVA_LONG, 48);
        resource[6] = value.get(ValueLayout.JAVA_LONG, 64);
        resource[7] = value.get(ValueLayout.JAVA_LONG, 72);
        resource[8] = value.get(ValueLayout.JAVA_LONG, 104);
        resource[9] = value.get(ValueLayout.JAVA_LONG, 112);
        resource[10] = value.get(ValueLayout.JAVA_LONG, 96);
      }
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

  private static BuiltinResult shutdown(List<MooValue> arguments) {
    if (arguments.size() == 2 && arguments.get(1).isTruthy()) {
      return new BuiltinResult.Panic(((StringValue) arguments.getFirst()).text());
    }
    return new BuiltinResult.Shutdown();
  }

  private static BuiltinResult length(List<MooValue> arguments) {
    MooValue value = arguments.getFirst();
    if (value instanceof StringValue string) {
      return BuiltinResult.value(new IntegerValue(string.length()));
    }
    if (value instanceof ListValue list) {
      return BuiltinResult.value(new IntegerValue(list.size()));
    }
    if (value instanceof MapValue map) {
      return BuiltinResult.value(new IntegerValue(map.size()));
    }
    return BuiltinResult.error(ErrorValue.E_TYPE);
  }

  private static long valueBytes(MooValue value, WorldTxn world) {
    return switch (value) {
      case IntegerValue _ -> 16;
      case BooleanValue _ -> 16;
      case ObjectValue _ -> 16;
      case ErrorValue _ -> 16;
      case AnonymousObjectValue _ -> 16;
      case FloatValue _ -> 24;
      case StringValue string -> 17L + string.length();
      case ListValue list -> {
        long bytes = 32;
        for (MooValue element : list.elements()) {
          bytes += valueBytes(element, world);
        }
        yield bytes;
      }
      case MapValue map -> {
        long bytes = 32;
        for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
          bytes += 24;
          bytes += valueBytes(entry.getKey(), world);
          bytes += valueBytes(entry.getValue(), world);
        }
        yield bytes;
      }
      case WaifValue waif -> {
        long bytes = 72;
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
    };
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

  private BuiltinResult floatingRandom(List<MooValue> arguments) {
    double minimum = arguments.size() == 2 ? ((FloatValue) arguments.get(0)).value() : 0.0;
    double maximum =
        ((FloatValue) arguments.get(arguments.size() == 2 ? 1 : 0)).value();
    double fraction = (floatingRandom.nextInt() >>> 1) / (double) Integer.MAX_VALUE;
    return BuiltinResult.value(new FloatValue(minimum + fraction * (maximum - minimum)));
  }

  private static BuiltinResult randomBytes(List<MooValue> arguments, WorldTxn world) {
    long requested = ((IntegerValue) arguments.getFirst()).value();
    if (requested < 0 || requested > 10_000) {
      return BuiltinResult.raised(
          ErrorValue.E_INVARG, StringValue.of("Invalid count"), arguments.getFirst());
    }
    byte[] random = new byte[(int) requested];
    SECURE_RANDOM.nextBytes(random);
    ByteArrayOutputStream encoded = new ByteArrayOutputStream(random.length);
    for (byte current : random) {
      int value = Byte.toUnsignedInt(current);
      if (value != '~' && ((value >= 0x21 && value <= 0x7e) || value == ' ')) {
        encoded.write(value);
      } else {
        encoded.write('~');
        encoded.write(Character.toUpperCase(Character.forDigit(value >>> 4, 16)));
        encoded.write(Character.toUpperCase(Character.forDigit(value & 0x0f, 16)));
      }
    }
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
    if (encoded.size() > maximumLength) {
      return catchable
          ? BuiltinResult.error(ErrorValue.E_QUOTA)
          : new BuiltinResult.SecondsAbort();
    }
    return BuiltinResult.value(StringValue.of(encoded.toByteArray()));
  }

  private static BuiltinResult ctime(List<MooValue> arguments) {
    long epochSecond =
        arguments.isEmpty()
            ? Instant.now().getEpochSecond()
            : Math.min(((IntegerValue) arguments.getFirst()).value(), CTIME_MAX_SECONDS);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment time = arena.allocate(ValueLayout.JAVA_LONG);
      time.set(ValueLayout.JAVA_LONG, 0, epochSecond);
      MemorySegment localTime = arena.allocate(64, Long.BYTES);
      MemorySegment converted = NativeCalls.localtimeR(time, localTime);
      if (converted.address() == 0) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      MemorySegment format = arena.allocate(32);
      format.setString(0, "%a %b %d %H:%M:%S %Y %Z");
      MemorySegment buffer = arena.allocate(128);
      long length = NativeCalls.strftime(buffer, 128L, format, localTime);
      if (length == 0) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      String text = buffer.getString(0);
      if (text.charAt(8) == '0') {
        text = text.substring(0, 8) + ' ' + text.substring(9);
      }
      return BuiltinResult.value(StringValue.of(text));
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
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment timespec = arena.allocate(16, Long.BYTES);
      int status = NativeCalls.clockGettime(clockId, timespec);
      if (status != 0) {
        return BuiltinResult.error(ErrorValue.E_FLOAT);
      }
      long seconds = timespec.get(ValueLayout.JAVA_LONG, 0);
      long nanoseconds = timespec.get(ValueLayout.JAVA_LONG, Long.BYTES);
      return BuiltinResult.value(
          new FloatValue(seconds + nanoseconds / 1_000_000_000.0));
    }
  }

  private BuiltinResult randomInteger(List<MooValue> arguments) {
    long lower = 1;
    long upper = Long.MAX_VALUE;
    if (arguments.size() == 1) {
      upper = ((IntegerValue) arguments.getFirst()).value();
      if (upper <= 0) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
    } else if (arguments.size() == 2) {
      lower = ((IntegerValue) arguments.get(0)).value();
      upper = ((IntegerValue) arguments.get(1)).value();
      if (upper < lower) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
    }
    if (lower == upper) {
      return BuiltinResult.value(new IntegerValue(lower));
    }
    long width = upper - lower + 1;
    if (width > 0) {
      return BuiltinResult.value(new IntegerValue(lower + random.nextLong(width)));
    }
    long value;
    do {
      value = random.nextLong();
    } while (value < lower || value > upper);
    return BuiltinResult.value(new IntegerValue(value));
  }

  private static BuiltinResult raise(List<MooValue> arguments) {
    MooValue code = arguments.getFirst();
    ErrorValue error = code instanceof ErrorValue errorValue ? errorValue : ErrorValue.E_INVARG;
    StringValue message =
        arguments.size() >= 2 ? (StringValue) arguments.get(1) : StringValue.of(code.toLiteral());
    MooValue value = arguments.size() >= 3 ? arguments.get(2) : new IntegerValue(0);
    return BuiltinResult.raised(error, message, value);
  }

  private static BuiltinResult listInsert(
      List<MooValue> arguments, boolean append, WorldTxn world) {
    ListValue list = (ListValue) arguments.get(0);
    MooValue value = arguments.get(1);
    long requestedPosition;
    if (arguments.size() == 2) {
      requestedPosition = append ? list.size() + 1L : 1L;
    } else {
      long supplied = ((IntegerValue) arguments.get(2)).value();
      requestedPosition = append && supplied != Long.MAX_VALUE ? supplied + 1 : supplied;
    }
    int position;
    if (requestedPosition <= 0) {
      position = 1;
    } else if (requestedPosition > list.size() + 1L) {
      position = list.size() + 1;
    } else {
      position = (int) requestedPosition;
    }
    List<MooValue> inserted = new ArrayList<>(list.elements());
    inserted.add(position - 1, value);
    return enforceListValueLimit(new ListValue(inserted), world);
  }

  private static BuiltinResult listDelete(List<MooValue> arguments, WorldTxn world) {
    ListValue list = (ListValue) arguments.get(0);
    long position = ((IntegerValue) arguments.get(1)).value();
    if (position <= 0 || position > list.size()) {
      return BuiltinResult.error(ErrorValue.E_RANGE);
    }
    List<MooValue> deleted = new ArrayList<>(list.elements());
    deleted.remove((int) position - 1);
    return enforceListValueLimit(new ListValue(deleted), world);
  }

  private static BuiltinResult listSet(List<MooValue> arguments, WorldTxn world) {
    ListValue list = (ListValue) arguments.get(0);
    long position = ((IntegerValue) arguments.get(2)).value();
    if (position <= 0 || position > list.size()) {
      return BuiltinResult.error(ErrorValue.E_RANGE);
    }
    List<MooValue> replaced = new ArrayList<>(list.elements());
    replaced.set((int) position - 1, arguments.get(1));
    return enforceListValueLimit(new ListValue(replaced), world);
  }

  private static BuiltinResult mapKeys(List<MooValue> arguments) {
    MapValue map = (MapValue) arguments.getFirst();
    return BuiltinResult.value(new ListValue(sortedMapKeys(map)));
  }

  private static List<MooValue> sortedMapKeys(MapValue map) {
    List<MooValue> keys = new ArrayList<>(map.entries().keySet());
    keys.sort(
        (left, right) -> {
          int leftRank =
              switch (left.type()) {
                case INTEGER -> 0;
                case OBJECT -> 1;
                case ERROR -> 2;
                case FLOAT -> 3;
                case BOOLEAN -> 4;
                case STRING -> 5;
                case ANONYMOUS -> 6;
                case WAIF -> 7;
                case LIST, MAP -> throw new IllegalArgumentException("collection map key");
              };
          int rightRank =
              switch (right.type()) {
                case INTEGER -> 0;
                case OBJECT -> 1;
                case ERROR -> 2;
                case FLOAT -> 3;
                case BOOLEAN -> 4;
                case STRING -> 5;
                case ANONYMOUS -> 6;
                case WAIF -> 7;
                case LIST, MAP -> throw new IllegalArgumentException("collection map key");
              };
          if (leftRank != rightRank) {
            return Integer.compare(leftRank, rightRank);
          }
          return switch (left) {
            case IntegerValue integer ->
                Long.compare(integer.value(), ((IntegerValue) right).value());
            case ObjectValue object ->
                Long.compare(object.value(), ((ObjectValue) right).value());
            case ErrorValue error ->
                Integer.compare(error.code(), ((ErrorValue) right).code());
            case FloatValue floating -> {
              double leftValue = floating.value();
              double rightValue = ((FloatValue) right).value();
              yield leftValue == rightValue ? 0 : (leftValue - rightValue < 0.0 ? -1 : 1);
            }
            case BooleanValue _ -> 0;
            case StringValue string -> string.compareIgnoringCase((StringValue) right);
            case AnonymousObjectValue _ -> left == right ? 0 : 1;
            case WaifValue _ -> 0;
            case ListValue _ -> throw new IllegalArgumentException("list map key");
            case MapValue _ -> throw new IllegalArgumentException("map map key");
          };
        });
    return keys;
  }

  private static BuiltinResult mapValues(List<MooValue> arguments) {
    MapValue map = (MapValue) arguments.getFirst();
    List<MooValue> values = new ArrayList<>();
    if (arguments.size() == 1) {
      for (MooValue key : sortedMapKeys(map)) {
        values.add(map.get(key, true).orElseThrow());
      }
      return BuiltinResult.value(new ListValue(values));
    }
    for (int index = 1; index < arguments.size(); index++) {
      final Optional<MooValue> value;
      try {
        value = map.get(arguments.get(index), true);
      } catch (IllegalArgumentException invalidKey) {
        return BuiltinResult.error(ErrorValue.E_RANGE);
      }
      if (value.isEmpty()) {
        return BuiltinResult.error(ErrorValue.E_RANGE);
      }
      values.add(value.orElseThrow());
    }
    return BuiltinResult.value(new ListValue(values));
  }

  private static BuiltinResult mapHasKey(List<MooValue> arguments) {
    MooValue key = arguments.get(1);
    if (invalidMapBuiltinKey(key)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    MapValue map = (MapValue) arguments.getFirst();
    boolean caseMatters = arguments.size() == 3 && arguments.get(2).isTruthy();
    return BuiltinResult.value(new IntegerValue(map.get(key, caseMatters).isPresent() ? 1 : 0));
  }

  private static BuiltinResult mapDelete(List<MooValue> arguments) {
    MapValue original = (MapValue) arguments.getFirst();
    MooValue requested = arguments.get(1);
    boolean multiple = requested instanceof ListValue;
    List<MooValue> keys =
        multiple ? ((ListValue) requested).elements() : List.of(requested);
    MapValue result = original;
    for (MooValue key : keys) {
      if (invalidMapBuiltinKey(key)) {
        return BuiltinResult.error(ErrorValue.E_TYPE);
      }
      Optional<MapValue> deleted = deleteMapKey(result, key);
      if (deleted.isEmpty()) {
        return multiple
            ? BuiltinResult.raised(
                ErrorValue.E_RANGE,
                StringValue.of("Key " + key.toLiteral() + " not found in map"),
                key)
            : BuiltinResult.error(ErrorValue.E_RANGE);
      }
      result = deleted.orElseThrow();
    }
    return BuiltinResult.value(result);
  }

  private static Optional<MapValue> deleteMapKey(MapValue map, MooValue key) {
    Map<MooValue, MooValue> remaining = new LinkedHashMap<>();
    boolean found = false;
    for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
      if (!found && MapValue.compareKeys(entry.getKey(), key) == 0) {
        found = true;
      } else {
        remaining.put(entry.getKey(), entry.getValue());
      }
    }
    return found ? Optional.of(new MapValue(remaining)) : Optional.empty();
  }

  private static boolean invalidMapBuiltinKey(MooValue key) {
    return key instanceof ListValue
        || key instanceof MapValue
        || key instanceof AnonymousObjectValue;
  }

  private static BuiltinResult setAdd(List<MooValue> arguments, WorldTxn world) {
    ListValue list = (ListValue) arguments.get(0);
    MooValue value = arguments.get(1);
    return enforceListValueLimit(list.elements().contains(value) ? list : list.append(value), world);
  }

  private static BuiltinResult setRemove(List<MooValue> arguments, WorldTxn world) {
    return enforceListValueLimit(setRemoveValue(arguments), world);
  }

  private static ListValue setRemoveValue(List<MooValue> arguments) {
    ListValue list = (ListValue) arguments.get(0);
    MooValue value = arguments.get(1);
    for (int index = 0; index < list.size(); index++) {
      List<MooValue> pendingLeft = new ArrayList<>();
      List<MooValue> pendingRight = new ArrayList<>();
      pendingLeft.add(list.elements().get(index));
      pendingRight.add(value);
      boolean equal = true;
      while (!pendingLeft.isEmpty()) {
        int last = pendingLeft.size() - 1;
        MooValue left = pendingLeft.remove(last);
        MooValue right = pendingRight.remove(last);
        if (left instanceof BooleanValue bool && right instanceof IntegerValue integer) {
          if (integer.value() != (bool.value() ? 1 : 0)) {
            equal = false;
            break;
          }
          continue;
        }
        if (left instanceof IntegerValue integer && right instanceof BooleanValue bool) {
          if (integer.value() != (bool.value() ? 1 : 0)) {
            equal = false;
            break;
          }
          continue;
        }
        if (left instanceof ListValue leftList) {
          if (!(right instanceof ListValue rightList) || leftList.size() != rightList.size()) {
            equal = false;
            break;
          }
          for (int nested = 0; nested < leftList.size(); nested++) {
            pendingLeft.add(leftList.elements().get(nested));
            pendingRight.add(rightList.elements().get(nested));
          }
          continue;
        }
        if (left instanceof MapValue leftMap) {
          if (!(right instanceof MapValue rightMap) || leftMap.size() != rightMap.size()) {
            equal = false;
            break;
          }
          List<Map.Entry<MooValue, MooValue>> rightEntries =
              new ArrayList<>(rightMap.entries().entrySet());
          boolean[] matched = new boolean[rightEntries.size()];
          for (Map.Entry<MooValue, MooValue> leftEntry : leftMap.entries().entrySet()) {
            int matching = -1;
            for (int candidate = 0; candidate < rightEntries.size(); candidate++) {
              if (matched[candidate]) {
                continue;
              }
              MooValue leftKey = leftEntry.getKey();
              MooValue rightKey = rightEntries.get(candidate).getKey();
              boolean keysEqual;
              if (leftKey instanceof BooleanValue bool
                  && rightKey instanceof IntegerValue integer) {
                keysEqual = integer.value() == (bool.value() ? 1 : 0);
              } else if (leftKey instanceof IntegerValue integer
                  && rightKey instanceof BooleanValue bool) {
                keysEqual = integer.value() == (bool.value() ? 1 : 0);
              } else {
                keysEqual = leftKey.equals(rightKey);
              }
              if (keysEqual) {
                matching = candidate;
                break;
              }
            }
            if (matching < 0) {
              equal = false;
              break;
            }
            matched[matching] = true;
            pendingLeft.add(leftEntry.getValue());
            pendingRight.add(rightEntries.get(matching).getValue());
          }
          if (!equal) {
            break;
          }
          continue;
        }
        if (!left.equals(right)) {
          equal = false;
          break;
        }
      }
      if (equal) {
        List<MooValue> remaining = new ArrayList<>(list.elements());
        remaining.remove(index);
        return new ListValue(remaining);
      }
    }
    return list;
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

  private static BuiltinResult allMembers(List<MooValue> arguments) {
    MooValue value = arguments.get(0);
    ListValue list = (ListValue) arguments.get(1);
    List<MooValue> positions = new ArrayList<>();
    for (int index = 0; index < list.size(); index++) {
      if (Thread.currentThread().isInterrupted()) {
        throw new CancellationException("all_members host work was canceled");
      }
      ListValue removed =
          setRemoveValue(
              List.of(
                  new ListValue(List.of(list.elements().get(index))),
                  value));
      if (removed.size() == 0) {
        positions.add(new IntegerValue(index + 1L));
      }
    }
    return BuiltinResult.value(new ListValue(positions));
  }

  private static BuiltinResult sortValues(List<MooValue> arguments) {
    ListValue values = (ListValue) arguments.getFirst();
    boolean usingSeparateKeys =
        arguments.size() >= 2 && ((ListValue) arguments.get(1)).size() > 0;
    ListValue keys =
        usingSeparateKeys ? (ListValue) arguments.get(1) : values;
    if (keys.size() == 0) {
      return BuiltinResult.value(new ListValue(List.of()));
    }
    if (usingSeparateKeys && values.size() != keys.size()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    MooValue.Type keyType = keys.elements().getFirst().type();
    if (!sortableType(keyType)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    List<Integer> order = new ArrayList<>(keys.size());
    for (int index = 0; index < keys.size(); index++) {
      if (Thread.currentThread().isInterrupted()) {
        throw new CancellationException("sort host work was canceled");
      }
      if (keys.elements().get(index).type() != keyType) {
        return BuiltinResult.error(ErrorValue.E_TYPE);
      }
      order.add(index);
    }

    boolean natural = arguments.size() >= 3 && arguments.get(2).isTruthy();
    order.sort(
        (left, right) -> {
          if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("sort host work was canceled");
          }
          return compareSortValues(
              keys.elements().get(left), keys.elements().get(right), natural);
        });
    if (arguments.size() >= 4 && arguments.get(3).isTruthy()) {
      java.util.Collections.reverse(order);
    }

    List<MooValue> sorted = new ArrayList<>(values.size());
    for (int index : order) {
      sorted.add(values.elements().get(index));
    }
    return BuiltinResult.value(new ListValue(sorted));
  }

  private static boolean sortableType(MooValue.Type type) {
    return switch (type) {
      case INTEGER, OBJECT, ERROR, FLOAT, BOOLEAN, STRING -> true;
      case LIST, MAP, ANONYMOUS, WAIF -> false;
    };
  }

  private static int compareSortValues(MooValue left, MooValue right, boolean natural) {
    return switch (left) {
      case IntegerValue integer -> Long.compare(integer.value(), ((IntegerValue) right).value());
      case ObjectValue object -> Long.compare(object.value(), ((ObjectValue) right).value());
      case ErrorValue error -> Integer.compare(error.code(), ((ErrorValue) right).code());
      case FloatValue floating -> {
        double leftValue = floating.value();
        double rightValue = ((FloatValue) right).value();
        yield leftValue < rightValue ? -1 : (leftValue > rightValue ? 1 : 0);
      }
      case BooleanValue _ -> 0;
      case StringValue string ->
          natural
              ? compareNaturallyIgnoringCase(string, (StringValue) right)
              : compareCStringIgnoringCase(string, (StringValue) right);
      case ListValue _ -> throw new IllegalArgumentException("list sort key");
      case MapValue _ -> throw new IllegalArgumentException("map sort key");
      case AnonymousObjectValue _ ->
          throw new IllegalArgumentException("anonymous sort key");
      case WaifValue _ -> throw new IllegalArgumentException("waif sort key");
    };
  }

  private static int compareCStringIgnoringCase(StringValue left, StringValue right) {
    byte[] leftBytes = left.bytes();
    byte[] rightBytes = right.bytes();
    int index = 0;
    while (true) {
      int leftByte = foldAscii(cStringByte(leftBytes, index));
      int rightByte = foldAscii(cStringByte(rightBytes, index));
      if (leftByte != rightByte) {
        return Integer.compare(leftByte, rightByte);
      }
      if (leftByte == 0) {
        return 0;
      }
      index++;
    }
  }

  private static int compareNaturallyIgnoringCase(StringValue left, StringValue right) {
    byte[] leftBytes = left.bytes();
    byte[] rightBytes = right.bytes();
    int leftIndex = 0;
    int rightIndex = 0;
    while (true) {
      int leftByte = cStringByte(leftBytes, leftIndex);
      int rightByte = cStringByte(rightBytes, rightIndex);
      while (isAsciiSpace(leftByte)) {
        leftByte = cStringByte(leftBytes, ++leftIndex);
      }
      while (isAsciiSpace(rightByte)) {
        rightByte = cStringByte(rightBytes, ++rightIndex);
      }
      if (isAsciiDigit(leftByte) && isAsciiDigit(rightByte)) {
        int result =
            leftByte == '0' || rightByte == '0'
                ? compareLeftAlignedDigits(leftBytes, leftIndex, rightBytes, rightIndex)
                : compareRightAlignedDigits(leftBytes, leftIndex, rightBytes, rightIndex);
        if (result != 0) {
          return result;
        }
      }
      if (leftByte == 0 && rightByte == 0) {
        return 0;
      }
      leftByte = foldAscii(leftByte);
      rightByte = foldAscii(rightByte);
      if (leftByte != rightByte) {
        return Integer.compare(leftByte, rightByte);
      }
      leftIndex++;
      rightIndex++;
    }
  }

  private static int compareLeftAlignedDigits(
      byte[] left, int leftIndex, byte[] right, int rightIndex) {
    while (true) {
      int leftByte = cStringByte(left, leftIndex++);
      int rightByte = cStringByte(right, rightIndex++);
      if (!isAsciiDigit(leftByte) && !isAsciiDigit(rightByte)) {
        return 0;
      }
      if (!isAsciiDigit(leftByte)) {
        return -1;
      }
      if (!isAsciiDigit(rightByte)) {
        return 1;
      }
      if (leftByte != rightByte) {
        return Integer.compare(leftByte, rightByte);
      }
    }
  }

  private static int compareRightAlignedDigits(
      byte[] left, int leftIndex, byte[] right, int rightIndex) {
    int bias = 0;
    while (true) {
      int leftByte = cStringByte(left, leftIndex++);
      int rightByte = cStringByte(right, rightIndex++);
      if (!isAsciiDigit(leftByte) && !isAsciiDigit(rightByte)) {
        return bias;
      }
      if (!isAsciiDigit(leftByte)) {
        return -1;
      }
      if (!isAsciiDigit(rightByte)) {
        return 1;
      }
      if (bias == 0 && leftByte != rightByte) {
        bias = Integer.compare(leftByte, rightByte);
      }
    }
  }

  private static int cStringByte(byte[] value, int index) {
    return index >= value.length ? 0 : Byte.toUnsignedInt(value[index]);
  }

  private static boolean isAsciiDigit(int value) {
    return value >= '0' && value <= '9';
  }

  private static boolean isAsciiSpace(int value) {
    return value == ' '
        || value == '\t'
        || value == '\n'
        || value == '\r'
        || value == '\f'
        || value == 0x0B;
  }

  private static BuiltinResult setThreadMode(List<MooValue> arguments, boolean threadMode) {
    if (arguments.isEmpty()) {
      return BuiltinResult.value(new IntegerValue(threadMode ? 1 : 0));
    }
    return new BuiltinResult.ThreadMode(arguments.getFirst().isTruthy());
  }

  private static BuiltinResult stringSubstitute(List<MooValue> arguments) {
    byte[] source = ((StringValue) arguments.get(0)).bytes();
    byte[] what = ((StringValue) arguments.get(1)).bytes();
    byte[] replacement = ((StringValue) arguments.get(2)).bytes();
    if (what.length == 0) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    boolean caseMatters = arguments.size() == 4 && arguments.get(3).isTruthy();
    ByteArrayOutputStream substituted = new ByteArrayOutputStream(source.length);
    int position = 0;
    while (position <= source.length - what.length) {
      if (matchesAt(source, position, what, caseMatters)) {
        substituted.writeBytes(replacement);
        position += what.length;
      } else {
        substituted.write(source[position]);
        position++;
      }
    }
    substituted.write(source, position, source.length - position);
    return BuiltinResult.value(StringValue.of(substituted.toByteArray()));
  }

  private static BuiltinResult stringTranslate(List<MooValue> arguments) {
    byte[] source = ((StringValue) arguments.get(0)).bytes();
    byte[] from = ((StringValue) arguments.get(1)).bytes();
    byte[] to = ((StringValue) arguments.get(2)).bytes();
    boolean caseMatters = arguments.size() == 4 && arguments.get(3).isTruthy();
    int[] translations = new int[256];
    Arrays.fill(translations, -2);
    for (int index = 0; index < from.length; index++) {
      int key = Byte.toUnsignedInt(from[index]);
      if (!caseMatters) {
        key = foldAscii(key);
      }
      translations[key] = index < to.length ? Byte.toUnsignedInt(to[index]) : -1;
    }
    ByteArrayOutputStream translated = new ByteArrayOutputStream(source.length);
    for (byte sourceByte : source) {
      int original = Byte.toUnsignedInt(sourceByte);
      int key = caseMatters ? original : foldAscii(original);
      int replacement = translations[key];
      if (replacement == -2) {
        translated.write(original);
      } else if (replacement >= 0) {
        if (!caseMatters) {
          replacement = preserveAsciiCase(original, replacement);
        }
        translated.write(replacement);
      }
    }
    return BuiltinResult.value(StringValue.of(translated.toByteArray()));
  }

  private static int preserveAsciiCase(int source, int replacement) {
    if (source >= 'A' && source <= 'Z' && replacement >= 'a' && replacement <= 'z') {
      return replacement - ('a' - 'A');
    }
    if (source >= 'a' && source <= 'z' && replacement >= 'A' && replacement <= 'Z') {
      return replacement + ('a' - 'A');
    }
    return replacement;
  }

  private BuiltinResult parseAnsi(List<MooValue> arguments) {
    byte[] parsed = ((StringValue) arguments.getFirst()).bytes();
    List<AnsiReplacement> replacements =
        List.of(
            new AnsiReplacement("[red]", "\u001b[31m"),
            new AnsiReplacement("[green]", "\u001b[32m"),
            new AnsiReplacement("[yellow]", "\u001b[33m"),
            new AnsiReplacement("[blue]", "\u001b[34m"),
            new AnsiReplacement("[purple]", "\u001b[35m"),
            new AnsiReplacement("[cyan]", "\u001b[36m"),
            new AnsiReplacement("[normal]", "\u001b[0m"),
            new AnsiReplacement("[inverse]", "\u001b[7m"),
            new AnsiReplacement("[underline]", "\u001b[4m"),
            new AnsiReplacement("[bold]", "\u001b[1m"),
            new AnsiReplacement("[bright]", "\u001b[1m"),
            new AnsiReplacement("[unbold]", "\u001b[22m"),
            new AnsiReplacement("[blink]", "\u001b[5m"),
            new AnsiReplacement("[unblink]", "\u001b[25m"),
            new AnsiReplacement("[magenta]", "\u001b[35m"),
            new AnsiReplacement("[unbright]", "\u001b[22m"),
            new AnsiReplacement("[white]", "\u001b[37m"),
            new AnsiReplacement("[gray]", "\u001b[1;30m"),
            new AnsiReplacement("[grey]", "\u001b[1;30m"),
            new AnsiReplacement("[beep]", "\u0007"),
            new AnsiReplacement("[black]", "\u001b[30m"),
            new AnsiReplacement("[b:black]", "\u001b[40m"),
            new AnsiReplacement("[b:red]", "\u001b[41m"),
            new AnsiReplacement("[b:green]", "\u001b[42m"),
            new AnsiReplacement("[b:yellow]", "\u001b[43m"),
            new AnsiReplacement("[b:blue]", "\u001b[44m"),
            new AnsiReplacement("[b:magenta]", "\u001b[45m"),
            new AnsiReplacement("[b:purple]", "\u001b[45m"),
            new AnsiReplacement("[b:cyan]", "\u001b[46m"),
            new AnsiReplacement("[b:white]", "\u001b[47m"));
    for (AnsiReplacement replacement : replacements) {
      parsed =
          replaceAsciiIgnoringCase(
              parsed, StringValue.of(replacement.tag()).bytes(), StringValue.of(replacement.code()).bytes());
    }
    byte[][] randomCodes = {
      StringValue.of("\u001b[31m").bytes(),
      StringValue.of("\u001b[32m").bytes(),
      StringValue.of("\u001b[33m").bytes(),
      StringValue.of("\u001b[34m").bytes(),
      StringValue.of("\u001b[35m").bytes(),
      StringValue.of("\u001b[35m").bytes()
    };
    byte[] randomTag = StringValue.of("[random]").bytes();
    ByteArrayOutputStream randomized = new ByteArrayOutputStream(parsed.length);
    int position = 0;
    while (position < parsed.length) {
      if (matchesAt(parsed, position, randomTag, false)) {
        randomized.writeBytes(randomCodes[random.nextInt(6)]);
        position += randomTag.length;
      } else {
        randomized.write(parsed[position++]);
      }
    }
    parsed =
        replaceAsciiIgnoringCase(
            randomized.toByteArray(), StringValue.of("[null]").bytes(), new byte[0]);
    return BuiltinResult.value(StringValue.of(parsed));
  }

  private static BuiltinResult removeAnsi(List<MooValue> arguments) {
    byte[] stripped = ((StringValue) arguments.getFirst()).bytes();
    List<String> tags =
        List.of(
            "[red]", "[green]", "[yellow]", "[blue]", "[purple]", "[cyan]",
            "[normal]", "[inverse]", "[underline]", "[bold]", "[bright]", "[unbold]",
            "[blink]", "[unblink]", "[magenta]", "[unbright]", "[white]", "[gray]",
            "[grey]", "[beep]", "[black]", "[b:black]", "[b:red]", "[b:green]",
            "[b:yellow]", "[b:blue]", "[b:magenta]", "[b:purple]", "[b:cyan]",
            "[b:white]", "[random]", "[null]");
    for (String tag : tags) {
      stripped = replaceAsciiIgnoringCase(stripped, StringValue.of(tag).bytes(), new byte[0]);
    }
    return BuiltinResult.value(StringValue.of(stripped));
  }

  private static BuiltinResult simplexNoise(List<MooValue> arguments) {
    ListValue coordinates = (ListValue) arguments.getFirst();
    double[] values = new double[coordinates.size()];
    for (int index = 0; index < coordinates.size(); index++) {
      MooValue coordinate = coordinates.elements().get(index);
      if (!(coordinate instanceof FloatValue value)) {
        return BuiltinResult.error(ErrorValue.E_TYPE);
      }
      values[index] = value.value();
    }
    if (values.length < 1 || values.length > 4) {
      return BuiltinResult.value(ErrorValue.E_TYPE);
    }
    return BuiltinResult.value(new FloatValue(SimplexNoise.noise(values)));
  }

  private static byte[] replaceAsciiIgnoringCase(
      byte[] source, byte[] what, byte[] replacement) {
    ByteArrayOutputStream output = new ByteArrayOutputStream(source.length);
    int position = 0;
    while (what.length > 0 && position <= source.length - what.length) {
      if (matchesAt(source, position, what, false)) {
        output.writeBytes(replacement);
        position += what.length;
      } else {
        output.write(source[position++]);
      }
    }
    output.write(source, position, source.length - position);
    return output.toByteArray();
  }

  private record AnsiReplacement(String tag, String code) {}

  private static BuiltinResult explode(List<MooValue> arguments) {
    byte[] source = ((StringValue) arguments.getFirst()).bytes();
    byte[] delimiterArgument =
        arguments.size() >= 2 ? ((StringValue) arguments.get(1)).bytes() : new byte[0];
    byte delimiter = delimiterArgument.length == 0 ? (byte) ' ' : delimiterArgument[0];
    boolean preserveEmpty = arguments.size() == 3 && arguments.get(2).isTruthy();
    List<MooValue> pieces = new ArrayList<>();

    if (preserveEmpty) {
      int start = 0;
      for (int end = 0; end <= source.length; end++) {
        if (end == source.length || source[end] == delimiter) {
          pieces.add(StringValue.of(Arrays.copyOfRange(source, start, end)));
          start = end + 1;
        }
      }
    } else {
      int start = 0;
      while (start < source.length) {
        while (start < source.length && source[start] == delimiter) {
          start++;
        }
        int end = start;
        while (end < source.length && source[end] != delimiter) {
          end++;
        }
        if (start < end) {
          pieces.add(StringValue.of(Arrays.copyOfRange(source, start, end)));
        }
        start = end;
      }
    }
    return BuiltinResult.value(new ListValue(pieces));
  }

  private static BuiltinResult reverse(List<MooValue> arguments) {
    MooValue value = arguments.getFirst();
    if (value instanceof StringValue string) {
      byte[] bytes = string.bytes();
      for (int left = 0, right = bytes.length - 1; left < right; left++, right--) {
        byte exchanged = bytes[left];
        bytes[left] = bytes[right];
        bytes[right] = exchanged;
      }
      return BuiltinResult.value(StringValue.of(bytes));
    }
    if (value instanceof ListValue list) {
      List<MooValue> elements = list.elements();
      List<MooValue> reversed = new ArrayList<>(elements.size());
      for (int index = elements.size() - 1; index >= 0; index--) {
        reversed.add(elements.get(index));
      }
      return BuiltinResult.value(new ListValue(reversed));
    }
    return BuiltinResult.error(ErrorValue.E_INVARG);
  }

  private static BuiltinResult stringIndex(List<MooValue> arguments, boolean reverse) {
    byte[] source = ((StringValue) arguments.get(0)).bytes();
    byte[] what = ((StringValue) arguments.get(1)).bytes();
    boolean caseMatters = arguments.size() >= 3 && arguments.get(2).isTruthy();
    long offset = arguments.size() == 4 ? ((IntegerValue) arguments.get(3)).value() : 0;
    if ((!reverse && offset < 0) || (reverse && offset > 0)) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    if (reverse) {
      long prefixLength = source.length + offset;
      if (prefixLength < 0) {
        return BuiltinResult.value(new IntegerValue(0));
      }
      int length = (int) Math.min(prefixLength, source.length);
      for (int position = length - what.length; position >= 0; position--) {
        if (matchesAt(source, position, what, caseMatters)) {
          return BuiltinResult.value(new IntegerValue(position + 1L));
        }
      }
      return BuiltinResult.value(new IntegerValue(0));
    }

    if (offset > source.length) {
      return BuiltinResult.value(new IntegerValue(0));
    }
    int start = (int) offset;
    for (int position = start; position <= source.length - what.length; position++) {
      if (matchesAt(source, position, what, caseMatters)) {
        return BuiltinResult.value(new IntegerValue(position - start + 1L));
      }
    }
    return BuiltinResult.value(new IntegerValue(0));
  }

  private static BuiltinResult stringCompare(List<MooValue> arguments) {
    byte[] left = ((StringValue) arguments.get(0)).bytes();
    byte[] right = ((StringValue) arguments.get(1)).bytes();
    int commonLength = Math.min(left.length, right.length);
    for (int index = 0; index < commonLength; index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return BuiltinResult.value(new IntegerValue(comparison));
      }
    }
    return BuiltinResult.value(new IntegerValue(Integer.compare(left.length, right.length)));
  }

  private static BuiltinResult crypt(List<MooValue> arguments, WorldTxn world, long programmer) {
    byte[] password = ((StringValue) arguments.getFirst()).bytes();
    String salt;
    if (arguments.size() == 1 || ((StringValue) arguments.get(1)).bytes().length < 2) {
      String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789./";
      salt =
          new String(
              new char[] {
                alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length())),
                alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length()))
              });
    } else {
      salt = ((StringValue) arguments.get(1)).text();
    }
    OptionalLong strength = cryptStrength(salt);
    if (strength.isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    long selectedStrength = strength.orElseThrow();
    if (!isWizard(world, programmer)
        && (isRecognizedBcrypt(salt) ? selectedStrength != 5 : selectedStrength != 0)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    String result = nativeCrypt(password, StringValue.of(salt).bytes());
    if (isRecognizedBcrypt(salt) && (result.equals("*0") || result.equals("*1"))) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    return BuiltinResult.value(StringValue.of(result));
  }

  private static OptionalLong cryptStrength(String salt) {
    if (isRecognizedBcrypt(salt)) {
      int separator = salt.indexOf('$', 4);
      if (separator < 0) {
        return OptionalLong.of(0);
      }
      String cost = salt.substring(4, separator);
      try {
        long parsed = Long.parseLong(cost);
        return parsed >= 4 && parsed <= 31 ? OptionalLong.of(parsed) : OptionalLong.empty();
      } catch (NumberFormatException error) {
        return OptionalLong.empty();
      }
    }
    if (salt.startsWith("$5$rounds=") || salt.startsWith("$6$rounds=")) {
      int separator = salt.indexOf('$', 10);
      if (separator < 0) {
        return OptionalLong.empty();
      }
      try {
        long rounds = Long.parseLong(salt.substring(10, separator));
        return rounds >= 1000 && rounds <= 999_999_999
            ? OptionalLong.of(rounds)
            : OptionalLong.empty();
      } catch (NumberFormatException error) {
        return OptionalLong.empty();
      }
    }
    return OptionalLong.of(0);
  }

  private static boolean isRecognizedBcrypt(String salt) {
    return salt.startsWith("$2a$") || salt.startsWith("$2x$") || salt.startsWith("$2y$");
  }

  @SuppressWarnings("restricted")
  private static String nativeCrypt(byte[] password, byte[] salt) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment passwordString = nullTerminated(arena, password);
      MemorySegment saltString = nullTerminated(arena, salt);
      MemorySegment result = NativeCalls.crypt(passwordString, saltString);
      if (result.equals(MemorySegment.NULL)) {
        return "*0";
      }
      return result.reinterpret(128).getString(0, StringValue.charset());
    }
  }

  private static MemorySegment nullTerminated(Arena arena, byte[] bytes) {
    MemorySegment terminated = arena.allocate(Math.addExact(bytes.length, 1));
    terminated.asSlice(0, bytes.length).copyFrom(MemorySegment.ofArray(bytes));
    terminated.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
    return terminated;
  }

  private static BuiltinResult argon2(List<MooValue> arguments) {
    byte[] password = cStringBytes((StringValue) arguments.get(0));
    byte[] salt = cStringBytes((StringValue) arguments.get(1));
    final Argon2Settings settings;
    try {
      settings = argon2Settings(arguments);
    } catch (IllegalArgumentException | ArithmeticException error) {
      return BuiltinResult.error(ErrorValue.E_INVIND);
    }
    final byte[] hash;
    try {
      hash =
          argon2Hash(
              password,
              salt,
              settings.iterations(),
              settings.memoryKiB(),
              settings.parallelism(),
              32);
    } catch (IllegalArgumentException error) {
      return BuiltinResult.error(ErrorValue.E_INVIND);
    }
    Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
    String encoded =
        "$argon2id$v=19$m="
            + settings.memoryKiB()
            + ",t="
            + settings.iterations()
            + ",p="
            + settings.parallelism()
            + "$"
            + encoder.encodeToString(salt)
            + "$"
            + encoder.encodeToString(hash);
    return BuiltinResult.value(StringValue.of(encoded));
  }

  private static Argon2Settings argon2Settings(List<MooValue> arguments) {
    return new Argon2Settings(
        optionalPositiveInt(arguments, 2, 3),
        optionalPositiveInt(arguments, 3, 4096),
        optionalPositiveInt(arguments, 4, 1));
  }

  private record Argon2Settings(int iterations, int memoryKiB, int parallelism) {}

  static BuiltinResult argon2Verify(
      List<MooValue> arguments, ServerLog serverLog, Argon2Hasher hasher) {
    String encoded = ((StringValue) arguments.get(0)).text();
    byte[] password = cStringBytes((StringValue) arguments.get(1));
    String[] fields = encoded.split("\\$", -1);
    if (fields.length != 6
        || !fields[0].isEmpty()
        || !fields[1].equals("argon2id")
        || !fields[2].equals("v=19")) {
      return BuiltinResult.value(new IntegerValue(0));
    }
    int memoryKiB = 0;
    int iterations = 0;
    int parallelism = 0;
    for (String parameter : fields[3].split(",", -1)) {
      String[] pair = parameter.split("=", 2);
      if (pair.length != 2) {
        return BuiltinResult.value(new IntegerValue(0));
      }
      final int value;
      try {
        value = Integer.parseInt(pair[1]);
      } catch (NumberFormatException malformed) {
        return BuiltinResult.value(new IntegerValue(0));
      }
      switch (pair[0]) {
        case "m" -> memoryKiB = value;
        case "t" -> iterations = value;
        case "p" -> parallelism = value;
        default -> {
          return BuiltinResult.value(new IntegerValue(0));
        }
      }
    }
    if (memoryKiB <= 0 || iterations <= 0 || parallelism <= 0) {
      return BuiltinResult.value(new IntegerValue(0));
    }
    byte[] salt;
    try {
      salt = Base64.getDecoder().decode(fields[4]);
    } catch (IllegalArgumentException malformed) {
      return BuiltinResult.value(new IntegerValue(0));
    }
    byte[] expected;
    try {
      expected = Base64.getDecoder().decode(fields[5]);
    } catch (IllegalArgumentException malformed) {
      return BuiltinResult.value(new IntegerValue(0));
    }
    if (salt.length == 0 || expected.length == 0) {
      return BuiltinResult.value(new IntegerValue(0));
    }
    final byte[] actual;
    try {
      actual =
          hasher.hash(password, salt, iterations, memoryKiB, parallelism, expected.length);
    } catch (RuntimeException failure) {
      serverLog.error(
          "ARGON2 VERIFY: internal failure: " + failure.getClass().getSimpleName());
      return BuiltinResult.error(ErrorValue.E_QUOTA);
    }
    return BuiltinResult.value(new IntegerValue(MessageDigest.isEqual(expected, actual) ? 1 : 0));
  }

  @FunctionalInterface
  interface Argon2Hasher {
    byte[] hash(
        byte[] password,
        byte[] salt,
        int iterations,
        int memoryKiB,
        int parallelism,
        int outputLength);
  }

  private static byte[] argon2Hash(
      byte[] password,
      byte[] salt,
      int iterations,
      int memoryKiB,
      int parallelism,
      int outputLength) {
    Argon2Parameters parameters =
        new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKiB)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build();
    Argon2BytesGenerator generator = new Argon2BytesGenerator();
    generator.init(parameters);
    byte[] output = new byte[outputLength];
    generator.generateBytes(password, output);
    return output;
  }

  private static int optionalPositiveInt(
      List<MooValue> arguments, int index, int defaultValue) {
    if (arguments.size() <= index) {
      return defaultValue;
    }
    long value = ((IntegerValue) arguments.get(index)).value();
    if (value <= 0) {
      throw new IllegalArgumentException("argon2 parameter must be positive");
    }
    return Math.toIntExact(value);
  }

  private static byte[] cStringBytes(StringValue value) {
    byte[] bytes = value.bytes();
    int length = 0;
    while (length < bytes.length && bytes[length] != 0) {
      length++;
    }
    return Arrays.copyOf(bytes, length);
  }

  private static BuiltinResult decodeBinary(List<MooValue> arguments) {
    byte[] binary = ((StringValue) arguments.getFirst()).bytes();
    ByteArrayOutputStream raw = new ByteArrayOutputStream(binary.length);
    for (int index = 0; index < binary.length; index++) {
      int value = Byte.toUnsignedInt(binary[index]);
      if (value != '~') {
        raw.write(value);
        continue;
      }
      if (index + 2 >= binary.length) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      int high = Character.digit((char) Byte.toUnsignedInt(binary[++index]), 16);
      int low = Character.digit((char) Byte.toUnsignedInt(binary[++index]), 16);
      if (high < 0 || low < 0) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      raw.write((high << 4) | low);
    }

    byte[] decoded = raw.toByteArray();
    if (arguments.size() == 2 && arguments.get(1).isTruthy()) {
      return BuiltinResult.value(
          new ListValue(
              java.util.stream.IntStream.range(0, decoded.length)
                  .mapToObj(index -> new IntegerValue(Byte.toUnsignedInt(decoded[index])))
                  .map(MooValue.class::cast)
                  .toList()));
    }

    List<MooValue> values = new ArrayList<>();
    ByteArrayOutputStream printable = new ByteArrayOutputStream();
    for (byte current : decoded) {
      int value = Byte.toUnsignedInt(current);
      if ((value >= 0x21 && value <= 0x7e) || value == ' ' || value == '\t') {
        printable.write(value);
      } else {
        if (printable.size() != 0) {
          values.add(StringValue.of(printable.toByteArray()));
          printable.reset();
        }
        values.add(new IntegerValue(value));
      }
    }
    if (printable.size() != 0) {
      values.add(StringValue.of(printable.toByteArray()));
    }
    return BuiltinResult.value(new ListValue(values));
  }

  private static BuiltinResult generateJson(List<MooValue> arguments) {
    JsonCodec.Mode mode = JsonCodec.Mode.COMMON_SUBSET;
    if (arguments.size() >= 2) {
      mode = JsonCodec.Mode.parse((StringValue) arguments.get(1)).orElse(null);
      if (mode == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
    }
    try {
      return BuiltinResult.value(
          JsonCodec.generate(
              arguments.getFirst(), mode, arguments.size() >= 3 && arguments.get(2).isTruthy()));
    } catch (IllegalArgumentException error) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
  }

  private static BuiltinResult parseJson(List<MooValue> arguments) {
    JsonCodec.Mode mode = JsonCodec.Mode.COMMON_SUBSET;
    if (arguments.size() == 2) {
      mode = JsonCodec.Mode.parse((StringValue) arguments.get(1)).orElse(null);
      if (mode == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
    }
    try {
      return BuiltinResult.value(JsonCodec.parse((StringValue) arguments.getFirst(), mode));
    } catch (IllegalArgumentException error) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
  }

  private static BuiltinResult encodeBinary(List<MooValue> arguments) {
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    for (MooValue argument : arguments) {
      if (!appendBinaryBytes(raw, argument, 0, 255)) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
    }

    ByteArrayOutputStream encoded = new ByteArrayOutputStream(raw.size());
    for (byte current : raw.toByteArray()) {
      int value = Byte.toUnsignedInt(current);
      if (value != '~' && ((value >= 0x21 && value <= 0x7e) || value == ' ')) {
        encoded.write(value);
      } else {
        encoded.write('~');
        encoded.write(Character.toUpperCase(Character.forDigit(value >>> 4, 16)));
        encoded.write(Character.toUpperCase(Character.forDigit(value & 0x0f, 16)));
      }
    }
    return BuiltinResult.value(StringValue.of(encoded.toByteArray()));
  }

  private static BuiltinResult chr(List<MooValue> arguments, WorldTxn world, long programmer) {
    int minimum = isWizard(world, programmer) ? 0 : 32;
    int maximum = isWizard(world, programmer) ? 255 : 254;
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    for (MooValue argument : arguments) {
      if (!appendBinaryBytes(raw, argument, minimum, maximum)) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
    }
    return BuiltinResult.value(StringValue.of(raw.toByteArray()));
  }

  private static BuiltinResult disassemble(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (!(arguments.get(0) instanceof ObjectValue object)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    MooValue descriptor = arguments.get(1);
    if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    WorldVerb verb;
    if (descriptor instanceof IntegerValue integer) {
      long index = integer.value() - 1;
      if (index >= target.verbs().size()) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verb = target.verbs().get((int) index);
    } else {
      WorldVerb candidate =
          world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null);
      if (candidate == null || !target.verbs().contains(candidate)) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verb = candidate;
    }

    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && ObjectFlags.isWizard(actor.flags());
    if (verb.owner() != programmer && !wizard && (verb.permissions() & 1) == 0) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    List<MooValue> lines =
        new MooCompiler().compile(verb.programSource()).disassemble().lines()
            .map(StringValue::of)
            .map(MooValue.class::cast)
            .toList();
    return BuiltinResult.value(new ListValue(lines));
  }

  private static boolean appendBinaryBytes(
      ByteArrayOutputStream output, MooValue value, int minimum, int maximum) {
    if (value instanceof StringValue string) {
      output.writeBytes(string.bytes());
      return true;
    }
    if (value instanceof IntegerValue integer) {
      if (integer.value() < minimum || integer.value() > maximum) {
        return false;
      }
      output.write((int) integer.value());
      return true;
    }
    if (value instanceof ListValue list) {
      for (MooValue element : list.elements()) {
        if (!appendBinaryBytes(output, element, minimum, maximum)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static boolean matchesAt(
      byte[] source, int position, byte[] what, boolean caseMatters) {
    if (position < 0 || position + what.length > source.length) {
      return false;
    }
    for (int index = 0; index < what.length; index++) {
      int sourceByte = Byte.toUnsignedInt(source[position + index]);
      int whatByte = Byte.toUnsignedInt(what[index]);
      if (!caseMatters) {
        sourceByte = foldAscii(sourceByte);
        whatByte = foldAscii(whatByte);
      }
      if (sourceByte != whatByte) {
        return false;
      }
    }
    return true;
  }

  private static int foldAscii(int value) {
    return value >= 'A' && value <= 'Z' ? value + ('a' - 'A') : value;
  }

  private static BuiltinResult create(List<MooValue> arguments, WorldTxn world, long programmer) {
    ParentArgument parents = parentArgument(arguments.getFirst(), world);
    if (parents.error().filter(ErrorValue.E_TYPE::equals).isPresent()) {
      return BuiltinResult.error(parents.error().orElseThrow());
    }
    long owner = programmer;
    boolean ownerSpecified = false;
    boolean anonymous = false;
    boolean anonymousSpecified = false;
    ListValue initializeArguments = new ListValue(List.of());
    boolean initializerSpecified = false;
    for (int index = 1; index < arguments.size(); index++) {
      MooValue argument = arguments.get(index);
      if (index == 1 && argument instanceof ObjectValue requestedOwner) {
        owner = requestedOwner.value();
        ownerSpecified = true;
      } else if (argument instanceof IntegerValue requestedAnonymous && !anonymousSpecified) {
        anonymous = requestedAnonymous.value() != 0;
        anonymousSpecified = true;
      } else if (argument instanceof ListValue requestedArguments && !initializerSpecified) {
        initializeArguments = requestedArguments;
        initializerSpecified = true;
      } else {
        return BuiltinResult.error(ErrorValue.E_TYPE);
      }
    }
    if (parents.error().isPresent()) {
      return BuiltinResult.error(parents.error().orElseThrow());
    }
    if ((anonymous && owner == -1) || (owner != -1 && world.object(owner).isEmpty())) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    boolean wizard = isWizard(world, programmer);
    if ((ownerSpecified && owner != programmer && !wizard)
        || !parentsAllowed(
            parents.ids(),
            world,
            programmer,
            anonymous ? ObjectFlags.FLAG_ANONYMOUS : ObjectFlags.FLAG_FERTILE)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    if (owner != -1 && !decrementOwnershipQuota(world, owner)) {
      return BuiltinResult.error(ErrorValue.E_QUOTA);
    }
    if (anonymous) {
      final AnonymousObjectValue created;
      try {
        created = world.createAnonymousObject(parents.ids(), owner);
      } catch (IllegalArgumentException error) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      return world.verb(created, "initialize", true).isPresent()
          ? new BuiltinResult.Initialize(created, initializeArguments)
          : BuiltinResult.value(created);
    }
    final WorldObject created;
    try {
      created = world.createObject(parents.ids(), owner);
    } catch (IllegalArgumentException error) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    ObjectValue identity = new ObjectValue(created.id());
    return world.verb(created.id(), "initialize", true).isPresent()
        ? new BuiltinResult.Initialize(identity, initializeArguments)
        : BuiltinResult.value(identity);
  }

  private static BuiltinResult recreate(List<MooValue> arguments, WorldTxn world, long programmer) {
    long objectId = ((ObjectValue) arguments.getFirst()).value();
    if (objectId <= 0 || objectId > world.maximumObjectId() || world.object(objectId).isPresent()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    ParentArgument parents = parentArgument(arguments.get(1), world);
    if (parents.error().isPresent()) {
      return BuiltinResult.error(parents.error().orElseThrow());
    }
    long owner = programmer;
    if (arguments.size() == 3) {
      long requestedOwner = ((ObjectValue) arguments.get(2)).value();
      if (world.object(requestedOwner).isPresent()) {
        owner = requestedOwner;
      }
    }
    if ((owner != programmer && !isWizard(world, programmer))
        || !parentsAllowed(parents.ids(), world, programmer, ObjectFlags.FLAG_FERTILE)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    if (world.object(owner).isPresent() && !decrementOwnershipQuota(world, owner)) {
      return BuiltinResult.error(ErrorValue.E_QUOTA);
    }
    final WorldObject created;
    try {
      created = world.recreateObject(objectId, parents.ids(), owner);
    } catch (IllegalArgumentException error) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    ObjectValue identity = new ObjectValue(created.id());
    return world.verb(created.id(), "initialize", true).isPresent()
        ? new BuiltinResult.Initialize(identity, new ListValue(List.of()))
        : BuiltinResult.value(identity);
  }

  private static boolean decrementOwnershipQuota(WorldTxn world, long owner) {
    MooValue quota = world.readObjectProperty(owner, "ownership_quota").orElse(null);
    if (!(quota instanceof IntegerValue integer)) {
      return true;
    }
    if (integer.value() <= 0) {
      return false;
    }
    if (!world
        .writeObjectProperty(owner, "ownership_quota", new IntegerValue(integer.value() - 1))
        .isOk()) {
      throw new IllegalStateException("ownership_quota disappeared during create");
    }
    return true;
  }

  private static BuiltinResult parent(List<MooValue> arguments, WorldTxn world) {
    MooValue value = arguments.getFirst();
    List<Long> parents;
    if (value instanceof ObjectValue object) {
      WorldObject target = world.object(object.value()).orElse(null);
      if (target == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      parents = target.parents();
    } else if (value instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
      if (target == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      parents = target.parents();
    } else if (value instanceof WaifValue) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    } else {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    return BuiltinResult.value(new ObjectValue(parents.isEmpty() ? -1 : parents.getFirst()));
  }

  private static BuiltinResult parents(List<MooValue> arguments, WorldTxn world) {
    MooValue value = arguments.getFirst();
    List<Long> parents;
    if (value instanceof ObjectValue object) {
      WorldObject target = world.object(object.value()).orElse(null);
      if (target == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      parents = target.parents();
    } else if (value instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
      if (target == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      parents = target.parents();
    } else if (value instanceof WaifValue) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    } else {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    return BuiltinResult.value(objectList(parents));
  }

  private static BuiltinResult ancestors(List<MooValue> arguments, WorldTxn world) {
    MooValue value = arguments.getFirst();
    boolean full = arguments.size() > 1 && arguments.get(1).isTruthy();
    List<MooValue> result = new ArrayList<>();
    if (value instanceof ObjectValue object) {
      if (world.object(object.value()).isEmpty()) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      List<Long> ancestry = world.ancestry(object.value());
      for (int index = full ? 0 : 1; index < ancestry.size(); index++) {
        result.add(new ObjectValue(ancestry.get(index)));
      }
      return BuiltinResult.value(new ListValue(result));
    }
    if (value instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
      if (target == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      if (full) {
        result.add(anonymous);
      }
      Set<Long> visited = new LinkedHashSet<>();
      for (long parent : target.parents()) {
        for (long ancestor : world.ancestry(parent)) {
          if (visited.add(ancestor)) {
            result.add(new ObjectValue(ancestor));
          }
        }
      }
      return BuiltinResult.value(new ListValue(result));
    }
    return BuiltinResult.error(ErrorValue.E_TYPE);
  }

  private static BuiltinResult children(List<MooValue> arguments, WorldTxn world) {
    MooValue value = arguments.getFirst();
    if (value instanceof AnonymousObjectValue anonymous) {
      return world.anonymousObject(anonymous).isEmpty()
          ? BuiltinResult.error(ErrorValue.E_INVARG)
          : BuiltinResult.value(new ListValue(List.of()));
    }
    if (value instanceof WaifValue) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    if (!(value instanceof ObjectValue object)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    return target == null
        ? BuiltinResult.error(ErrorValue.E_INVARG)
        : BuiltinResult.value(objectList(target.children()));
  }

  private static BuiltinResult changeParents(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    MooValue target = arguments.getFirst();
    if (!(target instanceof ObjectValue) && !(target instanceof AnonymousObjectValue)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    ParentArgument parents = parentArgument(arguments.get(1), world);
    if (parents.error().isPresent()) {
      return BuiltinResult.error(parents.error().orElseThrow());
    }
    long owner;
    boolean recursive = false;
    if (target instanceof ObjectValue object) {
      WorldObject body = world.object(object.value()).orElse(null);
      if (body == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      owner = body.owner();
      for (long parent : parents.ids()) {
        if (world.ancestry(parent).contains(object.value())) {
          recursive = true;
          break;
        }
      }
    } else {
      WorldAnonymousObject body = world.anonymousObject((AnonymousObjectValue) target).orElse(null);
      if (body == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      owner = body.owner();
    }
    if ((!isWizard(world, programmer) && owner != programmer)
        || !parentsAllowed(parents.ids(), world, programmer, ObjectFlags.FLAG_FERTILE)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    if (recursive) {
      return BuiltinResult.error(ErrorValue.E_RECMOVE);
    }
    WorldResult<Boolean> changed =
        target instanceof ObjectValue object
            ? world.changeParents(object.value(), parents.ids())
            : world.changeParents((AnonymousObjectValue) target, parents.ids());
    return mutationResult(changed, new IntegerValue(0));
  }

  private static boolean isWizard(WorldTxn world, long programmer) {
    WorldObject object = world.object(programmer).orElse(null);
    return object != null && ObjectFlags.isWizard(object.flags());
  }

  private static BuiltinResult mutationResult(WorldResult<?> result, MooValue success) {
    if (result instanceof WorldResult.Failed<?> failed) {
      return BuiltinResult.error(failed.reason().value());
    }
    return BuiltinResult.value(success);
  }

  private static boolean parentsAllowed(
      List<Long> parents, WorldTxn world, long programmer, int permissionFlag) {
    if (isWizard(world, programmer)) {
      return true;
    }
    for (long parentId : parents) {
      WorldObject parent = world.object(parentId).orElseThrow();
      if (parent.owner() != programmer && (parent.flags() & permissionFlag) == 0) {
        return false;
      }
    }
    return true;
  }

  private static ParentArgument parentArgument(MooValue value, WorldTxn world) {
    List<Long> parents = new ArrayList<>();
    if (value instanceof ObjectValue parent) {
      if (parent.value() == -1) {
        return new ParentArgument(List.of(), Optional.empty());
      }
      parents.add(parent.value());
    } else if (value instanceof ListValue list) {
      for (MooValue element : list.elements()) {
        if (!(element instanceof ObjectValue parent)) {
          return new ParentArgument(List.of(), Optional.of(ErrorValue.E_TYPE));
        }
        parents.add(parent.value());
      }
    } else {
      return new ParentArgument(List.of(), Optional.of(ErrorValue.E_TYPE));
    }
    for (long parent : parents) {
      if (parent < 0 || world.object(parent).isEmpty()) {
        return new ParentArgument(List.of(), Optional.of(ErrorValue.E_INVARG));
      }
    }
    return new ParentArgument(List.copyOf(parents), Optional.empty());
  }

  private static ListValue objectList(List<Long> objectIds) {
    return new ListValue(
        objectIds.stream().map(ObjectValue::new).map(MooValue.class::cast).toList());
  }

  private static BuiltinResult locateByName(List<MooValue> arguments, WorldTxn world) {
    byte[] query = ((StringValue) arguments.getFirst()).bytes();
    boolean caseMatters = arguments.size() == 2 && arguments.get(1).isTruthy();
    List<MooValue> matches = new ArrayList<>();
    long maximum = world.maximumObjectId();
    for (long objectId = 0; objectId <= maximum; objectId++) {
      WorldObject object = world.object(objectId).orElse(null);
      if (object == null) {
        continue;
      }
      byte[] name = StringValue.of(object.name()).bytes();
      for (int position = 0; position <= name.length - query.length; position++) {
        if (matchesAt(name, position, query, caseMatters)) {
          matches.add(new ObjectValue(objectId));
          break;
        }
      }
    }
    return BuiltinResult.value(new ListValue(matches));
  }

  private static BuiltinResult locations(List<MooValue> arguments, WorldTxn world) {
    long objectId = ((ObjectValue) arguments.getFirst()).value();
    WorldObject object = world.object(objectId).orElse(null);
    if (object == null) {
      return BuiltinResult.error(ErrorValue.E_INVIND);
    }
    long base = arguments.size() > 1 ? ((ObjectValue) arguments.get(1)).value() : 0;
    boolean checkParent = arguments.size() > 2 && arguments.get(2).isTruthy();
    List<MooValue> result = new ArrayList<>();
    long location = object.location();
    while (world.object(location).isPresent()) {
      if (base != 0
          && (checkParent ? world.ancestry(location).contains(base) : location == base)) {
        break;
      }
      MooValue located = new ObjectValue(location);
      if (!result.contains(located)) {
        result.add(located);
      }
      location = world.object(location).orElseThrow().location();
    }
    return BuiltinResult.value(new ListValue(result));
  }

  private static BuiltinResult nextRecycledObject(List<MooValue> arguments, WorldTxn world) {
    long start = arguments.isEmpty() ? 0 : ((ObjectValue) arguments.getFirst()).value();
    long maximum = world.maximumObjectId();
    if (start < 0 || start > maximum) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    for (long objectId = start; objectId < maximum; objectId++) {
      if (world.object(objectId).isEmpty()) {
        return BuiltinResult.value(new ObjectValue(objectId));
      }
    }
    return BuiltinResult.value(new IntegerValue(0));
  }

  private static BuiltinResult recycledObjects(WorldTxn world) {
    List<MooValue> recycled = new ArrayList<>();
    long maximum = world.maximumObjectId();
    for (long objectId = 0; objectId <= maximum; objectId++) {
      if (world.object(objectId).isEmpty()) {
        recycled.add(new ObjectValue(objectId));
      }
    }
    return BuiltinResult.value(new ListValue(recycled));
  }

  private static BuiltinResult waifStats(WorldTxn world) {
    Map<MooValue, MooValue> result = new LinkedHashMap<>();
    result.put(StringValue.of("total"), new IntegerValue(world.snapshot().waifs().size()));
    long pending =
        world.pendingFinalization().stream().filter(WaifValue.class::isInstance).count();
    result.put(StringValue.of("pending_recycle"), new IntegerValue(pending));
    Map<Long, Long> classes = new LinkedHashMap<>();
    for (WaifValue waif : world.snapshot().waifs().keySet()) {
      classes.merge(waif.classObject().value(), 1L, Long::sum);
    }
    for (Map.Entry<Long, Long> entry : classes.entrySet()) {
      result.put(new ObjectValue(entry.getKey()), new IntegerValue(entry.getValue()));
    }
    return BuiltinResult.value(new MapValue(result));
  }

  private static BuiltinResult ownedObjects(List<MooValue> arguments, WorldTxn world) {
    long owner = ((ObjectValue) arguments.getFirst()).value();
    if (world.object(owner).isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVIND);
    }
    List<MooValue> owned = new ArrayList<>();
    long maximum = world.maximumObjectId();
    for (long objectId = 0; objectId <= maximum; objectId++) {
      WorldObject object = world.object(objectId).orElse(null);
      if (object != null && object.owner() == owner) {
        owned.add(new ObjectValue(objectId));
      }
    }
    return BuiltinResult.value(new ListValue(owned));
  }

  private record ParentArgument(List<Long> ids, Optional<ErrorValue> error) {}

  private static BuiltinResult isPlayer(List<MooValue> arguments, WorldTxn world) {
    ObjectValue object = (ObjectValue) arguments.getFirst();
    if (world.object(object.value()).isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    return BuiltinResult.value(new IntegerValue(world.players().contains(object.value()) ? 1 : 0));
  }

  private static BuiltinResult valid(List<MooValue> arguments, WorldTxn world) {
    MooValue value = arguments.getFirst();
    if (value instanceof ObjectValue object) {
      return BuiltinResult.value(new IntegerValue(world.object(object.value()).isPresent() ? 1 : 0));
    }
    if (value instanceof AnonymousObjectValue anonymous) {
      return BuiltinResult.value(new IntegerValue(world.anonymousObject(anonymous).isPresent() ? 1 : 0));
    }
    if (value instanceof WaifValue) {
      return BuiltinResult.value(new IntegerValue(0));
    }
    return BuiltinResult.error(ErrorValue.E_TYPE);
  }

  private static Optional<ErrorValue> resolveOwnershipPreamble(
      MooValue receiver, long newOwner, WorldTxn world, long programmer) {
    long targetOwner;
    int targetFlags;
    if (receiver instanceof ObjectValue object) {
      WorldObject target = world.object(object.value()).orElse(null);
      if (target == null) {
        return Optional.of(ErrorValue.E_INVARG);
      }
      targetOwner = target.owner();
      targetFlags = target.flags();
    } else if (receiver instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
      if (target == null) {
        return Optional.of(ErrorValue.E_INVARG);
      }
      targetOwner = target.owner();
      targetFlags = target.flags();
    } else {
      return Optional.of(ErrorValue.E_TYPE);
    }
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && ObjectFlags.isWizard(actor.flags());
    boolean writable = targetOwner == programmer || wizard || ObjectFlags.isWritable(targetFlags);
    return writable && (newOwner == programmer || wizard)
        ? Optional.empty()
        : Optional.of(ErrorValue.E_PERM);
  }

  private static BuiltinResult addVerb(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    ListValue info = (ListValue) arguments.get(1);
    if (info.size() != 3
        || !(info.elements().get(0) instanceof ObjectValue owner)
        || !(info.elements().get(1) instanceof StringValue permissionValue)
        || !(info.elements().get(2) instanceof StringValue namesValue)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (world.object(owner.value()).isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    int permissions = 0;
    String permissionText = permissionValue.text();
    for (int index = 0; index < permissionText.length(); index++) {
      permissions |=
          switch (Character.toLowerCase(permissionText.charAt(index))) {
            case 'r' -> 1;
            case 'w' -> 2;
            case 'x' -> 4;
            case 'd' -> 8;
            default -> -1;
          };
      if (permissions < 0) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
    }
    String names = namesValue.text().stripLeading();
    if (names.isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    ListValue verbArguments = (ListValue) arguments.get(2);
    if (verbArguments.size() != 3
        || verbArguments.elements().stream().anyMatch(value -> !(value instanceof StringValue))) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    String directText = ((StringValue) verbArguments.elements().get(0)).text();
    String prepositionText = ((StringValue) verbArguments.elements().get(1)).text();
    String indirectText = ((StringValue) verbArguments.elements().get(2)).text();
    int direct =
        switch (directText.toLowerCase(Locale.ROOT)) {
          case "none" -> 0;
          case "any" -> 1;
          case "this" -> 2;
          default -> -1;
        };
    int indirect =
        switch (indirectText.toLowerCase(Locale.ROOT)) {
          case "none" -> 0;
          case "any" -> 1;
          case "this" -> 2;
          default -> -1;
        };
    int preposition =
        switch (prepositionText.toLowerCase(Locale.ROOT)) {
          case "none" -> -1;
          case "any" -> -2;
          case "at" -> 1;
          case "in" -> 3;
          default -> Integer.MIN_VALUE;
        };
    if (direct < 0 || indirect < 0 || preposition == Integer.MIN_VALUE) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    MooValue receiver = arguments.get(0);
    Optional<ErrorValue> ownershipError =
        resolveOwnershipPreamble(receiver, owner.value(), world, programmer);
    if (ownershipError.isPresent()) {
      return BuiltinResult.error(ownershipError.orElseThrow());
    }

    int encodedPermissions = permissions | (direct << 4) | (indirect << 6);
    int slot =
        receiver instanceof ObjectValue object
            ? world.addVerb(
                object.value(), names, owner.value(), encodedPermissions, preposition)
            : world.addVerb(
                (AnonymousObjectValue) receiver,
                names,
                owner.value(),
                encodedPermissions,
                preposition);
    return BuiltinResult.value(
        new IntegerValue(slot));
  }

  private static BuiltinResult addProperty(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    ListValue info = (ListValue) arguments.get(3);
    if (info.size() != 2
        || !(info.elements().get(0) instanceof ObjectValue owner)
        || !(info.elements().get(1) instanceof StringValue permissionValue)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (world.object(owner.value()).isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    int permissions = 0;
    String permissionText = permissionValue.text();
    for (int index = 0; index < permissionText.length(); index++) {
      permissions |=
          switch (Character.toLowerCase(permissionText.charAt(index))) {
            case 'r' -> 1;
            case 'w' -> 2;
            case 'c' -> 4;
            default -> -1;
          };
      if (permissions < 0) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
    }
    MooValue receiver = arguments.get(0);
    Optional<ErrorValue> ownershipError =
        resolveOwnershipPreamble(receiver, owner.value(), world, programmer);
    if (ownershipError.isPresent()) {
      return BuiltinResult.error(ownershipError.orElseThrow());
    }
    String name = ((StringValue) arguments.get(1)).text();
    WorldResult<Boolean> added =
        receiver instanceof ObjectValue object
            ? world.addProperty(
                object.value(), name, arguments.get(2), owner.value(), permissions)
            : world.addProperty(
                (AnonymousObjectValue) receiver,
                name,
                arguments.get(2),
                owner.value(),
                permissions);
    return mutationResult(added, new IntegerValue(0));
  }

  private static BuiltinResult properties(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.getFirst() instanceof WaifValue) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    if (!(arguments.getFirst() instanceof ObjectValue object)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && ObjectFlags.isWizard(actor.flags());
    if (target.owner() != programmer && !wizard && !ObjectFlags.isReadable(target.flags())) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    List<MooValue> names =
        target.properties().stream()
            .filter(WorldProperty::defined)
            .map(WorldProperty::name)
            .map(StringValue::of)
            .map(MooValue.class::cast)
            .toList();
    return BuiltinResult.value(new ListValue(names));
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
    WorldObject viewer = world.object(programmer).orElse(null);
    boolean wizard = viewer != null && ObjectFlags.isWizard(viewer.flags());
    return wizard || body.owner() == programmer ? value : new AnonymousObjectValue();
  }

  private static BuiltinResult isClearProperty(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (!(arguments.getFirst() instanceof ObjectValue object)) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    String name = ((StringValue) arguments.get(1)).text();
    String normalized = name.toLowerCase(Locale.ROOT);
    if (normalized.equals("name")
        || normalized.equals("location")
        || normalized.equals("contents")
        || normalized.equals("owner")
        || normalized.equals("programmer")
        || normalized.equals("wizard")
        || normalized.equals("r")
        || normalized.equals("w")
        || normalized.equals("f")) {
      return BuiltinResult.value(new IntegerValue(0));
    }
    WorldProperty property = world.property(object.value(), name).orElse(null);
    if (property == null) {
      return BuiltinResult.error(ErrorValue.E_PROPNF);
    }
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && ObjectFlags.isWizard(actor.flags());
    if (property.owner() != programmer && !wizard && (property.permissions() & 1) == 0) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    WorldProperty local =
        target.properties().stream()
            .filter(candidate -> candidate.name().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    return BuiltinResult.value(new IntegerValue(local != null && local.clear() ? 1 : 0));
  }

  private static BuiltinResult propertyInfo(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (arguments.getFirst() instanceof WaifValue) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    if (!(arguments.getFirst() instanceof ObjectValue object)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (world.object(object.value()).isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    String name = ((StringValue) arguments.get(1)).text();
    String normalized = name.toLowerCase(Locale.ROOT);
    if (normalized.equals("name")
        || normalized.equals("location")
        || normalized.equals("contents")
        || normalized.equals("owner")
        || normalized.equals("programmer")
        || normalized.equals("wizard")
        || normalized.equals("r")
        || normalized.equals("w")
        || normalized.equals("f")) {
      return BuiltinResult.error(ErrorValue.E_PROPNF);
    }
    WorldProperty property = world.property(object.value(), name).orElse(null);
    if (property == null) {
      return BuiltinResult.error(ErrorValue.E_PROPNF);
    }
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && ObjectFlags.isWizard(actor.flags());
    if (property.owner() != programmer && !wizard && (property.permissions() & 1) == 0) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    String permissions = "";
    if ((property.permissions() & 1) != 0) {
      permissions += "r";
    }
    if ((property.permissions() & 2) != 0) {
      permissions += "w";
    }
    if ((property.permissions() & 4) != 0) {
      permissions += "c";
    }
    return BuiltinResult.value(
        new ListValue(List.of(new ObjectValue(property.owner()), StringValue.of(permissions))));
  }

  private static BuiltinResult clearProperty(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (!(arguments.getFirst() instanceof ObjectValue object)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    String name = ((StringValue) arguments.get(1)).text();
    String normalized = name.toLowerCase(Locale.ROOT);
    if (normalized.equals("name")
        || normalized.equals("location")
        || normalized.equals("contents")
        || normalized.equals("owner")
        || normalized.equals("programmer")
        || normalized.equals("wizard")
        || normalized.equals("r")
        || normalized.equals("w")
        || normalized.equals("f")) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    WorldProperty property = world.property(object.value(), name).orElse(null);
    if (property == null) {
      return BuiltinResult.error(ErrorValue.E_PROPNF);
    }
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && ObjectFlags.isWizard(actor.flags());
    if (property.owner() != programmer && !wizard && (property.permissions() & 2) == 0) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    WorldProperty local =
        target.properties().stream()
            .filter(candidate -> candidate.name().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    if (local == null || local.defined()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    return mutationResult(world.clearProperty(object.value(), name), new IntegerValue(0));
  }

  private static BuiltinResult deleteProperty(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (!(arguments.getFirst() instanceof ObjectValue object)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && ObjectFlags.isWizard(actor.flags());
    if (target.owner() != programmer && !wizard && !ObjectFlags.isWritable(target.flags())) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    String name = ((StringValue) arguments.get(1)).text();
    boolean defined =
        target.properties().stream()
            .anyMatch(property -> property.defined() && property.name().equalsIgnoreCase(name));
    if (!defined) {
      return BuiltinResult.error(ErrorValue.E_PROPNF);
    }
    return mutationResult(world.deleteProperty(object.value(), name), new IntegerValue(0));
  }

  private static BuiltinResult setPlayerFlag(List<MooValue> arguments, WorldTxn world) {
    ObjectValue object = (ObjectValue) arguments.get(0);
    return mutationResult(
        world.setPlayerFlag(object.value(), arguments.get(1).isTruthy()), new IntegerValue(0));
  }

  private static BuiltinResult setVerbInfo(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    MooValue descriptor = arguments.get(1);
    if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    MooValue receiver = arguments.get(0);
    if (!(receiver instanceof ObjectValue object)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    List<WorldVerb> targetVerbs = target.verbs();

    ListValue info = (ListValue) arguments.get(2);
    if (info.size() != 3
        || !(info.elements().get(0) instanceof ObjectValue owner)
        || !(info.elements().get(1) instanceof StringValue permissionValue)
        || !(info.elements().get(2) instanceof StringValue namesValue)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (world.object(owner.value()).isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    int permissions = 0;
    String permissionText = permissionValue.text();
    for (int index = 0; index < permissionText.length(); index++) {
      permissions |=
          switch (Character.toLowerCase(permissionText.charAt(index))) {
            case 'r' -> 1;
            case 'w' -> 2;
            case 'x' -> 4;
            case 'd' -> 8;
            default -> -1;
          };
      if (permissions < 0) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
    }
    String names = namesValue.text().stripLeading();
    if (names.isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    WorldVerb verb;
    int verbIndex;
    if (descriptor instanceof IntegerValue integer) {
      long index = integer.value() - 1;
      if (index >= targetVerbs.size()) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verbIndex = (int) index;
      verb = targetVerbs.get(verbIndex);
    } else {
      WorldVerb candidate =
          world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null);
      if (candidate == null || !targetVerbs.contains(candidate)) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verb = candidate;
      verbIndex = targetVerbs.indexOf(verb);
    }

    boolean wizard = BuiltinPermissionRule.WIZARD_ONLY.allows(world, programmer);
    if ((verb.owner() != programmer && !wizard && (verb.permissions() & 2) == 0)
        || (!wizard && verb.owner() != owner.value())) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    WorldResult<Boolean> updated =
        world.setVerbInfo(object.value(), verbIndex, names, owner.value(), permissions);
    return mutationResult(updated, new IntegerValue(0));
  }

  private static BuiltinResult verbs(List<MooValue> arguments, WorldTxn world, long programmer) {
    MooValue receiver = arguments.getFirst();
    long owner;
    int flags;
    List<WorldVerb> verbs;
    if (receiver instanceof ObjectValue object) {
      WorldObject target = world.object(object.value()).orElse(null);
      if (target == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      owner = target.owner();
      flags = target.flags();
      verbs = target.verbs();
    } else if (receiver instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
      if (target == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      owner = target.owner();
      flags = target.flags();
      verbs = target.verbs();
    } else {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (owner != programmer && !isWizard(world, programmer) && !ObjectFlags.isReadable(flags)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    return BuiltinResult.value(
        new ListValue(verbs.stream().map(WorldVerb::names).map(StringValue::of).toList()));
  }

  private static BuiltinResult verbInfo(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    MooValue receiver = arguments.get(0);
    if (!(receiver instanceof ObjectValue object)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    MooValue descriptor = arguments.get(1);
    if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    WorldVerb verb;
    if (descriptor instanceof IntegerValue integer) {
      long index = integer.value() - 1;
      if (index >= target.verbs().size()) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verb = target.verbs().get((int) index);
    } else {
      WorldVerb candidate =
          world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null);
      if (candidate == null || !target.verbs().contains(candidate)) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verb = candidate;
    }

    boolean wizard = BuiltinPermissionRule.WIZARD_ONLY.allows(world, programmer);
    if (verb.owner() != programmer && !wizard && (verb.permissions() & 1) == 0) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    StringBuilder flags = new StringBuilder(4);
    if ((verb.permissions() & 1) != 0) {
      flags.append('r');
    }
    if ((verb.permissions() & 2) != 0) {
      flags.append('w');
    }
    if ((verb.permissions() & 4) != 0) {
      flags.append('x');
    }
    if ((verb.permissions() & 8) != 0) {
      flags.append('d');
    }
    return BuiltinResult.value(
        new ListValue(
            List.of(new ObjectValue(verb.owner()), StringValue.of(flags.toString()), StringValue.of(verb.names()))));
  }

  private static BuiltinResult verbArgs(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    MooValue receiver = arguments.get(0);
    if (!(receiver instanceof ObjectValue object)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    MooValue descriptor = arguments.get(1);
    if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    WorldVerb verb;
    if (descriptor instanceof IntegerValue integer) {
      long index = integer.value() - 1;
      if (index >= target.verbs().size()) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verb = target.verbs().get((int) index);
    } else {
      WorldVerb candidate =
          world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null);
      if (candidate == null || !target.verbs().contains(candidate)) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verb = candidate;
    }

    boolean wizard = BuiltinPermissionRule.WIZARD_ONLY.allows(world, programmer);
    if (verb.owner() != programmer && !wizard && (verb.permissions() & 1) == 0) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    String direct =
        switch ((verb.permissions() >> 4) & 3) {
          case 0 -> "none";
          case 1 -> "any";
          case 2 -> "this";
          default -> throw new IllegalStateException("invalid direct-object verb specification");
        };
    String indirect =
        switch ((verb.permissions() >> 6) & 3) {
          case 0 -> "none";
          case 1 -> "any";
          case 2 -> "this";
          default -> throw new IllegalStateException("invalid indirect-object verb specification");
        };
    String preposition =
        switch (verb.preposition()) {
          case -1 -> "none";
          case -2 -> "any";
          case 0 -> "with/using";
          case 1 -> "at/to";
          case 2 -> "in front of";
          case 3 -> "in/inside/into";
          case 4 -> "on top of/on/onto/upon";
          case 5 -> "out of/from inside/from";
          case 6 -> "over";
          case 7 -> "through";
          case 8 -> "under/underneath/beneath";
          case 9 -> "behind";
          case 10 -> "beside";
          case 11 -> "for/about";
          case 12 -> "is";
          case 13 -> "as";
          case 14 -> "off/off of";
          default -> throw new IllegalStateException("invalid verb preposition specification");
        };
    return BuiltinResult.value(
        new ListValue(List.of(StringValue.of(direct), StringValue.of(preposition), StringValue.of(indirect))));
  }

  private static BuiltinResult deleteVerb(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    MooValue descriptor = arguments.get(1);
    if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    MooValue receiver = arguments.get(0);
    if (!(receiver instanceof ObjectValue object)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    boolean wizard = BuiltinPermissionRule.WIZARD_ONLY.allows(world, programmer);
    if (target.owner() != programmer && !wizard && !ObjectFlags.isWritable(target.flags())) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }

    int verbIndex;
    if (descriptor instanceof IntegerValue integer) {
      long index = integer.value() - 1;
      if (index >= target.verbs().size()) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verbIndex = (int) index;
    } else {
      WorldVerb candidate =
          world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null);
      if (candidate == null || !target.verbs().contains(candidate)) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verbIndex = target.verbs().indexOf(candidate);
    }
    return mutationResult(world.deleteVerb(object.value(), verbIndex), new IntegerValue(0));
  }

  private static BuiltinResult setVerbArgs(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    MooValue descriptor = arguments.get(1);
    if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    MooValue receiver = arguments.get(0);
    if (!(receiver instanceof ObjectValue object)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    ListValue verbArguments = (ListValue) arguments.get(2);
    if (verbArguments.size() != 3
        || verbArguments.elements().stream().anyMatch(value -> !(value instanceof StringValue))) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    String directText = ((StringValue) verbArguments.elements().get(0)).text();
    String prepositionText = ((StringValue) verbArguments.elements().get(1)).text();
    String indirectText = ((StringValue) verbArguments.elements().get(2)).text();
    int direct =
        switch (directText.toLowerCase(Locale.ROOT)) {
          case "none" -> 0;
          case "any" -> 1;
          case "this" -> 2;
          default -> -1;
        };
    int indirect =
        switch (indirectText.toLowerCase(Locale.ROOT)) {
          case "none" -> 0;
          case "any" -> 1;
          case "this" -> 2;
          default -> -1;
        };
    String normalizedPreposition = prepositionText.toLowerCase(Locale.ROOT);
    int slash = normalizedPreposition.indexOf('/');
    if (slash >= 0) {
      normalizedPreposition = normalizedPreposition.substring(0, slash);
    }
    int preposition =
        switch (normalizedPreposition) {
          case "none" -> -1;
          case "any" -> -2;
          case "with", "using" -> 0;
          case "at", "to" -> 1;
          case "in front of" -> 2;
          case "in", "inside", "into" -> 3;
          case "on top of", "on", "onto", "upon" -> 4;
          case "out of", "from inside", "from" -> 5;
          case "over" -> 6;
          case "through" -> 7;
          case "under", "underneath", "beneath" -> 8;
          case "behind" -> 9;
          case "beside" -> 10;
          case "for", "about" -> 11;
          case "is" -> 12;
          case "as" -> 13;
          case "off", "off of" -> 14;
          default -> {
            String numeric =
                normalizedPreposition.startsWith("#")
                    ? normalizedPreposition.substring(1)
                    : normalizedPreposition;
            if (numeric.isEmpty()
                || numeric.chars().anyMatch(character -> character < '0' || character > '9')) {
              yield Integer.MIN_VALUE;
            }
            try {
              int code = Integer.parseInt(numeric);
              yield code >= 0 && code <= 14 ? code : Integer.MIN_VALUE;
            } catch (NumberFormatException ignored) {
              yield Integer.MIN_VALUE;
            }
          }
        };
    if (direct < 0 || indirect < 0 || preposition == Integer.MIN_VALUE) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    List<WorldVerb> targetVerbs = target.verbs();
    WorldVerb verb;
    int verbIndex;
    if (descriptor instanceof IntegerValue integer) {
      long index = integer.value() - 1;
      if (index >= targetVerbs.size()) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verbIndex = (int) index;
      verb = targetVerbs.get(verbIndex);
    } else {
      WorldVerb candidate =
          world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null);
      if (candidate == null || !targetVerbs.contains(candidate)) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verb = candidate;
      verbIndex = targetVerbs.indexOf(verb);
    }

    boolean wizard = BuiltinPermissionRule.WIZARD_ONLY.allows(world, programmer);
    if (verb.owner() != programmer && !wizard && (verb.permissions() & 2) == 0) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    WorldResult<Boolean> updated =
        world.setVerbArgs(object.value(), verbIndex, direct, preposition, indirect);
    return mutationResult(updated, new IntegerValue(0));
  }

  private static BuiltinResult setVerbCode(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    ListValue code = (ListValue) arguments.get(2);
    if (code.elements().stream().anyMatch(value -> !(value instanceof StringValue))) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    MooValue receiver = arguments.get(0);
    List<WorldVerb> targetVerbs;
    if (receiver instanceof ObjectValue object) {
      WorldObject target = world.object(object.value()).orElse(null);
      if (target == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      targetVerbs = target.verbs();
    } else if (receiver instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
      if (target == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      targetVerbs = target.verbs();
    } else {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    MooValue descriptor = arguments.get(1);
    if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    WorldVerb verb;
    int verbIndex;
    if (descriptor instanceof IntegerValue integer) {
      long index = integer.value() - 1;
      if (index >= targetVerbs.size()) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verbIndex = (int) index;
      verb = targetVerbs.get(verbIndex);
    } else {
      WorldVerb candidate =
          receiver instanceof ObjectValue object
              ? world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null)
              : world
                  .verb(
                      (AnonymousObjectValue) receiver,
                      ((StringValue) descriptor).text(),
                      false)
                  .orElse(null);
      if (candidate == null || !targetVerbs.contains(candidate)) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verb = candidate;
      verbIndex = targetVerbs.indexOf(verb);
    }

    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && ObjectFlags.isWizard(actor.flags());
    boolean programmerFlag = actor != null && ObjectFlags.isProgrammer(actor.flags());
    if ((!programmerFlag && !wizard)
        || (verb.owner() != programmer && !wizard && (verb.permissions() & 2) == 0)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }

    String suppliedSource =
        code.elements().stream()
            .map(StringValue.class::cast)
            .map(StringValue::text)
            .collect(java.util.stream.Collectors.joining("\n"));
    final Ast.Program program;
    try {
      program = MooParser.parse(suppliedSource);
    } catch (IllegalArgumentException error) {
      return compilationDiagnostic(error);
    }
    try {
      new MooCompiler().compile(program);
    } catch (IllegalArgumentException error) {
      return compilationDiagnostic(error);
    }
    String canonicalSource =
        MooUnparser.unparse(program).lines()
            .map(String::stripLeading)
            .collect(java.util.stream.Collectors.joining("\n"));
    WorldResult<Boolean> updated =
        receiver instanceof ObjectValue object
            ? world.setVerbCode(object.value(), verbIndex, canonicalSource)
            : world.setVerbCode((AnonymousObjectValue) receiver, verbIndex, canonicalSource);
    return mutationResult(updated, new ListValue(List.of()));
  }

  private static BuiltinResult compilationDiagnostic(IllegalArgumentException error) {
    String diagnostic = error.getMessage();
    if (diagnostic == null) {
      diagnostic = error.getClass().getSimpleName();
    }
    return BuiltinResult.value(new ListValue(List.of(StringValue.of(diagnostic))));
  }

  private static BuiltinResult verbCode(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (!(arguments.get(0) instanceof ObjectValue object)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    MooValue descriptor = arguments.get(1);
    if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    WorldVerb verb;
    if (descriptor instanceof IntegerValue integer) {
      long index = integer.value() - 1;
      if (index >= target.verbs().size()) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verb = target.verbs().get((int) index);
    } else {
      WorldVerb candidate =
          world.verb(object.value(), ((StringValue) descriptor).text(), false).orElse(null);
      if (candidate == null || !target.verbs().contains(candidate)) {
        return BuiltinResult.error(ErrorValue.E_VERBNF);
      }
      verb = candidate;
    }

    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && ObjectFlags.isWizard(actor.flags());
    if (verb.owner() != programmer && !wizard && (verb.permissions() & 1) == 0) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }

    boolean indent = arguments.size() < 4 || arguments.get(3).isTruthy();
    List<MooValue> lines =
        MooUnparser.unparse(MooParser.parse(verb.programSource())).lines()
            .map(line -> indent ? line : line.stripLeading())
            .map(StringValue::of)
            .map(MooValue.class::cast)
            .toList();
    return BuiltinResult.value(new ListValue(lines));
  }

  private static BuiltinResult move(List<MooValue> arguments, WorldTxn world, long programmer) {
    ObjectValue object = (ObjectValue) arguments.get(0);
    ObjectValue destination = (ObjectValue) arguments.get(1);
    long position =
        arguments.size() == 3 ? ((IntegerValue) arguments.get(2)).value() : 0;
    if (position < 0) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    WorldObject moving = world.object(object.value()).orElse(null);
    if (moving == null
        || (destination.value() != -1 && world.object(destination.value()).isEmpty())) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    if (recursiveMove(world, object.value(), destination.value())) {
      return BuiltinResult.error(ErrorValue.E_RECMOVE);
    }
    WorldObject programmerObject = world.object(programmer).orElse(null);
    if (programmerObject != null && ObjectFlags.isWizard(programmerObject.flags())) {
      if (destination.value() == moving.location()) {
        return position == 0
            ? BuiltinResult.value(new IntegerValue(0))
            : mutationResult(
                world.move(object.value(), destination.value(), position), new IntegerValue(0));
      }
      return new BuiltinResult.Move(object.value(), destination.value(), position);
    }
    return mutationResult(
        world.move(object.value(), destination.value(), position), new IntegerValue(0));
  }

  private static boolean recursiveMove(WorldTxn world, long object, long destination) {
    Set<Long> visited = new LinkedHashSet<>();
    long location = destination;
    while (location != -1 && visited.add(location)) {
      if (location == object) {
        return true;
      }
      WorldObject container = world.object(location).orElse(null);
      if (container == null) {
        return false;
      }
      location = container.location();
    }
    return false;
  }

  private static BuiltinResult recycle(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && ObjectFlags.isWizard(actor.flags());
    MooValue receiver = arguments.getFirst();
    if (receiver instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
      if (target == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      if (target.owner() != programmer && !wizard) {
        return BuiltinResult.error(ErrorValue.E_PERM);
      }
      return new BuiltinResult.RecycleAnonymous(anonymous);
    }
    if (receiver instanceof ObjectValue object) {
      WorldObject target = world.object(object.value()).orElse(null);
      if (target == null) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
      if (target.owner() != programmer && !wizard) {
        return BuiltinResult.error(ErrorValue.E_PERM);
      }
      return new BuiltinResult.Recycle(object.value());
    }
    return BuiltinResult.error(ErrorValue.E_TYPE);
  }

  private BuiltinResult switchPlayer(List<MooValue> arguments, WorldTxn world) {
    long oldPlayer = ((ObjectValue) arguments.getFirst()).value();
    long newPlayer = ((ObjectValue) arguments.get(1)).value();
    if (oldPlayer == newPlayer
        || connections().connectionId(oldPlayer).isEmpty()
        || !world.players().contains(newPlayer)) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    return new BuiltinResult.SwitchPlayer(newPlayer);
  }

  private static BuiltinResult setTaskPerms(
      List<MooValue> arguments, WorldTxn world, long currentProgrammer) {
    ObjectValue requested = (ObjectValue) arguments.getFirst();
    if (requested.value() != currentProgrammer && !isWizard(world, currentProgrammer)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    return new BuiltinResult.Programmer(requested.value());
  }

  private BuiltinResult notifyLine(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    long target = ((ObjectValue) arguments.getFirst()).value();
    if (target != programmer && !isWizard(world, programmer)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    StringValue line = (StringValue) arguments.get(1);
    boolean noFlush = arguments.size() > 2 && arguments.get(2).isTruthy();
    boolean noNewline = arguments.size() > 3 && arguments.get(3).isTruthy();
    if (target == programmer && !noFlush && !noNewline) {
      return new BuiltinResult.Output(line.text());
    }
    OptionalLong connectionId = connections().connectionId(target);
    if (connectionId.isEmpty()) {
      return BuiltinResult.value(new IntegerValue(1));
    }
    return new BuiltinResult.Notify(
        connectionId.orElseThrow(), line.text(), noFlush, noNewline);
  }

  private static BuiltinResult toStringValue(List<MooValue> arguments) {
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
        double converted = Double.parseDouble(string.text().strip());
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
            exactlyEqual(arguments.get(0), arguments.get(1), valueSemantics) ? 1 : 0));
  }

  private static boolean exactlyEqual(
      MooValue left, MooValue right, ValueSemantics valueSemantics) {
    if (left instanceof BooleanValue bool && right instanceof IntegerValue integer) {
      return integer.value() == (bool.value() ? 1 : 0);
    }
    if (left instanceof IntegerValue integer && right instanceof BooleanValue bool) {
      return integer.value() == (bool.value() ? 1 : 0);
    }
    if (valueSemantics.promoteNumbers()
        && ((left instanceof IntegerValue && right instanceof FloatValue)
            || (left instanceof FloatValue && right instanceof IntegerValue))) {
      return numericDouble(left) == numericDouble(right);
    }
    if (left instanceof StringValue leftString && right instanceof StringValue rightString) {
      return Arrays.equals(leftString.bytes(), rightString.bytes());
    }
    if (left instanceof ListValue leftList && right instanceof ListValue rightList) {
      if (leftList.size() != rightList.size()) {
        return false;
      }
      for (int index = 0; index < leftList.size(); index++) {
        if (!exactlyEqual(
            leftList.elements().get(index), rightList.elements().get(index), valueSemantics)) {
          return false;
        }
      }
      return true;
    }
    if (left instanceof MapValue leftMap && right instanceof MapValue rightMap) {
      if (leftMap.size() != rightMap.size()) {
        return false;
      }
      var leftEntries = leftMap.entries().entrySet().iterator();
      var rightEntries = rightMap.entries().entrySet().iterator();
      while (leftEntries.hasNext()) {
        Map.Entry<MooValue, MooValue> leftEntry = leftEntries.next();
        Map.Entry<MooValue, MooValue> rightEntry = rightEntries.next();
        if (!exactlyEqual(leftEntry.getKey(), rightEntry.getKey(), valueSemantics)
            || !exactlyEqual(leftEntry.getValue(), rightEntry.getValue(), valueSemantics)) {
          return false;
        }
      }
      return true;
    }
    return left.equals(right);
  }

  private static BuiltinResult dynamicEval(List<MooValue> arguments) {
    String source =
        arguments.stream()
            .map(StringValue.class::cast)
            .map(StringValue::text)
            .collect(java.util.stream.Collectors.joining("\n"));
    return new BuiltinResult.DynamicEval(source);
  }

  private static BuiltinResult gcStats() {
    Map<MooValue, MooValue> colors = new LinkedHashMap<>();
    for (String color :
        List.of("green", "yellow", "black", "gray", "white", "purple", "pink")) {
      colors.put(StringValue.of(color), new IntegerValue(0));
    }
    return BuiltinResult.value(new MapValue(colors));
  }

  private static BuiltinResult typeOf(List<MooValue> arguments) {
    return BuiltinResult.value(new IntegerValue(arguments.getFirst().type().code()));
  }

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
    long openNetworkConnection(String host, int port, boolean ipv6, long listenerHandler)
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
    default void setConnectionKeepAlive(long connectionId, KeepAliveOptions options) {}

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
  public record ConnectionOptionRequest(long target, ConnectionOption option, MooValue value) {}

  /** One validated line to inject into a live connection's input stream. */
  public record ForcedInputRequest(long target, String line) {}

  /** One authorized notification captured for commit-time transport publication. */
  public record NotificationRequest(
      long connectionId, String line, boolean noFlush, boolean noNewline) {}

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
