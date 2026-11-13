import java.util.EmptyStackException;
import java.util.List;

/**
 * Lightweight, self-contained unit test suite for {@link LocationStack} and {@link CampusNavigator}.
 *
 * Runs without third-party frameworks:
 * <pre>
 *   javac LocationStackTest.java && java LocationStackTest
 * </pre>
 * Exit code 0 = all tests passed.
 * Exit code 1 = at least one test failed.
 */
public class LocationStackTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Running LocationStack Test Suite ===");
        System.out.println();

        testEmptyStack();
        testPushAndPeek();
        testLIFOOrdering();
        testClear();
        testNullPushThrows();
        testToListOrder();
        testCampusNavigatorLinearMovement();
        testCampusNavigatorBackAndForward();
        testNavigatorBranchingClearsForward();
        testIterativeDfsMatchesRecursiveDfs();
        testStressPushPop();

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

    // ── Test 1: Empty Stack ───────────────────────────────────────────────────

    private static void testEmptyStack() {
        LocationStack<String> stack = new LocationStack<>();
        assertTrue("New stack must be empty", stack.isEmpty());
        assertEquals("New stack size must be 0", 0, stack.size());

        boolean popThrew = false;
        try {
            stack.pop();
        } catch (EmptyStackException e) {
            popThrew = true;
        }
        assertTrue("pop() on empty stack must throw EmptyStackException", popThrew);

        boolean peekThrew = false;
        try {
            stack.peek();
        } catch (EmptyStackException e) {
            peekThrew = true;
        }
        assertTrue("peek() on empty stack must throw EmptyStackException", peekThrew);
    }

    // ── Test 2: Push and Peek ─────────────────────────────────────────────────

    private static void testPushAndPeek() {
        LocationStack<String> stack = new LocationStack<>();
        stack.push("A");
        assertFalse("Stack not empty after push", stack.isEmpty());
        assertEquals("Stack size is 1", 1, stack.size());
        assertEquals("peek() returns top element", "A", stack.peek());
        assertEquals("peek() does not remove element", 1, stack.size());

        stack.push("B");
        assertEquals("peek() returns new top", "B", stack.peek());
        assertEquals("size is 2", 2, stack.size());
    }

    // ── Test 3: LIFO Ordering ─────────────────────────────────────────────────

    private static void testLIFOOrdering() {
        LocationStack<Integer> stack = new LocationStack<>();
        stack.push(10);
        stack.push(20);
        stack.push(30);

        assertEquals("First pop is 30", Integer.valueOf(30), stack.pop());
        assertEquals("Second pop is 20", Integer.valueOf(20), stack.pop());
        assertEquals("Third pop is 10", Integer.valueOf(10), stack.pop());
        assertTrue("Stack empty after popping all", stack.isEmpty());
    }

    // ── Test 4: Clear ─────────────────────────────────────────────────────────

    private static void testClear() {
        LocationStack<String> stack = new LocationStack<>();
        stack.push("one");
        stack.push("two");
        stack.clear();

        assertTrue("Stack empty after clear", stack.isEmpty());
        assertEquals("Size is 0 after clear", 0, stack.size());
    }

    // ── Test 5: Null Push Throws ──────────────────────────────────────────────

    private static void testNullPushThrows() {
        LocationStack<String> stack = new LocationStack<>();
        boolean threw = false;
        try {
            stack.push(null);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        assertTrue("push(null) must throw IllegalArgumentException", threw);
    }

    // ── Test 6: ToList Order ──────────────────────────────────────────────────

    private static void testToListOrder() {
        LocationStack<String> stack = new LocationStack<>();
        stack.push("bottom");
        stack.push("middle");
        stack.push("top");

        List<String> list = stack.toList();
        assertEquals("List size matches stack size", 3, list.size());
        assertEquals("Index 0 is top", "top", list.get(0));
        assertEquals("Index 1 is middle", "middle", list.get(1));
        assertEquals("Index 2 is bottom", "bottom", list.get(2));
    }

    // ── Test 7: Campus Navigator Linear Movement ──────────────────────────────

    private static void testCampusNavigatorLinearMovement() {
        LocationNode campus = new LocationNode("vit", "VIT Pune", LocationType.CAMPUS);
        LocationNode bBlock = new LocationNode("b-block", "B Building", LocationType.BUILDING);
        LocationNode f1     = new LocationNode("f1", "First Floor", LocationType.FLOOR);
        LocationNode b107   = new LocationNode("b107", "B107", LocationType.LAB);

        CampusNavigator nav = new CampusNavigator(campus);
        assertEquals("Current location is campus", campus, nav.getCurrentLocation());
        assertFalse("Cannot go back from initial location", nav.canGoBack());

        nav.visit(bBlock);
        nav.visit(f1);
        nav.visit(b107);

        assertEquals("Current location is B107", b107, nav.getCurrentLocation());
        assertTrue("Can go back", nav.canGoBack());
        assertEquals("History depth is 3", 3, nav.getHistoryDepth());

        String crumbs = nav.getBreadcrumbPath(" -> ");
        assertEquals("Breadcrumb trail matches linear path",
                "VIT Pune -> B Building -> First Floor -> B107", crumbs);
    }

    // ── Test 8: Campus Navigator Back and Forward ─────────────────────────────

    private static void testCampusNavigatorBackAndForward() {
        LocationNode loc1 = new LocationNode("1", "A", LocationType.CAMPUS);
        LocationNode loc2 = new LocationNode("2", "B", LocationType.BUILDING);
        LocationNode loc3 = new LocationNode("3", "C", LocationType.ROOM);

        CampusNavigator nav = new CampusNavigator(loc1);
        nav.visit(loc2);
        nav.visit(loc3);

        LocationNode backed = nav.goBack();
        assertEquals("Backed out to B", loc2, backed);
        assertEquals("Current is B", loc2, nav.getCurrentLocation());
        assertTrue("Can now go forward", nav.canGoForward());

        LocationNode forward = nav.goForward();
        assertEquals("Forward restored C", loc3, forward);
        assertEquals("Current is C", loc3, nav.getCurrentLocation());
        assertFalse("Cannot go forward again", nav.canGoForward());
    }

    // ── Test 9: Navigator Branching Clears Forward ────────────────────────────

    private static void testNavigatorBranchingClearsForward() {
        LocationNode loc1 = new LocationNode("1", "A", LocationType.CAMPUS);
        LocationNode loc2 = new LocationNode("2", "B", LocationType.BUILDING);
        LocationNode loc3 = new LocationNode("3", "C", LocationType.ROOM);
        LocationNode loc4 = new LocationNode("4", "D", LocationType.ROOM);

        CampusNavigator nav = new CampusNavigator(loc1);
        nav.visit(loc2);
        nav.visit(loc3);

        nav.goBack(); // back to loc2
        assertTrue("Can go forward before branch", nav.canGoForward());

        nav.visit(loc4); // visit D instead
        assertFalse("Visiting branch must clear forward history", nav.canGoForward());
        assertEquals("Current location is D", loc4, nav.getCurrentLocation());
    }

    // ── Test 10: Iterative DFS Matches Recursive DFS ──────────────────────────

    private static void testIterativeDfsMatchesRecursiveDfs() {
        CampusTree tree = new CampusTree();
        tree.seedDefaultCampus();

        List<LocationNode> recursive = tree.dfsTraversal();
        List<LocationNode> iterative = tree.dfsIterative();

        assertEquals("Both DFS methods must visit same number of nodes",
                recursive.size(), iterative.size());

        for (int i = 0; i < recursive.size(); i++) {
            assertEquals("DFS node match at index " + i,
                    recursive.get(i).getId(), iterative.get(i).getId());
        }
    }

    // ── Test 11: Stress Push Pop ──────────────────────────────────────────────

    private static void testStressPushPop() {
        LocationStack<Integer> stack = new LocationStack<>();
        int count = 10000;
        for (int i = 0; i < count; i++) {
            stack.push(i);
        }
        assertEquals("Stack holds 10000 items", count, stack.size());

        for (int i = count - 1; i >= 0; i--) {
            assertEquals("Stress pop order check", Integer.valueOf(i), stack.pop());
        }
        assertTrue("Stack empty after stress test", stack.isEmpty());
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

    private static void pass(String message) {
        passed++;
        System.out.println("  PASS [" + message + "]");
    }

    private static void fail(String message) {
        failed++;
        System.err.println("  FAIL [" + message + "]");
    }
}

