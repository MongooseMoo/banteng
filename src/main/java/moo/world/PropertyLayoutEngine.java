package moo.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Pure inheritance-graph and property-layout algorithms shared by world record kinds. */
final class PropertyLayoutEngine {
  private PropertyLayoutEngine() {}

  static List<Long> validateNewParents(
      long objectId, List<Long> requestedParents, Map<Long, WorldObject> objects) {
    Objects.requireNonNull(requestedParents, "requestedParents");
    List<Long> parents = List.copyOf(requestedParents);
    if (new LinkedHashSet<>(parents).size() != parents.size()) {
      throw new IllegalArgumentException("duplicate inheritance parent");
    }
    for (long parentId : parents) {
      if (parentId < 0 || !objects.containsKey(parentId)) {
        throw new IllegalArgumentException("missing parent #" + parentId);
      }
      if (parentId == objectId
          || ancestryFromParents(List.of(parentId), objects).contains(objectId)) {
        throw new IllegalArgumentException("recursive inheritance parent #" + parentId);
      }
    }

    Map<String, Long> definitions = new LinkedHashMap<>();
    if (objectId >= 0) {
      WorldObject target = Objects.requireNonNull(objects.get(objectId));
      for (WorldProperty property : target.properties()) {
        if (property.defined()) {
          definitions.put(property.name().toLowerCase(Locale.ROOT), objectId);
        }
      }
    }
    for (long ancestorId : ancestryFromParents(parents, objects)) {
      WorldObject ancestor = Objects.requireNonNull(objects.get(ancestorId));
      for (WorldProperty property : ancestor.properties()) {
        if (!property.defined()) {
          continue;
        }
        String name = property.name().toLowerCase(Locale.ROOT);
        Long previous = definitions.putIfAbsent(name, ancestorId);
        if (previous != null && previous != ancestorId) {
          throw new IllegalArgumentException("conflicting inherited property " + property.name());
        }
      }
    }
    return parents;
  }

  static List<Long> ancestryFromParents(
      List<Long> parents, Map<Long, WorldObject> objects) {
    List<Long> result = new ArrayList<>();
    Set<Long> visited = new LinkedHashSet<>();
    for (long parentId : parents) {
      collectAncestry(parentId, objects, new LinkedHashSet<>(), visited, result);
    }
    return List.copyOf(result);
  }

  private static void collectAncestry(
      long objectId,
      Map<Long, WorldObject> objects,
      Set<Long> visiting,
      Set<Long> visited,
      List<Long> result) {
    if (visited.contains(objectId)) {
      return;
    }
    WorldObject object = objects.get(objectId);
    if (object == null || !visiting.add(objectId)) {
      throw new IllegalArgumentException("invalid inheritance graph at #" + objectId);
    }
    visited.add(objectId);
    result.add(objectId);
    for (long parentId : object.parents()) {
      collectAncestry(parentId, objects, visiting, visited, result);
    }
    visiting.remove(objectId);
  }

