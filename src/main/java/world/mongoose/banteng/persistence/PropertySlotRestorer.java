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

  private record ParentValues(Map<String, MooValue> byName, List<MooValue> bySlot) {}

  static Map<Long, List<WorldProperty>> restore(
      Map<Long, Input> objects) throws IOException {
    Map<Long, List<WorldProperty>> restored = new LinkedHashMap<>();
    Map<Long, List<Long>> definitions = new HashMap<>();
    Map<Long, ParentValues> parentValues = new HashMap<>();
    Set<Long> ancestry = new HashSet<>();
    for (Input object : objects.values()) {
      restoreProperties(object, objects, restored, definitions, parentValues, ancestry);
    }
    return restored;
  }

  private static List<WorldProperty> restoreProperties(
      Input object,
      Map<Long, Input> objects,
      Map<Long, List<WorldProperty>> restored,
      Map<Long, List<Long>> definitions,
      Map<Long, ParentValues> parentValues,
      Set<Long> ancestry)
      throws IOException {
    List<WorldProperty> existing = restored.get(object.id());
    if (existing != null) {
      return existing;
    }
    if (!ancestry.add(object.id())) {
      throw malformed("cyclic property ancestry at object #" + object.id());
    }
    List<Long> canonical = object.propertyNames().isEmpty() ? List.of() : List.of(object.id());
    Set<Long> multipleParents = object.parents().size() > 1 ? new LinkedHashSet<>(canonical) : null;
    List<ParentValues> inherited = new ArrayList<>(object.parents().size());
    for (long parentId : object.parents()) {
      if (parentId == -1) {
        continue;
      }
      Input parent = objects.get(parentId);
      if (parent == null) {
        throw malformed("object #" + object.id() + " has missing parent #" + parentId);
      }
      List<WorldProperty> parentProperties =
          restoreProperties(parent, objects, restored, definitions, parentValues, ancestry);
      List<Long> parentDefinitions = Objects.requireNonNull(definitions.get(parentId));
      if (multipleParents != null) {
        multipleParents.addAll(parentDefinitions);
      } else if (canonical.isEmpty()) {
        canonical = parentDefinitions;
      } else if (!parentDefinitions.isEmpty()) {
        List<Long> ownAndInherited = new ArrayList<>(1 + parentDefinitions.size());
        ownAndInherited.add(object.id());
        ownAndInherited.addAll(parentDefinitions);
        canonical = ownAndInherited;
      }
      inherited.add(parentValues.computeIfAbsent(parentId, ignored -> firstByName(parentProperties)));
    }
    if (multipleParents != null) canonical = List.copyOf(multipleParents);

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
          if (inherited.size() == 1) {
            value = inherited.getFirst().bySlot().get(slotIndex - 1 - object.propertyNames().size());
          } else {
            for (ParentValues parent : inherited) {
              value = parent.byName().get(name);
              if (value != null) break;
            }
          }
          if (value == null) {
            throw malformed("object #" + object.id()
                + " has clear property without direct-parent fallback " + name);
          }
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
    ancestry.remove(object.id());
    List<WorldProperty> result = List.copyOf(properties);
    restored.put(object.id(), result);
    definitions.put(object.id(), List.copyOf(canonical));
    return result;
  }

  private static ParentValues firstByName(List<WorldProperty> properties) {
    Map<String, MooValue> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (WorldProperty property : properties) {
      values.putIfAbsent(property.name(), property.value());
    }
    List<MooValue> slots = new ArrayList<>(properties.size());
    for (WorldProperty property : properties) {
      slots.add(Objects.requireNonNull(values.get(property.name())));
    }
    return new ParentValues(values, List.copyOf(slots));
  }
}

