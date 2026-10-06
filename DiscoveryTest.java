import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class DiscoveryTest {
    public static void main(String[] args)
            throws IOException, InterruptedException {
        if (args.length < 2) {
            System.out.println("Usage: java DiscoveryTest <userId> <tcpPort>");
            return;
        }

        String userId = args[0];
        int tcpPort = Integer.parseInt(args[1]);

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
                        System.out.println("TCP connected to " + peerId);
                    }

                    @Override
                    public void onDisconnected(String peerId) {
                        System.out.println("TCP disconnected from " + peerId);
                    }

                    @Override
                    public void onText(String peerId, String message) {
                        System.out.println(peerId + ": " + message);
                    }

                    @Override
                    public void onFileReceived(String peerId, Path file) {
                        System.out.println(
                                "File from " + peerId + " saved to " + file
                        );
                    }

                    @Override
                    public void onError(String peerId, Exception error) {
                        System.err.println(
                                "TCP error with " + peerId + ": "
                                        + error.getMessage()
                        );
                    }
                }
        );

        tcp.start();
        new Thread(broadcaster).start();
        new Thread(discovery).start();

        System.out.println("Started as " + userId);
        System.out.println("Send a message: /msg <peerId> <message>");
        System.out.println("Send a file:    /file <peerId> <filePath>");

        Thread commands = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+", 3);

                try {
                    if (parts.length == 3 && parts[0].equals("/msg")) {
                        tcp.sendText(parts[1], parts[2]);
                    } else if (parts.length == 3 && parts[0].equals("/file")) {
                        tcp.sendFile(parts[1], Paths.get(parts[2]));
                    } else {
                        System.out.println(
                                "Use /msg <peerId> <message> or "
                                        + "/file <peerId> <filePath>"
                        );
                    }
                } catch (IOException e) {
                    System.err.println("Send failed: " + e.getMessage());
                }
            }
        });

        commands.setDaemon(true);
        commands.start();

        // Keep trying to connect as peers are discovered.
        while (true) {
            for (PeerListener.PeerInfo peer : discovery.getKnownPeers()) {
                tcp.connectTo(peer);
            }

            Thread.sleep(1000);
        }
    }
}