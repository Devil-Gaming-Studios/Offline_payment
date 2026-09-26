package com.example.offlinewallet;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.StrongBoxUnavailableException;

import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.ECGenParameterSpec;

// Separate from the wallet's identity key (Transaction signing key).
// This key's ONLY job is to sign (seqNo, prevHash) so that editing the
// plain ledger file directly can't produce a valid signature without
// access to hardware the attacker doesn't control.
//
// HONEST LIMITATION: this stops "edit the file with a text editor / script"
// tampering. It does NOT by itself stop "replay an old, validly-signed
// state" — true rollback resistance needs a hardware monotonic counter,
// which isn't a universal cross-device Android API.
public class HardwareLedgerGuard {

    private static final String KEY_ALIAS = "wallet_counter_guard_key";
    private static final String KEYSTORE = "AndroidKeyStore";

    public static class HardwareBackingResult {
        public final boolean isHardwareBacked;
        public final boolean isStrongBox;
        public HardwareBackingResult(boolean isHardwareBacked, boolean isStrongBox) {
            this.isHardwareBacked = isHardwareBacked;
            this.isStrongBox = isStrongBox;
        }
    }

    // Call once at wallet setup. Tries StrongBox first (physically separate
    // secure chip), falls back to TEE if StrongBox isn't available.
    public static HardwareBackingResult generateGuardKeyIfNeeded() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);

        if (!ks.containsAlias(KEY_ALIAS)) {
            boolean usedStrongBox = tryGenerate(true);
            if (!usedStrongBox) {
                tryGenerate(false); // fall back to TEE-only
            }
        }
        return checkBacking();
    }

    private static boolean tryGenerate(boolean requireStrongBox) throws Exception {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, KEYSTORE);

            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setIsStrongBoxBacked(requireStrongBox);

            kpg.initialize(builder.build());
            kpg.generateKeyPair();
            return requireStrongBox;
        } catch (StrongBoxUnavailableException e) {
            return false;
        }
    }

    // Checks where the key ACTUALLY landed — never trust the request alone.
    public static HardwareBackingResult checkBacking() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        PrivateKey key = (PrivateKey) ks.getKey(KEY_ALIAS, null);

        KeyFactory factory = KeyFactory.getInstance(key.getAlgorithm(), KEYSTORE);
        KeyInfo keyInfo = factory.getKeySpec(key, KeyInfo.class);

        boolean hardwareBacked = keyInfo.isInsideSecureHardware();
        boolean strongBox;
        try {
            strongBox = keyInfo.getSecurityLevel() == KeyProperties.SECURITY_LEVEL_STRONGBOX;
        } catch (NoSuchMethodError e) {
            strongBox = false; // older API level without this field
        }
        return new HardwareBackingResult(hardwareBacked, strongBox);
    }

    // Signs the ledger's current (seqNo, prevHash) pair with the hardware key.
    public static byte[] signState(long seqNo, byte[] prevHash) throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        PrivateKey key = (PrivateKey) ks.getKey(KEY_ALIAS, null);

        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(key);
        signer.update(buildStatePayload(seqNo, prevHash));
        return signer.sign();
    }

    // Verifies a loaded (seqNo, prevHash, signature) triple came from THIS
    // device's hardware key — not from a text-edited file.
    public static boolean verifyState(long seqNo, byte[] prevHash, byte[] signature) throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        PublicKey pub = ks.getCertificate(KEY_ALIAS).getPublicKey();

        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(pub);
        verifier.update(buildStatePayload(seqNo, prevHash));
        return verifier.verify(signature);
    }

    private static byte[] buildStatePayload(long seqNo, byte[] prevHash) {
        ByteBuffer buf = ByteBuffer.allocate(8 + prevHash.length);
        buf.putLong(seqNo);
        buf.put(prevHash);
        return buf.array();
    }
}
