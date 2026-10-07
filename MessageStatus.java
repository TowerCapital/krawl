/**
 * Lifecycle states for a MeshLink application message.
 *
 * Lifecycle (happy path):
 *
 *   CREATED → SENT → RECEIVED
 *
 * On transmission failure:
 *
 *   CREATED → FAILED
 *
 * Status is tracked entirely in memory for this prototype.
 * No ACK packets are exchanged at the networking layer;
 * "RECEIVED" means the receiving application successfully decoded
 * and stored the message locally.
 *
 * NOTE: SQL persistence will replace in-memory tracking in a future
 *       iteration. The status values here map directly to database
 *       column values (enum name → VARCHAR).
 */
public enum MessageStatus {
    /** Message object created; not yet sent over the network. */
    CREATED,

    /** Message was handed to PeerConnectionManager.sendText() without error. */
    SENT,

    /** Message was decoded successfully by the receiving application. */
    RECEIVED,

    /** Transmission failed (e.g. peer not connected, IOException). */
    FAILED
}
