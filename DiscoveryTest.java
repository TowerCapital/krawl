import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Scanner;

/**
 * Entry point for the MeshLink two-device prototype.
 *
 * Usage:
 *   java DiscoveryTest <userId> <tcpPort>
 *   or: java DiscoveryTest <username> <password> <tcpPort>
 *
 * Phase 1 Commands (Data Structures: Tree, Queue, Trie, Stack):
 *   /msg <peerId> <message>   — send a structured CHAT message
 *   /file <peerId> <filePath> — send a file
 *   /peers                    — list discovered peers and connection status
 *   /contacts                 — list persisted contacts with last-seen timestamps
 *   /messages <peerId>        — show stored messages with a peer
 *   /locate <id>              — General Tree: campus location hierarchy path
 *   /search <prefix>          — Trie: prefix-based room autocomplete
 *   /nav <id> / /back         — Stack: breadcrumb navigation history
 *   /pending                  — Queue: offline pending message buffer
 *
 * Phase 2 Commands (Data Structures: HashMap, PriorityQueue, LinkedList, Graph):
 *   /whois <peerId>           — HashMap: peer presence registry lookup
 *   /urgent <peerId> <text>   — Priority Queue: send high-priority message
 *   /history <peerId> [n]     — Doubly-Linked List: last N conversation messages
 *   /network                  — Graph: show peer network topology
 *   /route <from> <to>        — Graph BFS: find shortest relay path between peers
 */
