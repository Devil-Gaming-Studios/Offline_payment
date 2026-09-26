package com.example.rsaserverapplet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class IdentityKeyManager {

    private static final Path PRIVATE_KEY_FILE = Path.of("wallet_private.key");
    private static final Path PUBLIC_KEY_FILE  = Path.of("wallet_public.key");

    private KeyPair keys;

    private static KeyPair generateKeypair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed25519");
        return keyGen.generateKeyPair();
    }

    // saves a freshly generated keypair to disk so fetchKeys() can load it next time
    private static void saveKeys(KeyPair keyPair) throws Exception {
        Files.write(PRIVATE_KEY_FILE, keyPair.getPrivate().getEncoded());
        Files.write(PUBLIC_KEY_FILE, keyPair.getPublic().getEncoded());
    }

    private KeyPair fetchKeys() throws Exception {
        byte[] privBytes = Files.readAllBytes(PRIVATE_KEY_FILE);
        byte[] pubBytes  = Files.readAllBytes(PUBLIC_KEY_FILE);

        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
        PublicKey publicKey  = kf.generatePublic(new X509EncodedKeySpec(pubBytes));

        return new KeyPair(publicKey, privateKey);
    }

    KeyPair getKeys() {
        try {
            if (Files.exists(PRIVATE_KEY_FILE) && Files.exists(PUBLIC_KEY_FILE)) {
                this.keys = fetchKeys();
            } else {
                this.keys = generateKeypair();
                saveKeys(this.keys);
            }
        } catch (Exception e) {
            System.err.println("your key was not able to be generated or loaded");
            //e.printStackTrace();
        }
        return keys;
    }
}