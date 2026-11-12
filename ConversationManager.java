import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages per-peer {@link ConversationThread} doubly-linked list instances.
 *
 * <p>When a new message is sent or received, the ConversationManager appends
 * it to the appropriate thread identified by peerId. Threads are created
 * lazily on first message.
 *
 * <p><b>Academic Complexity Summary:</b>
 * <ul>
 *   <li>{@link #record}: Time O(1) average, Space O(1)</li>
 *   <li>{@link #getThread}: Time O(1) average</li>
 *   <li>{@link #getHistory}: Time O(K)</li>
 * </ul>
 */
public class ConversationManager {

    private final String localUserId;

    /** HashMap: peerId → ConversationThread (doubly-linked list). */
    private final Map<String, ConversationThread> threads;

    public ConversationManager(String localUserId) {
        this.localUserId = localUserId;
        this.threads     = new HashMap<>();
    }

    /**
     * Records a message into the appropriate conversation thread.
     * Creates the thread lazily if it does not yet exist.
     *
     * <p><b>Time Complexity:</b> O(1) average
     *
     * @param peerId  the peer on the other side of this conversation
     * @param message the message to record
     */
    public void record(String peerId, Message message) {
        if (peerId == null || message == null) return;
        String key = peerId.trim().toLowerCase();
        threads.computeIfAbsent(key, k -> new ConversationThread(localUserId, k))
               .append(message);
    }

    /**
     * Returns the full {@link ConversationThread} for a given peer.
     *
     * @param peerId peer identifier
     * @return thread, or null if no messages exist yet
     */
    public ConversationThread getThread(String peerId) {
        if (peerId == null) return null;
        return threads.get(peerId.trim().toLowerCase());
    }

    /**
     * Returns the last N messages in a peer conversation (oldest-first order).
     *
     * @param peerId peer identifier
     * @param n      number of recent messages to fetch
     * @return list of messages, or empty list if no history exists
     */
    public List<Message> getHistory(String peerId, int n) {
        ConversationThread thread = getThread(peerId);
        if (thread == null) return Collections.emptyList();
        return thread.getLastN(n);
    }

    /**
     * Returns all conversations currently tracked.
     *
     * @return unmodifiable map of peerId → ConversationThread
     */
    public Map<String, ConversationThread> getAllThreads() {
        return Collections.unmodifiableMap(threads);
    }

    public boolean hasThread(String peerId) {
        return peerId != null && threads.containsKey(peerId.trim().toLowerCase());
    }

    public int totalPeers() { return threads.size(); }
}

