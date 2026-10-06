# ADR-001: Separation of Application Messages from Network Transport

| Field       | Value                                 |
|-------------|---------------------------------------|
| **Status**  | Accepted                              |
| **Date**    | 2026-10-07                            |
| **Authors** | System Design team                    |
| **Context** | MeshLink — Third-semester CE project  |

---

## Context

MeshLink is an offline LAN communication system. It needs to send structured messages (text, future file metadata, future bulletins) between peer devices over a local network. The networking implementation (UDP discovery + TCP framing) was built first and is owned by the networking team member. The application-level message format, storage, and lifecycle are owned by the system design team member.

The question is: **where should message semantics (type, sender, receiver, timestamp, payload, status) live relative to the TCP transport code?**

---

## Decision

**Application-level message semantics are separated from the underlying TCP transport.**

The system is divided into two distinct layers:

### Networking Layer (Ronak's domain — do not modify)

| Component              | Responsibility                                               |
|------------------------|--------------------------------------------------------------|
| `PeerBroadcaster`      | UDP broadcast: announces this device to the LAN every 4 s    |
| `PeerListener`         | UDP receive: maintains a live table of discovered peers      |
| `PeerConnectionManager`| TCP: manages connections, HELLO handshake, TEXT/FILE frames  |

The networking layer transports **opaque strings**. It has no knowledge of MeshLink message structure.

### Message Layer (system design team's domain)

| Component       | Responsibility                                               |
|-----------------|--------------------------------------------------------------|
| `MessageType`   | Enum of supported message categories (CHAT, FILE, BULLETIN)  |
| `MessageStatus` | Lifecycle state machine (CREATED → SENT → RECEIVED / FAILED) |
| `Message`       | Immutable POJO carrying identity, content, and mutable status|
| `MessageCodec`  | Serialises `Message ↔ String` using a versioned wire format  |
| `MessageStore`  | In-memory persistence keyed by messageId (→ SQL later)       |
| `MessageService`| Application service: creates, encodes, decodes, deduplicates |

### Boundary contract

The **only** coupling between layers is two method calls in `DiscoveryTest`:

```java
// OUTBOUND: application layer → networking layer
tcp.sendText(peerId, messageService.encodeForTransport(msg));

// INBOUND: networking layer → application layer
Message decoded = messageService.receive(wire);  // called from onText callback
```

---

## Rationale

### 1. Independent evolution

Ronak can change TCP framing internals (e.g. compression, new frame types) without touching any message-layer code. The system design team can change the wire format version, add new `MessageType` values, or swap `MessageStore` for a SQL backend without touching any networking code.

### 2. Testability

The message layer (`Message`, `MessageCodec`, `MessageStore`, `MessageService`) can be fully unit-tested without starting a socket. All 42 tests in `MeshLinkTest` run with `java MeshLinkTest` and no network.

### 3. Clean responsibility boundary for DBMS work

When SQL is introduced (Parth's DBMS sprint), only `MessageStore` needs to change. The SQL schema maps directly to `Message` fields. `MessageService`, `MessageCodec`, and `DiscoveryTest` are unchanged.

### 4. Duplicate detection hook for FODS work

`MessageStore.put()` uses `putIfAbsent()`, making the deduplication point a single atomic operation. When Parth implements Bloom-filter-based FODS dedup later, the Bloom filter check can be inserted in `MessageService.receive()` before the store call — no other file changes.

---

## Consequences

### Positive

- Networking team and system design team can develop in parallel without merge conflicts.
- Each layer can be tested independently.
- Wire format is versioned: `MESHMSG|1|...`. A version bump is sufficient for future format changes without breaking existing clients (they see version ≠ 1 and reject gracefully).
- SQL migration requires changing exactly one class.

### Negative / Trade-offs

- Slight overhead from Base64 encoding each string field. Acceptable for a LAN prototype; irrelevant at this scale.
- `DiscoveryTest` must explicitly call `markSent()` / `markFailed()` after `sendText()`. This is acceptable for a single entry point but would need a cleaner abstraction if multiple UI components called send.

---

## Alternatives Considered

| Alternative                          | Why rejected                                             |
|--------------------------------------|----------------------------------------------------------|
| Put MessageCodec inside PeerConnectionManager | Violates the networking/application boundary. Ronak's code would need to know about MessageType, senderId, etc. |
| Single monolithic class              | Would make networking and DBMS changes collide; harder to test |
| JSON (Jackson/Gson)                  | Adds external dependency; Base64 pipe-delimited format is sufficient for prototype and uses only the JDK |
| ACK frames at TCP level              | Over-engineering for a prototype; RECEIVED is set locally on successful decode |

---

## Future Work

| Item                           | Owner        | Priority |
|--------------------------------|--------------|----------|
| SQL persistence (replace MessageStore) | DBMS team | Sprint 2 |
| FODS Bloom filter dedup        | Parth        | Sprint 2 |
| FILE MessageType implementation| System design| Sprint 3 |
| BULLETIN broadcast routing     | Networking   | Sprint 3 |
| Message acknowledgement frames | Networking   | Sprint 3 |
