package Network;

import Exceptions.EncryptionException;
import Exceptions.ServerAcceptException;

public interface ServerErrorListener {
    void onAcceptError(ServerAcceptException e);
    void onClientRejected(String reason);
    void onEncryptionError(EncryptionException e);
}
