-- =============================================================================
-- MeshLink – queries/contacts.sql
-- =============================================================================
-- Purpose  : CRUD operations for the contacts table.
--            SQL equivalent of PeerRegistry operations.
--
-- Parameters:
--   :local_user_id  – the user_id of the local account
--   :peer_id        – the peer's identifier (as seen on the wire)
--   :display_name   – optional human-readable label
-- =============================================================================


-- ---------------------------------------------------------------------------
-- Query 1 — List all contacts for a local user
-- ---------------------------------------------------------------------------
-- Equivalent to: PeerRegistry.getAllPeers()
-- ---------------------------------------------------------------------------
SELECT
    peer_id,
    display_name,
    first_added_at,
    last_seen_at
FROM contacts
WHERE local_user_id = :local_user_id
ORDER BY last_seen_at DESC
;


-- ---------------------------------------------------------------------------
-- Query 2 — Look up a single contact by peer ID
-- ---------------------------------------------------------------------------
-- Equivalent to: PeerRegistry.getPeer(peerId)
-- ---------------------------------------------------------------------------
SELECT
    peer_id,
    display_name,
    first_added_at,
    last_seen_at
FROM contacts
WHERE local_user_id = :local_user_id
  AND peer_id       = :peer_id
LIMIT 1
;


-- ---------------------------------------------------------------------------
-- Query 3 — Add a new contact (or ignore if already known)
-- ---------------------------------------------------------------------------
-- Equivalent to: PeerRegistry.register() — idempotent.
-- INSERT IGNORE silently skips the insert when the (local_user_id, peer_id)
-- UNIQUE constraint would be violated.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO contacts
    (local_user_id, peer_id, display_name)
VALUES
    (:local_user_id, :peer_id, :display_name)
;


-- ---------------------------------------------------------------------------
-- Query 4 — Upsert a contact (add or update last_seen_at)
-- ---------------------------------------------------------------------------
-- Called whenever a peer sends or receives a message — updates last_seen_at
-- in the same way PeerRegistry.PeerEntry.setStatus(ONLINE) updates the
-- lastSeenTimestamp field.
-- ---------------------------------------------------------------------------
INSERT INTO contacts
    (local_user_id, peer_id, display_name)
VALUES
    (:local_user_id, :peer_id, :display_name)
ON DUPLICATE KEY UPDATE
    last_seen_at = NOW(),
    display_name = COALESCE(:display_name, display_name)
                       -- preserve existing name if new value is NULL
;


-- ---------------------------------------------------------------------------
-- Query 5 — Rename a contact (set display_name)
-- ---------------------------------------------------------------------------
UPDATE contacts
SET    display_name = :display_name
WHERE  local_user_id = :local_user_id
  AND  peer_id       = :peer_id
;


-- ---------------------------------------------------------------------------
-- Query 6 — Remove a contact
-- ---------------------------------------------------------------------------
-- Note: does NOT delete conversation or message history (different tables).
-- ---------------------------------------------------------------------------
DELETE FROM contacts
WHERE  local_user_id = :local_user_id
  AND  peer_id       = :peer_id
;


-- ---------------------------------------------------------------------------
-- Query 7 — Insert a new message row
-- ---------------------------------------------------------------------------
-- This is the central write path: called after MessageService.receive() stores
-- a message in the in-memory MessageStore and before returning to the caller.
-- conversation_id must be resolved first via the conversations upsert query.
-- ---------------------------------------------------------------------------
INSERT INTO messages
    (message_id, type, sender_id, receiver_id, timestamp,
     payload, status, local_user_id, conversation_id)
VALUES
    (:message_id, :type, :sender_id, :receiver_id, :timestamp,
     :payload, :status, :local_user_id, :conversation_id)
;

