package com.sk7software.climbviewer.device;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.sk7software.climbviewer.ActivityUpdateInterface;
import com.sk7software.climbviewer.ApplicationContextProvider;
import com.sk7software.climbviewer.db.Preferences;

import java.util.UUID;

public class BTCadenceController {
    private static BTCadenceController INSTANCE = null;
    private static final String TAG = BTCadenceController.class.getSimpleName();
    private boolean available = false;
    private boolean connected = false;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice selectedDevice;
    private CadenceMeasurementParser cadenceParser;
    private ActivityUpdateInterface activity;
    private Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private Runnable reconnectRunnable;
    private boolean shouldAttemptReconnect = false; // Flag to control auto-reconnect
    private int reconnectAttemptCount = 0;
    private int cadenceRPM = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = -1;
    private static final long RECONNECT_DELAY_MS = 3000;
    private static final UUID CSC_SERVICE_UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb");
    // CSC Measurement Characteristic (contains speed and cadence data)
    private static final UUID CSC_MEASUREMENT_CHAR_UUID = UUID.fromString("00002A5B-0000-1000-8000-00805f9b34fb");
    // Client Characteristic Configuration Descriptor (CCCD) - used to enable notifications
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static BTCadenceController getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BTCadenceController();
        }
        return INSTANCE;
    }

    private BTCadenceController() {
        bluetoothManager = (BluetoothManager) ApplicationContextProvider.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                available = false;
            }
        } else {
            available = false;
        }
        cadenceParser = new CadenceMeasurementParser();
        available = true;
    }

    public boolean isAvailable() {
        return available;
    }

    public static String getSelectedDeviceName() {
        String deviceName = Preferences.getInstance().getStringPreference(Preferences.PREFERENCE_SELECTED_BLE_DEVICE_NAME);
        if (deviceName == null || deviceName.isEmpty()) {
            return null;
        }
        return deviceName;
    }
    public static String getSelectedDeviceAddr() {
        String deviceAddr = Preferences.getInstance().getStringPreference(Preferences.PREFERENCE_SELECTED_BLE_DEVICE_ADDRESS);
        if (deviceAddr == null || deviceAddr.isEmpty()) {
            return null;
        }
        return deviceAddr;
    }

    public boolean connectToMacAddress(Context context, String macAddress, ActivityUpdateInterface activity) throws SecurityException {
        this.activity = activity;

        // Validate MAC address format
        if (!BluetoothAdapter.checkBluetoothAddress(macAddress)) {
            Log.e(TAG, "Invalid MAC Address format: " + macAddress);
            return false;
        }

        // If a GATT connection is already active for this device, reuse it
        if (selectedDevice != null && selectedDevice.getAddress().equals(macAddress) && bluetoothGatt != null) {
            Log.d(TAG, "Attempting to reconnect to existing GATT connection for: " + macAddress);
            shouldAttemptReconnect = true;
            return bluetoothGatt.connect();
        }

        // Close any existing GATT connection before attempting a new one
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        // Get BluetoothDevice object from MAC address
        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
        if (device == null) {
            Log.e(TAG, "Device not found for MAC address: " + macAddress);
            return false;
        }

        // Store the device we are connecting to
        selectedDevice = device;

        // Connect to the GATT server hosted by the BLE device
        // autoConnect = true: The system will automatically reconnect if connection is lost.
        // This is generally preferred for long-term, background connections.
        Log.d(TAG, "Connecting to GATT server for MAC: " + macAddress + " with autoConnect=true");
        bluetoothGatt = device.connectGatt(context, true, gattCallback);
        shouldAttemptReconnect = true;

        reconnectRunnable = new Runnable() {
            @Override
            public void run() {
                if (shouldAttemptReconnect && selectedDevice != null && bluetoothGatt != null) {
                    if (MAX_RECONNECT_ATTEMPTS < 0 || reconnectAttemptCount < MAX_RECONNECT_ATTEMPTS) {
                        reconnectAttemptCount++;
                        try {
                            Log.d(TAG, "Attempting to reconnect to device (Attempt " + reconnectAttemptCount + ")");
                            bluetoothGatt.connect();
                            reconnectHandler.postDelayed(this, RECONNECT_DELAY_MS); // Schedule next attempt
                        } catch (SecurityException e) {
                            Log.e(TAG, "SecurityException during reconnect: " + e.getMessage());
                            shouldAttemptReconnect = false;
                            closeGatt();
                        }
                    } else {
                        Log.w(TAG, "Max reconnect attempts reached. Stopping auto-reconnect.");
                        shouldAttemptReconnect = false;
                        closeGatt();
                    }
                }
            }
        };
        return true;
    }

    // BluetoothGattCallback for managing GATT interactions
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Device connected, discover services
                Log.d(TAG, "Connected to GATT server.");
                // Reset reconnect attempts on successful connection
                cancelReconnectAttempts();
                connected = true;

                try {
                    gatt.discoverServices();
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException during service discovery: " + e.getMessage());
                    Toast.makeText(ApplicationContextProvider.getContext(), "Failed to discover services. Please check permissions.", Toast.LENGTH_LONG).show();
                    closeGatt();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Device disconnected
                Log.d(TAG, "Disconnected from GATT server.");
                connected = false;

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
                Log.d(TAG, "Services discovered.");

                BluetoothGattService cscService = gatt.getService(CSC_SERVICE_UUID);
                if (cscService == null) {
                    Log.e(TAG, "CSC Service not found!");
                    disconnectBleDevice();
                    return;
                }

                BluetoothGattCharacteristic cscMeasurementChar = cscService.getCharacteristic(CSC_MEASUREMENT_CHAR_UUID);
                if (cscMeasurementChar == null) {
                    Log.e(TAG, "CSC Measurement Characteristic not found!");
                    disconnectBleDevice();
                    return;
                }

                // Enable notifications for the CSC Measurement Characteristic
                // ensure BLUETOOTH_CONNECT permission is checked
                try {
                    gatt.setCharacteristicNotification(cscMeasurementChar, true);

                    BluetoothGattDescriptor descriptor = cscMeasurementChar.getDescriptor(CCCD_UUID);
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor); // Write to descriptor to enable notifications
                        Log.d(TAG, "Successfully set notification for CSC Measurement Characteristic.");
                    } else {
                        Log.e(TAG, "CCCD Descriptor not found for CSC Measurement Characteristic!");
                        disconnectBleDevice();
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "SecurityException while setting notifications: " + e.getMessage());
                    disconnectBleDevice();
                }
            } else {
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
                            //Log.d(TAG, "Cadence: " + currentCadenceRPM + " RPM");
                            cadenceRPM = currentCadenceRPM;
                            if (activity != null) {
                                activity.updateDeviceData(currentCadenceRPM);
                            }
                        } else { // Handle cases where parser returns a special value (e.g., -1 for no crank data)
                            //Log.d(TAG, "Cadence: -- RPM");
                            cadenceRPM = -1;
                            if (activity != null) {
                                activity.updateDeviceData(-1);
                            }
                        }
                    }
                });
            }
        }
    };

    public int getCadenceRPM() {
        return cadenceRPM;
    }

    public void cleanup() {
        try {
            Log.d(TAG, "Cleaning up BTCadenceController resources.");
            disconnectBleDevice();
            selectedDevice = null;
            reconnectAttemptCount = 0;
            connected = false;
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException during cleanup: " + e.getMessage());
        }
    }

    private void disconnectBleDevice() {
        if (bluetoothGatt == null) {
            return;
        }
        // User initiated disconnect, so stop auto-reconnect
        shouldAttemptReconnect = false;
        cancelReconnectAttempts();
        closeGatt();
    }

    private void cancelReconnectAttempts() {
        if (reconnectHandler == null) {
            return;
        }
        reconnectHandler.removeCallbacks(reconnectRunnable);
        reconnectHandler.removeCallbacksAndMessages(null);
        reconnectAttemptCount = 0;
        Log.d(TAG, "Reconnect attempts canceled.");
    }

    private void closeGatt() {
        if (bluetoothGatt == null) {
            return;
        }
        try {
            bluetoothGatt.close();
        } catch (SecurityException e) {
            Log.e(TAG, "Error closing GATT: " + e.getMessage());
        }
        bluetoothGatt = null;
        selectedDevice = null;
    }

    public void reset(final String macAddress, final AppCompatActivity c, final ActivityUpdateInterface a) {
        BTCadenceController.getInstance().cleanup();
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Allow time for cleanup to happen
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {}
                BTCadenceController.getInstance().connectToMacAddress(c, macAddress, a);
            }
        }).start();

    }
}