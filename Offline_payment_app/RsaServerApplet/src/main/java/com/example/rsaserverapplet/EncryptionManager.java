package com.example.rsaserverapplet;

//import org.springframework.cglib.core.KeyFactory;

import javax.crypto.Cipher;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class EncryptionManager {
    private static final String PUBLIC_KEY_STRING = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCSoK+mQcka+FIWzFXZ5mowxZd+kKDpjo5+zYUryH3tuggk3iqJ1YxrZUVEd9sMIA3fRlNIcC3ZvQrbPZLCjborH8SIGB8Nb22aUMz9wmSPn9+Ve0uQGsu2PudF7Ac3Bhu+Au1Mu9dycRzIodmJGHgFdHOSdXGgOybhmehWBr+nyQIDAQAB";
    private PublicKey publicKey;
    public void initFromStrings()
    {
        try{
            X509EncodedKeySpec keySpecPublic = new X509EncodedKeySpec(decode(PUBLIC_KEY_STRING));
            //PKCS8EncodedKeySpec keySpecPrivate = new PKCS8EncodedKeySpec(decode(PRIVATE_KEY_STRING));

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            publicKey = keyFactory.generatePublic(keySpecPublic);
            //privateKey = keyFactory.generatePrivate(keySpecPrivate);
        }
        catch (Exception e)
        {}
    }
    public byte[] decode(String encryptedMessage)
    {
        return Base64.getDecoder().decode(encryptedMessage);
    }
    public String encode(byte[] MessageInByte)
    {
        return Base64.getEncoder().encodeToString(MessageInByte);
    }
    public String encrypt(String message) throws Exception
    {
        byte[] messageInBytes = message.getBytes();
        Cipher encryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        encryptCipher.init(Cipher.ENCRYPT_MODE,publicKey);
        byte[] encriptedBytes = encryptCipher.doFinal(messageInBytes);
        return encode(encriptedBytes);
    }
}
