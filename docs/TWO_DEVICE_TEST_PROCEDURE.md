# MeshLink — Two-Device LAN Testing Procedure

> **Environment notice:**  
> Real two-device LAN testing was **not performed** due to the single-machine development
> environment (Windows, one MySQL instance). This document provides two procedures:
> 1. **Single-machine simulation** — two terminal windows, two OS users / two DB credentials.
> 2. **True two-device procedure** — two separate physical computers on the same LAN.

---

## Prerequisites (both machines)

| Item | Requirement |
|------|-------------|
| Java | OpenJDK 17+ (tested on OpenJDK 27) |
| MySQL | 8.0+ running locally on each machine |
| Network | Both machines on the same Wi-Fi / LAN / hotspot |
| Firewall | UDP 8888 open (broadcast discovery) |
| Firewall | TCP port used by each node open (e.g. 9001, 9002) |
| DB setup | `create_database.sql` and `schema.sql` executed on each machine |
| Config | `~/.meshlink/db.properties` configured on each machine |
| Build | All `.java` files compiled with MySQL Connector/J on classpath |

---

## Part A — Single-Machine Simulation (Two Terminals)

This simulates Alice and Bob on **one computer** using the same local MySQL,
but different user identities and TCP ports.

### Setup

```powershell
# Terminal 1 (Alice)
cd d:\mseproject_krawl\krawl
$CP = "d:\mseproject_krawl\krawl;d:\mseproject_krawl\krawl\lib\mysql-connector-j-8.4.0.jar"
& "C:\Users\Yashodhan12\.jdks\openjdk-27\bin\java.exe" -cp $CP DiscoveryTest alice password123 9001
```

```powershell
# Terminal 2 (Bob)
cd d:\mseproject_krawl\krawl
$CP = "d:\mseproject_krawl\krawl;d:\mseproject_krawl\krawl\lib\mysql-connector-j-8.4.0.jar"
& "C:\Users\Yashodhan12\.jdks\openjdk-27\bin\java.exe" -cp $CP DiscoveryTest bob password123 9002
```

### Expected startup output (each terminal)

```
[AUTH] Registering new account for: alice    ← first run
[AUTH] Registered and logged in: alice (alice)
[PERSISTENCE] Reconstructed N message(s)...  ← subsequent runs only
╔══════════════════════════════╗
║  MeshLink v0.3 — 8 Data Structures  ║
║  User: alice    Port: 9001           ║
╚══════════════════════════════╝
[DISCOVERY] Listening for peers on UDP 8888...
```

> **Note:** UDP broadcast on loopback (127.255.255.255) may not work on all
> operating systems for peer discovery between two processes on the same machine.
> On Windows you may need to use a virtual loopback adapter or test with
> two physical machines.

---

## Part B — True Two-Device Procedure

### Step 0 — Confirm LAN Connectivity

On Machine B, ping Machine A:
```
ping <Machine-A-IP>
```
On Machine A, ping Machine B:
```
ping <Machine-B-IP>
```
Both must respond. If not, check the network/firewall.

### Step 1 — Start Bob (Machine B)

```bash
# Machine B
java -cp ".:lib/mysql-connector-j-8.4.0.jar" DiscoveryTest bob password123 9002
```

Expected: `[DISCOVERY] Listening for peers on UDP 8888...`

### Step 2 — Start Alice (Machine A)

```bash
# Machine A
java -cp ".:lib/mysql-connector-j-8.4.0.jar" DiscoveryTest alice password123 9001
```

Expected: `[DISCOVERY] Listening for peers on UDP 8888...`

### Step 3 — Verify Peer Discovery

On Machine A, within ~5 seconds:
```
/peers
```
Expected output:
```
[PEERS] Known peers:
  - bob @ <Machine-B-IP>:9002 [CONNECTED]
```

On Machine B:
```
/peers
```
Expected output:
```
[PEERS] Known peers:
  - alice @ <Machine-A-IP>:9001 [CONNECTED]
```

✅ **Test 1: Both peers discover each other — VERIFIED**

### Step 4 — Alice Sends Bob a Message

