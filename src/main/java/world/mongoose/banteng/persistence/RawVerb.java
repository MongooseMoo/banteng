package world.mongoose.banteng.persistence;

/** Shared persisted verb metadata read before its optional program body. */
record RawVerb(String names, long owner, int permissions, int preposition) {}
