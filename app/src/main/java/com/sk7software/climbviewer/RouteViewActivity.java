package com.sk7software.climbviewer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
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
import com.sk7software.climbviewer.model.Track;
import com.sk7software.climbviewer.model.TrackFile;
import com.sk7software.climbviewer.model.TrackSegment;
import com.sk7software.climbviewer.view.ClimbView;
import com.sk7software.climbviewer.view.DisplayFormatter;
import com.sk7software.climbviewer.view.PositionMarker;
import com.sk7software.climbviewer.view.ScreenController;
import com.sk7software.util.aspectlogger.DebugTrace;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RouteViewActivity extends AppCompatActivity implements ActivityUpdateInterface {

    private ClimbView fullRouteView;
    private RelativeLayout offRoutePanel;
    private MapFragment map;
    private ImageButton btnShowClimbs;
    private int routeId;
    private GPXRoute route;
    private float totalDist;
    private float totalElevGain;
    private int prepareForFinish;
    private LatLng lastRoutePoint;
    private boolean justLeftRoute;
    private boolean ignoreLocationUpdates;
    private Map<Integer, Double> climbDistFromStart = new HashMap<>();


    private static final String TAG = RouteViewActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "RouteViewActivity create");

        setContentView(R.layout.activity_route_view);
        getSupportActionBar().hide();

        routeId = getIntent().getIntExtra("id", 0);
        int startIdx = getIntent().getIntExtra("startIdx", 0);

        if (startIdx < 0) {
            ignoreLocationUpdates = true;
            startIdx = 0;
        } else {
            ignoreLocationUpdates = false;
            LocationMonitor.getInstance(this);
        }

        Log.d(TAG, "Location info: " + ignoreLocationUpdates + " [" + startIdx + "]");

        // Get route and adjust start point
        route = Database.getInstance().getRoute(routeId);
        route.adjustRoute(startIdx);
        ClimbController.getInstance().loadRoute(route);

        calcDistAndElevation();
        prepareForFinish = -1;
        justLeftRoute = true;

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

        btnShowClimbs = (ImageButton) findViewById(R.id.showClimbsBtn);

        if (!ignoreLocationUpdates) {
            btnShowClimbs.setVisibility(View.GONE);

            if (PositionMonitor.getInstance().getMonitoring().contains(PositionMonitor.MonitorType.CLIMB)) {
                // if monitoring climbs, get distance of start of each climb from the start of the route
                List<GPXRoute> climbs = TrackFile.findClimbsOnTrackFromPoints(route);
                for (GPXRoute c : climbs) {
                    climbDistFromStart.put(c.getId(), getDistFromStart(c));
                }
            }
        }

        btnShowClimbs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<GPXRoute> climbs = TrackFile.findClimbsOnTrackFromPoints(route);
                String climbIds = climbs.stream()
                        .map(r -> String.valueOf(r.getId()))
                        .collect(Collectors.joining(","));
                fullRouteView.setShowClimbsList(climbIds);
                fullRouteView.invalidate();

                for (GPXRoute climb : climbs) {
                    List<RoutePoint> pts = climb.getPoints();
                    map.plotClimbTrackFromRoutePoints(pts);
                }
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
        } else {
            map.setMapType(GoogleMap.MAP_TYPE_NORMAL, MapFragment.PlotType.ROUTE, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "RouteViewActivity resume");
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        Intent i = new Intent(ApplicationContextProvider.getContext(), ClimbChooserActivity.class);
        startActivity(i);
    }

    @Override
    @DebugTrace
    public void locationChanged(RoutePoint point) {
        Log.d(TAG, "Location changed");
        if (ignoreLocationUpdates) return;

        if (prepareForFinish >= 0 && --prepareForFinish <= 0) {
            // Finish route and return to home screen
            ClimbController.getInstance().setRouteInProgress(false);
            ClimbController.getInstance().clearRoute();
            PositionMonitor.getInstance().setOnRoute(false);
            PositionMonitor.getInstance().resetRejoin();
            PositionMonitor.getInstance().stopMonitor(PositionMonitor.MonitorType.ROUTE);
            Intent i = ScreenController.getInstance().getNextIntent(this);
            if (i != null) {
                startActivity(i);
            }
        }

        boolean positionMonitorDone = false;

        if (PositionMonitor.getInstance().getMonitoring().contains(PositionMonitor.MonitorType.ROUTE)) {
            Log.d(TAG, "Monitoring route");
            PositionMonitor.getInstance().locationChanged(point);
            positionMonitorDone = true;

            // Monitoring route again, so look for returning to route
            if (PositionMonitor.getInstance().isOnRoute()) {
                // Looks like we've returned to the route
                Log.d(TAG, "Back on route at index " + PositionMonitor.getInstance().getMatchingSectionIdx());
                offRoutePanel.setVisibility(View.GONE);
                justLeftRoute = true;
                ClimbController.getInstance().rejoinRoute(PositionMonitor.getInstance().getMatchingSectionIdx());
                PositionMonitor.getInstance().stopMonitor(PositionMonitor.MonitorType.ROUTE);
                PositionMonitor.getInstance().resetRejoin();
                setMapForFollowing();
                map.setTrackRider(true);
                map.removeMarker(ClimbController.PointType.ROUTE, ClimbController.PointType.ROUTE.getColor(), PositionMarker.Size.MEDIUM);
                map.plotLocalSection(PositionMonitor.getInstance().getMatchingSectionIdx()-1, PositionMonitor.getInstance().getMatchingSectionIdx()+10);
                fullRouteView.addPlot(ClimbController.PointType.ROUTE);
            }
        }

        if (ClimbController.getInstance().isRouteInProgress()) {
            Log.d(TAG, "Climb view initialised: " + fullRouteView.isInitialised());

            if (!fullRouteView.isInitialised()) {
                return;
            }

            fullRouteView.invalidate();
            updateDistAndElevation();

            Log.d(TAG, "Position camera");
            RoutePoint snappedPos = ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ROUTE).getSnappedPosition();
            if (map != null && snappedPos != null) {
                lastRoutePoint = new LatLng(snappedPos.getLat(), snappedPos.getLon());
                map.addMarker(lastRoutePoint, ClimbController.PointType.ROUTE,
                        ClimbController.PointType.ROUTE.getColor(), PositionMarker.Size.LARGE);
                map.plotLocalSection(ClimbController.getInstance().getRouteIdx()-1, ClimbController.getInstance().getRouteIdx()+10);
                map.moveCamera(point, false, false);
            }
        } else if (prepareForFinish < 0) {
            // Hide elevation data and show "off route" warning and restart monitoring
            Log.d(TAG, "NOT ON ROUTE");
            offRoutePanel.setVisibility(View.VISIBLE);
            if (justLeftRoute) {
                PositionMonitor.getInstance().resetRejoin();
                justLeftRoute = false;
            }
            ClimbController.getInstance().setRouteInProgress(false);
            PositionMonitor.getInstance().setOnRoute(false);
            PositionMonitor.getInstance().doMonitor(PositionMonitor.MonitorType.ROUTE);
            PositionMonitor.getInstance().setTryingToResume(true);
            if (map != null) {
                map.setTrackRider(false);

                // Calculate distance from current point to last route point
                double radius = calcDistBetweenPoints(point, lastRoutePoint);
                map.plotOffRouteTrack(radius, new LatLng(point.getLat(), point.getLon()), point.getBearing());
                map.removeMarker(ClimbController.PointType.ROUTE, ClimbController.PointType.ROUTE.getColor(), PositionMarker.Size.LARGE);
                map.addMarker(new LatLng(point.getLat(), point.getLon()), ClimbController.PointType.ROUTE,
                        ClimbController.PointType.ROUTE.getColor(), PositionMarker.Size.MEDIUM);
            }
        }

        // Check if any climbs on the route are in progress
        if (PositionMonitor.getInstance().getMonitoring().contains(PositionMonitor.MonitorType.CLIMB)) {
            Log.d(TAG, "Monitoring climb");
            if (!positionMonitorDone) {
                PositionMonitor.getInstance().locationChanged(point);
            }

            if (PositionMonitor.getInstance().getOnClimbId() > 0) {
                Log.d(TAG, "On climb id " + PositionMonitor.getInstance().getOnClimbId());
                int climbId = PositionMonitor.getInstance().getOnClimbId();
                ClimbController.getInstance().loadClimb(Database.getInstance().getClimb(climbId));

                ClimbController.getInstance().startAttempt();
                ClimbController.getInstance().loadPB();
                Intent nextIntent = getNextScreen();

                if (nextIntent != null) {
                    nextIntent.putExtra("id", climbId);
                    startActivity(nextIntent);
                }
            } else {
                // Find distance to start of next climb

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
        Log.d(TAG, "Update panels");
        if (ClimbController.getInstance().isRouteInProgress()) {
            TextView label1 = findViewById(R.id.panel1Label);
            TextView label2 = findViewById(R.id.panel2Label);
            TextView txtDist = findViewById(R.id.txtPanel1);
            TextView txtElev = findViewById(R.id.txtPanel2);

            float distDone = ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ROUTE).getDist();
            float elevDone = ClimbController.getInstance().getAttempts().get(ClimbController.PointType.ROUTE).getElevDone();

            // Determine if within 3km of any climbs
            double minDist = Double.MAX_VALUE;
            int nextClimb = -1;
            for (Map.Entry<Integer, Double> climbDist : climbDistFromStart.entrySet()) {
                double distToClimb = climbDist.getValue() - distDone;
                if (distToClimb > 0 && distToClimb < 3000 && distToClimb < minDist) {
                    minDist = distToClimb;
                    nextClimb = climbDist.getKey();
                }
            }

            if (nextClimb > 0) {
                label1.setText("NEXT CLIMB");
                label2.setText("RATING");

                DisplayFormatter.setDistanceText((float)minDist, "km", txtDist, true);
                txtElev.setText(String.valueOf(Database.getInstance().getClimbRating(nextClimb)));
            } else {
                label1.setText("TO GO");
                label2.setText("ELEV LEFT");

                DisplayFormatter.setDistanceText(totalDist - distDone, "km", txtDist, false);
                DisplayFormatter.setDistanceText(totalElevGain - elevDone, "m", txtElev, false);
            }

            // If in last 25m, flag screen to close after 5 more updates
            if (totalDist - distDone <= 25) {
                if (prepareForFinish < 0) {
                    prepareForFinish = 5;
                }

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

    private Double getDistFromStart(GPXRoute climb) {
        RoutePoint climbStart = climb.getPoints().get(0);
        PointF startPt = new PointF((float)climbStart.getEasting(), (float)climbStart.getNorthing());

        // Find where on route this lands
        for (int i=0; i<route.getPoints().size()-1; i++) {
            PointF p1 = new PointF((float)route.getPoints().get(i).getEasting(), (float)route.getPoints().get(i).getNorthing());
            PointF p2 = new PointF((float)route.getPoints().get(i+1).getEasting(), (float)route.getPoints().get(i+1).getNorthing());
            if (LocationMonitor.pointWithinLineSegment(startPt, p1, p2)) {
                // Get distance from p1 to start of climb
                double delta =  Math.sqrt(Math.pow(startPt.x - p1.x, 2) + Math.pow(startPt.y - p1.y, 2));
                return route.getPoints().get(i).getDistFromStart() + delta;
            }
        }

        return Double.valueOf(-1);
    }
}
