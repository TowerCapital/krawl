-- =============================================================================
-- MeshLink – test_schema.sql
-- =============================================================================
-- Comprehensive verification script for MySQL schema constraints and queries.
-- =============================================================================

USE meshlink;

-- 1. Verify tables
SELECT '--- [TEST 1] Tables Present ---' AS test_name;
SHOW TABLES;

-- 2. Insert test local users
SELECT '--- [TEST 2] Insert Local Users ---' AS test_name;
INSERT INTO local_users (user_id, username, password_hash, password_salt)
VALUES
    ('alice', 'Alice', 'hash123', 'salt123'),
    ('bob',   'Bob',   'hash456', 'salt456');

SELECT * FROM local_users;

-- 3. Insert contacts
SELECT '--- [TEST 3] Insert Contacts & Test Uniqueness ---' AS test_name;
INSERT INTO contacts (local_user_id, peer_id, display_name)
VALUES ('alice', 'bob', 'Bob Lab Laptop');

-- Duplicate contact for same (local_user_id, peer_id) should be ignored or fail
INSERT IGNORE INTO contacts (local_user_id, peer_id, display_name)
VALUES ('alice', 'bob', 'Duplicate Bob');

SELECT * FROM contacts;

-- 4. Insert conversations
SELECT '--- [TEST 4] Insert Conversations ---' AS test_name;
INSERT INTO conversations (local_user_id, peer_id, last_message_at)
VALUES
    ('alice', 'bob', NOW()),     -- conversation_id = 1
    ('bob', 'alice', NOW());     -- conversation_id = 2

SELECT conversation_id, local_user_id, peer_id, created_at, last_message_at FROM conversations;

-- 5. Test composite conversation ownership enforcement
SELECT '--- [TEST 5] Testing Composite Ownership FK Enforcement ---' AS test_name;
-- Message 1: alice sending to bob in alice's conversation (id=1, local_user='alice') -> SHOULD SUCCEED
INSERT INTO messages
    (message_id, type, sender_id, receiver_id, timestamp, payload, status, local_user_id, conversation_id)
VALUES
    ('msg-uuid-001', 'CHAT', 'alice', 'bob', 1700000000000, 'Hello Bob!', 'SENT', 'alice', 1);

SELECT message_id, local_user_id, conversation_id, payload, status FROM messages;

-- Message 2: Attempting to insert a message with local_user_id='bob' but conversation_id=1 (which belongs to 'alice')
-- This MUST trigger a foreign key constraint violation (fk_messages_conv_user)!
-- We wrap in a test block or execute it to observe the error.

