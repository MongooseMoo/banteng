package moo.builtin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import moo.bytecode.MooCompiler;
import moo.syntax.MooParser;
import moo.syntax.MooUnparser;
import moo.value.MooValue;
import moo.value.MooValue.AnonymousObjectValue;
import moo.value.MooValue.BooleanValue;
import moo.value.MooValue.ErrorValue;
import moo.value.MooValue.FloatValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.ListValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.ObjectValue;
import moo.value.MooValue.StringValue;
import moo.value.MooValue.WaifValue;
import moo.world.WorldAnonymousObject;
import moo.world.WorldObject;
import moo.world.WorldProperty;
import moo.world.WorldTxn;
import moo.world.WorldVerb;

/** The explicit builtin catalog required by the first managed runtime path. */
public final class BuiltinCatalog {
  private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
  private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
  private static final Set<ArgType> NUMBER = Set.of(ArgType.NUMBER);
  private static final Set<ArgType> STRING = Set.of(ArgType.STRING);
  private static final Set<ArgType> OBJECT = Set.of(ArgType.OBJECT);

  private final List<BuiltinSpec> manifest;
  private final BuiltinHandler killTask;
  private final BuiltinHandler queuedTasks;
  private final Random random;
  private final Map<String, BuiltinSpec> specs;

  /** Creates a catalog without host listener access for focused VM execution. */
  public BuiltinCatalog() {
    this(
        (a, w, p, t, rt, rs, r, cp, c) -> emptyQueuedTasks(a),
        (a, w, p, t, rt, rs, r, cp, c) -> Result.error(ErrorValue.E_INVARG));
  }

  /** Creates a catalog with the production task-registry handler. */
  public BuiltinCatalog(BuiltinHandler queuedTasks) {
    this(
        queuedTasks,
        (a, w, p, t, rt, rs, r, cp, c) -> Result.error(ErrorValue.E_INVARG));
  }

  /** Creates a catalog with the production task-registry handlers. */
  public BuiltinCatalog(BuiltinHandler queuedTasks, BuiltinHandler killTask) {
    this.queuedTasks = Objects.requireNonNull(queuedTasks, "queuedTasks");
    this.killTask = Objects.requireNonNull(killTask, "killTask");
    random = new Random();
    manifest = buildManifest();
    specs = indexManifest(manifest);
  }

  /** Creates the production catalog with the concrete server listener owner. */
  public BuiltinCatalog(ListenerControl listenerControl) {
    this(
        listenerControl,
        (a, w, p, t, rt, rs, r, cp, c) -> emptyQueuedTasks(a),
        (a, w, p, t, rt, rs, r, cp, c) -> Result.error(ErrorValue.E_INVARG));
  }

  /** Creates the production catalog with concrete listener and task owners. */
  public BuiltinCatalog(ListenerControl listenerControl, BuiltinHandler queuedTasks) {
    this(
        listenerControl,
        queuedTasks,
        (a, w, p, t, rt, rs, r, cp, c) -> Result.error(ErrorValue.E_INVARG));
  }

  /** Creates the production catalog with concrete listener and task owners. */
  public BuiltinCatalog(
      ListenerControl listenerControl, BuiltinHandler queuedTasks, BuiltinHandler killTask) {
    Objects.requireNonNull(listenerControl, "listenerControl");
    this.queuedTasks = Objects.requireNonNull(queuedTasks, "queuedTasks");
    this.killTask = Objects.requireNonNull(killTask, "killTask");
    random = new Random();
    manifest = buildManifest();
    specs = indexManifest(manifest);
  }

