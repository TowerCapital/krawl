import java.net.*;
import java.util.*;

/**
 * Broadcasts "I'm here" UDP packets every few seconds so other
 * MeshLink instances on the LAN can discover this device.
 */
public class PeerBroadcaster implements Runnable {

    private static final int DISCOVERY_PORT = 8888;
    private static final int BROADCAST_INTERVAL_MS = 4000;

    private final String userId;
    private final String hostname;
    private final int tcpPort; // port this device listens on for actual chat connections

    private volatile boolean running = true;

    public PeerBroadcaster(String userId, String hostname, int tcpPort) {
        this.userId = userId;
        this.hostname = hostname;
        this.tcpPort = tcpPort;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);

            String message = "MESHLINK|" + userId + "|" + hostname + "|" + tcpPort;
            byte[] data = message.getBytes();

            InetAddress broadcastAddr = getBroadcastAddress();

            while (running) {
                DatagramPacket packet = new DatagramPacket(
                        data, data.length, broadcastAddr, DISCOVERY_PORT
                );
                socket.send(packet);
                Thread.sleep(BROADCAST_INTERVAL_MS);
            }
        } catch (Exception e) {
            System.err.println("Broadcaster stopped: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
    }

    /**
     * Computes the subnet-directed broadcast address (e.g. 192.168.1.255)
     * instead of relying on 255.255.255.255, which some networks block.
     * Falls back to 255.255.255.255 if this fails.
     */
    private InetAddress getBroadcastAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                for (InterfaceAddress ifaceAddr : iface.getInterfaceAddresses()) {
                    InetAddress broadcast = ifaceAddr.getBroadcast();
                    if (broadcast != null) {
                        return broadcast; // first usable one found
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Could not determine broadcast address, falling back.");
        }
        try {
            return InetAddress.getByName("255.255.255.255");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
