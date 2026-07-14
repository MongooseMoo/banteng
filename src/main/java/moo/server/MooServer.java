package moo.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import moo.runtime.MooRuntime;

/** The concrete blocking socket server for the first managed vertical slice. */
public final class MooServer implements AutoCloseable {
  private final MooRuntime runtime;
  private final ServerSocket listener;
  private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean serving = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();
  private long nextConnectionId = -2;

  /** Binds the configured address and port. Port zero requests an ephemeral test port. */
  public MooServer(String address, int port, MooRuntime runtime) throws IOException {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
    listener = new ServerSocket();
    listener.bind(new InetSocketAddress(InetAddress.getByName(address), port));
  }

  /** Returns the bound port, including the assigned ephemeral port in tests. */
  public int port() {
    return listener.getLocalPort();
  }

  /** Accepts connections until the server is closed. */
  public void serve() {
    if (!serving.compareAndSet(false, true)) {
      throw new IllegalStateException("server is already serving");
    }
    try {
      while (!closed.get()) {
        Socket socket;
        try {
          socket = listener.accept();
        } catch (SocketException error) {
          if (closed.get()) {
            return;
          }
          throw new UncheckedIOException(error);
        } catch (IOException error) {
          throw new UncheckedIOException(error);
        }

        long connectionId = nextConnectionId--;
        connections.add(socket);
        if (closed.get()) {
          closeSocket(socket);
          connections.remove(socket);
          return;
        }
        Thread.startVirtualThread(() -> handleConnection(socket, connectionId));
      }
    } finally {
      serving.set(false);
    }
  }

  private void handleConnection(Socket socket, long connectionId) {
    boolean opened = false;
    try (socket;
        BufferedReader input =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
        BufferedWriter output =
            new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1))) {
      writeLines(output, runtime.openConnection(connectionId));
      opened = true;
      String line;
      while ((line = input.readLine()) != null) {
        writeLines(output, runtime.executeLine(connectionId, line));
      }
    } catch (IOException error) {
      if (!closed.get() && !socket.isClosed()) {
        throw new UncheckedIOException(error);
      }
    } finally {
      if (opened) {
        runtime.closeConnection(connectionId);
      }
      connections.remove(socket);
    }
  }

  private static void writeLines(BufferedWriter output, List<String> lines) throws IOException {
    for (String line : lines) {
      output.write(line);
      output.write("\r\n");
    }
    output.flush();
  }

  /** Closes the listener and every accepted socket. */
  @Override
  public void close() throws IOException {
    if (!closed.compareAndSet(false, true)) {
      return;
    }

    IOException failure = null;
    try {
      listener.close();
    } catch (IOException error) {
      failure = error;
    }
    for (Socket connection : connections) {
      try {
        connection.close();
      } catch (IOException error) {
        if (failure == null) {
          failure = error;
        } else {
          failure.addSuppressed(error);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private static void closeSocket(Socket socket) {
    try {
      socket.close();
    } catch (IOException ignored) {
      // The server is already closing; there is no remaining socket state to preserve.
    }
  }
}
