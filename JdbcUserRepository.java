import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * JDBC implementation of {@link UserRepository}.
 */
public class JdbcUserRepository implements UserRepository {

    private final DatabaseConnection databaseConnection;

    public JdbcUserRepository(DatabaseConnection databaseConnection) {
        if (databaseConnection == null) {
            throw new IllegalArgumentException("DatabaseConnection cannot be null.");
        }
        this.databaseConnection = databaseConnection;
    }

    public JdbcUserRepository() {
        this(new DatabaseConnection());
    }

    @Override
    public boolean register(User user) throws SQLException {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null.");
        }

        String sql = "INSERT INTO local_users "
                + "(user_id, username, password_hash, password_salt, password_iterations, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getUserId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getPasswordSalt());
            ps.setInt(5, user.getPasswordIterations());
            ps.setTimestamp(6, user.getCreatedAt() != null ? user.getCreatedAt() : new Timestamp(System.currentTimeMillis()));

            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLIntegrityConstraintViolationException e) {
            // Duplicate user_id or duplicate username
            return false;
        }
    }

    @Override
    public Optional<User> findById(String userId) throws SQLException {
        if (userId == null || userId.trim().isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT user_id, username, password_hash, password_salt, password_iterations, created_at "
                + "FROM local_users WHERE user_id = ?";

        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findByUsername(String username) throws SQLException {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }

        String sql = "SELECT user_id, username, password_hash, password_salt, password_iterations, created_at "
                + "FROM local_users WHERE username = ?";

        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean updatePassword(String userId, String passwordHash, String passwordSalt, int iterations) throws SQLException {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }

        String sql = "UPDATE local_users SET password_hash = ?, password_salt = ?, password_iterations = ? "
                + "WHERE user_id = ?";

        try (Connection conn = databaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, passwordHash);
            ps.setString(2, passwordSalt);
            ps.setInt(3, iterations);
            ps.setString(4, userId.trim());

            return ps.executeUpdate() > 0;
        }
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getString("user_id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("password_salt"),
                rs.getInt("password_iterations"),
                rs.getTimestamp("created_at")
        );
    }
}

