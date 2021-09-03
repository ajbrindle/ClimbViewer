package com.sk7software.climbviewer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.DisplayFormatter;

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

        totalDist = (TextView)findViewById(R.id.txtTotalDist);
        distToGo = (TextView)findViewById(R.id.txtToGoDist);

        int numClimbPoints = ClimbController.getInstance().getClimb().getPoints().size();
        DisplayFormatter.setDistanceText(ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart(),
                                        "km", totalDist, true);

        monitor = new LocationMonitor(this);
        map = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.climbMap);
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL, MapFragment.PlotType.FULL_CLIMB);
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
    public void locationChanged(RoutePoint point) {
        int numClimbPoints = ClimbController.getInstance().getClimb().getPoints().size();
        DisplayFormatter.setDistanceText(ClimbController.getInstance().getClimb().getPoints().get(numClimbPoints-1).getDistFromStart() -
                                ClimbController.getInstance().getAttemptDist(), "km", distToGo, true);
        map.addMarker(new LatLng(point.getLat(), point.getLon()), ClimbController.PointType.ATTEMPT, Color.CYAN);

        if (ClimbController.getInstance().isPlotPB()) {
            map.addMarker(new LatLng(ClimbController.getInstance().getPbPoint().getLat(),
                                     ClimbController.getInstance().getPbPoint().getLon()),
                          ClimbController.PointType.PB, Color.GREEN);
        }
        map.moveCamera(point);

        long now = new Date().getTime();
        if (now - loadTime > ClimbController.DISPLAY_INTERVAL) {
            Intent i = new Intent(ApplicationContextProvider.getContext(), FullClimbActivity.class);
            startActivity(i);
        }
    }
}