package com.sk7software.climbviewer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.ClimbView;
import com.sk7software.climbviewer.view.DisplayFormatter;

import org.w3c.dom.Text;

import java.time.LocalDateTime;
import java.util.Date;

public class FullClimbActivity extends AppCompatActivity implements ActivityUpdateInterface {

    private LocationMonitor monitor;
    private ClimbView elevationView;
    private TextView gradientNow;
    private TextView distToGo;
    private TextView gradientNext;
    private TextView totDistToGo;
    private long loadTime;

    private static final String TAG = FullClimbActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_climb);

        gradientNow = (TextView)findViewById(R.id.txtGradientNow);
        gradientNext = (TextView)findViewById(R.id.txtGradientNext);
        distToGo = (TextView)findViewById(R.id.txtRemaining);
        totDistToGo = (TextView)findViewById(R.id.txtTotRemaining);

        monitor = new LocationMonitor(this);

        int numClimbPoints = ClimbController.getInstance().getClimb().getPoints().size();
        DisplayFormatter.setDistanceText(ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart(),
                "km", totDistToGo, true);

        elevationView = (ClimbView) findViewById(R.id.elevationView);
        elevationView.setClimb(ClimbController.getInstance().getClimb(), 300);
        elevationView.setPB(ClimbController.getInstance().getPbAttempt());
        elevationView.invalidate();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        loadTime = new Date().getTime();
        if (monitor != null && !monitor.isListenerRunning()) {
            monitor.resumeListener();
        }
        super.onResume();
    }

    @Override
    protected void onStop() {
        if (monitor.isListenerRunning()) {
            monitor.stopListener();
        }
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed");
        Intent i = new Intent(ApplicationContextProvider.getContext(), ClimbChooserActivity.class);
        startActivity(i);
    }

    @Override
    public void locationChanged(RoutePoint point) {
        if (ClimbController.getInstance().isAttemptInProgress()) {
            elevationView.pbLocation(ClimbController.getInstance().getPbPoint());
            elevationView.plotLocation(point);
            elevationView.invalidate();

            DisplayFormatter.setGradientText(ClimbController.getInstance().getCurrentGradient(), gradientNow);
            DisplayFormatter.setGradientText(ClimbController.getInstance().getNextGradient(), gradientNext);
            DisplayFormatter.setDistanceText(ClimbController.getInstance().getSegmentToGo(), "m", distToGo, true);

            int numClimbPoints = ClimbController.getInstance().getClimb().getPoints().size();
            DisplayFormatter.setDistanceText(ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart() -
                    ClimbController.getInstance().getAttemptDist(), "km", totDistToGo, true);

            long now = new Date().getTime();
            if (now - loadTime > ClimbController.DISPLAY_INTERVAL) {
                // Check next screen
                if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_PURSUIT)) {
                    Intent i = new Intent(ApplicationContextProvider.getContext(), PursuitActivity.class);
                    startActivity(i);
                } else if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_2D)) {
                    Intent i = new Intent(ApplicationContextProvider.getContext(), MapActivity.class);
                    startActivity(i);
                } else {
                    // Advance load time so preference checks are not repeated
                    loadTime += 600000;
                }
            }
        }
    }

    @Override
    public void setProgress(boolean showProgressDialog, String progressMessage) {}
}