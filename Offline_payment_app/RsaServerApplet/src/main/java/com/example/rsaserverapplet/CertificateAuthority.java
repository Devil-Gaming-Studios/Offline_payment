package com.example.rsaserverapplet;

import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.nio.charset.StandardCharsets;

// The server's own long-term identity. Its public key gets baked into
// every wallet app at build time, so every device can verify certificates
// this class issues, without ever contacting the server to check.
@Component
public class CertificateAuthority {

    private static final Path CA_PRIVATE_KEY_FILE = Path.of("ca_private.key");
    private static final Path CA_PUBLIC_KEY_FILE  = Path.of("ca_public.key");

    private KeyPair caKeys;

    @PostConstruct
    public void init() throws Exception {
        if (Files.exists(CA_PRIVATE_KEY_FILE) && Files.exists(CA_PUBLIC_KEY_FILE)) {
            byte[] privBytes = Files.readAllBytes(CA_PRIVATE_KEY_FILE);
            byte[] pubBytes  = Files.readAllBytes(CA_PUBLIC_KEY_FILE);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            PublicKey pub   = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
            caKeys = new KeyPair(pub, priv);
        } else {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            caKeys = kpg.generateKeyPair();
            Files.write(CA_PRIVATE_KEY_FILE, caKeys.getPrivate().getEncoded());
            Files.write(CA_PUBLIC_KEY_FILE, caKeys.getPublic().getEncoded());
        }
    }

    public PublicKey getCaPublicKey() {
        return caKeys.getPublic();
    }

    // Binds a userId to a wallet's public key, signed by the server.
    // This is what defeats the "attacker hands out their own key
    // claiming to be user A" attack discussed earlier.
    public WalletCertificate issueCertificate(String userId, byte[] walletPublicKey) throws Exception {
        byte[] payload = buildPayload(userId, walletPublicKey);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(caKeys.getPrivate());
        signer.update(payload);
        byte[] certSignature = signer.sign();

        return new WalletCertificate(userId, walletPublicKey, certSignature);
    }

    public boolean verifyCertificate(WalletCertificate cert) throws Exception {
        byte[] payload = buildPayload(cert.userId, cert.walletPublicKey);

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(caKeys.getPublic());
        verifier.update(payload);
        return verifier.verify(cert.certSignature);
    }

    private byte[] buildPayload(String userId, byte[] walletPublicKey) {
        byte[] userIdBytes = userId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(userIdBytes.length + walletPublicKey.length);
        buf.put(userIdBytes);
        buf.put(walletPublicKey);
        return buf.array();
    }
}
