package com.sk7software.climbviewer;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.device.CadenceMeasurementParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BTCadenceActivity extends AppCompatActivity {

    private static final String TAG = BTCadenceActivity.class.getSimpleName();

    // Request Codes
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;

    // UUIDs for Cycling Speed and Cadence Service
    // Cycling Speed and Cadence Service (CSC)
    private static final UUID CSC_SERVICE_UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb");
    // CSC Measurement Characteristic (contains speed and cadence data)
    private static final UUID CSC_MEASUREMENT_CHAR_UUID = UUID.fromString("00002A5B-0000-1000-8000-00805f9b34fb");
    // Client Characteristic Configuration Descriptor (CCCD) - used to enable notifications
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Bluetooth components
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt; // The GATT client for the connected device

    // UI elements
    private TextView statusTextView;
    private TextView cadenceValueTextView;
    private TextView scanningStatusTextView;
    private Button scanButton;
    private Button disconnectButton;
    private ListView devicesListView;

    // For device list
    private ArrayAdapter<String> devicesArrayAdapter;
    // Store BluetoothDevice objects mapped by their address to avoid duplicates and easily retrieve
    private Map<String, BluetoothDevice> discoveredDevicesMap;
    private BluetoothDevice selectedDevice; // The device selected for connection
    private CadenceMeasurementParser cadenceParser;

    // Handler for stopping scan after a delay
    private Handler scanHandler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;
    private static final long SCAN_PERIOD = 10000; // Scan for 10 seconds

    // --- Reconnect Logic Variables ---
    private Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private Runnable reconnectRunnable;
    private boolean shouldAttemptReconnect = false; // Flag to control auto-reconnect
    private int reconnectAttemptCount = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 10000; // Max retries
    private static final long RECONNECT_DELAY_MS = 3000; // 3 seconds delay between attempts

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bt_cadence);

        // Initialize UI components
        statusTextView = findViewById(R.id.statusTextView);
        cadenceValueTextView = findViewById(R.id.cadenceValueTextView);
        scanningStatusTextView = findViewById(R.id.scanningStatusTextView);
        scanButton = findViewById(R.id.scanButton);
        disconnectButton = findViewById(R.id.disconnectButton);
        devicesListView = findViewById(R.id.devicesListView);
        cadenceParser = new CadenceMeasurementParser();

        // Initialize device list and adapter
        discoveredDevicesMap = new HashMap<>();
        devicesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        devicesListView.setAdapter(devicesArrayAdapter);

        // Get BluetoothManager and BluetoothAdapter
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // --- Set up Click Listeners ---
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Ensure no active reconnect attempts if starting a new scan
                cancelReconnectAttempts();

                if (checkBluetoothPermissionsGranted()) {
                    startBleScan();
                } else {
                    requestBluetoothPermissions();
                }
            }
        });

        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // When user manually disconnects, stop auto-reconnect attempts
                shouldAttemptReconnect = false;
                cancelReconnectAttempts();
                disconnectBleDevice();
            }
        });

        // --- Set up ListView Item Click Listener ---
        devicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Get the device from the map based on the selected item's address
                String selectedDeviceInfo = (String) parent.getItemAtPosition(position);
                String deviceAddress = selectedDeviceInfo.substring(selectedDeviceInfo.length() - 17); // Extract MAC address (last 17 chars)

                selectedDevice = discoveredDevicesMap.get(deviceAddress);

                if (selectedDevice != null) {
                    stopBleScan(); // Stop scanning once a device is selected

                    // Cancel any previous reconnect attempts if a new device is selected
                    cancelReconnectAttempts();
                    shouldAttemptReconnect = true; // Enable reconnect for this newly selected device

                    if (ActivityCompat.checkSelfPermission(BTCadenceActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(BTCadenceActivity.this, "BLUETOOTH_CONNECT permission needed to connect.", Toast.LENGTH_SHORT).show();
                        requestBluetoothPermissions();
                        return;
                    }
                    Toast.makeText(BTCadenceActivity.this, "Selected: " + selectedDevice.getName() + " (" + selectedDevice.getAddress() + ")", Toast.LENGTH_SHORT).show();
                    updateStatus("Connecting to " + selectedDevice.getName() + "...");

                    // Close previous GATT if exists before connecting to new one
                    if (bluetoothGatt != null) {
                        bluetoothGatt.close();
                        bluetoothGatt = null;
                    }
                    // Connect to the GATT server hosted by the BLE device
                    // ensure BLUETOOTH_CONNECT permission is checked
                    if (ActivityCompat.checkSelfPermission(BTCadenceActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        // `autoConnect` false: Directly connect. True: Reconnect if connection is lost.
                        bluetoothGatt = selectedDevice.connectGatt(BTCadenceActivity.this, false, gattCallback);
                        // store selected device in preferences
                        Parcel parcel = Parcel.obtain();
                        selectedDevice.writeToParcel(parcel, 0);
                        byte[] byteArray = parcel.marshall();
                        // encode byteArray as base64
                        String encodedDevice = android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT);
                        Preferences.getInstance().addPreference(Preferences.PREFERENCE_SELECTED_BLE_DEVICE, encodedDevice);
                    } else {
                        Toast.makeText(BTCadenceActivity.this, "BLUETOOTH_CONNECT permission needed to connect.", Toast.LENGTH_SHORT).show();
                        requestBluetoothPermissions();
                    }
                }
            }
        });

        // Initialize reconnect runnable (defined here, posted later)
        reconnectRunnable = new Runnable() {
            @Override
            public void run() {
                if (shouldAttemptReconnect && selectedDevice != null && bluetoothGatt != null) {
                    if (reconnectAttemptCount < MAX_RECONNECT_ATTEMPTS) {
                        reconnectAttemptCount++;
                        if (ActivityCompat.checkSelfPermission(BTCadenceActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            Log.e(TAG, "BLUETOOTH_CONNECT permission missing for reconnect attempt.");
                            requestBluetoothPermissions();
                            return;
                        }
                        updateStatus("Reconnecting to " + selectedDevice.getName() + " (Attempt " + reconnectAttemptCount + "/" + MAX_RECONNECT_ATTEMPTS + ")");
                        Log.d(TAG, "Attempting to reconnect to " + selectedDevice.getName() + " (Attempt " + reconnectAttemptCount + ")");
                        // Ensure BLUETOOTH_CONNECT permission is checked before calling connect()
                        if (ActivityCompat.checkSelfPermission(BTCadenceActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            bluetoothGatt.connect(); // Use connect() on existing GATT object
                        } else {
                            Log.e(TAG, "BLUETOOTH_CONNECT permission missing for reconnect attempt.");
                            Toast.makeText(BTCadenceActivity.this, "Cannot reconnect: Permissions missing.", Toast.LENGTH_SHORT).show();
                            shouldAttemptReconnect = false; // Stop trying if permissions are gone
                        }
                        reconnectHandler.postDelayed(this, RECONNECT_DELAY_MS); // Schedule next attempt
                    } else {
                        updateStatus("Reconnection failed after " + MAX_RECONNECT_ATTEMPTS + " attempts.");
                        Toast.makeText(BTCadenceActivity.this, "Failed to reconnect to device.", Toast.LENGTH_LONG).show();
                        shouldAttemptReconnect = false; // Stop trying
                        closeGatt(); // Clean up GATT object
                    }
                }
            }
        };

        // Initial permission request and Bluetooth status check
        requestBluetoothPermissions();
    }

    private void updateStatus(String s) {
        Log.d(TAG, s);
        runOnUiThread(() -> {
            statusTextView.setText(s);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBluetoothStatus(); // Check Bluetooth status every time the activity resumes
    }

    /**
     * Checks if all necessary Bluetooth permissions are granted.
     * @return true if permissions are granted, false otherwise.
     */
    private boolean checkBluetoothPermissionsGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else { // Older Android versions
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Requests necessary Bluetooth permissions at runtime.
     */
    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_BLUETOOTH_PERMISSIONS);
        } else { // Older Android versions
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (checkBluetoothPermissionsGranted()) {
                Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show();
                checkBluetoothStatus();
            } else {
                Toast.makeText(this, "Bluetooth permissions denied. Cannot use Bluetooth features.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Checks if Bluetooth is enabled and prompts the user to enable it if not.
     */
    private void checkBluetoothStatus() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            // ensure BLUETOOTH_CONNECT permission is checked
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                Toast.makeText(this, "BLUETOOTH_CONNECT permission not granted. Cannot enable Bluetooth.", Toast.LENGTH_SHORT).show();
                requestBluetoothPermissions();
            }
        } else {
            updateStatus("Bluetooth is enabled");
            // Also ensure the device supports BLE
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                Toast.makeText(this, "BLE not supported on this device", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth enabled by user", Toast.LENGTH_SHORT).show();
                updateStatus("Bluetooth is enabled");
            } else {
                Toast.makeText(this, "Bluetooth not enabled by user", Toast.LENGTH_SHORT).show();
                updateStatus("Bluetooth is disabled");
            }
        }
    }

    /**
     * Starts BLE scanning for a predefined period.
     */
    private void startBleScan() {
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is not enabled.", Toast.LENGTH_SHORT).show();
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "BLE Scanner not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Clear previous results
        devicesArrayAdapter.clear();
        discoveredDevicesMap.clear();
        devicesArrayAdapter.add("--- Discovered BLE Devices ---"); // Header
        devicesArrayAdapter.notifyDataSetChanged();

        // Stop any existing scan
        if (isScanning) {
            stopBleScan();
        }
        // Stop any ongoing reconnect attempts
        cancelReconnectAttempts();

        // Start scanning
        // Ensure BLUETOOTH_SCAN permission is checked
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            isScanning = true;
            bluetoothLeScanner.startScan(scanCallback);
            scanningStatusTextView.setText("Scanning: In Progress...");
            Toast.makeText(this, "Starting BLE device discovery...", Toast.LENGTH_SHORT).show();

            // Stop scan after SCAN_PERIOD
            scanHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopBleScan();
                }
            }, SCAN_PERIOD);

        } else {
            Toast.makeText(this, "BLUETOOTH_SCAN permission not granted. Cannot start BLE discovery.", Toast.LENGTH_SHORT).show();
            requestBluetoothPermissions();
        }
    }

    /**
     * Stops BLE scanning.
     */
    private void stopBleScan() {
        if (isScanning && bluetoothLeScanner != null) {
            // Ensure BLUETOOTH_SCAN permission is checked
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothLeScanner.stopScan(scanCallback);
            }
            isScanning = false;
            scanningStatusTextView.setText("Scanning: Finished");
            Toast.makeText(this, "BLE device discovery finished.", Toast.LENGTH_SHORT).show();

            // If no devices found, add a message
            if (discoveredDevicesMap.isEmpty()) {
                devicesArrayAdapter.add("No BLE devices found.");
                devicesArrayAdapter.notifyDataSetChanged();
            }
        }
    }

    // BLE Scan Callback for handling scan results
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            // Ensure BLUETOOTH_CONNECT permission is checked before calling getName()
            if (ActivityCompat.checkSelfPermission(BTCadenceActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                if (device != null && !discoveredDevicesMap.containsKey(device.getAddress())) {
                    String deviceName = device.getName() != null ? device.getName() : "Unknown Device";
                    String deviceInfo = deviceName + "\n" + device.getAddress();
                    devicesArrayAdapter.add(deviceInfo);
                    discoveredDevicesMap.put(device.getAddress(), device);
                    devicesArrayAdapter.notifyDataSetChanged();
                    Log.d(TAG, "Found BLE device: " + deviceInfo);
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results) {
                onScanResult(0, result); // Process each result
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            isScanning = false;
            scanningStatusTextView.setText("Scanning: Failed (" + errorCode + ")");
            Toast.makeText(BTCadenceActivity.this, "BLE scan failed: " + errorCode, Toast.LENGTH_LONG).show();
            Log.e(TAG, "BLE scan failed with error code: " + errorCode);
        }
    };

    // BluetoothGattCallback for managing GATT interactions
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Device connected, discover services
                if (ActivityCompat.checkSelfPermission(BTCadenceActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(BTCadenceActivity.this, "BLUETOOTH_CONNECT permission needed to connect.", Toast.LENGTH_SHORT).show();
                    requestBluetoothPermissions();
                    return;
                }
                updateStatus("Connected to " + gatt.getDevice().getName() + ". Discovering services...");
                Log.d(TAG, "Connected to GATT server.");
                // Reset reconnect attempts on successful connection
                cancelReconnectAttempts();

                // ensure BLUETOOTH_CONNECT permission is checked
                if (ActivityCompat.checkSelfPermission(BTCadenceActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices();
                } else {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission missing, cannot discover services.");
                    disconnectBleDevice();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Device disconnected
                updateStatus("Disconnected from " + gatt.getDevice().getName());
                runOnUiThread(() -> {
                    cadenceValueTextView.setText("Cadence: -- RPM");
                });
                Log.d(TAG, "Disconnected from GATT server.");

                // --- Reconnect Logic Trigger ---
                if (shouldAttemptReconnect && selectedDevice != null && bluetoothGatt != null) {
                    Log.d(TAG, "Initiating reconnect attempt...");
                    reconnectAttemptCount = 0; // Reset counter for new attempts
                    reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
                } else {
                    // If not auto-reconnecting, then just close GATT
                    closeGatt();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (ActivityCompat.checkSelfPermission(BTCadenceActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(BTCadenceActivity.this, "BLUETOOTH_CONNECT permission needed to discover services.", Toast.LENGTH_SHORT).show();
                    requestBluetoothPermissions();
                    return;
                }
                updateStatus("Services discovered on " + gatt.getDevice().getName());
                Log.d(TAG, "Services discovered.");

                BluetoothGattService cscService = gatt.getService(CSC_SERVICE_UUID);
                if (cscService == null) {
                    updateStatus("CSC Service not found on " + gatt.getDevice().getName());
                    Log.e(TAG, "CSC Service not found!");
                    disconnectBleDevice();
                    return;
                }

                BluetoothGattCharacteristic cscMeasurementChar = cscService.getCharacteristic(CSC_MEASUREMENT_CHAR_UUID);
                if (cscMeasurementChar == null) {
                    updateStatus("CSC Measurement Characteristic not found.");
                    Log.e(TAG, "CSC Measurement Characteristic not found!");
                    disconnectBleDevice();
                    return;
                }

                // Enable notifications for the CSC Measurement Characteristic
                // ensure BLUETOOTH_CONNECT permission is checked
                if (ActivityCompat.checkSelfPermission(BTCadenceActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.setCharacteristicNotification(cscMeasurementChar, true);

                    BluetoothGattDescriptor descriptor = cscMeasurementChar.getDescriptor(CCCD_UUID);
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor); // Write to descriptor to enable notifications
                        updateStatus("Subscribed to Cadence notifications.");
                        Log.d(TAG, "Successfully set notification for CSC Measurement Characteristic.");
                    } else {
                        updateStatus("CCCD Descriptor not found for Cadence.");
                        Log.e(TAG, "CCCD Descriptor not found for CSC Measurement Characteristic!");
                        disconnectBleDevice();
                    }
                } else {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission missing, cannot set notifications.");
                    disconnectBleDevice();
                }

            } else {
                updateStatus("Service discovery failed: " + status);
                Log.w(TAG, "onServicesDiscovered received: " + status);
                disconnectBleDevice();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            // This is called when the sensor sends new data (notification)
            super.onCharacteristicChanged(gatt, characteristic, value);
            if (CSC_MEASUREMENT_CHAR_UUID.equals(characteristic.getUuid())) {
                final int currentCadenceRPM = cadenceParser.parseAndCalculateCadence(value);

                // Update UI on the main thread
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (currentCadenceRPM >= 0) { // Parser returns 0 or positive for valid RPM
                            cadenceValueTextView.setText("Cadence: " + currentCadenceRPM + " RPM");
                            statusTextView.setText("Received Cadence Data.");
                        } else { // Handle cases where parser returns a special value (e.g., -1 for no crank data)
                            cadenceValueTextView.setText("Cadence: -- RPM");
                            statusTextView.setText("Received Incomplete Cadence Data.");
                        }
                    }
                });
            }
        }
    };

    /**
     * Disconnects from the current BLE device and closes the GATT client.
     */
    private void disconnectBleDevice() {
        if (bluetoothGatt == null) {
            updateStatus("Not connected to any BLE device.");
            return;
        }
        // User initiated disconnect, so stop auto-reconnect
        shouldAttemptReconnect = false;
        cancelReconnectAttempts();

        // Ensure BLUETOOTH_CONNECT permission is checked
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt.disconnect(); // This will trigger onConnectionStateChange with STATE_DISCONNECTED
        } else {
            Toast.makeText(this, "BLUETOOTH_CONNECT permission needed to disconnect.", Toast.LENGTH_SHORT).show();
            requestBluetoothPermissions();
        }
        if (cadenceParser != null) {
            cadenceParser.reset();
        }
        updateStatus("Disconnecting...");
    }

    /**
     * Closes the BluetoothGatt client. Should be called after disconnect.
     */
    private void closeGatt() {
        if (bluetoothGatt == null) {
            return;
        }
        // ensure BLUETOOTH_CONNECT permission is checked
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt.close();
        }
        if (cadenceParser != null) {
            cadenceParser.reset();
        }
        bluetoothGatt = null;
        selectedDevice = null;
        // Also ensure parser is reset
        if (cadenceParser != null) { cadenceParser.reset(); }
        updateStatus("Connection closed.");
    }

    /**
     * Cancels any pending scheduled reconnect attempts.
     */
    private void cancelReconnectAttempts() {
        reconnectHandler.removeCallbacks(reconnectRunnable);
        reconnectAttemptCount = 0; // Reset counter
        Log.d(TAG, "Reconnect attempts canceled.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBleScan(); // Stop any ongoing scan
        cancelReconnectAttempts(); // Cancel any pending reconnects
        disconnectBleDevice(); // Disconnect if still connected
        closeGatt(); // Close GATT client

        // Remove callbacks to prevent memory leaks
        scanHandler.removeCallbacksAndMessages(null);
        reconnectHandler.removeCallbacksAndMessages(null);
    }
}