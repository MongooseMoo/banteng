package world.mongoose.banteng.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import world.mongoose.banteng.value.MooValue.AnonymousObjectValue;
import org.jspecify.annotations.Nullable;

/** Toast-shaped chained hash table for callable verb resolution. */
final class VerbCache {
  static final int TABLE_SIZE = 7_507;
  private static final int HISTOGRAM_MAX_DEPTH = 16;

  private Entry @Nullable [] table;
  private long hits;
  private long negativeHits;
  private long misses;
  private long generation;

  Resolution resolve(Object receiver, String verbName, Supplier<Resolution> loader) {
    Objects.requireNonNull(receiver, "receiver");
    Objects.requireNonNull(verbName, "verbName");
    Objects.requireNonNull(loader, "loader");
    if (table == null) {
      table = new Entry[TABLE_SIZE];
    }
    int hash = stringHash(verbName) ^ ~receiverHash(receiver);
    int bucket = Integer.remainderUnsigned(hash, table.length);
    for (Entry entry = table[bucket]; entry != null; entry = entry.next) {
      if (entry.hash == hash
          && entry.receiver.equals(receiver)
          && entry.verbName.equalsIgnoreCase(verbName)) {
        if (entry.resolution.found()) {
          hits++;
        } else {
          negativeHits++;
        }
        return entry.resolution;
      }
    }

    misses++;
    Resolution resolution = Objects.requireNonNull(loader.get(), "resolution");
    table[bucket] = new Entry(hash, receiver, verbName, resolution, table[bucket]);
    return resolution;
  }

  void invalidate() {
    if (table == null) {
      return;
    }
    generation++;
    table = new Entry[TABLE_SIZE];
  }

  WorldTxn.VerbCacheStats stats() {
    List<Integer> histogram = new ArrayList<>(HISTOGRAM_MAX_DEPTH + 1);
    for (int index = 0; index <= HISTOGRAM_MAX_DEPTH; index++) {
      histogram.add(0);
    }
    if (table != null) {
      for (Entry bucket : table) {
        int depth = 0;
        for (Entry entry = bucket; entry != null; entry = entry.next) {
          depth++;
        }
        int slot = Math.min(depth, HISTOGRAM_MAX_DEPTH);
        histogram.set(slot, Math.incrementExact(histogram.get(slot)));
      }
    }
    return new WorldTxn.VerbCacheStats(hits, negativeHits, misses, generation, histogram);
  }

  private static int receiverHash(Object receiver) {
    return receiver.hashCode();
  }

  private static int stringHash(String value) {
    int hash = 0;
    for (int index = 0; index < value.length(); index++) {
      int character = value.charAt(index) & 0xff;
      if (character >= 'A' && character <= 'Z') {
        character += 'a' - 'A';
      }
      hash = (hash << 3) + (hash >>> 28) + character;
    }
    return hash;
  }

  record Resolution(
      @Nullable Object location,
      int verbIndex,
      List<Long> permanentReads,
      List<AnonymousObjectValue> anonymousReads) {
    Resolution {
      permanentReads = List.copyOf(permanentReads);
      anonymousReads = List.copyOf(anonymousReads);
    }

    static Resolution missing(
        List<Long> permanentReads, List<AnonymousObjectValue> anonymousReads) {
      return new Resolution(null, -1, permanentReads, anonymousReads);
    }

    boolean found() {
      return location != null;
    }
  }

  private static final class Entry {
    private final int hash;
    private final Object receiver;
    private final String verbName;
    private final Resolution resolution;
    private final Entry next;

    private Entry(
        int hash, Object receiver, String verbName, Resolution resolution, Entry next) {
      this.hash = hash;
      this.receiver = receiver;
      this.verbName = verbName;
      this.resolution = resolution;
      this.next = next;
    }
  }
}
