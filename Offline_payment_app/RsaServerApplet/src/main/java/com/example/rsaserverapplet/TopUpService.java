package com.example.rsaserverapplet;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TopUpService {

    private static final Logger log = LoggerFactory.getLogger(TopUpService.class);

    // Demo approval policy: for now, every signed request with a valid
    // signature goes to manual review — nothing auto-credits. Approve/reject
    // from the admin dashboard at /admin.html. Set this back above 0 later
    // if you want small amounts to auto-approve again.
    private static final long AUTO_APPROVE_LIMIT = 0L;

    private final ReconciliationService reconciliationService;
    private final Map<String, TopUpRecord> requests = new ConcurrentHashMap<>();

    public TopUpService(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    public TopUpRecord submit(TopUpRequest req) throws Exception {
        log.info("Top-up request received: amount={}, timestamp={}, publicKeyLen={}, signatureLen={}",
                req.amount, req.timestamp,
                req.publicKey == null ? -1 : req.publicKey.length,
                req.signature == null ? -1 : req.signature.length);

        TopUpRecord record = new TopUpRecord();
        record.requestId = UUID.randomUUID().toString();
        record.publicKeyB64 = Base64.getEncoder().encodeToString(req.publicKey);
        record.amount = req.amount;
        record.timestamp = req.timestamp;

        boolean sigValid;
        try {
            sigValid = verifySignature(req);
        } catch (Exception e) {
            // A malformed key/signature throws instead of just returning false —
            // without this catch, submit() propagates the exception and the
            // caller (Spring) turns it into a 500 with no TopUpRecord returned
            // at all, which looks exactly like "the server never got the request."
            log.warn("Signature verification threw an exception — treating as invalid", e);
            sigValid = false;
        }

        if (!sigValid) {
            record.status = TopUpRecord.Status.REJECTED;
            record.reason = "Invalid signature";
            log.info("Rejected top-up {}: invalid signature", record.requestId);
        } else if (req.amount <= 0) {
            record.status = TopUpRecord.Status.REJECTED;
            record.reason = "Amount must be positive";
            log.info("Rejected top-up {}: non-positive amount", record.requestId);
        } else if (req.amount <= AUTO_APPROVE_LIMIT) {
            reconciliationService.creditTopUp(req.publicKey, req.amount);
            record.status = TopUpRecord.Status.APPROVED;
            record.reason = "Auto-approved (within limit)";
            log.info("Auto-approved top-up {}", record.requestId);
        } else {
            record.status = TopUpRecord.Status.PENDING;
            record.reason = "Exceeds auto-approve limit, awaiting manual review";
            log.info("Queued top-up {} as PENDING", record.requestId);
        }

        requests.put(record.requestId, record);
        return record;
    }

    public TopUpRecord get(String requestId) {
        return requests.get(requestId);
    }

    public java.util.List<TopUpRecord> listPending() {
        java.util.List<TopUpRecord> pending = new java.util.ArrayList<>();
        for (TopUpRecord r : requests.values()) {
            if (r.status == TopUpRecord.Status.PENDING) pending.add(r);
        }
        pending.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        return pending;
    }

    public java.util.List<TopUpRecord> listAll() {
        java.util.List<TopUpRecord> all = new java.util.ArrayList<>(requests.values());
        all.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        return all;
    }

    public TopUpRecord approve(String requestId) throws Exception {
        TopUpRecord record = requests.get(requestId);
        if (record == null) return null;
        if (record.status == TopUpRecord.Status.PENDING) {
            byte[] publicKey = Base64.getDecoder().decode(record.publicKeyB64);
            reconciliationService.creditTopUp(publicKey, record.amount);
            record.status = TopUpRecord.Status.APPROVED;
            record.reason = "Manually approved";
            log.info("Manually approved top-up {}", requestId);
        }
        return record;
    }

    public TopUpRecord reject(String requestId, String reason) {
        TopUpRecord record = requests.get(requestId);
        if (record == null) return null;
        if (record.status == TopUpRecord.Status.PENDING) {
            record.status = TopUpRecord.Status.REJECTED;
            record.reason = (reason == null || reason.isBlank()) ? "Manually rejected" : reason;
            log.info("Manually rejected top-up {}: {}", requestId, record.reason);
        }
        return record;
    }

    private boolean verifySignature(TopUpRequest req) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(req.publicKey));

        ByteBuffer buf = ByteBuffer.allocate(req.publicKey.length + 8 + 8);
        buf.put(req.publicKey);
        buf.putLong(req.amount);
        buf.putLong(req.timestamp);

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(buf.array());
        return verifier.verify(req.signature);
    }
}