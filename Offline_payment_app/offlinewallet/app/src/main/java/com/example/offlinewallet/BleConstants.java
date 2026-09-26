package com.example.offlinewallet;

import java.util.UUID;

public class BleConstants {
    // Custom UUIDs for the offline wallet service. Both phones must use
    // the exact same UUIDs, or they'll never recognize each other.
    public static final UUID SERVICE_UUID =
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID TRANSFER_CHARACTERISTIC_UUID =
            UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    // Read-only: lets a sender confirm — after scanning the receiver's QR —
    // that this BLE peripheral is actually the same device before paying it.
    public static final UUID IDENTITY_CHARACTERISTIC_UUID =
            UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
}
