import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Data access operations for 1-to-1 conversation threads (`conversations` table).
 *
 * Keeps database persistence operations separate from business logic.
 */
public interface ConversationRepository {

    /**
     * Finds an existing conversation thread for the given local user and peer.
     *
     * @param localUserId local user identifier
     * @param peerId remote peer identifier
     * @return Optional containing the Conversation if found, empty otherwise
     * @throws SQLException on database errors
     */
    Optional<Conversation> findByUserAndPeer(String localUserId, String peerId) throws SQLException;

    /**
     * Creates a new conversation thread if one does not already exist, or updates
     * its {@code last_message_at} timestamp if it already exists (upsert).
     * Mirrors {@code ConversationManager.record() -> computeIfAbsent()}.
     *
     * @param localUserId local user identifier
     * @param peerId remote peer identifier
     * @return the persistent {@link Conversation} record
     * @throws SQLException on database errors
     */
    Conversation createIfAbsent(String localUserId, String peerId) throws SQLException;

    /**
     * Updates the denormalized {@code last_message_at} timestamp for a conversation.
     *
     * @param conversationId unique conversation identifier
     * @param lastMessageAt new message timestamp
     * @return true if updated, false if conversation not found
     * @throws SQLException on database errors
     */
    boolean updateLastMessageTimestamp(long conversationId, Timestamp lastMessageAt) throws SQLException;

    /**
     * Retrieves recent conversations for a local user ordered by most-recent activity.
     *
     * @param localUserId local user identifier
     * @param limit maximum number of conversations to return
     * @return list of recent conversations
     * @throws SQLException on database errors
     */
    List<Conversation> getRecentConversations(String localUserId, int limit) throws SQLException;
}

