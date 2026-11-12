/**
 * Wraps a {@link Message} with an associated {@link MessagePriority} level.
 *
 * <p>Used by {@link PriorityMessageQueue} to order messages by urgency
 * rather than purely by insertion order.
 *
 * <p>Implements {@link Comparable} so the Min-Heap can compare envelopes:
 * lower priority weight = higher urgency = closer to the heap root.
 */
public class PriorityMessage implements Comparable<PriorityMessage> {

    private final Message         message;
    private final MessagePriority priority;
    private final long            insertionOrder;

    /**
     * Constructs a PriorityMessage envelope.
     *
     * @param message        the underlying {@link Message} to deliver
     * @param priority       the urgency level
     * @param insertionOrder monotonic counter used as tiebreaker (FIFO within same priority)
     */
    public PriorityMessage(Message message, MessagePriority priority, long insertionOrder) {
        if (message == null)  throw new IllegalArgumentException("Message cannot be null.");
        if (priority == null) throw new IllegalArgumentException("Priority cannot be null.");
        this.message        = message;
        this.priority       = priority;
        this.insertionOrder = insertionOrder;
    }

    public Message         getMessage()        { return message; }
    public MessagePriority getPriority()       { return priority; }
    public long            getInsertionOrder() { return insertionOrder; }

    /**
     * Compares first by priority weight (lower = more urgent),
     * then by insertion order (FIFO tiebreaker within the same priority level).
     */
    @Override
    public int compareTo(PriorityMessage other) {
        int cmp = Integer.compare(this.priority.getWeight(), other.priority.getWeight());
        if (cmp != 0) return cmp;
        return Long.compare(this.insertionOrder, other.insertionOrder);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s", priority, message.getPayload());
    }
}

