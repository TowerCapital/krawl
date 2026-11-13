USE meshlink;

-- Conversation 1 belongs to 'alice'
-- Attempting to insert a message with conversation_id = 1 and local_user_id = 'bob'
-- Must fail with error 1452 (Cannot add or update a child row: a foreign key constraint fails)
INSERT INTO messages
    (message_id, type, sender_id, receiver_id, timestamp, payload, status, local_user_id, conversation_id)
VALUES
    ('msg-uuid-fail-001', 'CHAT', 'bob', 'alice', 1700000000001, 'Spoofed message', 'CREATED', 'bob', 1);

