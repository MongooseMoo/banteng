package moo.builtin;

/** Closed publication behavior for one builtin implementation. */
public enum EffectClass {
  PURE,
  TRANSACTION_READ,
  TRANSACTION_WRITE,
  DEFERRED_COMMIT,
  EXTERNAL_READ,
  SUSPENDING_HOST,
  IRREVOCABLE
}
