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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import moo.builtin.BuiltinCatalog.ListenerControl;
import moo.runtime.MooRuntime;
import moo.value.MooValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.StringValue;
import moo.world.WorldTxn;

/** The concrete blocking socket server for the first managed vertical slice. */
public final class MooServer implements AutoCloseable, ListenerControl {
  private final MooRuntime runtime;
  private final InetAddress listenAddress;
  private final ServerSocket primaryListener;
  private final Listener primary;
  private final int primaryPort;
  private final Map<Integer, Listener> listeners = new ConcurrentHashMap<>();
  private final Map<Long, Socket> connections = new ConcurrentHashMap<>();
  private final Map<Long, BufferedWriter> outputs = new ConcurrentHashMap<>();
  private final AtomicBoolean serving = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicLong nextConnectionId = new AtomicLong(-2);

  /** Binds the configured address and port. Port zero requests an ephemeral test port. */
  public MooServer(String address, int port, WorldTxn world) throws IOException {
    listenAddress = InetAddress.getByName(address);
    primaryListener = new ServerSocket();
    primaryListener.bind(new InetSocketAddress(listenAddress, port));
    primaryPort = primaryListener.getLocalPort();
    primary = new Listener(primaryListener, 0, true);
    listeners.put(primaryPort, primary);
    runtime = new MooRuntime(Objects.requireNonNull(world, "world"), this);
  }

  /** Returns the bound port, including the assigned ephemeral port in tests. */
  public int port() {
    return primaryPort;
  }

  /** Accepts connections until the server is closed. */
  public void serve() {
    if (!serving.compareAndSet(false, true)) {
      throw new IllegalStateException("server is already serving");
    }
    try {
      acceptConnections(primary);
    } finally {
      serving.set(false);
    }
  }

  private void acceptConnections(Listener listener) {
    while (!closed.get()
        && Objects.equals(listeners.get(listener.socket.getLocalPort()), listener)) {
      Socket socket;
      try {
        socket = listener.socket.accept();
      } catch (SocketException error) {
        if (closed.get() || listener.socket.isClosed()) {
          return;
        }
        throw new UncheckedIOException(error);
      } catch (IOException error) {
        throw new UncheckedIOException(error);
      }

      long connectionId = nextConnectionId.getAndDecrement();
      connections.put(connectionId, socket);
      if (closed.get()) {
        closeSocket(socket);
        connections.remove(connectionId, socket);
        return;
      }
      Thread.startVirtualThread(() -> handleConnection(socket, connectionId, listener));
    }
  }

  private void handleConnection(Socket socket, long connectionId, Listener listener) {
    boolean opened = false;
    try (socket;
        BufferedReader input =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
        BufferedWriter output =
            new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1))) {
      outputs.put(connectionId, output);
      Map<MooValue, MooValue> connectionInfo = new LinkedHashMap<>();
      String sourceAddress = socket.getLocalAddress().getHostAddress();
      String destinationAddress = socket.getInetAddress().getHostAddress();
      connectionInfo.put(encode("source_address"), encode(sourceAddress));
      connectionInfo.put(encode("source_ip"), encode(sourceAddress));
      connectionInfo.put(encode("source_port"), new IntegerValue(socket.getLocalPort()));
      connectionInfo.put(encode("destination_address"), encode(destinationAddress));
      connectionInfo.put(encode("destination_ip"), encode(destinationAddress));
      connectionInfo.put(encode("destination_port"), new IntegerValue(socket.getPort()));
      connectionInfo.put(
          encode("protocol"),
          encode(socket.getInetAddress().getAddress().length == 16 ? "IPv6" : "IPv4"));
      connectionInfo.put(encode("outbound"), new IntegerValue(0));
      List<String> initialOutput =
          runtime.openConnection(
              connectionId, listener.handler, listener.printMessages, new MapValue(connectionInfo));
      opened = true;
      writeLines(output, initialOutput);
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
      outputs.remove(connectionId);
      connections.remove(connectionId, socket);
    }
  }

  private static void writeLines(BufferedWriter output, List<String> lines) throws IOException {
    synchronized (output) {
      for (String line : lines) {
        output.write(line);
        output.write("\r\n");
      }
      output.flush();
    }
  }

  private static StringValue encode(String value) {
    return new StringValue(value.getBytes(StandardCharsets.ISO_8859_1));
  }

  /** Binds and starts a dynamic listener owned by one MOO object. */
  @Override
  public synchronized int listen(long handler, int port, boolean printMessages) throws IOException {
    if (closed.get()) {
      throw new IllegalArgumentException("server is closed");
    }
    if (listeners.containsKey(port)) {
      throw new IllegalArgumentException("listener already exists on port " + port);
    }
    ServerSocket socket = new ServerSocket();
    try {
      socket.bind(new InetSocketAddress(listenAddress, port));
      Listener listener = new Listener(socket, handler, printMessages);
      int descriptor = socket.getLocalPort();
      if (listeners.putIfAbsent(descriptor, listener) != null) {
        throw new IllegalArgumentException("listener already exists on port " + descriptor);
      }
      Thread.startVirtualThread(() -> acceptConnections(listener));
      return descriptor;
    } catch (IOException | RuntimeException error) {
      try {
        socket.close();
      } catch (IOException closeError) {
        error.addSuppressed(closeError);
      }
      throw error;
    }
  }

  /** Closes one dynamic listener without closing its accepted connections. */
  @Override
  public synchronized boolean unlisten(int port) {
    if (port == primaryPort) {
      return false;
    }
    Listener listener = listeners.get(port);
    if (listener == null) {
      return false;
    }
    try {
      listener.socket.close();
      listeners.remove(port, listener);
      return true;
    } catch (IOException error) {
      return false;
    }
  }

  /** Writes the final boot message and closes one accepted socket. */
  @Override
  public void bootConnection(long connectionId, List<String> lines) {
    Socket socket = connections.get(connectionId);
    BufferedWriter output = outputs.get(connectionId);
    if (socket == null) {
      return;
    }
    try {
      if (output != null) {
        writeLines(output, lines);
      }
    } catch (IOException ignored) {
      // The logical connection is already gone; closing the socket completes the boot.
    } finally {
      closeSocket(socket);
    }
  }

  /** Closes the listener and every accepted socket. */
  @Override
  public synchronized void close() throws IOException {
    if (!closed.compareAndSet(false, true)) {
      return;
    }

    IOException failure = null;
    for (Listener listener : listeners.values()) {
      try {
        listener.socket.close();
      } catch (IOException error) {
        if (failure == null) {
          failure = error;
        } else {
          failure.addSuppressed(error);
        }
      }
    }
    listeners.clear();
    for (Socket connection : connections.values()) {
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

  private record Listener(ServerSocket socket, long handler, boolean printMessages) {
    private Listener {
      Objects.requireNonNull(socket, "socket");
    }
  }
}
