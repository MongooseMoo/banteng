package world.mongoose.banteng.app;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Transfers external POSIX signals to a Java thread without a native Java upcall. */
final class ProcessSignals implements AutoCloseable {
  private final MethodHandle next;
  private final MethodHandle stop;
  private final Thread dispatcher;

  @SuppressWarnings("restricted")
  ProcessSignals(IntConsumer action) throws IOException {
    Path library = Files.createTempFile("banteng-signals-", ".so");
    SymbolLookup symbols;
    try {
      try (InputStream resource = Objects.requireNonNull(
          ProcessSignals.class.getResourceAsStream("/libbanteng-signals.so"),
          "native signal bridge is missing from the distribution")) {
        Files.copy(resource, library, StandardCopyOption.REPLACE_EXISTING);
      }
      // Native handlers can still be in flight after restoration. Keep code mapped
      // until process exit; deleting the pathname does not unmap the loaded library.
      symbols = SymbolLookup.libraryLookup(library, Arena.global());
    } finally {
      Files.deleteIfExists(library);
    }
    Linker linker = Linker.nativeLinker();
    FunctionDescriptor signature = FunctionDescriptor.of(ValueLayout.JAVA_INT);
    MethodHandle install = linker.downcallHandle(symbols.findOrThrow("banteng_signals_install"), signature);
    next = linker.downcallHandle(symbols.findOrThrow("banteng_signals_next"), signature);
    stop = linker.downcallHandle(symbols.findOrThrow("banteng_signals_stop"), signature);
    int result = invoke(install);
    if (result != 0) throw new IOException("cannot install process signals: errno " + -result);
    dispatcher = Thread.ofPlatform().daemon().name("banteng-signals").start(() -> {
      while (true) {
        int signal = invoke(next);
        if (signal == 0) return;
        if (signal < 0) throw new IllegalStateException("signal pipe read failed: errno " + -signal);
        action.accept(signal);
      }
    });
  }

  private static int invoke(MethodHandle operation) {
    try {
      return (int) operation.invokeExact();
    } catch (Throwable failure) {
      throw new IllegalStateException("native signal operation failed", failure);
    }
  }

  @Override
  public void close() {
    int result = invoke(stop);
    if (result != 0) throw new IllegalStateException("cannot restore process signals: errno " + -result);
    try {
      dispatcher.join();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("signal dispatcher shutdown interrupted", interrupted);
    }
  }
}
