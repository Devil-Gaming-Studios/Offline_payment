package com.example.rsaserverapplet;

public class QrTransferResult {
    public enum Status { RECORDED, DUPLICATE_IGNORED, INVALID_SENDER_SIGNATURE, INVALID_RECEIVER_SIGNATURE, INVALID_AMOUNT }

    public Status status;
    public String nonce;
    public String message;

    public QrTransferResult() {}

    public QrTransferResult(Status status, String nonce, String message) {
        this.status = status;
        this.nonce = nonce;
        this.message = message;
    }
}
