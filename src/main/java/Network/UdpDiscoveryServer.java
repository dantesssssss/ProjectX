package Network;

import ServerFunctionalities.Enums.LOADPROP;
import ServerFunctionalities.InfoLoader;

import java.net.*;

public class UdpDiscoveryServer {

    private static final int DISCOVERY_PORT = 9999;
    private final int tcpPort;
    String pcId = InfoLoader.load(LOADPROP.ID);

    public UdpDiscoveryServer(int tcpPort) {
        this.tcpPort = tcpPort;
        start();

    }

    private void start() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
                byte[] buffer = new byte[512];

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("Received packet from " + packet.getSocketAddress() + ": " + msg);
                    if (msg.equals("DISCOVER_PC")) {

                        String pcName = InetAddress.getLocalHost().getHostName().trim().replaceAll(" ", "");

                        String response = "PC_FOUND|" + "id=" + pcId + "|name=" + pcName + "|port=" + tcpPort;
                        byte[] respData = response.getBytes();

                        DatagramPacket responsePacket = new DatagramPacket(
                                respData,
                                respData.length,
                                packet.getAddress(),
                                packet.getPort()
                        );

                        socket.send(responsePacket);
                    }
                }
            } catch (Exception e) {
                System.out.println("Error starting UDP discovery: " + e);
                e.printStackTrace();
                System.exit(1);
            }
        }).start();
    }
}
