package Network;

import ServerFunctionalities.Enums.LOADPROP;
import ServerFunctionalities.InfoLoader;
import ServerFunctionalities.ServerFunc;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ServerNetwork {
    //SOCKET FOR LISTENING TCP
    private ServerSocket tcpServerSocket = null;
    private InetAddress inetAddress;
    private final String ID = InfoLoader.load(LOADPROP.ID);
    private final String TOKEN = InfoLoader.load(LOADPROP.TOKEN);
    private boolean authCompleted = false;

    public ServerNetwork() {
        try {
            tcpServerSocket = new ServerSocket(0);
            startListening();
            System.out.println("Server started");
            System.out.println("IP: " + tcpServerSocket.getInetAddress() + ":" + tcpServerSocket.getLocalPort());
            System.out.println("Token: " + TOKEN + "\nId: " + ID);
        }catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void startListening() {
        new Thread(() -> {
                Socket clientSocket = null;
                while (true) {
                    try {
                        clientSocket = tcpServerSocket.accept();
                    } catch (IOException e) {
                        System.out.println("Error accepting client connection");
                        e.printStackTrace();
                        System.exit(1);
                    }
                    handleClient(clientSocket);
            }
        }).start();
    }

    private void handleClient(Socket clientSocket) {
        try(
             BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter clientOut = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)
        ){
            System.out.println("Client connected");
            clientSocket.setSoTimeout(120_000);


            String line = clientIn.readLine();
            System.out.println("Received: " + line);

            if (line == null) return;

            if (!ServerFunc.validateToken(line)) {
                clientOut.println("AUTH_FAIL");
                System.out.println("AUTH_FAILED\nDisconnecting client...");
                clientSocket.close();
                return;
            }

            clientOut.println("AUTH_OK");
            System.out.println("AUTH_OK");


            String cmd;
            while ((cmd = clientIn.readLine()) != null) {
                System.out.println("Command received: " + cmd);
                clientOut.println(ServerFunc.handleCommand(cmd));
            }

            System.out.println("Client disconnected");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setInetAddress(InetAddress inetAddress) {
        this.inetAddress = inetAddress;
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }
    public int getPort() {
        return tcpServerSocket.getLocalPort();
    }

    public ServerSocket getTcpServerSocket() {
        return tcpServerSocket;
    }
    public void setTcpServerSocket(ServerSocket tcpServerSocket) {
        this.tcpServerSocket = tcpServerSocket;
    }
}
