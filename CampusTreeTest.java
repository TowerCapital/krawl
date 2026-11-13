import java.util.List;

/**
 * Lightweight, self-contained unit test suite for {@link CampusTree} and {@link LocationNode}.
 *
 * Runs without third-party frameworks:
 * <pre>
 *   javac CampusTreeTest.java && java CampusTreeTest
 * </pre>
 * Exit code 0 = all tests passed.
 * Exit code 1 = at least one test failed.
 */
public class CampusTreeTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Running CampusTree Test Suite ===");
        System.out.println();

        testEmptyTree();
        testAddAndFindLocation();
        testParentChildBidirectionalLink();
        testAddUnderNonExistentParent();
        testFindNonExistentNode();
        testGetChildren();
        testGetFullPath();
        testBfsTraversal();
        testDfsTraversal();
        testRemoveChild();
        testTreeHeightAndSize();
        testSeedDefaultCampus();

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

    // ── Test 1: Empty Tree ────────────────────────────────────────────────────

    private static void testEmptyTree() {
        CampusTree tree = new CampusTree();
        assertNull("Empty tree root must be null", tree.getRoot());
        assertEquals("Empty tree size must be 0", 0, tree.size());
        assertEquals("Empty tree height must be 0", 0, tree.getHeight());
        assertTrue("BFS on empty tree must be empty", tree.bfsTraversal().isEmpty());
        assertTrue("DFS on empty tree must be empty", tree.dfsTraversal().isEmpty());
    }

    // ── Test 2: Add and Find Location ─────────────────────────────────────────

    private static void testAddAndFindLocation() {
        CampusTree tree = new CampusTree();
        LocationNode campus = new LocationNode("vit-pune", "VIT Pune", LocationType.CAMPUS);
        tree.setRoot(campus);

        LocationNode bBlock = new LocationNode("b-building", "B Building", LocationType.BUILDING);
        boolean added = tree.addLocation("vit-pune", bBlock);

        assertTrue("Adding building under campus must succeed", added);
        assertEquals("Tree size must be 2", 2, tree.size());

        LocationNode found = tree.findLocation("b-building");
        assertNotNull("findLocation must locate b-building", found);
        assertEquals("Found node name must match", "B Building", found.getName());
        assertEquals("Found node type must match", LocationType.BUILDING, found.getType());

        // Test case-insensitivity
        LocationNode foundCase = tree.findLocation("B-BUILDING");
        assertNotNull("findLocation must be case-insensitive", foundCase);
    }

    // ── Test 3: Bidirectional Parent-Child Link ───────────────────────────────

    private static void testParentChildBidirectionalLink() {
        CampusTree tree = new CampusTree();
        LocationNode root = new LocationNode("campus", "Campus", LocationType.CAMPUS);
        LocationNode child = new LocationNode("dept", "Computer Dept", LocationType.BUILDING);
        tree.setRoot(root);
        tree.addLocation("campus", child);

        assertTrue("Root parent must be null", root.isRoot());
        assertFalse("Child must not be root", child.isRoot());
        assertEquals("Child parent must point to root", root, child.getParent());
        assertEquals("Child level must be 1", 1, child.getLevel());
        assertEquals("Root level must be 0", 0, root.getLevel());
    }

    // ── Test 4: Add under non-existent parent ──────────────────────────────────

    private static void testAddUnderNonExistentParent() {
        CampusTree tree = new CampusTree(new LocationNode("root", "Root", LocationType.CAMPUS));
        LocationNode rogue = new LocationNode("orphan", "Orphan", LocationType.ROOM);
        boolean result = tree.addLocation("non-existent-id", rogue);

        assertFalse("Adding under non-existent parent must return false", result);
        assertNull("Orphan should not be in tree", tree.findLocation("orphan"));
    }

    // ── Test 5: Find non-existent node ────────────────────────────────────────

    private static void testFindNonExistentNode() {
        CampusTree tree = new CampusTree(new LocationNode("root", "Root", LocationType.CAMPUS));
        assertNull("Searching invalid ID must return null", tree.findLocation("ghost-room"));
        assertNull("Searching null ID must return null", tree.findLocation(null));
    }

    // ── Test 6: Get children ──────────────────────────────────────────────────

    private static void testGetChildren() {
        CampusTree tree = new CampusTree();
        LocationNode root = new LocationNode("root", "Campus", LocationType.CAMPUS);
        tree.setRoot(root);
        tree.addLocation("root", new LocationNode("b1", "Block 1", LocationType.BUILDING));
        tree.addLocation("root", new LocationNode("b2", "Block 2", LocationType.BUILDING));

        List<LocationNode> children = tree.getChildren("root");
        assertEquals("Root must have 2 children", 2, children.size());
        assertEquals("First child must be Block 1", "Block 1", children.get(0).getName());
        assertEquals("Second child must be Block 2", "Block 2", children.get(1).getName());

        List<LocationNode> leafChildren = tree.getChildren("b1");
        assertTrue("Leaf children must be empty", leafChildren.isEmpty());
    }

    // ── Test 7: Get full path ─────────────────────────────────────────────────

    private static void testGetFullPath() {
        CampusTree tree = new CampusTree();
        tree.setRoot(new LocationNode("vit-pune", "VIT Pune", LocationType.CAMPUS));
        tree.addLocation("vit-pune", new LocationNode("b-building", "B Building", LocationType.BUILDING));
        tree.addLocation("b-building", new LocationNode("first-floor", "First Floor", LocationType.FLOOR));
        tree.addLocation("first-floor", new LocationNode("b107", "B107", LocationType.LAB));

        String defaultPath = tree.getFullPath("b107", null);
        assertEquals("Default path format", "VIT Pune > B Building > First Floor > B107", defaultPath);

        String customPath = tree.getFullPath("b107", " / ");
        assertEquals("Custom delimiter path format", "VIT Pune / B Building / First Floor / B107", customPath);

        assertNull("Path for missing node must be null", tree.getFullPath("unknown", null));
    }

    // ── Test 8: BFS Traversal ─────────────────────────────────────────────────

    private static void testBfsTraversal() {
        CampusTree tree = new CampusTree();
        tree.setRoot(new LocationNode("root", "Root", LocationType.CAMPUS));
        tree.addLocation("root", new LocationNode("b1", "B1", LocationType.BUILDING));
        tree.addLocation("root", new LocationNode("b2", "B2", LocationType.BUILDING));
        tree.addLocation("b1", new LocationNode("f1", "F1", LocationType.FLOOR));
        tree.addLocation("b2", new LocationNode("f2", "F2", LocationType.FLOOR));

        List<LocationNode> bfs = tree.bfsTraversal();
        assertEquals("BFS must visit all 5 nodes", 5, bfs.size());
        assertEquals("Level 0 must be Root", "root", bfs.get(0).getId());
        assertEquals("Level 1 must contain B1", "b1", bfs.get(1).getId());
        assertEquals("Level 1 must contain B2", "b2", bfs.get(2).getId());
        assertEquals("Level 2 must contain F1", "f1", bfs.get(3).getId());
        assertEquals("Level 2 must contain F2", "f2", bfs.get(4).getId());
    }

    // ── Test 9: DFS Traversal ─────────────────────────────────────────────────

    private static void testDfsTraversal() {
        CampusTree tree = new CampusTree();
        tree.setRoot(new LocationNode("root", "Root", LocationType.CAMPUS));
        tree.addLocation("root", new LocationNode("b1", "B1", LocationType.BUILDING));
        tree.addLocation("b1", new LocationNode("f1", "F1", LocationType.FLOOR));
        tree.addLocation("root", new LocationNode("b2", "B2", LocationType.BUILDING));

        List<LocationNode> dfs = tree.dfsTraversal();
        assertEquals("DFS must visit all 4 nodes", 4, dfs.size());
        assertEquals("First node is Root", "root", dfs.get(0).getId());
        assertEquals("Branch 1 child is B1", "b1", dfs.get(1).getId());
        assertEquals("Branch 1 subchild is F1 (depth first before B2)", "f1", dfs.get(2).getId());
        assertEquals("Next branch is B2", "b2", dfs.get(3).getId());
    }

    // ── Test 10: Remove Child ─────────────────────────────────────────────────

    private static void testRemoveChild() {
        CampusTree tree = new CampusTree();
        LocationNode root = new LocationNode("root", "Root", LocationType.CAMPUS);
        LocationNode temp = new LocationNode("temp", "Temp Building", LocationType.BUILDING);
        tree.setRoot(root);
        tree.addLocation("root", temp);

        assertEquals("Root children before remove", 1, root.getChildCount());
        boolean removed = root.removeChild("temp");
        assertTrue("Remove must return true", removed);
        assertEquals("Root children after remove", 0, root.getChildCount());
        assertNull("Temp parent pointer cleared", temp.getParent());
    }

    // ── Test 11: Tree Height and Size ─────────────────────────────────────────

    private static void testTreeHeightAndSize() {
        CampusTree tree = new CampusTree();
        assertEquals("Empty height", 0, tree.getHeight());

        tree.setRoot(new LocationNode("root", "Root", LocationType.CAMPUS));
        assertEquals("Single node height", 1, tree.getHeight());

        tree.addLocation("root", new LocationNode("b", "Building", LocationType.BUILDING));
        assertEquals("Two levels height", 2, tree.getHeight());

        tree.addLocation("b", new LocationNode("f", "Floor", LocationType.FLOOR));
        assertEquals("Three levels height", 3, tree.getHeight());

        tree.addLocation("f", new LocationNode("r", "Room", LocationType.ROOM));
        assertEquals("Four levels height", 4, tree.getHeight());
        assertEquals("Total size", 4, tree.size());
    }

    // ── Test 12: Seed Default Campus ──────────────────────────────────────────

    private static void testSeedDefaultCampus() {
        CampusTree tree = new CampusTree();
        tree.seedDefaultCampus();

        assertNotNull("Seeded root must exist", tree.getRoot());
        assertEquals("Root name must be VIT Pune", "VIT Pune", tree.getRoot().getName());
        assertEquals("Campus must have 4 levels (Campus, Building, Floor, Room)", 4, tree.getHeight());

        LocationNode b107 = tree.findLocation("b107");
        assertNotNull("b107 must exist in seeded campus", b107);
        assertEquals("b107 type must be LAB", LocationType.LAB, b107.getType());

        String path = tree.getFullPath("b107", " > ");
        assertEquals("b107 full path",
                "VIT Pune > B Building > First Floor > B107 - Operating Systems Lab",
                path);

        LocationNode a202 = tree.findLocation("a202");
        assertNotNull("a202 must exist in seeded campus", a202);
        String aPath = tree.getFullPath("a202", " > ");
        assertEquals("a202 full path",
                "VIT Pune > A Building > Second Floor > A202 - IoT Systems Lab",
                aPath);
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

