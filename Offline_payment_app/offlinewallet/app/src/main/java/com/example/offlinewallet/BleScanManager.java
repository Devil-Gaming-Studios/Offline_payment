package com.example.offlinewallet;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

// Runs on the SENDING phone. Scans for nearby wallets advertising our
// service UUID, connects as a GATT client, reads the peer's identity
// characteristic and checks it against the public key we scanned from
// their QR code, and only then writes the transaction bytes. If the
// identity doesn't match (wrong device, someone else's phone caught the
// scan first), the send is aborted before anything is written.
public class BleScanManager {

    public interface OnSendCompleteListener {
        void onSendSuccess();
        void onSendFailed(String reason);
    }

    private static final String TAG = "BleScanManager";

    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final OnSendCompleteListener listener;
    private final byte[] payloadToSend;
    private final byte[] expectedReceiverPublicKey; // from scanned QR code; null = skip check

    private BluetoothLeScanner scanner;
    private BluetoothGatt connectedGatt;

    public BleScanManager(Context context, byte[] payloadToSend,
                           byte[] expectedReceiverPublicKey,
                           OnSendCompleteListener listener) {
        this.context = context;
        this.payloadToSend = payloadToSend;
        this.expectedReceiverPublicKey = expectedReceiverPublicKey;
        this.listener = listener;
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    }

    @SuppressLint("MissingPermission")
    public void startScan() {
        scanner = bluetoothManager.getAdapter().getBluetoothLeScanner();

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BleConstants.SERVICE_UUID))
                .build();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        scanner.startScan(List.of(filter), settings, scanCallback);
        Log.d(TAG, "Scan started");
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {
        if (scanner != null) scanner.stopScan(scanCallback);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "Found wallet device: " + result.getDevice().getAddress());
            stopScan(); // stop after first match — keep it simple for the demo
            connectedGatt = result.getDevice().connectGatt(context, false, gattClientCallback);
        }

        @Override
        public void onScanFailed(int errorCode) {
            listener.onSendFailed("Scan failed: " + errorCode);
        }
    };

    private final BluetoothGattCallback gattClientCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected, discovering services...");
                gatt.discoverServices();
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
            if (service == null) {
                listener.onSendFailed("Wallet service not found on peer");
                gatt.disconnect();
                return;
            }

            if (expectedReceiverPublicKey == null) {
                // No QR verification requested — proceed as before (legacy path).
                writePayment(gatt, service);
                return;
            }

            BluetoothGattCharacteristic identityChar =
                    service.getCharacteristic(BleConstants.IDENTITY_CHARACTERISTIC_UUID);
            if (identityChar == null) {
                listener.onSendFailed("Peer doesn't support identity verification");
                gatt.disconnect();
                return;
            }
            gatt.readCharacteristic(identityChar); // completes in onCharacteristicRead below
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (!characteristic.getUuid().equals(BleConstants.IDENTITY_CHARACTERISTIC_UUID)) return;

            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onSendFailed("Could not verify peer identity");
                gatt.disconnect();
                return;
            }

            byte[] actualKey = characteristic.getValue();
            if (!Arrays.equals(actualKey, expectedReceiverPublicKey)) {
                Log.w(TAG, "Identity mismatch — connected device is not the one scanned via QR");
                listener.onSendFailed("Identity mismatch: this isn't the device you scanned");
                gatt.disconnect();
                return;
            }

            BluetoothGattService service = gatt.getService(BleConstants.SERVICE_UUID);
            writePayment(gatt, service);
        }

        @SuppressLint("MissingPermission")
        private void writePayment(BluetoothGatt gatt, BluetoothGattService service) {
            BluetoothGattCharacteristic characteristic =
                    service.getCharacteristic(BleConstants.TRANSFER_CHARACTERISTIC_UUID);
            characteristic.setValue(payloadToSend);
            gatt.writeCharacteristic(characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener.onSendSuccess();
            } else {
                listener.onSendFailed("Write failed: " + status);
            }
            gatt.disconnect();
        }
    };
}
