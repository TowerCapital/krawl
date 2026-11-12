import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Custom undirected Graph representing the campus peer network topology.
 *
 * <p>Models the live TCP connectivity between devices on the LAN:
 * <ul>
 *   <li>Each <b>vertex</b> is a peer device (identified by peerId).</li>
 *   <li>Each <b>edge</b> is an active TCP connection between two peers.</li>
 * </ul>
 *
 * <p><b>Implementation:</b> Adjacency List using a HashMap of HashSets.
 * Chosen over an adjacency matrix because campus peer networks are <i>sparse</i>
 * (a device is typically connected to 2–10 peers, not all N peers simultaneously).
 *
 * <p><b>Academic Complexity Summary:</b>
 * <ul>
 *   <li>{@link #addPeer}: Time O(1) average</li>
 *   <li>{@link #addEdge}: Time O(1) average</li>
 *   <li>{@link #removeEdge}: Time O(1) average</li>
 *   <li>{@link #getNeighbors}: Time O(1) average</li>
 *   <li>{@link #bfsPath}: Time O(V + E), Space O(V)</li>
 *   <li>{@link #isReachable}: Time O(V + E), Space O(V)</li>
 * </ul>
 * where V = total peers (vertices), E = active TCP connections (edges).
 */
public class PeerGraph {

    /**
     * Adjacency list representation.
     *
     * <p>Key: peerId (vertex)
     * <br>Value: Set of directly connected peer IDs (neighbors)
     *
     * <p>Using HashSet for neighbor storage gives O(1) average edge lookup,
     * compared to O(degree) for a List.
     */
    private final Map<String, Set<String>> adjacencyList;

    public PeerGraph() {
        this.adjacencyList = new HashMap<>();
    }

    // ── Mutation Operations ───────────────────────────────────────────────────

    /**
     * Registers a peer as a vertex in the graph (without any edges yet).
     *
     * <p><b>Time Complexity:</b> O(1) average
     *
     * @param peerId unique peer identifier
     */
    public void addPeer(String peerId) {
        if (peerId == null) return;
        adjacencyList.putIfAbsent(normalize(peerId), new HashSet<>());
    }

    /**
     * Adds an undirected edge (active TCP connection) between two peers.
     * Both vertices are created if they don't already exist.
     *
     * <p><b>Time Complexity:</b> O(1) average
     *
     * @param peerA first peer (e.g., "alice")
     * @param peerB second peer (e.g., "bob")
     */
    public void addEdge(String peerA, String peerB) {
        if (peerA == null || peerB == null || peerA.equals(peerB)) return;
        String a = normalize(peerA);
        String b = normalize(peerB);

        adjacencyList.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        adjacencyList.computeIfAbsent(b, k -> new HashSet<>()).add(a);
    }

    /**
     * Removes an undirected edge (TCP disconnection) between two peers.
     *
     * <p><b>Time Complexity:</b> O(1) average
     *
     * @param peerA first peer
     * @param peerB second peer
     */
    public void removeEdge(String peerA, String peerB) {
        if (peerA == null || peerB == null) return;
        String a = normalize(peerA);
        String b = normalize(peerB);

        Set<String> neighborsA = adjacencyList.get(a);
        Set<String> neighborsB = adjacencyList.get(b);
        if (neighborsA != null) neighborsA.remove(b);
        if (neighborsB != null) neighborsB.remove(a);
    }

    /**
     * Removes a peer and all its edges from the graph (device left the network).
     *
     * <p><b>Time Complexity:</b> O(degree(peer)) — removes from each neighbor's set
     *
     * @param peerId peer to remove entirely
     */
    public void removePeer(String peerId) {
        if (peerId == null) return;
        String p = normalize(peerId);
        Set<String> neighbors = adjacencyList.remove(p);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                Set<String> nb = adjacencyList.get(neighbor);
                if (nb != null) nb.remove(p);
            }
        }
    }

    // ── Query Operations ──────────────────────────────────────────────────────

    /**
     * Returns the set of peers directly connected to the given peer.
     *
     * <p><b>Time Complexity:</b> O(1) average
     *
     * @param peerId peer to query
     * @return unmodifiable set of neighbor peer IDs
     */
    public Set<String> getNeighbors(String peerId) {
        Set<String> neighbors = adjacencyList.get(normalize(peerId));
        return (neighbors != null)
                ? Collections.unmodifiableSet(neighbors)
                : Collections.emptySet();
    }

    public boolean hasPeer(String peerId) {
        return peerId != null && adjacencyList.containsKey(normalize(peerId));
    }

    public boolean hasEdge(String peerA, String peerB) {
        Set<String> neighbors = adjacencyList.get(normalize(peerA));
        return neighbors != null && neighbors.contains(normalize(peerB));
    }

    public int peerCount()       { return adjacencyList.size(); }
    public Set<String> allPeers() { return Collections.unmodifiableSet(adjacencyList.keySet()); }

    // ── Graph Traversal: BFS ──────────────────────────────────────────────────

    /**
     * Breadth-First Search: finds the shortest relay path between two peers.
     *
     * <p>Useful when two devices cannot directly connect (e.g., different floors,
     * weak signal) but can communicate via relay through intermediate peers.
     *
     * <p>Example: Alice → Bob → Ronak (Alice cannot reach Ronak directly).
     *
     * <p><b>Time Complexity:</b> O(V + E)
     * <br><b>Space Complexity:</b> O(V) for visited set and predecessor map
     *
     * @param source      starting peer
     * @param destination target peer
     * @return ordered list of peer IDs forming the shortest relay path,
     *         or empty list if no path exists
     */
    public List<String> bfsPath(String source, String destination) {
        if (source == null || destination == null) return Collections.emptyList();
        String src  = normalize(source);
        String dest = normalize(destination);

        if (src.equals(dest)) return Collections.singletonList(src);
        if (!adjacencyList.containsKey(src) || !adjacencyList.containsKey(dest)) {
            return Collections.emptyList();
        }

        // BFS with predecessor tracking for path reconstruction
        Set<String>    visited     = new HashSet<>();
        Map<String, String> predecessor = new HashMap<>();
        Queue<String>  queue       = new ArrayDeque<>();

        queue.offer(src);
        visited.add(src);

        while (!queue.isEmpty()) {
            String current = queue.poll();

            if (current.equals(dest)) {
                return reconstructPath(predecessor, src, dest);
            }

            for (String neighbor : adjacencyList.getOrDefault(current, Collections.emptySet())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    predecessor.put(neighbor, current);
                    queue.offer(neighbor);
                }
            }
        }
        return Collections.emptyList(); // no path found
    }

    /**
     * Checks if two peers can communicate (directly or via relay).
     *
     * <p><b>Time Complexity:</b> O(V + E)
     *
     * @param source      starting peer
     * @param destination target peer
     * @return true if any path exists between them
     */
    public boolean isReachable(String source, String destination) {
        return !bfsPath(source, destination).isEmpty();
    }

    // ── Visualization Helper ──────────────────────────────────────────────────

    /**
     * Returns a human-readable ASCII representation of the network topology.
     *
     * @return formatted string showing each peer and its direct connections
     */
    public String toTopologyString() {
        if (adjacencyList.isEmpty()) {
            return "[NETWORK] No peers in graph.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[NETWORK TOPOLOGY] %d peers, connections:%n", peerCount()));
        for (Map.Entry<String, Set<String>> entry : adjacencyList.entrySet()) {
            sb.append(String.format("  %-12s ──── %s%n",
                    entry.getKey(),
                    entry.getValue().isEmpty() ? "(no direct connections)" : entry.getValue()));
        }
        return sb.toString();
    }

    // ── Internal Helpers ──────────────────────────────────────────────────────

    private String normalize(String id) {
        return id.trim().toLowerCase();
    }

    private List<String> reconstructPath(Map<String, String> predecessor, String src, String dest) {
        List<String> path = new ArrayList<>();
        String current = dest;
        while (current != null) {
            path.add(current);
            current = predecessor.get(current);
        }
        Collections.reverse(path);
        return path;
    }
}

