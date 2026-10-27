package world.mongoose.banteng.builtin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
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
import world.mongoose.banteng.builtin.family.FileIoBuiltins;
import java.util.function.LongBinaryOperator;
import java.util.function.Supplier;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA224Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA384Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.KeyParameter;
import world.mongoose.banteng.builtin.family.ConversionBuiltins;

import static world.mongoose.banteng.builtin.BuiltinResult.mutationResult;
import static world.mongoose.banteng.builtin.ValueLimits.valueBytes;

import world.mongoose.banteng.builtin.family.JsonBuiltins;
import world.mongoose.banteng.builtin.family.ListBuiltins;
import world.mongoose.banteng.builtin.family.MapBuiltins;
import world.mongoose.banteng.builtin.family.MathBuiltins;
import world.mongoose.banteng.builtin.family.RandomBuiltins;
import world.mongoose.banteng.builtin.family.ObjectBuiltins;
import world.mongoose.banteng.builtin.family.PropertyBuiltins;
import world.mongoose.banteng.builtin.family.StringBuiltins;
import world.mongoose.banteng.builtin.family.TimeBuiltins;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.host.Aspell;
import world.mongoose.banteng.host.Libc;
import world.mongoose.banteng.host.NativeCallException;
import world.mongoose.banteng.host.NativeCurl;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.syntax.MooParser;
import world.mongoose.banteng.syntax.MooUnparser;
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
import static world.mongoose.banteng.world.Access.resolveOwnershipPreamble;
import world.mongoose.banteng.world.Access;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.ObjectFlags;
import world.mongoose.banteng.world.Prepositions;
import world.mongoose.banteng.world.PropertyHolder;
import world.mongoose.banteng.world.VerbPerms;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldResult;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldVerb;

/** The explicit builtin catalog required by the first managed runtime path. */
public final class BuiltinCatalog {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
  private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
  private static final Set<ArgType> NUMBER = Set.of(ArgType.NUMBER);
  private static final Set<ArgType> STRING = Set.of(ArgType.STRING);
  private static final Set<ArgType> OBJECT = Set.of(ArgType.OBJECT);
  private static final int DEFAULT_MAX_QUEUED_OUTPUT = 65_536;
  private static final String SERVER_VERSION = "0.1.0-SNAPSHOT";

  private final List<BuiltinSpec> manifest;
  private final FileIoBuiltins fileIo;
  private final BuiltinHosts hosts;
  private final PcreService pcre = new PcreService();
  private final Optional<ListenerControl> listenerControl;
  private final Random random;
  private final SqliteService sqlite;
  private final ToastRegexService toastRegex = new ToastRegexService();
  private final Random floatingRandom;
  private final Map<String, BuiltinSpec> specs;
  private volatile Set<String> protectedFunctions = Set.of();

