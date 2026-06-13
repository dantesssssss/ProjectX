package Network;

import Exceptions.EncryptionException;
import Exceptions.HandleCommandException;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class TCPServer {

    //USER SESSION
    private volatile String token;
    private volatile String idOfDesktop;
    private volatile String encryptionKey;

    //SESSION
    private volatile boolean running = true;
    private final ServerSocket serverSocket;
    private final InetAddress inetAddress;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean pairingMode;

    //THREADING
    private final ThreadPoolExecutor clientPool;
    private Thread serverThread;

    //LISTENER
    private volatile TCPServerListener serverListener;



    public TCPServer(String idOfDesktop, String token, String encryptionKey, boolean pairingMode) throws IOException {
        this.token = token;
        this.idOfDesktop = idOfDesktop;
        this.encryptionKey = encryptionKey;

        this.serverSocket = new ServerSocket(0);
        //it's the best you can do to get IP of server,it may be buggy in case of VPN
        this.inetAddress = InetAddress.getLocalHost();
        this.pairingMode = new AtomicBoolean(pairingMode);

        //pool that would have the max of connections at same time = 3, and kill every thread after it's not used after 60 sec
        clientPool = new ThreadPoolExecutor(1,3,60,TimeUnit.SECONDS,new SynchronousQueue<>(),new ThreadPoolExecutor.AbortPolicy());
        clientPool.allowCoreThreadTimeOut(true);
    }

    public void startListening() {
        if (serverThread != null && serverThread.isAlive()) {
            throwException(ExceptionServerMessages.LISTENING_ALREADY_STARTED);
            return;
        }

        serverThread = new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clients.add(clientSocket);
                    try {
                        if (pairingMode.get()) {
                            clientPool.execute(() -> handlePairingClient(clientSocket));
                        } else {
                            clientPool.execute(() -> handleClient(clientSocket));
                        }
                    } catch (RejectedExecutionException e) {
                        throwException(ExceptionServerMessages.CLIENT_REJECTED_MAXIMUM_CLIENT_CAPACITY_REACHED);
                        try { clientSocket.close(); } catch (IOException ignored) {}
                    }
                } catch (IOException e) {
                    if (running) {
                        throwException(ExceptionServerMessages.NETWORK_EXCEPTION);
                    }
                    break;
                }
            }
        },"TCP-Server-Thread");
        serverThread.start();
    }

    public void stopListening() {
        if (running) {
            running = false;
            clientPool.shutdownNow();
            try {clientPool.awaitTermination(5, TimeUnit.SECONDS);} catch (InterruptedException ignored) {}
            try {
                serverSocket.close();
                for (Socket socket : clients) {
                    try {socket.close();} catch (IOException ignored) {}
                }
            } catch (IOException ignored) {}
        }
    }

    private void handleClient(Socket socket) {
        try(
             Socket clientSocket = socket;
             BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter clientOut = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)
        ){
            clientSocket.setSoTimeout(120_000);

            String line = clientIn.readLine();
            if(line == null) return;

            line = AesGcmEncryptor.decrypt(line, encryptionKey);


            if (!ServerFunc.validateToken(line, token)) {
                clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.AUTH_FAIL.toString(), encryptionKey));
                return;
            }

            clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.AUTH_OK.toString(), encryptionKey));

            String cmd;
            String response;
            while ((cmd = clientIn.readLine()) != null) {
                try {
                    cmd = AesGcmEncryptor.decrypt(cmd, encryptionKey);
                    response = ServerFunc.handleCommand(cmd);
                    clientOut.println(AesGcmEncryptor.encrypt(response, encryptionKey));
                }catch (EncryptionException e) {
                    clientOut.println(ResponseMessages.ENCRYPTION_EXCEPTION);
                    throwException(ExceptionServerMessages.ENCRYPTION_EXCEPTION_WHILE_HANDLING_CMD);
                    break;
                }catch (HandleCommandException e) {
                    clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.HANDLE_COMMAND_EXCEPTION.toString(), encryptionKey));
                    throwException(ExceptionServerMessages.HANDLE_COMMAND_EXCEPTION);
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
            clientSocket.setSoTimeout(120_000);
            String line = clientIn.readLine();

            if(line == null) return;
            try {
                line = AesGcmEncryptor.decrypt(line, encryptionKey);
            }catch (EncryptionException e) {
                clientOut.println(ResponseMessages.WRONG_ENCRYPTION_WORDS_CAN_NOT_DECRYPT);
                return;
            }
            if(!line.trim().equals(idOfDesktop)) {
                clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.WRONG_UID.toString(), encryptionKey));
                if (serverListener != null) {
                    serverListener.onPairing(false);
                }
                return;
            }

            clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.OK.toString(), encryptionKey));

            line = clientIn.readLine();
            if(line == null) return;
            line = AesGcmEncryptor.decrypt(line, encryptionKey);
            if (!line.trim().equals(token)) {
                clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.WRONG_TOKEN.toString(), encryptionKey));
                if (serverListener != null) {
                    serverListener.onPairing(false);
                }
                return;
            }

            clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.OK.toString(), encryptionKey));


            clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.SUCCESS_PAIRING.toString(), encryptionKey));
            if (serverListener != null) {
                serverListener.onPairing(true);
            }
            pairingMode.set(false);
        }catch (IOException e) {
            if (running) {
                serverListener.onPairing(false);
                throwException(ExceptionServerMessages.NETWORK_EXCEPTION);
            }
        }catch (EncryptionException e) {
            throwException(ExceptionServerMessages.ENCRYPTION_EXCEPTION);
        }finally {
            clients.remove(socket);
        }
    }
    public void setServerListener(TCPServerListener listener) {
        this.serverListener = listener;
    }
    public InetAddress getInetAddress() {
        return inetAddress;
    }
    public int getPort() {
        return serverSocket.getLocalPort();
    }
    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }
    public void setToken(String token) {
        this.token = token;
    }
    public void setUId(String uId) {
        this.idOfDesktop = uId;
    }
    private void throwException(ExceptionServerMessages message){if (serverListener != null) serverListener.onException(message.toString());}
}
