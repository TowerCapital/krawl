import java.util.UUID;

/**
 * Immutable POJO representing one application-level MeshLink message.
 *
 * This class belongs to the MESSAGE layer; it knows nothing about
 * sockets, TCP frames, or UDP discovery.
 */
public final class Message {

    private final String      messageId;
    private final MessageType type;
    private final String      senderId;
    private final String      receiverId;
    private final long        timestamp;
    private final String      payload;

    /**
     * Full constructor used by MessageService and MessageCodec.
     * The caller is responsible for supplying a valid UUID messageId
     * (use {@link #create} for convenience).
     */
    public Message(
            String      messageId,
            MessageType type,
            String      senderId,
            String      receiverId,
            long        timestamp,
            String      payload
    ) {
        this.messageId  = messageId;
        this.type       = type;
        this.senderId   = senderId;
        this.receiverId = receiverId;
        this.timestamp  = timestamp;
        this.payload    = payload;
    }

    /**
     * Factory: creates a new message with a fresh UUID and the current
     * system time as timestamp.
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
                payload
        );
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String      getMessageId()  { return messageId;  }
    public MessageType getType()       { return type;       }
    public String      getSenderId()   { return senderId;   }
    public String      getReceiverId() { return receiverId; }
    public long        getTimestamp()  { return timestamp;  }
    public String      getPayload()    { return payload;    }

    // ── Utility ──────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Message{"
                + "id='"        + messageId  + '\''
                + ", type="     + type
                + ", from='"    + senderId   + '\''
                + ", to='"      + receiverId + '\''
                + ", ts="       + timestamp
                + ", payload='" + payload    + '\''
                + '}';
    }
}
