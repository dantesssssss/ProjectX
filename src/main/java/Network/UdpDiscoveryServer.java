package Network;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class UdpDiscoveryServer {

    //private static final int DISCOVERY_PORT = 9999;
    private int tcpPort;
    private String pcUID;
    private static Thread waitingThread;

    public UdpDiscoveryServer(int tcpPort, String pcUID) {
        this.tcpPort = tcpPort;
        this.pcUID = pcUID;
    }

    /*public void startListening() {
        waitingThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
                byte[] buffer = new byte[512];

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String msg = new String(packet.getData(), 0, packet.getLength());
                    if (msg.equals("DISCOVER_PC")) {
                        String response = "{" +
                                "\"RESPONSE\":"+"\"PC_FOUND\","+
                                "\"UID\":" + "\"" + pcUID + "\"," +
                                "\"PORT\":" + "\"" + tcpPort + "\"" +
                                "}";
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
        });
        waitingThread.start();
    }*/

    public void startListening(){
        waitingThread = new Thread(() -> {
            try {
                InetAddress addr = InetAddress.getLocalHost();
                JmDNS jmdns = JmDNS.create(addr, "MyDesktopPC");

                Map<String, String> properties = new HashMap<>();
                properties.put("UID", pcUID);
                properties.put("PORT", "" + tcpPort);

                ServiceInfo serviceInfo = ServiceInfo.create(
                        "_mypcapp._tcp.local.",
                        "MyDesktopPC",
                        9999,
                        0, 0,
                        properties
                );

                jmdns.registerService(serviceInfo);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        waitingThread.start();
    }

    public void stopListening() {
        waitingThread.interrupt();
    }

    public void changeUID(String uid) {
        pcUID = uid;
    }
    public void changePort(int port) {
        tcpPort = port;
    }
}
