package com.example.rsaserverapplet;

import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// SETUP REQUIRED:
// 1. A Google Cloud service account with the "Play Integrity API" enabled
//    on the same Cloud project registered in Play Console.
// 2. An OAuth2 access token for that service account (use
//    google-auth-library-oauth2-http; omitted here for brevity).
// 3. Your app's package name, matching what's registered in Play Console.
@RestController
@RequestMapping("/verify-integrity")
public class IntegrityVerificationController {

    private static final String PACKAGE_NAME = "com.example.offlinewallet";

    public static class IntegrityCheckRequest {
        public String integrityToken;
        public String expectedNonce;
    }

    @PostMapping
    public String verify(@RequestBody IntegrityCheckRequest req, @RequestHeader("Authorization") String bearerToken) throws Exception {
        String url = String.format(
                "https://playintegrity.googleapis.com/v1/%s:decodeIntegrityToken", PACKAGE_NAME);
        String body = String.format("{\"integrity_token\":\"%s\"}", req.integrityToken);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", bearerToken) // "Bearer <service-account-oauth-token>"
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Parse properly (Jackson) in a real build and check:
        //   - nonce matches req.expectedNonce (replay protection)
        //   - deviceRecognitionVerdict includes MEETS_DEVICE_INTEGRITY
        //   - appRecognitionVerdict is PLAY_RECOGNIZED
        return response.body();
    }
}
