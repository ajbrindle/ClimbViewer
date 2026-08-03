package com.sk7software.climbviewer;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
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
import com.sk7software.climbviewer.device.BTCadenceController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BTCadenceActivity extends AppCompatActivity implements ActivityUpdateInterface {

    private static final String TAG = BTCadenceActivity.class.getSimpleName();

    // Request Codes
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;

    // Bluetooth components
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

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

    // Handler for stopping scan after a delay
    private Handler scanHandler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;
    private static final long SCAN_PERIOD = 10000; // Scan for 10 seconds

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
                BTCadenceController.getInstance().cleanup();
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
                BTCadenceController.getInstance().cleanup();
            }
        });

        // --- Set up ListView Item Click Listener ---
        devicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Get the device from the map based on the selected item's address
                String selectedDeviceInfo = (String) parent.getItemAtPosition(position);
                String deviceAddress = selectedDeviceInfo.substring(selectedDeviceInfo.length() - 17); // Extract MAC address (last 17 chars)

                BluetoothDevice selectedDevice = discoveredDevicesMap.get(deviceAddress);

                if (selectedDevice != null) {
                    stopBleScan(); // Stop scanning once a device is selected
                    connectToDevice(selectedDevice);
                }
            }
        });

        // Initial permission request and Bluetooth status check
        requestBluetoothPermissions();

        if (BTCadenceController.getSelectedDeviceName() != null && BTCadenceController.getSelectedDeviceAddr() != null) {
            connectToDevice(BTCadenceController.getSelectedDeviceName(), BTCadenceController.getSelectedDeviceAddr());
        }
    }

    private void connectToDevice(BluetoothDevice selectedDevice) {
        if (selectedDevice == null) {
            return;
        }

        if (ActivityCompat.checkSelfPermission(BTCadenceActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(BTCadenceActivity.this, "BLUETOOTH_CONNECT permission needed to connect.", Toast.LENGTH_SHORT).show();
            requestBluetoothPermissions();
            return;
        }
        connectToDevice(selectedDevice.getName(), selectedDevice.getAddress());
    }

    private void connectToDevice(String deviceName, String deviceAddress) {
        Log.d(TAG, "Connecting to device: " + deviceName + " (" + deviceAddress + ")");
        Toast.makeText(BTCadenceActivity.this, "Selected: " + deviceName + " (" + deviceAddress + ")", Toast.LENGTH_SHORT).show();
        updateStatus("Connecting to " + deviceName + "...");

        BTCadenceController btController = BTCadenceController.getInstance();
        if (btController.isAvailable()) {
            Preferences.getInstance().addPreference(Preferences.PREFERENCE_SELECTED_BLE_DEVICE_ADDRESS, deviceAddress);
            Preferences.getInstance().addPreference(Preferences.PREFERENCE_SELECTED_BLE_DEVICE_NAME, deviceName);
            if (!btController.connectToMacAddress(BTCadenceActivity.this, deviceAddress, BTCadenceActivity.this)) {
                Toast.makeText(BTCadenceActivity.this, "Failed to connect to device.", Toast.LENGTH_SHORT).show();
                updateStatus("Failed to connect to " + deviceName);
            } else {
                updateStatus("Connected to " + deviceName + "...");
            }
        }
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
        BTCadenceController.getInstance().cleanup();

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


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBleScan(); // Stop any ongoing scan
        scanHandler.removeCallbacksAndMessages(null);
        BTCadenceController.getInstance().cleanup();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public void updateDeviceData(int value) {
        runOnUiThread(() -> {
            cadenceValueTextView.setText("Cadence: " + (value >= 0 ? value : "--") + " RPM");
        });
    }
}