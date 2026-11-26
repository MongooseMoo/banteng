package world.mongoose.banteng.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;
import world.mongoose.banteng.bytecode.MooCompiler;
import world.mongoose.banteng.server.ConnectionRegistry;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.ObjectValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import world.mongoose.banteng.vm.VmSnapshot;
import world.mongoose.banteng.vm.VmState;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldSnapshot;
import world.mongoose.banteng.world.WorldTxn;
import world.mongoose.banteng.world.WorldWaif;

final class FinalizationCollectorTest {
  @Test
  void checksNewBodiesEvenWhenPermanentReachabilityCanBeReused() {
    WaifValue retained = waif();
    WorldObject root = new WorldObject(0, "root", 0, 0, -1,
        List.of(), List.of(), List.of(), List.of(), List.of(property(retained, true)));
    WorldTxn world = new WorldTxn(List.of(), List.of(root), Map.of(),
        Map.of(retained, new WorldWaif(List.of())), List.of(), 0);
    try (MooRuntime runtime = runtime(world); WorldTxn transaction = world.begin()) {
      var collector = new FinalizationCollector(runtime);
      var context = context(transaction);
      collector.queueUnreachableAnonymousObjects(context, List.of());
      assertEquals(List.of(), transaction.pendingFinalization());

      WaifValue added = transaction.createWaif(0, 0);
      collector.queueUnreachableAnonymousObjects(context, List.of());
      assertEquals(List.of(added), transaction.pendingFinalization());
    }
  }

  @Test
  void propertyBodiesRetainOnlyCollectibleRootsWithoutChangingTheirSlots() {
    AnonymousObjectValue retained = new AnonymousObjectValue();
    AnonymousObjectValue discarded = new AnonymousObjectValue();
    WaifValue waif = waif();
    List<WorldProperty> slots = new ArrayList<>();
    for (int index = 0; index < 1000; index++) {
      slots.add(property(new IntegerValue(index), true));
    }
    WorldProperty nested = property(new MapValue(Map.of(waif, new ListValue(List.of(retained)))), false);
    slots.add(nested);
    WorldObject root = new WorldObject(0, "root", 0, 0, -1,
        List.of(), List.of(), List.of(), List.of(), slots);
    WorldAnonymousObject body = new WorldAnonymousObject("", 0, 0,
        List.of(), List.of(), root.properties());
    WorldWaif waifBody = new WorldWaif(body.properties());
    assertEquals(slots, root.properties());
    assertEquals(slots.hashCode(), root.properties().hashCode());
    assertEquals(List.of(nested), root.collectibleProperties());
    assertSame(root.properties(), body.properties());
    assertSame(body.properties(), waifBody.properties());
    assertSame(root.collectibleProperties(), waifBody.collectibleProperties());
    slots.clear();
    assertEquals(1001, root.properties().size());
    assertThrows(UnsupportedOperationException.class,
        () -> root.properties().set(0, nested));
    assertThrows(UnsupportedOperationException.class,
        () -> root.collectibleProperties().clear());
    List<WorldProperty> replaced = new ArrayList<>(root.properties());
    replaced.set(1000, property(new IntegerValue(0), false));
    assertTrue(new WorldWaif(replaced).collectibleProperties().isEmpty());
    WorldTxn world = new WorldTxn(List.of(), List.of(root),
        Map.of(retained, anonymous(), discarded, anonymous()),
        Map.of(waif, new WorldWaif(List.of())), List.of(), 0);
    try (MooRuntime runtime = runtime(world); WorldTxn transaction = world.begin()) {
      new FinalizationCollector(runtime).queueUnreachableAnonymousObjects(context(transaction), List.of());
      assertEquals(List.of(discarded), transaction.pendingFinalization());
    }
  }