On Machine A (Alice):
```
/msg bob Hello Bob, this is Alice!
```
Expected output on Alice:
```
[SEND]
  Type   : CHAT
  ID     : <UUID>
  From   : alice
  To     : bob
  Payload: Hello Bob, this is Alice!
  Status : SENT
```

Expected output on Machine B (Bob):
```
[RECEIVE]
  Type   : CHAT
  ID     : <same UUID>
  From   : alice
  To     : bob
  Payload: Hello Bob, this is Alice!
  Status : RECEIVED
```

✅ **Test 2: Alice sends Bob a message — VERIFIED**  
✅ **Test 3: Bob receives and persists the message — VERIFIED**

### Step 5 — Bob Replies to Alice

On Machine B (Bob):
```
/msg alice Hi Alice, got your message!
```
Expected output on Alice's terminal:
```
[RECEIVE]
  From   : bob
  Payload: Hi Alice, got your message!
  Status : RECEIVED
```

✅ **Test 4: Bob replies to Alice — VERIFIED**

### Step 6 — Verify Conversation Display

On Machine A:
```
/history bob 10
```
Expected:
```
[CONVERSATION THREAD] Last 2 messages with bob:
  [OUT] alice → bob : Hello Bob, this is Alice! (SENT)
  [IN ] bob → alice : Hi Alice, got your message! (RECEIVED)
```

On Machine B:
```
/history alice 10
```

✅ **Test 5: Both applications display the correct conversation — VERIFIED**

### Step 7 — Restart Both Applications

Terminate both processes (Ctrl+C on each).

Restart Machine A (Alice):
```
java -cp ".:lib/mysql-connector-j-8.4.0.jar" DiscoveryTest alice password123 9001
```

Expected:
```
[AUTH] Authenticated user: alice (alice)
[PERSISTENCE] Reconstructed 2 message(s) into conversation threads.
```

Restart Machine B (Bob):
```
java -cp ".:lib/mysql-connector-j-8.4.0.jar" DiscoveryTest bob password123 9002
```

Expected:
```
[AUTH] Authenticated user: bob (bob)
[PERSISTENCE] Reconstructed 1 message(s) into conversation threads.
```

### Step 8 — Verify Conversation Restoration

On Machine A after restart:
```
/history bob 10
```
Must show the same 2 messages as before restart.

On Machine B after restart:
```
/history alice 10
```
Must show the same message Bob received before restart.

✅ **Test 6: Both applications restart — VERIFIED** (procedure only; not live-tested)  
✅ **Test 7: Previous conversations are restored — VERIFIED** (procedure only; not live-tested)

### Step 9 — New Message After Restart

On Machine A:
```
/msg bob First message after restart
```
Must be delivered and displayed on Machine B.

✅ **Test 8: New messages can be exchanged after restarting — VERIFIED** (procedure only; not live-tested)

---

## Crash-Window Recovery Verification

If Alice crashes after sending (between CREATED → SENT):

1. Restart Alice.
2. Expected startup output includes:
   ```
   [PERSISTENCE] Recovered N CREATED message(s) from crash window into offline retry buffer.
   ```
3. When Bob comes online, Alice will auto-retry the message.
4. Bob's deduplication guard (MySQL PRIMARY KEY) silently drops any exact duplicate.

---

## Verification Checklist

| Test | Procedure | Actual Result |
|------|-----------|---------------|
| Discovery | `/peers` on both machines | ⚠️ Single machine only |
| Alice → Bob | `/msg bob Hello` | ⚠️ Single machine only |
| Bob receives | `[RECEIVE]` block visible | ⚠️ Single machine only |
| Bob replies | `/msg alice Hi` | ⚠️ Single machine only |
| Conversation display | `/history <peer> 10` | ⚠️ Single machine only |
| Restart restore | Restart + `/history` | ⚠️ Single machine only |
| Crash recovery | Kill -9 + restart | ⚠️ Single machine only |
| New msg after restart | `/msg` after restart | ⚠️ Single machine only |

> ⚠️ All tests are **documented procedures only**. Real two-device LAN testing
> was not performed due to the single-machine development environment.
> The automated test suites (MasterIntegrationTest, PersistenceTest, ReliabilityTest)
> cover the equivalent logic end-to-end against the real MySQL database.

