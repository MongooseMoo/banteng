package world.mongoose.banteng.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import jdk.jfr.FlightRecorder;
import org.jspecify.annotations.Nullable;
import world.mongoose.banteng.builtin.family.ConnectionBuiltins;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldProperty;
import world.mongoose.banteng.world.WorldSnapshot;
import world.mongoose.banteng.world.WorldWaif;
import world.mongoose.banteng.vm.MooVm.RootVerbCall;
import world.mongoose.banteng.world.WorldTxn.ResolvedVerb;
import world.mongoose.banteng.vm.VmSnapshot;
import world.mongoose.banteng.runtime.MooRuntime.AttemptContext;
import world.mongoose.banteng.runtime.MooRuntime.RuntimeContinuation;

final class FinalizationCollector {
  private final MooRuntime runtime;
  private volatile @Nullable PermanentReachability permanentReachability;

  private record PermanentReachability(
      WorldSnapshot.CollectibleGraph graph,
      Set<AnonymousObjectValue> anonymous,
      Set<WaifValue> waifs,
      Set<MooValue> traversed) {}

  FinalizationCollector(MooRuntime runtime) {
    this.runtime = runtime;
  }

  void finalizePendingObjects(AttemptContext context) {
    List<MooValue> pending = context.world.pendingFinalization();
    if (pending.isEmpty()) {
      return;
    }
    WorldSnapshot snapshot = context.world.snapshot();
    Set<AnonymousObjectValue> finalizing = new LinkedHashSet<>();
    Set<MooValue> traversed = identitySet();
    Set<WaifValue> visitedWaifs = new LinkedHashSet<>();
    List<WaifValue> finalizingWaifs = new ArrayList<>();
    List<MooValue> remaining = new ArrayList<>();
    for (MooValue value : pending) {
      if (value instanceof AnonymousObjectValue anonymous) {
        FinalizationCollector.markFinalizingAnonymous(anonymous, snapshot, finalizing, visitedWaifs, traversed);
      } else if (value instanceof WaifValue waif) {
        finalizingWaifs.add(waif);
      } else {
        remaining.add(value);
      }
    }
    Set<AnonymousObjectValue> withoutHook = new LinkedHashSet<>();
    for (AnonymousObjectValue anonymous : finalizing) {
      ResolvedVerb recycle = context.world.resolveVerb(anonymous, "recycle", true).orElse(null);
      if (recycle == null) {
        withoutHook.add(anonymous);
        continue;
      }
      remaining.add(anonymous);
      RootVerbCall locals = new RootVerbCall(anonymous, -1, -1, "recycle",
          new ListValue(List.of()), "", Optional.empty());
      context.spawnedSteps
          .add(
              runtime.startStored(context,
                  recycle,
                  locals,
                  RuntimeContinuation.anonymousFinalization(anonymous)));
    }
    context.world.removeAnonymousObjects(withoutHook);
    WorldSnapshot waifSnapshot = context.world.snapshot();
    Set<WaifValue> withoutWaifHook = new LinkedHashSet<>();
    Set<AnonymousObjectValue> waifAnonymous = new LinkedHashSet<>();
    Set<MooValue> waifTraversal = identitySet();
    for (WaifValue waif : finalizingWaifs) {
      ResolvedVerb recycle = context.world.resolveVerb(waif.classObject().value(), ":recycle", true).orElse(null);
      if (recycle == null) {
        markReachableAnonymous(waif, waifSnapshot, waifAnonymous, withoutWaifHook, waifTraversal);
        continue;
      }
      remaining.add(waif);
      RootVerbCall locals = new RootVerbCall(waif, -1, -1, ":recycle",
          new ListValue(List.of()), "", Optional.empty());
      context.spawnedSteps
          .add(
              runtime.startStored(context,
                  recycle,
                  locals,
                  RuntimeContinuation.backgroundWaifFinalization(waif)));
    }
    context.world.removeWaifs(withoutWaifHook);
    context.world.replacePendingFinalization(remaining);
  }

  void finishAnonymousFinalization(AttemptContext context, AnonymousObjectValue anonymous) {
    context.world.removeAnonymousObjects(Set.of(anonymous));
    List<MooValue> remaining = new ArrayList<>();
    for (MooValue value : context.world.pendingFinalization()) {
      if (value != anonymous) {
        remaining.add(value);
      }
    }
    context.world.replacePendingFinalization(remaining);
  }

