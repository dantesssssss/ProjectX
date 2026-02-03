package Network;

import Exceptions.EncryptionException;
import Network.Security.AesGcmEncryptor;
import ServerFunctionalities.Enums.ExceptionServerMessages;
import ServerFunctionalities.Enums.ResponseMessages;
import ServerFunctionalities.ServerFunc;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.*;

public class ServerNetwork {
    private final ServerSocket tcpServerSocket;
    private final InetAddress inetAddress;
    private final String TOKEN;
    private final String ID;
    private volatile boolean running = true;
    private volatile String pass;
    ExecutorService clientPool;
    private volatile ServerListener serverListener;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private Thread serverThread;
    private volatile boolean pairingMode;


    public ServerNetwork(String TOKEN,String ID,String pass,boolean pairingMode) throws IOException {
        this.TOKEN = TOKEN;
        this.pass = pass;
        this.tcpServerSocket = new ServerSocket(0);
        //it's the best you can do to get IP of server,it may be buggy in case of VPN
        this.inetAddress =  InetAddress.getLocalHost();
        this.ID = ID;
        this.pairingMode = pairingMode;
        if (this.pairingMode) {
            clientPool = new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new SynchronousQueue<>(),new ThreadPoolExecutor.AbortPolicy());
        }else{
            clientPool = new ThreadPoolExecutor(1,2,60,TimeUnit.SECONDS,new SynchronousQueue<>(),new ThreadPoolExecutor.AbortPolicy());
        }
    }

    public void startListening() {
        if (serverThread != null && serverThread.isAlive()) {
            serverListener.onException(ExceptionServerMessages.LISTENING_ALREADY_STARTED.toString());
            return;
        }
        serverThread = new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = tcpServerSocket.accept();
                    try {
                        if (pairingMode) {
                            clientPool.execute(() -> handlePairingClient(clientSocket));
                        } else {
                            clientPool.execute(() -> handleClient(clientSocket));
                        }
                    } catch (RejectedExecutionException e) {
                        throwException(ExceptionServerMessages.CLIENT_REJECTED_MAXIMUM_CLIENT_CAPACITY_REACHED);

                        clientSocket.close();
                    }
                } catch (IOException e) {
                    if (running) {
                        serverListener.onException(ExceptionServerMessages.NETWORK_EXCEPTION.toString());
                    }
                    break;
                }
            }
        },"server-thread");
        serverThread.start();
    }

    public void stopListening() {
        if (running) {
            running = false;
            clientPool.shutdownNow();
            try {
                tcpServerSocket.close();
                for (Socket socket : clients) {
                    socket.close();
                }
            } catch (IOException ignored) {}
        }
    }

    private synchronized void switchToNormalMode() {
        if (!pairingMode) {
            return;
        }

        pairingMode = false;

        ExecutorService oldPool = clientPool;


        new Thread(() -> {
            oldPool.shutdown();
            try {
                oldPool.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                oldPool.shutdownNow();
            }
        }).start();
        clientPool = new ThreadPoolExecutor(1,2,60,TimeUnit.SECONDS,new SynchronousQueue<>(),new ThreadPoolExecutor.AbortPolicy());
    }

    private void handleClient(Socket socket) {
        try(
             Socket clientSocket = socket;
             BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter clientOut = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)
        ){
            clients.add(clientSocket);
            clientSocket.setSoTimeout(120_000);

            String line = clientIn.readLine();
            if (line == null) {
                return;
            }

            line = AesGcmEncryptor.decrypt(line, pass);


            if (!ServerFunc.validateToken(line,TOKEN)) {
                clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.AUTH_FAIL.toString(),pass));
                return;
            }

            clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.AUTH_OK.toString(),pass));

            String cmd;
            String response;
            while ((cmd = clientIn.readLine()) != null) {
                try {
                    cmd = AesGcmEncryptor.decrypt(cmd, pass);
                    response = ServerFunc.handleCommand(cmd);
                    clientOut.println(AesGcmEncryptor.encrypt(response,pass));
                }catch (EncryptionException e) {
                    clientOut.println(ResponseMessages.ENCRYPTION_EXCEPTION);
                    break;
                }
            }
        }catch (SocketTimeoutException ignored){

        }catch (IOException e) {
            throwException(ExceptionServerMessages.NETWORK_EXCEPTION);
        }catch (EncryptionException e) {
            throwException(ExceptionServerMessages.ENCRYPTION_EXCEPTION);
        }finally {
            clients.remove(socket);
        }
    }


    private void handlePairingClient(Socket socket){
        try(
                Socket clientSocket = socket;
                BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter clientOut = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)
        ){
            clients.add(clientSocket);
            clientSocket.setSoTimeout(120_000);
            String line = clientIn.readLine();
            if (line == null) {
                return;
            }
            try {
                line = AesGcmEncryptor.decrypt(line, pass);
            }catch (EncryptionException e) {
                clientOut.println(ResponseMessages.WRONG_ENCRYPTION_WORDS_CAN_NOT_DECRYPT);
                serverListener.onPairing(false);
                return;
            }


            if (!line.trim().equals(TOKEN)) {
                clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.WRONG_TOKEN.toString(),pass));
                serverListener.onPairing(false);
                return;
            }

            clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.OK.toString(),pass));

            line = clientIn.readLine();

            if (line == null) {
                return;
            }
            line = AesGcmEncryptor.decrypt(line, pass);

            if(!line.trim().equals(ID)) {
                clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.WRONG_ID.toString(),pass));
                serverListener.onPairing(false);
                return;
            }

            clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.OK.toString(),pass));

            clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.SUCCESS_PAIRING.toString(),pass));
            serverListener.onPairing(true);
            clients.remove(clientSocket);
            switchToNormalMode();
        }catch (IOException e) {
            if (running) {
                serverListener.onPairing(false);
                throwException(ExceptionServerMessages.NETWORK_EXCEPTION);
            }
        }catch (EncryptionException e) {
            throwException(ExceptionServerMessages.ENCRYPTION_EXCEPTION);
        }
    }

    public void setServerListener(ServerListener listener) {
        this.serverListener = listener;
    }
    public InetAddress getInetAddress() {
        return inetAddress;
    }
    public int getPort() {
        return tcpServerSocket.getLocalPort();
    }
    public void setPassword(String password) {
        this.pass = password;
    }
    private void throwException(ExceptionServerMessages message){
        if (serverListener != null) {
            serverListener.onException(message.toString());
        }
    }
}
