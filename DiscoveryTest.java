import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

/**
 * Entry point for the MeshLink two-device prototype.
 *
 * Usage:
 *   java DiscoveryTest <userId> <tcpPort>
 *
 * Commands:
 *   /msg <peerId> <message>   — send a structured CHAT message
 *   /file <peerId> <filePath> — send a file
 *   /peers                    — list discovered peers and connection status
 *   /messages <peerId>        — show stored messages with a peer
 */
public class DiscoveryTest {
    public static void main(String[] args)
            throws IOException, InterruptedException {
        if (args.length < 2) {
            System.out.println("Usage: java DiscoveryTest <userId> <tcpPort>");
            return;
        }

        String userId = args[0];
        int    tcpPort = Integer.parseInt(args[1]);

        // ── Message layer (sits ABOVE the networking layer) ─────────────────
        MessageService messageService = new MessageService();

        // ── Networking layer ────────────────────────────────────────────────
        PeerBroadcaster broadcaster =
                new PeerBroadcaster(userId, "device-" + userId, tcpPort);
        PeerListener discovery = new PeerListener(userId);

        PeerConnectionManager tcp = new PeerConnectionManager(
                userId,
                tcpPort,
                Paths.get("received"),
                new PeerConnectionManager.Listener() {

                    @Override
                    public void onConnected(String peerId) {
                        System.out.println();
                        System.out.println("[CONNECTION] Connected to " + peerId);
                    }

                    @Override
                    public void onDisconnected(String peerId) {
                        System.out.println();
                        System.out.println("[CONNECTION] Disconnected from " + peerId);
                    }

                    @Override
                    public void onText(String peerId, String wire) {
                        // Hand off entirely to the message layer.
                        Message decoded = messageService.receive(wire);

                        if (decoded != null) {
                            // Valid, non-duplicate MeshLink message.
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
                                    userId, peerId, text);

                            // 2. Encode
                            String wire = messageService.encodeForTransport(msg);

                            // 3. Transport — update status based on outcome
                            try {
                                tcp.sendText(peerId, wire);
                                messageService.markSent(msg);   // SENT

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
                                System.err.println("[SEND FAILED] " + sendErr.getMessage()
                                        + " (message stored with status FAILED)");
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

                        // ── unknown ───────────────────────────────────────
                        default:
                            System.out.println("Commands:");
                            System.out.println("  /msg <peerId> <message>");
                            System.out.println("  /file <peerId> <filePath>");
                            System.out.println("  /peers");
                            System.out.println("  /messages <peerId>");
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
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║           MeshLink  v0.2             ║");
        System.out.println("╠══════════════════════════════════════╣");
        System.out.printf ("║  User  : %-28s ║%n", userId);
        System.out.printf ("║  Port  : %-28d ║%n", tcpPort);
        System.out.println("╠══════════════════════════════════════╣");
        System.out.println("║  /msg <peer> <text>                  ║");
        System.out.println("║  /file <peer> <path>                 ║");
        System.out.println("║  /peers                              ║");
        System.out.println("║  /messages <peer>                    ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println();
        System.out.println("[DISCOVERY] Listening for peers on UDP 8888...");
    }
}