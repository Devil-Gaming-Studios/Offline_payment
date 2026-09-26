package com.example.rsaserverapplet;

import org.springframework.web.bind.annotation.*;

@RestController
public class TopUpController {

    private final TopUpService topUpService;

    public TopUpController(TopUpService topUpService) {
        this.topUpService = topUpService;
    }

    // Wallet calls this instead of crediting its own local balance.
    @PostMapping("/topup/request")
    public TopUpRecord request(@RequestBody TopUpRequest request) throws Exception {
        return topUpService.submit(request);
    }

    @GetMapping("/topup/{requestId}")
    public TopUpRecord status(@PathVariable String requestId) {
        return topUpService.get(requestId);
    }

    // Stands in for an admin/ops review action. In production this would sit
    // behind real admin auth — it's open here only for demo purposes.
    @GetMapping("/admin/topup/pending")
    public java.util.List<TopUpRecord> pending() {
        return topUpService.listPending();
    }

    @GetMapping("/admin/topup/all")
    public java.util.List<TopUpRecord> all() {
        return topUpService.listAll();
    }

    @PostMapping("/admin/topup/{requestId}/approve")
    public TopUpRecord approve(@PathVariable String requestId) throws Exception {
        return topUpService.approve(requestId);
    }

    @PostMapping("/admin/topup/{requestId}/reject")
    public TopUpRecord reject(@PathVariable String requestId, @RequestParam(required = false) String reason) {
        return topUpService.reject(requestId, reason);
    }
}
