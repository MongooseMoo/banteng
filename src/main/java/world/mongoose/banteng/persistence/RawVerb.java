package world.mongoose.banteng.persistence;

import java.io.IOException;
import world.mongoose.banteng.world.ArgSpec;
import world.mongoose.banteng.world.VerbPerms;
import world.mongoose.banteng.world.WorldVerb;

/** Shared persisted verb metadata read before its optional program body. */
record RawVerb(String names, long owner, int permissions, int preposition) {
  WorldVerb compile(String source) throws IOException {
    try {
      return new WorldVerb(names, owner, new VerbPerms(permissions & 15),
          ArgSpec.fromValue((permissions >> 4) & 3), preposition,
          ArgSpec.fromValue((permissions >> 6) & 3), source);
    } catch (IllegalArgumentException error) {
      throw new IOException("unparsable verb program: " + names, error);
    }
  }
}
