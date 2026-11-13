import java.util.List;
import java.util.NoSuchElementException;

/**
 * Lightweight, self-contained unit test suite for {@link PendingMessageQueue} and {@link PeerMessageBuffer}.
 *
 * Runs without third-party frameworks:
 * <pre>
 *   javac PendingMessageQueueTest.java && java PendingMessageQueueTest
 * </pre>
 * Exit code 0 = all tests passed.
 * Exit code 1 = at least one test failed.
 */
public class PendingMessageQueueTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Running PendingMessageQueue Test Suite ===");
        System.out.println();

        testEmptyQueue();
        testEnqueueAndPeek();
        testFIFOOrdering();
        testSingleElementCycle();
        testNullEnqueueThrows();
        testClear();
        testToList();
        testPeerMessageBufferBasic();
        testPeerMessageBufferFlush();
        testIndependentPeerQueues();
        testStressEnqueueDequeue();

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

    // ── Test 1: Empty Queue ───────────────────────────────────────────────────

    private static void testEmptyQueue() {
        PendingMessageQueue<String> queue = new PendingMessageQueue<>();
        assertTrue("New queue must be empty", queue.isEmpty());
        assertEquals("New queue size is 0", 0, queue.size());

        boolean dequeueThrew = false;
        try {
            queue.dequeue();
        } catch (NoSuchElementException e) {
            dequeueThrew = true;
        }
        assertTrue("dequeue() on empty queue must throw NoSuchElementException", dequeueThrew);

        boolean peekThrew = false;
        try {
            queue.peek();
        } catch (NoSuchElementException e) {
            peekThrew = true;
        }
        assertTrue("peek() on empty queue must throw NoSuchElementException", peekThrew);
    }

    // ── Test 2: Enqueue and Peek ──────────────────────────────────────────────

    private static void testEnqueueAndPeek() {
        PendingMessageQueue<String> queue = new PendingMessageQueue<>();
        queue.enqueue("first");

        assertFalse("Queue not empty after enqueue", queue.isEmpty());
        assertEquals("Queue size is 1", 1, queue.size());
        assertEquals("peek() returns front element", "first", queue.peek());
        assertEquals("peek() does not decrement size", 1, queue.size());

        queue.enqueue("second");
        assertEquals("peek() still returns front element", "first", queue.peek());
        assertEquals("Queue size is 2", 2, queue.size());
    }

    // ── Test 3: FIFO Ordering ─────────────────────────────────────────────────

    private static void testFIFOOrdering() {
        PendingMessageQueue<Integer> queue = new PendingMessageQueue<>();
        queue.enqueue(100);
        queue.enqueue(200);
        queue.enqueue(300);

        assertEquals("First dequeue is 100", Integer.valueOf(100), queue.dequeue());
        assertEquals("Second dequeue is 200", Integer.valueOf(200), queue.dequeue());
        assertEquals("Third dequeue is 300", Integer.valueOf(300), queue.dequeue());
        assertTrue("Queue empty after dequeuing all", queue.isEmpty());
    }

    // ── Test 4: Single Element Cycle ──────────────────────────────────────────

    private static void testSingleElementCycle() {
        PendingMessageQueue<String> queue = new PendingMessageQueue<>();
        queue.enqueue("item");
        assertEquals("Dequeued single item", "item", queue.dequeue());
        assertTrue("Queue empty after dequeuing single element", queue.isEmpty());
        assertEquals("Size is 0", 0, queue.size());

        // Re-enqueue after becoming empty
        queue.enqueue("item2");
        assertEquals("Re-enqueued item retrieved", "item2", queue.dequeue());
        assertTrue("Queue empty again", queue.isEmpty());
    }

    // ── Test 5: Null Enqueue Throws ───────────────────────────────────────────

    private static void testNullEnqueueThrows() {
        PendingMessageQueue<String> queue = new PendingMessageQueue<>();
        boolean threw = false;
        try {
            queue.enqueue(null);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        assertTrue("enqueue(null) must throw IllegalArgumentException", threw);
    }

    // ── Test 6: Clear ─────────────────────────────────────────────────────────

    private static void testClear() {
        PendingMessageQueue<String> queue = new PendingMessageQueue<>();
        queue.enqueue("a");
        queue.enqueue("b");
        queue.clear();

        assertTrue("Queue empty after clear()", queue.isEmpty());
        assertEquals("Size is 0 after clear()", 0, queue.size());
    }

    // ── Test 7: ToList ────────────────────────────────────────────────────────

    private static void testToList() {
        PendingMessageQueue<String> queue = new PendingMessageQueue<>();
        queue.enqueue("A");
        queue.enqueue("B");
        queue.enqueue("C");

        List<String> list = queue.toList();
        assertEquals("List size is 3", 3, list.size());
        assertEquals("Index 0 is A (front)", "A", list.get(0));
        assertEquals("Index 1 is B", "B", list.get(1));
        assertEquals("Index 2 is C (tail)", "C", list.get(2));
    }

    // ── Test 8: PeerMessageBuffer Basic ───────────────────────────────────────

    private static void testPeerMessageBufferBasic() {
        PeerMessageBuffer buffer = new PeerMessageBuffer();
        Message m1 = Message.create(MessageType.CHAT, "parth", "bob", "Hello Bob!");
        Message m2 = Message.create(MessageType.CHAT, "parth", "bob", "Are you in B107?");

        assertFalse("Bob has no pending initially", buffer.hasPending("bob"));
        buffer.bufferMessage("bob", m1);
        buffer.bufferMessage("bob", m2);

        assertTrue("Bob has pending messages", buffer.hasPending("bob"));
        assertEquals("Bob pending count is 2", 2, buffer.getPendingCount("bob"));
        assertEquals("Total pending count is 2", 2, buffer.getTotalPendingCount());

        Message next = buffer.dequeueNext("bob");
        assertNotNull("Dequeued next message", next);
        assertEquals("Dequeued message is m1 (FIFO)", "Hello Bob!", next.getPayload());
        assertEquals("Bob remaining count is 1", 1, buffer.getPendingCount("bob"));
    }

    // ── Test 9: PeerMessageBuffer Flush ───────────────────────────────────────

    private static void testPeerMessageBufferFlush() {
        PeerMessageBuffer buffer = new PeerMessageBuffer();
        Message m1 = Message.create(MessageType.CHAT, "parth", "alice", "Msg 1");
        Message m2 = Message.create(MessageType.CHAT, "parth", "alice", "Msg 2");
        Message m3 = Message.create(MessageType.CHAT, "parth", "alice", "Msg 3");

        buffer.bufferMessage("alice", m1);
        buffer.bufferMessage("alice", m2);
        buffer.bufferMessage("alice", m3);

        List<Message> flushed = buffer.flushPending("alice");
        assertEquals("Flushed batch size is 3", 3, flushed.size());
        assertEquals("Batch 0 is Msg 1", "Msg 1", flushed.get(0).getPayload());
        assertEquals("Batch 1 is Msg 2", "Msg 2", flushed.get(1).getPayload());
        assertEquals("Batch 2 is Msg 3", "Msg 3", flushed.get(2).getPayload());

        assertFalse("Alice queue cleared after flush", buffer.hasPending("alice"));
        assertEquals("Alice pending count is 0", 0, buffer.getPendingCount("alice"));
    }

    // ── Test 10: Independent Peer Queues ──────────────────────────────────────

    private static void testIndependentPeerQueues() {
        PeerMessageBuffer buffer = new PeerMessageBuffer();
        Message mBob = Message.create(MessageType.CHAT, "parth", "bob", "To Bob");
        Message mCarol = Message.create(MessageType.CHAT, "parth", "carol", "To Carol");

        buffer.bufferMessage("bob", mBob);
        buffer.bufferMessage("carol", mCarol);

        assertEquals("Total pending is 2", 2, buffer.getTotalPendingCount());
        assertTrue("Peers with pending contains bob", buffer.getPeersWithPending().contains("bob"));
        assertTrue("Peers with pending contains carol", buffer.getPeersWithPending().contains("carol"));

        Message dequeuedBob = buffer.dequeueNext("bob");
        assertEquals("Dequeued Bob message", mBob, dequeuedBob);
        assertFalse("Bob has no more pending", buffer.hasPending("bob"));
        assertTrue("Carol still has pending message", buffer.hasPending("carol"));
        assertEquals("Carol message intact", mCarol, buffer.dequeueNext("carol"));
    }

    // ── Test 11: Stress Enqueue Dequeue ───────────────────────────────────────

    private static void testStressEnqueueDequeue() {
        PendingMessageQueue<Integer> queue = new PendingMessageQueue<>();
        int count = 10000;
        for (int i = 0; i < count; i++) {
            queue.enqueue(i);
        }
        assertEquals("Queue holds 10000 items", count, queue.size());

        for (int i = 0; i < count; i++) {
            assertEquals("Stress FIFO order check", Integer.valueOf(i), queue.dequeue());
        }
        assertTrue("Queue empty after stress test", queue.isEmpty());
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

    private static void pass(String message) {
        passed++;
        System.out.println("  PASS [" + message + "]");
    }

    private static void fail(String message) {
        failed++;
        System.err.println("  FAIL [" + message + "]");
    }
}

