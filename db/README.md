# MeshLink – Local MySQL Database & Full Persistence Integration (Phase 3)

## Overview

Each device in the MeshLink peer-to-peer network runs its **own independent MySQL instance**.  
There is no central server, no cloud database, and no internet dependency.

```
Alice's Laptop          Bob's Laptop
┌──────────────┐        ┌──────────────┐
│  meshlink DB │        │  meshlink DB │
│  (Alice's    │        │  (Bob's      │
│   messages)  │        │   messages)  │
└──────────────┘        └──────────────┘
       │  TCP / LAN  │
       └─────────────┘
```

---

## Architecture & Integration (Phase 3)

The application layer (`MessageService`, `DiscoveryTest`, `PeerConnectionManager`) is now fully integrated with the JDBC persistence layer and local authentication:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   LOCAL AUTHENTICATION (AuthService)                   │
│   • PBKDF2WithHmacSHA256 (100,000 iterations, 16-byte random salt)     │
│   • Constant-time verification (MessageDigest.isEqual)                 │
│   • Local user identity bound to senderId / receiverId                 │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│                     APPLICATION MESSAGE SERVICE                        │
│   • Outbound: Persist (CREATED) ──► Send TCP ──► Update SENT / FAILED  │
│   • Inbound: Decode ──► Deduplicate & Persist (RECEIVED) ──► Record   │
│   • Startup: Reconstruct ConversationThreads & Recover FAILED msgs    │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│                        JDBC REPOSITORY LAYER                           │
│   UserRepository · ContactRepository · ConversationRepository         │
│   MessageRepository (Atomic transactions & composite FK validation)    │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│                         LOCAL MYSQL DATABASE                           │
│   local_users  ·  contacts  ·  conversations  ·  messages              │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Directory Structure

```
d:/mseproject_krawl/krawl/
├── lib/
│   └── mysql-connector-j-8.4.0.jar     — MySQL JDBC driver
├── db/
│   ├── create_database.sql             — Run once as root: creates DB + app user
│   ├── schema.sql                      — All tables, constraints, indexes
│   ├── README.md                       — This documentation
│   └── queries/
│       ├── conversation_history.sql    — Chat history queries
│       ├── recent_conversations.sql    — Inbox / recent chats queries
│       └── contacts.sql                — Contact list + message insert queries
├── User.java                           — User model
├── Contact.java                        — Contact model
├── Conversation.java                   — Conversation model
├── DatabaseConfig.java                 — Configuration loader (~/.meshlink/db.properties & env)
├── DatabaseConnection.java             — JDBC connection management & validation
├── DatabaseInitializer.java            — Non-destructive schema verification on startup
├── AuthService.java                    — PBKDF2WithHmacSHA256 local authentication service
├── AuthException.java                  — Authentication exception
├── UserRepository.java                 — Interface for local_users operations
├── JdbcUserRepository.java             — JDBC implementation for UserRepository
├── ContactRepository.java              — Interface for contacts operations
├── JdbcContactRepository.java          — JDBC implementation for ContactRepository
├── ConversationRepository.java         — Interface for conversations operations
├── JdbcConversationRepository.java     — JDBC implementation for ConversationRepository
├── MessageRepository.java              — Interface for messages operations
├── JdbcMessageRepository.java          — JDBC implementation for MessageRepository
├── InMemoryMessageRepository.java      — In-memory test repository adapter
├── MessageService.java                 — Refactored service with repository injection
├── ConversationHistoryService.java     — History reconstruction and failed-message recovery
├── DiscoveryTest.java                  — Entry point with authentication and DB integration
├── PersistenceTest.java                — Phase 2 persistence test suite (12 tests)
└── MasterIntegrationTest.java          — Phase 3 master integration test suite (15 tests)
```

---

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Java JDK | OpenJDK 8+ (tested on OpenJDK 27) |
| MySQL Server | 5.7+ or 8.0+ (tested on 8.0.46) |
| MySQL Connector/J | 8.0+ / 8.4+ (included in `lib/`) |

---

## Database Setup (per device)

### Step 1 — Run `create_database.sql` as root

```bash
mysql -u root -p < db/create_database.sql
```

This creates:
- Database `meshlink` (utf8mb4, unicode_ci)
- Application user `meshlink_app@localhost` with password `changeme`
- Grants full privileges on `meshlink.*` to `meshlink_app`

