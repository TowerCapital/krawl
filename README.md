# MeshLink (`krawl`)

[![Java](https://img.shields.io/badge/Java-JDK%208%2B-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Status](https://img.shields.io/badge/Status-Prototype%20v0.2-green.svg)]()

**MeshLink** is a decentralized, zero-configuration peer-to-peer (P2P) communication tool designed for offline Local Area Networks (LAN). It enables devices on the same Wi-Fi or Ethernet network to automatically discover one another, exchange structured chat messages, and transfer files directly over TCP without requiring an internet connection or central server.

---

## ✨ Features

- **Automatic LAN Discovery**: Broadcasts heartbeat packets via UDP (port `8888`) using subnet-directed broadcasting; no manual IP configuration needed.
- **Direct TCP Transport**: Automatic bidirectional TCP connection handshake (`HELLO`, `TEXT`, `FILE_*` frames).
- **Decoupled Message Architecture**: Clean separation between network transport and application message semantics ([ADR-001](docs/architecture/ADR-001-message-transport-separation.md)).
- **Reliable Lifecycle Tracking**: Tracks message state transitions: `CREATED` → `SENT` → `RECEIVED` (or `FAILED`).
- **Safe Versioned Wire Format**: Pipe-safe, Unicode-safe, Base64-URL encoded wire format (`MESHMSG|1|...`).
- **Idempotency & Deduplication**: UUID-based duplicate detection prevents duplicate delivery or re-processing.
- **Chunked File Transfer**: High-throughput file transfers split into 32 KB frames and saved directly to the `received/` directory.
- **Zero External Dependencies**: Pure Java standard library (JDK only).

---

## 🏗️ Architecture

MeshLink strictly decouples networking transport from application message logic:

```
┌─────────────────────────────────────────────────────────────┐
│                      APPLICATION LAYER                      │
│                                                             │
│   DiscoveryTest (CLI Entry Point & Command Dispatch)        │
│         │                                                   │
│   MessageService  ◄───►  MessageStore (In-memory storage)   │
│         │                                                   │
│   MessageCodec (Encodes/decodes MESHMSG wire protocol)      │
└──────────────────────────────┬──────────────────────────────┘
                               │ (Opaque wire payload)
┌──────────────────────────────┴──────────────────────────────┐
│                      NETWORKING LAYER                       │
│                                                             │
│   PeerBroadcaster       — UDP beacon (every 4s on port 8888)│
│   PeerListener          — Discovers active peers & timeouts │
│   PeerConnectionManager — Manages persistent TCP connections│
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Getting Started

### Prerequisites
- **Java Development Kit (JDK 8 or higher)** installed.
- Two terminal windows on the same machine, or multiple devices connected to the same LAN / Wi-Fi.

### 1. Compile the Source Code

From the project root:

```bash
javac *.java
```

### 2. Run the Unit Tests (Optional)

MeshLink includes standalone test suites that run without network dependencies:

```bash
# Test message creation, encoding/decoding, deduplication, and store
java MeshLinkTest

# Test payload edge cases (spaces, pipes, Unicode, punctuation)
java CodecTest
```

---

## 💻 How to Use

### Running Locally (Two Instances on One Machine)

Open two separate terminal windows:

#### Terminal 1 — Alice
```bash
java DiscoveryTest alice 9001
```

#### Terminal 2 — Bob
```bash
java DiscoveryTest bob 9002
```

> **Note:** Once started, both instances will automatically discover each other over UDP within a few seconds and establish a TCP connection.

### Interactive CLI Commands

Once connected, use the following commands in the prompt:

| Command | Description | Example |
|---|---|---|
| `/peers` | List all discovered and connected LAN peers | `/peers` |
| `/msg <peerId> <message>` | Send a structured text message to a peer | `/msg bob Hey Bob, are you free?` |
| `/file <peerId> <filePath>` | Send a file to a peer | `/file bob ./sample.pdf` |
| `/messages <peerId>` | View conversation history with a peer | `/messages bob` |

---

## 📡 Wire Protocols

### 1. UDP Discovery Beacon (Port `8888`)
```
MESHLINK|<userId>|<hostname>|<tcpPort>
```
*Broadcast every 4 seconds. Peers silent for >15 seconds are automatically pruned.*

### 2. Application Message Protocol
```
MESHMSG|1|<TYPE>|<messageId-b64>|<senderId-b64>|<receiverId-b64>|<timestamp>|<payload-b64>
```
- **Type**: `CHAT`, `FILE`, or `BULLETIN`.
- **Payload & IDs**: URL-safe Base64 without padding (handles special characters, pipes `|`, spaces, and UTF-8).

---

## 📂 Project Structure

```
├── DiscoveryTest.java         # Main interactive CLI application & runner
├── PeerBroadcaster.java       # UDP discovery announcement sender
├── PeerListener.java          # UDP discovery packet listener & peer registry
├── PeerConnectionManager.java # TCP socket manager, framing, and file streaming
├── Message.java               # Immutable message entity with lifecycle status
├── MessageCodec.java          # Serialization / deserialization of wire format
├── MessageService.java        # High-level message operations & deduplication
├── MessageStatus.java         # Lifecycle enum (CREATED, SENT, RECEIVED, FAILED)
├── MessageStore.java          # In-memory message repository (ConcurrentHashMap)
├── MessageType.java           # Message type enum (CHAT, FILE, BULLETIN)
├── MeshLinkTest.java          # Message layer unit tests
├── CodecTest.java             # Codec validation tests
├── docs/                      # Architectural Decision Records (ADRs) & specs
└── received/                  # Default destination folder for received files
```

---

## 🗺️ Roadmap

- [ ] **SQL Persistence**: Replace in-memory `MessageStore` with SQLite/JDBC storage.
- [ ] **Bloom Filter Deduplication**: Probabilistic deduplication for high-volume network traffic.
- [ ] **End-to-End Encryption**: Optional TLS/Noise handshake for peer sessions.
- [ ] **Bulletin Broadcasts**: Gossip routing for network-wide announcements.
