import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Automated test suite for the MeshLink JDBC persistence layer.
 *
 * Verifies all 12 persistence requirements:
 * <ol>
 *   <li>Successful MySQL connection</li>
 *   <li>User CRUD</li>
 *   <li>Contact CRUD</li>
 *   <li>Conversation creation and retrieval</li>
 *   <li>Message insertion and retrieval</li>
 *   <li>Duplicate message ID handling</li>
 *   <li>Message status updates</li>
 *   <li>Chronological history ordering</li>
 *   <li>Last-N history query</li>
 *   <li>Cursor-based pagination</li>
 *   <li>Failed message retrieval (recovery)</li>
 *   <li>Foreign-key and composite conversation ownership enforcement</li>
 * </ol>
 *
 * Runs non-destructively against the local MySQL instance using isolated test fixtures.
 */
public final class PersistenceTest {

    private static final String TEST_ALICE = "test_alice_" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_BOB   = "test_bob_"   + UUID.randomUUID().toString().substring(0, 8);

    private final DatabaseConnection   dbConn;
    private final DatabaseInitializer  initializer;
    private final UserRepository       userRepo;
    private final ContactRepository    contactRepo;
    private final ConversationRepository conversationRepo;
    private final MessageRepository    messageRepo;

    private int passedCount = 0;
    private int failedCount = 0;

    public PersistenceTest() {
        this.dbConn           = new DatabaseConnection();
        this.initializer      = new DatabaseInitializer(dbConn);
        this.userRepo         = new JdbcUserRepository(dbConn);
        this.contactRepo      = new JdbcContactRepository(dbConn);
        this.conversationRepo = new JdbcConversationRepository(dbConn);
        this.messageRepo      = new JdbcMessageRepository(dbConn);
    }

    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("   MeshLink JDBC Persistence Layer Automated Test Suite   ");
        System.out.println("==========================================================\n");

