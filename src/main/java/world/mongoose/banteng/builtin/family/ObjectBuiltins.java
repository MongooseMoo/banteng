package world.mongoose.banteng.builtin.family;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import world.mongoose.banteng.builtin.ArgType;
import world.mongoose.banteng.builtin.BuiltinPermissionRule;
import world.mongoose.banteng.builtin.BuiltinRegistrar;

import static world.mongoose.banteng.builtin.BuiltinResult.mutationResult;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.builtin.BuiltinSpec;
import world.mongoose.banteng.builtin.CallShape;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.StringValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import static world.mongoose.banteng.value.ValueSemantics.matchesAt;

import world.mongoose.banteng.value.ValueSemantics;
import world.mongoose.banteng.world.ObjectFlags;
import world.mongoose.banteng.world.SuspendedActivation;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldResult;
import world.mongoose.banteng.world.WorldTxn;

/** Owns builtin registrations and implementations for this family. */
public final class ObjectBuiltins {
    private static final Set<ArgType> ANY = Set.of(ArgType.ANY);
    private static final Set<ArgType> INTEGER = Set.of(ArgType.INTEGER);
    private static final Set<ArgType> STRING = Set.of(ArgType.STRING);
    private static final Set<ArgType> OBJECT = Set.of(ArgType.OBJECT);

    private ObjectBuiltins() {
    }

