import java.util.List;

/**
 * Standalone unit test suite for {@link PeerGraph} (Adjacency-List Graph + BFS).
 * Run: javac PeerGraphTest.java && java PeerGraphTest
 */
public class PeerGraphTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Running PeerGraph (Adjacency-List Graph) Test Suite ===");
        System.out.println();

        testEmptyGraph();
        testAddPeerAndEdge();
        testUndirectedEdge();
        testRemoveEdge();
        testRemovePeer();
        testGetNeighbors();
        testBfsDirectPath();
        testBfsRelayPath();
        testBfsNoPath();
        testIsReachable();
        testTopologyString();

        System.out.println();
        System.out.printf("Results: %d passed, %d failed.%n", passed, failed);
        if (failed > 0) { System.err.println("TEST SUITE FAILED"); System.exit(1); }
        else            { System.out.println("ALL TESTS PASSED SUCCESSFULLY"); System.exit(0); }
    }

    private static void testEmptyGraph() {
        PeerGraph g = new PeerGraph();
        assertEquals("Empty graph has 0 peers", 0, g.peerCount());
        assertFalse("No peer exists", g.hasPeer("alice"));
        assertTrue("BFS on empty graph returns empty", g.bfsPath("alice", "bob").isEmpty());
    }

    private static void testAddPeerAndEdge() {
        PeerGraph g = new PeerGraph();
        g.addPeer("alice");
        g.addPeer("bob");
        g.addEdge("alice", "bob");

        assertEquals("2 peers", 2, g.peerCount());
        assertTrue("alice exists", g.hasPeer("alice"));
        assertTrue("bob exists", g.hasPeer("bob"));
        assertTrue("edge alice-bob", g.hasEdge("alice", "bob"));
    }

    private static void testUndirectedEdge() {
        PeerGraph g = new PeerGraph();
        g.addEdge("alice", "bob");
        assertTrue("Edge alice→bob", g.hasEdge("alice", "bob"));
        assertTrue("Edge bob→alice (undirected)", g.hasEdge("bob", "alice"));
    }

    private static void testRemoveEdge() {
        PeerGraph g = new PeerGraph();
        g.addEdge("alice", "bob");
        g.removeEdge("alice", "bob");

        assertFalse("Edge alice-bob removed", g.hasEdge("alice", "bob"));
        assertFalse("Edge bob-alice removed", g.hasEdge("bob", "alice"));
        assertTrue("alice vertex still exists", g.hasPeer("alice"));
    }

    private static void testRemovePeer() {
        PeerGraph g = new PeerGraph();
        g.addEdge("alice", "bob");
        g.addEdge("alice", "carol");
        g.removePeer("alice");

        assertFalse("alice removed", g.hasPeer("alice"));
        assertFalse("bob no longer connected to alice", g.hasEdge("bob", "alice"));
        assertFalse("carol no longer connected to alice", g.hasEdge("carol", "alice"));
    }

    private static void testGetNeighbors() {
        PeerGraph g = new PeerGraph();
        g.addEdge("alice", "bob");
        g.addEdge("alice", "carol");

        assertEquals("alice has 2 neighbors", 2, g.getNeighbors("alice").size());
        assertTrue("bob is alice's neighbor", g.getNeighbors("alice").contains("bob"));
        assertTrue("carol is alice's neighbor", g.getNeighbors("alice").contains("carol"));
    }

    private static void testBfsDirectPath() {
        PeerGraph g = new PeerGraph();
        g.addEdge("alice", "bob");

        List<String> path = g.bfsPath("alice", "bob");
        assertEquals("Direct path length is 2", 2, path.size());
        assertEquals("Path starts at alice", "alice", path.get(0));
        assertEquals("Path ends at bob",     "bob",   path.get(1));
    }

    private static void testBfsRelayPath() {
        // alice ─── bob ─── ronak ─── soham
        PeerGraph g = new PeerGraph();
        g.addEdge("alice", "bob");
        g.addEdge("bob",   "ronak");
        g.addEdge("ronak", "soham");

        // alice cannot reach soham directly — relay needed
        List<String> path = g.bfsPath("alice", "soham");
        assertEquals("Relay path has 4 hops", 4, path.size());
        assertEquals("Path[0] = alice", "alice", path.get(0));
        assertEquals("Path[1] = bob",   "bob",   path.get(1));
        assertEquals("Path[2] = ronak", "ronak", path.get(2));
        assertEquals("Path[3] = soham", "soham", path.get(3));
    }

    private static void testBfsNoPath() {
        PeerGraph g = new PeerGraph();
        g.addPeer("alice");
        g.addPeer("bob");
        // No edge between them

        List<String> path = g.bfsPath("alice", "bob");
        assertTrue("No path returns empty list", path.isEmpty());
    }

    private static void testIsReachable() {
        PeerGraph g = new PeerGraph();
        g.addEdge("alice", "bob");
        g.addEdge("bob",   "carol");
        g.addPeer("soham"); // isolated

        assertTrue("alice can reach carol via bob", g.isReachable("alice", "carol"));
        assertFalse("alice cannot reach soham", g.isReachable("alice", "soham"));
    }

    private static void testTopologyString() {
        PeerGraph g = new PeerGraph();
        g.addEdge("alice", "bob");
        String topology = g.toTopologyString();
        assertTrue("Topology contains peer count", topology.contains("2 peers"));
        assertTrue("Topology is non-empty", !topology.isEmpty());
    }

    private static void assertTrue(String m, boolean c) { if (c) pass(m); else fail(m + " (expected true)"); }
    private static void assertFalse(String m, boolean c) { if (!c) pass(m); else fail(m + " (expected false)"); }
    private static void assertEquals(String m, Object e, Object a) {
        if (e == null ? a == null : e.equals(a)) pass(m);
        else fail(m + " (expected [" + e + "], got [" + a + "])");
    }
    private static void pass(String m) { passed++; System.out.println("  PASS [" + m + "]"); }
    private static void fail(String m) { failed++; System.err.println("  FAIL [" + m + "]"); }
}
