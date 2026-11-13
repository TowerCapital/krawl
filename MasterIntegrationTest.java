import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Master Integration Test Suite for MeshLink (Phase 3).
 *
 * Verifies all 15 required integration test cases:
 * <ol>
 *   <li>Outgoing message persistence before transmission</li>
 *   <li>Successful send updates status to SENT</li>
 *   <li>Failed send updates status to FAILED</li>
 *   <li>Incoming message persistence</li>
 *   <li>Duplicate incoming message handling</li>
 *   <li>Conversation history restoration after restart</li>
 *   <li>Failed-message recovery after restart</li>
 *   <li>No recovery of SENT messages</li>
 *   <li>Local registration</li>
 *   <li>Successful login</li>
 *   <li>Incorrect password rejection</li>
 *   <li>Duplicate username rejection</li>
 *   <li>Existing codec regression tests</li>
 *   <li>Existing conversation data structure regression tests</li>
 *   <li>Existing networking regression tests</li>
 * </ol>
 */
public class MasterIntegrationTest {

    private static final String SUITE_ID   = UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_USER_A = "master_alice_" + SUITE_ID;
    private static final String TEST_USER_B = "master_bob_"   + SUITE_ID;

    private final DatabaseConnection        dbConn;
    private final UserRepository            userRepo;
    private final ContactRepository         contactRepo;
    private final ConversationRepository    convRepo;
    private final MessageRepository         messageRepo;
    private final AuthService               authService;
    private final ConversationHistoryService historyService;

    private int passedCount = 0;
    private int failedCount = 0;

    public MasterIntegrationTest() {
        this.dbConn         = new DatabaseConnection();
        this.userRepo       = new JdbcUserRepository(dbConn);
        this.contactRepo    = new JdbcContactRepository(dbConn);
        this.convRepo       = new JdbcConversationRepository(dbConn);
        this.messageRepo    = new JdbcMessageRepository(dbConn);
        this.authService    = new AuthService(userRepo);
        this.historyService = new ConversationHistoryService(convRepo, messageRepo);
    }

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("       MeshLink Phase 3 — Master Integration Test Suite        ");
        System.out.println("===============================================================\n");

