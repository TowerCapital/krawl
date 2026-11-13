import java.util.List;
import java.util.NoSuchElementException;

/**
 * Standalone unit test suite for {@link ConversationThread} and {@link ConversationManager} (Doubly-Linked List).
 * Run: javac ConversationTest.java && java ConversationTest
 */
public class ConversationTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Running ConversationThread (Doubly-Linked List) Test Suite ===");
        System.out.println();

        testEmptyThread();
        testAppendAndRetrieve();
        testGetOldestAndLatest();
        testGetLastN();
        testGetAll();
        testMultiplePeers();
        testConversationManager();
        testNullSafety();

        System.out.println();
        System.out.printf("Results: %d passed, %d failed.%n", passed, failed);
        if (failed > 0) { System.err.println("TEST SUITE FAILED"); System.exit(1); }
        else            { System.out.println("ALL TESTS PASSED SUCCESSFULLY"); System.exit(0); }
    }

    private static void testEmptyThread() {
        ConversationThread thread = new ConversationThread("alice", "bob");
        assertTrue("Empty thread isEmpty", thread.isEmpty());
        assertEquals("Empty thread size 0", 0, thread.size());

        boolean threw = false;
        try { thread.getLatest(); } catch (NoSuchElementException e) { threw = true; }
        assertTrue("getLatest on empty throws", threw);

        threw = false;
        try { thread.getOldest(); } catch (NoSuchElementException e) { threw = true; }
        assertTrue("getOldest on empty throws", threw);
    }

    private static void testAppendAndRetrieve() {
        ConversationThread thread = new ConversationThread("alice", "bob");
        Message m1 = Message.create(MessageType.CHAT, "alice", "bob", "Hey Bob!");
        thread.append(m1);

        assertFalse("Not empty after append", thread.isEmpty());
        assertEquals("Size is 1", 1, thread.size());
        assertEquals("Latest payload", "Hey Bob!", thread.getLatest().getPayload());
        assertEquals("Oldest payload", "Hey Bob!", thread.getOldest().getPayload());
    }

    private static void testGetOldestAndLatest() {
        ConversationThread thread = new ConversationThread("alice", "bob");
        Message m1 = Message.create(MessageType.CHAT, "alice", "bob", "First");
        Message m2 = Message.create(MessageType.CHAT, "bob",   "alice", "Second");
        Message m3 = Message.create(MessageType.CHAT, "alice", "bob", "Third");

        thread.append(m1);
        thread.append(m2);
        thread.append(m3);

        assertEquals("Oldest is First",  "First",  thread.getOldest().getPayload());
        assertEquals("Latest is Third",  "Third",  thread.getLatest().getPayload());
        assertEquals("Size is 3", 3, thread.size());
    }

    private static void testGetLastN() {
        ConversationThread thread = new ConversationThread("alice", "bob");
        for (int i = 1; i <= 5; i++) {
            thread.append(Message.create(MessageType.CHAT, "alice", "bob", "Msg " + i));
        }

        List<Message> last3 = thread.getLastN(3);
        assertEquals("getLastN(3) returns 3", 3, last3.size());
        assertEquals("First in last3 is Msg 3", "Msg 3", last3.get(0).getPayload());
        assertEquals("Last in last3 is Msg 5",  "Msg 5", last3.get(2).getPayload());

        List<Message> last100 = thread.getLastN(100);
        assertEquals("getLastN(100) caps at size", 5, last100.size());

        List<Message> last0 = thread.getLastN(0);
        assertTrue("getLastN(0) is empty", last0.isEmpty());
    }

    private static void testGetAll() {
        ConversationThread thread = new ConversationThread("alice", "bob");
        Message m1 = Message.create(MessageType.CHAT, "alice", "bob", "A");
        Message m2 = Message.create(MessageType.CHAT, "bob",   "alice", "B");
        thread.append(m1);
        thread.append(m2);

        List<Message> all = thread.getAll();
        assertEquals("getAll returns 2", 2, all.size());
        assertEquals("First is A", "A", all.get(0).getPayload());
        assertEquals("Second is B", "B", all.get(1).getPayload());
    }

    private static void testMultiplePeers() {
        ConversationThread t1 = new ConversationThread("alice", "bob");
        ConversationThread t2 = new ConversationThread("alice", "carol");

        t1.append(Message.create(MessageType.CHAT, "alice", "bob",   "To Bob"));
        t2.append(Message.create(MessageType.CHAT, "alice", "carol", "To Carol"));

        assertEquals("Bob thread has 1 message", 1, t1.size());
        assertEquals("Carol thread has 1 message", 1, t2.size());
        assertEquals("Bob msg correct",   "To Bob",   t1.getLatest().getPayload());
        assertEquals("Carol msg correct", "To Carol", t2.getLatest().getPayload());
    }

    private static void testConversationManager() {
        ConversationManager mgr = new ConversationManager("alice");
        Message m1 = Message.create(MessageType.CHAT, "alice", "bob",   "Hi Bob");
        Message m2 = Message.create(MessageType.CHAT, "bob",   "alice", "Hi Alice");
        Message m3 = Message.create(MessageType.CHAT, "alice", "carol", "Hey Carol");

        mgr.record("bob",   m1);
        mgr.record("bob",   m2);
        mgr.record("carol", m3);

        assertEquals("2 peer threads", 2, mgr.totalPeers());
        assertTrue("Has bob thread", mgr.hasThread("bob"));
        assertTrue("Has carol thread", mgr.hasThread("carol"));

        List<Message> bobHistory = mgr.getHistory("bob", 10);
        assertEquals("Bob history has 2 messages", 2, bobHistory.size());
        assertEquals("First bob msg", "Hi Bob",   bobHistory.get(0).getPayload());
        assertEquals("Second bob msg","Hi Alice", bobHistory.get(1).getPayload());

        List<Message> last1 = mgr.getHistory("bob", 1);
        assertEquals("Last 1 of bob", "Hi Alice", last1.get(0).getPayload());
    }

    private static void testNullSafety() {
        ConversationThread thread = new ConversationThread("alice", "bob");
        boolean threw = false;
        try { thread.append(null); } catch (IllegalArgumentException e) { threw = true; }
        assertTrue("append(null) throws", threw);

        ConversationManager mgr = new ConversationManager("alice");
        mgr.record(null, Message.create(MessageType.CHAT, "a", "b", "test"));
        assertEquals("Null record ignored", 0, mgr.totalPeers());
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

