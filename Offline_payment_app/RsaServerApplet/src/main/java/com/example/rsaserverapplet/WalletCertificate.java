package com.example.rsaserverapplet;

public class WalletCertificate {
    public String userId;
    public byte[] walletPublicKey;
    public byte[] certSignature;

    public WalletCertificate() {} // needed for JSON deserialization

    public WalletCertificate(String userId, byte[] walletPublicKey, byte[] certSignature) {
        this.userId = userId;
        this.walletPublicKey = walletPublicKey;
        this.certSignature = certSignature;
    }
}
