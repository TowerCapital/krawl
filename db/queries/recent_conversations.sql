-- =============================================================================
-- MeshLink – queries/recent_conversations.sql              (Revision 2)
-- =============================================================================
-- Purpose  : Retrieve the user's conversation list sorted by most-recent
--            activity — the "chat inbox" view.
--
-- Revision 2 changes:
--   • Removed the former "unread count" query (Query 2 in Revision 1).
--     RECEIVED in MessageStatus means "decoded and stored by the receiving
--     application" — it is a delivery status, NOT a read/seen indicator.
--     Treating RECEIVED as "unread" was semantically incorrect and has been
--     removed. A read-status column is out of scope for Phase 1.
--   • Replaced it with a "FAILED messages per conversation" query, which IS
--     meaningful: it lets the application surface a retry indicator to the
--     user for messages that failed to send.
--
-- Parameters:
--   :local_user_id  – the user_id of the local account
--   :limit_n        – max number of conversations to return (e.g. 20)
-- =============================================================================


-- ---------------------------------------------------------------------------
-- Query 1 — All conversations ordered by last message time
-- ---------------------------------------------------------------------------
-- Returns one row per peer the local user has chatted with, plus the most
-- recent message snippet. Equivalent to iterating ConversationManager's
-- getAllThreads() map and calling getLatest() on each ConversationThread.
--
-- The correlated subquery (WHERE m2.conversation_id = c.conversation_id)
-- is compatible with MySQL 5.7+ and 8.0+.
-- ---------------------------------------------------------------------------
SELECT
    c.conversation_id,
    c.peer_id,
    c.created_at           AS conversation_started,
    c.last_message_at,

    -- Inline preview of the most recent message in this thread.
    latest.message_id      AS last_message_id,
    latest.type            AS last_message_type,
    latest.sender_id       AS last_sender_id,
    latest.payload         AS last_payload,
    latest.status          AS last_status

FROM conversations AS c

INNER JOIN messages AS latest
    ON  latest.message_id = (
            SELECT m2.message_id
            FROM   messages AS m2
            WHERE  m2.conversation_id = c.conversation_id
            ORDER  BY m2.timestamp DESC
            LIMIT  1
        )

WHERE c.local_user_id = :local_user_id

ORDER BY c.last_message_at DESC   -- most-recent conversation at the top
LIMIT :limit_n
;


-- ---------------------------------------------------------------------------
-- Query 2 — FAILED messages per conversation (send-failure indicator)
-- ---------------------------------------------------------------------------
-- A delivery status of FAILED means PeerConnectionManager.sendText() threw
-- an IOException. This query surfaces which conversations have unsent
-- messages so the application can show a retry prompt.
--
-- This query is semantically correct: FAILED is a delivery state, not a
-- read state, and we are querying it as such.
-- ---------------------------------------------------------------------------
SELECT
    c.conversation_id,
    c.peer_id,
    COUNT(m.message_id)    AS failed_count
FROM conversations  AS c
INNER JOIN messages AS m
    ON  m.conversation_id = c.conversation_id
    AND m.local_user_id   = c.local_user_id    -- respect composite FK ownership
    AND m.status          = 'FAILED'
WHERE c.local_user_id = :local_user_id
GROUP BY
    c.conversation_id,
    c.peer_id
HAVING
    failed_count > 0
ORDER BY
    failed_count DESC
;


-- ---------------------------------------------------------------------------
-- Query 3 — Upsert a conversation row
-- ---------------------------------------------------------------------------
-- Called when the first message to/from a new peer is processed.
-- Mirrors ConversationManager.record() → computeIfAbsent().
-- If the (local_user_id, peer_id) pair already exists, only last_message_at
-- is updated; conversation_id and created_at are left untouched.
-- ---------------------------------------------------------------------------
INSERT INTO conversations (local_user_id, peer_id, last_message_at)
VALUES (:local_user_id, :peer_id, NOW())
ON DUPLICATE KEY UPDATE
    last_message_at = NOW()
;
