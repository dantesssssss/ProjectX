package Network;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class UdpDiscoveryServer {
    //SESSION
    private static final int DISCOVERY_PORT = 9999;

    //TCP SESSION
    private int tcpPort;
    private String desktopId;

    //USER SESSION
    private MobileDevicePlatform platform;

    //THREADING
    private static Thread waitingThread;


    public UdpDiscoveryServer(int tcpPort, String desktopId, MobileDevicePlatform platform) {
        this.tcpPort = tcpPort;
        this.desktopId = desktopId;
        //platform have to go soon, this is pain in the ass, as apple doesn't allow usual UDP connections, we will deal with it on server stage
        this.platform = platform;
    }

    public void startListening() {
        if (platform == MobileDevicePlatform.ANDROID) {
            waitingThread = new Thread(() -> {
                try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {
                    byte[] buffer = new byte[512];

                    while (true) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);

                        String msg = new String(packet.getData(), 0, packet.getLength());
                        if (msg.equals("DISCOVER_PC")) {
                            String response = "{" +
                                    "\"RESPONSE\":" + "\"PC_FOUND\"," +
                                    "\"UID\":" + "\"" + desktopId + "\"," +
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
                }
            },"Discovery-Server-Thread");
        }else{
            waitingThread = new Thread(() -> {
                try {
                    InetAddress addr = InetAddress.getLocalHost();
                    JmDNS jmdns = JmDNS.create(addr, "MyDesktopPC");

                    Map<String, String> properties = new HashMap<>();
                    properties.put("UID", desktopId);
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
            },"Discovery-Server-Thread");
        }
        waitingThread.start();
    }

    public void changeMobilePlatform(MobileDevicePlatform platform) {
        this.platform = platform;
        stopListening();
        startListening();
    }

    public void stopListening() {
        waitingThread.interrupt();
    }

    public void changeIdOfDesktop(String uid) {
        desktopId = uid;
        if(platform != MobileDevicePlatform.ANDROID){
            stopListening();
            startListening();
        }
    }
    public void changeTCPPort(int port) {
        tcpPort = port;
        if(platform != MobileDevicePlatform.ANDROID){
            stopListening();
            startListening();
        }
    }
}
