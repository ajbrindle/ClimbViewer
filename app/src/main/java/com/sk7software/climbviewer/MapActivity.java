package com.sk7software.climbviewer;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;

public class MapActivity extends AppCompatActivity implements ActivityUpdateInterface {

    private MapFragment map;
    private int climbId;
    private GPXRoute climb;
    private LocationMonitor monitor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        monitor = new LocationMonitor(this);
        map = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.fragmentContainerView);
    }

    @Override
    public void locationChanged(RoutePoint point) {

    }
}