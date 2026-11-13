import java.sql.SQLException;
import java.util.Optional;

/**
 * Data access operations for local user accounts (`local_users` table).
 *
 * Keeps database persistence operations separate from business logic.
 */
public interface UserRepository {

    /**
     * Registers a new local user account.
     *
     * @param user user entity to persist
     * @return true if inserted, false if user_id or username already exists
     * @throws SQLException on database errors
     */
    boolean register(User user) throws SQLException;

    /**
     * Finds a local user by their unique user_id string.
     *
     * @param userId application user identifier
     * @return Optional containing the User if found, empty otherwise
     * @throws SQLException on database errors
     */
    Optional<User> findById(String userId) throws SQLException;

    /**
     * Finds a local user by their human-readable username.
     *
     * @param username login username
     * @return Optional containing the User if found, empty otherwise
     * @throws SQLException on database errors
     */
    Optional<User> findByUsername(String username) throws SQLException;

    /**
     * Updates authentication credentials for an existing local user.
     *
     * @param userId user identifier
     * @param passwordHash new hashed password string
     * @param passwordSalt new password salt string
     * @param iterations PBKDF2 iterations
     * @return true if updated, false if user was not found
     * @throws SQLException on database errors
     */
    boolean updatePassword(String userId, String passwordHash, String passwordSalt, int iterations) throws SQLException;
}

