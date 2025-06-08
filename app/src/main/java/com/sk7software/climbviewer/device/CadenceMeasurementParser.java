package com.sk7software.climbviewer.device;

import android.util.Log;

public class CadenceMeasurementParser {

    private final String TAG = CadenceMeasurementParser.class.getSimpleName();

    // Store previous values to calculate delta
    private long previousCumulativeCrankRevolutions = -1;
    private long previousLastCrankEventTime = -1; // in 1/1024 seconds

    private long lastCalculatedTimestamp = -1; // Epoch time when last RPM was calculated

    public CadenceMeasurementParser() {
        // Constructor
    }

    /**
     * Parses the raw CSC Measurement characteristic data and calculates the current cadence (RPM).
     *
     * @param value The byte array received from the CSC Measurement characteristic.
     * @return The calculated cadence in RPM, or 0 if data is insufficient/invalid, or -1 if no crank data.
     */
    public int parseAndCalculateCadence(byte[] value) {
        if (value == null || value.length < 1) {
            Log.w(TAG, "Empty or too short characteristic value.");
            return 0; // Or indicate an error
        }

        int flags = value[0] & 0xFF;
        boolean crankRevolutionPresent = (flags & 0x02) > 0; // Bit 1 indicates Crank Revolution Data Present

        if (!crankRevolutionPresent) {
            Log.d(TAG, "Crank Revolution data not present in this CSC Measurement.");
            // Reset previous values if no crank data is present in a consecutive packet,
            // or handle as appropriate for your application.
            // For now, return -1 to indicate no crank data.
            previousCumulativeCrankRevolutions = -1;
            previousLastCrankEventTime = -1;
            return 0; // Return 0 RPM if no crank data, or -1 to signal its absence
        }

        // Current parsing offset
        int offset = 1; // Start after flags byte

        // Skip Wheel Revolution Data if present (Flags Bit 0)
        boolean wheelRevolutionPresent = (flags & 0x01) > 0;
        if (wheelRevolutionPresent) {
            // Cumulative Wheel Revolutions (4 bytes, Little Endian)
            // int cumulativeWheelRevolutions = (value[offset + 3] & 0xFF) << 24 | (value[offset + 2] & 0xFF) << 16 | (value[offset + 1] & 0xFF) << 8 | (value[offset] & 0xFF);
            offset += 4;
            // Last Wheel Event Time (2 bytes, Little Endian, in 1/1024 s)
            // int lastWheelEventTime = (value[offset + 1] & 0xFF) << 8 | (value[offset] & 0xFF);
            offset += 2;
        }

        // Check if array has enough bytes for crank data after skipping wheel data (if present)
        if (value.length < offset + 4) { // Need 2 bytes for revolutions, 2 for time
            Log.w(TAG, "Characteristic value too short for crank data after flags/wheel data.");
            return 0;
        }


        // Cumulative Crank Revolutions (2 bytes, Little Endian)
        long currentCumulativeCrankRevolutions = (value[offset + 1] & 0xFF) << 8 | (value[offset] & 0xFF);
        offset += 2;

        // Last Crank Event Time (2 bytes, Little Endian, in 1/1024 s)
        long currentLastCrankEventTime = (value[offset + 1] & 0xFF) << 8 | (value[offset] & 0xFF);
        // offset += 2; // Not strictly needed as we're at the end for cadence

        // --- Calculate Delta and Handle Rollover ---
        long deltaRevolutions;
        long deltaTimeUnits; // in 1/1024 seconds

        if (previousCumulativeCrankRevolutions == -1 || previousLastCrankEventTime == -1) {
            // First data packet or previous data was invalid/reset
            Log.d(TAG, "Initializing previous values.");
            previousCumulativeCrankRevolutions = currentCumulativeCrankRevolutions;
            previousLastCrankEventTime = currentLastCrankEventTime;
            lastCalculatedTimestamp = System.currentTimeMillis();
            return 0; // Can't calculate RPM from the first data point
        }

        // Calculate delta revolutions, handling 16-bit rollover (max 65535)
        if (currentCumulativeCrankRevolutions < previousCumulativeCrankRevolutions) {
            // Rollover occurred
            deltaRevolutions = (65535 - previousCumulativeCrankRevolutions) + currentCumulativeCrankRevolutions + 1;
        } else {
            deltaRevolutions = currentCumulativeCrankRevolutions - previousCumulativeCrankRevolutions;
        }

        // Calculate delta time, handling 16-bit rollover (max 65535 * 1/1024s ~ 64 seconds)
        if (currentLastCrankEventTime < previousLastCrankEventTime) {
            // Rollover occurred
            deltaTimeUnits = (65535 - previousLastCrankEventTime) + currentLastCrankEventTime + 1;
        } else {
            deltaTimeUnits = currentLastCrankEventTime - previousLastCrankEventTime;
        }

        // Update previous values for the next calculation
        previousCumulativeCrankRevolutions = currentCumulativeCrankRevolutions;
        previousLastCrankEventTime = currentLastCrankEventTime;

        // Calculate RPM
        double deltaTimeSeconds = deltaTimeUnits / 1024.0; // Convert 1/1024s to seconds

        int cadenceRPM = 0;
        if (deltaTimeSeconds > 0 && deltaRevolutions > 0) {
            cadenceRPM = (int) Math.round((deltaRevolutions / deltaTimeSeconds) * 60.0);
            lastCalculatedTimestamp = System.currentTimeMillis();
            Log.d(TAG, String.format("RPM Calculated: %d (Delta Revolutions: %d, Delta Time: %.3f s)", cadenceRPM, deltaRevolutions, deltaTimeSeconds));
        } else if (deltaTimeSeconds > 0 && deltaRevolutions == 0) {
            // If time passed but no revolutions, cadence is 0
            cadenceRPM = 0;
            Log.d(TAG, "RPM is 0: No revolutions in delta time.");
        } else {
            // Should ideally not happen if sensor sends proper updates
            Log.w(TAG, "Invalid delta time or revolutions: " + deltaTimeSeconds + "s, " + deltaRevolutions + " revs");
        }

        return cadenceRPM;
    }

    /**
     * Resets the parser's internal state. Call this when disconnecting or restarting.
     */
    public void reset() {
        previousCumulativeCrankRevolutions = -1;
        previousLastCrankEventTime = -1;
        lastCalculatedTimestamp = -1;
        Log.d(TAG, "Cadence parser state reset.");
    }
}