  /** Creates a catalog from one complete standalone host composition. */
  public BuiltinCatalog(BuiltinHosts hosts) {
    this.hosts = Objects.requireNonNull(hosts, "hosts");
    listenerControl = Optional.empty();
    ConfinedFileRoot files = new ConfinedFileRoot(Path.of("files"));
    fileIo = new FileIoBuiltins(files);
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
    fileIo = new FileIoBuiltins(files);
    sqlite = new SqliteService(files);
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
    entries.add(new BuiltinSpec(
        "task_local", List.of(new CallShape(List.of(), List.of(), Optional.empty())),
        BuiltinPermissionRule.WIZARD_ONLY, false, call -> BuiltinResult.value(call.taskLocal())));
    entries.add(new BuiltinSpec(
        "set_task_local", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
        BuiltinPermissionRule.WIZARD_ONLY, false, call -> new BuiltinResult.TaskLocal(call.arguments().getFirst())));
    entries.add(new BuiltinSpec(
        "getenv", List.of(new CallShape(List.of(STRING), List.of(), Optional.empty())),
        BuiltinPermissionRule.WIZARD_ONLY, false, call -> {
          String value = System.getenv(((StringValue) call.arguments().getFirst()).text());
          return value == null ? BuiltinResult.value(new IntegerValue(0))
              : encodeBinary(List.of(StringValue.of(value)));
        }));
    entries.add(new BuiltinSpec(
        "respond_to", List.of(new CallShape(List.of(ANY, STRING), List.of(), Optional.empty())),
        BuiltinPermissionRule.ANY, false, call -> respondTo(call.arguments(), call.world(), call.programmer())));
    BuiltinHandler setThreadMode =
        call -> setThreadMode(call.arguments(), call.threadMode());
    BuiltinHandler callFunction = this::callFunction;
    entries.add(
        new BuiltinSpec(
            "value_bytes",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call ->
                BuiltinResult.value(new IntegerValue(valueBytes(call.arguments().getFirst(), call.world())))));
    entries.add(
        new BuiltinSpec(
            "object_bytes",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> objectBytes(call.arguments().getFirst(), call.world(), call.programmer())));
    ListBuiltins.registerLength(entries);
    MathBuiltins.register(entries);
    RandomBuiltins.register(entries, () -> random, () -> floatingRandom, SECURE_RANDOM);
    TimeBuiltins.register(entries);
    entries.add(
        new BuiltinSpec(
            "raise",
            List.of(new CallShape(List.of(ANY), List.of(STRING, ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> raise(call.arguments())));
    ListBuiltins.registerListEdits(entries);
    MapBuiltins.register(entries);
    JsonBuiltins.register(entries);
    ListBuiltins.registerSetsAndSort(entries);
    StringBuiltins.registerExplode(entries);
    ListBuiltins.registerReverse(entries);
    StringBuiltins.registerSubstitutionAndAnsi(entries, random);
    entries.add(
        new BuiltinSpec(
            "simplex_noise",
            List.of(new CallShape(List.of(Set.of(ArgType.LIST)), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> simplexNoise(call.arguments())));
    StringBuiltins.registerSearchAndCompare(entries);
    entries.add(
        new BuiltinSpec(
            "crypt",
            List.of(new CallShape(List.of(STRING), List.of(STRING), Optional.empty())),
            BuiltinPermissionRule.ANY,
            true,
            call -> crypt(call.arguments(), call.world(), call.programmer())));
    entries.add(new BuiltinSpec("salt",
        List.of(new CallShape(List.of(STRING, STRING), List.of(), Optional.empty())),
        BuiltinPermissionRule.ANY, false, call -> salt(call.arguments())));
    for (String input : List.of("string", "binary", "value")) {
      entries.add(new BuiltinSpec(input + "_hash",
          List.of(new CallShape(List.of(input.equals("value") ? ANY : STRING),
              List.of(STRING, ANY), Optional.empty())),
          BuiltinPermissionRule.ANY, false, call -> hash(input, call.arguments())));
      entries.add(new BuiltinSpec(input + "_hmac",
          List.of(new CallShape(List.of(input.equals("value") ? ANY : STRING, STRING),
              List.of(STRING, ANY), Optional.empty())),
          BuiltinPermissionRule.ANY, false, call -> hmac(input, call.arguments())));
    }
    entries.add(
        new BuiltinSpec(
            "argon2",
            List.of(
                new CallShape(
                    List.of(STRING, STRING),
                    List.of(INTEGER, INTEGER, INTEGER),
                    Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            false,
            call -> argon2(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "argon2_verify",
            List.of(new CallShape(List.of(STRING, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            false,
            call ->
                argon2Verify(
                    call.arguments(), call.hosts().serverLog(), BuiltinCatalog::argon2Hash)));
    entries.add(
        new BuiltinSpec(
            "decode_binary",
            List.of(new CallShape(List.of(STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> decodeBinary(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "disassemble",
            List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> disassemble(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "curl",
            List.of(new CallShape(List.of(STRING), List.of(ANY, INTEGER), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            false,
            call -> curl(call.arguments(), call.world(), call.threadMode())));
    entries.add(
        new BuiltinSpec(
            "spellcheck",
            List.of(new CallShape(List.of(STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> spellcheck((StringValue) call.arguments().getFirst())));
    entries.add(
        new BuiltinSpec(
            "url_encode",
            List.of(new CallShape(List.of(STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> urlEncode((StringValue) call.arguments().getFirst())));
    entries.add(
        new BuiltinSpec(
            "url_decode",
            List.of(new CallShape(List.of(STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> urlDecode((StringValue) call.arguments().getFirst())));
    entries.add(
        new BuiltinSpec(
            "encode_binary",
            List.of(new CallShape(List.of(), List.of(), Optional.of(ANY))),
            BuiltinPermissionRule.ANY,
            false,
            call -> encodeBinary(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "chr",
            List.of(new CallShape(List.of(), List.of(), Optional.of(ANY))),
            BuiltinPermissionRule.ANY,
            false,
            call -> chr(call.arguments(), call.world(), call.programmer())));
    PropertyBuiltins.register(entries);
    entries.add(
        new BuiltinSpec(
            "delete_verb",
            List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
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
            false,
            call -> addVerb(call.arguments(), call.world(), call.programmer())));
    ObjectBuiltins.registerLifecycleAndTopology(entries);
    entries.add(
        new BuiltinSpec(
            "set_verb_code",
            List.of(
                new CallShape(
                    List.of(ANY, ANY, Set.of(ArgType.LIST)),
                    List.of(),
                    Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
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
            false,
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
            false,
            call -> setVerbInfo(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "verbs",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> verbs(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "verb_args",
            List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> verbArgs(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "verb_code",
            List.of(new CallShape(List.of(ANY, ANY), List.of(ANY, ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> verbCode(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "verb_info",
            List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> verbInfo(call.arguments(), call.world(), call.programmer())));
    ObjectBuiltins.registerMovementAndWaifs(entries);
    entries.add(
        new BuiltinSpec(
            "switch_player",
            List.of(new CallShape(List.of(OBJECT, OBJECT), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            false,
            call -> switchPlayer(call.hosts(), call.arguments(), call.world())));
    entries.add(
        new BuiltinSpec(
            "pass",
            List.of(new CallShape(List.of(), List.of(), Optional.of(ANY))),
            BuiltinPermissionRule.ANY,
            false,
            call -> new BuiltinResult.Pass(new ListValue(call.arguments()))));
    entries.add(
        new BuiltinSpec(
            "caller_perms",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call ->
                BuiltinResult.value(new ObjectValue(call.callers().apply(false).size() == 0 ? -1 : call.callerProgrammer()))));
    entries.add(
        new BuiltinSpec(
            "callers",
            List.of(new CallShape(List.of(), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> callers(call.callers().apply(
                !call.arguments().isEmpty() && call.arguments().getFirst().isTruthy()),
                call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "call_function",
            List.of(new CallShape(List.of(STRING), List.of(), Optional.of(ANY))),
            BuiltinPermissionRule.ANY,
            true,
            callFunction));
    entries.add(
        new BuiltinSpec(
            "queue_info",
            List.of(new CallShape(List.of(), List.of(OBJECT), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> call.hosts().queueInfo().invoke(call)));
    entries.add(
        new BuiltinSpec(
            "queued_tasks",
            List.of(new CallShape(List.of(), List.of(INTEGER, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            true,
            call -> call.hosts().queuedTasks().invoke(call)));
    entries.add(
        new BuiltinSpec(
            "task_stack",
            List.of(new CallShape(List.of(INTEGER), List.of(ANY, ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            true,
            call -> call.hosts().taskStack().invoke(call)));
    entries.add(
        new BuiltinSpec(
            "kill_task",
            List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            true,
            call -> call.hosts().killTask().invoke(call)));
    entries.add(
        new BuiltinSpec(
            "thread_pool",
            List.of(
                new CallShape(
                    List.of(STRING, STRING), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            true,
            this::configureThreadPool));
    entries.add(
        new BuiltinSpec(
            "threads",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            true,
            call -> call.hosts().threads().invoke(call)));
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
    entries.add(
        new BuiltinSpec(
            "match",
            List.of(new CallShape(List.of(STRING, STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> toastMatch(call.arguments(), false)));
    entries.add(
        new BuiltinSpec(
            "rmatch",
            List.of(new CallShape(List.of(STRING, STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> toastMatch(call.arguments(), true)));
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
    entries.add(
        new BuiltinSpec(
            "task_perms",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> BuiltinResult.value(new ObjectValue(call.programmer()))));
    entries.add(
        new BuiltinSpec(
            "task_id",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> BuiltinResult.value(new IntegerValue(call.taskId()))));
    entries.add(
        new BuiltinSpec(
            "ticks_left",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> BuiltinResult.value(new IntegerValue(call.remainingTicks()))));
    entries.add(
        new BuiltinSpec(
            "seconds_left",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> BuiltinResult.value(new IntegerValue(call.remainingSeconds()))));
    entries.add(
        new BuiltinSpec(
            "set_task_perms",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> setTaskPerms(call.arguments(), call.world(), call.programmer())));
    entries.add(
        new BuiltinSpec(
            "set_thread_mode",
            List.of(new CallShape(List.of(), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            setThreadMode));
    entries.add(
        new BuiltinSpec(
            "notify",
            List.of(new CallShape(List.of(OBJECT, STRING), List.of(ANY, ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> notifyLine(call.hosts(), call.arguments(), call.world(), call.programmer())));
    ConversionBuiltins.registerConversions(entries);
    entries.add(
        new BuiltinSpec(
            "eval",
            List.of(new CallShape(List.of(STRING), List.of(), Optional.of(ANY))),
            BuiltinPermissionRule.PROGRAMMER_ONLY,
            false,
            call -> dynamicEval(call.arguments())));
    ConversionBuiltins.registerTypeOf(entries);
    entries.add(
        new BuiltinSpec(
            "server_version",
            List.of(new CallShape(List.of(), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> serverVersion(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "function_info",
            List.of(new CallShape(List.of(), List.of(STRING), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> functionInfo(call.arguments())));
    fileIo.register(entries);
    entries.add(
        new BuiltinSpec(
            "pcre_match",
            List.of(
                new CallShape(
                    List.of(STRING, STRING), List.of(INTEGER, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            true,
            call -> pcre.match(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "pcre_replace",
            List.of(new CallShape(List.of(STRING, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> pcre.replace(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "pcre_cache_stats",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            false,
            call -> pcre.cacheStats()));
    addSqliteManifest(entries);
    entries.add(
        new BuiltinSpec(
            "server_log",
            List.of(new CallShape(List.of(STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            true,
            call -> serverLog(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "log_cache_stats",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            true,
            call -> logCacheStats(call.world())));
    entries.add(
        new BuiltinSpec(
            "verb_cache_stats",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            false,
            call -> verbCacheStats(call.world())));
    entries.add(
        new BuiltinSpec(
            "usage",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            false,
            call -> usage()));
    entries.add(
        new BuiltinSpec(
            "memory_usage",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> memoryUsage()));
    entries.add(
        new BuiltinSpec(
            "load_server_options",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            true,
            call -> {
              reloadProtectedFunctions(call.world());
              return BuiltinResult.value(new IntegerValue(0));
            }));
    entries.add(
        new BuiltinSpec(
            "run_gc",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            false,
            call -> BuiltinResult.value(new IntegerValue(0))));
    entries.add(
        new BuiltinSpec(
            "gc_stats",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            false,
            call -> gcStats()));
    ObjectBuiltins.registerResetMaximum(entries);
    entries.add(
        new BuiltinSpec(
            "suspend",
            List.of(new CallShape(List.of(), List.of(NUMBER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> suspend(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "resume",
            List.of(new CallShape(List.of(INTEGER), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            true,
            call -> call.hosts().resumeTask().invoke(call)));
    entries.add(
        new BuiltinSpec(
            "yin",
            List.of(
                new CallShape(
                    List.of(), List.of(NUMBER, INTEGER, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> yin(call.arguments(), call.world(), call.remainingTicks(), call.remainingSeconds())));
    entries.add(
        new BuiltinSpec(
            "shutdown",
            List.of(new CallShape(List.of(), List.of(STRING, ANY), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            false,
            call -> shutdown(call.arguments())));
    entries.add(
        new BuiltinSpec(
            "db_disk_size",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            false,
            call -> call.hosts().dbDiskSize().invoke(call)));
    entries.add(
        new BuiltinSpec(
            "dump_database",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            false,
            BuiltinCatalog::dumpDatabase));
    return List.copyOf(entries);
  }

  private void addSqliteManifest(List<BuiltinSpec> entries) {
    entries.add(
        sqliteSpec(
            "sqlite_open",
            new CallShape(List.of(STRING), List.of(INTEGER), Optional.empty()),
            true,
            call -> sqlite.open(call.arguments())));
    entries.add(
        sqliteSpec(
            "sqlite_close",
            new CallShape(List.of(INTEGER), List.of(), Optional.empty()),
            true,
            call -> sqlite.close(call.arguments())));
    entries.add(
        sqliteSpec(
            "sqlite_handles",
            new CallShape(List.of(), List.of(), Optional.empty()),
            false,
            call -> sqlite.handles(call.arguments())));
    entries.add(
        sqliteSpec(
            "sqlite_info",
            new CallShape(List.of(INTEGER), List.of(), Optional.empty()),
            false,
            call -> sqlite.info(call.arguments())));
    entries.add(
        sqliteSpec(
            "sqlite_query",
            new CallShape(List.of(INTEGER, STRING), List.of(ANY), Optional.empty()),
            false,
            call -> sqlite.query(call.arguments(), call.threadMode())));
    entries.add(
        sqliteSpec(
            "sqlite_execute",
            new CallShape(
                List.of(INTEGER, STRING, Set.of(ArgType.LIST)),
                List.of(),
                Optional.empty()),
            false,
            call -> sqlite.execute(call.arguments(), call.threadMode())));
    entries.add(
        sqliteSpec(
            "sqlite_last_insert_row_id",
            new CallShape(List.of(INTEGER), List.of(), Optional.empty()),
            false,
            call -> sqlite.lastInsertRowId(call.arguments())));
    entries.add(
        sqliteSpec(
            "sqlite_limit",
            new CallShape(List.of(INTEGER, ANY, INTEGER), List.of(), Optional.empty()),
            true,
            call -> sqlite.limit(call.arguments())));
    entries.add(
        sqliteSpec(
            "sqlite_interrupt",
            new CallShape(List.of(INTEGER), List.of(), Optional.empty()),
            true,
            call -> sqlite.interrupt(call.arguments())));
  }

  private static BuiltinSpec sqliteSpec(
      String name,
      CallShape shape,
      boolean irrevocable,
      BuiltinHandler handler) {
    return new BuiltinSpec(
        name,
        List.of(shape),
        BuiltinPermissionRule.WIZARD_ONLY,
        irrevocable,
        handler);
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
    return call.hosts().threadPool().invoke(call);
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

  private BuiltinResult functionInfo(List<MooValue> arguments) {
    if (arguments.isEmpty()) {
      return BuiltinResult.value(
          new ListValue(
              manifest.stream()
                  .map(BuiltinCatalog::functionInfoDescription)
                  .map(MooValue.class::cast)
                  .toList()));
    }
    StringValue requestedName = (StringValue) arguments.getFirst();
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

  private BuiltinResult callFunction(BuiltinCall call) {
    List<MooValue> arguments = call.arguments();
    String name = ((StringValue) arguments.getFirst()).text().toLowerCase(Locale.ROOT);
    BuiltinSpec target = specs.get(name);
    if (target == null) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    return invoke(target, new BuiltinCall(
        arguments.subList(1, arguments.size()), call.world(), call.programmer(),
        call.taskLocal(), call.taskId(), call.remainingTicks(), call.remainingSeconds(),
        call.receiver(), call.callerProgrammer(), call.callers(), call.threadMode(),
        call.stagedBufferedOutputLength(), call.hosts()));
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
      if (result.status() == 0) return encodeBinary(List.of(StringValue.of(result.body())));
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

  private static BuiltinResult raise(List<MooValue> arguments) {
    MooValue code = arguments.getFirst();
    StringValue message =
        arguments.size() >= 2 ? (StringValue) arguments.get(1)
            : (StringValue) ((BuiltinResult.Value) ConversionBuiltins.toStringValue(List.of(code))).value();
    MooValue value = arguments.size() >= 3 ? arguments.get(2) : new IntegerValue(0);
    return BuiltinResult.raised(code, message, value);
  }

  private static BuiltinResult respondTo(List<MooValue> arguments, WorldTxn world, long programmer) {
    MooValue target = arguments.getFirst();
    String name = ((StringValue) arguments.get(1)).text();
    WorldVerb verb;
    MooValue location;
    long owner;
    int flags;
    if (target instanceof ObjectValue object) {
      WorldObject body = world.object(object.value()).orElse(null);
      if (body == null) return BuiltinResult.error(ErrorValue.E_INVARG);
      verb = world.verb(object.value(), name).orElse(null);
      if (verb == null) return BuiltinResult.value(new IntegerValue(0));
      location = new ObjectValue(world.verbLocation(object.value(), name, true).orElseThrow());
      owner = body.owner();
      flags = body.flags();
    } else if (target instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject body = world.anonymousObject(anonymous).orElse(null);
      if (body == null) return BuiltinResult.error(ErrorValue.E_INVARG);
      verb = world.verb(anonymous, name, true).orElse(null);
      if (verb == null) return BuiltinResult.value(new IntegerValue(0));
      location = world.verbLocation(anonymous, name, true).orElseThrow();
      owner = body.owner();
      flags = body.flags();
    } else {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    return owner == programmer || world.isWizard(programmer) || ObjectFlags.isReadable(flags)
        ? BuiltinResult.value(new ListValue(List.of(location, StringValue.of(verb.names()))))
        : BuiltinResult.value(new IntegerValue(1));
  }

  private static BuiltinResult setThreadMode(List<MooValue> arguments, boolean threadMode) {
    if (arguments.isEmpty()) {
      return BuiltinResult.value(new IntegerValue(threadMode ? 1 : 0));
    }
    return new BuiltinResult.ThreadMode(arguments.getFirst().isTruthy());
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

  private BuiltinResult crypt(List<MooValue> arguments, WorldTxn world, long programmer) {
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
    if (!world.isWizard(programmer)
        && (isRecognizedBcrypt(salt) ? selectedStrength != 5 : selectedStrength != 0)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    String result = nativeCrypt(password, StringValue.of(salt).bytes());
    if (isRecognizedBcrypt(salt) && (result.equals("*0") || result.equals("*1"))) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    return BuiltinResult.value(StringValue.of(result));
  }

  private static BuiltinResult salt(List<MooValue> arguments) {
    String prefix = ((StringValue) arguments.getFirst()).text();
    String alphabet = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    boolean md5 = prefix.startsWith("$1$");
    boolean sha = prefix.startsWith("$5$") || prefix.startsWith("$6$");
    boolean bcrypt = isRecognizedBcrypt(prefix);
    OptionalLong strength = cryptStrength(prefix);
    if (strength.isEmpty() || (!md5 && !sha && !bcrypt && !prefix.isEmpty()
        && (prefix.length() < 2 || alphabet.indexOf(prefix.charAt(0)) < 0
            || alphabet.indexOf(prefix.charAt(1)) < 0))) {
      return BuiltinResult.raised(ErrorValue.E_INVARG, StringValue.of("Invalid prefix"),
          arguments.getFirst());
    }
    Optional<byte[]> decoded = binaryBytes((StringValue) arguments.get(1));
    if (decoded.isEmpty()) {
      return BuiltinResult.raised(ErrorValue.E_INVARG, StringValue.of("Invalid binary input"),
          arguments.get(1));
    }
    byte[] bytes = decoded.orElseThrow();
    if (bytes.length < (bcrypt ? 16 : md5 || sha ? 3 : 2) || prefix.startsWith("$2x$")) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    if (bcrypt) {
      long cost = strength.orElseThrow() == 0 ? 5 : strength.orElseThrow();
      String encoded = Base64.getEncoder().withoutPadding().encodeToString(Arrays.copyOf(bytes, 16));
      String base64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
      String bcryptAlphabet = "./ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
      StringBuilder output = new StringBuilder(prefix.substring(0, 4));
      output.append(cost < 10 ? "0" : "").append(cost).append('$');
      for (int index = 0; index < encoded.length(); index++) {
        output.append(bcryptAlphabet.charAt(base64.indexOf(encoded.charAt(index))));
      }
      return BuiltinResult.value(StringValue.of(output.toString()));
    }
    if (!md5 && !sha) {
      return BuiltinResult.value(StringValue.of("" + alphabet.charAt(bytes[0] & 63)
          + alphabet.charAt(bytes[1] & 63)));
    }
    StringBuilder output = new StringBuilder(prefix.substring(0, 3));
    if (sha && strength.orElseThrow() != 0) {
      output.append("rounds=").append(strength.orElseThrow()).append('$');
    }
    int length = Math.min(bytes.length / 3 * 3, md5 ? 6 : 12);
    for (int index = 0; index < length; index += 3) {
      int word = Byte.toUnsignedInt(bytes[index])
          | (Byte.toUnsignedInt(bytes[index + 1]) << 8)
          | (Byte.toUnsignedInt(bytes[index + 2]) << 16);
      for (int shift = 0; shift < 24; shift += 6) {
        output.append(alphabet.charAt((word >>> shift) & 63));
      }
    }
    return BuiltinResult.value(StringValue.of(output.toString()));
  }

  private static OptionalLong cryptStrength(String salt) {
    if (isRecognizedBcrypt(salt)) {
      int separator = salt.indexOf('$', 4);
      if (separator < 0) {
        return OptionalLong.of(0);
      }
      if (salt.length() < 7) {
        return OptionalLong.empty();
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
    if ((salt.startsWith("$5$") || salt.startsWith("$6$"))
        && salt.indexOf('$', 3) >= 0) {
      return OptionalLong.empty();
    }
    return OptionalLong.of(0);
  }

  private static boolean isRecognizedBcrypt(String salt) {
    return salt.startsWith("$2a$") || salt.startsWith("$2x$") || salt.startsWith("$2y$");
  }

  private String nativeCrypt(byte[] password, byte[] salt) {
    try {
      return hosts.crypt().hash(password, salt);
    } catch (NativeCallException failure) {
      return "*0";
    }
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

  private static Optional<byte[]> binaryBytes(StringValue encoded) {
    byte[] binary = encoded.bytes();
    ByteArrayOutputStream raw = new ByteArrayOutputStream(binary.length);
    for (int index = 0; index < binary.length; index++) {
      int value = Byte.toUnsignedInt(binary[index]);
      if (value != '~') {
        raw.write(value);
        continue;
      }
      if (index + 2 >= binary.length) {
        return Optional.empty();
      }
      int high = Character.digit((char) Byte.toUnsignedInt(binary[++index]), 16);
      int low = Character.digit((char) Byte.toUnsignedInt(binary[++index]), 16);
      if (high < 0 || low < 0) {
        return Optional.empty();
      }
      raw.write((high << 4) | low);
    }

    return Optional.of(raw.toByteArray());
  }

  private static Optional<byte[]> hashInput(String input, MooValue value) {
    return switch (input) {
      case "binary" -> binaryBytes((StringValue) value);
      case "value" -> Optional.of(StringValue.of(value.toLiteral()).bytes());
      default -> Optional.of(((StringValue) value).bytes());
    };
  }

  private static BuiltinResult hash(String input, List<MooValue> arguments) {
    String algorithm = arguments.size() > 1
        ? ((StringValue) arguments.get(1)).text().toLowerCase(Locale.ROOT) : "sha256";
    Digest digest = switch (algorithm) {
      case "md5" -> new MD5Digest();
      case "sha1" -> new SHA1Digest();
      case "sha224" -> new SHA224Digest();
      case "sha256" -> new SHA256Digest();
      case "sha384" -> new SHA384Digest();
      case "sha512" -> new SHA512Digest();
      case "ripemd160" -> new RIPEMD160Digest();
      default -> null;
    };
    Optional<byte[]> message = hashInput(input, arguments.getFirst());
    if (digest == null || message.isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    byte[] bytes = message.orElseThrow();
    digest.update(bytes, 0, bytes.length);
    byte[] result = new byte[digest.getDigestSize()];
    digest.doFinal(result, 0);
    return digestResult(result, arguments.size() > 2 && arguments.get(2).isTruthy());
  }

  private static BuiltinResult hmac(String input, List<MooValue> arguments) {
    String algorithm = arguments.size() > 2
        ? ((StringValue) arguments.get(2)).text().toLowerCase(Locale.ROOT) : "sha256";
    HMac mac = switch (algorithm) {
      case "sha1" -> new HMac(new SHA1Digest());
      case "sha256" -> new HMac(new SHA256Digest());
      default -> null;
    };
    Optional<byte[]> key = binaryBytes((StringValue) arguments.get(1));
    Optional<byte[]> message = hashInput(input, arguments.getFirst());
    if (mac == null || key.isEmpty() || message.isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    mac.init(new KeyParameter(key.orElseThrow()));
    byte[] bytes = message.orElseThrow();
    mac.update(bytes, 0, bytes.length);
    byte[] digest = new byte[mac.getMacSize()];
    mac.doFinal(digest, 0);
    return digestResult(digest, arguments.size() > 3 && arguments.get(3).isTruthy());
  }

  private static BuiltinResult digestResult(byte[] digest, boolean binary) {
    StringBuilder result = new StringBuilder(digest.length * (binary ? 3 : 2));
    for (byte current : digest) {
      int value = Byte.toUnsignedInt(current);
      if (binary) {
        result.append('~');
      }
      result.append("0123456789ABCDEF".charAt(value >>> 4));
      result.append("0123456789ABCDEF".charAt(value & 15));
    }
    return BuiltinResult.value(StringValue.of(result.toString()));
  }

  private static BuiltinResult decodeBinary(List<MooValue> arguments) {
    Optional<byte[]> bytes = binaryBytes((StringValue) arguments.getFirst());
    if (bytes.isEmpty()) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    byte[] decoded = bytes.orElseThrow();
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
    int minimum = world.isWizard(programmer) ? 0 : 32;
    int maximum = world.isWizard(programmer) ? 255 : 254;
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    for (MooValue argument : arguments) {
      if (!appendBinaryBytes(raw, argument, minimum, maximum)) {
        return BuiltinResult.error(ErrorValue.E_INVARG);
      }
    }
    byte[] bytes = raw.toByteArray();
    int length = 0;
    while (length < bytes.length && bytes[length] != 0) {
      length++;
    }
    return BuiltinResult.value(StringValue.of(Arrays.copyOf(bytes, length)));
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

    if (!Access.canRead(world, programmer, verb.owner(), verb.perms())) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    List<MooValue> lines = new ArrayList<>();
    lines.add(StringValue.of("Main code vector:"));
    verb.program().disassemble().lines()
            .map(StringValue::of)
            .map(MooValue.class::cast)
            .forEach(lines::add);
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
    String names = namesValue.text();
    int firstName = 0;
    while (firstName < names.length() && names.charAt(firstName) == ' ') {
      firstName++;
    }
    names = names.substring(firstName);
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
    ArgSpec direct =
        switch (directText.toLowerCase(Locale.ROOT)) {
          case "none" -> ArgSpec.NONE;
          case "any" -> ArgSpec.ANY;
          case "this" -> ArgSpec.THIS;
          default -> null;
        };
    ArgSpec indirect =
        switch (indirectText.toLowerCase(Locale.ROOT)) {
          case "none" -> ArgSpec.NONE;
          case "any" -> ArgSpec.ANY;
          case "this" -> ArgSpec.THIS;
          default -> null;
        };
    int preposition = parsePreposition(prepositionText);
    if (direct == null || indirect == null || preposition == Integer.MIN_VALUE) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }

    MooValue receiver = arguments.get(0);
    Optional<ErrorValue> ownershipError =
        resolveOwnershipPreamble(receiver, owner.value(), world, programmer);
    if (ownershipError.isPresent()) {
      return BuiltinResult.error(ownershipError.orElseThrow());
    }

    int slot =
        receiver instanceof ObjectValue object
            ? world.addVerb(
                object.value(), names, owner.value(), new VerbPerms(permissions), direct, preposition, indirect)
            : world.addVerb(
                (AnonymousObjectValue) receiver,
                names,
                owner.value(),
                new VerbPerms(permissions), direct, preposition, indirect);
    return BuiltinResult.value(
        new IntegerValue(slot));
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
    String names = namesValue.text();
    int firstName = 0;
    while (firstName < names.length() && names.charAt(firstName) == ' ') {
      firstName++;
    }
    names = names.substring(firstName);
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

    boolean wizard = world.isWizard(programmer);
    if (!Access.canWrite(world, programmer, verb.owner(), verb.perms())
        || (!wizard && verb.owner() != owner.value())) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    WorldResult<Boolean> updated =
        world.setVerbInfo(object.value(), verbIndex, names, owner.value(), new VerbPerms(permissions));
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
    if (owner != programmer && !world.isWizard(programmer) && !ObjectFlags.isReadable(flags)) {
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

    if (!Access.canRead(world, programmer, verb.owner(), verb.perms())) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    StringBuilder flags = new StringBuilder(4);
    if (verb.perms().readable()) {
      flags.append('r');
    }
    if (verb.perms().writable()) {
      flags.append('w');
    }
    if (verb.perms().executable()) {
      flags.append('x');
    }
    if (verb.perms().debug()) {
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

    if (!Access.canRead(world, programmer, verb.owner(), verb.perms())) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    String direct =
        switch (verb.dobj()) {
          case NONE -> "none";
          case ANY -> "any";
          case THIS -> "this";
          default -> throw new IllegalStateException("invalid direct-object verb specification");
        };
    String indirect =
        switch (verb.iobj()) {
          case NONE -> "none";
          case ANY -> "any";
          case THIS -> "this";
          default -> throw new IllegalStateException("invalid indirect-object verb specification");
        };
    String preposition = Prepositions.format(verb.preposition());
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

    boolean wizard = world.isWizard(programmer);
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
    ArgSpec direct =
        switch (directText.toLowerCase(Locale.ROOT)) {
          case "none" -> ArgSpec.NONE;
          case "any" -> ArgSpec.ANY;
          case "this" -> ArgSpec.THIS;
          default -> null;
        };
    ArgSpec indirect =
        switch (indirectText.toLowerCase(Locale.ROOT)) {
          case "none" -> ArgSpec.NONE;
          case "any" -> ArgSpec.ANY;
          case "this" -> ArgSpec.THIS;
          default -> null;
        };
    int preposition = parsePreposition(prepositionText);
    if (direct == null || indirect == null || preposition == Integer.MIN_VALUE) {
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

    if (!Access.canWrite(world, programmer, verb.owner(), verb.perms())) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    WorldResult<Boolean> updated =
        world.setVerbArgs(object.value(), verbIndex, direct, preposition, indirect);
    return mutationResult(updated, new IntegerValue(0));
  }

  private static int parsePreposition(String prepositionText) {
    return Prepositions.parse(prepositionText);
  }

  private BuiltinResult setVerbCode(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    ListValue code = (ListValue) arguments.get(2);
    if (code.elements().stream().anyMatch(value -> !(value instanceof StringValue))) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    MooValue receiver = arguments.get(0);
    if (!(receiver instanceof ObjectValue) && !(receiver instanceof AnonymousObjectValue)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    MooValue descriptor = arguments.get(1);
    if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
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

    boolean wizard = world.isWizard(programmer);
    WorldObject actor = world.object(programmer).orElse(null);
    boolean programmerFlag = actor != null && ObjectFlags.isProgrammer(actor.flags());
    if ((!programmerFlag && !wizard)
        || !Access.canWrite(world, programmer, verb.owner(), verb.perms())) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }

    String suppliedSource =
        code.elements().stream()
            .map(StringValue.class::cast)
            .map(StringValue::text)
            .collect(java.util.stream.Collectors.joining("\n"));
    MooCompiler.CompilationResult compiled =
        new MooCompiler().compileResult(suppliedSource, name -> spec(name).isPresent());
    if (!compiled.diagnostics().isEmpty()) {
      return BuiltinResult.value(new ListValue(compiled.diagnostics().stream()
          .map(diagnostic -> (MooValue) StringValue.of(diagnostic.display())).toList()));
    }
    String canonicalSource =
        Arrays.stream(compiled.program().orElseThrow().source().split("\n", -1))
            .map(String::stripLeading)
            .collect(java.util.stream.Collectors.joining("\n"));
    WorldResult<Boolean> updated =
        receiver instanceof ObjectValue object
            ? world.setVerbCode(object.value(), verbIndex, canonicalSource)
            : world.setVerbCode((AnonymousObjectValue) receiver, verbIndex, canonicalSource);
    return mutationResult(updated, new ListValue(List.of()));
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

    if (!Access.canRead(world, programmer, verb.owner(), verb.perms())) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }

    boolean indent = arguments.size() < 4 || arguments.get(3).isTruthy();
    if (verb.programSource().isEmpty()) {
      return BuiltinResult.value(new ListValue(List.of()));
    }
    List<MooValue> lines =
        Arrays.stream(MooUnparser.unparse(MooParser.parse(verb.programSource()),
            arguments.size() >= 3 && arguments.get(2).isTruthy()).split("\n", -1))
            .map(line -> indent ? line : line.stripLeading())
            .map(StringValue::of)
            .map(MooValue.class::cast)
            .toList();
    return BuiltinResult.value(new ListValue(lines));
  }

  private BuiltinResult switchPlayer(BuiltinHosts invocationHosts, List<MooValue> arguments, WorldTxn world) {
    long oldPlayer = ((ObjectValue) arguments.getFirst()).value();
    long newPlayer = ((ObjectValue) arguments.get(1)).value();
    if (oldPlayer == newPlayer
        || connections(invocationHosts).connectionId(oldPlayer).isEmpty()
        || !world.players().contains(newPlayer)) {
      return BuiltinResult.error(ErrorValue.E_INVARG);
    }
    return new BuiltinResult.SwitchPlayer(newPlayer);
  }

  private static BuiltinResult setTaskPerms(
      List<MooValue> arguments, WorldTxn world, long currentProgrammer) {
    ObjectValue requested = (ObjectValue) arguments.getFirst();
    if (requested.value() != currentProgrammer && !world.isWizard(currentProgrammer)) {
      return BuiltinResult.error(ErrorValue.E_PERM);
    }
    return new BuiltinResult.Programmer(requested.value());
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

  private BuiltinResult dynamicEval(List<MooValue> arguments) {
    if (arguments.stream().anyMatch(argument -> !(argument instanceof StringValue))) {
      return BuiltinResult.error(ErrorValue.E_TYPE);
    }
    String source =
        arguments.stream()
            .map(StringValue.class::cast)
            .map(StringValue::text)
            .collect(java.util.stream.Collectors.joining("\n"));
    MooCompiler.CompilationResult compiled =
        new MooCompiler().compileResult(source, name -> spec(name).isPresent());
    if (!compiled.diagnostics().isEmpty()) {
      return BuiltinResult.value(new ListValue(List.of(new IntegerValue(0),
          new ListValue(compiled.diagnostics().stream()
              .map(diagnostic -> (MooValue) StringValue.of(diagnostic.display())).toList()))));
    }
    return new BuiltinResult.DynamicEval(compiled.program().orElseThrow());
  }

  private static BuiltinResult gcStats() {
    Map<MooValue, MooValue> colors = new LinkedHashMap<>();
    for (String color :
        List.of("green", "yellow", "black", "gray", "white", "purple", "pink")) {
      colors.put(StringValue.of(color), new IntegerValue(0));
    }
    return BuiltinResult.value(new MapValue(colors));
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
