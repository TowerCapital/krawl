/**
 * Doubly-linked node for the {@link ConversationThread} chat history list.
 *
 * <p>Each node stores a {@link Message} and bidirectional pointers to
 * its predecessor and successor in the conversation timeline.
 */
public class ChatNode {

    /** The message stored at this position in the conversation. */
    final Message    message;

    /** Pointer to the previous (older) message in the thread. */
    ChatNode prev;

    /** Pointer to the next (newer) message in the thread. */
    ChatNode next;

    public ChatNode(Message message) {
        if (message == null) throw new IllegalArgumentException("Message must not be null.");
        this.message = message;
        this.prev    = null;
        this.next    = null;
    }

    public Message getMessage() { return message; }

    @Override
    public String toString() {
        return String.format("[%s] %s → %s: %s (%s)",
                message.getType(),
                message.getSenderId(),
                message.getReceiverId(),
                message.getPayload(),
                message.getStatus());
    }
}