  void collectAfterAnonymousFinalization(AttemptContext context,
      AnonymousObjectValue target,
      List<VmSnapshot> otherTaskRoots,
      boolean startNextFinalizationWave) {
    Objects.requireNonNull(target, "target");
    List<MooValue> transientRoots =
        context.world.anonymousObject(target).orElseThrow().collectibleProperties().stream()
            .filter(WorldProperty::defined)
            .map(WorldProperty::value)
            .toList();
    finishAnonymousFinalization(context, target);
    queueUnreachableAnonymousObjects(context, otherTaskRoots, transientRoots);
    finalizePendingObjects(context);
    if (startNextFinalizationWave) {
      queueUnreachableAnonymousObjects(context, otherTaskRoots);
      finalizePendingObjects(context);
    }
  }

  void finishWaifFinalization(AttemptContext context, WaifValue root) {
    Set<WaifValue> component = removeWaifComponent(context, root);
    List<MooValue> remaining = new ArrayList<>();
    for (MooValue value : context.world.pendingFinalization()) {
      if (value instanceof WaifValue waif && component.contains(waif)) {
        continue;
      }
      remaining.add(value);
    }
    context.world.replacePendingFinalization(remaining);
  }

  Set<WaifValue> removeWaifComponent(AttemptContext context, WaifValue root) {
    WorldSnapshot snapshot = context.world.snapshot();
    Set<WaifValue> component = new LinkedHashSet<>();
    FinalizationCollector.markReachableAnonymous(root, snapshot, new LinkedHashSet<>(), component, identitySet());
    context.world.removeWaifs(component);
    return component;
  }

  void queueUnreachableAnonymousObjects(AttemptContext context,
      List<VmSnapshot> otherTaskRoots, VmSnapshot... taskRoots) {
    queueUnreachableAnonymousObjects(context, otherTaskRoots, List.of(), taskRoots);
  }