        PersistenceTest suite = new PersistenceTest();
        suite.runAllTests();
    }

    public void runAllTests() {
        try {
            test1_SuccessfulConnection();
            test2_UserCrud();
            test3_ContactCrud();
            test4_ConversationCreationAndRetrieval();
            test5_MessageInsertionAndRetrieval();
            test6_DuplicateMessageIdHandling();
            test7_MessageStatusUpdates();
            test8_ChronologicalHistory();
            test9_LastNHistory();
            test10_Pagination();
            test11_FailedMessageRetrieval();
            test12_ForeignKeyEnforcement();
        } finally {
            cleanupTestData();
        }

        System.out.println("\n----------------------------------------------------------");
        System.out.printf("Test Results: %d PASSED, %d FAILED\n", passedCount, failedCount);
        System.out.println("----------------------------------------------------------");

        if (failedCount > 0) {
            System.err.println("FAILED: One or more persistence tests did not pass.");
            System.exit(1);
        } else {
            System.out.println("SUCCESS: All 12 persistence test areas passed completely.");
        }
    }

    // ── Test 1: Successful Connection & Schema Validation ──────────────────────

    private void test1_SuccessfulConnection() {
        startTest("1. Successful MySQL Connection & Schema Validation");
        try {
            boolean reachable = dbConn.isReachable(3);
            assertTrue("MySQL server should be reachable", reachable);

            boolean schemaValid = initializer.validateSchema();
            assertTrue("All 4 required tables should exist in schema", schemaValid);

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 2: User CRUD ──────────────────────────────────────────────────────

    private void test2_UserCrud() {
        startTest("2. User CRUD Operations");
        try {
            User alice = new User(TEST_ALICE, TEST_ALICE, "hash_init", "salt_init", 100000);
            boolean registered = userRepo.register(alice);
            assertTrue("User registration should succeed", registered);

            // Duplicate registration should return false
            boolean duplicate = userRepo.register(alice);
            assertTrue("Duplicate user registration should return false", !duplicate);

            // Find by ID
            Optional<User> foundId = userRepo.findById(TEST_ALICE);
            assertTrue("User should be found by ID", foundId.isPresent());
            assertEquals("User ID must match", TEST_ALICE, foundId.get().getUserId());

            // Find by username
            Optional<User> foundName = userRepo.findByUsername(TEST_ALICE);
            assertTrue("User should be found by username", foundName.isPresent());

            // Update password
            boolean updated = userRepo.updatePassword(TEST_ALICE, "hash_updated", "salt_updated", 200000);
            assertTrue("Password update should succeed", updated);

            User updatedUser = userRepo.findById(TEST_ALICE).orElseThrow(() -> new AssertionError("User lost"));
            assertEquals("Password hash should be updated", "hash_updated", updatedUser.getPasswordHash());
            assertEquals("Iterations should be updated", 200000, updatedUser.getPasswordIterations());

            // Also register Bob for subsequent relationship tests
            userRepo.register(new User(TEST_BOB, TEST_BOB, "hash_bob", "salt_bob", 100000));

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 3: Contact CRUD ───────────────────────────────────────────────────

    private void test3_ContactCrud() {
        startTest("3. Contact CRUD Operations");
        try {
            // Add contact
            boolean added = contactRepo.addContact(TEST_ALICE, TEST_BOB, "Bob's Workstation");
            assertTrue("Adding contact should succeed", added);

            // Find contact
            Optional<Contact> found = contactRepo.findContact(TEST_ALICE, TEST_BOB);
            assertTrue("Contact should be found", found.isPresent());
            assertEquals("Display name must match", "Bob's Workstation", found.get().getDisplayName());

            // Explicit update last seen
            Timestamp customTime = new Timestamp(System.currentTimeMillis() - 5000);
            boolean updated = contactRepo.updateLastSeen(TEST_ALICE, TEST_BOB, customTime);
            assertTrue("Updating last-seen timestamp should succeed", updated);

            // List contacts
            List<Contact> contacts = contactRepo.listContacts(TEST_ALICE);
            assertTrue("Contact list should contain Bob", contacts.size() >= 1);

            // Remove contact
            boolean removed = contactRepo.removeContact(TEST_ALICE, TEST_BOB);
            assertTrue("Removing contact should succeed", removed);
            assertTrue("Contact should not be found after removal", !contactRepo.findContact(TEST_ALICE, TEST_BOB).isPresent());

            // Re-add contact for ongoing tests
            contactRepo.addContact(TEST_ALICE, TEST_BOB, "Bob");

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 4: Conversation Creation & Retrieval ─────────────────────────────

    private void test4_ConversationCreationAndRetrieval() {
        startTest("4. Conversation Creation & Retrieval");
        try {
            // Create if absent
            Conversation conv = conversationRepo.createIfAbsent(TEST_ALICE, TEST_BOB);
            assertTrue("Conversation ID should be > 0", conv.getConversationId() > 0);
            assertEquals("Local user ID must match", TEST_ALICE, conv.getLocalUserId());
            assertEquals("Peer ID must match", TEST_BOB, conv.getPeerId());

            // Idempotent call should return the exact same conversation
            Conversation sameConv = conversationRepo.createIfAbsent(TEST_ALICE, TEST_BOB);
            assertEquals("Conversation IDs must be identical", conv.getConversationId(), sameConv.getConversationId());

            // Find by user and peer
            Optional<Conversation> found = conversationRepo.findByUserAndPeer(TEST_ALICE, TEST_BOB);
            assertTrue("Conversation must be findable", found.isPresent());

            // Retrieve recent conversations
            List<Conversation> recent = conversationRepo.getRecentConversations(TEST_ALICE, 10);
            assertTrue("Recent conversations should not be empty", !recent.isEmpty());

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 5: Message Insertion & Retrieval ─────────────────────────────────

    private void test5_MessageInsertionAndRetrieval() {
        startTest("5. Message Insertion & Retrieval");
        try {
            Conversation conv = conversationRepo.findByUserAndPeer(TEST_ALICE, TEST_BOB).get();
            String msgId = UUID.randomUUID().toString();
            long ts = System.currentTimeMillis();

            Message msg = new Message(msgId, MessageType.CHAT, TEST_ALICE, TEST_BOB, ts, "Hello Bob over JDBC!", MessageStatus.SENT);
            boolean saved = messageRepo.saveIfAbsent(msg, TEST_ALICE, conv.getConversationId());
            assertTrue("Message should be successfully saved", saved);

            // Verify existsById
            assertTrue("Message must exist", messageRepo.existsById(msgId));

            // Retrieve by ID
            Optional<Message> retrieved = messageRepo.findById(msgId, TEST_ALICE);
            assertTrue("Retrieved message should be present", retrieved.isPresent());
            assertEquals("Message ID must match", msgId, retrieved.get().getMessageId());
            assertEquals("Type must match", MessageType.CHAT, retrieved.get().getType());
            assertEquals("Sender ID must match", TEST_ALICE, retrieved.get().getSenderId());
            assertEquals("Receiver ID must match", TEST_BOB, retrieved.get().getReceiverId());
            assertEquals("Payload must match", "Hello Bob over JDBC!", retrieved.get().getPayload());
            assertEquals("Status must match", MessageStatus.SENT, retrieved.get().getStatus());

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 6: Duplicate Message ID Handling ─────────────────────────────────

    private void test6_DuplicateMessageIdHandling() {
        startTest("6. Duplicate Message ID Handling (Idempotency)");
        try {
            Conversation conv = conversationRepo.findByUserAndPeer(TEST_ALICE, TEST_BOB).get();
            String msgId = UUID.randomUUID().toString();
            long ts = System.currentTimeMillis();

            Message msg = new Message(msgId, MessageType.CHAT, TEST_ALICE, TEST_BOB, ts, "Original text", MessageStatus.SENT);
            boolean firstSave = messageRepo.saveIfAbsent(msg, TEST_ALICE, conv.getConversationId());
            assertTrue("First save should succeed", firstSave);

            // Attempt saving again with same ID (different payload to ensure no overwrite)
            Message duplicateMsg = new Message(msgId, MessageType.CHAT, TEST_ALICE, TEST_BOB, ts, "Altered duplicate text", MessageStatus.CREATED);
            boolean secondSave = messageRepo.saveIfAbsent(duplicateMsg, TEST_ALICE, conv.getConversationId());
            assertTrue("Second save with duplicate ID should return false without throwing", !secondSave);

            // Verify original message was untouched
            Message current = messageRepo.findById(msgId, TEST_ALICE).get();
            assertEquals("Original payload should be retained", "Original text", current.getPayload());

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 7: Message Status Updates ────────────────────────────────────────

    private void test7_MessageStatusUpdates() {
        startTest("7. Message Status Lifecycle Updates");
        try {
            Conversation conv = conversationRepo.findByUserAndPeer(TEST_ALICE, TEST_BOB).get();
            String msgId = UUID.randomUUID().toString();

            Message msg = new Message(msgId, MessageType.CHAT, TEST_ALICE, TEST_BOB, System.currentTimeMillis(), "Lifecycle test", MessageStatus.CREATED);
            messageRepo.saveIfAbsent(msg, TEST_ALICE, conv.getConversationId());

            // Transition CREATED -> SENT
            boolean markedSent = messageRepo.updateStatus(msgId, TEST_ALICE, MessageStatus.SENT);
            assertTrue("Status update to SENT should succeed", markedSent);
            assertEquals("Status should be SENT", MessageStatus.SENT, messageRepo.findById(msgId, TEST_ALICE).get().getStatus());

            // Transition SENT -> RECEIVED
            boolean markedRecv = messageRepo.updateStatus(msgId, TEST_ALICE, MessageStatus.RECEIVED);
            assertTrue("Status update to RECEIVED should succeed", markedRecv);
            assertEquals("Status should be RECEIVED", MessageStatus.RECEIVED, messageRepo.findById(msgId, TEST_ALICE).get().getStatus());

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 8: Chronological History Ordering ────────────────────────────────

    private void test8_ChronologicalHistory() {
        startTest("8. Chronological History Ordering (Oldest First)");
        try {
            Conversation conv = conversationRepo.findByUserAndPeer(TEST_ALICE, TEST_BOB).get();
            long baseTs = 1710000000000L;

            // Insert 3 messages with distinct timestamps
            String id1 = UUID.randomUUID().toString();
            String id2 = UUID.randomUUID().toString();
            String id3 = UUID.randomUUID().toString();

            messageRepo.saveIfAbsent(new Message(id1, MessageType.CHAT, TEST_ALICE, TEST_BOB, baseTs + 100, "First", MessageStatus.SENT), TEST_ALICE, conv.getConversationId());
            messageRepo.saveIfAbsent(new Message(id2, MessageType.CHAT, TEST_BOB, TEST_ALICE, baseTs + 200, "Second", MessageStatus.RECEIVED), TEST_ALICE, conv.getConversationId());
            messageRepo.saveIfAbsent(new Message(id3, MessageType.CHAT, TEST_ALICE, TEST_BOB, baseTs + 300, "Third", MessageStatus.SENT), TEST_ALICE, conv.getConversationId());

            List<Message> history = messageRepo.getConversationHistory(conv.getConversationId(), TEST_ALICE);
            assertTrue("History should have at least 3 messages", history.size() >= 3);

            // Verify chronological monotonic ordering
            for (int i = 0; i < history.size() - 1; i++) {
                long tA = history.get(i).getTimestamp();
                long tB = history.get(i + 1).getTimestamp();
                assertTrue("History must be ordered oldest-first (tA <= tB)", tA <= tB);
            }

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 9: Last-N History Query ──────────────────────────────────────────

    private void test9_LastNHistory() {
        startTest("9. Last-N Messages Retrieval");
        try {
            Conversation conv = conversationRepo.findByUserAndPeer(TEST_ALICE, TEST_BOB).get();

            List<Message> last2 = messageRepo.getLastNMessages(conv.getConversationId(), TEST_ALICE, 2);
            assertEquals("Should return exactly 2 messages", 2, last2.size());

            // The returned list must be ordered oldest-first for display
            assertTrue("Last-N messages should be in oldest-first display order",
                    last2.get(0).getTimestamp() <= last2.get(1).getTimestamp());

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 10: Pagination ───────────────────────────────────────────────────

    private void test10_Pagination() {
        startTest("10. Cursor-Based Pagination");
        try {
            Conversation conv = conversationRepo.findByUserAndPeer(TEST_ALICE, TEST_BOB).get();
            List<Message> all = messageRepo.getConversationHistory(conv.getConversationId(), TEST_ALICE);
            assertTrue("Need at least 3 messages for pagination test", all.size() >= 3);

            // Use the timestamp of the last message as cursor
            long cursor = all.get(all.size() - 1).getTimestamp();

            List<Message> older = messageRepo.getPaginatedHistory(conv.getConversationId(), TEST_ALICE, cursor, 2);
            assertTrue("Older messages should be found", !older.isEmpty());
            for (Message m : older) {
                assertTrue("Paginated message timestamp must be strictly older than cursor", m.getTimestamp() < cursor);
            }

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 11: Failed Message Retrieval ─────────────────────────────────────

    private void test11_FailedMessageRetrieval() {
        startTest("11. FAILED Messages Retrieval for Retry Prompts");
        try {
            Conversation conv = conversationRepo.findByUserAndPeer(TEST_ALICE, TEST_BOB).get();
            String failedId = UUID.randomUUID().toString();

            Message failedMsg = new Message(failedId, MessageType.CHAT, TEST_ALICE, TEST_BOB,
                    System.currentTimeMillis(), "Unsent network failure", MessageStatus.FAILED);
            messageRepo.saveIfAbsent(failedMsg, TEST_ALICE, conv.getConversationId());

            List<Message> failedList = messageRepo.getFailedMessages(TEST_ALICE);
            assertTrue("Failed list must not be empty", !failedList.isEmpty());

            boolean containsFailed = failedList.stream().anyMatch(m -> m.getMessageId().equals(failedId));
            assertTrue("Failed message list must contain the recorded failed message", containsFailed);

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 12: Foreign Key & Composite Ownership Enforcement ─────────────────

    private void test12_ForeignKeyEnforcement() {
        startTest("12. Foreign Key & Composite Ownership Enforcement");
        try {
            // Case A: Non-existent conversation_id (FK failure)
            String badMsgId = UUID.randomUUID().toString();
            Message badMsg = new Message(badMsgId, MessageType.CHAT, TEST_ALICE, TEST_BOB,
                    System.currentTimeMillis(), "Invalid conv", MessageStatus.CREATED);

            boolean threwFkError = false;
            try {
                messageRepo.saveIfAbsent(badMsg, TEST_ALICE, 99999999L);
            } catch (SQLIntegrityConstraintViolationException e) {
                threwFkError = true;
            }
            assertTrue("Inserting with non-existent conversation_id must throw FK violation", threwFkError);

            // Case B: Conversation exists, but belongs to Alice, and message claims ownership by Bob
            // (Testing composite FK (conversation_id, local_user_id) -> conversations(conversation_id, local_user_id))
            Conversation aliceConv = conversationRepo.findByUserAndPeer(TEST_ALICE, TEST_BOB).get();
            String spoofedMsgId = UUID.randomUUID().toString();
            Message spoofedMsg = new Message(spoofedMsgId, MessageType.CHAT, TEST_BOB, TEST_ALICE,
                    System.currentTimeMillis(), "Spoofed user ownership", MessageStatus.CREATED);

            boolean threwCompositeFkError = false;
            try {
                messageRepo.saveIfAbsent(spoofedMsg, TEST_BOB, aliceConv.getConversationId());
            } catch (SQLIntegrityConstraintViolationException e) {
                threwCompositeFkError = true;
            }
            assertTrue("Inserting with mismatched local_user_id for conversation must throw composite FK violation", threwCompositeFkError);

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test Helpers ──────────────────────────────────────────────────────────

    private void cleanupTestData() {
        try (Connection conn = dbConn.getConnection()) {
            // Deleting local_users automatically CASCADE-deletes contacts, conversations, and messages
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM local_users WHERE user_id IN (?, ?)")) {
                ps.setString(1, TEST_ALICE);
                ps.setString(2, TEST_BOB);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("[PersistenceTest] Warning during test cleanup: " + e.getMessage());
        }
    }

    private void startTest(String testName) {
        System.out.printf("RUNNING: %s ... ", testName);
    }

    private void passTest() {
        passedCount++;
        System.out.println("PASSED");
    }

    private void failTest(Exception e) {
        failedCount++;
        System.out.println("FAILED");
        System.err.println("  Error: " + e.getMessage());
        e.printStackTrace(System.err);
    }

    private static void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError("Assertion Failed: " + message);
        }
    }

    private static void assertEquals(String message, Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(String.format("Assertion Failed: %s [expected: %s, actual: %s]", message, expected, actual));
        }
    }
}
