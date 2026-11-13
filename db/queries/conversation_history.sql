-- =============================================================================
-- MeshLink – queries/conversation_history.sql
-- =============================================================================
-- Purpose  : Retrieve the message history for a single conversation thread.
--            SQL equivalent of ConversationThread.getAll() and getLastN(n).
--
-- Usage    : Replace the :placeholders with actual values before executing,
--            or bind them via PreparedStatement in the future JDBC layer.
--
-- Parameters:
--   :local_user_id  – the user_id of the local account (e.g. 'alice')
--   :peer_id        – the peer on the other side of the conversation (e.g. 'bob')
--   :limit_n        – for the paginated variant: max rows to return
--   :before_ts      – for the paginated variant: epoch-ms cursor (load older msgs)
-- =============================================================================


-- ---------------------------------------------------------------------------
-- Query 1 — Full conversation history (chronological, oldest first)
-- ---------------------------------------------------------------------------
-- Equivalent to: ConversationThread.getAll()
-- Joins conversations to verify (local_user_id, peer_id) ownership before
-- returning rows, so there is no risk of cross-user data leakage.
-- ---------------------------------------------------------------------------
SELECT
    m.message_id,
    m.type,
    m.sender_id,
    m.receiver_id,
    m.timestamp,                -- epoch ms (matches Message.getTimestamp())
    m.payload,
    m.status,
    m.stored_at
FROM messages AS m
INNER JOIN conversations AS c
    ON  m.conversation_id = c.conversation_id
    AND c.local_user_id   = :local_user_id
    AND c.peer_id         = :peer_id
ORDER BY
    m.timestamp ASC           -- oldest first → natural chat scroll direction
;


-- ---------------------------------------------------------------------------
-- Query 2 — Last N messages (chronological, oldest first)
-- ---------------------------------------------------------------------------
-- Equivalent to: ConversationThread.getLastN(n)
-- Uses a subquery to grab the N newest rows, then re-orders them oldest-first
-- for display — the same reversal ConversationThread.getLastN() does.
-- ---------------------------------------------------------------------------
SELECT *
FROM (
    SELECT
        m.message_id,
        m.type,
        m.sender_id,
        m.receiver_id,
        m.timestamp,
        m.payload,
        m.status,
        m.stored_at
    FROM messages AS m
    INNER JOIN conversations AS c
        ON  m.conversation_id = c.conversation_id
        AND c.local_user_id   = :local_user_id
        AND c.peer_id         = :peer_id
    ORDER BY m.timestamp DESC   -- newest first to apply LIMIT efficiently
    LIMIT :limit_n
) AS newest
ORDER BY timestamp ASC          -- re-order to oldest-first for display
;


-- ---------------------------------------------------------------------------
-- Query 3 — Paginated history (cursor-based, load messages before a timestamp)
-- ---------------------------------------------------------------------------
-- For infinite scroll / "load more": caller passes the timestamp of the
-- oldest currently-visible message as :before_ts.
-- ---------------------------------------------------------------------------
SELECT
    m.message_id,
    m.type,
    m.sender_id,
    m.receiver_id,
    m.timestamp,
    m.payload,
    m.status,
    m.stored_at
FROM messages AS m
INNER JOIN conversations AS c
    ON  m.conversation_id = c.conversation_id
    AND c.local_user_id   = :local_user_id
    AND c.peer_id         = :peer_id
WHERE m.timestamp < :before_ts   -- cursor: messages older than the visible window
ORDER BY m.timestamp DESC
LIMIT :limit_n
;


-- ---------------------------------------------------------------------------
-- Query 4 — Single message lookup by ID
-- ---------------------------------------------------------------------------
-- Equivalent to: MessageStore.getById(messageId)
-- ---------------------------------------------------------------------------
SELECT
    m.message_id,
    m.type,
    m.sender_id,
    m.receiver_id,
    m.timestamp,
    m.payload,
    m.status,
    m.stored_at
FROM messages AS m
WHERE m.message_id  = :message_id
  AND m.local_user_id = :local_user_id  -- ownership guard
LIMIT 1
;


-- ---------------------------------------------------------------------------
-- Query 5 — Update message status (SENT / FAILED / RECEIVED)
-- ---------------------------------------------------------------------------
-- Equivalent to: Message.setStatus() + MessageStore persistence.
-- Called by MessageService.markSent() / markFailed() once JDBC is integrated.
-- ---------------------------------------------------------------------------
UPDATE messages
SET    status = :new_status          -- 'SENT' | 'RECEIVED' | 'FAILED'
WHERE  message_id    = :message_id
  AND  local_user_id = :local_user_id
;

