package Network;

import Exceptions.ServerAcceptException;

public interface ServerErrorListener {
    void onAcceptError(ServerAcceptException e);
    void onClientRejected(String reason);
}
