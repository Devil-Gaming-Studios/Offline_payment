package com.example.rsaserverapplet;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

// Must match the client's Transaction.toBytes() layout EXACTLY —
// this is what makes a signature created on the client verifiable here.
public class Transaction {
    public byte[] senderPublicKey;
    public byte[] receiverPublicKey;
    public long amount;
    public long seqNo;
    public byte[] prevTxHash;
    public long timestamp;

    public Transaction() {} // needed for JSON deserialization

    public Transaction(byte[] senderPublicKey, byte[] receiverPublicKey,
                        long amount, long seqNo, byte[] prevTxHash, long timestamp) {
        this.senderPublicKey = senderPublicKey;
        this.receiverPublicKey = receiverPublicKey;
        this.amount = amount;
        this.seqNo = seqNo;
        this.prevTxHash = prevTxHash;
        this.timestamp = timestamp;
    }

    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(
                senderPublicKey.length + receiverPublicKey.length
                        + 8 + 8 + prevTxHash.length + 8);
        buf.put(senderPublicKey);
        buf.put(receiverPublicKey);
        buf.putLong(amount);
        buf.putLong(seqNo);
        buf.put(prevTxHash);
        buf.putLong(timestamp);
        return buf.array();
    }

    public byte[] hash() throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        return sha256.digest(toBytes());
    }
}
