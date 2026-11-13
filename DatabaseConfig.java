import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration manager for MeshLink MySQL database connectivity.
 *
 * Configuration lookup precedence:
 * <ol>
 *   <li>Environment variable overrides (e.g. {@code MESHLINK_DB_URL}, {@code MESHLINK_DB_PASSWORD})</li>
 *   <li>Configuration file at {@code ~/.meshlink/db.properties}</li>
 *   <li>Default parameters (localhost:3306/meshlink, user 'meshlink_app')</li>
 * </ol>
 *
 * Database credentials must NEVER be hardcoded in application source code.
 */
public final class DatabaseConfig {

    public static final String DEFAULT_HOST             = "localhost";
    public static final int    DEFAULT_PORT             = 3306;
    public static final String DEFAULT_DATABASE         = "meshlink";
    public static final String DEFAULT_USER             = "meshlink_app";
    public static final int    DEFAULT_CONNECT_TIMEOUT  = 5000;

    private static final String CONFIG_DIR_NAME  = ".meshlink";
    private static final String CONFIG_FILE_NAME = "db.properties";

    private final String url;
    private final String user;
    private final String password;
    private final String host;
    private final int    port;
    private final String database;
    private final int    connectTimeoutMs;

    public DatabaseConfig(String url, String user, String password, String host, int port,
                          String database, int connectTimeoutMs) {
        this.host             = (host != null && !host.trim().isEmpty()) ? host.trim() : DEFAULT_HOST;
        this.port             = (port > 0) ? port : DEFAULT_PORT;
        this.database         = (database != null && !database.trim().isEmpty()) ? database.trim() : DEFAULT_DATABASE;
        this.user             = (user != null && !user.trim().isEmpty()) ? user.trim() : DEFAULT_USER;
        this.password         = (password != null) ? password : "";
        this.connectTimeoutMs = (connectTimeoutMs > 0) ? connectTimeoutMs : DEFAULT_CONNECT_TIMEOUT;

        if (url != null && !url.trim().isEmpty()) {
            this.url = url.trim();
        } else {
            this.url = String.format(
                    "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=%d",
                    this.host, this.port, this.database, this.connectTimeoutMs
            );
        }
    }

    /**
     * Loads database configuration by inspecting {@code ~/.meshlink/db.properties}
     * and overlaying environment variables.
     */
    public static DatabaseConfig load() {
        Properties fileProps = new Properties();
        File configFile = getDefaultConfigFile();

        if (configFile.exists() && configFile.isFile()) {
            try (InputStream in = new FileInputStream(configFile)) {
                fileProps.load(in);
            } catch (IOException e) {
                System.err.println("[DatabaseConfig] Warning: Failed to read "
                        + configFile.getAbsolutePath() + ": " + e.getMessage());
            }
        }

        String envUrl      = getEnvOrNull("MESHLINK_DB_URL");
        String envHost     = getEnvOrNull("MESHLINK_DB_HOST");
        String envPortStr  = getEnvOrNull("MESHLINK_DB_PORT");
        String envDb       = getEnvOrNull("MESHLINK_DB_NAME");
        String envUser     = getEnvOrNull("MESHLINK_DB_USER");
        String envPassword = getEnvOrNull("MESHLINK_DB_PASSWORD");
        String envTimeout  = getEnvOrNull("MESHLINK_DB_TIMEOUT_MS");

        String host = (envHost != null) ? envHost : fileProps.getProperty("db.host", DEFAULT_HOST);
        int port = DEFAULT_PORT;
        String portVal = (envPortStr != null) ? envPortStr : fileProps.getProperty("db.port");
        if (portVal != null) {
            try {
                port = Integer.parseInt(portVal.trim());
            } catch (NumberFormatException ignored) {}
        }

        String db = (envDb != null) ? envDb : fileProps.getProperty("db.name", DEFAULT_DATABASE);
        String user = (envUser != null) ? envUser : fileProps.getProperty("db.user", DEFAULT_USER);
        String password = (envPassword != null) ? envPassword : fileProps.getProperty("db.password", "changeme");

        int timeout = DEFAULT_CONNECT_TIMEOUT;
        String timeoutVal = (envTimeout != null) ? envTimeout : fileProps.getProperty("db.connectTimeoutMs");
        if (timeoutVal != null) {
            try {
                timeout = Integer.parseInt(timeoutVal.trim());
            } catch (NumberFormatException ignored) {}
        }

        String url = (envUrl != null) ? envUrl : fileProps.getProperty("db.url");

        return new DatabaseConfig(url, user, password, host, port, db, timeout);
    }

    /**
     * Factory for constructing custom test configurations.
     */
    public static DatabaseConfig forTest(String url, String user, String password) {
        return new DatabaseConfig(url, user, password, null, 0, null, 3000);
    }

    public static File getDefaultConfigFile() {
        String userHome = System.getProperty("user.home", ".");
        return new File(userHome + File.separator + CONFIG_DIR_NAME, CONFIG_FILE_NAME);
    }

    private static String getEnvOrNull(String name) {
        String val = System.getenv(name);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : null;
    }

    public String getUrl()              { return url; }
    public String getUser()             { return user; }
    public String getPassword()         { return password; }
    public String getHost()             { return host; }
    public int    getPort()             { return port; }
    public String getDatabase()         { return database; }
    public int    getConnectTimeoutMs() { return connectTimeoutMs; }

    @Override
    public String toString() {
        return "DatabaseConfig{"
                + "url='" + url + '\''
                + ", user='" + user + '\''
                + ", host='" + host + '\''
                + ", port=" + port
                + ", database='" + database + '\''
                + ", connectTimeoutMs=" + connectTimeoutMs
                + '}';
    }
}

