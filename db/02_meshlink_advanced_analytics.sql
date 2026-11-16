-- =============================================================================
-- MESHLINK DBMS DEMONSTRATION — ADVANCED ANALYTICS & COMPLEX QUERIES
-- Course: Database Management Systems (DBMS)
-- Concepts: Aggregate Functions, Window Functions (OVER/PARTITION), CTEs, Ratios
-- =============================================================================
USE meshlink;

-- -----------------------------------------------------------------------------
-- 1. CONVERSATION METRICS & DATA VOLUME ANALYSIS
-- Demonstrates: Aggregation functions (COUNT, MIN, MAX, AVG, SUM),
-- string length calculations, and duration measurement.
-- -----------------------------------------------------------------------------
SELECT 
    conv.local_user_id AS `Local User`,
    conv.peer_id       AS `Peer`,
    COUNT(m.message_id) AS `Total Messages`,
    CONCAT(ROUND(SUM(CHAR_LENGTH(m.payload)) / 1024, 2), ' KB') AS `Total Data Volume`,
    ROUND(AVG(CHAR_LENGTH(m.payload)), 1) AS `Avg Chars/Message`,
    FROM_UNIXTIME(MIN(m.timestamp) / 1000, '%Y-%m-%d %H:%i') AS `First Message`,
    FROM_UNIXTIME(MAX(m.timestamp) / 1000, '%Y-%m-%d %H:%i') AS `Latest Message`,
    ROUND((MAX(m.timestamp) - MIN(m.timestamp)) / 60000, 1)   AS `Conversation Span (Mins)`
FROM conversations conv
LEFT JOIN messages m 
       ON conv.conversation_id = m.conversation_id 
      AND conv.local_user_id = m.local_user_id
GROUP BY conv.local_user_id, conv.peer_id, conv.conversation_id
ORDER BY `Total Messages` DESC;


-- -----------------------------------------------------------------------------
-- 2. MESSAGE DELIVERY LIFECYCLE BREAKDOWN & RELIABILITY RATE
-- Demonstrates: Conditional SUM with CASE, percentage calculation,
-- and computing delivery success ratios.
-- -----------------------------------------------------------------------------
SELECT 
    local_user_id AS `Device User`,
    COUNT(*) AS `Total Logged Messages`,
    SUM(CASE WHEN status = 'SENT'     THEN 1 ELSE 0 END) AS `Sent Count`,
    SUM(CASE WHEN status = 'RECEIVED' THEN 1 ELSE 0 END) AS `Received Count`,
    SUM(CASE WHEN status = 'FAILED'   THEN 1 ELSE 0 END) AS `Failed Count`,
    CONCAT(
        ROUND(
            (SUM(CASE WHEN status IN ('SENT', 'RECEIVED') THEN 1 ELSE 0 END) * 100.0) / COUNT(*),
            1
        ),
        '%'
    ) AS `Delivery Reliability Rate`
FROM messages
GROUP BY local_user_id;


-- -----------------------------------------------------------------------------
-- 3. WINDOW FUNCTION: TOP 3 MOST RECENT MESSAGES PER CONVERSATION
-- Demonstrates: SQL Window Functions (ROW_NUMBER() with PARTITION BY),
-- Common Table Expressions (WITH clause / CTE).
-- -----------------------------------------------------------------------------
WITH RankedMessages AS (
    SELECT 
        conversation_id,
        local_user_id,
        sender_id,
        payload,
        status,
        timestamp,
        ROW_NUMBER() OVER (
            PARTITION BY conversation_id, local_user_id 
            ORDER BY timestamp DESC
        ) AS rank_order
    FROM messages
)
SELECT 
    local_user_id AS `Owner`,
    conversation_id AS `Thread ID`,
    sender_id AS `Sender`,
    payload AS `Recent Message`,
    status AS `Status`,
    FROM_UNIXTIME(timestamp / 1000, '%H:%i:%s') AS `Time`
FROM RankedMessages
WHERE rank_order <= 3
ORDER BY conversation_id, rank_order ASC;


-- -----------------------------------------------------------------------------
-- 4. PEER COMMUNICATION RANKING (LEADERBOARD)
-- Demonstrates: DENSE_RANK() window function to rank contacts by engagement.
-- -----------------------------------------------------------------------------
SELECT 
    local_user_id AS `User`,
    receiver_id   AS `Contact`,
    COUNT(*)      AS `Messages Sent`,
    DENSE_RANK() OVER (
        PARTITION BY local_user_id 
        ORDER BY COUNT(*) DESC
    ) AS `Activity Rank`
FROM messages
WHERE sender_id = local_user_id
GROUP BY local_user_id, receiver_id
ORDER BY local_user_id, `Activity Rank`;


-- -----------------------------------------------------------------------------
-- 5. STORAGE FOOTPRINT & ROW-LEVEL AUDIT
-- Demonstrates: System metadata queries, average row size, and table sizing.
-- -----------------------------------------------------------------------------
SELECT 
    table_name AS `Table`,
    table_rows AS `Approx Row Count`,
    ROUND(data_length / 1024, 2) AS `Data Size (KB)`,
    ROUND(index_length / 1024, 2) AS `Index Size (KB)`,
    ROUND((data_length + index_length) / 1024, 2) AS `Total Size (KB)`
FROM information_schema.tables
WHERE table_schema = 'meshlink'
ORDER BY (data_length + index_length) DESC;
