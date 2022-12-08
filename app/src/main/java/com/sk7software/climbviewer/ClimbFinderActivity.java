package com.sk7software.climbviewer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.view.ClimbView;

import java.util.ArrayList;
import java.util.List;

public class ClimbFinderActivity extends AppCompatActivity {

    private MapFragment map;
    private ClimbView routeClimbView;
    private ClimbView zoomClimbView;
    private int routeId;
    private GPXRoute route;

    private static final String TAG = ClimbFinderActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_climb_finder);
        getSupportActionBar().hide();

        routeId = getIntent().getIntExtra("id", 0);
        int startIdx = 0;
        route = Database.getInstance().getRoute(routeId);
        ClimbController.getInstance().loadRoute(route);

        int viewHeight = setClimbViewHeight();

        routeClimbView = (ClimbView) findViewById(R.id.climbFinder);
        routeClimbView.setClimb(route);
        routeClimbView.setHeight(viewHeight, false);
        routeClimbView.setTransparency(0xFF);
        routeClimbView.invalidate();

        zoomClimbView = (ClimbView) findViewById(R.id.climbZoom);
        zoomClimbView.setClimb(route);
        zoomClimbView.setHeight(viewHeight, true);
        zoomClimbView.setTransparency(0xFF);
        zoomClimbView.setZoomFac(5);

        routeClimbView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN ) {
                    Log.d(TAG, "X click: " + (int)motionEvent.getX());
                    routeClimbView.setShowGradientAt((int)motionEvent.getX());
                    routeClimbView.setX0((int)motionEvent.getX());
                    zoomClimbView.setX0((int)motionEvent.getX());
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                    routeClimbView.setShowGradientAt((int)motionEvent.getX());
                    routeClimbView.setX1((int)motionEvent.getX());
                    zoomClimbView.setX1((int)motionEvent.getX());
                    LatLng ll = routeClimbView.getLatLongAtX((int)motionEvent.getX());
                    if (ll != null) {
                        List<LatLng> trackPoints = new ArrayList<>();
                        for (int i=zoomClimbView.getTrackStartIndex(); i<= zoomClimbView.getTrackEndIndex(); i++) {
                            trackPoints.add(new LatLng(route.getPoints().get(i).getLat(), route.getPoints().get(i).getLon()));
                        }
                        map.plotClimbTrack(trackPoints);
                    }
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    //routeClimbView.fixClimbPoint((int)motionEvent.getX());
                }
                routeClimbView.invalidate();
                zoomClimbView.clearPoints();
                zoomClimbView.setFixedYRange(routeClimbView.getFixedYRange());
                zoomClimbView.invalidate();
                return true;
            }
        });

        zoomClimbView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    Log.d(TAG, "X click: " + (int) motionEvent.getX());
                    zoomClimbView.setShowGradientAt((int) motionEvent.getX());
                    zoomClimbView.setX0Fine((int) motionEvent.getX());
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                    zoomClimbView.setShowGradientAt((int) motionEvent.getX());
                    zoomClimbView.setX1Fine((int) motionEvent.getX());
                    LatLng ll = zoomClimbView.getLatLongAtX((int) motionEvent.getX());
                    if (ll != null) {
                        List<LatLng> trackPoints = new ArrayList<>();
                        for (int i = zoomClimbView.getTrackStartIndex(); i <= zoomClimbView.getTrackEndIndex(); i++) {
                            trackPoints.add(new LatLng(route.getPoints().get(i).getLat(), route.getPoints().get(i).getLon()));
                        }
                        map.plotClimbTrack(trackPoints);
                    }
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    //routeClimbView.fixClimbPoint((int)motionEvent.getX());
                }
                zoomClimbView.invalidate();
                return true;
            }
        });

        map = (MapFragment) getSupportFragmentManager().findFragmentById(R.id.mapClimbView);
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL, MapFragment.PlotType.ROUTE, false);
    }

    private int setClimbViewHeight() {
        WindowManager wm = (WindowManager) ApplicationContextProvider.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        int s=0;
        int resource = ApplicationContextProvider.getContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resource > 0) {
            s = ApplicationContextProvider.getContext().getResources().getDimensionPixelSize(resource);
        }

        return (size.y - s)/3;
    }

}