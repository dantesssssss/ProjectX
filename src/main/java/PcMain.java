import Network.ServerNetwork;
import Network.UdpDiscoveryServer;

public class PcMain {
    public static void main(String[] args) throws Exception {
        ServerNetwork tcp = new ServerNetwork();
        System.out.println("TCP server started");
        UdpDiscoveryServer udp = new UdpDiscoveryServer(tcp.getPort());
        System.out.println("UDP pc discovery Server Started");


        //System.out.println(System.getProperty("os.name").toLowerCase());
        //System.out.println(OS.detect());
    }
}
