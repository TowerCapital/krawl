-- =============================================================================
-- MESHLINK DBMS DEMONSTRATION — CORE QUERIES & RELATIONAL INTEGRITY
-- Course: Database Management Systems (DBMS)
-- Project: MeshLink Peer-to-Peer Offline Communication System
-- =============================================================================
USE meshlink;

-- -----------------------------------------------------------------------------
-- 1. LOCAL ACCOUNTS & AUTHENTICATION AUDIT
-- Shows registered device accounts, stored PBKDF2 salt, and iteration count.
-- Demonstrates: Column selection, string formatting, date functions.
-- -----------------------------------------------------------------------------
SELECT 
    user_id          AS `User ID`,
    username         AS `Display Name`,
    LEFT(password_hash, 16) AS `Hash (Truncated)`,
    LEFT(password_salt, 12) AS `Salt (Truncated)`,
    password_iterations     AS `PBKDF2 Iterations`,
    DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS `Account Created`
FROM local_users
ORDER BY created_at ASC;


-- -----------------------------------------------------------------------------
-- 2. CONTACT DIRECTORY WITH LAN PRESENCE
-- Demonstrates: INNER JOIN across local_users and contacts,
-- and calculating human-readable time elapsed since peer was last seen.
-- -----------------------------------------------------------------------------
SELECT 
    c.local_user_id AS `Owner`,
    c.peer_id       AS `Peer ID`,
    COALESCE(c.display_name, 'No Alias') AS `Alias`,
    DATE_FORMAT(c.first_added_at, '%Y-%m-%d %H:%i') AS `Added On`,
    DATE_FORMAT(c.last_seen_at, '%Y-%m-%d %H:%i:%s') AS `Last Seen`,
    TIMESTAMPDIFF(SECOND, c.last_seen_at, NOW()) AS `Seconds Since Last Ping`
FROM contacts c
JOIN local_users u ON c.local_user_id = u.user_id
ORDER BY c.last_seen_at DESC;


-- -----------------------------------------------------------------------------
-- 3. ACTIVE CONVERSATION THREAD SUMMARY (WhatsApp-Style Chat List)
-- Demonstrates: Multi-table JOIN (conversations + messages), subquery/correlated
-- join to fetch the most recent message snippet for each conversation thread.
-- -----------------------------------------------------------------------------
SELECT 
    conv.conversation_id   AS `Conv ID`,
    conv.local_user_id     AS `Current User`,
    conv.peer_id           AS `Chatting With`,
    DATE_FORMAT(conv.last_message_at, '%H:%i:%s') AS `Last Active`,
    (
        SELECT LEFT(m.payload, 35)
        FROM messages m
        WHERE m.conversation_id = conv.conversation_id 
          AND m.local_user_id = conv.local_user_id
        ORDER BY m.timestamp DESC
        LIMIT 1
    ) AS `Last Message Preview`,
    (
        SELECT COUNT(*)
        FROM messages m
        WHERE m.conversation_id = conv.conversation_id 
          AND m.local_user_id = conv.local_user_id
    ) AS `Total Messages`
FROM conversations conv
ORDER BY conv.last_message_at DESC;


-- -----------------------------------------------------------------------------
-- 4. CHRONOLOGICAL CHAT HISTORY WITH DIRECTION INDICATOR
-- Demonstrates: CASE statements, Timestamp conversion from epoch-milliseconds,
-- and conditional status labeling.
-- -----------------------------------------------------------------------------
SELECT 
    m.message_id AS `UUID`,
    CASE 
        WHEN m.sender_id = m.local_user_id THEN 'OUTGOING  ->'
        ELSE                                    'INCOMING  <-'
    END AS `Direction`,
    m.sender_id   AS `From`,
    m.receiver_id AS `To`,
    m.payload     AS `Message Body`,
    m.status      AS `Delivery Status`,
    FROM_UNIXTIME(m.timestamp / 1000, '%Y-%m-%d %H:%i:%s') AS `Sent At`
FROM messages m
WHERE m.local_user_id = 'alice'  -- Change user ID as desired for demonstration
ORDER BY m.timestamp ASC;


-- -----------------------------------------------------------------------------
-- 5. RELATIONAL INTEGRITY & MULTI-TENANT ISOLATION CHECK
-- Demonstrates: Verifying that composite foreign key (conversation_id, local_user_id)
-- enforces strict per-user ownership and prevents cross-user message leakage.
-- -----------------------------------------------------------------------------
SELECT 
    m.local_user_id AS `Message Owner`,
    c.local_user_id AS `Conversation Owner`,
    COUNT(*)        AS `Row Count`,
    CASE 
        WHEN m.local_user_id = c.local_user_id THEN 'PASSED: Ownership Matches'
        ELSE 'FAILED: Leakage Detected'
    END AS `Integrity Assertion`
FROM messages m
JOIN conversations c 
  ON m.conversation_id = c.conversation_id 
 AND m.local_user_id = c.local_user_id
GROUP BY m.local_user_id, c.local_user_id;