### Step 2 — Run `schema.sql` as the application user (or root)

```bash
mysql -u meshlink_app -pchangeme meshlink < db/schema.sql
```

---

## Local Authentication Specifications

MeshLink uses local password hashing:
- **Algorithm**: `PBKDF2WithHmacSHA256`
- **Salt**: 16 cryptographically secure random bytes generated via `SecureRandom`, stored in Base64
- **Iterations**: 100,000 iterations
- **Key length**: 256 bits
- **Comparison**: Constant-time byte array comparison via `MessageDigest.isEqual(...)`
- **Storage**: Only the hash, salt, and iteration count are stored in `local_users`. Plaintext passwords are never stored or logged.

---

## Message Lifecycles

### Outgoing Message Lifecycle
1. `MessageService.createChatMessage(senderId, receiverId, text)` creates the `Message` with status `CREATED`.
2. Persists the message to MySQL (`messages` table) before transmission.
3. Encodes via `MessageCodec.encode(msg)`.
4. Transmits over TCP socket (`tcp.sendText(...)`).
5. If send succeeds: `markSent(msg)` updates status to `SENT` in memory and in MySQL.
6. If send fails (`IOException`): `markFailed(msg)` updates status to `FAILED` in memory and in MySQL, and buffers the message in `PeerMessageBuffer` for automatic retry upon peer reconnection.

### Incoming Message Lifecycle
1. Peer receives TCP `TEXT` frame in `PeerConnectionManager.Listener.onText(...)`.
2. `MessageService.receive(wire)` decodes via `MessageCodec`.
3. Checks for duplicates against MySQL using the primary key constraint (`message_id`).
4. Persists the message to MySQL with status `RECEIVED`.
5. Only after successful persistence, records the message in `ConversationManager` and updates the contact's `last_seen_at` timestamp.
6. Duplicate wire frames are safely rejected (returns `null`), preventing duplicate entries in in-memory conversation threads.

---

## Conversation Reconstruction & Recovery

During application startup:
1. `ConversationHistoryService.reconstructConversations(...)` queries MySQL for all conversations belonging to the local user and populates Parth's `ConversationManager` / `ConversationThread` in strict chronological order (`ORDER BY timestamp ASC, message_id ASC`).
2. `ConversationHistoryService.recoverFailedMessages(...)` queries MySQL for all messages with status `FAILED` and restores them into `PeerMessageBuffer`. When the peer reconnects over TCP, `PeerConnectionManager.onConnected(...)` flushes the queue and delivers them automatically.
3. Messages with status `SENT` or `RECEIVED` are strictly excluded from the retry queue.

---

## Compiling and Running

### Compile all files
```bash
javac -cp ".;lib/mysql-connector-j-8.4.0.jar" *.java
```

### Run Master Integration Tests (Phase 3)
```bash
java -cp ".;lib/mysql-connector-j-8.4.0.jar" MasterIntegrationTest
```
Expected output:
```
Master Integration Results: 15 PASSED, 0 FAILED
SUCCESS: All 15 master integration tests passed completely.
```

### Run Phase 2 Persistence Tests
```bash
java -cp ".;lib/mysql-connector-j-8.4.0.jar" PersistenceTest
```
Expected output:
```
Test Results: 12 PASSED, 0 FAILED
SUCCESS: All 12 persistence test areas passed completely.
```

### Run Standalone Unit Tests
```bash
java -cp ".;lib/mysql-connector-j-8.4.0.jar" MeshLinkTest
java -cp ".;lib/mysql-connector-j-8.4.0.jar" CodecTest
java -cp ".;lib/mysql-connector-j-8.4.0.jar" ConversationTest
```
All pass completely (42 + 8 + 32 = 82 passed, 0 failed).

### Run Application Demo
```bash
java -cp ".;lib/mysql-connector-j-8.4.0.jar" DiscoveryTest <username> [password] <tcpPort>
```
Example:
```bash
# Terminal 1: Alice on port 9001
java -cp ".;lib/mysql-connector-j-8.4.0.jar" DiscoveryTest alice password123 9001

# Terminal 2: Bob on port 9002
java -cp ".;lib/mysql-connector-j-8.4.0.jar" DiscoveryTest bob password123 9002
```
