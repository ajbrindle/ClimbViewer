package com.sk7software.climbviewer;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.DisplayFormatter;
import com.sk7software.climbviewer.view.ScreenController;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DecimalFormat;
import java.util.Date;

public class MapActivity extends AppCompatActivity implements ActivityUpdateInterface {

    private MapFragment map;
    private int climbId;
    private GPXRoute climb;
    private LocationMonitor monitor;
    private TextView totalDist;
    private TextView distToGo;
    private long loadTime;

    private static final String TAG = MapActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        getSupportActionBar().hide();

        totalDist = (TextView)findViewById(R.id.txtTotalDist);
        distToGo = (TextView)findViewById(R.id.txtToGoDist);

        int numClimbPoints = ClimbController.getInstance().getClimb().getPoints().size();
        DisplayFormatter.setDistanceText(ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart(),
                                        "km", totalDist, true);

        monitor = new LocationMonitor(this);
        map = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.climbMap);
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL, MapFragment.PlotType.FULL_CLIMB, false);
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
        if (!ClimbController.getInstance().isAttemptInProgress()) {
            Intent i = ScreenController.getInstance().getNextIntent(this.getClass());
            if (i != null) {
                startActivity(i);
            }
            return;
        }

        int numClimbPoints = ClimbController.getInstance().getClimb().getPoints().size();
        DisplayFormatter.setDistanceText(ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart() -
                                ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getDist(), "km", distToGo, true);

        // Add markers
        if (ClimbController.getInstance().getAttempts().get(ClimbController.PointType.PB) != null) {
            map.addMarker(new LatLng(ClimbController.getInstance().getAttempts().get(ClimbController.PointType.PB).getSnappedPosition().getLat(),
                            ClimbController.getInstance().getAttempts().get(ClimbController.PointType.PB).getSnappedPosition().getLon()),
                          ClimbController.PointType.PB, Color.GREEN, false);
        }

        LatLng snappedPoint = new LatLng(ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getSnappedPosition().getLat(),
                ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ATTEMPT).getSnappedPosition().getLon());
        map.addMarker(snappedPoint, ClimbController.PointType.ATTEMPT, Color.CYAN, false);
        map.moveCamera(point, false);

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
    }

    @Override
    public void setProgress(boolean showProgressDialog, String progressMessage) {}
}