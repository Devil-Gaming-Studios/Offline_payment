package com.example.rsaserverapplet;

// What the wallet sends to request a top-up. signature is over
// (publicKey bytes + amount as 8-byte long + timestamp as 8-byte long),
// signed with the wallet's own private key — proves the request actually
// came from the holder of that wallet, not someone spoofing the publicKey.
public class TopUpRequest {
    public byte[] publicKey;
    public long amount;
    public long timestamp;
    public byte[] signature;

    public TopUpRequest() {} // needed for JSON deserialization
}