  @Test
  void sharedCollectionsRetainDistinctReachabilityAndFinalizationRules() {
    AnonymousObjectValue root = new AnonymousObjectValue();
    AnonymousObjectValue local = new AnonymousObjectValue();
    WaifValue waif = waif();
    WorldSnapshot world = new WorldSnapshot(0, List.of(), Map.of(),
        Map.of(root, anonymous(property(waif, false), property(local, true)),
            local, anonymous()), Map.of(waif, new WorldWaif(List.of(property(root, true)))));
    MooValue shared = root;
    // Clear inherited properties and VM locals can alias the same immutable graph.
    for (int depth = 0; depth < 12; depth++) {
      shared = new ListValue(List.of(shared, shared));
      shared = new MapValue(Map.of(new IntegerValue(0), shared, new IntegerValue(1), shared));
    }
    Set<MooValue> traversed = identities();
    Set<AnonymousObjectValue> reachable = new LinkedHashSet<>();
    Set<WaifValue> waifs = new LinkedHashSet<>();
    FinalizationCollector.markReachableAnonymous(shared, world, reachable, waifs, traversed);
    assertEquals(Set.of(root, local), reachable);
    assertEquals(Set.of(waif), waifs);
    assertEquals(27, traversed.size());
    FinalizationCollector.markReachableAnonymous(shared, world, reachable, waifs, traversed);
    assertEquals(27, traversed.size());

    Set<AnonymousObjectValue> finalizing = new LinkedHashSet<>();
    FinalizationCollector.markFinalizingAnonymous(shared, world, finalizing,
        new LinkedHashSet<>(), identities());
    assertEquals(Set.of(root), finalizing);

    Set<AnonymousObjectValue> references = new LinkedHashSet<>();
    FinalizationCollector.collectAnonymousReferences(shared, world, references,
        new LinkedHashSet<>(), identities());
    assertEquals(Set.of(root), references);
    Set<WaifValue> waifReferences = new LinkedHashSet<>();
    FinalizationCollector.collectWaifReferences(shared, world, waifReferences,
        new LinkedHashSet<>(), new LinkedHashSet<>(), identities());
    assertEquals(Set.of(waif), waifReferences);
  }

  @Test
  void skipsPureDataContainersWhileRetainingNestedIdentityRoots() {
    AnonymousObjectValue anonymous = new AnonymousObjectValue();
    Map<MooValue, MooValue> data = new LinkedHashMap<>();
    for (int index = 0; index < 1000; index++) {
      data.put(new IntegerValue(index), new ListValue(List.of(new IntegerValue(index))));
    }
    MapValue pure = new MapValue(data);
    ListValue nested = new ListValue(List.of(anonymous));
    ListValue roots = new ListValue(List.of(pure, pure, nested));
    WorldSnapshot snapshot = new WorldSnapshot(0, List.of(), Map.of(),
        Map.of(anonymous, anonymous()));
    Set<MooValue> traversed = identities();
    Set<AnonymousObjectValue> reachable = new LinkedHashSet<>();
    FinalizationCollector.markReachableAnonymous(roots, snapshot, reachable,
        new LinkedHashSet<>(), traversed);
    assertEquals(Set.of(anonymous), reachable);
    assertEquals(3, traversed.size());
    assertTrue(traversed.contains(roots));
    assertTrue(traversed.contains(nested));
    assertTrue(traversed.contains(anonymous));
  }

  @Test
  void preservesStableComponentRootsAndBatchesNoHookRemoval() {
    AnonymousObjectValue tail = new AnonymousObjectValue();
    AnonymousObjectValue first = new AnonymousObjectValue();
    AnonymousObjectValue second = new AnonymousObjectValue();
    AnonymousObjectValue cycle = new AnonymousObjectValue();
    AnonymousObjectValue cycleTail = new AnonymousObjectValue();
    AnonymousObjectValue independent = new AnonymousObjectValue();
    Map<AnonymousObjectValue, WorldAnonymousObject> anonymous = new LinkedHashMap<>();
    anonymous.put(tail, anonymous());
    anonymous.put(first, anonymous(property(tail, false)));
    anonymous.put(second, anonymous(property(tail, false)));
    anonymous.put(cycle, anonymous(property(cycleTail, false)));
    anonymous.put(cycleTail, anonymous(property(cycle, false)));
    anonymous.put(independent, anonymous());
    WaifValue waifTail = waif();
    WaifValue waifFirst = waif();
    WaifValue waifSecond = waif();
    WaifValue waifCycle = waif();
    WaifValue waifCycleTail = waif();
    WaifValue waifIndependent = waif();
    Map<WaifValue, WorldWaif> waifs = new LinkedHashMap<>();
    waifs.put(waifTail, new WorldWaif(List.of()));
    waifs.put(waifFirst, new WorldWaif(List.of(property(waifTail, true))));
    waifs.put(waifSecond, new WorldWaif(List.of(property(waifTail, true))));
    waifs.put(waifCycle, new WorldWaif(List.of(property(waifCycleTail, true))));
    waifs.put(waifCycleTail, new WorldWaif(List.of(property(waifCycle, true))));
    waifs.put(waifIndependent, new WorldWaif(List.of()));
    WorldTxn world = world(anonymous, waifs);
    try (MooRuntime runtime = runtime(world); WorldTxn transaction = world.begin()) {
      var context = context(transaction);
      FinalizationCollector collector = new FinalizationCollector(runtime);
      collector.queueUnreachableAnonymousObjects(context, List.of());
      assertEquals(List.of(first, second, independent, cycle,
          waifFirst, waifSecond, waifIndependent, waifCycle), transaction.pendingFinalization());
      collector.finalizePendingObjects(context);
      assertTrue(transaction.snapshot().anonymousObjects().isEmpty());
      assertTrue(transaction.snapshot().waifs().isEmpty());
      assertTrue(transaction.pendingFinalization().isEmpty());
      assertTrue(context.spawnedSteps.isEmpty());
    }
  }

