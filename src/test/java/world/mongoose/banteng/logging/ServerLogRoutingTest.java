package world.mongoose.banteng.logging;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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

}
