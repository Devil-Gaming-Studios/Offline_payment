package com.example.offlinewallet;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

// Two-way QR payment protocol, as an alternative to BLE:
//   1. Payer generates a signed TransactionQr and displays it.
//   2. Payee scans it, verifies the payer's signature, credits their balance,
//      then generates a signed AckQr (binding the same nonce) and displays it.
//   3. Payer scans the AckQr, verifies it came from the payee and matches the
//      nonce they sent, and only then debits their own balance.
// The nonce stops a payee from replaying the same transaction QR twice.
public class QrPaymentUtil {

    public static class TransactionData {
        public byte[] senderPublicKey;
        public long amountPaise;
        public long timestamp;
        public String nonce;
        public byte[] signature;
    }

    // ---------- Step 1: payer builds the transaction QR ----------

    public static String buildTransactionQr(KeyPair senderKeys, long amountPaise) throws Exception {
        byte[] pubKeyBytes = senderKeys.getPublic().getEncoded();
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();

        byte[] payload = transactionSignedBytes(pubKeyBytes, amountPaise, timestamp, nonce);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(senderKeys.getPrivate());
        signer.update(payload);
        byte[] signature = signer.sign();

        JSONObject obj = new JSONObject();
        obj.put("type", "tx");
        obj.put("senderPublicKey", Base64.getEncoder().encodeToString(pubKeyBytes));
        obj.put("amountPaise", amountPaise);
        obj.put("timestamp", timestamp);
        obj.put("nonce", nonce);
        obj.put("signature", Base64.getEncoder().encodeToString(signature));
        return obj.toString();
    }

    // ---------- Step 2: payee parses + verifies the scanned transaction QR ----------

    public static TransactionData parseAndVerifyTransaction(String scannedJson) throws Exception {
        JSONObject obj = new JSONObject(scannedJson);
        if (!"tx".equals(obj.optString("type"))) {
            throw new IllegalArgumentException("Not a payment QR code");
        }

        TransactionData tx = new TransactionData();
        tx.senderPublicKey = Base64.getDecoder().decode(obj.getString("senderPublicKey"));
        tx.amountPaise = obj.getLong("amountPaise");
        tx.timestamp = obj.getLong("timestamp");
        tx.nonce = obj.getString("nonce");
        tx.signature = Base64.getDecoder().decode(obj.getString("signature"));

        if (tx.amountPaise <= 0) {
            throw new IllegalArgumentException("Invalid amount in payment QR");
        }

        byte[] payload = transactionSignedBytes(tx.senderPublicKey, tx.amountPaise, tx.timestamp, tx.nonce);

        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PublicKey senderKey = kf.generatePublic(new X509EncodedKeySpec(tx.senderPublicKey));

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(senderKey);
        verifier.update(payload);
        if (!verifier.verify(tx.signature)) {
            throw new SecurityException("Signature on payment QR does not verify");
        }

        return tx;
    }

    // ---------- Step 2b: payee builds the ack QR after crediting ----------

    public static String buildAckQr(KeyPair receiverKeys, TransactionData tx) throws Exception {
        byte[] receiverPubKeyBytes = receiverKeys.getPublic().getEncoded();
        byte[] payload = ackSignedBytes(receiverPubKeyBytes, tx.nonce);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(receiverKeys.getPrivate());
        signer.update(payload);
        byte[] signature = signer.sign();

        JSONObject obj = new JSONObject();
        obj.put("type", "ack");
        obj.put("receiverPublicKey", Base64.getEncoder().encodeToString(receiverPubKeyBytes));
        obj.put("nonce", tx.nonce);
        obj.put("signature", Base64.getEncoder().encodeToString(signature));
        return obj.toString();
    }

    // ---------- Step 3: payer parses + verifies the scanned ack QR ----------
    // expectedNonce ties this ack back to the specific transaction just sent —
    // without this check, a payer could scan an unrelated old ack and wrongly
    // conclude an unrelated payment succeeded.

    public static byte[] parseAndVerifyAck(String scannedJson, String expectedNonce) throws Exception {
        JSONObject obj = new JSONObject(scannedJson);
        if (!"ack".equals(obj.optString("type"))) {
            throw new IllegalArgumentException("Not a payment confirmation QR code");
        }

        byte[] receiverPublicKey = Base64.getDecoder().decode(obj.getString("receiverPublicKey"));
        String nonce = obj.getString("nonce");
        byte[] signature = Base64.getDecoder().decode(obj.getString("signature"));

        if (!nonce.equals(expectedNonce)) {
            throw new SecurityException("Confirmation QR doesn't match this transaction");
        }

        byte[] payload = ackSignedBytes(receiverPublicKey, nonce);

        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PublicKey receiverKey = kf.generatePublic(new X509EncodedKeySpec(receiverPublicKey));

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(receiverKey);
        verifier.update(payload);
        if (!verifier.verify(signature)) {
            throw new SecurityException("Signature on confirmation QR does not verify");
        }

        return receiverPublicKey;
    }

    private static byte[] transactionSignedBytes(byte[] senderPublicKey, long amountPaise,
                                                  long timestamp, String nonce) {
        byte[] nonceBytes = nonce.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(senderPublicKey.length + 8 + 8 + nonceBytes.length);
        buf.put(senderPublicKey);
        buf.putLong(amountPaise);
        buf.putLong(timestamp);
        buf.put(nonceBytes);
        return buf.array();
    }

    private static byte[] ackSignedBytes(byte[] receiverPublicKey, String nonce) {
        byte[] nonceBytes = nonce.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(receiverPublicKey.length + nonceBytes.length);
        buf.put(receiverPublicKey);
        buf.put(nonceBytes);
        return buf.array();
    }
}