  void queueUnreachableAnonymousObjects(AttemptContext context,
      List<VmSnapshot> otherTaskRoots,
      List<MooValue> transientRoots,
      VmSnapshot... taskRoots) {
    WorldSnapshot snapshot = context.world.snapshot();
    if (snapshot.anonymousObjects().isEmpty() && snapshot.waifs().isEmpty()) return;
    @Nullable FinalizationScanEvent scan = null;
    if (FlightRecorder.isInitialized()) {
      scan = new FinalizationScanEvent();
      scan.taskId = context.taskId;
      scan.begin();
    }
    PermanentReachability permanent = permanentReachability(snapshot);
    Set<AnonymousObjectValue> unclaimed = new LinkedHashSet<>();
    for (AnonymousObjectValue identity : snapshot.anonymousObjects().keySet()) {
      if (!permanent.anonymous().contains(identity)) unclaimed.add(identity);
    }
    Set<WaifValue> unclaimedWaifs = new LinkedHashSet<>();
    for (WaifValue identity : snapshot.waifs().keySet()) {
      if (!permanent.waifs().contains(identity)) unclaimedWaifs.add(identity);
    }
    // Other roots can only reduce the set of bodies eligible for finalization.
    if (unclaimed.isEmpty() && unclaimedWaifs.isEmpty()) return;

    Set<AnonymousObjectValue> reachable = new LinkedHashSet<>();
    Set<MooValue> traversed = identitySet();
    Set<WaifValue> visitedWaifs = new LinkedHashSet<>();
    Consumer<MooValue> mark = value ->
        markReachableAnonymous(value, snapshot, reachable, visitedWaifs, traversed, permanent.traversed());
    for (MooValue value : transientRoots) {
      mark.accept(value);
    }
    for (VmSnapshot task : Stream.concat(otherTaskRoots.stream(), Arrays.stream(taskRoots)).toList()) {
      for (MooValue value : task.initialLocals().values()) {
        mark.accept(value);
      }
      for (VmSnapshot.Frame frame : task.frames()) {
        frame.temporary().ifPresent(mark);
        for (MooValue value : frame.operandStack()) {
          mark.accept(value);
        }
        for (VmSnapshot.IndexState index : frame.indexCollections()) {
          mark.accept(index.collection());
          index
              .key()
              .ifPresent(mark);
        }
        for (MooValue value : frame.locals().values()) {
          mark.accept(value);
        }
        for (VmSnapshot.HandlerState handler : frame.handlers()) {
          handler.selector().ifPresent(mark);
        }
        for (VmSnapshot.FinallyState state : frame.finallyStates()) {
          switch (state) {
            case VmSnapshot.Raise raised ->
                mark.accept(raised.exception());
            case VmSnapshot.Uncaught uncaught ->
                mark.accept(uncaught.value());
            case VmSnapshot.Return returned ->
                mark.accept(returned.value());
            case VmSnapshot.FallThrough ignored -> {
              // No retained MOO value.
            }
            case VmSnapshot.Exit ignored -> {
              // No retained MOO value.
            }
          }
        }
        for (VmSnapshot.LoopState loop : frame.loops().values()) {
          switch (loop) {
            case VmSnapshot.CollectionLoop collection -> {
              mark.accept(collection.base());
              collection
                  .next()
                  .ifPresent(
                      value ->
                          mark.accept(value));
            }
            case VmSnapshot.RangeLoop range -> {
              mark.accept(range.next());
              mark.accept(range.end());
            }
          }
        }
        mark.accept(frame.receiver());
      }
      for (ConnectionBuiltins.ConnectionOptionRequest request : task.connectionOptionRequests()) {
        mark.accept(request.value());
      }
      for (AnonymousObjectValue anonymous : task.anonymousCollectionDeferrals()) {
        mark.accept(anonymous);
      }
      task
          .returnValue()
          .ifPresent(mark);
      task
          .forkRequest()
          .ifPresent(
              fork -> {
                for (MooValue value : fork.locals().values()) {
                  mark.accept(value);
                }
              });
      task
          .pendingBuiltin()
          .ifPresent(
              pending -> {
                for (MooValue value : pending.arguments()) {
                  mark.accept(value);
                }
                mark.accept(pending.taskLocal());
                mark.accept(pending.receiver());
                mark.accept(pending.callers());
              });
      mark.accept(task.taskLocal());
    }

    List<MooValue> pending = new ArrayList<>(snapshot.pendingFinalization());
    for (MooValue value : pending) {
      mark.accept(value);
    }
    unclaimed.removeIf(reachable::contains);
    Set<AnonymousObjectValue> referenced = new LinkedHashSet<>();
    Set<WaifValue> referenceWaifs = new LinkedHashSet<>();
    Set<MooValue> referenceTraversal = identitySet();
    for (AnonymousObjectValue identity : unclaimed) {
      WorldAnonymousObject body = Objects.requireNonNull(snapshot.anonymousObjects().get(identity));
      for (WorldProperty property : body.collectibleProperties()) {
        collectAnonymousReferences(
            property.value(), snapshot, referenced, referenceWaifs, referenceTraversal);
      }
    }
    Set<AnonymousObjectValue> roots = new LinkedHashSet<>(unclaimed);
    roots.removeAll(referenced);
    Set<MooValue> componentTraversal = identitySet();
    while (!unclaimed.isEmpty()) {
      AnonymousObjectValue root = roots.isEmpty()
          ? unclaimed.iterator().next() : roots.iterator().next();
      pending.add(root);
      Set<AnonymousObjectValue> component = new LinkedHashSet<>();
      markReachableAnonymous(root, snapshot, component, new LinkedHashSet<>(), componentTraversal);
      unclaimed.removeAll(component);
      roots.removeAll(component);
    }

    unclaimedWaifs.removeIf(visitedWaifs::contains);
    Set<WaifValue> referencedWaifs = new LinkedHashSet<>();
    Set<WaifValue> traversedWaifs = new LinkedHashSet<>();
    Set<AnonymousObjectValue> traversedAnonymous = new LinkedHashSet<>();
    Set<MooValue> waifReferenceTraversal = identitySet();
    for (WaifValue identity : unclaimedWaifs) {
      WorldWaif body = Objects.requireNonNull(snapshot.waifs().get(identity));
      for (WorldProperty property : body.collectibleProperties()) {
        collectWaifReferences(property.value(), snapshot, referencedWaifs,
            traversedWaifs, traversedAnonymous, waifReferenceTraversal);
      }
    }
    Set<WaifValue> waifRoots = new LinkedHashSet<>(unclaimedWaifs);
    waifRoots.removeAll(referencedWaifs);
    Set<MooValue> waifComponentTraversal = identitySet();
    while (!unclaimedWaifs.isEmpty()) {
      WaifValue root = waifRoots.isEmpty()
          ? unclaimedWaifs.iterator().next() : waifRoots.iterator().next();
      pending.add(root);
      Set<WaifValue> component = new LinkedHashSet<>();
      markReachableAnonymous(root, snapshot, new LinkedHashSet<>(), component, waifComponentTraversal);
      unclaimedWaifs.removeAll(component);
      waifRoots.removeAll(component);
    }
    context.world.replacePendingFinalization(pending);
    if (scan != null) {
      scan.traversedValues = permanent.traversed().size() + traversed.size();
      scan.reachableAnonymous = permanent.anonymous().size() + reachable.size();
      scan.reachableWaifs = permanent.waifs().size() + visitedWaifs.size();
      scan.worldAnonymous = snapshot.anonymousObjects().size();
      scan.worldWaifs = snapshot.waifs().size();
      scan.commit();
    }
  }

