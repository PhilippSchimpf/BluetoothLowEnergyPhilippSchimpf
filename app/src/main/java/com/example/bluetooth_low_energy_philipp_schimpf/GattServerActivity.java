package com.example.bluetooth_low_energy_philipp_schimpf;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.example.bluetooth_low_energy_philipp_schimpf.Constants.CHARACTERISTIC_ECHO_UUID;
import static com.example.bluetooth_low_energy_philipp_schimpf.Constants.SERVICE_UUID;

public class GattServerActivity extends AppCompatActivity {

    private Button startServerBtn, stopServerBtn, clearLogBtn;
    private TextView serverInfoTextView, logTextView;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private BluetoothGattServer mGattServer;
    private List<BluetoothDevice> mDevices;
    private Handler mHandler, mLogHandler;

/*------------------------------------------------- LIFECYCLE --------------------------------------------------*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gatt_server);

        startServerBtn = (Button) findViewById(R.id.start_server_button);
        stopServerBtn = (Button) findViewById((R.id.stop_server_button));
        clearLogBtn = (Button) findViewById(R.id.clear_log_button);
        serverInfoTextView = (TextView) findViewById(R.id.server_device_info_text_view);
        logTextView = (TextView) findViewById(R.id.log_text_view);

        mDevices = new ArrayList<>();
        mHandler = new Handler();
        mLogHandler = new Handler(Looper.getMainLooper());

        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        clearLogBtn.setOnClickListener(v -> clearLogs());

        startServerBtn.setOnClickListener(v -> {
            setUpServer();
            startAdvertising();

            @SuppressLint("HardwareIds")
            String macAddress = android.provider.Settings.Secure.getString(getApplicationContext().getContentResolver(), "bluetooth_address");
            String deviceInfo = "Device Info" + "\nName: " + mBluetoothAdapter.getName() + "\nAddress: " + macAddress;
            serverInfoTextView.setText(deviceInfo);
        });

        stopServerBtn.setOnClickListener(v -> {
            stopAdvertising();
            stopServer();
        });
    }

    protected void onResume() {
        super.onResume();
        // Check if bluetooth is enabled
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            finish();
            return;
        }
        //check low energy support
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            // Get a newer device
            log("No LE Support.");
            finish();
            return;
        }
        //Check advertising
        if (!mBluetoothAdapter.isMultipleAdvertisementSupported()) {
            log("No Advertising Support.");
            finish();
            return;
        }
        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        GattServerCallback gattServerCallback = new GattServerCallback();
        mGattServer = mBluetoothManager.openGattServer(this, gattServerCallback);

        @SuppressLint("HardwareIds")
        String deviceInfo = "Device Info" + "\nName: " + mBluetoothAdapter.getName() ;
        serverInfoTextView.setText(deviceInfo);

        setUpServer();
        startAdvertising();
    }

    protected void onPause(){
        super.onPause();
        stopAdvertising();
        stopServer();
    }

    /*-------------------------------------------------------- GATT SERVER ---------------------------------------------------------------*/

    // Server set up
    private void setUpServer(){
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Write characteristic
        BluetoothGattCharacteristic writeCharacteristic = new BluetoothGattCharacteristic(CHARACTERISTIC_ECHO_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        service.addCharacteristic(writeCharacteristic);

        mGattServer.addService(service);
    }

    private void stopServer(){
        if(mGattServer != null){
            mGattServer.close();
        }
    }

    /*-------------------------------------------------- ADVERTISING -----------------------------------------------------------------*/
    private void startAdvertising(){
        if(mBluetoothLeAdvertiser == null){
            return;
        }
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .build();
        ParcelUuid parcelUuid = new ParcelUuid(SERVICE_UUID);
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(parcelUuid)
                .build();
        mBluetoothLeAdvertiser.startAdvertising(settings, data, mAdvertiseCallback);
    }

    private void stopAdvertising(){
        if(mBluetoothLeAdvertiser != null){
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
            log("Peripheral Advertising Stopped");
        }
    }

    /*------------------------------------------------ NOTIFICATIONS -------------------------------------------------------------*/

    private void notifyCharacteristic(byte[] value, UUID uuid) {
        mHandler.post(() -> {
            BluetoothGattService service = mGattServer.getService(SERVICE_UUID);
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid);
            log("Notifying characteristic " + characteristic.getUuid().toString()
                    + ", new value: " + StringUtils.byteArrayInHexFormat(value));

            characteristic.setValue(value);
            boolean confirm = BluetoothUtils.requiresConfirmation(characteristic);
            for(BluetoothDevice device : mDevices) {
                mGattServer.notifyCharacteristicChanged(device, characteristic, confirm);
            }
        });
    }

/*--------------------------------------------------- GATT SERVER ACTIONS LISTENER ----------------------------------------------------------*/

