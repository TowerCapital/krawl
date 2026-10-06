import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persistent TCP connections for peers discovered by PeerListener.
 * UDP is still used for discovery; chat and file data use TCP.
 */
public final class PeerConnectionManager implements AutoCloseable {

    private static final int HELLO = 1;
    private static final int TEXT = 2;
    private static final int FILE_START = 3;
    private static final int FILE_CHUNK = 4;
    private static final int FILE_END = 5;

    private static final int CHUNK_SIZE = 32 * 1024;
    private static final int MAX_FRAME_BYTES = 1024 * 1024;
    private static final long MAX_FILE_BYTES = 1024L * 1024L * 1024L;

    public interface Listener {
        default void onConnected(String userId) {}
        default void onDisconnected(String userId) {}
        default void onText(String userId, String message) {}
        default void onFileReceived(String userId, Path file) {}
        default void onError(String userId, Exception error) {}
    }

    private final String myUserId;
    private final int tcpPort;
    private final Path receiveDirectory;
    private final Listener listener;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final Map<String, Boolean> connecting = new ConcurrentHashMap<>();
    private final ExecutorService connectorPool = Executors.newFixedThreadPool(4);

    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    public PeerConnectionManager(
            String myUserId,
            int tcpPort,
            Path receiveDirectory,
            Listener listener
    ) {
        this.myUserId = myUserId;
        this.tcpPort = tcpPort;
        this.receiveDirectory = receiveDirectory;
        this.listener = listener == null ? new Listener() {} : listener;
    }

    /** Starts the TCP listener. Call once before connecting to peers. */
    public synchronized void start() throws IOException {
        if (running) return;

        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(tcpPort));
        serverSocket = server;
        running = true;

        Thread acceptThread =
                new Thread(() -> acceptLoop(server), "tcp-accept-" + myUserId);
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /**
     * Call this for each discovered peer. It connects in the background and
     * ignores duplicate attempts.
     *
     * Only the lexicographically smaller user ID starts the connection.
     * The other peer accepts it, avoiding duplicate connections.
     */
    public void connectTo(PeerListener.PeerInfo peer) {
        if (!running) throw new IllegalStateException("Call start() first.");
        if (peer == null || peer.userId.equals(myUserId)) return;
        if (myUserId.compareTo(peer.userId) > 0) return;
        if (connections.containsKey(peer.userId)) return;
        if (connecting.putIfAbsent(peer.userId, Boolean.TRUE) != null) return;

        connectorPool.execute(() -> {
            Socket socket = new Socket();
            try {
                socket.connect(
                        new InetSocketAddress(peer.address, peer.tcpPort),
                        3000
                );
                prepareConnection(socket, peer.userId);
            } catch (Exception e) {
                closeQuietly(socket);
                connecting.remove(peer.userId);
                if (running) listener.onError(peer.userId, e);
            }
        });
    }

    public boolean isConnected(String userId) {
        return connections.containsKey(userId);
    }

    public void sendText(String userId, String message) throws IOException {
        requireConnection(userId).sendFrame(
                TEXT,
                out -> out.writeUTF(message)
        );
    }

    /**
     * Sends a file in small chunks rather than loading the whole file into RAM.
     * Run this on a worker thread if called from a UI.
     */
    public void sendFile(String userId, Path file) throws IOException {
        Connection connection = requireConnection(userId);
        long size = Files.size(file);

        if (size > MAX_FILE_BYTES) {
            throw new IOException("File exceeds the 1 GiB transfer limit.");
        }

        String transferId = UUID.randomUUID().toString();
        String fileName = file.getFileName().toString();

        connection.sendFrame(FILE_START, out -> {
            out.writeUTF(transferId);
            out.writeUTF(fileName);
            out.writeLong(size);
        });

        byte[] buffer = new byte[CHUNK_SIZE];

        try (InputStream input = Files.newInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                connection.sendFileChunk(transferId, buffer, count);
            }
        }

