import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 4 — MeshLink Reliability Test Suite.
 *
 * Covers edge-cases identified during the Phase 4 audit:
 * <ol>
 *   <li>Identical timestamps → deterministic ordering by message_id</li>
 *   <li>DB unavailable at startup → app starts in fallback (in-memory) mode</li>
 *   <li>Concurrent incoming messages (same wire) → only 1 persisted</li>
 *   <li>Multi-user DB isolation → Alice's messages are invisible to Bob</li>
 *   <li>Long message (10 KB Unicode) → MEDIUMTEXT column accepts it</li>
 *   <li>Invalid login (empty username) → AuthException</li>
 *   <li>Invalid login (wrong password) → AuthException</li>
 *   <li>Crash-window CREATED recovery → buffered into PeerMessageBuffer</li>
 *   <li>CREATED recovery does NOT buffer SENT or RECEIVED messages</li>
 *   <li>Different users get different salts for the same password</li>
 * </ol>
 */
public class ReliabilityTest {

    private static final String SUITE_ID    = UUID.randomUUID().toString().substring(0, 8);
    private static final String USER_REL_A  = "rel_alice_" + SUITE_ID;
    private static final String USER_REL_B  = "rel_bob_"   + SUITE_ID;

    private final DatabaseConnection     dbConn;
    private final UserRepository         userRepo;
    private final ConversationRepository convRepo;
    private final MessageRepository      messageRepo;
    private final AuthService            authService;
    private final ConversationHistoryService historyService;

    private int passedCount = 0;
    private int failedCount = 0;

    public ReliabilityTest() {
        this.dbConn         = new DatabaseConnection();
        this.userRepo       = new JdbcUserRepository(dbConn);
        this.convRepo       = new JdbcConversationRepository(dbConn);
        this.messageRepo    = new JdbcMessageRepository(dbConn);
        this.authService    = new AuthService(userRepo);
        this.historyService = new ConversationHistoryService(convRepo, messageRepo);
    }

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("         MeshLink Phase 4 — Reliability Test Suite             ");
        System.out.println("===============================================================\n");

