import java.sql.Timestamp;

/**
 * Model representing a record from the `contacts` table.
 *
 * Persists the local user's saved peer contacts across application restarts,
 * mirroring in-memory {@link PeerRegistry.PeerEntry}.
 */
public final class Contact {

    private final long      id;
    private final String    localUserId;
    private final String    peerId;
    private final String    displayName;
    private final Timestamp firstAddedAt;
    private final Timestamp lastSeenAt;

    public Contact(long id, String localUserId, String peerId, String displayName,
                   Timestamp firstAddedAt, Timestamp lastSeenAt) {
        this.id           = id;
        this.localUserId  = localUserId;
        this.peerId       = peerId;
        this.displayName  = displayName;
        this.firstAddedAt = firstAddedAt;
        this.lastSeenAt   = lastSeenAt;
    }

    public Contact(String localUserId, String peerId, String displayName) {
        this(0L, localUserId, peerId, displayName,
             new Timestamp(System.currentTimeMillis()),
             new Timestamp(System.currentTimeMillis()));
    }

    public long      getId()           { return id; }
    public String    getLocalUserId()  { return localUserId; }
    public String    getPeerId()       { return peerId; }
    public String    getDisplayName()  { return displayName; }
    public Timestamp getFirstAddedAt() { return firstAddedAt; }
    public Timestamp getLastSeenAt()   { return lastSeenAt; }

    @Override
    public String toString() {
        return "Contact{"
                + "id=" + id
                + ", localUserId='" + localUserId + '\''
                + ", peerId='" + peerId + '\''
                + ", displayName='" + displayName + '\''
                + ", lastSeenAt=" + lastSeenAt
                + '}';
    }
}

