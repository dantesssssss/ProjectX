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

public class TCPServer {
    private final ServerSocket tcpServerSocket;
    private final InetAddress inetAddress;
    private final String TOKEN;
    private final String ID;
    private volatile boolean running = true;
    private volatile String pass;
    private ExecutorService clientPool;
    private volatile TCPServerListener TCPServerListener;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private Thread serverThread;
    private volatile boolean pairingMode;


    public TCPServer(String ID,String TOKEN, String pass, boolean pairingMode) throws IOException {
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
            TCPServerListener.onException(ExceptionServerMessages.LISTENING_ALREADY_STARTED.toString());
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
                        TCPServerListener.onException(ExceptionServerMessages.NETWORK_EXCEPTION.toString());
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
            if(isNull(line)) return;

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
                    throwException(ExceptionServerMessages.ENCRYPTION_EXCEPTION_WHILE_HANDLING_CMD);
                    break;
                }catch (HandleCommandException e) {
                    clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.HANDLE_COMMAND_EXCEPTION.toString(),pass));
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
            clients.add(clientSocket);
            clientSocket.setSoTimeout(120_000);
            String line = clientIn.readLine();

            if(isNull(line)) return;
            try {
                line = AesGcmEncryptor.decrypt(line, pass);
            }catch (EncryptionException e) {
                clientOut.println(ResponseMessages.WRONG_ENCRYPTION_WORDS_CAN_NOT_DECRYPT);
                return;
            }
            if(!line.trim().equals(ID)) {
                clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.WRONG_UID.toString(),pass));
                if (TCPServerListener != null) {
                    TCPServerListener.onPairing(false);
                }
                return;
            }

            clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.OK.toString(),pass));

            line = clientIn.readLine();
            if(isNull(line)) return;
            line = AesGcmEncryptor.decrypt(line, pass);
            if (!line.trim().equals(TOKEN)) {
                clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.WRONG_TOKEN.toString(),pass));
                if (TCPServerListener != null) {
                    TCPServerListener.onPairing(false);
                }
                return;
            }

            clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.OK.toString(),pass));


            clientOut.println(AesGcmEncryptor.encrypt(ResponseMessages.SUCCESS_PAIRING.toString(),pass));
            if (TCPServerListener != null) {
                TCPServerListener.onPairing(true);
            }
            clients.remove(clientSocket);
            switchToNormalMode();
        }catch (IOException e) {
            if (running) {
                TCPServerListener.onPairing(false);
                throwException(ExceptionServerMessages.NETWORK_EXCEPTION);
            }
        }catch (EncryptionException e) {
            throwException(ExceptionServerMessages.ENCRYPTION_EXCEPTION);
        }
    }

    public void setTCPServerListener(TCPServerListener listener) {
        this.TCPServerListener = listener;
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
        if (TCPServerListener != null) {
            TCPServerListener.onException(message.toString());
        }
    }
    private boolean isNull(String str){
        return str == null;
    }
}