        connection.sendFrame(FILE_END, out -> out.writeUTF(transferId));
    }

    private Connection requireConnection(String userId) throws IOException {
        Connection connection = connections.get(userId);
        if (connection == null) {
            throw new IOException("Not connected to peer: " + userId);
        }
        return connection;
    }

    private void acceptLoop(ServerSocket server) {
        while (running) {
            try {
                Socket socket = server.accept();
                prepareConnection(socket, null);
            } catch (SocketException e) {
                if (running) listener.onError(null, e);
                break;
            } catch (IOException e) {
                if (running) listener.onError(null, e);
            }
        }
    }

    private void prepareConnection(Socket socket, String expectedUserId)
            throws IOException {
        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(10_000);

            Connection connection = new Connection(socket);
            connection.sendFrame(HELLO, out -> out.writeUTF(myUserId));

            Thread reader = new Thread(
                    () -> connection.readLoop(expectedUserId),
                    "tcp-read-" + myUserId
            );
            reader.setDaemon(true);
            reader.start();
        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }
    }

    private final class Connection {
        private final Socket socket;
        private final DataInputStream input;
        private final DataOutputStream output;
        private final Object writeLock = new Object();
        private final Map<String, IncomingFile> incomingFiles =
                new ConcurrentHashMap<>();

        private volatile String remoteUserId;

        private Connection(Socket socket) throws IOException {
            this.socket = socket;
            this.input = new DataInputStream(socket.getInputStream());
            this.output = new DataOutputStream(socket.getOutputStream());
        }

        private void sendFileChunk(
                String transferId,
                byte[] buffer,
                int count
        ) throws IOException {
            sendFrame(FILE_CHUNK, out -> {
                out.writeUTF(transferId);
                out.writeInt(count);
                out.write(buffer, 0, count);
            });
        }

        private void sendFrame(int type, FrameWriter writer) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream frame = new DataOutputStream(bytes);

            frame.writeByte(type);
            writer.write(frame);
            frame.flush();

            byte[] body = bytes.toByteArray();
            if (body.length < 1 || body.length > MAX_FRAME_BYTES) {
                throw new IOException("Invalid or oversized TCP frame.");
            }

            synchronized (writeLock) {
                output.writeInt(body.length);
                output.write(body);
                output.flush();
            }
        }

        private void readLoop(String expectedUserId) {
            boolean registered = false;

            try {
                Frame hello = readFrame();
                if (hello.type != HELLO) {
                    throw new IOException("Missing peer handshake.");
                }

                String peerId = hello.input.readUTF();

                if (peerId.isEmpty() || peerId.equals(myUserId)) {
                    throw new IOException("Invalid peer identity.");
                }

                if (expectedUserId != null && !expectedUserId.equals(peerId)) {
                    throw new IOException(
                            "Connected peer identity did not match discovery."
                    );
                }

                socket.setSoTimeout(0);

                Connection existing = connections.putIfAbsent(peerId, this);
                if (existing != null) return;

                remoteUserId = peerId;
                registered = true;
                connecting.remove(peerId);
                listener.onConnected(peerId);

                while (running && !socket.isClosed()) {
                    handleFrame(readFrame());
                }
            } catch (EOFException | SocketException ignored) {
                // The peer closed the connection.
            } catch (Exception e) {
                if (running) listener.onError(remoteUserId, e);
            } finally {
                if (expectedUserId != null) {
                    connecting.remove(expectedUserId);
                } else if (remoteUserId != null) {
                    connecting.remove(remoteUserId);
                }

                closeIncomingFiles();
                closeQuietly(socket);

                if (registered) {
                    connections.remove(remoteUserId, this);
                    listener.onDisconnected(remoteUserId);
                }
            }
        }

        private Frame readFrame() throws IOException {
            int length = input.readInt();

            if (length < 1 || length > MAX_FRAME_BYTES) {
                throw new IOException("Invalid TCP frame length: " + length);
            }

            byte[] body = new byte[length];
            input.readFully(body);

            DataInputStream frame =
                    new DataInputStream(new ByteArrayInputStream(body));

            return new Frame(frame.readUnsignedByte(), frame);
        }

        private void handleFrame(Frame frame) throws IOException {
            switch (frame.type) {
                case TEXT:
                    listener.onText(remoteUserId, frame.input.readUTF());
                    break;

                case FILE_START:
                    beginIncomingFile(frame.input);
                    break;

                case FILE_CHUNK:
                    writeIncomingChunk(frame.input);
                    break;

                case FILE_END:
                    finishIncomingFile(frame.input.readUTF());
                    break;

                default:
                    throw new IOException(
                            "Unknown TCP frame type: " + frame.type
                    );
            }
        }

        private void beginIncomingFile(DataInputStream frame)
                throws IOException {
            String transferId = frame.readUTF();
            String originalName = frame.readUTF();
            long size = frame.readLong();

            if (transferId.isEmpty() || size < 0 || size > MAX_FILE_BYTES) {
                throw new IOException("Invalid incoming file metadata.");
            }

            Files.createDirectories(receiveDirectory);

            String safeName = safeFileName(originalName);
            Path destination = receiveDirectory.resolve(
                    UUID.randomUUID() + "_" + safeName
            );

            OutputStream output = Files.newOutputStream(
                    destination,
                    StandardOpenOption.CREATE_NEW
            );

            IncomingFile incoming = new IncomingFile(destination, output, size);

            if (incomingFiles.putIfAbsent(transferId, incoming) != null) {
                output.close();
                Files.deleteIfExists(destination);
                throw new IOException("Duplicate file transfer id.");
            }
        }

        private void writeIncomingChunk(DataInputStream frame)
                throws IOException {
            String transferId = frame.readUTF();
            int count = frame.readInt();

            if (count < 1 || count > CHUNK_SIZE || count > frame.available()) {
                throw new IOException("Invalid incoming file chunk.");
            }

            IncomingFile incoming = incomingFiles.get(transferId);
            if (incoming == null || count > incoming.remaining) {
                throw new IOException("Unexpected file chunk.");
            }

            byte[] chunk = new byte[count];
            frame.readFully(chunk);
            incoming.output.write(chunk);
            incoming.remaining -= count;
        }

        private void finishIncomingFile(String transferId) throws IOException {
            IncomingFile incoming = incomingFiles.remove(transferId);
            if (incoming == null) {
                throw new IOException("Unknown file transfer.");
            }

            if (incoming.remaining != 0) {
                incoming.output.close();
                Files.deleteIfExists(incoming.destination);
                throw new IOException("File ended before all bytes arrived.");
            }

            incoming.output.close();
            listener.onFileReceived(remoteUserId, incoming.destination);
        }

        private void closeIncomingFiles() {
            for (IncomingFile file : incomingFiles.values()) {
                try {
                    file.output.close();
                    Files.deleteIfExists(file.destination);
                } catch (IOException ignored) {
                    // Best-effort cleanup of incomplete transfers.
                }
            }

            incomingFiles.clear();
        }
    }

    private static String safeFileName(String name) {
        String normalized = name.replace('\\', '/');
        String base = normalized
                .substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "_")
                .trim();

        if (base.isEmpty() || base.equals(".") || base.equals("..")) {
            return "received-file";
        }

        return base;
    }

    private static final class Frame {
        private final int type;
        private final DataInputStream input;

        private Frame(int type, DataInputStream input) {
            this.type = type;
            this.input = input;
        }
    }

    private static final class IncomingFile {
        private final Path destination;
        private final OutputStream output;
        private long remaining;

        private IncomingFile(
                Path destination,
                OutputStream output,
                long remaining
        ) {
            this.destination = destination;
            this.output = output;
            this.remaining = remaining;
        }
    }

    @FunctionalInterface
    private interface FrameWriter {
        void write(DataOutputStream output) throws IOException;
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        closeQuietly(serverSocket);

        for (Connection connection : connections.values()) {
            closeQuietly(connection.socket);
        }

        connectorPool.shutdownNow();
    }

    private static void closeQuietly(ServerSocket server) {
        if (server == null) return;

        try {
            server.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }
}