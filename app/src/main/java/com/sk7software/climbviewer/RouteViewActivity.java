package com.sk7software.climbviewer;

import android.content.Context;
import android.content.Intent;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.sk7software.climbviewer.db.Database;
import com.sk7software.climbviewer.db.Preferences;
import com.sk7software.climbviewer.geo.GeoConvert;
import com.sk7software.climbviewer.geo.Projection;
import com.sk7software.climbviewer.model.DirectionChecker;
import com.sk7software.climbviewer.model.GPXRoute;
import com.sk7software.climbviewer.model.RoutePoint;
import com.sk7software.climbviewer.model.TrackFile;
import com.sk7software.climbviewer.view.ClimbView;
import com.sk7software.climbviewer.view.DisplayFormatter;
import com.sk7software.climbviewer.view.PositionMarker;
import com.sk7software.climbviewer.view.ScreenController;
import com.sk7software.climbviewer.view.SummaryPanel;
import com.sk7software.util.aspectlogger.DebugTrace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RouteViewActivity extends AppCompatActivity implements ActivityUpdateInterface {

    private ClimbView fullRouteView;
    private ClimbView nextClimbView;
    private RelativeLayout offRoutePanel;
    private RelativeLayout routeInfoPanel;
    private RelativeLayout completionPanel;
    private MapFragment map;
    private ImageButton btnShowClimbs;
    private ImageButton btnShowLabels;
    private int routeId;
    private GPXRoute route;
    private float totalDist;
    private float totalElevGain;
    private int prepareForFinish;
    private LatLng lastRoutePoint;
    private boolean justLeftRoute;
    private boolean ignoreLocationUpdates;
    private Map<Integer, Double> climbDistFromStart = new HashMap<>();
    private boolean loadNextClimbWarning = false;
    private EditText txtRouteName;
    private int nextClimbId;
    private float nextClimbLength;
    private float nextClimbHeight;
    private int nextClimbCounter;
    private boolean showingClimbs;
    private boolean showingLabels;

    private static final String TAG = RouteViewActivity.class.getSimpleName();
    private static final int DEFAULT_TRANSPARENCY = 190;
    private static final float[] NEGATIVE = {
            -1.0f, 0, 0, 0, 255, // red
            0, -1.0f, 0, 0, 255, // green
            0, 0, -1.0f, 0, 255, // blue
            0, 0, 0, 1.0f, 0  // alpha
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "RouteViewActivity create");

        setContentView(R.layout.activity_route_view);
        getSupportActionBar().hide();

        routeId = getIntent().getIntExtra("id", 0);
        int startIdx = getIntent().getIntExtra("startIdx", 0);
        int lastClimbId = getIntent().getIntExtra("lastClimbId", 0);

        if (startIdx < 0) {
            RelativeLayout namePanel = (RelativeLayout)findViewById(R.id.panelRouteName);
            namePanel.setVisibility(View.VISIBLE);
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
        txtRouteName = (EditText) findViewById(R.id.txtRouteName);
        txtRouteName.setText(route.getName());
        txtRouteName.setEnabled(false);

        ClimbController.getInstance().loadRoute(route);

        calcDistAndElevation();
        prepareForFinish = -1;
        justLeftRoute = true;
        showingClimbs = false;

        routeInfoPanel = findViewById(R.id.panelRouteInfo);
        fullRouteView = findViewById(R.id.fullRouteView);
        nextClimbView = findViewById(R.id.nextClimbView);
        nextClimbView.setVisibility(View.GONE);
        fullRouteView.setClimb(route, 20);

        int transparencyVal = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_TRANSPARENCY, DEFAULT_TRANSPARENCY);
        fullRouteView.setTransparency(transparencyVal);
        setClimbViewHeight(fullRouteView);
        fullRouteView.invalidate();

        fullRouteView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN || motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                    fullRouteView.setShowGradientAt((int)motionEvent.getX());
                    LatLng ll = fullRouteView.getLatLongAtX((int)motionEvent.getX());
                    if (ll != null) {
                        map.showPosition(ll);
                        showClimbMarker((int)motionEvent.getX(), ll);
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
        btnShowLabels = (ImageButton) findViewById(R.id.showLabelsBtn);
        btnShowLabels.setVisibility(View.GONE);

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
                map.clearClimbTracks();
                if (!showingClimbs) {
                    showingClimbs = true;
                    showingLabels = false;
                    List<GPXRoute> climbs = TrackFile.findClimbsOnTrackFromPoints(route);
                    String climbIds = climbs.stream()
                            .map(r -> String.valueOf(r.getId()))
                            .collect(Collectors.joining(","));
                    fullRouteView.setShowClimbsList(climbIds);
                    fullRouteView.invalidate();
                    btnShowClimbs.getDrawable().setColorFilter(new ColorMatrixColorFilter(NEGATIVE));
                    btnShowLabels.setVisibility(View.VISIBLE);

                    for (GPXRoute climb : climbs) {
                        List<RoutePoint> pts = climb.getPoints();
                        map.plotClimbTrackFromRoutePoints(pts);
                    }
                } else {
                    showingClimbs = false;
                    showingLabels = false;
                    fullRouteView.setShowClimbsList("");
                    fullRouteView.invalidate();
                    btnShowClimbs.getDrawable().clearColorFilter();
                    btnShowLabels.getDrawable().clearColorFilter();
                    btnShowLabels.setVisibility(View.GONE);
                }
            }
        });

        btnShowLabels.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!showingLabels) {
                    showingLabels = true;
                    map.clearClimbMarkers();
                    btnShowLabels.getDrawable().setColorFilter(new ColorMatrixColorFilter(NEGATIVE));
                    List<GPXRoute> climbs = TrackFile.findClimbsOnTrackFromPoints(route);
                    for (GPXRoute climb : climbs) {
                        List<RoutePoint> pts = climb.getPoints();
                        int midIndex = climb.getPoints().size()/2;
                        map.setClimbIcon(climb.getName(), climb.getPoints().get(midIndex).getLat(), climb.getPoints().get(midIndex).getLon());
                    }
                } else {
                    showingLabels = false;
                    map.clearClimbMarkers();
                    btnShowLabels.getDrawable().clearColorFilter();
                }
            }
        });

        SeekBar transparency = findViewById(R.id.profileTransparency);
        transparency.setProgress(transparencyVal);
        transparency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                fullRouteView.setTransparency(i);
                fullRouteView.invalidate();
                Preferences.getInstance().addPreference(Preferences.PREFERENCES_TRANSPARENCY, i);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        offRoutePanel = findViewById(R.id.panelOffRoute);
        offRoutePanel.setVisibility(View.GONE);

        ImageButton btnEdit = (ImageButton)findViewById(R.id.btnEdit);
        ImageButton btnOK = (ImageButton)findViewById(R.id.btnOK);

        txtRouteName.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                txtRouteName.post(new Runnable() {
                    @Override
                    public void run() {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (hasFocus) {
                            imm.showSoftInput(txtRouteName, InputMethodManager.SHOW_IMPLICIT);
                        } else {
                            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                        }
                    }
                });
            }
        });

        btnEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnEdit.setVisibility(View.GONE);
                btnOK.setVisibility(View.VISIBLE);
                txtRouteName.setEnabled(true);
                txtRouteName.requestFocus();
                txtRouteName.selectAll();
            }
        });

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String newName = txtRouteName.getText().toString();
                if (!"".equals(newName)) {
                    Database.getInstance().updateRouteName(route.getId(), newName);
                    btnOK.setVisibility(View.GONE);
                    btnEdit.setVisibility(View.VISIBLE);
                    txtRouteName.setEnabled(false);
                }
            }
        });

        if (lastClimbId > 0) {
            // Show summary panel before resuming route
            routeInfoPanel.setVisibility(View.GONE);
            completionPanel = findViewById(R.id.climbCompletePanel);
            SummaryPanel panel = new SummaryPanel();
            panel.showSummary(completionPanel, lastClimbId, this);
        }

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
        Log.d(TAG, "RouteViewActivity stopped");
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        Intent i = new Intent(ApplicationContextProvider.getContext(), MainActivity.class);
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

                ClimbController.getInstance().startAttempt(0);
                ClimbController.getInstance().loadPB();
                Intent nextIntent = getNextScreen();

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

    private void setClimbViewHeight(ClimbView climbView) {
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
        climbView.setHeight((size.y - s)/3, false);
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

            // Determine if within threshold distance of any climbs
            double minDist = Double.MAX_VALUE;
            int nextClimb = -1;
            for (Map.Entry<Integer, Double> climbDist : climbDistFromStart.entrySet()) {
                double distToClimb = climbDist.getValue() - distDone;
                int warnDist = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_CLIMB_WARNING, 1000);
                if (distToClimb > 0 && distToClimb < warnDist && distToClimb < minDist) {
                    minDist = distToClimb;
                    nextClimb = climbDist.getKey();
                }
            }
            if (nextClimb > 0 && nextClimb != nextClimbId) {
                nextClimbId = nextClimb;
                if (!loadNextClimbWarning) {
                    Log.d(TAG, "Load next climb view");
                    loadNextClimbWarning = true;
                    GPXRoute gc = Database.getInstance().getClimb(nextClimb);
                    gc.setPointsDist();
                    gc.calcSmoothedPoints();
                    nextClimbLength = gc.getPoints().get(gc.getPoints().size()-1).getDistFromStart();
                    nextClimbHeight = (float)gc.getElevationChange();
                    fullRouteView.setVisibility(View.GONE);
                    nextClimbView.setVisibility(View.VISIBLE);
                    nextClimbView.setClimb(gc, 20);
                    int transparencyVal = Preferences.getInstance().getIntPreference(Preferences.PREFERENCES_TRANSPARENCY, DEFAULT_TRANSPARENCY);
                    nextClimbView.setTransparency(transparencyVal);
                    setClimbViewHeight(nextClimbView);
                    nextClimbView.invalidate();
                }
            }

            if (nextClimb > 0) {
                label1.setText("NEXT CLIMB");
                DisplayFormatter.setDistanceText((float)minDist, "km", txtDist, true);

                nextClimbCounter++;

                if (nextClimbCounter % 15 < 5) {
                    label2.setText("RATING");
                    txtElev.setText(String.valueOf(Database.getInstance().getClimbRating(nextClimb)));
                } else if (nextClimbCounter % 15 < 10) {
                    label2.setText("DIST");
                    DisplayFormatter.setDistanceText(nextClimbLength, "km", txtElev, true);
                } else {
                    label2.setText("HEIGHT");
                    DisplayFormatter.setDistanceText(nextClimbHeight, "m", txtElev, true);
                }
            } else {
                label1.setText("TO GO");
                label2.setText("ELEV LEFT");
                loadNextClimbWarning = false;
                fullRouteView.setVisibility(View.VISIBLE);
                nextClimbView.setVisibility(View.GONE);

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
    public void clearCompletionPanel() {
        completionPanel.setVisibility(View.GONE);
        routeInfoPanel.setVisibility(View.VISIBLE);
    }

    private Double getDistFromStart(GPXRoute climb) {
        RoutePoint climbStart = climb.getPoints().get(0);
        PointF startPt = new PointF((float)climbStart.getEasting(), (float)climbStart.getNorthing());

        // Find where on route this lands
        for (int i=0; i<route.getPoints().size()-1; i++) {
            PointF p1 = new PointF((float)route.getPoints().get(i).getEasting(), (float)route.getPoints().get(i).getNorthing());
            PointF p2 = new PointF((float)route.getPoints().get(i+1).getEasting(), (float)route.getPoints().get(i+1).getNorthing());
            if (LocationMonitor.pointWithinLineSegment(startPt, p1, p2)) {
                PointF second = new PointF((float) climb.getPoints().get(1).getEasting(), (float) climb.getPoints().get(1).getNorthing());
                DirectionChecker checker = new DirectionChecker(i, p1, p2, startPt);
                if (checker.check(second, route.getPoints(), 1)) {
                    // Get distance from p1 to start of climb
                    Log.d(TAG, "FOUND CLIMB START " + climb.getName());
                    double delta =  Math.sqrt(Math.pow(startPt.x - p1.x, 2) + Math.pow(startPt.y - p1.y, 2));
                    return route.getPoints().get(i).getDistFromStart() + delta;
                }
            }
        }

        return Double.valueOf(-1);
    }

    private void showClimbMarker(int x, LatLng ll) {
        if (showingLabels || fullRouteView.getClimbCoords() == null) {
            return;
        }

        boolean found = false;

        for (ClimbView.ClimbCoords cc : fullRouteView.getClimbCoords()) {
            if (cc.idAtLocation(x) > 0) {
                found = true;
                map.setSingleClimbIcon(cc.nameAtLocation(x), ll);
            }
        }

        if (!found) {
            map.clearClimbMarkers();
        }
    }
}
