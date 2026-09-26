package com.example.rsaserverapplet;

import okhttp3.OkHttpClient;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

import javax.crypto.Cipher;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class RSAClient {


    private int KEY_SIZE = 1024;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    //private static final String PUBLIC_KEY_STRING = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCSoK+mQcka+FIWzFXZ5mowxZd+kKDpjo5+zYUryH3tuggk3iqJ1YxrZUVEd9sMIA3fRlNIcC3ZvQrbPZLCjborH8SIGB8Nb22aUMz9wmSPn9+Ve0uQGsu2PudF7Ac3Bhu+Au1Mu9dycRzIodmJGHgFdHOSdXGgOybhmehWBr+nyQIDAQAB";
    private static final String PRIVATE_KEY_STRING = "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAJKgr6ZByRr4UhbMVdnmajDFl36QoOmOjn7NhSvIfe26CCTeKonVjGtlRUR32wwgDd9GU0hwLdm9Cts9ksKNuisfxIgYHw1vbZpQzP3CZI+f35V7S5Aay7Y+50XsBzcGG74C7Uy713JxHMih2YkYeAV0c5J1caA7JuGZ6FYGv6fJAgMBAAECgYAw40iNW2PNDTx5g3mgZFXFmyvutBQiqzhRiP684WUzTRKIEFcIi938tNBKitfLDCjz58O+5EA1V/V1serr2UtXsEeCacnThcn3OFOWV56WVpVJ+p9SMzPSMQcdJgZEp1+7ZOYIB2VYmtC3HwVUBglZF69QP02oFlCb3t9AfU8NHQJBAKIaYSUdEi5EBMF0HoVftKwW6Noo4pomT0Ac6EMKTVyP+35Rg5159aaMvvzRGoTvw+BV4bL74FZ1XQdvaUZRaYcCQQDnj4ajwoaJEVgkjIrNAYFNOdptXDc27jv6GcLb2kjtSxqB37DTslIn6ODvhyMHdHGB1a++rUoImwuCEaWGengvAkA6twdc5AzDyUtXrvGnKaVNd/bbnleFsj6eYFoYflDLKDPV6zya+6Posa4z8KGEaTwvs6vOosD9UAFkQgyFtdNdAkEAtADFOA2iSXC7JQY/a6eM3PxpCHQT09aTtxJJgGAKKrQkMesyaQ4IgU+tc2WIXGYvSi9TQ5UvpMrpwj13f7c6LwJADr6FEroqKDdNyIMNjkjXq3+xYW9OH9VeXAlDnzBalRvj1cSns8WcACusC1NynErTU2IlIix+wPPrtC0Zuk2hLg==";
    public void init()
    {
        try{
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE);
            KeyPair pair = generator.generateKeyPair();
            privateKey = pair.getPrivate();
            publicKey = pair.getPublic();
        }catch(Exception e)
        {
            System.err.println(e);
        }
    }

    public void initFromStrings()
    {
        try{
            //X509EncodedKeySpec keySpecPublic = new X509EncodedKeySpec(decode(PUBLIC_KEY_STRING));
            PKCS8EncodedKeySpec keySpecPrivate = new PKCS8EncodedKeySpec(decode(PRIVATE_KEY_STRING));

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            //publicKey = keyFactory.generatePublic(keySpecPublic);
            privateKey = keyFactory.generatePrivate(keySpecPrivate);
        }
        catch (Exception e)
        {}
    }
    public String encrypt(String message) throws Exception
    {
        byte[] messageInBytes = message.getBytes();
        Cipher encryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        encryptCipher.init(Cipher.ENCRYPT_MODE,publicKey);
        byte[] encriptedBytes = encryptCipher.doFinal(messageInBytes);
        return encode(encriptedBytes);
    }

    public String decrypt(String encryptedMessage) throws Exception{
        byte[] encryptedBytes = decode(encryptedMessage);
        Cipher decryptedCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        decryptedCipher.init(Cipher.DECRYPT_MODE,privateKey);
        byte[] decryptedMessage = decryptedCipher.doFinal(encryptedBytes);
        return new String(decryptedMessage,"UTF8");
    }
    public String encode(byte[] MessageInByte)
    {
        return Base64.getEncoder().encodeToString(MessageInByte);
    }
    public byte[] decode(String encryptedMessage)
    {
        return Base64.getDecoder().decode(encryptedMessage);
    }
    private void getkey()
    {
        System.out.println(encode(publicKey.getEncoded()));
    }

    public static void main(String args[])
    {
        RSAClient rsa = new RSAClient();
        rsa.initFromStrings();

        try {
            OkHttpClient client = new OkHttpClient();
            HttpUrl url = HttpUrl.parse("http://localhost:8080/getSecretMessage")
                    .newBuilder()
                    .addQueryParameter("m", "Hello Server")
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            Response response = client.newCall(request).execute();

            System.out.println("RESPONSE BODY " + rsa.decrypt(response.body().string()) );
        }
        catch(Exception e)
        {}
    }
}

