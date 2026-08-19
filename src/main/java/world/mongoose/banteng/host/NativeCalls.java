package world.mongoose.banteng.host;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** Typed host-library calls that keep {@link Throwable}'s method-handle channel at one boundary. */
public final class NativeCalls {
  @SuppressWarnings("restricted")
  private static final SymbolLookup CRYPT_LIBRARY =
      SymbolLookup.libraryLookup("libcrypt.so.1", Arena.global());

  @SuppressWarnings("restricted")
  private static final MethodHandle CRYPT =
      Linker.nativeLinker()
          .downcallHandle(
              CRYPT_LIBRARY.findOrThrow("crypt"),
              FunctionDescriptor.of(
                  ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

  @SuppressWarnings("restricted")
  private static final MethodHandle CLOCK_GETTIME =
      Linker.nativeLinker()
          .downcallHandle(
              Linker.nativeLinker().defaultLookup().findOrThrow("clock_gettime"),
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

  @SuppressWarnings("restricted")
  private static final MethodHandle GETRUSAGE =
      Linker.nativeLinker()
          .downcallHandle(
              Linker.nativeLinker().defaultLookup().findOrThrow("getrusage"),
              FunctionDescriptor.of(
                  ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

  @SuppressWarnings("restricted")
  private static final MethodHandle LOCALTIME_R =
      Linker.nativeLinker()
          .downcallHandle(
              Linker.nativeLinker().defaultLookup().findOrThrow("localtime_r"),
              FunctionDescriptor.of(
                  ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

  @SuppressWarnings("restricted")
  private static final MethodHandle STRFTIME =
      Linker.nativeLinker()
          .downcallHandle(
              Linker.nativeLinker().defaultLookup().findOrThrow("strftime"),
              FunctionDescriptor.of(
                  ValueLayout.JAVA_LONG,
                  ValueLayout.ADDRESS,
                  ValueLayout.JAVA_LONG,
                  ValueLayout.ADDRESS,
                  ValueLayout.ADDRESS));

  private NativeCalls() {}

  /** Calls {@code getrusage(2)}. */
  public static int getrusage(int who, MemorySegment value) {
    try {
      return (int) GETRUSAGE.invokeExact(who, value);
    } catch (Error error) {
      throw error;
    } catch (Throwable failure) {
      throw new NativeCallException("getrusage", failure);
    }
  }

  /** Calls {@code localtime_r(3)}. */
  public static MemorySegment localtimeR(MemorySegment time, MemorySegment result) {
    try {
      return (MemorySegment) LOCALTIME_R.invokeExact(time, result);
    } catch (Error error) {
      throw error;
    } catch (Throwable failure) {
      throw new NativeCallException("localtime_r", failure);
    }
  }

  /** Calls {@code strftime(3)}. */
  public static long strftime(
      MemorySegment buffer, long size, MemorySegment format, MemorySegment localTime) {
    try {
      return (long) STRFTIME.invokeExact(buffer, size, format, localTime);
    } catch (Error error) {
      throw error;
    } catch (Throwable failure) {
      throw new NativeCallException("strftime", failure);
    }
  }

  /** Calls {@code clock_gettime(2)}. */
  public static int clockGettime(int clockId, MemorySegment timespec) {
    try {
      return (int) CLOCK_GETTIME.invokeExact(clockId, timespec);
    } catch (Error error) {
      throw error;
    } catch (Throwable failure) {
      throw new NativeCallException("clock_gettime", failure);
    }
  }

  /** Calls {@code crypt(3)}, whose result uses process-global storage. */
  public static synchronized MemorySegment crypt(
      MemorySegment password, MemorySegment salt) {
    try {
      return (MemorySegment) CRYPT.invokeExact(password, salt);
    } catch (Error error) {
      throw error;
    } catch (Throwable failure) {
      throw new NativeCallException("crypt", failure);
    }
  }

  /** Checked host-linkage failure translated for code that cannot declare {@link Throwable}. */
  public static final class NativeCallException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    private NativeCallException(String operation, Throwable cause) {
      super(operation + " invocation failed", cause);
    }
  }
}
