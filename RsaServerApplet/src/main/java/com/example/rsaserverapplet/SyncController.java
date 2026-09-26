package com.example.rsaserverapplet;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/sync")
public class SyncController {

    private final ReconciliationService reconciliationService;

    public SyncController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    public static class SyncResultEntry {
        public long seqNo;
        public String status;

        public SyncResultEntry(long seqNo, String status) {
            this.seqNo = seqNo;
            this.status = status;
        }
    }

    @PostMapping
    public List<SyncResultEntry> sync(@RequestBody List<TransactionEnvelope> envelopes) throws Exception {
        List<SyncResultEntry> results = new ArrayList<>();
        // Process in the order the wallet sent them — this matters, since
        // ReconciliationService checks seqNo continuity against what it's
        // already accepted.
        for (TransactionEnvelope envelope : envelopes) {
            ReconciliationService.Status status = reconciliationService.processTransaction(envelope);
            results.add(new SyncResultEntry(envelope.transaction.seqNo, status.toString()));
        }
        return results;
    }

    @GetMapping("/balance")
    public Long getBalance(@RequestParam byte[] publicKey) {
        return reconciliationService.getBalance(publicKey);
    }
}