  static List<WorldProperty> inheritedProperties(
      List<Long> parents, long owner, Map<Long, WorldObject> objects) {
    List<WorldProperty> properties = new ArrayList<>();
    for (long ancestorId : ancestryFromParents(parents, objects)) {
      WorldObject ancestor = Objects.requireNonNull(objects.get(ancestorId));
      for (WorldProperty property : ancestor.properties()) {
        if (!property.defined()) {
          continue;
        }
        WorldProperty fallback =
            directParentProperty(parents, property.name(), objects).orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "missing direct-parent property " + property.name()));
        properties.add(
            new WorldProperty(
                property.name(),
                fallback.value(),
                (fallback.permissions() & 8) != 0 ? owner : fallback.owner(),
                fallback.permissions(),
                true,
                false));
      }
    }
    return properties;
  }

  static Map<Long, WorldObject> rebuildPropertyLayouts(
      Map<Long, WorldObject> oldSource,
      Map<Long, WorldObject> newSource,
      Set<Long> roots) {
    Set<Long> affected = descendantsOf(roots, newSource);
    Map<Long, WorldObject> rebuilt = new LinkedHashMap<>(newSource);
    Set<Long> complete = new LinkedHashSet<>();
    for (long objectId : affected) {
      rebuildPropertyLayout(objectId, oldSource, newSource, rebuilt, affected, complete);
    }
    return rebuilt;
  }

  private static void rebuildPropertyLayout(
      long objectId,
      Map<Long, WorldObject> oldSource,
      Map<Long, WorldObject> newSource,
      Map<Long, WorldObject> rebuilt,
      Set<Long> affected,
      Set<Long> complete) {
    if (!complete.add(objectId)) {
      return;
    }
    WorldObject object = Objects.requireNonNull(newSource.get(objectId));
    for (long parentId : object.parents()) {
      if (affected.contains(parentId)) {
        rebuildPropertyLayout(parentId, oldSource, newSource, rebuilt, affected, complete);
      }
    }
    List<WorldProperty> properties = new ArrayList<>();
    for (WorldProperty property : object.properties()) {
      if (property.defined()) {
        properties.add(property);
      }
    }
    WorldObject oldObject = oldSource.get(objectId);
    Map<PropertyDefinition, WorldProperty> old =
        oldObject == null ? Map.of() : oldPropertySlots(oldObject, oldSource);
    Set<String> names = new LinkedHashSet<>();
    for (WorldProperty property : properties) {
      names.add(property.name().toLowerCase(Locale.ROOT));
    }
    for (long ancestorId : ancestryFromParents(object.parents(), newSource)) {
      WorldObject ancestor = Objects.requireNonNull(newSource.get(ancestorId));
      for (WorldProperty definition : ancestor.properties()) {
        if (!definition.defined()) {
          continue;
        }
        String normalized = definition.name().toLowerCase(Locale.ROOT);
        if (!names.add(normalized)) {
          throw new IllegalArgumentException("conflicting inherited property " + definition.name());
        }
        WorldProperty fallback =
            directParentProperty(object.parents(), definition.name(), rebuilt).orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "missing direct-parent property " + definition.name()));
        WorldProperty previous = old.get(new PropertyDefinition(ancestorId, normalized));
        if (previous != null && !previous.defined()) {
          properties.add(
              new WorldProperty(
                  definition.name(),
                  previous.clear() ? fallback.value() : previous.value(),
                  previous.owner(),
                  previous.permissions(),
                  previous.clear(),
                  false));
        } else {
          properties.add(
              new WorldProperty(
                  definition.name(),
                  fallback.value(),
                  (fallback.permissions() & 8) != 0 ? object.owner() : fallback.owner(),
                  fallback.permissions(),
                  true,
                  false));
        }
      }
    }
    rebuilt.put(
        objectId,
        new WorldObject(
            object.id(),
            object.name(),
            object.flags(),
            object.owner(),
            object.location(),
            object.lastMove(),
            object.parents(),
            object.contents(),
            object.children(),
            object.verbs(),
            properties));
  }

  static Map<PropertyDefinition, WorldProperty> oldPropertySlots(
      WorldObject object, Map<Long, WorldObject> source) {
    Map<PropertyDefinition, WorldProperty> slots = new LinkedHashMap<>();
    if (object == null) {
      return slots;
    }
    List<PropertyDefinition> definitions = new ArrayList<>();
    for (WorldProperty property : object.properties()) {
      if (property.defined()) {
        definitions.add(
            new PropertyDefinition(object.id(), property.name().toLowerCase(Locale.ROOT)));
      }
    }
    for (long ancestorId : ancestryFromParents(object.parents(), source)) {
      WorldObject ancestor = Objects.requireNonNull(source.get(ancestorId));
      for (WorldProperty property : ancestor.properties()) {
        if (property.defined()) {
          definitions.add(
              new PropertyDefinition(ancestorId, property.name().toLowerCase(Locale.ROOT)));
        }
      }
    }
    int count = Math.min(definitions.size(), object.properties().size());
    for (int index = 0; index < count; index++) {
      slots.put(definitions.get(index), object.properties().get(index));
    }
    return slots;
  }

  static Set<Long> descendantsOf(Set<Long> roots, Map<Long, WorldObject> objects) {
    Set<Long> descendants = new LinkedHashSet<>();
    List<Long> pending = new ArrayList<>(roots);
    for (int index = 0; index < pending.size(); index++) {
      long objectId = pending.get(index);
      if (!descendants.add(objectId)) {
        continue;
      }
      WorldObject object = objects.get(objectId);
      if (object != null) {
        pending.addAll(object.children());
      }
    }
    descendants.retainAll(objects.keySet());
    return descendants;
  }

  static Optional<WorldProperty> directParentProperty(
      List<Long> parents, String name, Map<Long, WorldObject> objects) {
    for (long parentId : parents) {
      WorldObject parent = objects.get(parentId);
      if (parent == null) {
        continue;
      }
      for (WorldProperty property : parent.properties()) {
        if (property.name().equalsIgnoreCase(name)) {
          return Optional.of(property);
        }
      }
    }
    return Optional.empty();
  }

  static boolean usesAffectedAncestor(
      List<Long> parents, Map<Long, WorldObject> objects, Set<Long> affectedObjects) {
    for (long parent : parents) {
      if (objects.containsKey(parent)) {
        for (long ancestor : ancestryFromParents(List.of(parent), objects)) {
          if (affectedObjects.contains(ancestor)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  static List<WorldProperty> rebuiltAnonymousProperties(
      WorldAnonymousObject oldObject,
      List<Long> oldParents,
      Map<Long, WorldObject> oldObjects,
      WorldAnonymousObject object,
      List<Long> parents,
      Map<Long, WorldObject> objects) {
    List<WorldProperty> properties = new ArrayList<>();
    Map<PropertyDefinition, WorldProperty> old = new LinkedHashMap<>();
    Set<String> names = new LinkedHashSet<>();
    List<PropertyDefinition> oldDefinitions = new ArrayList<>();
    for (WorldProperty property : oldObject.properties()) {
      if (property.defined()) {
        oldDefinitions.add(
            new PropertyDefinition(Long.MIN_VALUE, property.name().toLowerCase(Locale.ROOT)));
      }
    }
    for (long ancestorId : ancestryFromParents(oldParents, oldObjects)) {
      WorldObject ancestor = Objects.requireNonNull(oldObjects.get(ancestorId));
      for (WorldProperty property : ancestor.properties()) {
        if (property.defined()) {
          oldDefinitions.add(
              new PropertyDefinition(ancestorId, property.name().toLowerCase(Locale.ROOT)));
        }
      }
    }
    int oldCount = Math.min(oldDefinitions.size(), oldObject.properties().size());
    for (int index = 0; index < oldCount; index++) {
      old.put(oldDefinitions.get(index), oldObject.properties().get(index));
    }
    for (WorldProperty property : object.properties()) {
      String normalized = property.name().toLowerCase(Locale.ROOT);
      if (property.defined()) {
        names.add(normalized);
        properties.add(property);
      }
    }
    for (long ancestorId : ancestryFromParents(parents, objects)) {
      WorldObject ancestor = Objects.requireNonNull(objects.get(ancestorId));
      for (WorldProperty definition : ancestor.properties()) {
        if (!definition.defined()) {
          continue;
        }
        String normalized = definition.name().toLowerCase(Locale.ROOT);
        if (!names.add(normalized)) {
          throw new IllegalArgumentException("conflicting inherited property " + definition.name());
        }
        WorldProperty fallback =
            directParentProperty(parents, definition.name(), objects).orElseThrow();
        WorldProperty previous = old.get(new PropertyDefinition(ancestorId, normalized));
        if (previous != null && !previous.defined()) {
          properties.add(
              new WorldProperty(
                  definition.name(),
                  previous.clear() ? fallback.value() : previous.value(),
                  previous.owner(),
                  previous.permissions(),
                  previous.clear(),
                  false));
        } else {
          properties.add(
              new WorldProperty(
                  definition.name(),
                  fallback.value(),
                  (fallback.permissions() & 8) != 0 ? object.owner() : fallback.owner(),
                  fallback.permissions(),
                  true,
                  false));
        }
      }
    }
    return List.copyOf(properties);
  }

  record PropertyDefinition(long objectId, String name) {}
}
