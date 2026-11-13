import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory test adapter implementing {@link MessageRepository}.
 *
 * Allows unit tests (such as {@code MeshLinkTest}) to execute completely in-memory
 * without requiring a running MySQL server. Bridges to the legacy {@link MessageStore}.
 */
public class InMemoryMessageRepository implements MessageRepository {

    private final MessageStore store;
    private final Map<String, Long> messageConversations = new ConcurrentHashMap<>();

    public InMemoryMessageRepository(MessageStore store) {
        this.store = (store != null) ? store : new MessageStore();
    }

    public InMemoryMessageRepository() {
        this(new MessageStore());
    }

    public MessageStore getMessageStore() {
        return store;
    }

    @Override
    public boolean saveIfAbsent(Message message, String localUserId, long conversationId) throws SQLException {
        if (message == null) return false;
        boolean added = store.put(message);
        if (added) {
            messageConversations.put(message.getMessageId(), conversationId);
        }
        return added;
    }

    @Override
    public boolean existsById(String messageId) throws SQLException {
        return messageId != null && store.getById(messageId) != null;
    }

    @Override
    public Optional<Message> findById(String messageId, String localUserId) throws SQLException {
        if (messageId == null) return Optional.empty();
        return Optional.ofNullable(store.getById(messageId));
    }

    @Override
    public boolean updateStatus(String messageId, String localUserId, MessageStatus newStatus) throws SQLException {
        Message m = store.getById(messageId);
        if (m != null) {
            m.setStatus(newStatus);
            return true;
        }
        return false;
    }

    @Override
    public List<Message> getConversationHistory(long conversationId, String localUserId) throws SQLException {
        List<Message> all = new ArrayList<>(store.getAll());
        all.sort(Comparator.comparingLong(Message::getTimestamp)
                           .thenComparing(Message::getMessageId));
        return all;
    }

    @Override
    public List<Message> getLastNMessages(long conversationId, String localUserId, int n) throws SQLException {
        List<Message> history = getConversationHistory(conversationId, localUserId);
        if (history.size() <= n) return history;
        return history.subList(history.size() - n, history.size());
    }

    @Override
    public List<Message> getPaginatedHistory(long conversationId, String localUserId, long beforeTimestamp, int limit) throws SQLException {
        List<Message> history = getConversationHistory(conversationId, localUserId);
        List<Message> filtered = history.stream()
                .filter(m -> m.getTimestamp() < beforeTimestamp)
                .sorted(Comparator.comparingLong(Message::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
        return filtered;
    }

    @Override
    public List<Message> getFailedMessages(String localUserId) throws SQLException {
        return store.getAll().stream()
                .filter(m -> m.getStatus() == MessageStatus.FAILED)
                .sorted(Comparator.comparingLong(Message::getTimestamp))
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> getRecentMessages(String localUserId, int limit) throws SQLException {
        return store.getAll().stream()
                .sorted(Comparator.comparingLong(Message::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> getCreatedMessages(String localUserId) throws SQLException {
        return store.getAll().stream()
                .filter(m -> m.getStatus() == MessageStatus.CREATED)
                .sorted(Comparator.comparingLong(Message::getTimestamp))
                .collect(Collectors.toList());
    }
}

