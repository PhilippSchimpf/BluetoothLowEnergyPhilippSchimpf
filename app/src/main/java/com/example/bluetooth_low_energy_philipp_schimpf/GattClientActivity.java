package com.example.bluetooth_low_energy_philipp_schimpf;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.bluetooth_low_energy_philipp_schimpf.Constants.CHARACTERISTIC_ECHO_UUID;
import static com.example.bluetooth_low_energy_philipp_schimpf.Constants.SERVICE_UUID;

public class GattClientActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_FINE_LOCATION = 2;

    ToggleButton onOffToggle;
    Button discoverBtn, clearLogBtn, sendMessageBtn;
    TextView statusText, logTextView;
    EditText sendMessageEditText;

    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler, mLogHandler;
    private HashMap<String, BluetoothDevice> mScanResults;
    private Boolean mScanning = false;
    private ScanCallback mScanCallback;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothGatt mGatt;
    private boolean mConnected;
    private boolean mEchoInitialized;

    /*--------------------------------------------------- LIFECYCLE ---------------------------------------------------*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gatt_client);

        statusText = (TextView) findViewById(R.id.text_view_status);
        logTextView = (TextView) findViewById(R.id.log_text_view);
        onOffToggle = (ToggleButton) findViewById(R.id.toggle_button);
        sendMessageBtn = (Button) findViewById(R.id.send_message_button);
        discoverBtn = (Button) findViewById(R.id.discover_button);
        clearLogBtn = (Button) findViewById(R.id.clear_log_button);
        sendMessageEditText = (EditText) findViewById(R.id.message_edit_text);

        mLogHandler = new Handler(Looper.getMainLooper());
        // Get BL Adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Change BL Status
        manageBluetoothState();

        // clear logs
        clearLogBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearLogs();
            }
        });

        // On/Off ToggleButton
        onOffToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                on(buttonView);
            } else {
                off(buttonView);
            }
        });

        // Send Message Button
        sendMessageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            sendMessage();
            }
        });

        // Discover Devices Button
        discoverBtn.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View v) {
                startScan();
            }
        });

        //List view Devices OnClickListener
        ListView listView = (ListView) findViewById(R.id.listView1);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
            @Override
            public void onItemClick(AdapterView<?> adapter, View v, int position,
                                    long arg3)
            {
                String value = (String)adapter.getItemAtPosition(position).toString();
                String address = value.substring(0,17);
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                connectDevice(device);
            }
        });
    }

    protected void onResume() {
        super.onResume();
        // Check low energy support
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            finish();
        }
        //Change BL Status
        manageBluetoothState();
    }

    /*--------------------------------------------------- CHANGE BL STATE BROADCAST RECEIVER ---------------------------------------------------------------------*/

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            // It means the user has changed his bluetooth state.
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
             manageBluetoothState();
            }
        }
    };

    /*----------------------------------------------------------------- SCANNING -----------------------------------------------------------------------------*/

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startScan() {
        if (!hasPermissions() || mScanning) {
            log("No Permissions");
            return;
        }

        disconnectGattServer();

        // TODO start the scan
        log("Starts Scanning");
        // lists filters for scan
        List<ScanFilter> filters = new ArrayList<>();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();
        mScanResults = new HashMap<>();
        mScanCallback = new BtleScanCallback(mScanResults);
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
        mScanning = true;
        mHandler = new Handler();
        mHandler.postDelayed(this::stopScan, 5000);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void stopScan() {
        if (mScanning && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback);
            log("Scan Stopped");
            scanComplete();
        }

        mScanCallback = null;
        mScanning = false;
        mHandler = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void scanComplete() {
        if (mScanResults.isEmpty()) {
            log("No Devices Found");
            return;
        }
        for (String deviceAddress : mScanResults.keySet()) {
            BluetoothDevice device = mScanResults.get(deviceAddress);
            log("Found Device--> Address: " + device.getAddress() + " Name: " + device.getName());
        }
        showDevices(mScanResults);
    }

    private boolean hasPermissions() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            requestBluetoothEnable();
            return false;
        } else if (!hasLocationPermissions()) {
            requestLocationPermission();
            return false;
        }
        return true;
    }

    private void requestBluetoothEnable() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        log("Requested User Enables Bluetooth. Try Starting The Scan Again.");
    }

    private boolean hasLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return false;
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
        }
    }

    /*----------------------------------------------------- CUSTOM HASH-MAP ADAPTER --------------------------------------------------------------------*/

    public class MyAdapter extends BaseAdapter {
        private final ArrayList mData;

        public MyAdapter(Map<String, BluetoothDevice> map) {
            mData = new ArrayList();
            mData.addAll(map.entrySet());
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public Map.Entry<String, BluetoothDevice> getItem(int position) {
            return (Map.Entry) mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            // TODO implement you own logic with ID
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View result;

            if (convertView == null) {
                result = LayoutInflater.from(parent.getContext()).inflate(R.layout.discovered_devices_list_view, parent, false);
            } else {
                result = convertView;
            }

            Map.Entry<String, BluetoothDevice> item = getItem(position);

            ((TextView) result.findViewById(android.R.id.text1)).setText(item.getKey());
            ((TextView) result.findViewById(android.R.id.text2)).setText(item.getValue().getName());

            return result;
        }
    }

    /*-------------------------------------------------- TURN OFF/ON BLUETOOTH ------------------------------------------------------------*/

    //TURN ON BLUETOOTH
    public void on(View view) {
        Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(turnOn, REQUEST_ENABLE_BT);
        Toast.makeText(getApplicationContext(), "Turned On", Toast.LENGTH_LONG).show();
        log("Turned On");

        //Change BL Status
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    //TURN OFF BLUETOOTH
    public void off(View view) {
        mBluetoothAdapter.disable();
        Toast.makeText(getApplicationContext(), "Turned Off", Toast.LENGTH_LONG).show();
        log("Turned Off");

        //Change BL Status
        this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    /*------------------------------------------------- BLUETOOTH STATE -------------------------------------------------------*/

    public void manageBluetoothState(){
        if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
            statusText.setText("Status: Enabled");
            onOffToggle.setChecked(true);
            return;
        }

        if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
            statusText.setText("Status: Disabled");
            onOffToggle.setChecked(false);
            return;
        }
    }

    /*----------------------------------------- SHOW DEVICES-SEND MESSAGE --------------------------------------*/

    // Show Devices
    public void showDevices(HashMap<String, BluetoothDevice> devices) {
        MyAdapter adapter = new MyAdapter(devices);
        ListView listView = (ListView) findViewById(R.id.listView1);
        listView.setAdapter(adapter);
    }



    //Send Message
    private void sendMessage() {
        if (!mConnected || !mEchoInitialized) {
            return;
        }

        BluetoothGattCharacteristic characteristic = BluetoothUtils.findEchoCharacteristic(mGatt);
        if (characteristic == null) {
            logError("Unable to find echo characteristic.");
            disconnectGattServer();
            return;
        }

        String message = sendMessageEditText.getText().toString();
        log("Sending message: " + message);

        byte[] messageBytes = StringUtils.bytesFromString(message);
        if (messageBytes.length == 0) {
            logError("Unable to convert message to bytes");
            return;
        }

        characteristic.setValue(messageBytes);
        boolean success = mGatt.writeCharacteristic(characteristic);
        if (success) {
            log("Wrote: " + StringUtils.byteArrayInHexFormat(messageBytes));
        } else {
            logError("Failed to write data");
        }
    }

    /*------------------------------------------------------- LOGGING ----------------------------------------------------------------*/

    public void log(String msg) {
        Log.d("CLIENT", msg);
        logTextView.setMovementMethod(new ScrollingMovementMethod());
        mLogHandler.post(() -> {
            logTextView.setText(msg + "\n");
        });
    }

    private void clearLogs() {
        logTextView.setText("");
    }

    public void logError(String msg) {
        log("Error: " + msg);
    }

    /*--------------------------------------------------- GATT CONNECTION ---------------------------------------------------*/

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void connectDevice(BluetoothDevice device) {
        log("Connecting to " + device.getAddress());
        GattClientCallback gattClientCallback = new GattClientCallback();
        mGatt = device.connectGatt(this, false, gattClientCallback);
    }

    /*------------------------------------------------ GATT CLIENT ACTIONS -------------------------------------------*/

    public void setConnected(boolean connected) {
        mConnected = connected;
    }

    public void initializeEcho() {
        mEchoInitialized = true;
    }

    public void disconnectGattServer() {
        log("Closing Gatt connection");
        clearLogs();
        mConnected = false;
        mEchoInitialized = false;
        if (mGatt != null) {
            mGatt.disconnect();
            mGatt.close();
        }
    }

    /*------------------------------------------------------------- CALLBACKS --------------------------------------------------------------------------*/

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private class BtleScanCallback extends ScanCallback {

        public BtleScanCallback(Map<String, BluetoothDevice> mScanResults) {
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            addScanResult(result);
            log("OnScanResult : " + result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                addScanResult(result);
                log( "OnBatchScanResult : " + result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            log("BluetoothLiteEnergy Scan Failed With Code(OnScanFailed): " + errorCode);
        }

        private void addScanResult(ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String deviceAddress = device.getAddress();
            mScanResults.put(deviceAddress,device);

            log("Remote Device Address: " + deviceAddress);
            log("Remote Device Name: " + device.getName());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private class GattClientCallback extends BluetoothGattCallback {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            log("onConnectionStateChange newState: " + newState);//    android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.

            if (status == BluetoothGatt.GATT_FAILURE) {
                logError("Connection Gatt failure status " + status);
                disconnectGattServer();
                return;
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                // handle anything not SUCCESS as failure
                logError("Connection not GATT success status " + status);
                disconnectGattServer();
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Connected to device " + gatt.getDevice().getAddress());
                setConnected(true);
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Disconnected from device");
                disconnectGattServer();
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Device service discovery unsuccessful, status " + status);
                return;
            }

            List<BluetoothGattCharacteristic> matchingCharacteristics = BluetoothUtils.findCharacteristics(gatt);
            if (matchingCharacteristics.isEmpty()) {
                logError("Unable to find characteristics.");
                return;
            }

            log("Initializing: setting write type and enabling notification");
            for (BluetoothGattCharacteristic characteristic : matchingCharacteristics) {
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                enableCharacteristicNotification(gatt, characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Characteristic written successfully");
            } else {
                logError("Characteristic write unsuccessful, status: " + status);
                disconnectGattServer();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Characteristic read successfully");
                readCharacteristic(characteristic);
            } else {
                logError("Characteristic read unsuccessful, status: " + status);
                // Trying to read from the Time Characteristic? It doesnt have the property or permissions
                // set to allow this. Normally this would be an error and you would want to:
                // disconnectGattServer();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            log("Characteristic changed, " + characteristic.getUuid().toString());
            readCharacteristic(characteristic);
        }

        private void enableCharacteristicNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            boolean characteristicWriteSuccess = gatt.setCharacteristicNotification(characteristic, true);
            if (characteristicWriteSuccess) {
                log("Characteristic notification set successfully for " + characteristic.getUuid().toString());
                if (BluetoothUtils.isEchoCharacteristic(characteristic)) {
                    initializeEcho();
                }
            } else {
                logError("Characteristic notification set failure for " + characteristic.getUuid().toString());
            }
        }

        private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
            byte[] messageBytes = characteristic.getValue();
            log("Read: " + StringUtils.byteArrayInHexFormat(messageBytes));
            String message = StringUtils.stringFromBytes(messageBytes);
            if (message == null) {
                logError("Unable to convert bytes to string");
                return;
            }

            log("Received message: " + message);
        }

    }
}