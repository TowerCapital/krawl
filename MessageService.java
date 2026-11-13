import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Application service for the MeshLink message layer.
 *
 * Responsibilities:
 * <ul>
 *   <li>Create typed {@link Message} objects from user intent.</li>
 *   <li>Encode a {@link Message} for transport via {@link MessageCodec}.</li>
 *   <li>Persist outgoing messages with status CREATED to MySQL before transmission.</li>
 *   <li>Update persisted status to SENT on successful transmission or FAILED on failure.</li>
 *   <li>Decode incoming wire strings, deduplicate them against MySQL, and persist with RECEIVED status.</li>
 *   <li>Provide query methods for message history backed by {@link MessageRepository}.</li>
 * </ul>
 *
 * Separation of concerns:
 *   MessageService         = application lifecycle & persistence orchestration
 *   MessageCodec           = serialization / wire format (untouched)
 *   MessageRepository      = persistent database operations (MySQL)
 *   ConversationRepository = conversation thread management (MySQL)
 *   PeerConnectionManager  = transport (strings across TCP)
 */
public final class MessageService {

    private final MessageRepository      messageRepo;
    private final ConversationRepository conversationRepo;
    private final String                 localUserId;
    private final MessageStore           fallbackStore;

    /**
     * Default constructor for backward-compatible in-memory unit tests (e.g. MeshLinkTest).
     * Uses {@link InMemoryMessageRepository} without requiring an active MySQL instance.
     */
    public MessageService() {
        this.fallbackStore    = new MessageStore();
        this.messageRepo      = new InMemoryMessageRepository(this.fallbackStore);
        this.conversationRepo = null;
        this.localUserId      = null;
    }

    /**
     * Dependency injection constructor with {@link MessageRepository}.
     */
    public MessageService(MessageRepository messageRepo) {
        this(messageRepo, null, null);
    }

    /**
     * Production constructor with persistent repositories and local user identity.
     *
     * @param messageRepo persistence repository for messages
     * @param conversationRepo persistence repository for conversations
     * @param localUserId identity of the authenticated local user
     */
    public MessageService(MessageRepository messageRepo, ConversationRepository conversationRepo, String localUserId) {
        if (messageRepo == null) {
            throw new IllegalArgumentException("MessageRepository cannot be null.");
        }
        this.messageRepo      = messageRepo;
        this.conversationRepo = conversationRepo;
        this.localUserId      = (localUserId != null) ? localUserId.trim().toLowerCase() : null;
        this.fallbackStore    = (messageRepo instanceof InMemoryMessageRepository)
                ? ((InMemoryMessageRepository) messageRepo).getMessageStore()
                : new MessageStore();
    }

    // ── Outbound flow ─────────────────────────────────────────────────────────

    /**
     * Creates and persists a new CHAT message with status {@link MessageStatus#CREATED}.
     * The message is strictly persisted to MySQL before it can be transmitted.
     *
     * @param senderId   the local user's identifier
     * @param receiverId the remote peer's identifier
     * @param text       chat text
     * @return a ready-to-encode {@link Message}, persisted with CREATED status
     * @throws RuntimeException if database persistence fails before transmission
     */
    public Message createChatMessage(String senderId, String receiverId, String text) {
        Message msg = Message.create(MessageType.CHAT, senderId, receiverId, text);
        persistOutgoing(msg);
        return msg;
    }

    /**
     * Persists an outgoing message to the database before transmission.
     * Ensures message is stored with CREATED status.
     */
    public void persistOutgoing(Message msg) {
        String uid = (localUserId != null) ? localUserId : msg.getSenderId();
        try {
            long convId = 0L;
            if (conversationRepo != null) {
                Conversation conv = conversationRepo.createIfAbsent(uid, msg.getReceiverId());
                convId = conv.getConversationId();
            }

            boolean saved = messageRepo.saveIfAbsent(msg, uid, convId);
            if (!saved) {
                System.err.println("[MessageService] Warning: Message ID already exists in store: " + msg.getMessageId());
            }

            fallbackStore.put(msg);
        } catch (SQLException e) {
            throw new RuntimeException("Database error: Failed to persist outgoing message before transmission: " + e.getMessage(), e);
        }
    }

