import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Listens for other MeshLink instances' broadcast packets and
 * maintains a live table of known peers, expiring ones that go quiet.
 */
public class PeerListener implements Runnable {

    private static final int DISCOVERY_PORT = 8888;
    private static final long PEER_TIMEOUT_MS = 15000; // drop peer if silent this long

    private final String myUserId; // so we can ignore our own broadcasts
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public PeerListener(String myUserId) {
        this.myUserId = myUserId;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
            byte[] buffer = new byte[1024];

            // background thread to clean out stale peers
            Thread cleaner = new Thread(this::expireStalePeers);
            cleaner.setDaemon(true);
            cleaner.start();

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // blocks until a packet arrives

                String msg = new String(packet.getData(), 0, packet.getLength());
                handlePacket(msg, packet.getAddress());
            }
        } catch (Exception e) {
            System.err.println("Listener stopped: " + e.getMessage());
        }
    }

    private void handlePacket(String msg, InetAddress senderAddress) {
        String[] parts = msg.split("\\|");
        if (parts.length != 4 || !parts[0].equals("MESHLINK")) {
            return; // not a valid MeshLink packet, ignore
        }

        String userId = parts[1];
        String hostname = parts[2];
        int tcpPort = Integer.parseInt(parts[3]);

        if (userId.equals(myUserId)) {
            return; // ignore our own broadcast
        }

        PeerInfo peer = new PeerInfo(userId, hostname, senderAddress, tcpPort, System.currentTimeMillis());

        boolean isNew = !peers.containsKey(userId);
        peers.put(userId, peer);

        if (isNew) {
            System.out.println("Discovered new peer: " + userId + " @ " + senderAddress.getHostAddress() + ":" + tcpPort);
        }
    }

    private void expireStalePeers() {
        while (running) {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, PeerInfo>> it = peers.entrySet().iterator();
            while (it.hasNext()) {
                PeerInfo p = it.next().getValue();
                if (now - p.lastSeen > PEER_TIMEOUT_MS) {
                    System.out.println("Peer went offline: " + p.userId);
                    it.remove();
                }
            }
            try {
                Thread.sleep(5000); // check every 5s
            } catch (InterruptedException ignored) {
                break;
            }
        }
    }

    public List<PeerInfo> getKnownPeers() {
        return new ArrayList<>(peers.values());
    }

    public void stop() {
        running = false;
    }

    /** Simple data holder for a discovered peer. */
    public static class PeerInfo {
        public final String userId;
        public final String hostname;
        public final InetAddress address;
        public final int tcpPort;
        public final long lastSeen;

        public PeerInfo(String userId, String hostname, InetAddress address, int tcpPort, long lastSeen) {
            this.userId = userId;
            this.hostname = hostname;
            this.address = address;
            this.tcpPort = tcpPort;
            this.lastSeen = lastSeen;
        }

        @Override
        public String toString() {
            return userId + " (" + hostname + ") @ " + address.getHostAddress() + ":" + tcpPort;
        }
    }
}
