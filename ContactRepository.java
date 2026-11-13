import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Data access operations for the saved peer contact list (`contacts` table).
 *
 * Keeps database persistence operations separate from business logic.
 */
public interface ContactRepository {

    /**
     * Adds a new peer contact or updates their display name if already present.
     *
     * @param localUserId local user who owns this contact entry
     * @param peerId remote peer's advertised identifier
     * @param displayName optional human-readable display label
     * @return true if added or updated, false otherwise
     * @throws SQLException on database errors
     */
    boolean addContact(String localUserId, String peerId, String displayName) throws SQLException;

    /**
     * Removes a peer from the local user's contact list.
     * Note: Does NOT delete conversation or message history.
     *
     * @param localUserId local user identifier
     * @param peerId peer to remove
     * @return true if a contact was deleted, false if not found
     * @throws SQLException on database errors
     */
    boolean removeContact(String localUserId, String peerId) throws SQLException;

    /**
     * Retrieves a single contact entry by local user and peer ID.
     *
     * @param localUserId local user identifier
     * @param peerId remote peer identifier
     * @return Optional containing the Contact if found, empty otherwise
     * @throws SQLException on database errors
     */
    Optional<Contact> findContact(String localUserId, String peerId) throws SQLException;

    /**
     * Lists all saved contacts for a given local user, ordered by last seen time descending.
     *
     * @param localUserId local user identifier
     * @return list of saved contacts
     * @throws SQLException on database errors
     */
    List<Contact> listContacts(String localUserId) throws SQLException;

    /**
     * Updates the last-seen timestamp for an existing contact.
     *
     * @param localUserId local user identifier
     * @param peerId peer identifier
     * @param lastSeenAt new timestamp
     * @return true if updated, false if contact not found
     * @throws SQLException on database errors
     */
    boolean updateLastSeen(String localUserId, String peerId, Timestamp lastSeenAt) throws SQLException;
}

