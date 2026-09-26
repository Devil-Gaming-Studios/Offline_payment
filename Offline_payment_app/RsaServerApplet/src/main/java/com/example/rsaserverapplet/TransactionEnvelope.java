package com.example.rsaserverapplet;

// What a wallet actually sends over the wire: the transaction plus the
// sender's signature over it. This is what gets verified on arrival.
public class TransactionEnvelope {
    public Transaction transaction;
    public byte[] signature;

    public TransactionEnvelope() {} // needed for JSON deserialization

    public TransactionEnvelope(Transaction transaction, byte[] signature) {
        this.transaction = transaction;
        this.signature = signature;
    }
}
