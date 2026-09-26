package com.example.rsaserverapplet;

import java.util.HashMap;
import java.util.Map;

public class WalletLedgerState {
    public long lastSeqNo;
    public byte[] lastHash;
    public long balance;
    // remembers the hash accepted at each seqNo, so a resubmission with
    // DIFFERENT content at an already-accepted seqNo can be caught as fraud
    public Map<Long, byte[]> acceptedHashesBySeq = new HashMap<>();

    public WalletLedgerState(long lastSeqNo, byte[] lastHash, long balance) {
        this.lastSeqNo = lastSeqNo;
        this.lastHash = lastHash;
        this.balance = balance;
    }
}
