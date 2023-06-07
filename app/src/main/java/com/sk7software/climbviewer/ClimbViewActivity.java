package com.sk7software.climbviewer;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.model.AttemptPoint;
import com.sk7software.climbviewer.model.AttemptStats;
import com.sk7software.climbviewer.model.ClimbAttempt;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.ClimbView;
import com.sk7software.climbviewer.view.DisplayFormatter;
import com.sk7software.climbviewer.view.TrackView;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public class ClimbViewActivity extends AppCompatActivity implements DrawableUpdateInterface {

    private ClimbView elevationView;
    private MapFragment map;
    private int climbId;
    private GPXRoute climb;
    private boolean infoShown;

    private static final String TAG = ClimbViewActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_climb_view);
        getSupportActionBar().hide();

        infoShown = false;
        climbId = getIntent().getIntExtra("id", 0);
        climb = Database.getInstance().getClimb(climbId);
        ClimbController.getInstance().loadClimb(climb);

        elevationView = findViewById(R.id.elevationView);
        elevationView.setParent(this);
        elevationView.setClimb(climb, 20);
        setClimbViewHeight();
        elevationView.invalidate();

        elevationView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN || motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                    elevationView.setShowGradientAt((int)motionEvent.getX());
                    LatLng ll = elevationView.getLatLongAtX((int)motionEvent.getX());
                    if (ll != null) {
                        map.showPosition(ll);
                    }
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    elevationView.setShowGradientAt(-1);
                }
                elevationView.invalidate();
                return true;
            }
        });

        SeekBar smoothDist = (SeekBar)findViewById(R.id.viewSmooth);

        int smoothDistProgress = climb.getSmoothDist();
        if (smoothDistProgress == 0) {
            smoothDistProgress = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_SMOOTH_DIST, 50);
        }
        smoothDist.setProgress(smoothDistProgress);
        smoothDist.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                Database.getInstance().updateSmoothDist(climbId, i);
                climb.setSmoothDist(i);
                elevationView.setClimb(climb, 20);
                elevationView.clearPoints();
                setClimbViewHeight();
                elevationView.invalidate();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        map = (MapFragment)getSupportFragmentManager().findFragmentById(R.id.mapView);
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL, MapFragment.PlotType.NORMAL, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void updateAfterDraw(boolean resizeable) {
        displayClimbInfo();
    }

    private void displayClimbInfo() {
        if (infoShown) return;

        if (climb != null) {
            TextView txtDist = findViewById(R.id.txtSegmentDist);
            int numClimbPoints = ClimbController.getInstance().getClimb().getPoints().size();
            DisplayFormatter.setDistanceText(ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart(),
                    "km", txtDist, true);

            TextView txtAverageGradient = findViewById(R.id.txtAverageGradient);
            TextView txtMaxGradient = findViewById(R.id.txtMaxGradient);
            TextView txtElevationChange = findViewById(R.id.txtElevationChange);
            TextView txtRating = findViewById(R.id.txtRating);

            double maxElevation = Double.MIN_VALUE;
            double minElevation = Double.MAX_VALUE;
            RoutePoint lastPoint = null;
            for (RoutePoint p : ClimbController.getInstance().getClimb().getPoints()) {
                if (p.getElevation() < minElevation) {
                    minElevation = p.getElevation();
                }
                if (p.getElevation() > maxElevation) {
                    maxElevation = p.getElevation();
                }
            }

            double elevationChange = maxElevation - minElevation;
            double averageGradient = elevationChange / ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart();
            long rating = ClimbController.getInstance().getClimb().calcRating();
                    //(long)(averageGradient * 100 * (ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart()));

            DisplayFormatter.setDistanceText((float)elevationChange,"m", txtElevationChange, true);
            DisplayFormatter.setGradientText((float)averageGradient*100, txtAverageGradient);
            DisplayFormatter.setGradientText((float)elevationView.getMaxSmoothedGradient(), txtMaxGradient);
            txtRating.setText(String.valueOf(rating));
        }

        ClimbAttempt pb = Database.getInstance().getClimbTime(climbId, true);
        TextView txtPB = findViewById(R.id.txtPB);
        setTimeText(pb, txtPB);

        ClimbAttempt last = Database.getInstance().getClimbTime(climbId, false);
        TextView txtLastAttempt = findViewById(R.id.txtLastAttempt);
        setTimeText(last, txtLastAttempt);

        // Display number of attempts
        AttemptStats attempts = Database.getInstance().getLastAttempt(climbId);
        TextView txtAttempts = findViewById(R.id.txtAttempts);
        if (attempts != null) {
            txtAttempts.setText(String.valueOf(attempts.getTotal()));
        } else {
            txtAttempts.setText("0");
        }
        infoShown = true;
    }

    private void setTimeText(ClimbAttempt attempt, TextView txtTime) {
        if (attempt != null && attempt.getPoints() != null && attempt.getPoints().size() > 0) {
            int attMins = attempt.getDuration() / 60;
            int attSecs = attempt.getDuration() % 60;
            txtTime.setText(attMins + ":" + String.format("%02d", attSecs) + "s");
        } else {
            txtTime.setText("-:--s");
        }
    }

    private void setClimbViewHeight() {
        WindowManager wm = (WindowManager) ApplicationContextProvider.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        LinearLayout panel = findViewById(R.id.panel);

        int s=0;
        int resource = ApplicationContextProvider.getContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resource > 0) {
            s = ApplicationContextProvider.getContext().getResources().getDimensionPixelSize(resource);
        }

        // Set height to 1/2 screen
        int height = (size.y - s) / 2;
        Log.d(TAG, "Setting climb view height: " + height + "/" + size.y + " (" + s + ")");
        elevationView.setHeight(height, false);
    }
}