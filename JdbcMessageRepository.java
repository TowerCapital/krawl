import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link MessageRepository}.
 *
 * Persists and retrieves application {@link Message} entities from the `messages` table.
 * Preserves strict chronological ordering, deterministic secondary sorting on messageId,
 * and handles composite conversation ownership and duplicate message IDs safely.
 */
public class JdbcMessageRepository implements MessageRepository {

    private final DatabaseConnection databaseConnection;

    public JdbcMessageRepository(DatabaseConnection databaseConnection) {
        if (databaseConnection == null) {
            throw new IllegalArgumentException("DatabaseConnection cannot be null.");
        }
        this.databaseConnection = databaseConnection;
    }

    public JdbcMessageRepository() {
        this(new DatabaseConnection());
    }

    @Override
    public boolean saveIfAbsent(Message message, String localUserId, long conversationId) throws SQLException {
        if (message == null || localUserId == null) {
            throw new IllegalArgumentException("Message and localUserId cannot be null.");
        }

        String insertMessageSql = "INSERT INTO messages "
                + "(message_id, type, sender_id, receiver_id, timestamp, payload, status, local_user_id, conversation_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String updateConversationSql = "UPDATE conversations "
                + "SET last_message_at = ? "
                + "WHERE conversation_id = ? AND local_user_id = ?";

        try (Connection conn = databaseConnection.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement psMsg = conn.prepareStatement(insertMessageSql);
                 PreparedStatement psConv = conn.prepareStatement(updateConversationSql)) {

                psMsg.setString(1, message.getMessageId());
                psMsg.setString(2, message.getType().name());
                psMsg.setString(3, message.getSenderId());
                psMsg.setString(4, message.getReceiverId());
                psMsg.setLong(5, message.getTimestamp());
                psMsg.setString(6, message.getPayload());
                psMsg.setString(7, message.getStatus() != null ? message.getStatus().name() : MessageStatus.CREATED.name());
                psMsg.setString(8, localUserId.trim());
                psMsg.setLong(9, conversationId);

                psMsg.executeUpdate();

                // Update the conversation's last_message_at timestamp
                psConv.setTimestamp(1, new Timestamp(message.getTimestamp()));
                psConv.setLong(2, conversationId);
                psConv.setString(3, localUserId.trim());
                psConv.executeUpdate();

                conn.commit();
                return true;

            } catch (SQLIntegrityConstraintViolationException e) {
                conn.rollback();
                // Check if this was a duplicate message_id (Error code 1062)
                if (e.getErrorCode() == 1062 && e.getMessage().contains("PRIMARY")) {
                    return false; // Idempotent: already exists
                }
                // Any other integrity violation (e.g. FK failure on conv/user ownership) must be thrown
                throw e;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        }
    }

