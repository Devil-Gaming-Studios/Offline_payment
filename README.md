# Offline Micro-Payment Infrastructure (PS-07)

A lightweight cryptographic protocol enabling secure peer-to-peer digital wallet transfers over NFC/Bluetooth with **zero live connectivity**, reconciled with a central server once connectivity returns.

Built for PS-07 (Fintech/Security): *"Design a lightweight cryptography protocol enabling zero-connectivity digital wallet transfers using secure NFC/Bluetooth handshakes, reconciling transactions once connectivity is restored."*

---

## Table of Contents
- [Problem](#problem)
- [Solution Overview](#solution-overview)
- [Repo Structure](#repo-structure)
- [Architecture](#architecture)
- [Protocol Walkthrough](#protocol-walkthrough)
- [Security Features](#security-features)
- [Tech Stack](#tech-stack)
- [Setup](#setup)
- [Known Limitations](#known-limitations)
- [Comparison to UPI Lite X](#comparison-to-upi-lite-x)

---

## Problem

Digital payments fail completely without network connectivity — a real gap in rural India, disaster zones, and low-connectivity regions. Existing wallets assume an always-on connection to a central ledger for balance checks and fraud prevention. There is no widely accepted way for two devices to transact peer-to-peer with no network path, while still defending against fraud.

## Solution Overview

- Each wallet is an **Ed25519 keypair** — the public key is the wallet's address, no registry needed.
- Transactions are **signed offline** and remain independently verifiable — by the receiver instantly, or by the server days later — without either party needing to have witnessed the original exchange.
- A local **SHA-256 hash chain** links each wallet's transactions in order; tampering breaks the chain and is detectable.
- We do **not** claim to fully prevent offline double-spending (a limitation shared by real offline CBDC pilots, including RBI's UPI Lite X). Instead, offline exposure is capped, and fraud is caught **provably** — not heuristically — the moment devices reconnect.

## Repo Structure

```
Offline_payment/
├── offlinewallet/         # Android wallet app (Java)
│   └── app/src/main/java/com/example/offlinewallet/
│       ├── MainActivity.java
│       ├── Transaction.java
│       ├── TransactionSigner.java
│       ├── BleConstants.java
│       ├── BleGattServerManager.java     # BLE peripheral / GATT server (receiver role)
│       ├── BleScanManager.java           # BLE central / GATT client (sender role)
│       ├── DeviceSafetyPrecheck.java     # Developer Options / USB debugging block
│       ├── HardwareLedgerGuard.java      # StrongBox/TEE-backed counter signing
│       └── IntegrityChecker.java         # Play Integrity token request
└── RsaServerApplet/       # Spring Boot reconciliation server (Java)
    └── src/main/java/com/example/rsaserverapplet/
        ├── RsaServerAppletApplication.java
        ├── Transaction.java / TransactionEnvelope.java
        ├── CertificateAuthority.java / WalletCertificate.java
        ├── ReconciliationService.java / WalletLedgerState.java
        ├── OnboardingController.java     # POST /onboard
        ├── SyncController.java           # POST /sync, GET /sync/balance
        └── IntegrityVerificationController.java  # POST /verify-integrity
```

## Architecture

**Client (wallet app, each device)**
| Module | Responsibility |
|---|---|
| UI Layer | Balance, send/receive, sync status |
| Transaction Engine | Builds, signs (Ed25519), and verifies transactions |
| Local Ledger | Hash-chained transaction log (per-wallet) |
| Identity & Certificate Store | Long-term keypair + server-issued certificate |
| Transport Layer | BLE/NFC discovery, pairing, ECDH session |
| Sync Manager | Uploads local chain to server once online |

**Server**
| Module | Responsibility |
|---|---|
| API Gateway | Spring Boot REST endpoints |
| Onboarding & Certificate Authority | Verifies user, signs `userId ↔ publicKey` certificates |
| Reconciliation Engine | Validates and merges submitted chains |
| Master Ledger DB | Canonical balances & transaction history |
| Fraud Detection & Blacklist | Flags provable double-spends |

## Protocol Walkthrough

**Step 0 — Setup (once, online, before offline use)**
Wallet generates its identity keypair inside secure hardware (TEE/StrongBox); registers with the server and receives a signed certificate binding its identity to its public key.

**Step 1 — Discovery**
Two devices detect each other over BLE. No network involved from here on.

**Step 2 — Device Safety Check**
Each device checks: Developer Options/USB debugging off, and its last-known Play Integrity verdict is clean. Fails **closed** — refuses to transact if either check fails.

**Step 3 — Secure Key Exchange**
Devices exchange signed certificates (not raw keys), verify them against the server's known public key, then perform an **ECDH** key exchange to derive a shared session key encrypting everything that follows.

**Step 4 — Sender Signs the Transaction**
Sender builds `{senderPubKey, receiverPubKey, amount, seqNo, prevTxHash, timestamp}`, signs it with its **Ed25519** identity key (never the ephemeral ECDH key).

**Step 5 — Receiver Verifies & Countersigns**
Receiver checks: signature valid, `seqNo` continuity, amount within offline cap. If valid, countersigns — the record now carries two signatures.

**Step 6 — Both Sides Record Locally**
Each device appends the doubly-signed transaction to its own hash-chained ledger. Transaction is complete from the users' perspective — fully offline.

**Step 7 — Reconciliation (once online)**
Each wallet uploads its chain. Server checks continuity per wallet (`seqNo`, `prevTxHash`). Two different, validly-signed transactions at the same `seqNo` is mathematical proof of double-spend — the wallet is flagged and blacklisted.

## Security Features

| Layer | Mechanism | Defends against |
|---|---|---|
| Transaction authenticity | Ed25519 signatures | Forged/unauthorized transactions |
| Tamper-evidence | SHA-256 hash chain | Silent editing/reordering of transaction history |
| Session confidentiality | ECDH key exchange | Eavesdropping on the BLE/NFC handshake |
| Identity binding | Server-issued certificates | Man-in-the-middle claiming a false identity |
| Fraud detection | Reconciliation fork check | Double-spending — caught provably, not heuristically |
| Key extraction | Hardware-backed Keystore (StrongBox/TEE) | Private key theft via file access, root, or malware |
| Local tampering | `HardwareLedgerGuard` (hardware-signed `seqNo`/`prevHash`) | Direct editing of the local ledger file |
| Device compromise | Play Integrity API (server-verified) | Rooted, tampered, or non-certified devices |
| Early red flag | `DeviceSafetyPrecheck` | Developer Options / USB debugging enabled |

## Tech Stack

- **Client**: Native Android (Java), `BluetoothGatt`/`BluetoothLeAdvertiser` for BLE, Android Keystore (StrongBox/TEE), Play Integrity API
- **Server**: Spring Boot (Java), REST API, in-memory ledger (swap for Postgres/JPA in production)
- **Crypto**: `java.security` (JCA) — Ed25519 signatures, SHA-256 hashing, ECDH key agreement

## Setup

**Wallet app**: open `offlinewallet/` in Android Studio or build via CLI (`./gradlew assembleDebug`); install with `adb install`. Grant BLE runtime permissions on first launch (Android 12+).

**Server**: open `RsaServerApplet/`, run `RsaServerAppletApplication`. Exposes `/onboard`, `/sync`, `/sync/balance`, `/verify-integrity` on port 8080.

**Play Integrity** (optional hardening layer): requires app registration in Play Console with a Cloud Project Number, plus a Google Cloud service account for server-side token verification. Not required for the core protocol demo.

## Known Limitations

- Offline double-spend prevention is **capped**, not eliminated — same constraint accepted by real offline CBDC pilots. True prevention needs a hardware monotonic counter, which isn't a universal cross-device Android API.
- Current reconciliation ledger is **in-memory** for demo purposes; production needs a persistent DB.
- Certificate Authority's private key is currently stored as a plain file — production should use a secrets manager/HSM.
- No HTTPS/TLS configured yet on the server endpoints.

## Comparison to UPI Lite X

| | This Project | UPI Lite X |
|---|---|---|
| Tamper detection | Cryptographic (signatures + hash chain) — provable | Hardware/account-based — trusted, not provable per-transaction |
| Double-spend handling | Detected provably at reconciliation | Prevented via pre-funded wallet + hard caps |
| Proof portability | Transaction is self-verifying by anyone holding the public key | Relies on NPCI's backend and device trust |

**Positioning**: UPI Lite X minimizes damage by capping exposure through hardware trust; this project adds an evidentiary layer on top of the same capping idea — even within a small exposure window, fraud is **provable**, not just contained.
