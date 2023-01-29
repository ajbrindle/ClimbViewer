package com.sk7software.climbviewer;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.TextView;

import com.sk7software.climbviewer.db.Preferences;

import java.nio.channels.SelectionKey;

public class SettingsActivity extends AppCompatActivity {

    private static final String SMOOTH_LABEL = "Profile Smooth Distance: ";
    private static final String DELAY_LABEL = "Time Between Screens: ";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialise seek bar locations
        int delay = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_SCREEN_DELAY_S, 15);
        int smooth = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_SMOOTH_DIST, 50);

        SeekBar smoothDist = (SeekBar)findViewById(R.id.seekSmoothDistance);
        SeekBar delayS = (SeekBar)findViewById(R.id.seekScreenDelay);

        TextView smoothLabel = (TextView)findViewById(R.id.txtSmoothDistance);
        TextView delayLabel = (TextView)findViewById(R.id.txtScreenDelay);

        smoothLabel.setText(SMOOTH_LABEL + smooth + "m");
        delayLabel.setText(DELAY_LABEL + delay + "s");

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