  private PermanentReachability permanentReachability(WorldSnapshot snapshot) {
    WorldSnapshot.CollectibleGraph graph = snapshot.collectibleGraph();
    PermanentReachability existing = permanentReachability;
    if (existing != null && existing.graph().equals(graph)) return existing;
    Set<AnonymousObjectValue> anonymous = new LinkedHashSet<>();
    Set<WaifValue> waifs = new LinkedHashSet<>();
    Set<MooValue> traversed = identitySet();
    for (MooValue root : snapshot.permanentCollectibleRoots()) {
      markReachableAnonymous(root, snapshot, anonymous, waifs, traversed);
    }
    PermanentReachability reachable = new PermanentReachability(graph, anonymous, waifs, traversed);
    permanentReachability = reachable;
    return reachable;
  }

  static void collectWaifReferences(
      MooValue value,
      WorldSnapshot world,
      Set<WaifValue> references,
      Set<WaifValue> visitedWaifs,
      Set<AnonymousObjectValue> visitedAnonymous,
      Set<MooValue> traversed) {
    if (!visit(value, traversed)) return;
    if (value instanceof WaifValue waif) {
      references.add(waif);
      if (!visitedWaifs.add(waif)) {
        return;
      }
      WorldWaif body = world.waifs().get(waif);
      if (body != null) {
        for (WorldProperty property : body.collectibleProperties()) {
          collectWaifReferences(
              property.value(), world, references, visitedWaifs, visitedAnonymous, traversed);
        }
      }
      return;
    }
    if (value instanceof AnonymousObjectValue anonymous) {
      if (!visitedAnonymous.add(anonymous)) {
        return;
      }
      WorldAnonymousObject body = world.anonymousObjects().get(anonymous);
      if (body != null) {
        for (WorldProperty property : body.collectibleProperties()) {
          collectWaifReferences(
              property.value(), world, references, visitedWaifs, visitedAnonymous, traversed);
        }
      }
      return;
    }
    if (value instanceof ListValue list) {
      for (MooValue element : list.elements()) {
        collectWaifReferences(element, world, references, visitedWaifs, visitedAnonymous, traversed);
      }
      return;
    }
    if (value instanceof MapValue map) {
      for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
        collectWaifReferences(
            entry.getKey(), world, references, visitedWaifs, visitedAnonymous, traversed);
        collectWaifReferences(
            entry.getValue(), world, references, visitedWaifs, visitedAnonymous, traversed);
      }
    }
  }

  static void collectAnonymousReferences(
      MooValue value,
      WorldSnapshot world,
      Set<AnonymousObjectValue> references,
      Set<WaifValue> visitedWaifs,
      Set<MooValue> traversed) {
    if (!visit(value, traversed)) return;
    if (value instanceof AnonymousObjectValue anonymous) {
      references.add(anonymous);
      return;
    }
    if (value instanceof WaifValue waif) {
      if (!visitedWaifs.add(waif)) {
        return;
      }
      WorldWaif body = world.waifs().get(waif);
      if (body != null) {
        for (WorldProperty property : body.collectibleProperties()) {
          collectAnonymousReferences(property.value(), world, references, visitedWaifs, traversed);
        }
      }
      return;
    }
    if (value instanceof ListValue list) {
      for (MooValue element : list.elements()) {
        collectAnonymousReferences(element, world, references, visitedWaifs, traversed);
      }
      return;
    }
    if (value instanceof MapValue map) {
      for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
        collectAnonymousReferences(entry.getKey(), world, references, visitedWaifs, traversed);
        collectAnonymousReferences(entry.getValue(), world, references, visitedWaifs, traversed);
      }
    }
  }

  static void markReachableAnonymous(
      MooValue value,
      WorldSnapshot world,
      Set<AnonymousObjectValue> reachable,
      Set<WaifValue> visitedWaifs,
      Set<MooValue> traversed) {
    markReachableAnonymous(value, world, reachable, visitedWaifs, traversed, Set.of());
  }

  private static void markReachableAnonymous(
      MooValue value,
      WorldSnapshot world,
      Set<AnonymousObjectValue> reachable,
      Set<WaifValue> visitedWaifs,
      Set<MooValue> traversed,
      Set<MooValue> permanentlyTraversed) {
    if (!value.containsCollectibleReferences()
        || permanentlyTraversed.contains(value) || !traversed.add(value)) return;
    if (value instanceof AnonymousObjectValue anonymous) {
      if (!reachable.add(anonymous)) {
        return;
      }
      WorldAnonymousObject body = world.anonymousObjects().get(anonymous);
      if (body != null) {
        for (WorldProperty property : body.collectibleProperties()) {
          markReachableAnonymous(property.value(), world, reachable, visitedWaifs, traversed, permanentlyTraversed);
        }
      }
      return;
    }
    if (value instanceof WaifValue waif) {
      if (!visitedWaifs.add(waif)) {
        return;
      }
      WorldWaif body = world.waifs().get(waif);
      if (body != null) {
        for (WorldProperty property : body.collectibleProperties()) {
          markReachableAnonymous(property.value(), world, reachable, visitedWaifs, traversed, permanentlyTraversed);
        }
      }
      return;
    }
    if (value instanceof ListValue list) {
      for (MooValue element : list.elements()) {
        markReachableAnonymous(element, world, reachable, visitedWaifs, traversed, permanentlyTraversed);
      }
      return;
    }
    if (value instanceof MapValue map) {
      for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
        markReachableAnonymous(entry.getKey(), world, reachable, visitedWaifs, traversed, permanentlyTraversed);
        markReachableAnonymous(entry.getValue(), world, reachable, visitedWaifs, traversed, permanentlyTraversed);
      }
    }
  }

  static void markFinalizingAnonymous(
      MooValue value,
      WorldSnapshot world,
      Set<AnonymousObjectValue> finalizing,
      Set<WaifValue> visitedWaifs,
      Set<MooValue> traversed) {
    if (!visit(value, traversed)) return;
    if (value instanceof AnonymousObjectValue anonymous) {
      if (!finalizing.add(anonymous)) {
        return;
      }
      WorldAnonymousObject body = world.anonymousObjects().get(anonymous);
      if (body != null) {
        for (WorldProperty property : body.collectibleProperties()) {
          if (!property.defined()) {
            markFinalizingAnonymous(property.value(), world, finalizing, visitedWaifs, traversed);
          }
        }
      }
      return;
    }
    if (value instanceof WaifValue waif) {
      if (!visitedWaifs.add(waif)) {
        return;
      }
      WorldWaif body = world.waifs().get(waif);
      if (body != null) {
        for (WorldProperty property : body.collectibleProperties()) {
          markFinalizingAnonymous(property.value(), world, finalizing, visitedWaifs, traversed);
        }
      }
      return;
    }
    if (value instanceof ListValue list) {
      for (MooValue element : list.elements()) {
        markFinalizingAnonymous(element, world, finalizing, visitedWaifs, traversed);
      }
      return;
    }
    if (value instanceof MapValue map) {
      for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
        markFinalizingAnonymous(entry.getKey(), world, finalizing, visitedWaifs, traversed);
        markFinalizingAnonymous(entry.getValue(), world, finalizing, visitedWaifs, traversed);
      }
    }
  }
  private static Set<MooValue> identitySet() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }

  private static boolean visit(MooValue value, Set<MooValue> traversed) {
    return value.containsCollectibleReferences() && traversed.add(value);
  }

}
