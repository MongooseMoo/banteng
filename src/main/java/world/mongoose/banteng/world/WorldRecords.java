package world.mongoose.banteng.world;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import world.mongoose.banteng.value.MooValue;

/** Owned immutable ordered records, shared by world revisions and their snapshots. */
final class WorldRecords<K, V> extends AbstractMap<K, V> {
  private static final int MAX_REPLACEMENTS = 128;
  private final Map<K, V> records;
  private final Map<K, V> replacements;
  private final long greatestObjectId;
  private final List<MooValue> collectibleRoots;
  private final Object collectibleGraph;

  private WorldRecords(
      Map<K, V> owned, Map<K, V> replacements, long greatestObjectId, List<MooValue> collectibleRoots,
      Object collectibleGraph) {
    records = Collections.unmodifiableMap(owned);
    this.replacements = replacements;
    this.greatestObjectId = greatestObjectId;
    this.collectibleRoots = collectibleRoots;
    this.collectibleGraph = collectibleGraph;
  }

  static <K, V> WorldRecords<K, V> copyOf(Map<K, V> records) {
    if (records instanceof WorldRecords<K, V> owned) return owned;
    LinkedHashMap<K, V> copy = new LinkedHashMap<>(records);
    long greatest = -1;
    for (K key : copy.keySet()) {
      if (key instanceof Long id) greatest = Math.max(greatest, id);
    }
    return new WorldRecords<>(copy, Map.of(), greatest,
        collectPermanentRoots(copy, Map.of(), greatest), new Object());
  }

  static <K, V> WorldRecords<K, V> with(Map<K, V> records, K key, V value) {
    return updated(records, Set.of(key), Map.of(key, value));
  }

  static long greatestObjectId(Map<Long, WorldObject> records) {
    if (records instanceof WorldRecords<Long, WorldObject> owned) return owned.greatestObjectId;
    long greatest = -1;
    for (long id : records.keySet()) greatest = Math.max(greatest, id);
    return greatest;
  }

  static <K, V> WorldRecords<K, V> updated(
      Map<K, V> records, Set<K> changed, Map<K, V> replacements) {
    WorldRecords<K, V> original = copyOf(records);
    if (changed.isEmpty()) return original;
    if (changed.size() <= MAX_REPLACEMENTS) {
      Map<K, V> overlay = new HashMap<>(original.replacements);
      boolean sameKeys = true;
      boolean edgesChanged = false;
      for (K key : changed) {
        V value = replacements.get(key);
        if (value == null || !original.records.containsKey(key)) {
          sameKeys = false;
          break;
        }
        overlay.put(key, value);
        edgesChanged |= !sameCollectibleEdges(original.get(key), value);
      }
      if (sameKeys && overlay.size() <= MAX_REPLACEMENTS) {
        return original.revised(original.records, overlay, original.greatestObjectId, edgesChanged);
      }
    }
    Map<K, V> copy = new LinkedHashMap<>(original);
    long greatest = original.greatestObjectId;
    boolean removedGreatest = false;
    boolean collectibleRootsChanged = false;
    for (K key : changed) {
      V replacement = replacements.get(key);
      collectibleRootsChanged |= !sameCollectibleEdges(original.get(key), replacement);
      if (replacement == null) {
        copy.remove(key);
        if (key instanceof Long id && id == greatest) removedGreatest = true;
      } else {
        copy.put(key, replacement);
        if (key instanceof Long id) greatest = Math.max(greatest, id);
      }
    }
    if (removedGreatest) {
      greatest = -1;
      for (K key : copy.keySet()) {
        if (key instanceof Long id) greatest = Math.max(greatest, id);
      }
    }
    return original.revised(copy, Map.of(), greatest, collectibleRootsChanged);
  }

  private WorldRecords<K, V> revised(
      Map<K, V> base, Map<K, V> overlay, long greatest, boolean edgesChanged) {
    if (edgesChanged) {
      return new WorldRecords<>(base, overlay, greatest,
          collectPermanentRoots(base, overlay, greatest), new Object());
    }
    return new WorldRecords<>(base, overlay, greatest, collectibleRoots, collectibleGraph);
  }

  static List<MooValue> collectibleRoots(Map<Long, WorldObject> objects) {
    return copyOf(objects).collectibleRoots;
  }

  static Object collectibleGraph(Map<?, ?> records) {
    return copyOf(records).collectibleGraph;
  }

  private static boolean sameCollectibleEdges(@Nullable Object before, @Nullable Object after) {
    List<WorldProperty> previous = collectibleProperties(before);
    List<WorldProperty> next = collectibleProperties(after);
    if (previous.size() != next.size()) return false;
    for (int index = 0; index < previous.size(); index++) {
      if (!previous.get(index).value().equals(next.get(index).value())) return false;
    }
    return true;
  }

  private static List<WorldProperty> collectibleProperties(@Nullable Object value) {
    return switch (value) {
      case PropertyHolder holder -> holder.collectibleProperties();
      case WorldWaif waif -> waif.collectibleProperties();
      case null, default -> List.of();
    };
  }

  private static List<MooValue> collectPermanentRoots(
      Map<?, ?> records, Map<?, ?> replacements, long greatestObjectId) {
    if (greatestObjectId == -1) return List.of();
    Set<MooValue> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    List<MooValue> roots = new ArrayList<>();
    for (Entry<?, ?> entry : records.entrySet()) {
      Object replacement = replacements.isEmpty() ? null : replacements.get(entry.getKey());
      Object value = replacement == null ? entry.getValue() : replacement;
      if (value instanceof WorldObject object) {
        for (WorldProperty property : object.collectibleProperties()) {
          if (seen.add(property.value())) roots.add(property.value());
        }
      }
    }
    return List.copyOf(roots);
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    if (replacements.isEmpty()) return records.entrySet();
    return Collections.unmodifiableSet(new AbstractSet<>() {
      @Override
      public Iterator<Entry<K, V>> iterator() {
        return records.entrySet().stream().map(entry -> {
          V replacement = replacements.get(entry.getKey());
          return replacement == null ? entry : Map.entry(entry.getKey(), replacement);
        }).iterator();
      }

      @Override
      public int size() {
        return records.size();
      }
    });
  }

  @Override
  public Set<K> keySet() {
    return records.keySet();
  }

  @Override
  public @Nullable V get(@Nullable Object key) {
    V replacement = replacements.isEmpty() ? null : replacements.get(key);
    return replacement == null ? records.get(key) : replacement;
  }

  @Override
  public boolean containsKey(@Nullable Object key) {
    return records.containsKey(key);
  }

  @Override
  public int size() {
    return records.size();
  }
}
