package com.example.rsaserverapplet;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class QrSyncController {

    private final QrSyncService qrSyncService;

    public QrSyncController(QrSyncService qrSyncService) {
        this.qrSyncService = qrSyncService;
    }

    // A phone calls this once it's back online to report a two-way QR
    // payment it completed while offline. Safe to call more than once for
    // the same transaction (e.g. retried after a dropped connection, or
    // reported by both the sender's and receiver's phone) — duplicates are
    // detected by nonce and ignored without double-crediting.
    @PostMapping("/qrsync/report")
    public QrTransferResult report(@RequestBody QrTransferReport report) {
        return qrSyncService.report(report);
    }

    // Feeds the admin dashboard's "QR Transactions" tab. Open here only for
    // demo purposes, same as the top-up admin endpoints — no auth yet.
    @GetMapping("/admin/qrsync/all")
    public List<QrTransferRecord> all() {
        return qrSyncService.listAll();
    }
}
