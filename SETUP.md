# MeshLink — Setup Guide

> **Offline-first campus P2P messaging. No internet. No central server.**

---

## Requirements

| Component | Version |
|-----------|---------|
| Java | OpenJDK 17 or later (tested on OpenJDK 27) |
| MySQL | 8.0 or later |
| OS | Windows, Linux, macOS |
| Network | LAN / Wi-Fi / mobile hotspot (no internet needed) |

---

## Step 1 — Clone or Download

```bash
git clone https://github.com/TowerCapital/krawl
cd krawl
```

---

## Step 2 — Configure MySQL

### 2a. Create the database and user (run once as MySQL root)

```bash
mysql -u root -p < db/create_database.sql
```

This creates:
- Database: `meshlink`
- User: `meshlink_app` with ALL PRIVILEGES on `meshlink.*`

> **Change the default password** in `db/create_database.sql` before running in
> any shared environment. The default password `changeme` is for local development only.

### 2b. Create the schema tables

```bash
mysql -u meshlink_app -pchangeme meshlink < db/schema.sql
```

This creates:
- `local_users` — authenticated device users (PBKDF2 hashed passwords)
- `contacts` — LAN peers seen by this device
- `conversations` — conversation threads (composite ownership enforced)
- `messages` — all messages with lifecycle status (CREATED / SENT / RECEIVED / FAILED)

---

## Step 3 — Create the Configuration File

Create `~/.meshlink/db.properties` (Linux/macOS) or `%USERPROFILE%\.meshlink\db.properties` (Windows):

```properties
db.host=localhost
db.port=3306
db.name=meshlink
db.user=meshlink_app
db.password=changeme
db.connectTimeoutMs=5000
db.socketTimeoutMs=10000
```

> **Never commit this file to version control.** It contains your database password.

### Environment variable overrides

Any property can be overridden at runtime:

```bash
export MESHLINK_DB_HOST=192.168.1.100
export MESHLINK_DB_PASSWORD=mysecretpassword
```

---

## Step 4 — Compile

```bash
# Linux / macOS
javac -cp ".:lib/mysql-connector-j-8.4.0.jar" *.java

# Windows (PowerShell)
javac -cp "d:\path\to\krawl;d:\path\to\krawl\lib\mysql-connector-j-8.4.0.jar" *.java
```

---

## Step 5 — Run MeshLink

```bash
# Linux / macOS
java -cp ".:lib/mysql-connector-j-8.4.0.jar" DiscoveryTest <username> <password> <tcpPort>

# Windows
java -cp "d:\path\to\krawl;d:\path\to\krawl\lib\mysql-connector-j-8.4.0.jar" DiscoveryTest alice password123 9001
```

**On first run:** a new local account is created automatically.  
**On subsequent runs:** you are authenticated against the stored PBKDF2 hash.

> If MySQL is unreachable, MeshLink starts in **in-memory fallback mode** — messages
> are still exchangeable on the LAN but are not persisted across restarts.

---

## Step 6 — Available Commands

| Command | Description |
|---------|-------------|
| `/msg <peer> <text>` | Send a text message |
| `/urgent <peer> <text>` | Send high-priority message (Priority Queue) |
| `/file <peer> <path>` | Send a file over TCP |
| `/peers` | List discovered LAN peers |
| `/whois <peer>` | HashMap peer registry lookup |
| `/messages <peer>` | Message history from DB |
| `/history <peer> [n]` | Last N messages (Doubly-Linked List) |
| `/contacts` | Persisted contacts with last-seen |
| `/network` | Graph: peer network topology |
| `/route <from> <to>` | Graph BFS shortest relay path |
| `/locate <id>` | Campus Tree hierarchy path |
| `/search <prefix>` | Trie prefix room search |
| `/nav <id>` / `/back` | Stack breadcrumb navigation |
| `/pending` | Queue: offline pending message buffer |

---

## Running Tests

```powershell
$JAVA = "C:\Users\Yashodhan12\.jdks\openjdk-27\bin\java.exe"
$CP = "d:\mseproject_krawl\krawl;d:\mseproject_krawl\krawl\lib\mysql-connector-j-8.4.0.jar"

# Full test suite
& $JAVA -cp $CP MasterIntegrationTest    # 15 tests: auth, messaging, networking
& $JAVA -cp $CP PersistenceTest          # 12 tests: JDBC layer
& $JAVA -cp $CP MeshLinkTest            # 42 tests: in-memory regressions
& $JAVA -cp $CP CodecTest               #  8 tests: wire format
& $JAVA -cp $CP ConversationTest        # 32 tests: data structures
& $JAVA -cp $CP ReliabilityTest         # 10 tests: edge-case reliability
```

Expected total: **119 passed, 0 failed**.

---

## Security Notes

- Passwords are stored using **PBKDF2WithHmacSHA256**, 100,000 iterations, 16-byte random salt.
- Constant-time comparison prevents timing attacks during login.
- Each device maintains its own independent authentication database.
- No plaintext password is ever written to logs or the database.

> **OWASP note:** 100,000 PBKDF2-HMAC-SHA256 iterations meet NIST SP 800-132 minimums
> (10,000) and exceed them substantially. OWASP 2023 recommends 600,000 iterations for
> PBKDF2-HMAC-SHA256 for new deployments. Increasing iterations would require migrating
> existing stored hashes and is deferred to a future hardening pass.

---

## Network Requirements

| Protocol | Port | Purpose |
|----------|------|---------|
| UDP | 8888 | Peer broadcast discovery |
| TCP | user-chosen | Message and file transfer |

Both ports must be open in your firewall/router for LAN communication.

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `Communications link failure` | Check MySQL is running; check `db.properties` |
| `Access denied for user` | Re-run `create_database.sql` as root |
| `No peers discovered` | Check UDP 8888 is not blocked; both on same LAN |
| `SEND FAILED` | Peer not reachable; message is buffered for retry |
| Auth fails on restart | Username/password must match what was used at first registration |

---

## Two-Device Testing

See [`docs/TWO_DEVICE_TEST_PROCEDURE.md`](docs/TWO_DEVICE_TEST_PROCEDURE.md) for the
step-by-step procedure to test MeshLink across two separate computers on the same LAN.

