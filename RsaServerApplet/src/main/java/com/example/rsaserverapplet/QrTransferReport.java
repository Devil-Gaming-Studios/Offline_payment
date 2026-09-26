package com.example.rsaserverapplet;

// What a phone posts to the server once it's back online, to report a
// two-way QR payment that was completed fully offline. Both signatures are
// included so the server can independently verify the payment actually
// happened as claimed — it does not just trust the phone's word for it.
public class QrTransferReport {
    public byte[] senderPublicKey;
    public byte[] receiverPublicKey;
    public long amountPaise;
    public long timestamp;
    public String nonce;
    public byte[] senderSignature;   // over (senderPublicKey + amountPaise + timestamp + nonce)
    public byte[] receiverSignature; // over (receiverPublicKey + nonce)

    public QrTransferReport() {} // needed for JSON deserialization
}
