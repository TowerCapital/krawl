/**
 * Enumeration of message priority levels for the {@link PriorityMessageQueue}.
 *
 * <p>Lower ordinal value = higher urgency.
 * The Min-Heap always dequeues the message with the smallest priority value first.
 */
public enum MessagePriority {

    /** Campus-wide emergency broadcasts (fire drills, alerts). Delivered first. */
    URGENT(1),

    /** Standard peer-to-peer chat messages. */
    NORMAL(2),

    /** Background sync, file notifications, non-critical updates. */
    LOW(3);

    /** Numeric weight used by the Min-Heap comparator. Lower = higher urgency. */
    private final int weight;

    MessagePriority(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}