public class DiscoveryTest {
    public static void main(String[] args)
            throws IOException, InterruptedException {
        if (args.length < 2) {
            System.out.println("Usage: java DiscoveryTest <userId> <tcpPort>");
            System.out.println("   or: java DiscoveryTest <username> <password> <tcpPort>");
            return;
        }

        String username;
        String password;
        int tcpPort;

        if (args.length >= 3) {
            username = args[0];
            password = args[1];
            tcpPort  = Integer.parseInt(args[2]);
        } else {
            username = args[0];
            password = "password123";
            tcpPort  = Integer.parseInt(args[1]);
        }

        // ── Local Authentication & Persistence Layer ────────────────────────
        DatabaseConnection dbConn = new DatabaseConnection();
        UserRepository userRepo = new JdbcUserRepository(dbConn);
        ContactRepository contactRepo = new JdbcContactRepository(dbConn);
        ConversationRepository convRepo = new JdbcConversationRepository(dbConn);
        MessageRepository messageRepo = new JdbcMessageRepository(dbConn);
        AuthService authService = new AuthService(userRepo);

        String userId;
        boolean dbActive = false;

        try {
            if (dbConn.isReachable(2)) {
                User authUser;
                try {
                    authUser = authService.login(username, password);
                    System.out.println("[AUTH] Authenticated user: " + authUser.getUsername() + " (" + authUser.getUserId() + ")");
                } catch (AuthException e) {
                    System.out.println("[AUTH] Registering new account for: " + username);
                    authUser = authService.register(username, password);
                    System.out.println("[AUTH] Registered and logged in: " + authUser.getUsername() + " (" + authUser.getUserId() + ")");
                }
                userId = authUser.getUserId();
                dbActive = true;
            } else {
                System.out.println("[AUTH] Local MySQL not reachable; using in-memory mode.");
                userId = username.toLowerCase();
            }
        } catch (Exception e) {
            System.err.println("[AUTH] Database notice: " + e.getMessage() + ". Running in fallback mode.");
            userId = username.toLowerCase();
        }

        final String finalUserId = userId;

        // ── Message layer (sits ABOVE the networking layer) ─────────────────
        MessageService messageService = dbActive
                ? new MessageService(messageRepo, convRepo, finalUserId)
                : new MessageService();

        // ── Campus Data Structures Layer (Parth's Module - Phase 1) ────────
        CampusTree campusTree = new CampusTree();
        campusTree.seedDefaultCampus();

        LocationTrie locationTrie = new LocationTrie();
        locationTrie.indexCampusTree(campusTree);

        CampusNavigator navigator = new CampusNavigator(campusTree.getRoot());
        PeerMessageBuffer messageBuffer = new PeerMessageBuffer();

        // ── Advanced Data Structures Layer (Parth's Module - Phase 2) ──────
        PeerRegistry peerRegistry = new PeerRegistry();
        PriorityMessageQueue priorityQueue = new PriorityMessageQueue();
        ConversationManager conversationManager = new ConversationManager(finalUserId);
        PeerGraph peerGraph = new PeerGraph();
        peerGraph.addPeer(finalUserId);

        // ── History Reconstruction & Failed-Message Recovery ───────────────
        if (dbActive) {
            try {
                ConversationHistoryService historyService = new ConversationHistoryService(convRepo, messageRepo);
                int restoredMsgs    = historyService.reconstructConversations(conversationManager, finalUserId);
                int recoveredFailed = historyService.recoverFailedMessages(messageBuffer, finalUserId);
                // Crash-window recovery: messages persisted as CREATED but never marked SENT/FAILED
                int recoveredCreated = historyService.recoverCreatedMessages(messageBuffer, finalUserId);
                if (restoredMsgs > 0) {
                    System.out.println("[PERSISTENCE] Reconstructed " + restoredMsgs + " message(s) into conversation threads.");
                }
                if (recoveredFailed > 0) {
                    System.out.println("[PERSISTENCE] Restored " + recoveredFailed + " FAILED message(s) into offline retry buffer.");
                }
                if (recoveredCreated > 0) {
                    System.out.println("[PERSISTENCE] Recovered " + recoveredCreated
                            + " CREATED message(s) from crash window into offline retry buffer.");
                    System.out.println("[PERSISTENCE] Note: If a recovered message was already delivered, the recipient");
                    System.out.println("[PERSISTENCE] will silently deduplicate it via its database primary key.");
                }
            } catch (SQLException e) {
                System.err.println("[PERSISTENCE] Warning during history reconstruction: " + e.getMessage());
            }
        }

        // ── Networking layer ────────────────────────────────────────────────
        PeerBroadcaster broadcaster =
                new PeerBroadcaster(finalUserId, "device-" + finalUserId, tcpPort);
        PeerListener discovery = new PeerListener(finalUserId);

        final PeerConnectionManager[] tcpHolder = new PeerConnectionManager[1];
        final boolean isDbActive = dbActive;
        PeerConnectionManager tcp = new PeerConnectionManager(
                finalUserId,
                tcpPort,
                Paths.get("received"),
                new PeerConnectionManager.Listener() {

                    @Override
                    public void onConnected(String peerId) {
                        System.out.println();
                        System.out.println("[CONNECTION] Connected to " + peerId);

                        // Phase 2: Update PeerRegistry status & PeerGraph topology
                        peerRegistry.markOnline(peerId);
                        peerGraph.addEdge(finalUserId, peerId);

                        // Phase 3: Update contacts repository
                        if (isDbActive) {
                            try {
                                contactRepo.addContact(finalUserId, peerId, null);
                                contactRepo.updateLastSeen(finalUserId, peerId, new Timestamp(System.currentTimeMillis()));
                            } catch (SQLException ignored) {}
                        }

                        // Flush any pending messages buffered while peer was offline
                        if (messageBuffer.hasPending(peerId)) {
                            List<Message> pending = messageBuffer.flushPending(peerId);
                            System.out.println("[OFFLINE BUFFER] Delivering "
                                    + pending.size() + " queued message(s) to " + peerId + "...");
                            for (Message m : pending) {
                                try {
                                    String wire = messageService.encodeForTransport(m);
                                    if (tcpHolder[0] != null) {
                                        tcpHolder[0].sendText(peerId, wire);
                                    }
                                    messageService.markSent(m);
                                    conversationManager.record(peerId, m);
                                    if (isDbActive) {
                                        contactRepo.updateLastSeen(finalUserId, peerId, new Timestamp(System.currentTimeMillis()));
                                    }
                                    System.out.println("  ✓ Sent queued msg: " + m.getPayload());
                                } catch (IOException e) {
                                    messageService.markFailed(m);
                                    System.err.println("  ✗ Delivery failed: " + e.getMessage());
                                } catch (SQLException e) {
                                    System.err.println("  ✗ Contact update error: " + e.getMessage());
                                }
                            }
                        }
                    }

                    @Override
                    public void onDisconnected(String peerId) {
                        System.out.println();
                        System.out.println("[CONNECTION] Disconnected from " + peerId);

                        // Phase 2: Update PeerRegistry status & PeerGraph topology
                        peerRegistry.markOffline(peerId);
                        peerGraph.removeEdge(finalUserId, peerId);
                    }

                    @Override
                    public void onText(String peerId, String wire) {
                        // Hand off to the message layer (decodes & persists with RECEIVED status)
                        Message decoded = messageService.receive(wire);

                        if (decoded != null) {
                            // Valid, persisted, non-duplicate MeshLink message -> record in memory
                            conversationManager.record(peerId, decoded);
                            if (isDbActive) {
                                try {
                                    contactRepo.addContact(finalUserId, peerId, null);
                                    contactRepo.updateLastSeen(finalUserId, peerId, new Timestamp(System.currentTimeMillis()));
                                } catch (SQLException ignored) {}
                            }
                            System.out.println();
                            System.out.println("[RECEIVE]");
                            System.out.println("  Type   : " + decoded.getType());
                            System.out.println("  ID     : " + decoded.getMessageId());
                            System.out.println("  From   : " + decoded.getSenderId());
                            System.out.println("  To     : " + decoded.getReceiverId());
                            System.out.println("  Payload: " + decoded.getPayload());
                            System.out.println("  Status : " + decoded.getStatus());
                        } else if (!wire.startsWith("MESHMSG|")) {
                            // Raw / legacy string from a non-MeshLink sender.
                            System.out.println();
                            System.out.println("[RAW] " + peerId + ": " + wire);
                        }
                        // If receive() returned null AND it started with MESHMSG|,
                        // it was a duplicate — messageService already printed the
                        // [DEDUPLICATED] notice; nothing more to do here.
                    }

                    @Override
                    public void onFileReceived(String peerId, Path file) {
                        System.out.println();
                        System.out.println("[FILE] Received from " + peerId
                                + " → saved to " + file);
                    }

                    @Override
                    public void onError(String peerId, Exception error) {
                        System.err.println();
                        System.err.println("[ERROR] Peer=" + peerId
                                + " | " + error.getMessage());
                    }
                }
        );

        tcpHolder[0] = tcp;
        tcp.start();
        new Thread(broadcaster).start();
        new Thread(discovery).start();

        banner(userId, tcpPort);

        // ── Command loop ─────────────────────────────────────────────────────
        Thread commands = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                // Split into at most 3 tokens so the message body is kept whole.
                String[] parts = line.split("\\s+", 3);

                try {
                    switch (parts[0]) {

                        // ── /msg <peerId> <text> ──────────────────────────
                        case "/msg": {
                            if (parts.length < 3) {
                                System.out.println("Usage: /msg <peerId> <message>");
                                break;
                            }
                            String peerId = parts[1];
                            String text   = parts[2];

                            // 1. Create & store (CREATED)
                            Message msg = messageService.createChatMessage(
                                    finalUserId, peerId, text);

                            // 2. Encode
                            String wire = messageService.encodeForTransport(msg);

                            // 3. Transport — update status based on outcome
                            try {
                                tcp.sendText(peerId, wire);
                                messageService.markSent(msg);   // SENT
                                conversationManager.record(peerId, msg);

                                System.out.println();
                                System.out.println("[SEND]");
                                System.out.println("  Type   : " + msg.getType());
                                System.out.println("  ID     : " + msg.getMessageId());
                                System.out.println("  From   : " + msg.getSenderId());
                                System.out.println("  To     : " + msg.getReceiverId());
                                System.out.println("  Payload: " + msg.getPayload());
                                System.out.println("  Status : " + msg.getStatus());

                            } catch (IOException sendErr) {
                                messageService.markFailed(msg); // FAILED
                                messageBuffer.bufferMessage(peerId, msg);
                                conversationManager.record(peerId, msg);
                                System.err.println("[SEND FAILED] " + sendErr.getMessage()
                                        + " -> Queued in offline FIFO buffer ("
                                        + messageBuffer.getPendingCount(peerId) + " pending for " + peerId + ")");
                            }
                            break;
                        }

                        // ── /file <peerId> <filePath> ─────────────────────
                        case "/file": {
                            if (parts.length < 3) {
                                System.out.println("Usage: /file <peerId> <filePath>");
                                break;
                            }
                            try {
                                tcp.sendFile(parts[1], Paths.get(parts[2]));
                                System.out.println("[FILE] Sent " + parts[2]
                                        + " to " + parts[1]);
                            } catch (IOException sendErr) {
                                System.err.println("[FILE FAILED] " + sendErr.getMessage());
                            }
                            break;
                        }

                        // ── /peers ────────────────────────────────────────
                        case "/peers": {
                            List<PeerListener.PeerInfo> known =
                                    discovery.getKnownPeers();
                            System.out.println();
                            if (known.isEmpty()) {
                                System.out.println("[PEERS] No peers discovered yet.");
                            } else {
                                System.out.println("[PEERS] Known peers:");
                                for (PeerListener.PeerInfo p : known) {
                                    String status = tcp.isConnected(p.userId)
                                            ? "CONNECTED" : "DISCOVERED";
                                    System.out.println("  - " + p.userId
                                            + " @ " + p.address.getHostAddress()
                                            + ":" + p.tcpPort
                                            + " [" + status + "]");
                                }
                            }
                            break;
                        }

                        // ── /contacts (Persisted Contacts) ────────────────
                        case "/contacts": {
                            System.out.println();
                            if (isDbActive) {
                                List<Contact> contacts = contactRepo.listContacts(finalUserId);
                                if (contacts.isEmpty()) {
                                    System.out.println("[CONTACTS] No saved contacts in database.");
                                } else {
                                    System.out.println("[CONTACTS] Saved contacts for " + finalUserId + ":");
                                    for (Contact c : contacts) {
                                        System.out.println("  - " + c.getPeerId()
                                                + (c.getDisplayName() != null ? " (" + c.getDisplayName() + ")" : "")
                                                + " | Last seen: " + c.getLastSeenAt());
                                    }
                                }
                            } else {
                                System.out.println("[CONTACTS] Database offline; see /peers for active LAN peers.");
                            }
                            break;
                        }

                        // ── /messages <peerId> ────────────────────────────
                        case "/messages": {
                            if (parts.length < 2) {
                                System.out.println("Usage: /messages <peerId>");
                                break;
                            }
                            String peerId = parts[1];
                            List<Message> history =
                                    messageService.getMessagesWithPeer(peerId);
                            System.out.println();
                            if (history.isEmpty()) {
                                System.out.println("[MESSAGES] No messages with "
                                        + peerId + " yet.");
                            } else {
                                System.out.println("[MESSAGES] With " + peerId + ":");
                                for (Message m : history) {
                                    System.out.println();
                                    System.out.println("  [" + m.getType() + "] "
                                            + m.getSenderId() + " → "
                                            + m.getReceiverId());
                                    System.out.println("  " + m.getPayload());
                                    System.out.println("  Status: " + m.getStatus());
                                }
                            }
                            break;
                        }

                        // ── /locate <locationId> (General Tree) ───────────
                        case "/locate": {
                            if (parts.length < 2) {
                                System.out.println("Usage: /locate <roomId/locationId> (e.g. /locate b107)");
                                break;
                            }
                            String locId = parts[1];
                            String path = campusTree.getFullPath(locId, " > ");
                            System.out.println();
                            if (path == null) {
                                System.out.println("[LOCATION] Unknown location ID: " + locId);
                            } else {
                                System.out.println("[CAMPUS TREE HIERARCHY]");
                                System.out.println("  Path: " + path);
                            }
                            break;
                        }

                        // ── /search <prefix> (Trie Prefix Search) ─────────
                        case "/search": {
                            if (parts.length < 2) {
                                System.out.println("Usage: /search <prefix> (e.g. /search b10 or /search iot)");
                                break;
                            }
                            String prefix = parts[1];
                            List<LocationNode> matches = locationTrie.searchPrefix(prefix);
                            System.out.println();
                            if (matches.isEmpty()) {
                                System.out.println("[TRIE SEARCH] No locations found matching prefix: " + prefix);
                            } else {
                                System.out.println("[TRIE SEARCH] Matches for '" + prefix + "':");
                                for (LocationNode node : matches) {
                                    System.out.println("  - " + node.getName()
                                            + " [" + node.getId() + "] (" + node.getType() + ")");
                                }
                            }
                            break;
                        }

                        // ── /nav <locationId> (Stack Navigation History) ──
                        case "/nav": {
                            if (parts.length < 2) {
                                System.out.println("Usage: /nav <locationId> (e.g. /nav b107)");
                                break;
                            }
                            LocationNode target = campusTree.findLocation(parts[1]);
                            if (target == null) {
                                System.out.println("[NAV] Unknown location ID: " + parts[1]);
                            } else {
                                navigator.visit(target);
                                System.out.println();
                                System.out.println("[NAV STACK] Visited: " + target.getName());
                                System.out.println("  Breadcrumbs: " + navigator.getBreadcrumbPath(" -> "));
                            }
                            break;
                        }

                        // ── /back (Stack Pop Navigation) ──────────────────
                        case "/back": {
                            if (!navigator.canGoBack()) {
                                System.out.println("[NAV] At beginning of navigation history (cannot go back).");
                            } else {
                                LocationNode previous = navigator.goBack();
                                System.out.println();
                                System.out.println("[NAV STACK] Returned to: " + previous.getName());
                                System.out.println("  Breadcrumbs: " + navigator.getBreadcrumbPath(" -> "));
                            }
                            break;
                        }

                        // ── /pending (Queue Pending Buffer) ───────────────
                        case "/pending": {
                            System.out.println();
                            int total = messageBuffer.getTotalPendingCount();
                            if (total == 0) {
                                System.out.println("[OFFLINE QUEUE] No pending messages across all peers.");
                            } else {
                                System.out.println("[OFFLINE QUEUE] Total pending messages: " + total);
                                for (String peer : messageBuffer.getPeersWithPending()) {
                                    System.out.println("  - " + peer + ": "
                                            + messageBuffer.getPendingCount(peer) + " message(s) queued");
                                }
                            }
                            break;
                        }

                        // ── /whois <peerId> (HashMap Peer Registry) ───────
                        case "/whois": {
                            if (parts.length < 2) {
                                System.out.println("Usage: /whois <peerId>");
                                break;
                            }
                            String target = parts[1];
                            PeerRegistry.PeerEntry entry = peerRegistry.getPeer(target);
                            System.out.println();
                            if (entry == null) {
                                System.out.println("[PEER REGISTRY] Peer not found in registry: " + target);
                            } else {
                                System.out.println("[PEER REGISTRY - HashMap Lookup]");
                                System.out.println("  " + entry);
                            }
                            break;
                        }

                        // ── /urgent <peerId> <text> (Priority Queue Min-Heap)
                        case "/urgent": {
                            if (parts.length < 3) {
                                System.out.println("Usage: /urgent <peerId> <message>");
                                break;
                            }
                            String peerId = parts[1];
                            String text   = "[URGENT] " + parts[2];

                            Message msg = messageService.createChatMessage(finalUserId, peerId, text);
                            priorityQueue.insert(msg, MessagePriority.URGENT);
                            PriorityMessage pm = priorityQueue.extractMin();

                            String wire = messageService.encodeForTransport(pm.getMessage());
                            try {
                                tcp.sendText(peerId, wire);
                                messageService.markSent(pm.getMessage());
                                conversationManager.record(peerId, pm.getMessage());

                                System.out.println();
                                System.out.println("[SEND - URGENT PRIORITY (Min-Heap Weight: " + pm.getPriority().getWeight() + ")]");
                                System.out.println("  Type   : " + pm.getMessage().getType());
                                System.out.println("  ID     : " + pm.getMessage().getMessageId());
                                System.out.println("  From   : " + pm.getMessage().getSenderId());
                                System.out.println("  To     : " + pm.getMessage().getReceiverId());
                                System.out.println("  Payload: " + pm.getMessage().getPayload());
                                System.out.println("  Status : SENT");
                            } catch (IOException sendErr) {
                                messageService.markFailed(pm.getMessage());
                                messageBuffer.bufferMessage(peerId, pm.getMessage());
                                conversationManager.record(peerId, pm.getMessage());
                                System.err.println("[URGENT FAILED] Queued in offline buffer ("
                                        + messageBuffer.getPendingCount(peerId) + " pending for " + peerId + ")");
                            }
                            break;
                        }

                        // ── /history <peerId> [count] (Doubly-Linked List) ──
                        case "/history": {
                            if (parts.length < 2) {
                                System.out.println("Usage: /history <peerId> [count]");
                                break;
                            }
                            String peerId = parts[1];
                            int count = (parts.length >= 3) ? Integer.parseInt(parts[2]) : 10;
                            List<Message> history = conversationManager.getHistory(peerId, count);
                            System.out.println();
                            if (history.isEmpty()) {
                                System.out.println("[CONVERSATION THREAD] No messages recorded with " + peerId);
                            } else {
                                System.out.println("[CONVERSATION THREAD - Doubly-Linked List] Last "
                                        + history.size() + " messages with " + peerId + ":");
                                for (Message m : history) {
                                    String dir = m.getSenderId().equalsIgnoreCase(finalUserId) ? "OUT" : "IN ";
                                    System.out.println("  [" + dir + "] " + m.getSenderId() + " → "
                                            + m.getReceiverId() + " : " + m.getPayload()
                                            + " (" + m.getStatus() + ")");
                                }
                            }
                            break;
                        }

                        // ── /network (Graph Topology Map) ─────────────────
                        case "/network": {
                            System.out.println();
                            System.out.print(peerGraph.toTopologyString());
                            break;
                        }

                        // ── /route <fromPeer> <toPeer> (Graph BFS Path) ───
                        case "/route": {
                            if (parts.length < 3) {
                                System.out.println("Usage: /route <fromPeer> <toPeer>");
                                break;
                            }
                            String from = parts[1];
                            String to   = parts[2];
                            List<String> path = peerGraph.bfsPath(from, to);
                            System.out.println();
                            if (path.isEmpty()) {
                                System.out.println("[GRAPH ROUTE] No route found between " + from + " and " + to);
                            } else {
                                System.out.println("[GRAPH ROUTE - BFS Shortest Path (" + (path.size() - 1) + " hops)]:");
                                System.out.println("  " + String.join(" ──► ", path));
                            }
                            break;
                        }

                        // ── unknown ───────────────────────────────────────
                        default:
                            System.out.println("Available Commands:");
                            System.out.println("  /msg <peerId> <message>");
                            System.out.println("  /urgent <peerId> <message> (Priority Queue Min-Heap)");
                            System.out.println("  /file <peerId> <filePath>");
                            System.out.println("  /peers");
                            System.out.println("  /whois <peerId>            (HashMap Peer Registry)");
                            System.out.println("  /messages <peerId>");
                            System.out.println("  /history <peerId> [count]  (Doubly-Linked List Chat Thread)");
                            System.out.println("  /locate <locationId>       (General Tree hierarchy path)");
                            System.out.println("  /search <prefix>           (Trie prefix autocomplete)");
                            System.out.println("  /nav <locationId>          (Stack navigation history)");
                            System.out.println("  /back                      (Stack back step)");
                            System.out.println("  /pending                   (Queue offline message buffer)");
                            System.out.println("  /network                   (Graph peer network topology)");
                            System.out.println("  /route <from> <to>         (Graph BFS shortest relay path)");
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR] " + e.getMessage());
                }
            }
        });

        commands.setDaemon(true);
        commands.start();

        // ── Discovery → connection loop ───────────────────────────────────────
        while (true) {
            for (PeerListener.PeerInfo peer : discovery.getKnownPeers()) {
                peerRegistry.register(peer.userId, peer.address.getHostAddress(), peer.tcpPort);
                if (!tcp.isConnected(peer.userId)) {
                    // PeerConnectionManager already deduplicates connection
                    // attempts internally; calling connectTo() repeatedly is safe.
                    tcp.connectTo(peer);
                }
            }
            Thread.sleep(1000);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void banner(String userId, int tcpPort) {
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║            MeshLink v0.3 — 8 Data Structures               ║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.printf ("║  User    : %-46s  ║%n", userId);
        System.out.printf ("║  Port    : %-46d  ║%n", tcpPort);
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.println("║ [COMMUNICATION]                                            ║");
        System.out.println("║  /msg <peer> <text>      - Send text message               ║");
        System.out.println("║  /urgent <peer> <text>   - Priority Queue Min-Heap send    ║");
        System.out.println("║  /file <peer> <path>     - Send file over TCP              ║");
        System.out.println("║  /messages <peer>        - Basic message store             ║");
        System.out.println("║  /history <peer> [n]     - Doubly-Linked List chat thread  ║");
        System.out.println("║ [NETWORK & PEERS]                                          ║");
        System.out.println("║  /peers                  - List discovered & connected LAN ║");
        System.out.println("║  /whois <peer>           - HashMap Peer Registry info      ║");
        System.out.println("║  /network                - Graph Peer Network Topology Map ║");
        System.out.println("║  /route <from> <to>      - Graph BFS Shortest Relay Path   ║");
        System.out.println("║ [CAMPUS LOCATION & OFFLINE QUEUE]                          ║");
        System.out.println("║  /locate <id>            - Tree location hierarchy path    ║");
        System.out.println("║  /search <prefix>        - Trie room prefix search         ║");
        System.out.println("║  /nav <id> & /back       - Stack breadcrumb navigation     ║");
        System.out.println("║  /pending                - Queue offline pending buffer    ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("[DISCOVERY] Listening for peers on UDP 8888...");
    }
}