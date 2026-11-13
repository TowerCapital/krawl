-- =============================================================================
-- MeshLink – schema.sql
-- =============================================================================
-- Purpose  : Define all tables, constraints, and indexes for the local
--            MeshLink MySQL database.
-- Run after: create_database.sql
-- Usage    :
--   mysql -u meshlink_app -p meshlink < schema.sql
--   (or paste into MySQL Workbench against the 'meshlink' schema)
--
-- Architecture:
--   • One database per physical device (Alice's DB != Bob's DB).
--   • No replication, no cloud, no central server.
--   • The in-memory classes (MessageStore, ConversationManager) mirror this
--     schema; SQL is the persistent source of truth for chat history.
--
-- Table map:
--   local_users          – the single account that owns this device's DB
--   contacts             – peer IDs this user has communicated with
--   conversations        – one row per (local_user, peer) pair
--   messages             – every individual message (sent or received)
--
-- Key Constraints & Invariants:
--   1. conversations has UNIQUE KEY (conversation_id, local_user_id)
--   2. messages has composite FK (conversation_id, local_user_id) referencing
--      conversations (conversation_id, local_user_id), strictly enforcing that
--      every message belongs to a conversation owned by the same local user.
--   3. message_id is PRIMARY KEY on messages. No redundant secondary unique
--      constraint on message_id.
--   4. Valid automatic timestamps using CURRENT_TIMESTAMP and ON UPDATE
--      CURRENT_TIMESTAMP.
-- =============================================================================

USE meshlink;

