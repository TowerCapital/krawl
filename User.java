import java.sql.Timestamp;

/**
 * Model representing a record from the `local_users` table.
 *
 * The `userId` is the application-level identifier matching `Message.getSenderId()`,
 * `Message.getReceiverId()`, and `ConversationManager.localUserId`.
 */
public final class User {

    private final String    userId;
    private final String    username;
    private final String    passwordHash;
    private final String    passwordSalt;
    private final int       passwordIterations;
    private final Timestamp createdAt;

    public User(String userId, String username, String passwordHash, String passwordSalt,
                int passwordIterations, Timestamp createdAt) {
        this.userId             = userId;
        this.username           = username;
        this.passwordHash       = passwordHash;
        this.passwordSalt       = passwordSalt;
        this.passwordIterations = passwordIterations;
        this.createdAt          = createdAt;
    }

    public User(String userId, String username, String passwordHash, String passwordSalt, int passwordIterations) {
        this(userId, username, passwordHash, passwordSalt, passwordIterations, new Timestamp(System.currentTimeMillis()));
    }

    public User(String userId, String username, String passwordHash, String passwordSalt) {
        this(userId, username, passwordHash, passwordSalt, 100000, new Timestamp(System.currentTimeMillis()));
    }

    public String    getUserId()             { return userId; }
    public String    getUsername()           { return username; }
    public String    getPasswordHash()       { return passwordHash; }
    public String    getPasswordSalt()       { return passwordSalt; }
    public int       getPasswordIterations() { return passwordIterations; }
    public Timestamp getCreatedAt()          { return createdAt; }

    @Override
    public String toString() {
        return "User{"
                + "userId='" + userId + '\''
                + ", username='" + username + '\''
                + ", iterations=" + passwordIterations
                + ", createdAt=" + createdAt
                + '}';
    }
}