    public static void registerLifecycleAndTopology(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("create", List.of(
                new CallShape(
                        List.of(ANY),
                        List.of(ANY, ANY, ANY),
                        Optional.empty()))).implementation(call -> create(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("recreate", List.of(new CallShape(List.of(OBJECT, OBJECT), List.of(OBJECT), Optional.empty()))).implementation(call -> recreate(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("parent", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call -> parent(call.arguments(), call.world()));
        registrar.builtin("parents", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call -> parents(call.arguments(), call.world()));
        registrar.builtin("ancestors", List.of(new CallShape(List.of(ANY), List.of(ANY), Optional.empty()))).implementation(call -> ancestors(call.arguments(), call.world()));
        registrar.builtin("isa", List.of(new CallShape(List.of(ANY, ANY), List.of(INTEGER), Optional.empty()))).implementation(call -> isa(call.arguments(), call.world()));
        registrar.builtin("children", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call -> children(call.arguments(), call.world()));
        registrar.builtin("chparent", List.of(new CallShape(List.of(ANY, OBJECT), List.of(), Optional.empty()))).implementation(call -> changeParents(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("chparents", List.of(new CallShape(List.of(ANY, Set.of(ArgType.LIST)), List.of(), Optional.empty()))).implementation(call -> changeParents(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("is_player", List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty()))).implementation(call -> isPlayer(call.arguments(), call.world()));
        registrar.builtin("valid", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call -> valid(call.arguments(), call.world()));
        registrar.builtin("max_object", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).implementation(call ->
                BuiltinResult.value(new ObjectValue(call.world().maximumObjectId())));
        registrar.builtin("locate_by_name", List.of(new CallShape(List.of(STRING), List.of(INTEGER), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> locateByName(call.arguments(), call.world()));
        registrar.builtin("locations", List.of(new CallShape(List.of(OBJECT), List.of(OBJECT, INTEGER), Optional.empty()))).implementation(call -> locations(call.arguments(), call.world()));
        registrar.builtin("recycled_objects", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).implementation(call -> recycledObjects(call.world()));
        registrar.builtin("next_recycled_object", List.of(new CallShape(List.of(), List.of(OBJECT), Optional.empty()))).implementation(call -> nextRecycledObject(call.arguments(), call.world()));
        registrar.builtin("owned_objects", List.of(new CallShape(List.of(OBJECT), List.of(), Optional.empty()))).implementation(call -> ownedObjects(call.arguments(), call.world()));
        registrar.builtin("set_player_flag", List.of(new CallShape(List.of(OBJECT, ANY), List.of(), Optional.empty()))).implementation(call -> setPlayerFlag(call.arguments(), call.world()));
    }

    public static void registerMovementAndWaifs(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("move", List.of(new CallShape(List.of(OBJECT, OBJECT), List.of(INTEGER), Optional.empty()))).implementation(call -> move(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("recycle", List.of(new CallShape(List.of(ANY), List.of(), Optional.empty()))).implementation(call -> recycle(call.arguments(), call.world(), call.programmer()));
        registrar.builtin("new_waif", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).implementation(call -> {
            if (call.receiver() instanceof AnonymousObjectValue anonymous
                    && call.world().anonymousObject(anonymous).isPresent()) {
                return BuiltinResult.error(ErrorValue.E_INVARG);
            }
            if (!(call.receiver() instanceof ObjectValue classObject)
                    || call.world().object(classObject.value()).isEmpty()) {
                return BuiltinResult.error(ErrorValue.E_INVIND);
            }
            return BuiltinResult.value(call.world().createWaif(classObject.value(), call.programmer()));
        });
        registrar.builtin("waif_stats", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).implementation(call -> waifStats(call.world()));
    }

    public static void registerResetMaximum(List<BuiltinSpec> entries) {
        BuiltinRegistrar registrar = new BuiltinRegistrar(entries);
        registrar.builtin("reset_max_object", List.of(new CallShape(List.of(), List.of(), Optional.empty()))).permission(BuiltinPermissionRule.WIZARD_ONLY).implementation(call -> {
            call.world().resetLastUsedObjectId();
            return BuiltinResult.value(new IntegerValue(0));
        });
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
        boolean wizard = world.isWizard(programmer);
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
                    ? new BuiltinResult.Initialize(created, initializeArguments, Optional.empty())
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
                ? new BuiltinResult.Initialize(identity, initializeArguments,
                    Optional.of(new SuspendedActivation.CreateContinuation(false, identity.value())))
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
        if ((owner != programmer && !world.isWizard(programmer))
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
                ? new BuiltinResult.Initialize(identity, new ListValue(List.of()),
                    Optional.of(new SuspendedActivation.CreateContinuation(true, identity.value())))
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

    private static BuiltinResult isa(List<MooValue> arguments, WorldTxn world) {
        MooValue subject = arguments.get(0);
        if (subject instanceof WaifValue waif) {
            subject = waif.classObject();
        }
        if (!(subject instanceof ObjectValue || subject instanceof AnonymousObjectValue)) {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        MooValue requested = arguments.get(1);
        List<MooValue> candidates;
        if (requested instanceof ObjectValue || requested instanceof AnonymousObjectValue) {
            candidates = List.of(requested);
        } else if (requested instanceof ListValue list) {
            if (list.elements().stream().anyMatch(candidate -> !(candidate instanceof ObjectValue))) {
                return BuiltinResult.error(ErrorValue.E_TYPE);
            }
            candidates = list.elements();
        } else {
            return BuiltinResult.error(ErrorValue.E_TYPE);
        }
        MooValue matched = null;
        for (MooValue candidate : candidates) {
            if (isA(subject, candidate, world)) {
                matched = candidate;
                break;
            }
        }
        boolean returnParent = arguments.size() == 3 && arguments.get(2).isTruthy();
        return BuiltinResult.value(
                returnParent
                        ? matched == null ? new ObjectValue(-1) : matched
                        : new IntegerValue(matched == null ? 0 : 1));
    }

    private static boolean isA(MooValue subject, MooValue candidate, WorldTxn world) {
        if (subject instanceof ObjectValue object) {
            return candidate instanceof ObjectValue parent
                    && world.ancestry(object.value()).contains(parent.value());
        }
        AnonymousObjectValue anonymous = (AnonymousObjectValue) subject;
        WorldAnonymousObject target = world.anonymousObject(anonymous).orElse(null);
        if (target == null) {
            return false;
        }
        if (candidate instanceof AnonymousObjectValue parent) {
            return anonymous == parent;
        }
        long parentId = ((ObjectValue) candidate).value();
        return target.parents().stream()
                .anyMatch(parent -> world.ancestry(parent).contains(parentId));
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
        if ((!world.isWizard(programmer) && owner != programmer)
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

    private static boolean parentsAllowed(
            List<Long> parents, WorldTxn world, long programmer, int permissionFlag) {
        if (world.isWizard(programmer)) {
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
            if (result.stream().noneMatch(value -> ValueSemantics.STANDARD.equal(value, located, false))) {
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

    private static BuiltinResult setPlayerFlag(List<MooValue> arguments, WorldTxn world) {
        ObjectValue object = (ObjectValue) arguments.get(0);
        return mutationResult(
                world.setPlayerFlag(object.value(), arguments.get(1).isTruthy()), new IntegerValue(0));
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
        boolean wizard = world.isWizard(programmer);
        if (!wizard && moving.owner() != programmer) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        if (position > 0 && destination.value() != -1 && destination.value() != moving.location()
                && !wizard && world.object(destination.value()).orElseThrow().owner() != programmer) {
            return BuiltinResult.error(ErrorValue.E_PERM);
        }
        return new BuiltinResult.Move(object.value(), destination.value(), position);
    }

    private static BuiltinResult recycle(
            List<MooValue> arguments, WorldTxn world, long programmer) {
        boolean wizard = world.isWizard(programmer);
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

    private record ParentArgument(List<Long> ids, Optional<ErrorValue> error) {
    }
}
