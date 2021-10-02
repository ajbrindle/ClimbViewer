package com.sk7software.climbviewer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.DisplayFormatter;

import java.util.Date;
import java.util.Map;

public class PursuitActivity extends AppCompatActivity implements ActivityUpdateInterface {

    private MapFragment map;
    private MapFragment mirror;
    private int climbId;
    private GPXRoute climb;
    private LocationMonitor monitor;
    private TextView distBehind;
    private TextView timeBehind;
    private long loadTime;
    private LinearLayout panel;
    private boolean paused = false;

    private static final String TAG = MapActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pursuit);

        distBehind = (TextView)findViewById(R.id.txtDistBehind);
        timeBehind = (TextView)findViewById(R.id.txtTimeBehind);

        int numClimbPoints = ClimbController.getInstance().getClimb().getPoints().size();
        DisplayFormatter.setDistanceText(ClimbController.getInstance().getDistToPB(),
                "m", distBehind, false);

        monitor = new LocationMonitor(this);
        map = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.pursuitMap);
        map.setMapType(GoogleMap.MAP_TYPE_HYBRID, MapFragment.PlotType.PURSUIT, false);
        mirror = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.mirrorMap);
        mirror.setMapType(GoogleMap.MAP_TYPE_HYBRID, MapFragment.PlotType.PURSUIT, true);

        panel = (LinearLayout) findViewById(R.id.displayPanel);
        panel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (paused) {
                    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.purple_700)));
                } else {
                    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.teal_700)));
                }
                paused = !paused;
            }
        });
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
        DisplayFormatter.setDistanceText(ClimbController.getInstance().getDistToPB(),
                "m", distBehind, false);
        DisplayFormatter.setTimeText(ClimbController.getInstance().getTimeDiffToPB(), timeBehind);

        if (ClimbController.getInstance().getDistToPB() < 0) {
            distBehind.setTextColor(Color.RED);
            timeBehind.setTextColor(Color.RED);
        } else {
            distBehind.setTextColor(Color.GREEN);
            timeBehind.setTextColor(Color.GREEN);
        }

        RoutePoint snappedPos = map.addMarker(new LatLng(point.getLat(), point.getLon()), ClimbController.PointType.ATTEMPT, Color.CYAN, true);
        mirror.addMarker(new LatLng(point.getLat(), point.getLon()), ClimbController.PointType.ATTEMPT, Color.CYAN, false);

        if (ClimbController.getInstance().isPlotPB()) {
            map.addMarker(new LatLng(ClimbController.getInstance().getPbPoint().getLat(),
                            ClimbController.getInstance().getPbPoint().getLon()),
                    ClimbController.PointType.PB, Color.GREEN, true);
            mirror.addMarker(new LatLng(ClimbController.getInstance().getPbPoint().getLat(),
                            ClimbController.getInstance().getPbPoint().getLon()),
                    ClimbController.PointType.PB, Color.GREEN, false);
        }
        map.moveCamera(snappedPos, false);

        if (ClimbController.getInstance().getDistToPB() > 0) {
            mirror.moveCamera(snappedPos, true);
        }

        LinearLayout ll = (LinearLayout)findViewById(R.id.mirror);
        if (ClimbController.getInstance().getDistToPB() > 20) {
            ll.setVisibility(View.VISIBLE);
        } else {
            ll.setVisibility(View.INVISIBLE);
        }

        long now = new Date().getTime();
        if (!paused && now - loadTime > ClimbController.DISPLAY_INTERVAL) {
            // Check next screen
            if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_2D)) {
                Intent i = new Intent(ApplicationContextProvider.getContext(), MapActivity.class);
                startActivity(i);
            } else if (Preferences.getInstance().getBooleanPreference(Preferences.PREFERNECE_ELEVATION)) {
                Intent i = new Intent(ApplicationContextProvider.getContext(), FullClimbActivity.class);
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