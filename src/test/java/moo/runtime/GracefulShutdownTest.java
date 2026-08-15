package moo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import moo.persistence.LambdaMooV17Codec;
import moo.persistence.LambdaMooV4Reader;
import moo.server.MooServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GracefulShutdownTest {
  private static final Path FIXTURE =
      Path.of("..", "moo-conformance-tests", "src", "moo_conformance", "_db", "Test.db");

  @Test
  void gracefulShutdownClosesListenersCompletesCheckpointAndTerminatesOwnedThreads(
      @TempDir Path directory) throws Exception {
    Path checkpoint = directory.resolve("graceful.db");
    AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    MooServer server =
        new MooServer("127.0.0.1", 0, new LambdaMooV4Reader().read(FIXTURE), checkpoint);
    Thread serving =
        Thread.ofPlatform()
            .name("banteng-server-test")
            .start(
                () -> {
                  try {
                    server.serve();
                  } catch (Throwable failure) {
                    serverFailure.set(failure);
                  }
                });
    try (Socket probe = new Socket()) {
      probe.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), server.port()));
    }

    server.gracefulShutdown();
    serving.join(TimeUnit.SECONDS.toMillis(3));

    assertFalse(serving.isAlive());
    assertEquals(null, serverFailure.get());
    assertTrue(checkpoint.toFile().isFile());
    assertTrue(new LambdaMooV17Codec().read(checkpoint).world().snapshot().objects().size() > 0);
    try (Socket rejected = new Socket()) {
      assertThrows(
          ConnectException.class,
          () ->
              rejected.connect(
                  new InetSocketAddress(InetAddress.getLoopbackAddress(), server.port())));
    }
    List<String> ownedThreads =
        Thread.getAllStackTraces().keySet().stream()
            .filter(Thread::isAlive)
            .map(Thread::getName)
            .filter(
                name ->
                    name.startsWith("moo-vm-")
                        || name.startsWith("moo-host-wake-")
                        || name.startsWith("moo-timer-wake-")
                        || name.startsWith("banteng-server"))
            .toList();
    assertEquals(List.of(), ownedThreads);
  }
}
