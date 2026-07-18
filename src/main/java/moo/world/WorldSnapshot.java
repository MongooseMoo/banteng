package moo.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An immutable public view of one committed or transaction-local world revision. */
public record WorldSnapshot(long revision, List<Long> players, Map<Long, WorldObject> objects) {
  /** Takes immutable, insertion-preserving copies of the ordered world records. */
  public WorldSnapshot {
    players = List.copyOf(players);
    objects = Collections.unmodifiableMap(new LinkedHashMap<>(objects));
  }
}
