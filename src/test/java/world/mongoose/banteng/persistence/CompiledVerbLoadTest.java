package world.mongoose.banteng.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

final class CompiledVerbLoadTest {
  @Test
  void persistedSourceCompilesWithoutReplacingItsSpelling() throws Exception {
    String source = " /* persisted comment */ return 7;";
    var verb = new RawVerb("work", 1, 12, -1).compile(source);
    assertEquals(source, verb.programSource());
    assertFalse(verb.program().instructions().isEmpty());
  }

  @Test
  void invalidPersistedProgramIsAnIoFailure() {
    IOException error = assertThrows(IOException.class,
        () -> new RawVerb("work", 1, 12, -1).compile("if ("));
    assertInstanceOf(IllegalArgumentException.class, error.getCause());
  }
}