    @Override
    public boolean existsById(String messageId) throws SQLException {
        if (messageId == null || messageId.trim().isEmpty()) {
            return false;
        }

        String sql = "SELECT 1 FROM messages WHERE message_id = ? LIMIT 1";

        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, messageId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public Optional<Message> findById(String messageId, String localUserId) throws SQLException {
        if (messageId == null || localUserId == null) {
            return Optional.empty();
        }

        String sql = "SELECT message_id, type, sender_id, receiver_id, timestamp, payload, status "
                + "FROM messages "
                + "WHERE message_id = ? AND local_user_id = ? "
                + "LIMIT 1";

        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, messageId.trim());
            ps.setString(2, localUserId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToMessage(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean updateStatus(String messageId, String localUserId, MessageStatus newStatus) throws SQLException {
        if (messageId == null || localUserId == null || newStatus == null) {
            return false;
        }

        String sql = "UPDATE messages SET status = ? WHERE message_id = ? AND local_user_id = ?";

        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newStatus.name());
            ps.setString(2, messageId.trim());
            ps.setString(3, localUserId.trim());

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<Message> getConversationHistory(long conversationId, String localUserId) throws SQLException {
        if (localUserId == null) {
            return new ArrayList<>();
        }

        String sql = "SELECT message_id, type, sender_id, receiver_id, timestamp, payload, status "
                + "FROM messages "
                + "WHERE conversation_id = ? AND local_user_id = ? "
                + "ORDER BY timestamp ASC, message_id ASC";

        List<Message> messages = new ArrayList<>();
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, conversationId);
            ps.setString(2, localUserId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapRowToMessage(rs));
                }
            }
        }
        return messages;
    }

    @Override
    public List<Message> getLastNMessages(long conversationId, String localUserId, int n) throws SQLException {
        if (localUserId == null || n <= 0) {
            return new ArrayList<>();
        }

        String sql = "SELECT * FROM ("
                + "  SELECT message_id, type, sender_id, receiver_id, timestamp, payload, status "
                + "  FROM messages "
                + "  WHERE conversation_id = ? AND local_user_id = ? "
                + "  ORDER BY timestamp DESC, message_id DESC "
                + "  LIMIT ?"
                + ") AS newest "
                + "ORDER BY timestamp ASC, message_id ASC";

        List<Message> messages = new ArrayList<>();
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, conversationId);
            ps.setString(2, localUserId.trim());
            ps.setInt(3, n);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapRowToMessage(rs));
                }
            }
        }
        return messages;
    }

    @Override
    public List<Message> getPaginatedHistory(long conversationId, String localUserId, long beforeTimestamp, int limit) throws SQLException {
        if (localUserId == null || limit <= 0) {
            return new ArrayList<>();
        }

        String sql = "SELECT message_id, type, sender_id, receiver_id, timestamp, payload, status "
                + "FROM messages "
                + "WHERE conversation_id = ? AND local_user_id = ? AND timestamp < ? "
                + "ORDER BY timestamp DESC, message_id DESC "
                + "LIMIT ?";

        List<Message> messages = new ArrayList<>();
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, conversationId);
            ps.setString(2, localUserId.trim());
            ps.setLong(3, beforeTimestamp);
            ps.setInt(4, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapRowToMessage(rs));
                }
            }
        }
        return messages;
    }

    @Override
    public List<Message> getFailedMessages(String localUserId) throws SQLException {
        if (localUserId == null || localUserId.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String sql = "SELECT message_id, type, sender_id, receiver_id, timestamp, payload, status "
                + "FROM messages "
                + "WHERE local_user_id = ? AND status = 'FAILED' "
                + "ORDER BY timestamp ASC, message_id ASC";

        List<Message> messages = new ArrayList<>();
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, localUserId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapRowToMessage(rs));
                }
            }
        }
        return messages;
    }

    @Override
    public List<Message> getRecentMessages(String localUserId, int limit) throws SQLException {
        if (localUserId == null || localUserId.trim().isEmpty()) {
            return new ArrayList<>();
        }

        int maxRows = (limit > 0) ? limit : 50;
        String sql = "SELECT message_id, type, sender_id, receiver_id, timestamp, payload, status "
                + "FROM messages "
                + "WHERE local_user_id = ? "
                + "ORDER BY timestamp DESC, message_id DESC "
                + "LIMIT ?";

        List<Message> messages = new ArrayList<>();
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, localUserId.trim());
            ps.setInt(2, maxRows);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapRowToMessage(rs));
                }
            }
        }
        return messages;
    }

    @Override
    public List<Message> getCreatedMessages(String localUserId) throws SQLException {
        if (localUserId == null || localUserId.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String sql = "SELECT message_id, type, sender_id, receiver_id, timestamp, payload, status "
                + "FROM messages "
                + "WHERE local_user_id = ? AND status = 'CREATED' "
                + "ORDER BY timestamp ASC, message_id ASC";

        List<Message> messages = new ArrayList<>();
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, localUserId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapRowToMessage(rs));
                }
            }
        }
        return messages;
    }

    private Message mapRowToMessage(ResultSet rs) throws SQLException {
        String messageId = rs.getString("message_id");
        MessageType type = MessageType.valueOf(rs.getString("type"));
        String senderId = rs.getString("sender_id");
        String receiverId = rs.getString("receiver_id");
        long timestamp = rs.getLong("timestamp");
        String payload = rs.getString("payload");
        MessageStatus status = MessageStatus.valueOf(rs.getString("status"));

        return new Message(messageId, type, senderId, receiverId, timestamp, payload, status);
    }
}

