package world.mongoose.banteng.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import world.mongoose.banteng.builtin.BuiltinHosts;
import world.mongoose.banteng.persistence.LambdaMooV5Reader;
import world.mongoose.banteng.runtime.MooRuntime;
import world.mongoose.banteng.server.MooServer;
import org.junit.jupiter.api.Test;

final class ServerLogRoutingTest {
  @Test
  void everyServerDiagnosticOwnerAcceptsTheSharedLog() {
    assertTrue(hasServerLogField(LambdaMooV5Reader.class));
    assertTrue(hasServerLogField(BuiltinHosts.class));
    assertTrue(hasServerLogField(MooRuntime.class));
    assertTrue(hasServerLogParameter(MooServer.class));
  }

  @Test
  void systemErrorIsOwnedOnlyByServerLog() throws IOException {
    Path sourceRoot = Path.of("src", "main", "java");
    List<Path> owners;
    try (var sources = Files.walk(sourceRoot)) {
      owners =
          sources
              .filter(path -> path.toString().endsWith(".java"))
              .filter(ServerLogRoutingTest::usesSystemError)
              .map(sourceRoot::relativize)
              .toList();
    }

    assertEquals(List.of(Path.of("moo", "logging", "ServerLog.java")), owners);
  }

  private static boolean hasServerLogField(Class<?> owner) {
    for (Field field : owner.getDeclaredFields()) {
      if (field.getType().equals(ServerLog.class)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasServerLogParameter(Class<?> owner) {
    for (Constructor<?> constructor : owner.getConstructors()) {
      for (Class<?> parameter : constructor.getParameterTypes()) {
        if (parameter.equals(ServerLog.class)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean usesSystemError(Path source) {
    try {
      return Files.readString(source).contains("System.err");
    } catch (IOException failure) {
      throw new IllegalStateException("failed to inspect " + source, failure);
    }
  }
}
