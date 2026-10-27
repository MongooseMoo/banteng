package world.mongoose.banteng.persistence;

import static world.mongoose.banteng.persistence.DbScanner.malformed;
import java.io.IOException;
import java.util.*;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.world.PropertyPerms;
import world.mongoose.banteng.world.WorldProperty;

/** Restores the canonical depth-first property slots shared by Toast database versions. */
final class PropertySlotRestorer {
  private PropertySlotRestorer() {}

  record Input(long id, List<Long> parents, List<String> propertyNames,
      List<RawPropertySlot> propertySlots) {}

  static Map<Long, List<WorldProperty>> restore(
      Map<Long, Input> objects) throws IOException {
    Map<Long, List<WorldProperty>> restored = new LinkedHashMap<>();
    for (Input object : objects.values()) {
      restoreProperties(object, objects, restored, new ArrayList<>());
    }
    return restored;
  }

  private static List<WorldProperty> restoreProperties(
      Input object,
      Map<Long, Input> objects,
      Map<Long, List<WorldProperty>> restored,
      List<Long> ancestry)
      throws IOException {
    List<WorldProperty> existing = restored.get(object.id());
    if (existing != null) {
      return existing;
    }
    if (ancestry.contains(object.id())) {
      throw malformed("cyclic property ancestry at object #" + object.id());
    }
    ancestry.add(object.id());

    for (long parentId : object.parents()) {
      if (parentId == -1) {
        continue;
      }
      Input parent = objects.get(parentId);
      if (parent == null) {
        throw malformed("object #" + object.id() + " has missing parent #" + parentId);
      }
      restoreProperties(parent, objects, restored, ancestry);
    }

    List<Long> canonical = rawAncestry(object.id(), objects);
    int expectedSlots = 0;
    for (long ancestorId : canonical) {
      expectedSlots =
          Math.addExact(
              expectedSlots,
              Objects.requireNonNull(objects.get(ancestorId)).propertyNames().size());
    }
    if (object.propertySlots().size() != expectedSlots) {
      throw malformed(
          "object #"
              + object.id()
              + " has "
              + object.propertyNames().size()
              + " definitions and "
              + object.propertySlots().size()
              + " value slots for "
              + (expectedSlots - object.propertyNames().size())
              + " inherited properties");
    }

    List<WorldProperty> properties = new ArrayList<>(object.propertySlots().size());
    int slotIndex = 0;
    for (long definingId : canonical) {
      Input defining = Objects.requireNonNull(objects.get(definingId));
      for (String name : defining.propertyNames()) {
        RawPropertySlot slot = object.propertySlots().get(slotIndex++);
        boolean defined = definingId == object.id();
        if (defined && slot.clear()) {
          throw malformed("object #" + object.id() + " has a clear local property " + name);
        }
        MooValue value = slot.value();
        if (value == null) {
          value = clearFallback(object, name, restored);
        }
        properties.add(
            new WorldProperty(
                name,
                value,
                slot.owner(),
                new PropertyPerms(slot.permissions()),
                slot.clear(),
                defined));
      }
    }
    ancestry.removeLast();
    List<WorldProperty> result = List.copyOf(properties);
    restored.put(object.id(), result);
    return result;
  }

  private static MooValue clearFallback(
      Input object, String propertyName, Map<Long, List<WorldProperty>> restored)
      throws IOException {
    for (long parentId : object.parents()) {
      if (parentId == -1) {
        continue;
      }
      List<WorldProperty> parentProperties = restored.get(parentId);
      if (parentProperties == null) {
        continue;
      }
      for (WorldProperty property : parentProperties) {
        if (property.name().equalsIgnoreCase(propertyName)) {
          return property.value();
        }
      }
    }
    throw malformed(
        "object #" + object.id() + " has clear property without direct-parent fallback "
            + propertyName);
  }

  private static List<Long> rawAncestry(long objectId, Map<Long, Input> objects)
      throws IOException {
    List<Long> result = new ArrayList<>();
    collectRawAncestry(objectId, objects, new LinkedHashSet<>(), new LinkedHashSet<>(), result);
    return List.copyOf(result);
  }

  private static void collectRawAncestry(
      long objectId,
      Map<Long, Input> objects,
      Set<Long> visiting,
      Set<Long> visited,
      List<Long> result)
      throws IOException {
    if (visited.contains(objectId)) {
      return;
    }
    Input object = objects.get(objectId);
    if (object == null) {
      throw malformed("missing inheritance object #" + objectId);
    }
    if (!visiting.add(objectId)) {
      throw malformed("cyclic property ancestry at object #" + objectId);
    }
    visited.add(objectId);
    result.add(objectId);
    for (long parentId : object.parents()) {
      if (parentId != -1) {
        collectRawAncestry(parentId, objects, visiting, visited, result);
      }
    }
    visiting.remove(objectId);
  }

}

