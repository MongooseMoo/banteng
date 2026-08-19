package moo.server;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import moo.builtin.BuiltinCatalog.ListenerDescription;
import moo.builtin.BuiltinCatalog.ListenerControl;
import moo.logging.ServerLog;
import moo.persistence.LambdaMooV17Codec;
import moo.runtime.MooRuntime;
import moo.value.MooValue;
import moo.value.MooValue.IntegerValue;
import moo.value.MooValue.MapValue;
import moo.value.MooValue.StringValue;
import moo.value.ValueSemantics;
import moo.world.WorldTxn;

/** The concrete blocking socket server for the first managed vertical slice. */
public final class MooServer implements AutoCloseable, ListenerControl {
  private final MooRuntime runtime;
  private final ConnectionRegistry connectionRegistry = new ConnectionRegistry();
  private final InetAddress listenAddress;
  private final ServerSocket primaryListener;
  private final Listener primary;
  private final int primaryPort;
  private final Map<Integer, Listener> listeners = new ConcurrentHashMap<>();
  private final Map<Long, Socket> connections = new ConcurrentHashMap<>();
  private final Map<Long, BufferedWriter> outputs = new ConcurrentHashMap<>();
  private final Map<Long, Boolean> binaryConnections = new ConcurrentHashMap<>();
  private final AtomicBoolean serving = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final CountDownLatch closedLatch = new CountDownLatch(1);
  private final AtomicLong nextConnectionId = new AtomicLong(-2);

  /** Binds the configured address and port. Port zero requests an ephemeral test port. */
  public MooServer(String address, int port, WorldTxn world, Path checkpoint) throws IOException {
    this(address, port, world, checkpoint, List.of(), List.of());
  }

  /** Binds the listener and restores delayed fork tasks from the loaded checkpoint. */
  public MooServer(
      String address,
      int port,
      WorldTxn world,
      Path checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<LambdaMooV17Codec.ActiveConnection> activeConnections)
      throws IOException {
    this(
        address,
        port,
        world,
        Optional.empty(),
        checkpoint,
        restoredTasks,
        activeConnections,
        ServerLog.stderr(System.Logger.Level.INFO),
        ValueSemantics.STANDARD);
  }

  /** Binds the listener with distinct loaded and checkpoint database files. */
  public MooServer(
      String address,
      int port,
      WorldTxn world,
      Path database,
      Path checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<LambdaMooV17Codec.ActiveConnection> activeConnections)
      throws IOException {
    this(
        address,
        port,
        world,
        database,
        checkpoint,
        restoredTasks,
        activeConnections,
        ValueSemantics.STANDARD);
  }

  /** Binds the listener with distinct files and selected value semantics. */
  public MooServer(
      String address,
      int port,
      WorldTxn world,
      Path database,
      Path checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<LambdaMooV17Codec.ActiveConnection> activeConnections,
      ValueSemantics valueSemantics)
      throws IOException {
    this(
        address,
        port,
        world,
        Optional.of(Objects.requireNonNull(database, "database")),
        checkpoint,
        restoredTasks,
        activeConnections,
        ServerLog.stderr(System.Logger.Level.INFO),
        valueSemantics);
  }

  /** Binds the listener with distinct database files and the shared server log. */
  public MooServer(
      String address,
      int port,
      WorldTxn world,
      Path database,
      Path checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<LambdaMooV17Codec.ActiveConnection> activeConnections,
      ServerLog serverLog)
      throws IOException {
    this(
        address,
        port,
        world,
        Optional.of(Objects.requireNonNull(database, "database")),
        checkpoint,
        restoredTasks,
        activeConnections,
        serverLog,
        ValueSemantics.STANDARD);
  }

