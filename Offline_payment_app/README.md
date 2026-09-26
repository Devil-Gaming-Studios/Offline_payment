# PS-07 Security Hardening — Setup & Integration

Four files, two on the wallet app side, two on the server side.

## Files

| File | Side | Purpose |
|---|---|---|
| `DeviceSafetyPrecheck.java` | Android | Instant, no-setup check: blocks if Developer Options or USB debugging is on |
| `HardwareLedgerGuard.java` | Android | Signs local `seqNo`/`prevHash` with a StrongBox/TEE-backed key so file edits can't forge state |
| `IntegrityChecker.java` | Android | Requests a Play Integrity token before allowing a send |
| `IntegrityVerificationController.java` | Server | Decodes the Play Integrity token — client cannot verify its own token |

Drop the Android files into `app/src/main/java/com/example/offlinewallet/`, and the server file into `src/main/java/com/example/rsaserverapplet/`.

## Gating order (call in this sequence before any offline send)

```java
private void startScanAndSend() {
    if (DeviceSafetyPrecheck.failsPrecheck(this)) {
        statusText.setText("Blocked: Developer Options or USB debugging is enabled.");
        return;
    }

    try {
        HardwareLedgerGuard.HardwareBackingResult backing =
                HardwareLedgerGuard.generateGuardKeyIfNeeded();

        if (!backing.isHardwareBacked) {
            statusText.setText("Blocked: no secure hardware (TEE/StrongBox) available.");
            return; // fail CLOSED
        }

        IntegrityChecker.requestIntegrityToken(this, new IntegrityChecker.OnTokenReceivedListener() {
            @Override
            public void onTokenReceived(String token, String nonce) {
                verifyIntegrityWithServerThenSend(token, nonce); // your existing send flow, gated
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> statusText.setText("Integrity check failed: " + e.getMessage()));
            }
        });

    } catch (Exception e) {
        statusText.setText("Security check failed: " + e.getMessage());
    }
}
```

## Setup required before this actually runs

1. **Play Console registration** — Play Integrity is tied to your app's Cloud Project Number. An arbitrary unsigned debug APK tested purely locally will not get a real verdict.
2. **Dependency**: `implementation("com.google.android.play:integrity:1.4.0")`
3. **Server-side OAuth** — the server needs a Google Cloud service account with the Play Integrity API enabled, and a way to mint an OAuth2 bearer token for it (e.g. `google-auth-library-oauth2-http`). Not included here — real plumbing, budget time for it.
4. **API level note** — `KeyInfo.getSecurityLevel()` (used to detect StrongBox specifically) needs API 31+; older devices only get the coarser `isInsideSecureHardware()` boolean.

## What's tested vs. what isn't (say this plainly if asked)

- `DeviceSafetyPrecheck` and `HardwareLedgerGuard` are plain Android Keystore usage — should work as written on any real device, no external setup needed.
- `IntegrityChecker` / `IntegrityVerificationController` need real Play Console + Cloud project setup to exercise end-to-end — this can't be verified without that registration in place.

## Honest limitations to state up front

- Hardware-backed keys stop key extraction and file-level `seqNo` tampering, but do **not** by themselves guarantee full rollback resistance — that needs a true hardware monotonic counter, which isn't a universal Android API.
- Play Integrity tells you the device is untampered *as of the check* — it's a verdict, not a guarantee against every possible attack.
- None of this replaces the cryptographic core (Ed25519 signatures + hash chain + reconciliation) — it hardens the device layer underneath it.
