package Network;

public interface TCPServerListener {
    void onException(String message);
    void onPairing(boolean success);
}
