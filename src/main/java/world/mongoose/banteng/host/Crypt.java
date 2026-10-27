package world.mongoose.banteng.host;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/** Lazy libcrypt ownership, including its process-global result buffer. */
@SuppressWarnings("restricted")
public final class Crypt {
  private final String library;
  private boolean loaded;
  private Optional<MethodHandle> handle = Optional.empty();
  private Optional<Throwable> lookupFailure = Optional.empty();

  /** Selects the library to resolve on the first call. */
  public Crypt(String library) {
    this.library = Objects.requireNonNull(library, "library");
  }

  /** Returns the shared, lazily linked system library. */
  public static Crypt system() {
    return SystemLibrary.INSTANCE;
  }

  private static final class SystemLibrary {
    private static final Crypt INSTANCE = new Crypt("libcrypt.so.1");
  }

  private MethodHandle handle() {
    if (!loaded) {
      try {
        handle = Optional.of(Linker.nativeLinker().downcallHandle(
            SymbolLookup.libraryLookup(library, Arena.global()).findOrThrow("crypt"),
            FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)));
      } catch (RuntimeException | LinkageError failure) {
        lookupFailure = Optional.of(failure);
      }
      loaded = true;
    }
    return handle.orElseThrow(() -> new NativeCallException("crypt", lookupFailure.orElseThrow()));
  }

  /** Copies crypt's shared result while holding the same lock as the native call. */
  public String hash(byte[] password, byte[] salt) {
    // All instances share crypt(3)'s process-global storage.
    synchronized (Crypt.class) {
      MethodHandle crypt = handle();
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment result =
            (MemorySegment) crypt.invokeExact(terminated(arena, password), terminated(arena, salt));
        return result.equals(MemorySegment.NULL)
            ? "*0" : result.reinterpret(128).getString(0, StandardCharsets.ISO_8859_1);
      } catch (Error error) {
        throw error;
      } catch (Throwable failure) {
        throw new NativeCallException("crypt", failure);
      }
    }
  }

  private static MemorySegment terminated(Arena arena, byte[] bytes) {
    MemorySegment result = arena.allocate(Math.addExact(bytes.length, 1));
    result.asSlice(0, bytes.length).copyFrom(MemorySegment.ofArray(bytes));
    result.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);
    return result;
  }
}