  /** Binds the listener with shared logging and selected value semantics. */
  public MooServer(
      String address,
      int port,
      WorldTxn world,
      Path database,
      Path checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<LambdaMooV17Codec.ActiveConnection> activeConnections,
      ServerLog serverLog,
      ValueSemantics valueSemantics)
      throws IOException {
    this(
        address,
        port,
        world,
        Optional.of(Objects.requireNonNull(database, "database")),
        checkpoint,
        restoredTasks,
        activeConnections,
        serverLog,
        valueSemantics);
  }

  private MooServer(
      String address,
      int port,
      WorldTxn world,
      Optional<Path> database,
      Path checkpoint,
      List<LambdaMooV17Codec.DurableTask> restoredTasks,
      List<LambdaMooV17Codec.ActiveConnection> activeConnections,
      ServerLog serverLog,
      ValueSemantics valueSemantics)
      throws IOException {
    listenAddress = InetAddress.getByName(address);
    primaryListener = new ServerSocket();
    WorldTxn runtimeWorld = Objects.requireNonNull(world, "world");
    Path checkpointPath = Objects.requireNonNull(checkpoint, "checkpoint");
    List<LambdaMooV17Codec.DurableTask> tasks =
        Objects.requireNonNull(restoredTasks, "restoredTasks");
    List<LambdaMooV17Codec.ActiveConnection> connectionsToRestore =
        Objects.requireNonNull(activeConnections, "activeConnections");
    ServerLog runtimeLog = Objects.requireNonNull(serverLog, "serverLog");
    runtime =
        database.isPresent()
            ? new MooRuntime(
                runtimeWorld,
                this,
                database.orElseThrow(),
                checkpointPath,
                tasks,
                connectionsToRestore,
                runtimeLog,
                valueSemantics,
                connectionRegistry)
            : new MooRuntime(
                runtimeWorld,
                this,
                checkpointPath,
                tasks,
                connectionsToRestore,
                runtimeLog,
                valueSemantics,
                connectionRegistry);
    try {
      primaryListener.bind(new InetSocketAddress(listenAddress, port));
    } catch (IOException | RuntimeException failure) {
      try {
        primaryListener.close();
      } catch (IOException closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
    primaryPort = primaryListener.getLocalPort();
    primary =
        new Listener(
            primaryListener,
            0,
            primaryPort,
            listenAddress.getAddress().length == 16,
            true,
            listenAddress.getHostAddress());
    listeners.put(primaryPort, primary);
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
      runtime.startServer();
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
        InputStream input = socket.getInputStream();
        BufferedWriter output =
            new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StringValue.charset()))) {
      outputs.put(connectionId, output);
      List<String> initialOutput =
          runtime.openConnection(
              connectionId, listener.handler, listener.printMessages, connectionInfo(socket, false));
      opened = true;
      writeLines(output, initialOutput);
      ByteArrayOutputStream line = new ByteArrayOutputStream();
      boolean afterCarriageReturn = false;
      boolean afterIac = false;
      int negotiationCommand = -1;
      ByteArrayOutputStream subnegotiation = null;
      boolean afterSubnegotiationIac = false;
      byte[] inputBuffer = new byte[1024];
      int inputCount;
      while ((inputCount = input.read(inputBuffer)) != -1) {
        if (binaryConnections.containsKey(connectionId)) {
          writeLines(
              output,
              runtime.executeLine(
                  connectionId,
                  new String(inputBuffer, 0, inputCount, StringValue.charset())));
          continue;
        }
        for (int inputIndex = 0; inputIndex < inputCount; inputIndex++) {
          int inputByte = Byte.toUnsignedInt(inputBuffer[inputIndex]);
          if (subnegotiation != null) {
            subnegotiation.write(inputByte);
            if (afterSubnegotiationIac) {
              afterSubnegotiationIac = false;
              if (inputByte == 0xF0) {
                StringBuilder encodedCommand = new StringBuilder();
                for (byte commandByte : subnegotiation.toByteArray()) {
                  int unsignedByte = Byte.toUnsignedInt(commandByte);
                  if (unsignedByte != '~'
                      && (unsignedByte == ' ' || (unsignedByte >= 33 && unsignedByte <= 126))) {
                    encodedCommand.append((char) unsignedByte);
                  } else {
                    encodedCommand.append("~%02X".formatted(unsignedByte));
                  }
                }
                subnegotiation = null;
                writeLines(
                    output,
                    runtime.executeTransportOutOfBand(connectionId, encodedCommand.toString()));
              }
            } else if (inputByte == 0xFF) {
              afterSubnegotiationIac = true;
            }
            continue;
          }
          if (negotiationCommand >= 0) {
            int completedCommand = negotiationCommand;
            negotiationCommand = -1;
            writeLines(
                output,
                runtime.executeTransportOutOfBand(
                    connectionId, "~FF~%02X~%02X".formatted(completedCommand, inputByte)));
            continue;
          }
          if (afterIac) {
            afterIac = false;
            if (inputByte >= 0xFB && inputByte <= 0xFE) {
              negotiationCommand = inputByte;
            } else if (inputByte == 0xFA) {
              subnegotiation = new ByteArrayOutputStream();
              subnegotiation.write(0xFF);
              subnegotiation.write(0xFA);
            } else if (inputByte == 0xF1) {
              writeLines(output, runtime.executeTransportOutOfBand(connectionId, "~FF~F1"));
            }
            continue;
          }
          if (inputByte == 0xFF) {
            afterIac = true;
            continue;
          }
          if (inputByte == '\r') {
            writeLines(
                output,
                runtime.executeLine(connectionId, line.toString(StringValue.charset())));
            line.reset();
            afterCarriageReturn = true;
            continue;
          }
          if (inputByte == '\n') {
            if (afterCarriageReturn) {
              afterCarriageReturn = false;
              continue;
            }
            writeLines(
                output,
                runtime.executeLine(connectionId, line.toString(StringValue.charset())));
            line.reset();
            continue;
          }
          afterCarriageReturn = false;
          line.write(inputByte);
        }
      }
      if (line.size() > 0) {
        writeLines(
            output, runtime.executeLine(connectionId, line.toString(StringValue.charset())));
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
      binaryConnections.remove(connectionId);
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

  private static MapValue connectionInfo(Socket socket, boolean outbound) {
    Map<MooValue, MooValue> connectionInfo = new LinkedHashMap<>();
    String sourceAddress = socket.getLocalAddress().getHostAddress();
    String destinationAddress = socket.getInetAddress().getHostAddress();
    connectionInfo.put(StringValue.of("source_address"), StringValue.of(sourceAddress));
    connectionInfo.put(StringValue.of("source_ip"), StringValue.of(sourceAddress));
    connectionInfo.put(StringValue.of("source_port"), new IntegerValue(socket.getLocalPort()));
    connectionInfo.put(StringValue.of("destination_address"), StringValue.of(destinationAddress));
    connectionInfo.put(StringValue.of("destination_ip"), StringValue.of(destinationAddress));
    connectionInfo.put(StringValue.of("destination_port"), new IntegerValue(socket.getPort()));
    connectionInfo.put(
        StringValue.of("protocol"),
        StringValue.of(socket.getInetAddress().getAddress().length == 16 ? "IPv6" : "IPv4"));
    connectionInfo.put(StringValue.of("outbound"), new IntegerValue(outbound ? 1 : 0));
    connectionInfo.put(
        StringValue.of("TLS"), new MapValue(Map.of(StringValue.of("active"), new IntegerValue(0))));
    return new MapValue(connectionInfo);
  }

  /** Binds and starts a dynamic listener owned by one MOO object. */
  @Override
  public synchronized int listen(
      long handler,
      int description,
      boolean ipv6,
      boolean printMessages,
      String interfaceAddress)
      throws IOException {
    if (closed.get()) {
      throw new IllegalArgumentException("server is closed");
    }
    if (listeners.containsKey(description)
        || listeners.values().stream()
            .anyMatch(listener -> listener.description == description && listener.ipv6 == ipv6)) {
      throw new IllegalArgumentException("listener already exists for description " + description);
    }
    ServerSocket socket = new ServerSocket();
    try {
      InetAddress bindAddress =
          interfaceAddress.isEmpty()
              ? (ipv6 ? InetAddress.getAllByName("::1")[0] : listenAddress)
              : InetAddress.getByName(interfaceAddress);
      if ((bindAddress.getAddress().length == 16) != ipv6) {
        throw new IllegalArgumentException("listener interface address family does not match ipv6");
      }
      socket.bind(new InetSocketAddress(bindAddress, description));
      int descriptor = socket.getLocalPort();
      Listener listener =
          new Listener(
              socket,
              handler,
              description,
              ipv6,
              printMessages,
              bindAddress.getHostAddress());
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

  /** Returns a stable snapshot of the active listener inventory. */
  @Override
  public synchronized List<ListenerDescription> listeners() {
    List<ListenerDescription> result = new ArrayList<>();
    for (Listener listener : listeners.values()) {
      result.add(
          new ListenerDescription(
              listener.handler,
              listener.description,
              listener.socket.getLocalPort(),
              listener.ipv6,
              listener.printMessages,
              listener.interfaceAddress));
    }
    result.sort(Comparator.comparingInt(ListenerDescription::port));
    return List.copyOf(result);
  }

  /** Closes one dynamic listener without closing its accepted connections. */
  @Override
  public synchronized boolean unlisten(int description, boolean ipv6) {
    Map.Entry<Integer, Listener> selected =
        listeners.entrySet().stream()
            .filter(entry -> !Objects.equals(entry.getValue(), primary))
            .filter(entry -> entry.getValue().description == description)
            .filter(entry -> entry.getValue().ipv6 == ipv6)
            .findFirst()
            .orElse(null);
    if (selected == null) {
      return false;
    }
    Listener listener = selected.getValue();
    try {
      listener.socket.close();
      listeners.remove(selected.getKey(), listener);
      return true;
    } catch (IOException error) {
      return false;
    }
  }

  /** Opens an outbound socket and registers its negative MOO connection object atomically. */
  @Override
  public long openNetworkConnection(String host, int port, boolean ipv6, long listenerHandler)
      throws IOException {
    if (closed.get() || port < 0 || port > 65_535) {
      throw new IllegalArgumentException("invalid outbound connection");
    }
    InetAddress remote = null;
    for (InetAddress candidate : InetAddress.getAllByName(host)) {
      if ((candidate.getAddress().length == 16) == ipv6) {
        remote = candidate;
        break;
      }
    }
    if (remote == null) {
      throw new IllegalArgumentException("host has no requested address family");
    }
    Socket socket = new Socket();
    long connectionId = nextConnectionId.getAndDecrement();
    try {
      socket.connect(new InetSocketAddress(remote, port));
      BufferedWriter output =
          new BufferedWriter(
              new OutputStreamWriter(socket.getOutputStream(), StringValue.charset()));
      connections.put(connectionId, socket);
      outputs.put(connectionId, output);
      runtime.registerOutboundConnection(
          connectionId, listenerHandler, connectionInfo(socket, true));
      return connectionId;
    } catch (IOException | RuntimeException failure) {
      outputs.remove(connectionId);
      connections.remove(connectionId, socket);
      closeSocket(socket);
      throw failure;
    }
  }

  /** Writes ordered lines to one accepted socket without closing it. */
  @Override
  public void writeConnection(long connectionId, List<String> lines) {
    BufferedWriter output = outputs.get(connectionId);
    if (output == null) {
      return;
    }
    try {
      writeLines(output, lines);
    } catch (IOException ignored) {
      // The connection reader owns physical cleanup after a failed write.
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

  /** Selects delimiter-free binary reads for one accepted socket. */
  @Override
  public void setConnectionBinary(long connectionId, boolean binary) {
    if (binary) {
      binaryConnections.put(connectionId, Boolean.TRUE);
    } else {
      binaryConnections.remove(connectionId);
    }
  }

  /** Output is flushed synchronously, so no bytes remain queued between writes. */
  @Override
  public long bufferedOutputLength(long connectionId) {
    return 0L;
  }

  /** Closes the production server after a committed shutdown checkpoint. */
  @Override
  public void shutdown() {
    closeTransport()
        .ifPresent(
            failure -> {
              throw new UncheckedIOException("server shutdown failed", failure);
            });
  }

  /** Requests a final checkpoint, then waits for listener and executor termination. */
  public void gracefulShutdown() {
    boolean requested = false;
    if (!closed.get()) {
      runtime.requestGracefulShutdown();
      requested = true;
    }
    try {
      if (!closedLatch.await(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("graceful shutdown did not close the server");
      }
      if (requested) {
        runtime.close();
      }
      if (!runtime.awaitTermination(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("graceful shutdown did not terminate VM workers");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("graceful shutdown interrupted", interrupted);
    }
  }

  /** Aborts the process after the runtime has durably published its panic database. */
  @Override
  public void panic() {
    try {
      int dumpableResult =
          (int)
              NativeAbort.PRCTL.invokeExact(
                  NativeAbort.PR_SET_DUMPABLE, 0L, 0L, 0L, 0L);
      if (dumpableResult != 0) {
        Runtime.getRuntime().halt(134);
      }
      MemorySegment previous =
          (MemorySegment) NativeAbort.SIGNAL.invokeExact(NativeAbort.SIGABRT, MemorySegment.NULL);
      if (previous.equals(MemorySegment.ofAddress(-1))) {
        Runtime.getRuntime().halt(134);
      }
      int result = (int) NativeAbort.RAISE.invokeExact(NativeAbort.SIGABRT);
      Runtime.getRuntime().halt(result == 0 ? 134 : 135);
    } catch (Throwable failure) {
      Runtime.getRuntime().halt(134);
    }
  }

  private static final class NativeAbort {
    private static final int SIGABRT = 6;
    private static final int PR_SET_DUMPABLE = 4;

    @SuppressWarnings("restricted")
    private static final MethodHandle PRCTL =
        Linker.nativeLinker()
            .downcallHandle(
                Linker.nativeLinker().defaultLookup().findOrThrow("prctl"),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG));

    @SuppressWarnings("restricted")
    private static final MethodHandle SIGNAL =
        Linker.nativeLinker()
            .downcallHandle(
                Linker.nativeLinker().defaultLookup().findOrThrow("signal"),
                FunctionDescriptor.of(
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    @SuppressWarnings("restricted")
    private static final MethodHandle RAISE =
        Linker.nativeLinker()
            .downcallHandle(
                Linker.nativeLinker().defaultLookup().findOrThrow("raise"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

    private NativeAbort() {}
  }

  /** Closes the listener and every accepted socket. */
  @Override
  public synchronized void close() throws IOException {
    Optional<IOException> failure = closeTransport();
    runtime.close();
    if (failure.isPresent()) {
      throw failure.orElseThrow();
    }
  }

  private synchronized Optional<IOException> closeTransport() {
    if (!closed.compareAndSet(false, true)) {
      return Optional.empty();
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
    closedLatch.countDown();
    return Optional.ofNullable(failure);
  }

  private static void closeSocket(Socket socket) {
    try {
      socket.close();
    } catch (IOException ignored) {
      // The server is already closing; there is no remaining socket state to preserve.
    }
  }

  private record Listener(
      ServerSocket socket,
      long handler,
      int description,
      boolean ipv6,
      boolean printMessages,
      String interfaceAddress) {
    private Listener {
      Objects.requireNonNull(socket, "socket");
      Objects.requireNonNull(interfaceAddress, "interfaceAddress");
    }
  }
}