    /**
     * Encodes a {@link Message} into a wire string suitable for
     * {@code PeerConnectionManager.sendText()}.
     */
    public String encodeForTransport(Message message) {
        return MessageCodec.encode(message);
    }

    /**
     * Marks a previously-created message as SENT in memory and in MySQL.
     * Call this after {@code PeerConnectionManager.sendText()} returns without throwing.
     */
    public void markSent(Message message) {
        message.setStatus(MessageStatus.SENT);
        updateDbStatus(message, MessageStatus.SENT);
    }

    /**
     * Marks a previously-created message as FAILED in memory and in MySQL.
     * Call this when transmission fails (e.g. peer offline, IOException).
     */
    public void markFailed(Message message) {
        message.setStatus(MessageStatus.FAILED);
        updateDbStatus(message, MessageStatus.FAILED);
    }

    private void updateDbStatus(Message message, MessageStatus status) {
        try {
            String uid = (localUserId != null) ? localUserId : message.getSenderId();
            messageRepo.updateStatus(message.getMessageId(), uid, status);
        } catch (SQLException e) {
            System.err.println("[MessageService] Warning: Failed to update message status to "
                    + status + " in database: " + e.getMessage());
        }
    }

    // ── Inbound flow ──────────────────────────────────────────────────────────

    /**
     * Processes a raw wire string received from {@code PeerConnectionManager.onText()}.
     *
     * <ol>
     *   <li>Decodes the wire string via {@link MessageCodec}.</li>
     *   <li>Sets status to {@link MessageStatus#RECEIVED}.</li>
     *   <li>Checks for duplicates and persists to MySQL. The database's primary key
     *       constraint acts as the final authority on deduplication under concurrency.</li>
     *   <li>Returns the message only after successful persistence. If duplicate or invalid,
     *       returns {@code null}.</li>
     * </ol>
     *
     * @param wire the raw wire string from the transport layer
     * @return the stored {@link Message} (status = RECEIVED), or {@code null} if invalid or duplicate
     */
    public Message receive(String wire) {
        Message decoded = MessageCodec.decode(wire);
        if (decoded == null) {
            return null; // Not a valid MeshLink application message
        }

        decoded.setStatus(MessageStatus.RECEIVED);

        String uid = (localUserId != null) ? localUserId : decoded.getReceiverId();
        try {
            long convId = 0L;
            if (conversationRepo != null) {
                Conversation conv = conversationRepo.createIfAbsent(uid, decoded.getSenderId());
                convId = conv.getConversationId();
            }

            boolean stored = messageRepo.saveIfAbsent(decoded, uid, convId);
            if (!stored) {
                // Duplicate — already processed this messageId
                System.out.println();
                System.out.println("[DEDUPLICATED]");
                System.out.println("Message already received: " + decoded.getMessageId());
                return null;
            }

            fallbackStore.put(decoded);
            return decoded;

        } catch (SQLException e) {
            System.err.println("[MessageService] Database error persisting received message: " + e.getMessage());
            return null;
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns all stored messages involving {@code peerId} (as sender or receiver).
     */
    public List<Message> getMessagesWithPeer(String peerId) {
        if (conversationRepo != null && localUserId != null) {
            try {
                Optional<Conversation> conv = conversationRepo.findByUserAndPeer(localUserId, peerId);
                if (conv.isPresent()) {
                    return messageRepo.getConversationHistory(conv.get().getConversationId(), localUserId);
                }
            } catch (SQLException e) {
                System.err.println("[MessageService] Error fetching history from database: " + e.getMessage());
            }
        }
        return fallbackStore.getByPeer(peerId);
    }

    /**
     * Returns all stored messages.
     */
    public List<Message> getAllMessages() {
        if (localUserId != null) {
            try {
                return messageRepo.getRecentMessages(localUserId, 1000);
            } catch (SQLException e) {
                System.err.println("[MessageService] Error fetching messages from database: " + e.getMessage());
            }
        }
        return fallbackStore.getAll();
    }

    /**
     * Returns the underlying store for backward-compatible unit tests.
     */
    MessageStore getStore() {
        return fallbackStore;
    }
}
