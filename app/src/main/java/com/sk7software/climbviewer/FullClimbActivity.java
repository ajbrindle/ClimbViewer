package com.sk7software.climbviewer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.ClimbView;
import com.sk7software.climbviewer.view.DisplayFormatter;
import com.sk7software.climbviewer.view.ScreenController;

import java.util.Date;

public class FullClimbActivity extends AppCompatActivity implements ActivityUpdateInterface {

    private LocationMonitor monitor;
    private ClimbView elevationView;
    private TextView gradientNow;
    private TextView distToGo;
    private TextView gradientNext;
    private TextView totDistToGo;
    private long loadTime;
    private boolean heightSet;

    private static final String TAG = FullClimbActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_climb);
        getSupportActionBar().hide();

        gradientNow = (TextView)findViewById(R.id.txtGradientNow);
        gradientNext = (TextView)findViewById(R.id.txtGradientNext);
        distToGo = (TextView)findViewById(R.id.txtRemaining);
        totDistToGo = (TextView)findViewById(R.id.txtTotRemaining);

        monitor = new LocationMonitor(this);

        int numClimbPoints = ClimbController.getInstance().getClimb().getPoints().size();
        DisplayFormatter.setDistanceText(ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart(),
                "km", totDistToGo, true);

        elevationView = (ClimbView) findViewById(R.id.elevationView);
        elevationView.setClimb(ClimbController.getInstance().getClimb(), true);
        elevationView.addPlot(ClimbController.PointType.ATTEMPT);

        if (ClimbController.getInstance().getAttempts().get(ClimbController.PointType.PB) != null) {
            elevationView.addPlot(ClimbController.PointType.PB);
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        loadTime = new Date().getTime();
        heightSet = false;

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
            if (!heightSet) {
                setClimbViewHeight();
            }

            if (!elevationView.isInitialised()) {
                return;
            }

            elevationView.startUpdating();
            elevationView.invalidate();

            DisplayFormatter.setGradientText(ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getCurrentGradient(), gradientNow);
            DisplayFormatter.setGradientText(ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getNextGradient(), gradientNext);
            DisplayFormatter.setDistanceText(ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getSegmentToGo(), "m", distToGo, true);

            int numClimbPoints = ClimbController.getInstance().getClimb().getPoints().size();
            DisplayFormatter.setDistanceText(ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart() -
                    ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getDist(), "km", totDistToGo, true);

            long now = new Date().getTime();
            if (now - loadTime > ClimbController.DISPLAY_INTERVAL) {
                // Check next screen
                Intent i = ScreenController.getInstance().getNextIntent(this.getClass());
                if (i != null) {
                    startActivity(i);
                } else {
                    // Advance load time so preference checks are not repeated
                    loadTime += 600000;
                }
            }
        } else {
            // Return to home screen or route screen
            Intent i = ScreenController.getInstance().getNextIntent(this.getClass());
            if (i != null) {
                startActivity(i);
            }
            return;
        }
    }

    private void setClimbViewHeight() {
        WindowManager wm = (WindowManager) ApplicationContextProvider.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        LinearLayout panel = (LinearLayout)findViewById(R.id.displayPanel);

        int s=0;
        int resource = ApplicationContextProvider.getContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resource > 0) {
            s = ApplicationContextProvider.getContext().getResources().getDimensionPixelSize(resource);
        }

        Log.d(TAG, "Setting climb view height: " + (size.y - panel.getHeight() - s) + "/" + size.y + " (" + s + ")");
        elevationView.setHeight(size.y - panel.getHeight() - s, true);
        heightSet = true;
    }

    @Override
    public void setProgress(boolean showProgressDialog, String progressMessage) {}
}