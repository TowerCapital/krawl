import java.util.List;

/**
 * Application service for the MeshLink message layer.
 *
 * Responsibilities
 * ────────────────
 * • Create typed {@link Message} objects from user intent.
 * • Encode a {@link Message} for transport via the networking layer.
 * • Store outgoing messages and mark them SENT or FAILED.
 * • Decode incoming wire strings, deduplicate them, store them, and mark
 *   them RECEIVED.
 * • Provide message history queries for the /messages command.
 *
 * What this class does NOT do
 * ────────────────────────────
 * • It does NOT open sockets.
 * • It does NOT know about TCP frames, UDP ports, or discovery.
 * • It does NOT perform SQL I/O (that is MessageStore's future concern).
 *
 * Separation of concerns
 * ──────────────────────
 *   MessageService  = application semantics + lifecycle
 *   MessageCodec    = serialization / wire format
 *   MessageStore    = temporary in-memory persistence (→ SQL later)
 *   PeerConnectionManager = transport (strings across TCP)
 */
public final class MessageService {

    private final MessageStore store;

    public MessageService() {
        this.store = new MessageStore();
    }

    // ── Outbound flow ─────────────────────────────────────────────────────────

    /**
     * Creates a new CHAT message (status = CREATED).
     * The caller is responsible for calling {@link #markSent} or
     * {@link #markFailed} after attempting transmission.
     *
     * @param senderId   the local user's identifier
     * @param receiverId the remote peer's identifier
     * @param text       chat text (may contain spaces, pipes, Unicode, etc.)
     * @return a ready-to-encode {@link Message}, already stored with CREATED
     */
    public Message createChatMessage(
            String senderId,
            String receiverId,
            String text
    ) {
        Message msg = Message.create(MessageType.CHAT, senderId, receiverId, text);
        store.put(msg);          // CREATED — store immediately
        return msg;
    }

    /**
     * Encodes a {@link Message} into a wire string suitable for
     * {@code PeerConnectionManager.sendText()}.
     */
    public String encodeForTransport(Message message) {
        return MessageCodec.encode(message);
    }

    /**
     * Marks a previously-created message as SENT.
     * Call this after {@code PeerConnectionManager.sendText()} returns
     * without throwing.
     */
    public void markSent(Message message) {
        message.setStatus(MessageStatus.SENT);
    }

    /**
     * Marks a previously-created message as FAILED.
     * Call this in the catch block around {@code sendText()}.
     */
    public void markFailed(Message message) {
        message.setStatus(MessageStatus.FAILED);
    }

    // ── Inbound flow ──────────────────────────────────────────────────────────

    /**
     * Processes a raw wire string received from
     * {@code PeerConnectionManager.onText()}.
     *
     * <ol>
     *   <li>Tries to decode as a MeshLink application message.</li>
     *   <li>If decoding fails → returns {@code null} (caller handles as raw).</li>
     *   <li>If the message ID already exists in the store → prints a
     *       DEDUPLICATED notice and returns {@code null}.</li>
     *   <li>Otherwise stores the message with status RECEIVED and returns it.</li>
     * </ol>
     *
     * @param wire the raw string from the transport layer
     * @return the stored {@link Message} (status = RECEIVED), or {@code null}
     *         when the string is not a valid or is a duplicate MeshLink message
     */
    public Message receive(String wire) {
        Message decoded = MessageCodec.decode(wire);
        if (decoded == null) {
            return null;      // not a MeshLink application message
        }

        decoded.setStatus(MessageStatus.RECEIVED);

        boolean stored = store.put(decoded);
        if (!stored) {
            // Duplicate — already processed this messageId.
            System.out.println();
            System.out.println("[DEDUPLICATED]");
            System.out.println("Message already received: " + decoded.getMessageId());
            return null;
        }

        return decoded;
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns all stored messages involving {@code peerId} (as sender or
     * receiver). Used by the /messages command.
     */
    public List<Message> getMessagesWithPeer(String peerId) {
        return store.getByPeer(peerId);
    }

    /**
     * Returns all stored messages. Useful for debugging.
     */
    public List<Message> getAllMessages() {
        return store.getAll();
    }

    /**
     * Returns the underlying store (package-visible for unit tests).
     */
    MessageStore getStore() {
        return store;
    }
}
