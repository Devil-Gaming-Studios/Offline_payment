package com.example.rsaserverapplet;

public class TopUpRecord {
    public enum Status { PENDING, APPROVED, REJECTED }

    public String requestId;
    public String publicKeyB64;
    public long amount;
    public long timestamp;
    public Status status;
    public String reason;
}
