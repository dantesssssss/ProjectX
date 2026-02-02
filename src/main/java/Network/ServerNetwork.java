package Network;

import Exceptions.ServerAcceptException;
import Exceptions.EncryptionException;
import Network.Security.AesGcmEncryptor;
import ServerFunctionalities.ServerFunc;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class ServerNetwork {
    //SOCKET FOR LISTENING TCP
    private ServerSocket tcpServerSocket = null;
    private InetAddress inetAddress;
    private final String ID;
    private final String TOKEN;
    private volatile boolean running = true;
    private final String pass;
    ExecutorService clientPool = new ThreadPoolExecutor(0, 5, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.AbortPolicy());
    private ServerErrorListener errorListener;

    public ServerNetwork(String ID, String TOKEN,String pass) throws IOException {
        this.ID = ID;
        this.TOKEN = TOKEN;
        this.pass = pass;
        tcpServerSocket = new ServerSocket(0);
    }


    public void startListening() {
         new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = tcpServerSocket.accept();
                    try {
                        clientPool.execute(() -> handleClient(clientSocket));
                    } catch (RejectedExecutionException e) {
                        if (errorListener != null) {
                            errorListener.onClientRejected(e.getMessage());
                            clientSocket.close();
                        }
                    }
                } catch (IOException e) {
                    if (running && errorListener != null) {
                        errorListener.onAcceptError(new ServerAcceptException(e.getMessage()));
                    }
                    break;
                }
            }
        },"server-thread").start();
    }

    public void stopListening() {
        running = false;
        clientPool.shutdownNow();
        try {
            tcpServerSocket.close();
        } catch (IOException ignored) {}
    }

    private void handleClient(Socket socket) {
        try(
             Socket clientSocket = socket;
             BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter clientOut = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)
        ){
            clientSocket.setSoTimeout(120_000);

            String line = clientIn.readLine();
            if (line == null) {
                return;
            }

            line = decrypt(line, pass);


            if (!ServerFunc.validateToken(line,TOKEN)) {
                clientOut.println(encrypt("AUTH_FAIL",pass));
                return;
            }

            clientOut.println(encrypt("AUTH_OK",pass));



            String cmd;
            String response;
            while ((cmd = clientIn.readLine()) != null) {
                cmd = decrypt(cmd, pass);
                response = ServerFunc.handleCommand(cmd);
                clientOut.println(encrypt(response,pass));
            }

        }catch (IOException e) {
            e.printStackTrace();
        }catch (EncryptionException e) {
            e.printStackTrace();
        }
    }

    public void onError(ServerErrorListener listener) {
        this.errorListener = listener;
    }

    public int getPort() {
        return tcpServerSocket.getLocalPort();
    }

    private String encrypt(String line,String pass) throws EncryptionException {
        return AesGcmEncryptor.encrypt(line,pass);
    }

    private String decrypt(String line,String pass) throws EncryptionException {
        return AesGcmEncryptor.decrypt(line,pass);
    }
}