  @Test
  void evaluatedHandlerSelectorIsATaskRoot() {
    AnonymousObjectValue retained = new AnonymousObjectValue();
    AnonymousObjectValue discarded = new AnonymousObjectValue();
    WorldTxn world = world(Map.of(retained, anonymous(), discarded, anonymous()), Map.of());
    var program = new MooCompiler().compile("try return 0; except (1) return 0; endtry");
    var specification = program.instructions().stream()
        .flatMap(instruction -> instruction.handler().stream())
        .filter(handler -> handler.dynamicErrors()).findFirst().orElseThrow();
    var handler = new VmSnapshot.HandlerState(specification, 0, VmSnapshot.HandlerPhase.TRY,
        Optional.of(new ListValue(List.of(retained))));
    var frame = new VmSnapshot.Frame(program, List.of(), List.of(), Map.of(), List.of(handler),
        List.of(), Map.of(), VmSnapshot.ReturnMode.ROOT, new ObjectValue(-1), new ObjectValue(-1),
        "", "", Optional.empty(), OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(),
        OptionalLong.empty(), 0, false, 0);
    var task = new VmSnapshot(Map.of(), 0, new ObjectValue(-1), "", "", List.of(frame),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        VmState.Outcome.RUNNING, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
        OptionalDouble.empty(), false, Optional.empty(), new IntegerValue(0), 1000, 0, 1000, 50, -1);
    try (MooRuntime runtime = runtime(world); WorldTxn transaction = world.begin()) {
      new FinalizationCollector(runtime).queueUnreachableAnonymousObjects(context(transaction), List.of(task));
      assertEquals(List.of(discarded), transaction.pendingFinalization());
    }
  }

  @Test
  void batchRemovalRetainsExactAnonymousConflictsAndDisjointWrites() {
    AnonymousObjectValue first = new AnonymousObjectValue();
    AnonymousObjectValue second = new AnonymousObjectValue();
    AnonymousObjectValue survivor = new AnonymousObjectValue();
    WaifValue firstWaif = waif();
    WaifValue secondWaif = waif();
    WaifValue survivingWaif = waif();
    WorldTxn world = world(Map.of(first, anonymous(), second, anonymous(), survivor, anonymous()),
        Map.of(firstWaif, new WorldWaif(List.of()), secondWaif, new WorldWaif(List.of()),
            survivingWaif, new WorldWaif(List.of())));
    try (WorldTxn firstWriter = world.begin(); WorldTxn secondWriter = world.begin();
        WorldTxn reader = world.begin()) {
      reader.anonymousObject(first);
      assertTrue(firstWriter.removeAnonymousObjects(Set.of(first, new AnonymousObjectValue())).isOk());
      firstWriter.removeWaifs(Set.of(firstWaif, waif()));
      assertTrue(firstWriter.commit().isCommitted());
      assertTrue(secondWriter.removeAnonymousObjects(Set.of(second)).isOk());
      secondWriter.removeWaifs(Set.of(secondWaif));
      assertTrue(secondWriter.commit().isCommitted());
      assertEquals(Set.of(first), reader.commit().conflictingAnonymousRecords());
      assertEquals(Set.of(survivor), world.snapshot().anonymousObjects().keySet());
      assertEquals(Set.of(survivingWaif), world.snapshot().waifs().keySet());
    }
  }

  private static Set<MooValue> identities() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }

  private static WorldProperty property(MooValue value, boolean defined) {
    return new WorldProperty("value", value, 0, new PropertyPerms(0), !defined, defined);
  }

  private static WorldAnonymousObject anonymous(WorldProperty... properties) {
    return new WorldAnonymousObject("", 0, 0, List.of(), List.of(), List.of(properties));
  }

  private static WaifValue waif() {
    return new WaifValue(new ObjectValue(0), new ObjectValue(0));
  }

  private static WorldTxn world(Map<AnonymousObjectValue, WorldAnonymousObject> anonymous,
      Map<WaifValue, WorldWaif> waifs) {
    WorldObject root = new WorldObject(0, "root", 0, 0, -1,
        List.of(), List.of(), List.of(), List.of(), List.of());
    return new WorldTxn(List.of(), List.of(root), anonymous, waifs, List.of(), 0);
  }

  private static MooRuntime runtime(WorldTxn world) {
    return SchedulerTestHarness.runtime(world, new ConnectionRegistry(), 2, System::nanoTime,
        MooRuntime::resolveCanonicalConnectionName, Optional.empty(), Optional.empty(), Optional.empty(),
        List.of(), List.of());
  }

  private static MooRuntime.AttemptContext context(WorldTxn world) {
    return new MooRuntime.AttemptContext(world, 1, new LinkedHashMap<>(), new ConnectionRegistry(), 0,
        new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
  }
}
