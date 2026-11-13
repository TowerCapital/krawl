/**
 * Standalone unit test suite for {@link PeerRegistry} (HashMap).
 * Run: javac PeerRegistryTest.java && java PeerRegistryTest
 */
public class PeerRegistryTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Running PeerRegistry (HashMap) Test Suite ===");
        System.out.println();

        testRegisterAndLookup();
        testMarkOnlineOffline();
        testIsOnline();
        testUpdateExistingPeer();
        testDeregister();
        testGetOnlinePeers();
        testNullSafety();
        testCaseInsensitivity();

        System.out.println();
        System.out.printf("Results: %d passed, %d failed.%n", passed, failed);
        if (failed > 0) { System.err.println("TEST SUITE FAILED"); System.exit(1); }
        else            { System.out.println("ALL TESTS PASSED SUCCESSFULLY"); System.exit(0); }
    }

    private static void testRegisterAndLookup() {
        PeerRegistry registry = new PeerRegistry();
        registry.register("alice", "192.168.1.10", 9001);

        assertTrue("Contains alice after register", registry.contains("alice"));
        assertEquals("Total registered is 1", 1, registry.totalRegistered());

        PeerRegistry.PeerEntry entry = registry.getPeer("alice");
        assertNotNull("getPeer returns entry", entry);
        assertEquals("IP matches", "192.168.1.10", entry.getIpAddress());
        assertEquals("Port matches", 9001, entry.getTcpPort());
        assertEquals("Initial status is UNKNOWN",
                PeerRegistry.PeerStatus.UNKNOWN, entry.getStatus());
    }

    private static void testMarkOnlineOffline() {
        PeerRegistry registry = new PeerRegistry();
        registry.register("bob", "192.168.1.20", 9002);

        registry.markOnline("bob");
        assertEquals("Status is ONLINE",
                PeerRegistry.PeerStatus.ONLINE, registry.getPeer("bob").getStatus());

        registry.markOffline("bob");
        assertEquals("Status is OFFLINE",
                PeerRegistry.PeerStatus.OFFLINE, registry.getPeer("bob").getStatus());
    }

    private static void testIsOnline() {
        PeerRegistry registry = new PeerRegistry();
        registry.register("carol", "192.168.1.30", 9003);

        assertFalse("Not online initially", registry.isOnline("carol"));
        registry.markOnline("carol");
        assertTrue("Online after markOnline", registry.isOnline("carol"));
        assertFalse("Unknown peer not online", registry.isOnline("ghost"));
    }

    private static void testUpdateExistingPeer() {
        PeerRegistry registry = new PeerRegistry();
        registry.register("alice", "192.168.1.10", 9001);
        registry.register("alice", "10.0.0.5", 9999); // re-register with new IP

        assertEquals("Total registered still 1", 1, registry.totalRegistered());
        assertEquals("IP updated", "10.0.0.5", registry.getPeer("alice").getIpAddress());
        assertEquals("Port updated", 9999, registry.getPeer("alice").getTcpPort());
    }

    private static void testDeregister() {
        PeerRegistry registry = new PeerRegistry();
        registry.register("alice", "192.168.1.10", 9001);
        registry.deregister("alice");

        assertFalse("alice removed from registry", registry.contains("alice"));
        assertNull("getPeer returns null after deregister", registry.getPeer("alice"));
    }

    private static void testGetOnlinePeers() {
        PeerRegistry registry = new PeerRegistry();
        registry.register("alice", "192.168.1.10", 9001);
        registry.register("bob",   "192.168.1.20", 9002);
        registry.register("carol", "192.168.1.30", 9003);

        registry.markOnline("alice");
        registry.markOnline("carol");

        assertEquals("2 online peers", 2, registry.totalOnline());
        assertTrue("alice in online set", registry.getOnlinePeers().contains("alice"));
        assertTrue("carol in online set", registry.getOnlinePeers().contains("carol"));
        assertFalse("bob not in online set", registry.getOnlinePeers().contains("bob"));
    }

    private static void testNullSafety() {
        PeerRegistry registry = new PeerRegistry();
        registry.register(null, "10.0.0.1", 9001);
        assertEquals("Null register ignored", 0, registry.totalRegistered());
        assertNull("getPeer null returns null", registry.getPeer(null));
        assertFalse("isOnline null returns false", registry.isOnline(null));
    }

    private static void testCaseInsensitivity() {
        PeerRegistry registry = new PeerRegistry();
        registry.register("ALICE", "10.0.0.1", 9001);
        assertTrue("Found with lowercase", registry.contains("alice"));
        assertTrue("Found with uppercase", registry.contains("ALICE"));
        registry.markOnline("Alice");
        assertTrue("isOnline case insensitive", registry.isOnline("alice"));
    }

    private static void assertTrue(String m, boolean c) { if (c) pass(m); else fail(m + " (expected true)"); }
    private static void assertFalse(String m, boolean c) { if (!c) pass(m); else fail(m + " (expected false)"); }
    private static void assertEquals(String m, Object e, Object a) {
        if (e == null ? a == null : e.equals(a)) pass(m);
        else fail(m + " (expected [" + e + "], got [" + a + "])");
    }
    private static void assertNotNull(String m, Object o) { if (o != null) pass(m); else fail(m + " (expected non-null)"); }
    private static void assertNull(String m, Object o)    { if (o == null) pass(m); else fail(m + " (expected null, got [" + o + "])"); }
    private static void pass(String m) { passed++; System.out.println("  PASS [" + m + "]"); }
    private static void fail(String m) { failed++; System.err.println("  FAIL [" + m + "]"); }
}

