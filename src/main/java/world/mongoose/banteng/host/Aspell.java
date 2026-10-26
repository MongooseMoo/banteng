package world.mongoose.banteng.host;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

/** Process-owned English Aspell dictionary, shared under its native thread-safety boundary. */
public final class Aspell {
  private Aspell() {}

  /** A correct-word status or the dictionary's ordered suggestions. */
  public record Result(int correct, List<String> suggestions) {}

  /** Checks a complete byte string, as aspell_speller_check does. */
  @SuppressWarnings("restricted")
  public static synchronized Result check(byte[] word) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment input = arena.allocate(word.length + 1L);
      MemorySegment.copy(word, 0, input, ValueLayout.JAVA_BYTE, 0, word.length);
      input.set(ValueLayout.JAVA_BYTE, word.length, (byte) 0);
      int correct = (int) Api.CHECK.invokeExact(Api.SPELLER, input, word.length);
      if (correct != 0) return new Result(correct, List.of());
      MemorySegment suggestions = (MemorySegment) Api.SUGGEST.invokeExact(Api.SPELLER, input, word.length);
      MemorySegment elements = (MemorySegment) Api.ELEMENTS.invokeExact(suggestions);
      List<String> result = new ArrayList<>();
      try {
        MemorySegment next = (MemorySegment) Api.NEXT.invokeExact(elements);
        while (!next.equals(MemorySegment.NULL)) {
          result.add(next.reinterpret(Long.MAX_VALUE).getString(0));
          next = (MemorySegment) Api.NEXT.invokeExact(elements);
        }
      } finally {
        Api.DELETE.invokeExact(elements);
      }
      return new Result(0, List.copyOf(result));
    } catch (Error error) {
      throw error;
    } catch (Throwable failure) {
      throw new IllegalStateException("Aspell dictionary call failed", failure);
    }
  }

  @SuppressWarnings("restricted")
  private static final class Api {
    private static final SymbolLookup LIBRARY = SymbolLookup.libraryLookup("libaspell.so.15", Arena.global());
    private static final MethodHandle CHECK = bind("aspell_speller_check",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle SUGGEST = bind("aspell_speller_suggest",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle ELEMENTS = bind("aspell_word_list_elements",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle NEXT = bind("aspell_string_enumeration_next",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle DELETE = bind("delete_aspell_string_enumeration",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MemorySegment SPELLER = initialize();

    private static MethodHandle bind(String symbol, FunctionDescriptor descriptor) {
      return Linker.nativeLinker().downcallHandle(LIBRARY.findOrThrow(symbol), descriptor);
    }

    private static MemorySegment initialize() {
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment config = (MemorySegment) bind("new_aspell_config",
            FunctionDescriptor.of(ValueLayout.ADDRESS)).invokeExact();
        int configured = (int) bind("aspell_config_replace",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
            .invokeExact(config, arena.allocateFrom("lang"), arena.allocateFrom("en_US"));
        if (configured == 0) throw new IllegalStateException("Cannot configure Aspell en_US");
        MemorySegment possibleError = (MemorySegment) bind("new_aspell_speller",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)).invokeExact(config);
        int error = (int) bind("aspell_error_number",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)).invokeExact(possibleError);
        if (error != 0) throw new IllegalStateException("Cannot load Aspell en_US dictionary");
        return (MemorySegment) bind("to_aspell_speller",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)).invokeExact(possibleError);
      } catch (Error error) {
        throw error;
      } catch (Throwable failure) {
        throw new IllegalStateException("Aspell initialization failed", failure);
      }
    }
  }
}
