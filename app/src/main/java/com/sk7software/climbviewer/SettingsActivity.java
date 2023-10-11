package com.sk7software.climbviewer;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.sk7software.climbviewer.db.Preferences;

public class SettingsActivity extends AppCompatActivity {

    private static final String SMOOTH_LABEL = "Profile Smooth Distance: ";
    private static final String DELAY_LABEL = "Time Between Screens: ";
    private static final String WARN_LABEL = "Warn About Upcoming Climbs: ";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialise seek bar locations
        int delay = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_SCREEN_DELAY_S, 15);
        int smooth = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_SMOOTH_DIST, 50);
        boolean auto = Preferences.getInstance().getBooleanPreference(Preferences.PREFERENCES_AUTO_MONITOR_CLIMBS, true);
        boolean ultra = Preferences.getInstance().getBooleanPreference(Preferences.PREFERENCES_CLIMB_ULTRA_TOLERANCE, false);
        int warn = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_CLIMB_WARNING, 1000);
        boolean sortRating = Preferences.getInstance().getBooleanPreference(Preferences.PREFERENCES_CLIMB_SORT_RATING, false);

        SeekBar smoothDist = findViewById(R.id.seekSmoothDistance);
        SeekBar delayS = findViewById(R.id.seekScreenDelay);
        SeekBar climbWarn = findViewById(R.id.seekClimbWarn);
        SwitchMaterial autoMonitor = findViewById(R.id.swiAutoMonitor);
        SwitchMaterial ultraTolerance = findViewById(R.id.swiClimbTolerance);
        SwitchMaterial climbSort = findViewById(R.id.swiClimbSort);

        TextView smoothLabel = findViewById(R.id.txtSmoothDistance);
        TextView delayLabel = findViewById(R.id.txtScreenDelay);
        TextView warnLabel = findViewById(R.id.txtClimbWarn);

        smoothLabel.setText(SMOOTH_LABEL + smooth + "m");
        delayLabel.setText(DELAY_LABEL + delay + "s");
        warnLabel.setText(WARN_LABEL + warn + "m");

        smoothDist.setProgress(smooth);
        smoothDist.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                smoothLabel.setText(SMOOTH_LABEL + i + "m");
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_SMOOTH_DIST, i);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        delayS.setProgress(delay);
        delayS.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                delayLabel.setText(DELAY_LABEL + i + "s");
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_SCREEN_DELAY_S, i);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }

        });

        climbWarn.setProgress(warn);
        climbWarn.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                warnLabel.setText(WARN_LABEL + i + "m");
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_CLIMB_WARNING, i);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }

        });

        autoMonitor.setChecked(auto);
        autoMonitor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_AUTO_MONITOR_CLIMBS, isChecked);
            }
        });

        ultraTolerance.setChecked(ultra);
        ultraTolerance.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_CLIMB_ULTRA_TOLERANCE, isChecked);
            }
        });

        climbSort.setChecked(sortRating);
        climbSort.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_CLIMB_SORT_RATING, isChecked);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}