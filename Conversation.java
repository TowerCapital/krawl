import java.sql.Timestamp;

/**
 * Model representing a record from the `conversations` table.
 *
 * Represents a direct 1-to-1 conversation between a local user and a peer.
 * Mirrors {@link ConversationThread} and {@link ConversationManager}.
 */
public final class Conversation {

    private final long      conversationId;
    private final String    localUserId;
    private final String    peerId;
    private final Timestamp createdAt;
    private final Timestamp lastMessageAt;

    public Conversation(long conversationId, String localUserId, String peerId,
                        Timestamp createdAt, Timestamp lastMessageAt) {
        this.conversationId = conversationId;
        this.localUserId    = localUserId;
        this.peerId         = peerId;
        this.createdAt      = createdAt;
        this.lastMessageAt  = lastMessageAt;
    }

    public Conversation(long conversationId, String localUserId, String peerId, Timestamp createdAt) {
        this(conversationId, localUserId, peerId, createdAt, null);
    }

    public long      getConversationId() { return conversationId; }
    public String    getLocalUserId()    { return localUserId; }
    public String    getPeerId()         { return peerId; }
    public Timestamp getCreatedAt()      { return createdAt; }
    public Timestamp getLastMessageAt()  { return lastMessageAt; }

    @Override
    public String toString() {
        return "Conversation{"
                + "conversationId=" + conversationId
                + ", localUserId='" + localUserId + '\''
                + ", peerId='" + peerId + '\''
                + ", createdAt=" + createdAt
                + ", lastMessageAt=" + lastMessageAt
                + '}';
    }
}

