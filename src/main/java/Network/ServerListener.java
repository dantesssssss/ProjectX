package Network;

public interface ServerListener {
    void onException(String message);
    void onPairing(boolean success);
}
