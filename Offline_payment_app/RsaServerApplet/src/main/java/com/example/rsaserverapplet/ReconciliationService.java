package com.example.rsaserverapplet;

import org.springframework.stereotype.Service;

import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReconciliationService {

    // Stands in for the Master Ledger DB in the architecture diagram.
    // In a real build, swap this for a JPA repository backed by Postgres/MySQL.
    private final Map<String, WalletLedgerState> ledger = new ConcurrentHashMap<>();
    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();

    public enum Status { ACCEPTED, DUPLICATE_IGNORED, FRAUD_DETECTED, GAP_REJECTED, INVALID_SIGNATURE }

    public Status processTransaction(TransactionEnvelope envelope) throws Exception {
        Transaction tx = envelope.transaction;
        String senderKeyB64 = Base64.getEncoder().encodeToString(tx.senderPublicKey);

        if (blacklist.contains(senderKeyB64)) {
            return Status.FRAUD_DETECTED; // already flagged, refuse further processing
        }

        if (!verifySignature(tx, envelope.signature)) {
            return Status.INVALID_SIGNATURE;
        }

        WalletLedgerState state = ledger.computeIfAbsent(senderKeyB64,
                k -> new WalletLedgerState(0L, new byte[32], 0L)); // genesis: seqNo 0, zero hash

        if (tx.seqNo <= state.lastSeqNo) {
            // Sender is resubmitting an old seqNo. Could be a harmless retry,
            // or could be an attempt to slip in a DIFFERENT transaction under
            // a seqNo we've already accepted — that's the provable fork.
            byte[] expectedHash = state.acceptedHashesBySeq.get(tx.seqNo);
            byte[] thisHash = tx.hash();
            if (expectedHash != null && !Arrays.equals(expectedHash, thisHash)) {
                blacklist.add(senderKeyB64);
                return Status.FRAUD_DETECTED;
            }
            return Status.DUPLICATE_IGNORED;
        }

        if (tx.seqNo > state.lastSeqNo + 1) {
            // Missing an earlier transaction — don't accept out of order.
            return Status.GAP_REJECTED;
        }

        // tx.seqNo == state.lastSeqNo + 1, the expected next transaction
        if (!Arrays.equals(tx.prevTxHash, state.lastHash)) {
            // seqNo lines up but doesn't chain to what we last accepted — fork.
            blacklist.add(senderKeyB64);
            return Status.FRAUD_DETECTED;
        }

        byte[] newHash = tx.hash();
        state.acceptedHashesBySeq.put(tx.seqNo, newHash);
        state.lastSeqNo = tx.seqNo;
        state.lastHash = newHash;
        state.balance -= tx.amount;

        String receiverKeyB64 = Base64.getEncoder().encodeToString(tx.receiverPublicKey);
        WalletLedgerState receiverState = ledger.computeIfAbsent(receiverKeyB64,
                k -> new WalletLedgerState(0L, new byte[32], 0L));
        receiverState.balance += tx.amount;

        return Status.ACCEPTED;
    }

    private boolean verifySignature(Transaction tx, byte[] signature) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PublicKey senderPublicKey = kf.generatePublic(new X509EncodedKeySpec(tx.senderPublicKey));

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(senderPublicKey);
        verifier.update(tx.toBytes());
        return verifier.verify(signature);
    }

    public boolean isBlacklisted(byte[] publicKey) {
        return blacklist.contains(Base64.getEncoder().encodeToString(publicKey));
    }

    public Long getBalance(byte[] publicKey) {
        WalletLedgerState state = ledger.get(Base64.getEncoder().encodeToString(publicKey));
        return state == null ? 0L : state.balance;
    }

    // Only called after TopUpService has verified the signature and the
    // request has been approved (auto or manual). Does not touch seqNo/hash
    // chain — top-ups aren't peer-to-peer transactions, just a ledger credit.
    public void creditTopUp(byte[] publicKey, long amount) {
        String keyB64 = Base64.getEncoder().encodeToString(publicKey);
        WalletLedgerState state = ledger.computeIfAbsent(keyB64,
                k -> new WalletLedgerState(0L, new byte[32], 0L));
        state.balance += amount;
    }
}
