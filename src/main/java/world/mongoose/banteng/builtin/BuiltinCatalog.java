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
  private static final int DEFAULT_MAX_QUEUED_OUTPUT = 65_536;

  private final List<BuiltinSpec> manifest;
  private final FileIoBuiltins fileIo;
  private final BuiltinHosts hosts;
  private final Optional<ListenerControl> listenerControl;
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
  public BuiltinCatalog(ListenerControl listenerControl, BuiltinHosts hosts) {
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

  static Supplier<ConnectionRegistryAccess> standaloneConnections() {
    return () -> EmptyConnectionRegistry.INSTANCE;
  }

  private static ConnectionRegistryAccess connections(BuiltinHosts invocationHosts) {
    return Objects.requireNonNull(invocationHosts.connections().get(), "hosts.connections().get()");
  }

  /** Returns the standalone invocation composition retained by this catalog. */
  public BuiltinHosts hosts() {
    return hosts;
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
    entries.add(
        new BuiltinSpec(
            "switch_player",
            List.of(new CallShape(List.of(OBJECT, OBJECT), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            false,
            this::switchPlayer));
    TaskBuiltins.registerGroup2(entries, this);
    entries.add(
        new BuiltinSpec(
            "read",
            List.of(new CallShape(List.of(), List.of(OBJECT, ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
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
              return call.hosts().read().invoke(call);
            }));
    entries.add(
        new BuiltinSpec(
            "connected_players",
            List.of(new CallShape(List.of(), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call ->
                BuiltinResult.value(
                    new ListValue(
                        connections(call.hosts())
                            .connectedPlayers(
                                !call.arguments().isEmpty()
                                    && call.arguments().getFirst().isTruthy())
                            .stream()
                            .map(ObjectValue::new)
                            .map(MooValue.class::cast)
                            .toList()))));
    entries.add(
        new BuiltinSpec(
            "idle_seconds",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> call.hosts().idleSeconds().invoke(call)));
    entries.add(
        new BuiltinSpec(
            "connected_seconds",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> call.hosts().connectedSeconds().invoke(call)));
    entries.add(
        new BuiltinSpec(
            "buffered_output_length",
            List.of(new CallShape(List.of(), List.of(OBJECT), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call ->
                bufferedOutputLength(call.hosts(),
                    call.arguments(),
                    call.world(),
                    call.programmer(),
                    call.stagedBufferedOutputLength())));
    entries.add(
        new BuiltinSpec(
            "boot_player",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> bootPlayer(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "connection_info",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> connectionInfo(call.hosts(), call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "connection_name",
            List.of(new CallShape(List.of(OBJECT), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> connectionName(call.hosts(), call.arguments(), call.world(), call.programmer())));
    RegexBuiltins.registerLegacy(entries);
    entries.add(
        new BuiltinSpec(
            "connection_name_lookup",
            List.of(new CallShape(List.of(OBJECT), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            this::connectionNameLookup));
    entries.add(
        new BuiltinSpec(
            "connection_options",
            List.of(new CallShape(List.of(OBJECT), List.of(STRING), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> call.hosts().connectionOptions().invoke(call)));
    entries.add(
        new BuiltinSpec(
            "output_delimiters",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> call.hosts().outputDelimiters().invoke(call)));
    entries.add(
        new BuiltinSpec(
            "set_connection_option",
            List.of(new CallShape(List.of(OBJECT, STRING, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> setConnectionOption(call.hosts(), call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "flush_input",
            List.of(new CallShape(List.of(OBJECT), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> call.hosts().flushInput().invoke(call)));
    entries.add(
        new BuiltinSpec(
            "force_input",
            List.of(new CallShape(List.of(OBJECT, STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> forceInput(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "listen",
            List.of(
                new CallShape(
                    List.of(OBJECT, ANY), List.of(Set.of(ArgType.MAP)), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            true,
            call -> listen(call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "listeners",
            List.of(new CallShape(List.of(), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> listeners(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "unlisten",
            List.of(new CallShape(List.of(ANY), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            true,
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
            true,
            this::openNetworkConnection));
    TaskBuiltins.registerGroup3(entries, this);
    entries.add(
        new BuiltinSpec(
            "notify",
            List.of(new CallShape(List.of(OBJECT, STRING), List.of(ANY, ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> notifyLine(call.hosts(), call.arguments(), call.world(), call.programmer())));
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

  private BuiltinResult connectionInfo(BuiltinHosts invocationHosts,
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

  private BuiltinResult bufferedOutputLength(BuiltinHosts invocationHosts,
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

  private BuiltinResult connectionName(BuiltinHosts invocationHosts,
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

  private BuiltinResult connectionNameLookup(BuiltinCall call) {
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

  private BuiltinResult setConnectionOption(BuiltinHosts invocationHosts,
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

  private BuiltinResult openNetworkConnection(BuiltinCall call) {
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

  private BuiltinResult switchPlayer(BuiltinCall call) {
    long oldPlayer = ((ObjectValue) call.arguments().getFirst()).value();
    long newPlayer = ((ObjectValue) call.arguments().get(1)).value();
    if (oldPlayer == newPlayer
        || connections(call.hosts()).connectionId(oldPlayer).isEmpty()
        || !call.world().players().contains(newPlayer)) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    return call.hosts().switchPlayer().invoke(call);
  }

  private BuiltinResult notifyLine(BuiltinHosts invocationHosts,
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
