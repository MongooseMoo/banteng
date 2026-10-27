package world.mongoose.banteng.builtin.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import world.mongoose.banteng.builtin.BuiltinResult;
import world.mongoose.banteng.logging.ServerLog;
import world.mongoose.banteng.value.MooValue;
import world.mongoose.banteng.value.MooValue.ErrorValue;
import world.mongoose.banteng.value.MooValue.IntegerValue;
import world.mongoose.banteng.value.MooValue.StringValue;

final class CryptoBuiltinsTest {
  @Test
  void argon2VerificationSeparatesMismatchFromInternalFailure(@TempDir Path directory)
      throws Exception {
    List<MooValue> arguments =
        List.of(
            StringValue.of("$argon2id$v=19$m=8,t=1,p=1$c2FsdA$AQID"), StringValue.of("password"));
    Path logFile = directory.resolve("server.log");
    try (ServerLog log =
        ServerLog.open(System.Logger.Level.ERROR, Optional.of(logFile))) {
      BuiltinResult mismatch =
          CryptoBuiltins.argon2Verify(
              arguments,
              log,
              (password, salt, iterations, memory, parallelism, length) -> new byte[length]);
      assertEquals(new IntegerValue(0), ((BuiltinResult.Value) mismatch).value());

      BuiltinResult internalFailure =
          CryptoBuiltins.argon2Verify(
              arguments,
              log,
              (password, salt, iterations, memory, parallelism, length) -> {
                throw new IllegalStateException("engine failed");
              });
      assertEquals(ErrorValue.E_QUOTA, ((BuiltinResult.ErrorResult) internalFailure).error());
    }

    String logged = Files.readString(logFile);
    assertTrue(logged.contains("ARGON2 VERIFY: internal failure: IllegalStateException"));
    assertFalse(logged.contains("password"));
  }
}
