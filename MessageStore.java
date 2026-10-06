import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for MeshLink application messages.
 *
 * Design notes
 * ────────────
 * • Uses {@code messageId} (UUID) as the primary key.
 * • Insertion is idempotent: a second put with the same ID is silently
 *   rejected and {@code false} is returned so the caller can detect it.
 * • All public methods are thread-safe (ConcurrentHashMap).
 * • This class is deliberately simple. It will be replaced or extended
 *   by a SQL-backed repository in a future iteration without requiring
 *   changes to {@link MessageService} — just swap the MessageStore
 *   implementation.
 *
 * Future SQL migration
 * ────────────────────
 * The store maps cleanly to a single table:
 *
 *   CREATE TABLE messages (
 *       message_id  VARCHAR(36) PRIMARY KEY,
 *       type        VARCHAR(20) NOT NULL,
 *       sender_id   VARCHAR(64) NOT NULL,
 *       receiver_id VARCHAR(64) NOT NULL,
 *       timestamp   BIGINT      NOT NULL,
 *       payload     TEXT        NOT NULL,
 *       status      VARCHAR(20) NOT NULL
 *   );
 *
 * Replace the ConcurrentHashMap with JDBC calls and the rest of the
 * application stays unchanged.
 */
public final class MessageStore {

    /** Primary storage: messageId → Message. */
    private final Map<String, Message> store = new ConcurrentHashMap<>();

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Stores a message if its ID has not been seen before.
     *
     * @param message the message to store
     * @return {@code true}  if the message was stored (new ID),
     *         {@code false} if the message ID already existed (duplicate)
     */
    public boolean put(Message message) {
        return store.putIfAbsent(message.getMessageId(), message) == null;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Retrieves a message by its UUID.
     *
     * @param messageId the UUID to look up
     * @return the {@link Message}, or {@code null} if not found
     */
    public Message getById(String messageId) {
        return store.get(messageId);
    }

    /**
     * Returns all messages where {@code peerId} is either the sender or
     * the receiver, in insertion order (best-effort; ConcurrentHashMap
     * does not guarantee ordering — sufficient for a prototype).
     *
     * @param peerId the peer user ID to filter on
     * @return unmodifiable snapshot list
     */
    public List<Message> getByPeer(String peerId) {
        List<Message> result = new ArrayList<>();
        for (Message m : store.values()) {
            if (peerId.equals(m.getSenderId()) || peerId.equals(m.getReceiverId())) {
                result.add(m);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns all stored messages as an unmodifiable snapshot.
     *
     * @return all messages
     */
    public List<Message> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(store.values()));
    }

    /**
     * Returns the number of stored messages.
     */
    public int size() {
        return store.size();
    }
}
