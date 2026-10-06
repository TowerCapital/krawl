# MeshLink Message Layer — Architecture

> **Version:** 0.2 (prototype)  
> **Authors:** System Design team  
> **Status:** In-memory prototype; SQL persistence planned

---

## 1. Message Model

A `Message` represents one application-level unit of communication between two MeshLink peers.

### Fields

| Field       | Type            | Mutability | Description                              |
|-------------|-----------------|------------|------------------------------------------|
| `messageId` | `String` (UUID) | Immutable  | Unique identifier; generated with UUID   |
| `type`      | `MessageType`   | Immutable  | CHAT, FILE, or BULLETIN                  |
| `senderId`  | `String`        | Immutable  | User ID of the originating peer          |
| `receiverId`| `String`        | Immutable  | User ID of the destination peer          |
| `timestamp` | `long`          | Immutable  | Epoch milliseconds at message creation   |
| `payload`   | `String`        | Immutable  | Message content (arbitrary Unicode text) |
| `status`    | `MessageStatus` | Mutable    | CREATED → SENT → RECEIVED (or FAILED)   |

The status field is `volatile` to ensure visibility across threads (sender thread writes, display thread reads).

---

## 2. Message Types (`MessageType`)

| Enum value  | Purpose                                  | Implemented |
|-------------|------------------------------------------|-------------|
| `CHAT`      | Human-readable text between two peers    | ✅ Yes       |
| `FILE`      | Binary file transfer (future)            | ❌ Not yet   |
| `BULLETIN`  | Broadcast message to all peers (future)  | ❌ Not yet   |

Only `CHAT` is functionally active in this prototype. `FILE` and `BULLETIN` exist so the wire format and service layer are already extensible.

---

## 3. Message Lifecycle (`MessageStatus`)

```
  [User types /msg]
         │
    CREATED  ← Message object constructed and stored locally
         │
    [tcp.sendText() called]
         │
    ┌────┴────┐
    │         │
   SENT     FAILED  ← IOException from networking layer
    │
    [Remote peer receives TCP frame]
         │
    RECEIVED  ← Remote application decoded message successfully
```

> **Note:** There are no ACK packets in this prototype. `RECEIVED` is set locally on the receiving JVM by `MessageService.receive()`.

---

## 4. MessageService

**Package:** message layer (no networking dependencies)

### Responsibilities

| Method                              | Description                                      |
|-------------------------------------|--------------------------------------------------|
| `createChatMessage(from, to, text)` | Creates a CHAT message, stores with CREATED      |
| `encodeForTransport(message)`       | Delegates to `MessageCodec.encode()`             |
| `markSent(message)`                 | Sets status → SENT after successful `sendText()` |
| `markFailed(message)`               | Sets status → FAILED after `IOException`         |
| `receive(wire)`                     | Decodes, deduplicates, stores, marks RECEIVED    |
| `getMessagesWithPeer(peerId)`       | Queries `MessageStore` by peer ID                |
| `getAllMessages()`                  | Returns all stored messages                      |

### What MessageService does NOT do

- Open sockets
- Know about TCP frame types
- Know about UDP ports or discovery
- Perform SQL I/O

---

## 5. MessageCodec

**Package:** message layer

Converts `Message ↔ String` using a versioned pipe-delimited wire format.

### Wire Format

```
MESHMSG|1|<TYPE>|<messageId-b64>|<senderId-b64>|<receiverId-b64>|<timestamp>|<payload-b64>
```

| Field          | Encoding       | Notes                             |
|----------------|----------------|-----------------------------------|
| `MESHMSG`      | Plain ASCII    | Prefix; enables fast rejection    |
| `1`            | Plain ASCII    | Protocol version                  |
| `TYPE`         | Plain ASCII    | Enum name (e.g. `CHAT`)           |
| `messageId`    | Base64-URL     | UUID string                       |
| `senderId`     | Base64-URL     | Arbitrary user ID string          |
| `receiverId`   | Base64-URL     | Arbitrary user ID string          |
| `timestamp`    | Decimal long   | Epoch milliseconds                |
| `payload`      | Base64-URL     | Arbitrary Unicode text            |

- **No external dependencies.** Uses `java.util.Base64` from the standard library.
- **No padding.** Base64-URL without `=` padding.
- **Pipe-safe.** All variable fields are Base64-encoded, so payload can contain `|` characters safely.
- **Unicode-safe.** Fields are UTF-8 encoded before Base64 and decoded back to UTF-8.

### Decode behaviour

`MessageCodec.decode()` returns `null` (never throws) when:
- Input is null or empty
- Prefix is not `MESHMSG`
- Version is not `1`
- Type is not a valid `MessageType` name
- Field count is wrong
- Base64 decoding fails
- Timestamp is not a valid long

---

## 6. MessageStore

**Package:** message layer

Temporary in-memory persistence using `ConcurrentHashMap<String, Message>`.

### Operations

| Method              | Description                                      |
|---------------------|--------------------------------------------------|
| `put(message)`      | Stores if ID is new; returns `false` on duplicate|
| `getById(id)`       | Retrieves by UUID; returns `null` if not found   |
| `getByPeer(peerId)` | Filters by sender OR receiver                    |
| `getAll()`          | Returns all messages as an unmodifiable list     |
| `size()`            | Count of stored messages                         |

`put()` uses `ConcurrentHashMap.putIfAbsent()` for atomic idempotent insertion. This is the deduplication mechanism.

### Future SQL Migration

`MessageStore` is the only class that will change when SQL is introduced. The rest of the application (service, codec, transport) stays unchanged. The target schema is:

```sql
CREATE TABLE messages (
    message_id  VARCHAR(36) PRIMARY KEY,
    type        VARCHAR(20) NOT NULL,
    sender_id   VARCHAR(64) NOT NULL,
    receiver_id VARCHAR(64) NOT NULL,
    timestamp   BIGINT      NOT NULL,
    payload     TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL
);
```

---

## 7. Separation from Networking

The networking layer and the message layer know nothing about each other's internals.

```
┌─────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER                    │
│                                                         │
│   DiscoveryTest (entry point + command dispatch)        │
│         │                                               │
│   MessageService  ←→  MessageStore                      │
│         │                                               │
│   MessageCodec                                          │
│         │                                               │
│   (wire string = opaque to networking layer below)      │
└──────────────────────┬──────────────────────────────────┘
                       │  PeerConnectionManager.sendText(peerId, wire)
                       │  PeerConnectionManager.Listener.onText(peerId, wire)
┌──────────────────────┼──────────────────────────────────┐
│                 NETWORKING LAYER                         │
│                                                         │
│   PeerConnectionManager  (TCP connections + frames)     │
│   PeerListener           (UDP peer discovery)           │
│   PeerBroadcaster        (UDP peer announcement)        │
└─────────────────────────────────────────────────────────┘
```

**Boundary contract:**  
- Networking layer calls `Listener.onText(peerId, rawString)`.  
- Application layer calls `tcp.sendText(peerId, encodedString)`.  
- Neither layer imports or references the other's internal types.

---

## 8. Future SQL Persistence

When the DBMS layer is added (next sprint):

1. **Create `MessageRepository.java`** — a JDBC-backed implementation with the same public interface as `MessageStore`.
2. **Inject into `MessageService`** — change the constructor to accept a `MessageStore`-like interface, then pass either the in-memory or SQL-backed implementation.
3. **No other files change.**

The `MessageStatus` enum values already map directly to SQL `VARCHAR` column values (`'CREATED'`, `'SENT'`, `'RECEIVED'`, `'FAILED'`).
