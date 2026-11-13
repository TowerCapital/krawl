USE meshlink;

-- Insert a sequence of messages between Alice and Bob
INSERT INTO messages
    (message_id, type, sender_id, receiver_id, timestamp, payload, status, local_user_id, conversation_id)
VALUES
    ('msg-uuid-002', 'CHAT', 'bob',   'alice', 1700000001000, 'Hey Alice, received!', 'RECEIVED', 'alice', 1),
    ('msg-uuid-003', 'CHAT', 'alice', 'bob',   1700000002000, 'Are we on LAN?', 'SENT', 'alice', 1),
    ('msg-uuid-004', 'CHAT', 'bob',   'alice', 1700000003000, 'Yes, direct TCP socket.', 'RECEIVED', 'alice', 1),
    ('msg-uuid-005', 'CHAT', 'alice', 'bob',   1700000004000, 'Unsent message test', 'FAILED', 'alice', 1);

-- Update conversation last_message_at
UPDATE conversations SET last_message_at = NOW() WHERE conversation_id = 1;

-- Test Query 1 from conversation_history.sql: Full conversation history (chronological, oldest first)
SELECT '--- Query 1: Full History (Oldest First) ---' AS query_test;
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
    AND c.local_user_id   = 'alice'
    AND c.peer_id         = 'bob'
ORDER BY m.timestamp ASC;

-- Test Query 2 from conversation_history.sql: Last N messages (Limit 2, displayed oldest first)
SELECT '--- Query 2: Last 2 Messages (Oldest First) ---' AS query_test;
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
        AND c.local_user_id   = 'alice'
        AND c.peer_id         = 'bob'
    ORDER BY m.timestamp DESC
    LIMIT 2
) AS newest
ORDER BY timestamp ASC;

-- Test Query 3 from recent_conversations.sql: Recent Conversations list with last message preview
SELECT '--- Query 3: Recent Conversations with Latest Message ---' AS query_test;
SELECT
    c.conversation_id,
    c.peer_id,
    c.created_at           AS conversation_started,
    c.last_message_at,
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
WHERE c.local_user_id = 'alice'
ORDER BY c.last_message_at DESC;

-- Test Query 4 from recent_conversations.sql: FAILED messages count
SELECT '--- Query 4: Failed Messages Count Per Conversation ---' AS query_test;
SELECT
    c.conversation_id,
    c.peer_id,
    COUNT(m.message_id)    AS failed_count
FROM conversations  AS c
INNER JOIN messages AS m
    ON  m.conversation_id = c.conversation_id
    AND m.local_user_id   = c.local_user_id
    AND m.status          = 'FAILED'
WHERE c.local_user_id = 'alice'
GROUP BY
    c.conversation_id,
    c.peer_id
HAVING
    failed_count > 0;

-- Test cascade delete: Delete local user 'alice' -> all contacts, conversations, and messages should cascade delete
SELECT '--- Query 5: Cascade Deletion Test ---' AS query_test;
DELETE FROM local_users WHERE user_id = 'alice';

SELECT COUNT(*) AS remaining_contacts FROM contacts WHERE local_user_id = 'alice';
SELECT COUNT(*) AS remaining_conversations FROM conversations WHERE local_user_id = 'alice';
SELECT COUNT(*) AS remaining_messages FROM messages WHERE local_user_id = 'alice';