-- =============================================================================
-- TABLE: local_users
-- =============================================================================
-- Stores the identity of the person who owns this machine's database.
-- In the current prototype there is exactly ONE row (the local user),
-- but the table is designed to allow future multi-profile support.
--
-- Identity column:
--   user_id  – the same string used as senderId / receiverId in Message.java
--              and as the localUserId in ConversationManager. It is a VARCHAR
--              because the existing code uses arbitrary human-readable IDs
--              (e.g. "alice", "bob"), NOT auto-incremented integers.
-- =============================================================================
CREATE TABLE IF NOT EXISTS local_users (
    user_id             VARCHAR(64)   NOT NULL,
    username            VARCHAR(64)   NOT NULL,

    -- PBKDF2 / bcrypt storage columns (hash + salt stored separately so the
    -- application can choose its KDF independently of MySQL).
    password_hash       VARCHAR(255)  NOT NULL,
    password_salt       VARCHAR(128)  NOT NULL,
    -- Iteration count for PBKDF2; not used for bcrypt but kept for flexibility.
    password_iterations INT UNSIGNED  NOT NULL DEFAULT 100000,

    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_local_users_username (username)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Local application user account(s) for this device.';


-- =============================================================================
-- TABLE: contacts
-- =============================================================================
-- Persists the local user's peer/contact list across restarts.
-- Maps directly to PeerRegistry.PeerEntry in memory.
--
-- peer_id:
--   The identifier the remote peer advertises in UDP discovery and in
--   Message.senderId / receiverId. Stored in its raw form (not normalized)
--   to match what comes off the wire; normalization (trim/lowercase) is
--   the application's responsibility before inserting.
--
-- display_name:
--   Optional human-friendly label (e.g. "Parth's Laptop"). NULL if the peer
--   has not been renamed by the local user.
--
-- Uniqueness:
--   A local user cannot have duplicate contact rows for the same peer.
--   (local_user_id, peer_id) is the natural composite key.
-- =============================================================================
CREATE TABLE IF NOT EXISTS contacts (
    id              BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    local_user_id   VARCHAR(64)       NOT NULL,
    peer_id         VARCHAR(64)       NOT NULL,
    display_name    VARCHAR(128)      NULL      DEFAULT NULL,

    -- When this peer was first seen / saved as a contact.
    first_added_at  DATETIME          NOT NULL  DEFAULT CURRENT_TIMESTAMP,
    -- Updated automatically whenever this row is modified.
    last_seen_at    DATETIME          NOT NULL  DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    -- Constraints
    PRIMARY KEY (id),

    -- Prevent duplicate contacts for the same (user, peer) pair.
    UNIQUE KEY uq_contacts_user_peer (local_user_id, peer_id),

    -- Index for fast contact lookup by local user
    INDEX idx_contacts_local_user (local_user_id),

    -- FK back to the local account that owns this contact entry.
    CONSTRAINT fk_contacts_local_user
        FOREIGN KEY (local_user_id)
        REFERENCES local_users (user_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Peer contact list for each local user. Mirrors PeerRegistry (in-memory).';


-- =============================================================================
-- TABLE: conversations
-- =============================================================================
-- One row per (local_user, peer) pair. Mirrors ConversationThread in memory.
--
-- Why a separate table?
--   • Allows O(1) lookup of "conversation ID" when inserting messages.
--   • Stores metadata (last_message_at) that powers the "recent chats" list
--     without scanning the entire messages table.
--   • The UNIQUE constraint enforces the same deduplication that
--     ConversationManager.record() does in-memory via computeIfAbsent().
--
-- conversation_id:
--   Surrogate BIGINT key – the messages table FKs to this.
--
-- Composite unique key (conversation_id, local_user_id):
--   Enforces that each conversation is uniquely identified along with its
--   local_user_id. Crucially, this composite key enables the composite foreign
--   key constraint in the `messages` table:
--   FOREIGN KEY (conversation_id, local_user_id) REFERENCES conversations (conversation_id, local_user_id)
--   This strictly prevents messages from belonging to a conversation owned
--   by a different local user.
-- =============================================================================
CREATE TABLE IF NOT EXISTS conversations (
    conversation_id     BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    local_user_id       VARCHAR(64)       NOT NULL,

    -- The peer on the other end of this 1-to-1 conversation.
    -- Matches ConversationThread.peerUserId.
    peer_id             VARCHAR(64)       NOT NULL,

    created_at          DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Denormalized timestamp of the most-recent message in this thread.
    -- Updated by the application whenever a new message is inserted into
    -- 'messages' for this conversation. Starts NULL (no messages yet).
    last_message_at     DATETIME          NULL     DEFAULT NULL,

    -- Constraints
    PRIMARY KEY (conversation_id),

    -- One conversation per (local_user, peer) pair – same invariant as
    -- ConversationManager's internal Map<peerId, ConversationThread>.
    UNIQUE KEY uq_conversations_user_peer (local_user_id, peer_id),

    -- Composite unique key referenced by messages (conversation_id, local_user_id)
    UNIQUE KEY uq_conversations_id_user (conversation_id, local_user_id),

    -- Index for fast recent conversations query ordered by last message time
    INDEX idx_conversations_user_last (local_user_id, last_message_at DESC),

    -- FK to the owning local user.
    CONSTRAINT fk_conversations_local_user
        FOREIGN KEY (local_user_id)
        REFERENCES local_users (user_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='One row per (local_user, peer) chat thread. Mirrors ConversationManager.';


-- =============================================================================
-- TABLE: messages
-- =============================================================================
-- The central fact table. Every message – sent or received – produces one row.
--
-- Column mapping to Message.java:
--   message_id    ← Message.messageId   (UUID string, e.g. "550e8400-…")
--   type          ← Message.type        (MessageType enum name: CHAT/FILE/BULLETIN)
--   sender_id     ← Message.senderId    (the peer ID who composed the message)
--   receiver_id   ← Message.receiverId  (the peer ID the message is addressed to)
--   timestamp     ← Message.timestamp   (System.currentTimeMillis() epoch-ms long)
--   payload       ← Message.payload     (raw message text; MEDIUMTEXT for file payloads)
--   status        ← Message.status      (MessageStatus enum name below)
--
-- Delivery status:
--   CREATED  – Message object constructed; not yet handed to the network layer.
--   SENT     – Handed to PeerConnectionManager.sendText() without IOException.
--   RECEIVED – Decoded and stored successfully by the RECEIVING application.
--              NOTE: RECEIVED means delivery status only. It does NOT imply
--              that the local user has seen or read the message.
--   FAILED   – Transmission attempt threw an IOException.
--
-- Additional local-ownership columns (not in Message.java – added by DB layer):
--   local_user_id     – which local user's DB this row lives in
--   conversation_id   – part of composite FK to conversations
--   stored_at         – wall-clock time the row was inserted
--
-- Primary key:
--   message_id is a UUID produced by Message.create() → UUID.randomUUID().
--   The PRIMARY KEY on message_id alone guarantees uniqueness. Any duplicate
--   message_id is rejected by this constraint.
--
-- Composite foreign key (conversation_id, local_user_id):
--   References conversations (conversation_id, local_user_id).
--   This guarantees that a message can ONLY be inserted into a conversation
--   that is owned by the exact same local_user_id. Mismatched conversation
--   and user ownership is rejected at the database level.
-- =============================================================================
CREATE TABLE IF NOT EXISTS messages (
    -- Wire-protocol identity (from Message.java)
    message_id          VARCHAR(36)         NOT NULL,
    type                VARCHAR(20)         NOT NULL
                            COMMENT 'CHAT | FILE | BULLETIN',
    sender_id           VARCHAR(64)         NOT NULL,
    receiver_id         VARCHAR(64)         NOT NULL,
    -- epoch milliseconds (System.currentTimeMillis()); BIGINT covers year 292M.
    timestamp           BIGINT              NOT NULL,
    -- MEDIUMTEXT supports up to 16 MB; sufficient for base64-encoded file payloads.
    payload             MEDIUMTEXT          NOT NULL,
    -- MessageStatus enum: CREATED | SENT | RECEIVED | FAILED
    status              VARCHAR(20)         NOT NULL DEFAULT 'CREATED',

    -- Local-DB ownership columns
    local_user_id       VARCHAR(64)         NOT NULL
                            COMMENT 'Which local account owns this row',
    conversation_id     BIGINT UNSIGNED     NOT NULL
                            COMMENT 'Part of composite FK to conversations',
    -- DB wall-clock insertion time
    stored_at           DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    PRIMARY KEY (message_id),

    -- Composite FK: enforces that message belongs to a conversation owned
    -- by the EXACT same local user.
    CONSTRAINT fk_messages_conv_user
        FOREIGN KEY (conversation_id, local_user_id)
        REFERENCES conversations (conversation_id, local_user_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    -- FK to local_users (keeps account deletion cascade clean)
    CONSTRAINT fk_messages_local_user
        FOREIGN KEY (local_user_id)
        REFERENCES local_users (user_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    -- Indexes
    INDEX idx_messages_conv_ts (conversation_id, timestamp),
    INDEX idx_messages_user_ts (local_user_id, timestamp),
    INDEX idx_messages_sender (sender_id),
    INDEX idx_messages_status (status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Persistent store for all MeshLink messages. Replaces MessageStore ConcurrentHashMap.';
