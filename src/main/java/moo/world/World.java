package moo.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The immutable committed world hidden behind {@link WorldTxn}. */
record World(List<Long> players, Map<Long, WorldObject> objects) {
  World {
    players = List.copyOf(players);
    objects = Collections.unmodifiableMap(new LinkedHashMap<>(objects));
  }
}
