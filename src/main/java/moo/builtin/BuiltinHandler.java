package moo.builtin;

/** Production callable stored directly in one builtin manifest entry. */
@FunctionalInterface
public interface BuiltinHandler {
  /** Invokes this builtin with the complete current activation context. */
  BuiltinResult invoke(BuiltinCall call);
}
