import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data access operations for application messages (`messages` table).
 *
 * Keeps database persistence operations separate from business logic.
 * Respects foreign key constraints and preserves strict chronological ordering.
 */
public interface MessageRepository {

    /**
     * Persists a message if its messageId has not been recorded before (idempotent).
     * Enforces that the conversationId belongs to the same localUserId.
     *
     * @param message the message entity to store
     * @param localUserId local user identifier who owns this database entry
     * @param conversationId conversation thread ID
     * @return true if stored (new message), false if messageId was already present (duplicate)
     * @throws SQLException on database errors
     */
    boolean saveIfAbsent(Message message, String localUserId, long conversationId) throws SQLException;

    /**
     * Checks if a message with the specified UUID already exists in the database.
     *
     * @param messageId unique message UUID string
     * @return true if message exists, false otherwise
     * @throws SQLException on database errors
     */
    boolean existsById(String messageId) throws SQLException;

    /**
     * Retrieves a single message by its UUID and owning local user ID.
     *
     * @param messageId unique message UUID string
     * @param localUserId local user identifier (ownership guard)
     * @return Optional containing the Message if found, empty otherwise
     * @throws SQLException on database errors
     */
    Optional<Message> findById(String messageId, String localUserId) throws SQLException;

    /**
     * Updates the delivery lifecycle status of an existing message.
     *
     * @param messageId unique message UUID string
     * @param localUserId local user identifier (ownership guard)
     * @param newStatus target {@link MessageStatus}
     * @return true if updated, false if message was not found
     * @throws SQLException on database errors
     */
    boolean updateStatus(String messageId, String localUserId, MessageStatus newStatus) throws SQLException;

    /**
     * Retrieves the complete chronological history for a conversation thread (oldest first).
     * Uses secondary sorting on messageId for deterministic ordering when timestamps match.
     *
     * @param conversationId unique conversation thread ID
     * @param localUserId local user identifier
     * @return chronological list of messages (oldest to newest)
     * @throws SQLException on database errors
     */
    List<Message> getConversationHistory(long conversationId, String localUserId) throws SQLException;

    /**
     * Retrieves the last N messages in a conversation thread (oldest first).
     *
     * @param conversationId unique conversation thread ID
     * @param localUserId local user identifier
     * @param n maximum number of recent messages to return
     * @return list of up to N messages ordered oldest to newest
     * @throws SQLException on database errors
     */
    List<Message> getLastNMessages(long conversationId, String localUserId, int n) throws SQLException;

    /**
     * Retrieves paginated messages before a specific timestamp cursor (cursor pagination).
     *
     * @param conversationId unique conversation thread ID
     * @param localUserId local user identifier
     * @param beforeTimestamp cursor epoch-millisecond timestamp (fetch messages older than this)
     * @param limit maximum rows to return
     * @return list of messages older than beforeTimestamp, ordered descending by timestamp
     * @throws SQLException on database errors
     */
    List<Message> getPaginatedHistory(long conversationId, String localUserId, long beforeTimestamp, int limit) throws SQLException;

    /**
     * Retrieves all messages marked with {@link MessageStatus#FAILED} for recovery / retry prompts.
     *
     * @param localUserId local user identifier
     * @return list of failed messages
     * @throws SQLException on database errors
     */
    List<Message> getFailedMessages(String localUserId) throws SQLException;

    /**
     * Retrieves recent messages for a user across all conversations.
     *
     * @param localUserId local user identifier
     * @param limit maximum rows to return
     * @return list of recent messages
     * @throws SQLException on database errors
     */
    List<Message> getRecentMessages(String localUserId, int limit) throws SQLException;

    /**
     * Retrieves all messages in CREATED status for the local user.
     *
     * CREATED messages are those that were persisted before transmission but
     * whose status was never updated (e.g. application crashed before markSent/markFailed).
     * On startup these are treated as potentially-unsent and buffered for retry.
     *
     * @param localUserId local user identifier
     * @return list of messages stuck in CREATED status
     * @throws SQLException on database errors
     */
    List<Message> getCreatedMessages(String localUserId) throws SQLException;
}

