package com.example.offlinewallet;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

// Runs on the RECEIVING phone. Advertises presence so nearby wallets can
// find it, and hosts a GATT server with:
//  - a read-only IDENTITY characteristic (our public key), so a sender who
//    scanned our QR code can confirm they're connected to the right device
//    before sending anything
//  - a writable TRANSFER characteristic, same as before
public class BleGattServerManager {

    public interface OnDataReceivedListener {
        void onDataReceived(byte[] data);
    }

    private static final String TAG = "BleGattServer";

    private final Context context;
    private final BluetoothManager bluetoothManager;
    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    private final OnDataReceivedListener listener;
    private final byte[] identityPublicKey;

    public BleGattServerManager(Context context, byte[] identityPublicKey, OnDataReceivedListener listener) {
        this.context = context;
        this.listener = listener;
        this.identityPublicKey = identityPublicKey;
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    }

    @SuppressLint("MissingPermission") // permission checked by caller before starting
    public void startAdvertisingAndServing() {
        BluetoothAdapter adapter = bluetoothManager.getAdapter();

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        BluetoothGattService service = new BluetoothGattService(
                BleConstants.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic identityChar = new BluetoothGattCharacteristic(
                BleConstants.IDENTITY_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        identityChar.setValue(identityPublicKey);

        BluetoothGattCharacteristic transferChar = new BluetoothGattCharacteristic(
                BleConstants.TRANSFER_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        service.addCharacteristic(identityChar);
        service.addCharacteristic(transferChar);
        gattServer.addService(service);

        advertiser = adapter.getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(new ParcelUuid(BleConstants.SERVICE_UUID))
                .setIncludeDeviceName(false)
                .build();

        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    @SuppressLint("MissingPermission")
    public void stop() {
        if (advertiser != null) advertiser.stopAdvertising(advertiseCallback);
        if (gattServer != null) gattServer.close();
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.d(TAG, "Advertising started");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG, "Advertising failed: " + errorCode);
        }
    };

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(BleConstants.IDENTITY_CHARACTERISTIC_UUID)) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, identityPublicKey);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                boolean responseNeeded, int offset, byte[] value) {

            if (characteristic.getUuid().equals(BleConstants.TRANSFER_CHARACTERISTIC_UUID)) {
                Log.d(TAG, "Received " + value.length + " bytes from " + device.getAddress());
                listener.onDataReceived(value);
            }

            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        }
    };
}
