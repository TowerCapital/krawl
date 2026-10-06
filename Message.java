import java.util.UUID;

/**
 * A single application-level MeshLink message.
 *
 * All identity fields (messageId, type, sender, receiver, timestamp, payload)
 * are immutable. The {@link MessageStatus} field is intentionally mutable
 * because a message progresses through a lifecycle (CREATED → SENT →
 * RECEIVED) after it is constructed.
 *
 * This class belongs to the MESSAGE layer — it knows nothing about sockets,
 * TCP frames, or UDP discovery.
 */
public final class Message {

    // ── Immutable identity fields ─────────────────────────────────────────────

    private final String      messageId;
    private final MessageType type;
    private final String      senderId;
    private final String      receiverId;
    private final long        timestamp;
    private final String      payload;

    // ── Mutable lifecycle state ───────────────────────────────────────────────

    /**
     * Current lifecycle status. Volatile so that reads from the display
     * thread always see the latest value written by the send/receive thread.
     */
    private volatile MessageStatus status;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Full constructor used by {@link MessageCodec} when decoding a wire
     * string. Callers supply an explicit status (typically {@code RECEIVED}).
     */
    public Message(
            String        messageId,
            MessageType   type,
            String        senderId,
            String        receiverId,
            long          timestamp,
            String        payload,
            MessageStatus status
    ) {
        this.messageId  = messageId;
        this.type       = type;
        this.senderId   = senderId;
        this.receiverId = receiverId;
        this.timestamp  = timestamp;
        this.payload    = payload;
        this.status     = status;
    }

    /**
     * Backward-compatible constructor (no status supplied).
     * Status defaults to {@link MessageStatus#CREATED}.
     *
     * This overload keeps {@link MessageCodec} working without changes
     * because codec-decoded messages are given an explicit status by
     * {@link MessageService} right after decoding.
     */
    public Message(
            String      messageId,
            MessageType type,
            String      senderId,
            String      receiverId,
            long        timestamp,
            String      payload
    ) {
        this(messageId, type, senderId, receiverId, timestamp, payload,
                MessageStatus.CREATED);
    }

    /**
     * Factory: creates a new CREATED message with a fresh UUID and the
     * current system time as timestamp.
     */
    public static Message create(
            MessageType type,
            String      senderId,
            String      receiverId,
            String      payload
    ) {
        return new Message(
                UUID.randomUUID().toString(),
                type,
                senderId,
                receiverId,
                System.currentTimeMillis(),
                payload,
                MessageStatus.CREATED
        );
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String        getMessageId()  { return messageId;  }
    public MessageType   getType()       { return type;       }
    public String        getSenderId()   { return senderId;   }
    public String        getReceiverId() { return receiverId; }
    public long          getTimestamp()  { return timestamp;  }
    public String        getPayload()    { return payload;    }
    public MessageStatus getStatus()     { return status;     }

    // ── Status mutation ───────────────────────────────────────────────────────

    /**
     * Advances the message status. Only {@link MessageService} should call
     * this; nothing in the networking layer should touch it.
     */
    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Message{"
                + "id='"        + messageId  + '\''
                + ", type="     + type
                + ", from='"    + senderId   + '\''
                + ", to='"      + receiverId + '\''
                + ", ts="       + timestamp
                + ", status="   + status
                + ", payload='" + payload    + '\''
                + '}';
    }
}
