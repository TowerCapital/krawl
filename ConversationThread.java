import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Doubly-Linked List implementation representing a single peer-to-peer conversation thread.
 *
 * <p>Models a chat history between two users exactly like WhatsApp or iMessage:
 * messages are appended at the tail and can be traversed in either direction —
 * oldest to newest (forward) or newest to oldest (backward scroll).
 *
 * <p><b>Academic Complexity Summary:</b>
 * <ul>
 *   <li>{@link #append}: Time O(1), Space O(1)</li>
 *   <li>{@link #getLatest}: Time O(1), Space O(1)</li>
 *   <li>{@link #getOldest}: Time O(1), Space O(1)</li>
 *   <li>{@link #getLastN}: Time O(K), Space O(K)</li>
 *   <li>{@link #size}: Time O(1), Space O(1)</li>
 * </ul>
 */
public class ConversationThread {

    private final String localUserId;
    private final String peerUserId;

    /** Sentinel head node (dummy). First real message is head.next. */
    private final ChatNode head;

    /** Sentinel tail node (dummy). Last real message is tail.prev. */
    private final ChatNode tail;

    /** Total number of messages (excluding sentinel nodes). */
    private int size;

    /**
     * Constructs an empty conversation thread between two users.
     *
     * @param localUserId the user running this application
     * @param peerUserId  the peer on the other end
     */
    public ConversationThread(String localUserId, String peerUserId) {
        this.localUserId = localUserId;
        this.peerUserId  = peerUserId;
        this.size        = 0;

        // Set up dummy sentinel nodes to eliminate null checks during insert/remove
        this.head = new ChatNode(Message.create(MessageType.CHAT, "__sentinel__", "__sentinel__", "HEAD"));
        this.tail = new ChatNode(Message.create(MessageType.CHAT, "__sentinel__", "__sentinel__", "TAIL"));
        head.next = tail;
        tail.prev = head;
    }

    // ── Core Operations ───────────────────────────────────────────────────────

    /**
     * Appends a new message at the end (tail) of the conversation thread.
     *
     * <p><b>Time Complexity:</b> O(1) — direct tail pointer update
     * <br><b>Space Complexity:</b> O(1)
     *
     * @param message the message to add to this thread
     */
    public void append(Message message) {
        if (message == null) throw new IllegalArgumentException("Message cannot be null.");
        ChatNode newNode = new ChatNode(message);
        ChatNode before  = tail.prev;

        before.next  = newNode;
        newNode.prev = before;
        newNode.next = tail;
        tail.prev    = newNode;
        size++;
    }

    /**
     * Returns the most recent (latest) message without removing it.
     *
     * <p><b>Time Complexity:</b> O(1)
     *
     * @return latest {@link Message}
     * @throws NoSuchElementException if thread is empty
     */
    public Message getLatest() {
        if (isEmpty()) throw new NoSuchElementException("Conversation thread is empty.");
        return tail.prev.getMessage();
    }

    /**
     * Returns the oldest (first) message in the thread without removing it.
     *
     * <p><b>Time Complexity:</b> O(1)
     *
     * @return oldest {@link Message}
     * @throws NoSuchElementException if thread is empty
     */
    public Message getOldest() {
        if (isEmpty()) throw new NoSuchElementException("Conversation thread is empty.");
        return head.next.getMessage();
    }

    /**
     * Returns the last N messages in chronological order (oldest first).
     *
     * <p>Traverses backwards from the tail and reverses for natural reading order.
     *
     * <p><b>Time Complexity:</b> O(K) where K = min(N, size)
     * <br><b>Space Complexity:</b> O(K)
     *
     * @param n number of recent messages to retrieve
     * @return list of up to N messages, oldest first
     */
    public List<Message> getLastN(int n) {
        if (n <= 0) return Collections.emptyList();
        List<Message> result = new ArrayList<>();
        ChatNode curr = tail.prev;
        int collected = 0;

        while (curr != head && collected < n) {
            result.add(curr.getMessage());
            curr = curr.prev;
            collected++;
        }
        Collections.reverse(result); // convert newest-first to oldest-first
        return result;
    }

    /**
     * Returns ALL messages in chronological order (oldest to newest).
     *
     * <p><b>Time Complexity:</b> O(N)
     *
     * @return full conversation history list
     */
    public List<Message> getAll() {
        List<Message> result = new ArrayList<>(size);
        ChatNode curr = head.next;
        while (curr != tail) {
            result.add(curr.getMessage());
            curr = curr.next;
        }
        return result;
    }

    public boolean isEmpty() { return size == 0; }
    public int     size()    { return size; }
    public String  getLocalUserId() { return localUserId; }
    public String  getPeerUserId()  { return peerUserId; }

    @Override
    public String toString() {
        return String.format("ConversationThread[%s ↔ %s, %d messages]",
                localUserId, peerUserId, size);
    }
}

