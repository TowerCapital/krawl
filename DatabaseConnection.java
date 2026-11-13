import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Manages JDBC connections to the local MeshLink MySQL database.
 *
 * Provides connection creation, validation, and actionable diagnostic errors.
 * Designed to be used with standard Java try-with-resources blocks:
 *
 * <pre>{@code
 * DatabaseConnection db = new DatabaseConnection();
 * try (Connection conn = db.getConnection()) {
 *     // perform database operations
 * }
 * }</pre>
 */
public class DatabaseConnection {

    static {
        try {
            // Explicitly load MySQL Connector/J driver class
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DatabaseConnection] FATAL: MySQL Connector/J driver class "
                    + "'com.mysql.cj.jdbc.Driver' not found on classpath.");
        }
    }

    private final DatabaseConfig config;

    public DatabaseConnection(DatabaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("DatabaseConfig cannot be null.");
        }
        this.config = config;
    }

    public DatabaseConnection() {
        this(DatabaseConfig.load());
    }

    /**
     * Opens a new validated connection to MySQL using the active configuration.
     * Callers must close the returned connection, typically via try-with-resources.
     *
     * @return an active, validated {@link Connection}
     * @throws SQLException with descriptive, actionable error message if connection fails
     */
    public Connection getConnection() throws SQLException {
        try {
            Connection conn = DriverManager.getConnection(
                    config.getUrl(),
                    config.getUser(),
                    config.getPassword()
            );

            // Validate connection readiness (3-second timeout)
            if (!conn.isValid(3)) {
                conn.close();
                throw new SQLException("Connection validation failed (connection is not alive).");
            }

            return conn;
        } catch (SQLException e) {
            throw enrichException(e);
        }
    }

    /**
     * Checks if the database is reachable and validating connections.
     *
     * @param timeoutSeconds timeout for ping
     * @return true if database responds, false otherwise
     */
    public boolean isReachable(int timeoutSeconds) {
        try (Connection conn = DriverManager.getConnection(
                config.getUrl(),
                config.getUser(),
                config.getPassword())) {
            return conn.isValid(Math.max(1, timeoutSeconds));
        } catch (Exception e) {
            return false;
        }
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    /**
     * Translates vendor-specific MySQL error codes into clear, actionable advice.
     */
    private SQLException enrichException(SQLException e) {
        int errorCode = e.getErrorCode();
        String sqlState = e.getSQLState();
        String originalMsg = e.getMessage();

        StringBuilder advice = new StringBuilder("[DatabaseConnection] Failed to connect to MySQL:\n");
        advice.append("  URL      : ").append(config.getUrl()).append("\n");
        advice.append("  User     : ").append(config.getUser()).append("\n");
        advice.append("  SQLState : ").append(sqlState).append(", ErrorCode: ").append(errorCode).append("\n");
        advice.append("  Cause    : ").append(originalMsg).append("\n");
        advice.append("  Remedy   : ");

        if (errorCode == 1045) {
            advice.append("Access denied. Please check user '")
                  .append(config.getUser())
                  .append("' and password in ~/.meshlink/db.properties or MESHLINK_DB_PASSWORD env variable.");
        } else if (errorCode == 1049) {
            advice.append("Database '")
                  .append(config.getDatabase())
                  .append("' does not exist. Run 'db/create_database.sql' to initialize.");
        } else if ("08S01".equals(sqlState) || "08001".equals(sqlState) || errorCode == 0) {
            advice.append("Communications failure. Ensure MySQL Server is running locally on port ")
                  .append(config.getPort())
                  .append(" ('sc query MySQL80' or MySQL Windows Service).");
        } else {
            advice.append("Verify MySQL Server configuration, network socket, and user grants.");
        }

        return new SQLException(advice.toString(), sqlState, errorCode, e);
    }
}

