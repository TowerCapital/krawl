import java.util.List;

/**
 * Lightweight self-contained tests for the MeshLink message layer.
 *
 * No external test framework required — runs with plain:
 *
 *   javac MeshLinkTest.java && java MeshLinkTest
 *
 * Exit code 0 = all tests passed.
 * Exit code 1 = at least one test failed.
 */
public class MeshLinkTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testMessageCreation();
        testEncodeDecodeRoundTrip();
        testUnicodePayload();
        testPipeInPayload();
        testInvalidEncodedMessage();
        testDuplicateMessageIdHandling();
        testMessageStoreRetrieval();
        testMessageStatusLifecycle();

        System.out.println();
        System.out.println("Results: " + passed + " passed, " + failed + " failed.");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ── Test 1: Message creation ──────────────────────────────────────────────

    private static void testMessageCreation() {
        Message m = Message.create(MessageType.CHAT, "alice", "bob", "Hello!");

        assertNotNull("messageId must not be null",  m.getMessageId());
        assertNotNull("senderId must not be null",   m.getSenderId());
        assertEquals ("type must be CHAT",           MessageType.CHAT, m.getType());
        assertEquals ("senderId",                    "alice",          m.getSenderId());
        assertEquals ("receiverId",                  "bob",            m.getReceiverId());
        assertEquals ("payload",                     "Hello!",         m.getPayload());
        assertEquals ("initial status must be CREATED",
                      MessageStatus.CREATED, m.getStatus());
        assertTrue   ("timestamp must be positive",  m.getTimestamp() > 0);
    }

    // ── Test 2: Encode / decode round trip ────────────────────────────────────

    private static void testEncodeDecodeRoundTrip() {
        // Use two separate service instances to simulate sender (Alice) and
        // receiver (Bob) running in different JVMs.
        MessageService sender   = new MessageService();
        MessageService receiver = new MessageService();

        Message original = sender.createChatMessage("alice", "bob", "Hello Bob!");
        String  wire     = sender.encodeForTransport(original);

        // receive() on the receiver side stores and marks RECEIVED
        Message decoded = receiver.receive(wire);

        assertNotNull("decoded must not be null", decoded);
        if (decoded == null) return;  // guard against NPE cascade
        assertEquals ("type",      MessageType.CHAT,        decoded.getType());
        assertEquals ("messageId", original.getMessageId(), decoded.getMessageId());
        assertEquals ("senderId",  "alice",                 decoded.getSenderId());
        assertEquals ("receiverId","bob",                   decoded.getReceiverId());
        assertEquals ("payload",   "Hello Bob!",            decoded.getPayload());
        assertEquals ("timestamp", original.getTimestamp(), decoded.getTimestamp());
        assertEquals ("status after receive", MessageStatus.RECEIVED, decoded.getStatus());
    }

    // ── Test 3: Unicode payload ───────────────────────────────────────────────

    private static void testUnicodePayload() {
        String unicode = "नमस्ते 你好 مرحبا Привет こんにちは";
        Message original = Message.create(MessageType.CHAT, "alice", "bob", unicode);
        String  wire     = MessageCodec.encode(original);
        Message decoded  = MessageCodec.decode(wire);

        assertNotNull("decoded must not be null",        decoded);
        assertEquals ("unicode payload survives round-trip", unicode, decoded.getPayload());
    }

    // ── Test 4: Payload containing pipe character ─────────────────────────────

    private static void testPipeInPayload() {
        String payload = "a|b||c|d|e";
        Message original = Message.create(MessageType.CHAT, "alice", "bob", payload);
        String  wire     = MessageCodec.encode(original);
        Message decoded  = MessageCodec.decode(wire);

        assertNotNull("decoded must not be null",         decoded);
        assertEquals ("pipe payload survives round-trip", payload, decoded.getPayload());
    }

    // ── Test 5: Invalid encoded message returns null ──────────────────────────

    private static void testInvalidEncodedMessage() {
        assertNull("null wire → null",          MessageCodec.decode(null));
        assertNull("empty wire → null",         MessageCodec.decode(""));
        assertNull("random string → null",      MessageCodec.decode("hello world"));
        assertNull("wrong prefix → null",       MessageCodec.decode("BADPREFIX|1|CHAT|a|b|c|0|d"));
        assertNull("wrong version → null",      MessageCodec.decode("MESHMSG|9|CHAT|a|b|c|0|d"));
        assertNull("bad type → null",           MessageCodec.decode("MESHMSG|1|UNKNOWN|a|b|c|0|d"));
        assertNull("too few fields → null",     MessageCodec.decode("MESHMSG|1|CHAT|a|b"));
    }

    // ── Test 6: Duplicate message ID handling ─────────────────────────────────

    private static void testDuplicateMessageIdHandling() {
        // Simulate the receiver side independently.
        MessageService sender   = new MessageService();
        MessageService receiver = new MessageService();

        Message orig = sender.createChatMessage("alice", "bob", "First");
        String  wire = sender.encodeForTransport(orig);

        // First receive on the receiver: should store and return the message.
        Message first = receiver.receive(wire);
        assertNotNull("first receive must return message", first);
        if (first != null) {
            assertEquals("first receive status", MessageStatus.RECEIVED, first.getStatus());
        }

        // Second receive of the SAME wire: must be deduplicated (null returned).
        Message second = receiver.receive(wire);
        assertNull("duplicate receive must return null", second);

        // Receiver store should contain exactly one entry for this messageId.
        List<Message> history = receiver.getMessagesWithPeer("bob");
        int count = 0;
        for (Message m : history) {
            if (m.getMessageId().equals(orig.getMessageId())) count++;
        }
        assertEquals("only one copy of the message ID in receiver store", 1, count);
    }

    // ── Test 7: MessageStore retrieval ────────────────────────────────────────

    private static void testMessageStoreRetrieval() {
        MessageStore store = new MessageStore();

        Message m1 = Message.create(MessageType.CHAT, "alice", "bob",   "Hi");
        Message m2 = Message.create(MessageType.CHAT, "bob",   "alice", "Hey");
        Message m3 = Message.create(MessageType.CHAT, "alice", "carol", "Hello Carol");

        store.put(m1);
        store.put(m2);
        store.put(m3);

        assertEquals("store size", 3, store.size());

        // getById
        Message found = store.getById(m1.getMessageId());
        assertNotNull("getById must find m1", found);
        assertEquals ("getById returns correct payload", "Hi", found.getPayload());

        // getById for unknown ID
        assertNull("getById with unknown id returns null", store.getById("no-such-id"));

        // getByPeer for "bob" — should return m1 and m2 (bob is sender or receiver)
        List<Message> bobMessages = store.getByPeer("bob");
        assertEquals("bob is involved in 2 messages", 2, bobMessages.size());

        // getByPeer for "carol" — only m3
        List<Message> carolMessages = store.getByPeer("carol");
        assertEquals("carol is involved in 1 message", 1, carolMessages.size());

        // duplicate put — same id, must be rejected
        boolean second = store.put(m1);
        assertFalse("duplicate put must return false", second);
        assertEquals("store size unchanged after duplicate", 3, store.size());
    }

    // ── Test 8: Message status lifecycle ─────────────────────────────────────

    private static void testMessageStatusLifecycle() {
        MessageService svc = new MessageService();
        Message        msg = svc.createChatMessage("alice", "bob", "Test");

        assertEquals("initial status is CREATED", MessageStatus.CREATED, msg.getStatus());

        svc.markSent(msg);
        assertEquals("after markSent", MessageStatus.SENT, msg.getStatus());

        // Reset to CREATED to test FAILED path.
        msg.setStatus(MessageStatus.CREATED);
        svc.markFailed(msg);
        assertEquals("after markFailed", MessageStatus.FAILED, msg.getStatus());
    }

    // ── Assertion helpers ─────────────────────────────────────────────────────

    private static void assertEquals(String label, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            System.out.println("  FAIL [" + label + "]"
                    + " expected=" + expected + " got=" + actual);
            failed++;
        } else {
            System.out.println("  PASS [" + label + "]");
            passed++;
        }
    }

    private static void assertEquals(String label, long expected, long actual) {
        assertEquals(label, Long.valueOf(expected), Long.valueOf(actual));
    }

    private static void assertEquals(String label, int expected, int actual) {
        assertEquals(label, Integer.valueOf(expected), Integer.valueOf(actual));
    }

    private static void assertNotNull(String label, Object value) {
        if (value == null) {
            System.out.println("  FAIL [" + label + "] expected non-null but got null");
            failed++;
        } else {
            System.out.println("  PASS [" + label + "]");
            passed++;
        }
    }

    private static void assertNull(String label, Object value) {
        if (value != null) {
            System.out.println("  FAIL [" + label + "] expected null but got " + value);
            failed++;
        } else {
            System.out.println("  PASS [" + label + "]");
            passed++;
        }
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            System.out.println("  FAIL [" + label + "] expected true");
            failed++;
        } else {
            System.out.println("  PASS [" + label + "]");
            passed++;
        }
    }

    private static void assertFalse(String label, boolean condition) {
        assertTrue(label, !condition);
    }
}
