import java.util.List;

/**
 * Lightweight, self-contained unit test suite for {@link LocationTrie} and {@link TrieNode}.
 *
 * Runs without third-party frameworks:
 * <pre>
 *   javac LocationTrieTest.java && java LocationTrieTest
 * </pre>
 * Exit code 0 = all tests passed.
 * Exit code 1 = at least one test failed.
 */
public class LocationTrieTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Running LocationTrie Test Suite ===");
        System.out.println();

        testEmptyTrie();
        testSingleInsertAndExactSearch();
        testStartsWith();
        testPrefixSearchMultipleMatches();
        testPrefixSearchNoMatches();
        testPrefixOverlappingKeys();
        testCaseInsensitivity();
        testNullAndEmptySafety();
        testCampusTreeIntegration();
        testCampusTreeKeywordSearch();

        System.out.println();
        System.out.printf("Results: %d passed, %d failed.%n", passed, failed);

        if (failed > 0) {
            System.err.println("TEST SUITE FAILED");
            System.exit(1);
        } else {
            System.out.println("ALL TESTS PASSED SUCCESSFULLY");
            System.exit(0);
        }
    }

    // ── Test 1: Empty Trie ────────────────────────────────────────────────────

    private static void testEmptyTrie() {
        LocationTrie trie = new LocationTrie();
        assertTrue("New trie must be empty", trie.isEmpty());
        assertEquals("New trie size must be 0", 0, trie.size());
        assertNull("Search in empty trie must return null", trie.searchExact("b107"));
        assertFalse("startsWith in empty trie must return false", trie.startsWith("b"));
        assertTrue("searchPrefix in empty trie must return empty list", trie.searchPrefix("b").isEmpty());
    }

    // ── Test 2: Single Insert & Exact Search ──────────────────────────────────

    private static void testSingleInsertAndExactSearch() {
        LocationTrie trie = new LocationTrie();
        LocationNode room = new LocationNode("b107", "B107 - OS Lab", LocationType.LAB);
        trie.insert("b107", room);

        assertFalse("Trie not empty after insert", trie.isEmpty());
        assertEquals("Trie size is 1", 1, trie.size());

        LocationNode found = trie.searchExact("b107");
        assertNotNull("Must find exact key", found);
        assertEquals("Found node ID matches", "b107", found.getId());
        assertEquals("Found node name matches", "B107 - OS Lab", found.getName());

        assertNull("Different key must return null", trie.searchExact("b108"));
    }

    // ── Test 3: StartsWith ────────────────────────────────────────────────────

    private static void testStartsWith() {
        LocationTrie trie = new LocationTrie();
        LocationNode room = new LocationNode("b107", "B107", LocationType.LAB);
        trie.insert("b107", room);

        assertTrue("startsWith('b')", trie.startsWith("b"));
        assertTrue("startsWith('b1')", trie.startsWith("b1"));
        assertTrue("startsWith('b10')", trie.startsWith("b10"));
        assertTrue("startsWith('b107')", trie.startsWith("b107"));
        assertFalse("startsWith('b2')", trie.startsWith("b2"));
        assertFalse("startsWith('a')", trie.startsWith("a"));
    }

    // ── Test 4: Prefix Search Multiple Matches ────────────────────────────────

    private static void testPrefixSearchMultipleMatches() {
        LocationTrie trie = new LocationTrie();
        LocationNode r1 = new LocationNode("b101", "B101", LocationType.ROOM);
        LocationNode r2 = new LocationNode("b107", "B107", LocationType.LAB);
        LocationNode r3 = new LocationNode("b108", "B108", LocationType.LAB);
        LocationNode r4 = new LocationNode("b201", "B201", LocationType.ROOM);

        trie.insert("b101", r1);
        trie.insert("b107", r2);
        trie.insert("b108", r3);
        trie.insert("b201", r4);

        assertEquals("Trie size is 4", 4, trie.size());

        // Prefix query: "b10"
        List<LocationNode> matches = trie.searchPrefix("b10");
        assertEquals("Prefix 'b10' must return 3 rooms", 3, matches.size());
        assertTrue("Matches contain b101", matches.contains(r1));
        assertTrue("Matches contain b107", matches.contains(r2));
        assertTrue("Matches contain b108", matches.contains(r3));
        assertFalse("Matches do NOT contain b201", matches.contains(r4));

        // Prefix query: "b"
        List<LocationNode> allB = trie.searchPrefix("b");
        assertEquals("Prefix 'b' must return all 4 rooms", 4, allB.size());

        // Prefix query: "b2"
        List<LocationNode> b2 = trie.searchPrefix("b2");
        assertEquals("Prefix 'b2' must return 1 room", 1, b2.size());
        assertEquals("b2 match is b201", r4, b2.get(0));
    }

    // ── Test 5: Prefix Search No Matches ──────────────────────────────────────

    private static void testPrefixSearchNoMatches() {
        LocationTrie trie = new LocationTrie();
        trie.insert("b107", new LocationNode("b107", "B107", LocationType.LAB));

        List<LocationNode> result = trie.searchPrefix("z");
        assertNotNull("Search with no match returns non-null list", result);
        assertTrue("Search with no match returns empty list", result.isEmpty());
    }

    // ── Test 6: Overlapping Keys ──────────────────────────────────────────────

    private static void testPrefixOverlappingKeys() {
        LocationTrie trie = new LocationTrie();
        LocationNode b1 = new LocationNode("b1", "B Block Wing 1", LocationType.FLOOR);
        LocationNode b107 = new LocationNode("b107", "B107", LocationType.LAB);

        trie.insert("b1", b1);
        trie.insert("b107", b107);

        assertEquals("Trie size is 2", 2, trie.size());
        assertEquals("Exact search for b1", b1, trie.searchExact("b1"));
        assertEquals("Exact search for b107", b107, trie.searchExact("b107"));

        List<LocationNode> matches = trie.searchPrefix("b1");
        assertEquals("Prefix b1 returns both nodes", 2, matches.size());
    }

    // ── Test 7: Case Insensitivity ────────────────────────────────────────────

    private static void testCaseInsensitivity() {
        LocationTrie trie = new LocationTrie();
        LocationNode node = new LocationNode("b107", "B107", LocationType.LAB);
        trie.insert("B107", node);

        assertNotNull("Exact search lowercase", trie.searchExact("b107"));
        assertNotNull("Exact search uppercase", trie.searchExact("B107"));
        assertTrue("Prefix lowercase", trie.startsWith("b10"));
        assertTrue("Prefix uppercase", trie.startsWith("B10"));
        assertEquals("Prefix query case match", 1, trie.searchPrefix("B10").size());
    }

    // ── Test 8: Null and Empty Safety ─────────────────────────────────────────

    private static void testNullAndEmptySafety() {
        LocationTrie trie = new LocationTrie();
        trie.insert(null, new LocationNode("b107", "B107", LocationType.LAB));
        trie.insert("  ", new LocationNode("b107", "B107", LocationType.LAB));
        trie.insert("b107", null);
        assertEquals("Size remains 0 after invalid inserts", 0, trie.size());

        assertNull("Search null", trie.searchExact(null));
        assertNull("Search empty", trie.searchExact("  "));
        assertFalse("StartsWith null", trie.startsWith(null));
        assertTrue("SearchPrefix null returns empty list", trie.searchPrefix(null).isEmpty());
    }

    // ── Test 9: CampusTree Integration ────────────────────────────────────────

    private static void testCampusTreeIntegration() {
        CampusTree tree = new CampusTree();
        tree.seedDefaultCampus();

        LocationTrie trie = new LocationTrie();
        trie.indexCampusTree(tree);

        assertTrue("Trie indexed tree nodes", trie.size() > 10);

        // Academic requirement demonstration: Searching "b10" returns B101, B107, B108
        List<LocationNode> b10Rooms = trie.searchPrefix("b10");
        assertEquals("Searching 'b10' on campus returns 3 locations", 3, b10Rooms.size());

        boolean foundB101 = false;
        boolean foundB107 = false;
        boolean foundB108 = false;
        for (LocationNode node : b10Rooms) {
            if ("b101".equals(node.getId())) foundB101 = true;
            if ("b107".equals(node.getId())) foundB107 = true;
            if ("b108".equals(node.getId())) foundB108 = true;
        }

        assertTrue("Found B101 in 'b10' search", foundB101);
        assertTrue("Found B107 in 'b10' search", foundB107);
        assertTrue("Found B108 in 'b10' search", foundB108);
    }

    // ── Test 10: CampusTree Keyword Search ─────────────────────────────────────

    private static void testCampusTreeKeywordSearch() {
        CampusTree tree = new CampusTree();
        tree.seedDefaultCampus();

        LocationTrie trie = new LocationTrie();
        trie.indexCampusTree(tree);

        // Search by topic / keyword
        List<LocationNode> iotMatches = trie.searchPrefix("iot");
        assertEquals("Search 'iot' returns IoT Systems Lab", 1, iotMatches.size());
        assertEquals("IoT match name", "a202", iotMatches.get(0).getId());

        List<LocationNode> aiMatches = trie.searchPrefix("ai");
        assertEquals("Search 'ai' returns AI Research Lab", 1, aiMatches.size());
        assertEquals("AI match name", "b205", aiMatches.get(0).getId());
    }

    // ── Assertion Helpers ─────────────────────────────────────────────────────

    private static void assertTrue(String message, boolean condition) {
        if (condition) {
            pass(message);
        } else {
            fail(message + " (expected true, but was false)");
        }
    }

    private static void assertFalse(String message, boolean condition) {
        if (!condition) {
            pass(message);
        } else {
            fail(message + " (expected false, but was true)");
        }
    }

    private static void assertEquals(String message, Object expected, Object actual) {
        if (expected == null && actual == null) {
            pass(message);
        } else if (expected != null && expected.equals(actual)) {
            pass(message);
        } else {
            fail(message + " (expected [" + expected + "], but got [" + actual + "])");
        }
    }

    private static void assertNotNull(String message, Object object) {
        if (object != null) {
            pass(message);
        } else {
            fail(message + " (expected non-null, but was null)");
        }
    }

    private static void assertNull(String message, Object object) {
        if (object == null) {
            pass(message);
        } else {
            fail(message + " (expected null, but got [" + object + "])");
        }
    }

    private static void pass(String message) {
        passed++;
        System.out.println("  PASS [" + message + "]");
    }

    private static void fail(String message) {
        failed++;
        System.err.println("  FAIL [" + message + "]");
    }
}

