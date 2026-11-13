import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Custom HashMap-based Peer Presence Registry.
 *
 * <p>Provides O(1) peer lookup, online/offline status tracking, and
 * hostname/port metadata storage — without scanning through any list.
 *
 * <p><b>Academic Complexity Summary:</b>
 * <ul>
 *   <li>{@link #register}: Time O(1) average, Space O(1)</li>
 *   <li>{@link #markOnline}/{@link #markOffline}: Time O(1) average</li>
 *   <li>{@link #isOnline}: Time O(1) average</li>
 *   <li>{@link #getPeer}: Time O(1) average</li>
 * </ul>
 */
public class PeerRegistry {

    /** Enum representing the current reachability state of a peer. */
    public enum PeerStatus { ONLINE, OFFLINE, UNKNOWN }

    /**
     * Value object holding all known metadata about a registered peer.
     */
    public static class PeerEntry {
        private final String peerId;
        private String ipAddress;
        private int    tcpPort;
        private PeerStatus status;
        private long   lastSeenTimestamp;

        public PeerEntry(String peerId, String ipAddress, int tcpPort) {
            this.peerId             = peerId;
            this.ipAddress          = ipAddress;
            this.tcpPort            = tcpPort;
            this.status             = PeerStatus.UNKNOWN;
            this.lastSeenTimestamp  = System.currentTimeMillis();
        }

        public String     getPeerId()           { return peerId; }
        public String     getIpAddress()         { return ipAddress; }
        public int        getTcpPort()            { return tcpPort; }
        public PeerStatus getStatus()             { return status; }
        public long       getLastSeenTimestamp()  { return lastSeenTimestamp; }

        public void setStatus(PeerStatus status) {
            this.status = status;
            if (status == PeerStatus.ONLINE) {
                this.lastSeenTimestamp = System.currentTimeMillis();
            }
        }

        public void update(String ipAddress, int tcpPort) {
            this.ipAddress = ipAddress;
            this.tcpPort   = tcpPort;
            this.lastSeenTimestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.format("[%s] %s @ %s:%d (last seen: %dms ago)",
                    status, peerId, ipAddress, tcpPort,
                    System.currentTimeMillis() - lastSeenTimestamp);
        }
    }

    /**
     * Core HashMap: peerId (String) → PeerEntry.
     *
     * <p>Provides O(1) average-case insertion, lookup, and removal.
     * Uses Java's built-in HashMap internally but the registry logic
     * (presence tracking, status management, snapshot queries) is
     * implemented manually for academic demonstration.
     */
    private final Map<String, PeerEntry> registry;

    public PeerRegistry() {
        this.registry = new HashMap<>();
    }

    // ── Core Operations ───────────────────────────────────────────────────────

    /**
     * Registers a peer into the registry.
     * If the peer already exists, updates their IP and port metadata.
     *
     * <p><b>Time Complexity:</b> O(1) average
     * <br><b>Space Complexity:</b> O(1)
     *
     * @param peerId    unique peer identifier
     * @param ipAddress network IP address
     * @param tcpPort   TCP listening port
     */
    public void register(String peerId, String ipAddress, int tcpPort) {
        if (peerId == null || peerId.trim().isEmpty()) return;
        String key = peerId.trim().toLowerCase();
        PeerEntry existing = registry.get(key);
        if (existing != null) {
            existing.update(ipAddress, tcpPort);
        } else {
            registry.put(key, new PeerEntry(key, ipAddress, tcpPort));
        }
    }

    /**
     * Marks a registered peer as ONLINE (connected via TCP).
     *
     * <p><b>Time Complexity:</b> O(1) average
     *
     * @param peerId peer to mark online
     */
    public void markOnline(String peerId) {
        PeerEntry entry = getEntry(peerId);
        if (entry != null) {
            entry.setStatus(PeerStatus.ONLINE);
        }
    }

    /**
     * Marks a registered peer as OFFLINE (disconnected or timed out).
     *
     * <p><b>Time Complexity:</b> O(1) average
     *
     * @param peerId peer to mark offline
     */
    public void markOffline(String peerId) {
        PeerEntry entry = getEntry(peerId);
        if (entry != null) {
            entry.setStatus(PeerStatus.OFFLINE);
        }
    }

    /**
     * Checks if a peer is currently ONLINE.
     *
     * <p><b>Time Complexity:</b> O(1) average
     *
     * @param peerId peer identifier to check
     * @return true if peer is registered and status is ONLINE
     */
    public boolean isOnline(String peerId) {
        PeerEntry entry = getEntry(peerId);
        return entry != null && entry.getStatus() == PeerStatus.ONLINE;
    }

    /**
     * Retrieves full metadata for a peer.
     *
     * <p><b>Time Complexity:</b> O(1) average
     *
     * @param peerId peer identifier
     * @return {@link PeerEntry}, or null if not registered
     */
    public PeerEntry getPeer(String peerId) {
        return getEntry(peerId);
    }

    /**
     * Removes a peer from the registry entirely.
     *
     * <p><b>Time Complexity:</b> O(1) average
     *
     * @param peerId peer to deregister
     */
    public void deregister(String peerId) {
        if (peerId != null) {
            registry.remove(peerId.trim().toLowerCase());
        }
    }

    /**
     * Returns a snapshot set of all currently ONLINE peer IDs.
     *
     * <p><b>Time Complexity:</b> O(N) where N = total registered peers
     *
     * @return unmodifiable set of online peer IDs
     */
    public Set<String> getOnlinePeers() {
        Set<String> online = new HashSet<>();
        for (Map.Entry<String, PeerEntry> e : registry.entrySet()) {
            if (e.getValue().getStatus() == PeerStatus.ONLINE) {
                online.add(e.getKey());
            }
        }
        return Collections.unmodifiableSet(online);
    }

    /**
     * Returns all registered peers regardless of status.
     *
     * @return unmodifiable map view
     */
    public Map<String, PeerEntry> getAllPeers() {
        return Collections.unmodifiableMap(registry);
    }

    public int totalRegistered() { return registry.size(); }
    public int totalOnline()     { return getOnlinePeers().size(); }
    public boolean contains(String peerId) {
        return peerId != null && registry.containsKey(peerId.trim().toLowerCase());
    }

    private PeerEntry getEntry(String peerId) {
        if (peerId == null) return null;
        return registry.get(peerId.trim().toLowerCase());
    }
}

