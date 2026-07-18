package moo.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One immutable committed world revision. */
record World(WorldRevision revision, List<Long> players, Map<Long, WorldObject> objects) {
  World {
    Objects.requireNonNull(revision, "revision");
    players = List.copyOf(players);
    objects = Collections.unmodifiableMap(new LinkedHashMap<>(objects));
  }

  WorldSnapshot snapshot() {
    return new WorldSnapshot(revision.value(), players, objects);
  }
}
