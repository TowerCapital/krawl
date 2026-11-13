import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link ContactRepository}.
 */
public class JdbcContactRepository implements ContactRepository {

    private final DatabaseConnection databaseConnection;

    public JdbcContactRepository(DatabaseConnection databaseConnection) {
        if (databaseConnection == null) {
            throw new IllegalArgumentException("DatabaseConnection cannot be null.");
        }
        this.databaseConnection = databaseConnection;
    }

    public JdbcContactRepository() {
        this(new DatabaseConnection());
    }

    @Override
    public boolean addContact(String localUserId, String peerId, String displayName) throws SQLException {
        if (localUserId == null || peerId == null) {
            throw new IllegalArgumentException("localUserId and peerId cannot be null.");
        }

        String sql = "INSERT INTO contacts (local_user_id, peer_id, display_name) "
                + "VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "last_seen_at = CURRENT_TIMESTAMP, "
                + "display_name = COALESCE(?, display_name)";

        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, localUserId.trim());
            ps.setString(2, peerId.trim());
            ps.setString(3, displayName);
            ps.setString(4, displayName);

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean removeContact(String localUserId, String peerId) throws SQLException {
        if (localUserId == null || peerId == null) {
            return false;
        }

        String sql = "DELETE FROM contacts WHERE local_user_id = ? AND peer_id = ?";

        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, localUserId.trim());
            ps.setString(2, peerId.trim());

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<Contact> findContact(String localUserId, String peerId) throws SQLException {
        if (localUserId == null || peerId == null) {
            return Optional.empty();
        }

        String sql = "SELECT id, local_user_id, peer_id, display_name, first_added_at, last_seen_at "
                + "FROM contacts WHERE local_user_id = ? AND peer_id = ?";

        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, localUserId.trim());
            ps.setString(2, peerId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToContact(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Contact> listContacts(String localUserId) throws SQLException {
        if (localUserId == null || localUserId.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String sql = "SELECT id, local_user_id, peer_id, display_name, first_added_at, last_seen_at "
                + "FROM contacts WHERE local_user_id = ? ORDER BY last_seen_at DESC, peer_id ASC";

        List<Contact> contacts = new ArrayList<>();
        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, localUserId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    contacts.add(mapRowToContact(rs));
                }
            }
        }
        return contacts;
    }

    @Override
    public boolean updateLastSeen(String localUserId, String peerId, Timestamp lastSeenAt) throws SQLException {
        if (localUserId == null || peerId == null) {
            return false;
        }

        String sql = "UPDATE contacts SET last_seen_at = ? WHERE local_user_id = ? AND peer_id = ?";

        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, lastSeenAt != null ? lastSeenAt : new Timestamp(System.currentTimeMillis()));
            ps.setString(2, localUserId.trim());
            ps.setString(3, peerId.trim());

            return ps.executeUpdate() > 0;
        }
    }

    private Contact mapRowToContact(ResultSet rs) throws SQLException {
        return new Contact(
                rs.getLong("id"),
                rs.getString("local_user_id"),
                rs.getString("peer_id"),
                rs.getString("display_name"),
                rs.getTimestamp("first_added_at"),
                rs.getTimestamp("last_seen_at")
        );
    }
}

