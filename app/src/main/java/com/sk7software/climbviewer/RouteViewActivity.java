package com.sk7software.climbviewer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapsSdkInitializedCallback;
import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.geo.Projection;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.view.ClimbView;
import com.sk7software.climbviewer.view.DisplayFormatter;
import com.sk7software.climbviewer.view.PositionMarker;
import com.sk7software.climbviewer.view.ScreenController;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RouteViewActivity extends AppCompatActivity implements ActivityUpdateInterface {

    private ClimbView fullRouteView;
    private RelativeLayout offRoutePanel;
    private MapFragment map;
    private int routeId;
    private GPXRoute route;
    private LocationMonitor monitor;
    private float totalDist;
    private float totalElevGain;
    private int prepareForFinish;
    private LatLng lastRoutePoint;

    private static final String TAG = RouteViewActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_route_view);
        getSupportActionBar().hide();

        routeId = getIntent().getIntExtra("id", 0);
        int startIdx = getIntent().getIntExtra("startIdx", 0);

        // Get route and adjust start point
        route = Database.getInstance().getRoute(routeId);
        route.adjustRoute(startIdx);
        ClimbController.getInstance().loadRoute(route);

        calcDistAndElevation();
        prepareForFinish = -1;

        fullRouteView = findViewById(R.id.fullRouteView);
        fullRouteView.setClimb(route, 20);
        fullRouteView.setTransparency(0x88);
        setClimbViewHeight();
        fullRouteView.invalidate();

        fullRouteView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN || motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                    fullRouteView.setShowGradientAt((int)motionEvent.getX());
                    LatLng ll = fullRouteView.getLatLongAtX((int)motionEvent.getX());
                    if (ll != null) {
                        map.showPosition(ll);
                    }
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    fullRouteView.setShowGradientAt(-1);
                    map.showPosition(null);
                }
                fullRouteView.invalidate();
                return true;
            }
        });

        SeekBar transparency = findViewById(R.id.profileTransparency);
        transparency.setProgress(0x88);
        transparency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                fullRouteView.setTransparency(i);
                fullRouteView.invalidate();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        offRoutePanel = findViewById(R.id.panelOffRoute);
        offRoutePanel.setVisibility(View.GONE);

        map = (MapFragment)getSupportFragmentManager().findFragmentById(R.id.mapView);

        if (ClimbController.getInstance().isRouteInProgress()) {
            setMapForFollowing();
            fullRouteView.addPlot(ClimbController.PointType.ROUTE);
            monitor = new LocationMonitor(this);
        } else {
            map.setMapType(GoogleMap.MAP_TYPE_NORMAL, MapFragment.PlotType.ROUTE, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (monitor != null && !monitor.isListenerRunning()) {
            monitor.resumeListener();
        }
    }

    @Override
    protected void onStop() {
        if (monitor != null && monitor.isListenerRunning()) {
            monitor.stopListener();
        }
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        Intent i = new Intent(ApplicationContextProvider.getContext(), ClimbChooserActivity.class);
        startActivity(i);
    }

    @Override
    public void locationChanged(RoutePoint point) {
        if (prepareForFinish >= 0 && --prepareForFinish <= 0) {
            // Finish route and return to home screen
            ClimbController.getInstance().setRouteInProgress(false);
            PositionMonitor.getInstance().setOnRoute(false);
            PositionMonitor.getInstance().stopMonitor(PositionMonitor.MonitorType.ROUTE);
            Intent i = ScreenController.getInstance().getNextIntent(this);
            if (i != null) {
                startActivity(i);
            }
        }

        if (PositionMonitor.getInstance().getMonitoring().contains(PositionMonitor.MonitorType.ROUTE)) {
            PositionMonitor.getInstance().locationChanged(point);

            // Monitoring route again, so look for returning to route
            if (PositionMonitor.getInstance().isOnRoute()) {
                // Looks like we've returned to the route
                offRoutePanel.setVisibility(View.GONE);
                ClimbController.getInstance().setRouteInProgress(true);
                PositionMonitor.getInstance().stopMonitor(PositionMonitor.MonitorType.ROUTE);
                setMapForFollowing();
                map.setTrackRider(true);
                fullRouteView.addPlot(ClimbController.PointType.ROUTE);
            }
        }

        if (ClimbController.getInstance().isRouteInProgress()) {
            if (!fullRouteView.isInitialised()) {
                return;
            }

            fullRouteView.invalidate();
            updateDistAndElevation();

            RoutePoint snappedPos = ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ROUTE).getSnappedPosition();
            if (map != null && snappedPos != null) {
                lastRoutePoint = new LatLng(snappedPos.getLat(), snappedPos.getLon());
                map.addMarker(lastRoutePoint, ClimbController.PointType.ROUTE,
                        ClimbController.PointType.ROUTE.getColor(), PositionMarker.Size.LARGE);
                map.moveCamera(point, false, false);
            }
        } else if (prepareForFinish < 0) {
            // Hide elevation data and show "off route" warning and restart monitoring
            Log.d(TAG, "NOT ON ROUTE");
            offRoutePanel.setVisibility(View.VISIBLE);
            ClimbController.getInstance().setRouteInProgress(false);
            PositionMonitor.getInstance().setOnRoute(false);
            PositionMonitor.getInstance().doMonitor(PositionMonitor.MonitorType.ROUTE);
            PositionMonitor.getInstance().setTryingToResume(true);
            if (map != null) {
                map.setTrackRider(false);

                // Calculate distance from current point to last route point
                double radius = calcDistBetweenPoints(point, lastRoutePoint);
                map.plotOffRouteTrack(radius, new LatLng(point.getLat(), point.getLon()));
                map.addMarker(new LatLng(point.getLat(), point.getLon()), ClimbController.PointType.ROUTE,
                        ClimbController.PointType.ROUTE.getColor(), PositionMarker.Size.MEDIUM);
            }
        }

        // Check if any climbs on the route are in progress
        if (PositionMonitor.getInstance().getMonitoring().contains(PositionMonitor.MonitorType.CLIMB)) {
            PositionMonitor.getInstance().locationChanged(point);

            if (PositionMonitor.getInstance().getOnClimbId() > 0) {
                int climbId = PositionMonitor.getInstance().getOnClimbId();
                Intent nextIntent = null;
                ClimbController.getInstance().loadClimb(Database.getInstance().getClimb(climbId));

                ClimbController.getInstance().startAttempt();
                ClimbController.getInstance().loadPB();
                nextIntent = getNextScreen();

                if (nextIntent != null) {
                    nextIntent.putExtra("id", climbId);
                    startActivity(nextIntent);
                }
            }
        }
    }

    private Intent getNextScreen() {
        return new Intent(ApplicationContextProvider.getContext(), SectionViewActivity.class);
    }

    private void setMapForFollowing() {
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL, MapFragment.PlotType.FOLLOW_ROUTE, false);
        map.setZoom(18);
        map.setTilt(45);
        map.setCentre(ClimbController.getInstance().getLastPointLL());
    }

    private void setClimbViewHeight() {
        WindowManager wm = (WindowManager) ApplicationContextProvider.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        int s=0;
        int resource = ApplicationContextProvider.getContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resource > 0) {
            s = ApplicationContextProvider.getContext().getResources().getDimensionPixelSize(resource);
        }

        Log.d(TAG, "Setting route view height: " + (size.y - s)/3 + "/" + size.y + " (" + s + ")");
        fullRouteView.setHeight((size.y - s)/3, false);
    }

    private void calcDistAndElevation() {
        RoutePoint lastPt = null;

        for (RoutePoint p : route.getPoints()) {
            if (lastPt == null) {
                lastPt = p;
                continue;
            }

            totalDist += Math.sqrt(Math.pow(p.getEasting() - lastPt.getEasting(), 2) + Math.pow(p.getNorthing() - lastPt.getNorthing(), 2));
            if (p.getElevation() > lastPt.getElevation()) {
                totalElevGain += p.getElevation() - lastPt.getElevation();
            }

            lastPt = p;
        }

        if (!ClimbController.getInstance().isRouteInProgress()) {
            TextView label1 = findViewById(R.id.panel1Label);
            TextView label2 = findViewById(R.id.panel2Label);
            TextView txtDist = findViewById(R.id.txtPanel1);
            TextView txtElev = findViewById(R.id.txtPanel2);

            label1.setText("DISTANCE");
            label2.setText("ELEV GAIN");

            DisplayFormatter.setDistanceText(totalDist, "km", txtDist, false);
            DisplayFormatter.setDistanceText(totalElevGain, "m", txtElev, false);
        }
    }

    private void updateDistAndElevation() {
        if (ClimbController.getInstance().isRouteInProgress()) {
            TextView label1 = findViewById(R.id.panel1Label);
            TextView label2 = findViewById(R.id.panel2Label);
            TextView txtDist = findViewById(R.id.txtPanel1);
            TextView txtElev = findViewById(R.id.txtPanel2);

            label1.setText("TO GO");
            label2.setText("ELEV LEFT");

            float distDone = ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ROUTE).getDist();
            float elevDone = ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ROUTE).getElevDone();

            DisplayFormatter.setDistanceText(totalDist - distDone, "km", txtDist, false);
            DisplayFormatter.setDistanceText(totalElevGain - elevDone, "m", txtElev, false);

            // If in last 25m, flag screen to close after 5 more updates
            if (totalDist - distDone <= 25) {
                prepareForFinish = 5;

                // Clear route preferences (to prevent resuming)
                Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ROUTE_ID);
                Preferences.getInstance().clearIntPreference(Preferences.PREFERENCES_ROUTE_START_IDX);
            }
        }
    }

    private double calcDistBetweenPoints(RoutePoint current, LatLng last) {
        if (lastRoutePoint == null) {
            return 1000.0;
        }

        Projection proj = Database.getInstance().getProjection(route.getProjectionId());
        RoutePoint currentGrid = GeoConvert.convertLLToGrid(proj, current, route.getZone());
        RoutePoint lastPoint = new RoutePoint();
        lastPoint.setLat(last.latitude);
        lastPoint.setLon(last.longitude);
        RoutePoint lastGrid = GeoConvert.convertLLToGrid(proj, lastPoint, route.getZone());
        return Math.sqrt(Math.pow(currentGrid.getEasting() - lastGrid.getEasting(), 2) +
                         Math.pow(currentGrid.getNorthing() - lastGrid.getNorthing(), 2));
    }

    @Override
    public void setProgress(boolean showProgressDialog, String progressMessage) {}
}