        MasterIntegrationTest suite = new MasterIntegrationTest();
        suite.runAllTests();
    }

    public void runAllTests() {
        try {
            // Authentication tests first (sets up test users)
            test09_LocalRegistration();
            test10_SuccessfulLogin();
            test11_IncorrectPasswordRejection();
            test12_DuplicateUsernameRejection();

            // Outbound message lifecycle tests
            test01_OutgoingMessagePersistenceBeforeTransmission();
            test02_SuccessfulSendUpdatesStatusToSent();
            test03_FailedSendUpdatesStatusToFailed();

            // Inbound message lifecycle & deduplication tests
            test04_IncomingMessagePersistence();
            test05_DuplicateIncomingMessageHandling();

            // Conversation history & failed message recovery tests
            test06_ConversationHistoryRestorationAfterRestart();
            test07_FailedMessageRecoveryAfterRestart();
            test08_NoRecoveryOfSentMessages();

            // Regression test suites
            test13_CodecRegressionTests();
            test14_ConversationDataStructureRegressionTests();
            test15_NetworkingRegressionTests();

        } finally {
            cleanupTestData();
        }

        System.out.println("\n---------------------------------------------------------------");
        System.out.printf("Master Integration Results: %d PASSED, %d FAILED\n", passedCount, failedCount);
        System.out.println("---------------------------------------------------------------");

        if (failedCount > 0) {
            System.err.println("FAILED: One or more integration tests failed.");
            System.exit(1);
        } else {
            System.out.println("SUCCESS: All 15 master integration tests passed completely.");
        }
    }

    // ── Test 9: Local Registration ────────────────────────────────────────────

    private void test09_LocalRegistration() {
        startTest("09. Local Registration (PBKDF2 Salt & Hash)");
        try {
            User alice = authService.register(TEST_USER_A, "PasswordA_123");
            assertNotNull("Registered user should not be null", alice);
            assertEquals("Username must match", TEST_USER_A, alice.getUsername());
            assertEquals("User ID must match", TEST_USER_A.toLowerCase(), alice.getUserId());
            assertNotNull("Password hash must be generated", alice.getPasswordHash());
            assertNotNull("Password salt must be generated", alice.getPasswordSalt());
            assertTrue("Salt must not be empty", !alice.getPasswordSalt().isEmpty());

            // Verify persistence in MySQL
            Optional<User> persisted = userRepo.findByUsername(TEST_USER_A);
            assertTrue("User must be persisted in MySQL", persisted.isPresent());

            // Also register Bob for subsequent messaging tests
            authService.register(TEST_USER_B, "PasswordB_123");

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 10: Successful Login ─────────────────────────────────────────────

    private void test10_SuccessfulLogin() {
        startTest("10. Successful Login (Constant-Time Verification)");
        try {
            User loggedIn = authService.login(TEST_USER_A, "PasswordA_123");
            assertNotNull("Login must return authenticated User", loggedIn);
            assertEquals("Authenticated username must match", TEST_USER_A, loggedIn.getUsername());

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 11: Incorrect Password Rejection ─────────────────────────────────

    private void test11_IncorrectPasswordRejection() {
        startTest("11. Incorrect Password Rejection");
        try {
            boolean rejected = false;
            try {
                authService.login(TEST_USER_A, "wrongPassword!");
            } catch (AuthException e) {
                rejected = true;
            }
            assertTrue("Login with wrong password must throw AuthException", rejected);

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 12: Duplicate Username Rejection ─────────────────────────────────

    private void test12_DuplicateUsernameRejection() {
        startTest("12. Duplicate Username Rejection");
        try {
            boolean rejected = false;
            try {
                authService.register(TEST_USER_A, "AnotherPassword");
            } catch (AuthException e) {
                rejected = true;
            }
            assertTrue("Registering duplicate username must throw AuthException", rejected);

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 1: Outgoing Message Persistence Before Transmission ──────────────

    private void test01_OutgoingMessagePersistenceBeforeTransmission() {
        startTest("01. Outgoing Message Persistence Before Transmission");
        try {
            MessageService serviceAlice = new MessageService(messageRepo, convRepo, TEST_USER_A);

            // Create message
            Message msg = serviceAlice.createChatMessage(TEST_USER_A, TEST_USER_B, "Hello Bob before sending");
            assertNotNull("Message should be created", msg);
            assertEquals("Initial status must be CREATED", MessageStatus.CREATED, msg.getStatus());

            // Check MySQL directly to verify persistence BEFORE transmission
            Optional<Message> inDb = messageRepo.findById(msg.getMessageId(), TEST_USER_A);
            assertTrue("Message MUST exist in database with CREATED status before sending", inDb.isPresent());
            assertEquals("Database status must be CREATED", MessageStatus.CREATED, inDb.get().getStatus());
            assertEquals("Payload in DB must match", "Hello Bob before sending", inDb.get().getPayload());

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 2: Successful Send Updates Status to SENT ────────────────────────

    private void test02_SuccessfulSendUpdatesStatusToSent() {
        startTest("02. Successful Send Updates Status to SENT");
        try {
            MessageService serviceAlice = new MessageService(messageRepo, convRepo, TEST_USER_A);
            Message msg = serviceAlice.createChatMessage(TEST_USER_A, TEST_USER_B, "Success message");

            // Simulate transport transmission success
            serviceAlice.markSent(msg);

            assertEquals("In-memory status must be updated to SENT", MessageStatus.SENT, msg.getStatus());

            // Check MySQL directly
            Message inDb = messageRepo.findById(msg.getMessageId(), TEST_USER_A).get();
            assertEquals("Database status must be updated to SENT", MessageStatus.SENT, inDb.getStatus());

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 3: Failed Send Updates Status to FAILED ──────────────────────────

    private void test03_FailedSendUpdatesStatusToFailed() {
        startTest("03. Failed Send Updates Status to FAILED");
        try {
            MessageService serviceAlice = new MessageService(messageRepo, convRepo, TEST_USER_A);
            Message msg = serviceAlice.createChatMessage(TEST_USER_A, TEST_USER_B, "Will fail message");

            // Simulate transport transmission failure (e.g. peer disconnected)
            serviceAlice.markFailed(msg);

            assertEquals("In-memory status must be updated to FAILED", MessageStatus.FAILED, msg.getStatus());

            // Check MySQL directly
            Message inDb = messageRepo.findById(msg.getMessageId(), TEST_USER_A).get();
            assertEquals("Database status must be updated to FAILED", MessageStatus.FAILED, inDb.getStatus());

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 4: Incoming Message Persistence ──────────────────────────────────

    private void test04_IncomingMessagePersistence() {
        startTest("04. Incoming Message Persistence (Status RECEIVED)");
        try {
            MessageService serviceBob = new MessageService(messageRepo, convRepo, TEST_USER_B);

            // Alice (on her separate device) creates and encodes an outgoing message
            Message msgFromAlice = Message.create(MessageType.CHAT, TEST_USER_A, TEST_USER_B, "Delivery test for Bob");
            String wire = MessageCodec.encode(msgFromAlice);

            // Bob receives over TCP and persists into his local database
            Message receivedByBob = serviceBob.receive(wire);
            assertNotNull("Received message must not be null", receivedByBob);
            assertEquals("Received message status must be RECEIVED", MessageStatus.RECEIVED, receivedByBob.getStatus());
            assertEquals("Message ID must match original", msgFromAlice.getMessageId(), receivedByBob.getMessageId());

            // Check Bob's database directly
            Optional<Message> inBobDb = messageRepo.findById(msgFromAlice.getMessageId(), TEST_USER_B);
            assertTrue("Incoming message must be persisted in Bob's database", inBobDb.isPresent());
            assertEquals("Status in Bob's DB must be RECEIVED", MessageStatus.RECEIVED, inBobDb.get().getStatus());

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 5: Duplicate Incoming Message Handling ───────────────────────────

    private void test05_DuplicateIncomingMessageHandling() {
        startTest("05. Duplicate Incoming Message Handling (DB Idempotency)");
        try {
            MessageService serviceBob = new MessageService(messageRepo, convRepo, TEST_USER_B);

            // Alice sends a message
            Message msg = Message.create(MessageType.CHAT, TEST_USER_A, TEST_USER_B, "Duplicate test message");
            String wire = MessageCodec.encode(msg);

            // First receive by Bob
            Message first = serviceBob.receive(wire);
            assertNotNull("First receive must succeed", first);

            // Second receive with identical wire string
            Message second = serviceBob.receive(wire);
            assertNull("Duplicate receive must return null (deduplicated by DB)", second);

            // Verify only 1 entry in Bob's message history with Alice
            List<Message> history = serviceBob.getMessagesWithPeer(TEST_USER_A);
            long count = history.stream().filter(m -> m.getMessageId().equals(msg.getMessageId())).count();
            assertEquals("Only 1 copy of the message should exist in Bob's store", 1L, count);

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 6: Conversation History Restoration After Restart ────────────────

    private void test06_ConversationHistoryRestorationAfterRestart() {
        startTest("06. Conversation History Restoration After Restart");
        try {
            // Reconstruct history into a fresh in-memory ConversationManager
            ConversationManager freshManager = new ConversationManager(TEST_USER_A);
            int restoredCount = historyService.reconstructConversations(freshManager, TEST_USER_A);
            assertTrue("Restored messages should be > 0", restoredCount > 0);

            // Verify that ConversationThread exists in memory
            ConversationThread thread = freshManager.getThread(TEST_USER_B);
            assertNotNull("ConversationThread for Bob must exist in memory", thread);
            assertTrue("Thread should contain messages", thread.size() > 0);

            // Verify chronological order (oldest first)
            List<Message> allInThread = thread.getAll();
            for (int i = 0; i < allInThread.size() - 1; i++) {
                long t1 = allInThread.get(i).getTimestamp();
                long t2 = allInThread.get(i + 1).getTimestamp();
                assertTrue("Messages in reconstructed thread must be chronological (t1 <= t2)", t1 <= t2);
            }

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 7: Failed-Message Recovery After Restart ──────────────────────────

    private void test07_FailedMessageRecoveryAfterRestart() {
        startTest("07. Failed-Message Recovery After Restart");
        try {
            // Ensure Alice has a FAILED message in MySQL
            MessageService serviceAlice = new MessageService(messageRepo, convRepo, TEST_USER_A);
            Message failedMsg = serviceAlice.createChatMessage(TEST_USER_A, TEST_USER_B, "Pending offline retry message");
            serviceAlice.markFailed(failedMsg);

            // Simulate restart: fresh empty PeerMessageBuffer
            PeerMessageBuffer freshBuffer = new PeerMessageBuffer();
            int recovered = historyService.recoverFailedMessages(freshBuffer, TEST_USER_A);
            assertTrue("Recovered failed messages count should be >= 1", recovered >= 1);
            assertTrue("PeerMessageBuffer should have pending messages for Bob", freshBuffer.hasPending(TEST_USER_B));

            // Dequeue next to inspect
            Message queued = freshBuffer.dequeueNext(TEST_USER_B);
            assertNotNull("Queued message should be retrieved", queued);
            assertEquals("Queued message must have status FAILED", MessageStatus.FAILED, queued.getStatus());

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 8: No Recovery of SENT Messages ──────────────────────────────────

    private void test08_NoRecoveryOfSentMessages() {
        startTest("08. No Recovery of SENT or RECEIVED Messages");
        try {
            PeerMessageBuffer testBuffer = new PeerMessageBuffer();
            historyService.recoverFailedMessages(testBuffer, TEST_USER_A);

            // Drain all buffered messages and ensure NONE of them are SENT or RECEIVED
            List<Message> buffered = testBuffer.flushPending(TEST_USER_B);
            for (Message m : buffered) {
                assertTrue("Recovered message must NOT be SENT", m.getStatus() != MessageStatus.SENT);
                assertTrue("Recovered message must NOT be RECEIVED", m.getStatus() != MessageStatus.RECEIVED);
                assertEquals("Recovered message must be FAILED", MessageStatus.FAILED, m.getStatus());
            }

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 13: Existing Codec Regression Tests ──────────────────────────────

    private void test13_CodecRegressionTests() {
        startTest("13. MessageCodec Regression Tests (Unicode, Pipes, Framing)");
        try {
            // Unicode round-trip
            String unicode = "Hello नमस्ते 你好 مرحبا Привет 🎉";
            Message m1 = Message.create(MessageType.CHAT, "alice", "bob", unicode);
            Message d1 = MessageCodec.decode(MessageCodec.encode(m1));
            assertNotNull("Decoded unicode message must not be null", d1);
            assertEquals("Unicode payload must survive round-trip", unicode, d1.getPayload());

            // Pipes in payload
            String pipes = "pipe|delimited|text||here";
            Message m2 = Message.create(MessageType.CHAT, "alice", "bob", pipes);
            Message d2 = MessageCodec.decode(MessageCodec.encode(m2));
            assertNotNull("Decoded pipe message must not be null", d2);
            assertEquals("Pipe payload must survive round-trip", pipes, d2.getPayload());

            // Corrupt inputs
            assertNull("null wire must decode to null", MessageCodec.decode(null));
            assertNull("empty wire must decode to null", MessageCodec.decode(""));
            assertNull("bad prefix must decode to null", MessageCodec.decode("INVALID|1|CHAT|a|b|c|1|d"));

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 14: Existing Conversation Data Structure Regression Tests ────────

    private void test14_ConversationDataStructureRegressionTests() {
        startTest("14. ConversationThread Linked List Regression Tests");
        try {
            ConversationThread thread = new ConversationThread("alice", "bob");
            assertTrue("Initial thread must be empty", thread.isEmpty());
            assertEquals("Initial size must be 0", 0, thread.size());

            Message msg1 = Message.create(MessageType.CHAT, "alice", "bob", "Msg 1");
            Message msg2 = Message.create(MessageType.CHAT, "bob", "alice", "Msg 2");
            Message msg3 = Message.create(MessageType.CHAT, "alice", "bob", "Msg 3");

            thread.append(msg1);
            thread.append(msg2);
            thread.append(msg3);

            assertEquals("Size after 3 appends must be 3", 3, thread.size());
            assertEquals("Oldest message must be Msg 1", "Msg 1", thread.getOldest().getPayload());
            assertEquals("Latest message must be Msg 3", "Msg 3", thread.getLatest().getPayload());

            List<Message> last2 = thread.getLastN(2);
            assertEquals("getLastN(2) must return 2 messages", 2, last2.size());
            assertEquals("First in last2 must be Msg 2", "Msg 2", last2.get(0).getPayload());
            assertEquals("Second in last2 must be Msg 3", "Msg 3", last2.get(1).getPayload());

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 15: Existing Networking Regression Tests ─────────────────────────

    private void test15_NetworkingRegressionTests() {
        startTest("15. Networking TCP Transport & Framing Regression Tests");
        try {
            int portA = 19101;
            int portB = 19102;
            final AtomicBoolean connectedB = new AtomicBoolean(false);
            final CountDownLatch receiveLatch = new CountDownLatch(1);
            final String[] receivedWire = new String[1];

            PeerConnectionManager tcpB = new PeerConnectionManager("net_bob", portB, Paths.get("received"), new PeerConnectionManager.Listener() {
                @Override public void onConnected(String peerId) { connectedB.set(true); }
                @Override public void onDisconnected(String peerId) {}
                @Override public void onText(String peerId, String wire) {
                    receivedWire[0] = wire;
                    receiveLatch.countDown();
                }
                @Override public void onFileReceived(String peerId, java.nio.file.Path file) {}
                @Override public void onError(String peerId, Exception error) {}
            });
            tcpB.start();

            PeerConnectionManager tcpA = new PeerConnectionManager("net_alice", portA, Paths.get("received"), new PeerConnectionManager.Listener() {
                @Override public void onConnected(String peerId) {}
                @Override public void onDisconnected(String peerId) {}
                @Override public void onText(String peerId, String wire) {}
                @Override public void onFileReceived(String peerId, java.nio.file.Path file) {}
                @Override public void onError(String peerId, Exception error) {}
            });
            tcpA.start();

            try {
                // Connect A to B on localhost
                PeerListener.PeerInfo bobInfo = new PeerListener.PeerInfo("net_bob", "localhost", java.net.InetAddress.getByName("127.0.0.1"), portB, System.currentTimeMillis());
                tcpA.connectTo(bobInfo);

                // Send a test text frame
                String testMessage = "MESHMSG|1|CHAT|abc|net_alice|net_bob|12345678|aGVsbG8=";
                // Wait briefly for socket handshake
                Thread.sleep(600);
                tcpA.sendText("net_bob", testMessage);

                boolean received = receiveLatch.await(4, TimeUnit.SECONDS);
                assertTrue("TCP text frame must be received by peer", received);
                assertEquals("Wire payload must match", testMessage, receivedWire[0]);

            } finally {
                tcpA.close();
                tcpB.close();
            }

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test Cleanup & Helpers ────────────────────────────────────────────────

    private void cleanupTestData() {
        try (Connection conn = dbConn.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM local_users WHERE user_id IN (?, ?)")) {
                ps.setString(1, TEST_USER_A.toLowerCase());
                ps.setString(2, TEST_USER_B.toLowerCase());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("[MasterIntegrationTest] Warning during test cleanup: " + e.getMessage());
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

    private static void assertNotNull(String message, Object value) {
        if (value == null) {
            throw new AssertionError("Assertion Failed: " + message + " [expected non-null]");
        }
    }

    private static void assertNull(String message, Object value) {
        if (value != null) {
            throw new AssertionError("Assertion Failed: " + message + " [expected null but got: " + value + "]");
        }
    }
}
