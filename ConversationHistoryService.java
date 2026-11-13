import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service responsible for reconstructing runtime in-memory data structures from MySQL
 * during application startup.
 *
 * Responsibilities:
 * <ul>
 *   <li>Reconstruct {@link ConversationManager} and {@link ConversationThread} doubly-linked
 *       lists in strict chronological order from persisted MySQL messages.</li>
 *   <li>Recover {@link MessageStatus#FAILED} messages into {@link PeerMessageBuffer} for retry
 *       upon LAN TCP reconnection.</li>
 *   <li>Ensure that duplicate messages are not appended during reconstruction.</li>
 *   <li>Never queue SENT or RECEIVED messages into the offline retry buffer.</li>
 * </ul>
 */
public class ConversationHistoryService {

    private final ConversationRepository conversationRepo;
    private final MessageRepository     messageRepo;

    public ConversationHistoryService(ConversationRepository conversationRepo, MessageRepository messageRepo) {
        if (conversationRepo == null || messageRepo == null) {
            throw new IllegalArgumentException("Repositories cannot be null.");
        }
        this.conversationRepo = conversationRepo;
        this.messageRepo      = messageRepo;
    }

    /**
     * Reconstructs all conversation threads for the local user from MySQL into the provided
     * {@link ConversationManager}.
     *
     * In-memory doubly-linked list structures (Parth's {@link ConversationThread}) are populated
     * in strict chronological order (oldest to newest).
     *
     * @param conversationManager in-memory conversation manager
     * @param localUserId identity of the local user
     * @return total number of reconstructed messages across all threads
     * @throws SQLException on database errors
     */
    public int reconstructConversations(ConversationManager conversationManager, String localUserId) throws SQLException {
        if (conversationManager == null || localUserId == null) {
            return 0;
        }

        String normalizedUser = localUserId.trim().toLowerCase();
        List<Conversation> conversations = conversationRepo.getRecentConversations(normalizedUser, 1000);
        int totalReconstructed = 0;

        for (Conversation conv : conversations) {
            String peerId = conv.getPeerId();
            List<Message> history = messageRepo.getConversationHistory(conv.getConversationId(), normalizedUser);

            // Deduplication guard during reconstruction
            Set<String> seenIds = new HashSet<>();
            ConversationThread existingThread = conversationManager.getThread(peerId);
            if (existingThread != null) {
                for (Message existingMsg : existingThread.getAll()) {
                    seenIds.add(existingMsg.getMessageId());
                }
            }

            for (Message msg : history) {
                if (!seenIds.contains(msg.getMessageId())) {
                    conversationManager.record(peerId, msg);
                    seenIds.add(msg.getMessageId());
                    totalReconstructed++;
                }
            }
        }

        return totalReconstructed;
    }

    /**
     * Recovers all unsent {@link MessageStatus#FAILED} messages belonging to the local user
     * into the offline {@link PeerMessageBuffer}.
     *
     * Guarantees:
     * <ul>
     *   <li>Only messages with status FAILED are buffered.</li>
     *   <li>SENT or RECEIVED messages are strictly excluded.</li>
     *   <li>The original messageId is preserved.</li>
     *   <li>Messages remain in buffer until LAN connection is established (no premature transmission).</li>
     * </ul>
     *
     * @param messageBuffer in-memory peer message buffer
     * @param localUserId identity of the local user
     * @return number of failed messages restored into the buffer
     * @throws SQLException on database errors
     */
    public int recoverFailedMessages(PeerMessageBuffer messageBuffer, String localUserId) throws SQLException {
        if (messageBuffer == null || localUserId == null) {
            return 0;
        }

        String normalizedUser = localUserId.trim().toLowerCase();
        List<Message> failedMessages = messageRepo.getFailedMessages(normalizedUser);
        int recoveredCount = 0;

        for (Message msg : failedMessages) {
            // Strictly enforce FAILED status
            if (msg.getStatus() == MessageStatus.FAILED) {
                // Buffer for the target recipient peer
                messageBuffer.bufferMessage(msg.getReceiverId(), msg);
                recoveredCount++;
            }
        }

        return recoveredCount;
    }

    /**
     * Recovers messages stuck in {@link MessageStatus#CREATED} status after a crash.
     *
     * <p>When the application crashes after persisting a message (CREATED) but before
     * updating its status (SENT or FAILED), the message is left in a CREATED state.
     * Since we cannot determine whether the message was actually delivered, it is
     * treated as potentially-unsent and buffered into {@link PeerMessageBuffer} for
     * re-transmission on peer reconnect.</p>
     *
     * <p>Receiver-side deduplication via the database PRIMARY KEY ensures that if the
     * message <em>was</em> already delivered, the duplicate will be silently rejected
     * on arrival at the recipient.</p>
     *
     * <p>The original messageId is preserved so that the recipient's deduplication guard
     * operates correctly.</p>
     *
     * @param messageBuffer in-memory peer message buffer
     * @param localUserId identity of the local user
     * @return number of CREATED messages queued for retry
     * @throws SQLException on database errors
     */
    public int recoverCreatedMessages(PeerMessageBuffer messageBuffer, String localUserId) throws SQLException {
        if (messageBuffer == null || localUserId == null) {
            return 0;
        }

        String normalizedUser = localUserId.trim().toLowerCase();
        List<Message> createdMessages = messageRepo.getCreatedMessages(normalizedUser);
        int recoveredCount = 0;

        for (Message msg : createdMessages) {
            if (msg.getStatus() == MessageStatus.CREATED) {
                // Buffer as potentially-unsent; receiver deduplication handles the
                // case where the message was already delivered before the crash.
                messageBuffer.bufferMessage(msg.getReceiverId(), msg);
                recoveredCount++;
            }
        }

        return recoveredCount;
    }
}

