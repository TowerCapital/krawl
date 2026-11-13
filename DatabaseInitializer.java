import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates the presence of required MySQL database tables on startup.
 *
 * IMPORTANT ARCHITECTURAL RULE:
 * This class NEVER automatically creates databases, executes DDL, or modifies
 * existing tables. Schema management in MeshLink is explicitly controlled via
 * {@code db/create_database.sql} and {@code db/schema.sql}.
 *
 * If any expected tables are missing, this class raises a descriptive error
 * guiding the developer or user to run the verified SQL scripts.
 */
public final class DatabaseInitializer {

    private static final List<String> REQUIRED_TABLES = Arrays.asList(
            "local_users",
            "contacts",
            "conversations",
            "messages"
    );

    private final DatabaseConnection databaseConnection;

    public DatabaseInitializer(DatabaseConnection databaseConnection) {
        if (databaseConnection == null) {
            throw new IllegalArgumentException("DatabaseConnection cannot be null.");
        }
        this.databaseConnection = databaseConnection;
    }

    public DatabaseInitializer() {
        this(new DatabaseConnection());
    }

    /**
     * Checks database connectivity and verifies that all four required tables exist.
     *
     * @return true if all tables are verified
     * @throws SQLException if connectivity fails or if any required table is missing
     */
    public boolean validateSchema() throws SQLException {
        try (Connection conn = databaseConnection.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();

            Set<String> existingTables = new HashSet<>();
            try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName != null) {
                        existingTables.add(tableName.toLowerCase());
                    }
                }
            }

            List<String> missing = new ArrayList<>();
            for (String required : REQUIRED_TABLES) {
                if (!existingTables.contains(required.toLowerCase())) {
                    missing.add(required);
                }
            }

            if (!missing.isEmpty()) {
                throw new SQLException(String.format(
                        "[DatabaseInitializer] Schema validation failed. Missing table(s): %s\n"
                        + "Remedy: Please run the verified schema script:\n"
                        + "  mysql -u %s -p %s < db/schema.sql\n"
                        + "Existing tables found in database '%s': %s",
                        missing,
                        databaseConnection.getConfig().getUser(),
                        databaseConnection.getConfig().getDatabase(),
                        catalog,
                        existingTables
                ));
            }

            return true;
        }
    }

    /**
     * Returns the list of four tables required by the MeshLink architecture.
     */
    public static List<String> getRequiredTables() {
        return new ArrayList<>(REQUIRED_TABLES);
    }
}

