import java.util.List;
import java.util.NoSuchElementException;

/**
 * Standalone unit test suite for {@link PriorityMessageQueue} (Min-Heap).
 * Run: javac PriorityMessageQueueTest.java && java PriorityMessageQueueTest
 */
public class PriorityMessageQueueTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Running PriorityMessageQueue (Min-Heap) Test Suite ===");
        System.out.println();

        testEmptyQueue();
        testSingleInsertExtract();
        testPriorityOrdering();
        testFIFOWithinSamePriority();
        testPeekMin();
        testPeekAll();
        testNullSafety();

        System.out.println();
        System.out.printf("Results: %d passed, %d failed.%n", passed, failed);
        if (failed > 0) { System.err.println("TEST SUITE FAILED"); System.exit(1); }
        else            { System.out.println("ALL TESTS PASSED SUCCESSFULLY"); System.exit(0); }
    }

    private static void testEmptyQueue() {
        PriorityMessageQueue pq = new PriorityMessageQueue();
        assertTrue("New queue is empty", pq.isEmpty());
        assertEquals("New queue size is 0", 0, pq.size());

        boolean threw = false;
        try { pq.extractMin(); } catch (NoSuchElementException e) { threw = true; }
        assertTrue("extractMin on empty throws", threw);

        threw = false;
        try { pq.peekMin(); } catch (NoSuchElementException e) { threw = true; }
        assertTrue("peekMin on empty throws", threw);
    }

    private static void testSingleInsertExtract() {
        PriorityMessageQueue pq = new PriorityMessageQueue();
        Message m = Message.create(MessageType.CHAT, "alice", "bob", "Hello");
        pq.insert(m, MessagePriority.NORMAL);

        assertFalse("Not empty after insert", pq.isEmpty());
        assertEquals("Size is 1", 1, pq.size());

        PriorityMessage extracted = pq.extractMin();
        assertNotNull("Extracted not null", extracted);
        assertEquals("Payload matches", "Hello", extracted.getMessage().getPayload());
        assertEquals("Priority matches", MessagePriority.NORMAL, extracted.getPriority());
        assertTrue("Queue empty after extract", pq.isEmpty());
    }

    private static void testPriorityOrdering() {
        PriorityMessageQueue pq = new PriorityMessageQueue();
        Message mLow    = Message.create(MessageType.CHAT, "a", "b", "Low priority");
        Message mUrgent = Message.create(MessageType.CHAT, "a", "b", "FIRE DRILL - EVACUATE");
        Message mNormal = Message.create(MessageType.CHAT, "a", "b", "Normal chat");

        pq.insert(mLow,    MessagePriority.LOW);
        pq.insert(mUrgent, MessagePriority.URGENT);
        pq.insert(mNormal, MessagePriority.NORMAL);

        assertEquals("First out is URGENT",
                "FIRE DRILL - EVACUATE", pq.extractMin().getMessage().getPayload());
        assertEquals("Second out is NORMAL",
                "Normal chat", pq.extractMin().getMessage().getPayload());
        assertEquals("Third out is LOW",
                "Low priority", pq.extractMin().getMessage().getPayload());
        assertTrue("Queue empty", pq.isEmpty());
    }

    private static void testFIFOWithinSamePriority() {
        PriorityMessageQueue pq = new PriorityMessageQueue();
        Message m1 = Message.create(MessageType.CHAT, "a", "b", "First normal");
        Message m2 = Message.create(MessageType.CHAT, "a", "b", "Second normal");
        Message m3 = Message.create(MessageType.CHAT, "a", "b", "Third normal");

        pq.insert(m1, MessagePriority.NORMAL);
        pq.insert(m2, MessagePriority.NORMAL);
        pq.insert(m3, MessagePriority.NORMAL);

        assertEquals("FIFO: first in first out", "First normal",  pq.extractMin().getMessage().getPayload());
        assertEquals("FIFO: second",             "Second normal", pq.extractMin().getMessage().getPayload());
        assertEquals("FIFO: third",              "Third normal",  pq.extractMin().getMessage().getPayload());
    }

    private static void testPeekMin() {
        PriorityMessageQueue pq = new PriorityMessageQueue();
        Message m1 = Message.create(MessageType.CHAT, "a", "b", "Normal");
        Message m2 = Message.create(MessageType.CHAT, "a", "b", "Urgent");

        pq.insert(m1, MessagePriority.NORMAL);
        pq.insert(m2, MessagePriority.URGENT);

        assertEquals("peek shows URGENT first", "Urgent", pq.peekMin().getMessage().getPayload());
        assertEquals("Size unchanged after peek", 2, pq.size());
    }

    private static void testPeekAll() {
        PriorityMessageQueue pq = new PriorityMessageQueue();
        pq.insert(Message.create(MessageType.CHAT, "a", "b", "Low"),    MessagePriority.LOW);
        pq.insert(Message.create(MessageType.CHAT, "a", "b", "Urgent"), MessagePriority.URGENT);
        pq.insert(Message.create(MessageType.CHAT, "a", "b", "Normal"), MessagePriority.NORMAL);

        List<PriorityMessage> all = pq.peekAll();
        assertEquals("peekAll returns 3", 3, all.size());
        assertEquals("First in peekAll is URGENT", MessagePriority.URGENT, all.get(0).getPriority());
        assertEquals("Second is NORMAL",           MessagePriority.NORMAL, all.get(1).getPriority());
        assertEquals("Third is LOW",               MessagePriority.LOW,    all.get(2).getPriority());
        assertEquals("Size unchanged after peekAll", 3, pq.size());
    }

    private static void testNullSafety() {
        PriorityMessageQueue pq = new PriorityMessageQueue();
        boolean threw = false;
        try {
            pq.insert(null, MessagePriority.NORMAL);
        } catch (IllegalArgumentException e) { threw = true; }
        assertTrue("insert(null, priority) throws", threw);
    }

    private static void assertTrue(String m, boolean c) { if (c) pass(m); else fail(m + " (expected true)"); }
    private static void assertFalse(String m, boolean c) { if (!c) pass(m); else fail(m + " (expected false)"); }
    private static void assertEquals(String m, Object e, Object a) {
        if (e == null ? a == null : e.equals(a)) pass(m);
        else fail(m + " (expected [" + e + "], got [" + a + "])");
    }
    private static void assertNotNull(String m, Object o) { if (o != null) pass(m); else fail(m + " (expected non-null)"); }
    private static void pass(String m) { passed++; System.out.println("  PASS [" + m + "]"); }
    private static void fail(String m) { failed++; System.err.println("  FAIL [" + m + "]"); }
}