    public void addDevice(BluetoothDevice device) {
        log("Deviced added: " + device.getAddress());
        mHandler.post(() -> mDevices.add(device));
    }

    public void removeDevice(BluetoothDevice device) {
        log("Devices removed: " + device.getAddress());
        mHandler.post(() -> mDevices.remove(device));
    }

    public void sendResponse(BluetoothDevice device, int requestId, int status, int offset, byte[] value) {
        mHandler.post(() -> mGattServer.sendResponse(device, requestId, status, 0, null));

    }

    private void sendReverseMessage(byte[] message) {
        mHandler.post(() -> {
            // Reverse message to differentiate original message & response
            byte[] response = ByteUtils.reverse(message);
            log("Sending: " + StringUtils.byteArrayInHexFormat(response));
            notifyCharacteristicEcho(response);
        });
    }

    public void notifyCharacteristicEcho(byte[] value) {
        notifyCharacteristic(value, CHARACTERISTIC_ECHO_UUID);
    }

    /*----------------------------------------------------- LOGGING -------------------------------------------------------------*/

    private void clearLogs() {
        logTextView.setText("");
    }

    public void log(String msg) {
        Log.d("SERVER", msg);
        logTextView.setMovementMethod(new ScrollingMovementMethod());
        mLogHandler.post(() -> {
            logTextView.setText(msg + "\n");// FIXED   android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.
        });

    }

    /*--------------------------------------------------------- CALLBACKS ------------------------------------------------------------*/

    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback(){

        public void onStartSuccess(AdvertiseSettings settingsInEffect){
            log("Peripheral Advertising Started");
        }

        @Override
        public void onStartFailure(int ErrorCode){
            String description = "";
            if (ErrorCode == AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED)
                description = "ADVERTISE_FAILED_FEATURE_UNSUPPORTED";
            else if (ErrorCode == AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS)
                description = "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS";
            else if (ErrorCode == AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED)
                description = "ADVERTISE_FAILED_ALREADY_STARTED";
            else if (ErrorCode == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE)
                description = "ADVERTISE_FAILED_DATA_TOO_LARGE";
            else if (ErrorCode == AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR)
                description = "ADVERTISE_FAILED_INTERNAL_ERROR";
            else description = "unknown";
            log("Peripheral Advertising Failed: " + description);
        }
    };

    private class GattServerCallback extends BluetoothGattServerCallback {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState){
            super.onConnectionStateChange(device, status, newState);//    android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.

            log("onConnectionStateChange " + device.getAddress() + "\nstatus " + status + "\nnewState " + newState);//    java.lang.NullPointerException: Attempt to invoke interface method 'boolean java.util.List.add(java.lang.Object)' on a null object reference

            if (newState == BluetoothProfile.STATE_CONNECTED){
                log("Devices added: " + device.getAddress());
                addDevice(device); //  FIXED   java.lang.NullPointerException: Attempt to invoke interface method 'boolean java.util.List.add(java.lang.Object)' on a null object reference

            }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                log("Devices removed: " + device.getAddress());
                removeDevice(device);
            }
        }

        // The Gatt will reject Characteristic Read requests that do not have the permission set,
        // so there is no need to check inside the callback
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device,
                                                int requestId,
                                                int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

            log("onCharacteristicReadRequest "
                    + characteristic.getUuid().toString());

            if (BluetoothUtils.requiresResponse(characteristic)) {
                // Unknown read characteristic requiring response, send failure
                sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null);
            }
            // Not one of our characteristics or has NO_RESPONSE property set
        }

        // The Gatt will reject Characteristic Write requests that do not have the permission set,
        // so there is no need to check inside the callback
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 byte[] value) {
            super.onCharacteristicWriteRequest(device,
                    requestId,
                    characteristic,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value);
            log("onCharacteristicWriteRequest" + characteristic.getUuid().toString()
                    + "\nReceived: " + StringUtils.byteArrayInHexFormat(value));


            if (CHARACTERISTIC_ECHO_UUID.equals(characteristic.getUuid())) {
                sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                sendReverseMessage(value);
            }
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            log("onNotificationSent");
        }
    }
}