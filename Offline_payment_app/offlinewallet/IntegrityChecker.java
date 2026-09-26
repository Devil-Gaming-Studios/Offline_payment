package com.example.offlinewallet;

import android.content.Context;
import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.IntegrityTokenRequest;
import com.google.android.play.core.integrity.IntegrityTokenResponse;

import java.security.SecureRandom;
import java.util.Base64;

// SETUP REQUIRED:
// 1. App must be registered in Play Console (App Integrity section) — this
//    will NOT work for an arbitrary unsigned debug APK tested purely locally.
// 2. Add dependency: implementation("com.google.android.play:integrity:1.4.0")
// 3. Get your Cloud Project Number from Play Console > App Integrity.
//
// This class only REQUESTS the token. It is opaque and signed by Google —
// your app cannot verify it itself. Send it to YOUR server, which calls
// Google's Play Integrity API to decode the actual verdict. Never trust
// a verdict decided on-device — a compromised device could lie about it.
public class IntegrityChecker {

    private static final long CLOUD_PROJECT_NUMBER = 0L; // TODO: replace with your real project number

    public interface OnTokenReceivedListener {
        void onTokenReceived(String token, String requestNonce);
        void onFailure(Exception e);
    }

    public static void requestIntegrityToken(Context context, OnTokenReceivedListener listener) {
        byte[] nonceBytes = new byte[16];
        new SecureRandom().nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);

        var integrityManager = IntegrityManagerFactory.create(context);
        IntegrityTokenRequest request = IntegrityTokenRequest.builder()
                .setNonce(nonce)
                .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                .build();

        integrityManager.requestIntegrityToken(request)
                .addOnSuccessListener((IntegrityTokenResponse response) ->
                        listener.onTokenReceived(response.token(), nonce))
                .addOnFailureListener(listener::onFailure);
    }
}
