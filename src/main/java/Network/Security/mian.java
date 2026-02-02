package Network.Security;

import java.io.IOException;

public class mian {
    public static void main(String[] args) throws Exception {

        String username = "lalaHIHI asddad asdasdasd";
        System.out.println(username);
        String password = "horsenigler94";
        String enc = AesGcmEncryptor.encrypt(username,password);
        System.out.println(enc);
        System.out.println(AesGcmEncryptor.decrypt(enc,password));

        System.out.println(enc.length());
    }
}
