package com.example.rsaserverapplet;

import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QrSyncService {

    private final ReconciliationService reconciliationService;

    // Keyed by nonce so a transaction reported more than once (e.g. by both
    // phones, or retried after a dropped connection) is only ever applied to
    // the ledger once — the second report just returns the record already on file.
    private final Map<String, QrTransferRecord> records = new ConcurrentHashMap<>();

    public QrSyncService(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    public QrTransferResult report(QrTransferReport r) {
        if (r.amountPaise <= 0) {
            return new QrTransferResult(QrTransferResult.Status.INVALID_AMOUNT, r.nonce, "Amount must be positive");
        }

        if (records.containsKey(r.nonce)) {
            return new QrTransferResult(QrTransferResult.Status.DUPLICATE_IGNORED, r.nonce,
                    "Already recorded — no change made");
        }

        try {
            if (!verifySenderSignature(r)) {
                return new QrTransferResult(QrTransferResult.Status.INVALID_SENDER_SIGNATURE, r.nonce,
                        "Sender's signature does not verify");
            }
            if (!verifyReceiverSignature(r)) {
                return new QrTransferResult(QrTransferResult.Status.INVALID_RECEIVER_SIGNATURE, r.nonce,
                        "Receiver's acknowledgment signature does not verify");
            }
        } catch (Exception e) {
            return new QrTransferResult(QrTransferResult.Status.INVALID_SENDER_SIGNATURE, r.nonce,
                    "Malformed key or signature: " + e.getMessage());
        }

        // Both signatures check out independently on the server. putIfAbsent
        // closes the same race window the old Set.add() check did — if two
        // requests for the same nonce land at once, only one gets RECORDED.
        QrTransferRecord record = new QrTransferRecord(r.senderPublicKey, r.receiverPublicKey,
                r.amountPaise, r.timestamp, r.nonce);
        if (records.putIfAbsent(r.nonce, record) != null) {
            return new QrTransferResult(QrTransferResult.Status.DUPLICATE_IGNORED, r.nonce,
                    "Already recorded — no change made");
        }

        reconciliationService.creditTopUp(r.receiverPublicKey, r.amountPaise);
        reconciliationService.creditTopUp(r.senderPublicKey, -r.amountPaise);

        return new QrTransferResult(QrTransferResult.Status.RECORDED, r.nonce,
                "Recorded on server ledger");
    }

    // Newest first — used by the admin dashboard's QR Transactions tab.
    public List<QrTransferRecord> listAll() {
        List<QrTransferRecord> all = new ArrayList<>(records.values());
        all.sort(Comparator.comparingLong((QrTransferRecord rec) -> rec.recordedAt).reversed());
        return all;
    }

    private boolean verifySenderSignature(QrTransferReport r) throws Exception {
        byte[] nonceBytes = r.nonce.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(r.senderPublicKey.length + 8 + 8 + nonceBytes.length);
        buf.put(r.senderPublicKey);
        buf.putLong(r.amountPaise);
        buf.putLong(r.timestamp);
        buf.put(nonceBytes);

        return verify(r.senderPublicKey, buf.array(), r.senderSignature);
    }

    private boolean verifyReceiverSignature(QrTransferReport r) throws Exception {
        byte[] nonceBytes = r.nonce.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(r.receiverPublicKey.length + nonceBytes.length);
        buf.put(r.receiverPublicKey);
        buf.put(nonceBytes);

        return verify(r.receiverPublicKey, buf.array(), r.receiverSignature);
    }

    private boolean verify(byte[] publicKeyBytes, byte[] payload, byte[] signature) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(payload);
        return verifier.verify(signature);
    }
}
