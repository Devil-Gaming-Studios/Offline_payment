package com.example.rsaserverapplet;

import java.util.Base64;

public class QrTransferRecord {
    public String nonce;
    public String senderKeyB64;
    public String receiverKeyB64;
    public long amountPaise;
    public long timestamp;      // when the transaction was originally signed on the sender's phone
    public long recordedAt;     // when the server actually received/recorded this report

    public QrTransferRecord() {}

    public QrTransferRecord(byte[] senderKey, byte[] receiverKey, long amountPaise, long timestamp, String nonce) {
        this.senderKeyB64 = Base64.getEncoder().encodeToString(senderKey);
        this.receiverKeyB64 = Base64.getEncoder().encodeToString(receiverKey);
        this.amountPaise = amountPaise;
        this.timestamp = timestamp;
        this.nonce = nonce;
        this.recordedAt = System.currentTimeMillis();
    }
}