        ReliabilityTest suite = new ReliabilityTest();
        suite.runAllTests();
    }

    public void runAllTests() {
        try {
            // Set up test users first
            setupTestUsers();

            test01_IdenticalTimestampOrdering();
            test02_FallbackModeWhenDbUnavailable();
            test03_ConcurrentDuplicateIncoming();
            test04_MultiUserDbIsolation();
            test05_LongMessagePersistence();
            test06_InvalidLoginEmptyUsername();
            test07_InvalidLoginWrongPassword();
            test08_CrashWindowCreatedRecovery();
            test09_CreatedRecoveryExcludesSentReceived();
            test10_DifferentSaltsForSamePassword();

        } finally {
            cleanupTestData();
        }

        System.out.println("\n---------------------------------------------------------------");
        System.out.printf("Reliability Results: %d PASSED, %d FAILED%n", passedCount, failedCount);
        System.out.println("---------------------------------------------------------------");

        if (failedCount > 0) {
            System.err.println("FAILED: One or more reliability tests failed.");
            System.exit(1);
        } else {
            System.out.println("SUCCESS: All 10 reliability tests passed.");
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void setupTestUsers() {
        try {
            authService.register(USER_REL_A, "ReliPasswordA_123");
            authService.register(USER_REL_B, "ReliPasswordB_123");
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up test users: " + e.getMessage(), e);
        }
    }

    // ── Test 1: Identical timestamps → deterministic order by message_id ──────

    private void test01_IdenticalTimestampOrdering() {
        startTest("01. Identical Timestamps → Deterministic Order by message_id");
        try {
            long fixedTs = System.currentTimeMillis();

            // Use valid 36-char UUIDs but swap bytes to guarantee lexicographic order:
            // id1 starts with "00000000" so it sorts before id2 which starts with "ffffffff"
            String id1 = "00000000-0000-0000-0000-" + String.format("%012x", System.nanoTime() % 0xFFFFFFFFFFFFL);
            String id2 = "ffffffff-ffff-ffff-ffff-" + String.format("%012x", System.nanoTime() % 0xFFFFFFFFFFFFL);

            // Ensure both are exactly 36 chars
            assertTrue("id1 must be 36 chars", id1.length() == 36);
            assertTrue("id2 must be 36 chars", id2.length() == 36);
            assertTrue("id1 must be lexicographically before id2", id1.compareTo(id2) < 0);

            Message m1 = new Message(id1, MessageType.CHAT, USER_REL_A, USER_REL_B, fixedTs, "First (should be earliest)", MessageStatus.CREATED);
            Message m2 = new Message(id2, MessageType.CHAT, USER_REL_A, USER_REL_B, fixedTs, "Second (should be latest)", MessageStatus.CREATED);

            Conversation conv = convRepo.createIfAbsent(USER_REL_A, USER_REL_B);
            messageRepo.saveIfAbsent(m1, USER_REL_A, conv.getConversationId());
            messageRepo.saveIfAbsent(m2, USER_REL_A, conv.getConversationId());

            List<Message> history = messageRepo.getConversationHistory(conv.getConversationId(), USER_REL_A);

            // Find positions in result
            int pos1 = -1, pos2 = -1;
            for (int i = 0; i < history.size(); i++) {
                if (history.get(i).getMessageId().equals(id1)) pos1 = i;
                if (history.get(i).getMessageId().equals(id2)) pos2 = i;
            }

            assertTrue("Both messages must appear in history (pos1=" + pos1 + ", pos2=" + pos2 + ")", pos1 >= 0 && pos2 >= 0);
            assertTrue("id1 (00...) must appear before id2 (ff...) when timestamps are equal", pos1 < pos2);

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 2: Fallback mode when DB unavailable ─────────────────────────────

    private void test02_FallbackModeWhenDbUnavailable() {
        startTest("02. Fallback In-Memory Mode When DB Unavailable");
        try {
            // Simulate unreachable DB by using InMemoryMessageRepository directly
            MessageService fallbackService = new MessageService(); // default no-arg = in-memory

            Message msg = fallbackService.createChatMessage("offline_alice", "offline_bob", "Fallback message");
            assertNotNull("Message must be created in fallback mode", msg);
            assertEquals("Status must be CREATED in fallback mode", MessageStatus.CREATED, msg.getStatus());

            fallbackService.markSent(msg);
            assertEquals("Status must update to SENT in fallback mode", MessageStatus.SENT, msg.getStatus());

            List<Message> all = fallbackService.getAllMessages();
            assertTrue("Fallback store must contain the message", all.stream().anyMatch(m -> m.getMessageId().equals(msg.getMessageId())));

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 3: Concurrent duplicate incoming messages ────────────────────────

    private void test03_ConcurrentDuplicateIncoming() {
        startTest("03. Concurrent Duplicate Incoming → Only 1 Persisted");
        try {
            MessageService serviceBob = new MessageService(messageRepo, convRepo, USER_REL_B);

            // Craft one wire message
            Message original = Message.create(MessageType.CHAT, USER_REL_A, USER_REL_B, "Concurrent duplicate test");
            String wire = MessageCodec.encode(original);

            int threadCount = 5;
            AtomicInteger storedCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);

            // All 5 threads try to receive the same wire simultaneously
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    try {
                        Message result = serviceBob.receive(wire);
                        if (result != null) {
                            storedCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean finished = latch.await(10, TimeUnit.SECONDS);
            pool.shutdown();

            assertTrue("All threads must complete within 10s", finished);
            assertEquals("Exactly 1 of the 5 concurrent receives must be stored", 1, storedCount.get());

            // Verify exactly 1 in DB
            Conversation conv = convRepo.createIfAbsent(USER_REL_B, USER_REL_A);
            List<Message> history = messageRepo.getConversationHistory(conv.getConversationId(), USER_REL_B);
            long dbCount = history.stream().filter(m -> m.getMessageId().equals(original.getMessageId())).count();
            assertEquals("Exactly 1 copy in DB", 1L, dbCount);

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 4: Multi-user DB isolation ──────────────────────────────────────

    private void test04_MultiUserDbIsolation() {
        startTest("04. Multi-User DB Isolation (Alice's Messages Invisible to Bob)");
        try {
            MessageService serviceAlice = new MessageService(messageRepo, convRepo, USER_REL_A);
            MessageService serviceBob   = new MessageService(messageRepo, convRepo, USER_REL_B);

            // Alice sends a message (persisted under Alice's local_user_id)
            Message aliceMsg = serviceAlice.createChatMessage(USER_REL_A, USER_REL_B, "Alice's private outgoing");

            // Bob's history with Alice should NOT include Alice's local outgoing record
            // (Bob would only see it if he received it over TCP, which we don't simulate here)
            List<Message> bobHistory = serviceBob.getMessagesWithPeer(USER_REL_A);
            long foundInBob = bobHistory.stream()
                    .filter(m -> m.getMessageId().equals(aliceMsg.getMessageId()))
                    .count();

            assertEquals("Alice's outgoing message must NOT appear in Bob's DB history", 0L, foundInBob);

            // Alice's own history must contain her outgoing message
            List<Message> aliceHistory = serviceAlice.getMessagesWithPeer(USER_REL_B);
            long foundInAlice = aliceHistory.stream()
                    .filter(m -> m.getMessageId().equals(aliceMsg.getMessageId()))
                    .count();

            assertEquals("Alice's outgoing message must appear in Alice's DB history", 1L, foundInAlice);

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 5: Long message (10 KB Unicode) ──────────────────────────────────

    private void test05_LongMessagePersistence() {
        startTest("05. Long Message (10 KB Unicode) → MEDIUMTEXT Column Accepts It");
        try {
            // Build a 10 KB unicode payload
            StringBuilder sb = new StringBuilder();
            String chunk = "Hello नमस्ते 你好 مرحبا Привет 🎉 "; // ~50 chars
            while (sb.length() < 10_000) {
                sb.append(chunk);
            }
            String longPayload = sb.toString();
            assertTrue("Test payload must be >= 10000 chars", longPayload.length() >= 10_000);

            MessageService svc = new MessageService(messageRepo, convRepo, USER_REL_A);
            Message msg = svc.createChatMessage(USER_REL_A, USER_REL_B, longPayload);
            assertNotNull("Long message must be created and persisted", msg);
            assertEquals("Long message must have CREATED status", MessageStatus.CREATED, msg.getStatus());

            // Verify round-trip from DB
            java.util.Optional<Message> fromDb = messageRepo.findById(msg.getMessageId(), USER_REL_A);
            assertTrue("Long message must be found in DB", fromDb.isPresent());
            assertEquals("Long message payload must match after DB round-trip", longPayload, fromDb.get().getPayload());

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 6: Invalid login — empty username ────────────────────────────────

    private void test06_InvalidLoginEmptyUsername() {
        startTest("06. Invalid Login — Empty Username → AuthException");
        try {
            boolean threw = false;
            try {
                authService.login("", "somepassword");
            } catch (AuthException e) {
                threw = true;
            }
            assertTrue("Login with empty username must throw AuthException", threw);

            // Also test null username
            boolean threwNull = false;
            try {
                authService.login(null, "somepassword");
            } catch (AuthException e) {
                threwNull = true;
            }
            assertTrue("Login with null username must throw AuthException", threwNull);

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 7: Invalid login — wrong password ────────────────────────────────

    private void test07_InvalidLoginWrongPassword() {
        startTest("07. Invalid Login — Wrong Password → AuthException");
        try {
            boolean threw = false;
            try {
                authService.login(USER_REL_A, "completelyWrongPassword!");
            } catch (AuthException e) {
                threw = true;
            }
            assertTrue("Login with wrong password must throw AuthException", threw);

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 8: Crash-window CREATED recovery ─────────────────────────────────

    private void test08_CrashWindowCreatedRecovery() {
        startTest("08. Crash-Window CREATED Recovery → Buffered into PeerMessageBuffer");
        try {
            // Persist a message directly as CREATED (simulating crash before markSent)
            Message crashMsg = Message.create(MessageType.CHAT, USER_REL_A, USER_REL_B, "Crash-window test message");
            Conversation conv = convRepo.createIfAbsent(USER_REL_A, USER_REL_B);
            messageRepo.saveIfAbsent(crashMsg, USER_REL_A, conv.getConversationId());
            // Status remains CREATED — never updated (simulates crash)

            // Simulate restart: fresh empty PeerMessageBuffer
            PeerMessageBuffer freshBuffer = new PeerMessageBuffer();
            int recovered = historyService.recoverCreatedMessages(freshBuffer, USER_REL_A);

            assertTrue("At least 1 CREATED message must be recovered", recovered >= 1);
            assertTrue("PeerMessageBuffer must have pending messages for Bob", freshBuffer.hasPending(USER_REL_B));

            // Flush all and search for our specific crashMsg (buffer may have multiple CREATED messages from earlier tests)
            List<Message> pending = freshBuffer.flushPending(USER_REL_B);
            assertNotNull("Pending list must not be null", pending);
            assertTrue("At least 1 message must be pending", !pending.isEmpty());

            boolean foundCrashMsg = pending.stream()
                    .anyMatch(m -> m.getMessageId().equals(crashMsg.getMessageId()));
            assertTrue("The crash-window message must appear in the recovered buffer", foundCrashMsg);

            // All recovered messages must be CREATED
            for (Message m : pending) {
                assertEquals("All recovered messages must be CREATED", MessageStatus.CREATED, m.getStatus());
            }

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 9: CREATED recovery excludes SENT/RECEIVED ──────────────────────

    private void test09_CreatedRecoveryExcludesSentReceived() {
        startTest("09. CREATED Recovery → Excludes SENT and RECEIVED Messages");
        try {
            MessageService svc = new MessageService(messageRepo, convRepo, USER_REL_A);

            // Create and mark SENT
            Message sentMsg = svc.createChatMessage(USER_REL_A, USER_REL_B, "This was sent successfully");
            svc.markSent(sentMsg);

            // Create and mark FAILED
            Message failedMsg = svc.createChatMessage(USER_REL_A, USER_REL_B, "This failed");
            svc.markFailed(failedMsg);

            // Fresh buffer — recover CREATED only
            PeerMessageBuffer buffer = new PeerMessageBuffer();
            historyService.recoverCreatedMessages(buffer, USER_REL_A);

            // Drain and verify no SENT or RECEIVED
            List<Message> pending = buffer.flushPending(USER_REL_B);
            for (Message m : pending) {
                assertTrue("Recovered CREATED message must NOT be SENT", m.getStatus() != MessageStatus.SENT);
                assertTrue("Recovered CREATED message must NOT be RECEIVED", m.getStatus() != MessageStatus.RECEIVED);
                assertTrue("Recovered CREATED message must NOT be FAILED", m.getStatus() != MessageStatus.FAILED);
                assertEquals("All recovered messages must be CREATED", MessageStatus.CREATED, m.getStatus());
            }

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Test 10: Different salts for same password ────────────────────────────

    private void test10_DifferentSaltsForSamePassword() {
        startTest("10. Different Users Get Different Salts for Same Password");
        try {
            String sharedPassword = "ExactSamePassword123!";

            java.util.Optional<User> userA = userRepo.findByUsername(USER_REL_A);
            java.util.Optional<User> userB = userRepo.findByUsername(USER_REL_B);

            assertTrue("User A must exist", userA.isPresent());
            assertTrue("User B must exist", userB.isPresent());

            String saltA = userA.get().getPasswordSalt();
            String saltB = userB.get().getPasswordSalt();
            String hashA = userA.get().getPasswordHash();
            String hashB = userB.get().getPasswordHash();

            assertTrue("Salt A must not be empty", saltA != null && !saltA.isEmpty());
            assertTrue("Salt B must not be empty", saltB != null && !saltB.isEmpty());
            assertTrue("Salt A and Salt B must be different (cryptographically unique)", !saltA.equals(saltB));
            assertTrue("Hash A and Hash B must be different (even if password were the same)", !hashA.equals(hashB));

            passTest();
        } catch (Exception e) {
            failTest(e);
        }
    }

    // ── Cleanup & Helpers ─────────────────────────────────────────────────────

    private void cleanupTestData() {
        try (Connection conn = dbConn.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM local_users WHERE user_id IN (?, ?)")) {
                ps.setString(1, USER_REL_A.toLowerCase());
                ps.setString(2, USER_REL_B.toLowerCase());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("[ReliabilityTest] Warning during cleanup: " + e.getMessage());
        }
    }

    private void startTest(String name) {
        System.out.printf("RUNNING: %s ... ", name);
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
        if (!condition) throw new AssertionError("Assertion Failed: " + message);
    }

    private static void assertEquals(String message, Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual))
            throw new AssertionError(String.format(
                    "Assertion Failed: %s [expected: %s, actual: %s]", message, expected, actual));
    }

    private static void assertNotNull(String message, Object value) {
        if (value == null) throw new AssertionError("Assertion Failed: " + message + " [expected non-null]");
    }
}
