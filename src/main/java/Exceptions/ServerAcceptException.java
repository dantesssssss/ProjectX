package Exceptions;

public class ServerAcceptException extends RuntimeException {
    public ServerAcceptException(String message) {
        super(message);
    }
    public ServerAcceptException() {}
}
