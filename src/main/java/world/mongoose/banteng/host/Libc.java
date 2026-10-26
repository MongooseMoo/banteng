package world.mongoose.banteng.host;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.StringTokenizer;

/** Lazy libc calls and the Linux resource layouts used by the host. */
@SuppressWarnings("restricted")
public final class Libc {
  private Libc() {}

  private record Bindings(
      MethodHandle clock, MethodHandle usage, MethodHandle localtime, MethodHandle strftime) {}

  private static final class Library {
    private static final Optional<Bindings> BINDINGS;
    private static final Optional<Throwable> FAILURE;

    static {
      Optional<Bindings> bindings = Optional.empty();
      Optional<Throwable> failure = Optional.empty();
      try {
        Linker linker = Linker.nativeLinker();
        SymbolLookup symbols = linker.defaultLookup();
        FunctionDescriptor intPointer =
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
        bindings = Optional.of(new Bindings(
            linker.downcallHandle(symbols.findOrThrow("clock_gettime"), intPointer),
            linker.downcallHandle(symbols.findOrThrow("getrusage"), intPointer),
            linker.downcallHandle(symbols.findOrThrow("localtime_r"), FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
            linker.downcallHandle(symbols.findOrThrow("strftime"), FunctionDescriptor.of(
                ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS, ValueLayout.ADDRESS))));
      } catch (RuntimeException | LinkageError unavailable) {
        failure = Optional.of(unavailable);
      }
      BINDINGS = bindings;
      FAILURE = failure;
    }
  }

  private static Bindings bindings(String operation) {
    return Library.BINDINGS.orElseThrow(
        () -> new NativeCallException(operation, Library.FAILURE.orElseThrow()));
  }

  /** Formats the local time using the existing libc format and space-padded day. */
  public static Optional<String> ctime(long epochSecond) {
    Bindings api = bindings("ctime");
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment time = arena.allocate(ValueLayout.JAVA_LONG);
      time.set(ValueLayout.JAVA_LONG, 0, epochSecond);
      MemorySegment localTime = arena.allocate(64, Long.BYTES);
      MemorySegment converted = (MemorySegment) api.localtime().invokeExact(time, localTime);
      if (converted.address() == 0) return Optional.empty();
      MemorySegment format = arena.allocateFrom("%a %b %d %H:%M:%S %Y %Z");
      MemorySegment buffer = arena.allocate(128);
      long length = (long) api.strftime().invokeExact(buffer, 128L, format, localTime);
      if (length == 0) return Optional.empty();
      String text = buffer.getString(0);
      if (text.charAt(8) == '0') text = text.substring(0, 8) + ' ' + text.substring(9);
      return Optional.of(text);
    } catch (Error error) {
      throw error;
    } catch (Throwable failure) {
      throw new NativeCallException("ctime", failure);
    }
  }

  /** Reads a clock using the Linux timespec layout. */
  public static double clockTime(int clockId) {
    Bindings api = bindings("clock_gettime");
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment timespec = arena.allocate(16, Long.BYTES);
      int status = (int) api.clock().invokeExact(clockId, timespec);
      if (status != 0) throw new IllegalStateException("clock_gettime returned " + status);
      return timespec.get(ValueLayout.JAVA_LONG, 0)
          + timespec.get(ValueLayout.JAVA_LONG, Long.BYTES) / 1_000_000_000.0;
    } catch (Error error) {
      throw error;
    } catch (Throwable failure) {
      throw new NativeCallException("clock_gettime", failure);
    }
  }

  /** Reads the resource fields exposed by usage(), retaining zeros on a failed status. */
  public static long[] resourceUsage() {
    Bindings api = bindings("getrusage");
    long[] resource = new long[11];
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment value = arena.allocate(144, 8);
      int status = (int) api.usage().invokeExact(0, value);
      if (status == 0) {
        long[] offsets = {0, 8, 16, 24, 40, 48, 64, 72, 104, 112, 96};
        for (int index = 0; index < offsets.length; index++) {
          resource[index] = value.get(ValueLayout.JAVA_LONG, offsets[index]);
        }
      }
      return resource;
    } catch (Error error) {
      throw error;
    } catch (Throwable failure) {
      throw new NativeCallException("getrusage", failure);
    }
  }

  /** Reads the five statm fields exposed by memory_usage(). */
  public static double[] memoryUsage() throws IOException {
    StringTokenizer fields = new StringTokenizer(
        Files.readString(Path.of("/proc/self/statm"), StandardCharsets.US_ASCII));
    if (fields.countTokens() != 7) throw new IllegalArgumentException("invalid statm field count");
    double[] result = new double[5];
    int output = 0;
    for (int index = 0; index < 7; index++) {
      String field = fields.nextToken();
      if (index != 4 && index != 6) result[output++] = Double.parseDouble(field);
    }
    return result;
  }

  /** Reads fixed-point load averages, retaining the existing zero fallback. */
  public static long[] loadAverages() {
    long[] loads = new long[3];
    try {
      StringTokenizer fields = new StringTokenizer(
          Files.readString(Path.of("/proc/loadavg"), StandardCharsets.US_ASCII));
      for (int index = 0; index < loads.length; index++) {
        loads[index] = (long) (Double.parseDouble(fields.nextToken()) * 65_536.0);
      }
    } catch (IOException | NumberFormatException | java.util.NoSuchElementException exception) {
      return new long[3];
    }
    return loads;
  }
}
