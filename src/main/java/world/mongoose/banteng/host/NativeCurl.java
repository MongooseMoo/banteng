package world.mongoose.banteng.host;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;

/** Bounded legacy GET transfers through the host libcurl. */
@SuppressWarnings("restricted")
public final class NativeCurl {
  private NativeCurl() {}

  /** Native status, response bytes, and whether the response hit its size limit. */
  public record Result(int status, String message, String body, boolean overflowed) {}

  /** Performs a transfer with verified TLS and only HTTP, HTTPS and DICT protocols. */
  public static Result get(String url, boolean headers, long timeout, long maximumBytes) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment handle = (MemorySegment) Api.INIT.invokeExact();
      if (handle.equals(MemorySegment.NULL)) {
        return new Result(-1, "Could not initialize curl handle", "", false);
      }
      try {
        Sink sink = new Sink(maximumBytes);
        MemorySegment writer = Linker.nativeLinker().upcallStub(Api.WRITE.bindTo(sink),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS), arena);
        pointerOption(handle, 10002, arena.allocateFrom(url)); // CURLOPT_URL
        pointerOption(handle, 10318, arena.allocateFrom("http,https,dict")); // PROTOCOLS_STR
        pointerOption(handle, 20011, writer); // WRITEFUNCTION
        pointerOption(handle, 10018, arena.allocateFrom("Banteng/0.1.0")); // USERAGENT
        pointerOption(handle, 10102, arena.allocateFrom("")); // ACCEPT_ENCODING
        longOption(handle, 13, timeout); // TIMEOUT
        longOption(handle, 99, 1); // NOSIGNAL
        longOption(handle, 64, 1); // SSL_VERIFYPEER
        longOption(handle, 81, 2); // SSL_VERIFYHOST
        longOption(handle, 42, headers ? 1 : 0); // HEADER
        int status = (int) Api.PERFORM.invokeExact(handle);
        MemorySegment error = (MemorySegment) Api.ERROR.invokeExact(status);
        return new Result(status, error.reinterpret(Long.MAX_VALUE).getString(0),
            sink.bytes.toString(StandardCharsets.ISO_8859_1), sink.overflowed);
      } finally {
        Api.CLEANUP.invokeExact(handle);
      }
    } catch (Error error) {
      throw error;
    } catch (Throwable failure) {
      throw new IllegalStateException("libcurl transfer failed", failure);
    }
  }

  private static void pointerOption(MemorySegment handle, int option, MemorySegment value)
      throws Throwable {
    int status = (int) Api.SET_POINTER.invokeExact(handle, option, value);
    if (status != 0) throw new IllegalStateException("libcurl rejected option " + option);
  }

  private static void longOption(MemorySegment handle, int option, long value) throws Throwable {
    int status = (int) Api.SET_LONG.invokeExact(handle, option, value);
    if (status != 0) throw new IllegalStateException("libcurl rejected option " + option);
  }

  private static final class Sink {
    private final long maximum;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private boolean overflowed;

    private Sink(long maximum) {
      this.maximum = maximum;
    }

    @SuppressWarnings("unused") // Native curl_write_callback entry point.
    private long write(MemorySegment data, long size, long count) {
      long available = maximum - bytes.size();
      if (size < 0 || count < 0 || (count > 0 && size > available / count)) {
        overflowed = true;
        return 0;
      }
      long length = size * count;
      bytes.writeBytes(data.reinterpret(length).toArray(ValueLayout.JAVA_BYTE));
      return length;
    }
  }

  private static final class Api {
    private static final SymbolLookup LIBRARY = library();
    private static final MethodHandle INIT = bind("curl_easy_init", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle PERFORM = bind("curl_easy_perform",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle CLEANUP = bind("curl_easy_cleanup",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle ERROR = bind("curl_easy_strerror",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle SET_POINTER = Linker.nativeLinker().downcallHandle(
        LIBRARY.findOrThrow("curl_easy_setopt"), FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS), Linker.Option.firstVariadicArg(2));
    private static final MethodHandle SET_LONG = Linker.nativeLinker().downcallHandle(
        LIBRARY.findOrThrow("curl_easy_setopt"), FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG), Linker.Option.firstVariadicArg(2));
    private static final MethodHandle WRITE = writer();

    private static MethodHandle bind(String name, FunctionDescriptor descriptor) {
      return Linker.nativeLinker().downcallHandle(LIBRARY.findOrThrow(name), descriptor);
    }

    private static SymbolLookup library() {
      SymbolLookup symbols = SymbolLookup.libraryLookup("libcurl.so.4", Arena.global());
      try {
        int status = (int) Linker.nativeLinker().downcallHandle(symbols.findOrThrow("curl_global_init"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)).invokeExact(3L);
        if (status != 0) throw new IllegalStateException("Cannot initialize libcurl");
        return symbols;
      } catch (Error error) {
        throw error;
      } catch (Throwable failure) {
        throw new IllegalStateException("libcurl initialization failed", failure);
      }
    }

    private static MethodHandle writer() {
      try {
        MethodHandle writer = MethodHandles.lookup().findVirtual(Sink.class, "write", MethodType.methodType(
            long.class, MemorySegment.class, long.class, long.class));
        // libcurl supplies a context pointer; this bound receiver already owns it.
        return MethodHandles.dropArguments(writer, 4, MemorySegment.class);
      } catch (NoSuchMethodException | IllegalAccessException failure) {
        throw new IllegalStateException("Cannot bind curl response writer", failure);
      }
    }
  }
}
