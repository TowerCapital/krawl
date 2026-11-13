USE meshlink;

-- 'msg-uuid-001' already exists in messages.
-- Attempting to insert another message with the same message_id must fail with Duplicate entry for key 'messages.PRIMARY'
INSERT INTO messages
    (message_id, type, sender_id, receiver_id, timestamp, payload, status, local_user_id, conversation_id)
VALUES
    ('msg-uuid-001', 'CHAT', 'alice', 'bob', 1700000000002, 'Duplicate message ID test', 'CREATED', 'alice', 1);

