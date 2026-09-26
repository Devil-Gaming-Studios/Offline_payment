package com.example.rsaserverapplet;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/onboard")
public class OnboardingController {

    private final CertificateAuthority certificateAuthority;

    public OnboardingController(CertificateAuthority certificateAuthority) {
        this.certificateAuthority = certificateAuthority;
    }

    public static class OnboardRequest {
        public String userId;
        public byte[] walletPublicKey;
    }

    @PostMapping
    public WalletCertificate onboard(@RequestBody OnboardRequest request) throws Exception {
        // NOTE: in a real build, verify the user (OTP/KYC) BEFORE issuing a
        // certificate. This endpoint currently trusts whatever userId is sent —
        // fine for a hackathon demo, not fine for production.
        return certificateAuthority.issueCertificate(request.userId, request.walletPublicKey);
    }
}
