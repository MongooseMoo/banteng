package world.mongoose.banteng.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import world.mongoose.banteng.builtin.BuiltinCatalog.ConnectionOptionRequest;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import world.mongoose.banteng.value.MooValue.ListValue;
import world.mongoose.banteng.value.MooValue.MapValue;
import world.mongoose.banteng.value.MooValue.WaifValue;
import world.mongoose.banteng.world.WorldAnonymousObject;
import world.mongoose.banteng.world.WorldObject;
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
    Set<WaifValue> visitedWaifs = new LinkedHashSet<>();
    List<WaifValue> finalizingWaifs = new ArrayList<>();
    List<MooValue> remaining = new ArrayList<>();
    for (MooValue value : pending) {
      if (value instanceof AnonymousObjectValue anonymous) {
        FinalizationCollector.markFinalizingAnonymous(anonymous, snapshot, finalizing, visitedWaifs);
      } else if (value instanceof WaifValue waif) {
        finalizingWaifs.add(waif);
      } else {
        remaining.add(value);
      }
    }
    for (AnonymousObjectValue anonymous : finalizing) {
      ResolvedVerb recycle = context.world.resolveVerb(anonymous, "recycle", true).orElse(null);
      if (recycle == null) {
        context.world.removeAnonymousObject(anonymous);
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
    for (WaifValue waif : finalizingWaifs) {
      ResolvedVerb recycle = context.world.resolveVerb(waif.classObject().value(), ":recycle", true).orElse(null);
      if (recycle == null) {
        removeWaifComponent(context, waif);
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
    context.world.replacePendingFinalization(remaining);
  }

  void finishAnonymousFinalization(AttemptContext context, AnonymousObjectValue anonymous) {
    context.world.removeAnonymousObject(anonymous);
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
        context.world.anonymousObject(target).orElseThrow().properties().stream()
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
    FinalizationCollector.markReachableAnonymous(root, snapshot, new LinkedHashSet<>(), component);
    for (WaifValue waif : component) {
      context.world.removeWaif(waif);
    }
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
    Set<AnonymousObjectValue> reachable = new LinkedHashSet<>();
    Set<WaifValue> visitedWaifs = new LinkedHashSet<>();
    for (WorldObject object : snapshot.objects().values()) {
      for (WorldProperty property : object.properties()) {
        FinalizationCollector.markReachableAnonymous(
            property.value(), snapshot, reachable, visitedWaifs);
      }
    }
    for (MooValue value : transientRoots) {
      FinalizationCollector.markReachableAnonymous(value, snapshot, reachable, visitedWaifs);
    }
    for (VmSnapshot task : Stream.concat(otherTaskRoots.stream(), Arrays.stream(taskRoots)).toList()) {
      for (MooValue value : task.initialLocals().values()) {
        FinalizationCollector.markReachableAnonymous(value, snapshot, reachable, visitedWaifs);
      }
      for (VmSnapshot.Frame frame : task.frames()) {
        frame.temporary().ifPresent(value ->
            FinalizationCollector.markReachableAnonymous(value, snapshot, reachable, visitedWaifs));
        for (MooValue value : frame.operandStack()) {
          FinalizationCollector.markReachableAnonymous(value, snapshot, reachable, visitedWaifs);
        }
        for (VmSnapshot.IndexState index : frame.indexCollections()) {
          FinalizationCollector.markReachableAnonymous(index.collection(), snapshot, reachable, visitedWaifs);
          index
              .key()
              .ifPresent(value -> FinalizationCollector.markReachableAnonymous(value, snapshot, reachable, visitedWaifs));
        }
        for (MooValue value : frame.locals().values()) {
          FinalizationCollector.markReachableAnonymous(value, snapshot, reachable, visitedWaifs);
        }
        for (VmSnapshot.FinallyState state : frame.finallyStates()) {
          switch (state) {
            case VmSnapshot.Raise raised ->
                FinalizationCollector.markReachableAnonymous(
                    raised.exception(), snapshot, reachable, visitedWaifs);
            case VmSnapshot.Uncaught uncaught ->
                FinalizationCollector.markReachableAnonymous(
                    uncaught.value(), snapshot, reachable, visitedWaifs);
            case VmSnapshot.Return returned ->
                FinalizationCollector.markReachableAnonymous(
                    returned.value(), snapshot, reachable, visitedWaifs);
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
              FinalizationCollector.markReachableAnonymous(
                  collection.base(), snapshot, reachable, visitedWaifs);
              collection
                  .next()
                  .ifPresent(
                      value ->
                          FinalizationCollector.markReachableAnonymous(
                              value, snapshot, reachable, visitedWaifs));
            }
            case VmSnapshot.RangeLoop range -> {
              FinalizationCollector.markReachableAnonymous(
                  range.next(), snapshot, reachable, visitedWaifs);
              FinalizationCollector.markReachableAnonymous(
                  range.end(), snapshot, reachable, visitedWaifs);
            }
          }
        }
        FinalizationCollector.markReachableAnonymous(frame.receiver(), snapshot, reachable, visitedWaifs);
      }
      for (ConnectionOptionRequest request : task.connectionOptionRequests()) {
        FinalizationCollector.markReachableAnonymous(request.value(), snapshot, reachable, visitedWaifs);
      }
      for (AnonymousObjectValue anonymous : task.anonymousCollectionDeferrals()) {
        FinalizationCollector.markReachableAnonymous(anonymous, snapshot, reachable, visitedWaifs);
      }
      task
          .returnValue()
          .ifPresent(value -> FinalizationCollector.markReachableAnonymous(value, snapshot, reachable, visitedWaifs));
      task
          .forkRequest()
          .ifPresent(
              fork -> {
                for (MooValue value : fork.locals().values()) {
                  FinalizationCollector.markReachableAnonymous(value, snapshot, reachable, visitedWaifs);
                }
              });
      task
          .pendingBuiltin()
          .ifPresent(
              pending -> {
                for (MooValue value : pending.arguments()) {
                  FinalizationCollector.markReachableAnonymous(value, snapshot, reachable, visitedWaifs);
                }
                FinalizationCollector.markReachableAnonymous(pending.taskLocal(), snapshot, reachable, visitedWaifs);
                FinalizationCollector.markReachableAnonymous(pending.receiver(), snapshot, reachable, visitedWaifs);
                FinalizationCollector.markReachableAnonymous(pending.callers(), snapshot, reachable, visitedWaifs);
              });
      FinalizationCollector.markReachableAnonymous(task.taskLocal(), snapshot, reachable, visitedWaifs);
    }

    List<MooValue> pending = new ArrayList<>(snapshot.pendingFinalization());
    for (MooValue value : pending) {
      FinalizationCollector.markReachableAnonymous(value, snapshot, reachable, visitedWaifs);
    }
    Set<AnonymousObjectValue> unclaimed =
        new LinkedHashSet<>(snapshot.anonymousObjects().keySet());
    unclaimed.removeAll(reachable);
    while (!unclaimed.isEmpty()) {
      Set<AnonymousObjectValue> referenced = new LinkedHashSet<>();
      Set<WaifValue> referenceWaifs = new LinkedHashSet<>();
      for (AnonymousObjectValue identity : unclaimed) {
        WorldAnonymousObject body =
            Objects.requireNonNull(snapshot.anonymousObjects().get(identity));
        for (WorldProperty property : body.properties()) {
          FinalizationCollector.collectAnonymousReferences(
              property.value(), snapshot, referenced, referenceWaifs);
        }
      }
      referenced.retainAll(unclaimed);
      AnonymousObjectValue root =
          unclaimed.stream()
              .filter(identity -> !referenced.contains(identity))
              .findFirst()
              .orElse(unclaimed.iterator().next());
      pending.add(root);
      Set<AnonymousObjectValue> component = new LinkedHashSet<>();
      FinalizationCollector.markReachableAnonymous(root, snapshot, component, new LinkedHashSet<>());
      unclaimed.removeAll(component);
    }

    Set<WaifValue> unclaimedWaifs = new LinkedHashSet<>(snapshot.waifs().keySet());
    unclaimedWaifs.removeAll(visitedWaifs);
    while (!unclaimedWaifs.isEmpty()) {
      Set<WaifValue> referenced = new LinkedHashSet<>();
      for (WaifValue identity : unclaimedWaifs) {
        WorldWaif body = Objects.requireNonNull(snapshot.waifs().get(identity));
        for (WorldProperty property : body.properties()) {
          FinalizationCollector.collectWaifReferences(
              property.value(),
              snapshot,
              referenced,
              new LinkedHashSet<>(),
              new LinkedHashSet<>());
        }
      }
      referenced.retainAll(unclaimedWaifs);
      WaifValue root =
          unclaimedWaifs.stream()
              .filter(identity -> !referenced.contains(identity))
              .findFirst()
              .orElse(unclaimedWaifs.iterator().next());
      pending.add(root);
      Set<WaifValue> component = new LinkedHashSet<>();
      FinalizationCollector.markReachableAnonymous(root, snapshot, new LinkedHashSet<>(), component);
      unclaimedWaifs.removeAll(component);
    }
    context.world.replacePendingFinalization(pending);
  }

  static void collectWaifReferences(
      MooValue value,
      WorldSnapshot world,
      Set<WaifValue> references,
      Set<WaifValue> visitedWaifs,
      Set<AnonymousObjectValue> visitedAnonymous) {
    if (value instanceof WaifValue waif) {
      references.add(waif);
      if (!visitedWaifs.add(waif)) {
        return;
      }
      WorldWaif body = world.waifs().get(waif);
      if (body != null) {
        for (WorldProperty property : body.properties()) {
          collectWaifReferences(
              property.value(), world, references, visitedWaifs, visitedAnonymous);
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
        for (WorldProperty property : body.properties()) {
          collectWaifReferences(
              property.value(), world, references, visitedWaifs, visitedAnonymous);
        }
      }
      return;
    }
    if (value instanceof ListValue list) {
      for (MooValue element : list.elements()) {
        collectWaifReferences(element, world, references, visitedWaifs, visitedAnonymous);
      }
      return;
    }
    if (value instanceof MapValue map) {
      for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
        collectWaifReferences(
            entry.getKey(), world, references, visitedWaifs, visitedAnonymous);
        collectWaifReferences(
            entry.getValue(), world, references, visitedWaifs, visitedAnonymous);
      }
    }
  }

  static void collectAnonymousReferences(
      MooValue value,
      WorldSnapshot world,
      Set<AnonymousObjectValue> references,
      Set<WaifValue> visitedWaifs) {
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
        for (WorldProperty property : body.properties()) {
          collectAnonymousReferences(property.value(), world, references, visitedWaifs);
        }
      }
      return;
    }
    if (value instanceof ListValue list) {
      for (MooValue element : list.elements()) {
        collectAnonymousReferences(element, world, references, visitedWaifs);
      }
      return;
    }
    if (value instanceof MapValue map) {
      for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
        collectAnonymousReferences(entry.getKey(), world, references, visitedWaifs);
        collectAnonymousReferences(entry.getValue(), world, references, visitedWaifs);
      }
    }
  }

  static void markReachableAnonymous(
      MooValue value,
      WorldSnapshot world,
      Set<AnonymousObjectValue> reachable,
      Set<WaifValue> visitedWaifs) {
    if (value instanceof AnonymousObjectValue anonymous) {
      if (!reachable.add(anonymous)) {
        return;
      }
      WorldAnonymousObject body = world.anonymousObjects().get(anonymous);
      if (body != null) {
        for (WorldProperty property : body.properties()) {
          markReachableAnonymous(property.value(), world, reachable, visitedWaifs);
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
        for (WorldProperty property : body.properties()) {
          markReachableAnonymous(property.value(), world, reachable, visitedWaifs);
        }
      }
      return;
    }
    if (value instanceof ListValue list) {
      for (MooValue element : list.elements()) {
        markReachableAnonymous(element, world, reachable, visitedWaifs);
      }
      return;
    }
    if (value instanceof MapValue map) {
      for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
        markReachableAnonymous(entry.getKey(), world, reachable, visitedWaifs);
        markReachableAnonymous(entry.getValue(), world, reachable, visitedWaifs);
      }
    }
  }

  static void markFinalizingAnonymous(
      MooValue value,
      WorldSnapshot world,
      Set<AnonymousObjectValue> finalizing,
      Set<WaifValue> visitedWaifs) {
    if (value instanceof AnonymousObjectValue anonymous) {
      if (!finalizing.add(anonymous)) {
        return;
      }
      WorldAnonymousObject body = world.anonymousObjects().get(anonymous);
      if (body != null) {
        for (WorldProperty property : body.properties()) {
          if (!property.defined()) {
            markFinalizingAnonymous(property.value(), world, finalizing, visitedWaifs);
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
        for (WorldProperty property : body.properties()) {
          markFinalizingAnonymous(property.value(), world, finalizing, visitedWaifs);
        }
      }
      return;
    }
    if (value instanceof ListValue list) {
      for (MooValue element : list.elements()) {
        markFinalizingAnonymous(element, world, finalizing, visitedWaifs);
      }
      return;
    }
    if (value instanceof MapValue map) {
      for (Map.Entry<MooValue, MooValue> entry : map.entries().entrySet()) {
        markFinalizingAnonymous(entry.getKey(), world, finalizing, visitedWaifs);
        markFinalizingAnonymous(entry.getValue(), world, finalizing, visitedWaifs);
      }
    }
  }
}
