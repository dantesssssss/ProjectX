import Network.ServerNetwork;
import Network.UdpDiscoveryServer;
import ServerFunctionalities.Enums.LOADPROP;
import ServerFunctionalities.InfoLoader;

import java.net.InetAddress;

public class PcMain {
    public static void main(String[] args) throws Exception {
        ServerNetwork tcp = new ServerNetwork(InfoLoader.load(LOADPROP.ID),InfoLoader.load(LOADPROP.TOKEN),"horsenigler94");
        tcp.startListening();
        System.out.println("TCP server started");
        UdpDiscoveryServer udp = new UdpDiscoveryServer(tcp.getPort());
        System.out.println("UDP pc discovery Server Started");
        System.out.println(InetAddress.getLocalHost().getHostAddress());


        //System.out.println(System.getProperty("os.name").toLowerCase());
        //System.out.println(OS.detect());
    }
}
