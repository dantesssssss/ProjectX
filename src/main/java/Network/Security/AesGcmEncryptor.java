package Network.Security;
import Exceptions.EncryptionException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class AesGcmEncryptor {

    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final int ITERATIONS = 100_000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AesGcmEncryptor() {}

    /** Encodes String -> Base64 for QR */
    public static String encrypt(String text,String strPassword) throws EncryptionException {
        char[] password = strPassword.toCharArray();
        byte[] salt = randomBytes(SALT_LENGTH);
        byte[] iv = randomBytes(IV_LENGTH);

        SecretKey key = deriveKey(password, salt);
        byte[] cipherText = encryptBytes(text.getBytes(StandardCharsets.UTF_8),key,iv);
        return packAndEncode(salt, iv, cipherText);
    }

    /** decrypts Base64 from QR -> String */
    public static String decrypt(String base64,String strPassword) throws EncryptionException {

        char[] password = strPassword.toCharArray();
        byte[] all = Base64.getDecoder().decode(base64);

        byte[] salt = Arrays.copyOfRange(all, 0, SALT_LENGTH);
        byte[] iv   = Arrays.copyOfRange(all, SALT_LENGTH, SALT_LENGTH + IV_LENGTH);
        byte[] data = Arrays.copyOfRange(all, SALT_LENGTH + IV_LENGTH, all.length);

        SecretKey key = deriveKey(password, salt);
        byte[] plain = decryptBytes(data, key, iv);

        return new String(plain, StandardCharsets.UTF_8);
    }

    private static SecretKey deriveKey(char[] password, byte[] salt) throws EncryptionException {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        }catch (Exception e){
            throw new EncryptionException(e.getMessage());
        }
    }

    private static byte[] encryptBytes(byte[] plain, SecretKey key, byte[] iv) throws EncryptionException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);

            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            return cipher.doFinal(plain);
        }catch (Exception e){
            throw new EncryptionException(e.getMessage());
        }
    }

    private static byte[] decryptBytes(byte[] cipherText, SecretKey key, byte[] iv) throws EncryptionException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);

            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            return cipher.doFinal(cipherText);
        }catch (Exception e){
            throw new EncryptionException(e.getMessage());
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String packAndEncode(byte[] salt,byte[] iv,byte[] cipherText) throws EncryptionException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(salt);
            out.write(iv);
            out.write(cipherText);

            return Base64.getEncoder().encodeToString(out.toByteArray());
        }catch (Exception e){
            throw new EncryptionException(e.getMessage());
        }
    }
}