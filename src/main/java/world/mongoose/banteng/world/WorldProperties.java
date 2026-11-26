package world.mongoose.banteng.world;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/** Immutable property slots and their collectible-bearing subset. */
final class WorldProperties extends AbstractList<WorldProperty> implements RandomAccess {
  private final List<WorldProperty> properties;
  private final List<WorldProperty> collectibleProperties;
  private volatile @Nullable Map<String, WorldProperty> propertiesByName;

  private WorldProperties(List<WorldProperty> properties) {
    this.properties = List.copyOf(properties);
    ArrayList<WorldProperty> roots = null;
    for (int index = 0; index < this.properties.size(); index++) {
      WorldProperty property = this.properties.get(index);
      if (property.value().containsCollectibleReferences()) {
        if (roots == null) roots = new ArrayList<>();
        roots.add(property);
      }
    }
    collectibleProperties = roots == null ? List.of() : List.copyOf(roots);
  }

  static WorldProperties copyOf(List<WorldProperty> properties) {
    return properties instanceof WorldProperties owned ? owned : new WorldProperties(properties);
  }

  List<WorldProperty> collectibleProperties() {
    return collectibleProperties;
  }

  @Nullable WorldProperty findByName(String name) {
    if (properties.isEmpty()) return null;
    Map<String, WorldProperty> index = propertiesByName;
    if (index == null) {
      index = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      for (WorldProperty property : properties) index.putIfAbsent(property.name(), property);
      propertiesByName = index;
    }
    return index.get(name);
  }

  @Override
  public WorldProperty get(int index) {
    return properties.get(index);
  }

  @Override
  public int size() {
    return properties.size();
  }
}