  private List<BuiltinSpec> buildManifest() {
    List<BuiltinSpec> entries = new ArrayList<>();
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
            (a, w, p, t, rt, rs, r, cp, c) -> length(a)));
    entries.add(
        new BuiltinSpec(
            "max",
            List.of(new CallShape(List.of(NUMBER), List.of(), Optional.of(NUMBER))),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> maximum(a)));
    entries.add(
        new BuiltinSpec(
            "random",
            List.of(new CallShape(List.of(), List.of(INTEGER, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> randomInteger(a)));
    entries.add(
        new BuiltinSpec(
            "time",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) ->
                Result.value(new IntegerValue(Instant.now().getEpochSecond()))));
    entries.add(
        new BuiltinSpec(
            "raise",
            List.of(new CallShape(List.of(ANY), List.of(STRING, ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> raise(a)));
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
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> listInsert(a, true)));
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
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> listInsert(a, false)));
    entries.add(
        new BuiltinSpec(
            "listdelete",
            List.of(
                new CallShape(
                    List.of(Set.of(ArgType.LIST), INTEGER), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> listDelete(a)));
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
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> listSet(a)));
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
            (a, w, p, t, rt, rs, r, cp, c) -> mapKeys(a)));
    entries.add(
        new BuiltinSpec(
            "setadd",
            List.of(new CallShape(List.of(Set.of(ArgType.LIST), ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> setAdd(a)));
    entries.add(
        new BuiltinSpec(
            "strsub",
            List.of(new CallShape(List.of(STRING, STRING, STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> stringSubstitute(a)));
    entries.add(
        new BuiltinSpec(
            "index",
            List.of(new CallShape(List.of(STRING, STRING), List.of(ANY, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> stringIndex(a, false)));
    entries.add(
        new BuiltinSpec(
            "rindex",
            List.of(new CallShape(List.of(STRING, STRING), List.of(ANY, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> stringIndex(a, true)));
    entries.add(
        new BuiltinSpec(
            "strcmp",
            List.of(new CallShape(List.of(STRING, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> stringCompare(a)));
    entries.add(
        new BuiltinSpec(
            "decode_binary",
            List.of(new CallShape(List.of(STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> decodeBinary(a)));
    entries.add(
        new BuiltinSpec(
            "disassemble",
            List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> disassemble(a, w, p)));
    entries.add(
        new BuiltinSpec(
            "encode_binary",
            List.of(new CallShape(List.of(), List.of(), Optional.of(ANY))),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> encodeBinary(a)));
    entries.add(
        new BuiltinSpec(
            "chr",
            List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> {
              long code = ((IntegerValue) a.getFirst()).value();
              return code < 0 || code > 255
                  ? Result.error(ErrorValue.E_INVARG)
                  : Result.value(new StringValue(new byte[] {(byte) code}));
            }));
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
            (a, w, p, t, rt, rs, r, cp, c) -> addProperty(a, w, p)));
    entries.add(
        new BuiltinSpec(
            "properties",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> properties(a, w, p)));
    entries.add(
        new BuiltinSpec(
            "property_info",
            List.of(new CallShape(List.of(ANY, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> propertyInfo(a, w, p)));
    entries.add(
        new BuiltinSpec(
            "is_clear_property",
            List.of(new CallShape(List.of(ANY, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> isClearProperty(a, w, p)));
    entries.add(
        new BuiltinSpec(
            "clear_property",
            List.of(new CallShape(List.of(ANY, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> clearProperty(a, w, p)));
    entries.add(
        new BuiltinSpec(
            "delete_property",
            List.of(new CallShape(List.of(ANY, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> deleteProperty(a, w, p)));
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
            (a, w, p, t, rt, rs, r, cp, c) -> addVerb(a, w, p)));
    entries.add(
        new BuiltinSpec(
            "create",
            List.of(new CallShape(List.of(OBJECT), List.of(INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> create(a, w, p)));
    entries.add(
        new BuiltinSpec(
            "parent",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> parent(a, w)));
    entries.add(
        new BuiltinSpec(
            "is_player",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> isPlayer(a, w)));
    entries.add(
        new BuiltinSpec(
            "valid",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> valid(a, w)));
    entries.add(
        new BuiltinSpec(
            "set_player_flag",
            List.of(new CallShape(List.of(OBJECT, INTEGER), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> setPlayerFlag(a, w)));
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
            (a, w, p, t, rt, rs, r, cp, c) -> setVerbCode(a, w, p)));
    entries.add(
        new BuiltinSpec(
            "verb_code",
            List.of(new CallShape(List.of(ANY, ANY), List.of(ANY, ANY), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_READ,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> verbCode(a, w, p)));
    entries.add(
        new BuiltinSpec(
            "move",
            List.of(new CallShape(List.of(OBJECT, OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> move(a, w, p)));
    entries.add(
        new BuiltinSpec(
            "recycle",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> recycle(a, w, p)));
    entries.add(
        new BuiltinSpec(
            "new_waif",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.TRANSACTION_WRITE,
            BuiltinOwner.WORLD,
            (a, w, p, t, rt, rs, r, cp, c) -> {
              if (!(r instanceof ObjectValue classObject)
                  || w.object(classObject.value()).isEmpty()) {
                return Result.error(ErrorValue.E_INVIND);
              }
              return Result.value(w.createWaif(classObject.value(), p));
            }));
    entries.add(
        new BuiltinSpec(
            "switch_player",
            List.of(new CallShape(List.of(OBJECT, OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.CONNECTION,
            (a, w, p, t, rt, rs, r, cp, c) -> switchPlayer(a)));
    entries.add(
        new BuiltinSpec(
            "caller_perms",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) ->
                Result.value(new ObjectValue(c.size() == 0 ? -1 : cp))));
    entries.add(
        new BuiltinSpec(
            "queued_tasks",
            List.of(new CallShape(List.of(), List.of(INTEGER, INTEGER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.TASK,
            queuedTasks));
    entries.add(
        new BuiltinSpec(
            "kill_task",
            List.of(new CallShape(List.of(INTEGER), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.TASK,
            killTask));
    entries.add(
        new BuiltinSpec(
            "task_perms",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> Result.value(new ObjectValue(p))));
    entries.add(
        new BuiltinSpec(
            "set_task_perms",
            List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> setTaskPerms(a)));
    entries.add(
        new BuiltinSpec(
            "notify",
            List.of(new CallShape(List.of(OBJECT, STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.CONNECTION,
            (a, w, p, t, rt, rs, r, cp, c) -> notifyLine(a)));
    entries.add(
        new BuiltinSpec(
            "tostr",
            List.of(new CallShape(List.of(), List.of(), Optional.of(ANY))),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> toStringValue(a)));
    entries.add(
        new BuiltinSpec(
            "tofloat",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> toFloat(a)));
    entries.add(
        new BuiltinSpec(
            "toint",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> toInteger(a)));
    entries.add(
        new BuiltinSpec(
            "toliteral",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> toLiteral(a)));
    entries.add(
        new BuiltinSpec(
            "toobj",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> toObject(a)));
    entries.add(
        new BuiltinSpec(
            "equal",
            List.of(new CallShape(List.of(ANY, ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> equalValues(a)));
    entries.add(
        new BuiltinSpec(
            "eval",
            List.of(new CallShape(List.of(STRING), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> dynamicEval(a)));
    entries.add(
        new BuiltinSpec(
            "typeof",
            List.of(new CallShape(List.of(ANY), List.of(), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> typeOf(a)));
    entries.add(
        new BuiltinSpec(
            "function_info",
            List.of(new CallShape(List.of(), List.of(STRING), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> functionInfo(a)));
    entries.add(
        new BuiltinSpec(
            "server_log",
            List.of(new CallShape(List.of(STRING), List.of(ANY), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.IRREVOCABLE,
            BuiltinOwner.SERVER,
            (a, w, p, t, rt, rs, r, cp, c) -> serverLog(a)));
    entries.add(
        new BuiltinSpec(
            "run_gc",
            List.of(new CallShape(List.of(), List.of(), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> Result.zero()));
    entries.add(
        new BuiltinSpec(
            "suspend",
            List.of(new CallShape(List.of(), List.of(NUMBER), Optional.empty())),
            BuiltinPermissionRule.ANY,
            BuiltinCostRule.fixed(0),
            EffectClass.PURE,
            BuiltinOwner.VM,
            (a, w, p, t, rt, rs, r, cp, c) -> suspend(a)));
    entries.add(
        new BuiltinSpec(
            "shutdown",
            List.of(new CallShape(List.of(), List.of(STRING, ANY), Optional.empty())),
            BuiltinPermissionRule.WIZARD_ONLY,
            BuiltinCostRule.fixed(0),
            EffectClass.DEFERRED_COMMIT,
            BuiltinOwner.SERVER,
            (a, w, p, t, rt, rs, r, cp, c) -> shutdown(a)));
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

  private static Result emptyQueuedTasks(List<MooValue> arguments) {
    return Result.value(
        arguments.size() == 2 && arguments.get(1).isTruthy()
            ? new IntegerValue(0)
            : new ListValue(List.of()));
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
  public Result invoke(
      String name,
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers) {
    Optional<BuiltinSpec> selected = spec(name);
    if (selected.isEmpty()) {
      return Result.error(ErrorValue.E_VERBNF);
    }
    return invoke(
        selected.orElseThrow(),
        arguments,
        world,
        programmer,
        taskLocal,
        remainingTicks,
        remainingSeconds,
        receiver,
        callerProgrammer,
        callers);
  }

  /** Validates and invokes one exact manifest entry. */
  public Result invoke(
      BuiltinSpec spec,
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers) {
    if (spec.callShapes().stream().noneMatch(shape -> shape.acceptsArity(arguments.size()))) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (spec.callShapes().stream().noneMatch(shape -> shape.accepts(arguments))) {
      return Result.error(ErrorValue.E_TYPE);
    }
    if (!spec.permission().allows(world, programmer)) {
      return Result.error(ErrorValue.E_PERM);
    }
    return spec.implementation()
        .invoke(
            arguments,
            world,
            programmer,
            taskLocal,
            remainingTicks,
            remainingSeconds,
            receiver,
            callerProgrammer,
            callers);
  }

  private Result functionInfo(List<MooValue> arguments) {
    if (arguments.size() > 1) {
      return Result.error(ErrorValue.E_ARGS);
    }
    if (arguments.isEmpty()) {
      return Result.value(
          new ListValue(
              manifest.stream()
                  .map(BuiltinCatalog::functionInfoDescription)
                  .map(MooValue.class::cast)
                  .toList()));
    }
    if (!(arguments.getFirst() instanceof StringValue requestedName)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    BuiltinSpec requested = specs.get(decode(requestedName).toLowerCase(Locale.ROOT));
    return requested == null
        ? Result.error(ErrorValue.E_INVARG)
        : Result.value(functionInfoDescription(requested));
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
            encode(spec.name()),
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

  private static Result dumpDatabase(
      List<MooValue> arguments,
      WorldTxn world,
      long programmer,
      MooValue taskLocal,
      long remainingTicks,
      long remainingSeconds,
      MooValue receiver,
      long callerProgrammer,
      ListValue callers) {
    return Result.checkpoint();
  }

  private static Result serverLog(List<MooValue> arguments) {
    String message = decode((StringValue) arguments.getFirst());
    System.err.println("> " + message);
    return Result.zero();
  }

  private static Result suspend(List<MooValue> arguments) {
    double seconds = 0;
    if (!arguments.isEmpty()) {
      MooValue delay = arguments.getFirst();
      seconds =
          delay instanceof IntegerValue integer
              ? integer.value()
              : ((FloatValue) delay).value();
    }
    return seconds < 0 ? Result.error(ErrorValue.E_INVARG) : Result.suspend(seconds);
  }

  private static Result shutdown(List<MooValue> arguments) {
    if (arguments.size() == 2 && arguments.get(1).isTruthy()) {
      return Result.error(ErrorValue.E_INVARG);
    }
    return Result.shutdown();
  }

  private static Result length(List<MooValue> arguments) {
    MooValue value = arguments.getFirst();
    if (value instanceof StringValue string) {
      return Result.value(new IntegerValue(string.length()));
    }
    if (value instanceof ListValue list) {
      return Result.value(new IntegerValue(list.size()));
    }
    if (value instanceof MapValue map) {
      return Result.value(new IntegerValue(map.size()));
    }
    return Result.error(ErrorValue.E_TYPE);
  }

  private static Result maximum(List<MooValue> arguments) {
    MooValue first = arguments.getFirst();
    if (first instanceof IntegerValue integer) {
      long maximum = integer.value();
      for (MooValue argument : arguments) {
        if (!(argument instanceof IntegerValue value)) {
          return Result.error(ErrorValue.E_TYPE);
        }
        maximum = Math.max(maximum, value.value());
      }
      return Result.value(new IntegerValue(maximum));
    }
    if (first instanceof FloatValue floating) {
      double maximum = floating.value();
      for (MooValue argument : arguments) {
        if (!(argument instanceof FloatValue value)) {
          return Result.error(ErrorValue.E_TYPE);
        }
        maximum = Math.max(maximum, value.value());
      }
      return Result.value(new FloatValue(maximum));
    }
    return Result.error(ErrorValue.E_TYPE);
  }

  private Result randomInteger(List<MooValue> arguments) {
    long lower = 1;
    long upper = Long.MAX_VALUE;
    if (arguments.size() == 1) {
      upper = ((IntegerValue) arguments.getFirst()).value();
      if (upper <= 0) {
        return Result.error(ErrorValue.E_INVARG);
      }
    } else if (arguments.size() == 2) {
      lower = ((IntegerValue) arguments.get(0)).value();
      upper = ((IntegerValue) arguments.get(1)).value();
      if (upper < lower) {
        return Result.error(ErrorValue.E_INVARG);
      }
    }
    if (lower == upper) {
      return Result.value(new IntegerValue(lower));
    }
    long width = upper - lower + 1;
    if (width > 0) {
      return Result.value(new IntegerValue(lower + random.nextLong(width)));
    }
    long value;
    do {
      value = random.nextLong();
    } while (value < lower || value > upper);
    return Result.value(new IntegerValue(value));
  }

  private static Result raise(List<MooValue> arguments) {
    MooValue code = arguments.getFirst();
    ErrorValue error = code instanceof ErrorValue errorValue ? errorValue : ErrorValue.E_INVARG;
    StringValue message =
        arguments.size() >= 2 ? (StringValue) arguments.get(1) : encode(code.toLiteral());
    MooValue value = arguments.size() >= 3 ? arguments.get(2) : new IntegerValue(0);
    return Result.raised(error, message, value);
  }

  private static Result listInsert(List<MooValue> arguments, boolean append) {
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
    return Result.value(new ListValue(inserted));
  }

  private static Result listDelete(List<MooValue> arguments) {
    ListValue list = (ListValue) arguments.get(0);
    long position = ((IntegerValue) arguments.get(1)).value();
    if (position <= 0 || position > list.size()) {
      return Result.error(ErrorValue.E_RANGE);
    }
    List<MooValue> deleted = new ArrayList<>(list.elements());
    deleted.remove((int) position - 1);
    return Result.value(new ListValue(deleted));
  }

  private static Result listSet(List<MooValue> arguments) {
    ListValue list = (ListValue) arguments.get(0);
    long position = ((IntegerValue) arguments.get(2)).value();
    if (position <= 0 || position > list.size()) {
      return Result.error(ErrorValue.E_RANGE);
    }
    List<MooValue> replaced = new ArrayList<>(list.elements());
    replaced.set((int) position - 1, arguments.get(1));
    return Result.value(new ListValue(replaced));
  }

  private static Result mapKeys(List<MooValue> arguments) {
    MapValue map = (MapValue) arguments.getFirst();
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
            case BooleanValue ignored -> 0;
            case StringValue string -> string.compareIgnoringCase((StringValue) right);
            case AnonymousObjectValue ignored -> left == right ? 0 : 1;
            case WaifValue ignored -> 0;
            case ListValue ignored -> throw new IllegalArgumentException("list map key");
            case MapValue ignored -> throw new IllegalArgumentException("map map key");
          };
        });
    return Result.value(new ListValue(keys));
  }

  private static Result setAdd(List<MooValue> arguments) {
    ListValue list = (ListValue) arguments.get(0);
    MooValue value = arguments.get(1);
    return Result.value(list.elements().contains(value) ? list : list.append(value));
  }

  private static Result stringSubstitute(List<MooValue> arguments) {
    byte[] source = ((StringValue) arguments.get(0)).bytes();
    byte[] what = ((StringValue) arguments.get(1)).bytes();
    byte[] replacement = ((StringValue) arguments.get(2)).bytes();
    if (what.length == 0) {
      return Result.error(ErrorValue.E_INVARG);
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
    return Result.value(new StringValue(substituted.toByteArray()));
  }

  private static Result stringIndex(List<MooValue> arguments, boolean reverse) {
    byte[] source = ((StringValue) arguments.get(0)).bytes();
    byte[] what = ((StringValue) arguments.get(1)).bytes();
    boolean caseMatters = arguments.size() >= 3 && arguments.get(2).isTruthy();
    long offset = arguments.size() == 4 ? ((IntegerValue) arguments.get(3)).value() : 0;
    if ((!reverse && offset < 0) || (reverse && offset > 0)) {
      return Result.error(ErrorValue.E_INVARG);
    }

    if (reverse) {
      long prefixLength = source.length + offset;
      if (prefixLength < 0) {
        return Result.value(new IntegerValue(0));
      }
      int length = (int) Math.min(prefixLength, source.length);
      for (int position = length - what.length; position >= 0; position--) {
        if (matchesAt(source, position, what, caseMatters)) {
          return Result.value(new IntegerValue(position + 1L));
        }
      }
      return Result.value(new IntegerValue(0));
    }

    if (offset > source.length) {
      return Result.value(new IntegerValue(0));
    }
    int start = (int) offset;
    for (int position = start; position <= source.length - what.length; position++) {
      if (matchesAt(source, position, what, caseMatters)) {
        return Result.value(new IntegerValue(position - start + 1L));
      }
    }
    return Result.value(new IntegerValue(0));
  }

  private static Result stringCompare(List<MooValue> arguments) {
    byte[] left = ((StringValue) arguments.get(0)).bytes();
    byte[] right = ((StringValue) arguments.get(1)).bytes();
    int commonLength = Math.min(left.length, right.length);
    for (int index = 0; index < commonLength; index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return Result.value(new IntegerValue(comparison));
      }
    }
    return Result.value(new IntegerValue(Integer.compare(left.length, right.length)));
  }

  private static Result decodeBinary(List<MooValue> arguments) {
    byte[] binary = ((StringValue) arguments.getFirst()).bytes();
    ByteArrayOutputStream raw = new ByteArrayOutputStream(binary.length);
    for (int index = 0; index < binary.length; index++) {
      int value = Byte.toUnsignedInt(binary[index]);
      if (value != '~') {
        raw.write(value);
        continue;
      }
      if (index + 2 >= binary.length) {
        return Result.error(ErrorValue.E_INVARG);
      }
      int high = Character.digit((char) Byte.toUnsignedInt(binary[++index]), 16);
      int low = Character.digit((char) Byte.toUnsignedInt(binary[++index]), 16);
      if (high < 0 || low < 0) {
        return Result.error(ErrorValue.E_INVARG);
      }
      raw.write((high << 4) | low);
    }

    byte[] decoded = raw.toByteArray();
    if (arguments.size() == 2 && arguments.get(1).isTruthy()) {
      return Result.value(
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
          values.add(new StringValue(printable.toByteArray()));
          printable.reset();
        }
        values.add(new IntegerValue(value));
      }
    }
    if (printable.size() != 0) {
      values.add(new StringValue(printable.toByteArray()));
    }
    return Result.value(new ListValue(values));
  }

  private static Result encodeBinary(List<MooValue> arguments) {
    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    for (MooValue argument : arguments) {
      if (!appendBinaryBytes(raw, argument)) {
        return Result.error(ErrorValue.E_INVARG);
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
    return Result.value(new StringValue(encoded.toByteArray()));
  }

  private static Result disassemble(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (!(arguments.get(0) instanceof ObjectValue object)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    MooValue descriptor = arguments.get(1);
    if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return Result.error(ErrorValue.E_INVARG);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return Result.error(ErrorValue.E_INVARG);
    }

    WorldVerb verb;
    if (descriptor instanceof IntegerValue integer) {
      long index = integer.value() - 1;
      if (index >= target.verbs().size()) {
        return Result.error(ErrorValue.E_VERBNF);
      }
      verb = target.verbs().get((int) index);
    } else {
      WorldVerb candidate =
          world.verb(object.value(), decode((StringValue) descriptor), false).orElse(null);
      if (candidate == null || !target.verbs().contains(candidate)) {
        return Result.error(ErrorValue.E_VERBNF);
      }
      verb = candidate;
    }

    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && (actor.flags() & 4) != 0;
    if (verb.owner() != programmer && !wizard && (verb.permissions() & 1) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    List<MooValue> lines =
        new MooCompiler().compile(verb.programSource()).disassemble().lines()
            .map(BuiltinCatalog::encode)
            .map(MooValue.class::cast)
            .toList();
    return Result.value(new ListValue(lines));
  }

  private static boolean appendBinaryBytes(ByteArrayOutputStream output, MooValue value) {
    if (value instanceof StringValue string) {
      output.writeBytes(string.bytes());
      return true;
    }
    if (value instanceof IntegerValue integer) {
      if (integer.value() < 0 || integer.value() > 255) {
        return false;
      }
      output.write((int) integer.value());
      return true;
    }
    if (value instanceof ListValue list) {
      for (MooValue element : list.elements()) {
        if (!appendBinaryBytes(output, element)) {
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

  private static Result create(List<MooValue> arguments, WorldTxn world, long programmer) {
    ObjectValue parent = (ObjectValue) arguments.getFirst();
    try {
      if (arguments.size() == 2 && ((IntegerValue) arguments.get(1)).value() != 0) {
        return Result.value(world.createAnonymousObject(parent.value(), programmer));
      }
      WorldObject created = world.createObject(parent.value(), programmer);
      return Result.value(new ObjectValue(created.id()));
    } catch (IllegalArgumentException error) {
      return Result.error(ErrorValue.E_INVARG);
    }
  }

  private static Result parent(List<MooValue> arguments, WorldTxn world) {
    if (!(arguments.getFirst() instanceof ObjectValue object)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    return target == null
        ? Result.error(ErrorValue.E_INVARG)
        : Result.value(new ObjectValue(target.parent()));
  }

  private static Result isPlayer(List<MooValue> arguments, WorldTxn world) {
    ObjectValue object = (ObjectValue) arguments.getFirst();
    if (world.object(object.value()).isEmpty()) {
      return Result.error(ErrorValue.E_INVARG);
    }
    return Result.value(new IntegerValue(world.players().contains(object.value()) ? 1 : 0));
  }

  private static Result valid(List<MooValue> arguments, WorldTxn world) {
    MooValue value = arguments.getFirst();
    if (value instanceof ObjectValue object) {
      return Result.value(new IntegerValue(world.object(object.value()).isPresent() ? 1 : 0));
    }
    if (value instanceof AnonymousObjectValue anonymous) {
      return Result.value(new IntegerValue(world.anonymousObject(anonymous).isPresent() ? 1 : 0));
    }
    return Result.error(ErrorValue.E_TYPE);
  }

  private static Result addVerb(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    ListValue info = (ListValue) arguments.get(1);
    if (info.size() != 3
        || !(info.elements().get(0) instanceof ObjectValue owner)
        || !(info.elements().get(1) instanceof StringValue permissionValue)
        || !(info.elements().get(2) instanceof StringValue namesValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    if (world.object(owner.value()).isEmpty()) {
      return Result.error(ErrorValue.E_INVARG);
    }
    int permissions = 0;
    String permissionText = decode(permissionValue);
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
        return Result.error(ErrorValue.E_INVARG);
      }
    }
    String names = decode(namesValue).stripLeading();
    if (names.isEmpty()) {
      return Result.error(ErrorValue.E_INVARG);
    }

    ListValue verbArguments = (ListValue) arguments.get(2);
    if (verbArguments.size() != 3
        || verbArguments.elements().stream().anyMatch(value -> !(value instanceof StringValue))) {
      return Result.error(ErrorValue.E_TYPE);
    }
    String directText = decode((StringValue) verbArguments.elements().get(0));
    String prepositionText = decode((StringValue) verbArguments.elements().get(1));
    String indirectText = decode((StringValue) verbArguments.elements().get(2));
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
          default -> Integer.MIN_VALUE;
        };
    if (direct < 0 || indirect < 0 || preposition == Integer.MIN_VALUE) {
      return Result.error(ErrorValue.E_INVARG);
    }

    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && (actor.flags() & 4) != 0;
    MooValue receiver = arguments.get(0);
    long targetOwner;
    int targetFlags;
    if (receiver instanceof ObjectValue object) {
      WorldObject target = world.object(object.value()).orElse(null);
      if (target == null) {
        return Result.error(ErrorValue.E_INVARG);
      }
      targetOwner = target.owner();
      targetFlags = target.flags();
    } else if (receiver instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
      if (target == null) {
        return Result.error(ErrorValue.E_INVARG);
      }
      targetOwner = target.owner();
      targetFlags = target.flags();
    } else {
      return Result.error(ErrorValue.E_TYPE);
    }
    boolean writable = targetOwner == programmer || wizard || (targetFlags & 32) != 0;
    if (!writable || (owner.value() != programmer && !wizard)) {
      return Result.error(ErrorValue.E_PERM);
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
    return Result.value(
        new IntegerValue(slot));
  }

  private static Result addProperty(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    ListValue info = (ListValue) arguments.get(3);
    if (info.size() != 2
        || !(info.elements().get(0) instanceof ObjectValue owner)
        || !(info.elements().get(1) instanceof StringValue permissionValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    if (world.object(owner.value()).isEmpty()) {
      return Result.error(ErrorValue.E_INVARG);
    }
    int permissions = 0;
    String permissionText = decode(permissionValue);
    for (int index = 0; index < permissionText.length(); index++) {
      permissions |=
          switch (Character.toLowerCase(permissionText.charAt(index))) {
            case 'r' -> 1;
            case 'w' -> 2;
            case 'c' -> 4;
            default -> -1;
          };
      if (permissions < 0) {
        return Result.error(ErrorValue.E_INVARG);
      }
    }
    MooValue receiver = arguments.get(0);
    long targetOwner;
    int targetFlags;
    if (receiver instanceof ObjectValue object) {
      WorldObject target = world.object(object.value()).orElse(null);
      if (target == null) {
        return Result.error(ErrorValue.E_INVARG);
      }
      targetOwner = target.owner();
      targetFlags = target.flags();
    } else if (receiver instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
      if (target == null) {
        return Result.error(ErrorValue.E_INVARG);
      }
      targetOwner = target.owner();
      targetFlags = target.flags();
    } else {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && (actor.flags() & 4) != 0;
    boolean writable = targetOwner == programmer || wizard || (targetFlags & 32) != 0;
    if (!writable || (owner.value() != programmer && !wizard)) {
      return Result.error(ErrorValue.E_PERM);
    }
    String name = decode((StringValue) arguments.get(1));
    boolean added =
        receiver instanceof ObjectValue object
            ? world.addProperty(
                object.value(), name, arguments.get(2), owner.value(), permissions)
            : world.addProperty(
                (AnonymousObjectValue) receiver,
                name,
                arguments.get(2),
                owner.value(),
                permissions);
    return added
        ? Result.zero()
        : Result.error(ErrorValue.E_INVARG);
  }

  private static Result properties(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (!(arguments.getFirst() instanceof ObjectValue object)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && (actor.flags() & 4) != 0;
    if (target.owner() != programmer && !wizard && (target.flags() & 16) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    List<MooValue> names =
        target.properties().stream()
            .filter(WorldProperty::defined)
            .map(WorldProperty::name)
            .map(BuiltinCatalog::encode)
            .map(MooValue.class::cast)
            .toList();
    return Result.value(new ListValue(names));
  }

  private static Result isClearProperty(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (!(arguments.getFirst() instanceof ObjectValue object)) {
      return Result.error(ErrorValue.E_INVARG);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    String name = decode((StringValue) arguments.get(1));
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
      return Result.zero();
    }
    WorldProperty property = world.property(object.value(), name).orElse(null);
    if (property == null) {
      return Result.error(ErrorValue.E_PROPNF);
    }
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && (actor.flags() & 4) != 0;
    if (property.owner() != programmer && !wizard && (property.permissions() & 1) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    WorldProperty local =
        target.properties().stream()
            .filter(candidate -> candidate.name().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    return Result.value(new IntegerValue(local != null && local.clear() ? 1 : 0));
  }

  private static Result propertyInfo(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (!(arguments.getFirst() instanceof ObjectValue object)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    if (world.object(object.value()).isEmpty()) {
      return Result.error(ErrorValue.E_INVARG);
    }
    String name = decode((StringValue) arguments.get(1));
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
      return Result.error(ErrorValue.E_PROPNF);
    }
    WorldProperty property = world.property(object.value(), name).orElse(null);
    if (property == null) {
      return Result.error(ErrorValue.E_PROPNF);
    }
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && (actor.flags() & 4) != 0;
    if (property.owner() != programmer && !wizard && (property.permissions() & 1) == 0) {
      return Result.error(ErrorValue.E_PERM);
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
    return Result.value(
        new ListValue(List.of(new ObjectValue(property.owner()), encode(permissions))));
  }

  private static Result clearProperty(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (!(arguments.getFirst() instanceof ObjectValue object)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    String name = decode((StringValue) arguments.get(1));
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
      return Result.error(ErrorValue.E_PERM);
    }
    WorldProperty property = world.property(object.value(), name).orElse(null);
    if (property == null) {
      return Result.error(ErrorValue.E_PROPNF);
    }
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && (actor.flags() & 4) != 0;
    if (property.owner() != programmer && !wizard && (property.permissions() & 2) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    WorldProperty local =
        target.properties().stream()
            .filter(candidate -> candidate.name().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    if (local == null || local.defined()) {
      return Result.error(ErrorValue.E_INVARG);
    }
    return world.clearProperty(object.value(), name)
        ? Result.zero()
        : Result.error(ErrorValue.E_PROPNF);
  }

  private static Result deleteProperty(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (!(arguments.getFirst() instanceof ObjectValue object)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return Result.error(ErrorValue.E_INVARG);
    }
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && (actor.flags() & 4) != 0;
    if (target.owner() != programmer && !wizard && (target.flags() & 32) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }
    String name = decode((StringValue) arguments.get(1));
    boolean defined =
        target.properties().stream()
            .anyMatch(property -> property.defined() && property.name().equalsIgnoreCase(name));
    if (!defined) {
      return Result.error(ErrorValue.E_PROPNF);
    }
    return world.deleteProperty(object.value(), name)
        ? Result.zero()
        : Result.error(ErrorValue.E_PROPNF);
  }

  private static Result setPlayerFlag(List<MooValue> arguments, WorldTxn world) {
    ObjectValue object = (ObjectValue) arguments.get(0);
    IntegerValue enabled = (IntegerValue) arguments.get(1);
    return world.setPlayerFlag(object.value(), enabled.isTruthy())
        ? Result.zero()
        : Result.error(ErrorValue.E_INVARG);
  }

  private static Result setVerbCode(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    ListValue code = (ListValue) arguments.get(2);
    if (code.elements().stream().anyMatch(value -> !(value instanceof StringValue))) {
      return Result.error(ErrorValue.E_TYPE);
    }
    MooValue receiver = arguments.get(0);
    List<WorldVerb> targetVerbs;
    if (receiver instanceof ObjectValue object) {
      WorldObject target = world.object(object.value()).orElse(null);
      if (target == null) {
        return Result.error(ErrorValue.E_INVARG);
      }
      targetVerbs = target.verbs();
    } else if (receiver instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
      if (target == null) {
        return Result.error(ErrorValue.E_INVARG);
      }
      targetVerbs = target.verbs();
    } else {
      return Result.error(ErrorValue.E_TYPE);
    }
    MooValue descriptor = arguments.get(1);
    if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return Result.error(ErrorValue.E_INVARG);
    }
    WorldVerb verb;
    int verbIndex;
    if (descriptor instanceof IntegerValue integer) {
      long index = integer.value() - 1;
      if (index >= targetVerbs.size()) {
        return Result.error(ErrorValue.E_VERBNF);
      }
      verbIndex = (int) index;
      verb = targetVerbs.get(verbIndex);
    } else {
      WorldVerb candidate =
          receiver instanceof ObjectValue object
              ? world.verb(object.value(), decode((StringValue) descriptor), false).orElse(null)
              : world
                  .verb(
                      (AnonymousObjectValue) receiver,
                      decode((StringValue) descriptor),
                      false)
                  .orElse(null);
      if (candidate == null || !targetVerbs.contains(candidate)) {
        return Result.error(ErrorValue.E_VERBNF);
      }
      verb = candidate;
      verbIndex = targetVerbs.indexOf(verb);
    }

    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && (actor.flags() & 4) != 0;
    boolean programmerFlag = actor != null && (actor.flags() & 2) != 0;
    if ((!programmerFlag && !wizard)
        || (verb.owner() != programmer && !wizard && (verb.permissions() & 2) == 0)) {
      return Result.error(ErrorValue.E_PERM);
    }

    String source =
        code.elements().stream()
            .map(StringValue.class::cast)
            .map(BuiltinCatalog::decode)
            .collect(java.util.stream.Collectors.joining("\n"));
    try {
      new MooCompiler().compile(source);
    } catch (IllegalArgumentException error) {
      String diagnostic = error.getMessage();
      if (diagnostic == null) {
        diagnostic = error.getClass().getSimpleName();
      }
      return Result.value(new ListValue(List.of(encode(diagnostic))));
    }
    boolean updated =
        receiver instanceof ObjectValue object
            ? world.setVerbCode(object.value(), verbIndex, source)
            : world.setVerbCode((AnonymousObjectValue) receiver, verbIndex, source);
    if (!updated) {
      return Result.error(ErrorValue.E_VERBNF);
    }
    return Result.value(new ListValue(List.of()));
  }

  private static Result verbCode(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    if (!(arguments.get(0) instanceof ObjectValue object)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    MooValue descriptor = arguments.get(1);
    if (!(descriptor instanceof StringValue) && !(descriptor instanceof IntegerValue)) {
      return Result.error(ErrorValue.E_TYPE);
    }
    if (descriptor instanceof IntegerValue integer && integer.value() <= 0) {
      return Result.error(ErrorValue.E_INVARG);
    }
    WorldObject target = world.object(object.value()).orElse(null);
    if (target == null) {
      return Result.error(ErrorValue.E_INVARG);
    }

    WorldVerb verb;
    if (descriptor instanceof IntegerValue integer) {
      long index = integer.value() - 1;
      if (index >= target.verbs().size()) {
        return Result.error(ErrorValue.E_VERBNF);
      }
      verb = target.verbs().get((int) index);
    } else {
      WorldVerb candidate =
          world.verb(object.value(), decode((StringValue) descriptor), false).orElse(null);
      if (candidate == null || !target.verbs().contains(candidate)) {
        return Result.error(ErrorValue.E_VERBNF);
      }
      verb = candidate;
    }

    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && (actor.flags() & 4) != 0;
    if (verb.owner() != programmer && !wizard && (verb.permissions() & 1) == 0) {
      return Result.error(ErrorValue.E_PERM);
    }

    boolean indent = arguments.size() < 4 || arguments.get(3).isTruthy();
    List<MooValue> lines =
        MooUnparser.unparse(MooParser.parse(verb.programSource())).lines()
            .map(line -> indent ? line : line.stripLeading())
            .map(BuiltinCatalog::encode)
            .map(MooValue.class::cast)
            .toList();
    return Result.value(new ListValue(lines));
  }

  private static Result move(List<MooValue> arguments, WorldTxn world, long programmer) {
    ObjectValue object = (ObjectValue) arguments.get(0);
    ObjectValue destination = (ObjectValue) arguments.get(1);
    if (world.object(object.value()).isEmpty() || world.object(destination.value()).isEmpty()) {
      return Result.error(ErrorValue.E_INVARG);
    }
    WorldObject programmerObject = world.object(programmer).orElse(null);
    if (programmerObject != null && (programmerObject.flags() & 4) != 0) {
      return Result.move(object.value(), destination.value());
    }
    return world.move(object.value(), destination.value())
        ? Result.zero()
        : Result.error(ErrorValue.E_INVARG);
  }

  private static Result recycle(
      List<MooValue> arguments, WorldTxn world, long programmer) {
    WorldObject actor = world.object(programmer).orElse(null);
    boolean wizard = actor != null && (actor.flags() & 4) != 0;
    MooValue receiver = arguments.getFirst();
    if (receiver instanceof AnonymousObjectValue anonymous) {
      WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
      if (target == null) {
        return Result.error(ErrorValue.E_INVARG);
      }
      if (target.owner() != programmer && !wizard) {
        return Result.error(ErrorValue.E_PERM);
      }
      return world.removeAnonymousObject(anonymous)
          ? Result.zero()
          : Result.error(ErrorValue.E_INVARG);
    }
    if (receiver instanceof ObjectValue object) {
      WorldObject target = world.object(object.value()).orElse(null);
      if (target == null) {
        return Result.error(ErrorValue.E_INVARG);
      }
      if (target.owner() != programmer && !wizard) {
        return Result.error(ErrorValue.E_PERM);
      }
      return Result.recycle(object.value());
    }
    return Result.error(ErrorValue.E_TYPE);
  }

  private static Result switchPlayer(List<MooValue> arguments) {
    ObjectValue player = (ObjectValue) arguments.get(1);
    return Result.switchPlayer(player.value());
  }

  private static Result setTaskPerms(List<MooValue> arguments) {
    ObjectValue programmer = (ObjectValue) arguments.getFirst();
    return Result.programmer(programmer.value());
  }

  private static Result notifyLine(List<MooValue> arguments) {
    StringValue line = (StringValue) arguments.get(1);
    return Result.output(decode(line));
  }

  private static Result toStringValue(List<MooValue> arguments) {
    StringBuilder text = new StringBuilder();
    for (MooValue argument : arguments) {
      text.append(
          switch (argument) {
            case StringValue string -> decode(string);
            case IntegerValue integer -> Long.toString(integer.value());
            case BooleanValue bool -> bool.toLiteral();
            case FloatValue floating -> floating.toLiteral();
            case ObjectValue object -> object.toLiteral();
            case AnonymousObjectValue anonymous -> anonymous.toString();
            case WaifValue waif -> waif.toString();
            case ErrorValue error -> errorDescription(error);
            case ListValue ignored -> "{list}";
            case MapValue ignored -> "[map]";
          });
    }
    return Result.value(encode(text.toString()));
  }

  private static String errorDescription(ErrorValue error) {
    return switch (error) {
      case E_NONE -> "No error";
      case E_TYPE -> "Type mismatch";
      case E_DIV -> "Division by zero";
      case E_PERM -> "Permission denied";
      case E_PROPNF -> "Property not found";
      case E_VERBNF -> "Verb not found";
      case E_VARNF -> "Variable not found";
      case E_INVIND -> "Invalid indirection";
      case E_RECMOVE -> "Recursive move";
      case E_MAXREC -> "Too many verb calls";
      case E_RANGE -> "Range error";
      case E_ARGS -> "Incorrect number of arguments";
      case E_NACC -> "Move refused by destination";
      case E_INVARG -> "Invalid argument";
      case E_QUOTA -> "Resource limit exceeded";
      case E_FLOAT -> "Floating-point arithmetic error";
      case E_FILE -> "File error";
      case E_EXEC -> "Exec error";
      case E_INTRPT -> "Interrupted";
    };
  }

  private static Result toFloat(List<MooValue> arguments) {
    MooValue argument = arguments.getFirst();
    if (argument instanceof FloatValue floating) {
      return Result.value(floating);
    }
    if (argument instanceof IntegerValue integer) {
      return Result.value(new FloatValue(integer.value()));
    }
    if (argument instanceof ObjectValue object) {
      return Result.value(new FloatValue(object.value()));
    }
    if (argument instanceof ErrorValue error) {
      return Result.value(new FloatValue(error.code()));
    }
    if (argument instanceof StringValue string) {
      try {
        double converted = Double.parseDouble(decode(string).strip());
        return Double.isFinite(converted)
            ? Result.value(new FloatValue(converted))
            : Result.error(ErrorValue.E_INVARG);
      } catch (NumberFormatException error) {
        return Result.error(ErrorValue.E_INVARG);
      }
    }
    return Result.error(ErrorValue.E_TYPE);
  }

  private static Result toInteger(List<MooValue> arguments) {
    MooValue argument = arguments.getFirst();
    if (argument instanceof IntegerValue integer) {
      return Result.value(integer);
    }
    if (argument instanceof BooleanValue bool) {
      return Result.value(new IntegerValue(bool.value() ? 1 : 0));
    }
    if (argument instanceof FloatValue floating) {
      return Double.isFinite(floating.value())
          ? Result.value(new IntegerValue((long) floating.value()))
          : Result.error(ErrorValue.E_FLOAT);
    }
    if (argument instanceof ObjectValue object) {
      return Result.value(new IntegerValue(object.value()));
    }
    if (argument instanceof ErrorValue error) {
      return Result.value(new IntegerValue(error.code()));
    }
    if (argument instanceof StringValue string) {
      String text = decode(string).strip();
      try {
        return Result.value(new IntegerValue(Long.parseLong(text)));
      } catch (NumberFormatException integerError) {
        try {
          double converted = Double.parseDouble(text);
          return Double.isFinite(converted)
              ? Result.value(new IntegerValue((long) converted))
              : Result.zero();
        } catch (NumberFormatException floatingError) {
          return Result.zero();
        }
      }
    }
    return Result.error(ErrorValue.E_TYPE);
  }

  private static Result toLiteral(List<MooValue> arguments) {
    return Result.value(encode(arguments.getFirst().toLiteral()));
  }

  private static Result toObject(List<MooValue> arguments) {
    MooValue argument = arguments.getFirst();
    if (argument instanceof ObjectValue object) {
      return Result.value(object);
    }
    if (argument instanceof IntegerValue integer) {
      return Result.value(new ObjectValue(integer.value()));
    }
    if (argument instanceof BooleanValue bool) {
      return Result.value(new ObjectValue(bool.value() ? 1 : 0));
    }
    if (argument instanceof FloatValue floating) {
      return Double.isFinite(floating.value())
          ? Result.value(new ObjectValue((long) floating.value()))
          : Result.error(ErrorValue.E_FLOAT);
    }
    if (argument instanceof ErrorValue error) {
      return Result.value(new ObjectValue(error.code()));
    }
    if (argument instanceof StringValue string) {
      String text = decode(string).strip();
      if (text.startsWith("#")) {
        text = text.substring(1);
      }
      try {
        return Result.value(new ObjectValue(Long.parseLong(text)));
      } catch (NumberFormatException error) {
        return Result.value(new ObjectValue(0));
      }
    }
    return Result.error(ErrorValue.E_TYPE);
  }

  private static Result equalValues(List<MooValue> arguments) {
    return Result.value(
        new IntegerValue(exactlyEqual(arguments.get(0), arguments.get(1)) ? 1 : 0));
  }

  private static boolean exactlyEqual(MooValue left, MooValue right) {
    if (left instanceof StringValue leftString && right instanceof StringValue rightString) {
      return Arrays.equals(leftString.bytes(), rightString.bytes());
    }
    if (left instanceof ListValue leftList && right instanceof ListValue rightList) {
      if (leftList.size() != rightList.size()) {
        return false;
      }
      for (int index = 0; index < leftList.size(); index++) {
        if (!exactlyEqual(leftList.elements().get(index), rightList.elements().get(index))) {
          return false;
        }
      }
      return true;
    }
    if (left instanceof MapValue leftMap && right instanceof MapValue rightMap) {
      if (leftMap.size() != rightMap.size()) {
        return false;
      }
      for (Map.Entry<MooValue, MooValue> leftEntry : leftMap.entries().entrySet()) {
        boolean matched = false;
        for (Map.Entry<MooValue, MooValue> rightEntry : rightMap.entries().entrySet()) {
          if (exactlyEqual(leftEntry.getKey(), rightEntry.getKey())) {
            if (!exactlyEqual(leftEntry.getValue(), rightEntry.getValue())) {
              return false;
            }
            matched = true;
            break;
          }
        }
        if (!matched) {
          return false;
        }
      }
      return true;
    }
    return left.equals(right);
  }

  private static Result dynamicEval(List<MooValue> arguments) {
    StringValue source = (StringValue) arguments.getFirst();
    return Result.dynamicEval(decode(source));
  }

  private static Result typeOf(List<MooValue> arguments) {
    return Result.value(new IntegerValue(arguments.getFirst().type().code()));
  }

  private static String decode(StringValue value) {
    return new String(value.bytes(), StandardCharsets.ISO_8859_1);
  }

  private static StringValue encode(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  /** Concrete host capability required by the MOO listener builtins. */
  public interface ListenerControl {
    /** Binds and starts one listener, returning its integer descriptor. */
    int listen(long handler, int port, boolean printMessages) throws IOException;

    /** Closes one dynamic listener selected by its integer descriptor. */
    boolean unlisten(int port);

    /** Writes ordered lines to one accepted connection selected by runtime ID. */
    void writeConnection(long connectionId, List<String> lines);

    /** Writes final lines and closes one accepted connection selected by runtime ID. */
    void bootConnection(long connectionId, List<String> lines);

    /** Selects delimiter-free binary reads for one accepted connection. */
    void setConnectionBinary(long connectionId, boolean binary);

    /** Stops the production server after its committed shutdown checkpoint is published. */
    void shutdown();
  }

  /** One validated mutation of the closed connection-option surface. */
  public record ConnectionOptionRequest(long target, ConnectionOption option, MooValue value) {}

  /** One validated line to inject into a live connection's input stream. */
  public record ForcedInputRequest(long target, String line) {}

  /** The connection options authorized by the held-input slice. */
  public enum ConnectionOption {
    HOLD_INPUT,
    FLUSH_COMMAND,
    DISABLE_OOB,
    BINARY
  }

  /** One explicit builtin value, MOO error, dynamic call, or staged effect. */
  public record Result(
      Optional<MooValue> value,
      Optional<ErrorValue> error,
      Optional<String> dynamicSource,
      Optional<String> output,
      OptionalLong switchedPlayer,
      OptionalLong programmer,
      OptionalLong recycleTarget,
      OptionalDouble delaySeconds,
      Optional<CompletableFuture<MooValue>> hostResult,
      Optional<ConnectionOptionRequest> connectionOptionRequest,
      OptionalLong bootPlayerTarget,
      Optional<ForcedInputRequest> forcedInputRequest,
      Optional<MooValue> taskLocal,
      OptionalLong moveObject,
      OptionalLong moveDestination,
      Optional<ListValue> errorDetails,
      Optional<CheckpointRequest> checkpointRequest) {
    private Result(
        Optional<MooValue> value,
        Optional<ErrorValue> error,
        Optional<String> dynamicSource,
        Optional<String> output,
        OptionalLong switchedPlayer,
        OptionalLong programmer,
        OptionalLong recycleTarget,
        OptionalDouble delaySeconds,
        Optional<CompletableFuture<MooValue>> hostResult,
        Optional<ConnectionOptionRequest> connectionOptionRequest,
        OptionalLong bootPlayerTarget,
        Optional<ForcedInputRequest> forcedInputRequest) {
      this(
          value,
          error,
          dynamicSource,
          output,
          switchedPlayer,
          programmer,
          recycleTarget,
          delaySeconds,
          hostResult,
          connectionOptionRequest,
          bootPlayerTarget,
          forcedInputRequest,
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          Optional.empty(),
          Optional.empty());
    }

    public static Result value(MooValue value) {
      return new Result(
          Optional.of(value),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty());
    }

    static Result zero() {
      return value(new IntegerValue(0));
    }

    static Result checkpoint() {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          Optional.empty(),
          Optional.of(new CheckpointRequest(false)));
    }

    static Result shutdown() {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          Optional.empty(),
          Optional.of(new CheckpointRequest(true)));
    }

    static Result suspend(double seconds) {
      return new Result(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.of(seconds),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty());
    }

    public static Result error(ErrorValue error) {
      return new Result(
          Optional.empty(),
          Optional.of(error),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty());
    }

    static Result raised(ErrorValue error, StringValue message, MooValue value) {
      return new Result(
          Optional.empty(),
          Optional.of(error),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          Optional.of(new ListValue(List.of(message, value, new ListValue(List.of())))),
          Optional.empty());
    }

    static Result dynamicEval(String source) {
      return new Result(
          Optional.empty(),
          Optional.empty(),
          Optional.of(source),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty());
    }

    static Result output(String line) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.of(line),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty());
    }

    static Result switchPlayer(long player) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.of(player),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty());
    }

    static Result programmer(long programmer) {
      return new Result(
          Optional.of(new IntegerValue(0)),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.of(programmer),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty());
    }

    static Result move(long object, long destination) {
      return new Result(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.of(object),
          OptionalLong.of(destination),
          Optional.empty(),
          Optional.empty());
    }

    static Result recycle(long object) {
      return new Result(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          OptionalLong.empty(),
          OptionalLong.of(object),
          OptionalDouble.empty(),
          Optional.empty(),
          Optional.empty(),
          OptionalLong.empty(),
          Optional.empty());
    }
  }
}
