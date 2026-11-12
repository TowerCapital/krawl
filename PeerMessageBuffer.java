import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages outgoing {@link Message} queues for peers that are currently disconnected or offline.
 *
 * <p>When a peer is unreachable over TCP, outgoing messages are buffered in a dedicated
 * FIFO {@link PendingMessageQueue}. Upon LAN discovery or reconnection, messages are
 * dequeued in strict chronological order and delivered.
 *
 * <p><b>Academic Complexity Summary:</b>
 * <ul>
 *   <li>{@link #bufferMessage(String, Message)}: Time O(1), Space O(1)</li>
 *   <li>{@link #dequeueNext(String)}: Time O(1), Space O(1)</li>
 *   <li>{@link #flushPending(String)}: Time O(K), Space O(K) where K = pending messages for peer</li>
 *   <li>{@link #getPendingCount(String)}: Time O(1), Space O(1)</li>
 * </ul>
 */
public class PeerMessageBuffer {

    /** Map of peerId -> FIFO queue of pending outgoing messages. */
    private final Map<String, PendingMessageQueue<Message>> peerQueues;

    /**
     * Constructs a thread-safe PeerMessageBuffer.
     */
    public PeerMessageBuffer() {
        this.peerQueues = new ConcurrentHashMap<>();
    }

    /**
     * Buffers an outgoing message for an offline peer.
     *
     * <p><b>Time Complexity:</b> O(1)
     * <br><b>Space Complexity:</b> O(1)
     *
     * @param peerId  recipient peer identifier
     * @param message message entity to buffer
     */
    public void bufferMessage(String peerId, Message message) {
        if (peerId == null || peerId.trim().isEmpty() || message == null) {
            return;
        }
        String normalized = peerId.trim().toLowerCase();
        peerQueues.computeIfAbsent(normalized, k -> new PendingMessageQueue<>())
                  .enqueue(message);
    }

    /**
     * Dequeues the next pending message for the given peer in FIFO order.
     *
     * <p><b>Time Complexity:</b> O(1)
     *
     * @param peerId recipient peer identifier
     * @return oldest pending {@link Message}, or null if none exist
     */
    public Message dequeueNext(String peerId) {
        if (peerId == null) return null;
        String normalized = peerId.trim().toLowerCase();
        PendingMessageQueue<Message> queue = peerQueues.get(normalized);
        if (queue == null || queue.isEmpty()) {
            return null;
        }
        Message msg = queue.dequeue();
        if (queue.isEmpty()) {
            peerQueues.remove(normalized);
        }
        return msg;
    }

    /**
     * Drains and returns all pending messages for a newly reconnected peer
     * in exact FIFO creation sequence.
     *
     * <p><b>Time Complexity:</b> O(K) where K is the number of pending messages
     * <br><b>Space Complexity:</b> O(K)
     *
     * @param peerId recipient peer identifier
     * @return list of pending messages to deliver
     */
    public List<Message> flushPending(String peerId) {
        if (peerId == null) return Collections.emptyList();
        String normalized = peerId.trim().toLowerCase();
        PendingMessageQueue<Message> queue = peerQueues.remove(normalized);
        if (queue == null || queue.isEmpty()) {
            return Collections.emptyList();
        }

        List<Message> flushed = new ArrayList<>(queue.size());
        while (!queue.isEmpty()) {
            flushed.add(queue.dequeue());
        }
        return flushed;
    }

    /**
     * Checks if a peer has any messages buffered waiting for delivery.
     *
     * @param peerId recipient peer identifier
     * @return true if queue exists and is non-empty
     */
    public boolean hasPending(String peerId) {
        if (peerId == null) return false;
        PendingMessageQueue<Message> queue = peerQueues.get(peerId.trim().toLowerCase());
        return queue != null && !queue.isEmpty();
    }

    /**
     * Returns the count of pending messages for a specific peer.
     *
     * @param peerId recipient peer identifier
     * @return number of pending messages
     */
    public int getPendingCount(String peerId) {
        if (peerId == null) return 0;
        PendingMessageQueue<Message> queue = peerQueues.get(peerId.trim().toLowerCase());
        return (queue != null) ? queue.size() : 0;
    }

    /**
     * Returns the total count of pending messages across all offline peers.
     *
     * @return total pending count
     */
    public int getTotalPendingCount() {
        int total = 0;
        for (PendingMessageQueue<Message> queue : peerQueues.values()) {
            total += queue.size();
        }
        return total;
    }

    /**
     * Returns the set of peer IDs that currently have pending messages.
     *
     * @return unmodifiable set of peer IDs
     */
    public Set<String> getPeersWithPending() {
        return Collections.unmodifiableSet(peerQueues.keySet());
    }

    /**
     * Clears any buffered messages for a given peer.
     *
     * @param peerId recipient peer identifier
     */
    public void clear(String peerId) {
        if (peerId != null) {
            peerQueues.remove(peerId.trim().toLowerCase());
        }
    }
}

