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

import java.util.Date;
import java.util.Map;

public class PursuitActivity extends AppCompatActivity implements ActivityUpdateInterface {

    private MapFragment map;
    private int climbId;
    private GPXRoute climb;
    private LocationMonitor monitor;
    private TextView distBehind;
    private TextView timeBehind;
    private long loadTime;

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
        map.setMapType(GoogleMap.MAP_TYPE_HYBRID, MapFragment.PlotType.PURSUIT);
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
        DisplayFormatter.setDistanceText(ClimbController.getInstance().getDistToPB(),
                "m", distBehind, false);
        DisplayFormatter.setTimeText(ClimbController.getInstance().getTimeDiffToPB(), timeBehind);

        map.addMarker(new LatLng(point.getLat(), point.getLon()), ClimbController.PointType.ATTEMPT, Color.CYAN);

        if (ClimbController.getInstance().isPlotPB()) {
            map.addMarker(new LatLng(ClimbController.getInstance().getPbPoint().getLat(),
                            ClimbController.getInstance().getPbPoint().getLon()),
                    ClimbController.PointType.PB, Color.GREEN);
        }
        map.moveCamera(point);
        long now = new Date().getTime();
        if (now - loadTime > ClimbController.DISPLAY_INTERVAL) {
            Intent i = new Intent(ApplicationContextProvider.getContext(), MapActivity.class);
            startActivity(i);
        }
    }
}