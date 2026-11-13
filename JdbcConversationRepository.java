import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link ConversationRepository}.
 */
public class JdbcConversationRepository implements ConversationRepository {

    private final DatabaseConnection databaseConnection;

    public JdbcConversationRepository(DatabaseConnection databaseConnection) {
        if (databaseConnection == null) {
            throw new IllegalArgumentException("DatabaseConnection cannot be null.");
        }
        this.databaseConnection = databaseConnection;
    }

    public JdbcConversationRepository() {
        this(new DatabaseConnection());
    }

    @Override
    public Optional<Conversation> findByUserAndPeer(String localUserId, String peerId) throws SQLException {
        if (localUserId == null || peerId == null) {
            return Optional.empty();
        }

        String sql = "SELECT conversation_id, local_user_id, peer_id, created_at, last_message_at "
                + "FROM conversations WHERE local_user_id = ? AND peer_id = ?";

        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, localUserId.trim());
            ps.setString(2, peerId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToConversation(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Conversation createIfAbsent(String localUserId, String peerId) throws SQLException {
        if (localUserId == null || peerId == null) {
            throw new IllegalArgumentException("localUserId and peerId cannot be null.");
        }

        String normalizedUser = localUserId.trim();
        String normalizedPeer = peerId.trim();

        // Check if conversation already exists first (fast path)
        Optional<Conversation> existing = findByUserAndPeer(normalizedUser, normalizedPeer);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Insert new conversation row
        String insertSql = "INSERT INTO conversations (local_user_id, peer_id, created_at, last_message_at) "
                + "VALUES (?, ?, CURRENT_TIMESTAMP, NULL) "
                + "ON DUPLICATE KEY UPDATE conversation_id = conversation_id";

        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {

            ps.setString(1, normalizedUser);
            ps.setString(2, normalizedPeer);
            ps.executeUpdate();
        }

        // Fetch the generated or existing conversation record
        return findByUserAndPeer(normalizedUser, normalizedPeer)
                .orElseThrow(() -> new SQLException("Failed to retrieve or create conversation for user '"
                        + normalizedUser + "' and peer '" + normalizedPeer + "'."));
    }

    @Override
    public boolean updateLastMessageTimestamp(long conversationId, Timestamp lastMessageAt) throws SQLException {
        String sql = "UPDATE conversations SET last_message_at = ? WHERE conversation_id = ?";

        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, lastMessageAt != null ? lastMessageAt : new Timestamp(System.currentTimeMillis()));
            ps.setLong(2, conversationId);

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<Conversation> getRecentConversations(String localUserId, int limit) throws SQLException {
        if (localUserId == null || localUserId.trim().isEmpty()) {
            return new ArrayList<>();
        }

        int maxRows = (limit > 0) ? limit : 50;
        String sql = "SELECT conversation_id, local_user_id, peer_id, created_at, last_message_at "
                + "FROM conversations "
                + "WHERE local_user_id = ? "
                + "ORDER BY (last_message_at IS NULL), last_message_at DESC, created_at DESC, conversation_id ASC "
                + "LIMIT ?";

        List<Conversation> conversations = new ArrayList<>();
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, localUserId.trim());
            ps.setInt(2, maxRows);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    conversations.add(mapRowToConversation(rs));
                }
            }
        }
        return conversations;
    }

    private Conversation mapRowToConversation(ResultSet rs) throws SQLException {
        return new Conversation(
                rs.getLong("conversation_id"),
                rs.getString("local_user_id"),
                rs.getString("peer_id"),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("last_message_at")
        );
    }
}

